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
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH26 — cross-DB schema-drift guard, MariaDB dimension.
 *
 * <p>The Liquibase changelogs are kept DB-agnostic by the
 * {@link com.viglet.shio.db.ShLiquibaseChangelogAgnosticTest} <em>lint</em>, but
 * a lint never proves the changelogs actually apply on the engines whose drivers
 * ship — dev and the rest of the test suite only run on H2. This IT boots the
 * <strong>full Spring context</strong> against a real MariaDB engine with
 * {@code spring.liquibase.enabled=true} + {@code ddl-auto=none}, so a green run
 * proves three things at once:
 *
 * <ol>
 *   <li>the full {@code db.changelog-master.yaml} set applies cleanly on MariaDB;</li>
 *   <li>every JPA entity maps onto the resulting schema — the {@code ShOnStartup}
 *       seed inserts run real DML against it as the context comes up;</li>
 *   <li>the {@link ShTenantBackfillRunner#TENANT_TABLES} list resolves to real
 *       tables — the regression that motivated SH26, where a snake_case list
 *       silently matched nothing.</li>
 * </ol>
 *
 * <p>Companion ITs cover the other engines: {@link ShPostgresSchemaIT} (the
 * strict engine — folds unquoted identifiers to lower-case, made to match via
 * the changelogs' {@code QUOTE_ALL_OBJECTS} strategy) and {@link ShH2SchemaIT}
 * (no Docker needed).
 *
 * <p>Skipped automatically when Docker isn't available.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ShMariaDbSchemaIT {

    @Container
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.4")
            .withDatabaseName("shio_it")
            .withUsername("shio")
            .withPassword("shio");

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

    @Autowired
    private ApplicationContext context;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void contextBootsWithFullChangelogAppliedOnMariaDb() {
        assertThat(context)
                .as("Spring context should boot on MariaDB with the full changelog applied")
                .isNotNull();

        // DATABASECHANGELOG only exists / is populated because Liquibase ran the
        // master changelog against MariaDB (not H2, not ddl-auto create-drop).
        Number applied = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")
                .getSingleResult();
        assertThat(applied.intValue())
                .as("Liquibase should have recorded applied changesets on MariaDB")
                .isPositive();
    }

    @Test
    void everyTenantBackfillTableResolvesByItsRealName() {
        // The bug SH26 fixes: a snake_case TENANT_TABLES list matched no real
        // table, so the pre-multi-tenant backfill silently no-op'd. INFORMATION_
        // SCHEMA is queried case-insensitively so what it cannot tolerate is a
        // name that does not exist at all (e.g. sh_post_type_attr).
        for (String table : ShTenantBackfillRunner.TENANT_TABLES) {
            Number hits = (Number) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE UPPER(TABLE_NAME) = UPPER(?1) "
                            + "AND UPPER(COLUMN_NAME) = 'TENANT_ID'")
                    .setParameter(1, table)
                    .getSingleResult();
            assertThat(hits.intValue())
                    .as("ShTenantBackfillRunner.TENANT_TABLES references '%s', which must exist "
                            + "with a TENANT_ID column on MariaDB", table)
                    .isPositive();
        }
    }

    @Test
    void incrementalChangelogColumnsLanded() {
        // SH12 (site_webhook), SH13 (api_token.expires_at/ephemeral), SH15
        // (sh_post_unified.*_scheduled_date) — proves the incremental files in
        // the master set applied on top of the baseline, not just the baseline.
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
                .as("Column %s.%s should exist on MariaDB after the full changelog applies",
                        table, column)
                .isPositive();
    }
}
