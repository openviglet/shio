/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.api.editor;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.utils.ShObjectUtils;
import com.viglet.shio.utils.ShPostUtils;
import com.viglet.shio.website.cache.component.ShCacheObject;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Universal Editor API - Provides endpoints for inline visual editing of Shio content.
 * Follows the Adobe Universal Editor pattern adapted for Shio CMS.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/universal-editor")
@Tag(name = "Universal Editor", description = "Universal Editor API for inline content editing")
@Slf4j
public class ShUniversalEditorAPI {

    private final ShPostRepository shPostRepository;
    private final ShPostAttrRepository shPostAttrRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUtils shPostUtils;
    private final ShObjectUtils shObjectUtils;
    private final ShCacheObject shCacheObject;

    public ShUniversalEditorAPI(ShPostRepository shPostRepository,
                                ShPostAttrRepository shPostAttrRepository,
                                ShPostTypeRepository shPostTypeRepository,
                                ShPostUtils shPostUtils,
                                ShObjectUtils shObjectUtils,
                                ShCacheObject shCacheObject) {
        this.shPostRepository = shPostRepository;
        this.shPostAttrRepository = shPostAttrRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUtils = shPostUtils;
        this.shObjectUtils = shObjectUtils;
        this.shCacheObject = shCacheObject;
    }

    /**
     * Update a single post attribute value (inline edit).
     * Used by the universal editor bridge to persist field changes.
     */
    @Transactional
    @PatchMapping("/post/{postId}/attr/{attrName}")
    public ResponseEntity<Map<String, Object>> updatePostAttr(
            @PathVariable String postId,
            @PathVariable String attrName,
            @RequestBody Map<String, Object> body,
            Principal principal) {

        if (!shObjectUtils.canAccess(principal, postId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<ShPost> shPostOpt = shPostRepository.findById(postId);
        if (shPostOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ShPost shPost = shPostUtils.loadLazyPost(postId, false);
        Set<ShPostAttr> attrs = shPostAttrRepository.findByShPost(shPost);

        ShPostAttr targetAttr = null;
        for (ShPostAttr attr : attrs) {
            if (attr.getShPostTypeAttr() != null
                    && attrName.equals(attr.getShPostTypeAttr().getName())) {
                targetAttr = attr;
                break;
            }
        }

        if (targetAttr == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Attribute not found: " + attrName));
        }

        Object value = body.get("value");
        String type = (String) body.getOrDefault("type", "text");

        switch (type) {
            case "text", "richtext":
                targetAttr.setStrValue(value != null ? value.toString() : null);
                break;
            case "date":
                targetAttr.setStrValue(value != null ? value.toString() : null);
                break;
            case "number":
                if (value != null) {
                    targetAttr.setIntValue(Integer.parseInt(value.toString()));
                }
                break;
            default:
                targetAttr.setStrValue(value != null ? value.toString() : null);
                break;
        }

        shPostAttrRepository.saveAndFlush(targetAttr);
        shCacheObject.deleteCache(postId);

        // Update post title/summary if this attr affects them
        if (targetAttr.getShPostTypeAttr().getIsTitle() == 1) {
            shPost.setTitle(value != null ? value.toString() : "");
            shPostRepository.saveAndFlush(shPost);
        }
        if (targetAttr.getShPostTypeAttr().getIsSummary() == 1) {
            shPost.setSummary(value != null ? value.toString() : "");
            shPostRepository.saveAndFlush(shPost);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("postId", postId);
        response.put("attrName", attrName);
        response.put("value", value);
        response.put("status", "updated");

        return ResponseEntity.ok(response);
    }

    /**
     * Batch update multiple attributes of a post at once.
     * Used when the editor saves all pending changes.
     */
    @Transactional
    @PatchMapping("/post/{postId}")
    public ResponseEntity<Map<String, Object>> batchUpdatePost(
            @PathVariable String postId,
            @RequestBody List<Map<String, Object>> updates,
            Principal principal) {

        if (!shObjectUtils.canAccess(principal, postId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<ShPost> shPostOpt = shPostRepository.findById(postId);
        if (shPostOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ShPost shPost = shPostUtils.loadLazyPost(postId, false);
        Set<ShPostAttr> attrs = shPostAttrRepository.findByShPost(shPost);
        Map<String, ShPostAttr> attrsByName = new HashMap<>();
        for (ShPostAttr attr : attrs) {
            if (attr.getShPostTypeAttr() != null) {
                attrsByName.put(attr.getShPostTypeAttr().getName(), attr);
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> update : updates) {
            String attrName = (String) update.get("attrName");
            Object value = update.get("value");
            String type = (String) update.getOrDefault("type", "text");

            ShPostAttr targetAttr = attrsByName.get(attrName);
            if (targetAttr == null) {
                results.add(Map.of("attrName", attrName, "status", "not_found"));
                continue;
            }

            switch (type) {
                case "text", "richtext":
                    targetAttr.setStrValue(value != null ? value.toString() : null);
                    break;
                case "number":
                    if (value != null) targetAttr.setIntValue(Integer.parseInt(value.toString()));
                    break;
                default:
                    targetAttr.setStrValue(value != null ? value.toString() : null);
                    break;
            }

            shPostAttrRepository.saveAndFlush(targetAttr);

            if (targetAttr.getShPostTypeAttr().getIsTitle() == 1) {
                shPost.setTitle(value != null ? value.toString() : "");
            }
            if (targetAttr.getShPostTypeAttr().getIsSummary() == 1) {
                shPost.setSummary(value != null ? value.toString() : "");
            }

            results.add(Map.of("attrName", attrName, "status", "updated"));
        }

        shPostRepository.saveAndFlush(shPost);
        shCacheObject.deleteCache(postId);

        return ResponseEntity.ok(Map.of("postId", postId, "results", results));
    }

    /**
     * Get the model definition for a post type (fields, widgets, settings).
     * Used by the editor shell to build the properties panel.
     */
    @GetMapping("/model/{postTypeId}")
    public ResponseEntity<Map<String, Object>> getModel(@PathVariable String postTypeId) {
        Optional<ShPostType> ptOpt = shPostTypeRepository.findById(postTypeId);
        if (ptOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ShPostType pt = ptOpt.get();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("id", pt.getId());
        model.put("name", pt.getName());
        model.put("title", pt.getTitle());

        List<Map<String, Object>> fields = new ArrayList<>();
        if (pt.getShPostTypeAttrs() != null) {
            List<ShPostTypeAttr> sorted = pt.getShPostTypeAttrs().stream()
                    .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal))
                    .collect(Collectors.toList());

            for (ShPostTypeAttr attr : sorted) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", attr.getName());
                field.put("label", attr.getLabel());
                field.put("description", attr.getDescription());
                field.put("widget", attr.getShWidget() != null ? attr.getShWidget().getName() : "Text");
                field.put("required", attr.getRequired() == 1);
                field.put("isTitle", attr.getIsTitle() == 1);
                field.put("isSummary", attr.getIsSummary() == 1);
                field.put("ordinal", attr.getOrdinal());
                field.put("component", mapWidgetToEditorComponent(
                        attr.getShWidget() != null ? attr.getShWidget().getName() : "Text"));
                fields.add(field);
            }
        }
        model.put("fields", fields);

        return ResponseEntity.ok(model);
    }

    /**
     * Get a post's current attribute values for the editor properties panel.
     */
    @GetMapping("/post/{postId}")
    public ResponseEntity<Map<String, Object>> getPostData(
            @PathVariable String postId,
            Principal principal) {

        if (!shObjectUtils.canAccess(principal, postId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<ShPost> shPostOpt = shPostRepository.findById(postId);
        if (shPostOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ShPost shPost = shPostUtils.loadLazyPost(postId, false);
        Set<ShPostAttr> attrs = shPostAttrRepository.findByShPost(shPost);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", shPost.getId());
        result.put("title", shPost.getTitle());
        result.put("postType", Map.of(
                "id", shPost.getShPostType().getId(),
                "name", shPost.getShPostType().getName()));

        Map<String, Object> attrValues = new LinkedHashMap<>();
        for (ShPostAttr attr : attrs) {
            if (attr.getShPostTypeAttr() != null) {
                Map<String, Object> attrData = new LinkedHashMap<>();
                attrData.put("strValue", attr.getStrValue());
                attrData.put("dateValue", attr.getDateValue());
                attrData.put("intValue", attr.getIntValue());
                attrData.put("arrayValue", attr.getArrayValue());
                attrData.put("widget", attr.getShPostTypeAttr().getShWidget() != null
                        ? attr.getShPostTypeAttr().getShWidget().getName() : "Text");
                attrData.put("label", attr.getShPostTypeAttr().getLabel());
                attrValues.put(attr.getShPostTypeAttr().getName(), attrData);
            }
        }
        result.put("attrs", attrValues);

        return ResponseEntity.ok(result);
    }

    private String mapWidgetToEditorComponent(String widgetName) {
        return switch (widgetName) {
            case "Text" -> "text";
            case "Text Area" -> "textarea";
            case "HTML Editor" -> "richtext";
            case "Check Box" -> "boolean";
            case "Combo Box" -> "select";
            case "Date" -> "date";
            case "Hidden" -> "hidden";
            case "Multi Select" -> "multiselect";
            case "Ace Editor - Javascript", "Ace Editor - HTML" -> "code";
            case "Content Select" -> "reference";
            case "File" -> "media";
            case "Relator" -> "container";
            case "Tab" -> "tab";
            default -> "text";
        };
    }
}
