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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentChangeCursor;
import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.gate.ShAgentGate;
import com.viglet.shio.api.gate.ShAgentGateApiService;
import com.viglet.shio.api.release.ShReleaseService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.gate.ShPublishGateRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1138 — a scheduled publish and a scheduled release ask the site's publish gates when they fire.
 *
 * <p>The SH15 sweep went live through {@code publishScheduled} and the release runner, and neither
 * asked a gate, so a page scheduled for Monday went live on Monday whatever a gate declared since —
 * and scheduling itself asks none either, so it was every scheduled publish. The fixture's stopped
 * page is <b>complete</b>, so SH1009's completeness check lets it through and only the gate can stop
 * it: its draft carries a field its type does not declare, which {@code verify} calls an error.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShScheduledGateTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh1138-" + RUN;
    private static final String STOPPED = SITE + "-stopped";
    private static final String SOUND = SITE + "-sound";
    private static final String HELD = SITE + "-held";

    @Autowired
    private ShPostScheduler scheduler;
    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShPostUnifiedRepository postRepository;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShAgentChangesService changesService;
    @Autowired
    private ShAgentGateApiService gateApi;
    @Autowired
    private ShPublishGateRepository gateRepository;
    @Autowired
    private ShReleaseService releaseService;
    @Autowired
    private ShWidgetRepository widgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Set<String> posts = new HashSet<>();
    private String siteId;

    @BeforeAll
    void seed() {
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

            ShWidget text = entityManager.find(ShWidget.class,
                    widgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());
            ShPostType type = new ShPostType();
            type.setName("PT-SH1138-" + RUN);
            type.setTitle("SH1138 Page");
            type.setSystem((byte) 0);
            type.setDate(then);
            type.setOwner("admin");
            ShPostTypeAttr title = new ShPostTypeAttr();
            title.setName(ShSystemPostTypeAttr.TITLE);
            title.setLabel("Title");
            title.setOrdinal(1);
            title.setRequired((byte) 1);
            title.setIsTitle((byte) 1);
            title.setIsSummary((byte) 0);
            title.setShWidget(text);
            title.setShPostType(type);
            type.setShPostTypeAttrs(new HashSet<>(Set.of(title)));
            entityManager.persist(type);

            for (String id : List.of(STOPPED, SOUND, HELD)) {
                ShPostUnified draft = new ShPostUnified();
                draft.setId(id);
                draft.setState(ShPostState.DRAFT);
                draft.setShSite(site);
                draft.setShFolder(root);
                draft.setShPostType(type);
                draft.setTitle(id);
                draft.setFurl("/" + id);
                draft.setLocale("en");
                draft.setOwner("admin");
                draft.setDate(then);
                // Complete in every case; the stopped and held pages also carry a key their type does
                // not declare, which verify reports as an error and so the gate below stops.
                draft.setData(SOUND.equals(id)
                        ? "{\"" + ShSystemPostTypeAttr.TITLE + "\":\"sound\"}"
                        : "{\"" + ShSystemPostTypeAttr.TITLE + "\":\"x\",\"stray\":\"not declared\"}");
                entityManager.persist(draft);
                posts.add(id);
            }
        });
        gateApi.put(SITE, "ready", new ShAgentGate("ready", true,
                List.of(new ShAgentGate.Check("verify", "error"))));
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            gateRepository.findBySiteIdOrderByNameAsc(siteId).forEach(gateRepository::delete);
            posts.forEach(id -> postRepository.findById(id).forEach(postRepository::delete));
        });
    }

    private static String now() {
        return new ShAgentChangeCursor(Instant.now(), "").toString();
    }

    private List<ShAgentChanges.Incident> incidentsAbout(String cursor, String subject) {
        ShAgentChanges feed = changesService.changes(SITE, cursor, 100, true, 0);
        return feed.incidents() == null ? List.of()
                : feed.incidents().stream().filter(i -> subject.equals(i.subject())).toList();
    }

    @Test
    void sh1138AScheduledPublishAGateStopsIsStillADraftAndTheFeedNamesTheGate() {
        writeService.schedule(STOPPED, Instant.now().minusSeconds(5), null, "curator");
        String before = now();

        scheduler.sweep();

        assertThat(postRepository.findByIdAndState(STOPPED, ShPostState.PUBLISHED))
                .as("SH1138: the page went live past a gate it fails, because the sweep asked none")
                .isEmpty();
        assertThat(postRepository.findByIdAndState(STOPPED, ShPostState.DRAFT).orElseThrow()
                .getPublishScheduledDate())
                .as("SH1138: the schedule stands, so the page goes live on the sweep after it is fixed")
                .isNotNull();
        List<ShAgentChanges.Incident> stopped = incidentsAbout(before, "id:" + STOPPED);
        assertThat(stopped)
                .as("SH1138: a stopped launch is in the feed a curator reads, naming the gate")
                .singleElement()
                .satisfies(incident -> {
                    assertThat(incident.kind()).isEqualTo("schedule");
                    // The gate's own detail, which names the field and its fix.
                    assertThat(incident.message()).contains("'ready'").contains("'stray'");
                    assertThat(incident.retry().op())
                            .as("SH1138: and says how to try again, which is an ordinary gated publish")
                            .isEqualTo(ShAgentBatchService.POST_PUBLISH);
                });
    }

    @Test
    void sh1138AScheduledPublishThatPassesTheGateGoesLive() {
        writeService.schedule(SOUND, Instant.now().minusSeconds(5), null, "curator");

        scheduler.sweep();

        assertThat(postRepository.findByIdAndState(SOUND, ShPostState.PUBLISHED))
                .as("SH1138: a gate that passes does not hold the launch")
                .isPresent();
    }

    @Test
    void sh1138AScheduledReleaseWithAPageTheGateStopsStaysScheduledAndNothingGoesLive() {
        String name = "launch-" + RUN;
        batchService.run(new com.viglet.shio.api.agent.dto.ShAgentBatch(false, List.of(
                new com.viglet.shio.api.agent.dto.ShAgentBatch.Op(ShAgentBatchService.RELEASE_UPSERT,
                        "release:" + SITE + "/" + name, null, null, null),
                new com.viglet.shio.api.agent.dto.ShAgentBatch.Op(ShAgentBatchService.RELEASE_ADD,
                        "release:" + SITE + "/" + name, null, null, null,
                        new tools.jackson.databind.ObjectMapper().createObjectNode().set("posts",
                                new tools.jackson.databind.ObjectMapper().createArrayNode()
                                        .add("/" + HELD))))), false, "curator");
        releaseService.scheduleAt(releaseService.require(siteId, SITE, name),
                Instant.now().minusSeconds(5), "curator");
        String before = now();

        scheduler.sweep();

        assertThat(releaseService.require(siteId, SITE, name).getState())
                .as("SH1138: one page the gate stops stops the release, which runs whole or not at all")
                .isEqualTo("SCHEDULED");
        assertThat(postRepository.findByIdAndState(HELD, ShPostState.PUBLISHED)).isEmpty();
        assertThat(incidentsAbout(before, "release:" + name))
                .as("SH1138: and the feed says which gate and which page")
                .singleElement()
                .satisfies(incident -> assertThat(incident.message())
                        .contains("'ready'").contains("id:" + HELD));
    }
}
