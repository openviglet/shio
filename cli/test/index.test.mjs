/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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

  assert.match(stdout, /^shio — the Viglet Shio CLI/);
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

/**
 * SH377 — the help output reaches its last line.
 *
 * <h2>Half of this guard is now structural, and that half is gone (SH208)</h2>
 *
 * SH377 described the failure precisely, and its worse form is the reason SH208 moved the text out
 * of JavaScript entirely. A *single* backtick in a help paragraph is the lucky case: the file fails
 * to parse and `shio` does not load. A paragraph quoting a shell idiom introduces backticks in
 * **pairs**, and a pair parses fine — ``HELP = `…` || true `…` `` is a valid expression whose value
 * is the first fragment. The help was then silently cut: measured at the time, 149 lines instead of
 * 200, with `Common options` and every flag below it simply absent. Nothing failed, and the
 * Commands guard above passed because the section it checks sits above the cut.
 *
 * The text is a `.txt` now, so neither failure has a mechanism: there is no delimiter to close and
 * nothing to truncate. Scanning the source for a raw backtick would be scanning for a shape that
 * cannot occur — `help-source.test.mjs` refuses the shape's *return* instead, which is the only
 * thing left to be wrong about.
 *
 * What stays is the end-to-end half, because it never depended on the cause: the printed output
 * must reach its last line, whatever truncates it.
 */
test("the help output reaches its last line", async () => {
  const { stdout } = await run(process.execPath, [ENTRY, "--help"]);
  assert.match(stdout, /^ {2}-h, --help {11}Show help$/m, "the help output does not reach its last line");
  assert.match(stdout, /^Common options:$/m);
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

/**
 * SH173 — a remote failure prints the SH80 teaching body, not a request URL.
 *
 * The defect this replaces: `shio push --content` died with "Shio CDA 404 for <700 characters
 * of URL>" while the body that named the problem and its fix went unprinted.
 */
test("formatError prints the teaching problem and hides the URL", async () => {
  const { formatError } = await import("../src/format-error.mjs");
  const err = Object.assign(new Error("Shio CDA 400 for http://shio.test/api/v2/agent/batch"), {
    status: 400,
    url: "http://shio.test/api/v2/agent/batch?address=" + "x".repeat(700),
    body: JSON.stringify({
      title: "'Page' does not declare a field named 'contentt'",
      detail: "A post.upsert declares whole state.",
      fix: "Use one of the declared fields below.",
      allowed: ["title", "content"],
      didYouMean: "content",
      at: "ops[3]",
    }),
  });

  const out = formatError(err);

  assert.match(out, /does not declare a field named 'contentt'/);
  assert.match(out, /fix: Use one of the declared fields/);
  assert.match(out, /allowed: title, content/);
  assert.match(out, /did you mean: content/);
  assert.match(out, /at: ops\[3\]/);
  assert.doesNotMatch(out, /xxxxx/, "the URL is diagnostic detail, not the message");
  assert.match(formatError(err, { debug: true }), /url: http/);
});

test("formatError falls back to the status when there is no teaching body", async () => {
  const { formatError } = await import("../src/format-error.mjs");
  const out = formatError(Object.assign(new Error("boom"), { status: 502, url: "http://x", body: "" }));
  assert.match(out, /the server answered 502/);
});

/**
 * SH513 — the one body shape this module could not print, and it is the shape a defect produces.
 *
 * <p>`shio convert` on a real site died with one line: `shio: the server answered 400`. The endpoint
 * had answered with Spring's default body, which is valid JSON carrying none of the six SH80 keys —
 * so it parsed, took the `title ?? …` branch, and skipped every other line, while the raw-body
 * fallback fires only when the body does *not* parse. `path` and `error` were on the wire and
 * nobody saw them. Diagnosing it took a log a 400 never reaches, a probe against an endpoint whose
 * contract I guessed wrong, and a byte-size listing.</p>
 */
test("SH513: a body that parses and teaches nothing still prints what the server said", async () => {
  const { formatError } = await import("../src/format-error.mjs");
  const err = Object.assign(new Error("boom"), {
    status: 400,
    url: "http://shio.test/api/v2/staticfile/upload",
    body: JSON.stringify({
      timestamp: "2026-08-06T12:00:00.000+00:00",
      status: 400,
      error: "Bad Request",
      path: "/api/v2/staticfile/upload",
    }),
  });

  const out = formatError(err);

  assert.match(out, /path: \/api\/v2\/staticfile\/upload/,
    "the field that names the endpoint that refused, which is the whole of the diagnosis");
  assert.match(out, /error: Bad Request/);
  assert.match(out, /the server answered 400/, "and the status still leads, since nothing titled it");
});

test("SH513: a teaching body is unchanged — the fallback is a fallback", async () => {
  const { formatError } = await import("../src/format-error.mjs");
  const out = formatError(Object.assign(new Error("boom"), {
    status: 400,
    url: "http://x",
    // `status` and `path` ride along on a real SH80 problem too (it is RFC 7807-shaped), so a rule
    // that printed the scalars unconditionally would append noise to every teaching error there is.
    body: JSON.stringify({ title: "No site named 'Acme'", fix: "Create it.", status: 400, path: "/x" }),
  }));

  assert.match(out, /No site named 'Acme'/);
  assert.match(out, /fix: Create it\./);
  assert.doesNotMatch(out, /path: \/x/, "a body that taught is printed as a teaching document");
});

test("formatError leaves an ordinary error alone", async () => {
  const { formatError } = await import("../src/format-error.mjs");
  assert.equal(formatError(new Error("no content files under shio/content")),
    "shio: no content files under shio/content\n");
});
