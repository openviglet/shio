/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH592 — recrawling a site to test something that is not the crawl.
 *
 * <p>`shio clone` over `vilt-group.com` with `--render`: 198 pages, 403 assets, 52.7 MB, about ten
 * minutes — by far the most expensive step in the chain, and the only one whose output was
 * immutable. Twice in this work that cost a full recapture for a reason that had nothing to do with
 * the source: SH578 changed the inventory's asset shape and `diff` refused every capture written
 * before it, and once the source had not changed at all.</p>
 *
 * <p>The rule these assertions hold is the one worth carrying: <b>a version bump is a migration or
 * a refusal, and a refusal is honest only when the new field genuinely cannot be computed from what
 * is on disk.</b> Both halves are asserted, because the second is what stops this from becoming a
 * migration that guesses.</p>
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { countAssets, INVENTORY_VERSION, scanPage } from "../src/clone.mjs";
import {
  describeMigration,
  MIGRATABLE_FROM,
  migrateInventory,
} from "../src/inventory-migrate.mjs";
import * as diff from "../src/commands/diff.mjs";

/** A page with two of its own images, one head link and an inline stylesheet that calls url(). */
const PAGE_HTML = `<html><head><title>Home</title>
  <link rel="icon" href="/favicon.ico">
  <style>.hero{background:url("/img/bg.png")}</style></head>
  <body><img src="/img/a.png"><img src="/img/b.png">
  <p>Copy that reads like a page a person wrote.</p></body></html>`;

/** The shape SH578 replaced: `assets` as one number, which is a version-3 row. */
const V3 = {
  version: 3,
  origin: "https://x.test/",
  pages: [{
    path: "/", url: "https://x.test/", title: "Home",
    links: { internal: 0, external: 0 }, assets: 4, forms: 0, textChars: 44,
  }],
};

test("SH592: a version-3 row is migrated from the markup it already stored, not re-crawled", () => {
  const { inventory, migrated, from, blocked } = migrateInventory(V3, new Map([["/", PAGE_HTML]]));

  assert.equal(from, 3);
  assert.deepEqual(migrated, ["/"]);
  assert.deepEqual(blocked, []);
  assert.equal(inventory.version, INVENTORY_VERSION);
  assert.equal(inventory.migratedFrom, 3);

  // Computed by the same function the capture runs, against the same bytes it stored — so a
  // migrated row and a freshly captured one cannot disagree.
  const fresh = countAssets(scanPage(PAGE_HTML, "https://x.test/").assets);
  assert.deepEqual(inventory.pages[0].assets, fresh);
  assert.equal(typeof fresh.own, "number");
  assert.equal(typeof fresh.head, "number");
  assert.equal(typeof fresh.style, "number");
});

test("SH592: a page with no stored markup is reported, never derived and never scored", () => {
  // A capture taken without --markup keeps no bytes to re-read. Guessing here would be the failure
  // the whole task is against, one level along: a number nobody measured, read as one that was.
  const { inventory, migrated, blocked } = migrateInventory(V3, new Map());

  assert.deepEqual(migrated, []);
  assert.deepEqual(blocked, ["/"]);
  assert.equal(inventory.pages[0].assets, 4, "left exactly as it was");

  const said = describeMigration({ from: 3, migrated: [], blocked: ["/"] });
  assert.match(said, /no stored markup/);
  assert.match(said, /--markup/);
});

test("SH592: a current capture is returned untouched", () => {
  const current = { version: INVENTORY_VERSION, pages: [{ path: "/", assets: { own: 2, head: 1, style: 1 } }] };
  const { inventory, migrated, blocked } = migrateInventory(current, new Map([["/", PAGE_HTML]]));

  assert.equal(inventory, current, "the same object — nothing to do is nothing done");
  assert.deepEqual(migrated, []);
  assert.deepEqual(blocked, []);
  assert.equal(describeMigration({ from: INVENTORY_VERSION, migrated: [], blocked: [] }), "");
});

test("SH592: a page already carrying the split is not recomputed", () => {
  // A mixed inventory — one row migrated by an earlier run, one not — must not have the first
  // rewritten. Re-deriving a row that is already right is how a migration becomes a second
  // implementation of the capture.
  const mixed = {
    version: 3,
    pages: [
      { path: "/kept", assets: { own: 9, head: 9, style: 9 } },
      { path: "/old", assets: 4 },
    ],
  };
  const { inventory, migrated } = migrateInventory(mixed, new Map([["/old", PAGE_HTML], ["/kept", PAGE_HTML]]));

  assert.deepEqual(migrated, ["/old"]);
  assert.deepEqual(inventory.pages[0].assets, { own: 9, head: 9, style: 9 });
});

test("SH592: a version below the floor is refused BY FIELD, because the crawl is not on disk", () => {
  // The other half of the rule, and the half that keeps this honest. `unfollowed` and `problems`
  // are facts about the crawl, not about a page, so no amount of stored markup recovers them — and
  // an absent `unfollowed` read as an empty one says "nothing was left uncrawled", which turns a
  // bounded sample into a report that reads as complete.
  assert.equal(MIGRATABLE_FROM, 3);

  assert.throws(
    () => migrateInventory({ version: 2, pages: [{ path: "/" }] }, new Map([["/", PAGE_HTML]])),
    (error) => {
      assert.match(error.message, /about the CRAWL rather than about a page/);
      assert.match(error.message, /unfollowed/, "the FIELD, not just the version number");
      assert.match(error.message, /nothing was left\n?\s*uncrawled/);
      assert.match(error.message, /not a policy/, "it says the refusal is the exception");
      return true;
    },
  );
});

test("SH592: diff reads a version-3 capture instead of refusing it", async () => {
  // The end this exists for: the same one-line downstream change that used to need a ten-minute
  // re-crawl now needs nothing. `diff` used to throw `cannot be compared (SH578)` here.
  const out = { text: "", write: (s) => { out.text += s; } };
  const ctx = {
    stdout: out,
    stderr: out,
    env: {},
    cwd: process.cwd(),
    fs: {
      async readFile(at) {
        const path = String(at).split("\\").join("/");
        if (path.includes("inventory")) return JSON.stringify(V3);
        // The markup `shio clone --markup` stored for `/`, which is what makes this migratable.
        if (path.includes("source-markup")) return PAGE_HTML;
        throw Object.assign(new Error("ENOENT"), { code: "ENOENT" });
      },
    },
    fetch: async () => ({ ok: true, status: 200, async text() { return PAGE_HTML; } }),
  };

  const result = await diff.run({ site: "Acme", url: "http://shio.test" }, [], ctx);

  assert.equal(result.pages.length, 1);
  assert.match(out.text, /migrated this capture from version 3/);
  assert.match(out.text, /not re-crawled/);
  assert.doesNotMatch(out.text, /cannot be compared/);
});
