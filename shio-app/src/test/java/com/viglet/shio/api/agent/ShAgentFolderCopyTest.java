/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH244 — {@code folder.copy}, the last content verb that only the console could perform.
 *
 * <p>Copy lived at {@code PUT /api/v2/object/copyto/{dest}} and nowhere else: no batch op, no MCP
 * verb, no CLI path. For a <b>post</b> that was close to harmless — an agent duplicates a page by
 * reading it and upserting the fields elsewhere — so what these assertions are about is the
 * <b>folder</b>, the case an agent could not reproduce without walking the tree and emitting an
 * upsert per post.</p>
 *
 * <p>Three of them are the ones that would have caught the design going wrong, and they are
 * deliberately about what the op <em>refuses</em>: a copy that numbered its own target would hand
 * an agent an address it did not ask for, and a copy that overwrote one would be the only write on
 * this surface that destroys without a confirm token.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentFolderCopyTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String otherSiteName;
    private String postTypeName;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            siteName = "copy-site-" + UUID.randomUUID();
            otherSiteName = "copy-dest-" + UUID.randomUUID();
            site(siteName);
            site(otherSiteName);

            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShWidget created = new ShWidget();
                        created.setName("Text");
                        created.setDescription("Text");
                        entityManager.persist(created);
                        return created;
                    });

            ShPostType postType = new ShPostType();
            postTypeName = "CopyArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Copy Article");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setIsTitle((byte) 1);
            attr.setRequired((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });
    }

    private void site(String name) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        site.setOwner("admin");
        entityManager.persist(site);

        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        root.setOwner("admin");
        entityManager.persist(root);
    }

    // --- helpers ---

    private ShAgentChangeSet run(boolean dryRun, Op... ops) {
        List<Op> list = List.of(ops);
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(dryRun, list), true, "tester"));
    }

    private JsonNode data(String headline) {
        return objectMapper.valueToTree(Map.of("headline", headline));
    }

    private Op folder(String path) {
        return new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + path,
                null, null, null);
    }

    private Op post(String url, String folder, String headline) {
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + url,
                postTypeName, folder, data(headline));
    }

    private Op copy(String from, String to) {
        return copyInto(siteName, from, to);
    }

    private Op copyInto(String destSite, String from, String to) {
        return new Op(ShAgentBatchService.FOLDER_COPY, "folder:" + siteName + from,
                null, null, to == null ? null : "folder:" + destSite + to, null);
    }

    private String folderId(String site, String path) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShSite found = entityManager.createQuery(
                            "select s from ShSite s where s.name = :n", ShSite.class)
                    .setParameter("n", site).getSingleResult();
            return shAgentBatchService.findFolder(found, path).map(ShFolder::getId).orElse(null);
        });
    }

    /**
     * Post titles in a folder, keyed on the folder <b>id</b>.
     *
     * <p>Filtering on {@code p.shFolder.name} is the SH22 join hazard {@code agents.md} names
     * first: {@code ShFolder} is an {@code ShObject} subclass, so an implicit association join
     * emits the {@code tenant_id} predicate against an alias that has no such column.</p>
     */
    private List<String> postsIn(String site, String path, ShPostState state) {
        String id = folderId(site, path);
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery(
                                "select p.title from ShPostUnified p where p.shFolder.id = :id "
                                        + "and p.state = :s order by p.title", String.class)
                        .setParameter("id", id)
                        .setParameter("s", state)
                        .getResultList());
    }

    private static Change last(ShAgentChangeSet result) {
        return result.results().get(result.results().size() - 1);
    }

    /**
     * The problem slug of the refusal a batch answered with.
     *
     * <p>Every op failure arrives wrapped in {@code BatchOpException} — that wrapper is what
     * carries {@code at: ops[3]} — so asserting the inner class would assert the wrapper's
     * absence. The slug is the stable thing a caller branches on (SH80), so it is what these
     * tests read.</p>
     */
    private static String slugOf(Throwable thrown) {
        return ((ShAgentBatchService.BatchOpException) thrown).toProblemDetail().getType()
                .toString().replaceAll(".*/", "");
    }

    // --- the assertions ---

    @Test
    @DisplayName("SH244: a copy duplicates the whole subtree and leaves the source where it was")
    void aCopyDuplicatesTheSubtreeAndLeavesTheSourceAlone() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/inner"),
                post(base + "/a", base, "A"), post(base + "/b", base + "/inner", "B"));

        Change change = last(run(false, copy(base, base + "-copy")));

        assertThat(change.action()).isEqualTo(Change.CREATE);
        assertThat(change.detail())
                .as("""
                        the plan and the run both say what a copy is about to duplicate — a caller \
                        cannot see a subtree from an address, and "created a folder" is what makes \
                        a hundred-page copy look like a one-folder one""")
                .isEqualTo("2 folder(s) and 2 post(s), every copy a draft");

        assertThat(postsIn(siteName, base + "-copy", ShPostState.DRAFT)).containsExactly("A");
        assertThat(postsIn(siteName, base + "-copy/inner", ShPostState.DRAFT)).containsExactly("B");
        assertThat(postsIn(siteName, base, ShPostState.DRAFT))
                .as("a copy is the one write here that must leave its subject untouched")
                .containsExactly("A");
    }

    @Test
    @DisplayName("SH244: every copied post lands as a draft, whatever the original was")
    void everyCopiedPostIsADraft() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/live", base, "Live"));
        run(false, new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + base + "/live",
                null, null, null));

        run(false, copy(base, base + "-copy"));

        assertThat(postsIn(siteName, base + "-copy", ShPostState.PUBLISHED))
                .as("""
                        SH107's publish gate answers for one post at a time. A folder copy that \
                        carried the PUBLISHED rows across would put a hundred pages live in one \
                        op that needs no confirm token — which is nobody's intention and no \
                        surface's decision to make (P7)""")
                .isEmpty();
        assertThat(postsIn(siteName, base + "-copy", ShPostState.DRAFT)).containsExactly("Live");
    }

    @Test
    @DisplayName("SH244: the copy lands at the name the caller chose, never a numbered one")
    void theCopyIsNamedByTheCaller() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/a", base, "A"));

        Change change = last(run(false, copy(base, base + "-archive")));

        assertThat(change.address())
                .as("""
                        the console numbers a clone ("archive (2)") because a curator can read the \
                        result. An agent writes the next op against this address, so a \
                        server-chosen name is a second round trip and a name nobody asked for""")
                .isEqualTo("folder:" + siteName + base + "-archive");
        assertThat(folderId(siteName, base + "-archive")).isNotNull();
    }

    @Test
    @DisplayName("SH244: an occupied target is refused, not numbered")
    void anOccupiedTargetIsRefused() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "-taken"));

        assertThatThrownBy(() -> run(false, copy(base, base + "-taken")))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> {
                    assertThat(slugOf(thrown))
                            .as("""
                                    its own slug and not move-target-occupied: the two have \
                                    different fixes, and a copy's is to pick another name because \
                                    the caller chose the name and the source has not moved""")
                            .isEqualTo("copy-target-occupied");
                    assertThat(((ShAgentBatchService.BatchOpException) thrown)
                            .toProblemDetail().getDetail()).contains("numbers a clone");
                });

        assertThat(postsIn(siteName, base + "-taken", ShPostState.DRAFT))
                .as("a refusal that had already written half the subtree is not a refusal")
                .isEmpty();
    }

    @Test
    @DisplayName("SH244: a dry run reports the scope and writes nothing")
    void aDryRunWritesNothing() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/a", base, "A"));

        Change planned = last(run(true, copy(base, base + "-copy")));

        assertThat(planned.action()).isEqualTo(Change.CREATE);
        assertThat(planned.detail()).isEqualTo("1 folder(s) and 1 post(s), every copy a draft");
        assertThat(folderId(siteName, base + "-copy"))
                .as("SH81's first rule: a plan that perturbs the instance is not a plan")
                .isNull();
    }

    @Test
    @DisplayName("SH244: a destination inside the source's own subtree is refused (SH203)")
    void aDestinationInsideTheSourceIsRefused() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/inner"));

        assertThatThrownBy(() -> run(false, copy(base, base + "/inner/again")))
                .as("""
                        the copy's cycle guard is not the move's: copyFolder saves the copy and \
                        then walks the source's children, so a destination inside the source makes \
                        the walk descend into the copies it is still making and never return""")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> assertThat(slugOf(thrown)).isEqualTo("folder-copy-cycle"));
    }

    @Test
    @DisplayName("SH244: a dry run refuses the cycle too, so the plan does not lie")
    void aDryRunRefusesTheCycle() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), folder(base + "/inner"));

        assertThatThrownBy(() -> run(true, copy(base, base + "/inner/again")))
                .as("SH82's rule: a plan and an apply differ only in a flag, or the plan lied")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> assertThat(slugOf(thrown)).isEqualTo("folder-copy-cycle"));
    }

    @Test
    @DisplayName("SH244: a copy may cross sites, which is the workaround folder.move sends you to")
    void aCopyMayCrossSites() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base), post(base + "/a", base, "A"));

        Change change = last(run(false, copyInto(otherSiteName, base, "/imported")));

        assertThat(change.address()).isEqualTo("folder:" + otherSiteName + "/imported");
        assertThat(postsIn(otherSiteName, "/imported", ShPostState.DRAFT))
                .as("""
                        folder.move refuses a site boundary because re-parenting takes the \
                        original with it. A copy detaches nothing, and its own teaching error \
                        used to send the caller round a per-post loop instead""")
                .containsExactly("A");
        assertThat(postsIn(siteName, base, ShPostState.DRAFT)).containsExactly("A");
    }

    @Test
    @DisplayName("SH244: the missing `to` teaches the name chain rather than a parent folder")
    void aCopyWithNoTargetTeaches() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base));

        assertThatThrownBy(() -> run(false, copy(base, null)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> {
                    assertThat(slugOf(thrown)).isEqualTo("missing-op-field");
                    assertThat(((ShAgentBatchService.BatchOpException) thrown)
                            .toProblemDetail().getDetail())
                            .as("""
                                    `to` is the copy's own name chain and not the folder to copy \
                                    into — a caller that read it the other way would put the copy \
                                    one level too deep and only find out by reading back""")
                            .contains("Its last segment is what the copy is called");
                });
    }

    @Test
    @DisplayName("SH244: a copy never creates the branch it lands on")
    void aCopyDoesNotCreateItsDestinationParent() {
        String base = "/cp-" + UUID.randomUUID().toString().substring(0, 8);
        run(false, folder(base));

        assertThatThrownBy(() -> run(false, copy(base, "/nowhere-" + base.substring(1) + "/here")))
                .as("""
                        a move refuses this so a typo in the path is reported instead of quietly \
                        building a new branch of the tree; a copy that differed would be one \
                        vocabulary teaching two rules for one field""")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> assertThat(slugOf(thrown)).isEqualTo("unresolved-address"));
    }
}
