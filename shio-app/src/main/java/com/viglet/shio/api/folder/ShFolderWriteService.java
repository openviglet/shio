/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.url.ShURLFormatter;

/**
 * Write-side logic for console folder management. Rebuilt for the unified post
 * model (SH-nuclear-refactor removed the legacy {@code ShFolderAPI}); folders
 * remain plain {@link ShObject} subclasses, unrelated to {@code sh_post_unified}.
 *
 * <p>A folder created under a {@link ShSite} becomes a {@code rootFolder};
 * created under another {@link ShFolder} it becomes a child. Group/user ACLs are
 * inherited from the parent object so navigation security stays consistent.</p>
 *
 * @author Alexandre Oliveira
 */
@Service
public class ShFolderWriteService {

    private final ShFolderRepository shFolderRepository;
    private final ShObjectRepository shObjectRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShFolderWriteService(ShFolderRepository shFolderRepository, ShObjectRepository shObjectRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shFolderRepository = shFolderRepository;
        this.shObjectRepository = shObjectRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * Create a folder under the given parent object (a {@link ShSite} root or a
     * {@link ShFolder}). Returns {@code empty} when the parent does not exist.
     */
    @Transactional
    public Optional<ShFolder> create(String parentObjectId, ShFolder payload, String owner) {
        return shObjectRepository.findById(parentObjectId).map(parent -> {
            ShFolder folder = new ShFolder();
            folder.setName(payload.getName());
            folder.setFurl(ShURLFormatter.format(payload.getName()));
            folder.setOwner(owner);
            folder.setDate(Instant.now());
            folder.setShGroups(new HashSet<>(parent.getShGroups()));
            folder.setShUsers(new HashSet<>(parent.getShUsers()));

            if (parent instanceof ShFolder parentFolder) {
                folder.setParentFolder(parentFolder);
                folder.setRootFolder((byte) 0);
                folder.setShSite(null);
            } else if (parent instanceof ShSite site) {
                folder.setParentFolder(null);
                folder.setRootFolder((byte) 1);
                folder.setShSite(site);
            }
            return shFolderRepository.save(folder);
        });
    }

    @Transactional
    public Optional<ShFolder> update(String id, ShFolder payload, String modifier) {
        return shFolderRepository.findById(id).map(folder -> {
            folder.setName(payload.getName());
            folder.setFurl(ShURLFormatter.format(payload.getName()));
            folder.setModifier(modifier);
            folder.setModifiedDate(new Date());
            return shFolderRepository.save(folder);
        });
    }

    @Transactional
    public boolean delete(String id) {
        return shFolderRepository.findById(id).map(folder -> {
            // sh_post_unified.folder_id → ShObject is FK onDelete NO ACTION, so every
            // unified post under this folder (and its descendants) must be removed
            // before the folder rows go. The ShFolder#shFolders cascade then drops the
            // now post-free sub-folder ShObject rows.
            deletePostsRecursively(folder);
            shFolderRepository.delete(folder);
            return true;
        }).orElse(false);
    }

    private void deletePostsRecursively(ShFolder folder) {
        for (ShFolder child : shFolderRepository.findByParentFolder(folder)) {
            deletePostsRecursively(child);
        }
        List<ShPostState> states = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);
        shPostUnifiedRepository.deleteAll(shPostUnifiedRepository.findByShFolderAndStateIn(folder, states));
    }
}
