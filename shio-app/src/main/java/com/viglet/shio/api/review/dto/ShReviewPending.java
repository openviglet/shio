/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review.dto;

/**
 * How much agent work is waiting for a human, in the cheapest form (SH207).
 *
 * <h2>What this is for</h2>
 *
 * <p>SH104 shipped the handoff as {@code shio report}, which is right for where a handoff usually
 * lands — a PR body, written by whatever ran the agent. It left the curator who never opens a
 * terminal with no answer to "what did the agent do since I last looked". §IX.8 argued the position
 * before the content: a banner on every page is noise, and a banner only on the review page is a
 * page nobody visits unless they already suspect something. What is left is an indicator in the
 * navigation — the shape of an unread count, which a curator already knows how to ignore safely
 * when it is zero.</p>
 *
 * <h2>Why a second endpoint over {@code /sessions} rather than a field on it</h2>
 *
 * <p>{@code /sessions} answers the same question and costs far more: it scans 2000 audit rows,
 * groups them, and resolves every touched post. That is affordable on a page somebody opened
 * deliberately, and this is asked for on <em>every</em> console render. Two reads with the same
 * subject and different costs is the normal shape of a list and its badge.</p>
 *
 * @param sessions runs with at least one change still awaiting a decision — <b>the number the
 *                 badge shows</b>, because the page it opens is a list of runs, and an unread
 *                 indicator that does not count the things in the list it opens is a number
 *                 nobody can reconcile
 * @param changes  distinct posts still holding a draft across those runs. Deliberately counted by
 *                 <em>post</em> and not by audit entry: a run that edited one page four times is
 *                 one decision, and four is the number {@code ShReviewSession.pending} reports for
 *                 it. The two are answering different questions and only this one is a count of
 *                 things a human has to decide
 */
public record ShReviewPending(int sessions, int changes) {
}
