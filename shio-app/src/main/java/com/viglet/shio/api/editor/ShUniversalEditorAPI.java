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

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostAttrRepository;
import com.viglet.shio.persistence.repository.post.ShPostRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
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
    private final ShFolderRepository shFolderRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShWidgetRepository shWidgetRepository;
    private final ShPostUtils shPostUtils;
    private final ShObjectUtils shObjectUtils;
    private final ShCacheObject shCacheObject;

    public ShUniversalEditorAPI(ShPostRepository shPostRepository,
                                ShPostAttrRepository shPostAttrRepository,
                                ShPostTypeRepository shPostTypeRepository,
                                ShFolderRepository shFolderRepository,
                                ShSiteRepository shSiteRepository,
                                ShWidgetRepository shWidgetRepository,
                                ShPostUtils shPostUtils,
                                ShObjectUtils shObjectUtils,
                                ShCacheObject shCacheObject) {
        this.shPostRepository = shPostRepository;
        this.shPostAttrRepository = shPostAttrRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shFolderRepository = shFolderRepository;
        this.shSiteRepository = shSiteRepository;
        this.shWidgetRepository = shWidgetRepository;
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

        Optional<ShPost> shPostOpt = shPostRepository.findById(postId);
        if (shPostOpt.isEmpty()) {
            log.warn("PATCH post {} — not found. Call ensurePost first.", postId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Post not found. It must be created via /post/ensure first."));
        }

        if (!shObjectUtils.canAccess(principal, postId)) {
            log.warn("PATCH post {} — access denied for {}", postId,
                    principal != null ? principal.getName() : "anonymous");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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

    /**
     * Ensure a post exists. If the post already exists, returns its data.
     * If not, auto-creates it using the provided model name and DOM field values.
     * Requires the post type to exist in the system.
     */
    @Transactional
    @PostMapping("/post/ensure")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> ensurePost(
            @RequestBody Map<String, Object> body,
            Principal principal) {

        String postId = (String) body.get("id");
        String modelName = (String) body.get("model");
        String siteName = (String) body.get("siteName");
        String folderPath = (String) body.get("folderPath");
        List<Map<String, Object>> fields = (List<Map<String, Object>>) body.getOrDefault("fields", List.of());

        if (postId == null || modelName == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "id and model are required"));
        }

        // If post already exists, fix permissions if needed and return it
        Optional<ShPost> existingPost = shPostRepository.findById(postId);
        if (existingPost.isPresent()) {
            ShPost ep = existingPost.get();
            boolean needsUpdate = false;
            if ((ep.getShGroups() == null || ep.getShGroups().isEmpty())
                    && (ep.getShUsers() == null || ep.getShUsers().isEmpty())) {
                ep.setShGroups(null);
                ep.setShUsers(null);
                needsUpdate = true;
            }
            if (needsUpdate) {
                shPostRepository.saveAndFlush(ep);
            }
            return getPostData(postId, principal);
        }

        // Find or create post type
        ShPostType postType = shPostTypeRepository.findByName(modelName);
        if (postType == null) {
            postType = createPostTypeFromFields(modelName, fields);
            log.info("Auto-created post type '{}' with {} fields from Universal Editor",
                    modelName, fields.size());
        }

        // Resolve or create site > folder hierarchy
        ShFolder folder = resolveOrCreateFolder(siteName, folderPath);
        if (folder == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No site/folder available to create post"));
        }

        // Build field values map from DOM data
        Map<String, String> fieldValues = new HashMap<>();
        for (Map<String, Object> field : fields) {
            String prop = (String) field.get("prop");
            String value = (String) field.get("value");
            if (prop != null) {
                fieldValues.put(prop, value);
            }
        }

        // Create the post with permissions copied from folder
        ShPost post = new ShPost();
        post.setId(postId);
        post.setShPostType(postType);
        post.setShFolder(folder);
        post.setDate(java.time.Instant.now());
        post.setOwner(principal != null ? principal.getName() : "system");
        post.setFurl(postId);
        post.setPublished(false);
        // Copy permissions from folder; set null if empty so access check passes
        if (folder.getShGroups() != null && !folder.getShGroups().isEmpty()) {
            post.setShGroups(new HashSet<>(folder.getShGroups()));
        } else {
            post.setShGroups(null);
        }
        if (folder.getShUsers() != null && !folder.getShUsers().isEmpty()) {
            post.setShUsers(new HashSet<>(folder.getShUsers()));
        } else {
            post.setShUsers(null);
        }

        // Derive title from fields
        String title = modelName;
        if (postType.getShPostTypeAttrs() != null) {
            for (ShPostTypeAttr pta : postType.getShPostTypeAttrs()) {
                if (pta.getIsTitle() == 1 && fieldValues.containsKey(pta.getName())) {
                    title = fieldValues.get(pta.getName());
                    break;
                }
            }
        }
        post.setTitle(title);

        shPostRepository.saveAndFlush(post);

        // Create post attributes
        if (postType.getShPostTypeAttrs() != null) {
            for (ShPostTypeAttr pta : postType.getShPostTypeAttrs()) {
                ShPostAttr attr = new ShPostAttr();
                attr.setShPost(post);
                attr.setShPostTypeAttr(pta);
                String value = fieldValues.getOrDefault(pta.getName(), "");
                attr.setStrValue(value);
                shPostAttrRepository.saveAndFlush(attr);

                if (pta.getIsSummary() == 1) {
                    post.setSummary(value);
                }
            }
        }

        shPostRepository.saveAndFlush(post);

        log.info("Auto-created post {} ({}) from Universal Editor", postId, modelName);

        return getPostData(postId, principal);
    }

    /**
     * Resolves the folder hierarchy from site name and path, creating any missing
     * pieces (site, root folder, sub-folders).
     * Path format: "Home/Articles" → root folder "Home" → child folder "Articles"
     */
    private ShFolder resolveOrCreateFolder(String siteName, String folderPath) {
        // Find or create site
        ShSite site = findOrCreateSite(siteName);
        if (site == null) return null;

        // Find or create root folder
        Set<ShFolder> rootFolders = shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1);
        ShFolder rootFolder;

        if (folderPath == null || folderPath.isBlank()) {
            // No path — use existing root or create one
            if (!rootFolders.isEmpty()) return rootFolders.iterator().next();
            rootFolder = createFolder("Home", site, null, true);
            return rootFolder;
        }

        String[] segments = folderPath.split("/");

        // First segment is the root folder
        rootFolder = rootFolders.stream()
                .filter(f -> segments[0].equals(f.getName()))
                .findFirst()
                .orElse(null);

        if (rootFolder == null) {
            rootFolder = createFolder(segments[0], site, null, true);
        }

        // Walk remaining segments, creating sub-folders as needed
        ShFolder current = rootFolder;
        for (int i = 1; i < segments.length; i++) {
            String seg = segments[i];
            ShFolder child = shFolderRepository.findByParentFolderAndName(current, seg);
            if (child == null) {
                child = createFolder(seg, null, current, false);
            }
            current = child;
        }

        return current;
    }

    private ShSite findOrCreateSite(String siteName) {
        if (siteName != null && !siteName.isBlank()) {
            ShSite existing = shSiteRepository.findByName(siteName);
            if (existing != null) return existing;

            // Create new site
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setFurl(siteName.toLowerCase().replace(" ", "_"));
            site.setDate(java.time.Instant.now());
            site.setShGroups(null);
            site.setShUsers(null);
            shSiteRepository.saveAndFlush(site);
            log.info("Auto-created site '{}' from Universal Editor", siteName);
            return site;
        }

        // No site name — use first existing site
        List<ShSite> sites = shSiteRepository.findAll();
        return sites.isEmpty() ? null : sites.getFirst();
    }

    private ShFolder createFolder(String name, ShSite site, ShFolder parent, boolean isRoot) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setFurl(name.toLowerCase().replace(" ", "-"));
        folder.setDate(java.time.Instant.now());
        folder.setShGroups(null);
        folder.setShUsers(null);
        if (isRoot) {
            folder.setShSite(site);
            folder.setRootFolder((byte) 1);
        } else {
            folder.setParentFolder(parent);
            folder.setRootFolder((byte) 0);
        }
        shFolderRepository.saveAndFlush(folder);
        log.info("Auto-created folder '{}' (root={}) from Universal Editor", name, isRoot);
        return folder;
    }

    /**
     * Creates a new ShPostType from DOM field definitions.
     * Maps field types (text, richtext, media) to Shio widgets.
     */
    private ShPostType createPostTypeFromFields(String modelName, List<Map<String, Object>> fields) {
        ShPostType postType = new ShPostType();
        postType.setName(modelName);
        postType.setTitle(modelName);
        postType.setDate(java.time.Instant.now());
        postType.setOwner("system");
        postType.setSystem((byte) 0);
        shPostTypeRepository.saveAndFlush(postType);

        int ordinal = 0;
        for (Map<String, Object> field : fields) {
            String prop = (String) field.get("prop");
            String type = (String) field.getOrDefault("type", "text");
            String label = (String) field.getOrDefault("label", prop);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName(prop);
            attr.setLabel(label != null ? label : prop);
            attr.setOrdinal(ordinal++);
            attr.setRequired((byte) 0);
            attr.setIsTitle(ordinal == 1 ? (byte) 1 : (byte) 0);
            attr.setIsSummary((byte) 0);
            attr.setShPostType(postType);

            // Map DOM type to Shio widget
            String widgetName = switch (type) {
                case "richtext" -> "HTML Editor";
                case "media" -> "File";
                case "textarea" -> "Text Area";
                default -> "Text";
            };
            ShWidget widget = shWidgetRepository.findByName(widgetName);
            if (widget == null) {
                widget = shWidgetRepository.findByName("Text");
            }
            attr.setShWidget(widget);

            postType.getShPostTypeAttrs().add(attr);
        }

        shPostTypeRepository.saveAndFlush(postType);
        return postType;
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
