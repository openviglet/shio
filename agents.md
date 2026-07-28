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
  **Paged since SH165**, with the CDA's own `page`/`size`/`totalPosts` (so the envelope is now
  the CDA's exactly, which is what that test asserts): `?page=&size=&sort=title|date|modifiedDate&q=`.
  **Only the posts page** — child folders come back whole on every page, because subfolders are
  structure (the move/copy picker walks them) and `size=1` is how a caller that wants only the
  structure asks. Three rules worth keeping: the **order and the filter are the server's**, since
  a client-side sort over a paged listing sorts one page and reads exactly like a correct answer —
  and **`q` is the CDA's own `FULLTEXT_PREDICATE` (SH240)**: title, summary and stored field data,
  the same predicate the delivery search uses, so the console cannot come to disagree with it about
  what "matches" is (it started as a title substring, faithful to the browser's old local filter,
  which left the console quietly weaker than the API it fronts; folder names stay a substring,
  since a folder has no content);
  an unknown `sort` is a teaching **400**, never a fallback to the default, because a listing
  answered in another order returns other *rows*; and the DRAFT-preference is a **predicate**
  (`ShPostUnifiedRepository#pageConsoleRows`), not an in-memory dedup like the CDA's, so `LIMIT`
  and the count are the database's and a post with both rows takes one slot and is counted once.
  **The browser's `Select → Everything` pages the rest in before selecting (SH237)** — a selection
  is a list of ids, so "everything" has to be everything, or the delete dialog says "50 item(s)" on
  a folder of five hundred and the curator is left believing it is empty. Past `SELECT_ALL_CAP`
  (1000) it selects what it loaded and *says* so rather than pulling an unbounded folder into the
  browser, which is the response paging removed; `invert` stays deliberately page-scoped, since
  inverting a set that includes rows nobody has seen is not a gesture anyone means. The rule to
  keep: **the number in the confirmation dialog is the number that will be acted on.**
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
**Both ends are authorized (SH201).** These two used to check `requireWrite(destId)` and nothing
else, so write on any single folder was enough to relocate content *out of* a subtree the caller
could not write — a removal, from the owner's point of view — or to copy content they had never
been granted read on. A move now requires WRITE on every source, a copy requires READ, and the
checks run over the whole body **before** the write service starts, so a partly-authorized batch
is refused whole. The rule to keep is symmetrical: **the two surfaces enforce the same ACL, and
whichever is stricter is the correct one** — the agent path has checked both ends since
SH134/SH137, and where the console is looser the console is the hole, not the bar.

- `PUT /api/v2/object/copyto/{destFolderId}` — Copy objects (body: `[id1, id2]`).
  **A copy takes names that are free (SH202).** It used to re-slug the copy's `furl` from the
  source's title — the same title, so the same slug — and since "clone" is copy-to-the-same-folder,
  the curator's most casual action put two posts in one site on one URL: a `duplicate-url`
  **error** from `shio verify`, and an address that resolves to whichever row is reached first,
  so the *original* went unaddressable too. Now `Home` clones as `Home (2)` / `home-2`, and the
  same numbering applies to a copied folder's **name**, because a folder is addressed by its
  name chain (SH79) and two siblings called `docs` are the same defect one level up. Two rules:
  the **title moves with the URL** (a `-2` URL under an identical title leaves two rows the
  curator cannot tell apart, which is the symptom they actually see), but the two counters are
  **independent** — a title is unique per *folder*, a URL per *site*, so a copy into another
  folder of the same site keeps its title and moves only its URL. Sharing one counter was the
  first implementation and it renamed a page for a collision in a scope the curator cannot see.
  The suffix is numeric, not `(copy)`, because the server writes it into content the console
  renders in whichever language the curator picked.
  **A folder cannot be copied into its own subtree (SH203).** SH137 gave *move* that guard and
  argued copy needed none — "the new folder is not yet in the tree, so it cannot be its own
  ancestor" — which is true of the folder being created and false of the traversal that fills
  it: `copyFolder` saves the copy and then walks `findByParentFolder(source)`, so a destination
  inside the source makes the walk descend into the copies it is still making, and it never
  returns. Both refusals now live on `ShFolderWriteService` (`checkMovable` / `checkCopyable`),
  the one place that knows what a subtree is.
- `PUT /api/v2/object/moveto/{destFolderId}` — Move objects (body: `[id1, id2]`).
  **A move relocates and changes nothing else (SH139):** it keeps the post's `furl`
  (it used to re-slug it from the title, silently changing a published page's live
  URL), assigns one only when there is none, and delegates the post branch to
  `ShPostUnifiedWriteService#move` — the same writer `post.move` uses, so the console
  and the agent cannot diverge on what a move means. A cross-site move onto an
  occupied URL is a teaching **409**, never quietly uniquified.
  **The folder branch delegates to `ShFolderWriteService#move` (SH137)**, which is where
  the cycle and site-root guards live: this path had neither, and a folder re-parented
  into its own subtree does not fail — it detaches the subtree from the site root and
  sends the recursive delete round the loop. Both are **409**s, so the console dialog
  needs its error path (it has one now).
  **And the picker no longer offers them (SH203).** The move/copy dialog navigates *down*
  from the site root, so the folders inside the one being dragged are exactly the ones a
  curator scrolls into — and they answer 409. A picker that lists choices the server will
  refuse teaches the curator that the operation fails at random, so the folders in the run
  are shown **disabled** (not hidden — someone hunting for "Docs" needs to see that it is the
  thing they are dragging, not conclude the picker lost it) and the confirm button carries the
  reason. The division to keep is the one SH108's confirm flow already uses: **the client
  prevents what it can see, the server refuses what it must** — a concurrent move or a stale
  tree is still the 409's job. It needs nothing new from the server, because the dialog only
  ever walks downward: a row is unreachable exactly when it is one of the ids being moved, and
  disabling it means the walk can never get *inside* one. That is why there is no ancestor
  check, and why the same disabling covers **copy** as well as move.
- Clone = copy to same folder — which is the gesture SH202 is about; see `copyto` above.

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

### The renderer — page → layout → region (SH183)

`GET /preview/{site}/{url}` composes a page out of content the instance already holds, so a
site an agent built is visible with **no front end and no client setup** — which was the gap
the whole of Block P is about. Owner: `com.viglet.shio.render`. Production delivery is still
Next.js over the CDA (P10); this is **preview-grade** and the guards that keep it that way are
deliberate: a distinct route, `no-store` + `X-Robots-Tag: noindex` (a freshness lifetime is how
a preview quietly becomes infrastructure), authentication, and a banner the page's own
stylesheet cannot hide because it is inline-styled rather than classed.

- **The model is content.** `PageLayout` (`HTML`/`THEME`/`JAVASCRIPT`), `Region`
  (`HTML`/`JAVASCRIPT`) and `Theme` (`CSS`/`JAVASCRIPT`) are **system post-types created on
  demand** — `POST /api/v2/render/post-types`, idempotent, never touching an existing type
  (SH38's `File` precedent, SH152's provision-never-upsert rule). The endpoint exists because
  `PUT /api/v2/post-type/{name}` refuses a system type, so without it the render model would be
  reachable only by whoever can restart the JVM. Because they are posts, `post.upsert`, the SH68
  package, the file projection and `shio verify` all work on them for free; the 2018
  `openviglet/bootstrap-site` export renders with **no migration**.
- **A page finds its layout through `site.postTypeLayout`**, read as the flat
  `{"<post type>": "<layout title>"}` map the one shipped example used. The
  `ShSitePostTypeLayouts` array (`format`/`mimeType`/`cacheTTL`) is the extension it evidently
  was and is **not** paid for up front — a non-string value is skipped, so the refusal names the
  post-type instead of a page rendering with a layout nobody chose. Layouts, regions and themes
  are addressed **by title**, because that is what `sh-region="NAME"` and a binding both name.
- **`sh-region` survives the render**, joined by `data-shio-region` and
  `data-shio-region-post`. Do not "tidy up" by stripping it: the rendered DOM is the only place
  the Universal Editor can learn which node maps to which post, and the attribute has been in
  the content since 2018. A slot with no Region post is **marked**
  (`data-shio-region-missing` + a constant comment), because a missing region and an empty one
  are different mistakes — SH169's rule. The marker never interpolates the region name: a name
  is content, HTML comments decode no entities, and `-->` inside one would close it.
- **The Universal Editor has a counterpart, and the protocol is a document (SH185)** —
  [docs/specs/SH185-universal-editor-protocol.md](docs/specs/SH185-universal-editor-protocol.md).
  `visual-editor.page.tsx` had posted `sh:*` messages into an iframe since SH10 with nothing in
  this repository on the other side. `shio-editor.js` is that side, served at
  `/preview/_shio/editor.js` and injected only for `?editor=true`, so an ordinary preview ships
  no editor code. Field-level annotation joins `sh-region`: **`sh-field="TEXT"` →
  `data-shio-prop`, `sh-post="{{id}}"` → `data-shio-post`**, with the page's own post stamped on
  `<body>` so the common case needs no `sh-post`. **Ownership is the template's job and the
  engine never infers it** — a region renders against the *page's* model, so `{{TEXT}}` inside
  it is the page's while inside a `{{#query}}` block `{{title}}` is the listed post's, and the
  rendered HTML records neither. Resolution is nearest-ancestor. Both attribute forms survive:
  `sh-*` is a contract the content declares (SH192 lints it, SH193 will graph it), `data-*` is
  what the browser consumes. **Neither side of the bridge uses `"*"`** — the console pins the
  iframe's origin and ignores anything else; the page is told its parent's origin on the script
  tag, because `document.referrer` is empty on some navigations and `window.parent.location`
  throws cross-origin. The script is **inert when not framed**. Injection happens *after* the
  render (`ShEditorBridge`), never inside `ShPageRenderService`, so the renderer keeps returning
  exactly what a front end would serve — the separation `ShPreviewBanner` also keeps.
- **Three rules on `ShTemplateEngine`, all asserted by `ShRenderNoScriptEngineTest`**, because
  each is a default or a dependency away from being undone and **none of them changes the
  rendered output when it breaks**. (1) **No script engine**: Handlebars.java declares
  `nashorn-core` at *compile* scope for its JS-helper path and the POM **excludes** it — the
  classes are not on the classpath, which is stronger than FSP's option of a `ClassFilter` it
  never passed. (2) **`MapValueResolver` only**: the default chain includes
  `JavaBeanValueResolver`, under which a template handed an entity walks the persistence model.
  (3) **A refusing `TemplateLoader`**: the default reads the classpath, so `{{> application}}`
  in a layout — which is content — is a file read.
- **A template is refused when it is written (SH192)** — `ShTemplateValidator` compiles a
  `PageLayout`'s or `Region`'s `HTML` on `post.upsert`: a real run **400s** with the line, the
  column and a caret (FSP's `regionError` output, which this product had in 2019), a `dryRun`
  **reports** the same thing as a plan note, following SH168's rule that a plan must not die on
  the first defect. Two boundaries, both deliberate: **an ordinary post's markup is not
  compiled** (an `Article`'s `TEXT` may legitimately contain braces), and **an unknown section
  is not flagged in general** — `{{#items}}` is a valid iteration and only the three
  known-missing helpers (`search`/`form`/`getRelation`) are named, because a rule with false
  positives is one authors learn to ignore. Handlebars catches a *parameterised* unknown helper
  itself; the scan covers the paramless form it cannot. Unknown **field** references cannot be a
  write-time check at all — a Region's post-type declares `HTML`/`JAVASCRIPT` while the fields its
  template interpolates belong to whatever page is bound to the layout that slots it — so they are
  a **lint**: `template-unknown-field` in `checks=content` (**SH251**).
- **`template-unknown-field` walks the bindings, not the pages (SH251)** — `ShTemplateFieldScanner`
  (pure: text → references with their *scope*) plus `ShTemplateFieldLint`. The resolution does not
  depend on the page: `postTypeLayout` is keyed by post-type and a layout's slots are static
  `sh-region` attributes, so every page of a type resolves to the same layout and regions. A
  thousand articles resolve once, the finding is addressed at the **layout or region post** (the
  artefact you edit), and SH143's scoping works for free because a finding is emitted only when
  that post is one of the run's subjects. **Two severities**: a top-level `{{TEXT}}` belongs to the
  page's post-type, which is known, so an undeclared name is an **error**; inside `{{#query}}` the
  rows are whatever the helper found, so the strongest honest claim is "no post-type in this
  instance declares this name" — a **warning**, which is SH99's line that `ok` is false only for
  what the CMS can prove from inside itself. Row values (`title`/`link`/`summary`/…) are never
  reported.
  **The scope is the whole point**, which is why this is not `Template#collect(TagType.VAR)` —
  Handlebars.java's own collector returns every variable flat and cannot tell the page's `{{TEXT}}`
  from a listed post's. **Do not "simplify" the false-positive exclusions**: frames
  (`post.`/`site.`/`theme.`/`render.`), `this`/`./`/`../`, data variables, subexpressions,
  literals, partials, comments, a block's own name, and an inline helper's name —
  `{{file_url HERO}}` reports `HERO`, and reporting `file_url` would fire on the one helper the
  shipped 2018 region used. A block's first positional argument is reported in the **enclosing**
  scope, since `{{#if TEXT}}` and `{{#getRelation SECTIONS}}` ask about the current one. Two known
  limits: Handlebars' `{{~TEXT~}}` form had to be stripped explicitly (left alone it silently
  *drops* the reference), and a **hash** argument that is a field (`{{#search q=TOPIC}}`) is still
  unchecked — **SH261**. The helper names are restated in five places and nothing checks that they
  agree — **SH260**.
- **A layout's and a region's blast radius is computed, never stored (SH193)** — `ShTemplateGraph`
  inverts the presentation edges (layout → the post-types bound to it, region → the layouts that
  slot it → their types → page and published counts). **Computed on demand and not extracted into
  `ShReference`**, which §XV.11 proposed and which is not available: `ShReference` joins
  `object_from`/`object_to` to `ShObject`, and a post is **not** an `ShObject`. It is also the
  better answer — an extracted edge goes stale when `site.postTypeLayout` changes, which is an
  `ShSite` write no post-level extraction would see, and a computed graph needs no migration, so it
  is right for an imported 2018 package immediately. Cost is bounded by *bindings*, not pages
  (SH251's finding). Two surfaces, no new contract: **`affects` on `/agent/changes`** for a row
  whose object is a `PageLayout` or `Region` — absent everywhere else, so an ordinary feed stays
  byte-identical, and it rides `shio_changes` because the MCP tool count is capped at 12 with 11
  used — and **`unused-region`/`unused-layout` warnings in `checks=content`**, which are warnings
  because a region waiting for its layout is work in progress and a gate that failed on that is a
  gate people bypass. `published` is counted separately from `pages`, since "how many are
  published" is what decides whether an edit is safe now. The **pre-edit** form of the question has
  no surface yet — **SH262** — and a **duplicate template title** silently resolves to whichever row
  comes back first — **SH263**.
- **Helpers are the closed capability set** `shObject.js` expressed as a convention:
  `{{#query folder= type= limit=}}`, `{{#navigation folder=}}`, `{{file_url}}`, and since SH247
  `{{#getRelation FIELD}}` and `{{#search q= type= limit=}}`. Every listing is sorted by a
  **total** order (date *plus* an id tiebreak) and the model is built with `ShStableMap` — a
  render that reorders itself moves SH101's digest for a change nobody made.
  **Every listing helper yields the same row shape** (`ShRenderHelpers#row`), which is what makes
  a card body written for `{{#query}}` work for a relation or a search result; the DRAFT-preference
  dedup and the comparator are shared for the same reason — a second copy of the preference is a
  second chance to get it backwards, and that reads as a preview showing the live page while the
  curator hunts for their unpublished change. `getRelation` takes the field's **value** (so it
  needs no post-type) and resolves each entry **by id, then by friendly URL in the current site**
  — SH181's order; a dangling entry is **skipped**, not marked, because SH154's
  `dangling-reference` lint already reports it and one defect in two reports is two reports that
  can disagree (the rule: *mark what only the renderer can see, leave to the lint what the lint
  reports*). `search` is SH131's `FULLTEXT_PREDICATE` through the repository's `search*` defaults
  (normalizing twice searches `%%term%%` and matches nothing), its `type` filter resolves to an
  **id** before the query — `p.shPostType.name` in JPQL trips the SH22 tenant join hazard — and an
  unknown type yields nothing rather than the whole site. It is **not** a `?q=` results page: the
  model holds no request, on purpose (SH183), and that is **SH259**.
  **`form` is the last of the seven and is deliberately unregistered** (SH247): a helper that
  silently renders nothing is worse than Handlebars' unknown-helper error, which carries a line
  and a column — and its absence is a *decision*, since a form needs somewhere for a submission to
  go. `ShTemplateValidator.KNOWN_MISSING_HELPERS` is down to `form`, and
  `ShTemplateValidatorTest` asserts **both** directions: `{{#form}}` still refused,
  `{{#getRelation}}`/`{{#search}}` now accepted. That pairing is why the validator test is
  Spring-wired — it exists so the validator and the renderer cannot disagree about which helpers
  exist, in either direction.
- **A region's `JAVASCRIPT` is never evaluated in the JVM, and is shipped to the browser
  (SH248)** — that split is the whole resolution of the Block D non-goal, and SH183 landed
  only the server half, so the field briefly accepted code with no effect. The composer now
  emits it **inline** (the page is `no-store`, so a cacheable route buys nothing and adds
  something that can 404) and **once per region name** (a layout may slot a nav in the header
  and the footer; the markup is wanted twice, the initialisation is not). The body is a jsoup
  `DataNode`, not text — as text, `if (a && b)` escapes to `&amp;&amp;` and the script is a
  syntax error.
- **`ShSiteFrontEnd` is the one place that answers "where can a person look at this site" (SH184)**
  — the configured `site.url`, or Shio's own `/preview/**` when there is none. It returns a
  **record, not a string**, because the two differ by a *token*: an external front end needs
  one to turn its own draft mode on at `/api/preview`, while `/preview/**` is already
  console-authenticated and already draft-preferred, so minting for it would issue a credential
  that unlocks nothing. `ShPreviewTokenAPI` therefore returns a **null `token`** in that case
  and `previewUrl` is the field always present. The fallback is **resolved, never stored**:
  writing `/preview/<site>` into the column would make every later reader unable to tell a
  placeholder from a real deployment, and would leave no empty value for `site.upsert` to fill.
  The console mirrors it in `shio-react/src/lib/site-front-end.ts` — both **View Site** buttons
  and the editor's iframe share it, since three inline copies of one rule is a rule that will be
  right in two of them.

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

**A folder delete is reversible, and it is a delegation (SH200).** It used to clear the subtree
with `shPostUnifiedRepository.deleteAll` — the repository, not the post writer — which skipped
the SH55 trash, the SH51 audit and the `DELETE` lifecycle event (so no webhook, and SH38's byte
cleanup never ran) in one line. That made the *most* destructive op on the surface the *least*
recoverable and the least observable, which is the inversion the task was filed for. Now every
post beneath it goes through `ShPostUnifiedWriteService#delete`, so a folder delete means exactly
what N post deletes mean, and `ShFolder.deleted_date` is stamped down the subtree instead of rows
being removed — the folder rows have to outlive the gesture, because a trashed post keeps its
`folder_id` and the `sh_post_unified.folder_id → ShObject` FK is `onDelete NO ACTION`. **The FK
ordering that motivated the old direct `deleteAll` is satisfied at purge time** (`ShFolderWriteService#purge`),
which is also where the lifecycle event fires — SH55's rule that a reversible trash has no side
effects. Beside it: `GET /v2/folder/trash` (one row per deleted *section*, not per folder in it),
`POST /v2/folder/{id}/untrash`, `DELETE /v2/folder/{id}/purge`, and the console's trash page lists
sections and posts together.
- **Every folder read excludes a trashed folder**, in `ShFolderRepository` — its derived finders
  are spelled out as JPQL with `deletedDate is null`, keeping their names so no call site changed.
  There is deliberately **no `@SQLRestriction` on `ShFolder`**: it would also fire when a
  `@ManyToOne` to a folder is initialized, and a trashed post's `getShFolder()` is precisely what
  the trash listing reads. Ids still resolve polymorphically through `ShObjectRepository`, which
  knows nothing about this — the three paths that matter guard it by hand, and **SH226** is the
  open task to make that an invariant rather than a convention.
- **A restore returns what *that* deletion trashed** — the delete stamps one instant down the
  subtree and anything marked earlier stays in the trash. Compared on loaded `Instant`s, never in
  SQL: timestamp equality is a fractional-second promise H2, MariaDB and PostgreSQL do not make
  the same way.
- **The audit records the folder *and* each post**, which is what lets the SH109 queue undo a
  section in one entry — "restore the section" is the gesture a human means. `/agent/changes`
  resolves a folder address for such an entry (through the trash, since the folder is deleted by
  then): an entry nobody can address is a change nobody can act on.

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
  first**; unshipped capabilities are reported `false`, never omitted. **Projectable since
  SH174**: `?include=identity,features,limits,endpoints,start-here` and `?format=json|terse`,
  with an unknown `format` a teaching 400 the way `/agent/context` has always answered.
  `include=features,limits` is the capability check and measures **298** against the full
  document's **2343** — the read this exists for, since no `304` helps on a session's first
  call. `format=terse` saves only 11%, and that is the finding rather than a defect: this
  document's weight is prose (a purpose sentence and a runnable example, twenty-nine times),
  kept verbatim because an abbreviated example is a call the instance would reject (SH230).
- `GET /agent/context` — model + sitemap + conventions in one call. `?format=terse`,
  `?include=`, `?budget=`, `?from=`, `?ids=true` (rows are **id-free by default**, SH121).
  The `digest` is how a later session skips re-reading. **`include=all,manifest` folds the
  manifest read into this one (SH157)** — it adds `capabilities` (features, limits,
  startHere) and deliberately *not* the endpoint index, which is ~87% of the manifest,
  changes only when the build does, and is what SH116's ETag already makes free. `manifest`
  is **the one section you must name**: `include=all` still means all the sections of the
  *pack*, so no session pays for a document it already holds. Capabilities are attached
  **after the digest is taken** — a build number must not move a content digest, or an
  upgrade sends every agent re-reading every sitemap.
- `GET /agent/resolve?address=…` — an address → an object id, for id-keyed endpoints.
- `POST /agent/batch` — **writes**: N addressed ops (`site.upsert`, `folder.upsert`,
  `folder.move`, `post.upsert`, `post.move`, `post.publish`, `post.unpublish`, `folder.delete`,
  `post.delete`) in one transaction; `{"dryRun":true}` reports without writing, and a batch that deletes needs
  SH108's `confirm` from its own dry run. **`?verify=true` returns the run's own proof
  (SH157)** — the SH99 lint over exactly the addresses it touched, which removes the closing
  turn of every write-then-check loop and is better than a separate `/agent/verify` because
  it is scoped to the change rather than the site. It runs *after* the write transaction
  commits (inside it, the lint would read state a rollback is about to discard), a **dry run
  gets none** (linting a state the plan has not created reads as "the plan is clean"), and an
  op that wrote no fields has its address named, because a finding against it predates the
  run. Only `post:` addresses are linted today (SH228). **`?format=terse` since SH157** —
  the write surface was the one part of this namespace without one. **A `post.upsert` refuses a `data` key the post-type
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
  **`folder.move` (SH137) is the same rule one level up, and the more important one** — the
  workaround it replaces is `folder.upsert` + `folder.delete`, and that delete takes every post
  beneath it. `to` is the folder's **whole name chain** (that is what addresses a folder), so
  its last segment is the new name and rename/relocate are one op. **No post's URL changes**: a
  friendly URL is site-unique and independent of the folder chain, so the pages inside a moved
  section keep their addresses — which is why the op is deliberately **not** destructive and
  needs no `confirm`. Making it demand one would push callers back to the pair it replaces.
  Its four refusals (cross-site, occupied chain, a site root, and a destination inside the
  folder's own subtree) live on **`ShFolderWriteService#move` — the one folder-move writer,
  which the console's `/moveto` also calls** (SH139's rule). Do not reintroduce a local
  re-parent on either side: the console's had no cycle check, and since its move dialog
  navigates *down* from the site root, a curator could pick a child of the folder being
  dragged, which succeeds and detaches the subtree from the root. `checkMovable` is how a
  `dryRun` reports those refusals without writing.
- `POST /agent/plan` / `POST /agent/apply` — a declarative desired-state content
  document; `plan` never writes, and `changed:0` is the content drift guard. `prune`
  deletes undeclared **posts and folders** inside the declared folders' subtrees (SH135),
  posts first and folders deepest-first; declared folders, their ancestors, any folder
  a declared post calls home, **and the source of every declared move (SH150)** are protected.
  Also `shio apply <file>` in the CLI (`--dry-run` / `--check` / `-` for stdin).
  **`moves` is the document's one intent channel (SH150)** — `{from, to?, folder?}`, compiled to
  `post.move` after folders and before upserts. Everything else here is state, which is what
  makes an apply re-runnable; a rename is *not* state, so declaring one as state alone is a
  delete plus a create that loses the post's id and history. A move naming neither `to` nor
  `folder` is a teaching **400**, not a noop — `changed:0` reads as "already in the right
  place", so a silent noop is how a caller comes to believe a rename happened.
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
  **A cut is a page, not a dead end (SH146):** a `truncated` response carries a `cursor` — the
  last address it returned — and `?after=<cursor>` continues the walk. **Keyset, never an
  offset**, because content is written while the walk runs and a post created ahead of an offset
  shifts every later page by one, dropping a row from a projection silently. The cursor is the
  address because the result is *already* sorted by address and an address is unique, so paging
  costs no second sort key. Three rules: the cursor is **absent on the last page** (that absence
  is how a caller knows the walk finished, instead of asking for an empty page); an `after` that
  is not a `post:`/`id:` address is a teaching **400**, since lexicographic comparison against a
  row number or a timestamp *succeeds* and returns a plausible page; and `truncated` **with no
  cursor** stays meaningful — it is the pre-SH146 dead end, which is why `shio pull --content`
  refuses on that and pages on the other. The client side is `agent-client.mjs`'
  **`findAll`**, which also stops on a cursor that did not advance rather than paging forever.
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
  **`checks=delivery` proves a route instead of resolving it (SH141)** — the one group that
  leaves the process. It fetches each published page and each unresolved link from the site's own
  URL through `ShAgentDeliveryProbe`, the single guarded fetch path SH101 built, and turns "the
  front end does not serve this" into `route-not-served` / `link-broken` **errors**. It is in the
  default set **only when `shio.agent.render.allow-fetch` is on** — naming it otherwise is a
  teaching 400, because a check that silently covered nothing while answering `ok` is the same
  false green an unresolvable `folder=` would be. Three rules worth keeping: an unreachable front
  end is **one** `delivery-unreachable` per site and ends that site's pass (fifty copies of "it is
  down" is a report nobody reads, and its other pages are *unknowable*, not broken); a site with
  no URL is a **warning** when the group arrived by default and a **400** when the caller named it
  (a group that joined on its own must not fail a call that never asked); and the pass is bounded
  by `shio.agent.verify.max-fetches` + `fetch-budget-millis`, with every cut in `notes`.
  When delivery runs, `routes` **hands it the unresolved links instead of warning** — the same
  defect reported once as a maybe and once as a fact is a report the reader has to de-duplicate.
  The legitimate exception is **declared, never guessed**: `ShFrontEndRoutes` reads glob patterns
  from the SH114 memory key **`frontend-routes`** (`*` within a segment, `**` across; scoped to a
  site or instance-wide), and a declaration **silences a guess but never a proof** — with fetching
  off the link is not reported and the count goes in `notes`; with fetching on it is still
  requested, and a declared route the site 404s is an error, because the declaration made a claim.
  A pattern that is not a path is dropped rather than compiled, since the dangerous failure mode
  here is one that matches everything.
- `GET /agent/render?address=…` — a page's **structural** digest (SH101): heading outline,
  landmark counts, links, images with alt, word count, plus a `digest` hash over the
  structure only (a typo fix does not move it; a lost heading does).
  **Since SH249 it reads the *rendered* page** (`source: rendered`) whenever the post-type has an
  SH183 layout binding — the artefact a visitor gets, and the only one that reflects regions *and*
  sections at once. Before that, this endpoint composed sections (SH169) while `/preview` composed
  regions, and neither knew the other existed, so a structural proof and a human looking at the
  page could describe two different compositions. The decision behind it is a document —
  [docs/specs/SH249-sections-and-regions.md](docs/specs/SH249-sections-and-regions.md): **two
  mechanisms, one composition path.** A region is named by a layout, by title, and varies with the
  post-*type*; a section is named by a page, by URL, in order, and varies with the *page*; they meet
  because a region's template iterates the field (`{{#getRelation SECTIONS}}`), so **sections are
  content a template reads, not a level of the render model.** SH169's composition survives as the
  **labelled** fallback (`source: content`) for a post that cannot be rendered — no binding, or no
  friendly URL (SH172's `id:` case) — because an unbound type is the normal state of a site
  mid-build and a digest that failed there would break the SH99 loop; `source` is in the response so
  a fallback is never mistaken for the real answer. Every render refusal is a fallback and **never**
  an error, and the catch is narrowed to `ShTeachingException`. `ShPageRenderService.render` is
  `noRollbackFor = ShTeachingException.class` for SH171's reason — catching a teaching exception from
  an inner `@Transactional` otherwise marks the caller's transaction rollback-only and blows up at
  *commit*, nowhere near the catch (it cost eleven unrelated test errors when SH249 landed).
  **A rendered digest also carries `appearance` and a second hash, `appearanceDigest` (SH187)** —
  the class inventory, the classes no stylesheet the page carries defines, inline `style=` counts,
  images with no declared size, and the external sheets that explain the rest. It is **not** a
  rendering: computed style and box geometry need a layout engine, and that limit is the design
  rather than a gap (the raster half is **SH265**). The unstyled-class list is **advisory, never a
  lint finding** — the 2018 `bootstrap-site` package loads Bootstrap from a CDN, so a rule reporting
  every class the `Theme` lacks would fire dozens of times on the one real example in the repo.
  **Two hashes on purpose**: `d:` covers structure and `a:` covers appearance, because one hash over
  both could not tell "I restyled it" from "I broke the outline" — the comparison an iterative loop
  needs, and the half of SH194 that is now done. Both are **absent** for a `source: content`
  fallback (a markup field is not a document and has no stylesheet). The CSS is read out of the
  rendered page's own `<style>` blocks, not from the `Theme` post, so it is whatever the page
  actually carries. The manifest reports `appearanceDigest: true` and keeps
  **`visualSnapshot: false`**, the only honest pair — there is still no raster here, and an agent
  reading `true` would hunt for a PNG. `?url=` digests the
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
  transforms, scheduled publishes, 5xx. Recorded by `ShAgentIncidentLog`; repeats fold into one
  row with a count. **Durable since SH144** (`sh_agent_incident`, written by
  `ShAgentIncidentStore`), so "was anything failing while I was away" is answerable and an empty
  report now means *nothing has failed* rather than *nothing is remembered* — `since` is where
  the record starts (**absent** when empty) and `dropped` changed meaning to *could not be
  written down*, the only way one is lost now. Three rules to keep: the store writes in
  **`REQUIRES_NEW`** (several callers are inside a transaction that is about to roll back, and an
  enlisted row would be destroyed by the very failure it records) and **never throws** (a
  diagnostics log that turned a webhook timeout into a 500 would be worse than none — a failed
  write is counted and reported instead); a row is keyed by **what went wrong**, not by each
  occurrence, which is what makes the table grow with the number of *problems*; and the feed
  position is **`lastSeen`**, so an incident that is still happening is re-delivered with a
  higher count rather than being reported once, ever.
  **Incidents also ride `/agent/changes` (SH144)** in their own `incidents` array, so one
  `since=` answers what changed *and* what broke. Not a `Change` with an invented `action` —
  every existing caller branches on `action` and would file a failure as content. The
  load-bearing rule: **an incident may repeat, a change may never be lost.** Two streams paged
  under one scalar cursor cannot both be exactly-once, and taking the *later* of the two
  positions — the obvious move — silently advances past unread audit rows whenever an incident is
  ahead of the page being walked. So the cursor advances past an incident only when the change
  stream reports no `more`; until then it stays the audit's and those incidents are read again,
  which costs nothing because rows are folded. Incidents carry **no site**, so `site=` does not
  narrow them and the `notes` say so.
- `GET/PUT/DELETE /agent/memory` — **the instance memory (SH114)**: keyed notes that come
  back in the context pack's `conventions`, attributed to whoever wrote them. Upsert by
  `(tenant, scope, key)`, so re-stating a convention rewrites one row; `scope` is a site name
  or `""` for the whole instance (empty, never `NULL` — a unique index does not treat `NULL`s
  as equal). Deliberately **not** content: read on the hot path, never delivered, and it must
  survive an SH82 `prune`. Written three ways: the **`shio_remember` MCP tool** (SH160),
  `shio remember`, or the endpoint. SH114 argued against the tool and was right about Claude
  Code — a schema is paid every turn and a coding agent has a shell — and wrong about a pure
  MCP client, which could read the memory and never write one; P1 wins because the memory is
  the only mechanism that makes session N+1 cheaper than N, and a store nobody can write to
  stays empty. **A site's notes travel with it (SH161)**: the SH68 package carries them and
  re-scopes them on import (a note names its site by name, and an import renames the site),
  and `shio pull/push --content` projects them to `shio/content/<site>/.memory.yaml` with the
  same three-way merge the content tree uses. Only the *site-scoped* notes — an instance-wide
  note describes the installation, and importing another instance's house rules is a P7
  surprise (SH231 revisits that for a whole-instance move). Two rules on the file side: no
  file at all means "never projected", never "forget everything", so a first push from a
  fresh clone cannot wipe a site's memory; and notes are applied **outside** the desired-state
  document, because a note is not content and `--prune` must never reach it.

**Byte stability (SH136).** `Map.copyOf`/`Set.copyOf` iterate in an order derived from a salt
set **once per JVM**, so they are a silent re-ordering wearing a defensive copy's clothes: the
same data serializes to different bytes after a restart, and any hash over that iteration moves
with it. Use **`ShStableMap.copyOf`** wherever the order is observable — serialized, hashed or
rendered — and `ShByteStabilityLintTest` fails the build otherwise (`byte-stable-exempt:
<reason>` is the hatch, for a keyed lookup that is never iterated). Two rules that came out of
it: **a hash sorts its own inputs** rather than trusting the caller (SH101's digest does now),
and **you cannot catch this class with a behavioural test** — twice-in-one-process comparison
passes over broken code, which is why the guard is a lint and why the digest was restructured
until order-independence became assertable.

**Conditional reads (SH116).** `manifest`, `context` and `blueprints` carry strong ETags;
`If-None-Match` on an unchanged instance answers `304`, which measures as **0** on
`X-Shio-Est-Tokens`. A tag names a **representation**, not a state — the SH77 digest covers
content only, so JSON and terse share it and every tag mixes in `format` + `ids`. Responses
say `Cache-Control: no-cache` (store it, ask first), never a freshness lifetime. The rest of
`find`, `read`, `changes` and the SH16 post-type listing joined them in **SH159**, tagged
over the **rendered body** rather than a content digest — those four have no digest of their
own, and four hand-derived ones would be four things that can quietly disagree with what was
sent. Hashing the bytes also gets the representation rule for free: a two-field projection
and a whole document are different bytes. `changes` revalidates in **cursor** mode and not in
window mode, which is correct — without `since`, its own cursor is the moving edge of the
feed (SH232).

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
**Both boot their own in-memory database**: several of the responses they measure describe the
*instance*, so on the shared test datasource the numbers grew with whatever post-types other
tests created — the task benchmark measured 1616 tokens alone and 2243 in a full run against an
1800 ceiling, which is a red build nobody caused.

**And it is attributed per source (SH164, corrected by SH239).** `ShAgentSourceAttributionTest`
isolates the five ideas a task ratio bundles — addressing, projection, batching, discovery, lint —
one pair each, with a floor each (`attribution.*` in the same file). It exists because the task
ratios fell three blocks running while the agent path stood still (SH117/SH120/SH176 each made the
*baseline* leaner), so one ratio per task could no longer tell "the protocol is cheaper" from "the
old console was fat". Measured, order-independent: **the lint carries the token claim — 15.5× on
five posts and 139.6× on forty-five, the only source that scales without bound** (one call against
one read per candidate, 45× on calls); **addressing is 11.8× and scales sublinearly** to 53.1×,
because the baseline is a listing *plus* a lean id-keyed read and since SH165 that listing is
paged, so the growth caps at one page; **batching buys turns, not bytes** (5.0× calls, 6.1×
tokens); **discovery is a round-trip claim and almost nothing else** (4.0× calls, **1.2×** tokens —
a context pack describes the instance, so it is not much smaller than the reads it replaces, and
its call ratio grows by one per folder); **projection is the smallest (1.5×)** and the easiest to
point at. Its pairs are deliberately generous to the baseline — handed folder, type and post ids it
could only have crawled for — so each ratio is a **floor** on the advantage.
**SH239 also corrected SH164's own first numbers**, which were order-dependent: the batching pair
wrote into the folder the addressing and lint pairs measure, so addressing read 26.2× when batching
had run first and 11.8× when it had not — the same confound the benchmark's own database fixed, one
level in. Batching writes to its own folder now, and the scaling claim is asserted by measuring
**two fixture sizes and requiring the ratio to rise** rather than by argument.
**The ≈10× per-task target is retired as a headline claim** and kept per source: an aggregate
mixes five savings, names none, and *falls* when the console gets leaner, so defending it would
mean arguing against P1. On tokens the claim is ≈10× where it is real (addressing, the lint); on
calls it is a **session** — a canonical task is 3–4 calls (SH157), one agent session instead of
three.

### MCP — `POST /mcp` (SH75/SH83)
Same capabilities over the Model Context Protocol (JSON-RPC 2.0, revision `2025-06-18`),
hand-rolled in `com.viglet.shio.api.agent.mcp` — **not** the MCP Java SDK, which is
Jackson-2/Boot-3 while this app is Jackson-3/Boot-4. `GET /mcp` is a deliberate `405`
(this server sends nothing unprompted). `shio mcp` in the CLI is a stdio bridge:
`claude mcp add shio -- shio mcp`.
- **Tools** (`ShMcpTool` beans, auto-registered): `shio_context` · `shio_find` ·
  `shio_read` · `shio_write` · `shio_apply` · `shio_publish` · `shio_assets` ·
  `shio_changes` · `shio_verify` · `shio_digest` · `shio_remember`. Every tool is a *shape*
  over a REST surface — if a tool needs new logic, the logic goes on the REST side first.
- **Resources**: `shio://manifest` · `shio://blueprints` · `shio://context` ·
  `shio://schema/{postType}` · `shio://blueprint/{name}` (+ the `/files` suffix, SH166).
  The last one is the only route an MCP-only caller has to a package's **carried files** —
  they travel on `GET /agent/blueprints/{name}?include=files` otherwise, which a client with
  no HTTP of its own cannot call. Served **verbatim, placeholders standing**, because SH119
  leaves carried-file substitution to the client (a project's npm name is local identity the
  server has no business being told); each body is preceded by the line naming the
  `{{placeholder}}`s in it, and an apply's `file <path> not-written placeholders=…` rows say
  the same thing for the caller that never fetches them. A **resource** rather than an
  argument on `shio_apply` because that tool is at 457/460 and `tools/list` is paid every
  turn. One bean serves both URIs: `ShMcpResource#matches` keys on the literal prefix before
  the first brace, so two beans would both claim `shio://blueprint/…`.
- `shio_apply` covers **two** verbs on purpose: a desired-state document, or a blueprint
  (`blueprint` + `params`). "Converge to this document" and "converge to this package" are
  one decision with two inputs, and a separate tool would cost more schema per turn than
  the two arguments do — see the budget note below.
- `shio_remember` (SH160) is the **eleventh** tool, and the one place P1 was allowed to
  outrank P3. SH114 argued against it correctly for Claude Code — a schema is paid every
  turn and a coding agent has a shell — and wrongly for a pure MCP client, which could read
  the memory and never write one, so `instanceMemory: true` was a half-truth for exactly the
  clients the manifest informs. It wins because the memory is the only mechanism that makes
  session N+1 cheaper than N and is write-once-read-many: a store nobody can write to stays
  empty. **Forgetting is an empty note, not a twelfth tool.** A `remember` property on
  `shio_write` was rejected as a cheaper shape that does not deliver the capability — most
  conclusions worth keeping are reached while *reading*, with no write to hang them on.
- **Prompts**: `new-site` · `add-section` · `translate-site` · `audit-content`.
- **Error routing matters**: a fixable failure (bad address, unknown post-type) is a
  tool *result* with `isError:true` so the model reads the SH80 `fix`; a JSON-RPC
  `error` is only for an unusable message (unknown method/tool), which the client
  swallows. Getting this backwards throws away the teaching error.
- A tool-count test fails the build past 12 — tool schemas are context paid every turn.
  The real ceilings are `mcp.tools.list.total` (**2360**, raised by SH160 for the eleventh
  tool, measured 2327) and `mcp.tools.list.max-per-tool` (460) in
  `src/test/resources/token-budgets.properties`, both measured; raising one is allowed and
  must be justified in the commit — and the description is trimmed *first*, which is what
  took `shio_remember` from 167 to 128 before the ceiling moved. **`shio_apply` is the tool
  to watch and it is now the constraint**: at **457** against 460 it is the largest, because
  it carries the blueprint verb as well as the document one, and it is why SH157's `verify`
  argument could go on `shio_write` and not on it (SH227). The answer there is a trim, not a
  raise.
  `ShMcpWireConformanceTest` pins `tools/list` against a golden — regenerate with
  `-Dshio.mcp.golden.update=true` and review the diff when the surface really changes.

Addresses (SH79): `post:<site>/<friendly-url>` · `folder:<site>/<name-chain>` ·
`site:<name>` · `id:<uuid>`. **Writes take only `post:`/`folder:`** — a folder path and
a friendly URL are separate namespaces, and a write that guessed wrong would create
content in the wrong place silently. CSRF-exempt **only for callers holding no session
cookie** (SH167 — see below), authenticated like every other API; the SH54 ACL and SH22
quota apply on the agent path identically.

### What bounds an agent — Block L (SH106–SH109, SH200, SH201)

**The invariant: the agent path is never a privilege escalation**, and after Block L it is
not a privilege *superset* either. Three guards, all narrower than what was reachable before.

**And it cuts both ways (SH201).** After the block landed, the agent path was in places
*stricter* than the console — and where that happens the console is the hole, not the bar. The
form to carry forward is symmetrical: **the two surfaces enforce the same ACL, and whichever is
stricter is the correct one.** A curator's session is not a lower bar than an agent's credential,
it is a different one. That is never a reason to loosen the agent path to match.

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
  trash, per entry, reporting what it cannot honestly undo instead of guessing. Since SH200 an
  entry may name a **folder**: reverting that one restores the whole deleted section, which is
  the gesture a human means by "undo that", and `approve` skips folder entries because a section
  has no draft to publish.
- **A deletion is recoverable at every level (SH200).** `folder.delete` and the console's folder
  delete put the subtree in the trash rather than erasing it, so the op SH108's confirm token
  exists to gate is no longer a *harder* delete than deleting each post by hand. What is still
  open, and worth knowing before relying on the undo: **the trash is console-only** — an agent
  can fill it and cannot list or restore it (**SH222**) — and **deleting a whole site** still
  behaves the way a folder delete used to (**SH223**).

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
- `DELETE /api/v2/post-type/{name}` — delete a non-system type (204/404/409). **A type content
  still points at is a teaching 409 naming the count (SH236)** — it used to be a *500*, because
  the check was the `system` flag alone and `sh_post_unified.post_type_id` is `onDelete NO ACTION`,
  so the database refused with a constraint violation the caller reads as a server fault. The
  count is what makes it actionable (rename vs retire), and **trashed posts are counted too and
  named separately**: a trashed row still holds the reference, so a refusal saying "0 posts"
  followed by a 500 would be the worst of both.

**Creating a site is `site.upsert` on the batch surface (SH170)** — addressed `site:<name>`,
idempotent, and it creates a **blank** site (`ShSiteWriteService.createBlank`), not a clone of the
SH71 bootstrap template. A token with an SH2 site allow-list is **refused** — inventing a site
outside the list is the escalation §XII forbids. `POST /api/v2/site` still exists for the console
(session + CSRF, and it keeps the starter clone).
**It fills what is empty and never replaces what somebody chose (SH184).** SH170 refused to update
at all, reasoning that a site's URL and description are console concerns with a human looking at
them — right about a *decision*, wrong about a *blank*, and it left an agent able to build a site,
deploy a front end and not connect the two without a human opening the console. An empty `url`,
`description` or `furl` is now filled and reported as an `update` naming the fields; a non-empty
one is **kept**, and the note says which and points at the console, because a bare `noop` reads as
the request having been ignored rather than as a value having been protected. Three rules: the op
stays **idempotent** (a second run is `noop`, so a document is re-runnable); a **`dryRun` answers
from the same code that writes** (`ShSiteWriteService.planFill`), so a plan cannot come back
greener than the run it predicts; and the rule lives on **`ShSiteWriteService`**, not in the batch
service — what an upsert may overwrite is write semantics, and SH245 is the standing lesson about
second copies of a verb. Do **not** reach for `update` instead: that is the console's full replace
and it nulls `postTypeLayout` and `searchablePostTypes` on the way past.

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

**Renaming is `POST /api/v2/post-type/{name}/rename` (SH163)** — body `{ "to": "NewName" }`,
and it is the only way. A `PUT` to a new name creates a *second* post-type and orphans the
first with its id, its fields and every `sh_post_unified` row pointing at it, while answering
`200` — SH134's defect one level up, with the same answer: one op that moves and reports what
moved. The content does not actually move (a post references its type by **id**), so the job is
the row plus **the one place the model stores a type by name** — a `Multi Select`'s
`multiselect.postType` (SH154), rewritten and named in `referencesUpdated`. Four refusals: an
unknown source (400 with the installed names), a blank target (400 — renaming to nothing is a
delete nobody asked for), an **occupied target (409, never a merge)** and a system type (409) or
a code-managed one against a `managed:false` hand edit (409, the same claim rule `upsert`
follows). Renaming to the current name is a **noop**, so a re-run is safe. What it deliberately
does not do is touch project files: `shio/content/**` frontmatter `type:` and a desired-state
document's `type` name the post-type too, and the response's `notes` say so rather than the
server writing a file it does not own. The console's API-name field stays read-only and grows a
**Rename** button beside it.

**`shio push` reaches it (SH235).** A renamed `shio/post-types/*.json` used to converge as a
create plus — with `--prune` — a delete aimed at the type it replaced; now `detectTypeRenames`
finds it from **two signals**, SH150's shape applied to the model: an explicit `"movedFrom":
"<old name>"` (an instruction `pull` never writes, ignored once the source is gone, and the only
signal that survives a rename *plus* an edit) and an **identical body** under a new name (the
plain `git mv`). Three rules carry over: **ambiguity is refused** before anything is written and
names the candidates; a **plausible but unprovable** pair (same field names, different bodies) is
*printed* with the `movedFrom` line to add, because silence is how a delete-plus-create destroys
content quietly; and a rename's source leaves `removed`, or `--prune` deletes the type the rename
just preserved. Renames are applied **before** upserts (an upsert on the new name first would
create the second type this exists to prevent), a rename-only change needs **no** upsert at all
(so `--check` is quiet on the next run), and `movedFrom` is stripped from the body — it is an
instruction to the CLI, not a field of the model. The content-tree half is *reported*, not
rewritten: a post-type rename leaves `shio/content/**` declaring the old `type:`, and the command
says which name to look for.

**Cascade gotcha:** `ShPostTypeAttr` maps `shWidget`/`shPostType`/`shParentPostTypeAttr` as
`@ManyToOne(cascade=ALL)`, so `ShPostTypeAdminService` detaches those before the
orphan-removal replace — otherwise an update would cascade-delete the shared widget catalog.

### `shio` CLI (`@viglet/shio`, in `cli/`)
The "JS dev, zero Java" command-line tool: `init` (scaffold via `create-shio-app`; **`--blueprint <name>` takes the starter from the instance instead, and with `--site` applies its content half in the same command** — SH119), `types` (SH11 codegen), `pull`/`push` (post-types as code → `shio/post-types/*.json`, `--dry-run`/`--prune`; **`--content` switches both verbs to content**, see below), `context` (`--as agents-md` renders the pack as project agent instructions — SH115),
`resolve`, `apply` (SH129 — a desired-state content document; `--check` is the CI drift guard, `-` reads stdin; **`--blueprint <name>` applies a package instead**, with `--param k=v` repeatable, `--params <file>`, `--site`, `--force`), `blueprints` (SH92 — the catalogue; `blueprints <name>` for one package's parameters), `verify` (SH99/SH100 — the content lint; **exits 1 on an error**, `--strict` also on warnings, `--folder`/`--address`/`--since` scope it per SH143, `--baseline`/`--accept` subtract accepted findings per SH142, **`--delivery` adds SH141's route proof** — it appends the group rather than replacing `--checks`, since a caller wanting the stronger proof still wants the lint — and the report's `notes` are printed, because an unreported cut reads as a clean pass), `digest` (SH101 — one page's structure), `snapshot` (SH102 — **a PNG for a human**, plus whether the page looks different; baselines in `shio/snapshots/`, `--accept`/`--fail-on-change`/`--width`. `playwright` is an **optional** dynamic import with a teaching error naming the install, so `@viglet/shio` never pulls a browser; with `pixelmatch` + `pngjs` the diff is per-pixel, without them it is byte-level and the output says which. **A change exits 0** — a page looking different is usually the change you just made, and a gate here would be the one people wrap in `|| true`. The manifest still reports `visualSnapshot: false`, correctly: there is no endpoint and no MCP tool, and an agent that read `true` would hunt for a call that does not exist), `changes` (SH88 — the feed; `--since last` reads the last content pull's position, `--cursor` prints only the position), `report` (SH104 — **the human handoff**: what changed with a preview link each, plus the warnings as open questions, as Markdown for a PR body; `--since last`/`content`, `--mark`, `--out`, `--title`, `--no-verify`, `--json`), `remember` (SH114 — the instance memory; bare lists it, `<key> "<note>"` writes one, `--forget` drops one, `--site` scopes it), `mcp`, `dev` (`--content` runs the SH90 sync loop instead of the site's dev server), `deploy`. `pull`/`push` use console Basic auth (`SHIO_USER`/`SHIO_PASSWORD`); `types` uses a CDA token (`SHIO_CDA_TOKEN`).
**The handoff report (SH104) is `shio report`, and it is deliberately CLI-only.** It composes
three existing reads — `changes` (what moved), `context?include=sites` (each site's public URL,
the only field it needs) and `verify` (what is unresolved) — into Markdown for a PR body. **No
`GET /agent/report` and no MCP tool**: a fourth contract restating three would put *presentation*
on the server (P10), and the artifact's consumer is a **human**, so per-turn tool schema would be
paid for something the agent hands to someone else. Four rules it must keep: a **deletion is a
row** (no address, no link — the row an implementation that builds the list out of links drops,
and the one a reviewer most needs); a **preview link is never invented** (it names which half is
missing — the site's URL or the post's friendly URL — because a plausible link that 404s costs
the reader their trust in the rest of the document); the **warnings are the open questions**
while errors are only *counted* (they belong to the agent's own loop, and restating them makes a
second copy of the lint report that drifts); and **`--mark` refuses to advance its cursor past a
range the report could not cover whole** — a read must not move a position as a side effect, since
running it twice would answer "nothing changed", and for a review artifact an empty report reads
as approval.

**CI gates, in the order they should run:** `shio push --check` (model drift) → `shio push --content --check` (content-tree drift) → `shio apply --check` (document drift) → `shio verify` (content defects).
**Accepted findings (SH142) live in `shio/verify-baseline.json`** — `(rule, address, field)` triples that `shio verify` subtracts, written by `--accept` (`--reason` optional) and merged, never replaced. The half that matters: **an accepted finding the run no longer produces fails the run**, so the file cannot rot into a blanket suppression that hides the next defect at the same address; a *truncated* report reports the unmatched entries without failing, because a finding that did not fit is not a finding that is gone. It is deliberately a project file and **not** an instance setting — `GET /agent/verify` keeps reporting everything, and only the gate subtracts. The content gates read **only the sites the tree holds** (SH173, derived from `shio/content/<site>/**`), a post they cannot read by address is a named finding rather than a fatal 404, and a remote failure prints the SH80 body — the request URL is behind `SHIO_DEBUG`. The CLI test suite needs `@viglet/shio-client` built first (`pnpm -C shio-client run build`), or every command test fails on a missing module rather than on its own logic; `shio-sections`' suite needs `@viglet/shio-model` built (`pnpm -C shio-model run build`) for the same reason.

**Two rules the CLI suite earned the hard way (SH198).** A fake filesystem **normalizes its
keys** — `test/support/paths.mjs`' `norm`/`endsWithPath`, one copy for all three of them. The
commands join with `node:path` because they write to a real disk, so on Windows a fake fs keyed
on the POSIX literal the author typed never matches, `readFile` throws `ENOENT`, and the command
takes its "no such file" branch: the test then fails on a *message*, several assertions away
from the cause. That is why one mistake read as seven unrelated defects across four areas. Do
**not** "fix" it by making a command emit forward slashes. And a test that arms a live handle
releases it in **`t.after`**, never only on the happy path — the watch-loop test's `stop()` sat
after its assertions, so one wrong expectation left a `setInterval` holding the event loop and
`node --test` printed all 199 results and then never exited. That is what "the suite hangs" was;
stdin had nothing to do with it. `--test-force-exit` is in the `test` script so the next leak
reports and exits instead of burning a CI timeout with no diagnostic.

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
  the body — and **the merge happens in the projection's domain (SH253)**, which is what makes
  that normalization safe rather than the cause of the drift it was written to prevent. A remote
  document is passed through `projectedPost` on the way in (`projectRemote`, called by *both*
  `pull --content` and `push --content`), so the base, the server side and the file side are
  compared as the file tree holds them. Before it, the base was fingerprinted from the *server's*
  document while the file held the normalized form, so a CRLF body read as a local change on
  every pull, forever, and `push --content --check` failed on content nobody edited. Two rules:
  fingerprinting the projected document is only **half** — measured, that alone turns a permanent
  `local-changed` into a permanent `remote-changed`, because the server's value is still outside
  the domain — and `projectedPost` **round-trips through the format** rather than restating its
  rules, so a normalization added later cannot escape it. This is SH145's `idsToPaths` argument
  ("the base, the server side and the file side all speak paths") generalized from one field type
  to every normalization the format has.
- **The round trip is asserted as a property (SH255, SH257)**, in
  `cli/test/content-round-trip.test.mjs`: one adversarial value list (trailing/leading whitespace,
  CRLF, lone `\r`, YAML keywords, numeric strings, `: `, ` #`, empty, `-`, `[`, `{`, BOM, a bare
  `---`, tab, emoji, `a/b`, `a\b`, `..`, `.`) × **six** round trips (post field, post body, memory
  note, SH191 sidecar, asset path, and a post's own `type`/`site`), plus `projectedPost` asserted
  to be a **fixed point**. It exists because `parse(serialize(x)) === x` is universally quantified while
  every test was an *example*, and every fixture happened to be a value its format represents
  exactly — which is how SH253 and SH254 both survived four blocks. **A value a format cannot
  hold must be named in `DECLARED` with the reason**, and a declared loss the format no longer has
  **fails the build** (SH142's rule, so the list cannot rot into a suppression). A refusal counts
  as legitimate non-holding — it is the only one that cannot lose work silently. Three are
  conversions (a body's CRLF and lone `\r`, an empty note) and the other sixteen are **refusals**,
  which is what a format whose output is a filesystem path should mostly do. If you add a
  normalization anywhere in `content-files.mjs`/`content-notes.mjs`/`content-assets.mjs`, this is
  the test that will tell you what it costs. **Its known limit is SH258**: a declared refusal is
  not checked for being the *right* refusal, so a throw from three layers down satisfies the
  property — which is how SH257's own site-name defect got past the run that added those entries.
- **An asset path may not navigate (SH256).** `assetLocationOf` **throws** on a `.`/`..` segment
  and `assetPathFor` returns **null** for a name it cannot place — and the asymmetry is the design,
  not an inconsistency: the first reads a value from the tree (hand-editable, and writable by a
  blueprint or a generated document) where `null` already means "this is an id, leave it alone"
  (SH145), while the second reads the server's own media listing, where SH173's rule applies and
  one unprojectable row must not take down a pull. It is the only path here that used to accept
  traversal — `siteDir`, `sidecarNameFor` and `ShBlueprintRegistry` all refused it already — and
  `Blog/../../../../etc/passwd` resolved through `readAssetBytes` to `/etc/passwd`.
- **A site name that has no faithful directory name is refused, never trimmed (SH257).** `siteDir`
  used to `trim()`, so a site called `"Blog "` made `pull --content` write a file whose `site:`
  disagreed with its own path — a tree the pull produced and every later command refused. Trimming
  into the frontmatter too is the worse fix: the file would declare a name that is not the
  server's, so a push would target or create the wrong site.
- **`content-notes.mjs`' `scalar()` and `content-files.mjs`' `encodeScalar` are deliberately two
  functions** (separate parsers, so separate charsets) and must **not** be assumed to agree —
  SH254 was one missing clause (`value !== value.trim()`) hiding behind a comment that claimed
  they did, which made a note with a trailing space a permanent conflict on every pull. What they
  share is the property above, not the output.
- **Code gets a file of its own (SH191).** A `PageLayout`'s `HTML`, a `Region`'s
  `HTML`/`JAVASCRIPT` and a `Theme`'s `CSS` are programs, and before this they projected as
  escaped one-liners inside YAML frontmatter — no line numbers, no highlighting, nothing
  `Grep` reaches, and every tweak a re-escape. Law P4 failed on the artifact an iterative loop
  edits most. They now project as **sidecars** beside the `.md` —
  `content-list.HTML.html`, `content-list.JAVASCRIPT.js` — named by a `files:` block the way
  `body:` already names the document body, so reading a file back never needs the model.
  Five rules: **the widget decides, never the post-type** (`SIDECAR_WIDGETS`, the
  `Ace Editor` pair) — a rule keyed on the type stops working the moment somebody models
  their own layout, which is the expected case since a layout is content; the file name
  **always carries the field**, because `home.html` collides silently the day a type declares
  two `Ace Editor - HTML` fields, and a name that changes when a field is added is the silent
  content move `bodyFieldFor` refuses; the bytes are **verbatim** — the one place this format
  normalizes nothing, deliberately unlike the body's CRLF → LF, because a sidecar is read
  back as the whole file with no framing to strip, and normalizing means the value differs
  from the fingerprint the base took from the *server's* copy and every later pull reports a
  local change nobody made (that was live on the body, and is **SH253**, above); a **missing** file named by
  `files:` is a refusal, since "empty" publishes a blank layout and "absent" makes
  `post.upsert` clear the server's copy; and a sidecar the post **no longer needs is removed**,
  read off the file on disk rather than inferred. A `.remote` conflict copy stays **whole**
  (values in `fields:`), so the artifact a human diffs is one file.
  A `Theme`'s `CSS` is declared `Ace Editor - HTML` because no CSS widget is seeded, so its
  sidecar is `.html` today — **SH252** is the one-line fix.
- **`shio pull --content`** merges three ways against `shio/.shio/content-base.json` (the
  last-synced base + the SH88 cursor): one side changed → that side, different fields →
  merged, same field → **conflict**, which writes nothing, exits 1, and offers
  `--ours`/`--theirs`/`--show-remote`. **There are no conflict markers on purpose** —
  `<<<<<<<` in a field value is content the next push would publish.
- **The base is per-field fingerprints, not values (SH148, snapshot `version: 2`).** It is only
  ever *compared*: `equal(a, b)` is `stable(a) === stable(b)`, the merged output always takes the
  remote or local value, and a conflict names fields and writes nothing — so the values were
  being stored in order to be compared and then discarded, one extra copy of the site on disk
  and in every clone once the file is committed to share the base. `digestsOf`/`baseEntryFor`
  own the shape; `site` and `url` stay verbatim because they are *used* (a pull deletes a file
  by the address the base remembers). Two things to keep: **absence has its own fingerprint**
  (`ABSENT`) — without it a field the server added that neither the base nor the file had came
  back a *conflict*, because a fingerprint map has no key where `undefined === undefined` used to
  be true — and a **v1 snapshot is digested on read**, so an existing project needs no re-pull.
- **`shio push --content`** compiles the tree into one desired-state document per site and
  applies it through `/agent/plan|apply` (law P10). It declares a post the server changed
  with the **server's** values — declaring the base would revert a console edit, omitting
  the post would expose it to `--prune`. `--check` is the drift guard.
- **Two refusals to keep:** a truncated `find` result makes the projection partial, so pull
  refuses (`--allow-partial` marks the snapshot, and `push --prune` then refuses too), and
  `push --content` over an empty directory is an error, not an empty document. Since **SH146**
  the first one fires far less often — the projection *pages* `find` to the end, so `limit` is
  the page size rather than a ceiling on how large a site can be — but it is unchanged and
  still strict: an instance that reports a short result and offers no cursor cannot be walked,
  and writing that tree is what a later `--prune` would read as deletions.
- **An empty folder is a file too (SH149)** — `shio/folders/<site>/<name-chain>/_folder.md`,
  frontmatter only (`site:` + `folder:`), which `push --content` declares as a `folder.upsert`.
  A tree of *only* markers is a legitimate push: that is the "scaffold the structure, then
  fill it" step, and it was the one step that had to leave the file tree and go through the API.
  Four rules: **a separate root, never inside `shio/content`** — there the directories are
  *URL* segments, so a marker under it would be claiming a URL prefix is a folder chain, which
  looks right until the one time the two disagree and it creates the wrong folder silently;
  **a marker only for a folder no post names**, or the projection would double its file count
  to restate every post's own `folder:`; **the base snapshot records folders** (`folders` in
  `content-base.json`), because a marker the server lacks is *either* a local scaffold not yet
  pushed *or* a folder somebody deleted, and existence alone cannot tell those apart — guessing
  either way loses work; and **a marker widens `--prune`'s scope**, which is the correct
  semantics rather than a hazard: declaring `/archive` means the tree owns it, and a *post*
  naming `/archive` already did exactly the same thing. `--folders-dir` moves the root.
  `reconcileFolders` is the pure half; the folder list comes from the context pack's sitemap
  (no new endpoint — P10), walked to the end for SH146's reason.
- **A renamed file is a move (SH150), and the document has a `moves` section for it.** A rename
  is two file edits (the path and `url:`, which the format refuses to let disagree), and to a
  pure state document that reads as one undeclared post at the old URL plus a new one at the new
  — so `--prune` deleted the first and created the second, and the post came out with a new id
  and none of its audit trail, version history or inbound references. `moves` is the document's
  one **intent** channel for that reason: state cannot express "this is the same post". It
  compiles to `post.move` (`ShAgentDesiredStateService.moveOp`) **after folders, before upserts**,
  so the upsert writes fields at the post's new address instead of creating a second post there.
  It stays re-runnable because a move whose `from` is gone is a **noop**, not an error.
  - **Detection is three signals** (`detectMoves`): `movedFrom: <old url>` in frontmatter — an
    instruction like `publish:`, never written by a pull, and the *only* signal that survives a
    rename plus a rewrite, since after a rewrite there is no content left to match on; identical
    content at a vanished address and a new one (the common `git mv` case, needing nothing from
    the author); and a `folder:` that differs from the server's.
  - **That last one is a defect this fixed, not a rename**: `post.upsert` deliberately does not
    relocate an existing post, so editing a file's `folder:` was a push that reported success
    and moved nothing at all.
  - **Ambiguity is refused, probability is reported.** Two files that could each be the same
    rename are a 1-in-2 chance of moving the wrong post's history, so the push stops and names
    the candidates. A pair that is *plausible* but unprovable (same type, different content) is
    printed with the `movedFrom:` line to add — silence there means a delete-plus-create destroys
    history without mentioning it.
  - **Two invariants that are easy to break.** A moved post's old address leaves `removed`, or
    `--prune` deletes the post the move just preserved; and it must not come back into the base
    snapshot from the server read (which was taken *before* the move), or the next run offers to
    prune it. On the server, a move's `from` counts as **declared** so the prune sweep skips it —
    without that, SH108's confirm digest would ask the operator to confirm destroying the post
    being preserved. **Not available to a blueprint** (`CONTENT_KEYS`) or to `shio_apply`: a
    package renaming the caller's live pages is not its business, and MCP renames with
    `shio_write`'s `post.move`, which costs no extra schema.
- **A large tree splits, except when it prunes (SH199).** `push --content` compiles one document
  per site and a tree past the SH138 document ceiling used to dead-end there; `splitDocuments`
  now cuts it into parts that each fit (`--max-ops`, default 1500), and the union converges
  exactly where the whole would have, because an upsert is independent and idempotent. Each part
  carries the folders and the moves (a move whose source is gone is a noop, so repeating one is
  free) and **never** prunes.
  **A pruning push stays whole and refuses instead**, and this is the finding rather than a
  limitation: prune deletes what a document does *not* declare, so both obvious splits are
  unsafe. Splitting by folder subtree does not narrow anything, because `documentsFor` builds
  folders from `ancestorsOf`, which always yields `/` — every part would prune the **whole site**
  and delete what the other parts declare. And "a content pass, then one pruning pass declaring
  every folder and no posts" deletes every post in those folders. So `--prune` past the ceiling
  is a teaching error naming `--site`, `--content-dir` or dropping `--prune`. `opsOf` counts the
  way the server does, publishes included — which is why 1000 posts is 2000 ops.
- **Assets project too (SH145)** — bytes under `shio/assets/<site>/<folder-chain>/<name>`, and a
  `File` field reads as that path instead of a UUID, which is what makes the tree movable to
  another instance, reviewable in a diff and a complete description of a site. Owner:
  `cli/src/content-assets.mjs` (pure: `fileFields`, `assetPathFor`, `idsToPaths`, `pathsToIds`).
  - **An asset is not addressed by a friendly URL**, unlike every other path in Block I, because
    a `File` post *has* none — `ShStaticFileService.upload` never assigns one, which is exactly
    why SH172 needed the `id:` fallback. It is addressed the way the server already thinks about
    files: **folder chain + file name**, the pair `fileExists` checks. No furl change, no new
    endpoint — the inventory is the SH62 media listing joined to the sitemap's folder chains on
    `folderId` (P10: both halves existed, nothing had put them together).
  - **Paths are the projection's currency; ids exist only on the wire.** An incoming `File` field
    is rewritten id → path *as it is read*, so the base, the server side and the file side all
    speak paths — otherwise a File field would read as changed on every single sync. The inverse
    happens only when a document is compiled.
  - **Idempotent by path against the server's own inventory**, not a remembered id, so a fresh
    clone with no base snapshot resolves exactly as a long-lived one does (SH81's `post.upsert`
    argument applied to bytes). **Only referenced assets upload** — a file in the tree that no
    `File` field names is never sent, which removes the question of what an unreferenced one means.
  - **Two refusals and one assumption.** A declared path with no bytes on disk stops the push (the
    alternative is publishing a post whose File field points at nothing); an upload's missing
    folder is created with the same `folder.upsert` the document would have used, never redirected
    to the site root. And **a path already on disk is not re-downloaded**, because a File post's
    bytes are immutable — an upload always creates a new id-keyed post, so the same path is the
    same asset. An id that resolves to no known asset stays an id and is reported.
  - Deleting an asset is **not** projected: bytes go when their post goes (SH38's cleanup
    listener), and a File post can be referenced by content this tree does not own.

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
- **A plan writes nothing and needs no write transaction (SH153).** It used to provision the
  package's post-types inside its own transaction and roll back — accurate, and it meant a plan
  could not run on a read-only credential, a replica, or inside a `readOnly` transaction, which
  is the wrong constraint on a surface whose safety story is "look before you write". Instead the
  plan **declares** what it would create — the site, and each post-type with the fields it would
  carry — and the batch planner resolves against that: `ShAgentBatchService.Declared`, the
  pending-folder idea one resource wider. Two rules keep it honest. It is **additive, never a
  substitute**: a live post-type is still resolved live and the declared names are *unioned* with
  its own, so a plan can never come back greener than the run it predicts — with `mergePostTypes`
  off, nothing is declared for an existing type and SH168's "a real run will refuse this write"
  survives. And it is **honoured on a dry run only**, so a declaration cannot stand in for an
  apply that did not happen. `ShBlueprintWriteService.plan` is `@Transactional(readOnly = true)`,
  which makes that a claim the database enforces rather than one a test has to remember.
- **The server never writes a project file, and never substitutes one.** `files` are returned
  and the CLI writes them (never over an existing one without `--force`); an MCP-only caller
  is told what it has *not* got — **and since SH166, that the bytes are templates**. Each row
  of an apply names the `{{placeholder}}`s standing in that file, read from the **bytes** and
  not from the manifest's parameter list (a package may declare a parameter its files never
  use, and reporting that one sends the caller hunting for something that is not there). Every
  name has its resolved value in the `params` the same response echoes, defaults included,
  which is what makes the report actionable rather than a warning. Server-side substitution
  was refused for a reason worth keeping: the bodies travel on a **read**
  (`?include=files`, or `shio://blueprint/{name}/files`) that has no parameter values, and
  giving a cacheable GET a write's arguments fights SH116.
- **An apply records itself (SH155).** It writes an SH114 note scoped to the site, keyed
  `blueprint.<name>`, saying which package and version built it and with which parameters —
  and the response names the key in `recordedAs`. One note **per package**, because a site can
  receive a `landing` and then a `blog` and a single key would erase the first; parameters are
  sorted and `site` dropped so the same apply writes the same bytes (P5). A memory note rather
  than a table because it then reaches the next session through the context pack it already
  reads (P2). Written *after* the writer's transaction, so a note that cannot be filed leaves
  the apply standing — the response just does not claim a record it does not have. This is not
  drift detection: it makes "is this site still what the blueprint says" *askable*, and the
  answer is still a `dryRun` away.
- **Packages load from the classpath, plus one directory an operator names (SH96).**
  `shio.blueprint.path`, off by default. That *is* the trust model: whoever can set it could
  already put a jar on the classpath, so it grants no new authority, and the person who set it
  is the one vouching for what is in it. Three rules — a **name collision refuses** at load
  naming both origins (shadowing would change what every existing `blog` apply does; ignoring
  would make the operator's package do nothing); a configured path that is not a readable
  directory **fails** rather than loading fewer packages; and the **same validation runs**, so
  "it loaded" means the same thing whichever source it came from. Every package carries its
  `origin`, reported on the listing for local ones only. No URL fetching, no registry
  resolution — that is the community half (SH217).

**The first-party set (SH94, SH119, SH156):** `blog` · `docs` · `product-catalog` ·
`marketing-site` · `landing` · **`nextjs-starter`**. The last one is the
`create-shio-app` template as a package (SH119). **Three packages carry front-end `files`,
all generated by the same `pnpm -C create-shio-app run emit`** — `nextjs-starter` gets the
template tree; `marketing-site` and `landing` get **the same tree plus the section overlay**
in `shio-sections/front-end/` (replace-by-path, so the section-aware catch-all *is*
`app/[...slug]/page.tsx`, plus `lib/sections.ts` and a merged `package.json` dependency whose
range is read from the workspace). `test/emit.test.mjs` fails the build on drift and asserts
the overlay changes **exactly three files** — if that grows, the overlay has become a fork.
**If you change `create-shio-app/template/` or `shio-sections/front-end/`, run `emit`.**
`blog`/`docs`/`product-catalog` are left out on purpose: they compose no sections, so the
overlay would ship a dependency they never import — what they want is a listing page (SH216).
The overlay is only usable because **`@viglet/shio-model` and `@viglet/shio-sections` now
publish** (SH156); before that, a package carrying a page that imports the vocabulary would
have produced a project whose `npm install` fails. Substitution is split by who
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

**Every exemption is session-narrowed, and the one that is not is argued (SH167).**
`/api/v2/post-type`, `/api/v2/review/**`, `/api/v2/cda/**`, `/api/v2/agent/**` and `/mcp` are
wrapped in **`ShStatelessRequestMatcher`**: exempt for a caller holding no session cookie,
CSRF-checked for one that does. A blanket exemption is only safe while no cookie-bearing
client reaches the path, which was true of the post-type API right up to the commit (SH117)
that pointed the React editor at it — and nothing in that commit would have noticed. The
agent namespace is the one that made this urgent rather than tidy: it is gated by
`anyRequest().authenticated()` and nothing more, so a curator's session already reaches
`POST /api/v2/agent/batch`, which deletes content. The CDA is a pre-emption — a
session-bearing CDA write already failed for want of `ROLE_CDA_WRITE`, so narrowing it breaks
nothing that works.
**`ShCsrfExemptionLintTest` fails the build on a new blanket entry**, because the failure mode
is a *future* caller rather than a current request, which no functional test can watch for. To
add one, name the constant in its allow-list with the reason a forged request there can do
nothing an unauthenticated one could not. `/graphql` is the only current member (no
`Mutation`; GraphiQL is a cookie-bearing browser) — a premise of exactly the shape that expired
before, so **since SH234 it is enforced, not written down**: the same lint fails the build the
day a `Mutation` appears in `schema.graphqls` or a `.mutation(` in `ShGraphQLSchema`, and its
message carries the decision made in advance — **narrow `GRAPHQL_PATH` with
`ShStatelessRequestMatcher` and set `spring.graphql.graphiql.enabled=false`**. A
mutation-capable delivery endpoint does not get a cookie-bearing playground in the console's
browser; keeping GraphiQL would mean shipping a CSRF-aware replacement or accepting cross-site
writes on the delivery API, and neither is worth a query playground.

**On the CDA, an explicitly presented `Key` beats an existing session (SH233).** `ShApiTokenFilter`
used to return early whenever anything had authenticated, which read as conservative and was why
the Universal Editor could not save: the console is **same-origin** with the API, so its session
cookie rides along whatever the client sets `withCredentials` to (that flag governs cross-origin
requests only), `SecurityContextHolderFilter` restores the session first, the `Key` header was
never read, and the PATCH authenticated as a curator with no `ROLE_CDA_WRITE`. Sending a key is a
request to be *that* principal; the token's scope, allow-list, expiry and rate limit still bound
it, and a key that cannot authenticate **leaves the context untouched** rather than clearing it.
The client half is the same fix from the other end: `universal-editor.service.ts` uses the shared
`@/lib/axios` singleton now, so the request carries CSRF (required of a session-bearing caller
since SH167) instead of a private `axios.create()` that silently opted out of every shared
interceptor.

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
the live backlog is the per-block follow-ups and Blocks A–G's remaining SH44–SH47 tenant work.

**Before you quote a benchmark number, know which way it moved and why.** Blocks N and O both
made the *baseline* lean rather than the agent path fast — aligning the console reads on the
CDA shapes (SH117/SH120) and then the post read (SH176) — so the SH112 token ratios fell from
3.8×–23.3× to 2.6×–12.6× and then 2.1×–12.1× with the agent path untouched. That is the
product improving and the claim shrinking, three blocks running. **SH157 is the first change
that moved a ratio the other way**, by folding discovery into one read and proof into the
write: every canonical task lost a call. The current reading is **tokens 2.8×–10.5×, tool
calls 2.3×–3.8×** — and **SH164/SH239 have since attributed it**: per-source, the lint is 15.5× at
five posts and 139.6× at forty-five (the only unbounded one), addressing 11.8× rising to 53.1×,
batching 5.0× on calls and 6.1× on tokens, discovery 4.0× on calls and **1.2×** on tokens,
projection 1.5×. So quote a *source*, not a task ratio: the aggregate ≈10× is retired precisely
because it mixes those five and moves when the baseline does.

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
vocabulary — SH95, depends on the DSL; **both publish since SH156**),
`create-shio-app` (Next.js starter),
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
**`-Dskip.npm=true`** — the single `<skip>${skip.npm}</skip>` toggle on the
plugin, which leaves the Java build untouched:
`./mvnw -pl shio-app -am test -Dskip.npm=true`.
(Still run the **full** suite, not a single `-Dtest` class.)
This file used to document `-Dskip.installnodepnpm=true -Dskip.pnpm=true`, which are
`frontend-maven-plugin`'s **per-goal** properties and are not what this pom is wired
for — passing them silently runs the React build anyway, so the documented
backend-only command was neither.

### Publishing to npm
`.github/workflows/publish-js-sdk.yml` (manual `workflow_dispatch`): bumps the
publishable packages, builds, and runs `pnpm publish` in dependency order
(`shio-model` → `shio-sections` → `shio-client` → `shio-react-sdk` → `create-shio-app` →
`@viglet/shio`). `pnpm publish` rewrites `workspace:*` to the published version
automatically — which is why the order is load-bearing, since `shio-sections` depends on
`shio-model` that way. Needs the `NPM_TOKEN` secret.

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
