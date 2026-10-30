/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH1305 — the sites listing's newest-change read, timed against an audit trail of the size a
 * working instance has.
 *
 * <p>{@code ShSiteListingCostTest} counts statements and the count was always three, so it could not
 * see this: one of the three was a correlated {@code max(date)} over the whole {@code ShHistory}
 * table, re-run for every row of it. On the dev store (8,012 rows, 75 sites) it took 2.7-3.2 s warm
 * in H2 and 10-20 s through the API, and the uncorrelated {@code (shSite, date) in (grouped max)}
 * form took 18-52 ms with no index. The one fast call the design could not explain (122 ms) was H2's
 * result cache answering a repeat with nothing written in between. So this seeds that size and asserts a ceiling: milliseconds are
 * normally printed rather than asserted (SH264), but here time is the defect and the statement count
 * cannot carry it. The ceiling sits thirty times above the fixed read and below the old one.</p>
 *
 * <p>It shares the plain context (SH423) and removes every row and site it made.</p>
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteListingHistoryCostTest {

    /** The dev store's shape when SH1305 was measured. */
    private static final int SITES = 75;
    private static final int ROWS_PER_SITE = 107;

    /** Thirty times the fixed read's slowest measurement, and a fraction of the old one's fastest. */
    private static final long CEILING_MS = 1_000;

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private final List<String> made = new ArrayList<>();
    /** Each seeded site's newest row's id, which the read must name. */
    private final Map<String, String> newestOf = new LinkedHashMap<>();

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (int s = 0; s < SITES; s++) {
                ShSite site = new ShSite();
                site.setName("sh1305-" + s + "-" + UUID.randomUUID());
                site.setDate(EPOCH);
                site.setOwner("admin");
                entityManager.persist(site);
                made.add(site.getId());
                for (int r = 0; r < ROWS_PER_SITE; r++) {
                    // Site s's newest row is at s + ROWS_PER_SITE seconds; the sites overlap in time,
                    // so one site's newest timestamp is an older row's timestamp in the next site. A
                    // read matching on the date alone would name both, and only the site check
                    // tells them apart.
                    ShHistory row = history(site.getId(), EPOCH.plusSeconds(s + (long) r));
                    if (r == ROWS_PER_SITE - 1) {
                        newestOf.put(site.getId(), row.getId());
                    }
                }
                entityManager.flush();
                entityManager.clear();
            }
        });
    }

    private ShHistory history(String siteId, Instant at) {
        ShHistory row = new ShHistory();
        row.setShSite(siteId);
        row.setShObject(siteId);
        row.setOwner("admin");
        row.setAction("UPDATE");
        row.setDate(at);
        entityManager.persist(row);
        return row;
    }

    @AfterAll
    void tearDown() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.createQuery("delete from ShHistory h where h.shSite in :ids")
                    .setParameter("ids", made).executeUpdate();
            entityManager.createQuery("delete from ShSite s where s.id in :ids")
                    .setParameter("ids", made).executeUpdate();
        });
    }

    @Test
    @DisplayName("SH1305: each site's newest change, and only its own, is named")
    void eachSiteGetsItsOwnNewestRow() {
        Map<String, String> first = new LinkedHashMap<>();
        for (ShHistory row : shHistoryRepository.findNewestPerSite()) {
            first.putIfAbsent(row.getShSite(), row.getId());
        }
        for (Map.Entry<String, String> seeded : newestOf.entrySet()) {
            assertThat(first)
                    .as("SH1305: a site's last change is its newest row, not another site's at the same instant")
                    .containsEntry(seeded.getKey(), seeded.getValue());
        }
        long rowsForSeeded = shHistoryRepository.findNewestPerSite().stream()
                .filter(row -> newestOf.containsKey(row.getShSite())).count();
        assertThat(rowsForSeeded)
                .as("SH1305: one row per site here, since no two of a site's rows share its newest instant")
                .isEqualTo(SITES);
    }

    @Test
    @DisplayName("SH1305: the newest-change read stays fast over a working instance's audit trail")
    void theNewestChangeReadIsNotQuadratic() {
        shHistoryRepository.findNewestPerSite(); // warm: the first read pays for the plan, not the rows
        long best = Long.MAX_VALUE;
        List<Long> runs = new ArrayList<>();
        for (int attempt = 0; attempt < 3; attempt++) {
            // H2 answers a repeated query from its result cache until the database changes, which
            // is how the old read measured 3 ms here and 122 ms once on the dev store. A listing
            // follows writes, so each run follows one: an older row, which moves no site's newest.
            int nth = attempt;
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    history(made.get(nth), EPOCH.minusSeconds(nth + 1L)));
            long started = System.nanoTime();
            shHistoryRepository.findNewestPerSite();
            long millis = (System.nanoTime() - started) / 1_000_000;
            runs.add(millis);
            best = Math.min(best, millis);
        }
        long rows = shHistoryRepository.count();
        System.out.printf("%nSH1305 — findNewestPerSite over %d history rows: %s ms (best %d)%n",
                rows, runs, best);
        assertThat(best)
                .as("""
                        SH1305: the sites listing waited 10-20 s on this read, a max(date) re-run per \
                        audit row. Over %d rows it took %d ms, ceiling %d.""", rows, best, CEILING_MS)
                .isLessThan(CEILING_MS);
    }
}
