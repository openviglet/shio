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
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * The undo the protocol already promised (SH222).
 *
 * <p>{@code post.delete} answers {@code "to trash (restorable)"} on purpose — an agent that believes
 * it destroyed something will not offer the human the undo. That string was the <b>only</b> place
 * the trash appeared on the agent surface: no read listed it, no MCP tool reached it, no CLI command
 * restored it. So the protocol told a caller an undo existed and gave it no way to get there, which
 * inverts P1 on the capability an agent needs most immediately after a mistake — and it is where
 * "the console can do it" is least comforting, because the mistake is usually noticed by the agent,
 * seconds later.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh222;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentTrashTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;

    @BeforeAll
    void setup() {
        siteName = "sh222-" + UUID.randomUUID().toString().substring(0, 8);
        postTypeName = "Sh222Page" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(siteName);
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

            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseThrow();

            ShPostType type = new ShPostType();
            type.setName(postTypeName);
            type.setTitle(postTypeName);
            type.setSystem((byte) 0);
            type.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName("title");
            attr.setLabel("Title");
            attr.setShPostType(type);
            attr.setShWidget(widget);
            attr.setIsTitle((byte) 1);
            attr.setIsSummary((byte) 0);
            attr.setRequired((byte) 1);
            attr.setOrdinal(1);
            entityManager.persist(attr);
        });
    }

    @Test
    @DisplayName("SH222: delete, list the trash, restore — the round trip the promise implied")
    void theUndoIsReachable() {
        String address = "post:" + siteName + "/regretted-" + UUID.randomUUID().toString().substring(0, 8);
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                new ObjectMapper().readTree("{\"title\":\"Regretted\"}")));
        // SH108 — a delete is destructive and takes the plan it was shown. Two steps here for the
        // same reason a caller has two: the confirm token is what makes the trash a decision.
        var plan = shAgentBatchService.run(new ShAgentBatch(true,
                List.of(new Op(ShAgentBatchService.POST_DELETE, address, null, null, null))),
                false, "tester");
        shAgentBatchService.run(new ShAgentBatch(false,
                List.of(new Op(ShAgentBatchService.POST_DELETE, address, null, null, null)),
                plan.confirm()), false, "tester");

        // The read half. `find?trash=true` answers with `id:` addresses, which is the only form a
        // trashed post has — it no longer has a reachable path, which is what SH172's fallback is
        // for and why the listing had to carry it.
        ShAgentFound trash = shAgentReadService.find(siteName, null, null, null, 50, false, null, true);
        String id = trash.results().stream()
                .filter(row -> "Regretted".equals(row.title()))
                .map(ShAgentFound.Row::address)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the deleted post is not in the trash listing"));

        assertThat(id)
                .as("""
                        a trashed post is addressed by id, because it has no path any more — and \
                        the listing that names it is the same `find` that lists live content, not \
                        a fourth read (SH222).""")
                .startsWith("id:");

        // The write half.
        var restored = run(new Op(ShAgentBatchService.POST_RESTORE, id, null, null, null));

        assertThat(restored.results()).singleElement()
                .satisfies(change -> assertThat(change.detail()).isEqualTo("from trash"));
        assertThat(shAgentReadService.find(siteName, null, null, null, 50, false, null, false)
                .results())
                .as("""
                        and it is live again: the promise `post.delete` makes on every call is now \
                        one the same surface can keep (SH222).""")
                .extracting(ShAgentFound.Row::title)
                .contains("Regretted");
    }

    @Test
    @DisplayName("SH222: the trash listing is empty of what was never deleted")
    void theListingIsNotEverything() {
        // A listing that answered with live content would make the restore an accident waiting for
        // somebody to run it over a page nobody deleted.
        String address = "post:" + siteName + "/kept-" + UUID.randomUUID().toString().substring(0, 8);
        run(new Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                new ObjectMapper().readTree("{\"title\":\"Kept\"}")));

        assertThat(shAgentReadService.find(siteName, null, null, null, 50, false, null, true)
                .results())
                .extracting(ShAgentFound.Row::title)
                .doesNotContain("Kept");
    }

    @Test
    @DisplayName("SH222: a restore addressed by path is refused, and says where to get the id")
    void aPathIsRefused() {
        // Resolved leniently, this would restore whichever LIVE post happens to share the path —
        // so the refusal is the safe answer, and P6 makes it name the call that hands out ids.
        assertThatThrownBy(() -> run(new Op(ShAgentBatchService.POST_RESTORE,
                "post:" + siteName + "/regretted", null, null, null)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(thrown -> assertThat(thrown.getMessage()).contains("trash=true"));
    }

    @Test
    @DisplayName("SH222: restoring what is not in the trash is a noop, not a failure")
    void restoringNothingIsANoop() {
        // Already restored, or purged. Both are states the caller wanted to reach, and a 4xx there
        // would make the retry of a half-finished undo look like a new problem.
        assertThat(run(new Op(ShAgentBatchService.POST_RESTORE,
                "id:" + UUID.randomUUID(), null, null, null)).results())
                .singleElement()
                .satisfies(change -> assertThat(change.detail()).isEqualTo("not in the trash"));
    }

    @Test
    @DisplayName("SH222: a dry run says whether the restore would find anything")
    void aPlanReadsTheTrash() {
        // A plan that answered "would restore" about a post nobody trashed is the plan an agent
        // acts on. So the dry run reads the trash rather than assuming its own success.
        assertThat(shAgentBatchService.run(new ShAgentBatch(true, List.of(
                new Op(ShAgentBatchService.POST_RESTORE, "id:" + UUID.randomUUID(),
                        null, null, null))), false, "tester").results())
                .singleElement()
                .satisfies(change -> assertThat(change.detail()).isEqualTo("not in the trash"));
    }

    // --- SH690: the same question asked of the folder half ---

    @Test
    @DisplayName("SH690: a folder.restore plan reads the trash instead of answering yes about nothing")
    void aFolderPlanReadsTheTrashToo() {
        // The branch this task exists for: `folderRestore` answered UPDATE "from trash, with its
        // subtree" unconditionally, one method below the post half that reads the trash and says
        // why. Before the fix this asserts, it said yes about an id nobody has ever seen.
        assertThat(plan(new Op(ShAgentBatchService.FOLDER_RESTORE, "id:" + UUID.randomUUID(),
                null, null, null)).results())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.action()).isEqualTo(Change.NOOP);
                    assertThat(change.detail()).isEqualTo("not in the trash");
                });
    }

    @Test
    @DisplayName("SH690: a trashed parent is its own reason in the plan, not 'not in the trash'")
    void aTrashedParentIsItsOwnReasonInThePlan() {
        String section = "sh690-" + UUID.randomUUID().toString().substring(0, 8);
        run(new Op(ShAgentBatchService.FOLDER_UPSERT,
                "folder:" + siteName + "/" + section, null, null, null));
        String childId = runWithIds(new Op(ShAgentBatchService.FOLDER_UPSERT,
                "folder:" + siteName + "/" + section + "/inner", null, null, null))
                .results().get(0).id();
        assertThat(childId).as("fixture: the nested folder was created and has an id").isNotNull();
        assertThat(confirmed(new Op(ShAgentBatchService.FOLDER_DELETE,
                "folder:" + siteName + "/" + section, null, null, null)).results())
                .as("fixture: the section really went to the trash, taking `inner` with it")
                .singleElement()
                .satisfies(change -> assertThat(change.action()).isEqualTo(Change.DELETE));

        // SH200 refuses this one: restoring it would attach a live folder to a deleted one, which
        // is invisible in navigation. The caller has something to do about it — restore the parent
        // — so the plan says which, in the sentence ShReviewService.revertFolder already prints.
        assertThat(plan(new Op(ShAgentBatchService.FOLDER_RESTORE, "id:" + childId,
                null, null, null)).results())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.action()).isEqualTo(Change.NOOP);
                    assertThat(change.detail())
                            .isEqualTo("its parent folder is in the trash — restore that instead");
                });
    }

    @Test
    @DisplayName("SH690: the plan refuses a subtree whose URL was taken, the way the write does")
    void aTakenUrlRefusesThePlanAndNotOnlyTheWrite() {
        String section = "sh690-" + UUID.randomUUID().toString().substring(0, 8);
        String furl = "sh690-taken-" + UUID.randomUUID().toString().substring(0, 8);
        run(new Op(ShAgentBatchService.FOLDER_UPSERT,
                "folder:" + siteName + "/" + section, null, null, null));
        run(new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + "/" + furl,
                postTypeName, "/" + section, new ObjectMapper().readTree("{\"title\":\"Sleeping\"}")));
        String sectionId = confirmed(new Op(ShAgentBatchService.FOLDER_DELETE,
                "folder:" + siteName + "/" + section, null, null, null)).results().get(0).id();
        // A live post moves onto the URL while the section sleeps. SH243 makes that refuse the
        // whole subtree with a 409 — so a plan answering UPDATE spends a confirm token to reach a
        // different answer, which is the round trip P9 says the textual proof replaces.
        run(new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + "/" + furl,
                postTypeName, "/", new ObjectMapper().readTree("{\"title\":\"Occupant\"}")));

        assertThatThrownBy(() -> plan(new Op(ShAgentBatchService.FOLDER_RESTORE,
                "id:" + sectionId, null, null, null)))
                .as("""
                        the plan raises SH243's refusal rather than reporting it: a batch op that \
                        will be refused is refused when it is planned (SH690).""")
                .hasMessageContaining(furl);
    }

    private com.viglet.shio.api.agent.dto.ShAgentChangeSet run(Op op) {
        return shAgentBatchService.run(new ShAgentBatch(false, List.of(op)), false, "tester");
    }

    /**
     * The dry run of one op — the document this task is about.
     *
     * <p>{@code ids = true}, unlike {@link #run}: {@code Session.change} nulls the id when the
     * caller did not ask for one, and a folder in the trash is addressable <em>only</em> by id
     * (SH222), so the fixtures below have no other way to name what they just trashed.</p>
     */
    private com.viglet.shio.api.agent.dto.ShAgentChangeSet plan(Op op) {
        return shAgentBatchService.run(new ShAgentBatch(true, List.of(op)), true, "tester");
    }

    /** A destructive op and the confirm token from its own plan (SH108). */
    private com.viglet.shio.api.agent.dto.ShAgentChangeSet confirmed(Op op) {
        return shAgentBatchService.run(
                new ShAgentBatch(false, List.of(op), plan(op).confirm()), true, "tester");
    }

    /** A write whose reply has to carry the id, for the same reason {@link #plan} does. */
    private com.viglet.shio.api.agent.dto.ShAgentChangeSet runWithIds(Op op) {
        return shAgentBatchService.run(new ShAgentBatch(false, List.of(op)), true, "tester");
    }
}
