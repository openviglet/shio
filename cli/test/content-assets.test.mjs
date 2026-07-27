/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH145 — the asset projection's pure half: which fields hold a file, what an asset's path
 * is, and the two rewrites that keep ids off the disk and paths off the wire.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  assetLocationOf,
  assetPathFor,
  fileFields,
  idsToPaths,
  pathsToIds,
} from "../src/content-assets.mjs";

const DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "hero", widget: "File", ordinal: 2 },
      { name: "attachment", widget: "File", ordinal: 3 },
      {
        name: "gallery",
        widget: "Relator",
        ordinal: 4,
        fields: [{ name: "image", widget: "File", ordinal: 1 }],
      },
    ],
  },
  { name: "Tag", fields: [{ name: "label", widget: "Text", ordinal: 1 }] },
];

const post = (data) => ({ site: "Blog", url: "/blog/hello", type: "Article", folder: "/blog", data });

test("File fields are read from the model, including inside a Relator", () => {
  const map = fileFields(DEFS);

  // Nested counts: an image inside a repeatable item is exactly as much of an asset as one at
  // the top level, and the model is right there to say so.
  assert.deepEqual([...map.get("Article")].sort(), ["attachment", "hero", "image"]);
  assert.deepEqual([...map.get("Tag")], []);
});

test("an asset's path is its site, its folder chain and its file name", () => {
  // Not the post's friendly URL, which is what every other projection path is: a `File` post
  // has none (upload never assigns one — the reason SH172 exists). So an asset is addressed the
  // way the server already thinks about files, which is the pair `fileExists` checks.
  assert.equal(assetPathFor({ siteName: "Blog", fileName: "logo.png" }, "/media"), "Blog/media/logo.png");
  assert.equal(assetPathFor({ siteName: "Blog", fileName: "logo.png" }, "/"), "Blog/logo.png");
  assert.deepEqual(assetLocationOf("Blog/media/logo.png"), {
    site: "Blog",
    folder: "/media",
    fileName: "logo.png",
  });
  assert.deepEqual(assetLocationOf("Blog/logo.png"), {
    site: "Blog",
    folder: "/",
    fileName: "logo.png",
  });
  // One segment is not an asset path — there is no site to put it in.
  assert.equal(assetLocationOf("logo.png"), null);
});

test("only declared File fields are rewritten, never a Text field holding a UUID", () => {
  const uuid = "11111111-2222-3333-4444-555555555555";
  const byId = new Map([[uuid, "Blog/media/logo.png"]]);
  const { doc } = idsToPaths(post({ headline: uuid, hero: uuid }), fileFields(DEFS), byId);

  assert.equal(doc.data.hero, "Blog/media/logo.png");
  // Guessing from the *shape* of a value would rewrite this one, and the model is available.
  assert.equal(doc.data.headline, uuid);
});

test("an id with no known asset is left alone and reported", () => {
  const uuid = "99999999-2222-3333-4444-555555555555";
  const { doc, unresolved } = idsToPaths(post({ hero: uuid }), fileFields(DEFS), new Map());

  // Inventing a path would put a broken one in the tree, which pushes back as a failed upload
  // instead of the dangling reference the lint already knows how to report.
  assert.equal(doc.data.hero, uuid);
  assert.deepEqual(unresolved, [uuid]);
});

test("the write direction turns paths back into ids and names what it cannot resolve", () => {
  const byPath = new Map([["Blog/media/logo.png", { id: "abc" }]]);
  const { doc, unresolved } = pathsToIds(
    post({ hero: "Blog/media/logo.png", attachment: "Blog/media/gone.pdf" }),
    fileFields(DEFS),
    byPath,
  );

  assert.equal(doc.data.hero, "abc");
  assert.equal(doc.data.attachment, "Blog/media/gone.pdf");
  assert.deepEqual(unresolved, ["Blog/media/gone.pdf"]);
});

test("a post whose type declares no File field is returned untouched", () => {
  const tag = { site: "Blog", url: "/t", type: "Tag", data: { label: "news" } };
  const { doc } = idsToPaths(tag, fileFields(DEFS), new Map());

  assert.equal(doc, tag, "the same object, so a no-op costs nothing");
});
