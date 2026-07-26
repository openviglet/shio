/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import java.io.IOException;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Binds the agent session id and the credential's grant to the request thread (SH106).
 *
 * <h2>Where a session id comes from</h2>
 *
 * <p>Three sources, in order of preference:</p>
 *
 * <ol>
 *   <li>The {@code X-Shio-Agent-Session} header the caller sent. This is what makes a
 *       multi-call run reviewable as one unit: "the agent proposed 14 changes" is a
 *       sentence about a session, and a session an agent cannot name is a session the
 *       review queue has to reconstruct by guessing at timestamps.</li>
 *   <li>The MCP {@code Mcp-Session-Id}, so a client that already speaks the protocol's own
 *       session mechanism does not have to send a second header saying the same thing.</li>
 *   <li>A freshly minted id, for a write that arrived without either. Minting rather than
 *       leaving it null is deliberate: an unattributed agent write is invisible to the
 *       review queue, and "the caller forgot a header" is not a good reason for a change
 *       to escape review.</li>
 * </ol>
 *
 * <h2>Why only writes get a minted id</h2>
 *
 * <p>A read changes nothing, so attributing it buys nothing and minting an id per read
 * would fill the budget counters with sessions of one call each. Reads still <em>carry</em>
 * a caller-supplied id when one is sent, because the budget (SH110) meters calls as well
 * as writes and a run that reads a thousand times is a run worth metering.</p>
 *
 * <p>Ordered after Spring Security so {@link ShApiTokenContext} is already populated —
 * the grant is derived from the authenticated token, and deriving it before authentication
 * would hand every caller the console's unbounded grant.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class ShAgentSessionFilter extends OncePerRequestFilter {

    /** The MCP transport's own session header, honoured as a second source. */
    static final String MCP_SESSION_HEADER = "Mcp-Session-Id";

    private static final String AGENT_PATH_PREFIX = "/api/v2/agent";
    private static final String MCP_PATH = "/mcp";

    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!isAgentSurface(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String session = header(request, ShAgentSessionContext.SESSION_HEADER);
        if (session == null) {
            session = header(request, MCP_SESSION_HEADER);
        }
        if (session == null && !"GET".equalsIgnoreCase(request.getMethod())) {
            session = UUID.randomUUID().toString();
        }

        ShAgentSessionContext.set(session, grant());
        try {
            filterChain.doFilter(request, response);
        } finally {
            ShAgentSessionContext.clear();
        }
    }

    /**
     * The grant of whatever credential authenticated this request.
     *
     * <p>No token context means a console session or HTTP Basic — a human, or a tool a
     * human is driving — which is {@link ShAgentGrant#CONSOLE}. A token context means the
     * grant the SH2 filter read off the row.</p>
     */
    private static ShAgentGrant grant() {
        return ShApiTokenContext.current()
                .map(ShApiTokenContext::agent)
                .orElse(ShAgentGrant.CONSOLE);
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        // Bounded, because it is echoed into an audit column and a response body: an
        // unbounded header would let a caller write novels into the trail.
        String trimmed = value.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private static boolean isAgentSurface(HttpServletRequest request) {
        String path = relativePath(request);
        return path.startsWith(AGENT_PATH_PREFIX)
                || path.equals(MCP_PATH) || path.startsWith(MCP_PATH + "/");
    }

    /** Request URI with any servlet context path stripped. */
    private static String relativePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return "";
        }
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }
}
