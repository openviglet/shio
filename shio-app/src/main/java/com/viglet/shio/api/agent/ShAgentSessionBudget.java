/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import org.springframework.http.HttpStatus;

/**
 * What one credential may write across a window (SH734).
 *
 * <h2>The gap between two ceilings that already existed</h2>
 *
 * <p>{@code batch.maxOps} caps one batch, and SH22/D's quota caps a tenant's total — the agent
 * write path has called {@code checkCanCreateSite} and {@code checkCanCreatePost} since it shipped.
 * Between them sits the case neither sees: <b>an agent looping</b> stays under the per-batch cap and
 * under a generous tenant total while making ten thousand calls. SH108's confirm token stops a
 * destructive op arriving as a typo; nothing stopped a cheap op arriving ten thousand times.</p>
 *
 * <p>SH110 held this behind SH45's metering for its whole life. It does not need one: a counter
 * keyed on the credential answers it, and bytes — the part that genuinely needs a meter — stayed
 * with SH110.</p>
 *
 * <h2>A fixed window, and why not a sliding one</h2>
 *
 * <p>A sliding window is more accurate and needs per-call timestamps, which is unbounded memory per
 * caller for a guard whose whole point is to bound something. A fixed window costs two fields and
 * makes the refusal <b>answerable</b>: it can say when the caller may retry, which a sliding window
 * can only approximate. The cost is that a caller can spend two windows' worth across a boundary,
 * which is the textbook objection and is not one here — this is a runaway guard, not a rate meter
 * somebody is billed against.</p>
 *
 * <h2>Dry runs are free</h2>
 *
 * <p>A plan writes nothing. Charging for one would push a caller towards writing blind, which is
 * the opposite of what SH108's two-step and P9 ask for — and the confirm token means a destructive
 * run <em>must</em> plan first, so charging would tax the safe path twice.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentSessionBudget {

    /**
     * How many callers are tracked before the oldest windows are dropped.
     *
     * <p>A guard that grows without bound is the defect it exists to prevent, one level down: the
     * key is caller-supplied, so an attacker with many credentials would otherwise turn this into a
     * memory leak. Eviction drops only <em>expired</em> windows, so a live caller is never
     * forgotten mid-window in the ordinary case; the cap is reached only under abuse, where losing
     * a count is the correct failure — it re-admits a caller rather than refusing everybody.
     */
    static final int MAX_TRACKED_CALLERS = 10_000;

    private final ShAgentProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * The clock this guard reads (SH759).
     *
     * <p>Injectable because <b>a window closing is a fact about time</b>, and a test that cannot
     * move time can only ask the machine to be slow enough. {@code theWindowReopens} used to set
     * the window to one nanosecond and spend twice, on the argument that a window already elapsed
     * needs no sleep — but the second {@code Instant.now()} returns the <em>same</em> instant
     * whenever the clock's granularity is coarser than the gap, which on Windows it is by orders
     * of magnitude. So the reset was observed only when a tick happened to land between two
     * adjacent reads: green most runs, red on 2026-08-14, and a flake is worse than a break
     * (SH250).</p>
     *
     * <p>Same shape {@code ShTenantApiCallMeter} takes, and for the same reason: the default
     * constructor is the one Spring uses and the package-private one is what a test drives.</p>
     */
    private final Clock clock;

    @Autowired
    public ShAgentSessionBudget(ShAgentProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ShAgentSessionBudget(ShAgentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Charges {@code ops} to the current caller, or refuses.
     *
     * @param ops how many write ops this run is about to perform; a dry run charges nothing and
     *            must not call this at all
     * @throws SessionBudgetException when the window is spent
     */
    public void spend(int ops) {
        int max = properties.getBatch().getMaxSessionOps();
        if (max <= 0 || ops <= 0) {
            return;
        }
        Duration length = properties.getBatch().getSessionWindow();
        Instant now = clock.instant();
        String caller = caller();
        evictExpired(now);

        Window window = windows.compute(caller, (key, current) ->
                current == null || !current.resetsAt.isAfter(now)
                        ? new Window(now.plus(length))
                        : current);
        int spent = window.spent.addAndGet(ops);
        if (spent > max) {
            // Charged before the check and deliberately not refunded: a caller that keeps trying
            // past the ceiling keeps pushing the count up, so a loop cannot walk the limit by
            // retrying a batch that is one op too large. The window is what ends it, not restraint.
            throw new SessionBudgetException(spent - ops, ops, max, window.resetsAt);
        }
    }

    /**
     * Who is spending — the API token when there is one, else the authenticated name.
     *
     * <p>The token first, because that is the identity an agent actually holds: two agents sharing
     * a login are two credentials and should have two budgets, and a token is also the thing an
     * operator can revoke when this fires repeatedly. The name is the fallback for the console's
     * own session, where there is no token and the human is the caller.</p>
     */
    private static String caller() {
        return ShApiTokenContext.current()
                .map(ShApiTokenContext::tokenId)
                .filter(id -> id != null && !id.isBlank())
                .map(id -> "token:" + id)
                .orElseGet(() -> {
                    var auth = org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication();
                    return "user:" + (auth == null ? "anonymous" : auth.getName());
                });
    }

    /** Drops windows that have already reset, and only then enforces the cap. */
    private void evictExpired(Instant now) {
        if (windows.size() < MAX_TRACKED_CALLERS) {
            return;
        }
        windows.values().removeIf(window -> !window.resetsAt.isAfter(now));
        if (windows.size() >= MAX_TRACKED_CALLERS) {
            windows.clear();
        }
    }

    /** Visible for the SH734 test, which has to observe a window ending without waiting an hour. */
    void forget() {
        windows.clear();
    }

    private static final class Window {

        private final Instant resetsAt;
        private final AtomicInteger spent = new AtomicInteger();

        private Window(Instant resetsAt) {
            this.resetsAt = resetsAt;
        }
    }

    /**
     * The window is spent — <b>429</b>, and the one refusal on this surface whose answer is to wait.
     *
     * <p>Every other teaching error here asks the caller to change the request. This one says the
     * request was fine and the caller has had enough of them, so the fix is an instant rather than
     * an edit — and naming it is the whole point: a 429 with no reset time is an invitation to
     * retry immediately, which is how a runaway becomes a busy loop.</p>
     */
    public static class SessionBudgetException extends ShTeachingException {

        public SessionBudgetException(int alreadySpent, int asked, int max, Instant resetsAt) {
            super(ShProblem.of("session-budget-spent", HttpStatus.TOO_MANY_REQUESTS,
                            "This credential has written its allowance for the window")
                    .withDetail("It had already written " + alreadySpent + " op(s) and this run asked "
                            + "for " + asked + " more, past the ceiling of " + max + ". Nothing in "
                            + "this run was written.")
                    .withFix("Wait until " + resetsAt + " and send it again — the count resets then "
                            + "and the request itself is fine. If this is legitimate work, the "
                            + "ceiling is shio.agent.batch.max-session-ops and an operator sets it; "
                            + "a dry run costs nothing, so planning while you wait is free."));
        }
    }
}
