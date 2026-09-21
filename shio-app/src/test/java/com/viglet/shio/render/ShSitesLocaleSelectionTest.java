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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1057 — the public locale segment selects the content, instead of decorating the link prefix.
 *
 * <p>Before this, {@code /sites/<site>/default/pt-br/about} and {@code …/en/about} rendered the same
 * page: the segment reached the SH459 check and the prefix, and nothing else. A translation lives at
 * its own URL ({@code translate} gives it a free one) and shares a translation group with its source,
 * so asking for a locale means "the member of this page's group in that locale" — answered with a 302
 * to that member's URL, in the reader's locale space, along the site's fallback chain.</p>
 *
 * @author Alexandre Oliveira
 */
// The shared context, SH315's rule: this reads only the three sites it seeds, each under a unique
// name, so it has no claim on an isolated one — and the suite had no slot left for one (SH423).
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
@com.viglet.shio.ShFixtureShapeGuard.BuildsDuplicateUrls(
        "SH1057: the unique key admits two translations at one URL, and a locale-blind read there "
                + "would redirect the reader to the address they are already at; the shared site "
                + "proves the locale is resolved in place instead")
class ShSitesLocaleSelectionTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    /** No declared policy: a locale is looked for alone. */
    private String plain;
    /** Declares pt-br → pt, so a missing Brazilian page is the Portuguese one. */
    private String chained;
    /** Two translations at one URL, which the unique key allows. */
    private String shared;

    @BeforeAll
    void seed() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        plain = "sh1057-plain-" + suffix;
        chained = "sh1057-chain-" + suffix;
        shared = "sh1057-shared-" + suffix;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1);
            ShPostType pageType = postType("Sh1057Page", (byte) 0);

            ShFolder plainRoot = site(plain, null, layoutType);
            String about = UUID.randomUUID().toString();
            post(plainRoot, pageType, "/", "en", "home", null);
            post(plainRoot, pageType, "/about", "en", "about in english", about);
            post(plainRoot, pageType, "/about-2", "pt-br", "sobre em portugues", about);
            post(plainRoot, pageType, "/contact", "en", "contact in english", null);

            ShFolder chainRoot = site(chained, "{\"default\":\"en\",\"enabled\":[\"pt-br\",\"pt\",\"en\"],"
                    + "\"fallback\":{\"pt-br\":\"pt\"}}", layoutType);
            String guide = UUID.randomUUID().toString();
            post(chainRoot, pageType, "/", "en", "chained home", null);
            post(chainRoot, pageType, "/guide", "en", "guide in english", guide);
            post(chainRoot, pageType, "/guia", "pt", "guia em portugues europeu", guide);

            ShFolder sharedRoot = site(shared, null, layoutType);
            String faq = UUID.randomUUID().toString();
            post(sharedRoot, pageType, "/faq", "en", "faq in english", faq);
            post(sharedRoot, pageType, "/faq", "pt-br", "perguntas em portugues", faq);
        });
    }

    @Test
    @DisplayName("SH1057: asking for a translation's locale sends the reader to the translation")
    void theSegmentSelectsTheTranslation() throws Exception {
        at(plain, "pt-br", "/about")
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/sites/" + plain + "/default/pt-br/about-2"));
        body(at(plain, "pt-br", "/about-2").andExpect(status().isOk()), "sobre em portugues");
    }

    @Test
    @DisplayName("SH1057: and back — the English member of a Portuguese page's group")
    void itSelectsInEitherDirection() throws Exception {
        at(plain, "en", "/about-2")
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/sites/" + plain + "/default/en/about"));
    }

    @Test
    @DisplayName("SH1057: a page with no translation in that locale is still served, as it always was")
    void aMissingTranslationServesThePageAtThePath() throws Exception {
        body(at(plain, "pt-br", "/contact").andExpect(status().isOk()), "contact in english");
    }

    @Test
    @DisplayName("SH1057: the route's own default asks for nothing, so every link it built still works")
    void theRoutesDefaultSelectsNothing() throws Exception {
        body(at(plain, "en-us", "/about-2").andExpect(status().isOk()), "sobre em portugues");
        body(mockMvc.perform(get("/sites/" + plain + "/")).andExpect(status().isOk()), "home");
    }

    @Test
    @DisplayName("SH1057: the site's fallback chain decides what a missing locale becomes")
    void theFallbackChainIsFollowed() throws Exception {
        // pt-br is declared and nothing is written in it, so this is both halves at once: the SH459
        // check accepts a declared locale, and pt-br → pt names the European Portuguese page.
        at(chained, "pt-br", "/guide")
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/sites/" + chained + "/default/pt-br/guia"));
        body(at(chained, "pt-br", "/guia").andExpect(status().isOk()), "guia em portugues europeu");
    }

    @Test
    @DisplayName("SH1057: a site that declared a default is addressed in it, not in en-us")
    void aDeclaredDefaultNamesTheSitesUrls() throws Exception {
        at(chained, "en-us", "/guide?utm=x")
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/sites/" + chained + "/default/en/guide?utm=x"));
        mockMvc.perform(get("/sites/" + chained + "/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/sites/" + chained + "/default/en/"));
        body(at(chained, "en", "/guide").andExpect(status().isOk()), "guide in english");
    }

    @Test
    @DisplayName("SH1057: an undeclared site keeps en-us, so no existing URL moves")
    void anUndeclaredSiteKeepsItsUrls() throws Exception {
        body(at(plain, "en-us", "/about").andExpect(status().isOk()), "about in english");
    }

    @Test
    @DisplayName("SH1057: two translations at one URL are told apart there, not redirected to themselves")
    void aSharedUrlIsResolvedInPlace() throws Exception {
        body(at(shared, "pt-br", "/faq").andExpect(status().isOk()), "perguntas em portugues");
        body(at(shared, "en", "/faq").andExpect(status().isOk()), "faq in english");
    }

    private ResultActions at(String site, String locale, String path) throws Exception {
        return mockMvc.perform(get("/sites/" + site + "/default/" + locale + path));
    }

    private static void body(ResultActions result, String expected) throws Exception {
        assertThat(result.andReturn().getResponse().getContentAsString())
                .as("SH1057: the locale in the URL chose which page this is")
                .contains(expected);
    }

    private ShFolder site(String name, String locales, ShPostType layoutType) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setFurl(name);
        site.setDate(WHEN);
        site.setOwner("admin");
        site.setPostTypeLayout("{\"Sh1057Page\":\"SH1057_LAYOUT\"}");
        site.setLocales(locales);
        entityManager.persist(site);

        ShFolder root = new ShFolder();
        root.setName(name);
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(WHEN);
        root.setOwner("admin");
        entityManager.persist(root);

        // A template is not content: no locale, and one per site because bindings name a title.
        ShPostUnified layout = row(root, layoutType, "/l", null, null);
        layout.setTitle("SH1057_LAYOUT");
        layout.setData("{\"HTML\":\"<html><body>{{TEXT}}</body></html>\"}");
        entityManager.persist(layout);
        return root;
    }

    private void post(ShFolder root, ShPostType type, String furl, String locale, String text,
                      String group) {
        ShPostUnified page = row(root, type, furl, locale, group);
        page.setTitle(text);
        page.setData("{\"TEXT\":\"" + text + "\"}");
        entityManager.persist(page);
    }

    private ShPostUnified row(ShFolder root, ShPostType type, String furl, String locale, String group) {
        ShPostUnified row = new ShPostUnified();
        row.setId(UUID.randomUUID().toString());
        row.setState(ShPostState.PUBLISHED);
        row.setShSite(root.getShSite());
        row.setShFolder(root);
        row.setShPostType(type);
        row.setFurl(furl);
        row.setLocale(locale);
        row.setTranslationGroupId(group != null ? group : row.getId());
        row.setOwner("admin");
        row.setDate(WHEN);
        return row;
    }

    private ShPostType postType(String name, byte system) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name).getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(WHEN);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }
}
