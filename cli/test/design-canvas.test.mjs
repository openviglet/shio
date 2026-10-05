/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH945 — this repository's own drawings, and the guarantee that a refresh does not delete them.
 *
 * ## What is drawn here, and what deliberately is not
 *
 * Blocks AQ and AR migrated the console: 23 pages, one route tree, the three shapes and the panel
 * taken from the package. Those screens are **not** drawn and should not be — the shell and
 * BENTO-AUTHORING already settled them, so an artboard would re-open a decided question at the cost
 * of days.
 *
 * What is drawn is the five surfaces this round invents, where the layout still has real
 * alternatives and the data model follows from whichever is chosen: revision history with a diff,
 * the translation workspace, release bundles, the agent-supervision inbox, and the asset browser.
 * Each artboard states what it decides and names the direction it did not take; the one rejected
 * direction that needed drawing rather than a sentence — a release as a session-scoped tray — is
 * kept as its own artboard, marked not chosen, so nobody re-proposes it.
 *
 * ## The failure this file exists for
 *
 * These live in `docs/design/` **beside** files a package owns and rewrites on every run.
 * `page-reference.mjs` keeps a consumer's own pages, artboards and annotations by filtering on the
 * `vds-` prefix and re-deriving only its own — so this repository's drawings survive a refresh by
 * a contract, not by luck. A package release that changed that merge would delete every drawing
 * here, and the only sign would be a canvas that quietly opens with fewer pages.
 *
 * So the last test does not read the rule and agree with it: it runs the bin for real against a
 * throwaway copy and compares what came back. That is the SH338 shape — assert the property, not
 * the implementation that currently provides it.
 */
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { cpSync, existsSync, mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// @scans prose — reason: the subject is a JSON declaration and the names of HTML files beside it;
// nothing here reads a sentence as a claim about a rule (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const CANVAS_DIR = "docs/design";
// SH944: the skill arrives through the enabled plugin, so the settings carry what the bin reads.
const SETTINGS = ".claude/settings.json";

/**
 * The five surfaces, by the artboard that settles each — named rather than counted, because a count
 * is satisfied by any five files and the point is *which* five were unsettled.
 */
const SURFACES = {
  "shio-revision-history.dc.html": "revision history with a field-level diff (Block AZ)",
  "shio-translation-workspace.dc.html": "the translation workspace and its coverage board (Block BH)",
  "shio-release-bundle.dc.html": "a release as an addressable entity (Block BA)",
  "shio-agent-inbox.dc.html": "the agent-supervision inbox, grouped by run (Block BB)",
  "shio-asset-browser.dc.html": "the asset browser and where-used (Block BE)",
  "shio-site-list.dc.html": "the sites list redrawn as a list, on the light ground (a proposal)",
  "shio-site-grid.dc.html": "the sites list redrawn as a grid, on the dark ground (a proposal)",
};

/** A direction that was drawn and rejected. Kept, so the argument against it survives with it. */
const NOT_CHOSEN = { "shio-release-tray.dc.html": "a release as a session-scoped tray" };

const require_ = createRequire(join(ROOT, "shio-react", "noop.js"));
const PACKAGE_ROOT = dirname(dirname(require_.resolve("@viglet/viglet-design-system/exports.json")));
const BIN = join(PACKAGE_ROOT, "scripts", "page-reference.mjs");

const canvasAt = (root) => JSON.parse(readFileSync(join(root, CANVAS_DIR, "canvas.json"), "utf8"));

/** This repository's own half of the declaration: everything the package does not own. */
const ours = (canvas) => ({
  pages: (canvas.pages ?? []).filter((page) => !page.id.startsWith("vds-")),
  artboards: (canvas.artboards ?? []).filter((board) => !board.file.startsWith("vds-")),
  annotations: (canvas.annotations ?? []).filter((note) => !note.id.startsWith("vds-")),
});

test("SH945: every surface this round invents has a drawing, and the rejected one was kept", () => {
  const missing = [...Object.keys(SURFACES), ...Object.keys(NOT_CHOSEN)]
    .filter((file) => !existsSync(join(ROOT, CANVAS_DIR, file)))
    .map((file) => `${file} — ${SURFACES[file] ?? NOT_CHOSEN[file]}`);

  assert.deepEqual(
    missing,
    [],
    `SH945: a drawing is gone from ${CANVAS_DIR}:\n${missing.join("\n")}\n`
      + "  These are the surfaces whose layout has real alternatives and whose data model follows"
      + " from the choice — without the drawing, that choice gets made in review of the first pull"
      + " request that implements one. If a surface was genuinely dropped from the plan, retire its"
      + " roadmap line and remove it from SURFACES in the same commit.",
  );
});

test("SH945: the drawings and the canvas agree, in both directions", () => {
  const canvas = ours(canvasAt(ROOT));
  const onDisk = readdirSync(join(ROOT, CANVAS_DIR))
    .filter((file) => file.startsWith("shio-") && file.endsWith(".dc.html"))
    .sort();
  const placed = canvas.artboards.map((board) => board.file).sort();

  assert.deepEqual(
    placed.filter((file) => !onDisk.includes(file)),
    [],
    "SH945: canvas.json places an artboard that is not in docs/design — the canvas opens it as a"
      + " blank frame rather than reporting anything.",
  );
  assert.deepEqual(
    onDisk.filter((file) => !placed.includes(file)),
    [],
    "SH945: a drawing in docs/design is on no page of canvas.json. It opens from the file tree and"
      + " is invisible on the canvas, so a reader sent to the canvas is missing a panel they cannot"
      + " tell is missing.",
  );

  const pages = new Set(canvas.pages.map((page) => page.id));
  assert.deepEqual(
    canvas.artboards.filter((board) => !pages.has(board.page)).map((board) => board.file),
    [],
    `SH945: an artboard is assigned to a page canvas.json does not declare. Declared: ${[...pages]}`,
  );
});

/**
 * The one that cannot be replaced by reading the bin's source.
 *
 * A refresh is a write, so this runs the real thing — not `--check` — against a copy, and then asks
 * what survived. If a design-system release ever stops preserving a consumer's own entries, this is
 * what says so, on the run that upgrades the package rather than months later.
 */
test("SH945: a page-reference refresh leaves this repository's own drawings untouched", () => {
  const root = mkdtempSync(join(tmpdir(), "shio-design-canvas-"));
  try {
    cpSync(join(ROOT, SETTINGS), join(root, SETTINGS));
    cpSync(join(ROOT, CANVAS_DIR), join(root, CANVAS_DIR), { recursive: true });

    const before = ours(canvasAt(root));
    assert.ok(
      before.pages.length > 0 && before.artboards.length > 0 && before.annotations.length > 0,
      "SH945: the copy carries none of this repository's own canvas entries, so the comparison"
        + " below would pass over an empty set",
    );

    const run = spawnSync(process.execPath, [BIN, "--root", root], { encoding: "utf8" });
    assert.equal(run.status, 0, `SH945: the refresh failed:\n${run.stdout}\n${run.stderr}`);

    assert.deepEqual(
      ours(canvasAt(root)),
      before,
      "SH945: a page-reference refresh changed this repository's own pages, artboards or"
        + " annotations. The bin keeps them by filtering on the `vds-` prefix and re-deriving only"
        + " its own; a release that changed that merge deletes every drawing in docs/design, and"
        + " the only visible sign is a canvas that opens with fewer pages.\n"
        + "  Do NOT fix this by re-adding the entries by hand — they would be dropped again on the"
        + " next run. Pin the design-system floor and report it against the package.",
    );

    for (const file of [...Object.keys(SURFACES), ...Object.keys(NOT_CHOSEN)]) {
      assert.ok(
        existsSync(join(root, CANVAS_DIR, file)),
        `SH945: the refresh deleted ${file}. The bin removes only \`vds-\` artboards it no longer`
          + " ships; a drawing of this repository's own must never be one of them.",
      );
    }
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
