/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH589 — one verdict, and the disagreements that produced it.
 *
 * <p>The four cases this is derived from are all the same shape: SH521 (the gate said `ok` on a
 * page `/agent/render` had already scored at nine unstyled classes), SH554 (`diff` reported a loss
 * `verify` could not see, and it was `diff` that was wrong), SH577 (`diff` red and `verify` green,
 * and the mode was the reason), SH586 (`diff` red where `verify` was clean, and the links were
 * leaving the site). Four for four — so the assertion worth writing is not that the composition
 * runs, it is that the <b>disagreement survives the summary</b>.</p>
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { describeJudgement, judgeRuns, subjectOf } from "../src/judge.mjs";
import * as judge from "../src/commands/judge.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** SH586's round, as data: `diff` red on a page `verify` looked at and cleared. */
const SH586 = [
  {
    name: "verify",
    ran: true,
    exitCode: 0,
    covered: ["/about", "/pricing"],
    flagged: [],
  },
  {
    name: "diff",
    ran: true,
    exitCode: 1,
    covered: ["/about", "/pricing"],
    flagged: [{ subject: "/about", why: "3 link(s) now point at the source" }],
  },
];

test("SH589: a red from one and a green from three is a red, and it names which and on what", () => {
  const judgement = judgeRuns([
    ...SH586,
    { name: "audit", ran: true, exitCode: 0, covered: ["/about"], flagged: [] },
    { name: "snapshot", ran: true, exitCode: 0, covered: ["/about"], flagged: [] },
  ]);

  assert.equal(judgement.verdict, "finding");
  assert.equal(judgement.exitCode, 1);
  assert.deepEqual(judgement.disagreements, [{
    subject: "/about",
    flaggedBy: ["diff"],
    clearedBy: ["verify", "audit", "snapshot"],
    why: [{ instrument: "diff", why: "3 link(s) now point at the source" }],
  }]);
});

test("SH589: the disagreement is the headline, above the four reports it summarises", () => {
  const text = describeJudgement(judgeRuns(SH586), { site: "Acme" });

  assert.match(text, /^shio judge: Acme — FINDING from 2 instrument\(s\).*1 disagreement\(s\)/);
  assert.match(text, /WHERE THEY DISAGREED/);
  assert.match(text, /\/about\s+diff say something; verify looked and did not/);
  assert.match(text, /diff: 3 link\(s\) now point at the source/);
  // The instrument lines are the evidence and come after — that is what a reader already had.
  assert.ok(text.indexOf("WHERE THEY DISAGREED") < text.indexOf("instruments:"));
});

test("SH589: an instrument that did not look at a page is not evidence that the page is fine", () => {
  // The distinction the whole join turns on. `audit` looked at one page; it cannot clear the other,
  // and counting silence as agreement would invent a disagreement out of a run's own scope.
  const judgement = judgeRuns([
    { name: "diff", ran: true, exitCode: 1, covered: ["/a", "/b"],
      flagged: [{ subject: "/b", why: "2 fact(s) lost" }] },
    { name: "audit", ran: true, exitCode: 0, covered: ["/a"], flagged: [] },
  ]);

  assert.deepEqual(judgement.disagreements, []);
  assert.deepEqual(judgement.agreed.map((row) => row.subject), ["/b"]);
});

test("SH589: two instruments flagging the same page agree — that is not a disagreement", () => {
  const judgement = judgeRuns([
    { name: "verify", ran: true, exitCode: 1, covered: ["/a"],
      flagged: [{ subject: "/a", why: "required-field-empty (heading)" }] },
    { name: "diff", ran: true, exitCode: 1, covered: ["/a"],
      flagged: [{ subject: "/a", why: "2 fact(s) lost" }] },
  ]);

  assert.deepEqual(judgement.disagreements, []);
  assert.equal(judgement.agreed.length, 1);
  assert.deepEqual(judgement.agreed[0].flaggedBy, ["verify", "diff"]);
});

test("SH589: an instrument that did not run is unproven, never green, and says what it would ask", () => {
  const judgement = judgeRuns([
    { name: "verify", ran: true, exitCode: 0, covered: ["/a"], flagged: [] },
    { name: "diff", ran: true, exitCode: 0, covered: ["/a"], flagged: [] },
    { name: "audit", ran: false, reason: "not asked for — pass --browser" },
    { name: "snapshot", ran: false, reason: "not asked for — pass --against <origin>" },
  ]);

  assert.equal(judgement.verdict, "ok");
  assert.equal(judgement.exitCode, 0, "unproven is a statement about coverage and does not gate");
  assert.deepEqual(judgement.unproven, ["audit", "snapshot"]);

  const text = describeJudgement(judgement, { site: "Acme" });
  assert.match(text, /audit\s+NOT RUN — not asked for/);
  assert.match(text, /it would have answered: is anything wrong with the page a browser renders/);
  // The one sentence that keeps a partial pass from reading as a full one.
  assert.match(text, /ok is a claim about the 2 instrument\(s\) that ran/);
});

test("SH589: nothing ran at all is unproven, not ok", () => {
  const judgement = judgeRuns([{ name: "verify", ran: false, reason: "no instance" }]);
  assert.equal(judgement.verdict, "unproven");
  assert.equal(judgement.exitCode, 0);
});

test("SH589: the two vocabularies are normalised to one subject, home page included", () => {
  // `verify` names an SH79 address and the other three name a served path; a disagreement keyed on
  // two different strings for one page is a disagreement nobody sees.
  assert.equal(subjectOf("post:Acme/about"), "/about");
  assert.equal(subjectOf("post:Acme/"), "/", "the home page, the one form the grammar omits (SH433)");
  assert.equal(subjectOf("/about"), "/about");
  // A site- or folder-scoped finding is real and is not about a page: kept under its own address,
  // so it reaches the report without joining a per-page comparison.
  assert.equal(subjectOf("site:Acme"), "site:Acme");
  assert.equal(subjectOf("folder:Acme/Blog"), "folder:Acme/Blog");
});

test("SH589: the command composes the real run functions and does not fork them", async () => {
  const stdout = sink();
  const seen = [];
  const ctx = {
    stdout,
    env: {},
    cwd: ".",
    // `diff` throws with no inventory, which is a DID NOT RUN and not damage to the replica —
    // the averaging this command exists to refuse.
    fs: {
      async readFile() {
        throw Object.assign(new Error("ENOENT: no such file"), { code: "ENOENT" });
      },
    },
    fetch: async (url) => {
      seen.push(String(url));
      return {
        ok: true,
        status: 200,
        async json() {
          return { findings: [], errors: 0, warnings: 0, checked: 3 };
        },
        async text() {
          return "{}";
        },
      };
    },
  };

  const result = await judge.run({ site: "Acme", checks: "content" }, [], ctx);

  assert.equal(result.verdict, "ok");
  assert.deepEqual(result.unproven, ["diff", "audit", "snapshot"]);
  assert.ok(seen.some((at) => at.includes("/agent/verify")), `verify was not called: ${seen}`);
  assert.match(stdout.text, /shio judge: Acme — OK from 1 instrument\(s\), 3 unproven/);
  assert.match(stdout.text, /diff\s+NOT RUN/);
});
