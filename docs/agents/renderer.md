# The renderer and the section vocabulary

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

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
  **And it is reachable without a browser (SH327):** `/api/v2/render/**` is CSRF-narrowed and this
  endpoint is declared in the manifest's index, because `serverRender: true` was a half-truth while the
  one call that makes a site visible needed a session cookie and appeared in no document a caller
  reads. Reachable *and* discoverable — one without the other is a capability an agent finds by
  guessing.
  **The widgets those fields reference are seeded per widget, on every startup (SH325).** They used
  to be seeded per *catalogue* — `if (findAll().isEmpty())` — and inside `ShOnStartup`'s first-time
  gate, which are **two** guards where the task named one: on any instance older than SH252's
  `Ace Editor - CSS`, this endpoint answered a **500** (`Widget '…' is not seeded`) and SH289's
  `reconcile`, the verb written to repair exactly that, failed identically because both read
  `specsFor`. One reader, one bug, both doors. That is SH152's rule at the wrong **granularity**: a
  widget catalogue is closed and code-owned (a widget is a Java class plus a React component), so
  find-or-create per row is safe and is the only way a release that adds a widget reaches an instance
  that already exists. It is **additive only** (SH289's rule again — changing a seeded row is a
  different decision, and removing one is a migration, which is why the SH60-deprecated `reCAPTCHA`
  row is still served on old instances), and the remaining refusal is a teaching **409**
  (`widget-not-seeded`) naming the widget and the restart, because the branch is now nearly
  unreachable and that is precisely the state SH189 warns about.
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
  rendered output when it breaks**. **Since SH275 the tests also assert the
  premises**, not only the guards: an *unconfigured* `new Handlebars()` does walk a bean
  (`{{secret.value}}` → the value) and does resolve a partial to a classpath path (the failure names
  `/application.hbs` — it is not refusing to look, it is failing to find). Without that, a library
  that became safe by default would turn both guards into dead weight and both comments into lies with
  nothing failing. One correction that came out of it: `{{secret.class.name}}` is blocked by the
  library itself, so the existing `[][]` assertion is evidence for the guard in its first half only.
  (1) **No script engine**: Handlebars.java declares
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
  scope, since `{{#if TEXT}}` and `{{#getRelation SECTIONS}}` ask about the current one. A block's **hash**
  arguments are read too (SH261) and resolve in the scope the *call* is written in, so
  `{{#search q=TOPIC}}` reports `TOPIC` while `type="Article"`, `limit=10` and `q=(concat A B)` are
  excluded — a literal, a number, and a subexpression skipped **whole** rather than parsed. The
  tokenizer tracks quotes and parens for that reason; splitting on whitespace would cut
  `q="two words"` and get the right answer by accident. Handlebars' `{{~TEXT~}}` form had to be
  stripped explicitly (left alone it silently *drops* the reference), which is the failure direction
  to watch here: an unrecognised construct yields no reference and the lint quietly stops covering it.
  **What the scanner does with every construct is a table** —
  `ShTemplateGrammarCoverageTest`, thirty rows, each `REPORTED` / `SKIPPED` / `UNREACHABLE` with a
  reason, plus a check that every marker the scanner branches on has a row (SH273). It exists because
  the failure mode here is *silent under-reporting* and writing the table immediately found
  `{{& TEXT}}` — unescaped output, the same reference as a triple stash — being dropped. **Add a row
  when you touch the scanner**; `UNREACHABLE` is a claim about another part of the system (three rows
  rest on SH183's refusing `TemplateLoader`) so it must carry its reason. Whether Handlebars.java's
  AST would replace all of this is **asserted rather than remembered (SH274)**: on 4.5.3
  `Template#collect` is *deduplicated* — four references to `TEXT` at three scopes return `[TEXT]` —
  `collect(SECTION)` gives only the helper's name, and the structural nodes are in `internal`.
  `ShHandlebarsAstPremiseTest` pins that with a version number, so a bump that adds scope information
  **fails the build** and reopens the question. One happy finding: `collectReferenceParameters()`
  returns the hash *path* and not the hash *literal*, the library drawing SH261's line in the same
  place independently. The same kind of unenforced premise about a dependency exists elsewhere —
  **SH275**.
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
  published" is what decides whether an edit is safe now. **A theme is the third edge (SH286)** —
  named by a layout's `THEME` *field* rather than by a slot, read through
  `ShPageRenderService.themeTitleOf` so the graph and the renderer cannot disagree; its impact is
  the union of its layouts', which is the largest blast radius in the model and the one that used
  to answer nothing. `unused-theme` joins the two warnings, reporting a theme **no bound layout**
  names — including one named only by an unbound layout, since that layout's own `unused-layout`
  finding beside it is what says where the chain broke.
  **An unused-* finding says what happened (SH298/SH308)**: when the title is used in another of the
  run's sites it names **the layout post's address** there — a site name is not an address and a
  title is not one either — and points at `post.move` (a stray template is a move, not a
  re-creation), and when the site binds no layouts at all it says *that* — one missing
  `postTypeLayout` entry rather than N faulty templates. Appended, so a finding with nothing extra
  to say stays byte-identical.
  **And a theme it names that does not exist is reported both ways (SH297)** — the page carries
  `data-shio-theme-missing` on `<body>` and `verify` reports `missing-theme`, an **error** against
  the *layout* (the theme has no address, since it does not exist). The rule across the three edges
  is now complete and deliberately uneven: **a missing layout refuses (409), a missing region or
  theme is marked** — because those degrade while a page with no layout is not a page. Checked in
  *both* states, so a draft-only theme stays SH281's `unpublished-template` rather than being
  reported twice.
  **And a rendered digest counts the markers it carries (SH299)** — `markers: {missingRegions,
  unpublishedRegions, missingThemes}` on `/agent/render`, because those attributes sit on elements
  the structural outline does not describe, so a proof could close green on a visibly broken page.
  It is **in neither hash**: folding it into `digest` would move that hash when a region is
  *published*, which is not a structural change and would undo SH187's two-question split. Present
  and all-zero on a clean rendered page (a caller must not have to tell "none" from "not reported"),
  absent on the `source: content` fallback, and the attribute names are read from
  `ShRegionComposer.DEFECT_MARKERS` rather than restated. **Not** a new `verify` finding — the lint
  reports the same defects through its own queries, and one defect in two reports is two reports
  that can disagree.
- **The rendered DOM is a contract, and it is a table with a test (SH300)** — every `data-shio-*`
  attribute is listed in the appendix of
  [docs/specs/SH185-universal-editor-protocol.md](docs/specs/SH185-universal-editor-protocol.md),
  and `ShRenderedDomContractTest` fails the build when the code emits one with no row or a row names
  one nothing writes. **Add the row when you add the attribute.** It reads *source* (Java and the
  injected JS), because an inline literal is what a constant scan misses. Three producers, one
  contract: the renderer's eleven, plus three the bridge sets in the browser (`data-shio-editing`,
  `-editable`, `-editor`) that exist only while `?editor=true` is on — a reader of the DOM cannot
  tell which side wrote one, which is why they are one set to the test and two tables to a reader.
  A name that is **read and never written** gets a row saying so rather than being left out,
  because from the reading side it looks like a working feature — the one such name,
  `data-shio-label`, is gone since **SH303** removed the *read*: a field's label is declared by the
  post-type, the bridge sends `data-shio-post-type` with each selection, and the console resolves
  labels from the schema. The scan reads **table rows only**, since prose may legitimately discuss
  an attribute that no longer exists.
- **The pre-edit form of the question is `?include=impact` on `/agent/read` (SH262)** — the same
  graph, asked *before* the write, which is the answer that decides whether to edit at all. A
  projection on the read rather than a `GET /agent/impact`, because a new endpoint with no MCP tool
  is a REST-only capability (P1) and the tool count is capped at 12 with 11 used; `shio_read` takes
  the same subject a read already takes, an address. Four rules: it is **opt-in** (an unconditional
  field makes every reader pay for a question some are not asking — P3); the impact is taken from
  **the post the read already loaded**, never by re-resolving the address, so it cannot describe a
  different row than the fields do — the DRAFT-preference is exactly what two lookups come to
  disagree about; it is **absent, not an error**, for a post that is not a template, so a caller
  reading a mixed list of addresses need not know which rows are templates before it asks; and in
  terse it is **its own `impact` line**, because `doc` describes the post while this describes what
  the post reaches. Its one boolean of schema put `tools/list` 19 tokens over the ceiling, and the
  ceiling did **not** move: the description was trimmed first (165 from 182, wording out, the
  draft-preference and the address example in) — that is the order, and it is why the capability
  landed for free.
- **A template's title is an identity, and a duplicate is an error (SH263)** — `sh-region="NAME"` and a
  `postTypeLayout` binding both address a template by title, and `postOfSystemType` resolves by title
  taking the **first** match, so two `Region` posts called `NAV` rendered whichever row came back.
  `duplicate-template-title` in `checks=content` is an **error** (unlike the unused-template warnings:
  an unused region may be work in progress, two templates competing for a name is provably wrong).
  **Detection is instance-wide within the site, reporting is subject-scoped** — a scoped run seeing one
  of two would report the name as *unique*, which is a positive wrong answer rather than a missing one.
  Keyed by post **id**, because a template's own PUBLISHED row shares its id and title with its DRAFT
  row and counting rows would flag every published template as its own duplicate. The resolution order is settled (**SH278**), and
  the write side refuses (**SH277**).
- **A template has a title field, and a duplicate title is refused where it is written (SH277)** —
  two halves, and the second was found by testing the first. `ShRenderPostTypes` declared **no
  `isTitle` field** on any of the three, on the premise that a title is "the post's own column, not
  a field ... the thing the curator types into the title box" — and there is no title box: the
  column is *derived by the writer* from whichever field carries `isTitle`, and `post.upsert` sends
  only `data`. So a template written through the documented SH183 flow landed with an **empty
  title** that no `sh-region` and no `postTypeLayout` binding could resolve to, and SH168 refused a
  `TITLE` key as undeclared, so it could not be set either. `TITLE` (Text, required, `isTitle`) is
  now first on all three — which is what the 2018 package it was copied from always had. **Every
  render test built its fixture with `entityManager.persist` + `setTitle`, which is precisely why
  nothing caught it**; the flow with no end-to-end test is the flow that breaks.
  The refusal is **`ShTemplateTitles` on `ShPostUnifiedWriteService`**, so the console, the CDA and
  every agent path refuse identically (SH201). The interactive-vs-bulk distinction SH277 was filed
  needing turns out to need **no new mechanism**: SH68's import writes through the *repository*, so
  a writer-level check refuses the interactive write and leaves a package containing a collision
  importable — its report is SH263's lint. A **plan reports** what a run refuses (SH168), asking the
  same component through the same `titleOf` the writer uses — a second derivation would disagree the
  first time a type gains a title field, and would read as the plan being right. Two non-collisions
  are load-bearing: a template's **own** DRAFT/PUBLISHED pair shares id and title, and a **trashed**
  template's title is free (no clause needed — `@SQLRestriction` means the finder sees what the
  renderer sees). Limit: `ensure` never touches an existing type (SH152), so an instance created
  before this keeps the old shape — which **SH289** now repairs.
- **An older instance's render model is repairable, on the operator's decision (SH289)** —
  `POST /api/v2/render/post-types/reconcile` (`?dryRun=true` reports, from the same code) adds
  fields an existing render type lacks and changes nothing else. A **second verb**, not a smarter
  `ensure`: `ensure` creates what is absent and anything may call it, while this changes a type the
  instance already has. **Additive only**, so a curator's edit survives — which is why SH252's
  widget drift is deliberately *not* fixed by it: changing a field is a different decision from
  adding a missing one. `ensure` and `reconcile` read **one** field table (`specsFor`), or a field
  added to one would be created on new instances and never offered to old ones — the exact drift
  this closes. The report is the **symptom**, because a post-type has no SH79 address and no place
  in SH143's scoping: **`untitled-template`**, an *error* in `checks=content` for a template with a
  blank title — which `unused-region` cannot see either, since `ShTemplateGraph` skips a blank
  title, so it was content nobody could reach that nothing reported.
  **And the transcription is now checked (SH290)** — `ShRenderModelSourceTest` compares the three
  types against `import/post-types.json`, the 2018 model they were copied from, which **nothing
  else reads**: a reference with no reader is how a transcription drifts. Field **names** and the
  **title flag** must match; every other difference is declared with a reason (`OMITTED` — no
  `DESCRIPTION`, no `CACHED`/`CACHE_TAB`, because there is no page-cache subsystem behind them;
  `WIDGETS` — `PageLayout.THEME` is `Text` because SH183 resolves a theme by title while the 2018
  `Content Select` stores an id, plus SH252's two), and **a declared difference that no longer
  exists fails the build** (SH142's rule, third application).
- **The render state reaches every level, and the row order is total (SH278)** — `ShRenderState` is the
  one writer for `statesFor` / `preferDraft`, used by both `ShPageRenderService` and
  `ShRenderHelpers`. A **DRAFT** render resolves the draft layout, the draft region *and* the draft
  theme; a **PUBLISHED** render never picks up an unpublished template edit. Before this,
  `postOfSystemType` read both states with **no** preference and took whichever row the database
  returned — so a preview could show the published layout while the curator edited the draft, which is
  the exact failure SH247 extracted `preferDraft` to prevent for listings and nobody applied here. Note
  the shape: not a second copy of the rule, **no copy at all** — when a rule is extracted, ask which
  callers should now use it, not only which copies to delete. The fold is ordered by **title then id**,
  so a duplicate title renders *consistently* wrongly rather than alternating; and reverting the fix
  left the draft-preview test **passing on H2**, which is why an unordered finder is a P5 violation you
  cannot see where you wrote it. **The render package's other unordered reads are ordered too (SH279)**:
  `{{file_url}}` and `currentFolder` now follow the render state, and `rootFolder` is ordered *and* has
  its "one root by construction" rule named, because that is the reasoning that held for a title until
  SH263 broke it. `resolveReference` needed no change — the shared `preferDraft` had already made it
  deterministic, which is the payoff for putting the order in one writer.
  **The sweep is finished, and it is one reader plus a lint (SH279)**:
  `ShFolderRepository.findRootFolder(site)` orders before it picks, nine call sites use it, and
  `ShRootFolderReaderLintTest` refuses a `findFirst`/`get(0)`/`min` over the raw
  `findByShSiteAndRootFolder` — which returns a **`Set`**, so picking from it asked the database to
  choose where a post lands and what a CDA listing describes. The lint **permits iterating** (the
  exchange export walks every root) and refuses **picking**. It found the ninth call site itself, and
  it was SH279's own first half: the render helper had added the comparator *inline*, leaving one
  ordered reader and eight unordered ones — SH245's rule from the other side.
  **One consequence worth knowing before you debug a blank page: a PUBLISHED render now needs published
  templates** (**SH280**). `post.upsert` writes a draft and publishing is separate (P7), so a site
  built through the SH183 flow has draft templates until somebody publishes them — and such a site now
  raises `MissingLayoutException` on a published render where it used to silently use the draft. The
  behaviour is right, and since **SH280** the refusal says which: `UnpublishedLayoutException` is a
  **409** ("exists, never published") with its own `fix` naming `post.publish`, distinct from the 404
  ("no such layout") so a caller can branch on it. And because a refusal only reaches whoever asked for
  the page, `verify` reports **`unpublished-layout`** (a warning) for a bound layout with no published
  row **where the site has published pages of that type** — that second condition is what keeps it off
  every site mid-build, and the general rule is that *work in progress and something wrong are
  separated by another condition, not a stricter first one*. Checked and negative: SH68 import is
  faithful to each post's own state and no first-party blueprint ships a layout, so this is an
  agent-built-site exposure rather than an imported one. **The region and theme levels get a marker and a
  finding instead of a refusal (SH281)**, because they *degrade* rather than failing — a slot is
  skipped so the rest of the page still serves, which is right for a preview. So
  `data-shio-region-unpublished` joins `data-shio-region-missing` as a **separate attribute**, keeping
  SH169's missing/empty distinction intact across a third state; the marker text is a constant for
  SH183's reason (a region name is content and `-->` would close the comment); it is computed only on
  a PUBLISHED render, since `statesFor(DRAFT)` already read both states; and `verify` reports
  **`unpublished-template`** for a region or theme a published page needs, with a `fix` that says
  explicitly that neither refuses the render — otherwise a reader who has just met SH280's 409 expects
  one. The rule across all three: **when a message can be wrong rather than absent, fixing it beats
  adding one** (SH189's bodiless 400, SH280's "no such layout", this).
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
  **The engine is the one source of the capability set (SH260)**: `register(name, ShHelperShape,
  helper)` records the name *and the call shape* (`BLOCK` / `BLOCK_PATH_ARG` / `INLINE_PATH_ARG`),
  and every reader asks — the unknown-helper refusal lists `engine.registeredNames()` (alphabetical,
  a total order), the SH251 scanner takes `ShHelperShapes.of(engine.registeredHelpers())`, and the
  lint reads `ShRenderHelpers.ROW_KEYS`. Five hand-kept copies used to restate it and every drift was
  silent: a registered helper missing from the scanner's list is reported as an unknown *field*, so
  the typo rule invents a typo. **The scanner has no overload that omits the shapes** — a default of
  "nothing is registered" would turn a forgotten argument into exactly that false positive; pass
  `ShHelperShapes.none()` and mean it. Adding a helper is one line plus its shape and its
  **signature** — SH272 added the third, because a hash parameter's name (`folder=`, `q=`) exists
  nowhere but inside the helper's body, so no reader could derive it from the registry.
- **The capability set is discoverable, in the context pack (SH272)** — `registeredSignatures()`
  renders as a `conventions` line naming every call form (`{{#query folder= type= limit=}}`,
  `{{#getRelation FIELD}}`, `{{file_url FIELD}}`, …) plus `sh-region`. Before it, an author learned
  the set by writing a template that called something else and reading the 400 — a teaching error
  doing the job of documentation, one name per attempt, which is P2 backwards. Three rules: the pack
  and **not** the manifest (`startHere` is orientation, and an MCP resource would answer only MCP
  clients — while a helper set is exactly the *instance-specific* fact this section exists for, next
  to SH114's notes); it is **conditional on the instance's own model** holding a render post-type, so
  a session that will never write a template pays nothing (P3) and the check costs no query; and a
  helper registered with no signature is **absent** rather than listed bare, since a half-answer
  about a capability is what sends somebody guessing. `form` stays unregistered and therefore
  unadvertised — a list naming it would have the author write what SH192 then refuses.
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

