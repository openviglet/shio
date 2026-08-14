/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.model.tenant;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persistent tenant record (SH22 follow-up — tenant catalog).
 *
 * <p>The catalog of tenants that can exist in one shared JVM. Each row's
 * {@link #name} is the tenant identifier carried by Hibernate's {@code @TenantId}
 * discriminator on the content entities (the value {@code ShTenantContext} resolves
 * per request). The reserved name {@link #DEFAULT_TENANT_NAME} ("shio") is the
 * single-tenant default and is always present (seeded at startup); it cannot be
 * renamed or deleted.
 *
 * <p>Deliberately <b>not</b> {@code @TenantId}-scoped — this is the global catalog
 * an admin lists across all tenants, mirroring the thin-CRUD pattern of
 * {@link com.viglet.shio.persistence.model.webhook.ShSiteWebhook} /
 * {@link com.viglet.shio.persistence.model.token.ShApiToken} but without the
 * discriminator.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Getter
@Setter
@Entity
@Table(name = "sh_tenant")
public class ShTenant implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The reserved default tenant — the single-tenant identity, always present. */
    public static final String DEFAULT_TENANT_NAME = "shio";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * The tenant identifier (the value of the {@code tenant_id} discriminator on
     * content rows). Unique; immutable once created.
     */
    @Column(name = "name", length = 255, nullable = false, unique = true)
    private String name;

    @Column(name = "description", length = 510)
    private String description;

    @Column(name = "enabled")
    private boolean enabled = true;

    /**
     * Lifecycle state (SH22 / Turing parity). {@code SUSPENDED} keeps the tenant's
     * data but blocks its members at {@code ShTenantFilter} with HTTP 403.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private ShTenantStatus status = ShTenantStatus.ACTIVE;

    /**
     * Billing/plan identifier (e.g. {@code FREE}, {@code PRO}). Free-form rather
     * than an enum so plans can evolve without a migration; {@code ShTenantQuotaService}
     * maps it to per-tenant create limits (SH22/D).
     */
    @Column(name = "plan", length = 50)
    private String plan = "FREE";

    /**
     * Bytes this tenant is storing, maintained rather than recomputed (SH45).
     *
     * <p>The other two metered resources are a {@code count()} on an indexed, {@code @TenantId}-
     * filtered repository. Storage has no such column: SH399 stamps a file's size as {@code "SIZE"}
     * inside {@code ShPostUnified.data}, a VARCHAR of JSON, so summing it means loading every File
     * post — and the alternative, walking {@code <root>/<tenant>}, is disk I/O. Both are O(files) on
     * a path the quota check runs before every upload, which is why this is kept instead.</p>
     *
     * <p>Drift is the price of that, and {@code ShTenantStorageMeter.reconcile} is the answer: SH47
     * gave every tenant its own directory, so the true figure is always recoverable.</p>
     */
    @Column(name = "storage_bytes", nullable = false)
    private long storageBytes;

    @Column(name = "owner", length = 255)
    private String owner;

    @Column(name = "created_date")
    private Instant createdDate;

    /** True for the reserved {@code shio} default tenant (never rename/delete). */
    public boolean isDefaultTenant() {
        return DEFAULT_TENANT_NAME.equals(name);
    }

    /** Whether this tenant is currently suspended (members blocked). */
    public boolean isSuspended() {
        return status == ShTenantStatus.SUSPENDED;
    }
}
