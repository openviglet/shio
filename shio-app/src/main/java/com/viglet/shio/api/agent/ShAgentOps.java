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
                    "Create a blank site, or fill an empty url/description/furl on one that "
                            + "exists. `data.postTypeLayout` binds a post-type to a PageLayout "
                            + "title, per key. `data.scratch:true` marks the new site DISPOSABLE, "
                            + "on the CREATE only. `data.reindex:true` back-fills the search "
                            + "index, paged by `data.limit`/`data.from`. Never replaces a value "
                            + "somebody set."),
            new OpSpec(ShAgentBatchService.SITE_DELETE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Remove a site and every folder and post in it. Needs `confirm` from its "
                    + "own dry run, and unlike folder.delete it is PURGED rather than trashed "
                    + "- there is no undo (SH463)."),
            new OpSpec(ShAgentBatchService.FOLDER_UPSERT,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Create a folder at that whole name chain, or confirm it is there."),
            new OpSpec(ShAgentBatchService.FOLDER_MOVE,
                    List.of(ADDRESS, TO), List.of(), List.of(), List.of(),
                    "Rename or relocate a section. `to` is the whole new name chain, so its last "
                            + "segment is the new name. No post's URL changes."),
            new OpSpec(ShAgentBatchService.FOLDER_DELETE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Trash a folder and everything beneath it. Needs `confirm` from its own dry "
                            + "run; use folder.move to rename."),
            new OpSpec(ShAgentBatchService.POST_UPSERT,
                    List.of(ADDRESS), List.of(TYPE, FOLDER), List.of(), List.of(DATA),
                    "Declare a post's whole state at that address. `data` keys the post-type does "
                            + "not declare are refused; use post.move to change the address."),
            new OpSpec(ShAgentBatchService.POST_MOVE,
                    List.of(ADDRESS), List.of(), List.of(TO, FOLDER), List.of(),
                    "Change a post's URL, its folder, or both, keeping its id and history. A "
                            + "published post's live URL changes immediately."),
            new OpSpec(ShAgentBatchService.POST_PUBLISH,
                    List.of(ADDRESS), List.of(), List.of(), List.of(WHEN),
                    "Publish the draft at that address, or schedule it with an ISO-8601 `when` "
                            + "(SH342). Needs a credential with mayPublish. Takes `id:` too, and "
                            + "only here: an asset has no URL to be addressed by, and an "
                            + "uploaded one is already published, so this answers noop rather "
                            + "than failing the batch (SH462)."),
            new OpSpec(ShAgentBatchService.POST_UNPUBLISH,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Withdraw the published row; the draft stays. Takes `id:` on the same "
                            + "terms as post.publish."),
            new OpSpec(ShAgentBatchService.POST_DELETE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Trash a post. Needs `confirm` from its own dry run."),
            // SH186 — the one addressless op. It acts on the instance's model rather than on a post, a
            // folder or a site, and `address` is optional only so a row can echo the site being built.
            new OpSpec(ShAgentBatchService.RENDER_PROVISION,
                    List.of(), List.of(), List.of(), List.of(ADDRESS, DATA),
                    "Create the PageLayout/Region/Theme post-types this instance renders with, "
                            + "idempotently. Do this once before writing a layout: post.upsert "
                            + "cannot create a system type. `data.reconcile:true` also adds an "
                            + "older instance's render types are missing. `data.types` installs "
                            + "SH95 vocabulary types by name (Page, SectionHero) - a bare "
                            + "instance ships none. Takes no address."));

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
