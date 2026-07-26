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

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The projectable sections of a context pack (SH78's {@code include=}).
 *
 * <p>Section-level projection rather than per-field: for a composite document the
 * useful question is "I don't need the sitemap", not "give me 3 of these 9 keys".
 * It is also the caller's escape hatch when the model alone is large — the budget
 * (SH78) will never silently shrink the model, so dropping it must be explicit.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShAgentContextSection {

    INSTANCE,
    SITES,
    MODEL,
    SITEMAP,
    CONVENTIONS;

    /** Lower-case names, in declaration order — the {@code allowed} set of the 400. */
    public static List<String> names() {
        return Arrays.stream(values()).map(ShAgentContextSection::wireName).toList();
    }

    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a comma-separated {@code include=} value.
     *
     * @param raw the raw parameter; blank or {@code all} means every section
     * @return the requested sections
     * @throws ShTeachingException 400 naming the allowed sections and the nearest
     *         match — an unknown section is a typo, and typos should cost one call
     */
    public static Set<ShAgentContextSection> parse(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return EnumSet.allOf(ShAgentContextSection.class);
        }
        EnumSet<ShAgentContextSection> sections = EnumSet.noneOf(ShAgentContextSection.class);
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            sections.add(resolve(name));
        }
        return sections.isEmpty() ? EnumSet.allOf(ShAgentContextSection.class) : sections;
    }

    private static ShAgentContextSection resolve(String name) {
        for (ShAgentContextSection section : values()) {
            if (section.wireName().equalsIgnoreCase(name)) {
                return section;
            }
        }
        throw new UnknownSectionException(name, names());
    }

    /** A 400 that teaches the section vocabulary (SH80). */
    public static class UnknownSectionException extends ShTeachingException {

        public UnknownSectionException(String sent, Collection<String> allowed) {
            super(ShProblem.badRequest("unknown-context-section",
                            "Unknown context section '" + sent + "'")
                    .withDetail("'" + sent + "' is not a section of the context pack.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Name sections as a comma-separated list, or omit `include` for all of them.")
                    .withExample("?include=model,sitemap"));
        }
    }
}
