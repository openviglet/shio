/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * Desired-state plan/apply for content (SH82).
 *
 * <h2>Why this and not just the batch</h2>
 *
 * <p>A batch (SH81) is imperative — it says what to do, so the caller has to know
 * what has already been done. That knowledge is precisely what an agent loses when
 * its context is compacted, and re-deriving it costs a full read of the site. A
 * desired-state document is a fact instead of a script: applying it twice is applying
 * it once, and the second plan is empty. That empty plan is also the drift guard —
 * the same signal {@code shio push --check} gives for post-types (SH65), now over
 * content.</p>
 *
 * <h2>One engine, two entry points</h2>
 *
 * <p>Law P10, taken literally: this class <em>compiles</em> a document into the batch
 * ops of SH81 and hands them to {@link ShAgentBatchService}. It owns no write logic of
 * its own, so plan and apply cannot drift apart from batch — and cannot drift apart
 * from each other, since {@code plan} is {@code apply} with the dry-run flag set.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentDesiredStateService {

    private final ShAgentBatchService shAgentBatchService;

    public ShAgentDesiredStateService(ShAgentBatchService shAgentBatchService) {
        this.shAgentBatchService = shAgentBatchService;
    }

    /**
     * What would change. Writes nothing, ever — the endpoint's only guarantee.
     *
     * <p>Runs as {@code DOCUMENT} (SH138): these ops were compiled here, not typed by the
     * caller, so the run is bounded by the document ceiling and a run over it is refused
     * with advice about scope rather than about splitting batches.</p>
     */
    @Transactional(readOnly = true)
    public ShAgentChangeSet plan(ShAgentDesiredState desired, boolean ids, String actor) {
        return plan(desired, ids, actor, ShAgentBatchService.Declared.NOTHING);
    }

    /**
     * As {@link #plan(ShAgentDesiredState, boolean, String)}, resolving against the site and
     * post-types the caller's own run would create first (SH153).
     *
     * <p>{@code readOnly} is the point of the parameter, not an implementation detail: a
     * blueprint plan used to provision its model inside this transaction and roll it back,
     * which made a plan a write — unavailable on a read-only credential or a replica. Nothing
     * on this path writes now, so the annotation is a claim the database enforces.</p>
     */
    @Transactional(readOnly = true)
    public ShAgentChangeSet plan(ShAgentDesiredState desired, boolean ids, String actor,
                                 ShAgentBatchService.Declared declared) {
        return shAgentBatchService.run(new ShAgentBatch(true, compile(desired)), ids, actor,
                ShAgentBatchService.Source.DOCUMENT, declared);
    }

    /** Makes it so, atomically. */
    @Transactional
    public ShAgentChangeSet apply(ShAgentDesiredState desired, boolean ids, String actor) {
        // The document's `confirm` is the batch's `confirm` (SH108): a prune is a batch of
        // deletes by the time it reaches the writer, so there is one check and one token, not
        // a second mechanism for the declarative path.
        return shAgentBatchService.run(
                new ShAgentBatch(false, compile(desired), desired == null ? null : desired.confirm()),
                ids, actor, ShAgentBatchService.Source.DOCUMENT);
    }

    /**
     * Compiles a document into ops.
     *
     * <p>Order is load-bearing and therefore fixed: folders, then moves, then posts, then
     * publishes, then prunes. Folders first because a post needs somewhere to go — and a
     * move's destination folder is one of those places, which is why moves come after them
     * and not first. Moves before upserts so the upsert that follows writes fields at the
     * post's new address instead of creating a second post there (SH150). Publishes after
     * all upserts so a page never goes live referencing a sibling that does not exist yet;
     * prunes last so a deletion can never race the creation of its replacement.</p>
     */
    List<ShAgentBatch.Op> compile(ShAgentDesiredState desired) {
        if (desired == null || desired.site() == null || desired.site().isBlank()) {
            throw new MissingSiteException();
        }
        String site = desired.site().trim();
        List<ShAgentBatch.Op> folders = new ArrayList<>();
        List<ShAgentBatch.Op> moves = new ArrayList<>();
        List<ShAgentBatch.Op> upserts = new ArrayList<>();
        List<ShAgentBatch.Op> publishes = new ArrayList<>();

        for (ShAgentDesiredState.Folder folder : nullSafe(desired.folders())) {
            folders.add(op(ShAgentBatchService.FOLDER_UPSERT,
                    ShAddress.folder(site, folder.path()).toString(), null, null, null));
        }
        for (ShAgentDesiredState.Move move : nullSafe(desired.moves())) {
            moves.add(moveOp(site, move));
        }
        for (ShAgentDesiredState.Post post : nullSafe(desired.posts())) {
            if (post.url() == null || post.url().isBlank()) {
                throw new MissingPostUrlException();
            }
            String address = ShAddress.post(site, post.url()).toString();
            upserts.add(new ShAgentBatch.Op(ShAgentBatchService.POST_UPSERT, address,
                    post.type(), post.folder(), null, post.data(), null, post.locale()));
            // Absent `publish` means "leave it alone", so only an explicit true or false
            // produces an op. Treating absence as false would unpublish live pages the
            // moment someone trimmed a field from the document (P7).
            if (Boolean.TRUE.equals(post.publish())) {
                publishes.add(op(ShAgentBatchService.POST_PUBLISH, address, null, null, null));
            } else if (Boolean.FALSE.equals(post.publish())) {
                publishes.add(op(ShAgentBatchService.POST_UNPUBLISH, address, null, null, null));
            }
        }

        List<ShAgentBatch.Op> ops = new ArrayList<>(folders);
        ops.addAll(moves);
        ops.addAll(upserts);
        ops.addAll(publishes);
        ops.addAll(pruneOps(desired, site));
        return List.copyOf(ops);
    }

    /**
     * One declared move as a {@code post.move} op (SH150).
     *
     * <p>A move naming neither a new URL nor a new folder is refused rather than compiled to
     * a noop: it is always a mistake — half a rename, or a key spelled wrong — and a silent
     * noop is how a caller comes to believe a rename happened when the post never moved.
     * Everything else the op needs is already enforced on {@code ShFolderWriteService} /
     * {@code ShPostUnifiedWriteService}, which is the point of compiling to an existing op
     * rather than restating its rules here (P10): an occupied target, a cross-site move and a
     * missing source all fail the same way they do for a hand-written batch.</p>
     */
    private static ShAgentBatch.Op moveOp(String site, ShAgentDesiredState.Move move) {
        if (move == null || move.from() == null || move.from().isBlank()) {
            throw new MissingMoveFromException();
        }
        boolean renames = move.to() != null && !move.to().isBlank();
        boolean relocates = move.folder() != null && !move.folder().isBlank();
        if (!renames && !relocates) {
            throw new EmptyMoveException(move.from());
        }
        return new ShAgentBatch.Op(ShAgentBatchService.POST_MOVE,
                ShAddress.post(site, move.from()).toString(),
                null,
                relocates ? move.folder() : null,
                renames ? ShAddress.post(site, move.to()).toString() : null,
                null);
    }

    /**
     * Deletions implied by {@code prune}: everything inside the declared folders' subtrees
     * that this document does not declare — posts (SH82) and, since SH135, folders.
     *
     * <h2>The scope rule, which has not changed</h2>
     *
     * <p><b>Bounded by the declared folders.</b> A prune that swept the whole site would
     * let a document about one section delete another — the blast radius would depend on
     * what the author forgot to mention, which is the definition of a surprise (P7).
     * Declaring no folders and asking to prune is therefore an error, not a licence to
     * delete everything. Every deletion appears in the plan before {@code apply} runs.</p>
     *
     * <h2>Why folders too (SH135)</h2>
     *
     * <p>Without it a document stops describing its own scope the moment a section moves:
     * the posts go, the empty folder stays, and the next plan reports {@code changed:0}
     * over a site that no longer matches the document. Convergence is the only property
     * that justifies declarative apply at all, so a prune that cannot converge is a prune
     * with a hole in it.</p>
     *
     * <h2>The ordering question, answered</h2>
     *
     * <p><em>Is a folder prunable while it still holds undeclared content?</em> It never
     * gets the chance to be: posts are pruned first and folders are pruned
     * <b>deepest-first</b>, so by the time a {@code folder.delete} runs its subtree is
     * already empty and the cascade in {@code ShFolderWriteService} has nothing left to
     * take. That matters because the cascade is silent — relying on it would make the
     * blast radius of one op depend on the order of the ops around it.</p>
     *
     * <p>Three kinds of folder are protected from the sweep: the declared folders
     * themselves, every ancestor of one (deleting {@code /blog} to reach
     * {@code /blog/2026} would be absurd), and any folder a declared <em>post</em> names
     * as its home — a document may place a post in a folder it never listed, and that
     * placement is a declaration too.</p>
     */
    private List<ShAgentBatch.Op> pruneOps(ShAgentDesiredState desired, String site) {
        if (!desired.prune()) {
            return List.of();
        }
        List<ShAgentDesiredState.Folder> folders = nullSafe(desired.folders());
        if (folders.isEmpty()) {
            throw new UnscopedPruneException();
        }
        Set<String> declaredPosts = new LinkedHashSet<>();
        Set<String> protectedFolders = new LinkedHashSet<>();
        for (ShAgentDesiredState.Folder folder : folders) {
            protect(protectedFolders, folder.path());
        }
        for (ShAgentDesiredState.Post post : nullSafe(desired.posts())) {
            declaredPosts.add(ShAddress.normalizePath(post.url()).toLowerCase(Locale.ROOT));
            if (post.folder() != null && !post.folder().isBlank()) {
                protect(protectedFolders, post.folder().startsWith("folder:")
                        ? ShAddress.parse(post.folder()).path() : post.folder());
            }
        }
        // A move's *source* is declared too (SH150), and this is the line that makes the whole
        // feature safe rather than dangerous. The sweep reads the database as it is now, before
        // any op has run, so a renamed post is still sitting at its old URL and nothing in
        // `posts` mentions that URL — it would be swept. At apply time the move runs first and
        // the delete would find nothing, so the content would survive; but the *plan* would say
        // "deleting post:Blog/blog/old", and SH108's confirm digest is taken over exactly that
        // list. A rename would ask the operator to confirm the deletion of the post it is
        // preserving, which is worse than the bug it fixes.
        for (ShAgentDesiredState.Move move : nullSafe(desired.moves())) {
            if (move != null && move.from() != null && !move.from().isBlank()) {
                declaredPosts.add(ShAddress.normalizePath(move.from()).toLowerCase(Locale.ROOT));
            }
            if (move != null && move.folder() != null && !move.folder().isBlank()) {
                // A relocation's destination is a placement, exactly like a declared post's
                // `folder` above: the folder a post is being moved into is not undeclared.
                protect(protectedFolders, move.folder().startsWith("folder:")
                        ? ShAddress.parse(move.folder()).path() : move.folder());
            }
        }

        ShSite resolved = shAgentBatchService.requireSite(site);
        List<ShAgentBatch.Op> postDeletes = new ArrayList<>();
        List<Doomed> folderDeletes = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        for (ShAgentDesiredState.Folder folder : folders) {
            String root = ShAddress.normalizePath(folder.path());
            shAgentBatchService.findFolder(resolved, root).ifPresent(target ->
                    sweep(target, root, site, declaredPosts, protectedFolders, visited,
                            postDeletes, folderDeletes));
        }

        // Deepest first, then reverse-alphabetical, so siblings go in a fixed order and
        // a parent never precedes its child (P5: the same document plans the same way).
        folderDeletes.sort(Comparator.comparingInt(Doomed::depth).reversed()
                .thenComparing(Comparator.comparing(Doomed::path).reversed()));

        List<ShAgentBatch.Op> ops = new ArrayList<>(postDeletes);
        for (Doomed doomed : folderDeletes) {
            ops.add(op(ShAgentBatchService.FOLDER_DELETE,
                    ShAddress.folder(site, doomed.path()).toString(), null, null, null));
        }
        return ops;
    }

    /** Depth-first walk of one declared folder's subtree, collecting what must go. */
    private void sweep(ShFolder folder, String path, String site, Set<String> declaredPosts,
                       Set<String> protectedFolders, Set<String> visited,
                       List<ShAgentBatch.Op> postDeletes, List<Doomed> folderDeletes) {
        // Declared folders may nest (/blog and /blog/2026), so the same folder can be
        // reached twice; visiting it once keeps the plan free of duplicate deletes.
        if (!visited.add(folder.getId())) {
            return;
        }
        for (ShPostUnified post : shAgentBatchService.postsIn(folder)) {
            String url = ShAddress.normalizePath(post.getFurl());
            if (!declaredPosts.contains(url.toLowerCase(Locale.ROOT))) {
                postDeletes.add(op(ShAgentBatchService.POST_DELETE,
                        ShAddress.post(site, url).toString(), null, null, null));
            }
        }
        for (ShFolder child : shAgentBatchService.childrenOf(folder)) {
            String childPath = ("/".equals(path) ? "" : path) + "/" + nullSafe(child.getName());
            sweep(child, childPath, site, declaredPosts, protectedFolders, visited,
                    postDeletes, folderDeletes);
            if (!protectedFolders.contains(childPath.toLowerCase(Locale.ROOT))) {
                folderDeletes.add(new Doomed(childPath, depthOf(childPath)));
            }
        }
    }

    /** A folder path and every ancestor of it — all off-limits to the prune. */
    private static void protect(Set<String> protectedFolders, String rawPath) {
        String path = ShAddress.normalizePath(rawPath);
        StringBuilder walked = new StringBuilder();
        protectedFolders.add("/");
        for (String segment : ShAgentBatchService.segments(path)) {
            walked.append('/').append(segment);
            protectedFolders.add(walked.toString().toLowerCase(Locale.ROOT));
        }
    }

    private static int depthOf(String path) {
        return ShAgentBatchService.segments(path).size();
    }

    /** A folder the prune will remove, with its depth so the order can be fixed. */
    private record Doomed(String path, int depth) {
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static ShAgentBatch.Op op(String name, String address, String type, String folder,
                                      tools.jackson.databind.JsonNode data) {
        return new ShAgentBatch.Op(name, address, type, folder, data);
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /** A 400: the document does not say which site it describes. */
    public static class MissingSiteException extends ShTeachingException {

        public MissingSiteException() {
            super(ShProblem.badRequest("missing-site", "The document names no site")
                    .withDetail("Every path in a desired-state document is relative to a site.")
                    .withFix("Add `site` with the site's name (or id).")
                    .withExample("{\"site\":\"Blog\",\"folders\":[{\"path\":\"/blog\"}]}"));
        }
    }

    /** A 400: a declared post has no URL, so it has no identity to be idempotent on. */
    public static class MissingPostUrlException extends ShTeachingException {

        public MissingPostUrlException() {
            super(ShProblem.badRequest("missing-post-url", "A declared post has no `url`")
                    .withDetail("The friendly URL is a post's identity in this document — it is "
                            + "what makes a second apply a no-op instead of a duplicate.")
                    .withFix("Give every post in `posts` a `url`.")
                    .withExample("{\"url\":\"/blog/hello\",\"type\":\"Article\",\"folder\":\"/blog\"}"));
        }
    }

    /** A 400: a declared move does not say what it is moving (SH150). */
    public static class MissingMoveFromException extends ShTeachingException {

        public MissingMoveFromException() {
            super(ShProblem.badRequest("missing-move-from", "A declared move has no `from`")
                    .withDetail("`from` is the URL the post is at now — it is what identifies the "
                            + "post being moved. Without it the move has no subject.")
                    .withFix("Give every entry in `moves` a `from`, and a `to` and/or `folder`.")
                    .withExample("{\"from\":\"/blog/old\",\"to\":\"/blog/new\"}"));
        }
    }

    /** A 400: a declared move that would move nothing (SH150). */
    public static class EmptyMoveException extends ShTeachingException {

        public EmptyMoveException(String from) {
            super(ShProblem.badRequest("empty-move",
                            "The move of '" + from + "' says where it is, not where it goes")
                    // Compiling this to a noop is the failure it is worth a 400 to avoid: the
                    // caller reads `changed:0` as "already in the right place" and believes a
                    // rename happened. It is always a mistake — half a rename, or a misspelt key.
                    .withDetail("A move needs a destination: `to` for a new URL, `folder` for a "
                            + "new folder, or both. Neither one means nothing would happen, and a "
                            + "silent no-op reads exactly like a move that already succeeded.")
                    .withAllowed(List.of("to", "folder"))
                    .withFix("Add `to` (rename), `folder` (relocate), or both — or drop the entry.")
                    .withExample("{\"from\":\"" + from + "\",\"to\":\"/blog/new-name\"}"));
        }
    }

    /** A 400: prune with nothing to scope it to. */
    public static class UnscopedPruneException extends ShTeachingException {

        public UnscopedPruneException() {
            super(ShProblem.badRequest("unscoped-prune", "`prune` needs declared folders")
                    .withDetail("Prune deletes undeclared posts inside the folders this document "
                            + "declares. With no folders it would have no boundary, and a "
                            + "document about one section would delete another.")
                    .withFix("List the folders this document owns in `folders`, or drop `prune`.")
                    .withExample("{\"site\":\"Blog\",\"folders\":[{\"path\":\"/blog\"}],\"prune\":true}"));
        }
    }
}
