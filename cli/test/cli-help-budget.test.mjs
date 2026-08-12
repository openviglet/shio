/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH394 — the CLI's discovery surface has a budget, so it can be compared rather than assumed.
 *
 * WHY THIS FILE EXISTS
 * `token-budgets.properties` has capped `mcp.tools.list.total` since SH132 and fails the build on
 * a regression, because that payload is re-sent every turn of every session. The CLI had no
 * equivalent: `shio --help` was prose that no test, ceiling or benchmark touched. Neither number
 * is knowable from the other, so the recurring argument that one surface is cheaper than the other
 * was being made from intuition on both sides — which P3 calls a defect regardless of which way it
 * points.
 *
 * THE TWO COSTS ARE SHAPED DIFFERENTLY, AND THAT IS THE WHOLE POINT
 * MCP's is per turn and bounded. The CLI's is per session, on demand, and unbounded: an agent pays
 * for the help it reads, once, and nothing stops that text growing. A ceiling alone is the wrong
 * instrument for the second shape, so the per-session comparison is PRINTED and only the three
 * ceilings are asserted. It is a benchmark before it is a ceiling.
 *
 * THE NUMBER IN THE TASK WAS STALE, AND RE-MEASURING WAS THE FIRST DELIVERABLE
 * §XIII.13 quoted `shio --help` at 14,363 bytes / ~3,600 tokens. It is 4,858 bytes / ~1,215
 * tokens. The old figure predates SH208, which moved every command's text out of `help.mjs` into
 * its own `.txt` — so it measured the source file, not the output. The conclusion the task drew
 * survives the correction (the surface was unmeasured, and that was the defect); the arithmetic
 * comparing it to MCP does not, and would have been wrong by 3x in the CLI's disfavour.
 *
 * WHAT IS COUNTED
 * Characters at the project's conventional 4-chars-per-token ratio (`ShTokenBudget.CHARS_PER_TOKEN`),
 * the same heuristic every `estTokens` on the wire uses — so these numbers are directly comparable
 * with the MCP ceilings sitting six lines above them in the same file. One source of truth for a
 * ceiling, which is the reasoning that file already carries.
 */
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// The properties reader lived here until SH673 needed a second caller. One reader, not two.
import { budgets, need, tokens } from "./support/token-budgets.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const HELP = resolve(HERE, "..", "src", "help");

/** Every help text, keyed by command. `shio` is the global one printed by `shio --help`. */
function helpTexts() {
  const out = new Map();
  for (const file of readdirSync(HELP).filter((name) => name.endsWith(".txt")).sort()) {
    out.set(file.replace(/\.txt$/u, ""), readFileSync(join(HELP, file), "utf-8"));
  }
  return out;
}

test("SH394: `shio --help` stays inside its budget", () => {
  const ceiling = need(budgets(), "cli.help.global");
  const measured = tokens(helpTexts().get("shio"));

  assert.ok(
    measured <= ceiling,
    `shio --help is ~${measured} est. tokens, over its ${ceiling} budget. This is the first thing ` +
      `an agent reads and the only text that is not optional, so it is the one help that is paid ` +
      `whether or not the command it leads to is ever run. Shorten it to the DECISION it informs ` +
      `— which command, and what that command needs — or raise cli.help.global and say why.`
  );
});

test("SH394: no single command's help dominates the surface", () => {
  const ceiling = need(budgets(), "cli.help.max-per-command");
  const over = [...helpTexts()]
    .filter(([command]) => command !== "shio")
    .map(([command, text]) => [command, tokens(text)])
    .filter(([, measured]) => measured > ceiling)
    .sort((left, right) => right[1] - left[1]);

  assert.deepEqual(
    over,
    [],
    `Over cli.help.max-per-command (${ceiling}): ${JSON.stringify(over)}. A command needing more ` +
      `than this is usually two commands wearing one name, or help restating what --help already ` +
      `lists — the same rule max-per-tool applies to MCP, one surface over.`
  );
});

test("SH394: the whole CLI's help has a ceiling, unbounded being the failure mode", () => {
  const ceiling = need(budgets(), "cli.help.all");
  const texts = helpTexts();
  const total = [...texts.values()].reduce((sum, text) => sum + tokens(text), 0);

  assert.ok(
    total <= ceiling,
    `Every help text together is ~${total} est. tokens, over ${ceiling}. Nothing reads all of it ` +
      `in one go — which is exactly why the total needs a ceiling. An unbounded per-command ` +
      `surface is how a CLI grows past the payload it was cheaper than, one command at a time, ` +
      `with every single addition defensible.`
  );
});

test("SH394: the per-session comparison, printed rather than asserted", () => {
  const props = budgets();
  const texts = helpTexts();
  const global = tokens(texts.get("shio"));
  const perTurn = need(props, "mcp.tools.list.total");

  // The smallest honest path from nothing to a correct first call over the CLI: the global text
  // to choose a command, then that command's own flags. `apply` because a desired-state apply is
  // the write the agent surface is built around — not the cheapest help, which would flatter it.
  const firstCall = global + tokens(texts.get("apply"));

  const rows = [1, 3, 10, 30].map((turns) => ({
    turns,
    mcp: perTurn * turns,
    cli: firstCall,
  }));

  const lines = rows
    .map(
      ({ turns, mcp, cli }) =>
        `    ${String(turns).padStart(2)} turn(s)   MCP ${String(mcp).padStart(6)}   ` +
        `CLI ${String(cli).padStart(6)}   ${mcp > cli ? "CLI cheaper" : "MCP cheaper"}`
    )
    .join("\n");

  console.log(
    `\nSH394 — discovery cost, est. tokens (4 chars/token, as on the wire)\n` +
      `  shio --help ${global} · apply --help ${tokens(texts.get("apply"))} · ` +
      `all ${texts.size} help texts ${[...texts.values()].reduce((s, t) => s + tokens(t), 0)}\n` +
      `  tools/list ${perTurn} per turn, every session, written or not\n` +
      `${lines}\n` +
      `  Read this as shapes, not a winner: MCP's cost is per turn and BOUNDED by a ceiling this\n` +
      `  build enforces; the CLI's is per session and UNBOUNDED except by the three lines above.\n` +
      `  A long session favours the CLI on this axis alone — and that axis is not the only one,\n` +
      `  since tools/list also buys schema validation the CLI's prose cannot give.\n`
  );

  // Asserting the ranking would be asserting a product decision, and the numbers move whenever
  // either surface grows. What must not happen is this printing nothing, which is how a benchmark
  // becomes decoration.
  assert.ok(rows.length > 0 && global > 0 && perTurn > 0);
});
