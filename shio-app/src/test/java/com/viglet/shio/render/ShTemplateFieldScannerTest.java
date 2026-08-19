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
import static org.assertj.core.api.Assertions.tuple;

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

    /** SH836 — keyed by the scope the reference is *written* in, which nothing else here prints. */
    private static List<String> writtenScopes(String template) {
        return ShTemplateFieldScanner.referencesIn(template, HELPERS).stream()
                .map(reference -> reference.scope() + ":" + reference.name())
                .toList();
    }

    /** SH836 — keyed by the scope the *lint* asks about, which is not always the one written. */
    private static List<String> helperScopes(String template) {
        return ShTemplateFieldScanner.referencesIn(template, HELPERS).stream()
                .map(reference -> reference.helperScope() + ":" + reference.name())
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
        // `{{#if TEXT}}` asks about the current scope's TEXT — and so does everything between the
        // braces, which is SH836's correction to this test's own premise. An `{{#if}}` decides
        // whether its body renders and leaves the context exactly as it found it, so a guarded
        // reference is owned by whatever encloses the guard. This one is at the top level, so both
        // are the page's, and the strong check — *the bound post-type declares no such field* — now
        // reaches the reference it could never see before.
        assertThat(pageScoped("{{#if SUMMARY}}<p>{{SUMMARY}}</p>{{/if}}"))
                .containsExactly("SUMMARY", "SUMMARY");
        assertThat(writtenScopes("{{#if SUMMARY}}<p>{{SUMMARY}}</p>{{/if}}"))
                .as("""
                        and `scope` is unchanged, because it answers a different question: where the \
                        reference is WRITTEN. Only `helperScope` walks out, and only through a block \
                        that rebinds nothing — so `inBlocks` above, which reads `isPageScoped`, is \
                        now empty for this template and that is the change rather than a loss.""")
                .containsExactly("null:SUMMARY", "if:SUMMARY");
        assertThat(inBlocks("{{#if SUMMARY}}<p>{{SUMMARY}}</p>{{/if}}")).isEmpty();
    }

    /**
     * SH836 — the two builtins that rebind the context are not transparent, and the suite is what
     * said so.
     *
     * <p>The task was filed naming all four builtins as blocks to walk out through, and with
     * {@code each} among them {@code {{#each ITEMS}}{{inner}}{{/each}}} reported {@code inner} as the
     * <em>page's</em> field — wrong on every listing, and the exact defect SH251 exists to catch with
     * the sign flipped. {@code blocksClose} above is what refused it; this pins the pair directly so
     * the distinction survives somebody reading only one of them.</p>
     */
    @Test
    @DisplayName("SH836: a guard is transparent and an iteration is not")
    void onlyBlocksThatRebindNothingAreWalkedOut() {
        assertThat(helperScopes("{{#form}}{{#if required}}{{label}}{{/if}}{{/form}}"))
                .as("""
                        the false positive FORM_FIELD_KEYS was added to stop, reached a second way: \
                        behind a guard the lint asked the LISTING's keys about `label`, did not find \
                        it, and fell to "no post-type anywhere declares this" — on a correct form \
                        body.""")
                .containsExactly("form:required", "form:label");
        assertThat(helperScopes("{{#each ITEMS}}{{#if inner}}{{inner}}{{/if}}{{/each}}"))
                .as("""
                        an iteration rebinds the context, so a reference inside one is a row's — \
                        and `ITEMS` is the block's own argument, resolved OUTSIDE it, which is why \
                        the first entry is the page's and the other two are not.""")
                .containsExactly("null:ITEMS", "each:inner", "each:inner");
        assertThat(helperScopes("{{#with post}}{{#if title}}{{title}}{{/if}}{{/with}}"))
                .as("`with` rebinds too, and is the other one the filed rule got wrong")
                .containsExactly("null:post", "with:title", "with:title");
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
                {{#each ITEMS}}{{@index}} {{this}} {{../outer}}{{/each}}
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

    /**
     * SH832 — {@code ./x} left this list, because it was on it for a reason that was backwards.
     *
     * <p>It sat beside {@code ../outer} as "resolved somewhere this cannot model", and the opposite
     * is true: {@code ./x} is the <b>current</b> context's own {@code x} — the scope the reference is
     * already written in and the only one this scanner ever knows. The ambiguous spelling is the
     * <em>bare</em> {@code x}, which Handlebars resolves up the context chain until something
     * answers, and that fallthrough is the defect SH832 fixed by respelling the generated layout.
     * Skipping the cure would have blinded the field lint on every template the product ships.</p>
     */
    @Test
    @DisplayName("SH832: ./x is the current row's own field, and is reported as one")
    void aRelativePathIsTheCurrentScopesField() {
        assertThat(ShTemplateFieldScanner.referencesIn(
                "{{#each ITEMS}}{{#if ./title}}{{./title}} {{../outer}}{{/if}}{{/each}}", HELPERS))
                .extracting(ShTemplateReference::name, ShTemplateReference::root,
                        ShTemplateReference::scope)
                .as("""
                        the scope is the innermost block the reference sits in, so the guard's own \
                        argument is scoped to the `each` and what it guards is scoped to the `if` — \
                        which is what a bare name did here too, and is not what SH832 changed.""")
                .containsExactly(
                        tuple("ITEMS", "ITEMS", null),
                        tuple("./title", "title", "each"),
                        tuple("./title", "title", "if"));
    }

    // ── SH261: hash arguments ───────────────────────────────────────────────────────

    @Test
    @DisplayName("a hash value that is a path is a field of the enclosing scope")
    void hashPathsAreReported() {
        // The blind spot SH251 shipped with: `q=TOPIC` produced no reference at all, so a typo in
        // the one form SH247 shipped `search` for — a term read from the page's own field — rendered
        // an empty listing with nothing reported.
        assertThat(pageScoped("{{#search q=TOPIC limit=5}}{{title}}{{/search}}"))
                .containsExactly("TOPIC");
        assertThat(pageScoped("{{#query folder=SECTION type=\"Article\"}}{{title}}{{/query}}"))
                .as("`folder=SECTION` is the model-driven form; `type=\"Article\"` is a literal")
                .containsExactly("SECTION");
    }

    @Test
    @DisplayName("a literal, a number and a subexpression are not paths")
    void hashLiteralsAreNotReported() {
        assertThat(pageScoped("""
                {{#query folder="/blog" type='Article' limit=10}}{{title}}{{/query}}
                {{#search q=(concat A B)}}{{title}}{{/search}}"""))
                .as("""
                        Each is excluded for its own reason and none needs cleverness: a quoted                         value is a literal, a numeric one is a number, and a subexpression is a                         parse this deliberately does not do — so it is skipped whole rather than                         descended into.""")
                .isEmpty();
    }

    @Test
    @DisplayName("a quoted value with spaces stays one token")
    void quotedValuesWithSpacesStayWhole() {
        // Splitting on whitespace alone would cut this into `q="two` and `words"`, and the first
        // half is not a path — the right answer for the wrong reason, which stops being right the
        // day somebody writes a subexpression.
        assertThat(pageScoped("{{#search q=\"two words\" folder=SECTION}}{{title}}{{/search}}"))
                .containsExactly("SECTION");
    }

    @Test
    @DisplayName("an inline helper's hash arguments resolve where the call is written")
    void inlineHashPathsAreEnclosingScope() {
        assertThat(pageScoped("<img src=\"{{file_url HERO alt=CAPTION}}\">"))
                .as("both the positional field and the hash one belong to the page, and the "
                        + "helper's own name is still not a field")
                .containsExactlyInAnyOrder("HERO", "CAPTION");
    }

    @Test
    @DisplayName("a hash argument inside a block belongs to that block's rows")
    void hashPathsNestLikeEverythingElse() {
        assertThat(inBlocks("{{#query folder=OUTER}}{{#search q=INNER}}x{{/search}}{{/query}}"))
                .as("the nested helper's hash is resolved where *it* is written, which is inside "
                        + "the outer block — the same rule as a positional argument")
                .containsExactly("query:INNER");
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
