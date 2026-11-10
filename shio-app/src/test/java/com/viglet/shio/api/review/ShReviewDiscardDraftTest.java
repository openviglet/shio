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

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

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

import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.review.ShReviewService.Decision;
import com.viglet.shio.api.review.dto.ShReviewDetail;
import com.viglet.shio.api.review.dto.ShReviewOutcome;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.review.ShReviewDecision.Kind;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1418 — the review queue can discard an agent's edit to a page that was never published.
 *
 * <p>Discard of an UPDATE used to mean "back to the published row", so a page a person wrote and
 * left as a draft, then an agent rewrote, offered no undo in the queue. The person's text is now
 * kept on the run's first write over it, and rejecting the change puts it back.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShReviewDiscardDraftTest {

    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShReviewService shReviewService;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String folderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("discard-draft-" + UUID.randomUUID().toString().substring(0, 8));
            site.setUrl("http://discard-draft.example.test");
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
            folderId = root.getId();

            ShWidget text = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());
            ShPostType postType = new ShPostType();
            postType.setName("PT-DISCARD-" + UUID.randomUUID());
            postType.setTitle("Discard Draft Type");
            postType.setDescription("Post type for SH1418");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            ShPostTypeAttr title = new ShPostTypeAttr();
            title.setName(ShSystemPostTypeAttr.TITLE);
            title.setLabel("Title");
            title.setDescription("Title field");
            title.setOrdinal(1);
            title.setRequired((byte) 1);
            title.setIsTitle((byte) 1);
            title.setIsSummary((byte) 0);
            title.setShWidget(text);
            title.setShPostType(postType);
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(title);
            postType.setShPostTypeAttrs(attrs);
            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    @AfterEach
    void clearSession() {
        ShAgentSessionContext.clear();
    }

    @Test
    @DisplayName("SH1418: a person's draft an agent rewrote comes back when the change is rejected")
    void rejectingAnEditToANeverPublishedPageRestoresThePersonsText() {
        String postId = draft("The curator's own words");
        String session = "sess-" + UUID.randomUUID();
        asAgent(session, () -> shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("The agent's rewrite"), ShPostState.DRAFT, "agent")));
        asAgent(session, () -> shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("The agent's second pass"), ShPostState.DRAFT, "agent")));

        ShReviewDetail detail = shReviewService.detail(session);
        assertThat(detail.entries())
                .filteredOn(entry -> ShContentAction.UPDATE.name().equals(entry.action()))
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.revertable())
                        .as("SH1418: an edit to a never-published page is offered a Discard")
                        .isTrue());

        ShReviewOutcome outcome = shReviewService.decide(session,
                List.of(new Decision(postId, Kind.REJECT, "not what I wrote")), "curator");

        assertThat(outcome.skipped()).as("SH1418: nothing skipped").isEmpty();
        assertThat(outcome.changed()).isEqualTo(1);
        assertThat(titleOf(postId)).isEqualTo("The curator's own words");
    }

    @Test
    @DisplayName("SH1418: an edit to a page the run itself created still says to revert the creation")
    void anEditToAPageTheRunCreatedStillPointsAtTheCreation() {
        String session = "sess-" + UUID.randomUUID();
        String postId = asAgent(session, () -> draft("Made by the agent"));
        asAgent(session, () -> shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Refined by the agent"), ShPostState.DRAFT, "agent")));

        assertThat(shReviewService.detail(session).entries())
                .filteredOn(entry -> ShContentAction.UPDATE.name().equals(entry.action()))
                .allSatisfy(entry -> assertThat(entry.revertable()).isFalse());
    }

    // --- fixtures ---

    private ObjectNode data(String title) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(ShSystemPostTypeAttr.TITLE, title);
        return node;
    }

    private String draft(String title) {
        return shPostUnifiedWriteService.create(new CreateRequest(folderId, postTypeId, data(title),
                ShPostState.DRAFT, "curator", null, null, null)).getId();
    }

    private String titleOf(String postId) {
        return new TransactionTemplate(transactionManager).execute(status -> shPostUnifiedRepository
                .findByIdAndState(postId, ShPostState.DRAFT).orElseThrow().getTitle());
    }

    private <T> T asAgent(String session, Supplier<T> work) {
        ShAgentSessionContext.set(session);
        try {
            return work.get();
        } finally {
            ShAgentSessionContext.clear();
        }
    }
}
