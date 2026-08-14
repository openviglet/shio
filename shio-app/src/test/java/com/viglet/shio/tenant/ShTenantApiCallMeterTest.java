/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.repository.tenant.ShTenantApiCallRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

/**
 * SH45 — API calls are metered as a flow, and a closed period can be exported.
 *
 * <h2>What these assertions are actually protecting</h2>
 *
 * <p>SH22 named three resources to meter and SH746 shipped the second of them. The third could not
 * reuse either existing shape and both refusals are asserted here rather than only argued in a
 * javadoc: storage is a <b>level</b> that fits in one column, and {@code ShCdaRateLimiter} already
 * counts calls but keeps its buckets in one JVM's heap, so a restart loses the count and a second
 * node doubles the budget. What replaces them is a durable per-period bucket moved by
 * <b>addition</b>, which is the one property that makes many nodes and many flushes agree — and the
 * one a read-modify-write would silently lack.</p>
 *
 * <h2>It shares {@code ShTenantStorageMeterTest}'s context on purpose</h2>
 *
 * <p>Same {@code @SpringBootTest} properties, to the character, so SH347's context cache keys one
 * entry for both — the headroom against the ceiling is zero, and a new key here would spend the
 * last slot on a test that does not need one. Tuning {@code shio.meter.flush-interval-millis} would
 * have been the natural thing to write and would have cost exactly that. It is also unnecessary:
 * every meter below is built with its own fixed clock and its own accumulator, so the bean's
 * scheduled flush drains a map these assertions never touch.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShTenantApiCallMeterTest {

    private static final String TENANT = "ut-sh45-tenant";

    @Autowired
    private ShTenantApiCallRepository repository;
    @Autowired
    private ShTenantRepository shTenantRepository;
    @Autowired
    private ShMeterProperties properties;
    @Autowired
    private ShBillingExportService exportService;

    /** A meter whose day never moves, so a bucket key is a constant the assertion can name. */
    private ShTenantApiCallMeter meterAt(String day) {
        return new ShTenantApiCallMeter(repository, properties, null,
                Clock.fixed(Instant.parse(day + "T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("SH45: a call is counted per tenant, day and surface, and survives the flush")
    void aCallLandsInItsBucket() {
        ShTenantApiCallMeter meter = meterAt("2026-01-02");
        meter.record(TENANT, ShTenantApiCallMeter.CDA);
        meter.record(TENANT, ShTenantApiCallMeter.CDA);
        meter.record(TENANT, ShTenantApiCallMeter.AGENT);

        assertThat(meter.pendingCalls(TENANT, ShTenantApiCallMeter.CDA))
                .as("SH45: counted in heap first — a row per call would make every request a write")
                .isEqualTo(2);
        assertThat(meter.persistedCalls(TENANT, "2026-01-02", ShTenantApiCallMeter.CDA))
                .as("nothing is durable until the flush, which is the window the interval bounds")
                .isZero();

        assertThat(meter.flush()).isEqualTo(3);

        assertThat(meter.persistedCalls(TENANT, "2026-01-02", ShTenantApiCallMeter.CDA))
                .as("SH45: nothing metered API calls at all — the resource SH22 named and the one "
                        + "ShCdaRateLimiter's in-heap buckets cannot be, because a bill cannot be "
                        + "computed from a number a restart loses")
                .isEqualTo(2);
        assertThat(meter.persistedCalls(TENANT, "2026-01-02", ShTenantApiCallMeter.AGENT))
                .as("SH45: the surfaces are kept apart, because which of them a plan charges for is "
                        + "a pricing decision and totalling them here would throw the answer away")
                .isEqualTo(1);
        assertThat(meter.pendingCalls(TENANT, ShTenantApiCallMeter.CDA))
                .as("the accumulator is drained by the flush, not merely read")
                .isZero();
    }

    @Test
    @DisplayName("SH45: flushes add to a bucket rather than replace it — the multi-node property")
    void theFlushIsAdditive() {
        ShTenantApiCallMeter meter = meterAt("2026-01-03");
        meter.record(TENANT, ShTenantApiCallMeter.CONSOLE);
        meter.flush();
        meter.record(TENANT, ShTenantApiCallMeter.CONSOLE);
        meter.record(TENANT, ShTenantApiCallMeter.CONSOLE);
        meter.flush();

        assertThat(meter.persistedCalls(TENANT, "2026-01-03", ShTenantApiCallMeter.CONSOLE))
                .as("SH45: addition commutes and a read-modify-write does not. This is the whole "
                        + "reason a flow CAN be metered from many nodes where SH3's level could not: "
                        + "100 from one node and 100 from another leave 200 in either order, and the "
                        + "second flush of the same bucket must not overwrite the first")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("SH45: the day is part of the key, so a period rolls into a new bucket")
    void thePeriodIsPartOfTheKey() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-04T23:59:00Z"));
        ShTenantApiCallMeter meter = new ShTenantApiCallMeter(repository, properties, null,
                Clock.fixed(now.get(), ZoneOffset.UTC));
        meter.record(TENANT, ShTenantApiCallMeter.CDA);
        meter.flush();

        ShTenantApiCallMeter nextDay = meterAt("2026-01-05");
        nextDay.record(TENANT, ShTenantApiCallMeter.CDA);
        nextDay.flush();

        assertThat(meter.persistedCalls(TENANT, "2026-01-04", ShTenantApiCallMeter.CDA))
                .as("SH45: a call is a FLOW — the billing question is calls per period, so the "
                        + "period is part of the key. A cumulative column would grow forever and "
                        + "resetting it monthly is a job nobody designed")
                .isEqualTo(1);
        assertThat(nextDay.persistedCalls(TENANT, "2026-01-05", ShTenantApiCallMeter.CDA))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SH45: a call nobody can attribute is not counted")
    void anUnattributableCallIsDropped() {
        ShTenantApiCallMeter meter = meterAt("2026-01-06");
        meter.record(null, ShTenantApiCallMeter.CDA);
        meter.record("  ", ShTenantApiCallMeter.CDA);

        assertThat(meter.flush())
                .as("SH45: filing an unresolved tenant under the default would bill one tenant for "
                        + "another's traffic, which is worse than not counting it")
                .isZero();
    }

    @Test
    @DisplayName("SH45: the switch is honoured, and it defaults to on")
    void meteringCanBeTurnedOff() {
        assertThat(properties.isEnabled())
                .as("SH45: a meter that ships off reads zero, and a zero nobody can tell apart from "
                        + "'no traffic' is the SH751 failure repeated on a new table")
                .isTrue();

        properties.setEnabled(false);
        try {
            ShTenantApiCallMeter meter = meterAt("2026-01-07");
            meter.record(TENANT, ShTenantApiCallMeter.CDA);
            assertThat(meter.pendingCalls(TENANT, ShTenantApiCallMeter.CDA)).isZero();
        } finally {
            properties.setEnabled(true);
        }
    }

    @Test
    @DisplayName("SH45: a period's usage names every tenant, including the ones that used nothing")
    void usageCoversTheWholeCatalog() {
        ShTenantApiCallMeter meter = meterAt("2026-01-08");
        meter.record(ShTenant.DEFAULT_TENANT_NAME, ShTenantApiCallMeter.AGENT);
        meter.flush();

        List<ShTenantUsage> usage = exportService.usage("2026-01-08");

        assertThat(usage)
                .as("SH45: driven from the tenant catalog, not from the meter rows — 'used nothing' "
                        + "is an answer a bill needs, and an absent row is indistinguishable from a "
                        + "tenant the export forgot")
                .extracting(ShTenantUsage::tenant)
                .contains(ShTenant.DEFAULT_TENANT_NAME);

        ShTenantUsage seeded = usage.stream()
                .filter(row -> ShTenant.DEFAULT_TENANT_NAME.equals(row.tenant()))
                .findFirst()
                .orElseThrow();
        assertThat(seeded.callsBySurface()).containsEntry(ShTenantApiCallMeter.AGENT, 1L);
        assertThat(seeded.totalCalls())
                .as("SH45: a caller that does not price by surface never has to learn the vocabulary")
                .isEqualTo(1);
        assertThat(seeded.period()).isEqualTo("2026-01-08");
    }

    @Test
    @DisplayName("SH45: the export hook fires on a rollover the node watched, and never before one")
    void theExportHookFiresOnceOnRollover() {
        AtomicReference<String> exported = new AtomicReference<>();
        ShBillingExportService service = new ShBillingExportService(
                shTenantRepository, repository, properties,
                List.of((period, usage) -> exported.set(period)));
        properties.setExportOnRollover(true);
        try {
            assertThat(service.exportIfPeriodClosed("2026-01-09"))
                    .as("SH45: the first observation adopts a period rather than exporting one this "
                            + "node never watched close — otherwise every restart is a re-delivery "
                            + "and every deploy a duplicate invoice")
                    .isNull();
            assertThat(exported.get()).isNull();

            assertThat(service.exportIfPeriodClosed("2026-01-10"))
                    .as("SH45: there were no billing-export hooks at all — a meter nothing can read "
                            + "out of is a number, not a bill")
                    .isEqualTo("2026-01-09");
            assertThat(exported.get()).isEqualTo("2026-01-09");

            exported.set(null);
            assertThat(service.exportIfPeriodClosed("2026-01-10"))
                    .as("the same period does not close twice on one node")
                    .isNull();
            assertThat(exported.get()).isNull();
        } finally {
            properties.setExportOnRollover(false);
        }
    }

    @Test
    @DisplayName("SH45: exporting is opt-in, and off is the default")
    void exportingIsOptIn() {
        AtomicReference<String> exported = new AtomicReference<>();
        ShBillingExportService service = new ShBillingExportService(
                shTenantRepository, repository, properties,
                List.of((period, usage) -> exported.set(period)));

        assertThat(properties.isExportOnRollover())
                .as("SH45/P7: metering is an internal number and exporting sends it somewhere — "
                        + "anything outward-facing is opt-in")
                .isFalse();

        service.exportIfPeriodClosed("2026-01-11");
        service.exportIfPeriodClosed("2026-01-12");
        assertThat(exported.get()).isNull();
    }
}
