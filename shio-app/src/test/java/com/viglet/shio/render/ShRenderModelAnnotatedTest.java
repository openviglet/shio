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

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;

import tools.jackson.databind.ObjectMapper;

/**
 * SH870 — the edit that was stored and never shown.
 *
 * <p>SH864 measured that annotating a fidelity page is free and decided where an annotated page's
 * values live: <b>the field is the address</b>. {@code ShPostDraftMergeService} writes
 * {@code data.<field>} on the post exactly as it does everywhere else, so the write path needed
 * nothing. What was missing is the render half — the page's markup is stored verbatim, so the value
 * landed in the post and the page went on showing the source's own text. The editor accepted an edit
 * and the page denied it.</p>
 *
 * <p>No Spring context, for {@code ShRenderModelUrlsTest}' reason: this needs an
 * {@code ObjectMapper} and the incident log, and SH317 counts contexts.</p>
 *
 * @author Alexandre Oliveira
 */
class ShRenderModelAnnotatedTest {

    // SH1056 — the locale service reads the site's own column here and never the content, so the
    // two repositories it would need for a DERIVED policy are genuinely unused on this path. A
    // mock would assert that; a null says it, and fails loudly if the path ever starts deriving.
    private final ShRenderModel renderModel = new ShRenderModel(new ObjectMapper(),
            new ShAgentIncidentLog(),
            new ShSiteLocaleService(null, null, new ShAgentIncidentLog(), new ObjectMapper()));

    /** A verbatim block as `convert` keeps it, with one element annotated (SH868 stamps these). */
    private static final String KEPT =
            "<section class=\"hero\"><h1 sh-field=\"headline\">Enterprise Intelligence</h1>"
                    + "<p>Copy the source wrote.</p></section>";

    @Test
    @DisplayName("SH870: an annotated element shows the post's field once the field has a value")
    void anAnnotatedElementShowsTheField() {
        Map<String, Object> model = modelOf("{\"content\":" + json(KEPT)
                + ",\"headline\":\"Enterprise Intelligence, edited\"}");

        assertThat((String) model.get("content"))
                .contains("Enterprise Intelligence, edited")
                .doesNotContain(">Enterprise Intelligence<");
        assertThat((String) model.get("content"))
                .as("and only that element: the rest of the block is the source's own markup")
                .contains("Copy the source wrote.")
                .contains("class=\"hero\"");
    }

    @Test
    @DisplayName("with no value the markup is returned byte-for-byte, so annotating moves no pixels")
    void withNoValueTheMarkupIsUntouched() {
        // The property SH864 measured — 41 annotations cost nothing against a source that varies
        // against itself by more. Parsing and re-serialising normalises markup, so a page with
        // nothing to substitute must not be parsed at all; anything else makes annotating cost a
        // rendering difference and the measurement stops being true.
        Map<String, Object> model = modelOf("{\"content\":" + json(KEPT) + "}");

        assertThat((String) model.get("content")).isEqualTo(KEPT);
    }

    @Test
    @DisplayName("a field equal to the source's own text is not an edit, and costs no rewrite")
    void aFieldEqualToTheSourceIsNotAnEdit() {
        Map<String, Object> model = modelOf("{\"content\":" + json(KEPT)
                + ",\"headline\":\"Enterprise Intelligence\"}");

        assertThat((String) model.get("content"))
                .as("what an unedited field renders IS the source's text, so writing it back would "
                        + "spend a re-serialisation to produce the same page")
                .isEqualTo(KEPT);
    }

    @Test
    @DisplayName("a value carrying markup is set as text, so the page cannot be rewritten by a field")
    void aValueCarryingMarkupIsText() {
        Map<String, Object> model = modelOf("{\"content\":" + json(KEPT)
                + ",\"headline\":\"<script>alert(1)</script>\"}");

        assertThat((String) model.get("content"))
                .as("one annotated element, one declared field, no expression — the value is the "
                        + "element's TEXT and never markup a curator can inject")
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("a collection path is left to the section's own template, which renders those rows")
    void aCollectionPathIsLeftAlone() {
        // `features.0.title` addresses a row of this post's own data and the section template draws
        // it. Substituting here would overwrite what that template produced, which is the reason
        // this lives in the model rather than in `ShRegionComposer`'s whole-document walk.
        String rows = "<li sh-field=\"features.0.title\">Fast</li>";
        Map<String, Object> model = modelOf("{\"content\":" + json(rows)
                + ",\"features.0.title\":\"Edited\"}");

        assertThat((String) model.get("content")).isEqualTo(rows);
    }

    @Test
    @DisplayName("a field with no annotation in the markup changes nothing at all")
    void anUnannotatedFieldChangesNothing() {
        String plain = "<section><h1>Enterprise Intelligence</h1></section>";
        Map<String, Object> model = modelOf("{\"content\":" + json(plain)
                + ",\"headline\":\"Edited\"}");

        assertThat((String) model.get("content"))
                .as("the annotation is the substitution point, so markup carrying none is verbatim")
                .isEqualTo(plain);
    }

    private Map<String, Object> modelOf(String data) {
        ShPostUnified post = new ShPostUnified();
        post.setId("99999999-8888-7777-6666-555555555555");
        post.setTitle("Home");
        post.setFurl("/");
        post.setData(data);
        return renderModel.of(post, null);
    }

    /** A JSON string literal, so a fixture's own quotes survive into the post's data. */
    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
