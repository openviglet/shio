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
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.agent.dto.ShAgentResolved;
import com.viglet.shio.api.agent.diagnostics.ShAgentDiagnosticsService;
import com.viglet.shio.api.agent.diagnostics.dto.ShAgentDiagnostics;
import com.viglet.shio.api.agent.verify.ShAgentRenderService;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.ShAgentVerifyTerse;
import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;

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
    private final ShAgentVerifyService shAgentVerifyService;
    private final ShAgentRenderService shAgentRenderService;
    private final ShAgentChangesService shAgentChangesService;
    private final ShAgentDiagnosticsService shAgentDiagnosticsService;

    public ShAgentAPI(ShAgentManifestService shAgentManifestService,
                      ShAgentContextService shAgentContextService,
                      ShAddressResolver shAddressResolver,
                      ShAgentBatchService shAgentBatchService,
                      ShAgentDesiredStateService shAgentDesiredStateService,
                      ShAgentReadService shAgentReadService,
                      ShAgentVerifyService shAgentVerifyService,
                      ShAgentRenderService shAgentRenderService,
                      ShAgentChangesService shAgentChangesService,
                      ShAgentDiagnosticsService shAgentDiagnosticsService) {
        this.shAgentManifestService = shAgentManifestService;
        this.shAgentContextService = shAgentContextService;
        this.shAddressResolver = shAddressResolver;
        this.shAgentBatchService = shAgentBatchService;
        this.shAgentDesiredStateService = shAgentDesiredStateService;
        this.shAgentReadService = shAgentReadService;
        this.shAgentVerifyService = shAgentVerifyService;
        this.shAgentRenderService = shAgentRenderService;
        this.shAgentChangesService = shAgentChangesService;
        this.shAgentDiagnosticsService = shAgentDiagnosticsService;
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

    /**
     * Verify the content: the agent's own correctness check (SH99 + SH100).
     *
     * <p>Every finding carries an address and a fix, and {@code ok} is the single value
     * a caller branches on — it is false when at least one <em>error</em>-severity
     * finding exists. Warnings are reported and counted but do not fail the report,
     * because a check that fails on "this folder is empty" would train callers to ignore
     * the answer.</p>
     *
     * @param site   scope to one site; omit for every readable site
     * @param checks comma-separated groups ({@code content,routes,diagnostics}); omit for all
     * @param limit  findings to return — the counts always describe the full run
     */
    @GetMapping("/verify")
    @Operation(summary = "Lint the content and its routes: a machine-readable report with a fix per finding")
    public ResponseEntity<Object> verify(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "checks", required = false) String checks,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentReport report = shAgentVerifyService.verify(site, checks, limit);
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentVerifyTerse.report(report))
                : ResponseEntity.ok(report);
    }

    /**
     * A page's structural digest (SH101) — heading outline, landmarks, link and image
     * inventory with alt text, word count.
     *
     * <p>Exactly one target. {@code address} digests the markup Shio holds, which needs
     * no front end running and is the useful call right after a write; {@code url}
     * fetches a delivery URL and digests what a visitor actually gets, and is off unless
     * {@code shio.agent.render.allow-fetch} is set (an endpoint that fetches on the
     * server's behalf needs an operator's consent, not a caller's).</p>
     */
    @GetMapping("/render")
    @Operation(summary = "Structural digest of a page: outline, landmarks, links, images — not pixels")
    public ResponseEntity<Object> render(
            @RequestParam(name = "address", required = false) String address,
            @RequestParam(name = "url", required = false) String url,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentDigest digest = shAgentRenderService.digest(address, url);
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentVerifyTerse.digest(digest))
                : ResponseEntity.ok(digest);
    }

    /**
     * What has changed, resumably (SH88).
     *
     * <p>Without {@code since} this is a window — the most recent entries, and a starting
     * position. With it, everything after that position, oldest first. The {@code cursor}
     * in the response is always the next {@code since}, which is what makes "what did the
     * human change while I was away" a single call rather than a guess at a window size.</p>
     *
     * @param site   scope to one site; omit for every readable site
     * @param since  a cursor from a previous call
     * @param limit  entries to return
     * @param ids    include object ids (SH121 default: off)
     */
    @GetMapping("/changes")
    @Operation(summary = "Content changes since a cursor: what a human (or another session) did")
    public ResponseEntity<Object> changes(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "since", required = false) String since,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentChanges changes = shAgentChangesService.changes(site, since, limit, ids);
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentChangesTerse.render(changes))
                : ResponseEntity.ok(changes);
    }

    /**
     * What has failed asynchronously (SH103): webhook deliveries, image transforms,
     * scheduled publishes, server-side errors — one call instead of grepping
     * {@code store/logs}.
     *
     * <p>The window is in memory and bounded, so the response states when it started
     * collecting and what it cannot see. An empty report means "nothing since then",
     * which is a different claim from "nothing ever".</p>
     */
    @GetMapping("/diagnostics")
    @Operation(summary = "Recent asynchronous failures: webhooks, transforms, schedules, 5xx")
    public ResponseEntity<Object> diagnostics(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(limit);
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentVerifyTerse.diagnostics(diagnostics))
                : ResponseEntity.ok(diagnostics);
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
