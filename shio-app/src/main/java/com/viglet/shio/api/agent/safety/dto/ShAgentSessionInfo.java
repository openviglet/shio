/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * What this credential is and what it may do, right now (SH106).
 *
 * <h2>Why an endpoint and not a block in the manifest</h2>
 *
 * <p>The manifest describes the <em>instance</em> and is byte-stable so it caches, diffs
 * and (with SH116) answers {@code 304}. This describes the <em>caller</em>, and changes as
 * the caller spends its budget. Folding it in would make the one document an agent is
 * supposed to cache uncacheable, to save a call it makes at most once per session.</p>
 *
 * <p>Reporting the bounds at all is design law P2 on the safety surface: an agent that
 * cannot ask "may I publish?" finds out by writing a whole document and being refused at
 * the end, which is the expensive place to learn it (P6's cost, one call earlier).</p>
 *
 * @param session     the session id this request is attributed to, or {@code null} when it
 *                    carries none — a read with no session header is unattributed and
 *                    unmetered, which is a true and useful thing to be told
 * @param scope       the credential's token scope, or {@code console} for a session /
 *                    Basic-auth caller
 * @param principal   who the writes are recorded as
 * @param mayPublish  whether this credential may change what visitors see (SH107)
 * @param confirmRequired whether a destructive write needs the token from its own plan (SH108)
 * @param metered     whether the ceilings below apply to this request (SH110)
 * @param calls       agent-surface calls counted against this session so far
 * @param maxCalls    the call ceiling, {@code -1} for unlimited
 * @param writes      content writes counted so far
 * @param maxWrites   the write ceiling, {@code -1} for unlimited
 * @param sites       the site names this credential may read and write, empty for "all"
 * @param notes       what this answer cannot promise — the counters are in memory, so a
 *                    restart resets them. Said out loud for the same reason SH103 says it:
 *                    a limit believed to be enforced when it is not is worse than none
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentSessionInfo(
        String session,
        String scope,
        String principal,
        boolean mayPublish,
        boolean confirmRequired,
        boolean metered,
        int calls,
        int maxCalls,
        int writes,
        int maxWrites,
        List<String> sites,
        List<String> notes) {
}
