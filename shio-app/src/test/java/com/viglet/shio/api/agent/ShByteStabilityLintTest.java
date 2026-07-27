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

    /** Costs a sentence, per §VIII.10 — a guard with no hatch gets deleted when it is wrong. */
    private static final String EXEMPT = "byte-stable-exempt";

    @Test
    @DisplayName("no Map.copyOf/Set.copyOf reaches a payload without a stated reason")
    void noHashOrderedCopyOnAnObservablePath() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!BANNED.matcher(line).find()) {
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
                        comment. See agents.md and IMPROVEMENTS.md SS VIII.11.""",
                        String.join("\n  ", offenders))
                .isEmpty();
    }

    /**
     * The other half of the class: a {@code Map} that becomes JSON without being sorted on
     * the way out. {@code ShMcpServer} is where every MCP payload is built, and it has
     * exactly two points where an in-memory container becomes bytes — the tool schemas and
     * an error's {@code data}. Both must go through {@code canonical(...)}; this pins that
     * the count has not grown behind a third, uncanonicalized one.
     */
    @Test
    @DisplayName("every valueToTree in the MCP server is canonicalized")
    void mcpPayloadsAreCanonicalized() throws IOException {
        Path server = MAIN.resolve(Path.of("com", "viglet", "shio", "api", "agent", "mcp",
                "ShMcpServer.java"));
        List<String> raw = Files.readAllLines(server, StandardCharsets.UTF_8).stream()
                .filter(line -> line.contains("valueToTree(")).toList();

        assertThat(raw).isNotEmpty();
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
