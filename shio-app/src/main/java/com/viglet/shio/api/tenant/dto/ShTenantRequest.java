/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.tenant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request DTO for the tenant catalog write endpoints (create / update / signup).
 *
 * <p>Introduced to satisfy SonarQube {@code java:S4684} — controllers must not bind
 * a persistence entity ({@code ShTenant}) directly from the request body. This record
 * mirrors only the writable fields the {@code ShTenantAPI} write methods actually
 * consume; its component names match the JSON field names that a serialized
 * {@code ShTenant} produces, so the React console payloads and {@code ShTenantAPITest}
 * still bind unchanged. Any other serialized field (id, owner, status, createdDate, …)
 * is ignored.
 *
 * @author Alexandre Oliveira
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShTenantRequest(
        String name,
        String description,
        boolean enabled,
        String plan) {
}
