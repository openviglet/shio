/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentRunService;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.review.ShReviewDecision;
import com.viglet.shio.persistence.repository.agent.ShAgentRunRepository;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.review.ShReviewDecisionRepository;

/**
 * SH1423 — the getting-started steps are read from the instance, not from a browser.
 *
 * <p>Each step is driven at its edge: an agent's update is not a person's edit, a console publish
 * session is not an agent, and each step turns true from the row that means it. The three tables the
 * steps read are emptied inside one transaction that is rolled back, so the test starts from an
 * instance nobody has touched without a Spring context of its own (SH423: there is no slot for one)
 * and leaves the shared database as it found it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
class ShConsoleSummaryStartTest {

    @Autowired
    private ShConsoleSummaryAPI api;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private ShReviewDecisionRepository shReviewDecisionRepository;
    @Autowired
    private ShAgentRunRepository shAgentRunRepository;
    @Autowired
    private ShAgentRunService shAgentRunService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("SH1423: each getting-started step turns true from the row that means it, and only that")
    void eachStepIsReadFromTheInstance() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            shHistoryRepository.deleteAllInBatch();
            shReviewDecisionRepository.deleteAllInBatch();
            shAgentRunRepository.deleteAllInBatch();

            ShConsoleSummary.Start before = api.summary().start();
            assertThat(before.edited()).as("no person's edit yet").isFalse();
            assertThat(before.agent()).as("no agent yet").isFalse();
            assertThat(before.reviewed()).as("no review decision yet").isFalse();

            // A console publish session is a person, not an agent.
            write("PUBLISH", "console:curator:2026-11-10");
            assertThat(api.summary().start().agent())
                    .as("SH1423: a console session counts as an agent").isFalse();

            // An agent that only introduced itself has connected.
            shAgentRunService.introduce("sess-" + UUID.randomUUID(), "claude-code", "2.1.0",
                    ShAgentRunService.MCP, "admin");
            assertThat(api.summary().start().agent())
                    .as("SH1423: an introduced run is not a connected agent").isTrue();

            // An agent's update is the agent's, and a person's is the person's.
            write("UPDATE", "sess-" + UUID.randomUUID());
            assertThat(api.summary().start().edited())
                    .as("SH1423: an agent's update ticks the person's edit").isFalse();
            write("UPDATE", null);
            assertThat(api.summary().start().edited())
                    .as("SH1423: a person's update is not an edit").isTrue();

            ShReviewDecision decision = new ShReviewDecision();
            decision.setSession("sess-" + UUID.randomUUID());
            decision.setPostId(UUID.randomUUID().toString());
            decision.setDecision("HOLD");
            decision.setActor("curator");
            decision.setDecidedAt(Instant.now());
            shReviewDecisionRepository.save(decision);
            assertThat(api.summary().start().reviewed())
                    .as("SH1423: a review decision is not a review").isTrue();
        });
    }

    private void write(String action, String session) {
        ShHistory row = new ShHistory();
        row.setAction(action);
        row.setSubjectKind("POST");
        row.setShObject(UUID.randomUUID().toString());
        row.setOwner("someone");
        row.setDate(Instant.now());
        row.setAgentSessionId(session);
        shHistoryRepository.saveAndFlush(row);
    }
}
