/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
  assetsReferencedBy,
  describeDownloads,
  placeAsset,
  planAssets,
  withDownloads,
} from "../clone-assets.mjs";
import {
  DEFAULT_DEPTH,
  DEFAULT_MAX_PAGES,
  INVENTORY_FILE,
  MARKUP_DIR,
  ROBOTS_AGENT,
  buildInventory,
  describeInventory,
  parseRobots,
  pathOf,
  planMarkup,
  resolveHref,
  robotsAllows,
  sameOrigin,
  scanPage,
  serializeInventory,
  settleRendered,
} from "../clone.mjs";
import { cloneProgress } from "../clone-progress.mjs";
import { migrateInventory } from "../inventory-migrate.mjs";
import { requireBrowser } from "../browser.mjs";
import { ASSETS_DIR } from "../content-assets.mjs";
import { assetPathCollisions, siteDir } from "../content-files.mjs";
import { helpText } from "../help.mjs";

const USER_AGENT = `${ROBOTS_AGENT} (+https://github.com/openviglet/shio)`;

/**
 * How a download's action reads as progress (SH543). A dedupe is not a failure and not work either:
 * counting it `ok` would make a run of identical assets look like a productive one.
 */
const OUTCOME_OF_ACTION = {
  wrote: "ok",
  failed: "failed",
  collision: "failed",
  deduped: "skipped",
  skipped: "skipped",
};

/**
 * What `shio clone --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("clone");

export async function run(flags, positionals, ctx) {
  const out = typeof flags.out === "string" ? flags.out : INVENTORY_FILE;
  // SH592 — the prior capture is read **first**, because under `--refresh` it is what says where the
  // site is. A caller re-checking a capture they already have should not have to retype the origin,
  // and a mistyped one would silently refresh against a different host.
  const refreshing = flags.refresh === true;
  const prior = refreshing ? await readPriorCapture(out, ctx) : null;

  const seed = flags.url ?? positionals[0] ?? prior?.inventory?.source?.seed
    ?? prior?.inventory?.source?.origin;
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

  // SH434 — `--render` needs a browser, and the refusal for a missing one belongs here for exactly
  // the reason the SH378 comment above gives about `--assets`: before any request at all, including
  // robots.txt. It used to be resolved when the crawl asked for its first page, so a run that could
  // never succeed had already fetched from somebody else's server, and then exited with a live
  // handle armed — which is where `exit(1)` was measured aborting instead of exiting.
  const browser = rendered && typeof ctx.render !== "function"
    ? await requireBrowser({
      command: "clone",
      needs: "--render",
      why: "Without it the crawl reads the HTML the server sent, which is the whole page on a "
        + "server-rendered site — the inventory says which of the two it got, and warns when a page "
        + "looks like it renders itself.",
    }, ctx)
    : null;

  const robots = flags.ignoreRobots === true
    ? { present: false, matched: null, sitemaps: [], crawlDelay: null, rules: null }
    : await readRobots(origin, ctx);
  if (flags.ignoreRobots === true) {
    notes.push("--ignore-robots was passed: this capture did not read the site's robots.txt at all");
  }

  // SH379 — the markup cache. Opt-in like `--assets`, and for the same reason: SH377's default is
  // that a capture writes one file you can read before anything else happens. Unlike `--assets` it
  // costs no extra request — the bytes were already in hand — so the flag buys a *decision* about
  // keeping a stranger's HTML in the tree, not a second pass over their server.
  const keepMarkup = flags.markup === true;

  // SH543 — opened before the browser is driven and after the ceilings are known, because the
  // ceilings are half of what it reports: a run 13 pages into 200 at 30 s each is a decision to
  // make now rather than in ninety minutes.
  const progress = cloneProgress({
    seed: String(seed),
    ceiling: { depth, maxPages, rendered, timeoutMs: Number(flags.timeout ?? 30000) },
    flags,
  }, ctx);

  const page = rendered ? await renderer(flags, ctx, browser) : null;
  try {
    const walk = refreshing
      ? await refresh({ origin, robots, page, flags, keepMarkup, progress, prior }, ctx)
      : await crawl({ origin, depth, maxPages, robots, page, flags, keepMarkup, progress }, ctx);
    notes.push(...walk.notes);
    // SH586 — the crawl's own origin, so a source's absolute link to itself is cached as the path
    // it already means. Without it a fidelity replica serves those hrefs verbatim and every page
    // links back to the site it copied.
    const markup = keepMarkup ? planMarkup(walk.visited, origin.origin) : null;
    if (markup) await writeMarkup(markup, ctx);
    let inventory = buildInventory({
      origin: origin.origin,
      seed: String(seed),
      visited: walk.visited,
      robots,
      options: refreshing
        // The ceilings of the run being refreshed, not this one's: a refresh walks what was
        // captured, so reporting `--depth 3` here would describe a walk that did not happen and
        // would quietly widen the ceiling a truncated capture recorded.
        ? { ...(prior.inventory.options ?? {}), rendered, refreshed: true, markup }
        : { depth, maxPages, rendered, truncated: walk.truncated, markup },
      notes,
    });
    if (site) {
      inventory = await download(inventory, { site, flags, progress }, ctx);
    }

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
    const exitCode = inventory.pages.length ? 0 : 1;
    // SH543 — the terminal write, and the only reason a poller can stop. `done` is the command's own
    // reckoning, so a capture that reached nothing settles `failed` here as well as exiting 1.
    await progress.settle(exitCode === 0 ? "done" : "failed",
      { pages: inventory.pages.length, out, exitCode });
    return { inventory, out, exitCode };
  } catch (error) {
    // A status file left saying `running` after the process is gone is the exact ambiguity this
    // channel exists to remove — so a throw is recorded before it is re-thrown.
    await progress.settle("failed", { error: error?.message ?? String(error) });
    throw error;
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
 * Writes the markup cache (SH379).
 *
 * <p>One file per page, named from the path so the next crawl overwrites the same file and the diff
 * is what the source changed. A collision is not written at all — `planMarkup` reports it and the
 * inventory carries the note, because a page's markup filed under another page's path is worse than
 * a missing one: everything downstream would read it as that page's own.
 */
async function writeMarkup(markup, ctx) {
  const dir = join(ctx.cwd, MARKUP_DIR);
  if (!markup.files.length) return;
  await ctx.fs.mkdir(dir, { recursive: true });
  for (const item of markup.files) {
    await ctx.fs.writeFile(join(dir, item.file), item.html, "utf8");
  }
}

/**
 * Downloads what the inventory names, and folds the outcome back into it.
 *
 * <p>Sequential like the crawl, and for one of the same reasons: this is somebody else's server, and
 * forty parallel connections to it is a load decision made on their behalf. It also keeps the dedupe
 * deterministic — with concurrency, *which* of two identical assets becomes the file on disk depends
 * on which response arrived first (P5).
 */
async function download(inventory, { site, flags, progress }, ctx) {
  const maxAssets = whole(flags.maxAssets, DEFAULT_MAX_ASSETS, "--max-assets", 1);
  const maxBytes = whole(flags.maxAssetBytes, DEFAULT_MAX_ASSET_BYTES, "--max-asset-bytes", 1);
  const origin = inventory?.source?.origin ?? "";
  // SH469 — behaviour is opt-in and its absence is not. See `BEHAVIOUR` in clone-assets.mjs.
  const scripts = flags.scripts === true;
  const { plan, skipped, external } = planAssets(inventory, { site, maxAssets, scripts });

  // SH408's question, asked of this root before anything is written: two source URLs whose names
  // differ only in case are one file on macOS and Windows, and the second would overwrite the first
  // with no digest ever compared.
  const collisions = assetPathCollisions(plan.map((item) => item.file));
  const results = [];
  const state = { byDigest: new Map(), byPath: new Map() };
  // SH452 — the rows a stylesheet's `url()` calls add. Held here rather than written into the
  // inventory as they are found, because `withDownloads` folds outcomes onto `assets` and a row
  // that appeared halfway through the loop would have no outcome for half of it.
  const discovered = [];
  const known = new Set((inventory?.assets ?? []).map((asset) => asset.path));
  // SH543 — a second phase, and it needs its own ceiling: `plan.length` is not `maxPages` and a
  // status that carried the crawl's total into the download would read as a run going backwards.
  // The total grows as stylesheets append to the plan, which is honest — the phase really did get
  // longer — and is why it is re-reported on every step rather than fixed here.
  progress?.phase("assets", plan.length);
  // Every path below pushes exactly one row per item, so the step rides on the push rather than
  // being repeated at each `continue` — one place that maps an action onto an outcome, and no way
  // for a later branch to be added without one.
  const record = (row) => {
    results.push(row);
    progress?.step({
      path: row.file,
      total: plan.length,
      outcome: OUTCOME_OF_ACTION[row.action] ?? "skipped",
      action: row.action,
      ...(row.detail ? { detail: row.detail } : {}),
    });
  };
  // Indexed rather than `for…of`: a stylesheet appends to this list while it is being walked, which
  // is the whole mechanism — a stylesheet that imports another is followed by the same loop.
  for (let at = 0; at < plan.length; at += 1) {
    const item = plan[at];
    if (collisions.has(item.file)) {
      const group = collisions.get(item.file);
      record({
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
      record({ action: "failed", source: item.source, file: item.file, detail: fetched.error });
      continue;
    }
    const placed = placeAsset(state, {
      source: item.source,
      file: item.file,
      bytes: fetched.bytes,
      contentType: fetched.contentType,
      kind: item.kind,
    });
    record(placed);
    if (placed.action !== "wrote") continue;
    const target = join(ctx.cwd, ASSETS_DIR, item.file);
    await ctx.fs.mkdir(dirname(target), { recursive: true });
    await ctx.fs.writeFile(target, fetched.bytes);

    // SH452 — and what this stylesheet itself references. Only a stylesheet that was actually
    // written: a deduped one is the same bytes under another path and its references are already
    // queued, and a failed one has nothing to read.
    if (item.kind !== "stylesheet") continue;
    const rows = assetsReferencedBy(decode(fetched.bytes), item.url, origin);
    for (const row of rows) {
      if (known.has(row.path)) continue;
      known.add(row.path);
      discovered.push(row);
      const queued = planAssets({ assets: [row], source: { origin } },
        { site, maxAssets: maxAssets - plan.length, scripts });
      plan.push(...queued.plan);
      skipped.push(...queued.skipped);
      for (const host of queued.external) {
        const seen = external.find((each) => each.host === host.host);
        if (seen) seen.refs += host.refs;
        else external.push(host);
      }
    }
  }
  return withDownloads({ ...inventory, assets: [...(inventory.assets ?? []), ...discovered] },
    { site, results, skipped, external });
}

/** A downloaded stylesheet's bytes as text. UTF-8 is what a stylesheet is, in practice and in spec. */
function decode(bytes) {
  return new TextDecoder("utf-8").decode(bytes);
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
async function crawl({ origin, depth, maxPages, robots, page, flags, keepMarkup = false, progress },
  ctx) {
  const now = typeof ctx.clock === "function" ? ctx.clock : Date.now;
  const visited = [];
  const seen = new Set();
  const notes = [];
  /** SH517 — what `--render` baked and this capture gave back, summed over the crawl. */
  const froze = {};
  let frontier = [pathOf(origin) === "/" ? new URL("/", origin).href : origin.href];
  seen.add(frontier[0]);
  let truncated = false;
  let level = 0;
  const delay = Number(flags.delay ?? robots?.crawlDelay ?? 0) * 1000;

  // SH543 — the ceiling, not the frontier, because the frontier is not known until the level above
  // it has been walked. `total` is therefore an upper bound and so is everything derived from it;
  // a crawl that ends at the site's edge finishes early rather than late, which is the safe way
  // round for a number somebody decides with. `--depth 0` is the one case where it is not a bound
  // but a fact — the seed and nothing else — and reporting 200 there is a wrong number, not a
  // cautious one: it read `1 of 200, 199 remaining` for a crawl that was already over.
  progress?.phase("crawl", depth === 0 ? 1 : maxPages);

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
      const startedMs = now();
      const fetched = await visit(
        url,
        { page, flags, keepMarkup, isSeed: level === 0 && visited.length === 0 },
        ctx,
      );
      visited.push({ ...fetched, depth: level, url: url.href });
      // SH543 — after the push, so `done` and `visited.length` are the same number and the ceiling
      // the status reports against is the one the loop above actually breaks on. The cost is here
      // and nowhere else: the inventory keeps a page's outcome but not what it took to get it, and
      // "every page is spending the full 30 s timeout" is the finding that ends a doomed run early.
      progress?.step({
        path,
        depth: level,
        outcome: fetched.error ? "failed" : "ok",
        status: fetched.status ?? 0,
        ms: now() - startedMs,
        ...(fetched.rendered ? { rendered: true } : {}),
        ...(fetched.error ? { error: fetched.error } : {}),
      });
      // SH517 — accumulated as it goes, because the note below is the whole of "say what you
      // froze": a capture that silently gave a canvas back is the same undeclared cut as a
      // truncated crawl.
      for (const [what, count] of Object.entries(fetched.froze ?? {})) {
        froze[what] = (froze[what] ?? 0) + count;
      }
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
  const gave = Object.entries(froze).filter(([, count]) => count > 0);
  if (gave.length) {
    notes.push(
      `--render bakes the DOM a script produced, which is right for content and wrong for `
        + `machinery: ${gave.map(([what, count]) => `${count} ${FROZE_NAMES[what] ?? what}`).join(", ")} `
        + "were given back, so the replica is not frozen mid-animation",
    );
  }
  return { visited, notes, truncated };
}

/**
 * `--refresh` (SH592) — re-check the capture you already have, and pay only for what moved.
 *
 * <h2>The asymmetry</h2>
 *
 * <p>`shio clone` over `vilt-group.com` with `--render`: 198 pages, 403 assets, 52.7 MB, about ten
 * minutes. It is by far the most expensive step in the chain and everything after it reads the
 * inventory off disk and runs in seconds. Twice in this work that asymmetry cost a full recapture
 * for a reason that had nothing to do with the source. `inventory-migrate.mjs` answered one half —
 * a version bump is a migration where the missing field is derivable. This is the other half: the
 * source has not changed either, and nothing could ask.</p>
 *
 * <h2>What makes it cheap, and it is not a hash</h2>
 *
 * <p>Comparing digests still costs the download, and under `--render` the download is not the
 * expense — the browser is, at up to `--timeout` per page. So the saving has to come from the
 * server declining to send anything: a conditional `GET` carrying the `ETag` and `Last-Modified`
 * this capture recorded, answered <b>304</b>. A 304 page is never rendered, never re-scanned and
 * never rewritten.</p>
 *
 * <p>A 304 carries no body, so the row is rebuilt from the <b>markup already on disk</b> — which is
 * SH592's own insight applied twice: the information was already in the file. That is why
 * `--markup` is a hard precondition rather than a nicety, and why the refusal for a capture without
 * it names the flag.</p>
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p><b>It does not walk for new pages.</b> A refresh re-fetches what was captured; a link that
 * appeared on a changed page is *counted and named in the notes* and not followed, because
 * following it is a crawl and a crawl is the thing being avoided. Reporting it is not optional —
 * a refreshed inventory that silently omitted new pages would read as a complete capture, which is
 * the SH431 failure exactly.</p>
 *
 * <p><b>It re-downloads assets by page, not by byte.</b> An asset is re-fetched when the page that
 * references it changed. An asset that moved under an unchanged page is therefore missed, and the
 * report says so and names `shio clone` as the answer — an honest gap beats a silent one.</p>
 */
async function refresh({ origin, robots, page, flags, keepMarkup, progress, prior }, ctx) {
  const now = typeof ctx.clock === "function" ? ctx.clock : Date.now;
  const visited = [];
  const notes = [];
  const froze = {};
  const delay = Number(flags.delay ?? robots?.crawlDelay ?? 0) * 1000;
  const rows = [...(prior.inventory.pages ?? [])].sort((a, b) => (a.path < b.path ? -1 : 1));
  const known = new Set(rows.map((row) => row.path));
  const appeared = new Set();
  const counts = { unchanged: 0, changed: 0, gone: 0, unconditional: 0, carriedRender: 0 };

  progress?.phase("refresh", rows.length);

  for (const row of rows) {
    const url = new URL(row.path, origin.origin);
    const path = pathOf(url);
    // Re-asked, not assumed. A site that tightened its robots.txt since the capture has withdrawn
    // permission for exactly these paths, and a refresh is a new set of requests.
    if (!robotsAllows(robots, path)) {
      notes.push(`robots.txt now disallows ${path}, so this refresh dropped it from the capture`);
      counts.gone += 1;
      continue;
    }
    const conditional = { etag: row.etag ?? null, lastModified: row.lastModified ?? null };
    if (!conditional.etag && !conditional.lastModified) counts.unconditional += 1;

    const startedMs = now();
    // Always the plain fetch first, even under `--render`. That IS the saving: a 304 here is a
    // browser that never launched.
    const probe = await visit(
      url,
      { page: null, flags, keepMarkup, isSeed: false, conditional },
      ctx,
    );

    const cached = prior.markup.get(path);
    let fetched;
    let outcome;
    if (probe.unchanged && cached !== undefined) {
      fetched = {
        status: row.status ?? 200,
        doc: scanPage(cached, url.href),
        ...(row.rendered ? { rendered: true } : {}),
        ...(keepMarkup ? { html: cached } : {}),
        ...(probe.etag ? { etag: probe.etag } : {}),
        ...(probe.lastModified ? { lastModified: probe.lastModified } : {}),
      };
      counts.unchanged += 1;
      if (row.rendered) counts.carriedRender += 1;
      outcome = "unchanged";
    } else if (probe.unchanged) {
      // 304, and no markup to rebuild from. Asking again without the conditional headers is the
      // only honest move: the alternative is a row carried forward on the strength of a body this
      // capture never held, which is a claim about bytes nobody has read.
      fetched = await visit(url, { page, flags, keepMarkup, isSeed: false }, ctx);
      counts.changed += 1;
      outcome = "refetched";
    } else if (page && probe.doc) {
      // Changed, and this run wants the rendered DOM. The probe's body is discarded on purpose —
      // it is the served HTML and `--render` exists because that is not what the page shows.
      fetched = await visit(url, { page, flags, keepMarkup, isSeed: false }, ctx);
      counts.changed += 1;
      outcome = "changed";
    } else {
      fetched = probe;
      if (probe.error || probe.notHtml) {
        counts.gone += 1;
        outcome = "gone";
      } else {
        counts.changed += 1;
        outcome = "changed";
      }
    }

    visited.push({ ...fetched, depth: row.depth ?? 0, url: url.href });
    progress?.step({
      path,
      depth: row.depth ?? 0,
      outcome: fetched.error ? "failed" : "ok",
      status: fetched.status ?? 0,
      ms: now() - startedMs,
      action: outcome,
      ...(fetched.rendered ? { rendered: true } : {}),
      ...(fetched.error ? { error: fetched.error } : {}),
    });
    for (const [what, count] of Object.entries(fetched.froze ?? {})) {
      froze[what] = (froze[what] ?? 0) + count;
    }
    if (outcome !== "unchanged" && fetched.doc) {
      for (const link of fetched.doc.links) {
        const resolved = resolveHref(link.href, url.href);
        if (!resolved || !sameOrigin(resolved, origin)) continue;
        const target = pathOf(resolved);
        if (!known.has(target)) appeared.add(target);
      }
    }
    if (delay > 0) await sleep(delay);
  }

  notes.push(
    `--refresh re-checked ${rows.length} captured page(s): ${counts.unchanged} unchanged (304), `
      + `${counts.changed} re-read, ${counts.gone} no longer captured`,
  );
  if (counts.unconditional) {
    notes.push(
      `${counts.unconditional} page(s) carried no ETag and no Last-Modified, so they were fetched in `
        + "full — this run recorded what the server sent, and the next --refresh can ask about them "
        + "conditionally",
    );
  }
  if (counts.carriedRender) {
    notes.push(
      `${counts.carriedRender} page(s) were carried forward from a --render capture without driving `
        + "a browser, so any --render counters below describe only the pages that were re-read",
    );
  }
  if (appeared.size) {
    const sample = [...appeared].sort().slice(0, 5);
    notes.push(
      `${appeared.size} path(s) are linked from a page that changed and are NOT in this capture — a `
        + "refresh re-checks what was captured and does not walk for new pages, so this inventory is "
        + `still bounded by the crawl that made it: ${sample.join(" ")}${appeared.size > sample.length ? " …" : ""}`
        + " — run `shio clone` to extend it",
    );
  }
  if (counts.unchanged && prior.inventory.assets?.length) {
    notes.push(
      "assets are re-downloaded per page, so one that changed under a page that did not is not "
        + "re-fetched by --refresh; `shio clone` is what re-reads every byte",
    );
  }
  return { visited, notes, truncated: prior.inventory.options?.truncated === true };
}

/**
 * The capture `--refresh` re-checks, plus the markup it needs to rebuild a 304 row (SH592).
 *
 * <p>Three refusals, and each one is a state where continuing would produce an inventory that reads
 * as a capture and is not one: no prior file at all, a version older than
 * `inventory-migrate.mjs` can bring forward, and a capture kept without `--markup`. The last is the
 * one worth being loud about — the whole mechanism is *rebuild the unchanged row from what is
 * already on disk*, and with nothing on disk a 304 would have to be answered by inventing a row.</p>
 */
async function readPriorCapture(out, ctx) {
  const target = join(ctx.cwd, out);
  let raw;
  try {
    raw = await ctx.fs.readFile(target, "utf8");
  } catch {
    throw new Error(
      `shio clone --refresh: there is no capture at ${out} to refresh.\n`
        + "  --refresh re-checks a capture you already have; it cannot make the first one.\n"
        + "  shio clone https://example.com --markup",
    );
  }
  let inventory;
  try {
    inventory = JSON.parse(raw);
  } catch (error) {
    throw new Error(`shio clone --refresh: ${out} is not readable JSON (${error.message}).`);
  }

  const markup = new Map();
  const missing = [];
  for (const row of inventory.pages ?? []) {
    if (!row.markup) {
      missing.push(row.path);
      continue;
    }
    try {
      markup.set(row.path, await ctx.fs.readFile(join(ctx.cwd, MARKUP_DIR, row.markup), "utf8"));
    } catch {
      missing.push(row.path);
    }
  }
  if ((inventory.pages ?? []).length && missing.length === (inventory.pages ?? []).length) {
    throw new Error(
      `shio clone --refresh: ${out} kept no page markup, and --refresh rebuilds an unchanged page\n`
        + "from the markup on disk rather than by asking for it again. Without it every 304 would\n"
        + "have to be answered with a row nobody read.\n"
        + `  shio clone ${inventory.source?.origin ?? "https://example.com"} --markup`,
    );
  }

  // The two halves of SH592 compose here: a capture one version behind is brought forward from the
  // markup this function has just read, so `--refresh` is also the cheapest way to pay off a bump.
  const migration = migrateInventory(inventory, markup);
  return { inventory: migration.inventory, markup, migrated: migration.migrated, from: migration.from };
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** What each SH517 counter is, in a sentence a reader can act on. */
const FROZE_NAMES = {
  canvas: "canvas element(s) a script had created",
  style: "empty style attribute(s) a script left behind",
  classes: "framework state class(es) caught mid-scroll",
};

/**
 * Fetches one page and scans it, or explains why it could not.
 *
 * <p>Only `text/html` is scanned. A link to a PDF is already recorded as an asset by whichever page
 * referenced it, and running an HTML scanner over a binary would invent findings.
 */
/**
 * How many redirects one URL may be followed through (SH569).
 *
 * <p>Five, because a real source chains two or three legitimately — `/a` → `/a/` → the canonical
 * host — and a budget tight enough to refuse those would report a working page as broken. What it
 * has to stop is the cycle, and a cycle exceeds any budget.</p>
 */
const MAX_REDIRECT_HOPS = 5;

async function visit(url, { page, flags, keepMarkup, isSeed, conditional, hops = 0 }, ctx) {
  if (page) {
    return renderOne(url, page, flags, keepMarkup);
  }
  let response;
  try {
    response = await ctx.fetch(url.href, {
      headers: {
        "User-Agent": USER_AGENT,
        Accept: "text/html,application/xhtml+xml",
        ...conditionalHeaders(conditional),
      },
      redirect: "manual",
    });
  } catch (error) {
    return { status: 0, error: `fetch failed: ${error?.message ?? error}` };
  }
  const status = response.status ?? 0;
  // SH592 — before `!response.ok`, which would otherwise read the one answer this whole flag exists
  // to get as `HTTP 304`. A 304 carries no body by definition, so there is nothing here to scan;
  // what it means is "the row you already have is still true", and only the caller knows what that
  // row was.
  if (status === 304) {
    return { status, unchanged: true, ...validatorsOf(response, conditional) };
  }
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
    // SH569 — the hop budget the comment below always claimed and the code never had. This
    // recursed with no counter, so a source answering `/loop` with `302 → /loop` spun until the
    // process died: the corpus's `hostile` archetype hit it on its first run, which is the whole
    // argument for an archetype that answers requests badly. A chain is legitimate (`/a` → `/a/` →
    // canonical), so the answer is a small budget rather than a single hop, and exceeding it is a
    // reported row — never a crash and never a silent omission.
    if (hops >= MAX_REDIRECT_HOPS) {
      return { status, error: `redirect loop: more than ${MAX_REDIRECT_HOPS} hops from ${pathOf(url)}` };
    }
    // Followed by hand so the redirect is visible in the inventory rather than erased by the
    // fetch layer.
    return {
      ...(await visit(target, { page: null, flags, keepMarkup, isSeed: false, hops: hops + 1 }, ctx)),
      redirectedTo: pathOf(target),
    };
  }
  if (!response.ok) {
    return { status, error: `HTTP ${status}` };
  }
  const contentType = response.headers?.get?.("content-type") ?? "";
  if (contentType && !/text\/html|application\/xhtml/i.test(contentType)) {
    // SH431 — a distinct outcome, and deliberately **not** an `error`. A 200 of application/json is
    // the site's data surface, linked on purpose and served correctly: it is not a page, and the
    // crawl did not fail on it. Filing it as an error put 24 of `openviglet.github.io`'s data files
    // into `problems`, where the coverage gate reads them as "source page(s) could not be read at
    // all" and downgrades every count in the report to a lower bound on the strength of them.
    return { status, contentType, notHtml: contentType.split(";")[0].trim() };
  }
  const html = await response.text();
  // `html` is carried only when `--markup` asked for it: holding 200 pages of somebody else's HTML
  // in memory for a run that will not write it is a cost nobody bought.
  return {
    status,
    contentType,
    doc: scanPage(html, url.href),
    ...validatorsOf(response),
    ...(keepMarkup ? { html } : {}),
  };
}

/**
 * The request headers that let a server answer 304 (SH592).
 *
 * <p>Both, when both are known. `If-None-Match` is the strong one and `If-Modified-Since` is the
 * fallback for a server that sends no ETag — sending both is what RFC 9110 says a cache does, and
 * a server that understands only one ignores the other.</p>
 */
function conditionalHeaders(conditional) {
  if (!conditional) return {};
  return {
    ...(conditional.etag ? { "If-None-Match": conditional.etag } : {}),
    ...(conditional.lastModified ? { "If-Modified-Since": conditional.lastModified } : {}),
  };
}

/**
 * What this response says about being asked for again — carried onto the row (SH592).
 *
 * <p>Deliberately **additive and optional**, and deliberately not an inventory version bump. SH592's
 * own rule is that a bump is a migration or a refusal; this is neither, because no reader needs
 * these fields. A capture written before this exists simply has none, and its first `--refresh` is
 * unconditional — which is the honest degradation and not a refusal, since the information really
 * was never recorded.</p>
 *
 * <p>On a 304 the server is not obliged to repeat them, so the ones we sent are carried forward:
 * dropping them would make the *next* refresh unconditional again and turn one cheap request into a
 * full re-fetch every other run.</p>
 */
function validatorsOf(response, previous = null) {
  const etag = response.headers?.get?.("etag") ?? previous?.etag ?? null;
  const lastModified = response.headers?.get?.("last-modified") ?? previous?.lastModified ?? null;
  return {
    ...(etag ? { etag } : {}),
    ...(lastModified ? { lastModified } : {}),
  };
}

/** A playwright response's headers, behind the `get` the rest of this file reads them through. */
function headersOf(response) {
  const raw = typeof response?.headers === "function" ? response.headers() : null;
  if (!raw) return { get: () => null };
  const lower = new Map(Object.entries(raw).map(([name, value]) => [name.toLowerCase(), value]));
  return { get: (name) => lower.get(String(name).toLowerCase()) ?? null };
}

/** The `--render` path: the same scanner, over the DOM the page's own JavaScript produced. */
async function renderOne(url, page, flags, keepMarkup) {
  try {
    const response = await page.goto(url.href,
      { waitUntil: "networkidle", timeout: Number(flags.timeout ?? 30000) });
    // SH517 — the *unrendered* body, which is the evidence for what a script created rather than
    // what the page was served with. It costs no request: these are the bytes the navigation has
    // already transferred, and `page.content()` below is the DOM after they ran.
    const source = await response?.text?.().catch(() => "") ?? "";
    const { html, froze } = settleRendered(await page.content(), source);
    return {
      status: 200,
      doc: scanPage(html, url.href),
      rendered: true,
      froze,
      // SH592 — playwright hands headers back as a plain object rather than a `Headers`, so the
      // shared reader is given a `get` to call. Worth doing: `--render` is the expensive path and
      // therefore the one whose next run has most to save by asking conditionally.
      ...validatorsOf({ headers: headersOf(response) }),
      ...(keepMarkup ? { html } : {}),
    };
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
 *
 * <p>The module arrives as an argument (SH434): `run` resolves it before the crawl starts, so a
 * missing one is refused before this command touches the network.
 */
async function renderer(flags, ctx, playwright) {
  if (typeof ctx.render === "function") {
    return ctx.render(flags);
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
