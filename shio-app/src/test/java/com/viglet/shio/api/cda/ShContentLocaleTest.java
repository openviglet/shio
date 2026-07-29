/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH53 content i18n: a created post defaults to its own locale group; a
 * translation is a linked sibling; and the CDA resolves a URL per locale,
 * reporting the available translations.
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShContentLocaleTest {

    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String siteId;
    private String folderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            ShSite site = new ShSite();
            site.setName("loc-site-" + UUID.randomUUID());
            site.setUrl("http://loc.example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();

            ShPostType pt = new ShPostType();
            pt.setName("PT-LOC-" + UUID.randomUUID());
            pt.setTitle("Locale Test Post Type");
            pt.setSystem((byte) 0);
            pt.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            pt.setOwner("admin");
            entityManager.persist(pt);
            postTypeId = pt.getId();
        });
    }

    private ObjectNode data(String title) {
        ObjectNode d = objectMapper.createObjectNode();
        d.put("title", title);
        return d;
    }

    @Test
    void createDefaultsToEnglishAndOwnGroup() {
        ShPostUnified post = writeService.create(new CreateRequest(
                folderId, postTypeId, data("Hello"), ShPostState.DRAFT, "admin", null, null));
        assertThat(post.getLocale()).isEqualTo("en");
        assertThat(post.getTranslationGroupId()).isEqualTo(post.getId());
    }

    @Test
    void translateLinksASiblingAndListsBoth() {
        ShPostUnified en = writeService.create(new CreateRequest(
                folderId, postTypeId, data("About"), ShPostState.DRAFT, "admin", null, null));
        ShPostUnified pt = writeService.translate(en.getId(), "pt-br", null, "admin");

        assertThat(pt.getId()).isNotEqualTo(en.getId());
        assertThat(pt.getLocale()).isEqualTo("pt-br");
        assertThat(pt.getTranslationGroupId()).isEqualTo(en.getTranslationGroupId());

        List<ShPostUnified> siblings = writeService.listTranslations(en.getId());
        assertThat(siblings).extracting(ShPostUnified::getLocale)
                .containsExactlyInAnyOrder("en", "pt-br");
    }

    @Test
    void cdaResolvesByLocaleAndReportsAvailable() {
        // Two PUBLISHED translations sharing one furl + group, differing by locale.
        //
        // SH323 — this shape is *deliverable* and is still a defect the instance reports: an SH79
        // address carries no locale, so `ShAddressResolver` refuses the pair (SH321) and
        // `duplicate-url` is an error over it, with a clause naming the locales so the curator who
        // did this deliberately is told why it does not help. What locale-aware delivery is for is
        // a translation with its own URL. The assertions below are the *delivery* contract, which
        // is unchanged.
        String group = UUID.randomUUID().toString();
        persistPublished("/about-loc", "en", "About", group);
        persistPublished("/about-loc", "pt-br", "Sobre", group);

        ShCdaPost en = shCdaService.getPostByUrl(siteId, "/about-loc", "en").orElseThrow();
        ShCdaPost pt = shCdaService.getPostByUrl(siteId, "/about-loc", "pt-br").orElseThrow();

        assertThat(en.locale()).isEqualTo("en");
        assertThat(en.title()).isEqualTo("About");
        assertThat(pt.locale()).isEqualTo("pt-br");
        assertThat(pt.title()).isEqualTo("Sobre");
        assertThat(en.availableLocales()).containsExactlyInAnyOrder("en", "pt-br");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SH323: two posts on one URL in one locale resolve, not 500")
    void theLocaleScopedReadIsDeterministicOnAContestedUrl() {
        // The actual SH323 defect: `findByShSite_IdAndFurlAndStateAndLocale` was a derived
        // Optional, so Spring Data expected at most one row and threw
        // IncorrectResultSizeDataAccessException — a 500 for a defect `duplicate-url` reports.
        // Two posts, one locale, *separate* translation groups: not translations, just a
        // contested URL, which is what SH321 fixed one parameter along.
        String contested = "/contested-loc";
        persistPublished(contested, "en", "First", UUID.randomUUID().toString());
        persistPublished(contested, "en", "Second", UUID.randomUUID().toString());

        ShCdaPost scoped = shCdaService.getPostByUrl(siteId, contested, "en").orElseThrow();
        ShCdaPost blind = shCdaService.getPostByUrl(siteId, contested).orElseThrow();

        assertThat(scoped.id())
                .as("""
                        the same row every request — SH278's rule on a second identity: \
                        consistently wrong is a defect somebody recognises and `verify` names, \
                        while a coin-flip reads as flakiness in delivery. A *write* refuses \
                        instead (ShAddressResolver.DuplicateUrlException).""")
                .isEqualTo(blind.id());
        assertThat(List.of("First", "Second")).contains(scoped.title());
    }

    private void persistPublished(String furl, String locale, String title, String group) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            ShSite site = entityManager.find(ShSite.class, siteId);
            ShFolder folder = entityManager.find(ShFolder.class, folderId);
            ShPostType pt = entityManager.find(ShPostType.class, postTypeId);
            ShPostUnified post = new ShPostUnified();
            post.setId(UUID.randomUUID().toString());
            post.setState(ShPostState.PUBLISHED);
            post.setShSite(site);
            post.setShFolder(folder);
            post.setShPostType(pt);
            post.setTitle(title);
            post.setFurl(furl);
            post.setLocale(locale);
            post.setTranslationGroupId(group);
            post.setPublished(true);
            post.setOwner("admin");
            post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            post.setData("{\"title\":\"" + title + "\"}");
            entityManager.persist(post);
        });
    }
}
