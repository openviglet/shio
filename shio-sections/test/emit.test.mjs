/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH95 → SH94 — the drift guard between the vocabulary and the packages that ship it.
 *
 * Why this test exists. The section post-types are authored once here (TypeScript, typed,
 * reviewable) and shipped as canonical JSON inside the blueprint packages, because the
 * running instance reads packages off the classpath and cannot run a JS build. Two copies
 * of anything eventually disagree, so the second copy is generated and this test fails the
 * build the moment the two diverge — the same argument SH130 makes for pinning the MCP
 * wire, applied to a content model.
 *
 * When it fails, the fix is `pnpm -C shio-sections run emit` and a look at the diff.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import { test } from "node:test";

import { TARGETS, files } from "../scripts/emit.mjs";

test("every blueprint that ships the vocabulary carries it byte-for-byte", async () => {
  const expected = files();
  assert.ok(expected.size > 0, "the vocabulary is not empty");

  for (const target of TARGETS) {
    for (const [name, json] of expected) {
      const path = join(target, name);
      const actual = await readFile(path, "utf8").catch(() => null);
      assert.ok(actual !== null, `${path} is missing — run: pnpm -C shio-sections run emit`);
      assert.equal(
        actual,
        json,
        `${path} has drifted from src/post-types.ts — run: pnpm -C shio-sections run emit`,
      );
    }
  }
});

test("the section vocabulary reaches every package that claims to compose sections", () => {
  // Named explicitly rather than globbed: a package that stops shipping the vocabulary is
  // a decision, and it should look like one in this list.
  assert.deepEqual(
    TARGETS.map((path) => path.split(/[\\/]/).at(-2)),
    ["marketing-site", "landing"],
  );
});

test("every emitted file is canonical JSON with a trailing newline", () => {
  for (const [name, json] of files()) {
    assert.match(name, /^[A-Za-z]+\.json$/);
    assert.match(json, /\n$/);
    const parsed = JSON.parse(json);
    // The canonical key order the CLI's diff depends on (SH16/SH63): a reordered file is
    // drift even when it is the same model.
    assert.deepEqual(Object.keys(parsed), [
      "name",
      "title",
      "namePlural",
      "description",
      "workflowPublishEntity",
      "system",
      "fields",
    ]);
    assert.equal(parsed.system, false, "a blueprint never provisions a system post-type");
  }
});
