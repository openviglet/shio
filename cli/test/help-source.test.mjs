/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH208 — help text lives in a file with no syntax, and stays there.
 *
 * ## What this refuses, and why a lint rather than a test
 *
 * A `HELP` written as a template literal breaks the **whole binary** the first time somebody puts
 * a backtick in the prose — and help text about a CLI is prose that names commands, which is prose
 * that attracts backticks. It happened three times in three sessions (`verify --delivery`, `shio
 * report`, and `movedFrom:` in the content section).
 *
 * What makes it worth a lint is the detection path, not the frequency. Only the three tests that
 * spawn the CLI as a subprocess ever noticed: every other CLI test imports the command modules
 * directly, so 155 tests passed against a package whose entry point could not be parsed. A guard
 * that reads the source catches the next one at the moment it is written, on any test run.
 *
 * ## The worse variant, which is why a file and not an escape
 *
 * A *single* backtick is the lucky case — it fails loudly. SH377 measured the other one: a
 * paragraph quoting a shell idiom brings backticks in **pairs**, and a pair parses fine, so the
 * help is silently cut at that point. 149 lines instead of 200, `Common options` and every flag
 * below it gone, no error anywhere. "Escape the backtick" is a rule that has to be remembered
 * every time; a file has no delimiter to close.
 *
 * ## And the fix that would not have worked
 *
 * §IX.9 proposed `String.raw` as the alternative. It changes how backslash escapes are read and
 * has no effect on the delimiter, so a backtick still ends the literal — a fix that would have
 * looked right and removed nothing. Asserted below, so nobody re-derives it.
 */
import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import { test } from "node:test";

const SRC = new URL("../src/", import.meta.url);

/** `const HELP = \`` or `export const HELP = \`` — the shape that broke the binary. */
const LITERAL_HELP = /(?:export\s+)?const\s+HELP\s*=\s*(?:String\.raw)?\s*`/;

async function sources() {
  const found = [];
  for (const dir of ["", "commands/"]) {
    for (const name of await readdir(new URL(dir, SRC))) {
      if (name.endsWith(".mjs")) {
        found.push([`${dir}${name}`, await readFile(new URL(`${dir}${name}`, SRC), "utf8")]);
      }
    }
  }
  return found;
}

test("SH208: no HELP is a template literal", async () => {
  const offenders = (await sources())
    .filter(([, body]) => LITERAL_HELP.test(body))
    .map(([name]) => name);

  assert.deepEqual(
    offenders,
    [],
    `${offenders.join(", ")} declares HELP as a template literal. A backtick in the prose is then ` +
      "a syntax error that stops `shio` loading entirely — not the command that was edited, the " +
      "whole binary — and only the three tests that spawn the CLI would notice. Put the text in " +
      "src/help/<command>.txt and read it with helpText(<command>).",
  );
});

test("SH208: every command's help file exists and is not empty", async () => {
  const missing = [];
  for (const [name, body] of await sources()) {
    const call = body.match(/helpText\("([\w-]+)"\)/);
    if (!call) continue;
    const text = await readFile(new URL(`help/${call[1]}.txt`, SRC), "utf8").catch(() => null);
    if (!text || !text.trim()) missing.push(`${name} -> help/${call[1]}.txt`);
  }
  assert.deepEqual(
    missing,
    [],
    `a helpText(...) call with no file behind it throws at module load, which is the same ` +
      `"the binary does not run" failure in a different costume: ${missing.join(", ")}`,
  );
});

test("SH208: the guard is not vacuous, and String.raw would not have helped", () => {
  assert.ok(
    LITERAL_HELP.test("export const HELP = `shio verify — lint`"),
    "the pattern must match the shape that actually shipped three times",
  );
  assert.ok(
    LITERAL_HELP.test("const HELP = String.raw`shio — the CLI`"),
    "String.raw only changes how backslashes are read; a backtick still ends the literal, so it " +
      "is refused too rather than accepted as a fix that removes nothing",
  );
  assert.ok(
    !LITERAL_HELP.test('export const HELP = helpText("verify");'),
    "and the shape SH208 moved to is not an offender, or the lint matches everything and means " +
      "nothing",
  );
});
