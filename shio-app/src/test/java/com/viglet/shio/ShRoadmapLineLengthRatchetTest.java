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
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH341 — the roadmap's one-sentence rule, asserted instead of remembered.
 *
 * <h2>Why a ratchet and not a limit</h2>
 *
 * <p>A ROADMAP entry is one sentence: what + why + a {@code →} pointer, with the reasoning in
 * IMPROVEMENTS.md at the other end of that pointer. The rule is written down in three places and has
 * been broken continuously: SH341 measured 95 active lines averaging <b>142</b> words, worst 555 —
 * and six of the worst eight were written in the session that then found the problem. That is a
 * drift the process invites, not a lapse, which is precisely the kind of rule that has to stop
 * depending on the writer noticing it.</p>
 *
 * <p>A hard limit cannot land: {@value #LIMIT} characters would fail the build on
 * {@value #OVER_LIMIT} existing lines today, and the fix for each is editorial — some of those lines
 * are the only home their reasoning has, so cutting them without checking their {@code → §} coverage
 * would destroy the argument rather than move it. So this is a <b>ratchet</b>: the count may fall and
 * may not rise. A new long line fails; shortening one and not updating the number here fails too,
 * which is what keeps the ceiling honest as SH341's editorial half proceeds.</p>
 *
 * <p><b>It is not `roadkeep lint`.</b> That tool owns the writes and reports all of this (249
 * findings today, 47 of them {@code why.too-long}), but it is a Python CLI a clean-room build has no
 * reason to have installed — so the gate here re-derives the one number it can from the file itself.
 * Same reasoning as SH290 reading {@code import/post-types.json}: the artefact is in the repository,
 * so the assertion needs nothing that is not.</p>
 *
 * @author Alexandre Oliveira
 */
class ShRoadmapLineLengthRatchetTest {

    private static final Path ROADMAP = Path.of("..", "docs", "ROADMAP.md");

    /** roadkeep's own `line` limit for this project ({@code roadkeep.toml}), so one rule, one number. */
    private static final int LIMIT = 320;

    /**
     * Lines over {@value #LIMIT} characters on 2026-08-02, after Block P's ten ships.
     *
     * <p>Lower it when you shorten one. Never raise it: a new line over the limit belongs in
     * IMPROVEMENTS.md, which is what the {@code →} pointer addresses.</p>
     *
     * <p><b>45 → 42 within an hour of being written</b>, and not by an editorial pass: roadkeep
     * refuses to <em>ship</em> a task whose line — or whose dependents' lines, which shipping
     * re-annotates — is over the limit, so SH341's own line, SH275's and SH276's each had to become
     * one sentence before the work they described could be recorded. That is the ratchet working
     * through the tool rather than through anybody's memory, which is what it was for.</p>
     */
    private static final int OVER_LIMIT = 42;

    /** A task line: marker, bold id. The blockquote prose between them is not governed by this. */
    private static final Pattern TASK =
            Pattern.compile("^\\s*-\\s+(📋|💭|⏳|🛠)\\s+\\*\\*SH\\d+\\*\\*.*");

    @Test
    @DisplayName("SH341: the number of over-long roadmap lines never grows")
    void theCountNeverGrows() {
        List<String> over = new ArrayList<>();
        for (String line : taskLines()) {
            if (line.length() > LIMIT) {
                over.add(line.substring(0, Math.min(90, line.length())) + "… (" + line.length()
                        + " chars)");
            }
        }

        assertThat(over.size())
                .as("""
                        %d task lines are over %d characters, and the recorded ceiling is %d. If \
                        you added one: a roadmap line is one sentence — what + why + the → \
                        pointer — and the paragraph you are writing belongs in IMPROVEMENTS.md at \
                        the other end of that pointer, which is the file whose job is rationale. \
                        If you SHORTENED one: lower OVER_LIMIT to %d in the same commit, because a \
                        ceiling nobody lowers stops being a ratchet and becomes a floor.
                        %s""", over.size(), LIMIT, OVER_LIMIT, over.size(), String.join("\n", over))
                .isLessThanOrEqualTo(OVER_LIMIT);
    }

    @Test
    @DisplayName("the guard is not vacuous: it finds the roadmap's task lines")
    void theScanFindsTaskLines() {
        List<String> tasks = taskLines();

        assertThat(tasks)
                .as("""
                        a scan matching nothing would pass forever while the file grew — and this \
                        one is a regex over a marker glyph, so a change to the marker set in \
                        roadkeep.toml silently empties it""")
                .hasSizeGreaterThan(20);
        assertThat(TASK.matcher("- 💭 **SH999** (deps: SH1) **Something** — because. → §I.1").matches())
                .as("the shape roadkeep writes")
                .isTrue();
        assertThat(TASK.matcher("> ✅ **SH183 has shipped** → CHANGELOG.md").matches())
                .as("and not the shipped-pointer blockquotes, which are prose and have no limit")
                .isFalse();
    }

    private static List<String> taskLines() {
        try {
            return Files.readAllLines(ROADMAP).stream()
                    .filter(line -> TASK.matcher(line).matches())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + ROADMAP.toAbsolutePath(), e);
        }
    }
}
