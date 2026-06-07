/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import com.viglet.shio.url.ShURLFormatter;

/**
 * Move/copy of content objects across the tree, rebuilt for the unified post
 * model. Backs {@code PUT /api/v2/object/moveto/{dest}} and {@code /copyto/{dest}}.
 *
 * <p>Folder ids resolve through {@link ShObjectRepository} (folders are
 * {@link ShObject} subclasses); post ids resolve through
 * {@link ShPostUnifiedRepository} (posts live in their own table and are
 * <em>not</em> {@code ShObject}s). A copy duplicates both the DRAFT and
 * PUBLISHED rows of a post under a freshly generated logical id; a folder copy
 * recurses into its sub-folders and posts.</p>
 *
 * @author Alexandre Oliveira
 */
@Service
public class ShObjectWriteService {

    private static final List<ShPostState> ALL_STATES = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShObjectRepository shObjectRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShObjectWriteService(ShObjectRepository shObjectRepository, ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shObjectRepository = shObjectRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    @Transactional
    public List<ShObject> moveTo(String destId, List<String> ids) {
        ShObject dest = shObjectRepository.findById(destId).orElse(null);
        List<ShObject> moved = new ArrayList<>();
        if (dest == null) {
            return moved;
        }
        for (String id : ids) {
            ShObject object = shObjectRepository.findById(id).orElse(null);
            if (object instanceof ShFolder folder) {
                reparent(folder, dest);
                moved.add(shFolderRepository.save(folder));
            } else if (dest instanceof ShFolder destFolder) {
                for (ShPostUnified row : shPostUnifiedRepository.findById(id)) {
                    row.setShFolder(destFolder);
                    row.setShSite(siteOf(destFolder));
                    row.setFurl(ShURLFormatter.format(safe(row.getTitle())));
                    shPostUnifiedRepository.save(row);
                }
            }
        }
        return moved;
    }

    @Transactional
    public List<ShObject> copyTo(String destId, List<String> ids) {
        ShObject dest = shObjectRepository.findById(destId).orElse(null);
        List<ShObject> copies = new ArrayList<>();
        if (dest == null) {
            return copies;
        }
        for (String id : ids) {
            ShObject object = shObjectRepository.findById(id).orElse(null);
            if (object instanceof ShFolder folder) {
                copies.add(copyFolder(folder, dest));
            } else if (dest instanceof ShFolder destFolder) {
                copyPost(id, destFolder);
            }
        }
        return copies;
    }

    private void reparent(ShFolder folder, ShObject dest) {
        if (dest instanceof ShFolder destFolder) {
            folder.setParentFolder(destFolder);
            folder.setRootFolder((byte) 0);
            folder.setShSite(null);
        } else if (dest instanceof ShSite destSite) {
            folder.setParentFolder(null);
            folder.setRootFolder((byte) 1);
            folder.setShSite(destSite);
        }
        folder.setFurl(ShURLFormatter.format(safe(folder.getName())));
    }

    private ShFolder copyFolder(ShFolder source, ShObject dest) {
        ShFolder copy = new ShFolder();
        copy.setName(source.getName());
        copy.setFurl(ShURLFormatter.format(safe(source.getName())));
        copy.setOwner(source.getOwner());
        copy.setDate(Instant.now());
        reparent(copy, dest);
        ShFolder saved = shFolderRepository.save(copy);

        for (ShPostUnified row : dedupById(shPostUnifiedRepository.findByShFolderAndStateIn(source, ALL_STATES))) {
            copyPost(row.getId(), saved);
        }
        for (ShFolder child : shFolderRepository.findByParentFolder(source)) {
            copyFolder(child, saved);
        }
        return saved;
    }

    private void copyPost(String postId, ShFolder destFolder) {
        String newId = UUID.randomUUID().toString();
        for (ShPostUnified source : shPostUnifiedRepository.findById(postId)) {
            ShPostUnified copy = new ShPostUnified();
            copy.setId(newId);
            copy.setState(source.getState());
            copy.setShPostType(source.getShPostType());
            copy.setShFolder(destFolder);
            copy.setShSite(siteOf(destFolder));
            copy.setTitle(source.getTitle());
            copy.setSummary(source.getSummary());
            copy.setFurl(ShURLFormatter.format(safe(source.getTitle())));
            copy.setOwner(source.getOwner());
            copy.setData(source.getData());
            copy.setDate(Instant.now());
            copy.setPublished(false);
            shPostUnifiedRepository.save(copy);
        }
    }

    /** Distinct logical post ids (the copy is keyed by id, state is re-fetched in copyPost). */
    private List<ShPostUnified> dedupById(List<ShPostUnified> rows) {
        List<ShPostUnified> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ShPostUnified row : rows) {
            if (!seen.contains(row.getId())) {
                seen.add(row.getId());
                out.add(row);
            }
        }
        return out;
    }

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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
