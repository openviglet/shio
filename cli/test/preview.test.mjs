/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1095 — `shio preview <file>`: a content file's page, rendered before it is pushed.
 */
import assert from "node:assert/strict";
import * as fs from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import * as preview from "../src/commands/preview.mjs";

async function tree() {
  const cwd = await fs.mkdtemp(join(tmpdir(), "shio-preview-test-"));
  const dir = join(cwd, "shio", "content", "Acme");
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(join(dir, "about.md"), [
    "---",
    "type: Page",
    "body: text",
    "fields:",
    "  title: About us",
    "files:",
    "  css: about.css",
    "---",
    "We make things.",
    "",
  ].join("\n"));
  await fs.writeFile(join(dir, "about.css"), "h1 { color: red }\n");
  return cwd;
}

function ctxAt(cwd, respond) {
  const out = { text: "" };
  const calls = [];
  return {
    env: { SHIO_URL: "http://shio.test", SHIO_USER: "admin", SHIO_PASSWORD: "secret" },
    cwd,
    fs,
    calls,
    stdout: { write: (text) => { out.text += text; } },
    stderr: { write: () => {} },
    text: () => out.text,
    fetch: async (url, init) => {
      calls.push({ url: String(url), body: JSON.parse(init.body) });
      const { status, payload } = respond();
      return {
        ok: status < 400,
        status,
        headers: { get: () => null },
        async text() { return JSON.stringify(payload); },
        async json() { return payload; },
      };
    },
  };
}

test("SH1095: preview renders the file's fields, sidecars included, at the page it names", async () => {
  const cwd = await tree();
  const ctx = ctxAt(cwd, () => ({
    status: 200,
    payload: {
      digest: { digest: "abc123", words: 3, headings: [{ level: 1, text: "About us" }], images: [] },
      html: "<h1>About us</h1>",
    },
  }));
  const out = join(cwd, "preview.html");

  const result = await preview.run({ out }, ["shio/content/Acme/about.md"], ctx);

  assert.equal(result.address, "post:Acme/about",
    "SH1095: the file names its own page, so the common case needs no --address");
  assert.match(ctx.calls[0].url, /\/api\/v2\/agent\/render\?format=html$/);
  assert.equal(ctx.calls[0].body.data.title, "About us");
  assert.equal(ctx.calls[0].body.data.text.trim(), "We make things.");
  assert.equal(ctx.calls[0].body.data.css, "h1 { color: red }\n",
    "SH1095: a sidecar is a field of the page, and a preview without it renders a different page");
  assert.equal(await fs.readFile(out, "utf8"), "<h1>About us</h1>");
  assert.match(ctx.text(), /post:Acme\/about \(unsaved\) digest abc123/);
  assert.match(ctx.text(), /h1 About us/);
  assert.match(ctx.text(), /markup: .*preview\.html/);
});

test("SH1095: a page the instance does not have is refused with what to do instead", async () => {
  const cwd = await tree();
  const ctx = ctxAt(cwd, () => ({ status: 404, payload: { message: "not found" } }));

  await assert.rejects(
    preview.run({ out: join(cwd, "p.html") }, ["shio/content/Acme/about.md"], ctx),
    /post:Acme\/about is not on this instance yet.*push the file once, or pass --address/s,
  );
});
