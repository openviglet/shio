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
 * SH1398 — the Universal Editor's page says how the editor saves today.
 *
 * SH335 moved the editor onto the console session and removed its token picker, and the page went
 * on telling readers that "the token authenticates, not your session cookie": a reader followed it
 * looking for a token setting that no longer existed.
 */
const PAGE = readFileSync(join(import.meta.dirname, "..", "docs", "src", "content", "docs", "universal-editor.md"), "utf8");

test("SH1398: the editor saves through the console-session merge, and the page says so", () => {
  assert.match(PAGE, /PATCH \/api\/v2\/post-unified\/\{id\}/u,
    "SH1398: the merge the editor actually calls is named");
  assert.match(PAGE, /saves on your console session, with no token to set up/u);
  assert.doesNotMatch(PAGE, /the token authenticates, not your session cookie/u,
    "SH1398: the pre-SH335 sentence is back");
});

test("SH1398: the delivery API's write routes are described as an own front end's, with its token", () => {
  const own = PAGE.slice(PAGE.indexOf("An editor of your own"));
  assert.match(own, /token of \*\*write\*\* scope/u);
  assert.match(own, /PATCH \/api\/v2\/cda\/post\/\{id\}/u);
});
