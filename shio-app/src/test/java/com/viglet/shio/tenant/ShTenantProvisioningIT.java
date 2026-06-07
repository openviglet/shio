/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;

/**
 * SH22 Part C — per-tenant post-type provisioning. A post-type defined in the
 * default {@code shio} tenant is copied into a freshly-provisioned tenant
 * ({@code acme}) and is invisible to an unrelated tenant ({@code beta}); a second
 * provision is a no-op (idempotent). No Docker (H2, {@code multi-tenant=true}).
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShTenantProvisioningIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh22c-it;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "false");
        registry.add("shio.multi-tenant", () -> "true");
    }

    @Autowired
    private ShPostTypeAdminService adminService;
    @Autowired
    private ShTenantProvisioningService provisioningService;

    @Test
    void provisionsDefaultModelIntoNewTenantAndIsolatesIt() {
        // Seed a post-type in the default tenant (the provisioning source).
        ShPostTypeDef article = new ShPostTypeDef(
                "ArticleProv", "Article", "Articles", "An article", null, false, List.of());
        asTenant("shio", () -> adminService.provisionFromDef(article));

        // Provision a brand-new tenant -> the post-type is copied in.
        int created = provisioningService.provision("acme");
        assertThat(created).isPositive();

        assertThat(asTenant("acme", () -> adminService.list()))
                .as("acme must see the provisioned post-type")
                .extracting(ShPostTypeDef::name).contains("ArticleProv");

        assertThat(asTenant("beta", () -> adminService.list()))
                .as("an unrelated tenant must NOT see acme's post-types")
                .extracting(ShPostTypeDef::name).doesNotContain("ArticleProv");

        // Idempotent: re-provisioning creates nothing new.
        assertThat(provisioningService.provision("acme")).isZero();
    }

    private <T> T asTenant(String tenant, Supplier<T> work) {
        ShTenantContext.setTenantId(tenant);
        try {
            return work.get();
        } finally {
            ShTenantContext.clear();
        }
    }
}
