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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.viglet.shio.render.ShPageRenderService.ShPageRedirectException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH385 — a replica keeps the original's URL space.
 *
 * <h2>What was wrong</h2>
 *
 * <p>A replication that changes a path breaks every link into it: search results, other people's
 * pages, a decade of email. The capture knows every source URL (SH377) and the conversion knows where
 * each page landed, so the map exists at exactly one moment — and nothing recorded it or served it.
 *
 * <h2>The decision this file is really about: a redirect's source is its friendly URL</h2>
 *
 * <p>The design offered a {@code from} field. Not having one is strictly less machinery for the same
 * capability, and it pays off <b>four</b> times, each of which is a test or a rule that did not have to
 * be written: the router already resolves a path to a post, so a redirect costs <b>no query</b> and no
 * site-wide scan on a 404; SH323 makes a friendly URL site-unique, so two redirects claiming one path
 * is impossible rather than reported; a redirect shadowing a real page is already SH99's
 * {@code duplicate-url} <em>error</em>, which is one of the two findings the task asked for; and in the
 * CLI's tree lint "served or redirected" is one lookup, because a Redirect is a post with that
 * {@code url}.
 *
 * <p>Its own database, for {@link ShRenderRelationSearchTest}'s reason (SH246).
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh385;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShRenderRedirectTest {

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private ShRenderPostTypes shRenderPostTypes;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void setup() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh385-" + tag;
        String pageType = "Sh385Page" + tag;
        shRenderPostTypes.ensure();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"" + pageType + "\":\"SH385_LAYOUT\"}");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setPosition((byte) 1);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType layoutType = type(ShSystemPostType.PAGE_LAYOUT);
            ShPostType redirectType = type(ShSystemPostType.REDIRECT);
            ShPostType page = postType(pageType, when);

            post(site, root, layoutType, "SH385_LAYOUT", "/l",
                    "{\"HTML\":\"<html><body><h1>{{TITLE}}</h1></body></html>\"}", when);
            post(site, root, page, "About", "/about", "{\"TITLE\":\"About\"}", when);

            // The four redirect shapes, and the fifth thing that is not one.
            post(site, root, redirectType, "Old about", "/company/about-us",
                    "{\"TO\":\"/about\"}", when);
            post(site, root, redirectType, "Old about, permanently", "/legacy/about",
                    "{\"TO\":\"/about\",\"PERMANENT\":\"1\"}", when);
            post(site, root, redirectType, "Permanent as a boolean", "/legacy/about-2",
                    "{\"TO\":\"/about\",\"PERMANENT\":true}", when);
            post(site, root, redirectType, "Moved house", "/partners",
                    "{\"TO\":\"https://partners.example.com/\"}", when);
            post(site, root, redirectType, "Unfinished", "/nowhere", "{}", when);
        });
    }

    @Test
    @DisplayName("a path only a Redirect claims answers with a redirect, not with a page")
    void aRedirectIsNotAPage() {
        assertThatThrownBy(() -> render("/company/about-us"))
                .isInstanceOf(ShPageRedirectException.class)
                .satisfies(thrown -> {
                    ShPageRedirectException redirect = (ShPageRedirectException) thrown;
                    assertThat(redirect.to()).isEqualTo("/about");
                    assertThat(redirect.permanent())
                            .as("""
                                    302 by default. A 301 is cached by browsers effectively for ever, \
                                    so the expensive answer is the one you ask for — SH121's shape, \
                                    applied to a status code.""")
                            .isFalse();
                });
    }

    @Test
    @DisplayName("PERMANENT is honoured whether it arrived as a checkbox or as a boolean")
    void permanentIsReadFromEitherSpelling() {
        // The Check Box widget stores "1" through the console and `true` through post.upsert, and a
        // 301 that silently became a 302 because of which one wrote it is not a defect anybody finds
        // by reading the content.
        for (String path : new String[] { "/legacy/about", "/legacy/about-2" }) {
            assertThatThrownBy(() -> render(path))
                    .isInstanceOf(ShPageRedirectException.class)
                    .satisfies(thrown ->
                            assertThat(((ShPageRedirectException) thrown).permanent())
                                    .as(path)
                                    .isTrue());
        }
    }

    @Test
    @DisplayName("an absolute target survives as written, because a path can move to another host")
    void anAbsoluteTargetIsKept() {
        assertThatThrownBy(() -> render("/partners"))
                .isInstanceOf(ShPageRedirectException.class)
                .satisfies(thrown -> assertThat(((ShPageRedirectException) thrown).to())
                        .isEqualTo("https://partners.example.com/"));
    }

    @Test
    @DisplayName("a redirect with no target is an unfinished page, not a redirect to nowhere")
    void anEmptyTargetIsNotARedirect() {
        // Redirecting to `/` would send a reader to the homepage and tell nobody. Falling through
        // makes it a post with no layout binding, which is a true statement about a post that is not
        // finished — and `verify`'s `redirect-target-missing` is where the defect is reported.
        assertThatThrownBy(() -> render("/nowhere"))
                .isNotInstanceOf(ShPageRedirectException.class);
    }

    @Test
    @DisplayName("an ordinary page is untouched, so one type comparison is the whole cost")
    void anOrdinaryPageStillRenders() {
        assertThat(render("/about")).contains("<h1>About</h1>");
    }

    private String render(String url) {
        return shPageRenderService.render(siteName, url, ShPostState.DRAFT).html();
    }

    // ── fixture ─────────────────────────────────────────────────────────────────────

    private ShPostType type(String name) {
        return entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElseThrow();
    }

    private ShPostType postType(String name, Instant when) {
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem((byte) 0);
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
