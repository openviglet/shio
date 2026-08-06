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
import { requireBrowser } from "../browser.mjs";
import { resolveAdmin } from "../context.mjs";
import {
  DEFAULT_WIDTH,
  SNAPSHOT_DIR,
  againstNames,
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
export const HELP = `shio snapshot <page> — a PNG for a human, and whether the page looks different

Baselines live in shio/snapshots/; the displaced one is kept as <slug>.prev.png. A change
exits 0 — a page looking different is usually the change you just made — so the gate is
opt-in. Needs playwright; pixelmatch + pngjs make the diff per-pixel instead of byte-level.

  --page <page>        The page to photograph (or a bare positional, or --address)
  --against <url>      Compare the page against the ORIGINAL at that URL, both captured in
                       this run (SH386). A difference exits 1 — here that IS the defect
  --accept             Replace the baseline without reporting a change
  --fail-on-change     Exit 1 when the page changed (a gate; off by default)
  --width <n[,n]>      Viewport width(s), default 1280. Several widths only with --against
  --height <n>         Viewport height (default 800)
  --viewport-only      Just the viewport, not the full page
  --preview            Photograph the instance's own preview — the only way to see a DRAFT
  --limit <n>          Rows to print with --against
  --timeout <ms>       Per-page navigation timeout (default 30000)
  --out <dir>          Where the images go (default shio/snapshots)

NOT --url: that is the instance, and it also decides which server a front-endless site is
previewed from — one flag that picked both would decide which server the picture came from.

Examples:
  shio snapshot post:Acme/about
  shio snapshot --page post:Acme/ --against https://acme.example.com/ --width 1280,375
`;

export async function run(flags, positionals, ctx) {
  // SH365 — `--page`, not `--url`. `--url` is the instance here as everywhere else, and reading it
  // as the page as well is what let one flag decide which *server* a picture came from.
  const target = pageTarget(flags, positionals, (message) =>
    ctx.stderr.write(`shio snapshot: ${message}\n`));
  if (!target) {
    throw new Error(
      "shio snapshot: pass a page — post:<site>/<url>, or an absolute URL.\n" +
        "Example: shio snapshot post:Blog/blog/hello-world",
    );
  }

  // SH434 — the browser is resolved here, before a socket is opened. It used to be resolved inside
  // `capture`, which is *after* the instance has been asked for its sites: a run that could never
  // succeed printed its refusal while the process was still carrying live handles, and `exit(1)` was
  // measured aborting rather than exiting. Spending a round trip on a run that cannot work is the
  // smaller half of the same mistake.
  const browser = typeof ctx.capture === "function" ? null : await requireBrowser({
    command: "snapshot",
    why: "It is optional because a screenshot is for a human to look at (SH102) — the agent's own "
      + "check is `shio digest`, which needs no browser and costs a fraction of the tokens.",
  }, ctx);

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
  const shot = await capture(resolved.url, { ...flags, width: widths[0] }, ctx,
    resolved.preview ? { user: admin.user, password: admin.password } : null, browser);
  const before = await readIfPresent(ctx, path);
  const result = compareSnapshots(before, shot, { compare: await comparator(ctx) });

  await write(ctx, path, shot, result, flags);
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
    const before = await capture(source, { ...flags, width }, ctx, null, browser);
    const after = await capture(resolved.url, { ...flags, width }, ctx, credentials, browser);
    const result = compareSnapshots(before, after, { compare });

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
    });
  }

  ctx.stdout.write(`${describeAgainst(rows, {
    source,
    page: resolved.url,
    limit: flags.limit,
  })}\n`);
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
async function capture(url, flags, ctx, credentials, playwright) {
  if (typeof ctx.capture === "function") {
    return ctx.capture(url, flags, credentials);
  }
  const width = Number(flags.width ?? DEFAULT_WIDTH);
  const height = Number(flags.height ?? 800);
  const browser = await playwright.chromium.launch();
  try {
    const page = await browser.newPage({
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
    return await page.screenshot({ fullPage: flags.viewportOnly !== true, type: "png" });
  } finally {
    await browser.close();
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

