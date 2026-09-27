# The renderer and the section vocabulary

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### The renderer — page → layout → region (SH183)

`GET /preview/{site}/{url}` composes a page out of content the instance already holds, so a
site an agent built is visible with **no front end and no client setup** — which was the gap
the whole of Block P is about. Owner: `com.viglet.shio.render`. This is **preview-grade** and the
guards that keep it that way are deliberate: a distinct route, `no-store` + `X-Robots-Tag: noindex`
(a freshness lifetime is how a preview quietly becomes infrastructure), authentication, and a banner
the page's own stylesheet cannot hide because it is inline-styled rather than classed.

**The banner reports how the page is made, from the page (SH559).** It used to end with
`layout <site> (fidelity)`, and on a converted site that string was the *only* statement anywhere
about how the page had been produced — so on an authorable conversion (eleven pages of section
lists, a home page with sixteen annotated fields) it named the other mode. The title was accurate
when one layout served one mode; **SH464 made a single generated layout serve all three** and the
name kept the word it was born with. Nothing downstream reads it, which is why it survived: the only
reader is a person or an agent looking at the page — and it cost exactly that reader a wrong
inference, since `layout R6Aut (fidelity)` beside an unstyled page argued for a mode mix-up when the
cause was a site-wide theme switch. **A label that is wrong is worse than no label**, because it is
evidence and it points somewhere. `ShRenderedPage.composition` is now `verbatim` / `authorable` /
`mixed`, computed by `ShPageRenderService.compositionOf` from the page's own `content` and
`sections` — the same two fields `PAGE_BODY_HTML` branches on, so the banner and the page cannot
disagree (SH281). **`null` where the page holds neither**, because this route renders every post
type and calling an `Article` "authorable" is the same defect one type over. The layout title stays
but is **quoted**, which marks it as a name rather than a claim: renaming it is a
`site.postTypeLayout` key change, so that is a migration and not a label fix.

**And since SH451 there is a second route, which is the same renderer with every one of those guards
inverted** — `ShSitesAPI`, `GET /sites/<site>/<format>/<locale>/<path…>`. "Production delivery is
Next.js over the CDA" was true and was also the only option, and the option did not work: `/preview`
answers 401 anonymously *by design*, and the Next.js starter could not install (SH446). So the
renderer composed a correct page nobody outside the console could read. It is now a choice between
two, and no non-goal forbids it — P10 says MCP must not become a third contract, and this is one
renderer over one body of content at a second address. See the section on it below.

- **The model is content.** `PageLayout` (`HTML`/`THEME`/`JAVASCRIPT`), `Region`
  (`HTML`/`JAVASCRIPT`) and `Theme` (`CSS`/`JAVASCRIPT`) are **system post-types created on
  demand** — `POST /api/v2/render/post-types`, idempotent, never touching an existing type, **and
  since SH186 also the `render.provision` batch op**, because a client speaking only JSON-RPC has no
  HTTP of its own and `post.upsert` cannot create a system type: an op rather than a thirteenth MCP
  tool (`tools/list` is paid every turn for a once-per-instance step), delegating to the same
  `ShRenderPostTypes.ensure()`. It is the **one addressless op** — it acts on the instance's model, and
  a `render:post-types` address would be a fourth SH79 form for one caller
  (SH38's `File` precedent, SH152's provision-never-upsert rule). The endpoint exists because
  `PUT /api/v2/post-type/{name}` refuses a system type, so without it the render model would be
  reachable only by whoever can restart the JVM. Because they are posts, `post.upsert`, the SH68
  package, the file projection and `shio verify` all work on them for free; the 2018
  `openviglet/bootstrap-site` export renders with **no migration**.
  **That is asserted end to end now, and it is narrower than it reads (SH294).**
  `ShRenderModelSourceTest` compares the model with `/import/post-types.json` — the real 2018
  artefact, whose `PageLayout`, `Region` and `Theme` entries carry their own 2018-06-14 and
  2019-07-11 dates — and `ShImportRendersSourceTest` imports a package whose templates are keyed
  the 2018 way and fetches the page back, so composition, `sh-region` and theme resolution are
  evidence rather than a sentence. **"No migration" is about the content, never the envelope:** a
  2018 template's `data` is keyed `TITLE`/`HTML`/`JAVASCRIPT`/`CSS` and the renderer reads those
  keys unchanged, while SH189 records that the *manifest* cost seven field renames inferred by
  reading the `ShExchange*` records. Two details the fixture had to be told, and both are the 2018
  convention rather than this renderer's: a field is named at the **model root** (`{{TITLE}}`,
  not `{{post.TITLE}}` — the `post` frame is the entity's properties), and a theme is **inlined by
  the layout** through `{{theme.css}}` rather than linked. The fixture reads every post-type and
  field name out of the artefact and refuses to write one it does not declare, which is what makes
  the test evidence instead of a tautology — the objection that kept SH294 an idea for a block.
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
  rendered HTML records neither. Resolution is nearest-ancestor. **`sh-field` declares the
  element's *content*, so one element declared one field — and a link is two (SH503)**:
  `sh-field-attr="FIELD:ATTRIBUTE"` → `data-shio-prop-attr` adds the ones an element draws into its
  own attributes, space-separated for more than one pair, copied verbatim like a prop and split by
  the bridge. Without it `ctaHref` was captured, stored and rendered into the `href` with no
  declaration, so a curator could rename a button and not repoint it; an `attr` field is edited in
  the properties panel and written back with `sh:field-set`. Both attribute forms survive:
  `sh-*` is a contract the content declares (SH192 lints it, SH193 will graph it), `data-*` is
  what the browser consumes. **Neither side of the bridge uses `"*"`, and since SH304 that is a test
  rather than a sentence** (`ShEditorBridgeTest`, over this script and the console's page) — it was
  false for the second, unspecified script the server used to serve at `/universal-editor/`, which
  posted to `"*"` and never read `event.origin`; that script was a copy of the published
  `@viglet/shio-editor-cors` and is gone, along with the permitted path. **One bridge per producer**:
  this one for the markup the renderer emits, the npm package for a front end that writes its own
  `data-sh-*` by hand. **SH350 fixed the package too**: it reads
  `<meta name="urn:shio:system:editor-origin">` and is **inert without it**, because a framed page
  cannot work out who framed it — the same reason the injected script is handed
  `data-shio-editor-origin`. All three implementations are asserted from `ShEditorBridgeTest`; a
  *behavioural* DOM test is still missing and is SH306's decision. The console pins the
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
- **Which render paths get SH311's cache is asserted, not assumed (SH313)** — the cache is taken
  **only** when the current transaction is read-only, so a caller already holding a **writing** one
  joins it under `REQUIRED`, `isCurrentTransactionReadOnly()` answers false, and the render silently
  costs 19 queries a page instead of 6. That the cache *works* was already guarded by
  `ShRenderSweepBenchmarkTest`'s marginal-cost number; what was missing was **which paths have it**,
  and the answer was "both" only because there are two callers and neither writes. The expensive
  case is a *future* caller, so `ShRenderCacheReachLintTest` fails the build on one:
  no file holding a `ShPageRenderService` and calling `.render(` may carry a `@Transactional`
  without `readOnly`. **A lint rather than the log line the task proposed** — SH103's own argument
  is that nobody reads logs, and a build failure names the new path to the person adding it.
- **One read-only-transaction cache, two populations (SH316)** — the mechanism lives in
  `ShReadOnlyTransactionCache`: a value resolved once per **read-only** transaction, and not cached
  at all in a writing one, which is what makes staleness unaskable rather than argued. SH311's
  templates-per-system-type is one population; `ShTemplateGraph` is the other, and it took the
  measurement to say **which** part of it. The task proposed memoizing the assembled graph; a second
  assembly inside one transaction already cost **0 queries** (SH311's cache covers every row it
  reads) — 1.13 ms of maps, sorts and an HTML parse per binding, real but not what a query ceiling
  can guard. The queries were one layer sideways: `pageCount`, `exists`, `isPublished`,
  `templatesOfTitle` and `duplicateTitlesIn` each read **every row in the site** and filter in
  memory, so a per-binding rule paid a site read per binding. Memoizing the rows took `impactOf`
  from 2 queries a call to 0 and a one-site `verify` from 95 to 60;
  `ShTemplateGraphMemoBenchmarkTest` holds all four numbers and prints them every run. Because one
  `ShSiteTemplateGraph` now answers several callers, its maps are unmodifiable — the in-place edit
  that used to be a caller's own business would now change what the next rule sees.
- **Three lessons this area paid for, kept when their sections went (SH191–SH194, SH251)** — they
  left IMPROVEMENTS as SH356's stale-section triage and they are rules rather than history.
  **(1) When a rule seems to need a per-instance sweep, check what it actually varies over.** SH251
  was framed as "a lint over resolved pages"; the resolution does not depend on the page, because
  `postTypeLayout` is keyed by post-type and a layout's slots are static — so walking the *bindings*
  checks identical ground at a fraction of the cost and reports at the layout or region post, which
  is the artefact somebody edits. **(2) Check the *shape* of what you are reusing, not just that it
  exists.** SH193 was designed to store presentation edges in `ShReference`, "which already exists
  and already survives a move" — and it cannot: `ShReference` joins to `ShObject`, and a post is not
  an `ShObject`. The analogy was sound and the vehicle was not. **(3) Whatever an agent would call
  next to find out whether its write worked belongs in the write's response** (SH157, generalised by
  SH194) — lint, then digest, and the next instrument to appear should be asked that before it gets
  an endpoint. Plus the one to protect on any change to the SH251 scanner: **the negative cases were
  most of the work** — frames, `this`/`./`/`../`, data variables, subexpressions, literals, partials,
  comments, a block's own name, an inline helper's name and every row value, with `{{~TEXT~}}` the
  one its own test caught being dropped. A reference *not* reported is the quiet failure direction.
- **`{{#image FIELD}}` is the tenth helper, and SH390's premise was stale — which *was* the finding.**
  The task offered three answers and said the choice was the task: build derivatives into the tree, add
  an on-demand resizing endpoint, or delegate to the delivery layer and ship "the helper shape". It
  dismissed the second as *"a new subsystem and a cache-invalidation question"* — and **that subsystem
  already exists**: SH40/SH41 ship `ShImageTransformPipeline`, served at `/file_source/**` with
  `?w=&h=&format=&crop=`, cached, guarded and optionally HMAC-signed. So two of the three options were
  about building something already built, and the choice collapsed to the third. Reading the code
  before choosing is what made this a small task instead of a large wrong one.
  What was genuinely missing is exactly two facts, and **both require the server**: a template does not
  know an image's **intrinsic width**, so every `srcset` candidate it wrote would risk upscaling; and it
  cannot compute an **HMAC**, so with signing on a hand-written srcset is a page of 403s. Hence a
  helper, and hence one that emits *whole URLs* rather than parts.
  **It costs nothing**, which is why it is a helper and not a build pass: `ShStaticFileService` already
  writes `WIDTH`/`HEIGHT` onto the `File` post's own `data` at upload, so this reads the same `fieldsOf`
  map `bytePath` already reads — no extra query, no file read, no image decoding. A derivative pass
  would have put N copies of every image in git and needed a native image library in a CLI that keeps
  even playwright optional.
  Three decisions: the ladder (320/640/960/1280/1920) is **filtered below the intrinsic width with the
  original as the last entry**, so nothing ever upscales — a `srcset` offering 1920w for a 1600px image
  tells the browser a lie it acts on, and SH386 grades a replica against a source that *does* ship a
  small image to a small viewport, which is the task's own framing of this as fidelity rather than
  optimisation; an image nothing measured (an SVG, a pre-reader upload) yields the **plain url and an
  empty srcset**, which in HTML means "use src" and is right, because fabricating a ladder from a width
  nobody knows is the one choice that produces *broken* images rather than unoptimised ones; and the row
  has **its own key set** (`IMAGE_KEYS`) for `FORM_FIELD_KEYS`' reason — a listing's rows are
  interchangeable because they are all posts, and putting `srcset` on every `{{#query}}` row would be a
  promise no listing keeps.
  Two things worth keeping from the run: the **tally held exactly** — three test edits for the new
  helper *name* (signature anchor, validator list, field-lint fixture) plus the silent-catch population,
  which is what SH384's note predicted; and the first draft of the signing assertion looked for a raw
  `&` and failed, because Handlebars **escapes** `{{srcset}}` as an attribute value. Asserting the raw
  form would have been asserting that the renderer had an XSS hole.
- **`site.scripts` is one ordered list per site, and Shio *never injects* it (SH389).** Browser
  JavaScript had two homes — a `Theme`'s `JAVASCRIPT` and a `Region`'s (SH248) — and both are
  per-layout, so a site with four layouts held **four copies** whose load order was a property of each
  copy. Consent has to load *before* what it gates, which makes that a compliance defect rather than
  duplication. The task was an **idea** because of a real risk it named: the renderer is preview-grade
  and `no-store`, production delivery is Next.js over the CDA (P10), and *a consent implementation that
  behaves differently in the two is worse than one that exists only in delivery*. **The answer is that
  Shio stores and exposes and has no code path that injects.** A layout writes
  `{{#each site.scripts.head}}`, the delivery layer reads the same post through the CDA, and the thing
  that could have diverged — an injected banner behaving one way in preview and another in production —
  does not exist. What the task actually wanted is the *single ordered list*, and that is what shipped:
  four layouts now read one order instead of holding four. Five decisions:
  **A fifth system post-type, `SiteScripts`**, one per site, found by *type* rather than by a magic
  title — so two of them is ambiguous and yields nothing (SH381's rule against a fabricated answer),
  reported as `site-scripts-ambiguous`. It is a system type for `Redirect`'s reason inverted: not
  consulted by the router, but **read by the render model**, which a vocabulary post-type cannot reach.
  **One JSON field, not a Relator** — `Theme.TOKENS` settled this in SH195 (no JSON widget in the
  catalogue, JSON is valid JavaScript), and a Relator would need a nested field spec for a value whose
  whole meaning is *its order*, which an array carries and sibling posts do not.
  **Three named lists rather than one plus a position field**, because Handlebars cannot compare two
  strings without a helper: `{{#if_eq}}` does not exist and SH260's closed set is not growing for this.
  `head` / `bodyStart` / `bodyEnd` are placeable with the `{{#each}}` every template already uses, and
  every position is present even when empty so a layout's loop is never an expression resolving to
  nothing.
  **An unknown `position` falls back to `bodyEnd`, never `head`** — a typo must not move a script
  *earlier* than the author wrote it, which is the direction that breaks a consent ordering, and the end
  of the body cannot block a paint.
  **An entry with neither `src` nor `inline` is skipped, not emitted** — `<script src="">` re-requests
  the page itself in some browsers, which is worse than the missing script. The renderer degrades and
  `verify` explains, which is the standing split.
  The lint that matters is **`site-scripts-dropped`**: moving the declarations to one list fixes the
  duplication and does *not* stop a layout from failing to emit them, so a bound layout whose HTML never
  names `site.scripts` is a warning with the page count in it (SH388's shape). And the **replication
  angle is what made this a task at all** — SH377 already inventoried a source's third-party scripts and
  had nowhere to put them, so they were silently dropped and the replica was subtly not the site;
  `shio propose` now proposes them as a `SiteScripts` list, every entry **for the head** (moving one
  later is a reviewer's decision, not a command's), with a **recognised** consent category where the host
  says so and a blank where it does not — a guess presented as a fact would be worse than a blank on a
  consent gate, and the report counts how many need a curator.
  **One process note worth keeping:** `./mvnw -q compile` printed nothing on a run that had *failed to
  compile*, and I read the silence as success. That is agents.md's `-q` trap one command along — it
  documents `-q` hiding a test total, and it hides a build failure exactly as well. Compile without it.
- **A layout's `<head>` is lintable from the stored HTML, and answering SH388's open question is what
  made it cheap.** The task was filed as an *idea* asking whether these are `verify` findings at all or
  belong to the delivery layer that owns the head in production (P10). **Both readings were about the
  wrong subject**: the head a *visitor* receives is composed by the front end over the CDA and Shio does
  not own it — but every defect the task named is a property of the **layout**, which is content Shio
  stores. So it is a template lint and says nothing about production. That reframing also deleted the
  expensive half: the idea proposed comparing *rendered* heads across a site, which is the per-page cost
  SH312/SH384 measured and which reports the **symptom** (sixty pages sharing one description) sixty
  times. The **cause** is one line of one layout and needs **no render at all** — a `<title>` that
  interpolates nothing has the same value on every page bound to it *by construction*. One text scan of
  a field already in hand, one finding, and the page count in the message rather than the count (SH280's
  shape). The evidence for the claim is the test: `ShHeadConventionsTest` is a plain unit test with no
  context, no database and no fixture, running in under a second — a head check that needed a running
  instance would mean the cheap version had not been found. Seven rules, and **two are errors** because
  they are provably wrong rather than a matter of taste: a **relative `og:image`** (a crawler resolves it
  against *its* origin, so the tag renders perfectly and the card is blank — `site.baseUrl` from SH337
  exists for exactly this) and a **`noindex`** (what a preview-shaped conversion carries over, and the
  preview route already sends `X-Robots-Tag: noindex` on every response, so the tag in the markup buys
  nothing and costs the live site). The five warnings — static/absent title, static/absent description,
  absent canonical — are warnings because a one-page site legitimately has one of each. Three silences
  are decisions: a layout with **no `<head>` at all** yields nothing (a fragment or a deliberately
  headless template, and reporting every absent convention would bury the layouts that have a head and
  get it wrong); a layout **nothing renders through** is skipped, because `unused-layout` is already that
  finding; and an `og:image` this scanner **cannot judge** (`{{OG_IMAGE}}`, or anything starting with an
  expression) is left alone rather than guessed at. The interpolation test is deliberately generous —
  any `{{…}}`, including a block — because the claim is narrow: *this value cannot differ between two
  pages*. A layout that varies its title some way the scanner cannot see is a false positive, which is
  why those five are warnings.
- **A `Redirect` is the fourth render system post-type, and its friendly URL *is* the path it answers
  (SH385)** — a replication that renames a path breaks every link into it (search results, other
  people's pages, a decade of email), and the map from source URL to replica address existed at exactly
  one moment and was discarded. **A system type rather than an SH382-style vocabulary one**, and SH249's
  test is what decides it: a menu needs no new mechanism because a *template* reads it, while a redirect
  is consulted by the **router** before a page is chosen — that is a new mechanism and it is the
  server's, so the server is entitled to know this name. Fields: `TO` (a friendly URL in this site, or
  an absolute URL) and `PERMANENT` (a Check Box). **There is deliberately no `FROM`, and that omission
  is the whole design** — the design section offered one, and not having it pays off four times, each
  a rule or a lookup nobody had to write: the router already resolves a path to a post, so a redirect
  costs **no query** and no site-wide scan on a 404; SH323 makes a furl site-unique, so two redirects
  claiming one path is *impossible* rather than reported; a redirect shadowing a real page is already
  SH99's `duplicate-url` **error**, which is one of the two findings SH385 asked for arriving with no
  code; and in the CLI's tree lint "served or redirected" is one lookup, because a Redirect is a post
  with that `url`. **Something writes them now (SH1064)**: `post.redirect` is the batch op, the
  console's publish dialog offers one when a rename vacates an address, and `verify` reports
  `url-orphaned` for a path this site used to serve that a published page still links to — for two
  years the type was served, linted and only ever authored by a curator by hand. That op keeps the
  default below rather than inverting it, which is the whole reason the default is stated here.
  Five more decisions: **302 is the default and 301 is opt-in**, because a permanent
  redirect is cached by browsers effectively for ever and a wrong one is the hardest routing mistake to
  take back (SH121's shape applied to a status code); **`PERMANENT` is read from `"1"` or `true`**,
  since the Check Box widget stores one through the console and the other through `post.upsert` and a
  301 that silently became a 302 is not a defect anybody finds by reading content; **an empty `TO`
  renders as an ordinary page**, so the request fails naming the missing layout binding — a true
  statement about an unfinished post — rather than redirecting a reader to `/` and telling nobody;
  **the preview honours it** (301/302 with `no-store` and `X-Robots-Tag`, and a relative target
  rewritten into the preview's own address space per SH336) because a replication's claim is *it
  renders the same* and a preview that 404'd on every old path would leave the thing a replica most
  often gets wrong untested; and **it is not a rewrite engine** — one exact path in, one address out,
  no patterns and no regular expressions, which is what makes a URL space verifiable from the tree
  instead of by running requests through it. `verify` gains **`redirect-target-missing`** (an error: a
  404 *after* a redirect is harder to diagnose than a plain one), and an **absolute** target is
  deliberately unchecked because verifying it means the instance fetching a stranger's URL, which SH268
  settled. The guards charged **five test edits for a fourth system type** — `ShRenderModelSourceTest`
  needed a new `INVENTED_TYPES` list (the 2018 model has no redirect of any kind, so a per-field entry
  would be three entries saying one thing), `ShRenderPostTypesTest` named "the three" in three places,
  and `ShSilentCatchInventoryTest` counted the controller's `catch` — which is **control flow, not a
  degradation**: it answers with a 302. Counted rather than exempted, so the population stays a diff.
  All three type lists derive from `ShRenderPostTypes.NAMES` now.
- **A theme's tokens are declared data, and the vocabulary is adopted rather than invented (SH195)** —
  a `Theme` now carries **`TOKENS`** beside `CSS` and `JAVASCRIPT`, in the **W3C Design Tokens
  (DTCG)** shape (`{"color":{"accent":{"$value":"#0b5"}}}`), which is what Style Dictionary, Figma
  and Tokens Studio already export — so a designer's file pastes in and no dialect is invented for an
  agent to learn. `ShThemeTokens` compiles it to CSS custom properties on `:root`
  (`color.accent` → `--color-accent`) and **prepends** the block to the theme's own CSS, so every
  existing layout gets tokens without being edited; a separate `{{theme.tokens.css}}` handle would
  have shipped the feature switched off. The same values are also a dotted map for a template
  (`{{theme.tokens.color.accent}}`), for the places `var()` cannot reach. **Four decisions worth
  keeping**: a theme with no tokens emits *nothing*, not an empty `:root{}`, because SH101's digest
  is over exactly these bytes; a token whose `$value` is a DTCG composite is skipped while its
  siblings compile; a name that cannot be a custom property is rewritten (`font size` →
  `--font-size-…`) because such a declaration is dropped silently and can take the block with it;
  and a `TOKENS` field that is not JSON **throws to the renderer**, which records a CONTENT incident
  and renders on the CSS alone — the page must not fail, and the degradation must not be silent
  (SH270). The field is the render model's one deliberate departure from the 2018 package, declared
  in `ShRenderModelSourceTest.INVENTED`: it is optional, so an imported 2018 theme arrives without
  it and renders exactly as it did. An instance created before this gets the field from
  `POST /render/post-types/reconcile`, and `GET /render/post-types/drift` names it (SH295).
- **What one source consolidates is the *shape*, not the names (SH260, kept by SH356)** — the
  obvious fix for five hardcoded helper catalogues was to expose the registered names and have the
  readers ask, and it solves the validator's list and nothing else: the SH251 scanner does not need
  to know *what* is registered but **how a call is written** (does the first token belong to the
  helper, or is it a field?). So `register(name, shape, helper)` declares the shape, which is the
  piece nothing could have derived — it lived in the author's head at the registration and was being
  reconstructed from memory by two other classes.
- **Three more kept when their sections went (SH248, SH249, SH252 — SH356's triage)** —
  **(1) Sections are content a template reads, not a level of the render model.** Sections and
  regions differ on every axis that matters (who names them, by what key, whether they vary with the
  page or with the *type*, whether they are homogeneous); what joins them is that a region's template
  can iterate a page's section field through SH247's `getRelation`, so a section-composed page
  renders through the ordinary path and the renderer needs no knowledge of the section vocabulary.
  That is SH185's "the engine never infers ownership" one level out, and it is what stopped this
  becoming a third composition path. The decision is written up in
  [specs/SH249-sections-and-regions.md](../specs/SH249-sections-and-regions.md).
  **(2) A fallback that is labelled beats a fallback that is guessed at** — SH169's composition stays
  for a post with no layout binding or no friendly URL, and `source` says which question was
  answered; failing instead would break the digest for every site mid-build, which is every site
  until it is finished. **(3) A region's browser JavaScript is emitted inline and once per region
  name** — inline because the page is `no-store`, so a cacheable route buys nothing and adds a thing
  that can 404; once per name because a layout may slot the same region twice and the markup is
  wanted in both places while the initialisation is not. And SH252's lesson about all three:
  **it was filed as cosmetic and was a defect** — a `Theme`'s `CSS` declared with the HTML editor is
  not a console preference, it is the widget deciding the sidecar's extension, so the field projected
  as `.html`.
- **Two rules the appearance digest was designed against (SH187, moved here by SH356)** —
  **two hashes, because they answer different questions**: folding appearance into SH101's `digest`
  would make a restyle and a structural break indistinguishable, which is the one comparison an
  iterative loop most needs, so they are prefixed `d:` and `a:` and cannot be confused in a report.
  And **a capability flag is added, never repurposed**: `visualSnapshot` stays `false` because there
  is still no raster here and `appearanceDigest: true` joins it — an agent reading
  `visualSnapshot: true` would hunt for a PNG that does not exist, which is what `universalEditor`
  did once. The honest scope is also the design: there is no layout engine, so the digest covers the
  appearance failure an **agent** creates (markup written against class names the theme lacks) and
  not the ones a person notices (SH265).
- **The drift is a fact about the instance, and it is a GET (SH295)** —
  `GET /api/v2/render/post-types/drift` names each render type this instance has that is missing a
  field the shipped model declares. The comparison existed before, as
  `POST /reconcile?dryRun=true` — so learning whether an instance needed repairing meant issuing a
  **write verb** and trusting a flag not to write, which a read-only caller cannot do at all. The
  only other signal was SH289's `untitled-template`, a finding on a *post*: it fires after somebody
  has written a template that cannot be addressed, so an instance that had run `ensure` and written
  nothing was indistinguishable from a sound one. **Where it deliberately does not live:** not a
  `verify` finding (a post-type has no SH79 address — the constraint that shaped SH289), and not the
  SH77 context pack (every session would pay for a line most instances do not need, P3). This
  endpoint already answers "can this instance render"; drift is the other half of that question.
  `reconcile` now *formats* the read rather than repeating the comparison, so the plan and the repair
  cannot describe different things — and the self-invocation is deliberate, since bypassing the proxy
  is what lets one comparison run read-only for the GET and inside the writing transaction for the
  repair.
- **What a helper costs is per page, and one of the four shapes was not (SH312, SH353)** —
  `ShRenderHelperSweepBenchmarkTest` sweeps four page shapes: plain **5** queries a page,
  a `{{#query limit=10}}` listing region **7**, five sections by id **10**, five sections by
  friendly URL **15**. The first three are the floor plus what the helper reads, and they cannot be
  cached away — a helper's input is the page's own folder or its own field values, so the read is per
  page by construction, which is why SH311's 6 is a floor and not an answer. The fourth was a defect:
  `resolveReference` tries the id first (SH181's order), and a URL entry then read **every row in the
  site** to filter one `furl`, once per entry. Memoized through `ShReadOnlyTransactionCache` it is
  one read per transaction and the row now costs **10** — the same as the identical page stored as
  ids, so the storage shape a `Content Select` or an SH95 `Multi Select` happens to use no longer
  decides what the page costs. **Caching a listing's rows stays refused**: those rows *are* the
  answer, so holding them is a freshness decision about content, where "which post does this URL
  name" is a resolution.
- **`param` — the one frame that comes from the reader (SH259)** — query-string values as strings,
  read-only, **never headers, never cookies, never the request object**. SH247's `{{#search q="…"}}`
  takes a term the *template* supplies, which is a tag page, not a search results page; that needs
  `?q=`. The question was never how to pass a parameter but **what a template may know about the
  request**, because SH183's non-goal resolution is that a layout is content and must not have the
  raw `HttpServletRequest` in scope — the thing that made the 2019 engine a sandboxing liability.
  Sorted, so two readers sending the same parameters in a different order cannot produce two SH101
  digests. `state` and `editor` are **excluded**: they are the renderer's own, and a template
  branching on `param.editor` renders differently inside the Universal Editor than for a reader.
  A repeated parameter flattens to its **first** value. **`{{{param.q}}}` is refused at write time**
  (`param-unescaped`, SH192's machinery) — every other value a template reaches is content written
  by somebody with an account, and this one is written by anybody with the URL, so the unescaped
  form is reflected XSS. `{{param.q}}` escapes by default, so only the deliberate opt-out is closed;
  `{{{TEXT}}}` stays free, because the rule is about **where the value came from**. Nothing is said
  about caching: `/preview` is already `no-store`, and preview is not delivery.
- **The preview is walkable, by two mechanisms with two jobs (SH336)** — `{{link}}` is a bare furl
  while the page is served at `/preview/<site>/…`, so every internal link 404'd, and **the lint
  could not see it**: `routes` resolves against content URLs where `/contato` is correct. The
  inversion was active — a hand-written `/preview/Demo/home` that *works* was reported
  `link-unresolved` while every really broken link passed.
  **(a) `ShPreviewLinks`, a server DOM pass** in `ShRenderAPI.page` only — `/agent/render` calls
  `ShPageRenderService.render` directly, so nothing here is ever hashed and the SH101 digest cannot
  move. It rewrote a root-relative `href` **only when the path was a URL this site publishes**,
  reusing the index `routes` builds — the rule **SH466 inverted**, below. Query string and fragment
  survive; the pass is idempotent.
  **(b) click interception in `shio-editor.js`** for the nav a region's SH248 `JAVASCRIPT` builds at
  runtime, which no server pass can see. Handed the *same* index via `data-shio-preview-paths` —
  without it the browser cannot tell a page from a file. Never on a modified or non-primary click,
  never on `target=…`, never outside `/preview/…` (the editor also runs against external front ends).
  **What is not an option is making `{{link}}` preview-aware**: it is content-facing, so one field
  would mean two things in two renders — the coupling SH187 spent a task splitting apart.
- **And the reader's MODE goes with the link, or edit mode ends on the first click (SH1036)** — both
  rewriters above returned a prefix plus the site path, so the link's own query survived and the
  request's `?editor=` and `?state=` did not. `?editor=true` is what injects the bridge at all, so
  the frame landed on a page with **no editor on it** — and the console, which derived its handshake
  from the iframe `src` that in-frame navigation cannot change, went on reporting *connected* while
  the panel described the previous page's element and Save still offered to write its fields. The
  helper that preserves them (`ShSitesAPI.withQueryOf`) existed and was called on the **redirect**
  path only.
  Three parts, and the ones to get right are the exclusions.
  **(a)** `rewriteInto(…, carry)` threads them onto every **navigation** and no asset: `?editor=true`
  on a stylesheet means nothing and busts its cache, and a `<link rel="stylesheet">` is an `href`
  the navigable pass sees — so the SH515 asset/navigation split is what keeps it off one.
  **(b)** the page script does the same for the links that pass never saw, copying the pair
  **verbatim** (the server encodes because it builds from a decoded `getParameter`; a script that
  re-encoded would double-escape). In both, **the link's own query wins** — an author who wrote
  `?state=published` means it — and the fragment is split off first, because `/a#b?c` is a fragment
  containing a question mark.
  **(c)** the console re-keys on what the bridge REPORTS rather than on `src`. `sh:ready` already
  carried `{postId, url}` on every load and nothing read it; a different `url` now clears the
  selection, the pending changes and the loaded labels. `sh:navigating` is the new half — departure,
  posted before leaving — which closes the window where a curator could press Save during a load and
  write the old page's fields. A navigation with no new `sh:ready` before the iframe's own `load`
  (which always follows it, since the script sends at `DOMContentLoaded`) is a page with **no
  bridge**, and the badge says so.
  **What was NOT added**: a `navigated` message, because `sh:ready` is already posted from `attach`
  on every load and a second arrival notification is two mechanisms for one fact; and a `navigate`
  message, because the console already drives the frame by reassigning `src`, which re-keys the
  handshake correctly on its own.
- **A site name in a path is a path segment, and a link already in the preview still carries the
  mode (SH1173)**: the prefix `/preview/<site>` was built at three call sites with
  `URLEncoder.encode`, which is form encoding. A space became `+`, which a path reads as a literal
  plus, so every link on a site named "Console Check" was a 404. The public `/sites/…` prefix had
  the same bug. `ShSiteFrontEnd.previewPrefix` and `pathSegment` (Spring's `encodePathSegment`) are
  now the one spelling. Separately, the home link a template writes from `ShSiteFrontEnd.of` arrives
  already prefixed, and `rewriteOne` skipped it as the `preview` instance route, so it never carried
  `editor=true`. A reference inside this page's own prefix is now kept as it is and gets the carried
  parameters. Another site's `/preview/…` is still left alone. `ShSiteFrontEndTest` had pinned
  `/preview/My+Site`, the 404 itself. It now pins `%20`.
- **A plain field is read the way it is written (SH1175, bridge half)**: `shio-editor.js` wrote a
  `plain` field with `textContent` and read every field with `innerHTML`, so a typed trailing space
  arrived as `&nbsp;`. The server stored it as text, the template escaped it, and each save added
  one more entity. `valueOf(el)` is now the one read: text for a `plain` field, with U+00A0 given
  back as a space, and markup otherwise. **And a section's fields are declared (SH1175, renderer
  half).** `compose` declared widgets only from the page type's `propTypes`, so every `sh-field`
  inside a section post had no `data-shio-prop-type`, and the bridge edited it as rich: the
  `marketing-site` hero heading gained an entity on every save. `compose` now takes an
  `ownerWidgets` resolver. It is asked once per page with every non-page owner id, sorted. The
  service answers with two queries whatever the number of sections:
  `ShPostUnifiedRepository.findPostTypeIdsByIdIn` (`p.shPostType.id`, never the name) and the
  types by id. A page that owns all its fields asks nothing, and a post the query cannot type stays
  undeclared, as a listing row always did. Measured on a clean instance: the heading reads
  `data-shio-prop-type="Text"`, and two editor saves leave `"Acme  "`, with no entity.
- **A field says what KIND it is, and the browser offers only that (SH1037)** — edit mode was granted
  by the presence of `data-shio-prop` alone, so every annotated element became `contenteditable` and
  reported `innerHTML`. A plain `Text` field therefore absorbed the markup of a paste, stored it, and
  then showed the tags as visible text — because the template escapes a text field, which is
  *correct*. The defect was an editor offering rich editing over a field that cannot hold it.
  `ShRegionComposer` now emits **`data-shio-prop-type`**, the declared widget verbatim — the model's
  own vocabulary, which the console's widget map, `shio pull` and the CDA schema already speak, so
  there is no third classification to disagree with. The script maps the name to one of three
  affordances and records which in **`data-shio-affordance`**: `Text`/`Text Area` →
  `contenteditable="plaintext-only"` (the *browser* refusing markup — a paste handler runs after the
  DOM is already built, and every later `innerHTML` read would carry it), `HTML Editor` → rich as
  before, anything else → **not contenteditable and still selectable**, because a `Date` or a
  `Content Select` has a shape a caret cannot express but is edited in the panel. `plaintext-only`
  is assigned in a `try` — an unsupported value *throws* rather than being ignored, and the fallback
  is rich, which is the old behaviour and not broken.
  Two absences are deliberate. **A missing kind means rich, not panel**: a listing row's type is not
  knowable at compose time (`sh-post="{{id}}"` is all an author can write — the row's type is the
  query's), and a page rendered before this, or an external front end writing its own `data-shio-*`,
  carries none either; reading that as uneditable would remove editing that works. And **the widget
  is never guessed** — a wrong kind is worse than none, since the browser acts on it.
  Sanitising is *not* the bridge's job: SH956 applies the allow-list at the write boundary, the draft
  merge included. This is about what the curator is offered, not about what is accepted.
- **Protocol v2 is negotiated, and silence means everything (SH1037)** — v1's `sh:ready` was a post id
  and a URL, so the console's only question was whether *anything* answered. That is the wrong
  question for a protocol with two producers: this script and `@viglet/shio-editor-cors`, and the
  published one implements less — so the console offered affordances the other side might not honour,
  and finding out meant a click that did nothing. Ready now carries `protocol`, `capabilities`,
  `postId`, `postType` and `url`, and the console hides what the page does not claim: the edit/preview
  switch is **disabled with the reason on hover** rather than removed, because a control that vanishes
  reads as a console bug. The capabilities are **derived from what is true of this page** —
  `navigate` only when the server shipped the URL index, since without it the interception is off
  (SH336) and claiming it would stop the console offering its own. **A bridge that sends no
  `capabilities` array is read as supporting all of them**, the same compatibility rule as a missing
  field kind: two implementations exist and treating silence as "supports nothing" removes editing
  that works. The negotiation is deliberately one-sided — a round trip would have to survive the page
  navigating under it, and hiding an affordance is the only action the console has either way.
- **The rich-text toolbar is restricted to what the allow-list keeps (SH1037)** — bold, italic, link,
  remove-formatting, and nothing else. A toolbar with headings, colours and fonts would offer writes
  SH956 then strips, so the curator sees the change, saves, and loses it with no explanation. It
  appears for `affordance="rich"` only: over a `plaintext-only` field its commands would produce
  exactly the markup that field cannot store. `mousedown` and not `click`, because a click moves
  focus out of the `contenteditable` and the selection is gone by the time a handler runs; and its CSS
  is scoped under `data-shio-editing` like every other rule, so preview falls back to the UA's own
  `[hidden]` and the page is byte-for-byte what a visitor sees.
- **A section is an object on the page, not a field of one (SH1038)** — the bridge bound two prop
  attributes and reported `text` or `attr`, so the editor could change a hero's headline and never the
  hero: a section could not be selected, moved, duplicated or removed, although `post.upsert` can do
  all four by rewriting the owning Page's ordered reference array. Everything needed was already
  there — the conversion's generated layout writes `data-shio-section` naming the type (SH556) and
  `ShHtmlRegions` reads it to tell authored content from kept markup — and only the browser half was
  missing. **The composer adds POSITION**: `data-shio-section-index` on each section and
  `data-shio-sections` on the element composing them. Derived from document order, which *is* the
  Page's order because `{{#getRelation}}` renders the field in the order it names — a template could
  write `{{@index}}` (the frame has published it since SH772) and then every template would have to
  remember to, and one that forgot would leave a page whose sections cannot be moved with nothing to
  say so. **Per container, not per page**: a layout may compose two ordered lists, and one running
  count would make the second list's first section read as position three of an array that does not
  exist. The count is on the container because *a position is only meaningful against a length*, and
  the position after the last section is a real destination with no element on it.
  The bridge sends `sh:component-selected` carrying the section's type, its post, `index`, `of` and
  the **Page** whose array a reorder rewrites. Its click handler runs after the per-field one (which
  stops propagation) and after the link handler, so **a click on a headline is the headline, the space
  around it is the hero, and a link is a navigation** — no extra chrome needed to tell them apart.
  `sections` is claimed as a capability only when the page has one. A page with no sections is
  byte-identical to what it was.
  **And then the three verbs, each one replacement of that array.** `sh:section-op`
  (`move`/`remove`/`duplicate`) acts on **what the curator selected** and on nothing the message
  names — with two composed lists there are two sections at index 0, so `{op, index}` identifies two
  elements and a move would rewrite whichever the selector reached first. It is applied
  **optimistically**, because the write is a draft merge and the frame renders the *published* row:
  nothing about the write makes that document change, so a curator would press Move and watch the
  page sit still. The bridge then re-derives every position (document order changed, so the recorded
  order has to) and rebinds fields, since `cloneNode` copies children and no listeners — a duplicate's
  copy would otherwise be outlined, editable-looking and inert. The console reloads the frame when its
  write is refused, which is the only case the two can disagree.
  **The overlay is the console's, and that is the point**: every other affordance is an injected
  stylesheet, which is right for a field — an outline and a caret are things CSS can express — but a
  section needs a toolbar, and the buttons have to live where the writes are. So the page sends
  `rect` with the selection and `sh:section-geometry` on every scroll and resize, in the framed
  **viewport's** coordinates: the iframe occupies one box in the console's layout and the framed
  viewport *is* that box, so the console wraps the frame in an element of exactly that size and
  positions the overlay with no measurement of its own. (The border moved off the iframe onto that
  wrapper — `absolute` is relative to the padding box, so a border on the iframe puts every overlay
  one pixel out.)
  **Duplicate places the same section twice**, because the array holds references: editing either
  heading edits both. That is what one replacement of the array can mean, and the toolbar's title
  says so rather than letting a curator find out by editing one of them; a copy is a different object
  and a different write (SH1101). **Which field is checked, not assumed**: `{{#getRelation sections}}`
  takes the field's *value*, so the name is gone by the time markup exists and no attribute can carry
  it. The console writes `sections` — the one name the generated layout uses — and offers the
  controls only when the Page's own row holds an array as long as the list on screen; a page composing
  from another field gets the header and the position with no buttons.
- **An edit can be taken back, and a refresh stops lying about what is pending (SH1041)** — the
  pending map had two operations, set and clear-on-success: no per-field revert, no discard, and a
  refresh that reloaded the frame while deliberately keeping the changes, so the badge counted edits
  that were no longer on screen and Save still wrote them. **Nothing recorded the value that was
  there before an edit**, and only the page could: `sh:content-changed` fires *after* the change.
  So the bridge takes a baseline when a field gains focus and sends `was` on every message; the
  console keeps the **first**, which is what makes an undo mean *before I started on this field*
  rather than *before the last keystroke*. Reverting needed the other half too — `sh:field-set` was
  the panel's write-back for an `attr` field and could not reach a **text** one, so clearing an
  entry would have left the frame showing text the editor was no longer going to save, which is a
  worse lie than having no revert. It now writes both, a `plain` field as text and a `rich` one as
  markup (SH1037's decision about what each can hold), and moves the page's baseline with it. A
  refresh **replays** the pending edits into the reloaded frame rather than asking: asking makes a
  reload cost a decision, and replaying makes the page and the count agree without one. Discard is
  confirmed because it is the one control here that destroys work rather than writing it, and both
  exits ask — `useUnsavedGuard` for the three the router never sees and, since SH1090's data router,
  for every route change as well, in the editor's own words naming how many edits are pending.
  **No Ctrl+Z**: a `contenteditable` already has the browser's own undo, which fires `input` and so
  is reported like any other edit, and a console-level shortcut competing with it inside a focused
  field would be two undos for one keystroke.
- **What fits in a container is declared, not derived (SH1039)** — an editor that can select and move
  a section still could not add one, because nothing said which types fit where: `/agent/editable`
  answered fields and regions with no catalogue, and the shared section types reached the console as
  post-type names in a picker. **Only the template knows what a container accepts**, which is
  `sh-field`'s argument one level out, so the allow-list is `sh-accepts="SectionHero SectionCta"` on
  the element composing the sections, rendered as `data-shio-accepts`. An **absent** declaration is
  *nothing is known to fit* and carries the line that would fix it — never *anything*, since that
  would need a server-side list of which post-types are sections and `ShVocabularyPostTypes` bans a
  third copy of the vocabulary. The pass also stamps `data-shio-sections="0"` on a declaring
  container that holds none, because **the empty container is the case insertion exists for** and the
  section pass can only find a container by the sections in it. The declaration is the template's and
  the **resolution is the instance's**: `GET /agent/editable?include=insertable` answers, per
  container, each accepted type with its title, description and fields, and a type the layout names
  and the instance has not got comes back `provisioned: false` rather than dropped (SH169). The
  bridge sends the names with the selection; the catalogue stays the server's, because a copy of what
  is provisioned would be a second place for it to be wrong. The CLI's generated layout declares what
  the conversion provisions — on `<body>`, since `{{#getRelation sections}}` is a block and not an
  element, so `<body>` *is* the container — and both blueprints declare their eight.
- **And a curator places one (SH1039)** — the console reads that catalogue through
  `ShUniversalEditorService.insertable`, on the session and by `id:` address, the way SH996's
  publish routes already read `/agent/read`. The rail shows each accepted type by *its own title and
  description* rather than as a post-type name, which is the whole difference between a list and
  something to place. **An insert is two writes in that order**: create the section as a DRAFT, then
  replace the Page's array — the order decides what a failure leaves, since a new post the page does
  not name is something a curator finds in the browser, while a page naming a post that was never
  created is a dangling reference on a live page. The reference is spelled the way the array already
  spells its entries, a furl where the neighbours are paths and the id where they are ids. The frame
  **reloads** afterwards, which is not laziness: a new section's markup comes from the template, so
  there is nothing the bridge could draw optimistically the way it does for a move, and `/preview`
  is draft-preferred so the reload shows what was written. `above`/`below` say *where*; SH1105 adds a
  drag onto a caret, which names the same index.
  Two limits, both deliberate: the rail appears only where the page composes **exactly one**
  container, because `sh:component-selected` says how many a container holds and not *which* one it
  is, so a second list would be written into by a guess; and a declared type this instance has not
  provisioned is **shown and not offered**, for the same reason the server reports it rather than
  dropping it.
- **And the rail shows the shape, not a sentence (SH1105)** — each offered type carries a thumbnail
  from `GET /api/v2/render/section-preview?page=&field=&type=`, which is
  `ShPageRenderService.sectionPreview`: the page rendered through **`renderPage`, the preview path
  and never a second one**, with `field` proposed as one transient section of that type. The section
  post exists only for that render — `ShRenderHelpers.withTransient` lets `resolveReference` find it
  without a row — and its text fields (`Text`, `Text Area`, `HTML Editor`) carry their own labels as
  placeholder text; a File or combo field stays empty, since a label there is a broken image or a
  choice that does not exist. The answer is the page's `<head>` plus the one `data-shio-section`
  element, scripts removed; the console draws it at a quarter scale in an `<iframe sandbox="">`, so
  the site's stylesheet cannot restyle the console and the thumbnail runs nothing. HTML, not a
  raster — the instance runs no browser. Console-only in `ShAgentReadReachabilityLintTest`: the same
  choice is data at `include=insertable`. **A card drags onto a caret between sections**: the bridge
  adds `sections` — every box of the selection's container — to `sh:component-selected` and
  `sh:section-geometry`; while a drag is in flight a transparent layer covers the frame (another
  origin, which would otherwise take the drag events) and `gapIndex` maps the pointer to a gap, a
  section's midpoint being the boundary. Above, Below, At end and a drop all place through
  `section-placement.ts`'s `placeAt`, so a drop under section `i` writes the array Below writes with
  `i` selected — `section-placement.test.mjs` asserts exactly that. The caret needs a selection,
  since the boxes ride on it.
- **A save says what moved (SH1109)** — the editor takes the page's `digest` and `appearanceDigest`
  from `GET /agent/render?address=id:<page>` (draft-preferred) before the write and again after,
  and `src/lib/save-delta.ts` turns the pair and the change log into one line in the dock (kind
  `saves`): the fields written, whether the structure moved, whether the appearance moved. A
  **text-only** edit that moved the appearance is reported with an attention tone — a reflow nobody
  asked for, the defect inline editing is likeliest to cause. A digest that could not be read is
  `not checked`, never `unchanged`. The log also serialises as `{changed:[{id, fields}]}`, the
  change-set shape the agent path answers with. **Measured first**: one digest of the marketing
  package's composed home page is a median of about 41 ms (`ShBlueprintRendersTest`'s SH1109 test
  prints it), so the pair is taken on every save rather than on request.
- **The panel draws the field the model declares (SH1037)** — it used to be a bare `<Input>` for an
  `attr` field and read-only text for everything else, so a `Date` was a string, a `Check Box` was
  `"1"` and a `Content Select` was a UUID — and `affordance="panel"`, the field a caret cannot
  express, is precisely the one that lands there. The console already owns a widget per kind
  (`getWidget`) and already fetches the type to resolve labels, so the declared field is synthesised
  into an `ShPostAttr` (`attrFromDeclared`) rather than read a third time. A declared `Text` is
  deliberately left as the plain line: the map's fallback *is* `TextWidget`, so "declared Text" and
  "nothing declared" would be indistinguishable, and the plain line is the honest render of both.
- **The public delivery route, recovered rather than designed (SH451)** — `ShSitesAPI` maps
  `GET /sites/**` and splits it **by position**, which is what branch `0.3.8`'s
  `ShSitesContextURLProcess` did: `/sites/<site-furl>/<format>/<locale>/<content-path…>`, with the
  site resolved SH181-style, and `default`/`en-us` as the defaults when the segments are absent. Two
  variants ship with it and both are here — **`x-sh-site`** names the site as a header so the URL
  carries only the content path (how one host serves one site), and **`sh-format`** replaces the
  format segment. The split is a `record` with a static factory (`ShSitesAPI.Address`) precisely
  because it *is* the recovered artefact: a claim about what a 0.3.x URL means has to be assertable
  without a servlet or it is a claim about a comment.
  **Two decisions.** The path stays **plural** — `/sites` preserves the URLs of anything migrating
  from 0.3.x and singular buys nothing. `<format>` is **vestigial**: kept so the shape is the old
  one, read (a URL that names one is still a URL, and `sh-format` still replaces it, and the *prefix*
  every link is rewritten with keeps whatever the caller used — a reader who asked for `pt-br` must
  not have that choice dropped on their first click) and then **ignored**, because a layout comes
  from `postTypeLayout` (SH328) and making the segment a second binding table is a feature rather
  than a recovery.
  **And vestigial means *fixed*, which is not what it first shipped as (SH457).** "Carries no
  behaviour" was implemented as **accepts anything**, and the two differ exactly where it costs: with
  any value in that slot and no segments after it the content path is empty, so the resolver returns
  the home page and the answer is a **200**. Measured: `GET /sites/publico/viglet-logo.png` answered
  200 `text/html` with the home page's 10265 bytes, and so did `/sites/publico/qualquer`. Four probes
  place it — `/sites/<site>/` is the home and right, `/sites/<site>/default/en-us/nao-existe` and
  `/sites/<site>/a/b/c/d/e` are teaching 404s and right, and the **three-segment** form is the one
  that lies. That shape is **what every root-relative asset becomes**: a page served at
  `/sites/publico/` carrying `<img src="/viglet-logo.png">` requests exactly that URL, and the
  browser is handed HTML where it expected an image — so a missing asset stopped being a 404 an
  author can see and became a silent soft-200, on the one surface `verify`'s route checks cannot
  reach. A format that is not `default` is now `UnknownFormatException`, and the rule applies to
  `sh-format` too: an override is not an escape from it, or the two sources disagree about one axis
  and a reader has to know which is checked. **404 and not 400** — the sender is a browser resolving
  an `<img>`, not somebody who typed a malformed URL, so "that is not here" is the answer an author
  can act on; the teaching body still carries the rule, and its `fix` names where the path should
  have gone, which is the rewrite whose absence is why the request arrived there at all. The
  **locale** segment has the same shape (read into the prefix, never rendered with) and is
  deliberately left to **SH459**: the format check covers every case this defect measured, because a
  root-relative reference puts its first segment in the *format* slot.
  **Six guards, inverted rather than dropped**, and that is the whole difference between the routes:
  **anonymous** (the path has been `permitAll` since before the renderer existed — a latent hazard
  while nothing served there, and now the point), **PUBLISHED and never DRAFT** (the route takes no
  `state`; SH278 then means the *whole model* must be published, and the 409 that says so names the
  layout — four `post.publish` ops, not one), **no banner**, **no editor bridge**, **cacheable**
  (`public, max-age=300` — five minutes, because a delivery route whose invalidation story is "wait"
  is one people put a CDN in front of and purge by hand), **indexable** (no `X-Robots-Tag`: a
  published page a crawler is told to ignore is not published), and links **rewritten into the public
  space**. That last is `ShPreviewLinks.rewriteInto`, parameterised rather than copied — SH336's rule
  is not about the preview, it is that a link is rewritten *only when its path is a URL this site
  publishes*, and that is identical here; what differs is the prefix. SH437's asset fallback and
  SH385's redirect are inherited by the same route, at public URLs.
- **The locale segment is checked against the site's own content (SH459)** — SH457 fixed the format
  slot; its twin one slot along was left, and lied the same way: `Address.of` reads the locale into
  the prefix and never passes it to the renderer, so with any value there and no segments after it
  the content path is empty and `/sites/<site>/default/xx-yy/` answered the **home page with a 200**.
  The two are not reachable by one probe, which is why one task could not close both — a
  root-relative reference puts its first segment in the *format* slot, so SH457's check caught every
  case it measured. **"Vestigial means fixed" is the wrong answer here**: pinning the segment to
  `en-us` would make a Portuguese site's public URLs say `en-us`, a lie of a different kind and a
  statement about a multi-language CMS that a route-lenience fix is not entitled to make. So the
  rule is a *fact about the site* — accepted when the site has published content in that locale, or
  when it is `DEFAULT_LOCALE` (which every URL this route builds writes, so refusing it would break
  a pt-br site's own navigation to fix its address bar). It stays true when locale selection ships,
  because that is the same set. The default short-circuits, so only a URL that actually names
  another locale pays the site read and the projection; `localesOf` lives on `ShPageRenderService`
  because `siteByKey` (SH181's id → furl → name order) does, and a second copy of that on the
  controller is the copy that stops matching. Since SH1057 the set also holds the locales a site
  *declares* (SH1056), since a URL may name one before any page is written in it and the fallback
  chain answers it.
- **The locale segment selects the content (SH1057)** — `Address.selectedLocale()` reaches
  `ShPageRenderService.render(…, locale)`, and `DEFAULT_LOCALE` is *no* selection (it is every link
  the route has built, and reading it as English would send a Portuguese site's home to its English
  translation). A translation lives at **its own URL** — `translate` goes through `create`, which
  gives it a free furl — and shares `translationGroupId` with its source, so "`/pt-br/about`" means
  the pt-br member of `/about`'s group. `pageInLocale` walks the site's declared fallback chain (the
  locale asked for alone when nothing is declared), and at each locale takes, in order: the page at
  the path if it is already in it; a row at the same path in it (the unique key lets two
  translations share a URL, and a locale-blind read there would redirect to itself for ever); a
  group member elsewhere, answered with a **302** to that member's URL in the reader's locale space.
  Serving the member in place would put one page at two addresses — SH467's split — and 302 rather
  than 301 because the missing translation may be written tomorrow. When nothing answers, the page
  at the path is served as it always was, which is what keeps a single-language site unchanged.
  Only the public route passes a locale; the preview resolves as before. `ShSitesLocaleSelectionTest`
  holds both directions, the chain, the untranslated page, the route default and the shared URL.
- **A declared site is addressed in its own tag, not `en-us` (SH1057)** — `DEFAULT_LOCALE` is 0.3.8's
  label for "no locale named", so a pt-br site's links said `en-us` over content stored as `pt-br`.
  Once a site *declares* a default, a request in the legacy label or in none is a **302** to the
  same path in that locale's space (`toDeclaredLocale`), and the links the reader then follows stay
  there. **Only a declared site moves**: moving every existing site would serve each page at two
  addresses to fix a label nobody chose, and declaring a policy is the act of choosing. The header
  form carries no locale and is left alone, as is a site whose declared default is `en-us` itself.
  The tag rides out on `ShRenderedPage.siteLocale` because the render already holds the site, so it
  costs no query — only the render before the 302, once per reader on a legacy URL.
- **What the fallback hides, verify says (SH1130)** — the chain above means a declared locale with
  nothing written in it answers every URL in it with 200 and another language's page, so no route
  check can see it. `verify`'s `content` group reports it as `locale-declared-unwritten` against
  `site:<name>`, naming the language the chain actually serves, and the mirror as
  `locale-undeclared-content`: published content in a locale the policy does not enable, which no
  switcher links to. Both are warnings, published rows only, silent on an undeclared site, and one
  query (`findDistinctLocaleTypesBySiteIdIn`) for every declared site in the run. The policy is read
  through `ShSiteLocaleService.declared`, which records an unreadable one as an incident, rather than
  parsed here behind a `catch` that `ShSilentCatchInventoryTest` would rightly count. **Templates and
  assets are left out by type** (`Region`, `Theme`, `PageLayout`, `SiteScripts`, `File`, `Redirect`),
  because every row gets `en` on write: without that, a site declaring only `pt-br` would be told its
  layouts are undeclared English, and English layouts would count as the English pages nobody wrote.
  The type is resolved by id in the service, never by `p.shPostType.name` in the JPQL (the
  `@TenantId` join rule). Not `isPage`: a headless site binds no layouts, and its content still has
  a language. `ShVerifySiteLocalesTest` holds both findings, the draft and template exclusions and
  the undeclared site's silence.
- **A `File` post is reachable at the path its source used, and `src` joins the rewrite (SH437)** —
  a static file's bytes are public at `/file_source/<postId>/<fileName>` (SH38), keyed by an id no
  source ever chose, so a fidelity replica's `<img src="/img/hero.png">` was as broken with the bytes
  uploaded as without them. SH429 called keeping the source's URL space the better option and
  measured that it *was not available*; this is the route it needed. **`ShSiteAssets`** walks a
  site-relative path as **folder chain + file name** — SH145's asset addressing (`shio/assets/<site>
  <chain>/<name>`) read backwards, not a new scheme — and **`ShSiteAssetDelivery`** streams the bytes
  through the same storage the id-keyed route uses. `/file_source/**` is unchanged and `{{file_url}}`
  still points there; the whole feature is the addressing.
  **One mapping, with the asset as a fallback.** Two `@GetMapping("/{siteKey}/**")` differing only by
  `produces` was the obvious shape and is wrong: `Accept: */*` matches both and Spring's choice is not
  a decision this class made — measured, the home page started answering `render-asset-unknown` for
  `/`. So `page` catches `UnknownPageException`, tries the asset, and rethrows the **page's** 404 when
  that misses too, because a path with neither is far more often a page somebody meant to write. That
  catch is the seventh row in `ShSilentCatchInventoryTest`'s `render` count, declared as control flow.
  **The headers are not the page's**: `max-age=300, private` and `noindex`, because an asset is not a
  draft page — its bytes are immutable while the post holds them, and `no-store` on forty images is a
  round trip each per reload. Private, since the route is authenticated.
  **And `src` joins `ShPreviewLinks`**, against this index rather than the URL one — the class note's
  "deliberately not `src`" was right about the index it had. Same rule as `href`: rewrite only what
  this site actually serves. One lookup per *distinct* path, because a fidelity page is a whole source
  document. `/file_source/…` is excluded explicitly, so a template that resolved an asset properly is
  never re-pointed. **The stored markup is never edited** — which is the point: `snapshot --against`
  compares a replica to its source, and an edited `src` is a difference it structurally cannot see.
- **And the same rule inside `<style>`, because one page carried two of them (SH461)** —
  a fidelity replica's markup is verbatim, so its `<img src>` is the source's own path and SH437's
  pass points it at this site's copy. Its stylesheet was not: the capture rewrote every `url()` to
  the **asset tree's** path (`/<site>/<chain>/<name>`), which is addressable in `shio/assets/` and
  nowhere a browser can ask for. So the logo loaded and the webfonts 404'd, on one page, from one
  capture that had fetched them correctly and a route that serves them when asked properly. Fixed on
  both sides and by *deleting* the second rule rather than adding a third: the CLI's `rewriteUrls`
  now emits the **source's** path (the asset map stays the gate — an uncaptured reference is still
  left alone and named, SH378), and `ShPreviewLinks` walks inline `<style>` blocks with the same
  `rewriteAsset` lookup, the same prefix and the same leave-it-alone default. Quoting is preserved
  as the author wrote it; a `<link rel=stylesheet>` needs nothing, because Shio's own theme is
  *inlined* by the layout (SH183), which is exactly why there is a `<style>` to walk.
- **Both spellings of a path answer, and the content says which is canonical (SH467, route half)** —
  a source serves `/shio/` with a 200 and answers `/shio` with a **301** to it; its replica did the
  exact opposite, `/shio` 200 and `/shio/` **404**, on the public route and the preview alike. The
  cause is not the route: `ShSitesAPI.Address` preserves the trailing slash faithfully (its own test
  says so) and `ShAddress.normalizePath` collapses it, so the stored `furl` is the source's path with
  one character removed. SH385's rule is that a replica keeps the original's URL space, and this
  broke it in the direction hardest to notice — every page still resolves, `verify` is clean, a crawl
  succeeds, and what fails is everything carrying the *source's* URLs: a sitemap, an inbound link, a
  bookmark, and the captured markup a fidelity replica serves. `ShPageRenderService.pageAt` now tries
  the other trailing-slash form when the exact one misses and throws SH385's own
  `ShPageRedirectException` toward **the stored `furl`** — the direction is read off the content, not
  decided here, so the same code redirects the other way the day a furl carries the slash. In the
  service and not in either controller: one behaviour, and both routes already turn that exception
  into their own address space. **Permanent**, unlike SH385's opt-in 302, because two spellings of one
  path will not stop meaning the same page. `/` has no other form, and a path neither form serves
  still raises `UnknownPageException` so the SH437 asset fallback is reached.
  **And a redirect now keeps the reader's query** (`ShSitesAPI.withQueryOf`, shared by both routes):
  invisible while every redirect came from a `Redirect` post somebody wrote, and immediately wrong
  once the route canonicalises by itself — `/search/?q=shio` arriving at `/search` with no term is a
  worse answer than the 404 it replaced. Skipped when the target declares a query of its own.
  **What this half deliberately did *not* do** was store the trailing slash the source declared, on
  the reading that it means splitting `/shio` and `/shio/` into two addresses — `normalizePath` is
  the instance's one canonical path form precisely so they are one. That reading was scoped twice
  and the second pass reached a different answer; the write half below is what it settled on, and
  the design is written up in
  [`docs/specs/SH467-canonical-slash.md`](../specs/SH467-canonical-slash.md).
- **One generator decides what a spelling is, and it landed before anything stored one (SH583)** —
  the paragraph above says nine lookups would become slash-sensitive. Measured at `077b115` it is
  **thirteen, across seven classes**, and the tolerance meant to bridge `/shio` and `/shio/` was not
  one function: `ShAddressResolver.furlCandidates`, `ShAgentBatchService.furlCandidates` (a private
  copy of it), `ShCdaService.urlCandidates` under a third name, and this route's own redirect rule.
  They agreed by **accident** — nothing was ever stored with a trailing slash, so all four were asked
  the same question. `normalizePath`'s javadoc had predicted exactly this and named the instrument
  that did not exist. Now `com.viglet.shio.url.ShFurlSpellings` is the one place: `canonical` (the
  body `ShAddress.normalizePath` used to carry, which now delegates), `candidates` (the four
  spellings of a path, **requested one first** so a stored `/x/` is not resolved to a stale `/x`),
  and `otherSlashForm` (what this route's 301 asks). Six readers route through it — resolver, batch,
  CDA, `verify`, `/agent/render` and copy/move — and `pageWithFurl` is the one deliberate
  *non*-tolerant reader, because tolerating both here would serve one page at two URLs, which is the
  split SH467 exists to close; the tolerance is the redirect. `ShFurlSpellingLintTest` fails on a
  fifth generator and on a post-`furl` reader that does not ask this one, with folder and site
  `furl`s named in its exclusions as the different namespace they are (SH79).
  `ShFurlSpellingAgreementTest` persists the row SH467 will write and asserts the readers agree
  about it — the assertion that
  would have caught the failure mode, which is a page that renders in `/preview`, answers **404** on
  the CDA and **verifies clean**.
- **The declared spelling is a write, not an identity — and that is the whole design (SH467, write
  half)** — the measurement that reopened it: `openviglet.github.io` serves `/model-catalog/` and
  301s `/model-catalog` to it, so a mirrored replica makes a document-relative `./app/main.js`
  resolve to `/app/main.js` instead of `/model-catalog/app/main.js`. `verify` calls it
  `page-reference-broken`, the only reference error on an otherwise clean authorable replica. Not a
  301 cost: a redirect does not fix a reference already resolved against the wrong base.
  Two options were on the table and **both are wrong**. A fourth component on `ShAddress` makes
  `post:S/x` and `post:S/x/` two unequal addresses for one post — the P5 identity split, and the
  render digest hashes the address. And `data.furl` is **`ShSite`'s**, written by `siteUpsert`;
  there is no `data.furl` on a post write and there cannot casually be one, because SH168 refuses
  any `data` key the post-type does not declare and carving an exception into the guard whose point
  is that it has none is worse than the defect. So the spelling rides on the **op**, beside
  `folder`, `locale` and `translationOf` — SH348's `translationOf` is the precedent, tenth component
  with a nine-field compatibility constructor under it. Two lines were carrying the loss:
  `ShAgentDesiredStateService` derives the address from `post.url()` and now passes the url along
  too, and `ShAgentBatchService` writes `declaredFurl(op, address)` instead of `address.path()`.
  **The storage layer needed no change at all** — `ShPostUnifiedWriteService.normalizeUrl`
  guarantees the *leading* slash and nothing else, so it stores what it is handed.
  `declaredFurl` refuses a url that is not a spelling of the address (`ShFurlSpellings.candidates`,
  the fifth reader and the reason SH583 went first), and ignores rather than 400s, because every
  pre-SH467 caller sends no url at all.
  **Honoured on create and nowhere else**, like `locale` and `translationOf`, and here the reason is
  sharper than theirs: a post's file path in `shio/content/**` *is* its URL, so `/x` and `/x/` are
  one filename and the projection structurally cannot carry the slash. An update that re-spelled
  would be un-done by the next `shio push`. Changing a live post's canonical URL stays `post.move`'s
  gesture (P7). `ShAgentCanonicalSlashTest` asserts it at the boundary it crossed — declare the
  slash, and the row carries it — and pairs with `ShRenderRedirectTest`, whose `/docs/` fixture was
  hand-persisted with a comment saying it was what the capture half needed.
- **A verbatim `href` is a hardcoded base, so the fallback is inverted (SH466)** — SH336's rule was
  *rewrite only what the index resolves, leave the rest alone*, and what it produced is a page
  carrying two kinds of `href` where one works. A replicated page held `href="/turing/"` — the
  source's path, verbatim — on a page served from `/sites/vigletorg/default/en-us/`, beside five
  links that were rewritten; `turing` was **unpublished** at that moment, so it was not in the index,
  and the same page on `/preview` had all six because the preview sees DRAFT. **The output depended
  on publication state**, and the failure appears exactly when a site is half published, which is
  every site mid-migration. Both spaces this pass writes into are different from `/`, so leaving a
  path alone is the one output guaranteed to point somewhere else. Now **every root-relative `href`
  is rebased**, resolvable or not: it 404s *inside* the replica, where `routes` already reports it —
  a missing page is a finding, a link out of the site is a silent exit. The exception is named
  directly instead of inferred from an index — `ShPreviewLinks.INSTANCE_ROUTES`, the five first
  segments the **instance** serves (`api`, `mcp`, `file_source`, `preview`, `sites`); the last two
  are the prefixes this pass writes, which is what keeps it idempotent now that the index no longer
  decides. The href pass therefore reads no index and no longer depends on `state`; `rewriteAsset`
  shares the exclusion but **not** the inverted default, because an unresolvable `src` is bytes that
  are not here and rebasing it would break a reference `snapshot --against` can still measure.
  Mechanism (b), the click interception, keeps the narrower index rule — it only ever *adds* cover
  for JS-built links, and the route this defect appeared on carries no editor bridge at all.
  The instrument is two-sided: `ShPageRenderTest` asserts no href survives the pass except an
  instance route, and `replication.conformance.mjs` asserts the same of a served replica, where the
  fixture's `/admin` had been the live case all along.
- **And a relative reference is worse than an absolute one (SH512)** — SH336's other exclusion said a
  document-relative reference "already resolves correctly under the prefix", which holds only while
  the replica's serving depth equals the source's, and it never does. A replica of
  `vilt-group.com/en/` lost its logo, its hero background and fourteen images on a run where every
  asset uploaded and `verify` was clean: `/sites/vilt/default/en-us/img/vilt-logo.svg` answers
  **200** and the markup asked for `./../../img/vilt-logo.svg` — which means `/img/x` from one
  segment deep and `/sites/vilt/img/x` from four. **44 of that page's references were relative**, and
  an absolute one is at least wrong in a *fixed* way; a relative one is wrong by however much the
  depth differs, so the breakage moves with the site's furl and the locale segment. `toSitePath`
  resolves it against the page's own URL first — `.` and `..` collapsed, clamped at the root — and
  everything after that is the rule SH466 already wrote. **The base is the request's content path,
  passed in by both controllers, not the post's furl**: `ShAddress.normalizePath` drops a trailing
  slash (correctly, for addressing) and that slash is exactly one level of `../`, so `/en/` and `/en`
  are different bases and only the request knows which the reader used. Left alone: any scheme
  (`mailto:`, `tel:`, `data:`, `javascript:` — each of which a naive path resolution mangles),
  protocol-relative, a bare `#fragment`. **Widened at the same time from `href`/`src` to every
  reference the page carries** — `srcset` (per candidate; an attribute holding a `data:` URI is
  skipped whole, since splitting on commas would tear it), `poster`, and `url()` in a `style`
  *attribute* as well as in a `<style>` element. That last one is half of SH515's evidence, and the
  reason the list is widened in one go rather than one entry per incident.
- **One list, asserted across the wire (SH515)** — the collector (`clone.mjs`, what a capture
  downloads) and the rewriter (`ShPreviewLinks`, what a served page asks for) are two readings of one
  question, and keeping them in step by hand produced four tasks: SH452 and SH455 for stylesheet
  `url()`, SH473 for the script bundle, SH515 for style attributes and `fetch()`. The measured
  consequence was three 404s **and a crash** — `insightsData.find is not a function`, because
  `content/home-insights.json` was rebased onto the serving base and never fetched, so the page
  parsed a 404 body. *The pass that knew the reference existed was the one that could not fetch it.*
  Both sides gained the two missing shapes — the collector reads the `style` attribute and inline
  `<script>` bodies (`scriptBlocks`, `styleBlocks`' twin, because `stripNonMarkup` blanks them); the
  rewriter rebases literal `fetch()` arguments. `FETCH_CALL` is a deliberate second copy of one
  grammar for `CSS_URL`'s reason, and the fixture is what stops them drifting:
  **`cli/test/fixtures/references-page.html`** carries every reference shape *and* the expected
  resolved set in one block, `clone.test.mjs` asserts the collector produces it and
  `ShReferenceParityTest` asserts the rewriter resolves it. A `fetch()` whose argument is computed —
  a variable, or a template literal holding `${…}` — is known to **neither** side, together: a
  rewrite of a fragment would corrupt a string the pass does not understand and a download would
  fetch what the page never asks for. **The fixture found a disagreement on its first run**: a
  `<link rel=stylesheet|icon|manifest|preload>` is an *asset* to the collector and was a
  *navigation* to the rewriter, so a favicon this site does not serve was rebased into the replica —
  SH466's unconditional rebase is the argument for navigations, and for bytes the opposite rule
  holds. `ASSET_RELS` is now the collector's own five, and `page-reference-broken` (SH516) reports
  the missing one either way.
- **`{{theme.css}}` is raw, and it had to become so before any of that could work (SH461)** —
  the documented layout is `<style>{{theme.css}}</style>`, the double stash escapes, and CSS decodes
  no entities, so `url("/fonts/inter.woff2")` reached the browser as `url(&quot;/fonts/…&quot;)`: an
  invalid `@font-face` whatever it pointed at. Nothing else in a stylesheet carries `"`, `<`, `>` or
  `&`, so the page looked styled and only the references broke — the failure was invisible in
  proportion to how ordinary the CSS was. The fix is in `ShRenderModel.themeFrame`
  (`Handlebars.SafeString`), **not** in the layouts: fixing the generated layout would cure one
  template and leave every hand-written one broken. `<style>` and `<script>` are raw-text elements,
  so entity escaping is not that context's encoding; what it bought was a defence against `</style>`
  in a `Theme` written by whoever also writes the `PageLayout` that emits it through `{{{content}}}`
  three lines below — no privilege boundary, so nothing to defend.
- **And on a row too, because SH549 added a second emission of the same stylesheet (SH555)** —
  a converted site's verbatim pages get the captured CSS *per page*, through
  `{{#if content}}{{#getRelation "/theme/captured"}}<style>{{CSS}}</style>{{/getRelation}}{{/if}}`.
  That one reads a plain field off a `getRelation` row, which `themeFrame` never touches, so the
  stored `Theme` was clean at 1,061,481 characters and zero entities while the page it produced
  carried **378** — `src:url(&quot;/assets/inter-…-BOeWTOD4.woff2&quot;)`. Not cosmetic: ten
  `page-reference-broken` errors on the one verbatim page of an otherwise clean site, each naming a
  path beginning with a literal `&quot;`, and the gate exits 1. The fidelity path is the contrast
  that locates it — same theme, emitted through `theme.css`, sixty clean `url()` and no entity at
  all — so the defect was in neither the capture, the store nor the escaping rule: **one of the two
  emissions was written as a field read**. `ShRenderHelpers.rawTextFields` gives it SH461's answer,
  keyed by **(post-type, field)** rather than by field name — the fields the product declares as
  `Ace Editor - CSS/JavaScript` and a template can only emit into a raw-text element. An `Article`
  whose author happens to add a field called `CSS` keeps escaping, which is the case escaping is
  for. The fixture is half the lesson: SH549's sentinel was `.sh549-captured-marker{color:#123456}`
  — no `"`, no `&` — so it survived escaping unchanged and passed on a page whose every `url()` had
  been destroyed. It carries an `@font-face` now.
- **And the same sentence had one more field in it (SH850)** — *one of the two emissions was written
  as a field read*. SH555 gave that read SH461's answer on **escaping** and left the **composition**:
  the stylesheet a theme renders as is `ShThemeTokens.assets` — its `TOKENS` compiled into a `:root`
  block and prepended — and a `getRelation` row carried the stored `CSS` bytes. Harmless while the
  two fields were independent, page-wide the moment they stopped being: SH424 **removes** a lifted
  declaration from `CSS` because the source re-declaring it later at equal specificity is what made
  every token inert, on the stated promise that the compiled block re-declares it. On this path
  nothing did. Measured on a replica one round apart — distinct undefined properties **62 → 194**,
  dangling `var()` occurrences **231 → 958**; of 133 lifted properties, **132** were declared nowhere
  on the served page and all 132 were still read by a rule that stayed (Tailwind's `@theme` layer,
  which every utility class in the sheet resolves through). A `var()` with no declaration and no
  fallback makes its declaration invalid at computed-value time, so the element inherits: colour,
  blur and animation gone across the verbatim chrome — the half of a mixed page a curator recognises
  as the site. **The fix is one composition, not a second copy.** `ShRenderModel.themeAssetsOf` owns
  it, with the incident SH270 requires, and both emissions ask it: `ShPageRenderService.themeAssets`
  is now a call, and `ShRenderHelpers.composedStylesheet` runs *ahead* of `rawTextFields` so the
  composed value is what gets SH555's answer. Two copies written where each was needed is how they
  diverged twice; a third would have been nobody's fault in particular. The assertion is §XVII.137's
  own — **no served page has a `var()` it does not declare** — read off the rendered HTML rather than
  off any field, because the store held a coherent pair and the page did not.
- **`{{theme.title}}` sits beside it and is escaped, which is the same decision read the other way
  (SH829)** — the model's theme frame carries the resolved `Theme` post's title, so a layout can
  write `<style data-shio-theme="{{theme.title}}">{{theme.css}}</style>` and the render digest's
  region table can print `VigR3 (captured)` where it printed `style[2]`. Two stashes, two contexts,
  two encodings: the CSS is raw because a `<style>` is a raw-text element, and the title is escaped
  because it is going into an **attribute** — the reason `ShRegionComposer` put a theme's title on
  `<body>` rather than into a comment (SH297). Getting the pair backwards is invisible on an ordinary
  title and breaks the digest's key on the first one with an `&` in it, which is what
  `ShPageRenderTest` asserts. `null` rather than an empty string when no theme resolved, so
  `data-shio-theme=""` reads as *no post* and the digest keeps `style[N]`; the title reaches
  `ShRenderModel.of` as a fourth argument from `ShPageRenderService`, which already resolved it for
  SH297's marker, rather than as a fourth component on `ShThemeAssets` — that record is the theme's
  *assets*, built by `ShThemeTokens` from three fields of the post, none of which is its title.
- **The list is derived, because it stated a rule and held four of six (SH657)** — SH555 spelled the
  pairs by hand while its own sentence said *the fields declared as `Ace Editor - CSS/JavaScript`*,
  and `ShRenderPostTypes` declares **six**. `Theme.TOKENS` (SH195) and `SiteScripts.SCRIPTS` (SH389)
  were each added to that table on their own task, correctly, and neither reached the list — nothing
  joined them, so the rule lived in prose and the list in code and they drifted twice. Both are JSON
  in a JavaScript editor, so the failure they were one template away from is
  `<script type="application/json">{{TOKENS}}</script>` emitting `&quot;` for every key: a document
  that does not parse, which is SH461's defect and SH555's a third and fourth time. `RAW_TEXT_FIELDS`
  is now `ShRenderPostTypes.rawTextFields()` — computed from the widget, which is what the sentence
  always meant — so a code-editor field added tomorrow reaches the renderer on the same commit.
  `Ace Editor - HTML` stays out deliberately: a `PageLayout`'s or `Region`'s `HTML` is a *template*,
  rendered rather than interpolated, so it reaches no stash and calling it raw would say something
  true about a path that does not exist. `ShRawTextFieldParityTest` names all six pair by pair —
  because once two things cannot drift, nothing is left to tell a live rule from a vacuous one — and
  `ShByteStabilityLintTest` caught the first draft returning `Map.copyOf`, whose iteration order
  comes from a per-JVM salt (P5).
- **Both render-model verbs are ops, not just routes (SH186, SH326)** — `render.provision` is
  addressless and idempotent; **`data.reconcile:true`** on it reaches SH289's repair, which had
  stayed HTTP-only, so an MCP client could *diagnose* an old instance (`untitled-template`) and
  not fix it — and that finding's own `fix` named the route, the SH328 shape again. Opt-in and
  never implied by `ensure`: SH289's division is that `ensure` creates what is absent and is safe
  unattended, while reconcile **changes a type the instance already has**. One op rather than a
  tenth name, because a vocabulary is paid for by every caller that reads it (P3). **SH607 added
  `data.types` on the same argument** — the SH95 vocabulary by name, so a conversion can install
  the `Page` and `Section*` a bare instance does not ship. **SH871 added `data.fields`** on the
  same argument again — `{Type: [name]}`, appending each missing name to a type that exists as a
  plain optional `Text` through SH152's additive merge, which is how `convert --annotate` declares
  the fields it computed from somebody's markup and no catalogue could ever ship. Detail in
  [post-types.md](post-types.md).
- **Two URLs per frame, and the pair is the point (SH337)** — the model carries
  `post.url` / `site.url` (the fields as content spells them) *and* `post.canonical` /
  `site.baseUrl` (the same two facts through `ShSiteFrontEnd`, SH184). The raw pair could not
  answer a canonical tag, an `og:url` or a sitemap entry, because `site.url` is the **customer's
  deployed front end** and is blank on a site an agent just built; the resolved pair is never
  blank, falling back to `/preview/<site>`. Both stay because they answer different questions —
  *"has this been deployed?"* is `site.url`, and writing a fallback into it would destroy that
  question, which is the same argument `ShSiteFrontEnd` makes for resolving rather than storing.
  `canonical` is **composed** by `pageUrl` rather than left to `{{site.baseUrl}}{{post.url}}`: a
  furl is not guaranteed to lead with `/`, and that concatenation yields `/preview/Democontato`.
  What is **not** an option is making `{{link}}` preview-aware — it is content-facing, so the same
  field would mean two things in two renders (the coupling SH187 spent a task splitting apart).
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
  rest on SH183's refusing `TemplateLoader`) so it must carry its reason. One row **changed** under
  SH832: `{{./TEXT}}` was `SKIPPED` as *"explicitly relative; the lint cannot say whose scope it lands
  in"*, and that had it backwards — `./TEXT` is the one spelling whose scope is not in doubt, and a
  **bare** `TEXT` is the ambiguous one, resolved up the context chain. It is `REPORTED` now, which is
  what keeps the lint seeing the generated layout after SH832 respelled it.
- **The edit that was stored and never shown (SH870)** — SH864 decided where an annotated fidelity
  page's values live: **the field is the address**. `ShPostDraftMergeService` writes `data.<field>`
  on the post exactly as it does everywhere else, so the write path needed nothing; what was missing
  is the render half. A fidelity page's markup is stored verbatim, so a value written through the
  Universal Editor landed in the post and the page went on showing the source's own text — the editor
  accepting an edit the page denies, which is worse than refusing it. `ShRenderModel` now fills an
  annotated element's text from the post's own field before a template ever sees the value.
  **In the model and not in `ShRegionComposer`, which is what scopes it**: that class already selects
  `[sh-field]` — over the *whole composed document* — so substituting there would rewrite every
  **section** region too, whose text the template already produced from that same field, sometimes
  through a helper, and the rendered value would be overwritten by the raw one. Doing it to the
  field's *value* reaches only this post's data and only a field whose own value carries the
  annotation. Four properties, each asserted: **byte-identical until edited** — a value equal to the
  source's text, or no value at all, returns the string untouched and never parses, because parsing
  normalises markup and annotating would then move pixels, which is the measurement SH864 rests on;
  **the value is text**, so a field carrying `<script>` is escaped rather than parsed — one annotated
  element, one declared field, no expression language, and `content` is still emitted `{{{content}}}`
  without interpolation (SH464); **a collection path is left alone**, since `features.0.title`
  addresses a row the section's own template draws; and **an unannotated block is verbatim**, the
  annotation being the substitution point. SH868 is the half that stamps the annotations on.
  **SH872 made "no value at all" the ordinary case rather than the first one.** A conversion no
  longer writes the element's text into a key beside the markup — the text is in the markup, which
  is the storage SH868 proposed — so the only stored value an annotated page ever holds is one a
  curator typed. That is what `ShAnnotatedFields` exists for: `verify`'s `unknown-field` and the
  batch's SH168 check would both call that key undeclared, and the rule they ask instead is **a key
  annotated by the post's own markup is declared by that markup**. Scoped to a string this same
  post stores and to a flat name — the same boundary `substituteInto` draws one step along — so it
  cannot be used to put an arbitrary key past the model.
- **A reference carries two scopes, because a guard is not an owner (SH836)** — the scanner hands each
  reference the **innermost** block, which is right for *where it is written* and is what
  `ShTemplateFieldLint` was asking `rowKeysFor` about. That `switch` answers `FORM_FIELD_KEYS` for
  `form`, `IMAGE_KEYS` for `image` and `ROW_KEYS` for everything else — **including `if`**. So
  `{{#form}}{{#if required}}{{label}}{{/if}}{{/form}}` asked the *listing's* keys about `label` and
  fell through to *no post-type anywhere declares this*, on a correct form body: the exact false
  positive `FORM_FIELD_KEYS` was added to stop, reached a second way, and unreachable in the ordinary
  case because a guard is where a template puts most of its references. The record now carries
  `helperScope` beside `scope` — the nearest enclosing block that decides whose data is in scope — and
  the lint asks about that one. `scope` is unchanged and still pinned. **The larger half is the other
  direction:** `isPageScoped()` reads `helperScope`, so `{{#if headline}}{{headline}}{{/if}}` at the
  top of a layout is the *page's* reference again and reaches the **error** — *the bound post-type
  declares no such field* — which could never fire behind a guard before. And the rule is narrower
  than the task that filed it: only `if` and `unless` are transparent. `each` and `with` **rebind** the
  context, which is what they are for, and walking out through them reported a row's field as the
  page's — wrong on every listing, SH251's defect inverted. `ShTemplateFieldScannerTest.blocksClose`
  is what refused the filed version; a test pins the pair directly now.
- **And the larger half got its own assertion a task later (SH837)** — SH836 shipped with the false
  positive asserted and the *error* unasserted, because the lint fixture's one undeclared page field
  sat outside every guard and its one guarded reference was declared. So the fixture could not tell
  the fix from its absence on the half that matters, and the suite going 1915 → 1917 with no new
  failures was consistent with the branch working **and** with nothing reaching it. `strapline` is
  the case now: drawn inside `{{#if SUMMARY}}`, declared by nothing, reported as an **error** naming
  the bound post-type. What makes it a test rather than a coincidence is what it does when the
  walk-out is reverted — it does not vanish, it **drops to a warning**, the weaker claim the CMS
  makes about a listing whose rows it cannot enumerate. Two branches, one reference, and only the
  severity says which one it took. Whether Handlebars.java's
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
  **A theme reached by relation is used too, and the rule could not see it (SH558)** — SH549 gave a
  generated layout a *second* theme, pulled in per page with
  `{{#getRelation "/theme/captured"}}`, and `unused-theme` read only the `THEME` field. So the
  captured theme had no binding and was reported unused **on a site where it renders at a megabyte
  a page**. The rule was not wrong about what it read; it was wrong about *what a reference is*, the
  way SH516 was — but unlike SH516 this needs no page, because `getRelation`'s argument here is a
  **literal** address and the reference is static in the template source. Two costs, and the second
  is the one that mattered: a warning that cannot be cleared on the file every conversion writes is
  how a warning list stops being read, and the reverse question — *is this theme reachable* — had a
  wrong answer, so a cleanup trusting it would delete a theme eleven pages were using.
  `ShTemplateGraph.relatedThemeTitles` scans **every template the page assembles from**, layout and
  regions alike, resolving each literal by friendly URL against the site's own rows — the same
  normalization `ShRenderHelpers.resolveReference` does at render time, which is SH281's rule again.
  A path expression (`{{#getRelation sections}}`) is deliberately *not* matched: it names a field,
  decided per page, and no static read of a template can say which theme that is.
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
  **`{{#navigation}}` yields the `url` its own docstring promised from SH183 to SH381**, and the
  two-release gap is the interesting part: the comment said `{name, url, id}` and the body built
  `{id, name, title}`, so the one helper a primary menu exists for rendered labels nobody could
  click, and a fixture in `ShTemplateFieldLintTest` had been writing `<a href="{{url}}">` against a
  field that was never there. It was not a one-line fix because **a folder has no URL** — SH79 keeps
  chains and friendly URLs in separate namespaces on purpose — so the value is a *decision*: the
  folder's **index post**, meaning the post **inside that folder** whose friendly URL is the folder's
  own chain (`ShAddressResolver.addressOf`, never a second parent walk: that method knows SH128's
  site-lives-on-the-root invariant). Three properties, each a test in
  `ShRenderNavigationUrlTest`. The match is **case-insensitive**, which follows from SH79 rather than
  being a convenience — a folder name is a label (`Solutions`) and a furl is a slug (`/solutions`), so
  byte equality between two deliberately-different namespaces would make the field almost never
  resolve. Only the folder's **own** posts are candidates, or a page in an unrelated folder captures
  a menu entry it does not own. And **an absent `url` beside a present `name` is a legitimate row**,
  not a defect: it is a group heading, which is what a nested menu is made of, so a template branches
  on `{{#if url}}`. A path is **never fabricated** — two candidates differing only in case yield no
  url rather than a guess, because choosing by sort order is a fabricated path wearing a
  deterministic hat, and a menu whose links 404 is worse than one whose entries are plain text since
  the second is visibly unfinished. `link` mirrors `url` following `row()`'s own precedent.
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
  **`{{#menu title="Primary"}}` is the curated menu, and the folder tree was never going to be one
  (SH382)** — which is the sentence SH381 left standing: giving a folder row its `url` made
  `{{#navigation}}` usable and did not make it a *menu*. Navigation is **curated** — a label that
  differs from the page title, groups that are not folders, an editorial ordering, entries that leave
  the site, a page deliberately left out, one menu per locale — and a folder chain expresses none of
  them, so every site hand-wrote its nav into every layout and each copy drifted. The vocabulary is
  **content**: `Menu` (a title plus `items`, a `references: post` Multi Select) and `MenuItem` (a
  `label`, a `target`, an `href`, a `rel`) live in `@viglet/shio-sections`, which is SH249's test
  applied — it needs no new mechanism, so `post.upsert`, the file projection, the section overlay and
  SH154's `dangling-reference` all work on it the day it exists, and that last one is why a menu entry
  naming a deleted page is reported with **no lint written for it**. Five decisions:
  **the helper knows no post-type name** — the server knowing a JS package's vocabulary by string
  would make the renderer opinionated about a convention instead of a model, so the contract is
  *structural* (a post whose `items` lists addresses) and anybody's post-type qualifies; the fixture
  deliberately uses its own names, or it could not tell a structural match from a hardcoded one.
  **A page listed directly is a link with its own title**, so the smallest useful menu is one post
  rather than six — the cheap case has to stay cheap or nobody adopts the vocabulary and the
  hand-written nav survives. **A nested menu is a row with a `name` and no `url`**, which is SH381's
  own convention reused rather than re-invented: a template branches on `{{#if url}}` and recurses
  with `{{#menu title=title}}`, so nesting needs no new row key, no new helper and no depth limit —
  and `ROW_KEYS` already carried `name`, so nothing had to be declared. **Two candidates yield
  nothing**, SH381's rule again (a title is not unique the way a furl is): an empty menu is visibly
  unfinished, somebody else's menu is not. **A `target` that resolves to nothing yields no url** and
  does *not* fall through to the item's own address — the fallback would send a visitor to the wrapper
  post, a page with a label and no content — and it is not marked either, per this file's standing
  split. No default ceiling, unlike every other helper here: a listing's length is the site's and
  grows on its own, a menu's is *authored*, so cutting it at 50 would silently drop the end of
  something somebody wrote by hand. **One claim that turned out to be false, kept because the
  correction is the interesting part**: the first draft argued the cycle guard prevented a hang. It
  cannot — `ShTemplateEngine` refuses partials outright, so a template cannot include itself and the
  recursion is bounded by the layout's own nesting depth whatever the content says. What the guard
  (the menus open on this thread, not a depth cap) actually buys is that a two-menu cycle nested three
  deep renders `{{else}}` instead of *the cycle* — wrong output that looks like a real menu, at a site
  scan per level. The test nests three deep for the same reason: two levels never reach the cycle, so
  a shallower fixture would have passed with no guard at all. The `target`-xor-`href` rule is the
  **CLI's** `menu-item-no-link` / `menu-item-ambiguous-link`, not the server's, for the boundary
  above — and it is a lint rather than a schema constraint because the model cannot express "exactly
  one of these" and a required field would make the other case impossible. *Neither* is the one that
  hides (a label with no link is indistinguishable from a deliberate heading); *both* is a silent
  precedence, since `target` wins and nothing said so.
  **`{{#query}}` gets a second page, an order and a filter — and the measurement came first (SH384).**
  `folder`/`type`/`limit` meant the helper could render only *the first page* of anything, so an
  insights hub with two hundred articles — the commonest page shape after a homepage — was not
  expressible. **The first deliverable was `ShRenderListingPageBenchmarkTest`, not the feature**, and it
  is the reason the feature looks the way it does. `ShRenderHelperSweepBenchmarkTest` counts *prepared
  statements* and by that measure a listing is flat; counting **entity loads** shows something else
  entirely: `findByShFolderAndStateIn` reads the **whole folder** and `limit` is an in-memory window
  over it, so **213 rows load to render 10** and page 19 costs what page 1 costs. That decided three
  things at once — a window, a comparator and a predicate are list operations over rows already in
  hand, so they cost **no read**; the sweep's query ceilings do not move; and an **exact total is
  free**, which is why the pager reports a real `@total`/`@pages` instead of a "has more" flag. The
  honest design and the cheap one were the same one, which is not something you can know without
  measuring. The ceiling in `token-budgets.properties` is on the **ratio**, not the count, so enlarging
  a fixture cannot absorb the finding — and the finding it preserves is the *pre-existing* one: rows
  loaded grow with the folder, so 5000 posts pay 5000 rows for page one. Pushing the window into the
  query is a follow-up and is **not** free, because the DRAFT-preference fold and the exact total both
  need rows a windowed query would exclude.
  Four decisions in the API: **`page=` and no `offset=`**, though the design offered either — two
  spellings of one window would need a precedence for a caller passing both, and a *silent* precedence
  is what SH382's `menu-item-ambiguous-link` exists to report rather than resolve (`limit=3 page=2` is
  every offset anybody needs). **It does not read `param.page` itself**: SH259 put the query string in
  template scope and kept it out of helper internals, so a paged archive is `{{#query page=param.page}}`
  — the template declares where the number comes from, and the same helper then serves a
  request-driven archive, a hardcoded second page and a number computed from a field, with no branch
  and no request in scope. **The pager's facts are `@`-prefixed data variables**, not row keys: a row
  is one post and these are properties of the *listing*, and SH251's scanner skips `@` by construction
  so no field lint had to learn about them. **`field=`/`value=` rather than a `tag=` convention**, and
  the value matches **an entry** — a `references: post` Multi Select and a tag list are both a JSON
  array, so `value="finance"` has to accept `["finance","energy"]` or the filter is useless on the one
  field shape a hub filters by; `referencesOf` makes a string and a list one code path, and the match
  is case-insensitive for SH79's reason.
  Three things tests caught, each a shape worth recognising. **`@first`/`@last` were missing and the
  pager repeated per row** — the block body runs per item and Handlebars gives a block helper no way to
  write into the enclosing scope, so `{{#if @last}}` is the only way to draw a pager once; using
  `{{#each}}`'s own names means nothing new is learned. **The benchmark's `isEqualTo` was flaky**: it
  failed once at 214 against 213 and passed on the next run of the same code, because each shape is its
  own post with its own layout and how much of the frame is already resolved depends on test order. A
  benchmark that fails one run in two is the assertion people re-run (SH250), so the claim is now the
  one the fixture supports — no second folder read. And **SH291's lint follows exactly one call from a
  registered helper**, so reading `sort=` inside a method called *by* a method called by the helper is
  not wrong code but code no signature can be checked against; every hash key is read in `query`'s own
  body and passed down. Registering hash params also cost **two more restatements** on top of the four
  SH382 predicted: `ShTemplateHelperDiscoveryTest` named four signatures as literals and
  `ShHelperSignatureLintTest` named `{{#query}}`'s parameter list twice. Both derive now — the pattern
  is that a test asserting *the plumbing is derived* must not itself restate the payload.
  **`{{#translations}}` and `post.locale` — SH348 stored a locale and nothing could read one (SH383).**
  The tree got `locale:` and `translationOf:` and the render model got nothing, so a six-language site
  could be *stored* and could not render `<html lang>`, an `hreflang` pair, or the switcher that is the
  one control every page of it carries. Three parts, and the interesting decisions are all in what was
  *not* done:
  **`post.locale` is never blank.** The column is nullable, so `post.getLocale()` would emit
  `<html lang="">` on any row predating SH348 — and an empty `lang` is not "unspecified" to a browser or
  a screen reader, it *asserts the language is unknown*, which is worse than a defaulted-but-valid tag.
  The defaulting is `ShPostUnifiedWriteService.localeOf`, and it moved there rather than being written
  inline: the *constant* had one owner and the fallback did not, so the read projection, the model and
  the new verify rule were each about to grow their own copy — three chances to disagree with what the
  context pack published, which is the drift `DEFAULT_LOCALE`'s own comment describes.
  **`render.defaultLocale`, not `site.defaultLocale`** — which is what the design asked for and does not
  exist: there is no per-site default locale in this model, so that name would promise a setting nobody
  can set and a template built on it would break the day one arrives. It goes on `render` beside `state`,
  the frame that already holds render-time facts, and can be joined later rather than moved.
  **`locale` is on *every* row**, not only a translation's: it is a property of every post, already
  loaded, and a multilingual listing wants it too. A second row shape for one field is the trade
  `FORM_FIELD_KEYS` is documented as *worth* and this one plainly is not.
  **The helper takes no arguments**, the only one here with an empty signature — a translation set is a
  property of *this* page, not something a template selects — and **excludes the current page**, which a
  switcher would otherwise undo with `{{#unless}}` on every template (marking the current language is a
  comparison against `post.locale`, available without spending a row).
  **Ordered by locale tag, not newest-first**, because a language picker whose items move when somebody
  edits one translation is a control people misclick.
  **The switcher's label is the sibling's own `title`**, which is already in the target language by
  construction. A JDK CLDR display name would read better and would move SH101's digest on every
  multilingual page the day the JDK is upgraded — the same reason SH377 keeps no timestamp in its
  inventory.
  **The group is read off the row, never put on the model**: `translationGroup` is a UUID, and SH79's
  rule is that an address a person writes is never one — the model is what a person writes against.
  `rowsOf` is the SH353 per-transaction read already in hand, so finding self in it costs nothing.
  **And no locale-prefixed route**, deliberately: SH323 makes a friendly URL site-unique, so `/en/about`
  and `/pt/sobre` are ordinary distinct URLs in one site and the preview needs no new addressing. A
  locale segment would be a second scheme beside SH79 for no capability. The bound that buys is real and
  named: a locale-per-*site* layout gets nothing from `{{#translations}}`, because it is scoped to the
  site like every other reference here (`getRelation`'s reason — a relation reaching another site would
  make a shared region's output depend on which page slotted it).
  The `verify` half is **`translation-missing`, a warning**, and most of its test file is the cases where
  it stays *silent* — because the naive rule is the useless one: "warn for every post missing any of the
  site's locales" emits 499 findings on a site with 500 English pages and one Portuguese page. A group of
  **two or more** locales is the evidence somebody meant this content translated; a page nobody
  translated is an untranslated page, not a missing translation. The site's languages are the **union of
  what its posts carry**, so a monolingual site is silent by construction rather than by a flag, and
  there is no locale list for anybody to maintain. It does not run at all on a **scoped** run: the
  finding is an *absence*, so unlike `translation-target-missing` there is no honest downgrade — scoped
  to one address, every group looks like it is missing whatever the scope excluded. One defect a test
  caught: the finding was landing on an arbitrary sibling (the head of a locale-keyed map filled in id
  order — deterministic and meaningless), reading as "the Portuguese page is missing German". It hangs on
  the group's **anchor** now, which SH53's column already identifies for free: a master post carries its
  own id as the group.
  **`translation-stale` (SH1058) is the other half: a translation whose source has changed since.**
  `sh_post_unified.source_digest` is stamped by `ShPostUnifiedWriteService.create` whenever the group is
  not the post's own — so both doors that make a translation (the console's `translate` and the agent's
  `translationOf`) get it — and holds `ShDocumentDigest` of the source's document, draft-preferred.
  The digest sorts object keys before hashing, because a re-save that reorders keys is not an edit and
  would otherwise make every translation of a re-saved page stale; array order is kept, because blocks
  in another order are another page. **A snapshot, never a mirror**: a source edit never touches it,
  which is what makes staleness a string comparison. The translation's *own* content edit re-stamps it
  — that save is the translator saying the text matches the source in front of them — and publish,
  a draft rebuilt from the live row and a revision restore all carry it across. Null means "not known to be
  stale" (a translation written before the column), so nothing is backfilled. Unlike
  `translation-missing` it runs on a scoped run, because its evidence is present: the translation, and
  its source read by id when the scope left it out. `ShTranslationStalenessTest` drives it through the
  real write service. **`ShTranslationStaleness` owns the rule** — which post is the source and which
  version of it — because four surfaces ask it (the write that stamps, `verify`, `/agent/read` and the
  console) and a disagreement shows up as a badge contradicting a report. The three reads carry it
  only when true: `shio_read`'s `translationStale`, the file projection's `translationStale:` key, and
  the console's amber marker in the translations menu — so a monolingual instance's reads, files and
  screens are exactly what they were.
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
  **What registering the eighth one actually costs, measured by doing it (SH382):** four test edits,
  and the fact that it is four rather than one is the guards working — `ShHelperSignatureLintTest`'s
  anchor list, `ShTemplateValidatorTest`'s `registeredNames()` assertion, and
  `ShTemplateFieldLintTest`'s fixture, which **refuses** a registered helper no template in it calls,
  since every negative assertion in that class would otherwise be silent about the new helper's rows.
  The fourth was a defect: a second assertion in `ShTemplateValidatorTest` held the joined names as a
  string literal — a sixth restatement of the capability set, in the one test whose subject is that
  none are left, two assertions below the one that reads the engine. It derives them now. Expect all
  four when you add the ninth, and treat a green run after touching only one as the tell.
  **Confirmed on the ninth (SH383's `translations`): exactly three, and the fourth held** — the derived
  assertion did not need touching, which is what "the instrument, not the patch" buys the next task.
  **And the count is about the helper's *name*, not its parameters — SH384 found two more.** Adding four
  hash keys to an existing helper touched no registration list and still failed twice, in
  `ShTemplateHelperDiscoveryTest` (four signatures as literals) and `ShHelperSignatureLintTest`
  (`{{#query}}`'s parameter list, asserted twice in one method). So the tally is: **three tests per new
  helper name, two per changed signature**, and every one of the five is a restatement that could
  derive. Three of them now do.
  **The signature is checked against the body now, both ways (SH291)** — `ShHelperSignatureLintTest`
  fails if a helper reads a hash key its signature omits (an incomplete call in the served context
  pack) *or* declares one nothing reads (a parameter that is silently ignored, which is not an error
  at write time). It reads the source as text, so it also fails on the two things that would make it
  meaningless: a key that is not a string literal, and a key read more than one call below the helper
  (`limit(options)` is followed; a longer chain is not). An accessor whose key is its own parameter is
  exempt by construction. So adding a helper with a wrong signature is a build failure, not a stale
  string.
- **The capability set is discoverable, in the context pack (SH272)** — `registeredSignatures()`
  renders as a `conventions` line naming every call form (`{{#query folder= type= limit=}}`,
  `{{#getRelation FIELD}}`, `{{file_url FIELD}}`, …) plus `sh-region`. Before it, an author learned
  the set by writing a template that called something else and reading the 400 — a teaching error
  doing the job of documentation, one name per attempt, which is P2 backwards. Three rules: the pack
  and **not** the manifest (`startHere` is orientation, and an MCP resource would answer only MCP
  clients — while a helper set is exactly the *instance-specific* fact this section exists for, next
  to SH114's notes); it is **conditional on the instance's own model** holding a render post-type, so
  a session that will never write a template pays nothing (P3) and the check costs no query; and a
  **And the model is stated too, since SH292** — a second conditional line says that a bare
  `{{FIELD}}` is the page's field at the top level and the listed post's inside a block, names the row
  keys (`ShRenderHelpers.ROW_KEYS`) and the reserved frames (`ShRenderModel.FRAMES`, now the single
  source — `ShTemplateFieldLint` had a private copy). Both derived, never restated, and both under
  `response.context.conventions.render` (195) — the first ceiling the conventions section has had,
  because the response budget test measures the pack *without* it. Measured: helpers 120 tokens,
  model 59.
  A helper registered with no signature is **absent** rather than listed bare, since a half-answer
  about a capability is what sends somebody guessing.
  **All seven exist now, `form` last (SH247)** — `ShTemplateValidator.KNOWN_MISSING_HELPERS` is
  **empty**, and the empty set stays for the *next* gap rather than being deleted. The validator
  test is Spring-wired for the property that outlived the gap: the validator and the renderer must
  never disagree about which helpers exist, in **either** direction, and its `{{#form}}` assertions
  had to invert rather than be removed — a write-time refusal for a helper the render then runs is
  the same defect pointing the other way.
  **What unblocked it was reading the artefact, not a decision meeting.** The task was filed for a
  block on "a form needs storage, spam, notification, retention — a subsystem, not a helper". FSP's
  `ShFormComponent` is two files and stores nothing, and `ShFormConfiguration` carries exactly
  `method`, `pageLayout`, `createPost` and `folder` — so **2018 had already answered it**, and its
  answer was that a submission becomes a post in a named folder. No subsystem, because the CMS is
  one. That is SH274's lesson from a new direction: an inherited premise about what a capability
  *requires* is as checkable as one about a dependency, and this one was recoverable in `origin/FSP`.
  **`{{#form type=}}` renders and never receives.** It yields one row per declared field
  (`name`/`label`/`widget`/`input`/`required`/`ordinal`, `ShRenderHelpers.FORM_FIELD_KEYS`) in
  ordinal order with a name tiebreak; the `<form>`, the `action` and every input are the author's
  markup, for SH183's reason — a helper emitting `<input class="form-control">` would be the one
  place in this engine where markup is a Java decision. The receiving half is deliberately absent:
  it is a **public, unauthenticated endpoint that writes content**, which is a security decision
  rather than a rendering one, and the renderer's scope is preview-grade (production delivery is
  Next.js over the CDA, P10). An unknown post-type renders `{{else}}`, not a refusal — `getRelation`'s
  rule, because SH99 already reports it with an address and a `fix`.
  **The row shape is a second one, on purpose.** `FORM_FIELD_KEYS` is not merged into `ROW_KEYS`:
  the five listing helpers are interchangeable because they yield the same row, and a field is
  categorically not a post — a card body written for `{{#query}}` is meaningless over it. Two
  honest shapes beat one that makes the shared shape mean less.
  **P2 cost nothing here:** the context pack names `{{#form type=}}` because it is derived from the
  engine (SH260/SH272), so registering the helper published it and no list needed editing.
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

### The banner reads what was rendered, not two model keys (SH774)

SH559 reads the page's composition — `verbatim` / `authorable` / `mixed` — *"from the page's own
fields, which is the only place the answer is"*. Since SH763 that sentence is false. A page whose
`<body>` begins with an accepted block has no `content` and every byte of the source's markup in
`SectionMarkup` posts, so `compositionOf` reported **authorable** of a page that is mixed. SH559's
own rule convicts it: *a label that is wrong is worse than no label*, because it is evidence and it
points somewhere.

**The measurement that chose the fix.** Resolving `sections` and asking what they are is the obvious
repair, and it was priced before it was written: `ShPostUnifiedRepository` has **no bulk furl
lookup** — `findByShSite_IdAndFurlAndState` is per URL, and `ShFurlSpellings` tries up to four
spellings of each — so the pass costs **one query per section, per render**, against a standalone
render measured at **21 queries in total** (`render.digest.first-page-queries`). Thirty to a hundred
and fifty per cent, for a diagnostic banner.

That was an *inference*, and the number already existed (SH776): `ShRenderHelperSweepBenchmarkTest`
has measured a section-composed page since SH312 — five sections is **11 queries a page against a
floor of 6**, by id and by URL alike, which is one lookup per entry exactly as reasoned. The
conclusion was right and its provenance was not, which is a distinction worth keeping: a measured
number and a plausible one are different kinds of claim. The other candidate was a flag on `Page`: the
replication-only field SH763 removed, re-added to a shipped type so that a label can be right.

So the banner reads the **composed page**, which costs nothing and is not a proxy for the subject —
it *is* the subject. The banner describes this render, and the composed page is where the page's own
fields and its sections have already been brought together. The markup branch of the generated
layout is the only thing in the vocabulary that draws an `html` field, and the composer turns its
`sh-field` into `data-shio-prop`, so the annotation is the mark.

The honest limit, stated: a hand-written template that annotates its own `html` field reads the same
way — and that is a page which genuinely carries markup somebody wrote by hand.

**One more thing changed, and it is the smaller half.** `composer.compose(...)` is now called once
and read twice rather than being re-composed for the question: producing it twice would be a second
answer that can differ from the one served.

### A relation's rows know where they are (SH777)

`{{#getRelation sections}}` called `options.fn(row)` with a plain context. Handlebars' `@index`,
`@first` and `@last` come from a **data frame**, and this helper created none — so inside the block
that walks a page's sections a template could not ask which row it was on.

Found by needing it: SH772 had to emit the captured stylesheet **once** for a page whose kept markup
is all in sections, and the natural spelling — fire on the first row carrying markup — did not
exist. That task shipped a per-row emission and stated the bound instead.

```handlebars
{{#getRelation sections}}
  <section {{#if @first}}class="lead"{{/if}} aria-posinset="{{@index}}">…</section>
  {{#unless @last}}<hr>{{/unless}}
{{/getRelation}}
```

**The same three names SH384 already publishes**, not a second spelling. `emit` has done exactly
this for a listing since then, for the same reason and with the same comment: a pager drawn once is
only expressible as `{{#if @last}}…{{/if}}`, and without the frame it repeated per item. SH777 makes
that idiom shared rather than copied.

What it makes expressible is more than the task that found it: a separator between rows and not
after the last, a first section that renders larger, `aria-setsize` on a list a screen reader is
counting, a numbered list that survives a reorder. Each of those is a field on every section today,
carrying its own position — a value that goes stale the moment a curator moves one.

**The assertion renders rather than reading the helper**, because what is claimed is that a template
can *ask*: `options.data(…)` being called proves nothing about whether `{{@index}}` resolves, since
the frame has to survive into the block body and only the engine decides that. Reverting the helper
to its plain context makes the test fail.

Still without a frame: `{{#navigation}}`, `{{#search}}`, `{{#translations}}` and `{{#menu}}` — four
helpers with the same shape and the same silence, left alone here because none of them has yet had a
template need it, and a capability added on speculation is one nobody measured.

### The section vocabulary — `@viglet/shio-sections` (SH95)

Nine post-types (`Page` + `SectionHero`/`SectionFeatureGrid`/`SectionCta`/`SectionLogoWall`/
`SectionTestimonial`/`SectionFaq`/`SectionRichText`/`SectionMarkup`) authored with
`@viglet/shio-model`, and
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
- **`section-not-accepted`** (SH1106, a warning) reads the other declaration: a resolved section
  whose type is not in the union of `sh-accepts` across the page's layout and its regions —
  `ShPageRenderService.acceptedSections`, a static read through `site.postTypeLayout` via
  `ShRegionComposer.acceptsOf`, no render, cached per site and type for the run. Once per type per
  field, naming the accepted list. **A warning and not a refusal on write**, because the list lives in
  a layout and layouts are edited: refusing would make an existing page unwritable the moment
  somebody narrowed its template. An empty union is "nobody declared", which reports nothing.
- `/agent/render`'s digest **composes** the page: each referenced section contributes its title as
  an `<h2>` and its markup/text fields to the outline, in the order the page names them, so two
  pages built from different sections no longer share `e3b0c442…` (the hash of nothing). A URL
  that resolves to nothing becomes `<h2>[missing section /url]</h2>` rather than contributing
  nothing — two different mistakes must not hash the same.
- **A `Multi Select` without the key stays tags.** Reading every value as a URL would report a
  defect for every tag on every post, which is why the field has to say what it holds. `references`
  and not `postType` because sections are heterogeneous, and not a naming convention because
  nothing can check prose.


## A listing loads the page, not the folder (SH419)

SH384 built `{{#query}}`'s paging, sorting and filtering as in-memory operations and said why: the
folder read already materialized every row, so a window cost no extra query. Its benchmark also
recorded the price of that premise — **213 entity rows loaded to render 10, identical for page 1 and
page 19**. The cost grew with the folder rather than with the page, so a 5000-post archive paid 5000
rows, each hydrated with its JSON `data`, to draw the first ten.

**The window is now decided before the rows are loaded.** `findListingKeys` returns tuples — id,
state, date, title, post-type id — the fold, the `type=` filter and the comparator run over those,
and the ≤`limit` survivors are read whole in one `IN` per state. Measured **2 rows loaded per row
rendered**, and `render.listing.max-rows-loaded-per-row-rendered` went from 25 to 4.

**Not one predicate, comparator or fold moved**, and that is the design rather than caution. Pushing
the ORDER BY into the query is *not portable here*: `date` is nullable and the comparator is
`nullsLast`, so the clause needs explicit null placement — MariaDB has no `NULLS LAST`, and it and
PostgreSQL disagree about where a `DESC` null falls by default. A listing whose page boundaries
differ by engine moves SH101's digest for a change nobody made, on one deployment and not another.
That is the fourth objection SH384 did not know about, and it is why SH634 carries the remaining
scan rather than this task closing it.

**The cost is a second fold and a second comparator family**, over keys instead of entities — two
writers for one rule, which is the shape SH245 warns about. `ShRenderListingOrderTest` is the price:
one population, folded and sorted both ways, must yield the **same sequence of ids**, with a null
date, a null title and two posts sharing a date in the fixture because that is where two
hand-written comparators actually drift. A disagreement would be the listing drawing different posts
depending on which branch ran, visible only to somebody comparing two pages that should be one.

`field=` is the one path still reading whole rows: it is a predicate over the post's JSON, so the
JSON is what it needs. Declared here and in the benchmark rather than quietly excepted.

The benchmark's own claim **inverted** rather than being replaced — it asserted `first > PER_PAGE`
(the folder reaches the reader) and now asserts `first < ARCHIVE` (it does not). A benchmark that
stops asserting the thing it was written about is one that passes for a reason nobody checked.

## A listing inside a listing keeps its own place (SH420)

SH384 published `@page`, `@pages`, `@total`, `@first`, `@last` and the rest as Handlebars **data
variables** so a pager could be drawn *once*: a block helper's body repeats per row and there is no
way to write into the enclosing scope, so `{{#if @last}}` is the only place a template can emit one.

All ten land in **one data scope shared with every enclosing block**, so the documented idiom broke
on the page shape that most needs it — an archive whose cards list their own children, a hub whose
sections carry a listing. The inner block's final iteration leaves `@last = true` behind, the outer
body reads it next, and the pager is drawn on **every outer row**: measured 2 of 2 where 1 was
expected. `@index` and `@first` had it too, and there is a second symptom the original report did
not name — the values also outlive the block, so `{{#if @last}}` written *after* `{{/query}}`
rendered as well.

**`query` now saves the ten before its block and puts them back after.** Of the three candidates the
task offered this is the only one that costs a template nothing: namespacing them (`@query.last`) is
a rename and therefore a break, and moving them onto the row gives up drawing a pager once — which
is the entire reason they are data variables and not row keys.

**Absent is restored as absent, not as `false`.** The saved map is a `LinkedHashMap` and not
`Map.of` precisely because `null` — *nothing was there* — is the value that has to survive the round
trip. Restoring `false` would light up an `{{#if @last}}` a template put outside any listing on
purpose, trading one wrong render for a quieter one.

The rendering is its own method (`emit`) so the save/restore bracket is structural rather than a
`try`/`finally` around a long body that invites a return path outside it. Both halves are asserted
in `ShRenderListingPageTest` against a layout that nests one `{{#query}}` in another, and both were
**verified to fail with the restore disabled** — a nested-listing test that has only run against the
fix is green for two indistinguishable reasons.

## A layout can say it omits the site's scripts on purpose (SH421)

SH389's `site-scripts-dropped` warns for **every** bound layout in a site that declares scripts and
whose HTML never names `site.scripts`. That is right for the case it was built for — a set of pages
with no consent banner, rendering perfectly, which is a compliance defect nothing else can see. It
is wrong for a transactional email template, a print sheet, an AMP-shaped variant or an embed a
partner iframes: each is a legitimate layout that must **not** load a chat widget, and its author had
no way to say so. SH388's lesson makes that fatal rather than annoying — *a lint over templates that
fires on correct work is one authors learn to skip, and the true finding goes unread with it.*

**`PageLayout.NO_SITE_SCRIPTS`**, a `Check Box`, is the declared opt-out. A **field** and not a
baseline acceptance, for the reason SH385 put a redirect's source on the post rather than in a side
table: the decision belongs where the person changing the layout reads it. A suppression file records
that somebody once dismissed a warning; this records that the omission is *intentional*, in the
content, beside the HTML that omits them. The finding's `fix` now names **both** ways out (P6) —
emit them, or tick the box.

**Unticked by default**, which is the load-bearing default: an opt-out that defaulted the other way
would silence the rule for a whole estate on upgrade, and SH389's finding is a compliance defect that
renders perfectly.

**One reader for what a ticked box means.** `ShPostFieldFlags.isTrue` is now shared, because the
value is not a `Boolean` on the wire — `"1"` from the console, `"on"` from a form, `true` from
`post.upsert` — and this gave that coercion a second caller in another package. SH245's rule, and the
disagreement it prevents is concrete: a layout whose opt-out the lint failed to recognise gets warned
about anyway.

**Two guards caught this being added, and both were right.** `ShRenderModelSourceTest` refuses a
field the 2018 model cannot carry unless it is declared in `INVENTED` **with its reason** — so the
divergence is a decision somebody wrote down rather than a difference nobody noticed (SH277's shape).
And `specsFor` is the single source for both `ensure` and the reconcile repair list, so the field
reaches old instances as well as new ones without a second edit.

`ShSiteScriptsDroppedTest` holds both directions against two byte-identical layouts differing only in
the flag — and both assertions were **verified to fail with the opt-out disabled**, because a test
that only asserts the silence would pass just as well if the rule had been deleted.

## A relation literal is checked the way a THEME field is (SH572)

SH558 taught `ShTemplateGraph` to read the literal address in a `{{#getRelation "…"}}` block, which
closed `unused-theme` — and made the opposite gap legible, because the graph now resolves these
references and can see one that resolves to nothing.

The defect was an **asymmetry**. SH297 reports a layout whose `THEME` field names a theme the site
has not got, as an *error*: the layout makes a claim and the claim is false. A relation literal has
the same consequence and had no rule at all, so `/theme/capturd` — one character — produced a page
that renders 200, carries no captured CSS, and reported clean. `getRelation` renders its else branch
when nothing resolves and deliberately does not mark the absence (SH247, and that is right — a
dangling *field* reference is SH154's finding, with its own address and fix), which is exactly why
the page cannot be the thing that tells you.

`dangling-relation` is that rule, at SH297's severity because it is the same claim in a different
spelling. Three decisions in it:

- **Resolved against any post, not just a `Theme`.** `relatedThemeTitles` matches Themes because its
  question is "is this theme reachable"; this one asks whether the address denotes *anything*. A
  layout may legitimately relate a Region or an ordinary post, and reporting those would be the
  false-positive shape SH421 had just finished removing from a neighbouring rule.
- **A path expression is not checked.** `{{#getRelation sections}}` names a *field*: what it points
  at is data, decided per page, and no static reading can say what it resolves to. Asserted, because
  a rule that guessed here would fire on every conversion the product writes.
- **Keyed by post id, and one row per id before reporting.** A literal can sit in a layout, a region
  or a theme, which share a namespace — so an id is the only key that cannot name the wrong post.
  And a post has a DRAFT row and a PUBLISHED row sharing that id, so walking `scan.posts()` directly
  reported a published template's defect **twice**: one defect, two findings, and order-dependent,
  because whether a published row exists depends on what ran first.

The template's HTML is read through `ShPageRenderService.htmlOf`, added for this, for the reason
`themeTitleOf` already exists (SH281): the graph and the page must not come to disagree about what a
template *says*, and the whole value of a static rule over template source is that it is the source
the renderer will use.

## The window is in the query now, and the measurement had to be rebuilt first (SH634)

SH419 took a listing from **213 entity rows loaded to render 10** down to **2** by folding and
sorting over sort keys and reading only the survivors whole. It did not remove the **scan**.

**The instrument that proved SH419 was structurally blind to what SH419 left.**
`getEntityLoadCount()` counts entities and a projection is not one, so the whole-folder key read
showed up as *nothing*. The first deliverable was therefore a second measurement — rows every query
in the session **returned** — and it put a number on the complaint: **228 rows to render 10, ratio
22, identical for page 1 and page 19**, while the entity ratio read 1. After the fix: **31, ratio
3.** Ceiling `render.listing.max-rows-scanned-per-row-rendered` = 6, for the reason the entity
ceiling is 4 and not 2 — the number carries the page frame as well as the listing, and what it must
catch is a return to scanning the folder, which at this fixture is 22 and not 5.

**The fold is one predicate that is correct for both renders**, which is why there is one query
rather than two that can disagree: `p.state = :preferred or not exists (a :preferred row for this id
in this folder)`. For a draft render (`states` = both, `preferred` = `DRAFT`) that keeps the draft
when there is one and the published row otherwise — exactly `preferDraftKeys`. For a published
render (`states` = `PUBLISHED` only, `preferred` = `PUBLISHED`) the left side is true for every row,
so it excludes nothing and costs nothing.

**`coalesce(date, floor)` is what made the ORDER BY portable**, which was SH419's fourth objection
and the one that had no answer. `date` is nullable and the comparator is `nullsLast`, so the clause
needs explicit null placement — and `NULLS LAST` is not portable: MariaDB has no such syntax, and it
and PostgreSQL disagree about where a `DESC` null falls by default. Mapping null to an instant below
every real one makes `desc` put nulls last and `asc` put them first on every engine. **`verify
-DskipITs=false` with Docker up is what turns that into a proof** — `ShPostgresSchemaIT` and
`ShMariaDbSchemaIT` ran 3/3 each rather than skipping.

**`sort="title"` deliberately keeps the scan.** Java's `CASE_INSENSITIVE_ORDER` over a possibly-null
value is not any database collation, and pushing it down would reorder somebody's archive on one
engine and not another — the exact defect the `coalesce` argument exists to avoid. `field=` keeps it
too, being a predicate over the post's JSON. Both are declared in the code and visible in the
benchmark rather than left to be discovered.

**One thing that is not SH634's**: `ShBlueprintApplyTest` failed again in a full suite during this
work — `errors()` 2 where 0 was expected — and was green on the next run with the same code, the
second occurrence of SH635. The number survived SH572's double-reporting fix, so that hypothesis is
disproved: the 2 is two real findings, not one counted twice. The assertion now **prints the
findings when it fails**, so the third occurrence names the rule and the address instead of a
number. That is SH354's lesson applied to a red nobody was present for.

## A form's endpoint and honeypot come from the site (SH636)

`{{#form type="Contact"}}` renders a post-type's declared fields, and SH632 was right that it must
not know an address — the destination is the site's, never the page's. But it published **no
endpoint** and **no honeypot name**, so an author hand-wrote the action URL and the literal
`shio_hp`, joined to the controller that checks it by nothing but memory. A honeypot nobody renders
is a spam control that is present in the code and absent from every page; a wrong action is a form
that silently posts nowhere.

**`site.formAction` and `site.formHoneypot`**, on the site frame. A template writes
`<form action="{{site.formAction}}">` and `<input type="hidden" name="{{site.formHoneypot}}">`, and
neither literal appears in content.

**The first implementation was `@action`/`@honeypot` as data variables from the helper, and a test
disproved it within the hour.** `action` has to sit on the `<form>` element, which necessarily
**wraps** the block — and a block's data variables do not exist outside it. So the fixture rendered
`action=""`, which is exactly what the design promised and exactly what nobody can use. The shape
was wrong, not the scope, and the right one was already implied by SH632: the endpoint is a property
of the **site**. Worth recording because the wrong version was defensible, carried a save/restore
honouring SH420, and would have shipped if the test had asserted the variable instead of the
rendered `<form>` tag.

`formAction` is the endpoint and nothing else — no folder, no post-type, no choice a template makes.
It is the same URL for every site but for its name, so a page still cannot point a submission
anywhere the operator did not configure, which is the property that kept the folder out of template
content in the first place. `formHoneypot` reads `ShFormSubmissionAPI.HONEYPOT_FIELD` rather than
spelling it again: a honeypot whose rendered name and checked name drift passes every submission and
reports nothing anywhere.

## The model's own reachability, as an asserted list (SH524)

`template-unknown-field` catches a template reading a field the type does not declare. **Nothing
caught the inverse** — a field no template renders — and that inverse produced two defects, found by
hand, weeks apart: four of seven section types drawn as a bare heading (SH465), and a `ctaHref`
rendered into an `href` and annotated by nothing, so a curator could rename a button and never
repoint it (SH503). Both are modelling failures that surfaced as rendering bugs, and both were found
by counting annotations against fields **by hand**.

`shio-sections/test/field-reachability.test.mjs` is `ShAgentP1ConformanceTest`'s idea pointed at the
content model instead of the API surface: for every declared field, does a component draw it, and
does the drawing annotate it — with the gaps held as a **list somebody signed** rather than a
refusal, because a field nobody renders is sometimes correct.

**All 25 section fields render.** SH465's class is now closed by assertion rather than by memory,
and a section added without a component drawing its fields fails the build naming the field.

**The types this package does not draw are a declared list, not a rule.** `Menu`, `MenuItem` and
`Page` are exempt because a `Page` is composed by the host application and a `Menu` is drawn by the
Java renderer's `{{#menu}}`. Spelling that as "starts with `Section`" would have been shorter and
would have silently absorbed a real section somebody forgot to draw — which is the defect the file
exists for. A second assertion checks each exempted name still exists, so an exemption cannot outlive
its type.

**The annotation half came back 25 of 25 missing**, which is SH503 at package scale and is now
**SH638**: every section field is rendered, stored, and inert on the page. It is asserted as the
whole list rather than fixed in passing, because emitting the attribute is a change to every
component *and* to what a headless host is expected to do with it — and because closing it one
component at a time is then a diff that visibly shrinks a list.

## A section is editable on the page (SH638)

SH524's instrument counted it: **25 of 25** section fields carried no editing annotation, so every
page composed from `@viglet/shio-sections` was content the Universal Editor could not reach. That
matters because the package is what a conversion writes — SH464's authorable mode turns captured
markup into fields a curator edits, and if the component annotates none of them the mode delivers a
model nobody can use *from the page*, putting the curator back in the console form the visual editor
exists to remove.

**The host contract was the part that needed deciding, and it is one optional prop.** The Java
renderer emits `data-shio-post` / `data-shio-prop` itself because it knows which post it is
rendering. A headless host does not: it fetched the section over the CDA and React drew it, so
**only the host knows the id**. `SectionProps.postId` is that contract, stated in a prop rather than
in prose a host has to find — and it is optional, so a public page carries no editing metadata and a
host that omits it renders byte-identical markup to before.

**A link needs two elements.** One `data-shio-prop` per element is the contract, and a CTA carries
both a target and a label, so the label is now wrapped in a `<span>`. That markup is added on
purpose: SH503's defect was exactly a curator able to rename a button and unable to repoint it.

Two limits are **declared rather than papered over**:

- **A relator's rows are not annotated.** A row is its own post and the editor needs that post's id
  to write to it; `rows()` receives plain objects off the wire with no id. Annotating them anyway
  would be worse than leaving them — the editor would attach and have nowhere to save, which is
  SH475's shape. The collection carries the annotation; the rows do not.
- **`SectionHero.align` is rendered as a class token**, not a value, so no attribute carries it
  verbatim and there is nothing to attach to. `columns` and `variant` are the contrast that makes
  this a distinction rather than an excuse: both are layout switches too, both land verbatim in a
  `data-` attribute, and both *are* annotated.

**SH524's instrument was checking `data-shio-field`, an attribute this product does not have.** The
real vocabulary is `data-shio-prop` and `data-shio-prop-attr` — what `ShRegionComposer` emits and
what `shio annotations` counts. The list it produced was right *by accident*, because nothing was
annotated at all, so the instrument passed its own review while asserting the wrong name. That is
precisely the failure an asserted list is supposed to prevent, and it survived a full review two
iterations ago.

## The selector the reader could not spell (SH686)

`ShHtmlAppearance` parses a page's own stylesheets for class selectors and reports the classes the
markup uses that it did not find; `verify` turns that into `page-unstyled`. On the `www.viglet.org`
replica it said **205 used, 1002 defined, 42 unstyled** — and every one of the 42 is defined in the
stored `theme/captured` CSS (1,061,479 chars).

CSS requires `/` and `:` to be escaped inside an identifier, so Tailwind emits `.bg-background\/80`
and `.dark\:text-slate-400`. The collector's identifier stopped at the first character outside
`[_a-zA-Z0-9-]`, and a **backslash is outside it**, so it collected `bg-background` and `dark`. The
markup carries the unescaped names. The two halves were comparing different languages and could
never agree. **All 42 carry a `/` or a `:`** — the trait is the whole population, not a sample.

So `page-unstyled` was a false positive on any site built with a modern utility framework, which is
most of what a replication will ever capture — and worse than noise, because it accused the
*verbatim* half of the page, which renders correctly. That is what sent a whole investigation into
hand-parsing 1.1 MB of HTML.

### The obvious fix adds a crash, and only a measurement says so

Letting the pattern's identifier accept escapes — `(?:[_a-zA-Z0-9-]|\…)*` — resolves every one of
the 42 and passes every test written for the defect. It also introduces a **`StackOverflowError`
reachable from captured CSS**, which is content from somebody else's website. Measured over `.a`
followed by 40,000 backslashes:

| collector | 40k backslashes | 40k plain chars | 1 MB Tailwind CSS |
|---|---|---|---|
| old regex | OK, 1.5 ms | OK, 2.8 ms | OK, 31.7 ms |
| escape-aware regex | **STACK OVERFLOW** | **STACK OVERFLOW** | OK, 58.4 ms |
| bounded `{0,200}` regex | OK, 2.7 ms | OK, 3.3 ms | OK, 30.9 ms |
| **linear scan (shipped)** | OK, 3.9 ms | OK, 3.4 ms | OK, 23–32 ms |

`java.util.regex` compiles `[class]*` to a non-recursive loop and `(?:A|B)*` to a recursive one, so
the repair would have traded a false positive for a crash — and the old pattern never entered a loop
at all, which is why it was safe. The bounded form fixes it, and a bound truncates a long name back
into a class the markup cannot match: the same defect one indirection along. The scan needs no bound
and is the cheapest of the four on the real workload.

It also collects **18,052 distinct names against the old 8,529** on that 1 MB fixture — the escaped
selectors were collapsing onto their own prefixes, which is the defect stated as a number.

The whole of this — that the regex works, that it overflows, and by how much the scan wins — came out
of running the benchmark on the fix, not out of reading it. Ordinary application of *measure before
you fix*: here the thing measured was the repair rather than the defect.

## Rendering a page from values nobody saved (SH1014)

`renderProposed(siteKey, url, state, proposed, params)` composes the page through the same
`renderPage` every other path uses, over a **transient** copy of the post. That copy is SH990's
split again: everything that is a fact about *where* the page lives — site, folder, type, address,
locale — comes from the stored row, and only the document differs. So a preview cannot move a post,
change its type or rename its URL, which is the whole of what an override could otherwise smuggle.

It **merges**: a field the override does not name keeps its stored value, the same rule as the merge
verb (SH335), which is what lets a caller ask *how would this page look with this headline* without
restating the document. The title follows the document where the override names it, so a preview's
`<title>` and its `<h1>` cannot disagree about what the curator just typed.

**It persists nothing**, and that is asserted rather than asserted-about: `ShPageRenderTest` renders
over the fixture that actually composes, then asks the database whether the row, its title and the
revision count changed. "No writer is called" is a claim about code, and code gets edited by people
who have not read the paragraph explaining it.

A stored document that will not parse **records a CONTENT incident** rather than being swallowed.
The symptom otherwise is a preview that renders with only the overridden fields — a page that looks
emptier than expected, which is the silent shape SH270 refused for the two render paths it named.

The surface is `POST /api/v2/agent/render` (a POST because the document is the argument), gated by
**write** and not read: the caller supplies the content and gets the page back, so the question is
whether they could author it, not whether they may see what is stored. `format=html` adds the markup
for a console frame; an agent asking for an outline does not pay for it. `state=rev:<n>` is refused
— a revision is a record, and laying unsaved values over one would render a document that never
existed under a version number saying it did.

## `{{#image}}` stops fabricating an empty alt (SH1031)

The helper put `alt: ""` whenever the asset's `ALT` was null, and a template writing
`alt="{{alt}}"` then emitted `alt=""` — which is the HTML for *this image is decorative, skip it*.
So a site built the recommended way told every screen reader to skip every undescribed image, and
`verify`'s `image-no-alt`, which fires on an **absent** attribute (deliberately: absent and empty
mean different things), could never fire on one.

The row now carries **`altAttr`**, the whole attribute, for the reason `srcset` is whole — a
template cannot express an attribute's own absence. Three states, and the third had no spelling
before:

| asset | `{{{altAttr}}}` |
|---|---|
| `ALT` set | ` alt="A wide hero"` |
| `decorative="true"` on the helper | ` alt=""` |
| neither | *(nothing)* |

`alt` is still on the row and still the asset's own text, so no existing template breaks. A
template that wants the finding to be reachable emits `{{{altAttr}}}` instead of `alt="{{alt}}"`.
Decoration is an **opt-in** because it is a decision somebody made: inferring it from a blank field
is what produced the defect.

The value is escaped in the helper, because it is emitted unescaped — a quote in alt text would
otherwise close the attribute and everything after it would be markup.
