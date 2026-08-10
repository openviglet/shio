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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH279 — picking one row out of an unordered {@code Set} is a P5 violation you cannot see.
 *
 * <h2>Why a lint and not a test</h2>
 *
 * <p>{@code findByShSiteAndRootFolder} returns a {@code Set}, and eight call sites took an element
 * from it — four of them to answer "the site's root folder", which decides where a post is created
 * (the post writer), what a CDA listing describes, and what an address resolves to. A {@code Set}
 * has no order, so all four were asking the database to choose, and SH278 established what that
 * costs: <b>the wrong answer is invisible on H2 and appears on somebody else's engine</b>. No
 * behavioural test in this suite could have caught it, because the suite runs on the engine that
 * happened to be kind.</p>
 *
 * <p>So the guard is textual, like {@code ShFolderSiteInvariantTest} for SH128's site invariant and
 * {@code ShByteStabilityLintTest} for SH136's iteration order. It permits <em>iterating</em> the set
 * — the exchange export and the console's write service legitimately walk every root — and refuses
 * <em>picking</em> from it, which is the operation that has no defined answer.</p>
 *
 * <p>The bar SH279 sets is two questions: <em>does anything guarantee uniqueness, and is that
 * guarantee enforced?</em> A site has one root by construction (SH128), so the answer is "yes, and
 * nothing checks it" — and a guarantee nobody can point at is the same as none, which is what SH263
 * demonstrated when a title that was unique in practice turned out not to be.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShRootFolderReaderLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /**
     * The finder, then a pick within the same statement.
     *
     * <p>{@code DOTALL} with a bounded gap rather than a line match, because the pick is usually on
     * the next line — {@code .stream()} then {@code .findFirst()} — and a line-oriented scan would
     * miss every real occurrence, which is the shape of lint that reports zero forever.</p>
     */
    private static final Pattern PICKS_FROM_THE_SET = Pattern.compile(
            "findByShSiteAndRootFolder\\s*\\([^)]*\\)[^;]{0,120}?"
                    + "(\\.findFirst\\s*\\(|\\.get\\s*\\(\\s*0\\s*\\)|\\.min\\s*\\(|\\.iterator\\s*\\()",
            Pattern.DOTALL);

    @Test
    @DisplayName("nothing picks a single root folder out of the unordered Set")
    void nobodyPicksFromTheUnorderedSet() {
        List<String> offenders = new ArrayList<>();
        sources().forEach((file, body) -> {
            if (file.equals("ShFolderRepository.java")) {
                // Where the ordering lives. `findRootFolder` is the one reader allowed to pick,
                // because it is the one that sorts first.
                return;
            }
            Matcher matcher = PICKS_FROM_THE_SET.matcher(body);
            if (matcher.find()) {
                offenders.add(file + " (" + matcher.group(1).trim() + ")");
            }
        });

        assertThat(offenders)
                .as("""
                        a Set has no order, so picking from it asks the database to choose — and \
                        SH278 showed what that costs: invisible on H2, wrong on PostgreSQL, and \
                        unreachable by any behavioural test the suite can run. Use \
                        `shFolderRepository.findRootFolder(site)`, which orders before it picks. \
                        Iterating every root is fine and this lint allows it.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the shared reader still orders before it picks")
    void theSharedReaderOrders() {
        String repository = sources().get("ShFolderRepository.java");

        assertThat(repository)
                .as("""
                        the lint above sends every caller here, so this is the one place the \
                        ordering can be removed without anything failing — which is exactly why \
                        it is asserted rather than trusted (SH275's habit: a premise that holds \
                        because somebody remembers it is a premise waiting to stop holding).""")
                .contains("default java.util.Optional<ShFolder> findRootFolder")
                .contains(".min(java.util.Comparator.comparing(ShFolder::getId))");
    }

    private static java.util.Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            java.util.Map<String, String> byName = new java.util.TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(), read(path)));
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return ShSourceText.code(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
