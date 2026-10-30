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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tools.jackson.databind.JsonNode;
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
import com.viglet.shio.api.agent.dto.ShAgentRunRow;
import com.viglet.shio.api.agent.dto.ShAgentVerdict;
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
import com.viglet.shio.api.query.ShContentQuery;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.history.ShHistoryFilter;
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
    /** SH997 — the site's publish gates, read and declared. */
    private final com.viglet.shio.api.gate.ShAgentGateApiService shAgentGateApiService;
    private final ShAgentRunService shAgentRunService;
    private final ShAgentVerdictService shAgentVerdictService;
    private final ShBlueprintRegistry shBlueprintRegistry;
    private final ShBlueprintService shBlueprintService;
    private final ShAgentBodyEtag shAgentBodyEtag;
    private final ShAgentProofService shAgentProofService;
    /** SH1063 — the routing table every index surface reads. */
    private final com.viglet.shio.turing.ShTuringIndexStatusService shTuringIndexStatusService;

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
                      ShAgentProofService shAgentProofService,
                      com.viglet.shio.api.gate.ShAgentGateApiService shAgentGateApiService,
                      ShAgentRunService shAgentRunService,
                      ShAgentVerdictService shAgentVerdictService,
                      com.viglet.shio.turing.ShTuringIndexStatusService shTuringIndexStatusService) {
        this.shTuringIndexStatusService = shTuringIndexStatusService;
        this.shAgentGateApiService = shAgentGateApiService;
        this.shAgentRunService = shAgentRunService;
        this.shAgentVerdictService = shAgentVerdictService;
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

        // SH979 — the surfaces are attached only when the caller named the section, so the
        // ordinary read is the document it was and the ETag above still identifies it.
        ShAgentManifest manifest = ShAgentManifestSection.project(
                shAgentManifestService.manifest(sections), sections);
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
     * @param include comma-separated sections ({@code instance,sites,model,sitemap,conventions};
     *                {@code manifest} and {@code ops} are the two that must be named)
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

    /** SH1022 — this listing's own parameters, outside the shared content-query vocabulary. */
    private static final List<String> FIND_OWN = List.of("limit", "ids", "after", "trash",
            "scheduled", "release", "format");

    /** Where the three words this listing cannot serve do work. */
    private static final String FIND_ELSEWHERE =
            "This listing pages with `limit` and `after`, and its cursor is an address — so it "
                    + "cannot be ordered by anything else. `sort`, `page`, `size`, `folderPage` "
                    + "and `folderSize` are served by the console listing, "
                    + "GET /api/v2/object/{id}/list.";

    /**
     * Find posts — addresses and titles, never bodies (SH83).
     *
     * <p>The filters are the one content-query vocabulary (SH1022): {@code site}, {@code folder},
     * {@code subtree}, {@code type}, {@code state}, {@code locale}, {@code modifiedSince},
     * {@code modifiedBefore}, {@code author}, {@code modifier} and {@code q}, each meaning here
     * what it means on the console listing and in {@code shio_find}. See {@link ShContentQuery}.
     * A word this listing does not serve is a teaching {@code 400} rather than a silent drop —
     * an ignored filter answers {@code 200} with every row, which reads exactly like a filter
     * that matched everything.</p>
     *
     * @param params the request's own parameters, which is how an unknown one can be refused at
     *               all: Spring binds what it recognises and discards the rest, so a typo has to
     *               be caught against the whole map or not at all
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
    @Operation(summary = "Find posts by site / type / folder / state / date / author — identifiers, not bodies")
    public ResponseEntity<Object> find(
            @RequestParam Map<String, String> params,
            // SH222 — the read half of the undo every delete already promises. A parameter on the
            // listing rather than an endpoint of its own: the trash is a list of posts, and this is
            // the call that lists posts (P10).
            @RequestParam(name = "trash", required = false, defaultValue = "false") boolean trash,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids,
            @RequestParam(name = "after", required = false) String after,
            // SH991 — `any`, or `from..to` in ISO instants; rows then carry the scheduled instants.
            @RequestParam(name = "scheduled", required = false) String scheduled,
            // SH993 — a release's name, with `site`: the listing is then the posts it holds.
            @RequestParam(name = "release", required = false) String release,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        ShContentQuery query = ShContentQuery.of(params, ShContentQuery.FILTERS, FIND_OWN,
                FIND_ELSEWHERE);
        ShAgentFound found = shAgentReadService.find(query, limit, ids, after, trash, scheduled,
                release);
        return isTerse(format)
                ? shAgentBodyEtag.terse("find", ShAgentReadTerse.found(found), ifNoneMatch)
                : shAgentBodyEtag.json("find", found, ifNoneMatch);
    }

    /**
     * Read one or more posts by address, optionally projected to named fields (SH123).
     *
     * @param address repeatable — several posts in one call
     * @param fields  comma-separated field names; omit for the whole document
     * @param state   SH619 — {@code published} reads what is live and {@code draft} the editable
     *                copy; omit it for the draft-preferred default. Two reads of one address at
     *                two moments is the fact-level "what will change when I publish this"
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
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "format", required = false) String format,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        // SH262 — `include=impact` attaches each template row's blast radius. A projection on this
        // read rather than a `GET /agent/impact`, because a new endpoint with no MCP tool is a
        // REST-only capability (P1) and the tool count is capped at 12 with 11 used; `shio_read`
        // carries it with no extra schema, and the subject of a read is already an address.
        //
        // SH619 — `state` is a parameter here for the same reason, and it is why the answer to
        // "what will change when I publish this" is a read rather than a `diff` endpoint: the
        // comparison is two reads of one address at two moments, which composes (P10) instead of
        // adding a third contract that would then need its own tool, its own budget and its own
        // conformance row.
        //
        // SH721 — and `include=digest` is the third rider on the same parameter, which is why the
        // conditional write needed no new endpoint, no new tool and no new query string: a caller
        // that intends to write asks the read it was already making for the one extra value.
        // SH981 — `include=links` is the fourth rider on the same parameter: the console's page
        // for this post and the preview a person opens, asked for by the caller about to hand the
        // work over. Measured before it was made opt-in: on every row it cost 26 est. tokens a row
        // and took the build-a-site benchmark 800 over its ceiling.
        // SH987 — `include=revisions` is the fifth rider, and `state=rev:<n>` the second reading of
        // `state`: the page's history and one past version of it, on the read that already takes an
        // address. Neither needed an endpoint, which is what keeps the tool count where it is.
        List<ShAgentDoc> docs = shAgentReadService.read(address, split(fields), ids,
                includes(include, "impact"), state, includes(include, "digest"),
                includes(include, "links"), includes(include, "revisions"),
                // SH991 — when the post goes live or comes down, read off its draft row.
                includes(include, "schedule"),
                // SH1003 — what a curator is still asking of this page, so a session that is about
                // to rewrite it reads the correction rather than being told again.
                includes(include, "notes"),
                // SH1008 — and whether another run wrote here in the last minute, so a session
                // learns somebody else is on the page BEFORE it plans a write rather than after
                // SH985's guard refuses one. Never a lock: it refuses nothing.
                includes(include, "working"),
                // SH1063 — and whether it is findable: the search site it belongs in, whether it
                // is one that gets sent, and the last indexing failure recorded against it. The
                // question search exists for had no surface at all on any of the three.
                includes(include, "index"));
        return isTerse(format)
                ? shAgentBodyEtag.terse("read", ShAgentReadTerse.docs(docs), ifNoneMatch)
                : shAgentBodyEtag.json("read", docs, ifNoneMatch);
    }

    /**
     * Re-reads the operator's package directory (SH219).
     *
     * <p><b>Admin-only, and a POST.</b> It changes what every later call to this instance can
     * apply, which is a write however little it stores — and the role is the same statement SH96
     * makes about the directory itself: whoever configured the path is vouching for what is in it,
     * so whoever reloads it should be somebody the instance already trusts with that.</p>
     *
     * <p>It answers with the counts rather than the catalogue. A caller who has just added a
     * package wants to know whether it is there, and re-sending every summary to say so would
     * charge them the listing they can ask for when they want it (P3). {@code rejected} carries the
     * ones that would not load, with the reason SH217 records — which makes this the author's loop
     * closed in a single call: drop the package in, reload, read why it was refused.</p>
     */
    @PostMapping("/blueprints/reload")
    @Operation(summary = "Re-read the operator-configured blueprint directory (admin)")
    public ShBlueprintRegistry.Reload reloadBlueprints() {
        return shBlueprintRegistry.reload();
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
     * @param digest SH194/SH266 — {@code true} for the instance's default page count, or a number
     *               ({@code ?digest=40}) the server clamps to its ceiling. Returns the two SH187
     *               hashes for each page this run <b>affected</b>,
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
                                        @RequestParam(name = "verify", required = false)
                                        String verify,
                                        @RequestParam(name = "digest", required = false)
                                        String digest,
                                        @RequestParam(name = "format", required = false)
                                        String format,
                                        Principal principal) {
        // SH266 — `digest` takes true/false *or* a page count, which is how `limit` already reads
        // everywhere else on this surface. A count is clamped to the instance's ceiling rather
        // than refused: the caller wants as many as it can have, and the cut is already a row.
        // SH320 — `verify` bounds work (a whole lint pass), so it teaches rather than reading an
        // unknown value as false. `ids` deliberately stays on Spring's lenient binding: it only
        // adds a field to rows that are otherwise identical.
        // SH1083 — the ops were checked as they arrived, before binding (ShAgentBatchBodyAdvice):
        // a `cancle` on a post.publish used to bind away and publish the page now.
        return rendered(shAgentProofService.attach(
                shAgentBatchService.run(batch, ids, name(principal)),
                ShAgentFlag.of(verify, "verify"),
                ShAgentDigestPages.wanted(digest), ShAgentDigestPages.count(digest)), format);
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
                                        @RequestParam(name = "verify", required = false)
                                        String verify,
                                        @RequestParam(name = "digest", required = false)
                                        String digest,
                                        @RequestParam(name = "format", required = false)
                                        String format,
                                        Principal principal) {
        // SH266/SH320 — same reading as /batch.
        return rendered(shAgentProofService.attach(
                shAgentDesiredStateService.apply(desired, ids, name(principal)),
                ShAgentFlag.of(verify, "verify"),
                ShAgentDigestPages.wanted(digest), ShAgentDigestPages.count(digest)),
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
     * @param state   SH990 — {@code published} lints what is live, {@code draft} what a publish
     *                would put there; omit for the draft-preferred subjects
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
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentReport report = shAgentVerifyService.verify(site, checks, limit, folder, address,
                since, state);
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
     *
     * @param state SH990 — {@code draft}, {@code published} or {@code rev:<n>}, beside an
     *              {@code address}: the digest of what a publish would change, or of what the page
     *              said before, is the same proof an agent already takes of the draft
     */
    @GetMapping("/render")
    @Operation(summary = "Structural digest of a page: outline, landmarks, links, images — not pixels")
    public ResponseEntity<Object> render(
            @RequestParam(name = "address", required = false) String address,
            @RequestParam(name = "url", required = false) String url,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "format", required = false) String format) {

        ShAgentDigest digest = shAgentRenderService.digest(address, url, state);
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(ShAgentVerifyTerse.digest(digest))
                : ResponseEntity.ok(digest);
    }

    /**
     * SH1014 — the page as it <em>would</em> read, from values nobody has saved.
     *
     * <p>A dry run proves a write is legal and says nothing about how the page will look, and a
     * preview token addresses a post id rather than a payload — so neither a curator nor an agent
     * could see a change without first making it. This renders through the same page render service
     * every other path uses, over a transient copy of the post, and persists nothing.</p>
     *
     * <p>A POST because the document is the argument, and it does not fit in a query string. It
     * writes nothing all the same, which is what {@code ShProposedRenderWritesNothingTest} holds:
     * the method that would make this a write is the one nobody called.</p>
     *
     * @param format {@code html} adds the rendered markup, which is what a console frame needs and
     *               what an agent asking for an outline should not pay for
     */
    @PostMapping("/render")
    @Operation(summary = "Render a page from proposed field values without saving them")
    public ResponseEntity<Object> renderProposed(
            @RequestBody ShProposedRender body,
            @RequestParam(name = "format", required = false) String format) {

        var proposed = shAgentRenderService.proposed(body.address(), body.state(), body.data(), body.on(),
                "html".equalsIgnoreCase(format));
        return ResponseEntity.ok(proposed.html() == null ? proposed.digest() : proposed);
    }

    /**
     * @param address the page to render (SH79)
     * @param state   {@code draft} or {@code published}; a revision is refused, being a record
     * @param data    field values laid over the stored document; a field it does not name is kept
     * @param on      SH1323 — with {@code address} a section, a page of its site that uses it: that
     *                page is drawn with the section carrying {@code data}; absent, {@code address}
     *                is the page
     */
    public record ShProposedRender(String address, String state, JsonNode data, String on) {
    }

    /**
     * SH809 — which fields on a page a curator could edit.
     *
     * <p>The one P1 exception that named a route and did not have it. {@code universalEditor} was
     * advertised {@code true} and reachable only from the console, because SH185's bridge is a
     * browser protocol — <em>honest to a human and not to an agent</em>, as the inventory put it.
     * So an agent could read a post, render it, and diff draft against live, and still not answer
     * the question a curator's job turns on.</p>
     *
     * <p>It reads the page rather than the model, which is deliberate: the answer is what somebody
     * can actually click, and the annotations are already there for the browser to use.</p>
     *
     * @param state SH990 — which version of the page to read the annotations off
     * @param include SH1039 — {@code insertable} attaches, per container, the section types the
     *                template says fit there, each resolved against this instance's post-types.
     *                An editor that can select and move a section could not add one, because
     *                nothing said which types fit where. A rider rather than a route of its own:
     *                the caller already holds the address this read takes, so a second endpoint
     *                would resolve the same page again for the half the first one left out (P2),
     *                and it is opt-in because the fields of every accepted type are what a caller
     *                asking <em>which fields can I edit</em> did not ask for (P3)
     */
    @GetMapping("/editable")
    @Operation(summary = "Which fields on a page a curator could edit, and the regions it declares")
    public ResponseEntity<Object> editable(@RequestParam(name = "address") String address,
                                           @RequestParam(name = "state", required = false) String state,
                                           @RequestParam(name = "include", required = false) String include) {
        return ResponseEntity.ok(
                shAgentRenderService.editable(address, state, includes(include, "insertable")));
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
     * @param budget SH723 — estimated-token ceiling on the page, up to the configured maximum;
     *               {@code 0} asks for none. {@code limit} bounds <em>rows</em>, and a change
     *               row's cost is not a constant, so this is the bound that holds: a page cut
     *               here reports {@code budget.truncated} and, in cursor mode, leaves the
     *               cursor at the last entry that fit
     * @param ifNoneMatch a tag from a previous read (SH159). This is the poll — "has anything
     *                    happened since I last looked" — and the honest answer is very often
     *                    "no", so a {@code 304} is what makes asking cheap enough to do often.
     *                    <b>It revalidates in both modes now (SH232)</b>: with {@code since},
     *                    the way SH88 intends the feed to be walked, an unchanged range is
     *                    byte-identical; and without it, because a window's cursor is the
     *                    newest row's position, which does not move until somebody writes.
     *                    SH159 shipped saying the window never could — true then, and made
     *                    untrue by SH147's watermark for every window that has rows in it.
     *                    Measuring left one case, the <em>empty</em> window, whose cursor was
     *                    the ceiling and so was {@code now}; it is the start of the trail
     *                    instead, which skips nothing an empty range could contain. A
     *                    repeating incident (SH144) raises its count and correctly does not
     *                    match; that is the stream saying it is still happening
     * @param include {@code fields} (SH989) names the fields each update, publish and restore
     *                changed — the one description of an approved run that survives the approve,
     *                since publishing makes the draft and the live row the same document
     * @param decision SH1002 — narrow to the changes a curator decided one way: {@code reject},
     *                {@code approve} or {@code hold}. Filtered in the query with the keyset, so a
     *                narrowed feed pages exactly the way the whole one does
     * @param by      SH999 — narrow to who wrote it: {@code agent}, {@code curator} (a console
     *                session counts as one) or a session id. This is the question the feed was built
     *                to answer and could not: an agent polling for what changed wants what changed
     *                <em>outside its own run</em>, and every row looked the same
     * @param action  SH1005 — one transition kind: {@code CREATE}, {@code UPDATE}, {@code PUBLISH},
     *                {@code UNPUBLISH}, {@code DELETE} or {@code RESTORE}
     * @param actor   SH1005 — one principal's rows. An agent acts <em>as</em> somebody, so this and
     *                {@code by} are different questions
     * @param subject SH1005 — {@code post}, {@code folder} or {@code site} (SH196)
     * @param address SH1005 — one page's own history, e.g. {@code post:Blog/hello-world}. This feed
     *                walked the same rows and could not answer "what happened to this page", which
     *                is the question a correction starts from and the one read
     *                {@code ShAgentReadReachabilityLintTest} held as a gap
     */
    @GetMapping("/changes")
    @Operation(summary = "Content changes since a cursor: what a human (or another session) did")
    public ResponseEntity<Object> changes(
            @RequestParam(name = "site", required = false) String site,
            @RequestParam(name = "since", required = false) String since,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "ids", required = false, defaultValue = "false") boolean ids,
            @RequestParam(name = "budget", required = false) Integer budget,
            @RequestParam(name = "include", required = false) String include,
            @RequestParam(name = "format", required = false) String format,
            @RequestParam(name = "by", required = false) String by,
            @RequestParam(name = "decision", required = false) String decision,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "actor", required = false) String actor,
            @RequestParam(name = "subject", required = false) String subject,
            @RequestParam(name = "address", required = false) String address,
            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        // SH1005 — the narrowings the console needed land here as well, and not only there: a
        // filter only one surface has is the console-only capability P1 refuses, and the reader
        // underneath is one query set now, so they cost this endpoint a parameter each and nothing
        // on the payloads an MCP session pays for on every turn (P3).
        ShAgentChanges changes = shAgentChangesService.changes(site, since, limit, ids, budget,
                includes(include, "fields"),
                new ShHistoryFilter(by, decision, action, actor, subject, address));
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
    /**
     * What a curator decided about this credential's own runs (SH1002).
     *
     * <h2>Read-only, on purpose and permanently</h2>
     *
     * <p>An agent approving its own work is a standing non-goal, and the review surface stays off
     * this namespace for that reason — there is no decide path here and there will not be one. What
     * this closes is the other direction: a revert leaves ordinary restore and delete audit rows
     * that look like a curator's own edit, so a resumed session could not tell "my page came back"
     * from "somebody rejected my page", and every correction needed a person to re-explain it.</p>
     *
     * @param decision {@code reject}, {@code approve} or {@code hold}; omit for all of them. A
     *                 resumed session almost always wants the rejections — the rest it can see in
     *                 the content
     */
    @GetMapping("/review")
    @Operation(summary = "What a curator decided about this credential's runs (read-only)")
    public List<ShAgentVerdict> review(
            @RequestParam(name = "decision", required = false) String decision,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return shAgentVerdictService.mine(decision, limit);
    }

    /**
     * What these runs were, and what each said it was for (SH1000).
     *
     * <p>The other half of SH999's {@code agent} field: the feed names a run and this says who it
     * was. Asked by the ids a caller already has, rather than listed, because "every run this
     * instance has ever seen" is not a question anybody has — and a listing would be a second,
     * unbounded surface over a table the review queue already scopes.</p>
     *
     * @param sessions comma-separated session ids, as the feed spells them in {@code agent}
     */
    @GetMapping("/runs")
    @Operation(summary = "Who a run was, and what it said it was for")
    public List<ShAgentRunRow> runs(@RequestParam(name = "session") String sessions) {
        List<String> ids = java.util.Arrays.stream(sessions.split(","))
                .map(String::trim).filter(id -> !id.isEmpty()).distinct().toList();
        return shAgentRunService.runs(ids).stream()
                .map(run -> new ShAgentRunRow(run.getId(), run.getClientName(),
                        run.getClientVersion(), run.getTransport(), run.getActor(),
                        run.getIntent(), run.getStartedAt()))
                // By the order asked for, not the order the database returned: a caller that sent
                // three ids and rendered three sections would otherwise render them at random (P5).
                .sorted(java.util.Comparator.comparingInt(row -> ids.indexOf(row.session())))
                .toList();
    }

    /**
     * The publish gates a site declares (SH997) — what stands between its content and visitors.
     *
     * <p>A gate travels as its own idempotent write rather than through the op vocabulary, the way
     * SH161's notes do and for the same reason: it is not content. Folding it into the desired-state
     * document would make it the thing {@code --prune} deletes the day a tree stops naming it, and
     * every op name is paid for by every caller that reads {@code tools/list}.</p>
     */
    @GetMapping("/gates")
    @Operation(summary = "The checks this site runs before content reaches visitors")
    public List<com.viglet.shio.api.gate.ShAgentGate> gates(@RequestParam(name = "site") String site) {
        return shAgentGateApiService.gates(site);
    }

    /**
     * SH997 — what this site's gates say about one address, without publishing it.
     *
     * <p>The CI form: `shio gate run` asks this for each page a pipeline is about to put live, and the
     * answer is the same evaluation the publish makes — asked here rather than recomputed in the CLI,
     * because a gate a pipeline judges differently from the server is a gate that passes in CI and
     * refuses in production.</p>
     */
    @GetMapping("/gates/check")
    @Operation(summary = "What this site's gates say about one address")
    public List<com.viglet.shio.api.gate.ShPublishGateService.Outcome> checkGates(
            @RequestParam(name = "site") String site,
            @RequestParam(name = "address") String address) {
        return shAgentGateApiService.check(site, address);
    }

    /** Declare one gate, idempotently by {@code (site, name)} — the shape a gate file pushes. */
    @PutMapping("/gates/{name}")
    @Operation(summary = "Declare a publish gate")
    public com.viglet.shio.api.gate.ShAgentGate putGate(@PathVariable String name,
                               @RequestParam(name = "site") String site,
                               @RequestBody com.viglet.shio.api.gate.ShAgentGate body) {
        return shAgentGateApiService.put(site, name, body);
    }

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

    /**
     * SH1063 — where this instance sends what it publishes, and which sites go nowhere.
     *
     * <p>The routing table rather than the switch: "indexing is on" is one bit and not the question
     * anybody has, which is whether <em>this</em> site's pages reach a search index. A site mapping
     * to nothing is the failure that raises no error — the listener builds a document, finds no
     * route and sends nothing — so it is a row here with an empty target, and a finding in
     * {@code verify}.</p>
     *
     * <p>One read for three surfaces: {@code shio://index}, {@code shio index status} and the
     * console's card all show this, because three computations of "which search site does this go
     * to" would be three chances to disagree with the listener that actually routes.</p>
     */
    @GetMapping("/index")
    @Operation(summary = "Search indexing: is it on, where it sends, and which sites route nowhere")
    public ResponseEntity<Object> index(@RequestParam(name = "format", required = false) String format) {
        com.viglet.shio.turing.ShTuringIndexStatus status = shTuringIndexStatusService.status();
        return isTerse(format)
                ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                        .body(com.viglet.shio.turing.ShTuringIndexTerse.status(status))
                : ResponseEntity.ok(status);
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
