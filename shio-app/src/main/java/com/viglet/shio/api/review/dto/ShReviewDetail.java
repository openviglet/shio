/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * What one agent run changed (SH109) — the list a human approves, reverts or ignores.
 *
 * @param session the SH106 agent session id
 * @param actor   the principal whose credential the run used
 * @param entries its changes, <b>oldest first</b>: the order the run made them is the order
 *                that reads as a story, and a reviewer following a build wants the beginning
 * @param appearance SH1000 — which of those pages this run changed the <b>look</b> of
 */
@JsonInclude(Include.NON_NULL)
public record ShReviewDetail(String session, String actor, List<Entry> entries,
                             Appearance appearance) {

    /** Before SH1000 said which pages changed their look. */
    public ShReviewDetail(String session, String actor, List<Entry> entries) {
        this(session, actor, entries, null);
    }

    /**
     * What this run changed the look of (SH1000).
     *
     * <h2>Why this is on the detail and not on the listing</h2>
     *
     * <p>An appearance digest is a <b>real render</b> (SH249), and nothing stores the one a page had
     * before: answering "did the look move" means rendering the revision that preceded the run and
     * rendering the page as it stands, two renders per page. The queue's listing is asked for on
     * every visit to the console and holds every open run, so computing it there would be that
     * multiplied by the whole queue. A detail is one run somebody opened.</p>
     *
     * <p>Capped, with the cut reported, the way {@code ShAgentProofService.digestsOf} caps its
     * digests and for its reason: an unreported cut reads as a complete answer (SH138).</p>
     *
     * @param moved   the addresses whose look changed, in address order (P5)
     * @param checked how many pages were compared — the denominator, without which {@code moved}
     *                being empty reads as "nothing moved" when it may mean "nothing was comparable"
     * @param note    what was left out, when the ceiling bit; absent otherwise
     */
    @JsonInclude(Include.NON_NULL)
    public record Appearance(List<String> moved, int checked, String note) {
    }

    /**
     * One change.
     *
     * @param action    the SH51 action — {@code CREATE}/{@code UPDATE}/{@code PUBLISH}/
     *                  {@code UNPUBLISH}/{@code DELETE}/{@code RESTORE}
     * @param postId    the post it touched, for the console's diff and edit links
     * @param address   its SH79 address, when the post still exists. Absent means the object is
     *                  gone — which is itself the answer to "what happened to it"
     * @param title     the post's title now, or the one recorded at the time if it is gone
     * @param siteId    the site
     * @param date      when it happened
     * @param draft     the post currently has a DRAFT row
     * @param published it currently has a PUBLISHED row
     * @param trashed   it is in the SH55 trash
     * @param revertable this entry has an undo the queue can actually perform. A
     *                  {@code PUBLISH} of a post since deleted does not, and offering a button
     *                  that fails is worse than not offering one
     */
    @JsonInclude(Include.NON_NULL)
    public record Entry(
            String action,
            String postId,
            String address,
            String title,
            String siteId,
            Instant date,
            boolean draft,
            boolean published,
            boolean trashed,
            boolean revertable) {
    }
}
