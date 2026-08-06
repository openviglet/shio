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
