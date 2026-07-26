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
@SpringBootTest
class ShAgentDiagnosticsTest {

    @Autowired
    private ShAgentIncidentLog incidentLog;
    @Autowired
    private ShAgentDiagnosticsService shAgentDiagnosticsService;
    @Autowired
    private ShAgentVerifyService shAgentVerifyService;

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

    @Test
    @DisplayName("eviction is admitted, not hidden")
    void evictionIsAdmitted() {
        for (int index = 0; index < ShAgentIncidentLog.CAPACITY + 10; index++) {
            incidentLog.record(ShAgentIncidentLog.Kind.API_ERROR, "GET /api/v2/thing/" + index,
                    "HTTP 500", null);
        }

        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(0);

        assertThat(diagnostics.dropped()).isEqualTo(10);
        assertThat(diagnostics.incidents()).hasSize(ShAgentDiagnosticsService.DEFAULT_LIMIT);
        assertThat(diagnostics.notes()).anySatisfy(note -> assertThat(note).contains("retained"));
    }

    @Test
    @DisplayName("an empty window says it is a window, not a clean bill of health")
    void anEmptyWindowSaysWhatItCannotSee() {
        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(null);

        assertThat(diagnostics.incidents()).isEmpty();
        assertThat(diagnostics.since()).isNotBlank();
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
