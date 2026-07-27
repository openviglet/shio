/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.memory.dto.ShAgentNote;

/**
 * SH141 — the declared front-end routes, as a grammar.
 *
 * <p>No Spring context: this is a pure translation from a memory note to a predicate, and the
 * reason it gets its own test is that a mistake here is <em>silent</em>. Every other rule in
 * Block K fails loudly when it is wrong; this one only ever stops a finding from being
 * reported, so an over-broad pattern turns the lint off for a whole site and nothing says so.
 * The assertions below are therefore mostly about what must <b>not</b> match.</p>
 *
 * @author Alexandre Oliveira
 */
class ShFrontEndRoutesTest {

    private static ShFrontEndRoutes of(String scope, String note) {
        return ShFrontEndRoutes.of(List.of(new ShAgentNote(scope, ShFrontEndRoutes.MEMORY_KEY,
                note, "tester", Instant.parse("2026-01-01T00:00:00Z"))));
    }

    @Test
    @DisplayName("a single star stays inside one path segment")
    void starDoesNotCrossSegments() {
        ShFrontEndRoutes routes = of("Blog", "/tags/*");
        assertThat(routes.declares("Blog", "/tags/java")).isTrue();
        // The whole reason it is a glob and not a prefix: /tags/* must not cover a deeper tree
        // the front end may not serve at all.
        assertThat(routes.declares("Blog", "/tags/java/page/2")).isFalse();
        assertThat(routes.declares("Blog", "/tag/java")).isFalse();
    }

    @Test
    @DisplayName("a double star crosses them")
    void doubleStarCrossesSegments() {
        ShFrontEndRoutes routes = of("Blog", "/archive/**");
        assertThat(routes.declares("Blog", "/archive/2026")).isTrue();
        assertThat(routes.declares("Blog", "/archive/2026/03/deep")).isTrue();
        assertThat(routes.declares("Blog", "/archives/2026")).isFalse();
    }

    @Test
    @DisplayName("a list may be separated by commas, newlines or spaces")
    void separatorsAreForgiving() {
        ShFrontEndRoutes routes = of("Blog", "/search, /tags/*\n/rss.xml");
        assertThat(routes.declares("Blog", "/search")).isTrue();
        assertThat(routes.declares("Blog", "/tags/go")).isTrue();
        assertThat(routes.declares("Blog", "/rss.xml")).isTrue();
        assertThat(routes.declares("Blog", "/rssaxml")).isFalse();
    }

    @Test
    @DisplayName("a declaration is scoped to its site, and an instance-wide one covers all")
    void scopingFollowsTheNote() {
        ShFrontEndRoutes site = of("Blog", "/search");
        assertThat(site.declares("Blog", "/search")).isTrue();
        assertThat(site.declares("Docs", "/search")).isFalse();

        ShFrontEndRoutes everywhere = ShFrontEndRoutes.of(List.of(
                new ShAgentNote(null, ShFrontEndRoutes.MEMORY_KEY, "/search", "tester",
                        Instant.parse("2026-01-01T00:00:00Z"))));
        assertThat(everywhere.declares("Docs", "/search")).isTrue();
    }

    @Test
    @DisplayName("prose that is not a path is dropped, not compiled into something broad")
    void nonPathsAreDropped() {
        // The realistic accident: somebody writes a sentence into the note. A word that is not
        // a path must contribute nothing — the dangerous failure is a pattern that matches
        // everything, because it would silence the whole rule and look like a clean site.
        ShFrontEndRoutes routes = of("Blog", "the front end owns tags and search");
        assertThat(routes.isEmpty()).isTrue();
        assertThat(routes.declares("Blog", "/tags/java")).isFalse();
        assertThat(routes.declares("Blog", "search")).isFalse();
    }

    @Test
    @DisplayName("only the frontend-routes key is read")
    void otherNotesAreIgnored() {
        ShFrontEndRoutes routes = ShFrontEndRoutes.of(List.of(
                new ShAgentNote("Blog", "tone-of-voice", "/anything", "tester",
                        Instant.parse("2026-01-01T00:00:00Z"))));
        assertThat(routes.isEmpty()).isTrue();
        assertThat(routes.declares("Blog", "/anything")).isFalse();
    }

    @Test
    @DisplayName("no notes at all declares nothing")
    void emptyIsEmpty() {
        assertThat(ShFrontEndRoutes.none().declares("Blog", "/x")).isFalse();
        assertThat(ShFrontEndRoutes.of(null).isEmpty()).isTrue();
        assertThat(ShFrontEndRoutes.of(List.of()).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a path is compared in the instance's one canonical form")
    void pathsAreNormalized() {
        ShFrontEndRoutes routes = of("Blog", "/Search/");
        assertThat(routes.declares("Blog", "/search")).isTrue();
        assertThat(routes.declares("Blog", "//SEARCH")).isTrue();
    }
}
