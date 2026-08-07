/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The projectable sections of the manifest (SH174's {@code include=}).
 *
 * <h2>Why the manifest, of all documents, needed this</h2>
 *
 * <p>The manifest is the first read of every session and the most expensive document on the
 * surface — 2169 estimated tokens against the context pack's 410 — and nearly all of that is
 * the endpoint index: a purpose sentence and a runnable example, twenty-nine times. On a first
 * read that index is the most valuable thing here. On every subsequent read it is pure
 * repetition, and the common subsequent read is an agent confirming <em>one boolean</em> in
 * {@code features}. SH116's ETag already answers the unchanged case for free; this answers the
 * case where the caller does not want the whole document at all.</p>
 *
 * <p>{@code identity} rather than {@code instance}: the four scalars at the top of the manifest
 * are what tells a client <em>which protocol it is talking to</em>, and they are cheap enough
 * that they are always emitted regardless of what was asked for. Naming the section anyway
 * keeps {@code include=} honest — a caller can ask for exactly it — and keeps the vocabulary
 * parallel with {@link ShAgentContextSection}, which is the enum an agent has already read.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShAgentManifestSection {

    IDENTITY,
    FEATURES,
    /**
     * SH393 — where the capabilities this surface does not carry do live.
     *
     * <p>Its own section rather than part of {@code features}, and that is the opposite of the
     * first design: bundled, it took {@code ?include=features,limits} — the cheap capability check
     * an agent repeats every session — from 326 tokens to 469, which P3 makes a build failure and
     * {@code ShAgentResponseBudgetTest} duly failed. Nine pointers are noise beside the full
     * document's endpoint index and a 44% tax on the smallest read on the surface.</p>
     */
    ELSEWHERE,
    LIMITS,
    ENDPOINTS,
    START_HERE;

    /** Lower-case, hyphenated names in declaration order — the {@code allowed} set of the 400. */
    public static List<String> names() {
        return Arrays.stream(values()).map(ShAgentManifestSection::wireName).toList();
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Parses a comma-separated {@code include=} value.
     *
     * @param raw the raw parameter; blank or {@code all} means every section
     * @return the requested sections
     * @throws UnknownManifestSectionException 400 naming the allowed sections and the nearest
     *         match — the same teaching the context pack gives, because a caller that learned
     *         {@code include=} there will try it here
     */
    public static Set<ShAgentManifestSection> parse(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return EnumSet.allOf(ShAgentManifestSection.class);
        }
        EnumSet<ShAgentManifestSection> sections = EnumSet.noneOf(ShAgentManifestSection.class);
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (!name.isEmpty()) {
                sections.add(resolve(name));
            }
        }
        return sections.isEmpty() ? EnumSet.allOf(ShAgentManifestSection.class) : sections;
    }

    /**
     * Projects a manifest down to the requested sections.
     *
     * <p>The identity scalars ({@code product}, {@code version}, {@code protocol},
     * {@code apiBase}, and SH460's {@code commit}/{@code builtAt}) survive every projection. They
     * cost about thirty tokens together, and a response that omitted {@code protocol} would be a
     * document a client cannot safely branch on — which turns a saving into a second call. The
     * build identity survives for the same reason one level down: the cheapest read on this
     * surface is {@code ?include=identity}, which is what a client probing reachability sends,
     * and that is exactly the call that must be able to say which build answered.
     * {@code @JsonInclude(NON_NULL)} on the record is what makes an omitted section actually
     * absent from the wire rather than {@code null}.</p>
     */
    public static ShAgentManifest project(ShAgentManifest manifest, Set<ShAgentManifestSection> sections) {
        if (sections.size() == values().length) {
            return manifest;
        }
        return new ShAgentManifest(
                manifest.product(),
                manifest.version(),
                manifest.protocol(),
                manifest.apiBase(),
                manifest.commit(),
                manifest.builtAt(),
                sections.contains(FEATURES) ? manifest.features() : null,
                sections.contains(ELSEWHERE) ? manifest.elsewhere() : null,
                sections.contains(LIMITS) ? manifest.limits() : null,
                sections.contains(ENDPOINTS) ? manifest.endpoints() : null,
                sections.contains(START_HERE) ? manifest.startHere() : null,
                omitted(manifest, sections));
    }

    /**
     * What the narrowing removed (SH600) — section name and how many entries, never the content.
     *
     * <h2>Why a 200 needed this and a 400 did not</h2>
     *
     * <p>{@link UnknownManifestSectionException} already teaches a caller that misspelled a
     * section. A caller that spelled one correctly and narrowly gets a 200 and no signal at all:
     * {@code ?include=identity} is four scalars and the fifty-seven capability flags, the limits
     * and {@code startHere} are simply gone. It looks like an answer. A skill's opening line
     * carried exactly that call for twelve rounds, and in every one of them the manifest was read
     * and the capability list was never seen — which cost proposing to build two shipped features
     * and diagnosing a 5xx from a server log with {@code diagnostics: true} two lines away in a
     * response nobody had asked for.</p>
     *
     * <p>P6 says errors are instructions and is scoped to the 4xx. <b>The failure here has no
     * error</b>, and a caller narrowing a <em>discovery</em> surface is by construction one who
     * does not yet know what is there — so this is the one place a 200 misleads worse than a 404,
     * because a 404 would have been read.</p>
     *
     * <p>Counts, not names of the things omitted. {@code features=57} is what tells a caller its
     * question was not answered; listing the fifty-seven flag names would cost most of what the
     * projection saved, which would make the fix an argument against itself (P3). Sorted, because
     * the manifest is byte-stable (P5/SH136), and absent entirely when nothing was dropped, so the
     * full document pays nothing for a report it has no content for.</p>
     */
    private static Map<String, Integer> omitted(ShAgentManifest manifest,
                                                Set<ShAgentManifestSection> sections) {
        Map<String, Integer> dropped = new TreeMap<>();
        if (!sections.contains(FEATURES)) {
            dropped.put(FEATURES.wireName(), size(manifest.features()));
        }
        if (!sections.contains(ELSEWHERE)) {
            dropped.put(ELSEWHERE.wireName(), size(manifest.elsewhere()));
        }
        if (!sections.contains(LIMITS) && manifest.limits() != null) {
            // One object rather than a collection, so the honest count is 1. Reporting it at all
            // matters more than the number: `limits` is how a client learns to pace itself, and a
            // caller that does not know it exists learns the ceiling from a 429 instead.
            dropped.put(LIMITS.wireName(), 1);
        }
        if (!sections.contains(ENDPOINTS)) {
            dropped.put(ENDPOINTS.wireName(), size(manifest.endpoints()));
        }
        if (!sections.contains(START_HERE)) {
            dropped.put(START_HERE.wireName(), size(manifest.startHere()));
        }
        return dropped.isEmpty() ? null : dropped;
    }

    private static int size(Collection<?> values) {
        return values == null ? 0 : values.size();
    }

    private static int size(Map<?, ?> values) {
        return values == null ? 0 : values.size();
    }

    private static ShAgentManifestSection resolve(String name) {
        for (ShAgentManifestSection section : values()) {
            if (section.wireName().equalsIgnoreCase(name)) {
                return section;
            }
        }
        throw new UnknownManifestSectionException(name, names());
    }

    /** A 400 that teaches the section vocabulary (SH80). */
    public static class UnknownManifestSectionException extends ShTeachingException {

        public UnknownManifestSectionException(String sent, Collection<String> allowed) {
            super(ShProblem.badRequest("unknown-manifest-section",
                            "Unknown manifest section '" + sent + "'")
                    .withDetail("'" + sent + "' is not a section of the manifest.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Name sections as a comma-separated list, or omit `include` for all "
                            + "of them. `features,limits` is the capability check.")
                    .withExample("?include=features,limits"));
        }
    }
}
