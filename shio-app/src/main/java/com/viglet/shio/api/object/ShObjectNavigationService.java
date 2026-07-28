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

import com.viglet.shio.api.cda.dto.ShCdaPath;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.object.ShFolderSite;
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

    private static final List<ShPostState> ALL_STATES = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

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

    @Transactional(readOnly = true)
    public Optional<ShConsoleListing> list(String objectId) {
        return live(objectId).map(object -> switch (object) {
            case ShFolder folder -> mapper.listing(folder.getId(),
                    new ArrayList<>(shFolderRepository.findByParentFolderOrderByPositionAsc(folder)),
                    postsInFolder(folder));
            // A site lists its root folders. It has no posts of its own — content lives in
            // folders — so this is not an empty answer, it is the answer.
            case ShSite site -> mapper.listing(site.getId(),
                    sortedByPosition(shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1)),
                    List.of());
            default -> mapper.listing(objectId, List.of(), List.of());
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
