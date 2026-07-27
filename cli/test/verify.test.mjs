/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH99/SH100/SH101 — `shio verify` and `shio digest`. Network-free: a scripted fetch
 * records the request the command builds and returns a canned report.
 *
 * The exit code is the property under test. `shio verify` only earns its place in CI if
 * an error fails the build and a warning does not, and if `--strict` is the single opt-in
 * that changes that — a gate whose threshold is guesswork gets wrapped in `|| true`.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as verify from "../src/commands/verify.mjs";
import * as digest from "../src/commands/digest.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

/** Records every request; replies with `body` as JSON. */
function scriptedFetch(body, { status = 200 } = {}) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
    return {
      ok: status >= 200 && status < 300,
      status,
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

/** No baseline on disk — the default state, and what these cases are about (SH142). */
function noFiles() {
  return {
    async readFile(path) {
      const error = new Error(`ENOENT: ${path}`);
      error.code = "ENOENT";
      throw error;
    },
  };
}

function ctxWith(fetchImpl) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    fs: noFiles(),
  };
}

const CLEAN = { ok: true, checks: ["content", "routes"], checked: 12, errors: 0, warnings: 0, found: 0, truncated: false, findings: [] };

const WARNED = {
  ok: true,
  checks: ["content"],
  checked: 12,
  errors: 0,
  warnings: 1,
  found: 1,
  truncated: false,
  findings: [
    {
      check: "content",
      rule: "empty-folder",
      severity: "warning",
      address: "folder:Blog/drafts",
      message: "Folder 'drafts' holds no posts and no subfolders.",
      fix: "Add the content it was created for, or delete it.",
    },
  ],
};

const BROKEN = {
  ok: false,
  checks: ["content", "routes"],
  checked: 12,
  errors: 1,
  warnings: 1,
  found: 2,
  truncated: false,
  findings: [
    {
      check: "content",
      rule: "required-field-empty",
      severity: "error",
      address: "post:Blog/blog/hello",
      field: "headline",
      message: "Required field 'headline' is empty.",
      fix: "Set it with a post.upsert op.",
    },
    ...WARNED.findings,
  ],
};

test("a clean report exits zero and says what it checked", async () => {
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = ctxWith(fetchImpl);

  const result = await verify.run({}, [], ctx);

  assert.equal(result.exitCode, undefined);
  assert.match(ctx.stdout.text, /ok — 0 error\(s\), 0 warning\(s\) over 12 object\(s\)/);
  assert.match(ctx.stdout.text, /\[content, routes\]/);
  assert.equal(fetchImpl.calls[0].url, "http://shio.test/api/v2/agent/verify");
});

test("an error exits non-zero and prints the fix under the finding", async () => {
  const ctx = ctxWith(scriptedFetch(BROKEN));

  const result = await verify.run({}, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.match(ctx.stdout.text, /✗ required-field-empty post:Blog\/blog\/hello · headline/);
  assert.match(ctx.stdout.text, /→ Set it with a post.upsert op\./);
  assert.match(ctx.stderr.text, /1 error\(s\)/);
});

test("a warning alone does not fail the build, but --strict makes it", async () => {
  const lenient = ctxWith(scriptedFetch(WARNED));
  assert.equal((await verify.run({}, [], lenient)).exitCode, undefined);
  assert.match(lenient.stdout.text, /! empty-folder/);

  const strict = ctxWith(scriptedFetch(WARNED));
  assert.equal((await verify.run({ strict: true }, [], strict)).exitCode, 1);
  assert.match(strict.stderr.text, /warning\(s\)/);
});

test("site, checks and limit reach the query string; a bare positional is the site", async () => {
  const fetchImpl = scriptedFetch(CLEAN);
  await verify.run({ checks: "content", limit: "5" }, ["Blog"], ctxWith(fetchImpl));

  const url = new URL(fetchImpl.calls[0].url);
  assert.equal(url.searchParams.get("site"), "Blog");
  assert.equal(url.searchParams.get("checks"), "content");
  assert.equal(url.searchParams.get("limit"), "5");
});

test("--delivery adds the group without dropping the ones that need no network", async () => {
  const fetchImpl = scriptedFetch(CLEAN);
  await verify.run({ delivery: true }, ["Blog"], ctxWith(fetchImpl));

  // Not `checks=delivery`: a caller asking for the stronger proof still wants the lint, and
  // sending the group alone would silently switch the content rules off (SH141).
  assert.equal(
    new URL(fetchImpl.calls[0].url).searchParams.get("checks"),
    "content,routes,diagnostics,delivery",
  );
});

test("--delivery keeps an explicit --checks and does not duplicate the group", async () => {
  const fetchImpl = scriptedFetch(CLEAN);
  await verify.run({ delivery: true, checks: "routes,delivery" }, [], ctxWith(fetchImpl));

  assert.equal(new URL(fetchImpl.calls[0].url).searchParams.get("checks"), "routes,delivery");
});

test("the report's notes are printed — a cut nobody is told about reads as a clean pass", async () => {
  const ctx = ctxWith(
    scriptedFetch({
      ...CLEAN,
      notes: [
        "The delivery check stopped after 50 request(s) and left 12 unproved.",
        "2 unresolved link(s) matched routes declared as the front end's (frontend-routes).",
      ],
    }),
  );

  const result = await verify.run({}, [], ctx);

  assert.match(ctx.stdout.text, /left 12 unproved/);
  assert.match(ctx.stdout.text, /frontend-routes/);
  // Notes explain the numbers; they are not defects, so they must not change the exit code.
  assert.equal(result.exitCode ?? 0, 0);
});

test("a truncated report says how much it is not showing", async () => {
  const ctx = ctxWith(scriptedFetch({ ...BROKEN, truncated: true, found: 40 }));

  await verify.run({}, [], ctx);

  // A shortened list of defects reads exactly like a shorter list of defects.
  assert.match(ctx.stdout.text, /showing 2 of 40 findings/);
});

// --- shio digest ---

const DIGEST = {
  source: "content",
  address: "post:Blog/blog/hello",
  fields: ["body"],
  headings: [
    { level: 1, text: "Hello" },
    { level: 2, text: "Details" },
  ],
  landmarks: { main: 1, section: 2 },
  links: [
    { href: "/blog/other", internal: true, text: "Other" },
    { href: "https://example.com", internal: false, text: "Out" },
  ],
  images: [
    { src: "/file_source/a/hero.png" },
    { src: "/file_source/a/rule.png", alt: "" },
  ],
  words: 120,
  digest: "d:abc123def456",
};

test("digest prints the outline, landmarks, links and the images missing alt", async () => {
  const fetchImpl = scriptedFetch(DIGEST);
  const ctx = ctxWith(fetchImpl);

  await digest.run({}, ["post:Blog/blog/hello"], ctx);

  assert.match(ctx.stdout.text, /shio digest: d:abc123def456 \(content, 120 words\)/);
  assert.match(ctx.stdout.text, /h1 Hello/);
  assert.match(ctx.stdout.text, /h2 Details/);
  assert.match(ctx.stdout.text, /landmarks: main×1 section×2/);
  assert.match(ctx.stdout.text, /link → \/blog\/other/);
  assert.match(ctx.stdout.text, /link ↗ https:\/\/example.com/);
  // The distinction the digest exists to report: absent attribute vs decorative.
  assert.match(ctx.stdout.text, /img \/file_source\/a\/hero.png \[no alt\]/);
  assert.match(ctx.stdout.text, /img \/file_source\/a\/rule.png alt=""/);
  assert.equal(
    new URL(fetchImpl.calls[0].url).searchParams.get("address"),
    "post:Blog/blog/hello",
  );
});

test("an http(s) positional is sent as a url, not an address", async () => {
  const fetchImpl = scriptedFetch({ ...DIGEST, source: "fetch", status: 200 });
  await digest.run({}, ["https://site.example.com/blog/hello"], ctxWith(fetchImpl));

  const params = new URL(fetchImpl.calls[0].url).searchParams;
  assert.equal(params.get("url"), "https://site.example.com/blog/hello");
  assert.equal(params.get("address"), null);
});

test("digest with no target explains what to pass", async () => {
  await assert.rejects(
    () => digest.run({}, [], ctxWith(scriptedFetch(DIGEST))),
    /pass a page/,
  );
});
