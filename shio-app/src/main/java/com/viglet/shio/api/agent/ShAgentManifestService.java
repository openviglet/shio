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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.dto.ShAgentEndpoint;
import com.viglet.shio.api.agent.dto.ShAgentLimits;
import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.cda.ShCdaProperties;
import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.property.ShImageTransformProperties;
import com.viglet.shio.schedule.ShScheduleProperties;
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

    public ShAgentManifestService(ShAppProperties shAppProperties,
                                 ShCdaProperties shCdaProperties,
                                 ShWebhookProperties shWebhookProperties,
                                 ShScheduleProperties shScheduleProperties,
                                 ShImageTransformProperties shImageTransformProperties,
                                 ShAgentProperties shAgentProperties) {
        this.shAppProperties = shAppProperties;
        this.shCdaProperties = shCdaProperties;
        this.shWebhookProperties = shWebhookProperties;
        this.shScheduleProperties = shScheduleProperties;
        this.shImageTransformProperties = shImageTransformProperties;
        this.shAgentProperties = shAgentProperties;
    }

    public ShAgentManifest manifest() {
        return new ShAgentManifest(
                "Viglet Shio CMS",
                version(),
                PROTOCOL,
                API_BASE,
                features(),
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

        // Build capabilities — compiled into this artifact.
        features.put("graphql", true);
        features.put("search", true);
        features.put("locales", true);
        features.put("contentAcl", true);
        features.put("trash", true);
        features.put("exchange", true);
        features.put("imageTransforms", true);
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
        // SH152: an apply can add the fields an existing post-type lacks (opt-in per call).
        features.put("postTypeMerge", true);
        // Block K: the agent can check its own work. contentLint was reported false
        // through the whole of Block H precisely so that this flag flipping would mean
        // something — it is now backed by real rules, not a stub that always passes.
        features.put("contentLint", true);
        features.put("routeCheck", true);
        features.put("renderDigest", true);
        features.put("diagnostics", true);
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
        return features;
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
                                + "no separate manifest read; format=terse compacts it; re-check "
                                + "`digest` next session instead of re-reading.",
                        "console",
                        "GET /api/v2/agent/context?format=terse&include=all,manifest"),
                new ShAgentEndpoint("GET", "/agent/resolve",
                        "Turn a content address into an object id — post:<site>/<url>, "
                                + "folder:<site>/<path>, site:<name> or id:<uuid>. Build the "
                                + "address from a context row; you never need to remember a UUID.",
                        "console", "GET /api/v2/agent/resolve?address=post:Blog/blog/hello-world"),
                new ShAgentEndpoint("POST", "/agent/batch",
                        "Write: N addressed ops (site.upsert, folder.upsert, post.upsert, "
                                + "post.move, post.publish, …) in one atomic call. Send dryRun to "
                                + "see the plan first. site.upsert makes a blank site and fills "
                                + "in an empty url/description/furl on one that exists (it never "
                                + "replaces a value somebody set) — that is "
                                + "step one and it is here, not on the console API. Rename or "
                                + "relocate with post.move, a section with folder.move — "
                                + "re-addressing an upsert creates a second post, and deleting the "
                                + "old folder takes everything inside it.",
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
                new ShAgentEndpoint("GET", "/agent/verify",
                        "Check your own work: required fields, dangling references, missing "
                                + "assets, duplicate URLs, empty folders, unresolved links. "
                                + "`ok:false` means at least one error; every finding carries a fix. "
                                + "Add checks=delivery to prove each published route by fetching it "
                                + "(needs deliveryProof).",
                        "console", "GET /api/v2/agent/verify?format=terse"),
                new ShAgentEndpoint("GET", "/agent/render",
                        "Structural digest of one page — heading outline, landmarks, links and "
                                + "images with alt, word count. Cheap proof the page came out "
                                + "right; ?url= digests what a visitor gets when fetching is enabled.",
                        "console", "GET /api/v2/agent/render?address=post:Blog/blog/hello-world"),
                new ShAgentEndpoint("GET", "/agent/changes",
                        "What changed while you were away: content transitions with an "
                                + "address each, and a `cursor` to pass back as since= so the "
                                + "next call is only the delta. Read it before trusting a "
                                + "cached context digest.",
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
                new ShAgentEndpoint("GET", "/cda/object/{folderId}/list",
                        "List a folder's children — subfolders + published posts.",
                        "token:READ", "GET /api/v2/cda/object/{folderId}/list?page=0&size=50"),
                new ShAgentEndpoint("GET", "/cda/object/{id}/path",
                        "Breadcrumb path for a folder or post — use it to build a path address.",
                        "token:READ", "GET /api/v2/cda/object/{id}/path"),
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
                new ShAgentEndpoint("POST", "/post-unified",
                        "Create a post. Body: {folderId, postTypeId, data, state}. Use state=DRAFT.",
                        "console", "POST /api/v2/post-unified"),
                new ShAgentEndpoint("PUT", "/post-unified/{id}",
                        "Update a post's data in place.",
                        "console", "PUT /api/v2/post-unified/{id}"),
                new ShAgentEndpoint("POST", "/post-unified/{id}/publish",
                        "Publish a draft — the only call that changes what visitors see.",
                        "console", "POST /api/v2/post-unified/{id}/publish"),
                new ShAgentEndpoint("POST", "/staticfile/upload",
                        "Upload a binary as a File post (multipart: file, folderId, createPost).",
                        "console", "POST /api/v2/staticfile/upload"),
                new ShAgentEndpoint("GET", "/site/{id}/export",
                        "Export a whole site as a portable exchange package (zip).",
                        "console", "GET /api/v2/site/{id}/export"));
    }

    /**
     * The opening move. Kept short on purpose: a long list is a plan the agent
     * has to evaluate, a short one is an instruction it can follow.
     */
    private List<String> startHere() {
        return List.of(
                "GET /api/v2/agent/context?format=terse — model, sitemap and conventions in one "
                        + "call; store the returned `digest` and compare it next session before "
                        + "re-reading anything",
                "address content as post:<site>/<url> or folder:<site>/<path>; "
                        + "GET /api/v2/agent/resolve turns one into an id when an endpoint needs it",
                "write with POST /api/v2/agent/batch (atomic, addressed, dryRun-able) rather than "
                        + "one id-keyed call per object — and before modelling a whole section by "
                        + "hand, check GET /api/v2/agent/blueprints for a package that already "
                        + "applies one",
                "then verify instead of asking a human: GET /api/v2/agent/verify?format=terse "
                        + "reports content and route defects with a fix each, and "
                        + "GET /api/v2/agent/render?address=... proves a page's structure",
                "over MCP: POST /mcp speaks the Model Context Protocol, with the same "
                        + "capabilities as tools (shio_context, shio_find, shio_read, shio_write, "
                        + "shio_apply, shio_publish, shio_assets, shio_changes, shio_verify, "
                        + "shio_digest)");
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
     */
    public String digest() {
        StringBuilder canonical = new StringBuilder(version()).append('|').append(PROTOCOL);
        features().forEach((key, value) -> canonical.append('|').append(key).append('=').append(value));
        return ShAgentContextService.digest(canonical.toString());
    }

    /** Exposed for tests: the feature map as an insertion-ordered copy. */
    Map<String, Boolean> featureSnapshot() {
        return new LinkedHashMap<>(features());
    }
}
