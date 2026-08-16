/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH768 — the sweep for post-types nobody meant to keep, and the refusals that keep it narrow.
 *
 * ## The finding this closes
 *
 * `shio pull` on a working instance wrote 22 post-types and two were debris —
 * `ShioConfRendermsgmgaup2qjcArticle` and `ShioConfRendermsgmgrcoqdq2Article`, named by the
 * generated run ids of two conformance runs whose own description says "deleted when it ends". They
 * did not end. A run that dies mid-way never reaches its teardown, and that is precisely the run
 * that leaves debris, which is why the answer is a flag the sweep can find afterwards rather than a
 * better `after` hook.
 *
 * ## What is asserted, and why the refusals matter more than the deletion
 *
 * The deletion is three lines. The value is in what the command will **not** do: `--scratch` is not
 * a filter a caller can widen, there is no `--all`, and an undeclared type is untouched at any
 * count. Those are the assertions that stop this from becoming a fast way to delete a real model —
 * the same argument SH591 makes for `site rm`, asserted here rather than inherited by resemblance.
 */
import assert from "node:assert/strict";
import { describe, test } from "node:test";

import { run } from "../src/commands/post-type.mjs";

const TYPES = [
  { name: "Page", title: "Page", system: false, managed: true },
  { name: "File", title: "File", system: true, managed: false },
  { name: "ShioConfRendermsgmgaup2qjcArticle", title: "SH448 Article", managed: true, scratch: true },
  { name: "ShioConfRendermsgmgrcoqdq2Article", title: "SH448 Article", managed: true, scratch: true },
];

/** A context whose `fetch` answers the admin listing and records every write it is asked for. */
function ctx({ types = TYPES, onDelete } = {}) {
  const out = [];
  const calls = [];
  return {
    out,
    calls,
    env: { SHIO_URL: "http://instance", SHIO_USER: "admin", SHIO_PASSWORD: "admin" },
    stdout: { write: (line) => out.push(line) },
    async fetch(url, init = {}) {
      const method = init.method ?? "GET";
      calls.push(`${method} ${new URL(url).pathname}`);
      if (method === "DELETE") {
        const name = decodeURIComponent(new URL(url).pathname.split("/").pop());
        const refusal = onDelete?.(name);
        if (refusal) return new Response(refusal, { status: 409 });
        return new Response(null, { status: 204 });
      }
      return new Response(JSON.stringify(types), {
        status: 200,
        headers: { "content-type": "application/json" },
      });
    },
  };
}

const said = (c) => c.out.join("");

describe("SH768: a conformance run's leftovers are sweepable, and nothing else is", () => {
  test("the listing says which is which — the half a reader actually meets", async () => {
    const c = ctx();

    const result = await run({}, ["ls"], c);

    assert.equal(result.scratch, 2);
    assert.match(said(c), /4 post-type\(s\), 2 declared disposable/);
    assert.match(said(c), /ShioConfRendermsgmgaup2qjcArticle {2}managed {2}SCRATCH/);
    // The whole defect was that these read alike. `Page` must not carry the word.
    assert.ok(!/Page {2}managed {2}SCRATCH/.test(said(c)), "a kept type is never marked SCRATCH");
    assert.match(said(c), /shio post-type rm --scratch removes the 2 marked SCRATCH/);
  });

  test("rm without --scratch is refused, and the refusal teaches (P6)", async () => {
    const c = ctx();

    await assert.rejects(() => run({}, ["rm"], c), (error) => {
      assert.match(error.message, /--scratch is required/);
      assert.match(error.message, /There is no `--all`/);
      assert.match(error.message, /shio post-type rm --scratch --dry-run/, "and carries the example");
      return true;
    });
    assert.deepEqual(c.calls.filter((call) => call.startsWith("DELETE")), [],
      "SH768: a refused sweep deletes nothing — the assertion, not the message");
  });

  test("only the declared types are deleted, at any count", async () => {
    const c = ctx();

    const result = await run({ scratch: true }, ["rm"], c);

    assert.deepEqual(result.deleted, [
      "ShioConfRendermsgmgaup2qjcArticle",
      "ShioConfRendermsgmgrcoqdq2Article",
    ]);
    assert.deepEqual(
      c.calls.filter((call) => call.startsWith("DELETE")),
      [
        "DELETE /api/v2/post-type/ShioConfRendermsgmgaup2qjcArticle",
        "DELETE /api/v2/post-type/ShioConfRendermsgmgrcoqdq2Article",
      ],
      "SH768: `Page` and `File` are in the same listing and are never addressed",
    );
  });

  test("--dry-run names them and writes nothing", async () => {
    const c = ctx();

    const result = await run({ scratch: true, dryRun: true }, ["rm"], c);

    assert.equal(result.deleted.length, 0);
    assert.equal(result.planned.length, 2);
    assert.match(said(c), /would delete 2 scratch post-type\(s\)/);
    assert.deepEqual(c.calls.filter((call) => call.startsWith("DELETE")), []);
  });

  test("a model with no fixtures sweeps nothing and says how one gets marked", async () => {
    const c = ctx({ types: TYPES.filter((type) => !type.scratch) });

    const result = await run({ scratch: true }, ["rm"], c);

    assert.deepEqual(result.deleted, []);
    assert.match(said(c), /none of the 2 type\(s\) is marked scratch/);
    assert.match(said(c), /"scratch": true/, "and names the only moment the flag can be set");
  });

  test("a refused delete is named and exits non-zero, never counted as swept", async () => {
    // A fixture with posts still in it refuses, and that refusal is the useful half: reporting
    // "deleted 2" having deleted one is how a sweep teaches people not to trust it.
    const c = ctx({ onDelete: (name) => (name.includes("aup2qjc") ? "post-type in use" : null) });

    const result = await run({ scratch: true }, ["rm"], c);

    assert.deepEqual(result.deleted, ["ShioConfRendermsgmgrcoqdq2Article"]);
    assert.equal(result.refused.length, 1);
    assert.equal(result.exitCode, 1);
    assert.match(said(c), /deleted 1 scratch post-type\(s\)/);
    assert.match(said(c), /1 refused:/);
    assert.match(said(c), /ShioConfRendermsgmgaup2qjcArticle/);
  });

  test("an unknown verb is refused with the two it takes", async () => {
    const c = ctx();

    await assert.rejects(() => run({}, ["purge"], c), /allowed: ls, rm/);
  });
});
