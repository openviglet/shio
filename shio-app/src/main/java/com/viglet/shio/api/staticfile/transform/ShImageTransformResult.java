/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.util.Arrays;
import java.util.Objects;

/**
 * The output of a {@link ShImageTransformPipeline}: the transformed bytes plus the
 * content type they should be served as (which differs from the source when a
 * {@code format} conversion happened — SH41, e.g. jpeg → webp).
 *
 * <p>SH42 adds {@code etag}: a stable validator (the cache key hash) the result
 * cache stamps onto every entry so the delivery path can emit an {@code ETag}
 * header and honour {@code If-None-Match} (→ 304). The producing pipelines
 * (Thumbnailator/no-op) leave it {@code null} via the two-arg convenience
 * constructor — only {@link ShCachingImageTransformPipeline} sets it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShImageTransformResult(byte[] bytes, String contentType, String etag) {

    /** A result with no ETag (the bytes are not cache-stamped yet). */
    public ShImageTransformResult(byte[] bytes, String contentType) {
        this(bytes, contentType, null);
    }

    /** A copy of this result carrying {@code etag}. */
    public ShImageTransformResult withEtag(String etag) {
        return new ShImageTransformResult(bytes, contentType, etag);
    }

    // Records compare/hash array components by reference by default (S6218); the bytes
    // are the payload, so compare them by content.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShImageTransformResult(byte[] otherBytes, String otherContentType, String otherEtag))) {
            return false;
        }
        return Arrays.equals(bytes, otherBytes)
                && Objects.equals(contentType, otherContentType)
                && Objects.equals(etag, otherEtag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(bytes), contentType, etag);
    }

    @Override
    public String toString() {
        return "ShImageTransformResult[bytes=" + (bytes == null ? "null" : bytes.length + " bytes")
                + ", contentType=" + contentType + ", etag=" + etag + "]";
    }
}
