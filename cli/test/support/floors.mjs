/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH741 — how a non-vacuity floor is found, in one place.
 *
 * <p>SH672 wrote this, SH738 taught it the Java half, and SH741 moved it here — because a second
 * reader now needs the same answer. `known-debt.test.mjs` asks whether a TOLERATED exemption still
 * names a floor that exists, and re-implementing the scan there would be SH583s shape exactly:
 * two places deciding what a floor is, agreeing only by accident. The ledger stays in
 * `floor-kind.test.mjs`; only the mechanism is here.</p>
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";

import { code } from "./source-text.mjs";

// @scans code — a javadoc explaining why a floor is the honest kind must not read as one.
const SUITES = fileURLToPath(new URL("../", import.meta.url));

/**
 * The other half of the repository, and the reason SH738 exists.
 *
 * <p>SH672 shipped complete against its own brief and blind against its reach: the walk read one
 * flat directory of `.test.mjs`, so `shio-app/src/test/java/**` — where the governed-docs readers
 * with the longest history live — was outside it entirely. SH737 then happened in exactly the shape
 * the header above describes: a floor over `IMPROVEMENTS.md`'s section count, written at 50,
 * lowered to 10, wrong again at 10 when block completions pruned the file to exactly that. This
 * classifier did not miss it. It <b>could not see it</b>, and would have flagged it on the first
 * run — which is what {@code theRuleWouldHaveCaughtSH737} asserts rather than claims.</p>
 */
const JAVA_SUITES = fileURLToPath(new URL("../../../shio-app/src/test/java/", import.meta.url));

/**
 * The governed files whose contents are **meant to shrink**.
 *
 * <p>`ROADMAP.md` empties as work ships and `IMPROVEMENTS.md` loses a section per ship. A test that
 * reads either and demands a count above one is asserting that the project has not made progress.</p>
 */
export const SHRINKING = ["IMPROVEMENTS", "ROADMAP"];

/**
 * And the ones that only grow, over which an absolute floor is an honest claim.
 *
 * <p>`CHANGELOG.md` gains an entry per ship and never loses one; a source corpus gains files. A floor
 * over those cannot fire for the project being healthy, which is the whole distinction.</p>
 */
export const GROWING = ["CHANGELOG", "src", "suite"];

/** `assert.ok(x.length > 12` / `x.size > 12` — the shape a non-vacuity floor is written in. */
const FLOOR = /assert\.ok\(\s*([A-Za-z_$][\w$.]*)\.(length|size)\s*>\s*(\d+)/g;

/**
 * The same claim in AssertJ, which is the second half of why SH737 was invisible (SH738).
 *
 * <p>A bound here is a <i>method</i>, and several of them mean the same thing, so this is a list
 * where the JavaScript side is one comparison. The subject is whatever the assertion was opened on
 * — usually a chained expression rather than a name — so it is captured loosely and only used to
 * label the offender.</p>
 */
const JAVA_FLOOR =
  /\.(hasSizeGreaterThan|hasSizeGreaterThanOrEqualTo|isGreaterThan|isGreaterThanOrEqualTo)\(\s*(\d+)\s*\)/g;

export function suites() {
  const files = new Map();
  // SH740 — comments stripped, which this file had not been doing. A floor written inside a
  // javadoc explaining floors is exactly the sentence-about-the-rule case, and this file is full
  // of that prose by construction.
  for (const name of readdirSync(SUITES)) {
    if (name.endsWith(".test.mjs")) {
      files.set(name, code(readFileSync(join(SUITES, name), "utf8")));
    }
  }
  for (const path of javaFiles(JAVA_SUITES)) {
    files.set(path.split(/[/\\]/).pop(), code(readFileSync(path, "utf8")));
  }
  return files;
}

/** Every `.java` under a tree. Recursive, because the Java suites are a package hierarchy. */
function javaFiles(dir) {
  const out = [];
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) out.push(...javaFiles(path));
    else if (entry.endsWith(".java")) out.push(path);
  }
  return out;
}

/** Which corpus a file reads — by the governed names it mentions at all. */
export function corporaOf(body) {
  return {
    shrinking: SHRINKING.filter((name) => body.includes(name)),
    growing: GROWING.filter((name) => body.includes(name)),
  };
}

/** Every numeric floor in a file, with its subject and its bound. */
export function floorsIn(body) {
  const found = [];
  FLOOR.lastIndex = 0;
  let match;
  while ((match = FLOOR.exec(body)) !== null) {
    found.push({ subject: match[1], bound: Number(match[3]) });
  }
  JAVA_FLOOR.lastIndex = 0;
  while ((match = JAVA_FLOOR.exec(body)) !== null) {
    found.push({ subject: match[1], bound: Number(match[2]) });
  }
  return found;
}
