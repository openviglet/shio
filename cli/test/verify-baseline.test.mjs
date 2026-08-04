/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH142 — the accepted-findings baseline.
 *
 * Two properties are the whole feature and both are asserted here:
 *  1. an accepted finding does not fail the gate, and
 *  2. an accepted finding that no longer exists *does*.
 *
 * Without (2) the file decays into a blanket suppression and hides the next real defect at
 * the same address — which would make it worse than not having it. The third property is
 * the one a truncated report forces: a finding that did not fit is not a finding that is
 * gone, so `stale` must not fail a run it cannot prove anything about.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as verify from "../src/commands/verify.mjs";
import {
  acceptedFrom,
  applyBaseline,
  parseBaseline,
  serializeBaseline,
} from "../src/verify-baseline.mjs";
import { norm } from "./support/paths.mjs";

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

function scriptedFetch(body) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
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

/** An in-memory fs with only the calls the command makes. Keys are `norm`ed — see SH198. */
function memFs(files = {}) {
  const store = new Map(Object.entries(files).map(([path, body]) => [norm(path), body]));
  return {
    store,
    async readFile(path) {
      const key = norm(path);
      if (!store.has(key)) {
        const error = new Error(`ENOENT: ${key}`);
        error.code = "ENOENT";
        throw error;
      }
      return store.get(key);
    },
    async writeFile(path, contents) {
      store.set(norm(path), contents);
    },
    async mkdir() {},
    /**
     * SH212 — `verify` reads the tree now, and these fixtures hold a baseline and nothing else.
     * A directory the project does not have is the projection group's "nothing to check", which
     * is exactly the state every test here is in.
     */
    async readdir(path) {
      const error = new Error(`ENOENT: ${norm(path)}`);
      error.code = "ENOENT";
      throw error;
    },
  };
}

function ctxWith(fetchImpl, fs = memFs()) {
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    fs,
  };
}

const NO_ALT = {
  check: "routes",
  rule: "image-no-alt",
  severity: "warning",
  address: "post:Blog/blog/hello",
  field: "body",
  message: "Image '/file_source/a/rule.png' has no alt attribute.",
  fix: "Add alt text, or alt=\"\" if the image is decorative.",
};

const UNKNOWN_FIELD = {
  check: "content",
  rule: "unknown-field",
  severity: "error",
  address: "post:Blog/blog/hello",
  field: "legacy",
  message: "This post stores 'legacy', which post-type 'Page' does not declare.",
  fix: "Add it to the post-type, or drop the key.",
};

function report(findings, extra = {}) {
  const errors = findings.filter((f) => f.severity === "error").length;
  return {
    ok: errors === 0,
    checks: ["content", "routes"],
    checked: 12,
    errors,
    warnings: findings.length - errors,
    found: findings.length,
    truncated: false,
    findings,
    ...extra,
  };
}

const BASELINE_PATH = "/repo/shio/verify-baseline.json";

function baselineFile(entries) {
  return JSON.stringify({ version: 1, accepted: entries });
}

// --- the pure half ---

test("an accepted finding is subtracted and its counts move with it", () => {
  const result = applyBaseline(
    report([NO_ALT, UNKNOWN_FIELD]),
    parseBaseline(baselineFile([{ rule: "unknown-field", address: NO_ALT.address, field: "legacy" }])),
  );

  assert.equal(result.errors, 0);
  assert.equal(result.ok, true);
  assert.equal(result.findings.length, 1);
  assert.equal(result.accepted.length, 1);
  // The server's own total stays what the server produced: the finding happened, it was
  // forgiven. A report that rewrote `found` could not be reconciled with the instance.
  assert.equal(result.found, 2);
});

test("matching is on rule + address + field, not the message", () => {
  const moved = { ...NO_ALT, message: "a completely different sentence" };
  const entry = { rule: NO_ALT.rule, address: NO_ALT.address, field: NO_ALT.field };

  assert.equal(applyBaseline(report([moved]), parseBaseline(baselineFile([entry]))).accepted.length, 1);
  // …but a different field at the same address is a different finding.
  const other = { ...NO_ALT, field: "intro" };
  assert.equal(applyBaseline(report([other]), parseBaseline(baselineFile([entry]))).accepted.length, 0);
});

test("an accepted finding the run did not produce is reported stale", () => {
  const result = applyBaseline(
    report([NO_ALT]),
    parseBaseline(baselineFile([
      { rule: NO_ALT.rule, address: NO_ALT.address, field: NO_ALT.field },
      { rule: "duplicate-url", address: "post:Blog/blog/gone" },
    ])),
  );

  assert.deepEqual(result.stale.map((entry) => entry.rule), ["duplicate-url"]);
  assert.equal(result.staleUnknown, false);
});

test("a truncated report cannot prove an entry is stale", () => {
  const result = applyBaseline(
    report([NO_ALT], { truncated: true, found: 40 }),
    parseBaseline(baselineFile([{ rule: "duplicate-url", address: "post:Blog/blog/x" }])),
  );

  assert.equal(result.stale.length, 1);
  assert.equal(result.staleUnknown, true);
});

test("a malformed baseline is a named error, not a silently empty one", () => {
  assert.throws(() => parseBaseline("{not json"), /not valid JSON/);
  assert.throws(() => parseBaseline('{"accepted":"all"}'), /"accepted" array/);
  // Absent is legitimately empty — a project without a baseline is the normal case.
  assert.deepEqual(parseBaseline(null).accepted, []);
});

test("serialization is byte-identical for the same set, in any order", () => {
  const a = [
    { rule: "b-rule", address: "post:S/z" },
    { rule: "a-rule", address: "post:S/a", field: "body", reason: "decorative" },
  ];
  assert.equal(serializeBaseline(a), serializeBaseline([...a].reverse()));
  assert.match(serializeBaseline(a), /"rule": "a-rule"[\s\S]*"rule": "b-rule"/);
  assert.match(serializeBaseline(a), /\n$/);
});

test("accepting merges over the existing file rather than replacing it", () => {
  // A run narrowed by --site sees part of the project; replacing would drop the rest.
  const existing = parseBaseline(baselineFile([{ rule: "empty-folder", address: "folder:Other/x" }]));
  const { entries, added } = acceptedFrom(report([NO_ALT]), existing, "reviewed 2026-07");

  assert.equal(entries.length, 2);
  assert.deepEqual(added.map((entry) => entry.rule), ["image-no-alt"]);
  assert.equal(added[0].reason, "reviewed 2026-07");
});

// --- the command ---

test("a baselined error does not fail the run", async () => {
  const fs = memFs({
    [BASELINE_PATH]: baselineFile([
      { rule: "unknown-field", address: UNKNOWN_FIELD.address, field: "legacy", reason: "cleanup pending" },
    ]),
  });
  const ctx = ctxWith(scriptedFetch(report([UNKNOWN_FIELD])), fs);

  const result = await verify.run({}, [], ctx);

  assert.equal(result.exitCode, undefined);
  assert.match(ctx.stdout.text, /ok — 0 error\(s\), 0 warning\(s\), 1 accepted/);
});

test("a stale baseline entry fails the run even when the content is clean", async () => {
  const fs = memFs({
    [BASELINE_PATH]: baselineFile([{ rule: "image-no-alt", address: "post:Blog/blog/gone", field: "body" }]),
  });
  const ctx = ctxWith(scriptedFetch(report([])), fs);

  const result = await verify.run({}, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.match(ctx.stdout.text, /✗ accepted-not-found image-no-alt post:Blog\/blog\/gone · body/);
  assert.match(ctx.stderr.text, /no longer exist/);
});

test("a truncated run reports the unmatched entries without failing on them", async () => {
  const fs = memFs({
    [BASELINE_PATH]: baselineFile([{ rule: "image-no-alt", address: "post:Blog/blog/far", field: "body" }]),
  });
  const ctx = ctxWith(scriptedFetch(report([], { truncated: true, found: 90 })), fs);

  const result = await verify.run({}, [], ctx);

  assert.equal(result.exitCode, undefined);
  assert.match(ctx.stdout.text, /truncated so that proves nothing/);
});

test("no baseline file is the normal case and changes nothing", async () => {
  const ctx = ctxWith(scriptedFetch(report([NO_ALT])), memFs());

  const result = await verify.run({}, [], ctx);

  assert.equal(result.exitCode, undefined);
  assert.equal(result.accepted.length, 0);
  assert.match(ctx.stdout.text, /! image-no-alt/);
});

test("an explicit --baseline that is not there is a mistake, not an empty baseline", async () => {
  await assert.rejects(
    () => verify.run({ baseline: "ci/accepted.json" }, [], ctxWith(scriptedFetch(report([])), memFs())),
    /no baseline at ci\/accepted\.json/,
  );
});

test("--accept writes the findings it accepted and names each one", async () => {
  const fs = memFs();
  const ctx = ctxWith(scriptedFetch(report([NO_ALT, UNKNOWN_FIELD])), fs);

  await verify.run({ accept: true, reason: "triaged" }, [], ctx);

  const written = JSON.parse(fs.store.get(BASELINE_PATH));
  assert.deepEqual(written.accepted.map((entry) => entry.rule), ["image-no-alt", "unknown-field"]);
  assert.equal(written.accepted[0].reason, "triaged");
  assert.match(ctx.stdout.text, /accepted 2 new finding\(s\)/);
  assert.match(ctx.stdout.text, /\+ image-no-alt post:Blog\/blog\/hello · body/);
});

test("--accept on a truncated report says it covered only part of it", async () => {
  const fs = memFs();
  const ctx = ctxWith(scriptedFetch(report([NO_ALT], { truncated: true, found: 50 })), fs);

  await verify.run({ accept: true }, [], ctx);

  assert.match(ctx.stdout.text, /report was truncated at 1 of 50/);
});

test("the scoping flags reach the query string and the subset is reported", async () => {
  const fetchImpl = scriptedFetch(
    report([], { partial: true, scope: "folder:Blog/news" }),
  );
  const ctx = ctxWith(fetchImpl, memFs());

  await verify.run({ folder: "folder:Blog/news", since: "2026-01-01T00:00:00Z|abc" }, [], ctx);

  const params = new URL(fetchImpl.calls[0].url).searchParams;
  assert.equal(params.get("folder"), "folder:Blog/news");
  assert.equal(params.get("since"), "2026-01-01T00:00:00Z|abc");
  assert.match(ctx.stdout.text, /scope: folder:Blog\/news/);
  assert.match(ctx.stdout.text, /covered a subset/);
});
