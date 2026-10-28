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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.render.ShTemplateEngine.ShHelperShape;
import com.viglet.shio.render.ShTemplateValidator.ShTemplateProblem;

/**
 * SH192 — a template fails when it is written, and the refusal says where.
 *
 * <p>A Spring test on purpose, against the <b>real</b> engine. A bare {@code new
 * ShTemplateEngine()} has no helpers registered — {@code ShRenderHelpers} does that at startup
 * — so every legitimate {@code {{#query}}} would be reported as an unknown helper and the
 * validator would look stricter than the renderer. The property that matters here is exactly
 * that the two agree about which helpers exist, and only the wired engine can prove it.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShTemplateValidatorTest {

    @Autowired
    private ShTemplateValidator validator;
    @Autowired
    private ShTemplateEngine engine;

    @Test
    @DisplayName("a sound template produces nothing")
    void aSoundTemplateIsSilent() {
        assertThat(validator.validate("""
                <div>{{#each items}}<p>{{title}}</p>{{/each}}</div>
                {{#query type="Article"}}<a href="{{link}}">{{title}}</a>{{/query}}"""))
                .isEmpty();
    }

    @Test
    @DisplayName("an unclosed block is reported with a caret under the offending character")
    void anUnclosedBlockPointsAtItself() {
        List<ShTemplateProblem> problems = validator.validate("<div>\n  {{#each items}}\n</div>");

        assertThat(problems).hasSize(1);
        ShTemplateProblem problem = problems.get(0);
        assertThat(problem.rule()).isEqualTo("template-syntax");
        assertThat(problem.line()).isEqualTo(3);
        assertThat(problem.window())
                .as("""
                        FSP's regionError printed the line and a caret under the column in 2019, \
                        and that is what P6 asks for — a position alone still means counting.""")
                .contains("</div>")
                .contains("^");
        assertThat(problem.describe()).startsWith("line 3, column ");
    }

    @Test
    @DisplayName("a broken template reports one problem, not a cascade from a broken parse")
    void aBrokenParseStopsThere() {
        // A second finding derived from a parse that already failed is noise on top of the
        // real one, and it buries the position the author needs.
        assertThat(validator.validate("{{#each items}} {{#search q=\"x\"}}")).hasSize(1);
    }

    @Test
    @DisplayName("nothing is known to be missing any more, and the assertion had to invert")
    void nothingIsKnownToBeMissing() {
        // This used to assert `{{#form}}` was named as absent, carrying the SH247 message. All
        // seven of shObject.js' capabilities are registered now, so `KNOWN_MISSING_HELPERS` is
        // empty and a form call is simply a call. The assertion inverts rather than being deleted:
        // a validator that still refused `form` while the renderer executed it is the same
        // author-versus-engine disagreement this class exists to prevent, pointing the other way.
        assertThat(validator.validate(
                "<div>\n{{#form type=\"Contact\"}}<p>{{label}}</p>{{/form}}\n</div>"))
                .as("""
                        the seventh capability is registered, so this is not an unknown helper. \
                        What must never happen is a write-time refusal for a helper the render \
                        then runs happily.""")
                .isEmpty();
    }

    @Test
    @DisplayName("all seven of shObject.js' capabilities are registered")
    void allSevenAreRegistered() {
        // The paramless call is the half the compiler cannot catch — `{{#x}}` with no parameters
        // is a valid section over a context value — so this is the assertion that the scan's list
        // emptied when the last of the seven landed.
        assertThat(validator.validate("{{#form}}{{/form}}")).isEmpty();
        assertThat(validator.validate("{{#getRelation}}{{/getRelation}}")).isEmpty();
        assertThat(validator.validate("{{#search}}{{/search}}")).isEmpty();
    }

    @Test
    @DisplayName("the helpers SH247 registered compile, parameters and all")
    void sh247HelpersAreAccepted() {
        // A parameterised call is the form Handlebars raises `could not find helper` on, so
        // these fail loudly if the registration and the validator ever drift apart.
        assertThat(validator.validate("""
                {{#getRelation SECTIONS}}<section>{{title}}</section>{{/getRelation}}
                {{#search q="handbook" type="Article" limit=5}}<a href="{{link}}">{{title}}</a>\
                {{else}}<p>Nothing found</p>{{/search}}"""))
                .isEmpty();
    }

    @Test
    @DisplayName("SH260: the engine is the one source, and the refusal lists exactly what it holds")
    void theEngineIsTheOneSource() {
        // Five places used to restate the capability set: this refusal's list of choices, its
        // complement within the known-missing seven, the SH251 scanner's two exclusion sets, and the
        // lint's copy of a row's keys. SH247 had to edit three in step to add two helpers, and every
        // failure mode was silent — a registered helper missing from the scanner's list is reported
        // as an unknown *field*, so the rule that catches typos would invent one.
        assertThat(engine.registeredNames())
                .as("sorted and total, because this set is rendered into a message (SH136)")
                .containsExactly("file_url", "form", "getRelation", "image", "menu", "navigation",
                        "query", "search", "t", "translations");

        // A *parameterised* unknown helper. `{{#form}}` used to serve here and cannot any more:
        // it is registered, so it exercises nothing. This is the branch that answers "what may I
        // call", and the answer grew by one when the seventh capability landed.
        ShTemplateProblem problem =
                validator.validate("{{#totallyUnknown q=\"x\"}}y{{/totallyUnknown}}").get(0);
        assertThat(problem.message())
                .as("""
                        the refusal now *asks* the engine rather than restating it, so a helper \
                        added without touching this class still appears in the choices — the drift \
                        that used to show up as a message offering the wrong ones. Alphabetical and \
                        not registration order, which was the hand-written list's accident of \
                        history and is not a fact a reader needs.

                        **And the expectation is derived, not written** (SH382). This assertion used \
                        to hold the joined names as a literal, which made it a sixth restatement of \
                        the capability set — in the one test whose whole subject is that there are \
                        no restatements left. Registering `menu` failed here for exactly that \
                        reason, two assertions after the one above had already been updated.""")
                .contains(String.join(", ", engine.registeredNames()));
    }

    @Test
    @DisplayName("SH260: every registered helper declares how a template calls it")
    void everyHelperDeclaresItsShape() {
        // The shape is what the scanner needs, and the only thing that knows it is the registration.
        // A helper registered without one would default to BLOCK and its argument would go
        // unchecked — visible here rather than as a missing finding three subsystems away.
        assertThat(engine.registeredHelpers())
                .containsEntry("query", ShHelperShape.BLOCK)
                .containsEntry("navigation", ShHelperShape.BLOCK)
                .containsEntry("search", ShHelperShape.BLOCK)
                .containsEntry("getRelation", ShHelperShape.BLOCK_PATH_ARG)
                .containsEntry("file_url", ShHelperShape.INLINE_PATH_ARG)
                .containsEntry("form", ShHelperShape.BLOCK);
        assertThat(engine.registeredHelpers()).hasSameSizeAs(engine.registeredNames());
    }

    @Test
    @DisplayName("a field whose name merely starts with a helper's is not a call")
    void prefixesAreNotCalls() {
        assertThat(validator.validate("{{searchable}} {{formatted}} {{getRelationship}}"))
                .as("the match is on a call, not on a substring — a rule with false positives "
                        + "is a rule authors learn to ignore")
                .isEmpty();
    }

    @Test
    @DisplayName("a legitimate section over a context value is left alone")
    void contextSectionsAreNotFlagged() {
        assertThat(validator.validate("{{#items}}<li>{{name}}</li>{{/items}}"))
                .as("""
                        {{#items}} is an iteration, and there is no way to tell it from a \
                        mistyped helper without the parameter list, which the AST does not \
                        expose. Only the three names that are certainly missing are checked.""")
                .isEmpty();
    }

    @Test
    @DisplayName("only the fields that actually hold a template are validated")
    void onlyTemplateFieldsAreChecked() {
        assertThat(validator.isTemplateField(ShSystemPostType.REGION, ShSystemPostTypeAttr.HTML))
                .isTrue();
        assertThat(validator.isTemplateField(ShSystemPostType.PAGE_LAYOUT, ShSystemPostTypeAttr.HTML))
                .isTrue();
        assertThat(validator.isTemplateField(ShSystemPostType.REGION, ShSystemPostTypeAttr.JAVASCRIPT))
                .as("a region's JavaScript is the browser's (SH248) and is not Handlebars — "
                        + "compiling it would reject perfectly good JS containing braces")
                .isFalse();
        assertThat(validator.isTemplateField("Article", ShSystemPostTypeAttr.HTML))
                .as("an ordinary post's markup is content, not a template")
                .isFalse();
    }

    @Test
    @DisplayName("an empty template is not a broken one")
    void blankIsFine() {
        assertThat(validator.validate(null)).isEmpty();
        assertThat(validator.validate("   ")).isEmpty();
    }

    // --- SH259: the one frame that comes from the reader ---

    /**
     * Every other value a template reaches is content, written by somebody with an account through
     * a surface that records who wrote it — rendering it unescaped is an author's decision about
     * their own markup. `param` is the exception, and a reader is anybody with the URL.
     */
    @Test
    @DisplayName("SH259: an unescaped param is refused at write time, both spellings")
    void anUnescapedParamIsRefused() {
        for (String template : List.of("<h1>{{{param.q}}}</h1>", "<h1>{{& param.q}}</h1>")) {
            assertThat(validator.validate(template))
                    .as(template)
                    .singleElement()
                    .satisfies(problem -> {
                        assertThat(problem.rule()).isEqualTo("param-unescaped");
                        assertThat(problem.message())
                                .as("P6 — the fix is the two-brace form, spelled out")
                                .contains("{{param.q}}");
                    });
        }
    }

    @Test
    @DisplayName("SH259: the escaping default is not flagged, and content still opts out freely")
    void theSafeDefaultAndOrdinaryContentAreLeftAlone() {
        assertThat(validator.validate("<h1>{{param.q}}</h1>"))
                .as("Handlebars escapes this already, so there is nothing to refuse")
                .isEmpty();
        assertThat(validator.validate("<article>{{{TEXT}}}</article>"))
                .as("""
                        an author rendering their own field unescaped is the rich-text case this                         product is for; the rule is about where the value came from, not about                         triple braces""")
                .isEmpty();
    }
}
