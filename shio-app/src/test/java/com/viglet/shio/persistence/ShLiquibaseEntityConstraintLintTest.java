/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.yaml.snakeyaml.Yaml;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;

/**
 * SH729 — a constraint declared only in Liquibase is invisible to the unit suite.
 *
 * <h2>The finding this is the instrument for</h2>
 *
 * <p>{@code src/test/resources/application.properties} sets
 * {@code spring.liquibase.enabled=false} and builds H2 from entity metadata via
 * {@code ddl-auto=create-drop}. Nothing in {@code db/changelog} exists during {@code mvn test}. So
 * SH719's unique index — correct in the changeset, correct in its assertion — was <b>absent</b> when
 * the assertion ran, and the test that a second insert is refused <b>passed by performing the
 * insert</b>. It was caught only because that assertion had been written to fail first and did not.
 * Written in the ordinary order it would have been green on arrival, and the guard would have
 * shipped switched off, to be discovered on whichever database first met a concurrent create.</p>
 *
 * <h2>Why the reader, and not another test</h2>
 *
 * <p>SH719's repair was to declare the index <em>twice</em> — {@code @Table(indexes = @Index(...,
 * unique = true))} for the suite and Liquibase for every real database. That is the right shape and
 * it was a convention nothing enforced: any later {@code addUniqueConstraint}, unique
 * {@code createIndex} or {@code addNotNullConstraint} could land changelog-only and stay invisible
 * to the whole suite. Per SH338 the instrument is the deliverable, so this parses the changelogs
 * for constraints the entity metadata should also carry and fails naming the <em>pair</em> that
 * disagree.</p>
 *
 * <p>The cross-DB ITs do execute the changelog, but they are opt-in since SH415 and skip without a
 * Docker daemon, so they are not this backstop. They answer <em>does the migration apply</em>; this
 * answers <em>does the suite know about it</em>.</p>
 *
 * <h2>What "equivalent" means here</h2>
 *
 * <p>Column <b>sets</b>, never names. Hibernate invents its own identifier for a
 * {@code @Column(unique = true)} and the 2026.2 baseline carries generator names like
 * {@code UK8q73y8bnx7kpktylf71gcco05_INDEX_9}; a name-keyed comparison would report every honest
 * pair as a mismatch and teach the reader to relax it. Two declarations agree when they constrain
 * the same columns of the same table.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShLiquibaseEntityConstraintLintTest {

    private static final Path CHANGELOG = Path.of("src", "main", "resources", "db", "changelog");
    private static final String MASTER = "db.changelog-master.yaml";
    private static final String ENTITY_PACKAGE = "com.viglet.shio";

    /**
     * A constraint one side declares, reduced to what both sides can express.
     *
     * @param table   the physical table, already {@link #fold(String) folded}
     * @param columns the constrained columns, order-insensitive and folded
     * @param origin  where it was found, so a failure names the file or the class to open
     */
    private record Constraint(String table, Set<String> columns, String origin) {

        String key() {
            return table + "(" + String.join(",", columns) + ")";
        }
    }

    /**
     * Case and underscores removed, so {@code ShSite}, {@code sh_site} and {@code SH_SITE} are one
     * identifier.
     *
     * <p>Not laziness — a deliberate refusal to re-implement the physical naming strategy. The 2026.2
     * baseline names tables {@code ShSite} and the 2026.3 changesets name them
     * {@code sh_post_unified}, so the two halves of this comparison are written in different
     * conventions by construction. Deciding which spelling Hibernate will emit is a question about
     * configuration; this lint asks a different one — <em>is the same constraint declared on both
     * sides</em> — and folding keeps a naming-strategy change from turning the instrument off.</p>
     */
    private static String fold(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT).replace("_", "");
    }

    /**
     * Tables a changelog constrains that no {@code @Entity} maps.
     *
     * <p>Held as a named list rather than a silent skip: an unmapped table is a constraint the suite
     * cannot see <em>either</em>, and the only difference from a real gap is that there is no
     * annotation to put it on. Empty today; a new entry is a decision, not a default.</p>
     */
    private static final Set<String> TABLES_WITHOUT_AN_ENTITY = Set.of();

    @Test
    @DisplayName("SH729: every unique constraint in a changelog is also declared on the entity")
    void everyChangelogUniqueIsDeclaredOnTheEntity() {
        List<Constraint> declared = liquibaseUniques();
        Map<String, Set<String>> entityUniques = entityUniquesByTable();
        Set<String> mappedTables = mappedTables();

        List<String> invisible = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();
        for (Constraint constraint : declared) {
            String table = constraint.table();
            if (!mappedTables.contains(table)) {
                if (!TABLES_WITHOUT_AN_ENTITY.contains(table)) {
                    unmapped.add(constraint.key() + " from " + constraint.origin());
                }
                continue;
            }
            Set<String> onTheEntity = entityUniques.getOrDefault(table, Set.of());
            if (!onTheEntity.contains(constraint.key())) {
                invisible.add(constraint.key() + " declared in " + constraint.origin()
                        + " but absent from the entity mapping " + constraint.table());
            }
        }

        System.out.printf("%n[SH729] %d unique constraints in db/changelog, %d entity tables carry "
                + "one, %d unmapped%n%n", declared.size(), entityUniques.size(), unmapped.size());

        assertThat(invisible)
                .as("""
                        The unit suite builds H2 from entity metadata with Liquibase off, so a \
                        constraint that lives only in a changelog is not there when the test that \
                        asserts it runs — SH719's uniqueness test passed by performing the insert \
                        it was written to prove impossible. Declare it on both sides: \
                        `@Table(indexes = @Index(name = "...", columnList = "...", unique = true))` \
                        or `@Table(uniqueConstraints = @UniqueConstraint(...))` for the suite, the \
                        changeset for every real database (SH729).""")
                .isEmpty();

        assertThat(unmapped)
                .as("""
                        A constrained table with no @Entity is invisible to the suite for the same \
                        reason and has nowhere to be declared. That is a decision — record it in \
                        TABLES_WITHOUT_AN_ENTITY with the reason — not something this lint should \
                        pass over quietly (SH729).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH729: every NOT NULL a changelog adds is also declared on the entity")
    void everyChangelogNotNullIsDeclaredOnTheEntity() {
        List<Constraint> declared = liquibaseNotNulls();
        Map<String, Set<String>> entityNotNulls = entityNotNullsByTable();
        Set<String> mappedTables = mappedTables();

        List<String> invisible = new ArrayList<>();
        for (Constraint constraint : declared) {
            String table = constraint.table();
            if (!mappedTables.contains(table)) {
                continue;
            }
            Set<String> onTheEntity = entityNotNulls.getOrDefault(table, Set.of());
            if (!onTheEntity.containsAll(constraint.columns())) {
                invisible.add(constraint.key() + " declared in " + constraint.origin()
                        + " but nullable in the entity mapping " + constraint.table());
            }
        }

        // Vacuous today by construction — `addNotNullConstraint` has never been used here, every
        // NOT NULL arriving inside its `createTable`. Printed rather than asserted away, because
        // the shape this guards is the *next* changeset, and a lint whose population is zero should
        // say so out loud instead of reporting the same green as one that checked something.
        System.out.printf("[SH729] %d addNotNullConstraint changes in db/changelog%n%n",
                declared.size());

        assertThat(invisible)
                .as("""
                        Same failure as the unique case: with Liquibase off the column is nullable \
                        in the suite's H2, so a test asserting the write is refused writes the row \
                        and passes. Add `nullable = false` to the @Column (SH729).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH729: the walk still reaches the changelogs and the entities")
    void theWalkIsNotVacuous() {
        // The failure mode of every reader: a moved directory, a renamed key, a package that no
        // longer scans — and the instrument turns off reporting the same green. Named facts rather
        // than counts, because a count is a number somebody relaxes instead of investigating.
        assertThat(includedChangelogs())
                .as("the master's include list is what Liquibase actually applies")
                .contains("v2026.2_baseline.yaml", "v2026.3.24.yaml");

        assertThat(liquibaseUniques())
                .as("SH719's index is the constraint that produced this lint; if the parse stops "
                        + "finding it, the lint is off")
                .anyMatch(constraint -> constraint.columns().equals(Set.of(fold("url_key"))));

        assertThat(entityUniquesByTable())
                .as("and the reflective half has to find the entity that carries its twin")
                .containsKey(fold("sh_post_unified"));
    }

    @Test
    @DisplayName("SH729: no changelog file is left out of the master's include list")
    void noChangelogIsOrphaned() {
        Set<String> included = includedChangelogs();
        List<String> orphans;
        try (Stream<Path> files = Files.list(CHANGELOG)) {
            orphans = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yaml") && !name.equals(MASTER))
                    .filter(name -> !included.contains(name))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertThat(orphans)
                .as("""
                        A changelog nobody includes never runs, which is the same defect one \
                        level up: the constraint is written down, reviewed, committed, and absent \
                        from every database. Add it to db.changelog-master.yaml or delete it \
                        (SH729).""")
                .isEmpty();
    }

    // ---------------------------------------------------------------- the changelog side

    /** File names in the order {@code db.changelog-master.yaml} includes them. */
    private static Set<String> includedChangelogs() {
        Set<String> names = new LinkedHashSet<>();
        for (Object entry : entries(load(CHANGELOG.resolve(MASTER)))) {
            Object include = map(entry).get("include");
            if (include != null) {
                String file = String.valueOf(map(include).get("file"));
                names.add(file.substring(file.lastIndexOf('/') + 1));
            }
        }
        return names;
    }

    private static List<Constraint> liquibaseUniques() {
        List<Constraint> found = new ArrayList<>();
        forEachChange((file, change) -> {
            Map<String, Object> createIndex = map(change.get("createIndex"));
            if (createIndex != null && Boolean.TRUE.equals(createIndex.get("unique"))) {
                found.add(new Constraint(fold(String.valueOf(createIndex.get("tableName"))),
                        columnNames(createIndex.get("columns")), file));
            }
            Map<String, Object> unique = map(change.get("addUniqueConstraint"));
            if (unique != null) {
                found.add(new Constraint(fold(String.valueOf(unique.get("tableName"))),
                        split(String.valueOf(unique.get("columnNames"))), file));
            }
            Map<String, Object> createTable = map(change.get("createTable"));
            if (createTable != null) {
                String table = fold(String.valueOf(createTable.get("tableName")));
                for (Object column : list(createTable.get("columns"))) {
                    Map<String, Object> definition = map(map(column).get("column"));
                    Map<String, Object> constraints =
                            definition == null ? null : map(definition.get("constraints"));
                    if (constraints != null && Boolean.TRUE.equals(constraints.get("unique"))) {
                        found.add(new Constraint(table,
                                normalize(List.of(String.valueOf(definition.get("name")))), file));
                    }
                }
            }
        });
        return found;
    }

    private static List<Constraint> liquibaseNotNulls() {
        List<Constraint> found = new ArrayList<>();
        forEachChange((file, change) -> {
            Map<String, Object> notNull = map(change.get("addNotNullConstraint"));
            if (notNull != null) {
                found.add(new Constraint(fold(String.valueOf(notNull.get("tableName"))),
                        normalize(List.of(String.valueOf(notNull.get("columnName")))), file));
            }
        });
        return found;
    }

    /** Every {@code changes:} entry of every changeSet of every included file, with its file name. */
    private static void forEachChange(java.util.function.BiConsumer<String, Map<String, Object>> visitor) {
        for (String name : includedChangelogs()) {
            for (Object entry : entries(load(CHANGELOG.resolve(name)))) {
                Map<String, Object> changeSet = map(map(entry).get("changeSet"));
                if (changeSet == null) {
                    continue;
                }
                for (Object change : list(changeSet.get("changes"))) {
                    Map<String, Object> asMap = map(change);
                    if (asMap != null) {
                        visitor.accept(name, asMap);
                    }
                }
            }
        }
    }

    private static Set<String> columnNames(Object columns) {
        List<String> names = new ArrayList<>();
        for (Object column : list(columns)) {
            Map<String, Object> definition = map(map(column).get("column"));
            if (definition != null) {
                names.add(String.valueOf(definition.get("name")));
            }
        }
        return normalize(names);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> entries(Object document) {
        Map<String, Object> root = map(document);
        return root == null ? List.of() : list(root.get("databaseChangeLog"));
    }

    private static Object load(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return new Yaml().load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    // ---------------------------------------------------------------- the entity side

    /**
     * Table name to the {@link Constraint#key()} of every unique group its entity declares.
     *
     * <p>Reflective rather than textual on purpose: {@code @Table}, {@code @Index},
     * {@code @UniqueConstraint} and {@code @Column} are all {@code RUNTIME}-retained, so the JVM
     * already holds exactly what Hibernate will read. A regex over the source would have to
     * re-implement inheritance and the naming strategy, and would disagree with Hibernate in
     * precisely the cases this lint exists to catch.</p>
     */
    private static Map<String, Set<String>> entityUniquesByTable() {
        Map<String, Set<String>> byTable = new TreeMap<>();
        for (Class<?> entity : entities()) {
            String table = tableOf(entity);
            Set<String> keys = byTable.computeIfAbsent(table, key -> new TreeSet<>());
            Table annotation = entity.getAnnotation(Table.class);
            if (annotation != null) {
                for (Index index : annotation.indexes()) {
                    if (index.unique()) {
                        keys.add(new Constraint(table, split(index.columnList()), table).key());
                    }
                }
                for (UniqueConstraint constraint : annotation.uniqueConstraints()) {
                    keys.add(new Constraint(table, normalize(Arrays.asList(constraint.columnNames())),
                            table).key());
                }
            }
            for (java.lang.reflect.Field field : ownFields(entity)) {
                Column column = field.getAnnotation(Column.class);
                if (column != null && column.unique()) {
                    keys.add(new Constraint(table, normalize(List.of(columnOf(field))), table).key());
                }
            }
        }
        byTable.values().removeIf(Set::isEmpty);
        return byTable;
    }

    /** Table name to the lower-cased columns its entity maps as {@code nullable = false}. */
    private static Map<String, Set<String>> entityNotNullsByTable() {
        Map<String, Set<String>> byTable = new TreeMap<>();
        for (Class<?> entity : entities()) {
            Set<String> columns =
                    byTable.computeIfAbsent(tableOf(entity), key -> new TreeSet<>());
            for (java.lang.reflect.Field field : ownFields(entity)) {
                Column column = field.getAnnotation(Column.class);
                if (column != null && !column.nullable()) {
                    columns.add(fold(columnOf(field)));
                }
            }
        }
        return byTable;
    }

    private static Set<String> mappedTables() {
        Set<String> tables = new TreeSet<>();
        entities().forEach(entity -> tables.add(tableOf(entity)));
        return tables;
    }

    private static List<Class<?>> entities() {
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : provider.findCandidateComponents(ENTITY_PACKAGE)) {
            try {
                found.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("scanned but unloadable: " + definition, e);
            }
        }
        return found;
    }

    /**
     * The entity's own fields plus any {@code @MappedSuperclass} ancestor's.
     *
     * <p>Stops at an {@code @Entity} superclass deliberately: Shio's hierarchies are JOINED
     * ({@code ShSite extends ShObject} via {@code @PrimaryKeyJoinColumn}), so a parent's columns
     * live in the <em>parent's</em> table and folding them in here would attribute a constraint to
     * the wrong one.</p>
     */
    private static List<java.lang.reflect.Field> ownFields(Class<?> entity) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        Class<?> type = entity;
        while (type != null && type != Object.class) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic() && !field.isAnnotationPresent(Transient.class)
                        && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    fields.add(field);
                }
            }
            Class<?> parent = type.getSuperclass();
            type = parent != null && parent.isAnnotationPresent(MappedSuperclass.class) ? parent : null;
        }
        return fields;
    }

    private static String tableOf(Class<?> entity) {
        Table table = entity.getAnnotation(Table.class);
        String name = table == null || table.name().isEmpty() ? entity.getSimpleName() : table.name();
        return fold(name);
    }

    /** Hibernate's default when {@code @Column(name)} is absent is the camel-cased field name. */
    private static String columnOf(java.lang.reflect.Field field) {
        Column column = field.getAnnotation(Column.class);
        return column == null || column.name().isEmpty() ? field.getName() : column.name();
    }

    private static Set<String> split(String columnList) {
        return normalize(Arrays.asList(columnList.split(",")));
    }

    private static Set<String> normalize(List<String> columns) {
        Set<String> normalized = new TreeSet<>();
        columns.forEach(column -> normalized.add(fold(column)));
        return normalized;
    }
}
