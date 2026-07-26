/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.agent.dto.ShAgentResolved;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The agent surface (Block H) — endpoints shaped for a coding agent rather than
 * for a browser or a page renderer.
 *
 * <p>This namespace is a <em>shape</em> over the existing CDA and console
 * services, never a third contract (design law P10): it holds no business logic
 * of its own. What it adds is discoverability and token economy — one call that
 * answers what would otherwise take a session of probing.</p>
 *
 * <p>Authentication: any authenticated principal, so both a console session /
 * HTTP Basic (the {@code shio} CLI) and a CDA {@code Key} token can read it. A
 * dedicated {@code AGENT} token scope with its own budget arrives with SH106.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/agent")
@Tag(name = "Agent", description = "Agent-facing surface — manifest, context, diagnostics")
public class ShAgentAPI {

    private final ShAgentManifestService shAgentManifestService;
    private final ShAgentContextService shAgentContextService;
    private final ShAddressResolver shAddressResolver;

    public ShAgentAPI(ShAgentManifestService shAgentManifestService,
                      ShAgentContextService shAgentContextService,
                      ShAddressResolver shAddressResolver) {
        this.shAgentManifestService = shAgentManifestService;
        this.shAgentContextService = shAgentContextService;
        this.shAddressResolver = shAddressResolver;
    }

    @GetMapping("/manifest")
    @Operation(summary = "Describe this instance to an agent: version, capabilities, limits, endpoints")
    public ShAgentManifest manifest() {
        return shAgentManifestService.manifest();
    }

    /**
     * The context pack (SH77). Every parameter is optional — the single-site case,
     * which is the common one, is a bare {@code GET /api/v2/agent/context}.
     *
     * @param site    site id or name; omit for every readable site
     * @param depth   folder-tree depth to walk
     * @param budget  estimated-token ceiling; {@code 0} = unbounded
     * @param include comma-separated sections ({@code instance,sites,model,sitemap,conventions})
     * @param from    resume cursor — the {@code budget.truncatedAfter} of a prior call
     * @param format  {@code json} (default) or {@code terse} (SH78, ~⅓–½ fewer tokens)
     */
    @GetMapping("/context")
    @Operation(summary = "One call that replaces a session: content model, sitemap by path, conventions")
    public ResponseEntity<Object> context(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "depth", required = false) Integer depth,
            @RequestParam(name = "budget", required = false) Integer budget,
            @RequestParam(name = "include", required = false) String include,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentContext pack = shAgentContextService.context(site, depth, budget, include, from);
        if (isTerse(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(ShAgentContextTerse.render(pack));
        }
        return ResponseEntity.ok(pack);
    }

    /**
     * Resolve a content address (SH79) to the object it names.
     *
     * <p>The bridge between path addressing and today's id-keyed endpoints: the
     * context pack gives an agent what it needs to <em>build</em> an address, and this
     * turns one back into the id that {@code /api/v2/post-unified/{id}} and friends
     * still require. Once the agent write surfaces land (SH81/SH82) they accept the
     * address directly and this becomes a convenience rather than a step.</p>
     *
     * @param address e.g. {@code post:Blog/blog/hello-world}, {@code folder:Blog/blog},
     *                {@code site:Blog}, {@code id:<uuid>}, or the lenient
     *                {@code site:Blog/blog/hello-world}
     */
    @GetMapping("/resolve")
    @Operation(summary = "Resolve a content address (post:/folder:/site:/id:) to its object")
    public ShAgentResolved resolve(@RequestParam(name = "address") String address) {
        return shAddressResolver.resolve(address);
    }

    private static boolean isTerse(String format) {
        return format != null && "terse".equalsIgnoreCase(format.trim());
    }
}
