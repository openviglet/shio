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
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.core.tenancy.VigletTenantResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * T397 / §XIV.9 — the {@link VigletTenantResolver} SPI carrying <em>only</em>
 * Shio's resolution sources; the shared {@code VigletTenantResolutionFilter} owns
 * the gating (suspended → 403, non-member → 403, platform-admin bypass,
 * always-clear) that used to live in {@code ShTenantFilter}.
 *
 * <p>Resolution precedence, highest first (unchanged from the SH22 filter):
 * <ol>
 *   <li>the CDA-token request attribute {@link ShTenantFilter#TENANT_REQUEST_ATTR}
 *       set earlier by {@code ShApiTokenFilter} — marked
 *       {@link VigletTenantResolver.Resolution#trusted() trusted}, so the shared
 *       gate skips the per-user membership check (the token authorizes delivery to
 *       its tenant); a suspended tenant still blocks;</li>
 *   <li>the session attribute {@link ShTenantFilter#TENANT_SESSION_ATTR} (set by
 *       {@code POST /tenant/{name}/switch} and {@code /impersonate});</li>
 *   <li>the OIDC/OAuth2 {@code workspace_id} claim.</li>
 * </ol>
 *
 * <p>When none of these resolve, this returns {@link Resolution#none()} and the
 * shared filter binds the default {@code shio} tenant (Turing-parity drop-to-default,
 * adopted in T397). The legacy username-as-implicit-tenant fallback is removed: an
 * authenticated request with no explicit tenant now operates in the shared default
 * tenant rather than being denied.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class ShVigletTenantResolver implements VigletTenantResolver {

    private static final String WORKSPACE_ID_CLAIM = "workspace_id";

    @Override
    public Resolution resolve(HttpServletRequest request, Authentication authentication) {
        // A CDA token resolved its own tenant in ShApiTokenFilter — token-scoped
        // delivery vouches for that tenant and is exempt from the membership gate.
        Object tokenTenant = request.getAttribute(ShTenantFilter.TENANT_REQUEST_ATTR);
        if (tokenTenant instanceof String tenant && StringUtils.hasText(tenant)) {
            return Resolution.trusted(tenant);
        }
        // An explicit tenant switch / impersonation is held on the session.
        String sessionTenant = sessionTenant(request);
        if (StringUtils.hasText(sessionTenant)) {
            return Resolution.of(sessionTenant);
        }
        // An OIDC/OAuth2 principal may carry its workspace as a claim.
        String workspace = workspaceClaim(authentication);
        if (StringUtils.hasText(workspace)) {
            return Resolution.of(workspace);
        }
        return Resolution.none();
    }

    private static String sessionTenant(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(ShTenantFilter.TENANT_SESSION_ATTR);
        return value instanceof String tenant ? tenant : null;
    }

    private static String workspaceClaim(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            Object workspace = oidcUser.getClaims().get(WORKSPACE_ID_CLAIM);
            return workspace != null ? workspace.toString() : null;
        }
        if (principal instanceof OAuth2User oAuth2User) {
            Object workspace = oAuth2User.getAttributes().get(WORKSPACE_ID_CLAIM);
            return workspace != null ? workspace.toString() : null;
        }
        return null;
    }
}
