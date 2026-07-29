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

