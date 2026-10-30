/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1050 — the dashboard's numbers, counted rather than measured.
 *
 * <h2>The defect, stated as a test</h2>
 *
 * <p>Home fetched every site and every post type on mount and used one property of each: the
 * length. There was no count endpoint, so "how many" could only be answered by transferring
 * everything — on the first screen after logging in, with the tiles blank until both collections
 * landed. An instance with four hundred sites paid four hundred rows to draw the number 400.</p>
 *
 * <h2>What is asserted, and what it would take to fake</h2>
 *
 * <p>The window query is the one with a real edge, so it is driven at that edge: a publish inside
 * the week counts, one outside it does not, a trashed post never does, and a post scheduled both up
 * and down inside the window counts <b>once</b> — which is the honest answer to "how many things
 * happen this week" and the one a naive `count(publishAt) + count(unpublishAt)` gets wrong.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-console-summary;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShConsoleSummaryAPITest {

    @Autowired
    private ShConsoleSummaryAPI api;
    @Autowired
    private com.viglet.shio.api.agent.ShAgentContextService shAgentContextService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private Instant now;

    @BeforeAll
    void seed() {
        now = Instant.now();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);

            ShSite site = new ShSite();
            site.setName("summary-" + suffix);
            site.setDate(now);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(now);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("SummaryPage" + suffix);
            type.setTitle("Summary page");
            type.setSystem((byte) 0);
            type.setDate(now);
            type.setOwner("admin");
            entityManager.persist(type);

            // Inside the window, going up.
            entityManager.persist(scheduled(site, root, type, "soon-up",
                    now.plusSeconds(3600), null, null));
            // Inside the window, going up AND coming down: one post, one thing happening.
            entityManager.persist(scheduled(site, root, type, "soon-both",
                    now.plusSeconds(7200), now.plusSeconds(10_800), null));
            // Past the horizon.
            entityManager.persist(scheduled(site, root, type, "later",
                    now.plusSeconds(60L * 60 * 24 * 30), null, null));
            // In the window and in the trash, which is not a thing that happens this week.
            entityManager.persist(scheduled(site, root, type, "trashed",
                    now.plusSeconds(3600), null, now));
            // SH1336 — live with no friendly URL: verify's `routes` calls that an error.
            ShPostUnified unreachable = scheduled(site, root, type, "unreachable", null, null, null);
            unreachable.setState(ShPostState.PUBLISHED);
            unreachable.setFurl(null);
            entityManager.persist(unreachable);
            unreachableId = unreachable.getId();
        });
    }

    private String unreachableId;

    @Test
    void aPageThatFailsACheckIsNamedForTheInbox() {
        ShConsoleSummary.Checks checks = api.summary().checks();

        assertThat(checks.errors())
                .as("SH1336: a published page with no URL is an error-level finding, counted")
                .isPositive();
        assertThat(checks.pages())
                .as("""
                        SH1336: and it is named, with the id the console's editor route takes, so \
                        home lists it as a link. Before, a curator learned of a failing page only by \
                        opening it.""")
                .anySatisfy(page -> {
                    assertThat(page.id()).isEqualTo(unreachableId);
                    assertThat(page.rule()).isEqualTo("url-missing");
                })
                .hasSizeLessThanOrEqualTo(5);
    }

    private ShPostUnified scheduled(ShSite site, ShFolder folder, ShPostType type, String name,
                                    Instant publishAt, Instant unpublishAt, Instant deletedAt) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.DRAFT);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(name);
        post.setFurl("/" + name);
        post.setDate(now);
        post.setData("{}");
        post.setPublishScheduledDate(publishAt);
        post.setUnpublishScheduledDate(unpublishAt);
        post.setDeletedDate(deletedAt);
        return post;
    }

    @Test
    void theWindowCountsWhatHappensInIt() {
        long soon = shPostUnifiedRepository.countScheduledBetween(now, now.plus(ShConsoleSummary.SOON));

        assertThat(soon)
                .as("SH1050: two posts have a transition inside the week — one going up, one doing "
                        + "both. The one a month out and the one in the trash are not things that "
                        + "happen this week, and a post scheduled up AND down is one post.")
                .isEqualTo(2);
    }

    @Test
    void aWindowThatHasPassedHoldsNothing() {
        // The other side of the edge, so the comparison is shown to be a comparison. A window
        // entirely in the past must be empty however many transitions exist ahead of it.
        long before = shPostUnifiedRepository.countScheduledBetween(
                now.minus(ShConsoleSummary.SOON), now);

        assertThat(before)
                .as("SH1050: a past window counted future transitions, so the bound is not being "
                        + "applied and every dashboard would report the whole backlog as due")
                .isZero();
    }

    @Test
    void thePackCarriesTheSameNumbersAndOnlyWhenAsked() {
        // SH1050's second criterion. The pack says what a caller CAN do; `include=status` says what
        // is going on. It is opt-in for `usage`'s reason rather than `releases`': a review queue
        // moves when anybody decides anything, so a default pack carrying it would put a number
        // that changes on every publish inside the document SH116's conditional read keeps matching.
        assertThat(shAgentContextService.context(null, 0, null, null, null).status())
                .as("SH1050: the default pack carries the instance's state, so a caller who asked "
                        + "for the model and the sitemap is paying for a number that moves under "
                        + "them — and the digest they cache it by moves with it")
                .isNull();

        var asked = shAgentContextService.context(null, 0, null, "status", null).status();
        assertThat(asked)
                .as("SH1050: `include=status` returned nothing, so the section exists in the enum "
                        + "and nowhere else")
                .isNotNull();
        assertThat(asked.scheduledSoon())
                .as("SH1050: the pack and the console must mean the same week. Two definitions of "
                        + "`soon` is a tile and a pack disagreeing about what is due, which is the "
                        + "kind of thing nobody notices and nobody can explain.")
                .isEqualTo(2);
        assertThat(asked.postTypes()).isPositive();
    }

    @Test
    void theSummaryCountsWithoutLoadingTheCollections() {
        ShConsoleSummary summary = api.summary();

        // The claim the task is named for. `count()` rather than `findAll().size()` is invisible in
        // a response, so what is asserted is that the numbers are real and the answer is whole.
        assertThat(summary.sites())
                .as("SH1050: the site count came back empty, so this fixture's own site is missing "
                        + "and every other assertion here is about nothing")
                .isPositive();
        assertThat(summary.postTypes()).isPositive();
        assertThat(summary.version())
                .as("SH1050: the version is what the footer shows, and `dev` is the honest answer "
                        + "outside a packaged jar — blank or null is not")
                .isNotBlank();
        assertThat(summary.attention()).isNotNull();
        assertThat(summary.attention().scheduledSoon())
                .as("SH1050: the endpoint's window must be the same window the query was driven "
                        + "at, or the tile and the agenda disagree about what 'soon' means")
                .isEqualTo(2);
        assertThat(summary.attention().openNotes()).isNotNegative();
        assertThat(summary.activity())
                .as("SH1050: home reads its activity list out of this answer rather than making a "
                        + "second request, so a null here is a second request coming back")
                .isNotNull();
        assertThat(summary.runs())
                .as("SH1050: the runs list is absent, so home's 'what the agent did' section has "
                        + "nothing to draw and `shio status` prints no runs on an instance that "
                        + "has had them")
                .isNotNull();
        assertThat(summary.diagnostics())
                .as("SH1050: diagnostics is absent. Zero incidents and no diagnostics at all are "
                        + "different answers, and only one of them means the instance is healthy.")
                .isNotNull();
        assertThat(summary.diagnostics().incidents()).isNotNegative();
    }
}
