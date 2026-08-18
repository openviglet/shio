/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ShImageTransformSpecTest {

    @Test
    void noParamsYieldsEmpty() {
        assertTrue(ShImageTransformSpec.parse(null, null, null, null).isEmpty());
        assertTrue(ShImageTransformSpec.parse("", "  ", "", " ").isEmpty(),
                "blank parameters are treated as absent");
    }

    @Test
    void parsesAllParameters() {
        ShImageTransformSpec spec = ShImageTransformSpec.parse("800", "600", "WebP", "Fill").orElseThrow();
        assertEquals(800, spec.width());
        assertEquals(600, spec.height());
        assertEquals("webp", spec.format(), "format is lower-cased");
        assertEquals(ShImageCropMode.FILL, spec.crop(),
                "SH39: crop is case-insensitive. This method IS the seam — `?w=&h=&format=&crop=` "
                + "on the delivery path becomes a spec, or it becomes four query parameters the "
                + "file server ignores while returning the original bytes with a 200");
    }

    @Test
    void widthOnlyIsAValidTransform() {
        ShImageTransformSpec spec = ShImageTransformSpec.parse("320", null, null, null).orElseThrow();
        assertEquals(320, spec.width());
        assertNull(spec.height());
        assertNull(spec.format());
        assertNull(spec.crop());
    }

    @Test
    void rejectsNonNumericDimension() {
        assertThrows(ShImageTransformException.class,
                () -> ShImageTransformSpec.parse("wide", null, null, null));
    }

    @Test
    void rejectsNonPositiveDimension() {
        assertThrows(ShImageTransformException.class,
                () -> ShImageTransformSpec.parse("0", null, null, null));
        assertThrows(ShImageTransformException.class,
                () -> ShImageTransformSpec.parse(null, "-5", null, null));
    }

    @Test
    void rejectsUnsupportedFormat() {
        assertThrows(ShImageTransformException.class,
                () -> ShImageTransformSpec.parse(null, null, "bmp", null));
    }

    @Test
    void rejectsUnknownCropMode() {
        assertThrows(ShImageTransformException.class,
                () -> ShImageTransformSpec.parse(null, null, null, "smart"));
    }

    @Test
    void acceptsEveryAllowedFormat() {
        for (String fmt : new String[] {"jpg", "jpeg", "png", "gif", "webp", "avif"}) {
            Optional<ShImageTransformSpec> spec = ShImageTransformSpec.parse(null, null, fmt, null);
            assertEquals(fmt, spec.orElseThrow().format());
        }
    }
}
