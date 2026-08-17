/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH782 — a regex built from a template literal, whose escapes belong to the wrong layer.
 *
 * ## The defect, written and shipped in one afternoon
 *
 *     const named = (name) => new RegExp(`\b${name}\b`).test(prose);
 *
 * Inside a template literal `\b` is the **backspace** escape, U+0008. The pattern became a control
 * character either side of the name and matched nothing, so a check written that day reported *0 of
 * 8 section types documented* against pages that named all eight. A false red — and the same
 * mistake in a rule phrased the other way is a false green nobody would ever look at.
 *
 * `\d`, `\w` and `\s` are worse in one respect. JavaScript leaves an unknown escape as the bare
 * character, so `` `\d` `` is the letter `d`: a pattern that still matches things, just not the
 * ones that were written, with nothing anywhere to say so.
 *
 * ## Why a lint that finds nothing today is worth having
 *
 * The population was measured before this was written: roughly fifteen `new RegExp(<template>)`
 * sites across the CLI, its conformance suite and its scripts, and **every one written before this
 * week spells `\\b`, `\\s`, `\\d`**. One newcomer got it wrong. So this opens at zero and exists to
 * catch the next — the SH338 shape, where the instrument lands because a defect proved the class is
 * reachable, not because the class is currently populated.
 *
 * ## What it deliberately does not do
 *
 * It does not parse JavaScript. It finds `new RegExp(` followed by a template literal and reads the
 * bytes between the backticks, skipping `${…}` spans so an interpolation carrying a backtick cannot
 * end the literal early. A `RegExp` built by concatenation, or handed a variable, is invisible to
 * it — deliberately, because the defect is *the escape written next to the interpolation*, and a
 * scanner that tried to follow values would trade a decidable rule for a guess.
 */
// @scans code — a regex inside a comment is prose ABOUT a regex, and this file's own header quotes
// the exact broken pattern it exists to refuse. Read raw, the explanation is a breach (SH740), which
// is the same trap SH769 met one file over; the shared stripper is what tells them apart.
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

import { isProse } from "./support/source-text.mjs";

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/** The trees whose JavaScript this project owns and runs. */
const TREES = ["cli/src", "cli/test", "cli/conformance", "scripts", "shio-sections/src",
  "shio-sections/scripts", "shio-model/src"];

/**
 * The escapes that mean something to a regex and something else to a string literal.
 *
 * <p>Two failure shapes, both silent. `\b` and `\B` are real string escapes — backspace — so the
 * pattern gets a control character. The rest are *unknown* escapes, which JavaScript resolves to
 * the bare letter, so `\d` becomes `d`. Neither is a syntax error anywhere.</p>
 *
 * <p>Not included: `\n`, `\t`, `\r`, `\0`, `\\`, `\.`, `\(` and friends. Those mean the same thing
 * to both layers, or are already spelled with the doubled backslash a regex needs, so flagging them
 * would report correct code.</p>
 */
const AMBIGUOUS = "bBdDwWsS";

/**
 * Signed exceptions: a single-backslash escape that really is meant as the string's.
 *
 * <p>Empty, and kept as a list rather than a rule for the reason every exemption here is: a naming
 * convention would silently absorb the next real one. If U+0008 genuinely is the subject, name the
 * site and say so.</p>
 */
const DECLARED = new Set();

/**
 * Comments blanked rather than removed, so a reported line is the line in the file.
 *
 * <p>`code()` drops the lines, which is right for a scan that only asks *whether* a phrase is in
 * the code and wrong for one that has to say **where**. Found by running this: the first findings
 * named lines whose content was an unrelated assertion, because every comment above them had
 * shifted the count. Same predicate, `isProse`, so this is the shared stripper's own rule and not a
 * second opinion about what a comment is (SH740).</p>
 */
function withoutComments(body) {
  return body.split("\n").map((line) => (isProse(line) ? "" : line)).join("\n");
}

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) walk(path, out);
    else if (/\.(mjs|js|ts)$/.test(path) && !path.includes("node_modules")) out.push(path);
  }
  return out;
}

/**
 * Every `new RegExp(<template literal>)` in one source, as the raw bytes between its backticks.
 *
 * <p>`${…}` spans are skipped rather than read: an interpolation may itself contain a backtick, and
 * a scanner that stopped at the first one would read half a pattern and report on the remainder of
 * the file. Nesting is counted, because `${a ? `x` : `y`}` is legal and rare and would otherwise
 * end the literal in the wrong place.</p>
 */
export function templatePatterns(source) {
  const found = [];
  const marker = "new RegExp(";
  for (let at = source.indexOf(marker); at >= 0; at = source.indexOf(marker, at + 1)) {
    let i = at + marker.length;
    while (i < source.length && /\s/.test(source[i])) i += 1;
    if (source[i] !== "`") continue;
    i += 1;
    let raw = "";
    let depth = 0;
    for (; i < source.length; i += 1) {
      const ch = source[i];
      if (ch === "\\") {
        // Only outside an interpolation. Found by running this: `${leaf.replace(/[^\w]/g, …)}`
        // carries a REGEX LITERAL whose `\w` is perfectly correct, and appending it here reported
        // a correct site — the scanner reading the half it had just decided to skip.
        if (depth === 0) raw += ch + (source[i + 1] ?? "");
        i += 1;
        continue;
      }
      if (ch === "$" && source[i + 1] === "{") {
        depth += 1;
        i += 1;
        continue;
      }
      if (depth > 0) {
        if (ch === "{") depth += 1;
        if (ch === "}") depth -= 1;
        continue;
      }
      if (ch === "`") break;
      raw += ch;
    }
    found.push({ raw, line: source.slice(0, at).split("\n").length });
  }
  return found;
}

/** The ambiguous escapes in one pattern — a backslash the string will eat before the regex sees it. */
export function wrongLayer(raw) {
  const hits = [];
  for (let i = 0; i < raw.length; i += 1) {
    if (raw[i] !== "\\") continue;
    let run = 0;
    while (raw[i + run] === "\\") run += 1;
    const next = raw[i + run];
    // An ODD run is one the string consumes: `\b` is backspace, `\\b` is a literal backslash then
    // `b`, which is what a regex needs. Even runs are already correct.
    if (run % 2 === 1 && next && AMBIGUOUS.includes(next)) hits.push(`\\${next}`);
    i += run;
  }
  return hits;
}

test("SH782: no regex built from a template literal spells an escape the string will eat", () => {
  const findings = [];
  let scanned = 0;
  let patterns = 0;
  for (const tree of TREES) {
    for (const path of walk(join(ROOT, tree))) {
      const relative = path.slice(ROOT.length).replace(/\\/g, "/");
      scanned += 1;
      for (const { raw, line } of templatePatterns(withoutComments(readFileSync(path, "utf8")))) {
        patterns += 1;
        const wrong = wrongLayer(raw);
        if (wrong.length && !DECLARED.has(`${relative}:${line}`)) {
          findings.push(`${relative}:${line} — ${wrong.join(", ")} in \`${raw}\``);
        }
      }
    }
  }

  process.stdout.write(`\nSH782: ${patterns} template-built regex(es) over ${scanned} file(s), `
    + `${findings.length} spelling an escape the string eats\n`);

  assert.ok(patterns > 5,
    `SH782: found ${patterns} template-built regexes, which is fewer than the population measured `
      + "when this was written — a walk that stopped finding them would pass over nothing");
  assert.deepEqual(
    findings,
    [],
    "SH782: inside a template literal this backslash belongs to the STRING, not to the regex. "
      + "`\\b` becomes backspace and matches nothing; `\\d` becomes the letter d and matches the "
      + "wrong thing. Neither is a syntax error, and the check that shipped with one reported 0 of "
      + "8 subjects documented against pages that named all eight. Double the backslash.",
  );
});

test("SH782: the reader is not vacuous — it finds the shape it exists to refuse", () => {
  // The pattern that shipped, and its correction, read by the same functions the walk above uses.
  // Without this the assertion could pass because the detector stopped detecting.
  const broken = templatePatterns("const re = new RegExp(`\\b${name}\\b`);");
  assert.equal(broken.length, 1, "the extractor finds a template-built regex at all");
  assert.deepEqual(wrongLayer(broken[0].raw), ["\\b", "\\b"],
    "and reports both ends of the exact pattern SH782 was filed for");

  const fixed = templatePatterns("const re = new RegExp(`\\\\b${name}\\\\b`);");
  assert.deepEqual(wrongLayer(fixed[0].raw), [],
    "while the doubled form every other site in this repository uses is silent");

  assert.deepEqual(wrongLayer("\\d and \\s and \\W"), ["\\d", "\\s", "\\W"],
    "the unknown escapes too, which are the quieter half: JavaScript resolves them to the bare "
      + "letter, so the regex matches something and it is not what was written");
  assert.deepEqual(wrongLayer("\\n\\t\\.\\("), [],
    "and the escapes that mean the same to both layers are left alone, or this would report every "
      + "correct pattern in the repository");

  // The interpolation guard: a backtick inside `${…}` must not end the literal early.
  const nested = templatePatterns("new RegExp(`^${a ? `x` : `y`}\\d$`)");
  assert.equal(nested.length, 1);
  assert.deepEqual(wrongLayer(nested[0].raw), ["\\d"],
    "the escape AFTER a nested interpolation is still read, which a scanner that stopped at the "
      + "first inner backtick would have missed");
});
