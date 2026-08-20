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

import { acceptanceBars } from "../src/acceptance.mjs";
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

/**
 * SH688 — the instrument that sat out the question it answers.
 *
 * <p>`audit` is the only instrument that renders in a browser and the only one that returns
 * geometry: it reports overflow in pixels with a CSS selector each. On the `www.viglet.org` replica
 * it printed `NOT RUN — not asked for — pass --browser` while the question being asked was *why does
 * this page look wrong*, and the investigation went to hand-parsing 1.1 MB of HTML and then to
 * asking a human for a screenshot.</p>
 *
 * <p>The honesty was never the problem — naming what it skipped is SH589 working. The default was.
 * `judge` can <em>test</em> whether playwright resolves, and declining the only geometric instrument
 * silently is a decision it should not make on the caller's behalf.</p>
 */
test("SH688: audit runs when playwright resolves, with no flag passed", async () => {
  const asked = [];
  const ctx = judgeCtx({
    // The seam `browser.mjs` reads. A resolvable playwright is the whole precondition; the audit
    // then fails at `launch()` on this stub, which is a DID NOT RUN from the instrument and not a
    // finding — exactly as a missing chromium behaves in the real world.
    import: async (name) => {
      asked.push(name);
      if (name === "playwright") return { chromium: { launch: async () => { throw new Error("no chromium"); } } };
      throw new Error(`unexpected import ${name}`);
    },
  });

  const result = await judge.run({ site: "Acme", checks: "content" }, [], ctx);

  assert.ok(
    asked.includes("playwright"),
    "it asks whether the browser is there rather than waiting to be told (SH688)",
  );
  assert.ok(
    !/not asked for/.test(reasonFor(result, "audit") ?? ""),
    `audit was not declined for want of a flag: ${reasonFor(result, "audit")}`,
  );
});

test("SH688: with no playwright the skip states the install, not a flag", async () => {
  // "not asked for" reads as a preference and "not installed" reads as a fact, and only one of them
  // is actionable. Telling a reader to pass a flag they did not withhold is a message nobody can act
  // on (P6) — and it is what sent the original investigation looking for the wrong thing.
  const ctx = judgeCtx({ import: async () => { throw new Error("Cannot find package 'playwright'"); } });

  const result = await judge.run({ site: "Acme", checks: "content" }, [], ctx);
  const reason = reasonFor(result, "audit");

  assert.match(reason, /playwright is not installed/);
  assert.match(reason, /pnpm add -D playwright/, "the install, because the fact is the actionable one");
  assert.doesNotMatch(reason, /not asked for/, "nobody declined it — the dependency is absent");
});

test("SH688: --no-browser is still a way to decline, and says that is what happened", async () => {
  // The cost is real — a page load each — so the caller keeps the override. What they lose is the
  // silence: a skip now says which of the two things it was.
  const ctx = judgeCtx({ import: async () => ({ chromium: {} }) });

  const result = await judge.run({ site: "Acme", checks: "content", noBrowser: true }, [], ctx);
  const reason = reasonFor(result, "audit");

  assert.match(reason, /declined with --no-browser/);
  assert.doesNotMatch(reason, /not installed/, "it is installed; the caller said no");
});

test("SH688: snapshot keeps saying `not asked for`, because for it that is true", async () => {
  // Not the same case, and the fix must not flatten them: no `--against` means no baseline exists,
  // and there is nothing to test for. Inventing one is worse than abstaining (SH589).
  const ctx = judgeCtx({ import: async () => { throw new Error("no playwright"); } });

  const result = await judge.run({ site: "Acme", checks: "content" }, [], ctx);

  assert.match(reasonFor(result, "snapshot"), /not asked for — pass --against <origin>/);
});

/** A ctx whose verify answers clean and whose diff has no inventory, plus the import seam. */
function judgeCtx(extra) {
  return {
    stdout: sink(),
    env: {},
    cwd: ".",
    fs: {
      async readFile() {
        throw Object.assign(new Error("ENOENT: no such file"), { code: "ENOENT" });
      },
    },
    fetch: async () => ({
      ok: true,
      status: 200,
      async json() {
        return { findings: [], errors: 0, warnings: 0, checked: 3 };
      },
      async text() {
        return "{}";
      },
    }),
    ...extra,
  };
}

function reasonFor(result, name) {
  return (result.runs ?? result.instruments ?? []).find((run) => run.name === name)?.reason;
}

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

  // SH860 — `accepted` where this said `ok`. The command passes the declared bar now, and the two
  // words are not synonyms: `ok` is "no instrument said anything", which is the only green available
  // to a run with no bar, and `accepted` is "every finding is at or below the bar" — a claim about a
  // criterion, and the one a block can close on. `judgeRuns` with no bar still answers `ok`.
  assert.equal(result.verdict, "accepted");
  assert.equal(result.mode, "authorable");
  assert.deepEqual(result.unproven, ["diff", "audit", "snapshot"]);
  assert.ok(seen.some((at) => at.includes("/agent/verify")), `verify was not called: ${seen}`);
  assert.match(stdout.text, /shio judge: Acme — ACCEPTED as authorable from 1 instrument\(s\), 3 unproven/);
  assert.match(stdout.text, /judged against acceptance\.properties as authorable/);
  assert.match(stdout.text, /diff\s+NOT RUN/);
  assert.match(stdout.text, /accepted is a claim about the 1 instrument\(s\) that ran/,
    "and it needs that sentence at least as much as `ok` did: it is the word a block closes on, so a "
      + "reader has to be told which questions were not asked before they close one on it");
});

test("SH860: the round Block Q kept reopening ends accepted, and holds under fidelity", () => {
  // Every criterion met and a pixel difference outstanding, which is the state six declared closes
  // were talked out of. Design equivalence with the source is not an acceptance criterion for
  // authorable mode; it is fidelity's whole criterion, at 0.00%.
  const runs = [
    { name: "verify", ran: true, exitCode: 0, covered: ["/"], flagged: [] },
    { name: "diff", ran: true, exitCode: 0, covered: ["/"], flagged: [] },
    {
      name: "snapshot",
      ran: true,
      exitCode: 1,
      covered: ["/", "/about"],
      flagged: [
        { subject: "/", kind: "snapshot.pixels-differ", why: "12.4% different" },
        { subject: "/about", kind: "snapshot.pixels-differ", why: "9.1% different" },
      ],
    },
  ];
  const bars = acceptanceBars();

  const authorable = judgeRuns(runs, { bars, mode: "authorable" });
  assert.equal(authorable.verdict, "accepted");
  assert.equal(authorable.exitCode, 0);
  assert.equal(authorable.held.length, 0);
  assert.deepEqual(authorable.noted.map((row) => row.bar), ["noted", "noted"],
    "reported, and that is what makes `accepted` an honest word — a finding the bar admitted is one "
      + "somebody decided to admit, and a reader has to see what they bought");
  assert.match(describeJudgement(authorable, { site: "VigR7" }),
    /2 finding\(s\) the bar admits — a task for later, not a reason to hold/);

  const fidelity = judgeRuns(runs, { bars, mode: "fidelity" });
  assert.equal(fidelity.verdict, "finding");
  assert.equal(fidelity.exitCode, 1);
  assert.equal(fidelity.held.length, 2);
});

test("SH860: a kind the bar does not name holds the round and says which file to write it in", () => {
  const judgement = judgeRuns([{
    name: "audit",
    ran: true,
    exitCode: 1,
    covered: ["/"],
    flagged: [{ subject: "/", kind: "audit.something-new", why: "a shape nobody has ruled on" }],
  }], { bars: acceptanceBars(), mode: "authorable" });

  assert.equal(judgement.verdict, "finding");
  assert.equal(judgement.held[0].undeclared, true,
    "accepting by omission is how a criterion stops meaning anything, so the default is the other "
      + "way and `acceptance-bar.test.mjs` is what makes it a build failure rather than a release one");
  assert.match(describeJudgement(judgement), /no bar declares `audit\.something-new`; add one to/);
});

test("SH860: an instrument that failed without naming a subject is never quietly accepted", () => {
  // The one signal that is not a per-subject row. A run that exited non-zero for a reason it could
  // not attribute to a page has said something, and a bar keyed on kinds cannot admit it.
  const judgement = judgeRuns([
    { name: "verify", ran: true, exitCode: 2, covered: ["/"], flagged: [] },
  ], { bars: acceptanceBars(), mode: "authorable" });

  assert.equal(judgement.verdict, "finding");
  assert.equal(judgement.exitCode, 1);
});

test("SH860: a caller that passes no bar gets the pre-SH860 verdict unchanged", () => {
  const runs = [{
    name: "snapshot",
    ran: true,
    exitCode: 1,
    covered: ["/"],
    flagged: [{ subject: "/", kind: "snapshot.pixels-differ", why: "12.4% different" }],
  }];
  const judgement = judgeRuns(runs);

  assert.equal(judgement.verdict, "finding",
    "any finding is red without a bar, which is the only honest answer available to a run that has "
      + "not been told what the criterion is");
  assert.equal("held" in judgement, false);
  assert.equal("mode" in judgement, false);
});
