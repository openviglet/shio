/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.repository.token;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.token.ShDeviceToken;

/**
 * SH1351 — person-bound device tokens. Not tenant-scoped: the entity carries no {@code @TenantId}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShDeviceTokenRepository extends JpaRepository<ShDeviceToken, String> {

    Optional<ShDeviceToken> findByTokenHash(String tokenHash);

    List<ShDeviceToken> findByUsernameOrderByCreatedDateDesc(String username);
}
