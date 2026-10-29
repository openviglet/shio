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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH351 — a roadmap line's {@code → §x.y} is the only route from a task to its design, so it has to
 * resolve, and it has to resolve to the <em>right</em> section.
 *
 * <h2>Why this is a build failure and not a proofreading habit</h2>
 *
 * <p>The three docs files are wired so that {@code ROADMAP.md} can stay terse: one sentence per task —
 * what, why, and a pointer. SH341 measured the drift when that rule is not kept (95 lines averaging
 * 142 words) and the answer was "the reasoning belongs in IMPROVEMENTS, which is what the pointer
 * addresses". That makes the pointer <b>load-bearing</b>: a dead one silently converts a terse line
 * into a line with no design at all, and nothing was checking a single one of them.</p>
 *
 * <h2>What it was hiding, measured before the fix</h2>
 *
 * <table>
 *   <caption>{@code docs/IMPROVEMENTS.md}, 121 sections, and {@code docs/ROADMAP.md}, 82 pointers</caption>
 *   <tr><th>found</th><th>what it was</th></tr>
 *   <tr><td>3 colliding numbers</td>
 *       <td>{@code XVI.17}, {@code XVI.31} and {@code XVI.53} each named two different tasks — so the
 *       pointer was ambiguous and the reader got whichever came first</td></tr>
 *   <tr><td>1 section written twice</td>
 *       <td>SH302's rationale, verbatim, in two places</td></tr>
 *   <tr><td>9 unnumbered sections</td>
 *       <td>rationale nothing could point at, which is what made <b>11 of 82 pointers</b> resolve to
 *       nothing at all</td></tr>
 *   <tr><td>1 stale section</td>
 *       <td>a shipped task's (SH300's) section still present — the thing that hid the collision, and
 *       the rule "IMPROVEMENTS is for unshipped sections only" exists to prevent exactly it</td></tr>
 *   <tr><td>14 pointers landing elsewhere</td>
 *       <td>resolving, but to a section that does not name the task — 9 because the task's own section
 *       had just been given a number, 5 pointing at an epic on purpose</td></tr>
 * </table>
 *
 * <p>Only 3 pointers were genuinely orphaned once the numbering was fixed (SH259, SH265, SH301 — no
 * section covers them), and those lost their pointer: a pointer to nowhere is worse than no pointer,
 * because it reads as though the design exists.</p>
 *
 * <h2>Four checks, because roadkeep owns the fifth</h2>
 *
 * <p>"Every roadmap pointer resolves to a section that exists" left this file when the project
 * adopted roadkeep: {@code roadkeep lint} reports it as {@code ref.unresolved}, from the same
 * parser that writes the lines, so keeping a second implementation here would be two readers of
 * one format waiting to disagree.</p>
 *
 * <p>The four that stay are <b>not</b> duplicates, and the reason is the numbering scheme.
 * roadkeep keys a section on its anchor and asks whether a <em>task line</em> points at it; its
 * ownership checks ({@code section.orphan}, {@code section.stale}) only fire for an id-shaped
 * anchor, and this project's anchors are a hand-kept outline ({@code §XVI.12}, declared as
 * {@code ref_scheme = "outline"}). Under that scheme roadkeep cannot tell which task a section
 * belongs to — the id is in the heading's prose, which it does not read. So "a section carries a
 * number", "no two say the same thing", "a task points at its own section" and "a section whose
 * task has left says why" remain this repository's to enforce.</p>
 *
 * <p>The last of them is the one with teeth. "Every pointer resolves" would have passed on eight lines
 * pointing at a section about three other tasks; <b>if a section names a task, that task's line must
 * point at it</b>. An epic section that names none of its follow-ups is unaffected, which is why
 * §VI.1's four and §VIII.11's one need no exemption list — they have no section of their own to be
 * pointed at.</p>
 *
 * @author Alexandre Oliveira
 */
class ShDesignRationalePointerLintTest {

    private static final Path IMPROVEMENTS = Path.of("..", "docs", "IMPROVEMENTS.md");
    private static final Path ROADMAP = Path.of("..", "docs", "ROADMAP.md");

    /** {@code ### XVI.12 Some title (SH123)} — the number is what a pointer names. */
    /**
     * A numbered rationale heading.
     *
     * <p>The trailing {@code (?:\.\d+)*} is a <b>subsection</b> — {@code §XV.0.3}, {@code §X.3.1}.
     * roadkeep's model has them (its {@code ship} reports what it found {@code nested}, and a
     * subsection is owned by its parent rather than by a task line of its own), and this lint
     * predates that: it read a third level as <em>no number at all</em> and reported ten perfectly
     * addressable headings as unaddressable. A pointer can name {@code §XV.0.3} exactly as well as
     * {@code §XV.14}, which is the property SH351 is about.</p>
     *
     * <p>Every Roman numeral, not only {@code I V X L}: the families reached XCI, and SH1286's
     * {@code §XCIII.1} was read as a heading with no number at all, the same misreading one digit on.</p>
     */
    private static final String ANCHOR = "(?:[IVXLCDM]+|\\d+)(?:\\.\\d+)+";

    private static final Pattern SECTION =
            Pattern.compile("^### (?:(" + ANCHOR + ")\\s+)?(.*)$");
    /** {@code → §XVI.12} in a roadmap line, or {@code → §XV.0.3} at a subsection. */
    private static final Pattern POINTER = Pattern.compile("→ §(" + ANCHOR + ")");
    /** A status-marked roadmap task line: the active backlog. */
    private static final Pattern TASK_LINE = Pattern.compile("^- [📋💭⏳🛠] \\*\\*(SH\\d+)\\*\\*");
    private static final Pattern TASK_ID = Pattern.compile("SH\\d+");

    private record Section(String number, String header, String body, int line) {

        List<String> tasks() {
            List<String> ids = new ArrayList<>();
            Matcher matcher = TASK_ID.matcher(header);
            while (matcher.find()) {
                ids.add(matcher.group());
            }
            return ids;
        }

        /** A section may stay after its task ships only if it says so — SH275's pattern. */
        boolean declaresItsFate() {
            return body.contains("✅") || body.contains("shipped") || body.contains("Shipped")
                    || body.contains("superseded") || body.contains("retired");
        }
    }

    private static List<String> lines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("SH351's lint cannot read " + path.toAbsolutePath(), e);
        }
    }

    private static List<Section> sections() {
        List<String> body = lines(IMPROVEMENTS);
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            if (body.get(i).startsWith("### ")) {
                starts.add(i);
            }
        }
        List<Section> sections = new ArrayList<>();
        for (int s = 0; s < starts.size(); s++) {
            int from = starts.get(s);
            int to = s + 1 < starts.size() ? starts.get(s + 1) : body.size();
            Matcher matcher = SECTION.matcher(body.get(from));
            String number = matcher.matches() ? matcher.group(1) : null;
            sections.add(new Section(number, body.get(from),
                    String.join("\n", body.subList(from, to)), from + 1));
        }
        return sections;
    }

    @Test
    @DisplayName("every rationale section carries a number, so a pointer can name it")
    void everySectionCarriesANumber() {
        List<String> unnumbered = sections().stream()
                .filter(section -> section.number() == null)
                .map(section -> "IMPROVEMENTS.md:" + section.line() + " " + section.header())
                .toList();

        assertThat(unnumbered)
                .as("""
                        SH351: nine sections had no number, which is why eleven of the roadmap's \
                        eighty-two pointers resolved to nothing. A section nothing can address is \
                        reasoning the terse line above it promised and cannot deliver.

                        Fix: give it the next free number in its family, or fold it into the section \
                        that owns the subject.""")
                .isEmpty();
    }

    @Test
    @DisplayName("no two sections share a number, and none is written twice")
    void sectionsAreUniqueByNumberAndByBody() {
        Map<String, List<String>> byNumber = new TreeMap<>();
        Map<String, List<String>> byHeader = new TreeMap<>();
        for (Section section : sections()) {
            if (section.number() != null) {
                byNumber.computeIfAbsent(section.number(), key -> new ArrayList<>())
                        .add("line " + section.line());
            }
            byHeader.computeIfAbsent(section.header(), key -> new ArrayList<>())
                    .add("line " + section.line());
        }
        byNumber.values().removeIf(where -> where.size() < 2);
        byHeader.values().removeIf(where -> where.size() < 2);

        assertThat(byNumber)
                .as("""
                        A number naming two sections makes every `→ §` pointer to it ambiguous, and \
                        the reader gets whichever comes first. Three did.""")
                .isEmpty();
        assertThat(byHeader)
                .as("SH302's rationale was in this file twice, verbatim; the second copy is what a "
                        + "reader would eventually correct in one place only")
                .isEmpty();
    }

    @Test
    @DisplayName("a task with its own section is pointed at that section")
    void aTaskWithItsOwnSectionIsPointedAtIt() {
        Map<String, String> sectionOf = new TreeMap<>();
        Map<String, String> headerOf = new TreeMap<>();
        for (Section section : sections()) {
            if (section.number() == null) {
                continue;
            }
            headerOf.put(section.number(), section.header());
            section.tasks().forEach(task -> sectionOf.putIfAbsent(task, section.number()));
        }

        Map<String, String> misdirected = new LinkedHashMap<>();
        for (String line : lines(ROADMAP)) {
            Matcher task = TASK_LINE.matcher(line);
            Matcher pointer = POINTER.matcher(line);
            if (!task.find() || !pointer.find()) {
                continue;
            }
            String id = task.group(1);
            String own = sectionOf.get(id);
            String named = pointer.group(1);
            String landing = headerOf.getOrDefault(named, "");
            if (own != null && !own.equals(named) && !landing.contains(id)) {
                misdirected.put(id, "points at §" + named + " (" + landing.replace("### ", "")
                        + ") while §" + own + " is the section that names it");
            }
        }

        assertThat(misdirected)
                .as("""
                        The check with teeth. "Every pointer resolves" passed while eight lines pointed \
                        at one section about three other tasks - resolving, and wrong. An epic section \
                        that names none of its follow-ups is unaffected, so the four §VI.1 lines and \
                        the one §VIII.11 line need no exemption: they have no section of their own.""")
                .isEmpty();
    }

    @Test
    @DisplayName("a section whose task is no longer in the backlog says why it is still here")
    void aSectionForAShippedTaskSaysSo() {
        TreeSet<String> active = new TreeSet<>();
        for (String line : lines(ROADMAP)) {
            Matcher matcher = TASK_LINE.matcher(line);
            if (matcher.find()) {
                active.add(matcher.group(1));
            }
        }

        Map<String, String> stale = new LinkedHashMap<>();
        for (Section section : sections()) {
            List<String> tasks = section.tasks();
            if (tasks.isEmpty() || section.declaresItsFate()) {
                continue;
            }
            if (tasks.stream().noneMatch(active::contains)) {
                stale.put("IMPROVEMENTS.md:" + section.line(), section.header());
            }
        }

        assertThat(stale)
                .as("""
                        IMPROVEMENTS holds rationale for *unshipped* work; `git log` is the history. A \
                        section left behind after its task shipped is what hid SH351's own collision, \
                        and it sends a reader to reasoning for work already done.

                        Fix: delete it when the task ships (the rule), or - if it stays because \
                        siblings still reference it - leave the ✅ shipped pointer that says so.""")
                .isEmpty();
    }
}
