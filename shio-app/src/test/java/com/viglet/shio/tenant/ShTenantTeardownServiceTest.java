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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.core.tenancy.VigletTenantContext;
import com.viglet.core.tenancy.VigletTenantTeardownService;
import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.api.staticfile.transform.ShImageTransformCache;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantStatus;
import com.viglet.shio.persistence.repository.tenant.ShTenantMembershipRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.tenant.ShTenantTeardownService.ShTenantTeardownReport;

/**
 * T397 / §XIV.9 — {@code ShTenantTeardownService}, re-homed onto the shared
 * {@code VigletTenantTeardownService}, still: purges bytes ({@code file_source} +
 * image cache) + removes the catalog row, refuses the reserved {@code shio} tenant
 * (409), and is idempotent for unknown ids and dry runs. The shared orchestration
 * gates on platform-admin (Shio's {@code ROLE_ADMIN}), so each test authenticates
 * as an admin.
 *
 * @author Alexandre Oliveira
 */
@ExtendWith(MockitoExtension.class)
class ShTenantTeardownServiceTest {

    @Mock
    private ShTenantRepository shTenantRepository;
    @Mock
    private ShTenantMembershipRepository shTenantMembershipRepository;
    @Mock
    private ShFileStorageService shFileStorageService;
    @Mock
    private ShImageTransformCache shImageTransformCache;
    @Mock
    private com.viglet.shio.api.site.ShSiteThumbnailStore shSiteThumbnailStore;

    @BeforeEach
    void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("root", "n/a",
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** Builds the adapter over the shared teardown delegate, wired with the SPI adapters. */
    private ShTenantTeardownService service() {
        VigletTenantContext context = new VigletTenantContext(true, ShTenant.DEFAULT_TENANT_NAME);
        ShPlatformAdminService platformAdmin = new ShPlatformAdminService(context);
        ShVigletTenantStore store = new ShVigletTenantStore(shTenantRepository, shTenantMembershipRepository);
        ShVigletTenantPurger purger = new ShVigletTenantPurger(shFileStorageService, shImageTransformCache,
                shSiteThumbnailStore);
        VigletTenantTeardownService delegate =
                new VigletTenantTeardownService(platformAdmin, context, store, purger);
        return new ShTenantTeardownService(delegate, shTenantRepository);
    }

    private ShTenant tenant(String name) {
        ShTenant t = new ShTenant();
        t.setId("id-" + name);
        t.setName(name);
        t.setStatus(ShTenantStatus.ACTIVE);
        return t;
    }

    @Test
    void deletePurgesBytesAndCatalogRow() {
        ShTenant acme = tenant("acme");
        when(shTenantRepository.findById("id-acme")).thenReturn(Optional.of(acme));
        when(shTenantRepository.findByName("acme")).thenReturn(Optional.of(acme));
        when(shTenantMembershipRepository.countByTenant_Name("acme")).thenReturn(0L);

        ShTenantTeardownReport report = service().delete("id-acme", false);

        assertThat(report.tenantExisted()).isTrue();
        assertThat(report.catalogRemoved()).isTrue();
        verify(shFileStorageService).deleteTenant("acme");
        verify(shImageTransformCache).purgeTenant("acme");
        verify(shSiteThumbnailStore).purgeTenant();
        verify(shTenantRepository).delete(acme);
    }

    @Test
    void deleteDryRunMutatesNothing() {
        ShTenant acme = tenant("acme");
        when(shTenantRepository.findById("id-acme")).thenReturn(Optional.of(acme));
        when(shTenantRepository.findByName("acme")).thenReturn(Optional.of(acme));
        when(shTenantMembershipRepository.countByTenant_Name("acme")).thenReturn(0L);

        ShTenantTeardownReport report = service().delete("id-acme", true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.tenantExisted()).isTrue();
        assertThat(report.catalogRemoved()).isFalse();
        verify(shFileStorageService, never()).deleteTenant(any());
        verify(shTenantRepository, never()).delete(any());
    }

    @Test
    void deleteUnknownIdIsNoOp() {
        when(shTenantRepository.findById("missing")).thenReturn(Optional.empty());

        ShTenantTeardownReport report = service().delete("missing", false);

        assertThat(report.tenantExisted()).isFalse();
        verify(shFileStorageService, never()).deleteTenant(any());
    }

    @Test
    void deleteRefusesDefaultTenant() {
        ShTenant shio = tenant(ShTenant.DEFAULT_TENANT_NAME);
        when(shTenantRepository.findById("id-shio")).thenReturn(Optional.of(shio));

        ShTenantTeardownService service = service();
        assertThatThrownBy(() -> service.delete("id-shio", false))
                .isInstanceOf(ResponseStatusException.class);
        verify(shTenantRepository, never()).delete(any());
    }

    @Test
    void suspendThenActivateFlipsStatus() {
        ShTenant acme = tenant("acme");
        when(shTenantRepository.findById("id-acme")).thenReturn(Optional.of(acme));
        when(shTenantRepository.findByName("acme")).thenReturn(Optional.of(acme));
        when(shTenantRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ShTenantTeardownService service = service();
        assertThat(service.suspend("id-acme").getStatus()).isEqualTo(ShTenantStatus.SUSPENDED);
        assertThat(service.activate("id-acme").getStatus()).isEqualTo(ShTenantStatus.ACTIVE);
    }

    @Test
    void suspendRefusesDefaultTenant() {
        ShTenant shio = tenant(ShTenant.DEFAULT_TENANT_NAME);
        when(shTenantRepository.findById("id-shio")).thenReturn(Optional.of(shio));

        ShTenantTeardownService service = service();
        assertThatThrownBy(() -> service.suspend("id-shio"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
