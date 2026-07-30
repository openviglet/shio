/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.List;
import java.util.Map;

import com.viglet.shio.utils.ShStableMap;

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
 * <h2>Known gap: this is MCP-only today</h2>
 *
 * <p>{@code shio://ops} is a resource, so a caller with no MCP client cannot read it — the inverse of
 * SH166, and a P1 shape this repository normally refuses. It is recorded as <b>SH346</b> rather than
 * papered over: the rendering is a plain string and any REST surface can carry it, but which one is a
 * real decision (a manifest section costs the SH113 budget on a document every session reads, while a
 * context-pack section costs the SH116 digest), and guessing it here would be the kind of unmeasured
 * choice §"measure before you fix" exists to prevent.</p>
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

    private static final Map<String, OpSpec> SPECS = ShStableMap.copyOf(Map.of(
            ShAgentBatchService.SITE_UPSERT, new OpSpec(ShAgentBatchService.SITE_UPSERT,
                    List.of(ADDRESS), List.of(), List.of(), List.of(DATA),
                    "Create a blank site, or fill an empty url/description/furl on one that "
                            + "exists. Never replaces a value somebody set."),
            ShAgentBatchService.FOLDER_UPSERT, new OpSpec(ShAgentBatchService.FOLDER_UPSERT,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Create a folder at that whole name chain, or confirm it is there."),
            ShAgentBatchService.FOLDER_MOVE, new OpSpec(ShAgentBatchService.FOLDER_MOVE,
                    List.of(ADDRESS, TO), List.of(), List.of(), List.of(),
                    "Rename or relocate a section. `to` is the whole new name chain, so its last "
                            + "segment is the new name. No post's URL changes."),
            ShAgentBatchService.FOLDER_DELETE, new OpSpec(ShAgentBatchService.FOLDER_DELETE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Trash a folder and everything beneath it. Needs `confirm` from its own dry "
                            + "run; use folder.move to rename."),
            ShAgentBatchService.POST_UPSERT, new OpSpec(ShAgentBatchService.POST_UPSERT,
                    List.of(ADDRESS), List.of(TYPE, FOLDER), List.of(), List.of(DATA),
                    "Declare a post's whole state at that address. `data` keys the post-type does "
                            + "not declare are refused; use post.move to change the address."),
            ShAgentBatchService.POST_MOVE, new OpSpec(ShAgentBatchService.POST_MOVE,
                    List.of(ADDRESS), List.of(), List.of(TO, FOLDER), List.of(),
                    "Change a post's URL, its folder, or both, keeping its id and history. A "
                            + "published post's live URL changes immediately."),
            ShAgentBatchService.POST_PUBLISH, new OpSpec(ShAgentBatchService.POST_PUBLISH,
                    List.of(ADDRESS), List.of(), List.of(), List.of(WHEN),
                    "Publish the draft at that address, or schedule it with an ISO-8601 `when` "
                            + "(SH342). Needs a credential with mayPublish."),
            ShAgentBatchService.POST_UNPUBLISH, new OpSpec(ShAgentBatchService.POST_UNPUBLISH,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Withdraw the published row; the draft stays."),
            ShAgentBatchService.POST_DELETE, new OpSpec(ShAgentBatchService.POST_DELETE,
                    List.of(ADDRESS), List.of(), List.of(), List.of(),
                    "Trash a post. Needs `confirm` from its own dry run."),
            // SH186 — the one addressless op. It acts on the instance's model rather than on a post, a
            // folder or a site, and `address` is optional only so a row can echo the site being built.
            ShAgentBatchService.RENDER_PROVISION, new OpSpec(ShAgentBatchService.RENDER_PROVISION,
                    List.of(), List.of(), List.of(), List.of(ADDRESS),
                    "Create the PageLayout/Region/Theme post-types this instance renders with, "
                            + "idempotently. Do this once before writing a layout: post.upsert "
                            + "cannot create a system type, and until they exist /preview has "
                            + "nothing to compose. Takes no address.")));

    private ShAgentOps() {
        // utility
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
