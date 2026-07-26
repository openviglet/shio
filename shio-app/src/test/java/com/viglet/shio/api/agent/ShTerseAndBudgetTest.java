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

import org.junit.jupiter.api.Test;

/**
 * SH78 mechanics: the terse line grammar and the token budget. Network-free.
 *
 * @author Alexandre Oliveira
 */
class ShTerseAndBudgetTest {

    @Test
    void bareValuesAreUnquotedAndBlankAttributesVanish() {
        String line = ShTerse.line("node").word("folder").word("/blog")
                .attr("id", "f1").attr("title", "  ").attr("type", null).build();

        assertThat(line).isEqualTo("node folder /blog id=f1\n");
    }

    @Test
    void valuesWithSpacesOrQuotesAreQuotedAndEscaped() {
        String line = ShTerse.line("node").word("post").attr("title", "Hello World")
                .attr("note", "say \"hi\"").build();

        assertThat(line).isEqualTo("node post title=\"Hello World\" note=\"say \\\"hi\\\"\"\n");
    }

    @Test
    void newlinesInAValueCannotBreakTheLineFormat() {
        // A post title is user input; an unescaped newline would forge a new row.
        String line = ShTerse.line("node").attr("title", "a\nb").build();

        assertThat(line).isEqualTo("node title=\"a\\nb\"\n");
        assertThat(line.chars().filter(c -> c == '\n').count()).isEqualTo(1);
    }

    @Test
    void flagsAndZeroCountsCostNothingWhenAbsent() {
        assertThat(ShTerse.line("type").word("Page").flag("managed", false).count("posts", 0).build())
                .isEqualTo("type Page\n");
        assertThat(ShTerse.line("type").word("Page").flag("managed", true).count("posts", 3).build())
                .isEqualTo("type Page managed posts=3\n");
    }

    @Test
    void nullPositionalKeepsArityStable() {
        // A parser splits on position, so a missing word must still occupy its slot.
        assertThat(ShTerse.line("node").word(null).word("/x").build()).isEqualTo("node - /x\n");
    }

    @Test
    void estimateRoundsUpAndHandlesEmpty() {
        assertThat(ShTokenBudget.estimate(null)).isZero();
        assertThat(ShTokenBudget.estimate("")).isZero();
        assertThat(ShTokenBudget.estimate("abcd")).isEqualTo(1);
        assertThat(ShTokenBudget.estimate("abcde")).isEqualTo(2);
    }

    @Test
    void tryAddRefusesTheChunkThatWouldBreachAndFlagsExhaustion() {
        ShTokenBudget budget = new ShTokenBudget(2); // 8 chars

        assertThat(budget.tryAdd("12345678")).isTrue();
        assertThat(budget.tryAdd("9")).isFalse();
        assertThat(budget.exhausted()).isTrue();
        // The refused chunk is not charged, so spent still reflects what was emitted.
        assertThat(budget.spentTokens()).isEqualTo(2);
    }

    @Test
    void zeroLimitAcceptsEverythingButStillMeasuresIt() {
        ShTokenBudget budget = new ShTokenBudget(0);

        assertThat(budget.tryAdd("x".repeat(10_000))).isTrue();
        assertThat(budget.exhausted()).isFalse();
        assertThat(budget.overBudget()).isFalse();
        // Unbounded means "don't refuse", not "don't count": a budget=0 response must
        // still report its true size, or X-Shio-Est-Tokens silently under-reports.
        assertThat(budget.spentTokens()).isEqualTo(2500);
    }

    @Test
    void unconditionalAddCanOverrunAndReportsIt() {
        // Mandatory sections are added regardless; the overrun is reported, not hidden.
        ShTokenBudget budget = new ShTokenBudget(1);
        budget.add("x".repeat(400));

        assertThat(budget.overBudget()).isTrue();
        assertThat(budget.exhausted()).isFalse();
        assertThat(budget.spentTokens()).isEqualTo(100);
    }
}
