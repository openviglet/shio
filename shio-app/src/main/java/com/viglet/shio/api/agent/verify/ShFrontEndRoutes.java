/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.memory.dto.ShAgentNote;

/**
 * The routes a site's front end owns, <b>declared</b> rather than guessed (SH141).
 *
 * <h2>The problem this solves</h2>
 *
 * <p>SH100 compares a link against the published friendly URLs and, when it matches none,
 * reports {@code link-unresolved} as a <em>warning</em> — because a front end legitimately
 * owns routes the CMS has never heard of: a tag index, a search page, a paginated archive.
 * The warning is honest and nearly useless: on a real site it fires constantly, and a
 * finding that always fires is a finding nobody reads.</p>
 *
 * <p>§IX.2 says the answer has to be <em>declared</em>, and the place to declare a fact
 * about a site that the CMS cannot derive is the SH114 memory — the store whose entire
 * purpose is "things this instance should know that are not content". So a note keyed
 * {@value #MEMORY_KEY}, scoped to the site or instance-wide, lists the patterns. It is
 * attributed to whoever wrote it, it travels in the context pack, it survives a prune, and
 * it is one {@code shio remember} away — none of which would be true of a new table or a
 * config property.</p>
 *
 * <h2>A declaration silences a guess; it never silences a proof</h2>
 *
 * <p>With fetching off, a declared route is simply not reported: the caller has asserted
 * something the CMS cannot check, and taking the assertion at face value is the whole
 * point. With the SH141 delivery pass on, a declared route is still <em>fetched</em> — and
 * a declared route the site answers 404 for is an error, because the declaration made a
 * claim and the claim is wrong. Suppression that survives contact with evidence is how a
 * baseline rots into a blanket, which is the same hazard SH142 was careful about.</p>
 *
 * <h2>Glob, not regex</h2>
 *
 * <p>{@code *} matches within one path segment, {@code **} matches across segments —
 * the grammar every developer already knows from {@code .gitignore} and shell expansion.
 * Not a regex, because a regex in a memory note is a way to accidentally silence the whole
 * site with one stray {@code .*}, and not a plain prefix, because {@code /blog/*} must not
 * cover {@code /blog/2026/deep/nested} unless somebody says {@code /blog/**}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShFrontEndRoutes {

    /** The memory key a site's front-end routes are declared under. */
    public static final String MEMORY_KEY = "frontend-routes";

    /** Patterns per lower-cased site name; the instance-wide ones live under {@code ""}. */
    private final Map<String, List<Pattern>> bySite;

    private ShFrontEndRoutes(Map<String, List<Pattern>> bySite) {
        this.bySite = bySite;
    }

    /** Nothing declared — every unresolved link stays a guess. */
    public static ShFrontEndRoutes none() {
        return new ShFrontEndRoutes(Map.of());
    }

    /**
     * Reads the declarations out of the readable notes.
     *
     * <p>Only the notes the caller may already see are considered, so this adds no new
     * visibility: {@link com.viglet.shio.api.agent.memory.ShAgentMemoryService#notes} has
     * applied the SH2 allow-list before the list gets here.</p>
     */
    public static ShFrontEndRoutes of(List<ShAgentNote> notes) {
        if (notes == null || notes.isEmpty()) {
            return none();
        }
        Map<String, List<Pattern>> bySite = new LinkedHashMap<>();
        for (ShAgentNote note : notes) {
            if (!MEMORY_KEY.equals(note.key())) {
                continue;
            }
            String scope = note.scope() == null ? "" : note.scope().toLowerCase(Locale.ROOT);
            List<Pattern> patterns = bySite.computeIfAbsent(scope, ignored -> new ArrayList<>());
            for (String raw : split(note.note())) {
                Pattern compiled = compile(raw);
                if (compiled != null) {
                    patterns.add(compiled);
                }
            }
        }
        return new ShFrontEndRoutes(bySite);
    }

    /** Whether this site (or the instance) declares the path as the front end's. */
    public boolean declares(String siteName, String path) {
        if (bySite.isEmpty() || path == null) {
            return false;
        }
        String normalized = normalize(path);
        return matches(bySite.get(""), normalized)
                || matches(bySite.get(siteName == null ? "" : siteName.toLowerCase(Locale.ROOT)),
                        normalized);
    }

    /** Whether anything is declared at all — what decides if a report mentions the mechanism. */
    public boolean isEmpty() {
        return bySite.values().stream().allMatch(List::isEmpty);
    }

    private static boolean matches(List<Pattern> patterns, String path) {
        return patterns != null && patterns.stream().anyMatch(each -> each.matcher(path).matches());
    }

    /** Comma, semicolon, newline or whitespace — a human writing a list should not have to care. */
    private static List<String> split(String note) {
        if (note == null || note.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String token : note.split("[,;\\s]+")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    /**
     * Translates one glob into a regex over the normalized path.
     *
     * <p>A pattern that is not a path at all (prose that crept into the note, a bare word)
     * is dropped rather than compiled into something that matches nothing or, worse,
     * everything. A malformed declaration must never widen what is silenced.</p>
     */
    private static Pattern compile(String glob) {
        if (!glob.startsWith("/")) {
            return null;
        }
        StringBuilder regex = new StringBuilder();
        String path = normalize(glob);
        int index = 0;
        while (index < path.length()) {
            char current = path.charAt(index);
            if (current == '*') {
                boolean doubled = index + 1 < path.length() && path.charAt(index + 1) == '*';
                regex.append(doubled ? ".*" : "[^/]*");
                index += doubled ? 2 : 1;
            } else {
                regex.append(Pattern.quote(String.valueOf(current)));
                index++;
            }
        }
        try {
            return Pattern.compile(regex.toString());
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /** The one canonical form the lint compares paths in — the same one duplicate-URL uses. */
    private static String normalize(String path) {
        return ShAddress.normalizePath(path).toLowerCase(Locale.ROOT);
    }
}
