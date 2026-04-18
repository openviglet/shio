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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostDraft;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.relator.impl.ShRelatorItemImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostDraftRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Backfills the unified post table from the legacy split model.
 *
 * <p>Reads every {@link ShPost} / {@link ShPostDraft} together with their
 * EAV attributes ({@code ShPostAttr} / {@code ShPostDraftAttr}) and writes
 * a single {@link ShPostUnified} row per (post id, state) pair with all
 * attribute values collapsed into a JSON document. Also projects each
 * {@link ShPostType} definition into a JSON schema stored in
 * {@code ShPostType.schemaJson}, so the new model can evolve without the
 * EAV metadata tables.</p>
 *
 * <p>The legacy rows are left untouched — this service only populates the
 * new table. Switching reads/writes to the unified model and retiring the
 * legacy schema are separate follow-up changes.</p>
 */
@Service
public class ShPostUnifiedMigrationService {

    private static final Log logger = LogFactory.getLog(ShPostUnifiedMigrationService.class);

    private final ShPostRepository shPostRepository;
    private final ShPostDraftRepository shPostDraftRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ObjectMapper objectMapper;

    public ShPostUnifiedMigrationService(ShPostRepository shPostRepository,
                                         ShPostDraftRepository shPostDraftRepository,
                                         ShPostUnifiedRepository shPostUnifiedRepository,
                                         ShPostTypeRepository shPostTypeRepository,
                                         ObjectMapper objectMapper) {
        this.shPostRepository = shPostRepository;
        this.shPostDraftRepository = shPostDraftRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MigrationResult migrateAll() {
        int publishedMigrated = 0;
        int draftMigrated = 0;
        int postTypesMigrated = 0;

        for (ShPostType postType : shPostTypeRepository.findAll()) {
            postType.setSchemaJson(buildSchemaJson(postType));
            shPostTypeRepository.save(postType);
            postTypesMigrated++;
        }

        for (ShPost post : shPostRepository.findAll()) {
            shPostUnifiedRepository.save(fromShPost(post));
            publishedMigrated++;
        }

        for (ShPostDraft draft : shPostDraftRepository.findAll()) {
            shPostUnifiedRepository.save(fromShPostDraft(draft));
            draftMigrated++;
        }

        logger.info("Unified migration done: postTypes=" + postTypesMigrated
                + " published=" + publishedMigrated
                + " drafts=" + draftMigrated);
        return new MigrationResult(postTypesMigrated, publishedMigrated, draftMigrated);
    }

    public ShPostUnified fromShPost(ShPost post) {
        ShPostUnified u = new ShPostUnified();
        u.setId(post.getId());
        u.setState(ShPostState.PUBLISHED);
        u.setShPostType(post.getShPostType());
        u.setShFolder(post.getShFolder());
        u.setShSite(post.getShSite());
        u.setTitle(post.getTitle());
        u.setSummary(post.getSummary());
        u.setFurl(post.getFurl());
        u.setOwner(post.getOwner());
        u.setPublished(post.isPublished());
        u.setPublishStatus(post.getPublishStatus());
        u.setDate(post.getDate());
        u.setModifiedDate(post.getModifiedDate());
        u.setPublicationDate(post.getPublicationDate());
        u.setData(buildDataJson(post.getShPostAttrsNonDraft()));
        return u;
    }

    public ShPostUnified fromShPostDraft(ShPostDraft draft) {
        ShPostUnified u = new ShPostUnified();
        u.setId(draft.getId());
        u.setState(ShPostState.DRAFT);
        u.setShPostType(draft.getShPostType());
        u.setShFolder(draft.getShFolder());
        u.setShSite(draft.getShSite());
        u.setTitle(draft.getTitle());
        u.setSummary(draft.getSummary());
        u.setFurl(draft.getFurl());
        u.setOwner(draft.getOwner());
        u.setPublished(draft.isPublished());
        u.setPublishStatus(draft.getPublishStatus());
        u.setDate(draft.getDate());
        u.setModifiedDate(draft.getModifiedDate());
        u.setPublicationDate(draft.getPublicationDate());
        u.setData(buildDataJson(draft.getShPostAttrsDraft()));
        return u;
    }

    private String buildDataJson(Collection<? extends ShPostAttrImpl> attrs) {
        ObjectNode root = objectMapper.createObjectNode();
        if (attrs != null) {
            for (ShPostAttrImpl attr : attrs) {
                if (attr.getShPostTypeAttr() == null) continue;
                writeAttr(root, attr);
            }
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize post attrs to JSON", e);
        }
    }

    private void writeAttr(ObjectNode target, ShPostAttrImpl attr) {
        ShPostTypeAttr typeAttr = attr.getShPostTypeAttr();
        String name = typeAttr.getName();
        if (name == null || name.isBlank()) return;

        Set<? extends ShRelatorItemImpl> children = attr.getShChildrenRelatorItems();
        if (children != null && !children.isEmpty()) {
            ArrayNode arr = target.putArray(name);
            List<ShRelatorItemImpl> sorted = new ArrayList<>(children);
            sorted.sort(Comparator.comparingInt(ShRelatorItemImpl::getOrdinal));
            for (ShRelatorItemImpl item : sorted) {
                ObjectNode child = objectMapper.createObjectNode();
                child.put("_id", item.getId());
                child.put("_ordinal", item.getOrdinal());
                if (item.getTitle() != null) child.put("_title", item.getTitle());
                if (item.getSummary() != null) child.put("_summary", item.getSummary());
                Set<? extends ShPostAttrImpl> childAttrs = item.getShChildrenPostAttrs();
                if (childAttrs != null) {
                    for (ShPostAttrImpl childAttr : childAttrs) {
                        if (childAttr.getShPostTypeAttr() != null) {
                            writeAttr(child, childAttr);
                        }
                    }
                }
                arr.add(child);
            }
            return;
        }

        Set<String> arrayValue = attr.getArrayValue();
        if (arrayValue != null && !arrayValue.isEmpty()) {
            ArrayNode arr = target.putArray(name);
            for (String v : arrayValue) arr.add(v);
            return;
        }

        if (attr.getDateValue() != null) {
            target.put(name, attr.getDateValue().toInstant().toString());
            return;
        }

        String strValue = attr.getStrValue();
        if (strValue != null) {
            target.put(name, strValue);
            return;
        }

        target.putNull(name);
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

    public record MigrationResult(int postTypesMigrated, int publishedMigrated, int draftMigrated) {
    }
}
