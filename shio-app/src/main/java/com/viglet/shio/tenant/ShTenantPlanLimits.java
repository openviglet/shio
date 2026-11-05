/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.util.Locale;

/**
 * Per-plan create limits (SH22/D, mirrors Turing's {@code TurTenantPlanLimits}).
 * A negative limit means unlimited. {@code FREE} is bounded; any other (paid)
 * plan is unlimited — kept deliberately simple so plans can evolve without a
 * migration (the plan is a free-form string on {@link
 * com.viglet.shio.persistence.model.tenant.ShTenant}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShTenantPlanLimits(long maxSites, long maxPosts, long maxStorageBytes) {

    public static final ShTenantPlanLimits UNLIMITED = new ShTenantPlanLimits(-1, -1, -1);
    public static final ShTenantPlanLimits FREE = new ShTenantPlanLimits(2, 200, 512L * 1024 * 1024);

    /**
     * SH1352 — agent calls a FREE tenant may make per UTC day, across {@code /api/v2/agent/**} and
     * {@code /mcp}. A flow rather than a holding, so it is not a component of this record: it is keyed
     * on {@code ShVigletResourceCounter#AGENT_CALLS} and counted from the SH45 meter. A placeholder
     * until SH1357 prices the phone's run; changing it is one number here.
     */
    public static final long FREE_AGENT_CALLS_PER_DAY = 500;

    /** Resolve the limits for a (free-form) plan id; unknown / paid plans are unlimited. */
    public static ShTenantPlanLimits forPlan(String plan) {
        if (plan == null) {
            return FREE;
        }
        return "FREE".equals(plan.trim().toUpperCase(Locale.ROOT)) ? FREE : UNLIMITED;
    }

    public boolean isUnlimited(long limit) {
        return limit < 0;
    }
}
