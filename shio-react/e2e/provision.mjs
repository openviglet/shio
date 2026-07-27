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
      url: API,
      furl: SITE.toLowerCase().replaceAll(/[^a-z0-9]+/g, "-"),
    }),
  })
  if (!res.ok) throw new Error(`POST /site → ${res.status} ${await res.text()}`)
  const site = await res.json()
  console.log(`site created: ${site.name} (${site.id})`)
  return site
}

async function applyBlueprint() {
  const res = await fetch(`${API}/api/v2/agent/blueprints/${BLUEPRINT}/apply`, {
    method: "POST",
    headers: { ...json, Authorization: AUTH },
    body: JSON.stringify({ site: SITE, params: { brand: BRAND } }),
  })
  const body = await res.text()
  if (!res.ok) throw new Error(`apply ${BLUEPRINT} → ${res.status} ${body}`)
  const applied = JSON.parse(body)
  console.log(
    `blueprint ${BLUEPRINT}: postTypes=${applied.postTypes?.length ?? 0} changed=${applied.changed ?? "?"}`,
  )
}

async function main() {
  await setupAdminPassword()
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
