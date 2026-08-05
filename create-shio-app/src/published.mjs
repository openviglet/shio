/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH446 — the versions a scaffold may declare, which are the ones the registry has.
 *
 * ## The defect this exists to make impossible
 *
 * The replication chain ran green to its last step and the last step did not install. `shio init`
 * scaffolds the Next.js app that serves a converted site, prints `npm install` as the next command,
 * and that command failed: `ETARGET — No matching version found for @viglet/shio-client@^2026.3.1`.
 * The range came from **the workspace**, which is at `2026.3.1` and unpublished, so every release
 * cycle re-broke every scaffold the moment the version was bumped and nothing said so until a human
 * ran `npm install` by hand.
 *
 * That is worth stating as the end of the chain rather than a packaging chore: `clone`, `propose`,
 * `convert` and `verify` all reported success, the gate said `ok — 0 error(s)`, and the replica was
 * viewable by nobody, because the one artifact that would serve it could not be installed outside
 * this monorepo. P9 failing at the last hop — every cheap textual proof passes and the perception
 * loop never closes.
 *
 * ## Why a declared range and not the workspace's version
 *
 * A scaffold is consumed **outside** this repository, so the only version it may name is one the
 * registry can serve. Reading it from the workspace answers a different question — "what is this
 * checkout building" — and the two are equal only in the instant after a release. So the ranges
 * live here, they are **caret** ranges on the latest *published* version, and they therefore pick
 * up the new one automatically the day it ships: `^2026.2.1` resolves today and resolves to
 * `2026.3.1` the moment that exists.
 *
 * `scaffold-versions.test.mjs` fails the build when the template disagrees with this file, and the
 * SH448 conformance suite hands the result to **npm**, which is the only thing that can say whether
 * a range is real.
 */

/**
 * Package → the range a scaffold declares, or `null` for "never published".
 *
 * A `null` is not a placeholder to fill in with a guess: it means the package has no version on the
 * registry at all, so **no range is correct** and any scaffold carrying it cannot install. It is
 * recorded rather than hidden so the failure has a name, and `unpublished()` is what reports it.
 */
export const PUBLISHED = {
  "@viglet/shio-client": "^2026.2.1",
  // SH156 made these publishable and no release has run, so both answer E404. The section-carrying
  // starters (`marketing-site`, `landing`) import the vocabulary, so they cannot be made installable
  // by choosing a different range — only by publishing. Left declared so the gap is a row rather
  // than an absence.
  "@viglet/shio-model": null,
  "@viglet/shio-sections": null,
};

/** The packages a scaffold would need and the registry does not have. */
export function unpublished(names = Object.keys(PUBLISHED)) {
  return names.filter((name) => PUBLISHED[name] === null);
}

/**
 * The range to declare for a dependency, or `null` when there is none to declare.
 *
 * Falls through for anything not in this table: `next`, `react` and the rest are somebody else's
 * packages on their own release cycle, and this file has no business having an opinion about them.
 */
export function rangeFor(name) {
  return Object.hasOwn(PUBLISHED, name) ? PUBLISHED[name] : undefined;
}
