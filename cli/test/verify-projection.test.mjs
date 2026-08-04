/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH212 — the first `shio verify` rules that read the project's own files.
 *
 * What is under test is the decision as much as the rule: an asset path a `File` field names is
 * provable from the tree alone, so it belongs in a group that needs no instance — and naming that
 * group alone has to skip the server entirely, or "cheapest gate first" is a claim the command
 * cannot keep.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { PROJECTION_CHECK, lintProjection, splitChecks } from "../src/verify-projection.mjs";

const DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "hero", widget: "File", ordinal: 2 },
      { name: "attachment", widget: "File", ordinal: 3 },
    ],
  },
];

const post = (data, extra = {}) => ({
  site: "Blog",
  url: "/blog/hello",
  type: "Article",
  data,
  ...extra,
});

test("a File field naming an asset the tree does not hold is an error", () => {
  // The defect: this used to surface as an exception out of `push --content`, so the class had no
  // report at all — one offender at a time, and nothing the SH142 baseline could hold.
  const { findings, checked } = lintProjection({
    posts: [post({ hero: "Blog/media/logo.png" })],
    defs: DEFS,
    assets: [],
  });

  assert.equal(checked, 1);
  assert.equal(findings.length, 1);
  assert.deepEqual(
    { rule: findings[0].rule, severity: findings[0].severity, address: findings[0].address,
      field: findings[0].field },
    { rule: "asset-missing", severity: "error", address: "post:Blog/blog/hello", field: "hero" },
  );
  assert.match(findings[0].message, /Blog\/media\/logo\.png/);
  // P6: the fix is what a reader acts on, and it names both remedies.
  assert.match(findings[0].fix, /shio pull --content/);
  assert.match(findings[0].fix, /shio\/assets\/Blog\/media\/logo\.png/);
});

test("an asset the tree holds is not a finding", () => {
  const { findings } = lintProjection({
    posts: [post({ hero: "Blog/media/logo.png" })],
    defs: DEFS,
    assets: ["Blog/media/logo.png"],
  });

  assert.deepEqual(findings, []);
});

test("every File field of a post is checked, not just the first", () => {
  const { findings } = lintProjection({
    posts: [post({ hero: "Blog/media/a.png", attachment: "Blog/media/b.pdf" })],
    defs: DEFS,
    assets: ["Blog/media/a.png"],
  });

  assert.deepEqual(findings.map((finding) => finding.field), ["attachment"]);
});

test("a File value that is not a path is an id already, and is left alone", () => {
  // SH145's rule: a reference this tree never projected. The push sends it as it is and the
  // server resolves or reports it, so inventing a finding here would be inventing a defect.
  const { findings } = lintProjection({
    posts: [post({ hero: "3f2a9c10-0000-4000-8000-000000000000" })],
    defs: DEFS,
    assets: [],
  });

  assert.deepEqual(findings, []);
});

test("an asset path that navigates is a finding, not a thrown lint", () => {
  // SH256's refusal reaches this as an exception. Reported rather than propagated: a lint that
  // dies on the first hostile value cannot report the rest of the tree, and this is the one place
  // that reads every asset reference a project has.
  const { findings } = lintProjection({
    posts: [post({ hero: "Blog/../../../etc/passwd" })],
    defs: DEFS,
    assets: [],
  });

  assert.equal(findings.length, 1);
  assert.equal(findings[0].rule, "asset-path-unsafe");
  assert.equal(findings[0].severity, "error");
});

test("a post whose type the tree lacks is reported as unchecked, not as clean", () => {
  // A warning, because the model lives on the server and a scoped projection legitimately holds
  // posts of types it does not own — but silence would count an unchecked post as a passing one.
  const { findings, checked } = lintProjection({
    posts: [post({ hero: "Blog/media/logo.png" }, { type: "Elsewhere" })],
    defs: DEFS,
    assets: [],
  });

  assert.equal(checked, 0, "it was not checked, and the count must not claim it was");
  assert.equal(findings.length, 1);
  assert.equal(findings[0].rule, "asset-check-skipped");
  assert.equal(findings[0].severity, "warning");
});

test("findings are ordered by address, so two runs over one tree read the same", () => {
  // P5: a report whose order moves with a directory walk is a report you cannot diff.
  const { findings } = lintProjection({
    posts: [
      post({ hero: "Blog/media/z.png" }, { url: "/z" }),
      post({ hero: "Blog/media/a.png" }, { url: "/a" }),
    ],
    defs: DEFS,
    assets: [],
  });

  assert.deepEqual(findings.map((finding) => finding.address), ["post:Blog/a", "post:Blog/z"]);
});

// --- where the group runs, which is half the decision (SH212) ---

test("the tree's group is in the default set and is never sent to the server", () => {
  // Default-on because the check already fails CI one gate later, as an exception out of
  // `push --content` — so it reports a defect earlier that was going to stop the pipeline anyway,
  // and it cannot fail a tree that was passing.
  assert.deepEqual(splitChecks(undefined), {
    remote: undefined,
    projection: true,
    callRemote: true,
  });
  assert.deepEqual(splitChecks(""), { remote: undefined, projection: true, callRemote: true });
});

test("naming the tree's group alone is the server-free gate", () => {
  assert.deepEqual(splitChecks(PROJECTION_CHECK), {
    remote: undefined,
    projection: true,
    callRemote: false,
  });
});

test("the group is stripped from what the server is asked for", () => {
  // A vocabulary that does not contain it answers a teaching 400, correctly (SH320): an
  // unrecognised `checks` value is one that would silently cover nothing.
  assert.deepEqual(splitChecks(`content,${PROJECTION_CHECK},routes`), {
    remote: "content,routes",
    projection: true,
    callRemote: true,
  });
});

test("a caller naming only server groups gets no tree lint", () => {
  // `--checks content` is a caller saying which groups they want, and answering a different
  // question is what SH320's rule forbids.
  assert.deepEqual(splitChecks("content"), {
    remote: "content",
    projection: false,
    callRemote: true,
  });
});

test("`all` includes the tree's group and still reaches the server", () => {
  assert.deepEqual(splitChecks("all"), { remote: "all", projection: true, callRemote: true });
});

// --- the two more defects the tree alone proves (SH374) ---

test("a `files:` entry naming nothing is an error, and every one is reported", () => {
  // The defect the push already refuses — rightly, because a missing sidecar read as an empty
  // value publishes a layout with no HTML from a push that reported success. What was wrong is
  // that the refusal was the *only* place it surfaced: an exception on the first offender rather
  // than a report of all of them, in a gate that needs an instance to reach.
  const { findings } = lintProjection({
    posts: [post({ headline: "Hello" })],
    defs: DEFS,
    missingSidecars: [
      { doc: post({}), field: "HTML", name: "hello.html", expected: "Blog/blog/hello.html" },
      { doc: post({}), field: "JAVASCRIPT", name: "hello.js", expected: "Blog/blog/hello.js" },
    ],
  });

  assert.deepEqual(findings.map((f) => [f.rule, f.field, f.severity]), [
    ["sidecar-missing", "HTML", "error"],
    ["sidecar-missing", "JAVASCRIPT", "error"],
  ]);
  assert.match(findings[0].message, /files: HTML: hello\.html/);
  assert.match(findings[0].fix, /put the value back in `fields:`/);
});

test("a translationOf naming a post the tree holds is not a finding", () => {
  const { findings } = lintProjection({
    posts: [
      post({ headline: "Hello" }, { url: "/en/hello" }),
      post({ headline: "Ola" }, { url: "/pt/ola", translationOf: "/en/hello" }),
    ],
    defs: DEFS,
  });

  assert.deepEqual(findings, []);
});

test("a dangling translationOf is an error on a whole-tree run", () => {
  // The server refuses it with a teaching 400 (SH348), because creating an unlinked post and
  // reporting success is a silent drop — so learning it here costs no round trip.
  const { findings } = lintProjection({
    posts: [post({ headline: "Ola" }, { url: "/pt/ola", translationOf: "/en/gone" })],
    defs: DEFS,
  });

  assert.equal(findings.length, 1);
  assert.equal(findings[0].rule, "translation-target-missing");
  assert.equal(findings[0].severity, "error");
  assert.equal(findings[0].field, "translationOf");
  assert.match(findings[0].message, /post:Blog\/en\/gone/);
});

test("the same dangling translationOf is a warning on a scoped run", () => {
  // A run scoped with --site legitimately holds a translation whose original is in a site it did
  // not read. "Not checked" and "wrong" are different claims and must not be spelled the same —
  // the distinction `asset-check-skipped` already makes, for the same reason.
  const { findings } = lintProjection({
    posts: [post({ headline: "Ola" }, { url: "/pt/ola", translationOf: "/en/gone" })],
    defs: DEFS,
    scoped: true,
  });

  assert.equal(findings[0].severity, "warning");
  assert.match(findings[0].fix, /Widen the run/);
});

test("findings are ordered by address, then rule, so two runs read the same", () => {
  // P5, and it matters more now that three rules can fire on one post: a report whose order moves
  // with which rule ran first is a report you cannot diff.
  const { findings } = lintProjection({
    posts: [
      post({ hero: "Blog/media/gone.png" }, { url: "/z", translationOf: "/nope" }),
      post({ hero: "Blog/media/gone.png" }, { url: "/a" }),
    ],
    defs: DEFS,
    assets: [],
  });

  assert.deepEqual(findings.map((f) => [f.address, f.rule]), [
    ["post:Blog/a", "asset-missing"],
    ["post:Blog/z", "asset-missing"],
    ["post:Blog/z", "translation-target-missing"],
  ]);
});

test("SH400: two files projecting to one address are an error the tree alone proves", () => {
  // The pair cannot come from `posts` — by the time this runs the map `readLocal` keys by address
  // has already dropped one of them — so it arrives from the read, and that is the whole rule.
  const { findings } = lintProjection({
    posts: [post({ hero: "Blog/media/logo.png" })],
    defs: DEFS,
    assets: ["Blog/media/logo.png"],
    duplicates: [{
      address: "post:Blog/blog/hello",
      site: "Blog",
      url: "/blog/hello",
      paths: ["shio/content/Blog/blog/hello.md", "shio/content/Blog/blog/hello/index.md"],
    }],
  });

  assert.equal(findings.length, 1);
  assert.equal(findings[0].rule, "duplicate-address");
  assert.equal(findings[0].severity, "error");
  assert.equal(findings[0].address, "post:Blog/blog/hello");
  assert.equal(findings[0].source, PROJECTION_CHECK);
  // Both paths, because the fix is a choice between them and nothing else can make it.
  assert.match(findings[0].message, /hello\.md and shio\/content\/Blog\/blog\/hello\/index\.md/);
  assert.match(findings[0].fix, /index` segment is dropped/);
});

test("SH400: the severity does not depend on the scope", () => {
  // Unlike `translation-target-missing`: both files are in the part of the tree that was read,
  // or there would be no pair to report, so a scoped run has the same evidence as a full one.
  const duplicates = [{
    address: "post:Blog/blog/hello",
    site: "Blog",
    url: "/blog/hello",
    paths: ["shio/content/Blog/blog/hello.md", "shio/content/Blog/blog/hello/index.md"],
  }];
  for (const scoped of [false, true]) {
    const { findings } = lintProjection({ posts: [], defs: DEFS, assets: [], duplicates, scoped });
    assert.equal(findings[0].severity, "error", `scoped=${scoped}`);
  }
});
