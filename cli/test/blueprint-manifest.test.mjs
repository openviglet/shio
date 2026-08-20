/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH881 — a package is a directory and a manifest, and nothing held the two together.
 *
 * ## The defect, twice
 *
 * A blueprint's `blueprint.json` names which of the files beside it an apply reads. A file no key
 * names is carried, shipped, kept byte-identical by `shio-sections`' generator — and installs
 * nothing. It is not visible from either side: the directory looks complete, and the manifest looks
 * deliberate.
 *
 * SH873 is what that costs. `Menu.json` and `MenuItem.json` sat in both section packages'
 * `post-types/` and in neither manifest, so the navigation mechanism both packages needed was a
 * type nothing could install — and both drew a page listing instead, putting every page's SEO title
 * in the menu, alphabetically. That task added the two it needed and swept up nothing else, which is
 * how a third file survived it: `SectionMarkup.json`, in the same position, in both packages.
 *
 * ## Why the manifest must list everything, rather than the directory hold only what is listed
 *
 * `emit.mjs` writes the **whole** SH95 vocabulary into both section packages and says why: *a site
 * that received the full vocabulary can grow a testimonial section next month without applying a
 * second blueprint*. That argument is about **provisioning**, and a carried file no manifest lists
 * provisions nothing — so the arrangement did not deliver the benefit the comment claimed.
 *
 * The manifests already agreed with the comment and one file disagreed: `landing` provisions
 * `SectionLogoWall`, `SectionRichText` and `SectionTestimonial` and writes a post of none of them.
 * Ten of eleven were the rule and the eleventh was drift, which is what makes this a check rather
 * than a decision to re-argue per package.
 *
 * ## Scoped to post-types, deliberately
 *
 * `files/` is a front-end tree whose members are named by a different key and often by directory, so
 * the same question there is a different one. This asks the narrow version, which is the one that
 * has produced two defects.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

// @scans prose reason: it reads shipped JSON manifests and a directory listing; what it extracts is
// a file name, which no code stripper would help with (SH740).

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const BLUEPRINTS = join(ROOT, "shio-app", "src", "main", "resources", "blueprints");

/** Every shipped package, as `{name, manifest, carried}`. */
function packages() {
  return readdirSync(BLUEPRINTS, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => {
      const dir = join(BLUEPRINTS, entry.name);
      const types = join(dir, "post-types");
      return {
        name: entry.name,
        manifest: JSON.parse(readFileSync(join(dir, "blueprint.json"), "utf8")),
        carried: existsSync(types)
          ? readdirSync(types).filter((file) => file.endsWith(".json")).sort()
          : [],
      };
    });
}

test("SH881: every post-type a package carries is one its manifest provisions", () => {
  const found = packages();
  assert.ok(found.length, "no package was read at all, which would make this pass forever");

  const unlisted = [];
  let carried = 0;
  for (const pkg of found) {
    const listed = new Set((pkg.manifest.postTypes ?? [])
      .map((path) => String(path).replace(/\\/g, "/").split("/").pop()));
    carried += pkg.carried.length;
    for (const file of pkg.carried) {
      if (!listed.has(file)) unlisted.push(`${pkg.name}/post-types/${file}`);
    }
  }
  console.log(`SH881: ${found.length} package(s), ${carried} post-type file(s) carried, `
    + `${unlisted.length} listed by no manifest`);

  assert.deepEqual(unlisted, [],
    "a post-type file a package carries and its manifest does not name is shipped, regenerated and "
      + "installed by nothing. SH873 is what that costs: Menu and MenuItem sat in exactly this "
      + "position, so both section packages drew a page listing where a curated menu was the "
      + "mechanism they had. Add it to `postTypes`, or stop emitting it into that package");
});

test("SH881: and every post-type a manifest names is one the package actually carries", () => {
  // The other direction, which the first cannot see: a manifest naming a file that is not there
  // fails at apply time with a path nobody can act on, and the failure arrives on somebody else's
  // instance. Cheap to ask here and impossible to ask from the registry, which only ever sees the
  // files it managed to read.
  const missing = [];
  for (const pkg of packages()) {
    const carried = new Set(pkg.carried);
    for (const path of pkg.manifest.postTypes ?? []) {
      const file = String(path).replace(/\\/g, "/").split("/").pop();
      if (!carried.has(file)) missing.push(`${pkg.name}: ${path}`);
    }
  }

  assert.deepEqual(missing, [],
    "a manifest naming a file the package does not carry is an apply that fails on a path, and it "
      + "fails wherever the package was installed rather than here");
});
