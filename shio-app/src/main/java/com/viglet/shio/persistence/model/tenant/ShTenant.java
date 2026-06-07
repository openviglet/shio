/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.model.tenant;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "owner", length = 255)
    private String owner;

    @Column(name = "created_date")
    private Instant createdDate;

    /** True for the reserved {@code shio} default tenant (never rename/delete). */
    public boolean isDefaultTenant() {
        return DEFAULT_TENANT_NAME.equals(name);
    }
}
