/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.ShH2ResultCache;
import com.viglet.shio.api.object.ShObjectNavigationService;
import com.viglet.shio.api.object.dto.ShConsoleListing;
import com.viglet.shio.api.query.ShContentQuery;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1025 — what one page of the content browser costs, and what the folder half of it costs.
 *
 * <h2>What was argued rather than known</h2>
 *
 * <p>SH165 paged the posts and left the folders whole, and wrote down a reason: subfolders are
 * structure, the move/copy picker walks them and the browser sorts them locally, and the set is
 * "bounded by how people organise a site rather than by how much they publish". That is a
 * plausible argument and it had never been measured — the number it depends on is how much a
 * folder costs against how much a post costs, and nobody had taken it. A cost that is argued
 * rather than measured fails quietly, in somebody else's instance.</p>
 *
 * <h2>What it measures</h2>
 *
 * <p>Three numbers per listing, and only two of them are asserted. <b>Rows fetched</b> is
 * Hibernate's entity-load count — SH264's unit, deterministic, and the one that scales.
 * <b>Response bytes</b> is the serialised envelope, which is what actually crosses the wire and
 * what the browser then turns into DOM. <b>Milliseconds</b> is printed and never asserted: a
 * timing ceiling on a shared machine is a flake, and a flake is worse than a break (SH250).</p>
 *
 * <p>The pair is the deliverable: the same folder, listed the way it was listed before SH1025 and
 * the way it is listed when a caller asks for a page of folders. Both paths still exist — folder
 * paging is opt-in, because the picker's whole-set read is correct and is not what this is about —
 * so this is a comparison that stays runnable rather than a number that existed for one commit.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-console-listing-cost;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShConsoleListingBenchmarkTest {

    /**
     * The fixture, and why it is this size.
     *
     * <p>Not the ten thousand posts the design named: the finding is about the <b>folder</b> half,
     * which is exactly linear in {@link #FOLDERS} and is already unmistakable at this size, and a
     * fixture that costs the suite a minute to insert buys a bigger number and no more knowledge.
     * The post half is held at one page throughout, which is what SH165 already made true.</p>
     */
    private static final int FOLDERS = 400;
    private static final int POSTS = 600;
    private static final int PAGE = 50;

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ShObjectNavigationService shObjectNavigationService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String sectionId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("listing-" + UUID.randomUUID());
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

            ShFolder section = new ShFolder();
            section.setName("section");
            section.setRootFolder((byte) 0);
            section.setParentFolder(root);
            section.setDate(EPOCH);
            section.setOwner("admin");
            entityManager.persist(section);
            sectionId = section.getId();

            for (int index = 0; index < FOLDERS; index++) {
                ShFolder child = new ShFolder();
                child.setName("child-" + String.format("%04d", index));
                child.setRootFolder((byte) 0);
                child.setParentFolder(section);
                child.setPosition(index);
                child.setDate(EPOCH);
                child.setOwner("admin");
                entityManager.persist(child);
            }

            ShPostType type = new ShPostType();
            type.setName("ListingCostArticle");
            type.setTitle("Listing Cost Article");
            type.setDescription("SH1025 benchmark fixture");
            type.setDate(EPOCH);
            entityManager.persist(type);

            List<ShPostUnified> rows = new ArrayList<>();
            for (int index = 0; index < POSTS; index++) {
                ShPostUnified post = new ShPostUnified();
                post.setState(ShPostState.DRAFT);
                post.setShSite(site);
                post.setShFolder(section);
                post.setShPostType(type);
                post.setTitle("Post " + String.format("%04d", index));
                post.setFurl("post-" + index);
                post.setData("{}");
                post.setDate(EPOCH);
                post.setModifiedDate(EPOCH);
                post.setOwner("admin");
                rows.add(post);
            }
            shPostUnifiedRepository.saveAll(rows);
        });
    }

    @Test
    @DisplayName("SH1025: a page of the browser reads one page of folders, not all of them")
    void aPageOfFoldersIsAPage() {
        Cost whole = costOf(ShContentQuery.ALL.withPaging(0, PAGE));
        Cost paged = costOf(ShContentQuery.ALL.withPaging(0, PAGE).withFolderPaging(0, PAGE));

        System.out.printf("""
                SH1025 console listing — %d folders, %d posts, page size %d
                  folders whole : %d row(s) fetched, %d byte(s), %d ms
                  folders paged : %d row(s) fetched, %d byte(s), %d ms%n""",
                FOLDERS, POSTS, PAGE,
                whole.rows, whole.bytes, whole.millis,
                paged.rows, paged.bytes, paged.millis);

        // The finding, as an assertion. SH165's argument was that a folder's subfolders are
        // "bounded by how people organise a site" — true of most folders and not of the one that
        // is a problem, and the cost of being wrong is the whole set on EVERY page of posts.
        assertThat(whole.rows)
                .as("the whole-set path reads every child folder, which is what it promises")
                .isGreaterThanOrEqualTo(FOLDERS);
        assertThat(paged.rows)
                .as("SH1025: a page of folders must read a page of folders")
                .isLessThan(whole.rows / 2);
        assertThat(paged.bytes)
                .as("SH1025: and the envelope is what the browser turns into DOM, so the bytes "
                        + "are the second half of the same finding")
                .isLessThan(whole.bytes / 2);
    }

    @Test
    @DisplayName("SH1025: the whole-set path still answers whole, because the picker walks it")
    void theWholeSetPathIsUnchanged() {
        ShConsoleListing listing = listing(ShContentQuery.ALL.withPaging(0, PAGE));

        assertThat(listing.folders())
                .as("SH1025: folder paging is opt-in. The move/copy picker asks for `size=1` and "
                        + "reads the folders off the answer (SH165), and a listing that started "
                        + "paging them by default would silently hand it one page of a site.")
                .hasSize(FOLDERS);
        assertThat(listing.totalFolders())
                .as("SH1025: and it counts them either way, so a caller can tell a complete "
                        + "answer from a page without counting the array")
                .isEqualTo(FOLDERS);
        assertThat(listing.posts()).hasSize(PAGE);
        assertThat(listing.totalPosts()).isEqualTo(POSTS);
    }

    @Test
    @DisplayName("SH1025: paging the folders does not change what the post half costs")
    void thePostHalfIsUnaffected() {
        ShConsoleListing paged = listing(
                ShContentQuery.ALL.withPaging(0, PAGE).withFolderPaging(0, PAGE));

        assertThat(paged.posts()).hasSize(PAGE);
        assertThat(paged.totalPosts()).isEqualTo(POSTS);
        assertThat(paged.folders()).hasSize(PAGE);
        assertThat(paged.totalFolders()).isEqualTo(FOLDERS);
    }

    private record Cost(long rows, int bytes, long millis) {
    }

    private ShConsoleListing listing(ShContentQuery query) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> shObjectNavigationService.list(sectionId, query).orElseThrow());
    }

    private Cost costOf(ShContentQuery query) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.clear();
            long before = statistics.getEntityLoadCount();
            ShConsoleListing[] listed = new ShConsoleListing[1];
            // SH1327 — the second listing repeats the first one's post read, which H2 would recall.
            long elapsed = ShH2ResultCache.millis(dataSource, () -> listed[0] = answer(() ->
                    shObjectNavigationService.list(sectionId, query).orElseThrow()));
            ShConsoleListing listing = listed[0];
            return new Cost(statistics.getEntityLoadCount() - before,
                    objectMapper.writeValueAsString(listing).length(), elapsed);
        });
    }

    private static ShConsoleListing answer(Supplier<ShConsoleListing> work) {
        return work.get();
    }
}
