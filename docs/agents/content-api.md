# Content API — console reads, posts, objects, folders, sites, files

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### Content Navigation — the console reads use the CDA shapes (SH120)
- `GET /api/v2/site` - List all sites
- `GET /api/v2/object/{id}/list` — children as `ShConsoleListing`:
  `{ folderId, folders, posts }` — `ShCdaListing`'s envelope. Rows are **strict supersets**
  of `ShCdaFolder` / `ShCdaPostSummary`: the CDA's components first (same names, same types,
  same order), then the console-only fields (`date`/`position` on a folder;
  `postTypeTitle`/`published`/`publishStatus`/`state` on a post).
  `ShConsoleShapeOverlapTest` fails the build if a shared component drifts.
  **Paged since SH165**, with the CDA's own `page`/`size`/`totalPosts` (so the envelope is now
  the CDA's exactly, which is what that test asserts): `?page=&size=&sort=title|date|modifiedDate&q=`.
  **Only the posts page** — child folders come back whole on every page, because subfolders are
  structure (the move/copy picker walks them) and `size=1` is how a caller that wants only the
  structure asks. Three rules worth keeping: the **order and the filter are the server's**, since
  a client-side sort over a paged listing sorts one page and reads exactly like a correct answer —
  and **`q` is the CDA's own `FULLTEXT_PREDICATE` (SH240)**: title, summary and stored field data,
  the same predicate the delivery search uses, so the console cannot come to disagree with it about
  what "matches" is (it started as a title substring, faithful to the browser's old local filter,
  which left the console quietly weaker than the API it fronts; folder names stay a substring,
  since a folder has no content);
  an unknown `sort` is a teaching **400**, never a fallback to the default, because a listing
  answered in another order returns other *rows*; and the DRAFT-preference is a **predicate**
  (`ShPostUnifiedRepository#pageConsoleRows`), not an in-memory dedup like the CDA's, so `LIMIT`
  and the count are the database's and a post with both rows takes one slot and is counted once.
  **The browser's `Select → Everything` pages the rest in before selecting (SH237)** — a selection
  is a list of ids, so "everything" has to be everything, or the delete dialog says "50 item(s)" on
  a folder of five hundred and the curator is left believing it is empty. Past `SELECT_ALL_CAP`
  (1000) it selects what it loaded and *says* so rather than pulling an unbounded folder into the
  browser, which is the response paging removed; `invert` stays deliberately page-scoped, since
  inverting a set that includes rows nobody has seen is not a gesture anyone means. The rule to
  keep: **the number in the confirmation dialog is the number that will be acted on.**
- `GET /api/v2/object/{id}/path` and `GET /api/v2/folder/{id}/path` — the CDA's own
  **`ShCdaPath`**: `{ site, breadcrumb: [...folders], currentFolder }`. Note `site`, not
  `shSite`; a breadcrumb folder carries `root: true|false`, not `rootFolder: 1`.
- Owned by `ShConsoleNavigationMapper`, which composes `ShCdaMapper` rather than restating
  it. **Do not serialize `ShFolder`/`ShPostUnified` out of a console read** — that was the
  old shape, and it dragged each post's attributes, folder, site and post-type field tree
  along to render one row.

### Posts (unified model only)
Content posts live in `sh_post_unified` (NOT the legacy split `ShPost`/`ShPostDraft`,
deleted in the nuclear refactor). All post CRUD is the unified API:
- `GET /api/v2/post-unified/{id}?state=DRAFT|PUBLISHED`, `/folder/{folderId}`, `/{id}/versions`
- `POST /api/v2/post-unified` (body `{ folderId, postTypeId, data, state }`)
- `PUT /api/v2/post-unified/{id}`, `POST .../publish|unpublish|restore|schedule`
- `DELETE /api/v2/post-unified/{id}`

**Every post-shaped response on this controller is a `ShConsolePost`, never the entity (SH176)**
— reads *and* writes, owned by `ShConsolePostMapper` + `ShConsolePostReadService`. It carries
`data` **parsed** (the column is a JSON string) and the post-type as its SH16 `ShPostTypeDef`,
so the form projects `fields` (there is no `schemaJson` — SH177 dropped the column and deleted
`/api/v2/migration/unified/**`, its only writer). Mapping happens **inside a transaction**,
because `shPostType`/`shFolder` are LAZY and a detached proxy is exactly how the post-type used
to reach the browser as `null` and every post edit page rendered zero fields. A write therefore
re-reads rather than mapping the entity its own (closed) transaction returned. `undeclaredFields`
names data keys the post-type does not declare — the console shows them with a warning instead
of hiding them, which is the read side of SH168.
- `GET /api/v2/post/xp/{id}` - resolve a referenced post for display (returns
  `{ shPost, allowPublish }`, DRAFT-preferred); used by Content Select / File widgets
- There is **no** `/api/v2/post` controller — do not add one.

### Object Operations
**Both ends are authorized (SH201).** These two used to check `requireWrite(destId)` and nothing
else, so write on any single folder was enough to relocate content *out of* a subtree the caller
could not write — a removal, from the owner's point of view — or to copy content they had never
been granted read on. A move now requires WRITE on every source, a copy requires READ, and the
checks run over the whole body **before** the write service starts, so a partly-authorized batch
is refused whole. The rule to keep is symmetrical: **the two surfaces enforce the same ACL, and
whichever is stricter is the correct one** — the agent path has checked both ends since
SH134/SH137, and where the console is looser the console is the hole, not the bar.

- `PUT /api/v2/object/copyto/{destFolderId}` — Copy objects (body: `[id1, id2]`).
  **A copy takes names that are free (SH202).** It used to re-slug the copy's `furl` from the
  source's title — the same title, so the same slug — and since "clone" is copy-to-the-same-folder,
  the curator's most casual action put two posts in one site on one URL: a `duplicate-url`
  **error** from `shio verify`, and an address that resolves to whichever row is reached first,
  so the *original* went unaddressable too. Now `Home` clones as `Home (2)` / `home-2`, and the
  same numbering applies to a copied folder's **name**, because a folder is addressed by its
  name chain (SH79) and two siblings called `docs` are the same defect one level up. Two rules:
  the **title moves with the URL** (a `-2` URL under an identical title leaves two rows the
  curator cannot tell apart, which is the symptom they actually see), but the two counters are
  **independent** — a title is unique per *folder*, a URL per *site*, so a copy into another
  folder of the same site keeps its title and moves only its URL. Sharing one counter was the
  first implementation and it renamed a page for a collision in a scope the curator cannot see.
  The suffix is numeric, not `(copy)`, because the server writes it into content the console
  renders in whichever language the curator picked.
  **A folder cannot be copied into its own subtree (SH203).** SH137 gave *move* that guard and
  argued copy needed none — "the new folder is not yet in the tree, so it cannot be its own
  ancestor" — which is true of the folder being created and false of the traversal that fills
  it: `copyFolder` saves the copy and then walks `findByParentFolder(source)`, so a destination
  inside the source makes the walk descend into the copies it is still making, and it never
  returns. Both refusals now live on `ShFolderWriteService` (`checkMovable` / `checkCopyable`),
  the one place that knows what a subtree is.
- `PUT /api/v2/object/moveto/{destFolderId}` — Move objects (body: `[id1, id2]`).
  **A move relocates and changes nothing else (SH139):** it keeps the post's `furl`
  (it used to re-slug it from the title, silently changing a published page's live
  URL), assigns one only when there is none, and delegates the post branch to
  `ShPostUnifiedWriteService#move` — the same writer `post.move` uses, so the console
  and the agent cannot diverge on what a move means. A cross-site move onto an
  occupied URL is a teaching **409**, never quietly uniquified.
  **The folder branch delegates to `ShFolderWriteService#move` (SH137)**, which is where
  the cycle and site-root guards live: this path had neither, and a folder re-parented
  into its own subtree does not fail — it detaches the subtree from the site root and
  sends the recursive delete round the loop. Both are **409**s, so the console dialog
  needs its error path (it has one now).
  **And the picker no longer offers them (SH203).** The move/copy dialog navigates *down*
  from the site root, so the folders inside the one being dragged are exactly the ones a
  curator scrolls into — and they answer 409. A picker that lists choices the server will
  refuse teaches the curator that the operation fails at random, so the folders in the run
  are shown **disabled** (not hidden — someone hunting for "Docs" needs to see that it is the
  thing they are dragging, not conclude the picker lost it) and the confirm button carries the
  reason. The division to keep is the one SH108's confirm flow already uses: **the client
  prevents what it can see, the server refuses what it must** — a concurrent move or a stale
  tree is still the 409's job. It needs nothing new from the server, because the dialog only
  ever walks downward: a row is unreachable exactly when it is one of the ids being moved, and
  disabling it means the walk can never get *inside* one. That is why there is no ancestor
  check, and why the same disabling covers **copy** as well as move.
- Clone = copy to same folder — which is the gesture SH202 is about; see `copyto` above.

### Folders
- `GET /api/v2/folder/{id}` - Get folder
- `POST /api/v2/folder/{parentFolderId}` - Create folder
- `PUT /api/v2/folder/{id}` - Update folder
- `DELETE /api/v2/folder/{id}` - Delete folder

### Sites
- `GET /api/v2/site` - List all sites
- `GET /api/v2/site/{id}` — one site **by id, furl or name** (SH181, in that order so the
  declared key wins a collision). The reads resolve any of the three; `PUT`, `DELETE` and
  `/{id}/export` stay strictly id-keyed — a read that resolved a near-miss shows the curator
  the wrong site, a write that did would change it.
- `GET /api/v2/site/{id}/export` — a portable SH68 zip (id-keyed). **A failure the caller can fix
  teaches (SH349)**: a post whose stored `data` is not JSON answers **409 `export-post-data-invalid`**
  naming the post's SH79 address (also in `at`), the parser's own offset, and a runnable
  `agent/read` + `post.upsert` fix. The address is built with `ShFolderSite.of`, never
  `folder.getShSite()` — only a root folder carries the site. The other two export-side
  `ShExchangeException.Kind`s are the instance's own IO and stay a 500, rethrown unchanged, because
  `ShAgentErrorRecordingFilter` (`/api/*`) already records them as an SH190 incident with `frames`.
- `POST /api/v2/site` — create. **Session + CSRF**, so a stateless caller gets a bodiless 403;
  that is the friction SH170 is filed for, and `shio-react/e2e/provision.mjs` walks it by hand.

### Importing an exchange package — `POST /api/v2/import`

Multipart (`file`), accepts an SH68 package zip and clones it in via `ShSiteImportService`.
**Every failure teaches (SH189)** — it used to answer `ResponseEntity.badRequest().build()` for all
of them while `ShExchangeException` knew exactly which, and bringing the 2018
`openviglet/bootstrap-site` package forward cost seven field renames inferred by reading Java.
`ShImportFailedException` classifies into four slugs (`import-manifest-unreadable`,
`import-package-unreadable`, `import-no-site`, `import-failed`, plus `import-no-file`), each with an
SH80 `fix` naming the *action* rather than restating the diagnosis. Two rules: the **cause's**
message is carried as well as the wrapper's, because that is where Jackson names the property that
did not map (reporting only the wrapper reproduces the defect one level in); and the `no-site` case
points at `PUT /api/v2/post-type/{name}` and `POST /api/v2/agent/apply`, since a package with no site
is usually a post-type-only or blueprint-shaped archive.
**The classification comes from `ShExchangeException.Kind`, never from the message (SH267)** — a
`switch` with **no `default`**, so a new kind fails the build until somebody decides what a caller
should do about it, and the kind is mandatory at all thirteen throw sites because there is no
message-only constructor. Two properties are asserted over *every* kind: a reworded message cannot
change the slug or the fix, and every kind (export-side ones included) yields a slug and a non-blank
`fix`. Mapping the thirteen sites to what the old string match produced is what found the defects —
a missing `export.json` and a truncated upload both answered `import-failed`, and the manifest fix
named `shio-exchange.json`, a file this codebase never writes; the manifest entry name is
interpolated from `ShExchangePackageCodec.MANIFEST_ENTRY` now.
**CSRF is session-narrowed, not exempt** (SH167's matcher, registered in
`ShCsrfExemptionLintTest.MUST_BE_NARROWED`) — the console posts this form with a cookie, so a
blanket exemption would let a forged request create a *site* against a curator's session.
**`shio import <file.zip>` is the CLI half (SH268)** — multipart, printing `imported site:<name>`, and
the teaching body is rendered by `formatError` rather than swallowed. **There is no MCP route and that
is a decision**: an SH68 package is a zip and this surface is JSON, so the capability is CLI-shaped by
construction (SH186 is the precedent that makes it a decision — a REST-only capability whose input is
JSON became an op there). The P1 inventory carries `exchange` as `CLI_ONLY` with that reason, so an MCP
client reads which tool to reach for instead of discovering it by failing.

### Static files — upload/serve subsystem (SH38)
Binary files are first-class `File`-post-type unified posts; the bytes live on
disk, **keyed by the file post's id** (not the folder tree), under
`shio.file-source.path` (default `store/file_source/<postId>/<fileName>`).
- `POST /api/v2/staticfile/upload` (multipart: `file`, `folderId`, `createPost`) —
  creates a **published** `File` `ShPostUnified` (`data.FILE` = original name) +
  writes the bytes in the same tx; returns the created post (the `File` widget
  stores its `id`). `ShStaticFileService.resolveOrCreateFilePostType()` bootstraps
  the system `File` post type on first use (system post types are **not** seeded).
  **A zero-byte file is a file (SH514).** This refused an empty upload with
  `ResponseStatusException(BAD_REQUEST, "Upload is empty")` — Spring's default body, *no title, no
  detail, no fix*, the only endpoint in a whole session's traffic to answer that way while its own
  sibling refusal for a missing `folderId` carries all four. And the file was real:
  `vilt-group.com` serves `/css/en.css?v=cf8427e` as **200 with 0 bytes**, the capture wrote it
  faithfully, and a 64-asset conversion died on an empty stylesheet. `MultipartFile.isEmpty()` was
  answering two questions at once — *no part sent* and *a part holding an empty file* — and
  separating them settles it: the first is a caller mistake (`upload-no-file`, and
  `upload-unnamed-file` beside it, both teaching), the second is **content**, because a replica that
  404s where its source answered an empty 200 is less faithful rather than safer. `convert` counts
  them apart in its report (`… , 1 the source itself serves empty`), which is not the same fact as
  `with no bytes in the tree`.
- `GET /api/v2/staticfile/{id}` — streams the bytes; `GET /pre-upload/{folderId}/{fileName}`
  is a legacy-parity existence check (never blocks upload).
- `POST /api/v2/staticfile/{id}/replace` (multipart: `file`) — **new bytes for an existing asset
  (SH210)**, keeping the id, the folder, the name and the SH62 usage list, re-stamping size and
  dimensions, and auditing an `UPDATE`. `POST` rather than `PUT` because servlet multipart
  resolution is specified for `POST` and a container-dependent extra for anything else; the name
  the multipart carries is **ignored**, since it is half the asset's address and a replace must
  not move the asset. An empty body is a teaching 400 — it would truncate the asset and answer 200.
- `GET /api/v2/staticfile/{id}/digest` — `{id, fileName, size, sha256}`, so a caller can tell an
  edited asset from an untouched one without transferring either copy (SH210). **Since SH398 the
  digest is stamped into the post's `data` at upload and replace and rides the listing**, so this
  endpoint is the fallback for rows written before that rather than the common path — measured, it
  took `shio pull --content` over a settled 200-asset tree from 208 requests to 8. Two rules that
  keep the cache honest: `replace` **re-stamps** (a digest outliving its bytes restores the silent
  no-op SH210 removed), and the **existence check runs in front of the stamp**, because a stamp
  cannot know somebody deleted the bytes underneath and reporting it would turn SH62's `size: -1`
  into a confident lie. **SH399 stamps the size beside the digest as the cache's validator** — a
  stamped size that disagrees with the file's is proof the hash is stale, and both the listing and
  this endpoint then report no stamp and recompute instead. It detects a length change and nothing
  else, and it never repairs: re-stamping on a read would make a `GET` write.
- Bytes are also public at `/file_source/<postId>/<fileName>` via
  `ShStaticResourceConfiguration` (a purely-additive `WebMvcConfigurer`; only
  `/file_source/**` is exposed — **not** `/store/**`, which holds the H2 DB/logs).
- Deleting the post removes its bytes (`ShStaticFileCleanupListener` on the `DELETE`
  `ShPostLifecycleEvent`). Owner: `com.viglet.shio.api.staticfile` +
  `ShFileSourceProperties`. Per-tenant isolation (SH47) will prefix `tenantId`.

**Every write assigns a friendly URL, and `create` was the one that did not (SH651).** SH396 found
five posts created through `POST /api/v2/post-unified` all reported at their folder's own address —
five distinct posts, one address, every delivery URL a 404 — and the write answered `201` throughout.
The probe (`ShPostFurlOnCreateTest`) settled which of three things it was: the title derived
correctly from the `isTitle` field and `furl` came back **null**, and `CreateRequest` had no `furl`
component at all, so no caller could have sent one and no projection could have been wrong about a
value that was not there. Four decisions came out of the fix:

- **Derived from the title, corrected to the first free slug**: `contato`, `contato-2`, `contato-3`.
  The rule is the copy path's (SH202) and now lives in **`ShFurlNaming`** — `create` and `copyPost`
  are the two callers, because a second copy of the loop is the smell SH245 exists to lint.
  Free is checked across every spelling (SH583) and both state rows.
- **The title is never renumbered.** `furl` and title are separate namespaces (SH79) and SH139's
  point is that renaming does not re-derive the URL, so three posts called "Same Title" keep that
  title and get three addresses.
- **`furl` is an optional field on `CreateRequest`/`CreatePayload`.** Sent, it is identity (SH81) and
  is taken as given — a declared address that gets renumbered makes the next upsert of the same
  document create a second post. Omitted, it is derived. `ShAgentBatchService` still creates and
  then calls `setFurl`, which this field would collapse into one call — **not** done here, because
  `setFurl` stores the leading-slash form and folding the two would silently re-spell every
  agent-written address.
- **An uploaded file gets none.** `ShStaticFileService.upload` reaches this same write with the file
  name as its title, which would slug into `logopng`; SH38 says an asset has no friendly URL and
  `id:` is its only address, which is why SH172 added that fallback. `create` skips derivation for
  the `FILE` post-type rather than asking the uploader to opt out — the suite caught this one, and
  the next binary post-type will not have a test already watching.

**The stored spelling is bare — `same-title`, not `/same-title`.** That is what `copyPost`,
`ShFolderWriteService` and `ShSiteWriteService` already store via `ShURLFormatter.format`.
`setFurl`/`move` still guarantee the leading slash through `normalizeUrl` — the two forms coexist,
SH467 keeps whichever the source declared, and `ShFurlSpellings` is why reading tolerates both — but
one write must not emit both, which is what the first version of SH651 did.

**And an address is composed, never concatenated (SH666).** Because both spellings are stored and
both are legitimate, the place they go wrong is the projection. `ShSiteExportService` built
`"post:" + site.getName() + "/" + row.getFurl()`, which assumes the bare form: every post whose
address the *agent* path declared exported as `post:site//page`, and the same post created through
the console exported as `post:site/page`. One of those is not an SH79 address and nothing failed —
the export succeeded, naming a post nobody can resolve. **`ShAddress.post(site, furl)` is the
composer**; it normalises through `ShFurlSpellings.canonical`, which is the same reader every
resolver already uses, and `ShAddress.id(...)` is the fallback for a row with no furl.

Measured before it was fixed: **one** line in the whole backend composed an address by addition —
`ShAddressResolver.addressOf` and `ShAgentBatchService.addressOf` already went through `ShAddress`.
So this was a single bypass rather than a pattern, and the instrument is what keeps it that way:
`ShFurlSpellingLintTest.noAddressIsBuiltByStringAddition` fails on a scheme literal being added to
with a stored URL on the same line. **Both halves of that rule are load-bearing** — the first draft
required only the scheme literal and matched eight lines, one of them a comment: `"site:" + name` is
fine (a site address carries no path, so there is no spelling to get wrong) and the teaching errors
spell `"post:" + site + "/blog/new-url"` as an *example* of the grammar. A gate that trips on those
seven is one people bypass. `ShSiteExportServiceTest` carries the behavioural half: the same row
stored `/broken-post` and `broken-post` must export as one address.

**A friendly URL is not unique by construction, and the two readers differ (SH321).** Nothing in the
schema prevents two posts in one site claiming one — `duplicate-url` is an SH99 *error* for exactly
that — so the finder returns an **ordered `List`**. A reader that must serve something takes the
first (`findByShSite_IdAndFurlAndState`'s default): consistently wrong beats alternating, SH278's
rule on a second identity. **`ShAddressResolver` refuses** with a teaching **409** naming both posts
by `id:`, because an address is what a *write* takes and picking the first is how an edit lands on
the wrong post. Before this, the derived `Optional` finder threw
`IncorrectResultSizeDataAccessException` and eight call sites answered **500**.
**The rule now covers locale, and a lint keeps it covered (SH323).** The locale-keyed finder was
the same derived `Optional` one parameter along — SH321 fixed eight call sites and missed it, so the
CDA's localized read still 500'd — and `ShFurlReaderLintTest` refuses the next one: a derived
`Optional` over a `furl` fails the build, with `default` as the only hatch, because a default method
reads the ordered `List` and *picks* rather than assuming the data. That order (**the lint before
the fixes**) is SH322's habit in its first application.
**And `duplicate-url` is deliberately locale-blind — that is the definition, not an oversight.** Two
published translations at one URL look like legitimate content the lint wrongly flags, and the
address grammar settles it the other way: `post:<site>/<furl>` carries **no locale**, so
`ShAddressResolver` refuses the pair and `post.move` refuses the URL — the CMS cannot *name* either
post, whatever tells them apart. A locale-aware lint would leave the agent path refusing content
`verify` had just called clean, which is the worst failure available here. Locale-aware delivery is
for a translation with its **own** URL, plus `availableLocales` linking the two — and the finding
*says so* when the claimants differ by locale (appended, so an ordinary one stays byte-identical),
because the message it had reads as carelessness to somebody who chose deliberately. The locales
come from the cross-cutting URL index, never the loaded rows: a scoped run holds one of the two and
cannot speak for the other (SH143, SH263).

### There is NO generic `DELETE /api/v2/object/{id}` endpoint
Delete must be done by type: `/v2/post-unified/{id}` or `/v2/folder/{id}`.

**A folder delete is reversible, and it is a delegation (SH200).** It used to clear the subtree
with `shPostUnifiedRepository.deleteAll` — the repository, not the post writer — which skipped
the SH55 trash, the SH51 audit and the `DELETE` lifecycle event (so no webhook, and SH38's byte
cleanup never ran) in one line. That made the *most* destructive op on the surface the *least*
recoverable and the least observable, which is the inversion the task was filed for. Now every
post beneath it goes through `ShPostUnifiedWriteService#delete`, so a folder delete means exactly
what N post deletes mean, and `ShFolder.deleted_date` is stamped down the subtree instead of rows
being removed — the folder rows have to outlive the gesture, because a trashed post keeps its
`folder_id` and the `sh_post_unified.folder_id → ShObject` FK is `onDelete NO ACTION`. **The FK
ordering that motivated the old direct `deleteAll` is satisfied at purge time** (`ShFolderWriteService#purge`),
which is also where the lifecycle event fires — SH55's rule that a reversible trash has no side
effects. Beside it: `GET /v2/folder/trash` (one row per deleted *section*, not per folder in it),
`POST /v2/folder/{id}/untrash`, `DELETE /v2/folder/{id}/purge`, and the console's trash page lists
sections and posts together.

**And `DELETE /v2/site/{id}` goes through that same purge, which is what makes it work at all
(SH454).** It called `shSiteRepository.delete(site)` alone, with a comment explaining that an entity
delete is used so the cascade reaches the folders through `ShSite#shFolders`. It does — and **nothing
reached the posts**, which hold `sh_post_unified.folder_id`, so Hibernate ordered a folder delete
against rows that still referenced it and the database answered `Referential integrity constraint
violation`. The controller catches nothing, so the caller got a **500 with a Spring default body**
(SH189's shape) on the console's most destructive button. **Only an empty site deleted, which is the
one nobody deletes** — and that is exactly why it went unnoticed: the obvious test creates a bare
site and removes it, and it passes against the defect. Trashing the posts first is the natural
workaround and does not help; trash is a flag on the row, the FK is still there, and the 500 is
identical. The fix is SH245's shape rather than new logic — the root folder is trashed and purged
through `ShFolderWriteService`, which solved this ordering once and is the only place that knows it,
and only then is there a site whose cascade has nothing left to trip on. The site is **re-read after
the purge** because `purge` clears the persistence context, so the instance loaded before it is
detached by the time it returns. `delete` now takes an **actor**, since the walk down records an
audit entry per post and per folder — the most destructive gesture in the console was the one with
no name against it. Found by SH448's conformance suite **in its own teardown**, which had to do the
three calls by hand; that teardown is one call again, so a regression fails every scenario in the
suite with the 500 that started this.
- **Every folder read excludes a trashed folder**, in `ShFolderRepository` — its derived finders
  are spelled out as JPQL with `deletedDate is null`, keeping their names so no call site changed.
  There is deliberately **no `@SQLRestriction` on `ShFolder`**: it would also fire when a
  `@ManyToOne` to a folder is initialized, and a trashed post's `getShFolder()` is precisely what
  the trash listing reads. Ids still resolve polymorphically through `ShObjectRepository`, which
  knows nothing about this, so **every production read of an id goes through `ShLiveObject.of`
  (SH226)** — one predicate, enforced textually by `ShLiveObjectInvariantTest` over
  `src/main/java`, waived by a `ShLiveObject-exempt: <reason>` comment. SH200 hand-guarded the two
  paths it knew about with a private `live` helper each; **the lint's first run found two more,
  and one was a live defect** — `ShFolderWriteService#create` resolved its parent unguarded, so a
  section could be created *inside* a trashed one and then appear in no listing, because what is
  excluded is the ancestor and nothing asks whether a child's ancestor is gone. It answered `200`.
  The one exemption is `ShContentAclService`'s ancestor walk, which decides *whose grants apply*
  rather than what a curator may see: a trashed folder still has an ACL, and the restore that
  brings it back is authorized by it.
- **A restore returns what *that* deletion trashed** — the delete stamps one instant down the
  subtree and anything marked earlier stays in the trash. Compared on loaded `Instant`s, never in
  SQL: timestamp equality is a fractional-second promise H2, MariaDB and PostgreSQL do not make
  the same way.
- **The audit records the folder *and* each post**, which is what lets the SH109 queue undo a
  section in one entry — "restore the section" is the gesture a human means. `/agent/changes`
  resolves a folder address for such an entry (through the trash, since the folder is deleted by
  then): an entry nobody can address is a change nobody can act on.

**Every folder write is audited, and one row is one write (SH196).** SH200 recorded `delete` and
`restore`; `create`, `update` and `move` stayed silent, so the feed described a site's *contents*
and never its *shape* — a session resuming with `since=<cursor>` was told a post now lives at
`folder:Blog/archive/2025` and never that the section had appeared, been renamed from `2024`, or
moved, and those call for different next actions. Three decisions, each of which was the tempting
wrong answer:
- **One row per folder write, never one per post beneath it.** The obvious shape is an `UPDATE`
  per affected post, because that is what the feed already carried — but no post changed (SH137:
  a folder move leaves every URL and every field alone), and a hundred updates for one rename is
  what makes a consumer stop trusting the feed.
- **The subject is a column, not a new action.** `ShHistory.subject_kind` (`POST` / `FOLDER`,
  `ShContentSubject`) — a rename is an `UPDATE` whose subject is a folder, with the previous name
  in the description (`Updated "2025" (renamed from "2024")`). An action per kind of object would
  double the enum each time something else becomes auditable and break every consumer that
  switches on `action`. `null` means `POST`, which is what every pre-SH196 row was.
- **It is stored, not derived.** The subject *was* derived — ask the folder repository whether the
  audited id is a folder — and that stops working exactly where it matters: after a purge there is
  no row to ask, so a section's own deletion entry was reclassified as a post and the queue
  answered "the post no longer exists" about a section it had just watched being destroyed.
  `ShReviewService` reads the column and falls back to the probe only for rows that predate it.

The undo follows: a folder `CREATE` is reverted by trashing the section (SH55, recoverable), a
`DELETE` by restoring it, and a rename or move is **skipped with the reason** — the description
records what the change was, but the previous parent is a name and not an id, and re-deriving a
folder from a name is how a subtree lands under the wrong section. `ShFolderWriteAuditLintTest`
is why the next mutating method here cannot forget: it fails the build unless every
non-`readOnly` `@Transactional` method reaches the audit helper, `purge` excepted (the `DELETE`
that had to precede it is the audited row, matching `ShPostUnifiedWriteService#purge`).

**Backend ownership (rebuilt 2026.2 after the nuclear refactor wiped them):**
- `ShFolderAPI` + `ShFolderWriteService` (`com.viglet.shio.api.folder`)
- `ShObjectAPI` + `ShObjectNavigationService` + `ShObjectWriteService` (`...api.object`)
  — list/path/copyto/moveto; `clear-cache` is a no-op (no page-cache subsystem yet).
  Folders/sites are `ShObject` subclasses; **posts are not** (`ShPostUnified` is its
  own table) — so copy/move resolves folder ids via `ShObjectRepository` and post ids
  via `ShPostUnifiedRepository`. `/list` dedups posts by id, DRAFT-preferred.


## Deleting a site now says that it happened (SH223)

§XII.7 described `ShSiteWriteService.delete` as removing the `ShSite` and letting the JPA cascade
take its folders, so every post left past the trash with no audit, no feed entry and no `DELETE`
event. **That is no longer what the code does** — it trashes each root folder first and then purges
it, so the posts leave through SH55's trash with their SH51 rows, the SH88 feed and the events. The
section had gone stale, and reading it before writing anything is what caught that.

What was still true is narrower and worth fixing: **the site itself was unaudited.** Its posts were
recorded one level down and its folders two, so a reader of the feed saw every page of a site leave
and nothing saying the site had — an inference the audit trail exists to remove, on the largest
deletion in the product.

`ShContentSubject` gains `SITE` beside `POST` and `FOLDER`, and the delete records itself **after
the delete succeeds**, because an audit row for something that did not happen is worse than none.

### What is still open, and why it is not here

**Is a deleted site trashed, or only its content?** Today it is purged, so there is no undo at site
level while there is one at every level below. Trashing the content and removing the site row cannot
work — the posts keep their `folder_id` and the root folders their `site_id`, which is exactly why
SH200 gave the folder a `deleted_date` rather than deleting it.

So the site needs one too, and that is a Liquibase change, a filter on every read that lists sites,
and the cross-DB ITs a schema move brings. It also drags a second question: a trashed site still
occupies its name and friendly URL, so re-creating it collides and *"that name is taken by something
you deleted"* is a teaching error somebody has to write.

Filed as **SH648**. The precedent is clear and the cost is schema plus a listing rule, not design —
which is precisely why it should not ride along inside a service fix.

## The one write nobody recorded (SH224)

`ShPostUnifiedWriteService.purge` fired the `DELETE` lifecycle event — so the SH12 webhook went and
SH38's listener removed the stored bytes — and wrote **no audit row at all**. The reversible half of
the trash was fully recorded; **the irreversible half recorded nothing**. That is backwards: "who
destroyed this, and when" is a question about the deletion that cannot be undone.

It also left a purge invisible to `/agent/changes`, so a caller walking the feed saw a post enter
the trash and never learned it was gone for good — the two states a reader most needs to tell apart.

### `PURGE` is its own action

Not a second `DELETE`. Folding them would make the feed say a post left twice and never say which
time was final, and the review queue would offer an undo for something no code can reverse — so
`ShReviewService` answers *"permanently deleted — there is nothing left to restore"* rather than a
generic refusal. The compiler enforced that decision: adding a value to the action enum broke three
switches, which is the closed set doing its job.

The row is written **before the rows are gone**, so the title and site come off the entity while it
still exists. SH88 already carries an entry whose object has vanished — it travels with its title
and a `null` address — which is exactly the shape this needs.

`purge` now takes the actor the endpoints already had; a cascade passes `null` honestly rather than
attributing the destruction to whoever happened to be nearest.

### The overload lint was right and I was wrong twice

Threading the actor produced a `purge(id)` / `purge(id, actor)` pair. The first attempt kept the old
signature delegating to the new one — and **a delegation between overloads is a self-invocation that
never crosses the proxy**, so the native purge ran with no transaction and said so
(`No active transaction for update or delete query`). Annotating both was the second wrong answer:
`ShAgentTransactionalOverloadLintTest` refuses the *shape*, not the annotation, and its message says
to collapse the pair. Collapsing is what shipped, and two callers learned one argument.

*(Unrelated, and found while shipping this: `roadkeep lint` reports twelve pre-existing
`body.promise` problems in `IMPROVEMENTS.md` §0.x — twelve with and without this change, so a
roadkeep upgrade surfaced them rather than any commit causing them. Filed as SH649.)*
