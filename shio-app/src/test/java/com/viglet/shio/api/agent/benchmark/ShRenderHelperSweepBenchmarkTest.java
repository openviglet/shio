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

import com.viglet.shio.api.agent.verify.ShAgentRenderService;
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
 * SH312 — what a sweep costs when the pages are not the simplest page there is.
 *
 * <h2>Why the existing number is a floor and not an answer</h2>
 *
 * <p>{@link ShRenderSweepBenchmarkTest}'s fixture is one layout, two regions, one theme, ten pages
 * and <b>no helpers</b>. It answered the question SH264 asked — is anything reused between pages —
 * and SH311 has since taken a swept page from 19 queries to 6. What it cannot answer is <em>what a
 * real sweep costs</em>, because the helpers are the part that varies without bound and none of them
 * is in that fixture.</p>
 *
 * <p>The reason they are a different question is <b>where the input comes from</b>. SH311's cache
 * holds a resolution — this site's Regions, this site's PageLayouts — which is per <em>type</em> and
 * therefore shared by every page of it. A helper reads content chosen by the page being rendered:
 * {@code {{#query}}} reads the page's own folder, {@code {{#getRelation}}} reads whatever that
 * page's field names. No resolution cache can touch those, by construction, so the 6 is a floor for
 * a page with nothing on it.</p>
 *
 * <h2>The four shapes, and why the two {@code getRelation} rows are separate</h2>
 *
 * <p>{@code plain} reproduces SH264's page here, so the other three are read against a baseline
 * measured on the same fixture rather than against a number from another class's database. Measured,
 * per page, in a sweep:</p>
 *
 * <pre>
 *   plain                        5
 *   listing  {{#query limit=10}} 7   +2 a page
 *   sections by id  (5 entries) 10   +5 — one lookup per entry, exactly as filed
 *   sections by url (5 entries) 15  +10 — TWO per entry
 * </pre>
 *
 * <p>Both claims in SH312 hold and the per-entry cost is what it predicted. The fourth row is the
 * one nobody had predicted, and it opened SH353.</p>
 *
 * <p>{@code getRelation} is measured <b>twice</b> because SH247's resolution order — by id, then by
 * friendly URL within the site (SH181's rule) — makes the two forms cost different things, and both
 * are shapes this product produces: a {@code Content Select} stores an id, an SH95 {@code Multi
 * Select} of sections stores friendly URLs. A URL entry pays the id lookup that misses <em>and</em>
 * a site-wide read, per entry. That the difference exists is the finding; whether it should be
 * closed is not this task's to decide, and the numbers below are what that decision would need.</p>
 *
 * <h2>Its own database</h2>
 *
 * <p>SH315's lint, and SH314's reason for it: these pages would land inside
 * {@code ShRenderSweepBenchmarkTest}'s instance-wide {@code verify(null, …)} number, which is
 * exactly how that class's three-site figure once went from 59 to 159.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh312;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderHelperSweepBenchmarkTest {

    private static final String BUDGETS = "/token-budgets.properties";

    /** Pages of each shape — a sweep, which is what {@code ?digest=true} does. */
    private static final int PAGES = 10;

    /** Rows a listing region asks for, and enough siblings in the folder to fill it. */
    private static final int LIMIT = 10;

    /** Sections a composed page names — SH95's shape, and small enough to be ordinary. */
    private static final int SECTIONS = 5;

    @Autowired
    private ShAgentRenderService renderService;
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
        siteName = buildSite("sh312-" + UUID.randomUUID().toString().substring(0, 6));
    }

    @Test
    @DisplayName("SH312: a page with a helper on it costs more than the floor, per page")
    void helperPagesCostMoreThanTheFloor() {
        long plain = sweep("plain");
        long listing = sweep("listing");
        long byId = sweep("sections-id");
        long byUrl = sweep("sections-url");

        measured.put("render.sweep.plain-page-queries", plain);
        measured.put("render.sweep.listing-page-queries", listing);
        measured.put("render.sweep.section-by-id-page-queries", byId);
        measured.put("render.sweep.section-by-url-page-queries", byUrl);

        assertThat(plain)
                .as("""
                        the floor, reproduced on this fixture so the three below are compared \
                        against a baseline from the same database (SH264's number is 6 on its \
                        own)""")
                .isLessThanOrEqualTo(ceiling("render.sweep.max-per-page-queries"));

        assertThat(listing)
                .as("""
                        SH312's first claim, measured: a listing region reads the page's OWN \
                        folder, so the read is per page and SH311's per-type cache cannot reach \
                        it. %d queries a page against a floor of %d.""", listing, plain)
                .isGreaterThan(plain)
                .isLessThanOrEqualTo(ceiling("render.sweep.max-listing-page-queries"));

        assertThat(byId)
                .as("""
                        SH312's second claim: %d sections is %d queries a page over the floor — \
                        one lookup per entry, because a relation is resolved from the page's own \
                        field values.""", SECTIONS, byId - plain)
                .isGreaterThan(plain)
                .isLessThanOrEqualTo(ceiling("render.sweep.max-section-page-queries"));
    }

    @Test
    @DisplayName("SH312: the same relation costs more by URL than by id")
    void aUrlRelationCostsMoreThanAnIdRelation() {
        long byId = sweep("sections-id");
        long byUrl = sweep("sections-url");

        assertThat(byUrl)
                .as("""
                        SH247 resolves a reference by id and THEN by friendly URL (SH181's order), \
                        so a URL entry pays the id lookup that misses and a site-wide read after \
                        it — %d queries a page against %d for the same %d sections stored as ids. \
                        Both shapes are real: a Content Select stores an id, an SH95 Multi Select \
                        of sections stores URLs. Recorded rather than fixed — a cache here would \
                        be over content, which is a freshness decision and not a resolution one.""",
                        byUrl, byId, SECTIONS)
                .isGreaterThan(byId);
    }

    @AfterAll
    void report() {
        StringBuilder out = new StringBuilder("\nSH312 — helper sweep, measured (queries per page):\n");
        measured.forEach((name, value) -> out.append("  ").append(name).append(": ")
                .append(value).append('\n'));
        System.out.println(out);
    }

    // --- measuring ---

    /**
     * Queries per page for one shape, swept inside a single read-only transaction.
     *
     * <p>The sweep and not N standalone calls, deliberately: that is the regime SH311's cache
     * applies to, so whatever is left over is the part no resolution cache can remove — which is
     * SH312's whole question. The first page is paid for outside the measurement, since it carries
     * the layout, the regions and the theme for every page after it.</p>
     */
    private long sweep(String shape) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        long[] marginal = new long[1];
        tx.executeWithoutResult(status -> {
            renderService.digest("post:" + siteName + "/" + shape + "-0", null);
            long before = statements();
            for (int page = 1; page < PAGES; page++) {
                renderService.digest("post:" + siteName + "/" + shape + "-" + page, null);
            }
            marginal[0] = (statements() - before) / (PAGES - 1);
        });
        return marginal[0];
    }

    /** A running total rather than a cleared one: the sweep measures a difference inside itself. */
    private long statements() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics()
                .getPrepareStatementCount();
    }

    private long ceiling(String key) {
        String value = budgets.getProperty(key);
        assertThat(value).as("%s is missing from %s", key, BUDGETS).isNotNull();
        return Long.parseLong(value.trim());
    }

    // --- fixture ---

    /**
     * One site, four page shapes, each bound to a layout of its own.
     *
     * <p>Four bindings rather than four sites: SH314 measured that the graph's cost follows
     * bindings, and what varies here is meant to be the <em>page</em>, so everything above the page
     * is held as close to identical as four shapes allow — same theme, same nav region, one extra
     * region or one extra helper call each.</p>
     */
    private String buildSite(String name) {
        String plainType = "Sh312Plain" + suffix();
        String listingType = "Sh312Listing" + suffix();
        String sectionType = "Sh312Section" + suffix();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(name);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + plainType + "\":\"SH312_PLAIN\","
                    + "\"" + listingType + "\":\"SH312_LISTING\","
                    + "\"" + sectionType + "\":\"SH312_SECTION\"}");
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
            ShPostType plain = postType(plainType, (byte) 0, when);
            ShPostType listing = postType(listingType, (byte) 0, when);
            ShPostType section = postType(sectionType, (byte) 0, when);

            post(site, root, themeType, "SH312_THEME", "/t", "{\"CSS\":\"body{color:#111}\"}", when);
            post(site, root, regionType, "SH312_NAV", "/r-nav",
                    "{\"HTML\":\"<nav><a href=\\\"/plain-0\\\">Home</a></nav>\"}", when);
            // The listing region — SH312's subject. `folder` omitted, so it reads the folder of the
            // page being rendered, which is the shape that makes the read per page.
            post(site, root, regionType, "SH312_LIST", "/r-list",
                    "{\"HTML\":\"<ul>{{#query limit=" + LIMIT + "}}"
                            + "<li><a href=\\\"{{url}}\\\">{{title}}</a></li>"
                            + "{{else}}<li>none</li>{{/query}}</ul>\"}", when);

            post(site, root, layoutType, "SH312_PLAIN", "/l-plain", layout(null, null), when);
            post(site, root, layoutType, "SH312_LISTING", "/l-listing",
                    layout("SH312_LIST", null), when);
            post(site, root, layoutType, "SH312_SECTION", "/l-section",
                    layout(null, "{{#getRelation SECTIONS}}<article>{{title}}</article>"
                            + "{{else}}<p>none</p>{{/getRelation}}"), when);

            // The sections a composed page names. Ordinary posts of the plain type: what makes them
            // sections is that another page's field points at them (SH249).
            List<String> sectionIds = new ArrayList<>();
            List<String> sectionUrls = new ArrayList<>();
            for (int index = 0; index < SECTIONS; index++) {
                String url = "/s-" + index;
                sectionIds.add(post(site, root, plain, "Section " + index, url,
                        "{\"TITLE\":\"Section " + index + "\",\"TEXT\":\"<p>s</p>\"}", when));
                sectionUrls.add(url);
            }

            for (int page = 0; page < PAGES; page++) {
                post(site, root, plain, "Plain " + page, "/plain-" + page,
                        "{\"TITLE\":\"Plain " + page + "\",\"TEXT\":\"<p>body</p>\"}", when);
                post(site, root, listing, "Listing " + page, "/listing-" + page,
                        "{\"TITLE\":\"Listing " + page + "\",\"TEXT\":\"<p>body</p>\"}", when);
                post(site, root, section, "Sections by id " + page, "/sections-id-" + page,
                        "{\"TITLE\":\"By id " + page + "\",\"SECTIONS\":" + json(sectionIds) + "}",
                        when);
                post(site, root, section, "Sections by url " + page, "/sections-url-" + page,
                        "{\"TITLE\":\"By url " + page + "\",\"SECTIONS\":" + json(sectionUrls) + "}",
                        when);
            }
        });
        return name;
    }

    /** The same page frame for every shape, with at most one thing added. */
    private static String layout(String extraRegion, String body) {
        return "{\"HTML\":\"<html><head><style>{{theme.css}}</style></head><body>"
                + "<h1>{{TITLE}}</h1>"
                + "<div sh-region=\\\"SH312_NAV\\\"></div>"
                + (extraRegion == null ? "" : "<div sh-region=\\\"" + extraRegion + "\\\"></div>")
                + "<main>{{TEXT}}</main>"
                + (body == null ? "" : body)
                + "</body></html>\",\"THEME\":\"SH312_THEME\"}";
    }

    private static String json(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            out.append(index == 0 ? "" : ",").append('"').append(values.get(index)).append('"');
        }
        return out.append(']').toString();
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

    private String post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
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
        return post.getId();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
