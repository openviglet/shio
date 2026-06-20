/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.api.token.dto;

import java.util.Set;

import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;

/**
 * Request payload for creating/updating a {@code ShApiToken} (CDA token).
 *
 * <p>Replaces the persistent {@code ShApiToken} entity as the
 * {@code @RequestBody} type (Sonar java:S4684). Component names mirror the
 * JSON properties the entity's getters produced, so existing console/CLI
 * payloads still bind unchanged. The {@code token} value itself is never
 * accepted from the client (it is server-generated and immutable).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShApiTokenRequest(
        String title,
        String description,
        ShApiTokenScope scope,
        Set<String> siteIds,
        ShApiTokenEnv env,
        boolean enabled) {
}
