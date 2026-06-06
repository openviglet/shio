/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Locks the SH12 → SH9 signing contract. The dispatcher must produce exactly
 * the {@code sha256=<hex>} value that {@code create-shio-app}'s
 * {@code /api/revalidate} (and {@code openssl dgst -sha256 -hmac}) recompute
 * over the raw body. The reference digests below were generated with:
 *
 * <pre>
 * printf '%s' '{"event":"publish","siteId":"s1","url":"/blog/hello"}' \
 *   | openssl dgst -sha256 -hmac 'topsecret' -hex
 * </pre>
 *
 * @author Alexandre Oliveira
 */
class ShWebhookSignerTest {

    @Test
    void matchesOpensslReferenceDigest() {
        byte[] body = "{\"event\":\"publish\",\"siteId\":\"s1\",\"url\":\"/blog/hello\"}"
                .getBytes(StandardCharsets.UTF_8);

        // openssl dgst -sha256 -hmac 'topsecret' over the exact body above.
        assertThat(ShWebhookSigner.hex("topsecret", body))
                .isEqualTo("9b7e68d5ec86434066f7367642ba7819891e100a6b93153fdf01b2107052ef9f");
    }

    @Test
    void headerCarriesSha256Prefix() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String header = ShWebhookSigner.header("k", body);
        assertThat(header).startsWith("sha256=");
        assertThat(header.substring("sha256=".length())).isEqualTo(ShWebhookSigner.hex("k", body));
    }

    @Test
    void differentSecretsProduceDifferentDigests() {
        byte[] body = "{\"event\":\"publish\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(ShWebhookSigner.hex("secretA", body))
                .isNotEqualTo(ShWebhookSigner.hex("secretB", body));
    }

    @Test
    void digestIsLowercaseHex64Chars() {
        String hex = ShWebhookSigner.hex("k", "payload".getBytes(StandardCharsets.UTF_8));
        assertThat(hex).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void blankSecretCannotSign() {
        // HMAC requires a non-empty key; the dispatcher guards against blank
        // secrets before reaching here (it skips delivery). Signing one is an
        // error, not a silent empty-key.
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> ShWebhookSigner.hex("", body))
                .isInstanceOf(IllegalStateException.class);
    }
}
