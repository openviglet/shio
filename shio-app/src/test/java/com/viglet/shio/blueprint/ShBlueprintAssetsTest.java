/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;
import com.viglet.shio.blueprint.dto.ShBlueprintManifest;

import tools.jackson.databind.node.JsonNodeFactory;

/**
 * SH1252 — a package carries its images, and applying it puts them in the site.
 *
 * <p>The showcase (SH1250) needs photography, and before this a package could only carry text: its
 * content would point at images nobody delivered. The loop asserted here is the whole contract: the
 * plan names each asset and writes nothing, the apply uploads them into folders the content engine
 * created, a re-apply of the same bytes is all {@code noop} (P5), and changed bytes are a
 * {@code replace} that keeps the post's id.</p>
 */
@SpringBootTest
class ShBlueprintAssetsTest {

    private static final byte[] PIXEL = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13,
            'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1f, 0x15, (byte) 0xc4,
            (byte) 0x89, 0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xae, 'B', '`', (byte) 0x82};

    @Autowired
    private ShBlueprintService shBlueprintService;
    @Autowired
    private ShBlueprintRegistry registry;
    @Autowired
    private ShBlueprintProperties blueprintProperties;
    @Autowired
    private ShAddressResolver shAddressResolver;
    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @TempDir
    private Path packages;

    private String originalPath;

    @BeforeEach
    void configure() {
        originalPath = blueprintProperties.getPath();
        blueprintProperties.setPath(packages.toString());
    }

    @AfterEach
    void restore() {
        blueprintProperties.setPath(originalPath);
        registry.reload();
    }

    @Test
    @DisplayName("SH1252: plan names the assets, apply writes them, re-apply is noop, new bytes replace")
    void assetsArePlannedWrittenAndConverge() throws IOException {
        Path dir = writePackage("sh1252-gallery", "assets/images/pixel.png", PIXEL);
        registry.reload();
        String site = "sh1252-" + UUID.randomUUID().toString().substring(0, 8);
        var options = new ShBlueprintWriteService.Options(true, false);
        var params = JsonNodeFactory.instance.objectNode();

        ShBlueprintApplied plan = shBlueprintService.plan("sh1252-gallery", site, params, "admin", options);
        assertThat(plan.assets()).singleElement().satisfies(row -> {
            assertThat(row.path()).isEqualTo("/images/pixel.png");
            assertThat(row.action()).isEqualTo("create");
            assertThat(row.bytes()).isEqualTo(PIXEL.length);
        });
        assertThat(plan.content().results())
                .as("SH1252: the folder an asset lands in is created by the content engine, so the "
                        + "plan names it beside every other folder")
                .anySatisfy(change -> assertThat(change.address()).contains("/images"));
        assertThat(shAddressResolver.find(ShAddress.site(site))).as("a plan writes nothing").isEmpty();

        ShBlueprintApplied applied = shBlueprintService.apply("sh1252-gallery", site, params, "admin",
                options);
        assertThat(applied.assets()).singleElement()
                .satisfies(row -> assertThat(row.action()).isEqualTo("create"));
        String folder = shAddressResolver.find(ShAddress.folder(site, "/images")).orElseThrow().id();
        String fileId = fileIdIn(folder).orElseThrow();

        ShBlueprintApplied again = shBlueprintService.apply("sh1252-gallery", site, params, "admin",
                options);
        assertThat(again.assets()).singleElement()
                .as("SH1252: P5 — the same bytes are left alone, so a converged re-apply uploads "
                        + "nothing and grows no rendition history")
                .satisfies(row -> assertThat(row.action()).isEqualTo("noop"));

        byte[] changed = PIXEL.clone();
        changed[changed.length - 5] ^= 1;
        Files.write(dir.resolve("assets/images/pixel.png"), changed);
        registry.reload();
        ShBlueprintApplied replaced = shBlueprintService.apply("sh1252-gallery", site, params, "admin",
                options);
        assertThat(replaced.assets()).singleElement()
                .satisfies(row -> assertThat(row.action()).isEqualTo("replace"));
        assertThat(fileIdIn(folder)).contains(fileId)
                .as("a replace keeps the id, so every field pointing at the asset still does");
    }

    @Test
    @DisplayName("SH1252: an asset outside assets/<folder>/ or missing from the package is refused at load")
    void malformedAssetsAreRefusedAtLoad() throws IOException {
        Path atRoot = writePackage("sh1252-root", "assets/pixel.png", PIXEL);
        assertThatThrownBy(() -> registry.readStaged(atRoot))
                .as("a site's root is not a folder an upload can address, so the package says where")
                .hasMessageContaining("a folder under assets/");

        Path outside = writePackage("sh1252-outside", "images/pixel.png", PIXEL);
        assertThatThrownBy(() -> registry.readStaged(outside)).hasMessageContaining("assets/");

        Path escaping = writePackage("sh1252-escaping", "assets/../secrets/pixel.png", null);
        assertThatThrownBy(() -> registry.readStaged(escaping))
                .as("SH1252: an asset is read from inside its own package, never beside it")
                .hasMessageContaining("escapes the package");

        Path missing = writePackage("sh1252-missing", "assets/images/pixel.png", null);
        assertThatThrownBy(() -> registry.readStaged(missing))
                .as("checked at load, like postTypes and content, not on somebody's apply")
                .hasMessageContaining("does not contain");

        ShBlueprint loaded = registry.readStaged(writePackage("sh1252-ok", "assets/a/b/pixel.png", PIXEL));
        assertThat(loaded.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.folder()).isEqualTo("/a/b");
            assertThat(asset.name()).isEqualTo("pixel.png");
            assertThat(asset.sha256()).hasSize(64);
        });
    }

    @Test
    @DisplayName("SH1252: the bound on a package's assets is half the installer's archive bound")
    void theAssetBoundIsRecorded() {
        assertThat(ShBlueprintRegistry.MAX_ASSET_BYTES).isEqualTo(32L * 1024 * 1024);
    }

    /** The lookup reads each candidate's lazy post-type, so it runs inside a read-only transaction. */
    private java.util.Optional<String> fileIdIn(String folder) {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        return readOnly.execute(status -> shStaticFileService.findFileId(folder, "pixel.png"));
    }

    /** A package with one content document and one declared asset; null bytes leave it absent. */
    private Path writePackage(String name, String asset, byte[] bytes) throws IOException {
        Path dir = Files.createDirectories(packages.resolve(name));
        Files.writeString(dir.resolve("content.json"), """
                {"folders":[{"path":"/gallery"}]}
                """);
        Files.writeString(dir.resolve(ShBlueprintManifest.FILE_NAME), """
                {
                  "formatVersion": 1,
                  "name": "%s",
                  "title": "A package with an image",
                  "version": "0.1.0",
                  "content": ["content.json"],
                  "assets": ["%s"]
                }
                """.formatted(name, asset));
        if (bytes != null) {
            Path file = dir.resolve(asset);
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        }
        return dir;
    }
}
