/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The op vocabulary, published (SH332).
 *
 * <h2>Why it exists</h2>
 *
 * <p>{@code tools/list} carries tool schemas, and the <b>op</b> vocabulary —
 * {@code site.upsert}, {@code post.move}, {@code folder.delete}, and which keys each one takes —
 * existed only as prose inside one tool's description. Two of SH331's four wrong calls were that:
 * an agent inferring a key name because nothing stated it. This is the same move SH260 made for
 * render helpers and SH300 for DOM attributes — <b>the code that owns the set answers what the set
 * is, instead of a reader restating it</b> — and it is the half of SH331 that prevents the guess
 * rather than repairing it.</p>
 *
 * <h2>Optimised for an agent, not for a person</h2>
 *
 * <p>The design steer here inverts the usual one, and it is worth stating because the rendering
 * below reads as machine noise: the console is the human's surface, so if this list is terse to the
 * point of ugliness that is acceptable. An agent inferring a key name from four inconsistent
 * spellings is not.</p>
 *
 * <h2>What keeps it true</h2>
 *
 * <p>The op <b>names</b> come from {@link ShAgentBatchService#OPS}, so there is one list. The
 * per-op <b>keys</b> are declared here and asserted against the service's own refusals by
 * {@code ShAgentOpsContractTest} — a source scan was tried first and is not faithful, because a
 * handler reads {@code op.address()} through a helper rather than directly (measured: the scan finds
 * {@code data} on {@code site.upsert} and nothing else). So the guard is behavioural: omit a
 * declared-required key and the plan must refuse. And an op in {@code OPS} with no spec here fails
 * the build, which is the assertion that matters most — a new op arriving undocumented is exactly
 * how this drifted in the first place.</p>
 *
 * <h2>Three requirement kinds, because two of them are conditional</h2>
 *
 * <p>SH175's rule: an argument whose requirement depends on another value has to say so on itself.
 * {@code post.upsert} needs {@code type} and {@code folder} <em>only when creating</em>, and
 * {@code post.move} needs {@code to} <b>or</b> {@code folder} — flattening either into "required"
 * would make a correct call look wrong, and flattening them into "optional" is what cost SH175 a
 * round trip on the most common write there is.</p>
 *
 * <h2>Both surfaces hand over the same bytes (SH346)</h2>
 *
 * <p>It was MCP-only for one block — {@code shio://ops} is a resource, so an HTTP-only agent could not
 * read the one document that says which keys a write takes, which is the inverse of SH166 and the P1
 * shape this repository refuses. It now also rides the context pack as {@code ?include=ops}, and what
 * settled <em>which</em> REST surface is a measurement rather than a preference:</p>
 *
 * <ul>
 *   <li>A <b>manifest</b> section charges {@link #terse()} — measured at <b>447 estimated tokens</b>
 *       — to the one document every session reads first, whether or not that session ever writes.
 *       Against a manifest of 2343 that is a 19% regression, which is what made this the option to
 *       reject rather than the obvious home.</li>
 *   <li>A <b>context-pack</b> section charges nothing to anybody who does not ask, because SH157
 *       already established the shape for exactly this: a non-default section, attached
 *       <em>after</em> the digest is taken. So it does not move the SH116 digest either, and an agent
 *       holding a cached pack does not re-read.</li>
 *   <li>The manifest still got <em>smaller</em>: the {@code /agent/batch} index entry used to
 *       enumerate op names in prose, which is the guess this class exists to prevent, and now points
 *       at the vocabulary instead.</li>
 * </ul>
 *
 * <p>Every surface serves {@link #terse()} verbatim — the MCP resource, the pack's JSON and the
 * pack's terse rendering are byte-identical — so the two cannot drift into disagreeing about what a
 * write takes.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentOps {

    /**
     * @param op                    the verb, from {@link ShAgentBatchService#OPS}
     * @param required              keys the op always needs
     * @param requiredWhenCreating  keys needed only when the subject does not exist yet
     * @param atLeastOneOf          keys of which at least one must be present
     * @param optional              keys the op reads when present
     * @param purpose               one line, imperative
     */
    public record OpSpec(String op, List<String> required, List<String> requiredWhenCreating,
                         List<String> atLeastOneOf, List<String> optional, String purpose) {
    }

    /** The keys, named once — nine literals is a typo waiting to be published as a contract. */
    private static final String ADDRESS = "address";
    private static final String TYPE = "type";
    private static final String FOLDER = "folder";
    private static final String TO = "to";
    private static final String DATA = "data";
    private static final String WHEN = "when";
    /** SH721 — the digest a read handed back, making the write conditional on it. */
    private static final String EXPECT = "expect";
    /** SH987 — which captured version post.rollback puts back. */
    private static final String REVISION = "revision";
    /** SH992 — clears the pending instant of the transition the op names. */
    private static final String CANCEL = "cancel";

    /**
     * SH197 — a {@code LinkedHashMap}, not the stability helper wrapped around a {@code Map.of}.
     *
     * <p>That was the shape this task exists to ban: the stability helper keeps whatever order its
     * source had, and a {@code Map.of} with ten entries has the order of a per-JVM salt. It froze a
     * random order and read like the fix. Nothing rendered from it — {@link #all()} projects
     * {@link ShAgentBatchService#OPS}, which is a source-ordered list — but {@link #specified()}
     * returned {@code keySet()}, so the one order a caller could observe was the salt's.</p>
     */
    private static final Map<String, OpSpec> SPECS = specs(
            new OpSpec(ShAgentBatchService.SITE_UPSERT,
                    List.of(ADDRESS), List.of(), List.of(), List.of(DATA),
                    // SH1299 added membersLogin and paid for it in this sentence's own words.
                    "Create a blank site, or fill empty url/description/furl. "
                            + "`data.postTypeLayout` binds a post-type to a PageLayout title. "
                            + "`data.scratch:true` marks it DISPOSABLE (CREATE only). "
                            + "`data.membersLogin:true` sends gated visitors to sign in. "
                            + "`data.reindex:true` back-fills the search index, paged by "
                            + "`data.limit`/`data.from`. Never replaces a set value."),
            new OpSpec(ShAgentBatchService.SITE_DELETE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(DATA),
                    "Trash a site and all its content."),
            new OpSpec(ShAgentBatchService.FOLDER_UPSERT,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Create a folder at that name chain, or confirm it."),
            new OpSpec(ShAgentBatchService.FOLDER_MOVE,
                    List.of(ADDRESS, TO), List.of(), List.of(), List.of(),
                    "Rename or relocate a section. `to` is the whole new name chain, so its last "
                            + "segment is the new name. No post's URL changes."),
            // SH1277 — who may edit a section; never who may read a delivered page. Paid for by trimming
            // site.delete, folder.copy and folder.delete to their decisions (SH346's ceiling).
            new OpSpec(ShAgentBatchService.FOLDER_ACL,
                    List.of(ADDRESS, DATA), List.of(), List.of(), List.of(),
                    "Editors: `data.acl` [{group, permission}]."),
            // SH1285 — who may read a section's delivered pages; folder.acl's visitor-side twin.
            new OpSpec(ShAgentBatchService.FOLDER_VISIBILITY,
                    List.of(ADDRESS, DATA), List.of(), List.of(), List.of(),
                    "Readers: `data.visibility` public|members, `data.groups`."),
            new OpSpec(ShAgentBatchService.FOLDER_COPY,
                    List.of(ADDRESS, TO), List.of(), List.of(), List.of(),
                    "Duplicate a section, as drafts. `to` is the copy's name chain; occupied "
                            + "is refused. May cross sites."),
            new OpSpec(ShAgentBatchService.FOLDER_DELETE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(DATA),
                    "Trash a folder and all beneath it; rename with folder.move."),
            new OpSpec(ShAgentBatchService.POST_UPSERT,
                    List.of(ADDRESS), List.of(TYPE, FOLDER), List.of(), List.of(DATA, EXPECT),
                    "Declare a post's whole state at that address. `data` keys the post-type does "
                            + "not declare are refused; use post.move to change the address. "
                            + "`expect`: a digest from read?include=digest, refused if stale."),
            new OpSpec(ShAgentBatchService.POST_MOVE,
                    List.of(ADDRESS), List.of(), List.of(TO, FOLDER), List.of(EXPECT),
                    "Change a post's URL, its folder, or both, keeping its id and history. A "
                            + "published post's live URL changes immediately."),
            // SH1064 — the op the move's own report points at. The one thing a caller would
            // otherwise get backwards is which end `address` is: a Redirect has no `from` field
            // (SH385), so the post answers AT the address that has to keep working.
            //
            // Cut to two keys by the SH346 ceiling, which this op breached by 16 tokens and which
            // says re-open the surface rather than raise the number. `folder` went with it: where
            // a redirect is filed in the tree is not a decision an agent has to make, so the site
            // root is the rule and there is one less key on every session's copy of this.
            new OpSpec(ShAgentBatchService.POST_REDIRECT,
                    List.of(ADDRESS, TO), List.of(), List.of(), List.of(DATA, EXPECT),
                    "`address` is the OLD path, kept answering; `to` is where readers go. "
                            + "Published; 302 unless `data.permanent:true`."),
            new OpSpec(ShAgentBatchService.POST_PUBLISH,
                    List.of(ADDRESS), List.of(), List.of(), List.of(WHEN, CANCEL, EXPECT),
                    // SH992 paid for `cancel` and unpublish's `when` out of this sentence rather than
                    // the SH346 ceiling: the asset clause kept its decision and lost its explanation.
                    "Publish the draft at that address, or at an ISO-8601 `when`; `cancel:true` "
                            + "clears a pending one. Needs mayPublish. Takes `id:` too; an asset "
                            + "answers noop (SH462)."),
            new OpSpec(ShAgentBatchService.POST_UNPUBLISH,
                    List.of(ADDRESS), List.of(), List.of(), List.of(WHEN, CANCEL),
                    "Withdraw the published row; the draft stays. `when`, `cancel`, `id:` as "
                            + "post.publish."),
            new OpSpec(ShAgentBatchService.POST_DELETE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(EXPECT, DATA),
                    "Trash a post."),
            // SH222 — the undo `post.delete` has always promised in its own reply. Addressed by
            // `id:` because a trashed post has no reachable path; the ids come from
            // `find?trash=true`, which is the read half.
            new OpSpec(ShAgentBatchService.POST_RESTORE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Undo a trash. `id:` address from find?trash=true. No confirm."),
            // SH987 — the other undo: not "it was trashed" but "it used to say something else".
            // `revision` comes from a read — include=revisions, or state=rev:<n> to see one first.
            new OpSpec(ShAgentBatchService.POST_ROLLBACK,
                    List.of(ADDRESS, REVISION), List.of(), List.of(), List.of(EXPECT),
                    "Put a captured revision back into the draft; `revision` from include=revisions."),
            new OpSpec(ShAgentBatchService.FOLDER_RESTORE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Undo a folder trash, with its posts. `id:` address."),
            // SH648 — `site:<name>`, not `id:`. A trashed site keeps its unique name, so it is the
            // one restore addressable by the same form its delete took.
            new OpSpec(ShAgentBatchService.SITE_RESTORE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Undo a site trash, with its content. `site:` address."),
            // SH186 — the one addressless op. It acts on the instance's model rather than on a post, a
            // folder or a site, and `address` is optional only so a row can echo the site being built.
            new OpSpec(ShAgentBatchService.RENDER_PROVISION,
                    List.of(), List.of(), List.of(), List.of(ADDRESS, DATA),
                    // SH1285 trimmed this to fund folder.visibility under SH346's ceiling: the same
                    // four decisions, without the sentences around them.
                    "Create the PageLayout/Region/Theme post-types, idempotently, before a layout. "
                            + "`data.reconcile:true` adds fields older render types lack; "
                            + "`data.types` installs vocabulary types by name (Page, SectionHero); "
                            + "`data.fields` {Type:[name]} appends Text fields to an existing type."),
            // SH993 — a release: a named set of posts in one site that goes live as one unit. Read one
            // back with find?site=<site>&release=<name>.
            new OpSpec(ShAgentBatchService.RELEASE_UPSERT,
                    List.of(ADDRESS), List.of(), List.of(), List.of(DATA),
                    "Plan release:<site>/<name>, or confirm it; `data.description`."),
            new OpSpec(ShAgentBatchService.RELEASE_ADD,
                    List.of(ADDRESS, DATA), List.of(), List.of(), List.of(),
                    "Put `data.posts` in a release that has not run."),
            new OpSpec(ShAgentBatchService.RELEASE_REMOVE,
                    List.of(ADDRESS, DATA), List.of(), List.of(), List.of(),
                    "Take `data.posts` out again."),
            new OpSpec(ShAgentBatchService.RELEASE_RUN,
                    List.of(ADDRESS), List.of(), List.of(), List.of(WHEN, CANCEL),
                    "Publish the release, atomically. `when` schedules it, `cancel` calls that off."),
            new OpSpec(ShAgentBatchService.RELEASE_ARCHIVE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(CANCEL),
                    "Hide it from the listing; `cancel` restores. Not a delete."),
            new OpSpec(ShAgentBatchService.RELEASE_ROLLBACK,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Undo a published release: each page reverts; pages it first published come down."),
            // SH1003 — the channel back. `data.body` is the correction itself; `field` narrows it to
            // one field and `revision` to one version, and `kind` is comment, request, reply or
            // reviewed (SH1061's per-field mark, which a work list must not read as a request).
            new OpSpec(ShAgentBatchService.NOTE_ADD,
                    List.of(ADDRESS, DATA), List.of(), List.of(), List.of(),
                    "Say something about this page: `data.body`; also `field`, `revision`, "
                            + "`parent`, `kind` (comment/request/reply/reviewed)."),
            new OpSpec(ShAgentBatchService.NOTE_RESOLVE,
                    List.of(ADDRESS, DATA), List.of(), List.of(), List.of(CANCEL),
                    "Close `data.note`; `cancel` re-opens."),
            // SH1032 — merges, where post.upsert declares whole state: an upsert carrying only
            // `alt` would drop `FILE` and leave the asset pointing at nothing.
            new OpSpec(ShAgentBatchService.ASSET_META,
                    List.of(ADDRESS, DATA), List.of(), List.of(), List.of(),
                    "Describe an image: `data.alt`, `caption`, `tags`. Merges; blank clears."));

    private ShAgentOps() {
        // utility
    }

    /**
     * The specs, keyed by their own {@code op} and in the order written (SH197).
     *
     * <p>Two defects in one line, and they were the same defect: the key was typed beside a record
     * that already carries it — "nine literals is a typo waiting to be published as a contract", as
     * the comment above says about the argument names — and the {@code Map.of} that needed the pair
     * is what made the order a per-JVM salt's. Keying off {@code spec.op()} removes both: there is
     * one name per op, and the map iterates as this file reads.</p>
     *
     * <p>A duplicate is a programming error and fails here rather than silently keeping the last
     * one, which is the {@code Map.of} behaviour this replaces — {@code Map.of} throws on a
     * duplicate key, and losing that would be a step back.</p>
     */
    private static Map<String, OpSpec> specs(OpSpec... specs) {
        Map<String, OpSpec> byOp = new LinkedHashMap<>();
        for (OpSpec spec : specs) {
            if (byOp.put(spec.op(), spec) != null) {
                throw new IllegalStateException("Two specs for " + spec.op());
            }
        }
        return Collections.unmodifiableMap(byOp);
    }

    /** Every op, in {@link ShAgentBatchService#OPS} order — the one list of names. */
    public static List<OpSpec> all() {
        return ShAgentBatchService.OPS.stream().map(SPECS::get).filter(java.util.Objects::nonNull).toList();
    }

    /** The ops this class knows about, for the guard that says none is missing. */
    public static List<String> specified() {
        return List.copyOf(SPECS.keySet());
    }

    /**
     * The whole vocabulary as text, one op per line.
     *
     * <p>Grammar, stated once at the top so the rows need no punctuation legend: a bare name is
     * required, {@code name?} is required only when creating, {@code a|b} means at least one, and
     * {@code [name]} is optional. Every op also accepts nothing else — an unrecognised key is
     * refused (SH331), so this list is exhaustive rather than indicative.</p>
     */
    public static String terse() {
        StringBuilder text = new StringBuilder("shio-ops 1\n");
        text.append("# grammar: name=required  name?=required when creating  a|b=at least one  "
                + "[name]=optional\n");
        text.append("# every op takes `op` plus the keys below and nothing else; batch-level keys "
                + "are dryRun, confirm, verify, digest\n");
        // SH733 — said once. site.delete, post.delete and folder.delete all carry this modifier,
        // and three explanations of one key cost more than the key does: with them the vocabulary
        // went 30 tokens over its ceiling, and with this line and none of them it is under.
        text.append("# on any delete, `data.purge:true` removes instead of trashing — frees file "
                + "bytes, fires the DELETE webhook, no undo\n");
        // SH1032 — the same move SH733 made for `purge`, applied to the one modifier still spelled
        // per-op. Five ops carried "Needs `confirm` from its own dry run": the three deletes,
        // release.run and release.rollback. Five explanations of one BATCH-level key cost 49 tokens
        // where the key costs none, and the line above already listed `confirm` without ever saying
        // what it was. This is the trim that paid for asset.meta, and it is also the SH332 rule the
        // class javadoc cites — a caller learns a key once, in one shape.
        text.append("# the deletes, release.run and release.rollback need `confirm`: the token "
                + "their own dry run returned\n");
        for (OpSpec spec : all()) {
            text.append(spec.op());
            spec.required().forEach(key -> text.append(' ').append(key));
            spec.requiredWhenCreating().forEach(key -> text.append(' ').append(key).append('?'));
            if (!spec.atLeastOneOf().isEmpty()) {
                text.append(' ').append(String.join("|", spec.atLeastOneOf()));
            }
            spec.optional().forEach(key -> text.append(" [").append(key).append(']'));
            text.append(" — ").append(spec.purpose()).append('\n');
        }
        return text.toString();
    }
}
