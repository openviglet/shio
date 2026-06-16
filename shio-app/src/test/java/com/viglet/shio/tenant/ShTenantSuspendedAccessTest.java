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
 * SH22/B — {@code ShTenantFilter} blocks a suspended tenant's non-admin members
 * with HTTP 403, while admins and active tenants pass through.
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
    private FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        ShTenantContext.clear();
    }

    private void authenticateAs(String username, String... roles) {
        var authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", authorities));
    }

    private ShTenant suspended(String name) {
        ShTenant t = new ShTenant();
        t.setName(name);
        t.setStatus(ShTenantStatus.SUSPENDED);
        return t;
    }

    @Test
    void suspendedTenantBlocksNonAdminMember() throws Exception {
        when(shAppProperties.isMultiTenant()).thenReturn(true);
        when(shTenantRepository.findByName("beta")).thenReturn(Optional.of(suspended("beta")));
        authenticateAs("alice", "ROLE_USER");
        // The tenant is resolved from the username fallback when there is no claim.
        var filter = new ShTenantFilter(shAppProperties, shTenantRepository);
        var request = new MockHttpServletRequest();
        request.setAttribute(ShTenantFilter.TENANT_REQUEST_ATTR, "beta");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void suspendedTenantStillReachableByAdmin() throws Exception {
        when(shAppProperties.isMultiTenant()).thenReturn(true);
        authenticateAs("root", "ROLE_ADMIN");
        var filter = new ShTenantFilter(shAppProperties, shTenantRepository);
        var request = new MockHttpServletRequest();
        request.setAttribute(ShTenantFilter.TENANT_REQUEST_ATTR, "beta");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }

    @Test
    void activeTenantPassesThrough() throws Exception {
        when(shAppProperties.isMultiTenant()).thenReturn(true);
        ShTenant active = new ShTenant();
        active.setName("beta");
        active.setStatus(ShTenantStatus.ACTIVE);
        when(shTenantRepository.findByName("beta")).thenReturn(Optional.of(active));
        authenticateAs("alice", "ROLE_USER");
        var filter = new ShTenantFilter(shAppProperties, shTenantRepository);
        var request = new MockHttpServletRequest();
        request.setAttribute(ShTenantFilter.TENANT_REQUEST_ATTR, "beta");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(chain).doFilter(any(), any());
    }
}
