/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
/**
 * SH1004 — every artefact the projection writes, and the rule for who wins.
 *
 * ## The failure this exists to stop
 *
 * `content-base.json` is the common ancestor of the three-way merge. A field the **projector**
 * writes but the **merger** does not know is either clobbered on the next pull or in conflict on
 * every pull, depending on which side is treated as authoritative — and neither of those announces
 * itself. SH993 projected releases and said how they merge; SH1003 projected open notes and said
 * they are never pushed. Nothing said the rule had to be stated, so the next one would not have to.
 *
 * Notes were the sharpest case and are the reason the rule is written rather than assumed: they are
 * the one projected artefact a person edits in the console **while** the tree holds a copy, and
 * "resolve it in the console with an edited body locally" has no natural answer. SH1003 chose one —
 * the file is a projection and is never read back — and this is where that choice is recorded so a
 * later reader does not have to re-derive it from the absence of a parser.
 *
 * ## Why a declared table rather than a walk of the merger
 *
 * The merger cannot be asked what it does not handle. A field it has never heard of produces no
 * branch, no error and no log line: the question "is this merged?" has no runtime form, which is
 * exactly SH338's shape — the instrument has to be the declaration, and the assertion is that the
 * declaration covers every writer the tree actually has.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src");

/** Where the projection's writers live. */
const WRITERS_MODULE = "content-store.mjs";

/**
 * How each written artefact merges.
 *
 * - `base`   — `per-field` when `content-base.json` holds a fingerprint per field and a concurrent
 *              edit to two different fields is two edits; `whole` when the base holds one hash for
 *              the artefact; `none` when the artefact does not participate at all.
 * - `wins`   — who wins when both sides changed the same thing.
 * - `conflict` — how a caller finds out, or why one cannot arise.
 */
const RULES = {
  writeDoc: {
    base: "per-field",
    wins: "neither: a field both sides changed is reported and the file is left alone",
    conflict: "`conflicts` on the pull report, per field, by address (SH148)",
  },
  writeRemoteSidecar: {
    base: "per-field",
    wins: "neither — a sidecar IS a field (SH191), so it merges as one",
    conflict: "the same per-field conflict its post's other fields raise",
  },
  writeAssetBytes: {
    base: "whole",
    wins: "neither: bytes are compared whole, because half an image is not a merge",
    conflict: "`conflicts` on the pull report, by asset path",
  },
  writeAssetMeta: {
    base: "per-key",
    wins:
      "neither: a key both sides changed keeps the TREE's value and is reported, because the "
      + "tree is what a person edits and a pull that overwrote it would lose the edit silently",
    conflict:
      "`assetMetaConflicts` on the pull report, as `<asset path>#<key>` (SH1032) — the key, not "
      + "the file, since two writers on one image are usually writing different ones",
  },
  writeFolderMarker: {
    base: "none",
    wins: "the server: a marker records that a folder exists, which is the server's fact",
    conflict:
      "none is possible — the file carries no value a person authors, so there is nothing to lose",
  },
  writeSiteMarker: {
    base: "per-key",
    wins:
      "neither: a key both sides changed is reported and the file is left alone. NOT the server, "
      + "which is `writeFolderMarker`'s rule one line up and does not transfer — a folder marker "
      + "records that a folder exists, which is the server's fact, while every key of a site marker "
      + "is something somebody chose",
    conflict:
      "`sites.conflicts` on the pull report, as `<site>#<key>` (SH1122) — the key and not the file, "
      + "for the reason `assetMeta` gives: \"the agent set the url, the curator wrote the "
      + "description\" is two edits that do not touch, and a whole-file hash would call it a "
      + "collision. A site with any conflicted key is left exactly as it is, because a marker is one "
      + "document and writing three of four keys would produce a file that is half each side's with "
      + "nothing saying which",
  },
  writeLocalNotes: {
    base: "per-key",
    wins: "neither: a note both sides changed is reported and neither copy is written",
    conflict: "`notes.conflicts` on the pull report, as `<site>:<key>` (SH161)",
  },
  writeLocalInstanceNotes: {
    base: "none",
    wins:
      "the server, and the tree cannot push back without `--adopt-memory` — the house rules belong "
      + "to the installation that exported them (SH231)",
    conflict: "none is possible: the file is projected and never read back",
  },
  writeOpenNotes: {
    base: "none",
    wins:
      "the server, always. An open note is the instance's record of what somebody asked for, so a "
      + "tree that could edit it would be a second writer of the same row (SH1003)",
    conflict:
      "none is possible: never parsed, never pushed, and a site with nothing open loses the file "
      + "rather than keeping a stale one",
  },
  writeBase: {
    base: "none",
    wins: "the pull that wrote it — this IS the base, not a thing merged against it",
    conflict: "none: it records the agreement rather than taking part in one",
  },
};

test("SH1004: every writer in the projection declares how it merges", async () => {
  const text = await readFile(join(SRC, WRITERS_MODULE), "utf8");
  const found = new Set();
  for (const match of text.matchAll(/^export async function (write[A-Za-z0-9_]+)/gm)) {
    found.add(match[1]);
  }

  assert.deepEqual(
    [...found].sort(),
    Object.keys(RULES).sort(),
    "SH1004: a projection writer is undeclared (or a declared one was renamed). State its rule in "
      + "RULES: whether it participates in content-base.json, who wins a concurrent change, and how "
      + "a conflict is reported — the merger cannot be asked what it has never heard of, so the "
      + "declaration is the instrument",
  );
});

for (const [name, rule] of Object.entries(RULES)) {
  test(`SH1004: ${name}'s rule is complete`, () => {
    assert.ok(["per-field", "per-key", "whole", "none"].includes(rule.base),
      `SH1004: ${name} must say whether it participates in the base, and how`);
    assert.ok(rule.wins?.length > 20,
      `SH1004: ${name} must say who wins in a sentence somebody can act on`);
    assert.ok(rule.conflict?.length > 20,
      `SH1004: ${name} must say how a conflict reaches a caller, or why none can arise`);
  });
}

test("SH1004: an artefact outside the base says so, and says who wins instead", () => {
  const unbased = Object.entries(RULES).filter(([, rule]) => rule.base === "none");

  assert.ok(unbased.length >= 3,
    "SH1004: red by construction — if nothing were outside the base this assertion would be vacuous");
  for (const [name, rule] of unbased) {
    assert.match(rule.wins, /server|pull|belong/i,
      `SH1004: ${name} is outside the three-way merge, so one side has to be authoritative and the `
        + "rule has to name it — the silent alternative is a local edit disappearing on the next pull");
  }
});

test("SH1004: content-files.md states the rules, which is where a reader looks", async () => {
  const doc = await readFile(
    join(dirname(fileURLToPath(import.meta.url)), "..", "..", "docs", "agents", "content-files.md"),
    "utf8",
  );

  assert.match(doc, /## What merges, and who wins/,
    "SH1004: the rule is documented once, in the area file for the projection");
  for (const name of Object.keys(RULES)) {
    assert.ok(doc.includes(name),
      `SH1004: content-files.md does not mention ${name} — a table in a test nobody reads is the `
        + "instrument without the documentation it exists to keep honest");
  }
});
