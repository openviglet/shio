/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.webhook;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.viglet.shio.persistence.model.webhook.ShWebhookEventType;

/**
 * Immutable snapshot of a content-lifecycle transition, published by
 * {@code ShPostUnifiedWriteService} (SH12). It carries everything the outbound
 * webhook needs so the dispatcher never has to touch a (possibly deleted) row:
 * the snapshot is captured <b>inside</b> the write transaction, before any
 * unpublish/delete removes the post.
 *
 * <p>SH84 added {@link #locale}, and for exactly the reason the snapshot exists at all: a
 * de-index has to name the locale the document was indexed under, and by the time a
 * {@code DELETE} listener runs the row has been purged, so it cannot be asked. A field the
 * snapshot does not carry is a field no {@code AFTER_COMMIT} listener can ever recover.
 *
 * @param type       the transition (publish / unpublish / delete)
 * @param postId     the post id
 * @param siteId     the post's site id (may be {@code null} if unresolved)
 * @param url        the friendly URL, leading-slash normalised (may be {@code null})
 * @param locale     the post's locale tag, e.g. {@code en} (may be {@code null})
 * @param occurredAt when the transition committed
 * @param facts      what this event is about beyond the post, written flat into the payload — the
 *                   gate and check of a {@code GATE_FAILED}, the run of a {@code REVIEW_REQUESTED}
 *                   (SH998). Empty for a content transition, which the post itself describes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShPostLifecycleEvent(ShWebhookEventType type, String postId, String siteId,
                                   String url, String locale, Instant occurredAt,
                                   Map<String, String> facts) {

    /** The pre-SH998 form: a transition, which the five fields above already describe. */
    public ShPostLifecycleEvent(ShWebhookEventType type, String postId, String siteId,
                                String url, String locale, Instant occurredAt) {
        this(type, postId, siteId, url, locale, occurredAt, Map.of());
    }

    public ShPostLifecycleEvent {
        // Insertion order, and never Map.copyOf: the body is HMAC-signed over its exact bytes, so
        // two deliveries of one event have to serialize identically (P5). A hash-ordered map makes
        // that true per JVM run and false between them, which is the worst of the two states — it
        // passes here and fails at a receiver comparing signatures.
        facts = facts == null || facts.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }
}
