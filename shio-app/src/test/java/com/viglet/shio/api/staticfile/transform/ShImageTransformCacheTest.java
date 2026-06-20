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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.viglet.shio.property.ShImageCacheProperties;
import com.viglet.shio.tenant.ShTenantContext;

class ShImageTransformCacheTest {

    @TempDir
    Path tempDir;

    private ShImageCacheProperties props(boolean enabled) {
        ShImageCacheProperties p = new ShImageCacheProperties();
        p.setEnabled(enabled);
        p.setPath(tempDir.toAbsolutePath().toString());
        p.setMaxMemoryEntries(2);
        return p;
    }

    private static Resource res(String name, byte[] bytes, long lastModified) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return name;
            }

            @Override
            public long lastModified() {
                return lastModified;
            }
        };
    }

    @AfterEach
    void clearTenant() {
        ShTenantContext.clear();
    }

    @Test
    void keyIsStableForSameSourceAndSpec() {
        ShImageTransformCache cache = new ShImageTransformCache(props(true));
        Resource src = res("a.png", "bytes".getBytes(StandardCharsets.UTF_8), 1000L);
        ShImageTransformSpec spec = new ShImageTransformSpec(100, null, "webp", null);

        assertEquals(cache.keyFor(src, spec), cache.keyFor(src, spec), "same inputs → same key");
    }

    @Test
    void keyChangesWithParamsAndSourceVersion() {
        ShImageTransformCache cache = new ShImageTransformCache(props(true));
        Resource src = res("a.png", "bytes".getBytes(StandardCharsets.UTF_8), 1000L);

        String base = cache.keyFor(src, new ShImageTransformSpec(100, null, "webp", null));
        assertNotEquals(base, cache.keyFor(src, new ShImageTransformSpec(200, null, "webp", null)),
                "a different width yields a different key");
        assertNotEquals(base, cache.keyFor(src, new ShImageTransformSpec(100, null, "png", null)),
                "a different format yields a different key");
        assertNotEquals(base, cache.keyFor(res("a.png", "bytes".getBytes(StandardCharsets.UTF_8), 2000L),
                new ShImageTransformSpec(100, null, "webp", null)),
                "a changed last-modified (re-upload) yields a different key");
    }

    @Test
    void keyIsTenantScoped() {
        ShImageTransformCache cache = new ShImageTransformCache(props(true));
        Resource src = res("a.png", "bytes".getBytes(StandardCharsets.UTF_8), 1000L);
        ShImageTransformSpec spec = new ShImageTransformSpec(100, null, null, null);

        ShTenantContext.setTenantId("alpha");
        String alpha = cache.keyFor(src, spec);
        ShTenantContext.setTenantId("beta");
        String beta = cache.keyFor(src, spec);

        assertNotEquals(alpha, beta, "the same source + params hash differently per tenant (SH22)");
    }

    @Test
    void storeThenLookupRoundTripsBytesContentTypeAndEtag() {
        ShImageTransformCache cache = new ShImageTransformCache(props(true));
        Resource src = res("a.png", "bytes".getBytes(StandardCharsets.UTF_8), 1000L);
        String key = cache.keyFor(src, new ShImageTransformSpec(100, null, "webp", null));

        byte[] produced = "transformed".getBytes(StandardCharsets.UTF_8);
        ShImageTransformResult stored = cache.store(key, new ShImageTransformResult(produced, "image/webp"));
        assertEquals(key, stored.etag(), "store stamps the key as the ETag");

        Optional<ShImageTransformResult> hit = cache.lookup(key);
        assertTrue(hit.isPresent());
        assertArrayEquals(produced, hit.get().bytes());
        assertEquals("image/webp", hit.get().contentType());
        assertEquals(key, hit.get().etag());
    }

    @Test
    void lookupReadsFromDiskAfterMemoryEviction() {
        // Cache another fresh instance over the same disk root → no in-memory state,
        // so a hit can only come from the disk layer written by store().
        ShImageTransformCache writer = new ShImageTransformCache(props(true));
        Resource src = res("a.png", "bytes".getBytes(StandardCharsets.UTF_8), 1000L);
        String key = writer.keyFor(src, new ShImageTransformSpec(50, 50, null, null));
        writer.store(key, new ShImageTransformResult("disk".getBytes(StandardCharsets.UTF_8), "image/png"));

        ShImageTransformCache reader = new ShImageTransformCache(props(true));
        Optional<ShImageTransformResult> hit = reader.lookup(key);
        assertTrue(hit.isPresent(), "the entry persists on disk across cache instances");
        assertArrayEquals("disk".getBytes(StandardCharsets.UTF_8), hit.get().bytes());
        assertEquals(key, hit.get().etag());
    }

    @Test
    void missReturnsEmpty() {
        ShImageTransformCache cache = new ShImageTransformCache(props(true));
        assertTrue(cache.lookup("deadbeef").isEmpty(), "an unknown key is a miss");
    }

    @Test
    void enabledReflectsProperty() {
        assertTrue(new ShImageTransformCache(props(true)).isEnabled());
        assertFalse(new ShImageTransformCache(props(false)).isEnabled());
    }

    @Test
    void diskLayoutIsTenantPrefixed() {
        ShImageTransformCache cache = new ShImageTransformCache(props(true));
        ShTenantContext.setTenantId("alpha");
        Resource src = res("a.png", "x".getBytes(StandardCharsets.UTF_8), 1L);
        String key = cache.keyFor(src, new ShImageTransformSpec(10, null, null, null));
        cache.store(key, new ShImageTransformResult("y".getBytes(StandardCharsets.UTF_8), "image/png"));

        Path tenantDir = tempDir.resolve("alpha");
        assertTrue(java.nio.file.Files.isDirectory(tenantDir), "entries live under <root>/<tenant>/");
        assertTrue(java.nio.file.Files.isRegularFile(tenantDir.resolve(key)), "bytes file present");
        assertTrue(java.nio.file.Files.isRegularFile(tenantDir.resolve(key + ".ct")), "content-type sidecar present");
    }
}
