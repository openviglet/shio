/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.persistence.repository.token;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.shio.persistence.model.token.ShDeviceAuthCode;

/** SH1360 — the one-time codes of the native app's browser sign-in. */
public interface ShDeviceAuthCodeRepository extends JpaRepository<ShDeviceAuthCode, String> {

    Optional<ShDeviceAuthCode> findByCodeHash(String codeHash);
}
