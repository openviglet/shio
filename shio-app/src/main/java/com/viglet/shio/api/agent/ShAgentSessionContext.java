/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The agent session id for the current request (SH75, generalized by SH106).
 *
 * <h2>Why this exists in the host and not later</h2>
 *
 * <p>Every write an agent makes has to be attributable to <em>which</em> agent session
 * made it, because that is the only thing that makes SH109's review queue ("the agent
 * proposed N changes") expressible at all. Attribution cannot be retrofitted: rows
 * written before the id existed simply do not have one. So the id is minted by the
 * transport, on the first {@code initialize}, and stamped into the SH51 audit actor
 * from then on.</p>
 *
 * <p><b>SH106 gave it the column SH75 deferred.</b> {@code ShHistory.agent_session_id}
 * now carries it, so the review queue can select one run's changes with a query instead of
 * a {@code LIKE} over an actor string, and {@code owner} went back to naming only the human
 * whose credential was used. One fact per field: an agent acts <em>as</em> someone, and both
 * halves of that are worth recording separately.</p>
 *
 * <p><b>It is no longer MCP's.</b> The REST agent surface writes too — {@code /agent/batch},
 * {@code /agent/apply}, {@code shio apply} — and a review queue that could only see MCP's
 * changes would be a review queue with a hole in it exactly where the CLI is. Both transports
 * set it: MCP mints one on {@code initialize}, and {@code ShAgentSessionFilter} accepts one on
 * the header for every other agent request.</p>
 *
 * <p>A {@link ThreadLocal}, matching {@code ShTenantContext} — the request thread is
 * the unit of work, and whatever sets it always clears it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentSessionContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ShAgentSessionContext() {
        // holder
    }

    public static void set(String sessionId) {
        CURRENT.set(sessionId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static Optional<String> current() {
        String value = CURRENT.get();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /**
     * The actor string to record in the SH51 audit trail: the human whose credential was
     * used, and nothing else.
     *
     * <p>It used to append {@code (agent:7f3c…)} because there was nowhere else to put the
     * session id. There is now — {@code ShHistory.agent_session_id}, stamped by
     * {@link com.viglet.shio.api.history.ShContentAuditService} from this same context — so
     * the two facts are two fields. Rows written before SH106 keep the suffix; they predate
     * the column and nothing can honestly backfill them.</p>
     *
     * @param principal the authenticated principal name, possibly {@code null}
     */
    public static String actor(String principal) {
        return principal == null || principal.isBlank() ? "agent" : principal;
    }

    /**
     * SH996 — the session a <b>console</b> publish belongs to: {@code console:<curator>:<date>}.
     *
     * <h2>Why a console publish is in the agent's queue at all</h2>
     *
     * <p>The review queue is where a change is looked at and, if it was wrong, reverted — and until
     * now only an agent's work could be. A curator who published the wrong draft had the same need
     * and no screen for it, which made "the agent's work is reviewable and mine is not" a property of
     * the product rather than a decision anybody made.</p>
     *
     * <h2>Why the day, and not the click</h2>
     *
     * <p>One session per press would make the queue a log, which is what the Activity page already
     * is. One session per curator forever would never close. The day is the unit a person actually
     * asks about — "put back what I published this morning" — and it is the only grouping that needs
     * no new state: the id is derived, so two publishes an hour apart join without anything storing
     * that they belong together.</p>
     */
    public static String consoleSession(String principal, java.time.Instant when) {
        String who = principal == null || principal.isBlank() ? "console" : principal;
        return "console:" + who + ":" + java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                .withZone(java.time.ZoneOffset.UTC).format(when);
    }

    /** Whether this session id is a curator's own publishing rather than an agent run (SH996). */
    public static boolean isConsole(String session) {
        return session != null && session.startsWith("console:");
    }

    /** {@link #actor(String)} for the authenticated principal of the current request. */
    public static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return actor(authentication == null ? null : authentication.getName());
    }
}
