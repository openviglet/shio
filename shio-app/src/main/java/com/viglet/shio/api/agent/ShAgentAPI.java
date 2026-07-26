/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;
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
    private final ShAgentBatchService shAgentBatchService;
    private final ShAgentDesiredStateService shAgentDesiredStateService;
    private final ShAgentReadService shAgentReadService;

    public ShAgentAPI(ShAgentManifestService shAgentManifestService,
                      ShAgentContextService shAgentContextService,
                      ShAddressResolver shAddressResolver,
                      ShAgentBatchService shAgentBatchService,
                      ShAgentDesiredStateService shAgentDesiredStateService,
                      ShAgentReadService shAgentReadService) {
        this.shAgentManifestService = shAgentManifestService;
        this.shAgentContextService = shAgentContextService;
        this.shAddressResolver = shAddressResolver;
        this.shAgentBatchService = shAgentBatchService;
        this.shAgentDesiredStateService = shAgentDesiredStateService;
        this.shAgentReadService = shAgentReadService;
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
     * @param ids     put the raw UUID back on every site and sitemap row (SH121). Off
     *                by default: rows are addressable without one, and the id is the
     *                most expensive field a row can carry
     */
    @GetMapping("/context")
    @Operation(summary = "One call that replaces a session: content model, sitemap by path, conventions")
    public ResponseEntity<Object> context(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "depth", required = false) Integer depth,
            @RequestParam(name = "budget", required = false) Integer budget,
            @RequestParam(name = "include", required = false) String include,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "format", required = false) String format,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids) {

        ShAgentContext pack = shAgentContextService.context(site, depth, budget, include, from, ids);
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

    /**
     * Find posts — addresses and titles, never bodies (SH83).
     *
     * @param site   site name/id/address; omit for every readable site
     * @param type   post-type name
     * @param folder folder path or address; matches the folder and everything beneath it
     * @param q      full-text over title, summary and content
     * @param limit  row cap
     * @param ids    include object ids (SH121 default: off)
     * @param format {@code json} (default) or {@code terse}
     */
    @GetMapping("/find")
    @Operation(summary = "Find posts by site / type / folder / text — identifiers, not bodies")
    public ResponseEntity<Object> find(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "folder", required = false) String folder,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentFound found = shAgentReadService.find(site, type, folder, q, limit, ids);
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentReadTerse.found(found))
                : ResponseEntity.ok(found);
    }

    /**
     * Read one or more posts by address, optionally projected to named fields (SH123).
     *
     * @param address repeatable — several posts in one call
     * @param fields  comma-separated field names; omit for the whole document
     */
    @GetMapping("/read")
    @Operation(summary = "Read posts by address, with an optional per-field projection")
    public ResponseEntity<Object> read(
            @RequestParam(name = "address") List<String> address,
            @RequestParam(name = "fields", required = false) String fields,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids,
            @RequestParam(name = "format", required = false) String format) {

        List<ShAgentDoc> docs = shAgentReadService.read(address, split(fields), ids);
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentReadTerse.docs(docs))
                : ResponseEntity.ok(docs);
    }

    /**
     * Apply N content operations as one transaction (SH81).
     *
     * <p>Every op names its target by address, so a twelve-page structure is one call
     * and no UUID has to survive between steps. {@code dryRun} in the body returns the
     * same report and writes nothing.</p>
     *
     * @param ids include object ids in the result rows (SH121 default: off)
     */
    @PostMapping("/batch")
    @Operation(summary = "Apply N addressed content ops atomically; dryRun reports without writing")
    public ShAgentChangeSet batch(@RequestBody ShAgentBatch batch,
                                  @RequestParam(name = "ids", required = false,
                                          defaultValue = "false") boolean ids,
                                  Principal principal) {
        return shAgentBatchService.run(batch, ids, name(principal));
    }

    /**
     * What applying this desired-state document would change (SH82). Never writes.
     *
     * <p>An empty {@code changed} count is the content drift guard — the same green
     * light {@code shio push --check} gives for post-types (SH65).</p>
     */
    @PostMapping("/plan")
    @Operation(summary = "Diff a desired-state content document against the instance; writes nothing")
    public ShAgentChangeSet plan(@RequestBody ShAgentDesiredState desired,
                                 @RequestParam(name = "ids", required = false,
                                         defaultValue = "false") boolean ids,
                                 Principal principal) {
        return shAgentDesiredStateService.plan(desired, ids, name(principal));
    }

    /** Make the desired-state document true, atomically (SH82). */
    @PostMapping("/apply")
    @Operation(summary = "Apply a desired-state content document atomically")
    public ShAgentChangeSet apply(@RequestBody ShAgentDesiredState desired,
                                  @RequestParam(name = "ids", required = false,
                                          defaultValue = "false") boolean ids,
                                  Principal principal) {
        return shAgentDesiredStateService.apply(desired, ids, name(principal));
    }

    private static boolean isTerse(String format) {
        return format != null && "terse".equalsIgnoreCase(format.trim());
    }

    /** Comma-separated list → trimmed values; blank/absent → empty. */
    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static String name(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}
