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
import java.util.Map;

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
 * @param scratch    SH591 — declare the created site <b>disposable</b>. Only meaningful with
 *                   {@code createSite}, and that is the guarantee rather than a limitation: the
 *                   flag says the site was never meant to be kept, and only its creator can say
 *                   that honestly. A document a human read before it ran is where the declaration
 *                   belongs, which is what keeps `shio site rm --scratch` from being a surprise.
 * @param createSite create {@code site}, blank, as this run's <em>first</em> step when it is
 *                not there (SH439). Opt-in for the same reason {@code prune} is: a document
 *                naming a site nobody made is usually a typo, and the refusal that catches
 *                it is worth more than the convenience of guessing (P7). Compiles to the
 *                {@code site.upsert} op the batch surface already has, which is what makes a
 *                <em>dry run</em> of a document into a new site resolve — SH329 reads the
 *                sites a run would create off the run's own ops
 * @param postTypeLayout post-type name → {@code PageLayout} title, the SH328 binding, as a
 *                second {@code site.upsert} op after {@code createSite} (SH449). A document
 *                that writes a layout and cannot bind it describes a site that does not
 *                render, and the binding is the one edge no {@code post.upsert} can express —
 *                a replication in fidelity mode writes the layout, so it needed this or a
 *                second, un-`--out`-able call. Deliberately <b>after</b> the create rather
 *                than folded into it: it began as a workaround for a create branch that
 *                dropped bindings, and since SH453 fixed that it is a choice — this field is
 *                settable without {@code createSite}, so one op serves both cases
 * @param postTypes SH95 vocabulary post-type names this document's posts are written against,
 *                installed before anything else runs (SH607). Compiles to the
 *                {@code render.provision} op, which is idempotent, so a document may declare
 *                them on every run. <b>Names, never definitions</b>: the model is the
 *                instance's, shipped at {@code post-types/vocabulary/}, and a document that
 *                could carry a def would be a second way to change the content model — one an
 *                agent could use to redefine {@code Page} underneath somebody's site. A name
 *                this instance does not ship is a 400 listing the ones it does. What this
 *                closes: {@code shio convert} wrote {@code Page} and {@code Section*} posts and
 *                nothing provisioned them, so the whole replication chain — fidelity included —
 *                failed on a bare instance with {@code allowed: [File, PageLayout, Redirect,
 *                Region, SiteScripts, Theme]}, and passed everywhere it was tried because a
 *                development instance had accumulated the types from an earlier blueprint
 * @param postTypeFields post-type name → field names this document needs the type to declare,
 *                added to the {@code render.provision} op above as {@code data.fields} (SH871).
 *                <b>Names, never definitions</b>, for {@code postTypes}' reason one level in: each
 *                is appended as a plain optional {@code Text} and nothing existing is changed, so
 *                a document cannot use this to redefine a field somebody owns. What this closes:
 *                {@code convert --annotate} stamps a verbatim page's own markup with the fields
 *                its blocks address, computed from the capture — so their names cannot be shipped
 *                in any catalogue, and the run that computes them had no way to declare them. It
 *                named forty, the batch refused the write on the first one, and the conversion
 *                died having uploaded 18 assets and written no page
 *
 * @param settings the site's own settings, as the tree's `_site.md` carries them (SH1122):
 *                {@code url}, {@code furl}, {@code description} and SH1056's {@code locales}
 *                policy. Compiled to one more {@code site.upsert}, which is the op that already
 *                fills each of them and already refuses to replace a value somebody chose — so
 *                this adds a way to <em>author</em> a site's settings and no new rule about them.
 *                <b>Only those four keys are read</b>, filtered where the op is built rather than
 *                trusted here: {@code scratch} is settable on a create and never afterwards, and
 *                {@code postTypeLayout} has its own field above, so a {@code settings} block that
 *                could carry either would be a second path to both and a way past the guarantee
 *                each one rests on
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                                  List<Move> moves, boolean prune, String confirm,
                                  boolean createSite, Map<String, String> postTypeLayout,
                                  boolean scratch, List<String> postTypes,
                                  Map<String, List<String>> postTypeFields, JsonNode settings) {

    /** The eleven-field form: every caller before SH1122 had no file to author a site's settings in. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                               List<Move> moves, boolean prune, String confirm,
                               boolean createSite, Map<String, String> postTypeLayout,
                               boolean scratch, List<String> postTypes,
                               Map<String, List<String>> postTypeFields) {
        this(site, folders, posts, moves, prune, confirm, createSite, postTypeLayout, scratch,
                postTypes, postTypeFields, null);
    }

    /** The ten-field form: every caller before SH871 wrote only fields somebody had declared. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                               List<Move> moves, boolean prune, String confirm,
                               boolean createSite, Map<String, String> postTypeLayout,
                               boolean scratch, List<String> postTypes) {
        this(site, folders, posts, moves, prune, confirm, createSite, postTypeLayout, scratch,
                postTypes, null);
    }

    /** The nine-field form: every caller before SH607 wrote against a model somebody else made. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                               List<Move> moves, boolean prune, String confirm,
                               boolean createSite, Map<String, String> postTypeLayout,
                               boolean scratch) {
        this(site, folders, posts, moves, prune, confirm, createSite, postTypeLayout, scratch,
                null, null);
    }

    /** The eight-field form: every caller before SH591 made a site declarable as disposable. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                               List<Move> moves, boolean prune, String confirm,
                               boolean createSite, Map<String, String> postTypeLayout) {
        this(site, folders, posts, moves, prune, confirm, createSite, postTypeLayout, false, null);
    }

    /** The four-field form: every document that does not prune, and every pre-SH108 caller. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts, boolean prune) {
        this(site, folders, posts, null, prune, null, false, null, false, null);
    }

    /** The five-field form: pre-SH150 callers, which never moved anything. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                               boolean prune, String confirm) {
        this(site, folders, posts, null, prune, confirm, false, null, false, null);
    }

    /** The six-field form: pre-SH439 callers, which never created the site they wrote into. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                               List<Move> moves, boolean prune, String confirm) {
        this(site, folders, posts, moves, prune, confirm, false, null, false, null);
    }

    /** The seven-field form: pre-SH449 callers, which never bound a layout. */
    public ShAgentDesiredState(String site, List<Folder> folders, List<Post> posts,
                               List<Move> moves, boolean prune, String confirm,
                               boolean createSite) {
        this(site, folders, posts, moves, prune, confirm, createSite, null, false, null);
    }

    /**
     * A folder that must exist, and optionally the one it used to be (SH211).
     *
     * <p>{@code movedFrom} is the folder half of the {@code moves} channel above, and it exists
     * for a worse version of the same defect. A renamed section declared as state alone is an
     * undeclared folder at the old path plus a declared one at the new, and a {@code prune}
     * deletes the first — which for a <b>folder</b> takes <em>every post beneath it</em>
     * (`ShFolderWriteService` cascades), where the post case only lost one post's history.</p>
     *
     * <p>It is on {@code Folder} rather than in {@code moves} because a folder move is not a
     * from/to pair with two independent axes: a folder's whole name chain <em>is</em> its
     * address, so its last segment is the new name and rename and relocate are one op (SH137).
     * The destination is therefore the entry's own {@code path}, and there is nothing left for a
     * second field to say.</p>
     *
     * @param path      folder path from the site root, e.g. {@code /blog/2026}
     * @param movedFrom the path this folder is at <em>now</em>, when it is moving; absent for
     *                  every folder that is merely being declared. A {@code movedFrom} whose
     *                  folder no longer exists while {@code path} does is a <b>noop</b>, which is
     *                  what keeps a document re-runnable (SH82) — the second apply of the same
     *                  document does nothing
     * @param acl       SH1277 — who may edit this folder and what is below it, as
     *                  {@code [{"group": "Administrator", "permission": "WRITE"}]} ({@code user} or
     *                  {@code role} in place of {@code group}). Absent leaves the folder's grants
     *                  alone; present replaces them, so the same document re-applied changes nothing.
     *                  It governs the console and the agent path, never who may read a delivered page
     */
    public record Folder(String path, String movedFrom, JsonNode acl) {

        /** The one-field form: every folder that is only being declared, and every pre-SH211 caller. */
        public Folder(String path) {
            this(path, null, null);
        }

        /** The two-field form, before SH1277 let a folder say who may edit it. */
        public Folder(String path, String movedFrom) {
            this(path, movedFrom, null);
        }

        /** Whether this entry asks for a move rather than only declaring a folder. */
        public boolean moves() {
            return movedFrom != null && !movedFrom.isBlank();
        }
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
    public record Post(String url, String type, String folder, JsonNode data, Boolean publish,
                       String locale, String translationOf) {

        /** The six-field form, before SH348 made the sibling link declarable. */
        public Post(String url, String type, String folder, JsonNode data, Boolean publish,
                    String locale) {
            this(url, type, folder, data, publish, locale, null);
        }

        /** The five-field form, before SH348 made a locale declarable. */
        public Post(String url, String type, String folder, JsonNode data, Boolean publish) {
            this(url, type, folder, data, publish, null, null);
        }
    }
}
