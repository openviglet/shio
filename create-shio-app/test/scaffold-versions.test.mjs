/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH446 — a scaffold may only declare a version the registry can serve.
 *
 * Network-free on purpose. Whether a range *resolves* is a question only npm can answer, and the
 * SH448 conformance suite asks it by handing the scaffold to `npm install --dry-run`. What this file
 * holds is the half a unit test can: that the tree and the declared table agree, so a version bump
 * in the workspace cannot silently reach a project consumed outside this repository — which is
 * exactly how `^2026.3.1` got into a scaffold whose dependency was published only to `2026.2.1`.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

import { PUBLISHED, rangeFor, unpublished } from "../src/published.mjs";

const TEMPLATE = fileURLToPath(new URL("../template/package.json", import.meta.url));
const WORKSPACE = fileURLToPath(new URL("../../shio-client/package.json", import.meta.url));

const manifest = async (path) => JSON.parse(await readFile(path, "utf8"));

test("SH446: every @viglet range the template declares is the published one", async () => {
  const template = await manifest(TEMPLATE);
  const declared = { ...template.dependencies, ...template.devDependencies };

  for (const [name, range] of Object.entries(declared)) {
    const expected = rangeFor(name);
    if (expected === undefined) continue;
    assert.equal(
      range,
      expected,
      `${name} is declared as ${range} and the registry has ${expected ?? "no version at all"}. `
        + "A scaffold is consumed outside this repository, so the only version it may name is one "
        + "npm can serve — see create-shio-app/src/published.mjs.",
    );
  }
});

test("SH446: and it is deliberately NOT the workspace's version", async () => {
  // The mechanism that caused the defect, pinned as a rule rather than as a value. Reading the
  // range off the workspace answers "what is this checkout building", and that is equal to "what
  // can somebody install" only in the instant after a release — so every version bump re-broke
  // every scaffold, and nothing said so until a human ran `npm install` by hand.
  const template = await manifest(TEMPLATE);
  const workspace = await manifest(WORKSPACE);

  assert.notEqual(
    template.dependencies["@viglet/shio-client"],
    `^${workspace.version}`,
    "if these are equal the release just happened, and published.mjs should say so explicitly "
      + "rather than this test passing by coincidence",
  );
});

test("SH446: a caret range on the published version picks up the next release for free", () => {
  // Why the answer is `^2026.2.1` rather than pinning: caret on a version whose major is >= 1
  // allows everything below the next major, so this resolves today AND resolves to 2026.3.1 the
  // moment that ships. The scaffold does not have to be edited again by the release.
  assert.equal(PUBLISHED["@viglet/shio-client"], "^2026.2.1");
  assert.ok(satisfiesCaret("2026.2.1", "^2026.2.1"), "the version that exists today");
  assert.ok(satisfiesCaret("2026.3.1", "^2026.2.1"), "and the one the workspace is building");
  assert.ok(!satisfiesCaret("2027.0.0", "^2026.2.1"), "but not across a major");
});

test("SH446: the packages that are not published at all are named, not blank", () => {
  // Recorded rather than hidden, because no range is correct for them and a guess would look like
  // a fix. `marketing-site` and `landing` carry a front end that imports the vocabulary, so they
  // cannot be made installable by choosing a different version — only by a release.
  assert.deepEqual(unpublished(), ["@viglet/shio-model", "@viglet/shio-sections"]);
  assert.equal(rangeFor("@viglet/shio-sections"), null);
  assert.equal(rangeFor("next"), undefined, "somebody else's package, on their own release cycle");
});

/** Caret semantics for a major >= 1: >=version <next-major. Enough for the assertion above. */
function satisfiesCaret(version, range) {
  const [major, minor, patch] = version.split(".").map(Number);
  const [lowMajor, lowMinor, lowPatch] = range.replace(/^\^/, "").split(".").map(Number);
  if (major !== lowMajor) return false;
  if (minor !== lowMinor) return minor > lowMinor;
  return patch >= lowPatch;
}
