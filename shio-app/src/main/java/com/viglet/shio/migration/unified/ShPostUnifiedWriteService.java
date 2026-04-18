/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.migration.unified;

import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.object.ShObjectPublishStatus;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.post.unified.ShPostUnifiedId;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Stand-alone writer for the unified post model. No legacy bridge — every
 * create / update / publish / unpublish / delete writes directly against
 * {@code sh_post_unified} and its composite key.
 */
@Service
public class ShPostUnifiedWriteService {

    private final ShFolderRepository shFolderRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;

    public ShPostUnifiedWriteService(ShFolderRepository shFolderRepository,
                                     ShSiteRepository shSiteRepository,
                                     ShPostTypeRepository shPostTypeRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository,
                                     ObjectMapper objectMapper) {
        this.shFolderRepository = shFolderRepository;
        this.shSiteRepository = shSiteRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
    }

    private ShFolder resolveContainerFolder(String containerId) {
        return shFolderRepository.findById(containerId).orElseGet(() -> {
            ShSite site = shSiteRepository.findById(containerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Folder or site not found: " + containerId));
            return shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Site " + containerId + " has no root folder"));
        });
    }

    @Transactional
    public ShPostUnified create(CreateRequest req) {
        ShFolder folder = resolveContainerFolder(req.folderId());
        ShPostType postType = shPostTypeRepository.findById(req.postTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Post type not found: " + req.postTypeId()));

        ShPostState state = req.state() != null ? req.state() : ShPostState.DRAFT;

        ShPostUnified row = new ShPostUnified();
        row.setState(state);
        row.setShFolder(folder);
        row.setShSite(folder.getShSite());
        row.setShPostType(postType);
        row.setOwner(req.owner());
        row.setDate(new Date().toInstant());
        row.setModifiedDate(new Date());
        row.setPublished(state == ShPostState.PUBLISHED);
        row.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH : ShObjectPublishStatus.DRAFT);
        if (state == ShPostState.PUBLISHED) row.setPublicationDate(new Date());

        String dataJson = writeJson(req.data() != null ? req.data() : objectMapper.createObjectNode());
        row.setData(dataJson);

        applyTitleAndSummary(row, req.data(), postType);

        return shPostUnifiedRepository.save(row);
    }

    @Transactional
    public ShPostUnified update(String postId, UpdateRequest req) {
        ShPostState state = req.state() != null ? req.state() : ShPostState.DRAFT;
        ShPostUnified row = shPostUnifiedRepository.findByIdAndState(postId, state)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Post not found: " + postId + " state=" + state));

        if (req.data() != null) {
            row.setData(writeJson(req.data()));
            applyTitleAndSummary(row, req.data(), row.getShPostType());
        }
        row.setModifiedDate(new Date());
        if (req.modifier() != null) row.setModifier(req.modifier());
        row.setPublished(state == ShPostState.PUBLISHED);
        row.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH : ShObjectPublishStatus.DRAFT);

        return shPostUnifiedRepository.save(row);
    }

    @Transactional
    public ShPostUnified publish(String postId, String publisher) {
        ShPostUnified draft = shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + postId));

        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                .ifPresent(shPostUnifiedRepository::delete);

        ShPostUnified pub = new ShPostUnified();
        pub.setId(postId);
        pub.setState(ShPostState.PUBLISHED);
        pub.setShFolder(draft.getShFolder());
        pub.setShSite(draft.getShSite());
        pub.setShPostType(draft.getShPostType());
        pub.setTitle(draft.getTitle());
        pub.setSummary(draft.getSummary());
        pub.setFurl(draft.getFurl());
        pub.setOwner(draft.getOwner());
        pub.setDate(draft.getDate());
        pub.setModifiedDate(new Date());
        pub.setPublisher(publisher);
        pub.setPublicationDate(new Date());
        pub.setPublished(true);
        pub.setPublishStatus(ShObjectPublishStatus.PUBLISH);
        pub.setData(draft.getData());
        return shPostUnifiedRepository.save(pub);
    }

    @Transactional
    public void unpublish(String postId) {
        shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED)
                .ifPresent(shPostUnifiedRepository::delete);
    }

    @Transactional
    public void delete(String postId) {
        shPostUnifiedRepository.findById(postId).forEach(shPostUnifiedRepository::delete);
    }

    private void applyTitleAndSummary(ShPostUnified row, JsonNode data, ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null || data == null) return;
        StringBuilder title = new StringBuilder();
        StringBuilder summary = new StringBuilder();
        postType.getShPostTypeAttrs().stream()
                .filter(a -> a.getShParentPostTypeAttr() == null)
                .sorted((a, b) -> Integer.compare(a.getOrdinal(), b.getOrdinal()))
                .forEach(attr -> {
                    JsonNode value = data.get(attr.getName());
                    if (value == null || value.isNull()) return;
                    String text = value.isTextual() ? value.asString() : value.toString();
                    if (attr.getIsTitle() == 1) {
                        if (!title.isEmpty()) title.append(", ");
                        title.append(text);
                    }
                    if (attr.getIsSummary() == 1) {
                        if (!summary.isEmpty()) summary.append(", ");
                        summary.append(text);
                    }
                });
        row.setTitle(title.toString());
        row.setSummary(summary.toString());
    }

    private String writeJson(JsonNode data) {
        if (data == null) {
            ObjectNode empty = objectMapper.createObjectNode();
            try {
                return objectMapper.writeValueAsString(empty);
            } catch (JacksonException e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize post data", e);
        }
    }

    // Reused helper so existing callers of objectType keep compiling
    @SuppressWarnings("unused")
    private static String postObjectType() {
        return ShObjectType.POST;
    }

    // Convenience factory for API layer
    public ShPostUnifiedId idOf(String postId, ShPostState state) {
        return new ShPostUnifiedId(postId, state);
    }

    public record CreateRequest(String folderId, String postTypeId, JsonNode data,
                                ShPostState state, String owner) {
    }

    public record UpdateRequest(JsonNode data, ShPostState state, String modifier) {
    }
}
