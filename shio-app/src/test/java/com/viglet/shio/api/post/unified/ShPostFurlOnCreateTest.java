/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.ShFixtureShapeGuard;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH651 — the probe. What address does a post created through the console actually get?
 *
 * <h2>Why a probe and not a fix</h2>
 *
 * <p>SH396 found five posts created through {@code POST /api/v2/post-unified} all reported at
 * their folder's own address, and named three things it could be, with different fixes and one
 * of them not a defect at all: the console always sends a {@code furl} and the API is right to
 * require it; the write should derive one from the title; or a {@code furl} exists and the
 * address projection is wrong. This class is what tells them apart, and it is deliberately
 * about the <b>service</b> rather than the projection — the projection can only be wrong about
 * a value that is there.
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
@ShFixtureShapeGuard.BuildsDuplicateUrls(
        "aDeclaredFurlIsIdentityAndIsNeverRenumbered has to produce the collision to prove the "
                + "declared address survives it — that is the whole difference between a declared "
                + "furl and a derived one, and it cannot be asserted without two rows on one URL")
class ShPostFurlOnCreateTest {

    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String folderId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> {
            ShSite site = new ShSite();
            site.setName("furl-site-" + UUID.randomUUID());
            site.setUrl("http://furl.example.test");
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
            folderId = root.getId();

            ShPostType pt = new ShPostType();
            pt.setName("PT-FURL-" + UUID.randomUUID());
            pt.setTitle("Furl Probe Post Type");
            pt.setSystem((byte) 0);
            pt.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            pt.setOwner("admin");
            entityManager.persist(pt);

            // A declared title field, like SH396's BenchArticle: without one the write derives
            // no title either, and the probe would be measuring the wrong absence.
            ShPostTypeAttr titleAttr = new ShPostTypeAttr();
            titleAttr.setName("headline");
            titleAttr.setLabel("Headline");
            titleAttr.setOrdinal(0);
            titleAttr.setRequired((byte) 0);
            titleAttr.setIsTitle((byte) 1);
            titleAttr.setIsSummary((byte) 0);
            titleAttr.setShPostType(pt);
            entityManager.persist(titleAttr);
            pt.setShPostTypeAttrs(Set.of(titleAttr));
            entityManager.merge(pt);

            postTypeId = pt.getId();
        });
    }

    private ShPostUnified createPost(String title) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("headline", title);
        return writeService.create(new CreateRequest(
                folderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null));
    }

    /**
     * The measurement that settled it, kept as the assertion. Before SH651 all three of these
     * came back {@code furl=null} while the title derived correctly, which ruled out
     * hypothesis 1 (nothing could send a {@code furl} — {@code CreateRequest} had no such
     * component) and hypothesis 3 (a projection can only be wrong about a stored value).
     */
    @Test
    void aCreatedPostGetsAnAddressDerivedFromItsTitle() {
        ShPostUnified post = createPost("Naive Page One");

        assertThat(post.getTitle())
                .as("SH651 — the title half always worked; it is derived from the isTitle field")
                .isEqualTo("Naive Page One");
        assertThat(post.getFurl())
                .as("SH651 — and now so does the address. A post with no furl reports at its "
                        + "folder's own address, which is a 404 for every delivery URL, and the "
                        + "write answers 201 while it happens")
                .isEqualTo("naive-page-one");
    }

    @Test
    void twoPostsWithOneTitleGetTwoAddresses() {
        ShPostUnified first = createPost("Same Title");
        ShPostUnified second = createPost("Same Title");
        ShPostUnified third = createPost("Same Title");

        assertThat(first.getFurl()).isEqualTo("same-title");
        assertThat(second.getFurl())
                .as("SH651 — a curator typing one title twice gets two pages, not one page and "
                        + "one unreachable row; the suffix rule is the copy path's (SH202)")
                .isEqualTo("same-title-2");
        assertThat(third.getFurl()).isEqualTo("same-title-3");

        assertThat(first.getTitle())
                .as("SH79/SH139 — furl and title are separate namespaces, so correcting the "
                        + "address must not renumber what the curator typed")
                .isEqualTo(second.getTitle())
                .isEqualTo("Same Title");
    }

    @Test
    void aDeclaredFurlIsIdentityAndIsNeverRenumbered() {
        createPost("Taken Address");

        ObjectNode data = objectMapper.createObjectNode();
        data.put("headline", "Taken Address");
        ShPostUnified declared = writeService.create(new CreateRequest(
                folderId, postTypeId, data, ShPostState.DRAFT, "admin", null, null, "taken-address"));

        assertThat(declared.getFurl())
                .as("SH651/SH81 — a declared address is the post's identity: renumbering it "
                        + "would make the next upsert of the same document create a second post")
                .isEqualTo("taken-address");
    }

    @Test
    void aPostWithNoTitleKeepsABlankAddressRatherThanAnInventedOne() {
        ShPostUnified untitled = writeService.create(new CreateRequest(
                folderId, postTypeId, objectMapper.createObjectNode(),
                ShPostState.DRAFT, "admin", null, null));

        assertThat(untitled.getFurl())
                .as("SH38 — an uploaded file post never gets a title, and SH172 added the `id:` "
                        + "address fallback for exactly that; inventing one is worse than none")
                .isBlank();
    }
}
