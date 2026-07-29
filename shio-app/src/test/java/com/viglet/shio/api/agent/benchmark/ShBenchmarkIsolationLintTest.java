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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH315 — a test that measures the whole instance must not share it.
 *
 * <h2>Why this is a lint and not a convention</h2>
 *
 * <p>Five times a shared fixture has made one measurement an input to another, and every one was
 * caught by accident:</p>
 *
 * <ul>
 *   <li><b>SH239</b> — a benchmark pair wrote into the folder another pair measured, so one ratio
 *       read 26.2× or 11.8× depending on order.</li>
 *   <li><b>SH272</b> — a test asserting "this instance has no templates" was really asserting what
 *       ran first.</li>
 *   <li><b>SH289</b> — an additive-only assertion survived a mutation because a sibling test had
 *       already repaired the fixture.</li>
 *   <li><b>SH314</b> — two large fixture sites pushed a neighbouring benchmark's three-site
 *       {@code verify} number from 59 to 159, visible only because SH264 prints its numbers.</li>
 *   <li><b>SH315</b>, writing this — {@code ShAgentDiagnosticsTest} asserted {@code singleElement()}
 *       on an instance-wide diagnostics report from the shared context.</li>
 * </ul>
 *
 * <p>The convention was already documented on four classes, each with a comment explaining why it
 * boots a database of its own. A convention documented five times and broken five times is a lint
 * that has not been written yet — and the defect it prevents is invisible to every functional test,
 * because the code is correct and only the <em>measurement</em> is wrong.</p>
 *
 * <p>It reads source, like {@code ShCsrfExemptionLintTest} and {@code ShAgentIncidentFramesTest}:
 * the property is a string in an annotation, which is what a reflective check would have to parse
 * out of a resolved environment anyway.</p>
 *
 * @author Alexandre Oliveira
 */
class ShBenchmarkIsolationLintTest {

    private static final Path TESTS = Path.of("src", "test", "java", "com", "viglet", "shio");

    // Both spellings: the annotation's `properties = "spring.datasource.url=jdbc:..."` and the
    // dynamic form `registry.add("spring.datasource.url", () -> "jdbc:...")`, which the SH22
    // Testcontainers ITs use and which a property-string-only matcher reads as "no database of its
    // own" — a false positive that would have this lint teach the wrong fix.
    private static final Pattern DATASOURCE =
            Pattern.compile("spring\\.datasource\\.url.{0,40}?jdbc:h2:mem:([a-z0-9-]+)",
                    Pattern.DOTALL);

    /** An instance-wide read: what it answers depends on every other test's writes. */
    private static final List<String> INSTANCE_WIDE =
            List.of("verify(null,", "context(null,", "changes(null,");

    /**
     * Classes that read instance-wide from a shared database <em>on purpose</em>, with the reason.
     *
     * <p>Empty, and kept for the same reason SH300's read-but-never-emitted list is: the next
     * exception should be a declaration with a justification beside it, not a quiet edit to the
     * matcher. SH142's rule applies — an entry that stops being needed fails the build.</p>
     */
    private static final Map<String, String> SHARED_ON_PURPOSE = Map.of();

    @Test
    @DisplayName("a benchmark boots its own database")
    void everyBenchmarkIsolatesItsDatabase() {
        List<String> shared = new ArrayList<>();
        for (Map.Entry<String, String> source : sources("benchmark").entrySet()) {
            if (source.getValue().contains("@SpringBootTest")
                    && !DATASOURCE.matcher(source.getValue()).find()) {
                shared.add(source.getKey());
            }
        }

        assertThat(shared)
                .as("""
                        a benchmark on the shared datasource measures whatever every other test \
                        created — which is how SH239 read 26.2× or 11.8× depending on order, and \
                        how SH314's fixture pushed a neighbour's number from 59 to 159. Add a \
                        `spring.datasource.url=jdbc:h2:mem:<name>` of its own, with the other four \
                        classes' comment explaining why.""")
                .isEmpty();
    }

    @Test
    @DisplayName("a test that reads the whole instance boots its own database")
    void everyInstanceWideReaderIsolatesItsDatabase() {
        List<String> shared = new ArrayList<>();
        Set<String> declared = SHARED_ON_PURPOSE.keySet();
        for (Map.Entry<String, String> source : sources("").entrySet()) {
            String body = source.getValue();
            boolean reads = INSTANCE_WIDE.stream().anyMatch(body::contains);
            if (reads && body.contains("@SpringBootTest") && !DATASOURCE.matcher(body).find()
                    && !declared.contains(source.getKey())) {
                shared.add(source.getKey());
            }
        }

        assertThat(shared)
                .as("""
                        `verify(null, …)`, `context(null, …)` and `changes(null, …)` answer for the \
                        whole instance, so on the shared context their answer includes every other \
                        test's content. An assertion on a count or a single element is then a \
                        statement about suite order. Either scope the call to this test's own site, \
                        or give the class a database of its own.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no two test classes claim the same in-memory database")
    void everyDatabaseNameIsClaimedOnce() {
        Map<String, List<String>> byName = new TreeMap<>();
        sources("").forEach((file, body) -> {
            Matcher matcher = DATASOURCE.matcher(body);
            while (matcher.find()) {
                byName.computeIfAbsent(matcher.group(1), key -> new ArrayList<>()).add(file);
            }
        });
        List<String> collisions = byName.entrySet().stream()
                .filter(entry -> entry.getValue().stream().distinct().count() > 1)
                // The SH22 cross-database ITs share a name deliberately: they are one scenario in
                // several files, and the second is a continuation rather than an independent
                // measurement. `-it` is the marker, and it is the suffix Failsafe keys on too.
                .filter(entry -> !entry.getKey().endsWith("-it"))
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .toList();

        assertThat(collisions)
                .as("""
                        two classes on one in-memory database is the shared fixture again, wearing \
                        the isolation's clothes — and it is the easiest way for it to come back, \
                        since the fix for the last four was copied from class to class.""")
                .isEmpty();
    }

    // --- reading ---

    /** @param under a path fragment to filter by, or {@code ""} for the whole test tree */
    private static Map<String, String> sources(String under) {
        try (Stream<Path> files = Files.walk(TESTS)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith("Test.java")
                            || path.toString().endsWith("IT.java"))
                    .filter(path -> under.isEmpty() || path.toString().contains(under))
                    // Not itself: this file names every string it looks for, which is what a
                    // source lint always has to say about its own source.
                    .filter(path -> !path.getFileName().toString()
                            .equals("ShBenchmarkIsolationLintTest.java"))
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
