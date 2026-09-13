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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * SH954 — an endpoint that answers 200 and does nothing is a lie with a status code.
 *
 * <h2>The one this was written for</h2>
 *
 * <p>{@code ShObjectAPI.clearCache} was a {@code @GetMapping("/{id}/clear-cache")} whose entire
 * body was {@code return ResponseEntity.ok().build();}. Its own javadoc said so: <em>"the
 * page-cache subsystem the legacy endpoint drove is not part of the current backend, so this is a
 * no-op kept for the console's clear cache action (it expects a 200)"</em>. The console rendered a
 * <b>Clear Cache</b> button on every row of the content browser, called it, and toasted "Cache
 * cleared". So the product told a curator it had done something, every time, for as long as the
 * cache had been gone — and the comment explaining that was in the file the whole time, which is
 * exactly why prose is not a guard.</p>
 *
 * <h2>Why a lint and not a deletion</h2>
 *
 * <p>Deleting the endpoint fixes today and restores the precise condition that produced it: a
 * subsystem goes, its endpoint is kept "because the console expects a 200", and the button outlives
 * both. This is the reader that finds the next one. Held as an asserted list rather than a count,
 * for the reason {@code agents.md} gives about ceilings: a list has to be argued entry by entry in
 * a diff somebody reads.</p>
 *
 * <h3>What it deliberately does not claim</h3>
 *
 * <p>It cannot tell a <em>no-op</em> from a <em>probe</em>. A liveness endpoint that returns a bare
 * 200 is correct and this would report it, which is what {@link #EXPECTED} is for — an entry there
 * says "answering nothing IS the answer", and that sentence is either true of the endpoint or it is
 * not. The rule is narrow on purpose: the method's body must be exactly one bare `ok`, so anything
 * that reads, writes, validates or even logs is out of scope and cannot be argued into it.</p>
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShNoOpMappingLintTest {

    private static final Path MAIN = Path.of("src", "main", "java");

    /** A Spring request mapping, of any verb. */
    private static final Pattern MAPPING = Pattern.compile(
            "@(?:Get|Post|Put|Delete|Patch|Request)Mapping\\b");

    /**
     * A body that is one bare success and nothing else.
     *
     * <p>{@code ResponseEntity.ok().build()}, {@code ResponseEntity.noContent().build()} and a bare
     * {@code return;} — the three ways to answer "fine" without having done anything. A body that
     * returns {@code ok(something)} is out: it computed the something.</p>
     */
    private static final Pattern BARE_SUCCESS = Pattern.compile(
            "^\\s*return\\s+ResponseEntity\\.(?:ok|noContent|accepted)\\(\\)\\.build\\(\\)\\s*;\\s*$"
                    + "|^\\s*return\\s*;\\s*$");

    /**
     * Mappings that answer nothing on purpose, with the sentence that makes it true.
     *
     * <p>One, and it is the distinction this list exists to draw. {@code clear-cache} was a removed
     * subsystem's endpoint kept alive so a button would keep working; {@code ShMcpAPI.end} is a
     * protocol obligation that is <em>satisfied</em> by doing nothing. The first is a lie and the
     * second is an answer.</p>
     */
    private static final Map<String, String> EXPECTED = Map.of(
            "ShMcpAPI.java :: @Operation(summary = \"End an MCP session\")",
            "MCP requires DELETE to end a session, and there is nothing here to free: a session id "
                    + "is an attribution label rather than server-side state, so 204 is the true "
                    + "answer and not a placeholder for one.");

    @Test
    void sh954NoMappingAnswersSuccessWithoutDoingAnything() throws IOException {
        List<String> noOps = new ArrayList<>();
        int mappings = 0;

        assertThat(Files.isDirectory(MAIN))
                .as("SH954: expected to run from the shio-app module").isTrue();

        try (Stream<Path> tree = Files.walk(MAIN)) {
            for (Path java : tree.filter(p -> p.toString().endsWith(".java")).toList()) {
                // Comments stripped: the javadoc that explained this endpoint was a no-op must not
                // read as the code doing something, and a `// return ResponseEntity.ok()` in a
                // comment must not read as a body (SH206).
                String source = ShSourceText.code(Files.readString(java));
                Matcher matcher = MAPPING.matcher(source);
                while (matcher.find()) {
                    mappings += 1;
                    String body = bodyAfter(source, matcher.end());
                    if (body != null && BARE_SUCCESS.matcher(body).matches()) {
                        noOps.add(MAIN.relativize(java).toString().replace('\\', '/')
                                + " :: " + signatureAfter(source, matcher.end()));
                    }
                }
            }
        }

        // 180-odd when this shipped. A walk that stops finding mappings reports a clean tree, which
        // is the failure mode of every lint of this shape (SH322).
        assertThat(mappings)
                .as("SH954: found %d request mappings, so this asserted nothing", mappings)
                .isGreaterThan(100);

        List<String> unexpected = noOps.stream()
                .filter(found -> EXPECTED.keySet().stream().noneMatch(found::contains))
                .toList();

        assertThat(unexpected)
                .as("SH954: a request mapping answers success and does nothing at all. That is a "
                        + "lie with a status code: a caller cannot tell it from a real one, and a "
                        + "console button on top of it reports success for ever.\n"
                        + "  This is how `clear-cache` survived the removal of the cache it "
                        + "cleared — the endpoint was kept 'because the console expects a 200'.\n"
                        + "  Delete it with its callers, or — if answering nothing genuinely IS "
                        + "the answer, as it is for a liveness probe — add it to EXPECTED with "
                        + "that sentence.")
                .isEmpty();
    }

    /** The method body that follows a mapping annotation, or {@code null} if none is found. */
    private static String bodyAfter(String source, int from) {
        int open = source.indexOf('{', from);
        if (open < 0) {
            return null;
        }
        // The annotation's own parentheses may contain braces (a `produces = {...}`), so the body
        // is the first brace after the signature's closing parenthesis rather than after the
        // annotation.
        int signatureEnd = source.indexOf(')', from);
        if (signatureEnd < 0) {
            return null;
        }
        open = source.indexOf('{', signatureEnd);
        if (open < 0) {
            return null;
        }
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open + 1, i);
                }
            }
        }
        return null;
    }

    /** Enough of the declaration to name the offender in the failure. */
    private static String signatureAfter(String source, int from) {
        int open = source.indexOf('{', source.indexOf(')', from));
        if (open < 0) {
            return "?";
        }
        String before = source.substring(from, open).replaceAll("\\s+", " ").trim();
        return before.length() > 120 ? before.substring(0, 120) + "…" : before;
    }
}
