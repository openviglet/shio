/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH347 — <b>the Spring test context cache has a ceiling, and it must not be invisible.</b>
 *
 * <h2>The failure this is written from</h2>
 *
 * <p>{@code ShAgentDurableIncidentTest#theCapDropsTheOldest} failed in the full suite and passed both
 * in isolation and paired with the test whose arrival broke it. There was no defect behind it:
 * SH315's isolated-datasource rule had produced <b>33 context keys against Spring's default
 * {@code maxSize} of 32</b>, an evicted context was rebuilt, {@code ddl-auto=create-drop} recreated
 * the schema mid-run, and rows a later test in the <em>shared</em> datasource was asserting about were
 * gone.</p>
 *
 * <p>That is the worst kind of red — order-dependent, with nothing wrong — and SH250's rule applies:
 * a flake is worse than a break, because it teaches people that red might mean nothing. Raising the
 * ceiling fixed the run; it did not make the ceiling <em>visible</em>, which is what this test is
 * for. Same shape as the token budgets: a number nobody can see is a number that surprises somebody.</p>
 *
 * <h2>Two assertions, and the first one is the important one</h2>
 *
 * <p>The ceiling must be <b>configured</b>, not defaulted. The whole defect was a limit nobody had
 * chosen: the default is a property of the framework version and can change under the project without
 * a line of this repository moving. And then the headroom: isolated contexts plus a margin must fit,
 * so the run that would have gone red announces itself while there is still room.</p>
 *
 * <h2>What an isolated context costs, measured (SH317)</h2>
 *
 * <p>SH315's rule has a price and nobody had counted it. It is <b>~1.5 seconds</b> per isolated
 * context — measured by adding a probe class that declared a datasource of its own and did nothing
 * but boot: surefire reported <b>1.585 s</b> and <b>1.390 s</b> for it on two full runs, against
 * 0.002 s for a class with no Spring context at all. At {@value #SECONDS_PER_CONTEXT} s each, the
 * {@code isolatedDatabases()} count below is about <b>two fifths of a ~2 minute suite</b>.</p>
 *
 * <p><b>The probe was deleted rather than kept</b>, which is the whole finding in miniature: a class
 * that exists only to measure the cost of a context would permanently consume the scarce thing this
 * test guards, and crossing that ceiling costs correctness rather than seconds. So the number is a
 * dated constant with its recipe beside it — SH276's shape for a premise that cannot be asserted
 * cheaply — and it is <em>printed</em> beside the count, because a cost nobody sees is a cost nobody
 * weighs.</p>
 *
 * <p><b>Do not read this from the suite's wall clock.</b> Two runs of the identical tree came in at
 * 2:09 and 2:30, so the total is ±20 s of noise and the one-context delta is invisible in it. That is
 * SH283's lesson one level along: the number has to come from the run <em>and</em> from the line that
 * actually measures it.</p>
 *
 * <p>The other direction — share a context but not a database — stays closed: the datasource URL is
 * part of the context cache key, so two URLs are two contexts by construction. Changing that means a
 * schema per test class inside one URL, or a reset between classes, and both trade a 1.5 s boot for a
 * cleanup that has to be exhaustive to be safe. At this price that trade is not worth making; the
 * number above is what a future proposal has to beat.</p>
 *
 * <h2>Declared limit: this counts a lower bound</h2>
 *
 * <p>A context key is the whole property set, not the datasource URL — two classes with the same URL
 * and different other properties are two contexts, and this counts them as one. So the guard warns
 * <em>late</em> rather than early, which is stated here rather than left to be discovered, because a
 * guard that quietly under-reports is the failure mode this repository keeps finding (SH273's
 * grammar table, SH322's grep). The margin is what covers the gap; widen it before widening the
 * counter.</p>
 *
 * @author Alexandre Oliveira
 */
class ShContextCacheHeadroomTest {

    private static final Path TEST_SOURCES = Path.of("src", "test", "java");
    private static final Path TEST_PROPERTIES =
            Path.of("src", "test", "resources", "application.properties");
    private static final String MAX_SIZE = "spring.test.context.cache.maxSize";

    /**
     * How many context keys the counter may miss, plus room to add tests without a red build.
     *
     * <p>Eight rather than one, for the declared limit above: the count is a lower bound, and a
     * margin that only covered "one more test" would let the real number cross while this passed.</p>
     */
    private static final int MARGIN = 8;

    /**
     * Seconds one isolated Spring context costs, measured 2026-08-02 (SH317).
     *
     * <p>To re-measure: add a {@code @SpringBootTest} class with a {@code jdbc:h2:mem:} URL of its
     * own and a single trivial assertion, run the full suite, and read <em>its</em> surefire
     * {@code Time elapsed} — not the suite total, which is ±20 s of noise. Then delete it.</p>
     */
    private static final double SECONDS_PER_CONTEXT = 1.5;

    /** Both spellings: the annotation literal and SH22's dynamic-property form. */
    private static final Pattern ISOLATED_DATASOURCE = Pattern.compile(
            "jdbc:h2:mem:([\\w-]+)");

    @Test
    @DisplayName("SH347: the context-cache ceiling is chosen here, not inherited from the framework")
    void theCeilingIsConfigured() throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(TEST_PROPERTIES)) {
            properties.load(in);
        }

        assertThat(properties.getProperty(MAX_SIZE))
                .as("""
                        %s is unset, so the ceiling is whatever this Spring version defaults to — \
                        which is exactly the state that produced an order-dependent failure with no \
                        defect behind it (SH347). A limit nobody chose is a limit nobody will \
                        recognise when it is crossed, and the default can move under the project \
                        without a line here changing.""".formatted(MAX_SIZE))
                .isNotNull();
    }

    @Test
    @DisplayName("SH347: the isolated contexts still fit under it, with room to spare")
    void theIsolatedContextsFit() throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(TEST_PROPERTIES)) {
            properties.load(in);
        }
        int ceiling = Integer.parseInt(properties.getProperty(MAX_SIZE, "32").trim());
        Set<String> isolated = isolatedDatabases();

        // Locale.ROOT: a build log that reads "1,5 s" on one machine and "1.5 s" on another is the
        // same defect as an unstable iteration order (P5), and it is the one printf inherits.
        System.out.printf(java.util.Locale.ROOT,
                "%n[SH347] %d isolated test databases, ceiling %d, margin %d%n"
                        + "[SH317] about %.0f s of the run, at ~%.1f s a context (measured "
                        + "2026-08-02)%n  %s%n%n",
                isolated.size(), ceiling, MARGIN,
                isolated.size() * SECONDS_PER_CONTEXT, SECONDS_PER_CONTEXT, isolated);

        assertThat(isolated.size() + MARGIN)
                .as("""
                        %d isolated databases against a %s of %d. Each is at least one Spring \
                        context, and crossing the ceiling does not merely cost time: an evicted \
                        context is rebuilt with ddl-auto=create-drop, which recreates the schema \
                        mid-run and wipes rows a later test in the shared datasource is asserting \
                        about. The symptom is a test failing only in the full suite.

                        Raise %s and say why, or ask whether the newest isolated test needs to be \
                        (SH315's rule is that a test measuring the *whole instance* may not share \
                        it — a test that only reads its own fixtures may).""".formatted(
                                isolated.size(), MAX_SIZE, ceiling, MAX_SIZE))
                .isLessThanOrEqualTo(ceiling);
    }

    @Test
    @DisplayName("the guard is not vacuous: the counter finds the databases it should")
    void theCounterFindsThem() {
        Set<String> isolated = isolatedDatabases();

        assertThat(isolated)
                .as("a counter that came back empty would pass forever while the real count grew — "
                        + "the lint-reports-zero failure this repository writes every guard against")
                .contains("shio-benchmark", "shio-naive", "shio-schedule")
                .hasSizeGreaterThanOrEqualTo(20);
        assertThat(ISOLATED_DATASOURCE.matcher(
                "\"spring.datasource.url=jdbc:h2:mem:shio-x;DB_CLOSE_DELAY=-1\"").find()).isTrue();
        assertThat(ISOLATED_DATASOURCE.matcher(
                "registry.add(\"spring.datasource.url\", () -> \"jdbc:h2:mem:shio-y\")").find())
                .as("the dynamic form too — SH22's cross-DB tests use it, and a counter blind to it "
                        + "would under-report exactly where the count is growing")
                .isTrue();
    }

    private static Set<String> isolatedDatabases() {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                Matcher matcher = ISOLATED_DATASOURCE.matcher(read(path));
                while (matcher.find()) {
                    names.add(matcher.group(1));
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // This test's own guard-the-guard strings would otherwise be counted as real databases.
        names.remove("shio-x");
        names.remove("shio-y");
        return names;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
