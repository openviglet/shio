/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.viglet.shio.marketplace.ShMarketplaceProperties;
import com.viglet.shio.api.agent.dto.ShAgentEndpoint;
import com.viglet.shio.api.agent.dto.ShAgentLimits;
import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.cda.ShCdaProperties;
import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.property.ShImageTransformProperties;
import com.viglet.shio.schedule.ShScheduleProperties;
import com.viglet.shio.turing.ShTuringIndexService;
import com.viglet.shio.webhook.ShWebhookProperties;

/**
 * Builds the agent manifest (SH76).
 *
 * <p>Two kinds of capability are reported, and the distinction matters for
 * honesty: <b>configured</b> features are read from the live properties (a
 * disabled webhook subsystem reports {@code false}), while <b>build</b> features
 * are constants of this artifact — the capability is compiled in, so an instance
 * of this version always has it. Planned-but-unshipped capabilities are reported
 * as {@code false} rather than omitted, because an absent flag makes a caller
 * probe, which is exactly the cost the manifest exists to remove.</p>
 *
 * <p>The whole payload is deterministic — sorted feature keys, fixed endpoint
 * order — so it caches, diffs, and (once SH116 lands) answers {@code 304}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShAgentManifestService {

    /**
     * Revision of the agent surface shape. Bump when the manifest or context pack
     * changes shape, not when the product version changes.
     */
    public static final int PROTOCOL = 1;

    /** Base path the {@code endpoints} index is expressed against. */
    public static final String API_BASE = "/api/v2";

    private final ShAppProperties shAppProperties;
    private final ShCdaProperties shCdaProperties;
    private final ShWebhookProperties shWebhookProperties;
    private final ShScheduleProperties shScheduleProperties;
    private final ShImageTransformProperties shImageTransformProperties;
    private final ShAgentProperties shAgentProperties;
    private final ShTuringIndexService shTuringIndexService;
    private final ShAgentBuildIdentity shAgentBuildIdentity;
    private final ShMarketplaceProperties shMarketplaceProperties;
    private final ShConsoleSurfaces shConsoleSurfaces;
    private final ShConsoleCost shConsoleCost;

    public ShAgentManifestService(ShAppProperties shAppProperties,
                                 ShCdaProperties shCdaProperties,
                                 ShWebhookProperties shWebhookProperties,
                                 ShScheduleProperties shScheduleProperties,
                                 ShImageTransformProperties shImageTransformProperties,
                                 ShAgentProperties shAgentProperties,
                                 ShTuringIndexService shTuringIndexService,
                                 ShAgentBuildIdentity shAgentBuildIdentity,
                                 ShMarketplaceProperties shMarketplaceProperties,
                                 ShConsoleSurfaces shConsoleSurfaces,
                                 ShConsoleCost shConsoleCost) {
        this.shAgentBuildIdentity = shAgentBuildIdentity;
        this.shAppProperties = shAppProperties;
        this.shCdaProperties = shCdaProperties;
        this.shWebhookProperties = shWebhookProperties;
        this.shScheduleProperties = shScheduleProperties;
        this.shImageTransformProperties = shImageTransformProperties;
        this.shAgentProperties = shAgentProperties;
        this.shTuringIndexService = shTuringIndexService;
        this.shMarketplaceProperties = shMarketplaceProperties;
        this.shConsoleSurfaces = shConsoleSurfaces;
        this.shConsoleCost = shConsoleCost;
    }

    /**
     * The manifest a caller asked for, which is the ordinary one plus whatever it opted into.
     *
     * <p>SH979 — {@code surfaces} is attached here rather than always built, so every other read is
     * byte-identical to what it was: the console's map is a document about the console, and a
     * caller looking up an endpoint should not pay seventeen rows for it.</p>
     */
    public ShAgentManifest manifest(Set<ShAgentManifestSection> sections) {
        ShAgentManifest manifest = manifest();
        boolean surfaces = sections.contains(ShAgentManifestSection.SURFACES);
        boolean console = sections.contains(ShAgentManifestSection.CONSOLE);
        if (!surfaces && !console) {
            return manifest;
        }
        // SH1112 — the console's cost, measured from the build this instance serves, attached the
        // way SH979's surfaces are: only when asked, so every other read keeps its bytes and its ETag.
        return new ShAgentManifest(manifest.product(), manifest.version(), manifest.protocol(),
                manifest.apiBase(), manifest.commit(), manifest.builtAt(), manifest.features(),
                manifest.elsewhere(), manifest.limits(), manifest.endpoints(), manifest.startHere(),
                manifest.omitted(), surfaces ? shConsoleSurfaces.surfaces() : null,
                console ? shConsoleCost.cost() : null);
    }

    public ShAgentManifest manifest() {
        return new ShAgentManifest(
                "Viglet Shio",
                version(),
                PROTOCOL,
                API_BASE,
                shAgentBuildIdentity.commit(),
                shAgentBuildIdentity.builtAt(),
                features(),
                elsewhere(),
                limits(),
                endpoints(),
                startHere());
    }

    /** Sorted so the payload is byte-stable across calls and instances (P5). */
    private Map<String, Boolean> features() {
        Map<String, Boolean> features = new TreeMap<>();

        // Configured — read from the live instance.
        features.put("multiTenant", shAppProperties.isMultiTenant());
        features.put("oidc", shAppProperties.isKeycloak());
        features.put("webhooks", shWebhookProperties.isEnabled());
        features.put("scheduledPublish", shScheduleProperties.isEnabled());
        features.put("previewTokens", shCdaProperties.getPreview().isEnabled());
        features.put("rateLimit", shCdaProperties.getRateLimit().isEnabled());
        features.put("signedImageUrls", shImageTransformProperties.getSigning().isEnabled());
        // SH84: whether publishing makes a page *findable*. Configured, and read from the
        // service rather than from the property, because "does this instance index" is
        // `enabled` and a URL together — a second copy of that rule here would report true
        // on an instance that indexes nothing. `search: true` above is the in-DB SH52
        // predicate and says nothing about this one; an agent that read only that flag
        // would take a published-but-unindexed page for a working search.
        features.put("searchIndexing", shTuringIndexService.active());
        // SH678 — whether this instance reads a package catalogue. Configured, and it is the
        // honest shape for exactly the reason the section above gives: off is the default, so an
        // agent that assumed a marketplace would be choosing from a list nobody enabled. `false`
        // does not mean no packages — `shio_marketplace` still lists what shipped with the
        // instance — it means nothing new can arrive. The tool is read-only either way, because
        // installing calls a service in process and an API token reaching /mcp is barred from
        // /api/v2/marketplace, which the non-goals name as an escalation.
        features.put("marketplace", shMarketplaceProperties.hasRemote());

        // Build capabilities — compiled into this artifact.
        features.put("graphql", true);
        features.put("search", true);
        features.put("locales", true);
        features.put("contentAcl", true);
        features.put("trash", true);
        features.put("exchange", true);
        features.put("imageTransforms", true);
        // SH1032 — declared so the gap is countable. The meta and usage endpoints have existed
        // since SH62 with no agent reach at all, and ShAgentP1ConformanceTest classifies FEATURE
        // KEYS: a capability nothing declares is one the instrument built to find P1 breaks cannot
        // see. Naming them is what turns an invisible gap into a failing build — which is the only
        // reason to name a capability before closing it.
        features.put("assetMetadata", true);
        features.put("assetUsage", true);
        features.put("postTypesAsCode", true);
        // SH183 — this instance can render a page itself, so a site is visible with no front
        // end and no project. It is the flag an MCP-only caller needs most (SH186): without
        // it, "build a site" ends with content nobody can look at and no way to know that
        // /preview exists.
        features.put("serverRender", true);
        // SH185 — true, and until then it was not. The console shell had posted `sh:*`
        // messages into an iframe since SH10 with nothing in this repository on the other
        // side, so this flag claimed a capability no caller could use. An unshipped
        // capability reports `false` and never overstates; this was the first one to break
        // its own rule, and the fix was to ship the counterpart rather than to demote it.
        features.put("universalEditor", true);
        features.put("teachingErrors", true);
        features.put("agentContext", true);
        features.put("terseResponses", true);
        features.put("pathAddressing", true);
        features.put("batchWrites", true);
        features.put("desiredState", true);
        features.put("mcp", true);
        features.put("fieldProjection", true);
        // SH134/SH135: the vocabulary can change a site, not only build one — an address
        // can be moved, and a desired-state prune removes undeclared folders as well as
        // undeclared posts. Advertised because an agent that does not know post.move
        // exists will reach for the destructive workaround.
        features.put("postMove", true);
        features.put("folderPrune", true);
        // SH137: the same verb one level up, and the reason it is advertised separately is
        // that its absence was the more dangerous one — an agent renaming a section without
        // it reaches for folder.upsert + folder.delete, and that delete takes the subtree.
        features.put("folderMove", true);
        // SH170: an agent can make the site it was asked to build. Advertised because its
        // absence used to send the agent to the console entity API, which answers a stateless
        // caller with a bodiless 403 — the one dead end on the whole surface.
        features.put("siteUpsert", true);
        // SH463: and it can remove one. The flag exists as much for the INSTRUMENT as for the
        // caller - ShAgentP1ConformanceTest classifies feature keys, so a capability with no
        // key is not classified at all, and site deletion had none. That is how the one test
        // built to find unreachable capabilities could not see the gap the product is named
        // after: an agent could create a site and needed a browser to remove one.
        features.put("siteDelete", true);
        // SH152: an apply can add the fields an existing post-type lacks (opt-in per call).
        features.put("postTypeMerge", true);
        // Block K: the agent can check its own work. contentLint was reported false
        // through the whole of Block H precisely so that this flag flipping would mean
        // something — it is now backed by real rules, not a stub that always passes.
        features.put("contentLint", true);
        features.put("routeCheck", true);
        features.put("renderDigest", true);
        features.put("diagnostics", true);
        // SH754: whether this instance can say what the caller has spent. Worth a flag for the
        // reason durableIncidents below is — it changes what a *question* means: without it, "how
        // much of my budget is left" has no answer here and a plan is something the agent has to
        // be told rather than something it can read.
        features.put("usageMeter", true);
        // SH144: the incident record survives a restart, and rides the SH88 cursor. Worth its
        // own flag because it changes what a *question* means, not just what an endpoint
        // returns: with it false, "was anything failing while I was away" is unanswerable and
        // an empty diagnostics report proves nothing.
        features.put("durableIncidents", true);
        // SH88: "everything since I last looked" is expressible now. Reported false
        // through Block H and Block K on purpose, so the flag flipping means the cursor
        // exists rather than that someone remembered to advertise it.
        features.put("changeCursor", true);
        // Whether ?url= may fetch a delivery URL. Configured, not compiled: this one
        // depends on an operator's decision, and probing it costs a refusal.
        features.put("renderFetch", shAgentProperties.getRender().isAllowFetch());
        // SH141: whether verify can prove a route from the delivery side rather than against
        // the content graph. Reported separately from renderFetch even though one property
        // governs both, because they answer different questions and an agent that reads only
        // `renderFetch` would never learn that `link-unresolved` can be an error here. The
        // manifest is where this belongs rather than the MCP tool schema: a tool description is
        // re-sent every turn (P3), and on the instances where this is false it would be a tax
        // for a capability that cannot run.
        features.put("deliveryProof", shAgentProperties.getRender().isAllowFetch());

        // Block J: appliable starting points. Reported false through Blocks H, K and I so
        // that this flag flipping means a real package format with a real registry behind
        // it, rather than a documented intention.
        features.put("blueprints", true);
        // Block I: content projects to files and syncs both ways from the CLI. Compiled
        // in rather than configured — the capability is `shio pull --content`, which is a
        // property of the tooling this instance's protocol supports, not of its config.
        features.put("contentAsFiles", true);
        // Block M: this instance remembers. The flag is what tells a session that
        // writing a conclusion down will actually be read back next time — without
        // it, "remember that" is a call an agent has no reason to make.
        features.put("instanceMemory", true);
        features.put("conditionalReads", true);
        features.put("agentsMd", true);
        // SH174: this document can be asked for in pieces and in the terse encoding. It is
        // advertised because the saving is unreachable by guessing — a capability check that
        // does not know `?include=features,limits` exists pays the full endpoint index to read
        // one boolean, on the first call of every session, where no ETag can help.
        features.put("manifestProjection", true);
        // SH157, the two halves of the round-trip claim. Both are opt-in and both are
        // unreachable by guessing, which is exactly what a capability flag is for:
        // `?include=manifest` on the context pack folds a session's two opening reads into
        // one, and `?verify=true` on a write folds the closing one in.
        features.put("contextCapabilities", true);
        features.put("writeProof", true);
        // Block L: what bounds this caller. Reported here because all three change what a
        // write does, and a caller that has to discover them by being refused pays for the
        // discovery twice — once in the failed call and once in the confusion.
        //
        // `publishGate` says publishing is a per-credential capability (SH107); it is true
        // as a *protocol* fact, and whether this particular caller has it is answered by
        // trying, because the manifest is cached and a credential is not. `confirmTokens`
        // says a delete needs the SH108 token from its own dryRun — an agent that does not
        // know this reads its first 428 as a bug.
        features.put("agentTokenScope", true);
        features.put("publishGate", true);
        features.put("confirmTokens", true);
        features.put("reviewQueue", true);
        // SH102's visual snapshot. It ships — as `shio snapshot`, with no endpoint and no MCP
        // tool — and it is still reported `false` here, which is the only honest answer: this
        // flag advertises what *this surface* can do, and an agent that read `true` would go
        // looking for the call that produces a PNG and find none. Saying `true` to mean "a
        // human has a command for it" would cost three orders of magnitude more tokens than
        // the digest the agent should be using, for a capability the protocol does not offer.
        features.put("visualSnapshot", false);
        // SH187 — the part of appearance this surface *does* answer: the class inventory, which of
        // them no stylesheet the page carries defines, inline styles, images that will reflow, and
        // a second hash over all of it so an agent can tell "I restyled it" from "I broke the
        // outline". A separate flag rather than flipping `visualSnapshot`, for that flag's own
        // stated reason: there is still no raster on this surface, and an agent that read
        // `visualSnapshot: true` would go looking for a PNG. Two capabilities, two answers.
        features.put("appearanceDigest", true);
        // SH1002 — the read half of review, and the only half on this namespace. A revert leaves
        // ordinary restore and delete audit rows that look like a curator's own edit, so a resumed
        // session could not tell "my page came back" from "somebody rejected my page".
        features.put("reviewVerdicts", true);
        // SH391's page audit — the third browser command, and the first one shaped for an agent:
        // `shio audit <page>` renders the page and returns the overflowing boxes, the failed
        // requests, the console errors, the contrast floor and the fallback fonts as *text*, each
        // with a selector. Reported `false` for `visualSnapshot`'s reason and one of its own: there
        // is no endpoint, and there must not be — "the instance does not run a browser" is a
        // standing non-goal, so this flag saying `true` would advertise a Chromium process inside
        // the JVM. The pointer beside it is how a client learns the capability exists at all.
        features.put("pageAudit", false);
        // SH377/SH378's capture pass — `shio clone <url>` walks a site that already exists, writes an
        // inventory, and with `--assets` downloads the bytes it names. `false` here is not modesty: a
        // URL the *server* fetches is SH101's request-forgery surface argued a second time, which
        // SH268 already settled, and a page whose markup arrives after hydration needs the browser
        // this surface must not have. Both reasons are the same reason the flag beside it is false.
        // One flag rather than two for the capture and the download: they are one capability with a
        // second pass, and a caller who has the command has both.
        features.put("siteCapture", false);
        // SH379's convert pass — `shio propose` reads the capture and says which blocks could become
        // SH95 sections, what fields each would carry and what accepting it would lose. Its own key
        // rather than folded into `siteCapture`, and the rule is the one that made the download a
        // second pass of the same capability: `--assets` is a *phase of the same act*, and a caller
        // who has `clone` has it. A conversion proposal is a different act on a different artefact,
        // and nothing in a session tells a client it exists — which is exactly the absence SH393
        // says must be published rather than left to be discovered by not finding a tool.
        features.put("conversionProposal", false);
        // SH1067 — the third act of the replication arc, and the one the census could not see. Two
        // of the three had keys (`siteCapture` since SH377, `conversionProposal` since SH379) and
        // the conversion itself had none, so "can an agent replicate a site" had no answer here at
        // all. `false` for its siblings' reason and for one of its own, which is the interesting
        // half: the conversion's WRITE is not CLI-shaped — `shio convert` composes an SH82
        // desired-state document and applies it through `POST /agent/apply`, which an MCP client
        // reaches with `shio_apply` today. What cannot leave the CLI is composing that document
        // from a capture in the file tree, which is `conversionProposal`'s reason. So the pointer
        // beside it names the command that WRITES the document rather than the one that applies it.
        features.put("siteConversion", false);
        return features;
    }

    /**
     * Where a capability lives when it does not live on this surface (SH393).
     *
     * <h2>Why this is twelve entries and not fifty-five</h2>
     *
     * <p>A caller does not need to be told where {@code post.move} lives — {@code tools/list} and
     * the endpoint index say so, and saying it a third time is the repetition P3 exists to stop.
     * What it cannot derive is the <b>absence</b>: nothing in a JSON-RPC session says "this one is
     * a zip, so it is CLI-shaped", and an agent has no way to learn that except by failing. So this
     * map holds exactly the capabilities the protocol does not carry, and its keys are asserted
     * equal to {@code ShAgentP1ConformanceTest}'s exception list — a new gap must either be closed
     * or published here.</p>
     *
     * <h2>The rule the entries follow, so the tenth does not have to be argued</h2>
     *
     * <p><b>MCP owns address-in, small-JSON-out verbs a session repeats</b>, because a client may
     * have no shell. <b>The CLI owns whatever has a file, a byte stream, a browser or a human on
     * one end</b>, because none of those fits a JSON-RPC frame. <b>The console owns the human's
     * veto.</b> Nothing is owned by neither — an unowned capability is the gap, not the answer.
     * Six shipped tasks each derived that boundary from scratch and each got it right (§VIII.15);
     * this is the derivation written down where it can be checked.</p>
     *
     * <p>Sorted, like {@code features}, so the payload is byte-stable (P5).</p>
     *
     * <h2>Pointers, never prose — and this one was measured, not asserted</h2>
     *
     * <p>The first draft explained each entry ("console — the human's veto over the agent's work
     * (SH109)") and cost <b>138 tokens</b> on the full manifest. The reason belongs in
     * {@code ShAgentP1ConformanceTest}, which is where a decision is reviewed; what a caller needs
     * is the command. Trimmed to bare pointers it is a third of that.</p>
     *
     * <p><b>And it is its own {@code include=} section rather than part of {@code features}</b>,
     * which is the opposite of the first design and for a measured reason: bundled, it put
     * {@code ?include=features,limits} — the cheap capability check an agent makes every session —
     * from 326 to 469 tokens, a 44% regression on the most-repeated read on the surface. P3 makes
     * that a build failure, and {@code ShAgentResponseBudgetTest} duly failed it. The full document
     * carries it (there, beside a 2000-token endpoint index, it is noise), and a client that wants
     * only the pointers asks for {@code include=elsewhere}.</p>
     */
    private Map<String, String> elsewhere() {
        Map<String, String> elsewhere = new TreeMap<>();
        // CLI-shaped: a file, a byte stream or a rasteriser on one end (SH268's rule).
        // SH812 — this pointed at the CLI, which was honest while `shio_context` took no
        // `format`. It does now, so a pointer here would send a caller to a shell for
        // something the tool they are already holding answers.
        elsewhere.put("contentAsFiles", "shio pull --content");
        elsewhere.put("conversionProposal", "shio propose");
        // SH1065 — both halves now, because until it had a verb the export was reachable only
        // by building a URL from a resolved uuid, and a pointer naming one half reads as a
        // capability that only goes one way.
        elsewhere.put("exchange", "shio import <file> | shio export site:<name>");
        elsewhere.put("pageAudit", "shio audit <page>");
        elsewhere.put("siteCapture", "shio clone <url>");
        // SH1067 — `--out` is the half that matters to a client holding `shio_apply`: it
        // writes the desired-state document instead of applying it, so the conversion
        // arrives on this surface even though composing it did not.
        elsewhere.put("siteConversion", "shio convert --out <file>");
        elsewhere.put("visualSnapshot", "shio snapshot <page>");
        // Operator-shaped: a person's credential is the point, not a browser. SH975 — this said the
        // word `console`, which is not something a caller can run, and was wrong besides:
        // `/api/v2/review/**` carries the narrowed CSRF exemption, so a curator reaches it with HTTP
        // Basic from a terminal. What an AGENT token may not do is write there (SH109), and that is
        // the fact worth publishing — so the pointer names the route and says whose credential.
        // Terse because this document has a ceiling and SH743 keeps room under it: the sentence the
        // caller needs is who may call it, and the console's own listing needs no pointer from here.
        // SH1002 — half of it is no longer elsewhere, and the pointer names the half that is: what
        // an AGENT token may not do is *decide*. The read it can make has its own flag
        // (`reviewVerdicts`) rather than a second pointer, because a pointer says "go somewhere
        // else" and that read is here. It is deliberately not in the endpoint index: that index is
        // curated and has a ceiling SH743 keeps room under, and this capability is already
        // published three ways — the flag, `shio://review/mine`, and this sentence.
        elsewhere.put("reviewQueue", "GET /review/sessions (operator credential, not AGENT)");
        // SH732 — `trash` was here and had been wrong for a block. SH222 gave an agent
        // `find?trash=true` to list it and `post.restore`/`folder.restore` to undo a delete, and
        // SH648 added `site.restore`. A pointer that says "console" for something three ops do is
        // worse than no pointer: it costs a caller the one read that would have told them
        // otherwise, and P2 makes this document the read a session makes first.
        // SH809 — `universalEditor` pointed here at "console", which was honest while the bridge was
        // a browser protocol with no agent counterpart. `/agent/editable` is that counterpart, so
        // the capability is no longer elsewhere and a pointer would send a caller away from the
        // route that answers them.
        // REST-only, and deliberately: the delivery surface a front end queries, and a body field
        // that would cost more on tools/list than it saves.
        elsewhere.put("graphql", "POST /graphql");
        elsewhere.put("postTypeMerge", "POST /agent/apply mergePostTypes:true");
        return elsewhere;
    }

    private ShAgentLimits limits() {
        ShCdaProperties.RateLimit rateLimit = shCdaProperties.getRateLimit();
        return new ShAgentLimits(
                rateLimit.isEnabled(),
                rateLimit.getCapacity(),
                rateLimit.getRefillTokens(),
                rateLimit.getRefillPeriodSeconds(),
                50,
                shCdaProperties.getCache().getMaxAgeSeconds(),
                shAgentProperties.getRender().getMaxWriteDigests());
    }

    /**
     * The curated index. Order is intentional — discovery, then reads, then the
     * content model, then writes — so reading it top-to-bottom is a usable
     * walkthrough rather than an alphabetical dump.
     */
    private List<ShAgentEndpoint> endpoints() {
        return List.of(
                new ShAgentEndpoint("GET", "/agent/manifest",
                        "This document: version, capabilities, limits, endpoint index. "
                                + "include=features,limits drops the index when you only need a "
                                + "capability check; format=terse compacts what is left.",
                        "console", "GET /api/v2/agent/manifest?include=features,limits"),
                new ShAgentEndpoint("GET", "/agent/context",
                        "Start here: content model + sitemap by path + conventions in one call. "
                                + "include=manifest adds this instance's capabilities so you need "
                                + "no separate manifest read; include=ops adds the write "
                                + "vocabulary; format=terse compacts it; re-check `digest` next "
                                + "session instead of re-reading.",
                        "console",
                        "GET /api/v2/agent/context?format=terse&include=all,manifest"),
                new ShAgentEndpoint("GET", "/agent/resolve",
                        "Turn a content address into an object id — post:<site>/<url>, "
                                + "folder:<site>/<path>, site:<name> or id:<uuid>. Build the "
                                + "address from a context row; you never need to remember a UUID.",
                        "console", "GET /api/v2/agent/resolve?address=post:Blog/blog/hello-world"),
                // SH346 — the op names and their keys used to be prose here, which is the guess
                // ShAgentOps exists to prevent, spelled out on the document every session reads
                // whether or not it writes. It points at the vocabulary instead: the pointer is
                // cheaper than the list it replaced, and the list it points at is exhaustive.
                new ShAgentEndpoint("POST", "/agent/batch",
                        "Write: N addressed ops in one atomic call — the whole vocabulary, with "
                                + "the keys each op takes, is GET /agent/context?include=ops. Read "
                                + "it before a first write: an unrecognised key is refused. Send "
                                + "dryRun to see the plan first. site.upsert is step one and it is "
                                + "here, not on the console API; re-addressing an upsert creates a "
                                + "second post, so rename with post.move.",
                        "console", "POST /api/v2/agent/batch?verify=true"),
                new ShAgentEndpoint("POST", "/agent/plan",
                        "Diff a desired-state content document against this instance. "
                                + "Writes nothing; changed=0 means no drift.",
                        "console", "POST /api/v2/agent/plan"),
                new ShAgentEndpoint("POST", "/agent/apply",
                        "Make that document true, atomically. Re-applying it is a no-op.",
                        "console", "POST /api/v2/agent/apply"),
                new ShAgentEndpoint("GET", "/agent/blueprints",
                        "Appliable starting points — a whole site section (model + folders + "
                                + "pages) as one package. Read this before modelling a blog, a "
                                + "docs tree or a marketing site by hand.",
                        "console", "GET /api/v2/agent/blueprints?format=terse"),
                new ShAgentEndpoint("POST", "/agent/blueprints/{name}/apply",
                        "Apply one, into an existing site: {site, params, dryRun}. dryRun "
                                + "returns the whole plan — post-types included — and writes "
                                + "nothing. A blueprint adds; it never prunes.",
                        "console", "POST /api/v2/agent/blueprints/blog/apply"),
                new ShAgentEndpoint("GET", "/agent/find",
                        "Find posts by site / type / folder subtree / text. Returns addresses "
                                + "and titles, never bodies — read only what you pick. A "
                                + "truncated result carries a cursor: pass it as ?after= to "
                                + "continue.",
                        "console", "GET /api/v2/agent/find?site=Blog&type=Article"),
                new ShAgentEndpoint("GET", "/agent/read",
                        "Read posts by address (drafts preferred). Repeat ?address= for several, "
                                + "and pass ?fields= to fetch only the fields you need. On a "
                                + "PageLayout or Region, include=impact says what renders through "
                                + "it — ask before you rewrite a shared one.",
                        "console", "GET /api/v2/agent/read?address=post:Blog/blog/hello-world"),
                // Deliberately terse: the first draft cost 90 tokens over the manifest's ceiling and
                // P3 says lean the payload before moving it. What survives is the decision a reader
                // makes — that this is the page's answer, not the model's, and that `content` is a
                // page which does not render rather than one with nothing to edit.
                new ShAgentEndpoint("GET", "/agent/editable",
                        "Which fields a curator could edit on a page, and its regions, from the "
                                + "rendered page. `source:content` means it does not render yet.",
                        "console", "GET /api/v2/agent/editable?address=post:Blog/blog/hello-world"),
                new ShAgentEndpoint("GET", "/agent/verify",
                        "Check your own work: required fields, dangling references, missing "
                                + "assets, duplicate URLs, empty folders, unresolved links. "
                                + "`ok:false` means at least one error; every finding carries a fix. "
                                + "Add checks=delivery to prove each published route by fetching it "
                                + "(needs deliveryProof).",
                        "console", "GET /api/v2/agent/verify?format=terse"),
                new ShAgentEndpoint("GET", "/agent/render",
                        "Structural digest of one page — heading outline, landmarks, links and "
                                + "images with alt, word count. ?url= digests what a visitor gets "
                                + "when fetching is enabled.",
                        "console", "GET /api/v2/agent/render?address=post:Blog/blog/hello-world"),
                new ShAgentEndpoint("GET", "/agent/changes",
                        "What changed while you were away: content transitions with an "
                                + "address each, and a `cursor` to pass back as since= so the "
                                + "next call is only the delta.",
                        "console", "GET /api/v2/agent/changes?since=2026-07-26T10:00:00Z|<id>"),
                new ShAgentEndpoint("GET", "/agent/diagnostics",
                        "What failed after you moved on: webhook deliveries, image transforms, "
                                + "scheduled publishes, server errors. One call instead of logs.",
                        "console", "GET /api/v2/agent/diagnostics"),
                new ShAgentEndpoint("PUT", "/agent/memory",
                        "Remember a decision for the next session: {key, note, scope}. It is "
                                + "served in the context pack from then on, so what you worked "
                                + "out here is not re-derived later. Re-using a key rewrites "
                                + "that note; DELETE ?key= forgets it.",
                        "console", "PUT /api/v2/agent/memory"),
                new ShAgentEndpoint("GET", "/cda/site",
                        "List published sites (id, name, root folder id).",
                        "token:READ", "GET /api/v2/cda/site"),
                // SH344 — the two CDA navigation rows that stood here were pruned to fund this one,
                // which is the question the SH327 note said the next raise had to answer. They are
                // the two the agent surface fully supersedes: the context pack's sitemap *is* the
                // folder tree and /agent/find walks it by address, so listing children by folder id
                // and building a breadcrumb from an id are steps an agent no longer takes. The four
                // CDA rows that remain describe what a *front end* reads — one post, a query, a
                // search, a schema — which is code the agent writes and cannot derive from here.
                // The index says of itself that it is curated, not exhaustive; this is that being
                // true rather than stated.
                new ShAgentEndpoint("GET", "/cda/post/{id}",
                        "One published post with expanded attributes.",
                        "token:READ", "GET /api/v2/cda/post/{id}"),
                // `/cda/post/by-url` used to sit here, described as "the cheapest addressing
                // available today" — a hedge written before addressing existed. SH79 plus
                // /agent/resolve and /agent/read do the same job without a site id, so the
                // slot went to /agent/memory. The index is capped on purpose (P3): adding an
                // entry is meant to cost a decision about which one it displaces, not a
                // raised ceiling. The endpoint itself is unchanged and still served.
                new ShAgentEndpoint("GET", "/cda/query",
                        "Query published posts by site / folder / post-type.",
                        "token:READ", "GET /api/v2/cda/query?siteId={siteId}&postType=Article&page=0&size=20"),
                new ShAgentEndpoint("GET", "/cda/search",
                        "Full-text search over title, summary and content.",
                        "token:READ", "GET /api/v2/cda/search?q=pricing&siteId={siteId}"),
                new ShAgentEndpoint("GET", "/cda/post-type",
                        "Content-model schema of every post-type (delivery shape, lossy).",
                        "token:READ", "GET /api/v2/cda/post-type"),
                new ShAgentEndpoint("GET", "/post-type",
                        "Lossless post-type definitions — the authoring shape `shio pull` writes.",
                        "console", "GET /api/v2/post-type"),
                new ShAgentEndpoint("PUT", "/post-type/{name}",
                        "Upsert a post-type by name; idempotent. Rejects system types (409).",
                        "console", "PUT /api/v2/post-type/Article"),
                // SH327 — declared because `serverRender: true` was a half-truth without it: the
                // capability existed and the endpoint that provisions it appeared nowhere a caller
                // reads. SH338's inventory named it and SH339's named the CSRF half.
                new ShAgentEndpoint("POST", "/site-webhook",
                        "Subscribe to content transitions: {siteId, name, url, secret, events}. The "
                                + "secret is write-only. Failures show in /agent/diagnostics.",
                        "console", "POST /api/v2/site-webhook"),
                new ShAgentEndpoint("POST", "/preview-token",
                        "Mint a short-lived token so an external front end can show a draft. "
                                + "Body: {postId}. Returns previewUrl + token; token is null when "
                                + "Shio previews the site itself.",
                        "console", "POST /api/v2/preview-token"),
                new ShAgentEndpoint("POST", "/render/post-types",
                        "Create the PageLayout/Region/Theme types this instance renders with; "
                                + "idempotent. Then /preview/{site}/{url} shows a page, no front "
                                + "end needed. GET .../drift names any field an older instance is "
                                + "missing.",
                        "console", "POST /api/v2/render/post-types"),
                // SH343 — the create and publish rows that stood here were pruned to fund the webhook
                // row, on a sharper version of SH344's reasoning: they are the exact id-keyed twins of
                // `post.upsert` and `post.publish`, so listing them in the *agent* manifest pointed the
                // agent at the path this whole surface exists to replace. `POST /api/v2/site` has never
                // been indexed for that reason (SH170 grew an op instead) — these two were the anomaly.
                // `PUT /post-unified/{id}` stays: a caller holding an id from /agent/resolve has one
                // honest reason to write in place.
                new ShAgentEndpoint("PUT", "/post-unified/{id}",
                        "Update a post's data in place.",
                        "console", "PUT /api/v2/post-unified/{id}"),
                // SH1035 — `replace` rides this row rather than taking one of its own. It is the
                // same multipart surface and the same reason it is not an MCP tool (base64 through
                // a tool call), so a caller who found this line has found both; a second row would
                // cost ~50 tokens on the most-read document here against ~15 for the clause, and
                // `response.manifest` has been at its wall twice.
                new ShAgentEndpoint("POST", "/staticfile/upload",
                        "Upload a binary as a File post (multipart: file, folderId, createPost); "
                                + "{id}/replace swaps its bytes, keeping the id.",
                        "console", "POST /api/v2/staticfile/upload"),
                new ShAgentEndpoint("GET", "/site/{id}/export",
                        "Export a whole site as a portable exchange package (zip).",
                        "console", "GET /api/v2/site/{id}/export"),
                // SH1050 — declared rather than classified console-only, and the difference is the
                // point. The context pack says what you CAN do here; this says what is going on:
                // how much exists, what is waiting for a person, what just happened. An agent
                // arriving mid-project wants the second, and composing it from the review surface,
                // the scheduled listing, the note list and the change feed is four calls for one
                // question — P2 by name. `shio status` is this read with a printer on it.
                new ShAgentEndpoint("GET", "/console/summary",
                        "What is going on here: counts, what is waiting for a person (review "
                                + "sessions, publishes due this week, open notes) and the most "
                                + "recent trail rows, in one call.",
                        "console", "GET /api/v2/console/summary"));
    }

    /**
     * The opening move, then the branch (SH601).
     *
     * <p>It used to be five ordered lines: read the pack, address content this way, write with
     * batch, then verify, and MCP exists. That is a <b>plan</b>, and a reader does not arrive with
     * a plan — they arrive with an intent. Mapping "this run failed with a 500" onto a list of
     * everything was the unbudgeted work, and it is where the errors were.</p>
     *
     * <p>So: the two facts that are true whatever the intent, then {@link ShAgentIntents} — the
     * question a reader arrives with, and the one call. Three of the five old lines are gone rather
     * than kept beside it, which is what pays for the routing: "write with batch" and "then verify"
     * are now the answers to two of the rows, and saying them twice would be the inventory this
     * replaces, at the cost the budget refused.</p>
     */
    private List<String> startHere() {
        List<String> lines = new ArrayList<>();
        lines.add("GET /api/v2/agent/context?format=terse — model, sitemap and conventions in one "
                + "call; store the returned `digest` and compare it next session before "
                + "re-reading anything");
        lines.add("address content as post:<site>/<url> or folder:<site>/<path>; "
                + "GET /api/v2/agent/resolve turns one into an id when an endpoint needs it");
        // The branch. Prefixed so a reader scanning for their own situation knows these are
        // alternatives rather than further steps — an ordered list read as a sequence is exactly
        // the misreading the old shape invited.
        lines.add("then, whichever you need:");
        lines.addAll(ShAgentIntents.lines());
        // Kept, and the trim that dropped it was caught by SH598's reachability walk within the
        // hour: this line is the ONLY place the tool names are published, so removing it took
        // eleven tools out of what a reader can reach from one call. Shortened rather than
        // restored — the prose around the names carried no decision, and the names are the whole
        // point of it.
        lines.add("over MCP: POST /mcp — tools shio_context, shio_find, shio_read, shio_write, "
                + "shio_apply, shio_publish, shio_assets, shio_changes, shio_verify, shio_digest, "
                + "shio_remember");
        return List.copyOf(lines);
    }

    /** Same source as {@code /api/v2/system/info}: the jar manifest, {@code dev} in an IDE. */
    private String version() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    /**
     * A content digest of the manifest (SH116) — the value behind its entity tag.
     *
     * <p>Derived from the parts that can actually move at runtime: the build version
     * and the configured feature flags. The endpoint index and the limits are
     * constants of the artifact, already covered by the version, and hashing the
     * serialized document instead would tie the tag to Jackson's field order — a
     * dependency upgrade would then invalidate every client's cache for no
     * change.</p>
     *
     * <p>SH460 folds the build identity in, and it is not a detail: {@code version} reads
     * {@code dev} on every developer instance and the feature flags do not move on a rebuild, so
     * without {@code builtAt} a client that cached this document before a restart is answered
     * {@code 304} by the very instance whose staleness it is trying to detect.</p>
     */
    public String digest() {
        StringBuilder canonical = new StringBuilder(version()).append('|').append(PROTOCOL)
                .append('|').append(shAgentBuildIdentity.commit())
                .append('|').append(shAgentBuildIdentity.builtAt());
        features().forEach((key, value) -> canonical.append('|').append(key).append('=').append(value));
        return ShAgentContextService.digest(canonical.toString());
    }

    /** Exposed for tests: the feature map as an insertion-ordered copy. */
    Map<String, Boolean> featureSnapshot() {
        return new LinkedHashMap<>(features());
    }
}
