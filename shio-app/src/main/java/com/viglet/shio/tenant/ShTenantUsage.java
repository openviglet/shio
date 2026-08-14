/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.util.Map;

/**
 * What one tenant used in one period — the unit a bill is computed from (SH45).
 *
 * <p>Carries both shapes SH22 named, because a bill needs both and they are not the same kind of
 * number: {@code storageBytes} is a <b>level</b> read at the moment of export (what is held now),
 * and {@code callsBySurface} is a <b>flow</b> accumulated over the period. Naming that difference
 * in the record is the point — an exporter that averaged the level or that took the last value of
 * the flow would be wrong in a way no type would have caught.</p>
 *
 * @param tenant         the tenant's name — the value the {@code tenant_id} discriminator carries
 * @param plan           the plan on the catalog row at export time, free-form (e.g. {@code FREE})
 * @param period         the bucket label, a UTC day in ISO-8601 ({@code 2026-08-14})
 * @param storageBytes   bytes held at export time (SH746's meter), not an average over the period
 * @param callsBySurface calls in the period, keyed by {@code cda} / {@code agent} / {@code console};
 *                       a surface with no calls is absent rather than zero
 * @param totalCalls     the sum of {@code callsBySurface}, so a caller that does not price by
 *                       surface never has to know the vocabulary
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShTenantUsage(
        String tenant,
        String plan,
        String period,
        long storageBytes,
        Map<String, Long> callsBySurface,
        long totalCalls) {
}
