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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.diagnostics.dto.ShAgentDiagnostics;
import com.viglet.shio.persistence.model.agent.ShAgentIncident;
import com.viglet.shio.webhook.ShWebhookProperties;
import com.viglet.shio.utils.ShStableMap;

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

    private final ShAgentIncidentLog incidentLog;
    private final ShAgentIncidentStore store;
    private final ShWebhookProperties webhookProperties;
    private final ShAgentProperties properties;

    public ShAgentDiagnosticsService(ShAgentIncidentLog incidentLog,
                                     ShAgentIncidentStore store,
                                     ShWebhookProperties webhookProperties,
                                     ShAgentProperties properties) {
        this.incidentLog = incidentLog;
        this.store = store;
        this.webhookProperties = webhookProperties;
        this.properties = properties;
    }

    /**
     * @param limit incidents to return; {@code null} or {@code <= 0} = the default.
     *              Counts cover every retained incident regardless, so a cap narrows the
     *              detail without changing the diagnosis
     */
    public ShAgentDiagnostics diagnostics(Integer limit) {
        ShAgentProperties.Diagnostics config = properties.getDiagnostics();
        int cap = limit == null || limit <= 0
                ? config.getDefaultLimit()
                : Math.min(limit, config.getMaxLimit());

        // Read from the durable store, not the buffer (SH144): the answer must not depend on
        // how long this process happens to have been running. The buffer is still consulted
        // below, but only to report what it holds that the store does not.
        //
        // SH357 — one snapshot, not three reads. `retained`, the window and `earliest` used to
        // come from three separate read-only transactions, which is how a response could carry
        // an empty `counts` beside a populated `since` and leave nobody able to say whether the
        // row had been written.
        ShAgentIncidentStore.Snapshot snapshot = store.snapshot(Math.max(cap, 1), Instant.now());
        long retained = snapshot.retained();
        List<ShAgentIncident> all = snapshot.window();

        Map<String, Integer> counts = new TreeMap<>();
        for (ShAgentIncident incident : all) {
            counts.merge(wireName(incident.getKind()), incident.getOccurrences(), Integer::sum);
        }
        List<ShAgentDiagnostics.Incident> rows = all.stream()
                .limit(cap)
                .map(ShAgentDiagnosticsService::row)
                .toList();
        Instant earliest = snapshot.earliest();
        return new ShAgentDiagnostics(earliest == null ? null : earliest.toString(),
                // Not the buffer's eviction count any more (SH144): an evicted row is still in
                // the table, so it is not dropped. What is genuinely lost is what could not be
                // written — see the DTO note.
                incidentLog.unpersisted(),
                // SH136: keeps the TreeMap's order — Map.copyOf would replace the sort
                // with a per-JVM hash order, so the same incident window serialized
                // differently after a restart and the SH116 ETag stopped matching.
                ShStableMap.copyOf(counts), rows, notes(retained, rows.size(), earliest));
    }

    private static ShAgentDiagnostics.Incident row(ShAgentIncident incident) {
        return new ShAgentDiagnostics.Incident(
                wireName(incident.getKind()),
                incident.getSubject(),
                incident.getMessage(),
                incident.getFix(),
                incident.getOccurrences(),
                incident.getFirstSeen().toString(),
                incident.getLastSeen().toString(),
                incident.getFrames());
    }

    /** Stored by enum name; served in the wire form a caller branches on. */
    private static String wireName(String kind) {
        return kind == null ? null : kind.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * What this view cannot see. Said out loud, because silence here reads as health.
     *
     * <p>The first note is the one that changed with SH144, and the direction it changed in is
     * the point: it used to warn that an empty report only covered this process's lifetime.
     * Now an empty report means nothing has ever been recorded, and what needs saying instead
     * is where the record <em>starts</em> — an instance whose oldest incident is from Tuesday
     * cannot answer a question about Monday either, it just fails to answer it for a different
     * reason.</p>
     */
    private List<String> notes(long retained, int returned, Instant earliest) {
        List<String> notes = new ArrayList<>();
        if (earliest == null) {
            notes.add("Nothing has been recorded on this instance — this is a durable record "
                    + "(SH144), so an empty report now means nothing has failed rather than "
                    + "nothing is remembered.");
        } else {
            notes.add("The record starts at " + earliest + "; incidents older than that, and "
                    + "any dropped by shio.agent.diagnostics.max-rows, are gone.");
        }
        long unpersisted = incidentLog.unpersisted();
        if (unpersisted > 0) {
            // The case worth shouting about: if the database was the thing failing, the record
            // of the failure is exactly what could not be written.
            notes.add(unpersisted + " incident(s) in this process could not be stored — the "
                    + "durable record is incomplete, and the reason is in the application log.");
        }
        if (!webhookProperties.isEnabled()) {
            notes.add("Webhooks are disabled (shio.webhook.enabled=false), so no delivery failure "
                    + "could have been recorded — a front end is not being revalidated on publish.");
        }
        if (returned < retained) {
            notes.add("Showing " + returned + " of " + retained
                    + " retained incidents; raise ?limit= for the rest.");
        }
        notes.add("Incidents also travel on GET /api/v2/agent/changes, so one since=<cursor> "
                + "answers what changed and what broke.");
        notes.add("Content defects are not here — run GET /api/v2/agent/verify for those.");
        return List.copyOf(notes);
    }
}
