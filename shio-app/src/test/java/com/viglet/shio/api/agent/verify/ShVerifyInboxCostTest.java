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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import com.viglet.shio.ShH2ResultCache;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.schedule.ShScheduleProperties;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * SH1336 — what home's inbox would pay to list pages that fail a check, measured before it asks.
 *
 * <p>SH1318's design put "pages with a failed check" in the inbox, and {@code /agent/verify}
 * renders pages to answer its {@code page} and {@code a11y} checks, so calling it from the console's
 * front door on every visit was a cost nobody had taken. The checks that do not render are
 * {@code content} and {@code routes}; this times those over a site the size of the showcase
 * (150 pages) and counts the statements, so the decision between running them per visit and
 * reading the last verify run is made from a number.</p>
 *
 * <p>It shares the plain context (SH423, the cache is at its declared ceiling) and removes every
 * row it made. Milliseconds are printed, not asserted (SH250); the statement count is asserted
 * flat across sizes, because a statement per page is the shape that would rule the per-visit read
 * out whatever the clock says.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShVerifyInboxCostTest {

    private static final int[] SIZES = { 30, 150 };
    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private DataSource dataSource;
    /** SH1284 — the sweep prepares statements on its own thread, inside the window. */
    @Autowired
    private ShScheduleProperties schedule;
    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String siteName;
    private String rootId;
    private String typeId;
    private int pages;

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "sh1336-" + UUID.randomUUID();
            site.setName(siteName);
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();
            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            rootId = root.getId();
            ShPostType type = new ShPostType();
            type.setName("Sh1336Page-" + UUID.randomUUID());
            type.setTitle("SH1336 Page");
            type.setDescription("SH1336 inbox cost fixture");
            type.setDate(EPOCH);
            entityManager.persist(type);
            typeId = type.getId();
        });
    }

    private void growTo(int target) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = entityManager.find(ShSite.class, siteId);
            ShFolder root = entityManager.find(ShFolder.class, rootId);
            ShPostType type = entityManager.find(ShPostType.class, typeId);
            for (int index = pages; index < target; index++) {
                ShPostUnified post = new ShPostUnified();
                post.setState(ShPostState.DRAFT);
                post.setShSite(site);
                post.setShFolder(root);
                post.setShPostType(type);
                post.setTitle("Page " + index);
                post.setFurl("page-" + index);
                post.setLocale("en");
                post.setOwner("admin");
                post.setDate(EPOCH);
                post.setData("{\"title\":\"Page " + index + "\"}");
                entityManager.persist(post);
            }
        });
        pages = Math.max(pages, target);
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from ShPostUnified p where p.shSite.id = :id")
                    .setParameter("id", siteId).executeUpdate();
            entityManager.remove(entityManager.find(ShFolder.class, rootId));
            entityManager.remove(entityManager.find(ShPostType.class, typeId));
            entityManager.remove(entityManager.find(ShSite.class, siteId));
        });
    }

    @Test
    @DisplayName("SH1336: verify's non-rendering checks over a showcase-sized site, timed and counted")
    void theNonRenderingChecksAreMeasured() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasOn = statistics.isStatisticsEnabled();
        boolean sweeping = schedule.isEnabled();
        statistics.setStatisticsEnabled(true);
        schedule.setEnabled(false);
        List<String> report = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        try {
            for (int size : SIZES) {
                growTo(size);
                verifyService.verify(siteName, "content,routes", null); // warm the plan, not the rows
                long statements = Long.MAX_VALUE;
                long millis = Long.MAX_VALUE;
                int checked = 0;
                for (int attempt = 0; attempt < 3; attempt++) {
                    long before = statistics.getPrepareStatementCount();
                    ShAgentReport[] answer = new ShAgentReport[1];
                    millis = Math.min(millis, ShH2ResultCache.millis(dataSource,
                            () -> answer[0] = verifyService.verify(siteName, "content,routes", null)));
                    statements = Math.min(statements, statistics.getPrepareStatementCount() - before);
                    checked = answer[0].checked();
                }
                counts.add(statements);
                report.add(String.format("  %4d pages: %d checked, %d statement(s), %d ms",
                        size, checked, statements, millis));
            }
        } finally {
            schedule.setEnabled(sweeping);
            statistics.setStatisticsEnabled(wasOn);
        }
        System.out.println("\nSH1336 — /agent/verify?checks=content,routes over one site:\n"
                + String.join("\n", report));

        assertThat(counts)
                .as("""
                        SH1336: the inbox can only afford a per-visit verify if its statements do \
                        not grow with the site. Measured %s at %s pages.""", counts, List.of(SIZES[0], SIZES[1]))
                .containsOnly(counts.get(0));
    }
}
