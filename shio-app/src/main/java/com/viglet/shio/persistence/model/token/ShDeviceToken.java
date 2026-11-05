/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.model.token;

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
 * SH1351 — a credential that is a <b>person</b> on one device.
 *
 * <p>The other token, {@link ShApiToken}, is deliberately a machine: its scopes are READ, PREVIEW,
 * WRITE, AGENT, FORM and SHARE, it is confined to the delivery and agent namespaces, and the review
 * queue is kept off it because the agent never approves its own work. A native client is the
 * curator, not an agent, and fits neither that nor the console's cookie. This row carries no
 * authority of its own: the filter resolves the user's authorities on every call, exactly as a Basic
 * login does, so a change to the person's groups applies to the device at once.</p>
 *
 * <p><b>No {@code @TenantId}</b>, for {@code ShUser}'s reason: a person is not a tenant's row, and
 * the lookup runs before the request's tenant is known. The tenant is resolved downstream from the
 * principal, the same way it is for Basic.</p>
 *
 * <p>Only the SHA-256 of the secret is stored. The value is shown once, when it is minted.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_device_token")
public class ShDeviceToken implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** The person this device acts as. */
    @Column(name = "username", nullable = false)
    private String username;

    /** What the person called the device, so the console's list can tell two phones apart. */
    @Column(name = "device_name", length = 100, nullable = false)
    private String deviceName;

    /** Hex SHA-256 of the secret; the secret itself is never stored. */
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    /** Last call this device made, coarse on purpose (see {@code ShDeviceTokenFilter}). */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /** Non-null once revoked; a revoked row is kept so the console can say when, and by whom. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private String revokedBy;

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
