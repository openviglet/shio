/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.api.cda.dto.ShCdaSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

/**
 * Content Delivery API (CDA) read service (SH1). All reads are pinned to
 * {@link ShPostState#PUBLISHED} — drafts are never exposed. Preview state is a
 * deliberate later choice (SH5/SH13) and remains a service-level concern, not a
 * URL segment, so the public contract stays stable.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Transactional(readOnly = true)
public class ShCdaService {

    private static final ShPostState STATE = ShPostState.PUBLISHED;
    private static final byte ROOT_FOLDER = 1;

    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShCdaMapper mapper;

    public ShCdaService(ShSiteRepository shSiteRepository,
                        ShFolderRepository shFolderRepository,
                        ShPostUnifiedRepository shPostUnifiedRepository,
                        ShCdaMapper mapper) {
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.mapper = mapper;
    }

    public List<ShCdaSite> listSites() {
        return shSiteRepository.findAll().stream()
                .filter(site -> siteAllowed(site.getId()))
                .map(site -> mapper.toSite(site, rootFolderId(site)))
                .toList();
    }

    public Optional<ShCdaSite> getSite(String siteId) {
        if (!siteAllowed(siteId)) {
            return Optional.empty();
        }
        return shSiteRepository.findById(siteId)
                .map(site -> mapper.toSite(site, rootFolderId(site)));
    }

    public Optional<ShCdaListing> listChildren(String folderId, int page, int size) {
        return shFolderRepository.findById(folderId)
                .filter(folder -> siteAllowed(siteIdOf(folder)))
                .map(folder -> {
            List<ShCdaFolder> folders = shFolderRepository.findByParentFolderOrderByPositionAsc(folder).stream()
                    .map(mapper::toFolder)
                    .toList();
            Page<ShPostUnified> posts = shPostUnifiedRepository
                    .findByShFolderAndState(folder, STATE, pageable(page, size));
            return new ShCdaListing(folderId, folders, summaries(posts),
                    page, size, posts.getTotalElements());
        });
    }

    public Optional<ShCdaPath> path(String objectId) {
        Optional<ShFolder> asFolder = shFolderRepository.findById(objectId);
        if (asFolder.isPresent()) {
            return asFolder
                    .filter(folder -> siteAllowed(siteIdOf(folder)))
                    .map(this::buildPath);
        }
        return shPostUnifiedRepository.findByIdAndState(objectId, STATE)
                .filter(post -> siteAllowed(siteIdOf(post)))
                .map(ShPostUnified::getShFolder)
                .map(this::buildPath);
    }

    public Optional<ShCdaPost> getPost(String id) {
        return shPostUnifiedRepository.findByIdAndState(id, STATE)
                .filter(post -> siteAllowed(siteIdOf(post)))
                .map(mapper::toPost);
    }

    public Optional<ShCdaPost> getPostByUrl(String siteId, String url) {
        if (!siteAllowed(siteId)) {
            return Optional.empty();
        }
        for (String candidate : urlCandidates(url)) {
            Optional<ShCdaPost> found = shPostUnifiedRepository
                    .findByShSite_IdAndFurlAndState(siteId, candidate, STATE)
                    .map(mapper::toPost);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public ShCdaListing query(String siteId, String folderId, String postType, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<ShPostUnified> posts;
        if (folderId != null && !folderId.isBlank()) {
            posts = shFolderRepository.findById(folderId)
                    .filter(folder -> siteAllowed(siteIdOf(folder)))
                    .map(folder -> shPostUnifiedRepository.findByShFolderAndState(folder, STATE, pageable))
                    .orElse(Page.empty(pageable));
        } else if (siteId != null && !siteId.isBlank() && !siteAllowed(siteId)) {
            posts = Page.empty(pageable);
        } else if (siteId != null && !siteId.isBlank() && postType != null && !postType.isBlank()) {
            posts = shPostUnifiedRepository
                    .findByShSite_IdAndShPostType_NameAndState(siteId, postType, STATE, pageable);
        } else if (siteId != null && !siteId.isBlank()) {
            posts = shPostUnifiedRepository.findByShSite_IdAndState(siteId, STATE, pageable);
        } else {
            posts = Page.empty(pageable);
        }
        return new ShCdaListing(folderId, List.of(), summaries(posts),
                page, size, posts.getTotalElements());
    }

    // --- helpers ---

    /**
     * Per-site enforcement (SH2). True when the current token has no allow-list
     * (or there is no token at all — console/test access), or the site is in the
     * token's allow-list. A disallowed site collapses to the existing 404/empty
     * path, so a per-site key cannot distinguish "wrong site" from "missing".
     */
    private boolean siteAllowed(String siteId) {
        return ShApiTokenContext.current()
                .map(context -> context.allowsSite(siteId))
                .orElse(true);
    }

    private static String siteIdOf(ShFolder folder) {
        ShSite site = folder != null ? folder.getShSite() : null;
        return site != null ? site.getId() : null;
    }

    private static String siteIdOf(ShPostUnified post) {
        ShSite site = post != null ? post.getShSite() : null;
        return site != null ? site.getId() : null;
    }

    private List<ShCdaPostSummary> summaries(Page<ShPostUnified> posts) {
        return posts.getContent().stream().map(mapper::toSummary).toList();
    }

    private ShCdaPath buildPath(ShFolder currentFolder) {
        if (currentFolder == null) {
            return null;
        }
        List<ShFolder> chain = new ArrayList<>();
        for (ShFolder f = currentFolder; f != null; f = f.getParentFolder()) {
            chain.add(f);
        }
        Collections.reverse(chain); // root → ... → current
        List<ShCdaFolder> breadcrumb = chain.stream().map(mapper::toFolder).toList();
        ShSite site = currentFolder.getShSite();
        return new ShCdaPath(
                site != null ? mapper.toSite(site, rootFolderId(site)) : null,
                breadcrumb,
                mapper.toFolder(currentFolder));
    }

    private String rootFolderId(ShSite site) {
        return shFolderRepository.findByShSiteAndRootFolder(site, ROOT_FOLDER).stream()
                .findFirst()
                .map(ShFolder::getId)
                .orElse(null);
    }

    /** Tolerate stored furls with or without a leading slash. */
    private List<String> urlCandidates(String url) {
        if (url == null) {
            return List.of();
        }
        if (url.startsWith("/")) {
            return List.of(url, url.substring(1));
        }
        return List.of(url, "/" + url);
    }

    private static Pageable pageable(int page, int size) {
        int safeSize = size <= 0 ? 50 : size;
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize);
    }
}
