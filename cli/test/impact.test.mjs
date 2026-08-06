/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH288 — `shio impact`. Network-free: a scripted fetch records the request.
 *
 * The decision this tests is as much what was NOT built: no `shio read`. A post's content is
 * already on disk after `pull --content`, where grep is cheaper than a request (P4). The blast
 * radius is computed from the site's bindings rather than stored in the post, so no projected
 * file holds it — which is the one question the files cannot answer and the only one this
 * command asks.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as impact from "../src/commands/impact.mjs";

const REGION = [
  {
    address: "post:Blog/regions/nav",
    type: "Region",
    state: "DRAFT",
    impact: {
      kind: "region",
      title: "NAV",
      layouts: ["Home Layout", "Article Layout"],
      postTypes: ["Article", "Page"],
      pages: 12,
      published: 9,
    },
  },
];

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

function scriptedFetch(body = REGION, { status = 200 } = {}) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
    return {
      ok: status >= 200 && status < 300,
      status,
      headers: { get: () => null },
      async text() {
        return typeof body === "string" ? body : JSON.stringify(body);
      },
      async json() {
        return body;
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

test("impact asks read for the impact section and prints the blast radius", async () => {
  const fetchImpl = scriptedFetch();
  const ctx = ctxWith(fetchImpl);

  await impact.run({}, ["post:Blog/regions/nav"], ctx);

  assert.match(fetchImpl.calls[0].url, /\/api\/v2\/agent\/read\?/);
  assert.match(fetchImpl.calls[0].url, /include=impact/);
  assert.equal(
    ctx.stdout.text,
    "region NAV\n" +
      "  layouts: Home Layout, Article Layout\n" +
      "  types:   Article, Page\n" +
      "  pages:   12 draft, 9 published\n",
  );
});

test("impact says so when the address is not a template, rather than failing", async () => {
  // SH262's rule for the HTTP surface, kept at the terminal: a caller sweeping a mixed list
  // should not have to know which addresses are templates before it asks. But silence would
  // read as "nothing depends on it", which is a different and much worse answer.
  const ctx = ctxWith(scriptedFetch([{ address: "post:Blog/hello", type: "Article" }]));

  await impact.run({}, ["post:Blog/hello"], ctx);

  assert.match(ctx.stdout.text, /post:Blog\/hello is not a template/);
});

test("impact --json prints the section for a pipeline", async () => {
  const ctx = ctxWith(scriptedFetch());

  await impact.run({ json: true }, ["post:Blog/regions/nav"], ctx);

  assert.deepEqual(JSON.parse(ctx.stdout.text), REGION[0].impact);
});

test("impact --json prints null for a non-template, so a pipeline can branch", async () => {
  const ctx = ctxWith(scriptedFetch([{ address: "post:Blog/hello", type: "Article" }]));

  await impact.run({ json: true }, ["post:Blog/hello"], ctx);

  assert.equal(ctx.stdout.text.trim(), "null");
});

test("impact url-encodes the address", async () => {
  // Site names carry spaces in the wild (SH72 provisions "Blog (acme)"). Note the encoding is
  // `+` for the space and `%28`/`%29` for the parens, not `resolve`'s `%20` and bare parens:
  // this goes through `read`, which builds its query with URLSearchParams. Both are correct —
  // a query value is form-encoded — and asserting the actual spelling is what keeps a future
  // "tidy up" from silently changing which one a server has to decode.
  const fetchImpl = scriptedFetch();

  await impact.run({}, ["post:Blog (acme)/regions/nav"], ctxWith(fetchImpl));

  assert.match(fetchImpl.calls[0].url, /address=post%3ABlog\+%28acme%29%2Fregions%2Fnav/);
});

test("impact without an address says how to call it", async () => {
  await assert.rejects(
    () => impact.run({}, [], ctxWith(scriptedFetch())),
    /an address is required.*shio impact post:Blog\/regions\/nav/s,
  );
});

test("impact names a template nothing uses without pretending it is an error", async () => {
  // An unused template is a real finding — `verify` reports it (SH193) — but reporting it here
  // as a failure would make a legitimate mid-build state look broken.
  const ctx = ctxWith(
    scriptedFetch([
      {
        address: "post:Blog/regions/stray",
        impact: { kind: "region", title: "STRAY", layouts: [], postTypes: [], pages: 0, published: 0 },
      },
    ]),
  );

  await impact.run({}, ["post:Blog/regions/stray"], ctx);

  assert.match(ctx.stdout.text, /layouts: none/);
  assert.match(ctx.stdout.text, /pages: {3}0 draft, 0 published/);
});
