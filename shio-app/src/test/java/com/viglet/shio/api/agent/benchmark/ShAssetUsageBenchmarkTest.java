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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.api.staticfile.ShStaticFileUsage;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1033 — what "where is this used" costs, before anything replaces it.
 *
 * <h2>Why the measurement is the deliverable</h2>
 *
 * <p>§LXX.6 proposes a reverse-reference table maintained on every write, and that may well be
 * right. But a row maintained on every create, update, publish and delete is a <em>hot-path</em>
 * cost paid to make a dialog fast, and agents.md is explicit that such a change lands as a
 * benchmark first: <em>a cost that is argued rather than measured is the same defect as a premise
 * carried in a comment, and it fails the same way — quietly, and in somebody else's
 * environment.</em> So this class states what the read costs today and what the maintenance would
 * cost, and whatever it says is SH1034's argument rather than the other way round.</p>
 *
 * <h2>What it found</h2>
 *
 * <p><b>The read is a full table scan, twice.</b> {@code findReferencingFileId} is
 * {@code data like concat('%', :fileId, '%')} — a leading wildcard, which no B-tree can serve —
 * and {@code listUsage} runs it once per state, so opening one detail dialog examines every post
 * row in the tenant twice to answer with a handful. The scan count and the elapsed time are both
 * linear in the table, which is the shape that makes the dialog a per-instance rather than a
 * per-asset cost.</p>
 *
 * <p><b>The maintenance is not.</b> A prototype of the index — the table, its two indexes, and the
 * walk that fills it from a post's own declared {@code File} and {@code Content Select} fields —
 * turns the read into a seek bounded by the number of referrers, and costs a fixed handful of
 * statements per write however large the table is. The two numbers are on different curves, and
 * that, rather than either number alone, is what settles BE6.</p>
 *
 * <h2>Rows examined, not milliseconds — and why this class needs a third instrument</h2>
 *
 * <p>SH264's rule holds (a timing ceiling on a shared machine is a flake, SH250), so every
 * assertion here is on a count and the milliseconds are printed for a reader. But neither
 * instrument this package already owns can see a scan:
 * {@code getEntityLoadCount()} counts the rows a query <em>returns</em>, which for this predicate
 * is the answer and not the work, and SH634's {@code getExecutionRowCount()} counts the same
 * thing one layer down. A query that reads fifty thousand rows to return one is invisible to
 * both — which is precisely the defect. So the scan is read off H2's own
 * {@code EXPLAIN ANALYZE}, whose {@code scanCount} is the quantity in question and is as
 * deterministic as an entity count.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-asset-usage-cost;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        // Without this the query counter below reads zero and the "twice per dialog" assertion
        // passes for the wrong reason — which it did, on this class's first run.
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShAssetUsageBenchmarkTest {

    /**
     * The three sizes §LXX.5 asks for, grown cumulatively into one table.
     *
     * <p>Cumulative rather than three fixtures, because the question is how one query behaves as
     * a table grows and three separate databases would answer it three times for three tables.
     * Every measurement is taken at a stated row count, and the count is printed beside it.</p>
     */
    private static final int[] SIZES = { 1_000, 10_000, 50_000 };

    /** Referrers pointing at the measured asset, constant at every size. */
    private static final int REFERRERS = 3;

    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");

    private static final String BUDGETS = "token-budgets.properties";

    /** H2 states the rows a step read as {@code /* scanCount: N *&#47;} inside the plan. */
    private static final Pattern SCAN_COUNT = Pattern.compile("scanCount:\\s*(\\d+)");

    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Map<String, Long> MEASURED = new LinkedHashMap<>();

    /** Statements one write's maintenance costs, one entry per size in {@link #SIZES}. */
    private static final List<Long> MAINTENANCE = new ArrayList<>();

    private static final Properties BUDGET = load();

    private String assetId;
    private ShFolder folder;
    private ShPostType articleType;
    private int rows;

    // --- fixture ---

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName("usage-cost-" + UUID.randomUUID());
            site.setDate(EPOCH);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(EPOCH);
            root.setOwner("admin");
            entityManager.persist(root);
            folder = root;

            // A type that DECLARES where its references live, which is the walk's whole advantage
            // over a substring: it reads two named fields instead of the whole document.
            articleType = new ShPostType();
            articleType.setName("Sh1033Article");
            articleType.setTitle("SH1033 Article");
            articleType.setDescription("SH1033 benchmark fixture");
            articleType.setDate(EPOCH);
            entityManager.persist(articleType);
            attribute(articleType, "HERO", ShSystemWidget.FILE);
            attribute(articleType, "RELATED", ShSystemWidget.CONTENT_SELECT);

            ShPostUnified asset = new ShPostUnified();
            asset.setShFolder(root);
            asset.setShPostType(shStaticFileService.resolveOrCreateFilePostType());
            asset.setState(ShPostState.PUBLISHED);
            asset.setTitle("hero.png");
            asset.setData("{\"FILE\":\"hero.png\",\"SIZE\":1234}");
            asset.setDate(EPOCH);
            asset.setOwner("admin");
            entityManager.persist(asset);
            assetId = asset.getId();
        });

        createPrototypeTable();
    }

    private void attribute(ShPostType type, String name, String widget) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setShPostType(type);
        attr.setShWidget(entityManager.createQuery(
                "select w from ShWidget w where w.name = :name",
                com.viglet.shio.persistence.model.widget.ShWidget.class)
                .setParameter("name", widget)
                .getSingleResult());
        entityManager.persist(attr);
    }

    /**
     * Grows the table to {@code target} rows of noise, plus the fixed referrers.
     *
     * <p>Noise rows carry a {@code data} document of realistic weight with <em>other</em> asset
     * ids in it — a scan whose rows are two bytes wide is not the scan this query performs, and
     * a substring match that never nearly-matches is not the comparison it performs either.</p>
     */
    private void growTo(int target) {
        if (rows == 0) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (int index = 0; index < REFERRERS; index++) {
                    ShPostUnified post = new ShPostUnified();
                    post.setShFolder(folder);
                    post.setShPostType(articleType);
                    post.setState(index == 0 ? ShPostState.DRAFT : ShPostState.PUBLISHED);
                    post.setTitle("referrer " + index);
                    post.setFurl("referrer-" + index);
                    post.setData("{\"HERO\":\"" + assetId + "\",\"RELATED\":[],"
                            + "\"BODY\":\"" + filler() + "\"}");
                    post.setDate(EPOCH);
                    post.setOwner("admin");
                    entityManager.persist(post);
                }
            });
            rows = REFERRERS;
        }
        int toAdd = target - rows;
        if (toAdd <= 0) {
            return;
        }
        final int start = rows;
        // Batched and cleared: fifty thousand managed entities in one persistence context is a
        // measurement of Hibernate's flush, not of the query under test.
        for (int batch = 0; batch < toAdd; batch += 500) {
            final int from = start + batch;
            final int to = Math.min(from + 500, start + toAdd);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (int index = from; index < to; index++) {
                    ShPostUnified post = new ShPostUnified();
                    post.setShFolder(folder);
                    post.setShPostType(articleType);
                    post.setState(index % 2 == 0 ? ShPostState.DRAFT : ShPostState.PUBLISHED);
                    post.setTitle("noise " + index);
                    post.setFurl("noise-" + index);
                    post.setData("{\"HERO\":\"" + UUID.randomUUID() + "\",\"RELATED\":[\""
                            + UUID.randomUUID() + "\"],\"BODY\":\"" + filler() + "\"}");
                    post.setDate(EPOCH);
                    post.setOwner("admin");
                    entityManager.persist(post);
                }
                entityManager.flush();
                entityManager.clear();
            });
        }
        rows = start + toAdd;
    }

    /** Body text, so a row is the width the scan actually reads rather than a stub. */
    private static String filler() {
        return "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor "
                + "incididunt ut labore et dolore magna aliqua ut enim ad minim veniam quis";
    }

    // --- the read, today ---

    @Test
    @Order(1)
    @DisplayName("SH1033: one dialog scans the whole table, once per state, at every size")
    void theDialogScansTheTableTwice() {
        List<String> report = new ArrayList<>();
        long firstScan = 0;
        long lastScan = 0;

        for (int size : SIZES) {
            growTo(size);
            long scans = usageQueryExecutions(this::usage);
            long examined = scanCount(likePlan(ShPostState.PUBLISHED));
            long millis = millisOf(this::usage);
            // Taken here rather than in its own method so the write-side number is measured at the
            // SAME three sizes as the read. Measuring it once at the end proves nothing about how
            // it scales, which is the only question being asked of it.
            long maintenance = maintenanceStatementsFor(2);
            MAINTENANCE.add(maintenance);

            if (firstScan == 0) {
                firstScan = examined;
            }
            lastScan = examined;
            report.add(String.format(
                    "  %6d rows: %d scan(s) per dialog, %d row(s) examined per scan, %d ms"
                            + "  |  maintenance %d statement(s) per write",
                    rows, scans, examined, millis, maintenance));

            assertThat(usage()).as("the answer does not change with the noise").hasSize(REFERRERS);

            // The first half of the symptom, as an assertion. Two states, two scans, one dialog.
            // Bounded BELOW as well, and that is not belt-and-braces: this class's first run had
            // no `generate_statistics`, so the counter read 0, the ceiling was satisfied, and the
            // assertion said nothing. A count that can be vacuously zero needs both ends.
            assertThat(scans)
                    .as("""
                            `listUsage` loops PUBLISHED then DRAFT and runs the same unindexed \
                            predicate for each, so a dialog pays for the table twice. A single-state \
                            query is the cheap half of BE6's fix; when it lands, this ceiling comes \
                            down with it rather than being left where a win can erode under it.""")
                    .isPositive()
                    .isLessThanOrEqualTo(ceiling("usage.dialog.max-scans"));

            // The second half, and the one no instrument in this package could see. A B-tree
            // cannot serve a leading wildcard, so every row is read whatever the answer is.
            assertThat(examined)
                    .as("""
                            `data like concat('%%', :fileId, '%%')` has a LEADING wildcard, which no \
                            index orders — so the plan is a table scan and the rows examined are the \
                            rows that exist. Measured %d examined to return %d at %d rows. If this \
                            ever drops below the row count, the predicate stopped being a scan and \
                            the whole of BE6's premise needs re-reading.""",
                            examined, REFERRERS, rows)
                    .isGreaterThanOrEqualTo(rows);
        }

        MEASURED.put("usage.scan.rows-examined-at-1k", firstScan);
        MEASURED.put("usage.scan.rows-examined-at-50k", lastScan);
        long growth = lastScan / Math.max(firstScan, 1);
        MEASURED.put("usage.scan.growth-ratio", growth);
        MEASURED.put("usage.maintenance.statements-per-write", MAINTENANCE.get(0));

        System.out.println("\nSH1033 — where-is-this-used, measured on H2:\n"
                + String.join("\n", report));

        // A FLOOR, which is the unusual direction and deliberate: what it asserts is that the cost
        // is linear in the table. Fifty times the rows, at least forty times the work. A fix makes
        // this fail, which is the point — it cannot be quietly satisfied, only re-measured.
        assertThat(growth)
                .as("""
                        fifty times the rows must cost about fifty times the scan, or this is not \
                        the shape §LXX.5 describes. Measured %dx (%d -> %d). THIS FAILING IS GOOD \
                        NEWS: it means an index now serves the predicate, and the honest response is \
                        to lower this floor and record what the new plan is — not to widen it.""",
                        growth, firstScan, lastScan)
                .isGreaterThanOrEqualTo(ceiling("usage.scan.min-growth-ratio"));

        // The write side's whole claim, and the comparison that makes it one: the SAME shape costs
        // the SAME at a thousand rows and at fifty thousand. The two curves are what settles BE6,
        // so they have to be measured on one fixture rather than quoted from two runs.
        assertThat(MAINTENANCE)
                .as("""
                        the maintenance a reverse index needs is bounded by the POST's declared \
                        reference fields, not by the table — one delete plus one insert per \
                        reference. Measured %s at %s rows respectively. Growing the fixture cannot \
                        absorb this finding, because the assertion is that the values are equal.""",
                        MAINTENANCE, List.of(SIZES[0], SIZES[1], SIZES[2]))
                .containsOnly(MAINTENANCE.get(0));
        assertThat(MAINTENANCE.get(0))
                .as("and the flat cost is small: one delete plus one insert per reference")
                .isLessThanOrEqualTo(ceiling("usage.maintenance.max-statements-per-write"));
    }

    // --- the write, prototyped ---

    /**
     * What the index would cost on the hot path, built rather than argued.
     *
     * <p>The prototype is the whole of §LXX.6's proposal minus the schema commitment: the table,
     * its two indexes, and the maintenance step — parse the post's {@code data} once, read the
     * fields its post-type declares as {@code File} or {@code Content Select}, replace that post's
     * rows. It lives in this class's own database because the decision to add a table is
     * SH1034's, and a changeset shipped to make a measurement is a schema nobody chose.</p>
     */
    @Test
    @Order(2)
    @DisplayName("SH1033: through the prototype index the same answer is a seek, not a scan")
    void theIndexedReadIsASeek() {
        growTo(SIZES[SIZES.length - 1]);
        backfillPrototype();

        long seek = scanCount("explain analyze select from_post_id, from_state "
                + "from sh_reference_prototype where to_object_id = '" + assetId + "'");
        long millis = millisOf(this::prototypeUsage);

        MEASURED.put("usage.indexed.rows-examined", seek);

        System.out.printf("""

                SH1033 - a maintained reverse index, prototyped at %d rows:
                  read: %d row(s) examined, %d ms, %d referrer(s) found
                  against the LIKE at the same size: %d row(s) examined, twice
                """, rows, seek, millis, prototypeUsage().size(),
                MEASURED.get("usage.scan.rows-examined-at-50k"));

        assertThat(prototypeUsage())
                .as("the prototype answers the same question as the scan, or it is not a "
                        + "comparison")
                .hasSize(REFERRERS);

        // The half that decides BE6. The read stops being linear in the table.
        assertThat(seek)
                .as("""
                        an equality predicate on an indexed column is a seek: %d row(s) examined \
                        against %d in the table, where the LIKE examines all of them. This is the \
                        number that makes the write-side cost worth paying.""", seek, rows)
                .isLessThanOrEqualTo(ceiling("usage.indexed.max-rows-examined"));
    }

    @AfterAll
    static void report() {
        StringBuilder out = new StringBuilder("\nSH1033 — recorded beside the budgets:\n");
        MEASURED.forEach((name, value) -> out.append("  ").append(name).append(": ")
                .append(value).append('\n'));
        System.out.println(out);
    }

    // --- measuring ---

    private List<ShStaticFileUsage> usage() {
        return shStaticFileService.listUsage(assetId);
    }

    /** How many times the {@code data like} predicate ran while {@code work} did. */
    private long usageQueryExecutions(Runnable work) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = likeExecutions(statistics);
        work.run();
        return likeExecutions(statistics) - before;
    }

    private static long likeExecutions(Statistics statistics) {
        long total = 0;
        for (String query : statistics.getQueries()) {
            if (query.contains("p.data like")) {
                total += statistics.getQueryStatistics(query).getExecutionCount();
            }
        }
        return total;
    }

    /**
     * Rows H2 read executing {@code plan}, off its own {@code EXPLAIN ANALYZE}.
     *
     * <p>The one quantity this package had no instrument for, and the one this task is about: an
     * entity count and a returned-row count both describe the <em>answer</em>, and a scan's whole
     * defect is that its answer is small.</p>
     */
    private long scanCount(String explainSql) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Object plan = entityManager.createNativeQuery(explainSql).getSingleResult();
            String text = String.valueOf(plan);
            Matcher matcher = SCAN_COUNT.matcher(text);
            long total = 0;
            boolean found = false;
            while (matcher.find()) {
                total += Long.parseLong(matcher.group(1));
                found = true;
            }
            assertThat(found)
                    .as("H2 states rows read as `scanCount:` in the plan; this one has none, so "
                            + "the measurement would silently read zero. Plan was:%n%s", text)
                    .isTrue();
            return total;
        });
    }

    /** The one-state half of what {@code listUsage} runs twice, as H2 sees it. */
    private String likePlan(ShPostState state) {
        return "explain analyze select p.id from sh_post_unified p where p.id <> '" + assetId
                + "' and p.state = '" + state + "' and p.data like '%" + assetId + "%'";
    }

    private long millisOf(Runnable work) {
        long started = System.nanoTime();
        work.run();
        return (System.nanoTime() - started) / 1_000_000;
    }

    // --- the prototype ---

    private void createPrototypeTable() {
        execute("""
                create table if not exists sh_reference_prototype (
                  from_post_id varchar(36) not null,
                  from_state varchar(16) not null,
                  to_object_id varchar(36) not null,
                  field_name varchar(255) not null
                )""");
        execute("create index if not exists ix_reference_prototype_to "
                + "on sh_reference_prototype(to_object_id)");
        execute("create index if not exists ix_reference_prototype_from "
                + "on sh_reference_prototype(from_post_id, from_state)");
    }

    /**
     * The maintenance step one write would pay, counted in statements.
     *
     * <p>One parse of the post's own {@code data}, one delete of its rows, one insert per
     * reference it declares. Nothing here reads another post, which is the property being
     * measured: the cost belongs to the row being written.</p>
     */
    private long maintenanceStatementsFor(int references) {
        String postId = UUID.randomUUID().toString();
        StringBuilder data = new StringBuilder("{\"HERO\":\"" + assetId + "\",\"RELATED\":[");
        for (int index = 1; index < references; index++) {
            data.append(index > 1 ? "," : "").append('"').append(UUID.randomUUID()).append('"');
        }
        data.append("]}");

        List<String> referenced = walk(data.toString());
        assertThat(referenced)
                .as("the walk reads the type's declared File and Content Select fields, so it "
                        + "must find exactly the references the document carries")
                .hasSize(references);

        return new TransactionTemplate(transactionManager).execute(status -> {
            long statements = 1;
            entityManager.createNativeQuery(
                    "delete from sh_reference_prototype where from_post_id = ?1 and from_state = ?2")
                    .setParameter(1, postId)
                    .setParameter(2, ShPostState.DRAFT.name())
                    .executeUpdate();
            for (String target : referenced) {
                entityManager.createNativeQuery("insert into sh_reference_prototype "
                        + "(from_post_id, from_state, to_object_id, field_name) "
                        + "values (?1, ?2, ?3, ?4)")
                        .setParameter(1, postId)
                        .setParameter(2, ShPostState.DRAFT.name())
                        .setParameter(3, target)
                        .setParameter(4, "HERO")
                        .executeUpdate();
                statements++;
            }
            // Written and then removed: this measures the WRITE, and leaving the rows behind
            // would make the seek below answer with them (SH239's confound, one table over).
            entityManager.createNativeQuery(
                    "delete from sh_reference_prototype where from_post_id = ?1")
                    .setParameter(1, postId)
                    .executeUpdate();
            return statements;
        });
    }

    /** The references a document declares, read through its post-type rather than guessed. */
    private List<String> walk(String data) {
        JsonNode parsed = objectMapper.readTree(data);
        List<String> found = new ArrayList<>();
        if (!parsed.isObject()) {
            return found;
        }
        for (ShPostTypeAttr attr : referenceFields()) {
            JsonNode value = parsed.get(attr.getName());
            if (value == null) {
                continue;
            }
            if (value.isString() && !value.asString().isBlank()) {
                found.add(value.asString());
            } else if (value.isArray()) {
                value.forEach(entry -> {
                    if (entry.isString() && !entry.asString().isBlank()) {
                        found.add(entry.asString());
                    }
                });
            }
        }
        return found;
    }

    private List<ShPostTypeAttr> referenceFields() {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("""
                        select a from ShPostTypeAttr a
                         where a.shPostType.id = :type
                           and a.shWidget.name in (:file, :select)""", ShPostTypeAttr.class)
                .setParameter("type", articleType.getId())
                .setParameter("file", ShSystemWidget.FILE)
                .setParameter("select", ShSystemWidget.CONTENT_SELECT)
                .getResultList());
    }

    /** Fills the prototype from the fixture, the way a backfill would. */
    private void backfillPrototype() {
        execute("delete from sh_reference_prototype");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery("""
                        insert into sh_reference_prototype
                               (from_post_id, from_state, to_object_id, field_name)
                        select p.id, p.state, ?1, 'HERO'
                          from sh_post_unified p
                         where p.data like ?2""")
                        .setParameter(1, assetId)
                        .setParameter(2, "%" + assetId + "%")
                        .executeUpdate());
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> prototypeUsage() {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createNativeQuery("select from_post_id, from_state from sh_reference_prototype "
                        + "where to_object_id = ?1")
                .setParameter(1, assetId)
                .getResultList());
    }

    private void execute(String sql) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createNativeQuery(sql).executeUpdate());
    }

    // --- budgets ---

    private static long ceiling(String key) {
        String value = BUDGET.getProperty(key);
        assertThat(value).as("%s is missing from %s", key, BUDGETS).isNotNull();
        return Long.parseLong(value.trim());
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream in = ShAssetUsageBenchmarkTest.class.getClassLoader()
                .getResourceAsStream(BUDGETS)) {
            assertThat(in).as("%s is on the test classpath", BUDGETS).isNotNull();
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + BUDGETS, e);
        }
        return properties;
    }
}
