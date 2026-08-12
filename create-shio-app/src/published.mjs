/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
 *
 * The `null` form is kept now that nothing uses it (SH502). It is the shape the next unpublished
 * package needs, and the two readers that branch on it — `init`'s warning and the SH448 conformance
 * — are written against `unpublished()` rather than against a fixed list, so they already work in
 * both worlds. Deleting it would mean rediscovering the answer the next time a package is added
 * ahead of its release, which is the situation these two rows spent two blocks in.
 */
export const PUBLISHED = {
  "@viglet/shio-client": "^2026.2.1",
  // SH502 — these were `null` for two blocks: SH156 made them publishable and no release had run, so
  // both answered E404 and the section-carrying starters (`marketing-site`, `landing`), which import
  // the vocabulary, could not be made installable by choosing a different range. Only by publishing.
  //
  // The release landed on 2026-08-12 at `2026.3.2` and both now resolve. Caret on the published
  // version, the same rule `@viglet/shio-client` follows one line up and for the same reason: it
  // resolves today and picks up the next release without this file being edited again.
  "@viglet/shio-model": "^2026.3.2",
  "@viglet/shio-sections": "^2026.3.2",
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
