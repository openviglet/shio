/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.utils.ShStableMap;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1056 — a site's locale policy: which language it is in, which it offers, and what to show
 * when the one asked for is missing.
 *
 * <h2>Why this is a value and not three columns</h2>
 *
 * <p>The three answers are only meaningful together — a default that is not in the enabled set is
 * not a default, and a fallback pointing at a locale the site does not offer is a redirect to
 * nothing. Validating them as one value is what lets the refusal say which of the three is wrong;
 * three columns would each be individually valid and jointly meaningless.</p>
 *
 * <h2>Declared, or derived — and the difference is visible</h2>
 *
 * <p>Before this, a site's languages were <em>inferred</em> from the locales its posts happened to
 * carry, deliberately: there was no setting, so inferring was the only honest answer. That answer
 * survives as {@link #derived}, which is what an undeclared site still reports, so nothing changes
 * behaviour on upgrade. {@link #declared()} is what tells a reader which of the two it is holding,
 * because "this site offers English and Portuguese" means one thing when somebody chose it and
 * another when it is a description of what has been typed so far.</p>
 *
 * <h2>Stable bytes</h2>
 *
 * <p>{@link #toJson} writes three keys in a fixed order, the enabled list in the order it was
 * declared — a switcher's order is a decision — and the fallback map sorted by key, since a map's
 * order is not. P5: the same policy serializes to the same bytes, so a projection of it does not
 * show a diff nobody made.</p>
 *
 * @param defaultLocale the locale this site is written in, always a member of {@code enabled}
 * @param enabled       the locales it offers, in the order it offers them, never empty
 * @param fallback      locale to show instead when a page is missing in it; keys and values are
 *                      members of {@code enabled}, and the chains never loop
 * @param declared      whether somebody set this, or it was read off the content
 * @param labels        SH1281 — what a language switcher calls a locale, keyed by enabled locales;
 *                      empty means every locale keeps its name from {@code ShLanguageNames}
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShSiteLocales(String defaultLocale, List<String> enabled,
                            Map<String, String> fallback, boolean declared,
                            Map<String, String> labels) {

    /** The keys of the stored document, in the order {@link #toJson} writes them. */
    public static final String DEFAULT_KEY = "default";
    public static final String ENABLED_KEY = "enabled";
    public static final String FALLBACK_KEY = "fallback";
    /** SH1281 — written only when a label is set, so a policy without one keeps its bytes. */
    public static final String LABELS_KEY = "labels";

    /** The op's own key, on {@code site.upsert}'s {@code data}. */
    public static final String FIELD = "locales";

    public ShSiteLocales {
        enabled = List.copyOf(enabled);
        // ShStableMap and not Map.copyOf: the latter's iteration order comes from a per-JVM salt,
        // so this document would serialize to different bytes after a restart — invisible in one
        // process, which is the whole reason ShByteStabilityLintTest exists to find it.
        fallback = ShStableMap.copyOf(fallback);
        labels = ShStableMap.copyOf(labels == null ? Map.of() : labels);
    }

    /** A policy with no labels: every caller before SH1281. */
    public ShSiteLocales(String defaultLocale, List<String> enabled, Map<String, String> fallback,
                         boolean declared) {
        this(defaultLocale, enabled, fallback, declared, Map.of());
    }

    /**
     * What a site with no declared policy reports — today's behaviour, unchanged.
     *
     * <p>The union of the locales its content is written in, sorted, with the instance default
     * included so a site with no content at all still answers something a switcher can render. The
     * default is the instance default when the content uses it and the first locale otherwise: a
     * site written entirely in Portuguese should not report that it is in English because nobody
     * has said otherwise.</p>
     */
    public static ShSiteLocales derived(String instanceDefault, Collection<String> inContent) {
        Set<String> union = new TreeSet<>();
        for (String locale : inContent == null ? List.<String>of() : inContent) {
            if (locale != null && !locale.isBlank()) {
                union.add(locale.trim());
            }
        }
        if (union.isEmpty()) {
            union.add(instanceDefault);
        }
        String fallbackDefault = union.contains(instanceDefault) ? instanceDefault
                : union.iterator().next();
        return new ShSiteLocales(fallbackDefault, List.copyOf(union), Map.of(), false);
    }

    /**
     * Validates a policy somebody is asking for, against the locales this instance knows.
     *
     * <p>Every refusal names the offending value and the allowed set (P6), because the caller is
     * usually an agent that has just guessed a tag — {@code pt-BR} for {@code pt-br}, or
     * {@code en-us} for {@code en}, which is the mismatch this model already carries between the
     * storage default and the delivery URL.</p>
     *
     * @param known the seeded locale tags, i.e. what {@code GET /api/v2/locale} lists
     * @throws ShTeachingException 400 naming the field, the value and what was allowed
     */
    public static ShSiteLocales of(String defaultLocale, List<String> enabled,
                                   Map<String, String> fallback, Collection<String> known) {
        return of(defaultLocale, enabled, fallback, Map.of(), known);
    }

    /**
     * As above, with the switcher's labels (SH1281): each key an enabled locale, each value a
     * non-blank label. A key outside {@code enabled} is refused the way a fallback's is, since a
     * label for a language the site does not offer is one no switcher can ever show.
     */
    public static ShSiteLocales of(String defaultLocale, List<String> enabled,
                                   Map<String, String> fallback, Map<String, String> labels,
                                   Collection<String> known) {
        List<String> offered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String locale : enabled == null ? List.<String>of() : enabled) {
            String tag = locale == null ? "" : locale.trim();
            requireKnown(tag, ENABLED_KEY, known);
            if (seen.add(tag)) {
                offered.add(tag);
            }
        }
        if (offered.isEmpty()) {
            throw new EmptyEnabledException();
        }
        String chosen = defaultLocale == null || defaultLocale.isBlank()
                ? offered.get(0) : defaultLocale.trim();
        requireKnown(chosen, DEFAULT_KEY, known);
        if (!offered.contains(chosen)) {
            throw new DefaultNotEnabledException(chosen, offered);
        }
        Map<String, String> chains = new TreeMap<>();
        for (Map.Entry<String, String> entry
                : (fallback == null ? Map.<String, String>of() : fallback).entrySet()) {
            String from = entry.getKey() == null ? "" : entry.getKey().trim();
            String to = entry.getValue() == null ? "" : entry.getValue().trim();
            requireOffered(from, offered);
            requireOffered(to, offered);
            if (from.equals(to)) {
                throw new FallbackLoopException(List.of(from, to));
            }
            chains.put(from, to);
        }
        requireNoLoop(chains);
        Map<String, String> named = new TreeMap<>();
        for (Map.Entry<String, String> entry
                : (labels == null ? Map.<String, String>of() : labels).entrySet()) {
            String tag = entry.getKey() == null ? "" : entry.getKey().trim();
            String label = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!offered.contains(tag)) {
                throw new LabelNotEnabledException(tag, offered);
            }
            if (!label.isEmpty()) {
                named.put(tag, label);
            }
        }
        return new ShSiteLocales(chosen, offered, chains, true, named);
    }

    /**
     * Reads a stored policy, or empty when the column holds nothing.
     *
     * <p>Not validated against the locale catalogue on the way out, on purpose: a tag that was
     * known when it was written and has since been removed from the catalogue must not make the
     * site unreadable. The write side is where a policy is judged.</p>
     *
     * @throws JacksonException the column is not JSON — the caller decides what an unreadable
     *                          policy means, because the answer differs between a render and a write
     */
    public static java.util.Optional<ShSiteLocales> parse(String stored, ObjectMapper mapper) {
        if (stored == null || stored.isBlank()) {
            return java.util.Optional.empty();
        }
        JsonNode node = mapper.readTree(stored);
        if (!node.isObject()) {
            return java.util.Optional.empty();
        }
        List<String> offered = new ArrayList<>();
        JsonNode enabled = node.get(ENABLED_KEY);
        if (enabled != null && enabled.isArray()) {
            for (JsonNode each : enabled) {
                if (each.isString() && !each.stringValue().isBlank()) {
                    offered.add(each.stringValue().trim());
                }
            }
        }
        if (offered.isEmpty()) {
            return java.util.Optional.empty();
        }
        JsonNode chosen = node.get(DEFAULT_KEY);
        String defaultLocale = chosen != null && chosen.isString() && !chosen.stringValue().isBlank()
                ? chosen.stringValue().trim() : offered.get(0);
        if (!offered.contains(defaultLocale)) {
            defaultLocale = offered.get(0);
        }
        Map<String, String> chains = new TreeMap<>();
        JsonNode fallback = node.get(FALLBACK_KEY);
        if (fallback != null && fallback.isObject()) {
            for (String from : fallback.propertyNames()) {
                JsonNode to = fallback.get(from);
                if (to != null && to.isString() && offered.contains(from)
                        && offered.contains(to.stringValue().trim())) {
                    chains.put(from, to.stringValue().trim());
                }
            }
        }
        Map<String, String> named = new TreeMap<>();
        JsonNode labels = node.get(LABELS_KEY);
        if (labels != null && labels.isObject()) {
            for (String tag : labels.propertyNames()) {
                JsonNode label = labels.get(tag);
                if (label != null && label.isString() && offered.contains(tag)
                        && !label.stringValue().isBlank()) {
                    named.put(tag, label.stringValue().trim());
                }
            }
        }
        return java.util.Optional.of(new ShSiteLocales(defaultLocale, offered, chains, true, named));
    }

    /** The stored form: three keys, fixed order, map sorted (P5). */
    public String toJson(ObjectMapper mapper) {
        return mapper.writeValueAsString(toNode());
    }

    /**
     * The same document as a node (SH1122), which is what a reader that hands it on needs.
     *
     * <p>{@link #toJson} delegates here so there is <b>one</b> place that knows this document's shape.
     * The context pack carries the stored policy so a site marker file can round-trip exactly, and a
     * pack that built those three keys for itself would be a second spelling of them — the shape that
     * agrees today and diverges the first time a key moves. Same rules, stated once: the three keys in
     * a fixed order, {@code enabled} in the order it was declared because a switcher's order is a
     * decision, and {@code fallback} sorted because a map's order is not (P5).</p>
     */
    public ObjectNode toNode() {
        ObjectNode document = JsonNodeFactory.instance.objectNode();
        document.put(DEFAULT_KEY, defaultLocale);
        ArrayNode offered = document.putArray(ENABLED_KEY);
        for (String locale : enabled) {
            offered.add(locale);
        }
        ObjectNode chains = document.putObject(FALLBACK_KEY);
        for (Map.Entry<String, String> entry : new TreeMap<>(fallback).entrySet()) {
            chains.put(entry.getKey(), entry.getValue());
        }
        // SH1281 — a fourth key only when there is something in it, sorted like the fallback map,
        // so every policy written before labels existed serializes to the bytes it always did.
        if (!labels.isEmpty()) {
            ObjectNode named = document.putObject(LABELS_KEY);
            for (Map.Entry<String, String> entry : new TreeMap<>(labels).entrySet()) {
                named.put(entry.getKey(), entry.getValue());
            }
        }
        return document;
    }

    /** SH1281 — what a switcher calls this locale, or null when the site has not said. */
    public String labelOf(String locale) {
        return locale == null ? null : labels.get(locale.trim());
    }

    /**
     * The locales to try for a reader who asked for this one, best first.
     *
     * <p>The requested locale, then its fallback, then that one's, ending at the default. This is
     * what the map is <em>for</em>: a site offering {@code pt-br}, {@code pt} and {@code en} wants
     * a missing Brazilian page to show the Portuguese one before the English one, and a flat
     * "fall back to the default" cannot say that.</p>
     */
    public List<String> chain(String wanted) {
        List<String> order = new ArrayList<>();
        String at = wanted == null || wanted.isBlank() ? defaultLocale : wanted.trim();
        while (at != null && !order.contains(at)) {
            order.add(at);
            at = fallback.get(at);
        }
        if (!order.contains(defaultLocale)) {
            order.add(defaultLocale);
        }
        return List.copyOf(order);
    }

    /** Whether this site offers a locale at all — what a switcher and a route check ask. */
    public boolean offers(String locale) {
        return locale != null && enabled.contains(locale.trim());
    }

    private static void requireKnown(String tag, String field, Collection<String> known) {
        if (tag.isEmpty() || (known != null && !known.contains(tag))) {
            throw new UnknownLocaleException(field, tag, known);
        }
    }

    private static void requireOffered(String tag, List<String> offered) {
        if (!offered.contains(tag)) {
            throw new FallbackNotEnabledException(tag, offered);
        }
    }

    /**
     * A fallback map whose chains end.
     *
     * <p>{@code pt-br → es} and {@code es → pt-br} is a policy {@link #chain} would walk forever if
     * it trusted the data, and the guard in {@code chain} stops the loop rather than reporting it.
     * Refusing it here is what makes that guard a belt rather than the only thing holding the
     * page up.</p>
     */
    private static void requireNoLoop(Map<String, String> chains) {
        for (String start : chains.keySet()) {
            Set<String> walked = new LinkedHashSet<>();
            String at = start;
            while (at != null && walked.add(at)) {
                at = chains.get(at);
            }
            if (at != null) {
                List<String> loop = new ArrayList<>(walked);
                loop.add(at);
                throw new FallbackLoopException(loop);
            }
        }
    }

    /** A 400 that teaches the locale vocabulary (SH1056). */
    public static class UnknownLocaleException extends ShTeachingException {

        public UnknownLocaleException(String field, String sent, Collection<String> known) {
            super(ShProblem.badRequest("unknown-locale",
                            "Unknown locale '" + sent + "' in " + FIELD + "." + field)
                    .withDetail("'" + sent + "' is not a locale this instance knows. The catalogue "
                            + "is the one GET /api/v2/locale lists, and a tag is case-sensitive: "
                            + "pt-br, not pt-BR.")
                    .withAllowed(known == null ? List.of() : new ArrayList<>(known))
                    .withDidYouMeanFrom(sent)
                    .withFix("Name locales from the catalogue, lower-case, as a JSON array.")
                    .withExample("{\"locales\":{\"default\":\"en\",\"enabled\":[\"en\",\"pt-br\"]}}"));
        }
    }

    /** A 400 for a policy that offers nothing (SH1056). */
    public static class EmptyEnabledException extends ShTeachingException {

        public EmptyEnabledException() {
            super(ShProblem.badRequest("empty-locales",
                            "A locale policy has to enable at least one locale")
                    .withDetail("`" + FIELD + "." + ENABLED_KEY + "` was absent or empty, which "
                            + "would leave the site offering no language at all — not the same as "
                            + "leaving the policy unset, which reports the locales the content is "
                            + "already written in.")
                    .withFix("List the locales this site offers, or omit `" + FIELD + "` entirely "
                            + "to keep deriving them from the content.")
                    .withExample("{\"locales\":{\"default\":\"en\",\"enabled\":[\"en\",\"pt-br\"]}}"));
        }
    }

    /** A 400 for a default the site does not offer (SH1056). */
    public static class DefaultNotEnabledException extends ShTeachingException {

        public DefaultNotEnabledException(String sent, List<String> offered) {
            super(ShProblem.badRequest("default-not-enabled",
                            "The default locale '" + sent + "' is not one this site offers")
                    .withDetail("A default outside the enabled set is not a default: every page "
                            + "that fell back to it would fall back to a language the site says "
                            + "it does not publish.")
                    .withAllowed(offered)
                    .withDidYouMeanFrom(sent)
                    .withFix("Add '" + sent + "' to `" + FIELD + "." + ENABLED_KEY + "`, or name a "
                            + "default that is already there."));
        }
    }

    /** A 400 for a fallback that leaves the enabled set (SH1056). */
    public static class FallbackNotEnabledException extends ShTeachingException {

        public FallbackNotEnabledException(String sent, List<String> offered) {
            super(ShProblem.badRequest("fallback-not-enabled",
                            "The fallback locale '" + sent + "' is not one this site offers")
                    .withDetail("Both sides of a fallback have to be enabled: a rule pointing at a "
                            + "locale the site does not publish sends a reader to a page that can "
                            + "never exist.")
                    .withAllowed(offered)
                    .withDidYouMeanFrom(sent)
                    .withFix("Enable '" + sent + "' or drop the rule that names it."));
        }
    }

    /** A 400 for a label on a locale the site does not offer (SH1281). */
    public static class LabelNotEnabledException extends ShTeachingException {

        public LabelNotEnabledException(String sent, List<String> offered) {
            super(ShProblem.badRequest("label-not-enabled",
                            "The label for '" + sent + "' names a locale this site does not offer")
                    .withDetail("A switcher only lists the locales in `" + FIELD + "." + ENABLED_KEY
                            + "`, so a label for any other is one no reader can ever see.")
                    .withAllowed(offered)
                    .withDidYouMeanFrom(sent)
                    .withFix("Key `" + FIELD + "." + LABELS_KEY + "` by an enabled locale, or "
                            + "enable '" + sent + "' first.")
                    .withExample("{\"labels\":{\"pt-br\":\"Português (Brasil)\",\"pt-pt\":\"Português (Portugal)\"}}"));
        }
    }

    /** A 400 for a fallback that never ends (SH1056). */
    public static class FallbackLoopException extends ShTeachingException {

        public FallbackLoopException(List<String> loop) {
            super(ShProblem.badRequest("fallback-loop",
                            "The fallback rules loop: " + String.join(" → ", loop))
                    .withDetail("A fallback chain has to end somewhere. This one returns to a "
                            + "locale it already passed through, so a page missing in any of them "
                            + "would have nowhere to land.")
                    .withFix("Point the last locale in the chain at the default, or leave it out — "
                            + "an unlisted locale already falls back to the default.")
                    .withExample("{\"fallback\":{\"pt-br\":\"pt\",\"pt\":\"en\"}}"));
        }
    }
}
