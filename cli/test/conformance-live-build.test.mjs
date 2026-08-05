/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH460 — the conformance harness's staleness gate, tested where it costs nothing to run.
 *
 * ## Why this lives in `test/` and not in `conformance/`
 *
 * The gate is the first thing `requireInstance` does, so nothing under `conformance/` can reach
 * it without an instance — and the case worth asserting is the one where the instance is *wrong*,
 * which no real instance will volunteer. A `node:http` stub answering a manifest of our choosing
 * is the whole fixture, so it belongs in the fast suite: `pnpm test` runs with no instance, no
 * browser and no registry, and this needs none of the three.
 *
 * ## What it asserts, and the reason it asserts the negative case too
 *
 * A gate that fails on everything is not a gate. The incident SH460 comes from was a *reachable,
 * authenticated, correctly-configured* instance running old code, so the interesting pair is:
 * a build newer than HEAD passes silently, and a build older than HEAD stops the run before a
 * single scenario builds anything — naming the timestamp, HEAD, and the command that fixes it.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import { createServer } from "node:http";

import { requireInstance } from "../conformance/support/instance.mjs";

/** A minimal instance: it answers the identity probe with whatever manifest the test sets. */
let server;
let baseUrl;
let manifest;

function env(extra = {}) {
  return { SHIO_URL: baseUrl, SHIO_USER: "admin", SHIO_PASSWORD: "admin", ...extra };
}

describe("SH460: the conformance harness refuses an instance older than the code under test", () => {
  before(async () => {
    server = createServer((req, res) => {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(manifest));
    });
    await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
    baseUrl = `http://127.0.0.1:${server.address().port}`;
  });

  after(() => server?.close());

  test("a build newer than HEAD is simply reachable, as before", async () => {
    manifest = {
      product: "Viglet Shio CMS",
      version: "dev",
      commit: "deadbee",
      builtAt: new Date(Date.now() + 60_000).toISOString(),
    };

    const conn = await requireInstance(env());

    assert.equal(conn.baseUrl, baseUrl);
    assert.equal(conn.manifest.commit, "deadbee");
  });

  test("a build older than HEAD fails the run, naming both sides and the fix", async () => {
    manifest = { product: "Viglet Shio CMS", version: "dev", commit: "0ldbu1d", builtAt: "2001-01-01T00:00:00Z" };

    await assert.rejects(() => requireInstance(env()), (error) => {
      assert.match(error.message, /older than the code under test/);
      assert.match(error.message, /2001-01-01T00:00:00Z/, "it must date the instance");
      assert.match(error.message, /0ldbu1d/, "and name the build, when the build names itself");
      assert.match(error.message, /HEAD {2}\w+/, "and date HEAD, so the reader compares two facts");
      assert.match(error.message, /spring-boot:run/, "and carry the command that fixes it");
      return true;
    });
  });

  test("an instance with no build identity is stale by definition — the field ships with this gate", async () => {
    manifest = { product: "Viglet Shio CMS", version: "dev" };

    await assert.rejects(() => requireInstance(env()), /reports no build identity at all/);
  });

  test("SHIO_CONFORMANCE_ALLOW_STALE=1 is the escape hatch for an instance you did not build", async () => {
    manifest = { product: "Viglet Shio CMS", version: "dev", builtAt: "2001-01-01T00:00:00Z" };

    const conn = await requireInstance(env({ SHIO_CONFORMANCE_ALLOW_STALE: "1" }));

    assert.equal(conn.baseUrl, baseUrl);
  });
});
