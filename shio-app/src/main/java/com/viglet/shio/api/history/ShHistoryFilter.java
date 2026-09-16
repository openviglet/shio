/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import java.util.Locale;

/**
 * Every narrowing the audit trail can be read through (SH1005).
 *
 * <h2>Why one record rather than six parameters</h2>
 *
 * <p>Two surfaces read this table — the agent's change feed and the console's Activity page —
 * and before SH1005 only one of them could narrow anything. Growing the service's parameter
 * list to close that would have made a nine-argument call that is easy to pass in the wrong
 * order and impossible to read at the call site; the repository pays that cost because a JPQL
 * query has no other shape, and the service, the two controllers and the console's request do
 * not have to.</p>
 *
 * <h2>Every narrowing rides with the keyset, never after the page</h2>
 *
 * <p>These are query predicates, not a filter over what came back. A post-filter hands back a
 * short page — or an empty one — while the rows the caller asked for sit behind the cursor, and
 * a caller that pages reads that as "nothing else happened". That is the defect SH1005 closed on
 * the console side, and stating it here is what keeps a later narrowing from being added the
 * cheap way.</p>
 *
 * @param by       {@code agent} for every run's writes, {@code curator} for every person's — a
 *                 console session is one of theirs (SH996) — or a session id for one run
 * @param decision SH1002 — only the changes a curator decided one way: {@code APPROVE},
 *                 {@code REJECT} or {@code HOLD}
 * @param action   one transition kind: {@code CREATE}, {@code UPDATE}, {@code PUBLISH},
 *                 {@code UNPUBLISH}, {@code DELETE} or {@code RESTORE}
 * @param actor    one principal's rows. An agent acts <em>as</em> somebody, so this and
 *                 {@code by} are different questions and the page needs both (SH999)
 * @param subject  {@code POST}, {@code FOLDER} or {@code SITE} (SH196). A row written before
 *                 that column existed is a post, and asking for posts returns it
 * @param object   one object's whole trail — what the post editor's history panel asks for
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShHistoryFilter(String by, String decision, String action, String actor,
                              String subject, String object) {

    /** Every row in scope: what every caller from SH88 to SH1004 asks for. */
    public static final ShHistoryFilter ALL =
            new ShHistoryFilter(null, null, null, null, null, null);

    public ShHistoryFilter {
        by = trimmed(by);
        decision = upper(decision);
        action = upper(action);
        actor = trimmed(actor);
        subject = upper(subject);
        object = trimmed(object);
    }

    /** The feed's two SH999/SH1002 narrowings, which is what every caller before SH1005 passed. */
    public static ShHistoryFilter of(String by, String decision) {
        return new ShHistoryFilter(by, decision, null, null, null, null);
    }

    /** One object's whole trail, in date order — the post editor's panel, and a read-back. */
    public static ShHistoryFilter ofObject(String objectId) {
        return new ShHistoryFilter(null, null, null, null, null, objectId);
    }

    /**
     * {@code agent} or {@code curator}, or {@code null} when {@code by} names a run.
     *
     * <p>A word or an id, told apart here rather than in the query: those two are the whole
     * vocabulary, and anything else is a session — the one case that needs no list of valid
     * values, because a session nobody wrote under is an empty page rather than a refusal.</p>
     */
    public String kind() {
        return "agent".equalsIgnoreCase(by) || "curator".equalsIgnoreCase(by)
                ? by.toLowerCase(Locale.ROOT)
                : null;
    }

    /** The one run {@code by} names, or {@code null} when it names a kind or nothing. */
    public String session() {
        return by == null || kind() != null ? null : by;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private static String upper(String value) {
        String clean = trimmed(value);
        return clean == null ? null : clean.toUpperCase(Locale.ROOT);
    }
}
