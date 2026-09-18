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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.folder.ShFolderWriteService;
import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.object.ShLiveObject;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.api.reference.ShObjectReferenceService;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.object.ShObjectRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.url.ShFurlNaming;
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
    /** SH1034 — the reverse index, maintained beside this class's own writes. */
    private final ShObjectReferenceService shObjectReferenceService;


    private static final List<ShPostState> ALL_STATES = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShObjectRepository shObjectRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShFolderWriteService shFolderWriteService;
    private final ShFurlNaming shFurlNaming;
    /** SH749 — a copy's bytes, which are keyed by the post id and so do not come along on their own. */
    private final ShFileStorageService shFileStorageService;

    public ShObjectWriteService(ShObjectRepository shObjectRepository, ShFolderRepository shFolderRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShPostUnifiedWriteService shPostUnifiedWriteService,
                                ShFolderWriteService shFolderWriteService,
                                ShFurlNaming shFurlNaming,
                                ShFileStorageService shFileStorageService,
            ShObjectReferenceService shObjectReferenceService) {
        this.shObjectReferenceService = shObjectReferenceService;
        this.shObjectRepository = shObjectRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shFolderWriteService = shFolderWriteService;
        this.shFurlNaming = shFurlNaming;
        this.shFileStorageService = shFileStorageService;
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
        return ShLiveObject.of(shObjectRepository, id).orElse(null);
    }

    /**
     * Refuses a move whose destination site already has a different post at {@code furl}.
     *
     * <p>SH583 — every spelling, and blank stays free. A collision check that asked only the exact
     * spelling would let a move land {@code /x} beside SH467's stored {@code /x/}: two posts one
     * character apart, both reachable, which is the {@code duplicate-url} defect created by the
     * guard meant to prevent it.</p>
     *
     * <p>The walk itself is {@code ShFurlNaming.liveOccupant}'s since SH243, which needed the same
     * question for a restore. Live occupants only, deliberately: a URL held by a <em>trashed</em>
     * post does not refuse a move, because a refusal names an occupant and no listing shows that
     * one. The case it leaves — moving onto a URL a sleeping post will want back — is refused
     * where it becomes real, at the restore.</p>
     */
    private void requireUrlFree(String postId, String destSiteId, String furl) {
        shFurlNaming.liveOccupant(destSiteId, furl, postId).ifPresent(other -> {
            throw new ShMoveUrlConflictException(furl, other.getTitle());
        });
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
                copies.add(copyFolder(folder, dest, freeFolderName(folder.getName(), dest), false));
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

    /**
     * Duplicates one folder under {@code dest} at a name the <b>caller</b> chose (SH244).
     *
     * <h2>Why the agent path needs a named copy and the console does not</h2>
     *
     * <p>{@link #copyTo} numbers its copies through {@link #freeFolderName} — right for a
     * curator, who clicks "clone" and then reads what appeared. An agent cannot read: it has to
     * know the address of what it just made in order to write the next op against it, and a
     * server-chosen {@code "archive (2)"} is a second round trip to find out (P2) and a name the
     * caller never asked for (P5). So the op supplies the whole name chain and an occupied
     * target is refused rather than numbered.</p>
     *
     * <p>Only the <b>top</b> folder is named. Everything beneath it keeps its own name, because
     * the copy's children are being created under a parent that is itself brand new — nothing
     * there can collide, and renaming a subtree the caller did not address would be the
     * surprise P7 forbids.</p>
     *
     * <p>This is the one copy implementation and both surfaces reach it, which is SH245's rule:
     * a second copy of a content verb is where the careless version grows. It is also why the
     * cycle guard is asked here and not by each caller.</p>
     *
     * <h2>{@code asDrafts}, and why the agent path is the caller that asks for it</h2>
     *
     * <p>A copy otherwise reproduces each of a post's state rows as it found them, which is
     * {@link #copyTo}'s documented behaviour and correct there: a curator who clones a live page
     * gets a live clone, at a URL {@link #freeNaming} has already made unique, and can see what
     * happened. Handed to an agent, the same rule is a <b>hole in SH107's publish gate</b> — a
     * credential without {@code mayPublish} could put a whole section live by copying it, which is
     * the one thing "the agent path is never a privilege escalation" (SH201) names outright. So
     * the op passes {@code true} and gets one DRAFT row per post; the console keeps what it had.
     * A post that exists <em>only</em> as {@code PUBLISHED} — SH38's uploaded asset, or an exchange
     * import — is copied as a draft rather than skipped, since dropping it would make a folder copy
     * silently lose content.</p>
     *
     * @return the new folder, or {@code empty} when {@code sourceFolderId} is not a live folder
     */
    @Transactional
    public Optional<ShFolder> copyFolderAs(String sourceFolderId, ShObject dest, String name,
                                           boolean asDrafts) {
        if (dest == null || !(live(sourceFolderId) instanceof ShFolder source)) {
            return Optional.empty();
        }
        shFolderWriteService.checkCopyable(sourceFolderId, dest);
        String chosen = safe(name).isBlank() ? freeFolderName(source.getName(), dest) : name;
        return Optional.of(copyFolder(source, dest, chosen, asDrafts));
    }

    /**
     * How much a folder copy would duplicate, without duplicating it — what a {@code dryRun}
     * reports (SH244).
     *
     * <p>It lives beside the copy rather than in the caller for {@link #checkCopyable}'s reason:
     * a second walk of the subtree is how a plan comes to describe a run that does something
     * else. The numbers are the <em>logical</em> objects — a post whose DRAFT and PUBLISHED rows
     * both copy is one post — because that is what the caller is going to see afterwards.</p>
     */
    @Transactional(readOnly = true)
    public CopyScope scopeOf(String folderId) {
        if (!(live(folderId) instanceof ShFolder folder)) {
            return new CopyScope(0, 0);
        }
        return scopeOf(folder, new CopyScope(1, 0));
    }

    private CopyScope scopeOf(ShFolder folder, CopyScope soFar) {
        CopyScope scope = new CopyScope(soFar.folders(), soFar.posts()
                + dedupById(shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL_STATES)).size());
        for (ShFolder child : shFolderRepository.findByParentFolder(folder)) {
            scope = scopeOf(child, new CopyScope(scope.folders() + 1, scope.posts()));
        }
        return scope;
    }

    /** What a copy is about to duplicate: folders including the one addressed, and posts. */
    public record CopyScope(int folders, int posts) {
    }

    private ShFolder copyFolder(ShFolder source, ShObject dest, String name, boolean asDrafts) {
        // SH202 one level up: a folder is addressed by its name chain (SH79), so two
        // siblings of the same name make `folder:<site>/a/b` resolve to whichever the walk
        // reaches first — the post case's defect with the addressing scheme swapped.
        ShFolder copy = new ShFolder();
        copy.setName(name);
        copy.setFurl(ShURLFormatter.format(name));
        copy.setOwner(source.getOwner());
        copy.setDate(Instant.now());
        reparent(copy, dest);
        ShFolder saved = shFolderRepository.save(copy);

        for (ShPostUnified row : dedupById(shPostUnifiedRepository.findByShFolderAndStateIn(source, ALL_STATES))) {
            copyPost(row.getId(), saved, asDrafts);
        }
        for (ShFolder child : shFolderRepository.findByParentFolder(source)) {
            // The child's own name, unnumbered: `saved` was created a line ago and holds
            // nothing, so `freeFolderName` could only ever return the name it was given.
            copyFolder(child, saved, safe(child.getName()), asDrafts);
        }
        return saved;
    }

    private void copyPost(String postId, ShFolder destFolder) {
        copyPost(postId, destFolder, false);
    }

    private void copyPost(String postId, ShFolder destFolder, boolean asDrafts) {
        List<ShPostUnified> sources = shPostUnifiedRepository.findById(postId);
        if (sources.isEmpty()) {
            return;
        }
        String newId = UUID.randomUUID().toString();
        // One name for the logical post, not one per state row: DRAFT and PUBLISHED are two
        // rows of the same post, and letting them be numbered independently would produce a
        // copy whose published URL is not the one its draft claims.
        Naming naming = freeNaming(sources.get(0).getTitle(), destFolder);
        // SH244 — one row, DRAFT, when the caller asked for drafts. The DRAFT is preferred and
        // the PUBLISHED row stands in only when there is no draft behind it (an uploaded asset,
        // an exchange import): skipping it instead would make a folder copy lose content, and
        // reproducing it as PUBLISHED is the publish-gate hole the flag exists to close.
        List<ShPostUnified> rows = asDrafts ? List.of(preferredRow(sources)) : sources;
        for (ShPostUnified source : rows) {
            ShPostUnified copy = new ShPostUnified();
            copy.setId(newId);
            copy.setState(asDrafts ? ShPostState.DRAFT : source.getState());
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
            shObjectReferenceService.reindex(shPostUnifiedRepository.save(copy));
        }
        // SH749 — the row is keyed by a fresh UUID and so is the storage, so a copy without this
        // line is a `File` post pointing at a directory nobody wrote: every surface reports
        // success and the bytes 404 at delivery. A no-op for a post that stores nothing, which is
        // why it is asked of the storage rather than of the post-type.
        shFileStorageService.copy(postId, newId);
    }

    /** What a copy will be called, and where it will answer. */
    private record Naming(String title, String furl) {
    }

    /** The DRAFT row of a post if it has one — the editable one, which is what a copy is. */
    private static ShPostUnified preferredRow(List<ShPostUnified> rows) {
        return rows.stream()
                .filter(row -> row.getState() == ShPostState.DRAFT)
                .findFirst()
                .orElse(rows.get(0));
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
        // them again through the formatter. The URL half is `ShFurlNaming`'s since SH651 —
        // post create needed the same loop, and a second copy of it was the wrong answer.
        return new Naming(freeTitle, shFurlNaming.freeFurl(ShFolderSite.idOf(destFolder), title));
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

    /*
     * `urlFree` lived here until SH651 and is now `ShFurlNaming.isFree` — post create needed
     * the same question answered and this was the only place that could answer it.
     */

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
