/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH677 — the marketplace has a CLI, so it is not a console-only feature.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { run } from "../src/commands/marketplace.mjs";

const CATALOGUE = {
  items: [
    {
      id: "blog", title: "Blog", version: "1.0.0", builtIn: true, installed: true,
      category: "first-party", tags: [], params: { properties: { blogTitle: {} } }, requires: [],
    },
    {
      id: "shio-blueprint-newsroom", title: "Newsroom", version: "2.0.0", builtIn: false,
      installed: false, category: "community", tags: ["news"],
      downloadUrl: "https://example.invalid/n.zip",
      readmeUrl: "https://example.invalid/n/README.md", params: null, requires: ["createSite"],
    },
  ],
  source: { url: "https://example.invalid/marketplace.json", state: "remote", reason: null },
};

/** A context whose fetch answers from a table, and records what was asked. */
function context(routes) {
  const calls = [];
  let out = "";
  return {
    calls,
    /** What the command printed. Named `output` because `ctx.stdout` is the writer. */
    output: () => out,
    ctx: {
      env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "viglet" },
      stdout: { write: (text) => { out += text; } },
      fetch: async (url, init) => {
        calls.push({ url, method: init?.method ?? "GET", headers: init?.headers ?? {} });
        for (const [match, answer] of routes) {
          if (url.includes(match)) return answer;
        }
        return { ok: false, status: 500, text: async () => "unrouted", json: async () => ({}) };
      },
    },
  };
}

const json = (body, status = 200) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
  text: async () => JSON.stringify(body),
});

test("SH677: list prints every entry and where the list came from", async () => {
  const { ctx, calls, output } = context([["/api/v2/marketplace", json(CATALOGUE)]]);

  const result = await run({}, ["list"], ctx);

  assert.equal(result.items.length, 2);
  assert.match(output(), /blog/);
  assert.match(output(), /shio-blueprint-newsroom/);
  assert.match(output(), /installed/);
  assert.match(output(), /available/);
  assert.match(output(), /source: https:\/\/example\.invalid\/marketplace\.json/,
    "SH677: the source line is printed on every listing, not only when something went wrong — a "
      + "listing that silently shrank is indistinguishable from a catalogue that shrank");
  assert.equal(calls[0].method, "GET");
  assert.match(calls[0].headers.Authorization, /^Basic /,
    "SH677: Basic auth, no session — which is the whole reason the writes moved to a role");
});

test("SH677: an unreachable catalogue is reported, and the built-in packages still list", async () => {
  const unreachable = {
    items: [CATALOGUE.items[0]],
    source: { url: "https://example.invalid/x.json", state: "unreachable", reason: "connect refused" },
  };
  const { ctx, output } = context([["/api/v2/marketplace", json(unreachable)]]);

  await run({}, ["list"], ctx);

  assert.match(output(), /blog/, "the packages that ship with the instance are still listed");
  assert.match(output(), /could not be read/);
  assert.match(output(), /connect refused/,
    "SH677: and it says why — the reason is the only thing separating 'the catalogue is empty' "
      + "from 'the catalogue could not be read'");
});

test("SH677: --available and --category narrow the listing", async () => {
  const { ctx, output } = context([["/api/v2/marketplace", json(CATALOGUE)]]);

  const result = await run({ available: true }, ["list"], ctx);

  assert.deepEqual(result.items.map((item) => item.id), ["shio-blueprint-newsroom"]);

  const second = context([["/api/v2/marketplace", json(CATALOGUE)]]);
  const filtered = await run({ category: "first-party" }, ["list"], second.ctx);
  assert.deepEqual(filtered.items.map((item) => item.id), ["blog"]);
});

test("SH677: show prints the entry and fetches the README through the instance", async () => {
  const { ctx, calls, output } = context([
    ["/api/v2/marketplace/readme", { ok: true, status: 200, text: async () => "# Newsroom\n" }],
    ["/api/v2/marketplace", json(CATALOGUE)],
  ]);

  const result = await run({}, ["show", "shio-blueprint-newsroom"], ctx);

  assert.equal(result.readme, "# Newsroom\n");
  assert.match(output(), /requires: createSite/,
    "SH677: the opt-ins are printed, because 'this will alter a post-type you already have' is "
      + "the thing P7 says must not be a surprise");
  const readmeCall = calls.find((call) => call.url.includes("/readme"));
  assert.ok(readmeCall, "the README goes through the instance, which only proxies a URL its own "
    + "catalogue declares — so `show` cannot be talked into fetching an arbitrary host");
  assert.match(readmeCall.url, /url=https%3A%2F%2Fexample\.invalid/);
});

test("SH677: show on an unknown id says how to find the right one", async () => {
  const { ctx, output } = context([["/api/v2/marketplace", json(CATALOGUE)]]);

  await assert.rejects(() => run({}, ["show", "nope"], ctx), /marketplace list/);
});

test("SH677: install posts, and --dry-run says nothing was written", async () => {
  const planned = { id: "shio-blueprint-newsroom", version: "2.0.0", origin: "/pkg", installed: false, loaded: 6 };
  const { ctx, calls, output } = context([["/install", json(planned)]]);

  await run({ "dry-run": true }, ["install", "shio-blueprint-newsroom"], ctx);

  assert.equal(calls[0].method, "POST");
  assert.match(calls[0].url, /\?dryRun=true$/);
  assert.match(output(), /nothing was written/,
    "SH677: P7 — a dry run that does not say it wrote nothing is a dry run nobody trusts");
  assert.doesNotMatch(output(), /shio apply/,
    "and it does not suggest applying something that is not installed");
});

test("SH677: a real install names the next step, because install is not apply", async () => {
  const done = { id: "shio-blueprint-newsroom", version: "2.0.0", origin: "/pkg", installed: true, loaded: 7 };
  const { ctx, calls, output } = context([["/install", json(done)]]);

  await run({}, ["install", "shio-blueprint-newsroom"], ctx);

  assert.doesNotMatch(calls[0].url, /dryRun/);
  assert.match(output(), /installed shio-blueprint-newsroom/);
  assert.match(output(), /shio apply --blueprint shio-blueprint-newsroom/,
    "SH677: installing puts the package where the registry can see it and stops; the command that "
      + "writes to content is a different one, and saying so is cheaper than a support question");
});

test("SH677: a teaching refusal survives instead of becoming an empty result", async () => {
  // The marketplace's two most useful refusals are 404s — the feature is off, and no entry has
  // that id — and each carries `fix`. ShioAdminClient's shared helper maps 404 to null, which
  // would turn the most instructive message on this surface into "nothing came back".
  const problem = {
    status: 404,
    title: "The package marketplace is not enabled on this instance",
    fix: "Set shio.marketplace.enabled=true",
  };
  const { ctx, output } = context([["/api/v2/marketplace", json(problem, 404)]]);

  await assert.rejects(() => run({}, ["list"], ctx), (error) => {
    assert.match(String(error.body ?? error.message), /marketplace.enabled/);
    return true;
  });
});

test("SH677: uninstall removes, and an unknown action is refused with the list", async () => {
  const gone = { id: "shio-blueprint-newsroom", version: "2.0.0", origin: "/pkg", installed: false, loaded: 6 };
  const { ctx, calls, output } = context([["/api/v2/marketplace/", json(gone)]]);

  await run({}, ["uninstall", "shio-blueprint-newsroom"], ctx);
  assert.equal(calls[0].method, "DELETE");
  assert.match(output(), /removed shio-blueprint-newsroom/);

  const other = context([]);
  await assert.rejects(() => run({}, ["frobnicate"], other.ctx),
    /unknown action .* list, show, install, uninstall/);
});

test("SH677: install and uninstall need an id, and say how to find one", async () => {
  const { ctx, output } = context([]);
  await assert.rejects(() => run({}, ["install"], ctx), /needs an id/);
  await assert.rejects(() => run({}, ["uninstall"], ctx), /marketplace list/);
});
