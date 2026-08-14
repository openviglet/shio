/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH548 — an instrument outside the order that runs it.
 *
 * ## What happened
 *
 * The round before last found that four instruments passed a page that had lost most of its content,
 * because each measures what is *there* and none holds an expectation. SH522's `diff` holds one, and
 * on its first real run it worked: twelve pages compared against the capture that declared them, **35
 * facts lost**, internal links 16 → 0 on eleven of them, text 7073 → 1819 on the home page, exit 1.
 *
 * On the same site `verify` exited **0** having warned `page-unstyled` on ten pages, and `audit` exited
 * **0** having warned `no-landmark`. Both warnings are true and neither is the finding. The gate order
 * in `docs/agents/cli.md` ran `verify --checks projection,replication`, three `--check` drift guards
 * and `verify` — and named neither `diff` nor a replica. So the only red signal on a gutted replica was
 * one a person had to remember to ask for.
 *
 * ## Why the check is two assertions and not one
 *
 * An order that exists only as prose is exactly how this one came to omit `diff`, so asserting the
 * prose alone would be asserting the thing that failed. The second assertion is that the one
 * **runnable** order — the conformance suite, which drives a real capture into a real instance and
 * reads what the route serves — actually invokes it. Prose a reader follows and a run that proves it
 * have to name the same commands, and either one drifting is a hole of the same shape.
 *
 * ## Source scans, deliberately
 *
 * Neither half can be asked at runtime: a CI gate is a sequence somebody types, and the conformance
 * suite needs a live instance this file must not require. What is checkable without either is whether
 * the two documents still say it — which is the property that was false.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// @scans both reason: it holds a Markdown page describing the gate order against the JS that
// implements it, so one side is prose by nature and the other is code (SH740).

const CLI_DOC = fileURLToPath(new URL("../../docs/agents/cli.md", import.meta.url));
const CONFORMANCE = fileURLToPath(
  new URL("../conformance/replication.conformance.mjs", import.meta.url));

/** The sentence that is the order — read, so a rename of the heading fails here rather than silently. */
async function gateOrder() {
  const doc = await readFile(CLI_DOC, "utf8");
  const marker = "**CI gates, in the order they should run:**";
  const start = doc.indexOf(marker);
  assert.ok(
    start >= 0,
    "the gate order is a named paragraph in docs/agents/cli.md; if it moved, move this with it — a "
      + "check that cannot find the order is a check that passes on its absence",
  );
  // To the end of the paragraph: a blank line, which is where the rationale after it begins.
  const end = doc.indexOf("\n\n", start);
  // Whitespace-collapsed, because the doc wraps: `push --check` is split across a newline in it, and
  // a check reading the raw bytes would be a check on the line width.
  return doc.slice(start, end < 0 ? doc.length : end).replace(/\s+/g, " ");
}

test("SH548: the documented gate order names diff", async () => {
  const order = await gateOrder();

  assert.match(
    order,
    /shio diff/,
    "the instrument that holds an expectation has to be in the order that runs it. Without it the "
      + "only command that can fail on a page which renders, returns 200 and has lost most of "
      + `itself is one a person has to remember: ${order}`,
  );
  assert.match(
    order,
    /capture exists/,
    "with its condition, which is the one `diff` already tests for: no capture means no declaration "
      + "to judge against, and the command says so rather than passing",
  );
});

test("SH548: the order is not vacuous — it still names the gates it always did", async () => {
  const order = await gateOrder();

  // A paragraph that lost its other commands would pass the assertion above and mean nothing.
  for (const gate of ["projection,replication", "push --check", "push --content --check",
    "apply --check"]) {
    assert.ok(order.includes(gate), `the order still names ${gate}: ${order}`);
  }
});

test("SH548: the runnable order invokes diff against a live replica", async () => {
  const source = await readFile(CONFORMANCE, "utf8");

  assert.match(
    source,
    /import \* as diff from "\.\.\/src\/commands\/diff\.mjs"/,
    "the conformance run is the only gate in this repository that drives a real capture into a real "
      + "instance and reads what the route serves, so it is where `diff` earns the place the doc "
      + "gives it",
  );
  assert.match(
    source,
    /diff\.run\(/,
    "and it calls it, rather than importing it for a comment",
  );
  assert.match(
    source,
    /comparison\.lost/,
    "asserting on the count that means a defect — `gained` is never a failure, since a Shio layout "
      + "legitimately adds links",
  );
});

test("SH548: `verify`'s severity stays the server's, which is the half deliberately not changed", async () => {
  // Both collapsed: the rule is a javadoc sentence wrapped across two lines with a ` * ` between
  // them, and the doc's own paragraph wraps too.
  const doc = (await readFile(CLI_DOC, "utf8")).replace(/\s+/g, " ");
  const verify = (await readFile(
    fileURLToPath(new URL("../src/commands/verify.mjs", import.meta.url)), "utf8"))
    .replace(/\s+\*?\s*/g, " ");

  // The temptation SH548 records and refuses: a site whose every page draws with classes no
  // stylesheet defines is not warning-shaped, so promote `page-unstyled` to an error. Two reasons not
  // to, and both are load-bearing — the split is the server's and this command says so, and SH521
  // cannot tell a missing theme from a deliberate one. `diff` can, because it knows what the source
  // had, so the two reads together decide what one could not.
  assert.match(
    verify,
    /severity split is the server's .* this command does not second-guess it/,
    "the rule that makes promoting a server warning in the CLI the wrong fix",
  );
  assert.match(
    doc,
    /the warning stays a warning, and the run goes red on the instrument holding the expectation/,
    "and the decision is written down where the order is, because a resolution nobody recorded is a "
      + "question the next round asks again",
  );
});
