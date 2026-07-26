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

import com.viglet.shio.api.agent.safety.ShAgentSessionContext;

/**
 * The MCP transport's view of the agent session id (SH75), now a thin delegate.
 *
 * <p>SH106 moved the id out of this class: it is no longer MCP's, because a REST agent
 * that could not be reviewed while an MCP one could would make SH109's review queue depend
 * on the transport rather than on who was writing. The holder is
 * {@link ShAgentSessionContext}, and this type stays because the transport is where the id
 * is <em>minted</em> — {@code initialize} is the only place in the protocol that can — and
 * the tools read it by a name that says which layer they belong to.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShMcpSessionContext {

    private ShMcpSessionContext() {
        // holder
    }

    public static void set(String sessionId) {
        ShAgentSessionContext.set(sessionId);
    }

    public static void clear() {
        ShAgentSessionContext.clear();
    }

    public static Optional<String> current() {
        return ShAgentSessionContext.current();
    }

    /** @see ShAgentSessionContext#actor(String) */
    public static String actor(String principal) {
        return ShAgentSessionContext.actor(principal);
    }

    /** @see ShAgentSessionContext#actor() */
    public static String actor() {
        return ShAgentSessionContext.actor();
    }
}
