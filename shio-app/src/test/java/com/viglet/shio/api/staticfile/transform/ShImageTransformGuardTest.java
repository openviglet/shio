/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.viglet.shio.property.ShImageTransformProperties;

class ShImageTransformGuardTest {

    private static ShImageTransformGuard guard(ShImageTransformProperties properties) {
        return new ShImageTransformGuard(properties, new ShImageTransformSigner(properties));
    }

    private static ShImageTransformSpec spec(String w, String h, String format, String crop) {
        return ShImageTransformSpec.parse(w, h, format, crop).orElseThrow();
    }

    @Test
    void defaultPolicyAllowsAReasonableTransform() {
        assertDoesNotThrow(() -> guard(new ShImageTransformProperties())
                .check("post-1", spec("800", "600", "webp", "fit"), null));
    }

    @Test
    void rejectsWidthOverCeiling() {
        ShImageTransformProperties properties = new ShImageTransformProperties();
        properties.setMaxWidth(1000);
        assertThrows(ShImageTransformException.class,
                () -> guard(properties).check("post-1", spec("4000", null, null, null), null));
    }

    @Test
    void rejectsHeightOverCeiling() {
        ShImageTransformProperties properties = new ShImageTransformProperties();
        properties.setMaxHeight(1000);
        assertThrows(ShImageTransformException.class,
                () -> guard(properties).check("post-1", spec(null, "4000", null, null), null));
    }

    @Test
    void rejectsDisallowedFormat() {
        ShImageTransformProperties properties = new ShImageTransformProperties();
        properties.setAllowedFormats(List.of("webp", "png")); // jpeg dropped from the policy
        assertThrows(ShImageTransformException.class,
                () -> guard(properties).check("post-1", spec(null, null, "jpeg", null), null));
    }

    @Test
    void allowsPermittedFormat() {
        ShImageTransformProperties properties = new ShImageTransformProperties();
        properties.setAllowedFormats(List.of("webp"));
        assertDoesNotThrow(() -> guard(properties).check("post-1", spec(null, null, "webp", null), null));
    }

    @Test
    void signingOffIgnoresSignature() {
        // Default signing is off, so a missing sig is fine.
        assertDoesNotThrow(() -> guard(new ShImageTransformProperties())
                .check("post-1", spec("100", null, null, null), null));
    }

    @Test
    void signingOnRejectsUnsigned() {
        ShImageTransformProperties properties = new ShImageTransformProperties();
        properties.getSigning().setEnabled(true);
        properties.getSigning().setSecret("secret");
        assertThrows(ShImageTransformForbiddenException.class,
                () -> guard(properties).check("post-1", spec("100", null, null, null), null));
    }

    @Test
    void signingOnAcceptsValidSignature() {
        ShImageTransformProperties properties = new ShImageTransformProperties();
        properties.getSigning().setEnabled(true);
        properties.getSigning().setSecret("secret");
        ShImageTransformSigner signer = new ShImageTransformSigner(properties);
        ShImageTransformGuard guard = new ShImageTransformGuard(properties, signer);
        ShImageTransformSpec spec = spec("100", null, null, null);

        String sig = signer.sign("post-1", spec);
        assertDoesNotThrow(() -> guard.check("post-1", spec, sig));
    }

    @Test
    void signatureIsCheckedBeforeLimits() {
        // An unsigned, also-out-of-policy request is a 403 (authz first), not a 400.
        ShImageTransformProperties properties = new ShImageTransformProperties();
        properties.getSigning().setEnabled(true);
        properties.getSigning().setSecret("secret");
        properties.setMaxWidth(10);
        assertThrows(ShImageTransformForbiddenException.class,
                () -> guard(properties).check("post-1", spec("9000", null, null, null), null));
    }
}
