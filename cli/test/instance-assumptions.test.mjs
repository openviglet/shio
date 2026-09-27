/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH728 — a live suite creates what it measures, and this is the reader that makes that checkable.
 *
 * ## The measurement
 *
 * SH724 is the rule. It was broken three times in one afternoon, each found the same way — a clean
 * instance, in CI — and each fixed by hand:
 *
 * - seven suites wrote `type: "Page"`, which a fresh install does not declare;
 * - SH714's determinism case reproduced against `PT-FOLDER-INDEX`, a type this developer's box
 *   happened to carry with two attributes at one ordinal;
 * - SH711 edited a Markdown body that only exists when the content field is not projected to a
 *   sidecar, which depends on the type's widget.
 *
 * `agents.md`'s own rule (SH338) is that when a review finds a law being broken, the instrument that
 * finds every *other* break lands **before** the fixes. Here the fixes landed and nothing looked for
 * the fourth — and the suite a runner catches is the one somebody else's session pays for: it
 * arrives as five red files and forty-six cancelled scenarios, none of which name the cause.
 *
 * ## Why static, and why it is cheap
 *
 * Every `type:` literal under `cli/conformance/**` is either provisioned by the suite that uses it —
 * `requireVocabulary`, `putPostTypeDef` — or it is an assumption about the instance. The second set
 * should be a declared list rather than a discovery. Same shape as `live-coverage.test.mjs` and
 * `assertion-debt.test.mjs`: a named list, in `pnpm test`, failing in seconds instead of in the gate
 * that costs a JVM and a live instance.
 *
 * ## The seeded set is read, not written down
 *
 * What a fresh install declares comes from `shio-app/src/main/resources/import/post-types.json` at
 * run time. A copy of those thirteen names here would be a second source of truth for the one fact
 * this test exists to check, and it would go stale in the direction that makes the test pass —
 * somebody adds a seeded type, the list here does not know, and a legitimate use reads as a
 * violation until somebody edits this file. Reading the file means the answer moves with the
 * product.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

import { uncommented } from "./support/source-text.mjs";

// @scans both reason: it reads suite sources for what they assume, and an assumption stated in
// a comment is as much the subject as one written in code (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const CONFORMANCE = join(ROOT, "cli", "conformance");
const SEED = join(ROOT, "shio-app", "src", "main", "resources", "import", "post-types.json");

/**
 * Types a suite may name without provisioning, and the reason each is allowed to.
 *
 * One entry, and it is the inverse of the defect rather than an exception to it. SH724 is about a
 * suite needing a type the instance may not have; `NoSuchTypeAtAll` is a suite needing a type the
 * instance must **not** have, because what it asserts is the refusal. Provisioning it would destroy
 * the test, so "install what you measure" cannot apply — what it measures is an absence.
 *
 * The category will recur: every refusal case needs a name nothing declares. It is declared per
 * file rather than matched by shape on purpose — a rule like "names starting with NoSuch are
 * exempt" is a rule an ordinary type name can wander into, and the whole value here is that the
 * list is short enough to read.
 */
const DECLARED_ASSUMPTIONS = {
  "mcp-write.conformance.mjs": ["NoSuchTypeAtAll"],
};

/** What a fresh install declares — read, never copied. See the header. */
function seededTypes() {
  const parsed = JSON.parse(readFileSync(SEED, "utf8"));
  const rows = Array.isArray(parsed)
    ? parsed
    : parsed.shPostTypes ?? parsed.postTypes ?? [];
  return new Set(rows.map((row) => row.name).filter(Boolean));
}

function conformanceFiles(dir = CONFORMANCE, found = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) conformanceFiles(path, found);
    else if (entry.endsWith(".conformance.mjs")) found.push(path);
  }
  return found;
}

/**
 * Comments stripped, so a paragraph explaining that `Page` is not seeded is not read as a suite
 * assuming it. This file's own header names `type: "Page"` twice, which is SH206's defect waiting
 * to happen and SH613's answer to it one language over.
 */
function code(source) {
  // SH1147 — the shared per-line reading; the cross-line strip this replaced could open inside a
  // string literal and delete the rest of the file.
  return uncommented(source);
}

/** `const NAME = "literal"` in the same file, so a type named once and used twice resolves. */
function constants(source) {
  const found = new Map();
  for (const [, name, value] of source.matchAll(/const\s+(\w+)\s*=\s*["'`]([^"'`]+)["'`]/g)) {
    found.set(name, value);
  }
  return found;
}

/** Every post-type name the file writes against, as a literal or through a local constant. */
function typesUsed(source, consts) {
  const used = new Set();
  for (const [, value] of source.matchAll(/\btype:\s*["'`]([^"'`]+)["'`]/g)) used.add(value);
  for (const [, name] of source.matchAll(/\btype:\s*([A-Z_][A-Z0-9_]*)\b/g)) {
    if (consts.has(name)) used.add(consts.get(name));
  }
  return used;
}

/**
 * Every post-type name the file installs before it uses it.
 *
 * `requireVocabulary(conn)` defaults to `["Page"]` — the default is part of the contract, so a suite
 * that calls it bare has provisioned `Page` and this has to know that, or the seven suites SH724
 * fixed would all read as violations again.
 */
function typesProvisioned(source, consts) {
  const provisioned = new Set();
  for (const [, args] of source.matchAll(/requireVocabulary\(([^)]*)\)/g)) {
    const names = [...args.matchAll(/["'`]([^"'`]+)["'`]/g)].map((match) => match[1]);
    if (names.length === 0) provisioned.add("Page");
    names.forEach((name) => provisioned.add(name));
  }
  for (const [, arg] of source.matchAll(/putPostTypeDef\(\s*([^,)]+)/g)) {
    const literal = arg.trim().match(/^["'`]([^"'`]+)["'`]$/);
    if (literal) provisioned.add(literal[1]);
    else if (consts.has(arg.trim())) provisioned.add(consts.get(arg.trim()));
  }
  return provisioned;
}

test("SH728: every post-type a conformance suite writes against is seeded or provisioned by it", () => {
  const seeded = seededTypes();
  assert.ok(seeded.size > 0, "SH728: the seed file yielded no types, so this test proves nothing.");
  assert.ok(!seeded.has("Page"),
    "SH728: `Page` is now seeded, which would make this test's own premise stale — SH724 exists "
    + "because a fresh install does NOT declare it. Re-read the task before relaxing anything.");

  const assumptions = [];
  for (const file of conformanceFiles()) {
    const source = code(readFileSync(file, "utf8"));
    const consts = constants(source);
    const provisioned = typesProvisioned(source, consts);
    const name = file.slice(CONFORMANCE.length + 1).replace(/\\/g, "/");
    for (const type of typesUsed(source, consts)) {
      if (seeded.has(type) || provisioned.has(type)) continue;
      if (DECLARED_ASSUMPTIONS[name]?.includes(type)) continue;
      assumptions.push(`  ${name} — writes type "${type}" and never installs it`);
    }
  }

  assert.deepEqual(assumptions, [],
    "SH728: these suites assume the instance already carries a post-type.\n"
    + `${assumptions.join("\n")}\n\n`
    + "That is SH724: the instance carrying the test. It is green on any workstation that has ever\n"
    + "applied a blueprint and red on a clean runner, as five failed files and forty-six cancelled\n"
    + "scenarios that name none of this.\n"
    + "Fix: `await requireVocabulary(conn, [\"<type>\"])` in the suite's `before`, or\n"
    + "`admin.putPostTypeDef(...)` for a shape you are defining. If the type genuinely has to come\n"
    + "from the instance, add it to DECLARED_ASSUMPTIONS with the reason — in a diff somebody reads.");
});

test("SH728: the reader finds the shape it is about, and not the prose around it", () => {
  // Guards the guard. A scan that matches nothing is green for the wrong reason, and one that
  // matches a comment is the defect SH613 named for the Java lints.
  const consts = constants('const COLLIDING_TYPE = "PT-FOLDER-INDEX";');
  assert.deepEqual([...typesUsed('ops: [{ type: "Page" }]', consts)], ["Page"]);
  assert.deepEqual([...typesUsed("ops: [{ type: COLLIDING_TYPE }]", consts)], ["PT-FOLDER-INDEX"]);

  assert.deepEqual([...typesProvisioned("await requireVocabulary(conn);", consts)], ["Page"],
    "the bare call's default IS the contract — reading it as provisioning nothing would fail the "
    + "seven suites SH724 fixed");
  assert.deepEqual([...typesProvisioned('requireVocabulary(conn, ["Article", "Page"]);', consts)],
    ["Article", "Page"]);
  assert.deepEqual([...typesProvisioned("admin.putPostTypeDef(COLLIDING_TYPE, {", consts)],
    ["PT-FOLDER-INDEX"]);

  assert.deepEqual([...typesUsed(code('// a suite that wrote type: "Ghost" would fail this'), consts)],
    [], "prose about the rule is not a breach of it");
});
