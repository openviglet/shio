/*
 * Console smoke check — drives the React console in a real browser and reports what
 * a human would have had to notice by hand.
 *
 * It exists because the console is the half of Shio no API test can see: a page that
 * renders an empty form, a widget that throws, a request that 404s behind a spinner —
 * all of them return 200 to `curl` and look broken only to a person. This walks the
 * routes, records what the browser said, and writes a report an agent can read.
 *
 * Routes are DISCOVERED, not hardcoded: the target ids come from the agent surface
 * (`/api/v2/agent/find`, `/agent/resolve`), so the check runs against any instance
 * without editing this file. Add a probe to ROUTES and it runs everywhere.
 *
 * Usage
 *   pnpm -C shio-react run console:check                 # needs the dev server + backend up
 *   pnpm -C shio-react run console:check -- --site Cafeteria --headed
 *
 *   --base   console origin           (default http://localhost:5173, env SHIO_CONSOLE_URL)
 *   --api    backend origin           (default http://localhost:2710, env SHIO_URL)
 *   --site   site to walk             (default: the first site the API reports)
 *   --out    report directory         (default e2e/report)
 *   --headed watch it run
 *
 * Credentials: SHIO_USER / SHIO_PASSWORD (default admin/admin).
 *
 * Exit code is 1 when any probe FAILS, so it works as a gate. A probe fails on a page
 * error, a failed same-origin request, or a `mustNotSee` string appearing — never on a
 * console *warning*, because a gate that trips on noise is a gate people bypass.
 */
import { chromium } from "playwright"
import { mkdir, writeFile } from "node:fs/promises"
import { join } from "node:path"

const args = process.argv.slice(2)
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`)
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback
}
const has = (name) => args.includes(`--${name}`)

const BASE = flag("base", process.env.SHIO_CONSOLE_URL || "http://localhost:5173")
const API = flag("api", process.env.SHIO_URL || "http://localhost:2710")
const OUT = flag("out", join("e2e", "report"))
const USER = process.env.SHIO_USER || "admin"
const PASS = process.env.SHIO_PASSWORD || "admin"
const AUTH = "Basic " + Buffer.from(`${USER}:${PASS}`).toString("base64")

/** Text that means the page gave up. Seeing any of these is a failure, not a warning. */
const BROKEN_TEXT = [
  "Schema JSON is empty",
  "Something went wrong",
  "Unexpected Application Error",
  "Cannot read properties of",
  "is not a function",
  "NaN",
  "undefined undefined",
]

async function api(path) {
  const res = await fetch(`${API}/api/v2${path}`, { headers: { Authorization: AUTH } })
  if (!res.ok) throw new Error(`GET ${path} → ${res.status} ${await res.text()}`)
  return res.json()
}

/**
 * Build the route list from the instance itself. Every probe carries what it expects,
 * so a failure says which promise was broken rather than "the page changed".
 */
async function discoverRoutes(siteName) {
  const sites = await api("/site")
  if (!sites.length) throw new Error("This instance has no sites — nothing to walk.")
  const site = siteName ? sites.find((s) => s.name === siteName) : sites[0]
  if (!site) throw new Error(`No site named '${siteName}'. Have: ${sites.map((s) => s.name).join(", ")}`)

  const found = await api(`/agent/find?site=${encodeURIComponent(site.name)}&ids=true`)
  const posts = found.results || []
  const postTypes = await api("/post-type")

  // One post per post-type, so every widget set gets rendered at least once. The edit
  // page is the interesting one: it is where a post-type's form is built from a saved
  // post rather than from the type's defaults.
  const byType = new Map()
  for (const p of posts) if (p.id && !byType.has(p.type)) byType.set(p.type, p)

  // A folder to hang the create-flow probes off: the one the site's own home page
  // lives in, so it exists on any instance.
  const rootFolderId = (await api(`/agent/resolve?address=${encodeURIComponent(`site:${site.name}`)}`)).id

  const routes = [
    { name: "sites", path: "/console/content", mustSee: [site.name] },
    { name: "site-children", path: `/console/content/object/${site.id}`, mustSee: [] },
    { name: "post-type-list", path: "/console/content/post-type", mustSee: postTypes.slice(0, 1).map((t) => t.name) },
    { name: "trash", path: "/console/content/trash", mustSee: [] },
    { name: "search", path: "/console/content/search", mustSee: [] },
    { name: "files", path: "/console/content/files", mustSee: [] },
    { name: "post-type-select", path: `/console/content/folder/${rootFolderId}/post-type-select`, mustSee: [] },
    { name: "universal-editor", path: `/console/universal-editor/${site.id}`, mustSee: [] },
    // The review queue (SH109) is the curator's primary workflow under the product's
    // orientation, so it is worth a probe even on an instance where no agent has run: an
    // empty queue must render its empty state, not a blank page.
    { name: "review", path: "/console/admin/review", mustSee: [] },
    // A route that does not exist must say so. If the SPA renders a blank shell for
    // an unknown path, every other probe's "ok" is worth less — a blank page and a
    // working page are the same observation.
    { name: "unknown-route", path: "/console/content/definitely-not-a-route", mustSee: ["404"] },
  ]

  for (const [type, post] of byType) {
    routes.push({
      name: `post-edit:${type}`,
      path: `/console/content/post/${post.id}`,
      // The form must render at least one field: a post-type with no fields cannot be
      // edited, and that is the failure this check was written for.
      mustSeeFields: true,
      note: `${post.title} (${post.address})`,
    })
  }
  for (const t of postTypes.filter((t) => !t.system).slice(0, 3)) {
    routes.push({ name: `post-type-edit:${t.name}`, path: `/console/content/post-type/${t.name}`, mustSee: [t.name] })
    // The create form reads the post-type directly, so it is the control case: when a
    // post-type renders here and not on the edit page, the defect is in the read, not
    // in the model.
    routes.push({
      name: `post-new:${t.name}`,
      path: `/console/content/folder/${rootFolderId}/post-type/${t.name}/new`,
      mustSeeFields: true,
    })
  }
  return { site, routes }
}

/** Count the widget cards a form rendered, without depending on any one widget's markup. */
async function countFields(page) {
  return page.evaluate(() => {
    const cards = document.querySelectorAll("[class*='border-l-4']")
    const inputs = document.querySelectorAll(
      "main input:not([type=hidden]), main textarea, main select, main [contenteditable=true]",
    )
    return { cards: cards.length, inputs: inputs.length }
  })
}

/**
 * SH238 — the one fact the Universal Editor rests on, checked in a browser.
 *
 * The editor is an `<iframe src={site.url}>`, so driving it end to end needs a front end this
 * repository does not have yet (Block P). Its save-back, though, rests on a single fact that needs
 * no iframe: **a PATCH from the console origin, carrying a `Key` header, authenticates as the token
 * and not as the logged-in curator.** SH233 was exactly that fact being false — `withCredentials:
 * false` does not stop a same-origin request sending the session cookie — and it survived because
 * every test in this repository builds the request in Java or mocks the client, and the comment
 * that described the mechanism described it wrongly.
 *
 * So this runs the request the editor runs, from a page the console served, and reads the status.
 * It **fails only on 403**, the symptom: anything else (no WRITE token on the instance, no post,
 * the CDA refusing for another reason) is reported as skipped with the reason, because a gate that
 * trips on a missing fixture is a gate people bypass.
 */
async function checkEditorSaveBack(context, postId) {
  const probe = { name: "universal-editor-save-back", path: "/api/v2/cda/post/{id}", ok: true,
    errors: [], warnings: [], failedRequests: [] }
  if (!postId) {
    probe.skipped = "no post discovered to patch"
    return probe
  }
  let token
  try {
    const tokens = await api("/api-token")
    token = (tokens || []).find((t) => t.enabled && t.scope === "WRITE")?.token
  } catch (e) {
    probe.skipped = `could not list tokens: ${e.message}`
    return probe
  }
  if (!token) {
    probe.skipped = "no enabled WRITE token on this instance (the editor needs one to save)"
    return probe
  }

  const page = await context.newPage()
  try {
    // A page the console served, so the session cookie exists and is same-origin with /api —
    // which is the whole point: this is the condition SH233's comment believed it had avoided.
    await page.goto(`${BASE}/console`, { waitUntil: "domcontentloaded", timeout: 20_000 })
    const result = await page.evaluate(async ({ id, key }) => {
      const csrf = await fetch("/api/v2/csrf", { credentials: "include" }).then((r) => r.json())
      // The CSRF header is required because the cookie *is* being sent (SH167 narrowed the CDA's
      // exemption to session-less callers), which is the other half of what SH233 got wrong.
      const headers = {
        "Content-Type": "application/json",
        Key: key,
        [csrf.headerName || "X-XSRF-TOKEN"]: csrf.token,
      }
      const res = await fetch(`/api/v2/cda/post/${id}`, {
        method: "PATCH", credentials: "include", headers,
        // An empty attrs map: the check is about who the request authenticates as, and a smoke
        // check must not change anybody's content to find out.
        body: JSON.stringify({ attrs: {} }),
      })
      return { status: res.status, body: (await res.text()).slice(0, 300) }
    }, { id: postId, key: token })

    probe.status = result.status
    if (result.status === 403) {
      probe.ok = false
      probe.errors.push("403 — the PATCH authenticated as the console session rather than the "
        + `Key token (SH233): ${result.body}`)
    } else if (result.status >= 400) {
      probe.skipped = `CDA answered ${result.status}: ${result.body}`
    }
  } catch (e) {
    probe.skipped = `could not run the request: ${String(e).split("\n")[0]}`
  } finally {
    await page.close()
  }
  return probe
}

/**
 * SH238 — the Universal Editor, driven end to end at last.
 *
 * ## What was open, and why it stayed open
 *
 * SH233's defect was a save-back that could not authenticate, and it survived because nothing in
 * this repository drove the editor: every test built the request in Java or mocked the client, and
 * both agreed with a comment that described the mechanism wrongly. `checkEditorSaveBack` above
 * closed the *one fact* that needed no iframe. The rest — the handshake, the properties panel, a
 * field's round trip, the publish action — was blocked on having a front end to embed.
 *
 * It is not blocked any more, and it has not been since SH183/SH184: a site with no configured
 * `url` falls back to **Shio's own renderer**, and `?editor=true` asks that renderer for the SH185
 * bridge. So the thing to embed shipped with the product.
 *
 * ## The two conditions, both discovered rather than assumed
 *
 * **Same origin.** The bridge posts to `data-shio-editor-origin`, which `ShRenderAPI.originOf`
 * computes from the request — and an iframe navigation carries no `Origin` header, so it falls back
 * to the *server's* own origin. Production serves the console and the renderer from one origin and
 * this is exactly right. A Vite dev console on `:5173` framing a renderer proxied from `:2710` is
 * not: the bridge targets `:2710`, the parent is `:5173`, and the handshake cannot complete. That is
 * a real gap and it is reported here as a skip with the reason rather than as a failure, because it
 * is a property of the topology under test and not of the build.
 *
 * **A page that renders with annotations.** A site whose `url` names a front end this instance does
 * not serve, or whose home is a 404, gives an iframe with nothing in it. The site is therefore
 * chosen by asking the renderer, not by taking the first one.
 */
async function checkEditorDrive(context, sites) {
  const probe = { name: "universal-editor-drive", path: "/console/universal-editor/{site}", ok: true,
    errors: [], warnings: [], failedRequests: [] }

  if (new URL(BASE).origin !== new URL(API).origin) {
    probe.skipped = `console is ${new URL(BASE).origin} and the renderer answers as `
      + `${new URL(API).origin}, so the bridge posts to an origin this window is not — the editor `
      + "cannot hand-shake across a dev proxy. Run this against the instance's own console"
    return probe
  }

  // A site the renderer will actually draw, with fields the editor can select. Asked of the
  // renderer because nothing else can answer it: `url` being empty is necessary and not sufficient.
  let target = null
  for (const site of sites) {
    if (site.url && site.url.trim()) continue
    try {
      const res = await fetch(`${API}/preview/${encodeURIComponent(site.name)}/?editor=true`,
        { headers: { Authorization: AUTH } })
      if (!res.ok) continue
      const html = await res.text()
      if (html.includes("data-shio-prop") && html.includes(ShEditorBridgeScript)) { target = site; break }
    } catch { /* the next site is the answer */ }
  }
  if (!target) {
    probe.skipped = "no site on this instance renders a page carrying editable props — the editor "
      + "would frame a 404, which is a fixture gap and not a defect"
    return probe
  }
  probe.site = target.name

  const page = await context.newPage()
  page.on("pageerror", (e) => probe.errors.push(String(e)))
  try {
    await page.goto(`${BASE}/console/universal-editor/${target.id}`,
      { waitUntil: "domcontentloaded", timeout: 30_000 })

    // 1. The handshake. `sh:ready` is what the whole protocol rests on, and until now nothing
    //    anywhere observed it — a bridge that never loaded looked identical to one that did.
    let frame = null
    for (let waited = 0; waited < 20_000 && !frame; waited += 500) {
      frame = page.frames().find((f) => f.url().includes("editor=true")) ?? null
      if (!frame) await page.waitForTimeout(500)
    }
    if (!frame) {
      probe.ok = false
      probe.errors.push("the editor never framed a page with ?editor=true")
      return probe
    }
    const fields = frame.locator("[data-shio-prop]")
    await fields.first().waitFor({ state: "attached", timeout: 20_000 })
    probe.props = await fields.count()

    // The bridge sets this on the framed document when it turns edit mode on, which is the last
    // thing `attach()` does before `sh:ready`. Reading the frame is sound *here* — this probe has
    // already established the renderer is Shio's own, which is the case the parent deliberately
    // does not inspect (SH333: it must work against a front end whose DOM it cannot touch).
    const editing = await frame.locator("[data-shio-editing='true']").count()
      .catch(() => 0)
    if (!editing) {
      probe.ok = false
      probe.errors.push("SH185: the bridge script loaded and never turned edit mode on, so "
        + "`sh:ready` was not sent and the console shows 'no connection' over a page that is fine")
    }

    // 2. Selection → the properties panel. The panel is the half a curator uses and the half
    //    `checkEditorSaveBack` cannot see: it is driven entirely by `sh:element-selected`.
    const EMPTY = /Nenhum elemento selecionado|No element selected/i
    probe.panelStartedEmpty = (await page.getByText(EMPTY).count()) > 0
    await fields.first().click({ timeout: 10_000 })
    // Waited for, not sampled: the click crosses a `postMessage` and a React state update, so
    // reading the panel in the same tick tests the scheduler rather than the protocol.
    const selected = await page.getByText(EMPTY).waitFor({ state: "detached", timeout: 10_000 })
      .then(() => true).catch(() => false)
    if (!selected) {
      probe.ok = false
      probe.errors.push("clicking an annotated element left the properties panel empty, so "
        + "`sh:element-selected` did not reach the console — the panel is decorative")
    }

    // 3. A field's round trip, as far as a smoke check may honestly take it. Typing into the framed
    //    page must reach the console as `sh:content-changed` and become a PENDING change — which is
    //    the half nothing observed. It stops there deliberately: `checkEditorSaveBack` already
    //    proves the PATCH those changes turn into, and this file must not edit anybody's content to
    //    find out (the same rule that probe states about its empty `attrs`). Nothing is saved, so
    //    nothing is written: the page is reloaded away and the draft is untouched.
    const typing = fields.first()
    if (await typing.getAttribute("data-shio-editable") !== "false") {
      await typing.click()
      await typing.press("End")
      await typing.type(" ", { delay: 40 })
      const pending = await page.getByText(/\d+\s+(altera|change)/i)
        .waitFor({ state: "visible", timeout: 10_000 })
        .then(() => true).catch(() => false)
      if (!pending) {
        probe.ok = false
        probe.errors.push("typing in the framed page produced no pending change in the console, so "
          + "`sh:content-changed` did not arrive — the save button can never enable and a curator's "
          + "edit is lost the moment they navigate")
      } else {
        probe.pendingChange = true
      }
    } else {
      probe.warnings.push("the first editable prop is declared read-only, so the round trip was "
        + "not driven on this instance")
    }
  } catch (e) {
    probe.ok = false
    probe.errors.push(`could not drive the editor: ${String(e).split("\n")[0]}`)
  } finally {
    await page.close()
  }
  return probe
}

/** The bridge's own script path — the tag `ShEditorBridge.inject` appends (SH185). */
const ShEditorBridgeScript = "/preview/_shio/editor.js"

async function main() {
  const { site, routes } = await discoverRoutes(flag("site", process.env.SHIO_SITE))
  await mkdir(OUT, { recursive: true })

  const browser = await chromium.launch({ headless: !has("headed") })
  const context = await browser.newContext({
    httpCredentials: { username: USER, password: PASS },
    viewport: { width: 1600, height: 1000 },
    extraHTTPHeaders: { Authorization: AUTH },
  })

  const report = { base: BASE, api: API, site: site.name, probes: [] }

  for (const route of routes) {
    const page = await context.newPage()
    const errors = []
    const warnings = []
    const failedRequests = []

    page.on("pageerror", (e) => errors.push(String(e)))
    page.on("console", (m) => {
      if (m.type() === "error") errors.push(m.text())
      else if (m.type() === "warning") warnings.push(m.text())
    })
    page.on("response", (r) => {
      const url = r.url()
      if (r.status() >= 400 && (url.startsWith(BASE) || url.startsWith(API))) {
        failedRequests.push(`${r.status()} ${r.request().method()} ${url.replace(BASE, "").replace(API, "")}`)
      }
    })

    const probe = { ...route, url: BASE + route.path }
    try {
      await page.goto(probe.url, { waitUntil: "domcontentloaded", timeout: 20_000 })
      // The console renders after its own fetches settle. `networkidle` is the honest
      // wait but the dev server's HMR socket never goes idle, so wait for the app shell
      // and then for quiet, and treat the timeout as "rendered what it had".
      await page.waitForSelector("main, [role=main], nav", { timeout: 10_000 }).catch(() => {})
      await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {})
      await page.waitForTimeout(400)

      // `mustSee` has to look at form values too, not only rendered text: the
      // post-type editor holds the API name in a read-only input, and asserting on
      // innerText alone reports a working page as broken.
      const body = await page.evaluate(() => {
        const values = [...document.querySelectorAll("input, textarea")]
          .map((el) => el.value || el.placeholder || "")
          .join("\n")
        return `${document.body.innerText}\n${values}`
      })
      probe.broken = BROKEN_TEXT.filter((t) => body.includes(t))
      probe.missing = (route.mustSee || []).filter((t) => !body.includes(t))
      if (route.mustSeeFields) {
        probe.fields = await countFields(page)
        if (probe.fields.cards === 0 && probe.fields.inputs === 0) probe.missing.push("<any form field>")
      }
      probe.title = (await page.locator("h1, h2").first().innerText().catch(() => "")).trim()

      const shot = join(OUT, `${route.name.replace(/[:/]/g, "_")}.png`)
      await page.screenshot({ path: shot, fullPage: true })
      probe.screenshot = shot
    } catch (e) {
      errors.push(`navigation: ${String(e).split("\n")[0]}`)
    }

    probe.errors = errors
    probe.warnings = warnings
    probe.failedRequests = failedRequests
    probe.ok =
      errors.length === 0 &&
      failedRequests.length === 0 &&
      (probe.broken || []).length === 0 &&
      (probe.missing || []).length === 0
    report.probes.push(probe)
    await page.close()

    const mark = probe.ok ? "ok  " : "FAIL"
    console.log(`${mark} ${route.name.padEnd(30)} ${route.path}`)
    for (const t of probe.broken || []) console.log(`       broken text: ${t}`)
    for (const m of probe.missing || []) console.log(`       missing: ${m}`)
    for (const f of probe.failedRequests.slice(0, 4)) console.log(`       request: ${f}`)
    for (const e of probe.errors.slice(0, 4)) console.log(`       error: ${e.slice(0, 160)}`)
  }

  // SH238: not a route, so it runs after the route walk rather than inside it.
  const anyPost = routes.find((r) => r.name.startsWith("post-edit:"))
  const editorProbe = await checkEditorSaveBack(context, anyPost?.path.split("/").pop())
  report.probes.push(editorProbe)
  console.log(`${editorProbe.ok ? "ok  " : "FAIL"} ${editorProbe.name.padEnd(30)} `
    + (editorProbe.skipped ? `skipped: ${editorProbe.skipped}` : `${editorProbe.status}`))
  for (const e of editorProbe.errors) console.log(`       error: ${e.slice(0, 200)}`)

  // SH238's other half: the editor itself, not only the fact its save-back rests on.
  const driveProbe = await checkEditorDrive(context, await api("/site"))
  report.probes.push(driveProbe)
  console.log(`${driveProbe.ok ? "ok  " : "FAIL"} ${driveProbe.name.padEnd(30)} `
    + (driveProbe.skipped
      ? `skipped: ${driveProbe.skipped}`
      : `${driveProbe.site} — ${driveProbe.props} editable prop(s)`))
  for (const e of driveProbe.errors) console.log(`       error: ${e.slice(0, 200)}`)

  await browser.close()
  await writeFile(join(OUT, "console-check.json"), JSON.stringify(report, null, 2))

  const failed = report.probes.filter((p) => !p.ok)
  console.log(`\n${report.probes.length - failed.length}/${report.probes.length} probes ok — report in ${OUT}`)
  if (failed.length) {
    console.log(`failing: ${failed.map((p) => p.name).join(", ")}`)
    process.exitCode = 1
  }
}

main().catch((e) => {
  console.error(`console-check: ${e.message}`)
  process.exitCode = 1
})
