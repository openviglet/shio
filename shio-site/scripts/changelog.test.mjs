// @scans prose reason: the subject is a published JSON file, read whole and parsed; what it
// checks is the shape www.viglet.org reads, not any code.
//
// SH1248: shio.viglet.org/changelog.json is the one list of Shio's releases. www.viglet.org reads
// it cross-origin, in the shape Turing's turing.viglet.org/changelog.json already uses, so a
// field renamed here is a release-notes page that renders empty somewhere else.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";

const FILE = resolve(process.cwd(), "public", "changelog.json");
const TAGS = /^\[(new|added|improved|fixed|removed)\] \S/i;

const releases = JSON.parse(readFileSync(FILE, "utf8"));

test("SH1248: changelog.json is an array of { version, pub_date, notes }", () => {
  assert.ok(Array.isArray(releases) && releases.length > 0);
  for (const r of releases) {
    assert.deepEqual(Object.keys(r).sort(), ["notes", "pub_date", "version"], r.version);
    assert.equal(typeof r.version, "string");
    assert.ok(
      r.pub_date === null || !Number.isNaN(Date.parse(r.pub_date)),
      `${r.version}: pub_date is an ISO date or null`,
    );
    assert.ok(Array.isArray(r.notes) && r.notes.length > 0, `${r.version}: notes`);
  }
});

test("SH1248: every note carries a tag the release-notes page renders", () => {
  for (const r of releases) {
    for (const n of r.notes) assert.match(n, TAGS, `${r.version}: ${n}`);
  }
});

test("SH1248: releases are newest first and each version appears once", () => {
  const versions = releases.map((r) => r.version);
  assert.equal(new Set(versions).size, versions.length);
  const dated = releases.filter((r) => r.pub_date).map((r) => Date.parse(r.pub_date));
  assert.deepEqual(dated, [...dated].sort((a, b) => b - a));
  assert.equal(releases.at(-1).version, "0.2.0", "the history starts at the first release");
});
