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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.dto.ShAgentEndpoint;
import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.agent.dto.ShConsoleSurface;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH978 — <b>every console surface says how an agent reaches the same capability.</b>
 *
 * <h2>The join that did not exist</h2>
 *
 * <p>The console's map is {@code bento-nav.ts}, and its own javadoc says the rail and the palette
 * both need it because "declared twice they disagree within a release". The manifest and
 * {@code tools/list} were not among its readers, and nothing in {@code shio-app} could read it at
 * all — it is TypeScript. {@code ShAgentP1ConformanceTest} is keyed on manifest {@code features},
 * and SH463 already recorded what that means: a capability with no flag is not classified. So a
 * round could add console surface after console surface, every instrument green, while P1 — the law
 * that a console-only feature is incomplete — was broken once per surface.</p>
 *
 * <h2>What this holds</h2>
 *
 * <p>{@code console/surfaces.json} is the backend's half of that map: an id, a section, a route, the
 * capability, and either an <b>agent equivalent</b> — a tool, a batch op or a declared endpoint,
 * each resolved here against the running server — or an <b>exception</b> with a reason and a
 * {@code closedBy} naming what would make it stale. The id set is asserted equal to the console's
 * own, read from {@code bento-nav.ts} as text, which is what makes the declaration impossible to
 * leave behind: a surface added to one side and not the other fails the build.</p>
 *
 * <p>The exception vocabulary here is the smallest today's surfaces need; SH980 is where it is
 * defined properly, and this test is what will hold it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
class ShConsoleSurfaceP1Test {

    private static final String SURFACES = "/console/surfaces.json";

    /** The console's own list, which is TypeScript and stays TypeScript (SH888). */
    private static final Path NAV = Path.of("..", "shio-react", "src", "app", "system",
            "bento-nav.ts");

    /**
     * A decorated surface: the row that carries a {@code titleKey} — a section carries none.
     *
     * <p>SH979 turned the console's array into a table keyed by the declaration's id, which is the
     * join working: the console now says what it <em>adds</em> to each surface rather than which
     * surfaces exist. This still reads the console's side, so a surface the console decorates and
     * nobody declared — or the other way round — is still the failure it was.</p>
     */
    private static final Pattern NAV_ITEM = Pattern.compile(
            "\"([\\w-]+)\":\\s*\\{[^\\n]*titleKey:");

    @Autowired
    private ShAgentManifestService shAgentManifestService;
    @Autowired
    private List<ShMcpTool> mcpTools;
    @Autowired
    private List<ShMcpResource> mcpResources;
    @Autowired
    private ShConsoleSurfaces shConsoleSurfaces;
    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode surfaces() {
        try (InputStream in = ShConsoleSurfaceP1Test.class.getResourceAsStream(SURFACES)) {
            assertThat(in).as("missing %s — the console's half of the map has no backend half",
                    SURFACES).isNotNull();
            return objectMapper.readTree(in).get("surfaces");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The ids the console renders, read from its own declaration. */
    private static Set<String> consoleIds() {
        Set<String> ids = new TreeSet<>();
        Matcher matcher = NAV_ITEM.matcher(read(NAV));
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath()
                    + " — this test joins the console's map to the agent surface, so it has to be "
                    + "able to read it", e);
        }
    }

    @Test
    @DisplayName("SH978: the declaration names exactly the surfaces the console renders")
    void everyConsoleSurfaceIsDeclared() {
        Set<String> declared = new TreeSet<>();
        surfaces().forEach(surface -> declared.add(surface.get("id").asString()));
        Set<String> rendered = consoleIds();

        assertThat(rendered)
                .as("SH978: bento-nav.ts is the console's map and this scan no longer finds items "
                        + "in it — the join is off, and a green run would mean nothing")
                .hasSizeGreaterThanOrEqualTo(10);
        assertThat(declared)
                .as("""
                        SH978: a surface on one side and not the other is the state this task \
                        exists to make impossible. A console page with no entry here is a \
                        capability nobody asked the P1 question about; an entry with no page is a \
                        claim about a surface that is gone (SH142).""")
                .containsExactlyInAnyOrderElementsOf(rendered);
    }

    @Test
    @DisplayName("SH978: every surface names an agent equivalent that exists, or a classified exception")
    void everySurfaceIsReachableOrClassified() {
        Set<String> tools = new TreeSet<>();
        mcpTools.forEach(tool -> tools.add(tool.name()));
        // SH1215 — a resource is an agent equivalent too: it is how a read made once per session
        // reaches an agent without a manifest row or a tools/list line, and every one carries the
        // HTTP twin P1 asks for (SH346).
        Set<String> resources = new TreeSet<>();
        mcpResources.forEach(resource -> resources.add(resource.uri()));
        Set<String> endpoints = new TreeSet<>();
        for (ShAgentEndpoint endpoint : shAgentManifestService.manifest().endpoints()) {
            endpoints.add(endpoint.path());
        }

        List<String> problems = new ArrayList<>();
        surfaces().forEach(surface -> {
            String id = surface.get("id").asString();
            for (String field : List.of("section", "route", "capability")) {
                if (surface.path(field).asString("").isBlank()) {
                    problems.add(id + ": no " + field);
                }
            }
            JsonNode agent = surface.get("agent");
            JsonNode exception = surface.get("exception");
            if ((agent == null) == (exception == null)) {
                problems.add(id + ": names " + (agent == null ? "neither an agent equivalent nor an "
                        + "exception — the P1 question has to be answered one way or the other"
                        : "both an agent equivalent and an exception, which cannot both be true"));
                return;
            }
            if (agent != null) {
                String kind = agent.path("kind").asString("");
                String target = agent.path("target").asString("");
                boolean exists = switch (kind) {
                    case "tool" -> tools.contains(target);
                    case "op" -> ShAgentBatchService.OPS.contains(target);
                    case "resource" -> resources.contains(target);
                    case "endpoint" -> endpoints.contains(target);
                    default -> false;
                };
                if (!exists) {
                    problems.add("%s: names %s '%s', which this server does not serve"
                            .formatted(id, kind.isBlank() ? "an unknown kind of equivalent" : kind,
                                    target));
                }
                return;
            }
            if (exception.path("reason").asString("").isBlank()
                    || exception.path("why").asString("").isBlank()) {
                problems.add(id + ": an exception with no reason is a sentence, not a claim");
            }
        });

        assertThat(problems)
                .as("""
                        SH978: P1 says a console-only feature is incomplete, and until now nothing \
                        could ask that question of a console page at all. Point the surface at the \
                        tool, op or declared endpoint that does the same thing — or classify it, \
                        with what would make the classification stale.""")
                .isEmpty();
    }

    /**
     * The words an exception may use, and what refuses one that uses another (SH980).
     *
     * <p>Written as a method over a supplied list rather than over the file, so the planted case
     * below can be run through the same rule: a check that can only be exercised by editing the
     * document it guards is a check nobody exercises.</p>
     */
    private List<String> classificationProblems(List<ShConsoleSurface> supplied) {
        Map<String, ShConsoleSurface.Reason> reasons = shConsoleSurfaces.reasons();
        List<String> problems = new ArrayList<>();
        supplied.forEach(surface -> {
            ShConsoleSurface.Exception exception = surface.exception();
            if (exception == null) {
                return;
            }
            String reason = exception.reason() == null ? "" : exception.reason();
            if (!reasons.containsKey(reason)) {
                problems.add("%s: `%s` is not one of the declared reasons %s — a word this file "
                        .formatted(surface.id(), reason, new TreeSet<>(reasons.keySet()))
                        + "does not define is an argument in prose, which is what nothing can read");
            }
            if (exception.why() == null || exception.why().isBlank()) {
                problems.add(surface.id() + ": a classification with no argument is a label");
            }
            boolean gap = "gap".equals(reason);
            boolean closed = exception.closedBy() != null && !exception.closedBy().isBlank();
            if (gap != closed) {
                problems.add(surface.id() + (gap
                        ? ": a gap with nothing named that would close it"
                        : ": names a task in closedBy and is not a gap — every other reason is"
                                + " falsified by its own kind, which the reasons map states once"));
            }
        });
        return problems;
    }

    @Test
    @DisplayName("SH980: every exception uses a word the declaration defines, and every word is used")
    void theExceptionVocabularyIsClosedAndLive() {
        assertThat(classificationProblems(shConsoleSurfaces.surfaces()))
                .as("""
                        SH980: a console-only surface used to argue its P1 position in a paragraph,                         which this gate cannot read — so either it landed red for a whole round or                         each task invented its own wording. The words are declared once, in the                         document all four readers hold.""")
                .isEmpty();

        Map<String, ShConsoleSurface.Reason> reasons = shConsoleSurfaces.reasons();
        assertThat(reasons.values())
                .as("SH980: a word with no `means` claims nothing, and one with no `falsifier` is a "
                        + "place to put things rather than a classification")
                .allSatisfy(reason -> {
                    assertThat(reason.means()).isNotBlank();
                    assertThat(reason.falsifier()).isNotBlank();
                });

        Set<String> used = new TreeSet<>();
        shConsoleSurfaces.surfaces().stream().map(ShConsoleSurface::exception)
                .filter(java.util.Objects::nonNull)
                .forEach(exception -> used.add(exception.reason()));
        shConsoleSurfaces.consoleOnly().forEach(entry -> used.add(entry.kind()));

        assertThat(used)
                .as("SH980: a word nobody uses is a word nobody agreed on, and it is how a "
                        + "vocabulary becomes a list of places to file things")
                .containsExactlyInAnyOrderElementsOf(new TreeSet<>(reasons.keySet()));
    }

    @Test
    @DisplayName("SH980: a surface with a paragraph and no kind is refused")
    void anUnclassifiedSurfaceIsRefused() {
        // The planted case, run through the rule the file is checked with. Without this the rule
        // could only be exercised by breaking the document, which is a test nobody runs.
        ShConsoleSurface planted = new ShConsoleSurface("planted", "content", "/bento/planted",
                "something a curator does", null,
                new ShConsoleSurface.Exception("it is really more of a console thing",
                        "a paragraph arguing the position, which is what this replaces", ""));

        assertThat(classificationProblems(List.of(planted)))
                .as("SH980: the gate has to refuse a reason it does not define, or the vocabulary is "
                        + "a suggestion and the next console-only task writes its own")
                .isNotEmpty()
                .anySatisfy(problem -> assertThat(problem).contains("not one of the declared reasons"));
    }

    @Test
    @DisplayName("SH980: what the console does with no agent leg is classified, not argued")
    void everyConsoleOnlyEntryIsClassified() {
        Map<String, ShConsoleSurface.Reason> reasons = shConsoleSurfaces.reasons();
        List<String> problems = new ArrayList<>();
        shConsoleSurfaces.consoleOnly().forEach(entry -> {
            if (!reasons.containsKey(entry.kind())) {
                problems.add(entry.id() + ": `" + entry.kind() + "` is not a declared reason");
            }
            if (entry.why() == null || entry.why().isBlank()) {
                problems.add(entry.id() + ": classified with no argument");
            }
        });

        assertThat(problems)
                .as("""
                        SH980: the shell, the cache, the keyboard affordances and a curator's own                         preferences have no agent leg by nature. Each is one classified entry here,                         so a later task adds a line instead of re-deriving the argument — which is                         the drift between two authorities agents.md already names.""")
                .isEmpty();
        assertThat(shConsoleSurfaces.consoleOnly())
                .as("SH980: seeded with what exists today, so the gate is green on the day it lands "
                        + "and a new entry is a reviewable diff")
                .isNotEmpty();
    }

    @Test
    @DisplayName("SH979: the manifest serves the surfaces only when asked, and is otherwise unchanged")
    void theManifestServesThemBehindAnInclude() {
        ShAgentManifest ordinary = shAgentManifestService.manifest(
                ShAgentManifestSection.parse(null));
        assertThat(ordinary.surfaces())
                .as("""
                        SH979: an ordinary manifest read is the first read of every session and \
                        the one an ETag makes free. Seventeen rows about the *console* on every one \
                        of them is the cost `include=` exists to remove, so this section is opted \
                        into by name — `all` included.""")
                .isNull();

        ShAgentManifest asked = shAgentManifestService.manifest(
                ShAgentManifestSection.parse("surfaces"));
        assertThat(asked.surfaces())
                .as("SH979: and a caller that names it gets the same list the console renders")
                .hasSameSizeAs(surfacesList())
                .extracting(ShConsoleSurface::id)
                .containsExactlyElementsOf(surfacesList().stream().map(ShConsoleSurface::id).toList());
    }

    @Test
    @DisplayName("SH979: the MCP resource and the console read one declaration, in one order")
    void theResourceAndTheConsoleAgree() throws java.io.IOException {
        ShMcpResource resource = mcpResources.stream()
                .filter(each -> "shio://surfaces".equals(each.uri()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "SH979: shio://surfaces is gone, so MCP has stopped being a reader"));

        List<String> published = new ArrayList<>();
        for (JsonNode surface : objectMapper.readTree(resource.read("shio://surfaces"))) {
            published.add(surface.get("id").asString());
        }

        assertThat(published)
                .as("""
                        SH979: four readers of one declaration, and the order is part of it — the \
                        rail's order is what a curator already knows, so a resource that sorted \
                        its own way would make two lists out of one.""")
                .containsExactlyElementsOf(surfacesList().stream().map(ShConsoleSurface::id).toList());
        assertThat(resource.httpTwin())
                .as("SH979: a resource with no HTTP twin is a P1 break (SH346), and this one's twin "
                        + "is the manifest include")
                .isEqualTo("/api/v2/agent/manifest?include=surfaces");
    }

    /** The declaration as the backend reads it — the same bean every other reader asks. */
    private List<ShConsoleSurface> surfacesList() {
        return shConsoleSurfaces.surfaces();
    }

    @Test
    @DisplayName("SH981: the console link is built from the declaration, and points at nothing it cannot")
    void theHandoffIsALink() {
        assertThat(shConsoleSurfaces.deepLinks())
                .as("SH981: an agent's closing move was a sentence — the batch detail said 'change "
                        + "it in the console' and named no URL. The templates are what replaced it")
                .containsKeys("post", "folder", "site");

        assertThat(shConsoleSurfaces.curatorUrl("post", "7f1c"))
                .as("SH981: built from the declared route rather than concatenated in Java, which "
                        + "is what survives the next time the console's prefix moves")
                .isEqualTo("/bento/content/post/7f1c");
        assertThat(shConsoleSurfaces.curatorUrl("post", null))
                .as("SH981: no id, no link — a caller handed a URL with a hole in it would open a "
                        + "page that does not exist")
                .isNull();
        assertThat(shConsoleSurfaces.curatorUrl("nothing-like-this", "7f1c"))
                .as("SH981: absent rather than invented where this console has no page for a kind")
                .isNull();
    }

    @Test
    @DisplayName("SH978: the inventory is the deliverable, and the run prints it")
    void theInventoryIsTheDeliverable() {
        Map<String, List<String>> byAnswer = new LinkedHashMap<>();
        surfaces().forEach(surface -> {
            String id = surface.get("id").asString();
            JsonNode agent = surface.get("agent");
            String answer = agent != null
                    ? agent.path("kind").asString("?")
                    : "exception:" + surface.path("exception").path("reason").asString("?");
            byAnswer.computeIfAbsent(answer, ignored -> new ArrayList<>()).add(id);
        });

        System.out.printf("%n[SH978] console surfaces, P1-answered — %d surfaces%n", surfaces().size());
        new TreeMap<>(byAnswer).forEach((answer, ids) ->
                System.out.printf("  %-22s %2d  %s%n", answer, ids.size(), new TreeSet<>(ids)));
        System.out.println();

        List<String> gaps = byAnswer.getOrDefault("exception:gap", List.of());
        assertThat(gaps)
                .as("""
                        the gaps are the deliverable: each is a capability a curator has and the \
                        product's primary operator does not. Closing one is an edit to this file; \
                        gaining one must be a reviewable diff naming the task that closes it.""")
                // SH1214 gained "notes" and SH1215 closed it with shio://notes, whose twin is the
                // GET /note/open the console's page reads.
                .containsExactly("activity");
    }
}
