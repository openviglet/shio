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
