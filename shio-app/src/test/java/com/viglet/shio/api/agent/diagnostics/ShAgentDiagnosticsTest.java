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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.diagnostics.dto.ShAgentDiagnostics;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;

/**
 * SH103 — the asynchronous half of the perception loop.
 *
 * <p>The properties that matter are not "a record comes back". They are: repeats
 * <b>fold</b> (one broken webhook retried on every publish must not evict every other
 * incident from a 50-slot window — the one way a diagnostics log actively misleads); the
 * window states its own <b>bound</b>, so "no incidents" cannot be mistaken for "nothing
 * has ever failed"; and the same incidents reach the {@code verify} report, because an
 * agent that has to remember a second call will not make it.</p>
 *
 * @author Alexandre Oliveira
 */
// Its own database, and its own context with it (SH315). Every assertion here reads the incident
// log through an *instance-wide* `verify(null, "diagnostics", …)` and one of them asserts
// `singleElement()` — so on the shared context any other test that recorded an incident would break
// it, in whatever order the suite happened to run. Nothing did today; that is not a property, it is
// a coincidence, and it is the fifth appearance of the shared-fixture lesson (SH239, SH272, SH289,
// SH314).
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-diagnostics;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
class ShAgentDiagnosticsTest {

    @Autowired
    private ShAgentIncidentLog incidentLog;
    @Autowired
    private ShAgentDiagnosticsService shAgentDiagnosticsService;
    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private com.viglet.shio.render.ShRenderModel renderModel;

    @BeforeEach
    void reset() {
        incidentLog.clear();
    }

    @Test
    @DisplayName("a failure is recorded with its subject, cause and fix")
    void aFailureIsRecordedWithItsFix() {
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://front.example.com/api/revalidate",
                "gave up after 3 attempt(s), last status 500", "Check the target is up.");

        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(null);

        assertThat(diagnostics.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.kind()).isEqualTo("webhook");
            assertThat(incident.subject()).isEqualTo("https://front.example.com/api/revalidate");
            assertThat(incident.count()).isEqualTo(1);
            assertThat(incident.fix()).isNotBlank();
        });
        assertThat(diagnostics.counts()).containsEntry("webhook", 1);
    }

    @Test
    @DisplayName("SH270: malformed content leaves an incident instead of an empty page nobody can explain")
    void malformedContentLeavesAnIncident() {
        // Not persisted: `fieldsOf` reads the column and the id, and the degradation is the parse.
        com.viglet.shio.persistence.model.post.unified.ShPostUnified broken =
                new com.viglet.shio.persistence.model.post.unified.ShPostUnified();
        broken.setId("11111111-2222-3333-4444-555555555555");
        broken.setData("{\"TITLE\": not json");

        assertThat(renderModel.fieldsOf(broken))
                .as("""
                        it still degrades rather than failing the page - that decision stands, and                         SH270 is about the silence around it, not about turning it into a 500""")
                .isEmpty();

        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(null);
        assertThat(diagnostics.incidents()).singleElement().satisfies(incident -> {
            assertThat(incident.kind())
                    .as("a fifth kind, because the other four are operational and this one is the "
                            + "content being wrong while the symptom is an empty page")
                    .isEqualTo("content");
            assertThat(incident.subject()).isEqualTo("id:" + broken.getId());
            assertThat(incident.fix()).contains("post.upsert");
        });

        for (int render = 0; render < 20; render++) {
            renderModel.fieldsOf(broken);
        }
        assertThat(shAgentDiagnosticsService.diagnostics(null).incidents())
                .as("""
                        folded by subject: a broken post renders on every page view, and twenty-one                         rows about one post would evict everything else in the window""")
                .hasSize(1);
        assertThat(shAgentDiagnosticsService.diagnostics(null).counts()).containsEntry("content", 21);
    }

    @Test
    @DisplayName("repeats fold into one row so one broken target cannot evict the rest")
    void repeatsFoldIntoOneRow() {
        for (int attempt = 0; attempt < 200; attempt++) {
            incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://front.example.com/hook",
                    "connection refused", null);
        }
        incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "id:abc", "publish failed", null);

        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(null);

        assertThat(diagnostics.incidents()).hasSize(2);
        assertThat(diagnostics.dropped()).isZero();
        assertThat(diagnostics.counts()).containsEntry("webhook", 200).containsEntry("schedule", 1);
    }

    /**
     * SH144 changed what this test is about, and the change is the point.
     *
     * <p>It used to assert that the in-memory buffer <em>admitted</em> evicting rows, because
     * eviction lost them. The record is durable now, so overflowing the buffer loses nothing —
     * every one of these incidents is in the table. What still has to be admitted is the
     * report's own <b>cap</b>: showing 25 of 60 must say so, or a reader concludes there were
     * 25.</p>
     */
    @Test
    @DisplayName("overflowing the buffer no longer loses anything; the report's cap is admitted")
    void theReportsCapIsAdmitted() {
        for (int index = 0; index < ShAgentIncidentLog.CAPACITY + 10; index++) {
            incidentLog.record(ShAgentIncidentLog.Kind.API_ERROR, "GET /api/v2/thing/" + index,
                    "HTTP 500", null);
        }

        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(0);

        // `dropped` now means "could not be written down" — nothing here failed to persist.
        assertThat(diagnostics.dropped()).isZero();
        assertThat(diagnostics.incidents()).hasSize(25);
        assertThat(diagnostics.notes()).anySatisfy(note ->
                assertThat(note).contains("Showing 25 of 60"));
    }

    @Test
    @DisplayName("an empty report says nothing has failed, and can now mean it")
    void anEmptyReportSaysWhatItCannotSee() {
        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(null);

        assertThat(diagnostics.incidents()).isEmpty();
        // Absent, not a process-start timestamp: there is no record, rather than a record that
        // began when this JVM did (SH144).
        assertThat(diagnostics.since()).isNull();
        assertThat(diagnostics.notes())
                .anySatisfy(note -> assertThat(note).contains("nothing has failed"))
                .anySatisfy(note -> assertThat(note).contains("agent/verify"));
    }

    @Test
    @DisplayName("incidents reach the verify report, so one call is enough after a write")
    void incidentsReachTheVerifyReport() {
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, "https://front.example.com/hook",
                "gave up after 3 attempt(s)", "Re-publish to re-fire it.");

        ShAgentReport report = shAgentVerifyService.verify(null, "diagnostics", null);

        assertThat(report.ok()).isFalse();
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.check()).isEqualTo("diagnostics");
            assertThat(finding.rule()).isEqualTo("webhook");
            assertThat(finding.severity()).isEqualTo(ShAgentReport.ERROR);
        });
    }

    @Test
    @DisplayName("a failed transform is a warning — the original was still served")
    void aFailedTransformIsOnlyAWarning() {
        incidentLog.record(ShAgentIncidentLog.Kind.TRANSFORM, "hero.png",
                "transform failed, original served: bad header", null);

        ShAgentReport report = shAgentVerifyService.verify(null, "diagnostics", null);

        assertThat(report.ok()).isTrue();
        assertThat(report.warnings()).isEqualTo(1);
    }
}
