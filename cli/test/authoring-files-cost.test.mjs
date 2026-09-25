/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1141 — what one authoring task costs an agent through the content files, against the API.
 *
 * WHY THIS FILE EXISTS
 * P4 says files beat APIs for authoring, and content-files.mjs argues it in its header: an HTTP
 * edit is two round trips and a full JSON body in and out, a file edit is one Edit. Every P4 test
 * asserted the format or its determinism, and ShAgentTaskBenchmarkTest runs both of its paths over
 * HTTP, so the claim the projection exists for had never been counted. This counts it, on one
 * canonical task, and prints both sides on every run.
 *
 * THE TASK
 * A site of POSTS posts, of which HITS carry a product name in their body; rename it everywhere.
 * The same fixture is served to both paths by the scripted instance the content commands are
 * tested against.
 *
 * WHAT IS COUNTED, AND WHAT IS NOT
 * Only what reaches the agent's context: what it sends (tool inputs, request bodies) and what
 * comes back (tool results, response bodies), at the conventional 4 chars per token. The HTTP the
 * CLI makes on the agent's behalf during pull and push is not in the context and is not counted —
 * that is the whole of the file path's argument, so it is the thing the measurement must honour.
 * The two directions are kept apart because output tokens are the dearer kind.
 *
 * The file path pays: the pull command and its stdout; a Grep of the tree; a Read of every file
 * it will change, because the harness refuses an Edit on a file the session has not read; one
 * Edit per file; the push command and its stdout.
 *
 * The API path pays: a find by text (addresses only, the cheapest discovery the surface has), one
 * batched read of the matches, and one batch writing each post's new body.
 *
 * WHERE IT IS GENEROUS, AND TO WHOM
 * To the API: find and read answer in the fake's lean shapes, the write sends the body field
 * alone, and its response is one short row per op; the real endpoints carry more. To the files:
 * paths are the fake's short /repo root, where a real checkout's absolute paths are longer. The
 * Read is counted whole, which a session that reads with an offset could beat.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import * as contentPull from "../src/commands/content-pull.mjs";
import * as contentPush from "../src/commands/content-push.mjs";
import { norm } from "./support/paths.mjs";
import { budgets, need, tokens } from "./support/token-budgets.mjs";

const POSTS = 30;
const HITS = 10;
const OLD = "Acme Widgets";
const NEW = "Acme Tools";
const SITE = "Blog";
const CONTENT_ROOT = "/repo/shio/content";

const DEFS = [
  {
    name: "Article",
    fields: [
      { name: "headline", widget: "Text", ordinal: 1 },
      { name: "summary", widget: "Text", ordinal: 2 },
      { name: "body", widget: "HTML Editor", ordinal: 3 },
    ],
  },
];

/** A post body of the length a real article has: six paragraphs, one of them naming the product. */
function bodyFor(index, named) {
  const paragraphs = Array.from(
    { length: 6 },
    (_, p) =>
      `<p>Paragraph ${p + 1} of article ${index} describes how the team plans, ships and ` +
      `measures its work, with enough detail that a reader can follow the reasoning.</p>`
  );
  if (named) paragraphs[2] = `<p>Article ${index} explains why ${OLD} changed how the team ships.</p>`;
  return paragraphs.join("\n");
}

function fixture() {
  const remote = {};
  for (let index = 1; index <= POSTS; index += 1) {
    const address = `post:${SITE}/blog/article-${String(index).padStart(2, "0")}`;
    remote[address] = {
      address,
      type: "Article",
      state: "DRAFT",
      folder: "/blog",
      data: {
        headline: `Article ${index}`,
        summary: `A short summary of article ${index}.`,
        body: bodyFor(index, index <= HITS),
      },
    };
  }
  return remote;
}

function json(body) {
  const text = JSON.stringify(body);
  return {
    ok: true,
    status: 200,
    headers: { get: () => null },
    text: async () => text,
    json: async () => body,
  };
}

/** The endpoints pull and push call, and the one text search the API path uses. */
function server(remote) {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    const target = String(url);
    const body = typeof init.body === "string" ? init.body : null;
    calls.push({ url: target, body });
    const params = new URL(target).searchParams;
    if (target.includes("/agent/find")) {
      const text = params.get("text");
      const hits = Object.keys(remote)
        .sort()
        .filter((address) => !text || JSON.stringify(remote[address].data).includes(text));
      return json({ count: hits.length, truncated: false, results: hits.map((address) => ({ address, type: "Article" })) });
    }
    if (target.includes("/agent/read")) {
      return json(params.getAll("address").map((address) => remote[address]).filter(Boolean));
    }
    if (target.includes("/api/v2/post-type")) return json(DEFS);
    if (target.includes("/agent/context")) return json({ sitemap: [], budget: { estTokens: 0, limitTokens: 0, truncated: false } });
    if (target.includes("/api/v2/staticfile")) return json([]);
    if (target.includes("/agent/memory")) return json({ notes: [] });
    if (target.includes("/agent/changes")) {
      return json({ found: 0, window: true, cursor: "2026-09-25T10:00:00Z|x", more: false, changes: [], notes: [] });
    }
    if (target.includes("/agent/plan") || target.includes("/agent/apply")) {
      const ops = body ? JSON.parse(body).ops?.length ?? 0 : 0;
      return json({ dryRun: target.includes("plan"), ops, changed: ops, results: [] });
    }
    return json({});
  };
  fetchImpl.calls = calls;
  return fetchImpl;
}

function fakeFs() {
  const files = new Map();
  const missing = (key) => Object.assign(new Error(`ENOENT: ${key}`), { code: "ENOENT" });
  return {
    files,
    async readFile(path) {
      const key = norm(path);
      if (!files.has(key)) throw missing(key);
      return files.get(key);
    },
    async writeFile(path, body) {
      files.set(norm(path), body);
    },
    async appendFile(path, body) {
      files.set(norm(path), (files.get(norm(path)) ?? "") + body);
    },
    async mkdir() {},
    async unlink(path) {
      if (!files.delete(norm(path))) throw missing(norm(path));
    },
    async readdir(path) {
      const prefix = `${norm(path)}/`;
      const names = new Set();
      for (const key of files.keys()) {
        if (!key.startsWith(prefix)) continue;
        const rest = key.slice(prefix.length);
        const cut = rest.indexOf("/");
        names.add(cut < 0 ? rest : `${rest.slice(0, cut)}/`);
      }
      if (!names.size) throw missing(prefix);
      return [...names].sort().map((name) => ({ name: name.replace(/\/$/, ""), isDirectory: () => name.endsWith("/") }));
    },
  };
}

function ctxWith(fetchImpl) {
  const sink = () => {
    const out = { text: "" };
    out.write = (chunk) => {
      out.text += chunk;
    };
    return out;
  };
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    fetch: fetchImpl,
    fs: fakeFs(),
  };
}

/** A tally of what the agent sent and what it was sent back, in tokens. */
function ledger() {
  const rows = [];
  return {
    rows,
    step(label, sent, received) {
      rows.push({ label, sent: tokens(sent), received: tokens(received) });
    },
    get sent() {
      return rows.reduce((sum, row) => sum + row.sent, 0);
    },
    get received() {
      return rows.reduce((sum, row) => sum + row.received, 0);
    },
    get total() {
      return this.sent + this.received;
    },
  };
}

/** The Read tool's answer: each line behind its number and a tab. */
const readResult = (text) =>
  text
    .split("\n")
    .map((line, index) => `${index + 1}\t${line}`)
    .join("\n");

async function throughFiles(remote) {
  const cost = ledger();
  const ctx = ctxWith(server(remote));

  await contentPull.run({}, [], ctx);
  cost.step("pull", JSON.stringify({ command: "shio pull --content" }), ctx.stdout.text + ctx.stderr.text);

  const tree = [...ctx.fs.files.keys()].filter((path) => path.startsWith(`${CONTENT_ROOT}/`)).sort();
  const matches = [];
  for (const path of tree) {
    ctx.fs.files
      .get(path)
      .split("\n")
      .forEach((line, index) => {
        if (line.includes(OLD)) matches.push({ path, line: index + 1, text: line });
      });
  }
  cost.step(
    "grep",
    JSON.stringify({ pattern: OLD, path: "shio/content", output_mode: "content", "-n": true }),
    matches.map((match) => `${match.path}:${match.line}:${match.text}`).join("\n")
  );

  for (const path of [...new Set(matches.map((match) => match.path))]) {
    const text = ctx.fs.files.get(path);
    cost.step("read", JSON.stringify({ file_path: path }), readResult(text));
    const oldLine = text.split("\n").find((line) => line.includes(OLD));
    const newLine = oldLine.replaceAll(OLD, NEW);
    cost.step(
      "edit",
      JSON.stringify({ file_path: path, old_string: oldLine, new_string: newLine }),
      `The file ${path} has been updated successfully.`
    );
    ctx.fs.files.set(path, text.replace(oldLine, newLine));
  }

  ctx.stdout.text = "";
  ctx.stderr.text = "";
  await contentPush.run({}, [], ctx);
  cost.step("push", JSON.stringify({ command: "shio push --content" }), ctx.stdout.text + ctx.stderr.text);

  const applied = ctx.fetch.calls.filter((call) => call.url.includes("/agent/apply"));
  return { cost, matches, applied };
}

async function throughApi(remote) {
  const cost = ledger();
  const call = async (fetchImpl, url, body) => {
    const response = await fetchImpl(url, body ? { method: "POST", body } : {});
    const text = await response.text();
    cost.step(new URL(url).pathname, url + (body ?? ""), text);
    return JSON.parse(text);
  };
  const fetchImpl = server(remote);
  const base = "http://shio.test/api/v2/agent";

  const found = await call(fetchImpl, `${base}/find?site=${SITE}&text=${encodeURIComponent(OLD)}`);
  const addresses = found.results.map((row) => row.address);
  const query = addresses.map((address) => `address=${encodeURIComponent(address)}`).join("&");
  const docs = await call(fetchImpl, `${base}/read?${query}`);

  const ops = docs.map((doc) => ({ op: "post.upsert", address: doc.address, data: { body: doc.data.body.replaceAll(OLD, NEW) } }));
  const written = JSON.stringify({ ops });
  const response = JSON.stringify({
    dryRun: false,
    results: ops.map((op) => ({ op: op.op, address: op.address, change: "updated" })),
  });
  cost.step("/api/v2/agent/batch", `${base}/batch${written}`, response);
  return { cost, addresses, ops };
}

function print(label, cost) {
  const lines = cost.rows.map((row) => `    ${row.label.padEnd(22)} sent ${String(row.sent).padStart(6)}  received ${String(row.received).padStart(6)}`);
  console.log(`  ${label}: ${cost.total} tokens (sent ${cost.sent}, received ${cost.received}) over ${cost.rows.length} steps\n${lines.join("\n")}`);
}

test("SH1141: both paths do the same task on the same fixture", async () => {
  const remote = fixture();
  const files = await throughFiles(remote);
  const api = await throughApi(remote);

  assert.equal(new Set(files.matches.map((match) => match.path)).size, HITS, "the Grep finds every post that names it");
  assert.equal(api.addresses.length, HITS, "the text search finds the same posts");
  assert.equal(files.applied.length, 1, "the push applies once");
  const pushed = files.applied[0].body;
  assert.equal(pushed.split(NEW).length - 1, HITS, "the push carries the new name once per post");
  assert.ok(!pushed.includes(OLD), "and the old one nowhere");
  assert.equal(api.ops.length, HITS);
});

test("SH1141: authoring through the files against the API, in tokens, held by token-budgets.properties", async () => {
  const remote = fixture();
  const files = (await throughFiles(remote)).cost;
  const api = (await throughApi(remote)).cost;

  console.log(`SH1141 authoring cost, ${HITS} of ${POSTS} posts renamed:`);
  print("files", files);
  print("api", api);
  const ratio = api.total / files.total;
  const sentRatio = api.sent / files.sent;
  console.log(`  api / files: ${ratio.toFixed(2)}x in all, ${sentRatio.toFixed(2)}x of what the agent wrote`);

  const props = budgets();
  assert.ok(files.total <= need(props, "authoring.files.tokens"), `files path ${files.total} over its ceiling`);
  assert.ok(api.total <= need(props, "authoring.api.tokens"), `api path ${api.total} over its ceiling`);
  // Ratios are integer tenths, as every ratio in the file is (25 = 2.5x).
  assert.ok(
    Math.floor(ratio * 10) >= need(props, "authoring.min-token-ratio"),
    `api/files fell to ${ratio.toFixed(2)}x: the files path lost what it was measured to save`
  );
  assert.ok(
    Math.floor(sentRatio * 10) >= need(props, "authoring.min-sent-ratio"),
    `api/files for what the agent wrote fell to ${sentRatio.toFixed(2)}x`
  );
});
