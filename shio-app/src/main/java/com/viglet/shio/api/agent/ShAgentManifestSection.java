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
import java.util.Set;

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
     * {@code apiBase}) survive every projection. They cost about twenty tokens together, and a
     * response that omitted {@code protocol} would be a document a client cannot safely branch
     * on — which turns a saving into a second call. {@code @JsonInclude(NON_NULL)} on the record
     * is what makes an omitted section actually absent from the wire rather than {@code null}.</p>
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
                sections.contains(FEATURES) ? manifest.features() : null,
                sections.contains(LIMITS) ? manifest.limits() : null,
                sections.contains(ENDPOINTS) ? manifest.endpoints() : null,
                sections.contains(START_HERE) ? manifest.startHere() : null);
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
