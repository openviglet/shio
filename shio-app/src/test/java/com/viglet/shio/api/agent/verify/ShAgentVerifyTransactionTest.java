/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentProperties;
import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH206 — the delivery fetches happen outside the report's read transaction.
 *
 * <h2>What this is the assertion for</h2>
 *
 * <p>SH141 ran its network pass inside {@code verify()}'s read-only transaction, holding a pooled
 * connection for its duration. That was accepted with the cost written down and bounded three ways
 * — a 50-request cap, a 15-second wall-clock budget, fail-fast per unreachable origin — which made
 * it <em>safe</em>, not right: on a slow front end a verification call occupied a connection for
 * fifteen seconds doing no database work at all.</p>
 *
 * <h2>Why the probe and not the front end</h2>
 *
 * <p>{@code ShAgentDeliveryTest} proves the delivery check against a real loopback server, which is
 * the right fixture for "does the report distinguish served from not-served". It is the wrong one
 * here, and in a way that would have made this test pass while proving nothing:
 * {@code TransactionSynchronizationManager} is <b>thread-local</b>, and an {@code HttpServer}
 * handler runs on the server's own thread, where no transaction is ever active. The question is
 * about the <em>calling</em> thread, so the observation has to happen there — which is what a probe
 * that records and returns does, and what a handler could not.</p>
 *
 * <p>This is also the test that would have caught the regression the fix is most likely to suffer:
 * putting {@code @Transactional} back on the class, or wrapping {@code verify()} in a transaction
 * one level up, both restore the defect while every other delivery assertion stays green.</p>
 *
 * @author Alexandre Oliveira
 */
// The shared datasource, deliberately (SH347's ceiling, and SH315's rule for when to leave it).
// Every read here is scoped to this class's own site — `verify(siteName, "delivery", …)` — and the
// assertions are about a probe this context alone installs, so nothing it does is a statement about
// the instance. `ShAgentDeliveryTest` is the same shape one question along and shares it too.
@SpringBootTest(properties = "shio.agent.render.allow-fetch=true")
@Import(ShAgentVerifyTransactionTest.ProbeConfig.class)
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentVerifyTransactionTest {

    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private RecordingProbe probe;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            siteName = "verify-tx-" + UUID.randomUUID().toString().substring(0, 8);
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(Instant.now());
            site.setOwner("admin");
            // A front-end URL it will never reach: the probe is replaced, so nothing is fetched.
            // What matters is only that the site HAS one, which is what puts it in `origins`.
            site.setUrl("http://localhost:1/");
            site.setFurl(siteName);
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.now());
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType postType = entityManager
                    .createQuery("select t from ShPostType t where t.name = 'Page'",
                            ShPostType.class)
                    .getResultStream().findFirst().orElse(null);

            ShPostUnified page = new ShPostUnified();
            page.setTitle("Proved page");
            page.setFurl("/proved");
            page.setState(ShPostState.PUBLISHED);
            page.setShSite(site);
            page.setShFolder(root);
            page.setShPostType(postType);
            page.setDate(Instant.now());
            page.setOwner("admin");
            page.setData("{}");
            entityManager.persist(page);
        });
    }

    @Test
    @DisplayName("no transaction is active while the delivery pass is fetching")
    void theFetchHappensOutsideTheReadTransaction() {
        probe.observed.clear();

        shAgentVerifyService.verify(siteName, "delivery", 50);

        assertThat(probe.observed)
                .as("""
                        the check has to actually fetch, or the assertion below is vacuous — a run \
                        that resolved no origin would record nothing and pass.""")
                .isNotEmpty();
        assertThat(probe.observed)
                .as("""
                        SH141 held a pooled connection for the whole network pass, bounded to 15 \
                        seconds and 50 requests — safe rather than right, since a slow front end \
                        occupied a connection doing no database work at all. The read pass now \
                        commits before the first request. Putting @Transactional back on the class \
                        restores the defect and leaves every other delivery assertion green, which \
                        is why this is asserted on the calling thread rather than inferred.""")
                .containsOnly(false);
    }

    @Test
    @DisplayName("and the report is still assembled, so the phase split did not lose the findings")
    void theReportSurvivesTheSplit() {
        probe.observed.clear();

        var report = shAgentVerifyService.verify(siteName, "delivery", 50);

        assertThat(report.notes())
                .as("""
                        the delivery coverage note is written by `report(...)`, which now runs after \
                        the transaction has closed and reads the site's name from a map gathered \
                        before it. If the phase split had dropped it, this run would be silently \
                        clean — the exact failure SH302 added coverage notes to prevent.""")
                .isNotNull()
                .anySatisfy(note -> assertThat(note).contains("proved").contains(siteName));
    }

    /** Replaces the real probe so the observation happens on the thread that calls it. */
    static class RecordingProbe extends ShAgentDeliveryProbe {

        private final List<Boolean> observed = new ArrayList<>();

        RecordingProbe(ShAgentSiteAccess siteAccess, ShAgentProperties properties) {
            super(siteAccess, properties);
        }

        @Override
        public Outcome status(URI target) {
            observed.add(TransactionSynchronizationManager.isActualTransactionActive());
            return new Outcome(200, null, null);
        }
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        @Primary
        RecordingProbe recordingProbe(ShAgentSiteAccess siteAccess, ShAgentProperties properties) {
            return new RecordingProbe(siteAccess, properties);
        }
    }
}
