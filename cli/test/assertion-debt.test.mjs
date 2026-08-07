/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH527 — a defect closes with an assertion, and this is the reader that makes that checkable.
 *
 * ## The measurement
 *
 * Six times a correction shipped half of itself and the other half became a new task:
 * SH452→455, SH429→437, SH469→473, SH468→472, SH465→475, SH470→474. **Three of those shipped the
 * report and left the behaviour.** At 246 commits in seven days, with verification being a person
 * asking an agent to look afterwards, that is the system's expected output rather than bad luck.
 *
 * ## The rule, and why it needs a reader
 *
 * One line: **a defect closes with an assertion carrying its id.** Not a unit test of the fix's
 * internals — *the assertion that would have caught it*, at the boundary it crossed. SH448 already
 * proved the shape: run first, it was red by construction and found two defects seven tasks before
 * the queue would have.
 *
 * A rule nobody can check is a preference. The suite knows which ids it asserts and the ledger knows
 * which shipped, so the difference is **computable**: shipped tasks with no assertion, listed. That
 * number is the debt, and it only goes down when somebody pays it.
 *
 * ## Why a list and not a ceiling
 *
 * A ceiling ("no more than 61") lets one task pay the debt and the next spend it, which is how a
 * budget becomes a licence. The list names every id, so a ship with no assertion fails **by name**
 * and the failure says what to do about it — and a task that genuinely cannot carry one is added
 * here deliberately, in a diff somebody reviews, with the reason beside it. That is the contract
 * `ShSilentCatchInventoryTest` already uses for a population nobody can lint.
 *
 * ## And it stays cheap
 *
 * Reading 294 files and one ledger, no network and no browser: §IX.36's last paragraph is a
 * requirement, not an aspiration. A suite that grows a case per defect and never sheds one becomes
 * the suite people skip, which is the failure mode that produced this backlog.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/**
 * Every tree that can hold an assertion, and the fact that this is a list is the point.
 *
 * <p>A glob over the whole repository would count an id mentioned in a source comment as an
 * assertion, which is exactly the thing this exists to distinguish: SH512's own docstring names
 * SH512, and a rule that accepted that would report zero debt for ever.</p>
 *
 * <p><b>An entry may narrow to a file pattern, and SH579 is why.</b> Every tree above holds
 * nothing but suites, so the directory was the whole selector — until `shio-site` grew its first
 * two, which sit in `scripts/` <i>beside the scripts they test</i>. Taking that directory whole
 * credits `prerender.mjs`, `markdown.mjs` and `token-budgets.mjs` for the ids their comments
 * name — three of them on the declared debt below, paid off by a source file mentioning them,
 * which is the exact false credit the paragraph above refuses. So the entry names the files.
 * (Measured: the first version of this note quoted those three ids in prose, outside the
 * declaration markers, and thereby credited all three itself. A reader that reads itself has to
 * be careful what it says out loud.)</p>
 *
 * <p>The blind spot cost eight commits. SH566 and SH571 each shipped two assertions, both in
 * `shio-site/scripts`, and this reader could not see either — so it reported them as debt and was
 * red at HEAD from the commit after it shipped. The alarm was wrong and nobody looked, which is
 * the case §IX.52 is actually about: an undeclared red suite teaches people that red means
 * nothing, and it does that whether or not the red is true.</p>
 */
const SUITES = [
  "claude-plugin/test",
  "cli/test",
  "cli/conformance",
  "create-shio-app/test",
  "shio-app/src/test",
  "shio-client/test",
  "shio-model/test",
  "shio-react/test",
  "shio-sections/test",
  { tree: "shio-site/scripts", only: /\.test\.mjs$/ },
];

const READABLE = new Set([".mjs", ".js", ".ts", ".tsx", ".java"]);

/**
 * The two markers around this file's own declarations, whose ids must not count (SH527).
 *
 * <p>Found twice, in opposite directions, which is why the boundary is here and not at the file.
 * **First**: reading this file whole counted all forty declared ids as *asserted*, so the debt came
 * back as three — a declaration that something is unasserted is not an assertion of it. **Then**,
 * with the whole file excluded, SH527 reported *itself* as debt, because the assertion for SH527 is
 * this file and nothing else names it. So what is skipped is the region between these markers, and
 * the rest of the file counts like any other suite: a test whose name carries an id is exactly the
 * assertion this reader is looking for.</p>
 */
const MARK = "debt-declaration";
const DECLARATION = [`${MARK}:start`, `${MARK}:end`];

/* debt-declaration:start — everything to the end marker is a DECLARATION, not an assertion. */

/**
 * Shipped work whose deliverable is **not in this repository**, so no suite here can assert it.
 *
 * <p>Not debt and not an exemption from the rule — the rule is about the boundary a defect crossed,
 * and for these the boundary is somewhere else. Listed rather than pattern-matched, because "Block R
 * is docs" is a claim that stops being true the moment a Block R task ships code.</p>
 */
const ELSEWHERE = {
  // Block R writes end-user documentation into the `vigletdocs` repository
  // (D:\Git\viglet\vigletdocs\vigletdocs.github.io), which has no test harness and is not a
  // checkout this suite can see. SH499 is the task that proposes executing those pages' examples;
  // until it ships there is nothing here to assert about them.
  docs: ["SH479", "SH480", "SH481", "SH482", "SH483", "SH484", "SH485", "SH486", "SH487", "SH488",
    "SH489", "SH490", "SH491", "SH492", "SH493", "SH494", "SH495", "SH496", "SH497", "SH498",
    "SH504"],
};

/**
 * Shipped work with no assertion naming it — **the debt**, as it stood when this reader shipped.
 *
 * <p>Every entry is a task that closed and left nothing behind that would catch its defect coming
 * back. The list goes down when somebody pays one off; it goes **up** only through a diff that says
 * why, which is the whole mechanism. Grouped by what each group would take, because the groups are
 * not equally cheap and a flat list of 40 ids reads as hopeless rather than as work.</p>
 */
const DEBT = [
  // The console, which has a test tree (`shio-react/test`) holding no reference to any of these.
  // Each is a React page or widget behaviour, so each is assertable there and none is asserted.
  "SH7", "SH8", "SH14", "SH48", "SH49", "SH50", "SH56", "SH57", "SH59", "SH73",
  "SH177", "SH178", "SH179", "SH180", "SH182", "SH237",
  // Removals and port-or-deprecate decisions. An absence is assertable — a lint that the directory
  // is gone, the way `ShIdeCompiledClassLintTest` asserts a state rather than a behaviour — and
  // nothing here does it, so a re-introduction would be silent.
  "SH20", "SH21", "SH60",
  // The public site in `shio-site/`. When this reader shipped the note here read "no test tree at
  // all"; SH566 and SH571 have since put two suites in `scripts/`, and neither covers these ten —
  // they are the prerender, the token budgets and the copy, and each is assertable beside the
  // script that owns it. Still the cheapest group and still the least defensible: it is code in
  // this repository, and there is now a place to put the assertions.
  "SH530", "SH531", "SH532", "SH533", "SH534", "SH535", "SH536", "SH537", "SH538", "SH539",
  // Conventions and process, where the assertion is a lint over the repository rather than a test
  // of a behaviour: a mutating test forcing its precondition (SH296), `noRollbackFor` on the class
  // (SH352), the plugin's version floor (SH355), the surefire fork count (SH414), `skipITs`
  // defaulting true (SH415), the license headers agreeing with LICENSE (SH541).
  "SH296", "SH352", "SH355", "SH414", "SH415", "SH541",
  // The locale run (SH361 is its own decision entry, SH366-SH371 the six steps). Four of the six
  // are asserted elsewhere by behaviour and not by id, which is the same gap: an assertion nobody
  // can find from the id does not close the loop this rule exists for.
  "SH361", "SH366", "SH367", "SH368", "SH369", "SH370", "SH371",
  // The Universal Editor's load-bearing fact, which SH238's own entry says is checked "in a browser
  // and in the chain" — in `shio-react/e2e`, which is not a tree this reader reads, because it
  // needs a running instance. Listed rather than exempted: the reader should learn that tree.
  "SH238",
].sort(byId);

/* debt-declaration:end */

function byId(a, b) {
  return Number(a.slice(2)) - Number(b.slice(2));
}

/** Every id the ledger records as shipped. `🗑` retirements are not shipments and are excluded. */
async function shippedIds() {
  const ledger = await readFile(join(ROOT, "docs/CHANGELOG.md"), "utf8");
  return new Set([...ledger.matchAll(/^- \*\*(SH\d+)/gm)].map(([, id]) => id));
}

/** Every id any suite in this repository names. */
async function assertedIds() {
  const found = new Set();
  let files = 0;
  for (const suite of SUITES) {
    const { tree, only } = typeof suite === "string" ? { tree: suite, only: null } : suite;
    for (const path of walk(join(ROOT, tree))) {
      if (only && !only.test(path)) continue;
      files += 1;
      const body = withoutDeclaration(await readFile(path, "utf8"));
      for (const id of body.match(/\bSH\d+\b/g) ?? []) found.add(id);
    }
  }
  return { found, files };
}

/** A file with this reader's own declaration cut out, and every other file unchanged. */
function withoutDeclaration(body) {
  const start = body.indexOf(DECLARATION[0]);
  const end = body.indexOf(DECLARATION[1]);
  return start >= 0 && end > start ? body.slice(0, start) + body.slice(end) : body;
}

function walk(dir) {
  const out = [];
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries) {
    if (entry === "node_modules" || entry === "target") continue;
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) out.push(...walk(path));
    else if (READABLE.has(entry.slice(entry.lastIndexOf(".")))) out.push(path);
  }
  return out;
}

test("SH527: every shipped task carries an assertion, or is on the declared debt", async () => {
  const shipped = await shippedIds();
  const { found, files } = await assertedIds();
  const elsewhere = new Set(Object.values(ELSEWHERE).flat());

  const debt = [...shipped].filter((id) => !found.has(id) && !elsewhere.has(id)).sort(byId);
  // Printed on every run, which is what makes the number a fact somebody sees rather than one this
  // file holds. §IX.36's own sentence: that number is the debt, and it only goes down when somebody
  // pays it.
  console.log(
    `SH527: ${shipped.size} shipped, ${shipped.size - debt.length - elsewhere.size} asserted, `
      + `${debt.length} in debt, ${elsewhere.size} delivered in another repository `
      + `(${files} suite files read)`,
  );

  assert.deepEqual(
    debt,
    DEBT,
    "A defect closes with an assertion carrying its id — the assertion that would have CAUGHT it, "
      + "at the boundary it crossed, not a unit test of the fix's internals.\n"
      + "If a task you just shipped is in `+ actual`, that is this rule: add the assertion, and name "
      + "the id in it so this reader can find it.\n"
      + "If it genuinely cannot carry one, add it to DEBT with the reason — in a diff somebody "
      + "reviews. A ceiling would let one task pay the debt and the next spend it.\n"
      + "If it is in `- expected`, somebody paid one off: delete the line.",
  );
});

test("SH527: the reader is not vacuous — it reads real suites and a real ledger", async () => {
  const shipped = await shippedIds();
  const { found, files } = await assertedIds();

  assert.ok(shipped.size > 400, `the ledger yielded ${shipped.size} shipped ids`);
  assert.ok(files > 200, `the suites yielded ${files} files`);
  // A reader that found no id at all would report every shipped task as debt and pass on a DEBT
  // list somebody had accepted. So two ids it must see, one per half of the walk, asserted apart so
  // a failure says which half went blind.
  assert.ok(found.has("SH516"), "the Java half of the walk found nothing: SH516 is asserted in "
    + "ShPageRenderTest and in no .mjs file");
  assert.ok(found.has("SH522"), "the JS half of the walk found nothing: SH522 is asserted in "
    + "cli/test/diff.test.mjs and in no .java file");
  assert.ok(found.has("SH527"), "and this file counts like any other suite outside its declaration "
    + "region — the assertion for SH527 is these very test names, so a reader that could not see "
    + "them would report itself as debt, which it did until the exclusion moved off the file");
  assert.ok(
    !DEBT.some((id) => found.has(id)),
    "and nothing on the declared debt is actually asserted: a stale entry here hides a real one",
  );
});
