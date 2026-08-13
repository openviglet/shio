/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
import { LANDMARKS, readShape } from "../audit.mjs";
import { launchBrowser, requireBrowser } from "../browser.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";
import {
  DEFAULT_WIDTH,
  SNAPSHOT_DIR,
  againstNames,
  compareShapes,
  describeShapes,
  compareSnapshots,
  describe,
  describeAgainst,
  pageTarget,
  resolveUrl,
  slugFor,
  widthsOf,
} from "../snapshot.mjs";

/**
 * What `shio snapshot --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("snapshot");

export async function run(flags, positionals, ctx) {
  // SH365 — `--page`, not `--url`. `--url` is the instance here as everywhere else, and reading it
  // as the page as well is what let one flag decide which *server* a picture came from.
  // SH209 — `--site` is the other way to say what to photograph, so the refusal below only applies
  // when neither was given. Silent when `--site` is present: `pageTarget` writes its own advice to
  // stderr, and advice about a flag the caller deliberately did not use is noise.
  const wholeSite = typeof flags.site === "string" && flags.site !== "";
  const target = pageTarget(flags, positionals, (message) => {
    if (!wholeSite) ctx.stderr.write(`shio snapshot: ${message}\n`);
  });
  if (!target && !wholeSite) {
    throw new Error(
      "shio snapshot: pass a page — post:<site>/<url>, or an absolute URL.\n" +
        "Example: shio snapshot post:Blog/blog/hello-world\n" +
        "Or photograph every published page of a site: shio snapshot --site Blog",
    );
  }
  if (target && wholeSite) {
    // Refused rather than resolved by precedence: one of them would be silently ignored, and which
    // one is exactly the sort of thing a caller finds out from the filenames afterwards.
    throw new Error(
      "shio snapshot: --site photographs every published page, so it cannot be combined with a\n" +
        "single page. Drop one:\n" +
        `  shio snapshot ${target}\n` +
        `  shio snapshot --site ${flags.site}`,
    );
  }

  // SH434 — the browser is resolved here, before a socket is opened. It used to be resolved inside
  // `capture`, which is *after* the instance has been asked for its sites: a run that could never
  // succeed printed its refusal while the process was still carrying live handles, and `exit(1)` was
  // measured aborting rather than exiting. Spending a round trip on a run that cannot work is the
  // smaller half of the same mistake.
  const playwright = typeof ctx.capture === "function" ? null : await requireBrowser({
    command: "snapshot",
    why: "It is optional because a screenshot is for a human to look at (SH102) — the agent's own "
      + "check is `shio digest`, which needs no browser and costs a fraction of the tokens.",
  }, ctx);

  if (wholeSite) {
    return withBrowser(playwright, ctx, (browser) => site(flags, ctx, browser));
  }
  return withBrowser(playwright, ctx, (browser) => onePage(target, flags, ctx, browser));
}

async function onePage(target, flags, ctx, browser) {
  const admin = resolveAdmin(flags, ctx.env);
  const sites = /^https?:\/\//.test(String(target)) ? new Map() : await siteUrls(flags, ctx);
  const resolved = resolveUrl(target, sites, {
    previewBase: admin.baseUrl,
    preview: flags.preview === true,
  });
  if (resolved.error) {
    throw new Error(`shio snapshot: ${resolved.error}`);
  }

  const slug = slugFor(target);
  const dir = typeof flags.out === "string" ? flags.out : SNAPSHOT_DIR;
  const widths = widthsOf(flags.width);

  if (typeof flags.against === "string" && flags.against) {
    return against(resolved, { slug, dir, widths, admin, flags, browser }, ctx);
  }
  if (widths.length > 1) {
    // Refused rather than quietly using the first, and rather than growing a baseline per width in
    // this mode. `<slug>.png` is a file every project already holds, so a second width here would
    // either orphan it or need a conditional name — the defect SH379 settled for the markup cache.
    // Comparing several widths is what `--against` exists for, and the message says so (P6).
    throw new Error(
      `shio snapshot: --width takes one viewport here, not ${widths.length}.\n` +
        "A baseline is one picture per page, and this mode compares a page against its own earlier\n" +
        "capture — a second width would need a second baseline nobody has.\n" +
        "Several widths are what --against is for, where both sides are captured in the same run:\n" +
        `  shio snapshot --page ${target} --against https://example.com/ --width ${widths.join(",")}`,
    );
  }
  const path = join(dir, `${slug}.png`);
  const diffPath = join(dir, `${slug}.diff.png`);

  // The preview is authenticated (SH359 closed it), so the browser needs the same credentials the
  // rest of the CLI uses. Passed only for the instance's own origin: handing a site's public host
  // an admin password because one command happens to photograph both would be a credential leak
  // nobody asked for.
  // The resolved width, not the raw flag: `widthsOf` is the one parser, so `capture` never has to
  // know that `--width` can be a list.
  // SH616 — through `shot()`, which `--against` has always used and this path never did. Since
  // SH526 `capture` returns `{bytes, shape}` in real mode and a bare Buffer only through the
  // `ctx.capture` seam the tests drive, so this handed a plain object to `writeFile`: every test
  // passed and `shio snapshot <page>` — the command's whole original purpose — threw
  // ERR_INVALID_ARG_TYPE against a real browser. A seam that differs in *shape* from production is
  // the one kind of double that can be green about something that cannot work.
  const bytes = shot(await capture(resolved.url, { ...flags, width: widths[0] }, ctx,
    resolved.preview ? { user: admin.user, password: admin.password } : null, browser)).bytes;
  const before = await readIfPresent(ctx, path);
  const result = compareSnapshots(before, bytes, { compare: await comparator(ctx) });

  await write(ctx, path, bytes, result, flags);
  let wroteDiff = null;
  if (result.diff) {
    await write(ctx, diffPath, result.diff, result, { ...flags, accept: true });
    wroteDiff = diffPath;
  }

  ctx.stdout.write(`${describe(result, { slug, path, diffPath: wroteDiff })}\n`);
  if (resolved.preview) {
    // Said out loud, because the picture is not of the site a visitor sees: it is the DRAFT
    // through Shio's own renderer, banner and all. A reader comparing it against production and
    // finding a stripe across the top should learn that here rather than from the image.
    ctx.stdout.write(`  preview: ${resolved.url} (DRAFT, with the preview banner)\n`);
  }
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
 * `--site <name>` — every published page, one baseline each (SH209).
 *
 * <h2>Why the set and not the page</h2>
 *
 * <p>SH102 photographs a page. The artifact a handoff or a nightly job wants is the <em>set</em>,
 * because that is the form in which a visual diff is worth reading: "these three pages changed" is
 * a sentence, and "this page changed" is a fact the person who changed it already knew.</p>
 *
 * <h2>The three properties, which are the actual work</h2>
 *
 * <p>The parts existed — {@code /agent/find} gives the addresses and {@code capture} takes the
 * picture — so what this adds is the loop's own behaviour. <b>One browser</b>, reused (see
 * {@link withBrowser}). <b>A per-page timeout</b>, so one page that never settles cannot consume
 * the run: {@code --timeout} already bounded a single navigation and now bounds each of many.
 * And <b>an unreachable page is never an unchanged one</b> — the SH141 lesson arriving in a second
 * place. A page that could not be photographed is reported as its own outcome and never scored,
 * because a failure to capture scoring as "no difference" is the same false green as SH450's
 * error page scoring 100%.</p>
 *
 * <p>Unreachable pages set a non-zero exit on their own, independently of {@code --fail-on-change}.
 * That flag is opt-in because a visual change is usually intended; a page the browser could not
 * open is not a change at all, and a run that could not do what it was asked must not exit 0.</p>
 */
async function site(flags, ctx, browser) {
  const admin = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({
    baseUrl: admin.baseUrl,
    user: admin.user,
    password: admin.password,
    fetch: ctx.fetch,
  });
  const sites = await siteUrls(flags, ctx);
  const dir = typeof flags.out === "string" ? flags.out : SNAPSHOT_DIR;
  const widths = widthsOf(flags.width);
  if (widths.length > 1) {
    throw new Error(
      `shio snapshot: --width takes one viewport here, not ${widths.length}.\n` +
        "A baseline is one picture per page, and this mode writes one baseline per page.",
    );
  }

  const found = await client.findAll({ site: flags.site, type: "Page" });
  // `published` is absent rather than false when a post has no live row (SH545), so this is a
  // positive test. A draft has no URL a visitor could open, and photographing the preview instead
  // would silently answer a different question — `--preview` is how a caller asks for that.
  const pages = (found.results ?? [])
    .filter((row) => row?.address && row.published === true)
    .map((row) => row.address)
    // Explicit comparator: which pages a run covered has to read the same every time
    // (P5), and the default sort's locale-free ordering is what that means here.
    .sort((left, right) => (left < right ? -1 : left > right ? 1 : 0));

  if (!pages.length) {
    ctx.stdout.write(`shio snapshot: no published page in '${flags.site}' to photograph.\n`);
    // Not an error: a site whose pages are all drafts is a normal state, and the sentence above
    // already distinguishes it from a run that photographed pages and found nothing changed.
    return { site: flags.site, pages: 0, results: [], exitCode: 0 };
  }
  if (found.truncated) {
    ctx.stdout.write(
      `  note: the page list was cut at ${pages.length} — this run does not cover the whole site.\n`,
    );
  }

  const results = [];
  for (const address of pages) {
    results.push(await onePageOfSite(address, sites, { admin, flags, dir, widths }, ctx, browser));
  }
  return summarize(flags.site, results, dir, flags, ctx);
}

/** One page of a site sweep: captured and scored, or recorded as unreachable. Never both. */
async function onePageOfSite(address, sites, { admin, flags, dir, widths }, ctx, browser) {
  const slug = slugFor(address);
  const resolved = resolveUrl(address, sites, {
    previewBase: admin.baseUrl,
    preview: flags.preview === true,
  });
  if (resolved.error) {
    return { address, slug, status: "unreachable", why: resolved.error };
  }
  const path = join(dir, `${slug}.png`);
  try {
    const captured = await capture(resolved.url, { ...flags, width: widths[0] }, ctx,
      resolved.preview ? { user: admin.user, password: admin.password } : null, browser);
    const bytes = shot(captured).bytes;
    const before = await readIfPresent(ctx, path);
    const result = compareSnapshots(before, bytes, { compare: await comparator(ctx) });
    await write(ctx, path, bytes, result, flags);
    if (result.diff) {
      await write(ctx, join(dir, `${slug}.diff.png`), result.diff, result, { ...flags, accept: true });
    }
    return { address, slug, path, status: result.status, ratio: result.ratio };
  } catch (error) {
    // Caught per page on purpose: a navigation timeout, a 404, a site whose front end is down.
    // Letting it propagate would end the sweep at the first bad page and lose every result after
    // it — the failure mode a set is supposed to remove.
    return { address, slug, status: "unreachable", why: error.message };
  }
}

/** What the sweep found, with the pages it could not reach kept apart from the ones that held. */
function summarize(name, results, dir, flags, ctx) {
  const by = (status) => results.filter((row) => row.status === status);
  const changed = by("changed");
  const unreachable = by("unreachable");
  const fresh = by("new");

  ctx.stdout.write(
    `shio snapshot: ${results.length} page(s) of '${name}' — ${changed.length} changed, ` +
      `${fresh.length} new, ${by("same").length} unchanged, ${unreachable.length} unreachable\n`,
  );
  for (const row of changed) {
    const ratio = typeof row.ratio === "number" ? ` (${(row.ratio * 100).toFixed(2)}% of pixels)` : "";
    ctx.stdout.write(`  changed  ${row.address}${ratio}\n    ${row.path}\n`);
  }
  for (const row of unreachable) {
    // Named with the reason, because "unreachable" without one is the row a reader has to
    // reproduce by hand to act on.
    ctx.stdout.write(`  UNREACHABLE  ${row.address}\n    ${row.why}\n`);
  }
  if (changed.length && !flags.accept) {
    ctx.stdout.write(`  previous images are beside each changed page in ${dir}\n`);
  }

  const failed = unreachable.length > 0
    || (Boolean(flags.failOnChange) && changed.length > 0);
  return {
    site: name,
    pages: results.length,
    changed: changed.length,
    unreachable: unreachable.length,
    results,
    exitCode: failed ? 1 : 0,
  };
}

/**
 * `--against <url>` — the replica against the original (SH386).
 *
 * <h2>Why SH102's comparison could not answer this</h2>
 *
 * <p>It answers <i>"did this page change since I last looked"</i>, and a replication's question is
 * <i>"does this page look like somebody else's"</i>. The appearance digest cannot answer it either, and
 * SH187 recorded why: there is no layout engine, so it covers the failure an <b>agent</b> creates and
 * not the one a <b>human</b> sees (SH265). A replica's whole risk is the second kind.
 *
 * <h2>Both sides in the same run, and the source is kept</h2>
 *
 * <p>Captured together at each width, with the same viewport, the same animation suppression and the
 * same timezone — a comparison between a picture taken now and one taken by a different browser build
 * last month measures the browser. The source image is <b>written to the tree</b>, which is what makes
 * this reviewable: a pull request shows what the replica was compared against, so a number nobody can
 * reproduce is at least a number somebody can look at. It is re-captured every run rather than reused,
 * because the source is <em>somebody else's site</em> and may have changed — the kept file is what
 * <i>this</i> run compared against, which is the honest artefact.
 *
 * <h2>A difference exits non-zero, and this is the one place that rule inverts</h2>
 *
 * <p>SH102 exits 0 on a change on purpose: a page looking different is usually the change you just
 * made, and gating on it produces the exit code people wrap in {@code || true}. Here the opposite
 * holds — a replica that does not match the original <b>is</b> the defect, and nobody runs this command
 * except to find out. So there is no {@code --fail-on-change} to remember.
 *
 * <h2>No endpoint, and the manifest keeps saying so</h2>
 *
 * <p>SH268's rule (a URL the server fetches is SH101's request-forgery surface) plus the standing
 * non-goal that the instance runs no browser. {@code visualSnapshot: false} stays correct, and an agent
 * reading {@code true} would hunt for a call that does not exist.
 */
async function against(resolved, { slug, dir, widths, admin, flags, browser }, ctx) {
  const source = String(flags.against);
  if (!/^https?:\/\//.test(source)) {
    throw new Error(
      `shio snapshot: --against takes an absolute URL, not "${source}".\n` +
        "It is the ORIGINAL — somebody else's site, which this instance never fetches (SH268), so\n" +
        "there is nothing for a relative path to resolve against.\n" +
        "  shio snapshot --page post:Acme/ --against https://acme.example.com/",
    );
  }
  const credentials = resolved.preview
    ? { user: admin.user, password: admin.password }
    : null;
  const compare = await comparator(ctx);
  const rows = [];

  for (const width of widths) {
    const names = againstNames(slug, width);
    const paths = {
      source: join(dir, names.source),
      replica: join(dir, names.replica),
      diff: join(dir, names.diff),
    };
    // The original first, so a failure fetching it is reported before the replica is photographed —
    // and so the two captures are as close together in time as the sequence allows.
    const captured = {
      source: shot(await capture(source, { ...flags, width }, ctx, null, browser)),
      replica: shot(await capture(resolved.url, { ...flags, width }, ctx, credentials, browser)),
    };
    const before = captured.source.bytes;
    const after = captured.replica.bytes;
    const result = compareSnapshots(before, after, { compare });
    // SH526 — beside the percentage, never instead of it. A pixel diff answers "identical to a
    // baseline", which is right for fidelity and cannot tell a lost footer from a re-cropped hero.
    const shapes = captured.source.shape && captured.replica.shape
      ? compareShapes(captured.source.shape, captured.replica.shape)
      : null;

    await put(ctx, paths.source, before);
    await put(ctx, paths.replica, after);
    let diffPath = null;
    if (result.diff) {
      await put(ctx, paths.diff, result.diff);
      diffPath = paths.diff;
    }
    rows.push({
      width,
      status: result.status,
      ratio: result.ratio,
      resolution: result.resolution,
      sourcePath: paths.source,
      replicaPath: paths.replica,
      diffPath,
      ...(shapes ? { shapes } : {}),
    });
  }

  ctx.stdout.write(`${describeAgainst(rows, {
    source,
    page: resolved.url,
    limit: flags.limit,
  })}\n`);
  // SH526 — one line, from the first width that produced a shape: a landmark is a landmark at every
  // viewport, so printing it per width would be the same sentence three times.
  const compared = rows.find((row) => row.shapes)?.shapes ?? null;
  if (compared) ctx.stdout.write(`${describeShapes(compared)}\n`);
  if (resolved.preview) {
    ctx.stdout.write(
      `  note: the replica side is the DRAFT through this instance's renderer, banner and all — `
        + `a stripe across the top of every diff is that, not your content\n`,
    );
  }
  if (rows.every((row) => row.ratio === null)) {
    // Said out loud, because a byte comparison across two origins is nearly always "differs" and a
    // reader could take that for a finding. `describe`'s rule about naming the resolution, at the one
    // place where the weaker answer is close to useless.
    ctx.stdout.write(
      "  note: no per-pixel comparator (install pixelmatch and pngjs), so this run can only say "
        + "whether the bytes match — two browsers photographing two origins almost never produce "
        + "identical bytes, so treat a difference here as unmeasured rather than as a magnitude\n",
    );
  }

  const differing = rows.filter((row) => row.status !== "same").length;
  return {
    slug,
    url: resolved.url,
    against: source,
    widths: rows,
    exitCode: differing ? 1 : 0,
  };
}

/** Writes one image, creating the directory. No baseline rotation: nothing here is displaced. */
async function put(ctx, path, bytes) {
  await ctx.fs.mkdir(dirname(path), { recursive: true });
  await ctx.fs.writeFile(path, bytes);
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
 *
 * <p>`playwright` arrives as an argument rather than being imported here (SH434): the module the
 * caller already resolved, because the refusal for a missing one has to happen before this command
 * opens anything.
 */
/**
 * A capture as `{bytes, shape}`, whichever form the producer used (SH526).
 *
 * <p>`ctx.capture` is the seam the tests drive and it hands back a Buffer, which was the whole
 * contract before the shape existed. Tolerating both here rather than changing every fixture is
 * what keeps this task from editing tests that are about something else — and a caller with no
 * shape gets `null`, which the report reads as "not compared" rather than as "nothing lost".</p>
 */
function shot(captured) {
  return captured && typeof captured === "object" && !Buffer.isBuffer(captured)
    ? { bytes: captured.bytes, shape: captured.shape ?? null }
    : { bytes: captured, shape: null };
}

/**
 * One browser for the whole run (SH209).
 *
 * <p>`capture` used to launch and close one per picture, which was already two launches for a
 * one-page `--against` at one width and four at two. Over a site it is the difference between a
 * run and a wait: a Chromium launch is the most expensive thing this command does, and the pages
 * it photographs have nothing to do with each other, so there is nothing a fresh browser buys.</p>
 *
 * <p>Not opened at all when `ctx.capture` is the seam: a unit test that launched a browser would
 * be testing Playwright.</p>
 */
async function withBrowser(playwright, ctx, body) {
  if (typeof ctx.capture === "function") {
    return body(null);
  }
  const browser = await launchBrowser(playwright, ctx);
  try {
    return await body(browser);
  } finally {
    await browser.close();
  }
}

async function capture(url, flags, ctx, credentials, browser) {
  if (typeof ctx.capture === "function") {
    return ctx.capture(url, flags, credentials);
  }
  const width = Number(flags.width ?? DEFAULT_WIDTH);
  const height = Number(flags.height ?? 800);
  let page = null;
  try {
    page = await browser.newPage({
      viewport: { width, height },
      deviceScaleFactor: 1,
      // Basic, which is what the preview accepts and what `resolveAdmin` already holds. Absent
      // for any other origin — see the call site.
      ...(credentials?.user
        ? { httpCredentials: { username: credentials.user, password: credentials.password ?? "" } }
        : {}),
      // A caret blinking in a focused field, or a date rendered in the runner's timezone, are
      // both changes nobody made.
      reducedMotion: "reduce",
      timezoneId: "UTC",
    });
    const response = await page.goto(url, {
      waitUntil: "networkidle",
      timeout: Number(flags.timeout ?? 30000),
    });
    // SH450 — a screenshot of an error is the one thing a visual gate must never score. The first
    // `--against` run that reached a browser reported `100.00% of pixels` on a replica whose markup
    // is the source's, verbatim: the PNG was a white page reading `{"error":"Unauthorized"}`, and
    // 100% is exactly what an unrelated image scores. A failure to *capture* has to read as a
    // failure, never as a difference, or the gate reports a defect in the content for a defect in
    // the run — and the two have nothing in common but the number.
    refuseNon2xx(response, url, credentials);
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
    const bytes = await page.screenshot({ fullPage: flags.viewportOnly !== true, type: "png" });
    // SH526 — the same page, the same moment, one more read inside the browser. A second load to
    // get the shape would be comparing two moments, which is the mistake `--against` exists to
    // avoid for the pixels and would be no less one here.
    return { bytes, shape: await page.evaluate(readShape, LANDMARKS) };
  } finally {
    // SH209 — the page, not the browser. The browser now outlives this call and is closed once by
    // `withBrowser`; leaving the page open instead would leak one per photograph, which over a
    // site is the same unbounded growth in a different resource.
    await page?.close();
  }
}

/**
 * Refuse a page the browser could not actually open (SH450).
 *
 * <h3>Why the status and not the pixels</h3>
 *
 * <p>Every heuristic over the image is worse than the header that is already there: an error page
 * is small, plain and stable, which is to say it looks exactly like a legitimately minimal page and
 * compares against itself perfectly. `page.goto` hands back the response for the top-level document
 * and its status is the one unambiguous fact in the whole capture.</p>
 *
 * <h3>The 401 gets its own sentence</h3>
 *
 * <p>Because it is the one a caller can act on and the one that produced this task. Playwright's
 * `httpCredentials` answers a **challenge** — it does not send `Authorization` unprompted — so a
 * 401 with credentials in hand means the server did not challenge, which is a fact about the server
 * and not about the flags. An instance older than SH450 is exactly that case, and naming it is the
 * difference between "fix your password" and "upgrade the instance".</p>
 */
export function refuseNon2xx(response, url, credentials) {
  // A null response is `about:blank` and same-document navigation, neither of which this reaches.
  const status = response?.status?.();
  if (!status || (status >= 200 && status < 300)) return;
  const sent = credentials?.user ? `as ${credentials.user}` : "with no credentials";
  const teaching = status === 401 || status === 403
    ? "The browser was given credentials, and Playwright's `httpCredentials` answers a *challenge* "
      + "rather than sending Authorization unprompted — so a 401 here means the server sent no "
      + "`WWW-Authenticate` header. Shio emits one on /preview/** since SH450; an older instance "
      + "does not, and no password will help.\n"
    : "";
  throw new Error(
    `shio snapshot: ${url} answered HTTP ${status} (${sent}), so there is no page to photograph.\n`
      + teaching
      + "Refusing rather than capturing it: an error page is small, plain and stable, so it "
      + "compares against itself perfectly and against a real site at 100% — which reads as a "
      + "total visual regression in the content rather than a failure of the run.",
  );
}

/**
 * The per-pixel comparator, when both optional helpers resolve.
 *
 * <p>`ctx.compare` is the injection seam, and since SH444 it has three states rather than two: a
 * function is the comparator, **`null` declares there is none**, and `undefined` means "resolve
 * whatever this checkout has". The middle one exists because the checkout stopped being a constant:
 * `pixelmatch` and `pngjs` were in no lockfile, so every test that did not pass a function was
 * silently pinning the byte-only path *by absence*, and declaring them at the root turned eleven of
 * them red at once. A test whose branch depends on what happens to be installed is not a test of
 * either branch (P5) — production still passes `undefined` and resolves for real.</p>
 */
async function comparator(ctx) {
  if (typeof ctx.compare === "function") {
    return ctx.compare;
  }
  if (ctx.compare === null) {
    return undefined;
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

