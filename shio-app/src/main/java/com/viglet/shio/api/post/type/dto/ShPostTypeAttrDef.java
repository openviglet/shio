/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type.dto;

import java.util.List;

/**
 * Lossless, round-trippable description of one post-type attribute (SH16),
 * consumed by the {@code shio} CLI's {@code pull}/{@code push} (post-types as
 * code).
 *
 * <p>Unlike the delivery-shaped {@code ShCdaSchemaField} (SH11) — which drops
 * {@code Tab} fields, collapses every scalar to {@code multiple}/non-multiple
 * and never carries {@code widgetSettings} — this DTO preserves everything the
 * authoring model needs to be re-applied: the raw widget name (including
 * container widgets such as {@code Tab}) and the verbatim {@code widgetSettings}
 * JSON. Relator (and {@code Tab}) children are nested under {@code fields}.</p>
 *
 * <p>The entity {@code id} is intentionally omitted — a post-type is identified
 * by its API {@code name}, and ids are server-assigned on {@code push}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShPostTypeAttrDef(
        String name,
        String label,
        String description,
        String widget,
        boolean required,
        boolean isTitle,
        boolean isSummary,
        int ordinal,
        String widgetSettings,
        Boolean indexed,
        String turingField,
        String turingType,
        List<ShPostTypeAttrDef> fields) {

    /**
     * The ten-field form: every caller before SH914 made a field declarable as indexed.
     *
     * <p>Kept rather than migrated at the call sites, and the reason is the CLI's canonical
     * serialization. {@code cli/src/post-types-io.mjs} materializes every other key with its
     * default — which is what makes two defs comparable by string — but omits these three when the
     * field is not indexed, so a {@code shio/post-types/*.json} written before this round-trips
     * byte-identically (P5). Three nulls through this constructor is what makes the Java side agree
     * with that: absent and {@code false} mean the same thing here, and neither writes a key.</p>
     */
    public ShPostTypeAttrDef(
            String name,
            String label,
            String description,
            String widget,
            boolean required,
            boolean isTitle,
            boolean isSummary,
            int ordinal,
            String widgetSettings,
            List<ShPostTypeAttrDef> fields) {
        this(name, label, description, widget, required, isTitle, isSummary, ordinal,
                widgetSettings, null, null, null, fields);
    }

    /** Whether this field is sent to Turing in a field of its own. Absent means no (SH914). */
    public boolean declaresIndexed() {
        return Boolean.TRUE.equals(indexed);
    }

    /** The Turing field name this lands in — the attribute's own name unless one is given. */
    public String resolvedTuringField() {
        return turingField == null || turingField.isBlank() ? name : turingField;
    }
}
