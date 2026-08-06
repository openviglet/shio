/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.webhook;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.core.webhook.VigletWebhookDispatcher;

/**
 * Registers the shared {@link VigletWebhookDispatcher} (viglet-core) that
 * {@link ShWebhookDispatcher} delivers outbound webhooks through, mirroring
 * Turing's {@code TurChatWebhookConfig}. A single pooled JDK {@code HttpClient}
 * backs every dispatch; {@code destroyMethod = "close"} tears down the (lazily
 * created) async pool on context shutdown.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Configuration
public class ShWebhookConfig {

    @Bean(destroyMethod = "close")
    VigletWebhookDispatcher vigletWebhookDispatcher(ShWebhookProperties properties) {
        return new VigletWebhookDispatcher(Duration.ofMillis(properties.getConnectTimeoutMillis()));
    }
}
