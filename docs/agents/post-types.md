# Post types — one name-keyed surface

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

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

**A name in `ShSystemPostType` is a type this system creates (SH293).** The class held thirteen
names transcribed from the 2018 model in `import/post-types.json`; eight of them — `Photo`, `Video`,
`Quote`, `Link`, `TextArea`, `Article`, `FolderIndex`, `Alias` — appeared **nowhere but in that
file**. Nothing created them, nothing looked them up, and one had been wrong since it was written:
the 2018 model declares `Links` and the constant said `Link`, which no reader could catch because a
constant nobody reads is a string nobody compares. They are deleted. What remains is five, each with
a creator named in its javadoc: `PageLayout`/`Region`/`Theme` (`ShRenderPostTypes.ensure`, SH183),
`File` (`ShStaticFileService.resolveOrCreateFilePostType`, SH38), and `Text` — **looked up, never
created**, which is declared in `ShSystemPostTypeInventoryTest.LOOKED_UP_ONLY` rather than left
looking like the others. That test is the guard: a constant with no creator fails the build, and a
declared exception that gains one fails too. Note what the class does *not* claim — it is not an
inventory of an instance's post-types, only of the ones this code provisions; everything else is a
user's or an import's, named by data.

**And `File` is now compared with the 2018 model, like the render three** (`ShFilePostTypeSourceTest`)
— name, plural, title, description, system flag, and the `FILE` field's label, ordinal, `required`,
`isTitle`, `isSummary` and widget. Same shape as SH290's comparison and a different blast radius: a
drift here breaks **uploads** rather than rendering, and would surface as a 404 on bytes that exist.
One mapping is stated rather than silent — the export's `label` is this model's `title`.

**`File` declares five fields, and for years it declared one (SH474).** Every upload stamps
`SHA256` (SH398), `SIZE` (SH399) and — since SH471 — `WIDTH`/`HEIGHT`, while the type mentioned only
`FILE`. SH168's `unknown-field` is an **error**, so a replica went red on assets this system had
itself uploaded: 66 findings, all of them true about the *model* and read as broken content. They
were invisible until SH470 removed the eighty-four false `dangling-reference` errors sitting on top
— **a gate's first run after a fix is worth reading in full rather than diffing against the count.**
The fix is to declare them, not to stop writing them: every one is read back. All four are `Hidden`,
which is the honest widget for a value stamped from the bytes.
**The repair is in `resolveOrCreateFilePostType` because there is nowhere else it could be.** `PUT
/api/v2/post-type/File` answers 409 (`system`), `mergeMissingFields` refuses a system type for the
same reason, and `render.provision` repairs only `ShRenderPostTypes.NAMES` — which `File` must stay
out of, since that list is also every "is this a template type" test in the renderer. So the
existing-type branch became additive, SH289's rule at the only door: a table this class owns, of
fields this class writes unconditionally, added and nothing else. The limit is real and stated — the
next upload is the trigger, so an instance nobody uploads to stays as it was. `ALT`, `CAPTION` and
`TAGS` are still undeclared, held as an asserted list in `ShFilePostTypeSourceTest` with the reason
(`TAGS` is an array and needs a widget decision, not a transcription) — SH478.
**And the error that reported all 66 named a door that answers 409.** `unknown-field`'s fix said
`PUT /api/v2/post-type/<name>`, which is right for a user's type and refused for a system one, so
the instruction was wrong for exactly the case that produced the findings (P6). It now branches on
the `system` flag and says what actually works.

**A bare instance ships six types, and the SH95 vocabulary is not among them (SH607).** `File`,
`PageLayout`, `Region`, `Theme`, `Redirect` and `SiteScripts` are created on demand; everything
else is a user's, an import's or a blueprint's. `Page` and the seven `Section*` are a blueprint's —
which is why `shio convert`, whose posts are `Page` and `Section*`, answered 400 on a Shio nobody
had used, on *every* mode including fidelity, with every suite green because a development instance
had applied a blueprint months earlier. **`render.provision` now takes `data.types`**, a list of
names resolved against `classpath:/post-types/vocabulary/*.json` — emitted from
`shio-sections/src/post-types.ts` by `pnpm -C shio-sections run emit` alongside the two blueprint
copies, with `emit.test.mjs` failing the build when the three disagree. Names and never
definitions: a caller that could send a def would have a second way to redefine `Page` underneath
somebody's site, so an unknown name is a 400 listing the catalogue rather than a type it invents.
The desired-state document's `postTypes` compiles to that op **first**, the order
`ShBlueprintWriteService` already uses against this same failure, and `Declared.withPostTypesFrom`
reads it off the run's own ops so a **dry run** into a bare instance resolves — SH153's problem
from the conversion side. Provision-never-upsert throughout (SH152), so a curator who added a field
to their `SectionHero` keeps it and a re-conversion changes no model.
**And the `Page` it installs is wider than any that shipped.** Three definitions existed and none
held the four keys `buildConversion` writes: the SH71 starter has `title`/`content` and no
`sections`, the blueprints had `title`/`description`/`sections` and no `content`. `content` is an
`Ace Editor - HTML` — it holds a whole captured page body, so it is code a curator inspects and
SH191 projects it to an `.html` sidecar rather than inlining a megabyte into the content file.

**Creating a site is `site.upsert` on the batch surface (SH170)** — addressed `site:<name>`,
idempotent, and it creates a **blank** site (`ShSiteWriteService.createBlank`), not a clone of the
SH71 bootstrap template. A token with an SH2 site allow-list is **refused** — inventing a site
outside the list is the escalation §XII forbids. `POST /api/v2/site` still exists for the console
(session + CSRF, and it keeps the starter clone).
**And the general form of that rule, which outlives this endpoint (SH184, moved here by SH356):**
the P7 question is not *"may an upsert write this field"* but *"was there anything there"*. Both of
SH184's rules were reached by rejecting a simpler version — SH170 refused to update a site at all,
which was right about a **decision** and wrong about a **blank**, since the agent is the thing that
just deployed the front end and knows the address.

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

