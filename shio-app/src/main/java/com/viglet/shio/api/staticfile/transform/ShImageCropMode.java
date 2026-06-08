/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.util.Locale;

/**
 * How the {@code crop} delivery-path parameter constrains a resize (SH39 parses
 * it; the SH40 transform pipeline interprets it):
 *
 * <ul>
 *   <li>{@link #FIT} — scale to fit inside {@code w}×{@code h}, preserve aspect, no crop.</li>
 *   <li>{@link #FILL} — scale to cover {@code w}×{@code h}, preserve aspect, crop the overflow.</li>
 *   <li>{@link #CROP} — crop to {@code w}×{@code h} without scaling.</li>
 *   <li>{@link #SCALE} — stretch to exactly {@code w}×{@code h}, ignore aspect.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public enum ShImageCropMode {

    FIT, FILL, CROP, SCALE;

    /**
     * Parse a case-insensitive {@code crop} parameter, or {@code null} when
     * absent/blank.
     *
     * @throws ShImageTransformException if the value is not a known mode
     */
    public static ShImageCropMode fromParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ShImageCropMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ShImageTransformException("Unsupported crop mode: " + raw);
        }
    }
}
