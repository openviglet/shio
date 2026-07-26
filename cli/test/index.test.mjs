/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * The entry point itself. Every other test here imports a command module directly, which
 * means nothing exercised `index.mjs` — and `HELP` is a template literal, so a single
 * backtick in a new help paragraph makes the whole CLI fail to parse while all 66 command
 * tests still pass. That happened while adding `shio verify`.
 *
 * So this runs the real binary as a child process: it catches the parse error, and it also
 * catches the drift that follows it — a command registered in COMMANDS but never mentioned
 * in HELP is a feature nobody can find.
 */
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { test } from "node:test";

const run = promisify(execFile);
const ENTRY = fileURLToPath(new URL("../src/index.mjs", import.meta.url));

test("the CLI parses and prints help", async () => {
  const { stdout } = await run(process.execPath, [ENTRY, "--help"]);

  assert.match(stdout, /^shio — the Shio CMS CLI/);
});

test("every registered command is documented in help", async () => {
  const source = await readFile(ENTRY, "utf8");
  const registry = source.slice(
    source.indexOf("const COMMANDS = {"),
    source.indexOf("const HELP ="),
  );
  const names = [...registry.matchAll(/^\s{2}(\w+):/gm)].map(([, name]) => name);
  assert.ok(names.length > 5, `expected a command registry, found ${names.length} entries`);

  const { stdout } = await run(process.execPath, [ENTRY, "--help"]);
  for (const name of names) {
    assert.ok(
      new RegExp(`^\\s{2}${name}\\b`, "m").test(stdout),
      `command "${name}" is registered but not listed in the Commands section of help`,
    );
  }
});

test("an unknown command exits non-zero and shows help", async () => {
  await assert.rejects(
    () => run(process.execPath, [ENTRY, "nonesuch"]),
    (error) => {
      assert.equal(error.code, 1);
      assert.match(error.stderr, /unknown command "nonesuch"/);
      return true;
    },
  );
});
