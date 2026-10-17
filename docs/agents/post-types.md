# Post types: one name-keyed surface

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### Post Types: one name-keyed surface (SH16 + SH117)
**There is one post-type API and it is keyed by name.** The CLI, the agent path and the
React console all use it. (SH117 retired the id-keyed, entity-shaped twin that used to
live at `/api/v2/post/type`, note the slash. If you find a reference to that path, it is
stale; there is no console post-type controller.)
- `GET /api/v2/post-type`: list as **lossless** `ShPostTypeDef`s (keeps `Tab`, verbatim
  `widgetSettings`, nested Relator fields, the authoring inverse of the lossy SH11 CDA schema)
- `GET /api/v2/post-type/{name}`: one def (404 unknown)
- `POST /api/v2/post-type`: **create**, 409 on an existing name (name comes from the body).
  This is what the console's "new post-type" form uses: a collision there is a mistake to
  report, not a model to overwrite.
- `PUT /api/v2/post-type/{name}`: **upsert**, idempotent by name (what `shio push` converges
  with); rejects system types (409), unknown widgets (400)
- `DELETE /api/v2/post-type/{name}`: delete a non-system type (204/404/409). **A type content
  still points at is a teaching 409 naming the count (SH236)**, it used to be a *500*, because
  the check was the `system` flag alone and `sh_post_unified.post_type_id` is `onDelete NO ACTION`,
  so the database refused with a constraint violation the caller reads as a server fault. The
  count is what makes it actionable (rename vs retire), and **trashed posts are counted too and
  named separately**: a trashed row still holds the reference, so a refusal saying "0 posts"
  followed by a 500 would be the worst of both.

**A name in `ShSystemPostType` is a type this system creates (SH293).** The class held thirteen
names transcribed from the 2018 model in `import/post-types.json`; eight of them, `Photo`, `Video`,
`Quote`, `Link`, `TextArea`, `Article`, `FolderIndex`, `Alias`, appeared **nowhere but in that
file**. Nothing created them, nothing looked them up, and one had been wrong since it was written:
the 2018 model declares `Links` and the constant said `Link`, which no reader could catch because a
constant nobody reads is a string nobody compares. They are deleted. What remains is five, each with
a creator named in its javadoc: `PageLayout`/`Region`/`Theme` (`ShRenderPostTypes.ensure`, SH183),
`File` (`ShStaticFileService.resolveOrCreateFilePostType`, SH38), and `Text`, **looked up, never
created**, which is declared in `ShSystemPostTypeInventoryTest.LOOKED_UP_ONLY` rather than left
looking like the others. That test is the guard: a constant with no creator fails the build, and a
declared exception that gains one fails too. Note what the class does *not* claim, it is not an
inventory of an instance's post-types, only of the ones this code provisions; everything else is a
user's or an import's, named by data.

**And `File` is now compared with the 2018 model, like the render three** (`ShFilePostTypeSourceTest`),
name, plural, title, description, system flag, and the `FILE` field's label, ordinal, `required`,
`isTitle`, `isSummary` and widget. Same shape as SH290's comparison and a different blast radius: a
drift here breaks **uploads** rather than rendering, and would surface as a 404 on bytes that exist.
One mapping is stated rather than silent, the export's `label` is this model's `title`.

**`File` declares five fields, and for years it declared one (SH474).** Every upload stamps
`SHA256` (SH398), `SIZE` (SH399) and (since SH471) `WIDTH`/`HEIGHT`, while the type mentioned only
`FILE`. SH168's `unknown-field` is an **error**, so a replica went red on assets this system had
itself uploaded: 66 findings, all of them true about the *model* and read as broken content. They
were invisible until SH470 removed the eighty-four false `dangling-reference` errors sitting on top,
**a gate's first run after a fix is worth reading in full rather than diffing against the count.**
The fix is to declare them, not to stop writing them: every one is read back. All four are `Hidden`,
which is the honest widget for a value stamped from the bytes.
**The repair is in `resolveOrCreateFilePostType` because there is nowhere else it could be.** `PUT
/api/v2/post-type/File` answers 409 (`system`), `mergeMissingFields` refuses a system type for the
same reason, and `render.provision` repairs only `ShRenderPostTypes.NAMES`, which `File` must stay
out of, since that list is also every "is this a template type" test in the renderer. So the
existing-type branch became additive, SH289's rule at the only door: a table this class owns, of
fields this class writes unconditionally, added and nothing else. The limit is real and stated, the
next upload is the trigger, so an instance nobody uploads to stays as it was.

**It declares eight now, and the three that waited were waiting on a premise that was false
(SH478).** `ALT`, `CAPTION` and `TAGS` are SH62's Media Library metadata, written by `updateMeta`
rather than by an upload, and SH474 held them for a modelling question: `TAGS` is an array, and
declaring it as `Multi Select` was thought to require `widgetSettings` naming the options a curator
may pick, which a tag vocabulary does not have in advance. **`multi-select.widget.tsx` reads no
`widgetSettings` at all.** It is a chip input: type a value, press Enter. The two option-driven
widgets are `Combo Box` and `Check Box`, and the belief was about them, carried across by
assumption. So the decision the three were queued behind never existed, and each cost one line:
`ALT` → `Text`, `CAPTION` → `Text Area`, `TAGS` → `Multi Select`, which are the controls the Media
Library dialog *already* renders for the same three values, a declaration disagreeing with that
dialog would put one value behind two controls.

**The lesson is about the shape of the record, not about the widget.** A hand-kept
`STILL_UNDECLARED` map recorded a belief and could not check one, so the belief was wrong for a
block while `unknown-field` (an error) fired on every File post a curator had captioned. It is
gone: `ShFilePostTypeSourceTest` now **reflects** `ShStaticFileService`'s `DATA_*` constants and
requires the type to declare all of them, so the next key added fails on the commit that adds it
rather than on somebody's replica (SH322). The half that lives on the console side,
that `Multi Select` is open and exactly two widgets are closed, is
`shio-react/test/widget-option-source.test.mjs`, which fails first if a widget ever changes contract.
**And the error that reported all 66 named a door that answers 409.** `unknown-field`'s fix said
`PUT /api/v2/post-type/<name>`, which is right for a user's type and refused for a system one, so
the instruction was wrong for exactly the case that produced the findings (P6). It now branches on
the `system` flag and says what actually works.

**A bare instance ships six types, and the SH95 vocabulary is not among them (SH607).** `File`,
`PageLayout`, `Region`, `Theme`, `Redirect` and `SiteScripts` are created on demand; everything
else is a user's, an import's or a blueprint's. `Page` and the seven `Section*` are a blueprint's,
which is why `shio convert`, whose posts are `Page` and `Section*`, answered 400 on a Shio nobody
had used, on *every* mode including fidelity, with every suite green because a development instance
had applied a blueprint months earlier. **`render.provision` now takes `data.types`**, a list of
names resolved against `classpath:/post-types/vocabulary/*.json`, emitted from
`shio-sections/src/post-types.ts` by `pnpm -C shio-sections run emit` alongside the two blueprint
copies, with `emit.test.mjs` failing the build when the three disagree. Names and never
definitions: a caller that could send a def would have a second way to redefine `Page` underneath
somebody's site, so an unknown name is a 400 listing the catalogue rather than a type it invents.
The desired-state document's `postTypes` compiles to that op **first**, the order
`ShBlueprintWriteService` already uses against this same failure, and `Declared.withPostTypesFrom`
reads it off the run's own ops so a **dry run** into a bare instance resolves, SH153's problem
from the conversion side. Provision-never-upsert throughout (SH152), so a curator who added a field
to their `SectionHero` keeps it and a re-conversion changes no model.
**And the `Page` it installs is wider than any that shipped.** Three definitions existed and none
held the four keys `buildConversion` writes: the SH71 starter has `title`/`content` and no
`sections`, the blueprints had `title`/`description`/`sections` and no `content`. `content` is an
`Ace Editor - HTML`: it holds a whole captured page body, so it is code a curator inspects and
SH191 projects it to an `.html` sidecar rather than inlining a megabyte into the content file.

**And a name no catalogue can ship is `data.fields` on the same op (SH871).** `{Type: [name]}`,
appended to a type that **already exists** as a plain optional `Text` through
`mergeMissingFields`: SH152's additive merge, so nothing existing is reordered, rewidgeted or
removed, and a system type is refused outright. It exists because `shio convert --annotate` names
its fields from the block hashes a capture produced (`b1a2c3dHeading`), so the run that computes
them is the only thing that could ever declare them: without it the run named forty preconditions,
`post.upsert` refused the first write, and the conversion died having uploaded 18 assets and
written no page. **Names, never definitions**, `postTypes`' rule one level in, because a payload
carrying a widget or `required` would be a second way to change the content model. `data.fields`
rather than a sixteenth op name, for `data.reconcile`'s and `data.reindex`'s reason: the op
vocabulary is paid by every caller that reads it (P3). `Declared.withPostTypesFrom` unions these
names too, so a **dry run** of the document that declares them resolves, and the names are
validated *there*, before the first op, which is what makes a bad one refuse the batch whole
instead of after the assets are up.
**Its first caller lasted one task, and that is the finding rather than an accident (SH872).**
`convert --annotate` declaring forty names per capture was solving a problem SH868 had created by
storing the annotated text on `Page` at all, the type every page shares, which went from 4 fields
to 44 on an eleven-page replica. The storage is the markup again, so a conversion declares nothing
and `data.fields` keeps the job it is actually right for: **widening a type you own without a
`PUT`**, which upserts and would revert a field somebody added. What replaces the declaration is a
rule rather than a model change, `ShAnnotatedFields`, asked by `verify` and by the SH168 check:
a key annotated by the post's own markup is declared by that markup.

**Creating a site is `site.upsert` on the batch surface (SH170)**: addressed `site:<name>`,
idempotent, and it creates a **blank** site (`ShSiteWriteService.createBlank`), not a clone of the
SH71 bootstrap template. A token with an SH2 site allow-list is **refused**, inventing a site
outside the list is the escalation §XII forbids. `POST /api/v2/site` still exists for the console
(session + CSRF, and it keeps the starter clone).
**And the general form of that rule, which outlives this endpoint (SH184, moved here by SH356):**
the P7 question is not *"may an upsert write this field"* but *"was there anything there"*. Both of
SH184's rules were reached by rejecting a simpler version, SH170 refused to update a site at all,
which was right about a **decision** and wrong about a **blank**, since the agent is the thing that
just deployed the front end and knows the address.

**It fills what is empty and never replaces what somebody chose (SH184).** SH170 refused to update
at all, reasoning that a site's URL and description are console concerns with a curator looking at
them, right about a *decision*, wrong about a *blank*, and it left an agent able to build a site,
deploy a front end and not connect the two without a curator opening the console. An empty `url`,
`description` or `furl` is now filled and reported as an `update` naming the fields; a non-empty
one is **kept**, and the note says which and points at the console, because a bare `noop` reads as
the request having been ignored rather than as a value having been protected. Three rules: the op
stays **idempotent** (a second run is `noop`, so a document is re-runnable); a **`dryRun` answers
from the same code that writes** (`ShSiteWriteService.planFill`), so a plan cannot come back
greener than the run it predicts; and the rule lives on **`ShSiteWriteService`**, not in the batch
service, what an upsert may overwrite is write semantics, and SH245 is the standing lesson about
second copies of a verb. Do **not** reach for `update` instead: that is the console's full replace
and it nulls `postTypeLayout` and `searchablePostTypes` on the way past.

**`managed` on a write is a claim about the writer, not a data field**: which is why it is a
`Boolean` and why *absent* and `false` mean different things:
- **absent** (or `true`) → a **code-owned** write. Marks the type managed, and is allowed
  against an already-managed type because the writer is the owner. The CLI's canonical
  serialization omits the key, so `shio push` and a hand-written `*.json` both land here.
- **`false`** → a **hand edit** (the console always sends this). Marks the type unmanaged, and
  is **refused with 409** (`ShManagedPostTypeException`) against a managed type rather than
  racing the repository that owns the model.

It is a UX guard, not an ACL, any caller may claim either.

**`scratch` is the other boxed flag on the same record and its omitted case means the opposite
(SH768).** A write may declare the type **disposable** (a fixture, not a convention) and
*absent means kept*: scratch is never a default, because guessing wrong lets a sweep delete a
model somebody meant. **Honoured on create only.** An update claiming it changes nothing, which
is SH591's guarantee for a scratch site and holds for the same reason: a flag a later write
could raise would make a real type sweepable after the fact. Delivered as `true` or omitted,
never `false` (P3, 20 rows saying "not debris" are 20 rows of nothing), excluded from the CLI's
canonical `normalizeDef` so it never reads as drift, surfaced as `SCRATCH` in `shio context`'s
model and in `agents.md`'s notes column, and swept by **`shio post-type rm --scratch`**, which,
like `site rm`, has no `--all` and never touches an undeclared type. It exists because two
conformance fixtures (`ShioConfRendermsgmgaup2qjcArticle`, `ShioConfRendermsgmgrcoqdq2Article`)
outlived their runs and read as conventions in every cold context pack; a teardown cannot cover
the run that dies mid-way, which is precisely the run that leaves debris.

Console-authenticated (HTTP Basic
or a session). **CSRF is required of session-bearing callers and waived for stateless ones**
(`ShStatelessRequestMatcher`, keyed on the session cookie), which is what lets the CLI and the
browser share the path safely, do not widen that back to a blanket exemption.

**Renaming is `POST /api/v2/post-type/{name}/rename` (SH163)**: body `{ "to": "NewName" }`,
and it is the only way. A `PUT` to a new name creates a *second* post-type and orphans the
first with its id, its fields and every `sh_post_unified` row pointing at it, while answering
`200`: SH134's defect one level up, with the same answer: one op that moves and reports what
moved. The content does not actually move (a post references its type by **id**), so the job is
the row plus **the one place the model stores a type by name**, a `Multi Select`'s
`multiselect.postType` (SH154), rewritten and named in `referencesUpdated`. Four refusals: an
unknown source (400 with the installed names), a blank target (400, renaming to nothing is a
delete nobody asked for), an **occupied target (409, never a merge)** and a system type (409) or
a code-managed one against a `managed:false` hand edit (409, the same claim rule `upsert`
follows). Renaming to the current name is a **noop**, so a re-run is safe. What it deliberately
does not do is touch project files: `shio/content/**` frontmatter `type:` and a desired-state
document's `type` name the post-type too, and the response's `notes` say so rather than the
server writing a file it does not own. The console's API-name field stays read-only and grows a
**Rename** button beside it.

**`shio push` reaches it (SH235).** A renamed `shio/post-types/*.json` used to converge as a
create plus (with `--prune`) a delete aimed at the type it replaced; now `detectTypeRenames`
finds it from **two signals**, SH150's shape applied to the model: an explicit `"movedFrom":
"<old name>"` (an instruction `pull` never writes, ignored once the source is gone, and the only
signal that survives a rename *plus* an edit) and an **identical body** under a new name (the
plain `git mv`). Three rules carry over: **ambiguity is refused** before anything is written and
names the candidates; a **plausible but unprovable** pair (same field names, different bodies) is
*printed* with the `movedFrom` line to add, because silence is how a delete-plus-create destroys
content quietly; and a rename's source leaves `removed`, or `--prune` deletes the type the rename
just preserved. Renames are applied **before** upserts (an upsert on the new name first would
create the second type this exists to prevent), a rename-only change needs **no** upsert at all
(so `--check` is quiet on the next run), and `movedFrom` is stripped from the body, it is an
instruction to the CLI, not a field of the model. The content-tree half is *reported*, not
rewritten: a post-type rename leaves `shio/content/**` declaring the old `type:`, and the command
says which name to look for.

**Cascade gotcha:** `ShPostTypeAttr` maps `shWidget`/`shPostType`/`shParentPostTypeAttr` as
`@ManyToOne(cascade=ALL)`, so `ShPostTypeAdminService` detaches those before the
orphan-removal replace, otherwise an update would cascade-delete the shared widget catalog.

### Resolving a widget: `findFirstByName` is the only way (SH753)

The catalog is **assumed** unique by name and **nothing enforces it**, no unique index, and it is
a shared global row set that an import, `POST /api/v2/widget` or a cascading write can add to. So
`ShWidgetRepository.findByName`, the derived single-result finder, threw
`IncorrectResultSizeDataAccessException` on live data: a suite run met a catalog holding five rows
named `Text`, and because the surviving caller was `ShStaticFileService.attrOf`, which resolves
the File post type on **every upload**, the whole upload path went down.

**The method is deleted, not documented.** Three of its four call sites had already been moved to
`findFirstByName`, each with a comment saying why; the fourth never got the memo. A rule the
compiler enforces beats one a comment asks for, so there is now exactly one resolver.

**Tolerant readers would have made the duplication invisible**, which is the other half:
`ShWidgetOnStartup.duplicateNames()` groups the catalog and `ShOnStartup` logs a WARN naming each
duplicated name and its count. One grouping over a closed sixteen-row catalog, once per boot.

**Not a unique index, deliberately.** An instance that already carries duplicates would fail the
migration, so the index needs a de-duplication step ahead of it, and de-duplicating means choosing
which row the `ShPostTypeAttr`s on the losers get repointed to. That is a migration, not a seed,
and the seeder is additive-only by SH289's rule.


## What a model change would cost, before it is made (SH523)

Modelling was the least agent-native thing in the product, and the contrast was **inside** it. For
**content**, SH379 gives a proposal: what would change, what it costs, accept it an entry at a time.
For the **model** there was no such artefact, you write a post-type, push it, and find what broke
afterwards.

**`PUT /api/v2/post-type/{name}?dryRun=true`** returns the proposal instead of writing. A dry run on
the write an agent was going to make anyway, and **not** a `/post-type/{name}/proposal` of its own:
a new endpoint with no MCP tool behind it is a REST-only capability (P1), and SH262 made exactly
this call at exactly this fork when it attached template impact to `shio_read` rather than adding
`GET /agent/impact`.

Four questions, and they divide in a way worth knowing:

| Finding | Asks |
|---|---|
| `required-field-violated` | which published posts would violate a field this proposal makes required |
| `removed-field-still-read` | which templates interpolate a field this proposal removes |
| `declared-field-unread` | which declared fields no template reads |
| `stored-key-undeclared` | which stored keys no field declares |

The first two are about a **change** and cannot be asked without one. The last two are about the
model **as it stands** and are true with or without a proposal, so passing no body change at all is
a legitimate way to review a model nobody is currently editing.

**Why a proposal and not a lint.** A model change is a *decision with alternatives*: making a field
required is either a data cleanup or a mistake, and **only the count of violating posts tells them
apart**. A refusal cannot say that. It is also what makes modelling incremental, the safe move used
to be to declare a field optional and never tighten it, because tightening was the change nobody
could see the cost of. `ShModelProposalTest.aCleanTighteningIsSilent` is the other half of that: a
field nothing violates costs nothing, which is the answer that lets somebody actually tighten.

`stored-key-undeclared` is **SH474's defect asked as a question**. A `File` post stores `SHA256`,
`SIZE`, `WIDTH` and `HEIGHT` against a type declaring one field, and it took a lint firing 66 times
to surface that. `declared-field-unread` is deliberately **not** called a defect, a field can be
delivered over the CDA with no template reading it, and a proposal that cried defect there is one
nobody reads twice.

`count` and `sample` are separate fields because they answer different questions: *cleanup or
mistake* is the count, *which ones* is the sample. The sample is capped at ten and the count never
is, so a proposal about a large type stays both readable and actionable.

**Two instruments priced this and both were right.** `ShAnonymousRouteAccessTest` classified the new
mapping, and `ShSilentCatchInventoryTest` refused the `catch` that treats unparseable `data` as
empty until the decision was written down: the degradation is not invisible, it is *already reported
by somebody else* (malformed `data` is `verify`'s own finding) and a second incident for one
defect is two reports that can disagree. What the catch buys is that one unparseable post does not
count as violating every required field and drown the finding somebody asked for.

SH524 and SH525 build on this.

## The 404 that said nothing (SH621)

SH499's execution instrument issued the requests `docs-shio` publishes, and this was its first
finding: `GET /api/v2/post-type/Article` on a bare instance answered **404 with
`Content-Length: 0`**, not a thin problem document, no body at all. The same miss one surface over
(`/agent/read` on a nonexistent address) answered a full RFC 9457 document with `fix` and `allowed`.
**P6 was implemented on `/api/v2/agent/**` and nowhere else.**

That is the wrong half to have covered. Post-types are **the one name-keyed surface**, SH117
retired the id-keyed twin, so a name is the only thing a caller can get wrong here, and a typo is
the likeliest way to arrive at this 404 at all. `didYouMean` was already a solved mechanism on this
codebase; this is the endpoint it was invented for.

**The cost was not cosmetic.** With zero bytes back, a renamed type and a type that never existed
are the same answer, and the caller's next move (retry, create, or ask a curator) depends on which.
An agent that cannot tell them apart guesses, and guessing on a model change is how a second
post-type gets created beside the one that was meant. So the `fix` names *both* moves: create it,
or ask `?dryRun=true` (SH523) what adopting a definition would cost first.

Two things about the test worth keeping:

- **The body is asserted on the bytes, not on a parsed field.** "No body at all" is exactly what a
  test deserializing a DTO would have missed, and it was the defect's whole signature.
- **The fixture creates its own post-type.** The first draft assumed `Text` was seeded in the shared
  context; it is not, so every assertion read 404 and `allowed` was empty. `allowed` and
  `didYouMean` are claims about a *population*, so borrowing another test's would have made them
  statements about suite order, SH315's rule, arrived at the hard way.

## An ordinal is not a total order (SH722)

P5 promises byte-identical serialization, and SH714 measured it failing on the wire: **ten identical
`shio_context` calls returned two distinct packs**, differing in one line, two attributes swapping
places. `ShCdaMapper#schemaFields` sorted by `ordinal` and nothing else, over the `Set` the
persistence layer hands it in **hash order**, and `DESCRIPTION` and `PAGE-LAYOUT` both carried
`ordinal: 2`. The comparator declared them equal and the tie went to whatever order the set iterated
this time. *A stable sort over an unstable input is an unstable sort.*

The consequences run past the bytes. The pack carries its own `digest`, so the digest flipped between
two calls describing the same instance and P9's cheap proof stopped being one; and a conditional read
is ETagged on the rendered bytes, so revalidation missed for a reason the caller could not see.

**The fix is a tiebreaker. The deliverable is the lint (SH322).** The task named one line;
`ShUnstableSortLintTest` found **seven**, and three call sites that already had the tiebreaker,
which is the usual shape: somebody learned this before and fixed the call site in front of them, and
nothing carried the lesson. The seven span the delivered CDA field order, the GraphQL schema
published to every client, the console's post-type form, navigation ordering and a *write* path
(`ShPostUnifiedWriteService`), so two of them were not reads at all. Two were invisible to a
hand-written grep because they used a lambda (`(a, b) -> Integer.compare(...)`) rather than a
`Comparator` chain.

The rule the lint enforces is narrow on purpose: a sort is flagged only when its **only** key is one
this codebase knows repeats, `ordinal` and `position`, both curator-editable numbers nothing forbids
from colliding. A name, an id, an address or a URI needs no tiebreaker, and demanding one is how a
lint gets muted.

## Required means required, on every writer (SH1009)

A post-type has always been able to declare a field required. Nothing enforced it. The console's
form painted the asterisk and posted the data unconditionally; REST's create and update validated
the post-type name and nothing else; the agent's batch refused an **undeclared** key and never an
empty required one. The only enforcement anywhere was `required-field-empty` in `shio verify`,
after the fact, and only if somebody ran it, so a page missing its title published, served, and
reached a reviewer looking complete.

**A draft may be incomplete; a publish may not.** Drafts are where work happens, and a rule that
refused an incomplete one would be worse than the defect: a curator could not save a page they had
half written, and an agent could not build one in two ops. So saving is never refused and publishing
always is, the moment the page stops being somebody's work in progress and becomes what a visitor
is served. The refusal is a teaching 400 whose `at` is `data.<field>`, which the console's problem
reader already maps onto the input, so the curator lands in the box to type in.

`ShRequiredFields` is the one evaluator, and the reason it is extracted rather than written where it
was needed is the verify rule: two definitions of "empty" would mean a page verify calls complete
and a publish refuses. Three callers, the write service (which every publish passes through: the
console's, REST's, the batch's, the scheduler's), the plans (the console's publish preview and the
batch's dry run, which report it in SH997's shape rather than refusing, so a plan of ten ops reports
ten), and verify.

Two things the first draft of it got wrong, both caught by a suite rather than by review:

- **A post-type's attribute set is flat.** A Relator's child fields carry the same post-type, so an
  unfiltered walk asked the document for `title` when `title` is a column inside each row of
  `features`: and every first-party section post refused to publish, naming a field it was never
  supposed to carry. The filter is `getShParentPostTypeAttr() == null`, which is what verify already
  did.
- **A gate's waiver has to get past it.** `required-field-empty` is a check a site's gate can
  declare, so a refusal that ignored SH998's waiver would put a second lock on the one door that
  task built. A recorded waiver publishes; it is refused to an agent outright and written to both
  the audit row and the revision, so the page that goes live incomplete carries a person's name and
  their reason.

## A constraint vocabulary, evaluated where required already is (SH1010)

`required` was the only rule a field could declare. So a slug field accepted spaces, a rating
accepted eleven, a locale code accepted `english`, and every rule past presence lived in somebody's
head, enforced by whoever remembered it, on the surface they happened to be writing, and nowhere on
the other three.

A field now carries an optional `constraints` block: `minLength`, `maxLength`, `pattern` with a
`patternMessage`, `min`, `max`, `values`, `minItems`, `maxItems`, an advisory `recommendedLength`,
`unique`, and `defaultValue`.

`ShFieldRules` evaluates them at the same three places `ShRequiredFields` is evaluated: the write
service every publish passes through, the plans (which report rather than refuse, so a plan of ten
ops still reports ten), and `verify`, which gains `constraint-violated` as an error and
`longer-than-recommended` as a warning. `shio push --check` fails on the error, so a content tree is
provably valid before it is applied.

Four decisions worth keeping:

- **An absent value is not a violation.** A constraint describes what a value must look like *when
  there is one*; applying `minLength` to an empty optional field would make every constrained field
  required by the back door, which is the one way a constraint vocabulary quietly changes the model
  it was added to. Presence is `required`'s question and only its question.
- **Advisory is not a weaker error.** `recommendedLength` never refuses a write and never appears in
  a refusal. A title over sixty characters is a judgement about search results, not a broken
  document, and a rule that refused it would teach people to raise the number rather than shorten
  the title.
- **A broken rule is no rule.** An unparseable block, or a pattern that does not compile, reads as
  no constraints rather than as a failing one. Refusing every publish of every post of a type
  because somebody typed a bad regular expression into it puts the fix furthest from the person who
  can make it, the post-type editor is where that is reported.
- **A pattern needs a `patternMessage`.** It is not enforced, and the refusal says so when one is
  missing: a regular expression quoted back at a curator is a refusal only its author can act on,
  which is what P6 is about.

**Storage and round-trip.** One column holding JSON rather than a column per kind: the vocabulary
grows, and a column per kind is a migration per kind on the table every post-type write touches. An
absent block means no constraints, on both sides, the CLI's canonical serialization omits the key
entirely, the way SH914's `indexed` is omitted, so every `shio/post-types/*.json` written before
this round-trips byte for byte (P5).

### The two that are not about one value

`unique` and `defaultValue` landed in a second part, and the reason is what they are rather than how
big they were. Every other kind is decided by looking at the value in front of you, which is what
lets `ShFieldRules` be a pure function over a document. These two are not.

**`unique` reads other posts.** It lives in `ShFieldUniqueness`, with a repository and a
transaction, and keeping it out of the cheap evaluator is what stops that evaluator from quietly
acquiring a database round trip every caller then pays. The scope is the **post-type**: a product
code and a campaign code are not competing for one namespace because both are called `code`. There
is no index on a JSON field and no portable way to ask for one, so the check reads the type's
authoring rows and compares parsed values, **one query per publish of a post whose type declares a
unique field, and none at all for every other publish in the product**, which is what makes it
affordable. It runs last in the publish gate, so a page already refused for its own shape does not
pay for a scan of its siblings. Values are trimmed and folded: `ACME-1` and `acme-1` being two posts
is the failure the rule is added to prevent.

**`defaultValue` is not a check.** It fills a missing field on **create** only. A default re-applied
on update would overwrite a value somebody deliberately cleared, the one way a convenience becomes
a data loss, and one that filled a field already holding something would be a rule about what a
page says rather than about where it starts. The document is returned unchanged when nothing was
filled, so a post that needs no default keeps its bytes and its digest.

### Where a curator meets it

The post-type editor has a constraints section under each field's flags, because `required` is one
rule of twelve now and deciding that a slug may not contain spaces is the same kind of decision. A
cleared key is deleted rather than written as `null`, and a block with nothing left in it becomes no
block, a form that materialized `{}` would push a change to every field of every type the first
time anybody opened one.

The post form shows the rules as one sentence under the input. That is the point of the vocabulary
reaching the form at all: a rule stops being a discovery on save. It shows the `patternMessage` and
never the expression, and it includes the recommendation, named as advice.

## `Blocks`: a body an agent can address a paragraph of (SH1015)

A rich-text value is `innerHTML`: one string with no schema. P4 promises `Edit` and `Grep` are the
agent's cheapest tools and lands them inside a blob where a paragraph has no address and two saves
of the same document differ in whitespace nobody typed. `Blocks` stores an ordered array of typed
blocks instead, `heading`, `paragraph`, `list`, `quote`, `code`, `image`, `embed`, `reference`.

`ShBlocks` is the authority, and it is a pure function over JSON with no Spring context: a model
whose rules need an application running is one nobody checks.

- **`require(field, value)`** validates on the way **in**, from both batch write paths. A malformed
  document that reached storage would be found by whatever renders it, a blank region on somebody's
  page, the shape SH168 refused for an undeclared key. An unknown type is refused with the
  registered list (P6), and so are a duplicate `key`, a heading with no `level`, and a mark whose
  range runs off its own text (which otherwise renders as a silently truncated emphasis).
- **`canonical(value)`** sorts keys inside each object and **never** reorders the blocks: key order
  is not content and block order is. That is P5, the same document twice is the same bytes, which
  `innerHTML` cannot manage because attribute order, whitespace and entity spelling all vary freely.
- **`markdown(value)`** is the P4 projection: real `##` headings, `-` lists, fenced code. Nothing
  falls through to nothing, because a projection that silently drops a block is how an author's work
  disappears through a round trip.

A heading is a field of a block rather than a tag to be found, so an outline can be *read* off the
document (P9). The digest still parses the rendered page, which is the right subject for it, what
changed is that the document itself can now answer the question without markup in the way.

**Marks are ranges, not nesting.** `{from, to, type}` over the block's own text. `<b><i>x</i></b>`
and `<i><b>x</b></i>` are one content with two spellings, which is precisely what makes `innerHTML`
uncanonical; a range has one. They are applied back to front when writing Markdown, so an earlier
mark's offsets are still valid after a later one has inserted its delimiters.

**`HTML Editor` stays.** `shio convert` produces HTML for imported bodies, and SH956's sanitizer
already guards that path.

## `post-type check`: what the content does not satisfy (SH1018)

SH1010 made constraints refuse a bad write and said nothing about the rows already stored, so a type
given a required field has an instance full of content that violates it, found one publish at a
time. The hole underneath is older: a post's `data` is keyed by field name, so renaming or removing a
field leaves values under a key nothing declares, and `shio verify` reported orphan posts and folders
and never an orphan field.

**The measurement ships before the migration**, which is SH322's rule. A migration argued for by
"some posts probably violate this" is one nobody can size, review or check afterwards.
`GET /api/v2/post-type/{name}/check` and `shio post-type check <name>` answer how many rows break
each rule and how many carry an undeclared key.

Three things it is careful about:

- **Both states.** A draft that violates a constraint is a publish that will be refused; a published
  row that violates one is a page that is already wrong. Counting drafts alone hides the live half.
- **Two rule sets**, because the model has two: `ShFieldRules` covers what a field's constraints
  declare, and `required` is a flag on the attribute that `ShRequiredFields` enforces. The report
  ran only the first until a test said so, and the required field is the *motivating example*, so
  it would have missed the case it was written for.
- **Unreadable rows are counted, not skipped.** A row this cannot parse is one it cannot judge, and
  leaving it out of the total would understate the number the command exists to produce.

One row per rule with a count, worst first (a shape to act on, not a log to read) and undeclared
keys listed apart, because the fix is different: a violation is content to correct, an undeclared
key is a rename that happened on one side only. It exits non-zero when there is anything to
reconcile, so it can sit in a gate.

### `post-type migrate`: the change the check argues for (SH1018)

Three mappings and no fourth: `rename` moves a value to the key the model now declares, `drop`
removes one it no longer has, and `default` fills a field that was added as required. Those are
exactly the three shapes `check` reports, the migration answers the measurement rather than being a
general-purpose content rewriter. A fourth, *set this field to a computed value*, is a script, and a
script inside a content write is a door with no bound on what goes through it.

- **A rename never lands on an occupied key.** It refuses the whole run and names the rows. Both
  values are somebody's content, and the two rules that would resolve it (old wins, new wins) are
  each silent data loss half the time.
- **A default only fills what is empty.** Overwriting a value somebody typed would be the migration
  deleting content to satisfy a rule that content already met.
- **Nothing is written until everything is checked.** A run that failed on row 400 of 900 leaves a
  type half in one shape and half in another with no way to tell which rows are which.
- **It plans by default.** `dryRun` defaults to *true* on the server and the CLI needs `--apply`:
  the flag a caller forgets should leave the content alone.

**Where it is not: the batch surface.** The design put it there, and the batch vocabulary's own
budget refused it, `ShAgentOpsReachTest` measures that list at 950 estimated tokens and says that
growing past it is a reason to re-open the surface choice rather than raise the number. The op cost
about fifty, and paying by trimming five other ops' descriptions would have bought room by making
the rest less clear. It belongs here anyway: the batch surface is the *content* vocabulary, a
post-type migration is a model operation, and its report is `check` one path along. What batch would
have given free is a dry run, and that is a query parameter.

Every row the plan names is checked against the content ACL before anything is written. The content
is what changes, so permission on the model would be permission on the wrong thing.
