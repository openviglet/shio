// @scans code
//
// SH1372: the home keeps the sections a visitor decides with, and the depth lives on the page that
// is about it. The home was 36,097 pixels tall at 390 wide with twenty sections (measured
// 2026-11-09); after the move it was 20,809 with eleven. The height is evidence, not a target, so
// this asserts the structure that produced it: which sections the home renders, that every moved
// section still has an anchor on the home, and that the agent protocol renders in full somewhere.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { code } from "../../cli/test/support/source-text.mjs";

const read = (path) => code(readFileSync(fileURLToPath(new URL(`../${path}`, import.meta.url)), "utf8"));
const rendered = (source) => [...source.matchAll(/^\s*<([A-Z]\w+) \/>$/gmu)].map((m) => m[1]);

const MOVED = ["laws", "one-call", "surface", "errors", "verify", "files", "blueprints", "replicate", "delivery", "compare"];

test("SH1372: the home renders the deciding sections and an index, not the depth in full", () => {
  const home = rendered(read("src/pages/landing.tsx"));
  assert.deepEqual(home, ["SiteNav", "Hero", "Paths", "UseCases", "Showcase", "Roles", "Pillars", "CuratorHalf",
    "DepthIndex", "SelfHost", "Resources", "CallToAction", "SiteFooter"]);
});

test("SH1372: every moved section keeps its anchor on the home, as a card", () => {
  const ids = [...read("src/lib/site-content.ts").matchAll(/^\s+id: "([\w-]+)",$/gmu)].map((m) => m[1]);
  for (const id of MOVED) assert.ok(ids.includes(id), `SH1372: /#${id} would no longer land anywhere on the home`);
});

test("SH1372: the agent protocol renders in full on /claude-code/", () => {
  const page = rendered(read("src/pages/claude-code.tsx"));
  for (const section of ["Laws", "OneCall", "Surface", "Errors", "Verify"]) {
    assert.ok(page.includes(section), `SH1372: ${section} is on no page now`);
  }
});

test("SH1372: no link still sends a reader to a home anchor whose section moved away", () => {
  for (const file of ["src/components/sections/site-nav.tsx", "src/components/sections/site-footer.tsx",
    "src/pages/feature-page.tsx"]) {
    assert.doesNotMatch(read(file), /withBase\("\/#(laws|one-call|surface|errors|verify|blueprints)"\)/u,
      `SH1372: ${file} links a moved section at its old home anchor`);
  }
});
