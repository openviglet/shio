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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH26 — cross-DB schema-drift guard, H2 dimension. Unlike {@link ShMariaDbSchemaIT}
 * / {@link ShPostgresSchemaIT} this needs no Docker, so it runs on every
 * {@code mvn verify}: it boots the <strong>full Spring context</strong> on a
 * fresh in-memory H2 with the production-like datasource params
 * ({@code DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true}),
 * {@code spring.liquibase.enabled=true} and {@code ddl-auto=none}.
 *
 * <p>The rest of the suite skips Liquibase (it builds the schema from entity
 * metadata via {@code create-drop}), so nothing else proves the
 * {@code db.changelog-master.yaml} set actually applies on H2 and that every
 * entity maps onto the Liquibase-built schema — this closes that gap and guards
 * the {@code QUOTE_ALL_OBJECTS} strategy + the SH26 backfill table names on the
 * engine Shio actually ships on by default.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShH2SchemaIT {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        // Fresh in-memory H2, isolated from the dev file DB, but with the same
        // load-bearing URL params and Liquibase wiring as production.
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:shio-sh26-it;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.default-schema", () -> "PUBLIC");
    }

    @Autowired
    private ApplicationContext context;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void contextBootsWithFullChangelogAppliedOnH2() {
        assertThat(context)
                .as("Spring context should boot on H2 with the full changelog applied")
                .isNotNull();

        Number applied = (Number) entityManager
                .createNativeQuery("SELECT COUNT(*) FROM DATABASECHANGELOG")
                .getSingleResult();
        assertThat(applied.intValue())
                .as("Liquibase should have recorded applied changesets on H2")
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
                            + "with a TENANT_ID column on H2", table)
                    .isPositive();
        }
    }

    @Test
    void incrementalChangelogColumnsLanded() {
        assertColumnExists("site_webhook", "target_url");
        assertColumnExists("api_token", "expires_at");
        assertColumnExists("api_token", "ephemeral");
        assertColumnExists("api_token", "may_publish");
        assertColumnExists("sh_post_unified", "publish_scheduled_date");
        assertColumnExists("sh_post_unified", "unpublish_scheduled_date");
    }

    /**
     * SH188 — {@code may_publish} arrived (v2026.3.11) with no default and no backfill,
     * against an entity that mapped it as a primitive {@code boolean}; every install
     * holding a token from before then failed on {@code GET /api/v2/api-token}. The
     * entity now reads NULL as not-granted, and v2026.3.14 is the other half: the
     * default {@code ephemeral} has carried since SH13, so a row can no longer be
     * written without one. Asserting the default is how the migration half stays
     * proven — the entity fix alone would make this test pass at runtime and leave the
     * column able to store NULL forever.
     */
    @Test
    void mayPublishCarriesADefaultLikeEveryOtherBooleanFlag() {
        assertThat(columnDefault("api_token", "may_publish"))
                .as("api_token.may_publish must default to false on H2 after v2026.3.14")
                .isNotNull()
                .satisfies(value -> assertThat(value.toUpperCase()).contains("FALSE"));
        assertThat(columnDefault("api_token", "ephemeral"))
                .as("the SH13 precedent this follows")
                .isNotNull();
    }

    private String columnDefault(String table, String column) {
        return (String) entityManager
                .createNativeQuery("SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE UPPER(TABLE_NAME) = UPPER(?1) "
                        + "AND UPPER(COLUMN_NAME) = UPPER(?2)")
                .setParameter(1, table)
                .setParameter(2, column)
                .getSingleResult();
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
                .as("Column %s.%s should exist on H2 after the full changelog applies",
                        table, column)
                .isPositive();
    }
}
