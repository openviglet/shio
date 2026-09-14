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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.object.ShMoveUrlConflictException;
import com.viglet.shio.api.post.unified.ShRestoreTitleConflictException;
import com.viglet.shio.api.post.unified.ShRestoreUrlConflictException;
import com.viglet.shio.url.ShSiteFurlConflictException;

/**
 * SH955 — every refusal carries what to do about it, and the ones about a field say which.
 *
 * <h2>The half that was already true</h2>
 *
 * <p>All 141 {@code ShProblem} constructions in this module already chain {@code withFix}, which is
 * the property P6 is about and is exactly the kind of thing that stays true until it quietly does
 * not. Nothing asserted it: the rule lived in {@code ShProblem}'s javadoc and in review habit, so
 * the 142nd refusal was free to ship without one. Measured before writing this, and it is green by
 * construction — which is the right state for a guard to be born in.</p>
 *
 * <h2>The half that was not</h2>
 *
 * <p>{@code at} — which field a refusal is about — was set in exactly two places, and one of them
 * is the batch, which sets {@code ops[3]}. A console form cannot match an op index against
 * anything, so a conflict over a friendly URL floated above a page with sixteen inputs instead of
 * landing on the one it was about. SH955's console half reads {@code at} through {@code fieldOf};
 * this is the side that gives it something to read.</p>
 *
 * <p>The four below are the refusals that are unambiguously about one field a curator typed. This
 * asserts them by construction rather than by scanning, because "is this problem about a field" is
 * a judgement no regex makes — and a lint that guessed would either miss the ones that matter or
 * demand an {@code at} on refusals that are about nothing in particular.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShTeachingProblemLintTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    @Test
    void sh955EveryRefusalSaysWhatToDoAboutIt() throws IOException {
        List<String> without = new ArrayList<>();
        int problems = 0;

        assertThat(Files.isDirectory(MAIN)).as("SH955: expected the shio-app module").isTrue();

        try (Stream<Path> tree = Files.walk(MAIN)) {
            for (Path java : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                // Comments stripped, or a javadoc explaining why some problem has no fix would read
                // as the call chaining one (SH206).
                List<String> lines = List.of(ShSourceText.code(Files.readString(java)).split("\n"));
                for (int i = 0; i < lines.size(); i++) {
                    if (!lines.get(i).contains("ShProblem.of(")
                            && !lines.get(i).contains("ShProblem.badRequest(")
                            && !lines.get(i).contains("ShProblem.conflict(")) {
                        continue;
                    }
                    problems += 1;
                    // The chain is written across lines; fourteen covers the longest of them and
                    // stops well before the next construction in any file here.
                    String window = String.join("\n",
                            lines.subList(i, Math.min(lines.size(), i + 14)));
                    if (!window.contains(".withFix(")) {
                        without.add(MAIN.relativize(java).toString().replace('\\', '/') + ":" + (i + 1));
                    }
                }
            }
        }

        assertThat(problems)
                .as("SH955: found %d problem constructions, too few to have looked anywhere", problems)
                .isGreaterThan(100);

        assertThat(without)
                .as("SH955: a refusal is built with no `fix`. P6 is the law that an error is an "
                        + "instruction, and the fix is the whole of the instruction — a 4xx "
                        + "carrying only a title tells a caller that something is wrong and "
                        + "nothing about what to do, which is the state the console was in until "
                        + "this task.\n"
                        + "  All %d others chain one. Add `.withFix(...)` saying what would work.")
                .isEmpty();
    }

    @Test
    void sh955ARefusalAboutOneFieldSaysWhichField() {
        // By construction, not by scanning: "is this problem about a field" is a judgement no
        // regex makes, and one that guessed would demand an `at` on refusals about nothing in
        // particular. These four are the ones a curator meets while typing into a form.
        assertThat(new ShRestoreUrlConflictException("pricing", "Pricing").problem()
                .toProblemDetail().getProperties())
                .extractingByKey("at").isEqualTo("data.furl");

        assertThat(new ShRestoreTitleConflictException("Pricing", "Pricing").problem()
                .toProblemDetail().getProperties())
                .extractingByKey("at").isEqualTo("data.title");

        assertThat(new ShMoveUrlConflictException("pricing", "Pricing").problem()
                .toProblemDetail().getProperties())
                .extractingByKey("at").isEqualTo("data.furl");

        // A site's own `furl` is not a post's `data.furl`. A console page that has both would pin
        // this on the wrong input if the pointer lied about which object it belongs to.
        assertThat(new ShSiteFurlConflictException("acme", "Acme").problem()
                .toProblemDetail().getProperties())
                .extractingByKey("at").isEqualTo("furl");
    }

    @Test
    void sh955TheFieldPointerTravelsWithTheRestOfTheDocument() {
        // The control on the assertions above: an `at` that arrived without a `fix` beside it would
        // pin an unactionable message on a field, which is worse than floating an actionable one.
        var properties = new ShRestoreUrlConflictException("pricing", "Pricing")
                .problem().toProblemDetail().getProperties();

        assertThat(properties).containsKeys("at", "fix");
        assertThat(String.valueOf(properties.get("fix")))
                .as("SH955: the fix is what the curator acts on once the field is marked")
                .contains("/pricing");
    }
}
