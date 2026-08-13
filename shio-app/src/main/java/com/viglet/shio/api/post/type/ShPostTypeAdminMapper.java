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

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
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
                attrDefs(attr.getShPostTypeAttrs(), false));
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
