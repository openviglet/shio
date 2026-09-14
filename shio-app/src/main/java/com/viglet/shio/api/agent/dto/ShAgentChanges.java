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

/**
 * What has changed, resumably (SH88).
 *
 * <h2>Why a cursor and not a bigger window</h2>
 *
 * <p>SH83 shipped {@code shio_changes} as a <em>window</em> — the last N transitions —
 * and said so, because a window answers "did anything happen?" but not "what happened
 * since I last looked". The second question is the one that makes session N+1 cheaper
 * than session N (§XIII) and the one {@code shio pull --content} has to answer before it
 * can bring a human's console edits back into the file tree. A window cannot answer it:
 * the caller would have to guess a size, and guessing low silently loses edits.</p>
 *
 * <h2>The two modes, and why they share an order</h2>
 *
 * <p>Without {@code since} this is still a window — the most recent {@code limit}
 * entries — and {@code window} says so. With {@code since} it is a feed: everything
 * after that position, oldest first, with {@code more} set when the page ended early.
 * <b>Both are ordered oldest → newest</b> so the cursor is always the last row's
 * position and a caller never has to know which mode it got to advance correctly.</p>
 *
 * <h2>Why the cursor is a pair and not a timestamp</h2>
 *
 * <p>Audit rows share timestamps — a batch of twelve writes commits inside one second,
 * and on a MariaDB with second-precision columns inside one *value*. A timestamp cursor
 * therefore either re-delivers a whole second's worth of entries or drops the ones that
 * tie, and the second failure is invisible. The cursor is {@code <instant>|<id>} and the
 * feed's ordering is {@code (date, id)}, so every entry has exactly one position.</p>
 *
 * @param found   entries in this response
 * @param window  true when no {@code since} was given, i.e. this is a starting position
 *                rather than a delta
 * @param since   the cursor this response continued from, echoed for traceability
 * @param cursor  the position to pass as {@code since} next time — always present, even
 *                when nothing changed, so a caller can hold a position on a quiet instance
 * @param more    entries remain after {@code cursor} right now; call again immediately
 * @param changes the entries themselves, oldest first
 * @param incidents asynchronous failures in the same range (SH144) — omitted when there
 *                are none, so a healthy instance pays nothing for the field. See the
 *                section below on why they share this response and this cursor
 * @param notes   what this view holds back and why — the lag rule, the scope, and
 *                whether any row's object is gone. Stated rather than implied, for the
 *                same reason SH103's window states its bound
 * @param budget  SH723 — what this page cost and what it was allowed to cost. Absent only
 *                on the pre-SH723 constructors kept for older callers
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentChanges(int found, boolean window, String since, String cursor,
                             boolean more, List<Change> changes, List<Incident> incidents,
                             List<String> notes, Budget budget) {

    /** Before SH144 folded incidents in — kept so existing callers read unchanged. */
    public ShAgentChanges(int found, boolean window, String since, String cursor, boolean more,
                          List<Change> changes, List<String> notes) {
        this(found, window, since, cursor, more, changes, null, notes, null);
    }

    /** Before SH723 gave the feed a token ceiling. */
    public ShAgentChanges(int found, boolean window, String since, String cursor, boolean more,
                          List<Change> changes, List<Incident> incidents, List<String> notes) {
        this(found, window, since, cursor, more, changes, incidents, notes, null);
    }

    /**
     * What this page cost, and what it was allowed to cost (SH723).
     *
     * <h2>Why the feed needed one and a row cap was not one</h2>
     *
     * <p>{@code limit} bounds <em>rows</em>, and a row's cost is not a constant a caller can
     * reason about: SH714 measured a change row at 38–44 estimated tokens against a
     * {@code find} row's 20–23, so the documented maximum of 500 returned about 20,000
     * estimated tokens in one response — the largest payload on the surface, and the only one
     * with no ceiling. P3 says tokens are a measured budget; this is that measurement, and the
     * ceiling it is measured against.</p>
     *
     * <h2>Which end is cut, and why it differs by mode</h2>
     *
     * <p><b>With {@code since}</b> the response is a delta, oldest first, so the budget cuts
     * the <em>newest</em> rows: {@code cursor} stops at the last row that fit and {@code more}
     * is true, which is exactly the resumable shape SH88 already built. Nothing is lost.</p>
     *
     * <p><b>Without {@code since}</b> the response is a window — "the most recent N" — so the
     * budget cuts the <em>oldest</em> rows instead. The window narrows, which is what a window
     * is for; {@code cursor} still marks the true edge of the feed, so the next call with
     * {@code since=} is unaffected.</p>
     *
     * <p>Either way {@code truncated} says it happened and a note says so in prose, because a
     * cut that is not visible reads as a clean answer (the SH138 rule).</p>
     *
     * @param est       estimated tokens in this response, the 4-chars-per-token heuristic
     *                  {@link com.viglet.shio.api.agent.ShTokenBudget} uses everywhere, measured
     *                  over the terse rendering — the one the MCP wire actually pays
     * @param limit     the ceiling in force; {@code 0} means the caller asked for none
     * @param truncated rows were held back to stay inside {@code limit}
     */
    @JsonInclude(Include.NON_NULL)
    public record Budget(int est, int limit, boolean truncated) {
    }

    /**
     * One asynchronous failure, positioned in the same stream as the changes (SH144).
     *
     * <h2>Why this rides the change feed instead of having its own cursor</h2>
     *
     * <p>"Was anything failing while I was away" and "what changed while I was away" are one
     * question asked twice, and the answer to both is a position in time. A second feed would
     * mean a second cursor for a caller to hold, a second retention story and two chances to
     * resume from the wrong place — so the incident stream is merged into this one, ordered by
     * the same {@code (instant, id)} rule, and one {@code since=} advances past both.</p>
     *
     * <h2>Why it is not spelled as a {@code Change}</h2>
     *
     * <p>It would have been cheaper to emit incidents as changes with an invented
     * {@code action}, and it would have been wrong: every existing caller branches on
     * {@code action} and would silently treat a failure as a content transition — pulling it
     * into a file tree, or counting it as work done. A separate array is a field a caller can
     * ignore; a fake action is a field a caller can misread.</p>
     *
     * <p><b>A recurring incident is re-delivered.</b> Its position is its last occurrence, so a
     * failure that is still happening moves ahead of any cursor that passed it and appears
     * again with a higher {@code count}. That is the honest answer to "is this still broken",
     * and the alternative — a stable position at first occurrence — would report an ongoing
     * outage exactly once, ever.</p>
     *
     * @param kind    {@code api-error}, {@code webhook}, {@code transform}, {@code schedule}
     * @param subject what it happened to — a URL, a post id, a request path
     * @param message the cause, in its shortest identifying form
     * @param fix     what to do about it, when there is anything generic to say (P6)
     * @param count   occurrences folded into this row
     * @param at      ISO-8601 most recent occurrence — also this row's feed position
     * @param first   ISO-8601 first occurrence, so an old failure is distinguishable from a
     *                new one that happens to have been seen just now
     * @param frames  SH190 — where in Shio it threw, innermost first; absent when there is no
     *                exception behind the incident
     * @param site    SH204 — the site this failure belongs to, so {@code site=} narrows this array
     *                the way it narrows {@code changes}. <b>Absent means instance-level</b>, not
     *                "unknown": {@code api-error} belongs to a request and {@code transform} to a
     *                file several sites may reference, and those two are the only kinds that can
     *                arrive without it. A row with no site reaches an unrestricted read only —
     *                the audit stream's rule, which the two must share because one cursor walks
     *                both
     * @param retry   SH969 — the call that attempts the failed transition again, present only on a
     *                {@code schedule} incident whose post still exists
     */
    @JsonInclude(Include.NON_NULL)
    public record Incident(String kind, String subject, String message, String fix, int count,
                           String at, String first, String frames, String site, Retry retry) {
    }

    /**
     * What to send to attempt a failed scheduled transition now (SH969).
     *
     * <p>A batch op rather than a retry endpoint: {@code {"op": "post.publish", "address": …}} is
     * exactly one entry of {@code POST /api/v2/agent/batch}, and the address is what
     * {@code shio_publish} and {@code shio publish} take. So retrying is the publish a caller already
     * knows how to make, with the same ACL and the same gate, and there is no second way to publish
     * to keep in step with the first (P10). A publish at or after the scheduled instant also clears
     * the schedule, so the sweep does not fire the same transition a minute later.</p>
     *
     * <p>Structured rather than left to {@code fix}, because the console builds a button from it and
     * a button built from prose is a parser of prose.</p>
     *
     * @param op      {@code post.publish} or {@code post.unpublish}
     * @param address the post's address as it reads now, which may differ from when the failure
     *                was recorded
     */
    @JsonInclude(Include.NON_NULL)
    public record Retry(String op, String address) {
    }

    /**
     * One content transition.
     *
     * <h2>Why {@code subject} is absent on a post row (SH196)</h2>
     *
     * <p>A site's <em>shape</em> changes as well as its contents: a section can be created,
     * renamed, moved or deleted, and a feed that reported only posts told a resuming session that
     * a page now lives at {@code folder:Blog/archive/2025} without ever saying the section had
     * appeared. Those changes now arrive here, carrying the same actions a post does — a rename is
     * an {@code UPDATE}, with the previous name in {@code what}.</p>
     *
     * <p>{@code subject} is emitted only when it is {@code folder}, so every existing post row is
     * byte-identical to what it was before (P3: a field that is always there is a field every row
     * pays for). Absent means {@code post}. It is not merely a shorter spelling of the
     * {@code address} prefix: an entry whose object is gone has no address, and that is the normal
     * state of a {@code DELETE} — the one row where knowing what was deleted matters most.</p>
     *
     * @param action  {@code CREATE} / {@code UPDATE} / {@code PUBLISH} / {@code UNPUBLISH}
     *                / {@code DELETE} / {@code RESTORE}
     * @param subject {@code folder} when this row is about a section; absent for a post (SH196)
     * @param address the SH79 {@code post:} or {@code folder:} address, so the next call can be a
     *                read — absent when the object no longer exists, which is the normal state
     *                of a {@code DELETE} row and the reason {@code what} carries the title
     * @param at      ISO-8601 instant of the transition
     * @param by      who did it. SH75 stamps the agent session into this string, so a
     *                caller can tell its own writes from a human's
     * @param what    the human-readable description recorded at the time
     * @param site    the site's name, for a feed that spans more than one
     * @param id      the object id — omitted unless the caller asked for ids (SH121)
     * @param affects SH193 — the blast radius, present only when the changed object is a
     *                {@code PageLayout} or a {@code Region}. Absent everywhere else, which is
     *                what keeps an ordinary feed byte-identical: a field that is always there is
     *                a field every row pays for, and this one answers a question only two
     *                post-types can raise
     * @param submission SH970 — present only on the {@code CREATE} a site's form wrote, so an
     *                arrival is told apart from a post someone made. Absent on every other row, for
     *                the reason {@code affects} is
     * @param fields  SH989 — the names of the fields the write changed, only with
     *                {@code include=fields}. {@code []} means the row recorded that nothing changed;
     *                absent means it recorded nothing, which is every create and delete and every row
     *                older than the column. Opt-in because the default row's bytes are the P3 claim:
     *                a list a reviewer reads once is not a list every poll pays for
     */
    @JsonInclude(Include.NON_NULL)
    public record Change(String action, String subject, String address, String at, String by,
                         String what, String site, String id, Affects affects,
                         Submission submission, List<String> fields) {

        /** Before SH989 named the changed fields. */
        public Change(String action, String subject, String address, String at, String by,
                      String what, String site, String id, Affects affects, Submission submission) {
            this(action, subject, address, at, by, what, site, id, affects, submission, null);
        }
    }

    /**
     * A form submission's arrival, as a report's caption needs it (SH970).
     *
     * <p>A submission is a post (SH633), so its arrival was already on the feed as a {@code CREATE}
     * by {@code form-token:…}, and nothing said so except an actor string a reader would have had to
     * know how to parse. This is that fact, stated, with the two fields a caption is built from: the
     * ones the post-type marks as its title and its summary, read from the post as it is now.</p>
     *
     * @param title   the post's title field
     * @param summary the post's summary field — absent when the type marks none or it is empty
     */
    @JsonInclude(Include.NON_NULL)
    public record Submission(String title, String summary) {
    }

    /**
     * What a template change reaches (SH193).
     *
     * <p>On the change feed rather than on a read of its own, which is the design's own
     * suggestion and buys two things: it arrives at the moment it matters — an agent has just
     * edited a shared region and the next question is always "what did I just touch" — and it
     * needs no twelfth MCP tool, so {@code shio_changes} carries it for free. The pre-edit form
     * of the question is a separate read and is SH262.</p>
     *
     * @param kind      {@code region}, {@code layout} or {@code theme} (SH286)
     * @param layouts   the layouts involved — a region's slotters, or the layout itself
     * @param postTypes the post-types whose pages render through them
     * @param pages     draft pages of those types in this site
     * @param published published pages — the number that decides whether the edit was safe to
     *                  make now, which is why it is separate rather than a total
     */
    @JsonInclude(Include.NON_NULL)
    public record Affects(String kind, List<String> layouts, List<String> postTypes,
                          int pages, int published) {
    }
}
