/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH804 — a byte no rendering shows you.
 *
 * ## The failure
 *
 * An edit wrote a literal **backspace** — one byte, `0x08` — where the two characters of `\b` were
 * meant. Every rendering hid it: the editor showed nothing, `grep` showed the line as expected, and
 * Node's own assertion output printed `expected: /… js\b/` beside `actual: '… js'`, a string that
 * regex plainly contains. An assertion failing while both halves render as a match is the least
 * diagnosable shape available. `od -c` was the only thing that answered.
 *
 * This is SH782's class — an escape resolved by the layer beneath the one meant to read it — but it
 * did not arrive through JavaScript, so SH782's guard could not see it. It arrived through the edit.
 * Hence a rule about the **byte** rather than the route: it catches every future variant whatever
 * tool introduces it, and needs no parsing to do so.
 *
 * ## What the first run found, which is why this is not a ratchet over an empty set
 *
 * Two live defects, both in `docs/agents/build.md`, both invisible for as long as they had been
 * there:
 *
 * - the SH782 table's "written" column showed `` new RegExp(`${x}`) `` where it meant
 *   `` new RegExp(`\b${x}\b`) `` — the two `\b` had been resolved into backspaces, so the row
 *   documenting the defect had become an instance of it, and read as the *opposite* of its point;
 * - `✖ test\fixture-traits.test.mjs` had lost its `\f` to a form feed, printing as
 *   `testixture-traits.test.mjs`. That one had been read aloud in this repository several times
 *   without anybody asking where the `f` went.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

// @scans both reason: the subject is BYTES, so this reads code and prose alike and deliberately
// reads them raw — stripping comments would hide a control character in a comment, which is exactly
// as invisible and exactly as wrong as one in code. Both defects the first run found were in prose.
// Nothing is lost by not using the shared stripper here because nothing is being told apart: there
// is no sentence about a control byte that contains one, only the byte or not.
//
// This file also reads ITSELF like any other: both
// declarations are written as escapes and the fixtures are built from character codes, so it holds
// none of them. The first draft did the opposite — skipped itself in the walk, and shipped with the
// literal bytes in its own character class. A file exempt from its own rule is a self-exclusion
// hiding exactly the thing (SH740, SH792).

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/** Where this repository's own source and prose live. */
const TREES = ["cli/src", "cli/test", "cli/conformance", "scripts", "shio-sections/src",
  "shio-model/src", "shio-react/src", "shio-app/src", "shio-site/src", "docs"];

const READ = new Set([".mjs", ".js", ".ts", ".tsx", ".java", ".md", ".json", ".yml", ".yaml"]);

/**
 * Built output that happens to live under a source tree.
 *
 * <p>`public/assets/` is the compiled console and its vendored bundles — minified third-party
 * JavaScript, which contains control bytes as a matter of course and which nobody here writes. A
 * rule about what *we* type has no business reading it.</p>
 */
const BUILT = ["shio-app/src/main/resources/public/"];

/**
 * The one C0 byte this repository writes on purpose, and where.
 *
 * <p>`\0` as a composite-key separator — `` `${a}\0${b}` `` and `.join("\0")` — is a deliberate
 * idiom: a byte that cannot occur in the data being joined, so two different pairs can never
 * collide into one key. Six files use it and each is named here rather than the byte being allowed
 * everywhere, so a seventh adoption is a diff somebody reads.</p>
 */
const SEPARATOR = new Set([
  "cli/src/convert.mjs",
  "cli/src/diff.mjs",
  "cli/src/theme.mjs",
  "cli/src/verify-baseline.mjs",
  "shio-react/src/app/console/content/static-file.list.page.tsx",
  "shio-app/src/main/java/com/viglet/shio/render/ShRenderHelpers.java",
]);

/** Tab, newline and carriage return are the three a source file may hold. */
const FORBIDDEN = /[\u0001-\u0008\u000B\u000C\u000E-\u001F]/;
const NUL = "\u0000";

function walk(dir, out = []) {
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries) {
    const path = join(dir, entry);
    if (["node_modules", "target", "dist", ".git"].includes(entry)) continue;
    if (statSync(path).isDirectory()) walk(path, out);
    else if (READ.has(path.slice(path.lastIndexOf(".")))) out.push(path);
  }
  return out;
}

/** Every control byte one file holds that it is not allowed to, as `path:line — U+XXXX`. */
export function offences(relative, text) {
  const found = [];
  text.split("\n").forEach((line, at) => {
    const chars = [...line].filter((ch) => FORBIDDEN.test(ch)
      || (ch === NUL && !SEPARATOR.has(relative)));
    for (const ch of new Set(chars)) {
      const code = ch.codePointAt(0).toString(16).toUpperCase().padStart(4, "0");
      found.push(`${relative}:${at + 1} — U+${code}`);
    }
  });
  return found;
}

test("SH804: no source or document holds a control byte no rendering will show you", () => {
  const offenders = [];
  let scanned = 0;
  for (const file of TREES.flatMap((tree) => walk(join(ROOT, tree)))) {
    const relative = file.slice(ROOT.length).replace(/\\/g, "/");
    if (BUILT.some((prefix) => relative.startsWith(prefix))) continue;
    scanned += 1;
    offenders.push(...offences(relative, readFileSync(file, "utf8")));
  }

  console.log(`SH804: ${scanned} file(s) read for control bytes`);
  assert.deepEqual(
    offenders,
    [],
    "a control byte is in a file somebody has to read, and no rendering will show it to them.\n"
      + "This is almost always an escape that a layer below resolved: `\\b` becoming a backspace, "
      + "`\\f` in a Windows path becoming a form feed. The line will look right in an editor, in "
      + "`grep`, and in a failure message — `od -c` is what answers (SH804).\n"
      + "Write the two characters instead. If the byte is deliberate, name the file in SEPARATOR "
      + "with the reason, in a diff somebody reviews.",
  );
});

test("SH804: the reader is not vacuous — it finds the two bytes that were actually there", () => {
  // Both defects the first run caught, as fixtures rather than as a memory. Composed from character
  // codes so this file does not contain the bytes it refuses — the same reason SH801's fixtures are
  // built by concatenation. It is NOT skipped in the walk above: a file exempt from its own rule can
  // ship full of what it forbids, and this one did until the scan was pointed at itself.
  const backspace = String.fromCharCode(8);
  const formFeed = String.fromCharCode(12);

  assert.deepEqual(
    offences("docs/agents/build.md", `| new RegExp(\`${backspace}\${x}${backspace}\`) |`),
    ["docs/agents/build.md:1 — U+0008"],
    "the SH782 table's row, which had lost both its `\\b` and so documented the opposite of itself");
  assert.deepEqual(
    offences("docs/agents/build.md", `✖ test${formFeed}ixture-traits.test.mjs`),
    ["docs/agents/build.md:1 — U+000C"],
    "and the `\\f` in a Windows path, which had been read aloud here several times as "
      + "`testixture-traits.test.mjs` with nobody asking where the f went");

  assert.deepEqual(offences("cli/src/diff.mjs", `const key = \`a${NUL}b\`;`), [],
    "while the declared separator is silent in a file that declares it");
  assert.deepEqual(offences("cli/src/other.mjs", `const key = \`a${NUL}b\`;`),
    ["cli/src/other.mjs:1 — U+0000"],
    "and not in one that does not — a seventh adoption of the idiom is a reviewed diff");
  assert.deepEqual(offences("cli/src/x.mjs", "tabs\tand\rnewlines are fine"), [],
    "and the three a source file may hold stay allowed, or this would report every file");
});
