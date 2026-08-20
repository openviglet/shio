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
  }
  return [...kinds].sort();
}

test("SH860: every finding kind `judge` can mint has a line in the bar", () => {
  const kinds = kindsInSource(source("../src/commands/judge.mjs"));
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
  const kinds = new Set(kindsInSource(source("../src/commands/judge.mjs")));
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
