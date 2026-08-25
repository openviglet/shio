/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH758 — which shipped ids the documentation matrix names, mirrored into this repository.
 *
 * ## The distance this closes
 *
 * `COVERAGE.md` lives in **vigletdocs**. The ship lives here. So SH747's ratchet — which works, and
 * has caught six ids in one session and five in the next — can only fire from a conformance run
 * that needs an instance, the docs checkout and somebody choosing to look. By then several tasks
 * have shipped and the person being asked to write a cluster sentence is the person furthest from
 * the reasons for it. Naming them at that point is archaeology.
 *
 * A mirror in this tree makes the question answerable **here**, on the `pnpm test` that follows the
 * ship, while the words are still in somebody's head.
 *
 * ## A second copy, and what stops it lying
 *
 * This is a copy of a fact whose home is another repository, which is exactly the shape SH673 says
 * drifts. Two things hold it: it is **generated, never edited** — `pnpm run coverage:mirror`
 * rewrites it from the matrix — and `docs-coverage.conformance.mjs` asserts it still agrees with
 * the matrix it was taken from. So the mirror is checked against the authority rather than trusted
 * beside it, and a stale one fails the gate that reads the real file.
 *
 * ## It reports; it does not refuse
 *
 * §XXIV.5 is explicit that this should not be made urgent: a ratchet that fires late still fires,
 * and the failure being prevented is the *silent* one, which SH747 already prevents. So the reader
 * built on this prints, and the enforcement stays where it is. What changes is **when** somebody
 * finds out, not what happens to them.
 */
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

// @scans prose reason: COVERAGE.md is a documentation page and the ledger diff is text;
// what is extracted from both is the id, which no code stripper would help with (SH740).

/** Where the matrix lives, with the same default and the same override the conformance suite uses. */
export const DOCS = process.env.SHIO_DOCS_DIR
  ?? "D:/Git/viglet/vigletdocs/vigletdocs.github.io/docs-shio";

export const MIRROR = join("docs", "coverage-named.json");

/** Every `SH<n>` the matrix mentions anywhere — the same set membership SH747's ratchet computes. */
export function namedIn(text) {
  return [...new Set([...text.matchAll(/\bSH\d+\b/g)].map(([id]) => id))]
    .sort((a, b) => Number(a.slice(2)) - Number(b.slice(2)));
}

/** The mirror as committed, or `null` where this tree has none. */
export function readMirror(root) {
  const path = join(root, MIRROR);
  if (!existsSync(path)) {
    return null;
  }
  try {
    const held = JSON.parse(readFileSync(path, "utf8"));
    return Array.isArray(held?.named) ? held : null;
  } catch {
    return null;
  }
}

/**
 * Ids added to the ledger since {@code sha}, read from the diff rather than from the file.
 *
 * <p>The whole file would answer "every id ever shipped", which is SH747's question and not this
 * one. What this asks is narrower and is the one with a live audience: <b>what have I shipped since
 * the matrix was last brought up to date?</b></p>
 *
 * <p>Returns {@code null} when git cannot answer — a shallow clone, a rewritten history, no git at
 * all. That is reported rather than treated as "nothing shipped", because an empty answer from a
 * broken reader is the failure mode every scan in this repository is written against.</p>
 */
export function shippedSince(root, sha) {
  if (!sha) {
    return null;
  }
  try {
    const diff = execFileSync(
      "git",
      ["log", `${sha}..HEAD`, "-p", "--unified=0", "--", "docs/CHANGELOG.md"],
      { cwd: root, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 },
    );
    /*
     * SH911 — net arrivals, because `roadkeep record amend` rewrites an entry in place.
     *
     * An amendment puts the id on a `-` line and again on a `+` one, so counting only `+` dates a
     * years-old entry to the commit that corrected a word in it. SH910 deleted forty files and
     * corrected the seven ledger entries whose prose named them; this reader then listed all seven
     * as owing a COVERAGE row, which pushed a queue of five over the ceiling that fails the build.
     *
     * Net rather than "drop anything that was also removed": across a *range* an id can legitimately
     * ship in one commit and be amended in a later one — `+2 / -1` — and that is still a ship.
     */
    const net = new Map();
    for (const [, id] of diff.matchAll(/^\+- \*\*(SH\d+)\*\*/gm)) {
      net.set(id, (net.get(id) ?? 0) + 1);
    }
    for (const [, id] of diff.matchAll(/^-- \*\*(SH\d+)\*\*/gm)) {
      net.set(id, (net.get(id) ?? 0) - 1);
    }
    return [...net].filter(([, count]) => count > 0).map(([id]) => id);
  } catch {
    return null;
  }
}

function headSha(root) {
  try {
    return execFileSync("git", ["rev-parse", "HEAD"], { cwd: root, encoding: "utf8" }).trim();
  } catch {
    return null;
  }
}

/** Rewrite the mirror from the matrix. The only supported way to change this file. */
export function refresh(root, docs = DOCS) {
  const path = join(docs, "COVERAGE.md");
  if (!existsSync(path)) {
    throw new Error(
      `SH758: the coverage matrix is not at ${path}.\n`
        + "  git clone https://github.com/vigletdocs/vigletdocs.github.io\n"
        + "  SHIO_DOCS_DIR=<path>/docs-shio pnpm run coverage:mirror",
    );
  }
  const named = namedIn(readFileSync(path, "utf8"));
  const body = {
    note: "SH758 — generated from docs-shio/COVERAGE.md by `pnpm run coverage:mirror`. Never edited "
      + "by hand: docs-coverage.conformance.mjs asserts it still agrees with the matrix, so an "
      + "edit here that the matrix does not carry fails the gate that reads the real file.",
    at: headSha(root),
    named,
  };
  writeFileSync(join(root, MIRROR), `${JSON.stringify(body, null, 2)}\n`, "utf8");
  return body;
}

if (process.argv[1] && import.meta.url.endsWith(process.argv[1].replace(/\\/g, "/"))) {
  const root = join(import.meta.dirname, "..");
  const written = refresh(root, process.argv[2] ?? DOCS);
  process.stdout.write(
    `SH758: ${MIRROR} now names ${written.named.length} id(s), taken at ${written.at}\n`,
  );
}
