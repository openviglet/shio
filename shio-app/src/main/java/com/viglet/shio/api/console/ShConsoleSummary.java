/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.console;

import java.util.List;

import com.viglet.shio.api.history.ShHistoryAPI.ShHistoryResponse;

/**
 * SH1050 — what the console's home asks for, in one answer.
 *
 * <h2>What it replaces</h2>
 *
 * <p>Home fired four requests on mount and used one property of two of them: every site and every
 * post type, transferred whole, for their <b>length</b>. There was no count endpoint, so the only
 * way to ask "how many" was to fetch everything and measure it — the shape P3 exists to refuse, on
 * the first screen a curator sees after logging in.</p>
 *
 * <h2>Counts, not rows</h2>
 *
 * <p>Every number here comes from a count query or a bounded read. Nothing loads a collection to
 * take its size, which is the whole point: an instance with four hundred sites answers this in the
 * same bytes as one with four.</p>
 *
 * <h2>Attention, not inventory</h2>
 *
 * <p>The second half is the one that matters in this product. "How much content exists" is a number
 * nobody acts on; "what is waiting for a person" is the dashboard's real question in a CMS an agent
 * drives — a review queue somebody has to look at, a publish that happens this week whether or not
 * anybody remembers, a note left open on a page. Each is a count with a page behind it.</p>
 *
 * @param sites how many sites exist
 * @param postTypes how many post types the model declares
 * @param version the instance's own version, as the footer reads it
 * @param attention what is waiting for a person
 * @param runs what the agent did recently, and what it said it was doing
 * @param diagnostics whether this instance is failing at anything
 * @param activity the most recent trail rows, so home needs no second request
 * @param checks pages failing verify's content or routes checks (SH1336)
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShConsoleSummary(long sites,
                               long postTypes,
                               String version,
                               Attention attention,
                               List<Run> runs,
                               Diagnostics diagnostics,
                               List<ShHistoryResponse> activity,
                               Checks checks) {

    /**
     * How far ahead "soon" looks — a week, the horizon somebody plans a publish inside.
     *
     * <p>On the record rather than on the controller so the context pack can use the same window
     * without depending on a controller. Two definitions of "soon" is a tile and a pack disagreeing
     * about what is due, which is the kind of thing nobody notices and nobody can explain.</p>
     */
    public static final java.time.Duration SOON = java.time.Duration.ofDays(7);

    /**
     * The four things that are waiting for somebody, each with a page that acts on it.
     *
     * @param reviewSessions agent sessions nobody has approved or reverted
     * @param reviewChanges the writes inside them — the size of the decision, not just its count
     * @param scheduledSoon publishes and unpublishes due in the next seven days
     * @param openNotes notes left on content and not resolved
     */
    public record Attention(int reviewSessions,
                            int reviewChanges,
                            long scheduledSoon,
                            long openNotes) {
    }

    /**
     * One agent run, as a dashboard names it.
     *
     * <p>The <b>intent</b> is the field that makes this a row worth drawing: a session id says an
     * agent was here, and the sentence it declared says what it was trying to do — which is what a
     * curator needs before deciding whether to read the diff. `pending` is how much of it is still
     * waiting on them, so a run with nothing pending is a run they have already dealt with.</p>
     *
     * @param session the session id, which is what the review queue addresses by
     * @param intent what the run said it was doing (SH1000), or null
     * @param agent the client that ran it (SH999), or null
     * @param changes how many writes it made
     * @param pending how many of them nobody has decided on
     * @param at when it was last seen, ISO-8601
     */
    public record Run(String session, String intent, String agent, int changes, int pending,
                      String at) {
    }

    /**
     * Whether this instance is failing at anything, from the in-memory window.
     *
     * <p>A window and not a total, and the difference has to survive into the tile: the incident
     * log is bounded and starts empty at boot, so `0` means "nothing since this process started"
     * rather than "nothing ever". `unpersisted` is the honest qualifier on a quiet feed — if the
     * database was the thing failing, the record of the failure is what could not be written.</p>
     */
    public record Diagnostics(int incidents, long unpersisted) {
    }

    /**
     * SH1336 — pages that fail a check, so a curator learns of one without opening it.
     *
     * <p>From verify's {@code content} and {@code routes} checks, which do not render: measured flat
     * at 14 statements and about 35 ms for a 150-page site ({@code ShVerifyInboxCostTest}), against 92
     * statements for the same checks scoped to ten addresses. The rendering checks ({@code page},
     * {@code a11y}) stay out of the front door.</p>
     *
     * @param errors error-level findings across every readable site
     * @param pages the first few pages they are on, one row per page
     */
    public record Checks(int errors, List<Failing> pages) {
    }

    /**
     * One page that fails a check, and the first reason why.
     *
     * @param address the page, as an SH79 address
     * @param id the post's id, which the console's editor route takes; null when it did not resolve
     * @param rule the rule that failed, as verify names it
     * @param message what is wrong, in verify's words
     */
    public record Failing(String address, String id, String rule, String message) {
    }
}
