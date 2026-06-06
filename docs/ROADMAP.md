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
- 📋 **SH2** (deps: SH1) **API-token scopes + per-site keys** — extend `ShApiToken` with `scope` (read/preview/write) and an optional `siteId` allow-list; `ShApiTokenFilter` enforces them so a public delivery key can read one site and nothing else. → §I.2
- 📋 **SH3** (deps: SH1) **CDA rate limiting + caching headers** — `Cache-Control`/`ETag` on CDA reads + a per-token bucket limiter, so a key can sit safely on the public internet behind a CDN. → §I.3
- 📋 **SH4** (deps: SH1) **Real GraphQL delivery schema** — replace the placeholder `schema.graphqls` content surface with a generated read schema over post-types (the programmatic `ShGraphQLSchema` already exists) so JS clients can query exactly the fields a page needs. → §I.4
- 💭 **SH5** (deps: SH2) **Per-environment keys (prod/preview)** — a key carries `env`; preview keys see drafts (ties into SH13), prod keys see only published. → §I.5

## Block B — JavaScript-for-pages DX (the core objective)

> This is the block that delivers the stated goal: a JS/TS developer builds a
> full site against Shio without ever seeing Java. The React SDK exists with
> hooks (`use-shio-post`, `use-shio-children`, `use-shio-query`, `use-shio-sites`);
> we complete it, add a framework-agnostic core, and ship a real starter. → §II

- ✅ **SH6** — shipped → [CHANGELOG.md](CHANGELOG.md) Block B. Standalone zero-dep `@viglet/shio-client` consuming the CDA. (Rebasing `shio-react-sdk` onto it is part of SH7.)
- 📋 **SH7** (deps: SH6) **Complete the React SDK surface** — add `useShioBreadcrumb`, `useShioSearch`, typed `ShioImage`/`ShioRichText`/`ShioRelator` render components, and a `<ShioField>` resolver mirroring the console's widget map. → §II.2
- 📋 **SH8** (deps: SH6) **`create-shio-app` — Next.js starter** — `npx create-shio-app` scaffolds an App-Router site wired to a Shio instance + token, with example pages, dynamic `[...slug]` routing from CDA paths, and typed content. The flagship "JS dev, zero Java" path. → §II.3
- 📋 **SH9** (deps: SH8, SH13) **SSG/ISR/SSR recipes + on-demand revalidation** — document the three rendering modes against the CDA; wire publish webhooks (SH12) to Next.js `revalidatePath`/`revalidateTag`. → §II.4
- ⏳ **SH10** (deps: SH1) **Mature the Universal Editor bridge** — `@viglet/shio-editor-cors` exists; harden the inline-edit handshake (field mapping, save-back through CDA write scope, draft preview) so any JS-rendered page becomes visually editable. → §II.5
- 💭 **SH11** (deps: SH6) **TypeScript type generation from post-types** — a `shio types` codegen step turns each post-type into a `.d.ts`, so page code gets autocomplete on `post.attrs.*`. → §II.6

## Block C — Composable platform primitives

> The table-stakes features the headless market (Contentful/Strapi/Sanity)
> assumes. Versioning is partly there (`/api/v2/post-unified/{id}/versions`). → §III

- 📋 **SH12** (deps: SH1) **Outbound webhooks on publish/unpublish/delete** — configurable per-site webhook targets with HMAC signing + retry, emitted from the unified publish path. The backbone for incremental static regeneration (SH9). → §III.1
- 📋 **SH13** (deps: SH2) **Draft / preview mode** — short-lived preview tokens that expose unpublished content via the CDA so the JS front can render a live preview before publish. → §III.2
- ⏳ **SH14** (deps: —) **Versioning UI** — the `/versions` endpoint exists; add console UI to diff/restore versions. → §III.3
- 💭 **SH15** (deps: SH12) **Scheduled publish/unpublish** — `publishAt`/`unpublishAt` on a post + a sweep that fires the transition and the webhook. → §III.4
- 💭 **SH16** (deps: SH6) **`shio` CLI** — `init` (scaffold), `dev` (proxy + types watch), `pull`/`push` (post-types as code), `deploy`. Mirrors Turing's `turing` CLI direction. → §III.5

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
