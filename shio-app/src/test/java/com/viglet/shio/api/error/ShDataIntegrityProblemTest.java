/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The other half of SH544: a database refusal that says something on the wire.
 *
 * <p>It used to be a 500 carrying Spring's default body, which is why the original defect was
 * diagnosed from the server log — nothing in the response named the column, the width, the op or
 * the address, and the CLI cannot print a field the server never sent (SH513). A 500 also tells an
 * agent to retry, and retrying is the one thing that cannot work when a constraint is what refused
 * the write.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShDataIntegrityProblemTest {

    /** SQLSTATE 22001 on H2, PostgreSQL and MariaDB alike. */
    private static final String TOO_LONG = "22001";

    @Test
    void anOverlongValueIsA400ThatNamesTheColumnAndTheOp() {
        ProblemDetail body = ShDataIntegrityProblem
                .of(refusal("Value too long for column \"DESCRIPTION CHARACTER VARYING(255)\"",
                        TOO_LONG), "post.upsert post:vilt/about")
                .toProblemDetail();

        assertThat(body.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.getType()).hasToString(ShProblem.TYPE_BASE + "value-too-long");
        assertThat(body.getDetail())
                .contains("post.upsert post:vilt/about")
                .contains("DESCRIPTION CHARACTER VARYING(255)");
        assertThat(body.getProperties()).containsKey("fix");
    }

    @Test
    void anyOtherConstraintIsA409() {
        ProblemDetail body = ShDataIntegrityProblem
                .of(refusal("Unique index or primary key violation: \"PUBLIC.FURL_IDX\"", "23505"),
                        "post.upsert post:vilt/about")
                .toProblemDetail();

        assertThat(body.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body.getType()).hasToString(ShProblem.TYPE_BASE + "data-integrity");
        assertThat(body.getProperties()).containsKey("fix");
    }

    /** No {@link SQLException} underneath — still a problem body, never a stack trace. */
    @Test
    void aRefusalWithNoSqlStateStillTeaches() {
        ProblemDetail body = ShDataIntegrityProblem
                .of(new DataIntegrityViolationException("could not execute statement"), null)
                .toProblemDetail();

        assertThat(body.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(body.getDetail()).contains("could not execute statement");
        assertThat(body.getProperties()).containsKey("fix");
    }

    /**
     * H2 quotes the offending <em>value</em> back at you. A rejected page body must not become
     * the response (P3), so the driver's message travels bounded and first-line-only.
     */
    @Test
    void theDriverMessageIsBoundedAndSingleLine() {
        ProblemDetail body = ShDataIntegrityProblem
                .of(refusal("Value too long: \"" + "x".repeat(20_000) + "\"\nSQL statement:\ninsert…",
                        TOO_LONG), null)
                .toProblemDetail();

        String detail = body.getDetail();
        assertThat(detail).doesNotContain("SQL statement:");
        assertThat(detail).hasSizeLessThan(400);
    }

    private static DataIntegrityViolationException refusal(String message, String sqlState) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException(message, sqlState));
    }
}
