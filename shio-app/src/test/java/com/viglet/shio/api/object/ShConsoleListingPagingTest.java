/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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
import com.viglet.shio.api.object.dto.ShConsolePostRow;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH165 — the console listing is paged, and the paging is the database's.
 *
 * <p>What is actually being asserted is that the three things the browser used to do over the
 * whole folder — page, order, filter — all now happen on the server and agree with each other.
 * A paged listing whose order or filter stayed client-side is the half-migration §XIV.7 warns
 * about: it answers {@code 200} with one page sorted among itself, which reads exactly like a
 * correct answer.</p>
 *
 * <p>The DRAFT-preference is the load-bearing part of the query and the reason it is a
 * predicate rather than an in-memory dedup: a post with both rows must occupy <em>one</em> slot
 * in the page and be counted once, or {@code totalPosts} over-reports and the last page comes
 * back short for no visible reason.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShConsoleListingPagingTest {

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShObjectNavigationService navigation;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String folderId;
    private String siteId;

    /** Five posts (Alpha…Echo), one of which is published as well as drafted, plus a subfolder. */
    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("ut-paging-site-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            folderId = root.getId();

            ShFolder sub = new ShFolder();
            sub.setName("Subsection");
            sub.setRootFolder((byte) 0);
            sub.setParentFolder(root);
            sub.setDate(EPOCH);
            sub.setOwner("admin");
            entityManager.persist(sub);

            ShPostType postType = new ShPostType();
            postType.setName("PT-PAGING-" + UUID.randomUUID());
            postType.setTitle("Paging Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(EPOCH);
            postType.setOwner("admin");
            entityManager.persist(postType);

            List<String> titles = List.of("Alpha", "Bravo", "Charlie", "Delta", "Echo");
            for (int i = 0; i < titles.size(); i++) {
                String id = UUID.randomUUID().toString();
                entityManager.persist(post(id, ShPostState.DRAFT, site, root, postType,
                        titles.get(i), EPOCH.plusSeconds(i)));
                // Charlie exists in both states: one logical post, two rows.
                if (titles.get(i).equals("Charlie")) {
                    entityManager.persist(post(id, ShPostState.PUBLISHED, site, root, postType,
                            titles.get(i), EPOCH.plusSeconds(i)));
                }
            }
        });
    }

    @Test
    @DisplayName("a page is a page: size rows, the total counts the folder")
    void pagesTheFolderAndCountsItWhole() {
        ShConsoleListing first = list(0, 2, ShConsoleListingSort.TITLE, null);

        assertThat(titles(first)).containsExactly("Alpha", "Bravo");
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        // Five logical posts, not six: Charlie's PUBLISHED row is the same post.
        assertThat(first.totalPosts()).isEqualTo(5);

        assertThat(titles(list(1, 2, ShConsoleListingSort.TITLE, null)))
                .containsExactly("Charlie", "Delta");
        assertThat(titles(list(2, 2, ShConsoleListingSort.TITLE, null)))
                .containsExactly("Echo");
        assertThat(titles(list(3, 2, ShConsoleListingSort.TITLE, null))).isEmpty();
    }

    @Test
    @DisplayName("the DRAFT row wins, and occupies exactly one slot in the page")
    void draftPreferredWithinThePage() {
        ShConsoleListing listing = list(0, 50, ShConsoleListingSort.TITLE, null);

        assertThat(titles(listing)).containsExactly("Alpha", "Bravo", "Charlie", "Delta", "Echo");
        assertThat(listing.posts().stream().filter(row -> "Charlie".equals(row.title())).toList())
                .singleElement()
                .satisfies(row -> assertThat(row.state()).isEqualTo(ShPostState.DRAFT.name()));
    }

    @Test
    @DisplayName("the order is the server's, across pages")
    void ordersOnTheServer() {
        // Newest first. Asserted across a page boundary on purpose: an order applied after the
        // slice would put Echo on page 2 and nothing would look wrong on page 1.
        assertThat(titles(list(0, 2, ShConsoleListingSort.DATE, null)))
                .containsExactly("Echo", "Delta");
        assertThat(titles(list(1, 2, ShConsoleListingSort.DATE, null)))
                .containsExactly("Charlie", "Bravo");
    }

    @Test
    @DisplayName("the filter is the server's, and it narrows the count as well as the rows")
    void filtersOnTheServer() {
        ShConsoleListing listing = list(0, 50, ShConsoleListingSort.TITLE, "ra");

        assertThat(titles(listing)).containsExactly("Bravo");
        // The count has to follow the filter, or "showing 1 of 5" is a lie the browser repeats.
        assertThat(listing.totalPosts()).isEqualTo(1);
        // ...and it is case-insensitive, matching what the browser's own box did.
        assertThat(titles(list(0, 50, ShConsoleListingSort.TITLE, "ALPHA")))
                .containsExactly("Alpha");
    }

    @Test
    @DisplayName("SH240: the filter reaches content, not just titles")
    void filtersOnContentToo() {
        // The box used to match titles only, which was faithful to the browser's local filter and
        // made the console quietly weaker than the delivery API: a curator who remembered a phrase
        // from a page's body was told there was nothing there. It is the CDA's own predicate now.
        ShConsoleListing listing = list(0, 50, ShConsoleListingSort.TITLE, "quokka");

        assertThat(titles(listing)).containsExactly("Delta");
        assertThat(listing.totalPosts()).isEqualTo(1);
    }

    @Test
    @DisplayName("the filter reaches folders too")
    void filtersFoldersAsWell() {
        assertThat(list(0, 50, ShConsoleListingSort.TITLE, "subsec").folders())
                .singleElement()
                .satisfies(row -> assertThat(row.name()).isEqualTo("Subsection"));
        assertThat(list(0, 50, ShConsoleListingSort.TITLE, "alpha").folders()).isEmpty();
    }

    @Test
    @DisplayName("child folders come back whole on every page")
    void foldersAreNotPaged() {
        // Page 3 of a 2-per-page listing holds no posts at all and still knows the structure —
        // which is what the move/copy picker walks.
        assertThat(list(2, 2, ShConsoleListingSort.TITLE, null).folders()).hasSize(1);
    }

    @Test
    @DisplayName("a site lists its root folders and no posts, with an honest zero")
    void siteListingIsStructureOnly() {
        ShConsoleListing listing = navigation.list(siteId, 0, 50, ShConsoleListingSort.TITLE, null)
                .orElseThrow();

        assertThat(listing.folders()).isNotEmpty();
        assertThat(listing.posts()).isEmpty();
        assertThat(listing.totalPosts()).isZero();
    }

    @Test
    @DisplayName("an unknown sort teaches instead of falling back")
    void unknownSortIsATeaching400() {
        assertThatThrownBy(() -> ShConsoleListingSort.of("name"))
                .isInstanceOf(ShUnknownListingSortException.class)
                .satisfies(thrown -> {
                    var problem = ((ShUnknownListingSortException) thrown).toProblemDetail();
                    assertThat(problem.getStatus()).isEqualTo(400);
                    assertThat(problem.getProperties()).containsKey("allowed");
                    assertThat(problem.getProperties()).containsKey("fix");
                });
        // Absent means the default, which is the one case that is not an error.
        assertThat(ShConsoleListingSort.of(null)).isEqualTo(ShConsoleListingSort.TITLE);
        assertThat(ShConsoleListingSort.of(" ")).isEqualTo(ShConsoleListingSort.TITLE);
        assertThat(ShConsoleListingSort.of("date")).isEqualTo(ShConsoleListingSort.DATE);
    }

    @Test
    @DisplayName("the unpaged overload still answers whole — nothing that called it had to change")
    void theWholeFolderOverloadIsStillThere() {
        assertThat(titles(navigation.list(folderId).orElseThrow())).hasSize(5);
    }

    private ShConsoleListing list(int page, int size, ShConsoleListingSort sort, String query) {
        return navigation.list(folderId, page, size, sort, query).orElseThrow();
    }

    private static List<String> titles(ShConsoleListing listing) {
        return listing.posts().stream().map(ShConsolePostRow::title).toList();
    }

    private static ShPostUnified post(String id, ShPostState state, ShSite site, ShFolder folder,
                                      ShPostType postType, String title, Instant date) {
        ShPostUnified post = new ShPostUnified();
        post.setId(id);
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(postType);
        post.setTitle(title);
        post.setOwner("admin");
        post.setDate(date);
        // A body nothing else matches, so SH240's content filter is provable: "quokka" appears
        // only in Delta's data, never in a title.
        post.setData("Delta".equals(title) ? "{\"body\":\"a quokka appears\"}" : "{}");
        return post;
    }
}
