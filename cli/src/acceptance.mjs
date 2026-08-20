/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH860 — the bar a verdict needs.
 *
 * `shio judge` already had the shape of an acceptance gate (SH589): it runs the instruments, answers
 * once, and names where they disagreed. What it had no notion of is a **bar**. It exited 1 on any
 * finding, so every round ended with work outstanding and none ended *accepted* — and the cost is
 * measurable rather than theoretical: Block Q carries 131 shipped entries and has been declared
 * closed and reopened six times. Each close was a line count reaching zero; each reopen was the next
 * round finding something. Emptiness was standing in for doneness because nothing else was available.
 *
 * The criterion itself is prose, in `docs/agents/replication.md`, which is deliberately outside
 * roadkeep: `IMPROVEMENTS.md` holds rationale for *unshipped* work, so SH692's definition of a correct
 * page was deleted the moment SH692 shipped, which is why it never held. This module is the half a
 * command can read.
 */

import { readFileSync } from "node:fs";

/** The declared bar, as bytes. Beside the source for `help.mjs`' reason: it ships with the CLI. */
export const ACCEPTANCE_FILE = "acceptance.properties";

/**
 * The word a value takes when a kind is reported on every run and holds none of them.
 *
 * <p>Not "ignored", and the distinction is the one this project keeps paying for: a `noted` kind is
 * printed under its own heading with the bar that admitted it. A finding nobody prints is the silence
 * P9 exists to remove; a finding that stops a release nobody agreed it should stop is what kept Block
 * Q open for six rounds.</p>
 */
export const NOTED = "noted";

/** The one key shape whose value is a list of instrument names rather than a bar (SH865). */
const REQUIRES = /^mode\.[a-z]+\.requires$/;

/**
 * The instruments a mode's bar rests on, or `[]` where the file names none (SH865).
 *
 * <p>A verdict of `accepted` is a claim that the criterion was met, and an instrument that did not
 * run tested nothing. SH589 already said an abstention is never green; what was missing is *which*
 * abstentions matter, since `unproven` alone must not fail a build — failing on it would make an
 * optional browser a hard dependency of every run (SH411's split).</p>
 *
 * <p>Declaring it per mode is what resolves that: `snapshot` is optional for authorable because
 * pixels are not that mode's criterion, and it is the whole criterion for fidelity. So the split
 * survives and a fidelity round without a browser stops reading as accepted.</p>
 */
export function requiredFor(bars, mode) {
  const named = bars?.get(`mode.${mode}.requires`);
  return Array.isArray(named) ? named : [];
}

/**
 * `key = value` lines, `#` comments, blanks — the same shape `token-budgets.properties` uses.
 *
 * <p>A value is a non-negative integer or {@link NOTED}. Anything else is a **throw** rather than a
 * skip: a bar nobody can read is worse than no bar, because it reads as a decision that was made.</p>
 *
 * @returns {Map<string, number|"noted">}
 */
export function readAcceptance(text) {
  const bars = new Map();
  for (const [at, raw] of String(text ?? "").split(/\r?\n/).entries()) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const split = line.indexOf("=");
    if (split < 0) {
      throw new Error(`${ACCEPTANCE_FILE}:${at + 1}: expected \`key = value\`, read \`${line}\``);
    }
    const key = line.slice(0, split).trim();
    const value = line.slice(split + 1).trim();
    if (!key) throw new Error(`${ACCEPTANCE_FILE}:${at + 1}: a value with no key`);
    if (value === NOTED) {
      bars.set(key, NOTED);
      continue;
    }
    // SH865 — the third value shape, and the only key pattern that takes it: which instruments a
    // mode's bar rests on. A list is spelled out rather than inferred from a comma or a space
    // anywhere, so a typo in a count is still a refusal rather than a one-element list.
    if (REQUIRES.test(key)) {
      const names = value.split(/\s+/).filter(Boolean);
      if (!names.length) {
        throw new Error(`${ACCEPTANCE_FILE}:${at + 1}: \`${key}\` names no instrument`);
      }
      bars.set(key, names);
      continue;
    }
    if (!/^\d+$/.test(value)) {
      throw new Error(
        `${ACCEPTANCE_FILE}:${at + 1}: \`${key}\` is \`${value}\`, which is neither a count nor `
          + `\`${NOTED}\` — a bar nobody can read is worse than no bar`,
      );
    }
    bars.set(key, Number(value));
  }
  return bars;
}

/** The bar as it ships. Read once per process; the file is a constant of the build. */
let cached = null;
export function acceptanceBars() {
  cached ??= readAcceptance(readFileSync(new URL(`./${ACCEPTANCE_FILE}`, import.meta.url), "utf8"));
  return cached;
}

/**
 * The bar for one finding kind, or `undefined` where the file declares none.
 *
 * <p>Three lookups in order, and the order is the whole rule: the exact key, then the key with its
 * last segment replaced by the run's mode, then the kind's family wildcard. So
 * `snapshot.pixels-differ` resolves through `snapshot.pixels-differ.authorable`, and every
 * `verify.<rule>` resolves through `verify.*` — because criterion 1 is about the count of errors and
 * not about which rule produced one.</p>
 *
 * <p><b>`undefined` is not a pass.</b> {@link classify} holds a kind with no bar, for the reason the
 * file's own header gives: an undeclared kind is one nobody has decided about, and accepting it by
 * omission is how a criterion stops meaning anything. The decision is written down or it does not
 * exist.</p>
 */
export function barFor(bars, kind, mode = "authorable") {
  const key = String(kind ?? "");
  if (bars.has(`${key}.${mode}`)) return bars.get(`${key}.${mode}`);
  if (bars.has(key)) return bars.get(key);
  const family = key.split(".")[0];
  return bars.get(`${family}.*`);
}

/**
 * The flagged rows split into the ones that hold the round and the ones the bar admits (SH860).
 *
 * <p>Counted **per kind across every instrument**, because the bar is a statement about how many
 * subjects may carry a finding and not about how many instruments noticed it. Two instruments
 * flagging one page for one reason is one subject, which is also what makes a `0` bar mean what a
 * reader expects it to.</p>
 *
 * @returns `{held, noted}` — each row carrying the `bar` that judged it, so the report can print the
 *          number rather than the verdict alone (P9). Sorted by kind then subject (P5).
 */
export function classify(rows, bars, mode = "authorable") {
  const byKind = new Map();
  for (const row of rows ?? []) {
    const kind = String(row?.kind ?? "unknown");
    const group = byKind.get(kind) ?? [];
    group.push(row);
    byKind.set(kind, group);
  }
  const held = [];
  const noted = [];
  for (const [kind, group] of byKind) {
    const bar = barFor(bars, kind, mode);
    const subjects = new Set(group.map((row) => row.subject));
    // A kind the file does not mention: held, and the row says the file and the key so the fix is
    // one line in a reviewable place rather than a guess about what somebody meant.
    if (bar === undefined) {
      for (const row of group) held.push({ ...row, bar: null, undeclared: true });
      continue;
    }
    if (bar === NOTED) {
      for (const row of group) noted.push({ ...row, bar: NOTED });
      continue;
    }
    // At or below the bar is admitted, above it holds — and *all* of them hold, not the surplus:
    // which subject is the one over the line is not a fact, and picking one would invent it.
    const over = subjects.size > bar;
    for (const row of group) (over ? held : noted).push({ ...row, bar });
  }
  const order = (a, b) => `${a.kind} ${a.subject}`.localeCompare(`${b.kind} ${b.subject}`);
  return { held: held.sort(order), noted: noted.sort(order) };
}
