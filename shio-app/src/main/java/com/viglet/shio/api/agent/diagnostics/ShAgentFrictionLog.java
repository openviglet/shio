/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * What readers asked for and did not get (SH602).
 *
 * <h2>The collector this replaces</h2>
 *
 * <p>Twelve rounds produced roughly forty tasks and <b>every one of them came from the same place</b>:
 * a session ended, somebody read it back, and a pattern was noticed. That is expensive, lossy, and
 * it only ever runs when a human asks for it — so it does not run at all for readers who are not
 * this one, and nobody is going to narrate a stranger's session.</p>
 *
 * <h2>Not incidents, which is why nothing recorded them</h2>
 *
 * <p>The instance already records what <em>failed</em>: {@link ShAgentIncidentLog} holds the 5xx and
 * the webhook that gave up. None of the signals here is a failure. A 404 on a path an agent
 * constructed is a correct answer. A {@code didYouMean} that fired is the surface working. A
 * capability asked for under a name that does not exist is a reader using their own vocabulary. Each
 * is the instance behaving properly and a reader not getting what they came for, and together they
 * are the whole discovery-friction record.</p>
 *
 * <h2>Keyed by what was ASKED FOR, not by what broke</h2>
 *
 * <p>That is the design, and it is what makes the output evidence rather than noise. <b>A key asked
 * for often and answered never is a missing capability with a name attached</b> — and the name is
 * the reader's own, which is strictly better than anything a transcript review produces, because a
 * summariser writes the name they would have used.</p>
 *
 * <h2>In memory, bounded, and never a write</h2>
 *
 * <p>The same terms as the incident log: a counter that survived a restart would be a second store
 * to migrate, and one that grew without a ceiling would be a denial of service keyed by a path an
 * attacker chooses. Past the ceiling nothing new is admitted and {@code dropped} says so, because a
 * counter that silently stops counting reads exactly like a quiet instance.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAgentFrictionLog {

    /**
     * Distinct keys held at once.
     *
     * <p>Small on purpose. This is a ranking, not a log: the question it answers is "what did
     * readers keep asking for", and the tail of a long-tailed distribution answers nothing while
     * costing memory an attacker can spend.</p>
     */
    static final int MAX_KEYS = 200;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong dropped = new AtomicLong();

    /**
     * One thing asked for, and how often.
     *
     * @param kind what kind of asking — {@code unknown-name}, {@code did-you-mean}, {@code not-found}
     * @param name what was asked for, in the reader's own words
     */
    public record Entry(String kind, String name, java.util.concurrent.atomic.AtomicInteger count) {
    }

    /** One thing asked for and not got, ranked. */
    public record Friction(String kind, String name, int count) {
    }

    /**
     * Record one asking.
     *
     * @param kind the shape of the friction
     * @param name what the reader asked for — never a value they supplied as content, only a NAME
     *             they used: a path, a capability, a field. A content value here would put a
     *             visitor's data in a diagnostics response every operator can read.
     */
    public void record(String kind, String name) {
        if (kind == null || name == null || name.isBlank()) {
            return;
        }
        String key = kind + " " + name.trim();
        Entry existing = entries.get(key);
        if (existing != null) {
            existing.count().incrementAndGet();
            return;
        }
        if (entries.size() >= MAX_KEYS) {
            dropped.incrementAndGet();
            return;
        }
        entries.computeIfAbsent(key,
                ignored -> new Entry(kind, name.trim(), new java.util.concurrent.atomic.AtomicInteger(0)))
                .count().incrementAndGet();
    }

    /**
     * The top of the list, most-asked first.
     *
     * <p>Ties break on the name so two runs of the same instance print the same order (P5) — a
     * ranking that reshuffles between reads is one nobody can diff.</p>
     */
    public List<Friction> top(int limit) {
        return entries.values().stream()
                .map(entry -> new Friction(entry.kind(), entry.name(), entry.count().get()))
                .sorted(Comparator.comparingInt(Friction::count).reversed()
                        .thenComparing(Friction::name))
                .limit(Math.max(limit, 0))
                .toList();
    }

    /** Keys refused because the ceiling was reached — a silent stop reads as a quiet instance. */
    public long dropped() {
        return dropped.get();
    }

    /** For tests, and for an operator who has just acted on the list. */
    public void clear() {
        entries.clear();
        dropped.set(0);
    }
}
