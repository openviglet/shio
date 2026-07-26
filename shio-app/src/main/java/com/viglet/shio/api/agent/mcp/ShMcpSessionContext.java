/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The agent session id for the current request (SH75).
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
 * <p><b>Deliberately a suffix on the actor string, not a column.</b> A column is the
 * right long-term answer and belongs to SH106 ({@code AGENT} token scope + session
 * identity), which owns the credential model and can migrate the schema once. Adding a
 * half-designed column here would mean two migrations and a period where two fields
 * disagree about who did what.</p>
 *
 * <p>A {@link ThreadLocal}, matching {@code ShTenantContext} — the request thread is
 * the unit of work, and the filter that sets it always clears it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShMcpSessionContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private ShMcpSessionContext() {
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
     * The actor string to record in the SH51 audit trail.
     *
     * <p>{@code alice (agent:7f3c…)} rather than either alone: the human whose
     * credential was used stays visible — an agent acts <em>as</em> someone, and hiding
     * that would make the audit trail worse, not better — while the session id lets a
     * reviewer select exactly the changes one run made.</p>
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
