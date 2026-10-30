/*
 * SH1304 — every console surface that shows a site opens its published pages, not only the preview.
 *
 * The site list's row menu, the object browser's hero and the site edit page's actions each had one
 * way to look at a site, and it was `/preview/<site>/`: authenticated, bannered, draft-preferred.
 * Each now carries "View published site" beside "Preview", and on a site with no front end that is
 * `/sites/<furl>/`, the route a visitor receives. This clicks all three and reads the tab it opens.
 *
 * It needs a site with no `url` set. With `--site` absent it takes the one `provision.mjs` creates.
 *
 * Usage
 *   node e2e/published-site.check.mjs [--site "Console Check"] [--headed]
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

async function api(path) {
  const res = await fetch(`${API}/api/v2${path}`, { headers: { Authorization: AUTH } })
  if (!res.ok) throw new Error(`GET /api/v2${path} answered ${res.status}: ${await res.text()}`)
  return res.json()
}

const failures = []
const claim = (ok, message) => {
  console.log(`${ok ? "ok  " : "FAIL"} ${message}`)
  if (!ok) failures.push(message)
}

const site = (await api("/site")).find((each) => each.name === SITE)
if (!site) throw new Error(`no site '${SITE}' — run e2e/provision.mjs first, or pass --site`)
if (site.url?.trim()) throw new Error(`'${SITE}' has a front end (${site.url}); this check needs a site without one`)
const expected = `/sites/${encodeURIComponent(site.furl || site.name)}/`

const PUBLISHED = /^(view published site|ver site publicado)$/i
const PREVIEW = /^(preview|visualizar)$/i
// A card's menu and a row's menu are labelled differently, and the list picks grid or rows by count.
const MENU = new RegExp(`^(more actions|mais ações|actions for ${SITE}|ações para ${SITE})$`, "i")

const browser = await chromium.launch({ headless: !has("headed") })
const context = await browser.newContext({
  httpCredentials: { username: USER, password: PASS },
  extraHTTPHeaders: { Authorization: AUTH },
  viewport: { width: 1400, height: 900 },
})
const page = await context.newPage()
const errors = []
page.on("pageerror", (error) => errors.push(error.message))

/** Clicks, and answers the path of the tab the click opened. */
async function opened(click) {
  const tab = context.waitForEvent("page", { timeout: 15_000 })
  await click()
  const popup = await tab
  const url = new URL(popup.url() === "about:blank"
    ? await popup.waitForURL((u) => u.href !== "about:blank").then(() => popup.url())
    : popup.url())
  await popup.close()
  return url.pathname
}

// 1. The site list: the site's own menu, scoped to the card or row that names it.
await page.goto(`${BASE}/bento/content`, { waitUntil: "networkidle" })
await page.getByText(SITE, { exact: true }).first().waitFor({ timeout: 15_000 })
const holder = page.locator("article, li, [role=row], [data-slot=card]").filter({ hasText: SITE })
const menu = (await holder.count()) ? holder.last().getByRole("button", { name: MENU }) : page.getByRole("button", { name: MENU })
await menu.first().click()
claim(await page.getByRole("menuitem", { name: PREVIEW }).isVisible(), "the site list keeps Preview in the row menu")
const fromList = await opened(() => page.getByRole("menuitem", { name: PUBLISHED }).click())
claim(fromList === expected, `the site list opens the published site (${fromList}, expected ${expected})`)

// 2. The object browser: the hero, on the site itself (the route takes a site id as an object id).
await page.goto(`${BASE}/bento/content/object/${site.id}`, { waitUntil: "networkidle" })
claim(await page.getByRole("button", { name: PREVIEW }).first().isVisible(), "the object browser shows Preview")
const fromBrowser = await opened(() => page.getByRole("button", { name: PUBLISHED }).click())
claim(fromBrowser === expected, `the object browser opens the published site (${fromBrowser}, expected ${expected})`)

// 3. The site edit page: its actions.
await page.goto(`${BASE}/bento/content/site/${site.id}`, { waitUntil: "networkidle" })
const direct = page.getByRole("button", { name: PUBLISHED })
const fromEdit = await opened(async () => {
  if (await direct.count()) {
    await direct.first().click()
  } else {
    await page.getByRole("button", { name: MENU }).last().click()
    await page.getByRole("menuitem", { name: PUBLISHED }).click()
  }
})
claim(fromEdit === expected, `the site edit page opens the published site (${fromEdit}, expected ${expected})`)

claim(errors.length === 0, `no page errors (${errors.join(" | ") || "none"})`)
await browser.close()
if (failures.length) {
  console.error(`\n${failures.length} claim(s) failed`)
  process.exitCode = 1
}
