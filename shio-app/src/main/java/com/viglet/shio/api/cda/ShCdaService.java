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

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.cda.dto.ShCdaRedirect;
import com.viglet.shio.api.cda.dto.ShCdaSite;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.api.post.revision.ShRevisionRow;
import com.viglet.shio.api.post.unified.ShPostStateNotFoundException;
import com.viglet.shio.api.site.ShSiteLocaleService;
import com.viglet.shio.api.site.ShSiteLocales;
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
import com.viglet.shio.url.ShFurlSpellings;

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
// SH990 — `noRollbackFor` since the service throws teaching refusals now (a draft asked of a
// production token, a revision asked of a listing), and a GraphQL fetcher that catches one must not
// find its transaction marked rollback-only at commit.
@Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
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
    /** SH990 — what a page said at a revision, for a preview token's {@code state=rev:<n>}. */
    private final ShPostRevisionService shPostRevisionService;
    /** SH1056 — which languages a site is in, the one resolver every surface reads. */
    private final ShSiteLocaleService shSiteLocaleService;
    /** SH1285 — whether the token's owner may read a members-only post. */
    private final ShContentAclService shContentAclService;

    public ShCdaService(ShSiteRepository shSiteRepository,
                        ShFolderRepository shFolderRepository,
                        ShPostUnifiedRepository shPostUnifiedRepository,
                        ShPostTypeRepository shPostTypeRepository,
                        ShCdaMapper mapper,
                        ShPostRevisionService shPostRevisionService,
                        ShSiteLocaleService shSiteLocaleService,
                        ShContentAclService shContentAclService) {
        this.shContentAclService = shContentAclService;
        this.shPostRevisionService = shPostRevisionService;
        this.shSiteLocaleService = shSiteLocaleService;
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

    /**
     * Every site this token may read, each with the languages it is in (SH1123).
     *
     * <p>The policies resolve in one batch: a declared one costs nothing and the undeclared ones
     * share a query, so the listing does not become a query per site.</p>
     */
    public List<ShCdaSite> listSites() {
        List<ShSite> sites = shSiteRepository.findAll().stream()
                .filter(site -> siteAllowed(site.getId()))
                .toList();
        Map<String, ShSiteLocales> locales = shSiteLocaleService.of(sites, readStates());
        return sites.stream()
                .map(site -> mapper.toSite(site, rootFolderId(site), locales.get(site.getId())))
                .toList();
    }

    /**
     * One site, with the languages it is in (SH1123).
     *
     * <p>An undeclared site's languages are read off the rows this token can see — a production
     * token is not told the site offers a language only a draft is written in, because every
     * switcher entry it built from that would link to a page it cannot fetch.</p>
     */
    public Optional<ShCdaSite> getSite(String siteId) {
        if (!siteAllowed(siteId)) {
            return Optional.empty();
        }
        return shSiteRepository.findById(siteId)
                .map(site -> mapper.toSite(site, rootFolderId(site),
                        shSiteLocaleService.of(List.of(site), readStates()).get(site.getId())));
    }

    /**
     * SH1133 — the site's redirect table, for a front end that owns its URLs.
     *
     * <p>A {@code Redirect} post is served by the instance's renderer, which a headless site never
     * reaches: Next.js answers those URLs and asks this API for content by address, so until now it
     * had no way to learn which addresses moved. The table is small, site-scoped and cacheable, so a
     * front end can hold it and consult it before a 404 rather than asking per request.</p>
     *
     * <p>The states are the token's own, draft-preferred for a preview token, by the rule every other
     * read here follows: a production front end must not redirect to a page it cannot fetch because
     * the redirect exists only in a draft. Ordered by {@code from} (P5), so the same table is the same
     * bytes.</p>
     *
     * @return empty when the token may not read the site or there is no such site — the 404 every
     *         other site read gives, so a scoped key cannot tell "wrong site" from "missing"
     */
    public Optional<List<ShCdaRedirect>> getRedirects(String siteId) {
        if (!siteAllowed(siteId) || shSiteRepository.findById(siteId).isEmpty()) {
            return Optional.empty();
        }
        ShPostType type = shPostTypeRepository.findByName(ShSystemPostType.REDIRECT);
        if (type == null) {
            return Optional.of(List.of());
        }
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        for (ShPostUnified row : shPostUnifiedRepository
                .findByShSite_IdAndShPostType_IdAndStateIn(siteId, type.getId(), readStates())) {
            byId.merge(row.getId(), row, (existing, candidate) ->
                    existing.getState() == ShPostState.DRAFT ? existing : candidate);
        }
        return Optional.of(byId.values().stream()
                .map(mapper::toRedirect)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ShCdaRedirect::from))
                .toList());
    }

    public Optional<ShCdaListing> listChildren(String folderId, int page, int size) {
        return listChildren(folderId, page, size, null);
    }

    /** As above, over the rows {@code state} names (SH990); a revision is refused for a listing. */
    public Optional<ShCdaListing> listChildren(String folderId, int page, int size, String state) {
        return listChildren(folderId, page, size, state, null);
    }

    /**
     * As above, in one locale (SH1057) — the posts only, since a folder has none.
     *
     * @param locale a tag such as {@code pt-br}, or null/blank for every locale
     */
    public Optional<ShCdaListing> listChildren(String folderId, int page, int size, String state,
                                               String locale) {
        List<ShPostState> states = listingStates(state);
        return shFolderRepository.findById(folderId)
                .filter(folder -> siteAllowed(siteIdOf(folder)))
                .filter(this::folderReadable)
                .map(folder -> {
            List<ShCdaFolder> folders = shFolderRepository.findByParentFolderOrderByPositionAsc(folder).stream()
                    .filter(this::folderReadable)
                    .map(mapper::toFolder)
                    .toList();
            Page<ShPostUnified> posts = pageByFolder(folder, states, localeOf(locale), pageable(page, size));
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
        return resolvePost(objectId, readStates())
                .filter(post -> siteAllowed(siteIdOf(post)))
                .filter(this::readable)
                .map(ShPostUnified::getShFolder)
                .map(this::buildPath);
    }

    public Optional<ShCdaPost> getPost(String id) {
        return getPost(id, null);
    }

    /**
     * One post from the perspective {@code state} names (SH990).
     *
     * <p>The site allow-list is applied to the post found, whichever row that is, so a scoped token
     * asking for a draft of a site outside its list gets the same empty answer it gets for the
     * published row. A revision is found draft-preferred, as a preview token reads, and answered as
     * what the page said at that number at the address it is at now.</p>
     */
    public Optional<ShCdaPost> getPost(String id, String state) {
        ShContentPerspective perspective = perspective(state);
        if (perspective.isRevision()) {
            return resolvePost(id, PREVIEW_STATES)
                    .filter(post -> siteAllowed(siteIdOf(post)))
                    .filter(this::readable)
                    .map(post -> mapper.toPost(asRevision(post, perspective.revision()),
                            availableLocales(post)));
        }
        return resolvePost(id, statesFor(perspective))
                .filter(post -> siteAllowed(siteIdOf(post)))
                .filter(this::readable)
                .map(post -> mapper.toPost(post, availableLocales(post)));
    }

    /**
     * SH1285 — whether this token's owner may read the post, under a members-only gate on it or
     * up its folder chain. A post they may not read is answered as absent, the 404 a missing post
     * gets, so the refusal does not confirm the post exists. The single reads ask this; the
     * listings, search and GraphQL's {@code posts} do not yet.
     */
    private boolean readable(ShPostUnified post) {
        return shContentAclService.visibility(post.getId()).allowed();
    }

    /** SH1285 — whether a members-only gate applies to this post, so its response is private. */
    public boolean isGated(String postId) {
        return shContentAclService.visibility(postId).gated();
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
        return getPostByUrl(siteId, url, locale, null);
    }

    /** As above, from the perspective {@code state} names (SH990) — see {@link #getPost(String, String)}. */
    public Optional<ShCdaPost> getPostByUrl(String siteId, String url, String locale, String state) {
        ShContentPerspective perspective = perspective(state);
        if (!siteAllowed(siteId)) {
            return Optional.empty();
        }
        List<ShPostState> states = perspective.isRevision() ? PREVIEW_STATES : statesFor(perspective);
        for (String candidate : ShFurlSpellings.candidates(url)) {
            for (ShPostState rowState : states) {
                Optional<ShPostUnified> found = (locale != null && !locale.isBlank())
                        ? shPostUnifiedRepository.findByShSite_IdAndFurlAndStateAndLocale(siteId, candidate, rowState, locale)
                        : shPostUnifiedRepository.findByShSite_IdAndFurlAndState(siteId, candidate, rowState);
                if (found.isPresent()) {
                    return found.filter(this::readable).map(post -> mapper.toPost(perspective.isRevision()
                            ? asRevision(post, perspective.revision()) : post, availableLocales(post)));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The distinct locales this post's translation group is available in (SH53), sorted.
     *
     * <h2>SH1128 — over the states this token reads, and through the batch</h2>
     *
     * <p>It counted every state, so the moment a curator started a Portuguese translation —
     * {@code translate} creates the sibling as a DRAFT — every production token's read of the English
     * page said it exists in {@code pt-br}. A switcher built from that field linked to a page the same
     * token gets a 404 for, and it stayed that way for as long as the translation was being written.
     * SH1123 fixed the same defect one level up for a site's derived languages; this is the
     * post-level field, which kept the old rule.</p>
     *
     * <p>It reads through {@link #availableLocales(List)} rather than a finder of its own, so there is
     * <b>one</b> reading of the rule instead of two that agree until somebody changes one. The batch
     * loads two columns where this used to load whole documents, so the single-post path got cheaper
     * on the way.</p>
     */
    private List<String> availableLocales(ShPostUnified post) {
        return availableLocales(List.of(post)).getOrDefault(groupOf(post), List.of());
    }

    public ShCdaListing query(String siteId, String folderId, String postType, int page, int size) {
        return query(siteId, folderId, postType, page, size, null);
    }

    /** As above, over the rows {@code state} names (SH990). */
    public ShCdaListing query(String siteId, String folderId, String postType, int page, int size,
                              String state) {
        return query(siteId, folderId, postType, page, size, state, null);
    }

    /** As above, in one locale (SH1057); null or blank is every locale. */
    public ShCdaListing query(String siteId, String folderId, String postType, int page, int size,
                              String state, String locale) {
        Page<ShPostUnified> posts = queryPage(siteId, folderId, postType, listingStates(state),
                localeOf(locale), pageable(page, size));
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
        return queryPosts(siteId, folderId, postType, page, size, null);
    }

    /** As above, over the rows {@code state} names (SH990) — GraphQL's {@code posts(state:)}. */
    public List<ShCdaPost> queryPosts(String siteId, String folderId, String postType, int page, int size,
                                      String state) {
        return queryPosts(siteId, folderId, postType, page, size, state, null);
    }

    /**
     * As above, in one locale — GraphQL's {@code posts(locale:)} (SH1129).
     *
     * <p>Each post carries the locales its translation group is in, read for the whole page in one
     * query. The single-post reads ask per post, which is one query for one post; on a list the same
     * shape is one query per row, and a page of fifty was fifty-one.</p>
     */
    public List<ShCdaPost> queryPosts(String siteId, String folderId, String postType, int page, int size,
                                      String state, String locale) {
        List<ShPostUnified> rows = readableOnly(queryPage(siteId, folderId, postType, listingStates(state),
                localeOf(locale), pageable(page, size)).getContent());
        Map<String, List<String>> byGroup = availableLocales(rows);
        return rows.stream()
                .map(post -> mapper.toPost(post, byGroup.getOrDefault(groupOf(post), List.of())))
                .toList();
    }

    /**
     * SH1129 — the locales every row's translation group is in, in one query for the page.
     *
     * <p>SH1128 — and only in the states {@link #readStates()} names, which is the whole fix: a
     * production token is never told a group is available in a language it cannot fetch a row of. By
     * the <em>token</em> rather than by the request's {@code state=}, because the question the field
     * answers is "which of these can I follow a link to", and that is decided by the credential. A
     * preview token looking at published rows still sees the draft translation, which is correct — it
     * can fetch it.</p>
     */
    private Map<String, List<String>> availableLocales(List<ShPostUnified> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        Set<String> groups = new java.util.LinkedHashSet<>();
        rows.forEach(post -> groups.add(groupOf(post)));
        Map<String, java.util.TreeSet<String>> sorted = new LinkedHashMap<>();
        for (Object[] row : shPostUnifiedRepository
                .findDistinctLocalesByTranslationGroupIdInAndStateIn(groups, readStates())) {
            sorted.computeIfAbsent((String) row[0], group -> new java.util.TreeSet<>()).add((String) row[1]);
        }
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        sorted.forEach((group, locales) -> byGroup.put(group, List.copyOf(locales)));
        return byGroup;
    }

    /** The translation group a post belongs to — its own id when it was never translated. */
    private static String groupOf(ShPostUnified post) {
        return post.getTranslationGroupId() != null ? post.getTranslationGroupId() : post.getId();
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
        return search(q, siteId, page, size, null);
    }

    /** As above, over the rows {@code state} names (SH990). */
    public ShCdaListing search(String q, String siteId, int page, int size, String state) {
        return search(q, siteId, page, size, state, null);
    }

    /** As above, in one locale (SH1057); null or blank is every locale. */
    public ShCdaListing search(String q, String siteId, int page, int size, String state, String locale) {
        List<ShPostState> states = listingStates(state);
        Pageable pageable = pageable(page, size);
        if (q == null || q.isBlank()) {
            return new ShCdaListing(null, List.of(), List.of(), page, size, 0);
        }
        // The repository normalizes the term (SH131) — lower-casing and %-wrapping it
        // here as well would search for `%%term%%`, which matches nothing.
        Page<ShPostUnified> posts = searchPage(siteId, q, states, localeOf(locale), pageable);
        return new ShCdaListing(null, List.of(), summaries(posts), page, size, posts.getTotalElements());
    }

    private Page<ShPostUnified> searchPage(String siteId, String term, List<ShPostState> states,
                                           String locale, Pageable pageable) {
        if (siteId != null && !siteId.isBlank()) {
            if (!siteAllowed(siteId)) {
                return Page.empty(pageable);
            }
            List<String> types = searchablePostTypeIds(siteId);
            if (!types.isEmpty()) {
                return states.size() > 1
                        ? draftPreferred(shPostUnifiedRepository
                                .searchBySiteAndTypesAndStateIn(siteId, types, states, term, locale), pageable)
                        : shPostUnifiedRepository.searchBySiteAndTypesAndState(siteId, types, states.get(0),
                                term, locale, pageable);
            }
            return searchBySites(List.of(siteId), term, states, locale, pageable);
        }
        // No explicit site: restrict to the token's allow-list when present, else all sites.
        Set<String> allowed = allowedSiteIds();
        if (allowed != null) {
            return allowed.isEmpty() ? Page.empty(pageable)
                    : searchBySites(allowed, term, states, locale, pageable);
        }
        return states.size() > 1
                ? draftPreferred(shPostUnifiedRepository.searchByStateIn(states, term, locale), pageable)
                : shPostUnifiedRepository.searchByState(states.get(0), term, locale, pageable);
    }

    private Page<ShPostUnified> searchBySites(Collection<String> siteIds, String term,
                                              List<ShPostState> states, String locale, Pageable pageable) {
        return states.size() > 1
                ? draftPreferred(shPostUnifiedRepository.searchBySitesAndStateIn(siteIds, states, term, locale),
                        pageable)
                : shPostUnifiedRepository.searchBySitesAndState(siteIds, states.get(0), term, locale, pageable);
    }

    /**
     * SH1057 — a locale filter, or null for none: blank is "not asked" rather than "the empty tag",
     * which is what a front end sending `?locale=` from an unset variable means.
     */
    private static String localeOf(String locale) {
        return locale == null || locale.isBlank() ? null : locale.trim();
    }

    /** SH1057 — the rows of one locale, for the draft-preferred reads that page in memory. */
    private static List<ShPostUnified> inLocale(List<ShPostUnified> rows, String locale) {
        return locale == null ? rows
                : rows.stream().filter(row -> locale.equals(row.getLocale())).toList();
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

    private Page<ShPostUnified> queryPage(String siteId, String folderId, String postType,
                                          List<ShPostState> states, String locale, Pageable pageable) {
        if (folderId != null && !folderId.isBlank()) {
            return shFolderRepository.findById(folderId)
                    .filter(folder -> siteAllowed(siteIdOf(folder)))
                    .filter(this::folderReadable)
                    .map(folder -> pageByFolder(folder, states, locale, pageable))
                    .orElse(Page.empty(pageable));
        }
        if (siteId == null || siteId.isBlank() || !siteAllowed(siteId)) {
            return Page.empty(pageable);
        }
        if (postType != null && !postType.isBlank()) {
            return pageBySiteAndType(siteId, postType, states, locale, pageable);
        }
        return pageBySite(siteId, states, locale, pageable);
    }

    /**
     * One page of a folder's posts over {@code states}: two states is the draft-preferred read (SH5's
     * preview), one is that state alone. Every listing below takes its states the same way, which is
     * what let SH990 add a perspective without a second copy of each query.
     */
    private Page<ShPostUnified> pageByFolder(ShFolder folder, List<ShPostState> states, String locale,
                                             Pageable pageable) {
        if (states.size() > 1) {
            return draftPreferred(inLocale(shPostUnifiedRepository
                    .findByShFolderAndStateIn(folder, states), locale), pageable);
        }
        return locale == null
                ? shPostUnifiedRepository.findByShFolderAndState(folder, states.get(0), pageable)
                : shPostUnifiedRepository.findByShFolderAndStateAndLocale(folder, states.get(0), locale, pageable);
    }

    /**
     * Posts of one post-type in a site. The public contract takes a post-type
     * <b>name</b> (`?postType=Article`), but the finder must filter by <b>id</b> —
     * filtering by name joins {@code ShPostType} and trips the SH22 {@code @TenantId}
     * hazard (see {@code ShPostUnifiedRepository}). An unknown name resolves to no
     * post-type and therefore to an empty page, which is the same answer the caller
     * would get for a real type with no content — never an error.
     */
    private Page<ShPostUnified> pageBySiteAndType(String siteId, String postType, List<ShPostState> states,
                                                  String locale, Pageable pageable) {
        ShPostType type = shPostTypeRepository.findByName(postType);
        if (type == null) {
            return Page.empty(pageable);
        }
        if (states.size() > 1) {
            return draftPreferred(inLocale(shPostUnifiedRepository
                    .findByShSite_IdAndShPostType_IdAndStateIn(siteId, type.getId(), states), locale), pageable);
        }
        return locale == null
                ? shPostUnifiedRepository
                        .findByShSite_IdAndShPostType_IdAndState(siteId, type.getId(), states.get(0), pageable)
                : shPostUnifiedRepository.findByShSite_IdAndShPostType_IdAndStateAndLocale(
                        siteId, type.getId(), states.get(0), locale, pageable);
    }

    private Page<ShPostUnified> pageBySite(String siteId, List<ShPostState> states, String locale,
                                           Pageable pageable) {
        if (states.size() > 1) {
            return draftPreferred(inLocale(shPostUnifiedRepository
                    .findByShSite_IdAndStateIn(siteId, states), locale), pageable);
        }
        return locale == null
                ? shPostUnifiedRepository.findByShSite_IdAndState(siteId, states.get(0), pageable)
                : shPostUnifiedRepository.findByShSite_IdAndStateAndLocale(siteId, states.get(0), locale, pageable);
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

    /**
     * The {@code state=} grammar, as this token may use it (SH990).
     *
     * <p>{@code published} is what every token already reads. A draft or a revision is what SH5 gave a
     * <b>preview</b> token and nothing else, so a production token asking for one is refused rather
     * than quietly answered with the published row — which would look like a draft that happens to
     * match what is live, the one wrong answer a front end building a preview cannot detect. The
     * refusal depends on the token alone and says nothing about a site or a post.</p>
     */
    private ShContentPerspective perspective(String state) {
        // SH993 — the delivery API serves what a visitor may see; "the version a launch replaced" is
        // a question for the agent surface, and answering it here would be a second history.
        ShContentPerspective perspective = ShContentPerspective.parse(state)
                .requireVersion("the delivery API", "GET /api/v2/agent/read?address=<address>&state=release:<name>");
        if ((perspective.row() == ShPostState.DRAFT || perspective.isRevision()) && !preview()) {
            throw new PreviewTokenRequiredException(state);
        }
        return perspective;
    }

    /** The row states a perspective reads: its row, or the token's default when it named none. */
    private List<ShPostState> statesFor(ShContentPerspective perspective) {
        return perspective.row() != null ? List.of(perspective.row()) : readStates();
    }

    /** A listing's states; a revision is a version of one page and has no set to list. */
    private List<ShPostState> listingStates(String state) {
        return statesFor(perspective(state).requireRow("the CDA's listings",
                "GET /api/v2/cda/post/{id}?state=rev:<n>"));
    }

    /** The post as it read at a revision, at the address it is at now (SH986's store, SH990's read). */
    private ShPostUnified asRevision(ShPostUnified present, int number) {
        return shPostRevisionService.revision(present.getId(), number)
                .map(revision -> shPostRevisionService.asOf(revision, present))
                .orElseThrow(() -> ShPostStateNotFoundException.noSuchRevision(present.getId(), number,
                        shPostRevisionService.rows(present.getId()).stream()
                                .map(ShRevisionRow::version).toList()));
    }

    /** First existing row for {@code id} across {@code states}, in their order. */
    private Optional<ShPostUnified> resolvePost(String id, List<ShPostState> states) {
        for (ShPostState state : states) {
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

    /** A 403: a draft or a revision asked of a token that reads only what is published (SH990). */
    public static class PreviewTokenRequiredException extends ShTeachingException {

        public PreviewTokenRequiredException(String sent) {
            super(ShProblem.of("state-needs-preview-token", HttpStatus.FORBIDDEN,
                            "`state=" + sent + "` needs a preview token")
                    .withDetail("This token reads the production environment, which is what is "
                            + "published. A draft or a revision is readable by a PREVIEW token, the "
                            + "one a front end's preview mode is configured with.")
                    .withAllowed(List.of("published"))
                    .withFix("Send `state=published`, or omit `state`, with this token; read drafts "
                            + "and revisions with a token whose environment is PREVIEW.")
                    .withExample("state=published"));
        }
    }

    private static String siteIdOf(ShPostUnified post) {
        ShSite site = post != null ? post.getShSite() : null;
        return site != null ? site.getId() : null;
    }

    private List<ShCdaPostSummary> summaries(Page<ShPostUnified> posts) {
        return readableOnly(posts.getContent()).stream().map(mapper::toSummary).toList();
    }

    /**
     * SH1296 — the rows of a listing this token's owner may read, in order.
     *
     * <p>Every listing passes through here, so a members-only post never reaches a reader outside
     * its groups through a folder, a query, a search or GraphQL's {@code posts}. On an instance with
     * no gate this is one count and no per-row walk. A site-wide page's {@code total} is still the
     * query's count, so a reader can infer how many gated posts a site holds but never read one.</p>
     */
    private List<ShPostUnified> readableOnly(List<ShPostUnified> rows) {
        if (rows.isEmpty() || !shContentAclService.anyGates()) {
            return rows;
        }
        return rows.stream().filter(this::readable).toList();
    }

    /** SH1296 — whether this token's owner may read what is in this folder. */
    private boolean folderReadable(ShFolder folder) {
        return !shContentAclService.anyGates() || shContentAclService.visibility(folder.getId()).allowed();
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
        // SH279 — was `findFirst()` over a Set: the delivery API's idea of "the root" was the
        // database's choice, and a CDA listing is the one place where picking the other root would
        // be visible to a visitor rather than to a curator.
        return shFolderRepository.findRootFolder(site)
                .map(ShFolder::getId)
                .orElse(null);
    }

    private static Pageable pageable(int page, int size) {
        int safeSize = size <= 0 ? 50 : size;
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize);
    }
}
