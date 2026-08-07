/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.url;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.ShAddress;

/**
 * SH583 — the shared generator's own behaviour.
 *
 * @author Alexandre Oliveira
 */
class ShFurlSpellingsTest {

    @Test
    @DisplayName("the canonical form is what ShAddress.normalizePath always meant")
    void canonicalCollapses() {
        assertThat(ShFurlSpellings.canonical("blog//hello")).isEqualTo("/blog/hello");
        assertThat(ShFurlSpellings.canonical("/blog/hello/")).isEqualTo("/blog/hello");
        assertThat(ShFurlSpellings.canonical("  /blog/hello  ")).isEqualTo("/blog/hello");
        assertThat(ShFurlSpellings.canonical("/")).isEqualTo("/");
        assertThat(ShFurlSpellings.canonical("")).isEqualTo("/");
        assertThat(ShFurlSpellings.canonical(null)).isEqualTo("/");
    }

    @Test
    @DisplayName("ShAddress.normalizePath is this method, not a copy of it")
    void theAddressGrammarDelegates() {
        // The whole point of SH583: two normalizations that agree today are the defect, not the
        // proof. This asserts one implementation rather than two matching answers.
        for (String path : new String[] {"/x", "/x/", "x", "x/", "//x//y//", "/", "", null}) {
            assertThat(ShAddress.normalizePath(path))
                    .as("normalizePath(%s)", path)
                    .isEqualTo(ShFurlSpellings.canonical(path));
        }
    }

    @Test
    @DisplayName("four spellings of a path, the requested one first")
    void candidatesAreRequestedFirst() {
        assertThat(ShFurlSpellings.candidates("/x"))
                .containsExactly("/x", "/x/", "x", "x/");
        assertThat(ShFurlSpellings.candidates("/x/"))
                .as("SH467 stores the declared spelling, so a request for it must not resolve to "
                        + "a stale row one character shorter")
                .containsExactly("/x/", "/x", "x", "x/");
        assertThat(ShFurlSpellings.candidates("x"))
                .as("a caller with no leading slash still means the canonical form")
                .containsExactly("/x", "/x/", "x", "x/");
        assertThat(ShFurlSpellings.candidates("/blog//hello/"))
                .containsExactly("/blog/hello/", "/blog/hello", "blog/hello", "blog/hello/");
    }

    @Test
    @DisplayName("the root has one spelling — an empty furl is absent, not empty")
    void theRootHasNoOtherSpelling() {
        // SH38 — an uploaded file post legitimately has no friendly URL, and several of them share
        // that blank. Treating "" as a spelling of "/" would resolve a site's home page to
        // whichever unnamed row sorted first.
        assertThat(ShFurlSpellings.candidates("/")).containsExactly("/");
        assertThat(ShFurlSpellings.candidates("")).containsExactly("/");
        assertThat(ShFurlSpellings.candidates(null)).containsExactly("/");
        assertThat(ShFurlSpellings.otherSlashForm("/")).isEmpty();
    }

    @Test
    @DisplayName("the other slash form points toward the stored spelling, not a fixed one")
    void otherSlashFormIsSymmetric() {
        assertThat(ShFurlSpellings.otherSlashForm("/x")).contains("/x/");
        assertThat(ShFurlSpellings.otherSlashForm("/x/")).contains("/x");
        assertThat(ShFurlSpellings.otherSlashForm(null)).isEmpty();
        assertThat(ShFurlSpellings.otherSlashForm("")).isEmpty();
    }
}
