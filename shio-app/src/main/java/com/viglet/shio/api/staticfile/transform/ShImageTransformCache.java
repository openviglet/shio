/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.viglet.shio.property.ShImageCacheProperties;
import com.viglet.shio.tenant.ShTenantContext;

import lombok.extern.slf4j.Slf4j;

/**
 * The SH42 result store behind {@link ShCachingImageTransformPipeline}: a bounded
 * in-memory LRU in front of a disk cache, keyed by {@code (tenant, source identity,
 * transform params)} and stamped with a stable ETag.
 *
 * <p><strong>Key</strong> — SHA-256 over
 * {@code tenant | filename | lastModified | length | w | h | format | crop}. The
 * source identity is the weak-validator triple (name + mtime + size), so a re-upload
 * that changes the bytes lands on a fresh key (old entries are simply orphaned; disk
 * pruning is an ops concern, not done here). The hex digest doubles as the ETag and
 * as the on-disk file name.</p>
 *
 * <p><strong>Tenant-aware (SH22)</strong> — the current {@link ShTenantContext}
 * tenant (default {@code shio}) is the first key component <em>and</em> the disk
 * sub-directory ({@code <root>/<tenant>/<hash>}), so tenant A and B never share a
 * cached entry or an ETag even for an identical source path + params.</p>
 *
 * <p>Disk persistence is best-effort: an I/O failure on read or write degrades to a
 * miss / no-store and is logged, never thrown — a broken cache must not break
 * delivery. Each entry is two files: {@code <hash>} (bytes) and {@code <hash>.ct}
 * (content type); a half-written pair (either missing) reads as a miss.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Slf4j
public class ShImageTransformCache {

    private static final String CONTENT_TYPE_SUFFIX = ".ct";

    private final ShImageCacheProperties properties;

    /** Access-ordered, size-bounded LRU; values already carry their ETag. */
    private final Map<String, ShImageTransformResult> memory;

    public ShImageTransformCache(ShImageCacheProperties properties) {
        this.properties = properties;
        int cap = Math.max(1, properties.getMaxMemoryEntries());
        this.memory = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ShImageTransformResult> eldest) {
                return size() > cap;
            }
        });
    }

    /** Whether caching is active (mirrors {@code shio.image-cache.enabled}). */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * The stable cache key / ETag value for {@code source} under {@code spec}: a hex
     * SHA-256 over the current tenant, the source's weak validators, and the params.
     */
    public String keyFor(Resource source, ShImageTransformSpec spec) {
        String raw = String.join("|",
                tenantId(),
                String.valueOf(source.getFilename()),
                String.valueOf(safeLastModified(source)),
                String.valueOf(safeContentLength(source)),
                String.valueOf(spec.width()),
                String.valueOf(spec.height()),
                String.valueOf(spec.format()),
                String.valueOf(spec.crop()));
        return sha256Hex(raw);
    }

    /** Memory-then-disk lookup; an entry is returned with its ETag already set. */
    public Optional<ShImageTransformResult> lookup(String key) {
        ShImageTransformResult hit = memory.get(key);
        if (hit != null) {
            return Optional.of(hit);
        }
        return readDisk(key).map(r -> {
            memory.put(key, r);
            return r;
        });
    }

    /**
     * Store {@code result} under {@code key} in memory and on disk, returning a copy
     * stamped with the key as its ETag (the value callers serve).
     */
    public ShImageTransformResult store(String key, ShImageTransformResult result) {
        ShImageTransformResult stamped = result.withEtag(key);
        memory.put(key, stamped);
        writeDisk(key, stamped);
        return stamped;
    }

    private Optional<ShImageTransformResult> readDisk(String key) {
        Path bytesPath = entryPath(key);
        Path ctPath = entryPath(key + CONTENT_TYPE_SUFFIX);
        if (bytesPath == null || ctPath == null
                || !Files.isRegularFile(bytesPath) || !Files.isRegularFile(ctPath)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(bytesPath);
            String contentType = Files.readString(ctPath, StandardCharsets.UTF_8).trim();
            return Optional.of(new ShImageTransformResult(bytes, contentType, key));
        } catch (IOException e) {
            log.debug("Image cache disk read failed for {}; treating as miss", key, e);
            return Optional.empty();
        }
    }

    private void writeDisk(String key, ShImageTransformResult result) {
        Path bytesPath = entryPath(key);
        Path ctPath = entryPath(key + CONTENT_TYPE_SUFFIX);
        if (bytesPath == null || ctPath == null) {
            return;
        }
        try {
            Files.createDirectories(bytesPath.getParent());
            Files.write(bytesPath, result.bytes());
            Files.writeString(ctPath, result.contentType() != null ? result.contentType() : "",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Image cache disk write failed for {}; serving without persisting", key, e);
        }
    }

    /** Resolve {@code <root>/<tenant>/<name>}, guarding against escaping the root. */
    private Path entryPath(String name) {
        Path root = properties.getRootDir();
        Path candidate = root.resolve(sanitize(tenantId())).resolve(name).normalize();
        if (!candidate.startsWith(root)) {
            log.warn("Image cache path escapes root for {}; skipping disk layer", name);
            return null;
        }
        return candidate;
    }

    /** The current tenant, defaulting to {@code shio} when unset (matches the resolver). */
    private String tenantId() {
        String tenant = ShTenantContext.getTenantId();
        return tenant != null && !tenant.isBlank() ? tenant : ShTenantContext.DEFAULT_TENANT;
    }

    /** Reduce a tenant id to a safe single path segment for the disk sub-directory. */
    private static String sanitize(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")
                ? "_" : cleaned.toLowerCase(Locale.ROOT);
    }

    private static long safeLastModified(Resource source) {
        try {
            return source.lastModified();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long safeContentLength(Resource source) {
        try {
            return source.contentLength();
        } catch (IOException e) {
            return -1L;
        }
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; this is unreachable.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
