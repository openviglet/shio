/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SH147 — the two properties that make the SH88 feed commit-ordered.
 *
 * <p>No Spring context on purpose: what is under test is a registry and a monotonic counter,
 * and both are assertable as values. The state SH147 is about — a write that has stamped and
 * not committed — lasts microseconds in production, so the only way to assert on it at all is
 * to hold it open deliberately. That is what {@code hold}/{@code release} are for, and the
 * transaction wiring that calls them is asserted here too rather than assumed.</p>
 *
 * @author Alexandre Oliveira
 */
class ShAuditFeedWatermarkTest {

    private final ShAuditFeedWatermark watermark = new ShAuditFeedWatermark();

    @AfterEach
    void clearAnyLeftoverSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /**
     * Ties are what the SH88 cursor's id exists to survive; SH147 removes them at the source.
     * A duplicated position is not merely untidy — it is the one case where the keyset
     * predicate has to decide by id, and an id is not the order anything happened in.
     */
    @Test
    @DisplayName("a reserved position is never handed out twice, however coarse the clock")
    void reserveIsStrictlyMonotonic() {
        List<Instant> reserved = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            reserved.add(watermark.reserve());
        }

        for (int i = 1; i < reserved.size(); i++) {
            assertThat(reserved.get(i))
                    .as("position %d must be strictly after position %d", i, i - 1)
                    .isAfter(reserved.get(i - 1));
        }
    }

    /**
     * The defect the suite caught, and the reason the idle ceiling is not simply {@code now}: a
     * burst of writes reserves faster than the clock ticks, so positions run ahead of it. Capped
     * at {@code now}, the feed then excluded rows that had all committed — for as long as the
     * clock's own granularity took to catch up, which on a fixture that writes a hundred audit
     * rows is every row it just wrote.
     */
    /**
     * The incident store supplies its own clock (SH372's frozen-instant case), and it still may
     * not go below a position already handed out — otherwise the merged feed has two orders and
     * the cursor belongs to neither.
     */
    @Test
    @DisplayName("a caller's own clock leads, and still never goes below the last position")
    void aSuppliedClockCannotGoBackwards() {
        Instant ahead = watermark.reserve(Instant.parse("2026-04-01T11:00:00Z"));
        Instant behind = watermark.reserve(Instant.parse("2020-01-01T00:00:00Z"));

        assertThat(ahead).isEqualTo(Instant.parse("2026-04-01T11:00:00Z"));
        assertThat(behind)
                .as("a clock that went backwards still yields a position after the last one")
                .isAfter(ahead);
    }

    @Test
    @DisplayName("a burst that outruns the clock is still under the ceiling")
    void positionsAheadOfTheClockAreStillReportable() {
        Instant last = null;
        for (int i = 0; i < 5000; i++) {
            last = watermark.reserve();
        }

        assertThat(watermark.ceiling()).isAfterOrEqualTo(last);
    }

    @Test
    @DisplayName("with nothing in flight the ceiling is now, so the feed is not artificially stale")
    void anIdleInstanceHasNoLag() {
        Instant before = Instant.now();

        Instant ceiling = watermark.ceiling();

        assertThat(ceiling).isBetween(before, Instant.now());
        assertThat(watermark.inFlightCount()).isZero();
    }

    /**
     * The claim SH147 is: an entry cannot be reported while a write that stamped before it is
     * still running. The ceiling is what enforces it, and it is exclusive so a row sharing the
     * held position is excluded too.
     */
    @Test
    @DisplayName("an in-flight write holds the ceiling strictly below its own position")
    void aHeldPositionCapsTheCeiling() {
        Instant at = watermark.reserve();

        Object token = watermark.hold(at);
        assertThat(watermark.ceiling()).isBefore(at);

        watermark.release(token);
        assertThat(watermark.ceiling()).isAfterOrEqualTo(at);
    }

    /**
     * The boundary is compared against a value that has been through the database, and a
     * timestamp column keeps microseconds where the JVM clock offers nanoseconds. A margin of
     * one nanosecond therefore admitted the row it existed to hold — a real regression, caught
     * by {@code ShAgentCommitOrderTest} and asserted here where the cause is visible.
     */
    @Test
    @DisplayName("the held boundary survives a stored timestamp's precision")
    void theBoundaryIsCoarserThanTheStore() {
        Instant at = Instant.parse("2026-04-01T11:00:00.123456789Z");

        watermark.hold(at);

        assertThat(watermark.ceiling())
                .as("a row stamped at that position comes back truncated, and must still be held")
                .isBefore(at.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("a reserved position is microsecond-aligned, so it round-trips unchanged")
    void positionsAreAlignedToWhatAColumnKeeps() {
        for (int i = 0; i < 100; i++) {
            Instant at = watermark.reserve();
            assertThat(at).isEqualTo(at.truncatedTo(ChronoUnit.MICROS));
        }
    }

    @Test
    @DisplayName("the ceiling follows the oldest in-flight write, not the newest")
    void theOldestInFlightWriteWins() {
        Instant first = watermark.reserve();
        Instant second = watermark.reserve();
        Object older = watermark.hold(first);
        Object newer = watermark.hold(second);

        assertThat(watermark.ceiling()).isBefore(first);

        // Releasing the newer one changes nothing: the older write is still the one a reader
        // must not pass, and reporting past it is exactly the lost edit.
        watermark.release(newer);
        assertThat(watermark.ceiling()).isBefore(first);

        watermark.release(older);
        assertThat(watermark.ceiling()).isAfterOrEqualTo(second);
    }

    /**
     * A rollback releases the hold as surely as a commit does. If it did not, one rolled-back
     * write would freeze the feed for the lifetime of the process — which is the failure mode
     * the {@code seq}-plus-gap-detection design has to paper over with a timeout.
     */
    @Test
    @DisplayName("the hold is released on completion, committed or rolled back")
    void completionReleasesTheHold() {
        for (int status : new int[] {TransactionSynchronization.STATUS_COMMITTED,
                                    TransactionSynchronization.STATUS_ROLLED_BACK}) {
            beginTransaction();
            Instant at = watermark.reserve();

            assertThat(watermark.inFlightCount()).isOne();
            assertThat(watermark.ceiling()).isBefore(at);

            completeTransaction(status);

            assertThat(watermark.inFlightCount())
                    .as("status %d must release the hold", status)
                    .isZero();
            assertThat(watermark.ceiling()).isAfterOrEqualTo(at);
        }
    }

    /**
     * Every row of a transaction is held, and all of them are released together. Holding only
     * the first would be enough arithmetically — it is the earliest, so it caps the feed below
     * the rest — and it would need a thread-bound marker saying "already held", which is a
     * lifetime that leaked both ways in practice. Per row, the only state is the transaction
     * manager's own synchronization list.
     */
    @Test
    @DisplayName("every row of a transaction is held, and released with it")
    void everyRowIsHeldAndReleasedTogether() {
        beginTransaction();
        Instant first = watermark.reserve();
        watermark.reserve();
        Instant last = watermark.reserve();

        assertThat(watermark.inFlightCount()).isEqualTo(3);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(3);
        assertThat(watermark.ceiling())
                .as("the earliest row of the transaction is the one that caps the feed")
                .isBefore(first);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(watermark.inFlightCount()).isZero();
        assertThat(watermark.ceiling()).isAfterOrEqualTo(last);
    }

    /**
     * Outside a transaction there is nothing to wait for: the row commits by itself in one
     * statement, so there is no interval between stamping and committing for a reader to fall
     * into. Holding anyway would be a hold nothing ever releases.
     */
    @Test
    @DisplayName("a reservation outside a transaction holds nothing")
    void noTransactionNoHold() {
        Instant at = watermark.reserve();

        assertThat(watermark.inFlightCount()).isZero();
        assertThat(watermark.ceiling()).isAfterOrEqualTo(at);
    }

// --- noticing a second writer (SH376) ---

    /**
     * The detector, and the reason it needs no new state: positions are monotonic and never below
     * the clock, so a row above the highest one this JVM issued cannot have come from it.
     */
    @Test
    @DisplayName("a position above the last one issued is proof of another writer")
    void aForeignPositionIsNoticed() {
        Instant mine = watermark.reserve();
        assertThat(watermark.foreignWriteSeen()).isFalse();

        watermark.observe(mine.plusMillis(2500));

        assertThat(watermark.foreignWriteSeen()).isTrue();
    }

    @Test
    @DisplayName("this node's own rows are never mistaken for another writer's")
    void ownPositionsAreNotForeign() {
        Instant first = watermark.reserve();
        Instant second = watermark.reserve();

        watermark.observe(first);
        watermark.observe(second);

        assertThat(watermark.foreignWriteSeen()).isFalse();
    }

    /**
     * Before this JVM has issued anything, `lastReserved` is the epoch and every row already in the
     * table sits above it — reporting a second writer there would fire on every single-node
     * instance that had just restarted.
     */
    @Test
    @DisplayName("a fresh process reports nothing until it has issued a position")
    void nothingIsForeignBeforeTheFirstReserve() {
        watermark.observe(Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(watermark.foreignWriteSeen()).isFalse();
    }

    @Test
    @DisplayName("the observation is sticky, so the disclosure cannot flicker")
    void theObservationIsSticky() {
        Instant mine = watermark.reserve();

        watermark.observe(mine.plusMillis(4000));
        // A fact about the installation does not stop being true because the next page happens not
        // to contain a foreign row — and a note that flickered would stop the one endpoint whose
        // job is to be polled from ever answering 304 (SH159).
        watermark.observe(mine);
        watermark.observe(null);

        assertThat(watermark.foreignWriteSeen()).isTrue();
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private static void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        for (TransactionSynchronization synchronization : synchronizations) {
            synchronization.afterCompletion(status);
        }
    }
}
