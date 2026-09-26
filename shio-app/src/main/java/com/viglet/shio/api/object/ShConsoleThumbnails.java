/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.staticfile.ShAssetKind;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.reference.ShObjectReferenceRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Which image each card of a listing page shows (SH1026).
 *
 * <p>A {@code File} post that holds an image is its own thumbnail; any other post shows the first
 * image it references, read off SH1034's reference index, which already knows every edge a post
 * has. Two statements for a whole page — its edges, then their targets — and never one per card,
 * which is what a grid of fifty would otherwise cost.</p>
 *
 * <p>The answer is the file's byte path on the public {@code /file_source/**} route. The card adds
 * the transform parameters, as the media library's own thumbnails do, so the size a grid asks for
 * stays a decision of the screen that draws it.</p>
 */
@Component
public class ShConsoleThumbnails {

    private final ShObjectReferenceRepository references;
    private final ShPostUnifiedRepository posts;
    private final ObjectMapper objectMapper;

    public ShConsoleThumbnails(ShObjectReferenceRepository references, ShPostUnifiedRepository posts,
                               ObjectMapper objectMapper) {
        this.references = references;
        this.posts = posts;
        this.objectMapper = objectMapper;
    }

    /** Post id to thumbnail path, for the posts that have one. */
    public Map<String, String> of(List<ShPostUnified> page) {
        Map<String, String> thumbnails = new HashMap<>();
        List<String> others = new java.util.ArrayList<>();
        for (ShPostUnified post : page) {
            String own = isFile(post) ? imagePath(post) : null;
            if (own != null) {
                thumbnails.put(post.getId(), own);
            } else if (!isFile(post)) {
                others.add(post.getId());
            }
        }
        if (others.isEmpty()) {
            return thumbnails;
        }
        Map<String, List<String>> targetsByPost = new LinkedHashMap<>();
        for (Object[] edge : references.findEdgesFrom(others)) {
            targetsByPost.computeIfAbsent((String) edge[0], id -> new java.util.ArrayList<>()).add((String) edge[1]);
        }
        if (targetsByPost.isEmpty()) {
            return thumbnails;
        }
        Map<String, String> imageOf = new HashMap<>();
        for (ShPostUnified target : posts.findByIdIn(targetsByPost.values().stream().flatMap(List::stream).toList())) {
            String path = isFile(target) ? imagePath(target) : null;
            if (path != null) {
                imageOf.putIfAbsent(target.getId(), path);
            }
        }
        targetsByPost.forEach((postId, targets) -> targets.stream().map(imageOf::get)
                .filter(java.util.Objects::nonNull).findFirst()
                .ifPresent(path -> thumbnails.put(postId, path)));
        return thumbnails;
    }

    private static boolean isFile(ShPostUnified post) {
        return post.getShPostType() != null && ShSystemPostType.FILE.equals(post.getShPostType().getName());
    }

    /** The byte path of a {@code File} post that holds an image, or null. */
    private String imagePath(ShPostUnified post) {
        if (post.getData() == null) {
            return null;
        }
        JsonNode data = objectMapper.readTree(post.getData());
        JsonNode name = data == null ? null : data.path(ShSystemPostTypeAttr.FILE);
        String fileName = name != null && name.isValueNode() && !name.isNull() ? name.asString() : "";
        if (fileName.isBlank() || !ShAssetKind.isImage(data, fileName)) {
            return null;
        }
        return "/file_source/" + post.getId() + "/"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
