/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.util.Locale;

import tools.jackson.databind.JsonNode;

/**
 * Whether a {@code File} post holds an image, by the stamp first and the name second.
 *
 * <p>The stamped content type is what SH1030 writes at upload; the extension is what covers
 * everything written before it. Asking the disk would make every reader pay a syscall per asset to
 * answer a question the row already knows. One rule for its readers — verify's alt-text check and
 * the console's thumbnails (SH1026) — so the two cannot disagree about what an image is.</p>
 */
public final class ShAssetKind {

    private ShAssetKind() {
    }

    public static boolean isImage(JsonNode data, String fileName) {
        JsonNode stamp = data == null ? null : data.path("CONTENT_TYPE");
        String contentType = stamp != null && stamp.isValueNode() && !stamp.isNull()
                ? stamp.asString().trim() : "";
        if (!contentType.isBlank()) {
            return contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".avif")
                || name.endsWith(".svg") || name.endsWith(".bmp") || name.endsWith(".tif")
                || name.endsWith(".tiff");
    }
}
