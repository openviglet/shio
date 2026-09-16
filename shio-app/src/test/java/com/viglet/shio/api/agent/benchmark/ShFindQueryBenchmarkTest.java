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

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentFound;
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

/**
 * SH1022 — what a narrowed {@code find} costs, in the only unit that scales.
 *
 * <h2>Why this lands before the predicate moves</h2>
 *
 * <p>The rule this project keeps breaking and then re-learning: a cost that is <em>argued</em>
 * rather than measured fails quietly, in somebody else's environment. SH375 found the same defect
 * in the media listing and only found it because it counted rows instead of reasoning about the
 * code — {@code folderId} narrowed the payload and multiplied the server's work, which is the
 * opposite of what everyone believed. So the benchmark states what the in-memory path costs today,
 * and whatever it says is the argument for the predicate rather than the other way round.</p>
 *
 * <h2>What it found</h2>
 *
 * <p>{@code /agent/find} pushes exactly one filter into the database — {@code type}, and only when
 * {@code q} is absent. {@code folder}, {@code state} and the release membership are applied in
 * memory after every draft <b>and</b> published row of the whole site has been loaded. So asking a
 * ten-thousand-post site for one folder's twenty posts reads twenty thousand rows, and asking it
 * for the published half reads both halves. That is the shape SH375 called "narrowing the payload
 * and multiplying the work", and it is why a client-side filter over a paged listing filters one
 * page: the page is drawn from rows the predicate never saw.</p>
 *
 * <p><b>The unfiltered number is the old cost of every one of these.</b> That is what makes this a
 * measurement rather than a recollection: the query {@code find} used to send took a site and a
 * post-type and nothing else, so a {@code folder=} listing, a {@code state=} listing and — once
 * they existed — a {@code locale=} or {@code author=} one all fetched exactly what the whole-site
 * read on the left of each line fetches. Each test prints both, so the ratio is on screen rather
 * than in this paragraph.</p>
 *
 * <p><b>Rows fetched, not milliseconds</b> — SH264's rule. A timing ceiling on a shared machine is
 * a flake, and a flake is worse than a break (SH250). Hibernate's entity-load count is
 * deterministic and is exactly the quantity in question.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-find-query-cost;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShFindQueryBenchmarkTest {

    /** Logical posts in the site, how many folders they sit in, and how many types they carry. */
    private static final int POSTS = 60;
    private static final int FOLDERS = 6;

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant RECENT = Instant.parse("2026-06-01T00:00:00Z");

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String typeName;
    private final List<String> folderNames = new ArrayList<>();

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("find-cost-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);
            siteName = site.getName();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);

            List<ShFolder> folders = new ArrayList<>();
            for (int index = 0; index < FOLDERS; index++) {
                ShFolder folder = new ShFolder();
                folder.setName("section" + index);
                folder.setRootFolder((byte) 0);
                folder.setParentFolder(root);
                folder.setDate(EPOCH);
                folder.setOwner("admin");
                entityManager.persist(folder);
                folders.add(folder);
                folderNames.add(folder.getName());
            }

            ShPostType article = new ShPostType();
            article.setName("FindCostArticle");
            article.setTitle("Find Cost Article");
            article.setDescription("SH1022 benchmark fixture");
            article.setDate(EPOCH);
            entityManager.persist(article);
            typeName = article.getName();

            ShPostType note = new ShPostType();
            note.setName("FindCostNote");
            note.setTitle("Find Cost Note");
            note.setDescription("SH1022 benchmark fixture");
            note.setDate(EPOCH);
            entityManager.persist(note);

            for (int index = 0; index < POSTS; index++) {
                // Both rows of every post — what BOTH_STATES loads, and what makes the in-memory
                // `state` filter cost double what the answer is worth. They share one id, because
                // publishing keeps the draft's (SH321): two ids on one furl is the duplicate-URL
                // defect, not a published page, and ShFixtureShapeGuard says so.
                String id = null;
                for (ShPostState state : List.of(ShPostState.DRAFT, ShPostState.PUBLISHED)) {
                    ShPostUnified post = new ShPostUnified();
                    if (id != null) {
                        post.setId(id);
                    }
                    post.setState(state);
                    post.setShSite(site);
                    post.setShFolder(folders.get(index % FOLDERS));
                    post.setShPostType(index % 2 == 0 ? article : note);
                    post.setTitle("Post " + index);
                    post.setFurl("post-" + index);
                    post.setData("{}");
                    post.setDate(EPOCH);
                    post.setModifiedDate(index % 3 == 0 ? RECENT : EPOCH);
                    post.setOwner(index % 4 == 0 ? "ana" : "bruno");
                    post.setModifier(index % 5 == 0 ? "ana" : "bruno");
                    post.setLocale(index % 6 == 0 ? "pt_BR" : "en");
                    id = shPostUnifiedRepository.save(post).getId();
                }
            }
        });
    }

    @Test
    @DisplayName("SH1022: a folder listing reads that folder's rows, not the whole site's")
    void aFolderListingIsBoundedByTheFolder() {
        long wholeSite = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName)));
        long oneFolder = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName)
                .withFolder("/" + folderNames.get(0))));

        System.out.printf(
                "SH1022 find: whole site %d row(s) fetched, one folder %d — %d posts x 2 states in %d folders%n",
                wholeSite, oneFolder, POSTS, FOLDERS);

        assertThat(wholeSite).isGreaterThanOrEqualTo(POSTS);
        // The finding, as an assertion. Before the predicate moved these two numbers were the same
        // one: `folderScope` resolved ids and then filtered a list that already held every row of
        // the site. One folder of a ten-thousand-post site therefore read twenty thousand rows to
        // answer with twenty.
        assertThat(oneFolder)
                .as("a folder listing must not read the whole site")
                .isLessThan(wholeSite / 2);
    }

    @Test
    @DisplayName("SH1022: a published-only listing does not read the draft half as well")
    void aStateListingIsBoundedByTheState() {
        long both = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName)));
        long published = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName)
                .withState("published")));

        System.out.printf("SH1022 find: no state %d row(s) fetched, state=published %d%n",
                both, published);

        // Before: the same number twice. `state` narrowed a list that BOTH_STATES had already
        // filled, so every published-only listing paid for the draft of every post it left out.
        assertThat(published)
                .as("asking for one state must not fetch the other")
                .isLessThan(both);
    }

    /**
     * The predicates the vocabulary adds, each measured against the unnarrowed read.
     *
     * <p>These four have no in-memory implementation to be slower than — before SH1022 no surface
     * could ask the question at all. What the number establishes is that they are predicates and
     * not a second pass: asking for one author's posts reads one author's rows.</p>
     */
    @Test
    @DisplayName("SH1022: locale, author, modifier and a date range are predicates, not passes")
    void theNewFiltersNarrowTheRead() {
        long wholeSite = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName)));
        long byAuthor = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName).withAuthor("ana")));
        long byLocale = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName)
                .withLocale("pt_BR")));
        long byModified = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName)
                .withModifiedSince(RECENT.toString())));
        // The control: `type` was the one filter already in the database, so it is what a
        // narrowed read was supposed to look like all along.
        long byType = rowsFetchedBy(() -> find(ShContentQuery.ofSite(siteName).withType(typeName)));

        System.out.printf(
                "SH1022 find: whole site %d row(s), type %d, author=ana %d, locale=pt_BR %d, "
                        + "modifiedSince %d%n",
                wholeSite, byType, byAuthor, byLocale, byModified);

        assertThat(byType).as("the filter that was already a predicate").isLessThan(wholeSite);
        assertThat(byAuthor).as("one author's posts").isLessThan(wholeSite);
        assertThat(byLocale).as("one locale's posts").isLessThan(wholeSite);
        assertThat(byModified).as("one window's posts").isLessThan(wholeSite);
    }

    private ShAgentFound find(ShContentQuery query) {
        return shAgentReadService.find(query, 500, false, null, false, null, null);
    }

    /** Entities Hibernate loaded while running {@code work} — deterministic, unlike a clock. */
    private long rowsFetchedBy(Supplier<ShAgentFound> work) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return new TransactionTemplate(transactionManager).execute(status -> {
            entityManager.clear();
            long before = statistics.getEntityLoadCount();
            work.get();
            return statistics.getEntityLoadCount() - before;
        });
    }
}
