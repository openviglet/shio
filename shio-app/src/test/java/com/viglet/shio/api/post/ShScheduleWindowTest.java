/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH991 — which scheduled posts a listing asks for, in one value.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShScheduleWindowTest {

    private static final Instant MAY_1 = Instant.parse("2031-05-01T00:00:00Z");
    private static final Instant MAY_2 = Instant.parse("2031-05-02T00:00:00Z");

    @Test
    @DisplayName("SH991: nothing sent is no filter, and any matches either schedule")
    void anyAndNothing() {
        assertThat(ShScheduleWindow.parse(null)).isNull();
        assertThat(ShScheduleWindow.parse(" ")).isNull();
        assertThat(ShScheduleWindow.parse("ANY").matches(null, MAY_1)).isTrue();
        assertThat(ShScheduleWindow.parse("any").matches(null, null))
                .as("SH991: a post with no schedule is not scheduled, whatever the window")
                .isFalse();
    }

    @Test
    @DisplayName("SH991: a range is [from, to), and either end may be left open")
    void aHalfOpenRange() {
        ShScheduleWindow day = ShScheduleWindow.parse("2031-05-01T00:00:00Z..2031-05-02T00:00:00Z");

        assertThat(day.matches(MAY_1, null)).isTrue();
        assertThat(day.matches(MAY_2, null))
                .as("SH991: exclusive at the top, so consecutive windows never count a post twice")
                .isFalse();
        assertThat(ShScheduleWindow.parse("..2031-05-02T00:00:00Z").matches(null, MAY_1)).isTrue();
        assertThat(ShScheduleWindow.parse("2031-05-02T00:00:00Z..").matches(MAY_1, null)).isFalse();
    }

    @Test
    @DisplayName("SH991: a value outside the grammar is refused, never read as no filter")
    void outsideTheGrammar() {
        for (String sent : new String[] { "tomorrow", "2031-05-01", "a..b",
                "2031-05-02T00:00:00Z..2031-05-01T00:00:00Z", "..x..", "2031-05-01T00:00:00Z" }) {
            assertThatThrownBy(() -> ShScheduleWindow.parse(sent))
                    .as("SH991: '%s'", sent)
                    .isInstanceOf(ShScheduleWindow.UnknownWindowException.class);
        }
    }
}
