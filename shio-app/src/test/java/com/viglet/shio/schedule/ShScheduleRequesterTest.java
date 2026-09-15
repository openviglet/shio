/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.history.ShHistoryPageableRepository;
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
 * SH991 — a transition the scheduler fires names the person who set the schedule.
 *
 * <p>The SH15 sweep publishes as {@code scheduler}, so a page that went live at midnight read in the
 * Activity view as the scheduler's work and nobody else's. The person who chose the time is who a
 * curator is asking about, and the audit row now names them beside the actor that ran it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShScheduleRequesterTest {

    private static final Instant DUE = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShPostRevisionRepository shPostRevisionRepository;
    @Autowired
    private ShHistoryPageableRepository shHistoryPageableRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> written = new HashSet<>();
    private String folderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName("sh991-req-" + UUID.randomUUID().toString().substring(0, 8));
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();

            ShWidget text = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());
            ShPostType type = new ShPostType();
            type.setName("PT-SH991-REQ-" + UUID.randomUUID());
            type.setTitle("SH991 Requester Type");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            ShPostTypeAttr title = new ShPostTypeAttr();
            title.setName(ShSystemPostTypeAttr.TITLE);
            title.setLabel("Title");
            title.setOrdinal(1);
            title.setRequired((byte) 0);
            title.setIsTitle((byte) 1);
            title.setIsSummary((byte) 0);
            title.setShWidget(text);
            title.setShPostType(type);
            Set<ShPostTypeAttr> attrs = new HashSet<>();
            attrs.add(title);
            type.setShPostTypeAttrs(attrs);
            entityManager.persist(type);
            postTypeId = type.getId();
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> written.forEach(id -> {
            shPostRevisionRepository.deleteAll(shPostRevisionRepository.findByPostIdOrderByVersionNoDesc(id));
            shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
        }));
    }

    private String createDraft(String title) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.TITLE, title);
        ShPostUnified created = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null, null));
        written.add(created.getId());
        return created.getId();
    }

    private ShPostUnified draft(String postId) {
        return shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT).orElseThrow();
    }

    private ShHistory last(String postId, ShContentAction action) {
        List<ShHistory> rows = shHistoryPageableRepository
                .findByShObjectOrderByDateDesc(postId, PageRequest.of(0, 50)).stream()
                .filter(row -> action.name().equals(row.getAction()))
                .sorted(Comparator.comparing(ShHistory::getDate))
                .toList();
        return rows.get(rows.size() - 1);
    }

    @Test
    @DisplayName("SH991: a scheduled publish is the scheduler's act and names who scheduled it")
    void aScheduledPublishNamesItsRequester() {
        String postId = createDraft("Midnight launch");
        shPostUnifiedWriteService.schedule(postId, DUE, null, "ana");
        assertThat(draft(postId).getScheduleRequestedBy()).isEqualTo("ana");

        shPostUnifiedWriteService.publishScheduled(postId);

        ShHistory published = last(postId, ShContentAction.PUBLISH);
        assertThat(published.getOwner())
                .as("SH991: the actor is still the scheduler — that is what ran it")
                .isEqualTo("scheduler");
        assertThat(published.getDescription())
                .as("SH991: and the person who chose the time is on the same row")
                .endsWith("(scheduled by ana)");
        assertThat(draft(postId).getScheduleRequestedBy())
                .as("SH991: nothing is left scheduled, so no stale name stays behind")
                .isNull();
    }

    @Test
    @DisplayName("SH991: a scheduled unpublish names its requester, and a direct publish names nobody")
    void anUnpublishToo() {
        String postId = createDraft("Promo");
        shPostUnifiedWriteService.publish(postId, "carol");
        assertThat(last(postId, ShContentAction.PUBLISH).getDescription())
                .as("SH991: a person publishing directly is the whole story")
                .doesNotContain("scheduled by");

        shPostUnifiedWriteService.schedule(postId, null, DUE, "dan");
        shPostUnifiedWriteService.unpublishScheduled(postId);

        assertThat(last(postId, ShContentAction.UNPUBLISH).getDescription())
                .endsWith("(scheduled by dan)");
    }

    @Test
    @DisplayName("SH991: clearing a schedule clears who set it")
    void clearingForgets() {
        String postId = createDraft("Changed my mind");
        shPostUnifiedWriteService.schedule(postId, Instant.parse("2031-01-01T00:00:00Z"), null, "erin");
        shPostUnifiedWriteService.schedule(postId, null, null, "frank");

        assertThat(draft(postId).getScheduleRequestedBy()).isNull();
    }
}
