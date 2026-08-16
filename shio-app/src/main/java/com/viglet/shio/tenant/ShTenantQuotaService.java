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

    /**
     * Throw 402 when the current tenant's plan has no room for {@code bytes} more (SH110).
     *
     * <h2>What had to change upstream before this could exist</h2>
     *
     * <p>SH45 deliberately shipped no {@code checkCanStore}, and the reason was the shared kernel's
     * signature rather than an oversight: {@code VigletQuotaService.check(resource)} compared the
     * <em>current</em> count against the limit, which is exact for sites and posts because each
     * write adds precisely one. Bytes do not. A single 1 GB upload under a 512 MB plan passed that
     * test whenever the tenant was at zero, so the check would have advertised a guarantee it did
     * not give — worse than having none.</p>
     *
     * <p>{@code viglet-core} 2026.3.11 adds {@code check(resource, delta)}, so the question can be
     * asked properly: not "is this tenant already full" but "does what I am about to write fit".
     * The one-argument form is that method at {@code delta = 1}, which is why nothing else moved.</p>
     *
     * <p>Called <b>before</b> the bytes are written and before the post row is created, because a
     * refusal after either leaves the tenant holding something they were told they could not have.
     * The meter SH751 wired is what makes the current figure O(1) to read here.</p>
     */
    public void checkCanStore(long bytes) {
        quotaService.check(ShVigletResourceCounter.STORAGE, bytes);
    }

    /** Throw 402 when the current tenant's plan has no room for another site. */
    public void checkCanCreateSite() {
        quotaService.check(ShVigletResourceCounter.SITES);
    }

    /** Throw 402 when the current tenant's plan has no room for another post. */
    public void checkCanCreatePost() {
        quotaService.check(ShVigletResourceCounter.POSTS);
    }

    /**
     * Throw 402 when the current tenant's plan has no room for {@code count} more posts (SH750).
     *
     * <p>{@code folder.copy} (SH244) is the first write on this surface that adds an unbounded
     * number in one call, and it could only ask the per-one question: a tenant one post below its
     * ceiling could copy a section of fifty and land fifty over it, refused nowhere. Asking once
     * was better than the console's none and was never a guarantee, and the code said so.</p>
     *
     * <p>Two things §XII.28 ruled out while the SPI was missing, both still wrong now that it is
     * not. <b>Looping the check</b> reads a count the copy has not written yet, so N calls answer
     * one question N times and refuse nothing. <b>Refusing on size</b> invents a second quota model
     * here, and the failure that causes — a legitimate section copy refused on a generous plan — is
     * worse than the one it prevents. The delta is the third answer and the only exact one.</p>
     */
    public void checkCanCreatePosts(long count) {
        quotaService.check(ShVigletResourceCounter.POSTS, count);
    }
}
