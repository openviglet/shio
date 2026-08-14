/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH740 — every JS suite that scans a file for a rule says what it reads.
 *
 * <p>SH613's argument, one language across. A scan over raw text cannot tell a rule from a sentence
 * about the rule, and neither can a reader — so the scan declares which half it means, and the
 * declaration is checked rather than believed. What is NOT enforced is a single stripping rule:
 * some of these read prose on purpose (a checklist, a published page, the governed docs) and some
 * read it by accident, and from outside those look identical. That is why this asks for a
 * declaration instead of imposing a rule, exactly as `ShSourceScanLintTest` does.</p>
 *
 * <p>The population is printed on every run rather than written down here: a number in a comment is
 * a measurement that has already started rotting.</p>
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { describe, test } from "node:test";

import { code, unquoted } from "./support/source-text.mjs";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/** The trees that hold suites able to scan something. */
const TREES = ["cli/test", "cli/conformance", "shio-site/scripts"];

/** Reading a file at all — deliberately the family, not the one spelling in use today. */
const READS_A_FILE = /\b(readFileSync|readFile|readdirSync)\b/;

/**
 * ...and the read thing being source or documentation, which is the population this is about.
 *
 * <p>Data formats are deliberately absent. A suite that reads a `.json` fixture and asserts on the
 * parsed object cannot be tripped by prose — there is no prose in it, and the parse is the whole
 * distinction this file exists to draw. Including them took the population from 25 to 66 and every
 * one of the 41 was a fixture read. SH613's Java rule is the same shape: reads a file, and the file
 * is source.</p>
 */
const SCANNED = /\.(mjs|js|ts|tsx|java|md|mdx)"/;

/**
 * ...and the file being read being **this repository's own**, rather than a fixture the suite
 * wrote a moment ago.
 *
 * <p>This is the clause that makes the population honest, and it is not in SH613's Java rule
 * because Java did not need it: a Java test naming `".java"` is scanning source. In JavaScript,
 * `"foo.mjs"` is overwhelmingly a <i>filename inside a fixture</i> — `apply`, `clone`, `init` and
 * twenty others build a temp tree and read it back. Those cannot be tripped by prose, because the
 * prose they read is prose they just wrote. Without this clause the population is 23 and mostly
 * noise; with it, 10 — and all three files this defect has actually bitten are in it.</p>
 */
const READS_THIS_REPOSITORY = /\bROOT\b|\bREPO\b|fileURLToPath\(new URL\("\.\.\/\.\.\//;

const DECLARATION = /@scans\s+(code|prose|both)\b/;
const REASON = /@scans\s+(?:prose|both)\b[^\n]*\breason:\s*\S/;

/** This file and the module it is about — both scan, and both are the instrument. */
const SELF = new Set(["source-scan.test.mjs", "source-text.mjs"]);

function walk(dir, out = []) {
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries) {
    if (entry === "node_modules" || entry === "target" || entry === "dist") continue;
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) walk(path, out);
    else if (/\.(mjs|js)$/.test(entry)) out.push(path);
  }
  return out;
}

/**
 * The scanners, by file name, **with their own comments already stripped**.
 *
 * <p>Every match below is therefore against code — the detection and the declaration alike. Reading
 * raw here would be the exact defect being guarded: this file's own header names `readFileSync` and
 * the declaration, so a raw scan would find files by their prose, and quite possibly this one.</p>
 */
function scanners() {
  const found = new Map();
  for (const tree of TREES) {
    for (const path of walk(join(ROOT, tree))) {
      const name = path.split(/[/\\]/).pop();
      if (SELF.has(name)) continue;
      const raw = readFileSync(path, "utf8");
      const source = code(raw);
      if (READS_A_FILE.test(source) && SCANNED.test(source)
          && READS_THIS_REPOSITORY.test(source)) {
        found.set(name, raw);
      }
    }
  }
  return found;
}

describe("SH740: a JS scan over source or documentation declares what it reads", () => {
  test("every scanner carries an @scans declaration", () => {
    const undeclared = [];
    const modes = {};
    for (const [name, raw] of scanners()) {
      const declared = DECLARATION.exec(raw);
      if (!declared) undeclared.push(name);
      else modes[name] = declared[1];
    }

    process.stdout.write(
      `\nSH740: ${Object.keys(modes).length + undeclared.length} JS scans over source or docs `
        + `${JSON.stringify(modes)}\n`,
    );

    assert.deepEqual(
      undeclared,
      [],
      "SH740: a scan over raw text cannot tell a rule from a sentence about the rule, and neither "
        + "can a reader — SH722's failure message said an id out loud and paid its debt off. Add a "
        + "`// @scans code` line, or `// @scans prose reason: <one sentence>` when reading prose is "
        + "the point.\n  " + undeclared.join("\n  "),
    );
  });

  test("a scan that claims to read code uses the shared stripper", () => {
    const unbacked = [];
    for (const [name, raw] of scanners()) {
      const declared = DECLARATION.exec(raw);
      if (declared?.[1] === "code" && !/support\/source-text\.mjs/.test(raw)) unbacked.push(name);
    }
    assert.deepEqual(
      unbacked,
      [],
      "SH740: a declaration nothing checks is a comment. `code` means a line of prose cannot be "
        + "read as a rule, and the only thing that makes that true is running the scan through "
        + "support/source-text.mjs — four private ideas of what a comment is was the state SH740 "
        + `found.\n  ${unbacked.join("\n  ")}`,
    );
  });

  test("reading prose on purpose is stated, not left to be inferred", () => {
    const unexplained = [];
    for (const [name, raw] of scanners()) {
      const declared = DECLARATION.exec(raw);
      if (declared && declared[1] !== "code" && !REASON.test(raw)) unexplained.push(name);
    }
    assert.deepEqual(
      unexplained,
      [],
      "SH740: an accident cannot explain itself, which is why `reason:` is required here and "
        + `pointless on code. Say what is being read out of the prose and why.\n  ${unexplained.join("\n  ")}`,
    );
  });

  test("the walk is not vacuous, and finds the scans that produced this rule", () => {
    // A moved tree or a tightened pattern turns the instrument off and reports the same green.
    // Named files rather than a count, because a count is a number somebody relaxes rather than
    // investigates — and these three are the ones that were actually tripped.
    assert.deepEqual(
      ["assertion-debt.test.mjs", "floor-kind.test.mjs", "readme-surface.test.mjs"]
        .filter((name) => !scanners().has(name)),
      [],
      "the walk lost a scan that has been bitten by this defect, so a green run means nothing",
    );
  });

  test("the strippers know a rule from a sentence about the rule", () => {
    // Guards the guard, and each case is one that actually happened.
    assert.ok(!code('// SH9201 is mentioned for context').includes("SH9201"),
      "a whole-line comment is not code");
    assert.ok(code('ok(x, "SH9202: holds"); // and SH9203 explains why').includes("SH9202"),
      "a trailing comment does not disqualify the line it trails — SH630 chose that direction");

    // The SH739 case, verbatim in shape: a document correcting a claim has to quote the claim.
    const checklist = 'This used to say *"nothing derives them"* and it was false.';
    assert.ok(!unquoted(checklist).includes("nothing derives them"),
      "a quoted phrase is somebody else's words, which is the whole reason unquoted() exists");
    assert.ok(unquoted("nothing derives them, still").includes("nothing derives them"),
      "while the same sentence unquoted is a claim the document is making");

    assert.ok(!unquoted("```\nnothing derives them\n```").includes("nothing derives them"),
      "a fenced block is a quotation with different punctuation");
    assert.ok(!unquoted("see `nothing derives them` here").includes("nothing derives them"),
      "and so is an inline code span");
  });
});
