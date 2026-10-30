/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where a P1 instrument writes the inventory it prints (SH974).
 *
 * <h2>Why a file and not only a printed block</h2>
 *
 * <p>Printing puts the figure in a log nobody keeps. {@code agents.md} then quoted two of them —
 * "of 25 mutating paths, which 8" — and both had moved, in the same paragraph that explains why the
 * other gate's counts are <em>not</em> written down. A number in prose is a claim, and a claim with
 * no artefact behind it is checked by whoever happens to re-derive it.</p>
 *
 * <p>So each instrument writes its counts here as well, and {@code cli/test/agents-md-figures.test.mjs}
 * refuses a figure in the index or an area doc unless it is cited from this file and matches. The
 * file is in {@code target/}, so it is rebuilt by the run that proves it and never committed.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShP1Inventory {

    /** Beside the surefire reports, for the same reason: it is an artefact of the run. */
    public static final Path FILE = Path.of("target", "p1-inventory.json");

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private ShP1Inventory() {
        // utility
    }

    /**
     * Records one instrument's counts, merging with whatever the run has already written.
     *
     * <p>Merged rather than replaced because the three instruments are three test classes, and a
     * targeted run of one of them must not delete the other two — a partial file is honest (the
     * reader checks the keys it finds), an overwritten one is not.</p>
     *
     * @param instrument the test class's simple name, which is what prose names
     * @param counts     what the run measured, keyed as the prose would say it
     */
    public static void record(String instrument, Map<String, Object> counts) {
        record(FILE, instrument, counts);
    }

    /** What the run has recorded so far, or empty. */
    public static Map<String, Object> read() {
        return read(FILE);
    }

    /**
     * SH1345 — the same, against {@code file}, safe across JVMs.
     *
     * <p>{@code synchronized} orders the threads of one JVM, and surefire runs the suite in several
     * forks: one fork read the file while another was halfway through writing it, and three P1 lints
     * failed on {@code Unexpected close marker '}'} in a run that passed unchanged on the next. So the
     * read-merge-write holds a lock on a sibling file, which the operating system shares between
     * processes, and the new document is written beside the target and moved over it whole.</p>
     */
    static synchronized void record(Path file, String instrument, Map<String, Object> counts) {
        locked(file, () -> {
            Map<String, Object> all = new TreeMap<>(parse(file));
            all.put(instrument, new TreeMap<>(counts));
            Path next = file.resolveSibling(file.getFileName() + ".next");
            // Written as UTF-8 with LF endings, like every other generated artefact here (SH863).
            Files.writeString(next, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(all)
                    .replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);
            Files.move(next, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return null;
        });
    }

    static synchronized Map<String, Object> read(Path file) {
        return locked(file, () -> parse(file));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(Path file) throws IOException {
        return Files.exists(file)
                ? MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), Map.class)
                : Map.of();
    }

    private interface Locked<T> {
        T run() throws IOException;
    }

    /** Runs {@code work} holding an exclusive lock every JVM in the run respects. */
    private static <T> T locked(Path file, Locked<T> work) {
        Path lock = file.resolveSibling(file.getFileName() + ".lock");
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock held = channel.lock()) {
                return work.run();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read or write " + file.toAbsolutePath(), e);
        }
    }
}
