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

import com.viglet.shio.utils.ShStableMap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH389 — the scripts that belong to the <b>site</b>, in one order, in one place.
 *
 * <h2>The defect</h2>
 *
 * <p>Every real site carries three or four scripts that are the site's and not a page's: a consent
 * banner, an analytics tag, a chat widget, a tag manager. Shio had two places to put browser
 * JavaScript — a {@code Theme}'s {@code JAVASCRIPT} and a {@code Region}'s (SH248) — and both are
 * per-layout or per-region. A site with four layouts therefore held <b>four copies</b>, and the load
 * order was a property of each copy. Consent has to load <em>before</em> what it gates, which makes
 * that a compliance defect rather than duplication.
 *
 * <h2>The ownership question SH389 was filed on, and the answer</h2>
 *
 * <p>The idea named a real risk: the renderer is preview-grade and {@code no-store}, production
 * delivery is Next.js over the CDA (P10), and <b>a consent implementation that behaves differently in
 * the two is worse than one that exists only in delivery</b>.
 *
 * <p>So this <b>stores and exposes; it never injects</b>. There is no code path that puts a script tag
 * into a page the layout did not ask for. A layout emits {@code {{#each site.scripts.head}}}, the
 * delivery layer reads the same post through the CDA, and the one thing that could have diverged — an
 * injected banner behaving one way in the preview and another in production — does not exist. What the
 * task actually asked for is the <em>single ordered list</em>, and that is what this is: four layouts
 * now read one order instead of holding four.
 *
 * <h2>Why a JSON field and not a Relator</h2>
 *
 * <p>{@code Theme.TOKENS} settled this exact question in SH195: the widget catalogue has no JSON
 * editor, JSON is valid JavaScript, so the field is {@code Ace Editor - Javascript} and holds JSON. A
 * Relator would have needed a nested field spec {@code ShRenderPostTypes} does not have, for a value
 * whose whole meaning is <b>its order</b> — which a JSON array carries and a set of sibling posts does
 * not.
 *
 * <h2>Three lists rather than one with a position field</h2>
 *
 * <p>Because a template has to be able to place them, and Handlebars cannot compare two strings without
 * a helper. One list plus {@code {{#if}}} on a position would need {@code {{#if_eq}}}, which does not
 * exist and which SH260's closed capability set is not going to grow for this. Three named lists —
 * {@code head}, {@code bodyStart}, {@code bodyEnd} — are placeable with the {@code {{#each}}} every
 * template already uses, and the order <em>within</em> each is the JSON's, which is the property the
 * whole task is about.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShSiteScripts {

    /** Where a script may be placed. The default is the one that cannot break a page's paint. */
    private static final String HEAD = "head";
    private static final String BODY_START = "bodyStart";
    private static final String BODY_END = "bodyEnd";
    private static final List<String> POSITIONS = List.of(HEAD, BODY_START, BODY_END);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShSiteScripts() {
        throw new IllegalStateException("static");
    }

    /**
     * The three lists a template sees, from the {@code SCRIPTS} field's JSON.
     *
     * <p>Every position is present even when empty, so {@code {{#each site.scripts.bodyEnd}}} in a
     * layout is never an expression that resolves to nothing — SH183's rule that a template naming
     * something outside the model resolves to empty is fine for a *field*, and a silent nothing where
     * a list was expected is how an author concludes the feature does not work.
     *
     * <p><b>A malformed field throws</b>, exactly as {@code Theme.TOKENS} does (SH195): the caller
     * catches it, records a CONTENT incident and renders without the scripts. Degrading is right — a
     * page must not fail over a tag manager — and degrading <em>silently</em> is what SH270 forbids.
     */
    public static Map<String, Object> of(String json) {
        Map<String, List<Map<String, Object>>> byPosition = new LinkedHashMap<>();
        for (String position : POSITIONS) {
            byPosition.put(position, new ArrayList<>());
        }
        if (json != null && !json.isBlank()) {
            // Deliberately not caught here. See the note above and `ShThemeTokens.parse`: this is a
            // pure function and the incident log is a bean, so the exception travels to the renderer,
            // which catches it beside the recorder.
            JsonNode root = MAPPER.readTree(json);
            if (root != null && root.isArray()) {
                for (JsonNode entry : root) {
                    add(byPosition, entry);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String position : POSITIONS) {
            out.put(position, List.copyOf(byPosition.get(position)));
        }
        return ShStableMap.copyOf(out);
    }

    /**
     * One entry, or nothing.
     *
     * <p>An entry with neither {@code src} nor {@code inline} is <b>skipped</b> rather than emitted as
     * an empty tag — a `&lt;script src=""&gt;` re-requests the page itself in some browsers, which is
     * a defect much worse than the missing script. {@code shio verify}'s
     * {@code site-scripts-entry-empty} is where it is reported, which is the standing split: the
     * renderer degrades, the lint explains.
     *
     * <p>An unknown {@code position} falls back to {@code bodyEnd}, not to {@code head}. A typo must
     * not move a script <em>earlier</em> than the author wrote — that is the direction that breaks a
     * consent ordering, and the end of the body is the position that cannot block a paint.
     */
    private static void add(Map<String, List<Map<String, Object>>> byPosition, JsonNode entry) {
        if (entry == null || !entry.isObject()) {
            return;
        }
        String src = text(entry, "src");
        String inline = text(entry, "inline");
        if (src.isEmpty() && inline.isEmpty()) {
            return;
        }
        String position = text(entry, "position");
        String slot = POSITIONS.contains(position) ? position : BODY_END;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("src", src);
        row.put("inline", inline);
        row.put("defer", entry.path("defer").asBoolean(false));
        row.put("async", entry.path("async").asBoolean(false));
        // The consent category the script belongs to. Shio does not act on it — acting on it is what
        // "never inject" rules out — but a layout that gates its own emission needs to be able to
        // read it, and a capture that recorded it must have somewhere to put it.
        row.put("category", text(entry, "category"));
        row.put("position", slot);
        byPosition.get(slot).add(ShStableMap.copyOf(row));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isValueNode() && !value.isNull() ? value.asString().trim() : "";
    }

    /** The positions a template may name — read by {@code verify} rather than restated (SH260). */
    public static List<String> positions() {
        return POSITIONS;
    }
}
