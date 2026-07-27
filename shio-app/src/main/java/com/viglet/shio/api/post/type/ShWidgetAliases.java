/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the widget names other content platforms use onto Shio's catalog (SH80).
 *
 * <p>Rationale — and the reason this exists next to {@code ShDidYouMean} rather
 * than inside it: a caller that guesses {@code "RichText"} is not making a
 * <em>spelling</em> mistake, it is speaking a different vocabulary. Edit distance
 * cannot bridge that ({@code richtext} is 4 edits from {@code text}, further from
 * everything else), so pure string similarity stays silent on precisely the guess
 * an agent is most likely to make. Agents carry the vocabulary of whatever CMS
 * they saw most: Contentful ({@code RichText}, {@code Symbol}), Sanity
 * ({@code string}, {@code reference}, {@code image}, {@code array}), Strapi /
 * Payload ({@code richtext}, {@code media}, {@code relation}, {@code enumeration},
 * {@code boolean}), or plain JSON-schema types.</p>
 *
 * <p>An alias is only ever suggested when its target is present in the installed
 * catalog, so this can never point at a widget the instance does not have.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShWidgetAliases {

    /**
     * Foreign name (normalized) → Shio catalog widget. Insertion-ordered for
     * readability; lookup is by exact normalized key, so order does not affect
     * the result.
     */
    private static final Map<String, String> ALIASES = buildAliases();

    private ShWidgetAliases() {
        // utility
    }

    /**
     * @param sent    the rejected widget name
     * @param catalog the installed widget names
     * @return the aliased catalog widget, if {@code sent} is a known foreign name
     *         <em>and</em> its target is installed
     */
    public static Optional<String> resolve(String sent, Collection<String> catalog) {
        if (sent == null || sent.isBlank() || catalog == null || catalog.isEmpty()) {
            return Optional.empty();
        }
        String target = ALIASES.get(normalize(sent));
        if (target == null) {
            return Optional.empty();
        }
        return catalog.stream().filter(target::equals).findFirst();
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        // Rich text — the single most common mismatch.
        put(aliases, "HTML Editor", "richtext", "rich-text", "richtexteditor", "wysiwyg",
                "html", "markdown", "md", "blockcontent", "portabletext", "lexical", "slate", "prosemirror");
        // Plain single-line text.
        put(aliases, "Text", "string", "symbol", "shorttext", "singleline", "slug", "title",
                "number", "integer", "float", "decimal", "email", "url", "uuid", "line");
        // Multi-line text.
        put(aliases, "Text Area", "longtext", "multiline", "textarea", "paragraph", "summary", "excerpt", "blockquote");
        // Booleans.
        put(aliases, "Check Box", "boolean", "bool", "toggle", "switch", "checkbox");
        // Single choice out of a closed set.
        put(aliases, "Combo Box", "enumeration", "enum", "select", "dropdown", "radio", "singleselect", "option");
        // Multiple values / tags.
        put(aliases, "Multi Select", "tags", "taglist", "multiselect", "checkboxes", "stringarray", "keywords");
        // Dates.
        put(aliases, "Date", "datetime", "timestamp", "dateandtime", "day", "calendar");
        // References to other content.
        put(aliases, "Content Select", "reference", "relation", "relationship", "link", "entryreference",
                "contentreference", "pagereference", "crossreference");
        // Binaries.
        put(aliases, "File", "media", "asset", "image", "img", "picture", "photo", "upload",
                "attachment", "document", "video");
        // Repeatable groups of fields.
        put(aliases, "Relator", "array", "list", "repeater", "component", "blocks", "group",
                "object", "nested", "children", "items", "collection");
        // Layout-only grouping.
        put(aliases, "Tab", "group", "fieldset", "section", "panel", "accordion");
        // Code editors.
        put(aliases, "Ace Editor - Javascript", "javascript", "js", "script", "code", "json");
        put(aliases, "Ace Editor - HTML", "htmlcode", "rawhtml", "embed", "iframe", "snippet");
        // Not user-facing.
        put(aliases, "Hidden", "hidden", "internal", "system", "meta");

        // byte-stable-exempt: a keyed lookup, never iterated and never serialized —
        // resolve() only ever asks it for one alias, so its order is not observable.
        return Map.copyOf(aliases);
    }

    /**
     * First writer wins, so an alias claimed by an earlier (more specific) widget
     * is not stolen by a later one — e.g. {@code group} belongs to {@code Relator}
     * (a repeatable group of fields), not to the layout-only {@code Tab}.
     */
    private static void put(Map<String, String> aliases, String widget, String... foreignNames) {
        for (String foreign : foreignNames) {
            aliases.putIfAbsent(normalize(foreign), widget);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
    }
}
