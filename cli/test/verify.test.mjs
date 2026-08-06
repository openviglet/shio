/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
import { readLocal } from "../src/content-store.mjs";

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
/**
 * An empty project: no baseline, and — since SH212 made `verify` read the tree — no post-types,
 * no content and no assets either. Every one of these is a legitimate state, and the projection
 * group's answer to all of them is "nothing to check".
 */
function noFiles() {
  return {
    async readFile(path) {
      throw enoent(path);
    },
    async readdir(path) {
      throw enoent(path);
    },
  };
}

function enoent(path) {
  const error = new Error(`ENOENT: ${path}`);
  error.code = "ENOENT";
  return error;
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
  // SH212 — the tree's own group runs by default and is named first, because its findings are
  // the ones a reader can act on without leaving the machine.
  assert.match(ctx.stdout.text, /\[projection, replication, content, routes\]/);
  // And it says it checked nothing rather than passing quietly: "0 findings" and "not run" have
  // to be distinguishable (SH302's lesson, one command along).
  assert.match(ctx.stdout.text, /projection: nothing to check in this tree/);
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

// --- the tree's own findings, and the gate that needs no instance (SH212) ---

/**
 * A project on disk: post-types, one post and whichever assets the test wants. The keys are what
 * `readLocalDefs`, `readLocal` and `readLocalAssets` walk, so this is the real read path.
 */
function projectFs({ assets = [], hero = "Blog/media/logo.png", extra = {} } = {}) {
  const files = new Map([
    ["/repo/shio/post-types/Article.json", JSON.stringify({
      name: "Article",
      fields: [
        { name: "headline", widget: "Text", ordinal: 1 },
        { name: "hero", widget: "File", ordinal: 2 },
        // SH374 — a code field, so a `files:` block in one of these fixtures is a real sidecar.
        { name: "HTML", widget: "Ace Editor - HTML", ordinal: 3 },
      ],
    })],
    ["/repo/shio/content/Blog/blog/hello.md",
      `---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Hello\n  hero: ${hero}\n---\n`],
    ...assets.map((path) => [`/repo/shio/assets/${path}`, "bytes"]),
    ...Object.entries(extra),
  ]);
  const norm = (path) => String(path).replaceAll("\\", "/");
  return {
    async readFile(path) {
      const key = norm(path);
      if (!files.has(key)) {
        const error = new Error(`ENOENT: ${key}`);
        error.code = "ENOENT";
        throw error;
      }
      return files.get(key);
    },
    /**
     * Honours `withFileTypes`, which node's does and a fake that ignored it would hide: the
     * content walk asks for entries and `readLocalDefs` asks for plain names, so a stub that
     * always returned one shape would pass here and fail on a real disk.
     */
    async readdir(path, options) {
      const prefix = `${norm(path)}/`;
      const names = new Set();
      for (const key of files.keys()) {
        if (!key.startsWith(prefix)) continue;
        const rest = key.slice(prefix.length);
        const cut = rest.indexOf("/");
        names.add(cut < 0 ? rest : `${rest.slice(0, cut)}/`);
      }
      if (!names.size) {
        const error = new Error(`ENOENT: ${prefix}`);
        error.code = "ENOENT";
        throw error;
      }
      const sorted = [...names].sort();
      if (!options?.withFileTypes) return sorted.map((name) => name.replace(/\/$/, ""));
      return sorted.map((name) => ({
        name: name.replace(/\/$/, ""),
        isDirectory: () => name.endsWith("/"),
      }));
    },
    async writeFile() {},
    async mkdir() {},
  };
}

function projectCtx(fetchImpl, fsImpl) {
  return { ...ctxWith(fetchImpl), fs: fsImpl };
}

test("SH212: a broken asset path is a finding, and the tree alone proves it", async () => {
  // No fetch at all — the assertion is that the command never reaches for one. `--checks
  // projection` is the gate that runs with no credentials and no instance, which is the whole
  // point of moving a check this cheap out of `push --content`.
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = projectCtx(fetchImpl, projectFs({ assets: [] }));

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.deepEqual(fetchImpl.calls, [], "the server was never asked");
  assert.match(ctx.stdout.text, /asset-missing post:Blog\/blog\/hello · hero/);
  assert.match(ctx.stdout.text, /Blog\/media\/logo\.png/);
  // A run over one tree and no instance is not a statement about the site (SH143's rule).
  assert.match(ctx.stdout.text, /covered a subset/);
});

test("SH212: the asset being there is a clean projection run", async () => {
  const ctx = projectCtx(scriptedFetch(CLEAN), projectFs({ assets: ["Blog/media/logo.png"] }));

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.equal(result.exitCode, undefined);
  assert.match(ctx.stdout.text, /ok — 0 error\(s\)/);
});

test("SH212: the tree's findings and the server's arrive in one report", async () => {
  const ctx = projectCtx(scriptedFetch(WARNED), projectFs({ assets: [] }));

  const result = await verify.run({}, [], ctx);

  assert.equal(result.exitCode, 1);
  // One error from the tree, one warning from the server, counted together — `ok` has to keep
  // meaning "nothing an error" across both halves.
  assert.match(ctx.stdout.text, /failed — 1 error\(s\), 1 warning\(s\)/);
  assert.match(ctx.stdout.text, /asset-missing/);
});

test("SH212: an accepted asset finding is subtracted like any other", async () => {
  // The reason this is a `verify` finding rather than a second command: SH142's baseline works on
  // `(rule, address, field)` triples and needed nothing new to hold a client-side one.
  const fsImpl = projectFs({ assets: [] });
  const baseline = JSON.stringify({
    version: 1,
    accepted: [{ rule: "asset-missing", address: "post:Blog/blog/hello", field: "hero" }],
  });
  const original = fsImpl.readFile;
  fsImpl.readFile = async (path) =>
    String(path).replaceAll("\\", "/").endsWith("verify-baseline.json")
      ? baseline
      : original(path);
  const ctx = projectCtx(scriptedFetch(CLEAN), fsImpl);

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.equal(result.exitCode, undefined);
  assert.match(ctx.stdout.text, /1 accepted/);
});

test("SH212: --delivery and the tree-only gate are refused together", async () => {
  // One asks for a proof that leaves the process and the other for the group that cannot.
  // Dropping either silently is how a flag that requested a proof and got none reads as a pass.
  const ctx = projectCtx(scriptedFetch(CLEAN), projectFs());

  await assert.rejects(
    () => verify.run({ checks: "projection", delivery: true }, [], ctx),
    /cannot be combined/,
  );
});

test("SH374: a missing sidecar reaches the report instead of stopping the run", async () => {
  // `readLocal` refuses on the first `files:` entry naming nothing, which is right for a write and
  // wrong for a lint — an exception on one offender is what SH212 replaced with findings, and this
  // is the second half of that argument.
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = projectCtx(fetchImpl, projectFs({
    assets: ["Blog/media/logo.png"],
    extra: {
      "/repo/shio/content/Blog/blog/layout.md": [
        "---",
        "site: Blog",
        "url: /blog/layout",
        "type: Article",
        "folder: /blog",
        "files:",
        "  HTML: layout.html",
        "fields:",
        "  headline: Layout",
        "---",
        "",
      ].join("\n"),
    },
  }));

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.deepEqual(fetchImpl.calls, [], "and it needed no instance to say so");
  assert.match(ctx.stdout.text, /sidecar-missing post:Blog\/blog\/layout · HTML/);
  assert.match(ctx.stdout.text, /layout\.html/);
});

test("SH374: a dangling translationOf is an error unscoped and a warning scoped", async () => {
  const tree = {
    assets: ["Blog/media/logo.png"],
    extra: {
      "/repo/shio/content/Blog/pt/ola.md": [
        "---",
        "site: Blog",
        "url: /pt/ola",
        "type: Article",
        "folder: /pt",
        "translationOf: /en/gone",
        "fields:",
        "  headline: Ola",
        "---",
        "",
      ].join("\n"),
    },
  };

  const unscoped = projectCtx(scriptedFetch(CLEAN), projectFs(tree));
  const strict = await verify.run({ checks: "projection" }, [], unscoped);
  assert.equal(strict.exitCode, 1);
  assert.match(unscoped.stdout.text, /translation-target-missing/);

  // Scoped to a site, the same tree is not a statement about where the original lives (SH143).
  const scoped = projectCtx(scriptedFetch(CLEAN), projectFs(tree));
  const lenient = await verify.run({ checks: "projection", site: "Blog" }, [], scoped);
  assert.equal(lenient.exitCode, undefined);
  assert.match(scoped.stdout.text, /! translation-target-missing/);
});

test("SH400: two files claiming one address reach the report, and the tree alone proves it", async () => {
  // `pathToUrl` drops a trailing `index` segment, so these two name one post. Before this the
  // map `readLocal` builds kept the second and never mentioned the first — a push then wrote one
  // body under that URL and reported success, which is the shape P7 exists to forbid.
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = projectCtx(fetchImpl, projectFs({
    assets: ["Blog/media/logo.png"],
    extra: {
      "/repo/shio/content/Blog/blog/hello/index.md": [
        "---",
        "site: Blog",
        "url: /blog/hello",
        "type: Article",
        "folder: /blog",
        "fields:",
        "  headline: The other one",
        "  hero: Blog/media/logo.png",
        "---",
        "",
      ].join("\n"),
    },
  }));

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.deepEqual(fetchImpl.calls, [], "and it needed no instance to say so");
  assert.match(ctx.stdout.text, /duplicate-address post:Blog\/blog\/hello/);
  assert.match(ctx.stdout.text, /hello\.md and shio\/content\/Blog\/blog\/hello\/index\.md/);
});

test("SH400: the write side refuses, naming both paths", async () => {
  // Same argument as the missing sidecar one file over: `post.upsert` declares *whole* state, so
  // delivering one of two files replaces the other's copy on the server. Half a description is
  // not a state anyone meant, and stopping is the better of the two readings.
  const ctx = projectCtx(scriptedFetch(CLEAN), projectFs({
    extra: {
      "/repo/shio/content/Blog/blog/hello/index.md":
        "---\nsite: Blog\nurl: /blog/hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Other\n---\n",
    },
  }));

  await assert.rejects(
    readLocal(ctx, { dir: "shio/content" }),
    (error) => {
      assert.match(error.message, /both project to `post:Blog\/blog\/hello`/);
      assert.match(error.message, /shio\/content\/Blog\/blog\/hello\.md/);
      assert.match(error.message, /shio\/content\/Blog\/blog\/hello\/index\.md/);
      // Teaching, not just refusing: the fix is a choice between two files (P6).
      assert.match(error.message, /Keep one of them/);
      return true;
    },
  );
});

test("SH401: two files differing only in case are a finding, not a clean tree", async () => {
  // The local half. These are two valid posts at two valid addresses — nothing here is a
  // duplicate — and the tree is still one that only exists on Linux. Authored by hand, since a
  // pull now refuses to write the pair in the first place.
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = projectCtx(fetchImpl, projectFs({
    assets: ["Blog/media/logo.png"],
    extra: {
      "/repo/shio/content/Blog/blog/Hello.md": [
        "---",
        "site: Blog",
        "url: /blog/Hello",
        "type: Article",
        "folder: /blog",
        "fields:",
        "  headline: The other one",
        "  hero: Blog/media/logo.png",
        "---",
        "",
      ].join("\n"),
    },
  }));

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.deepEqual(fetchImpl.calls, [], "and it needed no instance to say so");
  assert.match(ctx.stdout.text, /path-collision/);
  assert.match(ctx.stdout.text, /differ only in case/);
});

test("SH401: the write side refuses an unportable pair too", async () => {
  const ctx = projectCtx(scriptedFetch(CLEAN), projectFs({
    extra: {
      "/repo/shio/content/Blog/blog/Hello.md":
        "---\nsite: Blog\nurl: /blog/Hello\ntype: Article\nfolder: /blog\nfields:\n  headline: Other\n---\n",
    },
  }));

  await assert.rejects(
    readLocal(ctx, { dir: "shio/content" }),
    (error) => {
      assert.match(error.message, /differ only in case/);
      assert.match(error.message, /Windows or macOS checkout/);
      // Not SH400's finding: these are two posts, not one claimed twice.
      assert.doesNotMatch(error.message, /both project to/);
      return true;
    },
  );
});

test("SH407: two folder markers differing only in case are a finding too", async () => {
  // The folder root is the second place a name becomes a path, and until now no tree-only gate
  // read it at all — `verify` never opened `shio/folders/`.
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = projectCtx(fetchImpl, projectFs({
    assets: ["Blog/media/logo.png"],
    extra: {
      "/repo/shio/folders/Blog/news/_folder.md": "---\nsite: Blog\nfolder: /news\n---\n",
      "/repo/shio/folders/Blog/News/_folder.md": "---\nsite: Blog\nfolder: /News\n---\n",
    },
  }));

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.deepEqual(fetchImpl.calls, [], "and it needed no instance to say so");
  assert.match(ctx.stdout.text, /path-collision/);
  assert.match(ctx.stdout.text, /_folder\.md/);
});

test("SH410: the server-free gate does not call server acceptances stale", async () => {
  // The measurement. `--checks projection` sets `callRemote: false`, so the report holds tree
  // findings only. Before the fix every acceptance for a server rule matched nothing, was called
  // stale, and the run exited 1 — breaking the exact gate SH212 built to run without an instance.
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = projectCtx(fetchImpl, projectFs({
    assets: ["Blog/media/logo.png"],
    extra: {
      "/repo/shio/verify-baseline.json": JSON.stringify({
        version: 1,
        accepted: [
          { rule: "required-field-empty", address: "post:Blog/blog/hello", field: "headline",
            reason: "tracked in SH-000" },
        ],
      }),
    },
  }));

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.deepEqual(fetchImpl.calls, [], "it still needs no instance");
  assert.equal(result.exitCode, undefined, "a run that could not produce the finding must not "
    + "claim the finding is gone");
  assert.doesNotMatch(ctx.stdout.text, /no longer exist/);
});

test("SH410: a full run still reports an acceptance nothing matched", async () => {
  // The property that must survive the fix: a baseline quietly forgiving a defect nobody has is
  // how a gate rots, so where the run *could* have produced the finding, staleness still fails.
  const ctx = projectCtx(scriptedFetch(CLEAN), projectFs({
    assets: ["Blog/media/logo.png"],
    extra: {
      "/repo/shio/verify-baseline.json": JSON.stringify({
        version: 1,
        accepted: [
          { rule: "required-field-empty", address: "post:Blog/blog/hello", field: "headline" },
        ],
      }),
    },
  }));

  const result = await verify.run({}, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.match(ctx.stderr.text, /no longer exist/);
});

test("SH411: the tree-only gate still fails on a projection acceptance it proved spent", async () => {
  // What SH410's regression had switched off. The baseline holds one projection entry (covered by
  // this run, and the tree no longer produces it) and one server entry (which this run cannot
  // judge). Before SH411 the second silenced the first and the run passed.
  const fetchImpl = scriptedFetch(CLEAN);
  const ctx = projectCtx(fetchImpl, projectFs({
    assets: ["Blog/media/logo.png"],
    extra: {
      "/repo/shio/verify-baseline.json": JSON.stringify({
        version: 1,
        accepted: [
          { rule: "asset-missing", address: "post:Blog/blog/hello", field: "hero" },
          { rule: "required-field-empty", address: "post:Blog/blog/hello", field: "headline" },
        ],
      }),
    },
  }));

  const result = await verify.run({ checks: "projection" }, [], ctx);

  assert.deepEqual(fetchImpl.calls, [], "and still with no instance");
  assert.equal(result.exitCode, 1);
  assert.match(ctx.stderr.text, /1 accepted finding\(s\)/);
  // The server entry is reported as unchecked rather than condemned or ignored.
  assert.match(ctx.stdout.text, /1 accepted finding\(s\) were not checked by this run/);
});
