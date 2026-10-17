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

/**
 * Given an intent, the one call (SH601).
 *
 * <h2>Inventory is not routing</h2>
 *
 * <p>The manifest is complete, and that is the uncomfortable finding: every round that lost time to
 * discovery lost it holding a correct and complete list. <b>Completeness was never the
 * constraint.</b> What a reader arrives with is an <em>intent</em> — <em>this run failed with a
 * 500</em>, <em>what conventions does this instance already have</em>, <em>is there a package that
 * already does this</em> — and what the manifest offered was 57 booleans, 28 paths and a
 * {@code limits} object. Mapping the first onto the second is the work, it was unbudgeted, and it is
 * exactly where the errors were: {@code diagnostics: true} is discoverable to a reader who already
 * suspects diagnostics exist.</p>
 *
 * <p>So P2's second half is routing. {@code startHere} listed and did not branch; this is the
 * branch.</p>
 *
 * <h2>Why the table is here and not in the benchmark that needs it</h2>
 *
 * <p>SH597's fixture is already a list of intents with a known right first move, and SH601's own
 * instruction was to build the table <b>once</b>, score against it, then route with it. A router
 * whose intents were written separately from the benchmark's would be a router scored against a
 * different set than it serves — and the first time they drifted, the score would still be green.
 * So {@code ShAgentDiscoveryCostTest} reads this, and what it measures is what an agent is told.</p>
 *
 * <h2>Not a fifth registry</h2>
 *
 * <p>SH599 went the other way for a reason, and this must not undo it. What keeps it routing rather
 * than inventory is that the rows are keyed by <b>the question a reader arrives with</b>, not by
 * capability: there is deliberately no row for a capability nobody has ever arrived asking for, and
 * adding one because it exists is how this becomes the thing it is meant to replace. The set is
 * closed, small, and every row earns its place by having been a real first move — three of the six
 * taken verbatim from rounds that got them wrong.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentIntents {

    private ShAgentIntents() {
    }

    /**
     * One intent: the question, and the single call that answers it.
     *
     * @param id   stable name, so a benchmark can score a row and a report can name one
     * @param when the question in a reader's own words — this is the key the routing matches on,
     *             and writing it as a capability name would put the mapping back on the reader
     * @param call the one call, ready to make
     */
    public record Intent(String id, String when, String call) {
    }

    /**
     * The set. Six jobs an agent actually arrives with.
     *
     * <p>Ordered by how early in a run the question tends to arrive, not alphabetically: a reader
     * scanning for their own situation stops at the first match, so the order is part of the
     * answer.</p>
     */
    public static final List<Intent> INTENTS = List.of(
            new Intent("resume-a-session",
                    "picking up where a previous session left off",
                    "GET /api/v2/agent/changes?site=<site>&format=terse"),
            new Intent("recall-conventions",
                    "before deriving a convention, check this instance already worked it out",
                    "GET /api/v2/agent/context?format=terse"),
            new Intent("reuse-a-blueprint",
                    "building a section: check something already applies it",
                    "GET /api/v2/agent/blueprints?format=terse"),
            new Intent("write-without-ids",
                    "creating or changing content without holding a UUID",
                    "POST /api/v2/agent/batch with dryRun:true, addressed post:/folder:"),
            new Intent("prove-the-work",
                    "proving what you just wrote is correct without asking a human to look",
                    "GET /api/v2/agent/verify?site=<site>&format=terse"),
            // The row the whole task is named after: a run had `diagnostics: true` in front of it
            // and grepped store/logs anyway, because a flag is not an instruction.
            new Intent("diagnose-5xx",
                    "a call answered 5xx and you need to know what failed",
                    "GET /api/v2/agent/diagnostics?format=terse"));

    /** The routing as the manifest publishes it: {@code when → call}, one line each. */
    public static List<String> lines() {
        return INTENTS.stream().map(intent -> intent.when() + " → " + intent.call()).toList();
    }
}
