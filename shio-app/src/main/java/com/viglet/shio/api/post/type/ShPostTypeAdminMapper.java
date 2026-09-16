/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.post.ShFieldRules;
import com.viglet.shio.api.post.type.dto.ShFieldConstraints;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import tools.jackson.databind.ObjectMapper;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;

/**
 * Maps the post-type JPA entities to the lossless {@link ShPostTypeDef} /
 * {@link ShPostTypeAttrDef} DTOs (SH16) for the {@code shio} CLI.
 *
 * <p>Deliberately the inverse of {@code ShCdaMapper.toPostTypeSchema} (SH11):
 * it keeps <em>every</em> attribute (including the layout-only {@code Tab}),
 * carries the raw widget name and verbatim {@code widgetSettings}, and nests
 * child attributes under {@code fields} — so a {@code pull} → edit → {@code push}
 * round-trip preserves the full authoring model.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
public class ShPostTypeAdminMapper {

    /** SH1010 — the constraint block is stored as JSON, and this is what reads it back. */
    private final ObjectMapper objectMapper;

    public ShPostTypeAdminMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ShPostTypeDef toDef(ShPostType postType) {
        if (postType == null) {
            return null;
        }
        return new ShPostTypeDef(
                postType.getName(),
                postType.getTitle(),
                postType.getNamePlural(),
                postType.getDescription(),
                postType.getWorkflowPublishEntity(),
                postType.getSystem() == 1,
                postType.isManaged(),
                // SH768 — delivered as `true` or omitted, never `false`: a reader asks "is this
                // debris", and a key saying "no" on 20 of 22 types is 20 rows of nothing (P3).
                postType.isScratch() ? Boolean.TRUE : null,
                attrDefs(postType.getShPostTypeAttrs(), true));
    }

    public ShPostTypeAttrDef toAttrDef(ShPostTypeAttr attr) {
        if (attr == null) {
            return null;
        }
        return new ShPostTypeAttrDef(
                attr.getName(),
                attr.getLabel(),
                attr.getDescription(),
                attr.getShWidget() != null ? attr.getShWidget().getName() : null,
                attr.getRequired() == 1,
                attr.getIsTitle() == 1,
                attr.getIsSummary() == 1,
                attr.getOrdinal(),
                attr.getWidgetSettings(),
                // SH914 — the indexing declaration, delivered exactly as stored. `indexed` stays a
                // nullable Boolean rather than becoming `false`: the CLI omits a default-valued key,
                // so materializing false here would make every post-type pulled before this read as
                // drift on the next pull (P5).
                attr.getIndexed(),
                attr.getTuringField(),
                attr.getTuringType(),
                // SH1010 — what the field will accept, delivered exactly as stored and absent when
                // nothing is declared: a block materialized as nulls would make every post-type
                // pulled before this read as drift on the next pull (P5), which is the same trap
                // `indexed` describes one comment up.
                constraintsOf(attr),
                attrDefs(attr.getShPostTypeAttrs(), false));
    }

    /**
     * The stored constraint block, parsed, or {@code null} when the field declares none.
     *
     * <p>Parsed rather than passed through as a string, unlike {@code widgetSettings}: a widget's
     * settings are a widget's own business and this surface only carries them, while these are a
     * vocabulary the API declares — so a caller reads typed keys and a malformed block is one
     * server-side problem rather than every client's.</p>
     */
    private ShFieldConstraints constraintsOf(ShPostTypeAttr attr) {
        ShFieldConstraints declared = ShFieldRules.of(attr, objectMapper);
        return declared.declaresNothing() ? null : declared;
    }

    /**
     * Map a set of attributes to ordered defs. When {@code topLevel}, keep only
     * root attributes ({@code shParentPostTypeAttr == null}) — children are
     * emitted nested within their parent. No widget is dropped (unlike SH11).
     */
    private List<ShPostTypeAttrDef> attrDefs(Set<ShPostTypeAttr> attrs, boolean topLevel) {
        if (attrs == null) {
            return List.of();
        }
        return attrs.stream()
                .filter(attr -> !topLevel || attr.getShParentPostTypeAttr() == null)
                // SH722 — the console's field order is a rendering too, and a form whose fields
                // swap places between two loads is the same defect a curator can see.
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                .map(this::toAttrDef)
                .toList();
    }
}
