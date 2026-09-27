/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.render.ShSiteFrontEnd.Destination;
import com.viglet.shio.render.ShSiteFrontEnd.Kind;

/**
 * SH184 — "where can a person look at this site?" always has an answer now.
 *
 * @author Alexandre Oliveira
 */
class ShSiteFrontEndTest {

    @Test
    @DisplayName("a configured front end wins, and is used as given")
    void anExternalUrlIsUsed() {
        Destination destination = ShSiteFrontEnd.of(site("Blog", "https://blog.example.com"));

        assertThat(destination.kind()).isEqualTo(Kind.EXTERNAL);
        assertThat(ShSiteFrontEnd.pageUrl(destination, "/hello"))
                .isEqualTo("https://blog.example.com/hello");
    }

    @Test
    @DisplayName("a trailing slash does not become a double slash")
    void trailingSlashesAreTrimmed() {
        Destination destination = ShSiteFrontEnd.of(site("Blog", "https://blog.example.com/"));

        assertThat(ShSiteFrontEnd.pageUrl(destination, "/hello"))
                .as("some front ends serve //hello, some redirect, and one or two 404")
                .isEqualTo("https://blog.example.com/hello");
    }

    @Test
    @DisplayName("no front end falls back to Shio's own renderer instead of failing")
    void noUrlFallsBackToPreview() {
        Destination destination = ShSiteFrontEnd.of(site("Blog", null));

        assertThat(destination.kind())
                .as("""
                        an empty url is the *normal* state of a site an agent just built, so \
                        `400 site has no url` made both visual surfaces unavailable at exactly \
                        the moment there was something new to see. SH183 removed the reason.""")
                .isEqualTo(Kind.SHIO_PREVIEW);
        assertThat(ShSiteFrontEnd.pageUrl(destination, "/hello")).isEqualTo("/preview/Blog/hello");
    }

    @Test
    @DisplayName("blank counts as absent — a whitespace url is not a front end")
    void blankIsAbsent() {
        assertThat(ShSiteFrontEnd.of(site("Blog", "   ")).kind()).isEqualTo(Kind.SHIO_PREVIEW);
    }

    @Test
    @DisplayName("SH1173: a site name that needs encoding is encoded as a path segment")
    void siteNamesAreEncoded() {
        // This asserted `/preview/My+Site` — form encoding, where a path reads `+` as a literal plus,
        // so the URL it pinned was a 404. A space in a path is `%20`.
        assertThat(ShSiteFrontEnd.of(site("My Site", null)).baseUrl())
                .isEqualTo("/preview/My%20Site");
    }

    @Test
    @DisplayName("a site with no name is addressed by its id, not by an empty segment")
    void aNamelessSiteFallsBackToItsId() {
        ShSite site = new ShSite();
        site.setId("site-abc");

        assertThat(ShSiteFrontEnd.of(site).baseUrl())
                .as("""
                        /preview/{siteKey} resolves id, furl or name (SH181), so the id costs \
                        nothing and is the difference between an ugly URL and `/preview//x`. \
                        A site really can arrive here nameless: ShPreviewTokenAPI resolves it \
                        through ShFolderSite, which returns whatever the parent walk finds.""")
                .isEqualTo("/preview/site-abc");
    }

    @Test
    @DisplayName("a post with no friendly URL is the site root, not a broken path")
    void noFurlIsTheRoot() {
        Destination destination = ShSiteFrontEnd.of(site("Blog", null));

        assertThat(ShSiteFrontEnd.pageUrl(destination, null)).isEqualTo("/preview/Blog/");
        assertThat(ShSiteFrontEnd.pageUrl(destination, "hello"))
                .as("a furl missing its leading slash still composes to one path")
                .isEqualTo("/preview/Blog/hello");
    }

    private ShSite site(String name, String url) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setUrl(url);
        return site;
    }
}
