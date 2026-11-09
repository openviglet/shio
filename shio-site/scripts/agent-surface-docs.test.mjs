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
 * SH1404: the agent surface page reads in Diátaxis order.
 *
 * It had grown one increment at a time, so a procedure, a reference table and the reason for a
 * design sat under one heading. The rewrite puts the one procedure first, as Steps, the reference
 * after it, and the explanation last; a later edit that appends a how-to at the foot, or rationale
 * between the tables, breaks the order this holds.
 */
const PAGE = readFileSync(join(import.meta.dirname, "..", "docs", "src", "content", "docs", "agent-surface.mdx"), "utf8");
const headings = [...PAGE.matchAll(/^## (.+)$/gmu)].map((m) => m[1]);

test("SH1404: the how-to comes first, in Steps, and the explanation last before related pages", () => {
  assert.equal(headings[0], "Make a change and prove it", "SH1404: the page opens on its procedure");
  const howTo = PAGE.slice(PAGE.indexOf("## Make a change"), PAGE.indexOf("## Authenticating"));
  assert.match(howTo, /<Steps>[\s\S]*<\/Steps>/u, "SH1404: the procedure is a Steps list");
  assert.equal(headings.at(-1), "Related pages");
  assert.match(headings.at(-2), /^How the surface relates/u, "SH1404: the explanation closes the page");
  assert.equal((PAGE.match(/<Steps>/gu) ?? []).length, 1,
    "SH1404: a second procedure belongs in its own how-to section before the reference, not inside it");
});

test("SH1404: the op table is still the one place every op is listed", () => {
  const ops = PAGE.slice(PAGE.indexOf("| Op | What it does |"), PAGE.indexOf("Every delete goes to the"));
  const rows = [...ops.matchAll(/^\| `([a-z]+\.[a-z]+)`/gmu)].map((m) => m[1]);
  assert.ok(rows.length >= 25, `SH1404: only ${rows.length} op rows read`);
  for (const op of ["post.upsert", "post.redirect", "release.run", "render.provision", "folder.acl"]) {
    assert.ok(rows.includes(op), `SH1404: ${op} left the op table`);
  }
});
