/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH673 — a budget restated in a second file agrees with the file that declares it.
 *
 * ## The defect this is filed on
 *
 * `mcp-transport.conformance.mjs` holds `tools/list` to a budget it states as a constant rather
 * than reads, and the reasoning for that is sound: the suite runs against an instance it did not
 * build, so reading the working tree would compare a live payload against a budget that instance
 * may predate. In its own words, *"a constant that disagrees with the properties file is a visible
 * edit; a read that silently compares two versions is not."*
 *
 * What the design did not carry is the other half. **A visible edit is only visible if something
 * looks**, and nothing did. The constant was right at SH342 (2375) and then three raises landed on
 * the properties file alone — SH619 to 2385, SH222 to 2395, SH227 to 2400 — until a payload
 * measuring 2397 sat *between* the two: green on `ShMcpToolBudgetTest`, red on `pnpm conformance`.
 * The gate was red for a fortnight's worth of commits over a payload the declared budget allows.
 *
 * ## Why this is a table and not one assertion
 *
 * Restating a budget stays legitimate — the conformance suite's argument for it is unchanged, and
 * the answer is not "stop restating" but "declare it". So each restatement is a row: where the
 * constant is, which key it restates, and why it is not simply read. One row today. A second one
 * is a row, not another test, and a restatement nobody declares is the shape that produced this.
 *
 * ## Why it lives in `pnpm test`
 *
 * Both sides are files in this tree, so the comparison needs no instance, no packaged jar and no
 * boot — it is a string and a properties key. Putting it in the conformance run would mean the
 * drift is only discoverable by the gate the drift breaks, at the price that gate costs. Here it
 * fails in seconds, which is the difference between a check that runs and one that could.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { BUDGETS_FILE, budgets, need } from "./support/token-budgets.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const CLI = resolve(HERE, "..");

/**
 * Every place a budget is restated instead of read, and why.
 *
 * `constant` is matched as `const <name> = <number>`, which is how each of these is written. The
 * match is on the declaration rather than on any occurrence, so a number quoted in a comment or an
 * assertion message cannot satisfy — or break — this.
 */
const RESTATEMENTS = [
  {
    file: "conformance/mcp-transport.conformance.mjs",
    constant: "TOOLS_LIST_BUDGET_TOKENS",
    key: "mcp.tools.list.total",
    why: "the conformance suite runs against an instance it did not build, so reading the working "
      + "tree would compare a live payload against a budget that instance may predate",
  },
];

/** The `const NAME = 1234;` declaration in a source file, as a number. */
function declared(relative, constant) {
  const path = resolve(CLI, relative);
  const source = readFileSync(path, "utf-8");
  const match = new RegExp(`\\bconst\\s+${constant}\\s*=\\s*(\\d+)\\s*;`).exec(source);
  assert.ok(
    match,
    `SH673: ${relative} declares no \`const ${constant} = <number>;\`. This table names it as a `
      + "restated budget, so a rename there is a failure here rather than a row that quietly "
      + "stops checking anything.",
  );
  return Number(match[1]);
}

test("SH673: every restated budget equals the budget file that declares it", () => {
  const props = budgets();

  for (const { file, constant, key, why } of RESTATEMENTS) {
    const restated = declared(file, constant);
    const authoritative = need(props, key);

    assert.equal(
      restated,
      authoritative,
      `SH673: ${file} holds ${constant} at ${restated} while ${key} is ${authoritative} in\n`
        + `  ${BUDGETS_FILE}\n`
        + "The properties file is authoritative — it is where P3's budgets are declared and where "
        + "each raise is argued in the comment above it. Restating is allowed here because "
        + `${why}; keeping the two in step is the price of that, and this is what charges it.\n`
        + "Fix: move the constant to the declared value. If the payload itself grew past what the "
        + "project decided it may cost, that is the other failure and it belongs in the properties "
        + "file with the argument for the raise.",
    );
  }
});

test("SH673: the reader is not vacuous — it reads a real file and can see a disagreement", () => {
  assert.ok(RESTATEMENTS.length > 0, "an empty table would make the test above pass over nothing");

  // The properties file is really being parsed, and really carries the key the row names.
  const props = budgets();
  assert.ok(Object.keys(props).length > 10,
    `only ${Object.keys(props).length} budget(s) parsed — the properties format changed`);
  for (const { key } of RESTATEMENTS) {
    assert.ok(props[key] !== undefined, `${key} is not in the budgets file`);
  }

  // And the source-file half can fail: the same declaration shape with the wrong number is what
  // shipped, so a reader that could not see it would be the defect wearing the fix's name.
  const shipped = "const TOOLS_LIST_BUDGET_TOKENS = 2375;";
  const match = /\bconst\s+TOOLS_LIST_BUDGET_TOKENS\s*=\s*(\d+)\s*;/.exec(shipped);
  assert.equal(Number(match[1]), 2375);
  assert.notEqual(Number(match[1]), need(props, "mcp.tools.list.total"));
});
