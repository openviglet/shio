/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1034 — `shio assets prune`. Network-free: a scripted fetch answers the media listing.
 *
 * The two assertions that matter here are about the difference between **zero and absent**, which
 * is the whole reason `usageCount` is nullable. A run that treated a missing count as zero would
 * report a healthy library as entirely unused and offer to delete it, so the test that a
 * pre-SH1034 instance is refused rather than mis-read is the one guarding the destructive reading.
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";

import * as assets from "../src/commands/assets.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

function listing(items) {
  return { items, page: 0, size: 200, totalFiles: items.length, facets: {} };
}

function scriptedFetch(body) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
    return {
      ok: true,
      status: 200,
      headers: { get: () => null },
      async text() {
        return JSON.stringify(body);
      },
      async json() {
        return body;
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

function ctxWith(fetchImpl) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
  };
}

const LIBRARY = listing([
  { id: "a1", fileName: "hero.png", size: 2048, usageCount: 2, siteName: "Blog", folderName: "img" },
  { id: "a2", fileName: "old.png", size: 1024, usageCount: 0, siteName: "Blog", folderName: "img" },
  { id: "a3", fileName: "spare.pdf", size: 3072, usageCount: 0, siteName: "Blog", folderName: "doc" },
]);

test("SH1034: prune reports the unused assets and what they would free", async () => {
  const ctx = ctxWith(scriptedFetch(LIBRARY));

  const result = await assets.run({}, ["prune"], ctx);

  assert.equal(result.scanned, 3);
  assert.equal(result.unused, 2);
  // 1024 + 3072, and NOT the referenced asset's 2048 — the sum is the decision this command
  // exists to support, so a referenced row leaking into it is the defect to catch.
  assert.equal(result.bytes, 4096);
  assert.equal(result.deleted, false);
  assert.match(ctx.stdout.text, /3 scanned, 2 unused, 4\.0 KB reclaimable/);
  assert.match(ctx.stdout.text, /old\.png/);
  assert.match(ctx.stdout.text, /spare\.pdf/);
  assert.ok(!ctx.stdout.text.includes("hero.png"), "a referenced asset must not be listed");
});

test("SH1034: it says nothing was deleted, because a report that looks like an act is one", async () => {
  const ctx = ctxWith(scriptedFetch(LIBRARY));
  await assets.run({}, ["prune"], ctx);
  assert.match(ctx.stdout.text, /nothing was deleted — this is a dry run/);
});

test("SH1034: --json carries the set a pipeline would act on", async () => {
  const ctx = ctxWith(scriptedFetch(LIBRARY));
  await assets.run({ json: true }, ["prune"], ctx);

  const payload = JSON.parse(ctx.stdout.text);
  assert.equal(payload.unused, 2);
  assert.deepEqual(payload.items.map((row) => row.fileName), ["old.png", "spare.pdf"]);
  assert.equal(payload.deleted, false);
});

test("SH1034: an instance that cannot answer is refused, not read as all-unused", async () => {
  // The pre-SH1034 shape: rows with no `usageCount` at all. Absent is not zero, and a command
  // that conflated them would print "3 unused" about a library where everything is in use —
  // then invite a deletion. This is the assertion that keeps that impossible.
  const ctx = ctxWith(scriptedFetch(listing([
    { id: "a1", fileName: "hero.png", size: 2048 },
    { id: "a2", fileName: "old.png", size: 1024 },
  ])));

  await assert.rejects(
    () => assets.run({}, ["prune"], ctx),
    /does not report asset usage/,
  );
});

test("SH1034: an empty library is zero unused rather than a refusal", async () => {
  // The refusal above keys on "every row is unanswered", which is vacuously true of no rows.
  // An empty media library is a legitimate answer and must not look like an old instance.
  const ctx = ctxWith(scriptedFetch(listing([])));
  const result = await assets.run({}, ["prune"], ctx);
  assert.deepEqual(result, {
    scanned: 0, unused: 0, bytes: 0, pagesRendered: null, pagesTotal: null, deleted: false,
  });
});

const WITH_LOADS = {
  ...listing([
    { id: "f1", fileName: "inter-400.woff2", size: 2048, usageCount: 0, loadedBy: 5 },
    { id: "a2", fileName: "old.png", size: 1024, usageCount: 0, loadedBy: 0 },
    { id: "a1", fileName: "hero.png", size: 4096, usageCount: 1, loadedBy: 2 },
  ]),
  pagesRendered: 5,
  pagesTotal: 5,
};

test("SH1280: a font every page loads is not unused, though no field references it", async () => {
  const fetchImpl = scriptedFetch(WITH_LOADS);
  const ctx = ctxWith(fetchImpl);

  const result = await assets.run({}, ["prune"], ctx);

  assert.match(fetchImpl.calls[0].url, /[?&]loads=true/, "SH1280: prune asks the listing for page loads");
  assert.equal(result.unused, 1, "SH1280: only the asset nothing references AND no page loads");
  assert.ok(!ctx.stdout.text.includes("inter-400.woff2"),
    "SH1280: Quilhara's fonts were listed reclaimable while every page fetched them");
  assert.match(ctx.stdout.text, /1 unused, 1\.0 KB reclaimable \(rendered 5 page\(s\) to know\)/);
  assert.ok(!ctx.stdout.text.includes("note:"), "SH1280: a complete count needs no caveat");
});

test("SH1280: a count the server cut short says so, since a zero past the cap is a gap", async () => {
  const ctx = ctxWith(scriptedFetch({ ...WITH_LOADS, pagesRendered: 500, pagesTotal: 812 }));
  const result = await assets.run({ json: true }, ["prune"], ctx);
  assert.equal(result.pagesRendered, 500);
  assert.equal(result.pagesTotal, 812);

  const text = ctxWith(scriptedFetch({ ...WITH_LOADS, pagesRendered: 500, pagesTotal: 812 }));
  await assets.run({}, ["prune"], text);
  assert.match(text.stdout.text, /only 500 of 812 page\(s\) were rendered/);
});

test("SH1280: an instance that does not count page loads is judged on references, and says so", async () => {
  const ctx = ctxWith(scriptedFetch(LIBRARY));
  await assets.run({}, ["prune"], ctx);
  assert.match(ctx.stdout.text, /does not count what rendered pages load/);
});

test("SH1034: an unknown sub-command names the only one there is", async () => {
  const ctx = ctxWith(scriptedFetch(LIBRARY));
  await assert.rejects(() => assets.run({}, ["gc"], ctx), /unknown sub-command 'gc'/);
});
