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
    const inventory = buildInventory({
      origin: origin.origin,
      seed: String(seed),
      visited: walk.visited,
      robots,
      options: { depth, maxPages, rendered, truncated: walk.truncated },
      notes,
    });

    const out = typeof flags.out === "string" ? flags.out : INVENTORY_FILE;
    const target = join(ctx.cwd, out);
    await ctx.fs.mkdir(dirname(target), { recursive: true });
    await ctx.fs.writeFile(target, serializeInventory(inventory), "utf8");

    if (flags.json) {
      ctx.stdout.write(serializeInventory(inventory));
    } else {
      ctx.stdout.write(describeInventory(inventory, { ...flags, out }));
    }
    // A capture that reached nothing is not a capture. Exiting 0 on it would let a CI step that
    // wraps this read "0 pages" as "a site with no pages".
    return { inventory, out, exitCode: inventory.pages.length ? 0 : 1 };
  } finally {
    await page?.close?.();
  }
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
