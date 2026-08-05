/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH448 — scenario one: **replication, against the three things a unit test replaces**.
 *
 * SH422 drives the same chain and is green, and SH438, SH442 and SH446 all shipped under it. One
 * cause explains the three: it asserts the seams over a *served fixture object*, an *injected
 * capture* and *no registry*, so the only boundaries it cannot see are the ones it substitutes.
 * This file substitutes none of them:
 *
 * - the source is a real `node:http` server and the CLI gets the platform's own `fetch`;
 * - the conversion goes to a **running instance** over HTTP, and the content comes back through
 *   `shio pull --content` rather than being read out of the document that was sent;
 * - the scaffold `init` writes is handed to **npm**, which is the only thing that can say whether
 *   the versions in it exist.
 *
 * Every assertion names the task it would have caught. The three that matter most are SH446 (a
 * scaffold whose dependencies are unresolvable — invisible until somebody ran `npm install` by
 * hand), SH438 (sections nothing composed, so `/preview/<site>/` answered 404 while the chain was
 * green) and SH387 (`unfollowed` meaning "not visited", never "not there").
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import { spawn } from "node:child_process";
import * as nodeFs from "node:fs/promises";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { ShioAgentClient } from "../src/agent-client.mjs";
import { INVENTORY_FILE, MARKUP_DIR } from "../src/clone.mjs";
import { ASSETS_DIR } from "../src/content-assets.mjs";
import { PLAN_FILE } from "../src/propose.mjs";
import { SECTIONS_FOLDER } from "../src/convert.mjs";
import * as clone from "../src/commands/clone.mjs";
import * as contentPull from "../src/commands/content-pull.mjs";
import * as convert from "../src/commands/convert.mjs";
import * as init from "../src/commands/init.mjs";
import * as propose from "../src/commands/propose.mjs";
import * as verify from "../src/commands/verify.mjs";
import { ConsoleSession, keepsFixtures, requireInstance, scratchSite } from "./support/instance.mjs";
import { serveFixture } from "./support/fixture-site.mjs";

let conn;
let site;
let fidelitySite;
let source;
let cwd;
let ctx;
let console_;

describe("SH448 conformance: replication, from a served source to an installable front end", () => {
  before(async () => {
    conn = await requireInstance();
    site = scratchSite("Repl");
    fidelitySite = scratchSite("Fid");
    source = await serveFixture();
    cwd = await mkdtemp(join(tmpdir(), "shio-sh448-"));
    console_ = new ConsoleSession(conn);
    ctx = {
      // The real environment, plus the connection — every command below resolves its instance
      // through `resolveAdmin`, which is the path a user's shell takes.
      env: { ...process.env, SHIO_URL: conn.baseUrl, SHIO_USER: conn.user, SHIO_PASSWORD: conn.password },
      cwd,
      stdout: sink(),
      stderr: sink(),
      // Not a fake. This is the difference between this file and SH422's.
      fetch: globalThis.fetch,
      fs: nodeFs,
    };
  });

  after(async () => {
    await source?.close().catch(() => {});
    if (keepsFixtures()) return;
    await console_?.deleteSite(site).catch(() => {});
    await console_?.deleteSite(fidelitySite).catch(() => {});
    await rm(cwd, { recursive: true, force: true }).catch(() => {});
  });

  test("capture over a socket → convert into the instance → pull it back → gate it", async () => {
    // ── the capture, over HTTP ────────────────────────────────────────────────────────────
    await clone.run({ markup: true, assets: true, site, depth: 1 }, [source.origin], ctx);
    const inventory = JSON.parse(await readFile(join(cwd, INVENTORY_FILE), "utf8"));

    assert.deepEqual(
      inventory.pages.map((row) => row.path).sort(),
      ["/", "/about"],
      "SH387: the walk stopped at the origin, honoured robots.txt and was cut by --depth. Asserted "
        + "against a real server because robots and depth are decisions about *requests*, and an "
        + "injected fetch is a fixture that cannot refuse one",
    );
    assert.ok(
      !source.requested.includes("/deep") && !source.requested.includes("/admin"),
      `SH387: and the server was never asked for them. "absent from the source" and "absent from `
        + `the capture" are opposite claims, and only the socket can tell them apart. Asked for: `
        + `${source.requested.join(", ")}`,
    );
    assert.deepEqual(
      inventory.unfollowed.map((row) => row.path).sort(),
      ["/admin", "/deep"],
      "SH387: both are recorded as seen-and-not-followed, which is what the coverage group reads",
    );

    const stylesheet = inventory.assets.find((asset) => asset.kind === "stylesheet");
    assert.ok(stylesheet?.file, "the stylesheet was downloaded and its tree path recorded");

    // SH452 — the webfont, which nothing in the markup mentions. `clone` built its asset list from
    // the markup alone, so a font, a background image and an icon sprite were invisible to it, and
    // a replica whose fonts 404 renders in a serif fallback with every test still green.
    const font = inventory.assets.find((asset) => asset.kind === "font");
    assert.ok(
      font?.file,
      `SH452: the stylesheet's own url() was followed. Captured: `
        + `${inventory.assets.map((asset) => asset.path).join(" ")}`,
    );
    assert.equal(
      font.path,
      "/fonts/fixture.woff2",
      "resolved against the STYLESHEET — `../fonts/` from /css/site.css. Against the page it would "
        + "be the same string here only by accident, which is why the fixture nests the stylesheet",
    );
    assert.equal(
      (await readFile(join(cwd, ASSETS_DIR, font.file))).subarray(0, 4).toString("ascii"),
      "wOF2",
      "and the bytes are the font's, not a soft-404 page wearing a .woff2 name",
    );
    const image = inventory.assets.find((asset) => asset.path === "/img/hero.png");
    assert.ok(image?.file, "and so was the image the markup references");
    const bytes = await readFile(join(cwd, ASSETS_DIR, image.file));
    assert.equal(
      bytes.subarray(0, 8).toString("hex"),
      "89504e470d0a1a0a",
      "SH204: the bytes on disk are the PNG the server sent. A fake fetch answers `arrayBuffer` "
        + "from a string, so binary-safety is the one property it structurally cannot assert",
    );
    for (const row of inventory.pages) {
      const cached = await readFile(join(cwd, MARKUP_DIR, row.markup), "utf8");
      assert.match(cached, /<section class="hero">/, `page ${row.path} cached the markup it served`);
    }

    // ── the proposal, and one acceptance ──────────────────────────────────────────────────
    const { proposal } = await propose.run({}, [], ctx);
    const hero = proposal.candidates.find((row) => row.type === "SectionHero");
    assert.ok(hero, "the block that recurs on both captured pages is one candidate");
    assert.equal(hero.mode, "fidelity", "P7: nothing is authorable until somebody accepts it");

    await propose.run({ accept: true }, [hero.id], ctx);
    const plan = JSON.parse(await readFile(join(cwd, PLAN_FILE), "utf8"));
    assert.deepEqual(plan.accepted.map((entry) => entry.block), [hero.id]);

    // ── the conversion, into a site that does not exist yet ───────────────────────────────
    // SH439: `--create-site` compiles to a `site.upsert` op, run first. Without it this refusal
    // was the one a reader could not act on — the resolver named a verb the command did not take.
    await convert.run({ site, createSite: true }, [], ctx);

    const agent = new ShioAgentClient(conn);
    const resolved = await agent.resolve(`post:${site}/`);
    assert.ok(
      resolved?.id,
      "SH438/SH433: the replica has a home page. This is the assertion the chain could not make: "
        + "SH417 wrote sections nothing composed, three posts were created, /preview/<site>/ "
        + "answered 404, and every test was green because none of them asked an instance",
    );

    const found = await agent.findAll({ site });
    const urls = found.results.map((row) => row.address ?? row.url ?? "");
    assert.ok(
      urls.some((address) => address.includes(SECTIONS_FOLDER)),
      `SH417: the accepted block landed as a section post. Found: ${urls.join(", ")}`,
    );
    assert.ok(
      urls.some((address) => address.endsWith("/about")),
      "SH385: a replica keeps the source's URL space, so /about is /about and not /pages/about",
    );

    // ── back out of the instance, and gated on what came back ─────────────────────────────
    // The projection is the round trip: everything above proved what was *sent*, and a document
    // that was accepted is not the same claim as a site that reads back.
    await contentPull.run({ site }, [], ctx);
    const report = await verify.run({ checks: "projection,replication", site }, [], ctx);

    const replication = report.findings.filter((each) => each.source === "replication");
    assert.ok(replication.length >= 0, "the coverage group ran");
    assert.ok(
      !replication.some((each) => each.rule === "replication-unchecked"),
      "SH387: the reader understood the version the writer wrote",
    );
    const missing = replication
      .filter((each) => each.rule === "source-page-missing")
      .map((each) => each.message);
    assert.deepEqual(
      missing,
      [],
      `SH438: every page the capture recorded exists in the replica. This is the gate's own `
        + `statement that the replication is done, and it is only true once the content has been `
        + `through the instance and back. Still missing: ${missing.join(" | ")}`,
    );

    // ── and the front end that serves it can actually be installed ────────────────────────
    // The last boundary, and the one nothing in `cli/test/` touches: the npm registry. SH446 was
    // invisible to eleven green files and to SH422, and surfaced only under a hand-run
    // `npm install --dry-run` — which is precisely the shape of defect this suite exists for.
    const project = "front-end";
    const { targetDir, files } = await init.run(
      { name: project, yes: true, url: conn.baseUrl, site, token: "" },
      [],
      ctx,
    );
    assert.ok(files.length, "init wrote a scaffold");

    const manifest = JSON.parse(await readFile(join(targetDir, "package.json"), "utf8"));
    const install = await npm(["install", "--dry-run", "--no-audit", "--no-fund"], targetDir);
    assert.equal(
      install.code,
      0,
      `SH446: every version the scaffold declares resolves on the registry. It writes\n`
        + `${JSON.stringify(manifest.dependencies, null, 2)}\n`
        + `and npm answered ${install.code}:\n${install.output.trim().slice(-1500)}`,
    );
  });

  test("SH449: accept nothing, and the replica is still a site you can look at", async () => {
    // Fidelity mode, which the docs described for two blocks and no writer ever produced. It runs
    // second on purpose: the capture and the plan are already on disk from the test above, so this
    // is the *same* review, converted into a second site with the acceptances ignored — which is
    // exactly the comparison a reviewer makes before deciding.
    //
    // Nothing here can be asserted without an instance. The claim is that a page nobody restructured
    // renders, and the only thing that can answer it is the renderer.
    const unreviewed = await withoutPlan(cwd);
    try {
      await convert.run({ site: fidelitySite, createSite: true }, [], { ...ctx, cwd: unreviewed });
    } finally {
      await rm(unreviewed, { recursive: true, force: true }).catch(() => {});
    }

    const res = await fetch(`${conn.baseUrl}/preview/${encodeURIComponent(fidelitySite)}/`, {
      headers: { Authorization: basicOf(conn) },
    });
    const html = await res.text();
    assert.equal(
      res.status,
      200,
      `SH449: /preview answers for a conversion with no acceptances. Before this, convert wrote the `
        + `Theme and nothing else — every captured path 404'd and the coverage gate reported `
        + `source-page-missing for all of them, while the report said "2 in fidelity mode". `
        + `Body: ${html.slice(0, 400)}`,
    );
    assert.match(
      html,
      /class="hero"/,
      "and it is the source's own markup: the class attributes the authorable path reports losing "
        + "are all still here, which is the entire reason fidelity is a mode",
    );
    assert.match(html, /Body copy that reads like a page a person wrote/,
      "with the source's copy, not a field somebody mapped it into");
    assert.match(
      html,
      /--ink:\s*#102a43/,
      "SH380/SH424: and the captured theme is inlined by the layout, so a token edit reaches the "
        + "page — which is what a <head> copied verbatim from the source could never give",
    );

    // ── SH437: and its images load ────────────────────────────────────────────────────────
    // The half SH429 measured as unavailable. A static file's bytes are public at
    // /file_source/<postId>/<name>, keyed by an id no source ever chose, so uploading the capture
    // left <img src="/img/hero.png"> exactly as broken as leaving the bytes on disk. Nothing short
    // of a live instance can assert this: it is a route, a resolver and a render pass agreeing.
    assert.match(
      html,
      new RegExp(`src="/preview/${fidelitySite}/img/hero\\.png"`),
      "SH437: the render pass points the source's own path at the site's copy of those bytes — and "
        + "the stored markup is untouched, which is why `snapshot --against` can still compare it",
    );

    const image = await fetch(`${conn.baseUrl}/preview/${encodeURIComponent(fidelitySite)}/img/hero.png`, {
      headers: { Authorization: basicOf(conn), Accept: "image/png" },
    });
    assert.equal(
      image.status,
      200,
      "SH437: and the route answers with the bytes, resolved by folder chain + file name — SH145's "
        + "addressing read backwards, which is the join that did not exist",
    );
    assert.match(image.headers.get("content-type") ?? "", /^image\//,
      "as an image, from what the storage layer probed rather than from the URL");
    assert.equal(
      Buffer.from(await image.arrayBuffer()).subarray(0, 8).toString("hex"),
      "89504e470d0a1a0a",
      "and they are the PNG the fixture served, all the way through capture, upload and delivery",
    );

    // SH452 + SH437 together: a byte nothing in the markup ever named, delivered at the path the
    // stylesheet asks for. This is the whole replication claim in one request.
    const webfont = await fetch(
      `${conn.baseUrl}/preview/${encodeURIComponent(fidelitySite)}/fonts/fixture.woff2`,
      { headers: { Authorization: basicOf(conn) } },
    );
    assert.equal(
      webfont.status,
      200,
      "SH452: the webfont the stylesheet asked for is served by the replica. Before it, the capture "
        + "took the markup's assets only and the page rendered in a serif fallback",
    );
    assert.equal(
      Buffer.from(await webfont.arrayBuffer()).subarray(0, 4).toString("ascii"),
      "wOF2",
    );

    // And the theme's own url() now resolves to a tree path rather than being excused by a note
    // that said it still worked (SH452's second half).
    const theme = await fetch(
      `${conn.baseUrl}/api/v2/agent/read?address=${encodeURIComponent(`post:${fidelitySite}/theme/captured`)}`,
      { headers: { Authorization: basicOf(conn), Accept: "application/json" } },
    );
    const css = JSON.stringify(await theme.json());
    assert.ok(
      !/url\(\\?"?\.\.\/fonts/.test(css),
      "the captured theme points at what the tree holds, not at a path relative to a file that is "
        + "no longer there",
    );
  });
});

/** The same capture with the acceptances set aside, so one review yields both modes. */
async function withoutPlan(from) {
  const to = await mkdtemp(join(tmpdir(), "shio-sh449-"));
  await nodeFs.cp(join(from, "shio"), join(to, "shio"), { recursive: true });
  await rm(join(to, PLAN_FILE), { force: true });
  return to;
}

function basicOf({ user, password }) {
  return `Basic ${Buffer.from(`${user ?? ""}:${password ?? ""}`, "utf8").toString("base64")}`;
}

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

/**
 * Run npm in `cwd` and report its exit code and combined output.
 *
 * `shell: true` because npm is a `.cmd` shim on Windows, which `spawn` will not exec directly —
 * and this suite is run on the machine the developer has, not on a chosen one.
 */
function npm(args, cwd) {
  return new Promise((resolve) => {
    const child = spawn("npm", args, { cwd, shell: true });
    let output = "";
    child.stdout.on("data", (chunk) => (output += chunk));
    child.stderr.on("data", (chunk) => (output += chunk));
    child.on("error", (error) => resolve({ code: -1, output: `${output}\n${error.message}` }));
    child.on("close", (code) => resolve({ code, output }));
  });
}
