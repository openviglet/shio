/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.cda.dto.ShCdaSite;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Content Delivery API (CDA) — versioned, read-only public content surface (SH1).
 *
 * <p>Distinct from the authenticated console API: this namespace returns frozen
 * DTOs (never JPA entities), serves only {@code PUBLISHED} content, and is the
 * stable contract that JavaScript clients ({@code @viglet/shio-client},
 * {@code create-shio-app}) depend on. Authentication is via the {@code Key}
 * header handled by {@code ShApiTokenFilter} (GET-only, {@code ROLE_CDA}).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/cda")
@Tag(name = "CDA", description = "Content Delivery API — read-only published content")
public class ShCdaAPI {

    private final ShCdaService shCdaService;

    public ShCdaAPI(ShCdaService shCdaService) {
        this.shCdaService = shCdaService;
    }

    @GetMapping("/site")
    @Operation(summary = "List published sites")
    public List<ShCdaSite> sites() {
        return shCdaService.listSites();
    }

    @GetMapping("/site/{siteId}")
    @Operation(summary = "Get one site with its root folder id")
    public ResponseEntity<ShCdaSite> site(@PathVariable String siteId) {
        return ResponseEntity.of(shCdaService.getSite(siteId));
    }

    @GetMapping("/object/{folderId}/list")
    @Operation(summary = "List a folder's children (subfolders + published posts)")
    public ResponseEntity<ShCdaListing> list(@PathVariable String folderId,
                                             @RequestParam(name = "page", defaultValue = "0") int page,
                                             @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.of(shCdaService.listChildren(folderId, page, size));
    }

    @GetMapping("/object/{id}/path")
    @Operation(summary = "Get the breadcrumb path for a folder or post")
    public ResponseEntity<ShCdaPath> path(@PathVariable String id) {
        return ResponseEntity.of(shCdaService.path(id));
    }

    @GetMapping("/post/{id}")
    @Operation(summary = "Get one published post with expanded attributes")
    public ResponseEntity<ShCdaPost> post(@PathVariable String id) {
        return ResponseEntity.of(shCdaService.getPost(id));
    }

    @GetMapping("/post/by-url")
    @Operation(summary = "Resolve a published post by site + friendly URL (optionally per locale)")
    public ResponseEntity<ShCdaPost> postByUrl(@RequestParam String siteId,
                                               @RequestParam String url,
                                               @RequestParam(name = "locale", required = false) String locale) {
        return ResponseEntity.of(shCdaService.getPostByUrl(siteId, url, locale));
    }

    @GetMapping("/post-type")
    @Operation(summary = "List every post-type's content-model schema (for type generation)")
    public List<ShCdaPostTypeSchema> postTypes() {
        return shCdaService.listPostTypes();
    }

    @GetMapping("/post-type/{name}")
    @Operation(summary = "Get one post-type's content-model schema by its API name")
    public ResponseEntity<ShCdaPostTypeSchema> postType(@PathVariable String name) {
        return ResponseEntity.of(shCdaService.getPostType(name));
    }

    @GetMapping("/query")
    @Operation(summary = "Query published posts by site / folder / post type")
    public ShCdaListing query(@RequestParam(name = "siteId", required = false) String siteId,
                              @RequestParam(name = "folderId", required = false) String folderId,
                              @RequestParam(name = "postType", required = false) String postType,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              @RequestParam(name = "size", defaultValue = "50") int size) {
        return shCdaService.query(siteId, folderId, postType, page, size);
    }

    @GetMapping("/search")
    @Operation(summary = "Full-text search published posts by title, summary and content")
    public ShCdaListing search(@RequestParam(name = "q") String q,
                               @RequestParam(name = "siteId", required = false) String siteId,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", defaultValue = "50") int size) {
        return shCdaService.search(q, siteId, page, size);
    }
}
