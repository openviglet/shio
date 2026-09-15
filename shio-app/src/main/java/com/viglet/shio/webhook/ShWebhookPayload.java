/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.webhook;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The bytes an outbound webhook carries, built in one place (SH998).
 *
 * <h2>Why this is not a private method on the dispatcher any more</h2>
 *
 * <p>These bytes are what gets HMAC-signed, so they are the contract a receiver verifies against —
 * and the one thing that must not change by accident when a new event type is added. A private
 * method inside a component that needs a Spring context, an HTTP target and a pool thread to reach
 * is a contract nothing asserts: proving the payload names the failing check meant standing up a
 * receiver. Here it is a pure function of the event, and a test can read it the way a subscriber
 * does.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShWebhookPayload {

    private ShWebhookPayload() {
    }

    /**
     * One event as its signed JSON body.
     *
     * <p>Fixed keys first and in a fixed order, then the event's own facts — which is what makes the
     * body a function of the event alone. A fact may not redefine a fixed key: the alternative is a
     * gate named {@code event} rewriting what kind of thing arrived.</p>
     */
    public static byte[] of(ObjectMapper objectMapper, ShPostLifecycleEvent event) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("event", event.type().wire());
        node.put("siteId", event.siteId());
        // Absent rather than null (SH190): a notice about a run is not about one post, and a written
        // `"postId": null` reads as a post the sender failed to name.
        if (event.postId() != null) {
            node.put("postId", event.postId());
        }
        if (event.url() != null) {
            node.put("url", event.url());
        }
        if (event.occurredAt() != null) {
            node.put("occurredAt", event.occurredAt().toString());
        }
        event.facts().forEach((key, value) -> {
            if (value != null && !node.has(key)) {
                node.put(key, value);
            }
        });
        return objectMapper.writeValueAsBytes(node);
    }
}
