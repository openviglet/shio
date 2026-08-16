/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH752 — a block heading over nothing is a sweep nobody ran.
 *
 * ## What went wrong, and how long it went unnoticed
 *
 * When a block's last task ships, one more unit of work follows it: the sweep across `docs-shio`,
 * `shio-site` and the root README, because those surfaces describe a *block* and no per-task edit
 * touches them. The trigger is documented and is a **measurement** — "after every `ship`, ask
 * `stats` whether the block is now empty" — and nothing was obliged to ask.
 *
 * So three blocks sat finished with their headings standing: **I** since 2026-08-04, **Q** since
 * 2026-08-12, **S** since 2026-08-13. Ten days for the first. What it cost was small only by luck:
 * every Block I and Block Q cluster in `COVERAGE.md` was already ✅, so the omission left two stale
 * headings rather than a wrong page. Block S was not lucky — `shio-site/README.md` went on naming
 * SH540 as "all that is left" after it had been retired, which is a public document describing a
 * backlog that no longer existed.
 *
 * ## Why a test and not a habit
 *
 * `roadkeep stats` prints the zero. It printed it every time anybody ran it, and the process says
 * to run it, and that is exactly the shape agents.md calls a preference rather than a rule: **a
 * check nobody is obliged to read is one nobody reads.** `changelog-blocks.test.mjs` is this file's
 * sibling and was written for the same reason one file over — a lint reporting `block.repeated`
 * correctly for seven headings that nothing read until `ship` could no longer write.
 *
 * ## What it does not do
 *
 * It does not verify the sweep *happened*, which is not a claim any file here can make. It asserts
 * the one condition that is decidable: the heading is gone. `block drop <x>` is the last step of
 * the sweep and the only thing allowed to remove it (the guard denies the hand-edit), so a heading
 * still standing is proof the sweep did not finish — and dropping it without doing the sweep is a
 * lie somebody has to type on purpose, which is where a test's reach ends.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

// @scans prose reason: ROADMAP.md is Markdown and its headings are the subject, so there is no
// code half to separate — every line read is prose on purpose (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const ROADMAP = join(ROOT, "docs/ROADMAP.md");

/** A heading that declares a block, at any level — `changelog-blocks.test.mjs`'s rule. */
const BLOCK_HEADING = /^#{2,6}\s+Block\s+(\S+)/;

/** Any heading: a block's region ends at the next one, whatever its level. */
const ANY_HEADING = /^#{1,6}\s/;

/**
 * A task line, by its id rather than by its marker.
 *
 * <p>The markers are configuration (`[markers]` in `roadkeep.toml`) and a set this file must not
 * hold a second copy of — SH245's rule. The id is structure: every line the grammar accepts carries
 * one in bold, and a `- Block L` priority entry or a non-goal bullet carries none.</p>
 */
const TASK_LINE = /^-\s.*\*\*SH\d+\*\*/;

/** Each block heading in file order, with how many task lines its region holds. */
function blocksIn(markdown) {
  const blocks = [];
  let current = null;
  for (const line of markdown.split(/\r?\n/)) {
    const heading = BLOCK_HEADING.exec(line);
    if (heading) {
      current = { label: heading[1], heading: line.trim(), tasks: 0 };
      blocks.push(current);
      continue;
    }
    if (ANY_HEADING.test(line)) {
      current = null;
      continue;
    }
    if (current && TASK_LINE.test(line)) {
      current.tasks += 1;
    }
  }
  return blocks;
}

test("SH752: every block heading in the roadmap stands over at least one open task", async () => {
  const blocks = blocksIn(await readFile(ROADMAP, "utf8"));

  // An empty roadmap is a REACHABLE state, and on 2026-08-16 it was reached: SH750 shipped the
  // last open task and `block drop L` took the last heading with it. This used to read "no
  // headings means the file was reshaped", which was true only while every backlog had work in
  // it — the shape SH672 names, an absolute floor over a corpus meant to empty.
  //
  // The discriminator is the pair, not either half: a RESHAPED file still carries task lines and
  // has stopped yielding headings, where a finished backlog has neither.
  const orphaned = (await readFile(ROADMAP, "utf8")).split(/\r?\n/)
    .filter((line) => TASK_LINE.test(line));
  assert.ok(blocks.length > 0 || orphaned.length === 0,
    `SH752: ${orphaned.length} task line(s) and no block heading, which means the file was `
    + "reshaped and this test is measuring nothing — the failure mode SH696 names (SH674).");
  if (blocks.length === 0) {
    console.log("SH752: the roadmap holds no open task and no block heading — a finished backlog, "
      + "which is a state this reader can now tell apart from a broken one.");
    return;
  }

  const empty = blocks.filter((block) => block.tasks === 0).map((block) => block.heading);
  console.log(`SH752: ${blocks.length} block heading(s), `
    + `${blocks.length - empty.length} holding open work`);

  assert.deepEqual(empty, [],
    "SH752: a block heading is standing over no open task, which means its completion sweep did "
    + "not finish. The sweep is docs-shio's COVERAGE rows, shio-site's backlog verdict, the root "
    + "README's surface table, and `block drop <x>` last — see the `shio-roadmap-docs` skill. Three "
    + "blocks reached this state unnoticed (I, Q, S) and one of them left the site's README naming "
    + "a backlog that had been retired. If the block genuinely has work left, the fix is the "
    + "missing task line, not this list.");
});

test("SH752: the reader is not vacuous — it separates a task line from a heading's other bullets", () => {
  const sample = [
    "## Priority",
    "",
    "- Block L",
    "",
    "## Block L — Agent safety",
    "",
    "- 💭 **SH110** (deps: SH746 ✅) **Something** — because. → §XII.5",
    "",
    "## Block Z — Nothing left",
    "",
    "## Non-goals (do NOT add as tasks)",
    "",
    "- **Shio does not host a model.** No bundled LLM.",
  ].join("\n");

  const blocks = blocksIn(sample);

  assert.deepEqual(blocks.map((block) => [block.label, block.tasks]), [["L", 1], ["Z", 0]],
    "SH752: the reader must count the task line under Block L, must not count the `- Block L` "
    + "priority entry that precedes it, must not read the non-goals bullet as work, and must "
    + "report Block Z as empty. Getting the last one wrong is the whole point of the file.");
});
