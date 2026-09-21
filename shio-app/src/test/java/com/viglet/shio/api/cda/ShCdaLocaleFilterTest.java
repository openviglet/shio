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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1127 — a delivery listing can be asked for one language.
 *
 * <p>SH1057's CDA half. {@code /post/by-url} was the one read that took a locale; the folder listing,
 * the query and the search took none, so a front end rendering a Portuguese section of a two-language
 * site got every English page beside every Portuguese one and had to drop half of them itself — after
 * paging, so a page of fifty held whatever mix the database returned and {@code totalPosts} counted
 * both languages.</p>
 *
 * <p>Both read paths are held: a production token pages in the database, a preview token reads its
 * two states whole and pages draft-preferred in memory, and the locale has to reach each.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaLocaleFilterTest {

    private static final String PROBE = "locprobe" + UUID.randomUUID().toString().substring(0, 6);

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String folderId;
    private String postTypeName;
    private final List<String> postIds = new ArrayList<>();

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShPostType type = new ShPostType();
            type.setName("PT-LOCFILTER-" + UUID.randomUUID());
            type.setTitle("Locale filter type");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);
            postTypeName = type.getName();

            ShSite site = new ShSite();
            site.setName("locale-filter-" + UUID.randomUUID());
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();

            post(site, root, type, "/en-1", "en", ShPostState.PUBLISHED, when);
            post(site, root, type, "/en-2", "en", ShPostState.PUBLISHED, when);
            post(site, root, type, "/pt-1", "pt-br", ShPostState.PUBLISHED, when);
            post(site, root, type, "/pt-draft", "pt-br", ShPostState.DRAFT, when);
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String id : postIds) {
                shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
            }
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theFolderListingPagesOneLanguageAndCountsIt() {
        ShCdaListing listing = shCdaService.listChildren(folderId, 0, 50, null, "pt-br").orElseThrow();

        assertThat(locales(listing)).as("SH1127: a pt-br listing held English pages").containsOnly("pt-br");
        assertThat(listing.totalPosts())
                .as("SH1127: the count is of the language asked for, or a pager built on it is wrong")
                .isEqualTo(1);
        assertThat(shCdaService.listChildren(folderId, 0, 50, null, null).orElseThrow().totalPosts())
                .as("and asking for no locale is every locale, as before").isEqualTo(3);
    }

    @Test
    void theQueryFiltersBySiteAndByTypeAlike() {
        assertThat(locales(shCdaService.query(siteId, null, null, 0, 50, null, "pt-br")))
                .as("SH1127: the site query").containsOnly("pt-br");
        assertThat(locales(shCdaService.query(siteId, null, postTypeName, 0, 50, null, "en")))
                .as("SH1127: the site-and-type query").hasSize(2).containsOnly("en");
    }

    @Test
    void theSearchFindsTheTermInOneLanguage() {
        assertThat(locales(shCdaService.search(PROBE, siteId, 0, 50, null, "pt-br")))
                .as("SH1127: the search").containsOnly("pt-br");
        assertThat(locales(shCdaService.search(PROBE, siteId, 0, 50, null, " ")))
                .as("SH1127: a blank locale is not asked, not the empty tag").hasSize(3);
    }

    @Test
    void aPreviewTokenGetsTheDraftInThatLanguageAndNothingElse() {
        var auth = new UsernamePasswordAuthenticationToken("delivery", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", ShApiTokenScope.READ, Set.of(), ShApiTokenEnv.PREVIEW));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ShCdaListing listing = shCdaService.listChildren(folderId, 0, 50, null, "pt-br").orElseThrow();

        assertThat(listing.posts()).extracting(ShCdaPostSummary::furl)
                .as("SH1127: the preview read pages in memory, and the locale has to reach it there too")
                .containsExactlyInAnyOrder("/pt-1", "/pt-draft");
        assertThat(listing.totalPosts()).isEqualTo(2);
    }

    private static List<String> locales(ShCdaListing listing) {
        return listing.posts().stream().map(ShCdaPostSummary::locale).toList();
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String furl, String locale,
                      ShPostState state, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(PROBE + " " + furl);
        post.setFurl(furl);
        post.setLocale(locale);
        post.setPublished(state == ShPostState.PUBLISHED);
        post.setOwner("admin");
        post.setDate(when);
        post.setData("{\"title\":\"" + PROBE + "\"}");
        entityManager.persist(post);
        postIds.add(post.getId());
    }
}
