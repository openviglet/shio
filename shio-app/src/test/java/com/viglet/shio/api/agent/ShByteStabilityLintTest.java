/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;

/**
 * SH136 — <b>byte stability is a build guard, not a habit.</b>
 *
 * <h2>Why this is a lint and not a behavioural test</h2>
 *
 * <p>This is the first invariant in the codebase that <b>cannot</b> be tested by observing
 * behaviour, and the reason is worth stating precisely. {@code Map.of} / {@code Map.copyOf}
 * iterate in an order derived from a salt {@code ImmutableCollections} initialises <em>once
 * per JVM</em>. So within any one test process the order is fixed, and the obvious
 * assertion — build the payload twice, compare the bytes — passes whatever the code does.
 * It is the same shape of tautology SH130 removed from the version check: an assertion that
 * cannot fail at the moment it has a job to do.</p>
 *
 * <p>SH130 found the first instance by accident (a golden transcript disagreeing across
 * boots). A lint covers the call sites that do not exist yet, which is the §VIII.10 rule:
 * when an invariant is about how code is written, prefer a textual guard.</p>
 *
 * <h2>What it found on the day it was written</h2>
 *
 * <p>Five {@code Map.copyOf} call sites, <b>four of them defects</b>, every one the identical
 * shape: build a {@code TreeMap} or {@code LinkedHashMap} <em>deliberately, for
 * determinism</em>, then discard that order in the return expression. The worst fed SH101's
 * render digest, whose whole promise is that the same page hashes the same — and it was
 * unstable only for a single-field post, because the multi-field path re-sorts on merge.
 * Half-broken determinism, invisible to every test that existed.</p>
 *
 * @author Alexandre Oliveira
 */
@ShSourceScan(value = ShSourceScan.Reads.BOTH,
        reason = "The rule is about code and the waiver is a `byte-stable-exempt` comment on the line,"
                + " which is the whole escape hatch this lint was designed around.")
class ShByteStabilityLintTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /**
     * The trap: it reads as a defensive copy and is also a re-ordering. Anywhere the order
     * is observable — serialized, hashed, or rendered — the replacement is
     * {@code ShStableMap.copyOf}, which keeps whatever order the source had.
     *
     * <p>The lookbehind is load-bearing: {@code ShStableMap} ends in {@code Map}, so a plain
     * substring search flags the fix as the defect — which it did, on the first run.</p>
     */
    private static final Pattern BANNED =
            Pattern.compile("(?<![A-Za-z_])(?:Map|Set)\\.copyOf\\(");

    /**
     * The shape SH197 finished the class with: the stability helper wrapped around a
     * <em>hash-ordered literal</em>.
     *
     * <p>{@code ShStableMap.copyOf} keeps whatever order its source had, so
     * {@code ShStableMap.copyOf(Map.of(a, 1, b, 2))} freezes a per-JVM salt's order and reads
     * exactly like the fix — which is worse than the raw defect, because the next reader sees the
     * helper and stops looking. It was live in {@code ShAgentOps.SPECS} with ten entries.</p>
     *
     * <p><b>Why this rather than banning multi-entry {@code Map.of} outright</b>, which is the
     * question §VIII.14 left open and which the population answers: 112 {@code Map.of}/{@code Set.of}
     * sites in {@code main}, of which <b>48 are zero-arg and 21 single-entry</b> — order-free by
     * construction, so a rule that flagged them would spend its credibility on 69 non-defects. Of
     * the 43 multi-entry ones, <b>27 are MCP schema builders</b> whose serialization point is
     * already proved key-sorted by {@code mcpPayloadsAreCanonicalized} below, and the rest are JSON
     * response bodies where object key order is not observable to a parser. Banning the
     * construction therefore needs ~40 exemptions to say what two rules already say — and a guard
     * that noisy is one somebody deletes. The invariant is not "never build a hash-ordered map"; it
     * is <b>"no hash-ordered iteration reaches bytes that get compared"</b>, and that is guarded
     * where the bytes are made (the canonical rule) plus here, where the guard itself is faked.</p>
     */
    private static final Pattern FAKE_STABILITY =
            Pattern.compile("ShStableMap\\.copyOf\\(\\s*(?:Map|Set)\\.of\\(");

    /** Costs a sentence, per §VIII.10 — a guard with no hatch gets deleted when it is wrong. */
    private static final String EXEMPT = "byte-stable-exempt";

    /**
     * Every line under {@code root} matching {@code banned} that carries no {@code EXEMPT} note.
     *
     * <p>SH197 — one walker for the whole class rather than one per rule: the next rule to be added
     * should cost a pattern and an assertion, and a third copy of this loop is how the rules would
     * come to disagree about where the hatch may sit.</p>
     */
    private static List<String> offenders(Path root, Pattern banned) throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!banned.matcher(line).find()) {
                        continue;
                    }
                    // The hatch may sit on the line or anywhere in the three lines above it,
                    // so a multi-line reason reads naturally in the source.
                    boolean exempted = line.contains(EXEMPT)
                            || lines.subList(Math.max(0, i - 3), i).stream()
                                    .anyMatch(previous -> previous.contains(EXEMPT));
                    if (!exempted) {
                        offenders.add(file.getFileName() + ":" + (i + 1) + " — " + line.trim());
                    }
                }
            }
        }
        return offenders;
    }

    @Test
    @DisplayName("no Map.copyOf/Set.copyOf reaches a payload without a stated reason")
    void noHashOrderedCopyOnAnObservablePath() throws IOException {
        List<String> offenders = offenders(MAIN, BANNED);

        assertThat(offenders)
                .withFailMessage("""
                        These build an immutable copy whose iteration order comes from a \
                        per-JVM salt, so the same data serializes to different bytes after a \
                        restart and any hash over that iteration moves with it. It is \
                        invisible to a test in one process, because the salt is fixed per \
                        process:
                          %s
                        Fix: ShStableMap.copyOf(map), which keeps the order the source had \
                        (a TreeMap stays sorted, a LinkedHashMap stays in insertion order). \
                        If the order is genuinely not observable — a keyed lookup that is \
                        never iterated or serialized — add a `byte-stable-exempt: <reason>` \
                        comment. See docs/agents/agent-surface.md (Byte stability) and \
                        IMPROVEMENTS.md SS VIII.11.""",
                        String.join("\n  ", offenders))
                .isEmpty();
    }

    /**
     * The stability helper may not be handed a literal (SH197).
     *
     * <p>With two or more entries it freezes a per-JVM salt's order behind a name that promises the
     * opposite; with none or one there is no order to keep, so the wrapper is a guarantee where
     * none was needed. Both are the same reading defect — the next person sees {@code ShStableMap}
     * and stops looking — so the rule is simply that the source is never a literal. Give the helper
     * a {@code TreeMap} or a {@code LinkedHashMap}, or build the literal directly.</p>
     */
    @Test
    @DisplayName("ShStableMap.copyOf is never handed a Map.of/Set.of literal")
    void theStabilityHelperIsNotWrappedAroundAHashOrderedLiteral() throws IOException {
        List<String> offenders = offenders(MAIN, FAKE_STABILITY);

        assertThat(offenders)
                .withFailMessage("""
                        `ShStableMap.copyOf` keeps the order its source had, and a `Map.of` with \
                        two or more entries has the order of a per-JVM salt — so this freezes a \
                        random order behind the name of the fix, which is worse than the raw \
                        defect because the next reader sees the helper and stops looking. With \
                        one entry or none there is no order to keep and the wrapper claims a \
                        guarantee it is not providing:
                          %s
                        Fix: pass a TreeMap or a LinkedHashMap built in the order you mean (SH197 \
                        did this for ShAgentOps.SPECS, keying it off each spec's own `op`), or \
                        drop the wrapper and use the literal directly where the order cannot be \
                        observed.""", String.join("\n  ", offenders))
                .isEmpty();
    }

    /**
     * Both source rules can fail (SH197).
     *
     * <p>A textual lint whose pattern has quietly stopped matching passes forever and reads as
     * coverage — the failure this project has now hit twice (SH362's fixture, SH142's suppression
     * list). So each pattern is run against a line that <em>is</em> the defect and a line that is
     * the fix, here rather than in the codebase, because the codebase is meant to hold neither.</p>
     */
    @Test
    @DisplayName("the guard is not vacuous: each pattern matches the defect and spares the fix")
    void theGuardCanFail() {
        assertThat(BANNED.matcher("return Map.copyOf(sorted);").find()).isTrue();
        assertThat(BANNED.matcher("return ShStableMap.copyOf(sorted);").find())
                .as("the lookbehind is load-bearing — ShStableMap ends in Map, and a plain "
                        + "substring search flagged the fix as the defect on its first run")
                .isFalse();

        assertThat(FAKE_STABILITY.matcher("SPECS = ShStableMap.copyOf(Map.of(A, one, B, two));")
                .find()).isTrue();
        assertThat(FAKE_STABILITY.matcher("SPECS = ShStableMap.copyOf(new TreeMap<>(source));")
                .find())
                .as("an ordered source is the whole point of the helper")
                .isFalse();
    }

    /**
     * The other half of the class: a {@code Map} that becomes JSON without being sorted on the way
     * out. Both of the MCP server's serialization points — the tool schemas and an error's
     * {@code data} — must go through {@code canonical(...)}.
     *
     * <p><b>SH197 widened it from one file to the package.</b> It used to read {@code ShMcpServer}
     * by name, which is the "lint that reports zero forever" failure one file along: a second class
     * that built a payload would never be looked at, and the rule would keep passing while saying
     * nothing. The whole {@code mcp} package is walked now, and the assertion that the scan found
     * something is what keeps a rename from turning the guard off.</p>
     */
    @Test
    @DisplayName("every valueToTree in the MCP package is canonicalized")
    void mcpPayloadsAreCanonicalized() throws IOException {
        Path mcp = MAIN.resolve(Path.of("com", "viglet", "shio", "api", "agent", "mcp"));
        List<String> raw = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mcp)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                        .filter(line -> line.contains("valueToTree("))
                        .map(line -> file.getFileName() + " — " + line.trim())
                        .forEach(raw::add);
            }
        }

        assertThat(raw)
                .as("a scan that finds nothing is a rule that has stopped asking")
                .isNotEmpty();
        assertThat(raw)
                .withFailMessage("""
                        Every Map that becomes JSON here must be key-sorted first, or \
                        `tools/list` and the teaching payloads serialize differently on \
                        every boot (SH130 found this; SH136 generalized it). Wrap it in \
                        canonical(...):
                          %s""", String.join("\n  ", raw))
                .allMatch(line -> line.contains("canonical(objectMapper.valueToTree("));
    }
}
