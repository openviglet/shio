/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH1218 — a site's published home as a picture, for the sites grid.
 *
 * <p>Its own controller rather than three more methods on {@code ShSiteAPI}: a site write answers
 * with the row it wrote, while this one answers with the appearance digest the picture was stored
 * at, and the P9 census holds those as two different claims (SH983).</p>
 */
@RestController
@RequestMapping("/api/v2/site/{id}/thumbnail")
@Tag(name = "Site", description = "Site management API")
public class ShSiteThumbnailAPI {

    private final ShSiteRepository shSiteRepository;
    private final ShSiteThumbnailService shSiteThumbnailService;
    private final ShSiteThumbnailStore shSiteThumbnailStore;

    public ShSiteThumbnailAPI(ShSiteRepository shSiteRepository,
                              ShSiteThumbnailService shSiteThumbnailService,
                              ShSiteThumbnailStore shSiteThumbnailStore) {
        this.shSiteRepository = shSiteRepository;
        this.shSiteThumbnailService = shSiteThumbnailService;
        this.shSiteThumbnailStore = shSiteThumbnailStore;
    }

    /** The stored picture; 404 when there is none, which the grid shows as the site's initials. */
    @GetMapping
    public ResponseEntity<byte[]> get(@PathVariable String id) throws IOException {
        Optional<ShSiteThumbnailStore.Stored> stored = shSiteThumbnailStore.read(id);
        Optional<Path> image = shSiteThumbnailStore.image(id);
        if (shSiteRepository.findById(id).isEmpty() || stored.isEmpty() || image.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .eTag("\"" + stored.get().digest() + "\"")
                .cacheControl(CacheControl.noCache().cachePrivate())
                .contentType(MediaType.parseMediaType(stored.get().contentType()))
                .body(Files.readAllBytes(image.get()));
    }

    /**
     * Whether the stored picture shows the home as published now, and the digest a new picture has
     * to be sent with. One server render, no browser.
     */
    @GetMapping("/status")
    public ResponseEntity<ShSiteThumbnailService.Status> status(@PathVariable String id) {
        return shSiteRepository.findById(id)
                .map(site -> ResponseEntity.ok(shSiteThumbnailService.status(site)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Keeps a picture of the published home, taken by the console. Refused when the home changed
     * since {@code digest}; a no-op answering {@code stored: false} when the picture already shows
     * that digest, so re-publishing an unchanged home stores nothing.
     */
    @PutMapping
    public ResponseEntity<ShSiteThumbnailService.Outcome> put(@PathVariable String id,
            @RequestParam(name = "digest") String digest,
            @RequestHeader(name = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestBody byte[] bytes) {
        Optional<ShSite> site = shSiteRepository.findById(id);
        return site.map(found -> ResponseEntity.ok(
                        shSiteThumbnailService.put(found, digest, contentType, bytes)))
                .orElse(ResponseEntity.notFound().build());
    }
}
