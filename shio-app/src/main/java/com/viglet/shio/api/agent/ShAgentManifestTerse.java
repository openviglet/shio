/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.List;
import java.util.Map;

import com.viglet.shio.api.agent.dto.ShAgentEndpoint;
import com.viglet.shio.api.agent.dto.ShAgentLimits;
import com.viglet.shio.api.agent.dto.ShAgentManifest;

/**
 * Renders the manifest in the terse line format (SH78's encoding, SH174's document).
 *
 * <h2>What is compressed, and the one thing that is not</h2>
 *
 * <p>The manifest's weight is the endpoint index, and the index's weight is prose: a purpose
 * sentence and a runnable example per row, twenty-nine rows. JSON pays for the five key names
 * on every one of them; the terse form states the shape once in the verb.</p>
 *
 * <p>{@code purpose} and {@code example} are kept <b>verbatim</b>, and that is deliberate. The
 * example is a string the caller is meant to run, so abbreviating it would hand an agent a call
 * the instance would then reject — the same rule {@link ShAgentContextTerse} follows for widget
 * names. What this encoding removes is the structural repetition around them, which is most of
 * the document and none of its meaning.</p>
 *
 * <p>{@code features} is the section that compresses hardest and matters most: a map of
 * ~20 booleans becomes two lines, the present ones and the absent ones, because
 * {@code "conditionalReads": true,} costs roughly four times what {@code conditionalReads}
 * does. Both lines are emitted even when one is empty — an agent reading this to decide whether
 * a capability exists must be able to tell "not in the true list" from "the list is missing",
 * which is exactly the guess the manifest exists to close (P2).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentManifestTerse {

    private static final String FORMAT_HEADER = "shio-manifest";

    private ShAgentManifestTerse() {
        // utility
    }

    /**
     * Renders whichever sections the manifest still carries.
     *
     * <p>A section projected away by {@link ShAgentManifestSection} is {@code null} here, and a
     * null section emits nothing — so the terse form and the JSON form drop exactly the same
     * content for the same {@code include=}, which is what lets one ETag cover the pair as
     * different representations of one state.</p>
     */
    public static String render(ShAgentManifest manifest) {
        StringBuilder out = new StringBuilder();
        out.append(ShTerse.line(FORMAT_HEADER)
                .word(manifest.protocol())
                .attr("product", manifest.product())
                .attr("version", manifest.version())
                .attr("base", manifest.apiBase())
                .build());

        appendFeatures(out, manifest.features());
        appendElsewhere(out, manifest.elsewhere());
        appendLimits(out, manifest.limits());

        for (String step : nullSafe(manifest.startHere())) {
            out.append(ShTerse.line("start").word(step).build());
        }
        for (ShAgentEndpoint endpoint : nullSafe(manifest.endpoints())) {
            out.append(ShTerse.line("endpoint")
                    .word(endpoint.method())
                    .word(endpoint.path())
                    .attr("auth", endpoint.auth())
                    .attr("purpose", endpoint.purpose())
                    .attr("example", endpoint.example())
                    .build());
        }
        return out.toString();
    }

    /**
     * Two lines, present and absent, never one. The whole point of the manifest reporting a
     * capability it does not have is that {@code false} is an answer; folding the false ones
     * away would turn the answer back into a guess.
     */
    private static void appendFeatures(StringBuilder out, Map<String, Boolean> features) {
        if (features == null) {
            return;
        }
        ShTerse.Line on = ShTerse.line("features");
        ShTerse.Line off = ShTerse.line("features-off");
        features.forEach((name, present) -> {
            if (Boolean.TRUE.equals(present)) {
                on.word(name);
            } else {
                off.word(name);
            }
        });
        out.append(on.build()).append(off.build());
    }

    /**
     * One line per capability that lives somewhere else (SH393).
     *
     * <p>A line each rather than one long line, unlike {@code features}: the value is a command a
     * caller is meant to run, and the rule the endpoint examples follow is that <b>a runnable
     * string is never abbreviated</b> — an agent handed a mangled command would run it. Nothing is
     * emitted when the map is empty or absent, which is the honest terse form of "everything this
     * instance advertises is reachable from here".</p>
     */
    private static void appendElsewhere(StringBuilder out, Map<String, String> elsewhere) {
        if (elsewhere == null) {
            return;
        }
        elsewhere.forEach((name, where) ->
                out.append(ShTerse.line("elsewhere").word(name).attr("use", where).build()));
    }

    private static void appendLimits(StringBuilder out, ShAgentLimits limits) {
        if (limits == null) {
            return;
        }
        out.append(ShTerse.line("limits")
                .flag("rate-limited", limits.rateLimitEnabled())
                .count("burst", limits.rateLimitCapacity())
                .count("refill", limits.rateLimitRefillTokens())
                .count("period-seconds", limits.rateLimitPeriodSeconds())
                .count("page-size", limits.defaultPageSize())
                .count("cache-seconds", limits.cacheMaxAgeSeconds())
                .build());
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
