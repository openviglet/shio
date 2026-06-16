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

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantStatus;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.property.ShAppProperties;

import jakarta.servlet.FilterChain;

/**
 * SH22/B+C — {@code ShTenantFilter} gates: a suspended tenant blocks its non-admin
 * members with 403, and a non-default tenant requires an ACTIVE membership (hard
 * 403) unless the request is CDA-token-scoped or the principal is an admin.
 *
 * @author Alexandre Oliveira
 */
@ExtendWith(MockitoExtension.class)
class ShTenantSuspendedAccessTest {

    @Mock
    private ShAppProperties shAppProperties;
    @Mock
    private ShTenantRepository shTenantRepository;
    @Mock
    private ShTenantMembershipService shTenantMembershipService;
    @Mock
    private FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        ShTenantContext.clear();
    }

    private ShTenantFilter filter() {
        return new ShTenantFilter(shAppProperties, shTenantRepository, shTenantMembershipService);
    }

    private void authenticateAs(String username, String... roles) {
        var authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", authorities));
    }

    private ShTenant tenant(String name, ShTenantStatus status) {
        ShTenant t = new ShTenant();
        t.setName(name);
        t.setStatus(status);
        return t;
    }

    /** A token-scoped request to tenant "beta" (exempt from the membership gate). */
    private MockHttpServletRequest tokenRequestTo(String tenant) {
        var request = new MockHttpServletRequest();
        request.setAttribute(ShTenantFilter.TENANT_REQUEST_ATTR, tenant);
        return request;
    }

    @Test
    void suspendedTenantBlocksNonAdminMember() throws Exception {
        when(shAppProperties.isMultiTenant()).thenReturn(true);
        when(shTenantRepository.findByName("beta")).thenReturn(Optional.of(tenant("beta", ShTenantStatus.SUSPENDED)));
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(tokenRequestTo("beta"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void suspendedTenantStillReachableByAdmin() throws Exception {
        when(shAppProperties.isMultiTenant()).thenReturn(true);
        authenticateAs("root", "ROLE_ADMIN");
        var response = new MockHttpServletResponse();

        filter().doFilter(tokenRequestTo("beta"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void activeTokenScopedTenantPassesThrough() throws Exception {
        when(shAppProperties.isMultiTenant()).thenReturn(true);
        when(shTenantRepository.findByName("beta")).thenReturn(Optional.of(tenant("beta", ShTenantStatus.ACTIVE)));
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(tokenRequestTo("beta"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void nonMemberIsDeniedOnNonDefaultTenant() throws Exception {
        when(shAppProperties.isMultiTenant()).thenReturn(true);
        when(shTenantMembershipService.isActiveMember("alice", "alice")).thenReturn(false);
        // No token attr: tenant resolves from the username fallback ("alice").
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void activeMemberPassesNonDefaultTenant() throws Exception {
        when(shAppProperties.isMultiTenant()).thenReturn(true);
        when(shTenantMembershipService.isActiveMember("alice", "alice")).thenReturn(true);
        authenticateAs("alice", "ROLE_USER");
        var response = new MockHttpServletResponse();

        filter().doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }
}
