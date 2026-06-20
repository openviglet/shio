/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;

/**
 * Request DTO for the console post-type write endpoints
 * ({@code POST}/{@code PUT} {@code /api/v2/post/type}), introduced to stop
 * binding the persistent {@link ShPostType} entity (and its recursive
 * {@link ShPostTypeAttr} graph) directly to {@code @RequestBody}
 * (SonarQube {@code java:S4684}).
 *
 * <p>The records mirror exactly the entity-shaped JSON the React post-type
 * editor sends — the same fields {@link ShPostTypeConsoleWriteService} reads
 * off the payload. To preserve all of that service's cascade-safety and
 * widget-resolution logic untouched, {@link #toShPostType()} rehydrates a
 * transient {@link ShPostType} carrying only those read fields; the service
 * then consumes it exactly as before. Nothing here is persisted directly —
 * the service rebuilds managed instances from these values.</p>
 *
 * <p>Fields read by the service (see {@code ShPostTypeConsoleWriteService}):
 * post-type {@code name}, {@code namePlural}, {@code title},
 * {@code description}, {@code workflowPublishEntity}, {@code shPostTypeAttrs};
 * per attr {@code name}, {@code label}, {@code description}, {@code ordinal},
 * {@code required}, {@code isTitle}, {@code isSummary}, {@code widgetSettings},
 * {@code shWidget} (id, name) and the nested {@code shPostTypeAttrs}.</p>
 *
 * @author Alexandre Oliveira
 */
public record ShPostTypeRequest(
        String name,
        String namePlural,
        String title,
        String description,
        String workflowPublishEntity,
        List<ShPostTypeAttrRequest> shPostTypeAttrs) {

    /** Attribute node mirroring the recursive {@code shPostTypeAttrs} tree. */
    public record ShPostTypeAttrRequest(
            String name,
            String label,
            String description,
            Integer ordinal,
            Integer required,
            Integer isTitle,
            Integer isSummary,
            String widgetSettings,
            ShWidgetRef shWidget,
            List<ShPostTypeAttrRequest> shPostTypeAttrs) {

        ShPostTypeAttr toShPostTypeAttr() {
            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName(name);
            attr.setLabel(label);
            attr.setDescription(description);
            attr.setOrdinal(ordinal != null ? ordinal : 0);
            attr.setRequired(required != null ? (byte) (int) required : 0);
            attr.setIsTitle(isTitle != null ? (byte) (int) isTitle : 0);
            attr.setIsSummary(isSummary != null ? (byte) (int) isSummary : 0);
            attr.setWidgetSettings(widgetSettings);
            attr.setShWidget(shWidget != null ? shWidget.toShWidget() : null);
            attr.setShPostTypeAttrs(toShPostTypeAttrs(shPostTypeAttrs));
            return attr;
        }
    }

    /** Widget reference posted by the editor (resolved by the service to a managed row). */
    public record ShWidgetRef(String id, String name) {
        ShWidget toShWidget() {
            ShWidget widget = new ShWidget();
            widget.setId(id);
            widget.setName(name);
            return widget;
        }
    }

    /** Rehydrate a transient post-type carrying the fields the service reads. */
    ShPostType toShPostType() {
        ShPostType postType = new ShPostType();
        postType.setName(name);
        postType.setNamePlural(namePlural);
        postType.setTitle(title);
        postType.setDescription(description);
        postType.setWorkflowPublishEntity(workflowPublishEntity);
        postType.setShPostTypeAttrs(toShPostTypeAttrs(shPostTypeAttrs));
        return postType;
    }

    private static Set<ShPostTypeAttr> toShPostTypeAttrs(List<ShPostTypeAttrRequest> attrs) {
        Set<ShPostTypeAttr> result = new LinkedHashSet<>();
        if (attrs == null) {
            return result;
        }
        for (ShPostTypeAttrRequest src : attrs) {
            if (src != null) {
                result.add(src.toShPostTypeAttr());
            }
        }
        return result;
    }
}
