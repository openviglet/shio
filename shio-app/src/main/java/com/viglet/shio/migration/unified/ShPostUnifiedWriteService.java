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
import java.util.Set;

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
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

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
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShPostUnifiedWriteService(ShPostAPI shPostAPI,
                                     ShFolderRepository shFolderRepository,
                                     ShPostTypeRepository shPostTypeRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shPostAPI = shPostAPI;
        this.shFolderRepository = shFolderRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    @Transactional
    public ShPostUnified create(CreateRequest req) {
        ShFolder folder = shFolderRepository.findById(req.folderId())
                .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + req.folderId()));
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
}
