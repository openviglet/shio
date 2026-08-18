/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH814 — the flag SH780 removed, and the guard SH807 added, over every suite rather than one.
 *
 * ## What was actually true
 *
 * SH780 removed `--test-force-exit` because it was half the race that aborted the process inside
 * libuv, and SH807 added `scripts/test-watchdog.mjs` because removing it also removed what it was
 * hiding: a leaked handle that used to be killed now hangs, so the watchdog names the live handles
 * instead of letting CI burn a 20-minute timeout. Both landed on `cli`'s `test` script, which is
 * where the flake was found. Neither landed anywhere else, and nothing said so.
 *
 * Nine `node --test` invocations exist across the workspace. The watchdog was on one. The flag was
 * on two — `conformance` and `conformance:docs` — and those are precisely the suites that drive a
 * fixture server with live sockets, which is the handle shape that produced `UV_HANDLE_CLOSING` in
 * the first place. So the gate most exposed to the abort was the one still carrying what causes it,
 * and it is also the slowest gate, which makes a one-in-six abort there the most expensive kind.
 *
 * ## Why a lint and not eight edits
 *
 * SH338's rule: when a review finds a decision was applied in one place, the instrument that finds
 * every other place lands before the fixes. The edits are eight lines; what makes them stay correct
 * is that a *tenth* script cannot be added without deciding, here, which of the two it is. Both
 * lists are asserted exactly for that reason — gaining an entry is a reviewable diff with a reason
 * attached, which is the same shape as `ShAgentP1ConformanceTest`'s exception list.
 *
 * ## What it does not check
 *
 * Whether the watchdog *works* — that is `test-watchdog.test.mjs`, which drives a real leak. This
 * one asks only where it is installed, which is the question that was answered wrongly and could
 * not be asked at all at runtime: a `package.json` script is a string a person types.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

// @scans code — it reads `package.json` scripts, which are commands rather than prose (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/** Directories a workspace never puts its own manifests in, and that dwarf the tree if walked. */
const SKIP = new Set(["node_modules", "target", "dist", "build", ".git", ".roadkeep", "store"]);

/** Every `package.json` this repository owns, repo-relative and separator-normalised. */
function manifests(dir = ROOT, at = "") {
  const found = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (SKIP.has(entry.name)) continue;
      found.push(...manifests(join(dir, entry.name), at ? `${at}/${entry.name}` : entry.name));
    } else if (entry.name === "package.json") {
      found.push({ path: at ? `${at}/package.json` : "package.json", file: join(dir, entry.name) });
    }
  }
  return found;
}

/** `<package.json path>#<script name>` for every script that runs node's own test runner. */
function testRunnerScripts() {
  const scripts = [];
  for (const { path, file } of manifests()) {
    let manifest;
    try {
      manifest = JSON.parse(readFileSync(file, "utf8"));
    } catch {
      // A manifest this suite cannot parse is a different defect and belongs to whatever writes it;
      // swallowing it here would be this check reporting a green it did not earn, so say it.
      assert.fail(`SH814: ${path} is not readable JSON, so this lint cannot classify its scripts`);
    }
    for (const [name, command] of Object.entries(manifest.scripts ?? {})) {
      // The runner, not a delegation: `pnpm --filter … run test` reaches one of these by another
      // name, and counting it too would put a package under both lists at once.
      if (/(^|\s)node\s[^&|]*--test(\s|$)/.test(command)) {
        scripts.push({ key: `${path}#${name}`, command });
      }
    }
  }
  return scripts.sort((a, b) => a.key.localeCompare(b.key));
}

test("SH814: every suite that node runs is classified, and the inventory is the deliverable", () => {
  const keys = testRunnerScripts().map((each) => each.key);

  assert.deepEqual(keys, [
    "claude-plugin/package.json#test",
    "cli/package.json#conformance",
    "cli/package.json#conformance:docs",
    "cli/package.json#test",
    "create-shio-app/package.json#test",
    "shio-client/package.json#test",
    "shio-model/package.json#test",
    "shio-react/package.json#test",
    "shio-sections/package.json#test",
    "shio-site/package.json#test",
  ], "SH814: a suite added or removed changes what the two rules below cover, and the whole point "
    + "is that adding one made no decision at all. Update this list in the same commit, and put the "
    + "new script under the right rule while you are here");
});

test("SH814: --test-force-exit is gone, and the ledger's sentence is now true unqualified", () => {
  const forcing = testRunnerScripts()
    .filter((each) => each.command.includes("--test-force-exit"))
    .map((each) => each.key);

  assert.deepEqual(forcing, [],
    "SH814: `--test-force-exit` aborts the process inside libuv when a handle is mid-close — SH780 "
      + "reproduced that twice in 40 runs and measured the flag's benefit at nothing. It survived "
      + `in ${forcing.join(", ")}, which run a live fixture server: the sockets are the handle `
      + "shape that produced the abort, so the gates keeping the flag were the ones least able to "
      + "afford it. The ledger already says the flag is gone; this is what makes that sentence true");
});

test("SH814: a suite with no force-exit installs the watchdog, or a leak ends nothing", () => {
  const unwatched = testRunnerScripts()
    .filter((each) => !/--import\s+\S*test-watchdog\.mjs/.test(each.command))
    .map((each) => each.key);

  assert.deepEqual(unwatched, [],
    "SH814: without `--test-force-exit` a leaked handle no longer kills the run and no longer fails "
      + "it either — it hangs, and CI reports twenty minutes passing. The watchdog is what turns "
      + `that back into a named handle and a red (SH807). Missing from ${unwatched.join(", ")}. `
      + "The two rules are halves of one decision, which is why they are asserted together: "
      + "removing the flag without the guard trades a rare abort for a silent hang");
});
