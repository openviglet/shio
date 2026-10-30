/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

/**
 * SH1327 — makes H2 forget the results it would hand back to a repeated read, so a timed run is the
 * read's cost and not a cache hit.
 *
 * <h2>What it hides</h2>
 *
 * <p>H2 keeps the last result of each parsed query and returns it when the identical SQL runs again
 * with the same parameters and nothing it reads has changed. A benchmark that warms a read and then
 * times the same read is timing that cache from the second call on: {@code ShSiteListingCostTest}
 * printed 17 ms for a listing whose newest-change read cost 3.5 s once the cache could not answer,
 * so SH1217's own measurement hid the defect SH1305 fixed. Measured on H2 2.5 with a correlated
 * {@code max()} over 8,000 rows: 3,946 ms, then 0, 0, 0.</p>
 *
 * <h2>Why a schema change, and not switching the cache off</h2>
 *
 * <p>{@code SET OPTIMIZE_REUSE_RESULTS FALSE} was the first version of this class and it measured
 * a query production never runs: the same flag lets H2 evaluate an uncorrelated {@code IN}
 * subquery once rather than per outer row, so SH1305's fixed read went from 32 ms to 15.9 s.
 * {@code QUERY_CACHE_SIZE=0} is a URL setting only. A write invalidates only the tables it touches,
 * so it has to be a write to a table the read looks at that leaves its answer alone, which is what
 * {@code ShSiteListingHistoryCostTest} did first. Creating and dropping a sequence changes the
 * schema instead, which makes H2 recompile every cached statement on every connection: the next
 * run computes its result with subquery reuse intact, and pays one parse, which a first execution
 * pays too. On a database that is not H2 it does nothing, because only H2 has this cache.</p>
 *
 * <p>{@code ShTimedReadLintTest} fails a test that calls {@code System.nanoTime()} without going
 * through this class, unless it is listed there with its reason.</p>
 */
public final class ShH2ResultCache {

    private ShH2ResultCache() {
    }

    /** Invalidates every result H2 holds for {@code dataSource}; call it right before a timed read. */
    public static void forget(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!"H2".equals(connection.getMetaData().getDatabaseProductName())) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SEQUENCE IF NOT EXISTS sh1327_forget");
                statement.execute("DROP SEQUENCE IF EXISTS sh1327_forget");
            }
        } catch (SQLException refused) {
            throw new IllegalStateException("SH1327: H2 refused the schema change that empties its "
                    + "result cache", refused);
        }
    }

    /** Milliseconds {@code work} took, with nothing it reads answered from H2's result cache. */
    public static long millis(DataSource dataSource, Runnable work) {
        forget(dataSource);
        long started = System.nanoTime();
        work.run();
        return (System.nanoTime() - started) / 1_000_000;
    }
}
