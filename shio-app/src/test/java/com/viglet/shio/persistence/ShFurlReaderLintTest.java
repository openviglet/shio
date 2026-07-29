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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH323 — a friendly URL is not unique by construction, so no <em>derived</em> finder over it may
 * return an {@code Optional}.
 *
 * <h2>Why a lint, and why before the fix</h2>
 *
 * <p>Spring Data implements a derived {@code Optional} finder as "expect at most one row", and
 * throws {@code IncorrectResultSizeDataAccessException} when the database returns two. Nothing in
 * the schema keeps two posts in one site off one {@code furl} — that is why
 * {@code duplicate-url} has been an SH99 <b>error</b> since SH100 — so the finder turns a content
 * defect the instance itself reports into a <b>500</b>: the server knowing exactly what happened
 * and answering with the least useful thing available.</p>
 *
 * <p>SH321 fixed the state-keyed finder and eight call sites. It missed the locale-keyed one, which
 * is the same declaration one parameter along, and the miss survived a whole block. That is
 * SH322's habit in miniature: <b>when a task's body says "and whatever a grep turns up", the grep
 * is the deliverable and it lands as a lint before the fixes do</b> — so this exists to catch the
 * <em>third</em> one, which will be added by somebody who has never read either task.</p>
 *
 * <p>The guard is textual for {@code ShRootFolderReaderLintTest}'s reason: no behavioural test can
 * watch for a finder that does not exist yet, and the failure arrives in production as a 500 rather
 * than anywhere a fixture would look.</p>
 *
 * @author Alexandre Oliveira
 */
class ShFurlReaderLintTest {

    private static final Path REPOSITORIES =
            Path.of("src", "main", "java", "com", "viglet", "shio", "persistence", "repository");

    /**
     * An {@code Optional} return over a {@code Furl} query method.
     *
     * <p>{@code default} is the escape hatch and the only one: a default method <em>reads</em> the
     * ordered {@code List} finder and picks deterministically, so its {@code Optional} is a
     * decision rather than an assumption about the data. Everything else on this pattern is a
     * derived query Spring Data will translate into a single-result expectation.</p>
     */
    private static final Pattern OPTIONAL_QUERY_METHOD = Pattern.compile(
            "Optional<\\w+>\\s+(?:find|get|read)\\w*\\(");

    /**
     * True for a query method Spring Data will derive, false for a {@code default} that picks.
     *
     * <p>The {@code default} test is a separate string check rather than a lookahead in the pattern
     * because a lookahead anchored to the line start can be defeated by backtracking over the
     * indentation — the regex then matches the very declaration the hatch exists to permit, and a
     * lint that flags its own recommended form is one the next person deletes.</p>
     */
    private static boolean isDerivedOptionalOverFurl(String code) {
        return OPTIONAL_QUERY_METHOD.matcher(code).find()
                && code.contains("Furl")
                && !code.contains("default ");
    }

    @Test
    @DisplayName("no derived Optional finder resolves a friendly URL")
    void noDerivedOptionalFinderOverFurl() {
        List<String> offenders = new ArrayList<>();
        sources().forEach((file, body) -> {
            int lineNumber = 0;
            for (String line : body.split("\r?\n")) {
                lineNumber++;
                String code = stripComment(line);
                if (isDerivedOptionalOverFurl(code)) {
                    offenders.add(file + ":" + lineNumber + " —" + code.stripTrailing());
                }
            }
        });

        assertThat(offenders)
                .as("""
                        a derived Optional finder over `furl` asks Spring Data to expect at most \
                        one row, and two posts on one URL is a content defect `duplicate-url` \
                        already reports — so the finder answers 500 for something the instance can \
                        explain. Declare `List<ShPostUnified> findBy...FurlAnd...OrderByIdAsc` and \
                        a `default` Optional over it: a reader that must serve something serves \
                        the same row every time (SH278), and a *write* path refuses instead \
                        (ShAddressResolver.DuplicateUrlException).""")
                .isEmpty();
    }

    @Test
    @DisplayName("the lint matches the form that answered 500, and not the deliberate pick")
    void theLintDetectsTheFormThatBrokeDelivery() {
        // Guards the guard: a lint that silently matches nothing is worse than none. Both strings
        // are the real declarations, before and after SH321/SH323.
        assertThat(isDerivedOptionalOverFurl(
                "    Optional<ShPostUnified> findByShSite_IdAndFurlAndStateAndLocale(String siteId,"))
                .isTrue();
        assertThat(isDerivedOptionalOverFurl(
                "    Optional<ShPostUnified> findByShSite_IdAndFurlAndState(String siteId,"))
                .isTrue();

        assertThat(isDerivedOptionalOverFurl(
                "    default Optional<ShPostUnified> findByShSite_IdAndFurlAndState(String siteId,"))
                .as("a default method picks from the ordered list — that Optional is a decision")
                .isFalse();
        assertThat(isDerivedOptionalOverFurl(
                "    List<ShPostUnified> findByShSite_IdAndFurlAndStateOrderByIdAsc(String siteId,"))
                .isFalse();
        assertThat(isDerivedOptionalOverFurl(
                "    Optional<ShPostUnified> findByIdAndState(String id, ShPostState state);"))
                .as("an id is unique by construction *and* by primary key — that is the one "
                        + "guarantee SH279's two questions both answer yes to")
                .isFalse();
    }

    @Test
    @DisplayName("both URL readers still order before they pick")
    void theSharedReadersOrder() {
        String repository = sources().get("ShPostUnifiedRepository.java");

        assertThat(repository)
                .as("""
                        the lint above sends every caller to these two, so this is the one place \
                        the ordering can be removed without anything failing — which is exactly \
                        why it is asserted rather than trusted (SH275's habit). Without \
                        OrderByIdAsc a duplicate resolves to a different post per request, which \
                        reads as flakiness in delivery rather than as the defect `verify` names.""")
                .contains("List<ShPostUnified> findByShSite_IdAndFurlAndStateOrderByIdAsc(")
                .contains("List<ShPostUnified> findByShSite_IdAndFurlAndStateAndLocaleOrderByIdAsc(");
    }

    /** Drops comment lines so the rule can be described in a javadoc without failing the build. */
    private static String stripComment(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("*") || trimmed.startsWith("/*") || trimmed.startsWith("//")) {
            return "";
        }
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private static Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(REPOSITORIES)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(), read(path)));
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
