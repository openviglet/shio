/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH860 — the bar has to name every kind, or it accepts by omission.
 *
 * `acceptance.properties` is the machine-readable half of `docs/agents/replication.md`'s criterion,
 * and its whole value rests on one property: a finding kind the file does not mention must be a
 * **build** failure and not a release one. `classify` holds an undeclared kind, which is the runtime
 * half; this is the half that finds it before a round does. The subject is the mappers in
 * `commands/judge.mjs`, because they are the only place a `kind` is minted.
 *
 * The failure mode being guarded is silent: somebody adds a finding kind, nobody adds a line, and
 * either every round holds on it forever or — if the default had gone the other way — every round
 * accepts it forever. Both read as the gate working.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import {
  ACCEPTANCE_FILE, NOTED, acceptanceBars, barFor, classify, readAcceptance, requiredFor,
} from "../src/acceptance.mjs";

// @scans both reason: two subjects, and neither stripper is right for the other. The kind reader
// wants CODE — a `kind:` literal is a value the program mints, and a mention in a comment is not one
// — while the criterion checks read PROSE, because the thing being asserted is what a document
// claims. Read raw rather than through either: the code half looks only at lines containing `kind:`,
// which a sentence about kinds does not have, and the prose half asserts phrases no code line
// carries (SH740).

const source = (path) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), "utf8");

/** Every `kind` the mappers can produce, read off the source that mints them. */
function kindsInSource(text) {
  const kinds = new Set();
  // `kind: "diff.facts-lost"` and `kind: name === "audit" ? "audit.error" : "snapshot.…"`, which is
  // why this collects every quoted `<family>.<rest>` on a line that assigns `kind`.
  for (const line of text.split(/\r?\n/)) {
    if (!/\bkind:/.test(line)) continue;
    for (const match of line.matchAll(/["'`]([a-z][\w-]*\.[^"'`$]*)["'`]/gi)) kinds.add(match[1]);
    // A template — `kind: \`verify.${finding.rule ?? "unknown"}\`` — contributes its literal prefix
    // as a family, since the rest is whatever the instrument reported.
    for (const match of line.matchAll(/[`]([a-z][\w-]*)\.\$\{/gi)) kinds.add(`${match[1]}.*`);
    // SH869 — and `kind: REGRESSED`, which is a constant rather than a literal. Resolved against
    // this file's own `const NAME = "…"`, because the alternative is inlining the string at the use
    // site to satisfy a reader — a scan that dictates how the code is written rather than reading
    // what it says, which is the failure one construct along from SH862's.
    const named = /\bkind:\s*([A-Z][A-Z0-9_]*)\b/.exec(line);
    if (named) {
      const declared = new RegExp(`\\b${named[1]}\\s*=\\s*["']([^"']+)["']`).exec(text);
      if (declared) kinds.add(declared[1]);
    }
  }
  return [...kinds].sort();
}

/**
 * Every file that mints a `kind`, read together (SH869).
 *
 * <p>One file when this was written and two once a round's own movement became a finding. Named as a
 * list rather than a directory walk, because the question is *where a kind comes from* and a walk
 * would answer *where the word appears* — which is the over-match SH862 spent a task removing one
 * scan along. A third minter is a line here, in a diff somebody reads.</p>
 */
const MINTERS = ["../src/commands/judge.mjs", "../src/judge-rounds.mjs"];

function kindsMinted() {
  return [...new Set(MINTERS.flatMap((path) => kindsInSource(source(path))))].sort();
}

test("SH860: every finding kind `judge` can mint has a line in the bar", () => {
  const kinds = kindsMinted();
  const bars = acceptanceBars();

  assert.ok(kinds.length >= 5,
    `the reader has to be finding them, or this passes by seeing nothing: ${JSON.stringify(kinds)}`);
  const undeclared = kinds.filter((kind) =>
    barFor(bars, kind, "authorable") === undefined && barFor(bars, kind, "fidelity") === undefined);
  assert.deepEqual(undeclared, [],
    `each of these holds every round until somebody writes a line for it in ${ACCEPTANCE_FILE} — `
      + "which is the right default and the wrong thing to discover on a release");
});

test("SH860: the bar declares nothing `judge` cannot produce", () => {
  // The other direction, and it is the one that rots quietly: a key for a kind nobody mints reads as
  // a criterion being enforced. `snapshot.pixels-differ` is minted as one kind and keyed per mode,
  // so a key is matched when its own name OR its mode-stripped name is a kind.
  const kinds = new Set(kindsMinted());
  const stale = [...acceptanceBars().keys()].filter((key) => {
    // SH865 — `mode.<name>.requires` is not a bar for a kind, it is which instruments a mode's bar
    // rests on. Its own coverage is the test below: every mode `judge` can be in has a line.
    if (/^mode\./.test(key)) return false;
    if (kinds.has(key)) return false;
    const family = `${key.split(".")[0]}.*`;
    if (key === family) return kinds.has(family);
    return ![...kinds].some((kind) => key === kind || key.startsWith(`${kind}.`));
  });

  assert.deepEqual(stale, []);
});

test("SH865: every mode `judge` can be in declares which instruments its bar rests on", () => {
  // The two the criterion names, and `modeOf` answers one of them or null — so an undeclared mode
  // would make `requiredFor` return `[]` and every abstention pass, which is the defect this closes.
  const bars = acceptanceBars();
  for (const mode of ["authorable", "fidelity"]) {
    const required = requiredFor(bars, mode);
    assert.ok(required.includes("verify") && required.includes("diff"),
      `${mode} rests on verify and diff at least: diff is also what tells judge which mode the `
        + `round is in, so a mode that did not require it could not know itself — read ${required}`);
  }
  assert.ok(requiredFor(bars, "fidelity").includes("snapshot"),
    "and pixels are fidelity's whole criterion at 0.00%, so a fidelity round with no snapshot has "
      + "no evidence about the one thing it is judged on");
  assert.equal(requiredFor(bars, "authorable").includes("snapshot"), false,
    "while authorable does not name it, which is SH411's split kept — an optional browser stays "
      + "optional exactly where pixels are not the criterion");
  assert.deepEqual(requiredFor(bars, "nonesuch"), [],
    "and a mode nothing declares requires nothing, which `judgeRuns` reads as INCONCLUSIVE via the "
      + "unstated-mode branch rather than as a pass");
});

/**
 * The criterion's prose home. `acceptance.properties` is the machine-readable half and this is the
 * other one — outside roadkeep on purpose, since `IMPROVEMENTS.md` holds rationale for *unshipped*
 * work and SH692's definition of a correct page was deleted the moment SH692 shipped.
 */
const CRITERION = () => source("../../docs/agents/replication.md");

test("SH864: the criterion does not claim fidelity is the mode without fields", () => {
  // The boundary SH864 crossed was this document. It said fidelity *has no editable fields, by
  // definition — verbatim markup carries no annotations*, and that is a property of the pipeline
  // rather than of the markup: `sh-field` and `data-shio-prop` are attributes, attributes do not lay
  // out, and forty of them on a fidelity page cost nothing measurable against a source that varies
  // against itself by more. A sentence with `by definition` in it is the one nobody re-measures.
  const text = CRITERION();

  assert.doesNotMatch(text, /no editable fields by definition|carries no annotations/i,
    "the claim is falsified and must not come back: criteria 6 and 7 apply to whatever a page "
      + "actually annotates, whichever mode produced its markup");
  assert.match(text, /attributes do not lay out/,
    "and the reason is written where the claim was, so the next reader meets the measurement rather "
      + "than the assumption");
});

test("SH864: the criterion says where an annotated page's values live, and why not a position", () => {
  // The task's own last line: *the open design question is where the values live so a write
  // round-trips, and that is the task, not the demonstration*. A decision nobody wrote down is one
  // the next round re-opens — which is what six declared closes of this block were made of.
  const text = CRITERION();

  assert.match(text, /the field is the address/i,
    "the write path writes `data.<field>` on a post and never creates structure, so the address a "
      + "round-trip needs is the one every other post already uses");
  assert.match(text, /re-capture invalidates every address at once/i,
    "and the rejected alternative is named with the reason it loses, or it is proposed again");
  assert.match(text, /SH868|its own task/i,
    "with the implementation filed rather than implied — a decision that reads as done and is not "
      + "is the `report ahead of its behaviour` shape (SH763, SH849)");
});

test("SH860: a bar above zero has to be explained where it is written", () => {
  // `token-budgets.properties`' own rule, which is the file this one is patterned on: raising a
  // number is allowed and is a deliberate act, and raising one without saying why is the thing the
  // file exists to prevent. A `0` needs no comment — it is the criterion as written.
  const text = source("../src/acceptance.properties");
  const lines = text.split(/\r?\n/);
  const unexplained = [];
  lines.forEach((line, at) => {
    const match = /^\s*([\w.*-]+)\s*=\s*(\d+)\s*$/.exec(line);
    if (!match || Number(match[2]) === 0) return;
    const before = lines.slice(0, at).reverse();
    const comment = before.findIndex((each) => each.trim() && !each.trim().startsWith("#"));
    if (comment === 0) unexplained.push(match[1]);
  });

  assert.deepEqual(unexplained, [],
    "a number nobody explained is the one a later reader lowers or raises by accident");
});

test("SH860: a value that is neither a count nor `noted` is a throw, not a skip", () => {
  assert.throws(() => readAcceptance("diff.facts-lost = some"), /neither a count nor/);
  assert.throws(() => readAcceptance("diff.facts-lost"), /expected `key = value`/);
  assert.throws(() => readAcceptance("= 3"), /a value with no key/);
  assert.deepEqual([...readAcceptance("# a note\n\n a.b = 2 \nc.d = noted\n").entries()],
    [["a.b", 2], ["c.d", NOTED]],
    "a bar nobody can read is worse than no bar, because it reads as a decision that was made");
});

test("SH860: the bar is resolved by exact key, then by mode, then by family", () => {
  const bars = readAcceptance([
    "verify.* = 0",
    "audit.error = 2",
    "snapshot.pixels-differ.authorable = noted",
    "snapshot.pixels-differ.fidelity = 0",
  ].join("\n"));

  assert.equal(barFor(bars, "verify.required-field-empty"), 0, "criterion 1 is about the count");
  assert.equal(barFor(bars, "audit.error"), 2);
  assert.equal(barFor(bars, "snapshot.pixels-differ", "authorable"), NOTED);
  assert.equal(barFor(bars, "snapshot.pixels-differ", "fidelity"), 0);
  assert.equal(barFor(bars, "digest.gap"), undefined,
    "and a kind no line mentions resolves to nothing, which `classify` reads as HOLD");
});

test("SH860: the bar counts subjects per kind, and all of them hold when it is breached", () => {
  const bars = readAcceptance("audit.error = 1");
  const rows = [
    { subject: "/", kind: "audit.error", why: "1 error" },
    { subject: "/about", kind: "audit.error", why: "2 errors" },
  ];

  assert.equal(classify([rows[0]], bars).noted.length, 1, "one subject is at the bar");
  const { held, noted } = classify(rows, bars);
  assert.equal(noted.length, 0);
  assert.deepEqual(held.map((row) => row.subject), ["/", "/about"],
    "all of them, not the surplus: which subject is the one over the line is not a fact, and "
      + "picking one would invent it");
  assert.equal(held[0].bar, 1, "and the row carries the number that judged it, so the report can "
    + "print it rather than the verdict alone");
});
