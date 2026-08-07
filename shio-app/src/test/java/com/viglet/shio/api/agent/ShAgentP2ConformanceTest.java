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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.agent.mcp.ShMcpPrompt;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.api.agent.mcp.ShMcpServer;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH598 — P2's grep. Three breaks were found by hand; this finds the rest.
 *
 * <h2>Why this exists</h2>
 *
 * <p>SH338 states the rule: when a review finds a design law broken, the instrument that finds
 * every <em>other</em> break lands before the fixes. P1 obeyed it —
 * {@link ShAgentP1ConformanceTest} classifies every manifest {@code features} key and holds the
 * unreachable ones as an asserted list, naming four that no task had.</p>
 *
 * <p>P2 did the opposite. Three breaks were found one per round and each fixed alone: a capability
 * named by four documents and no command (SH587), an onboarding command that never read the
 * manifest (SH595), a feature reachable by REST, CLI and console and by neither MCP list (SH596).
 * So the honest count of P2 breaks before this class is <b>three that somebody happened to
 * notice</b>.</p>
 *
 * <h2>The measure, and why it needs no agent</h2>
 *
 * <p>Discoverability — <i>named in a response already received</i> — is graph reachability. Seed the
 * known set with an entry point's response; every name in it that is itself a <b>door</b> (a
 * document that can be fetched with what is now known) is opened, and its body's names join the
 * set; repeat to a fixed point. A capability is named at some depth or it is not named at all.
 * No model, no tokens, no judgement.</p>
 *
 * <p>Two entry points are walked, because P2 is a claim about <em>a</em> cold start and the product
 * has two: {@code GET /agent/context} for a caller with HTTP, and {@code initialize} for a client
 * whose only surface is MCP. SH596 is exactly the break that only the second walk can see, and
 * SH595 exactly the one that only a walk — rather than an inventory — can see at all: the endpoint
 * existed, the manifest named it, and the path from the entry point to it did not close.</p>
 *
 * <h2>What is asserted</h2>
 *
 * <p>The population is every concrete thing an agent must be able to <em>call</em>: MCP tools, MCP
 * resources, the manifest's declared routes, the batch op vocabulary, and the {@code elsewhere}
 * pointers to what lives outside this surface. Each walk reports the depth at which each was first
 * named, and the not-named set is asserted <b>exactly</b>, P1's shape rather than a ceiling: closing
 * one break cannot quietly open another, and gaining an entry is a reviewable diff with a reason.</p>
 *
 * <p>The depth is also the explanation for SH597's numbers. That benchmark says what the path costs;
 * this says how long it is, and which capability is at the far end of it.</p>
 *
 * <h2>What it found, stated plainly</h2>
 *
 * <p><b>Nothing, and the empty list is the result rather than a formality.</b> All 71 targets are
 * named from both entry points — SH587, SH595 and SH596 closed the three breaks anybody had seen,
 * and the walk says there is no fourth of that kind. Manufacturing entries to make the deliverable
 * look like P1's would be the one dishonest thing this class could do.</p>
 *
 * <p>What it did find is in the <b>depth</b> column, and it is the same shape SH597 prices:</p>
 *
 * <ul>
 *   <li><b>From HTTP the whole MCP surface is at depth 3</b> — pack names manifest, manifest names
 *       {@code POST /mcp}, and only then is there a list to read. Eleven of the seventy-one.</li>
 *   <li><b>From MCP the whole HTTP index is at depth 2</b>, through {@code shio://manifest}, and so
 *       is every {@code elsewhere} pointer.</li>
 *   <li><b>The op vocabulary is at depth 2 from HTTP</b>, because {@code include=ops} is a
 *       projection: the caller has been reading the endpoint that carries it since depth 0 and does
 *       not learn the parameter until the manifest arrives (SH346).</li>
 * </ul>
 *
 * <p>So the maximum depth is asserted alongside the gap list. A capability can stay findable and
 * still get further away, and a list-only check would call that no change.</p>
 *
 * <p>One finding was about the walk itself and is left in the code deliberately (see the
 * {@code resources/templates/list} door): modelling the protocol incompletely reported the
 * product's gaps and the instrument's in one list, which is the failure mode a reachability check
 * has that an inventory does not.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
/*
 * Its own database, and empty. Discovery is a property of the surface, not of the content — a walk
 * over the shared datasource would read whatever other tests created into the context pack, and the
 * question here is never "how many sites are there" but "does anything name this call".
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-p2;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentP2ConformanceTest {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShAgentManifestService shAgentManifestService;
    @Autowired
    private ShMcpServer shMcpServer;
    @Autowired
    private List<ShMcpTool> mcpTools;
    @Autowired
    private List<ShMcpResource> mcpResources;
    @Autowired
    private List<ShMcpPrompt> mcpPrompts;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    // ------------------------------------------------------------------ the graph

    /**
     * A document reachable once its address is known.
     *
     * @param namedBy the string that has to appear in something already received before this may be
     *                opened — the edge, and the whole reason this is a walk rather than a list. A
     *                door nothing names is a document nobody can find, however good it is
     */
    private record Door(String namedBy, String label, Supplier<String> open) {
    }

    /**
     * Everything an agent has to be able to name before it can call it.
     *
     * @param kind for the printed report — which surface the item belongs to
     */
    private record Target(String kind, String name) implements Comparable<Target> {

        @Override
        public int compareTo(Target other) {
            int byKind = kind.compareTo(other.kind);
            return byKind != 0 ? byKind : name.compareTo(other.name);
        }

        @Override
        public String toString() {
            return kind + ":" + name;
        }
    }

    private List<Door> doors() {
        return List.of(
                // --- HTTP: the routes a caller can open with nothing but the address ------------
                new Door("/api/v2/agent/manifest", "GET /agent/manifest",
                        () -> http(get("/api/v2/agent/manifest").param("format", "terse"))),
                new Door("/api/v2/agent/blueprints", "GET /agent/blueprints",
                        () -> http(get("/api/v2/agent/blueprints").param("format", "terse"))),
                new Door("/api/v2/agent/diagnostics", "GET /agent/diagnostics",
                        () -> http(get("/api/v2/agent/diagnostics").param("format", "terse"))),
                new Door("/api/v2/agent/memory", "GET /agent/memory",
                        () -> http(get("/api/v2/agent/memory"))),
                new Door("/api/v2/agent/changes", "GET /agent/changes",
                        () -> http(get("/api/v2/agent/changes").param("format", "terse"))),
                // The op vocabulary is a *projection*, so the door is the parameter and not the
                // route: a caller that never learns `include=ops` exists never receives it, even
                // though it has been reading the endpoint that carries it since depth 0 (SH346).
                new Door("include=ops", "GET /agent/context?include=ops",
                        () -> http(get("/api/v2/agent/context")
                                .param("include", "ops").param("format", "terse"))),

                // --- MCP: the protocol's own three lists ---------------------------------------
                // Named by `capabilities` in the initialize result, which is how the protocol
                // itself says a list exists — the honest edge for a client with no HTTP.
                new Door("\"tools\"", "mcp tools/list", () -> mcp("tools/list")),
                new Door("\"resources\"", "mcp resources/list", () -> mcp("resources/list")),
                // Both lists, because `capabilities.resources` announces both and a parameterized
                // read appears on neither the other. Leaving this door shut was the walk's own
                // first finding, and it was a finding about the walk: `shio://blueprint/{name}`
                // came back unreachable from an entry point that had already been told where to
                // look. A reachability check that models the protocol incompletely reports the
                // product's gaps and its own in the same list.
                new Door("\"resources\"", "mcp resources/templates/list",
                        () -> mcp("resources/templates/list")),
                new Door("\"prompts\"", "mcp prompts/list", () -> mcp("prompts/list")),
                new Door("/mcp", "mcp initialize", () -> mcp("initialize")));
    }

    /** One resource read per registered URI, opened only where the URI has actually been named. */
    private List<Door> resourceDoors() {
        return mcpResources.stream()
                .map(resource -> new Door(resource.uri(), "mcp resources/read " + resource.uri(),
                        () -> mcpRead(resource.uri())))
                .toList();
    }

    /**
     * Walks from one seed to a fixed point.
     *
     * @return every target, mapped to the depth at which it was first named; absent = never named
     */
    private Map<Target, Integer> walk(String seedLabel, Supplier<String> seed) {
        List<Door> doors = new ArrayList<>(doors());
        doors.addAll(resourceDoors());

        Map<Target, Integer> depths = new TreeMap<>();
        Set<String> opened = new LinkedHashSet<>();
        opened.add(seedLabel);
        StringBuilder known = new StringBuilder(seed.get());
        record(depths, known.toString(), 0);

        for (int depth = 1; depth <= doors.size(); depth++) {
            List<Door> next = doors.stream()
                    .filter(door -> !opened.contains(door.label()))
                    .filter(door -> known.toString().contains(door.namedBy()))
                    .toList();
            if (next.isEmpty()) {
                break;
            }
            for (Door door : next) {
                opened.add(door.label());
                known.append('\n').append(door.open().get());
            }
            record(depths, known.toString(), depth);
        }
        return depths;
    }

    /** First naming wins: a target already recorded keeps the shallower depth. */
    private void record(Map<Target, Integer> depths, String known, int depth) {
        for (Target target : population()) {
            if (!depths.containsKey(target) && known.contains(target.name())) {
                depths.put(target, depth);
            }
        }
    }

    /**
     * The population. Every entry is a call an agent makes, taken from the registries rather than
     * listed — a hand-kept population would go stale exactly where a new capability arrives, which
     * is the moment this check is for.
     */
    private Set<Target> population() {
        ShAgentManifest manifest = shAgentManifestService.manifest();
        Set<Target> targets = new TreeSet<>();
        mcpTools.forEach(tool -> targets.add(new Target("tool", tool.name())));
        mcpResources.forEach(resource -> targets.add(new Target("resource", resource.uri())));
        mcpPrompts.forEach(prompt -> targets.add(new Target("prompt", prompt.name())));
        manifest.endpoints().forEach(endpoint -> targets.add(
                new Target("route", endpoint.path())));
        ShAgentBatchService.OPS.forEach(op -> targets.add(new Target("op", op)));
        manifest.elsewhere().values().forEach(where -> targets.add(new Target("elsewhere", where)));
        return targets;
    }

    // ------------------------------------------------------------------ the assertions

    @Test
    @DisplayName("everything an agent must call is named on the walk from GET /agent/context")
    void theHttpWalkNamesEverythingOrTheGapIsDeclared() {
        Map<Target, Integer> depths = walk("seed",
                () -> http(get("/api/v2/agent/context").param("format", "terse")));
        print("http — GET /agent/context?format=terse", depths);

        assertThat(notNamed(depths))
                .as("""
                        SH598: P2 says one call replaces a session, so a call an agent has to make \
                        and that no response ever names is the law broken — the capability exists, \
                        and finding it needs somebody to say so out of band. This is asserted as a \
                        list and not a count (P1's shape): closing one gap must not silently fund \
                        another, and gaining an entry is a diff with a reason attached.""")
                .isEmpty();
        assertThat(deepest(depths))
                .as("""
                        SH598: an empty gap list is not the whole claim, because a capability can \
                        stay findable while becoming further away — and P2 is about the length of \
                        the path, not only its existence. The MCP surface sits at the bottom of \
                        this walk (depth %d): the pack names the manifest, the manifest names \
                        POST /mcp, and only then does a tool list exist to read. That is what \
                        SH597 prices; a fourth door would make it worse without failing anything \
                        here unless this line exists.""".formatted(HTTP_MAX_DEPTH))
                .isLessThanOrEqualTo(HTTP_MAX_DEPTH);
    }

    @Test
    @DisplayName("everything an agent must call is named on the walk from MCP initialize")
    void theMcpWalkNamesEverythingOrTheGapIsDeclared() {
        Map<Target, Integer> depths = walk("seed", () -> mcp("initialize"));
        print("mcp — initialize", depths);

        assertThat(notNamed(depths))
                .as("""
                        SH598: the walk SH596 would have failed. An MCP-only client has no HTTP of \
                        its own, so `initialize` is its entire cold start and everything else has \
                        to be named from there. A capability reachable over REST, the CLI and the \
                        console and named on neither MCP list is invisible to the one operator P1 \
                        puts first.""")
                .isEmpty();
        assertThat(deepest(depths))
                .as("""
                        SH598: the MCP side is the shorter walk, and that is the right way round — \
                        `initialize` announces three lists, the lists carry every tool, resource, \
                        prompt and op, and `shio://manifest` carries the whole HTTP index at depth \
                        %d. It is also the side with the least slack: there is no third document \
                        to hide a new capability behind, so a gap here shows up as an unnamed \
                        target rather than as a longer path.""".formatted(MCP_MAX_DEPTH))
                .isLessThanOrEqualTo(MCP_MAX_DEPTH);
    }

    /**
     * How far the deepest capability sits from the entry point.
     *
     * <p>Measured 2026.3: <b>3</b> from HTTP and <b>2</b> from MCP, with all 71 targets named from
     * both. These are exact rather than generous — a door is a round trip, so one more is a protocol
     * change and not drift, which is the same argument the SH112 call ceilings are exact for.</p>
     */
    private static final int HTTP_MAX_DEPTH = 3;
    private static final int MCP_MAX_DEPTH = 2;

    private static int deepest(Map<Target, Integer> depths) {
        return depths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    @Test
    @DisplayName("the walk is not vacuous: it opens doors, and a door nothing names stays shut")
    void theWalkCanFail() {
        Map<Target, Integer> depths = walk("seed",
                () -> http(get("/api/v2/agent/context").param("format", "terse")));

        assertThat(population())
                .as("an empty population would make both walks pass by accident, which is the "
                        + "lint-that-reports-zero-forever failure ShAgentP1ConformanceTest names")
                .hasSizeGreaterThanOrEqualTo(40);
        assertThat(deepest(depths))
                .as("a walk that never opened a door would report everything at depth 0, which "
                        + "would mean the seed already carried the whole surface — it does not, "
                        + "and if it ever did this check is how that would be noticed")
                .isGreaterThanOrEqualTo(1);
        assertThat(http(get("/api/v2/agent/manifest").param("format", "terse")))
                .as("a name the surface does not carry must not be found, or `contains` would "
                        + "report every target named and the walk would prove nothing")
                .doesNotContain("/agent/invented");
    }

    private List<String> notNamed(Map<Target, Integer> depths) {
        return population().stream()
                .filter(target -> !depths.containsKey(target))
                .map(Target::toString)
                .sorted()
                .toList();
    }

    /** The report — the depth column is what SH597's token numbers are the price of. */
    private void print(String entryPoint, Map<Target, Integer> depths) {
        Map<Integer, List<String>> byDepth = new TreeMap<>();
        depths.forEach((target, depth) -> byDepth
                .computeIfAbsent(depth, ignored -> new ArrayList<>()).add(target.toString()));
        Map<String, Integer> byKind = new LinkedHashMap<>();
        depths.keySet().forEach(target ->
                byKind.merge(target.kind(), 1, Integer::sum));

        System.out.printf("%n[SH598] P2 reachability from %s — %d of %d named%n",
                entryPoint, depths.size(), population().size());
        byDepth.forEach((depth, names) ->
                System.out.printf("  depth %d  %2d  %s%n", depth, names.size(), names));
        System.out.printf("  by kind: %s%n", new TreeMap<>(byKind));
        System.out.printf("  NOT NAMED: %s%n%n", notNamed(depths));
    }

    // ------------------------------------------------------------------ the two clients

    private String http(MockHttpServletRequestBuilder request) {
        try {
            return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException("discovery walk could not read a door", e);
        }
    }

    private String mcp(String method) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", 1);
        message.put("method", method);
        return shMcpServer.handle(message).toString();
    }

    private String mcpRead(String uri) {
        ObjectNode message = objectMapper.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.put("id", 1);
        message.put("method", "resources/read");
        message.putObject("params").put("uri", uri);
        return shMcpServer.handle(message).toString();
    }
}
