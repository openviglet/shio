/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH579 — a red suite is a stop, and this is the only thing that may say otherwise.
 *
 * ## The measurement
 *
 * At `6bc4ba8`, clean tree: `pnpm conformance` failed one of five and `pnpm -C cli test` failed one
 * of 1148. Eleven commits landed over the first and eight over the second. **No commit message
 * mentions either**, and neither red run stopped anything.
 *
 * Both alarms were working — and one of them was *wrong*, which is the part that matters. The
 * conformance failure was a real finding (SH577). The debt failure was `assertion-debt.test.mjs`
 * blind to `shio-site/scripts`, so it named two tasks that had shipped two assertions each. Nobody
 * found that out for eight commits, because nobody looked. That is what an undeclared red teaches:
 * red means nothing, whether or not the red is true.
 *
 * ## The rule, and why it needs somewhere to live
 *
 * One line: **a red suite either blocks the next commit or is a declared, dated exception.** The
 * first half is free — it is what a gate already means. The second half is the one that needed
 * building, because without it the rule is unusable: a finding you have just filed as its own task
 * (SH577) is a legitimate reason to keep working, and a rule with no room for it gets ignored on the
 * first honest case rather than the first lazy one.
 *
 * So `red-suites.json` is that room, and it is deliberately hostile to living in:
 *
 * - **Time-boxed.** At most fourteen days from `declared`, and an expired entry fails *here*. A
 *   forgotten exception becomes red rather than quiet, which is the failure mode this whole task is
 *   about.
 * - **Tied to open work.** `task` must be a task the roadmap still lists and the ledger does not.
 *   When SH577 ships, this file goes red until the entry is deleted — so the exception cannot
 *   outlive its reason, and the deletion lands in the same commit as the fix.
 * - **Reasoned in a diff somebody reviews.** Same contract `assertion-debt.test.mjs` uses for its
 *   DEBT list and `ShSilentCatchInventoryTest` for its inventory: the escape hatch is a named list
 *   with the reason beside each entry, never a ceiling. A ceiling lets one task pay it and the next
 *   spend it.
 *
 * ## Why `problems()` is a pure function and the ledger is an argument
 *
 * A check that has only ever been run against a file that satisfies it has not been shown to check
 * anything — which is precisely the shape of the defect this task was filed about, one directory
 * up. So the rules take their world (the ledger, the two docs, the date) as arguments, and the
 * second half of this file feeds them entries that must fail. The real ledger is then one more
 * caller.
 *
 * ## What this file cannot do
 *
 * It cannot run the suites — it is one of them, and it must stay cheap enough to run on every
 * change. It asserts the *ledger*, not the colour. Making red visible without somebody choosing to
 * look is the other half, and that is `.github/workflows/suites.yml`.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

import {
  CYCLE_EXIT, COMPARATOR_GATES, GATES, comparatorCycle, isStale, stamps,
} from "../../scripts/gate-stamp.mjs";
import { GATE_RUNS } from "../../scripts/gates.mjs";

// @scans prose reason: red-suites.json is a ledger read as text, and the reasons and dates
// inside its entries are exactly what this checks (SH740).

const LEDGER = new URL("../../red-suites.json", import.meta.url);
const ROADMAP = new URL("../../docs/ROADMAP.md", import.meta.url);
const CHANGELOG = new URL("../../docs/CHANGELOG.md", import.meta.url);
const BUILD_DOC = new URL("../../docs/agents/build.md", import.meta.url);

/**
 * The suites this rule is about, and the command each one is.
 *
 * <p>A closed set on purpose. "Suite" as free text would let an exception be declared against
 * something nobody runs, which is a way of declaring an exception against nothing. The commands
 * are here rather than only in the doc so the last test can hold the two in agreement.</p>
 */
const SUITES = {
  java: "./mvnw -pl shio-app -am clean test -Dskip.npm=true",
  js: "pnpm test",
  conformance: "pnpm conformance",
  // SH798 — the fourth, and the one that had been declarable against nothing: it is its own job in
  // `suites.yml` and its own red, and until it stamped there was no way to declare it here either.
  docs: "pnpm conformance:docs",
};

/** An exception is temporary by construction. Fourteen days is the whole of it. */
const MAX_DAYS = 14;

/** A reason short enough to fit in this is not a reason. Every other rule here passes a shrug. */
const MIN_WHY = 120;

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Everything wrong with one declared exception, as sentences a reader can act on.
 *
 * @param entry one element of `red-suites.json`'s `exceptions`
 * @param world `{ roadmap, changelog, today }` — the two documents as text and `YYYY-MM-DD`
 * @returns the problems; empty means the exception is well-formed and still live
 */
export function problems(entry, { roadmap, changelog, today }) {
  const out = [];
  const say = (message) => out.push(message);

  if (!Object.hasOwn(SUITES, entry.suite)) {
    say(`\`suite\` is one of ${Object.keys(SUITES).join(", ")} — got ${JSON.stringify(entry.suite)}. `
      + "An exception against a suite nobody runs is an exception against nothing.");
  }
  for (const field of ["test", "task", "declared", "expires", "why"]) {
    if (typeof entry[field] !== "string" || entry[field].trim().length === 0) {
      say(`\`${field}\` is required and must say something`);
    }
  }
  if (out.length) return out; // Everything below reads those fields; one message beats five.

  if (!/^SH\d+$/.test(entry.task)) say("`task` names the task that removes this red, as SH<n>");
  if (!DATE.test(entry.declared)) say("`declared` is YYYY-MM-DD");
  if (!DATE.test(entry.expires)) say("`expires` is YYYY-MM-DD");
  if (out.length) return out;

  const span = (Date.parse(`${entry.expires}T00:00:00Z`)
    - Date.parse(`${entry.declared}T00:00:00Z`)) / 86_400_000;
  if (span <= 0) {
    say("`expires` must be after `declared`");
  } else if (span > MAX_DAYS) {
    say(`an exception runs for at most ${MAX_DAYS} days and this one asks for ${span}. If the red `
      + "will outlive that, it is not an exception — it is the state of the project, and it "
      + "belongs in the roadmap rather than here.");
  }

  if (entry.why.length < MIN_WHY) {
    say(`\`why\` is ${entry.why.length} characters. Say what is red, what the failure actually `
      + "shows, and why it is safe to keep committing over it — the next person's only way to "
      + "judge this entry is what you wrote in it.");
  }

  if (entry.expires < today) {
    say(`this expired on ${entry.expires} and today is ${today}. It was declared on `
      + `${entry.declared} against ${entry.task}. Either that task ships, or the red is fixed, or `
      + "somebody re-declares it with a new date and a reason the first fourteen days were not "
      + "enough. What must not happen is the fourth thing, which is nothing.");
  }

  const open = new RegExp(`^- .*\\*\\*${entry.task}\\*\\*`, "m").test(roadmap);
  // SH781 — the roadmap decides, and the changelog only confirms. `roadkeep ship --part` records
  // the half that landed and LEAVES THE LINE OPEN, so an id can honestly be in both files: the work
  // is still open and the entry naming it still has a reason. Reading the changelog alone made
  // recording progress delete the exception that was tracking the rest of it — the collision SH764
  // named from the other side, where the act of writing a finding down disabled the gate for it.
  const shipped = !open && new RegExp(`^- \\*\\*${entry.task}\\b`, "m").test(changelog);
  if (shipped || !open) {
    say(`${entry.task} is ${shipped ? "shipped" : "not an open task"}, so this exception has `
      + "outlived its reason. Delete the entry — in the same commit as the fix, which is the point "
      + "of tying it to the task rather than to a date alone.");
  }

  return out;
}

/** Today in UTC, as the same `YYYY-MM-DD` the ledger writes. */
function today() {
  return new Date().toISOString().slice(0, 10);
}

async function world() {
  return {
    roadmap: await readFile(ROADMAP, "utf8"),
    changelog: await readFile(CHANGELOG, "utf8"),
    today: today(),
  };
}

test("SH579: every exception declared in red-suites.json is one this rule allows", async () => {
  const { exceptions } = JSON.parse(await readFile(LEDGER, "utf8"));
  assert.ok(Array.isArray(exceptions), "red-suites.json must carry an `exceptions` array");

  const where = await world();
  const bad = exceptions
    .map((entry, i) => [`exceptions[${i}] (${entry.task ?? "no task"})`, problems(entry, where)])
    .filter(([, found]) => found.length);

  // Printed on every run, the way SH527 prints the debt: a number somebody sees beats a number a
  // file holds. Zero is the normal state and the one worth noticing when it changes.
  console.log(`SH579: ${exceptions.length} declared red-suite exception(s) at ${where.today}`
    + exceptions.map((e) => `\n  ${e.suite} — ${e.task}, expires ${e.expires}`).join(""));

  assert.deepEqual(
    bad,
    [],
    "A red suite either blocks the next commit or is a declared, dated exception. These entries "
      + "are neither — see docs/agents/build.md.",
  );
});

/**
 * What each gate last answered here, against what the ledger says about it (SH755).
 *
 * <h2>The half SH579 could not build</h2>
 *
 * <p>The ledger is a <b>claim</b> about which gates are red, and until SH755 nothing compared it
 * to what a gate had actually said. Both directions were invisible: a red with no entry — the state
 * of the project for eight consecutive CI runs — and an entry for a gate that has since gone green.
 * The rule above checks an exception's <em>shape</em>, its date and its task, all of which can be
 * impeccable about a suite nobody has run.</p>
 *
 * <h2>Why this can exist now, when SH579 said it could not</h2>
 *
 * <p>SH579's own note says a suite cannot run the suites — it is one of them. True, and it is not
 * the only way to know: the gate wrappers leave a stamp (`scripts/gate-stamp.mjs`), so the
 * comparison costs two small file reads rather than a second run of anything.</p>
 *
 * <h2>An absent stamp is not a failure, and that is deliberate</h2>
 *
 * <p>A fresh clone has none, and CI runs each gate in a job of its own. "Nobody has run this here"
 * is a true and ordinary state; failing on it would make a first `pnpm test` red for a reason that
 * is not about the code, and a check that cries wolf on arrival is one nobody keeps. So an absent
 * stamp is <b>printed</b>, which is the whole of what this can honestly do about a command nobody
 * typed — and the printing is not decoration: the failure it is here for was found by somebody
 * reading a green gate and never learning the other two were red.</p>
 */
test("SH755: the declared red matches the red the gates actually reported", async () => {
  const { exceptions } = JSON.parse(await readFile(LEDGER, "utf8"));
  const root = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
  const recorded = stamps(root);
  const declared = new Set(exceptions.map((entry) => entry.suite));

  // The gate this test is running INSIDE, which it therefore cannot judge. `js` writes its stamp
  // when the run ends, so the only one on disk here is the PREVIOUS run's — and reading it would
  // make one red `pnpm test` fail the next one for a failure that has already been fixed, then
  // pass on the third. That is a one-run lag that reads as "still broken", which is how an
  // instrument teaches people to re-run it twice and believe neither answer.
  //
  // Nothing is lost: `ShDeclaredRedTest` reads the js stamp from inside the Java gate, and it is
  // the one that caught this. Each comparator judges the other two, which is the same rule the
  // Java side already states for its own row.
  const SELF = "js";

  const lines = GATES.map((gate) => {
    if (gate === SELF) {
      return `  ${gate} — this suite, running now (its stamp is written when it ends)`;
    }
    const seen = recorded[gate];
    if (!seen) {
      return `  ${gate} — no run recorded in this tree`;
    }
    const age = seen.sha ? ` at ${seen.sha.slice(0, 8)}` : "";
    // SH770 — a stamp taken against another commit is not this tree's colour, and saying so is the
    // whole repair: "the last run here was N commits ago" names the run somebody has to do, which
    // neither a colour nor a silence does.
    if (seen.stale) {
      return `  ${gate} — last run${age}, ${seen.when} — NOT this tree, so not a colour`;
    }
    return `  ${gate} — ${seen.red ? "RED" : "green"}${age}, ${seen.when}`
      + `${declared.has(gate) ? ", declared" : ""}`;
  });
  console.log(`SH755: the gates, as last run here\n${lines.join("\n")}`);

  // SH818 — this comparator cannot see its OWN gate (that is the SELF rule above), so the cycle it
  // can be half of is invisible to it by construction. The stamp on disk for `js` is the previous
  // run's, which is exactly the record that would say so: not a colour for judging, and perfectly
  // good for asking "were we here last time too". Printed rather than asserted, because both gates
  // being red is a genuine red until somebody reads the two logs and finds nothing in them.
  if (comparatorCycle({ ...recorded, [SELF]: recorded[SELF] })) {
    console.log(CYCLE_EXIT);
  }

  // SH770 — judged on the runs that are about this tree. A stale stamp is excluded from BOTH
  // directions, which is what makes it read as an absent one: a stale red must not block a commit
  // over a failure that may already be fixed, and a stale green must not retire a declaration.
  const fresh = (gate) => (recorded[gate]?.stale ? undefined : recorded[gate]);
  const judged = GATES.filter((gate) => gate !== SELF);
  const undeclared = judged.filter((gate) => fresh(gate)?.red && !declared.has(gate));
  const stale = [...declared].filter((gate) =>
    gate !== SELF && fresh(gate) && !fresh(gate).red);

  assert.deepEqual(undeclared, [],
    "SH755: this gate answered red here and the ledger says nothing about it. That is the third "
    + "state SH579 says does not exist, and it is the one the project spent eight CI runs in — "
    + "every one of them a push over a red nobody had declared. Fix the suite, or declare it in "
    + "red-suites.json against an open task.");

  assert.deepEqual(stale, [],
    "SH755: the ledger declares this gate red and it answered GREEN here. An exception outliving "
    + "its red is how a suite stops being a gate: the next real failure is already excused. Delete "
    + "the entry — and if it went green for a reason nobody understands, that is a task, not a "
    + "line in a ledger.");
});

test("SH770: a stamp from another commit is not this tree's colour, in either direction", () => {
  // The measurement that filed this: `.gates/conformance.json` held `red: true` at 4b20d8e0 while
  // HEAD was 42d02432, two commits later, and the Java gate failed over it. It was right by luck —
  // the defect happened to still be open.
  assert.equal(isStale({ sha: "4b20d8e0" }, "42d02432"), true,
    "a stale RED blocks a commit over a failure that may already be fixed, which teaches people to "
    + "re-run until it clears. A stale GREEN is worse: it reads as `this tree is green`, which is "
    + "the one sentence SH755 exists to stop somebody believing");
  assert.equal(isStale({ sha: "4b20d8e0" }, "4b20d8e0"), false,
    "and a stamp at HEAD is exactly what a colour is");

  // Both unknowns, and the same answer for the same reason: staleness invented from an absence is
  // the mistake in the other direction.
  assert.equal(isStale({}, "4b20d8e0"), false, "a stamp with no sha predates the field");
  assert.equal(isStale({ sha: "4b20d8e0" }, null), false, "and a checkout with no git cannot ask");

  // And the half that matters to a reader: a stale stamp is excluded from BOTH lists, which is
  // what makes it read as the absent one it effectively is.
  const declared = new Set(["conformance"]);
  const world = {
    java: { red: true, stale: true },
    conformance: { red: false, stale: true },
  };
  const fresh = (gate) => (world[gate]?.stale ? undefined : world[gate]);
  assert.deepEqual(GATES.filter((gate) => fresh(gate)?.red && !declared.has(gate)), [],
    "a red recorded against another commit does not accuse this one");
  assert.deepEqual([...declared].filter((gate) => fresh(gate) && !fresh(gate).red), [],
    "and a green recorded against another commit does not retire a declaration");
});

/**
 * SH798 — the assertion that would have caught a gate with no wrapper.
 *
 * <p>`docs` was a gate in every sense that matters — its own job in `suites.yml`, its own trigger,
 * its own input, its own red — and it ran `node --test` directly, so it wrote no stamp. Nothing said
 * so. It was simply absent from `GATES`, and a comparator cannot miss a gate it has never heard of:
 * for twelve commits it carried the whole coverage debt while both readers reported the tree
 * honestly and had nothing to say about it (SH797).</p>
 *
 * <p>So the rule is <b>a key in `GATES` has a wrapper that stamps it</b> — and `java` is named here
 * as the one that cannot, rather than quietly skipped. That exclusion is the subject of SH800: it is
 * correct (a suite cannot know whether the tests after it passed) and it is why the fastest gate,
 * the one somebody actually types, is the one neither comparator judges.</p>
 */
test("SH798: every gate has a row that runs it, with no exception left", async () => {
  const scripts = await readFile(new URL("../../scripts/gate-stamp.mjs", import.meta.url), "utf8");
  const runner = await readFile(new URL("../../scripts/run-gate.mjs", import.meta.url), "utf8");

  // SH802 — four wrappers became one runner over a table, so the question changed shape and not
  // substance: a gate with no row cannot be run through the thing that stamps, which is the same
  // silence as a gate with no wrapper.
  const rowless = GATES.filter((gate) => !Object.hasOwn(GATE_RUNS, gate));
  assert.deepEqual(
    rowless,
    [],
    "a gate is declared in GATES and has no row in `gates.mjs`, so nothing can run it through the "
      + "runner that stamps — and both comparators will report it as `no run recorded in this "
      + "tree` forever, which is indistinguishable from nobody having run it. That is how the docs "
      + "gate carried twelve commits of debt unremarked.\nAdd the row.",
  );
  assert.deepEqual(
    Object.keys(GATE_RUNS).filter((key) => !GATES.includes(key)),
    [],
    "and the other way: a row nothing declares as a gate stamps a key no comparator reads",
  );
  assert.match(runner, /stamp\(ROOT, key, code\)/,
    "the runner stamps under the key it was given — a runner that hardcoded one would stamp every "
      + "gate as that one, which is this table's failure mode rather than the wrappers'");

  // SH800 — this used to end `and java is named as the one that cannot`, with a note that SH800
  // would say what happens to that exclusion. It says: the exclusion was never about stamping, it
  // was about a comparator reading its own gate. Now that all four stamp, what remains is one SELF
  // per comparator — `java` here, `js` there — and every gate is judged by exactly the other one.
  assert.deepEqual(
    [...new Set(GATES)].filter((gate) => gate === "java" || gate === "js").sort(),
    ["java", "js"],
    "the two SELF gates are the two that host a comparator; a third would need a third reader",
  );
  assert.match(scripts, /export const GATES/, "GATES is read from the shared module, not restated");
});

test("SH755: the comparator fires in both directions, and an absent stamp fires in neither", () => {
  // Driven against a fabricated world for the reason the rules test below is: the real stamps are
  // whatever the last run left, so an assertion about them would be a test of this afternoon.
  const declared = new Set(["conformance"]);
  const world = {
    java: { red: true },
    js: { red: true },
    conformance: { red: false },
    // SH798 — the fourth gate, green and undeclared, which is the ordinary state and therefore the
    // one that must move neither list. Adding it here was not optional: the last assertion requires
    // the fabricated world to cover every gate, so a new one arriving fails this test rather than
    // being silently unjudged — which is exactly the hole `docs` itself had.
    docs: { red: false },
  };
  const judged = GATES.filter((gate) => gate !== "js");

  const undeclared = judged.filter((gate) => world[gate]?.red && !declared.has(gate));
  const stale = [...declared].filter((gate) => gate !== "js" && world[gate] && !world[gate].red);

  assert.deepEqual(undeclared, ["java"],
    "a red gate with no entry has to be named — that is the whole finding SH755 records");
  assert.deepEqual(stale, ["conformance"],
    "and an entry whose gate has gone green has to be named too, or the exception outlives its red");
  assert.ok(!undeclared.includes("js"),
    "SH755: `js` is the gate this very suite runs inside, and its stamp on disk is the PREVIOUS "
    + "run's — judging it here makes one red run fail the next one for a failure already fixed. "
    + "ShDeclaredRedTest reads it from the Java gate, which is where it can be true.");
  assert.deepEqual(GATES.filter((gate) => world[gate] === undefined), [],
    "the gate nobody ran here is neither red nor declared: a fresh clone must not open red about "
    + "a command it has never been asked to run");
});

test("SH579: the rules fire — every one of them, on an entry built to break it", () => {
  // A world of four lines rather than the real backlog, because the real one moves: an id that is
  // open when this is written is shipped a task later, and a rules test that went red for *that*
  // would be one more red nobody can act on. The real files are the previous test's subject.
  const where = {
    roadmap: "- 📋 **SH9301** (deps: SH9300 ✅) **Still open** — the entry an exception may name.\n",
    changelog: "- **SH9302** — already shipped, so an exception naming it has outlived its reason.\n",
    today: "2026-08-10",
  };
  const ok = {
    suite: "conformance",
    test: "capture over a socket → convert into the instance → pull it back → gate it",
    task: "SH9301",
    declared: "2026-08-07",
    expires: "2026-08-14",
    why: "x".repeat(MIN_WHY),
  };
  // The premise: without it, every case below would pass for the wrong reason.
  assert.deepEqual(problems(ok, where), []);

  const fires = (patch, expected) => {
    const found = problems({ ...ok, ...patch }, where);
    assert.ok(
      found.some((m) => m.includes(expected)),
      `${JSON.stringify(patch)} should have been refused for ${expected}; got ${JSON.stringify(found)}`,
    );
  };

  fires({ suite: "the one on my machine" }, "exception against nothing");
  fires({ why: undefined }, "`why` is required");
  fires({ task: "901" }, "as SH<n>");
  fires({ expires: "next friday" }, "`expires` is YYYY-MM-DD");
  fires({ expires: "2026-08-06" }, "must be after");
  fires({ expires: "2026-09-30" }, "at most 14 days");
  fires({ why: "flaky" }, "the next person's only way to judge");
  fires({ declared: "2026-07-01", expires: "2026-07-08" }, "expired on 2026-07-08");
  // The two halves of "tied to open work", which fail identically and for opposite reasons.
  fires({ task: "SH9399" }, "not an open task");
  fires({ task: "SH9302" }, "SH9302 is shipped");
});

/**
 * SH783 — the trap that only existed in somebody's notes.
 *
 * <p>A running instance holds `target/viglet-shio.jar`, so `clean` fails with a delete error that
 * reads as a broken build. Hit four times in one session, and the reflex it teaches is to re-run —
 * which usually works, because the instance has since been stopped for an unrelated reason, so the
 * lesson never lands and the next person pays again.</p>
 *
 * <p>The preflight that would have named the holder was refused on proportionality: nothing in this
 * repository starts the instance, and the build declares no `exec` or `antrun` plugin, so the check
 * would mean a plugin execution on every build for every developer and for CI, where no instance
 * ever runs. What is left is to put the fact where a session already looks — which is SH282's
 * precedent, and is only worth anything if it stays there.</p>
 *
 * <p>Asserting that a document says a thing is thin on its own and is the right weight here: the
 * defect is a person reading an error and concluding the wrong thing, so the fix IS the sentence,
 * and the failure mode is somebody tidying it away. `field-reachability.test.mjs` holds a rule the
 * same way — see SH638 — for the same reason.</p>
 */
test("SH783: the build doc says a locked jar is an instance, and that a failed clean ran nothing", async () => {
  const build = await readFile(BUILD_DOC, "utf8");

  assert.match(build, /Failed to delete/,
    "SH783: the message a reader actually meets has to be in the file, or they cannot find the "
      + "explanation by searching for what they are looking at");
  assert.match(build, /file lock, not a build problem/,
    "and the sentence that redirects them: Maven says the project could not be cleaned, and the "
      + "cause is a `java -jar` instance holding the jar open");
  assert.match(build, /did not run at all/,
    "SH783's expensive half: a clean that fails means the build never ran, so a green `Tests run:` "
      + "line above the error describes the PREVIOUS tree. That cost a false read in the session "
      + "that filed this.");
});

test("SH579: the ledger and the doc that explains it can each find the other", async () => {
  const { note, doc } = JSON.parse(await readFile(LEDGER, "utf8"));
  const build = await readFile(BUILD_DOC, "utf8");

  assert.ok(note?.includes("SH579"), "red-suites.json carries a `note` saying what it is for");
  assert.equal(doc, "docs/agents/build.md", "`doc` points at the file that states the rule");
  assert.ok(
    build.includes("red-suites.json"),
    "docs/agents/build.md must name the ledger. An escape hatch nobody can find from the rule is "
      + "an escape hatch nobody uses, and the rule then gets broken silently instead — which is "
      + "the state this task was filed about.",
  );
  // Not vacuous: the doc gives the command for every suite this rule covers, so an exception's
  // `suite` key is a thing a reader can actually run.
  for (const [name, command] of Object.entries(SUITES)) {
    assert.ok(
      build.includes(command),
      `docs/agents/build.md does not give the command for the \`${name}\` suite (${command})`,
    );
  }
});

/**
 * SH818 — the assertion that would have caught the state two comparators can both reach.
 *
 * ## What happened
 *
 * A real red in `java` made `js` red about it. Fixing the java defect was not enough: by then `js`
 * was red too, so the next `pnpm java` failed on the js stamp and the next `pnpm test` failed on
 * the java stamp. The Java suite reported **1907 tests, 1 failure**, and the failure was a stamp.
 * The js suite was clean but for the other one. Neither had a defect and no sequence of runs said
 * so — the cycle is a fixed point of the SELF rule, which is otherwise exactly right.
 *
 * ## Why this is a predicate and a printout rather than a suppression
 *
 * Both gates red IS a red until somebody reads the two kept logs and finds nothing in them. A check
 * that excused it would turn the one arrangement that catches an undeclared red into the one that
 * hides a double failure. So `comparatorCycle` answers a question about the *shape*, the report
 * names the exit, and the person decides — which is the same division SH755 already makes between
 * printing every gate's colour and asserting only about the undeclared ones.
 *
 * ## Driven against a fabricated world
 *
 * The real stamps are whatever the last run left, so an assertion about them is a test of this
 * afternoon (the sentence the fixture tests above already earn).
 */
test("SH818: both comparator gates red is a cycle, and neither one alone is", () => {
  const red = { red: true };
  const green = { red: false };

  assert.equal(comparatorCycle({ java: red, js: red }), true,
    "SH818: the deadlock — each of these two judges the other, so each run re-reads the other's "
    + "red and stamps itself red again. Nothing in the state hints that running the gates once "
    + "more is the single thing that cannot work, which is why the exit has to be printed");

  assert.equal(comparatorCycle({ java: red, js: green }), false,
    "one comparator red is the ordinary case and the arrangement working: js will report it");
  assert.equal(comparatorCycle({ java: green, js: red }), false, "and the same the other way");
  assert.equal(comparatorCycle({ java: red }), false,
    "a gate with no stamp is `no run recorded`, never half a cycle — inferring a red from an "
    + "absence is the mistake SH770 refuses one field along");

  // The half that matters after SH770: a stale stamp is not this tree's colour, so it cannot be
  // half of this tree's cycle either. Excluded here exactly as it is from `undeclared`.
  assert.equal(comparatorCycle({ java: red, js: { red: true, stale: true } }), false,
    "a stale red is excluded from both directions everywhere else, and a cycle diagnosed from one "
    + "would send a reader to clear a stamp when what they need is to run the gate");

  // SH818's other half: the exit is sanctioned and recorded, not a deletion somebody improvised.
  assert.deepEqual(COMPARATOR_GATES, ["java", "js"],
    "the pair that can deadlock is the pair that hosts a comparator, which the SELF test above "
    + "already holds to being exactly these two");
  assert.match(CYCLE_EXIT, /pnpm gates:clear/,
    "the report names the verb, because the escape used before it existed was `rm`, and a stamp "
    + "deleted to break a cycle is indistinguishable from one deleted to dodge a red");
  assert.match(CYCLE_EXIT, /shio-test\.log/,
    "and it sends the reader to the kept logs FIRST — clearing a stamp over a real failure is the "
    + "way this verb becomes the thing that turns any red green");
});
