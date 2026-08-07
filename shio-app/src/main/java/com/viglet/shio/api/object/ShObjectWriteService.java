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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.folder.ShFolderWriteService;
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
import com.viglet.shio.url.ShFurlSpellings;
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
    private final ShFolderWriteService shFolderWriteService;

    public ShObjectWriteService(ShObjectRepository shObjectRepository, ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShPostUnifiedWriteService shPostUnifiedWriteService,
                                ShFolderWriteService shFolderWriteService) {
        this.shObjectRepository = shObjectRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shFolderWriteService = shFolderWriteService;
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
        ShObject dest = live(destId);
        List<ShObject> moved = new ArrayList<>();
        if (dest == null) {
            return moved;
        }
        for (String id : ids) {
            ShObject object = live(id);
            if (object instanceof ShFolder) {
                // SH137 — the shared writer, not a local re-parent. It carries the two
                // guards this path never had: a folder cannot move into its own subtree
                // (the dialog navigates down from the site root, so a curator can reach
                // that destination in three clicks) and a site root cannot move at all.
                shFolderWriteService.move(id, dest, null, actor).ifPresent(moved::add);
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

    /**
     * Resolves an id, treating a trashed folder as absent (SH200).
     *
     * <p>{@link ShObjectRepository} resolves polymorphically and knows nothing about the
     * folder trash, so without this a caller could move live content <em>into</em> a deleted
     * section — where no listing would ever show it again — or copy a section out of the
     * trash and resurrect it sideways.</p>
     */
    private ShObject live(String id) {
        return shObjectRepository.findById(id)
                .filter(object -> !(object instanceof ShFolder folder) || !folder.isTrashed())
                .orElse(null);
    }

    /**
     * Refuses a move whose destination site already has a different post at {@code furl}.
     *
     * <p>SH583 — every spelling, through the shared generator, and blank stays free for
     * {@link #urlFree}'s reason. A collision check that asked only the exact spelling would let a
     * move land {@code /x} beside SH467's stored {@code /x/}: two posts one character apart, both
     * reachable, which is the {@code duplicate-url} defect created by the guard meant to prevent
     * it.</p>
     */
    private void requireUrlFree(String postId, String destSiteId, String furl) {
        if (destSiteId == null || furl == null || furl.isBlank()) {
            return;
        }
        for (String candidate : ShFurlSpellings.candidates(furl)) {
            for (ShPostState state : ALL_STATES) {
                shPostUnifiedRepository.findByShSite_IdAndFurlAndState(destSiteId, candidate, state)
                        .filter(other -> !postId.equals(other.getId()))
                        .ifPresent(other -> {
                            throw new ShMoveUrlConflictException(furl, other.getTitle());
                        });
            }
        }
    }

    /**
     * Duplicates objects into {@code destId}.
     *
     * <h2>A copy needs a name of its own (SH202)</h2>
     *
     * <p>"Clone" in the console is copy-to-the-same-folder, and this used to derive the
     * copy's {@code furl} from the source's title — the same title, so the same slug. Two
     * posts in one site then claimed one URL, which every downstream mechanism already
     * treated as broken and correctly: {@code shio verify} reports {@code duplicate-url} as
     * an <em>error</em>, and address resolution returns whichever row it reaches first, so
     * the clone was unaddressable and so, from that moment, was the original. SH134 spent
     * its whole design budget refusing an occupied address on a <em>move</em>; create simply
     * never asked.</p>
     *
     * <p><b>The title moves too</b>, not just the URL it derives: a {@code -2} URL under an
     * identical title still leaves two rows the curator cannot tell apart, and that is the
     * symptom they actually see. So a clone is "Home (2)" / {@code home-2} and not "Home" /
     * {@code home-2}.</p>
     *
     * <p>But the two answer to different scopes and are therefore corrected
     * <b>independently</b>: a <b>title</b> is unique per folder (a display concern — two
     * pages called "Overview" in different sections are not a defect), a <b>friendly URL</b>
     * is unique per site (it is the address). A copy into an empty folder of another site
     * keeps both verbatim; a copy into another folder of the <em>same</em> site keeps its
     * title and moves only its URL. See {@link #freeNaming} for why sharing one counter
     * between them was the wrong simplification.</p>
     */
    @Transactional
    public List<ShObject> copyTo(String destId, List<String> ids) {
        ShObject dest = live(destId);
        List<ShObject> copies = new ArrayList<>();
        if (dest == null) {
            return copies;
        }
        for (String id : ids) {
            ShObject object = live(id);
            if (object instanceof ShFolder folder) {
                // SH203 — the copy's own cycle guard. Unlike a move's, this one is not about
                // detaching a subtree: copyFolder saves the copy and then walks the source's
                // children, so a destination inside the source makes the walk descend into
                // the copies it is still making, and it never returns.
                shFolderWriteService.checkCopyable(id, dest);
                copies.add(copyFolder(folder, dest));
            } else if (dest instanceof ShFolder destFolder) {
                copyPost(id, destFolder);
            }
        }
        return copies;
    }

    /**
     * Parents a <b>freshly built copy</b> under its destination.
     *
     * <p>Copy-only since SH137 — a move goes through {@code ShFolderWriteService#move},
     * the writer the agent path shares, so the cycle and root-folder guards apply to both
     * surfaces. A copy needs no cycle guard: the new folder is not yet in the tree, so it
     * cannot be its own ancestor.</p>
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
        // SH202 one level up: a folder is addressed by its name chain (SH79), so two
        // siblings of the same name make `folder:<site>/a/b` resolve to whichever the walk
        // reaches first — the post case's defect with the addressing scheme swapped.
        String name = freeFolderName(source.getName(), dest);
        ShFolder copy = new ShFolder();
        copy.setName(name);
        copy.setFurl(ShURLFormatter.format(name));
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
        List<ShPostUnified> sources = shPostUnifiedRepository.findById(postId);
        if (sources.isEmpty()) {
            return;
        }
        String newId = UUID.randomUUID().toString();
        // One name for the logical post, not one per state row: DRAFT and PUBLISHED are two
        // rows of the same post, and letting them be numbered independently would produce a
        // copy whose published URL is not the one its draft claims.
        Naming naming = freeNaming(sources.get(0).getTitle(), destFolder);
        for (ShPostUnified source : sources) {
            ShPostUnified copy = new ShPostUnified();
            copy.setId(newId);
            copy.setState(source.getState());
            copy.setShPostType(source.getShPostType());
            copy.setShFolder(destFolder);
            copy.setShSite(ShFolderSite.of(destFolder));
            copy.setTitle(naming.title());
            copy.setSummary(source.getSummary());
            copy.setFurl(naming.furl());
            copy.setOwner(source.getOwner());
            copy.setData(source.getData());
            copy.setDate(Instant.now());
            copy.setPublished(false);
            shPostUnifiedRepository.save(copy);
        }
    }

    /** What a copy will be called, and where it will answer. */
    private record Naming(String title, String furl) {
    }

    /**
     * A title free in the destination folder and a URL free in the destination site (SH202).
     *
     * <p><b>Two counters, not one.</b> The first version of this shared a counter so the
     * title and the URL could never disagree, and its own test caught what that costs: a
     * page copied into a folder holding no page of that name came out "Contact (2)" because
     * the <em>URL</em> was taken somewhere else in the site. The rename was correct in a
     * scope the curator cannot see from the place it shows up, which is its own kind of
     * confusing. Each name is now corrected only where it is actually ambiguous — and in the
     * case that motivated the task, the clone, both are, so both still move together.</p>
     *
     * <p>That the two can end up disagreeing is not a compromise: they are separate
     * namespaces (SH79) and the model already treats them that way — SH139's whole point is
     * that renaming or moving a post does <em>not</em> re-derive its URL.</p>
     *
     * <p>Both loops terminate because every {@code n} yields a distinct candidate while the
     * sets they test against are finite. The bound is the number of copies of that page that
     * already exist, which is the honest one rather than an invented ceiling.</p>
     */
    private Naming freeNaming(String rawTitle, ShFolder destFolder) {
        String title = safe(rawTitle);
        Set<String> takenTitles = postTitlesIn(destFolder);
        String freeTitle = null;
        for (int n = 1; freeTitle == null; n++) {
            String candidate = numbered(title, n);
            if (!takenTitles.contains(candidate)) {
                freeTitle = candidate;
            }
        }

        // The slug is derived from the source's title, not from the numbered one: the two
        // counters are independent, and deriving it from `freeTitle` would silently couple
        // them again through the formatter.
        String destSiteId = ShFolderSite.idOf(destFolder);
        String base = ShURLFormatter.format(title);
        for (int n = 1; ; n++) {
            String furl = n == 1 ? base : base + "-" + n;
            if (urlFree(destSiteId, furl)) {
                return new Naming(freeTitle, furl);
            }
        }
    }

    /** The folder half: a name free among the destination's own children. */
    private String freeFolderName(String rawName, ShObject dest) {
        String name = safe(rawName);
        Set<String> taken = folderNamesIn(dest);
        for (int n = 1; ; n++) {
            String candidate = numbered(name, n);
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * {@code "Home"}, {@code "Home (2)"}, {@code "Home (3)"} — a numeric suffix rather than
     * a word, because the server writes this string into content the console renders in
     * whichever language the curator picked, and {@code "Home (copy)"} would be English in
     * a Portuguese listing forever.
     */
    private static String numbered(String base, int n) {
        return n == 1 ? base : base + " (" + n + ")";
    }

    /**
     * Whether no live post in {@code destSiteId} already answers at {@code furl}.
     *
     * <p>A blank URL is treated as free: an uploaded file post never gets one (SH38, which
     * is why SH172 needed the {@code id:} address fallback), so blank is a legitimate shared
     * value rather than a collision.</p>
     */
    private boolean urlFree(String destSiteId, String furl) {
        if (destSiteId == null || furl == null || furl.isBlank()) {
            return true;
        }
        // SH583 — every spelling: a copy that only checked the exact one would derive an
        // "unused" URL that a tolerant reader already resolves to something else.
        for (String candidate : ShFurlSpellings.candidates(furl)) {
            for (ShPostState state : ALL_STATES) {
                if (shPostUnifiedRepository
                        .findByShSite_IdAndFurlAndState(destSiteId, candidate, state).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }

    private Set<String> postTitlesIn(ShFolder destFolder) {
        Set<String> titles = new HashSet<>();
        for (ShPostUnified row : shPostUnifiedRepository.findByShFolderAndStateIn(destFolder, ALL_STATES)) {
            if (row.getTitle() != null) {
                titles.add(row.getTitle());
            }
        }
        return titles;
    }

    private Set<String> folderNamesIn(ShObject dest) {
        Set<ShFolder> siblings;
        if (dest instanceof ShFolder destFolder) {
            siblings = shFolderRepository.findByParentFolder(destFolder);
        } else if (dest instanceof ShSite destSite) {
            siblings = shFolderRepository.findByShSiteAndRootFolder(destSite, (byte) 1);
        } else {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        for (ShFolder sibling : siblings) {
            if (sibling.getName() != null) {
                names.add(sibling.getName());
            }
        }
        return names;
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
