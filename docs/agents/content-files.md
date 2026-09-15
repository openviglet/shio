# Content as files — shio/content/**

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### Content as files — `shio/content/**` (Block I)
Content projects to **one Markdown file per post**, `shio/content/<site>/<url>.md`: YAML
frontmatter for the metadata + `fields:`, and the post's first `HTML Editor` field as the
document body (the file records which field that was as `body:`, so reading it back never
needs the model). Owned by `cli/src/content-files.mjs` (the format, pure),
`content-sync.mjs` (the three-way merge, pure) and `content-store.mjs` (disk + wire).
- **The projection's field set is a table with a test (SH348).** Ten keys —
  `bodyField, data, folder, locale, movedFrom, publish, sidecarFiles, site, type, url` — and everything
  a post carries that is *dropped* is declared with a reason in
  `cli/test/content-projection-coverage.test.mjs`. **`locale:` is written only when the server reported
  one**, and `/agent/read` reports it only when it is not the instance default, so a monolingual tree is
  byte-identical to before SH348 and a translated page can finally say so. `post.upsert` honours it **on
  create only**: relocalizing an existing post would make a routine push change what language a live
  page claims to be in, and create is where the loss was — Block I promises the tree is a complete
  description of a site, so a tree that could not say `pt-br` rebuilt the page as `en` elsewhere.
  **Two locales of one page are two URLs**, because SH323 already forces that (a friendly URL is
  site-unique and an address carries no locale) — so one file per URL needed no new convention. The
  sibling link is **`translationOf: <url>`**, an address and never a group id (SH150's shape): every
  translation names the group's *anchor*, the anchor names nothing, and the server orders a declaring
  post after the others so the author never has to sequence the tree. `translationGroupId` stays out
  of the tree deliberately — a UUID in a file is what Block I exists to remove. The table exists
  because SH255's round-trip guard tests values of fields the format **has** and cannot see a field it
  **lacks**.
- **Two memory files, and the difference between them is a permission (SH161, SH231).** A
  *site's* notes project to `shio/content/<site>/.memory.yaml` and are reconciled like a field
  — three-way merged, a local deletion honoured as a forget. The **instance's** notes project
  to `shio/.memory.yaml`, beside `content/` rather than inside it, and are **not** reconciled:
  a pull writes the file, and `push --content` reports it and leaves it alone unless the
  operator passes `--adopt-memory`. That asymmetry is the whole design. An instance-wide note
  is a house rule of the installation that exported it — "copy is authored in files here",
  "this deployment publishes on Fridays" — so merging it into a target instance is the surprise
  SH161 refused; but leaving it with **no export path at all**, which is what SH161 did, made
  the least reproducible thing in the system the only thing that could not be moved. So it
  travels as a file and adopting it is a decision somebody makes. The push says so on every run
  that finds the file and not only when it acts, because a file silently ignored is how an
  operator learns by editing it and watching nothing happen (SH138). An instance with no house
  rules gets **no file** rather than an empty one — an empty file reads as "there are none"
  when it means "we never asked".
- **The path is the URL, not the folder chain.** A URL is unique per site, so the tree
  cannot collide; the folder travels in frontmatter because SH79 keeps the two namespaces
  apart. A file whose path and `url:` **disagree** is an error — a half-done rename is a
  move on one side and a create on the other (SH134's lesson).
  **Except by the trailing slash, and that exception is what `url:` is now for (SH585).** SH467
  made an instance able to store `/model-catalog/`, and a filename has no room for it: `/x` and
  `/x/` are one path, which is this economy's whole point. So for one commit Block I's promise was
  false — a `pull` produced a tree that, re-applied to an empty instance, rebuilt every
  directory-style page one character short and reintroduced the reference defect SH467 closed.
  Nothing was *broken* by it, which is why it was a task and not a follow-up: SH467 honours the
  spelling on create only, so a push sent the collapsed URL and `findPost` resolved it through
  SH583's candidates. The tree could not un-do the slash; it could not state it, and that surfaces
  one rebuild later on a machine that no longer has the capture.
  Three parts. The read reports it — **`ShAgentDoc.url`**, absent whenever it equals the address's
  own path, which is `locale`'s P3 shape with a stronger argument: the default is not merely
  knowable, it is *already on the row*. The format carries it — `serializePost` writes
  `url: storedUrl(...)` while `urlToPath` is untouched, so the file is still `model-catalog.md`.
  And the disagreement refusal above needed **no change at all**: it compares both sides collapsed,
  so `url: /x/` over `x.md` already passed and `url: /y` over `x.md` already did not. "May differ
  only by the slash" was the existing rule, discovered rather than written.
  **`normalizeUrl` was not touched, and the count is why.** It has 33 call sites and they were
  written as if interchangeable: about half normalize a **folder** chain — a different namespace
  SH467 does not touch, which must *never* keep a slash — six derive a URL from a path where there
  is no slash to keep, and the rest compare two URLs, where collapsing is right (`convert`'s SH584
  join asks "is this path written", and a page written at `/x/` does answer `/x`). Teaching one
  function to sometimes keep the slash would have changed 33 to fix 3. So `storedUrl` is the narrow
  second one, named to match the pair the server already keeps: `ShFurlSpellings.canonical` is
  `normalizeUrl`, and `ShFurlSpellings.stored` is this.
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
  as legitimate non-holding — it is the only one that cannot lose work silently.
  **And since SH258 an entry names *which* refusal it expects**: `{ why, refuses: /pattern/ }` for a
  throw whose message must match, `{ why }` for a lossy carry that must **not** throw. Before that a
  `throw` satisfied "the loss is declared" whatever it said, so the intended refusal, a refusal from
  three layers down blaming a symptom, and a `TypeError` in the check itself were indistinguishable
  — and the middle one is what actually happened during SH257 (`siteDir` trimmed, `parsePost` then
  complaining that a file disagreed with its own path), found by *reading* rather than by running.
  The pattern pins only the part that identifies the rule; this is **not** verbatim message
  assertion. The weak case is named in the table itself: `assetPathFor` answers `null` for two rules,
  so those entries can pin the function and not which of the two fired.
  Three are conversions (a body's CRLF and lone `\r`, an empty note) and the other sixteen are **refusals**,
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
  (values in `fields:`), so the artifact a curator diffs is one file.
  Since **SH252** a `Theme`'s `CSS` is declared `Ace Editor - CSS`, so it lands in a `.css` — and the
  projection needed no change for that, because this map keys on the *widget*. Note the limit:
  `ShRenderPostTypes.ensure` never touches an existing post-type (SH152), so an instance that already
  created its `Theme` keeps the old widget and the old `.html` sidecar until the field is changed by
  hand.
- **`shio pull --content`** merges three ways against `shio/.shio/content-base.json` (the
  last-synced base + the SH88 cursor): one side changed → that side, different fields →
  merged, same field → **conflict**, which writes nothing, exits 1, and offers
  `--ours`/`--theirs`/`--show-remote`. **There are no conflict markers on purpose** —
  `<<<<<<<` in a field value is content the next push would publish.
- **A fingerprint collision is a reported conflict, not a dropped edit (SH215, snapshot
  `version: 3`).** A fingerprint is `<16 hex>.<canonical byte length>`, and the length is **not**
  there to make a collision rarer — SH148's risk was accepted deliberately and is the one every
  hash-based sync takes. What was not deliberate is *how* a collision failed: two values hashing
  the same read as "this field did not change", so the edit was dropped and the run reported
  success. Compared as one string the length would be sixteen more bits; compared **part by part**
  it is a proof — equal hashes with unequal lengths cannot be one value, so
  `compareFingerprints` answers **`"collision"`** and the merge turns it into a conflict that names
  itself. Three rules:
  - **`collisions` travels beside `conflicts`, not instead of it.** Everything a conflict causes is
    what should happen (nothing is written, the run stops); the collision is the *reason*, and it
    gets its own line because the remedy is different — the base cannot describe the value it
    indexes, so the base is what has to be rebuilt, not a side that has to be picked.
  - **`same()` compares field by field now.** A whole-digest string comparison answered "not the
    same" for a collision by accident, and it would read a v2 base's length-free fingerprints as
    different from every new one.
  - **A missing length means unknown, never different.** A `version: 2` snapshot compares by hash
    alone — exactly what SH148 shipped — because a version that refused would make an upgrade read
    every field of every project as changed, which is the drift the merge exists to prevent.
  - The defect is pinned as a test in both directions (`content-sync.test.mjs`): the collision is a
    conflict, and with the lengths stripped the *same* state is silently read as `remote-changed`
    with the local edit gone.
- **The base is per-field fingerprints, not values (SH148).** It is only
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
- **Renaming a folder's directory is a move (SH211), and it is SH150's defect one level up.**
  SH149 gave a folder a marker; it did not give it a move, so renaming
  `shio/folders/Blog/news/_folder.md` to `.../updates/_folder.md` read as one folder gone and one
  arrived — `--prune` deleted `/news`, and **a folder delete takes every post beneath it**
  (`ShFolderWriteService` cascades), where the post case lost one post's history. The document
  now carries it on the folder entry itself: `folders: [{path, movedFrom}]`, compiled to
  `folder.move`. Five things to know before touching it:
  - **`movedFrom` is on `Folder`, not in `moves`.** A folder's whole name chain *is* its address,
    so its last segment is the new name and rename and relocate are one op (SH137) — the
    destination is the entry's own `path` and there is nothing for a second field to say.
  - **The compile order splits in three, and has to**: plain folder upserts, then the folder
    moves, then the upserts of the folders that moved. Upserting a move's destination first
    *creates* it and `folder.move` refuses an occupied target, so the rename would fail on the one
    path it was asked to produce. The destination's **parent** must still exist first (a move
    never creates one) and does — it is an ancestor, so it is a plain entry. The third phase is
    not redundant: after a **noop** move, where a previous run already did it, it is what makes
    the folder exist at all.
  - **The source is protected from `--prune`** for SH150's reason with a worse outcome: the sweep
    reads the database before any op runs, so the renamed section is still at its old path and
    nothing declares it. Unprotected, the *plan* would say "deleting folder:Blog/news" and SH108's
    confirm digest is taken over exactly that list — a rename would ask the operator to confirm
    destroying the section it is preserving.
  - **Detection is by subtree shape, not by content**, because a folder has no field values to
    compare. `detectFolderMoves` pairs one gone root with one appeared root when their relative
    descendants match, which makes a section rename with subfolders **one** move rather than one
    per folder; failing that, one of each left over is still unambiguous. `movedFrom:` in the
    marker is the explicit signal and wins.
  - **The ambiguity refusal is scoped to `--prune`, and that scoping is the design.** More than
    one candidate on either side is reported and nothing is moved; whether it *stops* the push
    depends on whether anything is about to be deleted, because refusing a push that destroys
    nothing would block the legitimate case — one section removed and two added — with no way for
    the author to declare that neither is a rename. `--force` is the way past it with `--prune` on.
    The one ambiguity that is always fatal is a `movedFrom` naming a folder the tree **still
    declares**: that is a contradiction, not an unproved guess.
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
- **An unprovable rename can be accepted once (SH214).** SH150 warns when a vanished post and a
  new one *might* be the same rename but the content differs, because the alternative — a delete
  plus a create that destroys history in silence — is worse than a false positive. For a
  legitimate delete-plus-create that printed on every run for ever, and a warning that is always
  there is a warning nobody reads, which costs the *unaccepted* ones their audience.
  `shio push --content --accept` records the pair in **`shio/verify-baseline.json`** — SH142's
  file, under a second key, `acceptedMoves`. Four rules:
  - **Both addresses are the key.** Accepting "this deletion is fine" alone would silence the
    warning for a *different* new post arriving next week, which is the case the warning exists
    for — a one-sided key turns the acceptance into the blanket suppression SH142 prevents.
  - **Spent, not stale — the one place this diverges from SH142.** A `verify` finding persists
    until the defect is fixed, so an accepted entry the run no longer produces means the file has
    rotted and failing is right. A probable move is **transient**: after the push, the new post is
    on the server and the old one is deleted or declared, so the pair can never be produced again.
    SH142's rule would fail the next run for doing exactly what was asked. So the entry is
    **consumed** by the push that honours it, which keeps SH142's actual goal by a stronger
    mechanism than a check — an acceptance cannot outlive its event. A plan consumes nothing.
  - **One file, two producers, neither erasing the other.** `verify` reads `accepted` and never
    `acceptedMoves`; `push --content` reads only `acceptedMoves`. Both write the whole document,
    so both carry the other's list through untouched — without that, `verify --accept` would
    silently un-accept every rename somebody had reviewed. `acceptedMoves` is omitted when empty,
    so a project that never accepted one has a file byte-identical to SH142's.
  - **`--accept` pushes nothing.** Accepting is a judgement about a destructive operation, and
    folding it into the run that performs it would mean the judgement and its consequence were
    never separately reviewable. Silenced pairs are **counted on stdout**, never quietly dropped.
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
    to the site root. And **a path already on disk is not re-downloaded** on `pull`, because a
    File post's bytes were immutable. An id that resolves to no known asset stays an id and is
    reported.
  - **A path the server already has is compared on push, not skipped (SH210).** That immutability
    was the projection's one silent no-op: `push --content` skipped a known path, so **editing an
    asset's bytes uploaded nothing and reported success**, on the most ordinary thing anyone does
    to an asset. Bytes are now replaceable in place — `ShStaticFileService.replace` behind
    `POST /api/v2/staticfile/{id}/replace`, which keeps the id, the folder, the name and the SH62
    usage list, re-stamps size and dimensions, and audits an `UPDATE` so the SH88 feed carries it.
    Four decisions worth keeping:
    - **Replace, never upload-and-repoint.** The cheap alternative would put two `File` posts at
      one (folder, file name) pair — and that pair *is* the asset's address, so the path would stop
      naming one asset. It also strands the old post, which may be referenced by content this tree
      does not own (the same reason deleting an asset is not projected).
    - **Detection is `size`, then a digest — never a byte comparison.** `GET /staticfile/{id}/digest`
      returns `{id, fileName, size, sha256}`, hashed from the server's own disk. Comparing bytes
      would have the cost profile *inverted*: an **unchanged** asset is exactly the one whose length
      matches, so the common case would download every referenced asset on every push. A differing
      length, and SH62's `size: -1` (the post exists, its bytes do not), are decided for free.
    - **The digest is computed on demand, not stamped at write time.** Stamping is the obvious
      optimisation and a different task: it covers only assets written after it ships, so the
      on-demand path has to exist anyway, and one mechanism beats two that disagree about which
      rows they cover.
    - **`ENOENT` on a path the server already holds means unchanged, not broken.** The
      missing-bytes refusal exists so a push cannot publish a `File` field pointing at nothing, and
      a path in the inventory points at something; a hand-written document naming an existing asset
      it never pulled is legitimate, and refusing it would be a worse defect than the one fixed.
    - **And a dangling reference is a `verify` finding now, not only a push refusal (SH212)** —
      `asset-missing`, in the `projection` check group, which is the first `verify` rule that reads
      the project's own files. The post-types, the posts and the bytes are all on disk, so it is
      provable with no instance: `shio verify --checks projection` is the server-free gate, and the
      group is in the default set so the full run reports it too. The push keeps refusing — it is
      the last net — and its message now names the cheaper gate. Two decisions behind it: it is a
      **finding rather than an exception**, so the whole tree is reported at once and SH142's
      baseline can hold one (it needed nothing new — the entries are `(rule, address, field)`
      triples); and a post whose **type the tree lacks** is a `asset-check-skipped` *warning*
      rather than silence, because an unchecked post must not read as a clean one (SH302's rule).
    - **Two more rules of the same class (SH374).** `sidecar-missing` — a `files:` entry naming
      nothing — and `translation-target-missing`, a `translationOf` naming a URL the tree does not
      hold. Both were provable from the tree and reported only by a gate that needs the server: the
      first as an *exception* out of `readLocal`, the second as the server's teaching 400 (SH348).
      Three things they needed:
      - **`readLocal` grew a `sidecars: "report"` mode.** Its default *refuses* on the first missing
        sidecar, which is right for a write — read as an empty value it publishes a layout with no
        HTML from a push that reported success, and read as an absent field it makes `post.upsert`
        clear the server's copy, because an upsert declares whole state. For a lint that refusal is
        the very shape SH212 replaced: one offender at a time instead of a report of all of them.
      - **`translation-target-missing` is an error unscoped and a warning when scoped to a site**,
        because a scoped projection legitimately holds a translation whose original lives somewhere
        it did not read. "Not checked" and "wrong" are different claims — SH143's rule, and the same
        distinction `asset-check-skipped` makes one line above.
      - **Findings sort by address, then rule, then field.** With three rules now able to fire on
        one post, an order that followed whichever rule ran first is a report nobody can diff (P5).
    - **`duplicate-address` — two files, one post (SH400).** `pathToUrl` drops a trailing `index`
      segment, so `blog/index.md` and `blog.md` both project to `post:<site>/blog`. `readLocal`
      accumulates into a map keyed by address, so the loser was parsed, its sidecars loaded, and
      then dropped with no finding and no refusal; a push wrote the survivor's body under that URL
      and reported success. A **pull never creates it** — `urlToPath` emits `index.md` only for the
      site root — which is exactly why nothing caught it: the tree is the authoring surface (P4),
      and an agent reaching for the `index.md` convention is not told it has spelled a name it
      already used. Same two answers as SH374, for the same reasons: the write side **refuses,
      naming both paths** (delivering one of two files is worse than stopping, because
      `post.upsert` declares whole state), and the lint reports **every** pair. The pair has to be
      carried out of `readLocal` beside `missingSidecars` — by the time `lintProjection` sees
      `posts` the map has already collapsed. Unlike `translation-target-missing` the severity does
      **not** depend on the scope: both files are in the part of the tree that was read, or there
      would be no pair.
    - **`path-collision` — two addresses, one file (SH401).** SH400 closed the tree's non-injective
      mapping; this is the wire's. `post:Blog/blog/hello` and `post:Blog/blog/Hello` are two posts
      — the furls differ, so `duplicate-url` never fires and `ShAddressResolver` resolves each to
      exactly one row — and `filePathFor` sends them to paths that differ only in case. Windows and
      macOS hold **one file** for the pair, so the pull wrote both, the disk kept the second, and a
      post vanished from the projection with the run reporting success. Holding both rows needs a
      case-sensitive collation, which H2's default comparison and PostgreSQL both are: reachable on
      this project's own dev configuration, from the platform it is developed on. **The loss does
      not stop at an invisible post** — the base records both, the tree holds one, and the next
      `push --prune` reads the difference as a local deletion and removes the survivor's twin from
      the server. Two existing mechanisms, composed rather than a third invented: the **asset
      conflict** is the precedent for the outcome (there is no merging two posts into one path, so
      write **neither** and name both), and **`partial`** is the precedent for the consequence — it
      exists so `push --prune` refuses to delete what was never projected, which is exactly the
      state a refused write leaves. Two things worth keeping straight:
      - **It is refused on Linux too**, where the pair coexists happily. The projection is
        committed and shared (P4), so a tree that only exists on one kind of filesystem is not one
        another machine can pull — and a repository holding `hello.md` beside `Hello.md` already
        breaks a checkout on the platform this is developed on. One tree, the same everywhere (P5).
      - **Both directions, because the tree is authored too.** The pull refuses to *write* the
        pair; `readLocal` refuses to *read* one, and `verify` reports it as `path-collision`, for a
        tree hand-authored on Linux where every other gate passes. Distinct from SH400's
        `duplicate-address`: that is two files reaching one address, this is two addresses reaching
        one file, and the fix differs — rename a post rather than delete a file.
    - **A post URL is a path too, and only the asset one was guarded (SH402).** SH256 refuses an
      asset path holding `.` or `..` because "under `shio/assets` it can resolve outside the
      project entirely". A post URL builds a path the same way and had no such check:
      `filePathFor({site: "Blog", url: "/../outside"})` is `Blog/../outside.md`, `path.join`
      normalizes it, and the pull writes to `shio/outside.md` — more segments leave the repository.
      The value is the **server's furl**, so this is remote data choosing where a local command
      writes, and it needs no malice to reach: an import, a `post.move` an agent composed, or a
      site whose furls were never constrained. Three things worth keeping:
      - **`filePathFor` is the choke point, and the read side needs nothing.** A hand-authored file
        cannot carry it — the path implies a URL and `parsePost` refuses a `url:` that disagrees
        with its path, and a walk never descends into a `..` entry. The one direction that reaches
        it is server → pull, and every write goes through that one function.
      - **Why it survived: a path is built from two values and only one was asked.** `siteDir` has
        refused a traversing `site` since SH257, and SH255's table declares that refusal — so the
        pair was half-covered, and the comment above the asset entries read "the *only* round trip
        whose output is used as a filesystem path". That premise is what made the gap invisible;
        it is corrected in place rather than deleted, because the sentence is the finding.
      - **It skips rather than throws**, into SH173's `skipped` channel: the projection is short by
        exactly that post and the rest of the pull is fine, which is what SH212 and SH374 both
        settled on. And `skipped` now marks the snapshot **partial** — SH173's rows too, not just
        SH402's. A post missing because it could not be *read* looks exactly like one a curator
        deleted, and `push --prune` acted on the difference; it had been reported to the screen
        since SH173 and never to the flag that stops the deletion.
    - **And the class, which SH402 did not close (SH403).** Two more producers took server data and
      had never been asked the same question: `folderMarkerPathFor` interpolates the sitemap's
      `node.path` after a `normalizeUrl` that keeps `..`, and `notesPathFor` interpolated the site
      name raw instead of through `siteDir` — and on a first pull into an empty tree the site list
      falls back to the **server's** names. SH402 fixed the value its investigation started from
      and read as complete; SH322 and SH338 both say the instrument lands first, and it did not.
      - **[path-safety.test.mjs](../../cli/test/path-safety.test.mjs) is the deliverable**, and it
        landed before either fix. It *discovers* every exported `*PathFor`/`*Dir` plus a declared
        `EXTRA`, and asserts the set equals a declared inventory — the
        `ShSilentCatchInventoryTest` shape. A new builder fails the build until somebody classifies
        it. A hand-written list of the three builders known that day would have passed forever.
      - **The measurement corrected the estimate twice, which is why it ran first.** The task
        predicted three producers with one guarded; there are **six**, and **four** were already
        right. `assetPathFor` guards by answering `null` rather than throwing (SH256's deliberate
        choice), so the lint accepts either — one that demanded a throw would have reported the
        builder that was already correct. `sidecarsOf` is safe for a reason worth recording: its
        names derive from the post stem and a field name, never from a stored value.
      - **A skip, not a throw, and one notice.** The folder filter mirrors SH402's post filter into
        the same `skipped` channel. That moved the notice: it used to print at the read, which ran
        before the folder rows existed, so it reported the posts and hid the folders. Notes need no
        filter — a post whose `site` navigates is already removed by SH402's filter, which is what
        the fallback list is built from — and saying so is why there is no third loop here.
    - **`partial` was a latch, and the push's advice could not clear it (SH404).** `push --content
      --prune` refuses on a partial base and says "take a complete projection first
      (`shio pull --content`)". Every pull wrote `partial: base.partial || …`, so once set only
      deleting `shio/.shio/content-base.json` cleared it — and nothing said so. The remedy the
      error named did not resolve the condition the error refused, which is a **P6** defect before
      it is a correctness one: an error naming a remedy the product does not implement is worse
      than one naming none, because it spends the reader's time proving it. What was left was
      `--force`, meaning "delete everything this tree does not declare" — the opposite of what the
      message steers toward. SH402 is what made it urgent rather than theoretical: until then only
      a *truncated* read set the flag, which needs a site bigger than `max-limit`; now one
      unprojectable row does, so an ordinary instance reaches the latch and stays there.
      - **Inherit when scoped, recompute when not.** A `--site` run proves nothing about the sites
        it did not read and must inherit. An unscoped run already covers every site the tree holds
        (SH173) — which is the scope `--prune` acts on, since the push scopes its own read the same
        way — so its own facts are the whole answer.
      - **The push still inherits, deliberately.** A pull may clear the flag because it rebuilds
        the projection and knows what it covered; a push only writes what the tree already
        declared, so it has no standing to claim the snapshot now describes the whole server. The
        asymmetry is recorded at the call site so it does not read as an oversight.
    - **The asset inventory's truncation was computed and discarded (SH405).** The post list treats
      a truncated read as fatal; the asset inventory derives from the same sitemap walk, gets the
      same `truncated` back from `readRemoteFolders`, returns it — and no command read it. Nothing
      was lost by that: `pullAssets` iterates only what it found, so a short inventory means assets
      simply were not downloaded, and the *dangerous* half was already covered because the pull's
      own folder read sees the same truncation and marks the snapshot partial. What was missing was
      the **cause**. The run said "N File reference(s) name no asset this run could see" — true,
      with "could see" doing quiet work — while `shio verify` offered `asset-missing` with the fix
      "Fetch it with `shio pull --content`", which the operator had just run. Same P6 shape as
      SH404: a symptom reported, a cause known and withheld, a remedy that cannot resolve it. It is
      reported before the unresolved-reference line, because when both fire this one is their
      cause, and it names what to raise (`shio.agent.read.max-limit`) rather than only what broke.
    - **A first pull fetched no assets at all (SH406).** `readRemote` scopes to the sites the tree
      holds and falls back to the whole instance when there are none — `sites.length ? sites :
      [null]`, and its comment says why: a first pull has nothing to scope to, and that is the one
      case where reading everything is the intent. `pullAssets` wrote the same shape and meant
      nothing by it: `sites.length ? sites : []`, **whose branches are the same value**. So a first
      pull walked no sites for assets, downloaded no bytes, and left every `File` field holding the
      raw id it arrived as — from a run that reported success (P7).
      - **Why it stayed invisible.** A second pull repairs it, because by then the tree names a
        site; the workaround was "pull twice" and nobody had to know that was what they were doing.
        `verify` does not catch it either, defensibly: an unresolved reference stays an id, and
        SH145's rule is that a non-path-shaped value was never projected, so `asset-missing` skips
        it. Every gate agreed the tree was describing ids.
      - **One binding, not three.** The fallback was written out three times — the folder read, the
        note list, and the asset read — and the asset copy was the one that drifted. They now share
        `siteScope`, because three copies of a fallback is how one of them gets lost.
      - **Found while testing SH405.** The truncation report could not fire in an empty-tree
        fixture, and the reason it could not was this. Worth recording as a habit: a test that
        cannot reach the condition it was written for is evidence about the code, not the test.
    - **The same collision, one root over (SH407).** SH401 refused two posts whose paths differ only
      in case and fixed the **post** root; folder markers are the same kind of path from the same
      server data. `folder:Blog/news` and `folder:Blog/News` give `Blog/news/_folder.md` and
      `Blog/News/_folder.md` — one file on Windows and macOS. **Third time this shape has
      appeared** (SH402→SH403, SH401→SH407), and the tell is that SH403's lint would not have
      caught it: it asks every producer whether it refuses a value that *navigates*, and nothing
      asked whether two of its outputs can *collide*.
      - **The consequence is narrower than a post's, and worth stating precisely rather than
        inflating.** A folder holding posts is implied by those posts, so `reconcileFolders` keeps
        it and `--prune` cannot touch it. What is exposed is the **empty** folder — the one a
        `_folder.md` exists to declare — whose marker was the only evidence it should survive.
        Losing it makes the folder undeclared, and SH211's prune deletes what the tree does not
        declare.
      - **One detector, two roots.** SH401's `pathCollisions` became `collisionsAmong(addresses,
        pathOf)`, and the folder caller passes `folderMarkerPathFor`. `unportableFindings` was
        already generic over `{addresses, paths}`, so `path-collision` covers both with no second
        rule — which is what "fix the class" should look like when it works.
      - **`verify` had never opened `shio/folders/` at all.** The tree-only gate read posts,
        post-types and assets; the folder root was outside every one of them. It reads markers now,
        in `mode: "report"`, the same load-versus-report split SH374 gave sidecars.
    - **The second question, asked of every root (SH408).** SH403's lint asks whether a producer
      refuses a value that *navigates*; SH407 was a defect it could not catch, and was then fixed
      by hand — leaving the instrument as unable to find the next one as before. Third repetition,
      so the question landed first this time, and it named three roots nobody had checked:
      `assetPathFor` folds `Logo.png` with `logo.png`, `fileNameFor` folds `Article.json` with
      `article.json`, and `siteDir` folds `Blog/` with `blog/` — one content directory, one folder
      directory and one memory file. **Post-types are the severe one**: `readLocalDefs` refuses a
      name defined *twice*, but two names differing only in case are two names, so the disk keeps
      one, `diffDefs` reports the other as removed, and `push --prune` deletes the post type every
      post of that type is validated against.
      - **The lint found a fourth traversal gap on its own.** SH403 enumerated three modules and
        `post-types-io.mjs` was not among them, so `fileNameFor` had never been classified for
        *either* question — `../evil` gave `../evil.json`, outside `shio/post-types/`. It is
        SH402's defect a fourth time, found by widening a list rather than by reading code.
      - **It also refused to take my word for something.** The declaration for `fileNameFor` first
        claimed the server rejected such names; the probe disagreed within seconds. A declared
        exemption that is not executed is a comment, and this is why each entry carries a thunk.
      - **Two drafts of the assertion were wrong, and both are recorded in the file.** The first
        demanded the *producer* not fold, which would have condemned posts and folder markers,
        where the design deliberately puts detection in the caller. The second ran a detector the
        test built itself, which passes whatever production does — vacuous. What it asserts now is
        that each root names a detector exported from `src/`, that the detector reports the pair,
        **and** that some module other than the one defining it actually calls it. That last check
        is what caught the real state of things: five detectors, none of them wired.
    - **And the fourth draft of that assertion, because the third was a proxy too (SH409).** SH408
      wired `defFileCollisions` into `content-pull.mjs`, which reads post-type defs to map `File`
      fields and **writes none**. The command that writes them is `pull.mjs`, whose loop is
      `writeFile(join(target, fileNameFor(def)), …)` — and it still folded `Article.json` and
      `article.json` in silence. **The lint was green**, because "wired into a command" greps for
      the detector's name in any module but the defining one: that proves *a* caller exists, not
      that the caller is the one at risk.
      - **Each entry now names the module whose write it guards** (`writtenBy`), and only that file
        is searched. The corrected lint named exactly one root and nothing else, which is what a
        good instrument looks like when it fires.
      - **Four drafts, each measuring something adjacent.** Producer-must-not-fold described
        neither the design nor the defect; a self-built detector passes whatever production does; a
        name-anywhere grep passes a check wired to the wrong command. Only the fourth states the
        claim that matters — *this root's writer consults this detector*. Worth keeping as the
        cautionary case: an instrument can be wrong three times and green every time.
      - **The check moved to the write and left nothing behind.** A warning in a command that
        cannot cause the condition is noise that reads as coverage, so `content-pull.mjs` carries
        only a comment saying where the check went and why.
    - **And the bytes are in the SH89 merge now (SH373).** SH210 fixed one direction and opened
      another: a server-side asset edit never reached the tree, and — newer and worse — a push
      *replaced* the server's bytes whenever they differed from the tree's, which reverts a
      curator's edit. Asset bytes were the last field type outside the rule that neither side
      silently overwrites the other. The base snapshot gained `assets: {path: sha256}`, and
      `reconcileBytes` gives the bytes the same outcomes every other field has. Four rules:
      - **A conflict is left on disk.** There is no merging two images, so the honest outcome is to
        write nothing and name the asset. `pull` says so and `push` refuses.
      - **A push and a pull are not symmetrical about missing evidence**, deliberately. With no
        agreed hash the *tree* is authoritative: it is the artifact under version control and the
        server's copy is reproducible from it, so a pull that guesses can destroy the only copy of
        something and a push that guesses cannot. Refusing on a push would also be a false positive
        on the one push that most needs to work — the first, before any pull has written a base.
        Both size branches follow that rule, which is the wart the first cut had: without a base a
        differing *length* replaced while differing *bytes of the same length* refused.
      - **Cost is measured, and one measurement changed the code.** A first pull is unchanged: bytes
        it just downloaded are hashed in this process, not sent back to be hashed — the benchmark
        caught that doubling a first pull's requests (407 against 207) the moment it ran. What
        remains is **one digest per asset already on disk**, which nothing but the server's own hash
        can answer, and guessing from `size` is the silent no-op SH210 removed on the push side.
        **SH398 removed it**: the digest is stamped into the `File` post's `data` at upload and
        replace (SH62's treatment of `WIDTH`/`HEIGHT`, for a bigger saving) and rides the listing,
        so a settled pull went from **208 requests to 8** over a 200-file library. Three rules:
        the endpoint stays, because a stamp covers only rows written after it and every asset
        already in an instance still uses it (measured: an unstamped library is 208 again, and the
        benchmark asserts both branches); `replace` **re-stamps**, since a digest outliving its
        bytes restores the silent no-op SH210 removed, by its own cache; and the existence check
        stays in front of the stamp, because a stamp cannot know somebody removed the bytes
        underneath the CMS and reporting it there would turn SH62's `size: -1` into a confident lie.
        The digest is read back from the **file just written**, never from a copy in memory: SH62
        buffered *images only* because buffering an upload allocates the size of the upload, and a
        digest that needed every byte must not be what widens that to a video. **And the size is stamped
        beside it as the cache's validator (SH399)** — not as a saving, which SH398 weighed and
        rejected, but because the digest's only invalidation was a `replace`: bytes changed under
        the CMS (a restored backup, a synced volume) left every reader confidently holding a hash
        that was not theirs. The listing already knows the real size, so a stamped size that
        disagrees is proof the stamp is stale, and the row reports **no** digest rather than the
        wrong one — a caller that gets none falls back and is right. Three rules: it **detects, never
        repairs**, since re-stamping on a read would make a `GET` write; the recompute is correct
        every time, so only the *cost* persists and only for a file somebody edited behind the
        CMS's back; and it catches a **length change and nothing else** — a length-preserving
        external edit is invisible, nothing short of hashing on every read would see it, and that
        limit is asserted in a test rather than left to be discovered.
      - **An agreement is recorded even when nothing moved**, or a tree that only ever *reads* its
        assets stays at "never agreed" for ever and the first server-side edit arrives as a conflict
        instead of a change.
  - **A push reads the folders it references, not the library (SH213).** Measured first, because a
    cost that is argued rather than measured fails in somebody else's environment:
    `cli/test/asset-inventory-cost.test.mjs` prints request, inventory-read and **row** counts over
    a 200-file library in 20 folders on every run, and asserts the ceilings. It was **200 rows to
    resolve one reference** and **400 when the upload's folder was missing** — the whole library,
    twice, because the second look-up wanted one new folder id and re-read the inventory to find
    it. Now: **10 rows** for one reference, **0** for the upload case (the referenced folder does
    not exist yet, so there is nothing to list), and the second look-up asks
    `readRemoteFolders` — the sitemap answers it alone. Two rules the numbers argued for:
    - **It is a threshold, not a replacement**, and since SH375 the number is measured rather than
      argued. Up to **8** referenced folders it lists per folder; past that one whole-site listing,
      and `--max-folder-reads` moves it. The CLI benchmark asserts both branches (10 folders → 1
      read of 200 rows; 3 folders → 3 reads of 30).
    - **The `folderId` filter had to move into the query first (SH375).** SH213 assumed the server's
      filter narrowed the server's work. It did not: `listFiles` loaded every published `File` post
      in the tenant and applied `folderId` **in memory**, stat-ing each file on the way through — so
      a one-folder listing read the whole library (measured: **69 rows against the whole site's
      69**) and F listings cost F whole-library reads. The narrowing was buying payload and paying
      in server work, which is the direction "measure before you fix" exists to catch.
      `ShStaticFileListingBenchmarkTest` now pins it: one folder reads **14**, and covering the
      library one folder at a time costs **84 against 69** — the fixed tail, F times. Two rules kept
      in `rowsFor`: the folder is passed as an **entity**, never as an id in JPQL, because
      `ShFolder` is an `ShObject` subclass and navigating one trips the SH22 tenant-join hazard; and
      an **unknown folder reads nothing**, because a filter that widened to the whole tenant would
      answer a caller asking about one folder with a whole site.
    - **What bounds the threshold is round trips, not server work** — that is what the measurement
      settled. A round trip is a client-side cost no benchmark here can measure, so eight survives
      as a *latency* budget, which is a claim about the deployment and therefore the caller's to
      change.
    - **A pull is deliberately still the whole library.** It rewrites every `File` field id → path,
      so it needs every id the site has; narrowing it would leave UUIDs in the tree, which is the
      defect SH145 exists to remove. The benchmark asserts that too, so the two directions cannot
      drift into each other.
  - Deleting an asset is **not** projected: bytes go when their post goes (SH38's cleanup
    listener), and a File post can be referenced by content this tree does not own.


## `shio/releases/<site>/<name>.yaml` (SH993)

A release projects to a file, so a launch is reviewable in a pull request rather than only in an
instance. `shio pull --content` writes one per release of the sites it covers, after the content —
a release file names addresses, and the pages should be on disk by the time somebody reads it — and
removes the file of a release the instance no longer lists, because archiving is how a launch stops
being something the next reader scrolls past.

**Two of the five fields are read-only, and that is the safety decision in this projection.** `state`
and `scheduledAt` are written so a reviewer can see what a release is doing, and ignored on push: a
file that could schedule or publish a launch would make `shio push --content` — the command a CI job
runs on every merge — a way to put forty pages live by editing a line. Publishing stays `release.run`
with its confirm token (P7). What the file declares is the **membership and the description**, which
is the half a person actually reviews, and a push converges those with `release.upsert`,
`release.add` and `release.remove` in one batch.

A release that already ran is left alone with its state named in the report: converging a closed one
would be refused op by op, halfway through a launch somebody already shipped. Releases travel
**outside** the desired-state document, like the SH161 site memory and for the same reason — a release
is not content, it *groups* content, so folding it in would make it the thing `--prune` deletes when a
tree stops naming it. The format is hand-written and byte-stable (P5): a re-pull of unchanged content
is an empty diff, and the parser refuses a line it cannot read rather than producing a launch that is
missing pages.

## `shio/gates/<site>/<name>.yaml`, and `shio gate run` (SH997)

A gate projects to a file like a release does, so what a site checks before publishing is reviewed in
a pull request rather than configured where nobody reads it. `shio pull --content` writes one per
gate, `shio push --content` declares each with its own idempotent PUT — outside the desired-state
document, like the SH161 notes, because a gate is not content and `--prune` must not delete it for
going unmentioned.

**The file is the whole checklist; the instance keeps the half it can enforce.** `audit` drives a
browser per viewport and the instance deliberately runs none, so the push leaves that step behind and
says so, and `shio gate run` runs it here where the browser already is. The split is written into the
file's own header, because a curator should not have to learn from a refusal which of their checks the
server keeps.

`shio gate run --site <name> [address...]` checks the addresses named, or every draft the site holds,
and **exits 1 when a blocking check failed** — so CI stops before the publish does. The verdict comes
from `GET /agent/gates/check`, not from judging the content here: a pipeline with a second opinion
about its own site is how a gate passes in CI and refuses in production. `shio gate list` prints what
a site declares.

## `shio/content/<site>/.notes.yaml` — Grep is the work list (SH1003)

Open notes project beside `.memory.yaml`, one file per site at the site's own root, with a leading
dot so they stay out of the `**/*.md` glob that finds posts. P4 says files beat APIs for authoring,
and the cheapest tool an agent has is a search over the tree it already pulled: a correction that
can only be reached by an HTTP call is one a session has to know to ask for.

**Projection only** — never read back, never pushed, never part of the base, the same decision SH231
made about the exporting instance's house rules. The note is the instance's record of what somebody
asked for, and a tree that could edit it would be a second writer of the same row. Close one with
`note.resolve` or from the console.

A site with nothing open **loses its file** rather than keeping an empty one: the absence is the
honest empty state, and a stale file listing a request somebody closed reads exactly like a clean
site. Rows are sorted by address, so a re-pull of an unchanged site is an empty diff (P5).

## What merges, and who wins (SH1004)

`content-base.json` is the common ancestor of the three-way merge, and a field the **projector**
writes but the **merger** does not know is either clobbered on the next pull or in conflict on every
pull — neither of which announces itself. The rule is therefore stated per artefact, here, and
`cli/test/merge-rules.test.mjs` fails the build when a writer arrives without one. The merger cannot
be asked what it has never heard of: a field it does not handle produces no branch, no error and no
log line, so the declaration **is** the instrument (SH338).

| Writer | In the base | Who wins | How a conflict is reported |
|---|---|---|---|
| `writeDoc` | per field | neither — the file is left alone | `conflicts` on the pull report, per field, by address (SH148) |
| `writeRemoteSidecar` | per field | neither — a sidecar **is** a field (SH191) | the same per-field conflict its post's other fields raise |
| `writeAssetBytes` | whole | neither — half an image is not a merge | `conflicts` on the pull report, by asset path |
| `writeFolderMarker` | no | the server: that a folder exists is the server's fact | none is possible — the file carries no value a person authors |
| `writeLocalNotes` | per key | neither — neither copy is written | `notes.conflicts`, as `<site>:<key>` (SH161) |
| `writeLocalInstanceNotes` | no | the server; the tree pushes back only with `--adopt-memory` (SH231) | none is possible: projected, never read back |
| `writeOpenNotes` | no | the server, always (SH1003) | none is possible: never parsed, never pushed |
| `writeBase` | no | the pull that wrote it — this **is** the base | none: it records the agreement rather than joining one |

**Notes were the sharpest case**, and the reason this table exists rather than a convention. They
are the one projected artefact a person edits in the console *while* the tree holds a copy, and
"resolve it in the console with an edited body locally" has no natural answer. SH1003 chose one —
the file is a projection and is never read back — and this is where that choice is recorded, so a
later reader does not have to re-derive it from the absence of a parser.

**An artefact outside the base has to name an authoritative side.** That is asserted, not just
written: "outside the merge" with nobody winning is how a local edit disappears on the next pull
with no report, which is the failure this whole table is about.
