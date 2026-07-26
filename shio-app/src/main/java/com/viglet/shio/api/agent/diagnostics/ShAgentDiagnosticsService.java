/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.diagnostics.dto.ShAgentDiagnostics;
import com.viglet.shio.webhook.ShWebhookProperties;

/**
 * Shapes the incident log into {@code GET /api/v2/agent/diagnostics} (SH103).
 *
 * <p>Composition, as everywhere on this surface (P10): the log is the only state, and
 * this class adds the two things a caller cannot derive — a per-kind count, and the
 * honest note about what the window does <em>not</em> cover. The second one is the
 * reason this is a service rather than a record mapper: a webhook subsystem that is
 * switched off reports zero failures, and zero failures reads as healthy. Saying "the
 * webhook subsystem is disabled, so nothing here could have been reported" costs one
 * line and prevents the wrong conclusion.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentDiagnosticsService {

    /** Incidents returned when the caller names no limit. */
    static final int DEFAULT_LIMIT = 25;

    private final ShAgentIncidentLog incidentLog;
    private final ShWebhookProperties webhookProperties;

    public ShAgentDiagnosticsService(ShAgentIncidentLog incidentLog,
                                     ShWebhookProperties webhookProperties) {
        this.incidentLog = incidentLog;
        this.webhookProperties = webhookProperties;
    }

    /**
     * @param limit incidents to return; {@code null} or {@code <= 0} = the default.
     *              Counts cover every retained incident regardless, so a cap narrows the
     *              detail without changing the diagnosis
     */
    public ShAgentDiagnostics diagnostics(Integer limit) {
        int cap = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        List<ShAgentIncidentLog.Incident> all = incidentLog.recent(0);

        Map<String, Integer> counts = new TreeMap<>();
        for (ShAgentIncidentLog.Incident incident : all) {
            counts.merge(incident.kind().wireName(), incident.count(), Integer::sum);
        }
        List<ShAgentDiagnostics.Incident> rows = all.stream()
                .limit(cap)
                .map(ShAgentDiagnosticsService::row)
                .toList();
        return new ShAgentDiagnostics(incidentLog.since().toString(), incidentLog.dropped(),
                Map.copyOf(counts), rows, notes(all.size(), rows.size()));
    }

    private static ShAgentDiagnostics.Incident row(ShAgentIncidentLog.Incident incident) {
        return new ShAgentDiagnostics.Incident(
                incident.kind().wireName(),
                incident.subject(),
                incident.message(),
                incident.fix(),
                incident.count(),
                incident.firstSeen().toString(),
                incident.lastSeen().toString());
    }

    /** What this view cannot see. Said out loud, because silence here reads as health. */
    private List<String> notes(int retained, int returned) {
        List<String> notes = new ArrayList<>();
        notes.add("This window is in memory and starts at process start — an empty report means "
                + "nothing has failed *since then*, not that nothing has ever failed.");
        if (!webhookProperties.isEnabled()) {
            notes.add("Webhooks are disabled (shio.webhook.enabled=false), so no delivery failure "
                    + "could have been recorded — a front end is not being revalidated on publish.");
        }
        if (returned < retained) {
            notes.add("Showing " + returned + " of " + retained
                    + " retained incidents; raise ?limit= for the rest.");
        }
        notes.add("Content defects are not here — run GET /api/v2/agent/verify for those.");
        return List.copyOf(notes);
    }
}
