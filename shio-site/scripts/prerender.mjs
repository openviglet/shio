// Static prerender for the public site.
//
// Runs AFTER both Vite builds:
//   1. `vite build`                          -> dist/      (client + index.html template)
//   2. `vite build --ssr src/entry-server`   -> dist-ssr/  (render() + routes)
//
// For each route we render the app to an HTML string, patch the per-page <head>
// (title / description / canonical / og), inject the markup into #root, and
// write a static index.html. The client then hydrates it. No react-router and no
// SSG framework — robust against the bleeding-edge Vite this workspace tracks.
//
// Every patch is `replaceOrThrow`: a template that drifts fails the build rather
// than shipping a page with somebody else's <title>. This is the gate SH538
// relies on, and it is the reason a silently untitled page cannot go public.

import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { pathToFileURL } from "node:url";

const SITE_ORIGIN = "https://shio.viglet.org";
const distDir = resolve(process.cwd(), "dist");
const serverEntry = resolve(process.cwd(), "dist-ssr", "entry-server.js");

const { render, routes } = await import(pathToFileURL(serverEntry).href);
const template = readFileSync(resolve(distDir, "index.html"), "utf-8");

/** Replace the first match of `re` in `html`, or throw so a template drift fails the build. */
function replaceOrThrow(html, re, replacement, label) {
  if (!re.test(html)) {
    throw new Error(`prerender: could not find ${label} in index.html template`);
  }
  return html.replace(re, replacement);
}

function buildHtml(route) {
  const appHtml = render(route.path);
  const canonical = `${SITE_ORIGIN}${route.path === "/" ? "/" : route.path}`;
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

for (const route of routes) {
  const html = buildHtml(route);
  const outFile =
    route.path === "/"
      ? resolve(distDir, "index.html")
      : resolve(distDir, `.${route.path}`, "index.html");
  mkdirSync(dirname(outFile), { recursive: true });
  writeFileSync(outFile, html, "utf-8");
  console.log(`prerendered ${route.path} -> ${outFile.replace(distDir, "dist")}`);
}

console.log(`\nprerendered ${routes.length} route(s).`);
