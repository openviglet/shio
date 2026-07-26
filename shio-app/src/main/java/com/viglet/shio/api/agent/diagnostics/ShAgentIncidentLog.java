/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * The bounded incident log behind {@code GET /api/v2/agent/diagnostics} (SH103).
 *
 * <h2>What this is for</h2>
 *
 * <p>SH80's teaching errors cover the failures an agent <em>causes</em>: they come back
 * on the same call, carrying the fix. This covers the other half — the failures that
 * happen after the agent has moved on. A webhook that never reached the front end, an
 * image transform that fell back to the original, a scheduled publish that threw at
 * 03:00, a 500 on a call whose response the agent no longer has. Today the only record
 * of those is a line in {@code store/logs}, which an agent cannot read and a human
 * reads only after being told something is wrong.</p>
 *
 * <h2>Why in memory, and why that is enough</h2>
 *
 * <p>A table would mean a migration, a retention policy and a write on the failure
 * path — where the code is, by definition, already having a bad time. What the agent
 * needs is "what has gone wrong since I started working", and the working session and
 * the process share a lifetime closely enough for a ring buffer to answer it. The cost
 * of the choice is stated in the payload rather than hidden: the diagnostics response
 * reports {@code since} (when this buffer started collecting) and {@code dropped}, so a
 * caller can tell "nothing failed" from "nothing is remembered". A durable feed, if it
 * is ever wanted, belongs with SH88's cursor, not here.</p>
 *
 * <p>Repeats are folded rather than listed: one broken webhook target retried on every
 * publish would otherwise evict every other incident from the buffer, which is the one
 * way a diagnostics log can actively mislead. Identical incidents (same kind, same
 * subject, same message) collapse to a single row with a {@code count} and a
 * {@code lastSeen}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAgentIncidentLog {

    /**
     * Distinct incidents retained. Small on purpose: this is a "what is broken right
     * now" window, and a caller reading 500 rows is grepping logs again — the thing
     * SH103 exists to remove.
     */
    static final int CAPACITY = 50;

    /** What failed. Kinds are a closed set so a caller can branch on them. */
    public enum Kind {

        /** An API call answered 5xx — the agent's own call, after the fact. */
        API_ERROR,

        /** A webhook delivery gave up, so a front end may still be serving stale pages. */
        WEBHOOK,

        /** An image transform failed and the original was served instead. */
        TRANSFORM,

        /** A scheduled publish/unpublish threw; the post is not in the state it was told to be. */
        SCHEDULE;

        public String wireName() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private final Deque<Incident> incidents = new ArrayDeque<>();
    private final Instant since = Instant.now();
    private long dropped;

    /**
     * Records one failure. Never throws and never blocks on anything but this monitor —
     * a diagnostics log that could break the path it observes would be worse than none.
     *
     * @param kind    what kind of failure
     * @param subject what it happened to: a URL, a post id, a request path
     * @param message the shortest thing that identifies the cause
     * @param fix     what to do about it, or {@code null} when there is nothing generic
     *                to say
     */
    public synchronized void record(Kind kind, String subject, String message, String fix) {
        if (kind == null) {
            return;
        }
        String subjectText = trim(subject);
        String messageText = trim(message);
        for (Incident existing : incidents) {
            if (existing.matches(kind, subjectText, messageText)) {
                existing.seenAgain();
                return;
            }
        }
        if (incidents.size() >= CAPACITY) {
            incidents.removeFirst();
            dropped++;
        }
        incidents.addLast(new Incident(kind, subjectText, messageText, trim(fix)));
    }

    /**
     * The incidents, most recent occurrence first.
     *
     * @param limit row cap; {@code <= 0} returns everything retained
     */
    public synchronized List<Incident> recent(int limit) {
        List<Incident> snapshot = incidents.stream()
                .map(Incident::copy)
                .sorted(Comparator.comparing(Incident::lastSeen).reversed()
                        .thenComparing(Incident::subject, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return limit <= 0 || snapshot.size() <= limit ? snapshot : List.copyOf(snapshot.subList(0, limit));
    }

    /** When this buffer started collecting — the honest bound on "nothing failed". */
    public Instant since() {
        return since;
    }

    /** Incidents evicted because the buffer was full. */
    public synchronized long dropped() {
        return dropped;
    }

    /** Test seam: forget everything (a shared bean would otherwise leak between tests). */
    public synchronized void clear() {
        incidents.clear();
        dropped = 0;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        if (text.isEmpty()) {
            return null;
        }
        // Long enough to identify a cause, short enough that 50 of them stay cheap.
        return text.length() <= 300 ? text : text.substring(0, 297) + "...";
    }

    /** One recorded failure, folded over its repeats. */
    public static final class Incident {

        private final Kind kind;
        private final String subject;
        private final String message;
        private final String fix;
        private final Instant firstSeen;
        private Instant lastSeen;
        private int count;

        private Incident(Kind kind, String subject, String message, String fix) {
            this(kind, subject, message, fix, Instant.now(), Instant.now(), 1);
        }

        private Incident(Kind kind, String subject, String message, String fix,
                         Instant firstSeen, Instant lastSeen, int count) {
            this.kind = kind;
            this.subject = subject;
            this.message = message;
            this.fix = fix;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.count = count;
        }

        private boolean matches(Kind otherKind, String otherSubject, String otherMessage) {
            return kind == otherKind
                    && java.util.Objects.equals(subject, otherSubject)
                    && java.util.Objects.equals(message, otherMessage);
        }

        private void seenAgain() {
            count++;
            lastSeen = Instant.now();
        }

        private Incident copy() {
            return new Incident(kind, subject, message, fix, firstSeen, lastSeen, count);
        }

        public Kind kind() {
            return kind;
        }

        public String subject() {
            return subject;
        }

        public String message() {
            return message;
        }

        public String fix() {
            return fix;
        }

        public Instant firstSeen() {
            return firstSeen;
        }

        public Instant lastSeen() {
            return lastSeen;
        }

        public int count() {
            return count;
        }
    }
}
