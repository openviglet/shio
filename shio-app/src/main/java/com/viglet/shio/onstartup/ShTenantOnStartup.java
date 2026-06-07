/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.onstartup;

import java.time.Instant;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Ensures the reserved default tenant ({@link ShTenant#DEFAULT_TENANT_NAME}) row
 * always exists in the catalog (SH22). Runs on every boot — not first-boot-guarded
 * like {@link ShOnStartup} — because the {@code sh_tenant} table is introduced by a
 * later changelog, so already-initialized installs would otherwise never get the
 * row. Idempotent: a no-op once the row exists. {@link ShTenant} is not
 * {@code @TenantId}-scoped, so this is independent of the request tenant context.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
@Order(0)
@Slf4j
public class ShTenantOnStartup implements ApplicationRunner {

    private final ShTenantRepository shTenantRepository;

    public ShTenantOnStartup(ShTenantRepository shTenantRepository) {
        this.shTenantRepository = shTenantRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!shTenantRepository.existsByName(ShTenant.DEFAULT_TENANT_NAME)) {
            ShTenant tenant = new ShTenant();
            tenant.setName(ShTenant.DEFAULT_TENANT_NAME);
            tenant.setDescription("Default single-tenant workspace");
            tenant.setEnabled(true);
            tenant.setOwner("system");
            tenant.setCreatedDate(Instant.now());
            shTenantRepository.save(tenant);
            log.info("Seeded default tenant '{}'", ShTenant.DEFAULT_TENANT_NAME);
        }
    }
}
