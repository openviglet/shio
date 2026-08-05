/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio audit <page>` (SH391) — what is wrong with this page, as text with a selector each.
 *
 * ## Why this is a command and not an MCP tool
 *
 * `tools/list` is capped at 12 with 11 used, and it is paid on **every turn**. A Bash call is paid
 * when it is made. An audit is also the one browser-shaped thing in the product, and "the instance
 * does not run a browser" is a standing non-goal — so putting it behind an endpoint would move a
 * Chromium process into the JVM's blast radius to save an agent one line of shell.
 *
 * ## Playwright stays an optional dynamic import
 *
 * Same reason `shio snapshot` does it (SH102): `@viglet/shio` is installed to author content, and
 * taxing every install with a browser for a command most projects never run is the wrong trade.
 * Its absence is a teaching error naming the two commands that fix it (P6).
 *
 * ## The target flag is `--page`, and `--url` is the instance
 *
 * `shio snapshot` read `flags.url` as *both* the page and the instance, which SH365 filed and then
 * fixed for both commands that had it. This one never inherited it: `--page` (or a positional, or
 * `--address`) is what to audit, and `--url` means the same thing here as it does everywhere else.
 * `flag-meaning.test.mjs` is what keeps that true of the next command as well.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { requireBrowser } from "../browser.mjs";
import { resolveAdmin } from "../context.mjs";
import { PER_RULE_CAP, classify, describe, parseWidths, probe } from "../audit.mjs";
import { pageTarget, resolveUrl } from "../snapshot.mjs";

/**
 * What `shio audit --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio audit <page> — what is wrong with a rendered page, as text with a selector each

Errors are what a machine observed (a 404, a thrown exception) and exit 1; overflow,
contrast, a collapsed box and a fallback font are warnings that exit 0, because each is
design intent often enough that gating on them makes this the exit code people wrap in
|| true. Needs playwright.

  --page <page>        The page to audit (or a bare positional, or --address)
  --width <n[,n]>      Viewport width(s) to load, default 1280 — findings merge across them
  --height <n>         Viewport height (default 800)
  --contrast <ratio>   The contrast floor to report against (default 4.5)
  --strict             Exit 1 on warnings too
  --preview            Audit the instance's own preview (the banner is never a finding)
  --timeout <ms>       Per-page navigation timeout (default 30000)
  --json               The findings as JSON, for a pipeline

NOT --url: that is the instance (SH365).

Examples:
  shio audit post:Acme/about --width 1280,375
  shio audit https://acme.example.com/ --strict
`;

export async function run(flags, positionals, ctx) {
  // No `warn` argument, which is how SH365's helper is told to refuse the legacy `--url`-as-page
  // reading: this command never documented it, so accepting it would create the hazard rather than
  // carry it — `--url http://localhost:2710` would audit the instance root instead of refusing.
  const target = pageTarget(flags, positionals);
  if (!target) {
    throw new Error(
      "shio audit: pass a page — post:<site>/<url>, or an absolute URL.\n" +
        "Example: shio audit post:Blog/blog/hello-world --width 1280,375",
    );
  }
  const { widths, error: widthError } = parseWidths(flags.width);
  if (widthError) {
    throw new Error(`shio audit: ${widthError}`);
  }

  // SH434 — before a socket is opened, for the reason `snapshot` says at the same point: the
  // refusal used to arrive after the instance had been asked for its sites, so a run that could
  // never succeed exited with live handles armed and was measured aborting instead.
  const browser = typeof ctx.audit === "function" ? null : await requireBrowser({
    command: "audit",
    why: "It is optional because most projects never render a page here — the browser-free checks "
      + "are `shio verify` (the content lint) and `shio digest` (one page's structure).",
  }, ctx);

  const admin = resolveAdmin(flags, ctx.env);
  const sites = /^https?:\/\//.test(String(target)) ? new Map() : await siteUrls(admin, ctx);
  const resolved = resolveUrl(target, sites, {
    previewBase: admin.baseUrl,
    preview: flags.preview === true,
  });
  if (resolved.error) {
    throw new Error(`shio audit: ${resolved.error}`);
  }

  // Only the instance's own preview is handed the instance's credentials — the preview is
  // authenticated since SH359, and a site's public host has no business seeing an admin password
  // because one command reaches both.
  const observed = await inspect(
    resolved.url,
    {
      widths,
      credentials: resolved.preview ? { user: admin.user, password: admin.password } : null,
      flags,
      browser,
    },
    ctx,
  );
  const { findings, counts, capped } = classify(observed, { contrast: flags.contrast });

  if (flags.json) {
    ctx.stdout.write(
      `${JSON.stringify({ target, url: resolved.url, widths, counts, findings, capped }, null, 2)}\n`,
    );
  } else {
    ctx.stdout.write(describe({ target, url: resolved.url, widths, findings, counts, capped }, flags));
    if (resolved.preview) {
      // The banner is excluded from the findings, so the reader is told which page this was: a
      // draft through Shio's renderer is not what a visitor gets, and "the page is fine" means
      // different things about the two.
      ctx.stdout.write(`  preview: DRAFT through this instance (the preview banner is not audited)\n`);
    }
  }

  // Errors are what a machine observed — a 404, a thrown exception — so they gate. Warnings are
  // judgments about design intent, and a command whose exit code fires on those is the one people
  // learn to wrap in `|| true`.
  const failed = counts.errors > 0 || (Boolean(flags.strict) && counts.total > 0);
  return { target, url: resolved.url, widths, counts, findings, capped, exitCode: failed ? 1 : 0 };
}

/** Site name → public base URL, from the context pack's `sites` section (as `shio snapshot`). */
async function siteUrls({ baseUrl, user, password }, ctx) {
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const sites = new Map();
  try {
    const { body } = await client.getContext({ include: "sites", format: "json" });
    for (const site of JSON.parse(body).sites ?? []) {
      if (site?.name) sites.set(site.name, site.url ?? "");
    }
  } catch {
    // Left empty deliberately: resolveUrl then says "not a site this instance reports", which is a
    // better answer than a fabricated origin.
  }
  return sites;
}

/**
 * Loads the page once per width and collects what each load observed.
 *
 * <p>`ctx.audit` is the seam the tests use. A unit test that launched Chromium would be testing
 * Playwright; what is worth pinning is the classification, the merge across widths and the exit
 * code, all of which live in `audit.mjs`.
 *
 * <p>Requests and console errors are collected **here** rather than in the page, because a
 * response that never became a DOM node — a stylesheet's background image, a fetch — leaves no
 * trace an in-page probe could find.
 */
async function inspect(url, { widths, credentials, flags, browser: playwright }, ctx) {
  if (typeof ctx.audit === "function") {
    return ctx.audit(url, { widths, credentials }, flags);
  }
  const timeout = Number(flags.timeout ?? 30000);
  const browser = await playwright.chromium.launch();
  const requests = [];
  const consoleErrors = [];
  const probes = [];
  try {
    for (const width of widths) {
      const page = await browser.newPage({
        viewport: { width, height: Number(flags.height ?? 800) },
        deviceScaleFactor: 1,
        ...(credentials?.user
          ? { httpCredentials: { username: credentials.user, password: credentials.password ?? "" } }
          : {}),
        reducedMotion: "reduce",
        timezoneId: "UTC",
      });
      page.on("response", (response) => {
        if (response.status() >= 400) {
          requests.push({ url: response.url(), status: response.status(), width });
        }
      });
      page.on("requestfailed", (request) => {
        requests.push({ url: request.url(), status: 0, failure: request.failure()?.errorText ?? null, width });
      });
      page.on("console", (message) => {
        if (message.type() === "error") consoleErrors.push({ text: message.text(), width });
      });
      page.on("pageerror", (error) => {
        consoleErrors.push({ text: String(error?.message ?? error), width });
      });
      try {
        await page.goto(url, { waitUntil: "networkidle", timeout });
        // Transitions mid-measurement produce a rectangle that was true for one frame, which is
        // the same determinism argument the snapshot makes — here it would be a phantom finding.
        await page.addStyleTag({
          content: "*,*::before,*::after{animation:none!important;transition:none!important}",
        });
        await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.evaluate(() => document.fonts?.ready);
        const result = await page.evaluate(probe, { cap: PER_RULE_CAP });
        probes.push({ width, ...result });
      } finally {
        await page.close();
      }
    }
    return { probes, requests, consoleErrors };
  } finally {
    await browser.close();
  }
}


