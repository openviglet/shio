/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH26 — cross-DB schema-drift guard, PostgreSQL dimension. Mirror of
 * {@link ShMariaDbSchemaIT}: boots the <strong>full Spring context</strong>
 * against a real PostgreSQL engine with {@code spring.liquibase.enabled=true} +
 * {@code ddl-auto=none}, proving the full {@code db.changelog-master.yaml}
 * applies, every JPA entity maps onto it (the {@code ShOnStartup} seed inserts
 * run real DML), and {@link ShTenantBackfillRunner#TENANT_TABLES} resolves.
 *
 * <p>PostgreSQL is the strict engine: it folds <em>unquoted</em> identifiers to
 * lower-case. Shio's schema is PascalCase ({@code ShPostType}) with
 * {@code globally_quoted_identifiers=true}, so Hibernate references those names
 * <em>quoted</em> (case-preserved). For the two to match on PostgreSQL the
 * changelogs set {@code objectQuotingStrategy: QUOTE_ALL_OBJECTS} so Liquibase
 * also preserves case — see {@code db/changelog/*.yaml}. H2 (case-insensitive
 * identifiers) and MariaDB (case-insensitive columns) tolerate either, so the
 * quoting strategy is transparent there.
 *
 * <p>Skipped automatically when Docker isn't available.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ShPostgresSchemaIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("shio_it")
            .withUsername("shio")
            .withPassword("shio");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "public");
    }

    @Autowired
    private ApplicationContext context;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void contextBootsWithFullChangelogAppliedOnPostgres() {
        assertThat(context)
                .as("Spring context should boot on PostgreSQL with the full changelog applied")
                .isNotNull();

        Number applied = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM databasechangelog")
                .getSingleResult();
        assertThat(applied.intValue())
                .as("Liquibase should have recorded applied changesets on PostgreSQL")
                .isPositive();
    }

    @Test
    void everyTenantBackfillTableResolvesByItsRealName() {
        for (String table : ShTenantBackfillRunner.TENANT_TABLES) {
            Number hits = (Number) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE UPPER(TABLE_NAME) = UPPER(?1) "
                            + "AND UPPER(COLUMN_NAME) = 'TENANT_ID'")
                    .setParameter(1, table)
                    .getSingleResult();
            assertThat(hits.intValue())
                    .as("ShTenantBackfillRunner.TENANT_TABLES references '%s', which must exist "
                            + "with a TENANT_ID column on PostgreSQL", table)
                    .isPositive();
        }
    }

    @Test
    void incrementalChangelogColumnsLanded() {
        assertColumnExists("site_webhook", "target_url");
        assertColumnExists("api_token", "expires_at");
        assertColumnExists("api_token", "ephemeral");
        assertColumnExists("sh_post_unified", "publish_scheduled_date");
        assertColumnExists("sh_post_unified", "unpublish_scheduled_date");
    }

    private void assertColumnExists(String table, String column) {
        Number hits = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE UPPER(TABLE_NAME) = UPPER(?1) "
                        + "AND UPPER(COLUMN_NAME) = UPPER(?2)")
                .setParameter(1, table)
                .setParameter(2, column)
                .getSingleResult();
        assertThat(hits.intValue())
                .as("Column %s.%s should exist on PostgreSQL after the full changelog applies",
                        table, column)
                .isPositive();
    }
}
