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
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One agent run, as the review queue lists it (SH109).
 *
 * @param session    the SH106 agent session id — the unit a human approves or reverts
 * @param actor      the principal whose credential the run used. An agent acts <em>as</em>
 *                   someone, and the reviewer's first question is who
 * @param changes    how many audit entries the run made
 * @param byAction   counts per action ({@code CREATE}/{@code UPDATE}/{@code PUBLISH}/…), so a
 *                   run that only wrote drafts is distinguishable at a glance from one that
 *                   published — that difference decides whether reviewing it is urgent
 * @param firstSeen  when the run's first change landed
 * @param lastSeen   when its last one did
 * @param sites      the site ids it touched
 * @param intent     SH1000 — what this run said it was for, declared once by whoever started it.
 *                   Absent when nothing declared one, and the queue says so in those words: a
 *                   reviewer reading a count of changes over a uuid has to reconstruct the premise
 *                   from the diff, which is the one thing the agent knew and never said
 * @param agentName  SH999 — what the run called itself on {@code initialize}. Absent when it never
 *                   said, which is every session the REST surface was handed rather than minted
 * @param pending    SH615 — <b>posts</b> in this run that still hold work nobody has released:
 *                   a draft with no published twin, or one modified after its twin was published.
 *                   Not "entries with a draft row", which was the same number forever, because
 *                   publishing keeps the draft as the editable copy. And posts rather than
 *                   entries, because one page edited four times is one decision
 */
@JsonInclude(Include.NON_NULL)
public record ShReviewSession(
        String session,
        String actor,
        int changes,
        Map<String, Integer> byAction,
        Instant firstSeen,
        Instant lastSeen,
        List<String> sites,
        String intent,
        String agentName,
        int pending) {

    /** Before SH1000 gave a run a premise and SH999 gave it a name. */
    public ShReviewSession(String session, String actor, int changes, Map<String, Integer> byAction,
                           Instant firstSeen, Instant lastSeen, List<String> sites, int pending) {
        this(session, actor, changes, byAction, firstSeen, lastSeen, sites, null, null, pending);
    }
}
