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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The other two gates, reported inside the one that gets run (SH755).
 *
 * <h2>The finding, and why it lands here of all places</h2>
 *
 * <p>SH579's rule is that a red suite either blocks the next commit or is a declared, dated
 * exception. On 2026-08-14 the project had spent <b>eight consecutive CI runs</b> in the third
 * state: {@code pnpm test} and {@code pnpm conformance} red, {@code red-suites.json} empty. The
 * mechanism was not disagreement about the rule — it was that the one gate anybody typed was
 * {@code ./mvnw … clean test}, it was green throughout, and a green gate is the most convincing
 * argument available that the others need not be run.</p>
 *
 * <p>So the report belongs <b>here</b>, in the suite that is actually executed, and not only in
 * {@code cli/test/red-suites.test.mjs} — which is inside one of the gates that was being skipped.
 * A check that can only be read by somebody who already looked is a check about looking.</p>
 *
 * <h2>What it can and cannot claim</h2>
 *
 * <p>It cannot run the other gates: this is a unit suite and they need a workspace and a live
 * instance. What it reads is the stamp each gate wrapper leaves in {@code .gates/}
 * ({@code scripts/gate-stamp.mjs}) — the exit code, the commit and the time. So the question it
 * answers is exact and narrow: <b>of the runs this tree has actually seen, is any red one
 * undeclared?</b></p>
 *
 * <p>An absent stamp fails nothing. A fresh clone has none, CI runs each gate in its own job, and
 * "nobody has run this here" is true rather than wrong. It is <b>printed</b>, every run, because
 * that sentence in front of the person reading a green Java suite is the entire repair — and it is
 * the sentence whose absence cost eight pushes.</p>
 *
 * @author Alexandre Oliveira
 */
class ShDeclaredRedTest {

    /** Tests run with {@code shio-app} as the working directory, so the repository is one up. */
    private static final Path ROOT = Path.of("..");

    private static final List<String> GATES = List.of("java", "js", "conformance");

    /** `"suite": "conformance"` — the one field of the ledger this test needs. */
    private static final Pattern DECLARED_SUITE =
            Pattern.compile("\"suite\"\\s*:\\s*\"([a-z]+)\"");

    /** `"red": true` — written by the wrapper, so the shape is this repository's own. */
    private static final Pattern STAMP_RED = Pattern.compile("\"red\"\\s*:\\s*(true|false)");
    private static final Pattern STAMP_SHA = Pattern.compile("\"sha\"\\s*:\\s*\"([0-9a-f]+)\"");
    private static final Pattern STAMP_WHEN = Pattern.compile("\"when\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    @DisplayName("SH755: no gate that answered red in this tree is missing from red-suites.json")
    void anUndeclaredRedIsNamedWhereSomebodyWillSeeIt() throws IOException {
        Set<String> declared = declaredSuites();
        List<String> report = new ArrayList<>();
        List<String> undeclared = new ArrayList<>();

        for (String gate : GATES) {
            Path stamp = ROOT.resolve(".gates").resolve(gate + ".json");
            if (!Files.isRegularFile(stamp)) {
                // The java row is a special case and saying "no run recorded" there would be
                // false in the most confusing available way: this test IS the java gate, and it
                // cannot stamp its own outcome because a test does not know whether the tests
                // after it passed. So it says which gate it is rather than pretending ignorance.
                report.add("  " + gate + ("java".equals(gate)
                        ? " — this suite, running now (it cannot stamp its own outcome)"
                        : " — no run recorded in this tree"));
                continue;
            }
            String body = Files.readString(stamp, StandardCharsets.UTF_8);
            boolean red = "true".equals(group(STAMP_RED, body));
            String sha = group(STAMP_SHA, body);
            report.add("  " + gate + " — " + (red ? "RED" : "green")
                    + (sha == null ? "" : " at " + sha.substring(0, Math.min(8, sha.length())))
                    + ", " + group(STAMP_WHEN, body)
                    + (declared.contains(gate) ? ", declared" : ""));
            if (red && !declared.contains(gate)) {
                undeclared.add(gate);
            }
        }

        // Printed on every run, green or red, and that is the deliverable rather than a courtesy:
        // the defect was a person reading one green gate and never learning about the other two.
        System.out.println("\n[SH755] the three gates, as last run in this tree\n"
                + String.join("\n", report)
                + "\n  (stamps are local and untracked; a gate with no run here is not a failure)\n");

        assertThat(undeclared)
                .as("""
                        SH755: this gate answered red in this tree and red-suites.json says nothing \
                        about it. That is the third state SH579 says does not exist, and the \
                        project spent eight consecutive CI pushes in it — every one over a red \
                        nobody had declared, while this suite stayed green and did the convincing. \
                        Fix the suite, or declare it against an open task.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH755: the reader is not vacuous — it knows the ledger and the gate names")
    void theReaderReadsSomethingReal() throws IOException {
        // The failure mode of every file-reading guard: a moved path makes it green for ever. Both
        // halves are asserted, because either one silently missing turns the test above off.
        assertThat(ROOT.resolve("red-suites.json"))
                .as("SH755: the ledger this compares against has moved, so the comparison above is "
                        + "between a red gate and an empty set — which passes, always")
                .exists();
        assertThat(GATES)
                .as("SH755: the gate names are the keys red-suites.json allows and the wrappers "
                        + "write; a name only this file knows would compare nothing")
                .containsExactlyInAnyOrder("java", "js", "conformance");
    }

    private static Set<String> declaredSuites() throws IOException {
        Path ledger = ROOT.resolve("red-suites.json");
        if (!Files.isRegularFile(ledger)) {
            return Set.of();
        }
        Set<String> suites = new LinkedHashSet<>();
        Matcher matcher = DECLARED_SUITE.matcher(Files.readString(ledger, StandardCharsets.UTF_8));
        while (matcher.find()) {
            suites.add(matcher.group(1));
        }
        return suites;
    }

    private static String group(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
