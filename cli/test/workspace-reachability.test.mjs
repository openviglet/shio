/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import {
  PACKAGES, classifyAll, isPublished, packageBreaches, publishedBreaches, reachability,
  shippedAgreesWithFiles,
} from "../../scripts/dependency-reachability.mjs";

/**
 * SH1114 — one walk over every workspace manifest, not the console's alone.
 *
 * ## What SH1045 left unread, and why it mattered more than it looked
 *
 * SH1045 found eighteen dead declarations in `shio-react` and left ten packages unread. The reason
 * that is worse than "ten more of the same" is where they end up: **seven of the eleven packages are
 * published to npm**, so a dead dependency there is not only installed on every CI run here, it is
 * downloaded by every consumer on every install. §LXXII.20 estimated four; `private: true` is what
 * decides, and only `shio-react`, `shio-site`, `claude-plugin` and the two examples carry it.
 *
 * ## The two breaches this repository's own runs cannot see
 *
 * Nothing here is installed from a registry, which makes both of these invisible locally and fatal
 * remotely:
 *
 * - a **`dependencies` entry reached only from code npm does not pack** is a download for a file the
 *   consumer never loads — and SH1045's rule says nothing about it, because the walk *does* reach it;
 * - a **`devDependency` reached from shipped code** resolves here (the whole workspace is installed)
 *   and throws on the consumer's first import, passing every gate in this repository.
 *
 * Telling them apart needs to know *where* a package was imported from, which is what `shipped` adds
 * to the reader and what the `PACKAGES` declaration exists to say.
 *
 * ## The assertion the design names
 *
 * A dead dependency planted in a workspace package **other than the console**, failing by name. That
 * is `SH1114: a dead dependency in a package that is not the console fails by name` below, driven
 * against each of the eleven declarations rather than reasoned about once.
 */

// @scans prose reason: the one text this reads is `pnpm-workspace.yaml`'s own `packages:` list, and
// a YAML manifest is neither code the shared stripper understands nor documentation. Its comment
// character is `#`, and a commented-out entry inside that block would otherwise read as a package
// the workspace has, which is the "a sentence about the rule read as the rule" shape SH613 names,
// so `packageList` drops those lines before matching.
const REPO = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const ALL = classifyAll(REPO);

/**
 * The package globs a `packages:` block declares, with its commented-out lines dropped.
 *
 * <p>See the `@scans` note: `#` is YAML's comment character, so a disabled entry would otherwise
 * be read as a package the workspace has, and this gate would demand a `PACKAGES` declaration for
 * a directory nobody has — a failure that reads as a missing declaration rather than as this
 * reader believing a comment.</p>
 */
export function packageList(block) {
  return block.split("\n")
    .map((line) => line.trim())
    .filter((line) => line.startsWith("-"))
    .map((line) => /^-\s*"?([^"#]+?)"?\s*$/u.exec(line)?.[1])
    .filter(Boolean);
}

/** A manifest and its lookups, planted — the same fixture shape SH1045's own tests use. */
function plant({ deps = {}, dev = {}, peer = {}, scripts = {}, files = ["src"], private_ = false,
  imports = [], shipped = [] }) {
  return {
    manifest: {
      scripts,
      dependencies: deps,
      devDependencies: dev,
      peerDependencies: peer,
      files,
      ...(private_ ? { private: true } : {}),
    },
    imports: new Set(imports),
    shipped: new Set(shipped),
    peersOf: () => [],
    binsOf: () => [],
  };
}

test("SH1114: every workspace package is declared, so none is walked by nothing", () => {
  // The non-vacuity guard, and the one that would have caught SH1045's own gap: a reader that walks
  // a list of packages passes for the wrong reason the moment the workspace grows one it does not
  // name. pnpm-workspace.yaml is the authority, so the list is checked against it rather than kept.
  const yaml = readFileSync(join(REPO, "pnpm-workspace.yaml"), "utf8");
  const block = /packages:\n((?:\s*-\s*.*\n)+)/u.exec(yaml)?.[1] ?? "";
  assert.ok(block, "SH1114: pnpm-workspace.yaml's packages list moved and this scan lost its subject");

  const globs = packageList(block);
  assert.ok(globs.length >= 10, `SH1114: read only ${globs.length} workspace entries`);

  const expanded = [];
  for (const glob of globs) {
    if (!glob.endsWith("/*")) {
      expanded.push(glob);
      continue;
    }
    const parent = glob.slice(0, -2);
    for (const entry of readdirSync(join(REPO, parent))) {
      if (statSync(join(REPO, parent, entry)).isDirectory()) expanded.push(`${parent}/${entry}`);
    }
  }

  assert.deepEqual(Object.keys(PACKAGES).sort(), expanded.sort(),
    "SH1114: PACKAGES and pnpm-workspace.yaml disagree. A package in the workspace and not in "
      + "PACKAGES is a manifest nothing reads — which is exactly the state SH1045 left ten packages "
      + "in — and one in PACKAGES and not the workspace is a declaration about nothing.");
});

test("SH1114: a commented-out workspace entry is not a package", () => {
  // The scan's own subject, guarded.
  assert.deepEqual(packageList('  - "cli"\n  # - "retired-one"\n  - "shio-model"\n'),
    ["cli", "shio-model"]);
  assert.deepEqual(packageList('  - "shio-examples/*"\n'), ["shio-examples/*"]);
});

test("SH1114: a dead dependency in a package that is not the console fails by name", () => {
  // The design's own assertion. Driven against EVERY declaration rather than one, because the thing
  // that failed before was not the rule, it was that only one package was ever asked.
  for (const [name, { declared, manifest }] of Object.entries(ALL)) {
    const rows = reachability(plant({
      deps: { "a-real-one": "*", "dead-weight": "*" },
      imports: ["a-real-one"],
      shipped: ["a-real-one"],
    }));
    const dead = rows.find((row) => row.name === "dead-weight");
    assert.equal(dead.reach, "unreachable", `SH1114: ${name}'s classification lost the planted dead`);

    const found = packageBreaches(name, { declared, manifest, rows }, REPO);
    const named = found.filter((line) => line.includes("dead-weight"));
    assert.equal(named.length, 1,
      `SH1114: ${name} reported ${named.length} findings for one planted dead dependency`);
    assert.match(named[0], new RegExp(`^${name.replace("/", "\\/")}: dead-weight is declared`, "u"),
      `SH1114: the finding has to name the package as well as the dependency — with eleven of them, `
        + "\"dead-weight is declared and nothing reaches it\" does not say where to go");
  }
});

test("SH1114: the real workspace comes back clean, or names what it owes", () => {
  const found = Object.entries(ALL).flatMap(([name, one]) => packageBreaches(name, one, REPO));
  assert.deepEqual(found, [],
    `SH1114: ${found.length} finding(s) across the workspace:\n  ${found.join("\n  ")}`);

  // And the walk is real rather than empty: a declaration whose directories do not exist classifies
  // nothing and reports nothing, which reads exactly like a clean package.
  for (const [name, one] of Object.entries(ALL)) {
    const declared = Object.keys(one.manifest.dependencies ?? {}).length
      + Object.keys(one.manifest.devDependencies ?? {}).length;
    assert.equal(one.rows.length, declared,
      `SH1114: ${name} declares ${declared} dependencies and ${one.rows.length} were classified`);
  }
  const total = Object.values(ALL).reduce((sum, one) => sum + one.rows.length, 0);
  assert.ok(total >= 90,
    `SH1114: ${total} declarations classified across the workspace. SH1045 governed 43 of them in `
      + "one package; a number near that means the other ten manifests are being read as empty.");
});

test("SH1114: a dependency nothing packs is a consumer's download for a file they never load", () => {
  // Invisible in this repository by construction — it resolves, it is imported, and SH1045's rule is
  // satisfied. The only thing that distinguishes it is which directory the import came from.
  const planted = plant({
    deps: { "only-in-tests": "*" },
    imports: ["only-in-tests"],
    shipped: [],
  });
  const rows = reachability(planted);
  assert.equal(rows[0].reach, "imported",
    "SH1114: it IS imported — that is why SH1045's rule says nothing about it");
  assert.match(rows[0].why, /not packed/u,
    "SH1114: and the sentence says which side reached it, because \"imported\" alone was the gap");

  const over = publishedBreaches("a-package", planted.manifest, rows, planted.shipped);
  assert.equal(over.length, 1);
  assert.match(over[0], /`only-in-tests` is a dependency and nothing npm packs imports it/u);
  assert.match(over[0], /Move it to `devDependencies`/u, "SH1114: P6 — the finding says what to do");

  // Private packages are exempt, and not as a convenience: for a package nobody installs, the split
  // between the two lists means something else, and a finding here would be noise people learn past.
  assert.deepEqual(
    publishedBreaches("private-one", { ...planted.manifest, private: true }, rows, planted.shipped),
    []);
});

test("SH1114: a runtime import from a devDependency, except where it is a declared peer", () => {
  const planted = plant({
    dev: { "runtime-need": "*" },
    imports: ["runtime-need"],
    shipped: ["runtime-need"],
  });
  const over = publishedBreaches("a-package", planted.manifest, reachability(planted),
    planted.shipped);
  assert.equal(over.length, 1);
  assert.match(over[0], /is a devDependency and shipped code imports it/u);
  assert.match(over[0], /throws on a consumer's first import/u);

  // The false positive this rule had waiting, and the reason it is worth leaving on. A React library
  // declares `react` as a PEER — the consumer supplies it — and as a devDependency so it is
  // installed here to build and test against. The first run of this rule reported both of ours,
  // `shio-sections` and `shio-react-sdk`, as breaches. SH1045's own note is that an
  // unused-dependency check is turned off after its first false positive.
  const asPeer = plant({
    dev: { react: "*" },
    peer: { react: ">=18.0.0" },
    imports: ["react"],
    shipped: ["react"],
  });
  assert.deepEqual(
    publishedBreaches("a-library", asPeer.manifest, reachability(asPeer), asPeer.shipped), [],
    "SH1114: a peer that is also a devDependency is the correct shape, not a breach");

  // And the two of ours that shipped this exemption are still in that shape, so the exemption
  // cannot outlive its reason without this going red.
  for (const name of ["shio-sections", "shio-react-sdk"]) {
    const { manifest } = ALL[name];
    assert.ok(manifest.peerDependencies?.react,
      `SH1114: ${name} no longer declares react a peer — if it moved to dependencies the exemption `
        + "above is now covering a real breach somewhere else");
    assert.ok(manifest.devDependencies?.react,
      `SH1114: ${name} no longer devDepends on react, so nothing here tests the exempted shape`);
  }
});

test("SH1114: a shipped declaration cannot drift from what npm packs", () => {
  const entry = { walk: ["src", "test"], root: [], shipped: ["src"] };

  assert.deepEqual(shippedAgreesWithFiles("ok", { files: ["src"] }, entry, REPO), []);
  assert.deepEqual(shippedAgreesWithFiles("built", { files: ["dist"] }, entry, REPO), [],
    "SH1114: dist is build output and no walk reads it — `shipped` stands behind it");

  // The drift that makes the two rules above lie: a package that starts packing its tests, while
  // this file goes on calling a test-only dependency test-only.
  const packsTests = shippedAgreesWithFiles("drifted", { files: ["src", "test"] }, entry, REPO);
  assert.equal(packsTests.length, 1);
  assert.match(packsTests[0], /packs `test` and its PACKAGES entry does not call it shipped/u);

  assert.match(shippedAgreesWithFiles("nofiles", {}, entry, REPO)[0], /declares no `files`/u,
    "SH1114: no `files` means npm packs everything, tests included");
  assert.deepEqual(shippedAgreesWithFiles("private", { private: true }, entry, REPO), [],
    "SH1114: a private package packs nothing, so it owes nothing here");

  // `create-shio-app` packs `template/`, which has a package.json of its own: its imports resolve
  // against THAT manifest once a consumer scaffolds with it, never against this one. A payload, not
  // unread source — and this is the one exemption that needs the real tree to be checked.
  assert.deepEqual(
    shippedAgreesWithFiles("create-shio-app", ALL["create-shio-app"].manifest,
      PACKAGES["create-shio-app"], REPO),
    [],
    "SH1114: a packed directory carrying its own manifest is a payload");
  assert.ok(statSync(join(REPO, "create-shio-app", "template", "package.json")).isFile(),
    "SH1114: the template's own manifest is what makes it a payload; without it the exemption is "
      + "covering unread source");
});

test("SH1114: every published package is walked, and the count is asserted not remembered", () => {
  const published = Object.entries(ALL).filter(([, one]) => isPublished(one.manifest));
  const names = published.map(([name]) => name).sort();

  assert.deepEqual(names, ["cli", "create-shio-app", "shio-client", "shio-js-sdk", "shio-model",
    "shio-react-sdk", "shio-sections"],
    "SH1114: which packages are published decides where these rules apply, so a change to it is a "
      + "change to this gate's reach. §LXXII.20 said four; seven is what `private: true` leaves.");

  for (const [name, one] of published) {
    assert.ok(PACKAGES[name].shipped.length,
      `SH1114: ${name} is published and declares nothing shipped, so every dependency it has reads `
        + "as test-only and both published rules check nothing for it");
    assert.ok(one.manifest.files?.length,
      `SH1114: ${name} is published and declares no \`files\`, so npm packs its tests`);
  }
});
