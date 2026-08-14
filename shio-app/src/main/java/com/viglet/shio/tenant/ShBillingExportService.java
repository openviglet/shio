/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantApiCall;
import com.viglet.shio.persistence.repository.tenant.ShTenantApiCallRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.utils.ShStableMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Assembles what each tenant used, and hands a closed period to the {@link ShBillingExporter} beans
 * (SH45).
 *
 * <h2>Two ways out, and they answer different questions</h2>
 *
 * <ul>
 *   <li><b>Pull</b> — {@link #usage(String)} / {@link #usageFor(String, String, String)}: what an
 *       operator or the console asks for, any period, at any time. Always available.</li>
 *   <li><b>Push</b> — {@link #exportIfPeriodClosed(String)}: the hook a billing system plugs into,
 *       invoked by the meter's own scheduled flush when it first notices the UTC day has rolled.
 *       Opt-in via {@code shio.meter.export-on-rollover}.</li>
 * </ul>
 *
 * <h2>What "closed" means here, and what it deliberately does not</h2>
 *
 * <p>The rollover is detected against a period this node has actually <b>seen</b>, held in heap.
 * A node that starts up mid-morning adopts today and exports nothing for yesterday — which is the
 * conservative direction: the alternative, exporting whatever period looks unexported at startup,
 * makes every restart a re-delivery and every deploy a duplicate invoice. The pull API is the
 * backstop for a period no node was up to watch close, and it is the reason this can afford to be
 * the simple thing.</p>
 *
 * <p>Delivery is therefore <b>at-least-once</b> across a multi-node install: each node exports the
 * close it saw. {@link ShBillingExporter} says so and requires idempotency, keyed on
 * {@code (tenant, period)}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Slf4j
public class ShBillingExportService {

    private final ShTenantRepository shTenantRepository;
    private final ShTenantApiCallRepository shTenantApiCallRepository;
    private final ShMeterProperties properties;
    private final List<ShBillingExporter> exporters;

    /**
     * The last period this node observed. {@code null} until the first flush, which is what makes a
     * fresh start adopt the current period instead of exporting one it never watched.
     */
    private final AtomicReference<String> lastSeenPeriod = new AtomicReference<>();

    public ShBillingExportService(ShTenantRepository shTenantRepository,
                                  ShTenantApiCallRepository shTenantApiCallRepository,
                                  ShMeterProperties properties,
                                  List<ShBillingExporter> exporters) {
        this.shTenantRepository = shTenantRepository;
        this.shTenantApiCallRepository = shTenantApiCallRepository;
        this.properties = properties;
        this.exporters = exporters;
    }

    /**
     * Every tenant's usage for one period, ordered by tenant name.
     *
     * <p>Driven from the tenant catalog rather than from the meter rows, so a tenant that made no
     * calls appears with an empty map. "Used nothing" is an answer a bill needs; an absent row is
     * indistinguishable from a tenant the export forgot.</p>
     */
    @Transactional(readOnly = true)
    public List<ShTenantUsage> usage(String period) {
        Map<String, Map<String, Long>> byTenant = new LinkedHashMap<>();
        for (ShTenantApiCall row : shTenantApiCallRepository
                .findByPeriodKeyOrderByTenantNameAscSurfaceAsc(period)) {
            byTenant.computeIfAbsent(row.getTenantName(), key -> new LinkedHashMap<>())
                    .merge(row.getSurface(), row.getCalls(), Long::sum);
        }
        List<ShTenantUsage> usage = new ArrayList<>();
        for (ShTenant tenant : shTenantRepository.findAllByOrderByNameAsc()) {
            usage.add(toUsage(tenant.getName(), tenant.getPlan(), tenant.getStorageBytes(), period,
                    byTenant.getOrDefault(tenant.getName(), Map.of())));
        }
        return usage;
    }

    /**
     * One tenant's usage over a range of periods, oldest first — what {@code GET
     * /api/v2/tenant/usage} answers. Bounds are inclusive and lexical, which is the same as
     * chronological for ISO-8601 dates and is why the period is stored as text.
     */
    @Transactional(readOnly = true)
    public List<ShTenantUsage> usageFor(String tenant, String fromPeriod, String toPeriod) {
        ShTenant row = shTenantRepository.findByName(tenant).orElse(null);
        String plan = row == null ? null : row.getPlan();
        long storageBytes = row == null ? 0L : row.getStorageBytes();

        Map<String, Map<String, Long>> byPeriod = new LinkedHashMap<>();
        for (ShTenantApiCall call : shTenantApiCallRepository
                .findByTenantNameAndPeriodKeyBetweenOrderByPeriodKeyAscSurfaceAsc(
                        tenant, fromPeriod, toPeriod)) {
            byPeriod.computeIfAbsent(call.getPeriodKey(), key -> new LinkedHashMap<>())
                    .merge(call.getSurface(), call.getCalls(), Long::sum);
        }
        List<ShTenantUsage> usage = new ArrayList<>();
        byPeriod.forEach((period, calls) ->
                usage.add(toUsage(tenant, plan, storageBytes, period, calls)));
        return usage;
    }

    /**
     * Export the period that just closed, if one did.
     *
     * <p>Called from {@code ShTenantApiCallMeter}'s scheduled flush, <b>after</b> it has written the
     * accumulator — otherwise the last minute of a day would be exported as part of the next one.
     * Returns the period exported, or {@code null} when nothing closed, the feature is off, or no
     * exporter is registered.</p>
     */
    public String exportIfPeriodClosed(String currentPeriod) {
        String previous = lastSeenPeriod.getAndSet(currentPeriod);
        if (previous == null || previous.equals(currentPeriod)) {
            return null;
        }
        if (!properties.isExportOnRollover() || exporters.isEmpty()) {
            return null;
        }
        List<ShTenantUsage> closed = usage(previous);
        for (ShBillingExporter exporter : exporters) {
            try {
                exporter.export(previous, closed);
            } catch (RuntimeException e) {
                // One integration being down is not a reason for the others to lose the period.
                log.warn("SH45: billing exporter {} failed for period {}: {}",
                        exporter.getClass().getSimpleName(), previous, e.getMessage());
            }
        }
        return previous;
    }

    /** Every period the meter holds, newest first. */
    @Transactional(readOnly = true)
    public List<String> periods() {
        return shTenantApiCallRepository.findPeriodKeys();
    }

    private static ShTenantUsage toUsage(String tenant, String plan, long storageBytes,
                                         String period, Map<String, Long> calls) {
        long total = calls.values().stream().mapToLong(Long::longValue).sum();
        // ShStableMap, not Map.copyOf: this map is serialized to an exporter and to
        // GET /api/v2/tenant/usage, and Map.copyOf iterates in an order seeded per JVM — the same
        // usage would export as different bytes after a restart, invisibly to any single-process
        // test. The source is a LinkedHashMap filled in the repository's declared order.
        return new ShTenantUsage(tenant, plan, period, storageBytes, ShStableMap.copyOf(calls), total);
    }
}
