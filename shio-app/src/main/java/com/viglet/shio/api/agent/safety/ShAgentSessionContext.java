/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The agent session id for the current request (SH106).
 *
 * <h2>What SH75 started and this finishes</h2>
 *
 * <p>SH75 minted a session id on the MCP transport and stamped it into the SH51 audit
 * <em>actor string</em>, deliberately as a placeholder: attribution cannot be retrofitted
 * — rows written before the id existed simply do not have one — but a column belongs to
 * the task that owns the credential model, so the schema moves once. That task is this
 * one. Two things change here:</p>
 *
 * <ul>
 *   <li>The id is no longer MCP's. Every write on {@code /api/v2/agent/**} can carry one
 *       too, supplied by the caller in {@code X-Shio-Agent-Session} or minted per request
 *       ({@link ShAgentSessionFilter}). A REST agent that could not be reviewed while an
 *       MCP one could would make the review queue depend on the transport rather than on
 *       who was writing.</li>
 *   <li>It travels alongside the credential's {@link ShAgentGrant}, because every
 *       enforcement point in Block L needs both: <em>who</em> for attribution and
 *       <em>what may they do</em> for the gate.</li>
 * </ul>
 *
 * <p>A {@link ThreadLocal}, matching {@code ShTenantContext} and the {@code ShMcpSessionContext}
 * it replaces — the request thread is the unit of work, and the filter that sets it always
 * clears it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentSessionContext {

    /** The header a caller uses to keep one session across many agent requests. */
    public static final String SESSION_HEADER = "X-Shio-Agent-Session";

    private static final ThreadLocal<String> SESSION = new ThreadLocal<>();
    private static final ThreadLocal<ShAgentGrant> GRANT = new ThreadLocal<>();

    private ShAgentSessionContext() {
        // holder
    }

    public static void set(String sessionId) {
        SESSION.set(sessionId);
    }

    /** Binds both the session id and the credential's grant for this request. */
    public static void set(String sessionId, ShAgentGrant grant) {
        SESSION.set(sessionId);
        GRANT.set(grant);
    }

    public static void clear() {
        SESSION.remove();
        GRANT.remove();
    }

    public static Optional<String> current() {
        String value = SESSION.get();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /**
     * The current credential's grant.
     *
     * <p>Defaults to {@link ShAgentGrant#CONSOLE} rather than to a denying grant, because
     * the un-bound case is a human at a console session — the caller Block L exists to
     * protect, not to restrain. An {@code AGENT} token always sets one explicitly.</p>
     */
    public static ShAgentGrant grant() {
        ShAgentGrant grant = GRANT.get();
        return grant == null ? ShAgentGrant.CONSOLE : grant;
    }

    /**
     * The actor string to record in the SH51 audit trail.
     *
     * <p>{@code alice (agent:7f3c…)} rather than either alone: the human whose credential
     * was used stays visible — an agent acts <em>as</em> someone, and hiding that would
     * make the audit trail worse, not better — while the session id lets a reviewer select
     * exactly the changes one run made. The id is also stored in its own column now, but
     * the suffix stays: the console's activity list renders the owner string and would
     * otherwise stop saying that a change was an agent's.</p>
     *
     * @param principal the authenticated principal name, possibly {@code null}
     */
    public static String actor(String principal) {
        String base = principal == null || principal.isBlank() ? "agent" : principal;
        return current().map(session -> base + " (agent:" + session + ")").orElse(base);
    }

    /** {@link #actor(String)} for the authenticated principal of the current request. */
    public static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return actor(authentication == null ? null : authentication.getName());
    }
}
