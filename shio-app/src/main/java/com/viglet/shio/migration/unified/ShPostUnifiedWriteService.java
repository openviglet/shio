/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.migration.unified;

import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.post.ShPostAPI;
import com.viglet.shio.object.ShObjectPublishStatus;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.relator.ShRelatorItem;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reverse projection: builds a legacy {@link ShPost} (with nested
 * {@code ShPostAttr} + {@code ShRelatorItem} graph) from a unified post
 * payload and delegates to {@link ShPostAPI#postSave(ShPost)} so the
 * existing publish/draft state machine and side-effects (Turing indexing,
 * furl generation, history commit) stay authoritative.
 *
 * <p>Once {@code postSave} returns, the sync hook in {@code ShPostAPI}
 * re-projects the row into {@code sh_post_unified}.</p>
 */
@Service
public class ShPostUnifiedWriteService {

    private final ShPostAPI shPostAPI;
    private final ShFolderRepository shFolderRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostRepository shPostRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShPostUnifiedWriteService(ShPostAPI shPostAPI,
                                     ShFolderRepository shFolderRepository,
                                     ShSiteRepository shSiteRepository,
                                     ShPostTypeRepository shPostTypeRepository,
                                     ShPostRepository shPostRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shPostAPI = shPostAPI;
        this.shFolderRepository = shFolderRepository;
        this.shSiteRepository = shSiteRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostRepository = shPostRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * Resolves the caller-supplied container id into an actual {@link ShFolder}.
     *
     * <p>The React folder list (and preview / search entry points) navigate
     * to {@code /content/object/{objectId}}, where {@code objectId} can be
     * either a real folder id or a site id — clicking a site opens its root
     * contents, but the URL still carries the site id. When "New post" is
     * clicked there, that same id is propagated into the create URL. So we
     * need to accept both shapes and transparently fall back to the site's
     * root folder when the id belongs to a {@code ShSite}.</p>
     */
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

        ShPost post = new ShPost();
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setOwner(req.owner());
        post.setDate(new Date().toInstant());
        post.setModifiedDate(new Date());

        ShPostState state = req.state() != null ? req.state() : ShPostState.DRAFT;
        post.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH
                : ShObjectPublishStatus.DRAFT);

        Set<ShPostAttr> attrs = buildAttrs(postType, req.data());
        attrs.forEach(a -> a.setShPost(post));
        post.setShPostAttrs(new HashSet<>(attrs));

        shPostAPI.postSave(post);

        return shPostUnifiedRepository.findByIdAndState(post.getId(), state)
                .or(() -> shPostUnifiedRepository.findByIdAndState(post.getId(), ShPostState.PUBLISHED))
                .or(() -> shPostUnifiedRepository.findByIdAndState(post.getId(), ShPostState.DRAFT))
                .orElseThrow(() -> new IllegalStateException(
                        "Post saved but not projected to unified table: " + post.getId()));
    }

    /**
     * Updates an existing post from the unified payload. Existing
     * {@code ShPostAttr} rows are kept in place (preserving their ids and
     * bidirectional links) — only their scalar values are replaced. Relator
     * subtrees are preserved as-is for now; editing nested relator items via
     * the unified payload is not supported in this iteration.
     */
    @Transactional
    public ShPostUnified update(String postId, UpdateRequest req) {
        ShPost post = shPostRepository.findByIdFull(postId)
                .or(() -> shPostRepository.findById(postId))
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        ShPostState state = req.state() != null ? req.state() : ShPostState.DRAFT;
        post.setPublishStatus(state == ShPostState.PUBLISHED
                ? ShObjectPublishStatus.PUBLISH
                : ShObjectPublishStatus.DRAFT);
        post.setModifiedDate(new Date());
        if (req.modifier() != null) post.setModifier(req.modifier());

        Map<String, ShPostAttr> existingByName = post.getShPostAttrsNonDraft().stream()
                .filter(a -> a.getShPostTypeAttr() != null && a.getShPostTypeAttr().getName() != null)
                .collect(Collectors.toMap(
                        a -> a.getShPostTypeAttr().getName(),
                        a -> a,
                        (left, right) -> left));

        ShPostType postType = post.getShPostType();
        if (postType != null && postType.getShPostTypeAttrs() != null) {
            List<ShPostTypeAttr> sorted = postType.getShPostTypeAttrs().stream()
                    .filter(a -> a.getShParentPostTypeAttr() == null)
                    .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal))
                    .toList();
            for (ShPostTypeAttr typeAttr : sorted) {
                JsonNode value = req.data() != null ? req.data().get(typeAttr.getName()) : null;
                ShPostAttr existing = existingByName.get(typeAttr.getName());
                if (existing != null) {
                    applyValue(existing, typeAttr, value);
                } else {
                    ShPostAttr fresh = buildAttr(typeAttr, value);
                    fresh.setShPost(post);
                    post.getShPostAttrsNonDraft().add(fresh);
                }
            }
        }

        shPostAPI.postSave(post);

        return shPostUnifiedRepository.findByIdAndState(post.getId(), state)
                .or(() -> shPostUnifiedRepository.findByIdAndState(post.getId(), ShPostState.PUBLISHED))
                .or(() -> shPostUnifiedRepository.findByIdAndState(post.getId(), ShPostState.DRAFT))
                .orElseThrow(() -> new IllegalStateException(
                        "Post saved but not projected to unified table: " + post.getId()));
    }

    private void applyValue(ShPostAttr target, ShPostTypeAttr typeAttr, JsonNode value) {
        if (value != null && value.isArray() && !value.isEmpty() && value.get(0).isObject()) {
            // Relator payload — preserve existing subtree, skip overwrite
            return;
        }
        target.setStrValue(null);
        target.setArrayValue(new HashSet<>());
        target.setDateValue(null);
        if (value == null || value.isNull()) return;
        if (value.isArray()) {
            Set<String> strings = new HashSet<>();
            for (JsonNode v : value) {
                if (v != null && !v.isNull()) strings.add(v.asString());
            }
            target.setArrayValue(strings);
            return;
        }
        if (value.isTextual()) {
            target.setStrValue(value.asString());
        } else if (value.isNumber()) {
            target.setIntValue(value.asInt());
            target.setStrValue(value.asString());
        } else if (value.isBoolean()) {
            target.setStrValue(Boolean.toString(value.asBoolean()));
        }
    }

    private Set<ShPostAttr> buildAttrs(ShPostType postType, JsonNode data) {
        Set<ShPostAttr> result = new HashSet<>();
        if (postType.getShPostTypeAttrs() == null) return result;

        List<ShPostTypeAttr> sorted = postType.getShPostTypeAttrs().stream()
                .filter(a -> a.getShParentPostTypeAttr() == null)
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal))
                .toList();

        for (ShPostTypeAttr typeAttr : sorted) {
            ShPostAttr attr = buildAttr(typeAttr, data != null ? data.get(typeAttr.getName()) : null);
            result.add(attr);
        }
        return result;
    }

    private ShPostAttr buildAttr(ShPostTypeAttr typeAttr, JsonNode value) {
        ShPostAttr attr = new ShPostAttr();
        attr.setShPostTypeAttr(typeAttr);

        if (value == null || value.isNull()) return attr;

        if (value.isArray()) {
            populateArray(attr, typeAttr, (ArrayNode) value);
            return attr;
        }

        if (value.isTextual()) {
            attr.setStrValue(value.asString());
        } else if (value.isNumber()) {
            attr.setIntValue(value.asInt());
            attr.setStrValue(value.asString());
        } else if (value.isBoolean()) {
            attr.setStrValue(Boolean.toString(value.asBoolean()));
        }
        return attr;
    }

    private void populateArray(ShPostAttr attr, ShPostTypeAttr typeAttr, ArrayNode array) {
        boolean isRelator = !array.isEmpty() && array.get(0).isObject();
        if (!isRelator) {
            Set<String> strings = new HashSet<>();
            for (JsonNode v : array) {
                if (v != null && !v.isNull()) strings.add(v.asString());
            }
            attr.setArrayValue(strings);
            return;
        }

        Set<ShRelatorItem> items = new HashSet<>();
        int ordinal = 0;
        for (JsonNode child : array) {
            if (!child.isObject()) continue;
            ObjectNode obj = (ObjectNode) child;
            ShRelatorItem item = new ShRelatorItem();
            if (obj.has("_title")) item.setTitle(obj.get("_title").asString());
            if (obj.has("_summary")) item.setSummary(obj.get("_summary").asString());
            if (obj.has("_ordinal") && obj.get("_ordinal").isInt()) {
                item.setOrdinal(obj.get("_ordinal").asInt());
            } else {
                item.setOrdinal(ordinal++);
            }
            Set<ShPostAttr> childAttrs = new HashSet<>();
            if (typeAttr.getShPostTypeAttrs() != null) {
                for (ShPostTypeAttr childTypeAttr : typeAttr.getShPostTypeAttrs()) {
                    JsonNode childValue = obj.get(childTypeAttr.getName());
                    ShPostAttr childAttr = buildAttr(childTypeAttr, childValue);
                    childAttr.setShParentRelatorItem(item);
                    childAttrs.add(childAttr);
                }
            }
            item.setShChildrenPostAttrs(childAttrs);
            item.setShParentPostAttr(attr);
            items.add(item);
        }
        attr.setShChildrenRelatorItems(items);
    }

    public record CreateRequest(String folderId, String postTypeId, JsonNode data,
                                ShPostState state, String owner) {
    }

    public record UpdateRequest(JsonNode data, ShPostState state, String modifier) {
    }
}
