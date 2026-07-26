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

    public ShAgentManifestService(ShAppProperties shAppProperties,
                                 ShCdaProperties shCdaProperties,
                                 ShWebhookProperties shWebhookProperties,
                                 ShScheduleProperties shScheduleProperties,
                                 ShImageTransformProperties shImageTransformProperties) {
        this.shAppProperties = shAppProperties;
        this.shCdaProperties = shCdaProperties;
        this.shWebhookProperties = shWebhookProperties;
        this.shScheduleProperties = shScheduleProperties;
        this.shImageTransformProperties = shImageTransformProperties;
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

        // Declared-but-unshipped (Block H–J). Reported false so a caller does not probe.
        features.put("batchWrites", false);
        features.put("blueprints", false);
        features.put("contentAsFiles", false);
        features.put("mcp", false);

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
                "GET /api/v2/agent/context?format=terse — model, sitemap and conventions in one call",
                "store the returned `digest`; next session, compare it before re-reading anything",
                "address content as post:<site>/<url> or folder:<site>/<path>; "
                        + "GET /api/v2/agent/resolve turns one into an id when an endpoint needs it");
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
