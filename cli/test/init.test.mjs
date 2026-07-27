/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH119 — `shio init --blueprint`. Network-free: a routed fake fetch answers the
 * blueprint detail and the apply.
 *
 * What is worth asserting is the difference between the two sources of the same starter.
 * Files come from the package rather than the bundled template; `{{projectName}}` is
 * substituted on the way to disk, because a package's carried source is the one thing the
 * server does not substitute; an existing file is never overwritten without `--force`; and
 * the content half is applied only when a site is named — with the output *saying* it was
 * not, since a silent front-end-only scaffold is discovered later as an empty site.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { resolve } from "node:path";

import * as init from "../src/commands/init.mjs";
import { parseArgs } from "../src/args.mjs";
import { endsWithPath, norm } from "./support/paths.mjs";

const DETAIL = {
  manifest: {
    formatVersion: 1,
    name: "nextjs-starter",
    title: "Next.js starter",
    version: "1.0.0",
    params: { type: "object", properties: { projectName: { type: "string" } } },
  },
  postTypes: ["Page"],
  folders: 1,
  posts: 2,
  files: [
    { path: "package.json", text: '{\n  "name": "{{projectName}}"\n}\n' },
    { path: "app/[...slug]/page.tsx", text: "export default function Page() {}\n" },
    { path: ".gitignore", text: "node_modules\n" },
  ],
};

const APPLIED = {
  dryRun: false,
  blueprint: "nextjs-starter",
  version: "1.0.0",
  site: "Acme",
  params: { site: "Acme", projectName: "cool-site" },
  postTypes: [{ name: "Page", action: "create" }],
  content: { dryRun: false, ops: 3, changed: 3, results: [] },
  files: ["package.json"],
  verify: { ok: true, missing: [], errors: 0, warnings: 0, checks: ["content"] },
};

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

function routedFetch(detail = DETAIL) {
  const calls = [];
  const fetchImpl = async (url, requestInit) => {
    const href = String(url);
    calls.push({ url: href, method: requestInit?.method ?? "GET" });
    const body = /\/apply$/.test(href) ? APPLIED : detail;
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
    stdin: { isTTY: false },
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

function flagsFor(argv) {
  return parseArgs(argv);
}

test("scaffolds from a blueprint, substituting the project name into carried source", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor(["cool-site", "--blueprint", "nextjs-starter", "--yes"]);

  const result = await init.run(flags, positionals, ctx);

  // `resolve`, not a literal: the command resolves against cwd, so on Windows this is
  // a real absolute path with the drive letter, which is what it should be (SH198).
  assert.equal(result.targetDir, resolve("/repo", "cool-site"));
  const pkg = ctx.written.find((f) => f.path.endsWith("package.json"));
  assert.ok(pkg, "package.json should be written");
  assert.match(pkg.text, /"name": "cool-site"/);
  assert.doesNotMatch(pkg.text, /\{\{projectName\}\}/, "the placeholder must not reach disk");

  // The catch-all route's dots are a path, not a traversal, all the way to the file system.
  assert.ok(ctx.written.some((f) => endsWithPath(f.path, "app/[...slug]/page.tsx")));
  // Local config is written here, never carried by the package (it holds a token).
  assert.ok(ctx.written.some((f) => f.path.endsWith(".env.local")));
});

test("without --site the content half is not applied, and says so", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor(["cool-site", "--blueprint", "nextjs-starter", "--yes"]);

  const result = await init.run(flags, positionals, ctx);

  assert.equal(result.applied, null);
  assert.match(ctx.stdout.text, /content NOT applied/);
  assert.match(ctx.stdout.text, /shio apply --blueprint nextjs-starter --site/);
  assert.ok(
    !fetchImpl.calls.some((call) => /\/apply$/.test(call.url)),
    "no site means no write to the instance"
  );
});

test("with --site the content half is applied in the same command", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl);
  const { flags, positionals } = flagsFor([
    "cool-site", "--blueprint", "nextjs-starter", "--site", "Acme", "--yes",
  ]);

  const result = await init.run(flags, positionals, ctx);

  assert.equal(result.applied.site, "Acme");
  assert.match(ctx.stdout.text, /applied 3 content change\(s\) into site Acme/);
  assert.ok(fetchImpl.calls.some((call) => /\/apply$/.test(call.url)));
});

test("an existing file is skipped unless --force", async () => {
  const fetchImpl = routedFetch();
  const ctx = ctxWith(fetchImpl, { existing: ["package.json"] });
  const { flags, positionals } = flagsFor(["cool-site", "--blueprint", "nextjs-starter", "--yes"]);

  await init.run(flags, positionals, ctx);

  assert.match(ctx.stdout.text, /skipped package\.json \(exists — pass --force to overwrite\)/);
  assert.ok(!ctx.written.some((f) => endsWithPath(f.path, "cool-site/package.json")));

  const forcedCtx = ctxWith(routedFetch(), { existing: ["package.json"] });
  const forced = flagsFor([
    "cool-site", "--blueprint", "nextjs-starter", "--force", "--yes",
  ]);
  await init.run(forced.flags, forced.positionals, forcedCtx);
  assert.match(forcedCtx.stdout.text, /overwrote package\.json/);
});

test("a blueprint with no front-end half is a teaching refusal, not an empty project", async () => {
  const contentOnly = { ...DETAIL, files: null };
  const ctx = ctxWith(routedFetch(contentOnly));
  const { flags, positionals } = flagsFor(["cool-site", "--blueprint", "blog", "--yes"]);

  await assert.rejects(() => init.run(flags, positionals, ctx), /carries no front-end files/);
});
