/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentSubject;
import com.viglet.shio.api.review.ShReviewService;
import com.viglet.shio.api.review.dto.ShReviewDetail;
import com.viglet.shio.api.review.dto.ShReviewOutcome;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH196 — a site's <b>shape</b> is audited, not only its contents.
 *
 * <p>Before this, {@code delete} and {@code restore} recorded and {@code create},
 * {@code update} and {@code move} did not, so a session resuming with {@code since=<cursor>}
 * was told a post now lives at {@code folder:Blog/archive/2025} and never that the folder had
 * appeared, been renamed from {@code 2024}, or moved. Those call for different next actions,
 * and the feed could not tell them apart.</p>
 *
 * <p>Three properties, each of which was a distinct wrong answer:</p>
 * <ul>
 *   <li><b>One row per folder write, never one per post beneath it.</b> The tempting shape is an
 *       {@code UPDATE} per affected post, because that is what the feed already carried — but no
 *       post changed, and a hundred updates for one rename is what makes a consumer stop
 *       trusting the feed.</li>
 *   <li><b>The subject is a column, not a new action.</b> A rename is an {@code UPDATE} whose
 *       subject is {@code FOLDER}, with the previous name in the description.</li>
 *   <li><b>The row keeps describing the object after the object is gone.</b> The subject used to
 *       be derived by asking the folder repository, which stops working exactly where it matters
 *       — a purged section's own deletion entry was reclassified as a post.</li>
 * </ul>
 *
 * <p>{@code lag-millis=0} because every assertion here is about a write that just happened; the
 * lag is SH88's ordering guard and has its own test.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {"shio.schedule.enabled=false", "shio.agent.changes.lag-millis=0"})
@TestInstance(Lifecycle.PER_CLASS)
class ShFolderAuditTest {

    @Autowired
    private ShFolderWriteService shFolderWriteService;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShReviewService shReviewService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String rootFolderId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            siteName = "ut-folder-audit-" + UUID.randomUUID().toString().substring(0, 8);
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setUrl("http://folder-audit.example.test");
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
            rootFolderId = root.getId();
        });
    }

    @AfterEach
    void clearSession() {
        ShAgentSessionContext.clear();
    }

    // --- the writes that were silent ---

    @Test
    void createIsAuditedAsAFolderCreateThatNamesItsParent() {
        String section = createFolder(rootFolderId, "Blog");

        ShHistory row = onlyRowFor(section);
        assertThat(row.getAction()).isEqualTo(ShContentAction.CREATE.name());
        assertThat(row.getSubjectKind()).isEqualTo(ShContentSubject.FOLDER.name());
        // Where it landed, because "a section appeared" and "a section appeared *here*" are
        // different facts and only the second one lets a reader skip a re-read of the tree.
        assertThat(row.getDescription()).isEqualTo("Created \"Blog\" (in root)");
    }

    @Test
    void aRenameIsAnUpdateThatNamesThePreviousName() {
        String section = createFolder(rootFolderId, "2024");

        inTx(() -> shFolderWriteService.update(section, named("2025"), "curator"));

        ShHistory row = rowFor(section, ShContentAction.UPDATE);
        assertThat(row.getSubjectKind()).isEqualTo(ShContentSubject.FOLDER.name());
        assertThat(row.getDescription()).isEqualTo("Updated \"2025\" (renamed from \"2024\")");
    }

    @Test
    void aMoveIsAnUpdateThatNamesBothParents() {
        String from = createFolder(rootFolderId, "Drafts");
        String to = createFolder(rootFolderId, "Published");
        String section = createFolder(from, "Q1");

        inTx(() -> shFolderWriteService.move(section, folder(to), null, "curator"));

        assertThat(rowFor(section, ShContentAction.UPDATE).getDescription())
                .isEqualTo("Updated \"Q1\" (moved from \"Drafts\" to \"Published\")");
    }

    /**
     * A move that only renames says only that, and vice versa.
     *
     * <p>{@code move} is one method for two gestures, so the naive description reports both
     * every time — "moved from Blog to Blog" describes a write nobody made, which is the same
     * defect as an update per post one order of magnitude smaller.
     */
    @Test
    void aMoveReportsOnlyWhatActuallyChanged() {
        String parent = createFolder(rootFolderId, "Section");
        String renamedOnly = createFolder(parent, "Old");
        String movedOnly = createFolder(parent, "Stable");
        String elsewhere = createFolder(rootFolderId, "Elsewhere");

        inTx(() -> shFolderWriteService.move(renamedOnly, folder(parent), "New", "curator"));
        inTx(() -> shFolderWriteService.move(movedOnly, folder(elsewhere), null, "curator"));

        assertThat(rowFor(renamedOnly, ShContentAction.UPDATE).getDescription())
                .isEqualTo("Updated \"New\" (renamed from \"Old\")")
                .doesNotContain("moved");
        assertThat(rowFor(movedOnly, ShContentAction.UPDATE).getDescription())
                .isEqualTo("Updated \"Stable\" (moved from \"Section\" to \"Elsewhere\")")
                .doesNotContain("renamed");
    }

    /**
     * The headline restraint: one row for the section, none for the tree under it.
     *
     * <p>SH137 is explicit that a folder move leaves every URL and every field alone, so an
     * {@code UPDATE} per affected post would be a lie about a cheap operation — and the reader it
     * lies to is the one deciding whether to re-pull the whole subtree.</p>
     */
    @Test
    void oneRowPerFolderWriteAndNoneForTheFoldersBeneathIt() {
        String section = createFolder(rootFolderId, "Deep");
        String child = createFolder(section, "Nested");
        String grandchild = createFolder(child, "Deeper");
        int before = rowsFor(child).size() + rowsFor(grandchild).size();

        inTx(() -> shFolderWriteService.update(section, named("Deep Renamed"), "curator"));

        assertThat(rowsFor(section)).hasSize(2); // its own CREATE, then this UPDATE
        assertThat(rowsFor(child).size() + rowsFor(grandchild).size()).isEqualTo(before);
    }

    // --- what the feed and the queue now see ---

    @Test
    void theFeedCarriesTheFolderSubjectAndAFolderAddress() {
        String section = createFolder(rootFolderId, "Feedable");

        ShAgentChanges changes = shAgentChangesService.changes(siteName, null, 100, false);

        assertThat(changes.changes())
                .filteredOn(change -> "Created \"Feedable\" (in root)".equals(change.what()))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.subject()).isEqualTo("folder");
                    assertThat(change.address()).isEqualTo("folder:" + siteName + "/Feedable");
                });
        assertThat(shFolderRepository.findById(section)).isPresent();
    }

    /**
     * SH109's revert, extended: undoing a run's folder creation trashes the section, which is the
     * same shape as undoing a post's creation — recoverable (SH55), not purged.
     */
    @Test
    void theQueueUndoesAFolderCreateByTrashingIt() {
        String session = "sess-" + UUID.randomUUID();
        String section = asAgent(session, () -> createFolder(rootFolderId, "Proposed"));
        assertThat(section).isNotBlank();

        ShReviewDetail detail = shReviewService.detail(session);
        assertThat(detail.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(ShContentAction.CREATE.name());
            assertThat(entry.address()).isEqualTo("folder:" + siteName + "/Proposed");
            assertThat(entry.revertable()).isTrue();
        });

        ShReviewOutcome outcome = inTx(() -> shReviewService.revert(session, "curator"));

        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(shFolderRepository.findById(section)).isEmpty();
        assertThat(shFolderRepository.findAnyById(section)).isPresent(); // trashed, not destroyed
    }

    /** A rename has no honest undo, so it is skipped with the reason rather than approximated. */
    @Test
    void theQueueRefusesToGuessAtUndoingARename() {
        String session = "sess-" + UUID.randomUUID();
        String section = createFolder(rootFolderId, "Renameable");
        asAgent(session,
                () -> inTx(() -> shFolderWriteService.update(section, named("Renamed"), "agent")));

        ShReviewOutcome outcome = inTx(() -> shReviewService.revert(session, "curator"));

        assertThat(outcome.changed()).isZero();
        assertThat(outcome.skipped()).singleElement()
                .satisfies(skipped -> assertThat(skipped.reason()).contains("no undo"));
        assertThat(shFolderRepository.findById(section)).isPresent();
    }

    /**
     * The reason the subject is stored and not derived.
     *
     * <p>After a purge there is no folder row to ask, so the derived answer flipped to "post" and
     * the queue reported <em>the post no longer exists</em> about a section it had just watched
     * being destroyed. The stored subject survives the object.</p>
     */
    @Test
    void aPurgedSectionStillReadsAsASection() {
        String session = "sess-" + UUID.randomUUID();
        String section = asAgent(session, () -> {
            String created = createFolder(rootFolderId, "Doomed");
            inTx(() -> shFolderWriteService.delete(created, "agent"));
            return created;
        });
        inTx(() -> shFolderWriteService.purge(section));
        assertThat(shFolderRepository.findAnyById(section)).isEmpty();

        ShReviewDetail detail = shReviewService.detail(session);
        assertThat(detail.entries()).allSatisfy(entry -> {
            assertThat(entry.address()).isNull();
            assertThat(entry.revertable()).isFalse();
        });

        ShReviewOutcome outcome = inTx(() -> shReviewService.revert(session, "curator"));
        assertThat(outcome.skipped()).isNotEmpty()
                .allSatisfy(skipped -> assertThat(skipped.reason()).contains("section"));
    }

    // --- fixtures ---

    private String createFolder(String parentId, String name) {
        return inTx(() -> shFolderWriteService.create(parentId, named(name), "curator")
                .orElseThrow()
                .getId());
    }

    private ShFolder named(String name) {
        ShFolder payload = new ShFolder();
        payload.setName(name);
        return payload;
    }

    private ShFolder folder(String id) {
        return inTx(() -> shFolderRepository.findById(id).orElseThrow());
    }

    /**
     * One shape, always returning a value — a {@code Runnable} sibling would make every call site
     * whose lambda body is an expression statement ambiguous, and every write here returns
     * something.
     */
    private <T> T inTx(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    /** Runs {@code work} as an agent session, so its audit rows reach the SH109 queue. */
    private <T> T asAgent(String session, Supplier<T> work) {
        ShAgentSessionContext.set(session);
        try {
            return work.get();
        } finally {
            ShAgentSessionContext.clear();
        }
    }

    private List<ShHistory> rowsFor(String objectId) {
        return shHistoryRepository.findAll().stream()
                .filter(row -> objectId.equals(row.getShObject()))
                .toList();
    }

    private ShHistory onlyRowFor(String objectId) {
        List<ShHistory> rows = rowsFor(objectId);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    /**
     * The one row for this object with that action.
     *
     * <p>Selected by action rather than by "the latest", because two writes in the same
     * millisecond tie on {@code date} and an ordering test would be the flake — the property under
     * test is what the row <em>says</em>, not when it landed.</p>
     */
    private ShHistory rowFor(String objectId, ShContentAction action) {
        List<ShHistory> rows = rowsFor(objectId).stream()
                .filter(row -> action.name().equals(row.getAction()))
                .toList();
        assertThat(rows).as("%s rows for %s", action, objectId).hasSize(1);
        return rows.get(0);
    }
}
