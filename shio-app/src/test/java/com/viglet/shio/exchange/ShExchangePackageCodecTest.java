/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.exchange.model.ShExchangeFile;
import com.viglet.shio.exchange.model.ShExchangeFolder;
import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.exchange.model.ShExchangePost;
import com.viglet.shio.exchange.model.ShExchangeSite;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The SH68 exchange package format codec ({@link ShExchangePackageCodec}). Pure
 * unit test — no Spring, no DB: it proves the on-disk format round-trips
 * losslessly (manifest scalars, the canonical {@link ShPostTypeDef}, the unified
 * post {@code data} JSON carried verbatim, and binary files), that a
 * single-wrapping-directory zip is unwrapped on read, and that zip-slip is
 * rejected.
 *
 * @author Alexandre Oliveira
 */
class ShExchangePackageCodecTest {

    private final ShExchangePackageCodec codec = new ShExchangePackageCodec();
    private final ObjectMapper json = new ObjectMapper();

    private ShExchangePackage samplePackage() {
        ObjectNode data = json.createObjectNode();
        data.put("title", "Hello");
        data.put("body", "<p>World</p>");
        data.putArray("tags").add("a").add("b");

        return new ShExchangePackage(0, "2026-06-07T00:00:00Z", "shio-test",
                List.of(new ShExchangeSite("site-1", "Sample", "desc", "http://example.test",
                        "sample", "{\"News\":\"layout\"}", "News", null, "admin",
                        List.of("folder-root"))),
                List.of(new ShPostTypeDef("News", "News", "News", "the news", null, false,
                        List.of(new ShPostTypeAttrDef("title", "Title", null, "Text",
                                true, true, false, 0, null, List.of())))),
                List.of(new ShExchangeFolder("folder-root", "Sample", "/", true, "site-1", null, 0, "admin"),
                        new ShExchangeFolder("folder-news", "News", "/news", false, null, "folder-root", 1, "admin")),
                List.of(new ShExchangePost("post-1", "folder-news", "News", "DRAFT", "Hello",
                        "summary", "/news/hello", "admin", false, "DRAFT", 0, data)),
                List.of(new ShExchangeFile("file-1", "logo.png", "image/png")));
    }

    @Test
    void manifestRoundTripsLosslessly() {
        ShExchangePackage pkg = samplePackage();

        String exportJson = codec.writeManifest(pkg);
        ShExchangePackage parsed = codec.readManifest(exportJson);

        // Records (incl. the nested ShPostTypeDef) + JsonNode have value equality.
        assertThat(parsed).isEqualTo(pkg);
        // The unified data document survived verbatim.
        assertThat(parsed.posts().get(0).data().get("body").asString()).isEqualTo("<p>World</p>");
        assertThat(parsed.posts().get(0).data().get("tags")).hasSize(2);
        // Pretty-printed (human-diffable) manifest.
        assertThat(exportJson).contains("\n");
    }

    @Test
    void zipRoundTripPreservesManifestAndFiles(@TempDir Path tmp) {
        ShExchangePackage pkg = samplePackage();
        Path stage = tmp.resolve("stage");
        codec.writePackage(pkg, stage);

        byte[] fileBytes = "PNG-BYTES".getBytes(StandardCharsets.UTF_8);
        Path filePath = codec.resolveFile(stage, "file-1");
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, fileBytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        codec.zip(stage, zipped);

        Path dest = tmp.resolve("dest");
        Path root = codec.unzip(new ByteArrayInputStream(zipped.toByteArray()), dest);

        ShExchangePackage parsed = codec.readPackage(root);
        assertThat(parsed).isEqualTo(pkg);

        try {
            assertThat(Files.readAllBytes(codec.resolveFile(root, "file-1"))).isEqualTo(fileBytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void unzipUnwrapsSingleWrappingDirectory(@TempDir Path tmp) {
        // Build a package nested one level deep, the shape a "Download ZIP" produces.
        Path parent = tmp.resolve("parent");
        Path inner = parent.resolve("bootstrap-site");
        codec.writePackage(samplePackage(), inner);

        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        codec.zip(parent, zipped);

        Path dest = tmp.resolve("dest");
        Path root = codec.unzip(new ByteArrayInputStream(zipped.toByteArray()), dest);

        // unzip returns the wrapping sub-directory that actually holds export.json.
        assertThat(root.getFileName()).hasToString("bootstrap-site");
        assertThat(codec.readPackage(root).sites()).hasSize(1);
    }

    @Test
    void unzipRejectsZipSlip(@TempDir Path tmp) throws Exception {
        ByteArrayOutputStream evil = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(evil)) {
            zos.putNextEntry(new ZipEntry("../escaped.txt"));
            zos.write("pwned".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        Path dest = tmp.resolve("dest");
        var evilZip = new ByteArrayInputStream(evil.toByteArray());
        assertThatExceptionOfType(ShExchangeException.class)
                .isThrownBy(() -> codec.unzip(evilZip, dest));
    }

    @Test
    void normalizesVersionAndNullCollections() {
        ShExchangePackage pkg = new ShExchangePackage(0, null, null, null, null, null, null, null);

        assertThat(pkg.version()).isEqualTo(ShExchangePackage.CURRENT_VERSION);
        assertThat(pkg.sites()).isEmpty();
        assertThat(pkg.postTypes()).isEmpty();
        assertThat(pkg.folders()).isEmpty();
        assertThat(pkg.posts()).isEmpty();
        assertThat(pkg.files()).isEmpty();
    }
}
