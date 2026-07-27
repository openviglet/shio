/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * A declarative statement of what a site's content <em>should</em> be (SH82).
 *
 * <h2>Why a second shape on top of the batch</h2>
 *
 * <p>A batch (SH81) says "do these six things"; a desired state says "this is the
 * site". The difference matters because only the second one is <b>re-runnable</b>:
 * an agent that has to remember what it already did is an agent that will get it
 * wrong after a context compaction. This document is a fact about the world, so
 * applying it twice is applying it once, and the second run's plan is empty.</p>
 *
 * <p>The engine underneath is the same one, deliberately (law P10): this document is
 * <em>compiled</em> into batch ops and then planned or applied by
 * {@code ShAgentBatchService}. SH65 already proved the shape for post-types —
 * declarative source → plan → apply → drift guard — and this is that engine over a
 * second resource type, not a second engine.</p>
 *
 * <h2>Why a state document carries {@code moves} (SH150)</h2>
 *
 * <p>Everything else here is pure state, and that is the property that makes an apply
 * re-runnable. A <b>move is not state</b> — "this post is at /b" and "this post used to be
 * at /a and is now at /b" describe the same world and imply different work. Declared as
 * state alone, a rename is an undeclared post at the old URL plus a declared one at the
 * new: a {@code prune} deletes the first and creates the second, so the replacement gets a
 * new id and loses the post's audit trail, its version history and every inbound reference.
 * That is exactly the workaround SH134 exists to prevent, reached from the file side.</p>
 *
 * <p>So the document needs one channel for <em>intent</em>, and it is deliberately the
 * narrowest one that works: a from/to pair, compiled to {@code post.move}, applied before
 * the upserts. It stays re-runnable because a move whose {@code from} no longer exists is a
 * noop, not an error — the second apply of the same document does nothing, which is the
 * whole point of the shape.</p>
 *
 * @param site   site name or id; every path in this document is relative to it
 * @param folders folder paths that must exist, e.g. {@code /blog}. Intermediate
 *                folders are created implicitly, so declaring {@code /a/b/c} is enough
 * @param posts   the posts that must exist, with the values they must hold
 * @param moves   renames and relocations to perform first, so a post keeps its identity
 *                across one (SH150). Optional, and a move of something already moved is a
 *                noop
 * @param prune   delete posts that live under the declared folders but are not declared
 *                here. Off by default and never implied: prune is the one operation
 *                whose blast radius grows with content the author never mentioned (P7),
 *                which is why the plan lists every deletion before {@code apply} runs
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                                  List<Move> moves, boolean prune, String confirm) {

    /** The four-field form: every document that does not prune, and every pre-SH108 caller. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts, boolean prune) {
        this(site, folders, posts, null, prune, null);
    }

    /** The five-field form: pre-SH150 callers, which never moved anything. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                               boolean prune, String confirm) {
        this(site, folders, posts, null, prune, confirm);
    }

    /** @param path folder path from the site root, e.g. {@code /blog/2026} */
    public record Folder(String path) {
    }

    /**
     * One post's rename and/or relocation (SH150).
     *
     * @param from   the URL the post is at <em>now</em> — what identifies it
     * @param to     the URL it should be at; absent leaves the URL alone, which is how a
     *               pure relocation between folders is expressed
     * @param folder the folder it should live in; absent leaves it where it is. This axis
     *               exists because {@code post.upsert} deliberately does not relocate an
     *               existing post, so without it a changed {@code folder} was a write that
     *               reported success and moved nothing
     */
    public record Move(String from, String to, String folder) {
    }

    /**
     * @param url     the post's friendly URL — its identity in this document, which is
     *                what makes an apply idempotent without storing an id anywhere
     * @param type    post-type name; required only when the post has to be created
     * @param folder  containing folder path; required only when the post has to be
     *                created (a URL says nothing about where a post lives — SH79's
     *                "two namespaces" rule)
     * @param data    field values, keyed by post-type attribute name
     * @param publish publish it after writing. Absent means "leave the publish state
     *                alone", which is not the same as {@code false}: an apply that
     *                silently unpublished live pages because a field was omitted would
     *                be exactly the surprise P7 forbids
     */
    public record Post(String url, String type, String folder, JsonNode data, Boolean publish) {
    }
}
