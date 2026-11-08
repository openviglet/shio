// The opening bands follow the hub's visual grammar (viglet.org): a surface that follows the theme,
// plain metadata, a solid product accent, and nothing moving behind the evidence.
//
// Source lints, like scroll-ownership.test.mjs beside them: these are properties of the components
// rather than of a rendered DOM, and the screenshots that judged them once cannot run in this suite.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const read = (path) => readFileSync(fileURLToPath(new URL(`../${path}`, import.meta.url)), "utf8");

/** What a permanent dark hero was made of. */
const DARK_HERO = [
  ["bg-[#150e0c]", "a hardcoded dark surface, which put a dark block on every light page"],
  ["grid-bg", "the grid behind the content"],
  ["animate-orb", "the animated orbs"],
  ["text-gradient", "gradient text, where the hub uses a solid accent"],
  ["rounded-full border border-white", "a metadata chip, where the hub uses plain text"],
];

test("SH1370: the home hero and the shared page hero carry none of the dark-hero decoration", () => {
  for (const file of ["src/components/sections/hero.tsx", "src/components/sections/page-hero.tsx"]) {
    const source = read(file);
    for (const [token, what] of DARK_HERO) {
      assert.ok(!source.includes(token), `SH1370: ${file} still has ${what} (${token})`);
    }
    assert.match(source, /bg-background/u, `SH1370: ${file} draws on the theme's surface`);
  }
});

test("SH1370: the eyebrow is plain accent text, not a gradient", () => {
  const brand = read("src/components/brand.tsx");
  const eyebrow = brand.slice(brand.indexOf("export function Eyebrow"), brand.indexOf("export function SectionHeader"));
  assert.ok(!/bg-gradient|bg-clip-text|text-transparent/u.test(eyebrow), "SH1370: the Eyebrow is a gradient again");
  assert.match(eyebrow, /text-primary/u);
});

test("SH1370: a button inside a page hero is not drawn for a dark band", () => {
  for (const page of ["claude-code", "compare", "console", "curator", "feature-page", "replicate", "showcase"]) {
    const source = read(`src/pages/${page}.tsx`);
    const hero = source.slice(source.indexOf("<PageHero"), source.indexOf("</PageHero>"));
    assert.ok(!hero.includes("text-white"), `SH1370: ${page}.tsx draws white text on the page hero's light surface`);
    assert.ok(!hero.includes("text-gradient"), `SH1370: ${page}.tsx keeps the pale gradient in its hero title`);
  }
});
