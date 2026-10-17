/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH942 — the vendored page reference is *current*, and a stale one fails the build.
 *
 * ## Why staleness is the interesting failure
 *
 * SH941 put the design system's page contract in this repository and asserted it is there. A
 * vendored file is read with the trust of the package that wrote it, so the failure that costs
 * something is not the one where it is missing — that is loud — but the one where it is present and
 * has silently stopped agreeing with the package. An authority that has drifted is worse than none.
 *
 * `page-reference.mjs` is built for exactly this: `--check` reports what would change, writes
 * nothing and exits non-zero on drift, and `--json` gives the machine-readable form. Nothing here
 * re-implements that comparison; it runs the bin and reads its verdict, which is the SH338 shape —
 * the instrument already exists, and what was missing was something obliged to run it.
 *
 * ## Inside the JS gate, not beside it
 *
 * The check runs as a test in `pnpm test` rather than as a step somebody adds to a script. Two
 * reasons, and the second is the project's own scar tissue. A gate runs through
 * `scripts/run-gate.mjs`, which keeps the log and stamps the outcome where the comparators read it,
 * so a check living inside one inherits all of that for free. And `agents.md`'s rule about never
 * piping a gate into `grep` is about this exact loss: a pipeline's exit status is its last
 * command's, which is how `pnpm test` once printed `fail 2` above `EXIT=0`. A test asserting on a
 * spawned process's own status cannot lose it that way.
 *
 * ## Broken on purpose, because a green check proves nothing on its own
 *
 * SH897's browser check passed with `bento.css` deleted. So this file does not stop at "the bin
 * said ok": it plants drift of each kind the bin can see — an edited copy, an artboard the package
 * never shipped — in a throwaway root, and requires the check to **name the file**. A gate that
 * cannot be made to fail is not known to be a gate.
 *
 * The last test is the one that keeps the gate switched on. `canvas.json` is merged rather than
 * copied, and the bin compares it as parsed JSON: reformatting it must NOT read as drift, or the
 * first person whose editor reindents it is the person who deletes this file. Its control is a
 * change to the same file that is *semantic*, which must still be caught.
 */
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// @scans prose — reason: a Markdown file is read and rewritten here, but as BYTES inside a throwaway
// root this file just created, to plant drift; no sentence is read as a claim about anything (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));

/**
 * The tree the bin owns in a consumer, and the one file inside it that is merged. The skill it used
 * to vendor beside it arrives through the enabled `viglet-ds` plugin since SH944, and the bin reads
 * that from the settings — so a throwaway copy carries the settings too, or it is another consumer.
 */
const SETTINGS = ".claude/settings.json";
const SKILL_DIR = ".claude/skills/viglet-ds-pages";
const CANVAS_DIR = "docs/design";
const CANVAS = `${CANVAS_DIR}/canvas.json`;
const PANEL = `${CANVAS_DIR}/vds-panel.dc.html`;

/**
 * The bin, resolved through whatever pnpm laid down for the console.
 *
 * A bin is not in `exports`, so it cannot be `require.resolve`d by name — `exports.json` is
 * resolvable and two levels up from it is the package root, which is the same route
 * `design-system-subpaths.test.mjs` takes for the same reason.
 */
const require_ = createRequire(join(ROOT, "shio-react", "noop.js"));
const PACKAGE_ROOT = dirname(dirname(require_.resolve("@viglet/viglet-design-system/exports.json")));
const BIN = join(PACKAGE_ROOT, "scripts", "page-reference.mjs");

const REFRESH =
  "Run `viglet-ds-page-reference` from the repository root to rewrite the vendored surfaces, and"
  + " read what changed — a drifted authority is read with the same trust as a current one. Nothing"
  + " under those two directories is hand-authored: a correction belongs in"
  + " @viglet/viglet-design-system, where every consumer gets it.";

/** Run the bin's own check against a root, and return its verdict as it reports it. */
function check(root) {
  const run = spawnSync(process.execPath, [BIN, "--root", root, "--check", "--json"], {
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  assert.equal(
    run.error ?? null,
    null,
    `SH942: could not run the page-reference bin at ${BIN}: ${run.error?.message}`,
  );
  let report;
  try {
    report = JSON.parse(run.stdout);
  } catch {
    assert.fail(
      `SH942: the bin's --json output did not parse. stdout:\n${run.stdout}\nstderr:\n${run.stderr}`,
    );
  }
  return { status: run.status, report };
}

/** A throwaway consumer carrying a copy of this repository's vendored surfaces. */
function vendoredCopy() {
  const root = mkdtempSync(join(tmpdir(), "shio-page-reference-"));
  cpSync(join(ROOT, SETTINGS), join(root, SETTINGS));
  cpSync(join(ROOT, CANVAS_DIR), join(root, CANVAS_DIR), { recursive: true });
  return root;
}

/** What the report says about one surface, or `undefined` where it names none. */
const stateOf = (report, to) => report.surfaces.find((surface) => surface.to === to)?.state;

test("SH942: the bin this gate depends on is installed", () => {
  assert.ok(
    existsSync(BIN),
    `SH942: no page-reference bin at ${BIN}. It arrived in @viglet/viglet-design-system 2026.3.10,`
      + " which SH940 raised shio-react's floor to — a resolved version below that has the subpaths"
      + " and none of the bins, and this gate would then silently check nothing.",
  );
});

test("SH942: the vendored page reference in this repository is current", () => {
  const { status, report } = check(ROOT);

  const stale = report.surfaces.filter((surface) => surface.state !== "unchanged");
  assert.deepEqual(
    stale.map((surface) => `${surface.state}  ${surface.to}`),
    [],
    `The vendored page reference has drifted from @viglet/viglet-design-system@${report.version}.`
      + " A copy-wide edit reaches these files too (SH1247: an em-dash pass rewrote six of them)."
      + `\n${REFRESH}`,
  );
  assert.deepEqual(
    report.orphans,
    [],
    "A `vds-` artboard is vendored here that the package no longer ships. This is the one direction"
      + ` a rewrite cannot fix on its own, which is why the bin reports it separately.\n${REFRESH}`,
  );
  assert.equal(
    report.code,
    null,
    `The bin reports \`${report.code}\`. \`page-reference.absent\` means the surfaces were never`
      + " vendored (SH941); `page-reference.stale` means they have drifted.\n" + REFRESH,
  );
  assert.equal(status, 0, "SH942: --check exits non-zero on drift, and its verdict is the gate");
});

test("SH942: the vendored copy is not ahead of the package that would rewrite it", () => {
  const { report } = check(ROOT);
  assert.equal(
    report.ahead,
    false,
    `SH942: the vendored copy is stamped ${report.stamped} and the installed package is`
      + ` ${report.version}, so refreshing would be a DOWNGRADE and the bin refuses to write.`
      + " Raise the design-system floor in shio-react/package.json rather than rewriting these"
      + " files from an older copy.",
  );
});

test("SH942: an edited copy is drift, and the check names the file", () => {
  const root = vendoredCopy();
  try {
    writeFileSync(join(root, PANEL), "\n<p>A line the package never wrote.</p>\n", { flag: "a" });
    const { status, report } = check(root);

    assert.equal(status, 1, "SH942: --check must exit non-zero on drift, or no gate can read it");
    assert.equal(report.code, "page-reference.stale");
    assert.equal(
      stateOf(report, PANEL),
      "would update",
      "SH942: the check must NAME the drifted file. An exit code alone sends the next reader to"
        + " diff ten surfaces by hand, and a check that cannot be made to name one is not"
        + " known to be reading them at all (SH897: the browser check passed with bento.css gone).",
    );
    assert.equal(
      stateOf(report, `${CANVAS_DIR}/vds-shell.dc.html`),
      "unchanged",
      "SH942: only the edited file may be reported — a check that calls everything drifted on one"
        + " edit is one nobody can act on",
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("SH942: an artboard the package never shipped is reported as an orphan", () => {
  const root = vendoredCopy();
  try {
    const ghost = `${CANVAS_DIR}/vds-a-drawing-that-was-never-shipped.dc.html`;
    writeFileSync(join(root, ghost), "<p>left behind by a release that dropped it</p>\n");
    const { status, report } = check(root);

    assert.equal(status, 1);
    assert.deepEqual(
      report.orphans,
      [ghost],
      "SH942: a `vds-` artboard the package stopped shipping must be reported. A rewrite leaves it"
        + " untouched, so this is the only direction of drift the copy discipline cannot repair by"
        + " itself, and a gate blind to it says `ok` over a drawing nothing declares.",
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("SH944: with the plugin enabled, a vendored skill left behind is an orphan the check names", () => {
  const root = vendoredCopy();
  try {
    // The state SH944 left behind, planted: the plugin carries the skill, and a stale copy lingers.
    const lingering = `${SKILL_DIR}/SKILL.md`;
    mkdirSync(join(root, SKILL_DIR), { recursive: true });
    writeFileSync(join(root, lingering), "---\nname: viglet-ds-pages\nvds-version: 2026.3.10\n---\n");
    const { status, report } = check(root);

    assert.equal(report.plugin, true, "SH944: the bin must read the copied settings as plugin-enabled");
    assert.equal(status, 1);
    assert.deepEqual(
      report.orphans,
      [lingering],
      "SH944: a vendored skill beside the enabled plugin is two skills named `viglet-ds-pages` — the"
        + " collision SH949 forbids — so the check must name it for removal, not pass over it.",
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("SH942: reformatting canvas.json is not drift, and changing it is", () => {
  const root = vendoredCopy();
  try {
    // The consumer's own formatter: same document, different bytes. `canvas.json` is the one
    // surface the bin merges rather than copies, precisely so this repository can keep its own
    // pages in it — and a comparison that read whitespace would make every adopter's formatter a
    // build failure. That gate gets switched off, which is worse than not having one.
    const parsed = JSON.parse(readFileSync(join(root, CANVAS), "utf8"));
    writeFileSync(join(root, CANVAS), JSON.stringify(parsed, null, 4));

    const reformatted = check(root);
    assert.equal(
      stateOf(reformatted.report, CANVAS),
      "unchanged",
      "SH942: canvas.json must be compared as PARSED JSON, never as bytes. Reindenting it is not"
        + " drift, and a gate that says it is, is the first thing somebody deletes.",
    );
    assert.equal(reformatted.status, 0, "SH942: a reformatted declaration leaves the check green");

    // The control, on the same file: drop a `vds-` artboard from the declaration. Semantic, so it
    // must be caught — otherwise the test above is passing because canvas.json is never read.
    parsed.artboards = parsed.artboards.filter((artboard) => artboard.file !== "vds-shell.dc.html");
    writeFileSync(join(root, CANVAS), `${JSON.stringify(parsed, null, 2)}\n`);

    const gutted = check(root);
    assert.equal(
      stateOf(gutted.report, CANVAS),
      "would update",
      "SH942: a `vds-` artboard removed from the declaration must be caught — if it is not, the"
        + " formatting test above proves only that canvas.json is being ignored.",
    );
    assert.equal(gutted.status, 1);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("SH942: a repository with nothing vendored is told that, not shown thirteen drifts", () => {
  const root = mkdtempSync(join(tmpdir(), "shio-page-reference-bare-"));
  try {
    const { status, report } = check(root);
    assert.equal(status, 1);
    assert.equal(
      report.code,
      "page-reference.absent",
      "SH942: an unwired repository must be diagnosed as absent rather than stale. The two want"
        + " different instructions — one adopts the contract, the other re-reads what changed in"
        + " it — and thirteen `would create` lines teach neither.",
    );
    assert.equal(report.wired, false);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
