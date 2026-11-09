// @scans code
//
// SH1371: every surface that links out takes its destinations from src/lib/destinations.ts. The
// marketing site and the docs area used to answer "Get started" and "Changelog" differently, and a
// visitor who crossed from one to the other met the same word leading somewhere else.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { code } from "../../cli/test/support/source-text.mjs";
import { DESTINATIONS, hrefOf } from "../src/lib/destinations.ts";

const read = (path) => code(readFileSync(fileURLToPath(new URL(`../${path}`, import.meta.url)), "utf8"));
const raw = (path) => readFileSync(fileURLToPath(new URL(`../${path}`, import.meta.url)), "utf8");
const ORIGIN = "https://shio.viglet.org";

test("SH1371: Get started is the setup path on both the marketing header and the docs header", () => {
  const nav = read("src/components/sections/site-nav.tsx");
  const getStarted = [...nav.matchAll(/<a href=\{LINKS\.(\w+)\}[^>]*>\s*Get started/gu)].map((m) => m[1]);
  assert.deepEqual(getStarted, ["install", "install"], "SH1371: both Get started buttons (desktop and menu)");
  assert.match(read("docs/src/components/SocialIcons.astro"), /"Get started", href: hrefOf\("install", site\)/u);
  assert.equal(hrefOf("install"), "/docs/installation-guide/");
});

test("SH1371: release notes are the hub's on both footers, and the roadmap says it is for development", () => {
  for (const file of ["src/components/sections/site-footer.tsx", "docs/src/components/Footer.astro"]) {
    const source = read(file);
    assert.ok(!/label: "Changelog"/u.test(source), `SH1371: ${file} still has a "Changelog" row`);
    assert.match(source, /label: "Release notes"/u, `SH1371: ${file}`);
    assert.match(source, /label: "Development roadmap"/u, `SH1371: ${file}`);
    assert.match(source, /label: "Installation"/u, `SH1371: ${file}`);
    assert.match(source, /label: "All Viglet products"/u, `SH1371: ${file} keeps a way back to the portfolio`);
  }
  assert.ok(!read("docs/src/components/Footer.astro").includes("https://"),
    "SH1371: the docs footer types a URL instead of taking it from the map");
  assert.equal(DESTINATIONS.releaseNotes.url, "https://www.viglet.org/shio/release-notes/");
});

test("SH1371: llms.txt names each destination at the address the map gives it", () => {
  const llms = raw("public/llms.txt");
  for (const name of ["docs", "install", "download", "releaseNotes", "releaseFeed", "source", "roadmap"]) {
    const href = hrefOf(name);
    const expected = href.startsWith("http") ? href : ORIGIN + href;
    assert.ok(llms.includes(expected), `SH1371: llms.txt does not link ${name} at ${expected}`);
  }
});
