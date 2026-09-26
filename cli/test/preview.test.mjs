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

/**
 * SH1143 — a preview of an image that is not pushed yet shows the image.
 *
 * <p>A content file spells a `File` field as a tree path; the renderer reads that field as a **File
 * post id** and emits `/file_source/<id>/<name>`. `push` maps path to id as it uploads, and a
 * preview stores nothing — so an image just dropped under `shio/assets/**` had no id, the lookup
 * found nothing, and the page showed a broken image where it will show a picture. The author
 * learned the wrong thing about the page, which is the one thing a preview must not do.</p>
 *
 * <p>At the CLI boundary, because that is where the decision is: what goes in the request body.</p>
 */
async function treeWithImage({ pushed = false } = {}) {
  const cwd = await fs.mkdtemp(join(tmpdir(), "shio-preview-asset-"));
  const content = join(cwd, "shio", "content", "Acme");
  const assets = join(cwd, "shio", "assets", "Acme", "img");
  await fs.mkdir(content, { recursive: true });
  await fs.mkdir(assets, { recursive: true });
  await fs.writeFile(join(content, "about.md"), [
    "---", "type: Page", "body: text", "fields:",
    "  title: About us",
    `  hero: ${pushed ? "1f0c4b6e-0000-4000-8000-000000000001" : "Acme/img/hero.png"}`,
    "---", "We make things.", "",
  ].join("\n"));
  // A one-pixel PNG: real bytes, so the base64 below is a real encoding and not a fixture string.
  await fs.writeFile(join(assets, "hero.png"), Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    "base64"));
  return cwd;
}

/** A context whose first answer is the post-type def and whose second is the render. */
function ctxWithType(cwd, def) {
  const ctx = ctxAt(cwd, () => ({ status: 200, payload: { digest: { digest: "d", words: 3 } } }));
  const inner = ctx.fetch;
  ctx.fetch = async (url, init) => {
    if (String(url).includes("/post-type/")) {
      return {
        ok: true, status: 200, headers: { get: () => null },
        async text() { return JSON.stringify(def); },
        async json() { return def; },
      };
    }
    return inner(url, init);
  };
  return ctx;
}

// `widget`, not `type`: that is the key `fileFields` reads and the one a def actually carries.
const PAGE_WITH_HERO = {
  name: "Page",
  fields: [
    { name: "title", widget: "Text" },
    { name: "hero", widget: "File" },
  ],
};

test("SH1143: a File field the instance has no id for is sent as its bytes", async () => {
  const cwd = await treeWithImage();
  const ctx = ctxWithType(cwd, PAGE_WITH_HERO);

  const result = await preview.run({}, ["shio/content/Acme/about.md"], ctx);

  const render = ctx.calls.find((call) => call.url.includes("/render"));
  assert.ok(render, "SH1143: no render call was made at all");
  assert.match(render.body.data.hero, /^data:image\/png;base64,iVBORw0KGgo/,
    "SH1143: the field went as the tree path `Acme/img/hero.png`, which the renderer reads as a "
      + "File post id. `findById` finds nothing, `{{file_url}}` renders the empty string, and the "
      + "author is shown a broken image where the pushed page will show a picture — the one thing "
      + "a preview must not do. Nothing is stored: the bytes ride along for this request only.");
  assert.deepEqual(result.inlined, ["Acme/img/hero.png"],
    "SH1143: and the run says which images it carried, because an inlined one renders without a "
      + "srcset and is the one way this page differs from the pushed one");
  assert.match(ctx.text(), /1 image\(s\) rendered from the tree: Acme\/img\/hero\.png/)
  await fs.rm(cwd, { recursive: true, force: true });
});

test("SH1143: an asset already on the instance is sent as its id, not its bytes", async () => {
  // The page the author will get, srcset and all. Inlining an uploaded asset would render a
  // DIFFERENT page from the one a push produces, which is the same defect pointing the other way.
  const cwd = await treeWithImage({ pushed: true });
  const ctx = ctxWithType(cwd, PAGE_WITH_HERO);

  await preview.run({}, ["shio/content/Acme/about.md"], ctx);

  const render = ctx.calls.find((call) => call.url.includes("/render"));
  assert.equal(render.body.data.hero, "1f0c4b6e-0000-4000-8000-000000000001",
    "SH1143: a value that is not a tree path is an id already and is left exactly as it is — "
      + "SH145's read side leaves those alone too");
  await fs.rm(cwd, { recursive: true, force: true });
});

test("SH1143: a Text field that happens to name a file is not inlined", async () => {
  // The model decides, never the shape of the value: `content-assets` refuses to guess for the
  // same reason, and a Text field holding a path that exists in the tree is exactly the case.
  const cwd = await treeWithImage();
  const ctx = ctxWithType(cwd, {
    name: "Page",
    fields: [{ name: "title", widget: "Text" }, { name: "hero", widget: "Text" }],
  });

  await preview.run({}, ["shio/content/Acme/about.md"], ctx);

  const render = ctx.calls.find((call) => call.url.includes("/render"));
  assert.equal(render.body.data.hero, "Acme/img/hero.png",
    "SH1143: `hero` is a Text field on this type, so its value is a string the page prints — "
      + "inlining it because a file of that name exists would rewrite content");
  await fs.rm(cwd, { recursive: true, force: true });
});
