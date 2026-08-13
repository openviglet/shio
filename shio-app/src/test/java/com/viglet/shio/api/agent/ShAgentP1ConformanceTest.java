/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.shio.api.agent.dto.ShAgentEndpoint;
import com.viglet.shio.api.agent.dto.ShAgentManifest;
import com.viglet.shio.api.agent.mcp.ShMcpResource;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

/**
 * SH338 — P1 is the only one of the ten laws with no instrument, and it is the one the product is
 * named after.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Four exploratory builds have each produced 6–13 tasks, and they keep finding the <b>same
 * classes</b>: SH160's {@code instanceMemory: true} half-truth came back as SH326's
 * {@code serverRender: true}; SH170's session+CSRF friction came back as SH327 and SH335. The reason
 * is structural. This repository is unusually good at turning a defect into a build guard — eleven
 * lints do exactly that — and every one of them guards <em>internal consistency</em>: the code agrees
 * with itself, or with its own documentation, or a decision was taken rather than inherited. Not one
 * asks whether the <b>operator the product is designed for can reach the capability</b>. So a
 * consistency defect becomes a permanent guard and a reachability defect becomes a task that is fixed
 * once and guards nothing.</p>
 *
 * <p>The rule is SH322's, one level up: <b>when a review finds a law is being broken, the instrument
 * that finds every other break is the deliverable, and it lands before the fixes.</b> Hence this test
 * before SH325–SH337.</p>
 *
 * <h2>What it asserts</h2>
 *
 * <p>Every key the manifest's {@code features} map reports is classified, and a key reported
 * {@code true} must be <em>reachable</em> — an MCP tool an MCP-only client can invoke, or a route
 * declared in the manifest's own endpoint index — or else named in the exception list below with the
 * reason a {@code true} there is still honest. Both sides are already machine-readable, so this needs
 * no new metadata: {@code features} is a flat boolean map and {@code ShMcpTool} beans are
 * auto-registered.</p>
 *
 * <p>Three things it deliberately gets right, all of them SH338's own design notes:</p>
 *
 * <ul>
 *   <li><b>A feature is not 1:1 with a tool.</b> {@code teachingErrors}, {@code conditionalReads} and
 *       {@code terseResponses} are properties of <em>every</em> route, and forcing them into a fake
 *       mapping would make the table lie in the direction that is hardest to notice. They are
 *       {@link Reach#CROSS_CUTTING}, which is a classification and not an excuse.</li>
 *   <li><b>An exception that has become unnecessary fails the build</b> (SH142's rule, fourth
 *       application) — each one names the tool or index path that <em>would</em> resolve it, and the
 *       test asserts that name is still absent. Ship the tool and forget the table, and this fails.</li>
 *   <li><b>The exception list is the real deliverable</b>, because it is the first honest inventory of
 *       how far P1 is actually honoured. It is asserted <em>exactly</em>, so gaining one is a
 *       reviewable diff with a reason attached, and the run prints it.</li>
 * </ul>
 *
 * <h2>What it is not</h2>
 *
 * <p>It does not ask whether a declared route can be called <b>without a cookie</b> — that is SH339,
 * and it is deliberately a separate instrument: this one asks whether a capability has an agent route
 * at all, that one asks whether the route it has is usable by a stateless credential.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShAgentP1ConformanceTest {

    @Autowired
    private ShAgentManifestService shAgentManifestService;
    @Autowired
    private List<ShMcpTool> mcpTools;
    @Autowired
    private List<ShMcpResource> mcpResources;

    /** How a capability reaches the operator P1 names. */
    enum Reach {

        /** An MCP-only client can invoke it. The strictest bar, and what P1 actually asks for. */
        MCP_TOOL(false),
        /**
         * An MCP-only client can <em>read</em> it — a document on {@code resources/list} rather
         * than a verb on {@code tools/list}. Reachable, and the bar P1 asks for.
         *
         * <p><b>SH596 added this category, and its absence is what produced the finding.</b> The
         * vocabulary could say "a tool delivers it" and could not say "a resource does, and no tool
         * needs to", so {@code diagnostics} was classified as {@code shio_changes} on the true
         * observation that incidents ride the change feed (SH144). They do — as rows in a stream.
         * The fold by cause, with the count, the fix and the frames, is a different document, and
         * it was on no MCP list at all. Answering "is this reachable" with a neighbouring
         * capability is how a conformance check comes back green over exactly the gap it exists to
         * find, and a classification can only be as honest as its categories allow.</p>
         */
        MCP_RESOURCE(false),
        /** Declared in the manifest's endpoint index, so an agent with HTTP finds it (P2). */
        AGENT_ROUTE(false),
        /** A property of every route rather than a call — {@code true} is honest with no target. */
        CROSS_CUTTING(false),
        /** Describes the installation, not a capability to invoke ({@code multiTenant}, {@code oidc}). */
        INSTANCE_FACT(false),
        /**
         * Reachable, and delivered by capabilities that are themselves claimed — a parameter on an
         * existing read, plus ops (SH732).
         *
         * <p><b>SH732 added this category for the reason SH596 added {@code MCP_RESOURCE}, and the
         * paragraph above predicted it: a classification can only be as honest as its categories
         * allow.</b> The vocabulary could say "a tool delivers it" and "a route delivers it", and
         * could not say "it is a shape over both" — which is what P10 produces every time it is
         * obeyed. {@code trash} is exactly that: `find?trash=true` lists it and three ops undo a
         * delete, and neither `/agent/find` nor `shio_write` belongs to it.</p>
         *
         * <p>So it carries <b>no target</b>. Claiming one would make this feature the owner of a
         * route the protocol already owns flagless, which `ShAgentCapabilityJoinTest` refuses — and
         * refuses correctly, since a flag map that counted one capability twice would read as an
         * endpoint count. What it carries instead is {@link Delivery#closedBy}, which is the
         * falsifiable half: name the ops, and the day one of them disappears this fails.</p>
         */
        COMPOSED(false),

        /** P1 break: HTTP-reachable, but undeclared here and unreachable from MCP. */
        REST_ONLY(true),
        /** P1 break: needs a browser session, so the agent cannot use it at all. */
        CONSOLE_ONLY(true),
        /** P1 break: only the CLI does it, so a client with no shell cannot. */
        CLI_ONLY(true);

        private final boolean exception;

        Reach(boolean exception) {
            this.exception = exception;
        }

        boolean isException() {
            return exception;
        }
    }

    /**
     * @param reach  how it is reached
     * @param target for a reachable feature, the tool name or index path that delivers it; for an
     *               exception, the tool name or index path whose <em>appearance</em> would resolve it
     *               — which is what makes a stale exception fail rather than rot
     * @param why    the claim. For a cross-cutting or instance fact it is the whole justification;
     *               for an exception it is what P1 is paying for and, where one exists, the task id
     */
    /**
     * @param closedBy SH732 — what would make this exception stale, named in advance.
     *
     *                 <p>The staleness check used to ask whether {@link #target} now exists, and
     *                 that is only the right question when the capability arrives by the road the
     *                 entry named. {@code trash} arrived by a different one — a query parameter on
     *                 an existing read plus three restore ops — so its exception could never go
     *                 green, and the check that exists to stop a held list rotting was blind to the
     *                 commonest way an entry becomes wrong.</p>
     *
     *                 <p>Naming the evidence turns the entry into a falsifiable claim: an op name, a
     *                 tool, or a route whose existence means the gap closed. Empty for an exception
     *                 whose target IS the evidence, which is most of them.</p>
     */
    record Delivery(Reach reach, String target, String why, List<String> closedBy) {

        Delivery(Reach reach, String target, String why) {
            this(reach, target, why, List.of());
        }
    }

    private static Delivery mcp(String tool, String why) {
        return new Delivery(Reach.MCP_TOOL, tool, why);
    }

    private static Delivery mcpResource(String uri, String why) {
        return new Delivery(Reach.MCP_RESOURCE, uri, why);
    }

    private static Delivery route(String path, String why) {
        return new Delivery(Reach.AGENT_ROUTE, path, why);
    }

    private static Delivery crossCutting(String why) {
        return new Delivery(Reach.CROSS_CUTTING, null, why);
    }

    private static Delivery instanceFact(String why) {
        return new Delivery(Reach.INSTANCE_FACT, null, why);
    }

    /**
     * Every feature key, classified. Add a feature to the manifest and this test fails until it is
     * classified here — which is the point: the classification is a decision about P1, and the
     * cheapest moment to make it is when the flag is written.
     */
    static Map<String, Delivery> classification() {
        Map<String, Delivery> byFeature = new TreeMap<>();

        // --- Reachable from an MCP-only client -------------------------------------------------
        byFeature.put("search", mcp("shio_find", "?q= on the find tool, SH131's predicate"));
        byFeature.put("imageTransforms", mcp("shio_assets", "the SH25 transform surface"));
        byFeature.put("agentContext", mcp("shio_context", "the context pack, P2's one call"));
        byFeature.put("contextCapabilities", mcp("shio_context", "include=manifest folds the two "
                + "opening reads into one (SH157) — the tool takes `include`"));
        byFeature.put("batchWrites", mcp("shio_write", "N addressed ops in one transaction"));
        byFeature.put("postMove", mcp("shio_write", "post.move — the op whose absence sends an agent "
                + "to the destructive workaround (SH134)"));
        byFeature.put("folderMove", mcp("shio_write", "folder.move, the same one level up (SH137)"));
        byFeature.put("siteUpsert", mcp("shio_write", "site.upsert — SH170's whole point was that "
                + "this must not be console-only"));
        // SH463 — and the key matters as much as the op. This table is keyed on `features`,
        // so a capability with no flag is not classified at all: site deletion had none, and
        // the one instrument built to find unreachable capabilities was structurally unable to
        // see it. An inventory keyed on what the manifest advertises measures the manifest.
        byFeature.put("siteDelete", mcp("shio_write", "site.delete — the other half of SH170, "
                + "found by clearing sixteen scratch sites through the browser's CSRF dance. "
                + "Destructive, so it takes the SH108 confirm token, and its plan line says it "
                + "is purged rather than trashed"));
        byFeature.put("writeProof", mcp("shio_write", "?verify=true on the write, which is why the "
                + "argument went on this tool and not on shio_apply (SH227)"));
        byFeature.put("confirmTokens", mcp("shio_write", "the SH108 token is a `confirm` argument"));
        byFeature.put("desiredState", mcp("shio_apply", "converge to a document"));
        byFeature.put("folderPrune", mcp("shio_apply", "prune removes undeclared folders (SH135)"));
        byFeature.put("blueprints", mcp("shio_apply", "the blueprint verb, plus the "
                + "shio://blueprints resource for the catalogue"));
        byFeature.put("contentLint", mcp("shio_verify", "the SH99 rules"));
        byFeature.put("routeCheck", mcp("shio_verify", "checks=routes"));
        byFeature.put("deliveryProof", mcp("shio_verify", "checks=delivery (SH141)"));
        byFeature.put("renderDigest", mcp("shio_digest", "the structural digest (SH101)"));
        byFeature.put("appearanceDigest", mcp("shio_digest", "the second hash (SH187)"));
        byFeature.put("renderFetch", mcp("shio_digest", "?url= digests the delivered page, when the "
                + "operator has allowed fetching"));
        byFeature.put("changeCursor", mcp("shio_changes", "since= is the SH88 cursor"));
        // SH596 — this said `shio_changes`, and the reason it gave was true: incidents do ride the
        // change feed. What it was not is the same document. The fold by cause — count, first and
        // last seen, a fix, the frames that threw — was on no MCP list at all, and MCP is a plugin
        // user's only surface. The entry now names what actually delivers it.
        byFeature.put("diagnostics", mcpResource("shio://diagnostics", "the failure report folded "
                + "by cause, with the frames and the fix; a read with no arguments, so a resource "
                + "rather than a twelfth tool"));
        byFeature.put("durableIncidents", mcp("shio_changes", "the same feed, and the row survives a "
                + "restart (SH144)"));
        // SH342 — `when` on post.publish. Note what this exposed about the rot check itself: the
        // exception named `/post-unified/{id}/schedule` as what would resolve it, and the fix took a
        // different shape (an argument on a tool that already existed), so nothing failed and the
        // entry had to be moved by hand. The stale check catches the predicted fix, not any fix —
        // worth knowing before trusting it as the only guard.
        byFeature.put("scheduledPublish", mcp("shio_write", "post.publish with an ISO-8601 `when`; "
                + "a past instant is refused rather than read as now"));
        // SH348 — closed in four steps, and the op turned out to be the last of them: the read reports
        // a non-default locale and the group's anchor, the file projection carries both, and
        // `post.upsert` honours them on create. `/post-unified/{id}/translate` is still there and is
        // no longer the only way in.
        byFeature.put("locales", mcp("shio_write", "post.upsert takes `locale` and `translationOf` "
                + "(an address, never a group id) on create; the read reports both"));
        byFeature.put("instanceMemory", mcp("shio_remember", "SH160 — the eleventh tool exists "
                + "because this flag was a half-truth for exactly the clients the manifest informs"));

        // --- Declared in the endpoint index ---------------------------------------------------
        byFeature.put("pathAddressing", route("/agent/resolve", "an address becomes an id, so no "
                + "UUID has to survive between calls (SH79)"));
        byFeature.put("manifestProjection", route("/agent/manifest", "?include= on this document "
                + "(SH174); the shio://manifest resource takes no arguments"));
        // SH327 — was the REST_ONLY entry this inventory named first: /preview and
        // POST /api/v2/render/post-types were the whole capability and neither was declared here nor
        // reachable without a cookie, so `serverRender: true` was SH160's half-truth again. The
        // endpoint is in the index and CSRF-narrowed now, and this lint refused to let the exception
        // outlive the fix.
        // SH186 closed the half SH327 left: an MCP-only client has no HTTP of its own, so a declared,
        // CSRF-narrowed *REST* endpoint was still unreachable for it — and `post.upsert` cannot create
        // a system post-type. `render.provision` is an op, so it arrives through shio_write with no
        // thirteenth tool on `tools/list`.
        byFeature.put("serverRender", route("/render/post-types", "provisions the PageLayout / "
                + "Region / Theme model — and since SH186 the `render.provision` batch op does the "
                + "same for a client that speaks only JSON-RPC; /preview/{site}/{url} then renders a "
                + "page with no front end at all. The one capability that makes a site *visible*"));
        // SH344 — the last step of SH184's answer: an agent that has deployed a front end can now
        // mint the token that front end needs to show a draft. Declared *and* CSRF-narrowed, which is
        // the pair SH327 established as the unit of a fix here.
        byFeature.put("previewTokens", route("/preview-token", "mints a short-lived PREVIEW token "
                + "for one post of one site; the response's token is null when Shio previews the "
                + "site itself, because /preview/** is already authenticated (SH184)"));
        // SH343 — declared and CSRF-narrowed. The exposure it uncovered is the part to remember: the
        // read returned the HMAC `secret` in plain text, so making the path reachable *first* would
        // have widened who could forge deliveries. A reachability change is also an access change.
        byFeature.put("webhooks", route("/site-webhook", "subscribe to content transitions; the "
                + "secret is write-only, and delivery failures ride /agent/diagnostics"));
        byFeature.put("postTypesAsCode", route("/post-type/{name}", "the one name-keyed post-type "
                + "surface (SH16/SH117), read and upserted over HTTP"));

        // --- Properties of the surface rather than calls on it --------------------------------
        byFeature.put("teachingErrors", crossCutting("every 4xx carries fix/allowed/didYouMean "
                + "(P6/SH80) — a property of every route, and forcing it into a mapping would make "
                + "this table lie"));
        byFeature.put("terseResponses", crossCutting("?format=terse on every read"));
        byFeature.put("conditionalReads", crossCutting("ETag + If-None-Match (SH116/SH159)"));
        byFeature.put("fieldProjection", crossCutting("?fields= on the reads that carry bodies"));
        byFeature.put("contentAcl", crossCutting("SH54 applies on every path identically"));
        byFeature.put("publishGate", crossCutting("SH107 — a protocol fact about the credential; "
                + "whether this caller has it is answered by trying"));
        byFeature.put("agentTokenScope", crossCutting("SH106 — what the credential is, not a call"));

        // --- Facts about the installation -----------------------------------------------------
        byFeature.put("multiTenant", instanceFact("whether content is tenant-isolated (SH22); it "
                + "changes how every answer is scoped, and there is no tenant call here"));
        byFeature.put("oidc", instanceFact("how a human authenticates"));
        byFeature.put("rateLimit", instanceFact("a bound, also reported in `limits`"));
        byFeature.put("signedImageUrls", instanceFact("whether a transform URL must be signed "
                + "(SH25); the URL shape, not an operation"));
        byFeature.put("mcp", instanceFact("that this instance speaks the protocol at all — the flag "
                + "an HTTP-only caller reads to learn a bridge exists"));
        // SH84. An instance fact and not a gap: indexing has no call of its own on any surface —
        // it is what a publish *means* on this deployment, and the SN routing is a deployment
        // fact rather than content (turning it into an operation would put one environment's
        // index names in the database). What an agent needs is to know it happens, which is this
        // flag, and to be told when it fails, which rides /agent/diagnostics as an INDEX incident.
        byFeature.put("searchIndexing", instanceFact("whether publishing also makes the page "
                + "findable in Turing (SH84); off by default, and failures arrive as INDEX "
                + "incidents on the change feed rather than as a call an agent makes"));

        // SH678. Reached, and read-only on purpose rather than as a first slice: MCP tools call
        // services in process, and an API token reaches /mcp while being barred from
        // /api/v2/marketplace (SH2's four namespaces), so an install verb here would hand a token
        // what the URL layer denies it — the non-goal names that shape by title. What the tool
        // delivers is the half a terminal cannot give a client that has no terminal, which is
        // discovery; installing is `shio marketplace install` or the console, where the credential
        // is the operator's.
        byFeature.put("marketplace", mcp("shio_marketplace", "the packages this instance could "
                + "install, with their parameters and whether each is already here (SH646); "
                + "read-only, because installing on this surface would be a token escalation"));

        byFeature.putAll(p1Exceptions());
        return byFeature;
    }

    /**
     * The P1 exception list — the inventory this test exists to produce.
     *
     * <p>Each entry is a capability the manifest advertises that the product's own primary operator
     * cannot reach the way P1 says it should. The {@code target} is what would resolve it, and it is
     * asserted <b>absent</b>: ship that tool or declare that route and this list fails until the entry
     * is removed, which is SH142's rule against a suppression file that rots.</p>
     */
    private static Map<String, Delivery> p1Exceptions() {
        Map<String, Delivery> byFeature = new TreeMap<>();

        byFeature.put("universalEditor", new Delivery(Reach.CONSOLE_ONLY, "/agent/editable",
                "SH185's bridge is a browser protocol and the console is its only client. `true` is "
                + "honest to a human and not to an agent; SH335 is what that costs in practice"));
        byFeature.put("reviewQueue", new Delivery(Reach.CONSOLE_ONLY, "/review/sessions",
                "SH109 put this off the agent namespace on purpose — it is the human's veto over "
                + "the agent's work, so an agent approving its own run is the thing it prevents. The "
                + "one exception here that is a design decision rather than a gap"));
        // SH732 — reclassified, and the old entry is worth keeping in view because of how it went
        // wrong. It read: "an agent can fill the trash (folder.delete, post.delete) and can neither
        // list nor restore it". SH222 answered the listing with `find?trash=true` and the restore
        // with `post.restore`/`folder.restore`; SH648 added `site.restore`. Both halves had been
        // false for a block, and the staleness check could not see it because the entry named a
        // route — `/agent/trash` — that was never built and never will be: the capability arrived
        // as a shape over a read (P10), which is the better design and the one the check was blind
        // to. `closedBy` now names the evidence so the same thing cannot happen quietly again.
        byFeature.put("trash", new Delivery(Reach.COMPOSED, null,
                "SH222/SH648 — `find?trash=true` lists it and post.restore, folder.restore and "
                + "site.restore bring things back, so the recoverability SH200 built is reachable "
                + "from where the deletion was made. PURGE is not, and that is the honest remainder: "
                + "an agent can undo a delete and cannot make one final",
                List.of("post.restore", "folder.restore", "site.restore")));
        // SH268 — a decision now, not a gap, and SH186 is what makes it one rather than a preference.
        // That task took a REST-only capability whose input is *JSON* and made it an op, reaching MCP
        // through an existing tool. An SH68 package is a **zip**: a client with no filesystem can
        // neither read one nor put one in a JSON-RPC message, and both ways of pretending otherwise
        // are worse than saying so — a URL the server fetches is SH101's request-forgery surface
        // argued again, and a base64 body is a megabyte of tokens for what `shio import` does in a
        // line. So the capability is CLI-shaped, `shio import` exists, and this row is the manifest
        // telling an MCP client which tool to reach for instead of letting it find out by failing.
        byFeature.put("exchange", new Delivery(Reach.CLI_ONLY, "shio import",
                "an SH68 package is a zip, and this surface is JSON — so import is CLI-shaped by "
                + "construction rather than by omission (SH268, decided against SH186's precedent). "
                + "`shio import <file>` posts it; export stays declared (GET /site/{id}/export) "
                + "because a zip *out* is a byte stream a caller can already ask for"));
        byFeature.put("postTypeMerge", new Delivery(Reach.REST_ONLY, "shio_apply.mergePostTypes",
                "a deliberate trade, not an oversight: the opt-in is a body field on the blueprint "
                + "apply, and adding it to shio_apply puts that tool over the SH132 per-tool ceiling "
                + "it is already 3 tokens under. Recorded so the trade stays visible"));
        byFeature.put("graphql", new Delivery(Reach.REST_ONLY, "/graphql",
                "the delivery surface a front end queries, undeclared here on purpose — an agent "
                + "reads content through /agent/read and the CDA. `true` describes what the instance "
                + "serves to the site, not what it offers the agent"));
        byFeature.put("contentAsFiles", new Delivery(Reach.CLI_ONLY, "shio_files",
                "Block I is `shio pull/push --content` and is CLI-shaped by design (P4 is about "
                + "Edit/Write/Grep on a disk). A client with no filesystem cannot use it, and the "
                + "flag does not say so"));
        byFeature.put("agentsMd", new Delivery(Reach.CLI_ONLY, "shio_context.format",
                "?format=agents-md renders the pack as project instructions and shio_context takes "
                + "no `format`, so the one output meant to be written to a file is reachable only by "
                + "whoever has a file to write it to"));
        byFeature.put("visualSnapshot", new Delivery(Reach.CLI_ONLY, "shio_snapshot",
                "reported `false`, which is the honest answer (SH102): `shio snapshot` rasterizes "
                + "and this surface has no endpoint and no tool. Classified rather than omitted so "
                + "flipping it to `true` has to pass through this table"));
        byFeature.put("siteCapture", new Delivery(Reach.CLI_ONLY, "shio_clone",
                "SH377 — the same two reasons as pageAudit, and one more that is decisive on its own: "
                + "the input is a URL somebody else controls, so an endpoint that fetched it is "
                + "SH101's request-forgery surface with a crawler attached. SH268 settled the boundary "
                + "and this is the case it was settled for"));
        byFeature.put("conversionProposal", new Delivery(Reach.CLI_ONLY, "shio_propose",
                "SH379 — CLI-shaped for the reason its input is: the markup it maps lives in the file "
                + "tree, and reading it over an endpoint would mean either uploading a stranger's HTML "
                + "or having the instance fetch it, which SH268 settled. The artefact is also a *file* "
                + "a reviewer argues with and partially accepts, which is P4's case rather than a "
                + "JSON-RPC one"));
        byFeature.put("pageAudit", new Delivery(Reach.CLI_ONLY, "shio_audit",
                "SH391 — and the one exception on this list that is a decision rather than a gap in "
                + "the same sense `reviewQueue` is. `shio audit` returns text an agent can act on, "
                + "so P1 would ordinarily demand a tool; two things refuse it. `tools/list` is "
                + "capped at 12 with 11 used and is paid every turn, while a Bash call is paid when "
                + "it is made. And it needs a *browser*: an endpoint would put Chromium in the JVM, "
                + "which is a standing non-goal — so unlike `contentAsFiles`, closing this one "
                + "would break something rather than fix it"));
        return byFeature;
    }

    @Test
    @DisplayName("every advertised capability is reachable, or is a named P1 exception")
    void everyAdvertisedCapabilityIsReachableOrNamed() {
        ShAgentManifest manifest = shAgentManifestService.manifest();
        Map<String, Boolean> features = manifest.features();
        Map<String, Delivery> classified = classification();
        Set<String> tools = toolNames();
        Set<String> resources = resourceUris();
        Set<String> declaredPaths = declaredPaths(manifest.endpoints());

        assertThat(classified.keySet())
                .as("""
                        every feature key is a claim about what this instance offers, so every one \
                        is a P1 decision — and the cheapest moment to make it is when the flag is \
                        written. A key here that the manifest no longer reports is the other half \
                        of the same rule (SH142): a classification for a capability that is gone \
                        would read as coverage.""")
                .containsExactlyInAnyOrderElementsOf(features.keySet());

        List<String> problems = new ArrayList<>();
        features.forEach((feature, advertised) -> {
            Delivery delivery = classified.get(feature);
            if (Boolean.FALSE.equals(advertised)) {
                // Nothing is claimed, so nothing has to be reachable. It stays classified.
                return;
            }
            if (delivery.why() == null || delivery.why().isBlank()) {
                problems.add(feature + ": no reason given");
            }
            switch (delivery.reach()) {
                case MCP_TOOL -> {
                    if (!tools.contains(delivery.target())) {
                        problems.add(feature + ": names tool '" + delivery.target()
                                + "', which is not registered");
                    }
                }
                case MCP_RESOURCE -> {
                    if (!resources.contains(delivery.target())) {
                        problems.add(feature + ": names resource '" + delivery.target()
                                + "', which is not registered");
                    }
                }
                case AGENT_ROUTE -> {
                    if (!declaredPaths.contains(delivery.target())) {
                        problems.add(feature + ": names route '" + delivery.target()
                                + "', which is not in the endpoint index");
                    }
                }
                case COMPOSED -> {
                    if (delivery.target() != null) {
                        problems.add(feature + ": a composed capability owns no target — the things "
                                + "that deliver it are claimed already, and naming one here would "
                                + "count the same capability twice");
                    }
                    if (delivery.closedBy().isEmpty()) {
                        problems.add(feature + ": composed, but names nothing that delivers it, so "
                                + "the claim is unfalsifiable");
                    }
                    for (String evidence : delivery.closedBy()) {
                        if (!tools.contains(evidence) && !declaredPaths.contains(evidence)
                                && !ShAgentBatchService.OPS.contains(evidence)) {
                            problems.add(feature + ": says '" + evidence + "' delivers it, and no "
                                    + "tool, route or op by that name exists");
                        }
                    }
                }
                case CROSS_CUTTING, INSTANCE_FACT -> {
                    if (delivery.target() != null) {
                        problems.add(feature + ": a cross-cutting property names a target, which "
                                + "means it is really a route — classify it as one");
                    }
                }
                case REST_ONLY, CONSOLE_ONLY, CLI_ONLY -> {
                    if (tools.contains(delivery.target()) || declaredPaths.contains(delivery.target())) {
                        problems.add(feature + ": exception is stale — '" + delivery.target()
                                + "' now exists, so the capability is reachable and the entry must go");
                    }
                    // SH732 — and the question the target alone cannot ask. An exception that named
                    // a route nobody built stays held forever while the capability arrives by ops,
                    // which is exactly what `trash` did between SH222 and SH648.
                    for (String evidence : delivery.closedBy()) {
                        if (tools.contains(evidence) || declaredPaths.contains(evidence)
                                || ShAgentBatchService.OPS.contains(evidence)) {
                            problems.add(feature + ": exception is stale — '" + evidence
                                    + "' exists, which this entry itself names as what would close "
                                    + "it, so the classification must be re-decided");
                        }
                    }
                }
            }
        });

        assertThat(problems)
                .as("""
                        P1 says every capability lands MCP → files/CLI → REST → console, so a \
                        `features` key reported true that no tool and no declared route delivers is \
                        the law being broken — and a stale exception is worse than none, because it \
                        reads as a known gap when the gap has been closed.""")
                .isEmpty();
    }

    @Test
    @DisplayName("the P1 exception list is exactly this, and the run prints it")
    void theExceptionListIsTheDeliverable() {
        Map<String, Boolean> features = shAgentManifestService.manifest().features();
        Map<String, Delivery> classified = classification();

        Map<Reach, List<String>> byReach = new LinkedHashMap<>();
        classified.forEach((feature, delivery) -> byReach
                .computeIfAbsent(delivery.reach(), ignored -> new ArrayList<>())
                .add(feature));

        List<String> unreachable = classified.entrySet().stream()
                .filter(entry -> entry.getValue().reach().isException())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        List<String> advertisedNow = unreachable.stream()
                .filter(feature -> Boolean.TRUE.equals(features.get(feature)))
                .toList();

        System.out.printf("%n[SH338] P1 conformance — %d features classified%n", classified.size());
        new TreeMap<>(byReach).forEach((reach, list) ->
                System.out.printf("  %-14s %2d  %s%n", reach, list.size(), new TreeSet<>(list)));
        System.out.printf("  not agent-reachable: %d, of which advertised true on this instance: "
                + "%d — %s%n%n", unreachable.size(), advertisedNow.size(), advertisedNow);

        assertThat(unreachable)
                .as("""
                        the inventory is the deliverable, so it is asserted exactly: gaining an \
                        entry must be a reviewable diff with a reason, and closing one must be an \
                        edit here rather than a number quietly moving. Only `reviewQueue` is a \
                        decision (SH109); the rest are gaps, most with a task id.

                        Asserted over the *classification* and not over what this instance \
                        advertises, because a config-driven flag (webhooks, previewTokens, \
                        scheduledPublish) would drop out of the list on an instance that disables \
                        it — and a P1 inventory that shrinks when an operator turns a subsystem off \
                        is measuring the environment instead of the code. SH315's lesson, one \
                        surface along.""")
                .containsExactly(
                        "agentsMd",
                        "contentAsFiles",
                        "conversionProposal",
                        "exchange",
                        "graphql",
                        "pageAudit",
                        "postTypeMerge",
                        "reviewQueue",
                        "siteCapture",
                        "universalEditor",
                        "visualSnapshot");

        assertThat(advertisedNow)
                .as("a flag that is off claims nothing, so it cannot be a P1 break — but it must "
                        + "still be classified, or flipping it on would bypass this table")
                .isSubsetOf(unreachable);
    }

    /**
     * SH393 — every exception on that list is <b>published</b>, and nothing else is.
     *
     * <h2>What this closes</h2>
     *
     * <p>The list above is the inventory, and until now it lived only here: an MCP client had no way
     * to learn that {@code exchange} is a zip and therefore CLI-shaped except by not finding a tool
     * and guessing why. SH268 argued that boundary, SH186 argued it, SH104, SH288, SH262 and SH391
     * each argued it — six tasks, one rule, derived six times from memory, which is the shape SH338
     * named: a law with no instrument.</p>
     *
     * <p>So the manifest carries {@code elsewhere}, and this asserts <b>set equality both ways</b>.
     * A new gap must be closed or published — it cannot be quietly classified. And a capability that
     * gains a real surface has to be deleted from two places, which is the failure the other
     * direction protects: an {@code elsewhere} entry outliving its exception would send a caller to
     * the CLI for something a tool now does, and nothing about the tool's arrival would say so.</p>
     */
    @Test
    @DisplayName("the P1 exception list is published in the manifest, and nothing else is")
    void everyExceptionSaysWhereTheCapabilityLives() {
        Map<String, String> elsewhere = shAgentManifestService.manifest().elsewhere();
        Set<String> exceptions = classification().entrySet().stream()
                .filter(entry -> entry.getValue().reach().isException())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

        assertThat(elsewhere.keySet())
                .as("""
                        `elsewhere` is the exception list made readable by the client that needs \
                        it. Equality both ways: a capability this surface does not carry and does \
                        not point at is one an agent learns about by failing (which is what SH268 \
                        did for eight of the first nine), and a pointer that outlives its exception \
                        sends the next caller to a CLI for something a tool now does.""")
                .containsExactlyInAnyOrderElementsOf(exceptions);

        assertThat(elsewhere.values())
                .as("a pointer is a command or a route, never an apology — the value is what the "
                        + "caller runs instead")
                .allSatisfy(where -> assertThat(where).isNotBlank());
        assertThat(List.copyOf(elsewhere.keySet()))
                .as("sorted, like features, because the manifest is byte-stable (P5/SH136)")
                .isSorted();
    }

    @Test
    @DisplayName("the guard is not vacuous: the three reachability checks can fail")
    void theGuardCanFail() {
        Set<String> tools = toolNames();
        Set<String> paths = declaredPaths(shAgentManifestService.manifest().endpoints());

        assertThat(tools)
                .as("if the registry came back empty every MCP_TOOL row would pass by accident, "
                        + "which is the lint-that-reports-zero-forever failure")
                .contains("shio_write", "shio_read", "shio_verify", "shio_remember")
                .hasSizeGreaterThanOrEqualTo(11);
        assertThat(resourceUris())
                .as("SH596 — the same vacuity question for the category it added. An empty resource "
                        + "registry would pass every MCP_RESOURCE row, which is precisely the shape "
                        + "of the finding: a classification that cannot be wrong is not a check")
                .contains("shio://manifest", "shio://context", "shio://diagnostics")
                .hasSizeGreaterThanOrEqualTo(5);
        assertThat(paths)
                .contains("/agent/resolve", "/post-type/{name}", "/render/post-types",
                        "/site-webhook", "/preview-token")
                .as("the paths a current exception awaits must still be absent, or the guard is "
                        + "asserting nothing")
                .doesNotContain("/agent/import", "/agent/trash", "/review/sessions");
    }

    private Set<String> toolNames() {
        return mcpTools.stream().map(ShMcpTool::name)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    /** The registered resource URIs (SH596), so an MCP_RESOURCE row is checked and not believed. */
    private Set<String> resourceUris() {
        return mcpResources.stream().map(ShMcpResource::uri)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> declaredPaths(List<ShAgentEndpoint> endpoints) {
        return endpoints.stream().map(ShAgentEndpoint::path)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }
}
