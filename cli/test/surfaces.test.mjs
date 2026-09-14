/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH979 — `shio surfaces` is the fourth reader of one declaration.
 *
 * The console builds its rail from `console/surfaces.json`, the manifest serves it behind
 * `include=surfaces`, MCP publishes it as `shio://surfaces`, and this prints it. What is asserted
 * here is the part only this reader can get wrong: that it asks for the section (an ordinary
 * manifest read carries no surfaces at all, by design), and that it prints the answer beside each
 * surface rather than only the list — "is there an agent way to do what that screen does" is the
 * question the command exists for.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as surfaces from "../src/commands/surfaces.mjs";

const DECLARED = [
  {
    id: "sites", section: "content", route: "/bento/content",
    capability: "browse a site's folders and posts",
    agent: { kind: "tool", target: "shio_find" },
  },
  {
    id: "api-tokens", section: "administration", route: "/bento/admin/tokens",
    capability: "mint and revoke API tokens",
    exception: { reason: "OPERATOR_ONLY", why: "an escalation Block L prevents", closedBy: "" },
  },
];

function ctxWith(fetchImpl) {
  const lines = [];
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    stdout: { write: (text) => lines.push(text) },
    stderr: { write: () => {} },
    fetch: fetchImpl,
    text: () => lines.join(""),
  };
}

test("SH979: shio surfaces asks for the section, and prints what an agent calls instead", async () => {
  const urls = [];
  const ctx = ctxWith(async (url) => {
    urls.push(String(url));
    return { ok: true, status: 200, json: async () => ({ surfaces: DECLARED }) };
  });

  await surfaces.run({}, [], ctx);

  assert.match(urls[0], /\/api\/v2\/agent\/manifest\?include=surfaces$/,
    "SH979: the surfaces are opt-in, so a read that does not name the section gets a manifest"
      + " without them and prints an empty list");
  const printed = ctx.text();
  assert.match(printed, /sites\s+\/bento\/content/);
  assert.match(printed, /→ tool shio_find/,
    "SH979: the point of the command is the answer beside the surface, not the surface");
  assert.match(printed, /✗ OPERATOR_ONLY: an escalation Block L prevents/,
    "SH979: a surface with no equivalent says why, which is what stops the next task filing it"
      + " as a gap that was already decided");
});

test("SH979: --json is the same list, for a pipeline", async () => {
  const ctx = ctxWith(async () => ({ ok: true, status: 200, json: async () => ({ surfaces: DECLARED }) }));

  await surfaces.run({ json: true }, [], ctx);

  assert.deepEqual(JSON.parse(ctx.text()), DECLARED);
});

/**
 * SH981 — the handoff is a link, and the CLI prints the one the instance built.
 *
 * `shio publish`'s closing line used to be an address and a verb; the console page for what it just
 * published was a sentence in a batch detail with no URL in it. The instance builds the link from
 * its declared routes, so this asserts the CLI prints what it was handed rather than composing a
 * URL of its own — a client that concatenated one would rot the next time the prefix moves, which
 * has already happened once.
 */
test("SH981: shio publish prints the console link the instance handed it", async () => {
  const { run } = await import("../src/commands/publish.mjs");
  const ctx = ctxWith(async (url, init) => {
    if (String(url).includes("/agent/batch")) {
      return {
        ok: true, status: 200,
        json: async () => ({
          changed: 1, ops: 1,
          results: [{
            op: "post.publish", address: "post:Blog/launch", action: "publish",
            curatorUrl: "/bento/content/post/7f1c",
          }],
        }),
      };
    }
    return { ok: true, status: 200, json: async () => ({ results: [] }) };
  });

  await run({}, ["post:Blog/launch"], ctx);

  assert.match(ctx.text(), /publish post:Blog\/launch/);
  assert.match(ctx.text(), /\/bento\/content\/post\/7f1c/,
    "SH981: an agent's closing move is a link a curator can open, not a sentence telling them to"
      + " find the page themselves");
});
