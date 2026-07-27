# Shio CMS - Development Guide

## Product orientation — read this before writing code

**Shio is a CMS whose primary operator is a coding agent.** The agent builds and
maintains the site (MCP tools + content as files + blueprints); the **human
curates** — approves, corrects, tweaks (React console, Universal Editor, preview
links); the **CDA delivers** (REST/GraphQL + the TS SDKs). Every design decision is
judged by *agent cycles and tokens*, not by clicks.

Shio hosts **no model and no prompts** — it is the substrate an external agent
drives. The differentiator is protocol quality (discoverability, determinism,
verifiability, token economy), not intelligence. The Java/Spring content engine and
the headless JS/TS delivery layer stay as they are; we do **not** rewrite the backend.

**The ten design laws** — a feature that violates one is wrong even if requested:

| # | Law |
|---|---|
| P1 | **Agent-first design order** — every capability lands MCP → files/CLI → REST → console. A console-only feature is incomplete. |
| P2 | **One call replaces a session** — discovery is a product feature (manifest + context pack), not a docs problem. |
| P3 | **Tokens are a measured budget** — response budgets + a CI benchmark; a token regression is a build failure. |
| P4 | **Files beat APIs for authoring** — project content to disk; `Edit`/`Write`/`Grep` are the agent's cheapest tools. |
| P5 | **Determinism over cleverness** — stable ordering, path addressing, idempotent writes, byte-identical serialization. |
| P6 | **Errors are instructions** — every 4xx carries `fix`/`allowed`/`didYouMean`/`example`. |
| P7 | **Never surprise the human** — draft-default, dry-run, explicit publish, confirm tokens, attribution, review queue. |
| P8 | **Skipping steps needs appliable units** — blueprints, not prose recipes. |
| P9 | **Close the perception loop** — cheap textual proof (lint, routes, render digest) instead of a human round trip. |
| P10 | **Compose, don't fork** — MCP is a *shape* over the CDA + console services, never a third contract. |

Full concept, protocol design, tool surface and worked example:
[docs/specs/SH74-agent-native-cms.md](docs/specs/SH74-agent-native-cms.md).
Backlog: [docs/ROADMAP.md](docs/ROADMAP.md) Blocks H–N. Rationale:
[docs/IMPROVEMENTS.md](docs/IMPROVEMENTS.md) §0 and §VIII–§XIV.

## Project Structure

### Backend (Spring Boot 4 + Java 21)
- **shio-app/** - Main Spring Boot application
  - `src/main/java/com/viglet/shio/` - Java source
  - `src/main/resources/ui/` - Legacy AngularJS frontend
  - `src/main/resources/public/` - React frontend build output
  - `src/main/resources/import/` - Default data (post-types.json, sample sites)
  - `store/` - Runtime data (file_source, logs, H2 database)

### Frontend (React + Vite + shadcn/ui + TypeScript)
- **shio-react/** - New React frontend
  - `src/app/console/` - Page components (content, admin, config)
  - `src/app/routes/` - Route definitions
  - `src/components/` - Reusable components
  - `src/components/ui/` - shadcn/ui components
  - `src/components/widgets/` - Dynamic form widgets (14 widgets)
  - `src/services/` - API service layer (class-based)
  - `src/models/` - TypeScript type definitions
  - `src/contexts/` - React Context providers
  - `src/i18n/` - Internationalization (en/pt)
  - `src/lib/` - Utilities (axios, utils, widget-colors)

## Architecture Patterns (follows Turing React)

### Service Layer
Class-based services with axios. Each domain has its own service:
```typescript
export class ShSiteService {
  async query(): Promise<ShSite[]> { ... }
  async get(id: string): Promise<ShSite> { ... }
  async create(site: ShSite): Promise<ShSite> { ... }
  async update(site: ShSite): Promise<ShSite> { ... }
  async delete(id: string): Promise<void> { ... }
}
```

### Context Providers
- `ThemeProvider` - Dark/light mode
- `BreadcrumbProvider` - Navigation breadcrumb state
- `UserProvider` - Current user from `/api/v2/user/current`
- `ShioServiceContext` - Centralized service instances

### API Base
- Backend: `http://localhost:2710`
- React dev: `http://localhost:5173` (Vite proxy to backend)
- API prefix: `/api/v2/`
- Post XP endpoint: `/api/v2/post/xp/{id}` (returns post with attrs for editing)

## Key API Endpoints

### Content Navigation — the console reads use the CDA shapes (SH120)
- `GET /api/v2/site` - List all sites
- `GET /api/v2/object/{id}/list` — children as `ShConsoleListing`:
  `{ folderId, folders, posts }` — `ShCdaListing`'s envelope. Rows are **strict supersets**
  of `ShCdaFolder` / `ShCdaPostSummary`: the CDA's components first (same names, same types,
  same order), then the console-only fields (`date`/`position` on a folder;
  `postTypeTitle`/`published`/`publishStatus`/`state` on a post).
  `ShConsoleShapeOverlapTest` fails the build if a shared component drifts.
  **Unpaged** — it returns a folder's children whole (SH165).
- `GET /api/v2/object/{id}/path` and `GET /api/v2/folder/{id}/path` — the CDA's own
  **`ShCdaPath`**: `{ site, breadcrumb: [...folders], currentFolder }`. Note `site`, not
  `shSite`; a breadcrumb folder carries `root: true|false`, not `rootFolder: 1`.
- Owned by `ShConsoleNavigationMapper`, which composes `ShCdaMapper` rather than restating
  it. **Do not serialize `ShFolder`/`ShPostUnified` out of a console read** — that was the
  old shape, and it dragged each post's attributes, folder, site and post-type field tree
  along to render one row.

### Posts (unified model only)
Content posts live in `sh_post_unified` (NOT the legacy split `ShPost`/`ShPostDraft`,
deleted in the nuclear refactor). All post CRUD is the unified API:
- `GET /api/v2/post-unified/{id}?state=DRAFT|PUBLISHED`, `/folder/{folderId}`, `/{id}/versions`
- `POST /api/v2/post-unified` (body `{ folderId, postTypeId, data, state }`)
- `PUT /api/v2/post-unified/{id}`, `POST .../publish|unpublish|restore|schedule`
- `DELETE /api/v2/post-unified/{id}`

**Every post-shaped response on this controller is a `ShConsolePost`, never the entity (SH176)**
— reads *and* writes, owned by `ShConsolePostMapper` + `ShConsolePostReadService`. It carries
`data` **parsed** (the column is a JSON string) and the post-type as its SH16 `ShPostTypeDef`,
so the form projects `fields` (there is no `schemaJson` — SH177 dropped the column and deleted
`/api/v2/migration/unified/**`, its only writer). Mapping happens **inside a transaction**,
because `shPostType`/`shFolder` are LAZY and a detached proxy is exactly how the post-type used
to reach the browser as `null` and every post edit page rendered zero fields. A write therefore
re-reads rather than mapping the entity its own (closed) transaction returned. `undeclaredFields`
names data keys the post-type does not declare — the console shows them with a warning instead
of hiding them, which is the read side of SH168.
- `GET /api/v2/post/xp/{id}` - resolve a referenced post for display (returns
  `{ shPost, allowPublish }`, DRAFT-preferred); used by Content Select / File widgets
- There is **no** `/api/v2/post` controller — do not add one.

### Object Operations
- `PUT /api/v2/object/copyto/{destFolderId}` - Copy objects (body: `[id1, id2]`)
- `PUT /api/v2/object/moveto/{destFolderId}` — Move objects (body: `[id1, id2]`).
  **A move relocates and changes nothing else (SH139):** it keeps the post's `furl`
  (it used to re-slug it from the title, silently changing a published page's live
  URL), assigns one only when there is none, and delegates the post branch to
  `ShPostUnifiedWriteService#move` — the same writer `post.move` uses, so the console
  and the agent cannot diverge on what a move means. A cross-site move onto an
  occupied URL is a teaching **409**, never quietly uniquified.
- Clone = copy to same folder

### Folders
- `GET /api/v2/folder/{id}` - Get folder
- `POST /api/v2/folder/{parentFolderId}` - Create folder
- `PUT /api/v2/folder/{id}` - Update folder
- `DELETE /api/v2/folder/{id}` - Delete folder

### Sites
- `GET /api/v2/site` - List all sites
- `GET /api/v2/site/{id}` — one site **by id, furl or name** (SH181, in that order so the
  declared key wins a collision). The reads resolve any of the three; `PUT`, `DELETE` and
  `/{id}/export` stay strictly id-keyed — a read that resolved a near-miss shows the curator
  the wrong site, a write that did would change it.
- `POST /api/v2/site` — create. **Session + CSRF**, so a stateless caller gets a bodiless 403;
  that is the friction SH170 is filed for, and `shio-react/e2e/provision.mjs` walks it by hand.

### Static files — upload/serve subsystem (SH38)
Binary files are first-class `File`-post-type unified posts; the bytes live on
disk, **keyed by the file post's id** (not the folder tree), under
`shio.file-source.path` (default `store/file_source/<postId>/<fileName>`).
- `POST /api/v2/staticfile/upload` (multipart: `file`, `folderId`, `createPost`) —
  creates a **published** `File` `ShPostUnified` (`data.FILE` = original name) +
  writes the bytes in the same tx; returns the created post (the `File` widget
  stores its `id`). `ShStaticFileService.resolveOrCreateFilePostType()` bootstraps
  the system `File` post type on first use (system post types are **not** seeded).
- `GET /api/v2/staticfile/{id}` — streams the bytes; `GET /pre-upload/{folderId}/{fileName}`
  is a legacy-parity existence check (never blocks upload).
- Bytes are also public at `/file_source/<postId>/<fileName>` via
  `ShStaticResourceConfiguration` (a purely-additive `WebMvcConfigurer`; only
  `/file_source/**` is exposed — **not** `/store/**`, which holds the H2 DB/logs).
- Deleting the post removes its bytes (`ShStaticFileCleanupListener` on the `DELETE`
  `ShPostLifecycleEvent`). Owner: `com.viglet.shio.api.staticfile` +
  `ShFileSourceProperties`. Per-tenant isolation (SH47) will prefix `tenantId`.

### There is NO generic `DELETE /api/v2/object/{id}` endpoint
Delete must be done by type: `/v2/post-unified/{id}` or `/v2/folder/{id}`.
(Deleting a folder cascades: `ShFolderWriteService` first removes the unified posts
in the folder + all descendant folders — the `sh_post_unified.folder_id → ShObject`
FK is `onDelete NO ACTION`, so they must go before the folder rows.)

**Backend ownership (rebuilt 2026.2 after the nuclear refactor wiped them):**
- `ShFolderAPI` + `ShFolderWriteService` (`com.viglet.shio.api.folder`)
- `ShObjectAPI` + `ShObjectNavigationService` + `ShObjectWriteService` (`...api.object`)
  — list/path/copyto/moveto; `clear-cache` is a no-op (no page-cache subsystem yet).
  Folders/sites are `ShObject` subclasses; **posts are not** (`ShPostUnified` is its
  own table) — so copy/move resolves folder ids via `ShObjectRepository` and post ids
  via `ShPostUnifiedRepository`. `/list` dedups posts by id, DRAFT-preferred.

### The agent surface — `/api/v2/agent/**` (Block H)
The namespace shaped for a coding agent rather than a browser. It is a **shape over**
the CDA + console services (law P10) and holds no business logic of its own.
- `GET /agent/manifest` — capabilities, limits, a curated endpoint index. **Read this
  first**; unshipped capabilities are reported `false`, never omitted.
- `GET /agent/context` — model + sitemap + conventions in one call. `?format=terse`,
  `?include=`, `?budget=`, `?from=`, `?ids=true` (rows are **id-free by default**, SH121).
  The `digest` is how a later session skips re-reading.
- `GET /agent/resolve?address=…` — an address → an object id, for id-keyed endpoints.
- `POST /agent/batch` — **writes**: N addressed ops (`site.upsert`, `folder.upsert`,
  `post.upsert`, `post.move`, `post.publish`, `post.unpublish`, `folder.delete`, `post.delete`) in one
  transaction; `{"dryRun":true}` reports without writing, and a batch that deletes needs
  SH108's `confirm` from its own dry run. **A `post.upsert` refuses a `data` key the post-type
  does not declare** (SH168) — a 400 carrying the field list and the nearest name, because an
  upsert declares whole state and a typo *replaced* the field it meant. Keys the post already
  holds are grandfathered (so a post carrying one stays fixable) and the `unknown-field` lint
  rule reports those; a **plan** warns on the row where a real run refuses, so a blueprint plan
  can still report the whole SH152 mismatch. **`post.move` (SH134) is the
  only way to rename or relocate a post** — re-addressing a `post.upsert` creates a
  second post and orphans the first with its id, audit trail and history. It takes `to`
  (new address) and/or `folder`, moves the DRAFT *and* PUBLISHED rows together (so a
  published post's live URL changes immediately, and the change set says so), refuses an
  occupied target and refuses to cross sites.
- `POST /agent/plan` / `POST /agent/apply` — a declarative desired-state content
  document; `plan` never writes, and `changed:0` is the content drift guard. `prune`
  deletes undeclared **posts and folders** inside the declared folders' subtrees (SH135),
  posts first and folders deepest-first; declared folders, their ancestors, and any folder
  a declared post calls home are protected. Also `shio apply <file>` in the CLI
  (`--dry-run` / `--check` / `-` for stdin).
  **A deletion plan is never shortened (SH138)** — `ShAgentChangeSet`'s constructor rejects
  `results.size() != ops`, so the type cannot express a cut, and the reason it gets this
  instead of the `truncated: true` every other unbounded section gets is that a shortened
  sitemap reads as incomplete while a shortened list of deletions reads as a *complete
  review of what is about to be destroyed*. Compiled ops therefore have their own ceiling,
  `shio.agent.batch.max-document-ops` (2000, vs `max-ops` 200 for a caller-written batch):
  a document's op count is not what the caller asked for, since a prune contributes one op
  per undeclared object. Which ceiling applies is a `Source` argument only
  `ShAgentDesiredStateService` passes — **never a field on `ShAgentBatch`**, or a caller
  could claim the document ceiling for a typed batch. Past it the run is refused
  (`document-too-large`) naming the deletion count and pointing at narrowing `folders`;
  do not "fix" that by re-adding the old `batch-too-large` advice, which tells the caller
  to split ops they never wrote.
- `GET /agent/blueprints` · `GET /agent/blueprints/{name}` · `POST /agent/blueprints/{name}/apply`
  — **appliable starting points** (Block J, below). `dryRun` in the body returns the whole
  plan, post-types included, and writes nothing. Two opt-ins, both refused by default because
  each changes something the package does not own: **`createSite`** (SH151) makes the target site
  blank if it is absent — the plan names it before it happens, and "the site you named does not
  exist" is never read as permission — and **`mergePostTypes`** (SH152) adds the fields an
  existing post-type lacks, never changing, removing or reordering one and never touching a
  system type. Neither is an MCP argument: either one puts `shio_apply` over the SH132 per-tool
  ceiling, and `tools/list` is re-sent every turn (P3), so an MCP client creates the site with
  `shio_write`'s `site.upsert` and the missing-site error says so.
- `GET /agent/find` — posts by site/type/folder-subtree/text; **addresses, not bodies**. A post
  with no friendly URL is addressed **`id:`** (SH172) — the fallback lives in
  `ShAddressResolver.addressOf`, so `find`, `changes` and the review queue cannot disagree about
  it, and the envelope's `notes` say the address is a fallback rather than a route.
- `GET /agent/read?address=…&fields=…` — post content, DRAFT-preferred, projected. With **several**
  addresses a row that cannot resolve carries its own SH80 `error` and the rest still come back
  (SH171); the **single**-address form still 404s. The read path is
  `noRollbackFor = ShTeachingException.class` for that reason — catching a teaching exception
  inside a read transaction otherwise blows up at commit.
- `GET /agent/verify` — the content-side `tsc` (SH99/SH100). `checks=content,routes,diagnostics`
  (all by default), `site=`, `limit=`. **`ok` is false only for an *error*** — a warning is
  something the CMS cannot prove from inside itself (an empty folder, a link that may be a
  front-end route), and a gate that failed on those is a gate people bypass. Every finding
  carries an SH79 address and a `fix`. `found` vs `findings` makes a cut visible (SH138's rule).
  **`folder=` / `address=` / `since=` scope the run** (SH143) — a subtree, comma-separated
  `post:` addresses, or an SH88 cursor; they union rather than intersect. A scoped run sets
  `partial` and describes itself in `scope` (both **absent** on a full pass), because `ok` over a
  subset is not a statement about the instance. What scoping narrows is the **subjects**; the
  cross-cutting indexes — who else claims this URL, does this link land, does this folder hold
  anything — stay whole, built from id/url projections. Narrowing them would invent a
  `link-unresolved` for every link out of the scope and miss the duplicate whose twin is
  outside. A `folder=` that is not a folder is a teaching **400**: a scope that covered nothing
  while answering `ok` is the worst false green there is.
- `GET /agent/render?address=…` — a page's **structural** digest (SH101): heading outline,
  landmark counts, links, images with alt, word count, plus a `digest` hash over the
  structure only (a typo fix does not move it; a lost heading does). `?url=` digests the
  delivered page but is **off** unless `shio.agent.render.allow-fetch=true`, and only for
  the origins of readable sites — an endpoint that fetches arbitrary URLs server-side is a
  request-forgery surface.
- `GET /agent/changes` — **the change feed (SH88)**: content transitions with an SH79
  address each, and a `cursor` to pass back as `since=`. The cursor is `<instant>|<id>`,
  never a bare timestamp — audit rows share timestamps, so a timestamp cursor would drop
  the entries that tie (a bare one is a teaching 400, not an interpretation). Both modes
  are ordered **oldest → newest** so the cursor is always the last row. An entry younger
  than `shio.agent.changes.lag-millis` (1s) is **held back**: SH51 stamps the audit row
  inside the writer's transaction, so a slow commit could otherwise land behind a cursor
  that already passed it — the `notes` say so, and SH147 is the real fix. Site-scoped by
  the SH2 allow-list.
- `GET /agent/diagnostics` — asynchronous failures (SH103): webhook deliveries, image
  transforms, scheduled publishes, 5xx. A **bounded in-memory** window that reports its own
  bound (`since`/`dropped`/`notes`), because "no incidents" must not read as "nothing ever
  failed". Recorded by `ShAgentIncidentLog`; repeats fold into one row with a count.
- `GET/PUT/DELETE /agent/memory` — **the instance memory (SH114)**: keyed notes that come
  back in the context pack's `conventions`, attributed to whoever wrote them. Upsert by
  `(tenant, scope, key)`, so re-stating a convention rewrites one row; `scope` is a site name
  or `""` for the whole instance (empty, never `NULL` — a unique index does not treat `NULL`s
  as equal). Deliberately **not** content: read on the hot path, never delivered, and it must
  survive an SH82 `prune`. **No MCP tool** — a tool schema is paid every turn and a coding
  agent has a shell, so the write path is `shio remember`; SH160 is the open question for a
  client without one, and SH161 is that notes do not yet travel with an exported site.

**Conditional reads (SH116).** `manifest`, `context` and `blueprints` carry strong ETags;
`If-None-Match` on an unchanged instance answers `304`, which measures as **0** on
`X-Shio-Est-Tokens`. A tag names a **representation**, not a state — the SH77 digest covers
content only, so JSON and terse share it and every tag mixes in `format` + `ids`. Responses
say `Cache-Control: no-cache` (store it, ask first), never a freshness lifetime. The rest of
the surface is still unconditional (SH159).

**`?format=` on `/agent/context` has three values (SH78, SH115):** `json`, `terse`, and
`agents-md` — the same pack rendered as project agent instructions, which is what
`shio context --as agents-md --out AGENTS.md` writes. An unknown value is a teaching **400**,
not a silent fallback to JSON.

**The cost of all of it is measured, not asserted (Block M).** `ShAgentTaskBenchmarkTest`
runs four canonical jobs over the agent surface *and* over the id-keyed console/CDA path and
asserts both ratios; `ShAgentResponseBudgetTest` guards eleven canonical response sizes. Both
read their ceilings from `shio-app/src/test/resources/token-budgets.properties` — the same
file as the SH132 MCP budgets, because a second place to keep a number is a place that goes
stale. Raising a ceiling is allowed and must be justified in the commit.

### MCP — `POST /mcp` (SH75/SH83)
Same capabilities over the Model Context Protocol (JSON-RPC 2.0, revision `2025-06-18`),
hand-rolled in `com.viglet.shio.api.agent.mcp` — **not** the MCP Java SDK, which is
Jackson-2/Boot-3 while this app is Jackson-3/Boot-4. `GET /mcp` is a deliberate `405`
(this server sends nothing unprompted). `shio mcp` in the CLI is a stdio bridge:
`claude mcp add shio -- shio mcp`.
- **Tools** (`ShMcpTool` beans, auto-registered): `shio_context` · `shio_find` ·
  `shio_read` · `shio_write` · `shio_apply` · `shio_publish` · `shio_assets` ·
  `shio_changes` · `shio_verify` · `shio_digest`. Every tool is a *shape* over a REST
  surface — if a tool needs new logic, the logic goes on the REST side first.
- **Resources**: `shio://manifest` · `shio://blueprints` · `shio://context` ·
  `shio://schema/{postType}`.
- `shio_apply` covers **two** verbs on purpose: a desired-state document, or a blueprint
  (`blueprint` + `params`). "Converge to this document" and "converge to this package" are
  one decision with two inputs, and an eleventh tool would cost more schema per turn than
  the two arguments do — see the budget note below.
- **Prompts**: `new-site` · `add-section` · `translate-site` · `audit-content`.
- **Error routing matters**: a fixable failure (bad address, unknown post-type) is a
  tool *result* with `isError:true` so the model reads the SH80 `fix`; a JSON-RPC
  `error` is only for an unusable message (unknown method/tool), which the client
  swallows. Getting this backwards throws away the teaching error.
- A tool-count test fails the build past 12 — tool schemas are context paid every turn.
  The real ceilings are `mcp.tools.list.total` (2160) and `mcp.tools.list.max-per-tool`
  (460) in `src/test/resources/token-budgets.properties`, both measured; raising one is
  allowed and must be justified in the commit. `shio_apply` is the tool to watch — at 437
  it is the largest, because it carries the blueprint verb as well as the document one.
  `ShMcpWireConformanceTest` pins `tools/list` against a golden — regenerate with
  `-Dshio.mcp.golden.update=true` and review the diff when the surface really changes.

Addresses (SH79): `post:<site>/<friendly-url>` · `folder:<site>/<name-chain>` ·
`site:<name>` · `id:<uuid>`. **Writes take only `post:`/`folder:`** — a folder path and
a friendly URL are separate namespaces, and a write that guessed wrong would create
content in the wrong place silently. CSRF-exempt (stateless callers), authenticated
like every other API; the SH54 ACL and SH22 quota apply on the agent path identically.

### What bounds an agent — Block L (SH106–SH109)

**The invariant: the agent path is never a privilege escalation**, and after Block L it is
not a privilege *superset* either. Three guards, all narrower than what was reachable before:

- **`AGENT` token scope (SH106).** The credential an agent runs on: writes on
  `/api/v2/agent/**` and `/mcp`, reads drafts like `PREVIEW`, carries `ROLE_AGENT` and
  **not** `ROLE_CDA_WRITE` — on the CDA it stays read-only. That is the whole reason it is
  not an alias for `WRITE`: a `WRITE` key patches published content outside the agent
  protocol's guards. It extends the SH2/SH5/SH13 model (expiry, site allow-list, rate
  limit), so an agent credential can be scoped and expiring instead of a console password.
- **Session identity (SH106).** `ShHistory.agent_session_id` — the column SH75 deferred, and
  what makes SH109 a `where` clause. `owner` names only the human whose credential was used.
  Set by **`ShAgentSessionFilter`** from the `Mcp-Session-Id` header on the agent namespace,
  and by MCP's own `initialize`; **never minted per request** — a session is a *run*, and one
  session per call is the same as none. `ShAgentSessionContext` is the ThreadLocal (it used to
  be `ShMcpSessionContext`, in the `mcp` package).
- **The publish gate (SH107).** `api_token.may_publish`, **off unless granted**, enforced in
  `ShAgentBatchService` — which is also the apply and blueprint path, since a document
  compiles to `post.publish` ops. Checked **before the first write** and **on a dryRun too**,
  so an agent learns it cannot publish while planning. A console caller has no token and is
  unaffected. It is a tightening: a `WRITE` token that used to publish through the agent
  surface now needs `mayPublish`.
- **Confirm tokens (SH108).** `post.delete`/`folder.delete`/a deleting `prune` take two calls:
  a `dryRun` returns `confirm`, the real run sends it back. The digest is over **what the plan
  actually deletes** — not the requested ops, so a delete of something already gone is a noop
  that needs nothing and a document stays re-runnable (SH82). Absent is a **428** carrying the
  token and the list; wrong is a **409** naming what would go now. `ShAgentConfirm` owns it;
  the CLI's `applyConfirmed` does the two-step and prints the deletions *before* applying.
- **The review queue (SH109).** `GET /api/v2/review/sessions` · `/sessions/{id}` ·
  `POST …/approve` · `POST …/revert`, plus the console page at `/console/admin/review`.
  Console-authenticated and deliberately **not** on the agent namespace. It is a **view over
  the SH51 audit grouped by session id, not a proposal table** — the DRAFT row *is* the
  proposal — so approve = publish the run's drafts and revert reuses SH14 restore + the SH55
  trash, per entry, reporting what it cannot honestly undo instead of guessing.

### Post Types — one name-keyed surface (SH16 + SH117)
**There is one post-type API and it is keyed by name.** The CLI, the agent path and the
React console all use it. (SH117 retired the id-keyed, entity-shaped twin that used to
live at `/api/v2/post/type` — note the slash. If you find a reference to that path, it is
stale; there is no console post-type controller.)
- `GET /api/v2/post-type` — list as **lossless** `ShPostTypeDef`s (keeps `Tab`, verbatim
  `widgetSettings`, nested Relator fields — the authoring inverse of the lossy SH11 CDA schema)
- `GET /api/v2/post-type/{name}` — one def (404 unknown)
- `POST /api/v2/post-type` — **create**, 409 on an existing name (name comes from the body).
  This is what the console's "new post-type" form uses: a collision there is a mistake to
  report, not a model to overwrite.
- `PUT /api/v2/post-type/{name}` — **upsert**, idempotent by name (what `shio push` converges
  with); rejects system types (409), unknown widgets (400)
- `DELETE /api/v2/post-type/{name}` — delete a non-system type (204/404/409)

**Creating a site is `site.upsert` on the batch surface (SH170)** — addressed `site:<name>`,
idempotent, and it creates a **blank** site (`ShSiteWriteService.createBlank`), not a clone of the
SH71 bootstrap template. It **never updates**: a site's URL and description are console concerns
with a human looking at them, so the op creates or reports `noop`. A token with an SH2 site
allow-list is **refused** — inventing a site outside the list is the escalation §XII forbids.
`POST /api/v2/site` still exists for the console (session + CSRF, and it keeps the starter clone).

**`managed` on a write is a claim about the writer, not a data field** — which is why it is a
`Boolean` and why *absent* and `false` mean different things:
- **absent** (or `true`) → a **code-owned** write. Marks the type managed, and is allowed
  against an already-managed type because the writer is the owner. The CLI's canonical
  serialization omits the key, so `shio push` and a hand-written `*.json` both land here.
- **`false`** → a **hand edit** (the console always sends this). Marks the type unmanaged, and
  is **refused with 409** (`ShManagedPostTypeException`) against a managed type rather than
  racing the repository that owns the model.

It is a UX guard, not an ACL — any caller may claim either. Console-authenticated (HTTP Basic
or a session). **CSRF is required of session-bearing callers and waived for stateless ones**
(`ShStatelessRequestMatcher`, keyed on the session cookie), which is what lets the CLI and the
browser share the path safely — do not widen that back to a blanket exemption.

**Renaming a post-type is not supported** (SH163): the name is the key, so a `PUT` to a new
name creates a second type and orphans the first with its content. The console's API-name
field is read-only for this reason.

**Cascade gotcha:** `ShPostTypeAttr` maps `shWidget`/`shPostType`/`shParentPostTypeAttr` as
`@ManyToOne(cascade=ALL)`, so `ShPostTypeAdminService` detaches those before the
orphan-removal replace — otherwise an update would cascade-delete the shared widget catalog.

### `shio` CLI (`@viglet/shio`, in `cli/`)
The "JS dev, zero Java" command-line tool: `init` (scaffold via `create-shio-app`; **`--blueprint <name>` takes the starter from the instance instead, and with `--site` applies its content half in the same command** — SH119), `types` (SH11 codegen), `pull`/`push` (post-types as code → `shio/post-types/*.json`, `--dry-run`/`--prune`; **`--content` switches both verbs to content**, see below), `context` (`--as agents-md` renders the pack as project agent instructions — SH115),
`resolve`, `apply` (SH129 — a desired-state content document; `--check` is the CI drift guard, `-` reads stdin; **`--blueprint <name>` applies a package instead**, with `--param k=v` repeatable, `--params <file>`, `--site`, `--force`), `blueprints` (SH92 — the catalogue; `blueprints <name>` for one package's parameters), `verify` (SH99/SH100 — the content lint; **exits 1 on an error**, `--strict` also on warnings, `--folder`/`--address`/`--since` scope it per SH143, `--baseline`/`--accept` subtract accepted findings per SH142), `digest` (SH101 — one page's structure), `changes` (SH88 — the feed; `--since last` reads the last content pull's position, `--cursor` prints only the position), `remember` (SH114 — the instance memory; bare lists it, `<key> "<note>"` writes one, `--forget` drops one, `--site` scopes it), `mcp`, `dev` (`--content` runs the SH90 sync loop instead of the site's dev server), `deploy`. `pull`/`push` use console Basic auth (`SHIO_USER`/`SHIO_PASSWORD`); `types` uses a CDA token (`SHIO_CDA_TOKEN`).
**CI gates, in the order they should run:** `shio push --check` (model drift) → `shio push --content --check` (content-tree drift) → `shio apply --check` (document drift) → `shio verify` (content defects).
**Accepted findings (SH142) live in `shio/verify-baseline.json`** — `(rule, address, field)` triples that `shio verify` subtracts, written by `--accept` (`--reason` optional) and merged, never replaced. The half that matters: **an accepted finding the run no longer produces fails the run**, so the file cannot rot into a blanket suppression that hides the next defect at the same address; a *truncated* report reports the unmatched entries without failing, because a finding that did not fit is not a finding that is gone. It is deliberately a project file and **not** an instance setting — `GET /agent/verify` keeps reporting everything, and only the gate subtracts. The content gates read **only the sites the tree holds** (SH173, derived from `shio/content/<site>/**`), a post they cannot read by address is a named finding rather than a fatal 404, and a remote failure prints the SH80 body — the request URL is behind `SHIO_DEBUG`. The CLI test suite needs `@viglet/shio-client` built first (`pnpm -C shio-client run build`), or every command test fails on a missing module rather than on its own logic; `shio-sections`' suite needs `@viglet/shio-model` built (`pnpm -C shio-model run build`) for the same reason.

### Content as files — `shio/content/**` (Block I)
Content projects to **one Markdown file per post**, `shio/content/<site>/<url>.md`: YAML
frontmatter for the metadata + `fields:`, and the post's first `HTML Editor` field as the
document body (the file records which field that was as `body:`, so reading it back never
needs the model). Owned by `cli/src/content-files.mjs` (the format, pure),
`content-sync.mjs` (the three-way merge, pure) and `content-store.mjs` (disk + wire).
- **The path is the URL, not the folder chain.** A URL is unique per site, so the tree
  cannot collide; the folder travels in frontmatter because SH79 keeps the two namespaces
  apart. A file whose path and `url:` **disagree** is an error — a half-done rename is a
  move on one side and a create on the other (SH134's lesson).
- **Only the DRAFT projects.** `publish:` in a file is an *instruction* push forwards, not
  state pull writes back — the merge deliberately ignores it.
- **Losslessness beats prettiness:** a value that would not survive the round trip stays in
  `fields:` (as JSON) instead of becoming the body. The one normalization is CRLF → LF in
  the body, so a pull cannot report a change nobody made.
- **`shio pull --content`** merges three ways against `shio/.shio/content-base.json` (the
  last-synced base + the SH88 cursor): one side changed → that side, different fields →
  merged, same field → **conflict**, which writes nothing, exits 1, and offers
  `--ours`/`--theirs`/`--show-remote`. **There are no conflict markers on purpose** —
  `<<<<<<<` in a field value is content the next push would publish.
- **`shio push --content`** compiles the tree into one desired-state document per site and
  applies it through `/agent/plan|apply` (law P10). It declares a post the server changed
  with the **server's** values — declaring the base would revert a console edit, omitting
  the post would expose it to `--prune`. `--check` is the drift guard.
- **Two refusals to keep:** a truncated `find` result makes the projection partial, so pull
  refuses (`--allow-partial` marks the snapshot, and `push --prune` then refuses too), and
  `push --content` over an empty directory is an error, not an empty document.
- Not projected yet: **assets** (SH145) and **empty folders** (SH149); a rename still
  pushes as delete + create rather than `post.move` (SH150).

### Blueprints — appliable starting points (Block J)

A **blueprint** is a section of a site the instance can apply: a content model, a folder
tree, sample pages, the parameters that make it somebody's, and the promise it keeps about
what exists afterwards (law P8 — skipping steps needs *appliable* units, not recipes).
Owner: `com.viglet.shio.blueprint`; packages live at `shio-app/src/main/resources/blueprints/<name>/`.

A package is a directory with `blueprint.json` plus the files it references:
`postTypes` are SH16 `ShPostTypeDef` JSON (the same bytes `shio pull` writes), `content`
files are SH82 desired-state documents, `files` are optional front-end sources, and `verify`
is the promise. **Nothing in the format is a new serialization**, which is what keeps it
from becoming a third contract (P10).

**The rules that are load-bearing:**
- **A package may not name its own site, and may not `prune`.** The site is the caller's; a
  `site` key in a content document is refused at load rather than ignored (an ignored key
  looks like it worked). Prune's blast radius depends on what an author forgot to mention,
  and that author is a package the user did not write.
- **Substitution is `{{name}}` and nothing else** — no conditionals, no expressions, so the
  same parameters always produce the same site (P5). `site` is a reserved parameter, always
  available, never declarable. An unknown parameter is a teaching 400, never ignored.
- **Everything is validated at load** (`ShBlueprintRegistry`): a missing referenced file, a
  path that escapes the package, an unknown manifest key, a `{{placeholder}}` with no
  declared parameter, a `verify.posts` entry the content never declares. These are the
  *author's* mistakes; a malformed package is a **409, not a 400** — nothing the caller sent
  caused it. `ShBlueprintRegistryTest` runs without a Spring context, so it is cheap enough
  to run on every change to a package.
- **The model is provisioned, never upserted.** An existing post-type of the same name is
  left exactly as it is (including a field a human added) and the response *names the fields
  it lacks* — the bootstrap `Page` (SH71: `title` + `content`) versus the section
  vocabulary's `Page` is the collision this actually happens to. SH152 reconciles.
- **The plan provisions post-types inside its transaction and rolls back** — otherwise the
  content planner fails on "no post-type named Article" for exactly the packages blueprints
  exist for. This is the technique `ShAgentBatchService` refuses for *content*, and the
  difference is real: post-type provisioning emits no audit rows, webhooks, events or quota
  movement. The content half of the plan still writes nothing at all. SH153 removes the
  write entirely.
- **The server never writes a project file.** `files` are returned and the CLI writes them
  (never over an existing one without `--force`); an MCP-only caller is told what it has
  *not* got.
- **Packages load from the classpath only.** Where a package may come from is a trust
  question — SH96.

**The first-party set (SH94, SH119):** `blog` · `docs` · `product-catalog` ·
`marketing-site` · `landing` · **`nextjs-starter`**. The last one is the
`create-shio-app` template as a package (SH119) and the only one that carries front-end
`files` — its tree is **generated** from `create-shio-app/template/` by
`pnpm -C create-shio-app run emit`, with `test/emit.test.mjs` failing the build on drift.
**If you change `create-shio-app/template/`, run `emit`.** Substitution is split by who
knows the answer: the server substitutes a package's *content*, the client its *carried
files* (the project's own name is local identity, and the server never writes a project
file) — and the JS regex is character-for-character `ShBlueprintParams`' so the two halves
cannot disagree. A package's path refs are checked per *segment* for `..`, not per
substring, or `app/[...slug]/page.tsx` would be rejected as traversal. Each declares a `verify` promise, checked at load against its own content and
after an apply against the instance (alongside the SH99 lint); `shio apply --blueprint` exits
non-zero when that fails.

### The section vocabulary — `@viglet/shio-sections` (SH95)

Eight post-types (`Page` + `SectionHero`/`SectionFeatureGrid`/`SectionCta`/`SectionLogoWall`/
`SectionTestimonial`/`SectionFaq`/`SectionRichText`) authored with `@viglet/shio-model`, and
the matching React components with a `SECTION_COMPONENTS` registry and a `<Sections>`
dispatcher. This is what makes *"now change the site"* cheap instead of archaeological: one
vocabulary for the agent, the console and the front end.

- **A `Page` names its sections by friendly URL, in order**, in a `Multi Select` — not by id
  (SH79 exists so no UUID has to survive between calls) and not as a `Relator` (one fixed
  child shape, while sections are deliberately heterogeneous).
- **The field declares that it holds addresses** — `widgetSettings` carries
  `{"multiselect":{"references":"post"}}` (SH154/SH169), and that key is what makes the
  composition *verifiable* rather than a convention. See **`ShPostReferenceFields`** below;
  `@viglet/shio-model` encodes it as `references: "post"` on `multiSelect(...)`.
- **The components are unstyled and semantic**: they ship structure (heading level, landmark,
  alt text) behind stable `shio-*` class names. SH101's digest reads structure, so a shared
  vocabulary is only verifiable if structure is the shared part. `<Sections>` takes
  **resolved** sections rather than fetching them.
- **The definitions are authored once and generated into the packages** that ship them
  (`pnpm -C shio-sections run emit`), because a running instance reads packages off the
  classpath and cannot run a JS build. `emit.test.mjs` fails the build the moment the two
  copies disagree. **If you change `src/post-types.ts`, run `emit`.**
**`ShPostReferenceFields`** (`com.viglet.shio.api.agent.verify`) is the one place that answers
"does this `Multi Select` hold post URLs or tags", and both readers key on it (SH154/SH169):
- `shio verify`'s **`dangling-reference`** rule resolves each declared URL in the post's own site
  and reports the ones that are not there — the same rule that already knew an id-shaped
  `Content Select`/`File` reference. `multiselect.postType`, when present, narrows it.
- `/agent/render`'s digest **composes** the page: each referenced section contributes its title as
  an `<h2>` and its markup/text fields to the outline, in the order the page names them, so two
  pages built from different sections no longer share `e3b0c442…` (the hash of nothing). A URL
  that resolves to nothing becomes `<h2>[missing section /url]</h2>` rather than contributing
  nothing — two different mistakes must not hash the same.
- **A `Multi Select` without the key stays tags.** Reading every value as a URL would report a
  defect for every tag on every post, which is why the field has to say what it holds. `references`
  and not `postType` because sections are heterogeneous, and not a naming convention because
  nothing can check prose.

### The Claude Code plugin (SH97)

`claude-plugin/` (published via the repo-root `.claude-plugin/marketplace.json`):
`claude plugin marketplace add openviglet/shio && claude plugin install shio`. An `.mcp.json`
entry running the CLI's stdio bridge through `npx`, three skills (`shio-site`,
`shio-content-model`, `shio-verify`), three commands (`/shio-start`, `/shio-blueprint`,
`/shio-check`) and an `AGENTS.fragment.md` for sessions without the plugin. It is the
**distribution channel for the whole concept**. `claude-plugin/test/plugin.test.mjs` validates
the manifests — a skill whose description does not state its trigger never loads, and that
failure is invisible to every other test in this repository.

## Security Configuration

### CSRF (follows Turing pattern)
- Backend: `CookieCsrfTokenRepository` (HttpOnly cookie)
- `ShCsrfHeaderFilter` forces lazy token materialization + adds `X-XSRF-TOKEN` response header + `Access-Control-Expose-Headers`
- `ShCsrfController` at `GET /api/v2/csrf` returns token as JSON
- Frontend: `src/lib/axios.ts` interceptor fetches token from `/v2/csrf` before PUT/POST/DELETE, reads from response header, auto-retries on 403

### Authentication
- HTTP Basic Auth
- `HttpSessionSecurityContextRepository` for session persistence
- `BasicAuthenticationFilter` with custom `ShAuthenticationEntryPoint`
- Login flow: React sends `GET /api/v2` with Basic auth header via `fetch()`, then redirects to `/console`

### CORS
- `Customizer.withDefaults()` in security config
- `WebMvcConfigurer.addCorsMappings` for `/api/**` with `allowedOriginPatterns("http://localhost:*")`, `allowCredentials(true)`, `exposedHeaders("X-XSRF-TOKEN")`

### Security Config Key Points
- Uses `PathPatternRequestMatcher.withDefaults()` with `mvc.matcher()` (same as Turing)
- Filter order: `addFilterAfter(ShCsrfHeaderFilter, BasicAuthenticationFilter.class)` (NOT CsrfFilter.class)
- Logout deletes cookies: `JSESSIONID`, `XSRF-TOKEN`
- `/api/v2/csrf` must be in `permitAll()`

## UI Design System

### Available shadcn/ui Components
Located in `src/components/ui/`:
avatar, badge, breadcrumb, button, card, checkbox, dialog, dropdown-menu, form,
gradient-button, input, label, select, separator, sheet, sidebar, skeleton, sonner,
switch, table, tabs, textarea, tooltip

**Not available** (do not attempt to import): accordion, collapsible, popover, command, calendar

### Widget Color System (`src/lib/widget-colors.ts`)
Shared color constants used across both the post-type editor and post form rendering.
Each widget type has a unique color for visual identification:

| Widget | Badge Color | Accent |
|---|---|---|
| Text | Blue | `border-l-blue-500` |
| Text Area | Indigo | `border-l-indigo-500` |
| HTML Editor | Purple | `border-l-purple-500` |
| Check Box | Green | `border-l-green-500` |
| Combo Box | Teal | `border-l-teal-500` |
| Date | Amber | `border-l-amber-500` |
| Hidden | Gray | `border-l-gray-400` |
| Multi Select | Cyan | `border-l-cyan-500` |
| Ace Editor - JS | Yellow | `border-l-yellow-500` |
| Ace Editor - HTML | Orange | `border-l-orange-500` |
| Content Select | Rose | `border-l-rose-500` |
| File | Pink | `border-l-pink-500` |
| Relator | Violet | `border-l-violet-500` |
| Tab | Slate | `border-l-slate-500` |

Helper functions: `getWidgetBadgeColor(name)`, `getWidgetAccentColor(name)` return appropriate classes with fallback defaults.

**Usage**: Import from `@/lib/widget-colors` in any component that needs widget-type-aware styling.

### Page Layout Pattern
Editor pages follow a consistent layout:
1. `<PageHeader>` with icon and breadcrumb
2. **Sticky save bar** - `sticky top-0 z-10` with `bg-orange-50 dark:bg-orange-950/30` orange accent:
   - Left: title + metadata badges (post type, field count, publish permission)
   - Right: Save, Save & Close, Cancel buttons
3. **Card(s)** containing the form content

### Field Card Pattern
Form fields (both in post-type editor and post form) render as individual cards:
- `border-l-4` with widget-type accent color
- `rounded-lg border bg-background`
- `hover:shadow-sm transition-shadow` for interactivity
- Header row with label, required indicator (`*`), and inline badges (Title, Summary, widget type)
- Widget component renders inside the card
- Optional description text below

### Empty State Pattern
When a list has no items, show a centered empty state:
```tsx
<div className="flex flex-col items-center justify-center py-16 text-center">
  <div className="rounded-full bg-muted p-4 mb-4">
    <Icon className="h-8 w-8 text-muted-foreground" />
  </div>
  <h3 className="font-semibold text-lg mb-1">Title</h3>
  <p className="text-sm text-muted-foreground max-w-sm mb-6">Description</p>
  <Button>CTA</Button>
</div>
```

### Toggle Preferences
- Use `Switch` for boolean toggles in settings/editor forms (modern look)
- Use `Checkbox` only inside form widgets (e.g., CheckBoxWidget for content editing)
- Use `Tooltip` with `Info` icon for field help text instead of permanent description paragraphs

### Badge Patterns
- **Widget type badge**: Colored per widget using `WIDGET_BADGE_COLORS`, variant `outline`
- **Required badge**: `border-amber-300 text-amber-600` (dark: `border-amber-700 text-amber-400`)
- **Title badge**: "T" with `border-blue-300 text-blue-600`
- **Summary badge**: "S" with `border-green-300 text-green-600`
- **Publish badge**: Shield icon with green background
- **Field count badge**: `variant="secondary"` with count number

## Widget System

### Dynamic Form Rendering
Widgets are React components that render based on the field's `widget` name
(`ShPostTypeSchemaField.widget`, projected from the post-type's `fields` by
**`postFormSchema()`** — the one exported entry point, SH178. It used to read
`shPostTypeAttr.shWidget.name` (SH117), and there used to be a second export with no fallback,
which is why creating a post worked and editing it did not.)
Registry in `src/components/widgets/index.ts` maps widget names to components via `WIDGET_MAP`.
`getWidget(name)` returns the component, defaulting to `TextWidget` for unknown names.

### Widget Names (from database)
| Widget Name | Component | Data Field |
|---|---|---|
| `Text` | Input | `strValue` |
| `Text Area` | Textarea (6 rows) | `strValue` |
| `HTML Editor` | contentEditable with formatting toolbar (Bold, Italic, Underline, Lists, Link, Code) | `strValue` |
| `Check Box` | Checkbox (multiple, parsed from widgetSettings choices) | `arrayValue` |
| `Combo Box` | Select dropdown (parsed from widgetSettings choices, optional null) | `strValue` |
| `Date` | Input type=date with clear button | `dateValue` (ISO string) |
| `Hidden` | hidden input (not rendered visually) | `strValue` |
| `Multi Select` | Free-form tag input with Enter to add, X to remove | `arrayValue` |
| `Ace Editor - Javascript` | CodeMirror (javascript mode, line numbers, folding) | `strValue` |
| `Ace Editor - HTML` | CodeMirror (html mode) | `strValue` |
| `Content Select` | ID input + status indicator + clear button | `strValue` |
| `File` | ID input + status indicator + clear button | `strValue` |
| `Relator` | Nested items list with add/remove (uses `crypto.randomUUID()`) | `shChildrenRelatorItems` |
| `Tab` | Groups fields into tabs (container, no data) | none |

### WidgetProps Interface
```typescript
interface WidgetProps {
  attr: ShPostAttr       // Current field value and metadata
  onChange: (attr: ShPostAttr) => void  // Callback for changes
  postId?: string        // ID of post being edited
}
```

### PostFormFields Component (`src/components/widgets/post-form-fields.tsx`)
Renders the dynamic form for post editing/creation:
1. Sorts attrs by `shPostTypeAttr.ordinal`
2. Groups fields into `TabGroup[]` when Tab widgets are present
3. First group (before any Tab) gets the post type name as label
4. Each tab trigger shows a field count badge
5. Each visible field renders as a **color-coded card** with widget accent border
6. Hidden widgets render invisibly (no card wrapper)
7. Fields show: label with required asterisk, Title/Summary/WidgetType badges, widget component, description

### Tab Widget Behavior
Tab widgets split the form into tabbed sections. The `PostFormFields` component:
1. Sorts attrs by `ordinal`
2. Groups attrs between Tab widgets into `TabGroup[]`
3. First group (before any Tab) gets the post type name as label
4. Renders shadcn `Tabs` with `TabsList` + `TabsContent`

### Widget Settings
Stored as JSON string in `shPostTypeAttr.widgetSettings`. Parsed by each widget:
- CheckBox/ComboBox: `{ "choices": "id:label\nid2:label2", "allowNull": 1 }`
- File: `{ "file": { "folderId": "..." } }`
- ContentSelect: `{ "contentSelect": { "folderId": "..." } }`
- MultiSelect: `{ "multiselect": { "postType": "...", "folderId": "..." } }`
- Relator: `{ "relator": { "title": "..." } }` (plus nested `shPostTypeAttrs`)
- Search/Semantic: `{ "search": { "association": "...", "isAdditionalField": 1, ... } }`

**IMPORTANT**: Always use safe parsing for `widgetSettings`. `JSON.parse("null")` returns `null`, not `{}`. Use the `parseSettings()` helper or guard:
```typescript
const p = JSON.parse(raw);
return p && typeof p === "object" ? p : {};
```

## Post Type Editor (`post-type.edit.page.tsx`)

### Layout
- Sticky save bar with title and Save/Save & Close/Cancel buttons
- Card with Tabs: Details (title, description, API name, plural name) | Workflow
- Fields card with drag-and-drop field list

### Field Editor (AttrFieldList + SortableAttrRow)
- Uses `@dnd-kit/core` + `@dnd-kit/sortable` for drag-and-drop reordering
- Each field is a collapsible card with:
  - Left accent border (widget color), numbered circle badge, expand chevron
  - Label (bold), identifier (mono), widget type badge, flag badges (Required, T, S)
  - Icon-only delete button with tooltip
- Expanded state has inner Tabs: Details | Semantic Navigation
  - Details: 2-column layout (label + identifier), field type select, horizontal Switch toggles (Required, Title, Description), widget-specific settings in bordered container
  - Semantic Navigation: search association dropdown, additional search field toggle
- Empty state shown when no fields exist

### Widget-Specific Settings in Post Type Editor
Rendered inside `WidgetSpecificSettings` based on widget name:
- **Check Box / Combo Box**: Choices textarea (monospace, `value: Label` format) + Allow Null switch
- **File**: Folder ID input
- **Content Select**: Folder ID input (key: `contentSelect`)
- **Multi Select**: Post Type dropdown + Folder ID input
- **Relator**: Relator title + recursive `AttrFieldList` for sub-fields

## Content Navigation (GitHub-like file browser)

### Site List Page (`/console/content`)
- Shows all sites as a list with hover actions (Download, Edit, View Site, Delete)
- Tabs: Sites | Tasks
- Search filter
- Click site → navigates to `/console/content/object/{siteId}`

### Object Children Page (`/console/content/object/{objectId}`)
Main navigation page. Shows folders and posts inside any object.
- **Breadcrumb bar**: clickable path (Viglet / Home / Developers)
- **Action bar**: New [PostType] + grid selector, New Folder, Import
- **Sort/Select/Batch** actions
- **Content list**: Each item as a card with position badge, checkbox, icon, name, date, type badges, hover actions
- **New Post**: "New [LastPostType]" button remembers last used (localStorage `sh-last-post-type`), grid icon opens post type selector page

### Post Type Selector (`/console/content/folder/{folderId}/post-type-select`)
Full page with grid of post types:
- System types in compact grid (6 columns)
- Custom types with description (4 columns)
- Alternative actions: "Or create a new Folder", "Or model a new Post Type"

### Post Edit Page (`post.edit.page.tsx`)
- Sticky save bar with post title, post type badge, field count badge, publish permission badge
- Save, Save & Close, Cancel buttons
- Card with `PostFormFields` rendering color-coded field cards
- Loading state with animated spinner
- Navigation: back to parent folder on save/cancel

### Post New Page (`post.new.page.tsx`)
- Same sticky save bar pattern as edit page
- Builds empty `ShPostAttr[]` from `ShPostTypeAttr[]` on the selected post type
- Derives title/summary from attrs with `isTitle`/`isSummary` flags on submit
- Navigates back to folder after creation

### Delete Dialog (follows Turing pattern)
`DialogDelete` component requires typing the item name to confirm deletion.

## Data Model Notes

### ShPost (from XP endpoint)
```typescript
{
  id, title, summary, furl,
  shPostType: { id, name, title },
  shFolder: { id, name, shSite: { id, name } },
  owner, date, published, publishStatus,
  shPostAttrs: ShPostAttr[]
}
```

### ShPostAttr
```typescript
{
  id, strValue, arrayValue, dateValue, intValue,
  shPostTypeAttr: {
    id, name, label, description, ordinal, required,
    isSummary, isTitle,
    shWidget: { name },
    widgetSettings?: string
  },
  shChildrenPostAttrs?: ShPostAttr[],
  shChildrenRelatorItems?: ShRelatorItem[]
}
```

### ShRelatorItem
```typescript
{
  id: string,
  title?: string,
  summary?: string,
  ordinal: number,
  shChildrenPostAttrs: ShPostAttr[]
}
```

### ShPostType (SH117 — the def shape, no ids, real booleans)
The console model mirrors the backend `ShPostTypeDef`. There is no `id`: a post-type is
addressed by `name`, which is why the editor routes on the name and `POST
/api/v2/post-unified` takes `postType`. The pre-SH117 entity shape (`id`,
`shPostTypeAttrs`, `shWidget: {id, name}`, `0`/`1` flags) is gone along with the endpoint
that served it.
```typescript
{
  name, title?, namePlural?, description?,
  workflowPublishEntity?: string,
  system?: boolean,
  managed?: boolean,   // delivered on read; on write it is a claim — see the API section
  fields?: ShPostTypeField[]
}
```

### ShPostTypeField
```typescript
{
  name, label?, description?,
  widget?: string,          // the widget NAME, e.g. "HTML Editor" — the catalogue is closed
  required?: boolean, isTitle?: boolean, isSummary?: boolean,
  ordinal: number,
  widgetSettings?: string,  // verbatim JSON, kept as a string so a round trip is lossless
  fields?: ShPostTypeField[]  // nested for Relator / Tab
}
```
A field has **no id**, so the post-type editor mints an editor-local `key` per row for
React/dnd identity and strips it before saving — `name` is what the user is typing and
cannot double as an identity.

## Roadmap & docs maintenance

The roadmap lives in `docs/`, **split across three files that must be kept in
sync**. Each has one job — never duplicate content between them, and when you
touch one, check whether a sibling needs updating:

| File | Single responsibility | Granularity |
|---|---|---|
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | **Active backlog only** — the source of truth for what's *unshipped* (📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress). | one line per task |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | What has **shipped** — a searchable "Shipped Ledger" indexed by Block (A–G); `git log` is authoritative for detail. | one entry per shipped task, under its block |
| [`docs/IMPROVEMENTS.md`](docs/IMPROVEMENTS.md) | **Design rationale** (the what/why) for *unshipped* sections only. No status tables, no shipped implementation reports. | prose per active section |

Tasks are numbered `SH<n>` (not contiguous across blocks — epics like SH25/SH62/SH67/SH74
own sub-ranges; superseded ids are retired, never reused). The `→ §x.y` pointer on a task
line is its IMPROVEMENTS.md design section. Per-task implementable specs live in
`docs/specs/SH<n>-<slug>.md`; the product concept is `docs/specs/SH74-agent-native-cms.md`.
Blocks **H–L, M, N and O** have shipped (Block L except **SH110**, still blocked on SH45);
the live backlog is the per-block follow-ups and Blocks A–G's remaining SH44–SH47 tenant work. Block N's
result is worth knowing before you quote a number: aligning the console reads on the CDA
shapes made the *baseline* lean, so the SH112 token ratios fell from 3.8×–23.3× to
2.6×–12.6× with the agent path unchanged — and to **2.1×–12.1×** once Block O's SH176 did the
same to the post read (SH164).

**The cross-file update rules — follow these every time:**

1. **When a task ships (mark it ✅):** move its full entry from `ROADMAP.md` →
   `CHANGELOG.md` (under its Block heading; the detailed shipped report lives
   *there*, not in the roadmap), and replace it in `ROADMAP.md` with **nothing**
   (delete it) or, if siblings still reference it or it's an epic header, a single
   terse pointer line: `> ✅ **<scope> shipped (SH..–SH..)** → see [CHANGELOG.md](CHANGELOG.md) Block X.`
   Then **delete** its design subsection from `IMPROVEMENTS.md` — `git log` is the
   history; do not leave a shipped implementation report behind. **The roadmap must
   never carry a multi-sentence ✅ task body** — that's the changelog's job. When an
   epic's children all ship, collapse the whole epic to one shipped pointer.
2. **When you add a new task:** add the one-liner to `ROADMAP.md` (with `deps:` +
   a `→ §x.y` pointer) and, if it needs design, add the rationale subsection to
   `IMPROVEMENTS.md`. Status markers (✅/📋/💭/⏳/🛠) live **only** in `ROADMAP.md`.
3. **Status belongs to exactly one file.** If a marker in `IMPROVEMENTS.md`
   disagrees with `ROADMAP.md`/`CHANGELOG.md`, the roadmap files win — fix it.
4. **Keep ROADMAP entries terse** (≈1 sentence: *what + why + pointer*).
   Implementation detail goes in the CHANGELOG entry, code, and commits.
5. **Non-goals are binding.** Check `ROADMAP.md` → "Non-goals" (and IMPROVEMENTS §0)
   before proposing new work.

## Committing

**When a task is done, commit by running `run-commit.cmd` from the repo root.**
It lives at `d:\dev\bin\run-commit.cmd` and is on the system PATH, so just invoke
`run-commit.cmd` (no path needed). It runs `git add *` then `ai_commit.py`, which
generates a Conventional-Commits message from the staged diff via the OpenAI API
(`OPENAI_API_KEY` must be set) and runs `git commit`. It does **not** push and
does **not** create a branch — it commits on the current branch. Don't hand-author
commits or run `git commit` manually for this repo; always use the script.

## Build & Deploy

### JavaScript workspace (pnpm)
The JS packages live in a **pnpm workspace** (`pnpm-workspace.yaml` + root
`package.json`): `shio-react` (console), `shio-client` (CDA core),
`shio-react-sdk` (React SDK, depends on the core via `workspace:*`),
`shio-model` (the post-types DSL — SH63), `shio-sections` (the page-section
vocabulary — SH95, depends on the DSL), `create-shio-app` (Next.js starter),
`cli` (`@viglet/shio`, the `shio` command — SH16) and `claude-plugin` (private;
the package exists so the plugin's manifests have a test — SH97). Run
`pnpm install` once at the repo root. **Node ≥26** — `shio-sections`' tests import
`.ts` directly and rely on native type stripping.
Native build scripts are allowlisted via `onlyBuiltDependencies` in
`pnpm-workspace.yaml` (`esbuild`, `@tailwindcss/oxide`) — pnpm 11 no longer reads
the `pnpm` field in `package.json`, so this setting lives in the workspace file.
`@viglet/viglet-design-system` resolves from public npmjs (no scope mapping needed).

### Maven Integration
`frontend-maven-plugin` (2.0.0) in `shio-app/pom.xml` runs, all from `../shio-react`:
1. `install-node-and-pnpm` (Node `v26.3.1` + pnpm `11.8.0`)
2. `pnpm install` — pnpm finds the workspace root (`../`) and installs all members
3. `pnpm run compile:build` (`tsc -b` + `vite build`)

**Skip the frontend build for backend-only work.** The three frontend executions
bind to `generate-resources`, so a plain `mvn compile`/`test` triggers the
(slow, Node-dependent) React build. For backend compile/test, always pass
`-Dskip.installnodepnpm=true -Dskip.pnpm=true` — these are the
`frontend-maven-plugin` per-goal skip flags and leave the Java build untouched:
`./mvnw -pl shio-app -am test -Dskip.installnodepnpm=true -Dskip.pnpm=true`.
(Still run the **full** suite, not a single `-Dtest` class.)

### Publishing to npm
`.github/workflows/publish-js-sdk.yml` (manual `workflow_dispatch`): bumps the
three publishable packages, builds, and runs `pnpm publish` in dependency order
(`shio-client` → `shio-react-sdk` → `create-shio-app`). `pnpm publish` rewrites
`workspace:*` to the published version automatically. Needs the `NPM_TOKEN` secret.

### Vite Config
- Output: `../shio-app/src/main/resources/public/`
- Proxy: `/api` and `/logout` and `/csrf` → `http://localhost:2710`
- Path alias: `@` → `./src`
- Chunk splitting: vendor-ui (radix), vendor-lib (other node_modules)

### Static Resources (Spring)
- `/store/**` → file_source parent directory
- `/file_source/**` → file_source directory
- `/thirdparty/**` → `classpath:/META-INF/resources/webjars/`
- `/console/**` → React SPA (permitAll in security)

## Database Schema (Liquibase, follows Turing)

The schema is owned by **Liquibase**, NOT by Hibernate. `spring.jpa.hibernate.ddl-auto=none`
in `application.properties` — never set it back to `update`/`create` for the app
(schema drift on `@TenantId` columns was the reason we moved off it).

### Layout
- Changelogs: `shio-app/src/main/resources/db/changelog/`
  - `db.changelog-master.yaml` — `include`s every versioned file in order (wired via `spring.liquibase.change-log`).
  - `v2026.2_baseline.yaml` — generated baseline (full `createTable` set incl. `TENANT_ID`).
  - New features add `vX.Y.Z.yaml` + one `include` line in the master.
- `shio-app/liquibase.properties` — config for the `liquibase-maven-plugin` (diff/generate only; the app applies changelogs at boot via `spring.liquibase.*`).
- Dev DB: `jdbc:h2:file:./store/db/shioDB` (relative to `shio-app/`).

### Load-bearing H2 URL params
The datasource URL **must** keep `;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true`.
Liquibase creates tables with unquoted mixed-case names (`ShPostTypeAttr`), while
Hibernate (`globally_quoted_identifiers=true`) queries them **quoted**
(`"ShPostTypeAttr"`, `"TENANT_ID"`). The params make the casing match — without
them you get `Column "..." not found` at runtime.

### Adding a migration
1. Hand-write a `vX.Y.Z.yaml` changeset (preferred for incremental changes — see
   Turing's `turing-app/.../db/changelog/vX.Y.Z.yaml` for the `addColumn` /
   `preConditions: onFail: MARK_RAN` pattern), **or** regenerate via diff:
   `mvn -pl shio-app org.liquibase:liquibase-maven-plugin:5.0.3:generateChangeLog -Dliquibase.url=... -Dliquibase.outputChangeLogFile=...`
   (invoke the plugin goal directly so the frontend build is skipped).
2. Add the `include` line to `db.changelog-master.yaml`.
3. Keep types **DB-agnostic** — `ShLiquibaseChangelogAgnosticTest` fails the build on
   `TIMESTAMP WITH TIME ZONE`, `CHARACTER LARGE OBJECT`, `BINARY LARGE OBJECT`,
   `BOOLEAN`, and `ENUM(`. Use `TIMESTAMP`, `CLOB`, lowercase `boolean`, and
   `VARCHAR` (map enums `@Enumerated(STRING)`) instead. **`ENUM(...)` is NOT
   allowed** — H2/MySQL/MariaDB accept inline `ENUM`, but PostgreSQL rejects it
   (`type "enum" does not exist`), so the SH26 cross-DB ITs would fail. The lint
   is only a textual guard; the real proof that the changelog applies on
   MariaDB + PostgreSQL is the SH26 Testcontainers ITs (see below).

### Cross-DB schema-drift ITs (SH26)
`ShMariaDbSchemaIT` / `ShPostgresSchemaIT` (extending `ShCrossDatabaseSchemaITBase`,
package `com.viglet.shio.tenant`) boot the **full Spring context** against a real
MariaDB / PostgreSQL Testcontainers engine with `spring.liquibase.enabled=true` +
`ddl-auto=none`, proving the full `db.changelog-master.yaml` applies, every entity
maps onto it (the `ShOnStartup` seed inserts run real DML), and
`ShTenantBackfillRunner.TENANT_TABLES` resolves to real tables. Run via `mvn verify`
(failsafe); auto-skipped without Docker (`@Testcontainers(disabledWithoutDocker=true)`).
Note: most tables are **PascalCase** entity names (no `@Table`, e.g. `ShPostTypeAttr`)
— the backfill list must use those, not snake_case, or it silently no-ops.

### Multi-tenancy (SH22 — core DB isolation)
Tenant isolation uses Hibernate's native `@TenantId` (DISCRIMINATOR), gated by
`shio.multi-tenant` (default `false`). `ShTenantFilter` resolves the tenant from the
OIDC `workspace_id` claim → username and sets `ShTenantContext` (ThreadLocal, default
`"shio"`); `ShTenantResolver` (a `CurrentTenantIdentifierResolver`) hands it to Hibernate.
`@TenantId` covers the active content tree: **`ShObject`** (the JOINED root — one `@TenantId`
there filters polymorphic *and* `ShSite`/`ShFolder`/`ShPostType` subclass queries; subclasses
need **no** column of their own), **`ShPostUnified`** (`tenantId` is **not** part of its
composite `@IdClass` — Hibernate adds `tenant_id=?` next to the `(id,state)` PK), and
`ShReference`, plus the 6 pre-existing ones (ShApiToken/ShSiteWebhook/ShPostTypeAttr/
ShRelatorItem/ShWorkflowTask/ShHistory). **Gotchas:**
1. **The discriminator column MUST be lowercase `tenant_id`.** Hibernate emits the
   `@TenantId` *filter predicate* UNQUOTED (`where x.tenant_id = ?`) even with
   `globally_quoted_identifiers=true` (only the SELECT projection is quoted). PostgreSQL
   folds the unquoted ref to lower-case, so an exact-case `TENANT_ID` column (what
   `QUOTE_ALL_OBJECTS` creates) is never matched (`column does not exist`). A default H2
   folds it UP instead, so a quoted lower-case `"tenant_id"` column would be missed there.
   Lower-case `tenant_id` + the load-bearing `DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true`
   H2 params (which both the dev DB **and the unit-test datasource** must carry) make all
   three engines agree. Backtick-quoting the column does NOT help — it quotes the projection,
   not the predicate. The baseline + `v2026.2.1` columns were renamed `TENANT_ID`→`tenant_id`
   with `validCheckSum: ANY`; unmapped legacy/draft tables keep `TENANT_ID` (never queried).
2. A populated table's new `tenant_id` must be added **nullable** and listed in
   `ShTenantBackfillRunner.TENANT_TABLES` so the startup backfill sets `'shio'` (a `NOT NULL`
   ALTER fails; a NULL `tenant_id` under live `@TenantId` is invisible).
3. The resolver resolves the tenant **once per session-open**
   (`validateExistingCurrentSessions()=false`), so tests that switch tenants must use a **new
   transaction per tenant** — see `ShTenantIsolationIT` (the proof that A can't see B; no Docker).

**SaaS follow-ups (shipped on top of the core):** a tenant **catalog** — `ShTenant`
(`sh_tenant`, deliberately **not** `@TenantId`, the global catalog) + admin CRUD on
`ShTenantAPI` (`ROLE_ADMIN`-gated writes; reserved `shio` tenant seeded by `ShTenantOnStartup`,
undeletable). **Delivery-path tenant resolution:** `ShApiTokenFilter` looks up the token via
`ShApiTokenRepository.findByTokenAcrossTenants` (native — the derived `findByToken` is itself
`@TenantId`-filtered, a chicken-and-egg) and propagates the token's tenant via
`ShTenantFilter.TENANT_REQUEST_ATTR`, which `ShTenantFilter` prefers over the principal — so CDA
delivery is scoped to the content owner's tenant. **Per-tenant provisioning:**
`ShTenantProvisioningService` copies the default model into a new tenant (read-under-`shio` +
write-under-new-tenant in separate txns; `ShPostTypeAdminService.provisionFromDef` preserves the
`system` flag `upsert` guards). **Cache:** `Vary: Key` on CDA responses. **Console:** a Tenants
admin page. **Gotcha (drift):** SH2/SH5 added `api_token.scope`/`.environment` + `api_token_site`
via `ddl-auto=update` with no changeset — invisible to the SH26 ITs (which never touch a token) —
so a clean Liquibase DB couldn't authenticate a CDA token; fixed in `v2026.2.6.yaml`. **Follow-up
(SH47):** per-tenant `file_source` storage — the upload/serve subsystem now ships (SH38, see
**Static files** above), id-keyed under `shio.file-source.path`; SH47 prefixes `tenantId` into
that layout from `ShTenantContext`.

### Tests
Tests skip Liquibase (`spring.liquibase.enabled=false`) and build the schema from
entity metadata via `ddl-auto=create-drop` on a fresh in-memory H2 per run.

## Common Issues & Solutions

### Optimistic Locking on Startup
`ShPostTypeImport` sets explicit IDs on `ShPostTypeAttr` via `setId()`. With cascade persist, Hibernate treats these as detached. Solution: use `ShAssignableUuidGenerator` (like Turing's `TurAssignableUuidGenerator`) that allows pre-assigned IDs.

### CSRF 403 on PUT/POST/DELETE
1. Verify `ShCsrfHeaderFilter` is added after `BasicAuthenticationFilter.class`
2. Verify `/api/v2/csrf` endpoint exists and is in `permitAll()`
3. Frontend axios interceptor must fetch token before mutating requests
4. Cookie must not have `Secure` flag in dev (HTTP)

### widgetSettings JSON.parse("null") Crash
`widgetSettings` may contain the string `"null"` from the backend. `JSON.parse("null")` returns JavaScript `null`, which passes truthy checks but crashes on property access (`null.file`, `null.choices`, etc.). Always guard parsed settings:
```typescript
const p = JSON.parse(raw);
return p && typeof p === "object" ? p : {};
```
The `parseSettings()` helper in `post-type.edit.page.tsx` centralizes this pattern.

### Vite "Outdated Optimize Dep" (504)
After installing/removing packages (`pnpm add`/`pnpm remove`), clear Vite cache: `rm -rf node_modules/.vite` and restart dev server.

### Static Files 404
- WebJars served from `classpath:/META-INF/resources/webjars/`
- Check Maven dependency resolution (transitive deps may override explicit versions)
- `store/file_source/` path must end with `/` in resource handler

### Full-text search returns nothing (SH131)

`ShPostUnifiedRepository.FULLTEXT_PREDICATE` needs a pre-lowercased, `%`-wrapped term.
Call the `search*` **default** methods, which normalize for you — the raw `@Query`
methods are suffixed `…Like` and take the pattern. Normalizing again before calling a
`search*` method searches for `%%term%%`, which matches nothing and looks like an empty
index rather than a bug.

### Unused Imports Fail TypeScript Build
`tsc -b` with strict mode flags unused imports as errors (TS6133). When refactoring (e.g., replacing `Checkbox` with `Switch`), always remove unused imports.

### `Column "..._.TENANT_ID" not found` (or any column) at startup
Schema drift. The schema is Liquibase-managed (`ddl-auto=none`) — a column referenced by an entity does not exist because no changeset created it. Fix by adding a Liquibase changeset (see **Database Schema** above), not by flipping `ddl-auto` back to `update`. Also confirm the datasource URL still has `DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true` — dropping those reintroduces quoted-vs-unquoted casing mismatches that surface as "column not found".

### A post or address comes back with no site (SH128 invariant)

**Only a site's root folder carries `shSite`.** `ShFolderWriteService` nulls it on every
child, so reading `folder.getShSite()` on anything nested silently yields `null` — a
site-less post, an address of `folder:null/...`, or a CDA allow-list check that denies
everything (`allowsSite(null)` is `false` under a token allow-list). Always resolve with
`ShFolderSite.of(folder)` / `.idOf(folder)`, which walks to the root:

```java
// ✗ null for every folder below the root — compiles, reviews clean, wrong at runtime
row.setShSite(folder.getShSite());
// ✓ walks the parent chain to the folder that actually carries the site
row.setShSite(ShFolderSite.of(folder));
```

`ShFolderSiteInvariantTest` fails the build on the wrong form. If a direct read is
genuinely what you mean (the exchange export records `shSite` root-only; the console XP
endpoint touches it to force a LAZY proxy), add a `ShFolderSite-exempt: <reason>` comment
on or directly above the line.

### `Column "spt1_1.tenant_id" not found` in a JPQL query (SH22 join hazard)
**Never navigate past the `id` of an association whose target is an `ShObject`
subclass** (`ShSite`, `ShFolder`, `ShPostType`). `@TenantId` lives on the JOINED
`ShObject` **root**, so an implicit association join makes Hibernate emit the tenant
predicate against the *subclass* table's alias — which has no `tenant_id` column —
and the query dies at prepare time. Cheap and reliable rule:

```java
// ✗ breaks: joins ShPostType, tenant predicate hits the subclass table alias
@Query("select p.shPostType.name, count(distinct p.id) from ShPostUnified p group by p.shPostType.name")
// ✓ works: uses the FK column already on sh_post_unified — no join at all
@Query("select p.shPostType.id, count(distinct p.id) from ShPostUnified p group by p.shPostType.id")
```

Resolve `id → name` in the service from the entity list you already loaded. The
id-only form is also faster (no join). This is invisible until runtime — the
mapping is valid JPQL — so it is a code-review item, not a compile error. Found
while building the SH77 context pack; see `ShPostUnifiedRepository`'s note.

## Color Theme
- Primary: Orange/Amber (vs Turing's Blue/Indigo)
- Gradients: `from-orange-500 to-amber-600`
- Badges: orange for folders, blue for posts, green for sites
- Sticky save bar: `bg-orange-50 dark:bg-orange-950/30`
- Widget types: each has a unique color (see Widget Color System above)

## i18n
- Languages: English (en), Portuguese (pt)
- Namespace files in `src/i18n/locales/{lang}/`
- Categories: common, content, admin, config, dialog, login, nav, sidebar
- Key naming: `namespace.camelCaseKey` (e.g., `content.fieldLabel`, `content.noFields`)
- Interpolation: `{{variable}}` syntax (e.g., `content.modelPostType: "Model your {{title}} Post Type"`)
- Always add keys to both `en` and `pt` locale files simultaneously
