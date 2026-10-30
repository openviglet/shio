/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH1327 — {@link ShH2ResultCache#forget} really makes H2 compute a repeated read, and leaves the
 * subquery reuse production relies on alone.
 *
 * <p>Counted rather than timed, so the proof is not itself a flake: the queries call a function H2
 * is told is deterministic, which is what lets it reuse a result, and the function counts its calls.
 * A read answered from the cache never calls it.</p>
 */
class ShH2ResultCacheTest {

    /** Public because H2 invokes it by reflection, and this test class is package-private. */
    public static final class Counter {

        static final AtomicInteger CALLS = new AtomicInteger();

        private Counter() {
        }

        public static int tick(int value) {
            CALLS.incrementAndGet();
            return value;
        }
    }

    private static final int ROWS = 20;

    private JdbcDataSource dataSource;

    @BeforeEach
    void database() throws SQLException {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:sh1327-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("create alias tick deterministic for \"" + Counter.class.getName() + ".tick\"");
            statement.execute("create table t(id int primary key)");
            statement.execute("insert into t select x from system_range(1, " + ROWS + ")");
        }
    }

    /** Calls {@code tick} made by one run of {@code sql} on {@code connection}. */
    private static int calls(Connection connection, String sql) throws SQLException {
        int before = Counter.CALLS.get();
        try (PreparedStatement select = connection.prepareStatement(sql);
                ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                assertThat(rows.getInt(1)).isPositive();
            }
        }
        return Counter.CALLS.get() - before;
    }

    @Test
    @DisplayName("SH1327: H2 answers a repeated read from its cache, and forget makes it compute it")
    void forgetMakesTheRepeatARead() throws SQLException {
        String sql = "select tick(id) from t";
        try (Connection connection = dataSource.getConnection()) {
            assertThat(calls(connection, sql)).isEqualTo(ROWS);
            assertThat(calls(connection, sql))
                    .as("SH1327: if this is not 0 H2 stopped reusing results, and the benchmarks' "
                            + "numbers were honest without the helper")
                    .isZero();

            ShH2ResultCache.forget(dataSource);

            assertThat(calls(connection, sql))
                    .as("SH1327: after forget the repeat is computed, which is the cost a timed run "
                            + "has to report; the helper's own connection is not the one reading")
                    .isEqualTo(ROWS);
        }
    }

    @Test
    @DisplayName("SH1327: forget leaves an uncorrelated subquery evaluated once, as production runs it")
    void forgetKeepsSubqueryReuse() throws SQLException {
        // The subquery's tick runs once if H2 reuses its result and once per outer row if it does
        // not: the second is the 15.9 s that switching OPTIMIZE_REUSE_RESULTS off measured for
        // SH1305's read, a cost no production query pays.
        String sql = "select id from t where id in (select tick(id) from t)";
        try (Connection connection = dataSource.getConnection()) {
            ShH2ResultCache.forget(dataSource);
            assertThat(calls(connection, sql))
                    .as("SH1327: the subquery is evaluated once, not once per outer row")
                    .isEqualTo(ROWS);
        }
    }
}
