// @scans code
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";

/**
 * SH1413: on a wide screen the docs text starts at the navigation's edge.
 *
 * Starlight centres the text column in the viewport, so on a 2000px monitor a band of empty page
 * sat between the left navigation and the text. docs.css overrides the property Starlight's content
 * panel reads for the column's inline margins; this holds both halves, because an upgrade that
 * renames the property would bring the gap back with every line of our CSS still in place.
 */
const DOCS = join(import.meta.dirname, "..", "docs");
const CSS = readFileSync(join(DOCS, "src", "styles", "docs.css"), "utf8");

test("SH1413: docs.css starts the column at the sidebar on a wide screen", () => {
  const block = CSS.slice(CSS.indexOf("@media (min-width: 72rem)"));
  assert.ok(block.length < CSS.length, "SH1413: the wide-screen block is gone from docs.css");
  assert.match(block, /\[data-has-sidebar\]\[data-has-toc\] \.main-pane\s*\{\s*--sl-content-margin-inline: 0 auto;/u,
    "SH1413: the column is centred in the viewport again, leaving a band beside the navigation");
});

test("SH1413: the installed Starlight still reads the property the override sets", () => {
  // The package exports no CommonJS entry, so it is found the way Node walks for one.
  let dir = DOCS;
  while (!existsSync(join(dir, "node_modules", "@astrojs", "starlight", "dist")) && dirname(dir) !== dir) {
    dir = dirname(dir);
  }
  const starlight = join(dir, "node_modules", "@astrojs", "starlight");
  const panel = readFileSync(join(starlight, "dist", "components", "ContentPanel.astro"), "utf8");
  assert.match(panel, /margin-inline: var\(--sl-content-margin-inline/u,
    "SH1413: Starlight no longer sizes the column's margins from --sl-content-margin-inline; "
      + "the override in docs.css does nothing until it names the new property");
});
