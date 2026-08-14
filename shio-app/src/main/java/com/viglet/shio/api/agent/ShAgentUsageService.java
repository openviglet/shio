/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.shio.tenant.ShBillingExportService;
import com.viglet.shio.tenant.ShTenantApiCallMeter;
import com.viglet.shio.tenant.ShTenantContext;
import com.viglet.shio.tenant.ShTenantUsage;

/**
 * The current tenant's spend, assembled once and rendered the same way everywhere (SH754).
 *
 * <p>P10 — a shape over what SH45 already holds, not a third contract: the written figures come
 * from {@link ShBillingExportService} (the same source {@code GET /api/v2/tenant/usage} reads) and
 * the unflushed ones from {@link ShTenantApiCallMeter}. Both the {@code shio://usage} resource and
 * {@code GET /agent/context?include=usage} call this, so the two are byte-identical by
 * construction rather than by review.</p>
 *
 * <p>The current tenant only, and never a parameter naming another. The REST route is scoped that
 * way and the agent path is never a privilege escalation (SH201) — reading every tenant's spend is
 * a platform-operator question, and it goes out through the exporter hook.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentUsageService {

    private final ShBillingExportService shBillingExportService;
    private final ShTenantApiCallMeter shTenantApiCallMeter;

    public ShAgentUsageService(ShBillingExportService shBillingExportService,
                               ShTenantApiCallMeter shTenantApiCallMeter) {
        this.shBillingExportService = shBillingExportService;
        this.shTenantApiCallMeter = shTenantApiCallMeter;
    }

    /** Today's usage for the request's tenant, terse. */
    public String terse() {
        String tenant = tenant();
        String period = shTenantApiCallMeter.currentPeriod();
        List<ShTenantUsage> written = shBillingExportService.usageFor(tenant, period, period);
        Map<String, Long> pending = new LinkedHashMap<>();
        for (String surface : List.of(ShTenantApiCallMeter.CDA, ShTenantApiCallMeter.AGENT,
                ShTenantApiCallMeter.CONSOLE)) {
            pending.put(surface, shTenantApiCallMeter.pendingCalls(tenant, surface));
        }
        return ShAgentUsageTerse.render(
                written.isEmpty() ? null : written.get(0), pending, tenant, period);
    }

    /**
     * The request's tenant, defaulting the way the storage layer does. A single-tenant install
     * resolves to the reserved {@code shio} tenant rather than to nothing, so the document says
     * whose spend it is even where there is only one answer.
     */
    private static String tenant() {
        String resolved = ShTenantContext.getTenantId();
        return resolved != null && !resolved.isBlank() ? resolved : ShTenantContext.DEFAULT_TENANT;
    }
}
