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

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.yaml.snakeyaml.Yaml;

/**
 * SH1033 on PostgreSQL — <b>the engine that answered a different question than the one asked.</b>
 *
 * <h2>What this class found, which is not a cost</h2>
 *
 * <p>The task was to price a scan. On this engine there is nothing to price, because the query
 * returns <b>zero rows</b> whatever the table holds. {@code ShPostUnified.data} is
 * {@code @Lob String}, and pgjdbc writes a {@code @Lob} as a <em>large object</em>: the column
 * stores an OID, the JSON lives in {@code pg_largeobject}, and Hibernate resolves it on read — so
 * the application is correct and every SQL predicate over {@code data} matches nothing. Measured
 * on this fixture: 5,001 rows, 5,001 large objects, every {@code data} value matching
 * {@code ^[0-9]+$}, and the entity read returning the right document.</p>
 *
 * <p>That was <b>SH1099</b>, filed from here, and it was not confined to the asset dialog:
 * {@code FULLTEXT_PREDICATE} matches {@code lower(cast(p.data as string))}, so console and CDA
 * search found nothing in a page's body on PostgreSQL. SH1099 bound the field as a string
 * ({@code LONGVARCHAR}) and migrated the stored objects back into their columns; the column
 * assertions below were flipped in that commit, which is how the loop closed from this end.</p>
 *
 * <p><b>SH1034 took the usage half of it away without meaning to, and that is asserted here too.</b>
 * The reverse index is written from the entity at write time and read by an equality predicate on
 * its own column, so "where is this used" never touches {@code data} and is correct on this engine
 * while every SQL predicate over {@code data} still matches nothing. Worth stating plainly: it is a
 * property of moving the read off the column, not a repair of the column.</p>
 *
 * <h2>The second plan, and why it is measured but not recommended</h2>
 *
 * <p>The claim the other two engines answer is that no index serves a leading wildcard. On
 * PostgreSQL that is false: a {@code pg_trgm} GIN index turns exactly this predicate into a bitmap
 * index scan, so §LXX.6 has a branch here the others do not. It is measured and deliberately not
 * recommended — a GIN index over every post's whole document is a large structure maintained on
 * every write, and choosing it would put the cheapest path on one of three supported engines.
 * That is SH1034's decision; this class exists so it is taken knowing the option is there.</p>
 *
 * <p>Skipped automatically when Docker isn't available.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(Lifecycle.PER_CLASS)
class ShPostgresAssetUsageScanIT extends ShAssetUsageScanMeasurement {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("shio_usage")
            .withUsername("shio")
            .withPassword("shio");

    /**
     * SH416 — started here rather than left to {@code @Container}, because the extension starts it
     * after Spring's first context-load attempt and {@link #datasource} resolves
     * {@code getJdbcUrl()} during that attempt. The reasoning and the three measurements are on
     * {@code ShPostgresSchemaIT}, which failed and was diagnosed first;
     * {@code ShTestcontainersEagerStartLintTest} fails the build for a class written without it.
     */
    static {
        POSTGRES.start();
    }

    @Autowired
    private DataSource dataSource;

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

    @Override
    protected String explainPrefix() {
        return "explain (analyze, timing off) ";
    }

    /**
     * {@code data} appears in a {@code Filter:} line and never in an {@code Index Cond:} one.
     *
     * <p>Not "the plan is a Seq Scan", which is what this asked first and which PostgreSQL
     * falsified for the wrong reason: it reaches for {@code idx_post_unified_type} to satisfy
     * {@code state} and then removes 2,501 rows with the substring. An index scan by node name, a
     * full read of the state partition by behaviour.</p>
     */
    @Override
    protected boolean dataIsFilteredNotIndexed(String plan) {
        boolean filtered = plan.lines()
                .anyMatch(line -> line.contains("Filter:") && line.contains("data "));
        boolean keyed = plan.lines()
                .anyMatch(line -> line.contains("Index Cond:") && line.contains("data "));
        return filtered && !keyed;
    }

    @Override
    protected String engine() {
        return "PostgreSQL 16";
    }

    @Test
    @DisplayName("SH1099: data holds the document itself here, so a predicate over it matches")
    void theDataColumnHoldsTheDocument() {
        long rows = count("select count(*) from sh_post_unified");
        long json = count("select count(*) from sh_post_unified where data like '{%'");
        long oids = count("select count(*) from sh_post_unified where data ~ '^[0-9]+$'");
        long objects = count("select count(*) from pg_largeobject_metadata");
        long body = count("select count(*) from sh_post_unified where lower(data) like '%lorem ipsum%'");

        System.out.printf("""

                SH1099 - what `data` holds on PostgreSQL:
                  %d row(s), %d holding a JSON document, %d holding a bare integer
                  %d large object(s) in pg_largeobject_metadata, %d row(s) matching a body term
                """, rows, json, oids, objects, body);

        // This asserted zero until SH1099 landed, and was written to fail when it did: pgjdbc wrote
        // the `@Lob String` as a large object, so the column held an OID and search's
        // FULLTEXT_PREDICATE found nothing in a page's body on this engine.
        assertThat(json)
                .as("SH1099: every row's `data` is the document, readable by SQL")
                .isEqualTo(rows);
        assertThat(oids).as("SH1099: and no row holds an OID in its place").isZero();
        assertThat(objects).as("SH1099: and writing a post creates no large object to leak").isZero();
        assertThat(body)
                .as("SH1099: a body-text predicate — what search runs — matches the %d posts carrying it", ROWS)
                .isEqualTo(ROWS);

        // And the consequence SH1034 removed before SH1099 landed, kept because it is the boundary
        // the defect crossed: `listUsage` used to read this column through SQL, so on this engine it
        // answered "nothing uses this asset" about an asset three posts used. The reverse index is
        // built from the entity at write time, so the read never depended on `data`.
        assertThat(usage())
                .as("""
                        SH1034 at the boundary SH1099 crosses: the index is written from the entity, \
                        so `listUsage` answers with all %d referrer(s) whatever `data` holds.""",
                        REFERRERS)
                .hasSize(REFERRERS);
    }

    /**
     * SH1099's migration, run against a row written the way every row was before it.
     *
     * <p>The fixture above is written by the fixed mapping, so it holds no large object; a legacy
     * row is seeded by hand (the document moved into {@code pg_largeobject}, its OID left in the
     * column, for {@code data} and {@code summary} alike) and the changeset's own SQL, read from the
     * changelog rather than copied, is run over it. Twice, because a migration that is not idempotent
     * is one that damages a database it already fixed.</p>
     */
    @Test
    @DisplayName("SH1099: the changeset brings a large object back into its column and unlinks it")
    void theChangesetInlinesALegacyLargeObject() throws Exception {
        // Keeps the body term, so the sibling test's count holds whichever of the two runs first.
        String document = "{\"HERO\":\"legacy\",\"BODY\":\"lorem ipsum, written before sh1099\"}";
        String row = "select id from sh_post_unified where title = 'row 7'";
        jdbc("update sh_post_unified set "
                + "data = lo_from_bytea(0, convert_to('" + document + "', 'UTF8'))::text, "
                + "summary = lo_from_bytea(0, convert_to('a legacy summary', 'UTF8'))::text "
                + "where id = (" + row + ")");
        assertThat(count("select count(*) from pg_largeobject_metadata"))
                .as("the seed: one row holding two OIDs, as every row did before SH1099")
                .isEqualTo(2);

        String changeset = changesetSql();
        jdbc(changeset);
        jdbc(changeset);

        assertThat(text("select data from sh_post_unified where id = (" + row + ")"))
                .as("SH1099: the document is back in the column, byte for byte")
                .isEqualTo(document);
        assertThat(text("select summary from sh_post_unified where id = (" + row + ")"))
                .isEqualTo("a legacy summary");
        assertThat(count("select count(*) from pg_largeobject_metadata"))
                .as("SH1099: and each object is unlinked, which deleting its row never did")
                .isZero();
        assertThat(count("select count(*) from sh_post_unified where data like '{%'"))
                .as("SH1099: and no row that already held its document was touched")
                .isEqualTo(count("select count(*) from sh_post_unified"));
    }

    @SuppressWarnings("unchecked")
    private static String changesetSql() throws Exception {
        try (InputStream in = ShPostgresAssetUsageScanIT.class
                .getResourceAsStream("/db/changelog/v2026.3.46.yaml")) {
            Map<String, Object> root = new Yaml().load(in);
            for (Object entry : (List<Object>) root.get("databaseChangeLog")) {
                Map<String, Object> changeSet = (Map<String, Object>) ((Map<String, Object>) entry).get("changeSet");
                if (changeSet != null && "sh1099-inline-large-objects".equals(changeSet.get("id"))) {
                    Map<String, Object> change = (Map<String, Object>) ((List<Object>) changeSet.get("changes")).get(0);
                    return (String) ((Map<String, Object>) change.get("sql")).get("sql");
                }
            }
        }
        throw new IllegalStateException("SH1099: the changeset sh1099-inline-large-objects is not in v2026.3.46.yaml");
    }

    /** Plain JDBC, because Hibernate's native-query parser reads the {@code ::} casts as parameters. */
    private void jdbc(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String text(String sql) {
        return (String) entityManager.createNativeQuery(sql).getSingleResult();
    }

    /**
     * The {@code pg_trgm} branch, measured on a column that actually holds the documents.
     *
     * <p><b>Not on {@code sh_post_unified}, and that is SH1099's doing rather than a shortcut.</b>
     * An index over that column today indexes five thousand OID strings, so a plan taken from it
     * would describe the storage defect and not the option — the first draft did exactly that and
     * came back a sequential scan, which reads as "PostgreSQL cannot do this" and is the opposite
     * of true. The mechanism is what §LXX.6 needs to know about, so it is demonstrated on a table
     * holding the same documents inline, which is what {@code sh_post_unified} will hold once
     * SH1099 lands.</p>
     */
    @Test
    @DisplayName("SH1033: a pg_trgm GIN index does serve a leading wildcard, on this engine only")
    void aTrigramIndexTurnsTheScanIntoAnIndexScan() {
        execute("create extension if not exists pg_trgm");
        execute("drop table if exists sh_sh1033_trgm");
        execute("create table sh_sh1033_trgm (post_id varchar(36) not null, data text not null)");
        execute("insert into sh_sh1033_trgm (post_id, data) "
                + "select gen_random_uuid()::text, "
                + "  '{\"HERO\":\"' || gen_random_uuid() || '\",\"BODY\":\"lorem ipsum dolor sit "
                + "amet consectetur adipiscing elit sed do eiusmod tempor\"}' "
                + "from generate_series(1, " + ROWS + ")");
        execute("insert into sh_sh1033_trgm (post_id, data) "
                + "select gen_random_uuid()::text, "
                + "  '{\"HERO\":\"" + assetId + "\",\"BODY\":\"lorem ipsum\"}' "
                + "from generate_series(1, " + REFERRERS + ")");

        String without = explain("select post_id from sh_sh1033_trgm "
                + "where data like '%" + assetId + "%'");

        execute("create index ix_sh1033_trgm on sh_sh1033_trgm using gin (data gin_trgm_ops)");
        execute("analyze sh_sh1033_trgm");

        String with = explain("select post_id from sh_sh1033_trgm "
                + "where data like '%" + assetId + "%'");

        // The planner's preference and the index's CAPABILITY are two questions, and at this size
        // they have different answers: a scan of five thousand narrow rows costs 194 against the
        // index's estimate, so it declines. Turning the scan off is how you ask the second
        // question on a fixture the first one is too small to settle — an index that cannot serve
        // a predicate is not used even when the alternative is forbidden.
        execute("set enable_seqscan = off");
        String forced = explain("select post_id from sh_sh1033_trgm "
                + "where data like '%" + assetId + "%'");
        execute("set enable_seqscan = on");

        System.out.printf("""

                SH1033 - a leading wildcard over %d inline documents on PostgreSQL:
                  without an index:
                %s  with a pg_trgm GIN index, planner free:
                %s  with a pg_trgm GIN index, seqscan disabled:
                %s""", ROWS + REFERRERS, without, with, forced);

        assertThat(without)
                .as("the control: unindexed, this engine reads the table like the other two")
                .contains("Seq Scan");
        assertThat(forced)
                .as("""
                        a trigram index orders substrings, so PostgreSQL CAN answer \
                        `like '%%<id>%%'` without reading the table — the one engine of the three \
                        where "no index can serve this" is false, and a branch §LXX.6 has here and \
                        nowhere else. It is measured and deliberately NOT recommended; the trade \
                        is in the class note. Plan:%n%s""", forced)
                .contains("Bitmap Index Scan");

        // Recorded rather than asserted, because it is a cost estimate and those move with the
        // planner's version and the fixture's width: at five thousand narrow rows PostgreSQL
        // prefers the scan even with the index available. §LXX.6 needs both halves — the option
        // exists, and it does not pay until the table is large enough to make it pay.
        System.out.printf("SH1033 - at %d rows the planner %s the trigram index on cost%n",
                ROWS + REFERRERS, with.contains("Bitmap Index Scan") ? "chose" : "declined");

        // Dropped, so no sibling inherits a structure it did not build (SH239's confound) and the
        // fixture this class shares is the one its other tests describe.
        execute("drop table if exists sh_sh1033_trgm");
    }

    private long count(String sql) {
        List<?> rows = entityManager.createNativeQuery(sql).getResultList();
        return ((Number) rows.get(0)).longValue();
    }
}
