/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.viglet.shio.property.ShImageTransformProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Mints and verifies signed image-transform URLs (SH43). When
 * {@code shio.image-transform.signing.enabled} is on, a transform request must carry
 * a {@code sig} that this signer can reproduce from the same secret — otherwise
 * {@link ShImageTransformGuard} rejects it with a 403. This stops the open transform
 * surface from being abused to flood the SH42 cache with arbitrary param combinations.
 *
 * <p><strong>What is signed</strong> — a canonical descriptor binding the request to a
 * specific resource <em>and</em> a specific transform:
 * {@code resourceId | w | h | format | crop} (null fields render as empty). Binding the
 * {@code resourceId} (the {@code File} post id, identical on both delivery routes) means
 * a signature minted for one image can't be replayed against another.</p>
 *
 * <p><strong>Algorithm</strong> — HMAC-SHA256 over UTF-8 of the canonical string, keyed
 * by the configured secret, rendered URL-safe base64 without padding. Verification is
 * constant-time ({@link MessageDigest#isEqual}). With the secret blank the signer fails
 * closed: {@link #sign} throws (nothing can be minted) and {@link #verify} returns
 * {@code false} (everything is rejected).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
@Slf4j
public class ShImageTransformSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ShImageTransformProperties properties;

    public ShImageTransformSigner(ShImageTransformProperties properties) {
        this.properties = properties;
    }

    /**
     * The signature a valid {@code sig} for {@code resourceId} + {@code spec} must equal.
     *
     * @throws IllegalStateException when no signing secret is configured (nothing can be
     *         minted without one)
     */
    public String sign(String resourceId, ShImageTransformSpec spec) {
        String secret = properties.getSigning().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Cannot sign an image-transform URL: shio.image-transform.signing.secret is not set");
        }
        return hmacBase64Url(secret, canonical(resourceId, spec));
    }

    /**
     * Whether {@code providedSig} is the valid signature for {@code resourceId} +
     * {@code spec}. Returns {@code false} (fail closed) for a blank/absent provided
     * signature or an unconfigured secret.
     */
    public boolean verify(String resourceId, ShImageTransformSpec spec, String providedSig) {
        if (providedSig == null || providedSig.isBlank()) {
            return false;
        }
        String secret = properties.getSigning().getSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Image-transform signing is enabled but no secret is configured; rejecting all transforms");
            return false;
        }
        String expected = hmacBase64Url(secret, canonical(resourceId, spec));
        // Constant-time comparison so a timing side-channel can't leak the signature.
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSig.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** The canonical, order-fixed descriptor that gets HMAC'd. Null fields render empty. */
    private static String canonical(String resourceId, ShImageTransformSpec spec) {
        return String.join("|",
                resourceId != null ? resourceId : "",
                spec.width() != null ? spec.width().toString() : "",
                spec.height() != null ? spec.height().toString() : "",
                spec.format() != null ? spec.format() : "",
                spec.crop() != null ? spec.crop().name() : "");
    }

    private static String hmacBase64Url(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException e) {
            // HmacSHA256 is a mandated JCE algorithm; this is unreachable.
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("Invalid image-transform signing secret", e);
        }
    }
}
