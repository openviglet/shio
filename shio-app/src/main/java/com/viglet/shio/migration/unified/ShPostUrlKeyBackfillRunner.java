/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.migration.unified;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Fills SH719's {@code url_key} on rows that predate it, once, at startup.
 *
 * <h2>Why the column ships empty and is filled here</h2>
 *
 * <p>The unique index has to be created over a column that cannot already be violating it, and an
 * instance that has met SH719 <b>is</b> holding two live posts on one address. Backfilling inside
 * the changelog would therefore build an index that cannot be built, and a migration that cannot run
 * is worse than the defect it was meant to close: it takes the instance down instead of one write.
 * So Liquibase adds the column all-NULL — which no unique index objects to, since all three engines
 * treat NULLs as distinct — and the keys are computed here, where a collision can be <em>skipped</em>
 * rather than fatal.</p>
 *
 * <p>A row that loses is left NULL, which is the honest outcome: it was already ambiguous,
 * {@code GET /api/v2/agent/verify} already reports it as {@code duplicate-url} with both addresses,
 * and the fix is the {@code post.move} that report names. What it is not is a reason to leave every
 * <em>other</em> address in the instance unguarded.</p>
 *
 * <h2>One pass, and it is idempotent by construction</h2>
 *
 * <p>The read selects only rows whose key is null, so the second startup finds nothing and the third
 * costs one query. Native SQL for {@code ShTenantBackfillRunner}'s reason — Hibernate's
 * {@code @TenantId} filter would scope this to whichever tenant the startup thread happens to carry,
 * and this has to see every row in the table.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration
public class ShPostUrlKeyBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(ShPostUrlKeyBackfillRunner.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final PlatformTransactionManager transactionManager;

    public ShPostUrlKeyBackfillRunner(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Bean
    ApplicationRunner postUrlKeyBackfill() {
        return args -> backfill();
    }

    /**
     * @return how many rows were given a key — 0 on every startup after the first
     */
    public int backfill() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            return tx.execute(status -> {
                List<Object[]> rows = readUnkeyedRows();
                if (rows.isEmpty()) {
                    return 0;
                }
                Map<String, Object[]> winners = winnersByKey(rows);
                int written = 0;
                for (Map.Entry<String, Object[]> entry : winners.entrySet()) {
                    written += entityManager
                            .createNativeQuery("update sh_post_unified set url_key = :key "
                                    + "where id = :id and state = :state")
                            .setParameter("key", entry.getKey())
                            .setParameter("id", entry.getValue()[0])
                            .setParameter("state", entry.getValue()[1])
                            .executeUpdate();
                }
                int skipped = rows.size() - winners.size();
                if (skipped > 0) {
                    // Named at WARN because it is not housekeeping: each one is a live address two
                    // posts claim, which is SH719 having already happened here.
                    log.warn("SH719: {} post row(s) left unkeyed — their address is already claimed "
                            + "by another live post. GET /api/v2/agent/verify reports these as "
                            + "duplicate-url with both addresses.", skipped);
                }
                if (written > 0) {
                    log.info("SH719: keyed {} post row(s) for the unique-address index", written);
                }
                return written;
            });
        } catch (RuntimeException e) {
            // A column that is not there yet is a partial import or a changelog that has not run,
            // and neither is a reason to refuse to start — the guard is simply off until it has.
            log.debug("Skipping url_key backfill: {}", e.getMessage());
            return 0;
        }
    }

    /** {@code id, state, site_id, locale, furl} for every live, addressed row with no key yet. */
    @SuppressWarnings("unchecked")
    private List<Object[]> readUnkeyedRows() {
        return entityManager
                .createNativeQuery("select id, state, site_id, locale, furl from sh_post_unified "
                        + "where url_key is null and deleted_date is null "
                        + "and furl is not null and furl <> '' and site_id is not null "
                        + "order by id")
                .getResultList();
    }

    /**
     * One row per key — the lowest id wins, which is a rule and not a preference.
     *
     * <p>Ordered by id in SQL and kept first-seen here, so two startups of the same instance key the
     * same row. Leaving it to the database's own order would make which of two duplicate posts owns
     * the address depend on the plan, which is precisely the coin flip SH719 exists to remove.</p>
     */
    private static Map<String, Object[]> winnersByKey(List<Object[]> rows) {
        Map<String, Object[]> winners = new LinkedHashMap<>();
        for (Object[] row : rows) {
            ShPostState state = state(row[1]);
            if (state == null) {
                // A state the enum does not know is not a row to invent a key for: the digest would
                // fold `null` into the subject and two unknown states would collide with each other.
                continue;
            }
            String key = ShPostUnified.urlKeyOf(text(row[2]), state, text(row[3]), text(row[4]), null);
            if (key != null) {
                winners.putIfAbsent(key, new Object[] { row[0], row[1] });
            }
        }
        return winners;
    }

    /** The state column as the enum, whichever way the driver hands a VARCHAR back. */
    private static ShPostState state(Object value) {
        if (value instanceof ShPostState already) {
            return already;
        }
        String name = text(value);
        for (ShPostState candidate : ShPostState.values()) {
            if (candidate.name().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
