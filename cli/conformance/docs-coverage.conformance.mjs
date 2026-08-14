/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH653 — **`COVERAGE.md`'s summary is a projection of its tables, so it is derived here rather
 * than typed there.**
 *
 * ## The defect this exists to have caught
 *
 * That file ends with one sentence anybody scanning it actually reads — *"Of N clusters, M are
 * still not documented correctly"* — and every figure in it was hand-typed. It drifted, and it
 * drifted in the direction that hides work: the `shio audit` row (SH652) was 🔴 in the tables from
 * the day the file shipped and absent from the summary for just as long, so the number said the
 * product was better documented than it was. Block T flipped seven verdicts and each was a hand
 * edit to a row *and* to a total — seven more chances for the two to part company.
 *
 * The file recorded the disagreement instead of resolving it, in a note saying a count of every
 * status-bearing row came to 48 against the 40 the summary was authored against. A note explaining
 * why a number cannot be trusted is larger than the script that recomputes it.
 *
 * ## What a cluster is, which had to be decided before anything could be counted
 *
 * **One table row.** Measured at the time: 48 rows in the status tables, against 33 distinct block
 * labels and the 42 the summary claimed — so neither reading reproduced the typed figure, and the
 * gap the note named was not the only one. A row is the unit because a row already carries its own
 * verdict; grouping by block would need an aggregation rule nobody has written, since `Block H`
 * spans five rows and `Block K` two with different verdicts.
 *
 * ## Why it asserts rather than rewrites
 *
 * Rewriting would end the drift and turn the summary into a section nobody writes and no reviewer
 * reads. This fails instead, printing the sentence to write — the arithmetic is taken out of a
 * person's hands without taking the sentence out of the diff.
 *
 * Beside `docs-vocabulary` and `docs-stack` because it reads the same other repository and needs no
 * instance: it is part of `pnpm conformance:docs`, the job SH655 put on every push.
 */
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { describe, test } from "node:test";
import { fileURLToPath } from "node:url";

// @scans prose reason: COVERAGE.md is Markdown and its table rows ARE the subject — the
// summary is derived from them, so nothing here can be stripped away (SH740).

const DOCS = process.env.SHIO_DOCS_DIR
  ?? "D:/Git/viglet/vigletdocs/vigletdocs.github.io/docs-shio";

const MATRIX = "COVERAGE.md";

/** The verdicts a status cell may carry, in the order the summary reports them. */
const STATUSES = ["✅", "🧩", "⚠️", "🔴"];

function matrix() {
  const path = join(DOCS, MATRIX);
  if (!existsSync(path)) {
    throw new Error(
      `SH653: the coverage matrix is not at ${path}.\n`
        + "This suite derives that file's own summary from its own tables, so without it there is\n"
        + "nothing to compare and reporting nothing as a pass is the failure it exists to stop.\n"
        + "  git clone https://github.com/vigletdocs/vigletdocs.github.io\n"
        + "  SHIO_DOCS_DIR=<path>/docs-shio pnpm conformance:docs",
    );
  }
  return readFileSync(path, "utf8");
}

/**
 * Every row of every table whose last column is `Status`, with the verdict it carries.
 *
 * <p>The header check is what keeps this honest. The file also holds a two-column
 * *"Deliberately internal: no public page (not orphans)"* table whose prose mentions ⚠️ and 🔴
 * while describing rows elsewhere — a reader that scanned for glyphs counted two of those as
 * verdicts and came to 50. They are not verdicts, and the section title says so.</p>
 */
export function rowsOf(text) {
  const rows = [];
  let inStatusTable = false;
  for (const line of text.split(/\r?\n/)) {
    if (!line.startsWith("|")) {
      inStatusTable = false;
      continue;
    }
    const cells = line.split("|").slice(1, -1).map((cell) => cell.trim());
    if (cells.every((cell) => /^:?-+:?$/.test(cell))) continue;
    if (cells[cells.length - 1]?.toLowerCase() === "status") {
      inStatusTable = true;
      continue;
    }
    if (!inStatusTable) continue;
    const verdict = STATUSES.find((glyph) => cells[cells.length - 1]?.startsWith(glyph));
    if (verdict) rows.push({ verdict, cluster: cells[0] });
  }
  return rows;
}

/**
 * The first cell of every row of the `Why no page` table — the rows that must never be tallied.
 *
 * <p>Read by its header rather than by any row's text, so this stays true when an entry is added.
 */
export function internalRows(text) {
  const out = [];
  let inInternal = false;
  for (const line of text.split(/\r?\n/)) {
    if (!line.startsWith("|")) {
      inInternal = false;
      continue;
    }
    const cells = line.split("|").slice(1, -1).map((cell) => cell.trim());
    if (cells.every((cell) => /^:?-+:?$/.test(cell))) continue;
    if (/^why no page$/i.test(cells[cells.length - 1] ?? "")) {
      inInternal = true;
      continue;
    }
    if (inInternal) out.push(cells[0]);
  }
  return out;
}

/** The counts the summary sentence is a projection of. */
export function tally(rows) {
  const counts = Object.fromEntries(STATUSES.map((glyph) => [glyph, 0]));
  for (const row of rows) counts[row.verdict] += 1;
  const wrong = counts["⚠️"] + counts["🔴"];
  return { total: rows.length, counts, wrong, covered: rows.length - wrong };
}

/** The sentence the file should carry, printed on a mismatch so nobody does the arithmetic. */
function sentence({ total, counts, wrong, covered }) {
  const head = wrong === 0
    ? `**Of ${total} clusters, none is undocumented: no 🔴 orphan and no ⚠️ page.**`
    : `**Of ${total} clusters, ${wrong} ${wrong === 1 ? "is" : "are"} still not documented `
      + `correctly: ${counts["🔴"]} 🔴 orphan${counts["🔴"] === 1 ? "" : "s"} and `
      + `${counts["⚠️"]} ⚠️ page${counts["⚠️"] === 1 ? "" : "s"}.**`;
  return `${head} ${covered} are covered (${counts["✅"]} ✅, ${counts["🧩"]} 🧩).`;
}

/** What the file currently claims, read off the one sentence that states a total. */
function claimed(text) {
  const of = /\*\*Of (\d+) clusters/.exec(text);
  const covered = /(\d+) are covered \((\d+) ✅, (\d+) 🧩\)/.exec(text);
  return {
    total: of ? Number(of[1]) : null,
    covered: covered ? Number(covered[1]) : null,
    ok: covered ? Number(covered[2]) : null,
    partial: covered ? Number(covered[3]) : null,
  };
}

const text = matrix();
const rows = rowsOf(text);
const derived = tally(rows);

describe("SH653 conformance: the coverage summary is derived from the coverage tables", () => {
  test("SH653: the summary's counts are the tables' counts", () => {
    const typed = claimed(text);

    assert.ok(
      typed.total !== null && typed.covered !== null,
      "SH653: the summary sentence is gone or reworded, and this check reads it by shape. If the "
        + "wording changed on purpose, change the reader with it — silently matching nothing is "
        + "how the typed number drifted for two blocks in the first place",
    );

    assert.deepEqual(
      { total: typed.total, covered: typed.covered, ok: typed.ok, partial: typed.partial },
      {
        total: derived.total,
        covered: derived.covered,
        ok: derived.counts["✅"],
        partial: derived.counts["🧩"],
      },
      "SH653: the summary disagrees with the rows it summarises, which is the drift that hid an "
        + `orphan for two blocks.\n  write: ${sentence(derived)}`,
    );
  });

  test("SH653: a two-column table's prose is not a verdict", () => {
    // The false positive that produced 50 instead of 48, and the reader's whole correctness rests
    // on not making it: the *"Deliberately internal: no public page (not orphans)"* table describes
    // rows elsewhere, so its prose says things like "folded into the ⚠️ rows above". A reader that
    // matched a glyph anywhere in the cell counted those, and inflated the number in the direction
    // that looks like more coverage.
    //
    // Derived rather than named. The first draft of this asserted no row matched `Infra`, one
    // entry of that table — and passed while a *different* one of its rows leaked, which is
    // precisely the shape of defect this file exists about.
    const internal = internalRows(text);
    assert.ok(
      internal.length > 5,
      `expected the internal-blocks table, found ${internal.length} row(s) — a reader that found `
        + "none of them would agree with any tally, including a wrong one",
    );

    const leaked = rows.map((row) => row.cluster).filter((cluster) => internal.includes(cluster));
    assert.deepEqual(
      leaked,
      [],
      "SH653: a row from the `Why no page` table reached the tally, so a glyph written as prose "
        + "is being read as a verdict",
    );
  });

  test("SH653: the counts are printed, and the reader is not vacuous", () => {
    // The alternative reading of "cluster", printed beside the chosen one so the decision stays
    // legible: a block label, with its qualifier dropped, so `Block H` and `Block H: …` are one.
    const labels = new Set(rows.map((row) =>
      (/^\*\*(.+?)\*\*/.exec(row.cluster)?.[1] ?? row.cluster)
        .replace(/\s*\(.*$/, "").replace(/:.*$/, "").trim()));
    process.stdout.write(
      `\nSH653 coverage: ${derived.total} cluster row(s) — `
        + STATUSES.map((glyph) => `${derived.counts[glyph]} ${glyph}`).join(", ")
        + `\n  ${derived.covered} covered, ${derived.wrong} wrong, over ${labels.size} distinct `
        + "block label(s) — the row is the cluster (SH653)\n",
    );
    assert.ok(
      derived.total > 20,
      `expected the matrix's rows, found ${derived.total} — an empty read would agree with any `
        + "summary that also said zero",
    );
  });
});

/**
 * SH747 — the matrix is the gate for shipped work, and nothing checked that shipped work reaches it.
 *
 * <p>Six ships in one session got no verdict — SH47/SH46/SH746 in Block F, SH691/SH245/SH744 in
 * Block O — and both misses were found by reading the ledger against the matrix by hand, after the
 * commits had landed.</p>
 *
 * <p><b>A per-id rule was measured and refused.</b> "Every shipped id is named in COVERAGE.md"
 * reports 524 of 703, because the matrix's contract is per <i>cluster</i> and most ids are covered
 * by a row that never names them. A gate opening with 524 findings is one nobody reads.</p>
 *
 * <p>What the six had in common is that they were <b>new</b>: a block whose rows already existed
 * gained a task and the matrix did not move. So this is a ratchet over a corpus meant to shrink —
 * the shape `ShImprovementsStaleSectionRatchetTest` uses. The number may fall and may not rise.</p>
 */
describe("SH747 conformance: a newly shipped id cannot slip past the coverage matrix", () => {
  /**
   * Shipped ids the matrix does not name, on 2026-08-14 (524 when SH747 landed; Block U named six).
   *
   * <p>Lower it when you name one. Raising it means a task shipped and the matrix did not move,
   * which is the whole defect — name the id in its cluster row, or add the 🔒 row saying why there
   * is no page.</p>
   */
  const UNNAMED = 505;

  /** Every id the ledger records as SHIPPED. A `🗑` retirement has no surface to document. */
  function shippedIds() {
    // From THIS file, not from DOCS: the docs live in another repository at a path the reader is
    // told (SHIO_DOCS_DIR), and the ledger is in this one. Deriving one from the other assumed a
    // sibling layout that is not guaranteed and was wrong on the machine that wrote it.
    const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
    const ledger = readFileSync(join(root, "docs", "CHANGELOG.md"), "utf8");
    return [...ledger.matchAll(/^- \*\*(SH\d+)\*\*/gm)].map(([, id]) => id);
  }

  test("SH747: the count of shipped ids the matrix does not name never rises", () => {
    const text = matrix();
    const shipped = shippedIds();
    // The ids the matrix names, extracted once. A regex per id would need a word boundary so
    // `SH47` does not match inside `SH470` - and the first version built that pattern through
    // a shell heredoc that ate a backslash, so every id matched nothing and the count came
    // back 689 of 689. Set membership cannot be wrong that way.
    const named = new Set([...text.matchAll(/\bSH\d+\b/g)].map(([id]) => id));
    const unnamed = shipped.filter((id) => !named.has(id));

    process.stdout.write(
      `\nSH747: ${shipped.length} shipped id(s), ${unnamed.length} not named in ${MATRIX} `
        + `(recorded ${UNNAMED})\n`,
    );

    assert.ok(shipped.length > 400,
      `the ledger yielded ${shipped.length} shipped ids — a read that came back empty would make `
        + "every id look named and this ratchet meaningless");

    assert.ok(
      unnamed.length <= UNNAMED,
      `${unnamed.length} shipped ids are not named in ${MATRIX}, against a recorded ${UNNAMED}. A `
        + "task shipped and the matrix did not move, which is the defect SH747 exists for — six "
        + "went past in one session. Name the id in its cluster row, or add the 🔒 row saying why "
        + "there is no page, then lower UNNAMED in the same commit.\n  newest unnamed: "
        + unnamed.slice(-8).join(" "),
    );
  });
});
