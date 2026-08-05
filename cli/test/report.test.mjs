/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH104 — the handoff report. The engine is pure, so most of this file is about what the
 * document must not leave out: a change past the range, a deletion, a truncated lint, a
 * preview link that cannot honestly be built. Those are the failures that make a summary
 * worse than no summary, because a reviewer trusts it instead of reading the diff.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { buildReport } from "../src/report.mjs";
import * as report from "../src/commands/report.mjs";

const CONTEXT = {
  sites: [
    { name: "Blog", url: "https://blog.example.com/" },
    { name: "Docs", url: "" },
  ],
};

const CHANGES = {
  found: 3,
  window: false,
  since: "2026-07-01T00:00:00Z|a",
  cursor: "2026-07-02T00:00:00Z|z",
  more: false,
  changes: [
    {
      action: "CREATE",
      address: "post:Blog/blog/hello",
      at: "2026-07-01T10:00:00Z",
      by: "agent",
      what: "Created Hello",
      site: "Blog",
    },
    {
      action: "PUBLISH",
      address: "post:Blog/blog/hello",
      at: "2026-07-01T10:05:00Z",
      by: "agent",
      what: "Published Hello",
      site: "Blog",
    },
  ],
};

function sink() {
  const out = { text: "" };
  out.write = (s) => {
    out.text += s;
  };
  return out;
}

test("a change becomes a labelled row with a preview link", () => {
  const built = buildReport({ changes: CHANGES, context: CONTEXT });

  assert.match(built.markdown, /\*\*new\*\* `post:Blog\/blog\/hello`/);
  assert.match(built.markdown, /\*\*published\*\* `post:Blog\/blog\/hello`/);
  // The site URL's trailing slash must not survive into the link.
  assert.match(built.markdown, /\[preview\]\(https:\/\/blog\.example\.com\/blog\/hello\)/);
  assert.doesNotMatch(built.markdown, /example\.com\/\/blog/);
  assert.equal(built.changed, 2);
  assert.equal(built.cursor, "2026-07-02T00:00:00Z|z");
});

test("the cursor is in the body, so a pasted report still says what range it covered", () => {
  const built = buildReport({ changes: CHANGES, context: CONTEXT });
  assert.match(built.markdown, /cursor `2026-07-02T00:00:00Z\|z`/);
});

test("a deletion is listed even though it has no address and no preview", () => {
  // The row most likely to be dropped by an implementation that builds the list out of
  // links, and the row a reviewer most needs.
  const built = buildReport({
    changes: {
      ...CHANGES,
      changes: [
        { action: "DELETE", address: null, at: "2026-07-01T11:00:00Z", by: "agent", what: "Deleted Old page" },
      ],
    },
    context: CONTEXT,
  });

  assert.match(built.markdown, /\*\*deleted\*\* `\(object no longer exists\)`/);
  assert.match(built.markdown, /Deleted Old page/);
  // "no URL" about a deleted page would read as a defect rather than as the point.
  assert.doesNotMatch(built.markdown, /no preview/);
});

test("a preview link is never invented, and the report says which half is missing", () => {
  const built = buildReport({
    changes: {
      ...CHANGES,
      changes: [
        { action: "UPDATE", address: "post:Docs/guide", site: "Docs", what: "Edited" },
        { action: "UPDATE", address: "id:abc-123", what: "Edited a post with no URL" },
        { action: "UPDATE", address: "post:Other/x", site: "Other", what: "Edited" },
      ],
    },
    context: CONTEXT,
  });

  assert.match(built.markdown, /no preview: site Docs has no front-end URL configured/);
  assert.match(built.markdown, /no preview: no friendly URL, so nothing serves it/);
  assert.match(built.markdown, /no preview: site Other is not in the context pack/);
  assert.doesNotMatch(built.markdown, /\[preview\]/);
});

test("warnings are the open questions; errors are counted, not restated", () => {
  const built = buildReport({
    changes: CHANGES,
    context: CONTEXT,
    verify: {
      ok: false,
      errors: 2,
      warnings: 1,
      found: 3,
      truncated: false,
      findings: [
        { severity: "error", rule: "required-field-empty", address: "post:Blog/a", message: "Required field 'x' is empty." },
        { severity: "warning", rule: "empty-folder", address: "folder:Blog/drafts", message: "Folder 'drafts' holds no posts and no subfolders." },
      ],
    },
  });

  assert.match(built.markdown, /2 errors still open/);
  assert.match(built.markdown, /not ready to merge/);
  assert.match(built.markdown, /## Open questions/);
  assert.match(built.markdown, /Folder 'drafts' holds no posts/);
  // The error belongs to the agent's loop; duplicating it here creates a second copy of the
  // lint report that drifts from the first.
  assert.doesNotMatch(built.markdown, /Required field 'x' is empty/);
});

test("a clean lint says so in the first paragraph", () => {
  const built = buildReport({
    changes: CHANGES,
    context: CONTEXT,
    verify: { ok: true, errors: 0, warnings: 0, found: 0, truncated: false, findings: [] },
  });

  assert.match(built.markdown, /no errors: `shio verify` passes/);
  assert.doesNotMatch(built.markdown, /## Open questions/);
});

test("everything that makes the report incomplete is stated in the report", () => {
  const built = buildReport({
    changes: { ...CHANGES, more: true, notes: ["Entries younger than 1s are held back."] },
    context: CONTEXT,
    verify: {
      ok: true,
      errors: 0,
      warnings: 0,
      found: 90,
      truncated: true,
      partial: true,
      scope: "folder folder:Blog/blog",
      findings: [],
    },
  });

  assert.match(built.markdown, /## What this report does not cover/);
  assert.match(built.markdown, /More changes exist after this range/);
  assert.match(built.markdown, /held back/);
  assert.match(built.markdown, /cut at 0 of 90 findings/);
  assert.match(built.markdown, /covered a subset of the content \(folder folder:Blog\/blog\)/);
  assert.equal(built.caveats.length, 4);
});

test("incidents on the feed become a 'what broke' section (SH144)", () => {
  // The payoff of folding incidents into the change feed: the report already makes this call,
  // so a stale front end shows up in the handoff without a second read.
  const built = buildReport({
    changes: {
      ...CHANGES,
      incidents: [
        {
          kind: "webhook",
          subject: "https://front.example.com/revalidate",
          message: "gave up after 3 attempt(s)",
          fix: "Check the target is up.",
          count: 4,
          at: "2026-07-01T12:00:00Z",
          first: "2026-07-01T10:00:00Z",
        },
      ],
    },
    context: CONTEXT,
  });

  assert.match(built.markdown, /## What broke/);
  assert.match(built.markdown, /\*\*webhook\*\* `https:\/\/front\.example\.com\/revalidate`/);
  assert.match(built.markdown, /×4/);
  assert.match(built.markdown, /Check the target is up\./);
});

test("no incidents means no 'what broke' section at all", () => {
  const built = buildReport({ changes: CHANGES, context: CONTEXT });
  assert.doesNotMatch(built.markdown, /## What broke/);
});

test("a window says it is not a delta", () => {
  const built = buildReport({ changes: { ...CHANGES, window: true }, context: CONTEXT });
  assert.match(built.markdown, /most recent activity, not a delta/);
});

test("an empty range says nothing changed rather than rendering an empty list", () => {
  const built = buildReport({ changes: { ...CHANGES, found: 0, changes: [] }, context: CONTEXT });

  assert.match(built.markdown, /\*\*0 changes\*\*/);
  assert.match(built.markdown, /No content changed in this range\./);
  assert.doesNotMatch(built.markdown, /## What changed/);
});

test("no context pack at all still produces the summary", () => {
  // The degradation that matters: losing site URLs must cost the links, not the document.
  const built = buildReport({ changes: CHANGES });

  assert.match(built.markdown, /\*\*new\*\* `post:Blog\/blog\/hello`/);
  assert.match(built.markdown, /no preview: site Blog is not in the context pack/);
});

// --- the command ---

/** Replies per endpoint, and records every request. */
function routedFetch(bodies) {
  const calls = [];
  const fetchImpl = async (url, init) => {
    calls.push({ url: String(url), init });
    const path = new URL(String(url)).pathname.split("/").pop();
    const body = bodies[path] ?? {};
    return {
      ok: true,
      status: 200,
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

function ctxWith(fetchImpl, files = {}) {
  const written = {};
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    written,
    fs: {
      async readFile(path) {
        const key = Object.keys(files).find((each) => path.endsWith(each.replace(/\//g, path.includes("\\") ? "\\" : "/")));
        if (key) return files[key];
        const error = new Error(`ENOENT: ${path}`);
        error.code = "ENOENT";
        throw error;
      },
      async mkdir() {},
      async writeFile(path, body) {
        written[path] = body;
      },
    },
  };
}

const BODIES = {
  changes: CHANGES,
  context: CONTEXT,
  verify: { ok: true, errors: 0, warnings: 0, found: 0, truncated: false, findings: [] },
};

test("the command reads changes, the sites section only, and verify", async () => {
  const fetchImpl = routedFetch(BODIES);
  await report.run({}, [], ctxWith(fetchImpl));

  const urls = fetchImpl.calls.map((call) => call.url);
  assert.ok(urls.some((url) => url.includes("/agent/changes")));
  assert.ok(urls.some((url) => url.includes("/agent/verify")));
  // One field is needed from the pack; fetching all nine sections would be a token bill
  // nobody asked for (P3).
  const context = urls.find((url) => url.includes("/agent/context"));
  assert.equal(new URL(context).searchParams.get("include"), "sites");
});

test("--no-verify drops the lint call and the open-questions section", async () => {
  const fetchImpl = routedFetch(BODIES);
  const ctx = ctxWith(fetchImpl);
  await report.run({ noVerify: true }, [], ctx);

  assert.ok(!fetchImpl.calls.some((call) => call.url.includes("/agent/verify")));
  assert.doesNotMatch(ctx.stdout.text, /shio verify` passes/);
});

test("--out writes the markdown and prints a one-line summary instead", async () => {
  const ctx = ctxWith(routedFetch(BODIES));
  await report.run({ out: "HANDOFF.md" }, [], ctx);

  const [target] = Object.keys(ctx.written);
  assert.match(target, /HANDOFF\.md$/);
  assert.match(ctx.written[target], /# Content handoff/);
  assert.match(ctx.stdout.text, /wrote HANDOFF\.md — 2 change\(s\)/);
});

test("--mark records the cursor, and --since last reads it back", async () => {
  const ctx = ctxWith(routedFetch(BODIES));
  await report.run({ mark: true }, [], ctx);

  const [target] = Object.keys(ctx.written);
  assert.match(target, /report-state\.json$/);
  assert.equal(JSON.parse(ctx.written[target]).cursor, "2026-07-02T00:00:00Z|z");

  const fetchImpl = routedFetch(BODIES);
  await report.run(
    { since: "last" },
    [],
    ctxWith(fetchImpl, {
      "shio/.shio/report-state.json": JSON.stringify({ version: 1, cursor: "2026-06-01T00:00:00Z|q" }),
    }),
  );
  const changes = fetchImpl.calls.find((call) => call.url.includes("/agent/changes"));
  assert.equal(new URL(changes.url).searchParams.get("since"), "2026-06-01T00:00:00Z|q");
});

test("--mark refuses to advance past a range it could not read whole", async () => {
  // A cursor moved past changes nobody saw is a permanent hole in the review trail — the
  // same refusal `pull --content` makes about a truncated projection.
  const ctx = ctxWith(routedFetch({ ...BODIES, changes: { ...CHANGES, more: true } }));
  const result = await report.run({ mark: true }, [], ctx);

  assert.equal(result.exitCode, 1);
  assert.deepEqual(Object.keys(ctx.written), []);
  assert.match(ctx.stderr.text, /not recording the cursor/);
});

test("--since last with no recorded position says so and reports a window", async () => {
  const fetchImpl = routedFetch(BODIES);
  const ctx = ctxWith(fetchImpl);
  await report.run({ since: "last" }, [], ctx);

  assert.match(ctx.stderr.text, /no previous handoff recorded/);
  assert.equal(new URL(fetchImpl.calls[0].url).searchParams.get("since"), null);
});

test("a context pack that cannot be read costs the links, not the report", async () => {
  const fetchImpl = async (url) => {
    if (String(url).includes("/agent/context")) {
      return { ok: false, status: 403, headers: { get: () => null }, async text() { return "{}"; } };
    }
    const path = new URL(String(url)).pathname.split("/").pop();
    const body = BODIES[path] ?? {};
    return {
      ok: true,
      status: 200,
      headers: { get: () => null },
      async text() { return JSON.stringify(body); },
      async json() { return body; },
    };
  };
  const ctx = ctxWith(fetchImpl);
  await report.run({}, [], ctx);

  assert.match(ctx.stdout.text, /# Content handoff/);
  assert.match(ctx.stdout.text, /no preview: site Blog is not in the context pack/);
});

test("SH442: a slug in the URL field is not a preview link, and the report says which it is", () => {
  // The same truthiness test `resolveUrl` had, one file over and with a worse output: `!origin` is
  // false for `cafeteria`, so the report composed `cafeteria/blog/hello` and printed it as a link.
  // A broken link presented as a working one is worse than saying there is none — the reader clicks
  // it, and a handoff whose links do not open is a handoff nobody trusts twice.
  const built = buildReport({
    changes: {
      ...CHANGES,
      changes: [
        { action: "UPDATE", address: "post:Cafeteria/menu", site: "Cafeteria", what: "Edited" },
        { action: "UPDATE", address: "post:Blog/blog/hello", site: "Blog", what: "Edited" },
      ],
    },
    context: {
      sites: [
        { name: "Blog", url: "https://blog.example.com/" },
        { name: "Cafeteria", url: "cafeteria" },
      ],
    },
  });

  assert.match(
    built.markdown,
    /no preview: site Cafeteria has `cafeteria` in its URL field, which is not an absolute http\(s\) URL/,
  );
  assert.doesNotMatch(built.markdown, /\(cafeteria\/menu\)/, "and no link is composed from it");
  // The site that does have a front end is untouched: this is a predicate correction, not a policy.
  assert.match(built.markdown, /\[preview\]\(https:\/\/blog\.example\.com\/blog\/hello\)/);
});
