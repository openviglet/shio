/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `token-budgets.properties`, read from JavaScript — one reader, not two (SH673).
 *
 * The file is P3's declaration: every ceiling on the agent surface, with the argument for each
 * raise in the comment above it. It lives under `shio-app/src/test/resources` because the Java
 * gates were its first readers, and the coupling across packages is the same one
 * `shio-site/scripts/token-budgets.mjs` already makes on purpose — a second place to keep a
 * ceiling is a place that goes stale.
 *
 * SH394 wrote this parser inside `cli-help-budget.test.mjs`. It is here because a second JS
 * reader of the same file was about to be written, and this project's own rule is that two
 * readers of one format are two readers waiting to disagree — which is, exactly, the defect
 * SH673 was filed on one level up.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));

/** The declaration itself. */
export const BUDGETS_FILE = resolve(
  HERE,
  "..",
  "..",
  "..",
  "shio-app",
  "src",
  "test",
  "resources",
  "token-budgets.properties"
);

/** The conventional ratio — `ShTokenBudget.CHARS_PER_TOKEN`, so these agree with `estTokens`. */
export const CHARS_PER_TOKEN = 4;

/** Characters at the conventional ratio, the same heuristic every `estTokens` on the wire uses. */
export const tokens = (text) => Math.ceil(text.length / CHARS_PER_TOKEN);

/**
 * Every `key=value` in the budgets file, comments and blanks dropped.
 *
 * A missing key is a failure at `need`, not a skipped assertion: a budget test that silently
 * checks nothing is worse than no budget test, because it reports green.
 */
export function budgets() {
  const out = {};
  for (const line of readFileSync(BUDGETS_FILE, "utf-8").split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const eq = trimmed.indexOf("=");
    if (eq === -1) continue;
    out[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
  }
  return out;
}

/** One budget as a number, with a rename in the properties file failing here rather than passing. */
export function need(props, key) {
  const raw = props[key];
  assert.ok(
    raw !== undefined,
    `token-budgets: no key '${key}'. This test asserts it, so a rename there is a failure here ` +
      `rather than an assertion that quietly stops running (SH394).`
  );
  const value = Number(raw);
  assert.ok(Number.isFinite(value), `token-budgets: '${key}' is '${raw}', not a number.`);
  return value;
}
