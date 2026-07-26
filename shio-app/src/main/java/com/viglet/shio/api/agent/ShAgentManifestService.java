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
        // Block K: the agent can check its own work. contentLint was reported false
        // through the whole of Block H precisely so that this flag flipping would mean
        // something — it is now backed by real rules, not a stub that always passes.
        features.put("contentLint", true);
        features.put("routeCheck", true);
        features.put("renderDigest", true);
        features.put("diagnostics", true);
        // Whether ?url= may fetch a delivery URL. Configured, not compiled: this one
        // depends on an operator's decision, and probing it costs a refusal.
        features.put("renderFetch", shAgentProperties.getRender().isAllowFetch());

        // Declared-but-unshipped (Block H–J). Reported false so a caller does not probe.
        features.put("blueprints", false);
        features.put("contentAsFiles", false);
        // SH102's visual snapshot. Deliberately last in the block: a PNG is for the
        // human handoff, and reporting it here as available would invite an agent to
        // spend three orders of magnitude more tokens than the digest costs.
        features.put("visualSnapshot", false);
        // SH88's cursor. shio_changes reads a window today; "everything since I last
        // looked" is not expressible yet.
        features.put("changeCursor", false);

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
                shCdaProperties.getCache().getMaxAgeSeconds());
    }

    /**
     * The curated index. Order is intentional — discovery, then reads, then the
     * content model, then writes — so reading it top-to-bottom is a usable
     * walkthrough rather than an alphabetical dump.
     */
    private List<ShAgentEndpoint> endpoints() {
        return List.of(
                new ShAgentEndpoint("GET", "/agent/manifest",
                        "This document: version, capabilities, limits, endpoint index.",
                        "console", "GET /api/v2/agent/manifest"),
                new ShAgentEndpoint("GET", "/agent/context",
                        "Start here: content model + sitemap by path + conventions in one call. "
                                + "Add format=terse for the compact encoding, and re-check `digest` "
                                + "next session instead of re-reading.",
                        "console", "GET /api/v2/agent/context?format=terse"),
                new ShAgentEndpoint("GET", "/agent/resolve",
                        "Turn a content address into an object id — post:<site>/<url>, "
                                + "folder:<site>/<path>, site:<name> or id:<uuid>. Build the "
                                + "address from a context row; you never need to remember a UUID.",
                        "console", "GET /api/v2/agent/resolve?address=post:Blog/blog/hello-world"),
                new ShAgentEndpoint("POST", "/agent/batch",
                        "Write: N addressed ops (folder.upsert, post.upsert, post.move, "
                                + "post.publish, …) in one atomic call. Send dryRun to see the "
                                + "plan first. Rename or relocate with post.move — re-addressing "
                                + "an upsert creates a second post.",
                        "console", "POST /api/v2/agent/batch"),
                new ShAgentEndpoint("POST", "/agent/plan",
                        "Diff a desired-state content document against this instance. "
                                + "Writes nothing; changed=0 means no drift.",
                        "console", "POST /api/v2/agent/plan"),
                new ShAgentEndpoint("POST", "/agent/apply",
                        "Make that document true, atomically. Re-applying it is a no-op.",
                        "console", "POST /api/v2/agent/apply"),
                new ShAgentEndpoint("GET", "/agent/find",
                        "Find posts by site / type / folder subtree / text. Returns addresses "
                                + "and titles, never bodies — read only what you pick.",
                        "console", "GET /api/v2/agent/find?site=Blog&type=Article"),
                new ShAgentEndpoint("GET", "/agent/read",
                        "Read posts by address (drafts preferred). Repeat ?address= for several, "
                                + "and pass ?fields= to fetch only the fields you need.",
                        "console", "GET /api/v2/agent/read?address=post:Blog/blog/hello-world"),
                new ShAgentEndpoint("GET", "/agent/verify",
                        "Check your own work: required fields, dangling references, missing "
                                + "assets, duplicate URLs, empty folders, unresolved links. "
                                + "`ok:false` means at least one error; every finding carries a fix.",
                        "console", "GET /api/v2/agent/verify?format=terse"),
                new ShAgentEndpoint("GET", "/agent/render",
                        "Structural digest of one page — heading outline, landmarks, links and "
                                + "images with alt, word count. Cheap proof the page came out "
                                + "right; ?url= digests what a visitor gets when fetching is enabled.",
                        "console", "GET /api/v2/agent/render?address=post:Blog/blog/hello-world"),
                new ShAgentEndpoint("GET", "/agent/diagnostics",
                        "What failed after you moved on: webhook deliveries, image transforms, "
                                + "scheduled publishes, server errors. One call instead of logs.",
                        "console", "GET /api/v2/agent/diagnostics"),
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
                new ShAgentEndpoint("GET", "/cda/post/by-url",
                        "Resolve a post by site + friendly URL — the cheapest addressing available today.",
                        "token:READ", "GET /api/v2/cda/post/by-url?siteId={siteId}&url=/blog/hello-world"),
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
                        + "one id-keyed call per object",
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

    /** Exposed for tests: the feature map as an insertion-ordered copy. */
    Map<String, Boolean> featureSnapshot() {
        return new LinkedHashMap<>(features());
    }
}
