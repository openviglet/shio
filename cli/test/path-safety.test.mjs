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
    },
  },
  filePathFor: {
    guarded: "SH402 — throws on a URL segment that navigates",
    probe: () => filePathFor({ site: "Blog", url: "/../escaped" }),
    collides: {
      values: ["post:Blog/blog/hello", "post:Blog/blog/Hello"],
      detector: postPathCollisions,
    },
  },
  siteDir: {
    guarded: "SH257 — throws on a site name that is dots or holds a separator",
    probe: () => siteDir(".."),
    collides: { values: ["Blog", "blog"], detector: siteDirCollisions },
  },
  folderMarkerPathFor: {
    guarded: "SH403 — throws on a chain segment that navigates; the value is the sitemap's",
    probe: () => folderMarkerPathFor({ site: "Blog", folder: "/../../escaped" }),
    collides: {
      values: ["folder:Blog/news", "folder:Blog/News"],
      detector: markerPathCollisions,
    },
  },
  notesPathFor: {
    guarded: "SH403 — through `siteDir`, so a server site name cannot leave `shio/content`",
    probe: () => notesPathFor(".."),
    // A notes file is `<siteDir>/.memory.yaml`, so its pair is a *site* pair and `siteDir`
    // above owns it. Named here rather than exempted, because the reader should not have to
    // rediscover that the two roots share one cause.
    collides: { values: ["Blog", "blog"], detector: siteDirCollisions },
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
    collides: { values: ["Article", "article"], detector: defFileCollisions },
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
 * And that each detector is actually *called* somewhere (SH408).
 *
 * The test above proves a detector exists and reports the pair. It cannot prove production runs
 * it, and a detector nobody calls is exactly the state assets and post-types were in — a function
 * that would have caught the defect, sitting unused. This is the greppable half: the name has to
 * appear in a module that is not the one defining it.
 */
test("SH408: every declared detector is wired into a command", async () => {
  const named = new Set(
    Object.values(DECLARED)
      .filter((entry) => entry.collides)
      .map((entry) => entry.collides.detector.name),
  );
  const callers = new Map([...named].map((name) => [name, []]));
  for (const dir of ["", "commands/"]) {
    const entries = await readdir(join(SRC, dir), { withFileTypes: true });
    for (const file of entries) {
      if (!file.isFile() || !file.name.endsWith(".mjs")) continue;
      if (file.name === "content-files.mjs") continue; // where they are defined
      const text = await readFile(join(SRC, dir, file.name), "utf8");
      for (const name of named) if (text.includes(name)) callers.get(name).push(dir + file.name);
    }
  }
  const unwired = [...callers].filter(([, files]) => !files.length).map(([name]) => name);
  assert.deepEqual(
    unwired,
    [],
    "these detectors exist and nothing calls them, which is the state that let the defect "
      + "through: a check that would have caught it, sitting unused",
  );
});
