import assert from "node:assert/strict";
import { test } from "node:test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

import { GENERATED, classify, describe } from "../src/emit-guard.mjs";

/**
 * SH218 — what a release's re-emit is allowed to change.
 *
 * The workflow bumps every package, re-runs `emit`, and commits the result. The re-emit step
 * itself already existed; what did not is anybody reading its output. `emit.test.mjs` answers "the
 * tree matches its source" on a **push**, which is after the release has committed — so the one
 * moment nobody checks is the one where a job writes into `shio-app/src/main/resources/` and
 * commits it in the same breath.
 *
 * Asserted on the classifier rather than on a repository, so the rule is checkable without putting
 * a working tree into six different states.
 */

const under = (path) => `${GENERATED}${path}`;

test("SH218: a release that only changes versions passes, and says what it changed", () => {
  const result = classify([
    ` M ${under("blog/files/package.json")}`,
    ` M ${under("landing/files/package.json")}`,
    " M create-shio-app/package.json",
  ].join("\n"));

  assert.deepEqual(result.added, []);
  assert.deepEqual(result.deleted, []);
  assert.equal(result.modified.length, 2,
    "only the generated trees are the subject — the bumped workspace manifests are what the "
      + "release is FOR, and counting them would make every release look surprising");

  const report = describe(result);
  assert.equal(report.ok, true);
  assert.match(report.text, /2 generated file\(s\) changed/,
    "printed on success too: a release that says nothing about what it regenerated leaves a "
      + "commit nobody can read afterwards");
});

test("SH218: a generated file appearing during a release fails it", () => {
  // The case that matters. An overlay somebody edited without running `emit` lands in the release
  // commit — the one commit nobody reviews — and looks like part of the version bump.
  const result = classify([
    `?? ${under("blog/files/app/blog/page.tsx")}`,
    ` M ${under("blog/files/package.json")}`,
  ].join("\n"));

  assert.deepEqual(result.added, [under("blog/files/app/blog/page.tsx")]);
  assert.equal(result.surprising, 1);

  const report = describe(result);
  assert.equal(report.ok, false);
  assert.match(report.text, /run `pnpm -C create-shio-app run emit`/,
    "and the refusal names the command that produces the change properly, so the fix is not a "
      + "guess about what the release wanted");
});

test("SH218: a generated file disappearing fails it too", () => {
  const result = classify(` D ${under("docs/files/app/docs/page.tsx")}`);

  assert.deepEqual(result.deleted, [under("docs/files/app/docs/page.tsx")]);
  assert.equal(describe(result).ok, false);
});

test("SH218: a rename is an addition, not a modification", () => {
  // Porcelain reports `R  old -> new`, and reading that as one path with an arrow in it would
  // classify a renamed generated file as a modification — which is precisely the surprise this
  // exists to catch, arriving in the one shape that looks innocent.
  const result = classify(`R  ${under("blog/files/a.tsx")} -> ${under("blog/files/b.tsx")}`);

  assert.deepEqual(result.added, [under("blog/files/b.tsx")]);
  assert.equal(describe(result).ok, false);
});

test("SH218: nothing outside the generated trees is the guard's business", () => {
  // The release bumps every publishable package and rewrites lockfiles. A guard that refused on
  // those would refuse every release, which is the same as no guard — people would delete it.
  const result = classify([
    " M package.json",
    " M cli/package.json",
    "?? some/new/source/file.mjs",
  ].join("\n"));

  assert.equal(result.surprising, 0);
  assert.deepEqual(result.modified, []);
});

test("SH218: the workflow runs the check, and before it publishes anything", () => {
  // A pure classifier nobody calls is a rule that does not exist. Asserted on the workflow text
  // because that is the only place the wiring lives, and the ordering is half the design: a
  // surprise found after the publish step has already pushed to npm is a surprise nobody can undo.
  const workflow = readFileSync(
    fileURLToPath(new URL("../../.github/workflows/publish-js-sdk.yml", import.meta.url)),
    "utf8",
  );

  const check = workflow.indexOf("run emit:check");
  // The STEP, not the phrase. The first version searched for "npm publish" and found it in a
  // comment thirty lines from the top explaining how `pnpm publish` rewrites `workspace:*` — the
  // SH613 lesson arriving in a YAML file: a scan over raw text cannot tell a rule from a sentence
  // about the rule, and here the sentence came first and made a correct workflow look wrong.
  const publish = workflow.search(/^\s*- name: Publish /m);
  assert.ok(check > 0, "the release does not run the guard at all");
  assert.ok(publish > 0, "no publish step found — this assertion would then be vacuous");
  assert.ok(
    check < publish,
    "the guard runs before anything is published: a release that finds the surprise after "
      + "pushing to the registry has found it too late to act on",
  );
});
