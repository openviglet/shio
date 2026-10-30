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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SH1345 — the P1 inventory stays one valid document when several JVMs write it at once.
 *
 * <p>Surefire runs the suite in forks, and three P1 lints merge their counts into one file. With only
 * {@code synchronized} guarding it, one fork read another's half-written file and the three failed
 * together on a torn document. This runs the writers as separate processes, because a race between
 * threads of one JVM is the case {@code synchronized} already covered.</p>
 */
class ShP1InventoryTest {

    private static final int WRITERS = 3;
    private static final int ROUNDS = 40;

    /** One writer process: records {@code rounds} instruments named after it, reading back each time. */
    public static void main(String[] args) {
        Path file = Path.of(args[0]);
        String writer = args[1];
        int rounds = Integer.parseInt(args[2]);
        for (int round = 0; round < rounds; round++) {
            ShP1Inventory.record(file, writer + "-" + round, Map.of("round", round));
            ShP1Inventory.read(file);
        }
    }

    @Test
    @DisplayName("SH1345: writer processes racing on one inventory leave valid JSON with every key")
    void forksDoNotTearTheFile(@TempDir Path dir) throws IOException, InterruptedException {
        Path file = dir.resolve("p1-inventory.json");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<Process> writers = new ArrayList<>();
        for (int writer = 0; writer < WRITERS; writer++) {
            writers.add(new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                    ShP1InventoryTest.class.getName(), file.toString(), "w" + writer, String.valueOf(ROUNDS))
                    .redirectErrorStream(true).start());
        }
        for (Process writer : writers) {
            assertThat(writer.waitFor(120, TimeUnit.SECONDS)).as("SH1345: a writer did not finish").isTrue();
            String output = new String(writer.getInputStream().readAllBytes());
            assertThat(writer.exitValue())
                    .as("SH1345: a writer process failed, which is how a torn read shows up:%n%s", output)
                    .isZero();
        }

        Map<String, Object> all = ShP1Inventory.read(file);
        assertThat(all)
                .as("SH1345: every key every writer recorded survives, so no write was based on a stale read")
                .hasSize(WRITERS * ROUNDS);
    }
}
