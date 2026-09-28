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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SH1033 on MariaDB — the third supported engine, and the one with no way out.
 *
 * <h2>Why there is no companion test here</h2>
 *
 * <p>{@link ShPostgresAssetUsageScanIT} measures a second plan, because PostgreSQL has a
 * {@code pg_trgm} index that serves a leading wildcard. MariaDB has no equivalent, and the
 * absence is worth stating rather than leaving as a test nobody wrote: a B-tree cannot order
 * substrings, and {@code FULLTEXT} — the only other index over text — matches <em>words</em>, so
 * a UUID broken on its hyphens into five tokens is neither the value stored nor a term that
 * identifies one asset. So on this engine the choice §LXX.6 faces is the scan or a
 * reverse-reference table, with nothing in between.</p>
 *
 * <p>{@code EXPLAIN} here reports {@code type = ALL}, which is MariaDB's spelling of the same
 * fact PostgreSQL calls {@code Seq Scan} and H2 reports as a {@code scanCount} equal to the
 * table.</p>
 *
 * <p>Skipped automatically when Docker isn't available.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(Lifecycle.PER_CLASS)
class ShMariaDbAssetUsageScanIT extends ShAssetUsageScanMeasurement {

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4")
            .withDatabaseName("shio_usage")
            .withUsername("shio")
            .withPassword("shio");

    /**
     * SH416 — started here, not left to {@code @Container}; the reasoning is on
     * {@code ShPostgresSchemaIT}, and {@code ShTestcontainersEagerStartLintTest} keeps it honest.
     */
    static {
        MARIADB.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MariaDBDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", MARIADB::getDatabaseName);
    }

    @Override
    protected String explainPrefix() {
        return "explain ";
    }

    /**
     * {@code type = ALL} and {@code key = null}: it read every row and used no index to do it.
     *
     * <p>Matched with the separators the base class writes between columns, so a row whose
     * {@code Extra} happens to contain the word cannot be read as the access type. Measured:
     * {@code 1 | SIMPLE | p | ALL | PRIMARY | null | null | null | 5001 | Using where}.</p>
     *
     * <p>{@code possible_keys} says {@code PRIMARY} and that is not a counter-example — it is the
     * key the optimiser considered for {@code id <> ?}, and it declined it. Nothing was ever
     * available for the substring, which is what {@code key = null} beside {@code rows = 5001}
     * states. An earlier draft asserted "no key is even considered" and passed for the wrong
     * reason: the row carries three other nulls.</p>
     */
    @Override
    protected boolean dataIsFilteredNotIndexed(String plan) {
        return plan.contains("| ALL |") && plan.contains("| ALL | PRIMARY | null |");
    }

    @Override
    protected String engine() {
        return "MariaDB 11.4";
    }

    /**
     * The answer is right here, which is the comparison {@link ShPostgresAssetUsageScanIT} cannot
     * make: on this engine the query is expensive, and on that one it is empty (SH1099).
     */
    @Test
    @DisplayName("SH1033: the scan is expensive on MariaDB, and it is correct")
    void theScanFindsEveryReferrer() {
        assertThat(usage())
                .as("""
                        every referrer and nothing else, at the cost SH1033 measured. The same \
                        assertion on PostgreSQL expects NONE, and that difference is SH1099.""")
                .hasSize(REFERRERS);
    }
}
