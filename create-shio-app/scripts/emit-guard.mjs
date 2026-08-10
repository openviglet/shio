#!/usr/bin/env node
/**
 * The release's own check on what `emit` produced (SH218).
 *
 * Runs `git status --porcelain` and refuses the release when the regeneration added or removed a
 * generated file. The decision lives in `src/emit-guard.mjs` as a pure function so it can be
 * asserted without a repository in a particular state — this file is the wiring.
 */
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { classify, describe } from "../src/emit-guard.mjs";

const root = fileURLToPath(new URL("../../", import.meta.url));
const porcelain = execFileSync("git", ["status", "--porcelain"], {
  cwd: root,
  encoding: "utf8",
  maxBuffer: 32 * 1024 * 1024,
});

const report = describe(classify(porcelain));
process.stdout.write(report.text);
if (!report.ok) process.exitCode = 1;
