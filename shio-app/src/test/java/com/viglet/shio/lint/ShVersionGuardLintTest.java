/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH985 — which content write can be made conditional, and which cannot.
 *
 * <h2>The asymmetry this counts</h2>
 *
 * <p>SH721 gave the agent {@code expect}: a write carrying the digest it was planned against is
 * refused when somebody wrote in between. Nothing on the console side took a version at all, so
 * since SH721 <b>the agent was protected from clobbering a curator and the curator was not
 * protected from clobbering the agent</b> — the worst-shaped version of this defect under P7,
 * because the party with the review queue and the audit trail is the one that lost work silently.</p>
 *
 * <p>The instrument lands before the fix (SH322): "which <em>other</em> writes have none of this"
 * is not a question the task that noticed one can answer. It reads the controllers as source and
 * classifies every mutating mapping on a content path as <b>guarded</b> — its signature takes
 * {@code If-Match} — or <b>unguarded</b>, with the reason a version would mean nothing there.</p>
 *
 * <h2>Why the scan counts parentheses instead of matching a signature</h2>
 *
 * <p>The first draft read the parameter list with {@code \\(([^)]*)\\)} and reported <b>one</b>
 * write in {@code ShStaticFileAPI} where there are three: a parameter annotated
 * {@code @RequestParam("file")} closes that group early, so every method whose parameters carry an
 * annotation with arguments was invisible. Under-reporting is the one way a lint like this goes
 * wrong — it is green and it is not looking — so the parameter list is taken by counting
 * parentheses, skipping string literals, which is the smallest reader that cannot be fooled by an
 * annotation.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShVersionGuardLintTest {

    /** The controllers that write content. Identity and configuration are a different question. */
    private static final List<String> CONTENT_WRITERS = List.of(
            "ShPostUnifiedAPI.java", "ShFolderAPI.java", "ShObjectAPI.java", "ShStaticFileAPI.java");

    /** What a guarded write takes: the HTTP header, by constant or by parameter name. */
    private static final Pattern GUARD = Pattern.compile("IF_MATCH|ifMatch");

    // --- why a version would mean nothing. Families, so the list is short enough to be read ------

    private static final String CREATES =
            "it creates, so there is no row a caller could have read and nothing to be unchanged";
    private static final String NOT_THE_CONTENT =
            "it writes metadata the editing form does not hold, so a digest over the post's content "
            + "would refuse writes that conflict with nothing and pass the ones that do";
    private static final String WHOLE_SUBTREE =
            "it acts on a subtree rather than on a row, and one post's digest says nothing about "
            + "what else is under a folder — the guard for this shape is the confirm token";
    private static final String TRASH_IS_THE_STATE =
            "the trash marker is the state being changed, and a caller asking to restore or purge a "
            + "trashed row has read exactly that";

    /** One write, as the lint sees it. */
    private record Write(String file, String method, boolean guarded) {
    }

    /**
     * What the tree says today, keyed by {@code file#method}.
     *
     * <p>A guarded write carries the empty string; an unguarded one carries its reason. The second
     * column is the whole value of the list: a row saying "unguarded" and nothing else is a gap
     * recorded as a fact.</p>
     */
    private static final Map<String, String> DECLARED = new TreeMap<>(Map.ofEntries(
            // Guarded (SH985) — the four writes a curator makes to a post that already exists.
            Map.entry("ShPostUnifiedAPI#update", ""),
            Map.entry("ShPostUnifiedAPI#patch", ""),
            Map.entry("ShPostUnifiedAPI#publish", ""),
            Map.entry("ShPostUnifiedAPI#delete", ""),
            // SH987 — a rollback is a write, and one onto a draft somebody has moved on from is
            // the same surprise as any other stale save.
            Map.entry("ShPostUnifiedAPI#restoreRevision", ""),
            // SH1132 — a rename is a write to a page that exists, and the lost update is concrete:
            // a curator renaming a page an agent has already moved takes the address back without
            // either of them knowing. Required on the real run only, since a plan writes nothing.
            Map.entry("ShPostUnifiedAPI#move", ""),

            // SH1021 — a bulk verb acts on a selection, and `If-Match` is one header carrying one
            // row's version: there is no spelling of it that means "these forty, each at the
            // version I last read". The per-op `expect` on the batch vocabulary is where a caller
            // who cares states it, row by row, which is the only place the question has an answer.
            Map.entry("ShPostUnifiedAPI#bulk", "it acts on a selection, and one If-Match cannot "
                    + "carry a version per row; the batch's per-op `expect` is where that is said"),

            Map.entry("ShPostUnifiedAPI#create", CREATES),
            Map.entry("ShPostUnifiedAPI#translate", CREATES),
            Map.entry("ShPostUnifiedAPI#unpublish", "it removes the live snapshot and leaves the "
                    + "draft being edited, so the row a curator is holding is not the row it changes"),
            Map.entry("ShPostUnifiedAPI#restore", "it overwrites the draft with what is live, which "
                    + "is the one write whose whole purpose is to discard the draft the caller holds"),
            Map.entry("ShPostUnifiedAPI#schedule", NOT_THE_CONTENT),
            Map.entry("ShPostUnifiedAPI#untrash", TRASH_IS_THE_STATE),
            Map.entry("ShPostUnifiedAPI#purge", TRASH_IS_THE_STATE),

            Map.entry("ShFolderAPI#create", CREATES),
            Map.entry("ShFolderAPI#update", "a folder carries a name and a parent and no content, so "
                    + "two curators renaming it lose each other nothing; the write that can lose "
                    + "content under a folder is a move, which is ShObjectAPI's"),
            Map.entry("ShFolderAPI#delete", WHOLE_SUBTREE),
            Map.entry("ShFolderAPI#untrash", TRASH_IS_THE_STATE),
            Map.entry("ShFolderAPI#purge", TRASH_IS_THE_STATE),

            Map.entry("ShObjectAPI#copyTo", CREATES),
            Map.entry("ShObjectAPI#moveTo", WHOLE_SUBTREE),
            Map.entry("ShObjectAPI#putAcl", "it writes who may read, not what is there, and the two "
                    + "are different documents — a curator editing a post and an administrator "
                    + "granting access are not overwriting each other"),
            // SH1302 — putAcl's reason, for the visitor-side twin of the same setting.
            Map.entry("ShObjectAPI#putVisibility", "it writes who may read the published page, not "
                    + "what the page says, so a version of the content would guard the wrong document"),

            Map.entry("ShStaticFileAPI#upload", CREATES),
            Map.entry("ShStaticFileAPI#replace", "the bytes carry their own sha256 and a replace is "
                    + "the deliberate overwrite of them; the guard worth having here is over the "
                    + "bytes a caller last read, which is a different digest from a post's"),
            Map.entry("ShStaticFileAPI#updateMeta", NOT_THE_CONTENT),
            // SH1035 — a revert takes a rendition NUMBER, which is a stronger guard than a version
            // would be: it names the exact bytes being restored, so two curators reverting at once
            // cannot each get the other's idea of "the previous one". It is also the undo of a
            // replace, and the replace's own entry above says why the digest that matters here is
            // the bytes' rather than the post's.
            Map.entry("ShStaticFileAPI#revert", "the rendition number IS the precondition: it "
                    + "addresses one set of bytes, so a concurrent replace cannot silently change "
                    + "what this call restores")));

    /** Every mutating mapping in the content writers, and whether its signature takes a version. */
    private static List<Write> writes() {
        Map<String, String> sources = ShControllerScan.sources();
        List<Write> found = new ArrayList<>();
        for (String file : CONTENT_WRITERS) {
            String body = sources.get(file);
            if (body == null) {
                throw new IllegalStateException(file + " is gone — move this lint with it");
            }
            // The parameter list is read by counting parentheses, which is this lint's own lesson and
            // now ShControllerScan's: SH990's census over GET needed the same reader.
            for (ShControllerScan.Mapping mapping
                    : ShControllerScan.mappings(body, "Post|Put|Patch|Delete")) {
                found.add(new Write(file.replace(".java", ""), mapping.method(),
                        GUARD.matcher(mapping.parameters()).find()));
            }
        }
        return found;
    }

    @Test
    @DisplayName("SH985: every content write says whether it can be made conditional")
    void everyContentWriteIsClassified() {
        List<Write> writes = writes();
        assertThat(writes)
                .as("SH985: the scan found too few mutating mappings, which is how this lint goes "
                        + "green without looking — see the parenthesis-counting note above")
                .hasSizeGreaterThanOrEqualTo(20);

        Map<String, Boolean> byName = new TreeMap<>();
        writes.forEach(write -> byName.put(write.file() + "#" + write.method(), write.guarded()));

        assertThat(byName.keySet())
                .as("""
                        SH985: a content write is either conditional or a place where a curator can \
                        silently overwrite an agent. Classify it here — guarded with an empty \
                        reason, or unguarded with the reason a version would mean nothing — since \
                        the list is what makes the gap countable rather than anecdotal.""")
                .containsExactlyInAnyOrderElementsOf(DECLARED.keySet());
    }

    @Test
    @DisplayName("SH985: a write that gains or loses a guard fails until its row moves")
    void theListAgreesWithTheTree() {
        List<String> problems = new ArrayList<>();
        for (Write write : writes()) {
            String name = write.file() + "#" + write.method();
            String reason = DECLARED.get(name);
            if (reason == null) {
                continue; // the test above owns an unclassified write
            }
            if (reason.isEmpty() && !write.guarded()) {
                problems.add(name + ": declared guarded and its signature takes no If-Match");
            }
            if (!reason.isEmpty() && write.guarded()) {
                problems.add(name + ": declared unguarded and it now takes one — good news, and the "
                        + "row moves with the reason deleted rather than left standing");
            }
        }

        assertThat(problems)
                .as("""
                        SH985: the reason column is the deliverable. A row saying only "unguarded" \
                        is a gap recorded as a fact, and that is what let the console keep four \
                        unconditional writes for the whole life of the agent's own guard.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH985: the counts print, so the gap is read rather than recomputed")
    void theCensusIsTheDeliverable() {
        List<Write> writes = writes();
        Map<String, List<String>> byFile = new LinkedHashMap<>();
        writes.forEach(write -> byFile
                .computeIfAbsent(write.file(), ignored -> new ArrayList<>())
                .add((write.guarded() ? "guarded   " : "unguarded ") + write.method()));

        int guarded = (int) writes.stream().filter(Write::guarded).count();
        System.out.printf("%n[SH985] content writes: %d of %d take a version guard%n",
                guarded, writes.size());
        byFile.forEach((file, rows) -> {
            System.out.println("  " + file);
            rows.stream().sorted().forEach(row -> System.out.println("    " + row));
        });
        System.out.println();
        ShP1Inventory.record("ShVersionGuardLintTest", new TreeMap<>(
                Map.of("guarded", guarded, "unguarded", writes.size() - guarded)));

        assertThat(guarded)
                .as("SH985: no content write is conditional, so the contract this lint exists to "
                        + "hold has been removed rather than the lint")
                .isPositive();
    }
}
