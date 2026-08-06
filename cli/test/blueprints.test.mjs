/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH92/SH93 — `shio blueprints` and `shio apply --blueprint`. Network-free: a routed
 * fake fetch answers the catalogue, the detail and the apply.
 *
 * The properties under test are the ones a caller would be hurt by if they broke:
 * a repeated --param is not silently reduced to the last one, values are coerced to the
 * types the package declared (a terminal has only strings), --dry-run never reaches the
 * write path, a failed post-apply verification is a non-zero exit rather than a printed
 * shrug, and a carried front-end file is never written over an existing one without
 * --force.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as apply from "../src/commands/apply.mjs";
import * as blueprints from "../src/commands/blueprints.mjs";
import { parseArgs } from "../src/args.mjs";
import { endsWithPath, norm } from "./support/paths.mjs";

const CATALOGUE = [
  {
    name: "blog",
    title: "Blog",
    description:
      "An Article post-type, a /blog folder and three published sample posts, which is " +
      "enough to show the shape and few enough to delete.",
    version: "1.0.0",
    postTypes: 1,
    posts: 3,
    files: 0,
    params: {
      type: "object",
      properties: { blogTitle: { type: "string", default: "Blog" } },
    },
  },
  {
    name: "landing",
    title: "Landing page",
    description: "One campaign page composed of four known sections from the vocabulary.",
    version: "1.0.0",
    postTypes: 8,
    posts: 5,
    files: 1,
    params: {
      type: "object",
      required: ["brand"],
      properties: {
        brand: { type: "string", description: "The company name." },
        columns: { type: "integer", default: 3 },
        featured: { type: "boolean", default: false },
      },
    },
  },
];

const DETAIL = {
  manifest: {
    formatVersion: 1,
    name: "landing",
    title: "Landing page",
    description: "One campaign page.",
    version: "1.0.0",
    params: CATALOGUE[1].params,
    files: [{ path: "app/get-started/page.tsx" }],
  },
  postTypes: ["Page", "SectionHero"],
  folders: 2,
  posts: 5,
  files: [{ path: "app/get-started/page.tsx", text: "export default function Page() {}\n" }],
};

const APPLIED = {
  dryRun: false,
  blueprint: "landing",
  version: "1.0.0",
  site: "Acme",
  params: { site: "Acme", brand: "Acme", columns: "3" },
  postTypes: [
    { name: "Page", action: "exists", missing: ["sections"] },
    { name: "SectionHero", action: "create" },
  ],
  content: {
    dryRun: false,
    ops: 3,
    changed: 2,
    results: [
      { op: "folder.upsert", address: "folder:Acme/sections", action: "create" },
      { op: "post.upsert", address: "post:Acme/get-started", action: "create" },
      { op: "post.publish", address: "post:Acme/get-started", action: "noop" },
    ],
  },
  files: ["app/get-started/page.tsx"],
  verify: { ok: true, missing: [], errors: 0, warnings: 1, checks: ["content"] },
};

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** Answers by path: the catalogue, one detail, and the apply. */
function routedFetch(applied = APPLIED) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    const href = String(url);
    calls.push({ url: href, init });
    let body;
    if (/\/blueprints\/[^/]+\/apply$/.test(href)) body = applied;
    else if (/\/blueprints\/[^/?]+(\?|$)/.test(href)) body = DETAIL;
    else body = CATALOGUE;
    return {
      ok: true,
      status: 200,
      headers: { get: () => null },
      async text() {
        return JSON.stringify(body);
      },
      async json() {
        return body;
      },
    };
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

function ctxWith(fetchImpl, { existing = [] } = {}) {
  const written = [];
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    written,
    fs: {
      async readFile(path) {
        if (existing.some((name) => endsWithPath(path, name))) return "old contents";
        const error = new Error("not found");
        error.code = "ENOENT";
        throw error;
      },
      async mkdir() {},
      async writeFile(path, text) {
        written.push({ path: norm(path), text });
      },
    },
  };
}

test("the catalogue lists every package with its counts and parameters", async () => {
  const ctx = ctxWith(routedFetch());

  await blueprints.run({}, [], ctx);

  assert.match(ctx.stdout.text, /shio blueprints: 2/);
  assert.match(ctx.stdout.text, /blog\s+1\.0\.0\s+1 type\(s\), 3 post\(s\)/);
  assert.match(ctx.stdout.text, /params: brand \(required\), columns, featured/);
});

test("a package the instance did not ship with says so, and the built-in ones stay quiet", async () => {
  // SH96 — the origin is printed only when present. Somebody about to apply a package to
  // their own site should not have to ask where it came from; six first-party rows saying
  // "classpath" would be noise that trains the reader to skip the line that matters.
  const local = { ...CATALOGUE[0], name: "acme-intranet", origin: "/opt/shio/blueprints" };
  const body = [CATALOGUE[0], local];
  const ctx = ctxWith(async () => ({
    ok: true,
    status: 200,
    headers: { get: () => null },
    async text() {
      return JSON.stringify(body);
    },
    async json() {
      return body;
    },
  }));

  await blueprints.run({}, [], ctx);

  assert.match(ctx.stdout.text, /from:\s+\/opt\/shio\/blueprints \(not a built-in package\)/);
  assert.equal(ctx.stdout.text.match(/from:/g).length, 1, "only the local package says where");
});

test("one package in detail prints its parameters, its files and how to apply it", async () => {
  const ctx = ctxWith(routedFetch());

  await blueprints.run({}, ["landing"], ctx);

  assert.match(ctx.stdout.text, /--param brand=….*required/);
  assert.match(ctx.stdout.text, /--param columns=….*default 3/);
  assert.match(ctx.stdout.text, /app\/get-started\/page\.tsx/);
  assert.match(ctx.stdout.text, /shio apply --blueprint landing --site <site> --dry-run/);
});

test("a repeated --param is collected, not reduced to the last one", () => {
  const { flags } = parseArgs(
    ["--blueprint", "landing", "--param", "brand=Acme", "--param", "columns=4"],
    { booleans: ["dry-run"], repeatable: ["param"] },
  );

  assert.deepEqual(flags.param, ["brand=Acme", "columns=4"]);
  assert.equal(flags.blueprint, "landing");
});

test("parameters are coerced to the types the package declared", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);

  await apply.run(
    { blueprint: "landing", site: "Acme", param: ["brand=Acme", "columns=4", "featured=true"] },
    [],
    ctx,
  );

  const call = fetchImpl.calls.find((entry) => entry.url.endsWith("/apply"));
  const body = JSON.parse(call.init.body);
  assert.equal(body.site, "Acme");
  // A terminal has only strings; the declared type decides what is sent.
  assert.deepEqual(body.params, { brand: "Acme", columns: 4, featured: true });
  assert.equal(body.dryRun, false);
});

test("--dry-run plans and writes no front-end file", async () => {
  const fetchImpl = routedFetch({ ...APPLIED, dryRun: true, verify: null });
  const ctx = ctxWith(fetchImpl);

  await apply.run({ blueprint: "landing", site: "Acme", dryRun: true, param: "brand=Acme" }, [], ctx);

  const call = fetchImpl.calls.find((entry) => entry.url.endsWith("/apply"));
  assert.equal(JSON.parse(call.init.body).dryRun, true);
  assert.equal(ctx.written.length, 0);
  assert.match(ctx.stdout.text, /1 front-end file\(s\) would be written/);
});

test("an existing post-type that lacks a field the package writes is named in the report", async () => {
  const ctx = ctxWith(routedFetch());

  await apply.run({ blueprint: "landing", site: "Acme", param: "brand=Acme" }, [], ctx);

  assert.match(ctx.stdout.text, /post-type exists Page — the existing type has no sections/);
  assert.match(ctx.stdout.text, /post-type create SectionHero/);
  assert.match(ctx.stdout.text, /verify: ok — 0 error\(s\), 1 warning\(s\) \[content\]/);
});

test("carried files are written once, and never over an existing one without --force", async () => {
  const first = ctxWith(routedFetch());
  await apply.run({ blueprint: "landing", site: "Acme", param: "brand=Acme" }, [], first);
  assert.deepEqual(
    first.written.map((file) => file.path),
    ["/repo/app/get-started/page.tsx"],
  );
  assert.match(first.stdout.text, /wrote app\/get-started\/page\.tsx/);

  const second = ctxWith(routedFetch(), { existing: ["app/get-started/page.tsx"] });
  await apply.run({ blueprint: "landing", site: "Acme", param: "brand=Acme" }, [], second);
  assert.equal(second.written.length, 0);
  assert.match(second.stdout.text, /skipped app\/get-started\/page\.tsx \(exists/);

  const forced = ctxWith(routedFetch(), { existing: ["app/get-started/page.tsx"] });
  await apply.run(
    { blueprint: "landing", site: "Acme", force: true, param: "brand=Acme" },
    [],
    forced,
  );
  assert.equal(forced.written.length, 1);
  assert.match(forced.stdout.text, /overwrote app\/get-started\/page\.tsx/);
});

test("a failed post-apply verification exits non-zero and says where to look", async () => {
  const ctx = ctxWith(
    routedFetch({
      ...APPLIED,
      verify: {
        ok: false,
        missing: ["post:Acme/get-started"],
        errors: 2,
        warnings: 0,
        checks: ["content"],
      },
    }),
  );

  const result = await apply.run({ blueprint: "landing", site: "Acme", param: "brand=Acme" }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.match(ctx.stderr.text, /verification failed \(2 error\(s\)\)/);
  assert.match(ctx.stderr.text, /Missing: post:Acme\/get-started/);
  assert.match(ctx.stderr.text, /shio verify --site Acme/);
});

test("--check on a blueprint is a drift guard: plan only, non-zero when the site diverged", async () => {
  const fetchImpl = routedFetch({
    ...APPLIED,
    dryRun: true,
    verify: null,
    content: { dryRun: true, ops: 3, changed: 2, results: [] },
  });
  const ctx = ctxWith(fetchImpl);

  const result = await apply.run(
    { blueprint: "landing", site: "Acme", check: true, param: "brand=Acme" },
    [],
    ctx,
  );

  assert.equal(JSON.parse(fetchImpl.calls.at(-1).init.body).dryRun, true);
  assert.equal(result.exitCode, 1);
  assert.match(ctx.stderr.text, /does not match blueprint landing/);
});

test("--blueprint with no name explains itself instead of calling the server", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);

  await assert.rejects(() => apply.run({ blueprint: true }, [], ctx), /shio blueprints/);
  assert.equal(fetchImpl.calls.length, 0);
});

test("a --param without an = is refused with the shape of a working one", async () => {
  const ctx = ctxWith(routedFetch());

  await assert.rejects(
    () => apply.run({ blueprint: "landing", site: "Acme", param: "brand" }, [], ctx),
    /--param takes name=value/,
  );
});
