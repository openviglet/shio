# Shio — Improvements & Roadmap (design rationale)

> **What this file is:** the *design rationale* (the what & why) for **unshipped**
> work — the spec behind each backlog item. It is one of three roadmap documents;
> keep them in sync:
>
> | File | Holds | One line per |
> |---|---|---|
> | [ROADMAP.md](ROADMAP.md) | **Task status** (single source of truth) — active backlog | task |
> | [CHANGELOG.md](CHANGELOG.md) | What has **shipped** (git is authoritative) | shipped task |
> | **IMPROVEMENTS.md** (this file) | Design rationale for active sections | — |
>
> **Founding rule:** once a task ships, its detail is removed from here and
> reduced to a one-line entry in CHANGELOG.md — `git log` is the authoritative
> history. Do not re-accrete shipped implementation reports into this file.

**Status legend** (status itself lives in ROADMAP.md): ⏳ partial · 📋 designed · 💭 idea.

**Cross-references** use relative paths from `docs/`; linked code may have moved —
verify against `git ls-files` before acting.

## Table of contents

- [0. Strategy — why headless-Java, not a rewrite](#0-strategy--why-headless-java-not-a-rewrite)
- [I. Headless foundation — the Content Delivery API](#i-headless-foundation--the-content-delivery-api)
- [II. JavaScript-for-pages developer experience](#ii-javascript-for-pages-developer-experience)
- [III. Composable platform primitives](#iii-composable-platform-primitives)
- [IV. Optional server-side JS rendering (GraalJS)](#iv-optional-server-side-js-rendering-graaljs)
- [V. Legacy retirement](#v-legacy-retirement)
- [VI. Market positioning / bold bets](#vi-market-positioning--bold-bets)

---

## 0. Strategy — why headless-Java, not a rewrite

### 0.1 The actual goals (separated)

The driving request mixed two goals that are **independent**:

1. **"Be relevant to today's market"** → this is an *architecture* decision
   (headless / API-first / composable), **not** a language decision.
2. **"Let JavaScript developers build pages, with a low learning curve"** → this
   is a *developer-experience* decision about templating/frontend, **also not** a
   backend-language decision.

Both are achievable without rewriting the backend. Conflating them into "rewrite
the backend in Node/Go/PHP" is the strategic trap this roadmap avoids.

### 0.2 What Shio is today (the facts that drive the decision)

- Backend is **small and modern**: ~136 Java files / ~9k LOC, Spring Boot 4 +
  Java 21, REST + GraphQL, JPA/Liquibase, OAuth2, Hazelcast, Tika. Not a legacy
  monolith.
- It is **part of an ecosystem** that is entirely Java/Spring — Turing (search/AI),
  Dumont (connectors). Shared models, security patterns, and SDKs.
- The **headless transition has already begun**: `@viglet/shio-react-sdk` (CDA
  consumer), `@viglet/shio-editor-cors` (visual editor), API-token auth, a
  unified Post API with publish/versions. See [CHANGELOG.md](CHANGELOG.md) Block 0.

### 0.3 Why NOT rewrite the backend (binding non-goal)

- A rewrite costs 6–18 months just to **reach parity** — zero new user value.
- It **destroys the Turing/Dumont sinergy**: shared models, security, SDKs become
  an HTTP-only island that must be re-integrated.
- **The language is not the market problem.** Reference composable CMSs are Java
  (Magnolia, dotCMS) or JS; PHP (WordPress/Drupal) *is* the legacy we want to move
  past; Go is great for infra but rare for CMS with weak admin/templating ecosystem.
- **A CMS's differentiator is never the server language** — it's the content
  model, the API, the editing experience, and delivery. The end user never knows
  if the backend is Java or Go.
- Conclusion: rewrite = **high cost, high risk, no perceived value**. Keep Java.

### 0.4 Why Groovy is not the answer to "JS for pages"

Groovy would solve *server-side scripting*, but the stated audience is **JavaScript
developers**, and the learning curve we want to cut is theirs. Groovy is not JS.
The right answers, in order:

1. **Headless + Next.js/React** (Block B) — pages are a pure JS/TS project that
   consumes the CDA. Zero Java exposed. This is the primary recommendation.
2. **GraalJS** (Block D, optional) — if a team insists on server-side rendering
   *inside* Shio, run real JavaScript on the JVM as the template engine, replacing
   Thymeleaf. Still JS, still on the existing Java runtime.

### 0.5 The north star

> Java/Spring stays the content **engine**. Everything a site-builder touches —
> pages, templates, rendering, data fetching — is **JavaScript/TypeScript**, via a
> first-class CDA and SDKs. "Headless-first, JS-on-top."

---

## I. Headless foundation — the Content Delivery API

> The CDA seed exists but is not yet a *contract*. Today's reads are spread across
> `/api/v2/discovery`, `/api/v2/post-unified`, and console endpoints, mixing
> authenticated console concerns with public delivery. This section carves out a
> stable, cacheable, public-grade read surface.

### I.1 Versioned read-only CDA contract (SH1) — ✅ shipped

Shipped: `/api/v2/cda/**` read-only namespace + frozen DTO contract. Detail in
[CHANGELOG.md](CHANGELOG.md) Block A; design record in
`docs/specs/SH1-cda-read-contract.md`. The forward-compat constraints there
(state is a service-level param, not a URL segment; `siteId` is a first-class
query param) bind SH2/SH3/SH5 below.

### I.2 API-token scopes + per-site keys (SH2)

`ShApiToken` currently authenticates; it does not *scope*. Add `scope`
(`READ`/`PREVIEW`/`WRITE`) and an optional `siteId` allow-list, enforced in
`ShApiTokenFilter`. A public delivery key should read exactly one site's published
content and nothing else — the precondition for putting a key in a client-side or
edge environment.

### I.3 Rate limiting + caching headers (SH3)

Emit `Cache-Control` + `ETag`/`Last-Modified` on CDA reads so a CDN absorbs the
load, and add a per-token token-bucket limiter. This is what makes the CDA safe on
the open internet.

### I.4 Real GraphQL delivery schema (SH4)

`ShGraphQLSchema` builds a schema programmatically, but the content surface is a
placeholder. Generate read types from each post-type so a JS client queries only
the fields a page needs (the headless GraphQL value proposition). REST (I.1)
remains the simple default; GraphQL is the precision option.

### I.5 Per-environment keys (SH5)

A key carries `env` (`prod`/`preview`). Preview keys see drafts (ties to §III.2),
prod keys see only published. Lets the same front-end codebase point at either
with only a token swap.

---

## II. JavaScript-for-pages developer experience

> This section *is* the stated objective. The React SDK already exists with data
> hooks; the work is to (a) make the core framework-agnostic, (b) complete the
> render surface, and (c) ship a turnkey starter so a JS dev is productive in
> minutes without seeing Java.

### II.1 `@viglet/shio-client` — framework-agnostic core (SH6) — ✅ shipped

Shipped: zero-dependency [`shio-client/`](../shio-client) package targeting the
CDA contract. Detail in [CHANGELOG.md](CHANGELOG.md) Block B. Design note: the new
core deliberately targets the **CDA** (`/api/v2/cda/**`, SH1), not the legacy
console endpoints the old `shio-react-sdk` client hit — that's why SH6 depends on
SH1. **Follow-up (SH7):** rebase `shio-react-sdk`'s hooks onto this core so React
becomes one consumer, not a parallel implementation.

### II.2 Complete the React SDK (SH7) — ✅ shipped

Shipped: `shio-react-sdk` rebased onto `@viglet/shio-client` (React is now one
consumer, not a parallel client) + new hooks (`useShioBreadcrumb`,
`useShioPostByUrl`, `useShioSite`) and render components (`ShioRichText`,
`ShioImage`, `ShioRelator`, `ShioField`). Detail in [CHANGELOG.md](CHANGELOG.md)
Block B. Design caveat: `ShioField`'s widget resolution is **heuristic** (by value
shape) because the CDA delivers `attrs` as a plain JSON map with no per-field
widget metadata. **Follow-up (SH11):** expose post-type schema in the CDA so
`ShioField` can resolve by the real widget type. `useShioSearch` was descoped to
SH11-adjacent work — the CDA has no full-text endpoint yet; `useShioQuery` covers
site/folder/post-type filtering.

### II.3 `create-shio-app` — Next.js starter (SH8) — ✅ shipped

Shipped: [`create-shio-app/`](../create-shio-app) scaffolder + Next.js 15 App-Router
template (catch-all `[...slug]` → `getPostByUrl`, SSG via `generateStaticParams`,
ISR via `revalidate`). Detail in [CHANGELOG.md](CHANGELOG.md) Block B. Consumes the
SH6 client, which consumes the SH1 CDA — the full headless chain end to end.
**Follow-up (SH9 — ✅ shipped):** the fixed `revalidate` is now backed by an
on-demand revalidation receiver + draft preview (see §II.4).

### II.4 SSG/ISR/SSR recipes + on-demand revalidation (SH9) — ✅ shipped

Shipped (Next.js consumer side): the `create-shio-app` template documents and
implements all three rendering modes against the CDA — SSG (`generateStaticParams`),
ISR (`revalidate`), and on-demand revalidation via a signed webhook receiver
(`app/api/revalidate` → `revalidatePath`/`revalidateTag`), plus SSR draft preview
(`app/api/preview` + Draft Mode through a PREVIEW-env client, riding on SH5).
Full recipe + the webhook contract: [recipes/nextjs-rendering.md](recipes/nextjs-rendering.md).
Detail in [CHANGELOG.md](CHANGELOG.md) Block B. **Dependency note:** the receiver
defines and is ready for the publish webhook, but the **emitter** lives in the
backend publish path — that's **SH12** (§III.1). Console "preview this draft" UX
is **SH13** (§III.2); the rendering side is in place today.

### II.5 Mature the Universal Editor bridge (SH10) — ✅ shipped

Shipped: the save-back half of the inline editor. A CDA write surface
(`ShCdaWriteAPI`/`ShCdaWriteService` under `/api/v2/cda`, gated by
`ROLE_CDA_WRITE` — the SH2 WRITE scope, §I.2) merges attribute patches into a
post's `DRAFT` row and publishes on demand, re-enforcing the SH2 per-site
allow-list; `getEditable` returns the post-type field schema for the editor
panel (field→DOM mapping). A React console editor shell (`/console/universal-editor`)
drives the existing `shio-editor.js` bridge over `postMessage` and saves back
with a WRITE token (a credential-less request so the token, not the console
session, authenticates). Detail in [CHANGELOG.md](CHANGELOG.md) Block B. **Live
draft preview** rides on the draft-targeted writes + SH5 PREVIEW-env reads; the
in-console "preview this draft" UX is **SH13** (§III.2), and the publish webhook
emitter is **SH12** (§III.1).

### II.6 Type generation from post-types (SH11) — ✅ shipped

Shipped: a public CDA post-type schema (`GET /api/v2/cda/post-type[/{name}]`,
recursive into Relator sub-fields and array-aware) + a pure, dependency-free
`generateShioTypes` in `@viglet/shio-client` and a `shio-types` CLI that turn each
post-type into a `.d.ts` — one typed `…Post` (a `ShioPost & {…}` intersection)
per type plus a `ShioPostTypes` name→type map. Page authors get autocomplete on `post.attrs.*`,
closing the loop between the dynamic content model and the statically-typed JS
front. Wired into `create-shio-app` (`npm run shio:types`). Detail in
[CHANGELOG.md](CHANGELOG.md) Block B; spec: [specs/SH11-type-generation.md](specs/SH11-type-generation.md).
The schema is also what the §II.2 `ShioField` follow-up needs to resolve widgets
by their real type instead of heuristically.

### II.7 Post-types as typed TS code (SH62)

**The idea.** SH16 already round-trips the content model as code — `pull` writes
`shio/post-types/*.json`, `push` applies it back. But hand-editing that JSON is
blind: a wrong widget name or a malformed `widgetSettings` only fails at `push`
time with a 400. A **typed TypeScript DSL** fixes this at the source — the
developer writes `postType("News", { fields: [ text("title", { isTitle: true }),
comboBox("category", { choices: { tech: "Tech" } }) ] })` and the `tsserver`
already running in their editor gives autocomplete, refactor/rename, and
compile-time validation **with no custom VSCode extension to build or maintain**.
It is the authoring inverse of SH11: SH11 turns the live model *into* types for
**consuming** content; SH62 turns typed code *into* the model for **authoring** it.

**The middle path (why not "DSL as the only source of truth").** Making the DSL
the sole source of truth breaks down against two facts of this codebase: the
React console has a first-class post-type **GUI editor** (a second legitimate
writer), and `pull` can't losslessly regenerate hand-written DSL (comments,
loops, shared field helpers) from flattened JSON. So the DSL is a **layer**, not
a replacement:

- **JSON stays the canonical interchange/push format.** The DSL *compiles to* the
  same normalized `ShPostTypeDef` JSON that SH16 already defines (reusing
  `normalizeDef`/`serializeDef` in `post-types-io.mjs`), and the admin API
  (`PUT /api/v2/post-type/{name}`) is untouched. The DSL is the source, the JSON
  is the build output, the wire format is unchanged.
- **The DSL owns only the custom subset.** System post-types are backend-owned
  (`push` already refuses them — 409); the DSL never claims them.
- **Two guards keep it single-sourced** instead of split-brained: a CI **drift
  check** (`shio push --check` — compile, diff, fail on divergence, so a pipeline
  can assert git == server) and a console **"managed by code" lock** (a `managed`
  flag + a read-only badge in the GUI editor, so a content author *sees* a
  code-owned model but can't silently overwrite it).

**Shape.** A pure, dependency-free package (`@viglet/shio-model`, sibling to the
equally-pure `generateShioTypes` in `@viglet/shio-client`): one factory per the
14 widgets, with `widgetSettings` typed as **per-widget discriminated unions** so
the editor enforces the right settings shape per widget (e.g. `choices` as an
object instead of the backend's `"id:label\nid2:label2"` string, typed
File/ContentSelect/MultiSelect/Relator/Search settings). The `cli` recognizes
`*.ts`/`*.mjs` sources in `shio/post-types/` and compiles them before the diff.
Tasks: **SH63** (DSL core) → **SH64** (CLI compile step) → **SH65** (CI drift
guard) → **SH66** (console managed-by-code lock).

---

## III. Composable platform primitives

> Table-stakes for the headless market (Contentful/Strapi/Sanity parity).

### III.1 Outbound webhooks (SH12)

Per-site webhook targets with HMAC signing + retry, emitted from the unified
publish/unpublish/delete path. The backbone for incremental static regeneration
(§II.4) and downstream integrations.

### III.2 Draft / preview mode (SH13)

Short-lived preview tokens expose unpublished content via the CDA so the JS front
renders a live preview before publishing. Pairs with the editor bridge (§II.5).

### III.3 Versioning UI (SH14) — ✅ shipped

Shipped: a console **History** dialog on the post editor that diffs the
unpublished `DRAFT` against the live `PUBLISHED` snapshot (field-level,
schema-driven) + a `POST /api/v2/post-unified/{id}/restore` that reverts the
draft to published. Detail in [CHANGELOG.md](CHANGELOG.md) Block C; spec:
[specs/SH14-versioning-ui.md](specs/SH14-versioning-ui.md). Design note: the
unified model is **two-row-per-state** (one `DRAFT`, one `PUBLISHED`), not a
per-save history — a richer version *timeline* (an N-snapshot history table)
remains a separate, larger task and is deliberately not folded in here.

### III.4 Scheduled publish/unpublish (SH15) — ✅ shipped

Shipped: two nullable instants on the post's `DRAFT` row
(`publishScheduledDate`/`unpublishScheduledDate`) set via `POST
/api/v2/post-unified/{id}/schedule`, and a `@Scheduled` sweep (`ShPostScheduler`,
`shio.schedule.*`) that fires each due transition through the existing
`publish`/`unpublish` path — reusing the §III.1 webhook emitter rather than
re-firing it. Each due post runs in its own transaction (one failure is skipped,
not fatal). This introduced the codebase's first `@EnableScheduling`. Detail in
[CHANGELOG.md](CHANGELOG.md) Block C; spec:
[specs/SH15-scheduled-publish.md](specs/SH15-scheduled-publish.md).

### III.5 `shio` CLI (SH16) — ✅ shipped

Shipped: the `@viglet/shio` CLI (`init`/`types`/`pull`/`push`/`dev`/`deploy`)
backed by a new **lossless, console-authenticated** post-type admin API
(`GET/PUT/DELETE /api/v2/post-type`). Design notes: (1) the CLI uses **two auth
models** — console Basic auth for the content model (`pull`/`push`, an admin act)
and a CDA token for delivery (`types`); (2) the admin API is **lossless** where
the SH11 CDA schema is deliberately lossy (it keeps `Tab`, `widgetSettings` and
nested Relator fields so the JSON round-trips), and is CSRF-exempt for the same
reason CDA writes are (stateless, no session cookie); (3) `init` delegates to
`create-shio-app`'s scaffolder rather than duplicating it. Detail in
[CHANGELOG.md](CHANGELOG.md) Block C; spec:
[specs/SH16-shio-cli.md](specs/SH16-shio-cli.md).

Original direction:
`init` (scaffold), `dev` (local proxy + type watch), `pull`/`push` (post-types as
code, GitOps-friendly), `deploy`. Mirrors the `turing` CLI direction in the
ecosystem.

### III.6 Audit / history API + viewer (SH51)

The `ShHistory` entity is already persisted on writes but is **never read back** —
SH14 only diffs the DRAFT against the PUBLISHED snapshot, it does not expose the
audit trail. Close the gap: a read endpoint for who/what/when + a console activity
view. Foundation already exists (the entity), so this is wiring, not new modeling.

### III.7 Content full-text search (SH52)

A real gap: `ShSite.searchablePostTypes` (a CLOB) and the **Search/Semantic
widgets** exist in the model, but there is no search service and the CDA `/query`
endpoint is filter-only (`siteId`/`folderId`/`postType`) — not full-text. SH33
(MCP read tools) already assumes a search primitive. Decide the engine up front:
in-DB vs the ecosystem's Turing (which is exactly the search/AI component) — the
latter keeps the Java/Spring sinergy (§0.2).

### III.8 Content i18n / locales (SH53)

`ShLocale` exists in the model but is **unused** by `ShPostUnified` — a post is
single-language. Add a locale axis to the content (locale field / translation
linkage) and a `locale` parameter on CDA reads, so a headless front can request
`post` per language. Table-stakes for the markets Contentful/Strapi target.

### III.9 Content-level ACL / permissions (SH54)

`ShUser`/`ShGroup`/`ShRole` have full CRUD, but **nothing ties them to content** —
there are no folder/post ACLs and no `@PreAuthorize` on the content controllers, so
any authenticated user can reach all content through the unified API. Add an ACL
model on folders/posts + enforcement at the `ShCdaService`/console boundary.

### III.10 Trash / soft-delete + restore (SH55)

Deletes are hard today. A recycle bin (soft-delete flag + restore + purge) is a
table-stakes safety net for an editorial CMS; it also composes with the SH14
restore work.

---

## IV. Optional server-side JS rendering (GraalJS)

> Only for teams that want server-rendered pages **inside Shio** authored in JS
> instead of Thymeleaf. The headless path (§II) is primary; this is a fallback for
> the "I don't want a separate Next.js app" case. **Not Groovy** (§0.4).

### IV.1 GraalJS template-engine spike (SH17)

Time-boxed 2–3 day spike: `org.graalvm.polyglot` JS as a Spring `ViewResolver`
replacing Thymeleaf for site rendering. Decide **before** building: sandboxing
(untrusted template authors), per-request context binding, hot-reload story, and
throughput vs Thymeleaf. Default assumption: **don't build it** unless a named
requirement beats the headless default.

### IV.2 `.js` page templates with content-binding API (SH18)

If the spike clears the bar: expose `post`, `site`, `query()`, `children()` to the
JS template scope; authors write pages in JS, the JVM renders server-side. Built
only on a proven SH17 result.

---

## V. Legacy retirement

> Shio carries a legacy AngularJS UI (`shio-app/src/main/resources/ui/`) plus
> Thymeleaf templates. The replacement console is `shio-react`.

### V.1 Finish AngularJS → React console migration (SH19)

Continue migrating console pages to `shio-react` (Vite + shadcn/ui + TS), following
the Turing-React patterns documented in `agents.md`. This is the precondition for
deleting the legacy tree. See project memory `project_shio_react_migration.md`.

The legacy-only console surface that still blocks SH21 is decomposed so each piece
ships on its own:

- **SH56 — Auth Providers console page** ✅ (shipped). The `/api/v2/provider/auth`
  API already existed; this migrated the `config/provider/auth` screens (list + edit
  with a vendor select and a dynamic config-properties editor).
- **SH57 — Exchange Providers (v2 API + page)**. The `config/provider/exchange`
  screens have no v2 backend — add `ShExchangeProviderAPI` mirroring the auth one,
  then a console page on the SH56 pattern.
- **SH58 — Email/SMTP config (v2 API + page)**. Migrate `config/email`; needs a v2
  settings endpoint over `ShConfigVar`.
- **SH59 — Search config page** (deps SH52). Migrate `config/search`; depends on the
  content full-text search backend.
- **SH60 — Legacy commerce/recaptcha widgets**. Decide port-or-deprecate for the
  AngularJS `payment` + `recaptcha` widgets before they block SH21.
- **SH61 — Static-file manager page** (deps SH38). Migrate `staticFile`; blocked on
  the upload/serve subsystem the current backend lacks.

### V.2 Deprecate Thymeleaf site rendering (SH20)

Once Block B is the documented delivery path (or GraalJS §IV ships), mark the
server-side Thymeleaf site templates `@Deprecated` with a migration note, then
remove.

### V.3 Delete `resources/ui/` (SH21)

Terminal step after SH19 — reclaim the AngularJS frontend tree.

> The next three are **console-completion gaps** surfaced by an audit of
> `shio-react`: backend (or service) exists, the console surface doesn't. They
> belong to the same "reach React-console parity" effort as SH19.

### V.4 Webhooks console UI (SH48)

SH12 shipped the full `ShSiteWebhook` CRUD API + delivery, but there is **no
console page** to create/edit/delete per-site webhooks — they can only be managed
by hitting the API directly. A standard admin CRUD page (the pattern already used
for tokens/tenants) closes it.

### V.5 Console dashboard / home (SH49)

`home.page.tsx` is a placeholder ("Welcome to Viglet Shio CMS"). Meanwhile
`system-info.service.ts` is built but **consumed by no page**. Turn the home route
into a real dashboard (content counts, recent activity once SH51 lands, quick
actions) using that service.

### V.6 Complete console i18n coverage (SH50)

The i18n framework (en/pt) exists, but several pages bypass it with hardcoded
English — `site.list.page.tsx` ("Tasks", "New", "Find a site…", action labels),
`object.children.page.tsx` ("New Folder", "Delete", "Filter items…"), and
placeholders in `post-type.edit.page.tsx`. Move them to keys in both locales
(per agents.md: always add `en` + `pt` together).

---

## VI. Market positioning / bold bets

> Differentiators to pursue once the headless foundation (Blocks A–C) is solid.

### VI.1 Multi-tenancy / SaaS (SH22)

One shared JVM, many isolated tenants. **Core DB isolation shipped** (see
[CHANGELOG.md](CHANGELOG.md) Block F): Hibernate `@TenantId` extended across the
active content tree — the JOINED `ShObject` root (covering `ShSite`/`ShFolder`/
`ShPostType`), `ShPostUnified`, and `ShReference` — with the `sh_post_unified`
`TENANT_ID` migration + backfill, and `ShTenantIsolationIT` proving tenant A's
rows are invisible to tenant B. The identity model is the existing
`workspace_id`-claim/username resolution (`ShTenantFilter`).

**Follow-ups shipped** (see [CHANGELOG.md](CHANGELOG.md) Block F): a tenant
**catalog** (`ShTenant` + admin CRUD on `ShTenantAPI`, beyond the read-only
`/current`), **delivery-path token→tenant resolution** (`ShApiTokenFilter` +
`ShTenantFilter`, plus the `api_token` Liquibase drift fix it surfaced),
**per-tenant post-type provisioning** (`ShTenantProvisioningService`),
**tenant-keyed CDA cache** (`Vary: Key`), and a **console Tenants page**.

**Follow-ups still open** (decomposed into tasks):

- **SH44 — Superadmin impersonation**: let a privileged operator act as / switch
  into any tenant from the console (audited), beyond the principal-derived
  `ShTenantContext` resolution.
- **SH45 — Tenant quotas & billing**: per-tenant usage metering (content count,
  API calls, storage) + quota enforcement + billing-export hooks.
- **SH46 — Nav/feature gating by tenant**: show/hide console nav + features by
  `multiTenant`/role so single-tenant installs don't surface SaaS-only UI.
- **SH47 — Per-tenant file storage**: isolate `file_source` per tenant. Blocked
  on the upload/serve subsystem (SH38) — that subsystem doesn't exist in the
  current backend, so there's nothing to isolate yet.

### VI.2 Starter marketplace (SH23 epic)

Curated `create-shio-app` templates (blog, docs site, e-commerce front,
landing-page kit) the community can publish — accelerates adoption and showcases
the JS-first story. Decomposed into:

- **SH27 — Multi-template scaffolder**: teach `create-shio-app` to take
  `--template <name>` and read a per-template manifest (metadata: name,
  description, required SDK version, prompts/defaults). The foundation everything
  else hangs off.
- **SH28 — Community publishing convention**: a `shio-template-*` npm naming
  convention + manifest validation, so a third party can publish a template
  without touching the core repo.
- **SH29 — Template registry + CLI browse**: a curated index (repo JSON or npm
  scope) wired into the `shio` CLI (`shio init --template`, browse/list).
- **SH30 — Curated official templates**: the first-party set — blog, docs site,
  e-commerce front, landing-page kit.
- **SH31 — Template listing page**: a public catalog page on the docs/site.

### VI.3 AI authoring via Turing / MCP (SH24 epic)

Expose Shio content + authoring as MCP tools so Claude/agents (and Turing) draft,
restructure, and translate content. This is exactly where the Java/Spring
ecosystem sinergy pays off — Turing already speaks this language. Decomposed into:

- **SH32 — MCP server skeleton**: a Spring-hosted MCP server + transport, with
  auth reusing SH2 token scopes. The host every tool plugs into.
- **SH33 — Read tools**: list sites/folders/posts, get a post, search — all over
  the CDA so they inherit SH2 scoping.
- **SH34 — Schema discovery tool**: post-type/schema introspection reusing the
  SH11 codegen surface, so the agent knows the shape of `attrs`.
- **SH35 — Authoring/write tools**: create/update/publish over the SH10 write
  surface (draft-targeted, `ROLE_CDA_WRITE`).
- **SH36 — High-level authoring ops**: composite draft / restructure / translate
  operations built on the read+write primitives.
- **SH37 — Turing integration + tenant-awareness**: wire into the ecosystem and
  scope every tool per SH22 tenant.

### VI.4 Image/asset transformation API (SH25 epic)

On-the-fly resize/format/crop on the file-source delivery path (Tika + jai-imageio
are already dependencies). The JS front requests `image.jpg?w=800&format=webp`;
results are cached. Removes the need for an external image CDN. Decomposed into:

- **SH38 — Prereq: file_source upload/serve subsystem** ⚠️: the file upload/serve
  backend doesn't exist in the current code (only a legacy security matcher — see
  the SH22 "N/A" note). There's nothing to transform until this is
  (re)implemented, so it blocks the rest of VI.4.
- **SH39 — Query-param interception**: parse `?w=&h=&format=&crop=` on the
  delivery path and route to the transform pipeline.
- **SH40 — Transform pipeline**: resize/crop via imageio.
- **SH41 — Format conversion**: webp/avif/jpeg output negotiation.
- **SH42 — Result cache**: disk/memory keyed by source + params, with ETag,
  tenant-aware (SH22).
- **SH43 — Limits & security**: max dimensions, allowed formats, signed URLs to
  prevent transform-bombing.
