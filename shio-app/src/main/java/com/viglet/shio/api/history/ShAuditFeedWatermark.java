/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * What makes the SH88 change feed commit-ordered rather than stamp-ordered (SH147).
 *
 * <h2>The defect this closes</h2>
 *
 * <p>SH51 writes the audit row <b>inside</b> the writer's transaction, so its timestamp is
 * assigned before the commit. Two writers therefore stamp in one order and commit in another:
 * a transaction that stamped {@code T0} can commit <em>after</em> one that stamped {@code T1 >
 * T0} has already been read and the reader's cursor has moved past {@code T1}. The
 * {@code T0} entry is then invisible for ever, which for a sync feed is a lost edit rather
 * than a late one. SH88 shipped a lag as the mitigation — hold back anything younger than
 * {@code now - lag} — and a lag is a guess about how long a commit takes, so a bulk apply of
 * hundreds of posts outran it.</p>
 *
 * <h2>What it does instead</h2>
 *
 * <p>Two facts, and together they are exact rather than probable.</p>
 *
 * <p><b>(1) Reserved positions are strictly monotonic.</b> {@link #reserve()} — not
 * {@code Instant.now()} — is what stamps an audit row, and it never returns a value it has
 * already returned, so the audit trail's {@code (date, id)} order is a <em>total</em> order in
 * which every row was stamped. Ties disappear, which makes the SH88 keyset tie-break
 * belt-and-braces rather than load-bearing.</p>
 *
 * <p><b>(2) A reader may not pass a write that has not finished.</b> Every {@code reserve()}
 * inside a transaction registers that position as in flight and releases it when the
 * transaction completes — committed or rolled back, since either outcome means nothing more
 * will arrive at that position. {@link #ceiling()} is therefore the position of the
 * <em>oldest in-flight write</em>, exclusive, and a row only becomes reportable once every
 * write stamped before it has finished.</p>
 *
 * <p>So the knob stops being load-bearing. {@code shio.agent.changes.lag-millis} is not a
 * guess about commit duration any more; it defaults to <b>0</b> and is a margin for the one
 * thing an in-process registry cannot see — a <em>second application node</em> writing to the
 * same database. That bound is the design, not an oversight: it is stated on the property and
 * disclosed in the feed's own {@code notes} whenever an operator configures one.</p>
 *
 * <h2>Why not a sequence column with gap detection</h2>
 *
 * <p>The shape §X.3.1 sketched — a monotonic {@code seq}, a cursor that is a {@code seq}, and a
 * reader that stops at the first hole — <b>does not close the hole either</b>. A hole is either
 * a transaction still in flight or one that rolled back, and nothing in the row distinguishes
 * them: the reader has to skip after a timeout, and a transaction slower than that timeout
 * loses its edit exactly the way the lag does. It relocates the guess and renames it. The
 * registry has the fact the database does not: whether that write is still running.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAuditFeedWatermark {

    /** How far apart two reservations are pushed when the clock has not moved between them. */
    private static final long STEP_NANOS = 1_000L;

    private final Map<Object, Instant> inFlight = new ConcurrentHashMap<>();

    private final AtomicReference<Instant> lastReserved = new AtomicReference<>(Instant.EPOCH);

    /**
     * The position to stamp an audit row with, and a promise to the feed that it will not report
     * past that position until this write finishes.
     *
     * <p><b>One hold per row, not per transaction</b>, and the difference was paid for. Holding
     * only the transaction's first position is enough on paper — reservation is monotonic, so
     * the first one is the earliest and capping the feed below it caps it below all of them —
     * and it needs somewhere to record "this transaction already holds one", which means a
     * thread-bound {@code TransactionSynchronizationManager} resource. That binding is a second
     * lifetime to get right, and getting it wrong fails <em>both</em> ways: a binding that
     * outlives its transaction makes every later {@code reserve()} on that thread skip its hold
     * (the feed reports a write in flight), and it strands the hold that came with it (the feed
     * reports nothing, for ever). Both were observed, in different classes, on different runs of
     * one suite. A synchronization per row has no state outside the transaction manager's own
     * contract, which is the same contract SH12's after-commit dispatch already trusts. The cost
     * is one small object per audit row of a bulk apply, and the min over the map is the same
     * answer either way.</p>
     *
     * <p>Outside a transaction nothing is held, and that is honest rather than a gap: SH51's
     * recording is inline with the caller's write transaction by design, so a caller with no
     * transaction has a row that commits by itself in a single statement. There is no interval
     * between stamping and committing for a reader to fall into.</p>
     *
     * @return a position strictly later than every position this JVM has handed out
     */
    public Instant reserve() {
        return reserve(Instant.now());
    }

    /**
     * As {@link #reserve()}, from a clock the caller supplies.
     *
     * <p>The incident store (SH144) takes this one, because both streams are merged under one
     * SH88 cursor and a cursor cannot be commit-ordered on one of them. It arrived with the
     * ordering half of this class and none of the visibility half — SH372 gave its rows a
     * strictly increasing {@code lastSeen} so the cap would stop deleting by random uuid, from a
     * counter of its own — and the two halves are the same mechanism, so it uses this counter
     * now. What that fixed here: with the lag gone, the window cursor sits at the edge of the
     * feed, and an incident stamped from an unsynchronised clock could commit <em>behind</em> a
     * cursor a reader already holds. That was one failure in two full suite runs.</p>
     *
     * @param from the caller's own instant; the position is still never below the last one
     */
    public Instant reserve(Instant from) {
        Instant at = next(from);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return at;
        }
        Object token = hold(at);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                release(token);
            }
        });
        return at;
    }

    /**
     * Registers a position as in flight, holding the feed's ceiling below it.
     *
     * <p>Public because it is the primitive {@link #reserve()} is built from, and because it is
     * how a test makes "a write that has stamped and not committed" a fact instead of a race:
     * the state SH147 is about lasts microseconds in production and cannot be asserted on by
     * timing.</p>
     *
     * @param at the reserved position
     * @return the token to pass to {@link #release(Object)}
     */
    public Object hold(Instant at) {
        Object token = new Object();
        inFlight.put(token, at);
        return token;
    }

    /** Releases a held position, whether the write committed or rolled back. */
    public void release(Object token) {
        inFlight.remove(token);
    }

    /**
     * The newest position the feed may report, inclusive.
     *
     * <p>One <b>millisecond</b> below the oldest in-flight position, truncated to the
     * millisecond, when there is one; otherwise the later of {@code now} and the last position
     * handed out.</p>
     *
     * <p>The margin is a millisecond and not a nanosecond because the boundary is compared
     * against a value that has been <em>through the database</em>, and a timestamp column's
     * precision is not the JVM clock's: H2 and PostgreSQL keep microseconds, so a position
     * carrying hundreds of nanoseconds comes back a fraction smaller than it went in, and
     * {@code oldest - 1ns} then admits the very row it exists to hold. That reproduced as the
     * feed reporting a write whose transaction was still open — this class's own defect, reached
     * through the guard against it. Rounding down to the millisecond is safe in the
     * only direction that matters: it can hold back a row stamped in the same millisecond as an
     * in-flight write slightly longer than strictly necessary, and it cannot report one early.
     * Positions are microsecond-aligned by {@link #next()} for the same reason — a position that
     * does not survive a round trip is not a total order any more.</p>
     *
     * <p><b>And the idle branch is {@code max(now, lastReserved)} rather than {@code now}</b>,
     * which is not a nicety either: capping it at {@code now} was a defect the suite caught in
     * the same pass. A reservation is pushed a step past the clock
     * whenever the clock has not moved since the previous one, so a burst of writes leaves
     * positions <em>ahead</em> of {@code now} — for as long as it takes the clock's own
     * granularity to catch up, every one of those rows was excluded from the feed although
     * every one of them had committed. Bounding by the last reserved position is exact for the
     * same reason the hold is: a row can only exist at a position this class handed out, and
     * every position at or below {@code lastReserved} with nothing in flight has finished.</p>
     */
    public Instant ceiling() {
        Instant oldest = null;
        for (Instant at : inFlight.values()) {
            if (oldest == null || at.isBefore(oldest)) {
                oldest = at;
            }
        }
        if (oldest != null) {
            return oldest.truncatedTo(ChronoUnit.MILLIS).minusMillis(1);
        }
        Instant now = Instant.now();
        Instant last = lastReserved.get();
        return now.isAfter(last) ? now : last;
    }

    /** How many writes are stamped and not yet finished — for a test, and for nothing else. */
    public int inFlightCount() {
        return inFlight.size();
    }

    /**
     * The next position: the caller's clock, or a microsecond past the last one when that clock
     * has not moved. Truncated to the microsecond because that is the precision a column keeps
     * — a position finer than the store is one that comes back changed, and the order these are
     * handed out in is only useful if it survives being written down.
     */
    private Instant next(Instant from) {
        Instant now = from.truncatedTo(ChronoUnit.MICROS);
        while (true) {
            Instant last = lastReserved.get();
            Instant at = now.isAfter(last) ? now : last.plusNanos(STEP_NANOS);
            if (lastReserved.compareAndSet(last, at)) {
                return at;
            }
        }
    }
}
