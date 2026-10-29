/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH707 — which of the agent's verbs a running instance has ever been asked.
 *
 * ## The gap
 *
 * The vocabulary is proved everywhere except where it is used. `shio-app` has forty-odd
 * `ShAgent*Test` classes, all MockMvc. `cli/test/` has sixty-odd module-level suites. And
 * `cli/conformance/`, the only tier with an instance behind it, calls **two** of the twelve MCP
 * tools — because SH561 set out to prove the transport, not the surface it carries.
 *
 * Every gap Block V names is a suite somebody has to write, and the first thing that has to exist
 * is the count. Otherwise the block ships six suites and nobody can say what fraction of the
 * vocabulary that bought. Same rule as SH338: before "write the tests", the instrument.
 *
 * ## Exercised means CALLED, and that distinction is the whole instrument
 *
 * The first draft of this counted any mention of a name anywhere under `conformance/`, and reported
 * five tools covered. Five is wrong: three of them appear in prose. A mention is what a comment
 * does; the claim being measured is that a client had to guess the name right, spell the input,
 * frame the request and read the refusal. So each population is extracted from its **call site**:
 *
 * - MCP tools — `mcp.call("<name>", …)`, the one path `mcp-stdio.mjs` offers.
 * - Batch ops — an `op:` field inside an ops array.
 * - CLI verbs — `import … from "../src/commands/<file>.mjs"`, which is how a conformance scenario
 *   drives a command; there is no shelling out.
 *
 * A looser reader would have made this file green on the day it shipped, which is the one thing it
 * must not be.
 *
 * ## A named list, never a ceiling
 *
 * Same contract as `assertion-debt.test.mjs` (SH527) and `lint-baseline.test.mjs` (SH701): the
 * unexercised verbs are held by name, so covering one is a deletion somebody reviews and adding a
 * verb without live coverage fails here rather than quietly lowering a percentage.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

import {
  batchOps as productVocabularyOps, cliVerbs, mcpResources, mcpTools,
} from "../conformance/support/product-vocabulary.mjs";
import { code } from "./support/source-text.mjs";

// @scans code reason: it reads the conformance suites to find which verbs are exercised, and a
// verb named only in a javadoc has not been spoken to an instance (SH740).

const CONFORMANCE = new URL("../conformance/", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
const BATCH_SERVICE = new URL(
  "../../shio-app/src/main/java/com/viglet/shio/api/agent/ShAgentBatchService.java",
  import.meta.url,
).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/** Every `.mjs` under `conformance/`, which is the only tier that has an instance. */
function conformanceSources(dir = CONFORMANCE, out = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) conformanceSources(path, out);
    else if (path.endsWith(".mjs")) out.push(path);
  }
  return out;
}

// Comments stripped (SH740/SH742): this asks which verbs a running instance has actually been
// asked, and a verb named in a javadoc explaining why it is NOT covered would answer yes.
const SOURCE = conformanceSources().map((f) => code(readFileSync(f, "utf8"))).join("\n");

/**
 * The batch op vocabulary, read from the service that dispatches it.
 *
 * <p>Derived rather than listed, for SH697's reason: a hand-typed vocabulary is the thing that
 * rots. `render.provision` is declared elsewhere and deliberately not counted here — this is the
 * content op table, which is what Block V is about.</p>
 *
 * <p>SH1068 moved the regex to `support/product-vocabulary.mjs`, where the docs gate reads the
 * same table: two readers of one table is how the two come to disagree about what an op is, and
 * this file's count is the one that would have looked right while the other went blind.</p>
 */
function batchOps() {
  return productVocabularyOps(BATCH_SERVICE);
}

/**
 * Tools reached through a `call("…")` — `mcp.call` directly, or a suite's own thin wrapper round it.
 *
 * <p>The receiver is optional and that widening was <b>forced by the instrument refusing to be
 * flattered</b>: SH708 drove all eight read tools through a local `call(name, args)` helper, and a
 * regex keyed on `.call(` credited none of them — coverage still read 2 of 12 with the suite
 * written and passing. Requiring the dot was over-tight, not strict.</p>
 *
 * <p>What it still requires is <b>call syntax with a literal name</b>. A mention in prose does not
 * match, which is the distinction the whole file rests on and the next test proves is still true.</p>
 */
function toolsCalled() {
  return new Set([...SOURCE.matchAll(/\bcall\(\s*["'`]([a-z_]+)["'`]/g)].map((m) => m[1]));
}

/**
 * Resources reached through `readResource("…")` (SH760).
 *
 * <p>The same rule the two readers around it apply: <b>call syntax with a literal argument</b>. A
 * `shio://…` in prose is what a comment does, and every one of the eight was argued for in prose
 * before this asked whether any had been fetched.</p>
 *
 * <p>Templates are matched by their declared prefix, because that is what `ShMcpResource#matches`
 * keys on — a scenario reading `shio://schema/Page` has exercised `shio://schema/`, and asking it
 * to name the literal prefix would be asking it to fetch a URI the server refuses.</p>
 */
function resourcesCalled(declared) {
  const asked = [...SOURCE.matchAll(/\breadResource\(\s*[`"']([^`"']+)[`"']/g)].map((m) => m[1]);
  const covered = new Set();
  for (const uri of asked) {
    for (const known of declared) {
      if (uri === known || (known.endsWith("/") && uri.startsWith(known))) covered.add(known);
    }
  }
  return covered;
}

/** Ops reached as `op: "…"` inside an ops array handed to the batch surface. */
function opsCalled() {
  return new Set([...SOURCE.matchAll(/\bop:\s*["'`]([a-z]+\.[a-z]+)["'`]/g)].map((m) => m[1]));
}

/**
 * Verbs a scenario drives, by either of the two ways a conformance suite can drive one.
 *
 * <h2>Importing the module, and spawning the bin</h2>
 *
 * <p>`import * as convert from "../src/commands/convert.mjs"` is the cheap form and was the only
 * one this reader knew. `shio(["pull", "--content", …])` — spawning `src/index.mjs` as a process —
 * is the <b>stronger</b> one: it is the only shape that exercises argv parsing, the exit code CI
 * reads, and the auth handshake. SH456 was a command that did its work and exited 1, which no
 * import can see.</p>
 *
 * <p>So the reader counting only the weaker form was backwards, and SH711 is what found it: a suite
 * that spawned ten verbs left this number at 7 of 31. Same correction as SH708's, from the other
 * direction — the instrument refusing to credit the better evidence.</p>
 *
 * <p>`content-pull.mjs` and `content-push.mjs` back the `pull` and `push` verbs, so the file name is
 * mapped rather than assumed; a reader that missed that would send somebody to write a suite that
 * already exists.</p>
 */
const MODULE_VERB = { "content-pull": "pull", "content-push": "push", "content-watch": "watch" };

function verbsCalled() {
  const imported = [...SOURCE.matchAll(/from\s+["'`][^"'`]*\/src\/commands\/([a-z-]+)\.mjs["'`]/g)]
    .map((m) => MODULE_VERB[m[1]] ?? m[1]);
  // The verb is argv[0] of the spawned bin, as a literal — call syntax with a literal name, which
  // is the same rule `toolsCalled` applies and the same reason: a mention must not count.
  const spawned = [...SOURCE.matchAll(/\bshio\(\s*\[\s*["'`]([a-z-]+)["'`]/g)].map((m) => m[1]);
  // And a table row that names the verb it drives. A sweep over ten verbs is naturally written as
  // data, so insisting the literal sit at the call site would count the one-off spawns and miss the
  // systematic ones — which is precisely backwards. `verb:` is specific enough to be a declaration
  // rather than a mention, and every hit is intersected with the real registry by the caller.
  const tabled = [...SOURCE.matchAll(/\bverb:\s*["'`]([a-z-]+)["'`]/g)].map((m) => m[1]);
  return new Set([...imported, ...spawned, ...tabled]);
}

/**
 * What no live suite has ever asked for — held by name.
 *
 * <h2>Measured at SH707, and the numbers in §XXIII.1 were both wrong</h2>
 *
 * <p>That section says thirteen MCP tools and thirty-three CLI commands. The product says
 * <b>twelve</b> and <b>thirty-one</b>, read from the registries by `product-vocabulary.mjs` — the
 * same drift SH697 found in the published docs, in the file that was arguing against hand-typed
 * counts. Both figures here are derived, so this note is the last time either is typed.</p>
 *
 * <h2>The shape of what is missing</h2>
 *
 * <p>It opened at <b>2 of 12</b> — `shio_context` and `shio_read`, a read and a read, with nothing
 * that <em>writes</em> ever spoken over a wire. SH708 took the read half and SH709 the write half,
 * so the tool row is now <b>12 of 12</b>.</p>
 *
 * <p>The op table still tells the other half of the story, and it is the same shape: the ops a
 * conformance scenario uses are the ones building a fixture needs, so `post.upsert` and
 * `post.publish` are covered and every op that <b>removes</b> or <b>moves</b> something is not.
 * Delete and move are where SH202, SH203 and SH243 each found a defect.</p>
 */
const UNEXERCISED = {
  // Empty since SH709, and left declared rather than deleted: SH708 took the read half and SH709
  // the write half, so all twelve are now spoken by a client that had to guess the name, spell the
  // schema and read the refusal. A key at `[]` makes a reintroduction a change to a declared zero
  // that a diff shows, rather than a tool quietly arriving with no live coverage.
  tools: [],
  ops: [
    // `folder.upsert` came off with SH708, whose fixture needs a folder. `folder.move` and
    // `post.delete` came off with SH712, which drives them out of order on purpose — the cycle
    // guard and the confirm token are exactly the shapes a wrong ordering reaches.
    //
    // What is left is every op that RESTORES or UNPUBLISHES, plus the deletes that act on a whole
    // container. SH648 gave a site delete an undo, so `site.delete` is no longer irreversible —
    // but it is still the widest gesture on the surface, and a suite that exercised it would be
    // trashing a whole site on an instance somebody else may be using. `site.restore` joins the
    // list for the ordinary reason the other two restores are on it: nothing drives it yet.
    //
    // `folder.copy` was the first entry this list ever gained from the matrix doing its job rather
    // than from an audit — the op shipped with SH244 and this assertion went red in the same run —
    // and it came off with SH748, which is the shape the whole tier is for: it was DECLARED while
    // nothing was running, because a live scenario written blind asserts what nobody observed, and
    // it was COVERED once an instance existed to answer. Three of its strings were measured before
    // they were asserted, and the pair of refusals turned out to carry different slugs and
    // different fixes, which is what made the third call worth making.
    // `post.rollback` arrives with SH987 and joins the list as the other restores did: nothing
    // live drives it yet. It is proved in ShAgentRevisionSurfaceTest, which is a proof about the
    // service and the plan rather than about the wire — and the thing a live scenario would add is
    // the one this op is most likely to get wrong, since a rollback is only meaningful against a
    // revision an earlier call in the same run produced.
    // The `release.*` family arrives with SH993 and joins the list together, which is the honest
    // shape while the family is half-built: a live scenario for a release is the one that PLANS it,
    // fills it and then RUNS it, and the run is the next slice. Written now it would assert the half
    // that exists and be rewritten by the task that finishes the other half. They are proved in
    // ShReleaseOpsTest against the service and the listing.
    // `note.add` and `note.resolve` arrive with SH1003 and join the list together, for the reason
    // the release family did: a live scenario for a note is the one that leaves a request on a page
    // an earlier call in the same run wrote, and then closes it — and the half of that which is
    // worth asserting live is that a curator's sentence reaches a resumed session, which is the
    // slice that puts notes on the read. They are proved in ShNoteTest against the service and the
    // op vocabulary.
    // `post.redirect` arrives with SH1064 and joins beside `post.move`, which is not a coincidence:
    // the live scenario for it IS the move's — rename a published page, then keep its old address
    // answering — so it will be covered by the run that covers `post.move` and by no other. It is
    // proved in ShRedirectOpTest against the service, the refusal and the lint it clears.
    // SH1277's `folder.acl` arrived the same way: ShAgentBatchTest proves the grant, the noop on a
    // re-apply, the unknown principal and the WRITE guard through MockMvc, which is the whole contract.
    // SH1285's `folder.visibility` likewise: ShAgentBatchTest proves the gate, the noop and the
    // refusals, and ShSitesMembersOnlyTest proves what a visitor, a stranger and a member each get
    // from /sites/** with the security filter chain in place.
    "folder.acl", "folder.delete", "folder.restore", "folder.visibility",
    "note.add", "note.resolve",
    "post.move", "post.redirect", "post.restore", "post.rollback", "post.unpublish",
    "release.add", "release.archive", "release.remove", "release.rollback", "release.run",
    "release.upsert",
    "site.delete", "site.restore",
  ],
  /**
   * The MCP resources no live suite has ever read (SH760).
   *
   * <p>It opens at **eight of eight**, which is the finding rather than an oversight: the
   * population did not exist, so nothing had ever asked. Three of these were argued into being
   * precisely as the route an MCP-only client would otherwise not have — `shio://ops` is the write
   * vocabulary (SH332), `shio://diagnostics` the failure report (SH596), `shio://usage` the spend
   * (SH754) — and each was reasoned about in prose without once being fetched over the wire.</p>
   *
   * <p>`shio://schema/` and `shio://blueprint/` are templates, held by the prefix
   * `ShMcpResource#matches` keys on: reading `shio://schema/Page` covers the entry.</p>
   */
  resources: [
    // `shio://usage` came off with SH761, and it is the right first one: the suite that reads it
    // is the suite proving the meter counts, so the resource is fetched because something needed
    // its answer rather than to lower a number. Seven left.
    // `shio://surfaces` arrived with SH979 and joins the list for the ordinary reason: nothing
    // live reads it yet. Its HTTP twin is asserted against the console's own declaration in
    // ShConsoleSurfaceP1Test, which is a proof about the document and not about the wire.
    // `shio://review/mine` arrives with SH1002, unexercised for a reason a scenario can fix: it
    // needs a run that wrote drafts AND a curator who rejected one of them, which is two actors in
    // one fixture. `ShAgentReviewReadOnlyTest` proves the scoping and the read-only shape; what a
    // live suite would add is that a real agent credential sees its own rejection over the wire.
    // `shio://index` arrives with SH1063 and joins on arrival: its live scenario is an instance
    // with indexing ON and a site deliberately routed nowhere, which is a deployment shape rather
    // than a content one — the conformance instance indexes nothing, so the table it would read is
    // "off" and every row empty. `ShIndexStatusTest` proves the table and the terse rendering.
    // `shio://requests` arrives with SH1006, for the two-actor reason `review/mine` gives: a live
    // scenario needs a curator's request from the dock and an agent credential answering it.
    // `ShNoteRelayTest` drives that round trip through the batch shio_write runs.
    // `shio://notes` arrives with SH1215 and joins on arrival: the conformance instance leaves no
    // note open, so a live read would answer "open notes: 0". `ShNoteRelayTest` reads it with one.
    "shio://blueprint/", "shio://blueprints", "shio://context", "shio://diagnostics",
    "shio://index", "shio://manifest", "shio://notes", "shio://ops", "shio://requests", "shio://review/mine",
    "shio://schema/", "shio://surfaces",
  ],
  verbs: [
    // SH997 — `gate` joins the list on arrival: its live scenario is a pipeline that declares a
    // gate, pushes content that fails it and reads the refusal, which is the SH748 shape (declared
    // while nothing runs it, covered once an instance answers).
    "gate",
    // SH1001 — `review` joins on arrival, for `gate`'s reason: its live scenario is a run that
    // leaves drafts, a decision against one of them, and the queue reporting what is still
    // waiting — declared while nothing runs it, covered once an instance answers.
    "review",
    // SH1003 — `notes` joins with the ops it reads: a live scenario leaves a request on a page and
    // watches the work list carry it, which is the same fixture `note.add` needs.
    "notes",
    // SH1063 — `index` joins with the resource it prints, and for the same reason: the state worth
    // driving live is an instance that indexes, and the conformance instance does not.
    "index",
    // SH711 spawned ten of these as a process. What is left splits three ways, and the split is
    // more useful than the count:
    //
    // - `dev`, `build`, `deploy` — declared out of scope in `cli-surface.conformance.mjs`, with the
    //   reason: a front-end toolchain and a hosting target are not this instance.
    // - `types` needs a CDA API token, which `/api/v2/cda/**` requires and a console credential
    //   does not carry (SH2). Covering it means minting one, which is its own decision.
    // - the rest are simply unwritten: a verb somebody has to drive and assert.
    // `surfaces` arrived with SH979. It reads the manifest with one include and prints it, which
    // is the shape a MockMvc proof settles; a live scenario would be asserting the same document
    // twice.
    // `versions` and `rollback` arrive with SH987. Both are unwritten rather than out of scope:
    // they need a live instance with a published post behind them, which is exactly the fixture a
    // live scenario has and a MockMvc proof invents.
    // `assets` arrives with SH1034 and is unwritten rather than out of scope, for a reason worth
    // naming: its whole answer is a count that the instance derives at write time, so a live
    // scenario is the only place it can be wrong — upload an asset, reference it from a page,
    // assert it is not offered for pruning, unreference it, assert it is. A scripted fetch proves
    // the arithmetic and the zero-versus-absent refusal (assets-prune.test.mjs) and cannot prove
    // that the number the server sent was true.
    // `patterns` arrives with SH1040 and is unwritten rather than out of scope, for the reason
    // `assets` gives one entry up: what a scripted fetch proves is the DOCUMENT this verb composes,
    // and what only a live run can prove is that applying it leaves the page it names holding the
    // sections in the order it asked for — capture from one page, place into another, read the
    // second back. The composition is asserted in `patterns.test.mjs`; the outcome is not.
    "annotations", "apply", "assets", "audit", "build", "deploy", "dev", "impact", "import",
    "judge", "patterns",
    // `preview` arrives with SH1095 and is unwritten rather than out of scope: its outcome is a
    // render of values the instance never stored, which only a live page can prove.
    "preview",
    // `share` arrives with SH1111 and is unwritten rather than out of scope: what only a live run can
    // prove is that the printed link, opened by a browser with no session, lands on the page and
    // refuses its neighbours - the exchange and the render, which a scripted fetch invents.
    "share",
    // `schedule` arrives with SH991, and for `versions`' reason: it needs a live instance holding a
    // scheduled post, which a live scenario has and a scripted fetch invents.
    // `status` arrives with SH1050 and is unwritten rather than out of scope. Its numbers are
    // asserted against a seeded instance in `ShConsoleSummaryAPITest`, which is a proof about the
    // counts; what only a live run can prove is that the rows a curator sees on the dashboard and
    // the rows this prints are the same rows — the claim the one endpoint was built to make.
    // `redirects` arrives with SH1064 and is unwritten rather than out of scope, for the reason
    // `assets` gives: its answer is derived from history — the addresses a site used to serve —
    // so the only place it can be wrong is an instance that has actually renamed something. A
    // live scenario would publish a page, rename it, and read the orphan back. `ShRedirectOpTest`
    // proves the finding and its repair against a seeded site; the wire is not proved.
    // `export` arrives with SH1065 and is unwritten rather than out of scope: its live scenario is
    // the round trip — export a site this run built, import it into a second instance, and read the
    // same addresses back — which is the one assertion a scripted fetch cannot make, since what it
    // proves is that the zip is ACCEPTED by the importer rather than merely produced.
    "export", "mcp", "publish", "redirects", "remember", "report", "rollback", "schedule",
    "snapshot", "status", "surfaces", "types", "versions",
  ],
};

test("SH707: the live tier's coverage of the agent vocabulary is the declared list", () => {
  const resources = mcpResources();
  const populations = {
    tools: { all: mcpTools(), called: toolsCalled() },
    ops: { all: batchOps(), called: opsCalled() },
    verbs: { all: cliVerbs(), called: verbsCalled() },
    // SH760 — the fourth. It was absent, and the absence was the finding: the matrix reported full
    // coverage of three populations while a fourth sat at zero, which reads as reassurance. Same
    // shape SH596 recorded one level up — a classification can only be as honest as its categories.
    resources: { all: resources, called: resourcesCalled(resources) },
  };

  const missing = {};
  const lines = [];
  for (const [name, { all, called }] of Object.entries(populations)) {
    missing[name] = [...all].filter((verb) => !called.has(verb)).sort();
    lines.push(`  ${all.size - missing[name].length}/${all.size} ${name}`);
  }
  console.log(`SH707 live coverage — what a running instance has ever been asked:\n${lines.join("\n")}`);

  assert.deepEqual(missing, {
    tools: [...UNEXERCISED.tools].sort(),
    ops: [...UNEXERCISED.ops].sort(),
    verbs: [...UNEXERCISED.verbs].sort(),
    resources: [...UNEXERCISED.resources].sort(),
  },
  "SH707: the set of verbs no live suite has ever called is not the declared one. If you covered "
  + "one, delete it from UNEXERCISED in the same commit — an entry claiming a gap that is closed "
  + "sends the next person to write a suite that exists. If you ADDED a verb, it arrived with no "
  + "live coverage: either write the scenario, or say in the diff why a MockMvc proof is enough "
  + "for this one. A percentage would have absorbed both silently, which is why this is a list.");
});

test("SH760: the resource reader credits a call and not a mention, and a template by its prefix", () => {
  // The vacuity guard this population needs more than the other three do, because it opens at
  // ZERO: an empty answer from a reader that has stopped matching is indistinguishable from the
  // true finding, and the true finding is what this shipped to report. So the reader is driven
  // against a sample where the right answer is known.
  const declared = new Set([
    "shio://manifest", "shio://ops", "shio://schema/", "shio://blueprint/",
  ]);
  // Single-quoted, so the sample can hold the backtick form a real call site may use without
  // this file needing to escape it — the escaping is exactly what went wrong writing this test.
  const sample = [
    '// A comment naming shio://ops and a sentence about shio://manifest, neither fetched.',
    'const pack = await mcp.readResource("shio://manifest");',
    'const one = await mcp.readResource(`shio://schema/Page`);',
  ].join("\n");

  // The same expression `resourcesCalled` uses. Stated here rather than shared, so the sample
  // proves the RULE and loosening the reader has to be done in two places on purpose — the trade
  // the tools guard above already takes, for the same reason.
  const asked = [...sample.matchAll(/\breadResource\(\s*[`"']([^`"']+)[`"']/g)].map((m) => m[1]);
  const covered = new Set();
  for (const uri of asked) {
    for (const known of declared) {
      if (uri === known || (known.endsWith("/") && uri.startsWith(known))) covered.add(known);
    }
  }

  assert.deepEqual([...covered].sort(), ["shio://manifest", "shio://schema/"],
    "SH760: a fetched URI counts and a mentioned one does not — `shio://ops` appears in that "
    + "sample as prose and is covered by neither. That distinction is the whole reason this "
    + "population is worth having: all eight were argued for in prose before any was fetched.");
  assert.ok(!covered.has("shio://blueprint/"),
    "SH760: a template nobody read must not be credited by another template being read");
});

test("SH707: exercised means called, not mentioned", () => {
  // The guard on the reader itself, and it is written against a **sample** rather than against the
  // corpus on purpose. It used to anchor on `shio_write` being named in prose and called nowhere;
  // SH709 called it, and an assertion whose premise the work keeps invalidating is one that gets
  // deleted rather than fixed. The rule it protects does not change: a mention is what a comment
  // does, and the claim being measured is that a client framed a request.
  const prose = `
    A comment about shio_write and shio_publish, and a sentence naming shio_apply.
    A markdown list: - shio_remember, the note that outlives the call.
  `;
  const calls = `
    await mcp.call("shio_read", { address });
    const answer = await call('shio_find', { q });
  `;

  // The same expression `toolsCalled` uses. Stated here rather than shared, so the sample
  // proves the RULE, and loosening the reader has to be done in two places on purpose.
  const readerOver = (text) => new Set(
    [...text.matchAll(/\bcall\(\s*["'`]([a-z_]+)["'`]/g)].map((m) => m[1]),
  );

  assert.deepEqual([...readerOver(prose)], [],
    "SH707: the reader credited a tool that only appears in prose. That is how the first draft "
    + "reported five tools covered when two were, and a coverage number that counts comments is "
    + "worse than no number — it is a number somebody will quote.");
  assert.deepEqual([...readerOver(calls)].sort(), ["shio_find", "shio_read"],
    "SH707: the reader must see BOTH shapes — `mcp.call(...)` and a suite's own `call(...)` "
    + "wrapper. Missing the second is what made SH708 read as uncovered while it was passing.");
});

test("SH707: the populations are read from the product, not from this file", () => {
  // SH697's rule. Every count above is derived; if a registry ever reads empty, this file would
  // report perfect coverage of nothing and pass.
  assert.ok(mcpTools().size >= 10, "SH707: the MCP registry read as empty or nearly so");
  assert.ok(cliVerbs().size >= 25, "SH707: the CLI command registry read as empty or nearly so");
  assert.ok(batchOps().size >= 10, "SH707: the batch op table read as empty or nearly so");

  assert.ok(!mcpTools().has("shio_not_a_tool"),
    "SH707: `shio_not_a_tool` is the name mcp-transport.conformance.mjs calls to assert a refusal. "
    + "It must never be in the registry — and a reader that took its population from the call "
    + "sites rather than from the product would have invented it.");
});
