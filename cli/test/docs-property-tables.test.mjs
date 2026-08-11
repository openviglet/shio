/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH663 — the page the property check exists for is the page it did not read.
 *
 * ## What was wrong
 *
 * `docs-vocabulary` holds every documented `shio.*` key against what the product binds, and its
 * extractor took **fenced blocks tagged `properties`**. `configuration-reference.md` has none: its
 * whole content is a Markdown **table**, one row per key. So the page whose entire subject is the
 * property vocabulary contributed nothing to the count that checks it, and three keys binding
 * nothing shipped as published rows and stayed (SH656 removed them, and could not have been told by
 * this gate).
 *
 * The two failures were independent, which is why fixing SH656 did not close this: with the
 * vocabulary correct, a page naming a key that was never bound at all would still pass, because the
 * row is not read.
 *
 * ## Why these cases
 *
 * The rule has to be narrow, because **a table cell is prose**. Each case below is a way a looser
 * rule goes wrong on the real pages, and each was met while writing it rather than imagined:
 *
 * - a backticked key in a *Description* column is a citation, not a documented key;
 * - a bare `max-width` in a property column is a field named **relative** to a prefix the page
 *   states in a fence three lines above, which this reader cannot join;
 * - a table with no property column at all is a comparison matrix, and reading its first column
 *   would invent keys wholesale.
 *
 * The last two are *declined and counted*, never guessed at: the printed count is what makes this
 * gate readable, and one that silently covered some tables would be worse than one covering none.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { propertyRows } from "../conformance/docs-examples.mjs";

test("SH663: a property table's first column is read, and only that column", () => {
  const { rows } = propertyRows([
    "| Property | Default | Description |",
    "|---|---|---|",
    "| `shio.url` | `http://localhost:2710` | The instance's own base URL |",
    "| `shio.cda.preview.ttl-seconds` | `300` | How long a preview token lives |",
    "| `server.port` | `2710` | HTTP listening port |",
  ].join("\n"));

  assert.deepEqual(
    rows.map((row) => row.key),
    ["shio.url", "shio.cda.preview.ttl-seconds", "server.port"],
    "every row's key, in order. Foreign prefixes are the vocabulary check's decision, not this "
      + "reader's — it reports what the page documents",
  );
});

test("SH663: a key cited in a Description column is not a documented key", () => {
  const { rows } = propertyRows([
    "| Property | Default | Description |",
    "|---|---|---|",
    "| `shio.multi-tenant` | `false` | Tenant isolation. Composes with `shio.url` and `shio.cda.*` |",
  ].join("\n"));

  assert.deepEqual(
    rows.map((row) => row.key),
    ["shio.multi-tenant"],
    "SH663: a citation in prose is the page explaining itself. Reading it as a documented key "
      + "would report the page for saying true things about its own settings",
  );
});

test("SH663: a field named under a prefix stated elsewhere is declined and counted", () => {
  const { rows, relativeRows } = propertyRows([
    "| Setting | What it stops | Response |",
    "|---|---|---|",
    "| `max-width` | One URL asking for a 50000x50000 re-encode | **400** |",
    "| `allowed-formats` | Output formats this install does not want | **400** |",
  ].join("\n"));

  assert.deepEqual(rows, [], "a key is dotted; a bare word is a field, and the prefix is elsewhere");
  assert.equal(
    relativeRows,
    2,
    "SH663: counted rather than dropped in silence. `search-caching.md` really is written this "
      + "way — the fence above the table spells `shio.image-transform.max-width` — so guessing the "
      + "prefix would either miss or fabricate a key, and saying nothing would claim coverage",
  );
});

test("SH663: a table with no property column is left alone, and says so when it holds a key", () => {
  const matrix = [
    "| Capability | Shio | Others |",
    "|---|---|---|",
    "| Configured by `shio.multi-tenant` | yes | no |",
  ].join("\n");
  const { rows, skipped } = propertyRows(matrix);

  assert.deepEqual(rows, [], "its first column is a capability, not a key");
  assert.equal(
    skipped,
    1,
    "and it holds a `shio.` key, so it is reported: coverage this reader does not have is a "
      + "number, never an omission",
  );

  const plain = [
    "| Capability | Shio | Others |",
    "|---|---|---|",
    "| Renders a page | yes | no |",
  ].join("\n");
  assert.equal(
    propertyRows(plain).skipped,
    0,
    "a comparison matrix with no key in it is not a property table that got away — reporting it "
      + "would make the number mean 'tables' rather than 'coverage missed'",
  );
});

test("SH663: prose that begins with a pipe is not a table", () => {
  const { rows } = propertyRows([
    "| Property | Default |",
    "not a divider",
    "| `shio.url` | x |",
  ].join("\n"));

  assert.deepEqual(rows, [], "without the `|---|` divider this is lines that start with a pipe");
});
