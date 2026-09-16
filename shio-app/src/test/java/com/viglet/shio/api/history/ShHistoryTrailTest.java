/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.history.ShHistoryAPI.ShHistoryPage;
import com.viglet.shio.api.history.ShHistoryAPI.ShHistoryResponse;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1005 — one reader over the audit table, for both surfaces.
 *
 * <h2>The defect</h2>
 *
 * <p>Every console read of this trail was <b>page zero</b>, capped at five hundred rows, with no
 * cursor and no filter — over a second set of finders, while {@code /agent/changes} read the same
 * table with a keyset cursor, a site scope, a commit-order ceiling and a measured budget. So the
 * five-hundredth row was the last one this product could show a person, on the one table that only
 * grows, and the weaker of the two readers was the one the curator used.</p>
 *
 * <h2>What is asserted, and why these things</h2>
 *
 * <p>Paging is asserted by <b>tiling</b>: the pages a reader walks, concatenated, must equal the
 * trail exactly — no row twice, none missed. A cursor that repeats a row is visible; one that skips
 * a row at a page boundary is not, and that is the failure a keyset predicate exists to prevent
 * (SH88's tie-break).</p>
 *
 * <p>Narrowing is asserted by <b>page fullness</b>, not by the values that come back. A filter
 * applied after the query returns the right kind of row and the wrong number of them: the page is
 * short while the matching rows sit behind the cursor, and a reader that pages reads a short page as
 * the end of the trail. Checking only that every row matches would pass against exactly the
 * implementation this task removed.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShHistoryTrailTest {

    /** Old enough to sit under the feed's SH147 ceiling however long the suite takes. */
    private static final Instant THEN = Instant.parse("2026-03-01T10:00:00Z");
    private static final String RUN = UUID.randomUUID().toString();
    private static final int SEEDED = 9;

    @Autowired
    private ShHistoryAPI shHistoryAPI;
    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String site;
    private String siteId;
    private final String postId = "sh1005-post-" + UUID.randomUUID().toString().substring(0, 8);
    private final String folderId = "sh1005-folder-" + UUID.randomUUID().toString().substring(0, 8);
    /** Newest first, which is the order both readers hand back. */
    private final List<String> seeded = new ArrayList<>();

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            site = "sh1005-" + UUID.randomUUID().toString().substring(0, 8);
            ShSite row = new ShSite();
            row.setName(site);
            row.setDate(THEN);
            row.setOwner("admin");
            entityManager.persist(row);
            siteId = row.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(row);
            root.setDate(THEN);
            root.setOwner("admin");
            entityManager.persist(root);

            // Nine rows, one second apart, so the order is a fact rather than a tie-break — and
            // three dimensions varied across them, because a filter that narrows on one must not
            // quietly narrow on another.
            record(0, ShContentAction.CREATE, "ana", null, ShContentSubject.POST, postId);
            record(1, ShContentAction.UPDATE, "ana", RUN, ShContentSubject.POST, postId);
            record(2, ShContentAction.PUBLISH, "ana", RUN, ShContentSubject.POST, postId);
            record(3, ShContentAction.UPDATE, "bruno", null, ShContentSubject.POST, postId);
            record(4, ShContentAction.PUBLISH, "bruno", null, ShContentSubject.POST, postId);
            record(5, ShContentAction.UPDATE, "ana", null, ShContentSubject.FOLDER, folderId);
            record(6, ShContentAction.PUBLISH, "ana", null, ShContentSubject.POST, postId);
            record(7, ShContentAction.UPDATE, "bruno", RUN, ShContentSubject.POST, postId);
            record(8, ShContentAction.PUBLISH, "bruno", null, ShContentSubject.POST, postId);
        });
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                shHistoryRepository.findAll().stream()
                        .filter(entry -> siteId.equals(entry.getShSite()))
                        .toList()
                        .forEach(shHistoryRepository::delete));
    }

    private void record(int nth, ShContentAction action, String owner, String session,
                        ShContentSubject subject, String object) {
        seeded.add(0, recordRow(nth, action, owner, session, subject, object));
    }

    /** One row, without joining the seeded trail — what a test that writes a tenth row needs. */
    private String recordRow(int nth, ShContentAction action, String owner, String session,
                             ShContentSubject subject, String object) {
        ShHistory entry = new ShHistory();
        String id = "sh1005-" + nth + "-" + UUID.randomUUID().toString().substring(0, 12);
        entry.setId(id);
        entry.setAction(action.name());
        entry.setSubjectKind(subject.name());
        entry.setShObject(object);
        entry.setShSite(siteId);
        entry.setOwner(owner);
        entry.setAgentSessionId(session);
        entry.setDate(THEN.plusSeconds(nth));
        entry.setDescription(action.name() + " " + nth);
        shHistoryRepository.save(entry);
        return id;
    }

    private ShHistoryPage page(ShHistoryFilter filter, String before, int limit) {
        return shHistoryAPI.recent(limit, before, null, filter.by(), filter.actor(),
                filter.action(), filter.subject(), site);
    }

    /** Every page a reader walks, concatenated — the whole point of a cursor. */
    private List<String> walk(ShHistoryFilter filter, int limit) {
        List<String> seen = new ArrayList<>();
        String before = null;
        ShHistoryPage page;
        do {
            page = page(filter, before, limit);
            page.rows().stream().map(ShHistoryResponse::id).forEach(seen::add);
            before = page.cursor();
        } while (page.more());
        return seen;
    }

    @Test
    @DisplayName("SH1005: the pages a reader walks tile the trail — no row twice, none missed")
    void pagingTilesTheTrail() {
        assertThat(walk(ShHistoryFilter.ALL, 4))
                .as("SH1005: before this, every read was page zero: the five-hundredth row was the "
                        + "last one this product could show, on the one table that only grows")
                .containsExactlyElementsOf(seeded);

        assertThat(walk(ShHistoryFilter.ALL, 1))
                .as("SH1005: a page of one is where a keyset predicate that drops or repeats ties "
                        + "shows itself, and it is what a page boundary is in the general case")
                .containsExactlyElementsOf(seeded);
    }

    @Test
    @DisplayName("SH1005: the last page says so, so a reader at the end is not sent to an empty one")
    void theLastPageSaysSo() {
        ShHistoryPage whole = page(ShHistoryFilter.ALL, null, SEEDED);

        assertThat(whole.rows()).hasSize(SEEDED);
        assertThat(whole.more())
                .as("SH1005: `more` is read from one row beyond the page rather than inferred from "
                        + "a full one — a full last page would otherwise cost a round trip that "
                        + "returns nothing, every time")
                .isFalse();
        assertThat(whole.latest()).isNotNull();
        assertThat(whole.cursor()).isNotEqualTo(whole.latest());
    }

    @Test
    @DisplayName("SH1005: a narrowing is in the query, so a narrowed page is full rather than short")
    void aNarrowedPageIsFull() {
        ShHistoryFilter published = new ShHistoryFilter(null, null, "publish", null, null, null);

        assertThat(page(published, null, 2).rows())
                .as("SH1005: four of these nine rows are a PUBLISH and only one of the newest two "
                        + "is — a filter applied after the query hands back one row here, and a "
                        + "reader that pages reads a short page as the end of the trail")
                .hasSize(2);
        assertThat(walk(published, 2))
                .as("SH1005: and it still tiles: every PUBLISH, once")
                .hasSize(4);
        assertThat(page(published, null, 50).rows())
                .allSatisfy(row -> assertThat(row.action()).isEqualTo("PUBLISH"));
    }

    @Test
    @DisplayName("SH1005: the three columns the row always carried, each now a filter of its own")
    void eachDimensionNarrowsOnItsOwn() {
        assertThat(page(new ShHistoryFilter(null, null, null, "bruno", null, null), null, 50).rows())
                .as("SH1005: one principal's rows — the actor column, asked as a question")
                .hasSize(4)
                .allSatisfy(row -> assertThat(row.owner()).isEqualTo("bruno"));

        assertThat(page(new ShHistoryFilter(null, null, null, null, "folder", null), null, 50).rows())
                .as("SH1005: SH196 recorded the subject on every row and no reader carried it, so "
                        + "the Activity page called a folder rename a post")
                .singleElement()
                .satisfies(row -> assertThat(row.shObject()).isEqualTo(folderId));

        assertThat(page(new ShHistoryFilter("agent", null, null, null, null, null), null, 50).rows())
                .as("SH999's filter, now one of six rather than the only one")
                .hasSize(3)
                .allSatisfy(row -> assertThat(row.agent()).isEqualTo(RUN));
    }

    @Test
    @DisplayName("SH1005: `since` reads the other way — what arrived after the page was drawn")
    void sinceAnswersWhatArrived() {
        ShHistoryPage drawn = page(ShHistoryFilter.ALL, null, SEEDED);
        String latest = drawn.latest();

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        String arrived = transaction.execute(status -> recordRow(SEEDED, ShContentAction.UPDATE,
                "ana", null, ShContentSubject.POST, postId));
        try {
            ShHistoryPage arrivals =
                    shHistoryAPI.recent(50, null, latest, null, null, null, null, site);

            assertThat(arrivals.rows()).map(ShHistoryResponse::id)
                    .as("SH1005: a live screen asks what has been written since it was drawn, and "
                            + "the answer is the same cursor grammar read forward — not a second "
                            + "poll that re-reads the page and diffs it")
                    .containsExactly(arrived);
        } finally {
            // Removed here rather than in tearDown: it is a tenth row in a trail every other test
            // in this class counts, and the order they run in is not something to depend on.
            transaction.executeWithoutResult(status ->
                    shHistoryRepository.findById(arrived).ifPresent(shHistoryRepository::delete));
        }
    }

    @Test
    @DisplayName("SH1005: both surfaces answer from one query set, so they cannot disagree")
    void theTwoReadersAgree() {
        int feed = shAgentChangesService
                .changes(site, null, 50, false, 0, false, ShHistoryFilter.of("agent", null))
                .changes().size();

        assertThat(feed)
                .as("SH1005: the feed's own narrowing still narrows; the point is that it is the "
                        + "same clause the console's page now rides on")
                .isEqualTo(3);
        assertThat(page(new ShHistoryFilter("agent", null, null, null, null, null), null, 50).rows())
                .hasSize(feed);
    }

    @Test
    @DisplayName("SH1005: one repository over the audit table, so a second reader has nowhere to live")
    void oneRepositoryOverTheTable() throws IOException {
        Path repositories = Path.of("src", "main", "java", "com", "viglet", "shio", "persistence",
                "repository", "history");
        try (Stream<Path> files = Files.list(repositories)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .as("SH1005: two repositories over one table is how a second reader gets "
                            + "written without anybody deciding to write one — this table had two, "
                            + "and the weaker reader was the one the curator used")
                    .containsExactly("ShHistoryRepository.java");
        }

        String api = Files.readString(Path.of("src", "main", "java", "com", "viglet", "shio", "api",
                "history", "ShHistoryAPI.java"));
        assertThat(api)
                .as("SH1005: the console's controller reads through the changes service, which is "
                        + "what keeps the scope, the ceiling and the cursor one answer")
                .doesNotContain("Repository");
    }
}
