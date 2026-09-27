/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import {
  KEPT, breaches, classify, packageOf, reachability, report, specifiersIn,
} from "../../scripts/dependency-reachability.mjs";

/**
 * SH1045 — a declared dependency the console cannot reach is a claim about the console.
 *
 * ## What this is the other half of
 *
 * SH1043 put a ceiling on what the console costs to load and SH1044 spent it, and both were about
 * code that ships. This is the same argument one level earlier: a package nobody imports ships no
 * bytes and costs anyway — installed on every CI run, pinned in the lockfile, audited, renovated,
 * and read by the next person as evidence that the console uses it. Eighteen had accumulated with
 * nothing to notice, because the shadcn layer under `components/ui` became a re-export of the design
 * system and its own Radix, `clsx` and `tailwind-merge` declarations outlived the code.
 *
 * ## Why this can be trusted to delete things
 *
 * An unused-dependency check that greps `src/` for the name deletes a package the build breaks
 * without, which is why most of them are turned off after their first false positive. The reader
 * this drives classifies four ways instead — imported, required as a peer by something already
 * reachable, run as a binary by `package.json`'s own scripts, or the `@types` of something
 * reachable — and the tests below drive each of those paths against a planted manifest rather than
 * only against a tree that satisfies them.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/** A manifest and its lookups, planted: the gate's own fixture rather than the tree's. */
function plant({ deps = {}, dev = {}, scripts = {}, peers = {}, bins = {}, imports = [], node }) {
  return {
    manifest: { scripts, dependencies: deps, devDependencies: dev },
    imports: new Set(imports),
    peersOf: (name) => peers[name] ?? [],
    binsOf: (name) => bins[name] ?? [],
    node,
  };
}

const reachOf = (rows, name) => rows.find((row) => row.name === name)?.reach;

test("SH1045: a declared dependency nothing reaches is named, and named alone", () => {
  // The assertion this task exists for, driven rather than reasoned: a check that has only ever
  // been run against a manifest that satisfies it has not been shown to check anything.
  const rows = reachability(plant({
    deps: { react: "*", "dead-weight": "*" },
    imports: ["react"],
  }));

  assert.equal(reachOf(rows, "react"), "imported");
  assert.equal(reachOf(rows, "dead-weight"), "unreachable");
  assert.equal(breaches(rows, {}).length, 1, "one finding, for the one dead package");
  assert.match(breaches(rows, {})[0], /^dead-weight is declared and nothing reaches it/u);
});

test("SH1045: a peer of something reachable is reachable, and a peer of something dead is not", () => {
  // The false positive that turns these checks off. `sonner` and `next-themes` appear in no import
  // here: the design system needs them installed beside it, and a grep would delete both.
  const rows = reachability(plant({
    deps: { "design-system": "*", sonner: "*", orphan: "*", "orphans-peer": "*" },
    imports: ["design-system"],
    peers: { "design-system": ["sonner"], orphan: ["orphans-peer"] },
  }));

  assert.equal(reachOf(rows, "sonner"), "peer");
  assert.equal(reachOf(rows, "orphan"), "unreachable");
  assert.equal(reachOf(rows, "orphans-peer"), "unreachable",
    "SH1045: the peer pass runs over the reachable set, not over everything declared — otherwise "
    + "one dead entry keeps the next one alive and the list can never shrink");
});

test("SH1045: a peer two packages deep is still reached, which is what the fixed point is for", () => {
  const rows = reachability(plant({
    deps: { first: "*", second: "*", third: "*" },
    imports: ["first"],
    peers: { first: ["second"], second: ["third"] },
  }));

  assert.deepEqual([reachOf(rows, "second"), reachOf(rows, "third")], ["peer", "peer"]);
});

test("SH1045: a binary the scripts run counts, and so do the types of something reachable", () => {
  const rows = reachability(plant({
    deps: { react: "*" },
    dev: { typescript: "*", "@types/react": "*", "@types/node": "*", "@types/gone": "*" },
    scripts: { build: "tsc -b && vite build" },
    bins: { typescript: ["tsc", "tsserver"] },
    imports: ["react"],
    node: true,
  }));

  assert.equal(reachOf(rows, "typescript"), "tooling");
  assert.equal(reachOf(rows, "@types/react"), "types");
  assert.equal(reachOf(rows, "@types/node"), "types");
  assert.equal(reachOf(rows, "@types/gone"), "unreachable",
    "SH1045: `@types/x` is reachable through x, so a types package for something nothing imports "
    + "is as dead as the thing it types");
});

test("SH1045: without a `node:` import, @types/node is not reachable either", () => {
  const rows = reachability(plant({ dev: { "@types/node": "*" }, node: false }));

  assert.equal(reachOf(rows, "@types/node"), "unreachable");
});

test("SH1045: an exemption that stops being needed fails too", () => {
  // The other direction, and the one this file would be a rubber stamp without: SH1043's budget
  // fails on a ceiling that outlives its chunk for the same reason.
  const rows = reachability(plant({ deps: { react: "*" }, imports: ["react"] }));

  assert.deepEqual(breaches(rows, { react: "kept for a reason that has expired" }),
    ["react is exempted and is now imported — delete its KEPT entry, so an excuse cannot outlive "
      + "the thing it was about"]);
  assert.deepEqual(breaches(rows, { "long-gone": "kept" }),
    ["long-gone is exempted and is no longer declared at all — delete its KEPT entry"]);
});

test("SH1045: a package reached only from CSS is reached", () => {
  // Tailwind v4 registers a theme with `@import` and a plugin with `@plugin`, so a package can
  // decide what the console looks like and appear in no `.ts` file at all. A scan that read
  // JavaScript alone would delete `tailwindcss` itself.
  const found = specifiersIn('@import "tailwindcss";\n@plugin "@tailwindcss/typography";\n'
    + '@source "../node_modules/x";');

  assert.deepEqual([...found].sort(),
    ["../node_modules/x", "@tailwindcss/typography", "tailwindcss"]);
});

test("SH1045: every import form the console writes is read, and nothing else is", () => {
  const found = specifiersIn([
    'import { a } from "pkg-from"',
    'import "pkg-bare"',
    'const b = await import("pkg-dynamic")',
    'const c = require("pkg-require")',
    'import { d } from "./local"',
    'import { e } from "node:fs"',
  ].join("\n"));

  assert.deepEqual([...found].sort(),
    ["./local", "node:fs", "pkg-bare", "pkg-dynamic", "pkg-from", "pkg-require"]);
  assert.equal(packageOf("@viglet/viglet-design-system/styles"), "@viglet/viglet-design-system",
    "a scoped name keeps two segments, or the whole scope reads as one package");
  assert.equal(packageOf("graphiql/style.css"), "graphiql");
  for (const not of ["./relative", "../up", "/absolute", "node:fs", "virtual:x", "https://a/b"]) {
    assert.equal(packageOf(not), null, `${not} names no declaration`);
  }
});

test("SH1045: every dependency this console declares is reachable", () => {
  const rows = classify(ROOT);
  // Printed on every run, which is half of what this is for: the classification is the answer to
  // "why is this here", and a reader who cannot see it has to grep for the absence of a name.
  console.log(report(rows));

  // The vacuity guard: a walk whose regexes stopped matching would classify everything as
  // unreachable, and one whose manifest read broke would classify nothing at all.
  assert.ok(rows.length >= 30, `only ${rows.length} dependencies read out of package.json`);
  assert.ok(rows.filter((row) => row.reach === "imported").length >= 20,
    "SH1045: almost nothing came back imported, so the walk is no longer finding the source");

  assert.deepEqual(breaches(rows, KEPT), [],
    "SH1045: this is the declaration half of P3 — a package nobody imports ships no bytes and is "
    + "installed, locked, audited and renovated anyway, and it tells the next reader the console "
    + "uses something it does not.");
});
