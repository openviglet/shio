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
                limits.limit(ShVigletResourceCounter.POSTS),
                limits.limit(ShVigletResourceCounter.STORAGE));
    }

    /*
     * SH45 — there is deliberately no `checkCanStore` here yet, and the reason is the shared
     * kernel's signature rather than an oversight.
     *
     * `VigletQuotaService.check(resource)` compares the CURRENT count against the limit, which is
     * exact for sites and posts because each write adds precisely one. Bytes do not: a single
     * 1 GB upload under a 512 MB plan passes that test whenever the tenant is currently at zero,
     * so the check would report a guarantee it does not provide — worse than having none, and the
     * "optimize a benchmark by degrading correctness" non-goal wearing a different hat.
     *
     * Enforcing storage needs `check(resource, delta)` in the shared kernel, which is a change to
     * Viglet's quota SPI and not Shio's to make unilaterally. What ships here is the METER, which
     * is what SH45's line actually names as missing; `currentLimits()` already reports the storage
     * bound, so a caller can ask and decide.
     */

    /** Throw 402 when the current tenant's plan has no room for another site. */
    public void checkCanCreateSite() {
        quotaService.check(ShVigletResourceCounter.SITES);
    }

    /** Throw 402 when the current tenant's plan has no room for another post. */
    public void checkCanCreatePost() {
        quotaService.check(ShVigletResourceCounter.POSTS);
    }
}
