/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.cda.dto.ShCdaField;
import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.cda.dto.ShCdaSchemaField;
import com.viglet.shio.api.cda.dto.ShCdaSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Maps persistence entities into the frozen CDA read DTOs (SH1).
 *
 * <p>Centralizing the mapping is what lets the JPA entities evolve without
 * breaking the public contract, and is the single place where the
 * authoring-only fields ({@code owner}, {@code modifier}, {@code publisher},
 * {@code publishStatus}) are dropped from the delivery surface.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
public class ShCdaMapper {

    private static final byte ROOT_FOLDER = 1;

    /** Widgets whose delivered value is an array of scalars (SH11). */
    private static final Set<String> ARRAY_WIDGETS = Set.of("Check Box", "Multi Select");
    /** Layout-only widgets that carry no delivered value — omitted from the schema (SH11). */
    private static final Set<String> CONTAINER_WIDGETS = Set.of("Tab");

    private final ObjectMapper objectMapper;

    public ShCdaMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ShCdaSite toSite(ShSite site, String rootFolderId) {
        if (site == null) {
            return null;
        }
        return new ShCdaSite(site.getId(), site.getName(), site.getDescription(), site.getUrl(), rootFolderId);
    }

    public ShCdaFolder toFolder(ShFolder folder) {
        if (folder == null) {
            return null;
        }
        return new ShCdaFolder(folder.getId(), folder.getName(), folder.getRootFolder() == ROOT_FOLDER);
    }

    public ShCdaPostSummary toSummary(ShPostUnified post) {
        if (post == null) {
            return null;
        }
        return new ShCdaPostSummary(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                post.getFurl(),
                postTypeName(post),
                post.getLocale(),
                post.getDate(),
                post.getModifiedDate());
    }

    public ShCdaPost toPost(ShPostUnified post) {
        return toPost(post, java.util.List.of());
    }

    /**
     * Full post including the SH53 locale axis. {@code availableLocales} lists
     * the locales this content is translated into (computed by the service from
     * the translation group) so a headless front can render a language switcher.
     */
    public ShCdaPost toPost(ShPostUnified post, java.util.List<String> availableLocales) {
        if (post == null) {
            return null;
        }
        return new ShCdaPost(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                post.getFurl(),
                postTypeName(post),
                post.getShSite() != null ? post.getShSite().getId() : null,
                post.getShFolder() != null ? post.getShFolder().getId() : null,
                post.isPublished(),
                post.getDate(),
                post.getModifiedDate(),
                post.getPublicationDate(),
                post.getLocale(),
                availableLocales != null ? availableLocales : java.util.List.of(),
                parseAttrs(post.getData()));
    }

    private String postTypeName(ShPostUnified post) {
        return post.getShPostType() != null ? post.getShPostType().getName() : null;
    }

    /**
     * The post-type's editable fields (SH10) — top-level attributes only
     * ({@code shParentPostTypeAttr == null}; Relator children are edited within
     * their parent), ordered by {@code ordinal}. Backs the editor properties panel.
     */
    public List<ShCdaField> toFields(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal))
                .map(this::toField)
                .toList();
    }

    public ShCdaField toField(ShPostTypeAttr attr) {
        if (attr == null) {
            return null;
        }
        return new ShCdaField(
                attr.getName(),
                attr.getLabel(),
                attr.getDescription(),
                attr.getShWidget() != null ? attr.getShWidget().getName() : null,
                attr.getRequired() == 1,
                attr.getIsTitle() == 1,
                attr.getIsSummary() == 1,
                attr.getOrdinal());
    }

    /**
     * The full content model of a post-type (SH11) — its API name plus every
     * top-level, value-bearing attribute as a {@link ShCdaSchemaField}, ordered
     * by {@code ordinal}. Backs {@code shio-types} code generation.
     */
    public ShCdaPostTypeSchema toPostTypeSchema(ShPostType postType) {
        if (postType == null) {
            return null;
        }
        return new ShCdaPostTypeSchema(
                postType.getName(),
                postType.getTitle(),
                postType.getNamePlural(),
                postType.getDescription(),
                schemaFields(postType.getShPostTypeAttrs(), true));
    }

    /**
     * One schema field, recursing into a Relator's sub-attributes. {@code multiple}
     * is true for a Relator (its delivered value is an item list) or an
     * array-valued widget ({@code Check Box} / {@code Multi Select}).
     */
    public ShCdaSchemaField toSchemaField(ShPostTypeAttr attr) {
        if (attr == null) {
            return null;
        }
        String widget = attr.getShWidget() != null ? attr.getShWidget().getName() : null;
        List<ShCdaSchemaField> children = schemaFields(attr.getShPostTypeAttrs(), false);
        boolean multiple = !children.isEmpty() || (widget != null && ARRAY_WIDGETS.contains(widget));
        return new ShCdaSchemaField(
                attr.getName(),
                attr.getLabel(),
                attr.getDescription(),
                widget,
                attr.getRequired() == 1,
                multiple,
                attr.getIsTitle() == 1,
                attr.getIsSummary() == 1,
                attr.getOrdinal(),
                children);
    }

    /**
     * Map a set of attributes to ordered schema fields, dropping container-only
     * widgets ({@code Tab}). When {@code topLevel}, also drops Relator children
     * (those are emitted nested within their parent).
     */
    private List<ShCdaSchemaField> schemaFields(Set<ShPostTypeAttr> attrs, boolean topLevel) {
        if (attrs == null) {
            return List.of();
        }
        return attrs.stream()
                .filter(attr -> !topLevel || attr.getShParentPostTypeAttr() == null)
                .filter(attr -> !isContainer(attr))
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal))
                .map(this::toSchemaField)
                .toList();
    }

    private static boolean isContainer(ShPostTypeAttr attr) {
        return attr != null && attr.getShWidget() != null
                && CONTAINER_WIDGETS.contains(attr.getShWidget().getName());
    }

    /**
     * Parse the {@code data} CLOB into a JSON node. A {@code null}, blank, or
     * malformed payload yields an empty object — never a 500 (acceptance
     * criterion 5).
     */
    private JsonNode parseAttrs(String data) {
        if (data == null || data.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(data);
            return (node != null && node.isObject()) ? node : objectMapper.createObjectNode();
        } catch (RuntimeException e) {
            return objectMapper.createObjectNode();
        }
    }
}
