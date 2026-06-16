/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.api.staticfile.transform.ShImageTransformCache;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantStatus;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Tenant lifecycle operations (SH22 / Turing parity, mirrors Turing's
 * {@code TurTenantTeardownService}): suspend / activate a tenant and tear it down.
 *
 * <p><strong>Teardown</strong> ({@link #delete}) purges the tenant's on-disk
 * footprint — its {@code file_source} byte directory and image-cache sub-directory
 * — and removes the {@code sh_tenant} catalog row. It deliberately leaves the
 * tenant's {@code @TenantId} content rows in place (they become invisible once the
 * catalog row is gone); a physical row sweep is a separate, heavier operation.
 * Idempotent (an unknown id is a no-op report) and audited via {@code log.warn};
 * the reserved {@code shio} default tenant can never be torn down (409).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Slf4j
public class ShTenantTeardownService {

    private final ShTenantRepository shTenantRepository;
    private final ShFileStorageService shFileStorageService;
    private final ShImageTransformCache shImageTransformCache;

    public ShTenantTeardownService(ShTenantRepository shTenantRepository,
                                   ShFileStorageService shFileStorageService,
                                   ShImageTransformCache shImageTransformCache) {
        this.shTenantRepository = shTenantRepository;
        this.shFileStorageService = shFileStorageService;
        this.shImageTransformCache = shImageTransformCache;
    }

    /** Set {@code SUSPENDED} (blocks the tenant's members at {@code ShTenantFilter}). */
    @Transactional
    public ShTenant suspend(String id) {
        return setStatus(id, ShTenantStatus.SUSPENDED);
    }

    /** Set {@code ACTIVE} (re-opens a suspended tenant). */
    @Transactional
    public ShTenant activate(String id) {
        return setStatus(id, ShTenantStatus.ACTIVE);
    }

    private ShTenant setStatus(String id, ShTenantStatus status) {
        ShTenant tenant = requireTenant(id);
        // The reserved default tenant is always live; refuse to suspend it.
        if (tenant.isDefaultTenant() && status == ShTenantStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The reserved default tenant cannot be suspended");
        }
        tenant.setStatus(status);
        log.warn("[TenantLifecycle] tenant '{}' -> {}", tenant.getName(), status);
        return shTenantRepository.saveAndFlush(tenant);
    }

    /**
     * Tear a tenant down. Removes its stored bytes (file_source + image cache) and
     * the catalog row. {@code dryRun=true} reports what would happen without
     * mutating anything. The {@code shio} default tenant is never deletable (409).
     */
    @Transactional
    public ShTenantTeardownReport delete(String id, boolean dryRun) {
        ShTenant tenant = shTenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return new ShTenantTeardownReport(id, null, dryRun, false, false);
        }
        if (tenant.isDefaultTenant()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The reserved default tenant cannot be deleted");
        }
        String name = tenant.getName();
        if (dryRun) {
            log.info("[TenantTeardown] DRY-RUN for tenant '{}': would purge bytes + catalog row", name);
            return new ShTenantTeardownReport(id, name, true, true, false);
        }
        log.warn("[TenantTeardown] DELETING tenant '{}' (audited)", name);
        shFileStorageService.deleteTenant(name);
        shImageTransformCache.purgeTenant(name);
        shTenantRepository.delete(tenant);
        return new ShTenantTeardownReport(id, name, false, true, true);
    }

    private ShTenant requireTenant(String id) {
        return shTenantRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant"));
    }

    /**
     * Outcome of a teardown. {@code tenantExisted} is false (and {@code name} null)
     * for an unknown id; {@code bytesPurged}/{@code catalogRemoved} are false on a
     * dry run.
     */
    public record ShTenantTeardownReport(String id, String name, boolean dryRun,
                                         boolean tenantExisted, boolean catalogRemoved) {
    }
}
