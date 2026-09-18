/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1030 — the media library answers one page, and counts the whole of what it filtered.
 *
 * <p>Two claims, and the second is the one nobody would notice breaking. Paging is visible the
 * moment it is wrong. <b>Facets counted over a page</b> are not: a library of two thousand images
 * reports fifty, the chip a curator presses to find the rest is the one telling them there is
 * nothing to find, and every number on screen is internally consistent.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-media-paging;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShStaticFilePagingTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private static final int IMAGES = 30;
    private static final int DOCUMENTS = 7;

    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ShFileStorageService storage;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String folderId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("paging-" + UUID.randomUUID());
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

            ShFolder media = new ShFolder();
            media.setName("media");
            media.setRootFolder((byte) 0);
            media.setParentFolder(root);
            media.setDate(EPOCH);
            media.setOwner("admin");
            entityManager.persist(media);
            folderId = media.getId();

            ShPostType fileType = shStaticFileService.resolveOrCreateFilePostType();
            List<ShPostUnified> rows = new ArrayList<>();
            for (int index = 0; index < IMAGES; index++) {
                rows.add(asset(site, media, fileType, "picture" + index + ".png"));
            }
            for (int index = 0; index < DOCUMENTS; index++) {
                rows.add(asset(site, media, fileType, "paper" + index + ".pdf"));
            }
            shPostUnifiedRepository.saveAll(rows).forEach(this::writeBytes);
        });
    }

    private ShPostUnified asset(ShSite site, ShFolder folder, ShPostType type, String name) {
        ShPostUnified post = new ShPostUnified();
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setState(ShPostState.PUBLISHED);
        post.setTitle(name);
        post.setData("{\"FILE\":\"" + name + "\"}");
        post.setDate(EPOCH);
        post.setOwner("admin");
        post.setShSite(null);
        return post;
    }

    /**
     * The bytes, because the category is read off the file's content type.
     *
     * <p>A fixture of rows with nothing on disk answers {@code other} for everything, which would
     * make the facet assertions below pass while proving nothing about categories. Writing one
     * byte per asset is what makes the classification real.</p>
     */
    private void writeBytes(ShPostUnified post) {
        try {
            storage.store(post.getId(), post.getTitle(),
                    new java.io.ByteArrayInputStream(new byte[] { 1 }));
        } catch (RuntimeException e) {
            throw new IllegalStateException("could not seed bytes for " + post.getTitle(), e);
        }
    }

    @Test
    @DisplayName("SH1030: a page is a page, and the total says how much was cut")
    void aPageIsAPage() {
        ShStaticFileListing first = page(null, null, 0, 10);
        ShStaticFileListing second = page(null, null, 1, 10);

        assertThat(first.items()).hasSize(10);
        assertThat(second.items()).hasSize(10);
        assertThat(first.totalFiles())
                .as("""
                        SH1030: how many match in total, not how many are in this page — the \
                        number that makes a cut visible (SH138). A listing that just stopped at \
                        ten would look complete.""")
                .isEqualTo(IMAGES + DOCUMENTS);
        assertThat(first.items()).doesNotContainAnyElementsOf(second.items());
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(10);

        // Past the end is an empty page rather than a refusal: a caller walking pages should stop
        // because there was nothing there, not because it was told off for asking.
        assertThat(page(null, null, 99, 10).items()).isEmpty();
    }

    @Test
    @DisplayName("SH1030: the facets count the whole filtered set, not the page")
    void facetsCountEverythingThatMatched() {
        ShStaticFileListing listing = page(null, null, 0, 5);

        assertThat(listing.items()).hasSize(5);
        assertThat(listing.facets())
                .as("""
                        SH1030: counted over the page, this would say 5 — and the chip a curator \
                        presses to find the other twenty-five would be the one telling them there \
                        is nothing to find. Every number on screen would agree with every other, \
                        which is what makes it the half nobody notices breaking.""")
                .containsEntry("image", (long) IMAGES)
                .containsEntry("document", (long) DOCUMENTS)
                .containsEntry("all", (long) (IMAGES + DOCUMENTS));
    }

    @Test
    @DisplayName("SH1030: a selected category still sees what else there is")
    void facetsIgnoreTheCategoryTheyOfferToSwitchTo() {
        ShStaticFileListing images = page("image", null, 0, 5);

        assertThat(images.totalFiles()).isEqualTo(IMAGES);
        assertThat(images.facets())
                .as("""
                        SH1030: the facets are counted BEFORE the category filter, because a chip \
                        saying "document 7" while `image` is selected is the answer to "what else \
                        is there". Counting inside the selection answers "30, 0" and tells nobody \
                        anything.""")
                .containsEntry("document", (long) DOCUMENTS)
                .containsEntry("all", (long) (IMAGES + DOCUMENTS));
    }

    @Test
    @DisplayName("SH1030: q narrows the total and the facets with it")
    void theTextFilterNarrowsBoth() {
        ShStaticFileListing found = page(null, "paper", 0, 50);

        assertThat(found.totalFiles()).isEqualTo(DOCUMENTS);
        assertThat(found.facets())
                .as("SH1030: the facets describe what the OTHER filters admitted, so a search "
                        + "that found only documents must not offer an image chip counting thirty")
                .containsEntry("all", (long) DOCUMENTS)
                .doesNotContainKey("image");
    }

    @Test
    @DisplayName("SH1030: the envelope is the console listing's, component for component")
    void theEnvelopeIsTheOneEveryListingUses() {
        List<String> media = names(ShStaticFileListing.class);
        List<String> console = names(ShConsoleListing.class);

        assertThat(media.subList(1, 3))
                .as("""
                        SH1030: `page` and `size` under those names and in that order. This was \
                        the one content read nobody had paged, and a third spelling of "which \
                        page is this" is the drift SH120 spent a task removing from the console \
                        listing.""")
                .containsExactly("page", "size");
        assertThat(console.subList(3, 5)).containsExactly("page", "size");
        assertThat(media)
                .as("SH1030: and the total is named for what it counts, the way totalPosts and "
                        + "totalFolders are")
                .contains("totalFiles");
    }

    private ShStaticFileListing page(String category, String q, int index, int size) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shStaticFileService.page(folderId, null, category, q, index, size));
    }

    private static List<String> names(Class<?> record) {
        assertThat(record.isRecord()).as("%s must be a record", record.getSimpleName()).isTrue();
        List<String> names = new ArrayList<>();
        for (RecordComponent component : record.getRecordComponents()) {
            names.add(component.getName());
        }
        return names;
    }
}
