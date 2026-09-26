/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH1110 — the rule a share link's path is held to, one string comparison and no content lookup.
 */
class ShPreviewSharePathTest {

    @Test
    @DisplayName("SH1110: a page prefix is that page, with or without its trailing slash, and nothing else")
    void aPageIsOnePage() {
        assertThat(ShPreviewShare.matches("/pricing", "/pricing")).isTrue();
        assertThat(ShPreviewShare.matches("/pricing", "/pricing/")).isTrue();
        assertThat(ShPreviewShare.matches("/pricing", "/pricing-old"))
                .as("a string prefix would let /pricing open /pricing-old, which is another page")
                .isFalse();
        assertThat(ShPreviewShare.matches("/pricing", "/pricing/annual"))
                .as("a page link is not a folder link")
                .isFalse();
        assertThat(ShPreviewShare.matches("/pricing", "/about")).isFalse();
    }

    @Test
    @DisplayName("SH1110: the home page is itself, not the whole site")
    void theHomePageIsOnePage() {
        assertThat(ShPreviewShare.matches("/", "/")).isTrue();
        assertThat(ShPreviewShare.matches("/", "")).isTrue();
        assertThat(ShPreviewShare.matches("/", "/about"))
                .as("`/` ends in a slash, and reading it as a folder would make the home page's link "
                        + "a site-wide one - the exact defect this task closes")
                .isFalse();
    }

    @Test
    @DisplayName("SH1110: a prefix ending in a slash is a folder and what is under it")
    void aFolderIsItsSubtree() {
        assertThat(ShPreviewShare.matches("/blog/", "/blog")).isTrue();
        assertThat(ShPreviewShare.matches("/blog/", "/blog/2026/hello")).isTrue();
        assertThat(ShPreviewShare.matches("/blog/", "/blogroll")).isFalse();
    }

    @Test
    @DisplayName("SH1110: a caller that is not a share is never narrowed")
    void notAShareIsNotNarrowed() {
        assertThat(ShPreviewShare.allowsPath("/anything")).isTrue();
    }
}
