/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1045, SH1114 — which declared dependencies each workspace package can actually reach.
 *
 * ## Lifted out of the console, because ten manifests were unread
 *
 * SH1045 wrote this against `shio-react` and found eighteen dead declarations. It left ten packages
 * unread, and **seven of those are published to npm** — where a dead dependency is not merely
 * installed on every CI run here, it is downloaded by every consumer. (§LXXII.20 estimated four;
 * the count is what it is because `private: true` is what decides, and only `shio-react`,
 * `shio-site`, `claude-plugin` and the two examples carry it.)
 *
 * The reader was written to be lifted: `reachability` was already pure over its inputs, and the only
 * console-specific parts were two constants naming directories. Those are now {@link PACKAGES}, one
 * declaration per package, so this is one walk over eleven manifests rather than a second walk.
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
import { dirname, join } from "node:path";
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
 * One declaration per workspace package: where its code lives, and which of that a consumer runs.
 *
 * <p>`walk` is every directory whose files can reach a package, product and tooling alike — tests
 * count, because a dependency only the suite imports is still reached and calling it dead would
 * delete `jsdom` out from under sixty test files. `root` names the files that import without living
 * in a walked directory, which is the build's own configuration.</p>
 *
 * <p>`shipped` is the half SH1114 added, and it is the one that only matters for a published
 * package: <b>which of the walked directories a consumer's install actually runs.</b> Without it,
 * two breaches are invisible here and break somewhere else — a `dependencies` entry imported only
 * from `test/` is a download for a file the consumer never loads, and a runtime import resolved from
 * a `devDependency` passes every gate in this repository and throws on `require` after `npm i`.</p>
 *
 * <p>It is a declaration rather than a derivation because `files` names build output for half these
 * packages: `shio-model` ships `dist/`, which is bundled and not walked, and the source that
 * produces it is `src/`. So `shipped` says which <em>walked</em> directory stands behind what npm
 * packs, and {@link shippedAgreesWithFiles} checks the declaration against the manifest's own
 * `files` so the two cannot drift apart silently.</p>
 */
export const PACKAGES = {
  cli: { walk: ["src", "test", "scripts", "conformance"], root: [], shipped: ["src"] },
  "shio-client": { walk: ["src", "test", "bin"], root: ["vite.config.ts"], shipped: ["src", "bin"] },
  "shio-model": { walk: ["src", "test"], root: ["vite.config.ts"], shipped: ["src"] },
  "shio-sections": {
    walk: ["src", "test", "scripts", "front-end"], root: ["vite.config.ts"], shipped: ["src"],
  },
  "shio-react-sdk": { walk: ["src"], root: ["vite.config.ts"], shipped: ["src"] },
  "shio-js-sdk": { walk: ["src"], root: ["vite.config.ts"], shipped: ["src"] },
  "create-shio-app": {
    walk: ["src", "test", "scripts", "overlays"], root: [], shipped: ["src", "overlays"],
  },
  "claude-plugin": { walk: ["test", "commands", "skills"], root: [], shipped: [] },
  "shio-react": {
    walk: ["src", "test", "e2e", "scripts"],
    root: ["vite.config.ts", "eslint.config.js"],
    shipped: [],
  },
  "shio-site": { walk: ["src", "scripts"], root: ["vite.config.ts"], shipped: [] },
  // SH1262 — the /docs area: Astro and Starlight are reached from its config and content config.
  "shio-site/docs": { walk: ["src", "scripts"], root: ["astro.config.mjs"], shipped: [] },
  "shio-examples/blog-site": { walk: ["src"], root: ["vite.config.ts"], shipped: [] },
  "shio-examples/corporate-site": { walk: ["src"], root: ["vite.config.ts"], shipped: [] },
};

/** The console's own layout, kept as names because SH1045's tests and docs use them. */
export const WALK = PACKAGES["shio-react"].walk;
export const ROOT_FILES = PACKAGES["shio-react"].root;

/**
 * A package is published unless it says otherwise — which is npm's rule, not a list kept here.
 *
 * <p>A list would be the thing that goes stale on the day a package stops being private, and that
 * day is exactly when these two rules start mattering.</p>
 */
export function isPublished(manifest) {
  return manifest.private !== true;
}

/** Build output `files` may name that no walk reaches, so `shipped` stands behind it instead. */
const BUILT = ["dist"];

/** A packed directory that declares its own dependencies resolves them against its own manifest. */
function carriesOwnManifest(dir) {
  try {
    return statSync(join(dir, MANIFEST)).isFile();
  } catch {
    return false;
  }
}

/**
 * Whether a package's `shipped` declaration still agrees with what npm would pack.
 *
 * <p>The drift this refuses is a package that starts shipping a directory nobody declared — `files`
 * gaining `test` would make a test-only dependency a consumer's problem while this file went on
 * calling it test-only. A `files` entry that is build output is satisfied by any `shipped` directory,
 * since that is the source the output is built from.</p>
 *
 * @returns the sentences to act on, empty when the two agree
 */
export function shippedAgreesWithFiles(name, manifest, declared, repo = ".") {
  if (!isPublished(manifest)) return [];
  const files = manifest.files ?? [];
  const problems = [];
  if (!files.length) {
    problems.push(`${name} is published and declares no \`files\`, so npm packs the whole `
      + "directory — including `test`. Either declare `files`, or make it private.");
  }
  if (!declared.shipped.length) {
    problems.push(`${name} is published and its PACKAGES entry declares nothing shipped, so every `
      + "dependency it has reads as test-only and the two published-package rules below check "
      + "nothing for it.");
  }
  for (const entry of files) {
    const top = entry.replace(/^\.?\//u, "").split("/")[0];
    if (BUILT.includes(top) || declared.shipped.includes(top)) continue;
    if (carriesOwnManifest(join(repo, name, top))) {
      // `create-shio-app` packs `template/`, which is the scaffold it writes into a new project and
      // has a `package.json` of its own. Its imports resolve against *that* manifest once a
      // consumer runs it, never against this one, so it is a payload rather than unread source.
      continue;
    }
    if (declared.walk.includes(top)) {
      problems.push(`${name} packs \`${top}\` and its PACKAGES entry does not call it shipped — a `
        + "dependency reached only from there would be reported as test-only while a consumer runs "
        + `it. Add \`${top}\` to \`shipped\`.`);
    } else {
      problems.push(`${name} packs \`${top}\`, which no walk reads. Either it is build output and `
        + `belongs in BUILT, or code that reaches dependencies is shipping unread.`);
    }
  }
  return problems;
}

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

/**
 * Every file that could hold a specifier, walked once, each tagged with whether a consumer runs it.
 *
 * @param root the package directory
 * @param declared its {@link PACKAGES} entry; the console's own by default, for SH1045's callers
 * @returns `{ path, shipped }` per file — `shipped` is what the two published-package rules turn on
 */
export function sourceFiles(root, declared = PACKAGES["shio-react"]) {
  const files = [];
  for (const dir of declared.walk) {
    let entries;
    try {
      entries = readdirSync(join(root, dir), { recursive: true });
    } catch {
      continue; // A directory a package does not have is not a failure; `walk` may be a superset.
    }
    const shipped = declared.shipped.includes(dir);
    for (const entry of entries) {
      const path = join(root, dir, entry);
      if (READ.test(entry) && statSync(path).isFile()) files.push({ path, shipped });
    }
  }
  for (const name of declared.root) {
    const path = join(root, name);
    try {
      // A build's own configuration is never shipped: it runs here and is not in `files`.
      if (statSync(path).isFile()) files.push({ path, shipped: false });
    } catch { /* a config this package does not have */ }
  }
  return files;
}

/**
 * Every package name a package's own files name, split by whether a consumer's install runs them.
 *
 * <p>`packages` is every name reached from anywhere, which is what decides reachability and is all
 * SH1045 needed. `shipped` is the subset reached from code npm packs, and it is what SH1114's two
 * rules compare against the list the entry was declared in.</p>
 */
export function importsOf(root, declared = PACKAGES["shio-react"]) {
  const packages = new Set();
  const shipped = new Set();
  let node = false;
  for (const file of sourceFiles(root, declared)) {
    for (const spec of specifiersIn(readFileSync(file.path, "utf8"))) {
      if (spec.startsWith("node:")) node = true;
      const name = packageOf(spec);
      if (!name) continue;
      packages.add(name);
      if (file.shipped) shipped.add(name);
    }
  }
  return { packages, shipped, node };
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
export function reachability({ manifest, imports, shipped = null, peersOf, binsOf, node = false }) {
  const declared = [
    ...Object.keys(manifest.dependencies ?? {}).map((name) => ({ name, dev: false })),
    ...Object.keys(manifest.devDependencies ?? {}).map((name) => ({ name, dev: true })),
  ].sort((a, b) => (a.name < b.name ? -1 : 1));
  const names = new Set(declared.map((one) => one.name));
  const reach = new Map();

  for (const { name } of declared) {
    if (!imports.has(name)) continue;
    // SH1114 — which side imported it is the whole of the published-package rules, so it is in the
    // sentence too: "imported" on its own was what let a test-only dependency read as fine.
    const where = shipped == null ? "" : shipped.has(name) ? " (shipped code)" : " (not packed)";
    reach.set(name, { reach: "imported", why: `imported${where}` });
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

/** The classification of one package on disk. */
export function classify(root, declared = PACKAGES["shio-react"]) {
  const manifest = JSON.parse(readFileSync(join(root, MANIFEST), "utf8"));
  const { packages, shipped, node } = importsOf(root, declared);
  return reachability({ manifest, imports: packages, shipped, node, ...lookups(root) });
}

/**
 * Every workspace package's classification, keyed by its directory.
 *
 * @param repo the repository root
 * @param only one package name, to classify just that
 */
export function classifyAll(repo, only = null) {
  const out = {};
  for (const [name, declared] of Object.entries(PACKAGES)) {
    if (only && name !== only) continue;
    const root = join(repo, name);
    out[name] = {
      declared,
      manifest: JSON.parse(readFileSync(join(root, MANIFEST), "utf8")),
      rows: classify(root, declared),
    };
  }
  return out;
}

/**
 * SH1114 — the two breaches only a published package can commit, and only this split can see.
 *
 * <p>Neither is visible in this repository's own runs, which is the whole reason they went unasserted
 * while eighteen dead declarations in the console were found and fixed. Nothing here is installed
 * from a registry, so:</p>
 *
 * <ul>
 *   <li>A <b>`dependencies` entry reached only from code npm does not pack</b> is a download on
 *   every consumer's install for a file they will never load. It is not dead — this walk reaches it
 *   — so SH1045's rule says nothing about it.</li>
 *   <li>A <b>`devDependency` reached from code npm does pack</b> is worse and quieter: it resolves
 *   here because the whole workspace is installed, and throws on the consumer's first import. Every
 *   gate in this repository passes.</li>
 * </ul>
 *
 * <p>Both are reported only for a published package, because for a private one the lists mean
 * something else entirely and a finding would be noise people learn to ignore.</p>
 */
export function publishedBreaches(name, manifest, rows, shippedImports) {
  if (!isPublished(manifest)) return [];
  /*
   * A peer is the correct home for a dependency the consumer supplies, and it is declared `dev` as
   * well so it is installed here to build and test against. That pair is the standard shape of a
   * React library and the first run of this rule reported both of ours — `shio-sections` and
   * `shio-react-sdk`, each with `react` as a peer and a devDependency — as breaches. They are not.
   * SH1045's own note says an unused-dependency check is turned off after its first false positive;
   * this rule had one waiting, and the exemption is why it is worth leaving on.
   */
  const peers = new Set(Object.keys(manifest.peerDependencies ?? {}));
  const over = [];
  for (const row of rows) {
    if (row.reach !== "imported") continue;
    const runsForConsumer = shippedImports.has(row.name);
    if (!row.dev && !runsForConsumer) {
      over.push(`${name}: \`${row.name}\` is a dependency and nothing npm packs imports it — a `
        + "consumer downloads it on every install for a file they never load. Move it to "
        + "`devDependencies`, or ship the code that uses it.");
    }
    if (row.dev && runsForConsumer && !peers.has(row.name)) {
      over.push(`${name}: \`${row.name}\` is a devDependency and shipped code imports it, so it `
        + "resolves here (the whole workspace is installed) and throws on a consumer's first "
        + "import. Move it to `dependencies`, or declare it a peer if the consumer supplies it.");
    }
  }
  return over;
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

/**
 * Everything one package owes: the dead declarations, the two published-package breaches, and a
 * `shipped` declaration that has drifted from what npm packs.
 */
export function packageBreaches(name, { declared, manifest, rows }, repo) {
  const { shipped } = importsOf(join(repo, name), declared);
  return [
    ...breaches(rows).map((line) => `${name}: ${line}`),
    ...publishedBreaches(name, manifest, rows, shipped),
    ...shippedAgreesWithFiles(name, manifest, declared, repo),
  ];
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  // `--all` walks the workspace; a path keeps SH1045's one-package invocation working.
  const arg = process.argv[2] ?? "--all";
  if (arg === "--all" || PACKAGES[arg]) {
    const repo = join(dirname(process.argv[1]), "..");
    const all = classifyAll(repo, arg === "--all" ? null : arg);
    let found = [];
    for (const [name, one] of Object.entries(all)) {
      const published = isPublished(one.manifest) ? "published" : "private";
      process.stdout.write(`\n== ${name} (${published}) ==\n${report(one.rows)}\n`);
      found = found.concat(packageBreaches(name, one, repo));
    }
    process.stdout.write(`\nSH1114 — ${Object.keys(all).length} package(s) walked, `
      + `${found.length} finding(s)\n`);
    for (const line of found) process.stdout.write(`  ! ${line}\n`);
    process.exit(found.length ? 1 : 0);
  }
  const rows = classify(arg);
  process.stdout.write(`${report(rows)}\n`);
  const found = breaches(rows);
  for (const line of found) process.stdout.write(`  ! ${line}\n`);
  process.exit(found.length ? 1 : 0);
}
