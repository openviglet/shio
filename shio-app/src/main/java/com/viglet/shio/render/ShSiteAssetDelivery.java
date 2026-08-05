/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import org.springframework.http.HttpStatus;

/**
 * SH437 — the HTTP half of serving a {@code File} post at the path its source used.
 *
 * <p>Split from {@link ShSiteAssets} because the two answer different questions and only one of
 * them is testable without a servlet: the resolver says <em>which post</em> a site-relative path
 * names, this says what a response for it looks like. SH245's rule — one writer per verb — read
 * forwards.</p>
 *
 * <h2>The headers, and why they are not the preview's</h2>
 *
 * <p>A page under {@code /preview/**} is {@code no-store} and {@code noindex} on purpose: a
 * freshness lifetime is how a preview becomes infrastructure. <b>An asset is not a page.</b> Its
 * bytes are immutable for as long as the post holds them — a replacement is a new upload, and the
 * console's own replace path assigns new bytes under the same id — so caching them privately for a
 * few minutes costs nothing a reviewer would notice and saves a round trip per image on every
 * reload of a page that has forty. {@code private} rather than {@code public}, because this route
 * is authenticated and a shared cache in front of it must not serve one reader's draft asset to
 * another. {@code noindex} stays: nothing under {@code /preview} teaches a crawler anything.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShSiteAssetDelivery {

    /** Long enough to matter on a page of images, short enough that a replaced asset appears. */
    private static final long CACHE_SECONDS = 300;

    private final ShSiteAssets shSiteAssets;
    private final ShSiteRepository shSiteRepository;
    private final ShStaticFileService shStaticFileService;
    private final ShFileStorageService storage;

    public ShSiteAssetDelivery(ShSiteAssets shSiteAssets, ShSiteRepository shSiteRepository,
                               ShStaticFileService shStaticFileService, ShFileStorageService storage) {
        this.shSiteAssets = shSiteAssets;
        this.shSiteRepository = shSiteRepository;
        this.shStaticFileService = shStaticFileService;
        this.storage = storage;
    }

    /**
     * Stream the asset a site-relative path names.
     *
     * @param siteKey id, friendly URL or name — SH181's order, the same one the page route takes
     * @param path    the source's own path, e.g. {@code /img/hero.png}
     * @param state   {@code PUBLISHED} never sees a draft asset
     */
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> serve(String siteKey, String path, ShPostState state) {
        ShSite site = resolveSite(siteKey);
        ShPostUnified post = shSiteAssets.find(site, path, state)
                .orElseThrow(() -> new UnknownAssetException(site.getName(), path));

        String fileName = shStaticFileService.fileNameOf(post);
        if (fileName == null) {
            throw new UnknownAssetException(site.getName(), path);
        }
        Path bytes = storage.resolve(post.getId(), fileName);
        if (!Files.isRegularFile(bytes)) {
            // The post is there and its bytes are not — SH429's `source-asset-missing` one layer
            // along, and a different sentence from "no asset at that path" because the fix differs.
            throw new StrandedAssetException(site.getName(), path, post.getId());
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storage.probeContentType(bytes)))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(CACHE_SECONDS)).cachePrivate())
                .header("X-Robots-Tag", "noindex, nofollow")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(new FileSystemResource(bytes));
    }

    private ShSite resolveSite(String siteKey) {
        return Optional.ofNullable(siteKey)
                .filter(key -> !key.isBlank())
                .flatMap(key -> shSiteRepository.findById(key)
                        .or(() -> Optional.ofNullable(shSiteRepository.findByFurl(key)))
                        .or(() -> Optional.ofNullable(shSiteRepository.findByName(key))))
                .orElseThrow(() -> new ShPageRenderService.UnknownSiteException(siteKey));
    }

    /**
     * A 404 that says what an asset's address is (P6).
     *
     * <p>The fix names the projection path rather than an API call, because the answer is almost
     * always that the bytes never left the tree: SH145 uploads an asset because a {@code File}
     * <em>field</em> names it, and a page kept in fidelity mode has no fields at all.</p>
     */
    public static class UnknownAssetException extends ShTeachingException {

        public UnknownAssetException(String siteName, String path) {
            super(ShProblem.of("render-asset-unknown", HttpStatus.NOT_FOUND,
                            "No asset at '" + path + "' in site '" + siteName + "'")
                    .withDetail("An asset is addressed by its folder chain plus its file name "
                            + "(SH145), so this path had to name a File post at that chain.")
                    .withFix("Put the bytes at shio/assets/" + siteName + path
                            + " and run `shio push --content`. "
                            + "`shio verify --checks replication` reports every captured asset the "
                            + "replica does not serve.")
                    .withExample("/preview/" + siteName + "/img/hero.png"));
        }
    }

    /** A 404 for a post whose bytes are gone — a different defect, so a different sentence. */
    public static class StrandedAssetException extends ShTeachingException {

        public StrandedAssetException(String siteName, String path, String postId) {
            super(ShProblem.of("render-asset-bytes-missing", HttpStatus.NOT_FOUND,
                            "The asset at '" + path + "' has no bytes on this instance")
                    .withDetail("The File post exists (" + postId + ") and nothing is stored for it, "
                            + "so this is a lost upload rather than a wrong path.")
                    .withFix("Re-upload it: `shio push --content` sends what is in shio/assets/.")
                    .withExample("/preview/" + siteName + path));
        }
    }
}
