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
 * SH459 — the locale segment is the format segment's twin, and it lied the same way.
 *
 * <h2>The 200 this closes</h2>
 *
 * <p>SH457 fixed the format slot: {@code /sites/publico/viglet-logo.png} is a 404 rather than the
 * home page with a 200. Its own note named the locale slot as the same shape and left it — and the
 * two are not reachable by the same probe, which is why one task could not close both. A
 * root-relative reference puts its first segment in the <b>format</b> slot, so SH457's check caught
 * every case it measured; a URL that names a format and then a locale gets past it, and
 * {@code /sites/<site>/default/xx-yy/} answered the home page with a 200 exactly as
 * {@code /sites/<site>/qualquer} had.</p>
 *
 * <h2>Why the site's own content and not a fixed value</h2>
 *
 * <p>"Vestigial means fixed" was right for the format, whose slot names a binding table this product
 * does not have. Pinning the locale to {@code en-us} would make a Portuguese site's public URLs say
 * {@code en-us} — a lie of a different kind, and a statement about a multi-language CMS that a
 * route-lenience fix is not entitled to make. So the rule is a fact about the site: the locale is
 * accepted when the site has content in it, or when it is the default this route writes.</p>
 *
 * <h2>And on a site that does publish en-us (SH1279)</h2>
 *
 * <p>Measured on Quilhara: a pt-br trail listed on the en-us search page linked to
 * {@code /en-us/trilhas/crista-do-vento}, and that URL answered 200 with the Portuguese page. A
 * second site here has that shape: an en-us page linking to a pt-br page, an en-us page and a path
 * nothing publishes. The Portuguese-only site above is the other side: its {@code en-us} links keep
 * working, because there the label is not a language.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-locale-segment;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShSitesLocaleSegmentTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private static final String BILINGUAL_LAYOUT = "<html><body><p>{{TEXT}}</p>"
            + "<a id=\"pt\" href=\"/trilhas/crista\">pt</a>"
            + "<a id=\"en\" href=\"/trails/crest?x=1\">en</a>"
            + "<a id=\"gone\" href=\"/nowhere\">gone</a></body></html>";

    private MockMvc mockMvc;
    private String siteName;
    private String bilingualName;

    @BeforeAll
    void seedAPublishedHomePageInPtBr() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        siteName = "sh459-" + UUID.randomUUID().toString().substring(0, 8);
        bilingualName = "sh1279-" + UUID.randomUUID().toString().substring(0, 8);
        seedABilingualSite();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setFurl(siteName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"Sh459Page\":\"SH459_LAYOUT\"}");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName(siteName);
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType pageType = postType("Sh459Page", (byte) 0, when);

            // The layout is a template, not content: it carries no locale, so it must not widen the
            // set the route accepts. That is the second thing this fixture pins.
            post(site, root, layoutType, "SH459_LAYOUT", "/l", null,
                    "{\"HTML\":\"<html><body>{{TEXT}}</body></html>\"}", when);
            post(site, root, pageType, "Home", "/", "pt-br", "{\"TEXT\":\"ola\"}", when);
        });
    }

    @Test
    @DisplayName("SH459: a locale the site has no content in is a 404, not the home page with a 200")
    void anUnknownLocaleIsAbsentRatherThanTheHomePage() throws Exception {
        mockMvc.perform(get("/sites/" + siteName + "/default/xx-yy/"))
                .andExpect(status().isNotFound())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("and it teaches what the site does have, which is the one thing a "
                                + "caller cannot derive from the URL it sent (P6)")
                        .contains("pt-br"));
    }

    @Test
    @DisplayName("SH459: the locale the content is actually in serves the page")
    void theSitesOwnLocaleServesThePage() throws Exception {
        mockMvc.perform(get("/sites/" + siteName + "/default/pt-br/"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("ola"));
    }

    @Test
    @DisplayName("SH459: the route's own default still answers, on a site that has no en-us content")
    void theDefaultLocaleIsAlwaysAccepted() throws Exception {
        // Every URL this route builds writes `en-us` (ShSitesAPI.DEFAULT_LOCALE), and the links a
        // rendered page carries are built from the prefix. Refusing it on a site whose content is
        // pt-br would break the site's own navigation to fix its address bar.
        mockMvc.perform(get("/sites/" + siteName + "/default/en-us/"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/sites/" + siteName + "/"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SH459: the check costs nothing on the shapes SH457 already answers")
    void theFormatCheckStillRunsFirst() throws Exception {
        // The format slot is checked in `Address.of`, before any site is resolved, so a
        // root-relative asset reference is still SH457's 404 and never reaches the locale query.
        mockMvc.perform(get("/sites/" + siteName + "/viglet-logo.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("SH1279: a link carries its target's locale when the target is not in the reader's")
    void aLinkCarriesItsTargetsLocale() throws Exception {
        String base = "/sites/" + bilingualName + "/default/";
        mockMvc.perform(get(base + "en-us/"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("SH1279: the pt-br page is linked in its own locale's space, not the page's")
                        .contains("href=\"" + base + "pt-br/trilhas/crista\"")
                        .as("SH1279: a target in the reader's locale keeps the reader's prefix, query too")
                        .contains("href=\"" + base + "en-us/trails/crest?x=1\"")
                        .as("SH1279: a path nothing publishes is rebased as SH466 rebases it")
                        .contains("href=\"" + base + "en-us/nowhere\""));
    }

    @Test
    @DisplayName("SH1279: a page under a language it is not in is redirected to its own, not served")
    void aMismatchedLocaleRedirects() throws Exception {
        String base = "/sites/" + bilingualName + "/default/";
        mockMvc.perform(get(base + "en-us/trilhas/crista"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", base + "pt-br/trilhas/crista"));
        mockMvc.perform(get(base + "pt-br/trilhas/crista"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("crista"));
    }

    private void seedABilingualSite() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(bilingualName);
            site.setFurl(bilingualName);
            site.setDate(when);
            site.setOwner("admin");
            site.setPostTypeLayout("{\"Sh1279Page\":\"SH1279_LAYOUT\"}");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName(bilingualName);
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType pageType = postType("Sh1279Page", (byte) 0, when);

            post(site, root, layoutType, "SH1279_LAYOUT", "/l", null,
                    "{\"HTML\":\"" + BILINGUAL_LAYOUT.replace("\"", "\\\"") + "\"}", when);
            post(site, root, pageType, "Home", "/", "en-us", "{\"TEXT\":\"hello\"}", when);
            post(site, root, pageType, "Crest", "/trails/crest", "en-us", "{\"TEXT\":\"crest\"}", when);
            post(site, root, pageType, "Crista", "/trilhas/crista", "pt-br", "{\"TEXT\":\"crista\"}", when);
        });
    }

    private ShPostType postType(String name, byte system, Instant when) {
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
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String locale, String data, Instant when) {
        ShPostUnified row = new ShPostUnified();
        row.setId(UUID.randomUUID().toString());
        row.setState(ShPostState.PUBLISHED);
        row.setShSite(site);
        row.setShFolder(folder);
        row.setShPostType(type);
        row.setTitle(title);
        row.setFurl(furl);
        row.setLocale(locale);
        row.setOwner("admin");
        row.setDate(when);
        row.setData(data);
        entityManager.persist(row);
    }
}
