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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.object.ShFolderSite;
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
            folder.setModifiedDate(Instant.now());
            return shFolderRepository.save(folder);
        });
    }

    /**
     * Relocates and/or renames a folder in place — <b>the one folder-move writer</b> (SH137).
     *
     * <h2>Why this exists at all</h2>
     *
     * <p>Until SH137 the agent's only way to rename a section was {@code folder.upsert} at
     * the new path plus {@code folder.delete} of the old — and that delete cascades over
     * every post beneath it. It is SH134's defect one level up and strictly more
     * destructive: a post lost its history, a folder loses its whole subtree.</p>
     *
     * <h2>Why one writer and not two</h2>
     *
     * <p>SH139's rule, and this is the method that pays for it: the console had its own
     * copy of "move a post" and it was the copy that silently rewrote live URLs. Both
     * surfaces call this, so the guards below cannot be present on one path and missing on
     * the other — and both of them were missing from the console path before now.</p>
     *
     * <h2>What a folder move does not change</h2>
     *
     * <p><b>No post's URL changes.</b> A friendly URL is unique per site and independent of
     * the folder chain (SH79 keeps the two namespaces apart), so moving a section leaves
     * every page beneath it at the address it already had. That is worth stating because it
     * is the opposite of what a path-shaped CMS does, and it is why this op needs no confirm
     * token: nothing is destroyed and nothing goes dark.</p>
     *
     * <p>The folder's own {@code furl} <em>does</em> follow a rename, unlike a post's
     * (SH139). The reason they differ: a post's {@code furl} is its address and its live
     * URL, so re-deriving it discards a decision and breaks links; a folder's is neither —
     * folders are addressed by name chain and the value is read only by the exchange
     * export. Deriving it from the name keeps it consistent with {@code create},
     * {@code update} and {@code folder.upsert}, all of which already do.</p>
     *
     * @param newParent   the destination: a {@link ShFolder}, or a {@link ShSite} to make
     *                    this a root folder (the console's "move to site" case)
     * @param newName     the folder's new name, or {@code null} to keep it
     * @return the saved folder, or {@code empty} when {@code folderId} does not exist
     */
    @Transactional
    public Optional<ShFolder> move(String folderId, ShObject newParent, String newName, String modifier) {
        return shFolderRepository.findById(folderId).map(folder -> {
            requireMovable(folder, newParent);
            if (newParent instanceof ShFolder destFolder) {
                folder.setParentFolder(destFolder);
                folder.setRootFolder((byte) 0);
                // Only a root folder carries the site (SH128); a nested one must not.
                folder.setShSite(null);
            } else if (newParent instanceof ShSite destSite) {
                folder.setParentFolder(null);
                folder.setRootFolder((byte) 1);
                folder.setShSite(destSite);
            }
            if (newName != null && !newName.isBlank() && !newName.equals(folder.getName())) {
                folder.setName(newName);
                folder.setFurl(ShURLFormatter.format(newName));
            }
            folder.setModifier(modifier);
            folder.setModifiedDate(Instant.now());
            return shFolderRepository.save(folder);
        });
    }

    /**
     * Asks the move's refusals without performing it — what a {@code dryRun} needs (SH137).
     *
     * <p>Exposed so the plan cannot disagree with the run. SH82's rule is that a plan and an
     * apply differ only in a flag; a guard that lived only on the write side would make a
     * plan report a move that the real call then refuses, which is the "the plan lied"
     * failure mode that makes a plan worthless.</p>
     */
    @Transactional(readOnly = true)
    public void checkMovable(String folderId, ShObject newParent) {
        shFolderRepository.findById(folderId).ifPresent(folder -> requireMovable(folder, newParent));
    }

    private void requireMovable(ShFolder folder, ShObject newParent) {
        if (folder.getRootFolder() == 1) {
            // The walk, not the field — even though a root folder is the one folder where the
            // two agree, so this needs no SH128 exemption. Naming the site is only for the
            // error message, and an error that says "null" while refusing a move is a worse
            // error than one that took two extra frames to build.
            ShSite owner = ShFolderSite.of(folder);
            throw new ShFolderMoveException.Root(owner == null ? "?" : owner.getName());
        }
        if (newParent instanceof ShFolder destination) {
            requireOutsideSubtree(folder, destination);
        }
    }

    /**
     * Refuses a destination that is {@code folder} itself or a descendant of it.
     *
     * <p>Walks up from the destination rather than down from the folder: the ancestor chain
     * is bounded by the tree's depth, while the subtree is bounded by its size.</p>
     */
    private void requireOutsideSubtree(ShFolder folder, ShFolder destination) {
        for (ShFolder cursor = destination; cursor != null; cursor = cursor.getParentFolder()) {
            if (folder.getId().equals(cursor.getId())) {
                throw new ShFolderMoveException.Cycle(folder.getName(), destination.getName());
            }
        }
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
