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

**SH62 — Post-types as typed TS code** (epic; a typed TypeScript DSL for the content model that **compiles to the canonical SH16 post-type JSON** — the authoring inverse of SH11's model→types codegen. The middle path: **JSON stays the interchange/push format** the admin API consumes, the DSL is an *optional* authoring layer on top of it, and two guards — a CI drift check + a console "managed by code" lock — keep the model single-sourced instead of split-brained between the DSL and the GUI editor) → §II.7 · spec: [specs/SH62-post-types-dsl.md](specs/SH62-post-types-dsl.md):
- 💭 **SH63** (deps: SH16) **DSL core (`@viglet/shio-model`)** — a pure, dependency-free typed builder (`postType()`, `text()`, `comboBox()`, `relator()`, `tab()`, … one factory per the 14 widgets) whose `widgetSettings` are **per-widget discriminated unions** (choices as an object, not the `"id:label\n…"` string; typed File/ContentSelect/MultiSelect/Relator/Search settings), emitting the normalized `ShPostTypeDef` via the existing `normalizeDef`/`serializeDef`. tsserver gives autocomplete + compile-time validation with zero custom extension. → §II.7
- 💭 **SH64** (deps: SH63) **CLI compile step** — `shio push`/`pull` recognize `*.ts`/`*.mjs` DSL sources alongside `*.json` in `shio/post-types/` and compile them to canonical JSON before the diff (so the wire format the admin API sees is unchanged); add `shio build` to emit the JSON artifacts. DSL is source-of-truth, JSON is the build output. → §II.7
- 💭 **SH65** (deps: SH64) **Drift guard for CI** — `shio push --check`: compile the DSL, diff against the live model, exit non-zero on drift (no writes). Lets a pipeline assert "git == server" so code stays the source of truth; complements the existing `--dry-run`/`--prune`. → §II.7
- 💭 **SH66** (deps: SH63) **Console "managed by code" lock** — a `managed` flag set on `push` + a read-only badge/lock in the React post-type editor for code-owned types, resolving the DSL↔GUI split-brain explicitly (the GUI shows but won't silently overwrite a code-owned model). System types stay backend-owned; the DSL owns only the custom subset. → §II.7

## Block C — Composable platform primitives

> The table-stakes features the headless market (Contentful/Strapi/Sanity)
> assumes. Versioning is partly there (`/api/v2/post-unified/{id}/versions`). → §III

- ✅ **SH12** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. Per-site outbound webhooks (`ShSiteWebhook` + `/api/v2/site-webhook` CRUD) fired on publish/unpublish/delete from the unified write path via a Spring `@TransactionalEventListener(AFTER_COMMIT)`; HMAC-SHA256 signed (matching the SH9 receiver), retried best-effort off the request thread. Spec: [specs/SH12-outbound-webhooks.md](specs/SH12-outbound-webhooks.md).
- ✅ **SH13** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. Short-lived (expiring) preview tokens minted per-post by an authenticated console user (`POST /api/v2/preview-token`), confined to the post's site (SH2) and `PREVIEW`-env (SH5 draft-preferred reads), enforced by an expiry check in `ShApiTokenFilter`; a **Preview Draft** console button opens the site front-end's Draft-Mode route with the token, and `create-shio-app`'s `/api/preview` consumes it via an httpOnly cookie. Spec: [specs/SH13-draft-preview-mode.md](specs/SH13-draft-preview-mode.md).
- ✅ **SH14** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. Console **History** dialog on the post editor diffs the unpublished `DRAFT` against the live `PUBLISHED` snapshot (field-level, schema-driven, "only changes" toggle) and offers **Restore** (revert draft to published) backed by a new `POST /api/v2/post-unified/{id}/restore`. Spec: [specs/SH14-versioning-ui.md](specs/SH14-versioning-ui.md).
- ✅ **SH15** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. `publishScheduledDate`/`unpublishScheduledDate` on the post's `DRAFT` row + a `@Scheduled` sweep (`ShPostScheduler`, guarded by `shio.schedule.enabled`) that fires the due transition through the existing `publish`/`unpublish` path (each in its own tx) — so the SH12 webhook fires identically to a manual transition. `POST /api/v2/post-unified/{id}/schedule` sets/clears the timestamps; a **Schedule** console dialog drives it. Introduces `@EnableScheduling`. Spec: [specs/SH15-scheduled-publish.md](specs/SH15-scheduled-publish.md).
- ✅ **SH16** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. `@viglet/shio` **CLI** (`init`/`types`/`pull`/`push`/`dev`/`deploy`) **+** a new lossless, console-authenticated post-type admin API (`GET/PUT/DELETE /api/v2/post-type`) so the content model round-trips as code (`pull` → edit JSON → `push --dry-run`/`--prune`). `init` reuses `create-shio-app`'s scaffolder; `types` wraps the SH11 codegen. Spec: [specs/SH16-shio-cli.md](specs/SH16-shio-cli.md).
- ✅ **SH26** — shipped → [CHANGELOG.md](CHANGELOG.md) Block C. Cross-DB schema-drift guard: Testcontainers H2/MariaDB/PostgreSQL ITs boot the full context against the real changelog set; fixed the cross-DB drift they surfaced (inline `ENUM`→`VARCHAR`, `QUOTE_ALL_OBJECTS` identifier quoting) + the silent snake_case `ShTenantBackfillRunner.TENANT_TABLES` no-op. Spec: [specs/SH26-cross-db-schema-guard.md](specs/SH26-cross-db-schema-guard.md).
- 💭 **SH51** (deps: —) **Audit / history API + viewer** — the `ShHistory` entity is persisted but **never read back** (SH14 only diffs DRAFT↔PUBLISHED); expose who/what/when via a read endpoint + a console activity view. → §III.6
- 💭 **SH52** (deps: —) **Content full-text search** — a search service + full-text CDA endpoint consuming the existing `ShSite.searchablePostTypes`; wires up the Search/Semantic widgets that exist in the model. SH33 (MCP read tools) already assumes it. → §III.7
- 💭 **SH53** (deps: —) **Content i18n / locales** — multi-language posts: put the unused `ShLocale` to work, add a locale axis to `ShPostUnified` + a `locale` param on CDA reads. → §III.8
- 💭 **SH54** (deps: —) **Content-level ACL / permissions** — folder/post access control tying the existing `ShUser`/`ShGroup`/`ShRole` to content; enforced at the `ShCdaService`/console boundary (today any authenticated user reaches all content). → §III.9
- 💭 **SH55** (deps: —) **Trash / soft-delete + restore** — a recycle bin instead of hard delete on posts/folders. → §III.10

## Block D — Optional server-side JS rendering (GraalJS)

> For teams that want server-rendered pages **inside Shio** (not a separate
> Next.js app) but authored in JavaScript instead of Thymeleaf. Optional — the
> headless path (Block B) is the primary recommendation. **Not Groovy** (§0.4). → §IV

- 💭 **SH17** (deps: —) **GraalJS template-engine spike** — 2–3 day spike: evaluate `org.graalvm.polyglot` JS as a Spring `ViewResolver` replacing Thymeleaf for site rendering. Decide before building: sandboxing, per-request context binding, hot-reload, perf vs Thymeleaf. → §IV.1
- 💭 **SH18** (deps: SH17) **`.js` page templates with a content-binding API** — expose `post`, `site`, `query()`, `children()` to the JS template scope; author writes pages in JS/JSX-lite, server renders. Only build if the spike (SH17) beats the headless default for a named requirement. → §IV.2

## Block E — Legacy retirement

> Shio carries a legacy AngularJS UI (`src/main/resources/ui/`) + Thymeleaf
> templates. The new console is `shio-react`. → §V

- ⏳ **SH19** (deps: —) **Finish AngularJS → React console migration** — continue moving console pages to `shio-react` (see [project memory]); the migration is the precondition for deleting `resources/ui/`. The remaining legacy-only surface (the `config/` section + static files + legacy commerce widgets) is decomposed into SH56–SH61 below so each ships independently. → §V.1
  - ✅ **SH56** (deps: SH19) **Auth Providers console page** — list + edit for `/api/v2/provider/auth` (vendor select, enabled toggle, dynamic config-properties editor), wired into the Administration sidebar. The v2 API already existed; this migrates the legacy `config/provider/auth` AngularJS screens. Shipped → [CHANGELOG.md](CHANGELOG.md) Block E. → §V.1
  - 💭 **SH57** (deps: SH19) **Exchange Providers — v2 API + console page** — the legacy `config/provider/exchange` screens have **no v2 backend** (only `/api/v2/provider/auth` exists); add `ShExchangeProviderAPI` mirroring the auth provider, then a console page reusing the SH56 pattern. (Gains real utility once the SH67 exchange engine lands — providers are remote sources to import/export against.) → §V.1
  - 💭 **SH58** (deps: SH19) **Email/SMTP config — v2 API + console page** — migrate legacy `config/email`; needs a v2 settings endpoint over `ShConfigVar` then a console form. → §V.1
  - 💭 **SH59** (deps: SH19, SH52) **Search config console page** — migrate legacy `config/search`; depends on the content full-text search backend (SH52) since there is no v2 search-config endpoint today. → §V.1
  - 💭 **SH60** (deps: SH19) **Legacy commerce/recaptcha widgets — port or deprecate** — the AngularJS `payment` (payment-slip/rede-card/shopline) and `recaptcha` widgets have no React equivalent; decide whether they belong in the headless-first product before porting, else mark for removal with SH21. → §V.1
  - 💭 **SH61** (deps: SH19, SH38) **Static-file manager console page** — migrate legacy `staticFile` upload/select screens; the upload/serve subsystem (SH38) now ships, so this is unblocked (the `File` widget already uploads via `POST /v2/staticfile/upload`; this adds a dedicated browse/manage page). → §V.1
- 💭 **SH20** (deps: SH19, SH8) **Deprecate Thymeleaf site rendering** — once Block B is the documented delivery path (or SH18 ships), mark server-side Thymeleaf site templates `@Deprecated`, then delete. → §V.2
- 💭 **SH21** (deps: SH19) **Delete `resources/ui/` (AngularJS)** — terminal step after SH19; reclaim the legacy frontend tree. → §V.3
- 💭 **SH48** (deps: SH12, SH19) **Webhooks console UI** — admin page for the shipped `ShSiteWebhook` CRUD API (SH12). The backend exists; there is **no console page** to manage per-site webhooks. → §V.4
- 💭 **SH49** (deps: SH19) **Console dashboard / home** — replace the placeholder `home.page.tsx` ("Welcome…") with content stats + quick actions, consuming the already-built-but-unused `system-info.service.ts`. → §V.5
- 💭 **SH50** (deps: SH19) **Complete console i18n coverage** — replace hardcoded English (`site.list`, `object.children`, `post-type.edit`: "New", "Delete", "Find a site…", "Tasks"…) with i18n keys in both `en` + `pt`. → §V.6

## Block F — Market positioning / bold bets

> Differentiators once the headless foundation is solid. → §VI

- ✅ **SH22** (deps: SH2) **Multi-tenancy / SaaS** — one shared JVM, many isolated tenants. Shipped → [CHANGELOG.md](CHANGELOG.md) Block F: core DB isolation (`@TenantId` across the content tree incl. `sh_post_unified`, `ShTenantIsolationIT`) **+ follow-ups** — tenant catalog (`ShTenant` + admin CRUD), delivery-path token→tenant resolution (+ `api_token` Liquibase drift fix), per-tenant post-type provisioning, tenant-keyed CDA cache (`Vary: Key`), and a console Tenants page. **N/A:** per-tenant `file_source` storage (the file-upload/serve subsystem isn't implemented in the current backend). → §VI.1
  - 💭 **SH44** (deps: SH22) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited), beyond the principal-derived `ShTenantContext`. → §VI.1
  - 💭 **SH45** (deps: SH22) **Tenant quotas & billing** — per-tenant usage metering (content count, API calls, storage) + quota enforcement + billing-export hooks. → §VI.1
  - 💭 **SH46** (deps: SH22) **Nav/feature gating by tenant** — show/hide console nav + features by `multiTenant`/role so single-tenant installs don't see SaaS surfaces. → §VI.1
  - 💭 **SH47** (deps: SH22, SH38) **Per-tenant file storage** — isolate `file_source` per tenant; SH38 now ships, so this prefixes `tenantId` into the id-keyed `ShFileStorageService` layout (`file_source/{tenantId}/<postId>/...`) from `ShTenantContext`. → §VI.1

**SH23 — Starter marketplace** (epic; curated `create-shio-app` templates the community can publish) → §VI.2:
- 💭 **SH27** (deps: SH8) **Multi-template scaffolder** — `create-shio-app --template <name>` + a per-template manifest/metadata format. → §VI.2
- 💭 **SH28** (deps: SH27) **Community publishing convention** — `shio-template-*` npm naming + manifest validation so third parties can ship templates. → §VI.2
- 💭 **SH29** (deps: SH27, SH16) **Template registry + CLI browse** — curated index (repo JSON or npm scope) wired into `shio init --template`/browse. → §VI.2
- 💭 **SH30** (deps: SH27) **Curated official templates** — blog, docs site, e-commerce front, landing-page kit. → §VI.2
- 💭 **SH31** (deps: SH29) **Template listing page** — public catalog on the docs/site. → §VI.2

**SH24 — AI authoring via Turing/MCP** (epic; expose Shio content + authoring as MCP tools for Claude/agents/Turing) → §VI.3:
- 💭 **SH32** (deps: SH2) **MCP server skeleton** — Spring-hosted MCP server + transport + auth via SH2 token scopes. → §VI.3
- 💭 **SH33** (deps: SH32, SH1) **Read tools** — list sites/folders/posts, get, search, over the CDA. → §VI.3
- 💭 **SH34** (deps: SH32, SH11) **Schema discovery tool** — post-type/schema introspection reusing the SH11 codegen surface. → §VI.3
- 💭 **SH35** (deps: SH32, SH10) **Authoring/write tools** — create/update/publish over the SH10 write surface. → §VI.3
- 💭 **SH36** (deps: SH35) **High-level authoring ops** — draft / restructure / translate. → §VI.3
- 💭 **SH37** (deps: SH32, SH22) **Turing integration + tenant-awareness** — wire into the ecosystem; scope tools per tenant. → §VI.3

**SH25 — Image/asset transformation API** (epic; on-the-fly resize/format/crop on the file-source delivery path) → §VI.4:
- ✅ **SH38** — shipped → [CHANGELOG.md](CHANGELOG.md) Block F. file_source upload/serve subsystem rebuilt on the unified model (`ShFileStorageService` id-keyed storage + `ShStaticFileService`/`ShStaticFileAPI` upload/serve + `/file_source/**` resource handler + delete-cleanup listener). Unblocks SH39–43, SH47, SH61, and the exchange binary-asset walk.
- 💭 **SH39** (deps: SH38) **Query-param interception** — parse `?w=&h=&format=&crop=` on the delivery path. → §VI.4
- 💭 **SH40** (deps: SH39) **Transform pipeline** — resize/crop via imageio. → §VI.4
- 💭 **SH41** (deps: SH40) **Format conversion** — webp/avif/jpeg output. → §VI.4
- 💭 **SH42** (deps: SH40, SH22) **Result cache** — disk/memory keyed by source+params + ETag, tenant-aware. → §VI.4
- 💭 **SH43** (deps: SH40) **Limits & security** — max dimensions, allowed formats, signed URLs. → §VI.4

## Block G — Content portability (exchange: export / import / clone / templates)

> The nuclear refactor (commit `2d3eb353`) deleted the entire
> `com.viglet.shio.exchange` subsystem (`ShCloneExchange`, `ShSiteImport`,
> `ShFolderImport`, `ShPostExchange`, `ShSiteExport`…) along with the legacy split
> model it serialized. **Visible regression:** creating a site used to clone a
> `bootstrap-site` template (post-types + folders + sample pages + static files);
> the rebuilt `ShSiteWriteService.create()` now only persists the `ShSite` + an
> empty root folder. The console **Download** / **Import** actions in
> `site.list.page.tsx` still render but their endpoints (`GET /api/v2/site/{id}/export`,
> site import) 404. This block rebuilds the exchange — now on the **unified post
> model** (`ShPostUnified.data`, not the deleted `ShPost`/`ShPostDraft`). → §VII

**SH67 — Content exchange / portability** (epic; rebuild the deleted exchange subsystem on the unified model so sites export/import/clone as portable packages and new sites provision the full basic structure again) → §VII:
- ✅ **SH68** — shipped → [CHANGELOG.md](CHANGELOG.md) Block G. Exchange package format on the unified model (`com.viglet.shio.exchange.model` DTOs + `ShExchangePackageCodec`; post-types reuse SH16 `ShPostTypeDef`, post `data` verbatim as `JsonNode`).
- ✅ **SH69** — shipped → [CHANGELOG.md](CHANGELOG.md) Block G. Site export → zip (`ShSiteExportService`, `GET /api/v2/site/{id}/export` — the console Download action; static files deferred to SH38).
- ✅ **SH70** — shipped → [CHANGELOG.md](CHANGELOG.md) Block G. Site import / clone (`ShSiteImportService` + `POST /api/v2/import` + React `site.import.page.tsx`; fresh-UUID clone with `data` reference remap).
- ✅ **SH71** — shipped → [CHANGELOG.md](CHANGELOG.md) Block G. Default bootstrap-site template on site creation (`ShBootstrapSiteProvider` + `classpath:/import/bootstrap-site/export.json`; `ShSiteWriteService.create()` clones it with the user's metadata, plain-root-folder fallback) — **closes the original regression** (new sites were blank).
- ✅ **SH72** — shipped → [CHANGELOG.md](CHANGELOG.md) Block G. Tenant provisioning rebased onto the exchange engine — `ShTenantProvisioningService` now exports the default tenant (`buildTenantPackage`) and clones it into the new tenant via `ShSiteImportService.provisionInto` (full content: sites + folders + posts + post-types), idempotent (content only into an empty tenant; tenant-suffixed site names dodge the global unique-name index).

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
