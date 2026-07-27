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

import com.viglet.shio.api.agent.ShAgentChangeCursor;
import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.ShAgentChangesTerse;
import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.diagnostics.dto.ShAgentDiagnostics;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.persistence.model.agent.ShAgentIncident;
import com.viglet.shio.persistence.repository.agent.ShAgentIncidentRepository;

/**
 * SH144 — the incident record survives a restart, and rides the SH88 cursor.
 *
 * <p>What is asserted is the pair of properties that make the durable form worth the table.
 * <b>One cursor answers both questions</b>: a caller passes a single {@code since=} and gets
 * what changed and what broke, so the two are never resumed from different places. And <b>a
 * recurring failure is re-delivered</b>: its position is its last occurrence, so an outage
 * that started before the cursor and is still going shows up again with a higher count — the
 * behaviour a position at {@code firstSeen} would get exactly backwards, reporting an ongoing
 * failure once and then never again.</p>
 *
 * <p>The read path is asserted against the <em>store</em> rather than the in-memory buffer,
 * because the whole point is that the answer no longer depends on how long this process has
 * been up.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShAgentDurableIncidentTest {

    @Autowired
    private ShAgentIncidentLog incidentLog;
    @Autowired
    private ShAgentIncidentStore store;
    @Autowired
    private ShAgentIncidentRepository repository;
    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShAgentDiagnosticsService shAgentDiagnosticsService;
    @Autowired
    private ShAgentProperties properties;

    @BeforeEach
    void reset() {
        incidentLog.clear();
    }

    @Test
    @DisplayName("an incident is written to the table, not only to the process")
    void anIncidentIsPersisted() {
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://front.example.com/hook",
                "gave up after 3 attempt(s)", "Check the target is up.");

        assertThat(repository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getKind()).isEqualTo("WEBHOOK");
            assertThat(row.getSubject()).isEqualTo("https://front.example.com/hook");
            assertThat(row.getOccurrences()).isEqualTo(1);
            assertThat(row.getFirstSeen()).isNotNull();
            assertThat(row.getLastSeen()).isNotNull();
        });
    }

    @Test
    @DisplayName("repeats fold into one row and move its feed position forward")
    void repeatsFoldAndAdvance() {
        incidentLog.record(ShAgentIncidentLog.Kind.TRANSFORM, "post-1", "thumbnail failed", null);
        Instant afterFirst = repository.findAll().get(0).getLastSeen();

        for (int i = 0; i < 5; i++) {
            incidentLog.record(ShAgentIncidentLog.Kind.TRANSFORM, "post-1", "thumbnail failed", null);
        }

        assertThat(repository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getOccurrences()).isEqualTo(6);
            // The property the merged feed depends on: a row that recurs cannot stay behind a
            // cursor that already passed it.
            assertThat(row.getLastSeen()).isAfterOrEqualTo(afterFirst);
            assertThat(row.getFirstSeen()).isBeforeOrEqualTo(row.getLastSeen());
        });
    }

    @Test
    @DisplayName("a fix learned later is kept; a missing one does not erase the earlier advice")
    void aFixIsNotLost() {
        incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "post-2", "publish threw", null);
        incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "post-2", "publish threw", "Retry it.");
        incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "post-2", "publish threw", null);

        assertThat(repository.findAll()).singleElement()
                .satisfies(row -> assertThat(row.getFix()).isEqualTo("Retry it."));
    }

    @Test
    @DisplayName("the change feed carries incidents, in their own array")
    void theFeedCarriesIncidents() {
        incidentLog.record(ShAgentIncidentLog.Kind.API_ERROR, "/api/v2/agent/batch",
                "500 on POST", "See the log.");
        // The SH88 lag applies to incidents too, and deliberately: the store stamps `lastSeen`
        // before it commits, so the same "an older row lands after a newer one was read"
        // hazard exists here. A test that read immediately would be asserting on an entry the
        // feed is correctly holding back.
        waitForLag();

        ShAgentChanges changes = shAgentChangesService.changes(null, null, 50, false);

        assertThat(changes.incidents()).isNotNull().singleElement().satisfies(incident -> {
            assertThat(incident.kind()).isEqualTo("api-error");
            assertThat(incident.subject()).isEqualTo("/api/v2/agent/batch");
            assertThat(incident.count()).isEqualTo(1);
            assertThat(incident.at()).isNotBlank();
        });
        // An invented `action` on a Change would have every existing caller treat a failure as
        // a content transition — the reason this is a separate array.
        assertThat(changes.changes())
                .noneSatisfy(change -> assertThat(change.action()).containsIgnoringCase("incident"));
        assertThat(changes.notes()).anySatisfy(note -> assertThat(note).contains("incident(s)"));
    }

    @Test
    @DisplayName("one cursor advances past both streams, and an incident after it comes back")
    void oneCursorCoversBoth() {
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://a.example.com", "first", null);
        waitForLag();

        String cursor = shAgentChangesService.changes(null, null, 50, false).cursor();
        // Nothing new yet: the same cursor must come back empty rather than re-delivering.
        ShAgentChanges quiet = shAgentChangesService.changes(null, cursor, 50, false);
        assertThat(quiet.incidents()).isNull();

        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://b.example.com", "second", null);
        waitForLag();

        ShAgentChanges next = shAgentChangesService.changes(null, cursor, 50, false);
        assertThat(next.incidents()).isNotNull()
                .anySatisfy(incident -> assertThat(incident.subject()).isEqualTo("https://b.example.com"));
        assertThat(ShAgentChangeCursor.later(cursor, next.cursor())).isEqualTo(next.cursor());
    }

    @Test
    @DisplayName("a failure that is still happening is reported again, with a higher count")
    void anOngoingFailureIsRedelivered() {
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://ongoing.example.com",
                "gave up", null);
        waitForLag();
        String cursor = shAgentChangesService.changes(null, null, 50, false).cursor();

        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://ongoing.example.com",
                "gave up", null);
        waitForLag();

        ShAgentChanges next = shAgentChangesService.changes(null, cursor, 50, false);
        assertThat(next.incidents()).isNotNull().singleElement().satisfies(incident -> {
            assertThat(incident.subject()).isEqualTo("https://ongoing.example.com");
            assertThat(incident.count()).isEqualTo(2);
            // `first` is what tells a reader this is an old problem, not a new one.
            assertThat(incident.first()).isNotEqualTo(incident.at());
        });
    }

    /**
     * The bug this design nearly shipped.
     *
     * <p>A recent incident sits far ahead of an audit page being walked one row at a time. If
     * the merged cursor took the later of the two positions, it would jump past the audit rows
     * the caller has not read — silently, which is the only kind of feed bug that matters. So
     * while the change stream says {@code more}, the cursor stays the change stream's, and the
     * incidents in that range are read again next time.</p>
     */
    @Test
    @DisplayName("a recent incident never advances the cursor past unread changes")
    void anIncidentCannotSkipUnreadChanges() {
        incidentLog.record(ShAgentIncidentLog.Kind.API_ERROR, "/skip-guard", "500", null);
        waitForLag();

        // A page of one over a trail that certainly holds more than one entry, anchored well
        // before anything: exactly the shape where a max-cursor would overshoot.
        String anchor = new ShAgentChangeCursor(Instant.parse("2000-01-01T00:00:00Z"), "").toString();
        ShAgentChanges page = shAgentChangesService.changes(null, anchor, 1, false);

        if (!page.more()) {
            // Nothing to protect on an instance with a one-row trail; the other tests cover
            // the advancing case.
            return;
        }
        assertThat(page.changes()).hasSize(1);
        String delivered = page.changes().get(0).at();
        // The cursor must still be the change row's position, not the incident's.
        assertThat(ShAgentChangeCursor.parse(page.cursor()).at())
                .isEqualTo(Instant.parse(delivered));

        // And the next page continues from there rather than from the far future.
        ShAgentChanges next = shAgentChangesService.changes(null, page.cursor(), 1, false);
        assertThat(next.changes()).isNotEmpty();
    }

    @Test
    @DisplayName("the terse feed gives an incident its own verb")
    void terseKeepsThemApart() {
        incidentLog.record(ShAgentIncidentLog.Kind.TRANSFORM, "post-9", "resize failed",
                "Re-upload it.");
        waitForLag();

        String terse = ShAgentChangesTerse.render(
                shAgentChangesService.changes(null, null, 50, false));

        assertThat(terse).contains("incident transform post-9");
        assertThat(terse).contains("fix Re-upload it.");
    }

    @Test
    @DisplayName("diagnostics reads the table, and says where the record starts")
    void diagnosticsReadsTheTable() {
        ShAgentDiagnostics empty = shAgentDiagnosticsService.diagnostics(null);
        assertThat(empty.since()).isNull();
        // The distinction SH103 could report but not make.
        assertThat(empty.notes()).anySatisfy(note ->
                assertThat(note).contains("nothing has failed rather than nothing is remembered"));

        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://c.example.com", "boom", null);

        ShAgentDiagnostics after = shAgentDiagnosticsService.diagnostics(null);
        assertThat(after.since()).isNotBlank();
        assertThat(after.notes()).anySatisfy(note ->
                assertThat(note).contains("The record starts at"));
        assertThat(after.notes()).anySatisfy(note ->
                assertThat(note).contains("/agent/changes"));
        assertThat(after.counts()).containsEntry("webhook", 1);
    }

    @Test
    @DisplayName("the cap drops the least recently seen, and only when it has to")
    void theCapDropsTheOldest() {
        int max = properties.getDiagnostics().getMaxRows();
        properties.getDiagnostics().setMaxRows(3);
        try {
            for (int i = 0; i < 5; i++) {
                incidentLog.record(ShAgentIncidentLog.Kind.API_ERROR, "/path/" + i,
                        "500 on GET " + i, null);
            }
            List<ShAgentIncident> rows = repository.findAll();
            assertThat(rows).hasSize(3);
            // The survivors are the most recent: an outage's newest evidence is the useful kind.
            assertThat(rows).extracting(ShAgentIncident::getSubject)
                    .containsExactlyInAnyOrder("/path/2", "/path/3", "/path/4");
        } finally {
            properties.getDiagnostics().setMaxRows(max);
        }
    }

    @Test
    @DisplayName("later() compares positions, not strings")
    void laterComparesPositions() {
        String early = new ShAgentChangeCursor(Instant.parse("2026-01-01T00:00:00Z"), "b").toString();
        String late = new ShAgentChangeCursor(Instant.parse("2026-06-01T00:00:00Z"), "a").toString();

        assertThat(ShAgentChangeCursor.later(early, late)).isEqualTo(late);
        assertThat(ShAgentChangeCursor.later(late, early)).isEqualTo(late);
        // A tie is broken by id, the same way the feed's ordering is.
        String tieA = new ShAgentChangeCursor(Instant.parse("2026-01-01T00:00:00Z"), "a").toString();
        assertThat(ShAgentChangeCursor.later(tieA, early)).isEqualTo(early);
        assertThat(ShAgentChangeCursor.later(null, late)).isEqualTo(late);
        assertThat(ShAgentChangeCursor.later(late, "")).isEqualTo(late);
    }

    /**
     * The feed holds back anything younger than the SH88 lag, so a test that writes and reads
     * in the same millisecond would assert on an entry the feed is correctly hiding.
     */
    private void waitForLag() {
        long lag = properties.getChanges().getLagMillis();
        if (lag <= 0) {
            return;
        }
        try {
            Thread.sleep(lag + 50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
