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
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.TagType;
import com.github.jknack.handlebars.Template;

/**
 * SH274 — the premise behind {@link ShTemplateFieldScanner}, asserted instead of remembered.
 *
 * <h2>Why this test exists</h2>
 *
 * <p>"Use Handlebars.java's AST rather than a hand-written scanner" was rejected twice, on a premise
 * stated once and inherited since: the AST is internal API, and the public surface
 * ({@code Template#collect}) returns every variable with no indication of which block it sits in —
 * which is the one thing SH251 needs, since {@code {{TEXT}}} at the top level belongs to the page's
 * post-type and the same token inside {@code {{#query}}} belongs to whatever post that block
 * listed.</p>
 *
 * <p>An inherited premise that happens to be right is indistinguishable from one that expired, right
 * up to the moment it costs something. So this <b>asserts</b> it against the version on the
 * classpath. If a future Handlebars gives scope information, this test fails and says to revisit —
 * which is the only mechanism that turns a design decision into one somebody will actually
 * reconsider.</p>
 *
 * <h2>The answer, as of Handlebars.java 4.5.3</h2>
 *
 * <ul>
 *   <li>{@code Template#collect(TagType…)} returns {@code List<String>} and it is
 *       <b>deduplicated</b>: a template with {@code TEXT} at the top level, inside a
 *       {@code {{#query}}} block, as {@code {{&TEXT}}} and as {@code {{{TEXT}}}} yields exactly
 *       {@code [TEXT]}. So it is not merely unscoped — it cannot say how many references there are
 *       or where any of them is, which is strictly weaker than a flat list and much weaker than
 *       what SH251 needs.</li>
 *   <li>{@code Template#collectReferenceParameters()} returns {@code [SECTION]} for
 *       {@code {{#query type="Article" folder=SECTION}}} — the <b>hash path</b> and not the
 *       literal. That is SH261's exact classification, arrived at independently by the library,
 *       which is the most reassuring thing found here. It is still unscoped, so it cannot replace
 *       the scanner; it does confirm the rule.</li>
 *   <li>{@code TagType} does distinguish {@code VAR} / {@code TRIPLE_VAR} / {@code AMP_VAR} /
 *       {@code SUB_EXPRESSION} / {@code SECTION} / {@code START_SECTION} / {@code STAR_VAR}, which is
 *       independent confirmation of SH273's finding that {@code {{&TEXT}}} is a variable and not a
 *       directive.</li>
 *   <li>The nodes that <em>do</em> carry structure ({@code internal.Block}, {@code internal.Variable})
 *       are in an {@code internal} package, reachable only by breaking encapsulation.</li>
 * </ul>
 *
 * <p><b>So the premise holds and the hand-written scanner stays.</b> What the AST would replace is
 * the token walk; what it cannot supply is the scope, which is the whole reason SH251 exists. A
 * hybrid — collect for the names, the scanner for the nesting — buys nothing, because having the
 * nesting is already having the names.</p>
 *
 * @author Alexandre Oliveira
 */
class ShHandlebarsAstPremiseTest {

    /** The same token at two scopes: the page's field, then a listed post's. */
    private static final String TEMPLATE = "<h1>{{TEXT}}</h1>"
            + "{{#query type=\"Article\" folder=SECTION}}<p>{{TEXT}}</p>{{/query}}"
            + "{{&TEXT}}{{{TEXT}}}";

    private static Template compile() throws IOException {
        Handlebars handlebars = new Handlebars();
        // A no-op stand-in: without it `{{#query}}` is a section over a context value, and the
        // question here is what the library reports for a *helper* call.
        handlebars.registerHelper("query", (context, options) -> options.fn(context));
        return handlebars.compileInline(TEMPLATE);
    }

    @Test
    @DisplayName("SH274: collect() returns names with no scope — the premise the scanner rests on")
    void collectHasNoScope() throws IOException {
        List<String> collected = compile().collect(
                TagType.VAR, TagType.TRIPLE_VAR, TagType.AMP_VAR, TagType.STAR_VAR);

        assertThat(collected)
                .as("""
                        The template holds TEXT four times — top level, inside {{#query}}, as \
                        {{&TEXT}} and as {{{TEXT}}} — and this returns ONE entry. Deduplicated, so \
                        it cannot say how many references exist or where any of them is: strictly \
                        weaker than a flat list, and far weaker than the scope SH251 needs. If a \
                        future Handlebars returns four, or carries positions, this fails and SH274 \
                        should be reopened.""")
                .containsExactly("TEXT");
        assertThat(compile().collect(TagType.SECTION, TagType.START_SECTION))
                .as("a section comes back as the helper's name, with nothing about what is inside")
                .containsExactly("query");
    }

    @Test
    @DisplayName("SH274: collectReferenceParameters() is also flat, so it is not the missing piece")
    void referenceParametersAreAlsoFlat() throws IOException {
        // The one API that sounds like it might carry a helper's arguments with their context. It
        // carries the arguments; it does not carry the context.
        assertThat(compile().collectReferenceParameters())
                .as("`folder=SECTION` is here and `type=\"Article\"` is not — the library draws "
                        + "SH261's line between a hash path and a hash literal in exactly the same "
                        + "place, which is independent confirmation of that rule. What it still "
                        + "cannot say is which scope the call sits in, so it cannot replace the "
                        + "scanner.")
                .containsExactly("SECTION");
    }

    @Test
    @DisplayName("SH274: the structural nodes are internal, so reading them means breaking encapsulation")
    void theStructuralNodesAreInternal() {
        // Recorded as an assertion rather than a comment because "it is internal" is the *other*
        // half of the premise, and it is the half most likely to change quietly in a minor release.
        assertThat(com.github.jknack.handlebars.TagType.class.getPackageName())
                .as("the public surface is `com.github.jknack.handlebars`…")
                .isEqualTo("com.github.jknack.handlebars");
        assertThat(classExists("com.github.jknack.handlebars.internal.Block"))
                .as("""
                        …while the node that knows what a block contains is in `.internal`. If this \
                        ever moves out of `internal`, the AST option becomes real and SH274 should \
                        be reopened.""")
                .isTrue();
    }

    @Test
    @DisplayName("SH274: TagType distinguishes the amp form, which is why SH273's fix was right")
    void tagTypeKnowsTheAmpForm() {
        // Independent confirmation from the library itself: `{{&x}}` is a variable, so treating it
        // as a directive (which the scanner did until SH273) dropped a real reference.
        assertThat(TagType.values()).contains(TagType.AMP_VAR, TagType.TRIPLE_VAR, TagType.VAR);
        assertThat(TagType.AMP_VAR.inline())
                .as("inline, like every other variable form")
                .isTrue();
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, ShHandlebarsAstPremiseTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
