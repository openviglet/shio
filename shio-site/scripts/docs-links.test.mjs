// @scans prose reason: the subject is the documentation's Markdown, its links and its headings,
// read as text (SH740).
//
// SH1262 — every link inside /shio/docs reaches a page, and every #anchor a heading on it.
//
// The pages came over from Docusaurus, which resolved `./page.md#anchor` itself; Starlight does
// not, so the importer rewrote each one to an absolute `/shio/docs/<slug>/#anchor` (the site is
// published under /shio/, on openviglet.github.io). A rewrite nobody
// checks is a 404 nobody sees until a reader clicks, so this reads the sources, needs no build,
// and runs in the site's own suite.

import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, sep } from "node:path";
import { test } from "node:test";

const CONTENT = join(process.cwd(), "docs", "src", "content", "docs");

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}

/** github-slugger's rule, which Astro uses for heading ids: lowercase, punctuation out, spaces to -. */
function slugify(heading) {
  return heading
    .replace(/`([^`]*)`/g, "$1")
    .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
    .replace(/[*_]/g, "")
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}\s-]/gu, "")
    .replace(/\s/g, "-");
}

/** Prose only: a link inside fenced code is an example, not a link. */
const prose = (text) => text.split(/^```[\s\S]*?^```\s*$/m).join("\n");

/** The coverage matrix sits beside the pages and is not one: the collection leaves it out. */
const MATRIX = join(CONTENT, "COVERAGE.md");

const pages = new Map();
for (const file of walk(CONTENT).filter((path) => /\.mdx?$/.test(path) && path !== MATRIX)) {
  const slug = relative(CONTENT, file).split(sep).join("/").replace(/\.mdx?$/, "").replace(/(^|\/)index$/, "");
  const text = readFileSync(file, "utf8");
  const anchors = new Set();
  const seen = new Map();
  for (const [, heading] of prose(text).matchAll(/^#{2,6}\s+(.+?)\s*$/gm)) {
    const base = slugify(heading);
    const count = seen.get(base) ?? 0;
    anchors.add(count ? `${base}-${count}` : base);
    seen.set(base, count + 1);
  }
  for (const [, id] of text.matchAll(/<span id="([\w-]+)"><\/span>/g)) anchors.add(id);
  pages.set(slug, { file, text, anchors });
}

test("SH1262: the documentation area has every page the vigletdocs tree had", () => {
  assert.ok(pages.size >= 38, `only ${pages.size} pages under ${CONTENT}`);
  for (const hub of ["agent-builds-it", "curator-approves-it", "cda-delivers-it", "run-it", ""]) {
    assert.ok(pages.has(hub), `the ${hub || "landing"} page is missing`);
  }
});

test("SH1262: every link inside /docs reaches a page, and its anchor a heading on it", () => {
  const broken = [];
  for (const [slug, { text }] of pages) {
    const links = [
      ...prose(text).matchAll(/\]\((\/shio\/docs\/[^)\s]*)\)/g),
      ...text.matchAll(/href="(\/shio\/docs\/[^"]*)"/g),
    ].map(([, href]) => href);
    for (const href of links) {
      const [path, hash] = href.split("#");
      const target = path.replace(/^\/shio\/docs\/?/, "").replace(/\/$/, "");
      const page = pages.get(target);
      if (!page) broken.push(`${slug || "index"}: ${href} (no such page)`);
      else if (hash && !page.anchors.has(hash)) broken.push(`${slug || "index"}: ${href} (no #${hash} there)`);
    }
    for (const [, href] of prose(text).matchAll(/\]\((\.{1,2}\/[^)\s]+\.mdx?(?:#[^)\s]*)?)\)/g)) {
      broken.push(`${slug || "index"}: ${href} (a Docusaurus file link Starlight does not resolve)`);
    }
    for (const [, href] of prose(text).matchAll(/\]\((\/shio\/(?!docs\/)[^)\s]*)\)/g)) {
      broken.push(`${slug || "index"}: ${href} (the Docusaurus /shio/ path; the pages are under /shio/docs/)`);
    }
    // A link missing the base reaches the host's root, which is not this site.
    for (const [, href] of [...prose(text).matchAll(/\]\((\/docs\/[^)\s]*)\)/g), ...text.matchAll(/href="(\/docs\/[^"]*)"/g)]) {
      broken.push(`${slug || "index"}: ${href} (missing the /shio base; write /shio${href})`);
    }
  }
  assert.deepEqual(broken, [], `${broken.length} broken link(s) in the documentation`);
});

test("SH1262: every page and #anchor COVERAGE.md cites exists", () => {
  // The matrix's verdicts are page + anchor; a ✅ pointing at a heading Starlight spells differently
  // is a verdict nothing backs. Its links stay file-relative (`./cli.md#…`) so they also read on
  // GitHub, which is why they are resolved here rather than rewritten.
  const text = readFileSync(MATRIX, "utf8");
  const broken = [];
  for (const [, target, hash] of text.matchAll(/\]\(\.\/([^)\s#]+?)\.mdx?(?:#([^)\s]*))?\)/g)) {
    const page = pages.get(target.replace(/(^|\/)index$/, ""));
    if (!page) broken.push(`${target} (no such page)`);
    else if (hash && !page.anchors.has(hash)) broken.push(`${target}#${hash} (no such anchor)`);
  }
  assert.ok(text.includes("](./"), "COVERAGE.md cites no page, so this check read nothing");
  assert.deepEqual([...new Set(broken)], [], `${broken.length} broken citation(s) in COVERAGE.md`);
});
