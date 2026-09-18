/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.api.staticfile.ShStaticFileUsage;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1033 — the same measurement as {@link ShAssetUsageBenchmarkTest}, on a real engine.
 *
 * <h2>Why H2 alone would not have answered this</h2>
 *
 * <p>§LXX.5 asks for the number <em>on each supported database</em>, and that turned out not to be
 * diligence for its own sake. Two things are true on a real engine and invisible on H2, and the
 * second is not a cost at all:</p>
 *
 * <ul>
 *   <li><b>"No index can serve a leading wildcard" is engine-specific.</b> It holds for a B-tree,
 *       which is all H2 and MariaDB offer over this column, and is false on PostgreSQL, which
 *       indexes substrings with {@code pg_trgm}. A decision about a reverse-reference table taken
 *       on H2's evidence alone would be taken without knowing that one of the three engines has a
 *       cheaper answer available.</li>
 *   <li><b>On PostgreSQL the query does not merely cost too much — it returns nothing.</b>
 *       {@code ShPostUnified.data} is {@code @Lob String}, which pgjdbc stores as a
 *       <em>large object</em>: the column holds an OID and the JSON lives in
 *       {@code pg_largeobject}. Hibernate reads it back transparently, so the application looks
 *       correct, and every SQL predicate over {@code data} matches zero rows. See
 *       {@link ShPostgresAssetUsageScanIT}, which holds that as an assertion.</li>
 * </ul>
 *
 * <h2>Five thousand rows, not fifty</h2>
 *
 * <p>The unit benchmark establishes the <em>magnitude</em> across three sizes on H2. What a real
 * engine adds is the <em>plan</em> — whether the optimiser reaches for an index at all — and a
 * planner declines an index over a five-thousand-row table for the same reason it declines one
 * over fifty thousand. Paying four times the container time to re-establish a slope H2 has
 * already measured would be buying the wrong fact.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
abstract class ShAssetUsageScanMeasurement {

    /** Enough that declining an index is a decision the planner makes, not a table it ignores. */
    protected static final int ROWS = 5_000;

    /** Referrers pointing at the measured asset. */
    protected static final int REFERRERS = 3;

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    private static final String TYPE = "Sh1033ScanArticle";

    @Autowired
    protected ShStaticFileService shStaticFileService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    protected EntityManager entityManager;

    protected String assetId;

    private String folderId;
    private String typeId;

    @BeforeAll
    void fixture() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("usage-scan-" + UUID.randomUUID());
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
            folderId = root.getId();

            ShPostType article = new ShPostType();
            article.setName(TYPE);
            article.setTitle("SH1033 Scan Article");
            article.setDescription("SH1033 cross-database fixture");
            article.setDate(EPOCH);
            entityManager.persist(article);
            typeId = article.getId();

            ShPostUnified asset = new ShPostUnified();
            asset.setShFolder(root);
            asset.setShPostType(shStaticFileService.resolveOrCreateFilePostType());
            asset.setState(ShPostState.PUBLISHED);
            asset.setTitle("hero.png");
            asset.setData("{\"FILE\":\"hero.png\",\"SIZE\":1234}");
            asset.setDate(EPOCH);
            asset.setOwner("admin");
            entityManager.persist(asset);
            entityManager.flush();
            assetId = asset.getId();
        });

        fill();
    }

    /**
     * Fills the table, the referrers first.
     *
     * <p>Ids are re-resolved per batch rather than entities carried across transactions: the
     * context is cleared every five hundred rows, so a held reference would be detached by the
     * next one.</p>
     */
    private void fill() {
        for (int batch = 0; batch < ROWS; batch += 500) {
            final int from = batch;
            final int to = Math.min(batch + 500, ROWS);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                ShFolder root = entityManager.getReference(ShFolder.class, folderId);
                ShPostType article = entityManager.getReference(ShPostType.class, typeId);
                for (int index = from; index < to; index++) {
                    // The first REFERRERS rows point at the asset; the rest carry other ids, so
                    // the scan compares against near-misses rather than against nothing.
                    String target = index < REFERRERS ? assetId : UUID.randomUUID().toString();
                    ShPostUnified post = new ShPostUnified();
                    post.setShFolder(root);
                    post.setShPostType(article);
                    post.setState(index % 2 == 0 ? ShPostState.PUBLISHED : ShPostState.DRAFT);
                    post.setTitle("row " + index);
                    post.setFurl("row-" + index);
                    post.setData("{\"HERO\":\"" + target + "\",\"BODY\":\"lorem ipsum dolor sit "
                            + "amet consectetur adipiscing elit sed do eiusmod tempor\"}");
                    post.setDate(EPOCH);
                    post.setOwner("admin");
                    entityManager.persist(post);
                }
                entityManager.flush();
                entityManager.clear();
            });
        }
    }

    // --- the measurement each engine answers ---

    /**
     * The one claim every engine has to answer, and the one that decides §LXX.6.
     *
     * <p>Stated as "the {@code data} predicate is applied as a <b>filter</b>, never as an index
     * condition" rather than as "the plan is a sequential scan", because the first draft said the
     * second and PostgreSQL falsified it for the wrong reason: it reaches for
     * {@code idx_post_unified_type} to satisfy {@code state}, then removes 2,501 rows with the
     * substring. That is an index scan by node name and a full read of the state partition by
     * behaviour — so the node name was measuring the wrong half of the predicate.</p>
     */
    @Test
    @DisplayName("SH1033: no index serves the usage predicate on this engine")
    void theUsagePredicateIsNotServedByAnyIndex() {
        String plan = explain(likeSql());
        System.out.printf("%n%s%n%s%n", header(), plan);

        assertThat(dataIsFilteredNotIndexed(plan))
                .as("""
                        SH1033 on %s: `data like '%%<id>%%'` has a leading wildcard, so there is no \
                        ordered structure the optimiser can use and every candidate row is read and \
                        discarded. If this fails, this engine found an index for the substring and \
                        §LXX.6's premise needs re-reading for it. The plan was:%n%s""",
                        engine(), plan)
                .isTrue();
    }

    /** What {@code listUsage} answers here — asserted per engine, because they disagree. */
    protected List<ShStaticFileUsage> usage() {
        return shStaticFileService.listUsage(assetId);
    }

    /** The one-state half of what {@code listUsage} runs twice, with the id inlined. */
    protected String likeSql() {
        return "select p.id from sh_post_unified p where p.id <> '" + assetId + "' "
                + "and p.state = 'PUBLISHED' and p.data like '%" + assetId + "%'";
    }

    protected String explain(String sql) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            List<?> rows = entityManager.createNativeQuery(explainPrefix() + sql).getResultList();
            StringBuilder out = new StringBuilder();
            for (Object row : rows) {
                if (row instanceof Object[] columns) {
                    for (Object column : columns) {
                        out.append(column).append(" | ");
                    }
                } else {
                    out.append(row);
                }
                out.append('\n');
            }
            return out.toString();
        });
    }

    protected String header() {
        return "SH1033 - usage predicate on " + engine() + ", " + ROWS + " rows:";
    }

    protected void execute(String sql) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery(sql).executeUpdate());
    }

    /** What this engine's EXPLAIN is spelled. */
    protected abstract String explainPrefix();

    /** Whether this engine's plan applies {@code data} as a filter rather than as a key. */
    protected abstract boolean dataIsFilteredNotIndexed(String plan);

    /** For the message, so a failure names the engine rather than the class. */
    protected abstract String engine();
}
