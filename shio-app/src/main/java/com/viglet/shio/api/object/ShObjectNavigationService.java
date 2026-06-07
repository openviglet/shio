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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * @author Alexandre Oliveira
 */
@Service
public class ShObjectNavigationService {

    private static final List<ShPostState> ALL_STATES = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShObjectRepository shObjectRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShObjectNavigationService(ShObjectRepository shObjectRepository, ShFolderRepository shFolderRepository,
                                     ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shObjectRepository = shObjectRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ShObjectList> list(String objectId) {
        return shObjectRepository.findById(objectId).map(object -> switch (object) {
            case ShFolder folder -> new ShObjectList(
                    new ArrayList<>(shFolderRepository.findByParentFolderOrderByPositionAsc(folder)),
                    postsInFolder(folder),
                    siteOf(folder),
                    breadcrumb(folder));
            case ShSite site -> new ShObjectList(
                    sortedByPosition(shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1)),
                    List.of(),
                    site,
                    List.of());
            default -> new ShObjectList(List.of(), List.of(), null, List.of());
        });
    }

    @Transactional(readOnly = true)
    public Optional<ShObjectPath> path(String objectId) {
        return shObjectRepository.findById(objectId).map(object -> switch (object) {
            case ShFolder folder -> new ShObjectPath(breadcrumb(folder), siteOf(folder), folder);
            case ShSite site -> new ShObjectPath(List.of(), site, null);
            default -> new ShObjectPath(List.of(), null, null);
        });
    }

    /** All posts in the folder, one row per logical id, DRAFT preferred over PUBLISHED. */
    private List<ShPostUnified> postsInFolder(ShFolder folder) {
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        for (ShPostUnified post : shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL_STATES)) {
            ShPostUnified existing = byId.get(post.getId());
            if (existing == null || post.getState() == ShPostState.DRAFT) {
                byId.put(post.getId(), post);
            }
        }
        List<ShPostUnified> posts = new ArrayList<>(byId.values());
        posts.sort(Comparator.comparing(p -> p.getTitle() == null ? "" : p.getTitle(),
                String.CASE_INSENSITIVE_ORDER));
        return posts;
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

    /** The owning site, found by walking up to the root folder (which carries shSite). */
    private ShSite siteOf(ShFolder folder) {
        ShFolder current = folder;
        while (current != null) {
            if (current.getShSite() != null) {
                return current.getShSite();
            }
            current = current.getParentFolder();
        }
        return null;
    }

    private static List<ShFolder> sortedByPosition(Iterable<ShFolder> folders) {
        List<ShFolder> list = new ArrayList<>();
        folders.forEach(list::add);
        list.sort(Comparator.comparingInt(ShObject::getPosition));
        return list;
    }

    /** Response shape consumed by the content browser's {@code /list} endpoint. */
    public record ShObjectList(List<ShFolder> shFolders, List<ShPostUnified> shPosts, ShSite shSite,
                               List<ShFolder> breadcrumb) {
    }

    /** Response shape consumed by the breadcrumb {@code /path} endpoint. */
    public record ShObjectPath(List<ShFolder> breadcrumb, ShSite shSite, ShFolder currentFolder) {
    }
}
