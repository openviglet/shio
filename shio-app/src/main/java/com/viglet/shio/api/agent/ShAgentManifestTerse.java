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
 * <p>{@code example} is kept <b>verbatim</b>, and that is deliberate: it is a string the caller is
 * meant to run, so abbreviating it would hand an agent a call the instance would then reject — the
 * same rule {@link ShAgentContextTerse} follows for widget names.</p>
 *
 * <p><b>{@code purpose} is cut to its lead sentence (SH230), and until then this paragraph said
 * "verbatim" of both.</b> The reason given was runnability, which is an argument about the example
 * and was never one about the purpose — a sentence is read, not executed. Holding them to one rule
 * cost the document 21%: measured, purpose is 4001 characters across the index and example is
 * 1079, so the encoding was protecting the smaller half and compacting nothing that mattered.
 * See {@link #lead(String)}.</p>
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
                // SH460 — which build answered. Absent attributes cost nothing here (ShTerse
                // omits a null), so an instance that cannot read its own timestamp says so by
                // silence rather than by an empty field a client would have to interpret.
                .attr("commit", manifest.commit())
                .attr("built", manifest.builtAt())
                .build());

        appendOmitted(out, manifest.omitted());
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
                    .attr("purpose", lead(endpoint.purpose()))
                    .attr("example", endpoint.example())
                    .build());
        }
        return out.toString();
    }

    /**
     * An endpoint's purpose reduced to its lead sentence — the terse half of SH230.
     *
     * <p>SH174 measured {@code format=terse} at 2088 against 2343 full: <b>11%</b>, where the same
     * encoding is worth a third to a half everywhere else. SH230 asked why, and the answer is that
     * the manifest is not rows — its weight is prose, and the terse encoding only ever removed the
     * structure around it.</p>
     *
     * <p>Measured over the twenty-eight entries: <b>purpose is 4001 characters and example is
     * 1079</b>. So the two repairs SH230 proposed both aimed at the smaller half — eliding the
     * thirteen derivable examples saves 334 characters, about 3.6% of the document. Truncating each
     * purpose to its first sentence saves <b>2010, about 21%</b>, and takes terse from 1.1x to a
     * ratio worth asking for.</p>
     *
     * <p>Why this is safe where abbreviating the example is not, and the distinction the class
     * javadoc used to blur: the example is <b>run</b>, so cutting it hands an agent a call the
     * instance rejects. The purpose is <b>read</b>, and its lead sentence is what the endpoint IS —
     * the tail is parameter guidance ({@code include=}, {@code format=}) that a caller who has
     * already chosen the endpoint asks for in full. {@code terse} is opt-in and its whole contract
     * is that it compacts; a caller who wants every word asks for {@code json}.</p>
     */
    static String lead(String purpose) {
        if (purpose == null) {
            return null;
        }
        // Scanned by index rather than matched by regex, because the lead is CUMULATIVE from the
        // start: a `find()` loop returns each following segment, so the second iteration would
        // answer "a draft." where the sentence is "Ask for e.g. a draft."
        for (int at = 0; at < purpose.length(); at++) {
            char here = purpose.charAt(at);
            if (here != '.' && here != '?' && here != '!') {
                continue;
            }
            boolean endsAWord = at + 1 == purpose.length()
                    || Character.isWhitespace(purpose.charAt(at + 1));
            // The shortest real purpose in the index is 30 characters, so a stop before MIN_LEAD
            // is an abbreviation's — `e.g.`, `i.e.`, `cf.` — rather than an end. A capital-letter
            // test would be the usual heuristic and is wrong here: half these sentences are
            // followed by `include=…` or `format=…`, which are lower case by nature.
            if (endsAWord && at + 1 >= MIN_LEAD) {
                return purpose.substring(0, at + 1);
            }
        }
        return purpose;
    }

    /** Below this, a full stop is punctuation inside a sentence rather than the end of one. */
    private static final int MIN_LEAD = 20;

    /**
     * What {@code include=} removed (SH600), <b>first</b> and on one line.
     *
     * <p>First because it is the line that tells a caller its question was not answered, and a
     * reader that stops early has to meet it before the sections that survived. One line because
     * it is a handful of counts: {@code omitted features=57 limits=1 endpoints=28 start-here=5} is
     * the whole report, and it is absent entirely on a full document — nothing was dropped, so
     * there is nothing to say and no tokens to spend saying it.</p>
     */
    private static void appendOmitted(StringBuilder out, Map<String, Integer> omitted) {
        if (omitted == null || omitted.isEmpty()) {
            return;
        }
        ShTerse.Line line = ShTerse.line("omitted");
        // `attr` and not `count`: count folds a zero away, and a dropped section that happened to
        // be empty is exactly the case a caller still has to be told about — "there is an
        // `elsewhere` section and you did not ask for it" is the report, not the number.
        omitted.forEach(line::attr);
        out.append(line.build());
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
