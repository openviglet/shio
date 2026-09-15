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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentVerdict;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.persistence.model.review.ShReviewDecision;
import com.viglet.shio.persistence.repository.review.ShReviewDecisionRepository;

/**
 * SH1002 — the verdict is readable by the one it is about, and by nothing else.
 *
 * <h2>The decision this must not undo</h2>
 *
 * <p>"The agent never approves, holds or rejects its own work" is a standing non-goal, and the
 * review surface stays off {@code /agent/**} because of it. This task adds the opposite direction —
 * a read — and the two assertions that keep the first decision intact are that the manifest's
 * endpoint index still has no approve route, and that the new resource exposes no path that
 * decides anything.</p>
 *
 * <h2>And scoped, which is the second half</h2>
 *
 * <p>A rejection's note is written for whoever is going to fix the page. A credential that did not
 * write the run has no business reading what a curator said about somebody else's work, so the read
 * answers about runs matching the caller's own principal and about no others.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentReviewReadOnlyTest {

    private static final String MINE = "sh1002-mine-" + UUID.randomUUID().toString().substring(0, 8);
    private static final String THEIRS = "sh1002-theirs-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private ShAgentVerdictService shAgentVerdictService;
    @Autowired
    private ShAgentRunService shAgentRunService;
    @Autowired
    private ShAgentManifestService shAgentManifestService;
    @Autowired
    private ShReviewDecisionRepository shReviewDecisionRepository;
    @Autowired
    private List<ShMcpResource> resources;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    void seed() {
        shAgentRunService.introduce(MINE, "claude-code", "1.0", ShAgentRunService.MCP, "ana");
        shAgentRunService.introduce(THEIRS, "claude-code", "1.0", ShAgentRunService.MCP, "bruno");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            decision(MINE, "sh1002-post-a", "the price table is last quarter's");
            decision(THEIRS, "sh1002-post-b", "bruno's agent got the tone wrong");
        });
    }

    @AfterAll
    void tearDown() {
        SecurityContextHolder.clearContext();
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shReviewDecisionRepository.findBySessionIn(List.of(MINE, THEIRS))
                        .forEach(shReviewDecisionRepository::delete));
    }

    private void decision(String session, String postId, String note) {
        ShReviewDecision row = new ShReviewDecision();
        row.setSession(session);
        row.setPostId(postId);
        row.setDecision(ShReviewDecision.Kind.REJECT.name());
        row.setNote(note);
        row.setActor("curator");
        row.setDecidedAt(Instant.now());
        shReviewDecisionRepository.save(row);
    }

    private void as(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    @DisplayName("SH1002: a rejection's reason is readable by the credential that wrote the run")
    void theRunsOwnCredentialReadsIt() {
        as("ana");

        List<ShAgentVerdict> mine = shAgentVerdictService.mine("reject", null);

        assertThat(mine).extracting(ShAgentVerdict::note)
                .as("SH1002: a revert leaves ordinary restore and delete rows that look like a "
                        + "curator's own edit, so every correction needed a person to re-explain it")
                .contains("the price table is last quarter's");
    }

    @Test
    @DisplayName("SH1002: and by no other credential, because the note is written for whoever fixes it")
    void anotherCredentialDoesNot() {
        as("ana");

        assertThat(shAgentVerdictService.mine("reject", null))
                .extracting(ShAgentVerdict::session)
                .as("SH1002: a credential that did not write the run has no business reading what a "
                        + "curator said about somebody else's work")
                .doesNotContain(THEIRS);

        as("bruno");
        assertThat(shAgentVerdictService.mine("reject", null))
                .extracting(ShAgentVerdict::session)
                .contains(THEIRS)
                .doesNotContain(MINE);
    }

    @Test
    @DisplayName("SH1002: the agent namespace still has no way to approve, which is the point")
    void nothingHereDecides() {
        assertThat(shAgentManifestService.manifest().endpoints())
                .as("SH1002: 'the agent never approves, holds or rejects its own work' is a standing "
                        + "non-goal, and this task adds a read — not a door. The index is every "
                        + "route this namespace offers, so a decide path would have to appear here")
                // On word boundaries, not substrings: `/preview-token` contains "review", which is
                // SH794's over-match — a bare word matches inside any identifier that spells it.
                .noneMatch(endpoint -> endpoint.path().matches(".*\breview/sessions\b.*")
                        || endpoint.path().matches(".*\bapprove\b.*")
                        || "POST".equals(endpoint.method())
                                && endpoint.path().matches(".*\breview\b.*"));
        assertThat(shAgentManifestService.manifest().features())
                .as("SH1002: and the capability is claimed, or it is a read an agent has and the "
                        + "document a session opens with never mentions. Not in the endpoint index: "
                        + "that one is curated and has a ceiling, and a flag is what P1's registries "
                        + "join on")
                .containsEntry("reviewVerdicts", true);

        ShMcpResource verdicts = resources.stream()
                .filter(resource -> "shio://review/mine".equals(resource.uri()))
                .findFirst().orElseThrow();
        assertThat(verdicts.httpTwin())
                .as("SH346: a document published only over MCP is a P1 break")
                .isEqualTo("/api/v2/agent/review?decision=reject");
        assertThat(verdicts.httpTwin() + verdicts.description())
                .as("SH1002: the resource names no path that decides anything")
                .doesNotContain("decide")
                .doesNotContain("approve");
    }

    @Test
    @DisplayName("SH1002: an empty answer says so, rather than being an empty document")
    void nothingRejectedIsAStatement() {
        as("nobody-" + UUID.randomUUID());

        assertThat(ShAgentVerdictTerse.render(shAgentVerdictService.mine("reject", null)))
                .as("SH1002: 'nothing was rejected' is the good news a resumed session is reading "
                        + "for, and a blank response is indistinguishable from a read that failed")
                .isEqualTo("verdicts 0\n");
    }
}
