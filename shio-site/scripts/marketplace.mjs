// SH676: the published catalogue, generated from the packages the build already carries.
//
// ## What this writes
//
//   public/marketplace/marketplace.json          the catalogue `shio.marketplace.url` defaults to
//   public/marketplace/<id>/<id>.zip             one installable package
//   public/marketplace/<id>/README.md            its long-form description
//
// ## Why the six, and why under a different id
//
// A catalogue with nothing in it cannot be judged and a format nobody has met is a guess. The six
// first-party packages are known good and already carry everything an entry needs to describe, so
// they are what proves both.
//
// They are published as **`shio-blueprint-<name>`**, the prefix SH643 reserved, and that is not
// cosmetic. `ShMarketplaceCatalog` drops a remote entry whose id collides with a package the
// instance already has (a name means one thing or the catalogue is lying), and the six ARE built
// into every instance. Published under their own names they would be discarded everywhere, which
// is a catalogue that proves the format and disproves the install path. Under the prefix they are
// genuinely installable, which is what the task is for.
//
// The manifest inside each zip is rewritten to that same name, because `ShMarketplaceInstaller`
// refuses a package whose manifest disagrees with the catalogue about what it is called. The
// source packages are untouched: this is a second distribution channel for the same bytes.
//
// ## `requires` is empty, and that is a statement rather than an omission
//
// The field carries the SH151/SH152 opt-ins so a console can ask for consent before an install.
// It cannot be derived here: `createSite` and `mergePostTypes` are **request options** on an
// apply, not manifest keys, and whether either is needed depends on the target instance rather
// than on the package. A publisher may declare them about their own package; this generator will
// not guess on their behalf.

import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join, relative, resolve, sep } from "node:path";

import { zip } from "./zip.mjs";

// @scans prose reason: it reads each package's README and manifest to build the published
// catalogue, so the long-form description is the subject (SH740).

/** The packages, as the jar carries them. */
const SOURCE = resolve(process.cwd(), "..", "shio-app", "src", "main", "resources", "blueprints");

/** Where the site serves them from. */
const OUT = resolve(process.cwd(), "public", "marketplace");

/**
 * The host `ShMarketplaceProperties.DEFAULT_URL` points at.
 *
 * Overridable because a catalogue is only useful where it is actually served: a staging deploy on
 * another host, and (the case this was first needed for) serving the generated tree on loopback
 * to prove a real instance can install from it. An entry's `downloadUrl` is absolute, so without
 * this the published catalogue can only ever be exercised in production.
 */
const BASE = process.env.SHIO_MARKETPLACE_BASE ?? "https://shio.viglet.org/marketplace";

/** The prefix SH643 reserved for a distributable package. */
const PREFIX = "shio-blueprint-";

/** Who publishes these. */
const AUTHOR = "Viglet";

/** SH1253 — a package's preview images, and the ones the instance's proxy will serve. */
const SCREENSHOTS = "screenshots/";
const SCREENSHOT_TYPES = /\.(png|jpe?g|gif|webp)$/i;
/** `ShMarketplaceItem.MAX_SCREENSHOTS`: the instance keeps no more than this per entry. */
const MAX_SCREENSHOTS = 4;

/** The bucket they are listed under. */
const CATEGORY = "first-party";

/** Every file in a directory, relative and slash-separated, sorted, so the zip is stable. */
function walk(root, base = root) {
  const out = [];
  for (const name of readdirSync(base).sort()) {
    const path = join(base, name);
    if (statSync(path).isDirectory()) {
      out.push(...walk(root, path));
    } else {
      out.push(relative(root, path).split(sep).join("/"));
    }
  }
  return out;
}

/** The long-form description, derived from the manifest rather than written twice. */
function readme(manifest, id) {
  const lines = [
    `# ${manifest.title ?? id}`,
    "",
    manifest.description ?? "",
    "",
    "## Install",
    "",
    "```",
    `shio marketplace install ${id}`,
    "```",
    "",
    `Then apply it to a site. Applying is a separate step: installing puts the package where`,
    `this instance can see it, and nothing is written to your content until you apply it.`,
    "",
  ];

  const params = manifest.params?.properties ?? {};
  const names = Object.keys(params);
  if (names.length) {
    const required = new Set(manifest.params?.required ?? []);
    lines.push("## Parameters", "");
    for (const name of names) {
      const spec = params[name] ?? {};
      const mark = required.has(name) ? " **(required)**" : "";
      const fallback = spec.default === undefined ? "" : ` Default: \`${spec.default}\`.`;
      lines.push(`- \`${name}\`${mark}: ${spec.description ?? ""}${fallback}`);
    }
    lines.push("");
  }

  const provisions = [];
  if (manifest.postTypes?.length) {
    provisions.push(`${manifest.postTypes.length} post type(s)`);
  }
  if (manifest.content?.length) {
    provisions.push(`${manifest.content.length} content document(s)`);
  }
  if (manifest.files?.length) {
    provisions.push(`${manifest.files.length} front-end file(s) for you to write`);
  }
  if (provisions.length) {
    lines.push("## What it provisions", "", provisions.map((each) => `- ${each}`).join("\n"), "");
  }

  lines.push(
    "---",
    "",
    `Published by ${AUTHOR} for [Viglet Shio](https://github.com/openviglet/shio).`,
    "",
  );
  return lines.join("\n");
}

/**
 * Build the catalogue.
 *
 * @returns {{items: Array<object>, digests: Map<string, string>}} the entries and, for the test,
 *   a digest per archive so determinism can be asserted without keeping the bytes
 */
export function build({ write = true } = {}) {
  const names = readdirSync(SOURCE)
    .filter((name) => statSync(join(SOURCE, name)).isDirectory())
    .sort();

  if (write) {
    rmSync(OUT, { recursive: true, force: true });
    mkdirSync(OUT, { recursive: true });
  }

  const items = [];
  const digests = new Map();

  for (const name of names) {
    const dir = join(SOURCE, name);
    const manifest = JSON.parse(readFileSync(join(dir, "blueprint.json"), "utf8"));
    const id = PREFIX + name;

    // The manifest the package is published WITH names the package as the catalogue does, or the
    // installer refuses it. Everything else is carried through untouched.
    const published = { ...manifest, name: id };

    const entries = [{ name: `${id}/blueprint.json`, body: JSON.stringify(published, null, 2) }];
    // SH1253 — images under `screenshots/` are what the console shows before an install. They
    // are published beside the zip and left out of it: they describe the package, and an install
    // has no use for them.
    const screenshots = [];
    for (const file of walk(dir)) {
      if (file === "blueprint.json") continue;
      if (file.startsWith(SCREENSHOTS)) {
        if (SCREENSHOT_TYPES.test(file) && screenshots.length < MAX_SCREENSHOTS) screenshots.push(file);
        continue;
      }
      entries.push({ name: `${id}/${file}`, body: readFileSync(join(dir, file)) });
    }

    const archive = zip(entries);
    digests.set(id, createHash("sha256").update(archive).digest("hex"));

    const item = {
      id,
      title: manifest.title ?? name,
      description: manifest.description ?? "",
      version: manifest.version ?? "1.0.0",
      author: AUTHOR,
      category: CATEGORY,
      tags: [],
      icon: name,
      downloadUrl: `${BASE}/${id}/${id}.zip`,
      readmeUrl: `${BASE}/${id}/README.md`,
      params: manifest.params ?? null,
      requires: [],
    };
    // Only when there are some, so the six plain packages publish exactly the bytes they did.
    if (screenshots.length) {
      item.screenshots = screenshots.map((file) => `${BASE}/${id}/${file.slice(SCREENSHOTS.length)}`);
    }
    items.push(item);

    if (write) {
      mkdirSync(join(OUT, id), { recursive: true });
      writeFileSync(join(OUT, id, `${id}.zip`), archive);
      writeFileSync(join(OUT, id, "README.md"), readme(manifest, id), "utf8");
      for (const file of screenshots) {
        writeFileSync(join(OUT, id, file.slice(SCREENSHOTS.length)), readFileSync(join(dir, file)));
      }
    }
  }

  if (write) {
    writeFileSync(join(OUT, "marketplace.json"), `${JSON.stringify(items, null, 2)}\n`, "utf8");
  }
  return { items, digests };
}

if (import.meta.url === `file://${process.argv[1]}`.replace(/\\/g, "/")
    || process.argv[1]?.endsWith("marketplace.mjs")) {
  const { items } = build();
  process.stdout.write(
    `[SH676] marketplace: ${items.length} package(s) -> ${relative(process.cwd(), OUT)}\n`,
  );
}
