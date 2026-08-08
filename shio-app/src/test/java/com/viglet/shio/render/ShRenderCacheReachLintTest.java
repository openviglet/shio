/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH313 — which render paths get SH311's cache, asserted instead of assumed.
 *
 * <h2>The gap</h2>
 *
 * <p>SH311 keys its resolved templates on {@code TransactionSynchronizationManager} and takes the
 * cache <b>only</b> when the current transaction is read-only. That is right, and it has a
 * consequence nobody had looked at: a caller that already holds a <em>writing</em> transaction joins
 * it under the default {@code REQUIRED} propagation, {@code isCurrentTransactionReadOnly()} answers
 * false, and the render silently costs 19 queries a page instead of 6.</p>
 *
 * <p>The half that says the cache <em>works</em> already exists — {@code ShRenderSweepBenchmarkTest}
 * asserts the marginal per-page cost, and that number is the cache. What was missing is the half
 * that says <b>which paths have it</b>, and today's answer is "both of them" only because there are
 * two callers and neither writes. The expensive case is a <em>future</em> one, which is exactly when
 * a silent behaviour difference costs the most.</p>
 *
 * <h2>Why a lint and not the log line the task proposed</h2>
 *
 * <p>The alternative was a metric or a log line when a render happens with no cache. SH103's own
 * argument disqualifies it: nobody reads logs, which is the reason the incident log exists at all.
 * A build failure names the new path to the person adding it, at the moment they add it.</p>
 *
 * <h2>Two things SH206 taught it</h2>
 *
 * <p><b>It read prose.</b> The scan ran over the whole file, so a javadoc sentence containing
 * {@code @Transactional} counted as a declaration — and one explaining why a class no longer has
 * the annotation failed the build for having mentioned it. A lint whose finding can be produced by
 * writing about the rule is one people learn to route around by not writing about it.</p>
 *
 * <p><b>And it could not see a transaction that is not an annotation.</b> SH206 moved
 * {@code ShAgentVerifyService} to a {@link org.springframework.transaction.support.TransactionTemplate}
 * because an annotation cannot end a transaction in the middle of a method. With the annotation
 * gone the file had nothing left to match, so the class became <em>compliant by being invisible</em>
 * — which is the same answer this lint would give a class that genuinely rendered inside a write.
 * A programmatic boundary is now checked on its own terms.</p>
 *
 * @author Alexandre Oliveira
 */
class ShRenderCacheReachLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /** A call into the page renderer — the thing whose cost this is about. */
    private static final Pattern RENDERS = Pattern.compile("\\.render\\(");

    /** Any {@code @Transactional}, with its arguments; an empty group means the default. */
    private static final Pattern TRANSACTIONAL =
            Pattern.compile("@Transactional(?:\\s*\\(([^)]*)\\))?");

    /** SH206 — a transaction boundary drawn in code rather than declared. */
    private static final Pattern PROGRAMMATIC =
            Pattern.compile("new\\s+TransactionTemplate\\s*\\(");

    /** What makes a programmatic one read-only, on either the template or its definition. */
    private static final Pattern SETS_READ_ONLY = Pattern.compile("setReadOnly\\s*\\(\\s*true\\s*\\)");

    @Test
    @DisplayName("SH313: nothing renders from inside a writing transaction")
    void nothingRendersInsideAWritingTransaction() {
        Map<String, String> callers = renderCallers();

        System.out.printf("%n[SH313] %d render call sites: %s%n%n",
                callers.size(), new TreeMap<>(callers).keySet());

        List<String> writing = new ArrayList<>();
        callers.forEach((name, source) -> {
            // SH206 — code only. This used to scan the raw file, so a javadoc sentence mentioning
            // the annotation was indistinguishable from declaring it.
            String body = code(source);
            Matcher matcher = TRANSACTIONAL.matcher(body);
            while (matcher.find()) {
                String arguments = matcher.group(1) == null ? "" : matcher.group(1);
                if (!arguments.contains("readOnly")) {
                    writing.add(name + " -> @Transactional(" + arguments + ")");
                }
            }
            // SH206 — and a boundary built rather than declared has to say the same thing. Without
            // this, moving off the annotation silently removes a file from this lint's reach.
            if (PROGRAMMATIC.matcher(body).find() && !SETS_READ_ONLY.matcher(body).find()) {
                writing.add(name + " -> new TransactionTemplate(...) with no setReadOnly(true)");
            }
        });

        assertThat(writing)
                .as("""
                        SH311's cache is taken only when the current transaction is read-only, and \
                        REQUIRED propagation means a caller that already holds a writing one keeps \
                        it. Rendering from there costs 19 queries a page instead of 6, silently — \
                        there is no error, no log line and no failing test, which is why this is a \
                        lint. Either render outside the write, or say in the commit why this path \
                        pays that and add it here.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan reads code, not prose, and sees a programmatic boundary")
    void theScanReadsCodeAndSeesBothKinds() {
        // Guards the guard (SH275's habit), and both halves are cases SH206 actually hit.
        String prose = """
                /**
                 * This class used to carry @Transactional and no longer does.
                 */
                // and a line comment mentioning @Transactional(readOnly = false) too
                public class Example {
                }
                """;
        assertThat(TRANSACTIONAL.matcher(code(prose)).find())
                .as("a sentence about the rule must not be readable as a breach of it")
                .isFalse();

        String declared = """
                public class Example {
                    @Transactional
                    public void write() {
                    }
                }
                """;
        assertThat(TRANSACTIONAL.matcher(code(declared)).find())
                .as("and the real declaration still matches, or stripping comments broke the lint")
                .isTrue();

        String programmatic = "this.readPass = new TransactionTemplate(manager, definition);";
        assertThat(PROGRAMMATIC.matcher(programmatic).find()).isTrue();
        assertThat(SETS_READ_ONLY.matcher(programmatic).find())
                .as("a template with no read-only setting is the writing case, spelled in code")
                .isFalse();
        assertThat(SETS_READ_ONLY.matcher("definition.setReadOnly(true);").find()).isTrue();
    }

    /**
     * The file with its comments removed (SH206).
     *
     * <p>String and text-block aware, because this repository is full of both and a URL's
     * {@code //} inside a literal is not a comment. Deliberately a small state machine rather than
     * a regex: comment stripping is the one job a regex cannot do on a language with nested quoting
     * rules, and getting it wrong here silently blinds the lint rather than failing it.</p>
     */
    private static String code(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int stop = end < 0 ? source.length() : end + 3;
                out.append(source, i, stop);
                i = stop;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < source.length() && source.charAt(j) != c) {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                int stop = Math.min(j + 1, source.length());
                out.append(source, i, stop);
                i = stop;
            } else if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? source.length() : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    @Test
    @DisplayName("the guard is not vacuous: it found the call sites it is about")
    void theGuardFoundTheCallers() {
        assertThat(renderCallers().keySet())
                .as("""
                        a scan that matched nothing would pass forever, which is the failure every \
                        lint in this repository is written against""")
                .contains("ShRenderAPI.java", "ShAgentRenderService.java");
    }

    /**
     * Files that call into the renderer.
     *
     * <p>Keyed on holding a {@code ShPageRenderService} reference <em>and</em> a {@code .render(}
     * call: the second alone matches every template engine, every DTO mapper and this file's own
     * pattern string.</p>
     */
    private static Map<String, String> renderCallers() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String body = read(path);
                        String name = path.getFileName().toString();
                        if (!name.equals("ShPageRenderService.java")
                                && body.contains("ShPageRenderService")
                                && RENDERS.matcher(body).find()) {
                            byName.put(name, body);
                        }
                    });
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
