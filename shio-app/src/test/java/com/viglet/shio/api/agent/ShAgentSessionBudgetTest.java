/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH734 — the ceiling between the two that already existed.
 *
 * <h2>What nothing caught</h2>
 *
 * <p>{@code batch.maxOps} caps one batch and SH22/D's quota caps a tenant's total — the agent write
 * path has called {@code checkCanCreateSite} and {@code checkCanCreatePost} since it shipped. An
 * agent <b>looping</b> passes both: every batch is small and the tenant total is generous, and ten
 * thousand calls go through. SH108's confirm token stops a destructive op arriving as a typo;
 * nothing stopped a cheap op arriving ten thousand times.</p>
 *
 * <p>SH110 held this behind SH45's metering for its whole life, and SH734 measured that the blocker
 * only covered the storage third. This is the half that needed no meter, and the test is a unit one
 * for the same reason: a counter and a window have no database in them.</p>
 */
class ShAgentSessionBudgetTest {

    private ShAgentProperties properties;
    private ShAgentSessionBudget budget;

    @BeforeEach
    void setUp() {
        properties = new ShAgentProperties();
        budget = new ShAgentSessionBudget(properties);
    }

    @Test
    @DisplayName("SH734: a credential that keeps writing is refused once its window is spent")
    void theWindowIsSpentAndTheNextRunIsRefused() {
        properties.getBatch().setMaxSessionOps(10);

        budget.spend(6);
        assertThatCode(() -> budget.spend(4))
                .as("the ceiling is the last op that fits, not the one before it")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> budget.spend(1))
                .as("""
                        the whole of SH734: before it, an agent under the per-batch cap and under \
                        its tenant total could loop for ever and nothing counted.""")
                .isInstanceOf(ShAgentSessionBudget.SessionBudgetException.class);
    }

    @Test
    @DisplayName("SH734: the refusal says when to retry, because the request itself was fine")
    void theRefusalNamesTheInstantRatherThanAnEdit() {
        properties.getBatch().setMaxSessionOps(1);
        budget.spend(1);

        assertThatThrownBy(() -> budget.spend(1))
                .isInstanceOf(ShAgentSessionBudget.SessionBudgetException.class)
                .satisfies(failure -> {
                    var problem = ((ShAgentSessionBudget.SessionBudgetException) failure)
                            .problem().toProblemDetail();
                    assertThat(problem.getStatus())
                            .as("429, not 409: the state is not what refuses this, the history is")
                            .isEqualTo(429);
                    assertThat(String.valueOf(problem.getProperties().get("fix")))
                            .as("""
                                    Every other refusal here asks for a different request; this one \
                                    says the request was fine. A 429 with no reset instant is an \
                                    invitation to retry immediately, which is how a runaway becomes \
                                    a busy loop.""")
                            .contains("Wait until")
                            .contains("dry run costs nothing");
                    assertThat(problem.getDetail())
                            .as("and nothing was written, which is what makes it safe to act on")
                            .contains("Nothing in this run was written");
                });
    }

    @Test
    @DisplayName("SH734: off by default, so an existing install is not rate-limited by an upgrade")
    void zeroMeansNoCeiling() {
        assertThat(properties.getBatch().getMaxSessionOps())
                .as("""
                        the same shape Turing indexing takes. A default-on limit would be either \
                        generous enough to be theatre or tight enough to break a legitimate bulk \
                        run, and there is no measurement of what a real replication spends.""")
                .isZero();

        assertThatCode(() -> budget.spend(1_000_000)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SH734: a spent window reopens when it resets")
    void theWindowReopens() {
        properties.getBatch().setMaxSessionOps(1);
        // A window that has already elapsed by the time the second call reads it, so the reset is
        // observed without the test waiting an hour — the behaviour, not a sleep.
        properties.getBatch().setSessionWindow(Duration.ofNanos(1));

        budget.spend(1);
        assertThatCode(() -> budget.spend(1))
                .as("a fixed window ends; the guard is a runaway brake, not a permanent ban")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SH734: a refused run keeps its charge, so retrying cannot walk the limit")
    void arefusedRunIsNotRefunded() {
        properties.getBatch().setMaxSessionOps(5);
        budget.spend(5);

        assertThatThrownBy(() -> budget.spend(3))
                .isInstanceOf(ShAgentSessionBudget.SessionBudgetException.class);
        assertThatThrownBy(() -> budget.spend(1))
                .as("""
                        a refund would let a caller sit exactly at the ceiling and retry for ever, \
                        which is the loop this exists to stop rather than a caller to be fair to.""")
                .isInstanceOf(ShAgentSessionBudget.SessionBudgetException.class);
    }
}
