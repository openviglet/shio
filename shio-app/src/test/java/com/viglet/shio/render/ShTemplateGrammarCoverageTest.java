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
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.render.ShTemplateEngine.ShHelperShape;
import com.viglet.shio.render.ShTemplateFieldScanner.ShHelperShapes;
import com.viglet.shio.render.ShTemplateFieldScanner.ShTemplateReference;

/**
 * SH273 — what {@link ShTemplateFieldScanner} does with <b>every</b> construct in the grammar.
 *
 * <h2>Why a table and not more examples</h2>
 *
 * <p>The scanner reads Handlebars' syntax by hand: mustaches, block nesting, triple stashes,
 * whitespace control, unescaped output, positional arguments and hash pairs. Each of those was added
 * on its own merits and the total is a small parser of somebody else's grammar, maintained against
 * no specification.</p>
 *
 * <p><b>The risk is silent under-reporting.</b> A construct the scanner does not recognise yields no
 * reference, so the SH251 lint quietly stops covering it and nothing goes red. That is exactly what
 * {@code {{~TEXT~}}} did — a real field dropped — and what {@code {{&TEXT}}} did until this table was
 * written. Neither was found by a failing test; both were found by looking. So the coverage becomes
 * the assertion: every construct is listed with what happens to it, and a construct nobody has
 * decided about cannot sit in the file unnoticed.</p>
 *
 * <p>This is SH255's "exact, or declared" applied to a parser instead of a format, and it is also how
 * the alternative gets evaluated honestly: with the table in place, "would Handlebars.java's AST give
 * us more than this" is a question with an answer.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTemplateGrammarCoverageTest {

    private static final ShHelperShapes HELPERS = ShHelperShapes.of(Map.of(
            "query", ShHelperShape.BLOCK,
            "getRelation", ShHelperShape.BLOCK_PATH_ARG,
            "file_url", ShHelperShape.INLINE_PATH_ARG));

    /** What the scanner is expected to do with a construct. */
    private enum Disposition {

        /** The field name is reported at the page scope. */
        REPORTED,

        /** Nothing is reported, and that is correct — it is not a field reference. */
        SKIPPED,

        /**
         * Nothing is reported and the construct <em>could</em> hold a field, but something else
         * makes it unreachable — so the omission costs nothing.
         */
        UNREACHABLE
    }

    private record Construct(String syntax, Disposition disposition, String why) {
    }

    /**
     * Every construct Handlebars' grammar has, with what this scanner does about it.
     *
     * <p>Adding a construct here is a decision. {@code UNREACHABLE} needs the reason it cannot
     * matter — not "we did not get to it", which is what {@code REPORTED} is for once somebody
     * does.</p>
     */
    private static final List<Construct> GRAMMAR = List.of(
            new Construct("{{TEXT}}", Disposition.REPORTED, "the plain form"),
            new Construct("{{{TEXT}}}", Disposition.REPORTED, "triple stash: escaping, not resolution"),
            new Construct("{{& TEXT}}", Disposition.REPORTED,
                    "unescaped output, the same reference as a triple stash — dropped until SH273"),
            new Construct("{{~TEXT~}}", Disposition.REPORTED,
                    "whitespace control: dropped until SH251's own test noticed"),
            new Construct("{{  TEXT  }}", Disposition.REPORTED, "padding is not part of the path"),
            new Construct("{{TEXT.title}}", Disposition.REPORTED,
                    "a dotted path, reported by its root — which is how the lint tells a field from "
                            + "a reserved frame like `post.` or `site.`"),
            new Construct("{{#if TEXT}}x{{/if}}", Disposition.REPORTED,
                    "a builtin block's first positional argument, in the enclosing scope"),
            new Construct("{{#getRelation TEXT}}x{{/getRelation}}", Disposition.REPORTED,
                    "a registered helper declared BLOCK_PATH_ARG (SH260)"),
            new Construct("{{file_url TEXT}}", Disposition.REPORTED,
                    "an inline helper's argument; the helper's own name is not a field"),
            new Construct("{{#query folder=TEXT}}x{{/query}}", Disposition.REPORTED,
                    "a hash value that is a path (SH261)"),
            new Construct("{{^unless TEXT}}x{{/unless}}", Disposition.REPORTED,
                    "an inverted section is still a block with an argument"),

            new Construct("{{! TEXT }}", Disposition.SKIPPED, "a comment"),
            new Construct("{{!-- TEXT --}}", Disposition.SKIPPED, "a long comment"),
            new Construct("{{@TEXT}}", Disposition.SKIPPED, "a data variable, not a field"),
            new Construct("{{this}}", Disposition.SKIPPED, "the context itself"),
            new Construct("{{.}}", Disposition.SKIPPED, "the context itself, short form"),
            // SH832 — was SKIPPED with "explicitly relative; the lint cannot say whose scope it
            // lands in", which had it backwards: `./TEXT` is the ONE spelling whose scope is not in
            // doubt. It is the current context's own `TEXT`, and a *bare* `TEXT` is the ambiguous
            // one — Handlebars resolves that up the chain, which is the defect SH832 fixed. The
            // generated layout is spelled this way now, so skipping it would blind the lint on the
            // templates the product ships.
            new Construct("{{./TEXT}}", Disposition.REPORTED,
                    "the current context's own field, which is what the scope it is written in "
                            + "already says"),
            new Construct("{{../TEXT}}", Disposition.SKIPPED,
                    "a parent reference; same reason, one level out"),
            new Construct("{{lookup a TEXT}}", Disposition.SKIPPED,
                    "a builtin whose arguments this does not model"),
            new Construct("{{#if (eq A TEXT)}}x{{/if}}", Disposition.SKIPPED,
                    "a subexpression, skipped whole rather than parsed (SH261)"),
            new Construct("{{#query q=\"TEXT\"}}x{{/query}}", Disposition.SKIPPED,
                    "a quoted hash value is a literal"),
            new Construct("{{#query limit=10}}x{{/query}}", Disposition.SKIPPED,
                    "a numeric hash value is a number"),
            new Construct("{{\"TEXT\"}}", Disposition.SKIPPED, "a string literal"),
            new Construct("{{42}}", Disposition.SKIPPED, "a number literal"),
            new Construct("{{#query}}x{{else}}y{{/query}}", Disposition.SKIPPED,
                    "`else` is a keyword, not a name"),
            new Construct("{{=<% %>=}}", Disposition.SKIPPED,
                    "a Mustache delimiter change; Handlebars.java does not support it"),

            new Construct("{{> TEXT}}", Disposition.UNREACHABLE,
                    "a partial — SH183's TemplateLoader refuses every one, so a template using it "
                            + "fails to compile and SH192 reports it before this rule runs"),
            new Construct("{{#> TEXT}}x{{/TEXT}}", Disposition.UNREACHABLE,
                    "a partial block; unreachable for the same reason"),
            new Construct("{{#*inline \"TEXT\"}}x{{/inline}}", Disposition.UNREACHABLE,
                    "a decorator; the loader refuses the partial it exists to define"),
            new Construct("{{#each items as |TEXT|}}x{{/each}}", Disposition.UNREACHABLE,
                    "a block parameter *names* a local rather than referencing a field, so there is "
                            + "nothing to resolve against a post-type"));

    @Test
    @DisplayName("SH273: every construct in the grammar has a declared disposition")
    void everyConstructIsDecided() {
        for (Construct construct : GRAMMAR) {
            List<String> reported = ShTemplateFieldScanner.referencesIn(construct.syntax(), HELPERS)
                    .stream()
                    // By root, not by name: a dotted path (`TEXT.title`) is a reference to TEXT,
                    // and root() is exactly what the SH251 lint compares against a post-type.
                    .map(ShTemplateReference::root)
                    .filter("TEXT"::equals)
                    .toList();

            if (construct.disposition() == Disposition.REPORTED) {
                assertThat(reported)
                        .as("""
                                `%s` should report TEXT (%s). A construct that stops being reported \
                                is a field the SH251 lint silently stops covering — the failure \
                                direction that never goes red.""",
                                construct.syntax(), construct.why())
                        .containsExactly("TEXT");
            } else {
                assertThat(reported)
                        .as("""
                                `%s` should report nothing (%s). If this now reports TEXT, either \
                                the scanner gained a false positive or this row's reasoning has \
                                stopped being true — and a rule with false positives is one authors \
                                learn to ignore.""",
                                construct.syntax(), construct.why())
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("SH273: every UNREACHABLE row says what makes it unreachable")
    void unreachableRowsCarryTheirReason() {
        // The point of the distinction: SKIPPED means "not a field", UNREACHABLE means "could be a
        // field, and something else already prevents it mattering". The second is a claim about
        // another part of the system and rots if that part changes — SH183's refusing TemplateLoader
        // is what three of these rest on — so it has to be written down rather than assumed.
        assertThat(GRAMMAR)
                .filteredOn(construct -> construct.disposition() == Disposition.UNREACHABLE)
                .isNotEmpty()
                .allSatisfy(construct -> assertThat(construct.why())
                        .as("`%s` is declared unreachable with no reason", construct.syntax())
                        .isNotBlank()
                        .hasSizeGreaterThan(30));
    }

    @Test
    @DisplayName("SH273: the table covers the constructs the scanner actually branches on")
    void theTableCoversEveryBranch() {
        // A weak but real guard against the table going stale while the scanner grows: every marker
        // the scanner special-cases has to appear somewhere in the table's syntax column. It cannot
        // prove completeness against Handlebars' grammar — nothing here can — but it does fail when
        // somebody adds a branch and no row.
        List<String> markers = List.of("{{{", "{{~", "{{&", "{{!", "{{>", "{{#", "{{^", "{{/", "{{=",
                "{{@", "as |", "(", "=\"", "else");
        for (String marker : markers) {
            assertThat(GRAMMAR)
                    .as("no row exercises `%s`, so nobody has decided what the scanner does with it",
                            marker)
                    .anySatisfy(construct -> assertThat(construct.syntax()).contains(marker));
        }
    }
}
