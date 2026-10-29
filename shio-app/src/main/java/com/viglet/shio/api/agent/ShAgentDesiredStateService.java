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
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.site.ShSiteLocales;
import com.viglet.shio.render.ShRenderPostTypes;
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
     *
     * <p><b>A folder move splits that first step in two (SH211), and it has to.</b> The folder
     * upserts run first, then the folder moves, then the upserts of the folders that moved.
     * Upserting a move's destination before the move would <em>create</em> it, and
     * {@code folder.move} then refuses an occupied target — the rename would fail on the one
     * path it was asked to produce. The destination's <b>parent</b> still has to exist first,
     * because a move never creates one (a typo in a path must be reported rather than quietly
     * building a new branch), and it does: it is an ancestor, so it is one of the plain entries.
     * The third phase is not redundant — after a successful move the upsert is a noop, and after
     * a <em>noop</em> move (the source is gone because a previous run already did it) it is what
     * makes the folder exist at all.</p>
     */
    List<ShAgentBatch.Op> compile(ShAgentDesiredState desired) {
        if (desired == null || desired.site() == null || desired.site().isBlank()) {
            throw new MissingSiteException();
        }
        String site = desired.site().trim();
        List<ShAgentBatch.Op> folders = new ArrayList<>();
        List<ShAgentBatch.Op> folderMoves = new ArrayList<>();
        List<ShAgentBatch.Op> movedFolders = new ArrayList<>();
        List<ShAgentBatch.Op> moves = new ArrayList<>();
        List<ShAgentBatch.Op> upserts = new ArrayList<>();
        List<ShAgentBatch.Op> publishes = new ArrayList<>();
        List<ShAgentBatch.Op> grants = new ArrayList<>();

        for (ShAgentDesiredState.Folder folder : nullSafe(desired.folders())) {
            ShAgentBatch.Op upsert = op(ShAgentBatchService.FOLDER_UPSERT,
                    ShAddress.folder(site, folder.path()).toString(), null, null, null);
            // SH1277 — who may edit it, once the folder exists: after every folder op, so a grant
            // never races the folder it names, and before the posts, which it then governs.
            if (folder.acl() != null && !folder.acl().isNull()) {
                grants.add(op(ShAgentBatchService.FOLDER_ACL, ShAddress.folder(site, folder.path()).toString(),
                        null, null, tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                                .set(ShAgentBatchService.ACL_KEY, folder.acl())));
            }
            if (folder.moves()) {
                folderMoves.add(folderMoveOp(site, folder));
                movedFolders.add(upsert);
            } else {
                folders.add(upsert);
            }
        }
        for (ShAgentDesiredState.Move move : nullSafe(desired.moves())) {
            moves.add(moveOp(site, move));
        }
        // SH348 — a post that declares `translationOf` is emitted after the ones that do not, so the
        // original exists by the time the translation's row runs. One comparator instead of asking the
        // author to order a file tree they did not write: a pull walks the disk alphabetically, so
        // "list the original first" would work by luck and fail on the first site where it does not.
        // Stable, so two posts in the same class keep document order (P5).
        List<ShAgentDesiredState.Post> ordered = new ArrayList<>(nullSafe(desired.posts()));
        ordered.sort(Comparator.comparing(post ->
                post.translationOf() == null || post.translationOf().isBlank() ? 0 : 1));
        for (ShAgentDesiredState.Post post : ordered) {
            if (post.url() == null || post.url().isBlank()) {
                throw new MissingPostUrlException();
            }
            // SH467 — the address collapses `/x/` to `/x` and must, because it is the identity the
            // render digest hashes. This is the line the declared spelling used to die on: it is
            // carried beside the address instead, so the write can store what the source served.
            String address = ShAddress.post(site, post.url()).toString();
            upserts.add(new ShAgentBatch.Op(ShAgentBatchService.POST_UPSERT, address,
                    post.type(), post.folder(), null, post.data(), null, post.locale(),
                    post.translationOf(), post.url()));
            // Absent `publish` means "leave it alone", so only an explicit true or false
            // produces an op. Treating absence as false would unpublish live pages the
            // moment someone trimmed a field from the document (P7).
            if (Boolean.TRUE.equals(post.publish())) {
                publishes.add(op(ShAgentBatchService.POST_PUBLISH, address, null, null, null));
            } else if (Boolean.FALSE.equals(post.publish())) {
                publishes.add(op(ShAgentBatchService.POST_UNPUBLISH, address, null, null, null));
            }
        }

        List<ShAgentBatch.Op> ops = new ArrayList<>();
        // SH607 — the model before anything written against it, which is the order
        // `ShBlueprintWriteService` already applies for the same reason: a document that declares
        // its post-types and writes a post of one is one intention, and splitting it into two calls
        // is what made `shio convert` unable to run against a fresh instance at all. Compiled to
        // `render.provision` rather than taught to this class, which is P10 and also what gives the
        // dry run its answer — `Declared.withPostTypesFrom` reads this very op.
        //
        // SH395 — and a document that WRITES a template needs the render model whether or not it
        // named a vocabulary. `render.provision` has always ensured the render five; the only way
        // to reach it was `postTypes`, which is the SH95 *vocabulary* list — so a document
        // declaring a `PageLayout` post could not ask for the type that post is of, and applying
        // one into a fresh instance failed on a post-type nobody could have provisioned.
        //
        // Inferred rather than declared, which this codebase is usually reluctant to do. It is
        // right here for one reason: a document that writes a PageLayout has already said it needs
        // PageLayout, and making the author say it twice is the trap SH395 is about — the model
        // and the content are one intention, and splitting them is what P2 exists to refuse.
        boolean writesTemplates = desired.posts() != null && desired.posts().stream()
                .map(ShAgentDesiredState.Post::type)
                .anyMatch(type -> type != null && ShRenderPostTypes.NAMES.contains(type));
        List<String> vocabulary = desired.postTypes() == null ? List.of() : desired.postTypes();
        // SH871 — the fields the document's own posts need declared, on the same op and for the
        // same reason: the model before anything written against it. A document that declares them
        // and writes them is one intention, and splitting it into two calls is what left a
        // conversion with 18 uploaded assets and no page.
        Map<String, List<String>> declared = desired.postTypeFields() == null
                ? Map.of() : desired.postTypeFields();
        if (!vocabulary.isEmpty() || writesTemplates || !declared.isEmpty()) {
            ops.add(op(ShAgentBatchService.RENDER_PROVISION, null, null, null,
                    postTypeData(vocabulary, declared)));
        }
        // SH439 — `createSite` first, before the folder a post needs. It is the whole flag: the op
        // is `site.upsert`, so an apply creates the site and a *plan* resolves against it too,
        // because SH329's `withSitesFrom` reads the sites a run would create off the run's own ops.
        // Nothing else here changes — that is the point of compiling to an op rather than teaching
        // this class what a site is (P10).
        if (desired.createSite()) {
            // SH591 — `scratch` rides the create and only the create. It is not a second op the way
            // the binding below is, because unlike `postTypeLayout` it is *not* settable without
            // `createSite`: a flag an existing site could gain would make `shio site rm --scratch` a
            // way to bulk-delete a site somebody chose to keep.
            ops.add(op(ShAgentBatchService.SITE_UPSERT, ShAddress.site(site).toString(),
                    null, null, desired.scratch() ? scratchData() : null));
        }
        // SH449 — the binding, as its own op right after the create. A fidelity replication writes
        // a PageLayout, and a layout nobody bound renders nothing: `/preview/<site>/` answers 400
        // naming a post-type instead of serving the page the document just described.
        //
        // It was a second op because `site.upsert`'s create branch dropped `data.postTypeLayout`;
        // SH453 fixed that, so folding it into the op above is now *possible* and is deliberately
        // not done. `postTypeLayout` is settable without `createSite`, so the separate op is the
        // path that serves both cases — folding it would buy one op on one of them and cost a
        // branch on every run. Idempotent either way: the create writes nothing here, and the fill
        // that follows binds what is unbound.
        if (desired.postTypeLayout() != null && !desired.postTypeLayout().isEmpty()) {
            ops.add(op(ShAgentBatchService.SITE_UPSERT, ShAddress.site(site).toString(),
                    null, null, bindingData(desired.postTypeLayout())));
        }
        // SH1122 — the site's own settings, from the tree's `_site.md`. A third `site.upsert` for the
        // reason the binding above is a second one: these are settable without `createSite`, so one
        // op serves both the site this document just created and the one that was already there.
        // Idempotent either way — the op fills what is empty, keeps what somebody chose, and says
        // which it did, so a re-run reports noop.
        tools.jackson.databind.JsonNode settings = settingsData(desired.settings());
        if (settings != null) {
            ops.add(op(ShAgentBatchService.SITE_UPSERT, ShAddress.site(site).toString(),
                    null, null, settings));
        }
        ops.addAll(folders);
        ops.addAll(folderMoves);
        ops.addAll(movedFolders);
        ops.addAll(grants);
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
    /**
     * One declared folder rename as a {@code folder.move} op (SH211).
     *
     * <p>The destination is the entry's own {@code path}, because a folder's name chain is its
     * address — so unlike a post's move there is no second axis and nothing to refuse for being
     * empty. A {@code movedFrom} equal to the path is refused rather than compiled: it is either
     * half of an edit (the path was changed and {@code movedFrom} was set to the new value) or a
     * copy-paste, and a noop is how a caller comes to believe a rename happened.</p>
     *
     * <p>Every other refusal is the writer's, which is the point of compiling to an existing op
     * (P10): a cross-site move, an occupied target, a site root, a destination inside the
     * folder's own subtree and a missing destination parent all fail exactly as they do for a
     * hand-written batch. The one case that is <em>not</em> a refusal is a source that is already
     * gone while the destination exists — {@code folderMove} answers that with a noop, which is
     * what makes a document with a rename in it safe to apply twice.</p>
     */
    private static ShAgentBatch.Op folderMoveOp(String site, ShAgentDesiredState.Folder folder) {
        String from = folder.movedFrom().trim();
        if (ShAddress.normalizePath(from).equalsIgnoreCase(ShAddress.normalizePath(folder.path()))) {
            throw new CircularFolderMoveException(folder.path());
        }
        return new ShAgentBatch.Op(ShAgentBatchService.FOLDER_MOVE,
                ShAddress.folder(site, from).toString(),
                null,
                null,
                ShAddress.folder(site, folder.path()).toString(),
                null);
    }

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
            // SH211 — and its *source* too, for the same reason a post move's is protected below,
            // one level up and with a much worse outcome. The sweep reads the database before any
            // op has run, so a renamed section is still at its old path and nothing declares that
            // path: it would be swept, and a folder delete takes every post beneath it. At apply
            // time the move runs first and the delete would find nothing — but the *plan* would
            // say "deleting folder:Blog/news", and SH108's confirm digest is taken over exactly
            // that list, so a rename would ask the operator to confirm destroying the section it
            // is preserving.
            if (folder.moves()) {
                protect(protectedFolders, folder.movedFrom());
            }
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

    /**
     * The binding map as the {@code data} a {@code site.upsert} reads (SH449).
     *
     * <p>Entries are copied in the document's own order and a blank title is dropped: the batch
     * op keeps string values only, so a null here would be silently skipped one layer down and
     * the caller would learn about it from a render error. Sorting is not applied — a map with
     * one binding is the common case, and the document's order is the caller's.</p>
     */
    private static tools.jackson.databind.JsonNode bindingData(java.util.Map<String, String> bindings) {
        tools.jackson.databind.node.ObjectNode data = tools.jackson.databind.node.JsonNodeFactory
                .instance.objectNode();
        tools.jackson.databind.node.ObjectNode layout = data.putObject("postTypeLayout");
        for (java.util.Map.Entry<String, String> entry : bindings.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                layout.put(entry.getKey(), entry.getValue());
            }
        }
        return data;
    }

    /**
     * SH607 — `{"types": [...]}` for the provision op, as the JSON literal the op reads.
     *
     * <p>Sorted and de-duplicated here rather than trusted from the document: this list ends up in
     * an op row a caller diffs between runs, and two conversions of one capture must produce the
     * same bytes (P5). Blanks are dropped for {@link #bindingData}'s reason — a value the op would
     * silently skip is one the caller learns about from a later failure.</p>
     */
    private static tools.jackson.databind.JsonNode postTypeData(List<String> postTypes,
                                                                Map<String, List<String>> fields) {
        tools.jackson.databind.node.ObjectNode data = tools.jackson.databind.node.JsonNodeFactory
                .instance.objectNode();
        tools.jackson.databind.node.ArrayNode types = data.putArray("types");
        postTypes.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .forEach(types::add);
        // SH871 — same treatment as the list above, and the key is absent when there is nothing to
        // declare: an empty object in an op row is a diff every run carries for no reader.
        if (fields.isEmpty()) {
            return data;
        }
        tools.jackson.databind.node.ObjectNode declared = data.putObject("fields");
        fields.keySet().stream().sorted().forEach(type -> {
            List<String> names = fields.get(type);
            if (names == null) {
                return;
            }
            tools.jackson.databind.node.ArrayNode array = declared.putArray(type);
            names.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .forEach(array::add);
        });
        return data;
    }

    /** SH591 — `{"scratch": true}` for the create op, as the JSON literal the op reads. */
    /**
     * The four keys a site marker projects, as the {@code data} a {@code site.upsert} reads (SH1122).
     *
     * <p><b>An allow-list, and that is the whole of this method.</b> A document is written by whoever
     * is pushing, so a {@code settings} block copied through unfiltered would be a second path to
     * {@code scratch} — which is settable on a create and never afterwards, the guarantee
     * {@code shio site rm --scratch} rests on — and to {@code postTypeLayout}, which has a field of
     * its own above. Filtering here rather than in the record because the record is data: a DTO that
     * validated would be a second place the rule lives, and the two would disagree eventually.</p>
     *
     * <p>{@code locales} is copied as the object it is (SH1056 validates it against the instance's
     * catalogue and turns it into the bytes to store); the other three are strings, and a blank is
     * dropped rather than sent — the op reads a blank as "nothing requested", so sending one would
     * make a marker with an empty key indistinguishable from one without it.</p>
     *
     * @return the op's data, or {@code null} when the block names nothing this projects
     */
    private static tools.jackson.databind.JsonNode settingsData(
            tools.jackson.databind.JsonNode settings) {
        if (settings == null || !settings.isObject()) {
            return null;
        }
        tools.jackson.databind.node.ObjectNode data = tools.jackson.databind.node.JsonNodeFactory
                .instance.objectNode();
        for (String key : SITE_SETTING_KEYS) {
            tools.jackson.databind.JsonNode value = settings.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (ShSiteLocales.FIELD.equals(key)) {
                if (value.isObject()) {
                    data.set(key, value);
                }
                continue;
            }
            String text = value.isString() ? value.stringValue() : null;
            if (text != null && !text.isBlank()) {
                data.put(key, text);
            }
        }
        return data.isEmpty() ? null : data;
    }

    /**
     * What a {@code settings} block may carry (SH1122) — the three fillable strings and the policy.
     *
     * <p>Spelled here rather than read from {@code ShSiteWriteService.FILLABLE} on purpose: that list
     * is what the op will fill, and this is what a <em>document</em> may ask it to. A field added to
     * the first should be a decision about the projection before it is a key in a file, and deriving
     * one from the other would make it neither.</p>
     */
    private static final List<String> SITE_SETTING_KEYS =
            List.of("url", "furl", "description", ShSiteLocales.FIELD);

    private static tools.jackson.databind.JsonNode scratchData() {
        tools.jackson.databind.node.ObjectNode data = tools.jackson.databind.node.JsonNodeFactory
                .instance.objectNode();
        data.put("scratch", true);
        return data;
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
    /** A 400: a folder declaring itself as the folder it moved from (SH211). */
    public static class CircularFolderMoveException extends ShTeachingException {

        public CircularFolderMoveException(String path) {
            super(ShProblem.badRequest("circular-folder-move",
                            "The folder '" + path + "' says it moved from itself")
                    // The likeliest mistake by far, and the reason this is a 400 rather than a
                    // noop: an author who renamed the path and then set `movedFrom` to the *new*
                    // value has written a document that reports success and moves nothing.
                    .withDetail("`movedFrom` is the path this folder is at now — the one that is "
                            + "no longer in the tree. Equal to `path`, it asks for nothing, and a "
                            + "silent no-op reads exactly like a rename that already succeeded.")
                    .withFix("Set `movedFrom` to the folder's previous path, or drop it if the "
                            + "folder is only being declared.")
                    .withExample("{\"path\":\"" + path + "\",\"movedFrom\":\"/the-old-name\"}"));
        }
    }

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
