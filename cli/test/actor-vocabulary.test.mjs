/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH705 — a person is named by the job, not by the species.
 *
 * ## What the copy was doing
 *
 * The three-role line read *"The agent builds. The human curates. The CDA delivers."* Two of those
 * actors are named by what they do and one by what it is not, and `site-content.ts` made the
 * asymmetry literal: `actor: "The human", verb: "curates"`. The page describing that actor is
 * served at `/curator` under the title *"The human half"* — the role word was already chosen, and
 * then not used.
 *
 * That is a register problem before it is a wording problem. A CMS whose selling point is that a
 * machine writes the site cannot afford copy that reads as machines-versus-people, because the
 * reader it most needs is the person deciding whether to let an agent near their site. P7 stated as
 * *"never surprise the human"* claims a species boundary; what it actually guarantees is narrower
 * and stronger — nothing reaches the live site without the say-so of whoever answers for it.
 *
 * ## The rule this file holds
 *
 * **A person gets the role noun** — curator, editor, reviewer, author — or second person in the
 * marketing copy, which `curator.tsx` already writes well ("You open the page before anyone else
 * can"). **The adjective stays**: `human-readable` pairs with `machine-readable` and describes a
 * *format*, not an identity; `human-in-the-loop` names a safety pattern the industry already has a
 * word for. Rewriting either would lose a real distinction, so {@link ALLOWED} is the exemption
 * list and it is deliberately short.
 *
 * ## Why a lint, and why it is zero rather than a baseline
 *
 * SH338: the instrument lands before the fixes, or the next page written reintroduces the term —
 * and 113 occurrences over 31 files is not a set anybody re-greps by hand. Unlike
 * `lint-baseline.test.mjs`, this one carries **no declared debt**, because the whole set was inside
 * one task's reach. A baseline here would be the ceiling that file warns about: one task pays it,
 * the next spends it, and a phrase we decided against is back in the copy a prospect reads.
 *
 * ## What it deliberately does not walk, which is the honest half
 *
 * {@link REPORTED} — the CLI's own source comments, the Java tree, the repo-local skills — is
 * counted and **printed** on every run, and asserted on by nothing. Those are notes between people
 * who already build this product, not copy anybody is sold to; holding them to the same gate would
 * churn a 60-file map on every comment edit and buy no reader anything. The count is printed so the
 * exclusion stays a decision somebody can see rather than a silence. The three roadkeep-governed
 * files are excluded outright: the ledger is a record of what shipped, and editing history to match
 * today's vocabulary is the one repair that makes a record worth less.
 */
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// @scans prose reason: the subject IS the copy — which noun names a person in site strings and
// documentation — so stripping prose would leave nothing to check (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/**
 * The published surfaces: what a reader outside this project is handed.
 *
 * <p>The site's copy, the area files an agent loads, the per-task specs, the plugin a user installs,
 * and the two every-turn indexes. A path may name a file or a directory.</p>
 */
const PUBLISHED = [
  "shio-site/src",
  "docs/agents",
  "docs/specs",
  "claude-plugin",
  "README.md",
  "agents.md",
];

/**
 * Walked, counted, printed — and asserted on by nothing. See the header: these are notes between
 * people who already build the product.
 */
const REPORTED = [
  "cli/src",
  "cli/test",
  "cli/conformance",
  ".claude/skills",
  "shio-app/src/main/java",
];

/** Extensions that carry prose. `.ts`/`.tsx` are here for the site's content modules. */
const PROSE = new Set([".md", ".mdx", ".ts", ".tsx", ".mjs", ".java"]);

/**
 * The compounds that survive, and the reason each one is not the defect.
 *
 * <p>Both are adjectives about a *format* or a *pattern*, and both have a paired opposite that
 * carries the meaning — `machine-readable`, and an agent that runs unattended. Neither names a
 * person, which is the thing SH705 is about.</p>
 */
const ALLOWED = ["human-readable", "human-in-the-loop"];

/** Every file under a path, or the file itself when the path names one. */
function filesUnder(path) {
  const absolute = join(ROOT, path);
  let entry;
  try {
    entry = statSync(absolute);
  } catch {
    return [];
  }
  if (!entry.isDirectory()) return [absolute];
  return readdirSync(absolute, { withFileTypes: true }).flatMap((child) => {
    if (child.name === "node_modules" || child.name === "dist" || child.name === "target") return [];
    return filesUnder(join(path, child.name));
  });
}

/**
 * Every place a file names the species rather than the job.
 *
 * <p>Matches the word wherever it appears — including inside an identifier such as `HumanHalf` or an
 * anchor such as `#human`, both of which were real and both of which a word-boundary check would
 * have missed.</p>
 */
function occurrences(file) {
  if (!PROSE.has(file.slice(file.lastIndexOf(".")))) return [];
  const text = readFileSync(file, "utf8");
  const found = [];
  for (const [index, line] of text.split(/\r?\n/).entries()) {
    let rest = line;
    for (const allowed of ALLOWED) {
      rest = rest.replaceAll(new RegExp(allowed, "gi"), "");
    }
    if (!/human/i.test(rest)) continue;
    found.push({
      file: relative(ROOT, file).replaceAll("\\", "/"),
      line: index + 1,
      text: line.trim().slice(0, 110),
    });
  }
  return found;
}

/** Every occurrence under the given paths, in a stable order. */
function sweep(paths) {
  return paths
    .flatMap((path) => filesUnder(path))
    .sort()
    .flatMap((file) => occurrences(file));
}

test("no published surface names a person by species (SH705)", () => {
  const found = sweep(PUBLISHED);
  const report = found.map((f) => `  ${f.file}:${f.line}  ${f.text}`).join("\n");
  assert.deepEqual(
    found,
    [],
    "SH705: a person is named by the role — curator, editor, reviewer, author — or in second "
      + `person, never as "the human". ${ALLOWED.join(" and ")} are the exemptions, because they `
      + `describe a format and a pattern rather than a person.\n${report}`,
  );
});

test("the exemptions describe a format, not a person (SH705)", () => {
  assert.deepEqual(
    ALLOWED,
    ["human-readable", "human-in-the-loop"],
    "SH705: growing this list is how the rule is repealed one compound at a time. Both entries "
      + "survive because each has a paired opposite that carries the meaning; a new one needs the "
      + "same argument, in a diff somebody reviews.",
  );
});

test("what the rule does not reach is counted, not silent (SH705)", () => {
  const found = sweep(REPORTED);
  const byFile = new Map();
  for (const one of found) byFile.set(one.file, (byFile.get(one.file) ?? 0) + 1);
  console.log(
    `SH705: ${found.length} occurrence(s) over ${byFile.size} file(s) in source comments and `
      + "repo-local skills — deliberately outside the gate, printed so the exclusion stays visible.",
  );
  assert.ok(
    Array.isArray(found),
    "SH705: this is a report, not a gate — see the header for why source comments are excluded.",
  );
});
