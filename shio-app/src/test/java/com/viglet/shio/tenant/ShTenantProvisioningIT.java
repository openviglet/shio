/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
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
import com.viglet.shio.api.site.ShSiteWriteService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * SH22 Part C / SH72 — per-tenant provisioning via the exchange engine. A post-type
 * defined in the default {@code shio} tenant is copied into a freshly-provisioned
 * tenant ({@code acme}) and is invisible to an unrelated tenant ({@code beta}); a
 * second provision is a no-op (idempotent). SH72 also clones the default tenant's
 * <strong>sites + content</strong> into a new tenant ({@code gamma}), once.
 * No Docker (H2, {@code multi-tenant=true}).
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
    @Autowired
    private ShSiteWriteService shSiteWriteService;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;

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

    @Test
    void clonesDefaultTenantSitesAndContentIntoNewTenant() {
        // Create a site in the default tenant — SH71 bootstraps a starter (Page
        // post-type + Home/Pages folders + Welcome/About sample pages).
        String siteName = "Flagship-" + UUID.randomUUID();
        ShSite payload = new ShSite();
        payload.setName(siteName);
        payload.setUrl("http://example.test");
        asTenant("shio", () -> shSiteWriteService.create(payload, "admin"));

        provisioningService.provision("gamma");

        // gamma gets a CLONE of the default tenant's site (fresh id, tenant-suffixed
        // name — ShSite.name is globally unique, so the clone can't reuse it verbatim).
        ShSite gammaSite = asTenant("gamma", () -> shSiteRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().startsWith(siteName)).findFirst().orElse(null));
        assertThat(gammaSite).as("gamma must receive a clone of shio's site").isNotNull();

        // ...and the cloned pages came along.
        List<String> titles = asTenant("gamma", () -> shPostUnifiedRepository
                .findByShSite_IdAndStateIn(gammaSite.getId(),
                        List.of(ShPostState.DRAFT, ShPostState.PUBLISHED))
                .stream().map(ShPostUnified::getTitle).toList());
        assertThat(titles).contains("Welcome", "About");

        // Idempotent: re-provisioning an already-seeded tenant doesn't duplicate sites.
        long before = asTenant("gamma", () -> shSiteRepository.count());
        provisioningService.provision("gamma");
        long after = asTenant("gamma", () -> shSiteRepository.count());
        assertThat(after).as("re-provision must not duplicate cloned sites").isEqualTo(before);
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
