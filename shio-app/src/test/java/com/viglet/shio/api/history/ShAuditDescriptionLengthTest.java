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

import org.junit.jupiter.api.Test;

import com.viglet.shio.persistence.model.history.ShHistory;

/**
 * The assertion SH544 did not have: an audit description never exceeds the column that stores it.
 *
 * <p>What failed was not the row. {@code ShHistory.description} is {@code VARCHAR(255)} and the
 * writer composed {@code Created "<title>"} with nothing between them, so five of one site's 198
 * page titles — ordinary marketing sentences, each over 245 characters — ended a 201-page
 * conversion with a 500, <em>after</em> 403 assets had been uploaded. The trail exists to describe
 * a write; it was deciding whether the write happened.</p>
 *
 * <p>Two halves, and the second is the one that keeps this true tomorrow: the bound holds for any
 * title, and the constant the writer truncates against is the width the <b>changelog</b> declares.
 * Asserting it against the {@code @Column} annotation would only prove the entity agrees with
 * itself — {@code ddl-auto=none}, so the schema is Liquibase's.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShAuditDescriptionLengthTest {

    private static final int MAX = ShHistory.DESCRIPTION_MAX_LENGTH;

    @Test
    void shortTitleIsUntouched() {
        assertThat(ShContentAuditService.describe(ShContentAction.CREATE, "About us", null))
                .isEqualTo("Created \"About us\"");
        assertThat(ShContentAuditService.describe(ShContentAction.UPDATE, "About us", "renamed"))
                .isEqualTo("Updated \"About us\" (renamed)");
    }

    @Test
    void blankTitleStillReads() {
        assertThat(ShContentAuditService.describe(ShContentAction.DELETE, "  ", null))
                .isEqualTo("Deleted \"(untitled)\"");
    }

    /** The exact shape of SH544: a title longer than the column, with no detail. */
    @Test
    void aTitleLongerThanTheColumnIsElidedRatherThanRefused() {
        String title = "A ".repeat(400);

        String described = ShContentAuditService.describe(ShContentAction.CREATE, title, null);

        assertThat(described).hasSizeLessThanOrEqualTo(MAX);
        assertThat(described).startsWith("Created \"A A ");
        assertThat(described).endsWith("…\"");
    }

    /**
     * The title gives way before the shape does — {@code detail} is what an SH196 reader needs to
     * tell a rename from a move, and it survives.
     */
    @Test
    void detailSurvivesAnOverlongTitle() {
        String described = ShContentAuditService.describe(
                ShContentAction.UPDATE, "x".repeat(600), "moved from folder:Blog/drafts");

        assertThat(described).hasSizeLessThanOrEqualTo(MAX);
        assertThat(described).endsWith("…\" (moved from folder:Blog/drafts)");
    }

    /** When the detail alone leaves no usable room, the whole line is cut instead. */
    @Test
    void aDetailWiderThanTheColumnIsBoundedToo() {
        String described = ShContentAuditService.describe(
                ShContentAction.UPDATE, "About us", "d".repeat(600));

        assertThat(described).hasSizeLessThanOrEqualTo(MAX);
        assertThat(described).startsWith("Updated \"About us\" (ddd");
        assertThat(described).endsWith("…");
    }

    /**
     * A page title with an emoji is not two half-characters. Cutting between a surrogate pair
     * stores something no database hands back as it was given, so the cut moves rather than splits.
     */
    @Test
    void theCutNeverSplitsASurrogatePair() {
        for (int pad = 0; pad < 8; pad++) {
            String title = "a".repeat(pad) + "🌊".repeat(400);

            String described = ShContentAuditService.describe(ShContentAction.CREATE, title, null);

            assertThat(described).hasSizeLessThanOrEqualTo(MAX);
            // codePoints() folds a valid pair into one code point, so only a *dangling* half
            // still reads as a surrogate here.
            assertThat(described.codePoints()
                    .anyMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE))
                    .as("a dangling surrogate at pad=%d: %s", pad, described)
                    .isFalse();
        }
    }

    /** Every action's verb composes a line that fits, for a title of any length. */
    @Test
    void everyActionStaysWithinTheColumn() {
        for (ShContentAction action : ShContentAction.values()) {
            for (int length : new int[] { 0, 1, 240, 245, 254, 255, 256, 4000 }) {
                String described = ShContentAuditService.describe(action, "t".repeat(length), null);
                assertThat(described)
                        .as("%s with a %d-character title", action, length)
                        .hasSizeLessThanOrEqualTo(MAX);
            }
        }
    }

    /**
     * The constant and the schema agree. This is the assertion that would have caught SH544 being
     * re-introduced by a widening of the column that nobody told the writer about — or a narrowing
     * of it, which is the same defect facing the other way.
     */
    @Test
    void theConstantIsTheWidthTheChangelogDeclares() throws IOException {
        List<Integer> declared = declaredDescriptionWidths();

        assertThat(declared)
                .as("no ShHistory.description column found in the changelog — has the table moved?")
                .isNotEmpty();
        assertThat(declared)
                .as("ShHistory.DESCRIPTION_MAX_LENGTH must be the width Liquibase declares")
                .containsOnly(MAX);
    }

    /** Every {@code VARCHAR(n)} declared for {@code description} inside a {@code ShHistory} block. */
    private static List<Integer> declaredDescriptionWidths() throws IOException {
        Path changelogDir = Paths.get("src/main/resources/db/changelog");
        assertThat(Files.isDirectory(changelogDir))
                .as("Expected changelog directory at %s", changelogDir)
                .isTrue();
        Pattern width = Pattern.compile(
                "name:\\s*description\\s*\\R\\s*type:\\s*VARCHAR\\((\\d+)\\)");
        List<Integer> widths = new ArrayList<>();
        try (var paths = Files.walk(changelogDir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".yaml") && !name.endsWith(".yml")) {
                    continue;
                }
                String content = Files.readString(path, StandardCharsets.UTF_8);
                for (String block : content.split("- changeSet:")) {
                    if (!block.contains("tableName: ShHistory")) {
                        continue;
                    }
                    Matcher matcher = width.matcher(block);
                    while (matcher.find()) {
                        widths.add(Integer.parseInt(matcher.group(1)));
                    }
                }
            }
        }
        return widths;
    }
}
