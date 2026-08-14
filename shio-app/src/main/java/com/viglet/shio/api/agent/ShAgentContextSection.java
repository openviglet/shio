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
    CONVENTIONS,
    /**
     * SH157 — the manifest's {@code features}, {@code limits} and {@code startHere}, folded
     * into this response so a session's two opening reads can be one.
     *
     * <p><b>The one section you have to ask for by name.</b> Every other section is about this
     * instance's content, which is what the pack is; this one is about the protocol, which a
     * session that has already read the manifest knows. Making it default would charge every
     * caller for a document most of them already hold, which is the exact cost this whole
     * block exists to remove.</p>
     */
    MANIFEST,
    /**
     * SH346 — the write vocabulary ({@code ShAgentOps.terse()}), the same bytes
     * {@code shio://ops} serves, so an agent with no MCP client can read which keys each op takes.
     *
     * <p><b>Asked for by name, for the same reason {@link #MANIFEST} is</b>, and the reason is
     * sharper here: this section is about how to <em>write</em>, and the pack is what a session
     * reads before it knows whether it will. Charging every reader for it would put the vocabulary
     * on the same footing as the manifest section SH346 measured and rejected. Like the manifest it
     * is attached after the digest is taken, so asking for it does not invalidate a cached pack.</p>
     */
    OPS,
    /**
     * SH754 — what this tenant has spent today, per surface, the same bytes {@code shio://usage}
     * serves.
     *
     * <p>SH45's meter counts an agent's calls under the {@code agent} surface key and published
     * the total only at {@code GET /api/v2/tenant/usage}, so the caller generating the largest
     * share of the number was the one that could not read it. A budget nobody can query is advice
     * rather than a bound.</p>
     *
     * <p><b>Asked for by name, and this one has a second reason.</b> Like {@link #MANIFEST} and
     * {@link #OPS} it is not content — so it must not move the digest — but unlike them it
     * <em>changes on every call</em>, this one included. Defaulting it on would make the pack a
     * document that never repeats, which is precisely what SH116's conditional read exists to
     * avoid.</p>
     */
    USAGE;

    /**
     * What a bare {@code /agent/context} returns, and what {@code include=all} means.
     *
     * <p>Everything except {@link #MANIFEST}, {@link #OPS} and {@link #USAGE} — see their notes.
     * "All the sections of the context pack" and "the context pack plus a different document" are
     * different requests, and only the first one should be reachable without saying so.</p>
     */
    private static Set<ShAgentContextSection> defaults() {
        return EnumSet.complementOf(EnumSet.of(MANIFEST, OPS, USAGE));
    }

    /**
     * Whether a bare {@code /agent/context} carries this section — the one place "opt-in" is
     * decided.
     *
     * <p>Exposed because {@code omitted} needs the same answer, and the two had drifted: SH754
     * added {@link #USAGE} to {@link #defaults()} and not to the loop that builds {@code omitted},
     * so a default pack reported a section as withheld that no caller had asked for. Two lists of
     * the same fact is one list too many.</p>
     */
    public static boolean isDefault(ShAgentContextSection section) {
        return defaults().contains(section);
    }

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
     * @param raw the raw parameter; blank or {@code all} means every content section.
     *            {@code all} is also valid <em>inside</em> the list, so {@code all,manifest}
     *            is "the usual pack plus this instance's capabilities"
     * @return the requested sections
     * @throws ShTeachingException 400 naming the allowed sections and the nearest
     *         match — an unknown section is a typo, and typos should cost one call
     */
    public static Set<ShAgentContextSection> parse(String raw) {
        if (raw == null || raw.isBlank() || "all".equalsIgnoreCase(raw.trim())) {
            return defaults();
        }
        EnumSet<ShAgentContextSection> sections = EnumSet.noneOf(ShAgentContextSection.class);
        for (String token : raw.split(",")) {
            String name = token.trim();
            if (name.isEmpty()) {
                continue;
            }
            // `all` is a token, not only a whole value (SH157). Once one section had to be
            // asked for by name, `all,manifest` became the natural way to write "the usual
            // pack plus capabilities" — and it is the form the manifest's own example shows,
            // so refusing it would make the endpoint index a set of instructions that do not
            // work. Anywhere else in the list it still means what it always meant.
            if ("all".equalsIgnoreCase(name)) {
                sections.addAll(defaults());
                continue;
            }
            sections.add(resolve(name));
        }
        return sections.isEmpty() ? defaults() : sections;
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
