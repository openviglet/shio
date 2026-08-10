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
         * SH84 — a Turing index push did not land, so the page is published but not findable.
         *
         * <p>Its own kind rather than a {@code WEBHOOK}, because the remedy differs and a caller
         * branching on {@code kind} is the reason kinds are a closed set: a webhook failure means a
         * front end may be serving a stale page, and this one means search will not return the page
         * at all. Same shape as {@code WEBHOOK} otherwise — an outbound delivery to another system,
         * failing where nothing in Shio looks wrong.</p>
         */
        INDEX,

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
        CONTENT,

        /**
         * SH217 — a blueprint package the operator added would not load.
         *
         * <p>Its own kind, and the reason is the remedy rather than the cause: every other kind
         * here is something the instance was <em>doing</em> that failed, and the fix is to retry
         * it or to fix the target. This is a package that was never usable, and the fix belongs to
         * whoever wrote it — which for the community half of the registry is not the operator.</p>
         *
         * <p>Silence is the answer on almost every instance, and that is the point of routing it
         * here rather than into the catalogue: wrapping a listing in an envelope would cost every
         * caller tokens on every call to be told nothing was refused (P3), while an incident costs
         * nothing until there is one.</p>
         */
        BLUEPRINT;

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
     * <h2>SH204 — one method, and why the convenience overloads had to go</h2>
     *
     * <p>This was a pair: a four-argument form and a five-argument one carrying {@code frames}.
     * Adding a site could not be another overload, because {@code record(kind, subject, message,
     * fix, siteId)} and {@code record(kind, subject, message, fix, frames)} are the <em>same
     * signature</em> — five strings — and the compiler cannot tell a caller's intent apart. A
     * reader could not either, which is the more important half: the SH357 lesson is that an
     * overload pair whose resolution decides behaviour is a defect even when it compiles.</p>
     *
     * <p>So every call site passes both, explicitly, {@code null} where the fact does not exist.
     * That is longer at four of the thirteen call sites and it makes the two facts that are
     * <b>deliberately absent</b> — the webhook give-up branch has no frame because the subscriber
     * refused a delivery that completed, and a 5xx has no site because it belongs to a request —
     * visible as a written {@code null} beside a comment, rather than as an argument nobody
     * passed.</p>
     *
     * @param kind    what kind of failure
     * @param subject what it happened to: a URL, a post id, a request path
     * @param message the shortest thing that identifies the cause
     * @param fix     what to do about it, or {@code null} when there is nothing generic
     *                to say
     * @param frames  SH190 — the throwing location, or {@code null} when there is none
     * @param siteId  SH204 — the site this belongs to, or {@code null} when it belongs to no one
     *                site. Part of the fold key: two sites failing the same way stay two rows
     */
    public void record(Kind kind, String subject, String message, String fix, String frames,
                       String siteId) {
        if (kind == null) {
            return;
        }
        String subjectText = trim(subject);
        String messageText = trim(message);
        String siteText = trim(siteId);
        // Durable first, and outside this object's monitor: the store opens its own
        // transaction (SH144), and holding a lock across a database round trip on the failure
        // path is how a diagnostics log becomes the thing that makes an outage worse.
        // `lost` and "there was nowhere to write it" are different facts: only the first one
        // means the durable record is incomplete, and only it should be reported as such.
        boolean lost = store != null && messageText != null
                && !store.record(kind, subjectText, messageText, trim(fix), trim(frames),
                        siteText, Instant.now());
        remember(kind, subjectText, messageText, trim(fix), trim(frames), siteText, lost);
    }

    private synchronized void remember(Kind kind, String subjectText, String messageText,
                                       String fixText, String framesText, String siteText,
                                       boolean lost) {
        for (Incident existing : incidents) {
            if (existing.matches(kind, subjectText, messageText, siteText)) {
                existing.seenAgain(messageText);
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
        incidents.addLast(
                new Incident(kind, subjectText, messageText, fixText, framesText, siteText));
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
        /** SH205 — not final: a repeat overwrites it with the latest occurrence's text. */
        private String message;
        private final String fix;
        /** SH190 — the throwing location, or null when there is no exception behind this kind. */
        private final String frames;
        /** SH204 — the site this belongs to, or null when it belongs to no one site. */
        private final String siteId;
        private final Instant firstSeen;
        private Instant lastSeen;
        private int count;

        private Incident(Kind kind, String subject, String message, String fix, String frames,
                         String siteId) {
            this(kind, subject, message, fix, frames, siteId, Instant.now(), Instant.now(), 1);
        }

        private Incident(Kind kind, String subject, String message, String fix, String frames,
                         String siteId, Instant firstSeen, Instant lastSeen, int count) {
            this.kind = kind;
            this.subject = subject;
            this.message = message;
            this.fix = fix;
            this.frames = frames;
            this.siteId = siteId;
            this.firstSeen = firstSeen;
            this.lastSeen = lastSeen;
            this.count = count;
        }

        /**
         * SH204 — the site is in the key, so the buffer folds exactly the way the table does.
         *
         * <p>Not a detail: the buffer is what is left when the store's write fails, which is most
         * likely when the database is itself the thing failing. A fallback that folds by a
         * different key than the record it stands in for reports a different set of problems in
         * precisely the situation nobody can cross-check it.</p>
         */
        private boolean matches(Kind otherKind, String otherSubject, String otherMessage,
                                String otherSiteId) {
            return kind == otherKind
                    && java.util.Objects.equals(subject, otherSubject)
                    // SH205 — on the *signature*, so a flapping target folds here exactly as it
                    // does in the table. Computed on both sides rather than stored, because this
                    // buffer is bounded at 50 and the comparison is cheaper than the field.
                    && java.util.Objects.equals(ShAgentIncidentSignature.of(message),
                            ShAgentIncidentSignature.of(otherMessage))
                    && java.util.Objects.equals(siteId, otherSiteId);
        }

        /** @param latest SH205 — the newest text wins, the same way the stored row's does */
        private void seenAgain(String latest) {
            count++;
            lastSeen = Instant.now();
            message = latest;
        }

        private Incident copy() {
            return new Incident(kind, subject, message, fix, frames, siteId, firstSeen, lastSeen,
                    count);
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

        /** SH204 — the site id, or null when this failure belongs to no one site. */
        public String siteId() {
            return siteId;
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
