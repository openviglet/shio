/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.review.dto.ShReviewOutcome;
import com.viglet.shio.api.review.dto.ShReviewPending;
import com.viglet.shio.api.review.dto.ShReviewSession;
import com.viglet.shio.persistence.model.review.ShReviewDecision.Kind;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH207 — the navigation badge's count, and what it is allowed to disagree with.
 *
 * <h2>What this is the assertion for</h2>
 *
 * <p>SH104 shipped the handoff as {@code shio report}, which answers "what did the agent do since
 * I last looked" for whoever ran it. A curator who never opens a terminal had no answer at all.
 * §IX.8 settled the position before the content: a banner on every page is noise, a banner only on
 * the review page is a page nobody visits unless they already suspect something, and what is left
 * is an indicator in the navigation.</p>
 *
 * <h2>The property that makes an indicator usable</h2>
 *
 * <p>It has to be <b>safely ignorable when zero</b>, which is a claim about what happens after the
 * curator acts, not about the number itself. So the interesting assertion is not "it counts three"
 * — it is that approving a run takes it to zero, because a badge that stays lit after the work is
 * done is one a curator stops looking at, and then it is worse than nothing.</p>
 *
 * <h2>The disagreement this used to pin, and what closing it cost (SH615)</h2>
 *
 * <p>{@code ShReviewSession.pending} counted <em>audit entries</em>, so a run that edited one
 * page four times reported four, and SH207 pinned that here rather than fixing it — it only had
 * to make the badge's own number defensible. The pin said so in its assertion message, which is
 * the right way to leave a disagreement: stated, not hidden.</p>
 *
 * <p>SH615 made the decision the pin deferred, and it turned out to be two. The unit is now the
 * <b>decision</b> — a post, not an entry — and the definition is {@code unreleased}, the one
 * SH207 had already written one method away because a badge has to be ignorable at zero. The old
 * definition asked whether a {@code DRAFT} row exists, and {@code publish} keeps the draft as the
 * editable copy, so <b>the count could only go up</b>. Both were fixed together: a number that is
 * fresh and still not a count of decisions reads as correct, which is worse than one visibly
 * stuck.</p>
 *
 * <p>Its own database: these assertions count agent runs across the whole instance, so any other
 * test that wrote an agent-attributed audit row would break them in whatever order the suite ran.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-review-pending;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShReviewPendingTest {

    private static final Instant T = Instant.parse("2026-03-01T10:00:00Z");
    private static final String SESSION = "sh207-session-a";
    private static final String QUIET_SESSION = "sh207-session-b";

    @Autowired
    private ShReviewService shReviewService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String draftedPostId;

    /**
     * Rebuilt per test, not per class: {@code approve} is one of the things under test and it
     * changes what the other two read. Sharing the fixture would make the suite's answer depend on
     * JUnit's method order, which is stable and unstated — the worst combination, because it
     * passes until somebody renames a method.
     */
    @BeforeEach
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            entityManager.createQuery("delete from ShHistory h where h.agentSessionId is not null")
                    .executeUpdate();
            // SH1001 — a decision outlives the content it is about, which is the point of the table
            // and a trap for a fixture: without this the suite's answer depends on what the previous
            // test decided, which is the shared-state flake this @BeforeEach exists to avoid.
            entityManager.createQuery("delete from ShReviewDecision d").executeUpdate();
            entityManager.createQuery("delete from ShPostUnified p").executeUpdate();
            entityManager.flush();
            ShSite site = new ShSite();
            site.setName("review-pending-" + UUID.randomUUID().toString().substring(0, 8));
            site.setDate(T);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(T);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType postType = entityManager
                    .createQuery("select t from ShPostType t", ShPostType.class)
                    .setMaxResults(1).getResultStream().findFirst().orElse(null);

            // One run, one post, still a draft — and edited three times, which is the case that
            // separates "runs awaiting review" from "audit entries awaiting review".
            ShPostUnified draft = new ShPostUnified();
            draft.setTitle("Agent draft");
            draft.setFurl("/agent-draft");
            draft.setState(ShPostState.DRAFT);
            draft.setShSite(site);
            draft.setShFolder(root);
            draft.setShPostType(postType);
            draft.setDate(T);
            draft.setOwner("agent");
            draft.setData("{}");
            entityManager.persist(draft);
            draftedPostId = draft.getId();

            for (int i = 0; i < 3; i++) {
                audit(SESSION, draftedPostId, "UPDATE", T.plusSeconds(i));
            }
            // A second run whose post is long gone: history rather than a decision.
            audit(QUIET_SESSION, "sh207-vanished-post", "DELETE", T.plusSeconds(10));
        });
    }

    @Test
    @DisplayName("the badge counts runs awaiting a decision, not the runs that are history")
    void itCountsOnlyRunsWithSomethingToDecide() {
        ShReviewPending pending = shReviewService.pending();

        assertThat(pending.sessions())
                .as("""
                        the page this badge opens is a list of runs, so the badge counts runs — a \
                        number that appears nowhere on the page it links to is one nobody can \
                        reconcile. The second run touched a post that no longer exists, which is \
                        history and not a decision.""")
                .isEqualTo(1);
        assertThat(pending.changes())
                .as("""
                        and `changes` counts distinct POSTS: one page edited three times is one \
                        decision. Since SH615 each session's own `pending` counts the same unit, \
                        so the two screens reconcile — see the assertion below.""")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SH615: the two counts now answer the same question about the same unit")
    void theTwoCountsAgree() {
        // SH207 pinned this at 3 and said so: `ShReviewSession.pending` counted audit ENTRIES, so
        // three edits to one page reported three, and the comment here read "pinned rather than
        // fixed". SH615 is the decision that comment deferred. A pinned disagreement is the right
        // way to leave one — it states the shape instead of hiding it — and this is what paying
        // it off looks like from the other end.
        ShReviewSession run = runOf(SESSION);

        assertThat(run.pending())
                .as("""
                        the unit is the DECISION, and one page edited three times is one decision. \
                        A run's own number and the badge's now count the same thing, so a reviewer \
                        can reconcile the two screens instead of learning which to distrust \
                        (SH615).""")
                .isEqualTo(1);
        assertThat(shReviewService.pending().changes())
                .as("the badge's read, counting the same one page")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SH615: a run's own count reaches zero once its work is released")
    void aRunsCountReachesZero() {
        // The assertion that would have caught it (SH527). `pending` asked whether the post has a
        // DRAFT row — and `publish` writes a PUBLISHED row and KEEPS the draft as the editable
        // copy, so every post that has ever existed has one, forever. The number could only go up:
        // a curator approved a run, the page reloaded with the same "1 to review", and Approve
        // stayed enabled on work already published. Nothing threw and nothing was logged.
        assertThat(runOf(SESSION).pending())
                .as("red by construction: a zero here would make the assertion below vacuous")
                .isEqualTo(1);

        shReviewService.approve(SESSION, "curator");

        assertThat(runOf(SESSION).pending())
                .as("""
                        A queue whose count cannot go down is a queue that asserts something untrue \
                        about the one thing it exists to resolve, and the curator has no way to \
                        tell it from work they have genuinely not done (SH615).""")
                .isZero();
    }

    private ShReviewSession runOf(String session) {
        return shReviewService.sessions(50).stream()
                .filter(each -> session.equals(each.session()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("approving takes it to zero, which is what makes an indicator ignorable")
    void itGoesToZeroWhenTheWorkIsDone() {
        assertThat(shReviewService.pending().sessions())
                .as("red by construction: if this were already zero the assertion below is vacuous")
                .isEqualTo(1);

        shReviewService.approve(SESSION, "curator");

        assertThat(shReviewService.pending())
                .as("""
                        the whole property of an unread indicator: it has to reach zero when the \
                        work is done. A badge that stays lit after a curator has acted is one they \
                        learn to ignore, and an indicator nobody reads is worse than no indicator \
                        — it occupies the place where a real one would have gone.""")
                .isEqualTo(new ShReviewPending(0, 0));
    }

    @Test
    @DisplayName("SH1001: one page of a run is approved on its own, and the rest is left alone")
    void oneChangeIsDecidedWithoutTheRest() {
        String second = secondDraft();

        ShReviewOutcome outcome = shReviewService.decide(SESSION,
                List.of(new ShReviewService.Decision(draftedPostId, Kind.APPROVE, null)), "ana");

        assertThat(outcome.posts()).containsExactly(draftedPostId);
        assertThat(shPostUnifiedRepository.findByIdAndState(draftedPostId, ShPostState.PUBLISHED))
                .as("SH1001: one bad page in a long run used to force a curator to accept all of it "
                        + "or undo all of it")
                .isPresent();
        assertThat(shPostUnifiedRepository.findByIdAndState(second, ShPostState.PUBLISHED))
                .as("SH1001: and the rest of the run is exactly where it was")
                .isEmpty();
        assertThat(shReviewService.decisions(SESSION)).singleElement()
                .satisfies(decision -> {
                    assertThat(decision.getPostId()).isEqualTo(draftedPostId);
                    assertThat(decision.getDecision()).isEqualTo("APPROVE");
                    assertThat(decision.getActor()).isEqualTo("ana");
                });
    }

    @Test
    @DisplayName("SH1001: a rejection with nothing written down is refused, and writes nothing")
    void aRejectionNeedsAReason() {
        assertThatThrownBy(() -> shReviewService.decide(SESSION,
                List.of(new ShReviewService.Decision(draftedPostId, Kind.REJECT, "  ")), "ana"))
                .isInstanceOf(ShReviewService.RejectionNeedsReasonException.class)
                .satisfies(failure -> assertThat(((ShReviewService.RejectionNeedsReasonException)
                        failure).problem().toProblemDetail().getDetail())
                        .as("SH1001: the agent that wrote the page cannot be told what was wrong "
                                + "with it, and the next person to open the run cannot see that "
                                + "anybody looked")
                        .contains("teaches nothing"));
        assertThat(shReviewService.decisions(SESSION)).isEmpty();
    }

    @Test
    @DisplayName("SH1001: a held page is parked — the badge stops counting it and the draft stays")
    void aHeldChangeStopsCountingAndStays() {
        assertThat(shReviewService.pending().sessions())
                .as("red by construction: if this were already zero the assertion below is vacuous")
                .isEqualTo(1);

        shReviewService.decide(SESSION,
                List.of(new ShReviewService.Decision(draftedPostId, Kind.HOLD, "waiting on legal")),
                "ana");

        assertThat(shReviewService.pending())
                .as("SH1001: a HOLD is the one decision with no side effect, so nothing about the "
                        + "content says it was looked at — without this the only way to park a page "
                        + "would leave the badge saying the same number for ever")
                .isEqualTo(new ShReviewPending(0, 0));
        assertThat(shPostUnifiedRepository.findByIdAndState(draftedPostId, ShPostState.DRAFT))
                .as("SH1001: parked, not published and not reverted — the draft is exactly where it was")
                .isPresent();
        assertThat(shReviewService.detail(SESSION).decisions()).singleElement()
                .satisfies(decision -> assertThat(decision.note()).isEqualTo("waiting on legal"));
    }

    @Test
    @DisplayName("SH1001: approving the whole run is the same call, and records a decision per page")
    void theWholeRunVerbIsTheSameCall() {
        String second = secondDraft();

        ShReviewOutcome outcome = shReviewService.approve(SESSION, "ana");

        assertThat(outcome.action())
                .as("SH1001: the gesture keeps its name — two code paths for one decision is how "
                        + "the whole-run verb and the per-change one come to disagree about what "
                        + "approve means")
                .isEqualTo("approve");
        assertThat(outcome.posts()).containsExactlyInAnyOrder(draftedPostId, second);
        assertThat(shReviewService.decisions(SESSION))
                .as("SH1001: and it leaves the same record, so a run approved wholesale is not a "
                        + "run nobody decided")
                .hasSize(2)
                .allSatisfy(decision -> assertThat(decision.getDecision()).isEqualTo("APPROVE"));
    }

    /** A second drafted page in the same run, so "one of them" is a thing this suite can say. */
    private String secondDraft() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            ShPostUnified first = entityManager.find(ShPostUnified.class,
                    new com.viglet.shio.persistence.model.post.unified.ShPostUnifiedId(
                            draftedPostId, ShPostState.DRAFT));
            ShPostUnified draft = new ShPostUnified();
            draft.setTitle("Agent draft two");
            draft.setFurl("/agent-draft-two");
            draft.setState(ShPostState.DRAFT);
            draft.setShSite(first.getShSite());
            draft.setShFolder(first.getShFolder());
            draft.setShPostType(first.getShPostType());
            draft.setDate(T);
            draft.setOwner("agent");
            draft.setData("{}");
            entityManager.persist(draft);
            audit(SESSION, draft.getId(), "UPDATE", T.plusSeconds(20));
            return draft.getId();
        });
    }

    private void audit(String session, String objectId, String action, Instant at) {
        ShHistory entry = new ShHistory();
        entry.setId(UUID.randomUUID().toString());
        entry.setAction(action);
        entry.setShObject(objectId);
        entry.setOwner("agent");
        entry.setDate(at);
        entry.setAgentSessionId(session);
        entry.setDescription("SH207 fixture");
        entityManager.persist(entry);
    }
}
