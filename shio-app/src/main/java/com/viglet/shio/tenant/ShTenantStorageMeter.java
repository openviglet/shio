/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * How many bytes a tenant is storing, maintained rather than recomputed (SH45).
 *
 * <h2>Why a kept number and not a query</h2>
 *
 * <p>SH22 named three resources to meter and two were built. Sites and posts are a {@code count()}
 * on an indexed, {@code @TenantId}-filtered repository, which is why they were cheap enough to check
 * before every write. Storage has no such column: SH399 stamps a file's size as {@code "SIZE"}
 * inside {@code ShPostUnified.data}, a VARCHAR of JSON, so the two obvious readings are both
 * <b>O(files)</b> on a path {@code VigletQuotaService.check} runs before every upload — walking
 * {@code <root>/<tenant>} is disk I/O per check, and summing the JSON means loading and parsing
 * every File post, because a {@code JSON_VALUE}/{@code ->>} predicate is engine-specific and the
 * SH26 cross-DB ITs refuse it.</p>
 *
 * <p>So the figure is kept on {@code sh_tenant} and adjusted as bytes arrive and leave. Design and
 * the two rejected shapes: IMPROVEMENTS §VI.5.</p>
 *
 * <h2>Drift, and the thing that answers for it</h2>
 *
 * <p>A maintained counter can disagree with the disk — a crash between the write and the commit, a
 * file removed by hand, an install that had files before this column existed. {@link #reconcile} is
 * the answer rather than a hope: SH47 gave every tenant its own directory, so the true figure is
 * always recoverable by walking one subtree. It is deliberately <b>not scheduled</b> here. What
 * cadence a reconcile runs at is an operator's decision and a scheduled job that nobody asked for
 * is a cost this task did not measure.</p>
 *
 * <p>{@link #record} clamps at zero. A negative total is arithmetically possible after drift — two
 * deletes of one file, a delete of bytes counted before the column existed — and a meter reading
 * below empty would refuse nothing and confuse everything; the honest floor is that a tenant
 * storing nothing stores zero.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Slf4j
public class ShTenantStorageMeter {

    private final ShTenantRepository shTenantRepository;
    private final ShFileStorageService shFileStorageService;

    public ShTenantStorageMeter(ShTenantRepository shTenantRepository,
                                ShFileStorageService shFileStorageService) {
        this.shTenantRepository = shTenantRepository;
        this.shFileStorageService = shFileStorageService;
    }

    /** What the current tenant is storing, in bytes. {@code 0} when the tenant row is unknown. */
    @Transactional(readOnly = true)
    public long bytesFor(String tenant) {
        return shTenantRepository.findByName(tenant).map(ShTenant::getStorageBytes).orElse(0L);
    }

    /**
     * Adjust the kept figure by {@code delta} — positive on a write, negative on a removal.
     *
     * <p>Clamped at zero, and a no-op for a tenant with no catalog row: a single-tenant install
     * ({@code shio.multi-tenant=false}) still resolves to the reserved {@code shio} tenant, which is
     * seeded, so "no row" means a caller asked about something that does not exist rather than a
     * state to record against.</p>
     */
    @Transactional
    public void record(String tenant, long delta) {
        if (delta == 0) {
            return;
        }
        shTenantRepository.findByName(tenant).ifPresent(row -> {
            long updated = Math.max(0L, row.getStorageBytes() + delta);
            row.setStorageBytes(updated);
            shTenantRepository.save(row);
        });
    }

    /**
     * Recount from disk and replace the kept figure, returning what it became.
     *
     * <p>The correction, not the meter: this is the O(files) walk the counter exists to avoid, run
     * when somebody asks rather than per upload.</p>
     */
    @Transactional
    public long reconcile(String tenant) {
        long onDisk = shFileStorageService.bytesOnDiskFor(tenant);
        shTenantRepository.findByName(tenant).ifPresent(row -> {
            if (row.getStorageBytes() != onDisk) {
                log.info("SH45: storage meter for '{}' drifted, {} -> {}",
                        tenant, row.getStorageBytes(), onDisk);
            }
            row.setStorageBytes(onDisk);
            shTenantRepository.save(row);
        });
        return onDisk;
    }
}
