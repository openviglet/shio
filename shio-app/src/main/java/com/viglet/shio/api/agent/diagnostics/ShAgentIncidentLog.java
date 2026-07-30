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
 * <h2>Durable now, and still in memory too (SH144)</h2>
 *
 * <p>SH103 shipped this as a ring buffer and stated the cost in the payload rather than
 * hiding it: {@code since} (when the buffer started collecting) and {@code dropped}, so a
 * caller could tell "nothing failed" from "nothing is remembered". SH144 makes the record
 * durable — {@link ShAgentIncidentStore} — because "was anything failing while I was away"
 * is a question about a period a buffer may no longer cover, and a restart answered it with
 * silence.</p>
 *
 * <p><b>The buffer did not go away, and that is deliberate.</b> The store writes in its own
 * transaction and swallows anything the persistence layer raises (a diagnostics log that
 * turned a webhook timeout into a 500 would be worse than none), so a write can fail. When it
 * does, the in-memory copy is what is left, and the payload says which rows are only there.
 * Keeping both also means the failure path never depends on a healthy database to leave a
 * trace — the one condition under which incidents are most worth having.</p>
 *
 * <p>Repeats are folded rather than listed, in both halves: one broken webhook target retried
 * on every publish would otherwise evict every other incident from the buffer, which is the
 * one way a diagnostics log can actively mislead. Identical incidents (same kind, same
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
        SCHEDULE,

        /**
         * SH270 — a page rendered <em>incomplete</em> because the content itself is malformed.
         *
         * <p>The four kinds above are operational: something the instance was doing failed. This one
         * is the opposite shape and it is the gap SH269's lint could not see, because the lint can
         * only check the recorders that exist. Two render paths <b>degrade silently by design</b> —
         * a {@code postTypeLayout} that is not JSON yields no bindings, and a post whose {@code data}
         * is not an object yields no fields — and the result in both cases is a page that looks
         * <em>empty</em> rather than a failure anybody can see. Nothing reported them.</p>
         *
         * <p>Deliberately narrow. "Record every swallowed exception" would fill the window with
         * handled conditions, which is what SH103 refused for 4xx: a degradation earns an incident
         * only when the content is wrong <em>and</em> the symptom is silence. Folding by subject keeps
         * one broken post to one entry however many times it renders.</p>
         */
        CONTENT;

        public String wireName() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    private final Deque<Incident> incidents = new ArrayDeque<>();
    private final Instant since = Instant.now();
    private long dropped;
    private long unpersisted;

    private final ShAgentIncidentStore store;

    @org.springframework.beans.factory.annotation.Autowired
    public ShAgentIncidentLog(ShAgentIncidentStore store) {
        this.store = store;
    }

    /**
     * The buffer alone, with no durable half.
     *
     * <p>For a caller with no database in reach — a unit test of one of the four recorders,
     * where standing up a repository would test the store rather than the recorder. It exists
     * because the alternative was a nullable store field injected by Spring, and "the log
     * works without persistence" is a property worth stating in a constructor rather than
     * discovering from a null check.</p>
     */
    public ShAgentIncidentLog() {
        this(null);
    }

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
    public void record(Kind kind, String subject, String message, String fix) {
        record(kind, subject, message, fix, null);
    }

    /** @param frames SH190 — the throwing location, or {@code null} when there is none */
    public void record(Kind kind, String subject, String message, String fix, String frames) {
        if (kind == null) {
            return;
        }
        String subjectText = trim(subject);
        String messageText = trim(message);
        // Durable first, and outside this object's monitor: the store opens its own
        // transaction (SH144), and holding a lock across a database round trip on the failure
        // path is how a diagnostics log becomes the thing that makes an outage worse.
        // `lost` and "there was nowhere to write it" are different facts: only the first one
        // means the durable record is incomplete, and only it should be reported as such.
        boolean lost = store != null && messageText != null
                && !store.record(kind, subjectText, messageText, trim(fix), trim(frames),
                        Instant.now());
        remember(kind, subjectText, messageText, trim(fix), trim(frames), lost);
    }

    private synchronized void remember(Kind kind, String subjectText, String messageText,
                                       String fixText, String framesText, boolean lost) {
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
        if (lost) {
            unpersisted++;
        }
        incidents.addLast(new Incident(kind, subjectText, messageText, fixText, framesText));
    }

    /**
     * Incidents this process holds that could not be written down.
     *
     * <p>Reported so an empty durable feed on a healthy-looking instance cannot be mistaken
     * for a quiet one: if the database was the thing that was failing, the record of the
     * failure is exactly what could not be stored.</p>
     */
    public synchronized long unpersisted() {
        return unpersisted;
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
        unpersisted = 0;
        if (store != null) {
            store.clear();
        }
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
        /** SH190 — the throwing location, or null when there is no exception behind this kind. */
        private final String frames;
        private final Instant firstSeen;
        private Instant lastSeen;
        private int count;

        private Incident(Kind kind, String subject, String message, String fix, String frames) {
            this(kind, subject, message, fix, frames, Instant.now(), Instant.now(), 1);
        }

        private Incident(Kind kind, String subject, String message, String fix, String frames,
                         Instant firstSeen, Instant lastSeen, int count) {
            this.kind = kind;
            this.subject = subject;
            this.message = message;
            this.fix = fix;
            this.frames = frames;
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
            return new Incident(kind, subject, message, fix, frames, firstSeen, lastSeen, count);
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

        public String frames() {
            return frames;
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
