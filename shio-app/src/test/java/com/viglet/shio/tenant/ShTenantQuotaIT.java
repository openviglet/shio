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

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

/**
 * SH22/D — plan-based create quotas: a FREE tenant is blocked at its site limit
 * (402), a paid plan and the default {@code shio} tenant are unlimited. Boots the
 * full context on a fresh H2 with Liquibase + multi-tenant.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShTenantQuotaIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh22d-it;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "PUBLIC");
        registry.add("shio.multi-tenant", () -> "true");
    }

    @Autowired
    private ShTenantQuotaService quotaService;
    @Autowired
    private ShTenantRepository shTenantRepository;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private PlatformTransactionManager txManager;

    private void catalogTenant(String name, String plan) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            ShTenant tenant = new ShTenant();
            tenant.setName(name);
            tenant.setPlan(plan);
            tenant.setEnabled(true);
            shTenantRepository.saveAndFlush(tenant);
        });
    }

    @Test
    void freePlanIsBlockedAtTheSiteLimit() {
        catalogTenant("freeco", "FREE");

        asTenant("freeco", () -> {
            // FREE allows 2 sites; the first two pass, then a third is blocked.
            quotaService.checkCanCreateSite();
            saveSite("s1");
            quotaService.checkCanCreateSite();
            saveSite("s2");
            assertThatThrownBy(() -> quotaService.checkCanCreateSite())
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Plan limit");
            return null;
        });
    }

    @Test
    void paidPlanIsUnlimited() {
        catalogTenant("paidco", "PRO");
        asTenant("paidco", () -> {
            assertThat(quotaService.currentLimits()).isEqualTo(ShTenantPlanLimits.UNLIMITED);
            saveSite("p1");
            saveSite("p2");
            saveSite("p3");
            quotaService.checkCanCreateSite(); // never throws
            return null;
        });
    }

    @Test
    void defaultTenantIsUnlimited() {
        asTenant(ShTenant.DEFAULT_TENANT_NAME, () -> {
            assertThat(quotaService.currentLimits()).isEqualTo(ShTenantPlanLimits.UNLIMITED);
            return null;
        });
    }

    private ShSite saveSite(String name) {
        ShSite site = new ShSite();
        site.setName(name);
        return (ShSite) shSiteRepository.save(site);
    }

    private <T> T asTenant(String tenant, Supplier<T> work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return new TransactionTemplate(txManager).execute(status -> work.get());
        } finally {
            ShTenantContext.clear();
        }
    }
}
