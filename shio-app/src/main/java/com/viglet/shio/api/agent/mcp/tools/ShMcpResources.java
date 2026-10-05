/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.shio.api.agent.ShAgentContextService;
import com.viglet.shio.api.agent.ShAgentContextTerse;
import com.viglet.shio.api.agent.ShAgentManifestService;
import com.viglet.shio.api.agent.ShConsoleSurfaces;
import com.viglet.shio.api.agent.ShAgentOps;
import com.viglet.shio.api.agent.ShAgentUsageService;
import com.viglet.shio.api.agent.ShAgentVerdictService;
import com.viglet.shio.api.agent.ShAgentVerdictTerse;
import com.viglet.shio.api.agent.diagnostics.ShAgentDiagnosticsService;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.api.agent.verify.ShAgentVerifyTerse;
import com.viglet.shio.api.cda.ShCdaService;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.note.ShNoteRelay;
import com.viglet.shio.blueprint.ShBlueprintRegistry;
import com.viglet.shio.blueprint.ShBlueprintTerse;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * The MCP resources (SH83, extended by SH93 and SH166): {@code shio://manifest},
 * {@code shio://blueprints}, {@code shio://context}, and the
 * {@code shio://schema/{postType}} and {@code shio://blueprint/{name}} templates.
 *
 * <p>All of them are documents a human might reasonably want to pin into a conversation,
 * which is the test for "resource rather than tool". {@code shio://context} closes the
 * last item SH77 left open — the pack has been reachable over HTTP and from the CLI
 * since SH126, but not as something a client could attach.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShMcpResources {

    private static final String TEXT = "text/plain";
    private static final String JSON = "application/json";
    private static final String SCHEMA_PREFIX = "shio://schema/";
    private static final String BLUEPRINT_PREFIX = "shio://blueprint/";
    private static final String FILES_SUFFIX = "/files";

    @Bean
    ShMcpResource shMcpManifestResource(ShAgentManifestService manifestService,
                                        ObjectMapper objectMapper) {
        return resource("shio://manifest", "Shio capability manifest",
                "What this instance can do: version, feature flags, limits, and the endpoint "
                        + "index. Read it before assuming a capability exists.",
                JSON, "/api/v2/agent/manifest",
                uri -> objectMapper.writeValueAsString(manifestService.manifest()));
    }

    /**
     * The console's surfaces (SH979).
     *
     * <p>A resource, and for the reason that keeps deciding this: it is a document about what
     * <em>a curator</em> can do, read once by a client reasoning about the division of labour, and
     * {@code tools/list} is re-sent every turn (P3). The same document the console's own rail is
     * built from, so an agent and a curator are looking at one list rather than two that drift.</p>
     *
     * <p>JSON rather than terse, because each row carries a classification a reader has to be able
     * to tell apart — an equivalent's kind and target, or an exception's reason — and a terse
     * encoding of a two-shape row is a parser somebody has to write.</p>
     */
    @Bean
    ShMcpResource shMcpSurfacesResource(ShConsoleSurfaces surfaces, ObjectMapper objectMapper) {
        return resource("shio://surfaces", "Shio console surfaces",
                "Every surface the console offers and how an agent reaches the same capability — "
                        + "the tool, op or endpoint that does it, or why there is none. Read it "
                        + "before assuming a curator's screen has no equivalent here.",
                JSON, "/api/v2/agent/manifest?include=surfaces",
                uri -> objectMapper.writeValueAsString(surfaces.surfaces()));
    }

    /**
     * The verdicts on this credential's own runs (SH1002).
     *
     * <p>A <b>resource</b>, and this one is the clearest case the distinction has: it is the first
     * thing a resumed session should read, and it is read once rather than called when the model
     * decides to act. It is also why it is not a tool — {@code tools/list} is re-sent every turn
     * (P3), and a read that matters at the start of a session should not be billed at every step
     * of it.</p>
     *
     * <p><b>There is no decide path here and there will not be one.</b> An agent approving its own
     * work is a standing non-goal; this resource answers what a curator <em>already</em> decided,
     * scoped to runs made with the calling credential, and nothing on this namespace writes a
     * decision. {@code ShAgentReviewReadOnlyTest} asserts both halves.</p>
     *
     * <p>Rejections only. The other two verdicts are legible in the content — an approved page is
     * live and a held one is still a draft — while a rejection leaves ordinary restore and delete
     * rows that look exactly like a curator's own edit, which is the whole reason this exists.</p>
     */
    @Bean
    ShMcpResource shMcpReviewResource(ShAgentVerdictService verdictService) {
        return resource("shio://review/mine", "Rejections on your runs",
                "What a curator rejected from your own runs, with the reason each time. Read it "
                        + "first on a resumed session: a revert leaves ordinary restore rows, so "
                        + "the content alone cannot tell you a page was turned down.",
                TEXT, "/api/v2/agent/review?decision=reject",
                uri -> ShAgentVerdictTerse.render(verdictService.mine("reject", null)));
    }

    /**
     * SH1006 — what a curator asked the agent from the console's dock.
     *
     * <p>Shio hosts no model, so a question typed into the dock is a {@code request} note on the
     * page the curator was on, and this is where the agent reads the open ones. It answers with
     * {@code note.add} as a {@code reply} on the thread, which the dock then shows. A resource and
     * not a tool, for the reason that keeps deciding it: read once when a session starts or
     * resumes, not billed on every turn's {@code tools/list} (P3). Scoped to the tenant by the
     * notes' own discriminator.</p>
     */
    @Bean
    ShMcpResource shMcpRequestsResource(ShNoteRelay relay) {
        return resource("shio://requests", "Questions from curators",
                "Open requests a curator sent from the console's dock, newest first. Answer each"
                        + " with note.add kind reply, parent the note id, then note.resolve it.",
                TEXT, "/api/v2/note/open?kind=request",
                uri -> relay.render());
    }

    /**
     * Every open note across the sites this caller can read (SH1215).
     *
     * <p>The console's Open notes page, for an agent in one read. Before this an agent reached
     * notes one page at a time ({@code include=notes}), one site at a time ({@code .notes.yaml}),
     * or as a count on {@code /console/summary}. A resource and not a manifest endpoint: the
     * manifest's endpoint index is held under its row count and token ceiling on purpose, and a
     * read made once when a session starts is the case resources exist for (P3).</p>
     */
    @Bean
    ShMcpResource shMcpNotesResource(ShNoteRelay relay) {
        return resource("shio://notes", "Open notes",
                "Every note still asking for something, across sites, newest first: its kind, its"
                        + " page and what it says. Fix the page, then note.resolve the note.",
                TEXT, "/api/v2/note/open",
                uri -> relay.renderOpen());
    }

    /**
     * The op vocabulary (SH332).
     *
     * <p>Two of SH331's four wrong calls were an agent inferring a key name because nothing stated
     * it: {@code tools/list} carries tool schemas, and the <em>op</em> set — which verbs exist and
     * which keys each takes — lived only as prose inside {@code shio_write}'s description. This is
     * the half of SH331 that prevents the guess instead of repairing it, and it is the same move
     * SH260 made for render helpers and SH300 for DOM attributes.</p>
     *
     * <p>A <b>resource</b> and not more tool description, for the reason that keeps deciding this:
     * {@code tools/list} is re-sent every turn (P3), while a resource is one row on
     * {@code resources/list} and is read once by whoever is about to write. Terse for the reason
     * SH332 states outright — this vocabulary is optimised for agent reliability, not human
     * readability.</p>
     */
    @Bean
    ShMcpResource shMcpOpsResource() {
        return resource("shio://ops", "Shio write vocabulary",
                "Every op POST /api/v2/agent/batch accepts and the keys each one takes — "
                        + "site.upsert, folder.upsert/move/delete, post.upsert/move/publish/"
                        + "unpublish/delete. Read it before a first write: an unrecognised key is "
                        + "refused, so the list is exhaustive rather than indicative.",
                TEXT, "/api/v2/agent/context?include=ops",
                uri -> ShAgentOps.terse());
    }

    /**
     * The blueprint catalogue (SH92/SH93).
     *
     * <p>A resource rather than a tool because it is a <em>document about what is
     * available</em>, not an action — and because the alternative is worse: an agent that
     * cannot see the catalogue will model a blog by hand, which is the exact cost Block J
     * exists to remove. Terse, and without the per-package parameter schemas: choosing
     * between five packages must not cost five schemas, and the one the agent picks is a
     * cheap second call.</p>
     */
    @Bean
    ShMcpResource shMcpBlueprintsResource(ShBlueprintRegistry registry) {
        return resource("shio://blueprints", "Shio blueprint catalogue",
                "The appliable starting points this instance carries — a whole section (content "
                        + "model + folders + pages) per package, with the parameters each takes. "
                        + "Read it before building a blog, a docs tree or a marketing site by "
                        + "hand; apply one with shio_apply's `blueprint` argument.",
                TEXT, "/api/v2/agent/blueprints",
                uri -> ShBlueprintTerse.catalogue(registry.summaries()));
    }

    /**
     * One blueprint, and — with the {@code /files} suffix — the bytes of its front-end half
     * (SH166).
     *
     * <h2>The hole this closes</h2>
     *
     * <p>{@code shio_apply} has reported a package's carried files since SH93, and an MCP-only
     * caller had no way to read one. The paths were a list of things that exist somewhere else:
     * the bodies travel on {@code GET /agent/blueprints/{name}?include=files}, which a client
     * with no HTTP of its own cannot reach. So the honest reading of SH166 is not only that the
     * files are unsubstituted — it is that for this caller they were unreachable, and "your
     * front end is thirteen files you cannot see" is a worse answer than either option the
     * design weighed.</p>
     *
     * <h2>Why a resource and not a tool argument</h2>
     *
     * <p>Because it is reference material a session pins, which is the test this class applies,
     * and because the alternative is unaffordable: {@code shio_apply} measures 457 against a
     * 460 per-tool ceiling, so the argument that would carry this does not fit, and
     * {@code tools/list} is re-sent every turn (P3). A resource template is one row on
     * {@code resources/templates/list}, read only when somebody asks for it.</p>
     *
     * <h2>Two URIs, one template</h2>
     *
     * <p>{@code shio://blueprint/<name>} is the parameter-by-parameter detail;
     * {@code shio://blueprint/<name>/files} adds the bodies, which are much the larger read
     * and are therefore something the caller asks for rather than something it is handed.
     * They share a bean because {@link ShMcpResource#matches} keys on the literal prefix
     * before the first brace — two beans would both claim {@code shio://blueprint/…} and the
     * one that answered would depend on sort order.</p>
     */
    @Bean
    ShMcpResource shMcpBlueprintResource(ShBlueprintRegistry registry) {
        return resource(BLUEPRINT_PREFIX + "{name}", "Shio blueprint",
                "One package in full: every parameter with its default, the post-types it "
                        + "provisions, and the front-end files it carries. Append /files to the "
                        + "URI for their bodies — the server never writes those into a project "
                        + "and never substitutes them, so each is listed with the {{placeholder}}s "
                        + "you still have to fill in from the parameters above.",
                TEXT, "/api/v2/agent/blueprints/{name}?include=files",
                uri -> {
                    String path = uri.substring(BLUEPRINT_PREFIX.length());
                    boolean withFiles = path.endsWith(FILES_SUFFIX);
                    String name = withFiles
                            ? path.substring(0, path.length() - FILES_SUFFIX.length())
                            : path;
                    var blueprint = registry.require(name);
                    return withFiles
                            ? ShBlueprintTerse.fileBodies(blueprint)
                            : ShBlueprintTerse.detail(blueprint);
                });
    }

    /**
     * What has failed, folded by cause (SH596).
     *
     * <h2>The one surface that could not ask what went wrong</h2>
     *
     * <p>{@code GET /api/v2/agent/diagnostics} carries what a failed response no longer can: a
     * count, first and last occurrence, a {@code fix}, and the frames naming the throwing method.
     * It was reachable over REST, from the CLI and from the console, and over MCP it was reachable
     * <b>not at all</b> — eleven tools and four resources, and it was none of them.</p>
     *
     * <p>That matters more than a missing row suggests, because MCP is not one surface among
     * several for a plugin user: <b>it is the only one</b>. The Claude Code plugin wires an
     * {@code .mcp.json} and nothing else. So the reader most likely to meet a 500 — an agent
     * driving an instance it did not build — was the one reader who could not ask what happened,
     * and the fallback was a server log on a machine that may not be theirs. P1 orders every
     * capability MCP → files/CLI → REST → console; this one landed on the bottom three and skipped
     * the top, which is the same inversion the CLI's missing publish verb was.</p>
     *
     * <p><b>And the manifest said it was fine.</b> {@code ShAgentP1ConformanceTest} classified the
     * {@code diagnostics} feature as reachable because incidents ride the change feed (SH144), and
     * they do — as rows in a stream. The fold by cause, with the frames and the fix, is a different
     * document, and answering "is it reachable" with a neighbouring capability is how a
     * conformance check comes back green over exactly this gap.</p>
     *
     * <h2>A resource and not a tool</h2>
     *
     * <p>It is a read with no arguments, like {@code shio://context} — the test this class applies
     * — and {@code tools/list} is re-sent every turn while a resource is one row read by whoever is
     * about to ask. Terse for the same reason the ops vocabulary is: this is read after something
     * has already gone wrong, and the reader wants the frames, not formatting. The default limit
     * applies, and the header states {@code since} and {@code dropped}, so an empty answer keeps
     * saying "nothing since then" rather than "nothing ever".</p>
     */
    @Bean
    ShMcpResource shMcpDiagnosticsResource(ShAgentDiagnosticsService diagnosticsService) {
        return resource("shio://diagnostics", "Shio failure report",
                "What has failed on this instance, folded by cause: webhook deliveries, image "
                        + "transforms, scheduled publishes and 5xx errors, each with a count, when "
                        + "it was first and last seen, a fix, and the frames that threw. Read it "
                        + "when a call answered 500 or when something did not happen — the failed "
                        + "response is gone and this is where its cause still is.",
                TEXT, "/api/v2/agent/diagnostics",
                uri -> ShAgentVerifyTerse.diagnostics(diagnosticsService.diagnostics(null)));
    }

    /**
     * What this tenant has spent (SH754).
     *
     * <h2>The meter counted the agent and the agent could not read it</h2>
     *
     * <p>SH45 published the API-call meter at {@code GET /api/v2/tenant/usage} and through the
     * {@code ShBillingExporter} SPI — a human route and a billing route. The meter's own surface
     * keys name the gap: {@code agent} is one of the three it counts, so the caller generating the
     * largest share of the number was the one with no way to ask for it. An agent told to work
     * inside a budget and unable to query its spend has been given advice, not a bound.</p>
     *
     * <h2>A resource, and terse</h2>
     *
     * <p>Same judgement as {@code shio://diagnostics} (SH596): a read with no arguments, which is
     * state to look at rather than an act to perform, and {@code tools/list} is paid every turn
     * (P3) while a resource is one row on {@code resources/list}. The twin is a projection on the
     * context pack rather than a new endpoint, following {@code shio://ops} — a new capability does
     * not get a new route by default.</p>
     *
     * <p><b>Written and pending, both stated.</b> The meter flushes on an interval, so a document
     * reporting only what is durable would answer zero for the first minute of every window, and an
     * agent that reads zero right after doing work stops believing the number.</p>
     */
    /**
     * Where this instance sends what it publishes (SH1063).
     *
     * <p>A resource and not a tool, by the test this class applies: it takes no arguments and it is
     * state to look at rather than an act to perform, and {@code tools/list} is paid on every turn
     * while a resource is one row on {@code resources/list}. What it makes visible is the failure
     * that raises nothing — a site mapping to no search site publishes into a void, and until this
     * existed the only trace was the absence of results in somebody else's search box.</p>
     */
    @Bean
    ShMcpResource shMcpIndexResource(
            com.viglet.shio.turing.ShTuringIndexStatusService indexStatusService) {
        return resource("shio://index", "Shio search indexing",
                "Whether this instance indexes what it publishes, where it sends it, and the "
                        + "routing table site by site — a site with published pages and no search "
                        + "site reaches no index and raises no error. Read it when search is "
                        + "missing content that exists.",
                TEXT, "/api/v2/agent/index",
                uri -> com.viglet.shio.turing.ShTuringIndexTerse.status(indexStatusService.status()));
    }

    @Bean
    ShMcpResource shMcpUsageResource(ShAgentUsageService usageService) {
        return resource("shio://usage", "Shio usage meter",
                "What this tenant has spent today: API calls per surface (cda, agent, console), "
                        + "each with what is durably counted and what is still pending a flush, "
                        + "plus stored bytes and the plan. Read it to know what a budget has left "
                        + "rather than to guess.",
                TEXT, "/api/v2/agent/context?include=usage",
                uri -> usageService.terse());
    }

    @Bean
    ShMcpResource shMcpContextResource(ShAgentContextService contextService) {
        return resource("shio://context", "Shio context pack",
                "The content model, the sitemap by path and this instance's conventions, in the "
                        + "terse encoding. The same payload shio_context returns.",
                TEXT, "/api/v2/agent/context?format=terse",
                uri -> ShAgentContextTerse.render(
                        contextService.context(null, null, null, null, null, false)));
    }

    /**
     * One post-type's field schema.
     *
     * <p>Served from the <b>CDA</b> schema (SH11), the same shape the context pack's
     * model section uses — not the lossless authoring definition. An agent writing
     * content needs to know the field names, widgets and cardinality; the verbatim
     * {@code widgetSettings} blob it would also get from {@code /api/v2/post-type} is
     * for the CLI's round-trip, and putting it here would make the cheap answer
     * expensive for every reader who did not need it.</p>
     */
    @Bean
    ShMcpResource shMcpSchemaResource(ShCdaService shCdaService,
                                      ShPostTypeRepository shPostTypeRepository,
                                      ObjectMapper objectMapper) {
        return resource(SCHEMA_PREFIX + "{postType}", "Post-type schema",
                "The fields of one post-type — names, widgets, required and multi-valued flags. "
                        + "Read it before writing content of a type you have not used yet.",
                JSON, "/api/v2/cda/post-type/{name}",
                uri -> {
                    String name = uri.substring(SCHEMA_PREFIX.length());
                    return objectMapper.writeValueAsString(shCdaService.getPostType(name)
                            .orElseThrow(() -> new UnknownSchemaException(name,
                                    shPostTypeRepository.findAll().stream()
                                            .map(ShPostType::getName)
                                            .filter(candidate -> candidate != null && !candidate.isBlank())
                                            .sorted().toList())));
                });
    }

    private static ShMcpResource resource(String uri, String name, String description,
                                          String mimeType, String httpTwin, Reader reader) {
        return new ShMcpResource() {

            @Override
            public String uri() {
                return uri;
            }

            @Override
            public String httpTwin() {
                return httpTwin;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String mimeType() {
                return mimeType;
            }

            @Override
            public String read(String requestedUri) {
                return reader.read(requestedUri);
            }
        };
    }

    @FunctionalInterface
    private interface Reader {
        String read(String uri);
    }

    /** A 404 that names the post-types this instance actually has (SH80). */
    public static class UnknownSchemaException extends ShTeachingException {

        public UnknownSchemaException(String sent, List<String> allowed) {
            super(ShProblem.badRequest("unknown-schema", "No post-type named '" + sent + "'")
                    .withDetail("The URI is shio://schema/<postTypeName>, by API name.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Use one of the listed names, or read shio://context for the whole model.")
                    .withExample(SCHEMA_PREFIX + (allowed.isEmpty() ? "Article" : allowed.get(0))));
        }
    }
}
