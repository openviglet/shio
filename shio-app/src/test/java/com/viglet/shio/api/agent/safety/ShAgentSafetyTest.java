/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet.Change;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Block L — the agent path is bounded, attributable and undoable.
 *
 * <p>What is asserted here is the <b>invariant</b> from §XII rather than the current field
 * list: a credential that may write is not thereby a credential that may publish (SH107);
 * a delete needs a plan a human could have read (SH108); a session that loops hits a
 * ceiling instead of a bill (SH110); and every write says which run made it, in a column
 * rather than in a display string (SH106) — which is the whole basis of SH109's queue.</p>
 *
 * <p>The tests drive {@link ShAgentSessionContext} directly instead of going through the
 * HTTP filter, because the property under test is "the gate holds", not "the filter sets
 * the thread-local". Two credentials appear: a console one (unbounded, may publish) and a
 * bounded agent one.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentSafetyTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentReviewService shAgentReviewService;
    @Autowired
    private ShAgentSessionService shAgentSessionService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "safety-site-" + UUID.randomUUID();
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShWidget created = new ShWidget();
                        created.setName("Text");
                        created.setDescription("Text");
                        entityManager.persist(created);
                        return created;
                    });

            ShPostType postType = new ShPostType();
            postTypeName = "SafetyArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Safety Article");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setIsTitle((byte) 1);
            attr.setRequired((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });
    }

    @AfterEach
    void clearSession() {
        ShAgentSessionContext.clear();
    }

    // --- SH107: draft-by-default and the publish gate ---

    @Test
    @DisplayName("a credential that may write is not thereby a credential that may publish")
    void publishIsRefusedWithoutTheGrant() {
        String base = bind(new ShAgentGrant(false, -1, -1));
        run(false, folder(base), post(base + "/a", base, "A"));

        assertThatThrownBy(() -> run(false, publish(base + "/a")))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(slugOf(failure)).isEqualTo("publish-not-permitted"));
    }

    @Test
    @DisplayName("taking a page down is gated too — a half-gate would let a bounded agent empty a site")
    void unpublishIsGatedAsWell() {
        String base = bind(new ShAgentGrant(false, -1, -1));

        assertThatThrownBy(() -> run(false,
                new Op(ShAgentBatchService.POST_UNPUBLISH, "post:" + siteName + base + "/a",
                        null, null, null)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(slugOf(failure)).isEqualTo("publish-not-permitted"));
    }

    @Test
    @DisplayName("the plan refuses too, so plan and apply cannot disagree about the same request")
    void theRefusalHappensOnADryRunAsWell() {
        String base = bind(new ShAgentGrant(false, -1, -1));

        assertThatThrownBy(() -> run(true, publish(base + "/anything")))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class);
    }

    @Test
    @DisplayName("writes land in DRAFT: a created post is not live until something publishes it")
    void writesLandInDraft() {
        String base = bind(new ShAgentGrant(false, -1, -1));
        ShAgentChangeSet result = run(false, folder(base), post(base + "/draft-only", base, "D"));

        assertThat(result.results()).extracting(Change::action)
                .containsExactly(Change.CREATE, Change.CREATE);
        // Nothing published: the only op that could have is the one this credential is
        // denied, and post.upsert has no parameter that could ask for it.
        assertThat(result.results()).noneMatch(change -> Change.PUBLISH.equals(change.action()));
    }

    // --- SH108: destructive-op confirm tokens ---

    @Test
    @DisplayName("a delete without a confirmation is refused, and the refusal does not leak the token")
    void deleteNeedsAConfirmation() {
        String base = bind(ShAgentGrant.CONSOLE);
        run(false, folder(base), post(base + "/doomed", base, "Doomed"));
        Op delete = delete(base + "/doomed");

        assertThatThrownBy(() -> run(false, delete))
                .isInstanceOf(ShAgentPolicy.ConfirmationRequiredException.class)
                .satisfies(failure -> {
                    ShAgentPolicy.ConfirmationRequiredException problem =
                            (ShAgentPolicy.ConfirmationRequiredException) failure;
                    // It names what would go — the agent has to be able to render that.
                    assertThat(problem.getMessage()).contains(base + "/doomed");
                    // …and it does not hand over the key. A refusal carrying the token
                    // would collapse two steps into one retry and the human would never
                    // see the plan, which is the entire content of SH108.
                    String token = run(true, delete).confirm();
                    assertThat(problem.getMessage()).doesNotContain(token);
                });
    }

    @Test
    @DisplayName("the plan carries the token, and the token unlocks that exact plan")
    void thePlanIssuesTheTokenThatUnlocksIt() {
        String base = bind(ShAgentGrant.CONSOLE);
        run(false, folder(base), post(base + "/gone", base, "Gone"));
        Op delete = delete(base + "/gone");

        String confirm = run(true, delete).confirm();
        assertThat(confirm).isNotBlank();

        ShAgentChangeSet applied = runWith(confirm, delete);
        assertThat(applied.results()).extracting(Change::action).containsExactly(Change.DELETE);
    }

    @Test
    @DisplayName("a token from one plan does not unlock a different one")
    void aTokenIsBoundToItsOwnPlan() {
        String base = bind(ShAgentGrant.CONSOLE);
        run(false, folder(base), post(base + "/one", base, "One"),
                post(base + "/two", base, "Two"));

        String confirmForOne = run(true, delete(base + "/one")).confirm();

        assertThatThrownBy(() -> runWith(confirmForOne, delete(base + "/two")))
                .isInstanceOf(ShAgentPolicy.ConfirmationRequiredException.class)
                .hasMessageContaining("changed between the plan and this call");
    }

    @Test
    @DisplayName("a write that destroys nothing needs no confirmation and is issued no token")
    void nonDestructiveWritesAreUnaffected() {
        String base = bind(ShAgentGrant.CONSOLE);

        ShAgentChangeSet plan = run(true, folder(base), post(base + "/kept", base, "Kept"));
        assertThat(plan.confirm()).isNull();

        assertThat(run(false, folder(base), post(base + "/kept", base, "Kept")).changed())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("deleting what is already absent destroys nothing, so it stays a one-step noop")
    void anAlreadyAbsentDeleteIsNotDestructive() {
        bind(ShAgentGrant.CONSOLE);
        ShAgentChangeSet result = run(false, delete("/never-existed-" + UUID.randomUUID()));

        assertThat(result.results()).extracting(Change::action).containsExactly(Change.NOOP);
    }

    // --- SH110: per-session budget guards ---

    @Test
    @DisplayName("a session that loops hits its write ceiling instead of running up a bill")
    void theWriteCeilingStopsARunawaySession() {
        String base = bind(new ShAgentGrant(true, -1, 2));

        assertThatThrownBy(() -> run(false, folder(base),
                post(base + "/a", base, "A"), post(base + "/b", base, "B")))
                .isInstanceOf(ShAgentBudgetExhaustedException.class)
                .hasMessageContaining("writes");
    }

    @Test
    @DisplayName("a refused batch writes nothing at all — the ceiling is a rollback, not a cut-off")
    void breachingTheCeilingRollsTheWholeBatchBack() {
        String base = bind(new ShAgentGrant(true, -1, 2));
        String address = "post:" + siteName + base + "/rolled-back";

        assertThatThrownBy(() -> run(false, folder(base),
                post(base + "/x", base, "X"), post(base + "/rolled-back", base, "R")))
                .isInstanceOf(ShAgentBudgetExhaustedException.class);

        // A fresh session, so the ceiling is not what is being observed here.
        bind(ShAgentGrant.CONSOLE);
        assertThat(run(true, new Op(ShAgentBatchService.POST_DELETE, address, null, null, null))
                .results().get(0).action()).isEqualTo(Change.NOOP);
    }

    @Test
    @DisplayName("the call ceiling is counted before the work, so the breaching call is refused")
    void theCallCeilingIsCountedUpFront() {
        String base = bind(new ShAgentGrant(true, 1, -1));
        run(true, folder(base));

        assertThatThrownBy(() -> run(true, folder(base)))
                .isInstanceOf(ShAgentBudgetExhaustedException.class)
                .hasMessageContaining("calls");
    }

    @Test
    @DisplayName("a console credential is never metered — the UI is unmetered, so metering the API alone would be theatre")
    void aConsoleCredentialIsNotMetered() {
        String base = bind(ShAgentGrant.CONSOLE);
        for (int i = 0; i < 5; i++) {
            run(true, folder(base));
        }
        assertThat(shAgentSessionService.info().metered()).isFalse();
    }

    // --- SH106: session identity, and SH109's queue on top of it ---

    @Test
    @DisplayName("every write records which agent session made it, in a column")
    void writesAreAttributedToTheirSession() {
        String base = bind(ShAgentGrant.CONSOLE);
        String session = ShAgentSessionContext.current().orElseThrow();
        run(false, folder(base), post(base + "/attributed", base, "A"));

        List<ShHistory> entries = shHistoryRepository.findByAgentSession(session);
        assertThat(entries).isNotEmpty();
        assertThat(entries).allSatisfy(entry ->
                assertThat(entry.getAgentSession()).isEqualTo(session));
        // The owner string keeps saying an agent did it (SH75), so the console's activity
        // list does not silently stop distinguishing agent work from a human's.
        assertThat(entries.get(0).getOwner()).contains("agent:" + session);
    }

    @Test
    @DisplayName("the queue groups a run into one reviewable unit")
    void theQueueGroupsChangesBySession() {
        String base = bind(ShAgentGrant.CONSOLE);
        String session = ShAgentSessionContext.current().orElseThrow();
        run(false, folder(base), post(base + "/q1", base, "Q1"), post(base + "/q2", base, "Q2"));

        var queued = shAgentReviewService.queue(true, 50, false).results().stream()
                .filter(row -> session.equals(row.session()))
                .findFirst()
                .orElseThrow();

        assertThat(queued.changes()).isEqualTo(2);
        assertThat(queued.pending()).isEqualTo(2);
        assertThat(queued.published()).isZero();
    }

    @Test
    @DisplayName("approving leaves the pending queue without touching the content")
    void approvingClearsTheQueue() {
        String base = bind(ShAgentGrant.CONSOLE);
        String session = ShAgentSessionContext.current().orElseThrow();
        run(false, folder(base), post(base + "/approved", base, "A"));

        shAgentReviewService.approve(session, "alice");

        assertThat(shAgentReviewService.changes(session, false))
                .allSatisfy(change -> assertThat(change.reviewed()).isTrue());
        assertThat(shAgentReviewService.queue(true, 50, false).results())
                .noneMatch(row -> session.equals(row.session()));
    }

    @Test
    @DisplayName("reverting a run undoes what it created, and says what it could not undo")
    void revertingUndoesTheRunAndReportsWhatItCannot() {
        String base = bind(ShAgentGrant.CONSOLE);
        String session = ShAgentSessionContext.current().orElseThrow();
        run(false, folder(base), post(base + "/reverted", base, "First"));
        // An edit in the same run: the audit row says the post changed, not what it
        // changed from, so revert has to report it rather than pretend.
        run(false, post(base + "/reverted", base, "Second"));

        var outcome = shAgentReviewService.revert(session, "alice");

        assertThat(outcome.reverted()).isGreaterThanOrEqualTo(1);
        assertThat(outcome.skipped()).anySatisfy(reason ->
                assertThat(reason).contains("no automatic inverse"));
        // The created post is gone (to the SH55 trash, so the undo is itself undoable).
        bind(ShAgentGrant.CONSOLE);
        assertThat(run(true, delete(base + "/reverted")).results().get(0).action())
                .isEqualTo(Change.NOOP);
    }

    @Test
    @DisplayName("the session endpoint answers 'may I publish' before the work, not after")
    void theSessionEndpointStatesTheBounds() {
        bind(new ShAgentGrant(false, -1, 7));
        var info = shAgentSessionService.info();

        assertThat(info.mayPublish()).isFalse();
        assertThat(info.metered()).isTrue();
        assertThat(info.maxWrites()).isEqualTo(7);
        assertThat(info.notes()).anySatisfy(note -> assertThat(note).contains("DRAFT"));
    }

    // --- helpers ---

    /**
     * Binds a fresh session with the given grant and returns a unique folder base, so each
     * test gets both its own budget counters and its own corner of the tree.
     */
    /** The teaching problem's stable slug — what a caller branches on, so what a test asserts. */
    private static String slugOf(Throwable failure) {
        return ((com.viglet.shio.api.error.ShTeachingException) failure).problem().slug();
    }

    private String bind(ShAgentGrant grant) {
        ShAgentSessionContext.set(UUID.randomUUID().toString(), grant);
        return "/sf-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ShAgentChangeSet run(boolean dryRun, Op... ops) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(ops)), false,
                        ShAgentSessionContext.actor("tester")));
    }

    private ShAgentChangeSet runWith(String confirm, Op... ops) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(ops), confirm),
                        false, "tester"));
    }

    private JsonNode data(String headline) {
        return objectMapper.valueToTree(Map.of("headline", headline));
    }

    private Op folder(String path) {
        return new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + path,
                null, null, null);
    }

    private Op post(String url, String folder, String headline) {
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + url,
                postTypeName, folder, data(headline));
    }

    private Op publish(String url) {
        return new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + url,
                null, null, null);
    }

    private Op delete(String url) {
        return new Op(ShAgentBatchService.POST_DELETE, "post:" + siteName + url,
                null, null, null);
    }
}
