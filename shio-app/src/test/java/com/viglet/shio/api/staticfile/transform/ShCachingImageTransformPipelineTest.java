/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.viglet.shio.property.ShImageCacheProperties;

class ShCachingImageTransformPipelineTest {

    @TempDir
    Path tempDir;

    private static final ShImageTransformSpec SPEC = new ShImageTransformSpec(100, null, "webp", null);

    private ShImageCacheProperties props(boolean enabled) {
        ShImageCacheProperties p = new ShImageCacheProperties();
        p.setEnabled(enabled);
        p.setPath(tempDir.toAbsolutePath().toString());
        p.setMaxMemoryEntries(50);
        return p;
    }

    private static Resource src() {
        return new ByteArrayResource("source".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "a.png";
            }

            @Override
            public long lastModified() {
                return 1234L;
            }
        };
    }

    /** A delegate that counts calls and returns a fixed (un-stamped) result. */
    private static ShThumbnailatorImageTransformPipeline countingDelegate(AtomicInteger calls,
                                                                          Optional<ShImageTransformResult> answer) {
        return new ShThumbnailatorImageTransformPipeline(new com.viglet.shio.property.ShImageTransformProperties(),
                new com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog()) {
            @Override
            public Optional<ShImageTransformResult> transform(Resource source, String ct, ShImageTransformSpec spec) {
                calls.incrementAndGet();
                return answer;
            }
        };
    }

    @Test
    void cachesPositiveResultAndStampsEtag() {
        AtomicInteger calls = new AtomicInteger();
        byte[] produced = "webp".getBytes(StandardCharsets.UTF_8);
        ShThumbnailatorImageTransformPipeline delegate =
                countingDelegate(calls, Optional.of(new ShImageTransformResult(produced, "image/webp")));
        ShCachingImageTransformPipeline pipeline =
                new ShCachingImageTransformPipeline(delegate, new ShImageTransformCache(props(true)));

        ShImageTransformResult first = pipeline.transform(src(), "image/png", SPEC).orElseThrow();
        assertNotNull(first.etag(), "a cached result carries an ETag");
        assertArrayEquals(produced, first.bytes());

        ShImageTransformResult second = pipeline.transform(src(), "image/png", SPEC).orElseThrow();
        assertEquals(1, calls.get(), "the second identical request is served from cache (delegate not re-run)");
        assertEquals(first.etag(), second.etag(), "same key → same ETag");
        assertArrayEquals(produced, second.bytes());
    }

    @Test
    void doesNotCacheEmptyResults() {
        AtomicInteger calls = new AtomicInteger();
        ShThumbnailatorImageTransformPipeline delegate = countingDelegate(calls, Optional.empty());
        ShCachingImageTransformPipeline pipeline =
                new ShCachingImageTransformPipeline(delegate, new ShImageTransformCache(props(true)));

        assertTrue(pipeline.transform(src(), "image/png", SPEC).isEmpty());
        assertTrue(pipeline.transform(src(), "image/png", SPEC).isEmpty());
        assertEquals(2, calls.get(), "an empty (serve-original) result is re-evaluated, never cached");
    }

    @Test
    void disabledCacheIsPassThroughWithoutEtag() {
        AtomicInteger calls = new AtomicInteger();
        ShThumbnailatorImageTransformPipeline delegate =
                countingDelegate(calls, Optional.of(new ShImageTransformResult("x".getBytes(StandardCharsets.UTF_8),
                        "image/webp")));
        ShCachingImageTransformPipeline pipeline =
                new ShCachingImageTransformPipeline(delegate, new ShImageTransformCache(props(false)));

        ShImageTransformResult first = pipeline.transform(src(), "image/png", SPEC).orElseThrow();
        assertNull(first.etag(), "disabled cache stamps no ETag");
        pipeline.transform(src(), "image/png", SPEC);
        assertEquals(2, calls.get(), "disabled cache always delegates");
    }
}
