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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.dto.ShAgentEndpoint;
import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.lint.ShControllerScan;
import com.viglet.shio.lint.ShP1Inventory;

/**
 * SH973 — <b>the read half of reachability</b>: every GET-only controller root is findable, or says
 * why not.
 *
 * <h2>The blind spot, which was in both instruments at once</h2>
 *
 * <p>{@code ShStatelessReachabilityLintTest} compiles a pattern for {@code Post}, {@code Put},
 * {@code Delete} and {@code Patch} and returns early for a class that declares none, so a read-only
 * controller yields no root and is never classified. {@code ShAgentCapabilityJoinTest} walks the
 * manifest's declarations rather than the controller inventory, so a GET-only controller nobody
 * declared escapes it too. Between them the product's <em>reads</em> — the audit trail the Activity
 * page renders, the locale list, system information — were an unmeasured surface, and P2 is the law
 * that cares: discovery is a feature, and an endpoint no index names is one an agent finds by
 * reading source.</p>
 *
 * <h2>The rule</h2>
 *
 * <p>A GET-only root is <b>declared</b> — the manifest's endpoint index names it or a path beneath
 * it — or it is classified below with a reason and something that would falsify the claim: an MCP
 * resource uri that must exist, a declared path that must stay declared, or, for a boundary, the
 * sentence that says why an agent has no business reading it. A root that becomes declared while
 * still classified fails the build, which is SH142's rule in the shape the two sibling gates already
 * use.</p>
 *
 * <p>It lives beside the manifest tests rather than beside its sibling because it needs the
 * manifest a running instance reports, which is the one thing a source scan cannot answer.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
class ShAgentReadReachabilityLintTest {

    @Autowired
    private ShAgentManifestService shAgentManifestService;
    @Autowired
    private List<ShMcpResource> mcpResources;

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> EVERY_VERB =
            Set.of("GET", "POST", "PUT", "DELETE", "PATCH");

    /** How a GET-only root is reachable, or why it is not. */
    enum Reach {

        /** A declared agent path answers the same question — the target is that path. */
        AGENT_EQUIVALENT,
        /** An MCP resource carries the same read, named by its uri. */
        MCP_RESOURCE,
        /** Read before the manifest, so the manifest listing it would answer a settled question. */
        ENTRY_POINT,
        /** The console's own surface, which an agent has no use for — the reason says why. */
        CONSOLE_ONLY,
        /** Not for an agent at all: infrastructure, a browser's own route, or delivery to a visitor. */
        NOT_AGENT_FACING,
        /** A real gap: no agent form, nothing declares it, and a task is filed. */
        GAP
    }

    /**
     * @param target   what would falsify this: a declared path or an MCP resource uri that must
     *                 exist on this instance
     * @param closedBy what would close a {@link Reach#GAP} — the task that does it
     */
    record Read(Reach reach, String target, String why, List<String> closedBy) {

        Read(Reach reach, String target, String why) {
            this(reach, target, why, List.of());
        }
    }

    private static Read agentEquivalent(String path, String why) {
        return new Read(Reach.AGENT_EQUIVALENT, path, why);
    }

    private static Read notAgentFacing(String why) {
        return new Read(Reach.NOT_AGENT_FACING, null, why);
    }

    /**
     * Every GET-only controller root the manifest does not declare, and why.
     *
     * <p>Sorted by path, so a diff reads as an addition or a removal rather than a reshuffle — the
     * shape both sibling gates use, for the reason SH142 gives.</p>
     */
    private static Map<String, Read> classified() {
        Map<String, Read> byRoot = new TreeMap<>();

        byRoot.put("/api/v2/events", agentEquivalent("/api/v2/agent/changes",
                "SH967 — one feed, two shapes. A held connection is the wrong shape for a turn: an "
                + "agent has a cursor and spends a turn on it, and the stream exists for a screen "
                + "that is already open"));
        byRoot.put("/api/v2/post/xp", agentEquivalent("/api/v2/agent/read",
                "the id-keyed console read, kept for a caller holding an id from /agent/resolve "
                + "(SH79). Its one addition, `allowPublish`, is the review gate the agent is told "
                + "about when it publishes"));
        byRoot.put("/api/v2/system/info", agentEquivalent("/api/v2/agent/manifest",
                "the instance's version, which the manifest reports beside what that version can "
                + "do — and reporting a version with no capabilities is what P2 calls a second call"));
        byRoot.put("/preview", agentEquivalent("/api/v2/agent/render",
                "the rendered page. /preview is the link a curator opens (SH99) and carries the "
                + "banner and the editor bridge for a browser; /agent/render answers with the "
                + "markup and the digest, which is what a perception loop reads"));

        byRoot.put("/api/v2/discovery", new Read(Reach.ENTRY_POINT, null,
                "the unauthenticated pointer at the manifest, which is what a caller reads first "
                + "(P2). A manifest that declared its own front door would be answering a question "
                + "its reader has already answered"));

        byRoot.put("/api/v2/content", new Read(Reach.CONSOLE_ONLY, null,
                "the console's search, which IS GET /api/v2/agent/find with a per-object permission "
                + "filter "
                + "over it (SH1027). An agent has no use for the filter — a credential is a site "
                + "allow-list, not a person with an ACL — so the read it makes instead is the one "
                + "this composes, and a second declaration would advertise a narrowing as a "
                + "capability"));

        byRoot.put("/api/v2/note/relay", new Read(Reach.CONSOLE_ONLY, null,
                "SH1006 — whether an agent run started recently, asked by the console before it "
                + "offers the dock's chat. An agent asking whether an agent is around has its "
                + "answer already; what it reads instead is shio://requests"));

        byRoot.put("/api/v2/locale", new Read(Reach.CONSOLE_ONLY, null,
                "the language picker's catalogue, labelled in two languages. Nothing validates a "
                + "write against it — post.upsert takes the locale string (SH348) — so an agent "
                + "reading it would learn what a person should be offered, not what it may write"));

        byRoot.put("/api/v2/render/section-preview", new Read(Reach.CONSOLE_ONLY, null,
                "SH1105 — a picture of one section type on this page, for the editor's rail. The "
                + "choice it illustrates is data at /agent/editable?include=insertable, which is "
                + "the read an agent makes, and a picture of placeholder text teaches it nothing"));

        byRoot.put("/", notAgentFacing("the console's index page: HTML for a browser"));
        byRoot.put("/*/console", notAgentFacing("the console shell, per site"));
        byRoot.put("/*/console/**", notAgentFacing("the console shell's own routes, served as HTML"));
        byRoot.put("/api/v2/csrf", notAgentFacing(
                "a token a browser session needs before it may write. The agent's paths are exempt "
                + "for session-less callers (SH339), so an agent that fetched one would be paying "
                + "for a defence against a cross-site form it cannot be the victim of"));
        byRoot.put("/api/v2/ping", notAgentFacing(
                "a liveness probe for whatever started the instance. An agent's first read is "
                + "/api/v2/discovery, which answers 'is it up' and 'what is it' in one call"));
        byRoot.put("/sites", notAgentFacing(
                "delivery: the published page a visitor is served. What an agent reads about a "
                + "site is its content, through /agent/read and the CDA"));
        byRoot.put("/api/v2/preview-share", notAgentFacing(
                "SH1042: where a person who has no account here exchanges a share link for a "
                + "preview cookie. It answers a redirect and a Set-Cookie and nothing an agent "
                + "could use — the agent reads the draft itself through /agent/read, which is "
                + "draft-preferred, and MINTS the link through /api/v2/preview-token, which is "
                + "the reachable half of this feature"));

        // SH1005 closed the last one. It was the audit trail per object: /agent/changes walked the
        // same rows by cursor and could not answer "what happened to this page", which is the
        // question a correction starts from — a curator leaves a note about one page (SH1003), and
        // the run that fixes it wants that page's history and not the instance's. It takes
        // `address=` now, addressed the way every other read on this surface is, and both surfaces
        // read through one query set, so the console cannot grow a narrowing the agent lacks
        // without this classification going stale.
        byRoot.put("/api/v2/history", agentEquivalent("/api/v2/agent/changes",
                "the same trail, addressed: ?address=post:Blog/hello-world is one page's own "
                + "history. The console pages it back from the newest row and an agent walks it "
                + "forward from a cursor, over one reader"));

        return byRoot;
    }

    @Test
    @DisplayName("SH973: every GET-only controller root is declared or classified")
    void everyReadIsDeclaredOrClassified() {
        Map<String, Set<String>> getOnly = getOnlyRoots();
        Set<String> declared = declaredPaths();
        Map<String, Read> classified = classified();

        List<String> unclassified = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        getOnly.keySet().forEach(root -> {
            boolean isDeclared = ShControllerScan.declares(declared, root);
            if (isDeclared && classified.containsKey(root)) {
                stale.add(root + " (" + classified.get(root).reach() + ")");
            }
            if (!isDeclared && !classified.containsKey(root)) {
                unclassified.add(root);
            }
        });

        assertThat(unclassified)
                .as("""
                        SH973: a read an agent cannot find is a P2 break — discovery is a feature, \
                        and an endpoint no index names is one found by reading source. Declare it in \
                        the manifest's endpoint index, or classify it in classified(): MCP_RESOURCE \
                        with the uri that carries the same read, CONSOLE_ONLY with the read an agent \
                        makes instead, NOT_AGENT_FACING for infrastructure, or GAP with the task \
                        that closes it.""")
                .isEmpty();
        assertThat(stale)
                .as("""
                        the manifest declares this root now, so the entry has to go — an inventory \
                        that can rot is worse than none, because it reads as a known gap after the \
                        gap closed (SH142).""")
                .isEmpty();
        assertThat(classified.keySet())
                .as("an entry for a root that no longer exists, or that mutates now, is the same "
                        + "rot from the other side")
                .isSubsetOf(getOnly.keySet());
    }

    @Test
    @DisplayName("SH973: every classification names something that would falsify it")
    void everyClassificationIsFalsifiable() {
        Set<String> resources = mcpResources.stream().map(ShMcpResource::uri)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> declared = declaredPaths();
        List<String> problems = new ArrayList<>();
        classified().forEach((root, read) -> {
            if (read.why() == null || read.why().isBlank()) {
                problems.add(root + ": no reason given");
            }
            switch (read.reach()) {
                case AGENT_EQUIVALENT -> {
                    if (!declared.contains(read.target())) {
                        problems.add(root + ": names '" + read.target()
                                + "', which the manifest's endpoint index does not declare");
                    }
                }
                case MCP_RESOURCE -> {
                    if (!resources.contains(read.target())) {
                        problems.add(root + ": names resource '" + read.target()
                                + "', which this instance does not serve");
                    }
                }
                case GAP -> {
                    if (read.closedBy().isEmpty()) {
                        problems.add(root + ": a gap with nothing named that would close it");
                    }
                }
                default -> {
                    if (read.target() != null) {
                        problems.add(root + ": " + read.reach() + " names a target, which nothing "
                                + "checks — put it in the reason instead");
                    }
                }
            }
        });

        assertThat(problems)
                .as("""
                        a classification nothing can falsify is a sentence, not a claim (SH813's \
                        rule one gate over): an MCP_RESOURCE names a uri this instance must serve, \
                        and a GAP names what would close it.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH973: the inventory is the deliverable, and the run prints it")
    void theInventoryIsTheDeliverable() {
        Map<String, Set<String>> getOnly = getOnlyRoots();
        Set<String> declared = declaredPaths();
        List<String> found = getOnly.keySet().stream()
                .filter(root -> ShControllerScan.declares(declared, root)).toList();

        Map<Reach, List<String>> byReach = new LinkedHashMap<>();
        classified().forEach((root, read) -> byReach
                .computeIfAbsent(read.reach(), ignored -> new ArrayList<>()).add(root));

        System.out.printf("%n[SH973] read reachability — %d GET-only controller roots%n",
                getOnly.size());
        System.out.printf("  %-18s %2d  %s%n", "DECLARED", found.size(), new TreeSet<>(found));
        new TreeMap<>(byReach).forEach((reach, roots) ->
                System.out.printf("  %-18s %2d  %s%n", reach, roots.size(), new TreeSet<>(roots)));
        System.out.println();

        // SH974 — and written down, so a figure in prose can be cited from the run that measured it
        // rather than from whoever last re-derived it.
        Map<String, Object> counts = new TreeMap<>();
        counts.put("getOnlyRoots", getOnly.size());
        counts.put("declared", found.size());
        byReach.forEach((reach, roots) -> counts.put(reach.name(), roots.size()));
        ShP1Inventory.record("ShAgentReadReachabilityLintTest", counts);

        assertThat(getOnly)
                .as("a scan that silently found nothing would pass forever, which is the failure "
                        + "mode every lint here is written against")
                .hasSizeGreaterThanOrEqualTo(5);
        assertThat(byReach.getOrDefault(Reach.GAP, List.of()))
                .as("""
                        the gaps are the deliverable: each is something the product knows and its \
                        own primary operator cannot read over HTTP. SH1005 closed the last one \
                        this instrument held — the audit trail of one page — so the list is empty, \
                        and gaining one must be a reviewable diff naming the task that closes it. \
                        Empty is not the same as vacuous: the count above proves the scan found \
                        roots, and every one of them is classified.""")
                .isEmpty();
    }

    /** Roots that read and never write — the surface neither sibling instrument sees. */
    private static Map<String, Set<String>> getOnlyRoots() {
        Map<String, Set<String>> byRoot = new TreeMap<>();
        ShControllerScan.Scan scan = ShControllerScan.of(EVERY_VERB);
        assertThat(scan.unresolved())
                .as("every controller must contribute a root, or this lint reports fewer paths "
                        + "than exist and passes — add the spelling to ShControllerScan")
                .isEmpty();
        scan.byRoot().forEach((root, verbs) -> {
            if (verbs.contains("GET") && verbs.stream().noneMatch(MUTATING::contains)) {
                byRoot.put(root, verbs);
            }
        });
        return byRoot;
    }

    /** The manifest's endpoint index, as absolute paths. */
    private Set<String> declaredPaths() {
        ShAgentManifest manifest = shAgentManifestService.manifest();
        String base = manifest.apiBase() == null ? "" : manifest.apiBase();
        Set<String> paths = new TreeSet<>();
        for (ShAgentEndpoint endpoint : manifest.endpoints()) {
            String path = endpoint.path();
            paths.add(path.startsWith(base) ? path : base + path);
        }
        return paths;
    }
}
