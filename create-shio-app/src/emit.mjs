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
 * SH156 — and generate the section-vocabulary packages' front-end half the same way, as
 * **the same tree plus an overlay**. That is the answer to "how do packages share a tree
 * instead of each carrying a copy": they each carry a copy and *nobody writes one*. A
 * hand-maintained second starter would drift within a release; a generated one cannot,
 * because `test/emit.test.mjs` fails the build the moment it disagrees with its sources.
 *
 * Why generate rather than share. A running Shio reads blueprints off the classpath
 * and cannot run a JS build, so the bytes have to exist under
 * `shio-app/src/main/resources/blueprints/`. That is a second copy by necessity, which
 * is precisely why the drift test exists — the same discipline `shio-sections`' `emit`
 * uses for post-type defs.
 *
 * Run: `pnpm -C create-shio-app run emit`
 */
import { mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import { rangeFor, unpublished } from "./published.mjs";
import { readTemplateFiles } from "./scaffold.mjs";

/** Where the blueprint packages live, relative to this file. */
const BLUEPRINTS = fileURLToPath(
  new URL("../../shio-app/src/main/resources/blueprints/", import.meta.url)
);

/**
 * The section vocabulary's front-end half, authored once (SH156).
 *
 * <p>It lives in `shio-sections` rather than here because the vocabulary owns how it
 * renders — the same argument that puts its post-type defs in `src/post-types.ts`. This
 * package owns the *starter*, that one owns the *sections*, and `emit` is where they
 * meet.</p>
 */
const SECTIONS_OVERLAY = fileURLToPath(new URL("../../shio-sections/front-end/", import.meta.url));

/** `@viglet/shio-sections`' own package.json — the version the overlay depends on. */
const SECTIONS_MANIFEST = fileURLToPath(
  new URL("../../shio-sections/package.json", import.meta.url)
);

/**
 * Every package generated from the starter tree.
 *
 * <p>Named explicitly rather than globbed, for the same reason `shio-sections`' target
 * list is: a package that stops carrying a front end is a decision, and it should look
 * like one here.</p>
 *
 * <p><b>`blog`, `docs` and `product-catalog` are deliberately absent.</b> They do not
 * compose sections — their models are ordinary posts with an HTML field, which the
 * starter's catch-all already renders — so what they are missing is an <em>index</em> page
 * over their own content, which is per-package and not this overlay. Giving them the
 * section tree would ship a dependency they never import, and would make three packages
 * claim a front end that renders their pages no better than the starter does.</p>
 */
export const PACKAGES = [
  { name: "nextjs-starter", overlay: null },
  { name: "marketing-site", overlay: SECTIONS_OVERLAY, needs: ["@viglet/shio-sections"] },
  { name: "landing", overlay: SECTIONS_OVERLAY, needs: ["@viglet/shio-sections"] },
];

/** Where the carried tree lives inside a package. */
const FILES_SUBDIR = "files";

/** The starter's package directory — kept as a named export for SH119's callers. */
export const PACKAGE_DIR = join(BLUEPRINTS, "nextjs-starter/");

/**
 * The manifest's `files` refs for a carried tree.
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

/** Read an overlay directory as the same `{path, text}` rows the template produces. */
export async function readOverlay(dir) {
  if (!dir || !existsSync(dir)) return [];
  const rows = [];
  for (const rel of await listFiles(dir)) {
    rows.push({ path: rel, text: await readFile(join(dir, rel), "utf8") });
  }
  return rows.sort((a, b) => a.path.localeCompare(b.path));
}

async function listFiles(dir, prefix = "") {
  const out = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const rel = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      out.push(...(await listFiles(join(dir, entry.name), rel)));
    } else {
      out.push(rel);
    }
  }
  return out;
}

/**
 * The tree one package carries: the starter, with the overlay's files replacing or
 * joining it, and the dependencies the overlay needs added to `package.json`.
 *
 * <p>Replace-by-path is what keeps the overlay small. The section-aware catch-all route
 * is the starter's route plus one branch, so it <em>is</em> `app/[...slug]/page.tsx`
 * rather than a second route the project would have to be told about.</p>
 *
 * @param {Array<{path: string, text: string}>} base    the starter tree
 * @param {Array<{path: string, text: string}>} overlay files that replace or join it
 * @param {Record<string, string>} dependencies         added to the project's package.json
 */
export function compose(base, overlay, dependencies = {}) {
  const byPath = new Map(base.map((file) => [file.path, file]));
  for (const file of overlay) {
    byPath.set(file.path, file);
  }
  if (Object.keys(dependencies).length > 0) {
    const manifest = byPath.get("package.json");
    if (!manifest) {
      throw new Error("emit: the starter tree has no package.json to add dependencies to");
    }
    const parsed = JSON.parse(manifest.text);
    // Sorted, so adding one dependency does not reorder the ones already there and the
    // generated diff is the change and nothing else (P5).
    parsed.dependencies = Object.fromEntries(
      Object.entries({ ...parsed.dependencies, ...dependencies }).sort(([a], [b]) =>
        a.localeCompare(b)
      )
    );
    byPath.set("package.json", {
      path: "package.json",
      text: `${JSON.stringify(parsed, null, 2)}\n`,
    });
  }
  return [...byPath.values()].sort((a, b) => a.path.localeCompare(b.path));
}

/**
 * The dependency ranges an overlay's `needs` resolve to.
 *
 * <h3>The comment that used to be here was exactly backwards (SH446)</h3>
 *
 * <p>It read: "read from the workspace rather than written down here, so a release that bumps
 * `@viglet/shio-sections` cannot leave the generated packages pointing at a version nobody
 * published." Reading it from the workspace is what <em>guarantees</em> pointing at a version nobody
 * published — the workspace is the version being <em>built</em>, and it is installable only in the
 * instant after a release. Measured on `@viglet/shio-client`, whose template range was derived the
 * same way: `^2026.3.1` against a registry that had `2026.2.1`, so `npm install` in a fresh scaffold
 * failed `ETARGET` and nothing in the repository noticed, because nothing installs from the
 * registry.</p>
 *
 * <p>So the published range wins when there is one ({@link rangeFor}), and the workspace's is the
 * fallback for a package that has never been published at all — where no range is correct and
 * writing *something* at least leaves a project that is right the day the release runs.
 * {@link unpublishedNeeds} is what reports that state, and `emit` prints it.</p>
 */
export async function resolveNeeds(needs) {
  if (!needs || needs.length === 0) return {};
  const { version } = JSON.parse(await readFile(SECTIONS_MANIFEST, "utf8"));
  return Object.fromEntries(needs.map((name) => [name, rangeFor(name) ?? `^${version}`]));
}

/** The `needs` across every package that name something the registry does not have (SH446). */
export function unpublishedNeeds() {
  return unpublished([...new Set(PACKAGES.flatMap((pkg) => pkg.needs ?? []))]);
}

/** The whole tree one entry of {@link PACKAGES} should carry. */
export async function filesFor(pkg, base) {
  const template = base ?? (await readTemplateFiles());
  if (!pkg.overlay) return template;
  return compose(template, await readOverlay(pkg.overlay), await resolveNeeds(pkg.needs));
}

/**
 * Read a package's manifest with its `files` list replaced by what its tree currently
 * holds — the shape `emit` would write. Pure, so the drift test can compare it against
 * what is on disk without touching the filesystem twice.
 */
export async function expectedManifest(files, name = "nextjs-starter") {
  const manifest = JSON.parse(await readFile(join(BLUEPRINTS, name, "blueprint.json"), "utf8"));
  return { ...manifest, files: fileRefs(files) };
}

/** The bytes a package should carry, keyed by package-relative path. */
export function expectedSources(files) {
  return new Map(files.map((file) => [sourcePath(file), file.text]));
}

export async function emit({ log = console.log } = {}) {
  const base = await readTemplateFiles();
  const emitted = {};

  for (const pkg of PACKAGES) {
    const dir = join(BLUEPRINTS, pkg.name);
    if (!existsSync(dir)) {
      throw new Error(
        `emit: the ${pkg.name} blueprint package is missing at ${dir}. ` +
          "The manifest is hand-authored (it declares the params and the promise); only " +
          "`files` and the carried tree are generated."
      );
    }
    const files = await filesFor(pkg, base);

    // Rewritten wholesale so a file deleted from the template disappears here too — a
    // stale leftover would be shipped to every project that applies the package.
    await rm(join(dir, FILES_SUBDIR), { recursive: true, force: true });
    for (const file of files) {
      const target = join(dir, sourcePath(file));
      await mkdir(dirname(target), { recursive: true });
      await writeFile(target, file.text, "utf8");
    }

    const manifest = await expectedManifest(files, pkg.name);
    await writeFile(join(dir, "blueprint.json"), `${JSON.stringify(manifest, null, 2)}\n`, "utf8");

    log(`✓ emitted ${files.length} file(s) into blueprints/${pkg.name}`);
    emitted[pkg.name] = files.map((file) => file.path);
  }

  // SH446 — said at the moment the file is written, because the alternative is a stranger's
  // `npm install` saying it, and by then the scaffold is on their disk and this repository is not.
  const missing = unpublishedNeeds();
  if (missing.length) {
    log(
      `\n! ${missing.join(" ")} ${missing.length === 1 ? "is" : "are"} NOT on the registry, so the `
        + `starter${missing.length === 1 ? "" : "s"} carrying the section overlay cannot `
        + `npm install outside this monorepo.\n`
        + `  No range fixes it — publish them, then record the range in src/published.mjs.\n`
        + `  \`shio init\` (no --blueprint) is unaffected: it carries only published packages.\n`,
    );
  }

  return { files: emitted["nextjs-starter"], packages: emitted, unpublished: missing };
}

// `pathToFileURL`, not a hand-built `file://` string: on Windows `process.argv[1]` is
// `D:\...` while `import.meta.url` is `file:///D:/...`, so the concatenated form never
// matched and running the script did nothing at all — silently, which is the worst way for
// a generator to fail.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await emit();
}
