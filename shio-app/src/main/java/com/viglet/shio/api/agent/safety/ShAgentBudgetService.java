/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.ShAgentProperties;

/**
 * Per-session call and write ceilings for agent credentials (SH110).
 *
 * <h2>What a ceiling is for</h2>
 *
 * <p>Not security — the ACL (SH54), the site allow-list (SH2) and the tenant quota (SH22)
 * already say what an agent may touch. This says <em>how much</em>, and the failure it
 * exists for is not an attacker but a loop: an agent that misreads its own verification
 * output and re-applies the same document four hundred times. A runaway agent should hit a
 * ceiling and be told so, not produce a bill.</p>
 *
 * <h2>Two counters, because they fail differently</h2>
 *
 * <p>Calls and writes are metered separately. A thousand reads costs tokens and wall clock
 * and is recoverable by hanging up; a thousand writes costs <em>content</em>, and the
 * recovery is a human in the review queue. Folding them into one number would let the
 * cheap failure consume the budget that exists for the expensive one.</p>
 *
 * <h2>In memory, and honest about it</h2>
 *
 * <p>The counters live in a bounded LRU map keyed by session id. That means three true
 * things a caller should be able to find out rather than discover: a restart resets every
 * budget, an idle session is forgotten after its TTL, and a very large number of concurrent
 * sessions evicts the oldest. All three are reported by {@link #snapshot} through the
 * {@code /agent/session} endpoint rather than left implicit — a limit that silently stops
 * limiting is worse than no limit, because it is believed.</p>
 *
 * <p>A durable, per-tenant version belongs with SH45's metering, which is where a number
 * that has to survive a restart already lives.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentBudgetService {

    private final ShAgentProperties properties;
    private final Clock clock;
    private final Map<String, Counters> sessions;

    public ShAgentBudgetService(ShAgentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.sessions = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Counters> eldest) {
                return size() > Math.max(16, properties.getBudget().getMaxSessions());
            }
        });
    }

    /**
     * Counts one agent-surface call against the current session, throwing when the ceiling
     * is spent.
     *
     * <p>Counted <em>before</em> the work, so the call that breaches the ceiling is refused
     * rather than being the last one allowed. That also makes the refusal cheap, which
     * matters when the thing being bounded is a loop.</p>
     */
    public void countCall() {
        Limits limits = limits();
        if (!limits.enforced()) {
            return;
        }
        Counters counters = counters(limits.session());
        int used = counters.calls().incrementAndGet();
        if (limits.maxCalls() >= 0 && used > limits.maxCalls()) {
            throw new ShAgentBudgetExhaustedException("calls", limits.maxCalls(), limits.session());
        }
    }

    /**
     * Counts {@code n} content writes against the current session.
     *
     * @param writes how many objects a call is about to change; a batch counts as its
     *               changed rows, not as one, because otherwise a session with a ceiling of
     *               ten could write two thousand posts in ten batches
     */
    public void countWrites(int writes) {
        if (writes <= 0) {
            return;
        }
        Limits limits = limits();
        if (!limits.enforced()) {
            return;
        }
        Counters counters = counters(limits.session());
        int used = counters.writes().addAndGet(writes);
        if (limits.maxWrites() >= 0 && used > limits.maxWrites()) {
            // Rolled back so a refused batch does not also consume the budget it was
            // refused for — otherwise one oversized request would spend a session's whole
            // remaining allowance without writing anything.
            counters.writes().addAndGet(-writes);
            throw new ShAgentBudgetExhaustedException("writes", limits.maxWrites(),
                    limits.session());
        }
    }

    /** What the current session has spent and what it has left; empty when unmetered. */
    public Snapshot snapshot() {
        Limits limits = limits();
        if (!limits.enforced()) {
            return new Snapshot(limits.session(), false, 0, 0, -1, -1);
        }
        Counters counters = counters(limits.session());
        return new Snapshot(limits.session(), true, counters.calls().get(), counters.writes().get(),
                limits.maxCalls(), limits.maxWrites());
    }

    /**
     * The ceilings that apply right now.
     *
     * <p>A session id is required: an unmetered request is one with nothing to meter
     * <em>against</em>, and inventing a key (the principal, say) would make two concurrent
     * runs of the same agent share one budget.</p>
     */
    private Limits limits() {
        ShAgentProperties.Budget config = properties.getBudget();
        String session = ShAgentSessionContext.current().orElse(null);
        ShAgentGrant grant = ShAgentSessionContext.grant();
        if (!config.isEnabled() || session == null || grant.unbounded()) {
            return new Limits(session, false, -1, -1);
        }
        return new Limits(session, true,
                ceiling(grant.maxCalls(), config.getDefaultMaxCalls()),
                ceiling(grant.maxWrites(), config.getDefaultMaxWrites()));
    }

    /** {@code 0} on a grant means "use the configured default"; negative means unlimited. */
    private static int ceiling(int fromGrant, int configured) {
        return fromGrant == 0 ? configured : fromGrant;
    }

    private Counters counters(String session) {
        Instant now = Instant.now(clock);
        Duration ttl = Duration.ofMinutes(Math.max(1, properties.getBudget().getSessionTtlMinutes()));
        synchronized (sessions) {
            Counters existing = sessions.get(session);
            if (existing != null && Duration.between(existing.startedAt(), now).compareTo(ttl) <= 0) {
                return existing;
            }
            Counters fresh = new Counters(now, new AtomicInteger(), new AtomicInteger());
            sessions.put(session, fresh);
            return fresh;
        }
    }

    private record Counters(Instant startedAt, AtomicInteger calls, AtomicInteger writes) {
    }

    private record Limits(String session, boolean enforced, int maxCalls, int maxWrites) {
    }

    /**
     * What a session has spent.
     *
     * @param session  the session id, or {@code null} when the request carries none
     * @param metered  whether ceilings apply at all — {@code false} for a console
     *                 credential, for a read with no session, and when budgets are off
     * @param calls    agent-surface calls counted so far
     * @param writes   content writes counted so far
     * @param maxCalls the call ceiling, {@code -1} for unlimited
     * @param maxWrites the write ceiling, {@code -1} for unlimited
     */
    public record Snapshot(String session, boolean metered, int calls, int writes,
                           int maxCalls, int maxWrites) {
    }
}
