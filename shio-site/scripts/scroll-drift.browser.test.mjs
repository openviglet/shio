// SH566 — the proof the source lint cannot make: the page does not move on its own.
//
// `scroll-ownership.test.mjs` asserts the *shape* (nobody but the router calls
// `scrollIntoView`), which is what runs on every CI build. This file asserts the
// *behaviour*, and it needs three things a unit test does not have: layout, a
// real scroll position, and 7 seconds of wall clock while the hero player takes
// two of its 2.6s steps. So it is opt-in.
//
//   pnpm --filter shio-site dev            # or: vite preview, or the deployed site
//   SHIO_SITE_URL=http://localhost:5173/ pnpm --filter shio-site test
//
// playwright is the workspace's own optional browser — the same dependency
// `shio clone --render`, `snapshot` and `audit` resolve through
// `cli/src/browser.mjs`. Absent, this skips rather than fails: the CLI's rule is
// that a missing browser is a refusal with an install line, never a red build.

import { test } from "node:test";
import assert from "node:assert/strict";

const URL_UNDER_TEST = process.env.SHIO_SITE_URL;
const STEP_MS = 2600;

/** Where a reader would be after scrolling past the hero. */
const TARGET_Y = 2000;
/** Sub-pixel settle from smooth scrolling is not drift; a yanked page is. */
const TOLERANCE_PX = 2;

async function browser() {
  try {
    return (await import("playwright")).chromium;
  } catch {
    return null;
  }
}

test("the hero player never scrolls the window (SH566)", async (t) => {
  if (!URL_UNDER_TEST) return t.skip("set SHIO_SITE_URL to a running site");
  const chromium = await browser();
  if (!chromium) return t.skip("playwright is not installed: pnpm add -D playwright");

  const instance = await chromium.launch();
  try {
    const page = await instance.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(URL_UNDER_TEST, { waitUntil: "networkidle" });

    // Let autoplay arm: the panel observes its own intersection before it starts.
    await page.waitForTimeout(1500);
    await page.evaluate((y) => window.scrollTo(0, y), TARGET_Y);
    await page.waitForTimeout(500);

    const before = await page.evaluate(() => window.scrollY);
    assert.ok(
      Math.abs(before - TARGET_Y) <= TOLERANCE_PX,
      `the page moved while the reader was scrolling: asked for ${TARGET_Y}, landed on ${before}`
    );

    // Two player steps, plus slack. This is the window the defect lived in.
    await page.waitForTimeout(STEP_MS * 2 + 1800);
    const after = await page.evaluate(() => window.scrollY);

    assert.ok(
      Math.abs(after - before) <= TOLERANCE_PX,
      `the page scrolled itself by ${after - before}px while the transcript played — ` +
        `the reader is driving, the player is not (P7)`
    );
  } finally {
    await instance.close();
  }
});
