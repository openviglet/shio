/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH145 — the asset projection's pure half: which fields hold a file, what an asset's path
 * is, and the two rewrites that keep ids off the disk and paths off the wire.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  assetLocationOf,
  assetPathFor,
  assetSizeDiffers,
  fileFields,
  idsToPaths,
  pathsToIds,
  reconcileBytes,
  sha256Of,
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

test("an asset path that navigates is refused, not read as a location or an id (SH256)", () => {
  // The measured failure: `readAssetBytes` joins the value under `shio/assets`, so this resolved
  // to `/etc/passwd` — outside the project entirely — and `push --content` would have read those
  // bytes and uploaded them as an asset, while `folder.upsert` was handed `/../../../../etc`.
  assert.throws(
    () => assetLocationOf("Blog/../../../../etc/passwd"),
    /navigates the filesystem instead of naming an asset/,
  );
  // `.` too: it is harmless to read and it makes two paths for one asset, which breaks the
  // idempotence the whole design rests on.
  assert.throws(() => assetLocationOf("Blog/./logo.png"), /navigates the filesystem/);
  // A refusal and not `null`, because null means "this is an id, leave it alone" (SH145) — which
  // would send the traversal to the server as a dangling reference and never mention it.
  assert.notEqual(assetLocationOf("Blog/media/logo.png"), null);
});

test("a file name that cannot be a path segment has no asset path (SH256)", () => {
  // The pair used to be non-injective: this projected to `Blog/media/a/b.png` and read back as
  // folder `/media/a`, file `b.png` — a folder nobody asked for, the wrong name, and a collision
  // with any real asset there.
  assert.equal(assetPathFor({ siteName: "Blog", fileName: "a/b.png" }, "/media"), null);
  assert.equal(assetPathFor({ siteName: "Blog", fileName: "a\\b.png" }, "/media"), null);
  assert.equal(assetPathFor({ siteName: "Blog", fileName: ".." }, "/media"), null);
  // Every segment, not just the file name: the chain comes from the sitemap and the site from
  // the media listing, and one bad segment is enough to escape.
  assert.equal(assetPathFor({ siteName: "Blog", fileName: "logo.png" }, "/../etc"), null);
  assert.equal(assetPathFor({ siteName: "..", fileName: "logo.png" }, "/media"), null);
  // null rather than a throw: the bad input is the server's data, and taking a whole pull down
  // over one unprojectable asset is SH173's mistake. The caller skips it and names it.
  assert.equal(assetPathFor({ siteName: "Blog", fileName: "logo.png" }, "/media"), "Blog/media/logo.png");
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

// --- telling an edited asset from an untouched one (SH210) ---

test("a length that differs is an answer, and a length that matches is not", () => {
  // The whole point of the two-valued return: `true` is decided for free, `null` costs one digest.
  // A boolean here would have forced the caller to guess which, and the cheap guess ("same length,
  // same bytes") is the silent no-op SH210 is about.
  assert.equal(assetSizeDiffers(new Uint8Array(4), 7), true);
  assert.equal(assetSizeDiffers(new Uint8Array(4), 4), null);
});

test("a size of -1 is the server saying its bytes are gone, and the tree has them", () => {
  assert.equal(assetSizeDiffers(new Uint8Array(4), -1), true);
});

test("a size the inventory did not report is undecided, never a replacement", () => {
  // `true` here would upload every referenced asset on every push against an instance that
  // reported one field differently — a silent no-op traded for a silent stampede.
  assert.equal(assetSizeDiffers(new Uint8Array(4), undefined), null);
  assert.equal(assetSizeDiffers(new Uint8Array(4), "4"), null);
});

test("length is counted in bytes, not in code units", () => {
  // An accented character in an svg is two bytes and one code unit, so a `String#length`
  // comparison against a server-side byte count reads as changed on every push, for ever.
  const accented = "é";
  assert.equal(assetSizeDiffers(accented, 2), null, "two bytes, and the server said two");
  assert.equal(assetSizeDiffers(accented, 1), true);
});

test("the digest is over the bytes, however the tree handed them over", () => {
  // `ctx.fs` is a seam: node hands back a Buffer, an in-memory fs hands back a string. A digest
  // that differed between the two would report every asset as edited in exactly one of them.
  const text = "hero image";
  assert.equal(sha256Of(text), sha256Of(new TextEncoder().encode(text)));
  assert.match(sha256Of(text), /^[0-9a-f]{64}$/);
  assert.notEqual(sha256Of(text), sha256Of("hero images"));
});

// --- the SH89 merge, for one asset's bytes (SH373) ---

const A = "hash-of-a";
const B = "hash-of-b";

test("one side moving takes that side, with no conflict", () => {
  // The defect this closes on the pull side: a server-side asset edit never reached the tree,
  // because a path already on disk was skipped. And on the push side, the newer one: replacing
  // whenever the bytes differ reverts a curator's edit whenever the tree did not touch it.
  assert.equal(reconcileBytes({ base: A, remote: B, local: A }), "remote-changed");
  assert.equal(reconcileBytes({ base: A, remote: A, local: B }), "local-changed");
  assert.equal(reconcileBytes({ base: A, remote: A, local: A }), "unchanged");
  assert.equal(reconcileBytes({ base: B, remote: A, local: A }), "unchanged");
});

test("both sides moving is a conflict, because there is no merging two images", () => {
  assert.equal(reconcileBytes({ base: A, remote: B, local: "hash-of-c" }), "conflict");
});

test("with no base and two different values, nothing says which moved", () => {
  // `conflict` is the only honest answer and the only one that cannot lose work. The *push* treats
  // a missing agreement as "the tree is authoritative" before it gets here, deliberately: a pull
  // that guesses can destroy the only copy of something, and a push that guesses cannot.
  assert.equal(reconcileBytes({ base: null, remote: A, local: B }), "conflict");
  assert.equal(reconcileBytes({ base: null, remote: A, local: A }), "unchanged");
});

test("an absence on one side is an addition or a deletion, never a silent overwrite", () => {
  assert.equal(reconcileBytes({ base: null, remote: A, local: null }), "remote-added");
  assert.equal(reconcileBytes({ base: null, remote: null, local: A }), "local-added");
  assert.equal(reconcileBytes({ base: A, remote: A, local: null }), "local-deleted");
  assert.equal(reconcileBytes({ base: A, remote: null, local: A }), "remote-deleted");
  assert.equal(reconcileBytes({ base: null, remote: null, local: null }), "both-deleted");
});

test("an absence on one side and an edit on the other is a conflict", () => {
  // Deleted there, edited here: deleting the file throws away an edit, keeping it resurrects
  // something somebody removed. The same rule a post follows (SH89).
  assert.equal(reconcileBytes({ base: A, remote: null, local: B }), "conflict");
  assert.equal(reconcileBytes({ base: A, remote: B, local: null }), "conflict");
});
