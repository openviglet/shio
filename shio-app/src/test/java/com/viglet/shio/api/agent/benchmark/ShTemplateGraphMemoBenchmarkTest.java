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

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

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

import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
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
 * SH316 — what a second graph question costs inside one transaction, measured before it is fixed.
 *
 * <h2>The premise this was filed on, and why it needed a number</h2>
 *
 * <p>§XVI.50 reads: "every caller asks the graph to rebuild itself", and proposes SH311's move one
 * layer up — memoize the assembled {@code ShSiteTemplateGraph} for the length of the read-only
 * transaction. It was filed as an <em>idea</em> rather than a plan for exactly the right reason:
 * after SH311 the assembly may be cheap enough that memoizing it is optimisation for its own sake,
 * and this block has spent three tasks establishing that a cost is measured before it is fixed.</p>
 *
 * <p><b>The measurement named a different subject than the task did.</b> Assembling the graph a
 * second time inside one read-only transaction already cost <b>no queries</b> — SH311's cache holds
 * every row {@code of(site)} reads, so what was left was maps, sorts and one HTML parse per binding:
 * 1.13 ms, real but not the thing this repository measures costs in. What repeated in <em>queries</em>
 * was one layer sideways: {@link ShTemplateGraph#impactOf} counts pages with its own repository call
 * ({@code 2} per call), and {@code exists}, {@code isPublished}, {@code templatesOfTitle} and
 * {@code duplicateTitlesIn} each re-read <b>every post in the site</b> to filter it in memory, once
 * per binding per rule. Measured on this fixture before the fix:</p>
 *
 * <pre>
 *   graph.assembly.marginal-queries   0        (unchanged — it was never the cost)
 *   graph.assembly.marginal-nanos     1129445  ->    9065
 *   graph.impact.marginal-queries     2        ->       0
 *   verify.one-site.queries          95        ->      60
 * </pre>
 *
 * <p>So the fix SH316 named landed as the smaller half — the assembly is memoized, and that is a CPU
 * saving — while the queries came from memoizing the site's rows. Both use one mechanism,
 * {@link com.viglet.shio.render.ShReadOnlyTransactionCache}, which is SH311's, extracted rather than
 * copied.</p>
 *
 * <h2>Queries, and a printed nanosecond figure that nothing asserts</h2>
 *
 * <p>The ceilings are on statement counts, for SH264's reason: a timing assertion on a shared machine
 * is a flake, and SH250's lesson is that a flake is worse than a break. The wall time is printed
 * anyway, because "is the assembly itself expensive" is precisely the question §XVI.50 asked and a
 * query count of zero cannot answer it.</p>
 *
 * <p>Its own database, per SH315's lint: this fixture is a site with {@value #BINDINGS} bindings and
 * would land inside any instance-wide measurement standing next to it.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh316;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShTemplateGraphMemoBenchmarkTest {

    private static final String BUDGETS = "/token-budgets.properties";

    /** Bindings, which is what the graph's cost follows (SH314). */
    private static final int BINDINGS = 12;

    /** Pages, so the site-wide row reads have something to scan. */
    private static final int PAGES = 40;

    /** Repeats of one question, so a marginal cost is distinguishable from a fixed one. */
    private static final int REPEATS = 20;

    @Autowired
    private ShTemplateGraph templateGraph;
    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, Long> measured = new LinkedHashMap<>();
    private String siteName;
    private Properties budgets;

    @BeforeAll
    void setup() throws IOException {
        budgets = new Properties();
        try (InputStream in = getClass().getResourceAsStream(BUDGETS)) {
            budgets.load(in);
        }
        siteName = buildSite("sh316-" + UUID.randomUUID().toString().substring(0, 6));
    }

    @Test
    @DisplayName("SH316: a second graph assembly inside one transaction is free of queries")
    void theSecondAssemblyCostsNoQueries() {
        long[] costs = inReadOnlyTransaction(site -> {
            long first = queriesFor(() -> templateGraph.of(site));
            long repeated = queriesFor(() -> {
                for (int repeat = 0; repeat < REPEATS; repeat++) {
                    templateGraph.of(site);
                }
            });
            long nanos = System.nanoTime();
            for (int repeat = 0; repeat < REPEATS; repeat++) {
                templateGraph.of(site);
            }
            return new long[] { first, repeated, (System.nanoTime() - nanos) / REPEATS };
        });

        measured.put("graph.assembly.first-queries", costs[0]);
        measured.put("graph.assembly.marginal-queries", costs[1] / REPEATS);
        measured.put("graph.assembly.marginal-nanos", costs[2]);

        assertThat(costs[0])
                .as("""
                        The number §XVI.50 should have been about: a COLD assembly over %d \
                        bindings, which is SH311's cache reaching the graph — without it this is a \
                        layout, a region list and a theme read per binding. The memo below cannot \
                        keep this honest, since it answers before the assembly runs, so this is \
                        the assertion that would catch the cache being lost.""", BINDINGS)
                .isLessThanOrEqualTo(ceiling("graph.assembly.max-first-queries"));
        assertThat(costs[1])
                .as("""
                        And the memo itself: %d further assemblies inside the same read-only \
                        transaction, %d queries. This was already zero before SH316 — the \
                        assembly's cost was the %d ns beside it, not queries, which is why the \
                        task's own proposal turned out to be the smaller half of it.""",
                        REPEATS, costs[1], costs[2])
                .isZero();
    }

    @Test
    @DisplayName("SH316: the repeated cost is the site's rows, re-read per question")
    void theRepeatedCostIsTheSiteRows() {
        long marginal = inReadOnlyTransaction(site -> {
            ShPostUnified region = templateOfType(site, ShSystemPostType.REGION);
            templateGraph.impactOf(region);
            return queriesFor(() -> {
                for (int repeat = 0; repeat < REPEATS; repeat++) {
                    templateGraph.impactOf(region);
                }
            }) / REPEATS;
        });

        measured.put("graph.impact.marginal-queries", marginal);
        assertThat(marginal)
                .as("""
                        Two site-wide page counts per call before SH316 — uncached, each loading \
                        every row in the site to filter it in memory — so an /agent/changes feed \
                        carrying N template entries paid 2N for one unchanging answer. THIS is \
                        what §XVI.50 was reaching for when it said the graph rebuilds itself.""")
                .isLessThanOrEqualTo(ceiling("graph.impact.max-marginal-queries"));
    }

    @Test
    @DisplayName("SH316: a verify pass over one site, on a fixture shaped by bindings")
    void aVerifyPassIsBounded() {
        long queries = queriesFor(() -> verifyService.verify(siteName, "content", 500));
        measured.put("verify.one-site.queries", queries);

        assertThat(queries)
                .as("""
                        The end-to-end number the fix had to move: %d bindings, %d pages, one \
                        site. ShRenderSweepBenchmarkTest bounds the multi-SITE pass; this one is \
                        shaped by bindings, which is the dial the graph's cost follows (SH314) and \
                        the one that makes a per-binding site read visible.""", BINDINGS, PAGES)
                .isLessThanOrEqualTo(ceiling("verify.one-site.max-queries"));
    }

    @org.junit.jupiter.api.AfterAll
    void report() {
        StringBuilder out = new StringBuilder("\nSH316 — graph memoization, measured:\n");
        measured.forEach((name, value) -> out.append("  ").append(name).append(": ")
                .append(value).append('\n'));
        System.out.println(out);
    }

    // --- measuring ---

    /**
     * The production shape: one read-only transaction, several questions inside it.
     *
     * <p>Read-only is the condition SH311 put its cache behind, so measuring outside one would
     * measure a regime no caller is in.</p>
     */
    private <T> T inReadOnlyTransaction(java.util.function.Function<ShSite, T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> work.apply(entityManager
                .createQuery("select s from ShSite s where s.name = :n", ShSite.class)
                .setParameter("n", siteName)
                .getSingleResult()));
    }

    private ShPostUnified templateOfType(ShSite site, String typeName) {
        return entityManager.createQuery("""
                        select p from ShPostUnified p where p.shSite.id = :site
                          and p.shPostType.id = :type""", ShPostUnified.class)
                .setParameter("site", site.getId())
                .setParameter("type", postTypeId(typeName))
                .setMaxResults(1)
                .getSingleResult();
    }

    private String postTypeId(String typeName) {
        return entityManager
                .createQuery("select t.id from ShPostType t where t.name = :name", String.class)
                .setParameter("name", typeName)
                .getSingleResult();
    }

    private long queriesFor(Runnable work) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        work.run();
        return statistics.getPrepareStatementCount();
    }

    private long ceiling(String key) {
        String value = budgets.getProperty(key);
        assertThat(value).as("%s is missing from %s", key, BUDGETS).isNotNull();
        return Long.parseLong(value.trim());
    }

    // --- fixture ---

    private String buildSite(String name) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(name);
            site.setDate(when);
            site.setOwner("admin");
            StringBuilder layouts = new StringBuilder("{");
            List<String> types = new ArrayList<>();
            for (int binding = 0; binding < BINDINGS; binding++) {
                String type = "Sh316T" + binding + suffix();
                types.add(type);
                layouts.append(binding == 0 ? "" : ",")
                        .append('"').append(type).append("\":\"SH316_L").append(binding)
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
            ShPostType themeType = postType(ShSystemPostType.THEME, (byte) 1, when);
            post(site, root, themeType, "SH316_THEME", "/t", "{\"CSS\":\"body{}\"}", when);
            post(site, root, regionType, "SH316_NAV", "/r-nav",
                    "{\"HTML\":\"<nav>nav</nav>\"}", when);
            post(site, root, regionType, "SH316_FOOT", "/r-foot",
                    "{\"HTML\":\"<footer>foot</footer>\"}", when);
            for (int binding = 0; binding < BINDINGS; binding++) {
                post(site, root, layoutType, "SH316_L" + binding, "/l-" + binding,
                        "{\"HTML\":\"<html><body><div sh-region=\\\"SH316_NAV\\\"></div>"
                                + "<main>{{TEXT}}</main>"
                                + "<div sh-region=\\\"SH316_FOOT\\\"></div></body></html>\","
                                + "\"THEME\":\"SH316_THEME\"}", when);
            }
            for (int page = 0; page < PAGES; page++) {
                ShPostType pageType = postType(types.get(page % BINDINGS), (byte) 0, when);
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
