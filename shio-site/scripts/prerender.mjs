// Static prerender for the public site.
//
// Runs AFTER both Vite builds:
//   1. `vite build`                          -> dist/      (client + index.html template)
//   2. `vite build --ssr src/entry-server`   -> dist-ssr/  (render() + routes)
//
// For each route we render the app to an HTML string, patch the per-page <head>
// (title / description / canonical / og), inject the markup into #root, and
// write a static index.html. The client then hydrates it. No react-router and no
// SSG framework, so the bleeding-edge Vite this workspace tracks has nothing framework-shaped to break.
//
// Every patch is `replaceOrThrow`: a template that drifts fails the build rather
// than shipping a page with somebody else's <title>. This is the gate SH538
// relies on, and it is the reason a silently untitled page cannot go public.

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { pathToFileURL } from "node:url";
import { pageToMarkdown } from "./markdown.mjs";

const SITE_ORIGIN = "https://shio.viglet.org";
const distDir = resolve(process.cwd(), "dist");
const serverEntry = resolve(process.cwd(), "dist-ssr", "entry-server.js");

const { render, routes } = await import(pathToFileURL(serverEntry).href);
const template = readFileSync(resolve(distDir, "index.html"), "utf-8");

/** Replace the first match of `re` in `text`, or throw so a drift fails the build. */
function replaceOrThrow(text, re, replacement, label, where = "index.html template") {
  if (!re.test(text)) {
    throw new Error(`prerender: could not find ${label} in ${where}`);
  }
  return text.replace(re, replacement);
}

/**
 * The published URL of a route. GitHub Pages serves `/x/index.html` at `/x/`
 * and answers `/x` with a 301, so the slashed form is the only one that does
 * not redirect, and it is the one a canonical, a sitemap entry and a link use.
 */
function pageUrl(path) {
  return path.endsWith("/") ? path : `${path}/`;
}

/**
 * Every link to a route must use `pageUrl`. Search Console reports a link that
 * redirects as a page with a redirect, and the route keys have no slash, so a
 * link typed from one of them is wrong by default. This reads what was written,
 * not the source, because an href can come out of a data file.
 */
const routePaths = new Set(routes.map((r) => r.path).filter((p) => p !== "/"));
function assertSlashedLinks(text, where) {
  const bad = [];
  const re = /(?:href="|\]\(|<loc>)(?:https:\/\/shio\.viglet\.org)?(\/[^"#?)<\s]*)/g;
  for (const m of text.matchAll(re)) {
    if (routePaths.has(m[1])) bad.push(m[1]);
  }
  if (bad.length) {
    throw new Error(
      `prerender: ${where} links to ${[...new Set(bad)].join(", ")} without the trailing slash; ` +
        `that URL answers with a redirect. Write ${pageUrl(bad[0])}.`
    );
  }
}

function buildHtml(route) {
  const appHtml = render(route.path);
  const canonical = `${SITE_ORIGIN}${pageUrl(route.path)}`;
  const title = route.title;
  const desc = route.description;

  let html = template;
  html = replaceOrThrow(html, /<title>[\s\S]*?<\/title>/, `<title>${title}</title>`, "<title>");
  html = replaceOrThrow(
    html,
    /(<meta\s+name="description"\s+content=")[\s\S]*?("\s*\/>)/,
    `$1${desc}$2`,
    "description meta"
  );
  html = replaceOrThrow(
    html,
    /(<meta\s+property="og:title"\s+content=")[\s\S]*?("\s*\/>)/,
    `$1${title}$2`,
    "og:title meta"
  );
  html = replaceOrThrow(
    html,
    /(<meta\s+property="og:url"\s+content=")[\s\S]*?("\s*\/>)/,
    `$1${canonical}$2`,
    "og:url meta"
  );
  html = replaceOrThrow(
    html,
    /<link\s+rel="canonical"[\s\S]*?\/>/,
    `<link rel="canonical" href="${canonical}" />`,
    "canonical link"
  );
  html = replaceOrThrow(
    html,
    /<div id="root"><\/div>/,
    `<div id="root">${appHtml}</div>`,
    "#root container"
  );
  return html;
}

/**
 * SH537: the Markdown twin, at `<path>.md`.
 *
 * A Claude Code-first product that answers an agent only in hydrated markup is
 * failing its own argument in the first thing anybody reads. The twin is not a
 * second copy of the page: it is the same render, converted (see markdown.mjs
 * for why that direction and not the other).
 */
function markdownPathFor(route) {
  return route.path === "/"
    ? resolve(distDir, "index.md")
    : resolve(distDir, `.${route.path}.md`);
}

const index = [];

for (const route of routes) {
  const html = buildHtml(route);
  assertSlashedLinks(html, route.path);
  const canonical = `${SITE_ORIGIN}${pageUrl(route.path)}`;

  const outFile =
    route.path === "/"
      ? resolve(distDir, "index.html")
      : resolve(distDir, `.${route.path}`, "index.html");
  mkdirSync(dirname(outFile), { recursive: true });
  writeFileSync(outFile, html, "utf-8");

  const mdFile = markdownPathFor(route);
  const markdown = pageToMarkdown(html, {
    title: route.title,
    description: route.description,
    canonical,
  });
  mkdirSync(dirname(mdFile), { recursive: true });
  writeFileSync(mdFile, markdown, "utf-8");

  const mdPath = route.path === "/" ? "/index.md" : `${route.path}.md`;
  index.push({
    path: route.path,
    markdown: mdPath,
    title: route.title,
    description: route.description,
    bytes: { html: Buffer.byteLength(html), markdown: Buffer.byteLength(markdown) },
  });

  console.log(
    `prerendered ${route.path} -> ${outFile.replace(distDir, "dist")}` +
      ` + ${mdPath} (${Math.round(Buffer.byteLength(markdown) / 1024)} KB` +
      ` vs ${Math.round(Buffer.byteLength(html) / 1024)} KB)`
  );
}

/**
 * The site's own manifest, in the shape `/api/v2/agent/manifest` uses, because
 * the discovery pattern the product teaches should be the one the site is
 * discovered by. `builtAt` is here for the reason SH460 put it on the product's
 * manifest: a client that cannot date what answered it cannot tell a current
 * site from a cached one.
 */
const manifest = {
  identity: {
    name: "Viglet Shio",
    kind: "marketing-site",
    origin: SITE_ORIGIN,
    builtAt: new Date().toISOString(),
  },
  startHere:
    "Every route has a Markdown twin at <path>.md. Fetch that instead of the HTML. " +
    "/llms.txt is the whole product in one file.",
  routes: index,
  product: {
    docs: "https://shio.viglet.org/docs/",
    source: "https://github.com/openviglet/shio",
    mcp: "claude mcp add shio -- npx @viglet/shio mcp",
    firstCall: "GET /api/v2/agent/manifest?include=features,limits",
  },
};
writeFileSync(resolve(distDir, "manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`, "utf-8");

const htmlBytes = index.reduce((n, r) => n + r.bytes.html, 0);
const mdBytes = index.reduce((n, r) => n + r.bytes.markdown, 0);

/**
 * SH925: the size pair in `llms.txt` is patched here rather than typed there.
 *
 * It was typed in two places and the two had already disagreed (78/592 in
 * `llms.txt`, 78/593 in the README) while the build was producing a third pair.
 * The site's own rule is that a measured number is generated, and this is the
 * one measurement that only exists after every route has been written, so it
 * is patched into the copy in `dist/` on the way out, with the same
 * `replaceOrThrow` discipline as the `<head>`: a sentence that drifts fails the
 * build instead of publishing a stale figure.
 */
const llmsPath = resolve(distDir, "llms.txt");
writeFileSync(
  llmsPath,
  replaceOrThrow(
    readFileSync(llmsPath, "utf-8"),
    /\d+ KB of Markdown against \d+ KB of HTML/,
    `${Math.round(mdBytes / 1024)} KB of Markdown against ${Math.round(htmlBytes / 1024)} KB of HTML`,
    "the Markdown-versus-HTML size pair",
    "llms.txt"
  ),
  "utf-8"
);

assertSlashedLinks(readFileSync(llmsPath, "utf-8"), "llms.txt");
assertSlashedLinks(readFileSync(resolve(distDir, "sitemap.xml"), "utf-8"), "sitemap.xml");

console.log(
  `\nprerendered ${routes.length} route(s), each with a Markdown twin.` +
    ` ${Math.round(mdBytes / 1024)} KB of Markdown against ${Math.round(htmlBytes / 1024)} KB of HTML` +
    `, ${Math.round((1 - mdBytes / htmlBytes) * 100)}% smaller.`
);
