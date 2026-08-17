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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH354 — the documented way to read the suite's number must not destroy the failure's name.
 *
 * <h2>The incident</h2>
 *
 * <p>Shipping SH293, one {@code clean test} in six reported <b>1186 tests, 1 failure</b>. Five
 * identical runs around it passed and the failing class is still unknown, because the command was
 * the one this repository documents — {@code … clean test 2>&1 | grep -E "Tests run:.*"} — and the
 * {@code <<< FAILURE!} line went into a pipe nobody kept. The second copy, {@code
 * target/surefire-reports/}, was removed by the {@code clean} that SH282 makes mandatory on the very
 * next run.</p>
 *
 * <p>So a real defect went undiagnosable for a process reason rather than a hard one: <b>reading the
 * total and preserving the diagnosis were the same command, and it only did the first.</b> That is
 * SH283's lesson one step along — the count must come from the run, and so must the evidence.</p>
 *
 * <h2>Why a lint and not a sentence</h2>
 *
 * <p>The fix is one {@code tee} in a documented command, which is exactly the kind of thing a later
 * edit shortens back out for readability: the pipeline reads better without it and nothing fails.
 * SH322's habit says the guard lands with the fix, so this asserts the property rather than the
 * spelling — the documented pipeline must send the run somewhere that survives it, by {@code tee} or
 * by redirection. A future author may change how; they may not quietly change whether.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTestCommandDocLintTest {

    /** The two files that document how to run this suite. Repo-root relative to the module. */
    private static final List<Path> DOCS = List.of(
            Path.of("..", "agents.md"),
            Path.of("..", "docs", "agents", "build.md"));

    /**
     * A documented invocation of a gate: a fenced or inline line running one of the three.
     *
     * <p>SH791 — this matched {@code mvnw … test} alone, which was fair when the rule it serves was
     * SH354's and about the Java suite. SH788 generalised the rule to all three gates and measured
     * the failure on {@code pnpm test}, so a guard reading only Maven lines checked the gate the
     * incident did <b>not</b> happen to.</p>
     *
     * <p>Broadening was measured before it was written, because {@code pnpm test} appears in these
     * files constantly as prose where {@code mvnw … test} does not — nineteen inline mentions in
     * `build.md` alone. The measurement that settles it is narrower than "prose or command": a line
     * can only be judged if it also holds a <b>shell pipe</b>, and across both files the count of
     * lines naming a gate and holding one is <b>zero</b>. A bare `pnpm test` in a sentence has no
     * pipe and can never be flagged, so the distinction the guard was going to need turned out not
     * to be one it has to make.</p>
     *
     * <p>The three gates and not {@code pnpm lint}: that is a CI step and the JS-side guard covers
     * it there, but it is not one of the three commands these documents call gates.</p>
     */
    private static final Pattern RUNS_THE_SUITE = Pattern.compile(
            "^.*(?:mvnw[^\\n]*\\btest\\b|pnpm\\s+(?:test|conformance))[^\\n]*$", Pattern.MULTILINE);

    /** One documented command per gate, so a pattern that stops matching one of them is not silent. */
    private static final List<String> GATES = List.of("mvnw", "pnpm test", "pnpm conformance");

    /** What makes a run readable afterwards: it is teed, or redirected, to a file. */
    private static final Pattern KEEPS_THE_RUN = Pattern.compile("\\|\\s*tee\\s|>\\s*[\\w./-]+\\.log");

    @Test
    @DisplayName("SH354: a documented command that pipes the run into a filter also keeps the run")
    void everyFilteredCommandKeepsItsLog() {
        List<String> lossy = new ArrayList<>();
        for (Path doc : DOCS) {
            String body = read(doc);
            Matcher matcher = RUNS_THE_SUITE.matcher(body);
            while (matcher.find()) {
                String line = matcher.group();
                // Only the ones that FILTER are at risk: a bare `./mvnw … test` prints everything
                // to the terminal, where the failure is still on screen. The defect is the pipe.
                if (line.contains("| grep") && !KEEPS_THE_RUN.matcher(line).find()) {
                    lossy.add(doc.getFileName() + ": " + line.trim());
                }
            }
        }

        assertThat(lossy)
                .as("""
                        A command that filters the run and keeps nothing is how SH354's failure \
                        became undiagnosable: the total survived, the name of the failing test did \
                        not, and the next mandatory `clean` removed target/surefire-reports. Add \
                        `| tee shio-test.log` before the grep, or redirect to a .log file.""")
                .isEmpty();
    }

    /**
     * SH788 — the other thing a pipe costs, and the one SH354's rule does not cover.
     *
     * <p>SH354 is about the <em>log</em>: filter a run and you throw away which test failed. This is
     * about the <em>verdict</em>. A pipeline's exit status is its last command's, so
     * {@code … clean test | tee x.log | grep -E "…" | tail -1} exits with {@code tail}'s status —
     * zero, always. The documented command had that shape, and a reader who ran it and saw a clean
     * exit had been told the suite passed by {@code tail}.</p>
     *
     * <p>It is not the same failure and knowing SH354 does not prevent it: there the evidence is
     * lost and the verdict is honest, here the evidence survives and the verdict is wrong. Measured
     * on {@code pnpm test}, which printed {@code fail 2} directly above {@code EXIT=0}.</p>
     *
     * <p>{@code set -o pipefail} is the escape hatch, because it makes the claim true again — which
     * is what CI does, asserted from the other side by {@code ci-gates.test.mjs}.</p>
     */
    @Test
    @DisplayName("SH788: a documented command does not take the suite's verdict from a pipeline")
    void noDocumentedCommandReadsItsVerdictFromAFilter() {
        List<String> lying = new ArrayList<>();
        for (Path doc : DOCS) {
            Matcher matcher = RUNS_THE_SUITE.matcher(read(doc));
            while (matcher.find()) {
                String line = matcher.group();
                // A Markdown table row is not a pipeline, and both files list the gates in one.
                // Found by the guard's first run, which is the argument for running it before
                // believing it: the cell `| ./mvnw … clean test | nothing |` has three `|` and no
                // shell in sight.
                if (line.strip().startsWith("|")) {
                    continue;
                }
                if (line.contains("|") && !line.contains("||") && !line.contains("pipefail")) {
                    lying.add(doc.getFileName() + ": " + line.trim());
                }
            }
        }

        assertThat(lying)
                .as("""
                        A pipeline exits with its LAST command's status, so a documented run piped \
                        into `tee`, `grep` or `tail` reports that filter's verdict — green for as \
                        long as the disk has room. Redirect instead (`> shio-test.log 2>&1`, which \
                        keeps the run exactly as the tee did) and read the count from the file, or \
                        put `set -o pipefail` in front of the pipeline.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard is not vacuous: it finds the documented commands it is about")
    void theScanFindsTheCommands() {
        List<String> found = new ArrayList<>();
        for (Path doc : DOCS) {
            Matcher matcher = RUNS_THE_SUITE.matcher(read(doc));
            while (matcher.find()) {
                found.add(matcher.group().trim());
            }
        }

        assertThat(found)
                .as("a scan matching nothing would pass forever — the failure every lint here is "
                        + "written against")
                .isNotEmpty()
                // SH788 — this used to anchor on `anyMatch(line -> line.contains("| grep"))`, which
                // was fair while the documented command WAS a pipeline. It is not any more, and an
                // anchor that requires the forbidden shape to exist is a lint holding the docs to
                // the defect it was written about. The property that survives both spellings is the
                // one this guard is actually about: a documented run is kept.
                .anyMatch(line -> KEEPS_THE_RUN.matcher(line).find());

        // SH791 — and one per gate, because "the scan found something" is satisfied by the Maven
        // lines alone. That is exactly how this guard came to claim three gates while reading one:
        // the population was never empty, so nothing ever said the other two had stopped matching.
        for (String gate : GATES) {
            assertThat(found)
                    .as("SH791: no documented command for the `%s` gate reached this scan. The rule "
                            + "in agents.md names three gates, so a pattern that matches only some "
                            + "of them is a guard whose subject is smaller than its claim — and it "
                            + "stays green while being so.", gate)
                    .anyMatch(line -> line.contains(gate));
        }
        assertThat(KEEPS_THE_RUN.matcher("… test 2>&1 | tee shio-test.log | grep -E \"x\"").find())
                .as("the tee spelling")
                .isTrue();
        assertThat(KEEPS_THE_RUN.matcher("… test 2>&1 > run.log").find())
                .as("and the redirection spelling, since the rule is the property and not one form")
                .isTrue();
        assertThat(KEEPS_THE_RUN.matcher("… test 2>&1 | grep -E \"Tests run\"").find())
                .as("and it must NOT match the shape the incident came from")
                .isFalse();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
