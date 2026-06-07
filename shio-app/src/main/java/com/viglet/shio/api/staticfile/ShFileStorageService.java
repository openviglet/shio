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

import org.springframework.stereotype.Service;

import com.viglet.shio.property.ShFileSourceProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Physical storage for the file_source subsystem (SH38). Bytes live on disk at
 * {@code <root>/<fileId>/<fileName>}; the database only keeps a {@code File}
 * post pointing at them (its id <em>is</em> the {@code fileId}).
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
     * directory, guaranteed to stay under the storage root.
     */
    public Path resolve(String fileId, String fileName) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("File id must not be blank");
        }
        Path root = rootDir();
        Path candidate = root.resolve(fileId).resolve(sanitizeFileName(fileName)).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Resolved path escapes file_source root");
        }
        return candidate;
    }

    /** The directory holding a single file id's bytes. */
    public Path dirOf(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("File id must not be blank");
        }
        Path root = rootDir();
        Path dir = root.resolve(fileId).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("Resolved path escapes file_source root");
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
     * missing directory (e.g. a non-file post being deleted) is a no-op.
     */
    public void delete(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return;
        }
        Path dir;
        try {
            dir = dirOf(fileId);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!Files.isDirectory(dir)) {
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
            log.warn("Could not walk file_source dir for {}", fileId, e);
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
