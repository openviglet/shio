/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH422 — **the seams**, which is the one thing eleven green test files do not cover.
 *
 * Block Q shipped as eleven tasks and each is thoroughly tested in isolation: the crawl has a fake
 * site, the proposal has a fake filesystem, the coverage group has a fixture capture, the render
 * helpers have their own databases. Nothing runs the pipeline. And the phases hand artefacts to each
 * other, so every handoff is a shape agreed in two places — `clone --markup` names a file `propose`
 * resolves by slug, `clone --assets` folds a source-URL-to-tree-path map the theme's `url()` rewrite
 * reads, `propose --accept` writes an id a later proposal reads back, and the coverage group reads an
 * `unfollowed` list a version-2 inventory carries and a version-1 one does not. Each of those is
 * pinned on one side by a unit test that builds the other side **by hand**, which is exactly the
 * shape that passes while the real producer writes something else.
 *
 * So this file has one test, and its subject is the handoffs rather than any phase: crawl a served
 * map of pages, propose, accept one candidate, and run `verify --checks projection,replication` —
 * each step reading only what the step before it actually wrote.
 *
 * Two deliberate choices about the harness:
 *
 * - **A real temp directory**, not the in-memory `fs` every other file here uses. A fake filesystem
 *   is right for a phase and wrong for a chain: the artefact a phase writes is the assertion, so the
 *   test must not be the thing that decides what is on disk between two of them.
 * - **No instance and no browser**, which is a property of the pipeline and not a limitation of the
 *   test. Every phase but the render is the CLI's, and `snapshot --against` — the one step that
 *   needs a browser — compares through an injected capture that `snapshot.test.mjs` already drives.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as nodeFs from "node:fs/promises";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { INVENTORY_FILE, INVENTORY_VERSION, MARKUP_DIR } from "../src/clone.mjs";
import { ASSETS_DIR } from "../src/content-assets.mjs";
import { PLAN_FILE, PROPOSAL_FILE } from "../src/propose.mjs";
import { flatten } from "../src/theme.mjs";
import { READS_VERSION } from "../src/verify-replication.mjs";
import * as clone from "../src/commands/clone.mjs";
import * as propose from "../src/commands/propose.mjs";
import * as verify from "../src/commands/verify.mjs";

// --- the source, served ---

/**
 * Two pages sharing a hero and a feature grid, one stylesheet, and two links the crawl will not reach.
 *
 * The shared structure is what makes a candidate (a block that recurs), the stylesheet is what makes
 * a theme, and `/admin` (robots) and `/deep` (past `--depth 1`) are what make the `unfollowed` rows.
 * All three are inputs to *later* phases, which is the only reason they are here. Nothing 404s: an
 * unreadable page is its own finding, and one in the fixture would blur the rule being asserted.
 */
const SITE = {
  "/robots.txt": { type: "text/plain", body: "User-agent: *\nDisallow: /admin\n" },
  "/": { body: page("Home", "Acme", ['<a href="/about">About</a>']) },
  "/about": { body: page("About", "Who we are", ['<a href="/deep">Deeper still</a>']) },
  "/deep": { body: page("Deep", "Never fetched", []) },
  "/admin": { body: page("Admin", "Disallowed", []) },
  "/css/site.css": {
    type: "text/css",
    body: `:root { --ink: #102a43 }
      .hero { color: #102a43; background: #f0f4f8 url("/img/hero.png") no-repeat }
      .hero h1 { color: #102a43; font-size: 2rem }
      .card { color: #102a43; background: #f0f4f8 }`,
  },
  "/img/hero.png": { type: "image/png", body: "PNG-BYTES" },
};

function page(title, lead, links) {
  return `<!doctype html>
<html lang="en">
<head>
  <title>${title}</title>
  <link rel="stylesheet" href="/css/site.css">
</head>
<body>
  <main>
    <section class="hero">
      <h1>${title}</h1>
      <p>${lead}. ${"Body copy that reads like a page a person wrote. ".repeat(4)}</p>
      <a href="/admin">Start</a>
    </section>
    <section class="features">
      <h2>Features</h2>
      <img src="/img/hero.png" alt="Hero">
      <div class="card"><h3>Fast</h3><p>Measured, not asserted.</p></div>
      <div class="card"><h3>Safe</h3><p>Draft by default.</p></div>
    </section>
    ${links.join("\n    ")}
  </main>
</body>
</html>`;
}

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

function served(site) {
  const requested = [];
  return {
    requested,
    async fetch(url) {
      requested.push(url);
      const parsed = new URL(url);
      const entry = site[parsed.pathname];
      if (!entry) {
        return response({ status: 404, body: "not found" });
      }
      return response({ ...entry, url });
    },
  };
}

function response({ status = 200, body = "", type = "text/html; charset=utf-8" } = {}) {
  const headers = new Map([["content-type", type]]);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => headers.get(String(name).toLowerCase()) ?? null },
    async text() {
      return body;
    },
    async arrayBuffer() {
      return new TextEncoder().encode(body).buffer;
    },
  };
}

const json = async (cwd, file) => JSON.parse(await readFile(join(cwd, file), "utf8"));

test("SH422: capture → propose → accept → verify, each phase reading what the last one wrote", async () => {
  const cwd = await mkdtemp(join(tmpdir(), "shio-sh422-"));
  const source = served(SITE);
  const ctx = {
    env: {},
    cwd,
    stdout: sink(),
    stderr: sink(),
    fetch: source.fetch,
    fs: nodeFs,
  };

  try {
    // ── phase one: the capture ──────────────────────────────────────────────────────────
    // `--depth 1` on purpose: /deep is linked from /about and never fetched, which is the state
    // the last phase's `unfollowed` rule exists for and the one a deeper crawl would hide.
    const captured = await clone.run(
      { markup: true, assets: true, site: "Acme", depth: 1 },
      ["https://acme.test"],
      ctx,
    );

    const inventory = await json(cwd, INVENTORY_FILE);
    assert.deepEqual(
      captured.inventory,
      inventory,
      "what the command reported and what it left on disk are the same artefact — every assertion "
        + "below reads the file, because the file is what the next phase gets",
    );
    assert.equal(
      inventory.version,
      READS_VERSION,
      "the version `clone` writes is the version the coverage group reads — asserted across the two "
        + "modules rather than inside either, because a bump on one side is invisible to both suites",
    );
    assert.equal(inventory.version, INVENTORY_VERSION);
    assert.deepEqual(
      inventory.pages.map((row) => row.path).sort(),
      ["/", "/about"],
      "the walk stopped at the origin, honoured robots (/admin) and was cut by --depth (/deep)",
    );

    // The markup handoff: the inventory names a file per page, and the file is the proposal's input.
    for (const row of inventory.pages) {
      assert.ok(row.markup, `page ${row.path} names its cached markup`);
      const cached = await readFile(join(cwd, MARKUP_DIR, row.markup), "utf8");
      assert.match(cached, /<section class="hero">/, "the named file holds that page's markup");
    }

    // The asset handoff: source path → tree path, the map the theme's url() rewrite reads.
    const stylesheet = inventory.assets.find((asset) => asset.kind === "stylesheet");
    assert.ok(stylesheet?.file, "the stylesheet was downloaded and its tree path recorded");
    await readFile(join(cwd, ASSETS_DIR, stylesheet.file), "utf8");
    assert.deepEqual(
      inventory.unfollowed.map((row) => row.path),
      ["/admin", "/deep"],
      "two links the walk saw and never followed, cut for two different reasons — /admin by "
        + "robots.txt and /deep by --depth — and neither is in `pages` or in the tree. 'Absent from "
        + "the source' and 'absent from the capture' are opposite claims (SH387)",
    );

    // ── phase two: the proposal ─────────────────────────────────────────────────────────
    const { proposal } = await propose.run({}, [], ctx);

    const hero = proposal.candidates.find((row) => row.type === "SectionHero");
    assert.ok(hero, "the block that recurs on both captured pages is one candidate");
    assert.deepEqual(
      hero.pages,
      ["/", "/about"],
      "a candidate's pages are the paths the *crawl* recorded, which is the join between the two "
        + "phases — a proposal built from a hand-written inventory cannot prove this",
    );
    assert.equal(hero.mode, "fidelity", "nothing is authorable until somebody accepts it (P7)");
    assert.ok(
      Object.keys(flatten(proposal.theme?.tokens ?? {})).length,
      "the theme came from the bytes phase one downloaded, not from a fixture stylesheet: the "
        + "inventory named the file, the file was read, and its declarations became tokens",
    );

    // The url() rewrite, which is the handoff with the most ways to be wrong. A reference inside a
    // stylesheet resolves against the *stylesheet* and then has to land on the tree path the capture
    // chose for those bytes — so the map has to be the one `clone` wrote, not a second copy of it.
    const image = inventory.assets.find((asset) => asset.path === "/img/hero.png");
    assert.ok(image?.file, "the image the stylesheet references was downloaded too");
    assert.equal(proposal.theme.urls.rewritten, 1);
    assert.deepEqual(
      proposal.theme.urls.unresolved,
      [],
      "nothing was left pointing at the source's origin, which is what an unshared map produces",
    );
    assert.ok(
      proposal.theme.css.includes(image.file),
      `the rewritten CSS names ${image.file} — the tree path phase one chose for those bytes`,
    );
    await json(cwd, PROPOSAL_FILE);

    // ── phase three: the acceptance ─────────────────────────────────────────────────────
    // The id is read out of the artefact the previous phase wrote, the way a reviewer would.
    await propose.run({ accept: true }, [hero.id], ctx);

    const plan = await json(cwd, PLAN_FILE);
    assert.deepEqual(
      plan.accepted.map((entry) => entry.block),
      [hero.id],
      "the plan holds the id the proposal published — an id is a hash of the block's structure, so "
        + "this is the assertion that the two phases hash the same block",
    );

    const { proposal: after } = await propose.run({}, [], ctx);
    assert.equal(
      after.candidates.find((row) => row.id === hero.id)?.mode,
      "authorable",
      "the accepted candidate comes back authorable, which only holds if the plan on disk is read "
        + "by the run that follows it",
    );
    assert.equal(
      after.candidates.filter((row) => row.mode === "authorable").length,
      1,
      "accepting one block leaves every other candidate in fidelity mode (SH379)",
    );

    // ── phase four: the coverage gate ───────────────────────────────────────────────────
    const report = await verify.run({ checks: "projection,replication" }, [], ctx);

    const replication = report.findings.filter((each) => each.source === "replication");
    assert.ok(replication.length, "the group ran against the capture rather than skipping it");
    assert.ok(
      !replication.some((each) => each.rule === "replication-unchecked"),
      "no version finding: the reader understood everything the writer wrote, which is the seam "
        + "this whole file exists to hold",
    );
    assert.ok(
      !(report.notes ?? []).some((note) => /`unfollowed` arrived in/.test(note)),
      "and no note saying `unfollowed` checked nothing — the field is present because phase one "
        + "put it there",
    );
    assert.deepEqual(
      replication.filter((each) => each.rule === "source-page-missing")
        .flatMap((each) => [...each.message.matchAll(/\/[a-z]*/g)].map((match) => match[0]))
        .filter((path) => path === "/" || path === "/about")
        .sort(),
      ["/", "/about"],
      "every page phase one captured is reported as absent from the replica, because nothing has "
        + "been projected yet — the honest reading of a tree with a capture and no content",
    );
    assert.ok(
      replication.some((each) => each.rule === "source-link-unresolved"
        && /\/deep/.test(each.message)),
      "the link the crawl never followed is the finding only the real `unfollowed` list can produce",
    );
    for (const finding of replication) {
      assert.equal(
        finding.address,
        `source:${inventory.source.origin}`,
        "every finding addresses the source the capture names, not an invented post",
      );
    }
    assert.notEqual(report.exitCode, 1, "warnings do not fail the gate without --strict");

    // The whole chain, and it never left the process.
    assert.ok(
      source.requested.every((url) => url.startsWith("https://acme.test")),
      "no phase reached anything but the served fixture: no instance, no browser, no registry",
    );
  } finally {
    await rm(cwd, { recursive: true, force: true });
  }
});
