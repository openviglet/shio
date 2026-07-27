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
 * @param pending    entries that can still be acted on: a change whose post is gone, or that
 *                   is already published, is history rather than a decision
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
        int pending) {
}
