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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.viglet.shio.property.ShFileSourceProperties;
import com.viglet.shio.tenant.ShTenantContext;
import com.viglet.shio.tenant.ShTenantStorageMeter;

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
 * <h2>It also moves SH45's storage meter (SH751)</h2>
 *
 * <p>Every byte this instance stores passes through {@link #store}, {@link #copy},
 * {@link #delete} or {@link #deleteTenant} — upload, replace, the SH749 copy, the cleanup
 * listener and tenant teardown are all callers of those four. So the delta is recorded
 * <b>here</b>, where the write happens, rather than in each service above: SH751 exists because
 * three of those call sites were supposed to remember and none did, and a fourth arrived with
 * SH749 while the rule was still unwritten.</p>
 *
 * <p>The rule that keeps the meter honest is that <b>a delta is recorded exactly when
 * {@link #bytesOnDiskFor} would have counted it</b> — under {@code <root>/<tenant>}, never the
 * pre-SH47 flat layout. That is not a detail: {@code reconcile} recounts from the tenant subtree,
 * so metering a legacy write would make every reconcile move the figure, and a correction that
 * always corrects is indistinguishable from drift.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Slf4j
public class ShFileStorageService {

    private final ShFileSourceProperties properties;

    /**
     * SH45's meter, reached through a provider because the dependency is a <b>cycle</b>:
     * {@code ShTenantStorageMeter.reconcile} calls {@link #bytesOnDiskFor} on this class. Spring
     * refuses a constructor cycle outright, and the two directions are genuinely different — this
     * one is "bytes moved", that one is "recount from disk" — so the lazy edge is the honest way
     * to say which is the derived relationship.
     */
    private final ObjectProvider<ShTenantStorageMeter> storageMeter;

    public ShFileStorageService(ShFileSourceProperties properties,
                                ObjectProvider<ShTenantStorageMeter> storageMeter) {
        this.properties = properties;
        this.storageMeter = storageMeter;
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
        return sumBytes(rootDir().resolve(sanitizeTenant(tenant)));
    }

    /**
     * Bytes held under {@code dir}, or {@code 0} when it holds none — the one reading both the
     * recount and every {@link #record} delta are computed from, so the two cannot disagree.
     */
    private long sumBytes(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0L;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
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
            throw new UncheckedIOException("cannot walk storage under " + dir, e);
        }
    }

    /** The size of one file, or {@code 0} when it is absent or unreadable. */
    private static long sizeOf(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0L;
        } catch (IOException unreadable) {
            return 0L;
        }
    }

    /**
     * Move SH45's meter by {@code delta}, but only for a path the recount would have counted
     * (SH751).
     *
     * <p>The guard is the whole contract: {@code reconcile} sums {@code <root>/<tenant>}, so a
     * write that {@link #resolve} sent to the pre-SH47 flat layout must not be metered. Meter it
     * and every reconcile would move the figure — a correction that always corrects, which reads
     * exactly like the drift it exists to remove.</p>
     *
     * <p>Never throws. Storage succeeding and accounting failing must not be reported to the
     * caller as a failed upload; the meter has {@code reconcile} for its own repair, and the
     * bytes are on disk either way.</p>
     */
    private void recordDelta(Path target, long delta) {
        if (delta == 0 || !target.startsWith(tenantRoot())) {
            return;
        }
        try {
            ShTenantStorageMeter meter = meter();
            if (meter != null) {
                meter.record(tenantId(), delta);
            }
        } catch (RuntimeException e) {
            log.warn("SH751: stored the bytes but could not move the meter by {}: {}",
                    delta, e.getMessage());
        }
    }

    /**
     * The meter, or {@code null} where there is none — a plain unit test of the storage layer
     * constructs this class without one, and storage is a subsystem that has to work when nobody
     * is counting.
     */
    private ShTenantStorageMeter meter() {
        return storageMeter == null ? null : storageMeter.getIfAvailable();
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

    /**
     * The current tenant as a single safe path segment (SH1035).
     *
     * <p>Public so the rendition store scopes its directories the same way this one does. A second
     * copy of {@code sanitizeTenant(tenantId())} would be SH245's smell in the place it matters
     * most: the two would agree until a tenant id turned up with a character one of them folded
     * and the other did not, and the superseded bytes would then be written where nothing looks
     * for them.</p>
     */
    public static String tenantSegment() {
        return sanitizeTenant(tenantId());
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
        // Before and after, not "the size written": REPLACE_EXISTING means a replace of the same
        // name adds only the difference, and SH751's acceptance test is that a reconcile after a
        // mixed run changes nothing. Counting the new size on a replace would fail it every time.
        long before = sizeOf(target);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            recordDelta(target, sizeOf(target) - before);
            return target;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file " + fileName + " for " + fileId, e);
        }
    }

    /**
     * How many bytes are stored under {@code fileId} as {@code fileName}, or {@code 0} (SH110).
     *
     * <p>What a <em>replace</em> needs, and the reason it cannot just ask the quota about the new
     * file's size: replacing a 900 MB video with a 1 MB one frees 899 MB, and charging the plan for
     * the new size would refuse a write that makes the tenant smaller.</p>
     */
    public long storedBytes(String fileId, String fileName) {
        return sizeOf(resolve(fileId, fileName));
    }

    /** Whether bytes named {@code fileName} exist for {@code fileId}. */
    public boolean exists(String fileId, String fileName) {
        return Files.isRegularFile(resolve(fileId, fileName));
    }

    /**
     * Duplicate every byte stored under {@code fromFileId} into {@code toFileId} (SH749).
     *
     * <h2>Why the whole directory, and why no post-type check</h2>
     *
     * <p>A copy of a post gets a fresh UUID, and this subsystem is keyed by that id — so before
     * this method a copied {@code File} post pointed at a directory nobody had written, and its
     * bytes 404'd while every surface reported success. The caller does not ask "is this a
     * {@code File} post": it asks whether the source id <em>has</em> a directory, which is the same
     * question one syscall cheaper and cannot disagree with the storage layout. An ordinary post
     * has none and this is a no-op.</p>
     *
     * <p>The whole directory rather than the name in {@code data.FILE}: what a copy owes is the
     * bytes that are there, and reading the name out of JSON would copy what the row <em>claims</em>
     * instead. It writes to the tenant path only — {@link #dirOf} may hand back a pre-SH47 flat
     * source, and a new write goes where new writes go.</p>
     *
     * <p><b>Copied, not shared.</b> Pointing the copy at the source's directory is cheaper and
     * wrong the first time either is purged: {@code ShStaticFileCleanupListener} frees a directory
     * on {@code DELETE}, so deleting one asset would blank the other. Bytes are what a copy is.</p>
     *
     * @return how many bytes were written; {@code 0} when the source stores nothing
     */
    public long copy(String fromFileId, String toFileId) {
        if (fromFileId == null || fromFileId.isBlank() || toFileId == null || toFileId.isBlank()
                || fromFileId.equals(toFileId)) {
            return 0L;
        }
        Path source = dirOf(fromFileId);
        if (!Files.isDirectory(source)) {
            return 0L;
        }
        Path tenantRoot = tenantRoot();
        Path target = tenantRoot.resolve(toFileId).normalize();
        if (!target.startsWith(tenantRoot)) {
            throw new IllegalArgumentException("Resolved path escapes file_source root");
        }
        long before = sumBytes(target);
        try (java.util.stream.Stream<Path> walk = Files.walk(source)) {
            Files.createDirectories(target);
            long written = 0L;
            for (Path from : walk.filter(Files::isRegularFile).toList()) {
                Path to = target.resolve(source.relativize(from).toString()).normalize();
                if (!to.startsWith(target)) {
                    continue;
                }
                Files.createDirectories(to.getParent());
                Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                written += Files.size(to);
            }
            // The delta is what the directory GAINED, which is not `written` when a name was
            // overwritten. The return value stays "bytes written" — that is what SH749's caller
            // reports — and the two are the same number for the fresh id a copy normally targets.
            recordDelta(target, sumBytes(target) - before);
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to copy stored bytes " + fromFileId + " -> " + toFileId, e);
        }
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
        Path owned = tenantRoot().resolve(fileId).normalize();
        // Measured before the removal, because afterwards there is nothing left to ask. Only the
        // tenant-scoped directory: the pre-SH47 flat one below was never counted by the recount,
        // so subtracting it would drive the meter under what the disk actually holds.
        long freed = sumBytes(owned);
        deleteDirRecursive(owned, root);
        deleteDirRecursive(root.resolve(fileId).normalize(), root);
        recordDelta(owned, -freed);
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
        // Zeroed by name rather than through recordDelta: teardown names the tenant it is purging
        // and is not necessarily running as it. The record is a no-op once the catalog row is gone,
        // so this only matters when the row outlives the bytes — a purge that keeps the tenant —
        // and that is exactly the case where a meter still claiming the freed bytes would refuse
        // uploads nobody made.
        long freed = bytesOnDiskFor(tenantId);
        deleteDirRecursive(root.resolve(sanitizeTenant(tenantId)).normalize(), root);
        if (freed > 0) {
            try {
                ShTenantStorageMeter meter = meter();
                if (meter != null) {
                    meter.record(tenantId, -freed);
                }
            } catch (RuntimeException e) {
                log.warn("SH751: purged {} but could not zero its meter: {}", tenantId, e.getMessage());
            }
        }
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
        // SH950: the JDK's table, then the handful of extensions it does not know. Since assets
        // are now served with `nosniff`, a type this gets wrong is no longer sniffed past — a
        // `.mjs` answered as text/plain is a module the browser refuses to execute.
        String byName = ShAssetResponseHeaders.contentTypeByName(path.getFileName().toString());
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
        return ShAssetResponseHeaders.FALLBACK_CONTENT_TYPE;
    }
}
