/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.webhook;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signing for outbound webhooks (SH12). The scheme matches the
 * {@code create-shio-app} receiver (SH9): the digest is computed over the exact
 * request body and emitted as {@code x-shio-signature: sha256=<lowercase hex>}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public final class ShWebhookSigner {

    /** Header carrying the signature, matching {@code /api/revalidate}. */
    public static final String SIGNATURE_HEADER = "x-shio-signature";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private ShWebhookSigner() {
    }

    /** Lowercase hex of {@code HMAC-SHA256(secret, body)}. */
    public static String hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute webhook signature", e);
        }
    }

    /** The full {@code sha256=<hex>} header value the receiver expects. */
    public static String header(String secret, byte[] body) {
        return "sha256=" + hex(secret, body);
    }
}
