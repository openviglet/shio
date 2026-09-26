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

    // SH798 — `docs` joined as the fourth. It had been a gate in every sense that matters, and the
    // only one that could be run without leaving a stamp, so this reader had nothing to say about it
    // while it carried twelve commits of debt (SH797).
    private static final List<String> GATES = List.of("java", "js", "conformance", "docs");

    /**
     * The gate this test runs inside, and therefore the one it cannot judge (SH800).
     *
     * <p>Exactly what `red-suites.test.mjs` declares for `js`, and for the same reason: a comparator
     * reading its own gate's stamp reads the previous run's. The two files exclude one gate each and
     * judge the other three, so between them every gate is judged once — which is the arrangement
     * that only became true when `java` started leaving a stamp.</p>
     */
    private static final String SELF = "java";

    /** `"suite": "conformance"` — the one field of the ledger this test needs. */
    private static final Pattern DECLARED_SUITE =
            Pattern.compile("\"suite\"\\s*:\\s*\"([a-z]+)\"");

    /** `"red": true` — written by the wrapper, so the shape is this repository's own. */
    private static final Pattern STAMP_RED = Pattern.compile("\"red\"\\s*:\\s*(true|false)");
    private static final Pattern STAMP_SHA = Pattern.compile("\"sha\"\\s*:\\s*\"([0-9a-f]+)\"");
    private static final Pattern STAMP_WHEN = Pattern.compile("\"when\"\\s*:\\s*\"([^\"]+)\"");
    /** SH1103 — the digest of what the run read that a ship rewrites, written by gate-stamp.mjs. */
    private static final Pattern STAMP_INPUTS = Pattern.compile("\"inputs\"\\s*:\\s*\"([0-9a-f]+)\"");

    /**
     * SH1103 — {@code STAMP_INPUTS} in {@code scripts/gate-stamp.mjs}, in its order. The digest has to be
     * byte-for-byte the one the script writes, so both hash raw bytes: the path, a NUL, the file (or
     * {@code <absent>}), a NUL.
     */
    static final List<String> INPUTS = List.of(
            "docs/ROADMAP.md", "docs/CHANGELOG.md", "docs/IMPROVEMENTS.md", "docs/coverage-named.json");

    @Test
    @DisplayName("SH755: no gate that answered red in this tree is missing from red-suites.json")
    void anUndeclaredRedIsNamedWhereSomebodyWillSeeIt() throws IOException {
        Set<String> declared = declaredSuites();
        List<String> report = new ArrayList<>();
        List<String> undeclared = new ArrayList<>();
        String head = headSha();

        for (String gate : GATES) {
            // SH800 — `java` now HAS a stamp, written by the wrapper `agents.md` documents, and this
            // test still must not judge it. The stamp on disk is the PREVIOUS run's: judging it here
            // makes one red run fail the next one for a failure already fixed, then pass on the
            // third, which is how an instrument teaches people to re-run it twice and believe
            // neither answer. It is `red-suites.test.mjs`'s reason for excluding `js`, arriving on
            // this side the moment the stamp existed — and it fired on the first run that had one.
            //
            // Nothing is lost, and this time that claim is checked rather than asserted:
            // `red-suites.test.mjs` judges every gate but its own, so it judges this one, and
            // SH798's wrapper test holds both exclusions to being exactly the two SELF gates.
            if (SELF.equals(gate)) {
                report.add("  " + gate + " — this suite, running now (its stamp is the last run's)");
                continue;
            }
            Path stamp = ROOT.resolve(".gates").resolve(gate + ".json");
            if (!Files.isRegularFile(stamp)) {
                report.add("  " + gate + " — no run recorded in this tree");
                continue;
            }
            String body = Files.readString(stamp, StandardCharsets.UTF_8);
            boolean red = "true".equals(group(STAMP_RED, body));
            String sha = group(STAMP_SHA, body);
            String at = sha == null ? "" : " at " + sha.substring(0, Math.min(8, sha.length()));
            // SH770 — a stamp taken against another commit is not this tree's colour. This test
            // failed over exactly that: `red: true` at 4b20d8e0 while HEAD was 42d02432, two
            // commits later. It happened to be right, because the defect was still open, and that
            // is luck rather than a property of a check.
            if (staleStamp(body, head)) {
                report.add("  " + gate + " — last run" + at + ", " + group(STAMP_WHEN, body)
                        + " — NOT this tree, so not a colour");
                continue;
            }
            report.add("  " + gate + " — " + (red ? "RED" : "green") + at
                    + ", " + group(STAMP_WHEN, body)
                    + (declared.contains(gate) ? ", declared" : ""));
            if (red && !declared.contains(gate)) {
                undeclared.add(gate);
            }
        }

        // Printed on every run, green or red, and that is the deliverable rather than a courtesy:
        // the defect was a person reading one green gate and never learning about the other two.
        System.out.println("\n[SH755] the gates, as last run in this tree\n"
                + String.join("\n", report)
                + "\n  (stamps are local and untracked; a gate with no run here is not a failure)\n");

        // SH818 — the state this test can be half of without being able to see it. The SELF rule
        // above skips `java` for a good reason, and the consequence is that the one stamp which
        // would reveal the cycle is the one never read. It is read HERE, where it is not being
        // judged: not as this tree's colour, which it isn't, but as the answer to "were we in this
        // same place last time". Printed and never asserted — both gates red is a genuine red until
        // somebody has read the two kept logs and found nothing in them, and a check that excused
        // it would turn the arrangement that catches an undeclared red into one that hides a double
        // failure.
        if (inComparatorCycle(head)) {
            System.out.println(CYCLE_EXIT);
        }

        assertThat(undeclared)
                .as("""
                        SH755: this gate answered red in this tree and red-suites.json says nothing \
                        about it. That is the third state SH579 says does not exist, and the \
                        project spent eight consecutive CI pushes in it — every one over a red \
                        nobody had declared, while this suite stayed green and did the convincing. \
                        Fix the suite, or declare it against an open task.""")
                .isEmpty();
    }

    /**
     * The two gates that host a comparator, so the only pair that can deadlock (SH818).
     *
     * <p>Restated here rather than imported because {@code gate-stamp.mjs} is JavaScript and this is
     * the JVM — the same duplication {@code GATES} already carries, held in agreement by
     * {@code red-suites.test.mjs}'s reading of that module's source.</p>
     */
    private static final String CYCLE_EXIT = """

            [SH818] `java` and `js` are BOTH red, and those are the two gates that judge each other.
              If each suite's only failure is the other's stamp, no run of either can clear it — the
              colours are the pair, not the code. Read the kept logs first (shio-test.log,
              shio-js-test.log): a real failure there is a real failure. If both are clean but for
              the comparator, break the cycle with `pnpm gates:clear java js`, then run `pnpm java`
              and `pnpm test` in that order. Clearing is recorded, not a deletion.
            """;

    /**
     * Are both comparator gates red about this tree? (SH818)
     *
     * <p>Reads {@code java}'s own stamp, which the check above must not — there it would be the
     * previous run's colour standing in for this one, and here it is the previous run's <em>state</em>
     * being asked a different question. A stale stamp is excluded exactly as it is everywhere else:
     * a cycle diagnosed from one sends a reader to clear a stamp when what they need is to run the
     * gate.</p>
     */
    private boolean inComparatorCycle(String head) {
        for (String gate : List.of("java", "js")) {
            Path stamp = ROOT.resolve(".gates").resolve(gate + ".json");
            if (!Files.isRegularFile(stamp)) {
                return false;
            }
            try {
                String body = Files.readString(stamp, StandardCharsets.UTF_8);
                if (!"true".equals(group(STAMP_RED, body)) || staleStamp(body, head)) {
                    return false;
                }
            } catch (IOException unreadable) {
                // A stamp that cannot be read is no stamp, which is this file's rule everywhere
                // else — and a diagnostic must never be the thing that fails a gate.
                return false;
            }
        }
        return true;
    }

    /**
     * SH789 — the other direction, and the one nothing was checking for the gate that had a
     * declaration.
     *
     * <h2>The hole, which is a seam and not an oversight</h2>
     *
     * <p>Two comparators, deliberately: this one and {@code red-suites.test.mjs}. That one holds
     * both directions but excludes {@code js}, because it <i>is</i> the js gate and reading its own
     * previous stamp would fail one run for a failure already fixed. It says so, and says nothing
     * is lost because this file reads the js stamp from inside the Java gate.</p>
     *
     * <p>That sentence was half true. This file only ever asked whether a <b>red</b> gate was
     * undeclared. The reverse — a declaration outliving the red that justified it — existed only on
     * the side that cannot judge {@code js}. So the repository's only exception, for the js gate,
     * was judged by neither comparator, and roughly fifty consecutive green runs never retired
     * it.</p>
     *
     * <p>An exception outliving its red is not tidiness. It is a standing licence: the next real
     * failure of that suite is already excused, and the person who hits it reads a ledger entry
     * saying this is known. The expiry date is the backstop, and it fires as a <b>red</b> — so the
     * cost of nobody noticing is a gate that goes red on a calendar day, for the ledger rather than
     * for the code.</p>
     */
    @Test
    @DisplayName("SH789: no declaration outlives the red it excuses, including the js gate's")
    void aDeclarationThatWentGreenIsNotAnException() throws IOException {
        Set<String> declared = declaredSuites();
        List<String> outlived = new ArrayList<>();
        String head = headSha();

        for (String gate : GATES) {
            if (!declared.contains(gate)) {
                continue;
            }
            Path stamp = ROOT.resolve(".gates").resolve(gate + ".json");
            if (!Files.isRegularFile(stamp)) {
                continue;
            }
            String body = Files.readString(stamp, StandardCharsets.UTF_8);
            // SH770 again, and it matters more here than above: a stale GREEN must not retire a
            // declaration any more than a stale red may block a commit. Excluding it in both
            // directions is what makes a stale stamp read as an absent one.
            if (staleStamp(body, head)) {
                continue;
            }
            if (!"true".equals(group(STAMP_RED, body))) {
                outlived.add(gate);
            }
        }

        assertThat(outlived)
                .as("""
                        SH789: red-suites.json declares this gate red and it answered GREEN in this \
                        tree, at this commit. An exception outliving its red is how a suite stops \
                        being a gate — the next real failure is already excused, and the expiry \
                        date turns that into a red for the ledger rather than for the code. Delete \
                        the entry. If it went green for a reason nobody understands, that is a \
                        task, not a line in a ledger.""")
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
                .containsExactlyInAnyOrder("java", "js", "conformance", "docs");
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

    @Test
    @DisplayName("SH770: a stamp from another commit is not this tree's colour, in either direction")
    void aStampFromAnotherCommitIsNotAColour() {
        // The measurement that filed this: `red: true` at 4b20d8e0 while HEAD was 42d02432. The
        // suite failed over a red recorded against code that had since changed twice, and was right
        // by luck — the defect happened to still be open.
        assertThat(isStale("4b20d8e030fe38ea7c8b4649478ec3b055018d06",
                "42d0243218f6b1b3c9f0e6a8d5c4b3a29187f6e5"))
                .as("""
                        a stale RED blocks a commit over a failure that may already be fixed,                         which teaches people to re-run until it clears. Excluding it is what makes                         the report say "nobody has run this here", which is true.""")
                .isTrue();
        assertThat(isStale("4b20d8e0", "4b20d8e0"))
                .as("and a stamp at HEAD is exactly what a colour is")
                .isFalse();

        // Both unknowns, and they are the same answer for the same reason: staleness invented from
        // an absence is the mistake in the other direction.
        assertThat(isStale(null, "4b20d8e0"))
                .as("a stamp with no sha predates the field; it cannot be shown to be about "
                        + "another commit")
                .isFalse();
        assertThat(isStale("4b20d8e0", null))
                .as("and neither can it in a checkout with no git to ask")
                .isFalse();
    }

    @Test
    @DisplayName("SH1103: the same commit with a different ledger is not the tree the stamp certified")
    void aShipAfterTheRunLeavesTheStampStale() {
        assertThat(isStale("4b20d8e0", "4b20d8e0", "aaaa", "bbbb"))
                .as("SH1103: a ship rewrites the ledger at the HEAD a green run was taken at; by sha "
                        + "alone that run read as current, and a commit landed over a red nobody saw")
                .isTrue();
        assertThat(isStale("4b20d8e0", "4b20d8e0", "aaaa", "aaaa")).isFalse();
        assertThat(isStale("4b20d8e0", "4b20d8e0", null, "bbbb"))
                .as("a stamp written before the field existed cannot be shown to be about other inputs")
                .isFalse();
        assertThat(inputsDigest()).as("this tree's inputs can be digested").matches("[0-9a-f]{64}");
    }

    /**
     * Was this stamp taken against the tree being judged? (SH770)
     *
     * <p><b>Unknown either side is not stale.</b> A stamp with no sha, or a checkout with no git,
     * cannot be shown to be about another commit, and inventing staleness from an absence is the
     * same mistake in the other direction.</p>
     *
     * <p>The commit alone, and deliberately not SH764's {@code compiles()} predicate. That one
     * answers <em>could this change have produced different classes</em>, which is the right
     * question about the JVM and the wrong one here: <b>no gate writes a {@code java} stamp</b> —
     * this test is the java gate and cannot stamp its own outcome — so every stamp that exists
     * belongs to {@code js} or {@code conformance}, and both read the whole tree.
     * {@code pnpm test} reads {@code ROADMAP.md} and {@code CHANGELOG.md}; {@code pnpm conformance}
     * reads {@code docs/}. A documentation commit really can flip either colour, so there is
     * nothing provably inert to exempt.</p>
     */
    static boolean isStale(String stampSha, String head) {
        if (stampSha == null || head == null || stampSha.isBlank() || head.isBlank()) {
            return false;
        }
        return !stampSha.trim().equals(head.trim());
    }

    /**
     * SH1103 — the same commit with different inputs is a different tree. A ship writes the ledger at
     * the HEAD a green run was taken at, so the sha alone read that run as current. Unknown either side
     * is not stale, for {@link #isStale}'s reason.
     */
    static boolean isStale(String stampSha, String head, String stampInputs, String inputs) {
        if (isStale(stampSha, head)) {
            return true;
        }
        if (stampSha == null || head == null || stampSha.isBlank() || head.isBlank()) {
            return false;
        }
        return stampInputs != null && inputs != null && !stampInputs.equals(inputs);
    }

    private static boolean staleStamp(String body, String head) {
        return isStale(group(STAMP_SHA, body), head, group(STAMP_INPUTS, body), inputsDigest());
    }

    /** The digest `gate-stamp.mjs` writes, over this tree's inputs now, or {@code null} if unreadable. */
    static String inputsDigest() {
        try {
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            for (String input : INPUTS) {
                Path file = ROOT.resolve(input);
                sha.update(input.getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
                sha.update(Files.isRegularFile(file)
                        ? Files.readAllBytes(file) : "<absent>".getBytes(StandardCharsets.UTF_8));
                sha.update((byte) 0);
            }
            return java.util.HexFormat.of().formatHex(sha.digest());
        } catch (IOException | java.security.NoSuchAlgorithmException cannotRead) {
            return null;
        }
    }

    /** HEAD, or {@code null} where git cannot say — the same tolerance the stamp writer has. */
    private static String headSha() {
        try {
            Process git = new ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(ROOT.toFile())
                    .redirectErrorStream(true)
                    .start();
            String out = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return git.waitFor() == 0 ? out.trim() : null;
        } catch (IOException | InterruptedException cannotAsk) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
