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
 * @param activity the most recent trail rows, so home needs no second request
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShConsoleSummary(long sites,
                               long postTypes,
                               String version,
                               Attention attention,
                               List<ShHistoryResponse> activity) {

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
}
