/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Binds the agent session id to the request thread on the REST agent surface (SH106).
 *
 * <p>MCP mints its own on {@code initialize} and sets the same context. This is the other
 * transport: {@code /agent/batch}, {@code /agent/apply} and everything the {@code shio} CLI
 * drives. Without it the SH109 review queue would be able to show what an MCP client changed
 * and nothing of what the CLI changed — a review surface with a hole in it exactly where the
 * most common client is.</p>
 *
 * <p><b>The header is supplied, never invented here.</b> A session is a <em>run</em> — many
 * requests that belong together — and a filter that minted one per request would produce a
 * session per call, which is the same as having none: the reviewer would see two hundred
 * one-change sessions instead of one run they can approve. A caller that sends no header gets
 * no session id, and its writes appear in the audit as an ordinary human's, which is what they
 * are. The CLI sends one per invocation.</p>
 *
 * <p>Ordered after Spring Security (which is where the tenant and the principal are resolved)
 * and it always clears, like {@code ShTenantFilter}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
@Order(Integer.MAX_VALUE - 100)
public class ShAgentSessionFilter extends OncePerRequestFilter {

    private final ShAgentRunService shAgentRunService;

    public ShAgentSessionFilter(ShAgentRunService shAgentRunService) {
        this.shAgentRunService = shAgentRunService;
    }

    /**
     * The header a caller stamps its run with. Deliberately the same name MCP uses
     * ({@code Mcp-Session-Id}) rather than a second spelling: one concept, one header, and an
     * MCP client bridged over HTTP does not have to translate it.
     */
    public static final String SESSION_HEADER = "Mcp-Session-Id";

    /**
     * What this run is for, in one sentence (SH1000).
     *
     * <p>A header rather than a body field because it belongs to the <em>run</em> and not to any one
     * call: {@code /agent/batch} and {@code /agent/apply} take different documents, and a premise
     * that had to be spelled into each of them would be two places to keep a sentence.</p>
     *
     * <p>{@code X-}-prefixed, which convention has discouraged since RFC 6648, and deliberately: it
     * is a Shio-specific hint with no registry entry and no ambition to have one, and the alternative
     * — an unprefixed {@code Intent} — is a name a proxy or a future standard could take.</p>
     */
    public static final String INTENT_HEADER = "X-Shio-Intent";

    /** Longest session id accepted — it is an opaque label, not a payload. */
    private static final int MAX_LENGTH = 64;

    private static final String AGENT_PATH_PREFIX = "/api/v2/agent";

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {
        String session = sessionOf(request);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }
        ShAgentSessionContext.set(session);
        try {
            // SH1000 — recorded before the request runs, so a run whose first call fails still says
            // what it was attempting. Only when the header is there: a caller that declares once
            // pays for it once, and one that repeats it on every call has chosen to.
            String intent = request.getHeader(INTENT_HEADER);
            if (intent != null && !intent.isBlank()) {
                shAgentRunService.declare(session, intent.trim(),
                        ShAgentSessionContext.actor());
            }
            filterChain.doFilter(request, response);
        } finally {
            ShAgentSessionContext.clear();
        }
    }

    /**
     * The session id for this request, or {@code null}.
     *
     * <p>Confined to the agent surface on purpose: a session header on a console request
     * would attribute a human's edit to an agent run, and the review queue would then offer
     * to revert a change no agent made.</p>
     */
    private static String sessionOf(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        if (path == null || !path.startsWith(AGENT_PATH_PREFIX)) {
            return null;
        }
        String header = request.getHeader(SESSION_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        String trimmed = header.trim();
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }
}
