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
      // SH1246: a template edit without the emit left `docs` saying "Shio CMS" after the rename.
      assert.equal(onDisk, text, `${pkg.name}/${source} has drifted from the template (SH1246) — run emit`);
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

test("SH216: every package is named here, and what it carries is the decision", () => {
  // This asserted that blog, docs and product-catalog were LEFT OUT, and said why: they do not
  // compose sections, so the section overlay would ship them a dependency they never import.
  // That reason still holds and is why they carry an overlay of their own instead — the list is
  // the record of which packages exist, and the assertion below is the record of what each gets.
  assert.deepEqual(
    PACKAGES.map((pkg) => pkg.name),
    ["nextjs-starter", "marketing-site", "landing", "blog", "docs", "product-catalog"],
  );
});

test("SH216: only the section packages bring a dependency with them", () => {
  // The distinction the whole task turns on. A section overlay carries a VOCABULARY, so it
  // declares `needs`; an index page uses the SDK the starter already depends on, so it declares
  // none. A `needs` appearing on one of the three would be the defect SH156 refused to ship.
  const withNeeds = PACKAGES.filter((pkg) => pkg.needs?.length).map((pkg) => pkg.name);
  assert.deepEqual(withNeeds, ["marketing-site", "landing"],
    "an index page that dragged in @viglet/shio-sections would make its package claim a front "
      + "end it does not have — the thing SH156 declined to do for these three");
});

test("SH216: each index package carries exactly one page the starter does not", () => {
  // The claim is per-model, so the route differs per package and that is the point: one overlay
  // could not have produced three shapes. Asserted on the composed tree rather than on the
  // emitted one, so it is about what `emit` WOULD write.
  const routes = {
    blog: "app/blog/page.tsx",
    docs: "app/docs/page.tsx",
    "product-catalog": "app/products/page.tsx",
  };
  for (const [name, route] of Object.entries(routes)) {
    const pkg = PACKAGES.find((entry) => entry.name === name);
    assert.ok(pkg?.overlay, `${name} has no overlay`);
    assert.ok(existsSync(join(pkg.overlay, route)), `${name} is missing ${route}`);
  }
});
