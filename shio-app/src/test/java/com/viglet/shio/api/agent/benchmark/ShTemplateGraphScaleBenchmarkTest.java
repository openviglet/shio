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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
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
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.render.ShTemplateGraph;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * SH314 — "bounded by bindings, not pages", measured instead of argued.
 *
 * <h2>Why the claim needed a number</h2>
 *
 * <p>Three shipped designs rest on it. SH193 computes the presentation graph on demand instead of
 * storing it <em>because</em> the cost follows bindings; SH251 lints templates rather than pages for
 * the same reason; SH262 attaches an impact to a read on the same basis. It had never been measured
 * — and SH311, which was aimed at the digest sweep, made a {@code verify} pass 2.3× cheaper by
 * removing re-resolution, which is a strong hint that re-resolution rather than the walk was the
 * cost all along.</p>
 *
 * <h2>Two dials, and its own database</h2>
 *
 * <p>The fixture varies <b>bindings</b> and <b>pages</b> independently, because a fixture with one
 * dial can only answer "it grew". And it is a separate class with a database of its own for a reason
 * this repository has now met four times (SH239, SH272, SH289, and here): these sites are large and
 * instance-wide measurements next door would silently include them. The first version of this test
 * lived in {@link ShRenderSweepBenchmarkTest} and pushed its three-site {@code verify} number from
 * 59 to 159 — a benchmark reporting another benchmark's fixture, which passed or failed on method
 * order.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh314;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShTemplateGraphScaleBenchmarkTest {

    @Autowired
    private ShTemplateGraph templateGraph;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, Long> measured = new LinkedHashMap<>();

    @Test
    @DisplayName("the graph's cost tracks bindings, not pages")
    void theGraphScalesWithBindingsNotPages() {
        String manyPages = buildSite("sh314-pages-" + suffix(), 2, 60);
        String manyBindings = buildSite("sh314-bindings-" + suffix(), 20, 6);

        long pagesUncached = graphQueries(manyPages, false);
        long bindingsUncached = graphQueries(manyBindings, false);
        long bindingsCached = graphQueries(manyBindings, true);

        measured.put("graph.2-bindings-60-pages.queries", pagesUncached);
        measured.put("graph.20-bindings-6-pages.queries", bindingsUncached);
        measured.put("graph.20-bindings-6-pages.cached-queries", bindingsCached);

        assertThat(pagesUncached)
                .as("""
                        thirty times the pages and a tenth of the bindings costs less — so the \
                        graph is walked per *binding*, which is what SH193's compute-do-not-store, \
                        SH251's lint-the-template and SH262's impact-on-a-read all assume. A \
                        measurement now, rather than an argument three designs lean on.""")
                .isLessThan(bindingsUncached);
        assertThat(bindingsCached)
                .as("""
                        and inside a read-only transaction SH311's cache collapses the per-binding \
                        walk too, because every binding of one site resolves the same layout, \
                        region and theme lists. That is why a verify pass got 2.3× cheaper from a \
                        change aimed at the digest sweep — and it is what makes the *uncached* \
                        number above the honest measure of the claim.""")
                .isLessThan(bindingsUncached);
    }

    @AfterAll
    void report() {
        StringBuilder out = new StringBuilder("\nSH314 — graph scale, measured:\n");
        measured.forEach((name, value) -> out.append("  ").append(name).append(": ")
                .append(value).append('\n'));
        System.out.println(out);
    }

    // --- measuring ---

    /**
     * Queries to build one site's graph, with or without SH311's read-only cache.
     *
     * <p>Both regimes on purpose: the cached one is what production pays, and the uncached one is
     * what the <em>claim</em> is about — whether the walk itself is per binding. Measuring only the
     * cached number would answer "the cache works", which is SH311's question and not this one.</p>
     */
    private long graphQueries(String siteName, boolean readOnly) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(readOnly);
        return queriesFor(() -> tx.executeWithoutResult(status -> {
            ShSite site = entityManager
                    .createQuery("select s from ShSite s where s.name = :n", ShSite.class)
                    .setParameter("n", siteName)
                    .getSingleResult();
            templateGraph.of(site);
        }));
    }

    private long queriesFor(Runnable work) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        work.run();
        return statistics.getPrepareStatementCount();
    }

    // --- fixture ---

    /**
     * A site with {@code bindings} bound layouts and {@code pages} pages spread across them.
     *
     * <p>Two dials, because SH314's question is which of them the cost follows.</p>
     */
    private String buildSite(String name, int bindings, int pages) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(name);
            site.setDate(when);
            site.setOwner("admin");
            StringBuilder layouts = new StringBuilder("{");
            List<String> types = new ArrayList<>();
            for (int binding = 0; binding < bindings; binding++) {
                String type = "Sh314T" + binding + suffix();
                types.add(type);
                layouts.append(binding == 0 ? "" : ",")
                        .append('"').append(type).append("\":\"SH314_L").append(binding)
                        .append('"');
            }
            site.setPostTypeLayout(layouts.append('}').toString());
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);
            post(site, root, regionType, "SH314_NAV", "/r-nav",
                    "{\"HTML\":\"<nav>nav</nav>\"}", when);
            for (int binding = 0; binding < bindings; binding++) {
                post(site, root, layoutType, "SH314_L" + binding, "/l-" + binding,
                        "{\"HTML\":\"<html><body><div sh-region=\\\"SH314_NAV\\\"></div>"
                                + "<main>{{TEXT}}</main></body></html>\"}", when);
            }
            for (int page = 0; page < pages; page++) {
                ShPostType pageType = postType(types.get(page % bindings), (byte) 0, when);
                post(site, root, pageType, "Page " + page, "/p-" + page,
                        "{\"TEXT\":\"<p>body</p>\"}", when);
            }
        });
        return name;
    }

    private ShPostType postType(String name, byte system, Instant when) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.DRAFT);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setData(data);
        post.setDate(when);
        post.setOwner("admin");
        entityManager.persist(post);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
