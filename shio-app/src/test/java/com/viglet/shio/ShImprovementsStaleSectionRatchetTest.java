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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH356 — rationale for a task that already shipped, counted so it can only fall.
 *
 * <h2>What a stale section is, and why deleting them all is the wrong answer</h2>
 *
 * <p>IMPROVEMENTS.md holds design rationale for <b>unshipped</b> work; the ledger says what shipped
 * and {@code git log} keeps the prose. So a section whose task is already in CHANGELOG.md is in the
 * wrong file — {@code ship} deletes the rationale as its third edit, and a survivor is one written or
 * moved by hand before roadkeep owned the files. There were <b>43</b>.</p>
 *
 * <p>The tempting fix is to delete them, and reading the first four refutes it: they open with
 * "Design records that bind what follows" and carry sentences like <em>every future address-taking
 * surface inherits this</em>. That is an <b>invariant filed under a shipped task's number</b>, not
 * history, and deleting it loses a rule governing work nobody has started. The triage is therefore a
 * reading — a standing rule moves to its {@code docs/agents/} area file and then the section goes; a
 * history section just goes — which is written down in {@code docs/agents/docs-process.md}.</p>
 *
 * <h2>Why a ratchet, and why this one cannot start where SH341's finished</h2>
 *
 * <p>{@code ShRoadmapLineLengthRatchetTest} took the roadmap to zero because shortening a line is
 * reversible and cheap. Dropping a section is <b>destructive</b>, so this count may only fall, one
 * reading at a time, and a build that fails because somebody moved a rule out is the wrong incentive.
 * What it does prevent is the count going <em>up</em>: a new rationale written under a shipped task's
 * number, which is how all 43 got here.</p>
 *
 * @author Alexandre Oliveira
 */
class ShImprovementsStaleSectionRatchetTest {

    private static final Path IMPROVEMENTS = Path.of("..", "docs", "IMPROVEMENTS.md");
    private static final Path CHANGELOG = Path.of("..", "docs", "CHANGELOG.md");

    /**
     * Sections whose task is in the ledger, on 2026-08-02 — 43 at the start, seven read and
     * triaged, and the seven are why the count is not simply a deletion queue: six carried standing
     * rules that moved into {@code agent-surface.md} and {@code mcp.md}, and one was history.
     *
     * <p>Lower it when you triage one. Raising it means a rationale section was written for work that
     * has already shipped, which is what this exists to stop.</p>
     */
    private static final int STALE = 29;

    /** A section heading at either level, with the task ids it names in its title. */
    private static final Pattern HEADING =
            Pattern.compile("^#{3,4} ([IVXLC]+\\.\\d+) (.*)$", Pattern.MULTILINE);

    /** A ledger entry's id — the bold form roadkeep writes. */
    private static final Pattern LEDGER_ID = Pattern.compile("^- \\*\\*(SH\\d+)\\*\\*", Pattern.MULTILINE);

    private static final Pattern TASK_ID = Pattern.compile("SH\\d+");

    @Test
    @DisplayName("SH356: no new rationale is written under a task that already shipped")
    void theStaleCountNeverGrows() {
        Set<String> shipped = shippedIds();
        List<String> stale = new ArrayList<>();

        Matcher heading = HEADING.matcher(read(IMPROVEMENTS));
        while (heading.find()) {
            Set<String> ids = new TreeSet<>();
            Matcher inTitle = TASK_ID.matcher(heading.group(2));
            while (inTitle.find()) {
                ids.add(inTitle.group());
            }
            // Every id in the title, not any: a section titled "(SH343, SH344, SH348)" is only stale
            // once all three have shipped — while one is open the rationale is still live design.
            if (!ids.isEmpty() && shipped.containsAll(ids)) {
                stale.add("§" + heading.group(1) + " " + ids);
            }
        }

        assertThat(stale.size())
                .as("""
                        %d sections explain a task the ledger already carries, against a recorded %d. \
                        Rising means new rationale was filed under a shipped id — write it in the \
                        area file under docs/agents/ instead, which is where somebody working in \
                        that area will look. Falling means you triaged one: lower STALE here in the \
                        same commit. The triage is a reading, not a deletion — see docs-process.md, \
                        because most of these are standing rules rather than history.
                        %s""", stale.size(), STALE, String.join("\n", stale))
                .isLessThanOrEqualTo(STALE);
    }

    @Test
    @DisplayName("SH356: no open task points at a section that is not there")
    void everyOpenPointerResolves() {
        Set<String> anchors = new TreeSet<>();
        Matcher heading = HEADING.matcher(read(IMPROVEMENTS));
        while (heading.find()) {
            anchors.add(heading.group(1));
        }

        List<String> dangling = new ArrayList<>();
        Matcher line = ROADMAP_POINTER.matcher(read(ROADMAP));
        while (line.find()) {
            if (!anchors.contains(line.group(2))) {
                dangling.add(line.group(1) + " → §" + line.group(2));
            }
        }

        assertThat(dangling)
                .as("""
                        This is the guard that was missing, and it is here because the triage \
                        broke one: §VIII.11 was stale by the rule above — every task in its title \
                        had shipped — and SH197, which is OPEN, pointed at it. Dropping it left a \
                        live task pointing at nothing, which reads as a design that exists.

                        So "every id in the title has shipped" is necessary and not sufficient. \
                        Before dropping a section, check who points at it: an open task's \
                        rationale has to be written somewhere else first, not deleted with the \
                        history around it.""")
                .isEmpty();
    }

    /** A task line's id and the anchor it points at. */
    private static final Pattern ROADMAP_POINTER =
            Pattern.compile("\\*\\*(SH\\d+)\\*\\*[^\\n]*→ §([IVXLC]+\\.\\d+)");

    private static final Path ROADMAP = Path.of("..", "docs", "ROADMAP.md");

    @Test
    @DisplayName("the guard is not vacuous: it reads both files and finds ids in each")
    void theScanReadsBothFiles() {
        assertThat(shippedIds())
                .as("a ledger scan that came back empty would make every section look live")
                .hasSizeGreaterThan(100)
                .contains("SH183", "SH290");
        assertThat(HEADING.matcher(read(IMPROVEMENTS)).results().count())
                .as("and a heading scan that matched nothing would make every section invisible")
                .isGreaterThan(50);
    }

    private static Set<String> shippedIds() {
        Set<String> ids = new TreeSet<>();
        Matcher entries = LEDGER_ID.matcher(read(CHANGELOG));
        while (entries.find()) {
            ids.add(entries.group(1));
        }
        return ids;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
