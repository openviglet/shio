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
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.cda.dto.ShCdaListing;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Regression cover for two shipped CDA paths that the SH122 investigation found
 * broken by the SH22 {@code @TenantId} join hazard (see
 * {@code ShTenantJoinHazardTest} for the characterization).
 *
 * <p>Both filtered posts by <b>post-type name</b>, which forces a join onto the
 * {@code ShPostType} table; Hibernate then emits the tenant predicate against a root
 * alias it never declared, and the statement cannot be prepared. Neither path had a
 * test that reached the finder, so both failed only in production:</p>
 * <ul>
 *   <li>{@code GET /api/v2/cda/query?siteId=&postType=} — a documented SH1 endpoint.</li>
 *   <li>{@code GET /api/v2/cda/search?q=&siteId=} — but only for a site that has
 *       {@code searchablePostTypes} configured (SH52), which is why the existing
 *       search tests passed.</li>
 * </ul>
 *
 * <p>The fix is the same one already applied to {@code findByShPostType_IdAndState}
 * three lines away in the same repository: filter by the post-type <b>id</b>, whose
 * FK column already lives on {@code sh_post_unified}, and resolve name → id in the
 * service. These tests exist so the fix cannot silently regress.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaTenantJoinRegressionTest {

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String searchableSiteId;
    private String postTypeName;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postTypeName = "JoinHazard" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Join Hazard");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            siteId = persistSiteWithPost(postType, null, "Plain");
            // A site whose searchable post-types are configured — the only shape that
            // reaches the by-name search finder.
            searchableSiteId = persistSiteWithPost(postType, postTypeName, "Searchable needle");
        });
    }

    private String persistSiteWithPost(ShPostType postType, String searchableTypes, String title) {
        ShSite site = new ShSite();
        site.setName("join-hazard-" + UUID.randomUUID());
        site.setSearchablePostTypes(searchableTypes);
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

        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.PUBLISHED);
        post.setShSite(site);
        post.setShFolder(root);
        post.setShPostType(postType);
        post.setTitle(title);
        post.setFurl("/" + UUID.randomUUID());
        post.setPublished(true);
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{\"title\":\"" + title + "\"}");
        entityManager.persist(post);
        return site.getId();
    }

    @Test
    void queryFilteredByPostTypeNameWorks() {
        ShCdaListing listing = shCdaService.query(siteId, null, postTypeName, 0, 10);

        assertThat(listing.posts()).extracting("title").containsExactly("Plain");
    }

    @Test
    void queryFilteredByAnUnknownPostTypeIsEmptyNotAnError() {
        // Name → id resolution must degrade to "no results", never to a 500.
        assertThat(shCdaService.query(siteId, null, "NoSuchPostType", 0, 10).posts()).isEmpty();
    }

    @Test
    void searchOnASiteWithConfiguredSearchableTypesWorks() {
        ShCdaListing listing = shCdaService.search("needle", searchableSiteId, 0, 10);

        assertThat(listing.posts()).extracting("title").containsExactly("Searchable needle");
    }

    @Test
    void searchStillWorksWhenNoSearchableTypesAreConfigured() {
        assertThat(shCdaService.search("Plain", siteId, 0, 10).posts()).hasSize(1);
    }
}
