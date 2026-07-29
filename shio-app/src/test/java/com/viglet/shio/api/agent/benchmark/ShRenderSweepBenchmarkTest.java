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

import com.viglet.shio.api.agent.verify.ShAgentRenderService;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * SH264 — what a whole-site structural proof costs, measured.
 *
 * <h2>Why this exists, and why it counts queries</h2>
 *
 * <p>Since SH249 a digest composes the page for real, so any per-page proof pays a render each: a
 * layout compile, its regions, and whatever the helpers read. Three tasks were filed against the
 * same unmeasured number — SH264 (the sweep), SH307 ({@code usedElsewhere} building every site's
 * graph per finding) and SH310 (message length on a many-template site) — and Block M's discipline
 * is that a cost is measured rather than asserted.</p>
 *
 * <p><b>Queries, not wall-clock.</b> A timing assertion on a shared CI machine is a flake, and
 * SH250's lesson is that a flake is worse than a break because it teaches people that red might
 * mean nothing. Hibernate's statement count is deterministic, is the cost that actually scales
 * here, and answers the question the tasks ask in the form they ask it: <em>does this grow with
 * pages, or with bindings?</em></p>
 *
 * <p><b>The marginal page is what is asserted</b>, not the total. A first render pays for the
 * layout, the regions and the theme; the tenth pays for its own page and whatever the render could
 * not reuse. A ceiling on the total would move whenever the fixture grew, which is a gate that
 * fails for the wrong reason.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sweep;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderSweepBenchmarkTest {

    private static final String BUDGETS = "/token-budgets.properties";

    /** Pages per site — enough that a per-page cost is distinguishable from a fixed one. */
    private static final int PAGES = 10;

    /** Sites, so `usedElsewhere` (SH307) has somewhere else to look. */
    private static final int SITES = 3;

    @Autowired
    private ShAgentRenderService renderService;
    @Autowired
    private ShAgentVerifyService verifyService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<String, Long> measured = new LinkedHashMap<>();
    private final List<String> siteNames = new ArrayList<>();
    private Properties budgets;

    @BeforeAll
    void setup() throws IOException {
        budgets = new Properties();
        try (InputStream in = getClass().getResourceAsStream(BUDGETS)) {
            budgets.load(in);
        }
        for (int site = 0; site < SITES; site++) {
            siteNames.add(buildSite("sh264-" + site + "-"
                    + UUID.randomUUID().toString().substring(0, 6)));
        }
    }

    @Test
    @DisplayName("a rendered digest's marginal cost per page is bounded")
    void theMarginalPageIsBounded() {
        String site = siteNames.getFirst();
        // Warm: the first render of a site pays for its layout, regions and theme, and Hibernate's
        // own second-level structures. What SH264 asks about is the *next* page.
        renderService.digest("post:" + site + "/page-0", null);

        long one = queriesFor(() -> renderService.digest("post:" + site + "/page-1", null));
        long many = queriesFor(() -> {
            for (int page = 2; page < PAGES; page++) {
                renderService.digest("post:" + site + "/page-" + page, null);
            }
        });

        long marginal = many / (PAGES - 2);
        measured.put("render.digest.first-page-queries", one);
        measured.put("render.digest.marginal-page-queries", marginal);
        assertThat(marginal)
                .as("a sweep is N renders and this is the N — the regression guard on it")
                .isLessThanOrEqualTo(ceiling("render.digest.max-marginal-page-queries"));

        // THE FINDING, asserted rather than remembered (SH274's shape). Measured: the marginal
        // page costs the same as the first, so nothing about the presentation chain is reused
        // between pages — every page re-resolves its layout, both regions and the theme. That is
        // SH264's answer, and it is what justifies SH311 with a number rather than a guess. If a
        // cache ever lands, this assertion fails and sends its author to reopen the question here.
        assertThat(marginal)
                .as("no reuse across pages today: %d queries for the first page and %d for the "
                        + "next, so a whole-site proof pays for the layout, its regions and the "
                        + "theme once per page rather than once per binding. A cache would show up "
                        + "here first, and this is what makes it impossible to add one and leave "
                        + "the story stale", one, marginal)
                .isGreaterThanOrEqualTo(one - 2);
    }

    @Test
    @DisplayName("a verify pass over several sites is bounded, and reports its own size")
    void verifyOverSeveralSitesIsBounded() {
        // SH307's subject: `usedElsewhere` builds every in-scope site's graph, once per unused
        // template. Three sites each holding an unused region is the smallest fixture where that
        // is visible at all.
        long queries = queriesFor(() -> verifyService.verify(null, "content", 500));
        ShAgentReport report = verifyService.verify(null, "content", 500);

        int messageChars = report.findings().stream()
                .mapToInt(finding -> (finding.message() == null ? 0 : finding.message().length())
                        + (finding.fix() == null ? 0 : finding.fix().length()))
                .sum();
        measured.put("verify.multi-site.queries", queries);
        measured.put("verify.multi-site.findings", (long) report.findings().size());
        measured.put("verify.multi-site.message-chars", (long) messageChars);

        assertThat(queries)
                .as("""
                        SH307 — the whole pass, over %d sites. A ceiling on the total rather than \
                        per finding, because what the task suspects is a multiplication: every \
                        site's graph rebuilt for every unused template.""", SITES)
                .isLessThanOrEqualTo(ceiling("verify.multi-site.max-queries"));
        assertThat(messageChars)
                .as("""
                        SH310 — the prose, which SH298 and SH308 have just made longer on a rule \
                        that fires once per template. Characters rather than tokens because this \
                        is a report's own text, and the response budget beside it already \
                        measures what a caller pays.""")
                .isLessThanOrEqualTo((int) ceiling("verify.multi-site.max-message-chars"));
    }

    @org.junit.jupiter.api.AfterAll
    void report() {
        // Printed, always: SH264's deliverable is the number, and a benchmark that only speaks when
        // it fails leaves the next person to re-derive what "normal" is.
        StringBuilder out = new StringBuilder("\nSH264 — render sweep, measured:\n");
        measured.forEach((name, value) -> out.append("  ").append(name).append(": ")
                .append(value).append('\n'));
        System.out.println(out);
    }

    // --- measuring ---

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

    /** A site with one bound layout, two regions, a theme, an unused region, and PAGES pages. */
    private String buildSite(String name) {
        String type = "Sh264Page" + UUID.randomUUID().toString().substring(0, 6);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(name);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + type + "\":\"SH264_LAYOUT\"}");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);
            ShPostType themeType = postType(ShSystemPostType.THEME, (byte) 1, when);

            post(site, root, layoutType, "SH264_LAYOUT", "/layout",
                    "{\"HTML\":\"<html><head><style>{{theme.css}}</style></head><body>"
                            + "<h1>{{TITLE}}</h1>"
                            + "<div sh-region=\\\"SH264_NAV\\\"></div>"
                            + "<div sh-region=\\\"SH264_FOOT\\\"></div>"
                            + "<main>{{TEXT}}</main></body></html>\","
                            + "\"THEME\":\"SH264_THEME\"}", when);
            post(site, root, regionType, "SH264_NAV", "/r-nav",
                    "{\"HTML\":\"<nav><a href=\\\"/page-0\\\">Home</a></nav>\"}", when);
            post(site, root, regionType, "SH264_FOOT", "/r-foot",
                    "{\"HTML\":\"<footer>foot</footer>\"}", when);
            // Unused on purpose: SH298's rule fires once per site, which is what SH307 measures.
            post(site, root, regionType, "SH264_STRAY", "/r-stray",
                    "{\"HTML\":\"<aside>stray</aside>\"}", when);
            post(site, root, themeType, "SH264_THEME", "/t",
                    "{\"CSS\":\"body{color:#111}\"}", when);

            for (int page = 0; page < PAGES; page++) {
                post(site, root, pageType, "Page " + page, "/page-" + page,
                        "{\"TITLE\":\"Page " + page + "\",\"TEXT\":\"<p>body " + page + "</p>\"}",
                        when);
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
}
