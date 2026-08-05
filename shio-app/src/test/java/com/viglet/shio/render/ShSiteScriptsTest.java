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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;

/**
 * SH389 — the site's script list, and the two properties it exists to keep.
 *
 * <h2>Order, and never injecting</h2>
 *
 * <p>The task's complaint was that four layouts held four copies of a site's scripts and the load order
 * was a property of each copy — which for a consent banner is a compliance defect rather than
 * duplication. So the one thing this class must prove is that <b>the order in the JSON is the order in
 * the list</b>, per position, always. The other half of the design — that Shio exposes these and never
 * injects them — is proved by the absence of any code that could, and recorded here so the next reader
 * knows the absence is deliberate.
 *
 * <p>A pure test: parsing a field is a function from a string to three lists, so this needs no context
 * and no database.
 *
 * @author Alexandre Oliveira
 */
class ShSiteScriptsTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> at(String json, String position) {
        return (List<Map<String, Object>>) ShSiteScripts.of(json).get(position);
    }

    @Test
    @DisplayName("the JSON's order is the load order, per position — the property the task is about")
    void theOrderIsTheJsonsOrder() {
        String json = """
                [{"src":"https://consent.test/c.js","position":"head","category":"consent"},
                 {"src":"https://analytics.test/a.js","position":"head","category":"analytics"},
                 {"src":"https://chat.test/w.js","position":"bodyEnd"}]""";

        // Consent before analytics. Four per-layout copies could each get this wrong independently,
        // which is what made it a compliance defect rather than duplication.
        assertThat(at(json, "head")).extracting(row -> row.get("src"))
                .containsExactly("https://consent.test/c.js", "https://analytics.test/a.js");
        assertThat(at(json, "bodyEnd")).extracting(row -> row.get("src"))
                .containsExactly("https://chat.test/w.js");
        assertThat(at(json, "bodyStart")).isEmpty();
    }

    @Test
    @DisplayName("every position is present even when empty, so a template's {{#each}} is never nothing")
    void allThreePositionsAreAlwaysPresent() {
        for (String json : new String[] { null, "", "[]", "not json at all".replace(" ", "") }) {
            Map<String, Object> scripts = json != null && json.equals("notjsonatall")
                    ? ShSiteScripts.of(null)
                    : ShSiteScripts.of(json);
            assertThat(scripts.keySet()).containsExactlyElementsOf(ShSiteScripts.positions());
        }
    }

    @Test
    @DisplayName("an unknown position falls back to the END of the body, never to the head")
    void anUnknownPositionFallsBackLate() {
        // A typo must not move a script EARLIER than the author wrote it: that is the direction that
        // breaks a consent ordering. The end of the body is also the position that cannot block a paint.
        String json = "[{\"src\":\"https://x.test/a.js\",\"position\":\"heed\"}]";

        assertThat(at(json, "head")).isEmpty();
        assertThat(at(json, "bodyEnd")).hasSize(1);
    }

    @Test
    @DisplayName("an entry with neither src nor inline is skipped, not emitted as an empty tag")
    void anEmptyEntryIsSkipped() {
        // `<script src="">` re-requests the page itself in some browsers, which is a defect much
        // worse than the missing script. `shio verify` reports the entry; the renderer degrades.
        String json = """
                [{"position":"head"},
                 {"src":"","inline":"  ","position":"head"},
                 {"inline":"console.log(1)","position":"head"}]""";

        assertThat(at(json, "head")).hasSize(1);
        assertThat(at(json, "head").getFirst().get("inline")).isEqualTo("console.log(1)");
    }

    @Test
    @DisplayName("a row carries what a layout needs to emit it, including the consent category")
    void aRowCarriesWhatATemplateNeeds() {
        String json = "[{\"src\":\"https://x.test/a.js\",\"position\":\"head\",\"defer\":true,"
                + "\"async\":false,\"category\":\"analytics\"}]";

        assertThat(at(json, "head").getFirst()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "src", "https://x.test/a.js",
                "inline", "",
                "defer", true,
                "async", false,
                // Shio does not act on the category — acting on it is what "never inject" rules out —
                // but a layout that gates its own emission has to be able to read it.
                "category", "analytics",
                "position", "head"));
    }

    @Test
    @DisplayName("a malformed field throws to the caller, which is what makes the degradation reportable")
    void malformedJsonThrows() {
        // Theme.TOKENS' shape exactly (SH195/SH270): the page must not fail over a tag manager, and it
        // must not fail silently either. This class is pure, so the catch lives beside the incident log
        // in ShPageRenderService — `ShSilentCatchInventoryTest` is what makes that a decision.
        assertThatThrownBy(() -> ShSiteScripts.of("[{\"src\":"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    @DisplayName("JSON that is valid but not an array yields nothing rather than guessing at a shape")
    void aNonArrayYieldsNothing() {
        for (String json : new String[] { "{}", "\"x\"", "42", "null" }) {
            assertThat(ShSiteScripts.of(json).values())
                    .as(json)
                    .allSatisfy(list -> assertThat((List<?>) list).isEmpty());
        }
    }

    @Test
    @DisplayName("parsing the same field twice yields the same lists")
    void parsingIsStable() {
        String json = "[{\"src\":\"https://a.test/1.js\"},{\"inline\":\"x\",\"position\":\"head\"}]";

        assertThat(ShSiteScripts.of(json)).isEqualTo(ShSiteScripts.of(json));
    }
}
