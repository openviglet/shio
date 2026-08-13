/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A sort by a key that is not unique needs a tiebreaker (SH722).
 *
 * <h2>The finding, and why the instrument is worth more than it</h2>
 *
 * <p>P5 promises byte-identical serialization, and SH714 measured it on the wire: ten identical
 * {@code shio_context} calls returned <b>two</b> distinct packs, differing in one line — two
 * attributes swapping places. {@code ShCdaMapper#schemaFields} sorted by {@code ordinal} and nothing
 * else, over a {@code Set} the persistence layer hands it in hash order, and two attributes shared
 * {@code ordinal: 2}. The comparator declared them equal and the tie went to whatever order the set
 * iterated this time. <b>A stable sort over an unstable input is an unstable sort.</b></p>
 *
 * <p>The consequences run past the bytes: the context pack carries its own {@code digest}, so the
 * digest flipped between two calls describing the same instance and P9's cheap proof stopped being
 * one; and a conditional read is ETagged on the rendered bytes, so revalidation missed for a reason
 * the caller could not see.</p>
 *
 * <p>SH722 named one line. <b>The grep found five, and three others that were already correct</b> —
 * which is SH322's rule in its usual shape: somebody had learned this before and fixed the call site
 * in front of them, and nothing carried the lesson to the next one. So the instrument lands with the
 * fixes rather than after them, because it is what finds the sixth.</p>
 *
 * <h2>The rule, and what it deliberately does not claim</h2>
 *
 * <p>A sort is flagged when its <em>only</em> key is one this codebase knows to be non-unique —
 * {@code ordinal} and {@code position}, both of which are curator-editable numbers nothing forbids
 * from colliding. Sorting by a name, an id, an address or a URI is left alone: those are unique by
 * construction, and demanding a tiebreaker for them would be noise that gets a lint switched off.
 * It is a narrow, declared list rather than a guess at which fields might repeat, because the honest
 * version of "any non-unique key" needs a schema this test cannot read.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShUnstableSortLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /**
     * Keys this codebase knows repeat.
     *
     * <p>Both are numbers a curator edits: {@code ordinal} orders a post-type's fields and
     * {@code position} orders a folder's children, and nothing in the schema or the write path
     * forbids two rows sharing one. The defect is not that they repeat — it is that repeating makes
     * the sort's output depend on a {@code HashSet}'s iteration order.</p>
     */
    private static final List<String> NON_UNIQUE_KEYS = List.of("getOrdinal", "getPosition");

    @Test
    @DisplayName("SH722: no sort is ordered by a non-unique key alone")
    void everySortByANonUniqueKeyHasATiebreaker() {
        List<String> violations = new ArrayList<>();
        for (Path source : javaFilesUnder(MAIN)) {
            violations.addAll(unstableSortsIn(source.getFileName().toString(), read(source)));
        }

        assertThat(violations)
                .withFailMessage("""
                        These sorts order by a key that is not unique, and nothing breaks the tie:
                        %s
                        The input is a Set the persistence layer hands over in HASH order, so two \
                        rows sharing the key come back in whichever order it iterated this time. \
                        That is how ten identical shio_context calls returned two distinct packs \
                        (SH714) — and the pack carries its own digest, so P9's cheap proof flipped \
                        with it.
                        Fix: .thenComparing(...) on something unique — the name, or the id. \
                        ShAgentRenderService and ShAgentContextService already do it, which is why \
                        this lint exists rather than one more repair (SH322).""",
                        String.join("\n", violations))
                .isEmpty();
    }

    @Test
    @DisplayName("SH722: the lint reads the shape it is about, and not the prose around it")
    void theLintDetectsTheShapeAndNotItsDescription() {
        // Guards the guard, twice over. A scan that matches nothing is green for the wrong reason,
        // and one that matches a javadoc is SH206's defect — this file's own class comment names
        // `ordinal`, `sorted` and `thenComparing` in one paragraph.
        assertThat(unstableSortsIn("ShExample.java", """
                return attrs.stream()
                        .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal))
                        .map(this::toSchemaField)
                        .toList();
                """))
                .as("the exact line SH714 measured on the wire")
                .isNotEmpty();

        assertThat(unstableSortsIn("ShExample.java", """
                return attrs.stream()
                        .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                                .thenComparing(ShPostTypeAttr::getName))
                        .toList();
                """))
                .as("and the repaired form, which three call sites already had")
                .isEmpty();

        assertThat(unstableSortsIn("ShExample.java", """
                /** Sorted by getOrdinal with no thenComparing, which SH722 fixed. */
                """))
                .as("prose about the rule is not a breach of it (SH206, SH613)")
                .isEmpty();

        assertThat(unstableSortsIn("ShExample.java", """
                return types.stream().sorted(Comparator.comparing(ShPostType::getName)).toList();
                """))
                .as("a unique key needs no tiebreaker, and demanding one is how a lint gets muted")
                .isEmpty();
    }

    /**
     * Statements that sort by a non-unique key and break no tie.
     *
     * <p>Split on {@code ;} rather than parsed: a sort is one statement in every instance here, and
     * a chained {@code .thenComparing} cannot land in a different one. Comments are stripped first
     * by {@link ShSourceText}, so a paragraph explaining the rule is not read as breaking it.</p>
     */
    private static List<String> unstableSortsIn(String file, String text) {
        List<String> hits = new ArrayList<>();
        String code = ShSourceText.code(text);
        int line = 1;
        for (String statement : code.split(";", -1)) {
            boolean sorts = statement.contains(".sorted(") || statement.contains(".sort(");
            if (sorts && !statement.contains("thenComparing")) {
                for (String key : NON_UNIQUE_KEYS) {
                    if (statement.contains(key)) {
                        hits.add("  %s:%d — sorted by %s() with no tiebreaker"
                                .formatted(file, line + offsetOf(statement, key), key));
                        break;
                    }
                }
            }
            line += statement.length() - statement.replace("\n", "").length();
        }
        return hits;
    }

    /** Which line inside the statement names the key, so the message points at the sort. */
    private static int offsetOf(String statement, String key) {
        String before = statement.substring(0, Math.max(0, statement.indexOf(key)));
        return before.length() - before.replace("\n", "").length();
    }

    private static List<Path> javaFilesUnder(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
