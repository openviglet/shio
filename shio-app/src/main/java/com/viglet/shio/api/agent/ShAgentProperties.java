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
         * How long an entry is held back before the feed will report it.
         *
         * <p>SH51 writes the audit row inside the writer's transaction, so its timestamp
         * is assigned before the commit: a slow transaction can commit an older timestamp
         * after a reader has already moved its cursor past it. Waiting this long before
         * reporting an entry gives any transaction that already stamped an earlier one
         * that much time to commit. One second covers ordinary writes; a bulk apply that
         * runs longer can still be reported late, which the response says out loud.</p>
         *
         * <p>Set to {@code 0} to disable — appropriate only where writes are serialized
         * (a single-writer import) and never for the console-plus-agent case this feed
         * exists to reconcile.</p>
         */
        private long lagMillis = 1000;
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
