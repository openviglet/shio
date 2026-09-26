/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.repository.token;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.token.ShApiToken;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;

/**
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Repository
public interface ShApiTokenRepository extends JpaRepository<ShApiToken, String> {

    Optional<ShApiToken> findByToken(String token);

    /**
     * Tenant-agnostic token lookup (SH22 Part D). {@code findByToken} carries the
     * Hibernate {@code @TenantId} discriminator, so during authentication — before
     * the tenant context is known — it would only find tokens of the default
     * tenant. A native query bypasses the discriminator filter, returning the row
     * regardless of tenant; the caller then resolves the request tenant from the
     * token's {@code tenant_id}. (In single-tenant mode every row is {@code shio},
     * so behaviour is identical.)
     */
    @Query(value = "SELECT * FROM api_token WHERE token = :token", nativeQuery = true)
    Optional<ShApiToken> findByTokenAcrossTenants(@Param("token") String token);

    List<ShApiToken> findAllByOrderByTitleAsc();

    /**
     * SH1110/SH1111 — the live credentials of one scope, soonest to expire first. Tenant-scoped like
     * every derived query here (`@TenantId`), which is what keeps one tenant's links off another's
     * listing; {@link #findByTokenAcrossTenants} is the one read that is not.
     */
    List<ShApiToken> findByScopeAndEnabledTrueAndExpiresAtAfterOrderByExpiresAtAsc(ShApiTokenScope scope,
                                                                                 Instant now);

    /**
     * Management list (SH13): durable tokens only, so short-lived preview tokens
     * minted by {@code ShPreviewTokenAPI} never clutter the token UI.
     */
    List<ShApiToken> findByEphemeralFalseOrderByTitleAsc();

    /** Opportunistic purge (SH13) of expired preview tokens — called on each mint. */
    @Transactional
    void deleteByEphemeralTrueAndExpiresAtBefore(Instant when);
}
