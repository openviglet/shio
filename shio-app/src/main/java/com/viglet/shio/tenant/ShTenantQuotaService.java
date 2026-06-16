/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.property.ShAppProperties;

/**
 * Per-plan create quotas (SH22/D, mirrors Turing's {@code TurTenantQuotaService} —
 * but actually wired into the create paths). Enforced with HTTP 402 at site /
 * post creation. A <strong>no-op</strong> when tenancy is off or for the reserved
 * default {@code shio} tenant, so single-tenant installs are unaffected.
 *
 * <p>Counts come from the {@code @TenantId}-filtered repositories, so they are
 * already scoped to the current tenant.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShTenantQuotaService {

    private final ShAppProperties shAppProperties;
    private final ShTenantRepository shTenantRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShTenantQuotaService(ShAppProperties shAppProperties,
                                ShTenantRepository shTenantRepository,
                                ShSiteRepository shSiteRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shAppProperties = shAppProperties;
        this.shTenantRepository = shTenantRepository;
        this.shSiteRepository = shSiteRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /** The current tenant's limits, or {@link ShTenantPlanLimits#UNLIMITED} when quotas don't apply. */
    public ShTenantPlanLimits currentLimits() {
        if (!shAppProperties.isMultiTenant()) {
            return ShTenantPlanLimits.UNLIMITED;
        }
        String tenantId = ShTenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()
                || ShTenantContext.DEFAULT_TENANT.equals(tenantId)) {
            return ShTenantPlanLimits.UNLIMITED;
        }
        return shTenantRepository.findByName(tenantId)
                .map(ShTenant::getPlan)
                .map(ShTenantPlanLimits::forPlan)
                .orElse(ShTenantPlanLimits.UNLIMITED);
    }

    /** Throw 402 when the current tenant's plan has no room for another site. */
    public void checkCanCreateSite() {
        ShTenantPlanLimits limits = currentLimits();
        if (!limits.isUnlimited(limits.maxSites()) && shSiteRepository.count() >= limits.maxSites()) {
            throw quotaExceeded("max " + limits.maxSites() + " sites");
        }
    }

    /** Throw 402 when the current tenant's plan has no room for another post. */
    public void checkCanCreatePost() {
        ShTenantPlanLimits limits = currentLimits();
        if (!limits.isUnlimited(limits.maxPosts()) && shPostUnifiedRepository.count() >= limits.maxPosts()) {
            throw quotaExceeded("max " + limits.maxPosts() + " posts");
        }
    }

    private static ResponseStatusException quotaExceeded(String what) {
        return new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                "Plan limit reached: " + what + ". Upgrade your plan.");
    }
}
