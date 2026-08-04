/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio clone <url>` (SH377) — walk one origin and write down what is there. No content.
 *
 * ## The four rules the walk keeps, and why each one is a rule
 *
 * **One origin.** Every link off the host is *recorded* and never followed. A crawler that drifts
 * is a crawler that mirrors somebody else's site into a customer's inventory.
 *
 * **A seed that redirects off-host is refused.** `example.com → www.example.com` is the common
 * case, and following it silently would capture a site under an origin that does not serve it —
 * every path in the inventory then belongs to a host nobody named. The error says which host it
 * actually got, so the fix is a copy-paste. A *page* that redirects off-host mid-crawl is a
 * different thing entirely and is just an outbound link.
 *
 * **The site's own robots rules bound it.** Not because a crawler is legally obliged to read them,
 * but because the alternative is a tool that behaves worse than a search engine against a stranger's
 * server. `--ignore-robots` exists for your own site and says so in the inventory, so a capture made
 * without permission cannot be mistaken later for one made with it.
 *
 * **Sequential, in a sorted frontier.** A concurrent crawl is faster and produces a different page
 * set every run once a ceiling truncates it — and the whole value of this file is that it can be
 * diffed against the next crawl (P5). Sequential also means `Crawl-delay` is something this can
 * actually honour rather than approximate.
 */
import { dirname, join } from "node:path";

import {
  DEFAULT_MAX_ASSETS,
  DEFAULT_MAX_ASSET_BYTES,
  describeDownloads,
  placeAsset,
  planAssets,
  withDownloads,
} from "../clone-assets.mjs";
import {
  DEFAULT_DEPTH,
  DEFAULT_MAX_PAGES,
  INVENTORY_FILE,
  ROBOTS_AGENT,
  buildInventory,
  describeInventory,
  parseRobots,
  pathOf,
  resolveHref,
  robotsAllows,
  sameOrigin,
  scanPage,
  serializeInventory,
} from "../clone.mjs";
import { ASSETS_DIR } from "../content-assets.mjs";
import { assetPathCollisions, siteDir } from "../content-files.mjs";

const USER_AGENT = `${ROBOTS_AGENT} (+https://github.com/openviglet/shio)`;

export async function run(flags, positionals, ctx) {
  const seed = flags.url ?? positionals[0];
  if (!seed) {
    throw new Error(
      "shio clone: pass the site to capture.\n" +
        "Example: shio clone https://example.com --depth 3 --max-pages 200",
    );
  }
  let origin;
  try {
    origin = new URL(String(seed));
  } catch {
    throw new Error(
      `shio clone: "${seed}" is not a URL. Pass an absolute http(s) address.\n` +
        `Example: shio clone https://example.com`,
    );
  }
  if (origin.protocol !== "http:" && origin.protocol !== "https:") {
    throw new Error(`shio clone: "${seed}" is not http(s), so there is nothing to crawl.`);
  }

  // SH378 — before any request at all, including robots.txt. A run that has nowhere to put the bytes
  // should cost a second and no traffic to somebody else's server, and the first draft of this
  // validated after the robots fetch, which the test noticed.
  const site = flags.assets === true ? assetSite(flags) : null;

  // `--depth 0` is the seed page and nothing else, which is a legitimate thing to ask for — the
  // cheapest possible look at a site. A ceiling of zero pages is not.
  const depth = whole(flags.depth, DEFAULT_DEPTH, "--depth", 0);
  const maxPages = whole(flags.maxPages, DEFAULT_MAX_PAGES, "--max-pages", 1);
  const rendered = flags.render === true;
  const notes = [];

  const robots = flags.ignoreRobots === true
    ? { present: false, matched: null, sitemaps: [], crawlDelay: null, rules: null }
    : await readRobots(origin, ctx);
  if (flags.ignoreRobots === true) {
    notes.push("--ignore-robots was passed: this capture did not read the site's robots.txt at all");
  }

  const page = rendered ? await renderer(flags, ctx) : null;
  try {
    const walk = await crawl({ origin, depth, maxPages, robots, page, flags }, ctx);
    notes.push(...walk.notes);
    let inventory = buildInventory({
      origin: origin.origin,
      seed: String(seed),
      visited: walk.visited,
      robots,
      options: { depth, maxPages, rendered, truncated: walk.truncated },
      notes,
    });
    if (site) {
      inventory = await download(inventory, { site, flags }, ctx);
    }

    const out = typeof flags.out === "string" ? flags.out : INVENTORY_FILE;
    const target = join(ctx.cwd, out);
    await ctx.fs.mkdir(dirname(target), { recursive: true });
    await ctx.fs.writeFile(target, serializeInventory(inventory), "utf8");

    if (flags.json) {
      ctx.stdout.write(serializeInventory(inventory));
    } else {
      ctx.stdout.write(describeInventory(inventory, { ...flags, out }));
      ctx.stdout.write(describeDownloads(inventory, flags));
    }
    // A capture that reached nothing is not a capture. Exiting 0 on it would let a CI step that
    // wraps this read "0 pages" as "a site with no pages".
    return { inventory, out, exitCode: inventory.pages.length ? 0 : 1 };
  } finally {
    await page?.close?.();
  }
}

/**
 * The site the bytes will belong to (SH378).
 *
 * <p>Required rather than guessed. An asset's tree path is `<site>/<chain>/<name>` and the crawl has
 * no idea what the replica will be called — deriving it from the origin's hostname would put a site
 * called `www.example.com` in somebody's instance, and a rename afterwards moves every asset.
 * `siteDir` validates it, so a name with a separator is refused here and not by the 200th write.
 */
function assetSite(flags) {
  const name = typeof flags.site === "string" ? flags.site : "";
  if (!name) {
    throw new Error(
      "shio clone: --assets needs --site <name>, because an asset's path in the tree is\n" +
        "<site>/<folder chain>/<file name> and a crawl cannot know what the replica is called.\n" +
        "Example: shio clone https://example.com --assets --site Acme",
    );
  }
  return siteDir(name);
}

/**
 * Downloads what the inventory names, and folds the outcome back into it.
 *
 * <p>Sequential like the crawl, and for one of the same reasons: this is somebody else's server, and
 * forty parallel connections to it is a load decision made on their behalf. It also keeps the dedupe
 * deterministic — with concurrency, *which* of two identical assets becomes the file on disk depends
 * on which response arrived first (P5).
 */
async function download(inventory, { site, flags }, ctx) {
  const maxAssets = whole(flags.maxAssets, DEFAULT_MAX_ASSETS, "--max-assets", 1);
  const maxBytes = whole(flags.maxAssetBytes, DEFAULT_MAX_ASSET_BYTES, "--max-asset-bytes", 1);
  const { plan, skipped, external } = planAssets(inventory, { site, maxAssets });

  // SH408's question, asked of this root before anything is written: two source URLs whose names
  // differ only in case are one file on macOS and Windows, and the second would overwrite the first
  // with no digest ever compared.
  const collisions = assetPathCollisions(plan.map((item) => item.file));
  const results = [];
  const state = { byDigest: new Map(), byPath: new Map() };

  for (const item of plan) {
    if (collisions.has(item.file)) {
      const group = collisions.get(item.file);
      results.push({
        action: "collision",
        source: item.source,
        file: item.file,
        detail: `folds onto ${group.paths.filter((each) => each !== item.file).join(" ")} on a `
          + "case-insensitive filesystem",
      });
      continue;
    }
    const fetched = await fetchBytes(item, maxBytes, ctx);
    if (fetched.error) {
      results.push({ action: "failed", source: item.source, file: item.file, detail: fetched.error });
      continue;
    }
    const placed = placeAsset(state, {
      source: item.source,
      file: item.file,
      bytes: fetched.bytes,
      contentType: fetched.contentType,
      kind: item.kind,
    });
    results.push(placed);
    if (placed.action !== "wrote") continue;
    const target = join(ctx.cwd, ASSETS_DIR, item.file);
    await ctx.fs.mkdir(dirname(target), { recursive: true });
    await ctx.fs.writeFile(target, fetched.bytes);
  }
  return withDownloads(inventory, { site, results, skipped, external });
}

/**
 * One asset's bytes, or the reason there are none.
 *
 * <p>The ceiling is checked against `content-length` **and** against what actually arrived: a server
 * that under-declares or omits the header would otherwise walk straight past the limit, and the
 * limit exists to stop a crawl filling a disk.
 */
async function fetchBytes(item, maxBytes, ctx) {
  let response;
  try {
    response = await ctx.fetch(item.url, {
      headers: { "User-Agent": USER_AGENT },
      redirect: "follow",
    });
  } catch (error) {
    return { error: `fetch failed: ${error?.message ?? error}` };
  }
  if (!response.ok) {
    return { error: `HTTP ${response.status ?? 0}` };
  }
  const declared = Number(response.headers?.get?.("content-length") ?? Number.NaN);
  if (Number.isFinite(declared) && declared > maxBytes) {
    return { error: `${declared} bytes, past --max-asset-bytes (${maxBytes})` };
  }
  let bytes;
  try {
    bytes = new Uint8Array(await response.arrayBuffer());
  } catch (error) {
    return { error: `could not read the body: ${error?.message ?? error}` };
  }
  if (bytes.length > maxBytes) {
    return { error: `${bytes.length} bytes, past --max-asset-bytes (${maxBytes})` };
  }
  return { bytes, contentType: response.headers?.get?.("content-type") ?? "" };
}

function whole(value, fallback, flag, min) {
  if (value === undefined || value === true) return fallback;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < min) {
    throw new Error(`shio clone: ${flag} takes a whole number of ${min} or more, not "${value}".`);
  }
  return parsed;
}

/**
 * Reads robots.txt, or decides that the absence of one is the answer.
 *
 * <p>A 404 allows everything and is reported as such. A network failure is treated the same way
 * rather than refusing the run: a crawler that cannot start because one optional file timed out is
 * a worse tool, and the inventory records that nothing restricted it.
 */
async function readRobots(origin, ctx) {
  try {
    const response = await ctx.fetch(new URL("/robots.txt", origin).href, {
      headers: { "User-Agent": USER_AGENT },
      redirect: "follow",
    });
    if (!response.ok) return parseRobots("", ROBOTS_AGENT);
    return parseRobots(await response.text(), ROBOTS_AGENT);
  } catch {
    return parseRobots("", ROBOTS_AGENT);
  }
}

/**
 * Breadth-first over the origin, bounded twice and deterministic.
 *
 * <p>The frontier is sorted before each level, so the pages a ceiling cuts are the same pages on
 * every run against an unchanged site. Without that, `--max-pages 50` would capture a different
 * fifty each time and the inventory could not be compared with itself.
 */
async function crawl({ origin, depth, maxPages, robots, page, flags }, ctx) {
  const visited = [];
  const seen = new Set();
  const notes = [];
  let frontier = [pathOf(origin) === "/" ? new URL("/", origin).href : origin.href];
  seen.add(frontier[0]);
  let truncated = false;
  let level = 0;
  const delay = Number(flags.delay ?? robots?.crawlDelay ?? 0) * 1000;

  while (frontier.length && level <= depth) {
    const next = new Set();
    // Explicit comparator, and the order is the point rather than a detail: it is what makes the
    // pages a ceiling cuts the same pages on every run.
    for (const href of [...frontier].sort((a, b) => (a < b ? -1 : 1))) {
      if (visited.length >= maxPages) {
        truncated = true;
        break;
      }
      const url = new URL(href);
      const path = pathOf(url);
      if (!robotsAllows(robots, path)) {
        notes.push(`robots.txt disallows ${path}, so it was not fetched`);
        continue;
      }
      const fetched = await visit(url, { page, flags, isSeed: level === 0 && visited.length === 0 }, ctx);
      visited.push({ ...fetched, depth: level, url: url.href });
      if (fetched.doc && level < depth) {
        for (const link of fetched.doc.links) {
          const resolved = resolveHref(link.href, url.href);
          if (!resolved || !sameOrigin(resolved, origin)) continue;
          if (seen.has(resolved.href)) continue;
          seen.add(resolved.href);
          next.add(resolved.href);
        }
      }
      if (delay > 0) await sleep(delay);
    }
    if (visited.length >= maxPages && next.size) truncated = true;
    if (truncated) break;
    frontier = [...next];
    level += 1;
  }
  if (truncated) {
    notes.push(
      `the crawl stopped at ${visited.length} page(s) — its ceiling, not the site's edge, so the ` +
        `inventory is a bounded sample and every count in it is a lower bound`,
    );
  }
  return { visited, notes, truncated };
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Fetches one page and scans it, or explains why it could not.
 *
 * <p>Only `text/html` is scanned. A link to a PDF is already recorded as an asset by whichever page
 * referenced it, and running an HTML scanner over a binary would invent findings.
 */
async function visit(url, { page, flags, isSeed }, ctx) {
  if (page) {
    return renderOne(url, page, flags);
  }
  let response;
  try {
    response = await ctx.fetch(url.href, {
      headers: { "User-Agent": USER_AGENT, Accept: "text/html,application/xhtml+xml" },
      redirect: "manual",
    });
  } catch (error) {
    return { status: 0, error: `fetch failed: ${error?.message ?? error}` };
  }
  const status = response.status ?? 0;
  if (status >= 300 && status < 400) {
    const location = response.headers?.get?.("location");
    const target = location ? resolveHref(location, url.href) : null;
    if (isSeed && target && !sameOrigin(target, url)) {
      throw new Error(
        `shio clone: ${url.href} redirects to ${target.origin}, which is a different host.\n` +
          `A capture recorded under an origin that does not serve it would put every path in the\n` +
          `inventory under a host nobody named. Crawl the one it points at instead:\n` +
          `  shio clone ${target.origin}`,
      );
    }
    if (!target || !sameOrigin(target, url)) {
      return { status, error: `redirects off-origin to ${location ?? "nowhere"}` };
    }
    // One hop, followed by hand so the redirect is visible in the inventory rather than erased by
    // the fetch layer.
    return {
      ...(await visit(target, { page: null, flags, isSeed: false }, ctx)),
      redirectedTo: pathOf(target),
    };
  }
  if (!response.ok) {
    return { status, error: `HTTP ${status}` };
  }
  const contentType = response.headers?.get?.("content-type") ?? "";
  if (contentType && !/text\/html|application\/xhtml/i.test(contentType)) {
    return { status, contentType, error: `not html (${contentType.split(";")[0]})` };
  }
  const html = await response.text();
  return { status, contentType, doc: scanPage(html, url.href) };
}

/** The `--render` path: the same scanner, over the DOM the page's own JavaScript produced. */
async function renderOne(url, page, flags) {
  try {
    await page.goto(url.href, { waitUntil: "networkidle", timeout: Number(flags.timeout ?? 30000) });
    const html = await page.content();
    return { status: 200, doc: scanPage(html, url.href), rendered: true };
  } catch (error) {
    return { status: 0, error: `render failed: ${error?.message ?? error}` };
  }
}

/**
 * The browser, when `--render` asks for one.
 *
 * <p>Optional and dynamic for SH102's reason: `@viglet/shio` is installed to author content, and a
 * crawl of a server-rendered site — which is most of them — needs no browser at all. `ctx.render` is
 * the seam the tests use.
 */
async function renderer(flags, ctx) {
  if (typeof ctx.render === "function") {
    return ctx.render(flags);
  }
  let playwright;
  try {
    playwright = await import("playwright");
  } catch {
    throw new Error(
      "shio clone: --render needs playwright, which is not installed.\n" +
        "  pnpm add -D playwright && npx playwright install chromium\n" +
        "Without it the crawl reads the HTML the server sent, which is the whole page on a " +
        "server-rendered site — the inventory says which of the two it got, and warns when a page " +
        "looks like it renders itself.",
    );
  }
  const browser = await playwright.chromium.launch();
  const page = await browser.newPage({
    viewport: { width: 1280, height: 800 },
    userAgent: USER_AGENT,
    reducedMotion: "reduce",
    timezoneId: "UTC",
  });
  page.close = async () => {
    await browser.close();
  };
  return page;
}
