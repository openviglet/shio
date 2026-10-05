/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.object;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

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

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * SH1236 — a listing of sites, folders or post types costs the same statements at any size.
 *
 * <p>{@code ShObject.shWorkflowTasks} was {@code @Fetch(JOIN)}, which makes a collection eager, and
 * a JPQL query cannot join it implicitly — so every query over an {@code ShObject} subclass paid one
 * more select per row to fill a collection nothing reads. SH1217 measured it on the sites listing
 * (16 statements for 15 sites, 401 for 400). This measures the two reads the console makes most
 * after that one, a folder's children and the post-type catalogue, at two sizes each, and asserts
 * the count does not grow with the rows. Counts are asserted; milliseconds would be SH250's flake.</p>
 *
 * <p>Shares the plain context, as {@code ShSiteListingCostTest} does: statistics are switched on
 * for the measurement only, and every row it seeded is removed after it.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShWorkflowTaskFetchCostTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private ShFolderRepository folders;
    @Autowired
    private ShPostTypeRepository postTypes;
    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String rootId;
    private final List<String> madeFolders = new ArrayList<>();
    private final List<String> madeTypes = new ArrayList<>();

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("sh1236-" + UUID.randomUUID());
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
            siteId = site.getId();
            rootId = root.getId();
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String id : madeFolders) {
                entityManager.remove(entityManager.find(ShFolder.class, id));
            }
            for (String id : madeTypes) {
                entityManager.remove(entityManager.find(ShPostType.class, id));
            }
            entityManager.remove(entityManager.find(ShFolder.class, rootId));
            entityManager.remove(entityManager.find(ShSite.class, siteId));
        });
    }

    private void growFolders(int target) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShFolder root = entityManager.find(ShFolder.class, rootId);
            for (int index = madeFolders.size(); index < target; index++) {
                ShFolder child = new ShFolder();
                child.setName("child-" + index);
                child.setParentFolder(root);
                child.setDate(EPOCH);
                child.setOwner("admin");
                entityManager.persist(child);
                madeFolders.add(child.getId());
            }
        });
    }

    private void growTypes(int target) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int index = madeTypes.size(); index < target; index++) {
                ShPostType type = new ShPostType();
                type.setName("Sh1236Type" + index + "-" + UUID.randomUUID().toString().substring(0, 8));
                type.setTitle("SH1236 " + index);
                type.setDescription("SH1236 fetch-cost fixture");
                type.setDate(EPOCH);
                entityManager.persist(type);
                madeTypes.add(type.getId());
            }
        });
    }

    /** Statements one read prepares, in a fresh transaction so nothing is served from the session. */
    private long statements(Supplier<List<?>> read) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasOn = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                long before = statistics.getPrepareStatementCount();
                assertThat(read.get()).isNotEmpty();
                return statistics.getPrepareStatementCount() - before;
            });
        } finally {
            statistics.setStatisticsEnabled(wasOn);
        }
    }

    @Test
    @DisplayName("SH1236: a folder's children cost the same statements at 15 folders and at 200")
    void folderChildrenAreBounded() {
        growFolders(15);
        long few = statements(() -> folders.findByParentFolderOrderByPositionAsc(
                entityManager.getReference(ShFolder.class, rootId)));
        growFolders(200);
        long many = statements(() -> folders.findByParentFolderOrderByPositionAsc(
                entityManager.getReference(ShFolder.class, rootId)));
        System.out.printf("%nSH1236 — a folder's children: 15 → %d statement(s), 200 → %d%n", few, many);

        assertThat(many)
                .as("SH1236: a statement per folder is the eager workflow-task collection, filled one "
                        + "select at a time for a field nothing reads. Measured %d at 15, %d at 200.", few, many)
                .isEqualTo(few);
    }

    @Test
    @DisplayName("SH1236: the post-type catalogue costs the same statements with 5 more types and 60 more")
    void postTypeCatalogueIsBounded() {
        growTypes(5);
        long few = statements(postTypes::findAll);
        growTypes(60);
        long many = statements(postTypes::findAll);
        System.out.printf("%nSH1236 — the post-type catalogue: +5 → %d statement(s), +60 → %d%n", few, many);

        assertThat(many)
                .as("SH1236: the catalogue grows a statement per type for the same reason. Measured %d "
                        + "and %d.", few, many)
                .isEqualTo(few);
    }
}
