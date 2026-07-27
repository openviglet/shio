/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
import { PACKAGE_DIR, expectedManifest, expectedSources, fileRefs } from "../src/emit.mjs";

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
