/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH119 — generate the `nextjs-starter` blueprint package from this package's
 * `template/` tree, so there is one starter with two ways to get it: offline
 * (`npm create shio-app`, which copies the tree) and from an instance
 * (`shio init --blueprint nextjs-starter`, which downloads the package's `files`).
 *
 * Why generate rather than share. A running Shio reads blueprints off the classpath
 * and cannot run a JS build, so the bytes have to exist under
 * `shio-app/src/main/resources/blueprints/`. That is a second copy by necessity, which
 * is precisely why `test/emit.test.mjs` fails the build when it disagrees with the
 * template — the same discipline `shio-sections`' `emit` uses for post-type defs.
 *
 * Run: `pnpm -C create-shio-app run emit`
 */
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { readTemplateFiles } from "./scaffold.mjs";

/** The blueprint package this generates into, relative to the repo root. */
export const PACKAGE_DIR = fileURLToPath(
  new URL("../../shio-app/src/main/resources/blueprints/nextjs-starter/", import.meta.url)
);

/** Where the carried tree lives inside the package. */
const FILES_SUBDIR = "files";

/**
 * The manifest's `files` refs for the template tree.
 *
 * <p>`path` is where the file goes in the caller's project; `from` is where its bytes
 * live in the package. Both are always stated: the tree lives under `files/` so the
 * package root stays readable (manifest, model, content, tree), and the two names differ
 * for dotfiles anyway — a leading dot is stripped inside the package, which is the same
 * dodge the npm template uses for `gitignore` and which the `path` now undoes *in the
 * format* rather than in each consumer's code.</p>
 *
 * @param {Array<{path: string, text: string}>} files
 */
export function fileRefs(files) {
  return files.map((file) => ({ path: file.path, from: sourcePath(file) }));
}

/** Where a row's bytes are written inside the package. */
export function sourcePath(file) {
  return `${FILES_SUBDIR}/${file.path.replace(/^\./, "")}`;
}

/**
 * Read the package's manifest with its `files` list replaced by what the template
 * currently holds — the shape `emit` would write. Pure, so the drift test can compare
 * it against what is on disk without touching the filesystem twice.
 */
export async function expectedManifest(files) {
  const manifest = JSON.parse(await readFile(join(PACKAGE_DIR, "blueprint.json"), "utf8"));
  return { ...manifest, files: fileRefs(files) };
}

/** The bytes the package should carry, keyed by package-relative path. */
export function expectedSources(files) {
  return new Map(files.map((file) => [sourcePath(file), file.text]));
}

export async function emit({ log = console.log } = {}) {
  if (!existsSync(PACKAGE_DIR)) {
    throw new Error(
      `emit: the nextjs-starter blueprint package is missing at ${PACKAGE_DIR}. ` +
        "The manifest is hand-authored (it declares the params and the promise); only " +
        "`files` and the carried tree are generated."
    );
  }
  const files = await readTemplateFiles();

  // Rewritten wholesale so a file deleted from the template disappears here too — a
  // stale leftover would be shipped to every project that applies the package.
  await rm(join(PACKAGE_DIR, FILES_SUBDIR), { recursive: true, force: true });
  for (const file of files) {
    const target = join(PACKAGE_DIR, sourcePath(file));
    await mkdir(dirname(target), { recursive: true });
    await writeFile(target, file.text, "utf8");
  }

  const manifest = await expectedManifest(files);
  await writeFile(
    join(PACKAGE_DIR, "blueprint.json"),
    `${JSON.stringify(manifest, null, 2)}\n`,
    "utf8"
  );

  log(`✓ emitted ${files.length} starter file(s) into blueprints/nextjs-starter`);
  return { files: files.map((file) => file.path) };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  await emit();
}
