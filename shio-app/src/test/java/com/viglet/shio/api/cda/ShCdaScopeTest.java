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

import com.viglet.shio.api.cda.dto.ShCdaSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH2 per-site enforcement in {@link ShCdaService}. A token whose context is
 * restricted to site A must read A and be blind to site B; an unrestricted
 * context (or no token at all) sees everything (the SH1 behaviour).
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaScopeTest {

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteAId;
    private String siteBId;
    private String folderAId;
    private String postAId;
    private String postBId;
    private static final String FURL = "/scoped-post";

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postType.setName("PT-SCOPE-" + UUID.randomUUID());
            postType.setTitle("Scope Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            // Site A + root folder + published post
            ShSite siteA = persistSite();
            siteAId = siteA.getId();
            ShFolder rootA = persistRoot(siteA);
            folderAId = rootA.getId();
            postAId = persistPost(siteA, rootA, postType, FURL);

            // Site B + root folder + published post (different furl)
            ShSite siteB = persistSite();
            siteBId = siteB.getId();
            ShFolder rootB = persistRoot(siteB);
            postBId = persistPost(siteB, rootB, postType, "/other-post");
        });
    }

    private ShSite persistSite() {
        ShSite site = new ShSite();
        site.setName("scope-site-" + UUID.randomUUID());
        site.setDescription("Scope Test Site");
        site.setUrl("http://scope.example.test");
        site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        site.setOwner("admin");
        entityManager.persist(site);
        return site;
    }

    private ShFolder persistRoot(ShSite site) {
        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        root.setOwner("admin");
        entityManager.persist(root);
        return root;
    }

    private String persistPost(ShSite site, ShFolder folder, ShPostType postType, String furl) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.PUBLISHED);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle("Scoped");
        post.setFurl(furl);
        post.setPublished(true);
        post.setOwner("admin");
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{\"title\":\"Scoped\"}");
        entityManager.persist(post);
        return post.getId();
    }

    @AfterAll
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (String id : new String[]{postAId, postBId}) {
                if (id != null) {
                    shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
                }
            }
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void restrictTo(String... siteIds) {
        var auth = new UsernamePasswordAuthenticationToken("delivery", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", ShApiTokenScope.READ, Set.of(siteIds)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void restrictedTokenReadsAllowedSiteOnly() {
        restrictTo(siteAId);

        assertThat(shCdaService.getSite(siteAId)).isPresent();
        assertThat(shCdaService.getSite(siteBId)).isEmpty();
        assertThat(shCdaService.getPost(postAId)).isPresent();
        assertThat(shCdaService.getPost(postBId)).isEmpty();
        assertThat(shCdaService.getPostByUrl(siteBId, "/other-post")).isEmpty();
        assertThat(shCdaService.listChildren(folderAId, 0, 50)).isPresent();
    }

    @Test
    void restrictedTokenListsAllowedSiteOnly() {
        restrictTo(siteAId);

        List<ShCdaSite> sites = shCdaService.listSites();
        assertThat(sites).extracting(ShCdaSite::id).contains(siteAId).doesNotContain(siteBId);
    }

    @Test
    void unrestrictedAccessSeesEverything() {
        // No authentication / no token context → unrestricted (SH1 behaviour).
        assertThat(shCdaService.getSite(siteAId)).isPresent();
        assertThat(shCdaService.getSite(siteBId)).isPresent();
        assertThat(shCdaService.getPost(postBId)).isPresent();
    }
}
