/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.viglet.shio.property.ShImageTransformProperties;

class ShImageTransformSignerTest {

    private static ShImageTransformSigner signer(String secret) {
        ShImageTransformProperties properties = new ShImageTransformProperties();
        properties.getSigning().setSecret(secret);
        return new ShImageTransformSigner(properties);
    }

    private static ShImageTransformSpec spec(String w, String h, String format, String crop) {
        return ShImageTransformSpec.parse(w, h, format, crop).orElseThrow();
    }

    @Test
    void signedTokenVerifies() {
        ShImageTransformSigner signer = signer("super-secret");
        ShImageTransformSpec spec = spec("800", "600", "webp", "fill");

        String sig = signer.sign("post-1", spec);
        assertTrue(signer.verify("post-1", spec, sig), "the minted signature verifies");
    }

    @Test
    void signatureIsBoundToResourceId() {
        ShImageTransformSigner signer = signer("super-secret");
        ShImageTransformSpec spec = spec("800", null, null, null);

        String sig = signer.sign("post-1", spec);
        assertFalse(signer.verify("post-2", spec, sig),
                "a signature minted for one post must not verify for another");
    }

    @Test
    void signatureIsBoundToParams() {
        ShImageTransformSigner signer = signer("super-secret");
        String sig = signer.sign("post-1", spec("800", null, null, null));

        assertFalse(signer.verify("post-1", spec("400", null, null, null), sig),
                "tampering with a param invalidates the signature");
    }

    @Test
    void differentSecretsProduceDifferentSignatures() {
        ShImageTransformSpec spec = spec("800", "600", null, null);
        assertNotEquals(signer("secret-a").sign("post-1", spec),
                signer("secret-b").sign("post-1", spec));
    }

    @Test
    void verifyFailsClosedOnMissingSignature() {
        ShImageTransformSigner signer = signer("super-secret");
        ShImageTransformSpec spec = spec("800", null, null, null);

        assertFalse(signer.verify("post-1", spec, null), "no signature → reject");
        assertFalse(signer.verify("post-1", spec, "  "), "blank signature → reject");
    }

    @Test
    void verifyFailsClosedWithoutSecret() {
        ShImageTransformSigner signer = signer("");
        assertFalse(signer.verify("post-1", spec("800", null, null, null), "anything"),
                "no configured secret → reject all");
    }

    @Test
    void signWithoutSecretThrows() {
        ShImageTransformSigner signer = signer("");
        ShImageTransformSpec spec = spec("800", null, null, null);
        assertThrows(IllegalStateException.class,
                () -> signer.sign("post-1", spec),
                "nothing can be minted without a secret");
    }
}
