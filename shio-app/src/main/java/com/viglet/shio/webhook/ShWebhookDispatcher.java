/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.webhook;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.viglet.core.webhook.VigletWebhookDispatcher;
import com.viglet.core.webhook.VigletWebhookRequest;
import com.viglet.core.webhook.VigletWebhookRetryPolicy;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.persistence.model.webhook.ShSiteWebhook;
import com.viglet.shio.persistence.repository.webhook.ShSiteWebhookRepository;

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
 * <p>Delivery (signing + POST + bounded linear retry/back-off, off the request
 * thread) is delegated to the shared {@link VigletWebhookDispatcher}
 * (viglet-core). Only the Shio-specific concerns stay here: reacting to the
 * post-lifecycle event, filtering matching targets, and building the SH9 JSON
 * body. Behaviour is preserved — the retry/back-off, timeouts and async
 * dispatch map one-to-one onto {@link VigletWebhookRetryPolicy} +
 * {@link VigletWebhookDispatcher#dispatchAsync}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Slf4j
@Component
public class ShWebhookDispatcher {

    /** Said once here rather than twice below; both failures have the same remedy. */
    private static final String RETRY_FIX =
            "The subscriber did not accept the delivery, so its pages may still be stale. "
                    + "Check the target is up and the shared secret matches, then re-publish "
                    + "the post to re-fire it.";

    private final ShSiteWebhookRepository shSiteWebhookRepository;
    private final ShWebhookProperties properties;
    private final ObjectMapper objectMapper;
    private final VigletWebhookDispatcher dispatcher;
    private final ShAgentIncidentLog incidentLog;

    public ShWebhookDispatcher(ShSiteWebhookRepository shSiteWebhookRepository,
                               ShWebhookProperties properties,
                               ObjectMapper objectMapper,
                               VigletWebhookDispatcher dispatcher,
                               ShAgentIncidentLog incidentLog) {
        this.shSiteWebhookRepository = shSiteWebhookRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.incidentLog = incidentLog;
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
        VigletWebhookRetryPolicy policy = new VigletWebhookRetryPolicy(
                properties.getMaxAttempts(),
                Duration.ofMillis(properties.getReadTimeoutMillis()),
                Duration.ofMillis(properties.getRetryBackoffMillis()));

        for (ShSiteWebhook target : targets) {
            deliver(target, body, event, policy);
        }
    }

    private void deliver(ShSiteWebhook target, byte[] body, ShPostLifecycleEvent event,
                         VigletWebhookRetryPolicy policy) {
        if (target.getUrl() == null || target.getUrl().isBlank()) {
            return;
        }
        if (target.getSecret() == null || target.getSecret().isBlank()) {
            // Can't HMAC-sign without a key, and an unsigned webhook would be
            // rejected by the receiver anyway — skip rather than throw.
            log.warn("Webhook '{}' has no secret — skipping delivery to {}", target.getName(), target.getUrl());
            return;
        }
        VigletWebhookRequest request = VigletWebhookRequest.builder()
                .url(target.getUrl())
                .body(body)
                .contentType("application/json")
                .signingSecret(target.getSecret())
                .signatureHeaderName(ShWebhookSigner.SIGNATURE_HEADER)
                .build();

        // Off the request thread: a slow/failing subscriber must never block the
        // editor's publish response. The shared dispatcher owns the async pool.
        dispatcher.dispatchAsync(request, policy).whenComplete((result, error) -> {
            if (error != null) {
                log.warn("Webhook '{}' dispatch errored for {}: {}",
                        target.getName(), target.getUrl(), error.getMessage());
                // SH103: a log line is not a surface an agent has. A revalidation that
                // never arrived is the difference between "published" and "visible", so
                // it is recorded where GET /api/v2/agent/diagnostics can report it.
                // SH269: the frame, because this branch has a real throwable — a connection
                // reset, a body that would not serialize — and "dispatch errored" alone does not
                // say whether the fault was ours or the subscriber's. `CompletionException` wraps
                // it, and `ShAgentIncidentFrames` walks to the deepest cause for that reason.
                incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, target.getUrl(),
                        "dispatch errored: " + error.getMessage(), RETRY_FIX,
                        ShAgentIncidentFrames.of(error));
            } else if (result.delivered()) {
                log.debug("Webhook '{}' delivered ({} {}) for post {} -> {}",
                        target.getName(), event.type().wire(), result.statusCode(), event.postId(),
                        target.getUrl());
            } else {
                log.warn("Webhook '{}' failed after {} attempt(s) for {} (last status {}) — dropping {} of post {}",
                        target.getName(), result.attempts(), target.getUrl(), result.statusCode(),
                        event.type().wire(), event.postId());
                // No frame here, and that is the honest answer rather than an omission: the
                // delivery completed and the *subscriber* rejected it, so there is no exception of
                // ours and nothing in Shio's code to point at. SH190's rule — absent beats empty.
                incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, target.getUrl(),
                        "gave up after " + result.attempts() + " attempt(s), last status "
                                + result.statusCode(), RETRY_FIX);
            }
        });
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
}
