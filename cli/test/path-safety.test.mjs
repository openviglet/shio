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
import { readFile } from "node:fs/promises";
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

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "src");

/** The modules that turn a value into a location under `shio/`. */
const MODULES = ["content-files.mjs", "content-assets.mjs", "content-notes.mjs"];

/** Producers whose name does not follow the convention, found by reading the module. */
const EXTRA = new Set(["sidecarsOf"]);

/**
 * Every path producer, and the traversal it must refuse.
 *
 * `guarded` is prose for the reader; the assertion is that `probe` throws or returns `null`.
 */
const DECLARED = {
  assetPathFor: {
    guarded: "SH256 — answers null for a `.` or `..` segment, deliberately rather than throwing",
    probe: () => assetPathFor({ fileName: "..", siteName: "Blog" }, "/"),
  },
  filePathFor: {
    guarded: "SH402 — throws on a URL segment that navigates",
    probe: () => filePathFor({ site: "Blog", url: "/../escaped" }),
  },
  siteDir: {
    guarded: "SH257 — throws on a site name that is dots or holds a separator",
    probe: () => siteDir(".."),
  },
  folderMarkerPathFor: {
    guarded: "SH403 — throws on a chain segment that navigates; the value is the sitemap's",
    probe: () => folderMarkerPathFor({ site: "Blog", folder: "/../../escaped" }),
  },
  notesPathFor: {
    guarded: "SH403 — through `siteDir`, so a server site name cannot leave `shio/content`",
    probe: () => notesPathFor(".."),
  },
  sidecarsOf: {
    guarded: "throws on a field name holding a separator; a dotted name stays inside the post's "
      + "own directory, because the name is derived from the post stem and never from a value",
    probe: () => sidecarsOf(
      { site: "Blog", url: "/blog/hello", data: { "../escaped": "x" } },
      [{ name: "../escaped", widget: "Ace Editor - HTML" }],
    ),
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
