/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.agent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.agent.ShAgentIncident;

/**
 * Durable incidents (SH144).
 *
 * <p>The keyset queries are deliberately the same shape as {@code ShHistoryRepository}'s,
 * because the two streams are merged under <b>one</b> SH88 cursor: a caller passes a single
 * {@code since=} and gets "what changed and what broke". Two different resumption rules over
 * one cursor would be a bug that only appears at a timestamp tie.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShAgentIncidentRepository extends JpaRepository<ShAgentIncident, String> {

    /** The fold key: what went wrong, not each time it did. */
    Optional<ShAgentIncident> findByKindAndSubjectAndMessage(String kind, String subject,
                                                            String message);

    /** Same, for the rows whose subject was never recorded — {@code null} is not {@code =}. */
    Optional<ShAgentIncident> findByKindAndSubjectIsNullAndMessage(String kind, String message);

    /**
     * Everything strictly after the {@code (lastSeen, id)} position, oldest first.
     *
     * <p>The tie-breaking half of the predicate matters more here than in the audit: a burst
     * of failures inside one second is the normal shape of an outage, and a plain
     * {@code lastSeen >} would report one of them and silently drop the rest.</p>
     */
    @Query("select i from ShAgentIncident i where i.lastSeen <= :ceiling "
            + "and (i.lastSeen > :at or (i.lastSeen = :at and i.id > :id)) "
            + "order by i.lastSeen asc, i.id asc")
    List<ShAgentIncident> findSince(Instant ceiling, Instant at, String id, Pageable pageable);

    /** The most recent incidents, newest first — the window a caller gets with no cursor. */
    @Query("select i from ShAgentIncident i where i.lastSeen <= :ceiling "
            + "order by i.lastSeen desc, i.id desc")
    List<ShAgentIncident> findWindow(Instant ceiling, Pageable pageable);

    /**
     * The same window with <b>no ceiling</b> — the diagnostics read (SH357).
     *
     * <p>Every other query here takes a ceiling because it serves a <b>cursor</b>: SH88's lag keeps
     * {@code /agent/changes} from reading past a row that commits a millisecond late, and a row a
     * cursor skips is lost for good. Diagnostics answers "what is broken right now" and resumes
     * nothing, so a ceiling there protects no one and can only hide a row that has just been
     * written — which it did, roughly once in fifteen full runs, in three different tests.</p>
     */
    @Query("select i from ShAgentIncident i order by i.lastSeen desc, i.id desc")
    List<ShAgentIncident> findRecent(Pageable pageable);

    /** Oldest first, for the cap: what gets dropped is the least recently seen. */
    @Query("select i from ShAgentIncident i order by i.lastSeen asc, i.id asc")
    List<ShAgentIncident> findOldest(Pageable pageable);

    /**
     * Occurrences per kind over the <b>whole table</b> — the diagnostics counts (SH357).
     *
     * <p>The counts used to be summed over the page {@code findRecent} returns, while the payload's
     * own contract says a cap "narrows the detail without changing the diagnosis". With
     * {@code default-limit} at 25 and {@code max-rows} at 500, an instance holding more than 25
     * distinct problems reported counts for the newest 25 and silently omitted the rest — so
     * whether a given incident appeared depended on how many others happened to be newer.</p>
     *
     * <p>An aggregate rather than a wider page: the answer is one row per kind however large the
     * table gets, which is the shape a count wants and is cheaper than the read it replaces.</p>
     */
    @Query("select i.kind, sum(i.occurrences) from ShAgentIncident i group by i.kind")
    List<Object[]> countsByKind();

    /** When this instance's record actually starts — the honest bound on "nothing failed". */
    @Query("select min(i.firstSeen) from ShAgentIncident i")
    Optional<Instant> earliest();
}
