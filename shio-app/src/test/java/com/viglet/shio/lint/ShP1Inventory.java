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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public static synchronized void record(String instrument, Map<String, Object> counts) {
        Map<String, Object> all = new TreeMap<>(read());
        all.put(instrument, new TreeMap<>(counts));
        try {
            Files.createDirectories(FILE.getParent());
            // Written as UTF-8 with LF endings, like every other generated artefact here (SH863).
            Files.writeString(FILE, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(all)
                    .replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + FILE.toAbsolutePath(), e);
        }
    }

    /** What the run has recorded so far, or empty. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> read() {
        if (!Files.exists(FILE)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(Files.readString(FILE, StandardCharsets.UTF_8), Map.class);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + FILE.toAbsolutePath(), e);
        }
    }
}
