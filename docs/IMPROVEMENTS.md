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

### III.4 Scheduled publish/unpublish (SH15)

`publishAt`/`unpublishAt` on a post + a sweep that fires the transition and the
webhook (§III.1).

### III.5 `shio` CLI (SH16)

`init` (scaffold), `dev` (local proxy + type watch), `pull`/`push` (post-types as
code, GitOps-friendly), `deploy`. Mirrors the `turing` CLI direction in the
ecosystem.

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

### V.2 Deprecate Thymeleaf site rendering (SH20)

Once Block B is the documented delivery path (or GraalJS §IV ships), mark the
server-side Thymeleaf site templates `@Deprecated` with a migration note, then
remove.

### V.3 Delete `resources/ui/` (SH21)

Terminal step after SH19 — reclaim the AngularJS frontend tree.

---

## VI. Market positioning / bold bets

> Differentiators to pursue once the headless foundation (Blocks A–C) is solid.

### VI.1 Multi-tenancy / SaaS (SH22)

One shared JVM, many isolated tenants. `ShTenantAPI` is a seed; the design can
mirror Turing's multi-tenancy block (shared `@TenantId`, tenant-scoped cache/
storage/keys, isolation IT suite). Unlocks a hosted Shio Cloud.

### VI.2 Starter marketplace (SH23)

Curated `create-shio-app` templates (blog, docs site, e-commerce front,
landing-page kit) the community can publish — accelerates adoption and showcases
the JS-first story.

### VI.3 AI authoring via Turing / MCP (SH24)

Expose Shio content + authoring as MCP tools so Claude/agents (and Turing) draft,
restructure, and translate content. This is exactly where the Java/Spring
ecosystem sinergy pays off — Turing already speaks this language.

### VI.4 Image/asset transformation API (SH25)

On-the-fly resize/format/crop on the file-source delivery path (Tika + jai-imageio
are already dependencies). The JS front requests `image.jpg?w=800&format=webp`;
results are cached. Removes the need for an external image CDN.
