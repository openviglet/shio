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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.api.post.revision.ShPostRevisionService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH990 — the CDA reads the {@code state=} grammar, and a token reads no more with it than without.
 *
 * <p>SH5 split the delivery API by token: production reads what is published, preview reads the
 * draft-preferred row. {@code state} names a version inside that, and the property that matters is
 * the one a front end cannot check for itself — a production token asking for a draft is refused,
 * not answered with the published row that would look like a draft identical to what is live. The
 * site allow-list applies to whichever row a state finds, so a scoped preview token cannot reach a
 * draft on a site outside its list either.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaStateTest {

    private static final String FURL = "/state-dual";
    private static final String LIVE = "Live v1";
    private static final String DRAFTED = "Drafted v2";

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShPostRevisionService shPostRevisionService;
    @Autowired
    private ShPostRevisionRepository shPostRevisionRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String postId;
    private String draftOnlyId;
    private int revision;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShPostType type = new ShPostType();
            type.setName("PT-STATE-" + UUID.randomUUID());
            type.setTitle("State Test Type");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);

            ShSite site = new ShSite();
            site.setName("state-site-" + UUID.randomUUID());
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

            postId = UUID.randomUUID().toString();
            row(postId, ShPostState.PUBLISHED, site, root, type, LIVE, FURL);
            row(postId, ShPostState.DRAFT, site, root, type, DRAFTED, FURL);
            draftOnlyId = UUID.randomUUID().toString();
            row(draftOnlyId, ShPostState.DRAFT, site, root, type, "Never released", "/state-draft-only");
        });
        revision = tx.execute(status -> {
            ShPostUnified then = new ShPostUnified();
            then.setId(postId);
            then.setState(ShPostState.PUBLISHED);
            then.setShPostType(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                    .orElseThrow().getShPostType());
            then.setTitle("As it was");
            then.setData("{\"title\":\"As it was\"}");
            return shPostRevisionService.capture(then, ShPostRevision.Reason.PUBLISH, "admin")
                    .getVersionNo();
        });
    }

    private void row(String id, ShPostState state, ShSite site, ShFolder folder, ShPostType type,
                     String title, String furl) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setPublished(state == ShPostState.PUBLISHED);
        post.setOwner("admin");
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{\"title\":\"" + title + "\"}");
        entityManager.persist(post);
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            shPostRevisionRepository.deleteAll(shPostRevisionRepository.findByPostIdOrderByVersionNoDesc(postId));
            for (String id : new String[] { postId, draftOnlyId }) {
                shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete);
            }
        });
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void token(ShApiTokenEnv env, Set<String> sites) {
        var auth = new UsernamePasswordAuthenticationToken("delivery", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", ShApiTokenScope.READ, sites, env));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("SH990: a production token reads published with state, and is refused a draft or a revision")
    void aProductionTokenReadsNoMore() {
        token(ShApiTokenEnv.PROD, Set.of());

        assertThat(shCdaService.getPost(postId, "published")).map(ShCdaPost::title).contains(LIVE);
        assertThatThrownBy(() -> shCdaService.getPost(postId, "draft"))
                .as("SH990: refused, not answered with the published row — which would read as a "
                        + "draft identical to what is live, the answer a preview cannot detect")
                .isInstanceOf(ShCdaService.PreviewTokenRequiredException.class);
        assertThatThrownBy(() -> shCdaService.getPostByUrl(siteId, FURL, null, "rev:" + revision))
                .isInstanceOf(ShCdaService.PreviewTokenRequiredException.class);
        assertThatThrownBy(() -> shCdaService.query(siteId, null, null, 0, 50, "draft"))
                .isInstanceOf(ShCdaService.PreviewTokenRequiredException.class);
    }

    @Test
    @DisplayName("SH990: a preview token picks the row it names, where it used to get the draft-preferred one")
    void aPreviewTokenPicksTheRow() {
        token(ShApiTokenEnv.PREVIEW, Set.of());

        assertThat(shCdaService.getPost(postId, null)).map(ShCdaPost::title).contains(DRAFTED);
        assertThat(shCdaService.getPost(postId, "published"))
                .as("SH990: what is live, through the token a preview front end already holds")
                .map(ShCdaPost::title).contains(LIVE);
        assertThat(shCdaService.getPostByUrl(siteId, FURL, null, "draft")).map(ShCdaPost::title)
                .contains(DRAFTED);
        assertThat(shCdaService.getPost(draftOnlyId, "published")).isEmpty();

        assertThat(shCdaService.query(siteId, null, null, 0, 50, "published").posts())
                .extracting(ShCdaPostSummary::id)
                .contains(postId).doesNotContain(draftOnlyId);
    }

    @Test
    @DisplayName("SH990: a revision reads as what the page said, by id and by URL, and a listing refuses one")
    void aRevisionReadsAsThePageSaid() {
        token(ShApiTokenEnv.PREVIEW, Set.of());

        assertThat(shCdaService.getPost(postId, "rev:" + revision)).map(ShCdaPost::title)
                .contains("As it was");
        assertThat(shCdaService.getPostByUrl(siteId, FURL, null, "rev:" + revision))
                .map(ShCdaPost::title).contains("As it was");
        assertThatThrownBy(() -> shCdaService.search("state", siteId, 0, 50, "rev:" + revision))
                .isInstanceOf(ShContentPerspective.RevisionNotServedException.class);
        assertThatThrownBy(() -> shCdaService.getPost(postId, "rev:999999"))
                .isInstanceOf(com.viglet.shio.api.post.unified.ShPostStateNotFoundException.class);
    }

    @Test
    @DisplayName("SH990: the site allow-list applies to the row a state finds, so a scoped token reaches no other site's draft")
    void theAllowListStillApplies() {
        token(ShApiTokenEnv.PREVIEW, Set.of("some-other-site"));

        assertThat(shCdaService.getPost(postId, "draft")).isEmpty();
        assertThat(shCdaService.getPost(postId, "rev:" + revision)).isEmpty();
        assertThat(shCdaService.getPostByUrl(siteId, FURL, null, "draft")).isEmpty();
    }
}
