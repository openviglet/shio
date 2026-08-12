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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH695 — one judgement, two implementations, held to the same answers.
 *
 * <h2>Why the judgement exists twice</h2>
 *
 * <p>{@link ShHtmlRegions} reads the page <b>the instance renders</b>, for the digest (SH687).
 * {@code cli/src/regions.mjs} reads the HTML <b>the replica serves</b>, for {@code shio diff}, which
 * is about to score one against the other (SH692). Two artefacts, two runtimes, one question: which
 * landmarks does this page draw, in what order, and who owns each one's markup.</p>
 *
 * <p>A shared implementation is not available and that is a deliberate property rather than an
 * oversight: {@code cli/package.json} declares <b>no third-party dependency at all</b>, and adding an
 * HTML parser to share this one judgement would be the first. So they agree by <em>assertion</em> —
 * this test and {@code cli/test/regions.test.mjs} read the same file, and a case added to it is a
 * case both must answer.</p>
 *
 * <h2>Why the fixtures are the awkward ones</h2>
 *
 * <p>Two parsers agree about a well-formed page by construction. Where they diverge is bad nesting,
 * raw text whose body looks like markup, void elements and a close tag matching nothing — so those
 * are what the file holds. A fixture set of neat pages would prove the readers agree about the pages
 * that were never going to be a problem.</p>
 *
 * @author Alexandre Oliveira
 */
class ShHtmlRegionsAgreementTest {

    /**
     * The shared cases, which live on the CLI side.
     *
     * <p>One copy, in the tree that also runs them from JavaScript. A copy per runtime is two files
     * that drift, and the drift would be invisible: each suite would keep passing against its own.</p>
     */
    private static final Path FIXTURES =
            Paths.get("..", "cli", "test", "fixtures", "regions.json");

    private static JsonNode fixtures() {
        try {
            return new ObjectMapper().readTree(Files.readString(FIXTURES, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "SH695 — the shared region fixtures are missing at " + FIXTURES.toAbsolutePath()
                            + ". They are read by this test and by cli/test/regions.test.mjs; if they "
                            + "moved, move both.", e);
        }
    }

    @Test
    @DisplayName("SH695: the Java reader answers every case the JavaScript one is held to")
    void bothReadersAnswerTheSharedCases() {
        JsonNode cases = fixtures().get("cases");
        assertThat(cases).isNotNull();
        assertThat(cases.size())
                .as("a fixture set that shrank is a guard that stopped asking")
                .isGreaterThanOrEqualTo(10);

        List<String> disagreed = new ArrayList<>();
        for (JsonNode each : cases) {
            String html = each.get("html").asString();
            List<String> expected = new ArrayList<>();
            each.get("expect").forEach(entry -> expected.add(entry.asString()));

            List<String> actual = ShHtmlRegions.of(html, Set.of()).stream()
                    .map(region -> region.tag() + ":" + region.markup())
                    .toList();
            if (!actual.equals(expected)) {
                disagreed.add(each.get("name").asString()
                        + "\n    expected: " + expected
                        + "\n    java got: " + actual
                        + "\n    html:     " + html);
            }
        }

        assertThat(disagreed)
                .as("""
                        `shio diff` is about to score the served HTML against the rendered page \
                        using both readers, so a case they answer differently is a comparison whose \
                        two sides describe different documents. The fixtures are shared on purpose \
                        — cli/test/fixtures/regions.json (SH695).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH695: the fixtures hold the shapes two parsers actually disagree about")
    void theFixturesAreTheAwkwardOnes() {
        // Guards the guard. The value of this file is entirely in WHICH pages it holds: two parsers
        // agree about well-formed markup by construction, so a set of neat pages would pass while
        // proving nothing. If somebody trims these, the agreement stops meaning anything and this
        // is what says so.
        String all = fixtures().get("cases").toString();

        assertThat(all).as("raw text whose body looks like structure").contains("<script>");
        assertThat(all).as("an element left unclosed").contains("<p>one<p>two");
        assertThat(all).as("a close tag matching nothing").contains("</div>");
        assertThat(all).as("a void element, which opens no frame").contains("<img");
        assertThat(all).as("a self-closing form of a container").contains("/>");
        assertThat(all).as("a landmark nested inside an authored section").contains("<table>");
    }
}
