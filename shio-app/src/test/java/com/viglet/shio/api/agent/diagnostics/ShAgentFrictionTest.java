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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The friction ranking, which replaces a human reading a transcript back (SH602).
 *
 * <h2>What it is for</h2>
 *
 * <p>Twelve rounds produced roughly forty tasks and every one came from the same place: a session
 * ended, somebody read it back, and a pattern was noticed. That collector is expensive, lossy, and
 * only runs when a human asks — so it does not run at all for readers who are not this one.</p>
 *
 * <p>None of what it records is a failure. A 404 on a path an agent constructed is a correct
 * answer; a {@code didYouMean} that fired is the surface working. Each is the instance behaving
 * properly while a reader does not get what they came for, which is exactly why the incident log
 * never held any of it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShAgentFrictionTest {

    @Test
    @DisplayName("SH602: the ranking is by what was asked for, most-asked first")
    void theRankingIsByWhatWasAskedFor() {
        ShAgentFrictionLog log = new ShAgentFrictionLog();
        log.record("unknown-post-type", "Artcle");
        log.record("unknown-post-type", "Artcle");
        log.record("unknown-post-type", "Artcle");
        log.record("unknown-field", "HEADLINE");

        assertThat(log.top(10))
                .as("""
                        a name asked for often and answered never is a missing capability with a \
                        name attached — and the name is the READER'S, which is strictly better \
                        evidence than a transcript review, where a summariser writes the name they \
                        would have used (SH602)""")
                .extracting(ShAgentFrictionLog.Friction::name)
                .containsExactly("Artcle", "HEADLINE");
        assertThat(log.top(10).get(0).count()).isEqualTo(3);
    }

    @Test
    @DisplayName("SH602: a didYouMean that fired is a different finding from one that did not")
    void aNearMissIsItsOwnKind() {
        // The distinction the exception handler draws, and it is not decorative: "asked for a
        // near-miss of something real" is a NAMING problem, and "asked for something with no
        // neighbour at all" is a missing capability. Folding them would rank them together and
        // answer neither question.
        ShAgentFrictionLog log = new ShAgentFrictionLog();
        log.record("unknown-post-type", "Artcle");
        log.record("unknown-post-type+didYouMean", "Artcle");

        assertThat(log.top(10))
                .hasSize(2)
                .extracting(ShAgentFrictionLog.Friction::kind)
                .containsExactlyInAnyOrder("unknown-post-type", "unknown-post-type+didYouMean");
    }

    @Test
    @DisplayName("SH602: ties break on the name, so two reads of one instance print one order")
    void theOrderIsStable() {
        ShAgentFrictionLog log = new ShAgentFrictionLog();
        for (String name : List.of("zebra", "alpha", "mango")) {
            log.record("unknown-name", name);
        }

        assertThat(log.top(10))
                .as("a ranking that reshuffles between reads is one nobody can diff (P5)")
                .extracting(ShAgentFrictionLog.Friction::name)
                .containsExactly("alpha", "mango", "zebra");
    }

    @Test
    @DisplayName("SH602: past the ceiling it stops admitting names and says how many it dropped")
    void theCeilingIsReported() {
        ShAgentFrictionLog log = new ShAgentFrictionLog();
        for (int index = 0; index < ShAgentFrictionLog.MAX_KEYS + 25; index++) {
            log.record("unknown-name", "name-" + index);
        }

        assertThat(log.top(1000)).hasSize(ShAgentFrictionLog.MAX_KEYS);
        assertThat(log.dropped())
                .as("""
                        a counter that silently stops counting reads exactly like a quiet instance \
                        — and unbounded growth keyed by a path an attacker chooses is a denial of \
                        service, so the ceiling is not optional (SH602)""")
                .isEqualTo(25);
    }

    @Test
    @DisplayName("SH602: a name already held still counts past the ceiling")
    void anExistingNameIsStillCounted() {
        // The ceiling bounds DISTINCT names, not occurrences. If it bounded occurrences, the
        // ranking would freeze the moment it filled and the most-asked name would stop being the
        // most-asked — which is the one thing the report is read for.
        ShAgentFrictionLog log = new ShAgentFrictionLog();
        log.record("unknown-name", "first");
        for (int index = 0; index < ShAgentFrictionLog.MAX_KEYS + 10; index++) {
            log.record("unknown-name", "filler-" + index);
        }
        log.record("unknown-name", "first");
        log.record("unknown-name", "first");

        assertThat(log.top(1))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.name()).isEqualTo("first");
                    assertThat(row.count()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("SH602: a blank name records nothing, so an empty key cannot top the list")
    void aBlankNameIsNotRecorded() {
        ShAgentFrictionLog log = new ShAgentFrictionLog();
        log.record("unknown-name", "");
        log.record("unknown-name", "   ");
        log.record(null, "something");
        log.record("unknown-name", null);

        assertThat(log.top(10)).isEmpty();
    }
}
