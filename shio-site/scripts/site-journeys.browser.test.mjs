// SH1374: the journeys and the look, in a browser. `rendered-contract.test.mjs` asserts what the
// build produced; this asserts what a visitor meets: each representative page at 390, 768 and 1440
// pixels in both themes with reduced motion, the phone menu by keyboard, and the consent banner
// fresh, declined and withdrawn. It keeps a screenshot of every page/width/theme and a route report,
// because the visual review this task asks for is a person looking at those, not a class assertion.
//
// Opt-in, like scroll-drift.browser.test.mjs: it needs a running site.
//
//   pnpm run build:site && pnpm --filter shio-site exec vite preview --port 4173
//   SHIO_SITE_URL=http://localhost:4173/ pnpm --filter shio-site test
//
// The docs pages are served by a build or the deployed site, not by `vite` dev; a run against dev
// reports them as not served instead of failing. External destinations are checked only with
// SHIO_SITE_LINKS=1, retry-bounded, and a network failure is reported as inconclusive.

import assert from "node:assert/strict";
import { mkdirSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { DESTINATIONS } from "../src/lib/destinations.ts";

const BASE = process.env.SHIO_SITE_URL?.replace(/\/$/u, "");
const OUT = fileURLToPath(new URL("../test-results/site-journeys/", import.meta.url));
const ROUTES = ["/", "/console/", "/features/delivery/", "/docs/", "/docs/installation-guide/"];
const WIDTHS = [390, 768, 1440];
const THEMES = ["light", "dark"];
const CONSENT_KEY = "shio-analytics-consent";

async function chromium() {
  try {
    return (await import("playwright")).chromium;
  } catch {
    return null;
  }
}

async function context(browser, width, scheme, consent = "denied") {
  const ctx = await browser.newContext({ viewport: { width, height: 900 }, colorScheme: scheme, reducedMotion: "reduce" });
  await ctx.addInitScript(([key, value]) => {
    try {
      if (value) localStorage.setItem(key, value);
    } catch { /* a private window refuses storage; the banner then shows, which is correct */ }
  }, [CONSENT_KEY, consent]);
  return ctx;
}

test("SH1374: each representative page renders at every width and theme, without overflow or errors", async (t) => {
  if (!BASE) return t.skip("set SHIO_SITE_URL to a running site");
  const engine = await chromium();
  if (!engine) return t.skip("playwright is not installed");
  mkdirSync(OUT, { recursive: true });
  const browser = await engine.launch();
  const report = [];
  const failures = [];
  try {
    for (const route of ROUTES) {
      for (const width of WIDTHS) {
        for (const scheme of THEMES) {
          const ctx = await context(browser, width, scheme);
          const page = await ctx.newPage();
          const errors = [];
          page.on("pageerror", (error) => errors.push(error.message));
          const response = await page.goto(BASE + route, { waitUntil: "networkidle" });
          const status = response?.status() ?? 0;
          if (status === 404 && route.startsWith("/docs/")) {
            report.push({ route, width, scheme, status, result: "not served (dev server?)" });
            await ctx.close();
            continue;
          }
          const facts = await page.evaluate(() => ({
            overflow: document.documentElement.scrollWidth - window.innerWidth,
            h1: document.querySelector("h1")?.textContent?.trim() ?? "",
            title: document.title,
          }));
          const name = `${route.replaceAll("/", "_").replace(/^_|_$/gu, "") || "home"}-${width}-${scheme}.png`;
          await page.screenshot({ path: OUT + name });
          const result = status === 200 && facts.overflow <= 0 && facts.h1 && !errors.length ? "ok" : "FAIL";
          report.push({ route, width, scheme, status, ...facts, errors, screenshot: name, result });
          if (result !== "ok") failures.push(`${route} @${width} ${scheme}: ${JSON.stringify({ status, ...facts, errors })}`);
          await ctx.close();
        }
      }
    }
  } finally {
    await browser.close();
    writeFileSync(OUT + "report.json", JSON.stringify(report, null, 2) + "\n");
  }
  t.diagnostic(`SH1374: ${report.length} renders, screenshots and report.json in test-results/site-journeys/`);
  assert.deepEqual(failures, []);
});

test("SH1374: the phone menu opens and closes by keyboard and returns focus to its button", async (t) => {
  if (!BASE) return t.skip("set SHIO_SITE_URL to a running site");
  const engine = await chromium();
  if (!engine) return t.skip("playwright is not installed");
  const browser = await engine.launch();
  try {
    const ctx = await context(browser, 390, "light");
    const page = await ctx.newPage();
    await page.goto(BASE + "/", { waitUntil: "networkidle" });
    const toggle = page.getByRole("button", { name: "Open menu" });
    await toggle.focus();
    await page.keyboard.press("Enter");
    await assert.doesNotReject(page.locator("header nav a", { hasText: "Get started" }).waitFor({ state: "visible" }));
    assert.equal(await page.locator("header nav a", { hasText: "part of viglet.org" }).count(), 1,
      "SH1374: the way back to the portfolio is in the phone menu");
    await page.keyboard.press("Escape");
    await page.getByRole("button", { name: "Open menu" }).waitFor({ state: "visible" });
    const focused = await page.evaluate(() => document.activeElement?.getAttribute("aria-label"));
    assert.equal(focused, "Open menu", "SH1374: closing the menu leaves focus on its button");
    await ctx.close();
  } finally {
    await browser.close();
  }
});

test("SH1374: consent asks when fresh, loads nothing when declined, and asks again when withdrawn", async (t) => {
  if (!BASE) return t.skip("set SHIO_SITE_URL to a running site");
  const engine = await chromium();
  if (!engine) return t.skip("playwright is not installed");
  const browser = await engine.launch();
  try {
    const ctx = await context(browser, 1440, "light", null);
    const page = await ctx.newPage();
    const analytics = [];
    page.on("request", (request) => {
      if (/googletagmanager|google-analytics/u.test(request.url())) analytics.push(request.url());
    });
    await page.goto(BASE + "/", { waitUntil: "networkidle" });
    const banner = page.getByText("We respect your privacy");
    await banner.waitFor({ state: "visible" });
    await page.getByRole("button", { name: "Decline", exact: true }).click();
    await banner.waitFor({ state: "hidden" });
    await page.reload({ waitUntil: "networkidle" });
    assert.equal(await banner.count(), 0, "SH1374: a declined visitor is not asked again on the next page");
    await page.getByRole("button", { name: "Cookie settings" }).click();
    await banner.waitFor({ state: "visible" });
    assert.deepEqual(analytics, [], "SH1374: nothing from Google loads before a visitor accepts");
    await ctx.close();
  } finally {
    await browser.close();
  }
});

test("SH1374: the external destinations answer, and a network failure is inconclusive", async (t) => {
  if (process.env.SHIO_SITE_LINKS !== "1") return t.skip("set SHIO_SITE_LINKS=1 to check the external destinations");
  const urls = Object.values(DESTINATIONS).flatMap((entry) => ("url" in entry ? [entry.url] : []));
  const broken = [];
  for (const url of urls) {
    let outcome = null;
    for (let attempt = 0; attempt < 3 && outcome === null; attempt += 1) {
      try {
        const response = await fetch(url, { method: "GET", redirect: "follow", signal: AbortSignal.timeout(15000) });
        outcome = response.status;
      } catch {
        await new Promise((resolve) => setTimeout(resolve, 1000 * (attempt + 1)));
      }
    }
    if (outcome === null) t.diagnostic(`SH1374: ${url} inconclusive (network), not a product regression`);
    else if (outcome >= 400) broken.push(`${url}: ${outcome}`);
  }
  assert.deepEqual(broken, []);
});
