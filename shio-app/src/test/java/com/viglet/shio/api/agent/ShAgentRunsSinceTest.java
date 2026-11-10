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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.dto.ShAgentRunRow;

/**
 * SH1421 — "did the agent I just connected arrive?" is a read the connect panel can make.
 *
 * <p>{@code GET /agent/runs} answered only by session ids a caller already had, and a newcomer who
 * has just issued a key has none. {@code since} is the bounded other form: the runs that introduced
 * themselves after an instant, newest first, five at most.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
class ShAgentRunsSinceTest {

    @Autowired
    private ShAgentAPI shAgentAPI;
    @Autowired
    private ShAgentRunService shAgentRunService;

    @Test
    @DisplayName("SH1421: a run that introduced itself after the instant is answered, one before it is not")
    void sinceAnswersTheRunsThatArrivedAfterTheInstant() throws InterruptedException {
        String earlier = "sess-" + UUID.randomUUID();
        shAgentRunService.introduce(earlier, "older-client", "1.0", ShAgentRunService.MCP, "admin");
        Thread.sleep(5);
        Instant issued = Instant.now();
        Thread.sleep(5);
        String arrived = "sess-" + UUID.randomUUID();
        shAgentRunService.introduce(arrived, "claude-code", "2.1.0", ShAgentRunService.MCP, "admin");

        List<ShAgentRunRow> rows = shAgentAPI.runs(null, issued.toString());

        assertThat(rows).extracting(ShAgentRunRow::session)
                .as("SH1421: the run the panel waits for")
                .contains(arrived)
                .doesNotContain(earlier);
        assertThat(rows).hasSizeLessThanOrEqualTo(5);
        assertThat(rows.get(0).clientName()).isEqualTo("claude-code");
    }

    @Test
    @DisplayName("SH1421: neither session nor since is a teaching 400 naming both")
    void neitherFormIsATeachingRefusal() {
        assertThatThrownBy(() -> shAgentAPI.runs(null, null))
                .isInstanceOf(ShAgentAPI.RunsQueryException.class);
        assertThatThrownBy(() -> shAgentAPI.runs("", "yesterday"))
                .isInstanceOf(ShAgentAPI.RunsQueryException.class)
                .hasMessageContaining("yesterday");
    }
}
