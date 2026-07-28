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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import com.viglet.shio.render.ShTemplateEngine.ShHelperShape;
import com.viglet.shio.render.ShTemplateFieldScanner.ShHelperShapes;
import com.viglet.shio.render.ShTemplateFieldScanner.ShTemplateReference;

/**
 * SH251 — the scanner, on its own, because the interesting property is the <b>scope</b>.
 *
 * <p>A flat list of names is what Handlebars.java's own {@code Template#collect(TagType.VAR)}
 * gives, and it is not enough: {@code {{TEXT}}} at the top level belongs to the page's post-type
 * and the identical token inside {@code {{#query}}} belongs to whatever post that block listed.
 * The first is checkable and the second is not, so getting the nesting right is the whole
 * difference between a rule that reports real defects and one that reports every listing body.</p>
 *
 * <p>No Spring context: this is pure text in, references out, which is what makes it cheap enough
 * to assert the false-positive cases as thoroughly as the true ones.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTemplateFieldScannerTest {

    /**
     * The shapes the engine registers, spelled out (SH260).
     *
     * <p>Explicit rather than defaulted, because there is no overload that omits them: a forgotten
     * argument would report every helper's own name as an undeclared field, which is the rule
     * inventing the very defect it exists to catch. Written as the map the engine hands over, so
     * this test also documents what a caller has to supply.</p>
     */
    private static final ShHelperShapes HELPERS = ShHelperShapes.of(Map.of(
            "query", ShHelperShape.BLOCK,
            "navigation", ShHelperShape.BLOCK,
            "search", ShHelperShape.BLOCK,
            "getRelation", ShHelperShape.BLOCK_PATH_ARG,
            "file_url", ShHelperShape.INLINE_PATH_ARG));

    private static List<String> pageScoped(String template) {
        return ShTemplateFieldScanner.referencesIn(template, HELPERS).stream()
                .filter(ShTemplateReference::isPageScoped)
                .map(ShTemplateReference::name)
                .toList();
    }

    private static List<String> inBlocks(String template) {
        return ShTemplateFieldScanner.referencesIn(template, HELPERS).stream()
                .filter(reference -> !reference.isPageScoped())
                .map(reference -> reference.scope() + ":" + reference.name())
                .toList();
    }

    @Test
    @DisplayName("a top-level interpolation belongs to the page")
    void topLevelBelongsToThePage() {
        assertThat(pageScoped("<h1>{{TITLE}}</h1><div>{{{TEXT}}}</div>"))
                .containsExactly("TITLE", "TEXT");
    }

    @Test
    @DisplayName("an interpolation inside a helper block belongs to that block's rows")
    void insideABlockBelongsToTheBlock() {
        String template = """
                <h1>{{TITLE}}</h1>
                <ul>{{#query type="Article"}}<li>{{title}} — {{TEXT}}</li>{{/query}}</ul>
                <p>{{FOOTER}}</p>""";

        assertThat(pageScoped(template))
                .as("""
                        the whole point: TITLE and FOOTER are the page's, TEXT inside the block is \
                        the listed post's. A scanner that lost the nesting would report TEXT \
                        against the page's post-type and be wrong on every listing.""")
                .containsExactly("TITLE", "FOOTER");
        assertThat(inBlocks(template)).containsExactly("query:title", "query:TEXT");
    }

    @Test
    @DisplayName("nesting closes, so what follows a block is the page's again")
    void blocksClose() {
        assertThat(pageScoped("{{#each ITEMS}}{{inner}}{{/each}}{{AFTER}}"))
                .as("ITEMS is the block's own argument, resolved where the block is written")
                .containsExactly("ITEMS", "AFTER");
        assertThat(inBlocks("{{#each ITEMS}}{{inner}}{{/each}}{{AFTER}}"))
                .containsExactly("each:inner");
    }

    @Test
    @DisplayName("a block's own argument is resolved in the enclosing scope")
    void blockArgumentsAreEnclosingScope() {
        // `{{#if TEXT}}` asks about the current scope's TEXT, so it is page-scoped even though
        // everything between the braces is not.
        assertThat(pageScoped("{{#if SUMMARY}}<p>{{SUMMARY}}</p>{{/if}}"))
                .containsExactly("SUMMARY");
        assertThat(inBlocks("{{#if SUMMARY}}<p>{{SUMMARY}}</p>{{/if}}"))
                .containsExactly("if:SUMMARY");
    }

    @Test
    @DisplayName("getRelation's argument is a field of the page — the SH95 sections case")
    void getRelationArgumentIsPageScoped() {
        String template = "{{#getRelation SECTIONS}}<section>{{title}}</section>{{/getRelation}}";

        assertThat(pageScoped(template))
                .as("""
                        this is the reference that matters most for an SH95 Page: if the field is \
                        called SECTIONS and the template says PARTS, the page renders empty and \
                        looks like content nobody wrote.""")
                .containsExactly("SECTIONS");
        assertThat(inBlocks(template)).containsExactly("getRelation:title");
    }

    @Test
    @DisplayName("an inline helper's name is not a field, and its argument is")
    void inlineHelperNamesAreNotFields() {
        assertThat(pageScoped("<img src=\"{{file_url HERO}}\">"))
                .as("reporting `file_url` would be a false positive on the one helper the 2018 "
                        + "navigation region used")
                .containsExactly("HERO");
    }

    @Test
    @DisplayName("the reserved frames are paths, and the lint recognises them by their root")
    void framesArePathsWithRoots() {
        List<ShTemplateReference> found = ShTemplateFieldScanner.referencesIn(
                "{{post.title}} {{site.name}} {{theme.css}}", HELPERS);

        assertThat(found).extracting(ShTemplateReference::root)
                .containsExactly("post", "site", "theme");
    }

    @Test
    @DisplayName("what is not a field reference is not reported")
    void nonFieldsAreNotReported() {
        assertThat(ShTemplateFieldScanner.referencesIn("""
                {{! a comment }}
                {{!-- a long comment --}}
                {{> partial}}
                {{#each ITEMS}}{{@index}} {{this}} {{./x}} {{../outer}}{{/each}}
                {{#if (eq A B)}}x{{/if}}
                {{lookup a b}}
                {{"literal"}}
                {{42}}""", HELPERS))
                .as("""
                        every one of these is either not a field or is resolved somewhere this \
                        cannot model — and a rule with false positives is one authors learn to \
                        ignore, which is SH192's finding applied a second time.""")
                .extracting(ShTemplateReference::name)
                .containsExactly("ITEMS");
    }

    @Test
    @DisplayName("an unterminated mustache stops the scan instead of guessing")
    void anUnterminatedMustacheStops() {
        // SH192 already refuses this template — it will not compile — so inventing a name here
        // would report something nobody wrote, on top of a finding that already exists.
        assertThat(pageScoped("{{GOOD}} {{#query}} {{BROKEN")).containsExactly("GOOD");
    }

    @Test
    @DisplayName("a block that never closes does not swallow the rest as page-scoped")
    void anUnclosedBlockKeepsItsScope() {
        assertThat(pageScoped("{{#query}}{{title}}"))
                .as("the safe direction: an unclosed block means the trailing references are "
                        + "attributed to it, so nothing is reported as the page's by mistake")
                .isEmpty();
    }

    @Test
    @DisplayName("whitespace and triple stashes do not change what is found")
    void formattingDoesNotMatter() {
        assertThat(pageScoped("{{  TEXT  }} {{{ TEXT }}} {{~TEXT~}}"))
                .as("a triple stash changes escaping, not resolution; `~` trims whitespace")
                .hasSize(3)
                .containsOnly("TEXT");
    }
}
