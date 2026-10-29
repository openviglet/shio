/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Two templating languages, one delimiter (SH395).
 *
 * <h2>The reason no package shipped a layout</h2>
 *
 * <p>A blueprint parameter is <code>{{name}}</code> and so is a Handlebars expression. So a package
 * that carried a {@code PageLayout} was refused at load and again at substitution:
 * <code>{{post.title}}</code>, <code>{{theme.css}}</code> and <code>{{#query}}</code> all read as
 * parameters nobody declared. <b>The format could not express a layout</b> — which is a different
 * finding from the one §XI.16 recorded, where six packages shipping no design read as an authoring
 * omission.</p>
 *
 * <p>So the unknown-name refusal is lifted inside the render fields and nowhere else. Substitution
 * still runs there, which is the point: a layout may say <code>{{blogTitle}}</code> in its masthead
 * and get the caller's value, while the renderer's own expressions pass through untouched.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBlueprintTemplateFieldTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> VALUES = Map.of("blogTitle", "Notes");

    private static JsonNode node(String json) {
        return MAPPER.readTree(json);
    }

    @Test
    @DisplayName("SH395: a declared parameter is substituted inside a template field")
    void aParameterStillSubstitutesInATemplate() {
        JsonNode out = ShBlueprintParams.substitute(
                node("{\"HTML\":\"<h1>{{blogTitle}}</h1>\"}"), VALUES, "blog");

        assertThat(out.get("HTML").asString())
                .as("the exemption is about UNKNOWN names, not about the field: a package that "
                        + "could not parameterise its own layout would be carrying a constant")
                .isEqualTo("<h1>Notes</h1>");
    }

    @Test
    @DisplayName("SH395: the renderer's own expressions pass through a template field")
    void handlebarsPassesThrough() {
        JsonNode out = ShBlueprintParams.substitute(
                node("{\"HTML\":\"<title>{{post.title}}</title><style>{{theme.css}}</style>\"}"),
                VALUES, "blog");

        assertThat(out.get("HTML").asString())
                .as("""
                        `post.title` and `theme.css` are the renderer's, and refusing them is what \
                        made a layout unshippable in a package. This is the whole of SH395's \
                        blocker in one assertion.""")
                .isEqualTo("<title>{{post.title}}</title><style>{{theme.css}}</style>");
    }

    @Test
    @DisplayName("SH395: outside a template field an unknown name is still the package's bug")
    void proseStillRefusesAnUnknownName() {
        // The check earns its keep everywhere it can work. A headline that says `{{blogTitel}}`
        // is a typo that would ship as a literal `{{blogTitel}}` in somebody's copy, and that is
        // exactly what the refusal exists for — so the exemption is field-scoped rather than a
        // relaxation of the rule.
        assertThatThrownBy(() -> ShBlueprintParams.substitute(
                node("{\"headline\":\"Welcome to {{blogTitel}}\"}"), VALUES, "blog"))
                .isInstanceOf(ShBlueprintParams.UnknownPlaceholderException.class);
    }

    @Test
    @DisplayName("SH395: the load check and the substitution agree about which fields are templates")
    void oneListOfTemplateFields() {
        // Two lists would drift, and the direction that matters is the quiet one: a field the
        // loader treats as prose and the substituter treats as a template would refuse a package
        // at load; the reverse writes `{{post.title}}` into somebody's page. So the registry reads
        // the same set rather than declaring its own.
        assertThat(ShBlueprintRegistry.TEMPLATE_FIELDS)
                .isSameAs(ShBlueprintParams.TEMPLATE_FIELDS)
                .containsExactlyInAnyOrder("HTML", "CSS", "JAVASCRIPT");
    }

    @Test
    @DisplayName("SH395: a content document may declare the render model")
    void theRenderModelIsDeclarable() {
        // The other half of the mechanism: `postTypes` compiles to `render.provision` and
        // `postTypeLayout` to the site binding, both of which the desired-state service already
        // emits before any folder or post — the model exists before anything is written against
        // it, which is the order the traps are about.
        // SH1283 added `pageType`, a third thing a package answers for itself: which of the types it
        // binds is the page a curator adds to an empty folder.
        assertThat(ShBlueprintRegistry.CONTENT_KEYS)
                .as("`site` still comes from the request and `prune` is still never a package's "
                        + "decision; the keys added are ones a package can answer for itself")
                .containsExactly("folders", "posts", "postTypes", "postTypeLayout", "pageType");
    }
}
