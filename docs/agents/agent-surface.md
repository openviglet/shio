# The agent surface — /api/v2/agent/** and what bounds it

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### The agent surface — `/api/v2/agent/**` (Block H)
The namespace shaped for a coding agent rather than a browser. It is a **shape over**
the CDA + console services (law P10) and holds no business logic of its own.
- `GET /agent/manifest` — capabilities, limits, a curated endpoint index. **Read this
  first**; unshipped capabilities are reported `false`, never omitted. **Projectable since
  SH174**: `?include=identity,features,limits,elsewhere,endpoints,start-here` and
  `?format=json|terse`,
  with an unknown `format` a teaching 400 the way `/agent/context` has always answered.
  `include=features,limits` is the capability check and measures **298** against the full
  document's **2343** — the read this exists for, since no `304` helps on a session's first
  call. `format=terse` saves only 11%, and that is the finding rather than a defect: this
  document's weight is prose (a purpose sentence and a runnable example, twenty-nine times),
  kept verbatim because an abbreviated example is a call the instance would reject (SH230).
  **`elsewhere` says where a capability lives when it does not live here (SH393)** — eleven
  entries, capability → the command or route to use instead (`exchange` → `shio import <file>`,
  `visualSnapshot` → `shio snapshot <page>`, `pageAudit` → `shio audit <page>`,
  `siteCapture` → `shio clone <url>`, `trash` →
  `console`). It is the third answer
  `features` could not give: `visualSnapshot: false` is the only honest thing this surface can
  say about a PNG and reads as *not possible*, when the truth is *possible, from the CLI*. Its
  keys are asserted **equal both ways** to `ShAgentP1ConformanceTest`'s exception list, so a new
  gap must be closed or published, and a pointer cannot outlive the gap it describes.
  **Its own `include=` section, and that is a measurement rather than taste**: bundled into
  `features` — the obvious home, since both are about capabilities — it took the capability check
  from 326 to **469** tokens, a 44% regression on the cheapest, most repeated read on the surface,
  and `ShAgentResponseBudgetTest` failed the build for it. The first draft also explained each
  entry in prose and cost 138 tokens against 81; the reason belongs in the conformance test, where
  it is reviewed, and the wire carries the command. `response.manifest` is 2650 for it — the raise
  is on that one line, deliberately.
  **The identity scalars now name the build (SH460):** `commit` (abbreviated, from the
  `git.properties` the build writes) and `builtAt` (ISO-8601, when the **running** classes were
  produced). They survive every projection, so `?include=identity` — the cheapest read on the
  surface, and what a client sends to probe reachability — is exactly the call that can say which
  build answered. `version` could not: it reads `dev` on every developer instance, before and
  after a rebuild, so it distinguished nothing. **`ShAgentBuildIdentity` captures both at bean
  construction, and that is the whole mechanism** — reading class timestamps when the request
  arrives reports the compile that happened *after* this JVM loaded its classes, which is a stale
  instance claiming to be fresh. `ShAgentBuildIdentityTest` makes the code source newer than the
  running build and asserts the value did not move. `builtAt` is the field to compare (an IDE
  compiling into `target/classes` on save moves the classes and not `git.properties`, so `commit`
  names a build and does not date it), and it is folded into `digest()` — otherwise a client that
  cached the manifest before a restart is answered `304` by the very instance whose staleness it
  is checking. Measured cost: manifest 2546 → 2574, terse 2305 → 2332, check 341 → 361; **no
  ceiling was raised.**
- `GET /agent/context` — model + sitemap + conventions in one call. `?format=terse`,
  `?include=`, `?budget=`, `?from=`, `?ids=true` (rows are **id-free by default**, SH121).
  The `digest` is how a later session skips re-reading. **`include=all,manifest` folds the
  manifest read into this one (SH157)** — it adds `capabilities` (features, limits,
  startHere) and deliberately *not* the endpoint index, which is ~87% of the manifest,
  changes only when the build does, and is what SH116's ETag already makes free. `manifest`
  and `ops` are **the two sections you must name**: `include=all` still means all the sections
  of the *pack*, so no session pays for a document it already holds. Both are attached
  **after the digest is taken** — a build number must not move a content digest, or an
  upgrade sends every agent re-reading every sitemap.
  **`include=ops` is the write vocabulary (SH346)** — the same bytes `shio://ops` serves, so an
  HTTP-only agent can read which keys each op takes; on its own it is the vocabulary and almost
  nothing else, which is what makes the `/agent/batch` index entry's pointer to it cheap to
  follow. It went here rather than on the manifest because it **measures 447 tokens** against a
  manifest of 2343 — a 19% regression on the first read of every session, for a document only a
  caller about to write needs. The endpoint index used to enumerate op names in prose and now
  points instead, so the manifest got smaller in the change that closed the gap.

**Four standing rules the context pack is built on (SH77, moved here from IMPROVEMENTS by SH356).**
They are not history: each constrains a surface that has not been written yet. **(1) The digest is the
product, not a nicety** — it is what turns "re-read the context" from a decision into a free default,
so every ordering in the pack is explicit and the digest *excludes* the budget and measurement lines.
SH116 wires the same value to `If-None-Match`; **do not start hashing measurements**, or a pack that
did not change will look as if it did. **(2) Only unbounded sections may be truncated.** The model,
sites and conventions are charged unconditionally and the sitemap gets the remainder, because a
partial model is not a smaller answer but a *wrong* one — the agent invents what is missing. A caller
that cannot afford the model drops it explicitly with `include=`. **(3) Charge every bounded section
before the gate opens** — charging one afterwards (a bug that shipped and was caught in test) lets the
walk admit rows a later charge pushes over the ceiling. **(4) Conventions are derived, not stored**,
and only the derivable subset; SH114's author-written store sits on top and the two must stay
distinguishable.

**And two the CLI keeps deliberately different** (`shio context`, SH126): `terse` is its *default*,
because its caller is an agent rather than a browser, and the body is passed through as text —
never parsed and re-serialized — because the digest is computed over exactly those bytes.

**Token-lean is a rule about round trips, not about bytes (SH78, same move).** An agent read **never
includes authoring metadata by default** (`owner`, `date`, `publishStatus`, `modifier`) — the CDA DTOs
settled that in SH1. The measurement that ordered the backlog behind it: the terse encoding saves
**per row, not per payload** (~25–35% on a row, diluted to about a third overall on a small pack where
fixed prose dominates), and what remains is dominated by the **36-character UUID** — which is why path
addressing was the bigger lever and dropping ids (SH121) was worth as much again as the encoding.
**The anti-goal is absolute: terseness must not cost a second round trip.** A response that omits
something the caller must then ask for is a regression, which is why per-field projection waited for
SH123 rather than being forced into SH78 — and why a projection naming a field the type does not have
is a **teaching error** (an empty projection is indistinguishable from an empty post, and the agent's
next move would be to overwrite real content with nothing), and why the response **echoes which fields
came back**: a few tokens that remove an "is this all of it?" round trip.

**Five rules the write surface is built on (SH81/SH82, moved here from IMPROVEMENTS by SH356).**
**(1) A dry run must not write.** The tempting implementation — apply, then roll back — is wrong for
a reason that outlives this endpoint: by the time the rollback is decided, the audit rows, lifecycle
events and quota counters have already happened. Anything calling itself a *plan* has to be
**computed**, which means modelling what earlier steps in the same plan would have created. Every
later `--dry-run`/`plan` surface inherits this — SH93's blueprints, SH108's confirm tokens.
**(2) The plan and the apply are the same code path**, differing only in a flag: `plan` is literally
`apply(dryRun=true)`. The one early version that let them diverge — a dry-run shortcut that also
fired in real runs — silently skipped a publish, which is the failure that makes a plan worthless.
**(3) Imperative and declarative are both needed, and one is built on the other.** A batch answers
"do this now"; a document answers "this is the site", and only the second survives a context
compaction, because it does not require the agent to remember what it already did. The document stays
a **compiler over the ops** rather than a second engine (P10). **(4) Destructive scope is declared,
never inferred**: `prune` deletes only inside the folders the document declares and declaring none is
an error, because a blast radius depending on what the author forgot to mention is the definition of
a surprise (P7). The tri-state `publish` follows the same rule — absent means *leave it alone*, since
treating a trimmed field as `false` would unpublish live pages. **(5) When the console and the agent
perform the same content verb, one of them delegates** — the rule SH245 is still trying to make a
lint.

**And the lesson those five were paid for three times.** A write vocabulary designed against *build
a site* will be missing the verbs for *change a site*, which is the harder half. All three gaps this
engine shipped with — no way to move a post (SH134), a prune that could not remove a folder (SH135),
no way to move a folder (SH137) — were invisible while building and obvious the first time a section
was renamed. The third shows it is not coincidence: building a site never needs it, and its absence
pushed callers to `folder.upsert` + `folder.delete`, which takes the subtree. **When a vocabulary
lacks a verb, callers do not stop — they compose the nearest destructive pair.**

**Three rules every address-taking surface inherits (SH79, moved here by SH356).** **(1) A capability
is not the same as a payload field.** Read naively, "path addressing everywhere" means putting an
`address` on every row — which would have turned this block's biggest *token lever* into a token
**regression**, since a row already carries the site, path and url the address is built from. The
grammar is stated once in the conventions and the resolver makes it actionable; before adding a
field, check whether the caller can already derive it. **(2) Folder paths and post URLs are two
namespaces** — a folder path is a chain of folder names, a `furl` is arbitrary. The scheme says which
is meant, and the lenient `site:` form **refuses to guess** when both are occupied, because an
"upsert this path" write that guessed would create content in the wrong place silently.
**(3) Absent, not forbidden**: an address outside the SH2 allow-list resolves **404, never 403**, so
a scoped credential cannot use the error to prove a site exists.

**And the one exemption, which is the absence of an address rather than a type (SH462).**
`post.publish` and `post.unpublish` take an `id:` when — and only when — the post has **no friendly
URL**. A `File` post is never given one, so `/agent/find` reports a site's assets as `id:<uuid>`,
correctly, and every write then refused that form with a `fix` saying "re-address the op with the
`post:` scheme" — advice this object cannot follow. Because a batch is atomic, one such op failed
the whole run at `ops[0]`: an agent thorough about its assets published nothing, including its
pages. **A surface that emits an address its own writes refuse is the defect**; the whole-batch
failure is what made it expensive rather than untidy. Nothing in rule (2) is weakened — there is no
second namespace to confuse and no address to prefer — and a post that *does* have a URL is still
refused, now by `addressable-by-url`, which hands back **that post's own address** rather than the
scheme it should have used. `post.upsert` and `post.move` are unchanged and must be: they take or
change an address, so an object with none cannot be their subject.

**The filing's other half did not exist, and the measurement is the first assertion in
`ShAgentAssetPublishTest`.** "An uploaded asset lands in DRAFT" — it does not:
`ShStaticFileService.upload` writes **one** row and it is `PUBLISHED` (SH38), so a replica's assets
are live on upload and `/agent/find` reports them so. Publishing one by `id:` is therefore a `noop`,
which is the outcome worth having: the agent is *told* they are already live and its batch survives.

**Making the op reachable found a deletion (SH462, and it shipped in the same commit).**
`ShPostUnifiedWriteService.unpublish` hard-deletes the `PUBLISHED` row and keeps the draft — right
for the post it was written for, because publishing leaves a draft behind. An uploaded `File` has no
draft, so the same call **destroys the object outright**, not even into the SH55 trash. The first
version of the test asserted the round trip and did destroy it; every later test in the class then
failed on an id that no longer resolved, which is the failure a curator would have had one asset at
a time. `nothing-to-fall-back-to` now refuses it on **both** address forms, not just the new one: a
`PUBLISHED` row with no draft is a shape an exchange import can produce for an ordinary post too.

**A site's home page is `post:<site>/`, and that is now written down in the four places it is looked
for (SH433).** It always resolved — `normalizePath` folds `post:Site/` and `post:Site//` to a `furl`
of `/`, the renderer looks for exactly that post, and `/preview/<site>/` then serves it — but nothing
*said* so: `folder:Site` is refused because a folder needs a path, the grammar reads
`post:<site>/<friendly-url>`, and an empty friendly URL looks like the same malformed case. Finding it
cost several probes against a live instance, which is the session P2 exists to remove — and it is the
**first** address a replication needs, because `verify --checks replication` reports
`source-page-missing` for `/` on every one of them. So: the context pack's address convention names it,
the generated `AGENTS.md` names it, `source-page-missing`'s fix names it **only on the root row** (a
sentence about the home page on every missing page is how a fix line stops being read), and
`ShAddress`'s refusal for a path-less `post:` address now answers with `post:<site>/` instead of the
grammar the caller just failed to apply. A path-less **`folder:`** address is deliberately not offered
one — there is no root folder page, and suggesting `folder:Site/` would point at something that does
not exist. This is P6 and P2, not a capability gap: nothing was missing except the sentence.

- `GET /agent/resolve?address=…` — an address → an object id, for id-keyed endpoints.
- `POST /agent/batch` — **writes**: N addressed ops (`site.upsert`, `site.delete`,
  `folder.upsert`, `folder.move`, `post.upsert`, `post.move`, `post.publish`, `post.unpublish`,
  `folder.delete`, `post.delete`) in one transaction; `{"dryRun":true}` reports without writing, and a batch that deletes needs
  SH108's `confirm` from its own dry run. **`?verify=true` returns the run's own proof
  (SH157)** — the SH99 lint over exactly the addresses it touched, which removes the closing
  turn of every write-then-check loop and is better than a separate `/agent/verify` because
  it is scoped to the change rather than the site. It runs *after* the write transaction
  commits (inside it, the lint would read state a rollback is about to discard), a **dry run
  gets none** (linting a state the plan has not created reads as "the plan is clean"), and an
  op that wrote no fields has its address named, because a finding against it predates the
  run. Only `post:` addresses are linted today (SH228).
  **A dry run resolves against the sites the same batch would create (SH329)** — `Declared`
  (SH153) started as something a *blueprint* caller supplied, so a plain
  `{site.upsert Demo}, {folder.upsert Demo/pages}` failed at `ops[1]`: the bootstrap case, where
  nothing else could stand in for it. `Declared.withSitesFrom` now reads the sites off the run's
  own ops, in `runOps` so every entry point including the blueprint path gets it. Still
  **dry-run only** and still **additive** — a declaration never makes a plan greener than the run
  it predicts. The refusal when the site really is absent is `UnknownSiteException.onWrite`,
  which names a `site.upsert` op: the shared resolver used to teach `Pass ?site=`, and this
  endpoint has no `?site=` (P6 — an instruction to do something that does not exist is worse
  than none). It carries **no allow-list branch**, because an empty readable list means both "a
  scoped token" and "a new instance", which want opposite advice; scope is `requireUnscopedCredential`'s
  message to give.
  **`site.upsert` binds a post-type to a layout, per key (SH328)** — `data.postTypeLayout` is
  `{"<post-type>": "<PageLayout title>"}`, and it is the **only** op that can write the one edge
  that makes a site render. Two surfaces already instructed the write (SH272's conventions line,
  SH298's `unused-layout` fix) while `ShSiteWriteService.FILLABLE` was three scalars, so an agent
  could build theme, regions, layout and pages and then not connect them. SH184's rule, one
  field-type wider: an unbound post-type is **filled**, a bound one is **kept and reported**, and
  a key is never a whole-map replace — which is what the console's `PUT /api/v2/site/{id}` is,
  nulling the map when the field is omitted. String values only, matching what the renderer
  reads: the SH193 array form is skipped, so a binding cannot look written and render nothing.
  **And both branches read it, which took a second task (SH453).** The op has two: an existing site
  goes through `fillEmpty`, which takes the fields *and* the bindings; a site that does not exist was
  built from `description`/`url`/`furl` alone and reported `CREATE — a blank site`. So one op that
  created a site and bound its layout was told it succeeded and got no binding — **both halves
  individually correct**, which is why eleven green tests missed it (every one of them creates first
  and binds second, so the op under test is always the *fill* branch) and SH448's conformance suite
  found it on its first run against an instance. The failure surfaced nowhere near the cause: the
  next thing to read the binding is `ShPageRenderService`, so the caller got a 400 from
  `/preview/<site>/` — *"Site '…' binds no layout for post-type '…'"* — after four more successful
  writes, a good message answering a question about the wrong step. §VIII.21 left open whether to
  apply the bindings or **refuse** a create carrying data it will not honour (SH320's rule);
  **applied**, because P2 is the argument for this surface — one op means one intention, and refusing
  would make the first step of every new-site build two ops with the second existing only to work
  around the first. The serialization stays in `ShSiteWriteService` (SH245: a second copy of a verb is
  where the careless version grows), and the report now names the bindings in the fill branch's own
  vocabulary — `a blank site, binding postTypeLayout.Article` — because reporting `a blank site`
  either way is what kept this invisible to the one caller who could have noticed.
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
  **A move that changes a live URL reports the pages it broke (SH140)** — `inboundLinks` on the
  move's own row: `{from, addresses, found, fix}`, where `addresses` are the published posts whose
  authored markup still links to the old path. `ShReference` is id-keyed and survives a move; **a
  URL typed into rich text is a string in a field** and cannot, so a move breaks pages nowhere near
  the one that moved. **The open design question was write-vs-lint, and only the write can answer
  it:** by the time `verify` runs, the old path matches no published URL and is indistinguishable
  from a route the front end owns — which is precisely why `link-unresolved` is a *warning* whose
  fix offers four possibilities. The move holds the old path, knows a live page served it and knows
  where that page went, so here it is a fact with one fix. `ShPostLinks` narrows with the SH52
  full-text predicate and then **re-reads each candidate with `ShHtmlOutline`**, because a `LIKE`
  over `data` matches `/target-2` for `/target` and matches prose that merely mentions the path;
  that scanner is the lint's own, so the two surfaces cannot disagree about what a link is.
  **Published rows only** (a draft's broken link is work in progress), the moved post itself is
  excluded, a **dry run answers the same question** (which is what makes it a plan a human can read
  — P7), a move that changes only the folder reports nothing, and a post that was never live
  reports nothing either: links to it were already broken and belong to `link-unresolved`, not to
  this op. Capped at ten with `found` always stated (SH138), and **absent rather than empty** when
  a move breaks nothing, so a clean move stays byte-identical to its pre-SH140 response.
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
  **`createSite: true` makes the site the document names, as the run's first op (SH439)** — it
  compiles to `site.upsert`, which is what also fixes the *plan*: SH329's `withSitesFrom` reads the
  sites a run would create off the run's own ops, so a dry run into a new site resolves instead of
  404-ing. Opt-in like `prune`, because a document naming a site nobody made is usually a typo and
  the refusal that catches it is worth more than guessing (P7). Reached from `shio apply
  --create-site` and `shio convert --create-site`, one meaning in both. Deliberately **not** an MCP
  argument, for the reason `ShApplyTool` already gives about `blueprint`'s copy of the flag:
  `tools/list` is re-sent every turn (P3), and `shio_write` takes `site.upsert` for free.
  **`moves` is the document's one intent channel for a *post* (SH150)** — `{from, to?, folder?}`,
  compiled to `post.move` after folders and before upserts. **A folder's is `movedFrom` on its own
  `folders` entry (SH211)**, since a folder's name chain is its address and the destination is
  therefore the entry's own `path` — nothing is left for a second field to say. It compiles to
  `folder.move` **between two halves of the folder step**: plain upserts, then the moves, then the
  upserts of the folders that moved, because upserting a destination first *creates* it and
  `folder.move` refuses an occupied target. Its source is protected from `prune` for the same
  reason a post move's is and with a worse outcome — a folder delete cascades to every post
  beneath it, so an unprotected source puts "deleting a section" into the very plan SH108's
  confirm digest is taken over. Everything else here is state, which is what
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
  **And the *empty* case needed the same treatment, which it did not get until SH547** — an ops batch
  sent to `/agent/apply` answered `empty-batch` with `allowed` listing the eleven op names and
  `example` showing an `ops` array: every teaching field describing the other endpoint, and following
  it exactly reproduced the same 400. `Source` was already threaded into `validate` for the ceiling,
  so the fix is one branch: `empty-document`, whose `allowed` is the document's own keys and whose
  `example` is a document. Its `fix` also names `/agent/batch`, because `ops` is not a key here — so
  Jackson drops it and a misdirected batch arrives as a site with no folders and no posts, which means
  one refusal has to answer both readers in one round trip. **`ShAgentEmptyRefusalTest`'s third test is
  the one to keep**: it takes the `example` out of each refusal, posts it back, and requires a
  *different* problem — because P6 is not that a 4xx carries fields, it is that following them works,
  and a document naming the wrong grammar is worse than a bare status. A bare status sends the reader
  to the docs; this one sent them in a circle.
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
- **A clamped `?digest=<n>` says so (SH319)** — the cut row fires when the *pages* run out; nothing
  fired when the *request* was reduced, so `?digest=200` on a ceiling of 100 that affected three
  pages returned three rows and the caller could not tell that from three being all there was. One
  row naming both numbers, in the branch where the cut row is absent so the two never both claim the
  tail. Reported **once**, not per row (SH146), and **silent when nothing was narrowed** — a note on
  every response is the prose P3 says to stop paying for.
- **Every SH79 address a finding names must resolve (SH309)** — `ShAgentVerifyTest`'s
  `everyAddressInAMessageResolves` walks a full report, extracts anything address-shaped from
  `address`, `message` **and** `fix`, and puts it through `ShAddressResolver`. Every teaching message
  here is assembled by string concatenation and the only assertion any of them had was
  `contains("post:")`; SH172's `id:` fallback exists because an address was built in three places and
  one was wrong. A message naming an address that 404s is trusted **less** than one with no address.
  Two exclusions: a **placeholder** (`post:<site>/<url>`) teaches a shape rather than naming an
  object, and a rule that names something **absent on purpose** must be declared in
  `NAMES_SOMETHING_ABSENT` with the reason — one entry today, `no-home-page`, whose fix ends
  `"to":"post:<site>/"`, the page the finding exists because the site has not got.
- **`no-home-page` — a site that publishes and has no page at `/` (SH334)**, a `routes` **warning**
  carrying the `site:` address. The symptom was the Universal Editor opening on a 404 (it frames
  `ShSiteFrontEnd`'s answer, the site root), and the fix was deliberately *not* a page picker:
  **a page at furl `/` is a convention nothing enforced, defaulted or reported.** The SH71 starter
  and the fixtures have one, which is why it never surfaced; an agent following SH79 has no reason
  to create one. Warning, because a site addressed entirely from an external router is a design and
  not a defect. **Skipped when the site publishes nothing** (no front door is missing from a
  building with no rooms) and **skipped on a scoped run** — every other `routes` rule is about a
  subject the run looked at, and this one is about the site.
- `GET /agent/verify` — the content-side `tsc` (SH99/SH100). `checks=content,routes,diagnostics`
  (all by default), `site=`, `limit=`. **`ok` is false only for an *error*** — a warning is
  something the CMS cannot prove from inside itself (an empty folder, a link that may be a
  front-end route), and a gate that failed on those is a gate people bypass. Every finding
  carries an SH79 address and a `fix`. `found` vs `findings` makes a cut visible (SH138's rule).
  **A `diagnostics` finding is always a `warning` (SH330)** — never an error, whatever the kind.
  SH144 made the incident record durable and nothing resolves one, so an *error* there made a
  single past 5xx fail `shio verify` **forever** on content that is provably clean. `ok` is a
  statement about what the CMS can prove from inside itself *now*, and an incident already
  happened; a caller that wants to gate on them reads `warnings`.
  **A `File` post's own `FILE` is a name, not an address (SH470).** `verify --site VigletOrg`
  reported **84 errors** on a replica whose every asset answers 200, all of them one rule on one
  field: `✗ dangling-reference · FILE — Field 'FILE' points at post
  inter-greek-wght-normal-CkhJZR-_.woff2, which does not exist.` It does not exist because it is not
  a post — the eighteen assets a capture uploads are eighteen `File` posts, each storing its own
  filename there, which is what `fileNameOf` has always read it as. **Two readers of one field, and
  the louder one was wrong.** A gate that cries wolf is worse than one that stays quiet, and this
  crossed the line where a reader stops looking; on the replication path it is worse than noise,
  since those checks exist so an agent can prove a replica *without* a human. The exemption is the
  **field**, not the widget: `File` stays in `REFERENCE_WIDGETS` because a user field — the seeded
  `Article.FILE`, or the `hero` the test asserts on — really does hold a file post's id, and it is
  the only path to `missing-asset`. Dropping the widget would silence 84 false errors by deleting a
  check that finds real ones. Both halves are asserted: the count on a fixture holding uploaded
  assets, and that `missing-asset` still fires.
  **And a `File` has no friendly URL by design, so `url-missing` does not fire on one (SH501).**
  Same family one rule along, same replica: three errors — "published but has no friendly URL, so no
  route reaches it" — on `viglet-logo.png` and two fonts that all answered **200** on the same run.
  Bytes are reached at `/file_source/<id>/<name>`, and since SH437 at the path the source used;
  `id:` is a `File`'s correct address (SH172's fallback), not a symptom. **The remedy was worse than
  the finding**: it handed over a `post.move` to "give it one", which would put a furl on the type
  defined by not having one, so an agent that trusted the error would corrupt the content model to
  clear it. The exemption is keyed on the **post type**, so a content post with no furl is still an
  error with the same `post.move` fix — silencing the rule would have cleared three false errors by
  deleting the check that finds real ones, which is SH470's mistake not made twice. The assertion is
  the one that keeps catching this family: **a replica whose every reference answers 200 verifies
  clean** — errors are counted, not one rule's absence. `find`'s own `id:` note (SH172) makes the
  same exemption, since it recommended that same `post.move`.
  **`folder=` / `address=` / `since=` scope the run** (SH143) — a subtree, comma-separated
  `post:` addresses, or an SH88 cursor; they union rather than intersect. A scoped run sets
  `partial` and describes itself in `scope` (both **absent** on a full pass), because `ok` over a
  subset is not a statement about the instance. What scoping narrows is the **subjects**; the
  cross-cutting indexes — who else claims this URL, does this link land, does this folder hold
  anything — stay whole, built from id/url projections. Narrowing them would invent a
  `link-unresolved` for every link out of the scope and miss the duplicate whose twin is
  outside. A `folder=` that is not a folder is a teaching **400**: a scope that covered nothing
  while answering `ok` is the worst false green there is.
  **`checks=page` reads the page the reader gets, which is the gap every other group shares
  (SH516)** — `verify --site VILT` answered **ok — 0 errors, 17 warnings** over a page with **38
  broken links of 39** and **43 broken references**; it had found 12 of the 38 and none of the 43.
  Not a missing rule: every check resolves a reference against the **content graph** — does this
  furl exist, is that `File` post addressable, does the layout name a theme — and all of it was
  true, while the page came out served four segments deep so its relative strings resolved
  elsewhere (SH512) and its query-stringed paths missed (SH515). *The content was self-consistent
  and the page did not work.* Six defects survived a green gate in one session (SH455, SH461,
  SH462, SH512, SH515 and the two that produced this), all six in that gap, each found by hand —
  and the hand was wrong once, reporting *0 broken* on the page with the missing logo because it
  filtered references on a leading slash instead of resolving them. So the group **renders each
  published page and resolves every reference on it against that page's own URL**:
  `page-link-broken` for a path the site publishes nothing at, `page-reference-broken` for bytes it
  does not serve, `page-unrendered` for a published page that does not compose — **errors**, not
  `link-unresolved`'s warning, because the route table judged against belongs to the process that
  will serve the request (`ShFrontEndRoutes` stays the exemption, for a site somebody else
  delivers). Two things make it cheap and honest: it **composes the page in-process** rather than
  fetching — `/sites/**` is that same render plus `ShPreviewLinks`, so it is the same bytes with no
  network, no `allow-fetch` and no request-forgery surface, which is why it is in the default set
  — and it reads references through **`ShPreviewLinks.referencesIn`, the same walk the rewriter
  uses**. That last one is the whole guarantee: a check with a list of its own would go green on
  the one attribute the rewriter reads and it does not. Subjects are the post-types the site binds
  a layout for (`postTypeLayout` *is* the definition of "is a page"), bounded at 50 pages, one
  finding per distinct path per page, and both the cut and the count go in `notes`.
  **And the group judges the artefact on two reads that already held the finding (SH521).**
  `verify` answered **ok** on a page that had lost its hero, its grid and its footer while
  `GET /agent/render` answered `links: 0, images: 0` — the intact version answers `links: 11,
  images: 1` — and listed nine classes the page draws with and nothing styles. *Shio knew.* Not a
  missing fact: a fact nobody joined to a judgement, `unstyledClasses` being a finding written as a
  list and `links: 0` one written as a number. Both are computed from the bytes the group has just
  rendered, so each is a **threshold, not an analysis**. `page-empty` (error) is four conditions at
  once — no heading, no link, no image and under 20 words — because any one alone is a page somebody
  may have meant (a landing page that is one image, a notice that is one block of prose) and all
  four together is the SH520 symptom. `page-unstyled` (warning) reports `unstyledClasses` **only
  when the page loads no external stylesheet**: `ShHtmlAppearance` keeps that list advisory for a
  right reason — the 2018 `bootstrap-site` package draws with CDN classes this instance has never
  seen — and the guard is what makes the same list a finding without inheriting the false positives,
  since the document's own CSS is then the whole of what styles it. It also retires the join that
  was being rebuilt in Python every round and got wrong twice.
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
  are ordered **oldest → newest** so the cursor is always the last row.
  **The feed is commit-ordered, and the guard is a watermark rather than a lag (SH147).**
  SH51 stamps the audit row inside the writer's transaction, so timestamps are a *stamp* order:
  a transaction that stamped earlier can commit after a faster one has been read, and a cursor
  past the faster one never sees it — a **lost edit** on the one surface that exists to be the
  delta. `ShAuditFeedWatermark` closes it with two facts instead of a duration. **Positions are
  reserved, not read off the clock** (`reserve()`, monotonic and unique, so ties vanish and the
  SH88 keyset tie-break becomes belt-and-braces), and **every reserve inside a transaction is
  registered as in flight** and released when it completes either way — so the ceiling is the
  *oldest unfinished write*, exclusive, and a row is reportable only once everything stamped
  before it has finished. `lag-millis` survives at **0**, meaning only what an in-process
  registry cannot see: a second app node against the same database — **and the feed says so when it
  meets one (SH376)**. Positions are monotonic and never below the clock, so a row stamped above the
  highest position this JVM has issued cannot have come from it, which is proof of another writer
  and needs no column, no heartbeat and no second registry. The disclosure is a `notes` line, said
  only once such a row is seen and only while `lag-millis` is 0, so a single instance pays no prose
  for a case it does not have (P3). Three things settled while building it: the observation is
  **sticky**, because an installation does not stop having two nodes when the next page happens not
  to contain a foreign row and a note that flickered would stop this endpoint revalidating (SH159);
  it is **skipped until this JVM has issued a position**, or every single-node instance would report
  a second writer for one read after each restart; and it reports **the fact and deliberately not a
  number** — the obvious "set the lag to the excess we saw" is wrong, because the excess measures
  how long ago *this* node last wrote rather than how far ahead the other one's clock runs, so an
  idle instance would advise a ten-minute lag. It is also **retrospective**, which is the honest
  limit: the proof arrives with the row, so this converts a permanent silent weakness into a
  reported one rather than into a guarantee. Four things worth knowing
  before touching it. **A `seq` column with gap detection — §X.3.1's shape — does not close the
  hole**: a gap is either a live transaction or a rolled-back one, nothing in the row says
  which, so the reader skips after a timeout and a slower transaction loses its edit exactly as
  the lag did; the registry has the fact the database does not. **The hold is per row, not per
  transaction**: holding only the transaction's first position is arithmetically enough and needs
  a thread-bound `TransactionSynchronizationManager` resource to record it, which leaked both
  ways in one suite — a stale binding makes later reserves skip their hold (feed reports a write
  in flight) *and* strands the hold it came with (feed reports nothing, ever). **The exclusive
  margin is a millisecond, not a nanosecond**, because it is compared against a value that has
  been through a timestamp column: microsecond storage hands back a slightly smaller instant and
  `oldest - 1ns` re-admits the row it exists to hold. Reserved positions are microsecond-aligned
  for the same reason. **And the idle ceiling is `max(now, lastReserved)`, never `now`** — a burst
  reserves faster than the clock ticks, so positions run ahead of it, and capping at `now` hid
  every row a fixture had just written. Site-scoped by the SH2 allow-list. **A row carries `subject: "folder"` when the change is structural
  (SH196)** — a section created, renamed, moved, deleted or restored, one row per write and
  never one per post beneath it. Emitted only when it is `folder`, so every post row is the
  bytes it always was (P3); absent means `post`. It is not a shorter spelling of the
  `folder:` address prefix: a row whose object is gone has no address at all, which is the
  normal state of a `DELETE` and the one row where knowing *what* was destroyed matters most.
  A rename arrives as an `UPDATE` with the previous name in `what`.
- `GET /agent/diagnostics` — asynchronous failures (SH103): webhook deliveries, image
  transforms, scheduled publishes, 5xx. **A row carries `frames` — where in Shio it threw (SH190)**:
  `Class.method:line`, innermost first, **at most three** and **only `com.viglet.shio`** frames, no
  arguments and no locals, Spring proxy frames skipped. The old `fix` ended "the instance log has the
  stack trace (the body never will)", which named a file the caller cannot open — an error whose
  remedy is out of the caller's reach is an error that has not been reported. **The exclusions are
  also the security argument** (SH190, kept here by SH356): arguments are *content*, and content in a
  diagnostics feed is how a log leaks what the ACL exists to protect — which is why the trimming is
  the design rather than an optimisation, and what makes a frame cheap enough to attach to every
  incident. **And the absence of a frame is itself signal (SH269, kept by SH356)**: four of the five
  recorders pass one, and the webhook's give-up branch does not, because delivery completed and the
  *subscriber* rejected it — there is no exception of ours and nothing in Shio to point at. SH190 set
  that as a technicality about honesty; the field's presence now distinguishes "we failed" from "they
  refused". A **log-tailing tool
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
  would be three fixtures asserting one line each. **A `catch` that records nothing is a declared
  population now (SH270)** — `ShSilentCatchInventoryTest` asserts the count per package (63,
  measured; it was 65), so adding one is a diff that asks the question instead of a habit. The two it
  removed were the content degradations whose symptom is *silence*: an unparseable `postTypeLayout`
  (every page then refuses with a message about the post-type) and a post whose `data` is not an
  object (the page renders with none of its fields). Both now record the fifth kind, **`content`** —
  the other four are operational ("something we were doing failed"), this one is "the content is
  wrong and the page is quietly incomplete", and it stays narrow because recording every swallowed
  exception is what SH103 refused for 4xx. Recorded by `ShAgentIncidentLog`; repeats fold into one
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
  **`REQUIRES_NEW` was on an overload nobody called, for two blocks (SH358).** `record` was a
  pair — a five-argument method carrying the annotation and delegating to a six-argument one
  carrying nothing — and the only caller took the second, so every incident this product ever
  wrote had *no* demarcation and joined whatever transaction was about to roll back. Nothing
  can see that by reading: the annotation is present, spelled right, on a method called
  `record`, with the javadoc explaining it three lines up. Only *which overload does the caller
  resolve to* finds it. The rule is now all-or-nothing per method name, guarded by
  **`ShAgentTransactionalOverloadLintTest`** over `src/main/java` — public and protected only,
  since a private overload can never be entered through the proxy, and the hatch is
  `@Transactional-overload-exempt:` for the one honest case (delegating through an injected
  self-reference, as `ShStaticFileService.listFiles()` does). It found two more on its first
  run: `ShPageRenderService.render` (every `/preview/**` with a query string lost the
  `noRollbackFor` that keeps a caught teaching 4xx from blowing up at commit) and
  `ShAgentProofService.attach` (SH193's graph walks LAZY associations, so the two-argument
  overload read them with no session open). And **one diagnostics response is one snapshot
  now (SH358)** — the count, the window and `earliest` were three read-only transactions, which
  is the only way the intermittent `{}`-beside-a-populated-`since` could be produced.
  **The flake was `counts`, and it was never a race (SH357).** They were summed over the *page*,
  while this payload's contract has always been that a cap **narrows the detail and never the
  diagnosis**. `default-limit` is 25 against `max-rows` 500, so the 26th-newest kind simply
  vanished — and *which* kinds survived depended on what else happened to be newer, which on a
  shared test context moves with run order. That is what "fails once in fifteen full runs and
  never reproduces" was. Counts now come from an aggregate over the whole table
  (`countsByKind`), one row per kind however large it gets, and `ShAgentDiagnosticsCeilingTest`
  fails on the old code every time. **The lesson worth keeping is about the search, not the
  bug:** two nanosecond-timing hypotheses were eliminated by 600 rounds that reproduced nothing,
  and the cause was a documented contract the code had stopped keeping — order-dependence reads
  exactly like a race from the outside, and is cheap to test for. The same pass removed the
  feed's `ceiling` from this read on its own merits: `/agent/changes` has a ceiling of its own
  (SH147's watermark) so a **cursor** cannot skip a late commit, and diagnostics resumes nothing, so the argument
  could only ever hide a row somebody had just recorded.
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
  **And an incident's position comes from the same watermark as an audit row's (SH147).**
  `ShAgentIncidentStore` kept a monotonic counter of its own (SH372, so the cap would stop
  choosing which row dies by random uuid) and that is half the mechanism — the other half is being
  held back from the feed until the writing transaction completes. **One cursor cannot be
  commit-ordered on only one of its two streams**: once the lag went, the window cursor sat at the
  edge of the feed and an incident stamped from an unsynchronised clock could commit *behind* a
  cursor a reader already held. It failed once in two full suite runs, which is the shape of every
  bug in this paragraph.
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
  **And absence is a conclusion both sides can reach (SH412).** `reconcileNotes` treats identical
  edits as agreement — "the same conclusion reached twice" — but its test required both sides to
  *hold* the note, which excluded the one case where they agreed by **deleting**. SH160 makes
  absence the representation of a forgotten note, so two sides forgetting one left both values
  null, both sides "moved", and the key in `conflicts`. The pull skips a site with any conflict, so
  **none** of its notes were written; and because a conflicted key never reaches `agreed`, the base
  kept it and every later run recomputed the same conflict from the same inputs. It did not decay,
  and the remedy a reader would try — forget it on both sides — was what caused it. Comparing both
  sides *including* their absence fixes it, and recording `agreed` only for a value is what makes
  the resolution stick: the key leaves the base instead of recurring. Found by probing the notes
  merge on the four axes the post merge had already passed — it agreed on three and differed on the
  one where agreement is silent.

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

**SH197 finished the class, and the open question is settled by counting.** SH136 left the
question of whether to ban a hash-ordered `Map.of` too, either everywhere in the model or only
where it reaches a serialized component. **Neither**, and the population is the argument: `main`
holds 112 `Map.of`/`Set.of` sites, of which **48 take no arguments and 21 hold one entry** — those
have no order to lose. Of the 43 multi-entry ones, **27 are MCP schema builders** whose
serialization point is already proved key-sorted by `mcpPayloadsAreCanonicalized`, and the rest
are JSON response bodies, where object key order is not observable to a parser. So a
ban-the-construction rule would need ~40 exemptions to restate what two other rules already say,
and a guard that noisy is one somebody deletes. The invariant is **not** "never build a
hash-ordered map" — it is *no hash-ordered iteration reaches bytes that get compared*, guarded
where the bytes are made.

What SH197 *did* add is the one shape nothing else can catch: **`ShStableMap.copyOf` may never be
handed a `Map.of`/`Set.of` literal.** The helper keeps the order its source had, so a literal with
two or more entries freezes a per-JVM salt behind the name of the fix — worse than the raw defect,
because the next reader sees `ShStableMap` and stops looking. It was live in `ShAgentOps.SPECS`
(ten entries), where `all()` was safe because it projects the source-ordered `OPS` list but
`specified()` returned `keySet()`; it is a `LinkedHashMap` keyed off each spec's own `op` now,
which also removed the op name being typed twice per entry. A literal with one entry or none is
banned by the same rule for the same reading reason, not because it can move.
Two smaller repairs in the same pass: the canonical rule **walks the whole `mcp` package** instead
of naming `ShMcpServer.java` (a rule pinned to a filename is a rule a rename switches off, and
"the scan found something" is asserted), and each pattern is now proved to **match the defect and
spare the fix** — the lookbehind that keeps `ShStableMap` from flagging itself has a test rather
than a comment.

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

**And one rule for an unrecognised parameter *name*, on the writes only (SH430): a mutating agent
endpoint refuses a query parameter it does not declare.** `POST /agent/batch?dryRun=true` created a
site, two folders and three posts and answered `"dryRun": false, "changed": 6` — `ids`, `verify`,
`digest` and `format` are query parameters there and `dryRun` is a body field, so the spelling that
looks exactly like the four flags that *are* query parameters is the one Spring drops. The refusal
is a teaching 400 in three shapes: *it is a body field here* (with the allow-list), *this endpoint
has no `dryRun` and `POST /agent/plan` takes the same document and never writes* (`/agent/apply`),
and *not a parameter of this endpoint* with `didYouMean`. `ShAgentQueryParams` is a
`HandlerInterceptor` on `/api/v2/agent/**` that reads the allow-list off each handler's own
`@RequestParam` declarations — so a new endpoint is covered before it is written, which a
per-endpoint check would not have been (SH338). Accepting `?dryRun=` as a synonym was the other
option and is worse: it makes the convention ambiguous rather than merely undiscoverable (SH365).
**Reads stay lenient** for SH320's reason, and `POST /mcp` is outside it — a malformed MCP message
must come back as JSON-RPC, not as an HTTP problem body.

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

**`site.delete` closes the other half of SH170 (SH463)**, and the instrument half of that task is
worth more than the verb. An agent could create a site and not remove one, so clearing sixteen
scratch sites took the browser's dance: `DELETE /api/v2/site/{id}` with Basic auth answered **403**
on all sixteen and only `GET /api/v2/csrf` + its cookie + the echoed token worked. It is
maintenance rather than an edge case — SH448's suite seeds a site per scenario and a **failed**
test never reaches teardown, so every red run leaves debris, and the suite's own harness had
hand-rolled a session for it and called the asymmetry "not this task's to fix" in a code comment,
filed nowhere. The op is destructive (SH108's confirm token) and its plan line says the thing
`folder.delete`'s cannot: **it is purged, not trashed, so there is no undo** —
`ShSiteWriteService.delete` trashes each root folder and then purges it (SH454), and an agent that
read this as restorable would not offer the human an undo that does not exist. Scoped credentials
are refused: an allow-list says what may be read and written, never that the container may be
destroyed.

**Two instruments existed to find that gap and neither could — which is the part to remember
(SH463).** `ShAgentP1ConformanceTest` is keyed on manifest `features`, and site deletion had **no
key**, so it was not classified at all: an inventory keyed on what the manifest advertises measures
the manifest, not the surface. `ShStatelessReachabilityLintTest` classified a whole controller
**root**, so `/api/v2/site` read as covered on the strength of `site.upsert` while the same
controller declared `DELETE` as well. Both are fixed in the same commit as the verb: `siteDelete`
is a feature key (so the classification is forced), and an `AGENT_EQUIVALENT` entry now **names the
HTTP verbs its agent form delivers**, asserted both ways against the verbs the source declares. On
its first run the new check found two more undeclared decisions nobody had written down —
`/api/v2/object PUT` (`copyto` has no op and needs none: a copy is a read plus a `post.upsert` at
the new address, which is better, because an op that duplicated an object would have to decide what
happens to its id) and `/api/v2/post-unified PATCH` (SH335's partial merge has no op deliberately:
`post.upsert` declares *whole* state, and the merge exists for the editor's several-curators
problem, which an agent does not have).

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

**Which surface owns a capability is a decision, and it is written down now (SH393).** SH268
argued a zip cannot be a JSON-RPC message, SH186 that a JSON-shaped verb becomes an op rather than
a thirteenth tool, SH104 that a human artifact has no server surface, SH288 that a file beats a
read command, SH262 that a projection beats a new endpoint, SH391 that a browser lives where a
browser lives. **Six tasks, one boundary, derived six times — each correctly, and each from
memory**: exactly the shape SH338 named one level up. The rule they converge on:

> **MCP owns address-in, small-JSON-out verbs a session repeats**, because a client may have no
> shell. **The CLI owns whatever has a file, a byte stream, a browser or a human on one end**,
> because none of those fits a JSON-RPC frame. **The console owns the human's veto.** Nothing is
> owned by neither — an unowned capability is the gap, not the answer.

Two things make it checkable rather than another paragraph to remember. The classification already
records the owning surface (`MCP_TOOL` · `AGENT_ROUTE` · `REST_ONLY` · `CONSOLE_ONLY` · `CLI_ONLY`)
with a reason per entry; SH393 publishes the exceptions as the manifest's **`elsewhere`** map and
asserts **set equality both ways**, so a capability this surface does not carry either gets closed
or gets a pointer, and a pointer cannot outlive the gap. The boundary is now a lookup for the
seventh task instead of a derivation — and the client that used to learn it by failing reads it.

**Translate dialects first, correct spelling second (SH80, moved here by SH356).** Nearest-string
matching is silent on exactly the guess an agent is most likely to make: `RichText` is four edits
from `Text` and further from every other widget, because it is a **vocabulary** mismatch — the
Contentful/Sanity/Strapi/Payload dialects — rather than a spelling one, so a `didYouMean` built only
on edit distance answers nothing in the common case. The fix is a curated alias map consulted
**before** the distance fallback (`ShWidgetAliases`), constrained to only ever suggest a target this
instance has installed. The rule generalises wherever a closed vocabulary meets a caller who learned
a different one; the surfaces that still have only the distance fallback are post-state names, locale
codes and the blueprint parameter vocabulary.

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

**A write is never refused by its own audit row, and a refusal that happens anyway names the op
(SH544).** `ShHistory.description` is `VARCHAR(255)` and `ShContentAuditService.describe()` composed
`Created "<title>"` with nothing between them, so five of one site's 198 page titles — ordinary
marketing sentences over 245 characters — ended a **201-page** conversion with a 500, after 403
assets had already been uploaded. Two defects, and only one of them is the column. The trail exists
to *describe* a write and was deciding whether the write happened: the description is derived, so it
is bounded where it is built, and the title gives way (the parenthesised `detail` an SH196 reader
needs survives). The second is that the refusal was a **500 with Spring's default body** — nothing on
the wire named the column, the width, the op or the address, so it was diagnosed from the server log,
and SH513's CLI cannot print a field the server never sent. `ShDataIntegrityProblem` is now the one
mapping both boundaries use: SQLSTATE `22001` is a 400 `value-too-long`, any other constraint a 409
`data-integrity`, and never a 500 — **a 500 tells an agent to retry, which is the one thing that
cannot work here**. In a batch the problem carries `at: ops[i]` plus the op and its address, and what
makes that index *true* is not an extra flush but an **earlier** one: Hibernate auto-flushes before
the next op's address query anyway, so without it a refused insert surfaces inside `ops[i+1]` and an
`at` naming the wrong op is worse than none (P6). The constant and the schema are held together
against the **changelog**, not the `@Column` annotation — `ddl-auto=none`, so asserting the entity
agrees with itself proves nothing.

**`state` is which row you got; `published` is whether the page is live (SH545).** A publish writes
a second row and leaves the draft alone, and every agent read is DRAFT-preferred so the agent can
see its own work — so `state` is `DRAFT` for a page that has been serving publicly for weeks, and
the question a curator cares about most had **no word at all**. What that cost is worth stating,
because it is P9 exactly: an agent that had just published could confirm it only by publishing again
(correctly answered `noop`), by calling the public route and reading the state off an HTTP code, or
by reporting a failure that had not happened. Repointing `state` was not available — it would then
lie about where `data` came from — so `/agent/read` and `/agent/find` carry **`published`**, and
after a publish `state` is still `DRAFT`, correctly. **On `find` it costs no query**: the walk
already fetches both state rows and `dedupe` was throwing the published one away.

**Two measurements decided its shape, and both refused the first design.** Present-on-every-row
(`published: false` on a draft) cost 2.6% of a single-address read and dropped
`attribution.addressing@scale.min-token-ratio` below its floor; grounding the convention in a full
sentence on the context pack dropped `discovery` below its own. So it is **conditional** — present
only when the page is live, an eight-word clause on the pack's existing *writes land in DRAFT* line
making absence an answer rather than a gap — which is SH348's pattern (`locale`) and not SH299's
(present and zero), and the right one because publication *has* a knowable default: P7 makes DRAFT
what a write lands in. The one floor that did move is recorded in `token-budgets.properties` with
the reason it is not an idea ceasing to pay: the baseline it is measured against
(`GET /api/v2/post-unified/{id}`) has always returned `published`, so 53.1× was partly the agent
read being cheaper by omitting a fact the console read carried, and the second call to the public
route that bought it was never counted. **The sitemap's `Node.state` has the same ambiguity and is
not fixed here** — it is a third surface with its own budget and an SH116 digest that a field would
move, and confirming a publish is not what a structure map is read for.

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
own `spring.datasource.url`; so does any test making an **instance-wide read**; and no two classes
claim the same in-memory database.
**Since SH318 that middle list is derived, not remembered** — a `*Service` method whose first
parameter is `String site` is instance-wide when that argument is `null`, *by construction*, so the
scan finds `find` and the SH160 memory reads that the hand-written `verify`/`context`/`changes` never
mentioned while its message implied the three were exhaustive (SH260's move for helpers and SH300's
for DOM attributes, on the third hand-kept list). It caught two real ones on the way in —
`ShAgentMemoryTest` and `ShRememberToolTest` both asserted on `notes(null)` from the shared context.
The match requires a **`Service.` receiver**, which is load-bearing twice: without it
`Declared.of(String site, …)` — a factory nested in a `*Service.java` — matched `List.of(null)` in an
unrelated test, and a *test-local* `verify(String checks)` helper that scopes correctly was flagged as
the opposite. Reads with no site parameter at all (`diagnostics`) stay declared, since there is no
nullable argument to notice. It exists because the
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

