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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.review.ShReviewService;
import com.viglet.shio.api.review.dto.ShReviewDetail;
import com.viglet.shio.api.review.dto.ShReviewOutcome;
import com.viglet.shio.api.review.dto.ShReviewSession;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Block L — what bounds an agent (SH106, SH107, SH108) and how a human undoes it (SH109).
 *
 * <p>Every assertion here is about a <em>refusal</em> or an <em>undo</em>, which is why the
 * block exists at all: an agent-driven CMS is adoptable only if the answer to "what if it gets
 * it wrong" is a mechanism rather than a promise. The three guards are deliberately different
 * shapes — a credential that cannot publish, a delete that needs the plan it was shown, and a
 * queue that can put things back — because they fail in different ways.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentSafetyTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShReviewService shReviewService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
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
            postTypeName = "SafetyPage" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Safety Page");
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
    void clearContexts() {
        SecurityContextHolder.clearContext();
        ShAgentSessionContext.clear();
    }

    // --- helpers ---

    private ShAgentChangeSet run(ShAgentBatch batch) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> shAgentBatchService.run(batch, true, "tester"));
    }

    private ShAgentChangeSet run(boolean dryRun, Op... ops) {
        return run(new ShAgentBatch(dryRun, List.of(ops)));
    }

    private JsonNode data(String headline) {
        return objectMapper.valueToTree(Map.of("headline", headline));
    }

    private Op folder(String path) {
        return new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + path, null, null, null);
    }

    private Op post(String url, String folder, String headline) {
        return new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + url,
                postTypeName, folder, data(headline));
    }

    private Op publish(String url) {
        return new Op(ShAgentBatchService.POST_PUBLISH, "post:" + siteName + url, null, null, null);
    }

    private Op delete(String url) {
        return new Op(ShAgentBatchService.POST_DELETE, "post:" + siteName + url, null, null, null);
    }

    /** Authenticates the current thread as an AGENT-scope token (SH106). */
    private void asAgentToken(boolean mayPublish) {
        var authentication = new UsernamePasswordAuthenticationToken("alice", null,
                List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
        authentication.setDetails(new ShApiTokenContext("tok-safety", ShApiTokenScope.AGENT,
                Set.of(), ShApiTokenEnv.PROD, mayPublish));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String base() {
        return "/sf-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // --- SH107: the publish gate ---

    @Test
    @DisplayName("an agent token without mayPublish writes the draft but cannot publish it")
    void publishIsRefusedWithoutTheCapability() {
        String base = base();
        asAgentToken(false);

        // The draft half is allowed: this is a credential that authors, not one that is locked out.
        assertThat(run(false, folder(base), post(base + "/page", base, "Draft")).changed())
                .isEqualTo(2);

        assertThatThrownBy(() -> run(false, publish(base + "/page")))
                .isInstanceOf(ShAgentBatchService.PublishNotPermittedException.class);
    }

    @Test
    @DisplayName("the refusal comes before the first write, not after the site is built")
    void publishIsRefusedBeforeAnythingIsWritten() {
        String base = base();
        asAgentToken(false);

        assertThatThrownBy(() -> run(false, folder(base), post(base + "/p", base, "P"),
                publish(base + "/p")))
                .isInstanceOf(ShAgentBatchService.PublishNotPermittedException.class);

        SecurityContextHolder.clearContext();
        assertThat(run(true, folder(base)).results().get(0).action())
                .as("the batch is atomic, so a refused publish must leave not even the folder behind "
                        + "— planning it again reports a create, which means it is not there")
                .isEqualTo("create");
    }

    @Test
    @DisplayName("a dryRun is refused too — an agent should learn it cannot publish while planning")
    void publishIsRefusedOnADryRunAsWell() {
        asAgentToken(false);
        assertThatThrownBy(() -> run(true, publish(base() + "/nothing")))
                .isInstanceOf(ShAgentBatchService.PublishNotPermittedException.class);
    }

    @Test
    @DisplayName("granting mayPublish is what makes it work; a console caller never needed it")
    void publishWorksWithTheCapabilityAndForAConsoleCaller() {
        String base = base();
        run(false, folder(base), post(base + "/granted", base, "Granted"),
                post(base + "/console", base, "Console"));

        asAgentToken(true);
        assertThat(run(false, publish(base + "/granted")).changed()).isEqualTo(1);

        SecurityContextHolder.clearContext();
        assertThat(run(false, publish(base + "/console")).changed())
                .as("no token means a human (or the CLI as one); the SH54 ACL is what bounds them")
                .isEqualTo(1);
    }

    // --- SH108: confirm tokens ---

    @Test
    @DisplayName("a delete without the confirm token is refused, and the refusal carries the token")
    void aDeleteNeedsConfirming() {
        String base = base();
        run(false, folder(base), post(base + "/doomed", base, "Doomed"));

        ShAgentChangeSet plan = run(true, delete(base + "/doomed"));
        assertThat(plan.confirm()).as("a dryRun that would delete hands back the token").isNotBlank();

        assertThatThrownBy(() -> run(false, delete(base + "/doomed")))
                .isInstanceOf(ShAgentConfirm.ConfirmRequiredException.class)
                .satisfies(e -> assertThat(((ShAgentConfirm.ConfirmRequiredException) e)
                        .problem().toProblemDetail().getStatus()).isEqualTo(428));

        ShAgentChangeSet done = run(new ShAgentBatch(false, List.of(delete(base + "/doomed")),
                plan.confirm()));
        assertThat(done.changed()).isEqualTo(1);
        assertThat(shPostUnifiedRepository.findByState(ShPostState.DRAFT).stream()
                .anyMatch(row -> (base + "/doomed").equals(row.getFurl())))
                .as("the post is gone (trashed, SH55) once it was confirmed")
                .isFalse();
    }

    @Test
    @DisplayName("a token that covers different deletions is refused — the drift guard")
    void aStaleConfirmIsRefused() {
        String base = base();
        run(false, folder(base), post(base + "/one", base, "One"), post(base + "/two", base, "Two"));

        String forOne = run(true, delete(base + "/one")).confirm();

        // The same token, now used for a run that would also take out /two: exactly the
        // "something changed underneath me" case, and the reason the digest is over the list.
        assertThatThrownBy(() -> run(new ShAgentBatch(false,
                List.of(delete(base + "/one"), delete(base + "/two")), forOne)))
                .isInstanceOf(ShAgentConfirm.ConfirmStaleException.class);

        assertThat(run(true, post(base + "/two", base, "Two")).results().get(0).action())
                .as("nothing was deleted: the batch is atomic and the refusal is before the writes")
                .isEqualTo("noop");
    }

    @Test
    @DisplayName("the same deletions always produce the same token, in any order")
    void theTokenIsDeterministic() {
        String base = base();
        run(false, folder(base), post(base + "/a", base, "A"), post(base + "/b", base, "B"));

        String forward = run(true, delete(base + "/a"), delete(base + "/b")).confirm();
        String reversed = run(true, delete(base + "/b"), delete(base + "/a")).confirm();

        assertThat(forward).isEqualTo(reversed).isNotBlank();
    }

    @Test
    @DisplayName("a delete of something already gone is a noop, and needs no token")
    void deletingNothingNeedsNoConfirm() {
        // The reason the digest is over the *plan*: hashing the request would refuse an
        // agent re-sending a document whose deletes already happened, and SH82's whole
        // promise is that re-sending it is safe.
        String base = base();
        ShAgentChangeSet result = run(false, delete(base + "/never-existed"));
        assertThat(result.results().get(0).action()).isEqualTo("noop");
        assertThat(result.changed()).isZero();
    }

    @Test
    @DisplayName("a batch that destroys nothing needs no token and is handed none")
    void nonDestructiveWritesAreUntouched() {
        String base = base();
        ShAgentChangeSet plan = run(true, folder(base), post(base + "/kept", base, "Kept"));
        assertThat(plan.confirm())
                .as("a token on a harmless batch is a habit, not a decision")
                .isNull();
        assertThat(run(false, folder(base), post(base + "/kept", base, "Kept")).changed()).isEqualTo(2);
    }

    // --- SH106: session identity ---

    @Test
    @DisplayName("a write inside an agent session is stamped with the session id")
    void theSessionIdReachesTheAuditTrail() {
        String base = base();
        String session = "run-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentSessionContext.set(session);
        run(false, folder(base), post(base + "/tracked", base, "Tracked"));
        ShAgentSessionContext.clear();

        List<ShHistory> rows = shHistoryRepository.findByAgentSession(session);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getAgentSessionId()).isEqualTo(session);
            assertThat(row.getOwner())
                    .as("owner names the human whose credential was used — one fact per field")
                    .isEqualTo("tester");
        });
    }

    @Test
    @DisplayName("a write outside a session carries none, so a human's edit is not an agent run")
    void aHumanEditHasNoSessionId() {
        String base = base();
        run(false, folder(base), post(base + "/mine", base, "Mine"));
        assertThat(shHistoryRepository.findByAgentSession(null)).isEmpty();
    }

    // --- SH109: the review queue ---

    @Test
    @DisplayName("the queue lists a run, and approving it publishes the drafts the agent could not")
    void approvingARunPublishesItsDrafts() {
        String base = base();
        String session = "run-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentSessionContext.set(session);
        run(false, folder(base), post(base + "/reviewed", base, "Reviewed"));
        ShAgentSessionContext.clear();

        List<ShReviewSession> sessions = shReviewService.sessions(50);
        assertThat(sessions).anySatisfy(row -> {
            assertThat(row.session()).isEqualTo(session);
            assertThat(row.changes()).isPositive();
            assertThat(row.pending()).as("a draft nobody has published is what there is to review")
                    .isPositive();
        });

        ShReviewDetail detail = shReviewService.detail(session);
        assertThat(detail.entries()).isNotEmpty();
        assertThat(detail.entries()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("CREATE");
            assertThat(entry.address()).endsWith(base + "/reviewed");
            assertThat(entry.draft()).isTrue();
            assertThat(entry.published()).isFalse();
        });

        ShReviewOutcome outcome = shReviewService.approve(session, "reviewer");
        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(shPostUnifiedRepository.findByState(ShPostState.PUBLISHED).stream()
                .anyMatch(row -> (base + "/reviewed").equals(row.getFurl())))
                .isTrue();
    }

    @Test
    @DisplayName("reverting a run undoes what it created")
    void revertingARunUndoesIt() {
        String base = base();
        String session = "run-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentSessionContext.set(session);
        run(false, folder(base), post(base + "/regret", base, "Regret"));
        ShAgentSessionContext.clear();

        ShReviewOutcome outcome = shReviewService.revert(session, "reviewer");

        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(shPostUnifiedRepository.findByState(ShPostState.DRAFT).stream()
                .anyMatch(row -> (base + "/regret").equals(row.getFurl())))
                .as("a reverted creation is trashed (SH55), which is recoverable — not purged")
                .isFalse();
    }

    @Test
    @DisplayName("an entry with no honest undo is reported with its reason, not approximated")
    void whatCannotBeRevertedSaysWhy() {
        String base = base();
        String session = "run-" + UUID.randomUUID().toString().substring(0, 8);
        ShAgentSessionContext.set(session);
        run(false, folder(base), post(base + "/gone", base, "Gone"));
        ShAgentSessionContext.clear();

        // Reverted once: the post is now in the trash.
        shReviewService.revert(session, "reviewer");
        // Reverting again has nothing left to do, and must say so rather than guess.
        ShReviewOutcome second = shReviewService.revert(session, "reviewer");

        assertThat(second.changed()).isZero();
        assertThat(second.skipped()).isNotEmpty();
        assertThat(second.skipped().get(0).reason()).isNotBlank();
    }
}
