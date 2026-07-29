/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import com.viglet.shio.exchange.ShExchangeException;
import com.viglet.shio.exchange.ShExchangeException.Kind;

/**
 * SH349 — an export failure the caller can fix teaches; the rest stay the instance's own 500.
 *
 * <p>SH189 removed the bodiless 400 from import and left the same exception unhandled on the way out,
 * so {@code GET /api/v2/site/{id}/export} answered a 500 with no slug. SH267's kinds made the
 * distinction cheap, and it is a distinction rather than a wrapper: of the three export-side kinds
 * only one — a post whose {@code data} is not JSON — is something a caller can act on.</p>
 *
 * <p>No Spring context: the classification is a pure function of the exception.</p>
 *
 * @author Alexandre Oliveira
 */
class ShExportFailedExceptionTest {

    /**
     * The one kind the caller owns. Held as a set rather than asserted case by case so the
     * parameterized test below can say something about <em>every</em> kind, including the ones added
     * after this was written.
     */
    private static final Set<Kind> THE_CALLER_S = EnumSet.of(Kind.POST_DATA_INVALID);

    private static String fixOf(ShExportFailedException thrown) {
        Object fix = thrown.toProblemDetail().getProperties().get("fix");
        return fix == null ? "" : fix.toString();
    }

    @Test
    @DisplayName("an unparseable data column is a 409 naming the post's address")
    void anUnparseableDataColumnNamesThePost() {
        ShExportFailedException thrown = ShExportFailedException.forCaller(
                ShExchangeException.about(Kind.POST_DATA_INVALID, "post:acme/about",
                        "Failed to parse post data JSON",
                        new IllegalArgumentException("Unexpected character ('n' (code 110))")))
                .orElseThrow();

        assertThat(thrown.problem().slug()).isEqualTo("export-post-data-invalid");
        assertThat(thrown.problem().status())
                .as("""
                        409 and not 400: the request is correct and the stored content is not, which \
                        is SH321's and SH325's precedent. A 400 would say the caller sent something \
                        wrong, and they sent a site id.""")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(thrown.toProblemDetail().getDetail())
                .as("the address is what a reader acts with, and the parser's own message says where "
                        + "in the column it gave up — SH189's rule that the wrapper alone is not enough")
                .contains("post:acme/about")
                .contains("code 110");
        assertThat(thrown.toProblemDetail().getProperties().get("at"))
                .as("SH81's field: which part of the export failed, in the caller's own vocabulary")
                .isEqualTo("post:acme/about");
        assertThat(fixOf(thrown))
                .as("P6 — the action, and it is runnable: read that post, rewrite its fields")
                .contains("/api/v2/agent/read?address=post:acme/about")
                .contains("post.upsert");
    }

    @Test
    @DisplayName("a failure that names no post still reads, and its fix stays runnable")
    void aFailureWithNoSubjectStillReads() {
        ShExportFailedException thrown = ShExportFailedException.forCaller(
                new ShExchangeException(Kind.POST_DATA_INVALID, "Failed to parse post data JSON"))
                .orElseThrow();

        assertThat(thrown.toProblemDetail().getDetail())
                .as("`The export stopped at null` is the kind of detail that makes a reader distrust "
                        + "the rest of the message")
                .doesNotContain("null")
                .contains("a post in this site");
        assertThat(fixOf(thrown))
                .as("the example address stands in for the one nobody knew, so the fix is still a "
                        + "call somebody can adapt rather than a sentence with a hole in it")
                .contains("post:<site>/<url>");
        assertThat(thrown.toProblemDetail().getProperties())
                .as("`at` points into the request, so absent beats a placeholder pointing nowhere")
                .doesNotContainKey("at");
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("SH349: a kind teaches only if the caller can act on it")
    void aKindTeachesOnlyIfTheCallerCanActOnIt(Kind kind) {
        Optional<ShExportFailedException> teaching = ShExportFailedException.forCaller(
                ShExchangeException.about(kind, "post:acme/about", "a failure of kind " + kind, null));

        assertThat(teaching.isPresent())
                .as("""
                        %s. The switch has no default, so a kind added later must be decided here \
                        rather than defaulting into a teaching 409 that blames the caller for the \
                        instance's disk — or into silence. Empty means the endpoint rethrows, which \
                        is a 500 SH190's filter already records with frames.""", kind)
                .isEqualTo(THE_CALLER_S.contains(kind));
        teaching.ifPresent(thrown -> {
            assertThat(thrown.problem().slug()).startsWith("export-");
            assertThat(fixOf(thrown)).as("P6 — every 4xx carries a fix: %s", kind).isNotBlank();
        });
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("SH349: rewording a message cannot change the outcome")
    void rewordingAMessageCannotChangeTheOutcome(Kind kind) {
        // SH267's property, restated on this side of the exchange: the message is presentational.
        String misleading = "manifest zip no site post data - reworded for the log";

        assertThat(ShExportFailedException.forCaller(
                        new ShExchangeException(kind, misleading)).isPresent())
                .as("%s", kind)
                .isEqualTo(ShExportFailedException.forCaller(
                        new ShExchangeException(kind, "the original wording")).isPresent());
    }
}
