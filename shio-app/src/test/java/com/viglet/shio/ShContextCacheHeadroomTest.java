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

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

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
@ShSourceScan(ShSourceScan.Reads.CODE)
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
     *
     * <p><b>Two rather than eight since SH423.</b> Eight was covering a known, systematic
     * undercount: the counter counted <em>datasource URLs</em> and the cache keys on the whole
     * property set, so it read 56 where the suite created 62. Now that the counter keys the way
     * Spring does, the margin has one job left — the four classes whose properties are registered
     * by {@code @DynamicPropertySource}, whose values a source scan cannot compute and which are
     * counted one per class.</p>
     */
    private static final int MARGIN = 2;

    /**
     * SH423 — <b>the gauge.</b> What the suite keys today, written down so a change has to be one.
     *
     * <h2>Why a declared number and not only a ceiling</h2>
     *
     * <p>SH347 made the wall visible; it did not price the next step toward it. A task adding
     * {@code @SpringBootTest(properties = …)} spent a slot silently — measured over Block Q,
     * eleven tasks took the count from 38 to 44 and nothing asked once. Then four tasks in one
     * session took it to the wall, and the first anybody heard was a red build in the middle of
     * unrelated work.</p>
     *
     * <p>So the count is asserted <em>exactly</em>, the way {@code token-budgets.properties} and
     * {@code ShSilentCatchInventoryTest} assert theirs. Raising it is allowed and is a deliberate,
     * reviewable act: the diff that changes this line is the moment somebody is asked. Lowering it
     * is the same — a task that frees a slot should be able to show it did.</p>
     *
     * <p>The headroom is printed on every run, so approach is visible rather than only arrival.</p>
     */
    /*
     * SH613 lowered this from 63 to 61, and the two it removed were never there.
     *
     * The scan read raw file text, so a javadoc naming `@SpringBootTest` or quoting a
     * `properties = "spring.datasource.url=…"` counted as a class declaring one. Four files carried
     * such a mention — including two lints that are not Spring tests at all, and this file, which
     * had already noticed the problem for ITSELF and excluded itself rather than asking whether
     * anybody else was affected. Reading through ShSourceText makes the count what it always
     * claimed to be, and the direction matters: the budget was reported as MORE spent than it is,
     * so the wall SH423 exists to warn about was two slots further away than the warning said.
     *
     * SH635 then spent one of them, back to 62. ShAgentOrphanScopeTest asserts on an
     * INSTANCE-WIDE read — `verify(null, …)`, the only call that can see a post belonging to no
     * site — which SH315's rule and ShBenchmarkIsolationLintTest both refuse on a shared context.
     * It was written into a shared-context class first and that lint caught it, which is the
     * intended order of events.
     *
     * SH221 spent another, to 63. ShPlanWritesNothingTest counts the mutations Hibernate performed
     * while a plan runs, and those statistics are cumulative on the SessionFactory — a sibling test
     * writing content on the shared context would be indistinguishable from the defect it exists to
     * catch. SH315's rule, in the one shape where sharing does not merely add noise but inverts the
     * answer.
     *
     * SH395 spent one more, to 64, and it is SH315's rule arriving late rather than a new appetite:
     * ShBlueprintApplyTest applies whole packages — provisioning render post-types, writing a Theme
     * and a PageLayout, asserting instance-wide counts — so another test's minimal `Theme` type made
     * it refuse a field. It is the same isolation SH635's flake wanted.
     *
     * SH518 spent one more, to 65, for the sibling of that reason: post-types are instance-wide and
     * ShBlueprintApplyTest bends one ON PURPOSE to prove a mismatch is reported, so every later
     * apply in that class meets it. ShBlueprintRendersTest asserts what a whole apply produces and
     * cannot share a bent model.
     *
     * SH222 spent one more, to 66. ShAgentTrashTest deletes content and then asserts what the TRASH
     * holds, which is instance-wide by construction — on a shared context another test's deletion
     * is indistinguishable from its own, and the listing assertion would be about suite order.
     *
     * SH225 spent one and then gave it back. It added a context to run with
     * `spring.jpa.open-in-view=false`, on the prediction that the ACL walk would throw without a
     * session; the association turned out to be EAGER and all three tests passed on the unfixed
     * code. A green-either-way test is not worth a slot when two remain, so it was withdrawn and
     * the assertion that does fail (ShContentAclEntryPointTest) needs no context at all.
     *
     * SH967 spent one, to 67, with maxSize raised to 69 beside it. ShEventStreamCostBenchmarkTest
     * counts the statements a pass of the change stream prepares, and Hibernate's statistics are
     * cumulative on the SessionFactory — the SH221 shape, where a sibling's writes would be counted as
     * the stream's. The behaviour tests (ShEventStreamTest) share the default context; only the
     * measurement needed its own.
     *
     * SH1022 spent two, to 69, with maxSize raised to 71 beside it, and neither could be folded
     * into a shared one. ShFindQueryBenchmarkTest counts Hibernate's entity loads, which are
     * cumulative on the SessionFactory — the SH221 shape, where a sibling's reads are counted as
     * this listing's and the ratio the whole task rests on becomes a statement about suite order.
     * ShContentQuerySurfacesTest compares the address SETS two listings answer with, across a
     * fixture built to differ on every axis the vocabulary names; on the shared context both sets
     * would carry every other test's content, so they would agree for a reason that has nothing to
     * do with the predicate agreeing.
     */
    private static final int DECLARED_CONTEXTS = 69;

    // SH603 raised this from 62, and the gauge is what asked. The slot went to
    // `ShAgentAuthoringCostTest`, which counts the calls it takes to build a site FROM AN EMPTY
    // INSTANCE — the archetypal case SH315's rule says may not share a context, because another
    // suite's content makes "from nothing" a statement about test order rather than about the
    // protocol. It cannot be folded into an existing context for the same reason.
    //
    // Headroom is now ZERO against the ceiling in application.properties, which is raised to 68 in
    // the same commit. That raise is the reviewable act this gauge exists to force, and the reason
    // is worth stating: the alternative was to not measure the second task shape at all, and
    // SH603's whole finding is that a protocol scored only on the shape it was tuned for cannot
    // tell discoverability from familiarity.

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

    // --- SH423: what Spring actually keys on --------------------------------

    private static final Pattern SPRING_BOOT_TEST = Pattern.compile("@SpringBootTest\\b");

    /** The `properties = "…"` or `properties = { … }` payload — the bulk of every key here. */
    private static final Pattern PROPERTIES =
            Pattern.compile("properties\\s*=\\s*(\\{[^}]*\\}|\"(?:[^\"\\\\]|\\\\.)*\")", Pattern.DOTALL);

    private static final Pattern IMPORTS = Pattern.compile("@Import\\(([^)]*)\\)", Pattern.DOTALL);

    private static final Pattern WEB_ENVIRONMENT =
            Pattern.compile("webEnvironment\\s*=\\s*[\\w.]+");

    /** Values a source scan cannot compute, so the class stands in for its own key. */
    private static final Pattern DYNAMIC_PROPERTIES = Pattern.compile("@DynamicPropertySource");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

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
        Set<String> contexts = contextKeys();
        int spare = ceiling - contexts.size() - MARGIN;

        // Locale.ROOT: a build log that reads "1,5 s" on one machine and "1.5 s" on another is the
        // same defect as an unstable iteration order (P5), and it is the one printf inherits.
        // SH423 — the headroom is printed, because "how close am I" was the question nobody could
        // answer without failing the build to find out.
        System.out.printf(java.util.Locale.ROOT,
                "%n[SH423] %d Spring contexts, ceiling %d, margin %d — %d slot(s) to spare%n"
                        + "[SH347] %d of them isolate a database of their own%n"
                        + "[SH317] about %.0f s of the run, at ~%.1f s a context (measured "
                        + "2026-08-02)%n  %s%n%n",
                contexts.size(), ceiling, MARGIN, spare, isolated.size(),
                isolated.size() * SECONDS_PER_CONTEXT, SECONDS_PER_CONTEXT, isolated);

        assertThat(contexts.size())
                .as("""
                        SH423 — the count moved, and a slot is spent or freed without anybody being \
                        asked unless this line moves with it. %d contexts are declared here and the \
                        suite now keys %d.

                        If you ADDED one: it is allowed, and it is a deliberate act — raise \
                        DECLARED_CONTEXTS and say why in the commit. First ask whether the new test \
                        needs its own context at all: SH315's rule is that a test measuring the \
                        *whole instance* may not share one, and a test that only reads its own \
                        fixtures may. There are %d slot(s) left before the ceiling.
                        If you REMOVED one: lower it in the same commit, or the budget a task freed \
                        is one the next task spends without knowing it was a gift.""".formatted(
                                DECLARED_CONTEXTS, contexts.size(), Math.max(0, spare)))
                .isEqualTo(DECLARED_CONTEXTS);

        assertThat(contexts.size() + MARGIN)
                .as("""
                        %d Spring contexts against a %s of %d. Crossing the ceiling does not merely \
                        cost time: an evicted context is rebuilt with ddl-auto=create-drop, which \
                        recreates the schema mid-run and wipes rows a later test in the shared \
                        datasource is asserting about. The symptom is a test failing only in the \
                        full suite.

                        Raise %s and say why, or share a context (SH315's rule is that a test \
                        measuring the *whole instance* may not share it — a test that only reads \
                        its own fixtures may).""".formatted(
                                contexts.size(), MAX_SIZE, ceiling, MAX_SIZE))
                .isLessThanOrEqualTo(ceiling);
    }

    @Test
    @DisplayName("SH423: the context counter keys the way Spring does, not by datasource")
    void theCounterKeysOnTheWholePropertySet() {
        // The exact shape the old counter collapsed: one URL, two property sets, two contexts.
        String shared = keyOf("@SpringBootTest(properties = \"spring.datasource.url=jdbc:h2:mem:a\")", "A.java");
        String plus = keyOf(
                "@SpringBootTest(properties = {\"spring.datasource.url=jdbc:h2:mem:a\", \"x=1\"})",
                "B.java");

        assertThat(plus)
                .as("""
                        two classes on one database with different properties are TWO contexts. \
                        Counting the URL read them as one, which is why the count said 56 while the \
                        suite created 62 — a systematic undercount the margin was silently \
                        absorbing.""")
                .isNotEqualTo(shared);

        assertThat(keyOf("@SpringBootTest\nclass Plain {}", "C.java"))
                .as("and a class that configures nothing shares the default context, or the count "
                        + "would be one per test class and mean nothing")
                .isEqualTo("<default>");
        assertThat(keyOf("@SpringBootTest @Import(Probe.class)\nclass D {}", "D.java"))
                .as("@Import contributes to the key — SH397's probe config is a context of its own, "
                        + "and it is invisible to a datasource scan")
                .isNotEqualTo("<default>");
        assertThat(contextKeys())
                .as("a counter that came back empty would pass forever while the real count grew")
                .hasSizeGreaterThan(40)
                .contains("<default>");
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

    /**
     * The distinct contexts the surefire run keys, SH423's counter.
     *
     * <h2>Why this replaced counting datasource URLs</h2>
     *
     * <p>Spring's cache key is the whole {@code MergedContextConfiguration}, not the datasource.
     * Two classes with the same URL and different other properties are two contexts, and the old
     * counter reported them as one — a <b>systematic</b> undercount, not an approximation: it read
     * 56 where the suite created 62. The declared limit above said so honestly and the margin was
     * carrying the difference, which is a margin doing a counter's job.</p>
     *
     * <p>Everything that contributes a key in this repository is read here, and the set is closed
     * by measurement rather than by hope: a scan for {@code @TestPropertySource},
     * {@code @ContextConfiguration}, {@code @ActiveProfiles}, {@code @MockitoBean} and
     * {@code @MockBean} finds none. If one appears, it belongs in this method — and
     * {@link #theCounterFindsThem} is what will notice the count stop matching.</p>
     *
     * <p><b>{@code *IT.java} is excluded.</b> Failsafe runs in its own JVM with its own cache, so
     * ten IT contexts cost nothing against this ceiling. Counting them would inflate the number
     * with contexts that never coexist.</p>
     */
    private static Set<String> contextKeys() {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().endsWith("IT.java"))
                    .filter(ShContextCacheHeadroomTest::notThisFile)
                    .forEach(path -> {
                        String body = read(path);
                        if (SPRING_BOOT_TEST.matcher(body).find()) {
                            keys.add(keyOf(body, path.getFileName().toString()));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return keys;
    }

    /**
     * Excludes this file from both scans.
     *
     * <p>Not housekeeping: the guard-the-guard assertions below contain literal
     * {@code @SpringBootTest(properties = …)} and {@code jdbc:h2:mem:} strings, so a counter that
     * read its own source would count its own examples — this file added one phantom context and
     * one phantom database the moment those cases were written. It is the shape
     * {@code assertion-debt.test.mjs} excludes itself for, and on its first run that omission made
     * every entry read as asserted.</p>
     */
    private static boolean notThisFile(Path path) {
        return !path.getFileName().toString().equals("ShContextCacheHeadroomTest.java");
    }

    /** One class's approximate cache key; classes that configure nothing share {@code <default>}. */
    private static String keyOf(String body, String name) {
        Set<String> parts = new TreeSet<>();
        collect(PROPERTIES.matcher(body), 1, parts, "");
        collect(IMPORTS.matcher(body), 1, parts, "import:");
        collect(WEB_ENVIRONMENT.matcher(body), 0, parts, "");
        if (DYNAMIC_PROPERTIES.matcher(body).find()) {
            // The values are computed at runtime, so the class is its own key. Four classes in the
            // surefire run, which is what MARGIN now covers.
            parts.add("dynamic:" + name);
        }
        return parts.isEmpty() ? "<default>" : String.join(" | ", parts);
    }

    private static void collect(Matcher matcher, int group, Set<String> into, String prefix) {
        while (matcher.find()) {
            into.add(prefix + WHITESPACE.matcher(matcher.group(group)).replaceAll(" ").trim());
        }
    }

    private static Set<String> isolatedDatabases() {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    // SH423 — the whole file rather than the two names it used to subtract: the
                    // guard-the-guard cases grew a third database and the removals did not, which
                    // is the maintenance an exclusion does not need.
                    .filter(ShContextCacheHeadroomTest::notThisFile)
                    .forEach(path -> {
                        Matcher matcher = ISOLATED_DATASOURCE.matcher(read(path));
                        while (matcher.find()) {
                            names.add(matcher.group(1));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }

    private static String read(Path path) {
        try {
            return ShSourceText.code(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
