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
import { mkdir, readFile, writeFile } from "node:fs/promises"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { compare, familyOf, foldFamilies, headroom, seedCeilings, staleEntries }
  from "../scripts/route-cost.mjs"
import { a11yFindings, staleDebt } from "../scripts/a11y-debt.mjs"
import axeCore from "axe-core"

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
 * SH1113 — the per-route cost budget, and the flag that seeds it.
 *
 * `--record-cost` rewrites `console-route-budget.json` from this run instead of comparing against
 * it, keeping every entry's `why`. It is a separate flag rather than a fallback for a missing file
 * because a gate that seeds itself when it finds nothing to compare against is a gate that passes
 * on the day somebody deletes its budget.
 */
const BUDGET = join(dirname(fileURLToPath(import.meta.url)), "..", "console-route-budget.json")
const RECORD_COST = has("record-cost")

/**
 * How long a route is given to put its first row on screen before the number is recorded as
 * unobserved. A route that never renders is a probe failure already, reported there; recording the
 * timeout for it would put that number into a ratio and read as a page that is merely slow.
 *
 * <p>The same 10s the shell wait below already allows, and deliberately not longer: this wait
 * happens *before* it, so every second here is spent again on every route that renders nothing —
 * sixteen of them on a thin instance, which is what took a recording run from ten minutes to
 * twenty-five.</p>
 */
const FIRST_ROW_TIMEOUT = 10_000

/**
 * What the page asks the SERVER for — the data reads, never the bundle.
 *
 * <h2>The first draft counted everything, and the numbers said why that was wrong</h2>
 *
 * <p>Counting every same-origin request made the eager bundle ~1.73 MB of every route's total, so
 * the post editor's own reads showed up as 1747000 against a site list's 1730000: a 20 KB signal
 * under a 1.7 MB constant. That is exactly the failure this task was filed about — a page's cost
 * hidden inside a bigger number — reproduced one level up, and it would also have failed this gate
 * every time the bundler regrouped a chunk, which is a regression `console-size-budget.json`
 * already owns. Two gates answering for one number is the state P10 names.</p>
 *
 * <p>So the filter is the data plane: {@code /api/v2/**} and {@code /graphql}. What is left is
 * small, stable and exactly the sentence the design wrote — "a route fetching three collections to
 * draw two figures". The static assets are not unmeasured; they are measured by the gate whose
 * subject they are.</p>
 *
 * <p>`bytes` is summed from Playwright's own accounting rather than from `content-length`, which a
 * chunked response does not carry. `ms` is wall-clock to the first row, and is only ever used as a
 * <em>ratio</em> against the baseline route, for the reason `route-cost.mjs` gives.</p>
 */
/**
 * SH1171 — the change stream is a held connection, not a read. Every screen opens SH968's shared
 * stream and SH969's bounded replay, and whether either is counted depended on when it opened
 * relative to the measurement: the seed saw 7 reads on the 404 page and a later run 9, from the
 * same code. Its cost has its own budget (`events.idle`, SH967), so it is left out here.
 */
const HELD_STREAM = /\/api\/v2\/events\b/u

/**
 * SH1172 — a read is decided by its **path**, not by a substring of its URL. `DATA_READ` matched any
 * URL with `/graphql` followed by a word boundary, which is also the explorer's own document
 * (`/bento/graphql`) and four of its bundle chunks (`/assets/graphql.page-….js`, `graphql-….css`,
 * `graphql.worker-….js`): the explorer's "14 reads" were 9 reads and 5 static files, and its
 * megabyte was the editor's code rather than the schema. The bundle is SH1043's subject, not this.
 */
function isDataRead(url) {
  let path
  try {
    path = new URL(url).pathname
  } catch {
    return false
  }
  return (path.startsWith("/api/v2/") || path === "/graphql") && !HELD_STREAM.test(path)
}

function watchCost(page) {
  const cost = { requests: 0, bytes: 0, ms: null }
  const sized = []
  const mine = (url) => (url.startsWith(BASE) || url.startsWith(API)) && isDataRead(url)
  page.on("request", (r) => { if (mine(r.url())) cost.requests++ })
  page.on("requestfinished", (r) => {
    if (!mine(r.url())) return
    sized.push(r.sizes()
      .then((s) => { cost.bytes += (s.responseBodySize ?? 0) + (s.responseHeadersSize ?? 0) })
      .catch(() => {}))
  })
  return {
    cost,
    /** Resolves once every response this page received has been accounted for. */
    async settle() { await Promise.all(sized) },
  }
}

/**
 * A first row that arrived this close to the deadline is recorded as not having arrived.
 *
 * <p>Bought by a recording run: two of forty families seeded ceilings of **27.1x** and **25.2x**
 * the baseline while every neighbour sat at 2.0-2.5x. Both had rendered at ~15s against a 15s
 * deadline — which is not a slow page, it is a page that *barely* rendered, and the difference
 * between it and a timeout is which side of the deadline the last poll fell on. Writing that down
 * as a ratio dresses an unreliable observation as a number, and a 27x ceiling governs nothing.</p>
 */
const FIRST_ROW_TRUST = 0.8

/**
 * Wall-clock from navigation to the first row, or `null` when none arrived in time to be believed.
 *
 * <p>"First row" is defined as the first moment the page's main region holds any text at all,
 * which is deliberately app-agnostic: a per-route selector would be a second description of every
 * screen, drifting from the first. The shell painting empty does not satisfy it, which is the
 * distinction that matters — a spinner inside `main` is not a row.</p>
 */
async function timeToFirstRow(page, startedAt) {
  try {
    await page.waitForFunction(() => {
      const main = document.querySelector("main, [role=main]")
      return main != null && (main.innerText || "").trim().length > 0
    }, null, { timeout: FIRST_ROW_TIMEOUT })
    const elapsed = Date.now() - startedAt
    return elapsed >= FIRST_ROW_TIMEOUT * FIRST_ROW_TRUST ? null : elapsed
  } catch {
    return null
  }
}

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
   * SH901 — the first surface that carries a save-bar morph, which is what `checkSaveBarMorph`
   * had been reporting "no surface carries one yet" about since SH897. The post editor is the
   * heaviest form in the product, so this probe also proves `PageHero`'s morph end to end.
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
   * SH902 — the post-type pages. Three surfaces, one probe each, because the three fail in
   * different ways and only one of them is visible in a source scan.
   *
   * The **list** and **new** pages are the ones that caught SH902's real defect: both were written
   * with a header-less `AdaptiveSectionCard`, which renders a plain card rather than the frosted
   * section, and a page that looks finished. `checkBentoSurface` reads
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
   * Probed at both addresses — the old `/console/…` path, which redirects, and the shell's — so a
   * redirect that stops landing on the page is caught, not just the page itself.
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

  /*
   * SH907 — the Universal Editor, and the one bento route deliberately NOT probed as a bento
   * surface.
   *
   * `visual-editor.page.tsx` is `h-screen`: it owns the viewport and draws its own toolbar, so it
   * is mounted outside the shell and renders no rail, no hero and nothing frosted. Marking it
   * `bento: true` would fail on all three and the honest fix would be to weaken the bento rule for
   * everyone — which is how a check stops catching the thing it exists for. It is probed as a page
   * that must render: an editor that 404s or throws is what this walk is for.
   */
  routes.push({
    name: "bento-universal-editor",
    path: `/bento/universal-editor/${site.id}`,
    mustSee: [],
  })
  routes.push({ name: "bento-editor-sites", path: "/bento/universal-editor", bento: true, mustSee: [] })

  /*
   * SH908 — the explorer and the 404, both probed at both addresses.
   *
   * The explorer is the only page in the console whose body is a third-party editor sized to its
   * container: `graphiql-height` below reads the frame's geometry, which is the half no source
   * scan can answer and the half SH907
   * measured for the Universal Editor.
   *
   * The not-found page is the one nobody navigates to on purpose, which is exactly why it is
   * probed: `mustSee: ["404"]` on a path under `/bento` that no route declares. Without the
   * catch-all this task added, that path falls through to the bare `*` and renders outside every
   * shell — and the bento probe's rail rule is what says so.
   */
  /*
   * SH909 — the old URLs, which must still land somewhere rather than 404.
   *
   * §LV.12 said to redirect rather than drop, and this is where that is checked in the one way it
   * can be: by opening the legacy URL in a browser and reading which path the app ended on. The
   * five below are the shapes that differ — the bare prefix and `/home` are the console's *index*
   * and become the bento shell's, a deep path is a straight prefix swap, a path with an id has to
   * carry it, and the tenant-prefixed form is the one a multi-tenant install's bookmarks hold.
   *
   * `bento: true` on the deep ones as well: landing on the right *path* while rendering the wrong
   * shell would be a redirect that technically worked, which is exactly what this task is about.
   */
  routes.push({ name: "legacy-console-root", path: "/console", redirectsTo: "/bento", bento: true, mustSee: [] })
  routes.push({ name: "legacy-console-home", path: "/console/home", redirectsTo: "/bento", bento: true, mustSee: [] })
  routes.push({
    name: "legacy-console-content",
    path: "/console/content",
    redirectsTo: "/bento/content",
    bento: true,
    mustSee: [site.name],
  })
  routes.push({
    name: "legacy-console-review",
    path: "/console/admin/review",
    redirectsTo: "/bento/admin/review",
    bento: true,
    mustSee: [],
  })
  routes.push({
    name: "legacy-console-tenant-prefixed",
    path: "/acme/console/content",
    redirectsTo: "/bento/content",
    bento: true,
    mustSee: [site.name],
  })
  if (anyPost?.id) {
    routes.push({
      name: "legacy-console-post",
      path: `/console/content/post/${anyPost.id}`,
      redirectsTo: `/bento/content/post/${anyPost.id}`,
      mustSee: [],
    })
  }
  // And the admin hub, which under the console was a bare Outlet with an index redirect. It is the
  // `urlBase` of eleven admin surfaces, so a hero back-link is what lands on it.
  routes.push({ name: "bento-admin-hub", path: "/bento/admin", redirectsTo: "/bento/admin/users", bento: true, mustSee: [] })

  routes.push({ name: "graphql", path: "/console/graphql", graphiql: true, redirectsTo: "/bento/graphql", mustSee: [] })
  routes.push({ name: "bento-graphql", path: "/bento/graphql", bento: true, graphiql: true, mustSee: [] })
  routes.push({ name: "unknown-route-bento", path: "/bento/definitely-not-a-route", bento: true, mustSee: ["404"] })
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
 * A source scan already knows which components each page composes — that is
 * `scripts/surface-digest.mjs`, and it is cheap and runs everywhere. What it cannot see is
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

/**
 * SH908 — does the third-party editor fit the viewport it was given?
 *
 * GraphiQL sizes itself to its container, so the container names a height, and that height is the
 * viewport minus whatever sits above it — a number the page cannot read off the shell. SH907
 * measured the same question for the Universal Editor by hand and found its iframe 48px **below
 * the fold** inside the shell; this asks it on every run instead.
 *
 * The geometry is reported whether or not it is wrong. A number nobody prints is a number nobody
 * notices has changed, and this one changes whenever the shell's header does.
 */
async function checkGraphiQLFit(page) {
  return page.evaluate(() => {
    const editor = document.querySelector(".graphiql-container")
    if (!editor) return { present: false }
    const rect = editor.getBoundingClientRect()
    return {
      present: true,
      top: Math.round(rect.top),
      bottom: Math.round(rect.bottom),
      viewport: innerHeight,
      // Two separate failures: the editor running past the bottom of the window, and the page
      // growing a scrollbar around a surface that is supposed to fill it exactly.
      belowFold: Math.round(Math.max(0, rect.bottom - innerHeight)),
      docOverflow: Math.round(Math.max(0, document.documentElement.scrollHeight - innerHeight)),
    }
  })
}

/**
 * SH1053 — the two facts a keyboard reader depends on, which no source scan can see.
 *
 * <p>The first Tab on a console page must land on the skip link (VDS141), or a keyboard reader tabs
 * through the rail and the header on every page. And after an in-app navigation focus must move to
 * the new page's heading, or it stays on the rail link just pressed and the reader has no way to
 * know the page changed. Both are the shell's, measured here because a composition of the shell can
 * still break them: a page that grabs focus first, or a route that renders no heading.</p>
 */
async function checkKeyboardLandmarks(context) {
  const probe = { name: "keyboard-landmarks", path: "/bento", ok: true, errors: [], warnings: [],
    failedRequests: [] }
  const page = await context.newPage()
  try {
    await page.goto(`${BASE}/bento`, { waitUntil: "networkidle" })
    await page.waitForTimeout(1000)
    await page.keyboard.press("Tab")
    const first = await page.evaluate(() => {
      const a = document.activeElement
      return { tag: a?.tagName.toLowerCase(), href: a?.getAttribute("href") ?? "",
        target: a?.getAttribute("href")?.startsWith("#")
          ? Boolean(document.getElementById(a.getAttribute("href").slice(1))) : false }
    })
    probe.firstTab = `${first.tag} ${first.href}`
    if (first.tag !== "a" || !first.href.startsWith("#") || !first.target) {
      probe.ok = false
      probe.errors.push(`SH1053: the first Tab reached ${probe.firstTab}, not a skip link to an element`
        + " on the page")
    }
    const rail = page.locator("nav a[href$='/bento/content']").first()
    if (await rail.count() === 0) {
      probe.afterNav = "not driven"
      probe.warnings.push("no rail link to /bento/content, so focus after navigation was not driven")
    } else {
      await rail.click()
      await page.waitForTimeout(1500)
      probe.afterNav = await page.evaluate(() => document.activeElement?.tagName.toLowerCase() ?? "none")
      if (probe.afterNav !== "h1") {
        probe.ok = false
        probe.errors.push(`SH1053: after navigating, focus is on <${probe.afterNav}>, not the new page's`
          + " heading — a keyboard reader is left on the link they pressed")
      }
    }
  } catch (e) {
    probe.ok = false
    probe.errors.push(`could not drive the keyboard: ${String(e).split("\n")[0]}`)
  } finally {
    await page.close()
  }
  return probe
}

/** SH1054 — axe's own bundle (the package exports its source), evaluated into each page. */
const AXE_SOURCE = axeCore.source
/** The committed map of accessibility debt below serious, by route family. */
const A11Y_DEBT = JSON.parse(await readFile(join(dirname(fileURLToPath(import.meta.url)), "..",
  "a11y-debt.json"), "utf8"))
/** Every rule id axe found per family in this run, for the entries that have been paid. */
const a11yObserved = new Map()

/**
 * SH1054 — one axe pass over the rendered page, and whether the document names its language.
 *
 * <p>Evaluated through Playwright rather than injected as a script tag, because a script tag is
 * subject to the page's CSP and the jar serves one; the evaluation is the browser's debugger
 * protocol and is not. Violations only: a pass list is thousands of nodes nobody reads.</p>
 *
 * <p>The language check compares `<html lang>` with what i18next stored for the reader. They
 * disagreed on every route before SH1054: `index.html` says `en`, the console rendered Portuguese,
 * and a screen reader pronounced it with English rules.</p>
 */
async function runAxe(page) {
  try {
    await page.evaluate(AXE_SOURCE)
    return await page.evaluate(async (vendor) => {
      // SH1054 — third-party subtrees the console embeds and does not author, each named with its
      // reason in a11y-debt.json's `vendor` and printed on every run rather than silently skipped.
      const context = vendor.length ? { include: [document], exclude: vendor.map((v) => [v]) } : document
      const result = await window.axe.run(context, { resultTypes: ["violations"] })
      const lang = document.documentElement.lang || ""
      let stored = ""
      try { stored = localStorage.getItem("i18nextLng") || "" } catch { stored = "" }
      const primary = (tag) => tag.toLowerCase().split("-")[0]
      return {
        violations: result.violations.map((v) => ({
          id: v.id, impact: v.impact, nodes: v.nodes.length, target: v.nodes[0]?.target?.join(" "),
        })),
        lang,
        langMismatch: stored && primary(stored) !== primary(lang)
          ? `<html lang="${lang}"> but the console renders "${stored}"` : null,
      }
    }, Object.keys(A11Y_DEBT.vendor ?? {}))
  } catch (e) {
    return { violations: [], lang: "", langMismatch: null, error: String(e).split("\n")[0] }
  }
}

/**
 * SH1054 — the same axe gate at the two other widths a curator uses.
 *
 * <p>Every route is axed at the check's own 1600px; this re-runs it on the routes whose layout
 * changes most with width — the rail collapses, a mosaic reflows, a toolbar wraps — at a phone and a
 * tablet. A violation composed by a layout (a control pushed under another, a name lost when a label
 * is hidden below `md`) exists only at the width that draws it.</p>
 */
const A11Y_VIEWPORTS = [
  { name: "phone", width: 390, height: 844 },
  { name: "tablet", width: 820, height: 1180 },
]
const A11Y_VIEWPORT_ROUTES = ["/bento", "/bento/content", "/bento/admin", "/bento/content/files"]

async function checkViewports(browser) {
  const probe = { name: "a11y-viewports", path: A11Y_VIEWPORT_ROUTES.join(" "), ok: true, errors: [],
    warnings: [], failedRequests: [], runs: 0 }
  for (const viewport of A11Y_VIEWPORTS) {
    const context = await browser.newContext({
      httpCredentials: { username: USER, password: PASS },
      viewport: { width: viewport.width, height: viewport.height },
      extraHTTPHeaders: { Authorization: AUTH },
    })
    const page = await context.newPage()
    try {
      for (const path of A11Y_VIEWPORT_ROUTES) {
        await page.goto(`${BASE}${path}`, { waitUntil: "networkidle" })
        await page.waitForTimeout(800)
        const result = await runAxe(page)
        probe.runs++
        const route = `${viewport.name}:${familyOf(path.replace(/^\//, "").replaceAll("/", "-"))}`
        const { findings } = a11yFindings(route, route, result.violations, A11Y_DEBT)
        if (findings.length) {
          probe.ok = false
          probe.errors.push(...findings)
        }
      }
    } finally {
      await context.close()
    }
  }
  return probe
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
/**
 * SH1046 — what a second folder costs, counted in requests rather than argued about.
 *
 * <p>The defect this closes was measurable and nothing measured it: the content browser read the
 * whole post-type catalogue inside an effect keyed on the folder id, so walking a tree cost one
 * full catalogue per folder for a list that decides a default and fills a filter. Sixteen other
 * call sites did the same on mount.</p>
 *
 * <p>`test/query-cache.test.mjs` drives the client's defaults in-process, which is the claim about
 * the cache. This is the claim about the <b>console</b>, and only a browser can make it: navigate
 * from a site's root folder into a child and count the requests the second navigation issues. A
 * catalogue read that appears twice is the regression, by name.</p>
 *
 * <p>No child folder is a <b>skip</b> and not a failure: it is a fixture fact about the instance,
 * the same reading `checkEditorDrive` takes, and `--strict` turns it red where the fixture is
 * provisioned.</p>
 */
async function checkFolderNavigation(context, site) {
  const probe = { name: "folder-navigation", ok: true, errors: [], repeated: [], requests: 0 }

  let children
  try {
    children = await api(`/object/${site.id}/list?page=0`)
  } catch (problem) {
    probe.skipped = `the site's own listing did not answer: ${problem}`
    return probe
  }
  const child = (children.folders ?? [])[0]
  if (!child) {
    probe.skipped = "this site has no child folder, so there is no second navigation to count — "
      + "a fixture gap rather than a defect"
    return probe
  }

  const page = await context.newPage()
  page.on("pageerror", (e) => probe.errors.push(String(e)))
  // Same-origin API reads only. The console's own chunks are SH1043's subject and not this one's.
  const seen = []
  page.on("request", (r) => {
    const url = r.url()
    if (url.startsWith(API) && url.includes("/api/v2/")) seen.push(new URL(url).pathname)
  })

  const settle = async () => {
    await page.waitForSelector("main, [role=main], nav", { timeout: 10_000 }).catch(() => {})
    await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {})
    await page.waitForTimeout(400)
  }

  try {
    await page.goto(`${BASE}/console/content/object/${site.id}`,
      { waitUntil: "domcontentloaded", timeout: 20_000 })
    await settle()

    // Everything from here is the *second* navigation, which is the one that has to be cheap.
    seen.length = 0
    await page.goto(`${BASE}/console/content/object/${child.id}`,
      { waitUntil: "domcontentloaded", timeout: 20_000 })
    await settle()
    probe.requests = seen.length

    /*
     * A full-page load rather than a click, so this measures the cache and not the router: the
     * client is rebuilt, and a catalogue still has to be read once. What must NOT appear is a
     * catalogue being read again *within* the second navigation — the shape of the effect that was
     * keyed on the folder id, which fired once per folder in a single session.
     */
    const CATALOGUES = ["/api/v2/post-type", "/api/v2/site", "/api/v2/widget"]
    for (const path of CATALOGUES) {
      const hits = seen.filter((one) => one === path).length
      if (hits > 1) probe.repeated.push(`${path} × ${hits}`)
    }
    if (probe.repeated.length) {
      probe.ok = false
      probe.errors.push("SH1046: a catalogue was read more than once inside one navigation — "
        + `${probe.repeated.join(", ")}. Every screen reads these through the cached hooks in `
        + "`hooks/use-catalogue.ts`, so a second read means a page went back to calling the "
        + "service directly, or to an effect keyed on something that changes per folder.")
    }
  } catch (problem) {
    probe.ok = false
    probe.errors.push(String(problem))
  } finally {
    await page.close()
  }
  return probe
}

/**
 * SH1047 — the race itself, reproduced rather than reasoned about.
 *
 * <p>The content browser rebuilds its page fetch on every change of folder, sort or filter, and
 * before this task the request it replaced kept running. A curator clicking quickly down a tree
 * could be shown the <b>earlier</b> folder's rows under the <b>later</b> folder's breadcrumb, with
 * nothing to notice by — no error, no spinner, just the wrong list.</p>
 *
 * <p>Source can say an `AbortController` is there; only a browser can say the superseded answer
 * does not land. So this holds the first folder's listing response back, navigates past it, releases
 * it, and reads what is on screen. Without cancellation the delayed body arrives second and wins.</p>
 *
 * <p>A instance with no child folder is a <b>skip</b>, as above: a fixture fact, red under
 * `--strict` where the fixture is provisioned.</p>
 */
async function checkSupersededNavigation(context, site) {
  const probe = { name: "superseded-navigation", ok: true, errors: [], held: 0 }

  let children
  try {
    children = await api(`/object/${site.id}/list?page=0`)
  } catch (problem) {
    probe.skipped = `the site's own listing did not answer: ${problem}`
    return probe
  }
  const child = (children.folders ?? [])[0]
  if (!child) {
    probe.skipped = "this site has no child folder, so there is no navigation to supersede"
    return probe
  }
  probe.from = site.id
  probe.to = child.id

  const page = await context.newPage()
  page.on("pageerror", (e) => probe.errors.push(String(e)))
  // The first folder's listing is held until after the second navigation has been asked for, which
  // is the whole experiment: a slow earlier response racing a fast later one.
  let release = null
  const held = new Promise((resolve) => { release = resolve })
  // A URL predicate rather than a Playwright glob, and deliberately. The shared comment stripper
  // the shape test reads this file through is not a parser: a slash followed by two stars, inside
  // a string or anywhere else, opens a block comment to it and it swallows everything to the next
  // close — so a wildcard pattern written the obvious way silently ends the file, and every
  // assertion after it matches an empty string. Found twice while writing SH1049: once in the
  // pattern, and once in the comment that was explaining the pattern.
  const listing = `/api/v2/object/${site.id}/list`
  await page.route((url) => url.pathname === listing, async (route) => {
    probe.held += 1
    await held
    await route.continue()
  })

  try {
    await page.goto(`${BASE}/console/content/object/${site.id}`,
      { waitUntil: "domcontentloaded", timeout: 20_000 })
    // Long enough for the listing to have been asked for and short enough that nothing settles.
    await page.waitForTimeout(1_000)
    await page.goto(`${BASE}/console/content/object/${child.id}`,
      { waitUntil: "domcontentloaded", timeout: 20_000 })
    release()
    await page.waitForSelector("main, [role=main], nav", { timeout: 10_000 }).catch(() => {})
    await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {})
    await page.waitForTimeout(800)

    if (!probe.held) {
      probe.skipped = "the held route never matched, so nothing was raced — the listing endpoint "
        + "moved and this probe is measuring nothing"
      return probe
    }
    // What is on screen has to be the second folder's. Read from the URL the app settled on and
    // the rows it drew: a stale render keeps the first folder's listing under the second's address.
    const landed = new URL(page.url()).pathname
    if (!landed.endsWith(child.id)) {
      probe.ok = false
      probe.errors.push(`the browser left ${landed} instead of the child folder`)
      return probe
    }
    const rows = await page.evaluate(() => document.body.innerText)
    const strays = (children.folders ?? []).concat(children.posts ?? [])
      .map((one) => one.title || one.name)
      .filter((name) => name && name !== child.name && rows.includes(name))
    if (strays.length) {
      probe.ok = false
      probe.errors.push("SH1047: the superseded folder's rows are on screen under the folder the "
        + `curator navigated to — ${strays.slice(0, 4).join(", ")}. The earlier listing was not `
        + "cancelled, so its delayed answer landed second and won.")
    }
  } catch (problem) {
    probe.ok = false
    probe.errors.push(String(problem))
  } finally {
    release?.()
    await page.close()
  }
  return probe
}

/**
 * SH1049 — what a curator sees when the read fails, which only a browser can answer.
 *
 * <p>`test/loading-state.test.mjs` refuses the <em>absence</em> of a loading state, which is what a
 * source scan can do. It cannot tell a good failure from a bad one, and the failure this task is
 * about was invisible to every scan: the post editor settled every promise, so a 500 left its value
 * null and the render gate spun for ever — no error, no retry, nothing to press.</p>
 *
 * <p>So this fails the read on purpose. Every {@code /api/v2/} request the page makes is answered
 * {@code 500}, and the page must then show something a person can act on: the word for a failure,
 * or a way to try again. A page that only shows a spinner is the defect, by name.</p>
 *
 * <p>The spinner is what it looks for rather than a specific string, because the console is
 * translated and the strings are not this probe's business. What it refuses is a screen that is
 * still <em>loading</em> long after every request it made has been answered.</p>
 */
const FAILED_BODY = JSON.stringify({ title: "planted by the failed-read probe" })

async function checkFailedReads(context, routes) {
  const probe = { name: "failed-reads", ok: true, errors: [], checked: [], silent: [] }

  // Three of them: a list, an editor and the dashboard, which is the spread of shapes rather than
  // every route. A 500 per route is a browser launch per route, and this is a smoke check.
  const wanted = ["sites", "site-children", "post-type-list"]
  const chosen = routes.filter((route) => wanted.includes(route.name))
  if (!chosen.length) {
    probe.skipped = "none of the sampled routes are in the walk any more, so this probe would "
      + "report success over nothing — update `wanted` with the routes that replaced them"
    return probe
  }

  for (const route of chosen) {
    const page = await context.newPage()
    // Only the reads. A 500 on the console's own chunks would be a broken page rather than a
    // broken instance, and the two must not report the same.
    await page.route((url) => url.pathname.startsWith("/api/v2/"),
      (target) => (target.request().method() === "GET"
        ? target.fulfill({ status: 500, contentType: "application/json", body: FAILED_BODY })
        : target.continue()))
    try {
      await page.goto(BASE + route.path, { waitUntil: "domcontentloaded", timeout: 20_000 })
      await page.waitForSelector("main, [role=main], nav", { timeout: 10_000 }).catch(() => {})
      await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {})
      await page.waitForTimeout(1_200)

      const body = await page.evaluate(() => document.body.innerText)
      const spinning = await page.locator(".animate-spin").count()
      const says = /erro|error|falh|fail|tente|try again|indispon|unavailable/i.test(body)
      probe.checked.push(route.name)
      if (!says) {
        probe.ok = false
        probe.silent.push(route.name)
        probe.errors.push(`SH1049: ${route.name} answered every read with 500 and said nothing a `
          + `curator could act on${spinning ? ` (${spinning} spinner(s) still turning)` : ""} — a `
          + "broken instance and a slow one are the same screen there.")
      }
    } catch (problem) {
      probe.ok = false
      probe.errors.push(`${route.name}: ${problem}`)
    } finally {
      await page.close()
    }
  }
  return probe
}

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

    // 3b. A click inside the preview, which is the step SH1036 exists for and the one no
    //     instrument reached. The unit suites prove the two rewriters compute the right
    //     destination; only a browser can prove edit mode SURVIVES arriving there — the bridge is
    //     injected by the server for `?editor=true`, so the question is whether the link carried it.
    //
    //     A link to another page of this site is only present on a site that has one, so a fixture
    //     with a single page warns rather than fails: the claim is about what happens on a click,
    //     not about the fixture's shape.
    const internal = frame.locator("a[href^='/preview/']").first()
    if (await internal.count() === 0) {
      probe.warnings.push("the framed page links to no other page of its own preview, so the "
        + "SH1036 click-through was not driven — the fixture needs a second page to click to")
    } else {
      const before = frame.url()
      await internal.click({ timeout: 10_000 })
      // The frame object survives a navigation in Playwright, so waiting on its url changing is
      // what says the click landed rather than was swallowed.
      const moved = await page.waitForFunction(
        (was) => Array.from(document.querySelectorAll("iframe"))
          .some((f) => f.contentWindow && f.src !== was),
        before, { timeout: 15_000 }).then(() => true).catch(() => false)
      const after = page.frames().find((f) => f.url().includes("/preview/")) ?? null
      const stillEditor = after ? after.url().includes("editor=true") : false
      if (!stillEditor) {
        probe.ok = false
        probe.errors.push("SH1036: a click inside the preview left for a URL with no `editor=true`, "
          + `so the next page ships no bridge (${after ? after.url() : "no frame"}). Edit mode ends `
          + "on the curator's first click and the console goes on reporting 'connected'")
      } else {
        // And the bridge actually attached there — the parameter surviving is necessary and the
        // script running is the thing the curator needs. `data-shio-editing` is set by `attach()`
        // immediately before `sh:ready`, so it stands for the handshake the console keys on.
        const editingAfter = await after.locator("[data-shio-editing='true']")
          .waitFor({ state: "attached", timeout: 15_000 }).then(() => true).catch(() => false)
        if (!editingAfter) {
          probe.ok = false
          probe.errors.push("SH1036: the click carried `editor=true` and the bridge did not attach "
            + "on the page it landed on, so edit mode is off behind a green badge")
        } else {
          probe.clickThrough = true
        }
      }
      if (!moved) {
        probe.warnings.push("the iframe's src did not change on the click, so the navigation was "
          + "in-frame only — which is the normal case and is why the url is what is asserted")
      }
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
      /*
       * SH1174 — what the write answered, heard at the network. A refusal goes to the dock through
       * `reportRefusal`, not to a toast, and a 4xx is not an incident, so without this the probe read
       * a refusal as silence and said "publish skipped: undefined" over a publish it had attempted.
       */
      const writes = []
      const hear = (r) => {
        const method = r.request().method()
        if (!/\/api\/v2\/post-unified\//.test(r.url()) || !["PATCH", "POST"].includes(method)) return
        writes.push(r.text().catch(() => "").then((body) => {
          let type
          try { type = JSON.parse(body)?.type } catch { type = undefined }
          return { status: r.status(), method, type: type ? String(type).replace(/^.*\/errors\//, "") : undefined }
        }))
      }
      page.on("response", hear)
      await publish.click({ timeout: 10_000 })
      // The toast, not the button's own state: `persist` clears the pending map in the same tick
      // it succeeds *and* in the tick it fails, so an empty change count says nothing about which.
      // SH1174 — `.first()`: the toast and the assistant dock (SH965) both say it, and a locator
      // matching two elements makes `waitFor` throw a strict-mode error — which the catch below read
      // as "Publish reported nothing" over a publish that had succeeded and said so twice.
      const published = await page.getByText(/^(Publicado|Published)$/).first().waitFor(
        { state: "visible", timeout: 15_000 }).then(() => true).catch(() => false)
      page.off("response", hear)
      probe.publishWrites = await Promise.all(writes)
      const refused = probe.publishWrites.find((w) => w.status >= 400)
      if (!published) {
        // What the curator was looking at when nothing said "published" — the one view of this
        // failure the network cannot give.
        probe.shot = join(OUT, "universal-editor-drive-publish.png")
        await page.screenshot({ path: probe.shot }).catch(() => {})
        probe.ok = false
        probe.publishRefused = refused
          ? `${refused.status} ${refused.type ?? "(no problem type)"}`
          : probe.publishWrites.length ? "the write succeeded and no toast followed" : "no write was sent"
        probe.errors.push(`SH659: the editor's Publish reported nothing — ${probe.publishRefused}. `
          + "`persist(true)` is the one path `universal-editor-save-back` does not cover, since it "
          + "patches with `publish:true` rather than saving a draft")
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

/**
 * SH927 — <b>which server answered, and is it even this product?</b>
 *
 * `--base` defaults to `http://localhost:5173`, which is not a Shio address. It is *the* Vite
 * default, so on any machine holding more than one Viglet front end it is whichever dev server was
 * started last. Measured here: `:5173` was answering `<title>Turing ES Administration Console</title>`
 * and serving `turing-app/src/App.tsx`, and this check would have walked it — logging in, screenshotting
 * and reporting on a different product's console without one line saying so.
 *
 * That is worse than the redness SH927 was filed about. A check that is red for the environment gets
 * skimmed; a check that answers confidently about the wrong application gets *believed*. The title in
 * `index.html` is the cheapest thing that tells them apart, it is written by this repository, and
 * `console-check-shape.test.mjs` holds the two together so it cannot drift silently.
 *
 * The dev/jar half is the other question the same probe answers: Vite serves its client shim at
 * `/@vite/client` as JavaScript, and the jar has no such route. Printed on every run, because the
 * two servers do not give the same answers — the Monaco workers fall back to the main thread under
 * the dev server and start normally from the jar, which is the rest of SH927.
 */
const SHIO_TITLE = "Viglet Shio CMS"

async function identifyServer() {
  let dev = false
  try {
    const shim = await fetch(`${BASE}/@vite/client`)
    dev = shim.ok && (shim.headers.get("content-type") ?? "").includes("javascript")
  } catch {
    // Not reachable — treat as "not the dev server" and let the title decide the rest.
  }

  let title = ""
  try {
    title = (/<title>([^<]*)<\/title>/i.exec(await (await fetch(BASE)).text())?.[1] ?? "").trim()
  } catch {
    // Left blank, which fails the identity check below rather than passing it.
  }

  return { dev, title, isShio: title === SHIO_TITLE, kind: dev ? "vite dev server" : "jar" }
}

/**
 * SH927 — the one console difference the Vite dev server is allowed to have.
 *
 * <h2>What is actually wrong</h2>
 *
 * Monaco's workers are *created* under the dev server and then die evaluating their own module
 * graph: `monaco-graphql` reaches CommonJS packages that Vite serves raw to a worker, rather than
 * through the optimizer that fixes that interop for the app graph. A module worker that fails to
 * evaluate raises an error event carrying **no message**, which is why this arrives as the bare
 * string "Event" beside Monaco's own warning. `new Worker` reports nothing useful about it;
 * importing the same URL from the page prints the cause.
 *
 * The page is correct. Monaco falls back to the main thread and the editor works, slowly. The cost
 * was never the fallback — it was that `console:check` came back red on two probes for anybody
 * running it the documented way, against `:5173`. **A gate that is red for the environment is a
 * gate people learn to skim**, which is the state SH579 exists to prevent one level up.
 *
 * <h2>Five fixes, all refuted by measurement</h2>
 *
 * Written down so the next reader does not spend the afternoon re-running them:
 *
 * 1. **Excluding the three `?worker` entries from `optimizeDeps`** — the imports then resolve to
 *    real `/@fs/` paths instead of dep chunks, the worker wrapper and every import it makes serve
 *    200, and the warning is unchanged.
 * 2. **The worker mechanism itself** — constructing that exact module worker by hand in the page
 *    succeeds with no error, so nothing is wrong with module workers here.
 * 3. **Naming the CJS package in `optimizeDeps.include`** — no chunk is emitted, because
 *    `graphiql` has already swallowed it and Vite dedupes.
 * 4. **`optimizeDeps.needsInterop`** — the import is rewritten no differently.
 * 5. **Aliasing the package to a local ESM shim** — this one *works*, and immediately reveals the
 *    next CommonJS package in the same graph. It is a chain, not a package, so the list has no end.
 *
 * The sixth option is to stop bundling the explorer and frame the one Spring serves, which is what
 * Turing does. Refuted for a different reason: that page loads twenty resources from `esm.sh` with
 * no local fallback, so the explorer would only work on an instance whose browser can reach a CDN.
 *
 * <h2>Why this is an exemption and not a shrug</h2>
 *
 * Two things keep it from becoming a blanket excuse:
 *
 * - It applies **only to the dev server**, and only on a route that actually rendered the explorer.
 *   The jar serves a built bundle where this cannot happen, so the same output there is still a
 *   failure — which is the point, because the jar is what ships.
 * - It **expires by failing**. If the dev server stops producing it, the exemption is spent and the
 *   run says so with the instruction, the way SH919's own expiry did.
 */
const MONACO_WORKER_WARNING = /Could not create web worker\(s\)/
/** What a module worker raises when its graph fails to evaluate: an event carrying nothing. */
const MESSAGELESS_ERROR = /^(Event|undefined)$/

function exemptDevWorkerNoise(probe, dev) {
  if (!dev || !probe.graphiql?.present) return false
  if (!probe.warnings.some((w) => MONACO_WORKER_WARNING.test(w))) return false
  if (!probe.errors.some((e) => MESSAGELESS_ERROR.test(e.trim()))) return false

  // Spliced in place rather than reassigned: `probe.ok` is computed from the same array object
  // this was handed, so replacing the property would leave the verdict reading the unfiltered one
  // — which is exactly what the first version of this did, and the probes stayed red with an
  // empty `errors` in the report saying they should not have.
  for (let i = probe.errors.length - 1; i >= 0; i -= 1) {
    if (MESSAGELESS_ERROR.test(probe.errors[i].trim())) probe.errors.splice(i, 1)
  }
  probe.devOnly = "SH927: Monaco's workers fell back to the main thread — a dev-server-only CJS"
    + " interop failure. The editor works, slowly. Not counted against this probe here; against"
    + " the jar the same output still is."
  return true
}

async function main() {
  /*
   * First, before the API is asked anything and before a browser is launched. It is two requests,
   * it needs nothing running but the origin under test, and everything after it is meaningless if
   * this is the wrong front end — including the discovery call, which would provision a fixture
   * site for a console that is never going to be opened.
   */
  const server = await identifyServer()
  console.log(`server: ${server.kind} at ${BASE} — ${JSON.stringify(server.title || "(no title)")}`)
  if (!server.isShio) {
    console.error(
      `\nSH927: ${BASE} is not answering as Shio.\n`
      + `  Expected <title>${SHIO_TITLE}</title>, got ${JSON.stringify(server.title || "(nothing)")}.\n`
      + "  :5173 is the Vite default, so it is whichever front end was started last — this check has\n"
      + "  walked another product's console before, and reported on it as though it were this one.\n"
      + "  Start this console (pnpm -C shio-react dev), or point the check at the right origin:\n"
      + "    pnpm -C shio-react console:check --base http://localhost:2710\n"
      + "    SHIO_CONSOLE_URL=http://localhost:2710 pnpm -C shio-react console:check\n")
    /*
     * `exitCode` and return, not `process.exit`. Killing the process here tears down the sockets
     * the two identity requests still hold, and libuv aborts on Windows — measured:
     * `Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)` and an exit status of **127**. A
     * gate whose refusal exits 127 instead of 1 is a gate a CI script reads as "crashed", which is
     * a different thing from "said no".
     */
    process.exitCode = 1
    return
  }

  const { site, routes } = await discoverRoutes(flag("site", process.env.SHIO_SITE))
  await mkdir(OUT, { recursive: true })

  const browser = await chromium.launch({ headless: !has("headed") })
  const context = await browser.newContext({
    httpCredentials: { username: USER, password: PASS },
    viewport: { width: 1600, height: 1000 },
    extraHTTPHeaders: { Authorization: AUTH },
  })

  const report = { base: BASE, api: API, site: site.name, server: server.kind, probes: [] }
  let devWorkerExemptionFired = false
  let devWorkerExemptionUnused = false

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
    /*
     * SH1170 — a request that never connected has no response, so the listener above cannot see it,
     * and the console message it raises carries no URL: probes failed on ERR_CONNECTION_TIMED_OUT
     * with nothing to say which resource. `requestfailed` names it. ERR_ABORTED is skipped because
     * the console aborts superseded and unmounted reads on purpose (SH1047, SH1116).
     */
    page.on("requestfailed", (r) => {
      const url = r.url()
      const why = r.failure()?.errorText ?? "failed"
      if (why.includes("ERR_ABORTED")) return
      if (url.startsWith(BASE) || url.startsWith(API)) {
        failedRequests.push(`failed ${r.method()} ${url.replace(BASE, "").replace(API, "")} (${why})`)
      }
    })

    const probe = { ...route, url: BASE + route.path }
    // SH1113 — armed before `goto`, or the document request itself is not counted.
    const watch = watchCost(page)
    try {
      const startedAt = Date.now()
      await page.goto(probe.url, { waitUntil: "domcontentloaded", timeout: 20_000 })
      watch.cost.ms = await timeToFirstRow(page, startedAt)
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
        // SH1053 — the shell owns exactly one main landmark (VDS141). A page that renders its own
        // puts a second one beside it, and a screen reader's "skip to main" then has two answers.
        const mains = await page.evaluate(() => document.querySelectorAll("main").length)
        if (mains !== 1) probe.missing.push(`SH1053: ${mains} <main> landmarks, where the shell owns one`)
        const morph = await checkSaveBarMorph(page)
        probe.morph = morph.ran ? (morph.findings.length ? morph.findings.join("; ") : "ok") : morph.why
        if (morph.ran) probe.missing.push(...morph.findings)
      }
      /*
       * SH909 — where a legacy console URL actually ended up, read off the browser.
       *
       * The redirect is client-side: the SPA loads at `/console/...`, React Router matches the
       * legacy pattern and replaces the entry. Nothing on the server can be asked about that, and
       * no source scan can watch a URL change — this is the only place the claim "the old
       * bookmarks still work" is checked rather than asserted.
       */
      if (route.redirectsTo) {
        probe.landedOn = new URL(page.url()).pathname
        if (probe.landedOn !== route.redirectsTo) {
          probe.missing.push(`redirected to ${probe.landedOn}, expected ${route.redirectsTo}`)
        }
      }
      if (route.graphiql) {
        /*
         * SH908 — the explorer's geometry, in whichever chrome this probe opened.
         *
         * No container is a **skip**, not a failure, and it has exactly one honest cause: the
         * explorer renders its empty state on an instance with no enabled CDA token, which is a
         * fixture fact rather than a console defect. `provision.mjs` creates one, so CI has the
         * subject — and `--strict` turns the skip into a failure there, the way SH238/SH659 made
         * a skipped editor drive fail: a probe that skips keeps `ok: true` and reads as coverage.
         */
        probe.graphiql = await checkGraphiQLFit(page)
        if (!probe.graphiql.present) {
          probe.graphiql.why = "this instance declares no enabled CDA token, so the explorer"
            + " renders its empty state instead of the editor (provision.mjs creates one)"
          if (STRICT) probe.missing.push(`graphiql: ${probe.graphiql.why}`)
        } else if (probe.graphiql.belowFold > 8) {
          probe.missing.push(`the editor runs ${probe.graphiql.belowFold}px below the fold`)
        }
      }
      probe.title = (await page.locator("h1, h2").first().innerText().catch(() => "")).trim()

      const shot = join(OUT, `${route.name.replace(/[:/]/g, "_")}.png`)
      await page.screenshot({ path: shot, fullPage: true })
      probe.screenshot = shot
    } catch (e) {
      errors.push(`navigation: ${String(e).split("\n")[0]}`)
    }

    // SH1113 — after every wait above, so a read the page makes late is still this route's cost.
    await watch.settle()
    probe.cost = watch.cost

    // SH1054 — axe on the page a reader actually gets, and the language the document claims.
    if (probe.cost.ms != null) {
      probe.a11y = await runAxe(page)
      if (probe.a11y) {
        const family = familyOf(route.name)
        const { findings, observed } = a11yFindings(route.name, family, probe.a11y.violations, A11Y_DEBT)
        probe.missing.push(...findings)
        const seen = a11yObserved.get(family) ?? new Set()
        observed.forEach((id) => seen.add(id))
        a11yObserved.set(family, seen)
        if (probe.a11y.langMismatch) probe.missing.push(`SH1054: ${probe.a11y.langMismatch}`)
      }
    }

    probe.errors = errors
    probe.warnings = warnings
    probe.failedRequests = failedRequests
    if (exemptDevWorkerNoise(probe, server.dev)) devWorkerExemptionFired = true
    else if (server.dev && probe.graphiql?.present) devWorkerExemptionUnused = true
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
    // SH909 — printed on every run: a redirect that lands correctly is the claim "old bookmarks
    // still work", and it is worth seeing in the log rather than only when it breaks.
    if (probe.landedOn) console.log(`       landed on: ${probe.landedOn}`)
    // SH908 — printed on every run, ok or not, for the same reason the morph is: the fit is a
    // number about two shells this page does not control, and it is worth seeing before it breaks.
    if (probe.graphiql) {
      const g = probe.graphiql
      console.log(g.present
        ? `       graphiql: top ${g.top}, bottom ${g.bottom} of ${g.viewport}`
          + `, below fold ${g.belowFold}, doc overflow ${g.docOverflow}`
        : `       graphiql: skipped — ${g.why}`)
    }
    // SH897 — printed on every run, ok or not. The morph has no subject until a Block AR editor
    // moves, and a fact nobody prints is a fact nobody notices has stopped being true.
    if (probe.morph) console.log(`       morph: ${probe.morph}`)
    for (const f of probe.failedRequests.slice(0, 4)) console.log(`       request: ${f}`)
    for (const e of probe.errors.slice(0, 4)) console.log(`       error: ${e.slice(0, 160)}`)
  }

  /*
   * SH1113 — the per-route budget, the moment the route loop is done.
   *
   * Here rather than at the end of `main`, and that position was bought: the first recording run
   * walked all fifty-seven routes and then died on a 401 in a later probe, so `recordCost` never
   * ran and twenty minutes of measurement went in the bin. Nothing after this line costs a route,
   * so nothing after it can be allowed to lose what this one measured.
   *
   * A route is costed when it **put a first row on screen**, which is `cost.ms != null`, and not
   * when its probe is `ok`. Both halves of that were bought by a run:
   *
   * - The dead page has to be excluded. One that threw on navigation made whatever requests it got
   *   to before it died, and folding that into a ceiling would teach the budget that a broken page
   *   is a cheap one — then fail the day somebody fixes it. A page that rendered nothing has no
   *   first row, so this predicate already excludes it.
   * - `p.ok` was the first predicate and it was **wrong, and flaky with it**. A probe fails for
   *   reasons that say nothing about cost — a missing frost surface, a `mustSee` string, a hero
   *   heading — and which of those fire moved between three consecutive runs on one instance
   *   (41, 45 and 46 of 57). The costed set moved with them, so families appeared and disappeared
   *   from the comparison and the gate reported routes as unbudgeted that a previous run had
   *   costed. A page that rendered its rows and then failed an assertion about its chrome made
   *   exactly the requests it always makes.
   */
  const measured = Object.fromEntries(report.probes
    .filter((p) => p.path && p.cost?.ms != null)
    .map((p) => [p.name, p.cost]))
  report.cost = { measured, families: foldFamilies(measured) }
  if (RECORD_COST) await recordCost(report)
  else await gateCost(report, server.dev)

  // SH1054 — the debt the map still holds that no route produced: paid, so delete it.
  const axed = report.probes.filter((p) => p.a11y && !p.a11y.error).length
  console.log(`\nSH1054: axe ran on ${axed} route(s)`)
  for (const [selector, why] of Object.entries(A11Y_DEBT.vendor ?? {})) {
    console.log(`       excluded ${selector}: ${why.slice(0, 120)}…`)
  }
  for (const note of staleDebt(A11Y_DEBT, a11yObserved)) console.log(`       note: ${note}`)
  for (const p of report.probes.filter((q) => q.a11y?.error)) {
    console.log(`       note: axe could not run on ${p.name}: ${p.a11y.error}`)
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
  // SH1054 — the axe gate at a phone's and a tablet's width, where the layout composes differently.
  const widthsProbe = await checkViewports(browser)
  report.probes.push(widthsProbe)
  console.log(`${widthsProbe.ok ? "ok  " : "FAIL"} ${widthsProbe.name.padEnd(30)} `
    + `${widthsProbe.runs} axe run(s) at ${A11Y_VIEWPORTS.map((v) => v.width).join(" and ")}px`)
  for (const e of widthsProbe.errors) console.log(`       error: ${e.slice(0, 200)}`)

  // SH1053 — what a keyboard reader meets: the skip link first, and focus on the page after a move.
  const keysProbe = await checkKeyboardLandmarks(context)
  report.probes.push(keysProbe)
  console.log(`${keysProbe.ok ? "ok  " : "FAIL"} ${keysProbe.name.padEnd(30)} `
    + `first tab: ${keysProbe.firstTab}; after navigation: ${keysProbe.afterNav}`)
  for (const e of keysProbe.errors) console.log(`       error: ${e.slice(0, 200)}`)

  // SH1046 — what a second folder costs, which is the one claim only a browser can check.
  const navProbe = await checkFolderNavigation(context, site)
  if (STRICT && navProbe.skipped) {
    navProbe.ok = false
    navProbe.errors.push(`--strict: the navigation count must run, and it skipped — ${navProbe.skipped}`)
  }
  report.probes.push(navProbe)
  console.log(`${navProbe.ok ? "ok  " : "FAIL"} ${navProbe.name.padEnd(30)} `
    + (navProbe.skipped
      ? `skipped: ${navProbe.skipped}`
      : `${navProbe.requests} API request(s) on the second navigation`
        + (navProbe.repeated.length ? `, repeated: ${navProbe.repeated.join(", ")}` : "")))
  for (const e of navProbe.errors) console.log(`       error: ${e.slice(0, 200)}`)

  // SH1047 — the race itself, held open and released after the navigation past it.
  const raceProbe = await checkSupersededNavigation(context, site)
  if (STRICT && raceProbe.skipped) {
    raceProbe.ok = false
    raceProbe.errors.push(`--strict: the race must be run, and it skipped — ${raceProbe.skipped}`)
  }
  report.probes.push(raceProbe)
  console.log(`${raceProbe.ok ? "ok  " : "FAIL"} ${raceProbe.name.padEnd(30)} `
    + (raceProbe.skipped
      ? `skipped: ${raceProbe.skipped}`
      : `${raceProbe.held} listing(s) held, the later folder won`))
  for (const e of raceProbe.errors) console.log(`       error: ${e.slice(0, 200)}`)

  // SH1049 — every read answered 500, and what a curator is left looking at.
  const failedProbe = await checkFailedReads(context, routes)
  report.probes.push(failedProbe)
  console.log(`${failedProbe.ok ? "ok  " : "FAIL"} ${failedProbe.name.padEnd(30)} `
    + (failedProbe.skipped
      ? `skipped: ${failedProbe.skipped}`
      : `${failedProbe.checked.length} route(s) driven with every read failing`))
  for (const e of failedProbe.errors) console.log(`       error: ${e.slice(0, 200)}`)

  report.probes.push(driveProbe)
  console.log(`${driveProbe.ok ? "ok  " : "FAIL"} ${driveProbe.name.padEnd(30)} `
    + (driveProbe.skipped
      ? `skipped: ${driveProbe.skipped}`
      : `${driveProbe.site} — ${driveProbe.props} editable prop(s), `
        // SH1174 — three outcomes, and `publishSkipped` is only the reason for NOT attempting.
        + (driveProbe.published ? "published"
          : driveProbe.publishRefused ? `publish refused: ${driveProbe.publishRefused}`
            : `publish skipped: ${driveProbe.publishSkipped}`)))
  for (const e of driveProbe.errors) console.log(`       error: ${e.slice(0, 200)}`)

  await browser.close()

  /*
   * SH927 — the exemption's expiry, asserted rather than remembered.
   *
   * `devWorkerExemptionUnused` means a dev-server run reached the explorer and Monaco did NOT
   * complain, which is the day the interop stopped biting. That is good news and it goes red on
   * purpose: a comment saying "remove this when it starts working" expires silently, and this
   * exemption is the kind of thing that quietly outlives its reason.
   */
  if (devWorkerExemptionUnused && !devWorkerExemptionFired) {
    console.log("\nSH927: **the dev server started the Monaco workers.** The exemption in"
      + " exemptDevWorkerNoise() is spent — delete it, delete this check, and close SH927."
      + " If instead the explorer simply did not render, that is the CDA-token skip above and"
      + " not this.")
    process.exitCode = 1
  }
  if (devWorkerExemptionFired) {
    console.log("\nSH927: Monaco fell back to the main thread under the dev server (known,"
      + " dev-only). The jar is unaffected and the same output there is still a failure.")
  }

  await writeFile(join(OUT, "console-check.json"), JSON.stringify(report, null, 2))

  const failed = report.probes.filter((p) => !p.ok)
  console.log(`\n${report.probes.length - failed.length}/${report.probes.length} probes ok — report in ${OUT}`)
  if (failed.length) {
    console.log(`failing: ${failed.map((p) => p.name).join(", ")}`)
    process.exitCode = 1
  }
}

/** `--record-cost`: rewrite the budget from this run, keeping every entry's `why`. */
async function recordCost(report) {
  const budget = JSON.parse(await readFile(BUDGET, "utf8"))
  const families = report.cost.families
  const baseline = families[budget.baseline]
  if (!baseline || !(baseline.ms > 0)) {
    console.log(`\nSH1113: --record-cost needs the baseline route '${budget.baseline}' to have`
      + " loaded, because every firstRow ceiling is a ratio against it. It did not, so nothing was"
      + " written — a budget seeded against a baseline of zero is worse than no budget.")
    process.exitCode = 1
    return
  }
  const routes = {}
  for (const family of Object.keys(families).sort()) {
    const seeded = seedCeilings(families[family], baseline.ms)
    routes[family] = { ...seeded, why: budget.routes?.[family]?.why ?? "" }
  }
  for (const [family, entry] of Object.entries(budget.routes ?? {})) {
    // An entry this run did not walk keeps its numbers: it is a missing fixture, not a dead route,
    // and re-recording would silently drop the ceiling somebody argued for.
    if (!routes[family]) routes[family] = entry
  }
  budget.routes = Object.fromEntries(Object.keys(routes).sort().map((k) => [k, routes[k]]))
  budget.measured = new Date().toISOString().replace(/:\d\d\.\d+Z$/u, "Z")
  await writeFile(BUDGET, `${JSON.stringify(budget, null, 2)}\n`)

  const blank = Object.entries(budget.routes).filter(([, e]) => !e.why?.trim()).map(([k]) => k)
  console.log(`\nSH1113: recorded ${Object.keys(routes).length} route families into ${BUDGET}`)
  if (blank.length) {
    console.log(`       ${blank.length} entr${blank.length === 1 ? "y has" : "ies have"} no \`why\``
      + ` — write one before committing, or the gate refuses them: ${blank.join(", ")}`)
  }
}

/**
 * The gate itself: findings fail, notes are printed.
 *
 * SH1171 — only against the production build the ceilings were measured on. Under the Vite dev
 * server `React.StrictMode` runs every effect twice, so every route read ~6 more times and ~16 KB
 * more than the jar, and all fifty families came back over: a gate red for the environment, which
 * is the state SH927 exists to prevent. The dev run still prints what it measured, as notes.
 */
async function gateCost(report, dev) {
  const budget = JSON.parse(await readFile(BUDGET, "utf8"))
  let findings
  try {
    findings = compare(budget, report.cost.measured)
  } catch (e) {
    console.log(`\n${e.message}`)
    process.exitCode = 1
    return
  }
  const notes = [...staleEntries(budget, report.cost.measured),
    ...headroom(budget, report.cost.measured)]
  report.cost.findings = findings
  report.cost.notes = notes

  const walked = Object.keys(report.cost.families).length
  console.log(`\nSH1113: ${walked} route famil${walked === 1 ? "y" : "ies"} costed against`
    + " console-route-budget.json")
  for (const note of notes) console.log(`       note: ${note}`)
  if (dev) {
    if (findings.length) {
      console.log(`       note: ${findings.length} finding(s) not gated — the budget is a production-build`
        + " measurement and StrictMode doubles every read under the dev server (SH1171). Run against"
        + " the jar to gate: --base http://localhost:2710")
    }
    return
  }
  for (const finding of findings) console.log(`       OVER: ${finding}`)
  if (findings.length) process.exitCode = 1
}

main().catch((e) => {
  console.error(`console-check: ${e.message}`)
  process.exitCode = 1
})
