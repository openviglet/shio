/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.post.type.ShPostTypeAdminMapper;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.api.post.unified.dto.ShConsolePost;
import com.viglet.shio.api.post.unified.dto.ShConsolePost.ShConsolePostFolder;
import com.viglet.shio.api.post.unified.dto.ShConsolePost.ShConsolePostSite;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Maps a unified post onto the console's read shape (SH176).
 *
 * <p>It composes {@link ShPostTypeAdminMapper} rather than projecting the content model a
 * second time — the console reads the same {@code ShPostTypeDef} the SH16 post-type surface
 * serves, so a widget the authoring shape keeps is a widget the form receives (law P10).</p>
 *
 * <p>Must be called with an open session: {@code shPostType} and {@code shFolder} are LAZY,
 * and fetching them here is the entire point — a detached proxy is what the entity read used
 * to serialize as {@code null}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShConsolePostMapper {

    private final ShPostTypeAdminMapper postTypeMapper;
    private final ObjectMapper objectMapper;

    public ShConsolePostMapper(ShPostTypeAdminMapper postTypeMapper, ObjectMapper objectMapper) {
        this.postTypeMapper = postTypeMapper;
        this.objectMapper = objectMapper;
    }

    public List<ShConsolePost> toConsolePosts(List<ShPostUnified> posts) {
        return posts.stream().map(this::toConsolePost).toList();
    }

    public ShConsolePost toConsolePost(ShPostUnified post) {
        if (post == null) {
            return null;
        }
        ShPostTypeDef postType = postTypeMapper.toDef(post.getShPostType());
        JsonNode data = readData(post.getData());
        return new ShConsolePost(
                post.getId(),
                post.getState() != null ? post.getState().name() : null,
                post.getTitle(),
                post.getSummary(),
                post.getFurl(),
                post.getOwner(),
                post.getModifier(),
                post.getPublisher(),
                post.isPublished(),
                post.getPublishStatus(),
                post.getDate(),
                post.getModifiedDate(),
                post.getPublicationDate(),
                post.getPublishScheduledDate(),
                post.getUnpublishScheduledDate(),
                post.getDeletedDate(),
                post.getLocale(),
                post.getTranslationGroupId(),
                postType,
                folder(post.getShFolder()),
                site(post),
                data,
                undeclaredFields(data, postType));
    }

    private ShConsolePostFolder folder(ShFolder folder) {
        if (folder == null) {
            return null;
        }
        return new ShConsolePostFolder(folder.getId(), folder.getName(), site(ShFolderSite.of(folder)));
    }

    /**
     * The post's site: its own column when set, otherwise walked up from its folder.
     *
     * <p>The fallback is not defensive padding — SH128 produced site-less post rows, and a
     * post whose {@code shSite} is null still has a site the console needs for the
     * breadcrumb and the preview button.</p>
     */
    private ShConsolePostSite site(ShPostUnified post) {
        return post.getShSite() != null ? site(post.getShSite()) : site(ShFolderSite.of(post.getShFolder()));
    }

    private ShConsolePostSite site(ShSite shSite) {
        return shSite == null ? null : new ShConsolePostSite(shSite.getId(), shSite.getName());
    }

    /**
     * The stored {@code data} column as an object. A blank or unparseable column reads as
     * {@code {}} rather than as a string or a null: the form binds to an object, and a post
     * whose data cannot be parsed is still a post whose fields the curator may fix.
     */
    private JsonNode readData(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed != null && parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (JacksonException e) {
            return objectMapper.createObjectNode();
        }
    }

    /** The data keys this post-type does not declare — see {@link ShConsolePost}. */
    private List<String> undeclaredFields(JsonNode data, ShPostTypeDef postType) {
        if (data == null || !data.isObject() || data.isEmpty()) {
            return List.of();
        }
        Set<String> declared = declaredNames(postType);
        List<String> undeclared = new ArrayList<>();
        for (String name : data.propertyNames()) {
            if (!declared.contains(name)) {
                undeclared.add(name);
            }
        }
        return List.copyOf(undeclared);
    }

    /**
     * Every field name the post-type declares, nested ones included: a {@code Tab} groups
     * fields it does not hold data for, and a {@code Relator}'s children are declared one
     * level down, so a flat read of the top level would report a working field as undeclared.
     */
    private Set<String> declaredNames(ShPostTypeDef postType) {
        Set<String> names = new LinkedHashSet<>();
        if (postType != null) {
            collectNames(postType.fields(), names);
        }
        return names;
    }

    private void collectNames(List<ShPostTypeAttrDef> fields, Set<String> into) {
        if (fields == null) {
            return;
        }
        for (ShPostTypeAttrDef field : fields) {
            if (field.name() != null) {
                into.add(field.name());
            }
            collectNames(field.fields(), into);
        }
    }
}
