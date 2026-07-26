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

    /** What would change. Writes nothing, ever — the endpoint's only guarantee. */
    @Transactional(readOnly = true)
    public ShAgentChangeSet plan(ShAgentDesiredState desired, boolean ids, String actor) {
        return shAgentBatchService.run(new ShAgentBatch(true, compile(desired)), ids, actor);
    }

    /** Makes it so, atomically. */
    @Transactional
    public ShAgentChangeSet apply(ShAgentDesiredState desired, boolean ids, String actor) {
        return shAgentBatchService.run(new ShAgentBatch(false, compile(desired)), ids, actor);
    }

    /**
     * Compiles a document into ops.
     *
     * <p>Order is load-bearing and therefore fixed: folders, then posts, then publishes,
     * then prunes. Folders first because a post needs somewhere to go; publishes after
     * all upserts so a page never goes live referencing a sibling that does not exist
     * yet; prunes last so a deletion can never race the creation of its replacement.</p>
     */
    List<ShAgentBatch.Op> compile(ShAgentDesiredState desired) {
        if (desired == null || desired.site() == null || desired.site().isBlank()) {
            throw new MissingSiteException();
        }
        String site = desired.site().trim();
        List<ShAgentBatch.Op> folders = new ArrayList<>();
        List<ShAgentBatch.Op> upserts = new ArrayList<>();
        List<ShAgentBatch.Op> publishes = new ArrayList<>();

        for (ShAgentDesiredState.Folder folder : nullSafe(desired.folders())) {
            folders.add(op(ShAgentBatchService.FOLDER_UPSERT,
                    ShAddress.folder(site, folder.path()).toString(), null, null, null));
        }
        for (ShAgentDesiredState.Post post : nullSafe(desired.posts())) {
            if (post.url() == null || post.url().isBlank()) {
                throw new MissingPostUrlException();
            }
            String address = ShAddress.post(site, post.url()).toString();
            upserts.add(op(ShAgentBatchService.POST_UPSERT, address,
                    post.type(), post.folder(), post.data()));
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
        ops.addAll(upserts);
        ops.addAll(publishes);
        ops.addAll(pruneOps(desired, site));
        return List.copyOf(ops);
    }

    /**
     * Deletions implied by {@code prune}: posts living in a declared folder that the
     * document does not declare.
     *
     * <p><b>Scoped to the declared folders on purpose.</b> A prune that swept the whole
     * site would let a document about one section delete another — the blast radius
     * would depend on what the author forgot to mention, which is the definition of a
     * surprise (P7). Declaring no folders and asking to prune is therefore an error,
     * not a licence to delete everything.</p>
     */
    private List<ShAgentBatch.Op> pruneOps(ShAgentDesiredState desired, String site) {
        if (!desired.prune()) {
            return List.of();
        }
        List<ShAgentDesiredState.Folder> folders = nullSafe(desired.folders());
        if (folders.isEmpty()) {
            throw new UnscopedPruneException();
        }
        Set<String> declared = new LinkedHashSet<>();
        for (ShAgentDesiredState.Post post : nullSafe(desired.posts())) {
            declared.add(ShAddress.normalizePath(post.url()).toLowerCase(Locale.ROOT));
        }
        ShSite resolved = shAgentBatchService.requireSite(site);
        List<ShAgentBatch.Op> ops = new ArrayList<>();
        for (ShAgentDesiredState.Folder folder : folders) {
            Optional<ShFolder> target = shAgentBatchService
                    .findFolder(resolved, ShAddress.normalizePath(folder.path()));
            if (target.isEmpty()) {
                continue;
            }
            for (ShPostUnified post : shAgentBatchService.postsIn(target.get())) {
                String url = ShAddress.normalizePath(post.getFurl());
                if (!declared.contains(url.toLowerCase(Locale.ROOT))) {
                    ops.add(op(ShAgentBatchService.POST_DELETE,
                            ShAddress.post(site, url).toString(), null, null, null));
                }
            }
        }
        return ops;
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
