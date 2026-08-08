/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.diagnostics.dto.ShAgentDiagnostics;
import com.viglet.shio.persistence.repository.agent.ShAgentIncidentRepository;

/**
 * SH357 — the read that hid a row it had just been told about.
 *
 * <h2>What the flake was, and how it became decidable</h2>
 *
 * <p>Three times across the suite's history a diagnostics read came back empty on a run where
 * nothing else failed and no code had changed: {@code diagnosticsReadsTheTable} twice and
 * {@code malformedContentLeavesAnIncident} once. SH358 made the payload one snapshot instead of
 * three read-only transactions, which removed the only way "the row landed between two reads"
 * could produce it. What was left had to be a row that was <em>written and not seen</em>, and the
 * last occurrence pinned it down by elimination: the failing sequence folds an existing row
 * twenty times, {@code enforceCap} runs only on the create path, so nothing deleted anything. A
 * row that is present and invisible fell outside the window.</p>
 *
 * <h2>Two candidates, and only one of them is the flake</h2>
 *
 * <p><b>The ceiling.</b> {@code findWindow} answers {@code lastSeen <= :ceiling}, which exists for
 * {@code /agent/changes} (SH88): that read computes {@code now - lagMillis} so a cursor cannot
 * skip a row committed a millisecond late, and a row a cursor skips is lost forever. Diagnostics
 * has <b>no cursor</b> and was passing {@code Instant.now()} anyway — a ceiling with nothing to
 * protect, which can only hide. It is gone, and the first test states the mechanism so the change
 * rests on an argument rather than on a flake. <b>It is not established as the cause:</b> six
 * hundred record-then-read rounds against the old code did not reproduce a single miss.</p>
 *
 * <p><b>The counts, which is the one that reproduces.</b> {@code counts} was summed over the
 * <em>page</em> while the payload's javadoc promises a cap "narrows the detail without changing
 * the diagnosis". With {@code default-limit} 25 against {@code max-rows} 500, the 26th-newest kind
 * vanished — and which kinds survived depended on what else happened to be newer, so on a shared
 * context the answer moved with test order. That is the shape of "fails once in fifteen full runs
 * and never reproduces", and unlike the ceiling it is <b>deterministic on demand</b>: the second
 * test below fails on the old code every time.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh357;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
class ShAgentDiagnosticsCeilingTest {

    @Autowired
    private ShAgentIncidentLog incidentLog;
    @Autowired
    private ShAgentIncidentStore store;
    @Autowired
    private ShAgentIncidentRepository repository;
    @Autowired
    private ShAgentDiagnosticsService shAgentDiagnosticsService;
    @Autowired
    private com.viglet.shio.api.agent.ShAgentProperties properties;

    @BeforeEach
    void reset() {
        incidentLog.clear();
    }

    @Test
    @DisplayName("a ceiling that predates a row hides it, however healthy the row")
    void aCeilingHidesALiveRow() {
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://ceiling.example.com",
                "gave up", null, null, null);

        Instant stamped = repository.findAll().get(0).getLastSeen();

        assertThat(store.recent(10, stamped.minusMillis(1), null))
                .as("""
                        the mechanism, stated once so the rest of this class is not arguing from a \
                        flake: `findWindow` answers `lastSeen <= :ceiling`, so a row stamped after \
                        the ceiling is absent from a table that holds it. Nothing here is broken — \
                        this is the query doing its job, which is why the defect was in the caller \
                        that had no reason to pass a ceiling at all.""")
                .isEmpty();
        assertThat(store.recent(10, stamped, null))
                .as("and the same read at the row's own instant finds it — the predicate is "
                        + "inclusive, so this is a boundary and not an off-by-one")
                .hasSize(1);
    }

    @Test
    @DisplayName("a cap narrows the detail and never the diagnosis")
    void countsCoverTheWholeTableAndNotThePage() {
        // The defect that reads as a flake, and it is not a race at all. `counts` was summed over
        // the *page* `findRecent` returns, while the payload's own javadoc has always promised
        // that a cap "narrows the detail without changing the diagnosis". `default-limit` is 25
        // and `max-rows` is 500, so on any instance holding more than one page of distinct
        // problems the older kinds simply vanished from the counts — and *which* ones did
        // depended on what happened to be newer. That is order-dependence, which is exactly what
        // "fails once in fifteen full runs and never reproduces" looks like from the outside.
        int page = properties.getDiagnostics().getDefaultLimit();

        // One incident of a kind nothing else here uses, recorded first so every one of the
        // fillers below is newer than it and it falls off the page.
        incidentLog.record(ShAgentIncidentLog.Kind.TRANSFORM, "post-sh357", "resize failed", null, null, null);
        for (int filler = 0; filler < page + 5; filler++) {
            incidentLog.record(ShAgentIncidentLog.Kind.API_ERROR, "/sh357/" + filler,
                    "500 on GET " + filler, null, null, null);
        }

        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(null);

        assertThat(diagnostics.incidents())
                .as("the detail is still capped at the page — that half was never wrong")
                .hasSize(page);
        assertThat(diagnostics.counts())
                .as("""
                        the oldest kind is off the page by construction, and it must still be in \
                        the counts: a diagnosis that changes with the limit is a diagnosis nobody \
                        can act on, and an agent that raised ?limit= to see more would watch the \
                        numbers move under it.""")
                .containsEntry("transform", 1)
                .containsEntry("api-error", page + 5);
    }

    @Test
    @DisplayName("the feed keeps its lag, because a cursor is the thing a ceiling protects")
    void theFeedKeepsItsCeiling() {
        // Guards the fix from being over-applied. Removing the ceiling from `/agent/changes` would
        // trade an invisible row for a *lost* one: SH88's lag exists so a cursor cannot read past a
        // row that commits a millisecond late, and a skipped row in a cursor feed never comes back.
        // The two reads are different questions and only one of them has something to protect.
        // SH204 — asserted on the *presence of an Instant* rather than on a parameter count, which
        // is what this always meant. `recent` grew a site scope and the count-based form failed
        // while the property it guards was untouched: a lint that breaks when an unrelated argument
        // is added teaches the next reader to adjust the number rather than ask the question.
        assertThat(ShAgentIncidentStore.class.getDeclaredMethods())
                .as("""
                        `recent(limit, ceiling, …)` is the feed's read and keeps its ceiling; \
                        `snapshot(limit)` is the diagnostics read and must not have one. If these \
                        ever collapse into one method, the question 'is there a cursor to protect' \
                        stops being asked.""")
                .anyMatch(method -> "recent".equals(method.getName())
                        && List.of(method.getParameterTypes()).contains(Instant.class))
                .noneMatch(method -> "snapshot".equals(method.getName())
                        && List.of(method.getParameterTypes()).contains(Instant.class));
    }
}
