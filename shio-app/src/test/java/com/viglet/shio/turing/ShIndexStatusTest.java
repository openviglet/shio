/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.turing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1063 — whether a published page is findable, on the surface an agent already reads.
 *
 * <p>{@code searchIndexing} is a live manifest feature whose only surfaces were a properties
 * class, a listener and an incident log entry: on an instance with indexing on, nobody could
 * answer the question search exists for. The two answers that cost nothing to compute are here —
 * where this page's content would go, and whether the site routes anywhere at all.</p>
 *
 * <p>Indexing is turned <b>on</b> for this class and pointed at a URL nothing listens on, which is
 * the state the read describes rather than one it acts on: nothing here sends a document. It costs
 * no Spring context — the properties bean is mutated and put back, the shape
 * {@code ShAgentSearchReindexTest} established for the same reason (SH315 permits sharing for a
 * class that reads only its own fixtures).</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShIndexStatusTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private ShTuringProperties turingProperties;
    @Autowired
    private ShTuringIndexStatusService statusService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    /** What the shared context had, so no other class sees this one's state. */
    private boolean wasEnabled;
    private String wasUrl;
    private java.util.Map<String, String> wasSites;

    @org.junit.jupiter.api.AfterAll
    void restore() {
        turingProperties.setEnabled(wasEnabled);
        turingProperties.setUrl(wasUrl);
        turingProperties.setSites(wasSites);
    }

    @BeforeAll
    void seed() {
        wasEnabled = turingProperties.isEnabled();
        wasUrl = turingProperties.getUrl();
        wasSites = turingProperties.getSites();
        turingProperties.setEnabled(true);
        // Syntactically reachable, dead in fact — and nothing here sends anything anyway.
        turingProperties.setUrl("http://127.0.0.1:1");
        turingProperties.setSites(new java.util.LinkedHashMap<>(
                java.util.Map.of("sh1063-routed", "Routed")));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType type = new ShPostType();
            type.setName("Sh1063Page" + UUID.randomUUID().toString().substring(0, 6));
            type.setTitle("SH1063 page");
            type.setSystem((byte) 0);
            type.setDate(EPOCH);
            type.setOwner("admin");
            entityManager.persist(type);

            // The site the properties above route: its pages go to the "Routed" search site.
            site("sh1063-routed", type);
            // And one nothing routes, which is the failure that raises nothing.
            site("sh1063-adrift", type);
        });
    }

    @Test
    @DisplayName("SH1063: a read says where this page is indexed, and only when asked")
    void theReadSaysWhereThePageGoes() {
        ShAgentDoc quiet = read("post:sh1063-routed/home", false);
        ShAgentDoc asked = read("post:sh1063-routed/home", true);

        assertThat(quiet.index())
                .as("SH1063: a rider nobody asked for is bytes on every row of every read (P3)")
                .isNull();
        assertThat(asked.index()).isNotNull();
        assertThat(asked.index().enabled())
                .as("SH1063: 'indexing is off' and 'this page routes nowhere' are different "
                        + "answers, and only the second is a defect")
                .isTrue();
        assertThat(asked.index().searchSite()).isEqualTo("Routed");
        assertThat(asked.index().indexed())
                .as("SH1063: published, routed, and indexing on — this page is one that gets sent")
                .isTrue();
    }

    @Test
    @DisplayName("SH1063: a site that routes nowhere says so, on the page and in the report")
    void aSiteRoutingNowhereIsVisible() {
        ShAgentDoc adrift = read("post:sh1063-adrift/home", true);

        assertThat(adrift.index().searchSite())
                .as("SH1063: no search site is the whole failure — the push is never made and "
                        + "nothing is recorded")
                .isNull();
        assertThat(adrift.index().indexed()).isFalse();

        List<Finding> findings = shAgentVerifyService.verify("sh1063-adrift", "routes", 200)
                .findings().stream()
                .filter(finding -> "index-unroutable".equals(finding.rule()))
                .toList();

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().severity())
                .as("SH1063: the content is right and the configuration is not")
                .isEqualTo("warning");
        assertThat(findings.getFirst().address()).isEqualTo("site:sh1063-adrift");
        assertThat(findings.getFirst().fix()).contains("shio.turing.sites");
    }

    @Test
    @DisplayName("SH1063: a routed site raises no finding, so the rule is about the gap")
    void aRoutedSiteIsSilent() {
        assertThat(shAgentVerifyService.verify("sh1063-routed", "routes", 200).findings())
                .noneMatch(finding -> "index-unroutable".equals(finding.rule()));
    }

    @Test
    @DisplayName("SH1063: the routing table is one read, and it names the site that goes nowhere")
    void theRoutingTableIsOneRead() {
        ShTuringIndexStatus status = statusService.status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.target()).isEqualTo("http://127.0.0.1:1");
        assertThat(status.sites()).extracting(ShTuringIndexStatus.Route::site)
                .as("SH1063: every site, in name order, because the question is which of them "
                        + "routes nowhere and a partial table cannot answer it")
                .contains("sh1063-adrift", "sh1063-routed");
        assertThat(status.sites()).filteredOn(route -> "sh1063-routed".equals(route.site()))
                .singleElement()
                .satisfies(route -> {
                    assertThat(route.searchSite()).isEqualTo("Routed");
                    assertThat(route.published()).isEqualTo(1);
                });
        assertThat(status.sites()).filteredOn(route -> "sh1063-adrift".equals(route.site()))
                .singleElement()
                .satisfies(route -> assertThat(route.searchSite()).isNull());
    }

    @Test
    @DisplayName("SH1063: the terse table says UNROUTABLE where content is published into nothing")
    void theTerseTableNamesTheGap() {
        String text = ShTuringIndexTerse.status(statusService.status());

        assertThat(text).startsWith("indexing on -> http://127.0.0.1:1");
        assertThat(text)
                .as("SH1063: the line an operator greps for — published pages, no route out")
                .contains("sh1063-adrift -> (nowhere)  1 published  UNROUTABLE");
        assertThat(text).contains("sh1063-routed -> Routed");
        assertThat(text).doesNotContain("sh1063-routed -> Routed  1 published  UNROUTABLE");
    }

    private ShAgentDoc read(String address, boolean index) {
        return shAgentReadService.read(List.of(address), List.of(), false, false, null, false,
                false, false, false, false, false, index).getFirst();
    }

    private void site(String name, ShPostType type) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(EPOCH);
        site.setOwner("admin");
        entityManager.persist(site);

        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(EPOCH);
        root.setOwner("admin");
        entityManager.persist(root);

        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.PUBLISHED);
        post.setShSite(site);
        post.setShFolder(root);
        post.setShPostType(type);
        post.setTitle("Home");
        post.setFurl("home");
        post.setData("{}");
        post.setLocale("en");
        post.setPublished(true);
        post.setDate(EPOCH);
        post.setOwner("admin");
        entityManager.persist(post);
    }
}
