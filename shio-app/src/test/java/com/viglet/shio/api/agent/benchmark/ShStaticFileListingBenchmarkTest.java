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
import java.util.List;
import java.util.UUID;

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

import com.viglet.shio.api.staticfile.ShStaticFileItem;
import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * SH375 — what a media listing costs, and what a {@code folderId} narrows.
 *
 * <h2>Why this exists</h2>
 *
 * <p>SH213 made {@code push --content} ask for the folders its {@code File} fields name instead of
 * a whole site, and measured the win in <b>rows returned</b>: 200 down to 10 for one reference.
 * What it did not measure is the constant it introduced — a ceiling of eight per-folder listings,
 * past which it falls back to one whole-site read — and the argument for eight rested on an
 * assumption nobody checked: that a {@code folderId} narrows what the <em>server</em> does.</p>
 *
 * <h2>What it found</h2>
 *
 * <p>It did not. {@code listFiles} loaded every published {@code File} post in the tenant and
 * applied {@code folderId} <b>in memory</b>, stat-ing each file on disk on the way through to fill
 * in its size and content type. So the narrowing saved payload and multiplied server work by the
 * number of folders: three folders meant reading the whole library three times to return thirty
 * rows. A cost that is argued rather than measured fails quietly and in somebody else's
 * environment, and this one was arguing in the wrong direction.</p>
 *
 * <p>So the threshold was never the question. The filter was, and it is a predicate in the query
 * now. What is asserted here is the property that makes SH213's shape correct: <b>a folder listing
 * fetches that folder's rows and no others</b>, so F listings cost F small reads rather than F
 * whole-library reads.</p>
 *
 * <p><b>Rows fetched, not milliseconds</b> — SH264's rule. A timing ceiling on a shared machine is
 * a flake, and a flake is worse than a break (SH250). Hibernate's entity-load count is
 * deterministic and is exactly the quantity in question.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-listing-cost;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShStaticFileListingBenchmarkTest {

    /** Files in the library, and how many folders they are spread over. */
    private static final int LIBRARY = 60;
    private static final int FOLDERS = 6;

    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private final List<String> folderIds = new java.util.ArrayList<>();

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("listing-cost-" + UUID.randomUUID());
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            for (int index = 0; index < FOLDERS; index++) {
                ShFolder media = new ShFolder();
                media.setName("media" + index);
                media.setRootFolder((byte) 0);
                media.setParentFolder(root);
                media.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                media.setOwner("admin");
                entityManager.persist(media);
                folderIds.add(media.getId());
            }

            ShPostType fileType = shStaticFileService.resolveOrCreateFilePostType();
            for (int index = 0; index < LIBRARY; index++) {
                ShPostUnified post = new ShPostUnified();
                post.setShFolder(entityManager.find(ShFolder.class, folderIds.get(index % FOLDERS)));
                post.setShPostType(fileType);
                post.setState(ShPostState.PUBLISHED);
                post.setTitle("asset" + index + ".png");
                post.setData("{\"FILE\":\"asset" + index + ".png\"}");
                post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
                post.setOwner("admin");
                post.setShSite(null);
                shPostUnifiedRepository.save(post);
            }
        });
    }

    @Test
    @DisplayName("SH375: a folder listing fetches that folder's rows and no others")
    void aFolderListingIsBoundedByTheFolder() {
        long wholeSite = rowsFetchedBy(() -> shStaticFileService.listFiles(null, siteId, null, null));
        long oneFolder = rowsFetchedBy(() -> shStaticFileService.listFiles(folderIds.get(0), null, null, null));

        System.out.printf(
                "SH375 media listing: whole site %d row(s) fetched, one folder %d — library %d in %d folders%n",
                wholeSite, oneFolder, LIBRARY, FOLDERS);

        // Both counts carry a small fixed tail — the post type, the folder, the site — so what is
        // asserted is the shape rather than an exact number a fixture change would move.
        assertThat(wholeSite).isGreaterThanOrEqualTo(LIBRARY);
        // The finding, as an assertion. Measured before the fix: **69 for one folder against 69 for
        // the whole site** — identical, because the predicate ran in memory after every row had
        // been loaded and stat-ed. F folder listings therefore cost F whole-library reads, so
        // SH213's narrowing was making the server do more work while returning fewer rows.
        assertThat(oneFolder)
                .as("a folder listing must not read the whole library")
                .isLessThan(wholeSite / 2);
        assertThat(oneFolder - (LIBRARY / FOLDERS))
                .as("beyond the folder's own rows it reads only a small fixed tail")
                .isLessThanOrEqualTo(8);
    }

    /**
     * What the per-folder shape costs against the whole-site one, in the only unit that scales.
     *
     * <p>This is what the SH213 ceiling should be argued from: with the predicate in the query, six
     * folder listings read the same rows one site listing does, so per-folder never multiplies the
     * work — it only trades one round trip for several. That makes the ceiling a question about
     * <em>latency</em>, which is a client-side cost this cannot measure, rather than about server
     * work, which it can.</p>
     */
    @Test
    @DisplayName("SH375: F folder listings read no more rows than one whole-site listing")
    void perFolderDoesNotMultiplyServerWork() {
        long wholeSite = rowsFetchedBy(() -> shStaticFileService.listFiles(null, siteId, null, null));
        // One session each, because that is what production does: every listing is its own HTTP
        // request. Measuring them inside one transaction would let Hibernate's first-level cache
        // answer the second read for free and hide exactly the multiplication in question.
        long everyFolder = 0;
        for (String folderId : folderIds) {
            everyFolder += rowsFetchedBy(() -> shStaticFileService.listFiles(folderId, null, null, null));
        }

        System.out.printf(
                "SH375 media listing: 1 site read %d row(s), %d folder reads %d row(s)%n",
                wholeSite, FOLDERS, everyFolder);

        // Measured: 84 against 69, so covering the library one folder at a time costs about a
        // fifth more than one whole-site read — the fixed tail, F times. Before the predicate moved
        // into the query it was 6 × 69 = 414, which is the number that made the SH213 ceiling look
        // like a server-work budget when it is really a round-trip one.
        assertThat(everyFolder)
                .as("covering every folder one at a time reads each row once, not F times")
                .isLessThan(wholeSite * 2);
    }

    /** Entities Hibernate loaded while running `work` — deterministic, unlike a clock. */
    private long rowsFetchedBy(java.util.function.Supplier<List<ShStaticFileItem>> work) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.clear();
            long before = statistics.getEntityLoadCount();
            work.get();
            return statistics.getEntityLoadCount() - before;
        });
    }
}
