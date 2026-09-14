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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;

/**
 * The result of a batch (SH81) or of a plan/apply run (SH82) — one row per op, in
 * the order they were given.
 *
 * <p>The same shape answers all three verbs on purpose. {@code plan} is
 * {@code apply} with {@code applied=false}, and an agent that can read one can read
 * the others; a separate "plan document" type would be a second vocabulary for the
 * same facts (law P10). It is also what makes the plan reviewable by a human before
 * anything changes (P7).</p>
 *
 * @param dryRun  nothing was written
 * @param ops     how many operations were considered
 * @param changed how many of them are not {@code noop} — the number worth reading.
 *                A {@code changed} of zero on a plan is the drift-guard's green light,
 *                the same signal {@code shio push --check} gives for post-types (SH65)
 * @param results one row per op
 * @param confirm the SH108 confirm token, on a {@code dryRun} that would delete something —
 *                pass it back on the real call. Absent means this run destroys nothing
 * @param proof   SH157 — the SH99 lint over exactly the addresses this run touched, present
 *                when the caller asked for it with {@code verify=true}. It removes the
 *                closing turn of every write-then-check loop, and it is strictly better than
 *                a separate {@code /agent/verify} call because it is already scoped to the
 *                change instead of to the site. <b>Read its notes before attributing
 *                anything</b>: it reports the state those addresses are in <em>now</em>, and
 *                a finding on a post this run only published or moved was already there
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentChangeSet(boolean dryRun, int ops, int changed, List<Change> results,
                               String confirm, ShAgentReport proof, List<PageDigest> digests) {

    /**
     * <b>This type cannot represent a truncated change set (SH138).</b>
     *
     * <p>There is exactly one row per op, so {@code results.size()} must equal
     * {@code ops} — and a mismatch is a programming error, not a caller error, so it
     * fails loudly here rather than answering {@code 200} with a short list.</p>
     *
     * <p>Why the invariant is worth a runtime check on a DTO: a plan is the document a
     * human reads before agreeing to a deletion, and SH135 made it able to list
     * thousands of them. Every other unbounded section on this surface is capped and
     * reports the cut ({@code found} vs {@code findings}, {@code truncated}), which is
     * the correct pattern for a <em>report</em> and the wrong one for a <em>plan</em>:
     * a shortened sitemap reads as incomplete, while a shortened list of deletions
     * reads as a complete review of what is about to be destroyed. So the two options
     * are to exempt the rows from any budget or to refuse the run outright, and the
     * one thing that must be unavailable is quietly shortening the list. Making the
     * type reject it is how that stays true against a future {@code limit} parameter
     * nobody has thought of yet (§VIII.10: guard the invariant, do not resolve to be
     * careful).</p>
     */
    public ShAgentChangeSet {
        if (results == null || results.size() != ops) {
            throw new IllegalStateException("A change set carries one row per op and is never "
                    + "truncated (SH138): " + ops + " ops but "
                    + (results == null ? "null" : results.size()) + " rows");
        }
    }

    /** The four-field form: no destructive rows, so no token to hand back (SH108). */
    public ShAgentChangeSet(boolean dryRun, int ops, int changed, List<Change> results) {
        this(dryRun, ops, changed, results, null, null, null);
    }

    /** Every caller from before SH157 — the SH108 form, with no proof attached. */
    public ShAgentChangeSet(boolean dryRun, int ops, int changed, List<Change> results,
                            String confirm) {
        this(dryRun, ops, changed, results, confirm, null, null);
    }

    /**
     * The same change set carrying its own verification (SH157).
     *
     * <p>Attached by the controller after the write transaction has committed, never inside
     * it: a lint that ran in the writer's transaction would report the state a rollback is
     * about to discard, and would make a read failure able to fail a write that succeeded.</p>
     */
    public ShAgentChangeSet withProof(ShAgentReport report) {
        return new ShAgentChangeSet(dryRun, ops, changed, results, confirm, report, digests);
    }

    /**
     * SH194 — the rendered digests of the pages this run affected.
     *
     * <p>The other half of SH157's argument. That one folded the closing <em>lint</em> into the
     * write; this folds the closing <em>look</em>, which is the loop an appearance change actually
     * runs: upsert the layout, render a page, compare. One call instead of three.</p>
     */
    public ShAgentChangeSet withDigests(List<PageDigest> pageDigests) {
        return new ShAgentChangeSet(dryRun, ops, changed, results, confirm, proof,
                pageDigests == null || pageDigests.isEmpty() ? null : List.copyOf(pageDigests));
    }

    /**
     * One affected page's two hashes — and deliberately <b>only</b> the hashes.
     *
     * <p>A full SH101 digest per page would be the expensive way to answer a cheap question. What
     * a write's caller is asking is "did this move", which is a comparison against the value it
     * already holds, so the outline, the links and the images are all payload it would discard.
     * The two hashes separately, because SH187 made them separate on purpose: a restyle moves
     * {@code appearanceDigest} alone and a structural break moves {@code digest}.</p>
     *
     * <h3>The one exception, and why it is not a comparison (SH301)</h3>
     *
     * <p>{@code markers} rides along, and it is the only field here that is not a hash. The rule above
     * holds for everything a caller can diff against a value it already has; a <b>marker count is an
     * assertion about the page now</b> — "this page is rendering a slot with no Region" — and the
     * caller that most wants it is exactly this one: somebody who has just rewritten a shared region
     * and is being told which pages it reached. Without it, the honest next step is a second call per
     * page, which is the round trip SH194 exists to remove.</p>
     *
     * <p><b>Present only when it is non-zero.</b> A clean run stays byte-identical to the pre-SH301
     * response, which matters because since SH266 the caller names the page count — a field on every
     * row would multiply whatever it asked for. It also costs no extra work: the digest behind each
     * row is a real render and has carried the count since SH299; this stops throwing it away.</p>
     *
     * @param address          the page's SH79 address
     * @param source           {@code rendered}, or {@code content} for a page that cannot be
     *                         rendered (SH249) — so a caller knows which question was answered
     * @param digest           the structural hash ({@code d:…})
     * @param appearanceDigest the appearance hash ({@code a:…}), absent on a fallback
     * @param markers          how many defect markers the rendered page carries, absent when none
     */
    @JsonInclude(Include.NON_NULL)
    public record PageDigest(String address, String source, String digest,
                             String appearanceDigest, ShAgentDigest.Markers markers) {

        /** The pre-SH301 form: no marker count, which is what a cut row and a fallback carry. */
        public PageDigest(String address, String source, String digest, String appearanceDigest) {
            this(address, source, digest, appearanceDigest, null);
        }
    }

    /**
     * What one op did, or would do.
     *
     * @param op      the requested operation, echoed so a result row stands alone
     * @param address the canonical address it acted on. For a {@code move} this is the
     *                <b>destination</b>, because that is where the post is now and where
     *                the next op has to address it
     * @param action  {@code create} · {@code update} · {@code move} · {@code publish} ·
     *                {@code unpublish} · {@code delete} · {@code noop}. An unchanged
     *                object reports {@code noop} rather than being omitted: "I looked
     *                and there was nothing to do" is an answer, and omitting it would
     *                make the agent ask again
     * @param detail  a short human/agent-readable note, only when it adds something the
     *                action does not already say (e.g. which parent folders were created)
     * @param id      the object id — omitted unless the caller asked for ids (SH121)
     * @param inboundLinks SH140 — the published pages whose authored markup still links to the URL
     *                this op moved <em>away</em> from, as SH79 addresses. Present on a
     *                {@code move} that changed a live URL and found some, absent otherwise: a
     *                move breaks pages nowhere near the one that moved, and nothing else on this
     *                surface can tell the caller which. It cannot be asked later — once the post
     *                has moved, the old path is indistinguishable from a route the front end owns,
     *                which is why {@code verify}'s {@code link-unresolved} is only a warning
     */
    @JsonInclude(Include.NON_NULL)
    public record Change(String op, String address, String action, String detail, String id,
                         InboundLinks inboundLinks, String curatorUrl) {

        /** Every caller from before SH981 attached the console's own link. */
        public Change(String op, String address, String action, String detail, String id,
                      InboundLinks inboundLinks) {
            this(op, address, action, detail, id, inboundLinks, null);
        }

        /** Every caller from before SH140: a change that breaks no inbound link. */
        public Change(String op, String address, String action, String detail, String id) {
            this(op, address, action, detail, id, null, null);
        }

        /**
         * The same row with the console's page for what it just wrote (SH981).
         *
         * <p>An agent's closing move was a sentence — the batch's own detail said "change it in the
         * console" and named no URL. Attached whatever {@code ids} says, because that flag is about
         * whether the caller wants UUIDs and this is a link a person opens.</p>
         */
        public Change withCuratorUrl(String url) {
            return url == null ? this
                    : new Change(op, address, action, detail, id, inboundLinks, url);
        }

        /** The no-change outcome, which is the common one on a second run (P5). */
        public static final String NOOP = "noop";
        public static final String CREATE = "create";
        public static final String UPDATE = "update";
        public static final String MOVE = "move";
        public static final String PUBLISH = "publish";
        public static final String UNPUBLISH = "unpublish";
        public static final String DELETE = "delete";

        public boolean isChange() {
            return !NOOP.equals(action);
        }
    }

    /**
     * The pages a move just broke (SH140).
     *
     * <p><b>Addresses and not a count.</b> The cheap version of this feature is "N posts mention
     * the old URL", and a number is the one answer the caller cannot act on: fixing them needs to
     * know which. So the list is the payload, `found` exists only so a cap can never read as the
     * whole answer (SH138), and the whole object is absent when nothing links to the old URL —
     * absent beats empty (SH190), and a clean move stays byte-identical to its pre-SH140
     * response.</p>
     *
     * <p>Its own type rather than a bare list on the row, because a bare list would have to choose
     * between being capped-and-silent or uncapped-and-unbounded; and rather than a
     * {@code ShAgentReport} finding, because a finding claims the *linking* post is defective and
     * it is not — it was correct until this op ran, and the fix belongs to the caller of the move.
     * </p>
     *
     * @param from      the URL the post moved away from — the string these pages still contain
     * @param addresses the linking pages, sorted, capped
     * @param found     how many there are in total
     * @param fix       what to do about it, in the P6 shape the rest of the surface uses
     */
    @JsonInclude(Include.NON_NULL)
    public record InboundLinks(String from, List<String> addresses, int found, String fix) {
    }
}
