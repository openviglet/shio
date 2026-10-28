---
title: Security
description: "Authentication in Viglet Shio: sessions for the console, API-key scopes for everything else, what an AGENT key may not do, CSRF on cookie callers only, CORS, and how uploaded files and rich text are kept from running as script."
---

Shio has two kinds of caller and treats them differently on purpose:

- **A browser** carries a session cookie and is therefore exposed to cross-site request
  forgery, so it gets CSRF protection.
- **A program** (an agent, a front end, a CI job, the CLI) carries an **API key** in a
  header, holds no cookie, and is therefore not exposed to CSRF at all.

Most of the design below follows from keeping those two apart rather than making one
pretend to be the other.

---

## Authenticating a browser

```http
POST /api/v2/login
```

A successful login sets a session cookie. Console writes additionally require a CSRF token,
which the server issues in a cookie and expects echoed in a header. `GET /api/v2/csrf`
hands one out.

Identity can come from Shio's own user store, from OAuth2/OIDC (`shio.keycloak=true` for a
Keycloak realm), or from a social provider configured under
`/api/v2/provider/auth`. Passwords are hashed, never stored recoverably.

---

## Authenticating a program

A program authenticates with an **API key** in a `Key` header: no cookie, no login round
trip, no CSRF token:

```http
GET /api/v2/cda/site HTTP/1.1
Key: 7f3c9a12b4e05d68af1c2903b
```

Create keys in the console (Administration → API Tokens) or over `/api/v2/api-token`.

### A key carries four independent limits

| Axis | Values | Controls |
|---|---|---|
| **Scope** | `READ` · `PREVIEW` · `WRITE` · `AGENT` | Which operations |
| **Environment** | `PROD` · `PREVIEW` | Which content state: a `PREVIEW` key reads **drafts** |
| **Site allow-list** | Any subset of sites; empty means all | Which content |
| **Rate limit** | Per-token bucket | How often |

Scope and environment are genuinely orthogonal, and that is worth internalising: **scope is
what you may do, environment is what you may see.** A `READ` key with `PREVIEW`
environment is a legitimate and useful thing: a preview build of a front end.

### Where a key is accepted

A key authenticates on **three namespaces and nowhere else**:

| Path | Accepts a key |
|---|---|
| `/api/v2/cda/**` | yes |
| `/graphql` | yes |
| `/api/v2/agent/**` and `/mcp` | yes |
| everything else | **no**: a `Key` header on another path is ignored |

That confinement is the point: a leaked delivery key cannot be replayed against the console
API.

### What an `AGENT` key may not do

The `AGENT` scope exists so an agent's credential is not an administrator's:

| May | May not |
|---|---|
| Write on `/api/v2/agent/**` and `/mcp` | Write **anywhere else**, on the delivery API it is read-only |
| Read drafts (agent reads are draft-preferred) | Publish, unless `mayPublish` is set on the token |
| Be site-scoped, expiring and rate-limited | Escape any of those |

It is deliberately **not** an alias for `WRITE`: a `WRITE` key can patch published content
outside the agent protocol's guards, which is exactly what an agent's credential must not be
able to do. See [Letting an agent in](/docs/agent-safety/).

### Short-lived preview tokens

`/api/v2/preview-token` mints an **expiring, single-site** token for one purpose: letting
someone see a draft. It grants nothing else, and it expires on its own.

---

## Authorization

| Layer | Enforces |
|---|---|
| **Roles** | What a user may do at instance level, `ADMIN` gates administration and tenant management |
| **Content ACLs** | Grants on a folder or post, inherited down the tree, default-allow. Read and set them at `GET`/`PUT /api/v2/object/{id}/acl` |
| **Token roles** | A key's scope becomes a role (`CDA`, `CDA_PREVIEW`, `CDA_WRITE`, `AGENT`) which is what the URL rules test |

The console and the agent surface enforce **the same ACL**, and where they ever disagree the
stricter one is treated as correct. A curator's session is not a lower bar than an agent's
credential; it is a different one.

---

## CSRF

CSRF protection applies to **cookie-bearing callers**. Six paths are exempt, and each
exemption is narrowed to callers that hold **no session**:

| Path | Why |
|---|---|
| `/graphql` | Token-authenticated delivery |
| `/api/v2/cda/**` | Token-authenticated, stateless |
| `/api/v2/post-type/**` | The CLI pushes the content model with Basic auth, but the console editor uses this path too, so the exemption checks for the absence of a session rather than exempting the path outright |
| `/api/v2/render/post-types` | The call that makes a site visible had to be reachable without a browser |
| `/api/v2/preview-token` · `/api/v2/site-webhook` · `/api/v2/import` · `/api/v2/staticfile/**` | Stateless agent and CLI paths; bytes in particular cannot travel on the agent surface |

The mechanism is worth naming because it is the interesting part: the matcher exempts a
request **only when it carries no session cookie**. A browser hitting the same URL is still
CSRF-checked. That is how one path can serve both a stateless CLI and a cookie-bearing
console without dropping protection for either.

---

## CORS

CORS is enabled, with the allowed origins configured by:

```properties
shio.allowedOrigins=localhost
```

Set it to the origins your front end and your console are actually served from. A delivery
API called from a browser needs the browser's origin listed here; a server-side front end
(the usual case for Next.js) does not, because the request never leaves your infrastructure.

---

## Uploaded files and rich text

Content is written by curators, by agents and by whatever a curator pastes, so Shio treats two
kinds of content as potentially hostile: the **files** that get uploaded, and the **HTML** in a
rich-text field.

### Uploaded files are served as files

Every response carrying uploaded bytes (`/file_source/**`, the static-file API and a site's
assets) sends three headers:

| Header | What it does |
|---|---|
| `X-Content-Type-Options: nosniff` | A browser may not treat the bytes as a type the server did not declare |
| `Content-Disposition` | `inline` only for raster images (PNG, JPEG, GIF, WebP, AVIF, BMP, ICO). Everything else, **SVG included**, is an `attachment` |
| `Content-Security-Policy: default-src 'none'; sandbox` | An asset opened directly runs nothing, in an origin of its own |

An uploaded SVG or HTML file therefore downloads instead of running as a page on the console's
own origin. **A delivered site is unaffected**: a browser honours the disposition when you
navigate to a file and ignores it for an `<img>`, a stylesheet or a script, so pages load
exactly as before.

The download name is the file's own name, quoted so that nothing in it can break the header: a
quote or a control character is escaped or dropped, and a name outside ASCII (`relatório.pdf`)
downloads as itself.

### What may be uploaded

An upload meets a policy before it is stored:

```properties
shio.upload.policy.text/html=deny
shio.upload.policy.image/svg+xml=sanitize
shio.upload.default-verdict=allow
```

Each media type takes `allow`, `deny` or `sanitize`; an exact type beats a `family/*` entry,
which beats the default. Out of the box:

- **HTML, XHTML and JavaScript are denied.** The refusal is a 400 that names the property that
  re-allows the type. `shio clone --scripts` uploads a site's own JavaScript on purpose, so set
  `shio.upload.policy.text/javascript=allow` on an instance you replicate that way.
- **SVG is sanitized, not refused**, because a site's logo is very often one. Scripts, event
  handlers, `foreignObject` and external links are removed and everything else is kept; the
  stored file carries `SANITIZED: true`.
- The verdict is taken on the file name as well as the declared type, and the stricter wins.

An agent can see files stored before the policy existed: `shio_assets` flags them `unsafe`, and
`shio verify` reports `asset-unsafe-type` for a denied type in a content tree before a push
meets the refusal.

### Rich text is cleaned when it is saved

A field using the **HTML Editor** widget is cleaned against an allow-list on every save,
from the console, the REST API, an agent's batch or the Universal Editor. Scripts, event
handlers and `javascript:`/`data:` URLs are removed; ordinary formatting, links, relative URLs,
images and HTML5 sections (`header`, `main`, `section`, `footer`) stay, and clean markup is
stored exactly as written.

**Layout templates are not touched.** `PageLayout` and `Region` use the **Ace Editor - HTML**
widget, where writing markup (scripts included) is the point.

Two ways to find out before a visitor does:

- An agent's `dryRun` names what a write would strip, per field.
- `shio verify` reports `html-unsafe` on published rich text that was stored before the cleaning
  existed. Saving the post again cleans it.

---

## Hardening checklist

1. **Set `shio.allowedOrigins`** to real origins, not a wildcard.
2. **Give an agent an `AGENT` key**, not a console password, and leave `mayPublish` off
   until you have decided otherwise.
3. **Scope keys to sites** and give them an expiry. A key with an empty allow-list can read
   every site.
4. **Put TLS in front** of the instance; API keys travel in a header.
5. **Change the default administrator password** before exposing the console.
6. **Check the rate limits** (`shio.cda.rate-limit.*`): the limiter is per JVM, so a
   multi-node deployment multiplies each token's budget by the node count.

---

## Related Pages

| Page | Description |
|---|---|
| [Letting an agent in](/docs/agent-safety/) | What the `AGENT` scope, drafts and confirm tokens protect |
| [Content Delivery API](/docs/headless/content-delivery-api/) | Scopes and environments from a front end's side |
| [Administration Guide](/docs/administration-guide/) | Users, groups, roles and permissions in the console |
| [Configuration Reference](/docs/configuration-reference/) | Every security-related property |
| [Multi-Tenancy](/docs/multi-tenancy/) | How a key scopes delivery to its tenant |
