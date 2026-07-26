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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
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
                .map(post -> mapper.toPost(post, availableLocales(post)));
    }

    public Optional<ShCdaPost> getPostByUrl(String siteId, String url) {
        return getPostByUrl(siteId, url, null);
    }

    /**
     * Resolve a published (or draft-preferred, under PREVIEW) post by site +
     * friendly URL, optionally constrained to a {@code locale} (SH53). When a
     * locale is given, only that translation matches; otherwise any locale at the
     * URL resolves (back-compat). The returned post carries its sibling locales.
     */
    public Optional<ShCdaPost> getPostByUrl(String siteId, String url, String locale) {
        if (!siteAllowed(siteId)) {
            return Optional.empty();
        }
        for (String candidate : urlCandidates(url)) {
            for (ShPostState state : readStates()) {
                Optional<ShPostUnified> found = (locale != null && !locale.isBlank())
                        ? shPostUnifiedRepository.findByShSite_IdAndFurlAndStateAndLocale(siteId, candidate, state, locale)
                        : shPostUnifiedRepository.findByShSite_IdAndFurlAndState(siteId, candidate, state);
                if (found.isPresent()) {
                    return found.map(post -> mapper.toPost(post, availableLocales(post)));
                }
            }
        }
        return Optional.empty();
    }

    /** The distinct locales this post's translation group is available in (SH53), sorted. */
    private List<String> availableLocales(ShPostUnified post) {
        String group = post.getTranslationGroupId() != null ? post.getTranslationGroupId() : post.getId();
        return shPostUnifiedRepository.findByTranslationGroupId(group).stream()
                .map(ShPostUnified::getLocale)
                .filter(l -> l != null && !l.isBlank())
                .distinct()
                .sorted()
                .toList();
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

    /**
     * Full-text search (SH52) over the post title, summary and raw attribute data,
     * matching a single query term. Honours the same per-site allow-list (SH2) and
     * environment rule (SH5: published-only in PROD, draft-preferred in PREVIEW) as
     * {@link #query}. When a single {@code siteId} is targeted, results are
     * restricted to that site's configured {@code searchablePostTypes} (when any
     * resolve to a real post-type); an unscoped search spans the token's allowed
     * sites, or every site when the token has no allow-list.
     */
    public ShCdaListing search(String q, String siteId, int page, int size) {
        Pageable pageable = pageable(page, size);
        if (q == null || q.isBlank()) {
            return new ShCdaListing(null, List.of(), List.of(), page, size, 0);
        }
        // The repository normalizes the term (SH131) — lower-casing and %-wrapping it
        // here as well would search for `%%term%%`, which matches nothing.
        Page<ShPostUnified> posts = searchPage(siteId, q, pageable);
        return new ShCdaListing(null, List.of(), summaries(posts), page, size, posts.getTotalElements());
    }

    private Page<ShPostUnified> searchPage(String siteId, String term, Pageable pageable) {
        if (siteId != null && !siteId.isBlank()) {
            if (!siteAllowed(siteId)) {
                return Page.empty(pageable);
            }
            List<String> types = searchablePostTypeIds(siteId);
            if (!types.isEmpty()) {
                return preview()
                        ? draftPreferred(shPostUnifiedRepository
                                .searchBySiteAndTypesAndStateIn(siteId, types, PREVIEW_STATES, term), pageable)
                        : shPostUnifiedRepository.searchBySiteAndTypesAndState(siteId, types, STATE, term, pageable);
            }
            return searchBySites(List.of(siteId), term, pageable);
        }
        // No explicit site: restrict to the token's allow-list when present, else all sites.
        Set<String> allowed = allowedSiteIds();
        if (allowed != null) {
            return allowed.isEmpty() ? Page.empty(pageable) : searchBySites(allowed, term, pageable);
        }
        return preview()
                ? draftPreferred(shPostUnifiedRepository.searchByStateIn(PREVIEW_STATES, term), pageable)
                : shPostUnifiedRepository.searchByState(STATE, term, pageable);
    }

    private Page<ShPostUnified> searchBySites(Collection<String> siteIds, String term, Pageable pageable) {
        return preview()
                ? draftPreferred(shPostUnifiedRepository.searchBySitesAndStateIn(siteIds, PREVIEW_STATES, term), pageable)
                : shPostUnifiedRepository.searchBySitesAndState(siteIds, STATE, term, pageable);
    }

    /**
     * The current token's site allow-list, or {@code null} when unrestricted (no
     * token, or a token whose allow-list is empty). Mirrors {@link #siteAllowed}.
     */
    private Set<String> allowedSiteIds() {
        return ShApiTokenContext.current()
                .map(ShApiTokenContext::siteIds)
                .filter(ids -> ids != null && !ids.isEmpty())
                .orElse(null);
    }

    /**
     * A site's configured searchable post-types (SH52) as post-type <b>ids</b>,
     * keeping only names that resolve to a real post-type — so a malformed/foreign
     * config degrades to "search all types" rather than silently returning nothing.
     *
     * <p>Ids, not names: the search query filters on {@code p.shPostType.id}, which
     * uses the FK column already on {@code sh_post_unified}. Filtering by name would
     * join {@code ShPostType} and trip the SH22 {@code @TenantId} hazard — see the
     * note in {@code ShPostUnifiedRepository}.</p>
     */
    private List<String> searchablePostTypeIds(String siteId) {
        List<String> names = shSiteRepository.findById(siteId)
                .map(ShSite::getSearchablePostTypes)
                .map(ShCdaService::parsePostTypeNames)
                .orElseGet(List::of);
        if (names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .map(shPostTypeRepository::findByName)
                .filter(Objects::nonNull)
                .map(ShPostType::getId)
                .distinct()
                .toList();
    }

    /**
     * Tolerant parse of the {@code searchablePostTypes} CLOB into post-type names.
     * Accepts a JSON string array ({@code ["News","Page"]}) or a plain
     * comma/semicolon/newline-separated list; bracket and quote noise is stripped.
     */
    private static List<String> parsePostTypeNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.replace("[", " ").replace("]", " ")
                .replace("\"", " ").replace("'", " ");
        return Arrays.stream(cleaned.split("[,;\\n\\r]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private Page<ShPostUnified> queryPage(String siteId, String folderId, String postType, Pageable pageable) {
        if (folderId != null && !folderId.isBlank()) {
            return shFolderRepository.findById(folderId)
                    .filter(folder -> siteAllowed(siteIdOf(folder)))
                    .map(folder -> pageByFolder(folder, pageable))
                    .orElse(Page.empty(pageable));
        }
        if (siteId == null || siteId.isBlank() || !siteAllowed(siteId)) {
            return Page.empty(pageable);
        }
        if (postType != null && !postType.isBlank()) {
            return pageBySiteAndType(siteId, postType, pageable);
        }
        return pageBySite(siteId, pageable);
    }

    private Page<ShPostUnified> pageByFolder(ShFolder folder, Pageable pageable) {
        return preview()
                ? draftPreferred(shPostUnifiedRepository
                        .findByShFolderAndStateIn(folder, PREVIEW_STATES), pageable)
                : shPostUnifiedRepository.findByShFolderAndState(folder, STATE, pageable);
    }

    /**
     * Posts of one post-type in a site. The public contract takes a post-type
     * <b>name</b> (`?postType=Article`), but the finder must filter by <b>id</b> —
     * filtering by name joins {@code ShPostType} and trips the SH22 {@code @TenantId}
     * hazard (see {@code ShPostUnifiedRepository}). An unknown name resolves to no
     * post-type and therefore to an empty page, which is the same answer the caller
     * would get for a real type with no content — never an error.
     */
    private Page<ShPostUnified> pageBySiteAndType(String siteId, String postType, Pageable pageable) {
        ShPostType type = shPostTypeRepository.findByName(postType);
        if (type == null) {
            return Page.empty(pageable);
        }
        return preview()
                ? draftPreferred(shPostUnifiedRepository
                        .findByShSite_IdAndShPostType_IdAndStateIn(siteId, type.getId(), PREVIEW_STATES), pageable)
                : shPostUnifiedRepository
                        .findByShSite_IdAndShPostType_IdAndState(siteId, type.getId(), STATE, pageable);
    }

    private Page<ShPostUnified> pageBySite(String siteId, Pageable pageable) {
        return preview()
                ? draftPreferred(shPostUnifiedRepository
                        .findByShSite_IdAndStateIn(siteId, PREVIEW_STATES), pageable)
                : shPostUnifiedRepository.findByShSite_IdAndState(siteId, STATE, pageable);
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

    /**
     * SH128 — walked, not read. Reading {@code folder.getShSite()} here made
     * {@link #siteAllowed} see {@code null} for every non-root folder, and
     * {@code allowsSite(null)} is {@code false} whenever a token carries an
     * allow-list: a scoped CDA token could not list the children of, or resolve the
     * path to, any folder below a site root.
     */
    private static String siteIdOf(ShFolder folder) {
        return ShFolderSite.idOf(folder);
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
        ShSite site = ShFolderSite.of(currentFolder);
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
