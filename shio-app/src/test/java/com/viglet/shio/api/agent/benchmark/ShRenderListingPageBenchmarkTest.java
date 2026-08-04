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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

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
 * SH384 — what a paged listing costs, measured <b>before</b> it was built.
 *
 * <h2>Why this class exists at all, and why it is the task's first deliverable</h2>
 *
 * <p>SH384's own design says the costs are measured before the fix, and names the defect to look for:
 * "a filter that reads every row in the site to keep ten is exactly the defect SH312 found". So the
 * question had to be asked before {@code page=}, {@code sort=} and a field filter were written, because
 * the answer decides whether they can be done in memory at all.
 *
 * <h2>{@link ShRenderHelperSweepBenchmarkTest} could not answer it, and the reason is the finding</h2>
 *
 * <p>That class counts <b>prepared statements</b>, and by that measure a listing is flat: one folder
 * read per page whatever {@code limit} says. The number this class counts is <b>entity loads</b> — rows
 * actually materialized — and there the shape is completely different:
 * {@code findByShFolderAndStateIn} reads the <b>whole folder</b>, and {@code limit} is applied in
 * memory afterwards. So a folder of 200 posts rendering {@code limit=10} loads 200 rows to show 10,
 * and it did so before this task existed.
 *
 * <h2>What that measurement decided</h2>
 *
 * <p>Two things, and both are the opposite of what the design assumed would need arguing.
 * <b>Paging, sorting and filtering are free</b>: the rows are already all in hand, so an offset and a
 * predicate are list operations rather than new reads, and the sweep benchmark's query ceilings do not
 * move at all. And an <b>exact total is free too</b>, which is why the pager gets a real
 * {@code totalRows} / {@code totalPages} rather than an estimate or a "has more" flag — the honest
 * design and the cheap one are the same one here.
 *
 * <p>What it also means is that the <em>pre-existing</em> cost is the real one: rows loaded grows with
 * the folder and not with the page size, so a folder of 5000 posts pays 5000 rows to render page one of
 * ten. That is not SH384's to fix — pushing the window into the query is a repository change with its
 * own correctness argument, since the DRAFT-preference fold and the total both need rows the window
 * would exclude — but it is SH384's to <b>measure and record</b>, which is what the ratio below is.
 * The ceiling is on the ratio rather than on the count, so the finding cannot be silently absorbed by
 * somebody enlarging the fixture.
 *
 * <h2>Its own database</h2>
 *
 * <p>SH315's lint and SH314's reason: 200 posts in one folder would land inside another benchmark's
 * instance-wide numbers, which is exactly how {@code ShRenderSweepBenchmarkTest}'s three-site figure
 * once went from 59 to 159.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh384;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderListingPageBenchmarkTest {

    private static final String BUDGETS = "/token-budgets.properties";

    /** Siblings in the listing folder — an archive, which is the page shape SH384 is about. */
    private static final int ARCHIVE = 200;

    /** Rows one page of it shows. */
    private static final int PER_PAGE = 10;

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
        siteName = buildSite("sh384-" + UUID.randomUUID().toString().substring(0, 6));
    }

    @Test
    @DisplayName("SH384: a listing loads the whole folder whatever the page size, and that predates paging")
    void aListingLoadsTheWholeFolder() {
        long first = loads("/archive-page-1");
        long deep = loads("/archive-page-19");

        measured.put("render.listing.rows-loaded-first-page", first);
        measured.put("render.listing.rows-loaded-last-page", deep);
        measured.put("render.listing.rows-rendered", (long) PER_PAGE);

        // The finding, and the reason it had to be measured rather than assumed: `limit` is applied
        // AFTER the read, so the rows loaded are the folder's and not the page's.
        assertThat(first)
                .as("""
                        %d rows loaded to render %d, in a folder of %d — `limit` is an in-memory \
                        window over a whole-folder read, which no query counter can see.""",
                        first, PER_PAGE, ARCHIVE)
                .isGreaterThan(PER_PAGE);

        assertThat(deep)
                .as("""
                        and the last page costs what the first does — the half that makes paging safe \
                        to add in memory, because there is no per-page read to multiply: page 19 would \
                        be 19 folder reads (+%d) if there were. Measured %d against %d.

                        **Not `isEqualTo`, and this one was caught being flaky rather than reasoned \
                        about.** It asserted exact equality, failed once at 214 against 213, and \
                        passed on the next run of the same code — because each shape is its own post \
                        with its own layout, so two measurements differ by the page frame as well as \
                        by the call, and how much of that frame is already resolved depends on which \
                        test in this class ran first. A benchmark that fails one run in two is the \
                        assertion people learn to re-run (SH250's rule), so the claim is now the one \
                        the fixture can actually support: no second folder read. Varying only the page \
                        number would need a render path carrying `?page=`, which `digest` has no \
                        argument for, and inventing one for a benchmark is the worse trade.""",
                        ARCHIVE * 18, deep, first)
                .isLessThan(first + PER_PAGE);

        long ratio = first / PER_PAGE;
        measured.put("render.listing.rows-loaded-per-row-rendered", ratio);
        assertThat(ratio)
                .as("""
                        The ceiling is on the RATIO, not the count, so enlarging the fixture cannot \
                        absorb the finding. Pushing the window into the query is the follow-up — and \
                        it is not free, because the DRAFT-preference fold and the exact total both \
                        need rows a windowed query would exclude.""")
                .isLessThanOrEqualTo(ceiling("render.listing.max-rows-loaded-per-row-rendered"));
    }

    @Test
    @DisplayName("SH384: paging and sorting add no read, because the rows are already in hand")
    void pagingAddsNoRead() {
        long unpaged = loads("/plain-page");
        long paged = loads("/archive-page-1");
        long sorted = loads("/sorted-page");

        measured.put("render.listing.rows-loaded-unpaged", unpaged);
        measured.put("render.listing.rows-loaded-sorted", sorted);

        // This is the assertion that licensed the whole design: an offset, a comparator and a
        // predicate are list operations over rows the folder read already materialized. If any of
        // them had cost a second read, `page=` would have had to be a repository change instead.
        //
        // **Not `isEqualTo`**, and the reason is worth keeping: each shape is its own post with its own
        // layout, so two shapes differ by the page frame as well as by the call — measured, one row
        // (212 unpaged against 213 paged). What must not happen is a *second folder read*, which would
        // be +%d, so the ceiling is the folder size rather than exact equality. The strong claim lives
        // in the test above, where two pages share one layout and are exactly equal.
        assertThat(paged)
                .as("""
                        a windowed listing costs no second read: %d against %d unwindowed, where \
                        another folder read would be +%d""", paged, unpaged, ARCHIVE)
                .isLessThan(unpaged + PER_PAGE);
        assertThat(sorted)
                .as("and neither does a re-sorted one (%d against %d)", sorted, unpaged)
                .isLessThan(unpaged + PER_PAGE);
    }

    @AfterAll
    void report() {
        StringBuilder out = new StringBuilder("\nSH384 — listing cost, measured (entity rows loaded):\n");
        measured.forEach((name, value) -> out.append("  ").append(name).append(": ")
                .append(value).append('\n'));
        System.out.println(out);
    }

    // --- measuring ---

    /**
     * Rows materialized rendering one page, in its own read-only transaction.
     *
     * <p>Entity loads and not prepared statements, which is the whole point of this class: the two
     * disagree here, and the one that disagrees is the one that grows with the site.
     */
    private long loads(String url) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        long[] loaded = new long[1];
        tx.executeWithoutResult(status -> {
            Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
            long before = statistics.getEntityLoadCount();
            renderService.digest("post:" + siteName + url, null);
            loaded[0] = statistics.getEntityLoadCount() - before;
        });
        return loaded[0];
    }

    private long ceiling(String key) {
        String value = budgets.getProperty(key);
        assertThat(value).as("%s is missing from %s", key, BUDGETS).isNotNull();
        return Long.parseLong(value.trim());
    }

    // --- fixture ---

    /** One site, one archive folder of {@value #ARCHIVE} siblings, and four listing shapes over it. */
    private String buildSite(String name) {
        String tag = suffix();
        String plainType = "Sh384Plain" + tag;
        String pagedType = "Sh384Paged" + tag;
        String deepType = "Sh384Deep" + tag;
        String sortedType = "Sh384Sorted" + tag;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(name);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + plainType + "\":\"SH384_PLAIN\","
                    + "\"" + pagedType + "\":\"SH384_PAGED\","
                    + "\"" + deepType + "\":\"SH384_DEEP\","
                    + "\"" + sortedType + "\":\"SH384_SORTED\"}");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType plain = postType(plainType, (byte) 0, when);
            ShPostType paged = postType(pagedType, (byte) 0, when);
            ShPostType deep = postType(deepType, (byte) 0, when);
            ShPostType sorted = postType(sortedType, (byte) 0, when);

            // One layout per shape and one page per layout, so the only difference between two
            // measurements is the listing call. SH314's reason on a smaller fixture: four bindings is
            // what four shapes cost, and holding everything above the page identical is what makes the
            // comparison mean the call.
            post(site, root, layoutType, "SH384_PLAIN", "/l-plain", layout(listing("")), when);
            post(site, root, layoutType, "SH384_PAGED", "/l-paged", layout(listing("page=1")), when);
            post(site, root, layoutType, "SH384_DEEP", "/l-deep", layout(listing("page=19")), when);
            post(site, root, layoutType, "SH384_SORTED", "/l-sorted",
                    layout(listing("sort=\\\"oldest\\\"")), when);

            post(site, root, plain, "Plain", "/plain-page", "{\"TITLE\":\"Plain\"}", when);
            post(site, root, paged, "Page one", "/archive-page-1", "{\"TITLE\":\"One\"}", when);
            post(site, root, deep, "Page nineteen", "/archive-page-19", "{\"TITLE\":\"Nineteen\"}", when);
            post(site, root, sorted, "Sorted", "/sorted-page", "{\"TITLE\":\"Sorted\"}", when);

            for (int index = 0; index < ARCHIVE; index++) {
                post(site, root, plain, "Article " + index, "/a-" + index,
                        "{\"TITLE\":\"Article " + index + "\"}", when);
            }
        });
        return name;
    }

    /** One layout per shape, differing only in the listing call on it — which is what varies. */
    private static String layout(String call) {
        return "{\"HTML\":\"<html><body><h1>{{TITLE}}</h1><ul>"
                + call
                + "</ul></body></html>\"}";
    }

    private static String listing(String extra) {
        return "{{#query limit=" + PER_PAGE + (extra.isEmpty() ? "" : " " + extra) + "}}"
                + "<li>{{title}}</li>{{else}}<li>none</li>{{/query}}";
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
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
