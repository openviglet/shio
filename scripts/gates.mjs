/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH802 — the four gate wrappers, as four rows instead of four files.
 *
 * ## What they were
 *
 * `test-with-log`, `conformance-with-log`, `conformance-docs-with-log` and `java-with-log` were the
 * same twenty lines four times: resolve the root, name a log, run a command through `runWithLog`,
 * stamp a key, exit with the code. What differed was a command, a filename and a string.
 *
 * The duplication had already produced a defect. `runWithLog` hardcoded its completion line as
 * `JS suites green` / `JS suites FAILED`, so the conformance and docs gates announced themselves as
 * the JS suites from the second wrapper onward — unnoticed, because that sentence is only read by
 * somebody already reading a failure. SH800 found it while adding the fourth.
 *
 * ## Why a table and not one function with four callers
 *
 * Each wrapper's header was genuinely different: SH798's and SH800's carried the measurements that
 * chose their mechanism, and a naive merge deletes exactly that. This repository already answers
 * the question once, in `known-debt.test.mjs` — **a table of rows, each carrying its own prose** —
 * and that is the shape here: `why` is a field, so consolidating the code costs no reason.
 */
import { join } from "node:path";

/**
 * The workspaces the JS gate runs, duplicated in `test:raw` and checked against it (SH645).
 *
 * <p>Changing which packages are tested is a decision, so the two are held in agreement by a test
 * rather than the duplication being argued away.</p>
 */
export const PACKAGES = [
  "@viglet/shio-client", "@viglet/shio-model", "@viglet/shio-sections",
  "@viglet/create-shio-app", "@viglet/shio", "@viglet/shio-claude-plugin",
  "shio-react", "shio-site",
];

/**
 * Every gate, keyed as `.gates/<key>.json` and as `red-suites.json`'s `suite`.
 *
 * <p>`command` and `args` are what the gate has always run, verbatim. Wrapping one must not quietly
 * change which scenarios it runs — that is a decision, and making it as a side effect of adding a
 * log is the scope change this repository keeps finding.</p>
 */
export const GATE_RUNS = {
  js: {
    label: "JS suites",
    log: "shio-js-test.log",
    command: "pnpm",
    // SH1292 — `--no-bail`: a recursive run stops at the first red package, so one declared red
    // (SH1289's shio-react entry) hid the cli package's undeclared one behind it. Every package runs;
    // the exit code is still non-zero when any of them fails.
    args: ["--reporter", "append-only", "--no-bail",
      ...PACKAGES.flatMap((name) => ["--filter", name]), "run", "test"],
    why: "SH645 — a red `pnpm test` had twice been read through a `grep` for the summary line, so "
      + "what survived was a count and never the failing test's name. The capture is the point; the "
      + "stamp came later (SH755).",
  },
  java: {
    label: "The Java suite",
    // `shio-test.log`, which is the name `agents.md` gives it — the JS gate's is `shio-js-test.log`,
    // and the two being one character apart is exactly why they are read from one table now.
    log: "shio-test.log",
    command: null,
    args: ["-pl", "shio-app", "-am", "clean", "test", "-Dskip.npm=true"],
    why: "SH800 — the one gate no comparator could judge, because a test inside it cannot know "
      + "whether the tests after it passed. Four in-JVM mechanisms were measured and none works: a "
      + "build extension needs an artifact this repository has not built yet; `exec-maven-plugin` "
      + "after `test` never runs on a red (SH416 set testFailureIgnore=false); a test cannot see its "
      + "own suite's outcome; and deriving it afterwards from the log gives the colour without the "
      + "commit, which SH770 refuses. So: a wrapper, and a bare `./mvnw` still leaves no stamp.",
  },
  conformance: {
    label: "The conformance suite",
    log: "shio-conformance.log",
    command: "pnpm",
    args: ["--reporter", "append-only", "--filter", "@viglet/shio", "run", "conformance"],
    why: "SH755 — the slowest gate, the only one needing a running instance, and therefore the one "
      + "nobody re-runs casually. A red here can be staleness, credentials, a downed instance or a "
      + "real defect; losing the run means doing the ten-minute part again to read a line.",
  },
  docs: {
    label: "The docs conformance suites",
    log: "shio-conformance-docs.log",
    command: "pnpm",
    args: ["--reporter", "append-only", "--filter", "@viglet/shio", "run", "conformance:docs"],
    why: "SH798 — a gate in every sense that mattered (its own job in `suites.yml`, its own trigger, "
      + "its own input, its own red) and the only one that could be run without leaving evidence it "
      + "had been. It carried twelve commits of coverage debt while both comparators reported the "
      + "tree honestly and had nothing to say about it (SH797).",
  },
};

/**
 * The Maven wrapper, by full path — `.cmd` on Windows.
 *
 * <p>Absolute, and that is not tidiness: `spawn` runs with `cwd` at the root, but the shell it runs
 * under resolves a bare name against `PATH` rather than that directory. The first version of the
 * Java row failed with `'mvnw.cmd' is not recognized`, and the wrapper stamped that as the gate's
 * colour — a red recording the recorder's own mistake, which is the worst thing a gate can write.</p>
 */
export function mvnw(root) {
  return join(root, process.platform === "win32" ? "mvnw.cmd" : "mvnw");
}

/** One gate's row, resolved against a root: `command` and `log` become paths where they must be. */
export function gateRun(key, root) {
  const row = GATE_RUNS[key];
  if (!row) {
    throw new Error(`no such gate: ${key} — one of ${Object.keys(GATE_RUNS).join(", ")}`);
  }
  return { ...row, command: row.command ?? mvnw(root), log: join(root, row.log) };
}
