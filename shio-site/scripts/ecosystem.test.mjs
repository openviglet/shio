// @scans prose reason: the subject IS the claim a visitor reads, what the site says about Viglet
// Turing and about which agent hosts have setup, so the copy is read as copy.
//
// SH1373: the portfolio relationship, with its boundary. Viglet Shio can index published content
// into Viglet Turing, and that is optional, off by default, and only on publish; and the site names
// one agent host with a plugin (Claude Code) and offers every other MCP client the generic endpoint,
// rather than listing hosts it has no setup for.

import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const SITE = fileURLToPath(new URL("..", import.meta.url));
const read = (path) => readFileSync(join(SITE, path), "utf8");
const walk = (dir) => readdirSync(dir).flatMap((name) => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? walk(path) : [path];
});

test("SH1373: the delivery page explains Viglet Turing indexing with its boundary", () => {
  const delivery = read("src/lib/feature-content.ts");
  const block = delivery.slice(delivery.indexOf("Search in Viglet Turing, if you want it"));
  assert.ok(block.length > 0, "SH1373: the delivery page has no Viglet Turing block");
  for (const fact of ["off by default", "only when a person publishes", "a draft is never sent",
    "runs in your own database"]) {
    assert.ok(block.slice(0, 900).includes(fact), `SH1373: the Viglet Turing block no longer says "${fact}"`);
  }
  assert.match(delivery, /\/docs\/search-caching\/#viglet-turing-indexing/u,
    "SH1373: the delivery page links the docs section that documents indexing");
  assert.match(read("docs/src/content/docs/search-caching.md"), /^## Viglet Turing indexing$/mu,
    "SH1373: the anchor the delivery page links has moved");
});

test("SH1373: llms.txt states that indexing is optional and off by default", () => {
  assert.match(read("public/llms.txt"), /Viglet Turing[^\n]*optional, off by default[^\n]*never sends drafts/u);
});

test("SH1373: no visitor text names an agent host the product has no setup for", () => {
  const files = [...walk(join(SITE, "src")), ...walk(join(SITE, "docs/src/content/docs")), join(SITE, "public/llms.txt")]
    .filter((path) => /\.(tsx?|mdx?|txt)$/u.test(path) && !path.endsWith("COVERAGE.md"));
  const named = files.flatMap((path) => {
    const text = readFileSync(path, "utf8");
    return [...text.matchAll(/\b(Cursor|Codex|Windsurf|Copilot)\b(?! *[a-z])/gu)].map((m) => `${path}: ${m[1]}`);
  });
  assert.deepEqual(named, [],
    "SH1373: Claude Code has a plugin; any other MCP client uses the generic endpoint. Naming a "
      + "host implies setup for it that does not exist");
});
