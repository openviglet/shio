/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.webhook;

import com.viglet.core.webhook.VigletWebhookSigner;

/**
 * HMAC-SHA256 signing for outbound webhooks (SH12). The scheme matches the
 * {@code create-shio-app} receiver (SH9): the digest is computed over the exact
 * request body and emitted as {@code x-shio-signature: sha256=<lowercase hex>}.
 *
 * <p>The signing algorithm now delegates to the shared
 * {@link VigletWebhookSigner} (viglet-core) — byte-for-byte identical
 * ({@code sha256=<lowercase hex>}, empty key rejected). Only the Shio-specific
 * header <i>name</i> stays here.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public final class ShWebhookSigner {

    /** Header carrying the signature, matching {@code /api/revalidate}. */
    public static final String SIGNATURE_HEADER = "x-shio-signature";

    private ShWebhookSigner() {
    }

    /** Lowercase hex of {@code HMAC-SHA256(secret, body)}. */
    public static String hex(String secret, byte[] body) {
        return VigletWebhookSigner.hex(secret, body);
    }

    /** The full {@code sha256=<hex>} header value the receiver expects. */
    public static String header(String secret, byte[] body) {
        return VigletWebhookSigner.header(secret, body);
    }
}
