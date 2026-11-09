/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1402 — the docs page is written with a skill an agent actually reaches.
 *
 * The defect was not a missing rule but a missing route: shio-roadmap-docs told an agent *where* a
 * shipped feature's page goes and nothing told it *how* the page is written, so each ship appended
 * a paragraph to the closest page. A skill nobody is pointed at is the same defect with a file
 * added, so what this holds is the route: the step that writes the page names the skill, the
 * agents.md index lists it, the skill applies the shared method rather than restating it, and it
 * names the lint (SH1401) and the gates a docs change runs.
 */
// @scans prose reason: the subject is skill and index Markdown read as text; the sentences that
// name the skill are what is asserted, so stripping prose would leave nothing (SH740).

import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
const read = (path) => readFileSync(join(ROOT, path), "utf8");
const SKILL = ".claude/skills/shio-docs-write/SKILL.md";

test("SH1402: the shio-docs-write skill exists and applies the shared docs-writing method", () => {
  assert.ok(existsSync(join(ROOT, SKILL)), `${SKILL} is missing; regenerate it with docs-setup`);
  const skill = read(SKILL);
  assert.match(skill, /^name: shio-docs-write$/m);
  assert.match(skill, /Apply `docs-writing` first/, "the skill must point at the shared method, not restate it");
  assert.ok(skill.split("\n").length <= 120, "the generated skill stays under 120 lines (docs-setup's budget)");
});

test("SH1402: the skill names the docs-style lint and every docs gate", () => {
  const skill = read(SKILL);
  for (const gate of [
    "scripts/docs-style.test.mjs",
    "pnpm --filter shio-site test",
    "pnpm test",
    "pnpm conformance:docs",
    "pnpm --filter shio-site build",
  ]) {
    assert.ok(skill.includes(gate), `${SKILL} does not name ${gate}`);
  }
  assert.ok(existsSync(join(ROOT, "shio-site/scripts/docs-style.test.mjs")), "the lint the skill names is gone");
});

test("SH1402: the step that writes a shipped feature's page names the skill", () => {
  const step = read(".claude/skills/shio-roadmap-docs/SKILL.md").match(
    /3\. \*\*Write for the user, not the commit\.\*\*[\s\S]*?(?=\n\d\. \*\*)/,
  );
  assert.ok(step, "shio-roadmap-docs lost its step 3 (Write for the user, not the commit)");
  assert.match(step[0], /`shio-docs-write`/, "step 3 of shio-roadmap-docs must name the shio-docs-write skill");
});

test("SH1402: the agents.md index lists the skill", () => {
  assert.match(read("agents.md"), /\| `shio-docs-write` \|/);
});
