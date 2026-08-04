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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.history.ShAuditFeedWatermark;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH147 — the change feed is commit-ordered, not stamp-ordered.
 *
 * <p>SH88 shipped a lag as the guard and said out loud that it did not close the hole: audit
 * rows are stamped inside the writer's transaction, so a transaction that stamped earlier can
 * commit after a faster one has been read, and a cursor past the faster one never sees it.
 * That is a <b>lost edit</b> on a feed whose whole job is to be the delta.</p>
 *
 * <p>Two claims are asserted here, and they are different claims. The first is about the
 * <em>ordering</em>: a held position keeps the feed's ceiling below it, so nothing stamped
 * after an unfinished write is reported and the cursor cannot advance past it — that is the
 * scenario the lag could only make unlikely, reproduced exactly rather than by timing. The
 * second is about the <em>wiring</em>: a real write through the real batch service registers
 * that hold, which is the half a registry test cannot see and the half that rots if
 * {@code ShContentAuditService} ever goes back to {@code Instant.now()}.</p>
 *
 * <p>The <b>shared</b> context, deliberately: every read here names its own site, so another
 * class's rows are another site's rows and cannot reach these assertions. Its own database would
 * be a forty-first application context in a suite that already holds forty, and the ones past
 * Spring's context-cache ceiling get evicted and rebuilt — which in this suite is not a slower
 * run but a red one, in classes that have nothing to do with the change. Isolation is for a test
 * that reads the <em>instance</em> (SH315); this one does not.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentCommitOrderTest {

    /** Old enough that no margin under test can reach them. */
    private static final Instant EARLIER = Instant.parse("2026-04-01T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-04-01T12:00:00Z");

    /** Between the two: where the write that commits slowly stamped itself. */
    private static final Instant IN_FLIGHT = Instant.parse("2026-04-01T11:00:00Z");

    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private ShAuditFeedWatermark watermark;
    @Autowired
    private ShAgentProperties properties;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String siteId;

    /**
     * Whatever hold the current test took, so a failed assertion cannot leave one behind. On a
     * shared context a leaked hold freezes the feed for every class that runs after this one,
     * which would be this test breaking other tests — the worst kind of guard.
     */
    private Object heldPosition;

    @BeforeAll
    void setup() {
        inTransaction(() -> {
            siteName = "sh147-" + UUID.randomUUID().toString().substring(0, 8);
            siteId = persistSite(siteName);
            return null;
        });
        inTransaction(() -> {
            // Written by hand, and with fixed instants, for the reason SH88's own test gives:
            // every property here is about *positions*, and a fixture that ages mid-run is a
            // flake rather than a test.
            record(ShContentAction.CREATE, EARLIER, "aaaa", "Created \"Early page\"");
            record(ShContentAction.PUBLISH, LATER, "cccc", "Published \"Late page\"");
            return null;
        });
    }

    @AfterEach
    void leaveTheSharedContextAsItWasFound() {
        properties.getChanges().setLagMillis(0);
        // SH376 — the observation is sticky and the bean is a singleton, so a test that sets it in a
        // shared context would put the disclosure on every later test's feed.
        watermark.forgetForeignWrite();
        if (heldPosition != null) {
            watermark.release(heldPosition);
            heldPosition = null;
        }
    }

    /**
     * The whole of SH147, as the sequence that used to lose an edit.
     *
     * <p>Causality matters to the fixture: the slow write stamps <em>before</em> the fast one,
     * because reservation is monotonic — so by the time the fast row exists at all, the slow
     * one is already in flight. Without the watermark the first read below returns the late row
     * and hands back a cursor past it, and the slow row — committing afterwards at an earlier
     * position — is then invisible for ever.</p>
     */
    @Test
    @DisplayName("nothing stamped after an unfinished write is reported, and the cursor waits too")
    void anUnfinishedWriteHoldsTheFeedBehindIt() {
        heldPosition = watermark.hold(IN_FLIGHT);

        ShAgentChanges held = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(descriptions(held))
                .as("the late row committed first, but it is stamped after a write still running")
                .containsExactly("Created \"Early page\"");
        assertThat(ShAgentChangeCursor.parse(held.cursor()).at())
                .as("a cursor past the in-flight position is what makes the loss permanent")
                .isBefore(IN_FLIGHT);

        // The slow transaction finally commits, at the position it reserved long before.
        inTransaction(() -> {
            record(ShContentAction.UPDATE, IN_FLIGHT, "bbbb", "Updated \"Early page\" slowly");
            return null;
        });
        watermark.release(heldPosition);
        heldPosition = null;

        assertThat(descriptions(shAgentChangesService.changes(siteName, held.cursor(), 50, false)))
                .as("both rows arrive, oldest first, and neither is lost")
                .containsExactly("Updated \"Early page\" slowly", "Published \"Late page\"");
    }

    /**
     * The wiring, end to end: a real write registers the hold, so the feed does not report the
     * change while the transaction that made it is still open. Read from <em>inside</em> that
     * transaction on purpose — the row is already flushed and readable there, so the only thing
     * that can be keeping it out of the feed is the watermark.
     */
    @Test
    @DisplayName("a write in progress is not in the feed, and is the moment it commits")
    void aRealWriteHoldsTheFeedForAsLongAsItRuns() {
        String folder = "sh147-inflight-" + UUID.randomUUID().toString().substring(0, 8);

        inTransaction(() -> {
            shAgentBatchService.run(new ShAgentBatch(false, List.of(
                    new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + "/" + folder,
                            null, null, null))), true, "tester");

            assertThat(watermark.inFlightCount())
                    .as("the write's own audit row registered a hold, which is the wiring")
                    .isPositive();
            assertThat(descriptions(shAgentChangesService.changes(siteName, null, 50, false)))
                    .as("the audit row is flushed and readable here; the feed still may not have it")
                    .noneMatch(what -> what.contains(folder));
            return null;
        });

        assertThat(descriptions(shAgentChangesService.changes(siteName, null, 50, false)))
                .as("and the moment the transaction completes, it is reportable")
                .anyMatch(what -> what.contains(folder));
    }

    /**
     * P3: the disclosure exists only when there is something to disclose. A configured margin
     * is a fact about the deployment the caller cannot otherwise see; a default install has no
     * margin and pays no prose for one.
     */
    @Test
    @DisplayName("the margin is disclosed when configured and silent when it is zero")
    void onlyAConfiguredMarginIsDisclosed() {
        assertThat(shAgentChangesService.changes(siteName, null, 50, false).notes())
                .noneMatch(note -> note.contains("held back"));

        properties.getChanges().setLagMillis(250);

        assertThat(shAgentChangesService.changes(siteName, null, 50, false).notes())
                .anyMatch(note -> note.contains("250ms are also held back"));
    }

    /**
     * SH376 — the guard is exact for one JVM and degrades against a second application node, and
     * the defect was that nothing said so. A row stamped above the highest position this JVM has
     * issued cannot have come from it, which is proof of another writer and needs no new state.
     */
    @Test
    @DisplayName("a row this node never stamped is noticed and disclosed")
    void aForeignWriteIsDisclosed() {
        watermark.forgetForeignWrite();
        // A position this node issued, so the detector is armed: before the first reserve every row
        // already in the table sits above the epoch and would read as foreign.
        Instant mine = watermark.reserve();
        assertThat(shAgentChangesService.changes(siteName, null, 50, false).notes())
                .as("nothing to disclose on a single instance, so nothing is said (P3)")
                .noneMatch(note -> note.contains("another application node"));

        // Stamped a millisecond above anything this node handed out — which is what a second node
        // writing to the same database looks like from here. Then wait for the clock to pass it, so
        // it is under the feed's ceiling and actually gets reported: a row the feed never returns is
        // a row the feed never observes, and that ordering is the point of the wiring.
        Instant foreign = mine.plusMillis(1);
        inTransaction(() -> {
            record(ShContentAction.UPDATE, foreign, "eeee", "Updated \"Early page\" elsewhere");
            return null;
        });
        while (!Instant.now().isAfter(foreign)) {
            Thread.onSpinWait();
        }

        ShAgentChanges seen = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(descriptions(seen))
                .as("the row is reported — observing it is not the same as hiding it")
                .contains("Updated \"Early page\" elsewhere");
        assertThat(seen.notes())
                .anyMatch(note -> note.contains("another application node")
                        && note.contains("lag-millis"));
        // And it stays said, because an installation does not stop having two nodes.
        assertThat(shAgentChangesService.changes(siteName, null, 50, false).notes())
                .anyMatch(note -> note.contains("another application node"));
    }

    /** A configured margin means the operator already knows, so the advice is not repeated. */
    @Test
    @DisplayName("the disclosure is silent once a margin is set")
    void aConfiguredMarginSilencesTheAdvice() {
        watermark.forgetForeignWrite();
        watermark.observe(watermark.reserve().plusSeconds(1));
        properties.getChanges().setLagMillis(1000);

        assertThat(shAgentChangesService.changes(siteName, null, 50, false).notes())
                .noneMatch(note -> note.contains("another application node"));
    }

    // --- fixture ---

    private List<String> descriptions(ShAgentChanges changes) {
        return changes.changes().stream().map(ShAgentChanges.Change::what).toList();
    }

    private String persistSite(String name) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(EARLIER);
        site.setOwner("admin");
        entityManager.persist(site);

        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(EARLIER);
        root.setOwner("admin");
        entityManager.persist(root);
        return site.getId();
    }

    private void record(ShContentAction action, Instant at, String idPrefix, String description) {
        ShHistory entry = new ShHistory();
        entry.setId(idPrefix + "-0000-4000-8000-" + UUID.randomUUID().toString().substring(0, 12));
        entry.setAction(action.name());
        entry.setShObject("sh147-object-" + idPrefix);
        entry.setShSite(siteId);
        entry.setOwner("tester");
        entry.setDate(at);
        entry.setDescription(description);
        shHistoryRepository.save(entry);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
