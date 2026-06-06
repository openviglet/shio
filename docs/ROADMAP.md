# Shio — Roadmap (active backlog)

> **Single source of truth for task status.** Flat, one line per task.
> Only **unshipped** work lives here (📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress).
> Shipped work is moved to [CHANGELOG.md](CHANGELOG.md); design rationale (the
> *what/why* per task) lives in [IMPROVEMENTS.md](IMPROVEMENTS.md).
>
> **North star (see IMPROVEMENTS.md §0):** make Shio a **headless / composable
> CMS** whose page-authoring experience is **100% JavaScript/TypeScript** — the
> Java/Spring backend stays as the content engine, and nobody who builds a site
> ever has to touch Java. We do **not** rewrite the backend in Node/Go/PHP
> (§0.3 explains why).
>
> **How to pick work:** lowest-numbered task in a block whose `deps` are all
> shipped. The `→` pointer is the section in IMPROVEMENTS.md with the full design.

| Symbol | Meaning |
|---|---|
| 📋 | Designed but not started |
| 💭 | Idea worth exploring; needs design |
| ⏳ | Partial — direction is right, more work remains |
| 🛠 | In progress |

---

## Block A — Headless foundation (Content Delivery API as a first-class product)

> The CDA seed exists (`@viglet/shio-react-sdk` consumes it; `/api/v2/discovery`,
> `/api/v2/post-unified`, API-token auth are in place). This block turns that
> seed into a stable, documented, public-grade delivery surface. → §I

- ✅ **SH1** — shipped → [CHANGELOG.md](CHANGELOG.md) Block A. Stable `/api/v2/cda/**` read namespace + frozen DTO contract. Spec: [specs/SH1-cda-read-contract.md](specs/SH1-cda-read-contract.md).
- ✅ **SH2** — shipped → [CHANGELOG.md](CHANGELOG.md) Block A. API-token `scope` (READ/PREVIEW/WRITE) + optional per-site allow-list, enforced in `ShApiTokenFilter` (namespace-confined) + at the `ShCdaService` boundary. Spec: [specs/SH2-token-scopes.md](specs/SH2-token-scopes.md).
- ✅ **SH3** — shipped → [CHANGELOG.md](CHANGELOG.md) Block A. `Cache-Control` + body-hash `ETag` (304 on `If-None-Match`) on CDA reads + an in-memory per-token bucket limiter (429 + `Retry-After`), tunable via `shio.cda.*`. Spec: [specs/SH3-cda-rate-limit-cache.md](specs/SH3-cda-rate-limit-cache.md).
- ✅ **SH4** — shipped → [CHANGELOG.md](CHANGELOG.md) Block A. Programmatic `ShGraphQLSchema` generated from content post-types (`ShContent` interface + one object type per post-type with a field per attr); `Query` (`post`/`postByUrl`/`posts`) delegates to `ShCdaService` so it inherits SH2 scoping; `/graphql` put behind `ROLE_CDA`. Spec: [specs/SH4-cda-graphql-schema.md](specs/SH4-cda-graphql-schema.md).
- ✅ **SH5** — shipped → [CHANGELOG.md](CHANGELOG.md) Block A. Tokens carry an `env` (`PROD`/`PREVIEW`); a `PROD` key reads `PUBLISHED` only, a `PREVIEW` key reads draft-preferred (prefers the `DRAFT` row, falls back to `PUBLISHED`) — resolved centrally in `ShCdaService` so REST + GraphQL inherit it. Spec: [specs/SH5-per-environment-keys.md](specs/SH5-per-environment-keys.md).

## Block B — JavaScript-for-pages DX (the core objective)

> This is the block that delivers the stated goal: a JS/TS developer builds a
> full site against Shio without ever seeing Java. The React SDK exists with
> hooks (`use-shio-post`, `use-shio-children`, `use-shio-query`, `use-shio-sites`);
> we complete it, add a framework-agnostic core, and ship a real starter. → §II

- ✅ **SH6** — shipped → [CHANGELOG.md](CHANGELOG.md) Block B. Standalone zero-dep `@viglet/shio-client` consuming the CDA. (Rebasing `shio-react-sdk` onto it is part of SH7.)
- ✅ **SH7** — shipped → [CHANGELOG.md](CHANGELOG.md) Block B. `shio-react-sdk` rebased onto `@viglet/shio-client`; new hooks + `ShioRichText`/`ShioImage`/`ShioRelator`/`ShioField`.
- ✅ **SH8** — shipped → [CHANGELOG.md](CHANGELOG.md) Block B. `create-shio-app` scaffolds a Next.js App-Router site wired to the CDA (`[...slug]` routing, SSG/ISR).
- ✅ **SH9** — shipped → [CHANGELOG.md](CHANGELOG.md) Block B. SSG/ISR/SSR recipes + on-demand revalidation on the Next.js side: `/api/revalidate` (HMAC-verified webhook → `revalidatePath`/`revalidateTag`) + `/api/preview` (Draft Mode SSR on SH5 PREVIEW keys). Recipe: [recipes/nextjs-rendering.md](recipes/nextjs-rendering.md). The webhook **emitter** is SH12.
- ✅ **SH10** — shipped → [CHANGELOG.md](CHANGELOG.md) Block B. CDA write surface (`/api/v2/cda` `PATCH`/`publish`/`editable`, gated by `ROLE_CDA_WRITE`, draft-targeted) + the React console editor shell that drives the `shio-editor.js` bridge over `postMessage` and saves back with a WRITE token. Spec: [specs/SH10-universal-editor-bridge.md](specs/SH10-universal-editor-bridge.md).
- ✅ **SH11** — shipped → [CHANGELOG.md](CHANGELOG.md) Block B. Public CDA post-type schema (`GET /api/v2/cda/post-type[/{name}]`, recursive into Relator sub-fields, array-aware) + a pure `generateShioTypes` in `@viglet/shio-client` and a `shio-types` CLI that turns it into a `.d.ts`, so page code gets autocomplete on `post.attrs.*`; wired into `create-shio-app` (`npm run shio:types`). Spec: [specs/SH11-type-generation.md](specs/SH11-type-generation.md).

## Block C — Composable platform primitives

> The table-stakes features the headless market (Contentful/Strapi/Sanity)
> assumes. Versioning is partly there (`/api/v2/post-unified/{id}/versions`). → §III

- ✅ **SH12** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. Per-site outbound webhooks (`ShSiteWebhook` + `/api/v2/site-webhook` CRUD) fired on publish/unpublish/delete from the unified write path via a Spring `@TransactionalEventListener(AFTER_COMMIT)`; HMAC-SHA256 signed (matching the SH9 receiver), retried best-effort off the request thread. Spec: [specs/SH12-outbound-webhooks.md](specs/SH12-outbound-webhooks.md).
- ✅ **SH13** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. Short-lived (expiring) preview tokens minted per-post by an authenticated console user (`POST /api/v2/preview-token`), confined to the post's site (SH2) and `PREVIEW`-env (SH5 draft-preferred reads), enforced by an expiry check in `ShApiTokenFilter`; a **Preview Draft** console button opens the site front-end's Draft-Mode route with the token, and `create-shio-app`'s `/api/preview` consumes it via an httpOnly cookie. Spec: [specs/SH13-draft-preview-mode.md](specs/SH13-draft-preview-mode.md).
- ✅ **SH14** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. Console **History** dialog on the post editor diffs the unpublished `DRAFT` against the live `PUBLISHED` snapshot (field-level, schema-driven, "only changes" toggle) and offers **Restore** (revert draft to published) backed by a new `POST /api/v2/post-unified/{id}/restore`. Spec: [specs/SH14-versioning-ui.md](specs/SH14-versioning-ui.md).
- ✅ **SH15** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. `publishScheduledDate`/`unpublishScheduledDate` on the post's `DRAFT` row + a `@Scheduled` sweep (`ShPostScheduler`, guarded by `shio.schedule.enabled`) that fires the due transition through the existing `publish`/`unpublish` path (each in its own tx) — so the SH12 webhook fires identically to a manual transition. `POST /api/v2/post-unified/{id}/schedule` sets/clears the timestamps; a **Schedule** console dialog drives it. Introduces `@EnableScheduling`. Spec: [specs/SH15-scheduled-publish.md](specs/SH15-scheduled-publish.md).
- ✅ **SH16** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. `@viglet/shio` **CLI** (`init`/`types`/`pull`/`push`/`dev`/`deploy`) **+** a new lossless, console-authenticated post-type admin API (`GET/PUT/DELETE /api/v2/post-type`) so the content model round-trips as code (`pull` → edit JSON → `push --dry-run`/`--prune`). `init` reuses `create-shio-app`'s scaffolder; `types` wraps the SH11 codegen. Spec: [specs/SH16-shio-cli.md](specs/SH16-shio-cli.md).
- ✅ **SH26** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. Cross-DB schema-drift guard: Testcontainers H2/MariaDB/PostgreSQL ITs boot the full context against the real changelog set; fixed the cross-DB drift they surfaced (inline `ENUM`→`VARCHAR`, `QUOTE_ALL_OBJECTS` identifier quoting) + the silent snake_case `ShTenantBackfillRunner.TENANT_TABLES` no-op. Spec: [specs/SH26-cross-db-schema-guard.md](specs/SH26-cross-db-schema-guard.md).

## Block D — Optional server-side JS rendering (GraalJS)

> For teams that want server-rendered pages **inside Shio** (not a separate
> Next.js app) but authored in JavaScript instead of Thymeleaf. Optional — the
> headless path (Block B) is the primary recommendation. **Not Groovy** (§0.4). → §IV

- 💭 **SH17** (deps: —) **GraalJS template-engine spike** — 2–3 day spike: evaluate `org.graalvm.polyglot` JS as a Spring `ViewResolver` replacing Thymeleaf for site rendering. Decide before building: sandboxing, per-request context binding, hot-reload, perf vs Thymeleaf. → §IV.1
- 💭 **SH18** (deps: SH17) **`.js` page templates with a content-binding API** — expose `post`, `site`, `query()`, `children()` to the JS template scope; author writes pages in JS/JSX-lite, server renders. Only build if the spike (SH17) beats the headless default for a named requirement. → §IV.2

## Block E — Legacy retirement

> Shio carries a legacy AngularJS UI (`src/main/resources/ui/`) + Thymeleaf
> templates. The new console is `shio-react`. → §V

- ⏳ **SH19** (deps: —) **Finish AngularJS → React console migration** — continue moving console pages to `shio-react` (see [project memory]); the migration is the precondition for deleting `resources/ui/`. → §V.1
- 💭 **SH20** (deps: SH19, SH8) **Deprecate Thymeleaf site rendering** — once Block B is the documented delivery path (or SH18 ships), mark server-side Thymeleaf site templates `@Deprecated`, then delete. → §V.2
- 💭 **SH21** (deps: SH19) **Delete `resources/ui/` (AngularJS)** — terminal step after SH19; reclaim the legacy frontend tree. → §V.3

## Block F — Market positioning / bold bets

> Differentiators once the headless foundation is solid. → §VI

- 💭 **SH22** (deps: SH2) **Multi-tenancy / SaaS** — one shared JVM, many isolated tenants (mirror Turing's Block J approach: `ShTenant` exists as a seed — `api/tenant/ShTenantAPI.java`). → §VI.1
- 💭 **SH23** (deps: SH8) **Starter marketplace** — curated `create-shio-app` templates (blog, docs, e-commerce front) the community can publish. → §VI.2
- 💭 **SH24** (deps: SH4) **AI authoring via Turing/MCP** — expose Shio content + authoring as MCP tools so Claude/agents (and Turing) can draft, restructure, and translate content. Reuses the ecosystem's Java/Spring sinergy. → §VI.3
- 💭 **SH25** (deps: SH6) **Image/asset transformation API** — on-the-fly resize/format/crop on the file-source delivery path (Tika + imageio already present), so the JS front requests `?w=800&format=webp`. → §VI.4

---

## Non-goals (do NOT add as tasks)

Binding constraints — see [IMPROVEMENTS.md](IMPROVEMENTS.md) §0.3–§0.4 for the
full rationale. Summary:

- **Do not rewrite the backend in Node/Next/Go/PHP.** Java/Spring Boot is the
  content engine and the cola with Turing + Dumont; a rewrite is high-cost,
  high-risk, zero user-visible value (§0.3).
- **Groovy is not the answer to "JS for pages."** The audience is JS developers;
  the answer is the headless JS SDK (Block B) and, optionally, GraalJS (Block D)
  — not Groovy (§0.4).
- Don't fork the API into a third surface — the console API and the CDA (SH1)
  are the only two read/write contracts.
- Don't couple the JS SDK to React — the framework-agnostic core (SH6) is the
  base; React is one consumer.
