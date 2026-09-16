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
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.agent.mcp.ShMcpServer;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShHistoryAPI;
import com.viglet.shio.api.review.ShReviewService;
import com.viglet.shio.api.review.dto.ShReviewSession;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH999 — a machine's change, told apart from a person's, on both readers of the audit trail.
 *
 * <h2>The guarantee this closes</h2>
 *
 * <p>{@code ShAgentChanges.Change#by}'s javadoc promised that the agent session was stamped into the
 * actor string "so a caller can tell its own writes from a human's". SH106 moved that fact to its own
 * column and the promise went on being printed for six blocks, because nothing asserted it. Both
 * readers reported the bare principal, so an agent and the person whose credential it used were one
 * name on the feed and one name on the Activity page.</p>
 *
 * <h2>Why a console session is asserted on the human side</h2>
 *
 * <p>It is the case a filter written from the column rather than from the question gets wrong. SH996
 * gives a curator's own publishing a session id so it reaches the review queue; a predicate reading
 * "has a session id" therefore files a curator's own press under the machines, on the page they
 * opened to find out who did this.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentChangesAttributionTest {

    /** Old enough to sit under the feed's ceiling however long the suite takes. */
    private static final Instant THEN = Instant.parse("2026-03-01T10:00:00Z");
    private static final String RUN = UUID.randomUUID().toString();
    private static final String CONSOLE = "console:ana:2026-03-01";

    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShAgentRunService shAgentRunService;
    @Autowired
    private ShHistoryAPI shHistoryAPI;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private ShAgentSessionFilter shAgentSessionFilter;
    @Autowired
    private ShMcpServer shMcpServer;
    @Autowired
    private ShReviewService shReviewService;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String site;
    private String siteId;
    private final String postId = "sh999-post-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            site = "sh999-" + UUID.randomUUID().toString().substring(0, 8);
            ShSite row = new ShSite();
            row.setName(site);
            row.setDate(THEN);
            row.setOwner("admin");
            entityManager.persist(row);
            siteId = row.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(row);
            root.setDate(THEN);
            root.setOwner("admin");
            entityManager.persist(root);

            // The three rows this task is about, and the point is that all three name one principal:
            // an agent acts AS someone, so `owner` cannot be what tells them apart.
            record("aaaa", ShContentAction.UPDATE, RUN, "Updated \"Home\"");
            record("bbbb", ShContentAction.UPDATE, null, "Updated \"Home\"");
            record("cccc", ShContentAction.PUBLISH, CONSOLE, "Published \"Home\"");
        });
        shAgentRunService.introduce(RUN, "claude-code", "2.1.0", ShAgentRunService.MCP, "ana");
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shHistoryRepository.findAll().stream()
                        .filter(entry -> siteId.equals(entry.getShSite()))
                        .toList()
                        .forEach(shHistoryRepository::delete));
    }

    private void record(String idPrefix, ShContentAction action, String session, String description) {
        ShHistory entry = new ShHistory();
        entry.setId(idPrefix + "-0000-4000-8000-" + UUID.randomUUID().toString().substring(0, 12));
        entry.setAction(action.name());
        entry.setShObject(postId);
        entry.setShSite(siteId);
        entry.setOwner("ana");
        entry.setAgentSessionId(session);
        entry.setDate(THEN);
        entry.setDescription(description);
        shHistoryRepository.save(entry);
    }

    private List<ShAgentChanges.Change> feed(String by) {
        return shAgentChangesService.changes(site, null, 50, false, 0, false, by).changes();
    }

    /** The console's read of the same rows — a page since SH1005, and the rows are on it. */
    private List<ShHistoryAPI.ShHistoryResponse> trail(String by, String actor) {
        return shHistoryAPI.byObject(postId, 50, null, null, by, actor, null).rows();
    }

    @Test
    @DisplayName("SH999: an agent's write and a person's, by the same credential, are distinguishable")
    void theFeedSaysWhichWasTheMachine() {
        List<ShAgentChanges.Change> rows = feed(null);

        assertThat(rows).hasSize(3);
        assertThat(rows).allSatisfy(row -> assertThat(row.by())
                .as("SH999: an agent acts AS someone, so the principal is the same on every row — "
                        + "which is exactly why it cannot be what tells them apart")
                .isEqualTo("ana"));
        assertThat(rows).filteredOn(row -> row.agent() != null).singleElement().satisfies(row -> {
            assertThat(row.agent()).isEqualTo(RUN);
            assertThat(row.agentName())
                    .as("SH999: MCP's initialize already said what the client was and this product "
                            + "threw it away, so every surface could only report a uuid")
                    .isEqualTo("claude-code");
        });
    }

    @Test
    @DisplayName("SH999: a curator's console session is a person, not a run")
    void aConsoleSessionIsNotAnAgent() {
        assertThat(feed(null)).filteredOn(row -> "PUBLISH".equals(row.action()))
                .singleElement()
                .satisfies(row -> assertThat(row.agent())
                        .as("SH996 gives a curator's own publishing a session id so it reaches the "
                                + "review queue; a filter written from the column rather than the "
                                + "question files that press under the machines")
                        .isNull());
    }

    @Test
    @DisplayName("SH999: by=agent, by=curator and by=<session> each answer with the rows they name")
    void theFilterSplitsTheFeed() {
        assertThat(feed("agent")).singleElement()
                .satisfies(row -> assertThat(row.agent()).isEqualTo(RUN));
        assertThat(feed("curator"))
                .as("SH999: what changed that was not me — the question a resuming session asks, "
                        + "and the one the feed could not answer")
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.agent()).isNull());
        assertThat(feed(RUN)).singleElement()
                .satisfies(row -> assertThat(row.agent()).isEqualTo(RUN));
        assertThat(feed("a-session-nobody-wrote-under"))
                .as("SH999: a session with no rows is an empty feed, not a refusal — the vocabulary "
                        + "is two words and everything else is an id")
                .isEmpty();
    }

    @Test
    @DisplayName("SH999: the console's Activity read carries the same three facts and the same filters")
    void theConsoleReadSaysItToo() {
        List<ShHistoryAPI.ShHistoryResponse> rows = trail(null, null);

        assertThat(rows).hasSize(3);
        assertThat(rows).filteredOn(row -> row.agent() != null).singleElement()
                .satisfies(row -> assertThat(row.agentName()).isEqualTo("claude-code"));
        assertThat(trail("agent", null)).singleElement()
                .satisfies(row -> assertThat(row.agent()).isEqualTo(RUN));
        assertThat(trail("curator", null)).hasSize(2);
        assertThat(trail(null, "ana"))
                .as("SH999: an agent and a person share a credential, so filtering by name and "
                        + "filtering by machine are not the same filter and the page needs both")
                .hasSize(3);
        assertThat(trail(null, "someone-else")).isEmpty();
    }

    @Test
    @DisplayName("SH1000: the REST header declares a run's premise, and the queue leads with it")
    void theHeaderDeclaresIt() throws Exception {
        String run = "sh1000-rest-" + UUID.randomUUID().toString().substring(0, 8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v2/agent/batch");
        request.addHeader(ShAgentSessionFilter.SESSION_HEADER, run);
        request.addHeader(ShAgentSessionFilter.INTENT_HEADER,
                "replace the launch banner on every product page");
        shAgentSessionFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(shAgentRunService.intents(List.of(run)).get(run))
                .as("SH1000: a premise belongs to the run, not to any one call — /agent/batch and "
                        + "/agent/apply take different documents, and a sentence spelled into each "
                        + "would be two places to keep one fact")
                .isEqualTo("replace the launch banner on every product page");
    }

    @Test
    @DisplayName("SH1000: MCP declares it in clientInfo, which is paid once per run and not per turn")
    void initializeDeclaresIt() {
        String run = "sh1000-mcp-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentSessionContext.set(run);
        try {
            shMcpServer.handle(objectMapper.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,"
                    + "\"method\":\"initialize\",\"params\":{\"clientInfo\":"
                    + "{\"name\":\"claude-code\",\"version\":\"2.1.0\","
                    + "\"intent\":\"convert the captured blocks into sections\"}}}"));
        } finally {
            ShAgentSessionContext.clear();
        }

        assertThat(shAgentRunService.intents(List.of(run)).get(run))
                .as("SH1000: tools/list is re-sent every turn and initialize is not, so this is the "
                        + "cheapest place on the protocol to say it once")
                .isEqualTo("convert the captured blocks into sections");
        assertThat(shAgentRunService.name(run)).contains("claude-code");
    }

    @Test
    @DisplayName("SH1000: the premise reaches the review queue, and a run without one says so")
    void theQueueLeadsWithIt() {
        List<ShReviewSession> queue = shReviewService.sessions(500);

        assertThat(queue).filteredOn(row -> RUN.equals(row.session())).singleElement()
                .satisfies(row -> assertThat(row.intent())
                        .as("SH1000: a run that declared nothing reports no premise rather than an "
                                + "empty sentence — the console names the flag that would supply one")
                        .isNull());
    }

    @Test
    @DisplayName("SH1000: the summary counts by site name, which is what a reviewer can read")
    void theSummaryCountsBySite() {
        assertThat(shReviewService.sessions(500))
                .filteredOn(row -> RUN.equals(row.session())).singleElement()
                .satisfies(row -> {
                    assertThat(row.sites()).containsExactly(siteId);
                    assertThat(row.bySite())
                            .as("SH1000: `sites` carried ids, and an id tells a reviewer nothing — "
                                    + "eleven changes over four sites is a different decision from "
                                    + "eleven in one, and that is unreadable as four uuids")
                            .containsExactly(java.util.Map.entry(site, 1));
                });
    }

    @Test
    @DisplayName("SH1000: a run whose pages have no earlier revision compares none, and says so")
    void appearanceReportsItsDenominator() {
        var appearance = shReviewService.detail(RUN).appearance();

        assertThat(appearance).isNotNull();
        assertThat(appearance.checked())
                .as("SH1000: `checked` is the denominator — without it an empty `moved` reads as "
                        + "'nothing moved' when it may mean 'nothing was comparable', and these "
                        + "fixtures have no revision before the run at all")
                .isZero();
        assertThat(appearance.moved())
                .as("SH1000: a page the run created has no before, so it is not compared — it is "
                        + "already a CREATE in the counts")
                .isEmpty();
        assertThat(appearance.note())
                .as("SH1000: the cut is reported only when the ceiling bit; three pages did not")
                .isNull();
    }

    @Test
    @DisplayName("SH1000: the premise is read once per run and never rides the change row")
    void itNeverJoinsTheChangePayload() {
        String rendered = objectMapper.writeValueAsString(feed(null));

        assertThat(rendered)
                .as("SH1000: one sentence about forty rows, carried on the row, is that sentence "
                        + "forty times — and the feed is the surface a session polls, so the "
                        + "repetition would be paid on every poll")
                .doesNotContain("intent");
    }

    @Test
    @DisplayName("SH999: the terse feed names the agent too, in one attribute")
    void theTerseFormSaysIt() {
        String rendered = ShAgentChangesTerse.render(
                shAgentChangesService.changes(site, null, 50, false, 0, false, "agent"));

        assertThat(rendered)
                .as("SH999: the terse form is the one an MCP session actually reads, so a "
                        + "distinction only the JSON carried would be a distinction the agent "
                        + "surface does not have")
                .contains("agent=claude-code");
        assertThat(ShAgentChangesTerse.render(
                shAgentChangesService.changes(site, null, 50, false, 0, false, "curator")))
                .as("SH999: and it costs a human-only feed nothing at all")
                .doesNotContain("agent=");
    }
}
