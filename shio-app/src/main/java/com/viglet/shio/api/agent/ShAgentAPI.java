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
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentMemory;
import com.viglet.shio.api.agent.memory.dto.ShAgentNote;
import com.viglet.shio.api.agent.memory.dto.ShAgentNoteRequest;
import com.viglet.shio.api.agent.verify.ShAgentRenderService;
import com.viglet.shio.api.agent.verify.ShAgentVerifyService;
import com.viglet.shio.api.agent.verify.ShAgentVerifyTerse;
import com.viglet.shio.api.agent.verify.dto.ShAgentDigest;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.ShBlueprintRegistry;
import com.viglet.shio.blueprint.ShBlueprintService;
import com.viglet.shio.blueprint.ShBlueprintWriteService;
import com.viglet.shio.blueprint.ShBlueprintTerse;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;
import com.viglet.shio.blueprint.dto.ShBlueprintDetail;
import com.viglet.shio.blueprint.dto.ShBlueprintRequest;

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

    /** {@code AGENTS.md} is Markdown; served as such so a browser or editor renders it. */
    private static final MediaType MEDIA_TYPE_MARKDOWN =
            MediaType.parseMediaType("text/markdown;charset=UTF-8");

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
    private final ShAgentMemoryService shAgentMemoryService;
    private final ShBlueprintRegistry shBlueprintRegistry;
    private final ShBlueprintService shBlueprintService;
    private final ShAgentBodyEtag shAgentBodyEtag;
    private final ShAgentProofService shAgentProofService;

    public ShAgentAPI(ShAgentManifestService shAgentManifestService,
                      ShAgentContextService shAgentContextService,
                      ShAddressResolver shAddressResolver,
                      ShAgentBatchService shAgentBatchService,
                      ShAgentDesiredStateService shAgentDesiredStateService,
                      ShAgentReadService shAgentReadService,
                      ShAgentVerifyService shAgentVerifyService,
                      ShAgentRenderService shAgentRenderService,
                      ShAgentChangesService shAgentChangesService,
                      ShAgentDiagnosticsService shAgentDiagnosticsService,
                      ShAgentMemoryService shAgentMemoryService,
                      ShBlueprintRegistry shBlueprintRegistry,
                      ShBlueprintService shBlueprintService,
                      ShAgentBodyEtag shAgentBodyEtag,
                      ShAgentProofService shAgentProofService) {
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
        this.shAgentMemoryService = shAgentMemoryService;
        this.shBlueprintRegistry = shBlueprintRegistry;
        this.shBlueprintService = shBlueprintService;
        this.shAgentBodyEtag = shAgentBodyEtag;
        this.shAgentProofService = shAgentProofService;
    }

    /**
     * The manifest (SH76), conditionally (SH116), and projectably (SH174).
     *
     * <p>It is deterministic and it changes only when the build or the operator's
     * configuration does, so after the first read it should cost a header for the
     * life of the deployment.</p>
     *
     * <p>The ETag covers the <em>representation</em>, not just the state, so {@code format} and
     * {@code include} are both mixed into it. Without that, a client holding the tag for
     * {@code ?include=features} would be told its cached two-line answer is still current when
     * it asked for the whole document — which is the same mistake SH116 already guards on the
     * context pack, one endpoint over.</p>
     *
     * @param include comma-separated sections
     *                ({@code identity,features,limits,endpoints,start-here}); omit for all.
     *                {@code features,limits} is the capability check, and it is the read this
     *                parameter exists for — confirming one boolean should not cost the
     *                twenty-nine-row endpoint index
     * @param format  {@code json} (default) or {@code terse}. An unknown value is a teaching
     *                400, the way {@code /agent/context} has always answered
     * @param ifNoneMatch a tag from a previous read (SH116)
     */
    @GetMapping("/manifest")
    @Operation(summary = "Describe this instance to an agent: version, capabilities, limits, endpoints")
    public ResponseEntity<Object> manifest(
            @RequestParam(name = "include", required = false) String include,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        Set<ShAgentManifestSection> sections = ShAgentManifestSection.parse(include);
        Representation representation = Representation.ofManifest(format);
        String etag = ShAgentEtag.of("manifest", shAgentManifestService.digest(),
                representation.name(), manifestSectionKey(sections));
        if (ShAgentEtag.matches(ifNoneMatch, etag)) {
            return ShAgentEtag.notModified(etag);
        }

        ShAgentManifest manifest =
                ShAgentManifestSection.project(shAgentManifestService.manifest(), sections);
        return representation == Representation.TERSE
                ? ShAgentEtag.ok(etag).contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentManifestTerse.render(manifest))
                : ShAgentEtag.ok(etag).body(manifest);
    }

    /**
     * A stable key for a section set. Declaration order, not request order — {@code
     * include=limits,features} and {@code include=features,limits} are the same representation
     * and must share a tag, or a caller alternating between two equivalent spellings would
     * revalidate on every call and never get a 304.
     */
    private static String manifestSectionKey(Set<ShAgentManifestSection> sections) {
        return sections.stream().map(ShAgentManifestSection::wireName).collect(Collectors.joining(","));
    }

    /**
     * The same, for the context pack's sections. Separately named rather than overloaded:
     * two {@code Set<…>} parameters erase to the same signature, so the compiler refuses the
     * pair outright.
     */
    private static String contextSectionKey(Set<ShAgentContextSection> sections) {
        return sections.stream().map(ShAgentContextSection::wireName).collect(Collectors.joining(","));
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
     * @param format  {@code json} (default), {@code terse} (SH78, ~⅓–½ fewer tokens),
     *                or {@code agents-md} (SH115 — the same pack rendered as project
     *                agent instructions, for a harness that loads a file rather than
     *                making a call)
     * @param ids     put the raw UUID back on every site and sitemap row (SH121). Off
     *                by default: rows are addressable without one, and the id is the
     *                most expensive field a row can carry
     * @param ifNoneMatch a tag from a previous read (SH116) — an unchanged pack in the
     *                    same representation answers {@code 304} and costs no tokens
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
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        Set<ShAgentContextSection> sections = ShAgentContextSection.parse(include);
        ShAgentContext pack = shAgentContextService.context(site, depth, budget, include, from, ids);
        Representation representation = Representation.of(format);
        // The digest covers the pack's content; the representation covers its bytes.
        // Both belong in the tag — see ShAgentEtag.
        //
        // `include` joined them in SH157, and it was a latent defect before that: a caller
        // asking for `include=model` and one asking for the whole pack were handed the same
        // tag off the same digest, so the first could revalidate against the second's cache
        // and keep a pack with no sitemap in it. SH174 guards the identical thing on the
        // manifest; this is the endpoint that already had the bug.
        String etag = ShAgentEtag.of("context", pack.digest(), representation.name(),
                Boolean.toString(ids), contextSectionKey(sections));
        if (ShAgentEtag.matches(ifNoneMatch, etag)) {
            return ShAgentEtag.notModified(etag);
        }
        return switch (representation) {
            case TERSE -> ShAgentEtag.ok(etag).contentType(MediaType.TEXT_PLAIN)
                    .body(ShAgentContextTerse.render(pack));
            case AGENTS_MD -> ShAgentEtag.ok(etag).contentType(MEDIA_TYPE_MARKDOWN)
                    .body(ShAgentAgentsMd.render(pack));
            case JSON -> ShAgentEtag.ok(etag).body(pack);
        };
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
     * @param after  the previous page's {@code cursor} — how a result past the cap is walked
     *               to the end instead of refused (SH146)
     * @param format {@code json} (default) or {@code terse}
     * @param ifNoneMatch a tag from a previous read (SH159) — an identical result answers
     *                    {@code 304} and costs no tokens. This is the read an agent repeats on
     *                    every iteration of a loop, so it is the one where revalidation
     *                    compounds
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
            @RequestParam(name = "after", required = false) String after,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        ShAgentFound found = shAgentReadService.find(site, type, folder, q, limit, ids, after);
        return isTerse(format)
                ? shAgentBodyEtag.terse("find", ShAgentReadTerse.found(found), ifNoneMatch)
                : shAgentBodyEtag.json("find", found, ifNoneMatch);
    }

    /**
     * Read one or more posts by address, optionally projected to named fields (SH123).
     *
     * @param address repeatable — several posts in one call
     * @param fields  comma-separated field names; omit for the whole document
     * @param ifNoneMatch a tag from a previous read (SH159). The projection is part of the
     *                    representation, so a two-field read never answers {@code 304} to a
     *                    caller holding the whole document — that falls out of tagging the
     *                    rendered bytes rather than a content digest
     */
    @GetMapping("/read")
    @Operation(summary = "Read posts by address, with an optional per-field projection")
    public ResponseEntity<Object> read(
            @RequestParam(name = "address") List<String> address,
            @RequestParam(name = "fields", required = false) String fields,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids,
            @RequestParam(name = "include", required = false) String include,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        // SH262 — `include=impact` attaches each template row's blast radius. A projection on this
        // read rather than a `GET /agent/impact`, because a new endpoint with no MCP tool is a
        // REST-only capability (P1) and the tool count is capped at 12 with 11 used; `shio_read`
        // carries it with no extra schema, and the subject of a read is already an address.
        List<ShAgentDoc> docs = shAgentReadService.read(address, split(fields), ids,
                includes(include, "impact"));
        return isTerse(format)
                ? shAgentBodyEtag.terse("read", ShAgentReadTerse.docs(docs), ifNoneMatch)
                : shAgentBodyEtag.json("read", docs, ifNoneMatch);
    }

    /**
     * Apply N content operations as one transaction (SH81).
     *
     * <p>Every op names its target by address, so a twelve-page structure is one call
     * and no UUID has to survive between steps. {@code dryRun} in the body returns the
     * same report and writes nothing.</p>
     *
     * @param ids include object ids in the result rows (SH121 default: off)
     * @param verify SH157 — run the SH99 lint over exactly the addresses this run touched and
     *               return the findings in {@code proof}. It removes the closing turn of every
     *               write-then-check loop, and the report is better than a separate
     *               {@code /agent/verify} call because it is scoped to the change rather than
     *               to the site. Opt-in: a caller that does not want it should not pay for it,
     *               and it is skipped on a {@code dryRun}, where there is nothing to prove
     * @param digest SH194 — also return the two SH187 hashes for each page this run <b>affected</b>,
     *               which for a write to a shared layout or region is not the address written but
     *               every page that renders through it (SH193's graph). It closes the appearance
     *               loop the way {@code verify} closed the correctness one: upsert, render, compare
     *               becomes one call. Opt-in and capped, because each row is a real render
     */
    @PostMapping("/batch")
    @Operation(summary = "Apply N addressed content ops atomically; dryRun reports without writing")
    public ResponseEntity<Object> batch(@RequestBody ShAgentBatch batch,
                                        @RequestParam(name = "ids", required = false,
                                                defaultValue = "false") boolean ids,
                                        @RequestParam(name = "verify", required = false,
                                                defaultValue = "false") boolean verify,
                                        @RequestParam(name = "digest", required = false,
                                                defaultValue = "false") boolean digest,
                                        @RequestParam(name = "format", required = false)
                                        String format,
                                        Principal principal) {
        return rendered(shAgentProofService.attach(
                shAgentBatchService.run(batch, ids, name(principal)), verify, digest), format);
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

    /**
     * Make the desired-state document true, atomically (SH82).
     *
     * @param verify SH157 — see {@link #batch}. Worth more here than anywhere: an apply is
     *               the call that writes a whole section, so the report it would otherwise
     *               take a second round trip to fetch is the one most likely to be needed
     * @param digest SH194 — see {@link #batch}
     */
    @PostMapping("/apply")
    @Operation(summary = "Apply a desired-state content document atomically")
    public ResponseEntity<Object> apply(@RequestBody ShAgentDesiredState desired,
                                        @RequestParam(name = "ids", required = false,
                                                defaultValue = "false") boolean ids,
                                        @RequestParam(name = "verify", required = false,
                                                defaultValue = "false") boolean verify,
                                        @RequestParam(name = "digest", required = false,
                                                defaultValue = "false") boolean digest,
                                        @RequestParam(name = "format", required = false)
                                        String format,
                                        Principal principal) {
        return rendered(shAgentProofService.attach(
                shAgentDesiredStateService.apply(desired, ids, name(principal)), verify, digest),
                format);
    }

    /**
     * A change set in the caller's encoding (SH157).
     *
     * <p>The write surface was the one part of this namespace with no terse form, which was an
     * oversight rather than a decision — {@code ShAgentReadTerse.changes} has rendered change
     * sets for MCP since SH83, and only the REST endpoints could not ask for it. It matters
     * more now that a write can carry a lint report: folding the closing turn in is supposed
     * to be a saving, and paying for the report in JSON while the separate {@code verify} it
     * replaced could be terse would trade one target for the other.</p>
     */
    private ResponseEntity<Object> rendered(ShAgentChangeSet changes, String format) {
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentReadTerse.changes(changes))
                : ResponseEntity.ok(changes);
    }

    /**
     * The blueprint catalogue (SH92) — the appliable starting points this instance carries.
     *
     * <p>Deliberately cheap to read: a name, a version, what applying it gives you, and
     * the parameter names. The full schema of the one the caller picks is
     * {@code /agent/blueprints/{name}}, so choosing between five packages does not cost
     * five schemas.</p>
     */
    @GetMapping("/blueprints")
    @Operation(summary = "Appliable starting points: blueprint packages this instance can apply")
    public ResponseEntity<Object> blueprints(
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        List<ShBlueprint.Summary> summaries = shBlueprintRegistry.summaries();
        // Packages load from the classpath (SH92), so this catalogue cannot change
        // without a redeploy — the strongest 304 candidate on the whole surface.
        String etag = ShAgentEtag.of("blueprints", catalogueDigest(summaries),
                isTerse(format) ? "terse" : "json");
        if (ShAgentEtag.matches(ifNoneMatch, etag)) {
            return ShAgentEtag.notModified(etag);
        }
        return isTerse(format)
                ? ShAgentEtag.ok(etag).contentType(MediaType.TEXT_PLAIN)
                        .body(ShBlueprintTerse.catalogue(summaries))
                : ShAgentEtag.ok(etag).body(summaries);
    }

    /**
     * One blueprint in detail: its manifest (including the {@code params} schema a caller
     * has to satisfy), what it provisions, and — with {@code include=files} — the
     * front-end files it carries, which the server never writes.
     */
    @GetMapping("/blueprints/{name}")
    @Operation(summary = "One blueprint: its parameters, what it provisions, and its carried files")
    public ResponseEntity<Object> blueprint(
            @PathVariable(name = "name") String name,
            @RequestParam(name = "include", required = false) String include,
            @RequestParam(name = "format", required = false) String format) {

        ShBlueprint blueprint = shBlueprintRegistry.require(name);
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShBlueprintTerse.detail(blueprint))
                : ResponseEntity.ok(ShBlueprintDetail.of(blueprint,
                        split(include).contains("files")));
    }

    /**
     * Apply a blueprint to a site (SH93) — the verb the whole block exists for.
     *
     * <p>{@code dryRun} in the body returns the complete plan and writes nothing: the
     * post-types it would create, the SH82 change set for every folder and page, and the
     * front-end files the caller still has to write itself. A real run additionally
     * reports whether the package kept its own promise (SH94).</p>
     */
    @PostMapping("/blueprints/{name}/apply")
    @Operation(summary = "Apply a blueprint into a site; dryRun returns the whole plan and writes nothing")
    public ShBlueprintApplied applyBlueprint(@PathVariable(name = "name") String name,
                                             @RequestBody(required = false) ShBlueprintRequest request,
                                             Principal principal) {
        ShBlueprintRequest body = request == null
                ? new ShBlueprintRequest(null, null, false) : request;
        // SH151/SH152 — both opt-in, both named in the plan before they happen.
        var options = new ShBlueprintWriteService.Options(body.createSite(), body.mergePostTypes());
        return body.dryRun()
                ? shBlueprintService.plan(name, body.site(), body.params(), name(principal), options)
                : shBlueprintService.apply(name, body.site(), body.params(), name(principal), options);
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
     * <p>{@code folder}, {@code address} and {@code since} narrow the run to what was just
     * touched (SH143) — the full pass is right for CI and a hundred times more work than the
     * call an agent makes after writing three pages. A narrowed run sets {@code partial} and
     * describes itself in {@code scope}, because a subset that reads as a full pass is worse
     * than no report at all.</p>
     *
     * @param site    scope to one site; omit for every readable site
     * @param checks  comma-separated groups ({@code content,routes,diagnostics}); omit for all
     * @param limit   findings to return — the counts always describe the full run
     * @param folder  a {@code folder:} address: this folder and its descendants
     * @param address comma-separated {@code post:} addresses
     * @param since   an SH88 cursor: only what changed after it
     */
    @GetMapping("/verify")
    @Operation(summary = "Lint the content and its routes: a machine-readable report with a fix per finding")
    public ResponseEntity<Object> verify(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "checks", required = false) String checks,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "folder", required = false) String folder,
            @RequestParam(name = "address", required = false) String address,
            @RequestParam(name = "since", required = false) String since,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentReport report = shAgentVerifyService.verify(site, checks, limit, folder, address,
                since);
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
     * @param ifNoneMatch a tag from a previous read (SH159). This is the poll — "has anything
     *                    happened since I last looked" — and the honest answer is very often
     *                    "no", so a {@code 304} is what makes asking cheap enough to do often.
     *                    <b>It revalidates in cursor mode, not in window mode</b>, and that
     *                    asymmetry is correct rather than a gap: without {@code since} the
     *                    response's own cursor is the moving edge of the feed, so two calls a
     *                    second apart really are different answers. With {@code since} — the
     *                    way SH88 intends the feed to be walked, carrying the previous
     *                    cursor back — an unchanged range is byte-identical and free. A
     *                    repeating incident (SH144) raises its count and correctly does not
     *                    match; that is the stream saying it is still happening
     */
    @GetMapping("/changes")
    @Operation(summary = "Content changes since a cursor: what a human (or another session) did")
    public ResponseEntity<Object> changes(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "since", required = false) String since,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        ShAgentChanges changes = shAgentChangesService.changes(site, since, limit, ids);
        return isTerse(format)
                ? shAgentBodyEtag.terse("changes", ShAgentChangesTerse.render(changes), ifNoneMatch)
                : shAgentBodyEtag.json("changes", changes, ifNoneMatch);
    }

    /**
     * What this instance has been told to remember (SH114).
     *
     * <p>The same notes the context pack already carries, on their own, for the
     * cases the pack is the wrong shape for: auditing what an agent has been
     * writing, or rendering them somewhere else. Reading them here is never
     * <em>necessary</em> — an agent that read the context has them already.</p>
     *
     * @param site  one site's notes plus the instance-wide ones; omit for all
     * @param limit rows to return; {@code found} always describes the full set
     */
    @GetMapping("/memory")
    @Operation(summary = "Remembered decisions and conventions — what earlier sessions worked out")
    public ShAgentMemory memory(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return shAgentMemoryService.memory(site, limit);
    }

    /**
     * Remember one decision or convention (SH114), idempotently by {@code (scope, key)}.
     *
     * <p>{@code PUT} rather than {@code POST} because re-stating a convention must
     * rewrite one note, not add a second: the next session pays for every near-duplicate
     * it has to reconcile. The author is taken from the credential, never from the body.</p>
     */
    @PutMapping("/memory")
    @Operation(summary = "Remember a decision or convention; re-using a key rewrites that note")
    public ShAgentNote remember(@RequestBody ShAgentNoteRequest request, Principal principal) {
        return shAgentMemoryService.remember(request, name(principal));
    }

    /**
     * Forget one note. Idempotent — forgetting what was never remembered is a
     * {@code 204}, because a retry that reports failure is a retry nobody makes.
     */
    @DeleteMapping("/memory")
    @Operation(summary = "Forget one remembered note")
    public ResponseEntity<Void> forget(
            @RequestParam(name = "key") String key,
            @RequestParam(name = "scope", required = false) String scope) {
        shAgentMemoryService.forget(scope, key);
        return ResponseEntity.noContent().build();
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

    /**
     * Name + version of every package. Enough to notice a redeploy that changed a
     * package and cheap enough to compute per request; hashing the whole catalogue
     * would tie the tag to fields (a description's wording) that a caller choosing
     * between packages already re-reads through {@code /blueprints/{name}}.
     */
    private static String catalogueDigest(List<ShBlueprint.Summary> summaries) {
        StringBuilder canonical = new StringBuilder();
        for (ShBlueprint.Summary summary : summaries) {
            canonical.append(summary.name()).append('@').append(summary.version()).append('|');
        }
        return ShAgentContextService.digest(canonical.toString());
    }

    /**
     * The renderings of a context pack.
     *
     * <p>An enum rather than a chain of string comparisons because the value is now
     * load-bearing twice over: it picks the renderer <em>and</em> it discriminates the
     * SH116 entity tag. Two representations sharing a tag is the bug this shape makes
     * hard to write.</p>
     */
    private enum Representation {
        JSON, TERSE, AGENTS_MD;

        static Representation of(String format) {
            if (format == null || format.isBlank()) {
                return JSON;
            }
            return switch (format.trim().toLowerCase(Locale.ROOT)) {
                case "json" -> JSON;
                case "terse" -> TERSE;
                case "agents-md", "agentsmd" -> AGENTS_MD;
                // Not a silent fallback to JSON. With two renderings a typo cost a
                // slightly wrong payload; with three, `agent-md` would quietly return
                // JSON and the caller would conclude the feature does not exist —
                // which is the probing cost this whole surface exists to remove (P6).
                default -> throw new UnknownFormatException(format,
                        "The context pack has three renderings.",
                        List.of("json", "terse", "agents-md"),
                        "Use terse for an agent, json for a program, agents-md to write "
                                + "project instructions to a file.");
            };
        }

        /**
         * The manifest's renderings (SH174) — {@code json} and {@code terse}, and nothing else.
         *
         * <p>Separate from {@link #of(String)} because the allowed set genuinely differs:
         * {@code agents-md} renders a <em>context pack</em> as project instructions, and there
         * is no such artifact for a capability document. Accepting it here to reuse one parser
         * would mean either producing something nobody asked for or a 500 in the renderer.</p>
         *
         * <p>Until SH174 this endpoint ignored {@code format=} entirely and returned full JSON
         * without comment, while its immediate neighbour taught a 400 for the same mistake. That
         * is the failure mode SH80 exists to prevent, and it is worse here than anywhere else on
         * the surface: the caller believes it asked for the compact form, gets the 2169-token
         * one, and is billed for the difference on the first call of every session.</p>
         */
        static Representation ofManifest(String format) {
            if (format == null || format.isBlank()) {
                return JSON;
            }
            return switch (format.trim().toLowerCase(Locale.ROOT)) {
                case "json" -> JSON;
                case "terse" -> TERSE;
                default -> throw new UnknownFormatException(format,
                        "The manifest has two renderings. `agents-md` renders the context pack, "
                                + "not this document.",
                        List.of("json", "terse"),
                        "Use terse for an agent, json for a program. To drop the endpoint index "
                                + "entirely, ask for `?include=features,limits`.");
            };
        }
    }

    /** A 400 that names the renderings rather than making the caller guess (SH80). */
    public static class UnknownFormatException extends ShTeachingException {

        public UnknownFormatException(String sent, String detail, List<String> allowed, String fix) {
            super(ShProblem.badRequest("unknown-format", "Unknown format '" + sent + "'")
                    .withDetail(detail)
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix(fix)
                    .withExample("?format=terse"));
        }
    }

    /** Comma-separated list → trimmed values; blank/absent → empty. */
    /**
     * Whether a comma-separated {@code include=} names a section (SH262).
     *
     * <p>Deliberately not a teaching 400 on an unknown value, unlike {@code format=} and
     * {@code checks=}: those <em>select</em> what the response is, so a typo silently returns the
     * wrong thing; this one only <em>adds</em> a section, so a typo returns the ordinary read. The
     * cost of being wrong is the caller not getting an extra it asked for, which its own code will
     * notice, rather than a plausible answer to a different question.</p>
     */
    private static boolean includes(String include, String section) {
        if (include == null || include.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(include.split(","))
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(section) || value.equalsIgnoreCase("all"));
    }

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
