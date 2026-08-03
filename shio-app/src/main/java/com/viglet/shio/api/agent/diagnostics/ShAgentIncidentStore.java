/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.persistence.model.agent.ShAgentIncident;
import com.viglet.shio.persistence.repository.agent.ShAgentIncidentRepository;

/**
 * The durable half of the incident log (SH144) — persistence, folding and the cap.
 *
 * <h2>Two rules that are the whole reason this is a separate class</h2>
 *
 * <p><b>It writes in its own transaction.</b> Every caller is on a failure path, and several
 * of them are inside a transaction that is <em>about to roll back</em> — the 5xx filter's
 * request, a scheduled publish that threw. An incident row enlisted in that transaction would
 * be rolled back with it, so the one record of the failure would be destroyed by the failure.
 * {@link Propagation#REQUIRES_NEW} is therefore not a detail; it is the feature.</p>
 *
 * <p><b>It never throws.</b> SH103's objection to a table was "a write on the failure path,
 * where the code is by definition already having a bad time", and the answer is not to avoid
 * the write but to make it incapable of making things worse: anything the persistence layer
 * raises is caught and logged, because a diagnostics store that turned a webhook timeout into
 * a 500 would be a strictly worse outcome than forgetting the incident.</p>
 *
 * <h2>The cap, and why it is reported</h2>
 *
 * <p>Folding bounds the table by the number of <em>distinct</em> problems, which is usually
 * small. It does not bound the pathological case — a subject carrying an id, so every
 * occurrence is its own row — so a cap drops the least recently seen. That is a retention
 * policy, which §IX.5 hoped to avoid; unbounded growth on a failure path was the worse of the
 * two, and what the cap drops is counted and reported rather than being quietly forgotten,
 * which is the property SH103 actually cared about.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAgentIncidentStore {

    private static final Logger logger = LoggerFactory.getLogger(ShAgentIncidentStore.class);

    /**
     * One microsecond — the smallest step {@code TIMESTAMP} keeps (SH372).
     *
     * <p>The column is declared with no explicit precision, which is 6 on both H2 and
     * PostgreSQL. Stepping by a nanosecond would be arithmetic the database rounds away, and a
     * uniqueness that survives only in memory is the same defect wearing a fix.</p>
     */
    private static final long TICK_NANOS = 1_000L;

    private final ShAgentIncidentRepository repository;
    private final ShAgentProperties properties;

    /**
     * The last instant handed to a row, so no two writes share one (SH372).
     *
     * <p>{@code enforceCap} deletes by {@code order by lastSeen asc, id asc}, and {@code id} is
     * a random UUID — so on a tie the row that dies was chosen by a random string, and the cap's
     * only claim is that the survivors are the <em>most recent</em>. The schema's own note says
     * ties "can and do" happen; that is harmless for the SH88 cursor, which needs any total
     * order, and wrong for a deletion, which needs <em>this</em> one.</p>
     *
     * <p>Fixed here rather than in the query because no tie-break the row carries is insertion
     * -ordered — {@code firstSeen} ties in the same burst and the id is noise. Making the
     * instant strictly increasing gives the existing order a total tail, costs no column and no
     * changeset, and repairs {@code findRecent} in the same stroke.</p>
     */
    private final AtomicReference<Instant> issued = new AtomicReference<>(Instant.EPOCH);

    public ShAgentIncidentStore(ShAgentIncidentRepository repository,
                               ShAgentProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * The caller's instant, or the next one after the last issued when the clock has not moved.
     *
     * <p>Drift is bounded by how many incidents arrive inside one clock tick and is given back
     * as soon as the clock catches up, because the wall clock wins whenever it is ahead.</p>
     */
    private Instant stamp(Instant now) {
        return issued.updateAndGet(last -> now.isAfter(last) ? now : last.plusNanos(TICK_NANOS));
    }

    /**
     * Records one failure durably, folding it into an existing row when it has happened
     * before.
     *
     * <p>SH357 — <b>one overload, deliberately.</b> This used to be a pair: a five-argument
     * method carrying {@code REQUIRES_NEW} and delegating to a six-argument one that carried
     * nothing. Nothing ever called the five-argument one, so the annotation the class note
     * calls "the feature" was on dead code and every real write ran with <em>no</em>
     * demarcation at all — which is the failure this class exists to prevent, since a
     * {@code save} with no transaction of its own joins the caller's and rolls back with it.
     * Adding the annotation to the second overload would not have fixed it either: the
     * delegation is a self-invocation, so it never crosses the proxy. The pair is the defect;
     * {@link ShAgentTransactionalOverloadLintTest} refuses the next one.</p>
     *
     * @param frames SH190 — the throwing location, or {@code null} when there is none
     * @return {@code true} when the row was written or updated; {@code false} when it could
     *         not be, so the caller can keep its in-memory copy honest
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean record(ShAgentIncidentLog.Kind kind, String subject, String message,
                          String fix, String frames, Instant now) {
        try {
            // SH372 — every row's position comes from here, so the feed's order and the cap's
            // deletion order are the same total order and neither ends in a random id.
            Instant when = stamp(now);
            Optional<ShAgentIncident> existing = subject == null
                    ? repository.findByKindAndSubjectIsNullAndMessage(kind.name(), message)
                    : repository.findByKindAndSubjectAndMessage(kind.name(), subject, message);
            if (existing.isPresent()) {
                ShAgentIncident row = existing.get();
                row.setOccurrences(row.getOccurrences() + 1);
                // Moves the row forward in the feed, so a cursor that already passed it
                // delivers it again with the higher count — see the entity's class note.
                row.setLastSeen(when);
                // A `fix` learned later is worth keeping; one lost later is not worth erasing
                // the earlier advice for.
                if (row.getFix() == null && fix != null) {
                    row.setFix(fix);
                }
                // SH190, same rule as `fix`: evidence learned later is worth keeping, and a
                // repeat that arrived without a frame must not erase the frame the first one had.
                if (row.getFrames() == null && frames != null) {
                    row.setFrames(frames);
                }
                repository.save(row);
                return true;
            }
            enforceCap();
            ShAgentIncident row = new ShAgentIncident();
            row.setKind(kind.name());
            row.setSubject(subject);
            row.setMessage(message);
            row.setFix(fix);
            row.setFrames(frames);
            row.setFirstSeen(when);
            row.setLastSeen(when);
            row.setOccurrences(1);
            repository.save(row);
            return true;
        } catch (RuntimeException e) {
            // Deliberately swallowed: see the class note. The log line is the fallback the
            // whole subsystem exists to improve on, which is the right place to land when
            // the improvement itself is unavailable.
            logger.warn("Could not record incident ({} {}): {}", kind, subject, e.getMessage());
            return false;
        }
    }

    /** Drops the least recently seen rows until one more fits. */
    private void enforceCap() {
        int max = properties.getDiagnostics().getMaxRows();
        if (max <= 0) {
            return;
        }
        long count = repository.count();
        if (count < max) {
            return;
        }
        int excess = (int) (count - max + 1);
        List<ShAgentIncident> oldest = repository.findOldest(PageRequest.of(0, excess));
        repository.deleteAll(oldest);
    }

    /** Most recent first, for the diagnostics view. */
    @Transactional(readOnly = true)
    public List<ShAgentIncident> recent(int limit, Instant ceiling) {
        return repository.findWindow(ceiling, PageRequest.of(0, Math.max(1, limit)));
    }

    /**
     * Everything one diagnostics payload says about the table, from <b>one</b> snapshot.
     *
     * <p>SH358 — the count, the window and the earliest row used to be three separate
     * {@code readOnly} methods, so a payload was assembled from three different states of the
     * table. That is not hypothetical: the one shape the intermittent failure had was an empty
     * {@code counts} beside a populated {@code since}, and a row landing between the first read
     * and the third produces exactly that. Reading them together does not make a concurrent
     * write impossible — it makes it impossible for <em>one answer</em> to disagree with
     * itself, which is what left the failure undiagnosable.</p>
     *
     * <p><b>And it takes no ceiling, which is the rest of SH357.</b> It used to be handed
     * {@code Instant.now()}, copied from {@link #recent(int, Instant)} where the argument means
     * something: that read serves a <em>cursor</em>, and SH88's lag stops it reading past a row
     * that commits late, because a row a cursor skips is never delivered again. Diagnostics
     * resumes nothing. A ceiling there had nothing to protect and one thing it could do, which
     * was hide a row somebody had just recorded — the whole flake, in one argument nobody had a
     * reason to pass.</p>
     */
    @Transactional(readOnly = true)
    public Snapshot snapshot(int limit) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Object[] row : repository.countsByKind()) {
            counts.merge((String) row[0], ((Number) row[1]).intValue(), Integer::sum);
        }
        return new Snapshot(repository.count(),
                repository.findRecent(PageRequest.of(0, Math.max(1, limit))),
                counts,
                repository.earliest().orElse(null));
    }

    /**
     * @param retained rows in the table
     * @param window   the most recent of them, newest first — narrowed by the caller's limit
     * @param counts   occurrences per kind over the <b>whole</b> table, which is why they are read
     *                 here rather than summed from {@code window}: the payload promises a cap
     *                 narrows the detail without changing the diagnosis, and summing the page made
     *                 that false the moment an instance held more distinct problems than one page
     * @param earliest when the record starts, or {@code null} when the table is empty — which
     *                 is genuinely "nothing has ever failed" rather than "nothing is
     *                 remembered", the distinction SH103 could report but not make
     */
    public record Snapshot(long retained, List<ShAgentIncident> window,
                           Map<String, Integer> counts, Instant earliest) {
    }

    /** Oldest first, strictly after a position — the SH88-shaped read the feed merges. */
    @Transactional(readOnly = true)
    public List<ShAgentIncident> since(Instant ceiling, Instant at, String id, int limit) {
        return repository.findSince(ceiling, at, id, PageRequest.of(0, Math.max(1, limit)));
    }

    /** Test seam, and an operator's reset. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear() {
        repository.deleteAll();
    }
}
