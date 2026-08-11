/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH660 — a field value that cannot be an id is never looked up.
 *
 * ## The defect, and why it was invisible
 *
 * `file.widget.tsx` renders two different things with one component: a **reference** on another
 * post's field, written by the content browser as `selection.id`, and the **stored file name** on
 * the `File` type's own `FILE` field, written by the uploader. Its resolver assumed the first, so
 * opening any uploaded file issued `GET /v2/post/xp/<name>` and `GET /v2/folder/<name>` — both 404,
 * both swallowed by `.catch(() => null)`, page 200, and the only witness the network tab. It took a
 * probe that reads failed requests to see it (`console-check.mjs`, on the first instance it met
 * that had an uploaded file).
 *
 * ## What is asserted here
 *
 * The decision, not the regex. The cases below are the two real values this console puts in that
 * field — an id the browser wrote and a file name the uploader wrote — plus the near-misses that
 * would make a looser rule wrong. A test that only checked a UUID matched a UUID pattern would
 * pass on a rule that helped nobody.
 *
 * The widgets' own use of it is checked too: the guard is worth nothing if a resolver stops calling
 * it, and that is a one-line regression which reads as a cleanup.
 */

import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

import { isObjectReference } from "../src/lib/object-reference.ts";

test("SH660: the two values this field really holds are told apart", () => {
  assert.equal(
    isObjectReference("4ed6d98a-bc54-46d8-8e33-b03d4a383ea2"),
    true,
    "what the content browser writes: `selection.id`, which the resolver exists to expand into a "
      + "readable path",
  );
  assert.equal(
    isObjectReference("inter-cyrillic-wght-normal-DqGufNeO.woff2"),
    false,
    "and what the uploader writes into a File post's own FILE field — the measured value from the "
      + "run that found this, which produced two 404s and no report",
  );
});

test("SH660: the near-misses, because a looser rule is the way this comes back", () => {
  for (const value of [
    "",
    "   ",
    "not-a-uuid",
    "4ed6d98a-bc54-46d8-8e33",                        // truncated
    "4ed6d98a-bc54-46d8-8e33-b03d4a383ea2-extra",     // suffixed
    "ZZZ6d98a-bc54-46d8-8e33-b03d4a383ea2",           // not hex
    "my-image-4ed6d98a-bc54-46d8-8e33-b03d4a383ea2.png",
  ]) {
    assert.equal(isObjectReference(value), false, `"${value}" is not an object id`);
  }

  assert.equal(isObjectReference(null), false, "an absent value is not a reference");
  assert.equal(isObjectReference(undefined), false);
  assert.equal(
    isObjectReference("  4ed6d98a-bc54-46d8-8e33-b03d4a383ea2  "),
    true,
    "a value that arrived with whitespace is still the id it names — refusing it would turn a "
      + "cosmetic difference into an unresolvable field",
  );
});

test("SH660: both resolvers ask before they fetch", async () => {
  for (const widget of ["file.widget.tsx", "content-select.widget.tsx"]) {
    const source = await readFile(
      new URL(`../src/components/widgets/${widget}`, import.meta.url), "utf8");

    assert.match(
      source,
      /isObjectReference/,
      `SH660: ${widget} resolves a field value against /v2/post/xp and /v2/folder, and both calls `
        + "end `.catch(() => null)` — so a value that cannot be an id costs two failed requests "
        + "and reports nothing. Removing the guard is a one-line regression that reads as a cleanup",
    );
    // Before the fetch, not after: a guard below the first `axios.get` would let the request it
    // exists to prevent go out.
    assert.ok(
      source.indexOf("isObjectReference") < source.indexOf("/v2/post/xp"),
      `${widget}: the guard has to run before the lookup it guards`,
    );
  }
});
