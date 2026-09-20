/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1045 — which declared dependencies the console can actually reach.
 *
 * ## Why a declaration needs the same governance as a byte
 *
 * SH1043 put a ceiling on what the console costs to load and SH1044 spent it, and both were about
 * code that ships. A dependency nobody imports ships nothing — and costs anyway: it is installed on
 * every CI run, pinned in the lockfile, audited, renovated, and read by the next person as evidence
 * that the console uses it. Seven of them had accumulated here with nothing to notice, which is the
 * same shape as the vendor chunk in SH1044: a declaration that stopped being true and had no reader.
 *
 * ## Four ways to be reachable, and only one of them is an import
 *
 * The naive check — grep `src/` for the name — deletes a package the build breaks without, which is
 * why unused-dependency tools are usually turned off after their first false positive. A package is
 * reached here when product source or tooling imports it, when something already reachable declares
 * it a **peer** (the design system needs `sonner` and `next-themes` installed beside it, and nothing
 * in `src/` imports either), when `package.json`'s own scripts run one of its **binaries**, or when
 * it is the `@types` package of something reachable.
 *
 * The peer pass runs to a fixed point over the reachable set rather than over everything declared:
 * a package that is only the peer of an unreachable package is unreachable too, or one dead entry
 * keeps the next one alive and the list never shrinks.
 *
 * ## Pure over its inputs, so the failing case can be driven
 *
 * `reachability` takes the manifest and three lookups and returns the classification. That is what
 * lets the gate plant a dependency nothing imports and watch it be named, rather than only ever
 * running against a tree that satisfies it.
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

/** Where a declaration lives, and where the code that would reach it does. */
export const MANIFEST = "package.json";

/**
 * Declarations this walk cannot reach and that stay anyway, each with the sentence that says why.
 *
 * <p>Empty, and that is the measurement rather than the default: SH1045's first run found eighteen
 * unreachable packages and every one of them was dead — the shadcn layer under `components/ui`
 * became a re-export of the design system, and its Radix, `clsx`, `tailwind-merge`,
 * `class-variance-authority` and `@tanstack/react-table` declarations outlived the code that
 * imported them. They were deleted in the same commit.</p>
 *
 * <p>An entry belongs here when a package is genuinely needed and genuinely invisible — resolved by
 * a name in a config this walk does not parse, say. It does not belong here to quiet a finding: the
 * gate below fails on a *stale* exemption as well as a new unreachable one, so an entry added for a
 * package that later becomes imported is named rather than left as a sentence nobody is measuring.</p>
 */
export const KEPT = {};

/**
 * Every directory whose files can reach a package, product and tooling alike.
 *
 * <p>Tests and the e2e probe count: a dependency only the suite imports is still reached, and
 * calling it dead would delete `jsdom` out from under sixty test files.</p>
 */
export const WALK = ["src", "test", "e2e", "scripts"];

/** Root files that import without living in a walked directory — the build's own configuration. */
export const ROOT_FILES = ["vite.config.ts", "eslint.config.js"];

const READ = /\.(?:tsx?|jsx?|mjs|cjs|css)$/u;

/**
 * Bare package specifiers in one file, from every form this console actually writes.
 *
 * <p>Four JavaScript forms and three CSS ones. The CSS at-rules are not decoration: Tailwind v4
 * registers a plugin with `@plugin` and a theme with `@import`, so a package can be essential to
 * what the console looks like and appear in no `.ts` file at all.</p>
 */
export function specifiersIn(text) {
  const found = new Set();
  const patterns = [
    /\bfrom\s*["']([^"']+)["']/gu,
    /\bimport\s*["']([^"']+)["']/gu,
    /\bimport\s*\(\s*["']([^"']+)["']/gu,
    /\brequire\s*\(\s*["']([^"']+)["']/gu,
    /@(?:import|plugin|config|source)\s+["']([^"']+)["']/gu,
  ];
  for (const pattern of patterns) {
    for (const [, spec] of text.matchAll(pattern)) found.add(spec);
  }
  return found;
}

/**
 * The package a specifier names, or null when it names no package.
 *
 * <p>Relative paths, absolute paths, `node:` builtins, urls and Vite's virtual modules all resolve
 * to something other than a declaration, and a scoped name keeps two segments where a plain one
 * keeps one — `@viglet/viglet-design-system/styles` is the design system, not a package called
 * `@viglet`.</p>
 */
export function packageOf(specifier) {
  if (/^[./]/u.test(specifier) || /^[a-z]+:/u.test(specifier)) return null;
  const parts = specifier.split("/");
  return specifier.startsWith("@") ? parts.slice(0, 2).join("/") : parts[0];
}

/** Every file that could hold a specifier, walked once. */
export function sourceFiles(root) {
  const files = [];
  for (const dir of WALK) {
    let entries;
    try {
      entries = readdirSync(join(root, dir), { recursive: true });
    } catch {
      continue; // A directory this console does not have is not a failure; `WALK` is a superset.
    }
    for (const entry of entries) {
      const path = join(root, dir, entry);
      if (READ.test(entry) && statSync(path).isFile()) files.push(path);
    }
  }
  for (const name of ROOT_FILES) {
    const path = join(root, name);
    try {
      if (statSync(path).isFile()) files.push(path);
    } catch { /* a config this console does not have */ }
  }
  return files;
}

/** Every package name the console's own files name, plus whether any of them reach into Node. */
export function importsOf(root) {
  const packages = new Set();
  let node = false;
  for (const file of sourceFiles(root)) {
    for (const spec of specifiersIn(readFileSync(file, "utf8"))) {
      if (spec.startsWith("node:")) node = true;
      const name = packageOf(spec);
      if (name) packages.add(name);
    }
  }
  return { packages, node };
}

/** One installed package's manifest, or null where nothing is installed under that name. */
export function installed(root, name) {
  try {
    return JSON.parse(readFileSync(join(root, "node_modules", name, "package.json"), "utf8"));
  } catch {
    return null;
  }
}

/** The command tokens `package.json`'s scripts run, which is how a binary-only dependency is used. */
export function scriptTokens(manifest) {
  const tokens = new Set();
  for (const command of Object.values(manifest.scripts ?? {})) {
    for (const token of command.split(/[\s|&><]+/u)) if (token) tokens.add(token);
  }
  return tokens;
}

/**
 * Every declared dependency, classified — and the whole point is the `why` beside each one.
 *
 * <p>Pure over four inputs so the gate can drive it with a planted manifest. `peersOf` and `binsOf`
 * read the installed tree in the real run and are stubs in the planted one; `imports` is the set of
 * package names the console's files name; `node` says whether anything imports a `node:` builtin,
 * which is the only thing that makes `@types/node` reachable.</p>
 *
 * @returns one row per declared dependency, sorted, each with `name`, `dev`, `reach` and `why`
 */
export function reachability({ manifest, imports, peersOf, binsOf, node = false }) {
  const declared = [
    ...Object.keys(manifest.dependencies ?? {}).map((name) => ({ name, dev: false })),
    ...Object.keys(manifest.devDependencies ?? {}).map((name) => ({ name, dev: true })),
  ].sort((a, b) => (a.name < b.name ? -1 : 1));
  const names = new Set(declared.map((one) => one.name));
  const reach = new Map();

  for (const { name } of declared) {
    if (imports.has(name)) reach.set(name, { reach: "imported", why: "imported by the console" });
  }
  byBinary(declared, reach, binsOf, scriptTokens(manifest));
  byPeer(names, reach, peersOf);
  byTypes(declared, reach, node);

  return declared.map((one) => ({
    ...one,
    ...(reach.get(one.name) ?? { reach: "unreachable", why: "nothing reaches it" }),
  }));
}

/** A package with no import that the scripts run as a command — `tsc` is `typescript`. */
function byBinary(declared, reach, binsOf, commands) {
  for (const { name } of declared) {
    if (reach.has(name)) continue;
    const bin = binsOf(name).find((one) => commands.has(one));
    if (bin) reach.set(name, { reach: "tooling", why: `package.json scripts run \`${bin}\`` });
  }
}

/**
 * A package something already reachable requires beside it, to a fixed point.
 *
 * <p>Over the reachable set and never over everything declared: a package that is only the peer of
 * an unreachable package is unreachable too, or one dead entry keeps the next one alive and the
 * list never shrinks. The snapshot is what lets the map grow while it is being read.</p>
 */
function byPeer(names, reach, peersOf) {
  for (let added = true; added;) {
    added = false;
    const holders = Array.from(reach.keys());
    for (const holder of holders) {
      for (const peer of peersOf(holder)) {
        if (!names.has(peer) || reach.has(peer)) continue;
        reach.set(peer, { reach: "peer", why: `${holder} requires it as a peer` });
        added = true;
      }
    }
  }
}

/** Last, because `@types/x` is reachable through `x`, and `x` has to have been settled first. */
function byTypes(declared, reach, node) {
  for (const { name } of declared) {
    if (reach.has(name) || !name.startsWith("@types/")) continue;
    const typed = name.slice("@types/".length);
    if (typed === "node" && node) {
      reach.set(name, { reach: "types", why: "the console imports `node:` builtins" });
    } else if (reach.has(typed)) {
      reach.set(name, { reach: "types", why: `the types of ${typed}, which is reachable` });
    }
  }
}

/** The real lookups, against an installed tree. */
export function lookups(root) {
  return {
    peersOf: (name) => Object.keys(installed(root, name)?.peerDependencies ?? {}),
    binsOf: (name) => {
      const bin = installed(root, name)?.bin;
      if (!bin) return [];
      return typeof bin === "string" ? [name.replace(/^@[^/]+\//u, "")] : Object.keys(bin);
    },
  };
}

/** The classification of a console on disk. */
export function classify(root) {
  const manifest = JSON.parse(readFileSync(join(root, MANIFEST), "utf8"));
  const { packages, node } = importsOf(root);
  return reachability({ manifest, imports: packages, node, ...lookups(root) });
}

/** The inventory, printed on every run: a number nobody sees is a number nobody argues with. */
export function report(rows) {
  const counted = new Map();
  for (const row of rows) counted.set(row.reach, (counted.get(row.reach) ?? 0) + 1);
  const lines = [`SH1045 — ${rows.length} declared dependencies: `
    + [...counted].sort((a, b) => (a[0] < b[0] ? -1 : 1))
      .map(([reach, count]) => `${count} ${reach}`).join(", ")];
  for (const row of rows) {
    lines.push(`  ${row.reach.padEnd(12)} ${row.name}${row.dev ? " (dev)" : ""} — ${row.why}`);
  }
  return lines.join("\n");
}

/**
 * Everything wrong with a classification against the exemption ledger, as sentences to act on.
 *
 * <p>Both directions, for the reason SH1043's budget fails on a ceiling that outlives its chunk: a
 * new unreachable declaration is the finding this exists for, and an exemption for a package that
 * has since become reachable is a sentence nobody is measuring any more.</p>
 */
export function breaches(rows, kept = KEPT) {
  const over = [];
  for (const row of rows) {
    if (row.reach !== "unreachable" || kept[row.name]) continue;
    over.push(`${row.name} is declared and nothing reaches it — delete it from package.json and `
      + "run `pnpm install`, or add it to KEPT with the sentence that says why it has to stay");
  }
  const classified = new Map(rows.map((row) => [row.name, row.reach]));
  for (const name of Object.keys(kept)) {
    const reach = classified.get(name);
    if (reach === "unreachable") continue;
    over.push(reach
      ? `${name} is exempted and is now ${reach} — delete its KEPT entry, so an excuse cannot `
        + "outlive the thing it was about"
      : `${name} is exempted and is no longer declared at all — delete its KEPT entry`);
  }
  return over;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const rows = classify(process.argv[2] ?? ".");
  process.stdout.write(`${report(rows)}\n`);
  const found = breaches(rows);
  for (const line of found) process.stdout.write(`  ! ${line}\n`);
  process.exit(found.length ? 1 : 0);
}
