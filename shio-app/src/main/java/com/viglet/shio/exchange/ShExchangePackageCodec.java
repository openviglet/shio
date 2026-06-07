/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import com.viglet.shio.exchange.model.ShExchangePackage;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * Reads and writes the on-disk form of a content exchange package (SH68): an
 * {@code export.json} manifest ({@link ShExchangePackage}) plus a {@code files/}
 * directory of binary assets, packed as a zip. This is the format <em>plumbing</em>
 * only — it does no entity ↔ DTO mapping, tree walking, or id remapping. The
 * export ({@code SH69}) builds the {@link ShExchangePackage} and supplies file
 * bytes; the import / clone ({@code SH70}) consumes them.
 *
 * <p>On-disk layout (inside the zip):</p>
 * <pre>
 *   export.json          ← the {@link ShExchangePackage} manifest
 *   files/&lt;fileId&gt;       ← one entry per {@code ShExchangeFile}, named by id
 * </pre>
 *
 * <p>The manifest is pretty-printed for human-diffable, GitOps-friendly packages
 * (the same instinct behind the SH16 {@code pull}/{@code push} JSON). Reads are
 * tolerant of a zip that wraps everything in a single top-level directory (the
 * shape a GitHub "Download ZIP" produces), and guard against zip-slip.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
public class ShExchangePackageCodec {

    /** The manifest entry name at the package root. */
    public static final String MANIFEST_ENTRY = "export.json";
    /** The directory holding binary assets, relative to the package root. */
    public static final String FILES_DIR = "files";

    private final ObjectMapper mapper = new ObjectMapper().rebuild()
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .build();

    // ------------------------------------------------------------------
    // Manifest (JSON) serialization
    // ------------------------------------------------------------------

    /** Serialize a package manifest to its pretty-printed {@code export.json} string. */
    public String writeManifest(ShExchangePackage pkg) {
        try {
            return mapper.writeValueAsString(pkg);
        } catch (RuntimeException e) {
            throw new ShExchangeException("Failed to serialize exchange manifest", e);
        }
    }

    /** Parse a package manifest from an {@code export.json} string. */
    public ShExchangePackage readManifest(String json) {
        try {
            return mapper.readValue(json, ShExchangePackage.class);
        } catch (RuntimeException e) {
            throw new ShExchangeException("Failed to parse exchange manifest", e);
        }
    }

    /** Parse a package manifest from an {@code export.json} stream. */
    public ShExchangePackage readManifest(InputStream in) {
        try {
            return mapper.readValue(in, ShExchangePackage.class);
        } catch (RuntimeException e) {
            throw new ShExchangeException("Failed to parse exchange manifest", e);
        }
    }

    // ------------------------------------------------------------------
    // Directory layout
    // ------------------------------------------------------------------

    /** Write {@code dir/export.json}, creating {@code dir} if needed. File bytes are the caller's job. */
    public void writePackage(ShExchangePackage pkg, Path dir) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(MANIFEST_ENTRY), writeManifest(pkg), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ShExchangeException("Failed to write package to " + dir, e);
        }
    }

    /** Read {@code dir/export.json} into a {@link ShExchangePackage}. */
    public ShExchangePackage readPackage(Path dir) {
        try (InputStream in = Files.newInputStream(dir.resolve(MANIFEST_ENTRY))) {
            return readManifest(in);
        } catch (IOException e) {
            throw new ShExchangeException("Failed to read package from " + dir, e);
        }
    }

    /** Resolve the on-disk path of a binary asset: {@code dir/files/<fileId>}. */
    public Path resolveFile(Path dir, String fileId) {
        return dir.resolve(FILES_DIR).resolve(fileId);
    }

    // ------------------------------------------------------------------
    // Zip
    // ------------------------------------------------------------------

    /** Zip the entire {@code sourceDir} tree (manifest + {@code files/}) into {@code out}. */
    public void zip(Path sourceDir, OutputStream out) {
        try (ZipOutputStream zos = new ZipOutputStream(out);
             Stream<Path> paths = Files.walk(sourceDir)) {
            // Sorted for deterministic, diff-stable archives.
            List<Path> entries = paths.filter(Files::isRegularFile).sorted().toList();
            for (Path file : entries) {
                String name = sourceDir.relativize(file).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(name));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new ShExchangeException("Failed to zip package", e);
        }
    }

    /**
     * Extract a package zip into {@code destDir} and return the directory that
     * actually holds {@code export.json} — {@code destDir} itself, or the single
     * wrapping sub-directory if the zip was nested.
     */
    public Path unzip(InputStream zip, Path destDir) {
        try {
            Files.createDirectories(destDir);
            Path root = destDir.normalize();
            try (ZipInputStream zis = new ZipInputStream(zip)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path target = root.resolve(entry.getName()).normalize();
                    if (!target.startsWith(root)) {
                        throw new ShExchangeException("Zip entry escapes target directory: " + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }
            return locateManifestRoot(root);
        } catch (IOException e) {
            throw new ShExchangeException("Failed to unzip package", e);
        }
    }

    /** {@code destDir}, or its single sub-directory containing the manifest (wrapped-zip case). */
    private static Path locateManifestRoot(Path destDir) throws IOException {
        if (Files.exists(destDir.resolve(MANIFEST_ENTRY))) {
            return destDir;
        }
        try (Stream<Path> children = Files.list(destDir)) {
            return children.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve(MANIFEST_ENTRY)))
                    .findFirst()
                    .orElse(destDir);
        }
    }
}
