// SH566: the proof the source lint cannot make: the page does not move on its own.
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
// playwright is the workspace's own optional browser, the same dependency
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
 * on anything; they *are* the measurement: both assertions here are about what
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

/**
 * Bring the agent session on screen.
 *
 * The hero now opens on the console session (a person building the same page
 * without an agent), and the transcript is the second tab. Everything below
 * asserts the transcript's behaviour, and a hidden panel never intersects, so
 * `armed()` would wait forever without this click. Selecting the tab is also
 * what makes the observer fire, so no extra scrolling is needed.
 */
async function showAgentSession(page) {
  await page.getByRole("tab", { name: "An agent builds it" }).click();
}

test("the hero player never scrolls the window (SH566)", async (t) => {
  if (!URL_UNDER_TEST) return t.skip("set SHIO_SITE_URL to a running site");
  const chromium = await browser();
  if (!chromium) return t.skip("playwright is not installed: pnpm add -D playwright");

  const instance = await chromium.launch();
  try {
    const page = await instance.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(URL_UNDER_TEST, { waitUntil: "networkidle" });

    await showAgentSession(page);
    await armed(page);
    await page.evaluate((y) => window.scrollTo(0, y), TARGET_Y);

    // Wait for the scroll to arrive rather than for half a second. The site sets
    // `scroll-behavior: smooth`, so `scrollTo` is animated and how long it takes
    // depends on the distance: a fixed wait reads a position mid-flight and then
    // reports the rest of the animation as drift, which is the opposite of what
    // this test is for. Measured here: 500ms was landing ~460px short.
    await page.waitForFunction(
      ([y, tol]) => Math.abs(window.scrollY - y) <= tol,
      [TARGET_Y, TOLERANCE_PX],
      { timeout: 5000 }
    );

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
      `the page scrolled itself by ${after - before}px while the transcript played: ` +
        `the reader is driving, the player is not (P7)`
    );
  } finally {
    await instance.close();
  }
});

test("the hero opens on the console session, and the agent one is one click away", async (t) => {
  if (!URL_UNDER_TEST) return t.skip("set SHIO_SITE_URL to a running site");
  const chromium = await browser();
  if (!chromium) return t.skip("playwright is not installed: pnpm add -D playwright");

  // The positioning fix, asserted where it can actually be checked. The claim is
  // not "there is a tab component"; it is that a first-time visitor who never
  // touches the switch sees a person building a page, because the previous
  // default said, without meaning to, that the CMS needs an agent.
  const instance = await chromium.launch();
  try {
    const page = await instance.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(URL_UNDER_TEST, { waitUntil: "networkidle" });

    const consoleSteps = page.locator("button[aria-label^='Show step']").first();
    const agentSteps = page.locator("button[aria-label^='Show call']").first();
    await consoleSteps.waitFor({ state: "visible" });
    assert.equal(
      await agentSteps.isVisible(),
      false,
      "the agent transcript is the second tab, not the one the page opens on"
    );

    await showAgentSession(page);
    await agentSteps.waitFor({ state: "visible" });
    assert.equal(
      await consoleSteps.isVisible(),
      false,
      "and switching shows one session at a time rather than stacking them"
    );
  } finally {
    await instance.close();
  }
});

test("clicking a step selects it and shows what that call produced (SH571)", async (t) => {
  if (!URL_UNDER_TEST) return t.skip("set SHIO_SITE_URL to a running site");
  const chromium = await browser();
  if (!chromium) return t.skip("playwright is not installed: pnpm add -D playwright");

  const instance = await chromium.launch();
  try {
    const page = await instance.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(URL_UNDER_TEST, { waitUntil: "networkidle" });
    await showAgentSession(page);
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
    // aria-current: two views of one selection, which is correct and would make
    // an unscoped count read 2 and mean nothing.
    assert.equal(
      await page.locator("button[aria-label^='Show call'][aria-current='step']").count(),
      1
    );

    // And the point of clicking it: the artefact pane shows *that* call's output.
    // Asserted as a round trip rather than against a literal, because the copy
    // lives in site-content.ts and a test that repeats it is a second place for
    // it to go stale: away and back must land on the same bytes.
    const artefact = page.locator(".scanlines pre");
    const fourth = await artefact.innerText();
    await steps.nth(0).click();
    assert.notEqual(await artefact.innerText(), fourth, "a different call, a different artefact");
    await steps.nth(3).click();
    assert.equal(await artefact.innerText(), fourth, `re-selecting ${tool} restores its output`);
  } finally {
    await instance.close();
  }
});
