/*
 * SH702 — a reopened dialog must not still be showing the previous read.
 *
 * ## What this asserts, and why it is not the thing SH702 was first filed about
 *
 * The task's first reading said a dialog syncing to `open` paints last time's data for one frame.
 * That is untestable and it is also not what happens. Measured against a running console, with the
 * versions read delayed to 2s and the dialog sampled every 16ms, the reopen shows the loading text
 * in the table *immediately* — the synchronous `setLoading(true)` the lint objects to is precisely
 * what buys that — while the header keeps the PREVIOUS read's summary and its restore button for
 * the whole two seconds.
 *
 * So the defect is a **mixed state that lasts the entire load**: a change count and a *Restore*
 * action derived from state that survived the close, above a table that says it is still loading.
 * The action is the part that matters — a restore control for a version the dialog is no longer
 * showing.
 *
 * ## Why a route delay rather than a race
 *
 * Without one this is a timing test, and a timing test on a fast local instance is a coin flip that
 * passes. Delaying the read makes the stale window two seconds wide and the assertion a statement
 * about state rather than about speed. `SH579` says a red gate is a stop; a flaky one is worse,
 * because it teaches people to re-run.
 *
 * ## What it does not cover
 *
 * One dialog of the six. `post-versions` is the one reachable from a route the console check
 * already discovers and the one whose stale header carries an *action*; the other five need
 * fixtures this instance does not have (an ACL with entries, a schedule, a folder tree deep enough
 * to browse). They share one construction, so this holds the shape — it does not hold each site.
 *
 * Usage
 *   node e2e/dialog-remount.check.mjs [postId]
 *
 *   --base   console origin   (default http://localhost:5173, env SHIO_CONSOLE_URL)
 *   --api    backend origin   (default http://localhost:2710, env SHIO_URL)
 *   --headed watch it run
 *
 * Credentials: SHIO_USER / SHIO_PASSWORD. Exit code 1 when the stale state is still on screen.
 */
import { chromium } from "playwright"

const args = process.argv.slice(2)
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`)
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback
}
const has = (name) => args.includes(`--${name}`)

const BASE = flag("base", process.env.SHIO_CONSOLE_URL || "http://localhost:5173")
const API = flag("api", process.env.SHIO_URL || "http://localhost:2710")
const USER = process.env.SHIO_USER || "admin"
const PASS = process.env.SHIO_PASSWORD || "admin"
const AUTH = "Basic " + Buffer.from(`${USER}:${PASS}`).toString("base64")

/** The stale header this is about: a change count, and a control that acts on the old read. */
const STALE = [/campo\(s\) alterado\(s\)/i, /field\(s\) changed/i, /Restaurar/i, /Restore/i]

const DELAY_MS = 2000

async function api(path) {
  const res = await fetch(`${API}/api/v2${path}`, { headers: { Authorization: AUTH } })
  if (!res.ok) throw new Error(`GET /api/v2${path} answered ${res.status} — check SHIO_USER/SHIO_PASSWORD`)
  return res.json()
}

/** A post to drive, discovered rather than hardcoded, so this runs against any instance. */
async function findPost(explicit) {
  if (explicit) return explicit
  for (const site of await api("/site")) {
    const seen = new Set()
    const queue = [site.id]
    while (queue.length) {
      const id = queue.shift()
      if (seen.has(id)) continue
      seen.add(id)
      const listing = await api(`/object/${id}/list`).catch(() => null)
      if (!listing) continue
      const post = (listing.posts ?? []).find((p) => p.id)
      if (post) return post.id
      for (const folder of listing.folders ?? []) queue.push(folder.id)
    }
  }
  throw new Error("no post on this instance to open a dialog on")
}

const postId = await findPost(args.find((a) => !a.startsWith("--") && a.includes("-")))
const browser = await chromium.launch({ headless: !has("headed") })
const context = await browser.newContext({
  httpCredentials: { username: USER, password: PASS },
  extraHTTPHeaders: { Authorization: AUTH },
  viewport: { width: 1400, height: 900 },
})
const page = await context.newPage()

await page.goto(`${BASE}/console/content/post/${postId}`, { waitUntil: "domcontentloaded", timeout: 20_000 })
await page.waitForSelector("main, [role=main], nav", { timeout: 15_000 }).catch(() => {})
await page.waitForTimeout(1200)

const button = page.getByRole("button", { name: /hist[oó]rico|version/i }).first()
if (!(await button.count())) {
  console.log(`SH702: no version-history control on /console/content/post/${postId} — nothing driven.`)
  await browser.close()
  process.exit(1)
}

// One settled open, so there IS a previous read for the reopen to be stale about.
await button.click()
await page.waitForTimeout(1800)
const settled = (await page.locator("[role=dialog]").innerText().catch(() => "")).replace(/\s+/g, " ")
if (!STALE.some((re) => re.test(settled))) {
  console.log("SH702: the first open showed no change summary, so this run proves nothing about a "
    + "stale one. It needs a post whose draft differs from its published version.")
  await browser.close()
  process.exit(1)
}

await page.keyboard.press("Escape")
await page.waitForTimeout(500)

let delayed = 0
await page.route("**/v2/post-unified/**", async (route) => {
  delayed++
  await new Promise((r) => setTimeout(r, DELAY_MS))
  await route.continue()
})

await button.click()
await page.waitForTimeout(600) // well inside the delayed read, and well past any mount
const during = (await page.locator("[role=dialog]").innerText().catch(() => "")).replace(/\s+/g, " ")

const stale = STALE.filter((re) => re.test(during)).map(String)
console.log(`SH702: reopened with the read delayed ${DELAY_MS}ms (${delayed} intercepted).`)
console.log(`  on screen 600ms in: ${during.slice(0, 160) || "(nothing)"}`)

await browser.close()

if (stale.length) {
  console.error(`\nSH702 FAIL: ${stale.length} marker(s) of the PREVIOUS read are still on screen `
    + `while the next one is loading — ${stale.join(", ")}.\n`
    + "A dialog that syncs to `open` keeps its state while closed, so the header renders from the\n"
    + "last subject for the whole of the next read. The one that matters is the restore control:\n"
    + "it acts on a version the dialog is no longer showing. The fix is a keyed remount — mount the\n"
    + "body inside DialogContent so it unmounts on close, and key it on the id.")
  process.exit(1)
}

console.log("\nSH702 ok — the reopened dialog renders nothing from the previous read.")
