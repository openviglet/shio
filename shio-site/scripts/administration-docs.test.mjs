// @scans prose reason: the subject is a docs page's headings and how its settings are laid out.
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
 * SH1409: the administration page states its settings as tables and its tasks as steps.
 *
 * Members-only reading, signing in on a site's own hostname and serving a site at its own address
 * were each one long bulleted or prose paragraph, so an operator looking up one rule read all of
 * them. The rewrite puts the operator's tasks first and the rules in tables under the anchors other
 * pages cite; this holds both halves.
 */
const PAGE = readFileSync(join(import.meta.dirname, "..", "docs", "src", "content", "docs", "administration-guide.md"), "utf8");
const headings = [...PAGE.matchAll(/^## (.+)$/gmu)].map((m) => m[1]);
const section = (title) => {
  const start = PAGE.indexOf(title);
  const next = PAGE.indexOf("\n## ", start + title.length);
  return PAGE.slice(start, next < 0 ? undefined : next);
};

test("SH1409: the operator's tasks open the page, as numbered steps", () => {
  const tasks = ["Change the default password", "Create an API token", "Make a folder members-only",
    "Serve each site at its own hostname"];
  assert.deepEqual(headings.slice(0, 4), tasks);
  for (const task of tasks) assert.match(section(`## ${task}`), /^1\. /mu, `SH1409: "${task}" lost its steps`);
});

test("SH1409: the rules an operator looks up are tables, not paragraphs", () => {
  for (const title of ["### Members-only pages", "### A site at its own address", "## API tokens", "## Tenants"]) {
    assert.match(section(title), /^\| .+ \|$/mu, `SH1409: ${title} has no reference table`);
  }
  assert.doesNotMatch(section("### Members-only pages"), /^- /mu,
    "SH1409: the members-only rules are back to a bulleted paragraph list");
});
