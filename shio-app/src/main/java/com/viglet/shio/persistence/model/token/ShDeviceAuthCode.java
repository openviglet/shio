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
 * SH1360 — a one-time code a browser sign-in hands back to a native app.
 *
 * <p>It holds <b>no secret</b>: only the SHA-256 of the code, the person who signed in, the device
 * name they asked for, the PKCE challenge and the redirect it was issued for. The device token is
 * minted at the exchange, after the verifier matches, so nothing worth stealing is ever at rest
 * here. A table rather than memory, because a memory store is right on one node and silently wrong
 * behind a load balancer.</p>
 *
 * <p><b>No {@code @TenantId}</b>, for {@link ShDeviceToken}'s reason: the code belongs to a person,
 * and the exchange runs before any tenant is known.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Entity
@Table(name = "sh_device_auth_code")
public class ShDeviceAuthCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Hex SHA-256 of the code; the code itself only ever travels in the redirect. */
    @Column(name = "code_hash", length = 64, nullable = false, unique = true)
    private String codeHash;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "device_name", length = 100, nullable = false)
    private String deviceName;

    /** The PKCE S256 challenge, base64url without padding. */
    @Column(name = "code_challenge", length = 128, nullable = false)
    private String codeChallenge;

    @Column(name = "redirect_uri", length = 512, nullable = false)
    private String redirectUri;

    @Column(name = "created_date", nullable = false)
    private Instant createdDate;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Non-null once exchanged, or burned by a wrong verifier: a code is good for one attempt. */
    @Column(name = "used_at")
    private Instant usedAt;
}
