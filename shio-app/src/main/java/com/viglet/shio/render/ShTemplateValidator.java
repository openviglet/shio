/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

/**
 * SH192 — a template fails when it is <b>written</b>, not when somebody looks at the page.
 *
 * <h2>The cost this removes</h2>
 *
 * <p>{@code post.upsert} refuses a field the post-type does not declare (SH168) and has no
 * opinion about the 1330 characters of markup inside one. So an unclosed {@code {{#each}}}, or
 * a call to a helper that does not exist, is accepted, stored, published — and discovered as a
 * blank page. <b>Every iteration then costs a guess</b>, which is precisely the loop this
 * block exists to make cheap. A renderer that only reports failure at view time has moved the
 * error message to the least useful moment.</p>
 *
 * <h2>The reporting standard is this product's own, from 2019</h2>
 *
 * <p>FSP's {@code regionError} printed the file, the line, the column and a caret under the
 * offending character. That is what law P6 asks for and it was there before any of this. The
 * position comes free from {@link ShTemplateException}; what this adds is the
 * <em>caret window</em>, because "line 14, column 22" still means counting.</p>
 *
 * <h2>What it checks, and what it deliberately cannot</h2>
 *
 * <p><b>Syntax</b> — compiled, so every malformed expression and unclosed block is caught with
 * no false positives.</p>
 *
 * <p><b>The helpers that are known to be missing</b> — {@code search}, {@code form} and
 * {@code getRelation} are three of {@code shObject.js}' seven and are deliberately
 * unregistered (SH247). A template calling one is certainly wrong, and saying so at write time
 * is much better than Handlebars' own behaviour, which is to treat {@code {{#search}}} as a
 * section over a context value named {@code search} and render nothing at all.</p>
 *
 * <p><b>Not unknown sections in general.</b> {@code {{#items}}…{{/items}}} is a legitimate
 * iteration over a context value, and there is no way to tell it from a mistyped helper
 * without the parameter list, which the AST does not expose. A rule that fired on both would
 * be a rule authors learn to ignore — worse than none.</p>
 *
 * <p><b>Not unknown field references.</b> §XV.10 wanted these to be errors "when the post-type
 * is known", and at write time it is not: a Region's own post-type declares {@code HTML} and
 * {@code JAVASCRIPT}, while the fields the template interpolates belong to whatever
 * <em>page</em> is later bound to the layout that slots it. That check needs a resolved page,
 * so it belongs to a lint pass over rendered pages rather than to a write.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShTemplateValidator {

    /**
     * The helpers a template can reasonably expect and not get. Named explicitly rather than
     * derived from "everything not registered", because the general form has false positives and
     * this one has none.
     *
     * <p>Down to one since SH247 shipped {@code getRelation} and {@code search}. {@code form} is
     * the last of the seven, and it is the one whose absence is a <em>decision</em> rather than a
     * gap: a form needs somewhere for a submission to go, which is a subsystem and not a helper.
     * When it ships, this set becomes empty and the whole scan can go with it — the compiler
     * already raises an unknown parameterised helper on its own, and that is the only half this
     * exists to cover.</p>
     */
    private static final Set<String> KNOWN_MISSING_HELPERS = Set.of("form");

    /** What {@link ShRenderHelpers} does register, named so a refusal can list the choices. */
    private static final List<String> REGISTERED =
            List.of("query", "navigation", "file_url", "getRelation", "search");

    /** Handlebars.java's own wording, which this re-words rather than passes through. */
    private static final String HELPER_NOT_FOUND = "could not find helper:";

    /** Which fields of which system post-type hold a template. */
    private static final Map<String, Set<String>> TEMPLATE_FIELDS = Map.of(
            ShSystemPostType.PAGE_LAYOUT, Set.of(ShSystemPostTypeAttr.HTML),
            ShSystemPostType.REGION, Set.of(ShSystemPostTypeAttr.HTML));

    private final ShTemplateEngine engine;

    public ShTemplateValidator(ShTemplateEngine engine) {
        this.engine = engine;
    }

    /**
     * @param postTypeName the post-type being written
     * @param field        the field name
     * @return whether that field is compiled as a template, and therefore worth validating
     */
    public boolean isTemplateField(String postTypeName, String field) {
        Set<String> fields = TEMPLATE_FIELDS.get(postTypeName);
        return fields != null && fields.contains(field);
    }

    /**
     * @param template the Handlebars source
     * @return every problem found, in source order; empty when the template is sound
     */
    public List<ShTemplateProblem> validate(String template) {
        if (template == null || template.isBlank()) {
            return List.of();
        }
        List<ShTemplateProblem> problems = new ArrayList<>();
        try {
            engine.compile(template);
        } catch (ShTemplateException e) {
            problems.add(fromCompileFailure(template, e));
            // A template that does not parse has no reliable structure to scan further, and a
            // second finding derived from a broken parse is noise on top of the real one.
            return problems;
        }
        problems.addAll(missingHelpers(template));
        return problems;
    }

    /**
     * Classifies a compile failure.
     *
     * <p>Handlebars.java catches an unknown helper itself — but <b>only when it is called with
     * parameters</b>, because {@code {{#search q="x"}}} cannot be anything else while
     * {@code {{#search}}} is a valid section over a context value. So the compiler covers the
     * unambiguous half and {@link #missingHelpers} covers the rest; splitting it this way
     * means no rule has to guess.</p>
     *
     * <p>Its message is re-worded rather than passed through. "could not find helper: 'search'"
     * is accurate and leaves the author looking for a typo, when the truth is that the helper
     * was one of the seven the 2019 engine had and has not been reimplemented — which is a
     * different problem with a different answer.</p>
     */
    private ShTemplateProblem fromCompileFailure(String template, ShTemplateException e) {
        String helper = unknownHelperIn(e.getMessage());
        if (helper == null) {
            return new ShTemplateProblem("template-syntax", e.getMessage(),
                    e.getLine(), e.getColumn(), window(template, e.getLine(), e.getColumn()));
        }
        // The compiler does not always carry a position for this one, so fall back to finding
        // the call in the source — a rule that reports "line 0" has given up on being useful.
        int[] pos = e.getLine() > 0
                ? new int[] { e.getLine(), e.getColumn() }
                : firstCallPosition(template, helper);
        return new ShTemplateProblem("template-unknown-helper", describeMissing(helper),
                pos[0], pos[1], window(template, pos[0], pos[1]));
    }

    private String unknownHelperIn(String message) {
        if (message == null) {
            return null;
        }
        int at = message.indexOf(HELPER_NOT_FOUND);
        if (at < 0) {
            return null;
        }
        String rest = message.substring(at + HELPER_NOT_FOUND.length()).trim();
        if (!rest.startsWith("'")) {
            return null;
        }
        int close = rest.indexOf('\'', 1);
        return close < 0 ? null : rest.substring(1, close);
    }

    private String describeMissing(String helper) {
        if (KNOWN_MISSING_HELPERS.contains(helper)) {
            return "There is no `" + helper + "` helper. It was one of the seven capabilities the "
                    + "2019 engine exposed and it has not been reimplemented (SH247); "
                    + "Handlebars would treat this as a section over a context value named `"
                    + helper + "` and render nothing.";
        }
        return "There is no `" + helper + "` helper. The registered set is closed: "
                + String.join(", ", REGISTERED) + ", plus Handlebars' own built-ins.";
    }

    /**
     * Scans for {@code {{#name}}} / {@code {{name}}} where {@code name} is one of the three
     * helpers this engine is known not to have. Textual rather than AST-driven because the
     * AST does not distinguish a helper call from a context iteration; the names are exact,
     * so the match is too.
     */
    private List<ShTemplateProblem> missingHelpers(String template) {
        List<ShTemplateProblem> problems = new ArrayList<>();
        Map<String, int[]> found = new LinkedHashMap<>();
        for (String helper : KNOWN_MISSING_HELPERS) {
            for (String opener : new String[] { "{{#" + helper, "{{" + helper }) {
                int at = template.indexOf(opener);
                if (at < 0) {
                    continue;
                }
                // Only a call: `{{#search q="x"}}` or `{{search}}`, never `{{searchable}}`.
                char next = at + opener.length() < template.length()
                        ? template.charAt(at + opener.length()) : '}';
                if (Character.isLetterOrDigit(next) || next == '_' || next == '-') {
                    continue;
                }
                found.putIfAbsent(helper, positionOf(template, at));
            }
        }
        found.forEach((helper, pos) -> problems.add(new ShTemplateProblem(
                "template-unknown-helper", describeMissing(helper),
                pos[0], pos[1], window(template, pos[0], pos[1]))));
        return problems;
    }

    /** Where {@code helper} is first called, for the compile errors that carry no position. */
    private int[] firstCallPosition(String template, String helper) {
        for (String opener : new String[] { "{{#" + helper, "{{" + helper }) {
            int at = template.indexOf(opener);
            if (at >= 0) {
                return positionOf(template, at);
            }
        }
        return new int[] { 0, 0 };
    }

    private int[] positionOf(String template, int index) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < index; i++) {
            if (template.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new int[] { line, column };
    }

    /**
     * The offending line with a caret under the column — FSP's {@code regionError} output,
     * which is the difference between a position and a place an author can look at.
     */
    private String window(String template, int line, int column) {
        if (line <= 0) {
            return null;
        }
        String[] lines = template.split("\n", -1);
        if (line > lines.length) {
            return null;
        }
        String source = lines[line - 1];
        int caret = Math.max(1, Math.min(column, source.length() + 1));
        return source + "\n" + " ".repeat(caret - 1) + "^";
    }

    /**
     * @param rule    a stable slug a caller can branch on, matching the verify rule names
     * @param message what is wrong
     * @param line    1-based
     * @param column  1-based
     * @param window  the offending line plus a caret, or {@code null} when the position is
     *                not known well enough to point at anything
     */
    public record ShTemplateProblem(String rule, String message, int line, int column, String window) {

        /** One line, position first — "unclosed block" without it means reading it all again. */
        public String describe() {
            String at = line > 0 ? "line " + line + ", column " + column + " — " : "";
            return at + message + (window == null ? "" : "\n" + window);
        }
    }
}
