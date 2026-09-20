/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1043 — what the console costs to load, before the first row of content.
 *
 * ## Why this exists in a project that already counts tokens
 *
 * The agent's cost is governed to the endpoint: `token-budgets.properties` holds a ceiling per
 * response, a raise carries its reason, and P3 makes a regression a build failure. The half of the
 * product a *person* waits for had no number at all — the three gates are the Maven suite, the JS
 * suite and conformance, and none of them looks at a byte. That asymmetry is how a 4.7 MB vendor
 * chunk came to be preloaded on every route with nobody noticing (SH1044).
 *
 * ## It reads the bundler's own answer rather than re-deriving it
 *
 * Which chunks are *eager* is a question about the static import graph, and Vite has already
 * answered it: the entry is the `<script type="module">` and every statically-imported chunk gets a
 * `<link rel="modulepreload">` in the emitted HTML. Parsing the chunks' own `import` statements
 * would be a second implementation of that analysis, disagreeing with the first on the day a
 * bundler option changes. So this reads `index.html` — which is also exactly what the browser reads.
 *
 * A build whose HTML declares no preloads would make this report a fraction of the truth and call
 * it a pass, so the reader refuses that shape rather than measuring it: see `eagerOf`.
 */
import { createReadStream } from "node:fs";
import { readFile, stat } from "node:fs/promises";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { createGzip } from "node:zlib";

/** Where `vite build` puts the console: the Spring Boot static root it is served from. */
export const DIST = join("..", "shio-app", "src", "main", "resources", "public");

/** The committed seed and the ceilings this compares against. */
export const BUDGET_FILE = "console-size-budget.json";

const ENTRY = /<script[^>]*type="module"[^>]*src="([^"]+)"/g;
const PRELOAD = /<link[^>]*rel="modulepreload"[^>]*href="([^"]+)"/g;
const STYLE = /<link[^>]*rel="stylesheet"[^>]*href="([^"]+)"/g;

/**
 * Every asset the browser fetches before it can render, from the emitted HTML.
 *
 * <p>Three kinds and all of them blocking in the sense that matters: the entry module, the chunks
 * it statically imports (which the browser is told to preload precisely because it will need them),
 * and the stylesheets, which block paint. A lazily imported route chunk appears in none of the
 * three, which is the distinction the whole budget turns on.</p>
 *
 * @throws Error when the HTML declares no entry or no preload — a build with module preloading
 *     turned off would otherwise report the entry alone and pass every ceiling forever, which is
 *     the report-zero shape every reader in this repository is written against
 */
export function eagerOf(html) {
  const entries = [...html.matchAll(ENTRY)].map(([, href]) => href);
  const preloads = [...html.matchAll(PRELOAD)].map(([, href]) => href);
  const styles = [...html.matchAll(STYLE)].map(([, href]) => href);
  if (!entries.length) {
    throw new Error("console-size: no <script type=\"module\"> in the built index.html — either "
      + "this is not a Vite build or the entry stopped being a module, and measuring what is left "
      + "would report a fraction of the load and call it a pass.");
  }
  if (!preloads.length) {
    throw new Error("console-size: the built index.html declares no <link rel=\"modulepreload\">. "
      + "Vite emits one per statically imported chunk, so this reader depends on them to know what "
      + "is eager; without them it would measure the entry alone. If `build.modulePreload` was "
      + "turned off deliberately, this reader has to walk the import graph itself instead.");
  }
  return [...new Set([...entries, ...preloads, ...styles])].sort((a, b) => (a < b ? -1 : 1));
}

/** Gzipped size, streamed — the number a reader on a real connection actually pays. */
export async function gzipSize(path) {
  return new Promise((resolve, reject) => {
    let total = 0;
    createReadStream(path)
      .pipe(createGzip({ level: 9 }))
      .on("data", (chunk) => { total += chunk.length; })
      .on("end", () => resolve(total))
      .on("error", reject);
  });
}

/**
 * The eager load of a built console: one row per asset, and the totals.
 *
 * <p>Raw and gzip both, because they answer different questions and a ceiling on one alone is
 * gameable by the other: gzip is what crosses the wire, raw is what the browser then parses and
 * compiles, and a megabyte of minified JavaScript costs a phone time whatever it compressed to.</p>
 */
export async function measure(dist) {
  const html = await readFile(join(dist, "index.html"), "utf8");
  const assets = [];
  for (const href of eagerOf(html)) {
    const path = join(dist, href.replace(/^\//, ""));
    const raw = (await stat(path)).size;
    assets.push({ asset: href, raw, gzip: await gzipSize(path) });
  }
  return {
    assets,
    raw: assets.reduce((sum, one) => sum + one.raw, 0),
    gzip: assets.reduce((sum, one) => sum + one.gzip, 0),
  };
}

/**
 * The bundler's content hash, taken off the front of the name (SH1043).
 *
 * <p>A ceiling keyed by `index-o5ddno1A.js` would be stale on the next edit, which is a budget
 * nobody can keep. The stem is what stays: `index.js`, `vendor-lib.js`, and so on.</p>
 */
export function stemOf(asset) {
  let name = asset.replace(/^.*\//, "");
  // Until it stops changing, because a chunk can carry MORE THAN ONE hash: a package that ships
  // pre-hashed files gets re-hashed on the way through, so the design system arrives as
  // `breadcrumb.context-BssgEydx-D1t6Pnxg.js` and stripping once would leave a ceiling keyed by
  // `breadcrumb.context-BssgEydx.js` — still stale on the next release of somebody else's package,
  // which is the stalest kind.
  for (;;) {
    const hashed = /^(.+)-[A-Za-z0-9_-]{8}\.(js|css)$/.exec(name);
    if (!hashed) return name;
    name = `${hashed[1]}.${hashed[2]}`;
  }
}

/**
 * Everything wrong with a measured build against a budget, as sentences a reader can act on.
 *
 * <p>A pure function of the two, so the gate can be driven by a planted build rather than only by
 * whatever happens to be on disk — which is the difference between a check that has been shown to
 * fail and one that has only ever been run against something that satisfies it.</p>
 *
 * <p>Four kinds of finding, and the last two are the ones a plain total would miss: a chunk the
 * budget does not name (so a new eager chunk cannot arrive unpriced), and an entry that is no
 * longer eager (so a ceiling cannot outlive the thing it was about).</p>
 */
export function breaches(measured, budget) {
  const over = [];
  for (const one of measured.assets) {
    const stem = stemOf(one.asset);
    const entry = budget.chunks?.[stem];
    if (!entry) {
      over.push(`${stem} is eager and the budget does not name it — add it with its measurement `
        + `(${kb(one.raw)} raw, ${kb(one.gzip)} gzip) and a sentence saying what it is`);
      continue;
    }
    if (one.raw > entry.raw) over.push(`${stem} raw ${kb(one.raw)} > ${kb(entry.raw)}`);
    if (one.gzip > entry.gzip) over.push(`${stem} gzip ${kb(one.gzip)} > ${kb(entry.gzip)}`);
  }
  const eager = new Set(measured.assets.map((one) => stemOf(one.asset)));
  for (const stem of Object.keys(budget.chunks ?? {})) {
    if (!eager.has(stem)) {
      over.push(`${stem} is in the budget and is no longer eager — delete its entry in the same `
        + "commit, so a ceiling cannot outlive the thing it was about");
    }
  }
  if (measured.raw > budget.eager.raw) {
    over.push(`eager raw ${kb(measured.raw)} > ${kb(budget.eager.raw)}`);
  }
  if (measured.gzip > budget.eager.gzip) {
    over.push(`eager gzip ${kb(measured.gzip)} > ${kb(budget.eager.gzip)}`);
  }
  return over;
}

/** Kilobytes, one decimal — the unit a reader compares, not the unit a test asserts. */
export function kb(bytes) {
  return `${(bytes / 1024).toFixed(1)} KB`;
}

/** The report, printed by the gate on every run and by this script when run directly. */
export function report(measured) {
  const lines = [`SH1043 — console eager load: ${kb(measured.raw)} raw, ${kb(measured.gzip)} gzip`];
  for (const one of [...measured.assets].sort((a, b) => b.raw - a.raw)) {
    lines.push(`  ${kb(one.raw).padStart(10)} ${kb(one.gzip).padStart(10)}  ${one.asset}`);
  }
  return lines.join("\n");
}

// `pathToFileURL` rather than a string built by hand: on Windows the argv path is `D:\...` and the
// module url is `file:///D:/...`, so every hand-rolled comparison of the two is off by a slash and
// the script silently does nothing when run directly.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const dist = process.argv[2] ?? DIST;
  // Said rather than thrown: run directly, this is a measurement somebody asked for, and "there is
  // no build here" is the answer to give them rather than a stack trace.
  const measured = await measure(dist).catch((problem) => {
    process.stdout.write(`${problem.message}\n`);
    process.exit(1);
  });
  process.stdout.write(`${report(measured)}\n`);
}
