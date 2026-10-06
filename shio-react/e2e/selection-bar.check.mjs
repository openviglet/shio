/*
 * SH1239 — the content browser's bulk verbs stay in view down a long folder.
 *
 * SH1223 let the browser's table scroll with the page, and the design system held only its header
 * row there, so rows ticked low in a long folder left Move and Delete at the top of the page.
 * 2026.3.19 (VDS209) makes the table's bar sticky while rows are selected and stacks the header row
 * under it. Classes cannot say whether a real layout keeps the bar on screen — an ancestor with
 * `overflow` would silently unstick it — so this scrolls a real page and measures.
 *
 * It needs the same `folder:<site>/bulk` seed as browser-window.check.mjs.
 *
 * Usage
 *   node e2e/selection-bar.check.mjs [--folder <id>] [--headed]
 *   --base   console origin (default http://localhost:5173, env SHIO_CONSOLE_URL)
 *   --api    backend origin (default http://localhost:2710, env SHIO_URL)
 *
 * Credentials: SHIO_USER / SHIO_PASSWORD. Exit code 1 on any failed claim.
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

async function api(path) {
  const res = await fetch(`${API}/api/v2${path}`, { headers: { Authorization: AUTH } })
  if (!res.ok) throw new Error(`GET /api/v2${path} answered ${res.status}`)
  return res.json()
}

async function bulkFolder() {
  const explicit = flag("folder", null)
  if (explicit) return explicit
  for (const site of await api("/site")) {
    const resolved = await api(`/agent/resolve?address=${encodeURIComponent(`folder:${site.name}/bulk`)}`)
      .catch(() => null)
    const id = resolved?.id ?? resolved?.object?.id
    if (id) return id
  }
  throw new Error("no folder:<site>/bulk — seed one with more than 150 posts first")
}

const failures = []
const claim = (ok, message) => {
  console.log(`${ok ? "ok  " : "FAIL"} ${message}`)
  if (!ok) failures.push(message)
}

const folderId = await bulkFolder()
const browser = await chromium.launch({ headless: !has("headed") })
const context = await browser.newContext({
  httpCredentials: { username: USER, password: PASS },
  extraHTTPHeaders: { Authorization: AUTH },
  viewport: { width: 1400, height: 900 },
})
const page = await context.newPage()
const errors = []
page.on("pageerror", (error) => errors.push(error.message))

await page.goto(`${BASE}/bento/content/object/${folderId}?view=spreadsheet`, { waitUntil: "domcontentloaded" })
const grid = page.locator('[role="grid"]')
await grid.locator('[role="row"]').nth(1).waitFor({ timeout: 20_000 })

/** Where the bar and the header row sit in the viewport, and how far the page has scrolled. */
const measure = () => page.evaluate(() => {
  const bar = document.querySelector('[data-slot="bento-data-table-bar"]')
  const header = document.querySelector('[data-slot="bento-data-table"] [role="rowgroup"]')
  const scroller = [...document.querySelectorAll("*")]
    .find((el) => el.scrollHeight > el.clientHeight + 50 && /auto|scroll/.test(getComputedStyle(el).overflowY))
  const top = (el) => (el ? Math.round(el.getBoundingClientRect().top) : null)
  return {
    bar: top(bar), barBottom: bar ? Math.round(bar.getBoundingClientRect().bottom) : null,
    header: top(header),
    sticky: bar ? getComputedStyle(bar).position : null,
    scrolled: Math.round(scroller ? scroller.scrollTop : window.scrollY),
  }
})
const scrollBy = (dy) => page.evaluate((delta) => {
  const scroller = [...document.querySelectorAll("*")]
    .find((el) => el.scrollHeight > el.clientHeight + 50 && /auto|scroll/.test(getComputedStyle(el).overflowY))
  if (scroller) scroller.scrollTop += delta; else window.scrollBy(0, delta)
}, dy)

const before = await measure()
claim(before.bar !== null && before.sticky !== "sticky", `with nothing selected the bar is in the flow (position ${before.sticky})`)

// Tick a row, then scroll the page well past where the bar started.
await grid.locator('[role="row"]').nth(3).locator('[role="checkbox"], input[type="checkbox"]').first().click()
await page.waitForTimeout(300)
await scrollBy(1500)
await page.waitForTimeout(500)
const down = await measure()
console.log(`selected and scrolled: ${JSON.stringify(down)}`)
claim(down.scrolled > 800, `the page scrolled (${down.scrolled}px)`)
claim(down.sticky === "sticky", `with a row selected the bar is sticky (position ${down.sticky})`)
claim(down.bar !== null && down.bar >= 0 && down.bar < 200, `the bar stays at the top of the viewport (top ${down.bar}px)`)
claim(down.header !== null && Math.abs(down.header - down.barBottom) <= 2,
  `the header row sits under the bar, not beneath it (header ${down.header}px, bar bottom ${down.barBottom}px)`)
const verbs = page.getByRole("toolbar", { name: /sele/i })
claim(await verbs.isVisible(), "the selection's verbs are on screen")

// Clear the selection from the bar itself — the ticked row is no longer mounted this far down — and
// the bar returns to the flow and scrolls away with the page.
await page.locator('[data-slot="bento-data-table-bar"]')
  .getByRole("button", { name: /clear selection|limpar sele/i }).click()
await page.waitForTimeout(300)
const cleared = await measure()
console.log(`cleared: ${JSON.stringify(cleared)}`)
claim(cleared.sticky !== "sticky", `with the selection cleared the bar leaves (position ${cleared.sticky})`)
claim(cleared.bar === null || cleared.bar < 0, `and scrolls off with the page (top ${cleared.bar}px)`)

claim(errors.length === 0, `no page errors${errors.length ? `: ${errors.join(" | ")}` : ""}`)
await browser.close()

if (failures.length) {
  console.error(`\nSH1239 FAIL: ${failures.length} claim(s) did not hold.`)
  process.exit(1)
}
console.log("\nSH1239 ok — the selection bar holds under the shell while rows are selected, and leaves with them.")
