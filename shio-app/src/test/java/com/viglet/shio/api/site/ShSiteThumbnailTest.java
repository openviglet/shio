/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.api.agent.verify.ShAgentRenderService;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.property.ShSiteThumbnailProperties;

/**
 * SH1218 — a site's published home, kept as a picture, and kept only when it changed.
 *
 * <p>The instance does not run a browser, so the console takes the picture and this decides whether
 * it is needed: the published home's appearance digest is the key. Driven with a renderer whose
 * digest the test sets, because what is under test is the bookkeeping around the hash and not the
 * renderer, which review already relies on (SH1002).</p>
 */
class ShSiteThumbnailTest {

    private static final byte[] PICTURE = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' };

    @TempDir
    Path root;

    private ShAgentRenderService renderer;
    private ShSiteThumbnailStore store;
    private ShSiteThumbnailService service;
    private ShSite site;

    @BeforeEach
    void setUp() {
        ShSiteThumbnailProperties properties = new ShSiteThumbnailProperties();
        properties.setPath(root.toString());
        properties.setMaxBytes(64);
        store = new ShSiteThumbnailStore(properties);
        renderer = mock(ShAgentRenderService.class);
        service = new ShSiteThumbnailService(renderer, store);
        site = new ShSite();
        site.setId("site-sh1218");
        site.setName("Cafe");
    }

    private void homeLooksLike(String appearance) {
        ShAgentDigest digest = mock(ShAgentDigest.class);
        when(digest.appearanceDigest()).thenReturn(appearance);
        when(renderer.digest(eq("post:Cafe/"), any(), eq("published"))).thenReturn(digest);
    }

    @Test
    @DisplayName("SH1218: publishing a home stores one picture, and the same home again stores none")
    void anUnchangedHomeStoresNothing() throws Exception {
        homeLooksLike("a:one");
        assertThat(service.status(site).fresh())
                .as("SH1218: nothing is stored yet, so the console is told to take a picture")
                .isFalse();

        assertThat(service.put(site, "a:one", "image/webp", PICTURE).stored()).isTrue();
        assertThat(store.read(site.getId())).get()
                .extracting(ShSiteThumbnailStore.Stored::digest).isEqualTo("a:one");
        Path image = store.image(site.getId()).orElseThrow();
        var written = Files.getLastModifiedTime(image);
        assertThat(service.status(site).fresh()).isTrue();

        assertThat(service.put(site, "a:one", "image/webp", PICTURE).stored())
                .as("SH1218: re-publishing a home that looks the same must store nothing")
                .isFalse();
        assertThat(Files.getLastModifiedTime(image)).isEqualTo(written);

        homeLooksLike("a:two");
        assertThat(service.status(site).fresh())
                .as("SH1218: a home that changed makes the stored picture stale").isFalse();
        assertThat(service.put(site, "a:two", "image/webp", PICTURE).stored()).isTrue();
    }

    @Test
    @DisplayName("SH1218: a picture of a home that has since changed is refused, naming the digest now")
    void aStalePictureIsRefused() {
        homeLooksLike("a:now");
        assertThatThrownBy(() -> service.put(site, "a:before", "image/png", PICTURE))
                .isInstanceOf(ShSiteThumbnailRefusedException.class)
                .hasMessageContaining("a:now");
        assertThat(store.read(site.getId())).isEmpty();
    }

    @Test
    @DisplayName("SH1218: no published home, no picture")
    void noHomeNoPicture() {
        when(renderer.digest(eq("post:Cafe/"), any(), eq("published")))
                .thenThrow(new IllegalArgumentException("no post at post:Cafe/"));
        assertThat(service.status(site).home()).isNull();
        assertThatThrownBy(() -> service.put(site, "a:one", "image/png", PICTURE))
                .isInstanceOf(ShSiteThumbnailRefusedException.class)
                .hasMessageContaining("post:Cafe/");
    }

    @Test
    @DisplayName("SH1218: only an image a card can show, and only at card size")
    void onlyAnImageAtCardSize() {
        homeLooksLike("a:one");
        assertThatThrownBy(() -> service.put(site, "a:one", "text/html", PICTURE))
                .isInstanceOf(ShSiteThumbnailRefusedException.class);
        assertThatThrownBy(() -> service.put(site, "a:one", "image/png", new byte[65]))
                .isInstanceOf(ShSiteThumbnailRefusedException.class)
                .hasMessageContaining("65 bytes");
        assertThat(store.read(site.getId())).isEmpty();
    }

    @Test
    @DisplayName("SH1218: purging the site takes its picture with it, and a listing finds what is stored")
    void forgetAndList() {
        homeLooksLike("a:one");
        service.put(site, "a:one", "image/jpeg", PICTURE);
        assertThat(store.all()).containsOnlyKeys(site.getId());

        store.forget(site.getId());
        assertThat(store.read(site.getId())).isEmpty();
        assertThat(store.all()).isEmpty();
    }
}
