/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { mkdtemp, mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import {
  BUDGET_FILE, breaches, eagerOf, kb, measure, report, stemOf,
} from "../scripts/console-size.mjs";

/**
 * SH1043 — a size budget for the console, in a project where a token regression fails the build.
 *
 * ## The asymmetry this closes
 *
 * The agent's cost is governed to the endpoint — a ceiling per response, a reason for every raise,
 * and P3 making a regression a build failure. The half of the product a *person* waits for had no
 * number at all: the three gates are the Maven suite, the JS suite and conformance, and none of them
 * looks at a byte. That is how a 4.8 MB vendor chunk came to be preloaded on every route with
 * nobody noticing, which is SH1044 and which this measured before it was written down.
 *
 * ## What runs where, and why this is not vacuous when it cannot measure
 *
 * The real comparison needs a **built** console, and `pnpm test` does not build one — `vite build`
 * is a minute against a suite that runs in eight seconds. So the comparison runs wherever a build
 * exists (a developer's tree, a `./mvnw package` that did not skip npm) and says so when there is
 * none.
 *
 * A reader that only works when somebody happens to have built is a reader that reports nothing
 * forever, which is the failure every instrument in this repository is written against. So the
 * **planted-import** test below builds a synthetic `dist` and drives the whole reader through it:
 * a chunk the entry statically imports is counted, the same chunk behind a dynamic import is not,
 * and a build whose HTML declares no preloads is refused rather than measured. That runs on every
 * run, with no build and no browser.
 */
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..");
const DIST = join(ROOT, "..", "shio-app", "src", "main", "resources", "public");

const budget = JSON.parse(await readFile(join(ROOT, BUDGET_FILE), "utf8"));

/** A synthetic build, so the reader can be driven without one costing a minute. */
async function plant({ preloaded = [], lazy = [], styles = [], preloadLinks = true } = {}) {
  const dist = await mkdtemp(join(tmpdir(), "sh1043-"));
  await mkdir(join(dist, "assets"), { recursive: true });
  const write = async (name, bytes) =>
    writeFile(join(dist, "assets", name), "x".repeat(bytes), "utf8");

  await write("entry-aaaaaaaa.js", 1000);
  for (const [name, bytes] of preloaded) await write(name, bytes);
  for (const [name, bytes] of lazy) await write(name, bytes);
  for (const [name, bytes] of styles) await write(name, bytes);

  const links = [
    ...(preloadLinks
      ? preloaded.map(([name]) => `<link rel="modulepreload" href="/assets/${name}">`)
      : []),
    ...styles.map(([name]) => `<link rel="stylesheet" href="/assets/${name}">`),
  ];
  await writeFile(join(dist, "index.html"),
    `<html><head>${links.join("")}</head>`
    + `<body><script type="module" src="/assets/entry-aaaaaaaa.js"></script></body></html>`,
    "utf8");
  return dist;
}

test("SH1043: a chunk the entry statically imports is counted, and a lazy one is not", async () => {
  // The planted import, which is the assertion this whole task turns on: a module that arrives
  // eagerly is the cost a person pays before seeing anything, and one behind `import()` is not.
  // Vite states the difference itself — a statically imported chunk gets a modulepreload link and
  // a lazily imported one does not — so this drives the reader through exactly that distinction.
  const dist = await plant({
    preloaded: [["big-bbbbbbbb.js", 500_000]],
    lazy: [["route-cccccccc.js", 900_000]],
  });

  const measured = await measure(dist);

  assert.deepEqual(measured.assets.map((one) => one.asset).sort(),
    ["/assets/big-bbbbbbbb.js", "/assets/entry-aaaaaaaa.js"]);
  assert.ok(measured.raw >= 501_000 && measured.raw < 600_000,
    `the lazy 900 KB route must not be in the eager total; got ${kb(measured.raw)}`);
});

test("SH1043: a large module planted into the entry fails the gate, and names itself", async () => {
  // The assertion this task exists for, driven rather than reasoned: a check that has only ever
  // been run against a build that satisfies it has not been shown to check anything. The budget
  // here is the shape of the real one and the planted chunk is the shape of the accident — a
  // vendor module that somebody imported from a module the entry already reaches.
  const dist = await plant({ preloaded: [["vendor-huge-eeeeeeee.js", 900_000]] });
  const tight = {
    eager: { raw: 10_000, gzip: 10_000 },
    chunks: { "entry.js": { raw: 2000, gzip: 2000, why: "the console's own entry chunk" } },
  };

  const found = breaches(await measure(dist), tight);

  assert.ok(found.some((line) => line.startsWith("vendor-huge.js is eager")),
    `the new chunk has to name itself, or the report is a number and not a diagnosis: ${found}`);
  assert.ok(found.some((line) => line.startsWith("eager raw")), `the total has to move too: ${found}`);
  assert.equal(stemOf("/assets/vendor-huge-eeeeeeee.js"), "vendor-huge.js",
    "a ceiling keyed by the hashed name would be stale on the next edit, which is a budget "
    + "nobody can keep");
});

test("SH1043: a ceiling that outlives its chunk fails too", async () => {
  // The other direction, and the one a plain total would never catch: a budget entry for something
  // that is no longer eager is a ceiling nobody is measuring, and SH1044 is about to make exactly
  // that happen to two of the real entries.
  const dist = await plant({ preloaded: [["entry-only-ffffffff.js", 10]] });
  const stale = {
    eager: { raw: 10_000_000, gzip: 10_000_000 },
    chunks: {
      "entry.js": { raw: 5000, gzip: 5000, why: "the console's own entry chunk" },
      "entry-only.js": { raw: 5000, gzip: 5000, why: "the planted preload" },
      "vendor-gone.js": { raw: 5000, gzip: 5000, why: "a chunk that is no longer loaded eagerly" },
    },
  };

  assert.deepEqual(breaches(await measure(dist), stale),
    ["vendor-gone.js is in the budget and is no longer eager — delete its entry in the same "
      + "commit, so a ceiling cannot outlive the thing it was about"]);
});

test("SH1043: a build that declares no preloads is refused, not measured", () => {
  // The reader composes on the bundler's own analysis, so it depends on those links existing. A
  // build with module preloading turned off would leave it measuring the entry alone and passing
  // every ceiling forever — the report-zero shape, arrived at by a config change nobody connected
  // to this gate.
  assert.throws(
    () => eagerOf('<script type="module" src="/assets/entry-aaaaaaaa.js"></script>'),
    /declares no <link rel="modulepreload">/);
  assert.throws(() => eagerOf("<html></html>"), /no <script type="module">/);
});

test("SH1043: stylesheets count, because they block paint", async () => {
  const dist = await plant({ styles: [["look-dddddddd.css", 300_000]],
    preloaded: [["big-bbbbbbbb.js", 10]] });

  const measured = await measure(dist);

  assert.ok(measured.assets.some((one) => one.asset.endsWith(".css")),
    "a megabyte of CSS is a megabyte the reader waits for, whatever it is not");
});

test("SH1043: the budget is a real document — every entry has a ceiling and a reason", () => {
  // The floor under every assertion below: a budget file that had drifted into an empty object
  // would make each of them pass over nothing.
  assert.ok(budget.eager?.raw > 0 && budget.eager?.gzip > 0);
  const chunks = Object.entries(budget.chunks ?? {});
  assert.ok(chunks.length >= 4, `only ${chunks.length} chunk(s) declared`);
  for (const [name, entry] of chunks) {
    assert.ok(entry.raw > 0 && entry.gzip > 0, `${name} has no ceiling`);
    assert.ok(typeof entry.why === "string" && entry.why.length > 30,
      `${name} has no reason. A ceiling with no sentence beside it is a number the next reader `
      + "cannot tell from a guess");
  }
});

test("SH1043: the console's eager load stays inside its budget", async (t) => {
  if (!existsSync(join(DIST, "index.html"))) {
    // Said out loud rather than passed over: "there is no build in this tree" and "the console got
    // smaller" must never read the same.
    t.diagnostic("SH1043: no built console in this tree, so nothing was measured. Build it with "
      + "`pnpm --filter shio-react exec vite build`, or run `pnpm --filter shio-react run size`.");
    return;
  }
  const measured = await measure(DIST);
  // Printed on every run, which is half of what this is for: a number nobody sees is a number
  // nobody argues with.
  console.log(report(measured));

  assert.deepEqual(breaches(measured, budget), [],
    "SH1043: the console got more expensive to load. This is P3 on the half of the product a "
    + "person waits for: make it smaller, or raise the ceiling in console-size-budget.json and say "
    + "why in the commit — a ceiling moved without a reason is the whole mechanism defeated.");
});

test("SH1043: the ceilings have room under them", async (t) => {
  if (!existsSync(join(DIST, "index.html"))) {
    t.diagnostic("SH1043: no built console in this tree; headroom not checked.");
    return;
  }
  const measured = await measure(DIST);
  // SH736's lesson, one gate over: a ceiling raised to exactly the total that breached it does not
  // measure the next addition, it refuses it — and the cheapest way to pass then is to delete
  // somebody else's feature, which is the degrade-the-measurement non-goal by name.
  for (const [name, ceiling, now] of [
    ["raw", budget.eager.raw, measured.raw], ["gzip", budget.eager.gzip, measured.gzip],
  ]) {
    const room = ceiling - now;
    assert.ok(room >= Math.round(ceiling * 0.04),
      `SH1043: the eager ${name} total is ${kb(now)} against a ceiling of ${kb(ceiling)} — `
      + `${kb(room)} of room, and this gate needs 4% to still be measuring anything.`);
  }
});
