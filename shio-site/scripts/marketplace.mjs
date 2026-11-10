// SH676: the published catalogue, generated from the packages the build already carries.
//
// ## What this writes
//
//   public/marketplace/marketplace.json          the catalogue every released instance reads
//   public/marketplace/v2/marketplace.json       SH1431: the same, with each package's `i18n`, the
//                                                default from the release that reads it
//   public/marketplace/v2/<id>/<id>.zip          that package, its manifest carrying `i18n`
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
// ## `requires` names manifest keys, and never the SH151/SH152 opt-ins
//
// The field carries the SH151/SH152 opt-ins so a console can ask for consent before an install.
// Those cannot be derived here: `createSite` and `mergePostTypes` are **request options** on an
// apply, not manifest keys, and whether either is needed depends on the target instance rather
// than on the package. A publisher may declare them about their own package; this generator will
// not guess on their behalf.
//
// SH1397 — what it does derive is `manifest:<key>` for every manifest key a package uses beyond
// the ones every instance has always read. An instance refuses a manifest key it does not know, and
// learned of it only after downloading the package; one that reads these refuses before. They ride
// in `requires` and not in a field of their own because an instance also refuses an unknown
// CATALOGUE key, and a new field would make every released instance read the whole catalogue as
// unreachable. An old instance shows them as text, which still tells a person what is missing.
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join, relative, resolve, sep } from "node:path";

import { zip } from "./zip.mjs";

export const BASE_MANIFEST_KEYS = ["formatVersion", "name", "title", "description", "version",
  "params", "postTypes", "content", "files", "verify"];

/** SH1397 — `manifest:<key>` for each key this manifest uses that an older instance may not read. */
export function manifestRequires(manifest) {
  return Object.keys(manifest).filter((key) => !BASE_MANIFEST_KEYS.includes(key)).sort()
    .map((key) => `manifest:${key}`);
}

// @scans prose reason: it reads each package's README and manifest to build the published
// catalogue, so the long-form description is the subject (SH740).

/** The packages, as the jar carries them. */
const SOURCE = resolve(process.cwd(), "..", "shio-app", "src", "main", "resources", "blueprints");

/**
 * SH1254 — packages published here and built into no jar: the showcase carries ~2 MB of
 * photography and fonts, which every instance would otherwise pay for whether it installs the
 * demo or not. Same layout as SOURCE, so an instance can also load it through `shio.blueprint.path`.
 */
const EXTRA_SOURCE = resolve(process.cwd(), "..", "marketplace");

/**
 * What a package directory may hold that is not the package: the polyweave project that bought
 * its photography (config, ledger, and the full-size originals the shipped WebP files are cut
 * from). An install has no use for any of it. SH1431: nor for the provenance record polyweave
 * writes beside each shipped copy (`*.prov.json`), which describes the file and is not part of it.
 */
const NOT_PACKAGED = /^(\.gitignore|polyweave\.[^/]+|assets\/source\/.*|.*\.prov\.json|web\.copies\.toml)$/;

/** Where the site serves them from. */
const OUT = resolve(process.cwd(), "public", "marketplace");

/**
 * SH1258 — which screenshots each package published, by file name, so the site's own pages show
 * the files the catalogue lists instead of a second copy. Committed like
 * `token-budgets.generated.json`, so `tsc -b` on a fresh clone has it before any build.
 */
const SCREENSHOT_INDEX = resolve(process.cwd(), "src", "lib", "marketplace-screenshots.generated.json");

/**
 * The host `ShMarketplaceProperties.DEFAULT_URL` points at.
 *
 * Overridable because a catalogue is only useful where it is actually served: a staging deploy on
 * another host, and (the case this was first needed for) serving the generated tree on loopback
 * to prove a real instance can install from it. An entry's `downloadUrl` is absolute, so without
 * this the published catalogue can only ever be exercised in production.
 */
const BASE = process.env.SHIO_MARKETPLACE_BASE ?? "https://shio.viglet.org/marketplace";

/**
 * SH1431 — the second catalogue's directory. `ShMarketplaceProperties.DEFAULT_URL` names
 * `v2/marketplace.json` from the release that reads `i18n`; every instance released before keeps
 * reading `marketplace.json`, whose bytes do not change.
 */
const V2 = "v2";

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
  const packages = [SOURCE, EXTRA_SOURCE]
    .flatMap((root) => readdirSync(root).map((name) => ({ name, dir: join(root, name) })))
    .filter(({ dir }) => statSync(dir).isDirectory() && existsSync(join(dir, "blueprint.json")))
    .sort((a, b) => a.name.localeCompare(b.name));

  if (write) {
    rmSync(OUT, { recursive: true, force: true });
    mkdirSync(OUT, { recursive: true });
  }

  const items = [];
  // SH1431 — the second catalogue, `v2/marketplace.json`, which no released instance reads.
  const itemsV2 = [];
  const digests = new Map();
  const shots = {};

  for (const { name, dir } of packages) {
    // SH1431 — `i18n` stays out of the first catalogue. A released instance refuses a manifest key
    // it does not read, and `manifest:i18n` in `requires` would make it refuse every package here.
    // The other languages ride the v2 catalogue instead, whose entries and zips carry them, and
    // which only an instance that reads `i18n` is pointed at.
    const full = JSON.parse(readFileSync(join(dir, "blueprint.json"), "utf8"));
    const manifest = { ...full };
    delete manifest.i18n;
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
      if (file === "blueprint.json" || NOT_PACKAGED.test(file)) continue;
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
      requires: manifestRequires(manifest),
    };
    // Only when there are some, so the six plain packages publish exactly the bytes they did.
    if (screenshots.length) {
      item.screenshots = screenshots.map((file) => `${BASE}/${id}/${file.slice(SCREENSHOTS.length)}`);
      shots[id] = screenshots.map((file) => file.slice(SCREENSHOTS.length));
    }
    items.push(item);

    // SH1431 — the v2 entry: the same package, its other languages carried in the entry and in the
    // manifest inside its own zip, so a package installed from it keeps them. The README and the
    // screenshots are the first catalogue's, not a second copy.
    let archiveV2 = null;
    if (full.i18n) {
      archiveV2 = zip([{ name: `${id}/blueprint.json`, body: JSON.stringify({ ...full, name: id }, null, 2) },
        ...entries.slice(1)]);
      itemsV2.push({ ...item, downloadUrl: `${BASE}/${V2}/${id}/${id}.zip`,
        requires: manifestRequires(full), i18n: full.i18n });
    } else {
      itemsV2.push(item);
    }

    if (write) {
      mkdirSync(join(OUT, id), { recursive: true });
      writeFileSync(join(OUT, id, `${id}.zip`), archive);
      writeFileSync(join(OUT, id, "README.md"), readme(manifest, id), "utf8");
      for (const file of screenshots) {
        writeFileSync(join(OUT, id, file.slice(SCREENSHOTS.length)), readFileSync(join(dir, file)));
      }
      if (archiveV2) {
        mkdirSync(join(OUT, V2, id), { recursive: true });
        writeFileSync(join(OUT, V2, id, `${id}.zip`), archiveV2);
      }
    }
  }

  if (write) {
    writeFileSync(join(OUT, "marketplace.json"), `${JSON.stringify(items, null, 2)}\n`, "utf8");
    writeFileSync(join(OUT, V2, "marketplace.json"), `${JSON.stringify(itemsV2, null, 2)}\n`, "utf8");
    writeFileSync(SCREENSHOT_INDEX, `${JSON.stringify(shots, null, 2)}\n`, "utf8");
  }
  return { items, itemsV2, digests, shots };
}

if (import.meta.url === `file://${process.argv[1]}`.replace(/\\/g, "/")
    || process.argv[1]?.endsWith("marketplace.mjs")) {
  const { items } = build();
  process.stdout.write(
    `[SH676] marketplace: ${items.length} package(s) -> ${relative(process.cwd(), OUT)}\n`,
  );
}
