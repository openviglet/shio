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
 *   --strict a skipped editor drive is a failure — what CI passes (SH238/SH659)
 *
 * Credentials: SHIO_USER / SHIO_PASSWORD (default admin/admin).
 *
 * Exit code is 1 when any probe FAILS, so it works as a gate. A probe fails on a page
 * error, a failed same-origin request, or a `mustNotSee` string appearing — never on a
 * console *warning*, because a gate that trips on noise is a gate people bypass.
 */
import { chromium } from "playwright"
import { mkdir, writeFile } from "node:fs/promises"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const args = process.argv.slice(2)
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`)
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback
}
const has = (name) => args.includes(`--${name}`)

const BASE = flag("base", process.env.SHIO_CONSOLE_URL || "http://localhost:5173")
const API = flag("api", process.env.SHIO_URL || "http://localhost:2710")
/*
 * SH902 — anchored to this script rather than to the cwd, which is not tidiness.
 *
 * `.gitignore` carries the anchored rule `/shio-react/e2e/report/`, so the default only landed
 * inside it when the script was run from `shio-react/`. Run from the repo root — which is where
 * the three gates are run from, and where `run-commit.cmd` stages — it wrote `<root>/e2e/report/`,
 * outside the ignore, and `git add *` was one command away from committing twenty-five
 * screenshots. Two cwds, two output paths, one of them silently unignored (P5).
 */
const OUT = flag("out", join(dirname(fileURLToPath(import.meta.url)), "report"))

/**
 * A skipped `universal-editor-drive` is a **failure** (SH238/SH659).
 *
 * <p>A skip is the right answer on a laptop, where the topology and the fixtures are whatever
 * they happen to be. It is the wrong answer in CI, where both are provisioned by this
 * repository one step earlier: there, "no site renders editable props" is not a fixture gap
 * somebody will notice, it is this instrument silently not running — and a probe that skips
 * keeps `ok: true`, so it counts toward "N/N probes ok" and leaves the exit code at 0.
 *
 * <p>That is not hypothetical. SH664 — the fixture declaring the instance as its own front
 * end, which excluded it from the drive's own candidate loop — survived from the commit that
 * introduced `provision.mjs`, and the drive reported ok the whole time. SH655 reached the same
 * conclusion one instrument over and is the precedent: a check nobody is running is worse than
 * a check that fails, because it reads as coverage.
 *
 * <p>Explicit flag rather than `process.env.CI`, so `cli/test/ci-gates.test.mjs` can assert the
 * workflow passes it. A signal nothing asserts is the same absent gate with an extra step.
 */
const STRICT = has("strict")
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
    /*
     * SH897 — the bento route tree. Every route above is a console route, so left alone this
     * script would run green through the whole of Block AR while never visiting a single
     * migrated surface — which is worse than not running, because a green check is counted as
     * evidence.
     *
     * `bento: true` sends the probe through `checkBentoSurface`, which reads the frost off
     * computed style rather than looking for class names. Block AR adds one line here per
     * migrated surface.
     */
    { name: "bento-root", path: "/bento", bento: true, mustSee: [] },
  ]

  /*
   * SH901 — the first bento surface that carries a save-bar morph, which is what
   * `checkSaveBarMorph` has been reporting "no surface carries one yet" about since SH897. The
   * post editor is the same component the console mounts, under the chrome provider, so this
   * probe also proves the adapter path end to end on the heaviest form in the product.
   */
  // Not a `File`: a static file has no post editor at all, which is the exclusion the
  // per-type loop below already makes and which SH921 is filed against. Picking the first
  // entry blindly picked exactly that, and the probe failed on the same defect twice.
  const anyPost = [...byType.entries()].find(([type]) => type !== "File")?.[1]
  if (anyPost) {
    routes.push({
      name: "bento-post-edit",
      path: `/bento/content/post/${anyPost.id}`,
      bento: true,
      mustSeeFields: true,
      note: `${anyPost.title} (${anyPost.address})`,
    })
  }

  for (const [type, post] of byType) {
    /*
     * SH897 — `File` is a static file, and a static file has no post editor.
     *
     * `/agent/find` returns one as `type: "File"` alongside every post, which is correct — it is
     * content and it is addressable — but the row it points at lives in `/api/v2/staticfile/{id}`
     * (200) and not in `/api/v2/post-unified/{id}` (404). So this loop was generating a
     * `post-edit:File` probe that opened a page which cannot exist, and the check failed on a
     * defect in itself. The curator's route to a file is the static-file list, which `files`
     * already probes.
     *
     * The 404 it drove into is filed as SH921: it carries **no body at all**, where P6 says every
     * 4xx teaches. That is the finding this probe was worth having.
     */
    if (type === "File") continue
    routes.push({
      name: `post-edit:${type}`,
      path: `/console/content/post/${post.id}`,
      // The form must render at least one field: a post-type with no fields cannot be
      // edited, and that is the failure this check was written for.
      mustSeeFields: true,
      note: `${post.title} (${post.address})`,
    })
  }
  /*
   * SH902 — the post-type slice under bento chrome. Three surfaces, one probe each, because the
   * three fail in different ways and only one of them is visible in a source scan.
   *
   * The **list** and **new** pages are the ones that caught SH902's real defect: both were written
   * with a header-less `AdaptiveSectionCard`, which falls back to a console card in *both* eras
   * and renders a page that looks finished and is half-migrated. `checkBentoSurface` reads
   * `backdrop-filter` off computed style, so it sees that and a class-name scan does not.
   *
   * The **edit** page is the second surface in the product to carry a save-bar morph, after
   * SH901's post editor — and the one that answers the question the section's name-key rule
   * raises: the hero edits the *display title*, while `name` stays a read-only input with its own
   * Rename action (SH117/SH163). A hero that let the API name be typed over would be a rename
   * with no confirmation, which is P7 exactly.
   */
  const bentoType = postTypes.find((t) => !t.system)
  if (bentoType) {
    routes.push({
      name: "bento-post-type-list",
      path: "/bento/content/post-type",
      bento: true,
      mustSee: [bentoType.name],
    })
    routes.push({
      name: `bento-post-type-edit:${bentoType.name}`,
      path: `/bento/content/post-type/${bentoType.name}`,
      bento: true,
      mustSee: [bentoType.name],
    })
    routes.push({ name: "bento-post-type-new", path: "/bento/content/post-type/new", bento: true, mustSee: [] })
  }

  /*
   * SH903 — the three surfaces where rendering nothing is the normal case.
   *
   * Probed in **both** chromes on purpose, which none of the earlier bento probes bothered with.
   * These pages are one component under a provider, so a console regression and a bento one are
   * the same edit, and a check that only ever opens the bento route would let the console half
   * break silently for as long as Block AR runs.
   *
   * `search` opens with no query, which is its **third** state — neither results nor an empty
   * result, just the form. That state used to render nothing below the form at all, and this
   * probe is what found it: it opens the route without searching, so it lands on exactly the
   * branch nobody had looked at. SH903 gave that branch its own words.
   */
  for (const [name, path] of [
    ["search", "/content/search"],
    ["trash", "/content/trash"],
    ["files", "/content/files"],
    /*
     * SH904 — the identity slice. The **create** routes are probed alongside the lists because
     * they are where this slice's one real hazard lives: a group is nothing but identity, so its
     * form body is empty, and `autosaveOnly` hides Save on an existing entity but deliberately
     * keeps it on a new one. That is the SH899 dead-button shape, and only opening the page
     * catches it.
     */
    ["groups", "/admin/groups"],
    ["roles", "/admin/roles"],
    ["users", "/admin/users"],
    /*
     * SH905 — the eleven infrastructure surfaces, every one of them probed.
     *
     * §LV.8's warning was that a slice this size "becomes a mechanical sweep with no attention
     * left over", and the answer to that is not care, it is coverage: eleven probes cost one line
     * each and they are what makes the difference between migrating eleven pages and believing
     * you did. The five lists are one component, so a defect in it is a defect in all five — and
     * the six editors are six different 130-to-300-line forms that share nothing but their shape.
     */
    ["tokens", "/admin/tokens"],
    ["auth-providers", "/admin/auth-providers"],
    ["exchange-providers", "/admin/exchange-providers"],
    ["webhooks", "/admin/webhooks"],
    ["tenants", "/admin/tenants"],
    ["email-config", "/admin/email-config"],
    // SH906 — the review queue first, because it is the surface §LV.9 says must not be the last
    // thing that still looks old, and the only one of the three with a stated done-when.
    ["review", "/admin/review"],
    ["activity", "/admin/activity"],
    ["marketplace", "/admin/marketplace"],
    /*
     * The create route of every editor that has one. These are the pages the lists send a curator
     * to, they render the whole form with no data behind it, and — as SH904 found the hard way —
     * a route whose param name does not match what the component reads leaves `isNew` false and
     * the page blank. Two of the five had exactly that (`:providerId` against `authProviderId`),
     * and nothing but opening them would have said so.
     */
    ["token-new", "/admin/tokens/new"],
    ["auth-provider-new", "/admin/auth-providers/new"],
    ["exchange-provider-new", "/admin/exchange-providers/new"],
    ["webhook-new", "/admin/webhooks/new"],
    ["tenant-new", "/admin/tenants/new"],
  ]) {
    routes.push({ name: `bento-${name}`, path: `/bento${path}`, bento: true, mustSee: [] })
  }
  /*
   * The two create pages, declared `noPanel` — a group and a role are `{name, description}` and
   * both are hero fields, so these pages genuinely have no body and never will. Declared here
   * rather than exempted inside the check, so the claim sits next to the route that makes it.
   *
   * They are probed at all because this is where the slice's one real hazard lives: `autosaveOnly`
   * hides Save on an existing entity and deliberately keeps it on a new one, which is the SH899
   * dead-button shape with the button still on screen.
   */
  for (const [name, path] of [["group-new", "/admin/groups/new"], ["role-new", "/admin/roles/new"]]) {
    routes.push({ name: `bento-${name}`, path: `/bento${path}`, bento: true, noPanel: true, mustSee: [] })
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

/**
 * SH897 — what makes a bento surface a *bento* surface, asked of a rendered page.
 *
 * ## Why this is not "does the class name appear"
 *
 * A source scan already knows which pages import the layer — that is
 * `console-chrome-census.test.mjs`, and it is cheap and runs everywhere. What it cannot see is
 * the failure this check exists for: the components mount, the markup carries every `bento-*`
 * class, and **the stylesheet is not loaded**, so the page renders working, tested, completely
 * unstyled divs. SH887 found that shape once already; nothing in `tsc`, in an import scan, or in
 * a unit test says a word about it.
 *
 * So each fact below is read off *computed style* wherever a computed style can carry it. A
 * frosted panel that is not frosted is the whole point.
 *
 * ## The three facts, and the one that has no subject yet
 *
 * - **the hero is present** — a bento surface leads with its own identity, which is what replaces
 *   the console's breadcrumb bar.
 * - **the frosted card is frosted** — `backdrop-filter` is not `none` on a `bento-glass` surface.
 * - **the save bar appears on scroll and not before** — the morph. `BentoFormHero` composes both
 *   halves; SH892 deferred it, and SH901/SH902 are the tasks that put one on a real surface.
 *
 * The third has no subject on this instance today, and that is **reported rather than passed
 * over**. This file's own history is the reason: SH238/SH659 found that a probe which skips keeps
 * `ok: true`, counts toward "N/N probes ok", and leaves the exit code at 0 — so a check nobody is
 * running reads as coverage. `morph: "no surface carries one yet"` is printed on every run, and
 * the count moves the day a migrated editor appears.
 */
async function checkBentoSurface(page, noPanel = false) {
  return page.evaluate((noPanel) => {
    window.__shioNoPanel = noPanel

    const findings = []

    /*
     * A hero is a non-empty `h1` **or** the entity shell's editable one.
     *
     * SH904 — the `h1` rule was written against `BentoHero`/`BentoFormHero`, which render one.
     * `BentoEntityShell` does not: its title is a `BentoInlineEdit`, and that component renders an
     * `<input>` (or a `<textarea>` when multiline) carrying the title as its *value*, with
     * `headlineFallback` as the placeholder. So on a create page — where the title is empty by
     * definition, because the entity does not exist yet — there is no heading text anywhere in
     * the document and this reported a correct page as broken.
     *
     * It was worse than a false negative: **no entity-shell page could ever have passed**, and
     * the first four to be probed were the first four written. The rule below asks the question
     * the original meant — is there a hero and does it name something — of both shapes.
     */
    const h1 = document.querySelector("h1")
    const editable = h1?.querySelector("input, textarea")
    const named = h1
      && (h1.innerText.trim()
        || (editable?.value ?? "").trim()
        || (editable?.getAttribute("placeholder") ?? "").trim())
    if (!named) findings.push("no hero heading on a bento surface")

    /*
     * Read the frost off computed style, not off the class list — every `bento-*` class is
     * still in the markup when the stylesheet is absent.
     *
     * `backdrop-filter` and nothing else. The first version of this also accepted a non-zero
     * `border-radius`, and that made it **vacuous**: `rounded-3xl` is a Tailwind class on the
     * component itself, so the radius is 24px with or without `bento.css`. Verified by deleting
     * the import and re-running — the page rendered as flat, borderless, unstyled divs, the
     * screenshot showed it plainly, and this probe said `ok`. With the stylesheet loaded the
     * value here is `blur(18px) saturate(1.8)`; without it, `none`.
     */
    const glass = document.querySelector(".bento-glass, .bento-tile")
    if (!glass) {
      /*
       * SH904 — a surface with no panel is a finding, *unless* the probe said so in advance.
       *
       * The create page for an entity that is nothing but identity has no body: a group is
       * `name` + `description`, both hero fields, so there is genuinely no panel to frost and
       * never will be. Exempting that shape globally would have made this rule vacuous for every
       * page — the failure it exists to catch (SH902's header-less section cards) is exactly "a
       * page that renders no frosted surface". So the exemption is per-probe and it is declared
       * next to the route, where somebody reviewing the diff sees the claim being made.
       */
      if (!window.__shioNoPanel) findings.push("no frosted surface (.bento-glass/.bento-tile) rendered")
    } else {
      const style = getComputedStyle(glass)
      const frost = style.backdropFilter || style.webkitBackdropFilter || "none"
      if (frost === "none") {
        findings.push(
          "frosted surface has no backdrop-filter — bento.css is not loaded. The components "
          + "mount and every class name is present, so nothing else in this repository notices.")
      }
    }

    // The rail is the shell, and it must not be sitting under a sidebar provider.
    if (!document.querySelector("nav[aria-label]")) findings.push("no nav rail")
    if (document.querySelector("[data-slot='sidebar-wrapper'], [data-sidebar]")) {
      findings.push("a sidebar provider is mounted inside the bento tree")
    }
    if (!document.querySelector(".bento-rail-gutter")) {
      findings.push("the routed page is not inside bento-rail-gutter, so it renders under the rail")
    }

    // SH901 — this used to also return a `morph` boolean, computed from two selectors the
    // package does not render and read by nothing. `checkSaveBarMorph` is the authority on
    // the morph; a second, wrong answer sitting beside it is worse than none.
    return { findings }
  }, noPanel)
}

/**
 * The save-bar morph: nothing fixed before scrolling, a bar after. Runs only where a surface
 * actually carries one — and says so when none does, rather than returning a quiet pass.
 */
async function checkSaveBarMorph(page) {
  /*
   * SH901 — the selectors, measured off the package rather than invented.
   *
   * SH897 wrote this check against `[data-bento-save-bar], .bento-save-bar`. **Neither exists.**
   * `BentoScrollSaveBar` renders a `.bento-save-bar-spacer` and wraps the fixed bar in
   * `.bento-fade-in`; `BentoSaveBar` itself is a `.bento-glass` panel with no id of its own. So
   * the check could never have found a morph on any page, and its honest-sounding report — "no
   * surface carries one yet" — would have gone on being true-by-construction after one arrived.
   *
   * It took until a surface actually had a morph for that to be visible, which is the whole
   * argument for giving a check its subject as early as possible.
   */
  const state = () => page.evaluate(() => {
    const spacer = document.querySelector(".bento-save-bar-spacer")
    const fade = document.querySelector(".bento-fade-in")
    return {
      present: Boolean(spacer),
      // The morph is an opacity fade, not a mount: the bar is in the DOM from the first paint
      // and `pointer-events: none` until it fades in. Presence proves nothing about visibility.
      visible: fade ? getComputedStyle(fade).opacity !== "0" : false,
      scrollable: document.body.scrollHeight > innerHeight + 40,
    }
  })

  const top = await state()
  if (!top.present) return { ran: false, why: "no surface carries a save-bar morph yet (SH901)" }
  if (!top.scrollable) {
    return { ran: false, why: "the surface carries a morph but is too short to scroll at this viewport" }
  }

  const findings = []
  if (top.visible) findings.push("the save bar is visible before scrolling")

  await page.evaluate(() => scrollTo(0, document.body.scrollHeight))
  await page.waitForTimeout(700)
  const bottom = await state()
  if (!bottom.visible) findings.push("the save bar did not appear after scrolling")
  await page.evaluate(() => scrollTo(0, 0))

  return { ran: true, findings }
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
  //
  // Ours first (SH659). Every site here is a candidate, and until this the winner was whichever
  // the API listed earliest — on a developer's instance that is somebody's real site, and the
  // publish step below may only touch content this repository created. Ordering rather than
  // filtering: an instance that has no fixture still gets the drive, it just does not get publish.
  let target = null
  for (const site of [...sites].sort((a, b) => Number(isOurs(b)) - Number(isOurs(a)))) {
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

    // 4. Publish — the half SH659 left, and the reason it left it has a boundary now (SH659).
    //
    // The reason was right and still is: *a smoke check must not edit anybody's content*. What
    // changed is that SH664 made the site `provision.mjs` creates the drive's own target, so there
    // is now a page this repository owns end to end. Publishing that one is not editing anybody's
    // content; publishing whatever the API listed first is, which is why the guard reads the
    // provisioner's description and not the `--site` the caller passed.
    //
    // It is also the only step that proves the button: `checkEditorSaveBack` runs the PATCH a
    // *pending change* becomes, and `persist(true)` is a different path — it patches with
    // `publish:true`, and publishes the selected post outright when nothing was edited.
    if (!isOurs(target)) {
      probe.publishSkipped = `'${target.name}' was not created by e2e/provision.mjs, and nothing `
        + "here may publish a site somebody else is using — run `console:provision` first"
    } else if (!probe.pendingChange) {
      probe.publishSkipped = "no pending change reached the console, so there is nothing to publish "
        + "and a green publish here would be green over an empty batch"
    } else {
      const publish = page.getByRole("button", { name: /^(Publicar|Publish)$/ })
      await publish.click({ timeout: 10_000 })
      // The toast, not the button's own state: `persist` clears the pending map in the same tick
      // it succeeds *and* in the tick it fails, so an empty change count says nothing about which.
      const published = await page.getByText(/^(Publicado|Published)$/).waitFor(
        { state: "visible", timeout: 15_000 }).then(() => true).catch(() => false)
      if (!published) {
        probe.ok = false
        probe.errors.push("SH659: the editor's Publish reported nothing — `persist(true)` is the "
          + "one path `universal-editor-save-back` does not cover, since it patches with "
          + "`publish:true` rather than saving a draft")
      } else {
        probe.published = true
      }
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

/**
 * What `e2e/provision.mjs` writes as the fixture site's description, and the only way this file
 * can tell a site **this repository created** from one somebody is using (SH659).
 *
 * <p>The description and not the name: `--site` is whatever the caller passed, so matching on it
 * would let `console:check --site Demo` publish somebody's home page. The description is written
 * by our own provisioner and by nothing else.</p>
 *
 * <p>`test/e2e-fixture-site.test.mjs` holds the two strings together — a marker that drifts from
 * the file that writes it silently turns publishing off, which reads exactly like a clean run.</p>
 */
const PROVISIONED = "Site the console smoke check walks"

const isOurs = (site) => site?.description === PROVISIONED

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
      if (route.bento) {
        // SH897 — bento facts, read off the rendered page. `missing` is what already decides
        // `ok`, so a finding here fails the probe exactly like a missing string does.
        const surface = await checkBentoSurface(page, route.noPanel === true)
        probe.missing.push(...surface.findings)
        const morph = await checkSaveBarMorph(page)
        probe.morph = morph.ran ? (morph.findings.length ? morph.findings.join("; ") : "ok") : morph.why
        if (morph.ran) probe.missing.push(...morph.findings)
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
    // SH897 — printed on every run, ok or not. The morph has no subject until a Block AR editor
    // moves, and a fact nobody prints is a fact nobody notices has stopped being true.
    if (probe.morph) console.log(`       morph: ${probe.morph}`)
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
  // Applied here rather than at each `probe.skipped =` inside the function, so a skip path
  // added later cannot be the one that forgot (SH238/SH659). The reason survives into the
  // report either way — `skipped` still says what happened, `ok` says whether it is allowed.
  if (STRICT && driveProbe.skipped) {
    driveProbe.ok = false
    driveProbe.errors.push(
      `--strict: the drive must run, and it skipped — ${driveProbe.skipped}`)
  }
  // The publish half is skippable for a good reason on a laptop and for none in CI, where the
  // fixture is provisioned one step earlier and the ordering above makes it the target (SH659).
  if (STRICT && !driveProbe.skipped && driveProbe.publishSkipped) {
    driveProbe.ok = false
    driveProbe.errors.push(
      `--strict: the drive reached the editor and did not publish — ${driveProbe.publishSkipped}`)
  }
  report.probes.push(driveProbe)
  console.log(`${driveProbe.ok ? "ok  " : "FAIL"} ${driveProbe.name.padEnd(30)} `
    + (driveProbe.skipped
      ? `skipped: ${driveProbe.skipped}`
      : `${driveProbe.site} — ${driveProbe.props} editable prop(s), `
        + (driveProbe.published ? "published" : `publish skipped: ${driveProbe.publishSkipped}`)))
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
