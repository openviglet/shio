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

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletResourceCounter;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * T397 / §XIV.9 — the {@link VigletResourceCounter} SPI feeding the shared
 * {@code VigletQuotaService}: Shio supplies the per-plan limits and the current
 * per-tenant counts; the compare-and-throw enforcement (HTTP 402) is shared.
 *
 * <p>The resource keys are the opaque contract between this counter and the Shio
 * quota call sites: {@value #SITES}, {@value #POSTS}. Both counts come from the
 * {@code @TenantId}-filtered repositories, so {@code count()} is already scoped to
 * the current tenant.
 *
 * <p>Plan mapping (lifted from {@code ShTenantPlanLimits}): the {@code FREE} plan
 * is bounded (2 sites, 200 posts); any other (paid) plan is unlimited until a
 * richer billing model lands, so a paying tenant is never blocked by accident.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class ShVigletResourceCounter implements VigletResourceCounter {

    public static final String SITES = "sites";
    public static final String POSTS = "posts";

    /** The bounded FREE plan: 2 sites, 200 posts. */
    private static final VigletPlanLimits FREE = VigletPlanLimits.of(
            SITES, ShTenantPlanLimits.FREE.maxSites(), POSTS, ShTenantPlanLimits.FREE.maxPosts());

    private final ShSiteRepository shSiteRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShVigletResourceCounter(ShSiteRepository shSiteRepository,
            ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shSiteRepository = shSiteRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    @Override
    public long count(String tenantId, String resourceKey) {
        return switch (resourceKey) {
            case SITES -> shSiteRepository.count();
            case POSTS -> shPostUnifiedRepository.count();
            default -> 0L;
        };
    }

    @Override
    public VigletPlanLimits limitsFor(String plan) {
        if (plan != null && "FREE".equals(plan.trim().toUpperCase(Locale.ROOT))) {
            return FREE;
        }
        return VigletPlanLimits.UNLIMITED;
    }
}
