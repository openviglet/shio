/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.reference;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShSourceScan;
import com.viglet.shio.lint.ShSourceText;

/**
 * SH1034 — a derived index is only as good as the writer nobody remembered.
 *
 * <h2>Why this lands with the index and not after it</h2>
 *
 * <p>agents.md's rule, in the case it was written for: <em>when a task says "and whatever a grep
 * turns up", the grep is the deliverable — it lands as a lint before the fixes, because that is
 * what finds the call site nobody read.</em> {@code sh_object_reference} is derived state, and
 * derived state has exactly one failure mode: a write that does not maintain it. That failure is
 * <b>silent</b> — the index simply answers with yesterday's edges, so "where is this used" is
 * confidently wrong and {@code asset-unused} offers a referenced asset for deletion.</p>
 *
 * <p>The shape of the risk is measured rather than feared. {@code ShPostUnifiedWriteService} calls
 * {@code save} from fifteen places and is not the only writer: five other classes set a post's
 * {@code data} and persist it themselves, having grown up beside the service rather than through
 * it. So "remember to call reindex" was never going to hold, and a choke point does not exist to
 * be used.</p>
 *
 * <h2>What it checks</h2>
 *
 * <p>A main source that writes {@code setData(...)} <b>and</b> persists a post must name
 * {@code ShObjectReferenceService}. It does not check that the call is correct — no source lint
 * can — it checks that the author of the next writer was made to think about it, which is the
 * failure that actually happens.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@ShSourceScan(ShSourceScan.Reads.CODE)
class ShReferenceIndexMaintenanceLintTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /** The maintainer a writer has to name. */
    private static final String MAINTAINER = "ShObjectReferenceService";

    /**
     * Classes that set a post's {@code data} and never persist it, with the reason.
     *
     * <p>Declared rather than pattern-matched, and SH142's rule applies: an entry that stops being
     * needed fails the build, so a class that starts saving cannot keep its exemption by being
     * forgotten.</p>
     */
    private static final Map<String, String> TRANSIENT_ONLY = Map.of(
            "ShPostRevisionService.java",
            "builds a detached row from a revision to diff and to digest; it is never persisted",
            "ShPageRenderService.java",
            "builds a throwaway row to render a preview of unsaved content; it is never persisted");

    @Test
    @DisplayName("SH1034: a class that writes a post's data maintains the reference index")
    void everyWriterMaintainsTheIndex() {
        List<String> unmaintained = new ArrayList<>();
        List<String> writers = new ArrayList<>();

        for (Map.Entry<String, String> source : sources().entrySet()) {
            String body = source.getValue();
            if (!body.contains(".setData(") || !persists(body)) {
                continue;
            }
            writers.add(source.getKey());
            if (!body.contains(MAINTAINER)) {
                unmaintained.add(source.getKey());
            }
        }

        System.out.printf("%n[SH1034] %d class(es) write a post's data and maintain the index: %s%n"
                + "         %d declared transient-only: %s%n%n",
                writers.size(), writers, TRANSIENT_ONLY.size(), TRANSIENT_ONLY.keySet());

        assertThat(unmaintained)
                .as("""
                        `sh_object_reference` is DERIVED, so a write that does not maintain it \
                        leaves the index answering with yesterday's edges — and that is silent: \
                        `where is this used` is confidently wrong and `asset-unused` offers a \
                        referenced asset for deletion. There is no choke point to rely on \
                        (ShPostUnifiedWriteService saves from fifteen places and five other classes \
                        bypass it), which is why this is a lint and not a convention.

                        Call `shObjectReferenceService.reindex(saved)` after the save — or \
                        `forget(...)` where the row is going away. If the row is never persisted, \
                        add the class to TRANSIENT_ONLY with the reason.""")
                .isEmpty();

        // The exemptions are checked in the other direction too: a declared transient-only class
        // that has started saving is the dangerous half of this list, and it would otherwise sit
        // there licensing exactly the defect above.
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> exempt : TRANSIENT_ONLY.entrySet()) {
            String body = sources().get(exempt.getKey());
            if (body == null || (body.contains(".setData(") && persists(body))) {
                stale.add(exempt.getKey());
            }
        }
        assertThat(stale)
                .as("""
                        a TRANSIENT_ONLY entry claims its class never persists the row it builds. \
                        These now do, or have gone away — either maintain the index there and drop \
                        the entry, or correct the name.""")
                .isEmpty();
    }

    /** Whether this source persists a post row, in either spelling writers here use. */
    private static boolean persists(String body) {
        return body.contains("shPostUnifiedRepository.save(")
                || body.contains("shPostUnifiedWriteService.");
    }

    private static Map<String, String> sources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            Map<String, String> byName = new TreeMap<>();
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> byName.put(path.getFileName().toString(), read(path)));
            return byName;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The file's CODE, with comments stripped by the shared stripper (SH613).
     *
     * <p>Not the raw text, and this class is exactly the case that rule was written for: it is
     * full of prose naming {@code ShObjectReferenceService} and {@code .setData(} while explaining
     * what it looks for, so a scan over raw bytes would read its own javadoc as a maintained
     * writer — and, worse, would read a neighbouring class's comment about the index as a call to
     * it.</p>
     */
    private static String read(Path path) {
        try {
            return ShSourceText.code(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
