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
 * @author Alexandre Oliveira
 */
class ShRenderCacheReachLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /** A call into the page renderer — the thing whose cost this is about. */
    private static final Pattern RENDERS = Pattern.compile("\\.render\\(");

    /** Any {@code @Transactional}, with its arguments; an empty group means the default. */
    private static final Pattern TRANSACTIONAL =
            Pattern.compile("@Transactional(?:\\s*\\(([^)]*)\\))?");

    @Test
    @DisplayName("SH313: nothing renders from inside a writing transaction")
    void nothingRendersInsideAWritingTransaction() {
        Map<String, String> callers = renderCallers();

        System.out.printf("%n[SH313] %d render call sites: %s%n%n",
                callers.size(), new TreeMap<>(callers).keySet());

        List<String> writing = new ArrayList<>();
        callers.forEach((name, body) -> {
            Matcher matcher = TRANSACTIONAL.matcher(body);
            while (matcher.find()) {
                String arguments = matcher.group(1) == null ? "" : matcher.group(1);
                if (!arguments.contains("readOnly")) {
                    writing.add(name + " -> @Transactional(" + arguments + ")");
                }
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
