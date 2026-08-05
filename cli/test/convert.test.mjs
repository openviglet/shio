/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH417 — `shio convert`, the verb the pipeline was one short of.
 *
 * SH379 stopped at the proposal on purpose: `--accept` records a review decision and applies nothing,
 * because review and mutation in one command is the surprise P7 forbids. That left the plan a file
 * whose only reader was the report that produced it. This is the other half, and what is worth pinning
 * is not the translation — a section's fields are already in the proposal — but the three things a
 * write owes: it refuses a **stale** acceptance, it lands in **DRAFT**, and it refuses when the target
 * cannot hold what it carries (SH436).
 *
 * Network-free: the desired-state document is built by a pure function, and the command's own IO is a
 * scripted `fetch`.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { SECTIONS_FOLDER, THEME_FOLDER, buildConversion } from "../src/convert.mjs";
import * as convert from "../src/commands/convert.mjs";
import { INVENTORY_FILE, MARKUP_DIR, markupFileFor } from "../src/clone.mjs";
import { PLAN_FILE } from "../src/propose.mjs";
import { norm } from "./support/paths.mjs";

// --- the translation ---

const candidate = (id, extra = {}) => ({
  id,
  type: "SectionHero",
  mode: "authorable",
  fields: { heading: "Home", body: "<p>Words.</p>" },
  pages: ["/"],
  ...extra,
});

test("SH417: an accepted candidate becomes one section post, in DRAFT", () => {
  // One post per candidate, not per page: a candidate IS a signature, so the hero that recurs on
  // twelve pages is one section reused — which is the whole reason the proposal groups by structure.
  const { document, sections } = buildConversion({
    proposal: { candidates: [candidate("b1a2"), candidate("c3d4", { mode: "fidelity" })] },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.equal(sections.length, 1, "the unaccepted candidate stays verbatim markup");
  assert.deepEqual(document.posts[0], {
    url: `${SECTIONS_FOLDER}/b1a2`,
    type: "SectionHero",
    folder: SECTIONS_FOLDER,
    data: { heading: "Home", body: "<p>Words.</p>" },
  });
  assert.ok(
    !("publish" in document.posts[0]),
    "absent, not false: absent means leave the publish state alone, so re-running cannot unpublish a "
      + "page a curator has since published — and P7's draft-default is why a human is in this loop",
  );
  assert.deepEqual(document.folders, [{ path: SECTIONS_FOLDER }]);
});

test("SH417: a stale acceptance refuses the whole document rather than writing part of it", () => {
  // The rule the structural id exists to make checkable. An acceptance from a run taken before the
  // source changed names a block that is not there now, and writing anything for it would apply a
  // decision nobody made about this markup.
  const { document, stale } = buildConversion({
    proposal: { candidates: [candidate("b1a2")] },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }, { block: "gone99", type: "SectionCta" }] },
    site: "Acme",
  });

  assert.deepEqual(stale, ["gone99"]);
  assert.equal(document, null, "all or nothing: a half-applied conversion is the worst outcome here");
});

test("SH417: the theme and the scripts ride the same document, with the render vocabulary's names", () => {
  const { document, extras } = buildConversion({
    proposal: {
      candidates: [candidate("b1a2")],
      theme: { rows: [{ path: "brand", source: "declared" }], css: ":root{}", tokens: { brand: { $value: "#0b5533" } } },
      scripts: [{ src: "https://x.test/a.js", position: "head" }],
    },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(extras, ["Theme", "SiteScripts"]);
  const theme = document.posts.find((post) => post.type === "Theme");
  assert.equal(theme.folder, THEME_FOLDER);
  assert.deepEqual(Object.keys(theme.data).sort(), ["CSS", "TITLE", "TOKENS"],
    "uppercase: a section's fields are the blueprint's vocabulary and a Theme's are the renderer's");
  assert.deepEqual(JSON.parse(theme.data.TOKENS), { brand: { $value: "#0b5533" } },
    "the TOKENS field is the DTCG tree ShThemeTokens.parse reads, serialized");
  const scripts = document.posts.find((post) => post.type === "SiteScripts");
  assert.deepEqual(JSON.parse(scripts.data.SCRIPTS)[0].src, "https://x.test/a.js");
});

test("SH417: a proposal with no theme declares no theme folder", () => {
  const { document, extras } = buildConversion({
    proposal: { candidates: [candidate("b1a2")] },
    plan: { accepted: [{ block: "b1a2", type: "SectionHero" }] },
    site: "Acme",
  });

  assert.deepEqual(extras, []);
  assert.deepEqual(document.folders, [{ path: SECTIONS_FOLDER }]);
});

// --- the command ---

const PAGE = "<html><body><main>"
  + "<section class=hero><h1>Home</h1><p>Welcome to the site.</p><a href=/go>Start</a></section>"
  + "</main></body></html>";

function ctxWith({ plan = null, site = { "/": PAGE }, fetchImpl } = {}) {
  const files = new Map();
  files.set(norm(`/repo/${INVENTORY_FILE}`), JSON.stringify({
    version: 3,
    source: { origin: "https://x.test", seed: "https://x.test" },
    crawl: { pages: 1, truncated: false, markup: true },
    pages: Object.keys(site).map((path) => ({ path, title: path, markup: markupFileFor(path) })),
  }));
  for (const [path, html] of Object.entries(site)) {
    files.set(norm(`/repo/${MARKUP_DIR}/${markupFileFor(path)}`), html);
  }
  if (plan) files.set(norm(`/repo/${PLAN_FILE}`), JSON.stringify(plan));
  const written = {};
  const calls = [];
  return {
    env: {},
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    written,
    calls,
    async fetch(url, init = {}) {
      calls.push({ url: String(url), method: init.method ?? "GET", body: init.body });
      return fetchImpl ? fetchImpl(String(url), init) : json({ changed: 3 });
    },
    fs: {
      async mkdir() {},
      async writeFile(path, body) {
        written[norm(path)] = body;
        files.set(norm(path), body);
      },
      async readFile(path) {
        const body = files.get(norm(path));
        if (body === undefined) {
          const error = new Error(`ENOENT: ${path}`);
          error.code = "ENOENT";
          throw error;
        }
        return body;
      },
    },
  };
}

const json = (body, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: () => "application/json" },
  async json() {
    return body;
  },
  async text() {
    return JSON.stringify(body);
  },
});

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

/** The id the current markup produces, read the way a reviewer would. */
async function idOf(ctx) {
  const { proposalFor } = await import("../src/commands/propose.mjs");
  const { proposal } = await proposalFor({}, ctx, "propose");
  return proposal.candidates[0].id;
}

test("SH417: --out writes the document and applies nothing", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const ctx = ctxWith({ plan: { accepted: [{ block: id, type: "SectionHero" }] } });

  const { applied } = await convert.run({ site: "Acme", out: "conversion.json" }, [], ctx);

  assert.equal(applied, false);
  assert.deepEqual(ctx.calls, [], "no instance was touched, which is what makes this reviewable");
  const document = JSON.parse(ctx.written[norm("/repo/conversion.json")]);
  assert.equal(document.site, "Acme");
  assert.equal(document.posts[0].type, "SectionHero");
  assert.match(ctx.stdout.text, /every post lands in DRAFT/);
});

test("SH417: --dry-run plans and writes nothing; a bare run applies", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const plan = { accepted: [{ block: id, type: "SectionHero" }] };

  const dry = ctxWith({ plan });
  await convert.run({ site: "Acme", dryRun: true }, [], dry);
  assert.ok(dry.calls.every((call) => call.url.includes("/agent/plan")), "plan, never apply");
  assert.match(dry.stdout.text, /nothing was written/);

  const wet = ctxWith({ plan });
  await convert.run({ site: "Acme" }, [], wet);
  assert.ok(wet.calls.some((call) => call.url.includes("/agent/apply")));
  assert.match(wet.stdout.text, /in DRAFT/);
});

test("SH417: nothing accepted is a refusal that names the two commands, not an empty write", async () => {
  const ctx = ctxWith();

  await assert.rejects(
    () => convert.run({ site: "Acme" }, [], ctx),
    /nothing has been accepted[\s\S]*shio propose --accept/,
  );
  assert.deepEqual(ctx.calls, []);
});

test("SH417: no --site is refused before anything is read", async () => {
  const ctx = ctxWith();

  await assert.rejects(() => convert.run({}, [], ctx), /pass the site to write into/);
});

test("SH436: a target missing Theme.TOKENS is refused with the reconcile call, before the write", async () => {
  // The precondition SH436 put in the artefact, checked at the one moment there is an instance to ask.
  // A theme written into a Theme with no TOKENS either fails somewhere unhelpful or writes the theme
  // without its tokens, after a capture, a conversion and a review have all been paid for.
  const withStyles = {
    "/": PAGE.replace("<body>", "<body><style>:root{--brand:#0b5533}</style>"),
  };
  const probe = ctxWith({ site: withStyles });
  const id = await idOf(probe);
  const ctx = ctxWith({
    site: withStyles,
    plan: { accepted: [{ block: id, type: "SectionHero" }] },
    fetchImpl: (url) => (url.includes("/render/post-types/drift")
      ? json([{ postType: "Theme", missingFields: ["TOKENS"] }])
      : json({ changed: 1 })),
  });

  await assert.rejects(
    () => convert.run({ site: "Acme" }, [], ctx),
    /missing Theme\.TOKENS[\s\S]*render\/post-types\/reconcile/,
  );
  assert.ok(
    !ctx.calls.some((call) => call.url.includes("/agent/apply")),
    "and it refused BEFORE writing, which is the whole point of asking first",
  );
});

test("SH436: a sound instance is not asked twice, and a dry run does not ask at all", async () => {
  const withStyles = { "/": PAGE.replace("<body>", "<body><style>:root{--brand:#0b5533}</style>") };
  const probe = ctxWith({ site: withStyles });
  const id = await idOf(probe);
  const plan = { accepted: [{ block: id, type: "SectionHero" }] };

  const sound = ctxWith({
    site: withStyles,
    plan,
    fetchImpl: (url) => (url.includes("drift") ? json([]) : json({ changed: 2 })),
  });
  await convert.run({ site: "Acme" }, [], sound);
  assert.equal(sound.calls.filter((call) => call.url.includes("drift")).length, 1);

  const dry = ctxWith({ site: withStyles, plan });
  await convert.run({ site: "Acme", dryRun: true }, [], dry);
  assert.ok(
    !dry.calls.some((call) => call.url.includes("drift")),
    "a dry run writes nothing, so a precondition on writing has nothing to guard",
  );
});

// --- SH439: a refusal that names a remedy this surface can express ---
//
// `shio convert --site Viglet2` against an absent site printed the plan and then refused with the
// write resolver's fix: "add a `site.upsert` op for it earlier in the same batch." Un-followable
// here — `convert` composes its own document and takes no ops — and it landed at the worst moment,
// because converting into a **new** site is the normal shape of a replication. P6 makes an
// instruction naming something that does not exist worse than none. So the flag, and the message.

test("SH439: --create-site rides the document, and its absence leaves the document as it was", () => {
  const proposal = { candidates: [candidate("b1a2")] };
  const plan = { accepted: [{ block: "b1a2", type: "SectionHero" }] };

  const asked = buildConversion({ proposal, plan, site: "Acme", createSite: true });
  assert.equal(asked.document.createSite, true);

  const not = buildConversion({ proposal, plan, site: "Acme" });
  assert.ok(
    !("createSite" in not.document),
    "absent rather than false: every document written before SH439 is the same bytes, and creating "
      + "a site nobody asked for is the surprise P7 forbids (P5)",
  );
});

test("SH439: `convert --create-site` sends the flag, and `--out` writes it into the file", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const plan = { accepted: [{ block: id, type: "SectionHero" }] };

  const wet = ctxWith({ plan });
  await convert.run({ site: "Acme", createSite: true }, [], wet);
  const sent = JSON.parse(wet.calls.find((call) => call.url.includes("/agent/apply")).body);
  assert.equal(sent.createSite, true, "the flag is the document's, so plan and apply agree by construction");

  const out = ctxWith({ plan });
  await convert.run({ site: "Acme", createSite: true, out: "conversion.json" }, [], out);
  const document = JSON.parse(out.written[norm("/repo/conversion.json")]);
  assert.equal(
    document.createSite,
    true,
    "an --out document must mean what the run meant, or `shio apply conversion.json` is a different act",
  );
});

test("SH439: an absent site is refused with --create-site, not with an op convert cannot take", async () => {
  const probe = ctxWith();
  const id = await idOf(probe);
  const ctx = ctxWith({
    plan: { accepted: [{ block: id, type: "SectionHero" }] },
    // The write resolver's own body, verbatim in shape: correct on the batch surface, and the
    // reason this task exists on this one.
    fetchImpl: (url) => (url.includes("/agent/apply") || url.includes("/agent/plan")
      ? json({
        type: "https://shio.viglet.com/errors/unknown-site",
        title: "No readable site 'Viglet2'",
        detail: "No site with that id or name is visible to this credential.",
        fix: "Address a site that exists, or add a `site.upsert` op for it earlier in the same batch.",
        example: "{\"op\":\"site.upsert\",\"address\":\"site:Viglet2\"}",
      }, 404)
      : json([])),
  });

  const failure = await convert.run({ site: "Viglet2" }, [], ctx).then(() => null, (error) => error);

  assert.ok(failure, "an absent site is still a refusal — the flag is the remedy, not the default");
  const problem = JSON.parse(failure.body);
  assert.match(problem.fix, /--create-site/);
  assert.ok(
    !problem.fix.includes("site.upsert"),
    "the op is what this surface cannot express; naming it is the defect, not the message's length",
  );
  assert.equal(problem.example, "shio convert --site Viglet2 --create-site");
  assert.equal(
    problem.title,
    "No readable site 'Viglet2'",
    "and the resolver's own title, detail and didYouMean stay: they are about the site, not the surface",
  );
});
