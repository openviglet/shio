// @scans prose reason: the subject is a docs page's headings and the place its procedures sit.
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

/**
 * SH1408: the CLI page keeps its procedures out of the command reference.
 *
 * Changing one field, handing a site over and gating a pipeline were paragraphs between the command
 * tables, so a reader looking a verb up read a procedure and a reader following one jumped between
 * tables. The rewrite puts the four procedures first as numbered steps and leaves the reference as
 * tables only; this holds that split.
 */
const PAGE = readFileSync(join(import.meta.dirname, "..", "docs", "src", "content", "docs", "cli.md"), "utf8");
const headings = [...PAGE.matchAll(/^## (.+)$/gmu)].map((m) => m[1]);

test("SH1408: the four procedures come before the reference", () => {
  assert.deepEqual(headings.slice(0, 4), [
    "Connect to an instance", "Gate a pull request on drift", "Change one field from a script",
    "Hand a site to another instance",
  ]);
  assert.ok(headings.indexOf("The commands") > 3, "SH1408: the command reference moved above a procedure");
});

test("SH1408: the command reference holds no procedure", () => {
  const reference = PAGE.slice(PAGE.indexOf("## The commands"), PAGE.indexOf("## Related pages"));
  assert.doesNotMatch(reference, /^\d+\. /mu, "SH1408: a numbered procedure is back inside the command reference");
  assert.doesNotMatch(reference, /^\*\*[A-Z][^*]+\.\*\* /mu,
    "SH1408: a bold-led how-to paragraph is back between the command tables");
});
