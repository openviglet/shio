/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.revision;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * One revision, as a caller reads it (SH987).
 *
 * <h2>Why the list carries no bodies</h2>
 *
 * <p>A revisions list is a navigation surface: a caller reads it to choose which revision to fetch,
 * compare or roll back to. Fifty revisions of a page with a long body is the whole page fifty times
 * for a question answered by dates and field names — P3 applied to the shape rather than to the
 * encoding. So {@code data} is {@code null} in a listing and present when one revision is asked
 * for by number.</p>
 *
 * <p>{@code changed} is what this revision altered relative to the one before it, by field name and
 * never by value. It is the half a caller actually reads: "title, body" says whether a revision is
 * worth fetching, and a diff of the values is what {@code shio diff} is for.</p>
 *
 * @param version   the monotonic number, which is what every other verb takes
 * @param state     {@code DRAFT} or {@code PUBLISHED} — what the captured row was
 * @param reason    {@code PUBLISH}, {@code SAVE} or {@code ROLLBACK}
 * @param actor     who, or {@code null} for the scheduler
 * @param agentSession the agent session that wrote it, when one did (SH106)
 * @param capturedAt when
 * @param title     the title as it was, so a list reads without a second call
 * @param note      what a person said about this moment, when one did — today, the reason a gate
 *                  was waived (SH998); {@code null} on every revision nobody overrode anything for
 * @param digest    what this content would digest as in the post's present place — see
 *                  {@link ShPostRevisionService#digestOf} for what that sentence is doing there
 * @param changed   field names this revision altered, against the revision before it
 * @param data      the document, only when one revision is asked for by number
 */
public record ShRevisionRow(
        int version,
        String state,
        String reason,
        String actor,
        String agentSession,
        Instant capturedAt,
        String title,
        // On the component alone, not the record: `data` is deliberately a written null in a
        // listing (SH987 — "no body here" said out loud), and a note is absent on every revision
        // nobody overrode anything for, which is almost all of them. Fifty rows paying for fifty
        // `"note":null` is P3's cost for a fact that is the exception.
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String note,
        String digest,
        List<String> changed,
        JsonNode data) {

    /** The same row without its body, which is what a listing answers with. */
    public ShRevisionRow terse() {
        return new ShRevisionRow(version, state, reason, actor, agentSession, capturedAt, title,
                note, digest, changed, null);
    }
}
