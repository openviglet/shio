/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

/**
 * What the authenticating credential is allowed to do on the agent surface (SH106).
 *
 * <h2>Why a value object rather than three fields on the token context</h2>
 *
 * <p>Because the same three answers have to be produced for callers that hold no token at
 * all. A console session and the {@code shio} CLI reach the agent surface over HTTP Basic,
 * and they are not agent credentials — they are the human, or a tool the human is driving.
 * Modelling "no token" as {@link #CONSOLE} rather than as a null token context means every
 * enforcement point asks one question ("may I publish?") instead of two ("is there a
 * token, and if so may it publish?"), and the second form is the one that gets an
 * {@code if} wrong eventually.</p>
 *
 * <h2>The default direction</h2>
 *
 * <p>{@link #CONSOLE} is unbounded and {@code AGENT} tokens are bounded. That asymmetry is
 * the §XII invariant in one line: the agent path is never a privilege escalation, so the
 * credential minted <em>for</em> agents starts more restricted than the human's, never
 * less. A ceiling of {@code 0} means "use the configured default"; a negative one means
 * explicitly unlimited, which is a thing an operator may choose and a package may not.</p>
 *
 * @param mayPublish whether this credential may change what visitors see (SH107)
 * @param maxCalls   per-session agent call ceiling, {@code 0} = configured default,
 *                   negative = unlimited (SH110)
 * @param maxWrites  per-session content-write ceiling, same convention (SH110)
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentGrant(boolean mayPublish, int maxCalls, int maxWrites) {

    /**
     * The credential a human is holding: publish allowed, no ceilings.
     *
     * <p>Not "no limits because we haven't got round to it" — a console operator already
     * has the console, where every one of these acts is one click away and unmetered.
     * Metering the API while leaving the UI open would be security theatre.</p>
     */
    public static final ShAgentGrant CONSOLE = new ShAgentGrant(true, -1, -1);

    /** @return {@code true} when this grant carries no ceilings of any kind. */
    public boolean unbounded() {
        return maxCalls < 0 && maxWrites < 0;
    }
}
