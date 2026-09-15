/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One agent run, as a caller reads it (SH1000).
 *
 * <h2>Why this is its own read and not a field on the change feed</h2>
 *
 * <p>A run's premise is one sentence about many rows. Carried on the change row it would be that
 * sentence repeated forty times on a forty-page run, which is P3's definition of waste — and the
 * feed is the surface a session polls, so the repetition would be paid on every poll. Read once,
 * by the ids the feed already names in {@code agent}.</p>
 *
 * <p>It is not a restatement of the review queue either (P10). The queue answers "what is waiting
 * for a person", scoped to what a console session may approve; this answers "who was that, and what
 * were they doing", for a caller composing a handoff out of a feed it has already read.</p>
 *
 * @param session     the id the transport minted or accepted
 * @param clientName  what the run called itself; absent when it never said
 * @param clientVersion its version, on the same terms
 * @param transport   {@code mcp} or {@code rest}
 * @param actor       the human whose credential it used
 * @param intent      what it said it was for; absent when nothing declared one, which reads as the
 *                    absence it is rather than as an empty sentence
 * @param startedAt   when the run introduced itself
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentRunRow(String session, String clientName, String clientVersion,
                            String transport, String actor, String intent, Instant startedAt) {
}
