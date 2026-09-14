/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.persistence.model.history.ShHistory;

/**
 * SH989 — the comparison an audit row records, and the bound that keeps it from refusing a write.
 *
 * <p>The row is the only place the answer survives: publishing makes the draft and the live row one
 * document, so a reader that recomputed the names after an approve would find none. That makes the
 * stored form the contract, and the width its first clause — SH544 was a width the schema knew and
 * the writer did not, and it ended a 201-page conversion with a 500.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShChangedFieldsTest {

    @Test
    @DisplayName("SH989: a changed, an added and a removed field are named, and an untouched one is not")
    void namesWhatDiffersAndNothingElse() {
        List<String> changed = ShChangedFields.between(
                "{\"title\":\"A\",\"body\":\"same\",\"old\":1}",
                "{\"title\":\"B\",\"body\":\"same\",\"new\":2}");

        assertThat(changed)
                .as("SH989: sorted, so the same write records the same bytes (P5)")
                .containsExactly("new", "old", "title");
    }

    @Test
    @DisplayName("SH989: against nothing, every field is new — a first publish")
    void againstNothingEveryFieldIsNew() {
        assertThat(ShChangedFields.between(null, "{\"title\":\"A\",\"body\":\"x\"}"))
                .containsExactly("body", "title");
    }

    @Test
    @DisplayName("SH989: a write that changed nothing records an empty list, not nothing")
    void anIdenticalDocumentIsAnEmptyListAndEncodesAsOne() {
        List<String> changed = ShChangedFields.between("{\"title\":\"A\"}", "{\"title\":\"A\"}");

        assertThat(changed).isEmpty();
        assertThat(ShChangedFields.encode(changed))
                .as("SH989: [] is recorded-and-unchanged; null is not-recorded. The readers show "
                        + "them differently, so the column has to keep them apart")
                .isEqualTo("[]");
        assertThat(ShChangedFields.encode(null)).isNull();
        assertThat(ShChangedFields.decode(null)).isNull();
        assertThat(ShChangedFields.decode("[]")).isEmpty();
    }

    @Test
    @DisplayName("SH989: a name nothing constrains survives the column — commas, quotes, non-ASCII")
    void aNameWithACommaRoundTrips() {
        List<String> names = List.of("a,b", "say \"hi\"", "título");

        assertThat(ShChangedFields.decode(ShChangedFields.encode(names)))
                .as("SH989: field names are a post's JSON keys, which is why the column is a JSON "
                        + "array and not a comma list")
                .containsExactlyElementsOf(names);
    }

    @Test
    @DisplayName("SH989: a list wider than the column keeps the names that fit and counts the rest")
    void aListWiderThanTheColumnIsBoundedAndSaysSo() {
        List<String> names = IntStream.range(0, 400)
                .mapToObj(i -> "a_rather_long_field_name_%03d".formatted(i)).toList();

        String stored = ShChangedFields.encode(names);

        assertThat(stored).hasSizeLessThanOrEqualTo(ShHistory.CHANGED_FIELDS_MAX_LENGTH);
        List<String> read = ShChangedFields.decode(stored);
        String marker = read.get(read.size() - 1);
        assertThat(marker).startsWith(ShChangedFields.MORE);
        int omitted = Integer.parseInt(marker.substring(ShChangedFields.MORE.length()));
        assertThat(read.subList(0, read.size() - 1))
                .as("SH989: the names kept are the first ones, whole")
                .containsExactlyElementsOf(names.subList(0, read.size() - 1));
        assertThat(read.size() - 1 + omitted)
                .as("SH989: kept plus counted is every name — a cut that loses the count reads as "
                        + "a short list")
                .isEqualTo(names.size());
    }

    @Test
    @DisplayName("SH989: the bound holds at every length near the column's edge")
    void theBoundHoldsAtEveryLength() {
        for (int count = 1; count < 120; count++) {
            for (int width : new int[] { 1, 7, 30, 200, 1100 }) {
                List<String> names = IntStream.range(0, count)
                        .mapToObj(i -> String.valueOf((char) ('a' + i % 26)).repeat(width) + i)
                        .toList();
                String stored = ShChangedFields.encode(names);
                assertThat(stored)
                        .as("%d names of width %d", count, width)
                        .hasSizeLessThanOrEqualTo(ShHistory.CHANGED_FIELDS_MAX_LENGTH);
                assertThat(ShChangedFields.decode(stored)).isNotEmpty();
            }
        }
    }

    /**
     * The constant and the schema agree — SH544's second half, for this column. Asserted against the
     * changelog because {@code ddl-auto=none}: the annotation agreeing with the constant proves only
     * that the entity agrees with itself.
     */
    @Test
    @DisplayName("SH989: the writer's bound is the width Liquibase declares")
    void theConstantIsTheWidthTheChangelogDeclares() throws IOException {
        Path changelogDir = Paths.get("src/main/resources/db/changelog");
        Pattern width = Pattern.compile(
                "name:\\s*changed_fields\\s*\\R\\s*type:\\s*VARCHAR\\((\\d+)\\)");
        List<Integer> declared = new ArrayList<>();
        try (var paths = Files.walk(changelogDir)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(each -> each.getFileName().toString().endsWith(".yaml")).toList()) {
                for (String block : Files.readString(path, StandardCharsets.UTF_8).split("- changeSet:")) {
                    if (!block.contains("tableName: ShHistory")) {
                        continue;
                    }
                    Matcher matcher = width.matcher(block);
                    while (matcher.find()) {
                        declared.add(Integer.parseInt(matcher.group(1)));
                    }
                }
            }
        }

        assertThat(declared)
                .as("no ShHistory.changed_fields column found in the changelog — has it moved?")
                .isNotEmpty()
                .containsOnly(ShHistory.CHANGED_FIELDS_MAX_LENGTH);
    }
}
