/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.tenant;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * A user's membership in a tenant (SH22 follow-up — Turing parity, mirrors
 * {@code TurTenantMembership}). Joins a principal ({@link #username}) to a
 * {@link ShTenant} with a {@link ShTenantRole} and a {@link ShTenantMembershipStatus}.
 *
 * <p>One membership per (tenant, username). The username is a plain string (the
 * principal name / OIDC {@code preferred_username}), not a FK to {@code ShUser},
 * so a tenant can carry memberships for users provisioned lazily on first login.
 * Deliberately <b>not</b> {@code @TenantId}-scoped — it is queried <em>before</em>
 * the request tenant is known (to resolve which tenants a user may enter), exactly
 * like the {@link ShTenant} catalog itself.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_tenant_membership",
        uniqueConstraints = @UniqueConstraint(name = "UK_sh_tenant_membership",
                columnNames = { "tenant_id", "username" }))
public class ShTenantMembership implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private ShTenant tenant;

    @Column(name = "username", length = 255, nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private ShTenantRole role = ShTenantRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private ShTenantMembershipStatus status = ShTenantMembershipStatus.ACTIVE;

    @Column(name = "created_date")
    private Instant createdDate;

    /** Whether this membership currently grants access. */
    public boolean isActive() {
        return status == ShTenantMembershipStatus.ACTIVE;
    }
}
