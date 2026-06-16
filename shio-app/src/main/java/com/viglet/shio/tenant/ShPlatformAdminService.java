/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.viglet.core.tenancy.VigletPlatformAdminService;
import com.viglet.core.tenancy.VigletTenantContext;

/**
 * T397 / §XIV.9 — Shio's cross-tenant privilege gate: the {@code viglet-core}
 * tenancy kernel ({@link VigletPlatformAdminService}) exposed as a Shio
 * {@code @Service} bean. Adopting it closes the gap §XIV.9 calls out — Shio
 * previously had only the {@code ROLE_ADMIN} bypass inside {@code ShTenantFilter},
 * with no audited {@code runForTenant} / {@code runAsSystem} primitives.
 *
 * <p>Shio's existing admin authority is {@link #ROLE_ADMIN}, so this overrides
 * {@link #isPlatformAdmin()} to honour <em>either</em> {@code ROLE_ADMIN}
 * (Shio's platform admin) or the shared {@link #ROLE_PLATFORM_ADMIN}. The shared
 * resolution-filter gate and teardown service therefore keep treating Shio admins
 * as platform admins exactly as the legacy filter did, while gaining the audited
 * cross-tenant primitives for free.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class ShPlatformAdminService extends VigletPlatformAdminService {

    /** Shio's existing platform-admin authority (the legacy {@code ShTenantFilter} bypass). */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    public ShPlatformAdminService(VigletTenantContext tenantContext) {
        super(tenantContext);
    }

    @Override
    public boolean isPlatformAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if (ROLE_PLATFORM_ADMIN.equals(role) || ROLE_ADMIN.equals(role)) {
                return true;
            }
        }
        return false;
    }
}
