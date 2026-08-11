/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH592 — `shio clone --refresh`: re-check a capture and pay only for what moved.
 *
 * ## What is actually being asserted
 *
 * Not "the flag runs". The claim is about **cost**, and cost is only observable at the request
 * boundary — so every case here reads what the server was asked for and with which headers. A
 * refresh that quietly re-fetched everything would produce an identical inventory and pass any
 * assertion written about the artefact.
 *
 * The archetype comes from `fixtures/source-sites/` (SH560) rather than a literal in this file, and
 * the fetch is wrapped rather than replaced: the wrapper adds the one thing the corpus's own driver
 * has no reason to model — an `ETag`, and a 304 when it matches. That keeps the pages, the assets
 * and the link graph the corpus's, and makes this file's subject the conditional request alone.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as nodeFs from "node:fs/promises";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { INVENTORY_FILE } from "../src/clone.mjs";
import * as clone from "../src/commands/clone.mjs";
import { load } from "../../fixtures/source-sites/corpus.mjs";

const ORIGIN = "https://fixture.test";

/** The archetype: two pages, a stylesheet, fonts and images — a source with something to re-check. */
const archetype = await load("theme-heavy");

/**
 * The corpus's route table behind a fetch that understands `If-None-Match`.
 *
 * <p>`changed` is the whole experiment: a path in it gets a new ETag, so the same request that
 * answers 304 for every other page answers 200 for that one. Every request is recorded with the
 * header it carried, because "was this asked conditionally" is a different question from "was this
 * asked".
 */
function conditionalFetch(table, changed = new Set()) {
  const requested = [];
  const etagOf = (key) => `"${changed.has(key) ? "v2" : "v1"}-${key}"`;
  return {
    requested,
    async fetch(url, init = {}) {
      const parsed = new URL(url, ORIGIN);
      const key = `${parsed.pathname}${parsed.search}`;
      const ifNoneMatch = init.headers?.["If-None-Match"] ?? null;
      requested.push({ key, ifNoneMatch });
      const row = table.get(key) ?? table.get(parsed.pathname);
      if (!row) return respond(404, "text/plain", Buffer.from("not found"), null);
      const etag = etagOf(key);
      if (ifNoneMatch && ifNoneMatch === etag) {
        return respond(304, row.type, Buffer.alloc(0), etag);
      }
      return respond(row.status, row.type, row.body, etag);
    },
  };
}

function respond(status, type, body, etag) {
  const headers = new Map([
    ["content-type", type],
    ["content-length", String(body.length)],
    ...(etag ? [["etag", etag]] : []),
  ]);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => headers.get(String(name).toLowerCase()) ?? null },
    async text() {
      return body.toString("utf8");
    },
    async arrayBuffer() {
      return body.buffer.slice(body.byteOffset, body.byteOffset + body.byteLength);
    },
  };
}

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

async function workspace() {
  const cwd = await mkdtemp(join(tmpdir(), "shio-sh592-"));
  return { cwd, dispose: () => rm(cwd, { recursive: true, force: true }) };
}

function context(cwd, source) {
  return { env: {}, cwd, stdout: sink(), stderr: sink(), fetch: source.fetch, fs: nodeFs };
}

/** Page requests only — robots.txt is a fetch this is not making a claim about. */
const pageRequests = (source) => source.requested.filter((row) => row.key !== "/robots.txt");

test("SH592: a capture records what the server said about asking again", async () => {
  const space = await workspace();
  try {
    const source = conditionalFetch(archetype.table);
    await clone.run({ markup: true, depth: 2 }, [ORIGIN], context(space.cwd, source));
    const inventory = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    assert.ok(inventory.pages.length >= 2, "the archetype captured its pages");
    for (const row of inventory.pages) {
      assert.ok(
        row.etag,
        `${row.path} carried an ETag and the row does not. Without it the next --refresh has `
          + "nothing to be conditional about, which is the whole mechanism",
      );
    }
    // The version is deliberately unmoved: these fields are additive and optional, and SH592's own
    // rule is that a bump is a migration or a refusal. A bump here would have made every capture in
    // existence need one to gain a field none of its readers wants.
    assert.equal(inventory.version, 5, "an additive optional field is not a version bump");
  } finally {
    await space.dispose();
  }
});

test("SH592: nothing moved, so nothing is fetched twice — asked conditionally, answered 304", async () => {
  const space = await workspace();
  try {
    const first = conditionalFetch(archetype.table);
    await clone.run({ markup: true, depth: 2 }, [ORIGIN], context(space.cwd, first));
    const before = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    // No URL. The capture says where the site is — a refresh that made the caller retype the origin
    // would let a typo refresh against a different host.
    const second = conditionalFetch(archetype.table);
    const ctx = context(space.cwd, second);
    await clone.run({ markup: true, refresh: true }, [], ctx);
    const after = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    const asked = pageRequests(second);
    assert.equal(
      asked.length,
      before.pages.length,
      `a refresh asks once per captured page and no more. Asked: ${asked.map((r) => r.key).join(" ")}`,
    );
    assert.deepEqual(
      asked.filter((row) => !row.ifNoneMatch).map((row) => row.key),
      [],
      "and every one of them carried If-None-Match — an unconditional request is a page the server "
        + "had to send, which is the cost this flag exists to avoid",
    );

    assert.deepEqual(
      after.pages.map((row) => row.path).sort(),
      before.pages.map((row) => row.path).sort(),
      "the refreshed capture holds the same pages",
    );
    assert.deepEqual(
      after.pages.map((row) => row.title),
      before.pages.map((row) => row.title),
      "rebuilt from the markup on disk, so a 304 row is the row it was — not an emptier one",
    );
    assert.ok(
      after.notes.some((note) => /--refresh re-checked .* unchanged \(304\)/.test(note)),
      `the run says what it did. Notes: ${JSON.stringify(after.notes, null, 2)}`,
    );
    assert.ok(
      !after.notes.some((note) => /carried no ETag/.test(note)),
      "and does not claim it had to fetch anything in full",
    );
  } finally {
    await space.dispose();
  }
});

test("SH592: one page moved, so one page is re-read and the rest are not", async () => {
  const space = await workspace();
  try {
    const first = conditionalFetch(archetype.table);
    await clone.run({ markup: true, depth: 2 }, [ORIGIN], context(space.cwd, first));

    const second = conditionalFetch(archetype.table, new Set(["/about"]));
    const ctx = context(space.cwd, second);
    await clone.run({ markup: true, refresh: true }, [], ctx);
    const after = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    // The distinction the counts have to keep: "re-read" is not "re-fetched everything". One page
    // answered 200 and the other answered 304, on the same number of requests. Asserted as the
    // exact sentence rather than an alternation — a check that accepts either split is a check that
    // cannot tell a working refresh from one that re-read both.
    assert.ok(
      after.notes.some((note) =>
        note === "--refresh re-checked 2 captured page(s): 1 unchanged (304), 1 re-read, "
          + "0 no longer captured"),
      `the note distinguishes the two. Notes: ${JSON.stringify(after.notes, null, 2)}`,
    );
    assert.equal(
      pageRequests(second).length,
      2,
      "a changed page costs one request here because this source is not rendered; under --render it "
        + "costs one request plus the browser, and an unchanged one costs neither",
    );
    assert.ok(
      after.pages.find((row) => row.path === "/about"),
      "and the page that moved is still in the capture",
    );
  } finally {
    await space.dispose();
  }
});

test("SH592: --refresh refuses what it cannot honestly do", async () => {
  const space = await workspace();
  try {
    const source = conditionalFetch(archetype.table);

    // No capture at all. `--refresh` re-checks one; it cannot make the first.
    await assert.rejects(
      () => clone.run({ refresh: true }, [], context(space.cwd, source)),
      (error) => {
        assert.match(error.message, /there is no capture at/);
        assert.match(error.message, /shio clone https:\/\/example\.com --markup/,
          "and it names the command that makes one");
        return true;
      },
    );

    // A capture kept without --markup. This is the loud one: the mechanism is *rebuild the
    // unchanged row from disk*, so with nothing on disk a 304 would be answered by inventing a row.
    await clone.run({ depth: 2 }, [ORIGIN], context(space.cwd, source));
    await assert.rejects(
      () => clone.run({ refresh: true }, [], context(space.cwd, source)),
      (error) => {
        assert.match(error.message, /kept no page markup/);
        assert.match(error.message, /--markup/);
        return true;
      },
    );
  } finally {
    await space.dispose();
  }
});

test("SH592: a link that appeared on a changed page is named, never followed", async () => {
  const space = await workspace();
  try {
    const first = conditionalFetch(archetype.table);
    await clone.run({ markup: true, depth: 0 }, [ORIGIN], context(space.cwd, first));
    const before = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));
    assert.equal(before.pages.length, 1, "--depth 0 captured the seed and nothing else");

    // The seed's ETag moves, so it is re-read, and its links are paths this capture does not hold.
    const second = conditionalFetch(archetype.table, new Set(["/"]));
    await clone.run({ markup: true, refresh: true }, [], context(space.cwd, second));
    const after = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    assert.equal(
      after.pages.length,
      1,
      "a refresh re-checks what was captured. Following a new link is a crawl, and a crawl is the "
        + "thing being avoided",
    );
    assert.ok(
      after.notes.some((note) => /are linked from a page that changed and are NOT in this capture/.test(note)),
      `and it says so, or the inventory reads as a complete capture — the SH431 failure exactly. `
        + `Notes: ${JSON.stringify(after.notes, null, 2)}`,
    );
    assert.deepEqual(
      pageRequests(second).map((row) => row.key),
      ["/"],
      "and it did not ask the server for any of them",
    );
  } finally {
    await space.dispose();
  }
});

// -------------------------------------------------------------------------------------------------
// SH611 — the other half, and the larger one by volume
// -------------------------------------------------------------------------------------------------

/**
 * The bytes, re-checked by byte.
 *
 * <p>SH592 made a *page* cheap to re-check and left the assets alone: one was re-downloaded only
 * when the page referencing it had changed, so a stylesheet, a font or a hero image that moved under
 * a still page was silently the old one and the report's only answer was `run shio clone`. That is
 * the larger half — `vilt-group.com` was 198 pages and <b>403 assets</b>, and the assets are where
 * the 52.7 MB is.</p>
 *
 * <p>Same subject as the cases above and therefore the same instrument: the claim is about
 * <b>cost</b>, so it is read at the request boundary. An implementation that re-downloaded every
 * asset and then discarded the bytes would write a byte-identical inventory and pass anything
 * asserted about the artefact.</p>
 */
const ASSET_FLAGS = { markup: true, depth: 2, assets: true, site: "Fixture" };

/** Asset requests only — the pages and robots.txt are a different claim. */
const assetRequests = (source) => source.requested.filter(
  (row) => row.key !== "/robots.txt" && !row.key.endsWith(".html")
    && row.key !== "/" && row.key !== "/about/",
);

test("SH611: a capture records an asset's validators, or the next refresh cannot be cheap", async () => {
  const space = await workspace();
  try {
    const source = conditionalFetch(archetype.table);
    await clone.run(ASSET_FLAGS, [ORIGIN], context(space.cwd, source));
    const inventory = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    const placed = inventory.assets.filter((asset) => asset.file);
    assert.ok(placed.length >= 2, `the archetype placed assets: ${JSON.stringify(inventory.assets)}`);
    for (const asset of placed) {
      assert.ok(
        asset.etag,
        `${asset.path} was served with an ETag and its row does not carry one. The run that makes `
          + "the next one cheap is the ORDINARY capture, so a validator nobody stored is a request "
          + "nobody can avoid",
      );
    }
    assert.equal(inventory.version, 5, "an additive optional field is not a version bump");
  } finally {
    await space.dispose();
  }
});

test("SH611: no asset moved, so no asset is downloaded twice — asked conditionally, answered 304", async () => {
  const space = await workspace();
  try {
    const first = conditionalFetch(archetype.table);
    await clone.run(ASSET_FLAGS, [ORIGIN], context(space.cwd, first));
    const before = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    const second = conditionalFetch(archetype.table);
    await clone.run({ ...ASSET_FLAGS, refresh: true }, [], context(space.cwd, second));
    const after = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    const asked = assetRequests(second);
    assert.ok(asked.length > 0, "the refresh re-checked the assets at all");
    assert.deepEqual(
      asked.filter((row) => !row.ifNoneMatch).map((row) => row.key),
      [],
      "every asset request carried If-None-Match. An unconditional one is a file the server had to "
        + "send, and on the capture that motivated this that is where the 52.7 MB is",
    );
    assert.ok(
      after.download.unchanged > 0,
      `the run counts what it did not download. download: ${JSON.stringify(after.download)}`,
    );
    assert.equal(
      after.download.wrote,
      0,
      "and nothing was written: `wrote` counts bytes that moved, so an unchanged asset counted "
        + "there would report work that did not happen",
    );

    // The row survives the cheap path intact. A 304 asset is a PLACED asset, and a row that dropped
    // its `file` because no download happened this run would read as one the capture never got.
    const fileOf = (inv) => new Map(inv.assets.map((a) => [a.path, a.file ?? null]));
    assert.deepEqual(fileOf(after), fileOf(before), "every placement is carried, none recomputed");
    const digestOf = (inv) => new Map(inv.assets.map((a) => [a.path, a.sha256 ?? null]));
    assert.deepEqual(
      digestOf(after),
      digestOf(before),
      "and so is every digest — this run never held the bytes, so it cannot have hashed them",
    );
    assert.ok(
      after.notes.some((note) => /--refresh re-checked \d+ asset\(s\) by byte/.test(note)),
      `the run says what it did. Notes: ${JSON.stringify(after.notes, null, 2)}`,
    );
  } finally {
    await space.dispose();
  }
});

test("SH611: one asset moved under a page that did not, and it is the one that is re-read", async () => {
  const space = await workspace();
  try {
    const first = conditionalFetch(archetype.table);
    await clone.run(ASSET_FLAGS, [ORIGIN], context(space.cwd, first));
    const before = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    // The defect exactly: the stylesheet changed and no page did. Under SH592 alone every page
    // answers 304, nothing marks the asset stale, and the replica keeps serving last week's CSS.
    const stylesheet = before.assets.find((asset) => asset.kind === "stylesheet" && asset.file);
    assert.ok(stylesheet, `the archetype captured a stylesheet: ${JSON.stringify(before.assets)}`);

    const second = conditionalFetch(archetype.table, new Set([stylesheet.path]));
    await clone.run({ ...ASSET_FLAGS, refresh: true }, [], context(space.cwd, second));
    const after = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    assert.equal(
      after.download.wrote,
      1,
      "exactly one asset was downloaded, and it is the one whose bytes changed — not the page's "
        + "worth of them, and not none",
    );
    assert.ok(
      after.download.unchanged > 0,
      "and the rest answered 304 in the same run, which is what makes this a saving rather than a "
        + "second full download triggered differently",
    );
    assert.notEqual(
      after.assets.find((asset) => asset.path === stylesheet.path)?.etag,
      stylesheet.etag,
      "the changed asset's row carries the new validator, so the NEXT refresh is conditional on it "
        + "rather than on the stale one",
    );
  } finally {
    await space.dispose();
  }
});

test("SH611: a 304 for a file no longer in the tree is not honoured", async () => {
  const space = await workspace();
  try {
    const first = conditionalFetch(archetype.table);
    await clone.run(ASSET_FLAGS, [ORIGIN], context(space.cwd, first));
    const before = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    // A 304 says the SERVER's copy is unchanged. It says nothing about ours, and the difference is
    // the only thing that can make a carried row a lie: an inventory naming a file that is not there
    // is worse than one that re-downloaded it, because everything downstream reads the row.
    const placed = before.assets.find((asset) => asset.file && !asset.dedupedWith);
    assert.ok(placed, "the archetype placed an asset to delete");
    await rm(join(space.cwd, "shio", "assets", placed.file), { force: true });

    const second = conditionalFetch(archetype.table);
    await clone.run({ ...ASSET_FLAGS, refresh: true }, [], context(space.cwd, second));
    const after = JSON.parse(await readFile(join(space.cwd, INVENTORY_FILE), "utf8"));

    assert.equal(
      after.assets.find((asset) => asset.path === placed.path)?.file,
      placed.file,
      "the row still names the file, because the file is there again",
    );
    await readFile(join(space.cwd, "shio", "assets", placed.file));
    assert.ok(
      after.notes.some((note) => /answered 304 and were downloaded anyway/.test(note)),
      `and the run says why it paid. Notes: ${JSON.stringify(after.notes, null, 2)}`,
    );
  } finally {
    await space.dispose();
  }
});
