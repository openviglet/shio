// @scans prose reason: the subject IS the rendered output, the HTML and Markdown a visitor or a
// crawler receives, so what is read is the built page rather than any source file.
//
// SH1374: the hub contract, asserted on what the build produced. The source lints beside this one
// (full-names, hub-grammar, destinations, home-depth) each guard a file; a page is assembled from
// many, and the prerender adds the title, canonical, Open Graph and JSON-LD on top. So this reads
// `dist/`: every marketing route and every docs page, and their Markdown twins.
//
// Locally a missing or stale build is a diagnostic, not a failure: a source edit must not demand a
// build before the suite runs. CI builds the site first and sets SHIO_REQUIRE_SITE_BUILD=1, and
// there "not measured" is a failure, the rule the console size gate follows (SH1126).

import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const SITE = fileURLToPath(new URL("..", import.meta.url));
const DIST = join(SITE, "dist");
const ORIGIN = "https://shio.viglet.org";
const REQUIRED = process.env.SHIO_REQUIRE_SITE_BUILD === "1";

const walk = (dir) => readdirSync(dir).flatMap((name) => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? walk(path) : [path];
});

/** Why there is nothing to read, or null when `dist/` is a build of this tree. */
function unmeasured() {
  if (!existsSync(join(DIST, "index.html"))) return "no dist/index.html: run `pnpm run build:site`";
  const built = statSync(join(DIST, "index.html")).mtimeMs;
  const newest = [...walk(join(SITE, "src")), ...walk(join(SITE, "docs/src"))]
    .reduce((max, path) => Math.max(max, statSync(path).mtimeMs), 0);
  return newest > built ? "dist/ is older than the sources: run `pnpm run build:site`" : null;
}

const skipReason = unmeasured();
function guard(t) {
  if (!skipReason) return false;
  assert.ok(!REQUIRED, `SH1374: ${skipReason} (SHIO_REQUIRE_SITE_BUILD=1)`);
  t.diagnostic(`SH1374: not measured, ${skipReason}`);
  t.skip(skipReason);
  return true;
}

const pages = () => walk(DIST).filter((path) => path.endsWith("index.html")
  && !relative(DIST, path).replaceAll("\\", "/").startsWith("docs/_"));
const route = (path) => "/" + relative(DIST, path).replaceAll("\\", "/").replace(/index\.html$/u, "");

/** What a visitor reads: body text plus the attributes a screen reader or a crawler reads out. */
function visibleText(html) {
  const attrs = [...html.matchAll(/\s(?:alt|aria-label|title)="([^"]*)"/gu)].map((m) => m[1]);
  const metas = [...html.matchAll(/<meta[^>]+(?:name|property)="(?:description|og:[a-z]+|twitter:[a-z]+)"[^>]*content="([^"]*)"/gu)]
    .map((m) => m[1]);
  // Attribute values go before the tags: an attribute may hold a literal `>` (a copy button's
  // `data-code` does), which would end a tag early and leak the code into the text. A Mermaid
  // block's source stays, because it renders as a diagram whose labels a reader sees.
  const body = html
    .replace(/\s[\w:-]+="[^"]*"/gu, (attr) => (/^\sclass="mermaid"$/u.test(attr) ? attr : ""))
    .replace(/<(script|style|code|svg|template)\b[\s\S]*?<\/\1>/giu, " ")
    .replace(/<pre(?! class="mermaid")\b[\s\S]*?<\/pre>/giu, " ")
    .replace(/<[^>]+>/gu, " ");
  return [body, ...attrs, ...metas].join("\n").replace(/&amp;/gu, "&").replace(/&#x27;|&#39;/gu, "'");
}

/** The console's own provider labels, which a page spells the way the screen does (SH1369). */
const LABELS = /\bShio (Native|Package)\b/gu;
const BARE = /(?<!Viglet )\b(Shio|Turing|Dumont)\b/gu;

test("SH1374: no rendered page names a product bare, in text, labels or metadata", (t) => {
  if (guard(t)) return;
  // Twelve marketing routes and the docs pages: a walk that found a handful read the wrong folder.
  assert.ok(pages().length >= 40, `SH1374: only ${pages().length} built pages found under dist/`);
  const found = [];
  for (const path of pages()) {
    const text = visibleText(readFileSync(path, "utf8")).replace(LABELS, "");
    const hits = [...new Set([...text.matchAll(BARE)].map((m) => {
      const at = m.index;
      return text.slice(Math.max(0, at - 40), at + 20).replace(/\s+/gu, " ").trim();
    }))];
    for (const hit of hits) found.push(`${route(path)}: …${hit}…`);
  }
  assert.deepEqual(found, [], "SH1374: the hub decided full names everywhere a visitor reads");
});

test("SH1374: every page carries a title, a canonical URL and Open Graph naming the product", (t) => {
  if (guard(t)) return;
  const wrong = [];
  for (const path of pages()) {
    const html = readFileSync(path, "utf8");
    const title = /<title>([^<]*)<\/title>/u.exec(html)?.[1] ?? "";
    const canonical = /<link rel="canonical" href="([^"]+)"/u.exec(html)?.[1] ?? "";
    const ogTitle = /<meta property="og:title" content="([^"]*)"/u.exec(html)?.[1] ?? "";
    if (!title.includes("Viglet Shio")) wrong.push(`${route(path)}: title "${title}"`);
    if (!canonical.startsWith(ORIGIN + "/")) wrong.push(`${route(path)}: canonical "${canonical}"`);
    if (!ogTitle) wrong.push(`${route(path)}: no og:title`);
  }
  assert.deepEqual(wrong, []);
});

test("SH1374: the JSON-LD on every page parses, and its breadcrumb starts at Viglet Shio", (t) => {
  if (guard(t)) return;
  const wrong = [];
  for (const path of pages()) {
    const html = readFileSync(path, "utf8");
    for (const [, json] of html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/gu)) {
      let data;
      try {
        data = JSON.parse(json);
      } catch (error) {
        wrong.push(`${route(path)}: JSON-LD does not parse (${error.message})`);
        continue;
      }
      for (const node of [data, ...(data["@graph"] ?? [])].flat()) {
        if (node?.["@type"] === "BreadcrumbList" && node.itemListElement?.[0]?.name !== "Viglet Shio") {
          wrong.push(`${route(path)}: breadcrumb starts at "${node.itemListElement?.[0]?.name}"`);
        }
      }
    }
  }
  assert.deepEqual(wrong, []);
});

test("SH1374: every internal link on a marketing route reaches a built page, and its anchor exists", (t) => {
  if (guard(t)) return;
  const idsOf = new Map();
  const ids = (file) => {
    if (!idsOf.has(file)) idsOf.set(file, new Set([...readFileSync(file, "utf8").matchAll(/\sid="([^"]+)"/gu)].map((m) => m[1])));
    return idsOf.get(file);
  };
  const broken = [];
  for (const path of pages().filter((p) => !route(p).startsWith("/docs/"))) {
    const html = readFileSync(path, "utf8");
    for (const [, href] of html.matchAll(/<a[^>]+href="(\/[^"]*)"/gu)) {
      const [pathname, anchor] = href.split("#");
      const clean = pathname.split("?")[0];
      const target = clean.endsWith("/") ? join(DIST, clean, "index.html") : join(DIST, clean);
      const file = existsSync(target) ? target : clean === "" ? path : null;
      if (!file) {
        broken.push(`${route(path)} → ${href}: no such page`);
      } else if (anchor && file.endsWith(".html") && !ids(file).has(anchor)) {
        broken.push(`${route(path)} → ${href}: no #${anchor} there`);
      }
    }
  }
  assert.deepEqual([...new Set(broken)], []);
});

test("SH1374: every marketing route has a Markdown twin, and the twins name the product in full", (t) => {
  if (guard(t)) return;
  const wrong = [];
  for (const path of pages().filter((p) => !route(p).startsWith("/docs/"))) {
    // The prerender writes `/claude-code.md` beside `/claude-code/`, and `/index.md` for the home.
    const r = route(path);
    const twin = join(DIST, r === "/" ? "index.md" : `${r.replace(/\/$/u, "")}.md`);
    if (!existsSync(twin)) {
      wrong.push(`${route(path)}: no Markdown twin`);
      continue;
    }
    const prose = readFileSync(twin, "utf8").replace(/```[\s\S]*?```/gu, "").replace(/`[^`]*`/gu, "")
      .replace(/\]\([^)]*\)/gu, "]");
    for (const m of prose.matchAll(BARE)) wrong.push(`${route(path)} twin: …${prose.slice(Math.max(0, m.index - 40), m.index + 20).replace(/\s+/gu, " ")}…`);
  }
  assert.deepEqual(wrong, []);
});
