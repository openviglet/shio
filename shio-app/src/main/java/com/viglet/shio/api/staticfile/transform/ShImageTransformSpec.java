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
import java.util.Optional;
import java.util.Set;

/**
 * A parsed, validated image-transform request lifted off the delivery-path query
 * string (SH39): {@code ?w=&h=&format=&crop=}. Immutable; any field may be
 * {@code null} (a transform that only resizes leaves {@code format}/{@code crop}
 * unset, etc.).
 *
 * <p>{@link #parse} is the single entry point. It returns {@link Optional#empty()}
 * when <em>no</em> transform parameter is present (the common case — the request
 * is for the original bytes), and throws {@link ShImageTransformException} (→ 400)
 * for a present-but-invalid value. Hard ceilings (max dimensions, signed URLs) are
 * deliberately out of scope here — that is SH43; this stage only parses + sanity
 * checks, then routes to the {@link ShImageTransformPipeline}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShImageTransformSpec(Integer width, Integer height, String format, ShImageCropMode crop) {

    /** Output formats accepted on the {@code format} parameter (SH41 encodes them; avif degrades gracefully). */
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "gif", "webp", "avif");

    /**
     * Parse the four delivery-path parameters into a spec.
     *
     * @return the spec, or {@link Optional#empty()} when none of the parameters
     *         are present (so the caller serves the original bytes untouched)
     * @throws ShImageTransformException for a malformed dimension, an unsupported
     *         format, or an unknown crop mode
     */
    public static Optional<ShImageTransformSpec> parse(String w, String h, String format, String crop) {
        Integer width = parseDimension(w, "w");
        Integer height = parseDimension(h, "h");
        String fmt = parseFormat(format);
        ShImageCropMode cropMode = ShImageCropMode.fromParam(crop);

        if (width == null && height == null && fmt == null && cropMode == null) {
            return Optional.empty();
        }
        return Optional.of(new ShImageTransformSpec(width, height, fmt, cropMode));
    }

    private static Integer parseDimension(String raw, String name) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ShImageTransformException(name + " must be an integer");
        }
        if (value <= 0) {
            throw new ShImageTransformException(name + " must be a positive integer");
        }
        return value;
    }

    private static String parseFormat(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String fmt = raw.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_FORMATS.contains(fmt)) {
            throw new ShImageTransformException("Unsupported format: " + raw);
        }
        return fmt;
    }
}
