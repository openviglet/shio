/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1118 — the instrument, which is the part that finds the copy nobody read.
 *
 * ## What it looks for, and why a grep is the right shape
 *
 * `source.replace(/\/\*[\s\S]*?\*\//g, "")` reads as "strip the block comments" and is not that. It
 * opens on a slash and two stars **anywhere** — string literals included — and closes on the next
 * star-slash, hundreds of lines down or at the end of the file. Everything between is deleted from
 * the scanned text, so every assertion over that region matches an empty string and **passes**.
 *
 * A silenced scan is invisible by construction: it is green. That is why this defect survived three
 * sightings of its own symptom (SH823, SH890, SH888 each found a scan reading its own prose, fixed
 * it by reaching for the strip, and never asked what the strip removed) and why SH1049 measured it
 * only by accident. So the question this file asks is not "is the shared helper right" — that is
 * `shio-react/test/source-text.test.mjs` — but **how many readers are asking the wrong question at
 * all**, which nothing could answer before, because an absence cannot be grepped from inside a
 * suite that the absence has silenced.
 *
 * ## The measurement, taken before any fix
 *
 * Across this repository's JS sources: **19 spans, 77,285 characters** removed by a strip that began
 * in the middle of a line. `cli/test/clone.test.mjs` hid **656 lines** behind a `robots.txt`
 * fixture's `Disallow: /*`; `cli/test/theme.test.mjs` 508 behind a CSS selector; the console's
 * `src/app/routes.const.ts` lost its route table to `` `${consolePath}/*` ``.
 *
 * And **ten** call sites wrote those two lines, not one. `cli/src/theme.mjs` already carries a
 * comment explaining in as many words why the pattern is wrong, and `cli/test/support/source-text.mjs`
 * already exports the safe line-oriented reading (SH740, SH630). The knowledge was in the tree and
 * nine readers did not have it — which is the general shape worth keeping: **a fix reported in the
 * singular usually was one.**
 *
 * ## Why the remainder is a named list and not a ceiling
 *
 * SH1118 fixed the shared helper and the three console scanners that inlined it. The six below need
 * one shared reading per package and then a migration with its own red risk per scan — the safe
 * reading keeps a trailing comment where the naive strip removed it, and a scan asserting an absence
 * can notice. A ceiling would let one task pay that down and the next spend it again, so this is a
 * list: it only goes down, and a seventh copy is red on the day it is written.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { code, uncommented } from "./support/source-text.mjs";

// @scans code
//
// The one scan in this repository whose subject is the strippers themselves, so it has to be held
// to the rule it enforces. It reads through `code()` — the shared line-oriented reading (SH740,
// SH630) — which is also why the pattern can be quoted in the prose above without this file finding
// itself: a whole-line comment is gone before the match runs.

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/** The trees that are this repository's own JavaScript, and none that are somebody else's. */
const TREES = [
  "cli", "scripts", "shio-react/src", "shio-react/test", "shio-react/e2e", "shio-react/scripts",
  "shio-client/src", "shio-model/src", "shio-sections/src", "shio-site/src",
  "claude-plugin", "shio-editor-cors/src", "nextjs-starter/src",
];

const READABLE = new Set([".mjs", ".js", ".ts", ".tsx"]);

/**
 * A cross-line block-comment strip, however it is spelled.
 *
 * <p>Written to survive the spellings actually found rather than one of them: `[\s\S]` or `[^]`, a
 * `u` or `g` flag in either order, `""` or `" "` as the replacement, and `.replace(` on the same
 * line or the next. What every copy shares is the non-greedy any-character run between an escaped
 * opener and an escaped closer, and that is what this matches.</p>
 */
const NAIVE = /\/\\\/\\\*(?:\[\\s\\S\]|\[\^\])\*\?\\\*\\\//;

/** The id whose job is to empty the list below. */
const OWNER = "SH1147";

/**
 * Every naive strip still in the tree, with what it costs where it is.
 *
 * <p>Each entry is a path. A copy that is removed comes off this list; a copy that appears and is
 * not on it fails the test on the day it is written. Nothing is added here to make a run green —
 * the whole point of a list over a count is that adding to it is a visible act.</p>
 */
// Empty since SH1147: the five CLI scans read through `uncommented`, and the console's surface
// digest has a per-line reading of its own (a build script does not import test support) whose
// output was byte-identical before and after. Kept as a declaration so a new copy has somewhere
// visible to be added, with its reason, rather than a count to drift.
const DECLARED = {};

function walk(dir) {
  const out = [];
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries) {
    if (entry === "node_modules" || entry === "dist" || entry === "target") continue;
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) out.push(...walk(path));
    else if (READABLE.has(entry.slice(entry.lastIndexOf(".")))) out.push(path);
  }
  return out;
}

/** Every file that spells a cross-line block-comment strip, as repository-relative paths. */
function naiveStrippers() {
  const found = [];
  for (const tree of TREES) {
    for (const path of walk(join(ROOT, tree))) {
      // Through the shared reading, because the pattern quoted inside a comment is prose about the
      // rule and not the rule — the SH613 defect this very file would otherwise commit while
      // describing it. `code()` drops whole-line comments, so the header above is invisible here.
      const body = code(readFileSync(path, "utf8"));
      if (!NAIVE.test(body)) continue;
      if (body.split("\n").some((line) => NAIVE.test(line) && line.includes(".replace(")))
        found.push(relative(ROOT, path).replace(/\\/g, "/"));
    }
  }
  return found.sort();
}

test("SH1118: every cross-line comment strip is either gone or on the declared list", () => {
  const found = naiveStrippers();
  const declared = Object.keys(DECLARED).sort();
  const undeclared = found.filter((path) => !(path in DECLARED));
  const gone = declared.filter((path) => !found.includes(path));

  // Printed on every run, which is what makes the number a fact somebody sees rather than one this
  // file holds. Ten before SH1118; the list exists to reach zero.
  process.stdout.write(
    `\nSH1118: ${found.length} naive comment stripper(s) in ${TREES.length} tree(s); `
    + `${declared.length} declared under ${OWNER}, ${gone.length} already removed.\n`,
  );

  assert.deepEqual(undeclared, [],
    "SH1118: a cross-line block-comment strip that is on no list. `source.replace(/\\/\\*[\\s\\S]*?"
    + "\\*\\//g, \"\")` opens on a `/*` inside a string literal and deletes everything to the next "
    + "closer — measured at 19 spans and 77,285 characters across this repository, the worst hiding "
    + "656 lines behind a `robots.txt` fixture. Every assertion over the deleted region then matches "
    + "an empty string and passes, so the scan you just wrote may be guarding nothing. Use the safe "
    + `reading beside it (\`cli/test/support/source-text.mjs\` or the console's), or add it to ${OWNER}`
    + "'s list with a sentence saying why it cannot yet.");

  assert.deepEqual(gone, [],
    `SH1118: a path declared under ${OWNER} no longer has a naive strip, so the list is claiming a `
    + "debt that has been paid. Delete the entry — a list that outlives its findings is how a count "
    + "stops meaning anything, and this one exists precisely so the number cannot drift.");
});

test("SH1147: the shared reading loses nothing behind a comment opener inside a string", () => {
  // The fixture that hid 656 lines of clone.test.mjs, in miniature: a string holding `/*`, then code
  // that a naive strip would have deleted up to the next closer.
  const source = [
    'const robots = "Disallow: /*"',
    "const kept = true",
    "// a whole-line comment",
    "const url = \"https://example.test//x\" // a trailing comment",
    "const re = /\\/\\//",
    "/* a closer, far below */",
  ].join("\n");
  const read = uncommented(source);

  assert.match(read, /const kept = true/,
    "SH1147: the line after a string holding `/*` is still read — the naive strip deleted it");
  assert.match(read, /const url = "https:\/\/example\.test\/\/x"$/m,
    "SH1147: a `//` inside a string is kept, and the trailing comment after it is cut");
  assert.doesNotMatch(read, /a whole-line comment|a trailing comment|a closer/,
    "SH1147: comments are still removed, which is the job the naive strip was doing");
  assert.match(read, /const re = \/\\\/\\\/\//, "SH1147: a regex literal's escaped slashes survive");
});

test("SH1118: the shared readings the list points at are themselves safe", () => {
  // The redirection has to be real, or the failure message above is advice to nowhere. Both halves
  // are asserted because SH823 kept the two copies apart on purpose, and a bug in one is exactly
  // what that decision costs.
  for (const support of ["cli/test/support/source-text.mjs", "shio-react/test/support/source-text.mjs"]) {
    const body = code(readFileSync(join(ROOT, support), "utf8"));
    assert.equal(NAIVE.test(body), false,
      `SH1118: ${support} is what the message above sends a reader to, and it spells the very `
      + "pattern it is supposed to replace. That is not a worse version of the defect, it is the "
      + "defect at the one place that cannot have it.");
  }
});
