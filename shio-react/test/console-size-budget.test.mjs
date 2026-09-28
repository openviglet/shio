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
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { BUILD_INFO, buildProvenance, sourceDigest } from "../scripts/console-build-info.mjs";
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
 *
 * ## SH1126 — and the build has to be THIS tree's
 *
 * "Wherever a build exists" was not enough. The gate measured a build of *some* tree with nothing
 * saying which, and it went wrong in both directions on consecutive tasks: one passed `pnpm test`
 * against a build that predated its own change, and the next failed the headroom check only because
 * a build made outside the session happened to include it. Neither verdict was about the bytes the
 * task under test had produced.
 *
 * So `buildProvenance` decides, and there are now three ways to be **not measured** — no build, a
 * build from before `build-info.json` existed, and a build from another tree. All three say so in
 * the same breath and produce no total, because a number from a different tree is worse than no
 * number. A stale build must not *fail* the suite, or every source edit would demand a build before
 * `pnpm test`; `SHIO_REQUIRE_CONSOLE_BUILD=1` is how the run that does build (CI's `js` job) turns
 * "not measured" into a failure, which is what makes the number real on every pull request.
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
  assert.equal(stemOf("/assets/breadcrumb.context-BssgEydx-D1t6Pnxg.js"), "breadcrumb.context.js",
    "SH1044: a package that ships pre-hashed files gets re-hashed on the way through, so a chunk "
    + "can carry more than one — stripping once leaves a ceiling keyed by somebody else's release, "
    + "which is the stalest kind");
  assert.equal(stemOf("/assets/jsonMode.js"), "jsonMode.js",
    "and a name whose own last segment is eight characters is not a hash: there is no `-` before "
    + "it, which is what the rule keys on rather than the length alone");
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

/**
 * SH1126 — whether this run may measure, said once for both readers below.
 *
 * <p>Said out loud rather than passed over: "there is no build from this tree" and "the console got
 * smaller" must never read the same. `SHIO_REQUIRE_CONSOLE_BUILD` is the other half — the run that
 * builds refuses to be told nothing, and every other run is told why.</p>
 */
function measurable(t) {
  const provenance = buildProvenance(DIST, ROOT);
  if (provenance.measurable) return true;
  const said = `SH1126: not measured — ${provenance.why}`;
  if (process.env.SHIO_REQUIRE_CONSOLE_BUILD === "1") {
    assert.fail(`${said}\n  This run set SHIO_REQUIRE_CONSOLE_BUILD=1, which means it built the `
      + "console and expects the size gate to have a number. A mismatch here is the build step "
      + "and the suite disagreeing about the tree, not a stale checkout.");
  }
  t.diagnostic(said);
  return false;
}

test("SH1043: the console's eager load stays inside its budget", async (t) => {
  if (!measurable(t)) return;
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
  if (!measurable(t)) return;
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

test("SH1180: index.es.js's ceiling names the split page's divider for as long as a page composes it", async () => {
  // SH1094's BentoSplitPage put react-resizable-panels (about 35 KB raw) into the shared eager
  // chunk, and it shipped unmeasured because every local run until SH1091 skipped a build from
  // another tree. The raise is only honest while the cause is in the tree: when the last split page
  // goes, the ceiling is 35 KB of room nobody is using, and this says so instead of leaving it.
  const why = budget.chunks["index.es.js"].why;
  const sources = await Promise.all(["post.edit.page.tsx", "translate.page.tsx"]
    .map((file) => readFile(join(ROOT, "src", "app", "content", file), "utf8").catch(() => "")));
  const composed = sources.some((source) => /<BentoSplitPage\b/u.test(source));
  if (composed) {
    assert.match(why, /react-resizable-panels/u,
      "SH1180: a page composes BentoSplitPage and index.es.js's reason does not name the divider it costs");
  } else {
    assert.doesNotMatch(why, /react-resizable-panels/u,
      "SH1180: no page composes BentoSplitPage any more; lower index.es.js's ceiling and drop the divider from its reason");
  }
});

/*
 * SH1126 — the planted mismatch, which is this task's own assertion.
 *
 * §LXXII.23 names it: a synthetic dist whose digest differs from the tree reports "not measured" and
 * never a total. Driven through `buildProvenance` rather than by rebuilding, so it runs on every run
 * with no build — the same discipline the planted-import tests above follow, and the reason this is a
 * check that has been shown to fail rather than one that has never been asked.
 */
test("SH1126: a build from another tree is not measured, and says which", async () => {
  const dist = await plant({ preloaded: [["vendor-bbbbbbbb.js", 2000]] });
  await writeFile(join(dist, BUILD_INFO),
    JSON.stringify({ sourceDigest: "sha256:deadbeef", commit: "0000000" }), "utf8");

  const provenance = buildProvenance(dist, ROOT);

  assert.equal(provenance.measurable, false,
    "SH1126: a build whose sources are not this tree's must not be measured — that is the whole "
    + "defect, and it went wrong in both directions: a pass against bytes older than the change, "
    + "and a failure from a build made outside the session");
  assert.match(provenance.why, /made from different sources/u);
  assert.match(provenance.why, /deadbeef/u,
    "SH1126: and it names both digests, because the reader's next question is which build they have");
  assert.match(provenance.why, /commit 0000000/u,
    "SH1126: the commit is recorded for exactly this sentence — it cannot be the comparison, since a "
    + "working tree is dirty most of the time anybody runs this");
});

test("SH1126: a build from THIS tree is measured, and one with no build-info is not", async () => {
  const dist = await plant({ preloaded: [["vendor-bbbbbbbb.js", 2000]] });

  // No `build-info.json` at all: a build from before this existed. Not measured, and the sentence
  // says so rather than pretending the console has no eager cost.
  const before = buildProvenance(dist, ROOT);
  assert.equal(before.measurable, false);
  assert.match(before.why, new RegExp(`carries no ${BUILD_INFO}`, "u"));
  assert.match(before.why, /predates SH1126/u);

  // The same dist, now claiming this tree's digest: measurable, and the reader gets a number.
  await writeFile(join(dist, BUILD_INFO),
    JSON.stringify({ sourceDigest: sourceDigest(ROOT), commit: "abc1234" }), "utf8");
  const after = buildProvenance(dist, ROOT);
  assert.equal(after.measurable, true, "SH1126: a matching digest is the one case that measures");
  assert.equal(after.why, "");
  const measured = await measure(dist);
  assert.ok(measured.raw > 0, "SH1126: and the reader still reads it — the provenance check gates "
    + "the verdict, it does not replace the measurement");
});

test("SH1126: an absent build is not measured either, and that is the third sentence", async () => {
  const empty = await mkdtemp(join(tmpdir(), "sh1126-"));
  const provenance = buildProvenance(empty, ROOT);

  assert.equal(provenance.measurable, false);
  assert.match(provenance.why, /no built console in this tree/u);
  // Three ways to be not-measured, one outcome: "there is no build", "the build predates this" and
  // "the build is from another tree" all mean no number, because a number from another tree is worse
  // than no number. What differs is only the sentence, and each one names what to do.
  assert.match(provenance.why, /pnpm run build:console/u);
});

test("SH1126: the digest covers the inputs that decide the bundle, and states what it does not", async () => {
  const inputs = (await import("../scripts/console-build-info.mjs")).SOURCE_INPUTS;
  assert.deepEqual([...inputs], ["src", "index.html", "vite.config.ts", "package.json"],
    "SH1126: §LXXII.23 asked for `src`; the other three are the entry, the chunking and the "
    + "dependency names, all of which decide what the bundle contains. The list is the decision, so "
    + "a reader asking \"would my change have been caught\" is asking about this line.");

  // The digest is a function of bytes and paths, not of the filesystem's order — so it is stable
  // across machines and a rename is a different digest (P5).
  assert.equal(sourceDigest(ROOT), sourceDigest(ROOT));
  assert.match(sourceDigest(ROOT), /^sha256:[0-9a-f]{64}$/u);
});
