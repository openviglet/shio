/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.webhook;

import java.time.Instant;

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
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShPostLifecycleEvent(ShWebhookEventType type, String postId, String siteId,
                                   String url, String locale, Instant occurredAt) {
}
