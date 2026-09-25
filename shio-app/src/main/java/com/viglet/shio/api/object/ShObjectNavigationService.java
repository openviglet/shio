/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.query.ShContentQuery;
import com.viglet.shio.api.query.ShContentQueryResolver;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.object.ShLiveObject;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

/**
 * Read-side navigation for the content browser, rebuilt for the unified post
 * model. Backs the React console's {@code /api/v2/object/{id}/list} and
 * {@code /path} endpoints (and {@code /api/v2/folder/{id}/path}).
 *
 * <p>A folder lists its child folders plus the unified posts it contains; a
 * site lists its root folders. Posts are deduplicated by logical id, preferring
 * the {@code DRAFT} row so the console always shows the latest editable
 * snapshot.</p>
 *
 * <p><b>SH120: the responses are DTOs, not entities.</b> {@code /path} returns the CDA's
 * own {@code ShCdaPath}, and {@code /list} returns rows whose overlapping fields carry the
 * CDA's names. Serializing {@code ShFolder}/{@code ShPostUnified} straight out was both a
 * third vocabulary for the same content and a payload that dragged the post-type's whole
 * field tree along to render a filename. {@link ShConsoleNavigationMapper} owns the
 * projection.</p>
 *
 * @author Alexandre Oliveira
 */
@Service
public class ShObjectNavigationService {

    /** What a caller that says nothing gets — the value the endpoint has defaulted to since SH165. */
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final ShObjectRepository shObjectRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShConsoleNavigationMapper mapper;
    /** SH1022 — names to ids, in the one place that decides what {@code type=Artcle} does. */
    private final ShContentQueryResolver contentQueryResolver;

    public ShObjectNavigationService(ShObjectRepository shObjectRepository, ShFolderRepository shFolderRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository,
                                     ShConsoleNavigationMapper mapper,
                                     ShContentQueryResolver contentQueryResolver) {
        this.shObjectRepository = shObjectRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.mapper = mapper;
        this.contentQueryResolver = contentQueryResolver;
    }

    /** The folder half of one listing: the rows to send, and how many there are in total. */
    private record Children(List<ShFolder> rows, long total) {
    }

    /**
     * The child folders one listing answers with (SH1025).
     *
     * <p>Whole unless a folder page was asked for, which is what keeps the move/copy picker
     * correct: it reads the folders off a {@code size=1} listing precisely because they have never
     * been paged, and a listing that started paging them by default would hand it one page of a
     * site and let it walk into a subtree it could not see.</p>
     *
     * <p>When a page <em>is</em> asked for, the <b>order comes from the database</b> on the same
     * axis the posts use. It has to: the browser applied its three sort options to an array it
     * held whole, which was correct exactly because it held it whole, and sorting a page
     * client-side sorts one page — the half-migration SH165 named when it moved the post order to
     * the server. A {@code q} filter still runs here, on the page: the folder name filter is a
     * substring over a set this small and pushing it down is SH240's question rather than this
     * one's.</p>
     */
    private Children children(ShFolder folder, ShContentQuery query, ShConsoleListingSort sort,
                              String text) {
        if (!query.pagesFolders()) {
            List<ShFolder> all = shFolderRepository.findByParentFolderOrderByPositionAsc(folder);
            return new Children(matching(all, text), all.size());
        }
        int index = Math.max(query.folderPage() == null ? 0 : query.folderPage(), 0);
        int size = Math.max(query.folderSize() == null ? DEFAULT_PAGE_SIZE : query.folderSize(), 1);
        Page<ShFolder> page = shFolderRepository.pageByParentFolder(folder,
                PageRequest.of(index, size, sort.folderSort()));
        return new Children(matching(page.getContent(), text),
                shFolderRepository.countChildren(folder));
    }

    /**
     * The folder ids one listing covers.
     *
     * <p>{@code subtree} is unsaid for every caller this endpoint had before SH1022, and unsaid
     * keeps what it has always done: the folder's own posts. Said {@code true}, it is the folder
     * and everything beneath it — the same word {@code /agent/find} spells, which defaults the
     * other way because "everything under /blog" is the question <em>that</em> call has always
     * answered. One word, one meaning, two defaults that were each already true.</p>
     */
    private List<String> folderScope(ShFolder folder, Boolean subtree) {
        if (subtree == null || !subtree) {
            return List.of(folder.getId());
        }
        List<String> ids = new ArrayList<>();
        collectSubtree(folder, ids);
        return ids;
    }

    private void collectSubtree(ShFolder folder, List<String> ids) {
        if (ids.contains(folder.getId())) {
            return;
        }
        ids.add(folder.getId());
        shFolderRepository.findByParentFolderOrderByPositionAsc(folder)
                .forEach(child -> collectSubtree(child, ids));
    }

    /** The whole folder, in title order — the pre-SH165 listing, kept for callers with no paging. */
    @Transactional(readOnly = true)
    public Optional<ShConsoleListing> list(String objectId) {
        return list(objectId, ShContentQuery.ALL.withPaging(0, Integer.MAX_VALUE));
    }

    /** One page, with only the four words this listing took before SH1022. */
    @Transactional(readOnly = true)
    public Optional<ShConsoleListing> list(String objectId, int page, int size,
                                           ShConsoleListingSort sort, String query) {
        return list(objectId, ShContentQuery.ALL.withPaging(page, size)
                .withSort(sort == null ? null : sort.key()).withQ(query));
    }

    /**
     * One page of an object's children (SH165).
     *
     * <p>Folders come back whole on every page and posts are paged — see
     * {@link ShConsoleListing} for why that asymmetry is the right one. The post page is
     * ordered, filtered and counted by the database
     * ({@code ShPostUnifiedRepository#pageConsoleRows}), so a folder with ten thousand posts
     * is no longer one query, one response and one render.</p>
     *
     * <p>The {@code query} filter applies to both halves — a folder by name, a post by title —
     * because the browser's search box has always filtered the combined list, and a search
     * that quietly stopped matching folders would be a regression dressed as a feature.</p>
     *
     * <p><b>SH1022 — the filters are the one content-query vocabulary.</b> {@code type},
     * {@code state}, {@code locale}, {@code modifiedSince}, {@code modifiedBefore},
     * {@code author} and {@code modifier} mean here exactly what they mean on
     * {@code /agent/find} and in {@code shio_find}, and they are predicates in the same
     * repository query — so "drafts changed this week by one person" is a listing on every
     * surface, and the curator and the agent cannot come to see different folders.</p>
     *
     * <p>Only the <em>posts</em> are filtered by them. A child folder has no post-type, no state
     * and no locale, so narrowing the folder list by a post's words would empty the navigation
     * every time a filter was set — which is the opposite of what a filter is for. The
     * {@code q} filter still applies to both halves, because the browser's search box has always
     * filtered the combined list.</p>
     *
     * @param query the filters, the order and the page; see {@link ShContentQuery}
     */
    @Transactional(readOnly = true)
    public Optional<ShConsoleListing> list(String objectId, ShContentQuery query) {
        int pageIndex = Math.max(query.page() == null ? 0 : query.page(), 0);
        int pageSize = Math.max(query.size() == null ? DEFAULT_PAGE_SIZE : query.size(), 1);
        ShConsoleListingSort sort = ShConsoleListingSort.of(query.sort());
        String text = query.q();
        String typeId = contentQueryResolver.typeId(query);
        ShPostState state = ShContentPerspective.parse(query.state()).row();
        return live(objectId).map(object -> switch (object) {
            case ShFolder folder -> {
                Page<ShPostUnified> posts = shPostUnifiedRepository.pageConsoleRows(
                        folderScope(folder, query.subtree()), text, typeId, state, query.locale(),
                        query.modifiedSince(), query.modifiedBefore(), query.author(),
                        query.modifier(), query.missingLocale(), query.submission(), query.agent(),
                        PageRequest.of(pageIndex, pageSize, sort.sort()));
                Children children = children(folder, query, sort, text);
                yield mapper.listing(folder.getId(), children.rows(),
                        posts.getContent(), pageIndex, pageSize, posts.getTotalElements(),
                        children.total());
            }
            // A site lists its root folders. It has no posts of its own — content lives in
            // folders — so this is not an empty answer, it is the answer.
            case ShSite site -> {
                List<ShFolder> roots = matching(
                        sortedByPosition(shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1)),
                        text);
                yield mapper.listing(site.getId(), roots, List.of(), pageIndex, pageSize, 0L,
                        roots.size());
            }
            default -> mapper.listing(objectId, List.of(), List.of(), pageIndex, pageSize, 0L, 0L);
        });
    }

    @Transactional(readOnly = true)
    public Optional<ShCdaPath> path(String objectId) {
        return live(objectId).map(object -> switch (object) {
            case ShFolder folder -> mapper.path(siteOf(folder), breadcrumb(folder), folder);
            case ShSite site -> mapper.path(site, List.of(), null);
            default -> mapper.path(null, List.of(), null);
        });
    }

    /**
     * Resolves an object id, refusing a folder that is in the trash (SH200).
     *
     * <p>Ids resolve polymorphically through {@link ShObjectRepository}, which knows nothing
     * about the folder trash — {@code ShFolderRepository} is where that exclusion lives. So a
     * trashed section is unreachable from any listing yet still openable by its own id, which
     * is how a curator ends up browsing content that is supposed to be deleted.</p>
     */
    private Optional<ShObject> live(String objectId) {
        return ShLiveObject.of(shObjectRepository, objectId);
    }

    /**
     * The folders whose name matches {@code query}, in the order they came in.
     *
     * <p>Filtered here rather than in SQL because folders are not paged: the set is already
     * loaded, so a second query would buy nothing, and keeping the predicate in one place means
     * the folder half and the post half cannot come to disagree about what "matches" is.</p>
     */
    private static List<ShFolder> matching(Iterable<ShFolder> folders, String query) {
        List<ShFolder> all = new ArrayList<>();
        folders.forEach(all::add);
        if (query == null || query.isBlank()) {
            return all;
        }
        String term = query.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(folder -> folder.getName() != null
                        && folder.getName().toLowerCase(Locale.ROOT).contains(term))
                .toList();
    }

    /** Ancestor chain from the site's root folder down to (and including) {@code folder}. */
    private List<ShFolder> breadcrumb(ShFolder folder) {
        List<ShFolder> chain = new ArrayList<>();
        ShFolder current = folder;
        while (current != null) {
            chain.add(current);
            current = current.getParentFolder();
        }
        Collections.reverse(chain);
        return chain;
    }

    /** The owning site, found by walking up to the root folder (SH128). */
    private ShSite siteOf(ShFolder folder) {
        return ShFolderSite.of(folder);
    }

    private static List<ShFolder> sortedByPosition(Iterable<ShFolder> folders) {
        List<ShFolder> list = new ArrayList<>();
        folders.forEach(list::add);
        // SH722 — `position` repeats exactly as `ordinal` does, and navigation is rendered.
        list.sort(Comparator.comparingInt(ShObject::getPosition)
                .thenComparing(o -> o.getId() == null ? "" : o.getId()));
        return list;
    }

}
