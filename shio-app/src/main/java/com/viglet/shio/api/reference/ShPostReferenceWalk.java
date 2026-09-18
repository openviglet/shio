/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.reference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The references a document declares, read through its post-type (SH1034).
 *
 * <h2>Why a walk and not the substring it replaces</h2>
 *
 * <p>{@code findReferencingFileId} asked whether an id appears <em>anywhere</em> in a post's
 * {@code data}. That is cheap to write, costs a table scan to run (SH1033: 50,002 rows examined at
 * fifty thousand posts), and is <b>less accurate</b> than reading the model: it cannot tell a
 * modelled reference from a UUID that happens to sit in a body, and it cannot say which field
 * holds the edge, so a finding could never name what to fix.</p>
 *
 * <p>This reads the fields a type <em>declares</em> as {@code File} or {@code Content Select} —
 * the two widgets that store another object's id as a plain string — and takes their values. The
 * cost is bounded by the post's own field count and not by anything the table controls, which is
 * the second of the two curves SH1033 measured: three statements per write, identical at fifty
 * thousand rows and at zero.</p>
 *
 * <h2>What it deliberately does not read</h2>
 *
 * <p>A {@code Multi Select} marked {@code references: post} holds friendly <em>URLs</em>, not ids
 * (SH154/SH169, {@code ShPostReferenceFields}). Those are addresses, so an index of them would go
 * stale on every {@code post.move} — the defect {@code ShPostLinks} exists to describe — and they
 * are a different question from "which object is this". Rich-text bodies are excluded for the same
 * reason and one more: the link in prose names an address and not an object, which SH140 settled.
 * So this index answers <b>"which posts point at this object"</b> and not "what links here".</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShPostReferenceWalk {

    /** The two widgets that store another object's id as a plain string. */
    private static final Set<String> REFERENCE_WIDGETS =
            Set.of(ShSystemWidget.FILE, ShSystemWidget.CONTENT_SELECT);

    private ShPostReferenceWalk() {
        throw new IllegalStateException("Utility class");
    }

    /** One edge, before it becomes a row. */
    public record Edge(String toObjectId, String fieldName) {
    }

    /**
     * Whether this attribute's value is another object's id.
     *
     * <p>Here rather than at each caller, so there is one answer to "is this a reference field" —
     * the rule {@code ShPostReferenceFields} follows for the {@code Multi Select} question, applied
     * to this one.</p>
     */
    public static boolean holdsReference(ShPostTypeAttr attr) {
        return attr != null && attr.getShWidget() != null
                && REFERENCE_WIDGETS.contains(attr.getShWidget().getName());
    }

    /**
     * The edges a document declares, deduplicated, in field order.
     *
     * <p>Deduplicated on {@code (id, field)} rather than on the id alone: two different fields
     * pointing at one asset are two facts a curator wants to see, and the same field repeating an
     * id is not. Blank and non-string values are skipped rather than stored — a {@code File} field
     * that has never been filled holds {@code ""}, and a row saying a post references the empty
     * string would make {@code asset-unused} answer with it.</p>
     *
     * @param data   the post's {@code data} column, which may be null or the literal {@code "null"}
     * @param fields the post-type's attributes; only reference-holding ones are read
     */
    public static List<Edge> edges(String data, Iterable<ShPostTypeAttr> fields,
            ObjectMapper mapper) {
        List<Edge> edges = new ArrayList<>();
        ObjectNode document = document(data, mapper);
        if (document == null || fields == null) {
            return edges;
        }
        // A Set of the record itself, rather than a joined string key: `Edge` has value equality,
        // and every separator available for a composite key is either a legal character in a field
        // name or a control byte the repository's own lints refuse in source (SH804).
        Set<Edge> seen = new LinkedHashSet<>();
        for (ShPostTypeAttr attr : fields) {
            if (!holdsReference(attr) || attr.getName() == null) {
                continue;
            }
            JsonNode value = document.get(attr.getName());
            for (String id : ids(value)) {
                Edge edge = new Edge(id, attr.getName());
                if (seen.add(edge)) {
                    edges.add(edge);
                }
            }
        }
        return edges;
    }

    /**
     * The ids a value holds — a lone string, or an array of them.
     *
     * <p>Both shapes, because both are written: a {@code File} stores one id and a
     * {@code Content Select} configured for many stores a list, and a reader that understood one of
     * them would silently index half the edges.</p>
     */
    private static List<String> ids(JsonNode value) {
        List<String> ids = new ArrayList<>();
        if (value == null) {
            return ids;
        }
        if (value.isString()) {
            add(ids, value.asString());
        } else if (value.isArray()) {
            for (JsonNode entry : value) {
                if (entry != null && entry.isString()) {
                    add(ids, entry.asString());
                }
            }
        }
        return ids;
    }

    /**
     * Ids only — and this is a correctness rule, not defensiveness.
     *
     * <p>The {@code File} post type declares its own {@code FILE} attribute with the {@code File}
     * widget, and that field holds the asset's <b>file name</b> rather than a reference to
     * anything: {@code ShStaticFileService} writes {@code data.FILE = "logo.png"}. So a walk that
     * took every {@code File} field's value indexed each asset as pointing at its own name — which
     * is not merely noise. It made {@code asset-unused} answer that a genuinely unreferenced asset
     * is referenced, and it broke the write outright the first time a name was longer than the 36
     * characters an id occupies ({@code inter-greek-wght-normal-CkhJZR-_.woff2}, 38, which is how
     * this was found).</p>
     *
     * <p>An id is a UUID everywhere in this schema — {@code VARCHAR(36)} on every table — so a
     * value that cannot be an id cannot be a reference. Checking the shape is therefore reading
     * the contract rather than guessing at one, and it keeps {@code to_object_id} at the width the
     * thing it stores actually has.</p>
     */
    private static void add(List<String> ids, String raw) {
        if (raw == null) {
            return;
        }
        String trimmed = raw.trim();
        if (isObjectId(trimmed)) {
            ids.add(trimmed);
        }
    }

    /** A 36-character, canonically hyphenated UUID — what every id column in this schema holds. */
    private static boolean isObjectId(String value) {
        if (value.length() != 36) {
            return false;
        }
        for (int index = 0; index < 36; index++) {
            char character = value.charAt(index);
            boolean hyphenExpected = index == 8 || index == 13 || index == 18 || index == 23;
            if (hyphenExpected != (character == '-')) {
                return false;
            }
            if (!hyphenExpected && Character.digit(character, 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@code data} as an object, or {@code null}.
     *
     * <p>The {@code agents.md} invariant in Java: the column can hold the literal string
     * {@code "null"}, and parsing that yields a JSON null node which passes a truthy check and
     * fails on the first property access. A non-object is treated as absent.</p>
     */
    private static ObjectNode document(String data, ObjectMapper mapper) {
        if (data == null || data.isBlank()) {
            return null;
        }
        JsonNode parsed = mapper.readTree(data);
        return parsed instanceof ObjectNode node ? node : null;
    }
}
