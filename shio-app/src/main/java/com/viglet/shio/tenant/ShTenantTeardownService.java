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

import com.viglet.core.tenancy.VigletTenantTeardownService;
import com.viglet.core.tenancy.VigletTenantTeardownService.VigletTenantTeardownReport;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

/**
 * Tenant lifecycle operations (SH22 / Turing parity): suspend / activate a tenant
 * and tear it down (purge bytes + catalog row).
 *
 * <p>T397 / §XIV.9 — re-homed onto the shared {@link VigletTenantTeardownService}:
 * the orchestration (platform-admin gate, dry-run, audit, registry deletion) now
 * lives once in {@code viglet-core-tenancy}. The physical purge stays Shio-specific
 * behind {@link ShVigletTenantPurger} ({@code file_source} bytes + image-cache);
 * the registry rows are removed via {@link ShVigletTenantStore}. The {@code @TenantId}
 * content rows are left in place (they become invisible once the catalog row is
 * gone).
 *
 * <p>This adapter keeps Shio's surrogate-UUID API ({@code suspend(id)} /
 * {@code delete(id, …)}) and its {@link ShTenantTeardownReport} shape, resolving
 * the UUID to the tenant <em>name</em> the shared service (and Shio's discriminator)
 * works in. The reserved {@code shio} default tenant is refused with HTTP 409 here,
 * before delegating.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShTenantTeardownService {

    private final VigletTenantTeardownService delegate;
    private final ShTenantRepository shTenantRepository;

    public ShTenantTeardownService(VigletTenantTeardownService delegate,
                                   ShTenantRepository shTenantRepository) {
        this.delegate = delegate;
        this.shTenantRepository = shTenantRepository;
    }

    /** Set {@code SUSPENDED} (blocks the tenant's members at {@code ShTenantFilter}). */
    @Transactional
    public ShTenant suspend(String id) {
        ShTenant tenant = requireTenant(id);
        if (tenant.isDefaultTenant()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The reserved default tenant cannot be suspended");
        }
        delegate.suspend(tenant.getName());
        return requireTenant(id);
    }

    /** Set {@code ACTIVE} (re-opens a suspended tenant). */
    @Transactional
    public ShTenant activate(String id) {
        ShTenant tenant = requireTenant(id);
        delegate.activate(tenant.getName());
        return requireTenant(id);
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
        VigletTenantTeardownReport report = delegate.delete(tenant.getName(), dryRun);
        return new ShTenantTeardownReport(id, tenant.getName(), report.dryRun(),
                report.tenantExisted(), report.purged());
    }

    private ShTenant requireTenant(String id) {
        return shTenantRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown tenant"));
    }

    /**
     * Outcome of a teardown. {@code tenantExisted} is false (and {@code name} null)
     * for an unknown id; {@code catalogRemoved} is false on a dry run.
     */
    public record ShTenantTeardownReport(String id, String name, boolean dryRun,
                                         boolean tenantExisted, boolean catalogRemoved) {
    }
}
