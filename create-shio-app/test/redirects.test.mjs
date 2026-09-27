/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1133 — a scaffolded front end answers a moved path the way Shio's own delivery would.
 *
 * The template's `lib/redirects.ts` is imported as TypeScript, which Node runs by stripping the
 * types; it imports nothing from `next`, which is what makes it testable here at all.
 */
import assert from "node:assert/strict";
import { test } from "node:test";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

import {
  canonicalPath,
  cdaRedirects,
  findRedirect,
  redirectLookup,
} from "../template/lib/redirects.ts";

const TABLE = [
  { from: "/about", to: "https://example.org/about", permanent: false },
  { from: "/pricing", to: "/plans", permanent: true },
];

test("SH1133: a moved path answers the table's destination with its 301 or 302", () => {
  assert.deepEqual(findRedirect(TABLE, "/pricing"), { destination: "/plans", status: 301 });
  assert.deepEqual(findRedirect(TABLE, "/about"), {
    destination: "https://example.org/about",
    status: 302,
  }, "SH1133: a 302 unless the row says permanent, SH385's default");
  assert.equal(findRedirect(TABLE, "/plans"), null,
    "SH1133: a path that did not move reaches the page");
});

test("SH1133: the path is matched in the spelling Shio matches it in", () => {
  assert.equal(canonicalPath("/pricing/"), "/pricing");
  assert.equal(canonicalPath("//pricing//"), "/pricing");
  assert.equal(canonicalPath("/"), "/");
  assert.equal(findRedirect(TABLE, "/pricing/")?.status, 301,
    "SH1133: a trailing slash is the same address on the server, so it is here");
  assert.equal(findRedirect(TABLE, "/Pricing"), null, "and case is not folded, as it is not there");
});

test("SH1133: the table is loaded once per interval, and a failed load keeps the last one", async () => {
  let clock = 0;
  let loads = 0;
  let fail = false;
  const lookup = redirectLookup(async () => {
    loads += 1;
    if (fail) throw new Error("unreachable");
    return TABLE;
  }, 1000, () => clock);

  const [a, b] = await Promise.all([lookup("/pricing"), lookup("/about")]);
  assert.equal(a?.status, 301);
  assert.equal(b?.status, 302, "SH1133: a request during the first load waits for it");
  assert.equal(loads, 1, "SH1133: concurrent first requests share one load");

  await lookup("/pricing");
  assert.equal(loads, 1, "SH1133: inside the interval nothing is fetched again");

  clock = 1000;
  fail = true;
  assert.equal((await lookup("/pricing"))?.status, 301,
    "SH1133: an unreachable CMS keeps the last table rather than failing the request");
  assert.equal(loads, 2);
});

test("SH1133: the loader reads the CDA endpoint, and an instance without it is an empty table", async () => {
  const calls = [];
  const original = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init });
    return url.includes("old-instance")
      ? new Response("", { status: 404 })
      : new Response(JSON.stringify(TABLE), { status: 200 });
  };
  try {
    assert.deepEqual(await cdaRedirects("https://cms.example.test/", "site 1", "tok")(), TABLE);
    assert.equal(calls[0].url, "https://cms.example.test/api/v2/cda/site/site%201/redirects");
    assert.equal(calls[0].init.headers.Key, "tok");
    assert.deepEqual(await cdaRedirects("https://old-instance.test", "s")(), []);
    assert.equal(await cdaRedirects("https://cms.example.test", "")(), null,
      "SH1133: no site configured, nothing to ask");
  } finally {
    globalThis.fetch = original;
  }
});

test("SH1133: the middleware answers with the table's status, before any page is looked up", async () => {
  const source = await readFile(
    fileURLToPath(new URL("../template/middleware.ts", import.meta.url)), "utf8");
  assert.match(source, /NextResponse\.redirect\(destination, found\.status\)/,
    "SH1133: Next's redirect() answers 307/308; only a response carries the 301 or 302 the table names");
  assert.match(source, /redirectLookup\(\s*cdaRedirects\(/);
  assert.doesNotMatch(source, /matcher: \[[^\]]*\\\./,
    "SH1133: the matcher must not skip paths with a dot — a replicated site's old addresses end in .html");
});
