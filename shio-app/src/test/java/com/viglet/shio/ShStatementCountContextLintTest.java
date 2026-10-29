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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH1284 — every test that counts prepared statements does so with the post scheduler out of its
 * window.
 *
 * <h2>Why a counter is not enough on its own</h2>
 *
 * <p>Hibernate's {@code Statistics} belongs to the {@code SessionFactory}, so a test reading
 * {@code getPrepareStatementCount()} before and after a call also counts whatever another thread
 * prepared in between. {@code ShPostScheduler} sweeps every minute in any context that leaves
 * {@code shio.schedule.enabled} on, and measured with a 5 ms sweep it added three to six statements to
 * the site listing's reads: SH1276 failed one run in four that way. A class is safe when its context
 * sets {@code shio.schedule.enabled=false}, or when it pauses {@code ShScheduleProperties} around the
 * measurement, as SH1276 and SH1284 do.</p>
 *
 * <p>The scheduler is the one background source today: the event stream polls only with a
 * subscriber, and the meter flushes only when metering is on. <b>A new scheduled job joins this
 * rule</b>, and its pause belongs beside the sweep's in every class this names.</p>
 *
 * @author Alexandre Oliveira
 */
// CODE, not prose: a class that only names ShScheduleProperties in a comment explaining why it does
// not need it is not guarded, and the property's string literal survives the stripper.
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShStatementCountContextLintTest {

    private static final Path TESTS = Path.of("src", "test", "java");

    /** Classes allowed to count without either guard, each with its reason. None today. */
    private static final List<String> EXEMPT = List.of();

    @Test
    @DisplayName("SH1284: a statement-count test switches the scheduler off or pauses it")
    void everyCounterIsGuarded() throws IOException {
        List<Path> counting = counting();
        List<String> unguarded = counting.stream()
                .filter(file -> !guarded(read(file)))
                .map(file -> file.getFileName().toString())
                .filter(name -> !EXEMPT.contains(name))
                .sorted()
                .toList();
        System.out.printf("SH1284: %d test class(es) count prepared statements, %d unguarded%n",
                counting.size(), unguarded.size());

        assertThat(counting)
                .as("SH1284: no test was found reading getPrepareStatementCount, so this lint reads "
                        + "nothing; check TESTS still points at the test sources")
                .isNotEmpty();
        assertThat(unguarded)
                .as("SH1284: these count statements on a global counter the post scheduler also "
                        + "writes. Set `shio.schedule.enabled=false` in the @SpringBootTest properties, "
                        + "or pause ShScheduleProperties around the measurement as "
                        + "ShSiteListingCostTest does. A new scheduled job belongs to the same rule.")
                .isEmpty();
    }

    private static List<Path> counting() throws IOException {
        try (Stream<Path> files = Files.walk(TESTS)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.getFileName().toString()
                            .equals(ShStatementCountContextLintTest.class.getSimpleName() + ".java"))
                    .filter(file -> read(file).contains("getPrepareStatementCount("))
                    .toList();
        }
    }

    private static boolean guarded(String source) {
        return source.contains("shio.schedule.enabled=false") || source.contains("ShScheduleProperties");
    }

    private static String read(Path file) {
        try {
            return ShSourceText.code(Files.readString(file));
        } catch (IOException unreadable) {
            throw new IllegalStateException("SH1284: could not read " + file, unreadable);
        }
    }
}
