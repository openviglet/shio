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

import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.api.cda.dto.ShCdaPost;
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
 * SH5 per-environment read resolution in {@link ShCdaService}. A {@code PROD}
 * token (and any no-token / console access) reads {@code PUBLISHED} only; a
 * {@code PREVIEW} token reads draft-preferred (the {@code DRAFT} row when present,
 * else {@code PUBLISHED}), so it sees unpublished edits and draft-only posts.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaEnvTest {

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String dualPostId;       // has divergent DRAFT + PUBLISHED rows
    private String draftOnlyPostId;  // has only a DRAFT row (never published)

    private static final String DUAL_FURL = "/env-dual";
    private static final String PUBLISHED_TITLE = "Published v1";
    private static final String DRAFT_TITLE = "Draft v2";
    private static final String DRAFT_ONLY_TITLE = "Draft Only";

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postType.setName("PT-ENV-" + UUID.randomUUID());
            postType.setTitle("Env Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.now());
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShSite site = new ShSite();
            site.setName("env-site-" + UUID.randomUUID());
            site.setDescription("Env Test Site");
            site.setUrl("http://env.example.test");
            site.setDate(Instant.now());
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.now());
            root.setOwner("admin");
            entityManager.persist(root);

            // A post with divergent published + draft rows under the same id.
            dualPostId = UUID.randomUUID().toString();
            persistRow(dualPostId, ShPostState.PUBLISHED, site, root, postType, PUBLISHED_TITLE, DUAL_FURL, true);
            persistRow(dualPostId, ShPostState.DRAFT, site, root, postType, DRAFT_TITLE, DUAL_FURL, false);

            // A draft-only post (never published).
            draftOnlyPostId = UUID.randomUUID().toString();
            persistRow(draftOnlyPostId, ShPostState.DRAFT, site, root, postType, DRAFT_ONLY_TITLE, "/env-draft-only", false);
        });
    }

    private void persistRow(String id, ShPostState state, ShSite site, ShFolder folder,
                            ShPostType postType, String title, String furl, boolean published) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle(title);
        post.setFurl(furl);
        post.setPublished(published);
        post.setOwner("admin");
        post.setDate(Instant.now());
        post.setData("{\"title\":\"" + title + "\"}");
        entityManager.persist(post);
    }

    @AfterAll
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (String id : new String[]{dualPostId, draftOnlyPostId}) {
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

    private void useEnv(ShApiTokenEnv env) {
        var auth = new UsernamePasswordAuthenticationToken("delivery", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", ShApiTokenScope.READ, Set.of(), env));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void prodReadsPublishedRowOnly() {
        useEnv(ShApiTokenEnv.PROD);

        // Divergent post → the PUBLISHED values, by id and by url.
        assertThat(shCdaService.getPost(dualPostId)).map(ShCdaPost::title).contains(PUBLISHED_TITLE);
        assertThat(shCdaService.getPostByUrl(siteId, DUAL_FURL)).map(ShCdaPost::title).contains(PUBLISHED_TITLE);

        // Draft-only post is invisible.
        assertThat(shCdaService.getPost(draftOnlyPostId)).isEmpty();
    }

    @Test
    void previewReadsDraftPreferred() {
        useEnv(ShApiTokenEnv.PREVIEW);

        // Divergent post → the DRAFT values win, by id and by url.
        assertThat(shCdaService.getPost(dualPostId)).map(ShCdaPost::title).contains(DRAFT_TITLE);
        assertThat(shCdaService.getPostByUrl(siteId, DUAL_FURL)).map(ShCdaPost::title).contains(DRAFT_TITLE);

        // Draft-only post becomes visible.
        assertThat(shCdaService.getPost(draftOnlyPostId)).map(ShCdaPost::title).contains(DRAFT_ONLY_TITLE);
    }

    @Test
    void listingIsDraftPreferredAndDeduped() {
        // PROD: only the published row of the divergent post; draft-only absent.
        useEnv(ShApiTokenEnv.PROD);
        ShCdaListing prod = shCdaService.query(siteId, null, null, 0, 50);
        assertThat(prod.posts()).extracting(ShCdaPostSummary::id)
                .contains(dualPostId).doesNotContain(draftOnlyPostId);
        assertThat(prod.posts()).filteredOn(p -> p.id().equals(dualPostId))
                .extracting(ShCdaPostSummary::title).containsExactly(PUBLISHED_TITLE);

        // PREVIEW: the divergent post appears once with the draft title; draft-only present.
        SecurityContextHolder.clearContext();
        useEnv(ShApiTokenEnv.PREVIEW);
        ShCdaListing preview = shCdaService.query(siteId, null, null, 0, 50);
        assertThat(preview.posts()).extracting(ShCdaPostSummary::id)
                .contains(dualPostId, draftOnlyPostId);
        assertThat(preview.posts()).filteredOn(p -> p.id().equals(dualPostId))
                .extracting(ShCdaPostSummary::title).containsExactly(DRAFT_TITLE);
    }

    @Test
    void noTokenStaysPublishedOnly() {
        // No authentication / no token context → PROD behaviour (SH1 preserved).
        assertThat(shCdaService.getPost(dualPostId)).map(ShCdaPost::title).contains(PUBLISHED_TITLE);
        assertThat(shCdaService.getPost(draftOnlyPostId)).isEmpty();
    }
}
