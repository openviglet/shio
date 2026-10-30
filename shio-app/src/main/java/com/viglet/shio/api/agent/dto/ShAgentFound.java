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
 * The result of an agent search (SH83's {@code find}).
 *
 * <p><b>Identifiers, not bodies.</b> The whole reason {@code find} and {@code read} are
 * two tools rather than one search-that-returns-content: a query that matched twelve
 * posts and returned twelve bodies has spent the agent's context on eleven it did not
 * want. Find narrows, read fetches — and read projects (SH123). A row here is a
 * decision aid, which is why it carries the title and nothing else quotable.</p>
 *
 * <p><b>A page is not a cut (SH146).</b> {@code truncated} says this response does not hold
 * everything that matched; {@code cursor} says what to pass as {@code after} to get the rest.
 * Present together they describe a <em>page</em> of a walk that can finish. {@code truncated}
 * without a {@code cursor} is the older, honest dead end — a result the caller cannot continue
 * past — and a caller that projects content to disk must treat the two differently, because
 * "there is more" and "you will never see the rest" have opposite safe responses: keep walking,
 * versus refuse to write a tree whose gaps the next {@code --prune} would read as deletions.</p>
 *
 * @param count     rows returned (not rows that exist — see {@code truncated})
 * @param truncated the limit cut the result set; page with {@code after=<cursor>}, or raise
 *                  {@code limit} / narrow the query
 * @param results   the matches, ordered by address so two identical queries agree (P5)
 * @param cursor    the last row's address, to pass back as {@code after}; absent when this
 *                  response is the whole result, because there is then nothing to continue
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentFound(int count, boolean truncated, List<Row> results, List<String> notes,
                           String cursor, Release release) {

    /** The five-field form, before SH993 let a listing be a release's posts. */
    public ShAgentFound(int count, boolean truncated, List<Row> results, List<String> notes,
                        String cursor) {
        this(count, truncated, results, notes, cursor, null);
    }

    /**
     * SH993 — the release a {@code release=} listing is the posts of: its address, state, note, the
     * instant it is scheduled for, and how many posts it holds in all, which a page of rows does not
     * say.
     */
    @JsonInclude(Include.NON_NULL)
    public record Release(String address, String state, String description, String scheduledAt,
                          int posts) {
    }

    /** The three-field form: nothing to note, nothing to continue. */
    public ShAgentFound(int count, boolean truncated, List<Row> results) {
        this(count, truncated, results, null, null);
    }

    /** The four-field form: notes, but a complete result. */
    public ShAgentFound(int count, boolean truncated, List<Row> results, List<String> notes) {
        this(count, truncated, results, notes, null);
    }

    /**
     * @param address the canonical {@code post:} address — how you read or write it
     * @param type    post-type name
     * @param state   {@code DRAFT} / {@code PUBLISHED} — which row this is, not whether the page is
     *                live. The walk is DRAFT-preferred, so it reads {@code DRAFT} for every post
     *                that has a draft; {@code published} is the live question (SH545)
     * @param title   the post title, so the agent can choose without a second call
     * @param id      omitted unless the caller asked for ids (SH121)
     * @param locale  present only when it is <b>not</b> the instance default (SH348) — this is the
     *                row where a caller discovers a site has translations at all, and on a
     *                monolingual instance it costs nothing because {@code NON_NULL} omits it
     * @param published {@code true} when this post has a {@code PUBLISHED} row, absent otherwise
     *                (SH545) — the same conditional shape as {@code locale} beside it, and for the
     *                measured reason recorded on {@code ShAgentDoc.published}. It costs no query
     *                either: the walk already fetches both states and {@code dedupe} was discarding
     *                the published one, so this is a fact that was in memory and being thrown away
     * @param changed SH1324 — {@code true} on a live post whose draft differs from what the site
     *                serves, absent otherwise: the third state a curator reads ("live, with changes
     *                not yet published"). Free on the same terms as {@code published}, from the two
     *                rows the walk already holds, so a {@code state=} listing does not carry it
     */
    @JsonInclude(Include.NON_NULL)
    public record Row(String address, String type, String state, String title, String id,
                      String locale, Boolean published, String publishAt, String unpublishAt,
                      Boolean changed) {

        /** The nine-field form, before SH1324. */
        public Row(String address, String type, String state, String title, String id,
                   String locale, Boolean published, String publishAt, String unpublishAt) {
            this(address, type, state, title, id, locale, published, publishAt, unpublishAt, null);
        }

        /** This row, marked as a live post with unpublished changes (SH1324). */
        public Row withChanged() {
            return new Row(address, type, state, title, id, locale, published, publishAt, unpublishAt,
                    Boolean.TRUE);
        }

        /**
         * The seven-field form, before SH991. {@code publishAt} and {@code unpublishAt} are set only
         * on a {@code scheduled=} listing, so every other row stays byte-identical to what it was.
         */
        public Row(String address, String type, String state, String title, String id,
                   String locale, Boolean published) {
            this(address, type, state, title, id, locale, published, null, null);
        }

        /** The five-field form, before SH348 made a non-default locale visible. */
        public Row(String address, String type, String state, String title, String id) {
            this(address, type, state, title, id, null, null);
        }

        /** The six-field form, before SH545 made publication readable. */
        public Row(String address, String type, String state, String title, String id,
                   String locale) {
            this(address, type, state, title, id, locale, null);
        }
    }
}
