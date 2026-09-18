/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1032 — an asset's own words in the tree, and the merge that keeps them.
 *
 * ## What is actually at risk here
 *
 * Not "does the file get written" — that fails loudly. The three failures this guards are all
 * silent, and each one was a real branch in the first draft:
 *
 * 1. **A delete coming back.** `reconcileBytes` answers `local-deleted` when the tree removed a
 *    key the remote still holds, and folding that verdict into the remote-wins arm reads
 *    perfectly well: the remote *does* have a value. It hands the curator back the alt text they
 *    just deleted, on the very next pull, for ever.
 * 2. **A reordering read as an edit.** `tags` is an array and the order an instance answers in is
 *    not something a person authored. Un-normalised, every tagged asset is permanently
 *    `local-changed` and every push sends them all back.
 * 3. **An emptied file left behind.** A metadata file kept after its last key was cleared says
 *    "described" to every reader that checks for the file, and the next push re-sends the words.
 *
 * None of the three throws, logs, or shows up in a diff of the code that has it.
 */
import { strict as assert } from "node:assert";
import { join } from "node:path";
import { test } from "node:test";

import {
  ASSET_META_KEYS,
  assetMetaOf,
  metaPathFor,
  parseAssetMeta,
  reconcileAssetMeta,
  serializeAssetMeta,
} from "../src/content-assets.mjs";
import { readAssetMetaOrNull, writeAssetMeta } from "../src/content-store.mjs";

/** An in-memory tree: enough fs for the two store functions under test. */
function memory(files = {}) {
  const store = new Map(Object.entries(files));
  return {
    store,
    ctx: {
      cwd: "/repo",
      fs: {
        async readFile(path) {
          if (!store.has(path)) {
            const error = new Error(`ENOENT: ${path}`);
            error.code = "ENOENT";
            throw error;
          }
          return store.get(path);
        },
        async writeFile(path, body) { store.set(path, body); },
        async mkdir() {},
        async rm(path) { store.delete(path); },
      },
    },
  };
}

// Through `join`, because the store does: on Windows the separator is a backslash and a
// test that hard-codes `/` asserts the harness rather than the writer.
const AT = (path) => join("/repo", "shio/asset-meta", path);

// --- the format ---------------------------------------------------------------------------

test("SH1032: the file is byte-identical for equal input, whatever order it arrived in", () => {
  const a = serializeAssetMeta({ tags: ["b", "a"], alt: "A", caption: "C" });
  const b = serializeAssetMeta({ caption: "C", alt: "A", tags: ["b", "a"] });

  assert.equal(a, b, "SH1032: insertion order must not reach the file (P5)");
  assert.equal(a, '{\n  "alt": "A",\n  "caption": "C",\n  "tags": [\n    "b",\n    "a"\n  ]\n}\n');
  assert.deepEqual(Object.keys(JSON.parse(a)), ASSET_META_KEYS,
    "SH1032: the declared key order, not the caller's — a projection that reordered when the "
    + "instance changed its serializer would show every asset as edited");
});

test("SH1032: an empty value is an absent key, not an empty string", () => {
  assert.deepEqual(assetMetaOf({ alt: "   ", caption: "", tags: [] }), {},
    "SH1032: a file that exists must say something about every key it names");
  assert.deepEqual(assetMetaOf({ tags: ["b", "a", "b", " a ", ""] }), { tags: ["a", "b"] },
    "SH1032: trimmed, de-duplicated and sorted — the server's order is not an authored fact");
});

test("SH1032: a malformed file is refused rather than read as empty", () => {
  // The refusal is the point: this file is a WRITE on the next push, so a broken one read as `{}`
  // would clear the alt text of every image it names. SH374's rule, one artefact over.
  assert.throws(() => parseAssetMeta("{", "x.json"), /not valid JSON/);
  assert.throws(() => parseAssetMeta("[]", "x.json"), /must hold a JSON object/);
  assert.throws(() => parseAssetMeta('{"focalPoint":"0,0"}', "x.json"),
    /focalPoint, which an asset does not carry/,
    "SH1032: and it names what is allowed — P6 applies to the file format too");
});

test("SH1032: a path that navigates rather than names has no metadata file", () => {
  assert.equal(metaPathFor("Blog/img/hero.png"), "Blog/img/hero.png.json");
  assert.equal(metaPathFor("Blog/../../etc/passwd"), null,
    "SH1032/SH403: guarded here, not argued from the caller — that every path it receives today "
    + "comes from `assetPathFor` is a property of today's call graph, not of this function");
  assert.equal(metaPathFor(""), null);
});

// --- the merge ----------------------------------------------------------------------------

const merged = (sides) => reconcileAssetMeta(sides).merged;

test("SH1032: a key only the server moved arrives, and one only the tree moved stays", () => {
  assert.deepEqual(merged({ base: {}, remote: { alt: "A" }, local: {} }), { alt: "A" });
  assert.deepEqual(merged({ base: { alt: "A" }, remote: { alt: "A" }, local: { alt: "L" } }),
    { alt: "L" });
});

test("SH1032: two writers on different keys of one image do not conflict", () => {
  const { merged: both, conflicts } = reconcileAssetMeta({
    base: {}, remote: { alt: "From the agent" }, local: { caption: "From the curator" },
  });

  // The reason the merge is per key rather than per file. An agent describing an image and a
  // curator captioning it is the expected pair, not the exceptional one, and a whole-file hash
  // would report it as a collision between two edits that do not touch.
  assert.deepEqual(both, { alt: "From the agent", caption: "From the curator" });
  assert.deepEqual(conflicts, []);
});

test("SH1032: both sides changing one key is a conflict, and the tree's value is kept", () => {
  const { merged: kept, conflicts } = reconcileAssetMeta({
    base: { alt: "A" }, remote: { alt: "R" }, local: { alt: "L" },
  });

  assert.deepEqual(kept, { alt: "L" },
    "SH1032: the tree is what a person edits, so a pull that overwrote it would lose the edit "
    + "silently — the same side `writeDoc` takes with a field");
  assert.deepEqual(conflicts, ["alt"]);
});

test("SH1032: a delete is honoured on EITHER side", () => {
  assert.deepEqual(merged({ base: { alt: "A" }, remote: {}, local: { alt: "A" } }), {},
    "SH1032: the server cleared it");
  assert.deepEqual(merged({ base: { alt: "A" }, remote: { alt: "A" }, local: {} }), {},
    "SH1032: THE TREE cleared it — `local-deleted` means the tree removed the key, and reading it "
    + "as \"the remote still has a value, take that\" hands the curator back the alt text they "
    + "just deleted, on the very next pull. It reads plausibly, it never throws, and it is the "
    + "one branch in this function that can lose a person's work.");
});

test("SH1032: a reordering by the server is not an edit", () => {
  const { merged: same, conflicts } = reconcileAssetMeta({
    base: { tags: ["a", "b"] }, remote: { tags: ["b", "a"] }, local: { tags: ["a", "b"] },
  });

  assert.deepEqual(same, { tags: ["a", "b"] });
  assert.deepEqual(conflicts, [],
    "SH1032: normalised INSIDE the merge rather than by its callers — three callers would each "
    + "have had to remember, and the one that forgot would report every tagged asset as changed "
    + "for ever and push them all back");
});

// --- the disk -----------------------------------------------------------------------------

test("SH1032: writing then reading round-trips the three keys", async () => {
  const { ctx, store } = memory();

  await writeAssetMeta(ctx, "Blog/img/hero.png", { alt: "A hero", tags: ["x"] });

  assert.ok(store.has(AT("Blog/img/hero.png.json")), "SH1032: named after the asset, plus .json");
  assert.deepEqual(await readAssetMetaOrNull(ctx, "Blog/img/hero.png"),
    { alt: "A hero", tags: ["x"] });
});

test("SH1032: clearing the last key removes the file rather than leaving an empty one", async () => {
  const { ctx, store } = memory();
  await writeAssetMeta(ctx, "Blog/img/hero.png", { alt: "A hero" });

  const wrote = await writeAssetMeta(ctx, "Blog/img/hero.png", {});

  assert.equal(wrote, null);
  assert.equal(store.has(AT("Blog/img/hero.png.json")), false,
    "SH1032: a file kept after its last key was cleared says \"described\" to every reader that "
    + "only checks for the file — `verify`'s finding, a grep, the next push — and the next push "
    + "would send back the words a curator deleted. An empty document and an absent one have to "
    + "mean the same thing, so only one of them is allowed to exist.");
  assert.equal(await readAssetMetaOrNull(ctx, "Blog/img/hero.png"), null);
});

test("SH1032: an asset nothing describes reads as null, not as an error", async () => {
  const { ctx } = memory();
  assert.equal(await readAssetMetaOrNull(ctx, "Blog/img/never.png"), null);
});
