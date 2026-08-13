/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH697 — the tool table nothing read, which is SH663 one vocabulary over.
 *
 * ## What was wrong
 *
 * `docs-vocabulary` holds every documented MCP tool against what the server registers, and its
 * extractor took **JSON fences carrying a `tools/call`**. `mcp.md` has exactly one, so of the twelve
 * tools the page publishes, one was ever checked — and the **table**, which is what a reader
 * consults, was unread. Measured: `shio_nonexistent` dropped into that table left the check green.
 *
 * What it cost: the page published `shio_blueprint` and `shio_forget` as tools. Neither exists, and
 * neither is an oversight — `ShApplyTool` and `ShRememberTool` each record the decision to make that
 * capability a *parameter* rather than pay for a schema on every turn of every session. Two rejected
 * designs shipped as published surface, and `shio_marketplace` shipped without reaching the page at
 * all.
 *
 * ## Why the rule is narrow, and why that is the whole design
 *
 * A table cell is prose. The page now carries a paragraph naming `shio_blueprint` and `shio_forget`
 * **in order to say they are not tools** — so a reader that took every backticked `shio_*` on the
 * page would report the page for the sentence that fixes the defect. Only the first cell, and only
 * under a header that says `Tool`.
 *
 * The other direction has no fixture here because it is not a parsing question: "every registered
 * tool is on the table" lives in `docs-vocabulary.conformance.mjs`, where the product's tool list
 * is. It is the direction a docs-to-product check structurally cannot have — a missing tool
 * contributes no subject, and a checker handed no subject passes.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { toolRows } from "../conformance/docs-examples.mjs";

test("SH697: a tool table's first column is read, and in order", () => {
  const { rows } = toolRows([
    "| Tool | What it does |",
    "|---|---|",
    "| `shio_context` | The one call that replaces a session |",
    "| `shio_write` | A batch of addressed ops, applied atomically |",
    "| `shio_marketplace` | The packages this instance could install |",
  ].join("\n"));

  assert.deepEqual(
    rows.map((row) => row.name),
    ["shio_context", "shio_write", "shio_marketplace"],
    "every row's tool, in order — the page's catalogue, which is the thing a reader copies from",
  );
});

test("SH697: a tool named in a description column is prose, not a documented tool", () => {
  // The real case, not an invented one: `mcp.md` explains that applying a blueprint is
  // `shio_apply`'s parameter rather than a `shio_blueprint` tool. A looser reader would report the
  // page for the sentence that documents the decision.
  const { rows } = toolRows([
    "| Tool | What it does |",
    "|---|---|",
    "| `shio_apply` | Make a desired-state document true — this is where `shio_blueprint` went |",
  ].join("\n"));

  assert.deepEqual(
    rows.map((row) => row.name),
    ["shio_apply"],
    "the first cell is the only position that means *this row is about this tool*",
  );
});

test("SH697: a table naming tools under some other header is declined, and counted", () => {
  // Never guessed at. The plugin's own table on the same page has a `Kind` column and mentions the
  // server, and reading its rows as tools would invent them wholesale — but a silent decline would
  // make partial coverage read as complete, which is SH663's stated reason for the counter.
  const { rows, skipped } = toolRows([
    "| Kind | Contents |",
    "|---|---|",
    "| **MCP server** | `shio_context` and the rest, wired to your instance |",
  ].join("\n"));

  assert.deepEqual(rows, [], "no header saying Tool, so no row is a documented tool");
  assert.equal(skipped, 1, "and the decline is reported rather than silent");
});

test("SH697: a run of pipes that is not a table is not read", () => {
  // A header with no `|---|` divider beneath it is prose that happens to start with a pipe.
  const { rows } = toolRows([
    "| Tool | What it does |",
    "| `shio_context` | no divider above me, so this is not a table |",
  ].join("\n"));

  assert.deepEqual(rows, [], "a table is a header, a divider, then rows");
});
