/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import org.springframework.stereotype.Service;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletQuotaService;

/**
 * Per-plan create quotas (SH22/D). Enforced with HTTP 402 at site / post creation.
 * A <strong>no-op</strong> when tenancy is off or for the reserved default
 * {@code shio} tenant, so single-tenant installs are unaffected.
 *
 * <p>T397 / §XIV.9 — re-homed onto the shared {@link VigletQuotaService}: the
 * compare-and-throw enforcement (and its "no-op when tenancy is off / for the
 * default tenant" guard) now lives once in {@code viglet-core-tenancy}, driven by
 * Shio's {@link ShVigletResourceCounter} (the per-plan limits + the per-tenant
 * counts). This class keeps the Shio-named, per-resource entry points so the
 * create endpoints read clearly; the resource keys are
 * {@link ShVigletResourceCounter#SITES} and {@link ShVigletResourceCounter#POSTS}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShTenantQuotaService {

    private final VigletQuotaService quotaService;

    public ShTenantQuotaService(VigletQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /**
     * The current tenant's limits, mapped back to the Shio shape (so existing
     * call-sites and assertions read {@link ShTenantPlanLimits#UNLIMITED} /
     * {@link ShTenantPlanLimits#FREE}). Unbounded resources surface as {@code -1}.
     */
    public ShTenantPlanLimits currentLimits() {
        VigletPlanLimits limits = quotaService.currentLimits();
        return new ShTenantPlanLimits(
                limits.limit(ShVigletResourceCounter.SITES),
                limits.limit(ShVigletResourceCounter.POSTS));
    }

    /** Throw 402 when the current tenant's plan has no room for another site. */
    public void checkCanCreateSite() {
        quotaService.check(ShVigletResourceCounter.SITES);
    }

    /** Throw 402 when the current tenant's plan has no room for another post. */
    public void checkCanCreatePost() {
        quotaService.check(ShVigletResourceCounter.POSTS);
    }
}
