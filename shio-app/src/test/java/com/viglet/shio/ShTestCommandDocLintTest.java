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

    /** A documented invocation of the suite: a fenced or inline `mvnw … test` line. */
    private static final Pattern RUNS_THE_SUITE =
            Pattern.compile("^.*mvnw[^\\n]*\\btest\\b[^\\n]*$", Pattern.MULTILINE);

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
                .anyMatch(line -> line.contains("| grep"));
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
