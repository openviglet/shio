/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.core.tenancy.VigletTenantResolutionFilter;

/**
 * SH22 — resolves the <em>current tenant</em> for each request and binds it to the
 * tenant context for the request thread.
 *
 * <p>T397 / §XIV.9 — re-homed onto the shared {@link VigletTenantResolutionFilter}:
 * this class is now a thin Shio-named bean that supplies the lifted gate
 * (suspended → 403, non-member → 403, platform-admin bypass, always-clear — the
 * cross-tenant-leak surface that used to be written once per product) with Shio's
 * own resolution sources via {@link ShVigletTenantResolver} and Shio's registry via
 * {@link ShVigletTenantStore}. Registered <strong>after</strong> the authentication
 * filters in {@code ShSecurityConfig}; the whole filter is a no-op when
 * {@code shio.multi-tenant=false} (the injected {@link VigletTenantContext} reports
 * tenancy disabled), so a single-tenant install behaves exactly as before.
 *
 * <p>The resolution-source constants live here (the {@code /switch} +
 * {@code /impersonate} endpoints write {@link #TENANT_SESSION_ATTR}; {@code
 * ShApiTokenFilter} writes {@link #TENANT_REQUEST_ATTR}); the resolution
 * <em>logic</em> lives in {@link ShVigletTenantResolver} and the gate in the shared
 * base.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class ShTenantFilter extends VigletTenantResolutionFilter {

    /**
     * Request attribute carrying the tenant of an authenticated CDA token (SH22
     * Part D). {@code ShApiTokenFilter} runs earlier in the chain and sets it; the
     * resolver prefers it over the principal-derived tenant, so token delivery is
     * scoped to the content owner's tenant. A token-scoped request is treated as a
     * {@code trusted} resolution — exempt from the membership gate, but a suspended
     * tenant still blocks.
     */
    public static final String TENANT_REQUEST_ATTR = "sh.tenantId";

    /** Session attribute set by {@code POST /tenant/{name}/switch} and {@code /impersonate} (SH22/C). */
    public static final String TENANT_SESSION_ATTR = "SH_TENANT";

    public ShTenantFilter(VigletTenantContext vigletTenantContext,
            ShVigletTenantStore store,
            ShVigletTenantResolver resolver,
            ShPlatformAdminService platformAdminService) {
        super(vigletTenantContext, store, resolver, platformAdminService);
    }
}
