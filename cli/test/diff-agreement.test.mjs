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
import { test } from "node:test";
import { comparePublished } from "../src/diff.mjs";

// @scans prose reason: the subject is a JSON fixture of comparison cases, read whole and parsed;
// it has no comments for a code stripper to remove, so it is read as it stands (SH740).

/**
 * SH988 — `shio diff` and the console's diff agree on what changed.
 *
 * `shio diff` composes over the agent read surface (SH619's decision, and P10's), so it keeps its
 * own comparison rather than calling the console's id-keyed endpoint. What it may not keep is its
 * own answer: both read `shio-app/src/test/resources/diff/agreement.json`, and
 * `ShPostDiffAgreementTest` holds the server to the same cases. The one that found a difference is
 * the key-order case: an object whose keys moved but whose values did not was a change here and
 * not on the server.
 */
const FIXTURE = new URL("../../shio-app/src/test/resources/diff/agreement.json", import.meta.url);

test("SH988: the CLI's comparison matches every shared case the server's does", () => {
  const { cases } = JSON.parse(readFileSync(FIXTURE, "utf8"));
  assert.ok(cases.length > 4, "SH988: the agreement fixture is empty");

  for (const each of cases) {
    const { posts: [post] } = comparePublished([{
      address: "post:site/page",
      draft: { data: each.after },
      live: each.before === null ? null : { data: each.before },
    }]);
    const changed = post.status === "new" ? post.names : post.fields.map((field) => field.field);
    assert.deepEqual([...changed].sort(), [...each.changed].sort(),
      `SH988: shio diff disagrees with the shared case '${each.name}'`);
  }
});
