# Content as files — shio/content/**

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### Content as files — `shio/content/**` (Block I)
Content projects to **one Markdown file per post**, `shio/content/<site>/<url>.md`: YAML
frontmatter for the metadata + `fields:`, and the post's first `HTML Editor` field as the
document body (the file records which field that was as `body:`, so reading it back never
needs the model). Owned by `cli/src/content-files.mjs` (the format, pure),
`content-sync.mjs` (the three-way merge, pure) and `content-store.mjs` (disk + wire).
- **The projection's field set is a table with a test (SH348).** Nine keys —
  `bodyField, data, folder, movedFrom, publish, sidecarFiles, site, type, url` — and everything a post
  carries that is *dropped* is declared with a reason in
  `cli/test/content-projection-coverage.test.mjs`. Two of those are gaps rather than decisions:
  **`locale` and `translationGroupId`**. Measured consequence — a `pt-br` page pulled to files and
  pushed back comes out as `en`, because `/agent/read` does not return a locale (zero occurrences in
  `ShAgentReadService`), so the tree could not carry it even if the format wanted to. That is why
  SH348's first step is the *read*, not the op. The table exists because SH255's round-trip guard tests
  values of fields the format **has** and cannot see a field it **lacks**.
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

