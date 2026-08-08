/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH397 — "can I work here", answered once.
 *
 * The interesting assertions are not that each check reads its endpoint. They are the two
 * judgements the command exists to make:
 *
 * - **An unreachable instance is one problem, not five.** Every check below it becomes
 *   unanswerable, and reporting those as failures would invent four defects out of one — the same
 *   rule SH141 applies to a page it could not fetch and SH209 to a page it could not photograph.
 * - **`dev` is not an answer to "which build".** It is the same string before and after a rebuild,
 *   which is exactly the mode where the instance can lag the tree being edited. `builtAt` (SH460)
 *   is the comparable fact, and its absence is said out loud rather than left to read as current.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as doctor from "../src/commands/doctor.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

const MANIFEST = { version: "2026.3.1", commit: "0e85e86", builtAt: "2026-08-08T12:00:00Z" };
const SITES = { sites: [{ name: "Blog", url: "https://blog.example.com/" }, { name: "Docs", url: "" }] };

/**
 * @param routes a map of URL fragment → `{ status, body }`, or a thrown Error for a dead socket.
 */
function ctxWith(routes, { files = {} } = {}) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    async fetch(url) {
      const key = Object.keys(routes).find((each) => String(url).includes(each));
      const route = key ? routes[key] : { status: 404, body: {} };
      if (route instanceof Error) throw route;
      return {
        ok: route.status >= 200 && route.status < 300,
        status: route.status,
        headers: { get: () => null },
        async text() {
          return JSON.stringify(route.body);
        },
        async json() {
          return route.body;
        },
      };
    },
    fs: {
      async readFile(path) {
        const key = Object.keys(files).find((each) => String(path).replace(/\\/g, "/").endsWith(each));
        if (key) return files[key];
        const error = new Error(`ENOENT: ${path}`);
        error.code = "ENOENT";
        throw error;
      },
    },
  };
}

const healthy = () => ({
  "/agent/manifest": { status: 200, body: MANIFEST },
  "/render/post-types/drift": { status: 200, body: [] },
  "/agent/context": { status: 200, body: SITES },
});

const statusOf = (result, name) => result.checks.find((check) => check.name === name)?.status;

test("SH397: a healthy instance answers every check and exits 0", async () => {
  const ctx = ctxWith(healthy());

  const result = await doctor.run({}, [], ctx);

  assert.equal(statusOf(result, "instance"), "ok");
  assert.equal(statusOf(result, "model"), "ok");
  assert.equal(statusOf(result, "sites"), "ok");
  assert.equal(result.exitCode, 0);
  assert.match(ctx.stdout.text, /version 2026\.3\.1/);
  assert.match(ctx.stdout.text, /built 2026-08-08T12:00:00Z/,
    "the build stamp is the fact this task exists for, so it is on the line, not in --json only");
});

test("SH397: an unreachable instance is one problem, not five", async () => {
  const ctx = ctxWith({ "/agent/manifest": new Error("ECONNREFUSED") });

  const result = await doctor.run({}, [], ctx);

  assert.equal(result.problems, 1,
    "every later check is unanswerable, and reporting those as failures would invent four " +
      "defects out of one — the SH141/SH209 rule in a third place");
  assert.equal(statusOf(result, "model"), "unknown");
  assert.equal(statusOf(result, "sites"), "unknown");
  assert.equal(result.exitCode, 1);
});

test("SH397: refused credentials are their own line, not 'unreachable'", async () => {
  const error = new Error("401");
  error.status = 401;
  const ctx = ctxWith({ "/agent/manifest": error });

  const result = await doctor.run({}, [], ctx);

  assert.equal(statusOf(result, "instance"), "problem");
  assert.match(ctx.stdout.text, /credentials were refused \(401\)/,
    "one line apart in the output, a long way apart in what the reader does next");
  assert.match(ctx.stdout.text, /SHIO_USER/);
});

test("SH397: a missing build stamp is unknown, never a quiet 'current'", async () => {
  const ctx = ctxWith({
    ...healthy(),
    "/agent/manifest": { status: 200, body: { version: "dev" } },
  });

  const result = await doctor.run({}, [], ctx);

  assert.equal(statusOf(result, "instance"), "unknown",
    "`dev` is the same string before and after a rebuild, so on its own it distinguishes nothing " +
      "— which is the whole defect SH397 names");
  assert.match(ctx.stdout.text, /no build timestamp/);
  assert.equal(result.exitCode, 0, "a stamp nobody can read is not a failing instance");
});

test("SH397: model drift is a problem, and names the repair", async () => {
  const ctx = ctxWith({
    ...healthy(),
    "/render/post-types/drift": {
      status: 200,
      body: [{ postType: "Theme", missingFields: ["TOKENS"] }],
    },
  });

  const result = await doctor.run({}, [], ctx);

  assert.equal(statusOf(result, "model"), "problem");
  assert.match(ctx.stdout.text, /Theme\(TOKENS\)/);
  assert.match(ctx.stdout.text, /reconcile/, "P6: a problem carries what fixes it");
  assert.equal(result.exitCode, 1);
});

test("SH397: a site with no front-end URL is said, not silently counted", async () => {
  const ctx = ctxWith(healthy());

  await doctor.run({}, [], ctx);

  assert.match(ctx.stdout.text, /Docs \(no front-end URL\)/,
    "a site nothing serves is the reason a later `snapshot` or `verify --delivery` cannot run");
});

test("SH397: a local content tree is reported with its cursor", async () => {
  const ctx = ctxWith(healthy(), {
    files: { "shio/.shio/content-base.json": JSON.stringify({ cursor: "2026-08-08T00:00:00Z|abc" }) },
  });

  const result = await doctor.run({}, [], ctx);

  assert.equal(statusOf(result, "content"), "ok");
  assert.match(ctx.stdout.text, /cursor 2026-08-08T00:00:00Z\|abc/);
  assert.match(ctx.stdout.text, /push --content --check/,
    "doctor stays cheap and points at the expensive check rather than running it");
});

test("SH397: --json is the same answer as data", async () => {
  const ctx = ctxWith(healthy());

  await doctor.run({ json: true }, [], ctx);

  const parsed = JSON.parse(ctx.stdout.text);
  assert.equal(parsed.checks.length, 4);
  assert.deepEqual(parsed.checks.map((check) => check.name), ["instance", "model", "sites", "content"]);
});
