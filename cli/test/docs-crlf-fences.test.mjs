/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH674 — a CRLF page published nothing, and the absence read as a documentation regression.
 *
 * ## What was wrong
 *
 * `fences()` matched ```` ```<lang>\n ````. On a **CRLF** file the `\r` sits exactly where that
 * pattern wants the newline, so the regex matched nothing — not one block, on the whole page. Seven
 * of the thirty-three published pages are CRLF, and `agent-surface.md` and `content-modeling.md`
 * are two of them: between them they publish `GET /api/v2/agent/manifest`,
 * `GET /api/v2/agent/context` and `GET /api/v2/post-type`, three of the four calls
 * `docs-examples.conformance.mjs` names as the discovery surface that must answer 200.
 *
 * ## Why it survived, and why the assertion is here rather than at the gate
 *
 * **It fails as an absence.** An extractor that reads no block reports no subject, and a checker
 * handed no subjects passes — so the gate did not say "this page is unreadable", it said the calls
 * were *"no longer published — this list has outlived its page"*. That is a sentence about the other
 * repository, and it was believed twice: once as SH674's own line, and once as an exception in
 * `red-suites.json` recording that no change in this tree could turn the suite green. The fix was
 * one `\r?` in this tree, and the pages had never stopped publishing anything.
 *
 * The conformance suite cannot hold this line, for the reason it missed it: it needs a running
 * instance, and it measures the *published* corpus, whose line endings are somebody else's commit.
 * A fixture pair is the only form that keeps asking after the real pages are normalised — and they
 * will be, silently, one file at a time. `blueprints.md` came back on its own the moment an
 * unrelated task edited it, because the editor wrote LF; a defect that repairs itself when a file is
 * touched for another reason is one that gets re-filed rather than found.
 */
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, test } from "node:test";

import { extract } from "../conformance/docs-examples.mjs";

/** One page, written verbatim — the discovery call the gate insists a page publishes. */
const PAGE = [
  "# A page",
  "",
  "## One call instead of a session",
  "",
  "```http",
  "GET /api/v2/agent/manifest",
  "```",
  "",
  "The instance describing itself.",
  "",
  "```bash",
  "shio context",
  "```",
  "",
].join("\n");

const roots = [];

after(() => {
  for (const root of roots) rmSync(root, { recursive: true, force: true });
});

/** A docs tree holding one page, written with the given line ending. */
function docsWith(eol) {
  const root = mkdtempSync(join(tmpdir(), "sh674-"));
  roots.push(root);
  mkdirSync(join(root, "docs"), { recursive: true });
  writeFileSync(join(root, "docs", "page.md"), PAGE.replace(/\n/g, eol), "utf8");
  return extract(root);
}

const lf = docsWith("\n");
const crlf = docsWith("\r\n");

const subjectsOf = (result) => result.blocks.flatMap((block) => block.subjects).sort();

test("SH674: a CRLF page yields the same subjects as the identical LF page", () => {
  assert.deepEqual(
    subjectsOf(crlf),
    subjectsOf(lf),
    "the same bytes but for the line ending must extract to the same claims. Before SH674 the "
      + "CRLF side was empty, and an empty extraction is indistinguishable from a page that "
      + "stopped publishing — which is the sentence the gate printed for four calls that were "
      + "there the whole time",
  );
});

test("SH674: and the comparison is not two empties — the LF side really carries the call", () => {
  // Without this, the assertion above passes on an extractor that reads nothing at all, which is
  // the exact shape of the defect it exists to catch. `deepEqual([], [])` is green.
  assert.deepEqual(
    subjectsOf(lf),
    ["GET /api/v2/agent/manifest", "context"],
    "one http subject and one CLI verb, from a page that publishes exactly one of each",
  );
});

test("SH674: the discovery GET is reachable from the CRLF page by name", () => {
  // Named rather than counted: `MUST_ANSWER_200` looks this string up, and a subject that came
  // back with a trailing `\r` would be a different key and would miss in exactly the same way.
  assert.ok(
    subjectsOf(crlf).includes("GET /api/v2/agent/manifest"),
    "the subject must be the bare request line — a trailing carriage return makes it a key "
      + "`requests.has()` cannot find, which fails identically to not publishing it",
  );
});
