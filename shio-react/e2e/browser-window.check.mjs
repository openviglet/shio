/*
 * SH1026 — the content browser's list and grid mount only the rows a reader can see.
 *
 * `browser-views.test.mjs` holds the window's arithmetic and the page's wiring; neither can say
 * whether a real layout measures a row, whether the shell's scroller moves the window, or whether
 * the keyboard reaches a row that was not mounted when the key was pressed. This drives it.
 *
 * It needs a folder of more than 150 posts (the threshold). With `--folder` absent it takes the
 * first folder named `bulk` on any site, which is what the SH1026 seed below creates:
 *
 *   ops: folder.upsert folder:<site>/bulk, then 200 × post.upsert post:<site>/bulk/row-NNN
 *
 * Usage
 *   node e2e/browser-window.check.mjs [--folder <id>] [--headed]
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
  // Resolved by address, the way an agent names it, rather than walked from the site's roots.
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
// The listing's first page, counted: a page that re-reads it on every render (found by the first run
// of this check) looks fine on screen until "load more" is thrown away by the next re-read.
let firstPageReads = 0
page.on("request", (request) => { if (/\/object\/[^/]+\/list\?page=0/.test(request.url())) firstPageReads++ })

for (const view of ["list", "grid"]) {
  await page.goto(`${BASE}/bento/content/object/${folderId}?view=${view}`, { waitUntil: "domcontentloaded" })
  const listbox = page.locator('[role="grid"]') // SH1054 — a grid now, not a listbox
  const options = listbox.locator('[role="row"]')
  // The listbox renders before its first page arrives, so wait for a row, not for the box.
  await options.first().waitFor({ timeout: 20_000 })
  // The browser pages by fifty; load every page so the folder crosses the threshold on screen.
  for (let i = 0; i < 10; i++) {
    const more = page.getByRole("button", { name: /carregar mais|load more/i })
    if (!(await more.count())) break
    const before = Number(await listbox.getAttribute("aria-rowcount"))
    await more.first().click()
    await page.waitForFunction((was) => {
      const grid = document.querySelector('[role="grid"]')
      return Number(grid?.getAttribute("aria-rowcount")) > was
    }, before, { timeout: 15_000 })
  }
  await page.waitForTimeout(500)
  firstPageReads = 0
  await page.waitForTimeout(2000)
  claim(firstPageReads === 0, `${view}: an idle page reads nothing (${firstPageReads} page-0 read(s) in 2s)`)

  const mounted = await options.count()
  const size = Number(await listbox.getAttribute("aria-rowcount"))
  console.log(`\n${view}: ${mounted} row(s) mounted of aria-rowcount ${size}`)
  claim(size > 150, `${view}: the folder is past the threshold (${size} rows)`)
  claim(mounted > 0 && mounted < size, `${view}: only a window is mounted (${mounted} of ${size})`)

  // The keyboard reaches a row that was not mounted when the key was pressed.
  await options.first().focus()
  await page.keyboard.press("End")
  await page.waitForTimeout(800)
  const focused = await page.evaluate(() => {
    const el = document.activeElement
    return el?.getAttribute("role") === "row"
      ? { posinset: el.getAttribute("aria-rowindex"), text: el.textContent?.slice(0, 40) } : null
  })
  claim(focused?.posinset === String(size),
    `${view}: End focuses the last row (aria-posinset ${focused?.posinset ?? "none"}, "${focused?.text ?? ""}")`)
  const mountedAtEnd = await options.count()
  claim(mountedAtEnd < size, `${view}: at the end the window moved rather than mounting everything (${mountedAtEnd})`)

  await page.keyboard.press("Home")
  await page.waitForTimeout(800)
  const first = await page.evaluate(() => document.activeElement?.getAttribute("aria-posinset"))
  claim(first === "1", `${view}: Home focuses the first row again (aria-posinset ${first ?? "none"})`)
}

claim(errors.length === 0, `no page errors${errors.length ? `: ${errors.join(" | ")}` : ""}`)
await browser.close()

if (failures.length) {
  console.error(`\nSH1026 FAIL: ${failures.length} claim(s) did not hold.`)
  process.exit(1)
}
console.log("\nSH1026 ok — the list and the grid are windowed, and the keyboard reaches every row.")
