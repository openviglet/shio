/*
 * SH1237 — the console takes a picture of a site's published home, in a real browser.
 *
 * SH1218 shipped the capture: an offscreen same-origin frame on `/sites/<furl>/`, rasterised with
 * `html-to-image`, sent with the appearance digest `status` gave. Its node tests fake the frame and
 * the rasteriser, so whether a real Chromium can draw the frame's document, whether the delivered
 * page's fonts and images taint the canvas, and whether `toBlob` yields WebP were argued and not
 * observed. This observes them.
 *
 * It needs a site with a published home. With `--site` absent it takes the one `provision.mjs`
 * creates, and publishes its home first if that has not happened (an agent write, `post.publish`).
 *
 * Usage
 *   node e2e/site-thumbnail.check.mjs [--site "Console Check"] [--headed]
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
const SITE = flag("site", process.env.SHIO_SITE || "Console Check")
const USER = process.env.SHIO_USER || "admin"
const PASS = process.env.SHIO_PASSWORD || "admin"
const AUTH = "Basic " + Buffer.from(`${USER}:${PASS}`).toString("base64")

async function api(path, init = {}) {
  const res = await fetch(`${API}/api/v2${path}`, {
    ...init, headers: { Authorization: AUTH, "Content-Type": "application/json", ...(init.headers ?? {}) },
  })
  if (!res.ok) throw new Error(`${init.method ?? "GET"} /api/v2${path} answered ${res.status}: ${await res.text()}`)
  return res.headers.get("content-type")?.includes("json") ? res.json() : res.arrayBuffer()
}

const failures = []
const claim = (ok, message) => {
  console.log(`${ok ? "ok  " : "FAIL"} ${message}`)
  if (!ok) failures.push(message)
}

const site = (await api("/site")).find((each) => each.name === SITE)
if (!site) throw new Error(`no site '${SITE}' — run e2e/provision.mjs first, or pass --site`)

let status = await api(`/site/${site.id}/thumbnail/status`)
if (!status.home) {
  // The blueprint writes drafts (draft-by-default, P7), so the fixture's home is not live yet.
  await api("/agent/batch", {
    method: "POST",
    body: JSON.stringify({ ops: [{ op: "post.publish", address: `post:${site.name}/` }] }),
  })
  status = await api(`/site/${site.id}/thumbnail/status`)
}
claim(Boolean(status.home), `'${site.name}' has a published home to picture (digest ${status.home})`)

const browser = await chromium.launch({ headless: !has("headed") })
const context = await browser.newContext({
  httpCredentials: { username: USER, password: PASS },
  extraHTTPHeaders: { Authorization: AUTH },
  viewport: { width: 1400, height: 900 },
})
const page = await context.newPage()
const errors = []
page.on("pageerror", (error) => errors.push(error.message))
const asked = []
page.on("request", (request) => {
  if (/\/site\/[^/]+\/thumbnail(\/status)?(\?|$)/.test(request.url())) asked.push(`${request.method()} ${request.url()}`)
})

// A fresh context has no remembered asks, which is the state a first visit is in.
// A 403 first is the console's ordinary stale-CSRF path: its axios interceptor fetches a fresh token
// and sends the write again, so the answer that counts is the one after it.
const stored = page.waitForResponse(
  (response) => response.request().method() === "PUT" && /\/site\/[^/]+\/thumbnail\?/.test(response.url())
    && response.status() !== 403,
  { timeout: 60_000 },
).then(async (response) => ({ status: response.status(), body: await response.json().catch(() => null) }))
  .catch((error) => ({ status: 0, body: null, error: String(error) }))
await page.goto(`${BASE}/bento/content`, { waitUntil: "domcontentloaded" })
const put = await stored
claim(put.status === 200 && put.body?.stored === true,
  `the sites list captured the home and stored it (PUT ${put.status} ${JSON.stringify(put.body ?? put.error)})`)

const picture = await fetch(`${API}/api/v2/site/${site.id}/thumbnail`, { headers: { Authorization: AUTH } })
const bytes = picture.ok ? (await picture.arrayBuffer()).byteLength : 0
claim(picture.ok && bytes > 1024 && bytes < 512 * 1024,
  `the stored picture is an image of plausible size (${picture.status} ${picture.headers.get("content-type")}, ${bytes} bytes)`)

// A second visit with nothing republished asks nothing.
asked.length = 0
await page.goto(`${BASE}/bento/content`, { waitUntil: "domcontentloaded" })
await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {})
await page.waitForTimeout(1500)
const repeat = asked.filter((line) => !line.startsWith("GET") || line.includes("/status"))
claim(repeat.length === 0, `a second visit asks nothing (${repeat.length ? repeat.join(", ") : "no status or PUT"})`)

claim(errors.length === 0, `no page errors (${errors.join(" | ") || "none"})`)
await browser.close()
if (failures.length) {
  console.error(`\n${failures.length} claim(s) failed`)
  process.exitCode = 1
}
