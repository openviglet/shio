/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.webhook;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.viglet.shio.persistence.model.webhook.ShSiteWebhook;
import com.viglet.shio.persistence.repository.webhook.ShSiteWebhookRepository;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Outbound webhook emitter (SH12). Listens for {@link ShPostLifecycleEvent}s
 * <b>after</b> the publish/unpublish/delete transaction commits, then signs and
 * POSTs the SH9 payload to every enabled, matching {@link ShSiteWebhook}.
 *
 * <p>The signature scheme matches {@code create-shio-app}'s
 * {@code /api/revalidate} receiver byte-for-byte: HMAC-SHA256 over the exact
 * request body, sent as {@code x-shio-signature: sha256=<hex>}. The same byte
 * array is signed and sent, so no re-serialisation can drift the digest.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Slf4j
@Component
public class ShWebhookDispatcher {

    private final ShSiteWebhookRepository shSiteWebhookRepository;
    private final ShWebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "sh-webhook-dispatch");
        t.setDaemon(true);
        return t;
    });

    public ShWebhookDispatcher(ShSiteWebhookRepository shSiteWebhookRepository,
                               ShWebhookProperties properties,
                               ObjectMapper objectMapper) {
        this.shSiteWebhookRepository = shSiteWebhookRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    /**
     * Fires only once the writing transaction has committed — a rolled-back
     * publish therefore emits nothing.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLifecycle(ShPostLifecycleEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        List<ShSiteWebhook> targets = shSiteWebhookRepository.findByEnabledTrue().stream()
                .filter(w -> w.matches(event.siteId(), event.type()))
                .toList();
        if (targets.isEmpty()) {
            return;
        }

        byte[] body = buildBody(event);
        for (ShSiteWebhook target : targets) {
            // Off the request thread: a slow/failing subscriber must never block
            // the editor's publish response.
            executor.submit(() -> deliver(target, body, event));
        }
    }

    private byte[] buildBody(ShPostLifecycleEvent event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("event", event.type().wire());
        node.put("siteId", event.siteId());
        node.put("postId", event.postId());
        if (event.url() != null) {
            node.put("url", event.url());
        }
        if (event.occurredAt() != null) {
            node.put("occurredAt", event.occurredAt().toString());
        }
        return objectMapper.writeValueAsBytes(node);
    }

    private void deliver(ShSiteWebhook target, byte[] body, ShPostLifecycleEvent event) {
        if (target.getUrl() == null || target.getUrl().isBlank()) {
            return;
        }
        if (target.getSecret() == null || target.getSecret().isBlank()) {
            // Can't HMAC-sign without a key, and an unsigned webhook would be
            // rejected by the receiver anyway — skip rather than throw.
            log.warn("Webhook '{}' has no secret — skipping delivery to {}", target.getName(), target.getUrl());
            return;
        }
        String signature = ShWebhookSigner.header(target.getSecret(), body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(target.getUrl()))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .header("Content-Type", "application/json")
                .header(ShWebhookSigner.SIGNATURE_HEADER, signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    log.debug("Webhook '{}' delivered ({} {}) for post {} -> {}",
                            target.getName(), event.type().wire(), status, event.postId(), target.getUrl());
                    return;
                }
                log.warn("Webhook '{}' got HTTP {} (attempt {}/{}) for {}",
                        target.getName(), status, attempt, maxAttempts, target.getUrl());
            } catch (Exception e) {
                log.warn("Webhook '{}' delivery failed (attempt {}/{}) for {}: {}",
                        target.getName(), attempt, maxAttempts, target.getUrl(), e.getMessage());
            }
            if (attempt < maxAttempts) {
                sleep(properties.getRetryBackoffMillis() * attempt);
            }
        }
        log.warn("Webhook '{}' exhausted {} attempts for {} — dropping {} of post {}",
                target.getName(), maxAttempts, target.getUrl(), event.type().wire(), event.postId());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
