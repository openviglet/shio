/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SH990 — which content read can be asked for a version of the page, and which cannot.
 *
 * <h2>The asymmetry this counts</h2>
 *
 * <p>{@code /agent/read} took {@code state}, understood {@code rev:<n>} and refused a typo;
 * {@code /preview} took it and read every value but {@code PUBLISHED} as the draft; nothing else did.
 * So an agent could read the draft of a page and could not render it, verify it, find it or ask
 * whether it was editable, and a front end on the CDA could not show anyone a draft or a revision at
 * all. The two paths that did accept a value each parsed it themselves.</p>
 *
 * <p>The instrument landed before the plumbing (SH322): it reads the three controllers a content read
 * goes through as source and classifies every {@code GET} as accepting {@code state} or not, with the
 * reason. The reads that should have taken it and did not were one named family, worked down over
 * five slices until it was empty; the family is gone with them, so a new page read has to take a
 * version or say why not, and a path that gains or loses the parameter fails until its row moves.</p>
 *
 * <h2>What it does not scan</h2>
 *
 * <p>The CDA's GraphQL schema is built in code ({@code ShGraphQLSchema}), not by annotation, so it is
 * outside this reader; {@code ShCdaStateTest} holds its {@code state} argument instead. The console's
 * {@code GET /api/v2/post-unified/{id}} binds a closed {@code ShPostState} the console itself sends,
 * and the public {@code /sites/**} route serves what is published by contract — a version parameter
 * there is a way to publish a draft by URL.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShPerspectiveCoverageLintTest {

    /** The controllers a read of a page goes through: the agent's, the preview's and the CDA's. */
    private static final List<String> CONTENT_READERS = List.of(
            "ShAgentAPI.java", "ShRenderAPI.java", "ShCdaAPI.java");

    /**
     * A parameter named {@code state}, by its request name, its Java name, or the vocabulary.
     *
     * <p>SH1022 added the third spelling and it is the one that matters now: a read whose filters
     * are {@code ShContentQuery} takes {@code state} through the shared vocabulary rather than
     * through a {@code @RequestParam} of its own, and the map it binds is how it refuses a word
     * nobody serves. The first two spellings would read that as "no version", which is the census
     * going red for a change that made the answer <em>more</em> true — and the fix for that is
     * never a second {@code state} parameter beside the map: two places parsing one value is what
     * this lint's own doc says is a finding at the moment it is written.</p>
     */
    private static final Pattern STATE =
            Pattern.compile("\"state\"|\\bString\\s+state\\b|\\bShContentQuery\\b");

    /** The grammar's own spelling of a revision, which only the grammar may carry. */
    private static final Pattern REVISION_LITERAL = Pattern.compile("\"rev:\"");

    // --- why a path takes no version. Families, so the list is short enough to be read ------------

    private static final String NOT_A_PAGE =
            "it answers about the instance, its model or its packages, and none of those has a draft "
            + "or a revision of its own";
    private static final String HISTORY =
            "it reads a history of what happened, which has one version — the one that happened";

    /** What the tree says today, keyed by {@code file#method}; an accepting read carries "". */
    private static final Map<String, String> DECLARED = new TreeMap<>(Map.ofEntries(
            Map.entry("ShAgentAPI#read", ""),
            Map.entry("ShRenderAPI#page", ""),
            // The two reads that go through the renderer, on its revision path (SH990's second slice).
            Map.entry("ShAgentAPI#render", ""),
            Map.entry("ShAgentAPI#editable", ""),
            // A listing and a lint take the two rows; a revision is refused with the read that
            // serves one, since it is a version of one page rather than a set (the third slice).
            Map.entry("ShAgentAPI#find", ""),
            Map.entry("ShAgentAPI#verify", ""),

            // The CDA, with a draft or a revision for a preview token only (the last slice).
            Map.entry("ShCdaAPI#list", ""),
            Map.entry("ShCdaAPI#post", ""),
            Map.entry("ShCdaAPI#postByUrl", ""),
            Map.entry("ShCdaAPI#query", ""),
            Map.entry("ShCdaAPI#search", ""),

            Map.entry("ShAgentAPI#manifest", NOT_A_PAGE),
            Map.entry("ShAgentAPI#context", "the pack is the model and a map of addresses, and an "
                    + "address names a post in every version of it; the page itself is a read"),
            Map.entry("ShAgentAPI#resolve", "an address resolves to one post whichever version is "
                    + "read, so a version would change nothing the answer carries"),
            Map.entry("ShAgentAPI#blueprints", NOT_A_PAGE),
            Map.entry("ShAgentAPI#blueprint", NOT_A_PAGE),
            Map.entry("ShAgentAPI#changes", HISTORY),
            // SH1000 — a run is a thing that happened, like the feed above it: it has a start and
            // what it said it was for, and no draft of itself. A `state` here would be asking for
            // the unpublished version of an event.
            Map.entry("ShAgentAPI#runs", HISTORY),
            // SH1002 — a verdict is a thing a curator did, like the feed above it. Asking for the
            // draft version of somebody's decision is asking for the unpublished version of an
            // opinion.
            Map.entry("ShAgentAPI#review", HISTORY),
            Map.entry("ShAgentAPI#memory", NOT_A_PAGE),
            // SH997 — a gate is a rule about publishing, not a version of a page: there is no
            // draft of a checklist, and a `state` here would name nothing. The check reads the
            // DRAFT by construction — it answers what publishing would put live — so a `state`
            // would be the one thing it must not take.
            Map.entry("ShAgentAPI#gates", NOT_A_PAGE),
            Map.entry("ShAgentAPI#checkGates", "it answers about the draft by construction: a gate "
                    + "is asked what THIS publish would put live, and what is live is already past "
                    + "it"),
            Map.entry("ShAgentAPI#diagnostics", HISTORY),
            Map.entry("ShRenderAPI#editorScript", "it serves the editor bridge's script, which is "
                    + "code the preview loads and not a page"),
            Map.entry("ShCdaAPI#sites", NOT_A_PAGE),
            Map.entry("ShCdaAPI#site", NOT_A_PAGE),
            Map.entry("ShCdaAPI#path", "it answers a folder's breadcrumb, and folders are not "
                    + "published or revised"),
            Map.entry("ShCdaAPI#postTypes", NOT_A_PAGE),
            Map.entry("ShCdaAPI#postType", NOT_A_PAGE)));

    /** One read, as the lint sees it. */
    private record Read(String name, boolean accepts) {
    }

    private static List<Read> reads() {
        Map<String, String> sources = ShControllerScan.sources();
        List<Read> found = new ArrayList<>();
        for (String file : CONTENT_READERS) {
            String body = sources.get(file);
            if (body == null) {
                throw new IllegalStateException(file + " is gone — move this lint with it");
            }
            for (ShControllerScan.Mapping mapping : ShControllerScan.mappings(body, "Get")) {
                found.add(new Read(file.replace(".java", "") + "#" + mapping.method(),
                        STATE.matcher(mapping.parameters()).find()
                                || STATE.matcher(mapping.body()).find()));
            }
        }
        return found;
    }

    @Test
    @DisplayName("SH990: every GET a page read goes through says whether it takes a version")
    void everyReadIsClassified() {
        List<Read> reads = reads();
        assertThat(reads)
                .as("SH990: the scan found too few GET mappings, which is how a census goes green "
                        + "without looking")
                .hasSizeGreaterThanOrEqualTo(20);

        assertThat(reads.stream().map(Read::name).toList())
                .as("""
                        SH990: a read either takes `state` or has a reason not to. Classify it here — \
                        accepting with an empty reason, or not with the reason a version would mean \
                        nothing — since a read that cannot see a draft is the gap this counts.""")
                .containsExactlyInAnyOrderElementsOf(DECLARED.keySet());
    }

    @Test
    @DisplayName("SH990: a read that gains or loses `state` fails until its row moves")
    void theListAgreesWithTheTree() {
        List<String> problems = new ArrayList<>();
        for (Read read : reads()) {
            String reason = DECLARED.get(read.name());
            if (reason == null) {
                continue; // the test above owns an unclassified read
            }
            if (reason.isEmpty() && !read.accepts()) {
                problems.add(read.name() + ": declared as taking `state` and its signature does not");
            }
            if (!reason.isEmpty() && read.accepts()) {
                problems.add(read.name() + ": declared as taking no `state` and it now takes one — "
                        + "the row moves, and if it was the remainder, SH990's remainder shrinks");
            }
        }
        assertThat(problems).as("SH990: the reason column is the deliverable").isEmpty();
    }

    /**
     * One grammar. The literal that spells a revision lives in {@code ShContentPerspective} and nowhere
     * else, so a path that starts parsing {@code state} on its own is a finding at the moment it is
     * written rather than the third reading of one value.
     */
    @Test
    @DisplayName("SH990: only ShContentPerspective parses the state grammar")
    void oneGrammar() {
        List<String> parsers = new ArrayList<>();
        ShControllerScan.sources().forEach((file, body) -> {
            if (REVISION_LITERAL.matcher(body).find()) {
                parsers.add(file);
            }
        });
        assertThat(parsers)
                .as("SH990: a second place spelling \"rev:\" is a second parser of `state`; call "
                        + "ShContentPerspective.parse instead")
                .containsExactly("ShContentPerspective.java");
    }

    @Test
    @DisplayName("SH990: the counts print, so the census is read rather than recomputed")
    void theCensusIsTheDeliverable() {
        List<Read> reads = reads();
        long accepting = reads.stream().filter(Read::accepts).count();
        System.out.printf("%n[SH990] content reads: %d of %d take a version%n",
                accepting, reads.size());
        reads.stream().map(read -> (read.accepts() ? "  state     " : "  no state  ") + read.name())
                .sorted().forEach(System.out::println);
        System.out.println();

        assertThat(accepting)
                .as("SH990: no read takes a version, so the grammar this lint holds has been removed "
                        + "rather than the lint")
                .isPositive();
    }
}
