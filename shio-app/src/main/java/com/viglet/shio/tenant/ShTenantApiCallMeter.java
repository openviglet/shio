/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.shio.persistence.model.tenant.ShTenantApiCall;
import com.viglet.shio.persistence.repository.tenant.ShTenantApiCallRepository;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * How many API calls a tenant made, per day and per surface (SH45).
 *
 * <h2>Why this could not be the storage meter's shape, and could not be SH3's limiter</h2>
 *
 * <p>SH746 shipped the other half of SH45 as one maintained column, because storage is a
 * <b>level</b>: how much is held right now, which is what
 * {@code VigletResourceCounter.count(tenant, resource)} models and why it fitted. An API call is a
 * <b>flow</b> — the billing question is calls <em>per period</em> — so the period is part of the
 * key and the row is the bucket.</p>
 *
 * <p>Shio already counts calls and it could not be reused: {@code ShCdaRateLimiter} is a per-token
 * bucket whose own javadoc disqualifies it, because the buckets live in <b>this JVM's heap</b>, so
 * a multi-node deployment gets {@code N x} the budget and a restart loses the count. Acceptable for
 * a burst guard; disqualifying for anything a bill is computed from.</p>
 *
 * <h2>The accumulator in front of the table is not the same defect</h2>
 *
 * <p>This class also counts in heap, and the difference is that it does not <em>decide</em>
 * anything from what it holds — it adds it to a durable figure and forgets it. The flush is
 * {@code calls = calls + delta} in the database, which commutes: two nodes flushing 100 each leave
 * 200 whichever order they arrive in, and a restart at worst loses one flush window's increments
 * rather than the count. A row per call would be exact and would also make every request a write,
 * which is a cost this task did not measure and would not have been able to defend.</p>
 *
 * <p>{@link java.util.concurrent.atomic.AtomicLong#getAndSet} rather than removing the entry, and
 * that is the part worth reading twice: draining by removal loses whatever a concurrent request
 * added to the map entry between the read and the removal. {@code getAndSet(0)} is atomic, and an
 * increment that lands a nanosecond later simply belongs to the next flush.</p>
 *
 * <h2>What counts as a call</h2>
 *
 * <p>All three surfaces, labelled — {@link #CDA}, {@link #AGENT}, {@link #CONSOLE}. "CDA only, or
 * the agent and console too?" is a <em>pricing</em> question, and answering it here would throw the
 * other two away permanently; recording them apart lets a plan decide later without a migration.
 * The classifier is {@link ShTenantApiCallFilter}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Slf4j
public class ShTenantApiCallMeter {

    /** Content delivery — {@code /api/v2/cda/**} and {@code /graphql}. */
    public static final String CDA = "cda";
    /** The agent surface — {@code /api/v2/agent/**} and {@code /mcp}. */
    public static final String AGENT = "agent";
    /** Everything else under {@code /api/**} — what the console and the CLI drive. */
    public static final String CONSOLE = "console";

    /** One bucket of the meter: the grain of both the table's unique key and this map. */
    record Bucket(String tenant, String period, String surface) {
    }

    private final ShTenantApiCallRepository repository;
    private final ShMeterProperties properties;
    private final ObjectProvider<ShBillingExportService> billingExport;
    private final Clock clock;
    private final ConcurrentHashMap<Bucket, AtomicLong> pending = new ConcurrentHashMap<>();

    @Autowired
    public ShTenantApiCallMeter(ShTenantApiCallRepository repository, ShMeterProperties properties,
                                ObjectProvider<ShBillingExportService> billingExport) {
        this(repository, properties, billingExport, Clock.systemUTC());
    }

    ShTenantApiCallMeter(ShTenantApiCallRepository repository, ShMeterProperties properties,
                         ObjectProvider<ShBillingExportService> billingExport, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.billingExport = billingExport;
        this.clock = clock;
    }

    /** The bucket label for now — the UTC day, ISO-8601. */
    public String currentPeriod() {
        return LocalDate.now(clock).toString();
    }

    /**
     * Count one call. Cheap by construction: one map lookup and one atomic add, no I/O.
     *
     * <p>A blank tenant is dropped rather than filed under a default — a call nobody can attribute
     * is not a call somebody should be billed for.</p>
     */
    public void record(String tenant, String surface) {
        if (!properties.isEnabled() || tenant == null || tenant.isBlank() || surface == null) {
            return;
        }
        pending.computeIfAbsent(new Bucket(tenant, currentPeriod(), surface), key -> new AtomicLong())
                .incrementAndGet();
    }

    /** What has been counted but not yet written — the window a crash would lose. */
    public long pendingCalls(String tenant, String surface) {
        AtomicLong held = pending.get(new Bucket(tenant, currentPeriod(), surface));
        return held == null ? 0L : held.get();
    }

    /**
     * Write the accumulated increments to the meter table and start the next window.
     *
     * <p>Each bucket is its own transaction (the repository's {@code addCalls} is
     * {@code @Transactional}), so one bucket that fails does not roll back the rest — and the
     * increments it failed to write are put back, because losing them silently is the one outcome a
     * meter must not have.</p>
     *
     * @return how many calls were written
     */
    public long flush() {
        long written = 0;
        List<Bucket> emptied = new ArrayList<>();
        for (Map.Entry<Bucket, AtomicLong> entry : pending.entrySet()) {
            long delta = entry.getValue().getAndSet(0);
            if (delta == 0) {
                emptied.add(entry.getKey());
                continue;
            }
            try {
                persist(entry.getKey(), delta);
                written += delta;
            } catch (RuntimeException e) {
                // Put them back rather than drop them: the next flush writes them, and a meter that
                // quietly loses a window is worse than one that is a minute behind.
                entry.getValue().addAndGet(delta);
                log.warn("SH45: could not flush {} calls for {}: {}", delta, entry.getKey(),
                        e.getMessage());
            }
        }
        // A bucket for a period that has closed and holds nothing will never be touched again —
        // record() only ever addresses the current period — so dropping it is safe, and leaving it
        // would grow the map by (tenants x surfaces) every day this node stays up.
        String current = currentPeriod();
        emptied.stream().filter(bucket -> !current.equals(bucket.period())).forEach(pending::remove);
        return written;
    }

    /**
     * The scheduled flush. Not {@code @Transactional} itself — see {@link #flush()}: the unit of
     * work is one bucket, so a single unwritable row cannot cost the others their window.
     */
    @Scheduled(fixedDelayString = "${shio.meter.flush-interval-millis:60000}")
    public void scheduledFlush() {
        if (!properties.isEnabled()) {
            return;
        }
        flush();
        // After the write, never before: the last minute of a day flushed after the export would be
        // billed as part of the next one. Reached through a provider so the meter can be built in a
        // test without the export half.
        ShBillingExportService export = billingExport == null ? null : billingExport.getIfAvailable();
        if (export != null) {
            export.exportIfPeriodClosed(currentPeriod());
        }
    }

    /**
     * Flush on the way down, so an orderly restart loses nothing at all. A kill loses at most one
     * window, which is what {@code shio.meter.flush-interval-millis} buys.
     */
    @PreDestroy
    public void flushOnShutdown() {
        try {
            flush();
        } catch (RuntimeException e) {
            log.warn("SH45: shutdown flush failed: {}", e.getMessage());
        }
    }

    /** What the table holds for one bucket, ignoring anything still pending. */
    public long persistedCalls(String tenant, String period, String surface) {
        return repository.findByTenantNameAndPeriodKeyAndSurface(tenant, period, surface)
                .map(ShTenantApiCall::getCalls)
                .orElse(0L);
    }

    private void persist(Bucket bucket, long delta) {
        if (repository.addCalls(bucket.tenant(), bucket.period(), bucket.surface(), delta) > 0) {
            return;
        }
        try {
            ShTenantApiCall row = new ShTenantApiCall();
            row.setTenantName(bucket.tenant());
            row.setPeriodKey(bucket.period());
            row.setSurface(bucket.surface());
            row.setCalls(delta);
            repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            // Another node inserted the same bucket between the update and this insert. The unique
            // key is what turns that race into a retry instead of a duplicate row.
            repository.addCalls(bucket.tenant(), bucket.period(), bucket.surface(), delta);
        }
    }
}
