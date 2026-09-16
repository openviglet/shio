/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
import { strict as assert } from "node:assert";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { QUERY_FLAGS } from "../src/commands/find.mjs";
import { parseArgs } from "../src/args.mjs";
import { code } from "./support/source-text.mjs";

/**
 * SH1022 — the fourth surface's half of "one query vocabulary".
 *
 * `ShContentQuerySurfacesTest` compares the address sets the agent listing and the console listing
 * answer with, which is the strong check and the one a Java test can make. It cannot reach `shio
 * find`: that is JavaScript, and a subprocess test of it would prove the flags parse, not that
 * they are *the same words*.
 *
 * So this reads the vocabulary out of the Java record and asserts the CLI spells every filter in
 * it. A word added on one side and forgotten on the other is the failure — the console gains
 * `publisher=`, nobody adds `--publisher`, and the CLI quietly answers a different question than
 * the surface it claims to mirror. Same trick as `blocks-parity.json`, one direction fewer: the
 * record is the authority and this is the mirror.
 */
// @scans code
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const QUERY_JAVA = join(ROOT, "shio-app", "src", "main", "java", "com", "viglet", "shio",
  "api", "query", "ShContentQuery.java");

/**
 * The `KEYS` list, read as the source declares it.
 *
 * Through the shared stripper, which is what `@scans code` means here and not a formality: the
 * record's javadoc names every one of these words in prose, so a scan over the raw text would
 * find `modifiedSince` in a sentence *about* `modifiedSince` and report a vocabulary that is
 * really a paragraph.
 */
function vocabulary() {
  const source = code(readFileSync(QUERY_JAVA, "utf8"));
  const block = /public static final List<String> KEYS = ([\s\S]*?);/u.exec(source)?.[1] ?? "";
  assert.ok(block, "SH1022: ShContentQuery.KEYS moved and this scan lost its subject");
  return [...block.matchAll(/"([a-zA-Z]+)"/gu)].map((match) => match[1]);
}

/** The three words that are paging and ordering rather than filtering. */
const PAGING = ["sort", "page", "size"];

test("SH1022: `shio find` spells every filter the vocabulary declares", () => {
  const filters = vocabulary().filter((key) => !PAGING.includes(key));
  assert.ok(filters.length >= 11, `SH1022: read only ${filters.length} filters`);

  for (const key of filters) {
    assert.ok(QUERY_FLAGS.includes(key),
      `SH1022: the server filters by \`${key}\` and \`shio find\` has no flag for it. A word on `
        + "one surface and not another is how the CLI comes to answer a different question than "
        + "the listing it mirrors.");
  }
  assert.deepEqual([...QUERY_FLAGS].sort(), [...filters].sort(),
    "SH1022: and nothing the other way either — a flag the server does not serve is refused by "
      + "it, which is a 400 the caller cannot act on");
});

test("SH1022: the kebab flag arrives as the server's own key", () => {
  // The whole reason the flags can be the vocabulary: `--modified-since` camel-cases to
  // `modifiedSince`, so the CLI forwards the server's spelling without a translation table —
  // and a translation table is exactly where two vocabularies start to drift.
  const { flags } = parseArgs(["--modified-since", "2026-09-01", "--modified-before",
    "2026-10-01", "--author", "ana"]);
  assert.equal(flags.modifiedSince, "2026-09-01");
  assert.equal(flags.modifiedBefore, "2026-10-01");
  assert.equal(flags.author, "ana");
});

test("SH1022: the command sends only vocabulary words, and walks to the end", async () => {
  const asked = [];
  const module = await import("../src/commands/find.mjs");
  const out = [];
  const code = await module.run(
    { url: "http://shio.test", user: "u", password: "p", modifiedSince: "2026-09-01",
      nonsense: "ignored", limit: "2" },
    ["/blog"],
    {
      env: {},
      stdout: { write: (text) => out.push(text) },
      stderr: { write: () => {} },
      fetch: async (url) => {
        asked.push(new URL(url).search);
        return {
          ok: true,
          status: 200,
          headers: { get: () => "application/json" },
          json: async () => ({ count: 1, truncated: false, results: [
            { address: "post:Blog/blog/hello", type: "Article", state: "DRAFT", title: "Hello" },
          ] }),
          text: async () => "",
        };
      },
    },
  );

  assert.equal(code, 0);
  assert.ok(asked[0].includes("modifiedSince=2026-09-01"), asked[0]);
  assert.ok(asked[0].includes("folder=%2Fblog"), `SH1022: a bare argument is the folder: ${asked[0]}`);
  assert.ok(!asked[0].includes("nonsense"),
    "SH1022: a flag this command does not know is not forwarded — the server would refuse it, "
      + "and its refusal is a worse message than this command's own usage text");
  assert.match(out.join(""), /post:Blog\/blog\/hello/u);
});
