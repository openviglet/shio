# The agent surface: /api/v2/agent/** and what bounds it

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### The agent surface: `/api/v2/agent/**` (Block H)
The namespace shaped for a coding agent rather than a browser. It is a **shape over**
the CDA + console services (law P10) and holds no business logic of its own.
- `GET /agent/manifest`: capabilities, limits, a curated endpoint index. **Read this
  first**; unshipped capabilities are reported `false`, never omitted. **Projectable since
  SH174**: `?include=identity,features,limits,elsewhere,endpoints,start-here` and
  `?format=json|terse`,
  with an unknown `format` a teaching 400 the way `/agent/context` has always answered.
  `include=features,limits` is the capability check and measures **298** against the full
  document's **2343**, the read this exists for, since no `304` helps on a session's first
  call. `format=terse` saves only 11%, and that is the finding rather than a defect: this
  document's weight is prose (a purpose sentence and a runnable example, twenty-nine times),
  kept verbatim because an abbreviated example is a call the instance would reject (SH230).
  **`elsewhere` says where a capability lives when it does not live here (SH393)**: eleven
  entries, capability → the command or route to use instead (`exchange` → `shio import <file>`,
  `visualSnapshot` → `shio snapshot <page>`, `pageAudit` → `shio audit <page>`,
  `siteCapture` → `shio clone <url>`, `trash` →
  `console`). It is the third answer
  `features` could not give: `visualSnapshot: false` is the only honest thing this surface can
  say about a PNG and reads as *not possible*, when the truth is *possible, from the CLI*. Its
  keys are asserted **equal both ways** to `ShAgentP1ConformanceTest`'s exception list, so a new
  gap must be closed or published, and a pointer cannot outlive the gap it describes.
  **Its own `include=` section, and that is a measurement rather than taste**: bundled into
  `features`: the obvious home, since both are about capabilities, it took the capability check
  from 326 to **469** tokens, a 44% regression on the cheapest, most repeated read on the surface,
  and `ShAgentResponseBudgetTest` failed the build for it. The first draft also explained each
  entry in prose and cost 138 tokens against 81; the reason belongs in the conformance test, where
  it is reviewed, and the wire carries the command. `response.manifest` is 2650 for it, the raise
  is on that one line, deliberately.
  **The identity scalars now name the build (SH460):** `commit` (abbreviated, from the
  `git.properties` the build writes) and `builtAt` (ISO-8601, when the **running** classes were
  produced). They survive every projection, so `?include=identity`, the cheapest read on the
  surface, and what a client sends to probe reachability, is exactly the call that can say which
  build answered. `version` could not: it reads `dev` on every developer instance, before and
  after a rebuild, so it distinguished nothing. **`ShAgentBuildIdentity` captures both at bean
  construction, and that is the whole mechanism**, reading class timestamps when the request
  arrives reports the compile that happened *after* this JVM loaded its classes, which is a stale
  instance claiming to be fresh. `ShAgentBuildIdentityTest` makes the code source newer than the
  running build and asserts the value did not move. `builtAt` is the field to compare (an IDE
  compiling into `target/classes` on save moves the classes and not `git.properties`, so `commit`
  names a build and does not date it), and it is folded into `digest()`, otherwise a client that
  cached the manifest before a restart is answered `304` by the very instance whose staleness it
  is checking. Measured cost: manifest 2546 → 2574, terse 2305 → 2332, check 341 → 361; **no
  ceiling was raised.**
- `GET /agent/context`: model + sitemap + conventions in one call. `?format=terse`,
  `?include=`, `?budget=`, `?from=`, `?ids=true` (rows are **id-free by default**, SH121).
  The `digest` is how a later session skips re-reading. **`include=all,manifest` folds the
  manifest read into this one (SH157)**, it adds `capabilities` (features, limits,
  startHere) and deliberately *not* the endpoint index, which is ~87% of the manifest,
  changes only when the build does, and is what SH116's ETag already makes free. `manifest`
  and `ops` are **the two sections you must name**: `include=all` still means all the sections
  of the *pack*, so no session pays for a document it already holds. Both are attached
  **after the digest is taken**: a build number must not move a content digest, or an
  upgrade sends every agent re-reading every sitemap.
  **`include=ops` is the write vocabulary (SH346)**: the same bytes `shio://ops` serves, so an
  HTTP-only agent can read which keys each op takes; on its own it is the vocabulary and almost
  nothing else, which is what makes the `/agent/batch` index entry's pointer to it cheap to
  follow. It went here rather than on the manifest because it **measures 447 tokens** against a
  manifest of 2343, a 19% regression on the first read of every session, for a document only a
  caller about to write needs. The endpoint index used to enumerate op names in prose and now
  points instead, so the manifest got smaller in the change that closed the gap.

**Four standing rules the context pack is built on (SH77, moved here from IMPROVEMENTS by SH356).**
They are not history: each constrains a surface that has not been written yet. **(1) The digest is the
product, not a nicety**, it is what turns "re-read the context" from a decision into a free default,
so every ordering in the pack is explicit and the digest *excludes* the budget and measurement lines.
SH116 wires the same value to `If-None-Match`; **do not start hashing measurements**, or a pack that
did not change will look as if it did. **(2) Only unbounded sections may be truncated.** The model,
sites and conventions are charged unconditionally and the sitemap gets the remainder, because a
partial model is not a smaller answer but a *wrong* one, the agent invents what is missing. A caller
that cannot afford the model drops it explicitly with `include=`. **(3) Charge every bounded section
before the gate opens**, charging one afterwards (a bug that shipped and was caught in test) lets the
walk admit rows a later charge pushes over the ceiling. **(4) Conventions are derived, not stored**,
and only the derivable subset; SH114's author-written store sits on top and the two must stay
distinguishable.

**And two the CLI keeps deliberately different** (`shio context`, SH126): `terse` is its *default*,
because its caller is an agent rather than a browser, and the body is passed through as text,
never parsed and re-serialized, because the digest is computed over exactly those bytes.

**Token-lean is a rule about round trips, not about bytes (SH78, same move).** An agent read **never
includes authoring metadata by default** (`owner`, `date`, `publishStatus`, `modifier`), the CDA DTOs
settled that in SH1. The measurement that ordered the backlog behind it: the terse encoding saves
**per row, not per payload** (~25–35% on a row, diluted to about a third overall on a small pack where
fixed prose dominates), and what remains is dominated by the **36-character UUID**, which is why path
addressing was the bigger lever and dropping ids (SH121) was worth as much again as the encoding.
**The anti-goal is absolute: terseness must not cost a second round trip.** A response that omits
something the caller must then ask for is a regression, which is why per-field projection waited for
SH123 rather than being forced into SH78, and why a projection naming a field the type does not have
is a **teaching error** (an empty projection is indistinguishable from an empty post, and the agent's
next move would be to overwrite real content with nothing), and why the response **echoes which fields
came back**: a few tokens that remove an "is this all of it?" round trip.

**Five rules the write surface is built on (SH81/SH82, moved here from IMPROVEMENTS by SH356).**
**(1) A dry run must not write.** The tempting implementation (apply, then roll back) is wrong for
a reason that outlives this endpoint: by the time the rollback is decided, the audit rows, lifecycle
events and quota counters have already happened. Anything calling itself a *plan* has to be
**computed**, which means modelling what earlier steps in the same plan would have created. Every
later `--dry-run`/`plan` surface inherits this, SH93's blueprints, SH108's confirm tokens.
**(2) The plan and the apply are the same code path**, differing only in a flag: `plan` is literally
`apply(dryRun=true)`. The one early version that let them diverge, a dry-run shortcut that also
fired in real runs, silently skipped a publish, which is the failure that makes a plan worthless.
**(3) Imperative and declarative are both needed, and one is built on the other.** A batch answers
"do this now"; a document answers "this is the site", and only the second survives a context
compaction, because it does not require the agent to remember what it already did. The document stays
a **compiler over the ops** rather than a second engine (P10). **(4) Destructive scope is declared,
never inferred**: `prune` deletes only inside the folders the document declares and declaring none is
an error, because a blast radius depending on what the author forgot to mention is the definition of
a surprise (P7). The tri-state `publish` follows the same rule, absent means *leave it alone*, since
treating a trimmed field as `false` would unpublish live pages. **(5) When the console and the agent
perform the same content verb, one of them delegates**, the rule SH245 is still trying to make a
lint.

**And the lesson those five were paid for three times.** A write vocabulary designed against *build
a site* will be missing the verbs for *change a site*, which is the harder half. All three gaps this
engine shipped with, no way to move a post (SH134), a prune that could not remove a folder (SH135),
no way to move a folder (SH137), were invisible while building and obvious the first time a section
was renamed. The third shows it is not coincidence: building a site never needs it, and its absence
pushed callers to `folder.upsert` + `folder.delete`, which takes the subtree. **When a vocabulary
lacks a verb, callers do not stop, they compose the nearest destructive pair.**

**Three rules every address-taking surface inherits (SH79, moved here by SH356).** **(1) A capability
is not the same as a payload field.** Read naively, "path addressing everywhere" means putting an
`address` on every row, which would have turned this block's biggest *token lever* into a token
**regression**, since a row already carries the site, path and url the address is built from. The
grammar is stated once in the conventions and the resolver makes it actionable; before adding a
field, check whether the caller can already derive it. **(2) Folder paths and post URLs are two
namespaces**, a folder path is a chain of folder names, a `furl` is arbitrary. The scheme says which
is meant, and the lenient `site:` form **refuses to guess** when both are occupied, because an
"upsert this path" write that guessed would create content in the wrong place silently.
**(3) Absent, not forbidden**: an address outside the SH2 allow-list resolves **404, never 403**, so
a scoped credential cannot use the error to prove a site exists.

**And the one exemption, which is the absence of an address rather than a type (SH462).**
`post.publish` and `post.unpublish` take an `id:` when (and only when) the post has **no friendly
URL**. A `File` post is never given one, so `/agent/find` reports a site's assets as `id:<uuid>`,
correctly, and every write then refused that form with a `fix` saying "re-address the op with the
`post:` scheme", advice this object cannot follow. Because a batch is atomic, one such op failed
the whole run at `ops[0]`: an agent thorough about its assets published nothing, including its
pages. **A surface that emits an address its own writes refuse is the defect**; the whole-batch
failure is what made it expensive rather than untidy. Nothing in rule (2) is weakened, there is no
second namespace to confuse and no address to prefer, and a post that *does* have a URL is still
refused, now by `addressable-by-url`, which hands back **that post's own address** rather than the
scheme it should have used. `post.upsert` and `post.move` are unchanged and must be: they take or
change an address, so an object with none cannot be their subject.

**The filing's other half did not exist, and the measurement is the first assertion in
`ShAgentAssetPublishTest`.** "An uploaded asset lands in DRAFT", it does not:
`ShStaticFileService.upload` writes **one** row and it is `PUBLISHED` (SH38), so a replica's assets
are live on upload and `/agent/find` reports them so. Publishing one by `id:` is therefore a `noop`,
which is the outcome worth having: the agent is *told* they are already live and its batch survives.

**Making the op reachable found a deletion (SH462, and it shipped in the same commit).**
`ShPostUnifiedWriteService.unpublish` hard-deletes the `PUBLISHED` row and keeps the draft, right
for the post it was written for, because publishing leaves a draft behind. An uploaded `File` has no
draft, so the same call **destroys the object outright**, not even into the SH55 trash. The first
version of the test asserted the round trip and did destroy it; every later test in the class then
failed on an id that no longer resolved, which is the failure a curator would have had one asset at
a time. `nothing-to-fall-back-to` now refuses it on **both** address forms, not just the new one: a
`PUBLISHED` row with no draft is a shape an exchange import can produce for an ordinary post too.

**A site's home page is `post:<site>/`, and that is now written down in the four places it is looked
for (SH433).** It always resolved, `normalizePath` folds `post:Site/` and `post:Site//` to a `furl`
of `/`, the renderer looks for exactly that post, and `/preview/<site>/` then serves it, but nothing
*said* so: `folder:Site` is refused because a folder needs a path, the grammar reads
`post:<site>/<friendly-url>`, and an empty friendly URL looks like the same malformed case. Finding it
cost several probes against a live instance, which is the session P2 exists to remove, and it is the
**first** address a replication needs, because `verify --checks replication` reports
`source-page-missing` for `/` on every one of them. So: the context pack's address convention names it,
the generated `AGENTS.md` names it, `source-page-missing`'s fix names it **only on the root row** (a
sentence about the home page on every missing page is how a fix line stops being read), and
`ShAddress`'s refusal for a path-less `post:` address now answers with `post:<site>/` instead of the
grammar the caller just failed to apply. A path-less **`folder:`** address is deliberately not offered
one, there is no root folder page, and suggesting `folder:Site/` would point at something that does
not exist. This is P6 and P2, not a capability gap: nothing was missing except the sentence.

- `GET /agent/resolve?address=…`: an address → an object id, for id-keyed endpoints.
- `POST /agent/batch`: **writes**: N addressed ops (`site.upsert`, `site.delete`, `site.restore`,
  `folder.upsert`, `folder.move`, `folder.copy`, `post.upsert`, `post.move`, `post.publish`, `post.unpublish`,
  `folder.delete`, `post.delete`) in one transaction; `{"dryRun":true}` reports without writing, and a batch that deletes needs
  SH108's `confirm` from its own dry run. **`?verify=true` returns the run's own proof
  (SH157)**, the SH99 lint over exactly the addresses it touched, which removes the closing
  turn of every write-then-check loop and is better than a separate `/agent/verify` because
  it is scoped to the change rather than the site. It runs *after* the write transaction
  commits (inside it, the lint would read state a rollback is about to discard), a **dry run
  gets none** (linting a state the plan has not created reads as "the plan is clean"), and an
  op that wrote no fields has its address named, because a finding against it predates the
  run. Only `post:` addresses are linted today (SH228).
  **A dry run resolves against the sites the same batch would create (SH329)**: `Declared`
  (SH153) started as something a *blueprint* caller supplied, so a plain
  `{site.upsert Demo}, {folder.upsert Demo/pages}` failed at `ops[1]`: the bootstrap case, where
  nothing else could stand in for it. `Declared.withSitesFrom` now reads the sites off the run's
  own ops, in `runOps` so every entry point including the blueprint path gets it. Still
  **dry-run only** and still **additive**, a declaration never makes a plan greener than the run
  it predicts. The refusal when the site really is absent is `UnknownSiteException.onWrite`,
  which names a `site.upsert` op: the shared resolver used to teach `Pass ?site=`, and this
  endpoint has no `?site=` (P6, an instruction to do something that does not exist is worse
  than none). It carries **no allow-list branch**, because an empty readable list means both "a
  scoped token" and "a new instance", which want opposite advice; scope is `requireUnscopedCredential`'s
  message to give.
  **`site.upsert` binds a post-type to a layout, per key (SH328)**: `data.postTypeLayout` is
  `{"<post-type>": "<PageLayout title>"}`, and it is the **only** op that can write the one edge
  that makes a site render. Two surfaces already instructed the write (SH272's conventions line,
  SH298's `unused-layout` fix) while `ShSiteWriteService.FILLABLE` was three scalars, so an agent
  could build theme, regions, layout and pages and then not connect them. SH184's rule, one
  field-type wider: an unbound post-type is **filled**, a bound one is **kept and reported**, and
  a key is never a whole-map replace, which is what the console's `PUT /api/v2/site/{id}` is,
  nulling the map when the field is omitted. String values only, matching what the renderer
  reads: the SH193 array form is skipped, so a binding cannot look written and render nothing.
  **And both branches read it, which took a second task (SH453).** The op has two: an existing site
  goes through `fillEmpty`, which takes the fields *and* the bindings; a site that does not exist was
  built from `description`/`url`/`furl` alone and reported `CREATE — a blank site`. So one op that
  created a site and bound its layout was told it succeeded and got no binding, **both halves
  individually correct**, which is why eleven green tests missed it (every one of them creates first
  and binds second, so the op under test is always the *fill* branch) and SH448's conformance suite
  found it on its first run against an instance. The failure surfaced nowhere near the cause: the
  next thing to read the binding is `ShPageRenderService`, so the caller got a 400 from
  `/preview/<site>/`: *"Site '…' binds no layout for post-type '…'"*, after four more successful
  writes, a good message answering a question about the wrong step. §VIII.21 left open whether to
  apply the bindings or **refuse** a create carrying data it will not honour (SH320's rule);
  **applied**, because P2 is the argument for this surface, one op means one intention, and refusing
  would make the first step of every new-site build two ops with the second existing only to work
  around the first. The serialization stays in `ShSiteWriteService` (SH245: a second copy of a verb is
  where the careless version grows), and the report now names the bindings in the fill branch's own
  vocabulary (`a blank site, binding postTypeLayout.Article`) because reporting `a blank site`
  either way is what kept this invisible to the one caller who could have noticed.
  **`?digest=true` closes the *appearance* loop the same way (SH194)**, the two SH187 hashes for
  each page the run **affected**, which for a write to a shared layout or region is not the address
  written but every page that renders through it (SH193's graph; this is that graph's first consumer
  that uses it to *do* something rather than to report). Only the hashes, never the outline: the
  caller is asking whether something moved, which is a comparison against a value it already holds.
  **One exception since SH301: `markers`, present only when non-zero.** A marker count is not a
  comparison, it is an assertion about the page *now* ("this one is rendering a slot with no
  Region"), and the caller being told which pages a shared-region write reached is exactly who needs
  it. It costs no extra work (each row is already a real render, and it has carried the count since
  SH299) and nothing at all on a clean run: measured, a clean row stays byte-identical at ~25 tokens
  and a broken one is ~43, against a second digest call per page at 6 queries plus a full response.
**`?digest=<n>` since SH266**: true for the instance's default (ten), or a count the server
  **clamps** to `shio.agent.render.max-write-digests` and reports the cut as a row (SH138). One
  parameter, because everywhere else here a count *is* the parameter; a bad value (`yes`, `0`) is a
  teaching 400, since a caller that believes it asked for something must not be answered a different
  question. Ten is a measurement now, not a guess: SH264 puts a swept page at 6 queries, so the
  default is ~60 queries behind one write. The ceiling is in the manifest's `limits`, because one the
  caller cannot see is one it learns by being cut off. Skipped on a `dryRun` for the
  same reason `verify` is, and opt-in so nobody pays for a render they did not ask for.
  `ShAgentProofService.attach` is `@Transactional(readOnly = true)`: it still runs after the write
  commits, and it needs a session of its own because the graph walks LAZY associations. **`?format=terse` since SH157**,
  the write surface was the one part of this namespace without one. **A `post.upsert` refuses a `data` key the post-type
  does not declare** (SH168), a 400 carrying the field list and the nearest name, because an
  upsert declares whole state and a typo *replaced* the field it meant. Keys the post already
  holds are grandfathered (so a post carrying one stays fixable) and the `unknown-field` lint
  rule reports those; a **plan** warns on the row where a real run refuses, so a blueprint plan
  can still report the whole SH152 mismatch. **`post.move` (SH134) is the
  only way to rename or relocate a post**, re-addressing a `post.upsert` creates a
  second post and orphans the first with its id, audit trail and history. It takes `to`
  (new address) and/or `folder`, moves the DRAFT *and* PUBLISHED rows together (so a
  published post's live URL changes immediately, and the change set says so), refuses an
  occupied target and refuses to cross sites.
  **A move that changes a live URL reports the pages it broke (SH140)**: `inboundLinks` on the
  move's own row: `{from, addresses, found, fix}`, where `addresses` are the published posts whose
  authored markup still links to the old path. `ShReference` is id-keyed and survives a move; **a
  URL typed into rich text is a string in a field** and cannot, so a move breaks pages nowhere near
  the one that moved. **The open design question was write-vs-lint, and only the write can answer
  it:** by the time `verify` runs, the old path matches no published URL and is indistinguishable
  from a route the front end owns, which is precisely why `link-unresolved` is a *warning* whose
  fix offers four possibilities. The move holds the old path, knows a live page served it and knows
  where that page went, so here it is a fact with one fix. `ShPostLinks` narrows with the SH52
  full-text predicate and then **re-reads each candidate with `ShHtmlOutline`**, because a `LIKE`
  over `data` matches `/target-2` for `/target` and matches prose that merely mentions the path;
  that scanner is the lint's own, so the two surfaces cannot disagree about what a link is.
  **Published rows only** (a draft's broken link is work in progress), the moved post itself is
  excluded, a **dry run answers the same question** (which is what makes it a plan a curator can read,
  P7), a move that changes only the folder reports nothing, and a post that was never live
  reports nothing either: links to it were already broken and belong to `link-unresolved`, not to
  this op. Capped at ten with `found` always stated (SH138), and **absent rather than empty** when
  a move breaks nothing, so a clean move stays byte-identical to its pre-SH140 response.
  **`folder.move` (SH137) is the same rule one level up, and the more important one**: the
  workaround it replaces is `folder.upsert` + `folder.delete`, and that delete takes every post
  beneath it. `to` is the folder's **whole name chain** (that is what addresses a folder), so
  its last segment is the new name and rename/relocate are one op. **No post's URL changes**: a
  friendly URL is site-unique and independent of the folder chain, so the pages inside a moved
  section keep their addresses, which is why the op is deliberately **not** destructive and
  needs no `confirm`. Making it demand one would push callers back to the pair it replaces.
  Its four refusals (cross-site, occupied chain, a site root, and a destination inside the
  folder's own subtree) live on **`ShFolderWriteService#move`, the one folder-move writer,
  which the console's `/moveto` also calls** (SH139's rule). Do not reintroduce a local
  re-parent on either side: the console's had no cycle check, and since its move dialog
  navigates *down* from the site root, a curator could pick a child of the folder being
  dragged, which succeeds and detaches the subtree from the root. `checkMovable` is how a
  `dryRun` reports those refusals without writing.
  **`folder.copy` (SH244) is the last content verb the console had to itself**: copy lived at
  `PUT /api/v2/object/copyto/{dest}` and nowhere else. For a **post** that was close to harmless:
  an agent duplicates a page by reading it and upserting the fields elsewhere, and a copy of a post
  *is* its field values somewhere else, which is why there is no `post.copy` and a second name on
  `tools/list` forever was refused on SH733's grounds. The gap is the **folder**: a folder copy buys
  the whole subtree in one call, and reproducing that here means walking the tree and emitting an
  upsert per post, the discovery-and-write loop Block H exists to remove, in the one case the
  server can do in one traversal. `to` is **the copy's own whole name chain**, exactly as it is for
  a move, so its last segment is what the copy is called, the console numbers a clone
  (`"archive (2)"`), which is right for somebody who can look at the result and wrong for a caller
  that must write the next op against the address. **Occupied is refused, never numbered**
  (`copy-target-occupied`, its own slug because a copy's fix is to pick another name while a move's
  is to clear the destination), and it **may cross sites** where `folder.move` refuses to: a copy
  detaches nothing, both sites go through the SH2 allow-list, and `cross-site-move`'s own fix used
  to send the caller round a per-post loop and now names this op. Not destructive, so no `confirm`,
  the same argument `folder.move` makes about being pushed back to the unsafe pair.
  **Every copied post lands as one DRAFT row, and §XVI.4's reason for that was wrong.** It said
  `copyPost` sets `published(false)`, it does, and it also reproduced each state row *as it found
  it*, so a copy of a live page came out `PUBLISHED` with the flag off. On the console that is a
  documented decision (`ShObjectCopyNamingTest` pins it: both rows, one name). On this surface it
  is a **hole in SH107's publish gate**, a credential without `mayPublish` could put a section live
  by copying it, which is SH201 by name, so `ShObjectWriteService#copyFolderAs` takes an
  `asDrafts` flag and the console keeps what it had. A post that exists *only* as `PUBLISHED`
  (SH38's uploaded asset, an exchange import) is copied **as a draft** rather than skipped: dropping
  it would make a folder copy silently lose content. The naming (SH202) and the cycle guard (SH203)
  are the shared writer's, so the two surfaces cannot disagree about them.
- `POST /agent/plan` / `POST /agent/apply`, a declarative desired-state content
  document; `plan` never writes, and `changed:0` is the content drift guard. `prune`
  deletes undeclared **posts and folders** inside the declared folders' subtrees (SH135),
  posts first and folders deepest-first; declared folders, their ancestors, any folder
  a declared post calls home, **and the source of every declared move (SH150)** are protected.
  Also `shio apply <file>` in the CLI (`--dry-run` / `--check` / `-` for stdin).
  **`createSite: true` makes the site the document names, as the run's first op (SH439)**: it
  compiles to `site.upsert`, which is what also fixes the *plan*: SH329's `withSitesFrom` reads the
  sites a run would create off the run's own ops, so a dry run into a new site resolves instead of
  404-ing. Opt-in like `prune`, because a document naming a site nobody made is usually a typo and
  the refusal that catches it is worth more than guessing (P7). Reached from `shio apply
  --create-site` and `shio convert --create-site`, one meaning in both. Deliberately **not** an MCP
  argument, for the reason `ShApplyTool` already gives about `blueprint`'s copy of the flag:
  `tools/list` is re-sent every turn (P3), and `shio_write` takes `site.upsert` for free.
  **`moves` is the document's one intent channel for a *post* (SH150)**, `{from, to?, folder?}`,
  compiled to `post.move` after folders and before upserts. **A folder's is `movedFrom` on its own
  `folders` entry (SH211)**, since a folder's name chain is its address and the destination is
  therefore the entry's own `path`, nothing is left for a second field to say. It compiles to
  `folder.move` **between two halves of the folder step**: plain upserts, then the moves, then the
  upserts of the folders that moved, because upserting a destination first *creates* it and
  `folder.move` refuses an occupied target. Its source is protected from `prune` for the same
  reason a post move's is and with a worse outcome, a folder delete cascades to every post
  beneath it, so an unprotected source puts "deleting a section" into the very plan SH108's
  confirm digest is taken over. Everything else here is state, which is what
  makes an apply re-runnable; a rename is *not* state, so declaring one as state alone is a
  delete plus a create that loses the post's id and history. A move naming neither `to` nor
  `folder` is a teaching **400**, not a noop, `changed:0` reads as "already in the right
  place", so a silent noop is how a caller comes to believe a rename happened.
  **A deletion plan is never shortened (SH138)**: `ShAgentChangeSet`'s constructor rejects
  `results.size() != ops`, so the type cannot express a cut, and the reason it gets this
  instead of the `truncated: true` every other unbounded section gets is that a shortened
  sitemap reads as incomplete while a shortened list of deletions reads as a *complete
  review of what is about to be destroyed*. Compiled ops therefore have their own ceiling,
  `shio.agent.batch.max-document-ops` (2000, vs `max-ops` 200 for a caller-written batch):
  a document's op count is not what the caller asked for, since a prune contributes one op
  per undeclared object. Which ceiling applies is a `Source` argument only
  `ShAgentDesiredStateService` passes, **never a field on `ShAgentBatch`**, or a caller
  could claim the document ceiling for a typed batch. Past it the run is refused
  (`document-too-large`) naming the deletion count and pointing at narrowing `folders`;
  do not "fix" that by re-adding the old `batch-too-large` advice, which tells the caller
  to split ops they never wrote.
  **And the *empty* case needed the same treatment, which it did not get until SH547**, an ops batch
  sent to `/agent/apply` answered `empty-batch` with `allowed` listing the eleven op names and
  `example` showing an `ops` array: every teaching field describing the other endpoint, and following
  it exactly reproduced the same 400. `Source` was already threaded into `validate` for the ceiling,
  so the fix is one branch: `empty-document`, whose `allowed` is the document's own keys and whose
  `example` is a document. Its `fix` also names `/agent/batch`, because `ops` is not a key here, so
  Jackson drops it and a misdirected batch arrives as a site with no folders and no posts, which means
  one refusal has to answer both readers in one round trip. **`ShAgentEmptyRefusalTest`'s third test is
  the one to keep**: it takes the `example` out of each refusal, posts it back, and requires a
  *different* problem, because P6 is not that a 4xx carries fields, it is that following them works,
  and a document naming the wrong grammar is worse than a bare status. A bare status sends the reader
  to the docs; this one sent them in a circle.
- `GET /agent/blueprints` · `GET /agent/blueprints/{name}` · `POST /agent/blueprints/{name}/apply`,
  **appliable starting points** (Block J, below). `dryRun` in the body returns the whole
  plan, post-types included, and writes nothing. Two opt-ins, both refused by default because
  each changes something the package does not own: **`createSite`** (SH151) makes the target site
  blank if it is absent, the plan names it before it happens, and "the site you named does not
  exist" is never read as permission, and **`mergePostTypes`** (SH152) adds the fields an
  existing post-type lacks, never changing, removing or reordering one and never touching a
  system type. Neither is an MCP argument: either one puts `shio_apply` over the SH132 per-tool
  ceiling, and `tools/list` is re-sent every turn (P3), so an MCP client creates the site with
  `shio_write`'s `site.upsert` and the missing-site error says so.
- `GET /agent/find`: posts by site/type/folder-subtree/text; **addresses, not bodies**. A post
  with no friendly URL is addressed **`id:`** (SH172), the fallback lives in
  `ShAddressResolver.addressOf`, so `find`, `changes` and the review queue cannot disagree about
  it, and the envelope's `notes` say the address is a fallback rather than a route.
  **A cut is a page, not a dead end (SH146):** a `truncated` response carries a `cursor`, the
  last address it returned, and `?after=<cursor>` continues the walk. **Keyset, never an
  offset**, because content is written while the walk runs and a post created ahead of an offset
  shifts every later page by one, dropping a row from a projection silently. The cursor is the
  address because the result is *already* sorted by address and an address is unique, so paging
  costs no second sort key. Three rules: the cursor is **absent on the last page** (that absence
  is how a caller knows the walk finished, instead of asking for an empty page); an `after` that
  is not a `post:`/`id:` address is a teaching **400**, since lexicographic comparison against a
  row number or a timestamp *succeeds* and returns a plausible page; and `truncated` **with no
  cursor** stays meaningful, it is the pre-SH146 dead end, which is why `shio pull --content`
  refuses on that and pages on the other. The client side is `agent-client.mjs`'
  **`findAll`**, which also stops on a cursor that did not advance rather than paging forever.
- `GET /agent/read?address=…&fields=…`: post content, DRAFT-preferred, projected. With **several**
  addresses a row that cannot resolve carries its own SH80 `error` and the rest still come back
  (SH171); the **single**-address form still 404s. The read path is
  `noRollbackFor = ShTeachingException.class` for that reason, catching a teaching exception
  inside a read transaction otherwise blows up at commit.
- **`include=digest` makes the next write conditional (SH721).** A curator patching one field while
  an agent ran `post.upsert` on the same post got `200`, the agent got `changed=1`, and the
  curator's field was gone, **with no signal to either party**. SH335 added the console `PATCH` for
  exactly that reason (*"reintroduces a lost update on a surface whose premise is several curators
  on one site"*) and the reasoning stopped at the console door. Neither side could prevent it,
  because nothing on the surface expressed *only if unchanged*.

  So a read can hand back `digest`, `ShAgentPostDigest`, over everything `post.upsert` declares:
  type, folder, address, locale and the **full** `data`, even when the read projected a subset,
  because an upsert overwrites all of it and a digest over the projection would promise only about
  the part the caller happened to look at. Deliberately **not** `modifiedDate`: it is a timestamp
  (SH200's precision argument), and it moves when a write changes nothing, which would refuse a
  caller whose expectation was correct. Deliberately not `shio_digest` or the SH194 appearance
  digest either, those are about a rendered *page*, so they move on a theme change and stay still
  when an unrendered field is rewritten. Both directions wrong.

  The write op then takes `expect`, and the refusal is `stale-expectation` (409). **The default
  stays last-write-wins**, which is what keeps the write surface cheap; what changed is that an
  agent that *cares* can say so. Three details are the design rather than the implementation:
  a **dry run refuses too** (P9, a plan that says `update` about a post somebody has since
  rewritten is a plan whose only use is to be trusted); an **absent** post fails the check, because
  absence is a different state and re-creating what somebody deleted is its own surprise; and the
  `fix` says *read again and compose a new write*, never *re-send with the fresh digest*, that
  instruction would perform the very overwrite `expect` was asked to prevent.

  It costs nothing to anyone who does not ask: `include=digest` rides the parameter `include=impact`
  already uses (SH262), so there is no new endpoint, no new tool schema and no new query string, and
  a read that does not ask is byte-identical to what it was.
- **A credential has a write ceiling across a window, if an operator sets one (SH734).** Two
  ceilings already existed and neither saw the case between them: `batch.maxOps` caps one batch, and
  SH22/D's quota caps a tenant's total, the agent write path has called `checkCanCreateSite` and
  `checkCanCreatePost` since it shipped. **An agent looping passes both**: every batch is small, the
  tenant total is generous, and ten thousand calls go through. SH108's confirm token stops a
  destructive op arriving as a typo; nothing stopped a cheap op arriving ten thousand times.

  `shio.agent.batch.max-session-ops` over `session-window`, keyed on the API token when there is one
  and the authenticated name otherwise, two agents sharing a login are two credentials and should
  have two budgets. **Off by default (`0`)**, the shape Turing indexing takes: a default-on limit
  would be either generous enough to be theatre or tight enough to break a legitimate bulk run, and
  there is no measurement of what a real replication spends.

  Three decisions worth naming. **A dry run costs nothing**, a plan writes nothing, and since the
  confirm token makes a destructive run plan *first*, charging would tax the safe path twice.
  **A refused run keeps its charge**, so a caller cannot sit at the ceiling and retry for ever; the
  window ends it, not restraint. And the refusal is a **429 naming the reset instant**, which is the
  only refusal on this surface whose answer is to wait rather than to edit, a 429 with no instant
  is an invitation to retry immediately, which is how a runaway becomes a busy loop.

  **This was SH110's for its whole life and did not need to be.** SH734 measured the blocker instead
  of trusting it: SH45's meter is genuinely required for the *storage* third, and the session and
  per-call thirds had already stopped waiting. SH110 keeps bytes.
- **And an agent can now empty the trash it fills (SH733).** SH732's reclassification named this
  while closing something else: an agent could undo a delete and could not make one final.
  `post.delete` and `folder.delete` trash; `site.delete` gained `data.purge` in SH648; the two ops
  an agent actually uses had no purge at all, and `DELETE /post-unified/{id}/purge` is
  console-shaped. Row growth is the boring half, **purge is what fires the `DELETE` lifecycle
  event**, so `ShStaticFileCleanupListener` never released a deleted asset's bytes and the SH12
  webhook never told anybody the post went, while the delete looked complete from every surface an
  agent can read.

  `data.purge:true` on both, the modifier SH413 established. **Not `post.purge`/`folder.purge`**,
  two names on `tools/list` for every caller forever, against two keys read only by a caller who
  declines the trash.

  **It cost 16 tokens and the ceiling moved, loudly.** The first attempt was 30 over, because all
  three deletes explained the same modifier; the grammar states it once now and the three sentences
  are gone. What remained would not compress without rewriting contracts SH170 and SH413 own, so
  `ShAgentOpsReachTest` went 680 → 720 with the re-examination its own message demands, including
  the surface choice re-run: 696 against a manifest of 2343 is **30%**, worse than the 24% SH647
  measured and the 19% that made SH346 reject the manifest, so the answer is unchanged and better
  supported than when it was made.
- **The trash is agent-reachable, and the instrument that said otherwise could not tell (SH732).**
  `ShAgentP1ConformanceTest` held `trash` as `CONSOLE_ONLY`, *"an agent can fill the trash and can
  neither list nor restore it"*, and both halves had been false for a block: SH222 gave
  `find?trash=true` and `post.restore`/`folder.restore`, SH648 added `site.restore`. The manifest's
  `elsewhere` map was still pointing agents at the console for something three ops do, which is
  worse than no pointer, because P2 makes that document the read a session performs first.

  **The staleness check was structurally blind to it.** It asked whether the entry's *named target*,
  `/agent/find`… no: `/agent/trash`, a route nobody built and nobody will, because the capability
  arrived as a shape over an existing read (P10). So an exception closed by a different mechanism
  than the one it named could never go green. `Delivery.closedBy` now names, in advance, the ops
  whose existence ends the exception, which makes each entry falsifiable instead of merely written.

  **And it needed a new category, which is SH596's finding repeating.** That task added
  `MCP_RESOURCE` because the vocabulary could not say "a resource delivers this", and wrote in the
  enum that *a classification can only be as honest as its categories allow*. `trash` is delivered
  by a parameter on a protocol route plus three ops, so it is neither a route nor a tool of its own:
  classifying it as `AGENT_ROUTE → /agent/find` made it claim a route the protocol owns flagless,
  and `ShAgentCapabilityJoinTest` refused, correctly, since a flag map that counts one capability
  twice reads as an endpoint count. `COMPOSED` carries **no target** and names its evidence instead.
- **`expect` covers the destructive ops too (SH730).** SH721 wired it into `post.upsert` because
  that is the op SH713 measured; the window is not specific to it, and `post.move`, `post.delete`
  and `post.publish` are the more destructive half, an upsert overwrites a field, a move changes
  the address a curator just corrected, a delete removes the post they are editing. All three now
  take it, and two of the three decisions are worth stating:

  **`post.publish` compares the DRAFT**, not the published row. Publishing is not a content change,
  so an expectation matched against what is live would refuse precisely the case publishing exists
  for, a draft that differs from it, which is every publish that does anything. The draft is also
  what an ordinary read returns, so the digest a caller already holds works without a second,
  state-scoped read.

  **`post.move` is checked before its already-moved branch.** That branch reports an idempotent
  `noop`, and if somebody else performed the move while the plan was running, that *is* the
  concurrent change `expect` was asked about, reporting it as a no-op would hide the one event the
  caller opted in to hear.

  **`post.unpublish` is deliberately not covered.** Its subject would be the published row, which
  the DRAFT-preferred read does not return, so a caller would need `state=published` and the op
  would mean something subtly different from its three neighbours. That is an argument to make, not
  to guess at.

  The vocabulary stayed under its 680-token ceiling by **trimming rather than raising**: four new
  key declarations took it to 681, and `post.upsert`'s own `expect` sentence was shortened to pay
  for them. `ShAgentOpsReachTest` says a raise is "a reason to re-open the surface choice, not to
  raise the number quietly", and SH647's decomposition says which half grows, 523 of the tokens
  are per-op prose and 104 are the keys, so prose is where a trim is found.
- **A clamped `?digest=<n>` says so (SH319)**: the cut row fires when the *pages* run out; nothing
  fired when the *request* was reduced, so `?digest=200` on a ceiling of 100 that affected three
  pages returned three rows and the caller could not tell that from three being all there was. One
  row naming both numbers, in the branch where the cut row is absent so the two never both claim the
  tail. Reported **once**, not per row (SH146), and **silent when nothing was narrowed**, a note on
  every response is the prose P3 says to stop paying for.
- **Every SH79 address a finding names must resolve (SH309)**: `ShAgentVerifyTest`'s
  `everyAddressInAMessageResolves` walks a full report, extracts anything address-shaped from
  `address`, `message` **and** `fix`, and puts it through `ShAddressResolver`. Every teaching message
  here is assembled by string concatenation and the only assertion any of them had was
  `contains("post:")`; SH172's `id:` fallback exists because an address was built in three places and
  one was wrong. A message naming an address that 404s is trusted **less** than one with no address.
  Two exclusions: a **placeholder** (`post:<site>/<url>`) teaches a shape rather than naming an
  object, and a rule that names something **absent on purpose** must be declared in
  `NAMES_SOMETHING_ABSENT` with the reason, one entry today, `no-home-page`, whose fix ends
  `"to":"post:<site>/"`, the page the finding exists because the site has not got.
- **`no-home-page`, a site that publishes and has no page at `/` (SH334)**, a `routes` **warning**
  carrying the `site:` address. The symptom was the Universal Editor opening on a 404 (it frames
  `ShSiteFrontEnd`'s answer, the site root), and the fix was deliberately *not* a page picker:
  **a page at furl `/` is a convention nothing enforced, defaulted or reported.** The SH71 starter
  and the fixtures have one, which is why it never surfaced; an agent following SH79 has no reason
  to create one. Warning, because a site addressed entirely from an external router is a design and
  not a defect. **Skipped when the site publishes nothing** (no front door is missing from a
  building with no rooms) and **skipped on a scoped run**, every other `routes` rule is about a
  subject the run looked at, and this one is about the site.
- `GET /agent/verify`: the content-side `tsc` (SH99/SH100).
  `checks=content,routes,page,a11y,diagnostics` (all by default), `site=`, `limit=`. **`ok` is false only for an *error***, a warning is
  something the CMS cannot prove from inside itself (an empty folder, a link that may be a
  front-end route), and a gate that failed on those is a gate people bypass. Every finding
  carries an SH79 address and a `fix`. `found` vs `findings` makes a cut visible (SH138's rule).
  **A `diagnostics` finding is always a `warning` (SH330)**: never an error, whatever the kind.
  SH144 made the incident record durable and nothing resolves one, so an *error* there made a
  single past 5xx fail `shio verify` **forever** on content that is provably clean. `ok` is a
  statement about what the CMS can prove from inside itself *now*, and an incident already
  happened; a caller that wants to gate on them reads `warnings`.
  **A `File` post's own `FILE` is a name, not an address (SH470).** `verify --site VigletOrg`
  reported **84 errors** on a replica whose every asset answers 200, all of them one rule on one
  field: `✗ dangling-reference · FILE, Field 'FILE' points at post
  inter-greek-wght-normal-CkhJZR-_.woff2, which does not exist.` It does not exist because it is not
  a post, the eighteen assets a capture uploads are eighteen `File` posts, each storing its own
  filename there, which is what `fileNameOf` has always read it as. **Two readers of one field, and
  the louder one was wrong.** A gate that cries wolf is worse than one that stays quiet, and this
  crossed the line where a reader stops looking; on the replication path it is worse than noise,
  since those checks exist so an agent can prove a replica *without* a person looking. The exemption is the
  **field**, not the widget: `File` stays in `REFERENCE_WIDGETS` because a user field, the seeded
  `Article.FILE`, or the `hero` the test asserts on, really does hold a file post's id, and it is
  the only path to `missing-asset`. Dropping the widget would silence 84 false errors by deleting a
  check that finds real ones. Both halves are asserted: the count on a fixture holding uploaded
  assets, and that `missing-asset` still fires.
  **And a `File` has no friendly URL by design, so `url-missing` does not fire on one (SH501).**
  Same family one rule along, same replica: three errors, "published but has no friendly URL, so no
  route reaches it", on `viglet-logo.png` and two fonts that all answered **200** on the same run.
  Bytes are reached at `/file_source/<id>/<name>`, and since SH437 at the path the source used;
  `id:` is a `File`'s correct address (SH172's fallback), not a symptom. **The remedy was worse than
  the finding**: it handed over a `post.move` to "give it one", which would put a furl on the type
  defined by not having one, so an agent that trusted the error would corrupt the content model to
  clear it. The exemption is keyed on the **post type**, so a content post with no furl is still an
  error with the same `post.move` fix, silencing the rule would have cleared three false errors by
  deleting the check that finds real ones, which is SH470's mistake not made twice. The assertion is
  the one that keeps catching this family: **a replica whose every reference answers 200 verifies
  clean**, errors are counted, not one rule's absence. `find`'s own `id:` note (SH172) makes the
  same exemption, since it recommended that same `post.move`.
  **`folder=` / `address=` / `since=` scope the run** (SH143): a subtree, comma-separated
  `post:` addresses, or an SH88 cursor; they union rather than intersect. A scoped run sets
  `partial` and describes itself in `scope` (both **absent** on a full pass), because `ok` over a
  subset is not a statement about the instance. What scoping narrows is the **subjects**; the
  cross-cutting indexes, who else claims this URL, does this link land, does this folder hold
  anything, stay whole, built from id/url projections. Narrowing them would invent a
  `link-unresolved` for every link out of the scope and miss the duplicate whose twin is
  outside. A `folder=` that is not a folder is a teaching **400**: a scope that covered nothing
  while answering `ok` is the worst false green there is.
  **`checks=page` reads the page the reader gets, which is the gap every other group shares
  (SH516)** (`verify --site VILT` answered **ok) 0 errors, 17 warnings** over a page with **38
  broken links of 39** and **43 broken references**; it had found 12 of the 38 and none of the 43.
  Not a missing rule: every check resolves a reference against the **content graph**, does this
  furl exist, is that `File` post addressable, does the layout name a theme, and all of it was
  true, while the page came out served four segments deep so its relative strings resolved
  elsewhere (SH512) and its query-stringed paths missed (SH515). *The content was self-consistent
  and the page did not work.* Six defects survived a green gate in one session (SH455, SH461,
  SH462, SH512, SH515 and the two that produced this), all six in that gap, each found by hand,
  and the hand was wrong once, reporting *0 broken* on the page with the missing logo because it
  filtered references on a leading slash instead of resolving them. So the group **renders each
  published page and resolves every reference on it against that page's own URL**:
  `page-link-broken` for a path the site publishes nothing at, `page-reference-broken` for bytes it
  does not serve, `page-unrendered` for a published page that does not compose, **errors**, not
  `link-unresolved`'s warning, because the route table judged against belongs to the process that
  will serve the request (`ShFrontEndRoutes` stays the exemption, for a site somebody else
  delivers). Two things make it cheap and honest: it **composes the page in-process** rather than
  fetching, `/sites/**` is that same render plus `ShPreviewLinks`, so it is the same bytes with no
  network, no `allow-fetch` and no request-forgery surface, which is why it is in the default set,
  and it reads references through **`ShPreviewLinks.referencesIn`, the same walk the rewriter
  uses**. That last one is the whole guarantee: a check with a list of its own would go green on
  the one attribute the rewriter reads and it does not. Subjects are the post-types the site binds
  a layout for (`postTypeLayout` *is* the definition of "is a page"), bounded at 50 pages, one
  finding per distinct path per page, and both the cut and the count go in `notes`.
  **And the group judges the artefact on two reads that already held the finding (SH521).**
  `verify` answered **ok** on a page that had lost its hero, its grid and its footer while
  `GET /agent/render` answered `links: 0, images: 0`, the intact version answers `links: 11,
  images: 1`, and listed nine classes the page draws with and nothing styles. *Shio knew.* Not a
  missing fact: a fact nobody joined to a judgement, `unstyledClasses` being a finding written as a
  list and `links: 0` one written as a number. Both are computed from the bytes the group has just
  rendered, so each is a **threshold, not an analysis**. `page-empty` (error) is four conditions at
  once (no heading, no link, no image and under 20 words) because any one alone is a page somebody
  may have meant (a landing page that is one image, a notice that is one block of prose) and all
  four together is the SH520 symptom. `page-unstyled` (warning) reports `unstyledClasses` **only
  when the page loads no external stylesheet**: `ShHtmlAppearance` keeps that list advisory for a
  right reason, the 2018 `bootstrap-site` package draws with CDN classes this instance has never
  seen, and the guard is what makes the same list a finding without inheriting the false positives,
  since the document's own CSS is then the whole of what styles it. It also retires the join that
  was being rebuilt in Python every round and got wrong twice.
  **`page-dangling-token` (warning) is the same question on the half of a stylesheet that is
  actually used (SH851)**, SH850 took a replica's dangling `var()` references from **231 to 958**
  and every instrument passed it: `verify` said `0 error(s), 2 warning(s)` on both rounds, `diff`
  reported the same 18 losses, `judge` found nothing new, `audit` rendered the page and returned no
  error. A person looking at it is what noticed. The gap is exact, `appearance` counted **class
  names**, and that page made **3,005** `var()` calls against **205** classes. So the digest now
  carries `danglingTokens`, `danglingTokenRefs` and `definedTokens` beside `unstyledClasses`, and
  `ShHtmlAppearance.tokensIn` computes them the same cheap way: declared set minus referenced set,
  off the CSS the page already carries, no rendering and no browser. Three decisions, each in the
  direction that under-reports, a **fallback** (`var(--x, 1rem)`) is a declaration the author made
  at the call site, **comments are skipped** (over-collecting a declaration shortens a finding list,
  over-collecting a reference invents one), and only what the page carries is read. Same guard as
  `page-unstyled` and it matters more here: a design-token framework is *precisely* a sheet that
  declares custom properties, so an external stylesheet makes the answer unknowable. The counts are
  in the **appearance hash** for the reason `inlineStyles` is, 231 references becoming 958 is not
  "six inline styles became seven", it is most of a page's colour and spacing going out, and the
  regression that motivated this moved no hash anywhere.
  **`page-image-empty` (error) is an image whose source rendered empty (SH1266)**: SH1265 put
  `src=""` on every card of a site and the group stayed green, because the reference walk skips an
  empty attribute (it is not a reference) and `a11y` reads `alt`, which was there.
  `ShHtmlOutline.emptySources` reports an `<img>` whose `src` is present and blank with no usable
  `srcset`, an empty `<source>` and an empty `video poster`, one finding per page naming the `alt`.
  Present-and-empty only, and through a stricter attribute read than `attribute`: `\b` matches
  after a hyphen, so the shared helper reads a lazy loader's `data-src`, and `<img data-src>` is a
  working image.
  **`checks=delivery` proves a route instead of resolving it (SH141)**: the one group that
  leaves the process. It fetches each published page and each unresolved link from the site's own
  URL through `ShAgentDeliveryProbe`, the single guarded fetch path SH101 built, and turns "the
  front end does not serve this" into `route-not-served` / `link-broken` **errors**. It is in the
  default set **only when `shio.agent.render.allow-fetch` is on**, naming it otherwise is a
  teaching 400, because a check that silently covered nothing while answering `ok` is the same
  false green an unresolvable `folder=` would be. Three rules worth keeping: an unreachable front
  end is **one** `delivery-unreachable` per site and ends that site's pass (fifty copies of "it is
  down" is a report nobody reads, and its other pages are *unknowable*, not broken); a site with
  no URL is a **warning** when the group arrived by default and a **400** when the caller named it
  (a group that joined on its own must not fail a call that never asked); and the pass is bounded
  by `shio.agent.verify.max-fetches` + `fetch-budget-millis`, with every cut in `notes`.
  **And `all` is a token, because `--delivery` was subtracting (SH1120).** `shio verify --delivery`
  ran *fewer* checks than `shio verify`: the CLI composed the set itself as the literal
  `content,routes,diagnostics`, a hand-written copy of `ShAgentCheck.defaults` that had been missing
  `page` since SH516 and `a11y` since it shipped. With fetching on it was worse than a narrowing, it
  traded two groups for one **already in the default set**, so the flag's only real effect there is
  to make the proof *required* rather than best-effort. The comment above that list argued, correctly,
  that sending `delivery` alone would drop the network-free rules, and then did the same thing by a
  longer route. The shape is what to keep: **a client-side copy of a server-side default rots
  silently**, because nothing on that side knows when a group is added, and a ninth would have been
  missed too. Neither existing spelling could express the request, a bare `all` resolves to the
  defaults, which drop `delivery` where fetching is off and take the teaching 400 with it, and
  `delivery` alone switches the content rules off. So `parse` now takes `all` **inside** a list, the
  way a context section has since SH157, and the CLI sends `all,delivery`: every group that can run,
  plus delivery named. The asymmetry is deliberate and asserted, a bare `all` asks *what is
  possible*, naming a group asks *for that group*, so `all,delivery` with fetching off still 400s.
  `all` is deliberately not in `names()`, which is what lets the CLI name no group at all, and
  `SH1120: no CLI module composes the server's check set` holds that: a literal whose
  comma-separated tokens are all check names is the defect's shape, and `all` is legal there
  precisely because it is not one of them.
  **The fetches happen outside the read transaction (SH206).** They used to run inside it, holding a
  pooled connection for the whole network pass, bounded three ways, which made it *safe* rather than
  right: a slow front end occupied a connection for fifteen seconds doing no database work. `verify()`
  is now three phases, **read** (every database access, including the delivery *gather*), **fetch**,
  **assemble**: and the transaction spans only the first. Two consequences to keep. The read pass
  returns a `Plan` holding **no entity**, because `Scan` is full of `ShPostUnified`/`ShSite` that
  detach at commit and a slow front end would otherwise become a `LazyInitializationException`. And
  it is a **`TransactionTemplate`, not a second bean**: §IX.7 proposed splitting the class because
  `verify()` calling its own annotated method is a self-invocation that never crosses the proxy, and
  a programmatic boundary sidesteps that *and* fixes the case a bean split would not have,
  `ShAgentProofService` calls `verify()` from inside its own transaction, so on that path the fetches
  were never in a transaction this class opened. The bounds stay: a verification call that takes four
  minutes is a hang whether or not it holds a connection. `ShAgentVerifyTransactionTest` asserts it on
  the *calling* thread, `TransactionSynchronizationManager` is thread-local, so a loopback server's
  handler would have reported "no transaction" no matter what.
  **Coverage is a note, and marker counting is settled as impossible here (SH302).** A clean pass
  emitted nothing at all, so forty proved pages and zero proved pages read identically, it now says
  how many it proved per site. Counting SH299's defect markers in the delivered body would be a
  stronger claim and **cannot work**: the pass fetches a site only when it has its own front-end URL,
  so every body it could parse is somebody else's render from the CDA and carries no `data-shio-*`.
  Asserted, not remembered, `ShAgentDeliveryTest` pins that Shio's own `/preview` is never a delivery
  target. (Switching to the body-bearing fetch would also have cost no extra network: `status()`
  already discards a body it has transferred. Cheapness was never the question.)
  When delivery runs, `routes` **hands it the unresolved links instead of warning**, the same
  defect reported once as a maybe and once as a fact is a report the reader has to de-duplicate.
  The legitimate exception is **declared, never guessed**: `ShFrontEndRoutes` reads glob patterns
  from the SH114 memory key **`frontend-routes`** (`*` within a segment, `**` across; scoped to a
  site or instance-wide), and a declaration **silences a guess but never a proof**, with fetching
  off the link is not reported and the count goes in `notes`; with fetching on it is still
  requested, and a declared route the site 404s is an error, because the declaration made a claim.
  A pattern that is not a path is dropped rather than compiled, since the dangerous failure mode
  here is one that matches everything.
- `GET /agent/render?address=…`: a page's **structural** digest (SH101): heading outline,
  landmark counts, links, images with alt, word count, plus a `digest` hash over the
  structure only (a typo fix does not move it; a lost heading does).
  **Since SH249 it reads the *rendered* page** (`source: rendered`) whenever the post-type has an
  SH183 layout binding, the artefact a visitor gets, and the only one that reflects regions *and*
  sections at once. Before that, this endpoint composed sections (SH169) while `/preview` composed
  regions, and neither knew the other existed, so a structural proof and a person looking at the
  page could describe two different compositions. The decision behind it is a document,
  [docs/specs/SH249-sections-and-regions.md](docs/specs/SH249-sections-and-regions.md): **two
  mechanisms, one composition path.** A region is named by a layout, by title, and varies with the
  post-*type*; a section is named by a page, by URL, in order, and varies with the *page*; they meet
  because a region's template iterates the field (`{{#getRelation SECTIONS}}`), so **sections are
  content a template reads, not a level of the render model.** SH169's composition survives as the
  **labelled** fallback (`source: content`) for a post that cannot be rendered, no binding, or no
  friendly URL (SH172's `id:` case), because an unbound type is the normal state of a site
  mid-build and a digest that failed there would break the SH99 loop; `source` is in the response so
  a fallback is never mistaken for the real answer. Every render refusal is a fallback and **never**
  an error, and the catch is narrowed to `ShTeachingException`. `ShPageRenderService.render` is
  `noRollbackFor = ShTeachingException.class` for SH171's reason, catching a teaching exception from
  an inner `@Transactional` otherwise marks the caller's transaction rollback-only and blows up at
  *commit*, nowhere near the catch (it cost eleven unrelated test errors when SH249 landed).
  **A rendered digest also carries `appearance` and a second hash, `appearanceDigest` (SH187)**,
  the class inventory, the classes no stylesheet the page carries defines, inline `style=` counts,
  images with no declared size, and the external sheets that explain the rest. It is **not** a
  rendering: computed style and box geometry need a layout engine, and that limit is the design
  rather than a gap (the raster half is **SH265**). The unstyled-class list is **advisory, never a
  lint finding**, the 2018 `bootstrap-site` package loads Bootstrap from a CDN, so a rule reporting
  every class the `Theme` lacks would fire dozens of times on the one real example in the repo.
  **Two hashes on purpose**: `d:` covers structure and `a:` covers appearance, because one hash over
  both could not tell "I restyled it" from "I broke the outline", the comparison an iterative loop
  needs, and the half of SH194 that is now done. Both are **absent** for a `source: content`
  fallback (a markup field is not a document and has no stylesheet). The CSS is read out of the
  rendered page's own `<style>` blocks, not from the `Theme` post, so it is whatever the page
  actually carries. The manifest reports `appearanceDigest: true` and keeps
  **`visualSnapshot: false`**, the only honest pair, there is still no raster here, and an agent
  reading `true` would hunt for a PNG. `?url=` digests the
  delivered page but is **off** unless `shio.agent.render.allow-fetch=true`, and only for
  the origins of readable sites, an endpoint that fetches arbitrary URLs server-side is a
  request-forgery surface.
- `GET /agent/changes`: **the change feed (SH88)**: content transitions with an SH79
  address each, and a `cursor` to pass back as `since=`. The cursor is `<instant>|<id>`,
  never a bare timestamp, audit rows share timestamps, so a timestamp cursor would drop
  the entries that tie (a bare one is a teaching 400, not an interpretation). Both modes
  are ordered **oldest → newest** so the cursor is always the last row.
  **`limit` is a row cap, and the bound that actually holds is `?budget=` (SH723).** A row's
  cost is not fixed, SH714 measured a change row at 38–44 estimated tokens against a `find`
  row's 20–23, so `limit=500`, the documented maximum, was ~20,000 estimated tokens: the
  largest payload on the surface, and the only one with no ceiling, while the context pack
  self-limits at 4,000. The feed now charges each row against `ShTokenBudget` before committing
  it, defaulting to `shio.agent.changes.max-budget-tokens` (6,000, the copy in
  `token-budgets.properties` being the reviewable one), with `?budget=0` to opt out and a
  negative value a teaching 400.
  **Which end is cut differs by mode, and that is the whole design.** With `since=` the page is
  a delta, so the budget drops the *newest* rows: the cursor stops at the last row that fit and
  `more` is true, SH88's resumable shape, nothing lost. Without it the page is a window ("the
  most recent N"), so the budget drops the *oldest* rows and the cursor stays at the true edge
  of the feed. Either way `budget.truncated` and a note say so, because a cut that is not
  visible reads as a clean answer (SH138). **At least one row always survives**: a page that fit
  nothing would leave the cursor where it was with `more` true, and the caller would poll the
  same position for ever.
  **The ceiling is declared on the feed's own terse header (`max-tokens=`), not in the
  manifest's `limits`.** `maxDigestPages` is in `limits` because it appears in no response;
  this one appears in every one of its own, so a caller learns it on the first read instead of
  by being cut off, and it is paid once per feed read rather than once per session. The
  context pack made the same call for the same reason. `budget.est` is the *terse rendering*
  measured, which is exactly `header + events + incidents + notes`; `ShAgentChangesTest`
  asserts that identity, so a budget that charges something the caller never receives fails the
  build rather than drifting.
  **The feed is commit-ordered, and the guard is a watermark rather than a lag (SH147).**
  SH51 stamps the audit row inside the writer's transaction, so timestamps are a *stamp* order:
  a transaction that stamped earlier can commit after a faster one has been read, and a cursor
  past the faster one never sees it, a **lost edit** on the one surface that exists to be the
  delta. `ShAuditFeedWatermark` closes it with two facts instead of a duration. **Positions are
  reserved, not read off the clock** (`reserve()`, monotonic and unique, so ties vanish and the
  SH88 keyset tie-break becomes belt-and-braces), and **every reserve inside a transaction is
  registered as in flight** and released when it completes either way, so the ceiling is the
  *oldest unfinished write*, exclusive, and a row is reportable only once everything stamped
  before it has finished. `lag-millis` survives at **0**, meaning only what an in-process
  registry cannot see: a second app node against the same database, **and the feed says so when it
  meets one (SH376)**. Positions are monotonic and never below the clock, so a row stamped above the
  highest position this JVM has issued cannot have come from it, which is proof of another writer
  and needs no column, no heartbeat and no second registry. The disclosure is a `notes` line, said
  only once such a row is seen and only while `lag-millis` is 0, so a single instance pays no prose
  for a case it does not have (P3). Three things settled while building it: the observation is
  **sticky**, because an installation does not stop having two nodes when the next page happens not
  to contain a foreign row and a note that flickered would stop this endpoint revalidating (SH159);
  it is **skipped until this JVM has issued a position**, or every single-node instance would report
  a second writer for one read after each restart; and it reports **the fact and deliberately not a
  number**, the obvious "set the lag to the excess we saw" is wrong, because the excess measures
  how long ago *this* node last wrote rather than how far ahead the other one's clock runs, so an
  idle instance would advise a ten-minute lag. It is also **retrospective**, which is the honest
  limit: the proof arrives with the row, so this converts a permanent silent weakness into a
  reported one rather than into a guarantee. Four things worth knowing
  before touching it. **A `seq` column with gap detection (§X.3.1's shape) does not close the
  hole**: a gap is either a live transaction or a rolled-back one, nothing in the row says
  which, so the reader skips after a timeout and a slower transaction loses its edit exactly as
  the lag did; the registry has the fact the database does not. **The hold is per row, not per
  transaction**: holding only the transaction's first position is arithmetically enough and needs
  a thread-bound `TransactionSynchronizationManager` resource to record it, which leaked both
  ways in one suite, a stale binding makes later reserves skip their hold (feed reports a write
  in flight) *and* strands the hold it came with (feed reports nothing, ever). **The exclusive
  margin is a millisecond, not a nanosecond**, because it is compared against a value that has
  been through a timestamp column: microsecond storage hands back a slightly smaller instant and
  `oldest - 1ns` re-admits the row it exists to hold. Reserved positions are microsecond-aligned
  for the same reason. **And the idle ceiling is `max(now, lastReserved)`, never `now`**, a burst
  reserves faster than the clock ticks, so positions run ahead of it, and capping at `now` hid
  every row a fixture had just written. Site-scoped by the SH2 allow-list. **A row carries `subject: "folder"` when the change is structural
  (SH196)**, a section created, renamed, moved, deleted or restored, one row per write and
  never one per post beneath it. Emitted only when it is `folder`, so every post row is the
  bytes it always was (P3); absent means `post`. It is not a shorter spelling of the
  `folder:` address prefix: a row whose object is gone has no address at all, which is the
  normal state of a `DELETE` and the one row where knowing *what* was destroyed matters most.
  A rename arrives as an `UPDATE` with the previous name in `what`.
- `GET /agent/diagnostics`: asynchronous failures (SH103): webhook deliveries, image
  transforms, scheduled publishes, 5xx. **A row carries `frames`, where in Shio it threw (SH190)**:
  `Class.method:line`, innermost first, **at most three** and **only `com.viglet.shio`** frames, no
  arguments and no locals, Spring proxy frames skipped. The old `fix` ended "the instance log has the
  stack trace (the body never will)", which named a file the caller cannot open, an error whose
  remedy is out of the caller's reach is an error that has not been reported. **The exclusions are
  also the security argument** (SH190, kept here by SH356): arguments are *content*, and content in a
  diagnostics feed is how a log leaks what the ACL exists to protect, which is why the trimming is
  the design rather than an optimisation, and what makes a frame cheap enough to attach to every
  incident. **And the absence of a frame is itself signal (SH269, kept by SH356)**: four of the five
  recorders pass one, and the webhook's give-up branch does not, because delivery completed and the
  *subscriber* rejected it, there is no exception of ours and nothing in Shio to point at. SH190 set
  that as a technicality about honesty; the field's presence now distinguishes "we failed" from "they
  refused". A **log-tailing tool
  was rejected** for SH103's three reasons: it hands over every request's data to answer a question
  about one, it is a per-deployment path, and it cannot be scoped by the SH2 allow-list or the SH54
  ACL. `frames` is **absent, not empty**, when there is nothing of ours in the trace (a request
  Tomcat rejected, or a 5xx the framework rendered with no exception reaching the filter). It is
  **persisted** (`v2026.3.15`), because SH144's promise is that the durable answer is the good one,
  and a repeat arriving without a frame **cannot erase** the first one's, the rule `fix` already
  followed. Since **SH269** every recorder inside a `catch` passes one, webhook dispatch errors,
  scheduled publish/unpublish, image transforms, and the webhook's *give-up* branch deliberately
  does **not**, because delivery completed and the subscriber refused it, so the presence of the
  field now distinguishes "we failed" from "they refused". Guarded by a **source lint** in
  `ShAgentIncidentFramesTest` (a `record(...)` inside a `catch` that passes no frame fails the
  build), because the defect is an omission at a call site and reproducing three async failures
  would be three fixtures asserting one line each. **A `catch` that records nothing is a declared
  population now (SH270)**, `ShSilentCatchInventoryTest` asserts the count per package (63,
  measured; it was 65), so adding one is a diff that asks the question instead of a habit. The two it
  removed were the content degradations whose symptom is *silence*: an unparseable `postTypeLayout`
  (every page then refuses with a message about the post-type) and a post whose `data` is not an
  object (the page renders with none of its fields). Both now record the fifth kind, **`content`**,
  the other four are operational ("something we were doing failed"), this one is "the content is
  wrong and the page is quietly incomplete", and it stays narrow because recording every swallowed
  exception is what SH103 refused for 4xx. Recorded by `ShAgentIncidentLog`; repeats fold into one
  row with a count. **Durable since SH144** (`sh_agent_incident`, written by
  `ShAgentIncidentStore`), so "was anything failing while I was away" is answerable and an empty
  report now means *nothing has failed* rather than *nothing is remembered*, `since` is where
  the record starts (**absent** when empty) and `dropped` changed meaning to *could not be
  written down*, the only way one is lost now. Three rules to keep: the store writes in
  **`REQUIRES_NEW`** (several callers are inside a transaction that is about to roll back, and an
  enlisted row would be destroyed by the very failure it records) and **never throws** (a
  diagnostics log that turned a webhook timeout into a 500 would be worse than none, a failed
  write is counted and reported instead); a row is keyed by **what went wrong**, not by each
  occurrence, which is what makes the table grow with the number of *problems*; and the feed
  position is **`lastSeen`**, so an incident that is still happening is re-delivered with a
  higher count rather than being reported once, ever.
  **The key is a `signature`, not the message (SH205).** The message is prose for a reader, "gave
  up after 3 attempt(s), last status 500", so the same webhook answering 503 next publish was a
  *different row*, and folding failed on a **flapping target**, which is the highest-volume case
  and the one whose every occurrence differs. The near-duplicates then evicted unrelated incidents
  through the cap, invisibly: several near-identical rows is what a report would look like if there
  really were several problems. `ShAgentIncidentSignature` collapses runs of digits, every varying
  part of every message here is a number (attempt count, status code, Jackson's `line: 1, column: 5`,
  a timeout, a port), including inside exception text nobody could enumerate. The standing objection
  to a scrubber is that over-folding hides a failure and nothing shows it; the split answers it
  rather than ignoring it, **`message` is overwritten with the most recent occurrence**, so a wrong
  fold is one row carrying the newest text, not one row that swallowed the other's. Case is kept
  deliberately, and the collision would have to survive an identical kind, subject *and* site.
  Note the asymmetry with `fix`/`frames`, which keep the **first** value: a repeat arriving without
  them must not erase what the first one knew, while a reader asking "what is it doing now" wants
  the current status code. Both halves asserted by `ShAgentIncidentSignatureTest`.
  **`REQUIRES_NEW` was on an overload nobody called, for two blocks (SH358).** `record` was a
  pair, a five-argument method carrying the annotation and delegating to a six-argument one
  carrying nothing, and the only caller took the second, so every incident this product ever
  wrote had *no* demarcation and joined whatever transaction was about to roll back. Nothing
  can see that by reading: the annotation is present, spelled right, on a method called
  `record`, with the javadoc explaining it three lines up. Only *which overload does the caller
  resolve to* finds it. The rule is now all-or-nothing per method name, guarded by
  **`ShAgentTransactionalOverloadLintTest`** over `src/main/java`, public and protected only,
  since a private overload can never be entered through the proxy, and the hatch is
  `@Transactional-overload-exempt:` for the one honest case (delegating through an injected
  self-reference, as `ShStaticFileService.listFiles()` does). It found two more on its first
  run: `ShPageRenderService.render` (every `/preview/**` with a query string lost the
  `noRollbackFor` that keeps a caught teaching 4xx from blowing up at commit) and
  `ShAgentProofService.attach` (SH193's graph walks LAZY associations, so the two-argument
  overload read them with no session open). And **one diagnostics response is one snapshot
  now (SH358)**, the count, the window and `earliest` were three read-only transactions, which
  is the only way the intermittent `{}`-beside-a-populated-`since` could be produced.
  **The flake was `counts`, and it was never a race (SH357).** They were summed over the *page*,
  while this payload's contract has always been that a cap **narrows the detail and never the
  diagnosis**. `default-limit` is 25 against `max-rows` 500, so the 26th-newest kind simply
  vanished, and *which* kinds survived depended on what else happened to be newer, which on a
  shared test context moves with run order. That is what "fails once in fifteen full runs and
  never reproduces" was. Counts now come from an aggregate over the whole table
  (`countsByKind`), one row per kind however large it gets, and `ShAgentDiagnosticsCeilingTest`
  fails on the old code every time. **The lesson worth keeping is about the search, not the
  bug:** two nanosecond-timing hypotheses were eliminated by 600 rounds that reproduced nothing,
  and the cause was a documented contract the code had stopped keeping, order-dependence reads
  exactly like a race from the outside, and is cheap to test for. The same pass removed the
  feed's `ceiling` from this read on its own merits: `/agent/changes` has a ceiling of its own
  (SH147's watermark) so a **cursor** cannot skip a late commit, and diagnostics resumes nothing, so the argument
  could only ever hide a row somebody had just recorded.
  **Incidents also ride `/agent/changes` (SH144)** in their own `incidents` array, so one
  `since=` answers what changed *and* what broke. Not a `Change` with an invented `action`,
  every existing caller branches on `action` and would file a failure as content. The
  load-bearing rule: **an incident may repeat, a change may never be lost.** Two streams paged
  under one scalar cursor cannot both be exactly-once, and taking the *later* of the two
  positions (the obvious move) silently advances past unread audit rows whenever an incident is
  ahead of the page being walked. So the cursor advances past an incident only when the change
  stream reports no `more`; until then it stays the audit's and those incidents are read again,
  which costs nothing because rows are folded.
  **An incident names its site now, and `site=` narrows them (SH204).** SH144 shipped a note saying
  it did not, which was honest about a hole rather than a property: four of the six recorders can
  attribute a failure and now do, the webhook dispatcher from the event it is delivering, both
  render paths from the page's own site, the Turing listener from the published post. `api-error`
  and `transform` **cannot ever**, and that is the answer rather than a gap: a 5xx belongs to a
  *request* and a failed transform to a *file* several sites may reference. So an absent `site`
  means instance-level, not unrecorded. Two rules make it work. **The site is in the fold key**,
  one webhook URL that two sites subscribe, failing identically, is two problems with two
  operators, and folding them keeps whichever failed first and shows one operator the other's
  failure. And **the scoping rule is the audit trail's, exactly**: an unattributed row reaches an
  unrestricted read and no scoped one, which falls out of `null in (…)` rather than being written,
  the two streams are merged under one cursor, so a row one hides and the other shows would make a
  single `since=` mean two things. A scoped read says where the instance-level failures went
  (`/agent/diagnostics`, still instance-wide), because an agent's own 5xx is the incident it most
  wants and the last one it would look elsewhere for. Asserted by `ShAgentIncidentSiteTest`.
  **And an incident's position comes from the same watermark as an audit row's (SH147).**
  `ShAgentIncidentStore` kept a monotonic counter of its own (SH372, so the cap would stop
  choosing which row dies by random uuid) and that is half the mechanism, the other half is being
  held back from the feed until the writing transaction completes. **One cursor cannot be
  commit-ordered on only one of its two streams**: once the lag went, the window cursor sat at the
  edge of the feed and an incident stamped from an unsynchronised clock could commit *behind* a
  cursor a reader already held. It failed once in two full suite runs, which is the shape of every
  bug in this paragraph.
- `GET/PUT/DELETE /agent/memory`: **the instance memory (SH114)**: keyed notes that come
  back in the context pack's `conventions`, attributed to whoever wrote them. Upsert by
  `(tenant, scope, key)`, so re-stating a convention rewrites one row; `scope` is a site name
  or `""` for the whole instance (empty, never `NULL`, a unique index does not treat `NULL`s
  as equal). Deliberately **not** content: read on the hot path, never delivered, and it must
  survive an SH82 `prune`. Written three ways: the **`shio_remember` MCP tool** (SH160),
  `shio remember`, or the endpoint. SH114 argued against the tool and was right about Claude
  Code (a schema is paid every turn and a coding agent has a shell) and wrong about a pure
  MCP client, which could read the memory and never write one; P1 wins because the memory is
  the only mechanism that makes session N+1 cheaper than N, and a store nobody can write to
  stays empty. **A site's notes travel with it (SH161)**: the SH68 package carries them and
  re-scopes them on import (a note names its site by name, and an import renames the site),
  and `shio pull/push --content` projects them to `shio/content/<site>/.memory.yaml` with the
  same three-way merge the content tree uses. Only the *site-scoped* notes, an instance-wide
  note describes the installation, and importing another instance's house rules is a P7
  surprise (SH231 revisits that for a whole-instance move). Two rules on the file side: no
  file at all means "never projected", never "forget everything", so a first push from a
  fresh clone cannot wipe a site's memory; and notes are applied **outside** the desired-state
  document, because a note is not content and `--prune` must never reach it.
  **And absence is a conclusion both sides can reach (SH412).** `reconcileNotes` treats identical
  edits as agreement ("the same conclusion reached twice") but its test required both sides to
  *hold* the note, which excluded the one case where they agreed by **deleting**. SH160 makes
  absence the representation of a forgotten note, so two sides forgetting one left both values
  null, both sides "moved", and the key in `conflicts`. The pull skips a site with any conflict, so
  **none** of its notes were written; and because a conflicted key never reaches `agreed`, the base
  kept it and every later run recomputed the same conflict from the same inputs. It did not decay,
  and the remedy a reader would try (forget it on both sides) was what caused it. Comparing both
  sides *including* their absence fixes it, and recording `agreed` only for a value is what makes
  the resolution stick: the key leaves the base instead of recurring. Found by probing the notes
  merge on the four axes the post merge had already passed, it agreed on three and differed on the
  one where agreement is silent.

**Byte stability (SH136).** `Map.copyOf`/`Set.copyOf` iterate in an order derived from a salt
set **once per JVM**, so they are a silent re-ordering wearing a defensive copy's clothes: the
same data serializes to different bytes after a restart, and any hash over that iteration moves
with it. Use **`ShStableMap.copyOf`** wherever the order is observable, serialized, hashed or
rendered, and `ShByteStabilityLintTest` fails the build otherwise (`byte-stable-exempt:
<reason>` is the hatch, for a keyed lookup that is never iterated). Two rules that came out of
it: **a hash sorts its own inputs** rather than trusting the caller (SH101's digest does now),
and **you cannot catch this class with a behavioural test**, twice-in-one-process comparison
passes over broken code, which is why the guard is a lint and why the digest was restructured
until order-independence became assertable.

**SH197 finished the class, and the open question is settled by counting.** SH136 left the
question of whether to ban a hash-ordered `Map.of` too, either everywhere in the model or only
where it reaches a serialized component. **Neither**, and the population is the argument: `main`
holds 112 `Map.of`/`Set.of` sites, of which **48 take no arguments and 21 hold one entry**, those
have no order to lose. Of the 43 multi-entry ones, **27 are MCP schema builders** whose
serialization point is already proved key-sorted by `mcpPayloadsAreCanonicalized`, and the rest
are JSON response bodies, where object key order is not observable to a parser. So a
ban-the-construction rule would need ~40 exemptions to restate what two other rules already say,
and a guard that noisy is one somebody deletes. The invariant is **not** "never build a
hash-ordered map", it is *no hash-ordered iteration reaches bytes that get compared*, guarded
where the bytes are made.

What SH197 *did* add is the one shape nothing else can catch: **`ShStableMap.copyOf` may never be
handed a `Map.of`/`Set.of` literal.** The helper keeps the order its source had, so a literal with
two or more entries freezes a per-JVM salt behind the name of the fix, worse than the raw defect,
because the next reader sees `ShStableMap` and stops looking. It was live in `ShAgentOps.SPECS`
(ten entries), where `all()` was safe because it projects the source-ordered `OPS` list but
`specified()` returned `keySet()`; it is a `LinkedHashMap` keyed off each spec's own `op` now,
which also removed the op name being typed twice per entry. A literal with one entry or none is
banned by the same rule for the same reading reason, not because it can move.
Two smaller repairs in the same pass: the canonical rule **walks the whole `mcp` package** instead
of naming `ShMcpServer.java` (a rule pinned to a filename is a rule a rename switches off, and
"the scan found something" is asserted), and each pattern is now proved to **match the defect and
spare the fix**, the lookbehind that keeps `ShStableMap` from flagging itself has a test rather
than a comment.

**Conditional reads (SH116).** `manifest`, `context` and `blueprints` carry strong ETags;
`If-None-Match` on an unchanged instance answers `304`, which measures as **0** on
`X-Shio-Est-Tokens`. A tag names a **representation**, not a state, the SH77 digest covers
content only, so JSON and terse share it and every tag mixes in `format` + `ids`. Responses
say `Cache-Control: no-cache` (store it, ask first), never a freshness lifetime. The rest of
`find`, `read`, `changes` and the SH16 post-type listing joined them in **SH159**, tagged
over the **rendered body** rather than a content digest, those four have no digest of their
own, and four hand-derived ones would be four things that can quietly disagree with what was
sent. Hashing the bytes also gets the representation rule for free: a two-field projection
and a whole document are different bytes. **`changes` revalidates in *both* modes (SH232)**, and
the road there is the lesson: SH159 shipped documenting the window as unrevalidatable because its
cursor was the moving edge of the feed. SH147 then replaced the lag with a watermark and made that
false for every window that has rows, the cursor became the newest row's position, which does not
move until somebody writes, and nobody noticed, because a limitation that quietly stops applying
fails no test. **Measuring it** (two polls, byte-compared, in all three shapes) left exactly one
case: the **empty** window, whose cursor was the ceiling and so was `now` to the microsecond. It is
the start of the trail instead, an empty window is proof that no row exists in scope below the
ceiling, so a floor skips nothing a ceiling would not have skipped, with a note saying so, since
`cursor=1970-01-01` otherwise reads as a bug. That is the read a session opens with and the one a
client that kept no cursor repeats, so it is where a `304` is worth the most.

**One rule for an unrecognised parameter value (SH320): a parameter that *selects or bounds work*
teaches; one that only *adds a section* does not.** So `?format=` and `?digest=` are teaching 400s
(they choose what the response is, a typo answers a different question), `?include=` is deliberately
lenient (a typo costs the caller an extra its own code will notice, which is also the answer to
SH287), and `?verify=` moved to the teaching side via `ShAgentFlag`, because it runs a whole lint
pass: `?verify=yes` used to be a caller asking for proof, getting silence, and reading a 200.
`?ids=` stays lenient, it only adds a field to rows that are otherwise identical, and `?ids=1` is
informal rather than wrong; `1`/`0` are accepted everywhere for that reason.

**And one rule for an unrecognised parameter *name*, on the writes only (SH430): a mutating agent
endpoint refuses a query parameter it does not declare.** `POST /agent/batch?dryRun=true` created a
site, two folders and three posts and answered `"dryRun": false, "changed": 6`, `ids`, `verify`,
`digest` and `format` are query parameters there and `dryRun` is a body field, so the spelling that
looks exactly like the four flags that *are* query parameters is the one Spring drops. The refusal
is a teaching 400 in three shapes: *it is a body field here* (with the allow-list), *this endpoint
has no `dryRun` and `POST /agent/plan` takes the same document and never writes* (`/agent/apply`),
and *not a parameter of this endpoint* with `didYouMean`. `ShAgentQueryParams` is a
`HandlerInterceptor` on `/api/v2/agent/**` that reads the allow-list off each handler's own
`@RequestParam` declarations, so a new endpoint is covered before it is written, which a
per-endpoint check would not have been (SH338). Accepting `?dryRun=` as a synonym was the other
option and is worse: it makes the convention ambiguous rather than merely undiscoverable (SH365).
**Reads stay lenient** for SH320's reason, and `POST /mcp` is outside it, a malformed MCP message
must come back as JSON-RPC, not as an HTTP problem body.

**`?format=` on `/agent/context` has three values (SH78, SH115):** `json`, `terse`, and
`agents-md`: the same pack rendered as project agent instructions, which is what
`shio context --as agents-md --out AGENTS.md` writes. An unknown value is a teaching **400**,
not a silent fallback to JSON.

**Measure before you fix, and the measurement is the first deliverable.** P3 makes this binding for
tokens and `ShAgentResponseBudgetTest` for response sizes; the same rule applies to **any** cost,
queries, renders, suite time. A performance change lands as two things in this order: a benchmark
that says what the cost is today, then whatever that number argues for. Not the other way round, and
not the fix alone.

Three reasons it is a rule rather than a preference, all paid for here: SH164/SH239 exist because an
unmeasured claim about cost drifted for three blocks with nobody noticing; SH264 measured a sweep at
21 queries a page and the number *changed the design* of the cache SH311 then built (sweep-scoped,
not longer-lived); and SH314 measured a premise three shipped designs rested on and found it held,
which is only worth knowing because it might not have. A cost that is argued rather than measured is
the same defect as a premise carried in a comment (SH275, SH290), and it fails the same way: quietly,
and in somebody else's environment.

Corollary, from SH322: **when a task says "and whatever a grep turns up", the grep is the
deliverable**, it lands as a lint *before* the fixes, because that is what finds the call site
nobody read. SH279's own first half created one of the nine sites its second half had to fix.

**And the same rule holds for a law, not just a call site (SH338).** When a review finds a design
law is being broken, the instrument that finds *every other* break is the deliverable, and it lands
before the fixes. This is filed because the omission is measurable: eleven lints in this repository
guard **internal consistency**, the code agrees with itself, or with its own documentation, or a
decision was taken rather than inherited, and **none** guards *reachability*, whether the operator
the product is designed for can actually invoke a capability. So a consistency defect becomes a
permanent guard and a reachability defect becomes a task that is fixed once and guards nothing,
which is why **P1 was the only one of the ten laws with no instrument** while P3 has the budget
tests, P5 has `ShByteStabilityLintTest`, P6 has a `fix` on every 4xx and P10 has
`ShConsoleShapeOverlapTest`. The cost is not hypothetical: SH160's `instanceMemory: true`
half-truth came back as SH326's `serverRender: true`, and SH170's session+CSRF friction came back
as SH327 and SH335, the same classes, two blocks apart, each found by someone doing a build rather
than by a build failing. **Before "fix the thirteen instances", ask what would have failed.**

**`site.delete` closes the other half of SH170 (SH463)**, and the instrument half of that task is
worth more than the verb. An agent could create a site and not remove one, so clearing sixteen
scratch sites took the browser's dance: `DELETE /api/v2/site/{id}` with Basic auth answered **403**
on all sixteen and only `GET /api/v2/csrf` + its cookie + the echoed token worked. It is
maintenance rather than an edge case, SH448's suite seeds a site per scenario and a **failed**
test never reaches teardown, so every red run leaves debris, and the suite's own harness had
hand-rolled a session for it and called the asymmetry "not this task's to fix" in a code comment,
filed nowhere. The op is destructive (SH108's confirm token). Scoped credentials
are refused: an allow-list says what may be read and written, never that the container may be
destroyed.

**SH648 gave it an undo, and the plan line is where that is decided.** `site.delete` used to say the
thing `folder.delete`'s could not (*purged, not trashed, so there is no undo*) and an agent that
read it as restorable would have offered the curator an undo that did not exist. It now **trashes**,
so the sentence names the op that reverses it, and the failure has an exact mirror: an agent that
failed to offer the undo that *does* exist is the same P7 defect from the other side. Which the
caller believes is decided by that one line, on both branches:

- `site.delete` → *"…to the trash - site.restore brings it back"*
- `site.delete` + `data.purge:true` → *"…permanently - `data.purge` declines the trash, so this one
  has no undo"*

A key on the existing op rather than a fourteenth name, because a purge is SH413's *modifier* shape:
the same gesture with the undo declined. `site.restore` **is** a name of its own, a restore is not
a way of declaring a site's state, and calling it anything else would make the vocabulary a caller
has already learned from `post.restore` and `folder.restore` inconsistent at the point they reach
for it. It is the one restore addressed by `site:<name>` rather than `id:`: a trashed site keeps its
unique name (that is what `ShSiteNameHeldByTrashException` exists to say), so an agent that trashed
a site can undo it without having kept an id. Same `requireUnscopedCredential` as the delete, and
for SH696's reason from the other direction: a credential scoped to a site list must not be able to
*add* a member by resurrection any more than it may remove one.

**And a site that was never meant to be kept (SH591).** Twelve replication rounds left thirteen
sites on one instance, `R6Fid`, `R6Aut`, `R9Aut`, `R9Loc`, `R10Ssr`, `R11`, `R12` and six older
ones, every one made to answer a question that has since been answered. Removing them meant
thirteen `site.delete` ops, each a 428 and each with its own confirm token: SH108 working exactly as
designed, applied to the one case where the surprise it guards against **cannot happen**. The
distinction the model was missing is *who declared the site disposable, and when*. So `ShSite`
carries a `scratch` column, `site.upsert` reads `data.scratch:true` **on the create branch and
nowhere else**, and `shio site rm --scratch` sweeps every site carrying it in one confirmation.
**The create-only rule is the entire safety argument**, not a limitation: a flag an existing site
could gain would make the sweep a fast way to delete a site somebody chose to keep, whereas a flag
set at creation was declared in a document a curator could read *before there was anything in it to
lose*, P7 satisfied rather than waived. `ShAgentSiteDeleteTest` asserts both directions (a create
marks, an upsert on an existing site does not), because that is the property that stays true only
until somebody adds a convenience. **Nothing in SH108 changed**, and that is worth stating: the
confirm token has always been *per batch*, `ShAgentConfirm.ofPlan` digests every deletion the plan
reports, so thirteen deletes in one document have always cost one token. The protocol was never
the problem; nothing marked a site as disposable, so the sweep could not be *composed*. Two smaller
decisions: only the JSON literal `true` counts (a flag deciding whether a site may later be
bulk-deleted is the last place to be generous about spelling), and the flag is **omitted** from the
context pack when false, because `scratch:false` on every row is a token per site to say the
ordinary thing (P3). The second half of the task is worth as much as the first, `shio context` and
`shio site` now **mark** the disposable ones, since a listing showing thirteen sites with no
difference between the two a curator maintains and the eleven an agent left behind is a listing
the curator cannot act on.

**Two instruments existed to find that gap and neither could, which is the part to remember
(SH463).** `ShAgentP1ConformanceTest` is keyed on manifest `features`, and site deletion had **no
key**, so it was not classified at all: an inventory keyed on what the manifest advertises measures
the manifest, not the surface. `ShStatelessReachabilityLintTest` classified a whole controller
**root**, so `/api/v2/site` read as covered on the strength of `site.upsert` while the same
controller declared `DELETE` as well. Both are fixed in the same commit as the verb: `siteDelete`
is a feature key (so the classification is forced), and an `AGENT_EQUIVALENT` entry now **names the
HTTP verbs its agent form delivers**, asserted both ways against the verbs the source declares. On
its first run the new check found two more undeclared decisions nobody had written down,
`/api/v2/object PUT` (`copyto` has no op and needs none: a copy is a read plus a `post.upsert` at
the new address, which is better, because an op that duplicated an object would have to decide what
happens to its id) and `/api/v2/post-unified PATCH` (SH335's partial merge has no op deliberately:
`post.upsert` declares *whole* state, and the merge exists for the editor's several-curators
problem, which an agent does not have).

**The same shape, one surface over: a bound property nothing reads
(`ShBoundPropertiesReachabilityLintTest`, SH656).** `ShGitProperties` bound `shio.git`,
`application.properties` shipped `url=GIT_URL_HERE` and `token=DEFINE_TOKEN_HERE`, and no bean
injected it. The dead class is two lines; what it did to the *check* is the finding.
`docs-vocabulary` derives the legitimate `shio.*` vocabulary from these very classes, so the
published configuration reference documented `shio.git.url` and **passed the gate**, an operator
sets it, gets no error, and cannot learn it was never read. A documentation check cannot see a
defect in the vocabulary it checks against.

**Written for one key, it found three.** `ShMgmtProperties` (`shio.mgmt.enabled`, documented as
*"Enable the management interface"*) was the second, on the same rule and named by nobody. The third
needed the rule sharpened: prefix coverage alone is too weak for the catch-all `shio` prefix, so the
first segment under a prefix must be a **field the binding class declares**, relaxed the way Spring
binds (`multi-tenant` → `multiTenant`). That found `shio.plugin.blogger`, which names a class this
repository does not contain. Three published rows removed, and the exemption map is **empty**, a
bound prefix nothing injects has no honest reason to exist, so an entry would have to survive the
question *"then why is it bound?"*. Source-level and deliberately not reflective: Spring injects by
type, so a consumed class names itself at the injection site, and `ShSourceText.code` strips prose
first so a javadoc mention cannot make a dead class read as live (SH206).

**P1 has an instrument now, and its exception list is the inventory (SH338).**
`ShAgentP1ConformanceTest` classifies **every** key in the manifest's `features` map, a key
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
what the instance advertises**, a config-driven flag (`webhooks`, `previewTokens`,
`scheduledPublish`) drops out when an operator disables the subsystem, and an inventory that
shrinks when somebody turns something off is measuring the environment (SH315's lesson, one
surface along). And **the run prints it**, like the SH264 sweep benchmark.
The measured answer: **52 features, 14 not agent-reachable**, 8 `REST_ONLY`, 3 `CONSOLE_ONLY`,
3 `CLI_ONLY`. Only `reviewQueue` is a decision (SH109 keeps the curator's veto off the agent
namespace) and three more are deliberate trades (`graphql` is the front end's, `contentAsFiles` is
CLI-shaped by P4, `postTypeMerge` would put `shio_apply` over its SH132 ceiling). The rest are
gaps, and **four had no task at all** before this ran: `locales` and `scheduledPublish` (SH342,
id-keyed post verbs with no address-native form), `webhooks` (SH343) and `previewTokens` (SH344).
That is the return on the instrument, and it is the argument for building one before working the
thirteen known instances by hand.

**Which of those are gaps and which are settled is a field, not a paragraph (SH813).** The list
above already made the distinction, `reviewQueue` is P7's veto, `pageAudit` would put Chromium in
the JVM, `siteCapture` would attach a crawler to SH101's request-forgery surface, but it made it
in prose, so *how much of P1 is left* had no answer shorter than reading every entry, and the one
number the run printed was decisions and gaps **added together**: a total that does not move when
the work lands. `Delivery.decision` splits it, set on the entries whose own reason already argued
it, and the run prints `N decided (closing would be wrong)` beside `M still open` with the open
ones named. A decision must be a `Reach` that `isException()`, nothing is decided about a
capability the agent already has.

**And then there were none (SH816).** SH813 also asserted the split was *real*, at least one of
each, on the reasoning that all-decided is a table that has stopped asking. The lone gap was
`contentAsFiles`, and it was one on the strength of a single clause: *"the flag does not say so."*
The flag had said so since 2026-08-04. SH393's `elsewhere` publishes `contentAsFiles → shio pull
--content`, and the check above it asserts that map **set-equal to this list both ways** on every
run, two assertions over the same nine keys, green together for a fortnight, sharing their keys
and not their prose. On the merits it is a ninth decision, and not a close one: P4 makes the
*filesystem* the capability rather than its delivery, so a client with no disk has nowhere to
project to and already holds `shio_find`/`shio_read` for the reads it would want instead. Closing
it is not merely unnecessary, it is incoherent.

Zero gaps being the true state, the guard against a table that has stopped asking cannot be a count
that is now false, and it may not be preserved by keeping a wrong entry, which is the tempting
move. It becomes: **every decision names what would falsify it**, a `target` whose appearance fails
the build or a `closedBy` naming the ops that already deliver it. That is the property "decided"
must never escape, and it survives the list being entirely decisions, because it makes each entry a
standing claim rather than a closed file.

`noReasonClaimsSilenceTheManifestBreaks` is the assertion that would have caught it (SH527): a
reason claiming the caller is not told, for a key `elsewhere` points at, is now a red build. It is
SH793's repair one field along, that task made a reason name the run that prints its number; this
one makes a reason about a *response* be checked against the response. Two details cost a run each.
The phrasings are a held list, not a regex over "say", because a pattern loose enough to catch
every wording catches the sentences explaining the rule. And **quoted spans are stripped**, because
the first version read the corrected entry's own quotation of the old clause as a fresh claim and
failed the build on the fix, SH794's lesson, third application, and a scan that forbids quoting
the defect makes its own entry unwritable.

### The inventory's own guarantee had a hole, and shipping through it is how it was found (SH812)

`ShAgentP1ConformanceTest`'s central promise is in its class javadoc: each exception *names the tool
or index path that would resolve it, and the test asserts that name is still absent*, **ship the
tool and forget the table, and this fails.**

It did not fail. SH812 shipped `format` on `shio_context`, which is exactly what `agentsMd`'s entry
named as its resolution, and the run came back green with the entry still listed. The staleness check
compared a target against tool **names** and route **paths**, and several entries name neither:
`agentsMd` awaited `shio_context.format` and `postTypeMerge` awaits `shio_apply.mergePostTypes`,
which are **parameters**. The guarantee was false for precisely the entries that name their
resolution most exactly.

The check reads a `tool.parameter` target now, it looks the tool up and asks whether its schema
declares that property, and re-running it failed naming `agentsMd`, which is what made removing the
entry forced rather than remembered. That distinction is the whole value of the rule: this list
became the P1 backlog two blocks ago, and an exception that cannot go stale is one that outlives the
work.

**Then the classification was wrong twice before it was right**, and both guards were correct.
`AGENT_ROUTE` pointing at `/agent/context` made `ShAgentCapabilityJoinTest` fail, that route is a
core endpoint belonging to no flag, and claiming it would count one capability twice, which is the
objection `COMPOSED` was created for (SH732). A capability delivered by *a parameter on something
already claimed* is that category's definition, so the entry carries `closedBy: shio_context` and no
target at all.

### The first row of that inventory worked rather than read (SH809)

`universalEditor` was the one `CONSOLE_ONLY` entry that **named a route and did not have it**:
`/agent/editable`, asserted absent, with the note that SH185's bridge is a browser protocol and the
console is its only client, *`true` is honest to a curator and not to an agent*.

So an agent could read a post, render it and diff draft against live (SH619), and still not answer
the question a curator's job turns on: **which fields on this page can somebody change**. That is the
replication round's subject one layer up (a replica is authorable or it is a screenshot) and P7's
*never surprise the curator* read from the other side.

`GET /api/v2/agent/editable?address=post:Blog/blog/hello` answers it, and **reads the page rather
than the model**. The editable surface is already written down in the rendered HTML by the protocol
SH185 specified for the browser, `data-shio-prop` naming a field, `data-shio-post` the post that
owns it, `data-shio-region` one level up, and the console's editor reads exactly those. Deriving
editability from post-types and layout bindings would answer what the *model permits*, a different
question, and the two would agree only by accident (SH583). What a curator can click is what the page
says it is.

`source` distinguishes the two empties: `rendered` means the page composed and this is its answer,
`content` means it does not render yet (no layout binding, or no friendly URL) which is the normal
state of a site mid-build and **not** the same as nothing being editable (SH169's argument, one
endpoint over).

### And what may be *added*, on `include=insertable` (SH1039)

SH1038 made a section a selectable object a curator can move, duplicate and remove, and an editor
that can do all three still could not **add** one, because this read answered fields and regions with
no insertable set, no per-container allow-list and no catalogue. The shared section types reached the
console as post-type names in a picker: a list of names, never something to place.

**The catalogue is the template's, and only the template's.** Only a template knows what a container
accepts (the same argument that makes `sh-field` the author's job rather than the engine's) so the
allow-list is `sh-accepts="SectionHero SectionCta"` on the element composing the sections, rendered
as `data-shio-accepts`. An **absent** declaration means *nothing is known to fit here* and the
container carries the line that would fix it; it is never read as *anything*, because reading it that
way would need a server-side list of which post-types are sections, and `ShVocabularyPostTypes` is
explicit that a third hand-written copy of the vocabulary is the drift SH245 bans. The CLI's generated
layout declares what the conversion provisions, derived from the same table the field filter reads,
and both first-party blueprints declare their eight.

The declaration is the template's and the **resolution is the instance's**: a type the layout names
and this instance has not got comes back `provisioned: false` with its name, rather than dropped,
the page declares it, so a shorter catalogue would be a quieter lie than an honest gap (SH169). One
post-type read per distinct name across the page, not per container.

A rider rather than a route: the caller already holds the address this read takes, so a second
endpoint would resolve the same page again for the half the first one left out (P2), and it would be
one more GET root for `ShAgentReadReachabilityLintTest` to classify. Opt-in because it is the
expensive half, the fields of every accepted type are what a properties panel needs and what a
caller asking *which fields can I edit* did not ask for (P3), so `containers` is **absent** when
nobody asked and **empty** for a page that composes no sections. MCP reaches it the way it reaches
`editable`, as `shio_digest include=insertable`, which implies `editable`: the catalogue is per
container of one page, and the containers are on the answer `editable` composes. In terse form it is
a `slot` line per container and a `fits` line per type, whose `required=` is the set an insert has to
supply, SH582 is the measured case of deriving fields and never comparing them with the required
set, which left a replica failing `verify` with its content complete.

**And the rows call an address `address` (SH810).** The first version answered
`{ post, field, region }`, which was the only place in the agent packages spelling an address
`post`: inherited from the attribute it is read from, `data-shio-post`, which is right for the HTML
and wrong on the wire. P2 only works if what comes back is the shape the next call takes: the row
was designed to hand straight to `post.upsert`, and a field named `post` where the write wants
`address` means an agent has to learn that two names are one idea.

`ShAgentAddressFieldLintTest` is the guard, and its first draft is the lesson. Applied to whole
source files it flagged **nine**, every one correctly named: they declare *local variables* holding a
post entity, and a variable holding a `ShPostUnified` should be called `post`. Scoped to a record's
parameter list it flags exactly one thing (the wire format) and reverting the rename fires it
naming the file. A lint that cries wolf on nine correct files is deleted within a week.

**Three guards fired on the way, each correctly.** Removing the exception left the feature key
unclassified, so it had to be declared `AGENT_ROUTE`; the manifest's `elsewhere` still pointed
`universalEditor` at the console, which would now send a caller away from the route that answers
them; and the new endpoint took the manifest **90 tokens over its ceiling**. P3 says lean first, so
the description was cut to the decision it informs (~53 tokens), and only then was the ceiling raised,
to the 4% headroom SH743 requires rather than to the measurement, which in turn made `manifest`'s
`AT_THE_WALL` entry stale and the staleness check said so. Four instruments, one change, no
argument.

**Which surface owns a capability is a decision, and it is written down now (SH393).** SH268
argued a zip cannot be a JSON-RPC message, SH186 that a JSON-shaped verb becomes an op rather than
a thirteenth tool, SH104 that a curator's artifact has no server surface, SH288 that a file beats a
read command, SH262 that a projection beats a new endpoint, SH391 that a browser lives where a
browser lives. **Six tasks, one boundary, derived six times, each correctly, and each from
memory**: exactly the shape SH338 named one level up. The rule they converge on:

> **MCP owns address-in, small-JSON-out verbs a session repeats**, because a client may have no
> shell. **The CLI owns whatever has a file, a byte stream, a browser or a person on one end**,
> because none of those fits a JSON-RPC frame. **The console owns the curator's veto.** Nothing is
> owned by neither, an unowned capability is the gap, not the answer.

Two things make it checkable rather than another paragraph to remember. The classification already
records the owning surface (`MCP_TOOL` · `AGENT_ROUTE` · `REST_ONLY` · `CONSOLE_ONLY` · `CLI_ONLY`)
with a reason per entry; SH393 publishes the exceptions as the manifest's **`elsewhere`** map and
asserts **set equality both ways**, so a capability this surface does not carry either gets closed
or gets a pointer, and a pointer cannot outlive the gap. The boundary is now a lookup for the
seventh task instead of a derivation, and the client that used to learn it by failing reads it.

**Translate dialects first, correct spelling second (SH80, moved here by SH356).** Nearest-string
matching is silent on exactly the guess an agent is most likely to make: `RichText` is four edits
from `Text` and further from every other widget, because it is a **vocabulary** mismatch, the
Contentful/Sanity/Strapi/Payload dialects, rather than a spelling one, so a `didYouMean` built only
on edit distance answers nothing in the common case. The fix is a curated alias map consulted
**before** the distance fallback (`ShWidgetAliases`), constrained to only ever suggest a target this
instance has installed. The rule generalises wherever a closed vocabulary meets a caller who learned
a different one; the surfaces that still have only the distance fallback are post-state names, locale
codes and the blueprint parameter vocabulary.

**And a benchmark over a correct script cannot see what an agent gets wrong (SH340).**
`ShAgentTaskBenchmarkTest` measures call and token ratios over a canonical task, which means it
measures a script, and a script never sends `postType` where the surface wants `type`.
`ShAgentNaiveClientBenchmarkTest` drives the MCP surface the way a **naive** client does (the four
wrong calls a real session sent, plus a sweep adding one unrecognised key per read tool) and counts
the outcomes against `naive.*` ceilings in `token-budgets.properties`. **A silent drop is counted
separately and is the expensive one**: a teaching error costs one round trip and the `fix` makes the
retry land, while a dropped argument costs a wrong outcome nobody notices. Measured before SH331: **6 silent drops in 12
calls**, and the sweep was the finding, `shio_context`, `shio_find`, `shio_verify` and
`shio_changes` all accepted an unknown key and answered 200, so the `scope`/`site` case was the
default behaviour of the surface rather than one tool's oversight. That measurement is what turned
SH331 from four renames into one guard; after it, **0 drops and 6/6 naive calls land first try**.
One caution the first run earned: the fixture named a site the isolated database did not contain, so
four rows failed for the *site* and were scored as vocabulary failures. A benchmark about argument
names must fail only for an argument name, SH324's class, in a benchmark instead of a fixture.

**A read-only transaction must not eat the refusal it just wrote (SH345).** A class-level
`@Transactional(readOnly = true)` that throws a `ShTeachingException` declares
`noRollbackFor = ShTeachingException.class`, and `ShTeachingRollbackLintTest` fails the build
otherwise. Without it the bean marks a *participating* transaction rollback-only, so a caller that
declared `noRollbackFor` on purpose, `ShAgentReadService`'s per-row errors, `ShAgentRenderService`'s
SH249 fallback, fails at **commit** with `UnexpectedRollbackException`, nowhere near the cause: that
is how `GET /agent/find?site=nope` answered a 500 instead of a teaching 404. The rule is safe as an
absolute because a read-only transaction has nothing to roll back; a **writing** one is the opposite
and is out of scope, since a refused batch must roll back. Two things the lint got wrong first, both
worth knowing: keyed on any read-only annotation it flags classes whose *writing* methods throw, and
matching `throw new X(` literally it misses the owner-qualified `Outer.XException`, which was the
very bean it was written from.

**A third, and it is the one that reads as correct (SH352, guarded by SH824).** The lint asks
whether the annotation carries the escape, and it once asked whether the *file* did, reading
`matcher.group(1)`, the annotation's own arguments, rather than the body. `ShPageRenderService`
names `noRollbackFor` six times: three annotations and three comments explaining why they carry it.
A file-wide search credits every read-only annotation in that file for a string belonging to the
first, and a **comment** about the escape is enough on its own, so the lint goes green over a bean
it exists to refuse. SH352 fixed it and left no assertion; SH824 is the fixture, a body whose
class annotation lacks the escape while the file has it elsewhere must still be flagged, and moving
the escape into that annotation must clear it. Verified by reverting the one line and watching it
go red.

That is the **fourth** scan in this repository to match outside the construct it was about, after
SH794 (a bare word inside a longer identifier), SH816 (a quotation of the defect read as a fresh
instance) and SH822 (where the proposed fix was the bug). None was visible from a passing run,
which is the whole argument for pinning the scope with a fixture rather than a comment.

**A write is never refused by its own audit row, and a refusal that happens anyway names the op
(SH544).** `ShHistory.description` is `VARCHAR(255)` and `ShContentAuditService.describe()` composed
`Created "<title>"` with nothing between them, so five of one site's 198 page titles, ordinary
marketing sentences over 245 characters, ended a **201-page** conversion with a 500, after 403
assets had already been uploaded. Two defects, and only one of them is the column. The trail exists
to *describe* a write and was deciding whether the write happened: the description is derived, so it
is bounded where it is built, and the title gives way (the parenthesised `detail` an SH196 reader
needs survives). The second is that the refusal was a **500 with Spring's default body**, nothing on
the wire named the column, the width, the op or the address, so it was diagnosed from the server log,
and SH513's CLI cannot print a field the server never sent. `ShDataIntegrityProblem` is now the one
mapping both boundaries use: SQLSTATE `22001` is a 400 `value-too-long`, any other constraint a 409
`data-integrity`, and never a 500, **a 500 tells an agent to retry, which is the one thing that
cannot work here**. In a batch the problem carries `at: ops[i]` plus the op and its address, and what
makes that index *true* is not an extra flush but an **earlier** one: Hibernate auto-flushes before
the next op's address query anyway, so without it a refused insert surfaces inside `ops[i+1]` and an
`at` naming the wrong op is worse than none (P6). The constant and the schema are held together
against the **changelog**, not the `@Column` annotation, `ddl-auto=none`, so asserting the entity
agrees with itself proves nothing.

**`state` is which row you got; `published` is whether the page is live (SH545).** A publish writes
a second row and leaves the draft alone, and every agent read is DRAFT-preferred so the agent can
see its own work, so `state` is `DRAFT` for a page that has been serving publicly for weeks, and
the question a curator cares about most had **no word at all**. What that cost is worth stating,
because it is P9 exactly: an agent that had just published could confirm it only by publishing again
(correctly answered `noop`), by calling the public route and reading the state off an HTTP code, or
by reporting a failure that had not happened. Repointing `state` was not available, it would then
lie about where `data` came from, so `/agent/read` and `/agent/find` carry **`published`**, and
after a publish `state` is still `DRAFT`, correctly. **On `find` it costs no query**: the walk
already fetches both state rows and `dedupe` was throwing the published one away.

**Two measurements decided its shape, and both refused the first design.** Present-on-every-row
(`published: false` on a draft) cost 2.6% of a single-address read and dropped
`attribution.addressing@scale.min-token-ratio` below its floor; grounding the convention in a full
sentence on the context pack dropped `discovery` below its own. So it is **conditional**, present
only when the page is live, an eight-word clause on the pack's existing *writes land in DRAFT* line
making absence an answer rather than a gap, which is SH348's pattern (`locale`) and not SH299's
(present and zero), and the right one because publication *has* a knowable default: P7 makes DRAFT
what a write lands in. The one floor that did move is recorded in `token-budgets.properties` with
the reason it is not an idea ceasing to pay: the baseline it is measured against
(`GET /api/v2/post-unified/{id}`) has always returned `published`, so 53.1× was partly the agent
read being cheaper by omitting a fact the console read carried, and the second call to the public
route that bought it was never counted. **The sitemap's `Node.state` has the same ambiguity and is
not fixed here**, it is a third surface with its own budget and an SH116 digest that a field would
move, and confirming a publish is not what a structure map is read for.

**A post's locale is on the read, and only when it is not the default (SH348).** `/agent/read` and
`find`'s rows carry `locale`, before this the string appeared nowhere in `ShAgentReadService`, so
`locales: true` advertised a capability no read reported the state of. **Absent means the instance
default**, which the context pack publishes on the first call a session makes, so absence is
unambiguous rather than merely cheap (SH121's pattern, not SH299's: a locale has a knowable default and
a marker count does not). The default is `ShPostUnifiedWriteService.DEFAULT_LOCALE`, named once,
because the writer owns it and the pack reports it. **And `post.upsert` accepts one, on create only** (SH348 step 2): the file projection carries
`locale:`, so a `pt-br` page pulled to files and pushed elsewhere is no longer rebuilt as `en`.
Relocalizing an existing post is deliberately not possible here, that is its own gesture, the way
`post.move` is for an address. **The sibling link is `translationOf`** on the same op: an address, never
a group id, resolved server-side to the target's group. Every translation names the group's *anchor* and
the anchor names nothing, which keeps it a forest; a declaring post is compiled after the others, so a
document never has to be ordered by hand; and a target that resolves to nothing is a teaching 400,
because creating an unlinked post and reporting success is SH331's silent drop.

**A read says when a translation has fallen behind (SH1058).** `translationStale: true` on
`/agent/read`, and nothing at all otherwise, never `false`, so every row of a monolingual instance
is the row it was. It answers the question an agent has before rewriting a page: the source this
translates has changed since the translation was written. The comparison is a stored digest of the
source's document (`ShTranslationStaleness`, shared with `verify`'s `translation-stale` and the
console), stamped at translate time and re-stamped by the translation's own edit, so a write is how
a translation says it has caught up, and no read ever refreshes it.

**A scheduled publish is `when` on `post.publish` (SH342).** ISO-8601, absent means now, and a
**past instant is refused rather than read as now**: publishing now is what the op already means with
no `when`, so accepting a past date would make two calls mean the same thing and swallow the timezone
slip this catches. Checked before the no-drift noop, since "the draft matches what is published" says
nothing about a transition asked for later; a shape over `ShPostUnifiedWriteService#schedule` (P10),
not a second scheduler. Two things it cost, both worth knowing before proposing the next argument:
`tools/list` had **one token** of headroom (2359/2360), so `shio_apply` was trimmed to 440 before the
ceiling moved to 2375, this surface is a wall, not a budget. And SH338's rot check did **not** fire,
because the exception named the route it expected and the fix took another shape: **the stale check
catches the predicted fix, not any fix.**

**A ROADMAP entry is one sentence: what + why + `→` pointer.** The reasoning belongs in
IMPROVEMENTS.md, which is what the pointer addresses; a line that restates it makes the roadmap a
history instead of a queue, and lets two files disagree about one design. Measured when it was
noticed (SH341): 95 active task lines averaging **142** words, worst 555, and six of the worst
eight were written in the same session that then found the problem, so treat this as a drift the
process invites rather than a lapse. If the analysis feels too big for the line, that is the
signal it goes in IMPROVEMENTS, not that the line should grow.

**The cost of all of it is measured, not asserted (Block M).** `ShAgentTaskBenchmarkTest`
runs four canonical jobs over the agent surface *and* over the id-keyed console/CDA path and
asserts both ratios; `ShAgentResponseBudgetTest` guards eleven canonical response sizes. Both
read their ceilings from `shio-app/src/test/resources/token-budgets.properties`, the same
file as the SH132 MCP budgets, because a second place to keep a number is a place that goes
stale. Raising a ceiling is allowed and must be justified in the commit.
**Every one of those scores a *surface*, and P2's claim is about a *path* (SH597).**
`tools/list`, a canonical response and a task an agent has already decided how to do are all
surfaces; "one call replaces a session" is cold start to the first correct action.
`ShAgentDiscoveryCostTest` scores that: six **intents**, a job with a known right first move,
each walked from an empty context up a two-rung ladder (`/agent/context?include=all,manifest`,
then the full endpoint index) until a rung's body *names* the move, which is then executed and
required to succeed. **The marker is a path, never a `features` flag**, because `diagnostics:
true` is exactly what a replication round had in front of it while it grepped `store/logs`. An
intent no rung names fails the build: a capability that shipped and cannot be found is P1's
failure wearing a green build. First measured at **10604 tokens over six independent cold
starts** (the sum is of cold starts, not of one session, an intent needing rung 2 is paying
for the one-call promise not holding), and the printed table names the rung that answered each.
Two of the six cost ~3.7× the rest because `startHere` named neither `/agent/diagnostics` nor
`/agent/changes`, so the only route to them was the ~2400-token index. That was the finding,
and SH601 moved it: **all six are now answered at rung 1, and the headline is 6387** (SH683
re-measured it, `diagnose-5xx` 3307 → 1041, `resume-a-session` 3285 → 1019).
**The ceilings did not move on their own, which is the second finding (SH683).** They read
3650 for weeks after the fix made them 1041, because `isLessThanOrEqualTo` cannot object to an
improvement: the surface could have grown 3.5× before a build failed, and the win was recorded
nowhere. The total is now asserted `isBetween`, a floor ~5% under the measurement, on the
**total only**, so a 40% win cannot be absorbed in silence again while a per-intent floor does
not put six tripwires under whoever improves one row. Falling through the floor is not a
defect; it is an improvement owing `token-budgets.properties` its new number.
**And P2 has a grep now, not only a price (SH598).** `ShAgentP2ConformanceTest` is P1's
instrument for the other law: discoverability (*named in a response already received*) is
**graph reachability**, so it seeds with an entry point's response, opens every **door** that
response names, and repeats to a fixed point. No model, no tokens, no judgement. Both cold
starts are walked, because the product has two: `GET /agent/context` and MCP `initialize`
(SH596 is the break only the second walk sees, SH595 the one only a *walk* sees at all, the
endpoint existed, the manifest named it, and the path from the entry point did not close).
The population is taken from the registries, never listed: 11 tools, 7 resources, 4 prompts,
28 declared routes, 11 batch ops, 10 `elsewhere` pointers. **All 71 are named from both
entry points, and the empty gap list is the result rather than a formality**, manufacturing
entries so the deliverable looked like P1's would have been the one dishonest thing available.
The finding is the **depth**, which is asserted beside the list because a capability can stay
findable and still get further away: the MCP surface sits at **depth 3** from HTTP (pack →
manifest → `POST /mcp` → a list), the HTTP index at **depth 2** from MCP through
`shio://manifest`, and the op vocabulary at depth 2 from HTTP because `include=ops` is a
projection nothing names until the manifest arrives. One finding was about the walk itself and
the comment is left in place: leaving `resources/templates/list` shut reported
`shio://blueprint/{name}` unreachable from an entry point that had already been told where to
look, a reachability check models the protocol or it reports its own gaps as the product's.
**And the three registries now join (SH599).** A capability is declared in three unrelated
places (a boolean in `features`, a path in `endpoints`, a tool/resource/prompt on MCP) and
`ShAgentP1ConformanceTest` only ever walked *feature → is it delivered*. The other direction was
noticed once (SH463: "a capability with no flag is not classified at all") and never generalised.
`ShAgentCapabilityJoinTest` puts all three in one key space (`tool:` / `resource:` / `prompt:` /
`route:`) and fails naming any entry that no flag claims and nothing declares flagless. **The
claimed side is read from P1's table, never copied**, a second copy is the drift the test exists
to catch, so `classification()` is package-visible for exactly that. Both directions are guarded:
an unaccounted entry fails, and an accounted entry no registry holds fails too (SH142's rule, so a
classification pointing at a deleted tool cannot read as coverage). **Measured: 50 entries, 16
claimed, 34 flagless by design**, declared in seven families (`PROTOCOL`, `MIRROR`, `PROMPT`, `CDA`,
`ID_KEYED`, `BYTES`, `SAME_FLAG`) rather than thirty-four essays, thirty-four paragraphs would be
thirty-four chances to write a different rule. The 34 are not a backlog: a flag answers "does this
instance do X", and most of the surface is machinery that is not optional. Claiming them anyway
would be SH596's mistake inverted, answering "is this accounted for" with a neighbouring
capability.

**A narrowed discovery response says what the narrowing removed (SH600).** `?include=identity`
is a reasonable-looking way to ask the manifest a narrow question; it returns **200** and drops
all 57 `features`, the `limits` and `startHere`, everything P2 exists to deliver. A skill's
opening line carried exactly that call for twelve rounds, and in every one of them the manifest
was read and the capability list was never seen. **P6 scopes "errors are instructions" to the
4xx and this failure has no error**, which is why it is the one place a 200 misleads worse than
a 404: a 404 would have been read. So `ShAgentManifest` carries **`omitted`**, section wire-name
→ entry count, built in `ShAgentManifestSection.project`, absent entirely on a full document.
Terse renders it as one line, **first**, so a reader that stops early meets it:
`omitted features=57 limits=1 endpoints=28 start-here=5`. Counts and section names only, never
content, listing the 57 flag names would cost most of what the projection saved, which would
make the fix an argument against itself. It uses `attr` and not `count`, because `count` folds a
zero away and a dropped section that happened to be empty is exactly the case a caller still has
to be told about.
**It reaches the context pack for free**, and that is the part worth knowing: the manifest folded
in at `include=manifest` is *itself* projected to features+limits+startHere, so a session opening
with the one call is now told `omitted endpoints=28 elsewhere=12`. Eight tokens against the ~2400
that rung 2 costs (SH597), and eight tokens is what stops a caller not knowing there is anywhere
to go. **The context pack's own `include=` still has the hole**, deliberately: its sections are
content-shaped, so a projection and an empty instance are indistinguishable after the fact the
way `features: null` and `features: {}` are not. That is SH608.
One trap this change exposed, and it is the same confusion one level up: three assertions used
the *word* `endpoints` as a proxy for the index's absence, and now the word appears in `omitted`.
They match the section's opener (`"endpoints":[`) instead, an assertion that cannot tell content
from a report of its absence would have passed over either one going missing.
**And the render sweep is measured in queries, not milliseconds (SH264).**
`ShRenderSweepBenchmarkTest` counts Hibernate statements, a timing ceiling on a shared machine is a
flake, and SH250's rule is that a flake is worse than a break. It prints on every run. The measured answer,
and what SH311 did with it: a rendered digest cost 21 queries per page and the *same* 21 for the next
page of the same site, which is per-page work carrying per-**type** information. **SH311 caches the
resolved templates for the length of one read-only transaction**, a sweep of ten pages is now
**6 queries per page** and a three-site `verify` pass **59 instead of 137**. The lifetime is the
design: a longer-lived cache would need invalidating on a `site.postTypeLayout` write that no
post-level listener sees (SH193's trap), and **a writing transaction gets no cache at all**, which is
`isCurrentTransactionReadOnly()` on the line above the lookup rather than a convention. A *standalone*
render is deliberately still 19 (independent calls share nothing) and the benchmark asserts that,
so a cache that ever outlives a transaction fails there instead of arriving quietly.
**And "bounded by bindings, not pages" is measured (SH314)**, because SH193, SH251 and SH262 all rest
on it: 2 bindings + 60 pages graphs in **12** queries, 20 bindings + 6 pages in **84**. Thirty times
the pages and a tenth of the bindings is seven times cheaper, so the premise holds. Measured
**uncached** on purpose, inside a read-only transaction SH311's cache takes 84 to 8, which is what
production pays and is not what the claim is about.

**A test that measures the whole instance may not share it, and `ShBenchmarkIsolationLintTest`
enforces it (SH315).** Three source rules: a `@SpringBootTest` in the benchmark package declares its
own `spring.datasource.url`; so does any test making an **instance-wide read**; and no two classes
claim the same in-memory database.
**Since SH318 that middle list is derived, not remembered**: a `*Service` method whose first
parameter is `String site` is instance-wide when that argument is `null`, *by construction*, so the
scan finds `find` and the SH160 memory reads that the hand-written `verify`/`context`/`changes` never
mentioned while its message implied the three were exhaustive (SH260's move for helpers and SH300's
for DOM attributes, on the third hand-kept list). It caught two real ones on the way in,
`ShAgentMemoryTest` and `ShRememberToolTest` both asserted on `notes(null)` from the shared context.
The match requires a **`Service.` receiver**, which is load-bearing twice: without it
`Declared.of(String site, …)`: a factory nested in a `*Service.java`, matched `List.of(null)` in an
unrelated test, and a *test-local* `verify(String checks)` helper that scopes correctly was flagged as
the opposite. Reads with no site parameter at all (`diagnostics`) stay declared, since there is no
nullable argument to notice. It exists because the
convention was documented on four classes and broken five times (SH239, SH272, SH289, SH314, and two
incident tests found while writing the lint), always caught by accident, because the code is right
and only the *measurement* is wrong.

**Both boot their own in-memory database**: several of the responses they measure describe the
*instance*, so on the shared test datasource the numbers grew with whatever post-types other
tests created, the task benchmark measured 1616 tokens alone and 2243 in a full run against an
1800 ceiling, which is a red build nobody caused.

**And it is attributed per source (SH164, corrected by SH239).** `ShAgentSourceAttributionTest`
isolates the five ideas a task ratio bundles, addressing, projection, batching, discovery, lint,
one pair each, with a floor each (`attribution.*` in the same file). It exists because the task
ratios fell three blocks running while the agent path stood still (SH117/SH120/SH176 each made the
*baseline* leaner), so one ratio per task could no longer tell "the protocol is cheaper" from "the
old console was fat". Measured, order-independent: **the lint carries the token claim, 15.5× on
five posts and 139.6× on forty-five, the only source that scales without bound** (one call against
one read per candidate, 45× on calls); **addressing is 11.8× and scales sublinearly** to 53.1×,
because the baseline is a listing *plus* a lean id-keyed read and since SH165 that listing is
paged, so the growth caps at one page; **batching buys turns, not bytes** (5.0× calls, 6.1×
tokens); **discovery is a round-trip claim and almost nothing else** (4.0× calls, **1.2×** tokens,
a context pack describes the instance, so it is not much smaller than the reads it replaces, and
its call ratio grows by one per folder); **projection is the smallest (1.5×)** and the easiest to
point at. Its pairs are deliberately generous to the baseline, handed folder, type and post ids it
could only have crawled for, so each ratio is a **floor** on the advantage.
**SH239 also corrected SH164's own first numbers**, which were order-dependent: the batching pair
wrote into the folder the addressing and lint pairs measure, so addressing read 26.2× when batching
had run first and 11.8× when it had not, the same confound the benchmark's own database fixed, one
level in. Batching writes to its own folder now, and the scaling claim is asserted by measuring
**two fixture sizes and requiring the ratio to rise** rather than by argument.
**The ≈10× per-task target is retired as a headline claim** and kept per source: an aggregate
mixes five savings, names none, and *falls* when the console gets leaner, so defending it would
mean arguing against P1. On tokens the claim is ≈10× where it is real (addressing, the lint); on
calls it is a **session**, a canonical task is 3–4 calls (SH157), one agent session instead of
three.

### What bounds an agent: Block L (SH106–SH109, SH200, SH201)

**The invariant: the agent path is never a privilege escalation**, and after Block L it is
not a privilege *superset* either. Three guards, all narrower than what was reachable before.

**And it cuts both ways (SH201).** After the block landed, the agent path was in places
*stricter* than the console, and where that happens the console is the hole, not the bar. The
form to carry forward is symmetrical: **the two surfaces enforce the same ACL, and whichever is
stricter is the correct one.** A curator's session is not a lower bar than an agent's credential,
it is a different one. That is never a reason to loosen the agent path to match.

- **`AGENT` token scope (SH106).** The credential an agent runs on: writes on
  `/api/v2/agent/**` and `/mcp`, reads drafts like `PREVIEW`, carries `ROLE_AGENT` and
  **not** `ROLE_CDA_WRITE`, on the CDA it stays read-only. That is the whole reason it is
  not an alias for `WRITE`: a `WRITE` key patches published content outside the agent
  protocol's guards. It extends the SH2/SH5/SH13 model (expiry, site allow-list, rate
  limit), so an agent credential can be scoped and expiring instead of a console password.
- **Session identity (SH106).** `ShHistory.agent_session_id`, the column SH75 deferred, and
  what makes SH109 a `where` clause. `owner` names only the person whose credential was used.
  Set by **`ShAgentSessionFilter`** from the `Mcp-Session-Id` header on the agent namespace,
  and by MCP's own `initialize`; **never minted per request**, a session is a *run*, and one
  session per call is the same as none. `ShAgentSessionContext` is the ThreadLocal (it used to
  be `ShMcpSessionContext`, in the `mcp` package).
- **The publish gate (SH107).** `api_token.may_publish`, **off unless granted**, enforced in
  `ShAgentBatchService`: which is also the apply and blueprint path, since a document
  compiles to `post.publish` ops. Checked **before the first write** and **on a dryRun too**,
  so an agent learns it cannot publish while planning. A console caller has no token and is
  unaffected. It is a tightening: a `WRITE` token that used to publish through the agent
  surface now needs `mayPublish`.
- **Confirm tokens (SH108).** `post.delete`/`folder.delete`/a deleting `prune` take two calls:
  a `dryRun` returns `confirm`, the real run sends it back. The digest is over **what the plan
  actually deletes**, not the requested ops, so a delete of something already gone is a noop
  that needs nothing and a document stays re-runnable (SH82). Absent is a **428** carrying the
  token and the list; wrong is a **409** naming what would go now. `ShAgentConfirm` owns it;
  the CLI's `applyConfirmed` does the two-step and prints the deletions *before* applying.
- **The review queue (SH109).** `GET /api/v2/review/sessions` · `/sessions/{id}` ·
  `POST …/approve` · `POST …/revert`, plus the console page at `/console/admin/review`.
  Console-authenticated and deliberately **not** on the agent namespace. It is a **view over
  the SH51 audit grouped by session id, not a proposal table**, the DRAFT row *is* the
  proposal, so approve = publish the run's drafts and revert reuses SH14 restore + the SH55
  trash, per entry, reporting what it cannot honestly undo instead of guessing. Since SH200 an
  entry may name a **folder**: reverting that one restores the whole deleted section, which is
  the gesture a curator means by "undo that", and `approve` skips folder entries because a section
  has no draft to publish.
  **`GET /api/v2/review/pending` is the navigation badge's read (SH207).** SH104's handoff answers
  "what did the agent do since I last looked" for whoever ran `shio report`; a curator who never
  opens a terminal had nothing. §IX.8 settled the *position* first, a banner on every page is
  noise, a banner only on the review page is a page nobody visits unless they already suspect
  something, so what shipped is an indicator in the sidebar, absent at zero. Its own endpoint
  because `/sessions` scans 2000 audit rows and resolves each post one at a time, which is fine for
  a page somebody opened and not for every console render; this one is two queries. It counts
  **runs**, matching the list it opens, and `changes` counts **distinct posts**. Neither equals
  `ShReviewSession.pending`, which counts audit *entries*, and which, because `publish` keeps the
  DRAFT row as the editable copy, **never decreases** (**SH615**). That is why this read asks the
  harder question: a draft with no published twin, or one modified after its twin was published.
  An indicator that stays lit after the curator acts is one they learn to ignore.
- **A deletion is recoverable at every level (SH200).** `folder.delete` and the console's folder
  delete put the subtree in the trash rather than erasing it, so the op SH108's confirm token
  exists to gate is no longer a *harder* delete than deleting each post by hand. What is still
  open, and worth knowing before relying on the undo: **the trash is console-only**, an agent
  can fill it and cannot list or restore it (**SH222**), and **deleting a whole site** still
  behaves the way a folder delete used to (**SH223**).

**Cascade safety is a fixture's problem too (SH289).** `ShPostTypeAttr` maps `shWidget` as
`@ManyToOne(cascade = ALL)`, so removing an attribute cascades a `REMOVE` into the **shared widget
catalog**. `ShPostTypeAdminService.detach` handles it in production; a *test* that mutates a
post-type needs the same three lines, and the one that did not deleted `Text` and
`Ace Editor - Javascript` for the whole JVM, surfacing as `Widget 'Text' is not seeded` thrown by
production code several steps from the cause.


## The sitemap's third answer to "is this live" (SH553)

SH545 gave `/agent/read` and `/agent/find` a `published` field and left the context pack as the
third surface with one word for two questions. `folderPosts` fetches both state rows, drops the
published one and reports `state` from what is left, so **a session that opened by reading the pack
was told every page of a fully published site is a draft.** P2 makes that the worst of the three
places for it: the pack is the call that replaces a session, so the agent learned the structure and
was misinformed about the state of everything in it.

**Free of queries here, which is the difference from the two surfaces SH545 fixed.** `/agent/read`
pays a PK lookup per row to answer this; the pack's walk was already fetching both state rows *in
order to discard one*, so the fact was in hand and being thrown away. `folderRows` keeps it.

**It was left out of SH545 on a measurement, not caution**, so the measurement came first here too:
two of that task's three designs were refused by benchmarks, an unconditional field cost
`addressing@scale` its floor, and a full convention sentence cost `discovery` its own. The sitemap is
the token-heaviest of the three surfaces (one node per post) and carries an SH116 digest, so a field
there changes what a cached pack compares against. Measured before and after: **`addressing@scale`
unmoved at 62 agent tokens**, because the field is conditional and only a published post pays for it.

**`Node` uses `NON_DEFAULT`, not `NON_NULL`, so nothing about SH545's shape transferred unexamined.**
Whether a `Boolean` false is omitted is a property of *this* record, and it is asserted rather than
assumed, on the object *and* on the serialized JSON, because the question the budget cares about is
what goes over the wire. A draft passes `null` and the key is absent entirely, so a site with no
published page pays nothing per row.

The fixture had to gain a published twin to make any of this observable, ten drafts and no live row
is a shape in which the sitemap's own defect cannot be seen, which is how it survived SH545.

## Inventory is not routing (SH601)

**The manifest was complete, and that is the uncomfortable finding**: every round that lost time to
discovery lost it holding a correct and complete list. Completeness was never the constraint. A
reader arrives with an *intent*, *this run failed with a 500*, *what conventions does this instance
already have*, and what the manifest offered was 57 booleans, 28 paths and a `limits` object.
Mapping the first onto the second was the work, it was unbudgeted, and it is where the errors were:
`diagnostics: true` is discoverable to a reader who already suspects diagnostics exist.

`startHere` listed and did not branch. It now does: two facts true whatever the intent, then
`then, whichever you need:` and six rows of **question → one call**.

**Keyed by the question, not the capability.** That is what keeps it routing rather than a fifth
registry (SH599 went the other way deliberately). A row named after a capability puts the mapping
back on the reader, and adding a row *because the capability exists* is exactly how this becomes the
thing it replaces. The set is closed and small, and three of the six are taken verbatim from rounds
that got them wrong.

**One table, two readers.** `ShAgentIntents` is in main and `ShAgentDiscoveryCostTest` reads it,
which was SH601's own instruction, build the table once, score against it, then route with it. A
benchmark whose intents were written separately would score a different set than an agent is told
about, and the first time they drifted the score would still be green. `ShAgentP2ConformanceTest`
asserts the other direction: every scored intent appears in the manifest a reader actually gets.

**Paid for by deletion, not by a raise.** Three of the five old lines are gone, "write with batch",
"then verify", and the addressing detail, because the routing now answers them per intent, and
saying them twice would be the inventory this replaces at the cost the budget refuses. Measured
after: manifest 2635/2650, **terse 2399/2400, one token of headroom**, which is recorded in
`token-budgets.properties` because this ceiling is now a wall for any new manifest line.

**SH230 took that wall down on the terse side: 2395 → 1890, and the ceiling to 2050.** `format=terse`
now cuts each endpoint's purpose to its **lead sentence**; the example stays verbatim. That split is
the whole finding. `ShAgentManifestTerse` held both "verbatim" under one rule whose stated reason was
that the caller *runs* it, true of the example, never true of the purpose, which is read. Holding
them to one rule is why terse was worth 1.1× on this document where the same encoding is worth a
third to a half everywhere else, and why SH230's own two candidates both aimed at the smaller half:
measured across the 28 entries, **purpose is 4001 characters and example is 1079**, so eliding the
13 derivable examples would have saved 3.6% against this 21%.

The lead is found by scanning to the first terminator that ends a word **and is at least 20
characters in**, because the usual next-word-is-capitalised heuristic is wrong here, half these
sentences are followed by `include=…` or `format=…`, lower case by nature. The guard errs toward
keeping the whole purpose: a genuinely short first sentence is not recognised and nothing is cut,
which loses a saving rather than publishing half a sentence as the purpose.

**The trim overshot and an instrument caught it within the hour.** Dropping the MCP line took the
eleven tool names out of what a reader can reach from one call, that line is the *only* place they
are published, and SH598's reachability walk failed with all eleven in its NOT-NAMED list. It came
back shortened rather than restored: the prose around the names carried no decision, and the names
are the whole point of it.

`ShAgentManifestTest` used to cap `startHere` at five lines, on the premise that a long list is a
plan and a short one an instruction. The premise was half right, and this is the correction, five
ordered lines were still a plan. The bound moved rather than being dropped: the **preamble** is
asserted at two lines, because that is what every reader pays for whatever they came to do.

## The pack says what it dropped (SH608)

SH600 closed this on the manifest; `/agent/context` took the same `include=` and had the same hole.
`?include=instance` returned four scalars, dropped the model, the sitemap and the conventions,
**including the remembered notes an entire SH597 intent is measured against**: and answered 200. A
caller narrowing a *discovery* surface is by construction one who does not yet know what is there,
which is why a silent projection misleads worse here than a 404 would: a 404 would have been read.

**Names, where the manifest reports counts.** A manifest is assembled and *then* projected, so
SH600's counts were already in hand. The pack is the other way round, a section `include=` drops is
never built, so counting it would cost exactly what the caller's projection saved, and the fix
would become an argument against itself (P3). A name is what tells a caller its question went
unanswered, which is the whole job.

**Built from the parsed section set, not from the assembled pack**, and that is the one thing that
could not be ported. The pack's sections are content-shaped, so after the fact a projection and an
empty instance are indistinguishable: `sites` is an empty list on a fresh instance *and* an empty
list when nobody asked. The manifest could derive its report because `features: null` and
`features: {}` differ; here only the request knows.

**Two fields, not one, that was the decision the task left open.** `budget.truncated` already says
"you did not get all of it". So does `omitted`, and they are not the same statement: `omitted` is
**the caller's own doing** and the remedy is to ask for the section; `truncated` is **the
instance's** and the remedy is a bigger budget or a cursor. One field would tell a reader the pack
is incomplete without telling them which lever is theirs, and someone who raised the budget to
recover a section they had excluded would get the same answer twice.

`manifest` and `ops` are opt-**in**, so they are never reported as omissions: naming them would tell
every default request that two sections were withheld, which is the opposite of the point. A whole
pack carries no `omitted` key at all.

**It rides after the digest**, like `budget`: it describes the *call*, not the content, so attaching
it must not change the entity tag or a narrowed pack becomes uncacheable for exactly the readers who
narrowed to save tokens. That is asserted on `withOmitted` rather than by comparing a whole pack
with a narrowed one, those two legitimately differ, because narrowing changes content, so that
comparison would have passed while proving nothing.

## The findings nobody narrated (SH602)

Twelve rounds produced roughly forty tasks and **every one came from the same place**: a session
ended, somebody read it back, and a pattern was noticed. That collector is expensive, lossy, and
only runs when someone asks, so it does not run at all for readers who are not this one. Nobody is
going to narrate a stranger's session.

`ShAgentFrictionLog` counts what readers **asked for and did not get**, and `diagnostics` reports
the top ten.

**None of it is a failure, which is why nothing recorded it before.** The incident log holds what
broke, a 5xx, a webhook that gave up. A 404 on a path an agent constructed is a *correct* answer; a
`didYouMean` that fired is the surface *working*. Each is the instance behaving properly while a
reader does not get what they came for, and together they are the whole discovery-friction record.
They are reported as notes beside the incidents rather than as a section of their own, so nothing
here reads as something having gone wrong.

**Keyed by what was asked for, not by what broke**: that is what makes the output evidence. A key
asked for often and answered never is a **missing capability with a name attached**, and the name is
the reader's own, which beats anything a transcript review produces because a summariser writes the
name *they* would have used.

**A `didYouMean` that fired is a different kind from one that did not.** "Asked for a near-miss of
something real" is a naming problem; "asked for something with no neighbour" is a missing
capability. Folding them ranks them together and answers neither.

**Recorded in one method**: the `@ExceptionHandler` every teaching refusal already passes through,
so a rule added tomorrow is counted without anybody remembering to count it. A collector that had to
be called at each throw site would have the same property as the one it replaces: it works until
somebody forgets.

In memory and bounded at 200 distinct names, on the incident log's terms: a counter that survived a
restart is a second store to migrate, and one that grew without a ceiling is a denial of service
keyed by a path an attacker chooses. The ceiling bounds **distinct names, not occurrences**, bounding
occurrences would freeze the ranking the moment it filled, and the most-asked name would stop being
the most-asked, which is the one thing the report is read for. Drops are reported, because a counter
that silently stops counting reads exactly like a quiet instance.

One correction worth keeping: the handler took the counter as a constructor argument, which broke
`ShTeachingErrorTest`: it constructs the advice directly to assert a problem document's *shape*.
Making a diagnostics aid a precondition of error handling is the wrong way round, and the no-arg
constructor is back.

## The second task shape (SH603)

The strongest evidence this project has about its protocol is twelve replication rounds, and they
are also its narrowest: one agent, one instance, one family of sources, and a corpus the same reader
helped design. That finds defects. It **cannot tell a protocol that is genuinely discoverable from
one a familiar reader has memorised**.

`ShAgentAuthoringCostTest` is a shape nothing was tuned for: build a small site from an **empty**
instance (no capture, no corpus, no proposal) scored with SH597's metric.

**Measured: 5 calls, 1053 estimated tokens** for the whole shape (read the pack, read the write
vocabulary, plan the batch, apply it, verify). A *single* discovery intent costs between 939 and
1438 tokens to reach its first correct move (the band was 950–3650 when SH603 ran; SH683
re-measured it after SH601). So the untuned shape is not more expensive than the tuned one, which
is the answer SH603 wanted: P2 is not an artefact of familiarity. The conclusion survives the band
narrowing by 2200 tokens, which is the stronger reading of it: the tuned shape got four times
cheaper and the untuned one did not have to.

**The first reading was 941 and was measuring nothing.** Two calls answered 400 and the last 404, so
the figure was the cost of *failing*, comfortably under any ceiling, and it would have shipped as
evidence. The benchmark now asserts every step's status **before** it asserts any budget, and checks
the site exists afterwards, because a batch's 200 does not prove a write happened: a dry run answers
200 too.

**What corrected it is the finding worth keeping.** Every fix came from the instance refusing the
first attempt and naming the remedy, `start-here` not `startHere` (with `didYouMean`), `folder`
required on `post.upsert`, and `Page` declaring `title`/`content` rather than `TITLE`/`TEXT`. That
is P6 and P2 working on the author of the benchmark, which is the closest thing to an outside reader
a file in this repository can have. It is also live input for SH602's friction ranking, which is
what those refusals now feed.

The slot it cost was declared: `ShContextCacheHeadroomTest` went 62 → 63 and
`spring.test.context.cache.maxSize` 64 → 68. This is the archetypal case SH315's rule says may not
share a context, another suite's content makes "from nothing" a statement about test order rather
than about the protocol.

## A post belonging to no site is not the problem of a site that asked (SH635)

`ShAgentVerifyService.readable(...)` appended **every site-less post in the instance** to every
scope, including a run that named a site. So a caller asking `verify?site=blog` was handed an
`error` about a post at an address in no site of theirs, carrying a `fix` they cannot carry out:
the post is not theirs to move. That is P6 inverted, an instruction the reader is unable to follow,
and P7, a report about content the caller never asked about.

**The rule was already written, one method away.** `folders(...)` says it plainly: *an orphan
belongs to no site, so no scope can name it, and a scoped run that swept them all in would report
content the caller did not ask about, while a full pass would still catch it.* Folders got it;
posts never did. The fix is `readable(sites, fullPass)`, with the full pass being exactly the run
that named no site. Nothing stops being reported, a `verify` with no `site=` still asks for these
rows by name, which is the only way they are visible at all.

### Why this took three red runs to find

It presented as a **flake**. `ShBlueprintApplyTest` asserts its blueprint site verifies clean, and
whether it did depended on whether another class sharing the context had left a site-less row
behind, which depends on suite order. Twice red, green on every re-run, and nothing about the
symptom pointed at scoping.

Three things closed it, in this order, and none alone would have:

- **The permanent capture.** SH635's earlier half prints every finding at that assertion on a
  failing run. It costs nothing on a green one, and it turned the third red from a number into the
  two `orphan-post` findings that named the cause immediately. SH354's point: the next `clean`
  removes `target/surefire-reports/`, so a red that is not captured *as it happens* is a red nobody
  can investigate.
- **An unrelated task's reordering.** SH613 added test classes, surefire's order changed, and the
  intermittent failure became deterministic.
- **An existing lint refusing the fix's own test.** The assertion needs `verify(null, …)`, an
  instance-wide read, the only call that can see an orphan, and `ShBenchmarkIsolationLintTest`
  caught it on the shared context (SH315). It now lives in `ShAgentOrphanScopeTest` with a database
  of its own, and `DECLARED_CONTEXTS` moved with it. That refusal was worth more than the assertion
  it blocked: without the second half, "never report orphans" would have satisfied the first.

## The moment the read surface could not reach (SH619)

`GET /api/v2/agent/read` was **draft-preferred and took no `state`**. SH545 added `published`, which
says *whether* a live row exists, the right field for that question. What no call could return was
the published row's **fields**, so a fact-level comparison of draft against live could not be
assembled at all: not expensively, at any price. The rendered live page is reachable (`/preview`,
`/sites/**`), so a *pixel* answer existed; the cheap one, which fields changed, the question
somebody asks before publishing, lived only inside the console's diff dialog, one post at a time,
in a browser, to a person.

### The decision: a parameter, not a diff endpoint

SH619 named two candidate shapes. `state=` won on P10: the comparison is **two reads of one address
at two moments**, which composes out of the read surface. A `diff` endpoint would be a third
contract needing its own MCP tool, its own budget line and its own conformance row, for an answer
callers can assemble. It also makes `state` mean what its own javadoc always said it meant.

**`state` names a row, and an absent row is a 404 that says which**: never a fallback to the
draft-preferred one. The fallback is the tempting implementation and the single worst answer
available: a caller comparing draft against live would be handed the draft twice and conclude
nothing changed, which is the exact wrong answer this capability exists to stop being unavoidable.
`no-such-state` distinguishes *never published* (publish it) from *has only a published row*
(an uploaded file, which has no draft to compare).

### It landed on MCP too, and the budget moved in the documented order

P1: a capability reachable only over REST is incomplete. `shio_read` gained `state`, which put
`tools/list` 22 tokens over. **The trim came first**, as it did for SH262 and SH342: the description
went 186 → 172 by dropping wording ("Pass `fields` to fetch only what you are about to change" →
"Project with `fields`"), which funded 14, and only the remaining 8 moved the budget line. Both
things a model acts on, draft-preference, and that an unprojected rich-text body is the expensive
case, still say why.

### Two findings the work turned up

- **`cli/src/commands/diff.mjs` was teaching a premise that stopped being true.** Its `--against
  published` refusal said the read "is DRAFT-preferred and takes no state (SH619)". Shipping the
  read without correcting that message would have left the CLI explaining a block that no longer
  exists. The message now names what remains, and the verb is **SH640**.
- **`assertion-debt.test.mjs` matches any `SH\d+` in a suite file.** A javadoc here named the
  console dialog's task in passing and took that id off the debt list without asserting anything
  about it, a false payment, the fourth. It is the JS twin of SH613, filed as **SH641**, and the
  rule there has to be narrower than "strip comments": an assertion message *is* a string literal,
  which is exactly where an id belongs.

## A plan writes nothing, and the database says so (SH221)

SH153 removed the last write from a blueprint plan, and its reasoning generalises further than the
fix did. `POST /agent/plan` and a `dryRun` batch write nothing today, but nothing **enforced** it:
the property was maintained by every author remembering it, and the last time it was not, **the
reason was good.** The planner needed to resolve a post-type the run would create, and a
rollback-only transaction was the obvious way to get one. That reason will recur, and it will be
just as locally defensible.

### The proposed fix was the right instinct and the wrong instrument

§XI.15 proposed wrapping the dry-run paths in a `readOnly` transaction. Trying to scope it turned up
two reasons not to:

**The entry points are shared.** All three `run(...)` overloads serve plan and apply alike, and
`dryRun` is a field of the request body rather than a different call, so the flag cannot move onto
the class, and dispatching to a second annotated method from inside the service is a self-invocation
that never crosses the proxy.

**And `readOnly` does not mean the same thing on the three engines this product supports.** Spring
marks the JDBC connection read-only and Hibernate stops flushing; **PostgreSQL refuses the write
outright, while H2 and MariaDB accept a connection that is merely *advertised* as read-only.** A
guarantee that holds on one of three engines is not structural, it is a flag that happens to be
enforced where CI runs Postgres, which is the shape of a rule people trust further than it goes.

### Counting the writes instead

`ShPlanWritesNothingTest` reads Hibernate's `Statistics` around each dry-run path and asserts the
instance inserted, updated and deleted **nothing**. That is engine-independent and states the
property directly, so the next author who reaches for a rollback-only transaction to resolve
something that does not exist yet fails by name, on every database.

Three cases, and the third is the one that makes the other two mean anything: a **real** apply must
move the counter. A statistics reader that counted nothing would pass both guarantees for ever and
report a property nobody has.

It gets its own database (SH315) for a sharper reason than usual: statistics are cumulative on the
`SessionFactory`, so a sibling test writing content on a shared context would be indistinguishable
from the defect. That is not added noise, it inverts the answer.

## The undo the protocol already promised (SH222)

`post.delete` answers `"to trash (restorable)"` on purpose, an agent that believes it destroyed
something will not offer the curator the undo. **That string was the only place the trash appeared on
the agent surface.** No read listed it, no MCP tool reached it, no CLI command restored it, while
`GET /api/v2/post-unified/trash` and its `untrash` twin are console endpoints. So the protocol told
a caller an undo existed and gave it nowhere to go, P1 inverted on the capability an agent needs
most immediately after a mistake, and the place "the console can do it" is least comforting, because
the mistake is usually noticed by the agent, seconds later.

### Both design questions, settled

**How much surface does it cost?** A verb on the existing vocabulary, not a new tool: `post.restore`
and `folder.restore` ride `shio_write`, so a restore costs no MCP schema, and the tool budget is
paid on every turn of every session while a restore happens once in a hundred. The listing is
`find?trash=true`, a shape over the read that already lists posts (P10) rather than a fourth read.

**Does a restore need a confirm token?** No. SH108's rule is that a *destructive* op needs the plan
it was shown; a restore destroys nothing and the thing it undoes is itself reversible. It is not
free (it can bring back content a curator deliberately removed) so it is **audited** (SH200's
`RESTORE` action) and answers with what came back. A token would put a round trip in front of the
one call somebody makes in a hurry.

**The listing forces the `id:` form**, and that was not obvious: a trashed post *keeps its friendly
URL*, so the address resolver still builds a path, one that does not resolve, because the post is
not live. Handing that back would give a caller a name that reads like every other row and answers
nothing. SH172's `id:` fallback exists for exactly this, and a restore refuses a path rather than
resolving it leniently: looked up, it would restore whichever **live** post happens to share it.

### Two defects found while building it

- **`ShTenantContext.getTenantId()` is null when nothing is bound**, and `findTrash` compares it to
  a column, so the first listing reported an **empty trash on a single-tenant instance**, the one
  wrong answer this call must never give. `ShPostUnifiedWriteService` already carried the
  null-to-default fallback; the new read did not.
- **A required key the vocabulary declares must be enforced the way every other op enforces it.**
  `ShAgentOpsContractTest` caught a bespoke refusal that the planner did not treat as one, the
  published list has to be the enforced list.

### It spent the vocabulary's remaining room

`ShAgentOpsReachTest` holds the op vocabulary under 600 tokens, with a message that says growth is
"a reason to re-open the surface choice, not to raise the number quietly". Two ops took it to
**640**. The raise to 680 is the loud kind: SH346's trade was the vocabulary *versus the manifest*,
and restore ops add to both sides equally, so that comparison is untouched, what changed is only
the size. **The next op should re-run the trade rather than move the line again**, which is
**SH647**.

### The folder half's plan did not read the trash (SH690)

`postRestore` reads the trash in its `dryRun` branch and says why, *"a plan that said yes about a
post nobody trashed is the plan an agent acts on."* `folderRestore`, one method below it, answered
`UPDATE "from trash, with its subtree"` **unconditionally**, so a plan promised a section restore in
three cases the write does not deliver: the id is not a trashed folder (`NOOP`), its **parent** is
still trashed (`NOOP`, SH200's rule), or a post beneath it has had its friendly URL taken, SH243's
409, which refuses the whole subtree. An agent that trusts the plan spends the confirm token to
reach a different answer, and that is precisely the round trip P9 claims the cheap textual proof
replaces.

Three decisions worth keeping:

- **The parent-trashed case keeps its own sentence** rather than folding into "not in the trash".
  It is a different instruction (the caller has something to do about it) and
  `ShReviewService.revertFolder` already prints that distinction; the wording is now shared with it
  verbatim rather than re-invented.
- **The plan raises SH243's refusal, it does not report it.** `ShFolderWriteService` now carries
  both forms of the same question: `restoreUrlConflict` (returns the reason) for the review queue,
  which must not *catch* a throw out of an inner `@Transactional` call, and `requireRestorableUrls`
  (throws) for the batch plan, which refuses ops the way every other op is refused. The write's own
  pre-flight loop is the private half of the throwing one, so the rule lives in a single place,
  the version that lives in two is the one that disagrees with itself the first time either moves.
- **All three checks run before `session.dryRun` is consulted.** That is what makes plan and apply
  one answer instead of two that drift: the apply path reaches the same NOOPs and the same 409
  through checks it was already running.

The three assertions carrying the id are in `ShAgentTrashTest`, and building them turned up the trap
worth naming: `run(batch, ids, actor)` nulls every `Change.id()` when `ids` is false, so a fixture
that trashes a folder and then addresses it cannot use the reply it just got. A trashed folder is
addressable *only* by id (SH222), so `ids = true` is not a preference on this path, it is the only
way to name what you deleted.

### `folder.restore` was the one op that wrote without asking the ACL (SH696)

Every mutating op in `ShAgentBatchService` calls `shContentAclService.requireWrite` before it
writes. `folderRestore` did not (`folderDelete` has since SH200 and `postRestore` since SH222) so
a credential scoped to part of a tree could bring back a section it could not have deleted, and a
restore carries the whole subtree's posts with it. That is the non-goal *"the agent path is not a
privilege escalation"*, and the fix is one line.

**SH690 rewrote that method four commits earlier and did not notice**, which is the finding rather
than the fix: a *missing* call is invisible to review, because there is nothing on the screen to look
at. So the deliverable is `ShAgentOpAclLintTest`, it reads the dispatch switch, extracts each
handler's body, and classifies **every** op in `OPS`. A fourteenth op arriving unguarded fails the
build instead of waiting to be read, and the run prints both counts, because a lint that reports only
what it dislikes cannot be told from one that found nothing.

**Writing it corrected the task's own premise.** The first draft looked for
`shContentAclService.require` and reported *three* unguarded ops, not one. Two of them,
`site.upsert`'s create branch and `site.delete`, carry `requireUnscopedCredential` instead, a
*stronger* check chosen on purpose: a credential scoped to a list of sites must not create or destroy
a member of it. Listing those as exempt would have recorded a guard as an absence, which is this
file's own mistake pointed the other way. The measured answer is **12 of 13 check authorisation, one
exempt**, `render.provision`, SH186's addressless op, which acts on no content object and has no id
an ACL could hang on.

The lint is paired with a behavioural assertion in `ShAgentTrashTest` rather than trusted alone: a
source scan cannot tell whether the guard is the *right* one. That test also asserts the folder is
**still trashed** afterwards, a refusal thrown after the write would be the same defect wearing a
403.

## A write proves its folders too, and says so when it proves nothing (SH228)

`ShAgentProofService` collects the `post:` **and** `folder:` addresses a run touched. It
was posts only, so a batch of `folder.upsert` ops came back with **no `proof` key at all**,
which a caller cannot tell apart from "asked for, and nothing was wrong". The lint has
always had folder rules (`empty-folder` is one of its warnings); the report existed and
was simply never asked for, and "scaffold the structure, then fill it" is a real first
step whose whole point is to be checked before content goes in.

**`address=` accepts a folder, and it is not `folder=`.** The two now mean different
things on purpose:

| Param | Scope |
|---|---|
| `address=folder:<site>/<chain>` | **that folder**, as a report subject |
| `folder=folder:<site>/<chain>` | that folder **and its descendants** |

A write proof names the folders a run created, and a just-created folder's subtree is
empty by construction, scoping to the subtree would make the proof's reach depend on
whatever happens to sit underneath, which is not what "prove what I just wrote" asks.
Before this, a folder address handed to `address=` resolved and was then looked up as a
post, found nothing, and was dropped silently while the scope still reported
`1 address(es)`.

**An empty proof is explicit.** `nothingToProve()` returns `ok` over zero objects with a
note saying *why* it is empty. The trap in that half, and the reason it is a method
rather than a fall-through, is that an empty scope must never reach `verify` as blank
params: **blank means the full pass**, so a run that wrote nothing lintable would have
linted the whole instance and reported findings about objects it never touched.

**What stays out.** A `site:` address has no bounded lint, proving a site *is* the full
pass, at a cost that scales with the instance rather than with the batch. And digests stay
posts-only: a digest is a real render, and a folder has no page to render.

**One thing to know before writing a test here.** `ShBenchmarkIsolationLintTest` (SH318)
refuses `verify(null, …)` in a class sharing a context, by call shape rather than by a
list, a scoped call still trips it if the first argument is `null`. Pass the site.

## An entity tag covers every parameter that changes the bytes (SH229)

`ShAgentEtagCoverageLintTest` walks every endpoint taking `If-None-Match` and fails when a
`@RequestParam` is missing from the tag. It exists because SH116 wrote the rule down
clearly and SH157 still found `include` missing from the context pack's tag three blocks
later, two projections of one unchanged pack sharing a tag off the same digest, so a
caller asking for `include=model` could revalidate against a full pack's cached copy and
**keep a pack with no sitemap in it**. Nothing failed, and nothing could have: any single
request is internally consistent, so the defect only exists between two requests that never
meet in one test.

**Three buckets, derived from the source rather than declared:**

| Bucket | Rule |
|---|---|
| `ShAgentBodyEtag` (6 of 9) | tag is the rendered bytes' hash, covers everything by construction. **Prefer this.** |
| Hand-built `ShAgentEtag.of(…)` (3) | every param must reach the tag's arguments, or sit in `EXEMPT` with a reason |
| Anything else (1) | the endpoint sits in `ENDPOINT_EXEMPT` naming where its tag comes from |

**A `digest()` call counts for nothing, and that is the design.** Coverage is traced
through local assignments, `Representation.of(format)` used as `representation.name()`
counts, but a digest is opaque. This is what makes the lint reproduce SH157 rather than
excuse it: `include` *did* flow into the pack, and the pack's digest *was* in the tag, but
the digest is taken **before** the projection is applied. A lint that trusted dataflow into
a digest would have called that bug covered.

The cost is that a parameter genuinely covered *through* the digest needs a hand-written
exemption. That is the right way round, an exemption is a sentence a reviewer can disagree
with; a silent inference is not. `context.budget` is the interesting one: it truncates the
walk, and the nodes it drops *are* content the digest covers, while the `Budget` record
describing the ceiling is attached after the digest so a narrower ceiling over identical
content stays the same representation.

**Two corrections made before it shipped**, both the same shape as the bug it guards:

- It filtered sources by `*API.java` first. A controller named otherwise would have been
  skipped in silence, and a lint with a blind spot reads exactly like a lint that found
  nothing. The header is the definition of a conditional read, so the header is the filter.
- `ShSourceScanLintTest` (SH613) refused it until it declared `@ShSourceScan(CODE)`, a
  javadoc paragraph explaining why a parameter is *not* in a tag is precisely the sentence
  a raw-text scan would mistake for the tag containing it.

## The headline benchmark ends at a page, not at a lint (SH396)

`ShAgentTaskBenchmarkTest.buildMarketingSite` was context → blueprints → apply → verify,
budgeted at 4 calls and 2700 tokens. Nothing in it published a template or fetched a page,
so the number P3 was defending described **content that passes checks**, not a site anybody
can open.

It now publishes the render model and ends at `GET /agent/render`, asserting
`source=rendered`, at least one heading, `landmarks`, and more than 50 words. A digest
alone proves nothing, a blank page has one too, and "it returned 200" is exactly the
evidence the old number stood on.

| | calls | tokens |
|---|---|---|
| agent | 6 (was 4) | 3674 (budget 3800, was 3400) |
| naive | 19 | 10289 |
| ratio | 3.17× | 2.80× |

**Both paths were extended, and that is the load-bearing decision.** The baseline now
hand-builds two template posts, binds `postTypeLayout` through the console site API, and
publishes each in order. Extending only the agent path would have *lowered* the reported
advantage while the agent did strictly more work, this task's own defect pointed the
other way. A ratio between two paths doing different jobs is not a ratio. Neither ratio
floor had to move.

**Publishing the templates is the step, not publishing the pages.** Since SH280 a
published render needs a published layout, and everything the blueprint writes is a draft
because publishing is never a default (P7). The agent path publishes theme, layout and
pages in one batch, they commit together, so order inside it does not matter. The
baseline has to get the order right across separate calls, and a session that publishes
pages first meets a 409 it must diagnose. That turn cannot be recorded here:
`ShAgentBenchmark.requireAllSucceeded` refuses any call ≥400, correctly, since a cheap run
that was really a 500 is not a measurement.

**Two understatements are declared rather than fixed**, both flattering the baseline:

- The baseline reuses `Theme` and `PageLayout`, post-types are instance-wide and the
  blueprint already provisioned them. There is no second instance to charge it on.
- It stops one call short of opening the page, because a console-created post comes back
  with **no friendly URL** (SH651), every one lands at its folder's address. That is not
  a layout problem and it answers 201.

The naive path also got **its own blank site** in the fixture. It used to build into the
shared one, which was fine while it only created content; it now binds a layout, and doing
that to a shared fixture would change what every other task in this class measures.

## The op vocabulary's cost, decomposed (SH647)

`ShAgentOpsReachTest` prints the breakdown on every run and asserts two things instead of
one total:

```
  total 640 in 13 ops = 49.2 per op
    header/grammar prose    54
    op + keys (the shape)   88  (6.8 per op)
    purpose prose          498  (38.3 per op)
  the manifest alternative: +640 on a 2650 manifest = 24% for every session
```

**Why one total was not enough.** SH346 set the ceiling at 600 with "room for two or three
more ops"; SH222 spent it and raised to 680. Both measured the *sum*, so "the vocabulary
grew" and "the vocabulary gained ops" could not be told apart, and those argue for
opposite things. More ops is the surface doing its job. Fatter prose per op is drift.

**The answer: it is prose.** 498 of 640 tokens are the per-op sentences, 88 are the keys.

**And the trade still holds.** SH346 chose this vocabulary over folding it into the
manifest because the manifest is paid by every session, writer or not; 447 was a 19%
regression then, and 640 on today's 2650-token manifest is **24%**. Still a regression, so
the surface choice stands.

**No trim was made, deliberately.** The obvious move from "78% is prose" is to cut prose,
and it is wrong here: those sentences carry facts nothing else on the surface carries,
which ops need `confirm` from their own dry run, that unknown `data` keys are refused,
where an `id:` address comes from. Cutting them to defend a number is P3 taking from P6.
The guard is on the **average** (< 42) so one op may need two sentences and every op may
not.

**What the two assertions replace** is an obligation on a future task, that whoever added
the next op would remember to re-run a hand comparison. They would not have. Both sides of
the trade are now measured every build, so adding an op is an ordinary act again and the
surface choice re-opens itself when it stops being true. The 680 total stays, unmoved.

## Counting a page is not describing it (SH687)

Two defects were visible in one glance at the `www.viglet.org` replica's home page: the captured
footer renders **above** the first section, and the accepted sections are presented by the Shio theme
while the chrome keeps the captured one. Neither was expressible in anything Shio returned.

`landmarks` is a histogram (`footer×1 header×1 nav×1 section×9`) which is equally true of a page
whose footer is last. `digest` promises an outline and gives the heading tree, which excludes a
footer because footers carry no headings. `classes`, `definedClasses`, `unstyledClasses`, `diff`'s
link and character counts, `annotations`' ratio: every one is a count or a set. `audit` alone holds
geometry and returned **0 errors** there. Both defects are positional, one is order, one is
attribution, and the appearance surface could *measure* a page without being able to *describe* it.

### `regions`, beside `landmarks` and in neither hash

`GET /agent/digest` on a **rendered** page now carries `regions`: the same landmarks in document
order, each saying whether an editor owns its markup (`sections`) or the conversion kept the source's
(`verbatim`), and how many undefined classes it draws with. Absent for a content digest, for
`appearance`'s reason, a post's own markup field is not a page.

The histogram stays. It is in the structural hash that lets two runs be compared by string (P5),
`snapshot` diffs a source against a replica with it, and `audit` reads it as roles: replacing it
would change what a digest *means* to three readers in order to add a fourth. `regions` is in
**neither** hash, for a third reason, it is the same structure `landmarks` already hashes, so
folding it in would move the digest for pages whose shape did not change, which is exactly the
discrimination the two existing hashes protect.

### What it says about the replica, measured

```
landmarks: footer×1 header×1 nav×1 section×9

regions (document order):
  header      1      verbatim  4 unstyled
  nav         2      verbatim  0 unstyled
  section     3      verbatim  1 unstyled
  footer      4      verbatim  4 unstyled
  section×8   5-12   sections  0 unstyled
```

The footer is region 4 and the authored content starts at 5, **the footer renders above every
section**, which is the finding, and it took a screenshot from a person to see. Two things the filed
section had not predicted also fall out: `section×9` was one *verbatim* section plus eight authored
ones, which the histogram merged into a single number; and every undefined class is in the captured
chrome, none in the authored sections, so the one warning that speaks about styling now points at a
half of the page rather than at the page.

### Jsoup here, regex in `ShHtmlOutline`

Not an inconsistency. The outline parses by regex because nothing it produces needs tree fidelity.
This needs precisely what that trade gives up, **containment**: "is this landmark inside an authored
section" is a question about ancestry, and answering it by counting tags is how a scanner attributes
a region to whichever marker it happened to follow. `ShAgentRenderService.markersOf` already parses
the same rendered page with Jsoup, so this adds no dependency and no second dialect.

`styled by`: naming the theme in force per region, and warning when two are, was deliberately not
part of SH687: it needs class-to-stylesheet provenance, which `definedIn` did not keep. That was the
second defect, filed rather than guessed at, and it is the section below (SH694 kept the provenance,
SH765 printed it).

## The theme in force, per region (SH694)

SH687 made the first of the replica's two positional defects sayable, the captured footer renders
above every section. This is the other: the accepted sections are presented by the Shio theme while
the chrome keeps the captured one, two visual languages in one document.

Nothing could state it because `ShHtmlAppearance.definedIn` concatenated every stylesheet into one
set of names. That answers *is this class defined* and destroys *by which sheet*, which is the only
form of the question that distinguishes one design from two.

### The fact on the digest, the judgement in `verify`

Each `regions` entry now carries **`styledBy`**, which of the page's own stylesheets styles anything
it draws, keyed `style[1]`, `style[2]` by document position. Position and not a friendly name: a
rendered page does not say which block is the captured theme and which the Shio one, and a label the
reader cannot check is what SH689 spent a task removing.

`verify` reads that field and warns `two-themes`. The split is the one `page-unstyled` already uses,
a digest describes, the gate judges, and it is what makes the warning able to fail a run.

**And SH765 is that field reaching the reader.** SH694 put `styledBy` on the wire and `shio digest`
dropped it, so the printed table still named a region's *markup* origin and never the stylesheet in
force over it, the finding had to be reassembled by hand from the layout template, which is where
this whole line started. The table now carries it as a fourth column, and the run key grew a third
component to match: contiguous regions collapse only when they share a tag, a provenance **and a
painter**, because two adjacent sections painted by different sheets are the two-visual-languages
finding and a row that merged across them would hide it. Measured on the replica's home page:

```
regions (document order):
  header      1      verbatim  4 unstyled  style[2]
  nav         2      verbatim  0 unstyled  style[2]
  section×8   4-11   sections  0 unstyled  style[1]
  footer      12     verbatim  4 unstyled  style[2]
```

The chrome and the sections share no sheet, which is the answer to *"the authorable part has none of
the site's formatting"*, SH510 behaving as designed, and also the defect as experienced. When no
region names a sheet the column is **omitted** rather than filled with dashes: an empty `styledBy`
means the caller supplied no sheet map, and a dash down the page would assert that nothing styles any
of it (SH694's absence-is-not-a-claim rule, one reader along).

**And SH829 is that column naming what it points at.** `style[1]` and `style[2]` are ordinal
positions, and nothing the digest returned mapped them to anything, so a column added to end a
lookup ended in one: both sheets are ordinary `Theme` posts with titles, and the reader had to make a
second call to learn which ordinal was the captured theme. The `<style>` element now carries
**`data-shio-theme`** with that title, written by the two places that emit a themed sheet, the
converted layout's `<style data-shio-theme="{{theme.title}}">{{theme.css}}</style>` and
`CAPTURED_STYLE_HTML`'s `{{title}}` off the `getRelation` row, and `definedBySheet` keys on it:

```
regions (document order):
  header      1      verbatim  4 unstyled  VigR3 (captured)
  section×8   4-11   sections  0 unstyled  VigR3 (Shio)
```

The name is **read off the page**, not invented, which is the property SH689 was protecting when
SH694 chose the ordinal. Three rules keep it honest: a `<style>` with no attribute is nobody's post
and keeps `style[N]`, the difference between a sheet somebody can edit and one nobody can; a title
two sheets share sends *both* back to their indexes, because half-naming them restores the second
lookup in disguise; and the title travels as an **attribute**, not HTML's own `title` on `<style>`,
which would declare an *alternate stylesheet set* and stop a browser painting with both.

**The finding is disjointness, not "two sheets exist".** Almost every real page carries a reset and a
theme; a rule that fired on those would be the next thing nobody reads. It fires when the chrome's
sheets and the sections' sheets have *nothing* in common, and a test pins the negative case.

A **warning**, not an error: two stylesheets with nothing in common is how a mid-migration site
legitimately looks, and finishing it is the curator's call. The instrument's job is that they are
told rather than left to notice.

### The cost that had this filed as undesigned was measured, and it was not there

This sat 💭 because keeping a set per sheet looked like it multiplied memory. On 1.02 MB of CSS
across two sheets:

```
single set   12,458 names  3.5 ms  1,377 KB
per sheet    12,458 names  3.5 ms  1,322 KB
```

Same walk, same names, slightly *less* heap. The names do not multiply, they **partition**; a class
costs twice only where two sheets both define it. The concern was written into the task by the person
who filed it (me, one round earlier) and did not survive being measured, which is the ordinary
outcome of applying *measure before you fix* to a premise rather than to a defect.

## The read half is spoken over the wire now (SH707, SH708)

**SH707 is the count, and it exists because "write six suites" is unmeasurable without one.**
`cli/test/live-coverage.test.mjs` joins three registries, the MCP tools, the CLI command directory
and `ShAgentBatchService`'s op constants, against what `cli/conformance/` *calls*, and holds the
rest as a named list. Both numbers §XXIII.1 stated were wrong: twelve tools and thirty-one verbs,
not thirteen and thirty-three. Every figure is derived now, so that is the last time either is typed.

**Exercised means called, and the distinction is the instrument.** The first reader counted any
occurrence under `conformance/` and reported five tools covered, three of the five were prose. A
mention is what a comment does; the claim being measured is that a client had to guess the name,
spell the schema, frame the request and read the refusal.

**Then the same strictness bit the other way, which is the useful half of the story.** SH708 drove
all eight read tools through a local `call(name, args)` wrapper, and a reader keyed on `.call(`
credited none of them: coverage still read 2 of 12 with the suite written and passing. The receiver
is optional now. Call syntax with a literal name is still required, and `shio_write`, named in
prose, called nowhere, is the asserted proof that the looser rule did not become a lax one.

**`mcp-read.conformance.mjs` (SH708)** seeds a scratch site through the batch surface and drives
`shio_context`, `shio_read`, `shio_find`, `shio_changes`, `shio_digest`, `shio_verify`,
`shio_assets` and `shio_marketplace` over `shio mcp`. What it can be wrong about and MockMvc cannot:
the declared input schema an agent fills in from `tools/list`, the bare `post:<site>/` home address
SH433 is about, a `shio_changes` cursor that has to survive the next call, and a `find` over two
rows seeded to collide on one term, a single match proves nothing about SH131's `%`-wrapping.

Live tool coverage went **2/12 → 8/12**. What is left is exactly the write half: `shio_write`,
`shio_apply`, `shio_publish`, `shio_remember`. Nothing that changes anything has ever been spoken
over this transport, and the op table says the same from the other end, every op that **removes**
or **moves** is still unexercised, which is where SH202, SH203 and SH243 each found a defect.

**Not covered:** the suite asserts that a tool answers and that its answer is about the content
seeded, not that every field of every projection is right. A response schema per tool is SH712's.

## The meter counted nothing on every default install (SH761)

SH45's meter had unit tests calling `record` and `flush` directly, and they passed. The first live
run said this, after five agent calls against a clean instance:

```
shio-usage tenant=shio period=2026-08-15
calls agent written=0 pending=0
```

**Zero, while naming the very tenant it had counted nothing for.** `shio.multi-tenant` is `false` by
default, so `ShTenantFilter` is a no-op and nothing is ever bound to `ShTenantContext`. The reading
side already fell back to the reserved `shio` tenant; the writing side read `getTenantId()` raw, got
`null`, and `ShTenantApiCallMeter.record` dropped every call, correctly, by its own rule that an
unattributable call must not be billed to anybody.

**The asymmetry was the defect, and the third copy of the fallback is what let it happen.**
`ShTenantContext.currentOrDefault()` is now the one place that decides, and the filter and
`ShAgentUsageService` both use it. `getTenantId()` keeps answering `null` when unset, which is right
for a reader that must tell "nobody" from "the default".

**The trade, said out loud:** in multi-tenant mode a counted request with nothing bound is now
attributed to the reserved tenant rather than dropped. That state is reachable only if the tenancy
gate let a request through without resolving one (which would be a defect elsewhere) and
attributing it to `shio` makes it visible where dropping it made it silent.

**Why only a live suite could find it.** The filter runs at `LOWEST_PRECEDENCE - 30`, *inside* the
Spring Security chain, so the tenant, the chain order and the flush are all things a running
instance arranges and MockMvc does not. `metering.conformance.mjs` reads the document, sends calls,
and requires the number to move, and it is also the first live read of any MCP resource
(`shio://usage`), which SH760's population had just opened at zero of eight.

This is SH751's shape one subsystem over: complete everywhere except the line that connects it,
with every surface reporting success.

## The relation that took an address and meant nothing by it (SH767)

`Page.sections` stores site-relative friendly URLs, and SH79 makes `post:<site>/<url>` the currency
of every other part of this protocol. So writing the relation the way the rest of the surface is
written:

```json
"sections": ["post:VigR2/sections/vb-top", "post:VigR2/sections/vb-mid"]
```

was accepted, reported `changed: true`, and stored **verbatim**. The renderer resolved none of them
and the page served with no sections at all.

**The failure mode is the expensive kind: it looks authored.** The post exists, its state is
PUBLISHED, the route answers 200, the digest lists the layout's own landmarks. The only symptom is a
curator opening the page and finding the content gone.

### Resolve, because the op already does exactly this one field over

An op's `folder` has taken either form since it shipped:

```java
op.folder().startsWith("folder:") ? ShAddress.parse(op.folder()).path() : ShAddress.normalizePath(op.folder())
```

A relation is the same question about a different field, so `post.upsert` now gives it the same
answer. The resolution is **purely syntactic**, `post:<site>/<url>` carries the URL, no lookup,
which is what makes it safe inside a batch that creates the target three ops later, and idempotent:
a second write of the address form normalizes to the same URL and is a NOOP (P5). It runs *before*
`sameData`, which is what makes that true.

Three forms are refused instead, each with the sentence naming what the field holds (P6):

| Sent | Why |
|---|---|
| `post:<other site>/<url>` | a relation resolves inside one site |
| `folder:` / `site:` | does not name a post |
| `id:<uuid>` | resolves perfectly well, and would put a UUID in a content file, SH79's whole point |

A value with **no scheme** is left exactly as sent. It is a URL; whether it resolves is `verify`'s
question, and refusing here would make a page unwritable until every section it names exists, the
opposite of how a conversion builds one.

### What the measurement found that the task did not say

The task was filed saying `verify` stayed green on the replica. It does not: a probe writing the
address form into a declared relation reports `dangling-reference` today, before any fix. The quiet
run had a different cause, and it is the more serious one, **the instance's `Page.sections` carried
no `references: post` at all**, so the field was invisible to the lint, to the digest and now to this
write check, all three of which key on that declaration. `ShVocabularyPostTypes.ensure` provisions
and never upserts (SH152, deliberately, a curator's added field must survive), so an instance
carrying an older `Page` keeps the blind field forever. Filed as SH773.

## The declaration three readers agree to need (SH773)

SH767 was filed on a replica where `shio verify --site VigR2` answered `ok` over a page whose
sections resolved to nothing. **Measuring it before fixing it found the report already right**: a
probe writing `post:<site>/<url>` into a *declared* relation reports `dangling-reference` today,
with no fix applied.

So the quiet run had another cause, and the same probe found it, written against a `sections` field
carrying **no** `widgetSettings`, verify says nothing. Not about the address form, and not about a
section that genuinely does not exist.

`ShPostReferenceFields` is the gate. SH154's rule, a `Multi Select` is tags unless the model says
otherwise, is right, and it makes `{"multiselect":{"references":"post"}}` the one fact **three**
readers depend on: the dangling lint, the SH101 digest, and SH767's write check. A type that has
lost it is not broken content; it is content that **cannot be checked**, which is worse.

### Why an instance gets into that state and cannot get out by itself

`ShVocabularyPostTypes.ensure` **provisions and never upserts** (SH152), deliberately: a curator who
added a field to their `SectionHero` keeps it, and that is what lets a conversion re-run against an
edited site. The cost is that an instance carrying a `Page` from before the key existed keeps the
blind field forever, and nothing said so. Every development box that ran the replication chain early
is in that state, which is why the replica measured clean.

### The rule, and what it deliberately does not report

`vocabulary-drift` is an **error** in the `content` group, scoped to the types the scan's posts
actually use, from data already loaded. It reports only the **silent** drift: a field the shipped
catalogue declares as holding post references whose live copy does not.

- A **missing** field is already loud, SH168 refuses the write naming it.
- A curator's **extra** field is the thing SH152 exists to protect.
- Every other difference from the catalogue would report a Combo Box somebody legitimately
  re-worded, on every run, the noise SH551 and SH142 both warn about.

The fix it names is one a caller can perform: `PUT /api/v2/post-type/<name>`. `render.provision`
cannot do it, and the refusal says so, because reaching for the provisioning op is the obvious wrong
move.

### The fixture forces the state rather than assuming it

`ShBlueprintApplyTest` and `ShVocabularyPostTypesTest` both provision a correct `Page` into the test
JVM's database, so whether the SH773 fixture *can* create a blind one depends on class order, and a
test that is right because it sorts first is what SH770 was about. It blanks the one String column
and restores it in a `finally`. Safe where removing the attribute would not be: SH289's cascade
reaches the shared widget catalogue through `ShPostTypeAttr.shWidget`, and nothing here touches it.

## A race test that was discarding the race it found (SH762)

`mcp-concurrency.conformance.mjs` went red once and green minutes later on one build, saying
`0 of the two creates were refused`. The obvious reading, a slow machine failing to make two
requests overlap, was wrong, and the measurement is what said so.

The helper was called `raceForADuplicate` and it looped **until it saw a duplicate**: two posts on
one URL, which is the defect SH719 was demonstrating *before* SH719 fixed it. The unique index made
that unreachable, so the exit condition could never fire. The loop ran all five attempts every time
and the caller asserted a refusal against **whatever the last attempt happened to be**, discarding
a genuine contention on attempt 1 and then failing on attempt 5.

**Measured after the fix: contention on attempt 1, three runs out of three.** Racing was never the
rare part; stopping when it happened was the missing part.

So the exit condition is now the one the caller needs (**one of the pair refused**) and the helper
is `raceUntilContested`. Two consequences worth keeping:

- **SH719's guarantee is asserted on every attempt**, not just the one returned. That check ("one
  post at this address, whatever the interleaving") holds whether the writes contended or not, and
  checking it only on the last attempt was throwing away four observations per run.
- **An uncontended run reports and does not fail.** Whether two HTTP requests overlap is a fact
  about the machine, and a suite that calls a slow box a broken product is one somebody switches
  off. The duplicate half still ran on all attempts, and the log says which half was exercised.

The general shape, which is the reusable part: **a test whose assertion has a precondition it does
not control must separate the two.** Asserting anyway is how a condition that never arose reads as a
guarantee that broke.

## A tool that advertises an argument reads it (SH952)

`shio_write`'s schema declared `when` ("post.publish: ISO-8601 instant to schedule for") and its
`call` built every op through `ShAgentBatch.Op`'s **six-field** constructor, the form whose own
javadoc says it is for the ops that never schedule. So five fields arrived as `null`. Over MCP,
which is how most agents write, **a publish dated for next Monday went live now and answered
success**, a wrong answer rather than a refusal, which is the worse of the two. `expect` (SH721's
concurrency guard), `locale`, `translationOf` (SH348) and `url` (SH467) were lost the same way. The
REST batch deserialises all eleven, which is why nothing looked wrong from the console, and the
golden `tools/list` only ever recorded the schema, so the one artefact naming `when` was the one
that could not tell whether anybody read it.

**`ShMcpDeclaredArgumentsTest` is the instrument, and it landed first (SH338).** An argument
silently dropped is invisible from both ends: the caller sees success, the schema still advertises
it, and no test passes the argument *because* nobody suspected it was ignored. The declared side
comes from `tools/list` itself (the artefact a model reads, so the only authoritative list) and
the read side from the tool's own source.

Two things it got wrong first, both worth keeping:

- **A mention is not a read.** The first version searched the source for the name as a literal and
  **passed on the defect it was written for**: the schema declaring `when` is in the same class, so
  it was comparing a declaration against itself. The pattern is the *accessor*.
- **There are two accessors.** Matching only `ShMcpArgs.<kind>(node, "name")` reported
  `shio_apply.publish` as a false positive, it is read by `post.get("publish")`, in a helper that
  must tell absent from `false` because omitting the field means "leave alone". A reader knowing one
  form would have cost somebody a rewrite of correct code.

`EXEMPT` is empty and should stay that way: an entry is a tool whose schema promises something its
handler never looks at.

## A tool declares what it reads (SH1073)

SH952 left `shio_write` reading `locale`, `translationOf`, `url` and `expect` with none of them in
its schema, so **no MCP client could learn they exist**, `tools/list` is the one document a model
reads before calling, and a caller who never learns an argument exists never reports its absence.
`ShAgentP1ConformanceTest` was counting `locales` as MCP-reachable on the strength of two of them.

**The other direction is an instrument now, and it landed first.**
`sh1073EveryArgumentAToolReadsIsDeclared` walks every tool's `ShMcpArgs.<kind>(node, "name")`
calls against its `inputSchema` and holds the difference as a named list, `UNDECLARED`. Its first
run found exactly these four across twelve tools. Only the accessor counts on this side: the raw
`.get("x")` that SH952's direction accepts also reads service results, which are not arguments.

**`expect` needed its source.** SH721's digest came from `read?include=digest`, which is REST;
`shio_read` could not ask, and `ShAgentReadTerse.docs` dropped the field even when the JSON carried
it, so a terse REST reader asking for the digest did not get it either. Declaring `expect` alone
would have advertised a guard whose only input MCP cannot obtain. `shio_read` takes `digest`, and
the line renders it, absent unless asked.

**The budget, measured.** The five arguments cost ~99 tokens. Cutting `shio_write`'s wording to its
decisions paid ~54, the verb list the `op` enum states, the `op` description the enum *is*, a
clause from nearly every field, and `mcp.tools.list.total` went 2560 → 2640 for the rest, keeping
two arguments of headroom. `shio_write` sits at 458 of 460 and is on `AT_THE_WALL`: the next field
there is a conversation about the per-tool ceiling. The three create-only fields say **"On create"**
because an upsert that finds the post ignores them, and a relocalization would otherwise read as
success.

## The change feed, pushed to the screens that are open (SH967)

Nothing in the backend streamed. An agent reads the feed by polling `GET /api/v2/agent/changes`
with a cursor, the console did not read it at all, so a curator looking at a folder while an agent
wrote to it saw nothing until they navigated. **`GET /api/v2/events`** is the same feed as Server-Sent
Events, for a screen that is open; the agent's polling read is unchanged, because an agent has a
cursor and a turn to spend it on and a held connection is the wrong shape for that.

**A shape over the feed, not a second one (P10).** `ShEventStreamHub` holds one subscription per
open stream and a pump (`shio.events.poll-millis`, 1s) reads `ShAgentChangesService` for each,
the same rows, ordering guard (SH147) and site scope. One event is one **page**, named `changes`,
whose SSE `id` is the feed cursor after it: a page is what the cursor positions, so a reconnect
sending `Last-Event-ID` resumes exactly, and an id that positioned less would have to be invented.
`since=` does the same for a client that cannot set the header, `site=` scopes like the feed's, and
with neither the stream starts at the edge. A comment heartbeat goes out after
`shio.events.heartbeat-millis` of silence, and a connection is closed after `timeout-millis` so the
reconnect re-checks the session.

**Whose rights a pass reads with.** The pump runs on a scheduler thread, and two checks this depends
on fail **open** there: the feed reads "no token" as an unrestricted console read, and the content ACL
grants anything to no authentication. So the tenant and the `Authentication` are captured on the
request thread when the stream opens and bound around every pass (`ShTenantHandoff` for the tenant).

**The ACL is applied here, and the feed does not apply it.** The feed scopes by site; SH54's grants
are per folder. Each entry is read with its id, checked with `canRead`, and the id removed before
it leaves (SH121). A withheld entry still moves the cursor, or the stream would stop at it for ever;
a page whose every entry was withheld sends nothing, so a reconnect re-reads (and re-withholds) it.

**Measured before relied on** (`ShEventStreamCostBenchmarkTest`, statements not milliseconds): an
idle stream costs **4 statements per pass**, per open tab, every second; a fan-out costs **7.05 per
entry per stream** for a reader who is not an administrator, 100 writes to 50 screens is 35,250
statements in one pass, most of it the ACL's folder chain walked again per entry and per stream.
Both ceilings are in `token-budgets.properties`, at the measured value rounded up. The benchmark
needed its own datasource, so SH423's gauge went 66 → 67.

**SH1077 took the fan-out to 2.11** (10,552 statements for the same 100 × 50). `pump()` keeps one
`ShContentAclService.ReadMemo` per tenant for the pass and calls `canRead(id, memo)`. The memo holds
the ACL's *inputs*: where an object's chain starts, each folder's parent, each level's ACL rows, and
each username's principals. `hasPermission` still makes the decision, so the stream cannot disagree
with a REST read. It is per tenant because every row in it is tenant-scoped, and per pass because a
memo that lived longer would keep answering after an ACL changed. The principals are keyed by
username, so two readers sharing a memo still get their own answer (`ShContentAclServiceTest`).
What remains is the feed page each stream reads for itself.

**An open stream holds no connection (SH1165).** Boot's open-in-view, on by default, bound an
EntityManager to the stream's request, and the connection its opening read took stayed held until
the async request ended: on disconnect, or thirty minutes later. Ten open console tabs were the whole
default pool, and every other request, sign-in included, then waited thirty seconds and failed.
`spring.jpa.open-in-view=false` turns Boot's registration off, and `ShEventsOpenInViewConfig`
registers the same interceptor with `/api/v2/events` excluded. Every other request keeps the session
it had, and some reads still rely on it. Declaring the interceptor as a bean also backs Boot's off
(`@ConditionalOnMissingBean`), so flipping the property back on does not restore the leak. Measured on
a pool of 4: ten streams used to starve the pump; after the fix, thirty streams leave reads at about
50 ms. `ShEventsOpenInViewTest` asserts on the handler chain the dispatcher builds for each path.

**`shio changes --follow`** holds the stream open over the CLI's Basic auth (a `Key` token is not
accepted on this path, and the agent's read does not need it to be), reconnects from the last event's
id with a backoff to 30s, ends on a 4xx rather than retrying an answer that will not change, and with
`--json` writes one line per event.

**Not covered:** the console does not subscribe yet, Block AX's dock is the consumer. A scheduled
transition's outcome (SH969) and a form submission's arrival (SH970) are on the feed as it is, not on
channels of their own.

## A schedule's outcome, on the feed the curator's screen reads (SH969)

`ShPostScheduler` recorded a failed publish in the incident log and a success in nothing a person
reads, so the curator who set the date found out from a customer. Both outcomes were in the feed
already, but in forms nobody could act on. The success is the `PUBLISH` (or `UNPUBLISH`) row the
publish writes, `by: scheduler`, with the post's address and the instant. The failure is a
`schedule` incident whose subject is `id:<uuid>`, which names no page a person could open.

**A failure now says how to try again.** A `schedule` incident whose post still exists carries
`retry: {op, address}`. That is one entry of `POST /api/v2/agent/batch` (`post.publish` or
`post.unpublish`), and the address is what `shio_publish` and `shio publish` take. There is no retry
endpoint: retrying is the publish a caller already knows, with its ACL and its gate (P10). The address
is resolved when the feed is read, not when the failure was recorded, so a post renamed since then is
retried where it lives now. The terse feed prints it as a `retry <op> <address>` line under `fix`, and
`shio changes` and `shio report` print it as the command to type.

**A publish after the scheduled instant is the scheduled publish.** A failed sweep rolls back the
transaction that cleared the schedule, so the schedule stays due. A curator's retry published the page,
and then the next sweep published it again as `scheduler`, sending a second webhook. `publish()` and
`unpublish()` now clear a schedule that is due. A schedule still in the future is left alone, because
"publish now, and again on Friday" is something a curator can mean.

**The stream withholds an incident about a post the reader cannot read.** The retry carries the
address the change rows were withheld for, so `ShEventStreamHub` applies `canRead` to any incident
subject of the form `id:<uuid>`. Subjects that are not content (a URL, a request path, a tenant) are
sent as before.

`ShScheduleOutcomeFeedTest` plants the failure the way one happens: the page is scheduled at an address
that another page still serves live, so SH719's unique index refuses the publish inside the sweep.

**Not covered:** the dock, a failure as a report with *open* and *retry* actions, a success naming the
page, and an unread report at next login for a curator who had no screen open, waits for SH964's
mounted assistant.

## A form submission's arrival, said as one (SH970)

A submission is a post (SH633), so its arrival was already on the feed: a `CREATE` in the form's
folder, `by: form-token:<id>`. Nothing else said it was a submission, and a report needs to tell a
lead arriving from a post a curator made. Parsing the actor string would have been guesswork.

**`submission: {title, summary}`** is present on exactly that row: a `CREATE` of a post whose actor
`ShFormSubmitter.isSubmitter` recognises. `ShFormSubmitter` also spells the actor for
`ShFormSubmissionAPI`, so the test and the spelling cannot drift apart. The two fields are the ones the
post-type marks `isTitle` and `isSummary`, read from the post as it is now, and they are what a report's
caption is built from. `summary` is absent when the type marks none or the field is empty. Every other
row omits the field, the rule `affects` follows. Only a submission's `CREATE` pays for the post read.

A tripped honeypot writes nothing, so it reaches no feed, no stream and no report: the pretence that it
succeeded stops at the response. `shio changes` prints a `form submission: <summary>` line under the
entry, and the stream carries the field through its ACL like any change row.

**Not covered:** the dock's report (tone attention, a caption, *open* and *archive* actions, grouping
when many arrive together) waits for SH964. *Archive* also needs a meaning first, and the inbox line in
Block BI is where it gets one.

## The read half of reachability (SH973)

Two instruments guarded P1 and both watched writes. `ShStatelessReachabilityLintTest` compiles a
pattern for `Post`, `Put`, `Delete` and `Patch`, so a controller that only reads yields no root and
is never classified; `ShAgentCapabilityJoinTest` walks what the manifest declares rather than what
the tree serves, so a GET-only controller nobody declared escapes it too. Between them the product's
reads were unmeasured, which is a P2 concern rather than a P1 one: discovery is a feature, and an
endpoint no index names is one an agent finds by reading source.

**`ShAgentReadReachabilityLintTest`** asks the inverse question over GET mappings. A GET-only root is
**declared**: the manifest's endpoint index names it or a path beneath it, or it is classified with
a reason and something that would falsify the claim: an `AGENT_EQUIVALENT` names a declared path, an
`MCP_RESOURCE` names a uri this instance must serve, a `GAP` names the task that closes it. A root
that becomes declared while still classified fails the build, and so does a classification for a root
that is gone (SH142).

**The first run's inventory**: `ShAgentReadReachabilityLintTest` prints it and writes it to
`shio-app/target/p1-inventory.json` (SH974), so these figures are the run's rather than a reader's:
**14 GET-only roots.** One declared (`/api/v2`), four with an agent
equivalent (`/api/v2/events` → `/agent/changes`, `/api/v2/post/xp` → `/agent/read`,
`/api/v2/system/info` → `/agent/manifest`, `/preview` → `/agent/render`), one entry point
(`/api/v2/discovery`, read before the manifest), one console-only (`/api/v2/locale`, the language
picker's catalogue, nothing validates a write against it), six not agent-facing (the console shell,
`/`, `/api/v2/csrf`, `/api/v2/ping`, `/sites`), and **one gap**: `/api/v2/history`. That feed walked
the same audit rows by cursor and could not answer "what happened to this page", which is the
question a correction starts from.

**SH1005 closed it, and that root is an agent equivalent now.**
`/agent/changes?address=post:Blog/hello-world` is that page's own trail, addressed the way every
other read on this surface is, an id is not something an agent has, and a feed that took a uuid
would make this the one question you have to look something up to ask. The console's Activity page
and this feed read through **one** query set (`ShAgentChangesService`), walked from opposite ends: an
agent forward from a cursor under a token budget, a person back from the newest row. The gap list is
empty, which is not the same as vacuous, the run's own count of roots is printed beside it, and
every root is classified.

Both lints read one scanner, `ShControllerScan`, a second copy of "what is a controller root" is the
cheapest way for two inventories to disagree about one file. It resolves a class-level
`@RequestMapping`, a constant, and the whole-path-on-the-method spelling `ShCsrfController` and
`ShIndexContext` use, which the older copy dropped.

## A figure in prose is cited from the run, or not written (SH974)

`agents.md` said, of the stateless gate, "of 25 mutating paths, which 8 an agent can write to without
a cookie", and `security.md` carried the same figure. Neither is current. The sentence immediately
before it says, about the *other* gate, that the run prints both counts "which is why neither is
written here", and then writes this gate's counts anyway. The rule was known; the exception was two
lines below it.

Nothing could have caught it: `ShAgentP1ConformanceTest` refuses an unreproducible figure only inside
its own exception reasons, and no instrument reads prose. So the three instruments now write what they
measured, through `ShP1Inventory`, to **`shio-app/target/p1-inventory.json`**, merged per instrument,
so a targeted run of one does not erase the others, and in `target/`, so it is rebuilt by the run that
proves it and never committed.

**`cli/test/agents-md-figures.test.mjs`** reads `agents.md` and `docs/agents/**`, and fails a sentence
that names one of the three instruments and states a figure unless that sentence cites the artefact.
When the artefact is present (after a Java run) it also fails a *cited* figure the run did not
measure, so the citation cannot rot into decoration either. Without it the check still holds the
citation rule and says so, which keeps the JS gate independent of the Java one.

The unit is the sentence, not the paragraph. A paragraph in these documents runs to half a section, so
the stateless gate's paragraph swept in every response budget printed near it and the check became a
list nobody would read. The cost of the narrower unit is stated in the test rather than hidden: a
figure written two sentences from the instrument's name is out of reach, which makes this rule
enforceable rather than complete.

## A pointer is something you can run, and an operator path is a category (SH975)

The manifest's `elsewhere` map pointed `reviewQueue` at the word **`console`**. Every other value is
something a caller runs (`shio import <file>`, `POST /graphql`) and the test that checks them says
so in its own message ("a pointer is a command or a route, never an apology") while asserting only
`isNotBlank()`. The rule was written; the check was not.

It was also wrong. `/api/v2/review/**` carries the narrowed CSRF exemption, so a curator reaches the
review queue with HTTP Basic from a terminal; what is refused there is an **AGENT** token, which may
write only on the agent namespace and MCP (SH109). So three artefacts said three things about one
path: the reachability lint said *writable*, the P1 table said `CONSOLE_ONLY`, and the manifest said
*console*. That is what a missing category looks like.

**`Reach.OPERATOR_HTTP`** is that category: reachable over HTTP with a person's own credential and
refused to an agent's. Still an exception (P1 is paid, deliberately) but for the honest reason. It
carries its own check: the target must be covered by a CSRF exemption, read from `ShSecurityConfig`
through `ShControllerScan.csrfExemptions()`, which is the same reader the reachability lint uses. The
two instruments now cross-check instead of disagreeing, and narrowing that exemption turns the row
back into `CONSOLE_ONLY` with a build failure saying so.

The pointer is now `GET /review/sessions (operator credential, not AGENT)`, and every `elsewhere`
value must parse as a `shio` command or as a verb and a path, with anything after it read as a note.
It is terse because the manifest has a ceiling and SH743 keeps room under it, at 96% used, a console
deep link would have cost more than it told anyone.

The vacuity guard's absent-path list is derived from the exceptions' own targets now. It had named
`/agent/trash` since long after SH732 deleted the entry that awaited it, so it was asserting the
absence of a path nothing was waiting for.

## The handoff as a link (SH981)

An agent's closing move was a sentence. The batch's own detail said *change it in the console* and
named no URL; the manifest pointed the review queue at the word `console`; `ShAgentDoc.withUrl`
attaches the stored `furl` spelling, which is a content address rather than a page. The reason it
stayed that way is that the console's routes live in TypeScript, and `routes.const.ts` records that
the prefix has already moved once, a URL concatenated in Java would have rotted without a sound.

SH978's declaration carries routes, so it carries this too. **`deepLinks`** is a template per kind of
thing an agent writes (`post`, `folder`, `site`) and `ShConsoleSurfaces.curatorUrl(kind, id)`
substitutes the id the caller already holds. `bento-nav.test.mjs` holds every template against the
router's own routes, so a template that points at no page fails the build on the console's side.

A read asks for them with **`include=links`** (`links` on `shio_read`), and gets **`curatorUrl`**
and, for a post with a site and a URL, **`previewUrl`**, `/preview/<site>/<url>`, SH99's route,
which answers for a draft too, and a draft is exactly what has just been written. A batch asks with
**`links: true`** and every change row carries `curatorUrl` whatever `ids` says: that flag is about
whether a caller wants UUIDs, and this is a link a person opens. A delete gets none, the page it
named is gone. `shio publish` asks, and prints the link under each row the way a deploy tool prints
its URL.

**The measurement changed the design, which is what it is for.** §LXIV.9 budgeted about ten est.
tokens per response and said to measure before shipping. The two links cost **26** on a terse read,
28 → 54, and on every row of every result they took the build-a-site benchmark **800 tokens over its
ceiling**: that task writes sixty times and hands over once. So they are opt-in, which is SH721's
shape for the digest one field over, and a default read is the 28 it always was. `find` rows have no
opt-in at all: a list of candidates is not a handoff, and a link per row would multiply the dearest
read on the surface by its page size. `shio push --content` does not ask either, a sync writes every
changed post, and the handoff is the publish.

## A census for "never surprise the curator" (SH982)

P1 has three instruments and P2 has one. **P7 had none**, and it is the law this round leans on
hardest: a release needs a confirm token, the console publish gains a dry run, bulk verbs report per
item, attribution becomes visible. Each of those tasks asserts its own fix, and none asks SH338's
question first: which *other* mutating paths have none of these? A law argued task by task is a law
nobody sweeps.

**`ShAgentP7ConformanceTest`** asks it of every mutating root `ShControllerScan` finds, on four axes.
**A dry run**: can a caller see the plan before it happens. **A confirm token**, does a destructive
branch require one taken from its own plan (SH108), which is what makes a deletion something a person
was shown. **An audit row**, does the write leave a trail carrying the actor, which
`ShContentAuditService` also stamps with the agent session. **A review entry**, does an agent's write
become something a curator can approve or revert, which `ShReviewService` reads off exactly those
rows.

Every claim is **falsifiable in both directions**: a guard that is present names the file and the
token that prove it, and an absence names the same pair and asserts the token is *not* there. So a
path that gains a dry run, or starts auditing, fails this test until its row moves, which is the
half SH142 keeps having to add to a held list. It paid for itself on the first run:
`/api/v2/render/post-types` was written down as having no dry run and has had one all along.

The counts print on every run and land in `p1-inventory.json` beside the other three instruments, so
a figure about them in prose is cited rather than remembered (SH974). Read before a feature block the
census is its worklist; read after, it is what stops the next console write arriving with none of the
four.

## A census for "close the perception loop" (SH983)

The same walk, one law later. P9 asks for cheap textual proof instead of asking somebody to look, and
here too the instruments are good and complete: the render digest, the address-scoped verify,
`include=impact`, `shio audit`. Every one of them is something a caller has to **request**, and
nothing enumerates which mutating responses carry proof at all, so a task can notice that one save
answers with a toast while the agent path answers with two digests, and the noticing stays inside
that task.

**`ShAgentP9ConformanceTest`** asks four axes of every mutating root. **A change set**, does the
answer say *what* changed rather than that something did. **A digest**, does it carry a value the
caller can compare with what it held. **Findings**, does it report what is now wrong, or would be.
**A route delta**: does it say which URLs a visitor can now reach. The bar is deliberately low: the
point is not that every write renders a page, it is that a write answering with a bare 200 is a
decision somebody wrote down.

Where this differs from the P7 census is **where the evidence is**. A guard lives in the controller;
proof lives in the **response type**, so a row names the DTO the caller is handed. That distinction
is the whole reason the census is worth running: `/api/v2/staticfile` exposes a digest on a second
`GET` endpoint *and* stamps `sha256` on the item a replace returns, one of those is answering the
write and the other is not, and only the second reading survives being written down. Both censuses
share `ShGuardCensus`, which holds the `Axis`/`yes`/`no` shape and the both-ways check, for SH973's
reason: two readers of one question need one reader.

The absences are the deliverable, and they are argued in families rather than one essay per row,
*the row is the answer* for a console write that hands back what the caller sent, *not content* for
a write the verify vocabulary does not cover, *no route moved* for one no visitor-facing URL follows.
Two of them are worth reading before writing a new response shape: `/api/v2/object` **does** move a
post's URL and says so only by returning the objects it touched, and `/api/v2/cda/form` withholds
everything on purpose, because a honeypot refusal has to answer exactly like an accepted submission
(SH633). The counts land in `p1-inventory.json` with the rest.

## Revisions, on the surfaces an agent already has (SH987)

SH986 captured what a page was. Nothing could read one: `state` reached the draft and the live row
and stopped, no listing existed on REST or MCP, and the only rollback available was *read the old
body and upsert it*, which re-sends bytes the server already holds and leaves a history in which
the rollback reads as an ordinary edit.

The whole chain rides parameters that already exist, which is why it cost no endpoint and no tool:

- **`include=revisions`** attaches the list to a read. Numbers, dates, actors, and the field names
  each revision changed, never bodies. Fifty revisions of a page with a long body is the page fifty
  times for a question the metadata answers.
- **`state=rev:<n>`** reads one revision in place of a row. It is the same question `state` already
  asks (*which version am I reading*) so a caller with `state=published` in a script changes one
  value. The row is still read underneath: the type, the folder and the links are facts about the
  post rather than about the revision.
- **`post.rollback`** joins the op vocabulary with `address`, `revision` and `expect`. An op rather
  than a tool because the tool budget is paid every turn and a rollback happens once in a hundred,
  and being an op is what gives it the plan, the dry run and the confirm token without reinventing
  them slightly differently.
- **REST** carries `GET /{id}/revisions`, `GET /{id}/revisions/{n}` and
  `POST /{id}/revisions/{n}/restore`; `GET /{id}/versions` is untouched and still means the two rows
  a post has now.
- **The CLI** is `shio versions`, `shio rollback --to <n>` (which plans unless given `--apply`) and
  `shio diff --against revision --from <n>`, which reuses the comparison `--against published`
  already has with a stored revision as the second row source.

Three decisions worth knowing:

**A rollback restores into the draft and publishes nothing.** P7 as mechanics rather than
convention: the caller asked to go back to a version, and putting that version in front of visitors
is the separate explicit act. It also writes a new revision and deletes none, so what it replaced is
still there to come forward to, which is why it is not a destructive op and needs no confirm token
of its own.

**The plan reads the revision** (SH690's rule): a dry run that says yes about a revision the write
cannot find is the plan an agent acts on. A missing `revision` is refused before the address is even
resolved, through the shared `missing-op-field` shape (SH332), because a caller that got both wrong
is otherwise told about the address, the error it can do least about, since the numbers it needs
come from reading that address.

**The vocabulary's ceiling moved, with the ritual it demands.** `ShAgentOpsReachTest` went from 770
to 800, measured at 795. The trim came first, the first draft measured 819 and ended with a clause
`shio_write`'s own description already teaches, and the decomposition says the growth is one more
op rather than fatter prose (per-op prose 36.3 → 36.8). `shio_write`'s schema was trimmed to stay
under the 460-token per-tool ceiling, and `include=revisions` got its own row in the response budget
rather than room added to `read.terse`: the claim an opt-in makes is precisely that the default did
not move.

## An approved run, still describable after the approve (SH989)

An audit row said *that* something changed and never *what*. The review queue's comparison is the
current draft against the current live row, and approving publishes, which copies the draft across,
so at the moment a curator accepts a run, the one comparison that could describe it becomes empty.

The fix is a stored fact, not a better reader: `ShHistory.changed_fields` holds the names of the
fields each write changed, computed by the write service in the transaction that holds both documents
(an update, a publish, both restores) and **never** recomputed afterwards, because after a publish the
two documents are one. Names only, the values are SH986's revisions, and a rich-text body in an audit
row would be a second revision store. `null` means not recorded (every create and delete, and every
row older than the column); `[]` means recorded, nothing changed. Both readers keep them apart: the
terse line prints `fields=-` for the second.

It reaches every surface through a parameter that already existed or an argument beside one:

- **REST** `GET /api/v2/agent/changes?include=fields`, which adds `fields` to each row that recorded
  names. The default row is byte-identical to what it was, asserted in `ShAuditChangedFieldsTest`.
- **MCP** `shio_changes` takes `fields`. It spent one of SH811's two arguments of headroom, and the
  properties file says so beside the ceiling.
- **The CLI** `shio changes --fields` prints a `fields:` line under each row, and `(none changed)`
  for an empty list. With `--follow` it rides on the stream (SH1080).
- **The pushed feed** `GET /api/v2/events?include=fields` (SH1080). The subscription keeps the flag
  and the hub reads each page with it, so an event names fields exactly as a polled page does. A
  stream that did not ask gets an event byte-identical to before, apart from the names, and
  `ShAuditChangedFieldsTest` asserts that. Until SH1080, `--follow --fields` was refused before
  connecting, because accepting it would have dropped the flag silently.
- **The console** Activity page shows the names under each description; `/api/v2/history` always
  carries `changedFields`, since a console table is not a budgeted response.

`ShChangedFields.between` is the one comparison, and `ShPostRevisionService` now calls it too: two
copies that agreed today would disagree the first time one learned a new rule about `null`.

## One `state` grammar, and a census of the reads that do not take it yet (SH990, first slice)

Two reads took `state` and each parsed it itself. `/agent/read` refused a typo with a teaching 400 and
understood `rev:<n>`; `/preview` compared the value to `PUBLISHED` and previewed the draft for anything
else, so `state=live` showed the draft under a 200. `ShContentPerspective` is now the one reading:
`draft` and `published` in any case, `rev:<n>` for a revision, absent for the path's own default (a
read is draft-preferred, a preview is the draft). Anything else is `unknown-state` with the three
spellings. A path that reads rows and not revisions says so with `state-revision-not-served` and
names the call that serves one, instead of answering with a different page than the one asked for.
`release:<name>` is BA4's, added with the thing it names.

`ShPerspectiveCoverageLintTest` came first and is the worklist. It classifies every `GET` in
`ShAgentAPI`, `ShRenderAPI` and `ShCdaAPI` as taking `state` or not, with a reason; the reads that
should and do not are one named family, SH990's remainder, and a read that gains the parameter fails
until its row moves. It also holds that the literal `"rev:"` lives in `ShContentPerspective` alone, so
a third private parser is a finding when it is written. The GraphQL schema is built in code and sits
outside the scan, which is why it is named in the remainder rather than in the census.

The reader that takes a mapping's parameter list by counting parentheses moved from
`ShVersionGuardLintTest` into `ShControllerScan.mappings`, which both lints now call.

**The second slice put the renderer on the grammar.** `ShPageRenderService.render(site, url,
perspective, params)` renders a revision by finding the page draft-preferred, as a preview does, and
putting `ShPostRevisionService.asOf`'s transient copy in its place: the revision's document and title,
at the page's present address, through its present templates, read at the state the revision was
captured from. That is the question a curator asks of a revision (what did this page say) and not
what the whole site looked like then, which the store does not hold. `/preview` serves `rev:<n>` now
and the banner says `Revision <n> preview`, since "Draft preview" over an old revision would misdescribe
the one thing the banner is for. `/agent/render` and `/agent/editable` take `state` beside an
`address`: a row the post does not have is SH619's `no-such-state`, a revision goes through the
renderer's revision path so the digest and the preview compose the same document, and `state` beside
`url` is `state-with-url`, because a delivered page has one version.

**The third put the listing and the lint on it.** `/agent/find?state=published` lists the rows of that
state with their own titles, so "which pages would a publish change" is one listing rather than a read
per address; the filter runs after the published index is taken and before the draft-preferred
dedupe, which would otherwise keep the draft of a post whose published row was asked for.
`/agent/verify?state=` narrows the *subjects* the rules report on and leaves the indexes they check
against over both states, since "published depends on unpublished" and "two posts claim one URL" are
questions about both rows whichever is the subject; the report says so in a note. A `state=draft`
run of `routes` carries a second note (SH1092) naming the rules that read published rows only and so
never ran on the drafts: `url-missing`, `url-orphaned`, `html-unsafe`, `link-unresolved`,
`link-broken`, `image-no-alt`, `asset-no-alt` and `asset-missing`, from
`ShAgentVerifyService.PUBLISHED_ONLY_RULES`, which `ShVerifyDraftNoteTest` holds against the rules the
service emits. Without it `errors: 0` read as a verdict on links and images nobody checked. Both refuse
`rev:<n>` with `state-revision-not-served`: a revision is a version of one page, not a set to list or a
site to lint, and the refusal names the read and the digest that do serve one.

**The fourth gave MCP and the CLI the argument.** `shio_find`, `shio_verify` and `shio_digest` take
`state` (the last also for its `include=editable` half), and `shio digest --state` and
`shio verify --state` pass it; `shio verify --state` over the file-tree groups alone is refused, since
those have no rows to choose between. The tool list measured 2843 against its 2820 ceiling with the
three arguments, and was brought back by trimming rather than raised: `shio_digest`'s examples of a page
shape said why a digest is useful to a caller that has already chosen to call it, and the three new
descriptions are their values alone. The list is now at its wall, and `token-budgets.properties` says
so beside the ceiling.

**The last put the CDA on it, and it is the one where the grammar meets a privilege.** SH5 already split
delivery by token: production reads what is published, preview reads the draft-preferred row. `state`
names a version *inside* that split and never widens it. `published` is open to every token; `draft` and
`rev:<n>` need a preview token, and a production token asking for either is `state-needs-preview-token`
(403) rather than an answer with the published row, which a front end building a preview would read as
a draft that happens to match what is live. The refusal depends on the token alone and says nothing
about a site or a post. The allow-list applies to whichever row a state finds, so a scoped preview token
reaches no other site's draft. `/post/{id}`, `/post/by-url`, `/object/{folderId}/list`, `/query` and
`/search` take it over REST, and `post`, `postByUrl` and `posts` take it as a GraphQL argument; a listing
refuses a revision. Every listing in `ShCdaService` now takes its states as one list (two is the
draft-preferred read, one is that state), which is what let the perspective in without a second copy of
each query. The census's remainder family is empty and gone: a new page read takes `state` or declares
why not.

## What is scheduled, readable (SH991, first slice)

Scheduling worked end to end through the batch, and nothing read it back: the only queries over
`publishScheduledDate` and `unpublishScheduledDate` were the sweep's due-only finders, so an agent could
not confirm the schedule it had just set.

`/agent/find?scheduled=` lists only scheduled posts, and those rows carry `publishAt` and `unpublishAt`
(terse `publish-at=`/`unpublish-at=`). Without the filter no row carries them, so an ordinary listing is
byte-identical. `shio_find` takes the same argument inside its SH977 reservation. The value is `any`, or
an ISO-8601 range `from..to` with either end optional and the top exclusive (`ShScheduleWindow`), so
consecutive windows never count a post twice. It is **one** parameter where the design named `before`
and `after`, because `after` is already find's page cursor and a second meaning under one name is the
collision that silently skips a page. The schedule is read off the **draft** row, where SH15 sets it,
and the index is taken before `state=` narrows the rows, so a published-only listing of scheduled posts
still sees it.

`/agent/read?include=schedule` attaches `schedule: {publishAt, unpublishAt}` when one is set. `shio_read`
does not take it: its reservation had 7 tokens left, less than an argument, and `shio_find scheduled`
already gives an MCP client the confirmation.

**The second slice is `shio schedule`**, the agenda: it walks `/agent/find?scheduled=` to the end and
sorts by instant, because find sorts by address (its page cursor) and an agenda in address order is a
list a person re-sorts in their head. A post due to go live and come down is two entries, `--from` and
`--to` become the server's window and also bound the entries shown, times are printed in UTC and the
header says so, and a walk that stopped short exits 1. The CLI help paid for the new verb by cutting why-
clauses out of `shio clone --help`.

**The third slice is who set it.** A transition the sweep fires is recorded as the `scheduler`'s, and
the Activity row now reads `Published "…" (scheduled by ana)`; `include=schedule` names the requester
as `requestedBy`. The schema half is in `docs/agents/database.md`.

## A takedown that waits, and a schedule called off (SH992)

`post.unpublish` bound `when` and never read it, so a takedown dated for December happened on the call
and reported success; `shio_publish` built its op with no `when` at all. Both ops now go through one
helper in `ShAgentBatchService` (`scheduled`), on both address forms: `when` sets that transition's
instant, `cancel: true` clears it and the change row says what it cleared (`cancelled the unpublish
scheduled for …`), and the same instant twice is a noop. `when` beside `cancel` is a 400
(`cancel-with-when`), because either precedence drops something the caller sent. A scheduled unpublish
is checked before "not published", so a page whose launch is itself scheduled can carry its takedown,
which is an embargo window. It still refuses a published row with no draft behind it (SH462), the same
refusal an immediate unpublish gives.

**The instant the op does not name is carried through.** SH342's path passed `null` for the unpublish
instant, so scheduling a launch cancelled the takedown already set for that page. And
`ShPostUnifiedWriteService#schedule` now changes the requester only for somebody who set an instant:
cancelling one of two, or saving both unchanged, leaves the other attributed to whoever chose it.

`shio_publish` takes `when` and `cancel` inside its SH977 reservation. `shio_write` could not declare
`cancel`, since it is at its per-tool wall, so it now **refuses any op field it does not declare**
(`undeclared-op-field`) instead of dropping it. Dropped, a `cancel` that shio://ops lists would have
arrived as a plain publish, and the op would have gone live instead of standing down. The refusal
names `shio_publish` for `cancel`. `shio publish --when <instant>` and `--cancel` are the CLI leg.

**The REST batch refuses one too (SH1083).** `POST /api/v2/agent/batch` bound its body with Spring's
lenient mapper, so `cancle: true` on a `post.publish` bound away and the page went live now. On a
clean instance it answered 200. `ShAgentBatchBodyAdvice` now reads the body as it arrived, for that
one target type, and `ShAgentOpFields.check` refuses the first op field that is not an
`ShAgentBatch.Op` component. The refusal carries the same `undeclared-op-field` slug, with
`ops[<i>]`, the allowed names and a `didYouMean`. The allowed names are read from the record, so a
new op field is accepted the day it exists. It is an advice rather than a `JsonNode` handler
parameter, which kept two things working: SH430's interceptor still learns that `dryRun` is a body
field from the parameter type, and a body that does not parse is still Spring's `unreadable-body`.
Other request bodies keep lenient binding.

## A release: the unit a launch did not have (SH993, first slice)

One batch call is atomic and nothing persisted a set across calls, so forty pages of a launch went live
as forty events in sweep order, with nothing to stage, review, schedule or undo as a whole. A release is
that unit: `sh_release` and `sh_release_item` (see `docs/agents/database.md`), addressed
`release:<site>/<name>`: a fifth `ShAddress.Kind`, whose name is one segment because it is also the file
the release will project to. Resolving one as content is a teaching 400 naming the listing instead.

Three ops, and the read that answers them. `release.upsert` plans one or confirms it, idempotent like
`folder.upsert`, so a document naming it re-runs; `release.add` and `release.remove` move the posts
`data.posts` names, one change row for the release naming what moved, because the subject of the op is
the set. A post of another site is refused: running a release changes one site and nothing else. A
release that has run takes nothing in or out, it is the record of what went live together.
`find?site=<site>&release=<name>` lists exactly its posts, with the release itself on the envelope
(`release <address> state=… posts=N` in terse), which is SH222's shape: what a release holds is a list
of posts, and find is the call that lists posts. `shio_find release=` is the MCP leg, inside its SH977
reservation.

**What the ops cost, and the two ceilings they moved.** Three names in `shio_write`'s enum measured 13
tokens, the ladder working: a capability expressible as an op costs a name, not a schema. That took
`shio_write` to 471 against a 460 per-tool ceiling and the allocation to 2832 against 2820, so the
ceilings moved to 490 and 2850 with the five-op family budgeted, and `shio_apply` left `AT_THE_WALL`
because it now has room. The ops vocabulary went to 839 against 800: the trade was re-run rather than
inherited, and the decomposition settled it, per-op prose **fell** (35.6 → 31.8) while the op count rose
16 → 19, which is the vocabulary gaining ops rather than drifting, and folding it into the manifest at
30% is a worse trade than the 19% that made SH346 reject it. The ceiling is 900, which budgets
`release.run` and `release.archive`.

### Running one (SH993, second slice)

`release.run` publishes every post in the release **in one transaction**, `ShReleaseRunner`, which is
also what the SH15 sweep calls for a release whose instant has arrived, because a launch that behaved
differently at midnight than on the call is the worst kind of thing to debug. A post that refuses takes
the rest of the launch back with it, which is the half-live site a release exists to make unreachable.

It takes **SH108's confirm token from its own dry run**, and it is the first op to do so without
destroying anything: what a run puts in front of visitors is a *set*, so the caller has to have seen the
set. The token is taken over the release's change row *including its detail*, which counts the posts,
so a page added between the plan and the run makes the confirm stale instead of launching quietly.
`when` and `cancel` carry SH992's grammar (schedule it, or call the pending run off) and confirm
nothing, because those calls move a date rather than a page. `release.run` also joins SH107's publish
gate: a credential without `mayPublish` was otherwise one `release.run` away from publishing a site.

Each item keeps `version_before`, the SH986 revision its post was at, recorded for **every** post in the
release including the ones that publish as noops, an item with no anchor is the page a rollback would
silently skip (SH994). A release above `shio.agent.batch.max-ops` is refused while planning, naming the
count: split it and the atomicity is gone, which is the thing a release is for. A release naming a post
whose draft was deleted is a 409 rather than a shorter launch.

### `state=release:<name>` (SH993, third slice)

The version grammar gains a fourth form, and it answers the one question a release knows and nothing
else does: **what a page was immediately before that release ran**. What the launch *put* live is what
`state=published` already reads; what it replaced is the item's `version_before`, which is also
SH994's rollback anchor, so the read and the undo are about the same number.

It is served on `/agent/read` only, and resolved **per post**, because one release holds many pages and
each was at its own version. Two refusals a reader can act on: a page the release does not hold is
`post-not-in-release` (404, pointing at `find?release=`), and a release that has not run is
`release-has-not-run` (409): until it runs there is no such version, and the draft is what the page
*will become* rather than what it was.

**Every other path that reads `state` had to decide about it**, and not deciding is the silent kind of
wrong: `release:` leaves both `row` and `revision` null, which is exactly what "no state was sent"
looks like. So `requireRow` refuses it wherever it already refuses `rev:<n>`, and a second guard,
`requireVersion`, refuses it on the paths that *do* serve revisions, `/agent/render`,
`/agent/editable`, `/preview` and the delivery API, each naming the read that answers it. One overload
of `read` was passing `parse(state).row()` and dropping the rest; it now goes through the full parse,
which is where a `rev:<n>` sent to it was being answered with the draft.

### Archiving one, and finding out they exist (SH993, fourth slice)

`release.archive` takes a release out of the way, and `cancel: true` puts it back. It is **not** a
delete: the release stays addressable and `find?release=` still reads what it holds, because the record
of what went live together is the thing not to throw away. What archiving removes is its place in the
listing a reader scrolls, and its place in the sweep, since archiving a SCHEDULED release is how a
launch is called off for good; a sweep that still fired it would publish a set somebody had just put
away, so `dueRuns` excludes archived ones in the query rather than filtering afterwards.

**`include=releases` on the context pack** is how an agent learns a release exists. A release is
addressed by a name and `find?release=` answers only a name it is given, so without a listing an agent
could create releases and never discover the ones already there, P1, not P3. It sits on the pack rather
than on a tool argument for the SH977 ladder's reason: `tools/list` is paid every turn by every session,
and this is a question a session asks once if at all, so `shio_context include=releases` reaches it for
nothing. Opt-in and attached after the digest, like `manifest`, `ops` and `usage`, so asking for it does
not invalidate a cached pack. It carries counts, not members: forty addresses per release in the pack is
the regression the opt-in sections exist to avoid, and `find?site=&release=<name>` is the read for what
one holds.

## Rolling a release back, as the unit it went live as (SH994)

SH993 recorded an anchor per item and declared a `ROLLED_BACK` state, and nothing reached either, a
column written and never read is the shape that rots, because the next reader cannot tell whether it is
dead or load-bearing, and atomicity without a reverse is half a promise.

`release.rollback` is the reverse of `release.run`, in one transaction. Per item it is SH987's rollback
into the draft **and then a publish**, and that second half is what makes it an undo rather than a
stack of drafts: what went wrong is what visitors are reading, so a rollback that left the live pages
alone would answer "put it back" with "the draft is correct now". SH987's own rule is kept where it
belongs, the restore captures a revision rather than deleting one, so the launch this undoes is still
there to come forward to.

**An item the release published for the first time has no anchor, and its undo is a takedown.** There
was no earlier version to put back, and leaving it live would be the page a rollback missed; the change
row says so, counting both (`2 back, 1 down`). It takes `release.run`'s confirm token, the same blast
radius pointed the other way, and joins SH107's publish gate for the same reason `release.run` did.
Only a `PUBLISHED` release rolls back: a planned one replaced nothing, and one already rolled back would
go back to the state its own rollback wrote. What it put back reads through `state=release:<name>`,
which is the same anchors from the other end.

## Which URLs a publish moves (SH996, first slice)

`include=impact` answered one question, a template's blast radius, which layouts and post-types a
region reaches, and a curator about to press Publish is asking a different one: what starts
resolving, what moves, and what breaks. The same opt-in now carries both, because they are one
question asked of two kinds of post, and a page can be both.

`impact.routes` has three cases and the third is the one nobody sees coming. A draft with no published
row **goes live** at its URL. A draft whose `furl` changed since it went live **moves** the live page,
so the old URL **stops** resolving, and that is the case that breaks inbound links, so `inbound`
comes with it: how many *published* pages link to the URL that disappears, counted with SH140's
scanner, the one `post.move` already reports with. Two counts of "who links here" would eventually
disagree, and the one a curator reads before publishing is the one that has to be right.

**Absent when a publish moves no URL**, which is almost every publish: an edit to a page already live
at the same address changes nothing about the site's routes, so an ordinary `include=impact` response
is byte-identical to what it was. In terse it is its own `routes` line, for the reason `impact` is its
own line: the header describes the post, and these describe what the site does about it.

## Publish gates: what a site checks before content reaches visitors (SH997, first slice)

Every instrument Shio had was opt-in (the address-scoped verify, the render digest, `shio audit`) so
the only gate that existed asked **who** may publish (SH995) and never whether this content *should*.
A gate is the second question: an ordered list of checks with severities, declared per site in
`sh_publish_gate` and evaluated **where the publish happens**, so the agent's `post.publish`, the
console's button and `release.run` all pass through it. A rule one surface enforces and another does
not is worse than no rule, it is a door nobody documented.

**Not a workflow engine.** No state machine, no assignee, no queue: an orchestrator inside Shio would
be a third contract over the CDA and the console (P10), and the operator driving this instance already
is one.

**Three checks, and one that cannot be here.** `verify` runs the whole address-scoped verification over
the **draft** (what this publish would put live, not what is already past the gate) `links-resolve`
narrows it to SH100's routes group and `render-digest` to the page group. The design also named
`audit`, and it is **refused** with the reason: `shio audit` drives a browser per viewport, and "the
instance does not run a browser" is a standing non-goal, so it belongs in CI where the browser already
is. A check nothing can run is refused at declaration rather than stored, because a gate that cannot be
evaluated is a gate that silently passes.

A blocking step is about **errors**; a warning-severity step reports errors and warnings without
stopping anything. That split matters: `link-unresolved` is deliberately a warning, since a front end
may own routes the CMS has never heard of, and a gate that stopped a launch over one would be wrong
about the product. The refusal is a 409 naming the gate, the check and the address, with the verify
call that reproduces it, and SH996's dry run carries the same sentence so a plan cannot promise what
the run refuses. Gates travel as their own idempotent writes (`GET /agent/gates`, `PUT
/agent/gates/{name}`), like SH161's notes and for the same reason: a gate is not content, so folding it
into the desired-state document would make it the thing `--prune` deletes when a tree stops naming it.

### Every door that publishes (SH1084)

SH997 named three gated surfaces, and there were more. `ShPublishGateLintTest` walks every method
that calls `shPostUnifiedWriteService.publish(` and fails on one that doesn't evaluate
`shPublishGateService.require*`, unless the method is in its `EXEMPT` map with a reason. It prints the
population on every run (ten when it landed). It found three doors the gate skipped:

- **The review queue's Approve** (`approveOne`). A gate that stops a page there comes back as a
  `skipped` row with the gate's title, not as a failed call, because the curator still wants the
  other pages. `override=<reason>` on `/review/sessions/{s}/approve` and `/decisions` takes it past.
- **`PATCH /v2/post-unified/{id}` with `publish`.** The merge gates after merging, in the same
  transaction, so a refused save-and-publish writes nothing. The route also takes `override`. It had
  asked only `requireWrite`, and now asks `requirePublish` as the button does (SH995).
- **The CDA write publish** (`ShCdaWriteService#publish`). A token cannot waive, so it takes no
  `override`.

Exempt, each with its reason in the map: the redirect write, the release rollback, a review revert,
and `ShReleaseRunner#run`, which both of its callers gate before calling.

### The sweep asks at fire time (SH1138)

The SH15 sweep published through `publishScheduled` and the release runner and asked no gate, and
**scheduling asks none either** (`post.publish` with `when`, `release.run` with `when`), so this was
every scheduled launch, not only drafts edited after they were scheduled. `ShPostScheduler` now asks
before each fire, which is the only moment that is right: a gate declared since, and a draft edited
since, are both what the curator would have met on the button.

**A dry run, not `require`.** `requirePost` throws a 409 nobody is waiting for and dispatches the
GATE_FAILED webhook, and a stopped schedule stays due, so it is asked again every sweep, once a
minute, to every subscriber. The sweep uses `evaluatePost` and records the stop as a `schedule`
incident instead, which folds on repeat (one row seen again, not five hundred). Its message starts
with `scheduled publish failed: ` so SH969's retry (a `post.publish`, itself gated) is offered, and it
names the gate and its first blocking detail. A due **release** asks for every page its plan puts
live and stops whole on the first refusal: partial release is the state a release exists to make
unreachable, so the design's "report the stopped page and go on" was declined; the release stays
`SCHEDULED` and its incident names the page. No waiver is offered at fire time, because there is
nobody to give the reason; the incident's fix names `?override=`.

The lint's pattern now matches the two calls that publish by proxy, `writeService.publishScheduled(`
and `shReleaseRunner.run(`, so the sweep and the release op are both found, and the sweep's dry run is
accepted only because `ShPostScheduler#sweepCurrentTenant` is declared in `DRY_RUN` with its reason.
`ShScheduledGateTest` holds the stopped post, the passing post and the stopped release.

## Waiving a gate, and the two events an orchestrator needs (SH998)

A gate with no sanctioned way past it is one a team removes the day it blocks something urgent, and
then nothing checks anything. So a person holding PUBLISH may publish past a failing gate by supplying
a **reason** (`POST /v2/post-unified/{id}/publish?override=<sentence>`) and the reason is the whole
of it. Two refusals sit around that: a blank one is `400 waiver-needs-a-reason`, because a waiver
nobody can see is worse than no gate (it looks exactly like the gate having held), and an **agent
credential** is `403 agent-cannot-waive`, because "the agent never approves, holds or rejects its own
work" is a standing non-goal and waving content past the check that judges it is that, one word later.
`ShApiTokenContext.current()` is what tells the two apart, so a console session is a person and a token
is not, with no new flag to keep in step.

**Recorded twice, because the two records answer different questions.** The audit row
(`waived gate 'ready' (verify): <reason>`, alongside SH991's `scheduled by`) is what somebody asking
"what happened on Tuesday" reads, in the Activity page and in `shio changes`. `sh_post_revision.note`
is what somebody who started at the *content* reads, in `shio versions` and the history panel. Either
one alone leaves a reader with no way to find out a check was overridden. The waiver is **not** a
fourth `ShPostRevision.Reason`: that column answers why a snapshot exists, and a waived publish is
still a publish, folding them would force a reader to choose between "this went live" and "somebody
overrode a check" when the honest answer is both.

**Two new webhook events, and the subscription rule that had to change with them.**
`GATE_FAILED` fires when a gate refuses, nothing moved, which is exactly why something outside has to
hear about it, and `REVIEW_REQUESTED` when an agent run leaves drafts a person has to release.
`GATE_FAILED` is **dispatched**, not published as a Spring event: `ShPostLifecycleEvent`'s listener
runs `AFTER_COMMIT` and there is no commit here, the transaction being the one abandoned, so a notice
that waited for one is a notice nobody receives. `REVIEW_REQUESTED` does wait, because the drafts have
to exist before an approver is told to look; it is deduplicated per transaction and site, so a
forty-page conversion sends one notice rather than forty. Each carries its own facts flat in the
payload (`gate`, `check`, `severity`, `address` for the first, `session` for the second) so a
receiver routing on the failing check reads it the way it reads `postId`.

And an **empty `events` set now means every content transition**, not literally every event. Nobody
chose the old reading for events that did not exist: every existing subscriber, the SH9
`/api/revalidate` receiver included, subscribed to what the enum held at the time, and widening it
under them would have revalidated a Next.js cache on a gate *failure*, the one moment nothing changed,
with no way to narrow it back, an empty set being the only subscription they had written. A notice
is delivered only where a subscription names it.

## A machine's change, told apart from a person's (SH999)

`ShHistory.agent_session_id` has recorded which run wrote a row since SH106, and **neither reader
carried it**. `ShAgentChanges.Change#by` reported the bare principal while its own javadoc went on
promising that the agent session was stamped into that string, "so a caller can tell its own writes
from a curator's", true until SH106 moved the fact to its own column, and printed for six blocks
after, because nothing asserted it.

The change row now carries **`agent`** (the session) and **`agentName`** (what that run called
itself), both omitted when absent, so a person's row is byte-identical to what it was. The terse
form carries one attribute, `agent=`, holding the name where there is one and the id otherwise: the
terse feed is what an MCP session actually reads, and a distinction only the JSON had would be a
distinction the agent surface does not have.

**`by=agent` · `by=curator` · `by=<session>`** narrows the feed, on `GET /agent/changes`, `shio changes
--by` and `shio_changes`. It is filtered **in the query**, with the keyset predicate, because this
feed is resumable: a post-filter would hand back a short page while the matching rows sat behind the
cursor, and the caller would read that as nothing having happened. `by=curator` is the question a
resuming session actually has (*what changed that was not me*) and until now every row looked the
same.

**A console session counts as a curator's.** SH996 gives their own publishing a session id so it
reaches the review queue, so a predicate written from the column rather than from the question files
that press under the machines. That is the case the assertion pins, and the role word is SH705's
rule rather than a nicety: the counterpart of a run is the person who answers for the site.

`agentName` comes from **`sh_agent_run`**, written on MCP's `initialize` from the `clientInfo` this
server used to discard, the one call in the protocol whose purpose is a client saying what it is.
A run that never introduced itself (every session the REST surface was handed rather than minted,
and every run older than the table) reports **no name rather than "unknown"**: a row naming an agent
called unknown is a row nobody can tell from one written by an agent that is called that.

## A run that says what it was for (SH1000)

The review queue rendered a count of changes by an actor over a uuid, and nothing anywhere stored
what the run was *trying* to do, so a reviewer read the diff to reconstruct the premise, which is
the one thing the agent knew and never said.

An intent is declared **once** and rides every surface. MCP takes it in `initialize`'s `clientInfo`,
which is the cheapest place on that protocol: `tools/list` is re-sent every turn and `initialize` is
not. The REST agent surface takes **`X-Shio-Intent`**, a header rather than a body field because the
premise belongs to the *run*, `/agent/batch` and `/agent/apply` take different documents, and a
sentence spelled into each would be two places to keep one fact. The CLI's `--intent` is global for
the same reason: `apply`, `push --content`, `convert` and `publish` all write.

It is stored on `sh_agent_run` and **never on the change row**. One sentence about forty rows,
carried on the row, is that sentence forty times, and the feed is the surface a session polls, so
the repetition would be paid on every poll. `GET /agent/runs?session=a,b,c` is the read, asked by
the ids the feed already names in `agent`, in the order they were asked for. There is no listing:
"every run this instance has ever seen" is not a question anybody has.

A run that declared none reports **no intent** rather than an empty sentence, and both readers name
the argument that would have supplied one. An absent premise is a fact about the run, and a blank
line is not the same statement.

**The CLI was not sending a run id at all**, which this task had to fix before an intent had
anywhere to land: `ShAgentSessionFilter`'s javadoc said "the CLI sends one per invocation" and the
CLI sent none, so every write it made was recorded as an ordinary curator's and the review queue had
exactly the hole SH106 said it was closing. One process is one run now, a module constant, not a
per-client value, so a command that builds two clients is still one thing somebody ran.

## The run summary the queue leads with (SH1000)

`ShReviewSession` gains `bySite`, counts per site **name**, which is the half `sites` could not
answer, since an id tells a reviewer nothing. `ShReviewDetail` gains `appearance`: the addresses
whose look moved, how many pages were compared, and the cut when the ceiling bit.

The comparison is deliberately **not** against a stored digest. `sh_post_revision` keeps what a page
*said*, not what it looked like, and that is the right split: the same content renders differently
after a template changes. So the before is the revision preceding the run, rendered through today's
templates, which is also the question a reviewer has, because a page whose look moved when somebody
edited a shared region is a page whose look moved. A page the run created has no before and is not
compared; it is already a `CREATE` in the counts, and calling a new page's appearance "changed"
would put the one row the action column can already explain into the list meant for the ones it
cannot.

## Decisions per change, with a reason (SH1001, the decision)

Review was approve-everything or revert-everything, so one bad page in a long run forced a curator
to accept all of it or undo all of it, while every entry already carried a `revertable` flag that
nothing let them use.

**`POST /api/v2/review/sessions/{session}/decisions`** takes a list of `{postId, decision, note}`,
where the decision is `APPROVE`, `REJECT` or `HOLD`. One call and one transaction, because a curator
triaging eleven pages means them together: sent one at a time, a refusal halfway leaves the run in a
state nobody chose. Per-change outcomes come back in the `skipped` shape this surface already
answers with, a page that could not be acted on is named with its reason, beside the ones that
worked.

**`approve` and `revert` are that call**, over every change, keeping their own names in the outcome.
They are not a second implementation: two code paths for one decision is how the whole-run gesture
and the per-change one come to disagree about which permission approve asks for, what it records and
what it skips. `APPROVE` asks for PUBLISH per post (SH995); `REJECT` runs the same undo `revert`
always did.

**A `REJECT` with no note is refused.** A rejection nobody can read teaches nothing: the agent that
wrote the page cannot be told what was wrong with it, and the next person to open the run cannot see
that anybody looked. `HOLD` is the decision for "not now", and it takes no reason.

`sh_review_decision` makes them durable, and **`HOLD` is why the table exists**: `APPROVE` and
`REJECT` already leave a trace, but "I have read this and I am not ready" has no side effect at all.
The pending count subtracts held pages, so parking one stops the badge saying the same number for
ever, the failure mode SH615's `unreleased` was written to avoid, one layer up. One standing
decision per change per run: a curator who changes their mind replaces it, and the history of what
*happened* is the audit trail's job.

## The verdict, readable by the one it is about (SH1002)

An agent approving its own work is a standing non-goal, the review surface stays off `/agent/**`
because of it, and none of that changes here. What was missing is the **other direction**: a revert
leaves ordinary restore and delete audit rows that look exactly like a curator's own edit, and an
approve leaves publish rows, so a resumed session could not tell "my page came back" from "somebody
rejected my page". Every correction needed a person to re-explain it, a loop with a curator in the
one part a machine could have done.

**`GET /agent/review`** answers what a curator decided about runs made with *this credential*, with
`?decision=reject|approve|hold`. Scoped by the run's actor (SH999), which is a narrower claim than
"the review queue, read-only": a rejection's note is written for whoever is going to fix the page,
and a credential that did not write it has no business reading what a curator said about somebody
else's work.

**`shio://review/mine`** is the MCP resource over it, rejections only, the other two verdicts are
legible in the content, an approved page being live and a held one still a draft. A resource rather
than a tool for the reason that keeps deciding this: it is the first thing a resumed session should
read, and `tools/list` is re-sent every turn (P3).

**The change row gains `review`** (decision, note, by, at), omitted on every row nobody has decided,
which is most of them, so an ordinary feed is byte-identical. `GET /agent/changes?decision=reject`
narrows it, filtered **in the query** with the keyset: a decision is keyed by the same two columns a
change row already carries, so the predicate is a subquery rather than a post-filter that would
strand rows behind the cursor.

**What keeps the first decision intact** is asserted, not assumed: the manifest's endpoint index has
no decide path, the resource names none, and a note is readable by the credential that wrote the run
and by no other. The index assertion matches on word boundaries, because `/preview-token` contains
"review", SH794's over-match, met again.

## Notes: a correction anchored where it is about (SH1003, the note)

Searching this backend for a comment, mention or annotation class returned nothing. The review queue
reviews **runs**, not documents, so a curator who wanted one paragraph fixed had no channel and an
agent had nothing to read, the correction lived in a conversation somewhere else, and the next
session started without it. Instance memory (SH114) was the nearest thing and answers "how we write
here", not "this sentence is wrong".

`sh_note` anchors on three columns, narrowing: `object_id` is required, `field_path` and
`revision_no` are not. "This page is wrong", "this field is wrong" and "this field was wrong at
version 7" are one kind of statement at three resolutions, and three tables for them would be three
read paths for one question. `kind` is `COMMENT`, `REQUEST`, `REPLY` or (since SH1061) `REVIEWED`:
assignment **is** a request, and the alternative, an assignee column, a request table and a comment
table, would be three shapes that all mean "somebody said something about this".

**`REVIEWED` is the fourth kind and the one that is not work (SH1061).** It says a field has been
checked against the source it translates, written by the console's translation workspace and read
back by it, so what a curator ticks is what an agent sees. It is left out of the work list
(`open(null, …)`) and of a read's `include=notes`, because both answer "what is still being asked of
this page" and a review mark is the opposite of a request, counted in, the page translated most
carefully would report the most outstanding work. Asked for **by name** (`kind=reviewed`) it is
there, and it is always in the object's own thread.

**Visibility is the object's, never its own.** Every read and write goes through the SH54 content
ACL for `objectId`: "who may see this remark" has one honest answer, which is "whoever may see the
page". Resolving asks for WRITE rather than for authorship, because closing a request is a claim
about the content, and tying it to the author would mean an agent could not close a request a
curator made of it, which is the one closing gesture this exists to enable.

**`note.add` and `note.resolve` are ops, not a thirteenth tool.** A note is an addressed write, it
hangs on an object and carries a body, so it costs a name in `shio_write`'s enum and no schema,
which is the ladder SH977 describes. Measured: 3 est. tokens each, and they took `shio_write` to
490, its own ceiling. The next op on it is SH993's `max-per-tool` conversation, not something anybody
slips in. They were paid for by releasing `shio_read`'s and `shio_find`'s reservations, whose planned
additions (SH990, SH991) had all shipped.

The console reaches the same rows through `/api/v2/note`, one vocabulary, two callers (P10).

## Where a note surfaces (SH1003, the surfaces)

**`include=notes`** on `/agent/read` attaches the **open** notes on each page, with each note's id
so the run that fixes the page can close it. Open only, and that is the whole shape: a read an agent
makes before rewriting a page wants what is still being asked of it, not the history of everything
anybody ever said. Absent on a page nobody has asked anything of, so a clean site reads exactly as it
did (P3), and resolved one query for the whole read rather than one per address.

The **terse** form carries a `note` line per open note. A note only the JSON carried would be a note
this surface does not have: an MCP session reads terse, and the correction has to reach it there.

**`include=index`** (SH1063) is the sixth rider, and it answers the question search exists for:
`enabled` (does this instance index at all), `searchSite` (where this page's content belongs, absent
when the site routes nowhere), `indexed` (published *and* routed *and* switched on, the three that
have to be true for a page to be sent) and the last indexing failure recorded against this address.
Three different answers to three different failures: switched off is a decision, routing nowhere is
the defect that raises no error, and a recorded failure is the push that happened and did not land.
Opt-in like every rider, because an instance that does not index would otherwise pay for a block
saying so on every row. The failure comes from the incident window in memory, so an old one that has
scrolled out reads as none, the same thing the diagnostics read says about it.

**`include=working`** (SH1008) is the rider beside it, and the same shape for the opposite reason: a
note is what somebody asked of this page, and this is who else is on it *now*. A run that wrote to
the address inside the last minute is reported with its name, its declared intent and an `until`, and
nothing is reported otherwise. A session about to rewrite a page learns another writer is here
**before** it plans, where SH985's `expect` tells it afterwards. It is not a lock and cannot become
one: no write path may read it, and `ShWorkingMarkTest` fails the build naming the file that does.
Full rationale (why derived rather than declared, and why the window is a constant) is in
[content-api § Who else is on this page](./content-api.md#who-else-is-on-this-page-awareness-not-co-editing-sh1008).

**`GET /api/v2/note/open`** is the work list, and every row names its page by **address**, a note
names something somebody has to go and fix, and a uuid names nothing anybody can act on. It is
scoped by site so a pull can project one site's, and filtered by kind so `request` is askable on its
own.

## One query vocabulary, four surfaces (SH1022)

`/agent/find` filters by **the words every content listing filters by**: `site`, `folder`,
`subtree`, `type`, `state`, `locale`, `missingLocale`, `modifiedSince`, `modifiedBefore`, `author`,
`modifier`, `submission` and `q`, parsed by `ShContentQuery`. The same words are served by
`shio_find`, by `shio find` and (minus the two the path already answers) by the console's
`GET /api/v2/object/{id}/list`, and they mean the same thing on each.

**`submission` is what arrived through a site's form (SH1066):** `true` for only the posts the form
endpoint wrote, `false` for none of them, absent for both. A submission is a post (SH633) and has no
column of its own, so the word reads the owner the endpoint records, `form`, or `form-token:<id>`
when a FORM token sent it, through `ShFormSubmitter`'s constants, which the JPQL predicate is built
from rather than copying. A value that is neither `true` nor `false` is refused, not guessed.
`verify` reports **`form-destination-missing`** (an error, on `site:<name>`) when a site's form is
enabled and its folder does not resolve: the endpoint refuses every submission to such a site with a
500 only the visitor sees. It resolves through `ShFormDestination`, the endpoint's own resolver, and a
switched-off form is not reported, since an operator may still be setting it up.

**`agent` is what an agent run wrote (SH1024):** `true` for only the posts with an audit row carrying
an agent session, `false` for none of them, absent for both. It exists because the console's author
filters offer "written by an agent", and a console-only filter is what P1 forbids. So it landed on
every surface: `shio_find`'s `agent`, `shio find --agent`, REST, and the browser's checkbox. It reads
`ShHistory.agent_session_id` through `ShHistoryRepository.AGENT_SESSION`. That is one spelling, used
by the activity feed's attribution filter, the presence marks and this predicate, and it excludes a
`console:` session, which is a curator publishing their own work (SH996). The trash asks it a row at
a time (`existsAgentWrite`), since its rows are outside the predicate. The `shio_find` argument was
paid for by trimming five of the tool's descriptions, not by raising a reservation.

**`missingLocale` is the coverage question (SH1060):** the pages whose translation group has *no*
member in that tag, "what is still to translate into pt-br" as a predicate rather than a scan
whose cost grows with the site. It answers on the group's **source** (`p.id =
p.translationGroupId`, SH383's anchor), so a page already in three languages and missing a fourth
is one row and not three: a work list with one entry per thing to do. It composes with the rest of
the vocabulary like any other word, and the trash listing, the one read that cannot use the
predicate, since a trashed row is outside that index, asks the same question a row at a time
rather than dropping the filter. `author` is the `owner` column, `modifier` the `modifier` column; both
match case-insensitively. The window is **inclusive below and exclusive above**, so two adjacent
ranges partition the timeline instead of overlapping on one instant, and it reads
`coalesce(modifiedDate, date)` because "changed this week" that excludes what was *written* this
week is the answer nobody means. A bare `2026-09-01` is midnight UTC, the only reading that does
not depend on where the server is, and the full ISO instant is what the answer returns, so a value
read out of one call goes straight into the next.

**An unknown parameter is a teaching 400.** Spring binds what it recognises and discards the rest,
so `?modifedSince=` used to return every row with a `200`, indistinguishable from a filter that
matched everything, and an agent has no screen to notice the count on. `ShContentQuery.of` is given
the request's whole parameter map for exactly that reason. Two refusals, because two things go
wrong: a word nobody spells is a typo and `didYouMean` closes it; a word the *vocabulary* spells
and this surface does not serve says where it does work. `sort`, `page` and `size` are the second
kind here, this listing's cursor is an address, so it cannot be ordered by anything else.

**`subtree` is `null` when unsaid, and unsaid keeps each surface's own default.** `/agent/find` has
always meant the folder and everything under it; the console listing has always meant direct
children. One word, one meaning, two defaults that were each already true, changing either to make
them agree would break every caller to win an argument about symmetry.

**The predicate is the database's.** `find` used to load both state rows of every post in the
readable sites and filter the list, so `folder=` narrowed the payload and not the work.
`ShFindQueryBenchmarkTest` measured it before the change and prints the ratio on every run: the
unfiltered read is what a folder listing, a state listing and every new filter used to cost. Two
things stayed in memory and both are stated in the code: **trash**, whose native query bypasses the
soft-delete restriction, and **release** membership, which is already bounded by the release. SH545's
`published` field survived the move, free when both rows are in hand, one id-and-state projection
when `state` narrowed the query so that they are not, and never a query per row.

## `asset-no-alt`: the accessibility rule that could fire (SH1031)

`verify`'s `content` check reports a `File` post that holds an image whose `ALT` is blank. It
exists because `image-no-alt` could not: the File type declares `ALT` optional, the recommended
`{{#image}}` helper wrote an empty alt when it was null, and `image-no-alt` fires only on an absent
attribute. Three defensible decisions composing into a rule that is structurally unreachable on a
site built the recommended way, the shape worth recognising, because nothing about it looks wrong
from inside any one of the three.

**Over the asset, not the page.** Alt text is a property of the image: the same photo on twelve
pages needs describing once, and a per-page rule would report it twelve times and be fixed in
twelve places. It also needs **no render**, which is what makes it cheap enough to be on by default
and what lets it name the asset the curator has to open.

**A publish check, never an upload refusal.** Uploading without alt is fine and must stay fine,
every capture and every conversion run uploads in bulk, and refusing there would break the two
paths this product exists to make easy. It fires where the decision is being made: the gate a site
runs before content goes live.

It reads the stamped content type first (SH1030) and the file extension second, so it costs no
syscall per asset, the difference between a check that ships on and one nobody turns on.

## `asset-unused`: the orphan rule assets did not have (SH1034)

`verify`'s `content` check warns about a `File` post nothing references. It is late because it was
unaffordable: "does anything reference this asset" was `data like '%<id>%'` over every post, so
asking it of a library meant one table scan per asset, SH1033 measured the single form at 50,002
rows examined on a fifty-thousand-post instance. On `sh_object_reference` it is one indexed query
for the whole set, which is the only reason the rule exists.

**The count is instance-wide, and a scoped run must not narrow it.** This is SH143's rule in the
case that would break it worst. A shared logo referenced from another site is the normal case, and
a count computed inside the scanned site would report it as unused, with "delete it" as the fix.
The index has no site dimension, so the count always answers for the instance; only *which* assets
are asked about is scoped.

**A warning, not an error**, and `empty-folder`'s reasoning exactly: nothing is broken, the bytes
are served, the site renders. What an unreferenced asset costs is storage, and a curator may be
holding it deliberately, which is why the fix points at `shio assets prune --dry-run` (the sum,
computed, deleting nothing) before it mentions a `post.delete`.

**One row per logical asset.** A draft and a published row of one upload are one file on disk;
reporting both would say "two unused assets" about one.

**What a rendered page loads is used (SH1268).** The index is fed by File and Content Select
fields, so a theme's `@font-face`, a CSS background or a `src` a layout writes as a site path never
counted, and a site that self-hosts its fonts was told to delete files every page fetches. When
the `page` check runs in the same call, every reference it resolves to a site asset is recorded and
`asset-unused` is withdrawn for those ids, so `checks=content,page` (and the default set) answers
correctly. A `content`-only run still reads the index alone and adds a note saying so, rather than
rendering pages it was not asked to. `shio assets prune` and the media library's `usageCount` still
read the index only; that disagreement is SH1280.

## Describing an asset, and asking where it is used (SH1032)

Two capabilities lived on `/api/v2/staticfile/**` alone, the metadata a curator types into the
Media Library dialog, and the "used in" list that says whether an asset is safe to delete. No op,
no CLI verb and no `features` key named either, which is the part that makes this a P1 story rather
than a missing-feature one: **`ShAgentP1ConformanceTest` classifies manifest feature keys**, so a
capability with no flag is not unclassified, it is invisible. The instrument built to find exactly
this gap could not see it.

So the flags landed **first**, before any reach existed, and the build went red naming
`assetMetadata` and `assetUsage` as unclassified. That is the order to copy: declaring the
capability is what turns "nobody noticed" into "the build says so".

### `asset.meta`: why an op and not an argument on `post.upsert`

`post.upsert` fails twice here, and the first failure is structural. A `File` post has **no friendly
URL**, the type's design, the reason `find` addresses assets `id:` and the reason SH501 exempts
them from `url-missing`, and writes take `post:<site>/<url>`, so there is no address an upsert of
an asset could carry. Then, even reachable, an upsert **declares whole state**:
`ShPostUnifiedWriteService#update` calls `setData` with exactly what it was sent. A caller sending
only `alt` would drop `FILE`, `WIDTH`, `HEIGHT`, `SHA256`, `SIZE` and `CONTENT_TYPE`, the file name
the bytes are served under, the intrinsic size `{{#image}}` needs to stop the layout shifting, the
digest `pull --content` compares against, and the content type the media library filters on. The
asset would still list, still resolve, and serve nothing.

`asset.meta` therefore takes `id:` (or `post:`, for an asset a curator gave a URL by hand) and
**merges** three keys through `ShStaticFileService#updateMeta`, the one writer of a `File` post's
metadata, which is what keeps the console and the agent meaning the same thing by a blank value.
Addressed to something that is not an asset it refuses `not-an-asset`, on the **dry run too**: a
plan that reported "would describe this" about a page is the plan P9 exists to stop being trusted.

### Metadata edits write an audit row

`replace` has recorded one since SH210 and `updateMeta` did not, so describing an image was the one
content write reaching neither the change feed nor the SH109 review queue. That was invisible while
the console was the only caller, a curator who typed the alt text knew they had typed it. An agent
is a caller now, and P7 says the curator is never surprised by what one did. The row names the
changed keys (SH989's overload) rather than saying "metadata", and **nothing is recorded when
nothing moved**: a re-send of the stored values would otherwise put a change in somebody's feed.

### `usage` on `shio_assets`, not a thirteenth tool

"Where is this used" is asked in one situation, before replacing or deleting an asset, about assets
the caller is already listing, so it is a flag on that call. It is **off by default** because it is
a query per row and the default listing is the browse. `used 0` is written rather than omitted,
which is the opposite of how `sanitized` and `unsafe` are treated and deliberate: deletion is
authorised by the *absence* of referrers, so the one reading that ends in data loss must not be
inferred from a line that might simply be missing. Draft referrers are reported with a `draft` flag
rather than flattened away, an asset used only by an unpublished page is not unused.

**What this cost, since both ceilings moved.** `asset.meta` is ~4 tokens in `shio_write`'s enum and
`usage` ~15 on `shio_assets`, which took `tools/list` to 2939 (2920 → 2960) and `shio_write` to 493
(`max-per-tool` 490 → 520). The two new `features` keys took `manifest.check` to 405 (420 → 450).
The ops vocabulary went to 952 against 950, paid for by the trim SH733's note predicted: five ops
each spelled out "Needs `confirm` from its own dry run", and the grammar header now says it once.

**Still on REST only:** the metadata does not yet round-trip through `shio/content/**`. An asset
projects as bytes and nothing else, so a sidecar carrying `alt`/`caption`/`tags` under SH1004's
merge rule is a file-format decision of its own and is the open half of SH1032.

### `folder.acl`: who may edit a section, declared (SH1277)

`data.acl` is a list of grants, each naming exactly one of `group`, `user` or `role` and a
`permission` of READ, WRITE or PUBLISH, and it **replaces** the folder's grants (an empty list opens
it again). A desired-state folder entry carries the same list as `acl`, so a blueprint's
`content/*.json` and a `shio apply` document declare it the same way, and `compile` emits the op
after every folder op and before the posts it then governs. Grants equal to the folder's current
ones are a **noop**, which keeps a re-applied package quiet. It is guarded by WRITE on the folder,
the console's own rule for `PUT /object/{id}/acl`, so the agent path grants nothing a curator could
not. A grant to a user or group the instance does not have is `unknown-acl-principal`, refused in a
dry run too, because a grant to nobody locks the folder against everyone but an administrator; a
role has no name lookup and fails closed the same way.

**What it is not:** a visitor gate. Content ACL decides who edits and publishes; an anonymous read is
never denied by it. A members area on a delivered site is `folder.visibility` (SH1285, below). **Cost:** `folder.acl` in `shio_write`'s enum took `tools/list` over
its ceiling by 4 and left `shio_write` one token short of an argument's headroom, paid by trimming
`shio_write`'s description, not by raising either number.

### `folder.visibility`: who may read a section's delivered pages (SH1285)

`data.visibility` is `public` or `members`; `data.groups` (optional) names the groups a members-only
section is for, and none means **any signed-in console user**. A member is a console user (the
owner's decision, 2026-10-29): no separate site-member account, no self-registration. It **replaces**
the folder's gate, a declaration equal to the current one is a **noop**, it is guarded by WRITE on the
folder like `folder.acl`, and a group the instance does not have is `unknown-member-group`, refused
in a dry run too. A missing `data.visibility` is `missing-op-field`; any other value is
`invalid-visibility`.

**A separate op, not a key on `folder.upsert`/`post.upsert`.** The design said the flag rides the
upserts; `folder.acl` had already set the precedent of one op per rule, and a reserved `visibility`
key in `post.upsert`'s `data` would collide with a field of that name. The owner kept visibility a
separate rule from the authoring ACL, so it is a separate op too.

**Where it lives.** `sh_content_visibility` (`v2026.3.51.yaml`), one row per gated folder or post:
`object_id` and `member_groups` (newline-separated). `ShContentAclService.visibility(id)` decides,
walking the folder chain **by id** exactly as the ACL does (SH650), nearest gate wins. Unlike
`canRead`, **no user is a visitor**, not a trusted internal call, because the question is asked on the
anonymous delivery route. An administrator always passes.

**Enforced on `/sites/**`** (`ShSitesAPI.serve`), right after the render and before the locale
redirects: a visitor outside the groups gets the **same `render-page-unknown` 404 a missing page
gets**, so the denial does not confirm the page exists. A member's response is
`Cache-Control: private, no-store`; a public page keeps `public, max-age=300`. A console session
reaches the check because `/sites/**` is `permitAll`, not unauthenticated.
`ShSitesMembersOnlyTest` asserts all four readers with the security filter chain applied.

**One post (SH1300).** `post.visibility` is the same gate on one post, with the same `data`,
noop, WRITE guard and refusals; both ops share `gateOf` and `replaceGate` in the batch service. The
owner chose an op of its own over a reserved key in `post.upsert`'s data (access is not a field
value) and over widening `folder.visibility` (its name would stop saying what it does). Storage and
every delivery check needed nothing: the walk starts at the object, so a post's own gate wins over
its folder's. A post the batch has not written yet plans as an update in a dry run and is the
resolver's 404 in a real one. Paid for under SH346 by tightening `post.upsert`, `post.move` and
`folder.copy` to their decisions, and under `tools/list` by four more words off `shio_write`.

**Sending a visitor to sign in (SH1299).** `ShSite.membersLogin` (`v2026.3.52.yaml`, nullable,
null = off) is the per-site switch the owner chose: when it is on, `/sites/**` answers an
**anonymous** visitor a gate refuses with a 302 to `/login?returnUrl=<the page>` and
`Cache-Control: no-store`; a signed-in user outside the groups still gets the 404, because signing
in again cannot help and a redirect would confirm the page. The console's login already took
`returnUrl` (the axios 401 handler builds it) and followed **any** value; it now follows only a
path on this host (`shio-react/src/lib/return-url.ts`), which closes an open redirect that predated
this task. The switch is `data.membersLogin` on `site.upsert` under the same fill-or-keep rule as
`url` (`ShSiteWriteService.FILLABLE`), and the console's site update replaces it. On a Keycloak
instance the login page hands off to the identity provider without `returnUrl`, so the visitor
lands on the console home after signing in rather than on the page.

**On the CDA, a member is the owner of the token** (SH1295; `ShApiTokenFilter` puts the owner's username in
the principal). `ShCdaService`'s single reads (`getPost`, `getPostByUrl`, `path`, which GraphQL's
`post` also uses) answer a gated post the owner may not read as **absent**, the missing-post 404.
`/post/{id}` and `/post/by-url` send `Cache-Control: private, no-store` for a gated post, overriding
the cache filter's `public`. A token's authorities are its scope's and never `ROLE_ADMIN`, so
`visibility()` also recognises an administrator by the **Administrator group** (or a `ROLE_ADMIN`
role), as `ShAuthorityResolver` does at sign-in: otherwise a page an admin reads on `/sites/**`
would be missing from that admin's token.

**CDA listings (SH1296).** Every listing row passes `ShCdaService.readableOnly`, so a folder list, a
query, a search and GraphQL's `posts` drop the posts the owner may not read; a gated folder lists as
absent (404) and is left out of its parent's `folders`. On an instance with **no gate at all** this
is one `count()` and no per-row walk (`ShContentAclService.anyGates`), which is what keeps
`ShCdaAvailableLocalesBenchmarkTest` where it was. **Totals are exact (SH1297):** a reader who fails
at least one gate (`ShContentAclService.deniesAnyGate`) takes the site-wide reads' unpaged branch,
the one preview reads already used, and `draftPreferred` filters before it counts, so `totalPosts`
never reveals how many gated posts a site holds. Every other reader keeps the paged query.

**Not yet:** the sitemap and the agent's public reads still include gated pages; a per-site redirect to a login page instead of the 404; a gate on a single
post; the flag in `_folder.md` and post frontmatter; and the console's folder settings. Those are
SH1285's remainder. **Cost:** `folder.visibility` took the ops vocabulary from under 1000 to 1014
(SH346's ceiling) and `tools/list` past its 2975; paid by trimming `render.provision`,
`post.rollback` and `release.rollback` to their decisions and `shio_write`'s description by four
words, not by raising either number.

### `shio_assets` refused every listing that held an asset (SH1098)

Found by SH1032's live upload, and it had been true since SH951 shipped. `ShStaticFileService`
writes `SANITIZED` into a `File` post on its two sanitize paths; `fileFields()` never declared it;
SH168 makes an undeclared field an **error**. `shio_assets` reads `FILE` and `SANITIZED` together,
so the tool an agent uses to choose an image refused (on every instance) the moment there was an
image to choose.

Nothing saw it, and each of the two reasons is worth more than the bug:

**The suite could not reach it.** `shio_assets` returns early when the listing is empty, and no
conformance scenario had ever uploaded an asset. Every green run exercised the one branch that
cannot fail. A tool with a guard clause is a tool whose fixture has to get past the guard.

**The lint could not see it.** `ShFilePostTypeSourceTest` is the instrument built for exactly this
question, and its javadoc promised "a key added tomorrow fails on its own commit", true only for a
key *named* `DATA_`, because it reflected `ShStaticFileService`'s constants by prefix. `SANITIZED`
is `ShSystemPostTypeAttr.SANITIZED`, a constant on another class, so four separate passes over this
same question (SH474's four keys, SH478's three, SH1030's `CONTENT_TYPE`) each re-ran a reflection
that structurally could not find it.

That file's own history is the lesson repeating one size smaller. SH478 removed a hand-kept list of
"fields we have not declared yet" because *a declared list records a belief; it cannot check one*,
and replaced it with a naming convention, which is a belief about the code. The scan now reads the
**call sites**: every `data.put`/`putArray`/`remove` in the service, resolving the key through
whichever class declares it, and counting a bare string literal too. What the writer writes is a
fact about its statements, so that is what is read.

## `checks=a11y`: the page as somebody who is not looking at it receives it (SH1055)

A CMS's real accessibility exposure is the pages it **publishes**, and verify had one rule about
them. The family is five rules read off the rendered document: `heading-skip`, `lang-missing`,
`link-no-text`, `landmark-missing`, `form-label-missing`. Each is about the *document* rather than
about any post's fields, whether the layout declares a language, marks a main region, composes an
outline, which is why it renders rather than reading stored values, over the same subjects and the
same cap as `page`. `ShHtmlAccessibility.of(html)` is pure, so the rules are testable without a
site.

**Its own group, not rules inside `page`.** A team running a publish gate asks for accessibility by
name, and on a site with a thousand pages the cost is a caller's decision. A group is a cost
boundary in this vocabulary.

**`image-no-alt` and `asset-no-alt` stayed in `content`,** which reads as an inconsistency and is
not one. Both need no render (one reads a stored field, the other the asset table) and that is
what makes them cheap enough to be on by default. Moving them would mean `checks=content` quietly
stopped reporting undescribed images for every caller already asking for it. Contrast, which the
design named, needs a theme's declared token pairs resolved to colours: that is SH1051's subject,
and a rule that guessed at the pairs would report on the guess.

**Every rule is conservative, and the fixture that proves it is the second one.** A rule that fires
on a page that is fine is a rule somebody turns off, and the four that were right go with it. So a
link named by the `alt` of the image inside it, a control wrapped in its own `<label>`, a
`role="main"` instead of a `<main>`, an `<a name>` with no `href`, a fragment with no `<html>`, all
real, all common, all silent. `ShHtmlAccessibilityTest` runs the one-of-each page and then the
*correct* form of each of those same shapes. Only the first heading jump is reported and unnamed
links are deduplicated per `href`: a page whose sections are all one level too deep is one repair,
not forty sentences.

**A page that will not render is skipped here**: `page` renders the same pages and has already
reported it, and a second finding under an a11y rule name would make one broken template read as
two defects. That is the one silent catch this added (`ShSilentCatchInventoryTest`, api 80 to 81).

### What it found on its first run: our own blueprint

`marketing-site`: the blueprint the headline benchmark applies, and the starting point this
product recommends, produced pages with **no `<main>`** and **no `<h1>`**: the section template
emitted `<h2>` for every section, so the first heading on every page skipped a level. Both rules
fired on all three published pages, and the repair is two lines of the layout: the section loop
wrapped in `<main>`, and `{{#if @first}}` choosing `<h1>` for the first section's heading, the data
frame `getRelation` has published since SH772, used for the case its own note names.

The measurement is the argument for repairing the blueprint rather than raising the budget.
`ShAgentTaskBenchmarkTest.buildMarketingSite` came to **4432 tokens against a 4000 ceiling** with
the findings, and **3972** with the blueprint repaired, under the ceiling, which did not move. Six
warnings about Shio's own template would otherwise have been the first thing every agent building a
marketing site read.

## The redirect a rename needs (SH1064)

`Redirect` has been a system post-type since SH385: the renderer serves it, its `PERMANENT` flag
chooses 301 over 302, and `verify` reports a target that resolves to nothing. **Nothing ever wrote
one.** Grepping the write service and the batch service for the type name returned the renderer and
the lint and no writer, so renaming `/pricing` to `/plans` answered 200 at the new URL, 404 at the
old one, and said nothing, every bookmark, search result and inbound link from somebody else's
site broke silently. The content-files side was further along (a post carries `movedFrom`, so the
three-way merge follows a rename) and that instructs a merge; it routes nothing.

**`post.redirect`** is the seventh post verb: the address is the **old** path, SH385 gave the type
no `from` field, so a redirect's source is its own friendly URL, and `to` takes an address, a
site-relative path or an absolute URL. `data.permanent:true` asks for a 301; the default is 302,
which is **SH385's rule and not a new one**, the first draft of this op inverted it ("a rename is
not coming back"), which is the argument SH385 had already weighed and declined, because a
permanent redirect is cached by browsers effectively for ever and a wrong one is the hardest
routing mistake to take back. Two defaults for one field, differing by which door wrote it, is
also the split P5 exists to stop.

Three decisions worth keeping:

- **A name, not a flag on `post.move`.** A batch is all-or-nothing and applied in order, so a move
  followed by a redirect at the vacated address *is* one transaction. A flag would also have been a
  second way to write a `Redirect` beside `post.upsert type=Redirect`. Measured before it was added,
  because the vocabulary is re-sent every turn: an op name costs ~4 est. tokens inside
  `shio_write`'s enum (SH977's cheap rung) and the list reads 2968 of 2975 after it.
- **It publishes**, which is the one place this surface departs from draft-default. A draft redirect
  routes nobody: the renderer serves published rows, so the op would report success while the old
  links went on 404ing. The change row says so rather than leaving it to be found.
- **A page at that address is refused** (`redirect-path-occupied`), not overwritten. Two posts on
  one URL is SH99's `duplicate-url`, and the refusal says to move the page out of the way in the
  same batch, before the op.

**`verify` gains `url-orphaned`**, under `checks=routes`, and it is an **error** where
`link-unresolved` is a warning, because here the CMS is not guessing. The evidence is the revision
table: a revision captures the page as it was, `furl` included, so the addresses a site *used to*
answer are its distinct revision URLs minus the ones it answers today. An address in that set with
a published page still linking to it is a 404 on a route this instance served, and the finding
carries the `post.redirect` op that repairs it. One finding per address rather than one per link,
the subject is the URL and the pages linking to it are its count. A **full** run only, for
`no-home-page`'s measured reason (SH334): it is a question about the site, and a scoped run would
attach a paragraph about the estate to a task about one page.

**What it cannot see**: a rename on an instance that captures no revisions, and a page deleted
before any revision existed. The rule reports what it can prove and is silent otherwise, which is
the direction SH410 points.

## The editable read is on a budget (SH1104)

`/agent/editable` had no response budget from SH809 onward, and SH1039's `include=insertable` made it
scale with what a layout declares. `ShAgentResponseBudgetTest` now measures it twice, as
`response.editable` (22) and `response.editable.insertable` (192). The catalogue has its own row for
SH987's reason: a shared ceiling would let the plain read grow into the option's room. It measures a
page that renders: the budget pages' type is bound to a layout whose `<main>` accepts two section
types. `theEditableBudgetMeasuresAPageThatComposes` holds that binding, because an unbound page
answers `source: content`, envelope that passes any ceiling. That fixture is seeded **after** every
other response is measured. Post types are instance-wide and the context pack lists them, so seeding
them first moved `context.json` from 182 to 212 with no product change.
