/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.LinkedHashMap;
import java.util.Map;

import com.viglet.shio.render.ShRenderModel.ShThemeAssets;
import com.viglet.shio.utils.ShStableMap;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH195 — a theme's design tokens, declared as data and compiled to CSS custom properties.
 *
 * <h2>The problem: the commonest change is the least reviewable one</h2>
 *
 * <p>A {@code Theme} carried {@code CSS} and {@code JAVASCRIPT} as opaque blobs, so "tighten the
 * headings", "change the accent colour" and "more density" were all stylesheet rewrites. Nothing
 * declared what may be changed, nothing verified the result, a diff showed a changed string, and an
 * agent editing raw CSS by string replacement is the least reliable thing in the whole loop. A
 * declared vocabulary turns that into a <b>field edit</b>: reviewable in the console, diffable in
 * the file projection, and safe for an agent because the set of names is closed.</p>
 *
 * <h2>Adopted, not invented — which was the open question</h2>
 *
 * <p>§XV.13 left one thing undecided: whose vocabulary. Inventing one is a design project and
 * produces a dialect nothing else reads, so the declaration is the <b>W3C Design Tokens (DTCG)</b>
 * shape — {@code {"color":{"accent":{"$value":"#0b5","$type":"color"}}}} — which is what Style
 * Dictionary, Figma and Tokens Studio already export and import. A designer's export pastes in.</p>
 *
 * <p>The runtime form is <b>CSS custom properties</b> on {@code :root}: {@code color.accent}
 * becomes {@code --color-accent}. Every stylesheet, every browser and every devtool already reads
 * those, so nothing new has to learn anything, and <b>raw CSS stays the escape hatch rather than
 * the interface</b> — which is the sentence the task was filed on.</p>
 *
 * <h2>Three decisions worth stating, because each had a plausible alternative</h2>
 *
 * <p><b>The block is prepended to the theme's own CSS</b> rather than exposed as a separate
 * {@code {{theme.tokens.css}}} for layouts to place. A separate handle would mean every existing
 * layout renders without tokens until somebody edits it — the feature would ship switched off, on a
 * template most authors will not know to change. Prepending puts the declarations before the rules
 * that use them, which is also the order CSS needs.</p>
 *
 * <p><b>A malformed value is skipped; a malformed file degrades at the caller.</b> A token whose
 * {@code $value} has no single CSS form is skipped here. A file that is not JSON at all throws, and
 * the renderer catches it, records a CONTENT incident and renders on the theme's CSS alone — one
 * degradation, reported once, in the class that can report.</p>
 *
 * <p><b>A malformed value is skipped, not refused.</b> A token file is content; refusing to render a
 * whole page because one token is an object where a string was expected would take the site down for
 * a typo. What is skipped is the token, and {@code shio verify} is where a rule about it belongs —
 * the same split SH183 makes between a missing layout (a refusal) and a missing region (a marker).</p>
 *
 * <p><b>The flattened map is what templates see</b> ({@code {{theme.tokens.color.accent}}} through
 * the dotted key {@code color.accent}), so a layout can put a token in an attribute or an inline
 * style where a custom property will not reach — an email template, an SVG {@code fill}. Same value,
 * two forms, one source.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShThemeTokens {

    /** DTCG's value key. The {@code $} prefix is the spec's, not a convention of ours. */
    private static final String VALUE = "$value";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShThemeTokens() {
        throw new IllegalStateException("static");
    }

    /**
     * A theme's assets, with its tokens parsed and compiled into the stylesheet.
     *
     * @param tokensJson the {@code TOKENS} field, DTCG JSON, or {@code null}
     * @param css        the {@code CSS} field, or {@code null}
     * @param javascript the {@code JAVASCRIPT} field, or {@code null}
     */
    public static ShThemeAssets assets(String tokensJson, String css, String javascript) {
        Map<String, String> tokens = parse(tokensJson);
        if (tokens.isEmpty()) {
            // Not even an empty `:root{}`: a theme with no tokens must render byte-identically to
            // how it rendered before this existed, or every digest in the instance moves for a
            // feature nobody used (SH101's hash is over exactly these bytes).
            // Empty, so there is nothing to keep in order and the stability helper would be a
            // guarantee about no elements (SH197).
            return new ShThemeAssets(css, javascript, Map.of());
        }
        StringBuilder out = new StringBuilder(":root{");
        tokens.forEach((name, value) -> out.append("--").append(customProperty(name))
                .append(':').append(value).append(';'));
        out.append('}');
        if (css != null && !css.isBlank()) {
            out.append('\n').append(css);
        }
        return new ShThemeAssets(out.toString(), javascript, ShStableMap.copyOf(tokens));
    }

    /**
     * DTCG JSON → dotted name to value, in document order.
     *
     * <p>Nested groups become a dotted path; a node carrying {@code $value} is a token and stops the
     * walk. Anything else — a string where a group was expected, a {@code $value} that is an object
     * (DTCG's composite types, which have no single CSS form) — is skipped rather than guessed at.</p>
     */
    private static Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        // Deliberately not caught here. A token file that is not JSON must degrade — the page is
        // served without tokens rather than refused — but degrading *silently* is what SH270 forbids,
        // and this class cannot report: it is a pure function and the incident log is a bean. So the
        // exception travels to `ShPageRenderService.themeOf`, which catches it beside the recorder.
        // `ShSilentCatchInventoryTest` is what turned that from a preference into a decision.
        JsonNode root = MAPPER.readTree(json);
        Map<String, String> flat = new LinkedHashMap<>();
        walk("", root, flat, 0);
        return flat;
    }

    /** @param depth guards a hand-written file with a cycle-shaped nesting mistake */
    private static void walk(String prefix, JsonNode node, Map<String, String> into, int depth) {
        if (node == null || !node.isObject() || depth > 8) {
            return;
        }
        JsonNode value = node.get(VALUE);
        if (value != null) {
            if (value.isValueNode() && !value.isNull()) {
                into.put(prefix, value.asString());
            }
            return;
        }
        for (String name : node.propertyNames()) {
            // `$description`, `$type` and any other spec metadata at group level: not tokens.
            if (name.startsWith("$")) {
                continue;
            }
            walk(prefix.isEmpty() ? name : prefix + "." + name, node.get(name), into, depth + 1);
        }
    }

    /**
     * {@code color.accent} → {@code color-accent}, the conventional CSS custom property spelling.
     *
     * <p>Anything outside {@code [A-Za-z0-9-]} becomes {@code -}: a custom property name is not
     * quotable, so a token called {@code font size} would emit a declaration the browser drops
     * silently, taking the rest of the {@code :root} block with it in some parsers.</p>
     */
    private static String customProperty(String name) {
        return name.replaceAll("[^A-Za-z0-9-]+", "-");
    }
}
