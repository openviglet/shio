/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.api.staticfile.ShStaticFileUsage;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.reference.ShObjectReferenceRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1034 — the index says what the posts say, through every write that changes them.
 *
 * <h2>What these assertions are for</h2>
 *
 * <p>A derived index fails in one way: it stops agreeing with what it derives from. That failure is
 * <b>silent</b> — nothing throws, the read just answers with edges that were true yesterday — so
 * the cases below are the lifecycle transitions where the two could drift apart, each asserted at
 * the boundary rather than through the service's internals. {@code ShReferenceIndexMaintenanceLintTest}
 * covers the other half of the same risk: the writer nobody remembered.</p>
 *
 * <p><b>The shared context, deliberately.</b> This class was written with a database of its own and
 * did not need one: SH315's rule is that a test measuring the <em>instance</em> may not share a
 * context, and every read here is scoped to an id this class created — {@code listUsage(assetId)},
 * {@code referrerCounts(ids)}, {@code findByToObjectId(id)}. Nothing counts rows the fixture did not
 * write, so no neighbour can move a number, and a slot in the context budget is worth more than the
 * reassurance (it had one left when this was written).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShObjectReferenceTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShObjectReferenceService shObjectReferenceService;
    @Autowired
    private ShObjectReferenceRepository shObjectReferenceRepository;
    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ShPostUnifiedWriteService writeService;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String folderId;
    private String typeId;
    private String assetId;
    private String otherAssetId;

    @BeforeAll
    void setup() {
        tx().executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ref-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();

            ShPostType article = new ShPostType();
            article.setName("Sh1034Article");
            article.setTitle("SH1034 Article");
            article.setDescription("SH1034 fixture");
            article.setDate(EPOCH);
            entityManager.persist(article);
            typeId = article.getId();
            attribute(article, "HERO", ShSystemWidget.FILE);
            attribute(article, "RELATED", ShSystemWidget.CONTENT_SELECT);
            // The field that makes the model-driven walk worth having: a plain text field whose
            // value is an id is NOT a reference, and the substring form could not tell.
            attribute(article, "NOTE", ShSystemWidget.TEXT);

            assetId = asset("hero.png");
            otherAssetId = asset("spare.png");
        });
    }

    private String asset(String name) {
        ShPostUnified post = new ShPostUnified();
        post.setShFolder(entityManager.getReference(ShFolder.class, folderId));
        post.setShPostType(shStaticFileService.resolveOrCreateFilePostType());
        post.setState(ShPostState.PUBLISHED);
        post.setTitle(name);
        post.setData("{\"FILE\":\"" + name + "\"}");
        post.setDate(EPOCH);
        post.setOwner("admin");
        entityManager.persist(post);
        entityManager.flush();
        return post.getId();
    }

    private void attribute(ShPostType type, String name, String widget) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setShPostType(type);
        attr.setShWidget(shWidgetRepository.findFirstByName(widget));
        entityManager.persist(attr);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    /** A post referencing {@code target} through {@code HERO}, written the way the API writes. */
    private String post(String target) {
        var data = objectMapper.createObjectNode();
        data.put("HERO", target == null ? "" : target);
        data.put("NOTE", "mentions " + assetId + " in prose");
        return tx().execute(status -> writeService.create(
                new ShPostUnifiedWriteService.CreateRequest(folderId, typeId, data,
                        ShPostState.DRAFT, "admin", null, null)).getId());
    }

    // --- the walk ---

    @Test
    @DisplayName("SH1034: a create indexes the fields the type declares, and nothing else")
    void aCreateIndexesDeclaredFieldsOnly() {
        String postId = post(assetId);

        assertThat(shObjectReferenceService.referrersOf(assetId))
                .extracting(edge -> edge.getFromPostId() + "/" + edge.getFieldName())
                .contains(postId + "/HERO");

        // The NOTE field holds the same id in prose and is a Text widget. The substring form this
        // replaced could not tell the two apart, so it reported the page twice — once for a
        // modelled reference and once for a sentence. Exactly one edge, from the declared field.
        assertThat(shObjectReferenceRepository.findByToObjectId(assetId))
                .filteredOn(edge -> postId.equals(edge.getFromPostId()))
                .hasSize(1);
    }

    @Test
    @DisplayName("SH1034: an update that clears the field removes the edge")
    void clearingAFieldRemovesTheEdge() {
        String postId = post(otherAssetId);
        assertThat(shObjectReferenceService.isReferenced(otherAssetId)).isTrue();

        var cleared = objectMapper.createObjectNode();
        cleared.put("HERO", "");
        tx().executeWithoutResult(status -> writeService.update(postId,
                new ShPostUnifiedWriteService.UpdateRequest(cleared, null, "admin")));

        // The case a patch-shaped maintenance gets wrong: a cleared field produces no edge to
        // compare against, so anything short of a rewrite leaves the old row and the asset reads
        // as used forever. `reindex` deletes first, which is why this passes.
        assertThat(shObjectReferenceRepository.findByToObjectId(otherAssetId))
                .filteredOn(edge -> postId.equals(edge.getFromPostId()))
                .isEmpty();
    }

    @Test
    @DisplayName("SH1034: publishing indexes the published row as its own document")
    void publishingIndexesTheSecondRow() {
        String postId = post(assetId);
        tx().executeWithoutResult(status -> writeService.publish(postId, "admin"));

        assertThat(shObjectReferenceRepository.findByToObjectId(assetId))
                .filteredOn(edge -> postId.equals(edge.getFromPostId()))
                .extracting(edge -> edge.getFromState())
                .contains(ShPostState.DRAFT, ShPostState.PUBLISHED);
    }

    @Test
    @DisplayName("SH1034: unpublishing forgets that state's edges and keeps the draft's")
    void unpublishingForgetsOneState() {
        String postId = post(assetId);
        tx().executeWithoutResult(status -> writeService.publish(postId, "admin"));
        tx().executeWithoutResult(status -> writeService.unpublish(postId, "admin"));

        // Both states, because forgetting both on an unpublish would make the asset read as
        // unused while a draft still points at it — and `asset-unused`'s fix is "delete it".
        assertThat(shObjectReferenceRepository.findByToObjectId(assetId))
                .filteredOn(edge -> postId.equals(edge.getFromPostId()))
                .extracting(edge -> edge.getFromState())
                .containsOnly(ShPostState.DRAFT);
    }

    @Test
    @DisplayName("SH1034: a trashed post stops holding its assets, and a restore gives them back")
    void trashAndRestoreMoveTheEdges() {
        String postId = post(otherAssetId);
        assertThat(referrerIds(otherAssetId)).contains(postId);

        tx().executeWithoutResult(status -> writeService.delete(postId, "admin"));
        // A post in the trash does not keep an asset alive: a curator emptying the media library
        // is asking what nothing REACHES, and a trashed page reaches nothing.
        assertThat(referrerIds(otherAssetId)).doesNotContain(postId);

        tx().executeWithoutResult(status -> writeService.restoreFromTrash(postId, "admin"));
        // And the inverse, which no save performs — the restore is a bulk UPDATE, so without an
        // explicit reindex the edges would stay gone and the asset would read as unused forever.
        assertThat(referrerIds(otherAssetId)).contains(postId);
    }

    @Test
    @DisplayName("SH1034: a purge forgets both states")
    void purgeForgetsEverything() {
        String postId = post(otherAssetId);
        tx().executeWithoutResult(status -> writeService.publish(postId, "admin"));
        tx().executeWithoutResult(status -> writeService.delete(postId, "admin"));
        tx().executeWithoutResult(status -> writeService.purge(postId, "admin"));

        assertThat(shObjectReferenceRepository.findByToObjectId(otherAssetId))
                .filteredOn(edge -> postId.equals(edge.getFromPostId()))
                .isEmpty();
    }

    // --- the reads the surfaces use ---

    @Test
    @DisplayName("SH1034: listUsage answers from the index, one row per referring post")
    void listUsageIsOneRowPerPost() {
        String postId = post(assetId);
        tx().executeWithoutResult(status -> writeService.publish(postId, "admin"));

        List<ShStaticFileUsage> usage = shStaticFileService.listUsage(assetId);

        // Published preferred, and ONE row: the post has a draft and a published edge, and a
        // dialog listing the same page twice is a report the curator has to de-duplicate by eye.
        assertThat(usage).filteredOn(row -> row.postId().equals(postId)).singleElement()
                .satisfies(row -> assertThat(row.published()).isTrue());
    }

    @Test
    @DisplayName("SH1034: the counts answer for every id asked, zero included")
    void countsIncludeZero() {
        String unreferenced = tx().execute(status -> asset("nobody-wants-me.png"));
        post(assetId);

        Map<String, Long> counts =
                shObjectReferenceService.referrerCounts(List.of(assetId, unreferenced));

        // Zero is the value `asset-unused` and `shio assets prune` act on, so an absent key would
        // push the same `getOrDefault` into every caller — and the one that forgot it would read
        // "no answer" as "no references".
        assertThat(counts).containsKey(unreferenced);
        assertThat(counts.get(unreferenced)).isZero();
        assertThat(counts.get(assetId)).isPositive();
    }

    @Test
    @DisplayName("SH1034: one post referencing one asset twice counts once")
    void aPostCountsOnce() {
        String twice = tx().execute(status -> asset("twice.png"));
        var data = objectMapper.createObjectNode();
        data.put("HERO", twice);
        data.putArray("RELATED").add(twice);
        String postId = tx().execute(status -> writeService.create(
                new ShPostUnifiedWriteService.CreateRequest(folderId, typeId, data,
                        ShPostState.DRAFT, "admin", null, null)).getId());

        // Two edges, one place to look. A count of rows would tell a curator the asset is used
        // twice and a badge saying 2 over a list of 1 is the disagreement P9 exists to prevent.
        assertThat(shObjectReferenceRepository.findByToObjectId(twice))
                .filteredOn(edge -> postId.equals(edge.getFromPostId())).hasSize(2);
        assertThat(shObjectReferenceService.referrerCounts(List.of(twice)).get(twice)).isEqualTo(1L);
    }

    private List<String> referrerIds(String objectId) {
        return shObjectReferenceService.referrersOf(objectId).stream()
                .map(edge -> edge.getFromPostId()).toList();
    }
}
