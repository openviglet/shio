# The agent surface — /api/v2/agent/** and what bounds it

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

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
  run. Only `post:` addresses are linted today (SH228).
  **`?digest=true` closes the *appearance* loop the same way (SH194)** — the two SH187 hashes for
  each page the run **affected**, which for a write to a shared layout or region is not the address
  written but every page that renders through it (SH193's graph; this is that graph's first consumer
  that uses it to *do* something rather than to report). Only the hashes, never the outline: the
  caller is asking whether something moved, which is a comparison against a value it already holds.
  **One exception since SH301: `markers`, present only when non-zero.** A marker count is not a
  comparison — it is an assertion about the page *now* ("this one is rendering a slot with no
  Region"), and the caller being told which pages a shared-region write reached is exactly who needs
  it. It costs no extra work (each row is already a real render, and it has carried the count since
  SH299) and nothing at all on a clean run: measured, a clean row stays byte-identical at ~25 tokens
  and a broken one is ~43, against a second digest call per page at 6 queries plus a full response.
**`?digest=<n>` since SH266** — true for the instance's default (ten), or a count the server
  **clamps** to `shio.agent.render.max-write-digests` and reports the cut as a row (SH138). One
  parameter, because everywhere else here a count *is* the parameter; a bad value (`yes`, `0`) is a
  teaching 400, since a caller that believes it asked for something must not be answered a different
  question. Ten is a measurement now, not a guess: SH264 puts a swept page at 6 queries, so the
  default is ~60 queries behind one write. The ceiling is in the manifest's `limits`, because one the
  caller cannot see is one it learns by being cut off. Skipped on a `dryRun` for the
  same reason `verify` is, and opt-in so nobody pays for a render they did not ask for.
  `ShAgentProofService.attach` is `@Transactional(readOnly = true)`: it still runs after the write
  commits, and it needs a session of its own because the graph walks LAZY associations. **`?format=terse` since SH157** —
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
  **Coverage is a note, and marker counting is settled as impossible here (SH302).** A clean pass
  emitted nothing at all, so forty proved pages and zero proved pages read identically — it now says
  how many it proved per site. Counting SH299's defect markers in the delivered body would be a
  stronger claim and **cannot work**: the pass fetches a site only when it has its own front-end URL,
  so every body it could parse is somebody else's render from the CDA and carries no `data-shio-*`.
  Asserted, not remembered — `ShAgentDeliveryTest` pins that Shio's own `/preview` is never a delivery
  target. (Switching to the body-bearing fetch would also have cost no extra network: `status()`
  already discards a body it has transferred. Cheapness was never the question.)
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
  transforms, scheduled publishes, 5xx. **A row carries `frames` — where in Shio it threw (SH190)**:
  `Class.method:line`, innermost first, **at most three** and **only `com.viglet.shio`** frames, no
  arguments and no locals, Spring proxy frames skipped. The old `fix` ended "the instance log has the
  stack trace (the body never will)", which named a file the caller cannot open — an error whose
  remedy is out of the caller's reach is an error that has not been reported. A **log-tailing tool
  was rejected** for SH103's three reasons: it hands over every request's data to answer a question
  about one, it is a per-deployment path, and it cannot be scoped by the SH2 allow-list or the SH54
  ACL. `frames` is **absent, not empty**, when there is nothing of ours in the trace (a request
  Tomcat rejected, or a 5xx the framework rendered with no exception reaching the filter). It is
  **persisted** (`v2026.3.15`), because SH144's promise is that the durable answer is the good one,
  and a repeat arriving without a frame **cannot erase** the first one's — the rule `fix` already
  followed. Since **SH269** every recorder inside a `catch` passes one — webhook dispatch errors,
  scheduled publish/unpublish, image transforms — and the webhook's *give-up* branch deliberately
  does **not**, because delivery completed and the subscriber refused it, so the presence of the
  field now distinguishes "we failed" from "they refused". Guarded by a **source lint** in
  `ShAgentIncidentFramesTest` (a `record(...)` inside a `catch` that passes no frame fails the
  build), because the defect is an omission at a call site and reproducing three async failures
  would be three fixtures asserting one line each. A `catch` that records *nothing* is still
  invisible — **SH270**. Recorded by `ShAgentIncidentLog`; repeats fold into one
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

**One rule for an unrecognised parameter value (SH320): a parameter that *selects or bounds work*
teaches; one that only *adds a section* does not.** So `?format=` and `?digest=` are teaching 400s
(they choose what the response is — a typo answers a different question), `?include=` is deliberately
lenient (a typo costs the caller an extra its own code will notice, which is also the answer to
SH287), and `?verify=` moved to the teaching side via `ShAgentFlag`, because it runs a whole lint
pass: `?verify=yes` used to be a caller asking for proof, getting silence, and reading a 200.
`?ids=` stays lenient — it only adds a field to rows that are otherwise identical, and `?ids=1` is
informal rather than wrong; `1`/`0` are accepted everywhere for that reason.

**`?format=` on `/agent/context` has three values (SH78, SH115):** `json`, `terse`, and
`agents-md` — the same pack rendered as project agent instructions, which is what
`shio context --as agents-md --out AGENTS.md` writes. An unknown value is a teaching **400**,
not a silent fallback to JSON.

**Measure before you fix, and the measurement is the first deliverable.** P3 makes this binding for
tokens and `ShAgentResponseBudgetTest` for response sizes; the same rule applies to **any** cost —
queries, renders, suite time. A performance change lands as two things in this order: a benchmark
that says what the cost is today, then whatever that number argues for. Not the other way round, and
not the fix alone.

Three reasons it is a rule rather than a preference, all paid for here: SH164/SH239 exist because an
unmeasured claim about cost drifted for three blocks with nobody noticing; SH264 measured a sweep at
21 queries a page and the number *changed the design* of the cache SH311 then built (sweep-scoped,
not longer-lived); and SH314 measured a premise three shipped designs rested on and found it held —
which is only worth knowing because it might not have. A cost that is argued rather than measured is
the same defect as a premise carried in a comment (SH275, SH290), and it fails the same way: quietly,
and in somebody else's environment.

Corollary, from SH322: **when a task says "and whatever a grep turns up", the grep is the
deliverable** — it lands as a lint *before* the fixes, because that is what finds the call site
nobody read. SH279's own first half created one of the nine sites its second half had to fix.

**And the same rule holds for a law, not just a call site (SH338).** When a review finds a design
law is being broken, the instrument that finds *every other* break is the deliverable, and it lands
before the fixes. This is filed because the omission is measurable: eleven lints in this repository
guard **internal consistency** — the code agrees with itself, or with its own documentation, or a
decision was taken rather than inherited — and **none** guards *reachability*, whether the operator
the product is designed for can actually invoke a capability. So a consistency defect becomes a
permanent guard and a reachability defect becomes a task that is fixed once and guards nothing,
which is why **P1 was the only one of the ten laws with no instrument** while P3 has the budget
tests, P5 has `ShByteStabilityLintTest`, P6 has a `fix` on every 4xx and P10 has
`ShConsoleShapeOverlapTest`. The cost is not hypothetical: SH160's `instanceMemory: true`
half-truth came back as SH326's `serverRender: true`, and SH170's session+CSRF friction came back
as SH327 and SH335 — the same classes, two blocks apart, each found by a human doing a build rather
than by a build failing. **Before "fix the thirteen instances", ask what would have failed.**

**P1 has an instrument now, and its exception list is the inventory (SH338).**
`ShAgentP1ConformanceTest` classifies **every** key in the manifest's `features` map — a key
reported `true` must be delivered by a registered `ShMcpTool` or by a path in the manifest's own
endpoint index, or be named in the exception list with the reason a `true` there is still honest.
Both sides were already machine-readable, so it needs no new metadata. Four rules carry the
design. **A feature is not 1:1 with a tool**: `teachingErrors`, `conditionalReads`,
`terseResponses` and four others are properties of *every* route and are classified
`CROSS_CUTTING`, because forcing them into a mapping would make the table lie in the direction
hardest to notice; `multiTenant`, `oidc` and `rateLimit` are `INSTANCE_FACT`s, which describe the
installation rather than offering a capability. **A stale exception fails the build** (SH142's
rule, fourth application): each one names the tool or index path that *would* resolve it and that
name is asserted **absent**, so shipping the route and forgetting the table is a red build rather
than a list that rots into suppression. **The list is asserted over the classification, not over
what the instance advertises** — a config-driven flag (`webhooks`, `previewTokens`,
`scheduledPublish`) drops out when an operator disables the subsystem, and an inventory that
shrinks when somebody turns something off is measuring the environment (SH315's lesson, one
surface along). And **the run prints it**, like the SH264 sweep benchmark.
The measured answer: **52 features, 14 not agent-reachable** — 8 `REST_ONLY`, 3 `CONSOLE_ONLY`,
3 `CLI_ONLY`. Only `reviewQueue` is a decision (SH109 keeps the human's veto off the agent
namespace) and three more are deliberate trades (`graphql` is the front end's, `contentAsFiles` is
CLI-shaped by P4, `postTypeMerge` would put `shio_apply` over its SH132 ceiling). The rest are
gaps, and **four had no task at all** before this ran: `locales` and `scheduledPublish` (SH342 —
id-keyed post verbs with no address-native form), `webhooks` (SH343) and `previewTokens` (SH344).
That is the return on the instrument, and it is the argument for building one before working the
thirteen known instances by hand.

**And a benchmark over a correct script cannot see what an agent gets wrong (SH340).**
`ShAgentTaskBenchmarkTest` measures call and token ratios over a canonical task, which means it
measures a script — and a script never sends `postType` where the surface wants `type`.
`ShAgentNaiveClientBenchmarkTest` drives the MCP surface the way a **naive** client does (the four
wrong calls a real session sent, plus a sweep adding one unrecognised key per read tool) and counts
the outcomes against `naive.*` ceilings in `token-budgets.properties`. **A silent drop is counted
separately and is the expensive one**: a teaching error costs one round trip and the `fix` makes the
retry land, while a dropped argument costs a wrong outcome nobody notices. Measured before SH331: **6 silent drops in 12
calls**, and the sweep was the finding — `shio_context`, `shio_find`, `shio_verify` and
`shio_changes` all accepted an unknown key and answered 200, so the `scope`/`site` case was the
default behaviour of the surface rather than one tool's oversight. That measurement is what turned
SH331 from four renames into one guard; after it, **0 drops and 6/6 naive calls land first try**.
One caution the first run earned: the fixture named a site the isolated database did not contain, so
four rows failed for the *site* and were scored as vocabulary failures. A benchmark about argument
names must fail only for an argument name — SH324's class, in a benchmark instead of a fixture.

**A read-only transaction must not eat the refusal it just wrote (SH345).** A class-level
`@Transactional(readOnly = true)` that throws a `ShTeachingException` declares
`noRollbackFor = ShTeachingException.class`, and `ShTeachingRollbackLintTest` fails the build
otherwise. Without it the bean marks a *participating* transaction rollback-only, so a caller that
declared `noRollbackFor` on purpose — `ShAgentReadService`'s per-row errors, `ShAgentRenderService`'s
SH249 fallback — fails at **commit** with `UnexpectedRollbackException`, nowhere near the cause: that
is how `GET /agent/find?site=nope` answered a 500 instead of a teaching 404. The rule is safe as an
absolute because a read-only transaction has nothing to roll back; a **writing** one is the opposite
and is out of scope, since a refused batch must roll back. Two things the lint got wrong first, both
worth knowing: keyed on any read-only annotation it flags classes whose *writing* methods throw, and
matching `throw new X(` literally it misses the owner-qualified `Outer.XException` — which was the
very bean it was written from.

**A post's locale is on the read, and only when it is not the default (SH348).** `/agent/read` and
`find`'s rows carry `locale` — before this the string appeared nowhere in `ShAgentReadService`, so
`locales: true` advertised a capability no read reported the state of. **Absent means the instance
default**, which the context pack publishes on the first call a session makes, so absence is
unambiguous rather than merely cheap (SH121's pattern, not SH299's: a locale has a knowable default and
a marker count does not). The default is `ShPostUnifiedWriteService.DEFAULT_LOCALE` — named once,
because the writer owns it and the pack reports it. **And `post.upsert` accepts one, on create only** (SH348 step 2): the file projection carries
`locale:`, so a `pt-br` page pulled to files and pushed elsewhere is no longer rebuilt as `en`.
Relocalizing an existing post is deliberately not possible here — that is its own gesture, the way
`post.move` is for an address. **The sibling link is `translationOf`** on the same op: an address, never
a group id, resolved server-side to the target's group. Every translation names the group's *anchor* and
the anchor names nothing, which keeps it a forest; a declaring post is compiled after the others, so a
document never has to be ordered by hand; and a target that resolves to nothing is a teaching 400,
because creating an unlinked post and reporting success is SH331's silent drop.

**A scheduled publish is `when` on `post.publish` (SH342).** ISO-8601, absent means now, and a
**past instant is refused rather than read as now** — publishing now is what the op already means with
no `when`, so accepting a past date would make two calls mean the same thing and swallow the timezone
slip this catches. Checked before the no-drift noop, since "the draft matches what is published" says
nothing about a transition asked for later; a shape over `ShPostUnifiedWriteService#schedule` (P10),
not a second scheduler. Two things it cost, both worth knowing before proposing the next argument:
`tools/list` had **one token** of headroom (2359/2360), so `shio_apply` was trimmed to 440 before the
ceiling moved to 2375 — this surface is a wall, not a budget. And SH338's rot check did **not** fire,
because the exception named the route it expected and the fix took another shape: **the stale check
catches the predicted fix, not any fix.**

**A ROADMAP entry is one sentence: what + why + `→` pointer.** The reasoning belongs in
IMPROVEMENTS.md, which is what the pointer addresses; a line that restates it makes the roadmap a
history instead of a queue, and lets two files disagree about one design. Measured when it was
noticed (SH341): 95 active task lines averaging **142** words, worst 555 — and six of the worst
eight were written in the same session that then found the problem, so treat this as a drift the
process invites rather than a lapse. If the analysis feels too big for the line, that is the
signal it goes in IMPROVEMENTS, not that the line should grow.

**The cost of all of it is measured, not asserted (Block M).** `ShAgentTaskBenchmarkTest`
runs four canonical jobs over the agent surface *and* over the id-keyed console/CDA path and
asserts both ratios; `ShAgentResponseBudgetTest` guards eleven canonical response sizes. Both
read their ceilings from `shio-app/src/test/resources/token-budgets.properties` — the same
file as the SH132 MCP budgets, because a second place to keep a number is a place that goes
stale. Raising a ceiling is allowed and must be justified in the commit.
**And the render sweep is measured in queries, not milliseconds (SH264).**
`ShRenderSweepBenchmarkTest` counts Hibernate statements — a timing ceiling on a shared machine is a
flake, and SH250's rule is that a flake is worse than a break. It prints on every run. The measured answer,
and what SH311 did with it: a rendered digest cost 21 queries per page and the *same* 21 for the next
page of the same site, which is per-page work carrying per-**type** information. **SH311 caches the
resolved templates for the length of one read-only transaction** — a sweep of ten pages is now
**6 queries per page** and a three-site `verify` pass **59 instead of 137**. The lifetime is the
design: a longer-lived cache would need invalidating on a `site.postTypeLayout` write that no
post-level listener sees (SH193's trap), and **a writing transaction gets no cache at all**, which is
`isCurrentTransactionReadOnly()` on the line above the lookup rather than a convention. A *standalone*
render is deliberately still 19 — independent calls share nothing — and the benchmark asserts that,
so a cache that ever outlives a transaction fails there instead of arriving quietly.
**And "bounded by bindings, not pages" is measured (SH314)**, because SH193, SH251 and SH262 all rest
on it: 2 bindings + 60 pages graphs in **12** queries, 20 bindings + 6 pages in **84**. Thirty times
the pages and a tenth of the bindings is seven times cheaper, so the premise holds. Measured
**uncached** on purpose — inside a read-only transaction SH311's cache takes 84 to 8, which is what
production pays and is not what the claim is about.

**A test that measures the whole instance may not share it, and `ShBenchmarkIsolationLintTest`
enforces it (SH315).** Three source rules: a `@SpringBootTest` in the benchmark package declares its
own `spring.datasource.url`; so does any test calling `verify(null, …)`, `context(null, …)` or
`changes(null, …)`; and no two classes claim the same in-memory database. It exists because the
convention was documented on four classes and broken five times (SH239, SH272, SH289, SH314, and two
incident tests found while writing the lint) — always caught by accident, because the code is right
and only the *measurement* is wrong.

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

**Cascade safety is a fixture's problem too (SH289).** `ShPostTypeAttr` maps `shWidget` as
`@ManyToOne(cascade = ALL)`, so removing an attribute cascades a `REMOVE` into the **shared widget
catalog**. `ShPostTypeAdminService.detach` handles it in production; a *test* that mutates a
post-type needs the same three lines, and the one that did not deleted `Text` and
`Ace Editor - Javascript` for the whole JVM — surfacing as `Widget 'Text' is not seeded` thrown by
production code several steps from the cause.

