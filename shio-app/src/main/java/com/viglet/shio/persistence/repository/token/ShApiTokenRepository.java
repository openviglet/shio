/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.repository.token;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.token.ShApiToken;

/**
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Repository
public interface ShApiTokenRepository extends JpaRepository<ShApiToken, String> {

    Optional<ShApiToken> findByToken(String token);

    List<ShApiToken> findAllByOrderByTitleAsc();

    /**
     * Management list (SH13): durable tokens only, so short-lived preview tokens
     * minted by {@code ShPreviewTokenAPI} never clutter the token UI.
     */
    List<ShApiToken> findByEphemeralFalseOrderByTitleAsc();

    /** Opportunistic purge (SH13) of expired preview tokens — called on each mint. */
    @Transactional
    void deleteByEphemeralTrueAndExpiresAtBefore(Instant when);
}
