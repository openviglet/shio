/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.List;
import java.util.Map;

import com.viglet.shio.tenant.ShTenantApiCallMeter;
import com.viglet.shio.tenant.ShTenantUsage;

/**
 * What this tenant has spent, for the caller that is spending it (SH754).
 *
 * <h2>Why this document exists at all</h2>
 *
 * <p>SH45 landed the meter with two ways out — {@code GET /api/v2/tenant/usage} for a human and
 * the {@code ShBillingExporter} SPI for a billing system — and neither is reachable by the party
 * the numbers are mostly about. The meter's own surface keys say so: {@code agent} is one of the
 * three, and the caller that could not read the figure was the one generating it. An agent asked
 * to work inside a budget with no way to ask what it has already spent is an agent given advice
 * rather than a bound.</p>
 *
 * <h2>Pending is not a detail</h2>
 *
 * <p>The meter accumulates in heap and flushes on an interval, so a document that reported only
 * what is written would answer <b>zero</b> for the first minute of every window — and an agent
 * that reads zero right after doing work learns to distrust the number, which is worse than not
 * publishing it. So each surface carries both, and {@code pending} is stated even when the written
 * figure is large: the two answer different questions, and adding them silently would make the
 * total un-reconcilable against what a bill is computed from.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentUsageTerse {

    /** The surfaces, in a fixed order — a document whose row order moved would churn every diff. */
    private static final List<String> SURFACES = List.of(
            ShTenantApiCallMeter.CDA, ShTenantApiCallMeter.AGENT, ShTenantApiCallMeter.CONSOLE);

    private ShAgentUsageTerse() {
        // utility
    }

    /**
     * Render one period's usage.
     *
     * <p>Every surface gets a row, including the ones at zero. "This tenant made no agent calls
     * today" is an answer, and an absent row is indistinguishable from a surface the document
     * forgot — the same reason {@code ShBillingExportService} drives its listing from the tenant
     * catalogue rather than from the meter rows.</p>
     *
     * @param usage   the written figures for the period, or {@code null} when the meter holds none
     * @param pending calls counted but not yet flushed, by surface
     * @param tenant  whose usage this is
     * @param period  the UTC day, ISO-8601
     */
    public static String render(ShTenantUsage usage, Map<String, Long> pending,
                                String tenant, String period) {
        StringBuilder out = new StringBuilder();
        out.append(ShTerse.line("shio-usage")
                .attr("tenant", tenant)
                .attr("period", period)
                .build());
        Map<String, Long> written = usage == null ? Map.of() : usage.callsBySurface();
        long total = 0;
        long totalPending = 0;
        for (String surface : SURFACES) {
            long calls = written.getOrDefault(surface, 0L);
            long waiting = pending.getOrDefault(surface, 0L);
            total += calls;
            totalPending += waiting;
            out.append(ShTerse.line("calls")
                    .word(surface)
                    .attr("written", calls)
                    .attr("pending", waiting)
                    .build());
        }
        out.append(ShTerse.line("calls")
                .word("total")
                .attr("written", total)
                .attr("pending", totalPending)
                .build());
        // A level, not a flow, and labelled so nobody averages it over the period: this is what is
        // held right now, where the rows above are what accumulated across the day.
        out.append(ShTerse.line("storage")
                .attr("bytes", usage == null ? 0L : usage.storageBytes())
                .attr("plan", usage == null ? null : usage.plan())
                .build());
        return out.toString();
    }
}
