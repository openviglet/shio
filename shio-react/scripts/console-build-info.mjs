/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { createHash } from "node:crypto";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join, posix, relative, sep } from "node:path";

/**
 * SH1126 — which tree a console build came from, so a size verdict is about the change under test.
 *
 * <h2>The two verdicts that were about somebody else's bytes</h2>
 *
 * <p>SH1043's comparison runs against whatever build sits in
 * `shio-app/src/main/resources/public`, and `pnpm test` never builds one. So the gate measured a
 * build of <em>some</em> tree with nothing saying which — seen on consecutive tasks, in both
 * directions: SH1059 passed `pnpm test` against a build that predated its own change, and the next
 * task failed the headroom check only because a build made outside the session happened to include
 * it. Neither verdict was about the bytes the task under test had produced.</p>
 *
 * <p>The conformance gate had the same shape and SH460 closed it there: the instance states its build
 * time and commit, and the harness refuses one older than HEAD. A console build can carry the same
 * identity, and this is it — {@link BUILD_INFO} beside `index.html`, holding the commit and a digest
 * of the sources the bundle is built from.</p>
 *
 * <h2>The digest is the comparison; the commit is for a person</h2>
 *
 * <p>A commit cannot be compared against a working tree — the tree is dirty most of the time a
 * developer runs this, and refusing a dirty tree would mean refusing every build anybody makes while
 * working. The digest is over the file contents, so it matches a dirty tree exactly when the build
 * was made from it. The commit is recorded because it is what a reader wants when the digests differ
 * and they are trying to work out which build they have.</p>
 *
 * <h2>What it covers, and the limit worth stating</h2>
 *
 * <p>§LXXII.23 says "a digest of `shio-react/src`". It is that plus the three files outside `src`
 * that decide what the bundle contains — `index.html` is the entry, `vite.config.ts` decides the
 * chunking, and `package.json` names the dependencies. What it does <b>not</b> cover is the installed
 * dependency bytes: a `pnpm update` that changes a vendor chunk without touching any of these leaves
 * the digest matching. That is a real gap and it is the right trade — the failure this closes was
 * seen twice and both times it was a source edit, and hashing `node_modules` would cost more than
 * the build it is protecting. The lockfile is deliberately out too: it moves on an install that
 * changes nothing the bundle sees.</p>
 */

/** Emitted beside `index.html`, so an instance serving the build can answer the same question. */
export const BUILD_INFO = "build-info.json";

/**
 * The inputs whose bytes decide what the bundle contains, relative to `shio-react/`.
 *
 * <p>Exported because it is the decision rather than a detail: a reader asking "would this change
 * have been caught" is asking about this list.</p>
 */
export const SOURCE_INPUTS = ["src", "index.html", "vite.config.ts", "package.json"];

/** Every file under `root` that the digest covers, as `/`-joined paths, sorted (P5). */
function inputsOf(root) {
  const found = [];
  const walk = (rel) => {
    const absolute = join(root, rel);
    if (!existsSync(absolute)) return;
    if (!statSync(absolute).isDirectory()) {
      found.push(rel.split(sep).join(posix.sep));
      return;
    }
    for (const entry of readdirSync(absolute).sort()) {
      // A dot-directory here is a tool's, never a source the bundle is built from.
      if (entry.startsWith(".")) continue;
      walk(join(rel, entry));
    }
  };
  for (const input of SOURCE_INPUTS) walk(input);
  return found.sort();
}

/**
 * `sha256:<hex>` over the console's sources, as a build and a test both compute it.
 *
 * <p>Path and bytes, in sorted path order, so a rename is a different digest and the filesystem's
 * own ordering cannot change the answer (P5). One function rather than two callers agreeing: a build
 * that hashed differently from the test would report a mismatch on every run, which is the same
 * "reports nothing forever" failure this closes pointing the other way.</p>
 *
 * @param root the `shio-react` directory
 */
export function sourceDigest(root) {
  const hash = createHash("sha256");
  for (const rel of inputsOf(root)) {
    hash.update(rel);
    hash.update("\u0000");
    hash.update(readFileSync(join(root, rel)));
    hash.update("\u0000");
  }
  return `sha256:${hash.digest("hex")}`;
}

/**
 * What a build says about itself, or `null` when it says nothing.
 *
 * <p>`null` for an absent or unreadable file rather than a throw: a build from before this existed
 * is an old build, which is exactly the case the caller has to report as "not measured", and failing
 * to parse it would turn that into a crash in a gate whose whole point is to say so calmly.</p>
 */
export function readBuildInfo(dist) {
  try {
    const parsed = JSON.parse(readFileSync(join(dist, BUILD_INFO), "utf8"));
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * Whether the build in `dist` was made from the tree at `root`, and why not when it was not.
 *
 * <p>One function, three answers, because the caller has to say the same thing for all three: a
 * number from a different tree is worse than no number, so "there is no build", "the build predates
 * this existing" and "the build is from another tree" all mean <b>not measured</b>. What differs is
 * only the sentence, and each names what to do.</p>
 *
 * @returns {{ measurable: boolean, why: string, info: object|null }}
 */
export function buildProvenance(dist, root) {
  if (!existsSync(join(dist, "index.html"))) {
    return {
      measurable: false,
      info: null,
      why: "there is no built console in this tree, so nothing was measured. Build it with "
        + "`pnpm run build:console`.",
    };
  }
  const info = readBuildInfo(dist);
  if (!info?.sourceDigest) {
    return {
      measurable: false,
      info,
      why: `the build in this tree carries no ${BUILD_INFO}, so it cannot say which sources it was `
        + "made from and nothing was measured. It predates SH1126 — rebuild it with "
        + "`pnpm run build:console`.",
    };
  }
  const digest = sourceDigest(root);
  if (info.sourceDigest !== digest) {
    return {
      measurable: false,
      info,
      why: `the build in this tree was made from different sources (${short(info.sourceDigest)}) `
        + `than the ones here (${short(digest)}), so nothing was measured — a number from another `
        + `tree is worse than no number. It was built from commit ${info.commit ?? "unknown"}; `
        + "rebuild it with `pnpm run build:console`.",
    };
  }
  return { measurable: true, info, why: "" };
}

/** Enough of a digest to tell two apart in a sentence somebody reads. */
function short(digest) {
  return String(digest).replace(/^sha256:/, "").slice(0, 12);
}
