/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.BeforeEach;

import com.viglet.shio.persistence.repository.agent.ShAgentIncidentRepository;

/**
 * SH205 — a flapping target is one row, and the prose is still the latest.
 *
 * <h2>What this is the assertion for</h2>
 *
 * <p>SH144's folding is what makes a durable incident table affordable: the row count tracks the
 * number of distinct <em>problems</em> rather than the traffic. The key was
 * {@code (kind, subject, message)} and the message is prose written for a human — <i>"gave up
 * after 3 attempt(s), last status 500"</i> — so the same broken webhook answering 503 on the next
 * publish was a different row.</p>
 *
 * <p>Folding therefore failed exactly where it mattered most. A flapping target is the
 * highest-volume case and the one whose every occurrence differs, so it filled the window with
 * near-duplicates and pushed unrelated incidents out through the cap — invisibly, because a
 * handful of near-identical rows is what a report would look like if there really were several
 * problems.</p>
 *
 * <p>The two halves are asserted together on purpose, because each is what makes the other
 * defensible: <b>the signature folds</b> (so the row count is a problem count) and <b>the message
 * is the most recent occurrence</b> (so the detail the key discards is not lost, which is the
 * standing objection to normalizing at all).</p>
 *
 * <p>Its own database, for {@code ShAgentDurableIncidentTest}'s reason: these assertions count
 * rows in the whole table.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-incident-signature;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
class ShAgentIncidentSignatureTest {

    private static final String HOOK = "https://flapping.example.com/hook";

    @Autowired
    private ShAgentIncidentLog incidentLog;
    @Autowired
    private ShAgentIncidentRepository repository;

    @BeforeEach
    void reset() {
        incidentLog.clear();
    }

    @Test
    @DisplayName("a flapping target is one row, not one row per status code")
    void aFlappingTargetFolds() {
        giveUp(3, 500);
        giveUp(5, 503);
        giveUp(2, 502);

        assertThat(repository.findAll())
                .as("""
                        the highest-volume case and the one every occurrence of which differs. \
                        Before SH205 this was three rows, and three rows is what a report would \
                        look like if there really were three problems — so nothing showed that the \
                        folding the cap depends on had stopped working on the one target that \
                        needed it.""")
                .singleElement()
                .satisfies(row -> assertThat(row.getOccurrences()).isEqualTo(3));
    }

    @Test
    @DisplayName("the folded row's message is the most recent occurrence, not the first")
    void theMessageIsTheLatest() {
        giveUp(3, 500);
        giveUp(9, 418);

        assertThat(repository.findAll())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getMessage())
                            .as("""
                                    the half that makes a lossy signature safe. A reader asking \
                                    "what is it doing now" wants the current status code, and the \
                                    detail the key strips is still here at its latest value — so a \
                                    normalization that folds two things is one row whose message is \
                                    the newest of them, never a row that hid the other's text. \
                                    Unlike `fix` and `frames`, which keep the FIRST value they were \
                                    given because a later repeat arriving without them must not \
                                    erase what the first one knew.""")
                            .isEqualTo("gave up after 9 attempt(s), last status 418");
                    assertThat(row.getSignature())
                            .as("and the key itself has no digits left to vary")
                            .isEqualTo("gave up after # attempt(s), last status #");
                });
    }

    @Test
    @DisplayName("two genuinely different causes on one subject stay two rows")
    void differentCausesDoNotFold() {
        giveUp(3, 500);
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, HOOK,
                "dispatch errored: connection reset", null, null, null);

        assertThat(repository.findAll())
                .as("""
                        the normalization stops at digits. A subscriber that refuses a delivery and \
                        a connection that never completed are different problems with different \
                        remedies, and they share a kind, a subject and a site — so if anything were \
                        going to over-fold, this is the shape it would take.""")
                .hasSize(2);
    }

    @Test
    @DisplayName("case survives, and normalizing twice is a no-op")
    void theNormalizerStopsWhereItShould() {
        assertThat(ShAgentIncidentSignature.of("NullPointerException at line 42"))
                .as("""
                        lowercasing would fold an exception class name into any sentence fragment \
                        that matched it, and no message here varies by case.""")
                .isEqualTo("NullPointerException at line #");

        String once = ShAgentIncidentSignature.of("status 500 after 3s");
        assertThat(ShAgentIncidentSignature.of(once))
                .as("the placeholder is not a digit, so a signature is its own signature — which "
                        + "is what lets the buffer compare stored text to incoming text")
                .isEqualTo(once);

        assertThat(ShAgentIncidentSignature.of("line: 1,\n  column: 5"))
                .as("a wrapped exception message carries a newline into the middle")
                .isEqualTo("line: #, column: #");

        assertThat(ShAgentIncidentSignature.of(null))
                .as("null, not empty — an empty signature would fold every message-less incident "
                        + "into one row")
                .isNull();
    }

    private void giveUp(int attempts, int status) {
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, HOOK,
                "gave up after " + attempts + " attempt(s), last status " + status,
                "Check the target is up.", null, null);
    }
}
