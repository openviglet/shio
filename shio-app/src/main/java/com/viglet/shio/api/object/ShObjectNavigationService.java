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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
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

    private final ShObjectRepository shObjectRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShConsoleNavigationMapper mapper;

    public ShObjectNavigationService(ShObjectRepository shObjectRepository, ShFolderRepository shFolderRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository,
                                     ShConsoleNavigationMapper mapper) {
        this.shObjectRepository = shObjectRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.mapper = mapper;
    }

    /** The whole folder, in title order — the pre-SH165 listing, kept for callers with no paging. */
    @Transactional(readOnly = true)
    public Optional<ShConsoleListing> list(String objectId) {
        return list(objectId, 0, Integer.MAX_VALUE, ShConsoleListingSort.TITLE, null);
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
     * @param sort  the post order; folders keep their manual {@code position} order, which is
     *              a property of the folder rather than an axis a caller picks
     * @param query a case-insensitive substring filter, or {@code null} for everything
     */
    @Transactional(readOnly = true)
    public Optional<ShConsoleListing> list(String objectId, int page, int size,
                                           ShConsoleListingSort sort, String query) {
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.max(size, 1);
        return live(objectId).map(object -> switch (object) {
            case ShFolder folder -> {
                Page<ShPostUnified> posts = shPostUnifiedRepository.pageConsoleRows(folder, query,
                        PageRequest.of(pageIndex, pageSize, sort.sort()));
                yield mapper.listing(folder.getId(),
                        matching(shFolderRepository.findByParentFolderOrderByPositionAsc(folder), query),
                        posts.getContent(), pageIndex, pageSize, posts.getTotalElements());
            }
            // A site lists its root folders. It has no posts of its own — content lives in
            // folders — so this is not an empty answer, it is the answer.
            case ShSite site -> mapper.listing(site.getId(),
                    matching(sortedByPosition(shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1)),
                            query),
                    List.of(), pageIndex, pageSize, 0L);
            default -> mapper.listing(objectId, List.of(), List.of(), pageIndex, pageSize, 0L);
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
        return shObjectRepository.findById(objectId)
                .filter(object -> !(object instanceof ShFolder folder) || !folder.isTrashed());
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
        String term = query.trim().toLowerCase();
        return all.stream()
                .filter(folder -> folder.getName() != null
                        && folder.getName().toLowerCase().contains(term))
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
        list.sort(Comparator.comparingInt(ShObject::getPosition));
        return list;
    }

}
