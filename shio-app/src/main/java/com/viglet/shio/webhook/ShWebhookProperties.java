/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for the outbound webhook emitter (SH12). Mirrors
 * {@code ShCdaProperties} — bound to {@code shio.webhook.*}, defaults shown.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Getter
@Setter
@Component
@ConfigurationProperties("shio.webhook")
public class ShWebhookProperties {

    /** Master switch for outbound webhook emission. */
    private boolean enabled = true;

    /** TCP connect timeout for a delivery attempt. */
    private long connectTimeoutMillis = 3000;

    /** Per-attempt response timeout. */
    private long readTimeoutMillis = 5000;

    /** Total delivery attempts per target before giving up (best-effort, no DLQ). */
    private int maxAttempts = 3;

    /** Linear back-off between attempts (multiplied by the attempt number). */
    private long retryBackoffMillis = 2000;
}
