/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { adviceOf, fieldOf, headlineOf, problemOf } from "../src/lib/problem.ts";

// @scans code — reason: the lint below looks for a CALL that reports a fixed string, and a javadoc
// naming that call must not read as one — which is the same distinction SH952's reader got wrong
// the first time (SH740).

const SRC = fileURLToPath(new URL("../src/", import.meta.url));

/**
 * SH955 — errors are instructions, on both sides of the wall.
 *
 * The backend writes RFC 9457 problem documents carrying `fix`, `allowed`, `didYouMean` and `at`,
 * and `ShProblem` calls a 4xx with none of them incomplete. Twenty-four console save paths caught
 * the rejection and toasted the word **Error** — three of them in the post editor, in `catch`
 * blocks that did not even bind the rejection. So one refusal taught an agent exactly what to do
 * and told a curator nothing, and P6 held on one side of the wall only.
 */

test("SH955: a teaching refusal reaches the curator in the server's own words", () => {
  const problem = problemOf({
    response: {
      status: 409,
      data: {
        title: "That URL is taken",
        detail: "A live post already answers /pricing.",
        fix: "Unpublish the other post, or give this one a different friendly URL.",
        allowed: ["/pricing-2026", "/plans"],
        didYouMean: "/plans",
        at: "data.furl",
      },
    },
  });

  assert.equal(problem.status, 409);
  assert.equal(headlineOf(problem, "Error"), "A live post already answers /pricing.",
    "SH955: `detail` is the long form and the one a person reads first — the title is a label");
  const advice = adviceOf(problem);
  assert.match(advice, /Unpublish the other post/, "SH955: P6 — the fix is the point of the message");
  assert.match(advice, /Did you mean: \/plans/);
  assert.match(advice, /Allowed: \/pricing-2026, \/plans/);
  assert.equal(fieldOf(problem), "furl",
    "SH955: `at` is `data.<field>` on a write, and a form matches it against its own field names");
});

test("SH955: a refusal with only a title still says more than the word Error", () => {
  const problem = problemOf({ response: { status: 400, data: { title: "Name is required" } } });

  assert.equal(headlineOf(problem, "Error"), "Name is required");
  assert.equal(adviceOf(problem), undefined, "SH955: no fix means no second line, not an empty one");
});

test("SH955: a rejection carrying nothing falls back to what the caller would have shown", () => {
  // The floor. This can never say LESS than the fixed string it replaced, or the change would be a
  // regression on the paths where the server said nothing.
  assert.equal(headlineOf(problemOf({}), "Error"), "Error");
  assert.equal(headlineOf(problemOf(new Error("Network Error")), "Error"), "Network Error",
    "SH955: 'Network Error' and 'Error' are different facts, and the first is worth keeping");
  // Spring's plain error body has `message` and no `title`.
  assert.equal(headlineOf(problemOf({ response: { data: { message: "Access denied" } } }), "Error"),
    "Access denied");
});

test("SH955: a long allowed list is capped and says how many it is not showing", () => {
  const problem = problemOf({
    response: { data: { title: "Unknown type", allowed: Array.from({ length: 40 }, (_, i) => `T${i}`) } },
  });

  const advice = adviceOf(problem);
  assert.match(advice, /\+34 more/,
    "SH955: forty post-type names is not a toast, and a truncated list that does not admit it is "
    + "truncated reads as the whole of what is allowed");
});

test("SH955: fieldOf leaves a non-data pointer alone", () => {
  assert.equal(fieldOf(problemOf({ response: { data: { title: "x", at: "folder" } } })), "folder");
  assert.equal(fieldOf(problemOf({ response: { data: { title: "x" } } })), undefined);
});

/* ---------------------------------------------------------------------------------------------
 * The lint. SH322: the grep is the deliverable, because the twenty-four were found by one.
 * ------------------------------------------------------------------------------------------- */

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) walk(path, out);
    else if (/\.(ts|tsx)$/.test(entry)) out.push(path);
  }
  return out;
}

/** `toast.error(t("common.error"))` — a report that discards whatever the server said. */
const FIXED_STRING_REPORT = /toast\s*\.\s*error\s*\(\s*t\(\s*"common\.error"\s*\)\s*\)/;

test("SH955: no save path reports a fixed string in place of the server's refusal", () => {
  const offenders = [];
  let scanned = 0;

  for (const path of walk(SRC)) {
    scanned += 1;
    const relative = path.slice(SRC.length).replace(/\\/g, "/");
    // Comments stripped crudely — enough that this file's own prose, and a `//` note explaining
    // the rule, cannot read as a call (SH206's distinction, SH952's mistake).
    const code = readFileSync(path, "utf8")
      .replace(/\/\*[\s\S]*?\*\//g, "")
      .split("\n")
      .map((line) => line.replace(/\/\/.*$/, ""))
      .join("\n");
    if (FIXED_STRING_REPORT.test(code)) offenders.push(relative);
  }

  assert.ok(scanned > 150, `SH955: walked ${scanned} source files, too few to have looked anywhere`);

  assert.deepEqual(offenders, [],
    "SH955: a save path reports the fixed `common.error` string, throwing away a problem document "
    + "that carries `detail`, `fix`, `allowed` and `didYouMean`. The server calls a 4xx without "
    + "them incomplete; discarding them is the same defect from the other end.\n"
    + "  Use `reportProblem(error, t(\"common.error\"))` from @/lib/problem — the fallback keeps "
    + "the old message for the rejections that genuinely carry nothing.\n  "
    + offenders.join("\n  "));
});

test("SH955: the lint is not vacuous — it matches the call it was written to find", () => {
  assert.ok(FIXED_STRING_REPORT.test('      toast.error(t("common.error"))'),
    "SH955: if this stops matching, the walk above passes over a repository full of them");
  assert.ok(!FIXED_STRING_REPORT.test('reportProblem(error, t("common.error"))'),
    "SH955: the replacement passes the fallback through and must not be reported as the defect");
});
