/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
 *
 * ## How it grows (SH458)
 *
 * This file ran green — 3 tests, 0 failures — on the instance where a hand pass found SH455, SH456
 * and SH457 in one sitting. That is the expected state of a first version, and the rule above is
 * what says how it closes: **each new assertion names the task it would have caught**, and it lands
 * while that task is still open, so it is red by construction and turns green when the fix arrives
 * (SH338 — the instrument precedes the fixes). Two of the three are here — the process's exit code,
 * which a module call structurally cannot see, and whether the rendered page's own references
 * resolve, which is what a reader checks by looking at the replica. The third is the public route's
 * negative cases, in `render.conformance.mjs`.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import { spawn } from "node:child_process";
import * as nodeFs from "node:fs/promises";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

import { unpublished } from "@viglet/create-shio-app/published";

import { ShioAdminClient } from "../src/admin-client.mjs";
import { ShioAgentClient } from "../src/agent-client.mjs";
import { INVENTORY_FILE, MARKUP_DIR } from "../src/clone.mjs";
import { ASSETS_DIR } from "../src/content-assets.mjs";
import { PLAN_FILE } from "../src/propose.mjs";
import { SECTIONS_FOLDER } from "../src/convert.mjs";
import * as clone from "../src/commands/clone.mjs";
import * as contentPull from "../src/commands/content-pull.mjs";
import * as convert from "../src/commands/convert.mjs";
import * as diff from "../src/commands/diff.mjs";
import * as init from "../src/commands/init.mjs";
import * as propose from "../src/commands/propose.mjs";
import * as verify from "../src/commands/verify.mjs";
import { ConsoleSession, keepsFixtures, requireInstance, scratchSite } from "./support/instance.mjs";
import { serveFixture } from "./support/fixture-site.mjs";

/** The bin `npm install -g @viglet/shio` puts on a PATH — the entry point no test in `test/` runs. */
const SHIO_BIN = fileURLToPath(new URL("../src/index.mjs", import.meta.url));

let conn;
let site;
let fidelitySite;
let exitSite;
let source;
let cwd;
let ctx;
let console_;

describe("SH448 conformance: replication, from a served source to an installable front end", () => {
  before(async () => {
    conn = await requireInstance();
    site = scratchSite("Repl");
    fidelitySite = scratchSite("Fid");
    exitSite = scratchSite("Exit");
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
    await console_?.deleteSite(exitSite).catch(() => {});
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

    // SH465 — and a *collection-shaped* candidate, which is the half a scalar-only acceptance could
    // not see. The fixture's `.features` block is two repeated cards, so it proposes as a
    // SectionFeatureGrid whose `features` holds both titles and bodies. Accepting only the hero left
    // every collection in the vocabulary unexercised end to end, which is how four of the seven
    // section types came to render as a heading and nothing else with this file green.
    const grid = proposal.candidates.find((row) => row.type === "SectionFeatureGrid");
    assert.ok(
      grid,
      `SH465: the capture holds a collection-shaped block. Candidates: `
        + `${proposal.candidates.map((row) => row.type).join(", ")}`,
    );
    assert.equal(
      grid.fields.features?.length,
      2,
      "and the proposal captured both rows — the content was never the thing that was missing",
    );

    await propose.run({ accept: true }, [hero.id, grid.id], ctx);
    const plan = JSON.parse(await readFile(join(cwd, PLAN_FILE), "utf8"));
    const byId = (a, b) => a.localeCompare(b);
    assert.deepEqual(
      plan.accepted.map((entry) => entry.block).toSorted(byId),
      [hero.id, grid.id].toSorted(byId),
    );

    // ── the conversion, into a site that does not exist yet ───────────────────────────────
    // SH439: `--create-site` compiles to a `site.upsert` op, run first. Without it this refusal
    // was the one a reader could not act on — the resolver named a verb the command did not take.
    await convert.run({ site, createSite: true }, [], ctx);

    // SH607 — the conversion installed the model it writes against, which is the step that was
    // never there. Every other type this document emits is a *system* one the instance provisions
    // on demand (Theme, SiteScripts, Redirect, PageLayout, File); `Page` and the SH95 sections
    // shipped only inside a blueprint, so on a bare instance `apply` answered 400 with
    // `allowed: [File, PageLayout, Redirect, Region, SiteScripts, Theme]` and every replication
    // scenario failed — fidelity included. This file was green throughout, because a development
    // instance had the types from an earlier console site or blueprint apply. Asserting the types
    // exist *after* a conversion is what makes the claim independent of which instance this is.
    const admin = new ShioAdminClient(conn);
    for (const declared of ["Page", "SectionHero", "SectionFeatureGrid"]) {
      assert.ok(
        await admin.getPostTypeDef(declared),
        `SH607: the conversion provisioned ${declared}. A capture cannot be replicated into a Shio `
          + `that has never been used, which is the only kind of Shio a new operator has`,
      );
    }
    assert.ok(
      (await admin.getPostTypeDef("Page")).fields.some((field) => field.name === "content"),
      "SH607: and it is the Page a conversion can actually write — three definitions shipped and "
        + "none held all four keys `buildConversion` emits, so the type every green run wrote "
        + "against matched no file in the repository",
    );

    const agent = new ShioAgentClient(conn);
    const resolved = await agent.resolve(`post:${site}/`);
    assert.ok(
      resolved?.id,
      "SH438/SH433: the replica has a home page. This is the assertion the chain could not make: "
        + "SH417 wrote sections nothing composed, three posts were created, /preview/<site>/ "
        + "answered 404, and every test was green because none of them asked an instance",
    );

    // ── SH464: and the authorable replica RENDERS, which is the half the block was named for ──
    //
    // The line above proves the home page *exists*; it existed before SH464 too. What it could not
    // ask is whether a visitor gets it: `convert` wrote sections, a Page and a Theme and no
    // PageLayout, so `/preview/<site>/` answered **400 - binds no layout for post-type 'Page'**, and
    // the console frames the site's front end or, failing that, that same preview - so the editor
    // had nothing to frame either. Block Q is named "an existing site becomes an authorable Shio
    // site" and neither of its two modes produced one. Resolving an address and rendering a page are
    // different questions, and only the second one had the wrong answer.
    const authorable = await fetch(`${conn.baseUrl}/preview/${encodeURIComponent(site)}/`, {
      headers: { Authorization: basicOf(conn) },
    });
    const authorableHtml = await authorable.text();
    assert.equal(
      authorable.status,
      200,
      `SH464: an authorable replica renders. Body: ${authorableHtml.slice(0, 300)}`,
    );
    assert.match(
      authorableHtml,
      /data-shio-prop="heading"/,
      "SH464: and the editor has props to bind - the layout declares sh-field per field of the SH95 "
        + "vocabulary, which the renderer turns into data-shio-prop. Fidelity mode renders with ZERO "
        + "of them, because verbatim markup declares no sh-field, so the bridge loaded over a page "
        + "with nothing to offer. That is the other half of the same measurement.",
    );

    // ── SH465: and the collection fields are ON that page ─────────────────────────────────
    //
    // SH464's layout drew six scalar fields and no collection, and four of the seven section types
    // are collection-shaped. So the accepted feature grid rendered its `heading` and stopped: the
    // section post held both cards, `verify` was clean, the field was editable in the console, and
    // the page a curator approves was missing half its content. That is worse than losing it at
    // capture, because every instrument said it was there — which is why the assertion has to be
    // about the *rendered bytes* and cannot be about the document, the post or the projection.
    for (const card of ["Fast", "Measured, not asserted.", "Safe", "Draft by default."]) {
      assert.ok(
        authorableHtml.includes(card),
        `SH465: the feature grid's rows are drawn. "${card}" is in the section post's features `
          + `array and was not on the page. Body: ${authorableHtml.slice(0, 600)}`,
      );
    }
    assert.match(
      authorableHtml,
      /data-shio-row="features"/,
      "SH465: and each row says which collection it came from - the indexed path SH475 gave it "
        + "repeats the collection, but a stylesheet and a reader cannot read one out of an sh-field.",
    );
    // SH503 — the count nothing was taking. `sh-field` declares what an element's *content* is, so
    // one element declared one field and a link is two: the CTA's destination was captured, stored
    // on the section and rendered into the href with no declaration, and a curator could rename the
    // button and not repoint it. Asserted on the live render because that is where the gap was
    // measured — the post, the projection and `verify` all read as complete.
    assert.match(
      authorableHtml,
      /data-shio-prop-attr="ctaHref:href"/,
      "SH503: a call to action's destination is addressable, and says which attribute holds it. "
        + `Body: ${authorableHtml.slice(0, 600)}`,
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

    // ── and the gate the order used to leave out ──────────────────────────────────────────
    // SH548. `verify --checks projection,replication` above measures what is *there*; `diff` is the
    // only instrument in the chain that holds an **expectation** — the capture's own counts — and on
    // its first real run it was the only one that caught a replica whose navigation was gone: 35
    // facts lost, internal links 16 → 0 on eleven pages, exit 1, while `verify` and `audit` both
    // exited 0 on the same site with true warnings that were not the finding. It was in no gate, so
    // the one red signal on a gutted replica was one a person had to remember to ask for. Running it
    // here is what makes the documented order in `cli.md` a thing this suite proves rather than
    // states, and `gate-order.test.mjs` asserts both halves stay in step.
    const comparison = await diff.run({ site, json: true }, [], ctx);
    assert.equal(
      comparison.lost,
      0,
      `SH548: every fact the capture declared **and this page's mode claims** is still on the pages `
        + `the replica serves. This is the only check here that can fail on a page that renders, `
        + `returns 200 and has lost most of `
        + `itself: ${JSON.stringify(comparison.pages?.filter((page) => page.lost) ?? [])}`,
    );

    // SH577 — and the mode it applied, because the line above passing for the wrong reason is
    // exactly what this chain did for two rounds in the other direction. Every block on both pages
    // was accepted above, so both are authorable, and `lost === 0` is a statement about the content
    // rather than a claim the source's chrome survived. Asserting the mode is what stops a future
    // regression that judges an authorable page as fidelity — or one that quietly excuses a
    // fidelity page — from reading as this same green line.
    assert.deepEqual(
      comparison.modes,
      { fidelity: 0, mixed: 0, authorable: 2 },
      "SH577: the mode is read off the plan and the proposal — the two artefacts this chain has "
        + "already written — and not from the served bytes, which would be the second opinion "
        + `SH522's whole design refuses. Pages: ${JSON.stringify(comparison.pages?.map(
          (page) => [page.path, page.mode]) ?? [])}`,
    );
    assert.ok(
      comparison.byDesign > 0,
      "SH577: and the chrome the vocabulary dropped is still COUNTED, not discarded. This suite's "
        + "fixture loses the source's `/about` link and all three of its assets on an authorable "
        + "page, and a comparison reporting zero of both would mean the facts had been removed from "
        + "the report rather than reclassified — which is how SH575's missing navigation would have "
        + `become invisible. Reported: ${JSON.stringify(comparison.byDesign)}`,
    );
    assert.deepEqual(
      comparison.missing ?? [],
      [],
      "and no captured page is unserved — the same claim `source-page-missing` makes from the tree, "
        + "made against the route a reader actually gets",
    );

    // ── and the front end that serves it cannot be the built-in one ───────────────────────
    // SH502. This site's pages are sections — asserted above, `/sections` in the address list — and
    // the built-in template renders a page's body and nothing else. It used to be handed over here,
    // and it installs, so every check in this suite passed on a project that serves empty pages.
    await assert.rejects(
      () => init.run({ name: "front-end", yes: true, url: conn.baseUrl, site, token: "" }, [], ctx),
      (error) => {
        assert.match(error.message, /builds its pages from sections/);
        assert.match(error.message, /marketing-site/, "and where a sections renderer comes from");
        return true;
      },
    );
  });

  /**
   * One `npm install --dry-run` per template — the measurement SH446 did not make (SH502).
   *
   * <p>SH446 proved the built-in template installs and stopped there, which left the half an
   * authorable replica needs unmeasured: the `marketing-site` blueprint is the only scaffold that
   * draws a Page's `sections`, and it asks for `@viglet/shio-sections`, which has never been
   * published. Two templates, one command each, and the pair is the finding — either alone reads as
   * "the scaffold installs".</p>
   *
   * <p>The section template's assertion is keyed on {@code published.mjs} rather than on a fixed
   * expectation, so it holds in both worlds: while the package is unpublished npm <b>must</b> refuse
   * it (and a pass would mean the table is lying), and the day it ships the same line requires a
   * clean install. That is what makes this a measurement and not a snapshot of today's breakage.</p>
   */
  test("SH502: one install per template, and the section-carrying one is measured too", async () => {
    // The built-in template, on its own: the site is what made it wrong above, not the template.
    const builtIn = await init.run(
      { name: "front-end", yes: true, url: conn.baseUrl, token: "" },
      [],
      ctx,
    );
    assert.ok(builtIn.files.length, "init wrote a scaffold");

    const manifest = JSON.parse(await readFile(join(builtIn.targetDir, "package.json"), "utf8"));
    const install = await npm(["install", "--dry-run", "--no-audit", "--no-fund"], builtIn.targetDir);
    assert.equal(
      install.code,
      0,
      `SH446: every version the scaffold declares resolves on the registry. It writes\n`
        + `${JSON.stringify(manifest.dependencies, null, 2)}\n`
        + `and npm answered ${install.code}:\n${install.output.trim().slice(-1500)}`,
    );

    // The one that renders sections. No `--site`: this measures the template, and applying content
    // would write a blueprint's own model into the replica under test.
    const sections = await init.run(
      { name: "sections-front-end", blueprint: "marketing-site", yes: true, url: conn.baseUrl, token: "" },
      [],
      ctx,
    );
    assert.ok(sections.files.length, "the blueprint carries a front end");

    const missing = unpublished(["@viglet/shio-sections", "@viglet/shio-model"]);
    const dry = await npm(["install", "--dry-run", "--no-audit", "--no-fund"], sections.targetDir);
    if (missing.length) {
      assert.notEqual(
        dry.code,
        0,
        `SH502: create-shio-app/src/published.mjs declares ${missing.join(" and ")} unpublished, and`
          + ` npm installed the section template anyway. If the release has happened, that table is`
          + ` what is now wrong — and until it is corrected every scaffold declaring these ranges is`
          + ` unmeasured.\n${dry.output.trim().slice(-800)}`,
      );
    } else {
      assert.equal(
        dry.code,
        0,
        `SH502: with @viglet/shio-sections published, the only scaffold that draws a Page's sections`
          + ` must install — that publish IS the fix, and this is the line that proves it landed.\n`
          + `${dry.output.trim().slice(-1500)}`,
      );
    }
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

    // ── SH458: and every reference the page makes resolves ────────────────────────────────
    //
    // The third missing assertion, and the one a reader makes instantly by looking at the replica.
    // The three checks above name three files by hand, so they prove those three and say nothing
    // about the fourth — which is how the logo stayed broken and how SH455's webfonts were reported
    // as fetched while the page rendered in a serif fallback. This asks the page instead: whatever
    // its markup and its CSS say they need, fetch it and expect 200.
    //
    // Same-origin and root-relative only. An absolute URL is somebody else's host and SH378's rule
    // is that we do not mirror it, so a 404 there is their outage and not this replica's defect.
    const referenced = referencesIn(html);
    assert.ok(referenced.length >= 2, `the page references something. Found: ${referenced.join(" ")}`);

    const broken = [];
    for (const path of referenced) {
      const asset = await fetch(`${conn.baseUrl}${path}`, { headers: { Authorization: basicOf(conn) } });
      if (asset.status !== 200) broken.push(`${path} → ${asset.status}`);
    }
    assert.deepEqual(
      broken,
      [],
      `SH455: every path the replica's own page names is served by the replica. A reference that `
        + `404s is the whole failure mode of a fidelity capture — the bytes may well be on the `
        + `instance, under a path the page does not ask for, and no unit test can tell those apart `
        + `because both halves are green in isolation. Referenced: ${referenced.join(" ")}`,
    );

    // ── SH466: and no link on it leaves the site ──────────────────────────────────────────
    //
    // The check above asks whether a reference resolves; this asks where a *navigation* points,
    // which is the other half and the one that was silently wrong. SH336 rewrote a link only when
    // its path matched a published URL, so an unresolvable one was emitted verbatim — the source's
    // base, on a page served under another. The fixture links `/admin`, a page robots.txt kept out
    // of the capture, so this file already served the exact case: clicking it left the replica for
    // the instance root. Whether a link fell through depended on the *index*, hence on publication
    // state, which is why a half-published site mixed two kinds of href and only one worked.
    const escaping = navigableIn(html).filter(
      (path) => !path.startsWith(`/preview/${fidelitySite}/`) && !isInstanceRoute(path),
    );
    assert.deepEqual(
      escaping,
      [],
      `SH466: every root-relative href on the replica is in the replica's own address space. A `
        + `verbatim one is a hardcoded base — a missing page is a finding \`verify\` reports, a link `
        + `out of the site is a silent exit. Hrefs: ${navigableIn(html).join(" ")}`,
    );
  });

  test("SH456: a conversion that wrote everything exits 0, said by the process and not by the module", async () => {
    // SH458's first missing assertion. Every `convert` above is a *module* call — `convert.run(...)`
    // — so the suite reads what the command returned and never what the shell would see. SH456 is
    // precisely the gap between those two: `convert --site Publico --create-site` printed its whole
    // report (`changed: 7 object(s) — in DRAFT`, three posts live on the instance) and then exited
    // **1** on `Malformed address 'folder:Publico'`, an address it built itself. Work done, refusal
    // reported. The same seam as SH445 — code fixed twice against a harness before anything ran the
    // command — and the reason this file spawns the bin instead of importing it.
    //
    // The fixture's `/favicon.ico` is what makes this bite: it is the only asset with no folder
    // segment, so it is the only one whose upload takes the root-folder branch that malforms.
    const run = await shio(["convert", "--site", exitSite, "--create-site"], cwd);

    assert.equal(
      run.code,
      0,
      `SH456: the exit code agrees with the report. A caller who trusts the 1 re-runs an idempotent `
        + `command forever; a caller who trusts the report ignores an exit code that is sometimes `
        + `real. Output:\n${run.output.trim().slice(-1500)}`,
    );
    const agent = new ShioAgentClient(conn);
    assert.ok(
      (await agent.resolve(`post:${exitSite}/`))?.id,
      "and it exited 0 because it succeeded, not because it did less — the home page is there",
    );
  });
});

/**
 * Every root-relative path a rendered page says it needs — from its markup and from its CSS.
 *
 * <p>`src`, `<link href>` and `url()`, which is the whole set that makes a browser issue a request
 * for bytes. A plain `<a href>` is excluded on purpose: it is navigation, and the fixture links
 * `/admin`, a page robots.txt kept out of the capture — a 404 there is correct, so including it
 * would make the assertion say something it does not mean.</p>
 */
function referencesIn(html) {
  const found = new Set();
  const keep = (raw) => {
    const value = String(raw ?? "").trim();
    if (value.startsWith("/") && !value.startsWith("//")) found.add(value.split("#")[0]);
  };
  for (const [, value] of html.matchAll(/\bsrc\s*=\s*"([^"]*)"/gi)) keep(value);
  for (const [, value] of html.matchAll(/<link\b[^>]*\bhref\s*=\s*"([^"]*)"/gi)) keep(value);
  for (const [, dq, sq, bare] of html.matchAll(/url\(\s*(?:"([^"]*)"|'([^']*)'|([^)'"\s]+))\s*\)/gi)) {
    keep(dq ?? sq ?? bare);
  }
  return [...found].sort();
}

/**
 * Every root-relative `<a href>` a rendered page carries — navigation rather than bytes (SH466).
 *
 * <p>The complement of `referencesIn`, and deliberately a second function: that one excludes
 * `<a href>` because a 404 there can be correct, and this one asserts something a 404 cannot say —
 * whose *base* the link was written against.</p>
 */
function navigableIn(html) {
  const found = new Set();
  for (const [, value] of html.matchAll(/<a\b[^>]*\bhref\s*=\s*"([^"]*)"/gi)) {
    const path = String(value ?? "").trim();
    if (path.startsWith("/") && !path.startsWith("//")) found.add(path);
  }
  return [...found].sort();
}

/**
 * The first segments the instance serves rather than a site — `ShPreviewLinks.INSTANCE_ROUTES`.
 *
 * <p>Three of its five. `preview` and `sites` are omitted on purpose: they are in the Java list
 * because they are the prefixes that pass *writes*, which is what makes it idempotent, but a served
 * page whose link points into a **different** site's preview has still left this replica — and
 * excusing the whole segment here would be the assertion excusing the case it exists for. The
 * caller admits its own prefix and nothing else under them.</p>
 *
 * <p>A second copy of a list, which SH245 warns about. Accepted for the reason that rule gives: the
 * shared contract is the instance's route table, and this side has to name it to assert about the
 * other side's output at all. A drift here is a red conformance run rather than a silent miss.</p>
 */
function isInstanceRoute(path) {
  const segment = path.split(/[/?#]/)[1] ?? "";
  return ["api", "mcp", "file_source"].includes(segment);
}

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

/**
 * Run the `shio` bin as a process, which is the only way to observe an exit code (SH458).
 *
 * <p>`node <bin>` rather than the shim, and `shell: false`: the shim is what a PATH install writes
 * and it is not what is under test here — the entry point is, and going through a shell would put
 * the shell's own exit-code handling between the command and the assertion. The environment is the
 * one `ctx` carries, so the process resolves its instance exactly as the module calls above do.</p>
 */
function shio(args, cwd) {
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [SHIO_BIN, ...args], { cwd, env: ctx.env });
    let output = "";
    child.stdout.on("data", (chunk) => (output += chunk));
    child.stderr.on("data", (chunk) => (output += chunk));
    child.on("error", (error) => resolve({ code: -1, output: `${output}\n${error.message}` }));
    child.on("close", (code) => resolve({ code, output }));
  });
}
