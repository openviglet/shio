/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.model.webhook;

/**
 * The content-lifecycle transitions an outbound webhook (SH12) can fire on.
 * The wire value emitted in the payload is the {@link #wire() lower-cased} name
 * (e.g. {@code "publish"}), to match the {@code create-shio-app} receiver (SH9).
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public enum ShWebhookEventType {

    PUBLISH,
    UNPUBLISH,
    DELETE;

    /** The lower-cased token sent in the JSON {@code event} field. */
    public String wire() {
        return name().toLowerCase();
    }
}
