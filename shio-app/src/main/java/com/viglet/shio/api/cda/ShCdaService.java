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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.cda.dto.ShCdaSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

/**
 * Content Delivery API (CDA) read service (SH1). The read state is a
 * service-level concern resolved per request (SH5), never a URL segment, so the
 * public contract stays stable. A {@code PROD} token (the default, and any
 * console/no-token access) reads {@link ShPostState#PUBLISHED} only; a
 * {@code PREVIEW} token reads draft-preferred — preferring the {@code DRAFT} row
 * of a post, falling back to {@code PUBLISHED}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Transactional(readOnly = true)
public class ShCdaService {

    private static final ShPostState STATE = ShPostState.PUBLISHED;
    /** Preview reads see both states, draft-preferred (SH5). */
    private static final List<ShPostState> PREVIEW_STATES = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);
    private static final byte ROOT_FOLDER = 1;

    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShCdaMapper mapper;

    public ShCdaService(ShSiteRepository shSiteRepository,
                        ShFolderRepository shFolderRepository,
                        ShPostUnifiedRepository shPostUnifiedRepository,
                        ShPostTypeRepository shPostTypeRepository,
                        ShCdaMapper mapper) {
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.mapper = mapper;
    }

    /**
     * The content model of every post-type (SH11), ordered by name. The schema is
     * a global, non-site-scoped contract (post-types are shared across sites), so
     * it is not gated by the SH2 per-site allow-list — it carries the field shape
     * only, never content. Backs {@code shio-types} code generation.
     */
    public List<ShCdaPostTypeSchema> listPostTypes() {
        return shPostTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(pt -> pt.getName() == null ? "" : pt.getName()))
                .map(mapper::toPostTypeSchema)
                .toList();
    }

    /** The content model of one post-type by its API name (SH11), or empty if unknown. */
    public Optional<ShCdaPostTypeSchema> getPostType(String name) {
        return Optional.ofNullable(shPostTypeRepository.findByName(name))
                .map(mapper::toPostTypeSchema);
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
            Page<ShPostUnified> posts = preview()
                    ? draftPreferred(shPostUnifiedRepository.findByShFolderAndStateIn(folder, PREVIEW_STATES),
                            pageable(page, size))
                    : shPostUnifiedRepository.findByShFolderAndState(folder, STATE, pageable(page, size));
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
        return resolvePost(objectId)
                .filter(post -> siteAllowed(siteIdOf(post)))
                .map(ShPostUnified::getShFolder)
                .map(this::buildPath);
    }

    public Optional<ShCdaPost> getPost(String id) {
        return resolvePost(id)
                .filter(post -> siteAllowed(siteIdOf(post)))
                .map(mapper::toPost);
    }

    public Optional<ShCdaPost> getPostByUrl(String siteId, String url) {
        if (!siteAllowed(siteId)) {
            return Optional.empty();
        }
        for (String candidate : urlCandidates(url)) {
            for (ShPostState state : readStates()) {
                Optional<ShCdaPost> found = shPostUnifiedRepository
                        .findByShSite_IdAndFurlAndState(siteId, candidate, state)
                        .map(mapper::toPost);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    public ShCdaListing query(String siteId, String folderId, String postType, int page, int size) {
        Page<ShPostUnified> posts = queryPage(siteId, folderId, postType, pageable(page, size));
        return new ShCdaListing(folderId, List.of(), summaries(posts),
                page, size, posts.getTotalElements());
    }

    /**
     * Full posts (attributes expanded) for the same query as {@link #query} —
     * GraphQL clients (SH4) select individual attr fields, so the attr-less
     * {@link ShCdaPostSummary} that {@link #query} returns is not enough.
     * Honours the same per-site allow-list (SH2) and {@code PUBLISHED}-only rule.
     */
    public List<ShCdaPost> queryPosts(String siteId, String folderId, String postType, int page, int size) {
        return queryPage(siteId, folderId, postType, pageable(page, size))
                .getContent().stream()
                .map(mapper::toPost)
                .toList();
    }

    private Page<ShPostUnified> queryPage(String siteId, String folderId, String postType, Pageable pageable) {
        if (folderId != null && !folderId.isBlank()) {
            return shFolderRepository.findById(folderId)
                    .filter(folder -> siteAllowed(siteIdOf(folder)))
                    .map(folder -> preview()
                            ? draftPreferred(shPostUnifiedRepository
                                    .findByShFolderAndStateIn(folder, PREVIEW_STATES), pageable)
                            : shPostUnifiedRepository.findByShFolderAndState(folder, STATE, pageable))
                    .orElse(Page.empty(pageable));
        } else if (siteId != null && !siteId.isBlank() && !siteAllowed(siteId)) {
            return Page.empty(pageable);
        } else if (siteId != null && !siteId.isBlank() && postType != null && !postType.isBlank()) {
            return preview()
                    ? draftPreferred(shPostUnifiedRepository
                            .findByShSite_IdAndShPostType_NameAndStateIn(siteId, postType, PREVIEW_STATES), pageable)
                    : shPostUnifiedRepository
                            .findByShSite_IdAndShPostType_NameAndState(siteId, postType, STATE, pageable);
        } else if (siteId != null && !siteId.isBlank()) {
            return preview()
                    ? draftPreferred(shPostUnifiedRepository
                            .findByShSite_IdAndStateIn(siteId, PREVIEW_STATES), pageable)
                    : shPostUnifiedRepository.findByShSite_IdAndState(siteId, STATE, pageable);
        } else {
            return Page.empty(pageable);
        }
    }

    // --- helpers ---

    /**
     * Environment resolution (SH5). True when the current token targets the
     * preview environment. No token (console/tests) ⇒ {@code PROD} ⇒ published-only,
     * preserving the SH1 behaviour.
     */
    private boolean preview() {
        return ShApiTokenContext.current().map(ShApiTokenContext::preview).orElse(false);
    }

    /** States a read considers, in preference order (SH5). */
    private List<ShPostState> readStates() {
        return preview() ? PREVIEW_STATES : List.of(STATE);
    }

    /** First existing row for {@code id} across {@link #readStates()} (draft-preferred under preview). */
    private Optional<ShPostUnified> resolvePost(String id) {
        for (ShPostState state : readStates()) {
            Optional<ShPostUnified> found = shPostUnifiedRepository.findByIdAndState(id, state);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Collapse rows spanning {@link #PREVIEW_STATES} to one per post id — the
     * {@code DRAFT} row when present, else {@code PUBLISHED} — then slice the
     * requested page in memory (SH5 preview path). Preview traffic is internal and
     * low-volume, so this avoids duplicating every paged query for the rare case.
     */
    private Page<ShPostUnified> draftPreferred(List<ShPostUnified> rows, Pageable pageable) {
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        for (ShPostUnified row : rows) {
            byId.merge(row.getId(), row, (existing, candidate) ->
                    existing.getState() == ShPostState.DRAFT ? existing : candidate);
        }
        List<ShPostUnified> deduped = new ArrayList<>(byId.values());
        int from = (int) Math.min(pageable.getOffset(), deduped.size());
        int to = Math.min(from + pageable.getPageSize(), deduped.size());
        return new PageImpl<>(deduped.subList(from, to), pageable, deduped.size());
    }

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
