/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH941 — the design system's page contract is vendored into this repository, and stays whole.
 *
 * ## What this holds, and what it deliberately does not
 *
 * The catalogue answers "does this component exist". It cannot answer "does this page read as the
 * same product", and that answer lives in two documents and nine artboards that no `node_modules`
 * read surfaces. `viglet-ds-page-reference` writes them here — `.claude/skills/viglet-ds-pages/`
 * so an agent session trips over the contract before the first screen, `docs/design/` so a person
 * opens an artboard straight from the file tree.
 *
 * This asserts that those surfaces are **present and internally coherent**. It does not ask the
 * installed package whether they are current: comparing the two copies means running the bin's
 * `--check`, and that is SH942's gate, not this one. The split matters because the two fail for
 * different reasons and want different instructions — a surface deleted here is fixed by re-running
 * the bin, a surface that drifted upstream is fixed by re-running it *and* reading what changed.
 *
 * ## The coherence that is worth a check without the package
 *
 * `canvas.json` is the one file the bin merges rather than copies: this repository's own pages and
 * artboards are preserved and the `vds-` ones are re-derived. A merge has a failure a copy does not
 * — the declaration and the files it lays out can disagree — and it is invisible, because a canvas
 * naming an artboard that is not on disk opens with a blank frame rather than an error. So the two
 * sides are compared in both directions, which is also what catches the likelier accident: somebody
 * tidying `docs/design/` by hand, against a `SKILL.md` that tells them not to.
 *
 * It lives in the CLI's suite for the reason `vite-config-esm.test.mjs` gives — a reader over the
 * whole repository rather than a test of one package — and it counts what it read, because a walk
 * that silently matches nothing is the failure mode of every lint of this shape (SH322).
 */
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

// @scans prose — reason: the subjects are a Markdown frontmatter block and a JSON declaration, so
// there is no code here to strip; nothing is read as a claim about a rule, only as a field (SH740).
const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const SKILL_DIR = ".claude/skills/viglet-ds-pages";
const CANVAS_DIR = "docs/design";

/** The bin's own payload: the orientation it writes, and the two documents it copies verbatim. */
const SKILL_FILES = ["SKILL.md", "authoring.md", "boundary.md"];

/** Nine when this shipped. The count is asserted as a floor, the names are read off disk. */
const ARTBOARDS_WHEN_SHIPPED = 9;

const at = (...parts) => join(ROOT, ...parts);

function artboardsOnDisk() {
  if (!existsSync(at(CANVAS_DIR))) return [];
  return readdirSync(at(CANVAS_DIR))
    .filter((name) => name.startsWith("vds-") && name.endsWith(".dc.html"))
    .sort();
}

const REFRESH =
  "Run `viglet-ds-page-reference` from the repository root to rewrite the vendored surfaces."
  + " Nothing under those two directories is hand-authored — SKILL.md says so in its own header —"
  + " so a correction belongs in @viglet/viglet-design-system, where every consumer gets it.";

test("SH941: the vendored page reference is on disk, both halves of it", () => {
  const missing = [
    ...SKILL_FILES.map((name) => `${SKILL_DIR}/${name}`),
    `${CANVAS_DIR}/canvas.json`,
  ].filter((path) => !existsSync(at(path)));

  assert.deepEqual(
    missing,
    [],
    `The design system's page contract is not vendored here:\n${missing.join("\n")}\n`
      + "Without it a session has no contract for the shell, the page shapes or the tokens a product"
      + ` claims — the four decisions that make two products built from this package stop looking`
      + ` like one.\n${REFRESH}`,
  );

  const artboards = artboardsOnDisk();
  assert.ok(
    artboards.length >= ARTBOARDS_WHEN_SHIPPED,
    `Found ${artboards.length} vds-*.dc.html artboards in ${CANVAS_DIR}; there were`
      + ` ${ARTBOARDS_WHEN_SHIPPED} when this shipped. The contract drawn is half of what is`
      + ` vendored, and a directory that lost them passes every other check here.\n${REFRESH}`,
  );
});

test("SH941: the skill is discoverable, and names the package version it was written from", () => {
  const skill = readFileSync(at(SKILL_DIR, "SKILL.md"), "utf8");
  const frontmatter = /^---\n([\s\S]*?)\n---\n/.exec(skill);

  assert.ok(
    frontmatter,
    "SKILL.md has no frontmatter block, so Claude Code does not register it and the contract is"
      + " vendored into a directory nothing reads — which is the whole defect SH941 closed, arrived"
      + ` at from the other side.\n${REFRESH}`,
  );

  const body = frontmatter[1];
  for (const key of ["name", "description", "vds-version"]) {
    assert.match(
      body,
      new RegExp(`^${key}:\\s*\\S`, "m"),
      `SKILL.md's frontmatter has no \`${key}\`. \`vds-version\` is the one the bin reads back to`
        + " decide whether refreshing would be a downgrade, so a copy without it cannot be judged"
        + ` against any package at all.\n${REFRESH}`,
    );
  }

  assert.match(
    body,
    /^name:\s*viglet-ds-pages\s*$/m,
    "The skill's name must stay `viglet-ds-pages` — it is the name the bin writes and the name a"
      + ` session loads it by.\n${REFRESH}`,
  );
});

test("SH941: canvas.json and the artboards it lays out agree, in both directions", () => {
  const canvas = JSON.parse(readFileSync(at(CANVAS_DIR, "canvas.json"), "utf8"));
  const onDisk = artboardsOnDisk();

  const declared = (canvas.artboards ?? [])
    .map((artboard) => artboard.file)
    .filter((file) => typeof file === "string" && file.startsWith("vds-"))
    .sort();

  const laidOutButAbsent = declared.filter((file) => !onDisk.includes(file));
  assert.deepEqual(
    laidOutButAbsent,
    [],
    `canvas.json lays out an artboard that is not in ${CANVAS_DIR}:\n`
      + `${laidOutButAbsent.join("\n")}\n`
      + "The canvas opens it as a blank frame rather than reporting anything, which is why this is"
      + ` asserted instead of noticed.\n${REFRESH}`,
  );

  const presentButUnlaid = onDisk.filter((file) => !declared.includes(file));
  assert.deepEqual(
    presentButUnlaid,
    [],
    `${CANVAS_DIR} holds a vds- artboard that canvas.json does not place:\n`
      + `${presentButUnlaid.join("\n")}\n`
      + "It opens from the file tree and is invisible on the canvas, so the drawing a reader is sent"
      + ` to is missing a panel nobody can see is missing.\n${REFRESH}`,
  );

  const pages = new Set(
    (canvas.pages ?? []).map((page) => page.id).filter((id) => typeof id === "string"),
  );
  const orphaned = (canvas.artboards ?? [])
    .filter((artboard) => String(artboard.file ?? "").startsWith("vds-"))
    .filter((artboard) => !pages.has(artboard.page))
    .map((artboard) => `${artboard.file} → page ${JSON.stringify(artboard.page)}`);

  assert.deepEqual(
    orphaned,
    [],
    `A vds- artboard is assigned to a page canvas.json does not declare:\n${orphaned.join("\n")}\n`
      + `The pages it does declare are: ${[...pages].join(", ")}.\n${REFRESH}`,
  );
});

test("SH941: the reader is not vacuous — it would see an artboard that is genuinely absent", () => {
  const onDisk = artboardsOnDisk();

  assert.ok(
    onDisk.includes("vds-shell.dc.html"),
    "The control: the shell artboard must be on disk, or the three tests above are agreeing about"
      + " an empty set. The contract's first instruction is to adopt the shell before the pages, so"
      + ` if any one artboard is load-bearing it is this one.\n${REFRESH}`,
  );

  // The same walk, against a name the package has never shipped: it must come back empty, or the
  // directional comparisons above are matching on something other than what is written down.
  assert.equal(
    onDisk.filter((file) => file === "vds-a-drawing-that-was-never-shipped.dc.html").length,
    0,
    "SH941: the artboard walk matched a file that does not exist — the filter is wrong, and both"
      + " directions of the canvas comparison are reading a set nobody wrote.",
  );
});
