// @scans prose reason: it reads the generated catalogue and the package descriptions it
// publishes, both as text (SH740).
//
// SH676: the published catalogue is generated, installable, and the same bytes every build.
//
// ## The one assertion that needed something other than this file
//
// A zip writer verified by a reader in the same commit proves the two agree, and nothing else.
// The archives here exist to be opened by `ShMarketplaceInstaller` (Java's `ZipInputStream`), so
// the check that matters is whether an implementation this repository did not write can read them.
// Python's `zipfile` is that implementation, it is on every machine this project builds on
// (roadkeep runs on it, CI is ubuntu-latest), and `testzip()` verifies every entry's CRC rather
// than merely parsing the headers.
//
// It is a hard failure when python is absent, not a skip. A verification that quietly stops
// running is the failure mode every reader in this repo is written against.

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { test } from "node:test";

import { build } from "./marketplace.mjs";
import { zip } from "./zip.mjs";

const OUT = resolve(process.cwd(), "public", "marketplace");

/** The six names the jar carries, which are also the ids that must NOT appear in the catalogue. */
const BUILT_IN = ["blog", "docs", "landing", "marketing-site", "nextjs-starter",
  "product-catalog"];

function python() {
  for (const candidate of ["python3", "python"]) {
    const probe = spawnSync(candidate, ["-c", "import zipfile"], { encoding: "utf8" });
    if (probe.status === 0) return candidate;
  }
  assert.fail(
    "SH676: no python with `zipfile` on PATH. This is the only check that reads these archives "
      + "with an implementation this repository did not write, so it fails rather than skips: "
      + "the archives exist to be opened by Java's ZipInputStream, and a writer verified only by "
      + "its own reader proves the two agree and nothing more.",
  );
  return null;
}

test("SH676: every first-party package becomes a catalogue entry", () => {
  const { items } = build({ write: false });

  assert.equal(items.length, BUILT_IN.length,
    `expected one entry per package, got ${items.length}`);
  assert.deepEqual(items.map((item) => item.id).sort(),
    BUILT_IN.map((name) => `shio-blueprint-${name}`).sort());
  for (const item of items) {
    assert.ok(item.title, `${item.id} has no title`);
    assert.ok(item.description, `${item.id} has no description`);
    assert.ok(item.downloadUrl.startsWith("https://shio.viglet.org/marketplace/"),
      `${item.id} does not download from the host shio.marketplace.url defaults to`);
    assert.ok(item.readmeUrl.endsWith("/README.md"), `${item.id} has no README`);
  }
});

test("SH676: no entry collides with a package the instance already has", () => {
  const { items } = build({ write: false });
  const ids = new Set(items.map((item) => item.id));

  for (const name of BUILT_IN) {
    assert.ok(!ids.has(name),
      `SH676: '${name}' is built into every instance, and ShMarketplaceCatalog DROPS a remote `
        + "entry whose id collides with a built-in one. Published under that name this entry "
        + "would be discarded everywhere, which is a catalogue that proves the format and "
        + "disproves the install path. The prefix is what makes it installable.");
  }
});

test("SH676: the manifest inside the zip names the package as the catalogue does", () => {
  // ShMarketplaceInstaller refuses a package whose manifest.name disagrees with the catalogue id
  // (SH675). Republishing under a prefix therefore has to rewrite the manifest, and forgetting to
  // would make every published package refuse to install, with a correct error, which is worse,
  // because it would read as the installer being broken.
  const runner = python();
  const { items } = build();

  for (const item of items) {
    const archive = join(OUT, item.id, `${item.id}.zip`);
    assert.ok(existsSync(archive), `${item.id} has no archive`);
    const read = spawnSync(runner, ["-c", `
import json, sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
bad = z.testzip()
if bad is not None:
    raise SystemExit("corrupt entry: " + bad)
m = json.loads(z.read(sys.argv[2] + "/blueprint.json"))
print(m["name"])
print(len(z.namelist()))
`, archive, item.id], { encoding: "utf8" });

    assert.equal(read.status, 0,
      `${item.id}: python could not read the archive:\n${read.stderr}`);
    // `split(/\r?\n/)`: python's print writes CRLF on Windows and LF on the CI runner, and a
    // trailing \r turns this assertion into a failure that reads like a name mismatch.
    const [name, entries] = read.stdout.trim().split(/\r?\n/);
    assert.equal(name, item.id,
      `${item.id}: the published manifest says '${name}', so ShMarketplaceInstaller would refuse `
        + "it with a name-mismatch");
    assert.ok(Number(entries) > 1, `${item.id}: the archive carries only the manifest`);
  }
});

test("SH676: the same packages produce the same bytes (P5)", () => {
  // The archives are built by the Pages workflow rather than committed, so this is not about diff
  // noise: it is that a package downloaded from shio.viglet.org should be reproducible from the
  // commit it was built at. Rebuild the tree, get the same bytes, and a mismatch then means
  // something other than the packages changed.
  const first = build({ write: false }).digests;
  const second = build({ write: false }).digests;

  assert.deepEqual([...second.entries()].sort(), [...first.entries()].sort(),
    "SH676: two builds of the same input disagreed, so something in the archive is not derived "
      + "from the packages: a timestamp, an ordering, or a path separator");
});

test("SH676: the writer is not vacuous: a changed byte changes the archive", () => {
  const one = zip([{ name: "a.txt", body: "hello" }]);
  const two = zip([{ name: "a.txt", body: "hellO" }]);
  const same = zip([{ name: "a.txt", body: "hello" }]);

  assert.notDeepEqual(one, two, "a different body must produce a different archive");
  assert.deepEqual(one, same, "the same body must produce the same archive");
  assert.equal(one.readUInt32LE(0), 0x04034b50, "the local file header signature");
  assert.equal(one.readUInt16LE(8), 0, "method 0: stored, which is what makes this simple");
});

test("SH676: the README is derived from the manifest, not written twice", () => {
  build();
  const { items } = build({ write: false });
  const readme = readFileSync(join(OUT, items[0].id, "README.md"), "utf8");

  assert.ok(readme.includes(items[0].title), "the README does not name the package");
  assert.ok(readme.includes(items[0].description.slice(0, 40)),
    "the README does not carry the manifest's own description");
  assert.ok(readme.includes(`shio marketplace install ${items[0].id}`),
    "the README does not say how to install it");
});
