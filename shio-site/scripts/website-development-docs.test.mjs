// @scans prose reason: the subject is a docs page's headings and section order, read as Markdown.
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
 * SH1406: the templates page reads in Diátaxis order.
 *
 * It had interleaved the template reference with the build and form procedures, and opened on a
 * note about an engine removed years before. The rewrite puts the two procedures first as Steps,
 * the reference after them, and the history last as an explanation.
 */
const PAGE = readFileSync(join(import.meta.dirname, "..", "docs", "src", "content", "docs", "website-development.mdx"), "utf8");
const headings = [...PAGE.matchAll(/^## (.+)$/gmu)].map((m) => m[1]);

test("SH1406: the two procedures open the page, each a Steps list", () => {
  assert.deepEqual(headings.slice(0, 2), ["Build a page from a layout", "Accept form submissions"]);
  for (const [from, to] of [["## Build a page", "## Accept form"], ["## Accept form", "## The render post types"]]) {
    assert.match(PAGE.slice(PAGE.indexOf(from), PAGE.indexOf(to)), /<Steps>[\s\S]*<\/Steps>/u,
      `SH1406: "${from.slice(3)}" is no longer a Steps procedure`);
  }
});

test("SH1406: the engine's history is the closing explanation, not the opening note", () => {
  assert.equal(headings.at(-1), "Related pages");
  assert.equal(headings.at(-2), "Why templates are Handlebars");
  assert.ok(PAGE.indexOf("Nashorn") > PAGE.indexOf("## Proving it renders"),
    "SH1406: the JavaScript engine is described before the reference again");
});
