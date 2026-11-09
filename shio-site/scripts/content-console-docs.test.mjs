// @scans prose reason: the subject is a docs page's bold UI labels, compared with the console's locale strings.
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
 * SH1405: the content console page tells a curator which button to press, so the labels in its
 * how-tos are the console's own. The page it replaced said **Purge**, **Save & Close**, **Action in
 * batch**, **Invert**, **Apply** and a "Content type" filter; the console draws "Delete permanently",
 * "Save and Close", "Action in Batch", "Invert Selection", "Carry it out" and "Post type".
 */
const ROOT = join(import.meta.dirname, "..", "..");
const PAGE = readFileSync(join(ROOT, "shio-site", "docs", "src", "content", "docs", "content-console.mdx"), "utf8");
const LOCALES = join(ROOT, "shio-react", "src", "i18n", "locales", "en");
const leaves = (value) => (typeof value === "string" ? [value] : Object.values(value).flatMap(leaves));
const strings = new Set(["content.json", "common.json", "staticFile.json", "home.json", "note.json"]
  .flatMap((file) => leaves(JSON.parse(readFileSync(join(LOCALES, file), "utf8")))));

test("SH1405: every label the how-tos bold is a string the console's English locale has", () => {
  const howTos = PAGE.slice(PAGE.indexOf("## Find a post"), PAGE.indexOf("## Finding your way"));
  // Bold that is not a label: the term the page defines, and the menu's icon.
  const NOT_LABELS = new Set(["draft", "⋯"]);
  const named = [...new Set([...howTos.matchAll(/\*\*([^*]+)\*\*/gu)].map((m) => m[1].replace(/\s+/gu, " ")))]
    .filter((label) => !NOT_LABELS.has(label));
  assert.ok(named.length >= 15, `SH1405: only ${named.length} labels read; the pattern stopped matching`);
  assert.deepEqual(named.filter((label) => !strings.has(label)), [],
    "SH1405: a how-to names a button the console does not draw");
});

test("SH1405: the how-tos come before the screens they use", () => {
  const firstHowTo = PAGE.indexOf("## Find a post");
  const firstReference = PAGE.indexOf("## Finding your way");
  assert.ok(firstHowTo > 0 && firstHowTo < firstReference, "SH1405: the curator's tasks open the page");
  assert.ok((PAGE.slice(firstHowTo, firstReference).match(/<Steps>/gu) ?? []).length >= 5);
});
