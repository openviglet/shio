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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import com.viglet.shio.api.cda.ShCdaService;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * SH1129 — what a GraphQL {@code posts} list costs to say which languages each post is in.
 *
 * <p>SH1057's design asked for this number before the field: {@code availableLocales} is answered
 * per post by the single reads, one query for one post, and the same shape on a list is one query
 * per row. The pair below is that choice, measured: the page read as it ships (one query for the
 * page's translation groups) and the same rows read the per-post way, so the ratio stays runnable
 * rather than being a number that existed for one commit.</p>
 *
 * <p>Statements, from Hibernate's cumulative statistics, which is why this has a database of its own
 * (SH315): a neighbour's reads would otherwise be counted as this page's. Milliseconds are printed and
 * never asserted (SH250).</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-cda-available-locales;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaAvailableLocalesBenchmarkTest {

    /** Pages, each post with one translation, so every row has a group of two to report. */
    private static final int PAGES = 40;
    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("available-locales-" + UUID.randomUUID());
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

            ShPostType type = new ShPostType();
            type.setName("AvailableLocalesArticle");
            type.setTitle("Available Locales Article");
            type.setDate(EPOCH);
            entityManager.persist(type);

            for (int index = 0; index < PAGES; index++) {
                String group = UUID.randomUUID().toString();
                persist(site, root, type, "/page-" + index, "en", group);
                persist(site, root, type, "/pagina-" + index, "pt-br", group);
            }
        });
    }

    @Test
    @DisplayName("SH1129: a page of posts reads its translations in one query, however long the page")
    void theListCostsTheSameAtAnySize() {
        Cost five = costOf(() -> shCdaService.queryPosts(siteId, null, null, 0, 5, null, "en"));
        Cost twentyFive = costOf(() -> shCdaService.queryPosts(siteId, null, null, 0, 25, null, "en"));
        Cost perRow = costOf(() -> {
            List<ShCdaPost> rows = new ArrayList<>();
            for (ShCdaPost post : shCdaService.queryPosts(siteId, null, null, 0, 25, null, "en")) {
                rows.add(shCdaService.getPost(post.id()).orElseThrow());
            }
            return rows;
        });

        System.out.printf("""
                SH1129 availableLocales on a list — %d groups of two
                  page of  5, batched  : %d statement(s), %d ms
                  page of 25, batched  : %d statement(s), %d ms
                  page of 25, per row  : %d statement(s), %d ms%n""",
                PAGES, five.statements, five.millis, twentyFive.statements, twentyFive.millis,
                perRow.statements, perRow.millis);

        assertThat(twentyFive.statements)
                .as("SH1129: the page's translation groups are one query, so five rows and twenty-"
                        + "five cost the same number of statements")
                .isEqualTo(five.statements);
        assertThat(perRow.statements)
                .as("SH1129: and the per-row shape the single reads use grows with the page, which "
                        + "is the N+1 the batch exists to avoid")
                .isGreaterThanOrEqualTo(twentyFive.statements + 25);
    }

    @Test
    @DisplayName("SH1129: every row carries both languages of its group")
    void everyRowSaysWhereItsTranslationIs() {
        List<ShCdaPost> page = shCdaService.queryPosts(siteId, null, null, 0, 10, null, "en");

        assertThat(page).hasSize(10).allSatisfy(post -> assertThat(post.availableLocales())
                .as("SH1129: a list row with no locales is a switcher with nothing in it")
                .containsExactly("en", "pt-br"));
    }

    private record Cost(long statements, long millis) {
    }

    private Cost costOf(Supplier<List<ShCdaPost>> read) {
        // Each service call opens its own transaction and so its own persistence context, which is
        // what keeps the first call's loaded rows from making the second one look cheaper.
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        // SH1327 — the batched and per-row reads repeat the same queries, which H2 would recall.
        long before = statistics.getPrepareStatementCount();
        long millis = ShH2ResultCache.millis(dataSource, read::get);
        return new Cost(statistics.getPrepareStatementCount() - before, millis);
    }

    private void persist(ShSite site, ShFolder folder, ShPostType type, String furl, String locale,
                         String group) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.PUBLISHED);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(furl);
        post.setFurl(furl);
        post.setLocale(locale);
        post.setTranslationGroupId(group);
        post.setPublished(true);
        post.setData("{}");
        post.setDate(EPOCH);
        post.setOwner("admin");
        entityManager.persist(post);
    }
}
