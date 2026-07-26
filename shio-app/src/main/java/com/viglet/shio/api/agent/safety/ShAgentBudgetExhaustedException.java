/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * A {@code 429}: this agent session has spent its budget (SH110).
 *
 * <h2>Why 429 and not 403</h2>
 *
 * <p>Because the caller is not forbidden, it is finished. {@code 403} says "you may never
 * do this" and sends a well-behaved agent looking for a different credential or a different
 * verb; {@code 429} says "not on this run", which is the true statement and the one whose
 * obvious next step — start a new session, or ask the human — is the right one.</p>
 *
 * <p>The {@code fix} deliberately does not suggest raising the ceiling. An agent that has
 * hit a runaway guard is the least reliable available judge of whether the guard was wrong,
 * and a limit whose error message tells you how to remove it is not a limit.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShAgentBudgetExhaustedException extends ShTeachingException {

    /**
     * @param resource {@code calls} or {@code writes} — named, because they are metered
     *                 separately and knowing which one ran out is the difference between
     *                 "I am looping" and "I am writing too much"
     * @param ceiling  the ceiling that was hit
     * @param session  the session that spent it
     */
    public ShAgentBudgetExhaustedException(String resource, int ceiling, String session) {
        super(ShProblem.of("agent-budget-exhausted", HttpStatus.TOO_MANY_REQUESTS,
                        "This agent session has used its " + ceiling + " " + resource)
                .withDetail("Session " + session + " has reached the per-session " + resource
                        + " ceiling for its credential. The ceiling exists to stop a loop from "
                        + "becoming a bill; it is per session, not per credential, so it is not "
                        + "a statement about the work being wrong.")
                .withFix("Stop and report what you have done so far. If the remaining work is "
                        + "genuinely needed, start a new session — and check GET "
                        + "/api/v2/agent/changes first, in case the previous attempts already "
                        + "applied it.")
                .withExample("GET /api/v2/agent/session"));
    }
}
