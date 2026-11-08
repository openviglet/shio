// @scans prose reason: the subject IS the copy, which name the site gives the product in a string a
// visitor reads; a code identifier (`ShioAdminClient`, `@viglet/shio`) is not a mention and the
// pattern below cannot match one, and comments are stripped because a sentence to a maintainer is
// not visitor text.
//
// The hub's naming decision (viglet.org docs/STRATEGY.md section I.7, 2026-11-07): every product
// mention a visitor reads is the full name, even after the first. A bare name is not an alternative
// display name. Historical release records and generated packages are the declared exceptions,
// each with its reason, so an absence here is a checked absence rather than an unread file.

import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const SITE = fileURLToPath(new URL("..", import.meta.url));

/** A bare product name, not preceded by `Viglet `. Case-sensitive, so `shio push` is a command. */
const BARE = /(?<!Viglet )\b(Shio|Turing|Dumont)\b/gu;

/** Files outside `src/` a visitor or a crawler reads. */
const OUTPUTS = ["public/llms.txt", "scripts/prerender.mjs", "docs/src/components/Head.astro"];

/** Visitor-reachable files that may keep a bare name, and why. */
const EXCEPTIONS = {
  "public/changelog.json": "a historical release record (\"Product name was changed to Shio\"), kept as written",
};

/** The docs pages a visitor reads; COVERAGE.md is the contributors' matrix and is not published. */
const DOCS = "docs/src/content/docs";

/**
 * Names in the docs that are labels the console shows, not mentions: the two built-in providers.
 * Rewriting them would make the page disagree with the screen it describes.
 */
const DOCS_LABELS = /\*\*Shio (Native|Package)\*\*/gu;

/** Prose only: fenced code, inline code, link targets and HTML tags are technical and stay. */
function docsProse(text) {
  let fence = false;
  return text.split(/\r?\n/u).map((line) => {
    if (/^\s*(```|~~~)/u.test(line)) {
      fence = !fence;
      return "";
    }
    return fence ? "" : line.replace(/`[^`]*`|\]\([^)]*\)|<[^>]+>/gu, "").replace(DOCS_LABELS, "");
  });
}

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}

/** Block and line comments out; a `//` inside a URL (`https://`) stays. */
function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//gu, "").replace(/(^|[^:])\/\/.*$/gmu, "$1");
}

function bareMentions(file) {
  const text = stripComments(readFileSync(join(SITE, file), "utf8"));
  return text.split("\n").flatMap((line, index) =>
    line.match(BARE) ? [`${file}:${index + 1}: ${line.trim().slice(0, 120)}`] : []);
}

test("SH1369: the site's visitor text names the product Viglet Shio every time", () => {
  const sources = walk(join(SITE, "src"))
    .filter((path) => /\.(ts|tsx)$/u.test(path))
    .map((path) => relative(SITE, path).replaceAll("\\", "/"));
  const found = [...sources, ...OUTPUTS].flatMap(bareMentions);
  assert.deepEqual(found, [],
    "SH1369: a bare Shio in a string a visitor reads; the hub decided full names everywhere, so "
      + "write Viglet Shio, and change the layout rather than abbreviate when it does not fit");
});

test("SH1369: the docs pages name Viglet Shio, Viglet Turing and Viglet Dumont in full", () => {
  const BARE_ANY = /(?<!Viglet )(?<![\w/.-])(Shio|Turing|Dumont)\b(?![\w-]*\.(?:java|ts|mjs|md))/u;
  const found = walk(join(SITE, DOCS))
    .filter((path) => path.endsWith(".md") && !path.endsWith("COVERAGE.md"))
    .flatMap((path) => docsProse(readFileSync(path, "utf8")).flatMap((line, index) =>
      BARE_ANY.test(line) ? [`${relative(SITE, path).replaceAll("\\", "/")}:${index + 1}: ${line.trim().slice(0, 120)}`] : []));
  assert.deepEqual(found, [],
    "SH1369: a bare product name in a docs page's prose; commands, properties and code stay as "
      + "they are, but a sentence names the product in full");
});

test("SH1369: the breadcrumbs crawlers read name the product in full", () => {
  for (const file of ["scripts/prerender.mjs", "docs/src/components/Head.astro"]) {
    assert.match(readFileSync(join(SITE, file), "utf8"), /name: "Viglet Shio", item:/u, `SH1369: ${file}`);
  }
});

test("SH1369: the phone header keeps the wordmark rather than dropping to the mark alone", () => {
  const nav = readFileSync(join(SITE, "src/components/sections/site-nav.tsx"), "utf8");
  assert.doesNotMatch(nav, /showWord=\{false\}/u, "SH1369: the mobile header rendered the mark without the name");
});

test("SH1369: every exception is a real file and says why", () => {
  for (const [file, why] of Object.entries(EXCEPTIONS)) {
    assert.ok(statSync(join(SITE, file)).isFile(), `${file} is listed as an exception and does not exist`);
    assert.ok(why.length > 20, `${file} needs a reason a reader can disagree with`);
  }
});
