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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentAuditService;
import com.viglet.shio.api.history.ShContentSubject;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.tenant.ShTenantContext;
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
 * <p><b>It is also the one place that knows what deleting a subtree means</b> (SH200):
 * {@link #delete} trashes it, {@link #restore} brings it back and {@link #purge} makes it final.
 * Both console and agent paths call these, for the same reason both call {@link #move} — a second
 * copy of "delete a section" is how the console came to have one that skipped the trash, the
 * audit trail and the byte cleanup.</p>
 *
 * @author Alexandre Oliveira
 */
@Service
public class ShFolderWriteService {

    private static final List<ShPostState> ALL_STATES = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShFolderRepository shFolderRepository;
    private final ShObjectRepository shObjectRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShContentAuditService shContentAuditService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public ShFolderWriteService(ShFolderRepository shFolderRepository, ShObjectRepository shObjectRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShPostUnifiedWriteService shPostUnifiedWriteService,
                                ShContentAuditService shContentAuditService) {
        this.shFolderRepository = shFolderRepository;
        this.shObjectRepository = shObjectRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shContentAuditService = shContentAuditService;
    }

    /**
     * Create a folder under the given parent object (a {@link ShSite} root or a
     * {@link ShFolder}). Returns {@code empty} when the parent does not exist.
     *
     * <p>Audited (SH196), like every other write here. Without this the change feed described a
     * site's contents and never its shape: a resuming session was told a post now lives at
     * {@code folder:Blog/archive/2025} and never that the section had just appeared, so it could
     * not tell "filed into an existing section" from "the site was restructured".</p>
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
            ShFolder saved = shFolderRepository.save(folder);
            recordFolder(ShContentAction.CREATE, saved, owner, "in " + nameOf(parent));
            return saved;
        });
    }

    /**
     * Renames a folder in place — the console's rename, audited (SH196).
     *
     * <p>The audit row is an {@code UPDATE} with the old name in its description, not a
     * {@code FOLDER_RENAME}: the subject is a column, so the action vocabulary does not grow per
     * kind of object. And it is <b>one</b> row, not one per post beneath it — SH137 is explicit
     * that a rename leaves every URL and every field alone, so a hundred post updates for one
     * rename would make a cheap operation look like a rewrite, which is how a consumer learns to
     * stop trusting the feed.</p>
     */
    @Transactional
    public Optional<ShFolder> update(String id, ShFolder payload, String modifier) {
        return shFolderRepository.findById(id).map(folder -> {
            String previousName = folder.getName();
            folder.setName(payload.getName());
            folder.setFurl(ShURLFormatter.format(payload.getName()));
            folder.setModifier(modifier);
            folder.setModifiedDate(Instant.now());
            ShFolder saved = shFolderRepository.save(folder);
            recordFolder(ShContentAction.UPDATE, saved, modifier, renamed(previousName, saved.getName()));
            return saved;
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
            String previousName = folder.getName();
            String previousParent = nameOf(folder.getParentFolder() != null
                    ? folder.getParentFolder()
                    : ShFolderSite.of(folder));
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
            ShFolder saved = shFolderRepository.save(folder);
            recordFolder(ShContentAction.UPDATE, saved, modifier,
                    detail(renamed(previousName, saved.getName()),
                            moved(previousParent, nameOf(newParent))));
            return saved;
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

    /**
     * Refuses a <b>copy</b> whose destination is the folder itself or inside it (SH203).
     *
     * <p>Lives beside {@link #checkMovable} for the reason SH137 gave for putting the move
     * guard here: this is the one place that knows what a folder's subtree is, and a second
     * copy of the walk is how the two operations come to disagree about it. They refuse the
     * same destination for different reasons — see {@link ShFolderCopyException.Cycle} — but
     * they must refuse exactly the same set of destinations, or the move dialog cannot
     * disable one subtree for both modes.</p>
     */
    @Transactional(readOnly = true)
    public void checkCopyable(String folderId, ShObject destination) {
        if (!(destination instanceof ShFolder destFolder)) {
            return;
        }
        shFolderRepository.findById(folderId).ifPresent(folder -> {
            if (isInSubtree(folder, destFolder)) {
                throw new ShFolderCopyException.Cycle(folder.getName(), destFolder.getName());
            }
        });
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
        if (isInSubtree(folder, destination)) {
            throw new ShFolderMoveException.Cycle(folder.getName(), destination.getName());
        }
    }

    /** Whether {@code destination} is {@code folder} itself or a descendant of it. */
    private boolean isInSubtree(ShFolder folder, ShFolder destination) {
        for (ShFolder cursor = destination; cursor != null; cursor = cursor.getParentFolder()) {
            if (folder.getId().equals(cursor.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts a folder and everything beneath it in the trash — <b>reversible</b> (SH200).
     *
     * <h2>What this used to be</h2>
     *
     * <p>The cascade called {@code shPostUnifiedRepository.deleteAll(...)} — the repository,
     * not the post writer. Everything Block L built hangs off that writer, so one line
     * skipped all of it at once: the SH55 trash (the rows were simply gone), the SH51 audit
     * (nothing recorded, so the deletion was invisible to the SH88 change feed and there was
     * nothing for the SH109 queue to revert) and the {@code DELETE} lifecycle event (no SH12
     * webhook, and SH38's cleanup listener never ran, so the file bytes leaked forever).</p>
     *
     * <p>The inversion that made it urgent rather than untidy: the <em>most</em> destructive
     * operation on the surface — the one SH108's confirm token exists to gate — was the
     * <em>least</em> recoverable and the least observable. A curator who deleted forty pages
     * one at a time could restore them; the same curator using the folder's delete button
     * could not.</p>
     *
     * <h2>Why the subtree is trashed rather than purged</h2>
     *
     * <p>Composing instead of forking (P10 inside the engine, and SH139's rule one level
     * down): each post goes through {@link ShPostUnifiedWriteService#delete}, so a folder
     * delete means exactly what forty post deletes mean. That is the recoverable answer, and
     * it is why {@link ShFolder#getDeletedDate()} had to exist: a trashed post keeps its
     * {@code folder_id}, so the folder rows must outlive the gesture. The FK ordering that
     * motivated the old direct {@code deleteAll} is satisfied at {@link #purge} time
     * instead.</p>
     *
     * <p>No lifecycle event fires here, which is SH55's rule, not an omission: a reversible
     * trash has no side effects, and the webhook plus the byte cleanup run on the
     * irreversible purge. One audit entry is written for the folder <em>and</em> one per post
     * (by the post writer), because SH109's revert operates per entry and "restore the
     * section" is the gesture a human wants — reverting the folder's entry restores the whole
     * subtree in one move.</p>
     *
     * @param actor who deleted it, recorded in the audit trail
     * @return {@code false} when no live folder has that id
     */
    @Transactional
    public boolean delete(String id, String actor) {
        return shFolderRepository.findById(id).map(folder -> {
            Instant trashedAt = Instant.now();
            for (ShFolder node : subtree(folder, false)) {
                trashPostsIn(node, actor);
                node.setDeletedDate(trashedAt);
                shFolderRepository.save(node);
            }
            recordFolder(ShContentAction.DELETE, folder, actor, null);
            return true;
        }).orElse(false);
    }

    /**
     * Restores a trashed folder and the content that went into the trash with it (SH200).
     *
     * <h2>What comes back, and what does not</h2>
     *
     * <p>Only what this deletion trashed. A post or sub-folder that was <em>already</em> in
     * the trash when the section was deleted stays there: the deletion stamped one instant
     * down the subtree, so anything marked earlier than the folder's own
     * {@code deletedDate} was somebody else's decision and is not this restore's business.
     * Comparison is on the loaded instants rather than in SQL — an equality test against a
     * timestamp column is a promise about fractional-second precision that three database
     * engines do not make the same way.</p>
     *
     * <p>A folder whose parent is itself trashed is refused: restoring it would attach a
     * live folder to a deleted one, which is invisible in navigation and reads to the
     * curator as a restore that did nothing. Restore the parent — it brings this one with
     * it.</p>
     *
     * @return {@code false} when the id is not a trashed folder, or its parent is trashed
     */
    @Transactional
    public boolean restore(String id, String actor) {
        Optional<ShFolder> found = shFolderRepository.findAnyById(id).filter(ShFolder::isTrashed);
        if (found.isEmpty()) {
            return false;
        }
        ShFolder folder = found.get();
        if (folder.getParentFolder() != null && folder.getParentFolder().isTrashed()) {
            return false;
        }
        Instant trashedAt = folder.getDeletedDate();
        List<ShFolder> restored = new ArrayList<>();
        for (ShFolder node : subtree(folder, true)) {
            if (node.isTrashed() && !node.getDeletedDate().isBefore(trashedAt)) {
                node.setDeletedDate(null);
                shFolderRepository.save(node);
                restored.add(node);
            }
        }
        for (ShPostUnified post : trashedPostsIn(restored)) {
            if (!post.getDeletedDate().isBefore(trashedAt)) {
                shPostUnifiedWriteService.restoreFromTrash(post.getId(), actor);
            }
        }
        recordFolder(ShContentAction.RESTORE, folder, actor, null);
        return true;
    }

    /**
     * Permanently removes a trashed folder subtree (SH200) — irreversible.
     *
     * <p>Posts go through {@link ShPostUnifiedWriteService#purge}, which is what fires the
     * {@code DELETE} lifecycle event: the SH12 webhook is delivered and SH38's listener
     * removes the stored bytes, neither of which the old cascade did at all. Only then do the
     * folder rows go — this is where the {@code folder_id} FK ordering has to be satisfied,
     * and it is satisfied by having reached it after the posts.</p>
     *
     * @return {@code false} when the id is not a trashed folder
     */
    @Transactional
    public boolean purge(String id) {
        Optional<ShFolder> found = shFolderRepository.findAnyById(id).filter(ShFolder::isTrashed);
        if (found.isEmpty()) {
            return false;
        }
        List<ShFolder> nodes = subtree(found.get(), true);
        // Anything still live under a trashed folder is trashed first so it leaves the same
        // way: purge only sees trashed rows, and a post left behind here would still hold the
        // folder_id FK and block the folder rows.
        for (ShFolder node : nodes) {
            for (ShPostUnified post : livePostsIn(node)) {
                shPostUnifiedWriteService.delete(post.getId(), null);
            }
        }
        for (ShPostUnified post : trashedPostsIn(nodes)) {
            shPostUnifiedWriteService.purge(post.getId());
        }
        // A purge is native DML (it has to be — @SQLRestriction hides the rows it deletes), so
        // the post entities it removed are still managed here. Flushing and clearing before the
        // folder goes is what keeps Hibernate from ordering a folder delete ahead of a post it
        // believes still references it — which surfaces as a TransientPropertyValueException
        // naming the folder, several frames away from the native delete that caused it.
        entityManager.flush();
        entityManager.clear();
        // The ShFolder#shFolders cascade drops the now post-free sub-folder rows.
        shFolderRepository.findAnyById(id).ifPresent(shFolderRepository::delete);
        return true;
    }

    /**
     * The trash's top level (SH200): one row per deleted <em>section</em>.
     *
     * <p>A trashed folder whose parent is not also trashed. Listing every folder in a
     * deleted subtree would imply the curator made eleven separate decisions and would bury
     * the one entry they are looking for.</p>
     */
    @Transactional(readOnly = true)
    public List<ShFolder> listTrash() {
        List<ShFolder> roots = new ArrayList<>();
        for (ShFolder folder : shFolderRepository.findTrashedFolders()) {
            ShFolder parent = folder.getParentFolder();
            if (parent == null || !parent.isTrashed()) {
                roots.add(folder);
            }
        }
        return List.copyOf(roots);
    }

    /**
     * The folder and its descendants, deepest last.
     *
     * @param includeTrashed whether to walk into folders already in the trash — {@code true}
     *                       for restore and purge, which operate <em>on</em> the trash, and
     *                       {@code false} for a delete, which must not re-stamp a subtree
     *                       somebody trashed earlier and thereby fold it into this deletion
     */
    private List<ShFolder> subtree(ShFolder root, boolean includeTrashed) {
        List<ShFolder> nodes = new ArrayList<>();
        Deque<ShFolder> pending = new ArrayDeque<>();
        Set<String> seen = new LinkedHashSet<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            ShFolder current = pending.removeFirst();
            if (!seen.add(current.getId())) {
                continue;
            }
            nodes.add(current);
            List<ShFolder> children = includeTrashed
                    ? shFolderRepository.findByParentFolderIncludingTrashed(current)
                    : new ArrayList<>(shFolderRepository.findByParentFolder(current));
            pending.addAll(children);
        }
        return nodes;
    }

    private void trashPostsIn(ShFolder folder, String actor) {
        for (ShPostUnified post : livePostsIn(folder)) {
            shPostUnifiedWriteService.delete(post.getId(), actor);
        }
    }

    /** One row per logical post id — the writer moves both states, so DRAFT+PUBLISHED is one call. */
    private List<ShPostUnified> livePostsIn(ShFolder folder) {
        List<ShPostUnified> posts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ShPostUnified row : shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL_STATES)) {
            if (seen.add(row.getId())) {
                posts.add(row);
            }
        }
        return posts;
    }

    /** Trashed posts across a set of folders, one row per logical id. */
    private List<ShPostUnified> trashedPostsIn(List<ShFolder> folders) {
        if (folders.isEmpty()) {
            return List.of();
        }
        List<String> folderIds = folders.stream().map(ShFolder::getId).toList();
        List<ShPostUnified> posts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ShPostUnified row : shPostUnifiedRepository.findTrashedByFolderIds(folderIds, tenantId())) {
            if (row.getDeletedDate() != null && seen.add(row.getId())) {
                posts.add(row);
            }
        }
        return posts;
    }

    /**
     * One audit row per folder write (SH196) — the single call site, so a new write here cannot
     * be the one that forgets. {@code ShFolderWriteAuditLintTest} asserts every mutating method
     * reaches this.
     */
    private void recordFolder(ShContentAction action, ShFolder folder, String actor, String detail) {
        shContentAuditService.record(action, ShContentSubject.FOLDER, folder.getId(),
                siteIdOf(folder), folder.getName(), actor, detail);
    }

    /** {@code renamed from "2024"}, or {@code null} when the name did not change. */
    private static String renamed(String previousName, String currentName) {
        return previousName == null || previousName.equals(currentName)
                ? null
                : "renamed from \"" + previousName + "\"";
    }

    /** {@code moved from "Blog" to "News"}, or {@code null} when the parent did not change. */
    private static String moved(String previousParent, String currentParent) {
        return previousParent == null || previousParent.equals(currentParent)
                ? null
                : "moved from \"" + previousParent + "\" to \"" + currentParent + "\"";
    }

    /**
     * Joins the parts of a change that happened, dropping the ones that did not.
     *
     * <p>A move that only renames says only that. Reporting "moved from X to X" alongside it
     * would describe a write nobody made, which is the same defect as a hundred post updates for
     * one rename, one order of magnitude smaller.</p>
     */
    private static String detail(String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                present.add(part);
            }
        }
        return present.isEmpty() ? null : String.join("; ", present);
    }

    /** A parent's name for the audit description — a folder's, a site's, or {@code "?"}. */
    private static String nameOf(ShObject parent) {
        if (parent instanceof ShFolder folder) {
            return folder.getName();
        }
        if (parent instanceof ShSite site) {
            return site.getName();
        }
        return "?";
    }

    private static String siteIdOf(ShFolder folder) {
        ShSite site = ShFolderSite.of(folder);
        return site == null ? null : site.getId();
    }

    private static String tenantId() {
        String tenant = ShTenantContext.getTenantId();
        return tenant != null ? tenant : ShTenantContext.DEFAULT_TENANT;
    }
}
