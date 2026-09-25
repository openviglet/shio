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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * SH1089 — a changelog that names a table, or a column, nothing created.
 *
 * <h2>Why the suite cannot see it</h2>
 *
 * <p>The unit suite runs with {@code spring.liquibase.enabled=false} and builds H2 from the entity
 * model, so nothing in {@code db/changelog} executes during {@code mvn test}. SH1010's changeset added
 * a column to {@code sh_post_type_attr}; the table the SH26 baseline created is {@code ShPostTypeAttr}.
 * The Java gate went green at 2233 tests and a clean instance refused to start. SH729's lint could not
 * catch it either, and on purpose: it folds case and underscores so that two naming conventions compare
 * as one identifier. <b>The database does not fold</b>, and that is the whole defect.</p>
 *
 * <h2>What this walks</h2>
 *
 * <p>The changelogs in the master's include order, keeping the tables and columns that have been
 * created, spelled exactly. Every change that names a table must name one created earlier, and every
 * column it names must exist on it by then. The walk needs no entity model and no database, and a
 * failure names the file, the changeset and the identifier.</p>
 *
 * <p>The change kinds it understands are enumerated, and one it does not is a failure rather than a
 * skip: a change type nobody taught this walk is exactly where the next unchecked name would hide.</p>
 */
class ShLiquibaseChangelogReferenceLintTest {

    private static final Path CHANGELOG = Path.of("src", "main", "resources", "db", "changelog");
    private static final String MASTER = "db.changelog-master.yaml";

    /** Every change type this walk reads. Anything else in a {@code changes:} list fails the lint. */
    private static final Set<String> UNDERSTOOD = Set.of("createTable", "addColumn", "createIndex",
            "addForeignKeyConstraint", "addUniqueConstraint", "dropColumn", "addDefaultValue", "update",
            "addNotNullConstraint", "dropTable", "dropIndex", "dropForeignKeyConstraint",
            "dropUniqueConstraint", "sql");

    /** SH1099 — structure a raw {@code sql} change would change without this walk seeing it. */
    private static final Pattern DDL = Pattern.compile(
            "\\b(create|alter|drop|rename)\\s+(table|index|column|view)\\b", Pattern.CASE_INSENSITIVE);

    /** SH1099 — the tables a raw {@code sql} change names, by this schema's own prefix. */
    private static final Pattern SH_TABLE = Pattern.compile("\\b(sh_[a-z0-9_]+)\\b");

    /** The walk's result: what refers to nothing, what it could not read, and what it saw. */
    private record Walk(List<String> dangling, List<String> unknown, Map<String, Set<String>> tables,
                        int changes) {
    }

    @Test
    @DisplayName("SH1089: every table and column a changeset names was created, spelled exactly")
    void everyReferenceWasCreated() {
        Walk walk = walk();

        System.out.printf("%n[SH1089] %d changes over %d tables walked in master order%n%n",
                walk.changes(), walk.tables().size());

        assertThat(walk.dangling())
                .as("""
                        A changeset names a table or column no earlier changeset created, spelled \
                        exactly. The unit suite runs with Liquibase off, so this passes every test and \
                        refuses to start a clean store: SH1010 wrote sh_post_type_attr for the table the \
                        baseline created as ShPostTypeAttr. Spell it as its createTable did (SH1089).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1089: a change type the walk was never taught is a failure, not a skip")
    void everyChangeTypeIsUnderstood() {
        assertThat(walk().unknown())
                .as("""
                        A change type this walk does not read is a place a table or column name goes \
                        unchecked. Teach the walk what it names (UNDERSTOOD and the switch in apply) \
                        rather than letting it pass (SH1089).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1089: the walk is not vacuous — it knows the baseline's spelling and SH1010's column")
    void theWalkIsNotVacuous() {
        Walk walk = walk();
        assertThat(walk.tables())
                .as("the baseline creates ShPostTypeAttr in that spelling, which is the name SH1010 missed")
                .containsKey("ShPostTypeAttr")
                .doesNotContainKey("sh_post_type_attr");
        assertThat(walk.changes()).as("a walk that read nothing reports the same green as a clean one")
                .isGreaterThan(200);
    }

    @Test
    @DisplayName("SH1089: the rule fires on SH1010's defect, reproduced")
    void theRuleFiresOnTheDefect() {
        // The defect itself, fed to the same apply: an addColumn on the snake_case spelling of a
        // table the baseline created in PascalCase.
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        tables.put("ShPostTypeAttr", new TreeSet<>(List.of("id", "name")));
        List<String> dangling = new ArrayList<>();
        apply("v2026.3.x.yaml", "sh1010-constraints", Map.of("addColumn", Map.of(
                "tableName", "sh_post_type_attr",
                "columns", List.of(Map.of("column", Map.of("name", "constraints_json"))))),
                tables, dangling, new ArrayList<>());
        assertThat(dangling).singleElement().asString()
                .contains("sh_post_type_attr").contains("sh1010-constraints");
    }

    // ---------------------------------------------------------------- the walk

    private static Walk walk() {
        Map<String, Set<String>> tables = new LinkedHashMap<>();
        List<String> dangling = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        int changes = 0;
        for (String file : includedChangelogs()) {
            for (Object entry : entries(load(CHANGELOG.resolve(file)))) {
                Map<String, Object> changeSet = map(map(entry).get("changeSet"));
                if (changeSet == null) {
                    continue;
                }
                String id = String.valueOf(changeSet.get("id"));
                for (Object change : list(changeSet.get("changes"))) {
                    Map<String, Object> asMap = map(change);
                    if (asMap != null) {
                        changes++;
                        apply(file, id, asMap, tables, dangling, unknown);
                    }
                }
            }
        }
        return new Walk(dangling, unknown, tables, changes);
    }

    /** One change against the tables created so far. */
    private static void apply(String file, String id, Map<String, Object> change,
                              Map<String, Set<String>> tables, List<String> dangling, List<String> unknown) {
        for (Map.Entry<String, Object> entry : change.entrySet()) {
            String kind = entry.getKey();
            Map<String, Object> body = map(entry.getValue());
            String where = file + " changeSet " + id + " (" + kind + ")";
            if (!UNDERSTOOD.contains(kind) || body == null) {
                unknown.add(where);
                continue;
            }
            switch (kind) {
                case "createTable" -> tables.put(text(body, "tableName"), new TreeSet<>(columns(body)));
                case "dropTable" -> requireTable(tables, text(body, "tableName"), where, dangling)
                        .ifPresent(table -> tables.remove(table));
                case "addColumn" -> requireTable(tables, text(body, "tableName"), where, dangling)
                        .ifPresent(table -> tables.get(table).addAll(columns(body)));
                case "dropColumn" -> requireTable(tables, text(body, "tableName"), where, dangling)
                        .ifPresent(table -> {
                            requireColumns(tables, table, List.of(text(body, "columnName")), where, dangling);
                            tables.get(table).remove(text(body, "columnName"));
                        });
                case "createIndex", "update" -> requireTable(tables, text(body, "tableName"), where, dangling)
                        .ifPresent(table -> requireColumns(tables, table, columns(body), where, dangling));
                case "addUniqueConstraint" -> requireTable(tables, text(body, "tableName"), where, dangling)
                        .ifPresent(table -> requireColumns(tables, table, split(text(body, "columnNames")),
                                where, dangling));
                case "addDefaultValue", "addNotNullConstraint" ->
                        requireTable(tables, text(body, "tableName"), where, dangling)
                                .ifPresent(table -> requireColumns(tables, table,
                                        List.of(text(body, "columnName")), where, dangling));
                // SH1099 — raw SQL is data work only (a migration of values), so it may not change
                // structure the walk tracks, and every `sh_` table it names must exist by then. Its
                // columns are not parsed out of free SQL; the engine-specific IT that runs it is
                // what proves those.
                case "sql" -> {
                    String sql = text(body, "sql");
                    if (DDL.matcher(sql).find()) {
                        dangling.add(where + " changes structure in raw SQL, which this walk cannot follow: "
                                + "use a typed change");
                    }
                    Matcher named = SH_TABLE.matcher(sql);
                    Set<String> seen = new TreeSet<>();
                    while (named.find()) {
                        seen.add(named.group(1));
                    }
                    seen.forEach(table -> requireTable(tables, table, where, dangling));
                }
                case "addForeignKeyConstraint" -> {
                    requireTable(tables, text(body, "baseTableName"), where, dangling)
                            .ifPresent(table -> requireColumns(tables, table,
                                    split(text(body, "baseColumnNames")), where, dangling));
                    requireTable(tables, text(body, "referencedTableName"), where, dangling)
                            .ifPresent(table -> requireColumns(tables, table,
                                    split(text(body, "referencedColumnNames")), where, dangling));
                }
                // Named by the object they drop, not by a table this walk could check.
                case "dropIndex", "dropForeignKeyConstraint", "dropUniqueConstraint" ->
                        requireTable(tables, text(body, kind.equals("dropForeignKeyConstraint")
                                ? "baseTableName" : "tableName"), where, dangling);
                default -> unknown.add(where);
            }
        }
    }

    private static java.util.Optional<String> requireTable(Map<String, Set<String>> tables, String table,
                                                           String where, List<String> dangling) {
        if (tables.containsKey(table)) {
            return java.util.Optional.of(table);
        }
        dangling.add(where + ": table '" + table + "' was never created"
                + suggestion(table, tables.keySet()));
        return java.util.Optional.empty();
    }

    private static void requireColumns(Map<String, Set<String>> tables, String table, List<String> columns,
                                       String where, List<String> dangling) {
        for (String column : columns) {
            if (!tables.get(table).contains(column)) {
                dangling.add(where + ": column '" + column + "' is not on '" + table + "'"
                        + suggestion(column, tables.get(table)));
            }
        }
    }

    /** The created name this one folds to, when there is one: the usual cause is a spelling. */
    private static String suggestion(String name, Set<String> created) {
        String folded = name.toLowerCase(java.util.Locale.ROOT).replace("_", "");
        return created.stream()
                .filter(candidate -> candidate.toLowerCase(java.util.Locale.ROOT).replace("_", "").equals(folded))
                .findFirst()
                .map(candidate -> " (it was created as '" + candidate + "')")
                .orElse("");
    }

    private static List<String> columns(Map<String, Object> body) {
        List<String> names = new ArrayList<>();
        for (Object column : list(body.get("columns"))) {
            Map<String, Object> definition = map(map(column).get("column"));
            if (definition != null && definition.get("name") != null) {
                names.add(String.valueOf(definition.get("name")));
            }
        }
        return names;
    }

    private static List<String> split(String names) {
        return names == null ? List.of() : Arrays.stream(names.split(",")).map(String::trim)
                .filter(name -> !name.isEmpty()).toList();
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

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
}
