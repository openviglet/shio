/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.migration.unified;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Post-type schema builder for the unified model.
 *
 * <p>Since the EAV tables ({@code sh_post_attr}, {@code sh_post_draft_attr})
 * are gone, there is nothing to backfill into {@code sh_post_unified} on a
 * fresh install. What remains is projecting each {@link ShPostType}'s
 * {@link ShPostTypeAttr} graph into a JSON schema so the frontend can render
 * the dynamic form from a single column instead of joining metadata tables
 * on every render.</p>
 */
@Service
public class ShPostUnifiedMigrationService {

    private static final Log logger = LogFactory.getLog(ShPostUnifiedMigrationService.class);

    private final ShPostTypeRepository shPostTypeRepository;
    private final ObjectMapper objectMapper;

    public ShPostUnifiedMigrationService(ShPostTypeRepository shPostTypeRepository,
                                         ObjectMapper objectMapper) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MigrationResult migrateAll() {
        int postTypesMigrated = 0;
        for (ShPostType postType : shPostTypeRepository.findAll()) {
            postType.setSchemaJson(buildSchemaJson(postType));
            shPostTypeRepository.save(postType);
            postTypesMigrated++;
        }
        logger.info("Unified schema projection done: postTypes=" + postTypesMigrated);
        return new MigrationResult(postTypesMigrated);
    }

    private String buildSchemaJson(ShPostType postType) {
        ArrayNode arr = objectMapper.createArrayNode();
        Set<ShPostTypeAttr> attrs = postType.getShPostTypeAttrs();
        if (attrs != null) {
            List<ShPostTypeAttr> sorted = new ArrayList<>(attrs);
            sorted.sort(Comparator.comparingInt(ShPostTypeAttr::getOrdinal));
            for (ShPostTypeAttr a : sorted) {
                if (a.getShParentPostTypeAttr() != null) continue;
                arr.add(attrToSchemaNode(a));
            }
        }
        try {
            return objectMapper.writeValueAsString(arr);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize post-type schema to JSON", e);
        }
    }

    private ObjectNode attrToSchemaNode(ShPostTypeAttr a) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", a.getName());
        node.put("label", a.getLabel());
        if (a.getDescription() != null) node.put("description", a.getDescription());
        node.put("ordinal", a.getOrdinal());
        node.put("required", a.getRequired() == 1);
        node.put("isTitle", a.getIsTitle() == 1);
        node.put("isSummary", a.getIsSummary() == 1);
        ShWidget widget = a.getShWidget();
        if (widget != null) node.put("widget", widget.getName());
        if (a.getWidgetSettings() != null) node.put("widgetSettings", a.getWidgetSettings());

        Set<ShPostTypeAttr> nested = a.getShPostTypeAttrs();
        if (nested != null && !nested.isEmpty()) {
            ArrayNode children = node.putArray("children");
            List<ShPostTypeAttr> sortedNested = new ArrayList<>(nested);
            sortedNested.sort(Comparator.comparingInt(ShPostTypeAttr::getOrdinal));
            for (ShPostTypeAttr child : sortedNested) {
                children.add(attrToSchemaNode(child));
            }
        }
        return node;
    }

    public record MigrationResult(int postTypesMigrated) {
    }
}
