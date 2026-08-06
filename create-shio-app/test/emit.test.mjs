/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH119 — the starter exists twice on disk and must be the same starter.
 *
 * `template/` is authored; `blueprints/nextjs-starter/` is generated from it by
 * `npm run emit`, because a running Shio reads packages off the classpath and cannot run
 * a JS build. Two copies are therefore unavoidable, and an unavoidable second copy needs
 * a test that fails the moment it drifts — otherwise `npm create shio-app` and
 * `shio init --blueprint nextjs-starter` slowly stop producing the same project, and
 * nothing tells anybody. Same discipline as `shio-sections`' emit test.
 *
 * If this fails: run `pnpm -C create-shio-app run emit` and review the diff.
 */
import assert from "node:assert/strict";
import { test } from "node:test";
import { readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { join } from "node:path";

import { readTemplateFiles } from "../src/scaffold.mjs";
import {
  PACKAGE_DIR,
  PACKAGES,
  compose,
  expectedManifest,
  expectedSources,
  fileRefs,
  filesFor,
  resolveNeeds,
} from "../src/emit.mjs";

/** Every generated package's directory, from the one list `emit` iterates. */
const dirOf = (name) => join(PACKAGE_DIR, "..", name);

test("the nextjs-starter package exists and is a valid manifest", async () => {
  assert.ok(existsSync(PACKAGE_DIR), `missing blueprint package at ${PACKAGE_DIR}`);
  const manifest = JSON.parse(await readFile(join(PACKAGE_DIR, "blueprint.json"), "utf8"));
  assert.equal(manifest.name, "nextjs-starter");
  assert.equal(manifest.formatVersion, 1);
  // The one parameter the starter needs is the project's own name, and it has to be
  // declared or the registry rejects the package's own `{{projectName}}` at load.
  assert.ok(manifest.params?.properties?.projectName, "projectName must be a declared param");
  assert.ok(manifest.postTypes?.length, "the starter provisions a model");
  assert.ok(manifest.content?.length, "the starter brings content to render");
});

test("the manifest's files list matches the template tree", async () => {
  const files = await readTemplateFiles();
  const manifest = JSON.parse(await readFile(join(PACKAGE_DIR, "blueprint.json"), "utf8"));
  assert.deepEqual(
    manifest.files,
    fileRefs(files),
    "blueprint.json `files` is stale — run `pnpm -C create-shio-app run emit`"
  );
});

test("every carried file is byte-identical to the template", async () => {
  const files = await readTemplateFiles();
  for (const [source, text] of expectedSources(files)) {
    const onDisk = await readFile(join(PACKAGE_DIR, source), "utf8").catch(() => null);
    assert.notEqual(onDisk, null, `${source} is missing — run emit`);
    assert.equal(onDisk, text, `${source} has drifted from the template — run emit`);
  }
});

test("the rest of the manifest is hand-authored and emit leaves it alone", async () => {
  // Only `files` is generated. If emit ever started rewriting the promise or the
  // parameters, an author's edit would silently vanish on the next run.
  const files = await readTemplateFiles();
  const onDisk = JSON.parse(await readFile(join(PACKAGE_DIR, "blueprint.json"), "utf8"));
  const regenerated = await expectedManifest(files);
  for (const key of ["name", "title", "version", "description", "params", "postTypes",
    "content", "verify"]) {
    assert.deepEqual(regenerated[key], onDisk[key], `emit must not change \`${key}\``);
  }
});

test("the catch-all route is carried, dots and all", async () => {
  // `app/[...slug]/page.tsx` is the file that made the blueprint path-escape guard's
  // `contains("..")` check wrong. It has to survive the whole round trip.
  const files = await readTemplateFiles();
  const catchAll = files.find((f) => f.path === "app/[...slug]/page.tsx");
  assert.ok(catchAll, "the template must carry the catch-all route");
  const source = `files/${catchAll.path}`;
  assert.equal(await readFile(join(PACKAGE_DIR, source), "utf8"), catchAll.text);
});

// --- SH156: the section packages carry the same starter plus an overlay ---

test("every generated package is byte-identical to its composed tree", async () => {
  // The whole point of generating them: `landing` and `marketing-site` each carry a full
  // starter, and a hand-maintained copy of a starter drifts within one release.
  const base = await readTemplateFiles();
  for (const pkg of PACKAGES) {
    const dir = dirOf(pkg.name);
    const files = await filesFor(pkg, base);
    for (const [source, text] of expectedSources(files)) {
      const onDisk = await readFile(join(dir, source), "utf8").catch(() => null);
      assert.notEqual(onDisk, null, `${pkg.name}/${source} is missing — run emit`);
      assert.equal(onDisk, text, `${pkg.name}/${source} has drifted — run emit`);
    }
    const manifest = JSON.parse(await readFile(join(dir, "blueprint.json"), "utf8"));
    assert.deepEqual(
      manifest.files,
      fileRefs(files),
      `${pkg.name}'s \`files\` is stale — run \`pnpm -C create-shio-app run emit\``
    );
  }
});

test("a section package's tree is the starter's, differing only by the overlay", async () => {
  const base = await readTemplateFiles();
  const landing = PACKAGES.find((p) => p.name === "landing");
  const files = await filesFor(landing, base);

  const changed = files
    .filter((file) => base.find((b) => b.path === file.path)?.text !== file.text)
    .map((file) => file.path)
    .sort();

  // Exactly three: the route that grew a branch, the resolver it calls, and the manifest
  // that gained the dependency. If this list grows, the "overlay" has become a fork.
  assert.deepEqual(changed, ["app/[...slug]/page.tsx", "lib/sections.ts", "package.json"]);
  assert.equal(
    files.length,
    base.length + 1,
    "the overlay adds one file and replaces the rest"
  );
});

test("the overlay's dependency is a real published range, read from the workspace", async () => {
  // Written down nowhere: a release bumps @viglet/shio-sections, and a hardcoded range
  // here would point the generated projects at a version nobody published.
  const needs = await resolveNeeds(["@viglet/shio-sections"]);
  assert.match(needs["@viglet/shio-sections"], /^\^\d+\.\d+\.\d+/);

  const base = await readTemplateFiles();
  const files = await filesFor(
    PACKAGES.find((p) => p.name === "marketing-site"),
    base
  );
  const manifest = JSON.parse(files.find((f) => f.path === "package.json").text);
  assert.equal(manifest.dependencies["@viglet/shio-sections"], needs["@viglet/shio-sections"]);
  // The starter's own dependencies survive the merge — an overlay adds, it does not replace.
  assert.ok(manifest.dependencies["@viglet/shio-client"], "the core dependency must survive");
  assert.ok(manifest.dependencies.next, "next must survive");
});

test("compose replaces by path and leaves the rest alone", () => {
  const base = [
    { path: "a.txt", text: "base a" },
    { path: "b.txt", text: "base b" },
  ];
  const composed = compose(base, [{ path: "b.txt", text: "overlay b" }, { path: "c.txt", text: "c" }]);
  assert.deepEqual(composed, [
    { path: "a.txt", text: "base a" },
    { path: "b.txt", text: "overlay b" },
    { path: "c.txt", text: "c" },
  ]);
});

test("blog, docs and product-catalog are left out on purpose", () => {
  // They do not compose sections, so the overlay would ship them a dependency they never
  // import. Named here so that including one later looks like the decision it is.
  assert.deepEqual(
    PACKAGES.map((p) => p.name),
    ["nextjs-starter", "marketing-site", "landing"]
  );
});
