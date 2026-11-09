// @scans prose reason: the subject is a docs page's walk-through and headings, read as Markdown.
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
 * SH1407: the replication page has one walk-through from a source URL to a judged replica.
 *
 * The page described each stage at length and never put them in one list, so a reader assembled
 * the round from four sections. The walk-through opens the page as Steps and runs every command of
 * the chain in order; the reference sections after it keep the detail.
 */
const PAGE = readFileSync(join(import.meta.dirname, "..", "docs", "src", "content", "docs", "replication.mdx"), "utf8");

test("SH1407: the page opens on one Steps walk-through that runs the whole chain in order", () => {
  const headings = [...PAGE.matchAll(/^## (.+)$/gmu)].map((m) => m[1]);
  assert.equal(headings[0], "Replicate a site");
  const walk = PAGE.slice(PAGE.indexOf("## Replicate a site"), PAGE.indexOf("## Two modes"));
  assert.match(walk, /<Steps>[\s\S]*<\/Steps>/u, "SH1407: the walk-through is a Steps list");
  const order = ["shio clone", "shio propose", "shio propose --accept", "shio convert", "shio push", "/sites/", "shio judge"]
    .map((step) => walk.indexOf(step));
  assert.ok(order.every((at) => at > 0), `SH1407: a step of the chain left the walk-through: ${order}`);
  assert.deepEqual(order, [...order].sort((a, b) => a - b), "SH1407: the walk-through runs the chain out of order");
});

test("SH1407: the section vocabulary's collection types are named, not counted", () => {
  assert.doesNotMatch(PAGE, /of the seven section types/u,
    "SH1407: the page counted the section types by hand, and the count went stale");
});
