/*
 * Provisions a freshly booted Shio instance so the console smoke check has something to walk
 * (SH182). Idempotent: run it against an instance that already has content and it does nothing.
 *
 * A clean instance is not usable by the check as it stands: the admin user is created with
 * **no password** (first-access setup sets it) and there is no site at all, so
 * `console-check.mjs` would fail on authentication and then on "this instance has no sites"
 * — neither of which is a console defect.
 *
 * Three steps, in the order a human would do them:
 *   1. `POST /api/v2/setup` — set the admin password (CSRF-exempt, permitAll, and only
 *      possible while the password is unset; an "already completed" 403 is success here).
 *   2. `POST /api/v2/site` — create the site. This one needs the **CSRF dance** (fetch a
 *      token, keep the cookie, echo it back) because site creation is console-only and
 *      session-shaped: it is the friction SH170 exists to remove, and doing it the long way
 *      here rather than hiding it keeps the cost visible.
 *   3. `POST /api/v2/agent/blueprints/marketing-site/apply` — fill the site with the SH94
 *      package the Block O defects were all reproduced against. CSRF-exempt (agent surface),
 *      so it is one call with Basic auth.
 *
 * Usage
 *   node e2e/provision.mjs [--api http://localhost:2710] [--site "Console Check"] [--brand Acme]
 *
 * Credentials: SHIO_USER / SHIO_PASSWORD (default admin/admin) — the password it sets is
 * SHIO_PASSWORD, so the check that follows can authenticate with the same pair.
 */
const args = process.argv.slice(2)
const flag = (name, fallback) => {
  const i = args.indexOf(`--${name}`)
  return i >= 0 && args[i + 1] && !args[i + 1].startsWith("--") ? args[i + 1] : fallback
}

const API = flag("api", process.env.SHIO_URL || "http://localhost:2710")
const SITE = flag("site", process.env.SHIO_SITE || "Console Check")
const BRAND = flag("brand", "Acme")
const BLUEPRINT = flag("blueprint", "marketing-site")
const USER = process.env.SHIO_USER || "admin"
const PASS = process.env.SHIO_PASSWORD || "admin"
const AUTH = "Basic " + Buffer.from(`${USER}:${PASS}`).toString("base64")

const json = { "Content-Type": "application/json" }

async function setupAdminPassword() {
  const res = await fetch(`${API}/api/v2/setup`, {
    method: "POST",
    headers: json,
    body: JSON.stringify({ password: PASS }),
  })
  // 403 = a password is already set, which is the state this step wants to reach.
  if (!res.ok && res.status !== 403) {
    throw new Error(`setup → ${res.status} ${await res.text()}`)
  }
  console.log(`admin password: ${res.ok ? "set" : "already set"}`)
}

async function sites() {
  const res = await fetch(`${API}/api/v2/site`, { headers: { Authorization: AUTH } })
  if (!res.ok) throw new Error(`GET /site → ${res.status} ${await res.text()}`)
  return res.json()
}

/**
 * Creates the site through the console's session+CSRF path: `GET /api/v2/csrf` hands back the
 * token and sets the `XSRF-TOKEN` + session cookies, and the POST has to carry both.
 */
async function createSite() {
  const csrfRes = await fetch(`${API}/api/v2/csrf`, { headers: { Authorization: AUTH } })
  if (!csrfRes.ok) throw new Error(`GET /csrf → ${csrfRes.status}`)
  const { token, headerName } = await csrfRes.json()
  const cookies = (csrfRes.headers.getSetCookie?.() ?? [])
    .map((c) => c.split(";")[0])
    .join("; ")

  const res = await fetch(`${API}/api/v2/site`, {
    method: "POST",
    headers: { ...json, Authorization: AUTH, [headerName]: token, Cookie: cookies },
    body: JSON.stringify({
      name: SITE,
      description: "Site the console smoke check walks",
      // No `url` — and that is the fixture's whole point since SH664. A non-empty `url`
      // declares an **external front end**: `siteDestination` sends the console's open-site
      // link there, and the Universal Editor frames it. This said `url: API`, so the editor
      // framed the instance itself — `GET /` answers 302 to `/login`, i.e. the console
      // rendered the console's login page inside its own iframe — and `checkEditorDrive`
      // skipped the one site CI provisions on purpose, because it excludes every site with
      // a `url` before it asks the renderer anything.
      //
      // Empty is also the *normal* state of a site an agent just built, which is the case
      // SH183/SH184 exist for: Shio's own renderer serves it and `?editor=true` supplies the
      // SH185 bridge. Measured on a live instance — this site's `/preview/<name>/?editor=true`
      // carries 14 `data-shio-prop` elements and the bridge script, so it is a target the
      // drive will take.
      furl: SITE.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-"),
    }),
  })
  if (!res.ok) throw new Error(`POST /site → ${res.status} ${await res.text()}`)
  const site = await res.json()
  console.log(`site created: ${site.name} (${site.id})`)
  return site
}

/**
 * SH1144 — with `mergePostTypes`, because a clean instance refuses the package without it.
 *
 * The bootstrap site a fresh instance provisions declares a `Page` with `title` and `content`, and
 * marketing-site's own `Page` adds `description` and `sections`. The apply keeps an existing type
 * unless asked (SH151), then refuses the content that needs the new fields: `400 undeclared-field`
 * at `ops[20]`, so every check that provisions first walked a site with no pages on exactly the
 * instance CI starts from. SH152's merge is additive — it adds the fields and removes none — which
 * is what provisioning a smoke-check fixture wants.
 */
async function applyBlueprint() {
  const res = await fetch(`${API}/api/v2/agent/blueprints/${BLUEPRINT}/apply`, {
    method: "POST",
    headers: { ...json, Authorization: AUTH },
    body: JSON.stringify({ site: SITE, params: { brand: BRAND }, mergePostTypes: true }),
  })
  const body = await res.text()
  if (!res.ok) throw new Error(`apply ${BLUEPRINT} → ${res.status} ${body}`)
  const applied = JSON.parse(body)
  // SH1176 — the count lives in the content half, an `ShAgentChangeSet` under `content`. Read off the
  // top level it was always absent, and `?` said the same thing for a whole site written as for none.
  const changed = applied.content?.changed
  console.log(
    `blueprint ${BLUEPRINT}: postTypes=${applied.postTypes?.length ?? 0} `
      + `changed=${changed ?? "unknown (content.changed absent)"}`,
  )
}

/**
 * SH908 — the GraphQL explorer has no subject without one of these.
 *
 * The page is token-only by design (SH4): the console session carries no `ROLE_CDA`, so the
 * explorer authorizes with a CDA token's `Key` header, and with no enabled token it renders its
 * empty state and never mounts GraphiQL at all. That is the correct page — and it means the live
 * check's geometry probe, the one thing no source scan can answer about a third-party editor
 * sized to its container, had nothing to measure on a freshly provisioned instance.
 *
 * `READ`, and no `siteIds`, which is the least this needs: the explorer only queries. The server
 * mints the secret and the id, so this posts a description of the token rather than a token.
 */
async function ensureCdaToken() {
  const res = await fetch(`${API}/api/v2/api-token`, { headers: { Authorization: AUTH } })
  if (!res.ok) throw new Error(`GET /api-token → ${res.status} ${await res.text()}`)
  const tokens = await res.json()
  if (tokens.some((tk) => tk.enabled && tk.token)) {
    console.log(`cda token: ${tokens.length} already declared — nothing to provision`)
    return
  }

  // The same session+CSRF dance `createSite` does, for the same reason: token administration is
  // console-only, so Basic auth alone is a 403 that reads like a permissions bug.
  const csrfRes = await fetch(`${API}/api/v2/csrf`, { headers: { Authorization: AUTH } })
  if (!csrfRes.ok) throw new Error(`GET /csrf → ${csrfRes.status}`)
  const { token, headerName } = await csrfRes.json()
  const cookies = (csrfRes.headers.getSetCookie?.() ?? []).map((c) => c.split(";")[0]).join("; ")

  const created = await fetch(`${API}/api/v2/api-token`, {
    method: "POST",
    headers: { ...json, Authorization: AUTH, [headerName]: token, Cookie: cookies },
    body: JSON.stringify({
      title: "Console Check (READ)",
      description: "Delivery token the console smoke check's GraphQL probe queries with",
      scope: "READ",
      env: "PROD",
      enabled: true,
      siteIds: [],
    }),
  })
  if (!created.ok) throw new Error(`POST /api-token → ${created.status} ${await created.text()}`)
  console.log("cda token: created (READ)")
}

async function main() {
  await setupAdminPassword()
  // Before the site guard below: an instance provisioned before this step existed has the site
  // and no token, and an early return would leave it that way for ever.
  await ensureCdaToken()
  const existing = await sites()
  if (existing.some((s) => s.name === SITE)) {
    console.log(`site '${SITE}' already exists — nothing to provision`)
    return
  }
  await createSite()
  await applyBlueprint()
}

main().catch((e) => {
  console.error(`provision: ${e.message}`)
  process.exitCode = 1
})
