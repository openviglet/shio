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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
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
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH987 — the revision store, reachable from the surfaces an agent actually has.
 *
 * <p>SH986 captured what a page was; until this, nothing could read one. The state parameter
 * reached the draft and the live row and nothing else, no listing existed on REST or MCP, and the
 * only rollback was "read the old body and upsert it", which loses the fact that it was a rollback
 * and re-sends bytes the server already holds.</p>
 *
 * <p>The claims here are the P1 chain's: the list is on the read that already takes an address,
 * a past version is a value of the parameter that already says which version, the rollback is a
 * verb on the vocabulary rather than a tool — and <b>a caller that asks for none of it pays
 * nothing</b>, which is the half an opt-in is only worth if it holds.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentRevisionSurfaceTest {

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShPostRevisionService shPostRevisionService;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShPostRevisionRepository shPostRevisionRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String siteName;
    private String folderId;
    private String postTypeId;
    private String postId;
    private String address;
    private final Set<String> written = new HashSet<>();

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "rev-surface-" + UUID.randomUUID();
            site.setName(siteName);
            site.setDescription("Revision surface test site");
            site.setUrl("http://example.test");
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
            folderId = root.getId();

            ShWidget text = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());

            ShPostType postType = new ShPostType();
            postType.setName("PT-REVSURF-" + UUID.randomUUID());
            postType.setTitle("Revision Surface Type");
            postType.setDescription("Post type for the revision surface test");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");

            ShPostTypeAttr title = new ShPostTypeAttr();
            title.setName(ShSystemPostTypeAttr.TITLE);
            title.setLabel("Title");
            title.setDescription("Title field");
            title.setOrdinal(1);
            title.setRequired((byte) 1);
            title.setIsTitle((byte) 1);
            title.setIsSummary((byte) 0);
            title.setShWidget(text);
            title.setShPostType(postType);
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(title);
            postType.setShPostTypeAttrs(attrs);
            entityManager.persist(postType);
            postTypeId = postType.getId();
        });

        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data("First"), ShPostState.DRAFT, "admin", null, null,
                "/first-page"));
        postId = created.getId();
        written.add(postId);
        address = "post:" + siteName + "/first-page";

        shPostUnifiedWriteService.publish(postId, "admin");
        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Second"), ShPostState.DRAFT, "admin"));
        shPostUnifiedWriteService.publish(postId, "admin");
    }

    @AfterAll
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> written.forEach(id -> {
            shPostRevisionRepository.deleteAll(
                    shPostRevisionRepository.findByPostIdOrderByVersionNoDesc(id));
            shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
        }));
    }

    private ObjectNode data(String title) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(ShSystemPostTypeAttr.TITLE, title);
        return node;
    }

    private ShAgentDoc read(String state, boolean revisions) {
        return shAgentReadService.read(List.of(address), null, false, false, state, false, false,
                revisions).getFirst();
    }

    @Test
    @DisplayName("SH987: a read that does not ask for the history is the read it was before")
    void anOrdinaryReadIsUnchanged() {
        ShAgentDoc plain = read(null, false);

        assertThat(plain.revisions())
                .as("""
                        SH987: the whole claim of an opt-in is that the caller who does not ask pays \
                        nothing. This post has two revisions and an ordinary read must not mention \
                        them — absent, not empty, which is SH190's rule and what keeps the line \
                        byte-identical.""")
                .isNull();
        assertThat(ShAgentReadTerse.docs(List.of(plain)))
                .as("SH987: and the terse line carries no revision rows either")
                .doesNotContain("revision ");
    }

    @Test
    @DisplayName("SH987: include=revisions lists what the page was, without the bodies")
    void theHistoryIsListedWhenAsked() {
        ShAgentDoc asked = read(null, true);

        assertThat(asked.revisions())
                .as("SH987: two publishes, two captured revisions")
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.reason()).isEqualTo("PUBLISH"));
        assertThat(asked.revisions().getFirst().version())
                .isGreaterThan(asked.revisions().getLast().version());
        assertThat(asked.revisions()).allSatisfy(row -> {
            assertThat(row.data())
                    .as("""
                            SH987: a listing carries no bodies. Fifty revisions of a page with a \
                            long body is the page fifty times for a question the numbers, the dates \
                            and the changed field names answer (P3).""")
                    .isNull();
            assertThat(row.digest()).isNotBlank();
            assertThat(row.capturedAt()).isNotNull();
        });
        assertThat(asked.revisions().getFirst().changed())
                .as("SH987: what this revision altered, by name and never by value")
                .contains(ShSystemPostTypeAttr.TITLE);

        assertThat(ShAgentReadTerse.docs(List.of(asked)))
                .as("SH987: the terse form says it too, one line per revision")
                .contains("revision ");
    }

    @Test
    @DisplayName("SH987: state=rev:<n> reads what the page said then")
    void aPastVersionIsReadable() {
        int oldest = shPostRevisionService.rows(postId).getLast().version();

        assertThat(read("rev:" + oldest, false).data().toString())
                .as("""
                        SH987: the same parameter that says draft or published says which captured \
                        version — a caller with `state=published` in a script changes one value to \
                        read what was published before it.""")
                .contains("First");
        assertThat(read(null, false).data().toString())
                .as("SH987: and the ordinary read still answers with what is there now")
                .contains("Second");
    }

    @Test
    @DisplayName("SH987: a number nothing captured is refused with the numbers that exist")
    void anUnknownRevisionIsRefusedWithWhatThereIs() {
        assertThatThrownBy(() -> read("rev:99", false))
                .as("""
                        SH987: a caller that asked for a revision has a list from somewhere, and \
                        the two ways to be wrong — the prune removed it, or that number belongs to \
                        another post — are told apart by seeing what is there (P6).""")
                .isInstanceOf(com.viglet.shio.api.post.unified.ShPostStateNotFoundException.class)
                .hasMessageContaining("revision");
    }

    @Test
    @DisplayName("SH987: post.rollback plans, then restores into the draft")
    void theRollbackIsAVerbOnTheVocabulary() {
        String rolled = publishedTwice("rolled-back-page");
        String at = "post:" + siteName + "/rolled-back-page";
        int oldest = shPostRevisionService.rows(rolled).getLast().version();

        var plan = shAgentBatchService.run(new ShAgentBatch(true, List.of(rollback(at, oldest))),
                false, "ut-sh987");
        assertThat(plan.results().getFirst().detail())
                .as("""
                        SH987: the plan reads the revision rather than answering "would roll back", \
                        which is SH690's rule — a plan that says yes about a revision the write \
                        cannot find is the plan an agent acts on.""")
                .contains("restore revision " + oldest);

        shAgentBatchService.run(new ShAgentBatch(false, List.of(rollback(at, oldest))), false,
                "ut-sh987");

        assertThat(shPostUnifiedRepository.findByIdAndState(rolled, ShPostState.DRAFT))
                .hasValueSatisfying(draft -> assertThat(draft.getData()).contains("First"));
        assertThat(shPostUnifiedRepository.findByIdAndState(rolled, ShPostState.PUBLISHED))
                .as("""
                        SH987: it restores into DRAFT and never publishes — P7 as mechanics rather \
                        than convention, so what visitors are reading is untouched until somebody \
                        says so.""")
                .hasValueSatisfying(live -> assertThat(live.getData()).contains("Second"));
    }

    @Test
    @DisplayName("SH987: a rollback with no revision is refused before anything is resolved")
    void aRollbackWithoutARevisionIsRefused() {
        assertThatThrownBy(() -> shAgentBatchService.run(
                new ShAgentBatch(true, List.of(rollback(address, null))), false, "ut-sh987"))
                .as("""
                        SH987: the shared missing-key refusal (SH332), and it fires before the \
                        address is resolved — a caller that got both wrong is otherwise told about \
                        the address, which is the error it can do least about, since the numbers it \
                        needs come from reading that address.""")
                // The batch wraps a per-op refusal, so the type asserted is the teaching one and
                // the instruction is what is read — which is also what a caller sees.
                .isInstanceOf(com.viglet.shio.api.error.ShTeachingException.class)
                .hasMessageContaining("include=revisions");
    }

    private ShAgentBatch.Op rollback(String at, Integer revision) {
        return new ShAgentBatch.Op(ShAgentBatchService.POST_ROLLBACK, at, null, null, null,
                null, null, null, null, null, null, revision);
    }

    /**
     * A post of this test's own, published twice.
     *
     * <p>The tests that write get one each rather than sharing the fixture's: a rollback changes
     * what the next read sees, and a suite whose assertions depend on which method ran first is a
     * suite that goes red for the wrong reason on a machine with a different order.</p>
     */
    private String publishedTwice(String slug) {
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data("First"), ShPostState.DRAFT, "admin", null, null,
                "/" + slug));
        written.add(created.getId());
        shPostUnifiedWriteService.publish(created.getId(), "admin");
        shPostUnifiedWriteService.update(created.getId(),
                new UpdateRequest(data("Second"), ShPostState.DRAFT, "admin"));
        shPostUnifiedWriteService.publish(created.getId(), "admin");
        return created.getId();
    }
}
