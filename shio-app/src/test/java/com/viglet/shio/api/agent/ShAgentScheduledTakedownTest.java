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

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.mcp.tools.ShPublishTool;
import com.viglet.shio.api.agent.mcp.tools.ShWriteTool;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH992 — a takedown that waits, and a publish tool that can say when.
 *
 * <p>{@code shio_publish} built its op with no {@code when}, and {@code post.unpublish} bound one and
 * never read it, so an embargoed takedown dated for December took the page down at once and reported
 * success. Both halves are asserted where the defect crossed: the page's live state after the call.</p>
 *
 * @author Alexandre Oliveira
 */
// ShScheduleRequesterTest's context, deliberately (SH423): every assertion reads rows this class seeds
// under names nobody else uses, so it measures nothing about the instance and spends no slot.
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentScheduledTakedownTest {

    private static final String RUN = java.util.UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh992-" + RUN;
    private static final String LAUNCH = "2099-08-01T09:00:00Z";
    private static final String DECEMBER = "2099-12-01T09:00:00Z";

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShPublishTool shPublishTool;
    @Autowired
    private ShWriteTool shWriteTool;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String siteId;
    private String folderId;
    private String typeId;
    private final java.util.Set<String> seeded = new java.util.HashSet<>();

    @BeforeAll
    void seedASite() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant then = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(SITE);
            site.setDate(then);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(then);
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();

            ShPostType type = new ShPostType();
            type.setName("PT-SH992-" + RUN);
            type.setTitle("Takedown Article");
            type.setSystem((byte) 0);
            type.setDate(then);
            type.setOwner("admin");
            entityManager.persist(type);
            typeId = type.getId();
        });
    }

    /** The posts go, the way ShScheduleRequesterTest leaves the context it shares. */
    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> seeded.forEach(id ->
                shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete)));
    }

    /** A post at {@code url}: a draft, and a published row beside it when {@code live}. */
    private String seed(String url, boolean live) {
        String id = "sh992-" + RUN + url.replace('/', '-');
        seeded.add(id);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (ShPostState state : live
                    ? List.of(ShPostState.DRAFT, ShPostState.PUBLISHED) : List.of(ShPostState.DRAFT)) {
                ShPostUnified row = new ShPostUnified();
                row.setId(id);
                row.setState(state);
                row.setShSite(entityManager.find(ShSite.class, siteId));
                row.setShFolder(entityManager.find(ShFolder.class, folderId));
                row.setShPostType(entityManager.find(ShPostType.class, typeId));
                row.setTitle(url);
                row.setFurl(url);
                row.setLocale("en");
                row.setOwner("admin");
                row.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                row.setData("{\"title\":\"" + url + "\"}");
                entityManager.persist(row);
            }
        });
        return id;
    }

    private ShPostUnified draft(String id) {
        return shPostUnifiedRepository.findByIdAndState(id, ShPostState.DRAFT).orElseThrow();
    }

    private boolean live(String id) {
        return shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED).isPresent();
    }

    private ShAgentChangeSet.Change run(boolean dryRun, String op, String url, String when,
                                        Boolean cancel) {
        ShAgentChangeSet changes = shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(
                new ShAgentBatch.Op(op, "post:" + SITE + url, null, null, null, null, when, null,
                        null, null, null, null, cancel))), false, "ut-sh992");
        return changes.results().get(0);
    }

    @Test
    @DisplayName("SH992: shio_publish with when leaves the page unpublished until the instant")
    void thePublishToolSchedules() {
        String id = seed("/launch", false);
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.putArray("address").add("post:" + SITE + "/launch");
        arguments.put("when", LAUNCH);

        String answer = shPublishTool.call(arguments);

        assertThat(live(id))
                .as("SH992: the tool built its op with no `when`, so a launch dated for August went "
                        + "live on the call and answered success")
                .isFalse();
        assertThat(draft(id).getPublishScheduledDate()).isEqualTo(Instant.parse(LAUNCH));
        assertThat(answer).contains("scheduled to publish at " + LAUNCH);
    }

    @Test
    @DisplayName("SH992: post.unpublish with a future when leaves the page live")
    void anUnpublishWithWhenWaits() {
        String id = seed("/promo", true);

        ShAgentChangeSet.Change change = run(false, ShAgentBatchService.POST_UNPUBLISH, "/promo",
                DECEMBER, null);

        assertThat(live(id))
                .as("SH992: postUnpublish never read `when`, so a takedown dated for December took "
                        + "the page down now and reported success")
                .isTrue();
        assertThat(draft(id).getUnpublishScheduledDate()).isEqualTo(Instant.parse(DECEMBER));
        assertThat(change.detail()).isEqualTo("scheduled to unpublish at " + DECEMBER);
    }

    @Test
    @DisplayName("SH992: the dry run reports the schedule the run sets, word for word, and writes nothing")
    void theDryRunMatchesTheRun() {
        String id = seed("/embargo", true);

        ShAgentChangeSet.Change planned = run(true, ShAgentBatchService.POST_UNPUBLISH, "/embargo",
                DECEMBER, null);
        assertThat(draft(id).getUnpublishScheduledDate()).as("SH992: a dry run writes nothing").isNull();
        ShAgentChangeSet.Change done = run(false, ShAgentBatchService.POST_UNPUBLISH, "/embargo",
                DECEMBER, null);

        assertThat(planned.action()).isEqualTo(done.action());
        assertThat(planned.detail()).isEqualTo(done.detail());
        assertThat(run(false, ShAgentBatchService.POST_UNPUBLISH, "/embargo", DECEMBER, null).action())
                .as("SH992: the same instant again is a noop (P5)")
                .isEqualTo("noop");
    }

    @Test
    @DisplayName("SH992: scheduling a launch keeps the takedown already set, and cancel clears only its own")
    void cancelClearsItsOwnInstant() {
        String id = seed("/window", false);
        run(false, ShAgentBatchService.POST_UNPUBLISH, "/window", DECEMBER, null);
        run(false, ShAgentBatchService.POST_PUBLISH, "/window", LAUNCH, null);

        assertThat(draft(id).getUnpublishScheduledDate())
                .as("SH992: SH342's path passed null for the unpublish instant, so scheduling a "
                        + "launch cancelled the takedown set for the same page")
                .isEqualTo(Instant.parse(DECEMBER));

        ShAgentChangeSet.Change cancelled = run(false, ShAgentBatchService.POST_UNPUBLISH, "/window",
                null, true);

        assertThat(cancelled.detail())
                .as("SH992: a cancel reports what it cleared")
                .isEqualTo("cancelled the unpublish scheduled for " + DECEMBER);
        assertThat(draft(id).getUnpublishScheduledDate()).isNull();
        assertThat(draft(id).getPublishScheduledDate()).isEqualTo(Instant.parse(LAUNCH));
        assertThat(run(false, ShAgentBatchService.POST_UNPUBLISH, "/window", null, true).detail())
                .isEqualTo("no unpublish scheduled");
    }

    @Test
    @DisplayName("SH992: cancelling one of two instants does not reattribute the other")
    void aCancelKeepsTheRequester() {
        String id = seed("/credit", false);
        shPostUnifiedWriteService.schedule(id, Instant.parse(LAUNCH), Instant.parse(DECEMBER), "ana");

        shPostUnifiedWriteService.schedule(id, null, Instant.parse(DECEMBER), "bob");

        assertThat(draft(id).getScheduleRequestedBy())
                .as("SH992: bob called off the launch; the takedown that still fires is ana's")
                .isEqualTo("ana");
    }

    @Test
    @DisplayName("SH992: when and cancel on one op is refused")
    void whenAndCancelAreRefused() {
        seed("/both", false);
        assertThatThrownBy(() -> run(true, ShAgentBatchService.POST_PUBLISH, "/both", LAUNCH, true))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(((ShAgentBatchService.BatchOpException) failure)
                        .problem().toProblemDetail().getType().toString())
                        .endsWith("cancel-with-when"));
    }

    @Test
    @DisplayName("SH992: shio_write refuses a cancel it does not declare, rather than publishing without it")
    void theWriteToolRefusesAnUndeclaredField() {
        String id = seed("/stray", false);
        ObjectNode arguments = objectMapper.createObjectNode();
        ObjectNode op = arguments.putArray("ops").addObject();
        op.put("op", "post.publish");
        op.put("address", "post:" + SITE + "/stray");
        op.put("cancel", true);

        assertThatThrownBy(() -> shWriteTool.call(arguments))
                .isInstanceOf(ShWriteTool.UndeclaredOpFieldException.class)
                .satisfies(failure -> assertThat(((ShTeachingException) failure).problem()
                        .toProblemDetail().getProperties().get("fix").toString())
                        .contains("shio_publish"));
        assertThat(live(id))
                .as("SH992: dropped, the cancel became a publish")
                .isFalse();
    }
}
