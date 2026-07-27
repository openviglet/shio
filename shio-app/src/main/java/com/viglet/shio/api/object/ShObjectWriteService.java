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
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;

    public ShObjectWriteService(ShObjectRepository shObjectRepository, ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShPostUnifiedWriteService shPostUnifiedWriteService) {
        this.shObjectRepository = shObjectRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
    }

    /**
     * Relocates objects into {@code destId}.
     *
     * <h2>A move changes where something lives, and nothing else (SH139)</h2>
     *
     * <p>This used to re-slug every moved post's {@code furl} from its title, so
     * dragging a published page between folders silently changed its live URL and
     * threw away any hand-set one. SH134 spent its whole design budget on that
     * question for the agent path; this is the path a human triggers by accident,
     * and it was the one getting it wrong.</p>
     *
     * <p>The fix is not a deleted line but a delegation: the post branch now calls
     * {@link ShPostUnifiedWriteService#move} — the code SH134 already got right —
     * so the console and the agent move a post the same way. That also buys the
     * things the fork was missing: both state rows move together (a published
     * post's live URL keeps working), {@code modifiedDate}/{@code modifier} are
     * stamped, and the move appears in the SH51 audit, which is what makes it
     * visible to the SH88 change feed and the SH109 review queue.</p>
     *
     * <p>A URL is assigned only when the object has none — the one case where a
     * move is not discarding a decision somebody made.</p>
     */
    @Transactional
    public List<ShObject> moveTo(String destId, List<String> ids, String actor) {
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
                movePost(id, destFolder, actor);
            }
        }
        return moved;
    }

    /**
     * Moves one post's rows into {@code destFolder}, keeping its friendly URL.
     *
     * <p>Within a site the URL cannot collide — it is the one the post already
     * held, and it was already unique there. Crossing a site boundary can, so it
     * is checked and refused (SH139) rather than silently uniquified, which would
     * be the old behaviour in a narrower case.</p>
     */
    private void movePost(String postId, ShFolder destFolder, String actor) {
        List<ShPostUnified> rows = shPostUnifiedRepository.findById(postId);
        if (rows.isEmpty()) {
            return;
        }
        ShPostUnified any = rows.get(0);
        String furl = any.getFurl();
        boolean needsUrl = furl == null || furl.isBlank();
        if (!needsUrl) {
            requireUrlFree(postId, ShFolderSite.idOf(destFolder), furl);
        }
        shPostUnifiedWriteService.move(postId, destFolder,
                needsUrl ? ShURLFormatter.format(safe(any.getTitle())) : null, actor);
    }

    /** Refuses a move whose destination site already has a different post at {@code furl}. */
    private void requireUrlFree(String postId, String destSiteId, String furl) {
        if (destSiteId == null) {
            return;
        }
        for (ShPostState state : ALL_STATES) {
            shPostUnifiedRepository.findByShSite_IdAndFurlAndState(destSiteId, furl, state)
                    .filter(other -> !postId.equals(other.getId()))
                    .ifPresent(other -> {
                        throw new ShMoveUrlConflictException(furl, other.getTitle());
                    });
        }
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

    /**
     * Re-parents a folder. Shared by move and copy — and it does <em>not</em> set
     * {@code furl}, because on a move that would discard a hand-set one for a slug
     * of the name (SH139). A copy is a new folder and assigns its own; here the URL
     * is only filled in when there is nothing to keep.
     */
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
        if (folder.getFurl() == null || folder.getFurl().isBlank()) {
            folder.setFurl(ShURLFormatter.format(safe(folder.getName())));
        }
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
            copy.setShSite(ShFolderSite.of(destFolder));
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
