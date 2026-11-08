#!/usr/bin/env node
/*
 * The half of "are the docs in sync with the code" that `pnpm conformance:docs` does not ask.
 *
 * The docs suites read the pages and check each name against the product (docs → code): a
 * renamed verb, an unmapped route, a property nothing binds. They are blind in the other
 * direction: a verb, op, resource or property the product HAS and no page mentions passes every
 * one of them. This report walks that direction, plus two structural checks a page-level reader
 * cannot make: a page missing from the sidebar, and a hand-typed `/docs/...` link or anchor that
 * no longer resolves.
 *
 * It is a REPORT, not a gate: the exit code is 0 unless --strict is given. Some gaps are
 * deliberate (an internal limit, a feature key documented under a prose name), and deciding which
 * is the judgment the skill asks for. Read-only; needs no instance.
 *
 * Usage (from the repo root):
 *   node .claude/skills/shio-docs-sync/docs-sync-report.mjs            # human-readable
 *   node .claude/skills/shio-docs-sync/docs-sync-report.mjs --json     # machine-readable
 *   node .claude/skills/shio-docs-sync/docs-sync-report.mjs --strict   # exit 1 on any gap
 */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, sep } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "..");
const args = new Set(process.argv.slice(2));

const { productVocabulary } = await import(
  pathToFileURL(join(ROOT, "cli", "conformance", "support", "product-vocabulary.mjs")).href);
const { DOCS_DIR } = await import(pathToFileURL(join(ROOT, "scripts", "docs-location.mjs")).href);
const SIDEBAR = join(ROOT, "shio-site", "docs", "astro.config.mjs");

if (!existsSync(DOCS_DIR)) {
  console.error(`docs not found at ${DOCS_DIR} (SHIO_DOCS_DIR overrides it)`);
  process.exit(2);
}

/** Every page, keyed by its Starlight slug (`getting-started/intro`, `index`). */
function pages(dir = DOCS_DIR, out = new Map()) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) pages(full, out);
    else if (/\.mdx?$/.test(name) && name !== "COVERAGE.md") {
      const slug = relative(DOCS_DIR, full).split(sep).join("/").replace(/\.mdx?$/, "").toLowerCase();
      out.set(slug, readFileSync(full, "utf8"));
    }
  }
  return out;
}

/** Starlight (github-slugger) heading ids, close enough for a report. */
const slugify = (heading) => heading.trim().toLowerCase()
  .replace(/<[^>]+>/g, "").replace(/`/g, "")
  .replace(/[^\p{L}\p{N}\s_-]/gu, "").replace(/\s/g, "-");

function headings(body) {
  const ids = new Set();
  let fenced = false;
  for (const line of body.split("\n")) {
    if (/^\s*(```|~~~)/.test(line)) fenced = !fenced;
    if (fenced) continue;
    const h = /^#{1,6}\s+(.*?)\s*(\{#([\w-]+)\})?\s*$/.exec(line);
    if (h) ids.add(h[3] ?? slugify(h[1]));
  }
  return ids;
}

const all = pages();
const prose = [...all.values()].join("\n");
const vocabulary = productVocabulary();

// 1. Code → docs: what the product declares that no page names.
//    `advisory` populations are named in prose by a description, not by their key, so a miss
//    there is a question to answer, not a defect to file.
const POPULATIONS = [
  { key: "cliVerbs", label: "CLI verbs (shio <verb>)", advisory: false },
  { key: "mcpTools", label: "MCP tools", advisory: false },
  { key: "mcpResources", label: "MCP resources", advisory: false },
  { key: "batchOps", label: "batch ops", advisory: false },
  { key: "verifyChecks", label: "verify --checks groups", advisory: false },
  { key: "properties", label: "shio.* properties", advisory: false },
  { key: "manifestFeatures", label: "manifest feature keys", advisory: true },
];
const isTemplate = (s) => /[<>]/.test(s);
const kebabToCamel = (s) => s.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
// Sibling shorthand, the configuration reference's house style for one row of related keys:
// `` `shio.agent.read.default-limit` · `.max-limit` `` names `shio.agent.read.max-limit` too.
// Each `.suffix` replaces the last segment of the full key that opens the run.
const shorthand = new Set();
for (const [, head, tail] of prose.matchAll(/`([a-z][\w.-]*\.)[\w-]+`((?:\s*·\s*`\.[\w-]+`)+)/g)) {
  for (const [, suffix] of tail.matchAll(/`\.([\w-]+)`/g)) shorthand.add(head + suffix);
}
const named = (subject) => prose.includes(subject) || shorthand.has(subject)
  // Spring relaxed binding: `shio.allowed-origins` may be documented as `shio.allowedOrigins`.
  || (subject.startsWith("shio.") && prose.includes(kebabToCamel(subject)));

const reverse = POPULATIONS.map(({ key, label, advisory }) => {
  const subjects = [...(vocabulary[key] ?? [])].map(String).filter((s) => !isTemplate(s));
  const missing = subjects.filter((s) => !named(s)).sort();
  return { key, label, advisory, total: subjects.length, missing };
});

// 2. Sidebar: a page absent from it builds as an unlisted route nobody reaches.
const sidebarSource = existsSync(SIDEBAR) ? readFileSync(SIDEBAR, "utf8") : "";
const unlisted = [...all.keys()]
  .filter((slug) => slug !== "index" && !sidebarSource.includes(`"${slug}"`))
  .sort();

// 3. Hand-typed internal links: `/docs/<slug>/` and `/docs/<slug>/#anchor`.
const ids = new Map([...all].map(([slug, body]) => [slug, headings(body)]));
const PUBLIC = join(ROOT, "shio-site", "docs", "public");
const brokenLinks = [];
let anchorsChecked = 0;
for (const [slug, body] of all) {
  for (const [, target, anchor] of body.matchAll(/\]\(\/docs\/([^)#\s]*?)\/?(?:#([^)\s]+))?\)/g)) {
    // A static file (`/docs/img/x.svg`) is served from the area's public/ directory.
    if (/\.[a-z0-9]+$/i.test(target)) {
      if (!existsSync(join(PUBLIC, ...target.split("/")))) {
        brokenLinks.push({ from: slug, link: `/docs/${target}`, reason: "no such file in public/" });
      }
      continue;
    }
    const page = (target || "index").toLowerCase();
    if (anchor) anchorsChecked++;
    if (!ids.has(page)) brokenLinks.push({ from: slug, link: `/docs/${target}/`, reason: "no such page" });
    else if (anchor && !ids.get(page).has(decodeURIComponent(anchor).toLowerCase())) {
      brokenLinks.push({ from: slug, link: `/docs/${target}/#${anchor}`, reason: "no such heading" });
    }
  }
}

const report = { docsDir: DOCS_DIR, pages: all.size, reverse, unlisted, brokenLinks, anchorsChecked };

if (args.has("--json")) {
  console.log(JSON.stringify(report, null, 2));
} else {
  console.log(`docs: ${relative(ROOT, DOCS_DIR)} (${all.size} pages)\n`);
  console.log("== Code -> docs: declared by the product, named on no page ==");
  for (const r of reverse) {
    const tag = r.advisory ? " [advisory]" : "";
    console.log(`${r.label}: ${r.total - r.missing.length}/${r.total} named${tag}`);
    for (const m of r.missing) console.log(`  - ${m}`);
  }
  console.log(`\n== Pages missing from the sidebar (astro.config.mjs): ${unlisted.length} ==`);
  for (const u of unlisted) console.log(`  - ${u}`);
  console.log(`\n== Internal links that do not resolve: ${brokenLinks.length} `
    + `(${anchorsChecked} anchored links checked) ==`);
  for (const b of brokenLinks) console.log(`  - ${b.from}: ${b.link} (${b.reason})`);
}

const gaps = reverse.filter((r) => !r.advisory).reduce((n, r) => n + r.missing.length, 0)
  + unlisted.length + brokenLinks.length;
if (args.has("--strict") && gaps > 0) process.exit(1);
