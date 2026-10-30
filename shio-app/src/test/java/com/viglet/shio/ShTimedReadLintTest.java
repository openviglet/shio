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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH1327 — every test that reads a clock first makes H2 forget the results it would recall.
 *
 * <p>A timed repeat of an identical read on H2 times its result cache, so a 3.5 s query printed as
 * 17 ms and SH1217's measurement hid the defect SH1305 fixed. {@link ShH2ResultCache} makes the
 * number the read's; this names every test class calling {@code System.nanoTime()} that neither
 * uses it nor is listed below with the reason it times no database read.</p>
 */
// CODE, not prose: a javadoc that names ShH2ResultCache while explaining why it is not needed is not
// a class going through it.
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShTimedReadLintTest {

    private static final Path TESTS = Path.of("src", "test", "java");

    /** Classes that read a clock around something that is not a database read, each with its reason. */
    private static final Map<String, String> EXEMPT = Map.of(
            "ShTuringTextExtractionTest.java",
            "times an extraction timeout against a stub, with no database in the call",
            "ShTemplateGraphMemoBenchmarkTest.java",
            "times the in-memory memo, whose repeats are asserted to run zero queries");

    @Test
    @DisplayName("SH1327: a test that times something goes through ShH2ResultCache or says why not")
    void everyClockGoesThroughTheWindow() throws IOException {
        List<Path> timing = timing();
        List<String> unguarded = timing.stream()
                .filter(file -> !guarded(read(file)))
                .map(file -> file.getFileName().toString())
                .filter(name -> !EXEMPT.containsKey(name))
                .sorted()
                .toList();
        List<String> staleExemptions = EXEMPT.keySet().stream()
                .filter(name -> timing.stream().noneMatch(file -> file.getFileName().toString().equals(name)))
                .sorted()
                .toList();
        System.out.printf("SH1327: %d test class(es) read System.nanoTime(), %d exempt, %d unguarded%n",
                timing.size(), EXEMPT.size(), unguarded.size());

        assertThat(timing)
                .as("SH1327: no test was found reading System.nanoTime(), so this lint reads nothing; "
                        + "check TESTS still points at the test sources")
                .isNotEmpty();
        assertThat(unguarded)
                .as("""
                        SH1327: these time something without ShH2ResultCache, and a repeated read on \
                        H2 is answered from its result cache until the database changes. Time the \
                        call with `ShH2ResultCache.millis(dataSource, ...)`, or call \
                        `ShH2ResultCache.forget(dataSource)` right before starting the clock; if no \
                        database read is timed, add the class to EXEMPT with the reason.""")
                .isEmpty();
        assertThat(staleExemptions)
                .as("SH1327: these are exempt but no longer read a clock, so the exemption excuses nothing")
                .isEmpty();
    }

    private static List<Path> timing() throws IOException {
        try (Stream<Path> files = Files.walk(TESTS)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !List.of("ShH2ResultCache.java",
                            ShTimedReadLintTest.class.getSimpleName() + ".java")
                            .contains(file.getFileName().toString()))
                    .filter(file -> read(file).contains("System.nanoTime("))
                    .toList();
        }
    }

    /** A call, not an import: a class that only imports the helper times nothing through it. */
    private static boolean guarded(String code) {
        return code.contains("ShH2ResultCache.forget(") || code.contains("ShH2ResultCache.millis(");
    }

    private static String read(Path file) {
        try {
            return ShSourceText.code(Files.readString(file));
        } catch (IOException unreadable) {
            throw new IllegalStateException("SH1327: could not read " + file, unreadable);
        }
    }
}
