/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantStatus;

import jakarta.servlet.FilterChain;

/**
 * T397 / §XIV.9 — {@code ShTenantFilter}, re-homed onto the shared
 * {@code VigletTenantResolutionFilter}, still applies Shio's gates: a suspended
 * tenant blocks its non-admin members (403), a non-default tenant requires an
 * ACTIVE membership (403) unless the request is CDA-token-scoped ({@code trusted})
 * or the principal is an admin. An authenticated request that resolves <em>no</em>
 * explicit tenant now drops to the default {@code shio} tenant and is served
 * (Turing-parity drop-to-default adopted in T397), rather than being denied.
 *
 * @author Alexandre Oliveira
 */
@ExtendWith(MockitoExtension.class)
class ShTenantSuspendedAccessTest {

    @Mock
    private ShVigletTenantStore store;
    @Mock
    private FilterChain chain;

    private final VigletTenantContext context = new VigletTenantContext(true, "shio");
    private final ShVigletTenantResolver resolver = new ShVigletTenantResolver();
    private final ShPlatformAdminService platformAdmin = new ShPlatformAdminService(context);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        context.clear();
    }

    private ShTenantFilter filter() {
        return new ShTenantFilter(context, store, resolver, platformAdmin);
    }

    private void authenticateAs(String username, String... roles) {
        var authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", authorities));
    }

    private VigletTenantRef ref(String name, VigletTenantStatus status) {
        return new VigletTenantRef(name, name, name, status, "FREE");
    }

    /** A token-scoped request to {@code tenant} (the {@code trusted} CDA-token path). */
    private MockHttpServletRequest tokenRequestTo(String tenant) {
        var request = new MockHttpServletRequest();
        request.setAttribute(ShTenantFilter.TENANT_REQUEST_ATTR, tenant);
        return request;
    }

    /** A request carrying a session-pinned tenant (the {@code /switch} path — membership-checked). */
    private MockHttpServletRequest sessionRequestTo(String tenant) {
        var request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(ShTenantFilter.TENANT_SESSION_ATTR, tenant);
        return request;
    }

    @Test
    void suspendedTenantBlocksNonAdminMember() throws Exception {
        when(store.findById("beta")).thenReturn(Optional.of(ref("beta", VigletTenantStatus.SUSPENDED)));
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(tokenRequestTo("beta"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void suspendedTenantStillReachableByAdmin() throws Exception {
        when(store.findById("beta")).thenReturn(Optional.of(ref("beta", VigletTenantStatus.SUSPENDED)));
        authenticateAs("root", "ROLE_ADMIN");
        var response = new MockHttpServletResponse();

        filter().doFilter(tokenRequestTo("beta"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void activeTokenScopedTenantPassesThrough() throws Exception {
        when(store.findById("beta")).thenReturn(Optional.of(ref("beta", VigletTenantStatus.ACTIVE)));
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(tokenRequestTo("beta"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void nonMemberIsDeniedOnNonDefaultTenant() throws Exception {
        when(store.findById("beta")).thenReturn(Optional.of(ref("beta", VigletTenantStatus.ACTIVE)));
        when(store.isActiveMember("beta", "alice")).thenReturn(false);
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(sessionRequestTo("beta"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void activeMemberPassesNonDefaultTenant() throws Exception {
        when(store.findById("beta")).thenReturn(Optional.of(ref("beta", VigletTenantStatus.ACTIVE)));
        when(store.isActiveMember("beta", "alice")).thenReturn(true);
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(sessionRequestTo("beta"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void unresolvedTenantDropsToDefaultAndIsServed() throws Exception {
        // No token / session / workspace_id claim — resolves to nothing, so the
        // shared filter binds the default 'shio' tenant and serves the request.
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }
}
