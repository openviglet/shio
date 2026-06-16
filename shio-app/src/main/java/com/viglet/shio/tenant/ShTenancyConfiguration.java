/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.core.tenancy.VigletTenantProvisioner;
import com.viglet.core.tenancy.VigletTenantSlugValidator;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.property.ShAppProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * T397 / §XIV.9 — Shio's tenancy wiring onto the expanded shared
 * {@code viglet-core-tenancy} surface. The shared services
 * ({@code VigletTenantMembershipService}, {@code VigletQuotaService},
 * {@code VigletTenantTeardownService}, {@code VigletInfraTenantScope}, and the
 * {@code VigletTenantResolutionFilter} base) auto-configure themselves the moment
 * Shio's SPI beans ({@link ShVigletTenantStore}, {@link ShVigletTenantResolver},
 * {@link ShVigletResourceCounter}, {@link ShVigletTenantPurger},
 * {@link ShPlatformAdminService}) are present.
 *
 * <p>This class supplies the three product policies the shared surface needs:
 * <ul>
 *   <li>the {@link VigletTenantContext} kernel, wired from {@code shio.multi-tenant}
 *       and the reserved default tenant {@code shio} (satisfying the core
 *       auto-configuration's {@code @ConditionalOnMissingBean} so the generic
 *       {@code viglet.tenancy.*} bean never activates) — its tenant state is the
 *       same thread-local the static {@link ShTenantContext} facade exposes;</li>
 *   <li>the slug validator's Shio-specific reserved names;</li>
 *   <li>the post-signup provisioning hook (best-effort) bridged to
 *       {@link ShTenantProvisioningService}.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class ShTenancyConfiguration {

    /**
     * The shared tenancy kernel, wired from {@code shio.multi-tenant} and the
     * reserved default tenant. {@link VigletTenantContext} holds its tenant/system
     * state in thread-locals, so this bean and the static {@link ShTenantContext}
     * facade observe the same per-request tenant regardless of instance identity.
     */
    @Bean
    public VigletTenantContext vigletTenantContext(ShAppProperties shAppProperties) {
        return new VigletTenantContext(shAppProperties.isMultiTenant(), ShTenant.DEFAULT_TENANT_NAME);
    }

    /**
     * The shared slug validator with Shio's extra reserved labels on top of the
     * platform-neutral defaults — so signup rejects {@code shio}, {@code store} and
     * {@code file_source} exactly as the legacy {@code ShTenantMembershipService}
     * did. Overrides the auto-config default ({@code @ConditionalOnMissingBean}).
     */
    @Bean
    public VigletTenantSlugValidator vigletTenantSlugValidator() {
        return new VigletTenantSlugValidator(Set.of("shio", "store", "file_source"));
    }

    /**
     * The post-signup provisioning hook the shared {@code VigletTenantMembershipService}
     * invokes after the registry row + OWNER membership are created. Bridged to
     * Shio's exchange-engine clone and kept <em>best-effort</em>: a provisioning
     * failure must not undo the tenant + membership (the explicit
     * {@code POST /tenant/{id}/provision} endpoint can retry).
     */
    @Bean
    public VigletTenantProvisioner vigletTenantProvisioner(
            ShTenantProvisioningService shTenantProvisioningService) {
        return tenantId -> {
            try {
                shTenantProvisioningService.provision(tenantId);
            } catch (RuntimeException e) {
                log.warn("Tenant '{}' created but provisioning failed: {}", tenantId, e.getMessage());
            }
        };
    }
}
