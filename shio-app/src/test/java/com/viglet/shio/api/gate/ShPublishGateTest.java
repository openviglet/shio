/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.post.unified.ShConsolePublishPlan;
import com.viglet.shio.api.post.unified.ShPostUnifiedAPI;
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
 * SH997 — a check that stands between content and visitors, on every surface.
 *
 * <p>Shio's instruments were all opt-in, so the only gate that existed asked who may publish and never
 * whether this content should. A gate is the second question, and the assertion that matters is that
 * it cannot be published around: the agent's op, the console's button and a release run all evaluate
 * it, because a rule one surface enforces and another does not is worse than no rule.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShPublishGateTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh997-" + RUN;

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShPublishGateService shPublishGateService;
    @Autowired
    private ShAgentGateApiService shAgentGateApiService;
    @Autowired
    private ShConsolePublishPlan shConsolePublishPlan;
    @Autowired
    private ShPostUnifiedAPI api;
    @Autowired
    private ShPublishGateRepository shPublishGateRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
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
                    shWidgetRepository.findFirstByName(ShSystemWidget.TEXT).getId());
            ShWidget html = entityManager.find(ShWidget.class,
                    shWidgetRepository.findFirstByName(ShSystemWidget.HTML_EDITOR).getId());
            ShPostType type = new ShPostType();
            type.setName("PT-SH997-" + RUN);
            type.setTitle("SH997 Page");
            type.setSystem((byte) 0);
            type.setDate(then);
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
            // A required field is the cheapest thing a gate can be right about: `required-field-empty`
            // is an ERROR in the content check, where `link-unresolved` is deliberately a warning —
            // a front end may own routes the CMS has never heard of (SH100).
            ShPostTypeAttr subtitle = new ShPostTypeAttr();
            subtitle.setName("subtitle");
            subtitle.setLabel("Subtitle");
            subtitle.setOrdinal(3);
            subtitle.setRequired((byte) 1);
            subtitle.setIsTitle((byte) 0);
            subtitle.setIsSummary((byte) 0);
            subtitle.setShWidget(text);
            subtitle.setShPostType(type);
            ShPostTypeAttr body = new ShPostTypeAttr();
            body.setName("body");
            body.setLabel("Body");
            body.setOrdinal(2);
            body.setRequired((byte) 0);
            body.setIsTitle((byte) 0);
            body.setIsSummary((byte) 0);
            body.setShWidget(html);
            body.setShPostType(type);
            type.setShPostTypeAttrs(new HashSet<>(Set.of(title, body, subtitle)));
            entityManager.persist(type);

            for (String url : List.of("/broken", "/sound", "/console")) {
                ShPostUnified draft = new ShPostUnified();
                draft.setId(SITE + url.replace('/', '-'));
                draft.setState(ShPostState.DRAFT);
                draft.setShSite(site);
                draft.setShFolder(root);
                draft.setShPostType(type);
                draft.setTitle(url);
                draft.setFurl(url);
                draft.setLocale("en");
                draft.setOwner("admin");
                draft.setDate(then);
                // `/sound` is complete; the other two leave the required field empty, which is what
                // the gate is about. The keys are the post-type's own attribute names — a key it does
                // not declare is itself an error, which is the gate being right about the fixture.
                draft.setData(url.equals("/sound")
                        ? "{\"" + ShSystemPostTypeAttr.TITLE + "\":\"sound\",\"subtitle\":\"ready\","
                                + "\"body\":\"<p>fine</p>\"}"
                        : "{\"" + ShSystemPostTypeAttr.TITLE + "\":\"" + url + "\","
                                + "\"body\":\"<p>unfinished</p>\"}");
                entityManager.persist(draft);
                posts.add(draft.getId());
            }
        });
        shAgentGateApiService.put(SITE, "ready", new ShAgentGate("ready", true,
                List.of(new ShAgentGate.Check("verify", "error"))));
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            shPublishGateRepository.findBySiteIdOrderByNameAsc(siteId)
                    .forEach(shPublishGateRepository::delete);
            posts.forEach(id -> shPostUnifiedRepository.findById(id)
                    .forEach(shPostUnifiedRepository::delete));
        });
    }

    private boolean live(String url) {
        return shPostUnifiedRepository
                .findByIdAndState(SITE + url.replace('/', '-'), ShPostState.PUBLISHED).isPresent();
    }

    private void publish(String url) {
        shAgentBatchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                ShAgentBatchService.POST_PUBLISH, "post:" + SITE + url, null, null, null, null))),
                false, "ut-sh997");
    }

    @Test
    @DisplayName("SH997: a failing gate refuses the agent's publish, naming the gate and the check")
    void theAgentIsStopped() {
        assertThatThrownBy(() -> publish("/broken"))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> {
                    var problem = ((ShAgentBatchService.BatchOpException) failure).problem()
                            .toProblemDetail();
                    assertThat(String.valueOf(problem.getType())).endsWith("publish-gate-failed");
                    assertThat(problem.getTitle())
                            .as("SH997: a 409 that says only 'a gate failed' sends a curator to read "
                                    + "a YAML file to find out which sentence was about them")
                            .contains("ready")
                            .contains("post:" + SITE + "/broken");
                    assertThat(problem.getProperties().get("fix").toString())
                            .contains("/agent/verify");
                });
        assertThat(live("/broken")).isFalse();
    }

    @Test
    @DisplayName("SH997: a page that passes is published exactly as before")
    void aSoundPageGoesThrough() {
        publish("/sound");

        assertThat(live("/sound"))
                .as("SH997: a gate is a check, not a hold — content that passes must not notice it "
                        + "is there")
                .isTrue();
    }

    @Test
    @DisplayName("SH997: the console cannot publish around it, and its dry run says which gate stops it")
    void theConsoleIsStoppedToo() {
        String postId = SITE + "-console";

        assertThat(shConsolePublishPlan.of(postId).results().get(0).detail())
                .as("SH997: a plan that could not say which gate would stop it promises what the run "
                        + "refuses")
                .contains("gate 'ready' stops this: verify");
        assertThatThrownBy(() -> api.publish(postId, null, false, () -> "ana"))
                .isInstanceOf(ShPublishGateService.GateFailedException.class);
        assertThat(live("/console")).isFalse();
    }

    @Test
    @DisplayName("SH997: audit is refused as a gate check, because the instance runs no browser")
    void auditCannotBeAServerSideGate() {
        assertThatThrownBy(() -> shAgentGateApiService.put(SITE, "visual",
                new ShAgentGate("visual", true, List.of(new ShAgentGate.Check("audit", "error")))))
                .isInstanceOf(ShPublishGateService.UnknownCheckException.class)
                .satisfies(failure -> assertThat(((ShPublishGateService.UnknownCheckException) failure)
                        .problem().toProblemDetail().getDetail())
                        .as("SH997: a check the instance cannot run must be refused rather than "
                                + "stored, or the gate silently passes what nobody looked at")
                        .contains("does not run a browser"));
    }
}
