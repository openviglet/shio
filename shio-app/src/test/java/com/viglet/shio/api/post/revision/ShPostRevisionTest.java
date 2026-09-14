/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.revision;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH986 — what a page was, after the row that held it is gone.
 *
 * <p>A post is {@code (id, state)} and the states are exactly two, so publishing deletes the live
 * row and writes the draft over it. Before this, the bytes a visitor read an hour ago could not be
 * recovered by anything in the product: {@code versions()} returned the two rows of one key,
 * {@code ShHistory} said a publish happened and carried no payload, and the review queue's undo was
 * narrower than it read.</p>
 *
 * <p>The closing assertion is the defect's own sentence: publish twice, restore the first revision,
 * and its bytes come back.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPostRevisionTest {

    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShPostRevisionService shPostRevisionService;
    @Autowired
    private ShPostRevisionRepository shPostRevisionRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String folderId;
    private String postTypeId;
    private final Set<String> written = new HashSet<>();

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("rev-site-" + UUID.randomUUID());
            site.setDescription("Revision test site");
            site.setUrl("http://example.test");
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
            postType.setName("PT-REV-" + UUID.randomUUID());
            postType.setTitle("Revision Test Type");
            postType.setDescription("Post type for revision tests");
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

    @AfterAll
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> written.forEach(id -> {
            shPostRevisionRepository.deleteAll(
                    shPostRevisionRepository.findByPostIdOrderByVersionNoDesc(id));
            shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
        }));
    }

    private ObjectNode data(String title) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(ShSystemPostTypeAttr.TITLE, title);
        return node;
    }

    private String createDraft(String title) {
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data(title), ShPostState.DRAFT, "admin", null, null, null));
        written.add(created.getId());
        return created.getId();
    }

    @Test
    @DisplayName("SH986: publish twice, restore the first revision, and its bytes come back")
    void anEarlierPublishedStateCanBeRestored() {
        String postId = createDraft("First");
        shPostUnifiedWriteService.publish(postId, "admin");

        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Second"), ShPostState.DRAFT, "admin"));
        shPostUnifiedWriteService.publish(postId, "admin");

        List<ShPostRevision> revisions = shPostRevisionService.revisions(postId);
        assertThat(revisions)
                .as("""
                        SH986: each publish captures the state it made visible, so two publishes \
                        are two revisions. Before this table the first one was simply gone — the \
                        publish deleted the row that held it.""")
                .hasSize(2);
        assertThat(revisions.get(0).getVersionNo()).isGreaterThan(revisions.get(1).getVersionNo());
        assertThat(revisions).allSatisfy(revision -> {
            assertThat(revision.getReason()).isEqualTo(ShPostRevision.Reason.PUBLISH.name());
            assertThat(revision.getState()).isEqualTo(ShPostState.PUBLISHED.name());
            assertThat(revision.getCapturedAt()).isNotNull();
            assertThat(revision.getPostTypeId())
                    .as("SH986: the type is named by the id that does not move, never by a name")
                    .isEqualTo(postTypeId);
        });

        ShPostRevision first = revisions.get(1);
        assertThat(first.getData()).contains("First");

        ShPostUnified restored = shPostUnifiedWriteService
                .restoreRevision(postId, first.getVersionNo(), "admin");

        assertThat(restored.getData())
                .as("SH986: the bytes of the first published state, back on the draft")
                .isEqualTo(first.getData());
        assertThat(restored.getState())
                .as("""
                        SH986: a restore lands on the draft and never on the live row — the \
                        curator asked to go back to a version, and publishing it is the separate \
                        act draft-default exists to keep separate (P7).""")
                .isEqualTo(ShPostState.DRAFT);
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .as("SH986: what visitors are reading is untouched by a restore")
                .hasValueSatisfying(live -> assertThat(live.getData()).contains("Second"));
    }

    @Test
    @DisplayName("SH986: the restore is itself a revision, so it can be undone")
    void aRestoreIsCaptured() {
        String postId = createDraft("Alpha");
        shPostUnifiedWriteService.publish(postId, "admin");
        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Beta"), ShPostState.DRAFT, "admin"));

        int published = shPostRevisionService.revisions(postId).get(0).getVersionNo();
        shPostUnifiedWriteService.restoreRevision(postId, published, "admin");

        List<ShPostRevision> revisions = shPostRevisionService.revisions(postId);
        assertThat(revisions.get(0).getReason())
                .as("""
                        SH986: an undo that destroys what it undid is the defect this table was \
                        added for, one level up — so the draft as it stood is captured before the \
                        restore overwrites it.""")
                .isEqualTo(ShPostRevision.Reason.ROLLBACK.name());
        assertThat(revisions.get(0).getData()).contains("Beta");
        assertThat(revisions.get(0).getState()).isEqualTo(ShPostState.DRAFT.name());
    }

    @Test
    @DisplayName("SH986: a draft save is not captured unless the instance asks for it")
    void savesAreNotCapturedByDefault() {
        String postId = createDraft("Quiet");
        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Quieter"), ShPostState.DRAFT, "admin"));

        assertThat(shPostRevisionService.capturesOnSave())
                .as("SH986: capture-on-save defaults off — the question a curator asks is what was "
                        + "live, and answering it does not need the fifty saves that produced it")
                .isFalse();
        assertThat(shPostRevisionService.revisions(postId))
                .as("SH986: nothing went live, so there is nothing a visitor saw to record")
                .isEmpty();
    }

    @Test
    @DisplayName("SH986: the next version is max + 1, which is what survives a prune")
    void versionNumbersDoNotRepeatAfterAPrune() {
        String postId = createDraft("Numbered");
        shPostUnifiedWriteService.publish(postId, "admin");
        shPostUnifiedWriteService.update(postId,
                new UpdateRequest(data("Numbered twice"), ShPostState.DRAFT, "admin"));
        shPostUnifiedWriteService.publish(postId, "admin");

        // The prune's shape, forced rather than waited for: delete the oldest and capture again.
        List<ShPostRevision> before = shPostRevisionService.revisions(postId);
        shPostRevisionRepository.delete(before.get(before.size() - 1));
        shPostUnifiedWriteService.publish(postId, "admin");

        List<Integer> numbers = shPostRevisionService.revisions(postId).stream()
                .map(ShPostRevision::getVersionNo).toList();
        assertThat(numbers)
                .as("""
                        SH986: a count would reissue the number a pruned revision used, and the \
                        unique constraint refuses the insert — which presents as publishing having \
                        stopped working, long after and nowhere near the prune.""")
                .doesNotHaveDuplicates()
                .isSortedAccordingTo((left, right) -> Integer.compare(right, left));
    }
}
