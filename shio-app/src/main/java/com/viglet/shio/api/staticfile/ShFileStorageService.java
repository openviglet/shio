/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.viglet.shio.property.ShFileSourceProperties;
import com.viglet.shio.tenant.ShTenantContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Physical storage for the file_source subsystem (SH38). Bytes live on disk at
 * {@code <root>/<tenant>/<fileId>/<fileName>}; the database only keeps a
 * {@code File} post pointing at them (its id <em>is</em> the {@code fileId}).
 *
 * <p><strong>Tenant-aware (SH47)</strong> — every lookup is rooted at the current
 * {@link ShTenantContext} tenant (default {@code shio}) sub-directory, so tenant A
 * and B never share a stored file even for an identical post id, mirroring the
 * {@code ShImageTransformCache} layout. Bytes written before SH47 live in the old
 * flat {@code <root>/<fileId>/<fileName>} layout; reads (and deletes) fall back to
 * it when the tenant-scoped path is absent, so no bulk migration is required —
 * new writes always go to the tenant path.</p>
 *
 * <p>All public lookups go through {@link #resolve}, which normalizes the
 * candidate path and rejects anything that would escape the configured root —
 * the single guard against {@code ../} path-traversal coming from a file name
 * or id.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Slf4j
public class ShFileStorageService {

    private final ShFileSourceProperties properties;

    public ShFileStorageService(ShFileSourceProperties properties) {
        this.properties = properties;
    }

    /** The on-disk root directory for stored binaries. */
    public Path rootDir() {
        return properties.getRootDir();
    }

    /**
     * Bytes currently on disk for {@code tenant} — the recount SH45's meter reconciles against.
     *
     * <p>Deliberately not on the quota path: this is the O(files) walk the maintained counter
     * exists to avoid, and it is here so the truth stays recoverable rather than so it can be
     * asked per upload. SH47's per-tenant directory is what makes it answerable at all.</p>
     */
    public long bytesOnDiskFor(String tenant) {
        Path root = rootDir().resolve(sanitizeTenant(tenant));
        if (!Files.isDirectory(root)) {
            return 0L;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException unreadable) {
                    // A file that cannot be sized is not a reason to refuse an answer about the
                    // rest: the reconcile is a correction, and a partial correction beats none.
                    log.warn("SH45: cannot size {} while reconciling storage", file, unreadable);
                    return 0L;
                }
            }).sum();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk storage for tenant " + tenant, e);
        }
    }

    /** The current tenant's storage sub-directory, {@code <root>/<tenant>}. */
    private Path tenantRoot() {
        return rootDir().resolve(sanitizeTenant(tenantId()));
    }

    /** The current tenant, defaulting to {@code shio} when unset (matches the resolver). */
    private static String tenantId() {
        String tenant = ShTenantContext.getTenantId();
        return tenant != null && !tenant.isBlank() ? tenant : ShTenantContext.DEFAULT_TENANT;
    }

    /** Reduce a tenant id to a safe single path segment for the disk sub-directory. */
    private static String sanitizeTenant(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")
                ? "_" : cleaned.toLowerCase(Locale.ROOT);
    }

    /**
     * Reduce a user-supplied name to a safe, single path segment: strip any
     * directory components and reject empties / traversal tokens.
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name must not be blank");
        }
        // Keep only the last segment regardless of slash flavour.
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }
        return name;
    }

    /**
     * Resolve the absolute path of {@code fileName} within {@code fileId}'s
     * directory under the current tenant, guaranteed to stay under the storage
     * root. New writes target {@code <root>/<tenant>/<fileId>/<fileName>}; a read
     * of a pre-SH47 file falls back to the flat {@code <root>/<fileId>/<fileName>}
     * layout when the tenant-scoped path holds no regular file.
     */
    public Path resolve(String fileId, String fileName) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("File id must not be blank");
        }
        Path root = rootDir();
        Path tenantRoot = tenantRoot();
        String safeName = sanitizeFileName(fileName);
        Path candidate = tenantRoot.resolve(fileId).resolve(safeName).normalize();
        // Guard against the tenant segment escaping (e.g. a "../" file id): the
        // candidate must stay under the tenant sub-directory, not merely the root.
        if (!candidate.startsWith(tenantRoot)) {
            throw new IllegalArgumentException("Resolved path escapes file_source root");
        }
        if (!Files.isRegularFile(candidate)) {
            Path legacy = root.resolve(fileId).resolve(safeName).normalize();
            if (legacy.startsWith(root) && Files.isRegularFile(legacy)) {
                return legacy;
            }
        }
        return candidate;
    }

    /**
     * The directory holding a single file id's bytes for the current tenant
     * ({@code <root>/<tenant>/<fileId>}), falling back to the pre-SH47 flat
     * {@code <root>/<fileId>} directory when only that exists.
     */
    public Path dirOf(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("File id must not be blank");
        }
        Path root = rootDir();
        Path tenantRoot = tenantRoot();
        Path dir = tenantRoot.resolve(fileId).normalize();
        if (!dir.startsWith(tenantRoot)) {
            throw new IllegalArgumentException("Resolved path escapes file_source root");
        }
        if (!Files.isDirectory(dir)) {
            Path legacy = root.resolve(fileId).normalize();
            if (legacy.startsWith(root) && Files.isDirectory(legacy)) {
                return legacy;
            }
        }
        return dir;
    }

    /**
     * Persist {@code content} as {@code fileName} under {@code fileId}, creating
     * the directory if needed and overwriting any existing file of the same name.
     *
     * @return the absolute path the bytes were written to
     */
    public Path store(String fileId, String fileName, InputStream content) {
        Path target = resolve(fileId, fileName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file " + fileName + " for " + fileId, e);
        }
    }

    /** Whether bytes named {@code fileName} exist for {@code fileId}. */
    public boolean exists(String fileId, String fileName) {
        return Files.isRegularFile(resolve(fileId, fileName));
    }

    /**
     * Best-effort recursive removal of a file id's directory. Never throws — a
     * missing directory (e.g. a non-file post being deleted) is a no-op. Removes
     * both the current tenant's {@code <root>/<tenant>/<fileId>} directory and any
     * pre-SH47 flat {@code <root>/<fileId>} directory.
     */
    public void delete(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        Path root = rootDir();
        deleteDirRecursive(tenantRoot().resolve(fileId).normalize(), root);
        deleteDirRecursive(root.resolve(fileId).normalize(), root);
    }

    /**
     * Best-effort recursive removal of a whole tenant's storage sub-directory
     * ({@code <root>/<tenant>}), used by tenant teardown (SH47/B). Never throws.
     */
    public void deleteTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        Path root = rootDir();
        deleteDirRecursive(root.resolve(sanitizeTenant(tenantId)).normalize(), root);
    }

    /** Recursively delete {@code dir} when it is a directory under {@code root}; never throws. */
    private void deleteDirRecursive(Path dir, Path root) {
        if (!dir.startsWith(root) || dir.equals(root) || !Files.isDirectory(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Could not delete {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not walk file_source dir {}", dir, e);
        }
    }

    /**
     * Best-effort content type for a stored file, derived from its name then its
     * bytes; falls back to {@code application/octet-stream}.
     */
    public String probeContentType(Path path) {
        String byName = URLConnection.guessContentTypeFromName(path.getFileName().toString());
        if (byName != null) {
            return byName;
        }
        try {
            String probed = Files.probeContentType(path);
            if (probed != null) {
                return probed;
            }
        } catch (IOException e) {
            log.debug("Could not probe content type for {}", path, e);
        }
        return "application/octet-stream";
    }
}
