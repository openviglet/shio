/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH993 — a release as a file, so a launch is reviewable in a pull request.
 *
 * The format is asserted as a round trip and as bytes: a projection that is not byte-stable makes a
 * re-pull of unchanged content a diff (P5), which is the thing that teaches a team to stop reading
 * these files. The push is asserted for what it does NOT do as much as for what it does — a file that
 * could run a launch would make `shio push --content` in CI a way to publish forty pages by editing a
 * line.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import {
  parseRelease,
  releaseOps,
  releasePathFor,
  renderRelease,
} from "../src/releases-store.mjs";

const release = {
  name: "spring-launch",
  site: "Acme",
  description: "Everything the spring campaign needs",
  state: "SCHEDULED",
  scheduledAt: "2099-03-01T09:00:00Z",
  posts: ["post:Acme/pricing", "post:Acme/spring"],
};

test("SH993: a release round-trips through the file it projects to", () => {
  const text = renderRelease(release);
  const read = parseRelease(text, "shio/releases/Acme/spring-launch.yaml");

  assert.equal(read.name, release.name);
  assert.equal(read.site, release.site);
  assert.equal(read.description, release.description);
  assert.deepEqual(read.posts, release.posts);
  assert.equal(renderRelease(read), text, "SH993: rendering what was read must be the same bytes");
  assert.equal(releasePathFor("Acme", "spring-launch"), "Acme/spring-launch.yaml");
});

test("SH993: the file says which fields it cannot change", () => {
  const text = renderRelease(release);
  assert.match(text, /`state` and `scheduledAt` are read-only/);
  assert.match(text, /`release\.run`, which takes a confirm token/);
});

test("SH993: a line the parser cannot read is a refusal, not a shorter release", () => {
  assert.throws(
    () => parseRelease("name: x\nsite: Acme\nposts:\n  * post:Acme/a\n", "x.yaml"),
    /cannot read line/,
    "SH993: a release file that parses to half its posts is a launch missing pages",
  );
  assert.throws(() => parseRelease("site: Acme\n", "x.yaml"), /needs `name` and `site`/);
});

test("SH993: the push converges membership and description, and never the state", () => {
  const { ops, skipped } = releaseOps(
    [release],
    [{ ...release, description: "old", posts: ["post:Acme/pricing", "post:Acme/dropped"] }],
  );

  assert.deepEqual(skipped, []);
  assert.deepEqual(ops, [
    { op: "release.upsert", address: "release:Acme/spring-launch", data: { description: release.description } },
    { op: "release.add", address: "release:Acme/spring-launch", data: { posts: ["post:Acme/spring"] } },
    { op: "release.remove", address: "release:Acme/spring-launch", data: { posts: ["post:Acme/dropped"] } },
  ]);
  assert.ok(
    !JSON.stringify(ops).includes("release.run"),
    "SH993: a push that could run a launch is a launch a merge can trigger",
  );
});

test("SH993: a release that already ran is left alone, with its state named", () => {
  const { ops, skipped } = releaseOps(
    [release],
    [{ ...release, state: "PUBLISHED", posts: [] }],
  );

  assert.deepEqual(ops, [], "SH993: converging a closed release fails op by op, halfway through");
  assert.equal(skipped.length, 1);
  assert.match(skipped[0].reason, /PUBLISHED/);
});

test("SH993: a release the instance does not have yet is created by the push", () => {
  const { ops } = releaseOps([release], []);

  assert.equal(ops[0].op, "release.upsert");
  assert.deepEqual(ops[1].data.posts, release.posts);
});
