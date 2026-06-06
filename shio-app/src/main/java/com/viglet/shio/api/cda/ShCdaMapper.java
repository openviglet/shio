/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.cda.dto.ShCdaField;
import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
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
                post.getDate(),
                toInstant(post.getModifiedDate()));
    }

    public ShCdaPost toPost(ShPostUnified post) {
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
                toInstant(post.getModifiedDate()),
                toInstant(post.getPublicationDate()),
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

    private static Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
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
