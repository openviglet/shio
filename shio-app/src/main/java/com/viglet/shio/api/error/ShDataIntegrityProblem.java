/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.error;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

/**
 * Turns a database refusal into a teaching problem (SH544).
 *
 * <h2>Why this exists</h2>
 *
 * <p>A write the database rejects used to leave the boundary as an unhandled
 * {@code DataIntegrityViolationException}: a 500 carrying Spring's default body, which names
 * neither the constraint, nor the column, nor the operation. SH544 was diagnosed from the server
 * log for exactly that reason — a 201-page conversion returned nothing after uploading 403 assets,
 * and nothing on the wire said why. The CLI already prints whatever the body carries (SH513); it
 * cannot invent a field the server never sent.</p>
 *
 * <p>One mapping, used by both boundaries that see these failures — {@link ShApiExceptionHandler}
 * for a single write and {@code ShAgentBatchService} for one op inside a batch — because a rule in
 * two places is two places that can disagree about what a refusal means.</p>
 *
 * <h2>Two statuses, from the SQLSTATE</h2>
 *
 * <p>{@code 22001} (<i>string data, right truncation</i>) is a value the caller sent being wider
 * than its column, which is a 400: the request is answerable, just not as written. Everything else
 * — a unique key, a foreign key, a not-null — is a 409, because the request is well-formed and the
 * <em>state</em> is what refuses it. Both are a change from the 500 they used to be, which is the
 * point: a 500 tells an agent to retry, and retrying is the one thing that cannot work here.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShDataIntegrityProblem {

    /** SQLSTATE for "string data, right truncation" — the same code on H2, PostgreSQL and MariaDB. */
    private static final String VALUE_TOO_LONG = "22001";

    /**
     * How much of the driver's message travels. Enough for every dialect's phrasing of "column X
     * takes N characters"; short enough that H2 — which quotes the offending <em>value</em> back —
     * cannot turn a rejected page body into the response (P3).
     */
    private static final int MESSAGE_BUDGET = 240;

    private ShDataIntegrityProblem() {
    }

    /**
     * @param failure the refusal, as Spring translated it
     * @param subject what was being written, in the caller's vocabulary — an op and an address
     *                ({@code post.upsert post:vilt/about}), or {@code null} when the boundary
     *                does not know. This is the field whose absence made SH544 a log-reading
     *                exercise, so a caller that <em>can</em> name the op should
     */
    public static ShProblem of(DataAccessException failure, String subject) {
        String message = message(failure);
        boolean tooLong = VALUE_TOO_LONG.equals(sqlState(failure));
        String wrote = subject == null || subject.isBlank() ? "" : " while running " + subject;
        if (isAddressAlreadyTaken(failure)) {
            return addressTaken(subject);
        }
        return tooLong
                ? ShProblem.badRequest("value-too-long", "A value is wider than its column")
                        .withDetail("The database refused this write" + wrote + ": " + message)
                        .withFix("Shorten the value the message names and retry. If it is a value "
                                + "Shio derives rather than one you sent, this is a defect — report "
                                + "it with this message.")
                : ShProblem.of("data-integrity", HttpStatus.CONFLICT,
                                "The database refused this write")
                        .withDetail("A constraint rejected it" + wrote + ": " + message)
                        .withFix("Read the current state (GET /api/v2/agent/read) and retry against "
                                + "it — the request is well-formed, so repeating it unchanged will "
                                + "be refused again.");
    }

    /**
     * SH719's index. Named in the driver's message by all three engines — H2 quotes
     * {@code PUBLIC.UK_POST_UNIFIED_URL}, PostgreSQL {@code unique constraint "uk_post_unified_url"},
     * MariaDB {@code for key 'uk_post_unified_url'} — so one case-insensitive test covers them.
     */
    private static final String ADDRESS_INDEX = "uk_post_unified_url";

    /** Whether the database refused this write because the address was already occupied (SH719). */
    private static boolean isAddressAlreadyTaken(DataAccessException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains(ADDRESS_INDEX)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * The one refusal on this surface whose right answer is <b>retry</b> (SH719).
     *
     * <h2>Why it is not the generic conflict above</h2>
     *
     * <p>Every other constraint here is a request that cannot succeed as written, so the generic
     * text says so: <i>"repeating it unchanged will be refused again."</i> This one is the exact
     * opposite and would be actively misleading — the write lost a race for an address that now
     * <em>exists</em>, and {@code post.upsert} is an upsert, so repeating it unchanged resolves the
     * winner and updates it. An agent that read the generic sentence would abandon the one action
     * that works.</p>
     *
     * <h2>What it replaces</h2>
     *
     * <p>Two connections creating one address both answered {@code changed=1} and the site was left
     * with two posts on one friendly URL — an address no write could name and no read could resolve,
     * removable only by id, with both callers told they had won. One racer now loses <em>and is told
     * so</em>, which is the whole difference: a refusal somebody can act on, instead of a success
     * nobody has a reason to check.</p>
     *
     * <p>The URL is not read back out of the index — it is a digest (SH719 explains why) — so the
     * address comes from {@code subject}, which is the caller's own op and address. Where the
     * boundary could not name one, the refusal says what happened without pretending to know which
     * address it was.</p>
     */
    private static ShProblem addressTaken(String subject) {
        boolean named = subject != null && !subject.isBlank();
        return ShProblem.of("duplicate-url", HttpStatus.CONFLICT,
                        "That address was taken while this write was in flight")
                .withDetail((named ? subject + " lost a race: another" : "Another")
                        + " write created a post at this friendly URL between the moment this one "
                        + "resolved the address and the moment it inserted. Only one post may hold "
                        + "an address, so the database refused the second — nothing was written.")
                .withFix("Retry the same op. The address exists now, so post.upsert resolves it and "
                        + "updates the post that won instead of creating a second one. If you meant "
                        + "a different post, give it its own URL first (post.move).");
    }

    /** The five-character SQLSTATE of the underlying {@link SQLException}, or {@code null}. */
    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState();
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return null;
    }

    /**
     * The driver's own first line, bounded. Deliberately the driver's words and not a
     * re-description of them: the column name and the width live only in that string, and every
     * supported database phrases it differently.
     */
    private static String message(DataAccessException failure) {
        String message = failure.getMostSpecificCause().getMessage();
        if (message == null || message.isBlank()) {
            return "the driver gave no message.";
        }
        String line = message.strip();
        int newline = line.indexOf('\n');
        if (newline > 0) {
            line = line.substring(0, newline).strip();
        }
        return line.length() <= MESSAGE_BUDGET ? line : line.substring(0, MESSAGE_BUDGET - 1) + "…";
    }
}
