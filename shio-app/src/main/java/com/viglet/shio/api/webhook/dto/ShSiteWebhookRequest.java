/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.webhook.dto;

import java.util.Set;

import com.viglet.shio.persistence.model.webhook.ShWebhookEventType;

/**
 * Request payload for creating/updating a {@code ShSiteWebhook} (SH12).
 *
 * <p>Replaces the persistent {@code ShSiteWebhook} entity as the
 * {@code @RequestBody} type (Sonar java:S4684). Component names mirror the
 * JSON properties the entity's getters produced, so existing console/CLI
 * payloads still bind unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShSiteWebhookRequest(
        String name,
        String siteId,
        String url,
        String secret,
        Set<ShWebhookEventType> events,
        boolean enabled) {
}
