/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

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
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH384 — {@code {{#query}}} gets a second page, an order and a filter.
 *
 * <h2>What was wrong</h2>
 *
 * <p>The helper took {@code folder}, {@code type} and {@code limit}, and {@code limit} exists because
 * a listing with no ceiling gets slower as the site grows. Both are right, and together they meant the
 * helper could render only <b>the first page</b> of anything: an insights hub with two hundred
 * articles, or the same hub filtered to one industry, was not expressible — and that is the most
 * common page shape on a real site after the homepage.
 *
 * <h2>What the measurement decided, before any of this was written</h2>
 *
 * <p>{@code ShRenderListingPageBenchmarkTest} was the task's first deliverable, and it is the reason
 * all three of these are in-memory operations rather than a repository change: the folder read is
 * <b>already whole-folder</b> (213 rows loaded to render 10), so a window, a comparator and a
 * predicate cost no read — and an exact total is free, which is why {@code @total} and {@code @pages}
 * are real counts rather than a "has more" flag.
 *
 * <p>Its own database, for {@link ShRenderRelationSearchTest}'s reason (SH246).
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh384u;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderListingPageTest {

    /** Six articles, so three pages of two and a partial last page are both expressible. */
    private static final int ARTICLES = 6;

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void setup() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh384u-" + tag;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");

            // One post-type per shape, so one layout each and one page each. A page's own listing call
            // is what is under test, and a layout is the only place a call can live.
            String[] shapes = { "one", "two", "four", "oldest", "title", "finance", "counts", "plain" };
            StringBuilder bindings = new StringBuilder("{");
            for (int index = 0; index < shapes.length; index++) {
                bindings.append(index == 0 ? "" : ",")
                        .append('"').append("Sh384").append(shapes[index]).append(tag).append('"')
                        .append(":\"L_").append(shapes[index]).append('"');
            }
            site.setPostTypeLayout(bindings.append('}').toString());
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setPosition((byte) 1);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            // The articles get a folder of their own, and that is not fixture tidiness: with
            // everything in root, `{{#query}}` with no `folder=` read the page's own folder and
            // listed the layouts as articles. Five assertions failed on it at once.
            ShFolder articles = new ShFolder();
            articles.setName("articles");
            articles.setRootFolder((byte) 0);
            articles.setParentFolder(root);
            articles.setPosition((byte) 1);
            articles.setDate(when);
            articles.setOwner("admin");
            entityManager.persist(articles);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType article = postType("Sh384Article" + tag, (byte) 0, when);

            post(site, root, layoutType, "L_one", "/l-one", layout("folder=\\\"/articles\\\" limit=2 page=1"), when);
            post(site, root, layoutType, "L_two", "/l-two", layout("folder=\\\"/articles\\\" limit=2 page=2"), when);
            post(site, root, layoutType, "L_four", "/l-four", layout("folder=\\\"/articles\\\" limit=2 page=4"), when);
            post(site, root, layoutType, "L_oldest", "/l-oldest", layout("folder=\\\"/articles\\\" limit=2 sort=\\\"oldest\\\""), when);
            post(site, root, layoutType, "L_title", "/l-title", layout("folder=\\\"/articles\\\" limit=9 sort=\\\"title\\\""), when);
            post(site, root, layoutType, "L_finance", "/l-finance",
                    layout("folder=\\\"/articles\\\" limit=9 field=\\\"industry\\\" value=\\\"finance\\\""), when);
            post(site, root, layoutType, "L_counts", "/l-counts", pager("folder=\\\"/articles\\\" limit=2 page=2"), when);
            post(site, root, layoutType, "L_plain", "/l-plain", layout("folder=\\\"/articles\\\" limit=9"), when);

            for (String shape : shapes) {
                post(site, root, postType("Sh384" + shape + tag, (byte) 0, when),
                        "Page " + shape, "/" + shape, "{}", when);
            }

            // Six articles, each a minute apart so "newest" and "oldest" are distinguishable, with
            // titles that sort differently from their dates so `sort="title"` proves something.
            String[] titles = { "Zebra", "Yak", "Xerus", "Walrus", "Vole", "Uakari" };
            String[] industries = { "finance", "finance", "energy", "finance", "energy", "energy" };
            for (int index = 0; index < ARTICLES; index++) {
                post(site, articles, article, titles[index], "/a-" + index,
                        "{\"industry\":\"" + industries[index] + "\"}",
                        when.plusSeconds(60L * index));
            }
        });
    }

    @Test
    @DisplayName("page 1 and page 2 are different rows of the same listing, which is the whole task")
    void pagingWindowsTheListing() {
        // Newest first, so page 1 is the last two articles created.
        assertThat(rows("/one")).containsExactly("Uakari", "Vole");
        assertThat(rows("/two")).containsExactly("Walrus", "Xerus");
    }

    @Test
    @DisplayName("a page past the end renders {{else}}, the same branch as an empty folder")
    void aPagePastTheEndIsEmpty() {
        // One branch for "nothing here" whatever the reason, because a template draws the same thing
        // for both — and a reader who edits `?page=` to 40 must not get the last page silently.
        assertThat(render("/four")).contains("<p class=\"empty\">none</p>");
    }

    @Test
    @DisplayName("sort=oldest reads an archive forwards, and it is still a total order")
    void oldestReversesTheDefault() {
        assertThat(rows("/oldest")).containsExactly("Zebra", "Yak");
    }

    @Test
    @DisplayName("sort=title orders by title, not by date, and case does not decide it")
    void titleSortsByTitle() {
        assertThat(rows("/title"))
                .containsExactly("Uakari", "Vole", "Walrus", "Xerus", "Yak", "Zebra");
    }

    @Test
    @DisplayName("a field filter keeps the posts whose field carries the value")
    void theFieldFilterNarrowsTheListing() {
        // Three of the six are `finance`, and the order is still the listing's.
        assertThat(rows("/finance")).containsExactly("Walrus", "Yak", "Zebra");
    }

    @Test
    @DisplayName("the pager's facts are exact, because the total was free once the rows were in hand")
    void thePagerHasRealCounts() {
        String html = render("/counts");

        // A "has more" flag is what this would have been if the whole-folder read had not already
        // materialized every matching row — the measurement is what made an exact total the cheap
        // answer as well as the honest one.
        assertThat(html).contains("page 2 of 3 (6 rows)");
        assertThat(html).contains("prev=1 next=3");
        assertThat(html).contains("hasPrev=true hasNext=true");
    }

    @Test
    @DisplayName("an unpaged listing is unchanged, so every existing template renders as it did")
    void theDefaultIsPageOne() {
        assertThat(rows("/plain"))
                .containsExactly("Uakari", "Vole", "Walrus", "Xerus", "Yak", "Zebra");
    }

    @Test
    @DisplayName("rendering the same page twice produces the same bytes")
    void renderingIsStable() {
        assertThat(render("/two")).isEqualTo(render("/two"));
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private java.util.List<String> rows(String url) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<li>([^<]*)</li>").matcher(render(url));
        java.util.List<String> out = new java.util.ArrayList<>();
        while (matcher.find()) {
            out.add(matcher.group(1));
        }
        return out;
    }

    private String render(String url) {
        return shPageRenderService.render(siteName, url, ShPostState.DRAFT).html();
    }

    private static String layout(String call) {
        return "{\"HTML\":\"<html><body><ul>{{#query " + call + "}}<li>{{title}}</li>"
                + "{{else}}<p class=\\\"empty\\\">none</p>{{/query}}</ul></body></html>\"}";
    }

    /**
     * The pager, drawn inside the block — the only scope Handlebars gives a block helper — and
     * <b>once</b>, from {@code @last}. Without that guard it repeated per row, which a test found.
     */
    private static String pager(String call) {
        return "{\"HTML\":\"<html><body>{{#query " + call + "}}<i>{{title}}</i>"
                + "{{#if @last}}"
                + "<span>page {{@page}} of {{@pages}} ({{@total}} rows)</span>"
                + "<span>prev={{@prevPage}} next={{@nextPage}}</span>"
                + "<span>hasPrev={{@hasPrev}} hasNext={{@hasNext}}</span>"
                + "{{/if}}{{/query}}</body></html>\"}";
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
