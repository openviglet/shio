// SH1262 — the one-time move of vigletdocs' `docs-shio/` into this area, kept so the conversion
// can be read and re-run rather than trusted.
//
//   node scripts/import-vigletdocs.mjs <path to vigletdocs>/docs-shio
//
// Content comes across intact; only what Docusaurus means and Starlight does not is rewritten:
//
// - frontmatter: `sidebar_label`/`sidebar_position` become `sidebar.label`/`sidebar.order`,
//   `hide_table_of_contents` becomes `tableOfContents: false`, and `slug` goes, because a page's
//   path is now its file (the hubs move from /category/<hub> to /<hub>; the redirect table says so);
// - the body's own `# Title`, which Starlight already renders from the frontmatter;
// - `:::info` and `:::warning`, which Starlight spells `:::note` and `:::caution`, and an
//   admonition's title, which it writes in brackets;
// - `## Heading {#id}`: the explicit id is kept as an anchor before the heading, so every link
//   that used it still lands;
// - links to `.md`/`.mdx` files, which Starlight does not rewrite, become absolute `/docs/<slug>/`
//   links, because Astro does not add the base to a link typed by hand;
// - each hub's `<DocCardList />`, which becomes a grid of cards over the hub's pages.
//
// The landing page (index.mdx) is not converted: it was built from inline React components, and
// is rewritten by hand with Starlight's own. COVERAGE.md is a maintenance file, not a page.

import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, posix, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

const SOURCE = process.argv[2];
if (!SOURCE) {
  console.error("usage: node scripts/import-vigletdocs.mjs <vigletdocs>/docs-shio");
  process.exit(2);
}
const HERE = dirname(fileURLToPath(import.meta.url));
const DEST = join(HERE, "..", "src", "content", "docs");
const BASE = "/docs";

/** The hubs and their pages, in sidebar order: what each hub's card grid lists. */
const HUBS = {
  "agent-builds-it": ["mcp", "agent-surface", "content-as-files", "cli", "blueprints",
    "website-development", "design-tokens", "replication", "token-economy"],
  "curator-approves-it": ["content-console", "content-lifecycle", "agent-safety", "universal-editor",
    "content-modeling", "content-i18n", "administration-guide", "import-export"],
  "cda-delivers-it": ["headless/content-delivery-api", "headless/javascript-client",
    "headless/react-sdk", "headless/nextjs-starter", "graphql", "webhooks"],
  "run-it": ["installation-guide", "configuration-reference", "multi-tenancy", "search-caching",
    "security"],
};

const SKIP = new Set(["COVERAGE.md", "index.mdx"]);

function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}

/** `slug` of a source file: its path without extension, posix. */
const slugOf = (file) => relative(SOURCE, file).split(sep).join("/").replace(/\.mdx?$/, "");

function splitFrontmatter(text) {
  const match = /^---\r?\n([\s\S]*?)\r?\n---\r?\n?/.exec(text);
  if (!match) return { fields: [], body: text };
  const fields = match[1].split(/\r?\n/).map((line) => {
    const at = line.indexOf(":");
    return at < 0 ? [line, null] : [line.slice(0, at).trim(), line.slice(at + 1).trim()];
  });
  return { fields, body: text.slice(match[0].length) };
}

function frontmatter(fields) {
  const out = [];
  const sidebar = [];
  for (const [key, value] of fields) {
    if (value === null) continue;
    if (key === "slug") continue;
    if (key === "sidebar_label") sidebar.push(`  label: ${value}`);
    else if (key === "sidebar_position") sidebar.push(`  order: ${value}`);
    else if (key === "hide_table_of_contents") out.push(`tableOfContents: ${value === "true" ? "false" : "true"}`);
    else out.push(`${key}: ${value}`);
  }
  if (sidebar.length) out.push("sidebar:", ...sidebar);
  return `---\n${out.join("\n")}\n---\n`;
}

const unquote = (value) => (value ?? "").replace(/^["']|["']$/g, "");

/** A link to a doc file, made absolute: Starlight leaves `.md` hrefs as they are. */
function link(fromSlug, target, hash) {
  const resolved = posix.normalize(posix.join(posix.dirname(fromSlug), target));
  const slug = resolved.replace(/(^|\/)index$/, "");
  return `${BASE}/${slug ? `${slug}/` : ""}${hash ?? ""}`;
}

function convertProse(text, fromSlug) {
  return text
    .replace(/^:::info\b/gm, ":::note")
    .replace(/^:::warning\b/gm, ":::caution")
    .replace(/^:::(\w+)[ \t]+(.+)$/gm, ":::$1[$2]")
    .replace(/^(#{2,6})[ \t]+(.*?)[ \t]+\{#([\w-]+)\}[ \t]*$/gm, '<span id="$3"></span>\n\n$1 $2')
    .replace(/\]\((?!https?:|\/|#|mailto:)([^)\s#]+?)\.mdx?(#[^)\s]*)?\)/g,
      (_, target, hash) => `](${link(fromSlug, target, hash)})`)
    // Docusaurus served this tree under /shio/, and a hub under /shio/category/<hub>.
    .replace(/\]\(\/shio\/(?:category\/)?([^)\s#]*?)\/?(#[^)\s]*)?\)/g,
      (_, target, hash) => `](${BASE}/${target ? `${target}/` : ""}${hash ?? ""})`);
}

/** Prose is converted; fenced code is not, since a `.md` in a code sample is a file name. */
function convertBody(body, fromSlug) {
  const parts = body.split(/^(```.*$[\s\S]*?^```\s*$)/m);
  return parts.map((part, index) => (index % 2 ? part : convertProse(part, fromSlug))).join("");
}

const pages = new Map();
for (const file of walk(SOURCE)) {
  const name = relative(SOURCE, file);
  if (SKIP.has(name) || !/\.mdx?$/.test(name)) continue;
  const { fields, body } = splitFrontmatter(readFileSync(file, "utf8").replace(/\r\n/g, "\n"));
  pages.set(slugOf(file), { file, fields, body });
}

for (const [slug, { file, fields, body }] of pages) {
  let title = unquote(fields.find(([key]) => key === "title")?.[1]);
  let text = body.replace(/^\s*import DocCardList from '@theme\/DocCardList';\s*\n/m, "");
  // Starlight renders the title; the page's own H1 would be a second one. A page whose title
  // only its H1 carried keeps it, as the frontmatter title Starlight requires.
  const heading = /^\s*# (.+)\n+/.exec(text);
  if (heading) {
    if (!title) {
      title = heading[1].trim();
      fields.unshift(["title", JSON.stringify(title)]);
    }
    text = text.slice(heading[0].length);
  }
  text = convertBody(text, slug);
  let imports = "";
  if (HUBS[slug]) {
    const cards = HUBS[slug].map((page) => {
      const target = pages.get(page);
      const cardTitle = unquote(target?.fields.find(([key]) => key === "title")?.[1]) || page;
      const description = unquote(target?.fields.find(([key]) => key === "description")?.[1]);
      return `  <LinkCard title=${JSON.stringify(cardTitle)} href="${BASE}/${page}/"`
        + `${description ? ` description=${JSON.stringify(description)}` : ""} />`;
    });
    text = text.replace(/<DocCardList\s*\/>/, `<CardGrid>\n${cards.join("\n")}\n</CardGrid>`);
    imports = "import { CardGrid, LinkCard } from '@astrojs/starlight/components';\n\n";
  }
  const out = join(DEST, `${slug}${file.endsWith(".mdx") ? ".mdx" : ".md"}`);
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, `${frontmatter(fields)}\n${imports}${text.trimStart()}`, "utf8");
  if (!title) console.warn(`${slug}: no title`);
}
console.log(`imported ${pages.size} page(s) into ${relative(process.cwd(), DEST)}`);
