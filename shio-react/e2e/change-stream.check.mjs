/*
 * SH1087 — the Activity page's live update, seen to arrive.
 *
 * ## Why this exists
 *
 * SH1005 gave the console its first client of SH967's stream, and every assertion about it was a
 * source scan: the hook says `EventSource`, it says `withCredentials`, it does not close on an
 * error. A stream that never delivers raises no error and fails no test; it looks exactly like an
 * instance where nothing happened, which on a quiet install is most of the time. So this opens the
 * page against a running console, writes through the agent API, and waits for the line.
 *
 * ## The two assertions
 *
 * 1. The "something changed" line appears without a reload.
 * 2. The listing did NOT move while it appeared. The page asks before redrawing because the row a
 *    curator is reading is the reason they came (P7), and a redraw would pass the first assertion
 *    while breaking the promise the line exists for.
 *
 * Usage
 *   node e2e/change-stream.check.mjs
 *
 *   --base   console origin   (default http://localhost:5173, env SHIO_CONSOLE_URL)
 *   --api    backend origin   (default http://localhost:2710, env SHIO_URL)
 *   --headed watch it run
 *
 * Credentials: SHIO_USER / SHIO_PASSWORD. Exit code 1 when the line never arrives or the list moved.
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

/** The line the page raises, in either language the console ships. */
const ARRIVED = /Something changed while you were reading|Algo mudou enquanto você lia/
/** The whole line, so stripping it from the page leaves nothing of it behind. */
const ARRIVED_LINE = /(?:Something changed while you were reading — show it|Algo mudou enquanto você lia — mostrar)/
const WAIT_MS = 15_000

/** One write through the agent surface: a site and a folder in it, so the trail moves. */
async function write() {
  const site = `sh1087-${Date.now().toString(36)}`
  const res = await fetch(`${API}/api/v2/agent/batch`, {
    method: "POST",
    headers: { Authorization: AUTH, "Content-Type": "application/json" },
    body: JSON.stringify({
      ops: [
        { op: "site.upsert", address: `site:${site}` },
        { op: "folder.upsert", address: `folder:${site}/news` },
      ],
    }),
  })
  if (!res.ok) throw new Error(`POST /agent/batch answered ${res.status}: ${await res.text()}`)
  return site
}

/** The page's text without the arrived line, which is the part allowed to change. */
async function listing(page) {
  // The bento shell renders the page in a `section`, not a `main`; the body is the whole screen.
  const text = await page.locator("body").innerText().catch(() => "")
  return text.replace(ARRIVED_LINE, "").replace(/\s+/g, " ").trim()
}

const browser = await chromium.launch({ headless: !has("headed") })
const context = await browser.newContext({
  httpCredentials: { username: USER, password: PASS },
  extraHTTPHeaders: { Authorization: AUTH },
  viewport: { width: 1400, height: 900 },
})
const page = await context.newPage()
let streams = 0
page.on("request", (request) => {
  if (request.url().includes("/api/v2/events")) streams += 1
})

// The console redirects /console/admin/activity to the bento shell's own path; either lands here.
await page.goto(`${BASE}/console/admin/activity`, { waitUntil: "domcontentloaded", timeout: 20_000 })
await page.getByRole("heading", { name: /^(Activity|Atividade)$/ }).first()
  .waitFor({ state: "visible", timeout: 15_000 })
// The stream opens after the first read; give both time to settle before measuring "before".
await page.waitForTimeout(2500)
const before = await listing(page)

const site = await write()
const started = Date.now()
const arrived = await page.getByText(ARRIVED).first()
  .waitFor({ state: "visible", timeout: WAIT_MS })
  .then(() => true, () => false)
const elapsed = Date.now() - started
const after = await listing(page)

await browser.close()

console.log(`SH1087: wrote site:${site}; ${streams} stream request(s) from the page.`)
if (!arrived) {
  console.error(`\nSH1087 FAIL: the "something changed" line did not appear within ${WAIT_MS}ms of a `
    + "write. A stream that never delivers looks exactly like a quiet instance, which is why this "
    + "check exists: the page opened, the write landed, and the screen said nothing.")
  process.exit(1)
}
console.log(`  the line appeared ${elapsed}ms after the write, without a reload.`)
if (after !== before) {
  console.error("\nSH1087 FAIL: the listing moved while the line appeared. The page is meant to ask "
    + "before redrawing, because the row a curator is reading is the reason they came (P7).\n"
    + `  before: ${before.slice(0, 200)}\n  after:  ${after.slice(0, 200)}`)
  process.exit(1)
}
console.log("\nSH1087 ok — an event arrived on an open Activity page, and the list waited to be asked.")
