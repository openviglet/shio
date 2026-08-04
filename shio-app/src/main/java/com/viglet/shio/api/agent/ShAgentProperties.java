/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Tuning for the agent surface ({@code shio.agent.*}), Block H.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio.agent")
public class ShAgentProperties {

    private Context context = new Context();

    private Batch batch = new Batch();

    private Read read = new Read();

    private Verify verify = new Verify();

    private Render render = new Render();

    private Changes changes = new Changes();

    private Memory memory = new Memory();

    private Diagnostics diagnostics = new Diagnostics();

    /**
     * Durable incident limits (SH103, SH144).
     *
     * <p>Both numbers exist because an incident feed's failure mode is the opposite of a
     * listing's: too few rows and it answers "nothing failed" when something did, too many and
     * it is the log file an agent already could not read.</p>
     */
    @Setter
    @Getter
    public static class Diagnostics {

        /**
         * Distinct incidents kept. Rows are folded by what went wrong, so this is a count of
         * <em>problems</em>, not of occurrences — 500 distinct failures on one instance is
         * already a story worth reading rather than paging through.
         *
         * <p>Set to {@code 0} to keep everything, which is reasonable where an operator
         * prunes the table themselves and wants no policy in the application at all.</p>
         */
        private int maxRows = 500;

        /**
         * Incidents a {@code /agent/diagnostics} read returns by default. Far smaller than
         * what is retained: the question the endpoint answers is "what is broken now".
         */
        private int defaultLimit = 25;

        /** Ceiling on a caller-supplied limit. */
        private int maxLimit = 200;
    }

    /**
     * Instance-memory limits (SH114).
     *
     * <p>Every ceiling here is really a token ceiling: the notes are rendered into
     * the context pack, which every session reads before doing anything. Memory
     * that grows without bound would quietly undo the saving it exists to create —
     * a hundred remembered notes cost more per session than re-deriving three.</p>
     */
    @Setter
    @Getter
    public static class Memory {

        /**
         * Notes the listing returns, and the number the context pack carries. Small
         * on purpose: a convention that did not make the top 50 is not a convention,
         * it is a note somebody forgot to delete.
         */
        private int defaultLimit = 50;

        /** Ceiling on a caller-supplied limit for the listing endpoint. */
        private int maxLimit = 500;

        /** A key is an identifier, not a sentence. */
        private int maxKeyLength = 120;

        /**
         * A note is one instruction. The limit is enforced rather than advisory
         * because the alternative is discovering the cost only once the context pack
         * starts truncating its sitemap to make room for prose.
         */
        private int maxNoteLength = 2000;
    }

    /** Change-feed limits and its ordering guard (SH88). */
    @Setter
    @Getter
    public static class Changes {

        /**
         * Entries a feed returns when the caller names no limit. Larger than a {@code find}
         * default because a delta is read in full by definition — a caller that has to
         * page twice to catch up pays two round trips for one question.
         */
        private int defaultLimit = 50;

        /** Ceiling on a caller-supplied limit. */
        private int maxLimit = 500;

        /**
         * An <b>extra</b> margin, on top of the SH147 watermark, before the feed will report
         * an entry.
         *
         * <p>It used to be the ordering guard itself and a guess: SH51 stamps the audit row
         * inside the writer's transaction, so holding entries back for a second gave any
         * transaction that had stamped an earlier one that long to commit. A bulk apply of
         * hundreds of posts outran it, which is why the default is now <b>0</b> — the ceiling
         * is {@code ShAuditFeedWatermark.ceiling()}, the oldest write that has stamped and not
         * finished, and that is a fact rather than an estimate.</p>
         *
         * <p>What is left for this knob is the one thing an in-process registry cannot see: a
         * <b>second application node</b> writing to the same database, whose in-flight writes
         * this JVM knows nothing about. Set it there, to something above that node's slowest
         * write transaction. The feed discloses it in {@code notes} whenever it is non-zero.</p>
         */
        private long lagMillis = 0;
    }

    /** Verification-report limits (SH99, SH100). */
    @Setter
    @Getter
    public static class Verify {

        /**
         * Findings a report returns when the caller names no limit. Generous compared
         * with a listing default, because a report that hides defects is worse than a
         * long one — and the count of what was produced travels either way, so a cut is
         * never silent.
         */
        private int defaultLimit = 200;

        /** Ceiling on a caller-supplied limit. */
        private int maxLimit = 2000;

        /**
         * Pages the SH141 delivery check fetches in one run.
         *
         * <p>A ceiling exists because this is the only check that leaves the process: a
         * thousand-page site would otherwise turn one call into a thousand requests against
         * a front end that did not ask for them. What was skipped is reported in the
         * report's {@code notes} — a cut that is not visible reads as a clean pass (SH138).</p>
         */
        private int maxFetches = 50;

        /**
         * Wall-clock the whole delivery pass may spend fetching, in milliseconds.
         *
         * <p>The cap above bounds the <em>count</em>; this bounds the <em>time</em>, and the
         * two are not the same bound. Fifty pages behind a front end that answers in five
         * seconds each is four minutes, which is not a verification call — it is a hang. Past
         * this the pass stops and says how many pages it did not reach.</p>
         */
        private long fetchBudgetMillis = 15000;
    }

    /** Render-digest configuration (SH101). */
    @Setter
    @Getter
    public static class Render {

        /**
         * Whether {@code ?url=} may fetch a delivery URL. <b>Off by default:</b> an
         * endpoint that fetches on the server's behalf is a request-forgery surface, and
         * the authoring digest ({@code ?address=}) answers most of the question with no
         * network at all. When on, only the origins of readable sites are allowed.
         */
        private boolean allowFetch = false;

        /** Connect + read timeout for a fetch. A verification call must not hang a turn. */
        private int timeoutMillis = 5000;

        /**
         * Characters of a fetched response that are scanned. A digest of the first
         * ~512 KB of HTML is the same digest for any page a human would call a page.
         */
        private int maxBytes = 512 * 1024;

        /**
         * Pages a write's {@code ?digest=} proof will render when the caller names no count.
         *
         * <p>Ten, unchanged from SH194 — but ten for a reason now. SH264 measured a swept page at
         * <b>6 queries</b> (SH311 caches the presentation chain for the length of the read-only
         * transaction a proof runs in), so this default is ~60 queries of work behind one write,
         * which is the same order as the write itself.</p>
         */
        private int writeDigests = 10;

        /**
         * The ceiling the caller's own {@code ?digest=<n>} is clamped to.
         *
         * <p>An operator's decision, because it is a bound on work the server does on somebody
         * else's behalf. Clamped rather than refused: a caller asking for more than the instance
         * allows wants as many as it can have, and the cut is already reported as a row, so there
         * is nothing a 400 would tell it that the answer does not (SH138's rule that a cut is
         * visible).</p>
         */
        private int maxWriteDigests = 100;
    }

    /** Agent content-read limits (SH83's `find`). */
    @Setter
    @Getter
    public static class Read {

        /**
         * Rows a {@code find} returns when the caller names no limit. Small on purpose:
         * find exists to narrow, and a default that returns everything would make the
         * tool a listing endpoint by accident.
         */
        private int defaultLimit = 25;

        /** Ceiling on a caller-supplied limit. */
        private int maxLimit = 200;
    }

    /** Batch + plan/apply limits (SH81, SH82). */
    @Setter
    @Getter
    public static class Batch {

        /**
         * Ops allowed in one atomic batch. A ceiling exists because the whole batch is
         * one transaction: an unbounded one holds locks for as long as an agent is
         * willing to keep typing. Sized well above the "build a small site" case the
         * endpoint is for, so hitting it means the caller should be using plan/apply.
         */
        private int maxOps = 200;

        /**
         * Ops allowed in one plan/apply run, where the ops were <b>compiled from a
         * document</b> rather than typed by the caller (SH138).
         *
         * <p>It has to be a separate, larger number, and the reason is the deletion
         * plan. SH135 made {@code prune} sweep whole subtrees, so the op count of a
         * document is not a measure of how much the caller asked for — a three-post
         * document can compile to four hundred ops because the site holds four hundred
         * undeclared posts. Held to {@code maxOps}, that run failed with "split the work
         * into batches of at most 200 ops", which the caller cannot do: they never wrote
         * the ops, and there is no way to hand-split a converge. A legitimate large prune
         * had no path forward at all.</p>
         *
         * <p>So this is the "exempt the destructive rows from the budget" half of SH138 —
         * bounded rather than unbounded, because one transaction still holds locks, but
         * sized for what a real content tree prunes. Past it the run is <b>refused</b>
         * with a count and a way to narrow scope, never shortened.</p>
         */
        private int maxDocumentOps = 2000;
    }

    /** Context-pack defaults (SH77) and its token ceiling (SH78). */
    @Setter
    @Getter
    public static class Context {

        /**
         * Estimated-token ceiling for a context pack when the caller names none.
         * Sized so a typical instance fits in a few thousand tokens — the point of
         * the pack is to be cheap enough to re-read, not to be complete at any cost.
         */
        private int defaultBudgetTokens = 4000;

        /**
         * Hard cap on a caller-supplied {@code budget}. Stops a client from asking
         * for a response large enough to blow its own context window.
         */
        private int maxBudgetTokens = 40000;

        /** How deep the sitemap walks from the site root when no {@code depth} is given. */
        private int defaultDepth = 3;

        /**
         * Absolute row cap for the sitemap, applied in addition to the token budget:
         * a deep tree of tiny folders can stay under budget while still being useless.
         */
        private int maxNodes = 1000;
    }
}
