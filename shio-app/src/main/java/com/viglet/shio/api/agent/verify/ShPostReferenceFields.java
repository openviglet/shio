/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.ArrayList;
import java.util.List;

import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Which {@code Multi Select} fields hold <em>references to posts</em> rather than tags
 * (SH154/SH169).
 *
 * <h2>The problem this settles</h2>
 *
 * <p>SH95's section vocabulary composes a page from a {@code Multi Select} of section friendly
 * URLs, in render order. Two shipped features could not see it: the SH99 lint's
 * {@code dangling-reference} rule knows a {@code Content Select} holding a post id and a
 * {@code File} holding an asset id, and the SH101 digest reads a post's own markup. So a page
 * could name a section that does not exist and {@code verify} stayed green, and a page built out
 * of five sections digested as the hash of nothing.</p>
 *
 * <p>Neither could simply treat every {@code Multi Select} value as a URL: the widget is a
 * free-form list of strings by design and most of them are tags, so that rule would report a
 * defect for every tag on every post. The field has to <b>say</b> that its values are addresses,
 * and it says it in the model — the one place both a lint and a digest already read:</p>
 *
 * <pre>
 * "widgetSettings": "{\"multiselect\":{\"references\":\"post\"}}"
 * </pre>
 *
 * <h2>Why a new key and not {@code multiselect.postType}</h2>
 *
 * <p>{@code postType} already exists and is nearly this, but it says <em>which</em> type, and a
 * page's sections are deliberately heterogeneous — hero, feature grid, FAQ. Overloading it would
 * mean either naming one type and lying, or leaving it blank and having the two readers guess.
 * {@code references} states the fact both of them need; {@code postType} may still narrow it, and
 * is honoured when present.</p>
 *
 * <p>A convention instead — "a field named {@code sections}" — was the alternative, and it is
 * unenforceable: nothing can check prose, and a second vocabulary would have to pick the same
 * word by luck.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShPostReferenceFields {

    /** The {@code widgetSettings} value that marks a Multi Select as holding post URLs. */
    public static final String REFERENCES_POST = "post";

    private ShPostReferenceFields() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Whether this attribute's values are friendly URLs of posts.
     *
     * @param mapper used to read {@code widgetSettings}, which is stored as a JSON string
     */
    public static boolean referencesPosts(ShPostTypeAttr attr, ObjectMapper mapper) {
        if (attr == null || attr.getShWidget() == null
                || !ShSystemWidget.MULTI_SELECT.equals(attr.getShWidget().getName())) {
            return false;
        }
        JsonNode settings = settings(attr.getWidgetSettings(), mapper);
        JsonNode multiselect = settings == null ? null : settings.get("multiselect");
        JsonNode references = multiselect == null ? null : multiselect.get("references");
        return references != null && references.isString()
                && REFERENCES_POST.equalsIgnoreCase(references.asString().trim());
    }

    /** The post-type these references must be, or {@code null} when any type will do. */
    public static String referencedPostType(ShPostTypeAttr attr, ObjectMapper mapper) {
        JsonNode settings = settings(attr == null ? null : attr.getWidgetSettings(), mapper);
        JsonNode multiselect = settings == null ? null : settings.get("multiselect");
        JsonNode postType = multiselect == null ? null : multiselect.get("postType");
        return postType != null && postType.isString() && !postType.asString().isBlank()
                ? postType.asString().trim()
                : null;
    }

    /**
     * The URLs a value holds, in declared order.
     *
     * <p>Accepts an array of strings (what the widget stores) and a lone string (what a
     * hand-written document or an older client may send), because a reader that understood only
     * one of them would report a defect for a shape the write surface accepts.</p>
     */
    public static List<String> urls(JsonNode value) {
        List<String> urls = new ArrayList<>();
        if (value == null) {
            return urls;
        }
        if (value.isString()) {
            add(urls, value.asString());
            return urls;
        }
        if (value.isArray()) {
            for (JsonNode entry : value) {
                if (entry != null && entry.isString()) {
                    add(urls, entry.asString());
                }
            }
        }
        return urls;
    }

    private static void add(List<String> urls, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String trimmed = raw.trim();
        urls.add(trimmed.startsWith("/") ? trimmed : "/" + trimmed);
    }

    /**
     * {@code widgetSettings} as a tree, or {@code null}.
     *
     * <p>The column holds the literal string {@code "null"} on some rows, and
     * {@code readTree("null")} answers a JSON null node rather than failing — the same trap the
     * console's {@code parseSettings} guards. A non-object is treated as absent.</p>
     */
    private static JsonNode settings(String raw, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = mapper.readTree(raw);
            return parsed != null && parsed.isObject() ? parsed : null;
        } catch (JacksonException e) {
            return null;
        }
    }
}
