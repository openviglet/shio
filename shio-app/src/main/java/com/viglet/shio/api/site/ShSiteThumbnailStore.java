/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.property.ShSiteThumbnailProperties;
import com.viglet.shio.utils.ShIoFailure;

import lombok.extern.slf4j.Slf4j;

/**
 * Each site's home-page picture on disk (SH1218): {@code <root>/<tenant>/<siteId>/image} and a
 * {@code meta.properties} naming the appearance digest it was taken at.
 *
 * <p>The digest is what makes re-publishing an unchanged home store nothing: a picture is replaced
 * only when the home's appearance hash moved. The meta file is written last, so a half-written pair
 * reads as no picture rather than as a picture with the wrong digest.</p>
 */
@Service
@Slf4j
public class ShSiteThumbnailStore {

    private static final String IMAGE = "image";
    private static final String META = "meta.properties";

    /** What is stored for one site. */
    public record Stored(String digest, String contentType, long size, Instant at) {
    }

    private final ShSiteThumbnailProperties properties;

    public ShSiteThumbnailStore(ShSiteThumbnailProperties properties) {
        this.properties = properties;
    }

    public int maxBytes() {
        return properties.getMaxBytes();
    }

    public Optional<Stored> read(String siteId) {
        return readMeta(siteDir(siteId));
    }

    /**
     * Every site in this tenant that holds a picture, keyed by site id — one directory listing,
     * so the sites listing does not stat a path per row it will not find.
     */
    public Map<String, Stored> all() {
        Path root = tenantRoot();
        Map<String, Stored> stored = new HashMap<>();
        if (!Files.isDirectory(root)) {
            return stored;
        }
        try (Stream<Path> sites = Files.list(root)) {
            sites.filter(Files::isDirectory).forEach(dir -> readMeta(dir)
                    .ifPresent(meta -> stored.put(dir.getFileName().toString(), meta)));
        } catch (IOException e) {
            log.warn("SH1218 could not list site pictures under {}: {}", root, ShIoFailure.describe(e));
        }
        return stored;
    }

    public Optional<Path> image(String siteId) {
        Path image = siteDir(siteId).resolve(IMAGE);
        return read(siteId).isPresent() && Files.isRegularFile(image) ? Optional.of(image) : Optional.empty();
    }

    public Stored write(String siteId, String digest, String contentType, byte[] bytes) {
        Path dir = siteDir(siteId);
        Instant at = Instant.now();
        try {
            Files.createDirectories(dir);
            Files.deleteIfExists(dir.resolve(META));
            Path partial = dir.resolve(IMAGE + ".partial");
            Files.write(partial, bytes);
            Files.move(partial, dir.resolve(IMAGE), StandardCopyOption.REPLACE_EXISTING);
            Properties meta = new Properties();
            meta.setProperty("digest", digest);
            meta.setProperty("contentType", contentType);
            meta.setProperty("size", String.valueOf(bytes.length));
            meta.setProperty("at", at.toString());
            try (OutputStream out = Files.newOutputStream(dir.resolve(META))) {
                meta.store(out, "SH1218 site home picture");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store the home picture of site " + siteId, e);
        }
        return new Stored(digest, contentType, bytes.length, at);
    }

    /** Drops a site's picture — what purging the site leaves behind. */
    public void forget(String siteId) {
        delete(siteDir(siteId));
    }

    /** Drops every picture of the current tenant — the tenant teardown's half. */
    public void purgeTenant() {
        delete(tenantRoot());
    }

    private void delete(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("SH1218 could not delete {}: {}", dir, ShIoFailure.describe(e));
        }
    }

    private Optional<Stored> readMeta(Path dir) {
        Path meta = dir.resolve(META);
        if (!Files.isRegularFile(meta) || !Files.isRegularFile(dir.resolve(IMAGE))) {
            return Optional.empty();
        }
        Properties values = new Properties();
        try (InputStream in = Files.newInputStream(meta)) {
            values.load(in);
            return Optional.of(new Stored(values.getProperty("digest"),
                    values.getProperty("contentType"),
                    Long.parseLong(values.getProperty("size", "0")),
                    Instant.parse(values.getProperty("at"))));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private Path tenantRoot() {
        return properties.getRootDir().resolve(ShFileStorageService.tenantSegment());
    }

    private Path siteDir(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalArgumentException("Site id must not be blank");
        }
        Path root = tenantRoot();
        Path dir = root.resolve(siteId).normalize();
        if (!dir.startsWith(root) || dir.equals(root)) {
            throw new IllegalArgumentException("Resolved picture path escapes its root");
        }
        return dir;
    }
}
