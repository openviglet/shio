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
 * SH1398 — the Visual Editor's page says how the editor saves today.
 *
 * SH335 moved the editor onto the console session and removed its token picker, and the page went
 * on telling readers that "the token authenticates, not your session cookie": a reader followed it
 * looking for a token setting that no longer existed.
 */
const PAGE = readFileSync(join(import.meta.dirname, "..", "docs", "src", "content", "docs", "visual-editor.mdx"), "utf8");

test("SH1398: the editor saves through the console-session merge, and the page says so", () => {
  assert.match(PAGE, /PATCH \/api\/v2\/post-unified\/\{id\}/u,
    "SH1398: the merge the editor actually calls is named");
  assert.match(PAGE, /saves on your console session, with no token to set up/u);
  assert.doesNotMatch(PAGE, /the token authenticates, not your session cookie/u,
    "SH1398: the pre-SH335 sentence is back");
});

/**
 * SH1403: the rewritten page tells a curator which button to press, so every label it names is
 * one the console draws. The page it replaced said **At end** and **Save**, which the console
 * spells "Add to the page" and "Save draft".
 */
test("SH1403: every console label the page bolds is a string the console's English locale has", () => {
  const LOCALES = join(import.meta.dirname, "..", "..", "shio-react", "src", "i18n", "locales", "en");
  const strings = new Set(["visual-editor.json", "content.json", "sidebar.json"]
    .flatMap((file) => Object.values(JSON.parse(readFileSync(join(LOCALES, file), "utf8")))));
  // Bold that is not a console label: concepts the page defines, and the two save outcomes.
  const NOT_LABELS = new Set(["Visual Editor", "draft", "structure", "appearance", "pattern",
    "write", "`DRAFT`"]);
  const howTos = PAGE.slice(PAGE.indexOf("## Open a page"), PAGE.indexOf("## Template annotations"));
  const named = [...howTos.matchAll(/\*\*([^*]+)\*\*/gu)].map((m) => m[1])
    .filter((label) => !NOT_LABELS.has(label) && !label.endsWith(":"));
  assert.ok(named.length >= 15, `SH1403: only ${named.length} labels read; the pattern stopped matching`);
  const missing = [...new Set(named)].filter((label) => !strings.has(label));
  assert.deepEqual(missing, [], "SH1403: the page names a button the console does not draw");
});

test("SH1398: the delivery API's write routes are described as an own front end's, with its token", () => {
  const own = PAGE.slice(PAGE.indexOf("An editor of your own"));
  assert.match(own, /token of \*\*write\*\* scope/u);
  assert.match(own, /PATCH \/api\/v2\/cda\/post\/\{id\}/u);
});
