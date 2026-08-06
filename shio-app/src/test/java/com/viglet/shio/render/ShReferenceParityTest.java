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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH515 — the two readings of "what does this page reference", held against one page.
 *
 * <h2>The asymmetry, which is the defect rather than any single missing pattern</h2>
 *
 * <p>A replica's console showed three 404s and a crash. {@code img/about-us.png} came from a
 * {@code style} <b>attribute</b>; {@code content/home-insights.json} came from a {@code fetch()}
 * inside a script; neither was a markup attribute the capture's collector read, nor a {@code url()}
 * in a stylesheet. And <b>the rewriter had already found all three</b> — the served page carried
 * them rebased onto the serving base. So one pass over the markup knew the reference existed and
 * the other did not, and the pass that knew is the one that cannot fetch. That asymmetry had
 * produced four tasks by then (SH452, SH455, SH473, SH515), each adding a place to look, and the
 * next unusual reference would have produced a fifth.</p>
 *
 * <h2>Why this test reads a file from the CLI</h2>
 *
 * <p>SH245's rule, and the same shape {@code ShConvertedLayoutRenderTest} uses: the seam is between
 * two languages, so a Java fixture asserted against a Java reader would go green while the halves
 * disagreed. {@code cli/test/fixtures/references-page.html} carries the page <em>and</em> the
 * expected set in one block; {@code clone.test.mjs} asserts the collector produces the {@code asset}
 * lines out of it, and this asserts the rewriter resolves the same ones plus the {@code link} lines.
 * A shape one side learns and the other does not fails here — which is the direction the defect
 * actually travelled.</p>
 *
 * <p>No Spring context: {@code referencesIn} is static and the question is about bytes.</p>
 *
 * @author Alexandre Oliveira
 */
class ShReferenceParityTest {

    /** The page both sides read — never copied. */
    private static final Path FIXTURE =
            Path.of("..", "cli", "test", "fixtures", "references-page.html");

    /** The URL the fixture declares itself served at: one segment deep, as SH512's source was. */
    private static final String BASE = "/en/";

    @Test
    @DisplayName("SH515: the rewriter resolves every reference the shared fixture declares")
    void theRewriterFindsWhatTheCollectorFetches() throws IOException {
        String html = Files.readString(FIXTURE, StandardCharsets.UTF_8);

        List<ShPreviewLinks.Reference> references = ShPreviewLinks.referencesIn(html, BASE);

        assertThat(references.stream().filter(ShPreviewLinks.Reference::asset)
                .map(ShPreviewLinks.Reference::path).distinct().sorted().toList())
                .as("the asset half, and it is the same list the capture downloads — a style "
                        + "attribute's url() and a fetch() literal included, which are the two that "
                        + "were rebased onto a replica that had never fetched them")
                .isEqualTo(expected(html, "asset"));
        assertThat(references.stream().filter(reference -> !reference.asset())
                .map(ShPreviewLinks.Reference::path).distinct().sorted().toList())
                .as("and the navigable half: an absolute URL, a mailto: and a bare #fragment are "
                        + "nobody's to rebase, and `careers` resolves inside /en/ because that is "
                        + "where the reader is")
                .isEqualTo(expected(html, "link"));
    }

    @Test
    @DisplayName("SH515: a fetch() the pass cannot read as a path is left exactly as written")
    void acomputedFetchArgumentIsNotAPath() throws IOException {
        String html = Files.readString(FIXTURE, StandardCharsets.UTF_8);

        assertThat(ShPreviewLinks.referencesIn(html, BASE))
                .as("`fetch(computedPath)` and a template literal holding ${…} name a path only at "
                        + "runtime. Rewriting a fragment of one would corrupt a string this pass "
                        + "does not understand, and fetching a guess would download what the page "
                        + "never asks for — so neither side knows about them, together")
                .noneMatch(reference -> reference.raw().contains("$")
                        || reference.raw().contains("computedPath"));
    }

    @Test
    @DisplayName("the fixture is not vacuous: it declares more than a handful and the page has them")
    void theFixtureIsNotEmpty() throws IOException {
        String html = Files.readString(FIXTURE, StandardCharsets.UTF_8);

        assertThat(expected(html, "asset"))
                .as("a fixture whose EXPECTED block silently came back empty would make both "
                        + "assertions above pass against a page nobody reads")
                .hasSizeGreaterThan(8);
        assertThat(html).contains("EXPECTED", "fetch(", "style=\"background-image:url(");
    }

    /** The fixture's own EXPECTED block — the one place the list lives, read rather than restated. */
    private static List<String> expected(String html, String kind) {
        Matcher lines = Pattern.compile("^" + kind + " (\\S+)$", Pattern.MULTILINE).matcher(html);
        return lines.results().map(result -> result.group(1)).sorted().toList();
    }
}
