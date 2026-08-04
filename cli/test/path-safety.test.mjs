/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH403 — the instrument SH402 should have landed before its fix.
 *
 * SH402 asked one path builder whether its input navigates the filesystem, found that it did not,
 * fixed it, and shipped. Two others took server data and had never been asked: the sitemap's
 * folder chain and — on a first pull into an empty tree — the site names the server reports. Same
 * write-outside-the-project primitive, one directory over.
 *
 * SH322's rule is that the grep is the deliverable and SH338's is that the instrument lands before
 * the fixes, so this file is the fix's first half. Two properties, and the first is the one that
 * matters:
 *
 *  1. **Every path producer is classified.** Discovery is by name — an exported `*PathFor` or
 *     `*Dir` — plus `EXTRA`, for producers that do not follow the convention. A new one fails this
 *     test until somebody declares it guarded or argues in writing that it needs no guard. That is
 *     the half a hand-written list cannot do: a list of the three builders known in August would
 *     have passed forever and said nothing about the fourth.
 *  2. **Every declared entry refuses a traversal**, by throwing or by answering `null`. Both count:
 *     SH256 chose `null` for asset paths deliberately, and a lint that demanded a throw would have
 *     reported the one builder that was already right.
 *
 * The naming convention is the known limit. A producer called neither `*PathFor` nor `*Dir` and
 * absent from `EXTRA` is invisible here — which is why `EXTRA` exists and why it carries
 * `sidecarsOf`, found by reading rather than by grepping.
 */
import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { test } from "node:test";

import {
  filePathFor,
  folderMarkerPathFor,
  sidecarsOf,
  siteDir,
} from "../src/content-files.mjs";
import { assetPathFor } from "../src/content-assets.mjs";
import { capturedAssetPathFor } from "../src/clone-assets.mjs";
import { notesPathFor } from "../src/content-notes.mjs";
import { fileNameFor } from "../src/post-types-io.mjs";
import {
  assetPathCollisions,
  defFileCollisions,
  markerPathCollisions,
  postPathCollisions,
  siteDirCollisions,
} from "../src/content-files.mjs";

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src");

/** The modules that turn a value into a location under `shio/`. */
const MODULES = [
  "content-files.mjs",
  "content-assets.mjs",
  "content-notes.mjs",
  // SH408 — the post-type root. It was outside this list, so `fileNameFor` was never classified
  // for either question, and it is the one whose collision `push --prune` turns into a deletion.
  "post-types-io.mjs",
  // SH378 — the newest producer, and the only one in the tree whose input is a URL from a
  // **stranger's server**. It writes into `shio/assets`, the same root `assetPathFor` owns, which is
  // why it shares that root's collision detector and not a new one.
  "clone-assets.mjs",
];

/** Producers whose name does not follow the convention, found by reading the module. */
const EXTRA = new Set(["sidecarsOf", "fileNameFor"]);

/**
 * Every path producer, and the traversal it must refuse.
 *
 * `guarded` is prose for the reader; the assertion is that `probe` throws or returns `null`.
 */
const DECLARED = {
  assetPathFor: {
    guarded: "SH256 — answers null for a `.` or `..` segment, deliberately rather than throwing",
    probe: () => assetPathFor({ fileName: "..", siteName: "Blog" }, "/"),
    collides: {
      values: ["Blog/media/Logo.png", "Blog/media/logo.png"],
      detector: assetPathCollisions,
      writtenBy: "commands/content-pull.mjs",
    },
  },
  capturedAssetPathFor: {
    guarded: "SH378 — answers null, like `assetPathFor` whose root it shares. The value that "
      + "reaches it is an encoded **slash**, not a dot segment: a URL parser resolves `%2e%2e` "
      + "(measured — this probe asserted it and was wrong) and deliberately preserves `%2f`, so the "
      + "separator is what a readability decode re-creates",
    probe: () => capturedAssetPathFor("https://x.test/img/..%2f..%2fetc/passwd", "Blog"),
    collides: {
      // The same root as `assetPathFor`, so the same detector — a second one over `shio/assets`
      // would be two answers to one question, and the pair would be reported by whichever the
      // writer happened to call.
      values: ["Blog/img/Logo.png", "Blog/img/logo.png"],
      detector: assetPathCollisions,
      writtenBy: "commands/clone.mjs",
    },
  },
  filePathFor: {
    guarded: "SH402 — throws on a URL segment that navigates",
    probe: () => filePathFor({ site: "Blog", url: "/../escaped" }),
    collides: {
      values: ["post:Blog/blog/hello", "post:Blog/blog/Hello"],
      detector: postPathCollisions,
      writtenBy: "commands/content-pull.mjs",
    },
  },
  siteDir: {
    guarded: "SH257 — throws on a site name that is dots or holds a separator",
    probe: () => siteDir(".."),
    collides: {
      values: ["Blog", "blog"],
      detector: siteDirCollisions,
      writtenBy: "commands/content-pull.mjs",
    },
  },
  folderMarkerPathFor: {
    guarded: "SH403 — throws on a chain segment that navigates; the value is the sitemap's",
    probe: () => folderMarkerPathFor({ site: "Blog", folder: "/../../escaped" }),
    collides: {
      values: ["folder:Blog/news", "folder:Blog/News"],
      detector: markerPathCollisions,
      writtenBy: "commands/content-pull.mjs",
    },
  },
  notesPathFor: {
    guarded: "SH403 — through `siteDir`, so a server site name cannot leave `shio/content`",
    probe: () => notesPathFor(".."),
    // A notes file is `<siteDir>/.memory.yaml`, so its pair is a *site* pair and `siteDir`
    // above owns it. Named here rather than exempted, because the reader should not have to
    // rediscover that the two roots share one cause.
    collides: {
      values: ["Blog", "blog"],
      detector: siteDirCollisions,
      writtenBy: "commands/content-pull.mjs",
    },
  },
  sidecarsOf: {
    guarded: "throws on a field name holding a separator; a dotted name stays inside the post's "
      + "own directory, because the name is derived from the post stem and never from a value",
    probe: () => sidecarsOf(
      { site: "Blog", url: "/blog/hello", data: { "../escaped": "x" } },
      [{ name: "../escaped", widget: "Ace Editor - HTML" }],
    ),
    // A sidecar's name comes from its post's stem, so two of them collide exactly when their
    // posts do — which `filePathFor` above already answers for. Nothing separate to check.
    collides: null,
  },
  fileNameFor: {
    guarded: "SH408 — throws on a separator or a dots-only name. The prose here first claimed the "
      + "server refused it; the lint declined to take that on trust, which is the point of a probe",
    probe: () => fileNameFor({ name: ".." }),
    collides: {
      values: ["Article", "article"],
      detector: defFileCollisions,
      // The command whose loop is `writeFile(join(target, fileNameFor(def)), …)` — *not*
      // `content-pull.mjs`, which reads defs to map File fields and writes none (SH409).
      writtenBy: "commands/pull.mjs",
    },
  },
};

test("SH403: every path producer in the projection is classified", async () => {
  const found = new Set();
  for (const module of MODULES) {
    const text = await readFile(join(SRC, module), "utf8");
    for (const match of text.matchAll(/^export function ([A-Za-z0-9_]+)/gm)) {
      const name = match[1];
      if (/PathFor$|Dir$/.test(name) || EXTRA.has(name)) found.add(name);
    }
  }
  // The point of the test. A new `*PathFor` lands here before it lands in a release, and the
  // author has to say which of the two it is rather than leave the question unasked.
  assert.deepEqual(
    [...found].sort(),
    Object.keys(DECLARED).sort(),
    "a path producer is undeclared (or a declared one was renamed) — classify it in DECLARED, "
      + "with a probe that shows it refuses a traversal, or state why it needs no guard",
  );
});

for (const [name, entry] of Object.entries(DECLARED)) {
  test(`SH403: ${name} refuses a value that navigates`, () => {
    let answered;
    try {
      answered = entry.probe();
    } catch (error) {
      // A refusal has to say what is wrong with the value, not fail somewhere three layers down
      // — SH258's rule, applied to the guard rather than to the round trip.
      assert.match(
        error.message,
        /shio content:/,
        `${name} threw, but not its own refusal: ${error.message}`,
      );
      return;
    }
    assert.equal(
      answered,
      null,
      `${name} projected a traversing value to ${JSON.stringify(answered)} instead of refusing `
        + `it. Declared as: ${entry.guarded}`,
    );
  });
}

/**
 * The second question, asked of every root (SH408).
 *
 * SH403's lint asks whether a producer refuses a value that *navigates*. SH407 was a defect it
 * could not catch, because what mattered was whether two of its outputs *collide* — and SH407
 * then fixed that root by hand, leaving the instrument as unable to find the next one as before.
 * Three repetitions of that pattern is enough: the question is asked here, of everything.
 *
 * **A producer is allowed to fold.** `filePathFor` gives `hello.md` and `Hello.md` and always
 * will; what SH401 built is a *detector* in the caller. So the assertion is not "the outputs
 * differ" — that would condemn the two roots that are already right — but "something reports the
 * pair". Each entry names the detector it relies on, and the test runs it.
 *
 * `collides: null` declares that a producer's outputs cannot fold independently of another's.
 */
for (const [name, entry] of Object.entries(DECLARED)) {
  if (entry.collides === null) continue;
  test(`SH408: a folding pair from ${name} is detected, not silently kept`, () => {
    const { values, detector } = entry.collides;
    assert.equal(
      typeof detector,
      "function",
      `${name} has no detector. It must name one exported from src/ — a lint that builds its own `
        + "would pass whatever production does, which is the vacuous version of this test.",
    );
    const found = detector(values);
    assert.deepEqual(
      [...found.keys()].sort(),
      [...values].sort(),
      `${name} projects ${JSON.stringify(values)} to one file on a case-insensitive filesystem, `
        + "and its detector does not report the pair. Wire it in where the projection writes — "
        + "SH401 for posts, SH407 for folder markers — or declare `collides: null` with the "
        + "reason the pair cannot arise on its own.",
    );
  });
}

/**
 * And that the module which **writes** each root calls its detector (SH408, corrected by SH409).
 *
 * SH408's version greped for the detector's name in any module but the defining one. That proves
 * *a* caller exists — and `defFileCollisions` had one, in `content-pull.mjs`, which reads
 * post-type defs to map `File` fields and writes none. The command that writes them, `pull.mjs`,
 * still folded `Article.json` and `article.json` in silence. The lint was green.
 *
 * That was the third draft of this assertion to check a proxy: the first demanded the producer not
 * fold, the second ran a detector the test built itself, the third greped for a name anywhere. The
 * claim worth asserting is narrower and is the one that matters — *this root's writer consults
 * this detector* — so each entry names the module whose write it guards, and that file is the only
 * one searched.
 */
test("SH408: the module that writes each root consults its detector", async () => {
  const missing = [];
  for (const [name, entry] of Object.entries(DECLARED)) {
    if (!entry.collides) continue;
    const { detector, writtenBy } = entry.collides;
    const text = await readFile(join(SRC, writtenBy), "utf8").catch(() => null);
    assert.ok(text !== null, `${name} names ${writtenBy}, which does not exist`);
    if (!text.includes(detector.name)) missing.push(`${name} → ${writtenBy}`);
  }
  assert.deepEqual(
    missing,
    [],
    "the module that writes this root does not call its detector, which is the state that let "
      + "SH409 through: a check wired to a command that could not cause the condition",
  );
});
