/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a site remembers about the package it was built from (SH155) — the key and the
 * sentence, and nothing else.
 *
 * <h2>Why an instance-memory note and not a table</h2>
 *
 * <p>{@code changed: 0} on a re-apply proves a site matches the document a package compiles
 * to. It does not record that the document came from {@code marketing-site 1.0.0} — so a
 * newer version of a package is undetectable, "which blueprint is this site" is unanswerable,
 * and the version in the apply response is a fact that survives only in whatever transcript
 * happened to capture it.</p>
 *
 * <p>The obvious fix is a row per applied package. The better one is SH114's instance memory,
 * because this is exactly a <em>decision about the site that a later session should not have
 * to re-derive</em> — which is the store's whole purpose. It also lands where it is needed
 * without anybody asking: memory is carried in the context pack's {@code conventions}, so the
 * next session learns what the site is made of in the call it already makes (P2). A table
 * would need an endpoint, a client and a reason to read it.</p>
 *
 * <h2>One note per package, keyed by its name</h2>
 *
 * <p>A site can receive several packages — a {@code landing} and then a {@code blog} — so a
 * single {@code blueprint} key would make the second apply erase the record of the first.
 * Keyed by package name, a re-apply rewrites its own row (the memory is upsert-by-key, so the
 * record cannot grow without bound) and a second package adds one. "What is this site made
 * of" is then the set of {@code blueprint.*} notes scoped to it.</p>
 *
 * <h2>What it is not</h2>
 *
 * <p><b>Not drift detection.</b> Knowing a site received {@code marketing-site 1.0.0} does not
 * mean it still matches it — that answer is a {@code dryRun} away and always was. This is the
 * fact that makes the question <em>askable</em> without a human remembering which package they
 * ran.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShBlueprintRecord {

    /** The key prefix, so the notes about packages are one grep apart from everything else. */
    public static final String KEY_PREFIX = "blueprint.";

    /**
     * Room for the sentence, its parameters, and the {@code (+n more)} that admits a cut. Well
     * under SH114's own 2000-character ceiling: a note is read on the hot path in every context
     * pack, so the budget that matters is the reader's, not the column's (P3).
     */
    private static final int MAX_NOTE = 400;

    /** Mirrors {@code ShAgentMemoryService}'s key grammar: a slug, lowercase. */
    private static final String KEY_CHARS = "[^a-z0-9.-]";

    private ShBlueprintRecord() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The memory key for one package, or {@code null} when its name cannot be a slug.
     *
     * <p>Null rather than a sanitised guess: the first-party names are already slugs, and a
     * third-party package (SH96) whose name is not one has a naming problem the record must
     * not paper over by filing itself under a name nobody can search for.</p>
     */
    public static String keyFor(String blueprint) {
        if (blueprint == null || blueprint.isBlank()) {
            return null;
        }
        String slug = blueprint.trim().toLowerCase(Locale.ROOT);
        if (!slug.equals(slug.replaceAll(KEY_CHARS, "")) || !slug.matches("[a-z0-9][a-z0-9.-]*")) {
            return null;
        }
        return KEY_PREFIX + slug;
    }

    /**
     * The sentence a later session reads in its context pack.
     *
     * <p>Parameters are included because they are half the answer: {@code marketing-site} says
     * what shape the site is, and {@code brand=Acme} says why it looks like this one. They are
     * sorted by name so the same apply always writes the same bytes (P5) — a note whose text
     * churned would rewrite the row, and the modification date is the record of when the
     * package was last confirmed.</p>
     *
     * <p>{@code site} is dropped: it is the reserved parameter, it always equals the note's own
     * scope, and repeating it in every record costs tokens in every context pack.</p>
     */
    public static String noteFor(String blueprint, String version, Map<String, String> params) {
        StringBuilder text = new StringBuilder("Built from blueprint '")
                .append(blueprint).append('\'');
        if (version != null && !version.isBlank()) {
            text.append(' ').append(version.trim());
        }
        List<String> pairs = pairsOf(params);
        if (pairs.isEmpty()) {
            return text.append('.').toString();
        }
        text.append(" — params: ");
        int kept = 0;
        for (String pair : pairs) {
            // Enough room for this pair plus the worst-case admission that others were cut.
            int budget = MAX_NOTE - " (+00 more)".length() - (kept > 0 ? 2 : 0);
            if (text.length() + pair.length() > budget) {
                break;
            }
            if (kept > 0) {
                text.append("; ");
            }
            text.append(pair);
            kept++;
        }
        int dropped = pairs.size() - kept;
        if (dropped > 0) {
            // An unreported cut would read as "these were all the parameters", which is the
            // one way this record could mislead the session that reads it back (SH138's rule).
            text.append(" (+").append(dropped).append(" more)");
        }
        return text.toString();
    }

    /** {@code name=value} for every parameter but the reserved {@code site}, sorted by name. */
    private static List<String> pairsOf(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return List.of();
        }
        Map<String, String> sorted = new TreeMap<>(params);
        List<String> pairs = new ArrayList<>(sorted.size());
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (entry.getKey() == null || "site".equals(entry.getKey())) {
                continue;
            }
            pairs.add(entry.getKey() + "=" + (entry.getValue() == null ? "" : entry.getValue()));
        }
        return List.copyOf(pairs);
    }
}
