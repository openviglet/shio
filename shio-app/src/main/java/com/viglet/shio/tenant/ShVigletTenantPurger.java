/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import org.springframework.stereotype.Component;

import com.viglet.core.tenancy.VigletTenantPurger;
import com.viglet.shio.api.site.ShSiteThumbnailStore;
import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.api.staticfile.transform.ShImageTransformCache;

import lombok.extern.slf4j.Slf4j;

/**
 * T397 / §XIV.9 — the {@link VigletTenantPurger} SPI wiping Shio's physical tenant
 * footprint (the bytes outside the registry rows) when the shared
 * {@code VigletTenantTeardownService} tears a tenant down: its {@code file_source}
 * byte directory and its image-cache sub-directory. Best-effort throughout — a
 * failure to remove some bytes must not abort the registry deletion.
 *
 * <p>The shared teardown service invokes this inside
 * {@code tenantContext.runAs(name, …)} and only when {@code dryRun=false}; the
 * {@code @TenantId} content rows are left in place (they become invisible once the
 * catalog row is gone, exactly as the legacy {@code ShTenantTeardownService} did).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class ShVigletTenantPurger implements VigletTenantPurger {

    private final ShFileStorageService shFileStorageService;
    private final ShImageTransformCache shImageTransformCache;
    private final ShSiteThumbnailStore shSiteThumbnailStore;

    public ShVigletTenantPurger(ShFileStorageService shFileStorageService,
            ShImageTransformCache shImageTransformCache,
            ShSiteThumbnailStore shSiteThumbnailStore) {
        this.shFileStorageService = shFileStorageService;
        this.shImageTransformCache = shImageTransformCache;
        this.shSiteThumbnailStore = shSiteThumbnailStore;
    }

    @Override
    public void purge(String tenantId, boolean dryRun) {
        if (dryRun) {
            return; // the shared service computes the plan without invoking us mutatively
        }
        try {
            shFileStorageService.deleteTenant(tenantId);
        } catch (RuntimeException e) {
            log.warn("[TenantTeardown] file_source purge failed for '{}': {}", tenantId, e.getMessage());
        }
        try {
            shImageTransformCache.purgeTenant(tenantId);
        } catch (RuntimeException e) {
            log.warn("[TenantTeardown] image-cache purge failed for '{}': {}", tenantId, e.getMessage());
        }
        try {
            // SH1218 — run inside the tenant's context, so the store resolves this tenant's root.
            shSiteThumbnailStore.purgeTenant();
        } catch (RuntimeException e) {
            log.warn("[TenantTeardown] site-picture purge failed for '{}': {}", tenantId, e.getMessage());
        }
    }
}
