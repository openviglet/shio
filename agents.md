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

### Content Navigation
- `GET /api/v2/site` - List all sites
- `GET /api/v2/object/{id}/list` - List children (folders + posts)
- `GET /api/v2/object/{id}/path` - Get breadcrumb path
  - Returns `{ breadcrumb: [...folders], shSite: {...}, currentFolder: {...} }`
  - Each breadcrumb item has `rootFolder: 1` for the site's root folder

### Posts (unified model only)
Content posts live in `sh_post_unified` (NOT the legacy split `ShPost`/`ShPostDraft`,
deleted in the nuclear refactor). All post CRUD is the unified API:
- `GET /api/v2/post-unified/{id}?state=DRAFT|PUBLISHED`, `/folder/{folderId}`, `/{id}/versions`
- `POST /api/v2/post-unified` (body `{ folderId, postTypeId, data, state }`)
- `PUT /api/v2/post-unified/{id}`, `POST .../publish|unpublish|restore|schedule`
- `DELETE /api/v2/post-unified/{id}`
- `GET /api/v2/post/xp/{id}` - resolve a referenced post for display (returns
  `{ shPost, allowPublish }`, DRAFT-preferred); used by Content Select / File widgets
- There is **no** `/api/v2/post` controller — do not add one.

### Object Operations
- `PUT /api/v2/object/copyto/{destFolderId}` - Copy objects (body: `[id1, id2]`)
- `PUT /api/v2/object/moveto/{destFolderId}` - Move objects (body: `[id1, id2]`)
- Clone = copy to same folder

### Folders
- `GET /api/v2/folder/{id}` - Get folder
- `POST /api/v2/folder/{parentFolderId}` - Create folder
- `PUT /api/v2/folder/{id}` - Update folder
- `DELETE /api/v2/folder/{id}` - Delete folder

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
- `POST /agent/batch` — **writes**: N addressed ops (`folder.upsert`, `post.upsert`,
  `post.publish`, `post.unpublish`, `folder.delete`, `post.delete`) in one transaction;
  `{"dryRun":true}` reports without writing.
- `POST /agent/plan` / `POST /agent/apply` — a declarative desired-state content
  document; `plan` never writes, and `changed:0` is the content drift guard.
- `GET /agent/find` — posts by site/type/folder-subtree/text; **addresses, not bodies**.
- `GET /agent/read?address=…&fields=…` — post content, DRAFT-preferred, projected.

### MCP — `POST /mcp` (SH75/SH83)
Same capabilities over the Model Context Protocol (JSON-RPC 2.0, revision `2025-06-18`),
hand-rolled in `com.viglet.shio.api.agent.mcp` — **not** the MCP Java SDK, which is
Jackson-2/Boot-3 while this app is Jackson-3/Boot-4. `GET /mcp` is a deliberate `405`
(this server sends nothing unprompted). `shio mcp` in the CLI is a stdio bridge:
`claude mcp add shio -- shio mcp`.
- **Tools** (`ShMcpTool` beans, auto-registered): `shio_context` · `shio_find` ·
  `shio_read` · `shio_write` · `shio_apply` · `shio_publish` · `shio_assets` ·
  `shio_changes`. `shio_verify` arrives with SH99. Every tool is a *shape* over a REST
  surface — if a tool needs new logic, the logic goes on the REST side first.
- **Resources**: `shio://manifest` · `shio://context` · `shio://schema/{postType}`.
- **Prompts**: `new-site` · `add-section` · `translate-site` · `audit-content`.
- **Error routing matters**: a fixable failure (bad address, unknown post-type) is a
  tool *result* with `isError:true` so the model reads the SH80 `fix`; a JSON-RPC
  `error` is only for an unusable message (unknown method/tool), which the client
  swallows. Getting this backwards throws away the teaching error.
- A tool-count test fails the build past 12 — tool schemas are context paid every turn.

Addresses (SH79): `post:<site>/<friendly-url>` · `folder:<site>/<name-chain>` ·
`site:<name>` · `id:<uuid>`. **Writes take only `post:`/`folder:`** — a folder path and
a friendly URL are separate namespaces, and a write that guessed wrong would create
content in the wrong place silently. CSRF-exempt (stateless callers), authenticated
like every other API; the SH54 ACL and SH22 quota apply on the agent path identically.

### Post Types — lossless admin API (SH16, used by the `shio` CLI)
- `GET /api/v2/post-type` - List all post-types as **lossless** `ShPostTypeDef`s (keeps `Tab`, verbatim `widgetSettings`, nested Relator fields — the authoring inverse of the lossy SH11 CDA schema)
- `GET /api/v2/post-type/{name}` - One def (404 unknown)
- `PUT /api/v2/post-type/{name}` - Upsert (idempotent by name); rejects system types (409), unknown widgets (400)
- `DELETE /api/v2/post-type/{name}` - Delete a non-system type (204/404/409)
- Console-authenticated (HTTP Basic) + **CSRF-exempt** (stateless CLI), like the CDA writes. **Cascade gotcha:** `ShPostTypeAttr` maps `shWidget`/`shPostType`/`shParentPostTypeAttr` as `@ManyToOne(cascade=ALL)`, so `ShPostTypeAdminService` detaches those before the orphan-removal replace — otherwise an update would cascade-delete the shared widget catalog.

### Post Types — console API (id-keyed, entity-shaped) — DIFFERENT from SH16
The React console (`ShPostTypeService`, post-type editor, "New [PostType]" pickers)
uses a **separate** surface at **`/api/v2/post/type`** (note the slash, not a hyphen):
- `GET /api/v2/post/type` (list) and `/{id}` (one) — return the **`ShPostType` entity**
  with its `shPostTypeAttrs` tree (NOT `ShPostTypeDef`). Reads run through
  `ShPostTypeConsoleWriteService.list/get` which initializes the LAZY attr graph
  inside a tx (open-in-view is off) — do **not** rely on `@JsonView` here: with
  `DEFAULT_VIEW_INCLUSION` off, activating a view *hides* the unannotated scalar
  fields. No `@JsonView` → all fields serialize, `@JsonIgnore` back-refs stay hidden.
- `POST /api/v2/post/type`, `PUT /{id}`, `DELETE /{id}` (id-keyed; system types 409).
- Backed by `ShPostTypeConsoleAPI` + `ShPostTypeConsoleWriteService` (`...api.post.type`),
  which reuses the same detach-before-replace cascade safety as the SH16 service.
- **This is session/CSRF-protected** (unlike SH16's `/api/v2/post-type`, which is
  CSRF-exempt). Two post-type APIs coexist on purpose: console (by id, entity) vs
  CLI (by name, lossless `Def`).

### `shio` CLI (`@viglet/shio`, in `cli/`)
The "JS dev, zero Java" command-line tool: `init` (scaffold via `create-shio-app`), `types` (SH11 codegen), `pull`/`push` (post-types as code → `shio/post-types/*.json`, `--dry-run`/`--prune`), `dev`, `deploy`. `pull`/`push` use console Basic auth (`SHIO_USER`/`SHIO_PASSWORD`); `types` uses a CDA token (`SHIO_CDA_TOKEN`).

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
Widgets are React components that render based on `shPostTypeAttr.shWidget.name`.
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

### ShPostType
```typescript
{
  id, name, namePlural, title, description,
  system: number, date, owner,
  workflowPublishEntity?: string,
  shPostTypeAttrs?: ShPostTypeAttr[]
}
```

### ShPostTypeAttr
```typescript
{
  id, name, label, description,
  ordinal: number, required: number,
  isSummary: number, isTitle: number,
  shWidget: { id, name },
  widgetSettings: string,
  shPostTypeAttrs?: ShPostTypeAttr[]  // nested for Relator
}
```

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
The current focus is **Blocks H–N** (the agent-native reorientation); Blocks A–G are
shipped except the SH44–SH47 tenant follow-ups.

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
`create-shio-app` (Next.js starter), and `cli` (`@viglet/shio`, the `shio`
command — SH16). Run `pnpm install` once at the repo root.
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

### Unused Imports Fail TypeScript Build
`tsc -b` with strict mode flags unused imports as errors (TS6133). When refactoring (e.g., replacing `Checkbox` with `Switch`), always remove unused imports.

### `Column "..._.TENANT_ID" not found` (or any column) at startup
Schema drift. The schema is Liquibase-managed (`ddl-auto=none`) — a column referenced by an entity does not exist because no changeset created it. Fix by adding a Liquibase changeset (see **Database Schema** above), not by flipping `ddl-auto` back to `update`. Also confirm the datasource URL still has `DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true` — dropping those reintroduces quoted-vs-unquoted casing mismatches that surface as "column not found".

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
