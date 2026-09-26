/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.shio.blueprint.ShBlueprintProperties;
import com.viglet.shio.marketplace.ShMarketplaceInstaller;

import tools.jackson.databind.ObjectMapper;

/**
 * SH1108 — a pushed pattern is the file, byte for byte, beside the packages and invisible to them.
 */
class ShPatternStoreTest {

    private static final String PRICING = """
            {
              "formatVersion": 1,
              "name": "pricing-block",
              "title": "Pricing",
              "from": "post:Acme/pricing",
              "params": {
                "headline": {
                  "default": "Plans"
                }
              },
              "sections": [
                {
                  "type": "SectionHero",
                  "data": {
                    "heading": "{{headline}}"
                  }
                },
                {
                  "type": "SectionFaq",
                  "data": {}
                }
              ]
            }
            """;

    @TempDir
    Path root;

    private ShPatternStore store(Path path) {
        ShBlueprintProperties properties = new ShBlueprintProperties();
        properties.setPath(path == null ? null : path.toString());
        return new ShPatternStore(properties, new ObjectMapper());
    }

    @Test
    @DisplayName("SH1108: a pushed pattern is listed with the digest of the bytes the CLI holds")
    void aPushIsTheFile() throws Exception {
        ShPatternStore store = store(root);
        byte[] bytes = PRICING.getBytes(StandardCharsets.UTF_8);

        ShPatternStore.Stored stored = store.push("pricing-block", bytes);

        assertThat(Files.readAllBytes(root.resolve(".patterns").resolve("pricing-block.json")))
                .as("stored as sent, so the digest is the file's and `push` can say whether a copy "
                        + "is stale by comparing two hashes (SH1108)")
                .isEqualTo(bytes);
        assertThat(stored.digest()).isEqualTo(ShPatternStore.digest(bytes)).startsWith("sha256:");
        assertThat(store.list()).singleElement().satisfies(summary -> {
            assertThat(summary.name()).isEqualTo("pricing-block");
            assertThat(summary.title()).isEqualTo("Pricing");
            assertThat(summary.sections()).containsExactly("SectionHero", "SectionFaq");
            assertThat(summary.params()).containsExactly("headline");
            assertThat(summary.digest()).isEqualTo(stored.digest());
        });
    }

    @Test
    @DisplayName("SH1108: a file the CLI's reader would refuse is refused here too, with the reason")
    void aMalformedPatternIsRefused() {
        ShPatternStore store = store(root);

        assertThatThrownBy(() -> store.push("pricing-block",
                PRICING.replace("\"formatVersion\": 1", "\"formatVersion\": 2").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ShPatternStore.InvalidPatternException.class);
        assertThatThrownBy(() -> store.push("other-name", PRICING.getBytes(StandardCharsets.UTF_8)))
                .as("the file's own name is the name it is placed by, so a push under another is refused")
                .isInstanceOf(ShPatternStore.InvalidPatternException.class);
        assertThatThrownBy(() -> store.push("Pricing Block", PRICING.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ShPatternStore.InvalidPatternException.class);
        assertThatThrownBy(() -> store.push("pricing-block",
                "{\"formatVersion\":1,\"name\":\"pricing-block\",\"sections\":[]}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ShPatternStore.InvalidPatternException.class);
        assertThat(store.list()).as("and nothing was written by any of them").isEmpty();
    }

    @Test
    @DisplayName("SH1108: without an operator directory there is nothing to list and nowhere to push")
    void noDirectoryIsTheInstallersRefusal() {
        ShPatternStore store = store(null);

        assertThat(store.list()).isEmpty();
        assertThat(store.read("pricing-block")).isEmpty();
        assertThatThrownBy(() -> store.push("pricing-block", PRICING.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ShMarketplaceInstaller.NoBlueprintPathException.class);
    }
}
