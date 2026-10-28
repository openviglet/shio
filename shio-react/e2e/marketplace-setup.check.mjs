/*
 * SH1251 — the marketplace dialog turns a package into a site: parameters, a site name, the plan,
 * then the confirm, and the site exists afterwards.
 *
 * Driven against a running console rather than asserted in a unit test, because what the task
 * promised is a flow a curator walks: the confirm is disabled until a plan exists for exactly the
 * inputs on screen, and the result is a site the instance serves. Uses the built-in `blog`
 * package, which every instance has, so it needs no catalogue; the install half of the route is
 * held by ShMarketplaceInstallerTest against a real download.
 *
 * Usage
 *   node e2e/marketplace-setup.check.mjs
 *
 *   --base   console origin   (default http://localhost:5173, env SHIO_CONSOLE_URL)
 *   --api    backend origin   (default http://localhost:2710, env SHIO_URL)
 *   --headed watch it run
 *
 * Credentials: SHIO_USER / SHIO_PASSWORD. Exit code 1 when any step is not what it should be.
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
const SITE = `SH1251 Check ${Date.now()}`

/** Both locales, because a fresh browser gets the Portuguese console. */
const T = {
  plan: /^(Plan|Planejar)$/,
  apply: /^(Apply|Aplicar|Install and apply|Instalar e aplicar)$/,
  planned: /What would happen|O que aconteceria/,
  ready: /is ready|está pronto/,
}

const failures = []
const check = (ok, message) => {
  console.log(`  ${ok ? "ok  " : "FAIL"} ${message}`)
  if (!ok) failures.push(message)
}

const browser = await chromium.launch({ headless: !has("headed") })
const context = await browser.newContext({
  httpCredentials: { username: USER, password: PASS },
  extraHTTPHeaders: { Authorization: AUTH },
  viewport: { width: 1400, height: 900 },
})
const page = await context.newPage()
const errors = []
page.on("pageerror", (error) => errors.push(error.message))

await page.goto(`${BASE}/bento/admin/marketplace`, { waitUntil: "domcontentloaded", timeout: 20_000 })
await page.getByRole("button", { name: /^Blog\b/ }).first().click({ timeout: 20_000 })
const dialog = page.locator("[role=dialog]")
await dialog.waitFor({ timeout: 10_000 })

const apply = dialog.getByRole("button", { name: T.apply })
check(await apply.isDisabled(), "the confirm is disabled before any plan exists")

await dialog.locator("#marketplace-setup-site").fill(SITE)
await dialog.getByRole("button", { name: T.plan }).click()
await dialog.getByText(T.planned).waitFor({ timeout: 20_000 }).catch(() => {})
const planText = (await dialog.innerText()).replace(/\s+/g, " ")
check(T.planned.test(planText), "the plan is shown, and says nothing was written")
check(/create|criar/i.test(planText), "the plan lists what the apply would create")
check(await apply.isEnabled(), "a plan for these inputs enables the confirm")

const before = await (await fetch(`${API}/api/v2/site`, { headers: { Authorization: AUTH } })).json()
check(!before.some((site) => site.name === SITE), "the plan created no site")

await dialog.locator("#marketplace-setup-site").fill(`${SITE} `)
check(await apply.isDisabled(), "changing an input throws the plan away")
await dialog.locator("#marketplace-setup-site").fill(SITE)
await dialog.getByRole("button", { name: T.plan }).click()
await dialog.getByText(T.planned).waitFor({ timeout: 20_000 })

await apply.click()
await dialog.getByText(T.ready).first().waitFor({ timeout: 60_000 }).catch(() => {})
check(T.ready.test((await dialog.innerText()).replace(/\s+/g, " ")), "the confirm reports the site ready")

const after = await (await fetch(`${API}/api/v2/site`, { headers: { Authorization: AUTH } })).json()
check(after.some((site) => site.name === SITE), `the instance now has the site "${SITE}"`)
check(errors.length === 0, `no page errors${errors.length ? `: ${errors.join("; ")}` : ""}`)

await browser.close()
if (failures.length) {
  console.error(`\nSH1251 FAIL: ${failures.length} step(s) were not what they should be.`)
  process.exit(1)
}
console.log("\nSH1251 ok — the dialog planned, confirmed and built the site.")
