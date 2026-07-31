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
import java.util.Map;
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

import com.viglet.shio.api.cda.dto.ShCdaField;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH10 CDA write surface ({@link ShCdaWriteService}) — the Universal Editor
 * save-back. Edits land on the {@code DRAFT} row; publishing promotes it; the
 * SH2 per-site allow-list is enforced on the write path; and {@code getEditable}
 * exposes the post-type field schema.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaWriteTest {

    @Autowired
    private ShCdaWriteService shCdaWriteService;
    @Autowired
    private com.viglet.shio.api.post.unified.ShPostDraftMergeService shPostDraftMergeService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String draftPostId;       // DRAFT row only
    private String publishedOnlyId;   // PUBLISHED row only
    private String toPublishId;       // DRAFT row, promoted in the publish test

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postType.setName("PT-WR-" + UUID.randomUUID());
            postType.setTitle("Write Test Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr titleAttr = attr(postType, "title", "Title", 0, (byte) 1, (byte) 0);
            ShPostTypeAttr bodyAttr = attr(postType, "body", "Body", 1, (byte) 0, (byte) 1);
            entityManager.persist(titleAttr);
            entityManager.persist(bodyAttr);
            postType.setShPostTypeAttrs(Set.of(titleAttr, bodyAttr));
            entityManager.merge(postType);

            ShSite site = new ShSite();
            site.setName("wr-site-" + UUID.randomUUID());
            site.setUrl("http://wr.example.test");
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            draftPostId = UUID.randomUUID().toString();
            persistRow(draftPostId, ShPostState.DRAFT, site, root, postType, "Original");

            publishedOnlyId = UUID.randomUUID().toString();
            persistRow(publishedOnlyId, ShPostState.PUBLISHED, site, root, postType, "Live");

            toPublishId = UUID.randomUUID().toString();
            persistRow(toPublishId, ShPostState.DRAFT, site, root, postType, "Pending");
        });
    }

    private ShPostTypeAttr attr(ShPostType postType, String name, String label,
                                int ordinal, byte isTitle, byte isSummary) {
        ShPostTypeAttr a = new ShPostTypeAttr();
        a.setName(name);
        a.setLabel(label);
        a.setOrdinal(ordinal);
        a.setRequired((byte) 0);
        a.setIsTitle(isTitle);
        a.setIsSummary(isSummary);
        a.setShPostType(postType);
        return a;
    }

    private void persistRow(String id, ShPostState state, ShSite site, ShFolder folder,
                            ShPostType postType, String title) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle(title);
        post.setPublished(state == ShPostState.PUBLISHED);
        post.setOwner("admin");
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{\"title\":\"" + title + "\"}");
        entityManager.persist(post);
    }

    @AfterAll
    void tearDown() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            for (String id : new String[]{draftPostId, publishedOnlyId, toPublishId}) {
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

    private void useToken(ShApiTokenScope scope, Set<String> siteIds) {
        var auth = new UsernamePasswordAuthenticationToken("editor", null, List.of());
        auth.setDetails(new ShApiTokenContext("tok", scope, siteIds, ShApiTokenEnv.PROD));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Map<String, JsonNode> change(String prop, String value) {
        return Map.of(prop, objectMapper.valueToTree(value));
    }

    @Test
    void patchUpdatesDraftAndRederivesTitle() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var result = shCdaWriteService.patch(draftPostId, change("title", "Updated"), false, "editor");

        assertThat(result).map(ShCdaPost::title).contains("Updated");
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().contains("Updated");
        // No published row was created (save-as-draft only).
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.PUBLISHED)).isEmpty();
    }

    @Test
    void patchOnPublishedOnlyPostSeedsADraft() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var result = shCdaWriteService.patch(publishedOnlyId, change("title", "Edited"), false, "editor");

        assertThat(result).map(ShCdaPost::title).contains("Edited");
        // The new draft holds the edit; the live published snapshot is untouched.
        assertThat(shPostUnifiedRepository.findByIdAndState(publishedOnlyId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().contains("Edited");
        assertThat(shPostUnifiedRepository.findByIdAndState(publishedOnlyId, ShPostState.PUBLISHED))
                .get().extracting(ShPostUnified::getTitle).isEqualTo("Live");
    }

    @Test
    void patchWithPublishPromotesDraftToPublished() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var result = shCdaWriteService.patch(toPublishId, change("title", "Announced"), true, "editor");

        assertThat(result).map(ShCdaPost::published).contains(true);
        assertThat(shPostUnifiedRepository.findByIdAndState(toPublishId, ShPostState.PUBLISHED))
                .get().extracting(ShPostUnified::getTitle).isEqualTo("Announced");
    }

    @Test
    void perSiteAllowListBlocksWritesToOtherSites() {
        useToken(ShApiTokenScope.WRITE, Set.of("some-other-site"));

        var result = shCdaWriteService.patch(draftPostId, change("title", "Hijacked"), false, "editor");

        assertThat(result).isEmpty();
        // The draft was not touched.
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().doesNotContain("Hijacked");
    }

    @Test
    void getEditableReturnsTheFieldSchema() {
        useToken(ShApiTokenScope.WRITE, Set.of());

        var editable = shCdaWriteService.getEditable(draftPostId);

        assertThat(editable).isPresent();
        assertThat(editable.get().fields()).extracting(ShCdaField::name)
                .contains("title", "body");
        assertThat(editable.get().fields()).filteredOn(f -> f.name().equals("title"))
                .extracting(ShCdaField::isTitle).containsExactly(true);
    }

    // --- SH335: the merge is the console's too, and needs no token ---

    /**
     * The property that decided the design. The two candidate fixes were a client
     * read-modify-write and a merge verb; the first reintroduces a lost update, and this is what
     * that would have cost — a curator editing the headline would have written back the body they
     * read a minute ago, over whatever a colleague changed in between.
     */
    @Test
    void theMergeLeavesAFieldItWasNotGiven() {
        useToken(ShApiTokenScope.WRITE, Set.of());
        shCdaWriteService.patch(draftPostId, change("summary", "Only the summary"), false, "editor");

        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString()
                .contains("Only the summary")
                .contains("title");
    }

    /**
     * SH335 — the console path holds no API token at all, which is the whole point: the editor
     * used to make a curator mint a WRITE key to save content their session already authorized.
     * The guard moved out to each caller, so the merge itself must not depend on one being there.
     */
    @Test
    void theSharedMergeRunsWithNoTokenInContext() {
        SecurityContextHolder.clearContext();

        var merged = shPostDraftMergeService.merge(draftPostId, change("title", "By a curator"),
                false, "curator");

        assertThat(merged).isPresent();
        assertThat(shPostUnifiedRepository.findByIdAndState(draftPostId, ShPostState.DRAFT))
                .get().extracting(ShPostUnified::getData).asString().contains("By a curator");
    }

    @Test
    void mergingAPostThatDoesNotExistIsEmptyRatherThanAnError() {
        assertThat(shPostDraftMergeService.merge("no-such-post", change("title", "x"), false, "c"))
                .isEmpty();
    }
}
