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
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.viglet.shio.property.ShAssetRenditionProperties;
import com.viglet.shio.utils.ShIoFailure;

import lombok.extern.slf4j.Slf4j;

/**
 * The bytes an asset used to have, so a replace can be undone (SH1035).
 *
 * <h2>What this is for</h2>
 *
 * <p>§LXX.7's argument is that the copy is what makes replacing <b>safe enough to encourage</b>.
 * The console's route to new bytes was delete-and-re-upload, which mints a new id and breaks every
 * field pointing at the old one; {@code replace} fixes that and introduces a different fear, which
 * is that the bytes it overwrites are simply gone. A curator who cannot undo a replace will not
 * use it, and will go back to the one that breaks references.</p>
 *
 * <h2>Where they are kept, which is the one decision with a security edge</h2>
 *
 * <p>Not under the asset's own directory. {@code /file_source/**} is mapped at the root of that
 * tree, so a rendition stored beside the live bytes would be <b>publicly downloadable</b> by
 * anyone who guessed the path — a superseded document says whatever it was replaced for saying.
 * These live under {@code shio.asset-renditions.path}, a sibling with no resource handler over it,
 * reached only through the authenticated console API.</p>
 *
 * <h2>A revert captures first</h2>
 *
 * <p>{@link #revert} supersedes the current bytes into a new rendition before restoring an old
 * one, so going back is itself reversible. Without that, a curator who reverts to the wrong
 * version has destroyed the one they were trying to keep — the exact failure this class exists to
 * remove, reintroduced by its own undo.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Slf4j
public class ShAssetRenditionStore {

    private final ShAssetRenditionProperties properties;
    private final ShFileStorageService storage;

    public ShAssetRenditionStore(ShAssetRenditionProperties properties,
            ShFileStorageService storage) {
        this.properties = properties;
        this.storage = storage;
    }

    /** Whether this instance keeps superseded bytes at all. */
    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * Copies the asset's current bytes aside, returning the rendition that now holds them.
     *
     * <p>Empty when the feature is off, or when there are no bytes to keep — a replace on a post
     * whose file is missing from disk supersedes nothing, and inventing an empty rendition for it
     * would offer a revert that restores zero bytes.</p>
     */
    public Optional<ShAssetRendition> capture(String fileId, String fileName) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        Path current = storage.resolve(fileId, fileName);
        if (!Files.isRegularFile(current)) {
            return Optional.empty();
        }
        int number = nextNumber(fileId);
        Path target = renditionDir(fileId).resolve(String.valueOf(number))
                .resolve(ShFileStorageService.sanitizeFileName(fileName));
        try {
            Files.createDirectories(target.getParent());
            Files.copy(current, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // A failed capture must not fail the replace: the curator asked for new bytes, and
            // refusing the write because the safety net could not be written would make the
            // feature less reliable than the one it replaces. Logged, and the response says how
            // many renditions exist, so a caller can see that this one is not among them.
            log.warn("SH1035 could not keep the superseded bytes of {} ({}): {}",
                    fileId, fileName, ShIoFailure.describe(e));
            return Optional.empty();
        }
        prune(fileId);
        return read(target, number);
    }

    /** Every rendition this asset holds, newest first. */
    public List<ShAssetRendition> list(String fileId) {
        Path dir = renditionDir(fileId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<ShAssetRendition> renditions = new ArrayList<>();
        try (Stream<Path> numbered = Files.list(dir)) {
            numbered.filter(Files::isDirectory).forEach(entry -> numberOf(entry)
                    .ifPresent(number -> bytesIn(entry)
                            .flatMap(file -> read(file, number))
                            .ifPresent(renditions::add)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list renditions of " + fileId, e);
        }
        renditions.sort(Comparator.comparingInt(ShAssetRendition::number).reversed());
        return List.copyOf(renditions);
    }

    /** The file holding one rendition's bytes, if it is there. */
    public Optional<Path> bytes(String fileId, int number) {
        Path dir = renditionDir(fileId).resolve(String.valueOf(number));
        return Files.isDirectory(dir) ? bytesIn(dir) : Optional.empty();
    }

    /**
     * Puts rendition {@code number} back as the asset's live bytes.
     *
     * <p>The current bytes are captured first, so this is undoable in its own terms. Returns the
     * file name that was restored, which the caller needs in order to re-stamp the digest — the
     * name never changes on a replace, so it is the asset's own.</p>
     */
    public Optional<String> revert(String fileId, String fileName, int number) {
        Optional<Path> source = bytes(fileId, number);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        capture(fileId, fileName);
        try (InputStream restored = Files.newInputStream(source.get())) {
            storage.store(fileId, fileName, restored);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot restore rendition " + number + " of " + fileId, e);
        }
        return Optional.of(fileName);
    }

    /** Drops an asset's renditions entirely — what a purge of the asset itself leaves behind. */
    public void forget(String fileId) {
        Path dir = renditionDir(fileId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("SH1035 could not delete rendition path {}: {}", path, ShIoFailure.describe(e));
                }
            });
        } catch (IOException e) {
            log.warn("SH1035 could not walk renditions of {}: {}", fileId, ShIoFailure.describe(e));
        }
    }

    // --- disk ---

    /**
     * {@code <root>/<tenant>/<fileId>}, tenant-scoped the way the live bytes are.
     *
     * <p>The tenant segment comes from {@link ShFileStorageService#tenantSegment()} rather than
     * being derived here, so the two directories can never disagree about which tenant a string
     * names.</p>
     */
    private Path renditionDir(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("File id must not be blank");
        }
        Path root = properties.getRootDir().resolve(ShFileStorageService.tenantSegment());
        Path dir = root.resolve(fileId).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("Resolved rendition path escapes its root");
        }
        return dir;
    }

    private int nextNumber(String fileId) {
        return list(fileId).stream().mapToInt(ShAssetRendition::number).max().orElse(0) + 1;
    }

    /**
     * Keeps the newest {@code keep} renditions and deletes the rest.
     *
     * <p>Bounded because these bytes are outside the tenant meter (SH110's difference rule would
     * otherwise make a replace grow a tenant's usage), so the cost of the safety net has to be a
     * function of the asset's size rather than of how often somebody replaces it.</p>
     */
    private void prune(String fileId) {
        List<ShAssetRendition> held = list(fileId);
        int keep = Math.max(properties.getKeep(), 1);
        for (ShAssetRendition rendition : held.stream().skip(keep).toList()) {
            Path dir = renditionDir(fileId).resolve(String.valueOf(rendition.number()));
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        log.warn("SH1035 could not prune {}: {}", path, ShIoFailure.describe(e));
                    }
                });
            } catch (IOException e) {
                log.warn("SH1035 could not prune rendition {} of {}: {}",
                        rendition.number(), fileId, ShIoFailure.describe(e));
            }
        }
    }

    /** The single file inside a numbered directory. */
    private Optional<Path> bytesIn(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Optional<ShAssetRendition> read(Path file, int number) {
        try {
            return Optional.of(new ShAssetRendition(number, file.getFileName().toString(),
                    Files.size(file),
                    Files.getLastModifiedTime(file).toInstant().truncatedTo(
                            java.time.temporal.ChronoUnit.MILLIS)));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> numberOf(Path dir) {
        try {
            return Optional.of(Integer.parseInt(dir.getFileName().toString()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Where renditions live, for a diagnostic that has to say so. */
    public Path rootDir() {
        return properties.getRootDir();
    }

    /** The instance's ceiling on superseded copies per asset. */
    public int keep() {
        return Math.max(properties.getKeep(), 1);
    }

    /** Package-visible for the guard that asserts these bytes are not publicly reachable. */
    Path dirFor(String fileId) {
        return renditionDir(fileId);
    }
}
