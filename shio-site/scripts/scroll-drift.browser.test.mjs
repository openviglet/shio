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

/**
 * Wait for autoplay to arm, on the observable fact rather than on a guess.
 *
 * SSR and the first client render draw the *finished* session, and the panel
 * resets to call 1 only once it has observed its own intersection. So the
 * progress dot for call 1 becoming `aria-current` is the event, and the dots
 * are the right thing to read: they carried that attribute before the steps
 * became clickable, which keeps this helper usable by both tests.
 *
 * The waits that follow it are deliberately fixed. They are not synchronising
 * on anything — they *are* the measurement: both assertions here are about what
 * fails to happen over a known span of wall clock, and there is no event for
 * "the page did not move".
 */
const armed = (page) =>
  page.waitForFunction(() =>
    document
      .querySelector("button[aria-current='step'][aria-label^='Call ']")
      ?.getAttribute("aria-label")
      ?.startsWith("Call 1:")
  );

test("the hero player never scrolls the window (SH566)", async (t) => {
  if (!URL_UNDER_TEST) return t.skip("set SHIO_SITE_URL to a running site");
  const chromium = await browser();
  if (!chromium) return t.skip("playwright is not installed: pnpm add -D playwright");

  const instance = await chromium.launch();
  try {
    const page = await instance.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(URL_UNDER_TEST, { waitUntil: "networkidle" });

    await armed(page);
    await page.evaluate((y) => window.scrollTo(0, y), TARGET_Y);
    await page.waitForTimeout(500); // let the smooth scroll settle before reading it

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

test("clicking a step selects it and shows what that call produced (SH567)", async (t) => {
  if (!URL_UNDER_TEST) return t.skip("set SHIO_SITE_URL to a running site");
  const chromium = await browser();
  if (!chromium) return t.skip("playwright is not installed: pnpm add -D playwright");

  const instance = await chromium.launch();
  try {
    const page = await instance.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(URL_UNDER_TEST, { waitUntil: "networkidle" });
    await armed(page);

    const steps = page.locator("ol li button[aria-label^='Show call']");
    assert.ok((await steps.count()) >= 4, "every step carries its own click target");

    // The fourth call, chosen by clicking it rather than by pressing next three times.
    const label = (await steps.nth(3).getAttribute("aria-label")) ?? "";
    const tool = label.replace(/^Show call \d+: /, "");
    await steps.nth(3).click();

    // Selecting is also taking over: the reader is driving, so autoplay stops and
    // stays stopped. A step that lit and then slid on is the defect this refuses.
    await page.waitForTimeout(STEP_MS + 800);
    assert.equal(
      await steps.nth(3).getAttribute("aria-current"),
      "step",
      `clicking ${tool} must leave it selected, not hand it back to playback`
    );
    // Exactly one step is lit. Scoped to the step buttons on purpose: the progress
    // dots are `<ol><li><button>` too, and the dot for the same call is *also*
    // aria-current — two views of one selection, which is correct and would make
    // an unscoped count read 2 and mean nothing.
    assert.equal(
      await page.locator("button[aria-label^='Show call'][aria-current='step']").count(),
      1
    );
  } finally {
    await instance.close();
  }
});
