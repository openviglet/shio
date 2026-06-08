/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * The SH42 result cache, wired as the {@code @Primary} {@link ShImageTransformPipeline}
 * so both delivery routes (the {@code /api/v2/staticfile/{id}} controller and the
 * {@code /file_source/**} resource transformer) go through it transparently. It
 * memoises the {@link ShThumbnailatorImageTransformPipeline} (SH40/SH41) output via
 * {@link ShImageTransformCache} and stamps every served result with a stable ETag.
 *
 * <p>Flow: build the cache key (tenant + source validators + params), return a hit
 * straight from the cache (ETag already set), otherwise run the delegate and — when
 * it actually produced bytes — store + return the stamped result. A delegate that
 * declines ({@link Optional#empty()} — non-image, writer-less target, I/O failure)
 * is <em>not</em> cached: the request falls through to serving the original bytes,
 * exactly as before SH42, and a later upload of a transformable image at the same id
 * is not poisoned by a negative entry.</p>
 *
 * <p>When the cache is disabled ({@code shio.image-cache.enabled=false}) this is a
 * thin pass-through to the delegate — no key is computed, no ETag is stamped.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Primary
@Slf4j
public class ShCachingImageTransformPipeline implements ShImageTransformPipeline {

    private final ShThumbnailatorImageTransformPipeline delegate;
    private final ShImageTransformCache cache;

    public ShCachingImageTransformPipeline(ShThumbnailatorImageTransformPipeline delegate,
                                           ShImageTransformCache cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public Optional<ShImageTransformResult> transform(Resource source, String sourceContentType,
                                                      ShImageTransformSpec spec) {
        if (!cache.isEnabled()) {
            return delegate.transform(source, sourceContentType, spec);
        }

        String key = cache.keyFor(source, spec);
        Optional<ShImageTransformResult> cached = cache.lookup(key);
        if (cached.isPresent()) {
            log.debug("Image transform cache hit {}", key);
            return cached;
        }

        Optional<ShImageTransformResult> produced = delegate.transform(source, sourceContentType, spec);
        // Only positive results are cached — an empty result means "serve the original",
        // which must stay re-evaluable (e.g. the bytes change to a transformable image).
        return produced.map(result -> cache.store(key, result));
    }
}
