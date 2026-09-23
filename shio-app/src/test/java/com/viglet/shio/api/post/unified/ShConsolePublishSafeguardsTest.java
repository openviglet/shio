/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
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

import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.review.ShReviewService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH996 — the console's publish gets the agent's safeguards.
 *
 * <p>The agent's publish has a dry run, a confirm token and a review entry; the console's had a
 * button, which made the surface P7 was written for the one with none of P7's instruments. The two
 * assertions the design asked for are here: a dry run writes nothing, and a console publish appears
 * in the queue where it can be reverted.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShConsolePublishSafeguardsTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh996c-" + RUN;

    @Autowired
    private ShPostUnifiedAPI api;
    @Autowired
    private ShConsolePublishPlan plan;
    @Autowired
    private ShReviewService shReviewService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    /** SH1064 — the Redirect system type has to exist before anything can write one. */
    @Autowired
    private com.viglet.shio.render.ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Set<String> posts = new HashSet<>();

    @BeforeAll
    void seed() {
        shRenderPostTypes.ensure();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant then = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(SITE);
            site.setDate(then);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(then);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("PT-SH996C-" + RUN);
            type.setTitle("SH996 Console Page");
            type.setSystem((byte) 0);
            type.setDate(then);
            type.setOwner("admin");
            entityManager.persist(type);

            // SH1064 — `/renamed` is published here and its draft moved in the test, which is
            // the shape a console rename has: two rows, two furls, one publish between them.
            for (String url : List.of("/planned", "/reviewed", "/renamed")) {
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
                draft.setData("{\"title\":\"" + url + "\"}");
                entityManager.persist(draft);
                posts.add(draft.getId());
            }
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                posts.forEach(id -> shPostUnifiedRepository.findById(id)
                        .forEach(shPostUnifiedRepository::delete)));
    }

    private boolean live(String url) {
        return shPostUnifiedRepository
                .findByIdAndState(SITE + url.replace('/', '-'), ShPostState.PUBLISHED).isPresent();
    }

    @Test
    @DisplayName("SH996: the console's dry run answers the agent's shape and writes nothing")
    void aDryRunWritesNothing() {
        String postId = SITE + "-planned";

        Object body = api.publish(postId, null, true, null, false, () -> "ana").getBody();

        assertThat(body).isInstanceOf(ShAgentChangeSet.class);
        ShAgentChangeSet answer = (ShAgentChangeSet) body;
        assertThat(answer.dryRun()).isTrue();
        assertThat(answer.results()).singleElement().satisfies(change -> {
            assertThat(change.action()).isEqualTo("publish");
            assertThat(change.detail())
                    .as("SH996: the press that puts a URL on the site for the first time is the one "
                            + "a curator most wants named before it happens")
                    .isEqualTo("this URL goes live for the first time");
        });
        assertThat(live("/planned")).as("SH996: a plan writes nothing").isFalse();
    }

    @Test
    @DisplayName("SH996: a console publish is in the review queue, as the curator's own day")
    void aConsolePublishIsReviewable() {
        String postId = SITE + "-reviewed";

        api.publish(postId, null, false, null, false, () -> "ana");

        assertThat(live("/reviewed")).isTrue();
        String session = ShAgentSessionContext.consoleSession("ana", Instant.now());
        assertThat(shReviewService.sessions(50))
                .as("SH996: a curator who published the wrong draft had the same need an agent run "
                        + "has and no screen for it")
                .anySatisfy(row -> {
                    assertThat(row.session()).isEqualTo(session);
                    assertThat(row.actor()).isEqualTo("ana");
                });
        assertThat(ShAgentSessionContext.current())
                .as("SH996: the thread is pooled, so a session left bound would stamp the next "
                        + "request's audit rows with this curator's day")
                .isEmpty();
    }

    /**
     * SH1064 — the dialog said which URL would stop answering and could do nothing about it.
     *
     * <p>The fixture is the console's own case: a draft whose URL differs from the live row's, so
     * publishing moves the live address. That is the {@code stops} SH996 already prints, and until
     * this shipped the paragraph printing it was information with no action beside it.</p>
     */
    @Test
    @DisplayName("SH1064: publishing a renamed draft with redirect=true leaves the old URL live")
    void aRenameCanLeaveItsRedirect() {
        String postId = SITE + "-renamed";
        api.publish(postId, null, false, null, false, () -> "ana");
        // Live at /renamed, draft now at /renamed-2026: publishing moves the live URL, which is
        // what a rename is here — the live row and the draft each carry their own furl (SH81).
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                        .ifPresent(draft -> {
                            draft.setFurl("/renamed-2026");
                            draft.setData("{\"title\":\"/renamed-2026\"}");
                            shPostUnifiedRepository.save(draft);
                        }));

        api.publish(postId, null, false, null, true, () -> "ana");

        ShPostUnified redirect = new TransactionTemplate(transactionManager).execute(status ->
                shPostUnifiedRepository.findAll().stream()
                        .filter(post -> post.getShSite() != null
                                && SITE.equals(post.getShSite().getName())
                                && "/renamed".equals(post.getFurl())
                                && post.getState() == ShPostState.PUBLISHED)
                        .findFirst().orElse(null));

        assertThat(redirect)
                .as("SH1064: a curator who renames a page breaks every bookmark into it, and the "
                        + "console's own dialog was the place that knew and said nothing actionable")
                .isNotNull();
        assertThat(redirect.getData()).contains("\"TO\":\"/renamed-2026\"", "\"PERMANENT\":true");
    }

    @Test
    @DisplayName("SH996: re-publishing unchanged content plans a noop, not a publish")
    void anUnchangedPlanIsANoop() {
        ShAgentChangeSet answer = plan.of(SITE + "-reviewed");

        assertThat(answer.changed()).isZero();
        assertThat(answer.results().get(0).detail()).isEqualTo("already live with this content");
    }

}
