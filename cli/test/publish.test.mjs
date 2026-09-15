/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH546 — the rung of the ladder that could not publish. Network-free: a scripted fetch records
 * every request, which is what lets the interesting properties be asserted at all.
 *
 * The first test is the P1 assertion and the one the task is about: publishing reaches
 * `/api/v2/agent/batch` as `post.publish` ops from the CLI. Before this, `grep post.publish
 * cli/src/` returned nothing and finishing a conversion meant `/agent/find` + a hand-written batch
 * document + curl — three tools to perform the op the previous command had just printed an
 * instruction to perform.
 *
 * The rest are the properties that make it safe to hand to an agent: `--dry-run` never writes,
 * `--all` needs a site and refuses to be mixed with named addresses, a walk that could not finish
 * is a refusal rather than a partial publish, and — the one worth reading the code for — the batch
 * ceiling is **discovered from the refusal** instead of being copied out of the server's
 * configuration into this repo's other half.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as publish from "../src/commands/publish.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/**
 * A fetch that answers each request from `handler(url, method, body)`, recording everything.
 *
 * `{ status, body }`, so a test can hand back an SH80 problem document as easily as a change set —
 * which the ceiling test needs, because the number it is about only exists in a 400.
 */
function scriptedFetch(handler) {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    const body = init.body ? JSON.parse(init.body) : undefined;
    calls.push({ url: String(url), method: init.method ?? "GET", body });
    const { status = 200, payload = {} } = handler(String(url), init.method ?? "GET", body) ?? {};
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: () => null },
      async text() {
        return JSON.stringify(payload);
      },
      async json() {
        return payload;
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

function ctxWith(fetchImpl) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
  };
}

const changed = (ops) => ({
  dryRun: false,
  ops: ops.length,
  changed: ops.length,
  results: ops.map((op) => ({ op: op.op, address: op.address, action: "publish" })),
});

/** A `find` page: `published` per row is SH545's field, which `--all` reports from. */
const page = (addresses, { published = [] } = {}) => ({
  count: addresses.length,
  truncated: false,
  results: addresses.map((address) => ({
    address,
    type: "Page",
    state: "DRAFT",
    ...(published.includes(address) ? { published: true } : {}),
  })),
});

test("SH546: named addresses reach /agent/batch as post.publish ops", async () => {
  const fetchImpl = scriptedFetch((url, method, body) => {
    assert.match(url, /\/api\/v2\/agent\/batch$/);
    assert.equal(method, "POST");
    return { payload: changed(body.ops) };
  });
  const ctx = ctxWith(fetchImpl);

  const result = await publish.run({}, ["post:Acme/about", "post:Acme/contact"], ctx);

  assert.equal(fetchImpl.calls.length, 1, "one atomic call, not one per address");
  assert.deepEqual(
    fetchImpl.calls[0].body.ops,
    [
      { op: "post.publish", address: "post:Acme/about" },
      { op: "post.publish", address: "post:Acme/contact" },
    ],
    "the op vocabulary is the server's — this is the grep that used to return nothing",
  );
  assert.equal(fetchImpl.calls[0].body.dryRun, false);
  assert.equal(result.changed, 2);
  assert.match(ctx.stdout.text, /2 of 2 post\(s\) changed/);
});

test("SH546: --unpublish is the same verb inverted, not a second command", async () => {
  const fetchImpl = scriptedFetch((url, method, body) => ({ payload: changed(body.ops) }));

  await publish.run({ unpublish: true }, ["post:Acme/old"], ctxWith(fetchImpl));

  assert.equal(fetchImpl.calls[0].body.ops[0].op, "post.unpublish");
});

test("SH992: --when and --cancel ride the op as the batch spells them", async () => {
  const fetchImpl = scriptedFetch((url, method, body) => ({ payload: changed(body.ops) }));

  await publish.run({ unpublish: true, when: "2099-12-01T09:00:00Z" }, ["post:Acme/promo"],
    ctxWith(fetchImpl));
  await publish.run({ cancel: true }, ["post:Acme/launch"], ctxWith(fetchImpl));

  assert.deepEqual(fetchImpl.calls[0].body.ops[0],
    { op: "post.unpublish", address: "post:Acme/promo", when: "2099-12-01T09:00:00Z" },
    "SH992: a takedown dated for December has to reach the server with its date, or it happens now");
  assert.deepEqual(fetchImpl.calls[1].body.ops[0],
    { op: "post.publish", address: "post:Acme/launch", cancel: true });
});

test("SH992: a noop on a schedule is not reported as a page already live", async () => {
  const fetchImpl = scriptedFetch((url, method, body) => ({
    payload: {
      dryRun: false, ops: 1, changed: 0,
      results: [{ op: body.ops[0].op, address: body.ops[0].address, action: "noop" }],
    },
  }));
  const ctx = ctxWith(fetchImpl);

  await publish.run({ cancel: true }, ["post:Acme/launch"], ctx);

  assert.match(ctx.stdout.text, /1 with no publish scheduled/);
});

test("SH546: --dry-run asks the server to write nothing", async () => {
  const fetchImpl = scriptedFetch((url, method, body) => ({
    payload: { dryRun: true, ops: body.ops.length, changed: 1, results: [] },
  }));
  const ctx = ctxWith(fetchImpl);

  await publish.run({ dryRun: true }, ["post:Acme/about"], ctx);

  assert.equal(fetchImpl.calls[0].body.dryRun, true);
  assert.match(ctx.stdout.text, /nothing written/);
});

test("SH546: --all names every post from a find, and says how many are already live", async () => {
  const fetchImpl = scriptedFetch((url, method, body) => {
    if (url.includes("/agent/find")) {
      return { payload: page(["post:Acme/a", "post:Acme/b"], { published: ["post:Acme/b"] }) };
    }
    return { payload: changed(body.ops) };
  });
  const ctx = ctxWith(fetchImpl);

  await publish.run({ all: true, site: "Acme" }, [], ctx);

  const batch = fetchImpl.calls.find((call) => call.url.includes("/agent/batch"));
  assert.deepEqual(
    batch.body.ops.map((op) => op.address),
    ["post:Acme/a", "post:Acme/b"],
    "every address is named in the ops — the server never receives a publish-everything",
  );
  assert.match(ctx.stdout.text, /2 post\(s\) in Acme — 1 already live/);
});

test("SH546: --all does not re-publish selectively — an edited live page is the server's noop to make", async () => {
  const fetchImpl = scriptedFetch((url, method, body) => {
    if (url.includes("/agent/find")) {
      return { payload: page(["post:Acme/a"], { published: ["post:Acme/a"] }) };
    }
    return { payload: changed(body.ops) };
  });

  await publish.run({ all: true, site: "Acme" }, [], ctxWith(fetchImpl));

  const batch = fetchImpl.calls.find((call) => call.url.includes("/agent/batch"));
  assert.equal(
    batch.body.ops.length,
    1,
    "a live page whose draft has drifted still needs publishing; filtering on `published` here " +
      "would drop that edit silently, which is the failure SH331 spent a task removing",
  );
});

test("SH546: the batch ceiling comes from the refusal, not from a constant in this repo", async () => {
  const seen = [];
  const fetchImpl = scriptedFetch((url, method, body) => {
    if (url.includes("/agent/find")) {
      return { payload: page(Array.from({ length: 5 }, (_, i) => `post:Acme/p${i}`)) };
    }
    seen.push(body.ops.length);
    if (body.ops.length > 2) {
      return {
        status: 400,
        payload: {
          type: "https://shio.viglet.com/errors/batch-too-large",
          title: "Batch of 5 ops is too large",
          detail: "A single transaction is capped at 2 operations.",
          fix: "Split the work into batches of at most 2 ops; each one is still atomic on its own.",
          example: 2,
        },
      };
    }
    return { payload: changed(body.ops) };
  });
  const ctx = ctxWith(fetchImpl);

  const result = await publish.run({ all: true, site: "Acme" }, [], ctx);

  assert.deepEqual(
    seen,
    [5, 2, 2, 1],
    "the whole run goes first — that is the atomic outcome and a refusal writes nothing — then " +
      "the split is sized by the `example` the 400 carried",
  );
  assert.equal(result.changed, 5, "every op still ran");
  assert.match(ctx.stdout.text, /sent as 3 batches/);
  assert.match(ctx.stdout.text, /atomic per batch, not across them/);
});

test("SH546: a run that fits is never split, and never pays a probe", async () => {
  const fetchImpl = scriptedFetch((url, method, body) => {
    if (url.includes("/agent/find")) return { payload: page(["post:Acme/a"]) };
    return { payload: changed(body.ops) };
  });

  await publish.run({ all: true, site: "Acme" }, [], ctxWith(fetchImpl));

  const batches = fetchImpl.calls.filter((call) => call.url.includes("/agent/batch"));
  assert.equal(batches.length, 1);
});

test("SH546: nothing to publish is a refusal that names both ways to say what", async () => {
  const fetchImpl = scriptedFetch(() => ({ payload: {} }));

  await assert.rejects(
    () => publish.run({}, [], ctxWith(fetchImpl)),
    /nothing to publish.*--site <name> --all/s,
  );
  assert.equal(fetchImpl.calls.length, 0, "and it costs no request");
});

test("SH546: --all needs a site, and refuses to be mixed with named addresses", async () => {
  const fetchImpl = scriptedFetch(() => ({ payload: {} }));

  await assert.rejects(
    () => publish.run({ all: true }, [], ctxWith(fetchImpl)),
    /--all needs --site/,
  );
  await assert.rejects(
    () => publish.run({ all: true, site: "Acme" }, ["post:Acme/a"], ctxWith(fetchImpl)),
    /two different requests/,
  );
  assert.equal(fetchImpl.calls.length, 0);
});

test("SH546: a find that could not finish refuses rather than publishing the part it read", async () => {
  const fetchImpl = scriptedFetch((url) => {
    if (url.includes("/agent/find")) {
      return { payload: { count: 1, truncated: true, results: [{ address: "post:Acme/a" }] } };
    }
    return { payload: {} };
  });

  await assert.rejects(
    () => publish.run({ all: true, site: "Acme" }, [], ctxWith(fetchImpl)),
    /could not read the whole site/,
  );
  assert.ok(
    !fetchImpl.calls.some((call) => call.url.includes("/agent/batch")),
    "a hole in the list means pages that silently stay dark, so nothing is sent",
  );
});
