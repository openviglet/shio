/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH522 — `shio diff`, the join nobody had walked.
 *
 * Shio holds both sides of every comparison it does not make: the capture declares the source, the
 * proposal declares the intent, the document declares the write, and the artefact each produces is
 * compared with none of them. So "is this what it was supposed to be?" was answered by hand every
 * round, and that hand was wrong twice — *zero broken references* on a page with a missing logo, and
 * *106 editable fields* on a page that had lost most of its content.
 *
 * Network-free: the replica's bytes arrive through the injected `fetch`, which is what lets a page
 * that lost its hero be a fixture rather than a story.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { INVENTORY_FILE, scanPage } from "../src/clone.mjs";
import { compareCapture, describeDiff } from "../src/diff.mjs";
import * as diff from "../src/commands/diff.mjs";
import { norm } from "./support/paths.mjs";

/** The source, as it was captured: a hero, a nav, a grid and a footer. */
const SOURCE = `<html lang="en"><head><title>Home</title></head><body>
  <nav><a href="/about">About</a><a href="/pricing">Pricing</a></nav>
  <section class="hero"><h1>Acme</h1><img src="/img/hero.png" alt="Hero">
    <p>${"Body copy that reads like a page a person wrote. ".repeat(6)}</p></section>
  <footer><a href="/privacy">Privacy</a></footer>
  </body></html>`;

/** The same page after a conversion that kept one section and lost the rest (the SH520 shape). */
const LOST = `<html lang="en"><head><title>Home</title></head><body>
  <section class="shio-section"><h2>Acme</h2></section>
  </body></html>`;

function inventoryOf(html) {
  const doc = scanPage(html, "https://x.test/");
  return {
    version: 3,
    source: { origin: "https://x.test", seed: "https://x.test" },
    crawl: { pages: 1, truncated: false, markup: true },
    pages: [{
      path: "/",
      title: doc.title,
      links: {
        internal: doc.links.filter((link) => link.href.startsWith("/")).length,
        external: doc.links.filter((link) => !link.href.startsWith("/")).length,
      },
      assets: doc.assets.length,
      forms: doc.forms.length,
      textChars: doc.textChars,
    }],
  };
}

function ctxWith({ served = {}, inventory = inventoryOf(SOURCE) } = {}) {
  const out = { text: "" };
  const files = new Map([[norm(`/repo/${INVENTORY_FILE}`), JSON.stringify(inventory)]]);
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: norm("/repo"),
    stdout: { write: (chunk) => { out.text += chunk; } },
    stderr: { write: () => {} },
    out,
    fs: {
      async readFile(at) {
        if (files.has(norm(at))) return files.get(norm(at));
        const error = new Error("ENOENT");
        error.code = "ENOENT";
        throw error;
      },
    },
    async fetch(url) {
      const path = new URL(url).pathname.replace(/^\/preview\/[^/]+/, "") || "/";
      const html = served[path];
      return html === undefined
        ? { ok: false, status: 404, async text() { return ""; } }
        : { ok: true, status: 200, async text() { return html; } };
    },
  };
}

test("SH522: a page that lost its content is named fact by fact", async () => {
  const ctx = ctxWith({ served: { "/": LOST } });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.equal(result.exitCode, 1, "a lost fact fails the gate — that is the whole point of a verb "
    + "whose answer was being assembled by hand");
  const [page] = result.pages;
  assert.deepEqual(
    page.rows.filter((row) => row.delta < 0).map((row) => row.fact).sort(),
    ["assets", "internal links"],
    "the hero image and every link the nav and footer carried",
  );
  assert.equal(page.text.lost, true, "and the prose, which is the fact SH520 removed and every "
    + "instrument called a success");
  assert.match(ctx.out.text, /internal links: 3 → 0/);
  assert.match(ctx.out.text, /assets: 1 → 0/);
});

test("SH522: a replica that matches its capture says so in one line and exits zero", async () => {
  const ctx = ctxWith({ served: { "/": SOURCE } });

  const result = await diff.run({ site: "Acme" }, [], ctx);

  assert.equal(result.exitCode, 0);
  assert.equal(result.lost, 0);
  assert.match(ctx.out.text, /nothing lost/);
  assert.match(ctx.out.text, /\/ {2}as declared/,
    "a clean page prints one line: a report whose matching rows are as long as its broken ones is "
      + "one nobody reads to the end");
});

test("SH522: a fact the replica gained is not a loss", () => {
  // A Shio layout adds links — a skip link, a nav the theme composes — so a gate that called any
  // difference damage would fire on every working conversion. Counted apart and never failing.
  const declared = [{ path: "/", title: "Home", links: { internal: 2, external: 0 }, assets: 1, forms: 0, textChars: 400 }];
  const produced = new Map([["/", scanPage(
    '<html><head><title>Home</title></head><body><a href="/a">a</a><a href="/b">b</a>'
      + '<a href="/c">c</a><img src="/img/hero.png" alt=""><p>'
      + "Body copy that reads like a page a person wrote. ".repeat(6) + "</p></body></html>",
    "https://x.test/")]]);

  const comparison = compareCapture(declared, produced);

  assert.equal(comparison.lost, 0);
  assert.equal(comparison.pages[0].gained, 1);
  assert.equal(comparison.pages[0].rows.find((row) => row.fact === "internal links").delta, 1);
});

test("SH522: a path the replica does not serve is one absence, not four lost facts", () => {
  const declared = [{ path: "/gone", title: "Gone", links: { internal: 3, external: 1 }, assets: 2, forms: 1, textChars: 900 }];

  const comparison = compareCapture(declared, new Map());

  assert.deepEqual(comparison.missing, ["/gone"]);
  assert.equal(comparison.lost, 0, "`source-page-missing` is already the finding for this, and "
    + "counting it again as four would be one defect reported twice");
  assert.match(describeDiff(comparison), /the replica serves nothing here/);
});

test("SH522: --against names the three declarations that are not built yet", async () => {
  await assert.rejects(
    () => diff.run({ site: "Acme", against: "proposal" }, [], ctxWith()),
    /not built[\s\S]*allowed: capture[\s\S]*same address\n *at an earlier moment/,
    "a flag that accepted the value and answered approximately would be worse than one that says "
      + "what it does not do",
  );
});

test("SH522: no capture is a refusal naming the command that writes one", async () => {
  const ctx = ctxWith();
  ctx.fs.readFile = async () => {
    const error = new Error("ENOENT");
    error.code = "ENOENT";
    throw error;
  };

  await assert.rejects(() => diff.run({ site: "Acme" }, [], ctx),
    /no capture at[\s\S]*shio clone/);
});
