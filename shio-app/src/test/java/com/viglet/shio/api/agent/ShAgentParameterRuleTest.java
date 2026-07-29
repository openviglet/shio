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
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH320 — one rule for what an unrecognised query-parameter value means.
 *
 * <blockquote><b>A parameter that selects or bounds work teaches. A parameter that only adds a
 * section does not.</b></blockquote>
 *
 * <p>Before the rule existed, one request to {@code /agent/batch} could carry four parameters with
 * four different answers: {@code digest} taught, {@code format} taught, {@code include} ignored, and
 * {@code verify}/{@code ids} took Spring's boolean binding — which reads {@code yes} as
 * <b>false</b>, silently. Three of the four answered a plausible response to a different question,
 * which is the failure P6 exists to prevent and the reason an unknown {@code format} has been a 400
 * since SH78.</p>
 *
 * <p>What the rule <em>keeps</em> is as important as what it changes. {@code include} stays lenient
 * because a typo there costs the caller a section it asked for and its own code notices;
 * {@code ids} stays lenient because it only adds a field to rows that are otherwise identical, and
 * {@code ?ids=1} is a caller being informal rather than wrong. Only {@code verify} moved, because it
 * makes the server run a whole lint pass — so {@code ?verify=yes} was a caller asking for proof,
 * getting silence, and reading a 200.</p>
 *
 * @author Alexandre Oliveira
 */
class ShAgentParameterRuleTest {

    @Test
    @DisplayName("a work-bounding flag reads true, false, 1 and 0 — and refuses the rest")
    void aWorkBoundingFlagTeaches() {
        assertThat(ShAgentFlag.of("true", "verify")).isTrue();
        assertThat(ShAgentFlag.of("TRUE", "verify")).isTrue();
        assertThat(ShAgentFlag.of("1", "verify"))
                .as("""
                        1 and 0 are accepted because Spring already accepted them here and a \
                        caller sending one means exactly one thing. Tightening that would break \
                        callers who are informal rather than wrong, which is not what this rule \
                        is for.""")
                .isTrue();
        assertThat(ShAgentFlag.of("false", "verify")).isFalse();
        assertThat(ShAgentFlag.of("0", "verify")).isFalse();
        assertThat(ShAgentFlag.of(null, "verify"))
                .as("absent is off, which is what every existing caller relies on")
                .isFalse();
    }

    @Test
    @DisplayName("`verify=yes` is a 400 naming the parameter, not a silent false")
    void anUnreadableFlagNamesItself() {
        Throwable failure = catchThrowable(() -> ShAgentFlag.of("yes", "verify"));

        assertThat(failure).isInstanceOf(ShAgentFlag.BadFlagException.class);
        var problem = ((ShAgentFlag.BadFlagException) failure).problem().toProblemDetail();
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail())
                .as("""
                        the refusal says which parameter and why it matters: `verify` decides \
                        whether the server runs a lint pass, so reading it as false answers a \
                        different question and looks like it worked.""")
                .contains("verify")
                .contains("extra work");
        assertThat(problem.getProperties().get("allowed").toString()).contains("true");
    }

    @Test
    @DisplayName("the section-adding parameters stay lenient, which is the other half of the rule")
    void aSectionAddingParameterDoesNotTeach() {
        // `include` is SH262's, and it only adds a section: a typo costs the caller an extra it
        // asked for, which its own code notices. Asserted here rather than left implicit, because
        // "make them all teach" is the obvious next step and it would be wrong — this is the test
        // that says so.
        assertThat(ShAgentDigestPages.wanted(null)).isFalse();
        assertThat(ShAgentDigestPages.count(null))
                .as("absent means the instance's default everywhere on this surface")
                .isNull();
    }
}
