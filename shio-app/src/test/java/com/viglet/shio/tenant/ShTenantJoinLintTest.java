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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH122 — build-time guard for the SH22 {@code @TenantId} join hazard.
 *
 * <p>{@code @TenantId} lives on the JOINED root {@code ShObject}. Reaching <b>past
 * the {@code id}</b> of an association whose target is one of its subclasses
 * ({@code ShSite}, {@code ShFolder}, {@code ShPostType}) makes Hibernate join only
 * the subclass table while still emitting the tenant predicate against a root alias
 * it never declared — {@code Column "spt1_1.tenant_id" not found}, at statement
 * preparation. Characterized in {@link ShTenantJoinHazardTest}.</p>
 *
 * <p><b>Why a textual lint.</b> The mapping is valid JPQL and the failure is
 * runtime-only, so nothing catches it at compile time; and it is invisible to tests
 * unless one happens to reach that finder. Three shipped CDA paths were broken this
 * way for exactly that reason — a documented endpoint
 * ({@code /cda/query?postType=}) and the site-scoped search path — while the
 * identical fix sat three lines above them in the same file. A cheap, always-on
 * textual guard is the right shape for that class of defect, in the same spirit as
 * {@code ShLiquibaseChangelogAgnosticTest}.</p>
 *
 * <p><b>The rule:</b> in repository sources, neither a {@code @Query} string nor a
 * derived method name may navigate an {@code ShObject}-subclass association to
 * anything other than {@code id}. The safe form uses the FK column already present
 * on the owning table — no join, no tenant predicate, and faster.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShTenantJoinLintTest {

    private static final Path REPOSITORY_ROOT =
            Path.of("src/main/java/com/viglet/shio/persistence/repository");

    /**
     * Associations whose target is an {@code ShObject} subclass. Property names as
     * they appear in JPQL; the derived-name form is the same word capitalized.
     */
    private static final List<String> HAZARDOUS_ASSOCIATIONS =
            List.of("shPostType", "shSite", "shFolder", "shParentPostTypeAttr");

    /** {@code p.shPostType.name} — navigation to a property other than {@code id}. */
    private static final Pattern JPQL_NAVIGATION = Pattern.compile(
            "\\.(" + String.join("|", HAZARDOUS_ASSOCIATIONS) + ")\\.(?!id\\b)(\\w+)");

    /**
     * {@code findByShPostType_Name…} — the derived-query spelling of the same thing.
     *
     * <p>Word boundaries are useless on both sides here, and assuming otherwise cost
     * two attempts. <b>Before</b> the association: a derived name glues it to
     * {@code By}/{@code And} ({@code findByShSite_IdAndShPostType_Name}), so there is
     * no boundary to anchor on. <b>After</b> {@code Id}: the next property starts with
     * a <em>capital</em>, so {@code Id\b} does not hold in {@code _IdAndState} and
     * every safe finder would be flagged. Hence no leading {@code \b}, an
     * {@code Id\b|Id[A-Z_]} tail, and no {@code (?i)} — the segment after {@code _} is
     * always capitalized.</p>
     */
    private static final Pattern DERIVED_NAVIGATION = Pattern.compile(
            "(" + String.join("|", capitalized(HAZARDOUS_ASSOCIATIONS))
                    + ")_(?!Id\\b|Id[A-Z_])(\\w+)");

    private static List<String> capitalized(List<String> names) {
        return names.stream()
                .map(name -> Character.toUpperCase(name.charAt(0)) + name.substring(1))
                .toList();
    }

    @Test
    @DisplayName("no repository query navigates past the id of an ShObject-subclass association")
    void noRepositoryQueryNavigatesPastAnAssociationId() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(REPOSITORY_ROOT)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> violations.addAll(scan(path)));
        }

        assertThat(violations)
                .withFailMessage("""
                        These repository queries navigate past the id of an association whose \
                        target is an ShObject subclass, which carries SH22's @TenantId on the \
                        JOINED root. Hibernate will emit the tenant predicate against an alias \
                        it never joined and the query dies at prepare time:
                        %s
                        Fix: filter/select the association's **id** (the FK column is already on \
                        the owning table — no join at all) and resolve id -> name in the service. \
                        See ShTenantJoinHazardTest, agents.md -> Invariants that fail silently, \
                        and docs/agents/database.md.""",
                        String.join("\n", violations))
                .isEmpty();
    }

    private static List<String> scan(Path source) {
        List<String> violations = new ArrayList<>();
        String text;
        try {
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + source, e);
        }
        int lineNumber = 0;
        for (String line : text.split("\n", -1)) {
            lineNumber++;
            String code = ShSourceText.codeLine(line);
            if (code.isBlank()) {
                continue;
            }
            // A native query is exempt: it names real columns and Hibernate adds no
            // tenant predicate to it (which is why the SH55 trash queries pass the
            // tenant explicitly).
            if (code.contains("nativeQuery = true")) {
                continue;
            }
            record(violations, source, lineNumber, code, JPQL_NAVIGATION);
            if (code.contains("find") || code.contains("count") || code.contains("delete")) {
                record(violations, source, lineNumber, code, DERIVED_NAVIGATION);
            }
        }
        return violations;
    }

    private static void record(List<String> violations, Path source, int lineNumber,
                               String code, Pattern pattern) {
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            violations.add("  %s:%d — %s.%s (use %s.id instead)"
                    .formatted(source.getFileName(), lineNumber,
                            matcher.group(1), matcher.group(2), matcher.group(1)));
        }
    }

    @Test
    void theLintDetectsTheFormThatBrokeProduction() {
        // Guards the guard: a lint that silently matches nothing is worse than none.
        assertThat(JPQL_NAVIGATION.matcher(
                "select p.shPostType.name, count(p) from ShPostUnified p").find()).isTrue();
        assertThat(DERIVED_NAVIGATION.matcher(
                "Page<ShPostUnified> findByShSite_IdAndShPostType_NameAndState(").find()).isTrue();

        // And does not flag the safe forms — including a mid-name `_Id`, where the next
        // property begins with a capital rather than a word boundary.
        assertThat(JPQL_NAVIGATION.matcher(
                "select p.shPostType.id from ShPostUnified p").find()).isFalse();
        assertThat(DERIVED_NAVIGATION.matcher(
                "List<ShPostUnified> findByShPostType_IdAndState(").find()).isFalse();
        assertThat(DERIVED_NAVIGATION.matcher(
                "findByShSite_IdAndShPostType_IdAndStateIn(String siteId").find()).isFalse();
        assertThat(DERIVED_NAVIGATION.matcher(
                "Optional<ShPostUnified> findByShSite_IdAndFurlAndState(").find()).isFalse();
        assertThat(JPQL_NAVIGATION.matcher(
                "select p from ShPostUnified p where p.state = :state").find()).isFalse();
    }
}
