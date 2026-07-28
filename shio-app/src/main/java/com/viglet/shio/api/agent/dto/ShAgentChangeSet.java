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
                               String confirm, ShAgentReport proof) {

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
        this(dryRun, ops, changed, results, null, null);
    }

    /** Every caller from before SH157 — the SH108 form, with no proof attached. */
    public ShAgentChangeSet(boolean dryRun, int ops, int changed, List<Change> results,
                            String confirm) {
        this(dryRun, ops, changed, results, confirm, null);
    }

    /**
     * The same change set carrying its own verification (SH157).
     *
     * <p>Attached by the controller after the write transaction has committed, never inside
     * it: a lint that ran in the writer's transaction would report the state a rollback is
     * about to discard, and would make a read failure able to fail a write that succeeded.</p>
     */
    public ShAgentChangeSet withProof(ShAgentReport report) {
        return new ShAgentChangeSet(dryRun, ops, changed, results, confirm, report);
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
     */
    @JsonInclude(Include.NON_NULL)
    public record Change(String op, String address, String action, String detail, String id) {

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
}
