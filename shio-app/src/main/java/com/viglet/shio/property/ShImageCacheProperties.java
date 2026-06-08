/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.property;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the image-transform result cache (SH42).
 *
 * <p>Transformed bytes are expensive to recompute (decode → resize/crop → re-encode,
 * SH40/SH41), yet a given {@code source + ?w=&h=&format=&crop=} combination always
 * yields the same output. {@link com.viglet.shio.api.staticfile.transform.ShCachingImageTransformPipeline}
 * memoises that output — a bounded in-memory LRU in front of a disk cache rooted
 * here — and stamps each result with a stable ETag.</p>
 *
 * <p>The on-disk layout is {@code <root>/<tenantId>/<hash>} (SH22-aware: the tenant
 * is part of the cache key, so tenant A and B never share an entry or an ETag even
 * for the same source path and params).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio.image-cache")
public class ShImageCacheProperties {

    /** Whether the result cache is active; when {@code false} every request recomputes. */
    private boolean enabled = true;

    /** Root directory for cached bytes; relative paths resolve against {@code user.dir}. */
    private String path = "store/image_cache";

    /** Maximum number of results held in the in-memory LRU (disk is unbounded). */
    private int maxMemoryEntries = 200;

    /**
     * The absolute, normalized cache root directory. A relative {@link #path} is
     * resolved against the process working directory ({@code user.dir}), matching
     * {@link ShFileSourceProperties}.
     */
    public Path getRootDir() {
        Path configured = Paths.get(path);
        Path resolved = configured.isAbsolute()
                ? configured
                : Paths.get(System.getProperty("user.dir")).resolve(configured);
        return resolved.toAbsolutePath().normalize();
    }
}
