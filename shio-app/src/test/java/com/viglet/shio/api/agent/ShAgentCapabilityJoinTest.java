/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.ShAgentP1ConformanceTest.Delivery;
import com.viglet.shio.api.agent.mcp.ShMcpPrompt;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

/**
 * SH599 — one capability, three lists, and until now no join.
 *
 * <h2>The gap</h2>
 *
 * <p>A capability is declared in three unrelated places: a boolean in the manifest's
 * {@code features}, a path in its {@code endpoints}, and a tool, resource or prompt in the MCP
 * listing. Nothing joined them. SH596 was one instance — {@code diagnostics} was {@code true} in the
 * first, present in the second and in neither MCP list — and it was found because a run needed it,
 * not because anything checked.</p>
 *
 * <p>{@link ShAgentP1ConformanceTest} walks one direction only: <i>feature → does something deliver
 * it</i>. The other direction had already been noticed and never generalized. SH463's comment says
 * it exactly: "a capability with no flag is not classified at all: site deletion had none, and the
 * one instrument built to find unreachable capabilities was structurally unable to see it." That is
 * this test.</p>
 *
 * <h2>What it does, and what it deliberately does not</h2>
 *
 * <p>{@code features} is the source, per §VIII.33, and the other two are <b>asserted equal</b> to it
 * rather than derived from it — "out of scope: making the derivation clever". So the claimed set is
 * computed from {@link ShAgentP1ConformanceTest#classification()} — the same table, read, never
 * copied, because a second copy is the drift this test exists to catch — and everything the manifest
 * does <em>not</em> claim is declared here with a reason.</p>
 *
 * <p>The consequence is the guard: <b>a new tool, resource, prompt or route is in neither set, and
 * the build fails naming it.</b> Adding a capability then forces the P1 decision at the moment the
 * capability is written, which is the cheapest moment there is.</p>
 *
 * <h2>The finding</h2>
 *
 * <p>Of 50 registry entries, {@code features} claims 16. The other 34 are below, and they are not a
 * backlog: they are what the flag map is <em>for</em> and what it is not. A flag answers "does this
 * instance do X"; most of the surface is the protocol's own machinery — reads, documents, prompts,
 * the CDA — which is not optional and therefore not a flag. Claiming them anyway would be SH596's
 * mistake in the other direction: answering "is this accounted for" with a neighbouring
 * capability.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
class ShAgentCapabilityJoinTest {

    @Autowired
    private ShAgentManifestService shAgentManifestService;
    @Autowired
    private List<ShMcpTool> mcpTools;
    @Autowired
    private List<ShMcpResource> mcpResources;
    @Autowired
    private List<ShMcpPrompt> mcpPrompts;

    // --- why a registry entry carries no feature flag; families, so 34 entries are not 34 essays --

    private static final String PROTOCOL =
            "the protocol's own machinery — not optional, so not a flag. A `features` boolean "
            + "answers 'does this instance do X'; a caller that reached this surface at all has X";
    private static final String MIRROR =
            "an MCP mirror of a document the endpoint index already declares (SH596's category). "
            + "The capability is flagged once, on the document, not once per representation";
    private static final String PROMPT =
            "a prompt is a scripted use of tools that are themselves claimed — flagging it would "
            + "count one capability twice and make `tools/list` look like a capability list";
    private static final String CDA =
            "the delivery surface (Block A), which predates `features` and is not an agent "
            + "capability: it is what the site reads, and an agent reads content through /agent/read";
    private static final String ID_KEYED =
            "kept for a caller holding an id from /agent/resolve (SH79). The capability is the "
            + "addressed write, which `batchWrites` claims; this is the escape hatch, not a feature";
    private static final String SAME_FLAG =
            "the other half of a flag P1 pins to one path. A second claim on the same feature would "
            + "make the flag map read as an endpoint count";
    private static final String BYTES =
            "a byte stream, so it has no JSON-RPC form and SH268's boundary applies. Its flag is "
            + "claimed by the CLI-shaped half, which is where the exception list already records it";

    /**
     * Everything the manifest's flag map does not claim, and why.
     *
     * <p>Asserted exactly, {@link ShAgentP1ConformanceTest}'s shape: an entry that stops being
     * needed fails the build (SH142), and a new one is a reviewable diff with a reason. The reasons
     * are shared families rather than one paragraph each — thirty-four paragraphs would be
     * thirty-four opportunities to write a different rule.</p>
     */
    private static Map<String, String> unclaimed() {
        Map<String, String> byEntry = new TreeMap<>();

        byEntry.put("tool:shio_read", PROTOCOL);
        byEntry.put("tool:shio_publish", PROTOCOL);

        byEntry.put("resource:shio://manifest", MIRROR);
        byEntry.put("resource:shio://context", MIRROR);
        byEntry.put("resource:shio://ops", MIRROR);
        byEntry.put("resource:shio://blueprints", MIRROR);
        byEntry.put("resource:shio://blueprint/{name}", MIRROR);
        byEntry.put("resource:shio://schema/{postType}", MIRROR);
        // SH979 — the console's surface map, which the manifest also serves behind
        // `include=surfaces`. Not a flag: it says what a *curator* can do, and a `features` boolean
        // answers "does this instance do X" about this surface.
        byEntry.put("resource:shio://surfaces", MIRROR);
        // SH1006 — the read half of the notes channel, whose write, note.add, batchWrites claims.
        byEntry.put("resource:shio://requests", SAME_FLAG);
        // SH1215 — every open note, the wider read the same channel's requests are one kind of.
        byEntry.put("resource:shio://notes", SAME_FLAG);


        byEntry.put("prompt:new-site", PROMPT);
        byEntry.put("prompt:add-section", PROMPT);
        byEntry.put("prompt:translate-site", PROMPT);
        byEntry.put("prompt:audit-content", PROMPT);

        byEntry.put("route:/cda/site", CDA);
        byEntry.put("route:/cda/post/{id}", CDA);
        byEntry.put("route:/cda/post-type", CDA);
        byEntry.put("route:/cda/query", CDA);
        byEntry.put("route:/cda/search", CDA);

        byEntry.put("route:/post-unified/{id}", ID_KEYED);
        byEntry.put("route:/staticfile/upload", BYTES);
        byEntry.put("route:/site/{id}/export", BYTES);

        // SH1050 — the instance's own state: counts, what is waiting for a person, what just
        // happened. Not a flag for PROTOCOL's reason and one more: a `features` boolean answers
        // "does this instance do X", and every instance has a number of sites and a review queue.
        // What this read reports is not a capability, it is a fact about right now.
        byEntry.put("route:/console/summary", PROTOCOL);

        byEntry.put("route:/agent/context", PROTOCOL);
        byEntry.put("route:/agent/read", PROTOCOL);
        byEntry.put("route:/agent/find", PROTOCOL);
        byEntry.put("route:/agent/batch", PROTOCOL);
        byEntry.put("route:/agent/plan", PROTOCOL);
        byEntry.put("route:/agent/apply", PROTOCOL);
        byEntry.put("route:/agent/verify", PROTOCOL);
        byEntry.put("route:/agent/render", PROTOCOL);
        byEntry.put("route:/agent/changes", PROTOCOL);
        byEntry.put("route:/agent/memory", PROTOCOL);
        byEntry.put("route:/agent/diagnostics", PROTOCOL);
        byEntry.put("route:/agent/blueprints", PROTOCOL);
        byEntry.put("route:/agent/blueprints/{name}/apply", PROTOCOL);
        byEntry.put("route:/post-type", SAME_FLAG);

        return byEntry;
    }

    // ------------------------------------------------------------------ the join

    /** Every entry of the three registries, in one namespace so the join has one key space. */
    private Set<String> registries() {
        Set<String> entries = new TreeSet<>();
        mcpTools.forEach(tool -> entries.add("tool:" + tool.name()));
        mcpResources.forEach(resource -> entries.add("resource:" + resource.uri()));
        mcpPrompts.forEach(prompt -> entries.add("prompt:" + prompt.name()));
        shAgentManifestService.manifest().endpoints()
                .forEach(endpoint -> entries.add("route:" + endpoint.path()));
        return entries;
    }

    /**
     * What {@code features} claims, keyed the same way — read from P1's table rather than restated.
     *
     * <p>An exception's target is deliberately excluded: it names what <em>would</em> resolve the
     * gap and is asserted absent over there, so treating it as a claim here would have the two
     * tables disagree about whether the capability exists.</p>
     */
    private static Map<String, String> claimed() {
        Map<String, String> byEntry = new TreeMap<>();
        ShAgentP1ConformanceTest.classification().forEach((feature, delivery) -> {
            String key = key(delivery);
            if (key != null) {
                byEntry.merge(key, feature, (first, second) -> first + "+" + second);
            }
        });
        return byEntry;
    }

    private static String key(Delivery delivery) {
        if (delivery.reach().isException() || delivery.target() == null) {
            return null;
        }
        return switch (delivery.reach()) {
            case MCP_TOOL -> "tool:" + delivery.target();
            case MCP_RESOURCE -> "resource:" + delivery.target();
            case AGENT_ROUTE -> "route:" + delivery.target();
            default -> null;
        };
    }

    @Test
    @DisplayName("every registry entry is claimed by a feature or declared unclaimed, with a reason")
    void theThreeRegistriesReconcile() {
        Set<String> registries = registries();
        Map<String, String> claimed = claimed();
        Map<String, String> unclaimed = unclaimed();

        Set<String> accounted = new TreeSet<>(claimed.keySet());
        accounted.addAll(unclaimed.keySet());

        print(registries, claimed, unclaimed);

        List<String> unaccounted = registries.stream()
                .filter(entry -> !accounted.contains(entry))
                .sorted()
                .toList();
        assertThat(unaccounted)
                .as("""
                        SH599: a tool, resource, prompt or route that no feature claims and that \
                        nothing here declares unclaimed is a capability in one registry and absent \
                        from the others — SH596, found in round thirteen because a run needed it. \
                        Claim it with a `features` flag, or say here why it does not carry one. \
                        The decision is cheapest at the moment the capability is written.""")
                .isEmpty();

        List<String> phantom = accounted.stream()
                .filter(entry -> !registries.contains(entry))
                .sorted()
                .toList();
        assertThat(phantom)
                .as("""
                        SH142's rule, the other way: an entry accounted for here that no registry \
                        holds is a claim about a capability that is gone, and it reads as coverage. \
                        A P1 classification pointing at a deleted tool fails here rather than in \
                        the round that goes looking for it.""")
                .isEmpty();

        assertThat(claimed.keySet())
                .as("a capability cannot be both claimed by a flag and declared flagless")
                .doesNotContainAnyElementsOf(unclaimed.keySet());
        assertThat(unclaimed.values())
                .as("a reason is what makes an unclaimed entry reviewable rather than a suppression")
                .allSatisfy(why -> assertThat(why).isNotBlank());
    }

    @Test
    @DisplayName("the claimed side names real features, and the join is not vacuous")
    void theJoinCanFail() {
        Map<String, Boolean> features = shAgentManifestService.manifest().features();

        List<String> unknown = new ArrayList<>();
        claimed().values().forEach(claimants -> {
            for (String feature : claimants.split("\\+")) {
                if (!features.containsKey(feature)) {
                    unknown.add(feature);
                }
            }
        });
        assertThat(unknown)
                .as("SH599: a claim naming a feature the manifest does not report is the join "
                        + "broken at the source — renaming a flag must not leave a registry entry "
                        + "pointing at nothing")
                .isEmpty();

        assertThat(registries())
                .as("an empty registry set would make the whole join pass by accident, which is "
                        + "the lint-that-reports-zero-forever failure")
                .hasSizeGreaterThanOrEqualTo(45);
        assertThat(claimed())
                .as("and an empty claimed set would report the entire surface as flagless, which "
                        + "would make the inventory below meaningless rather than wrong")
                .hasSizeGreaterThanOrEqualTo(10);
    }

    /** The join, printed — which flag covers what, and the half of the surface no flag covers. */
    private void print(Set<String> registries, Map<String, String> claimed,
                       Map<String, String> unclaimed) {
        Map<String, Integer> byKind = new TreeMap<>();
        registries.forEach(entry ->
                byKind.merge(entry.substring(0, entry.indexOf(':')), 1, Integer::sum));

        System.out.printf("%n[SH599] capability join — %d registry entries %s%n",
                registries.size(), byKind);
        System.out.printf("  claimed by a feature: %d%n", claimed.size());
        new TreeMap<>(claimed).forEach((entry, feature) ->
                System.out.printf("    %-38s %s%n", entry, feature));
        System.out.printf("  no flag, by design: %d%n", unclaimed.size());
        new TreeMap<>(unclaimed).forEach((entry, why) ->
                System.out.printf("    %-38s %s%n", entry, why.substring(0,
                        Math.min(60, why.length()))));
        System.out.println();
    }
}
