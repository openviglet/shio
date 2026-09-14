/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import com.viglet.shio.persistence.model.history.ShHistory;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Which fields of a post one write changed, by name and never by value (SH989).
 *
 * <h2>Why names and not values</h2>
 *
 * <p>The values already have a home: SH986's revisions hold every state that went live, and a
 * rich-text body is the most expensive thing on the surface to print. An audit row that carried
 * them would be a second revision store with a worse retention story. The names are what a reviewer
 * reads to decide whether to look further, and they are what was lost at the moment of approval:
 * publishing copies the draft over the live row, so the draft-against-published comparison the
 * review queue offers is empty exactly when the run has just been accepted.</p>
 *
 * <h2>One comparison, two readers</h2>
 *
 * <p>{@link com.viglet.shio.api.post.revision.ShPostRevisionService} asks the same question of two
 * revisions, so both call {@link #between}. Two copies that agreed today would be two answers the
 * first time one of them learned to treat a {@code null} value differently.</p>
 *
 * <h2>The column is bounded, and the bound is the writer's (SH544's lesson)</h2>
 *
 * <p>Field names are the keys of a post's JSON and nothing constrains them, so the stored form is
 * a JSON array rather than a comma list, and {@link #encode} keeps it inside
 * {@link ShHistory#CHANGED_FIELDS_MAX_LENGTH}. A list too long for the column keeps the names that
 * fit and ends on {@code …+N}, the count of names left out. The alternative was a 500 on the write:
 * an audit row must never decide whether the change it describes happened.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShChangedFields {

    /** Ends a list the column could not hold; the number after it is how many names were left out. */
    public static final String MORE = "…+";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShChangedFields() {
        // utility
    }

    /**
     * The names whose values differ between two documents, sorted.
     *
     * <p>A document that is absent or not an object counts as empty, so against nothing every field
     * is new — the rule a first publish and the oldest revision both need. A field removed by the
     * write is a change as much as one added.</p>
     */
    public static List<String> between(JsonNode before, JsonNode after) {
        if (after == null || !after.isObject()) {
            return List.of();
        }
        Set<String> names = new TreeSet<>();
        after.propertyNames().forEach(names::add);
        if (before != null && before.isObject()) {
            before.propertyNames().forEach(names::add);
        }
        List<String> changed = new ArrayList<>();
        for (String name : names) {
            JsonNode left = before == null ? null : before.get(name);
            if (!Objects.equals(left, after.get(name))) {
                changed.add(name);
            }
        }
        return List.copyOf(changed);
    }

    /** {@link #between} over two stored documents; unparseable text reads as empty. */
    public static List<String> between(String before, String after) {
        return between(parse(before), parse(after));
    }

    /**
     * The column's form: a JSON array that never exceeds the column.
     *
     * <p>Measured in UTF-16 units, which is never fewer than the characters a {@code VARCHAR}
     * counts, so a list that fits here fits there.</p>
     *
     * @return {@code null} for {@code null} — not recorded — and {@code []} for a write that
     *         changed nothing, which is a different statement
     */
    public static String encode(List<String> names) {
        if (names == null) {
            return null;
        }
        String whole = write(names);
        int max = ShHistory.CHANGED_FIELDS_MAX_LENGTH;
        if (whole.length() <= max) {
            return whole;
        }
        // The longest prefix that still leaves room for the marker. Lengths are summed rather than
        // re-serialized per candidate: a document with thousands of keys is the case that gets here.
        int used = 1; // "["
        int kept = 0;
        for (String name : names) {
            int cost = write(name).length() + 1; // the value and the comma after it
            String marker = write(MORE + (names.size() - kept - 1));
            if (used + cost + marker.length() + 1 > max) { // + "]"
                break;
            }
            used += cost;
            kept++;
        }
        List<String> bounded = new ArrayList<>(names.subList(0, kept));
        bounded.add(MORE + (names.size() - kept));
        return write(bounded);
    }

    /** The names a row recorded, or {@code null} when the row recorded none. */
    public static List<String> decode(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        JsonNode node = parse(stored);
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> names = new ArrayList<>(node.size());
        node.forEach(each -> names.add(each.asString()));
        return List.copyOf(names);
    }

    private static JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException e) {
            // A document that cannot be parsed has no fields to name; the write it belongs to has
            // already been decided elsewhere, and this must not be what refuses it.
            return null;
        }
    }

    private static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
