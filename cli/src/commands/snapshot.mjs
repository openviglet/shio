/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio snapshot` (SH102) — a page's PNG, and whether it looks different from last time.
 *
 * ## Playwright is optional, and that is not a compromise
 *
 * `@viglet/shio` is installed to author content; making it pull a browser-automation library
 * (and then a browser) would tax every install for a command §IX.4 itself calls optional. So
 * `playwright` is imported **dynamically**, and its absence is a teaching error naming the two
 * commands that fix it (P6) rather than a stack trace. The same applies to `pixelmatch` and
 * `pngjs`: with them the comparison is per-pixel, without them it is byte-level and says so.
 *
 * ## Determinism is the feature, not the polish
 *
 * A screenshot comparison whose baseline moves on its own is worse than no comparison: it
 * reports a change every run, a reader learns the output is noise, and then a real regression
 * goes unread. So the capture pins the viewport, disables animations and transitions, waits for
 * fonts, and scrolls the page once to settle lazy content. Everything here that looks like
 * fussiness is one of those.
 */
import { dirname, join } from "node:path";

import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { SNAPSHOT_DIR, compareSnapshots, describe, resolveUrl, slugFor } from "../snapshot.mjs";

export async function run(flags, positionals, ctx) {
  const target = flags.address ?? flags.url ?? positionals[0];
  if (!target) {
    throw new Error(
      "shio snapshot: pass a page — post:<site>/<url>, or an absolute URL.\n" +
        "Example: shio snapshot post:Blog/blog/hello-world",
    );
  }

  const sites = /^https?:\/\//.test(String(target)) ? new Map() : await siteUrls(flags, ctx);
  const resolved = resolveUrl(target, sites);
  if (resolved.error) {
    throw new Error(`shio snapshot: ${resolved.error}`);
  }

  const slug = slugFor(target);
  const dir = typeof flags.out === "string" ? flags.out : SNAPSHOT_DIR;
  const path = join(dir, `${slug}.png`);
  const diffPath = join(dir, `${slug}.diff.png`);

  const shot = await capture(resolved.url, flags, ctx);
  const before = await readIfPresent(ctx, path);
  const result = compareSnapshots(before, shot, { compare: await comparator(ctx) });

  await write(ctx, path, shot, result, flags);
  let wroteDiff = null;
  if (result.diff) {
    await write(ctx, diffPath, result.diff, result, { ...flags, accept: true });
    wroteDiff = diffPath;
  }

  ctx.stdout.write(`${describe(result, { slug, path, diffPath: wroteDiff })}\n`);
  if (result.status === "changed" && !flags.accept) {
    // The previous image is kept beside the new one, because the artifact a human wants is
    // "before and after", not "after plus a percentage".
    ctx.stdout.write(`  previous: ${join(dir, `${slug}.prev.png`)}\n`);
  }

  // A visual change is not a defect — it is usually the change you just made (§IX.4). The gate
  // is opt-in so that the command producing the evidence is not also the command people wrap
  // in `|| true`.
  const failed = Boolean(flags.failOnChange) && result.status === "changed";
  return { slug, url: resolved.url, path, ...result, diff: undefined, exitCode: failed ? 1 : 0 };
}

/**
 * Writes the capture, keeping the displaced baseline as `<slug>.prev.png`.
 *
 * <p>Overwriting silently would destroy the only copy of what the page used to look like at the
 * exact moment somebody wants to see it.
 */
async function write(ctx, path, bytes, result, flags) {
  await ctx.fs.mkdir(dirname(path), { recursive: true });
  if (result.status === "changed" && !flags.accept) {
    const previous = path.replace(/\.png$/, ".prev.png");
    const existing = await readIfPresent(ctx, path);
    if (existing) {
      await ctx.fs.writeFile(previous, existing);
    }
  }
  await ctx.fs.writeFile(path, bytes);
}

async function readIfPresent(ctx, path) {
  try {
    return await ctx.fs.readFile(path);
  } catch (error) {
    if (error?.code === "ENOENT") return null;
    throw error;
  }
}

/** Site name → public base URL, from the context pack's `sites` section (as `shio report`). */
async function siteUrls(flags, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const sites = new Map();
  try {
    const { body } = await client.getContext({ include: "sites", format: "json" });
    for (const site of JSON.parse(body).sites ?? []) {
      if (site?.name) sites.set(site.name, site.url ?? "");
    }
  } catch {
    // Left empty on purpose: resolveUrl then reports "not a site this instance reports", which
    // is a better error than a fabricated origin.
  }
  return sites;
}

/**
 * Takes the picture.
 *
 * <p>`ctx.capture` is the seam the tests use: a browser in a unit test would be testing
 * Playwright, and the parts of this command worth pinning are the comparison, the baseline
 * rotation and the exit code.
 */
async function capture(url, flags, ctx) {
  if (typeof ctx.capture === "function") {
    return ctx.capture(url, flags);
  }
  const playwright = await load("playwright", ctx);
  const width = Number(flags.width ?? 1280);
  const height = Number(flags.height ?? 800);
  const browser = await playwright.chromium.launch();
  try {
    const page = await browser.newPage({
      viewport: { width, height },
      deviceScaleFactor: 1,
      // A caret blinking in a focused field, or a date rendered in the runner's timezone, are
      // both changes nobody made.
      reducedMotion: "reduce",
      timezoneId: "UTC",
    });
    await page.goto(url, { waitUntil: "networkidle", timeout: Number(flags.timeout ?? 30000) });
    await page.addStyleTag({
      content:
        "*,*::before,*::after{animation:none!important;transition:none!important;" +
        "caret-color:transparent!important;scroll-behavior:auto!important}",
    });
    // Lazy images below the fold load on scroll; a snapshot taken before they do differs from
    // one taken after for reasons that have nothing to do with the content.
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.evaluate(() => document.fonts?.ready);
    return await page.screenshot({ fullPage: flags.viewportOnly !== true, type: "png" });
  } finally {
    await browser.close();
  }
}

/** The per-pixel comparator, when both optional helpers resolve. */
async function comparator(ctx) {
  if (typeof ctx.compare === "function") {
    return ctx.compare;
  }
  const pixelmatch = await optional("pixelmatch");
  const pngjs = await optional("pngjs");
  if (!pixelmatch || !pngjs) {
    return undefined;
  }
  const match = pixelmatch.default ?? pixelmatch;
  const { PNG } = pngjs;
  return (before, after) => {
    const a = PNG.sync.read(Buffer.from(before));
    const b = PNG.sync.read(Buffer.from(after));
    // Different dimensions is a change by definition, and pixelmatch refuses to compare them —
    // so it is answered here rather than thrown.
    if (a.width !== b.width || a.height !== b.height) {
      return { ratio: 1, diff: null };
    }
    const diff = new PNG({ width: a.width, height: a.height });
    const pixels = match(a.data, b.data, diff.data, a.width, a.height, { threshold: 0.1 });
    return { ratio: pixels / (a.width * a.height), diff: PNG.sync.write(diff) };
  };
}

async function optional(name) {
  try {
    return await import(name);
  } catch {
    return null;
  }
}

/** A required optional dependency: absent is a teaching error, not a stack trace. */
async function load(name, ctx) {
  const loaded = await optional(name);
  if (loaded) {
    return loaded;
  }
  throw new Error(
    `shio snapshot: this command needs ${name}, which is not installed.\n` +
      `  pnpm add -D ${name} && npx playwright install chromium\n` +
      `It is optional because a screenshot is for a human to look at (SH102) — the agent's own ` +
      `check is \`shio digest\`, which needs no browser and costs a fraction of the tokens.` +
      (ctx?.env?.SHIO_DEBUG ? `\n(resolved from ${process.cwd()})` : ""),
  );
}
