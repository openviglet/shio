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

> ✅ **All tasks shipped (SH1–SH5)** → see [CHANGELOG.md](CHANGELOG.md) Block A.

## Block B — JavaScript-for-pages DX (the core objective)

> This is the block that delivers the stated goal: a JS/TS developer builds a
> full site against Shio without ever seeing Java. The React SDK exists with
> hooks (`use-shio-post`, `use-shio-children`, `use-shio-query`, `use-shio-sites`);
> we complete it, add a framework-agnostic core, and ship a real starter. → §II

> ✅ **Foundation shipped (SH6–SH11)** → see [CHANGELOG.md](CHANGELOG.md) Block B.

> ✅ **SH62 epic — Post-types as typed TS code (SH63–SH66) shipped** — DSL core (`@viglet/shio-model`) + CLI compile step (`shio push`/`build` compile `*.ts`/`*.mjs` DSL sources to canonical JSON) + CI drift guard (`shio push --check`) + console "managed by code" lock (read-only editor for code-owned types) → see [CHANGELOG.md](CHANGELOG.md) Block B.

## Block C — Composable platform primitives

> The table-stakes features the headless market (Contentful/Strapi/Sanity)
> assumes. Versioning is partly there (`/api/v2/post-unified/{id}/versions`). → §III

> ✅ **Shipped: SH12–SH16, SH26** → see [CHANGELOG.md](CHANGELOG.md) Block C.

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
  - ✅ **SH56** — shipped → [CHANGELOG.md](CHANGELOG.md) Block E (Auth Providers console page).
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

- ✅ **SH22** (deps: SH2) **Multi-tenancy / SaaS** — shipped → [CHANGELOG.md](CHANGELOG.md) Block F (core DB isolation + tenant catalog, delivery-path token→tenant resolution, per-tenant provisioning, tenant-keyed CDA cache, console Tenants page). Follow-ups remain:
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

> ✅ **SH25 — Image/asset transformation API** (epic) fully shipped (SH38–SH43) → see [CHANGELOG.md](CHANGELOG.md) Block F.

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

> ✅ **SH67 — Content exchange / portability** (epic) fully shipped (SH68–SH72) → see [CHANGELOG.md](CHANGELOG.md) Block G. The original "new sites are blank" regression is closed.

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
