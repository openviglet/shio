/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post.type;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH293 — a post-type name this system declares must be a post-type this system creates.
 *
 * <h2>What the audit found</h2>
 *
 * <p>{@link ShSystemPostType} declared thirteen names transcribed from the 2018 model. Five had a
 * creator; <b>eight were referenced nowhere but in that file</b> — {@code Photo}, {@code Video},
 * {@code Quote}, {@code Link}, {@code TextArea}, {@code Article}, {@code FolderIndex},
 * {@code Alias}. Nothing built them, nothing looked them up, and one had been wrong since it was
 * written: the 2018 model says {@code Links} and the constant said {@code Link}. Nothing could
 * notice, because a constant nobody reads is a string nobody compares.</p>
 *
 * <p>That is the same defect as {@code ShWidget.implementationCode} pointing at a directory that
 * does not exist (SH285, still open) — a declaration that reads as a capability. The deletion was
 * the fix; this test is what stops it coming back, which is SH322's habit: the grep that found the
 * eight lands as a lint, not as a paragraph.</p>
 *
 * <h2>What counts as a creator, and why the scan is narrow on purpose</h2>
 *
 * <p>A <b>reference</b> is not a creator — that distinction is the whole point. {@code TEXT} is read
 * by {@code ShUserOnStartup} to seed a user's "last post type used" and tolerates its absence, so a
 * scan for "the constant appears in main" would have called it provisioned and passed on a name
 * nothing ever builds. So the pattern is creation-shaped: the constant passed to {@code setName},
 * to an {@code ensure(...)} call, or listed in {@code ShRenderPostTypes.NAMES}. Anything looked up
 * but never created has to be declared below, with the reason, and a declaration that stops being
 * needed fails the build (SH142's rule).</p>
 *
 * @author Alexandre Oliveira
 */
class ShSystemPostTypeInventoryTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "viglet", "shio");

    /**
     * Names this system reads but never creates, with why that is right.
     *
     * <p>Not a suppression list — an inventory of the exceptions, which is the artefact somebody
     * reading {@link ShSystemPostType} actually needs.</p>
     */
    private static final Map<String, String> LOOKED_UP_ONLY = new TreeMap<>(Map.of(
            "TEXT", "ShUserOnStartup seeds ShUser.lastPostType from it and tolerates a null, "
                    + "which on a fresh install is what it gets — a default for the console, not a "
                    + "type this system provisions"));

    @Test
    @DisplayName("SH293: every declared post-type name has a creator, or is declared as looked-up")
    void everyConstantHasACreator() {
        Map<String, String> declared = constants();
        List<String> sources = mainSources();

        List<String> orphans = new ArrayList<>();
        for (Map.Entry<String, String> constant : declared.entrySet()) {
            if (LOOKED_UP_ONLY.containsKey(constant.getKey())) {
                continue;
            }
            if (!creates(sources, constant.getKey())) {
                orphans.add(constant.getKey() + " = \"" + constant.getValue() + "\"");
            }
        }

        assertThat(orphans)
                .as("""
                        A name nothing creates reads as a capability this system has. Eight of these \
                        were deleted by SH293 — one of them (Link, against the 2018 model's Links) \
                        had been misspelled since it was written, and nothing could notice because \
                        a constant nobody reads is a string nobody compares. Either add the creator, \
                        delete the constant, or declare it in LOOKED_UP_ONLY with the reason.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH293: a declared exception that gained a creator fails, so the list cannot rot")
    void everyDeclaredExceptionIsStillAnException() {
        Map<String, String> declared = constants();
        List<String> sources = mainSources();

        List<String> stale = new ArrayList<>();
        LOOKED_UP_ONLY.forEach((name, reason) -> {
            if (!declared.containsKey(name)) {
                stale.add(name + " — declared here and no longer a constant at all");
            } else if (creates(sources, name)) {
                stale.add(name + " — declared as looked-up only, and something now creates it");
            }
        });

        assertThat(stale)
                .as("SH142's rule: a declaration that stops being true is worse than no "
                        + "declaration, because it is read as current")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard is not vacuous: it sees the creators that exist")
    void theScanFindsRealCreators() {
        List<String> sources = mainSources();

        assertThat(constants().keySet())
                .as("a reflection read that came back empty would pass forever")
                .contains("FILE", "REGION", "THEME", "PAGE_LAYOUT", "TEXT")
                .hasSizeLessThan(10);
        assertThat(creates(sources, "FILE"))
                .as("ShStaticFileService.resolveOrCreateFilePostType calls setName(FILE) — if this "
                        + "goes false the pattern has stopped matching creation and the guard is "
                        + "asserting nothing")
                .isTrue();
        assertThat(creates(sources, "PAGE_LAYOUT"))
                .as("ShRenderPostTypes lists it in NAMES and ensures it")
                .isTrue();
        assertThat(creates(sources, "TEXT"))
                .as("and the distinction the whole test rests on: TEXT is READ by ShUserOnStartup "
                        + "and created nowhere, so a scan that called this true would be matching "
                        + "references rather than creation")
                .isFalse();
    }

    /** Whether main creates this type: {@code setName}, {@code ensure(…)}, or a declared name list. */
    private static boolean creates(List<String> sources, String constant) {
        String reference = "ShSystemPostType." + constant;
        return sources.stream().anyMatch(body -> body.contains("setName(" + reference + ")")
                || body.contains("ensure(" + reference + ",")
                || namesListContains(body, reference));
    }

    /**
     * Whether {@code ShRenderPostTypes.NAMES} names it — the list that drives {@code ensure}.
     *
     * <p>Per file rather than over the concatenation: several classes declare a {@code NAMES} list,
     * and a scan that took the first one would answer about whichever file the walk happened to
     * reach first.</p>
     */
    private static boolean namesListContains(String body, String reference) {
        int start = body.indexOf("NAMES = List.of(");
        if (start < 0) {
            return false;
        }
        int end = body.indexOf(");", start);
        return end > start && body.substring(start, end).contains(reference);
    }

    private static Map<String, String> constants() {
        Map<String, String> values = new TreeMap<>();
        for (Field field : ShSystemPostType.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    values.put(field.getName(), (String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(field.getName(), e);
                }
            }
        }
        return values;
    }

    private static List<String> mainSources() {
        try (Stream<Path> files = Files.walk(MAIN)) {
            List<String> bodies = new ArrayList<>();
            // Sorted, so the scan reads the same files in the same order on every machine (P5) —
            // and not the constants file itself, since every name is in it by definition and a scan
            // that read it would call every constant its own creator.
            new TreeSet<>(files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("ShSystemPostType.java"))
                    .map(Path::toString).toList())
                    .forEach(path -> bodies.add(read(Path.of(path))));
            return bodies;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
