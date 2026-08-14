/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.util.List;

/**
 * Where a closed period's usage goes (SH45) — the billing-export hook.
 *
 * <p>An SPI and not an endpoint, because a bill is not something an operator fetches by hand once a
 * month: publish a bean implementing this and it receives every tenant's {@link ShTenantUsage} the
 * first time the meter notices the UTC day has rolled. Shio computes no prices and holds no
 * account — it says what was used, and the thing that knows what that costs lives outside.</p>
 *
 * <h2>Two properties an implementation must have</h2>
 *
 * <ul>
 *   <li><b>Idempotent.</b> Delivery is at-least-once: every node running the meter exports the
 *       period it saw close, so a two-node install delivers twice. The natural key is
 *       {@code (tenant, period)} and it is carried in the record, so an upsert is enough.</li>
 *   <li><b>Cheap, or its own thread.</b> This is called on the meter's scheduled thread, inline.
 *       An exporter that blocks on a remote billing system delays the next flush of every tenant's
 *       counters.</li>
 * </ul>
 *
 * <p>A throwing exporter is logged and skipped, and the others still run — one billing integration
 * being down is not a reason for the rest to lose the period.</p>
 *
 * <p>Nothing is exported unless {@code shio.meter.export-on-rollover=true}. Metering is an internal
 * number; exporting sends it somewhere, and P7 makes anything outward-facing opt-in.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@FunctionalInterface
public interface ShBillingExporter {

    /**
     * Hand over a closed period.
     *
     * @param period the UTC day that just closed, ISO-8601
     * @param usage  one record per tenant in the catalog, ordered by tenant name; a tenant that
     *               made no calls is present with an empty {@code callsBySurface}, because "used
     *               nothing" is an answer a bill needs and an absent row is not
     */
    void export(String period, List<ShTenantUsage> usage);
}
