# Content API: console reads, posts, objects, folders, sites, files

> Split out of the repo-root `agents.md` (see its routing table for the index).
> Read this before touching the area it covers. Detail here is authoritative.

### Content Navigation: the console reads use the CDA shapes (SH120)
- `GET /api/v2/site` - List all sites
- `GET /api/v2/object/{id}/list`: children as `ShConsoleListing`:
  `{ folderId, folders, posts }`: `ShCdaListing`'s envelope. Rows are **strict supersets**
  of `ShCdaFolder` / `ShCdaPostSummary`: the CDA's components first (same names, same types,
  same order), then the console-only fields (`date`/`position` on a folder;
  `postTypeTitle`/`published`/`publishStatus`/`state` on a post).
  `ShConsoleShapeOverlapTest` fails the build if a shared component drifts.
  **Paged since SH165**, with the CDA's own `page`/`size`/`totalPosts` (so the envelope is now
  the CDA's exactly, which is what that test asserts): `?page=&size=&sort=title|date|modifiedDate&q=`.
  **Only the posts page**: child folders come back whole on every page, because subfolders are
  structure (the move/copy picker walks them) and `size=1` is how a caller that wants only the
  structure asks. Three rules worth keeping: the **order and the filter are the server's**, since
  a client-side sort over a paged listing sorts one page and reads exactly like a correct answer,
  and **`q` is the CDA's own `FULLTEXT_PREDICATE` (SH240)**: title, summary and stored field data,
  the same predicate the delivery search uses, so the console cannot come to disagree with it about
  what "matches" is (it started as a title substring, faithful to the browser's old local filter,
  which left the console quietly weaker than the API it fronts; folder names stay a substring,
  since a folder has no content);
  an unknown `sort` is a teaching **400**, never a fallback to the default, because a listing
  answered in another order returns other *rows*; and the DRAFT-preference is a **predicate**
  (`ShPostUnifiedRepository#pageConsoleRows`), not an in-memory dedup like the CDA's, so `LIMIT`
  and the count are the database's and a post with both rows takes one slot and is counted once.
  **`published`/`publishStatus` on a post row are derived, not read off the row (SH1272)**: every
  publish writes a PUBLISHED row beside the draft and leaves the draft's own columns at `DRAFT`, so
  with the DRAFT-preference above every page ever published read as a draft, whoever published it,
  and a site an agent had just applied looked unpublished in the console while `/agent/read` and
  `/sites/**` served it. `ShConsoleNavigationMapper` reads the page's live rows in one query and
  answers `PUBLISHED` (a live row, same data), `CHANGED` (a live row, the draft differs) or `DRAFT`
  (no live row); `published` is true for the first two. The console's state mark and grid chip
  label all three.
  **The browser's `Select → Everything` pages the rest in before selecting (SH237)**: a selection
  is a list of ids, so "everything" has to be everything, or the delete dialog says "50 item(s)" on
  a folder of five hundred and the curator is left believing it is empty. Past `SELECT_ALL_CAP`
  (1000) it selects what it loaded and *says* so rather than pulling an unbounded folder into the
  browser, which is the response paging removed; `invert` stays deliberately page-scoped, since
  inverting a set that includes rows nobody has seen is not a gesture anyone means. The rule to
  keep: **the number in the confirmation dialog is the number that will be acted on.**
- `GET /api/v2/object/{id}/path` and `GET /api/v2/folder/{id}/path`, the CDA's own
  **`ShCdaPath`**: `{ site, breadcrumb: [...folders], currentFolder }`. Note `site`, not
  `shSite`; a breadcrumb folder carries `root: true|false`, not `rootFolder: 1`.
- Owned by `ShConsoleNavigationMapper`, which composes `ShCdaMapper` rather than restating
  it. **Do not serialize `ShFolder`/`ShPostUnified` out of a console read**, that was the
  old shape, and it dragged each post's attributes, folder, site and post-type field tree
  along to render one row.

### Posts (unified model only)
Content posts live in `sh_post_unified` (NOT the legacy split `ShPost`/`ShPostDraft`,
deleted in the nuclear refactor). All post CRUD is the unified API:
- `GET /api/v2/post-unified/{id}?state=DRAFT|PUBLISHED`, `/folder/{folderId}`, `/{id}/versions`
- `POST /api/v2/post-unified` (body `{ folderId, postTypeId, data, state }`)
- `PUT /api/v2/post-unified/{id}`, `POST .../publish|unpublish|restore|schedule`
- `DELETE /api/v2/post-unified/{id}`

**`.../schedule` takes each instant in either shape a caller holds (SH1179).** The body is
`{ publishAt, unpublishAt }`. Each field is an ISO-8601 instant, which is what `/agent/find`
returns and the console sends, or epoch millis, which the older tests post. `null` or an absent
field clears that side. Anything else is a teaching 400, `schedule-instant`, with `at` naming the
field. It read only millis until then, so every schedule saved from the console was a 400 with a
generic toast, and nothing noticed because nothing else writes through it.
`ShPostUnifiedAPITest.stage07db` posts the console's shape.

**The CDA's merge is the one stateless partial write, and it is conditional (SH1091).** The
three routes to changing one field of a post each hit a boundary drawn on purpose. The
maintainer chose the CDA's `PATCH /api/v2/cda/post/{id}` over exempting the console's merge
from CSRF, because it is the safer of the two. The console path keeps its boundary, and the
capability is a `WRITE` token, which an AGENT token deliberately is not (it has no
`ROLE_CDA_WRITE`). `ShCdaWriteAPI` borrows `ShConsolePostVersion`, so the two merges cannot
disagree about what "moved" means:
- `GET .../editable` answers the `ETag` of the row the merge reads: the draft, or the
  published row that a post with no draft is seeded from.
- A `PATCH` carrying it as `If-Match` is refused with 409 `stale-expectation` when that row
  changed. That is the console's status for every refusal about state; it is not 412.
- Every write answers the new tag.

`shio post set <address> <field>=<value>…` drives it with `SHIO_WRITE_TOKEN`, sending the
tag it read, or `--if-match` to pin an earlier one. `ShCdaWriteTest.sh1091*` holds the API
half, and `cli-surface.conformance.mjs` refuses a pinned stale set against a live instance.

**Every post-shaped response on this controller is a `ShConsolePost`, never the entity (SH176)**,
reads *and* writes, owned by `ShConsolePostMapper` + `ShConsolePostReadService`. It carries
`data` **parsed** (the column is a JSON string) and the post-type as its SH16 `ShPostTypeDef`,
so the form projects `fields` (there is no `schemaJson`, SH177 dropped the column and deleted
`/api/v2/migration/unified/**`, its only writer). Mapping happens **inside a transaction**,
because `shPostType`/`shFolder` are LAZY and a detached proxy is exactly how the post-type used
to reach the browser as `null` and every post edit page rendered zero fields. A write therefore
re-reads rather than mapping the entity its own (closed) transaction returned. `undeclaredFields`
names data keys the post-type does not declare, the console shows them with a warning instead
of hiding them, which is the read side of SH168.
- `GET /api/v2/post/xp/{id}` - resolve a referenced post for display (returns
  `{ shPost, allowPublish }`, DRAFT-preferred); used by Content Select / File widgets
- There is **no** `/api/v2/post` controller, do not add one.

### Object Operations

**One writer per content verb, and it is now a lint (SH245).** A content mutation goes through
`ShObjectWriteService` / `ShFolderWriteService` / `ShPostUnifiedWriteService`, never a repository
directly from a controller. The rule was written down and enforced nowhere, and the same defect was
found the same way four times, always because fixing the agent's version of a verb meant reading
the console's: a post move that re-slugged live URLs (SH139), a folder move with no cycle guard
(SH137), a folder delete with no trash, audit or lifecycle event (SH200), and a folder copy that
duplicated URLs and did not terminate (SH202/SH203). Each was caught by somebody happening to read
the neighbouring file; three had shipped for a release first.

`ShContentWriterOwnershipLintTest` fails the build on a write mapping whose body reaches a content
repository, with `ShContentWriter-exempt: <reason>` as the hatch, the same named-comment form as
`ShFolderSite-exempt`. Two things to know before editing it: it keys on the **content**
repositories by name, because "any `*Repository`" finds 23 admin-surface writes that are not this
defect; and the population is **0**, so a synthetic case reproducing SH200's shape is what proves
the regex still matches.

**A site's address has one writer too (SH744).** There were three, and only one checked anything:
the exchange import suffixed a taken furl with a random short id, `createBlank` saved whatever it
was given, and `create`'s SH71 bootstrap path handed the payload to the import, so a furl a curator
*typed* came back silently numbered. `ShSiteFurlNaming` now owns it, and the rule is the posts'
(SH81/SH651) rather than a new one: a **declared** address is identity and is refused when taken
(`site-furl-conflict`, 409), a **derived** one has no claim and is numbered `-2`, `-3`. An import
stays on the deriving side because a clone's exported furl is a starting point, not a promise this
instance made, so a package cannot fail halfway through on one name.

The check sits in `create` **before** the bootstrap branch, which is the part worth remembering:
there are two ways out of that method, the rule belongs to the verb rather than to one arm, and
putting it in `createBlank` alone left the console path (the one a curator uses) unguarded.
It mattered: `ShSiteRepository.findByFurl` returns **one** row and both `ShPageRenderService` and
`ShSiteAssetDelivery` resolve through it, so duplicates meant the database chose which site answered.

**Both ends are authorized (SH201).** These two used to check `requireWrite(destId)` and nothing
else, so write on any single folder was enough to relocate content *out of* a subtree the caller
could not write (a removal, from the owner's point of view) or to copy content they had never
been granted read on. A move now requires WRITE on every source, a copy requires READ, and the
checks run over the whole body **before** the write service starts, so a partly-authorized batch
is refused whole. The rule to keep is symmetrical: **the two surfaces enforce the same ACL, and
whichever is stricter is the correct one**, the agent path has checked both ends since
SH134/SH137, and where the console is looser the console is the hole, not the bar.

- `PUT /api/v2/object/copyto/{destFolderId}`: Copy objects (body: `[id1, id2]`). Answers
  `{folders, posts}` since SH1101. `folders` is the array it always returned. `posts` holds each post
  copied directly, as `{id, title, furl, address}`: the title and friendly URL SH202 may have
  numbered, and the address `ShAddressResolver` gives it. A post copy used to answer `[]`, so nothing
  could address it next, and the visual editor's Duplicate repeated the reference instead of placing
  a copy. Duplicate now copies the section into its own folder and names the copy in the Page's
  array in the neighbour's spelling (`section-reference.ts`: a path keeps its folder and takes the
  copy's friendly URL, an id becomes the copy's id). The frame reloads afterwards, because the
  bridge would tag an optimistically drawn copy with the original's post.
  **A copy takes names that are free (SH202).** It used to re-slug the copy's `furl` from the
  source's title (the same title, so the same slug) and since "clone" is copy-to-the-same-folder,
  the curator's most casual action put two posts in one site on one URL: a `duplicate-url`
  **error** from `shio verify`, and an address that resolves to whichever row is reached first,
  so the *original* went unaddressable too. Now `Home` clones as `Home (2)` / `home-2`, and the
  same numbering applies to a copied folder's **name**, because a folder is addressed by its
  name chain (SH79) and two siblings called `docs` are the same defect one level up. Two rules:
  the **title moves with the URL** (a `-2` URL under an identical title leaves two rows the
  curator cannot tell apart, which is the symptom they actually see), but the two counters are
  **independent**: a title is unique per *folder*, a URL per *site*, so a copy into another
  folder of the same site keeps its title and moves only its URL. Sharing one counter was the
  first implementation and it renamed a page for a collision in a scope the curator cannot see.
  The suffix is numeric, not `(copy)`, because the server writes it into content the console
  renders in whichever language the curator picked.
  **A folder cannot be copied into its own subtree (SH203).** SH137 gave *move* that guard and
  argued copy needed none, "the new folder is not yet in the tree, so it cannot be its own
  ancestor", which is true of the folder being created and false of the traversal that fills
  it: `copyFolder` saves the copy and then walks `findByParentFolder(source)`, so a destination
  inside the source makes the walk descend into the copies it is still making, and it never
  returns. Both refusals now live on `ShFolderWriteService` (`checkMovable` / `checkCopyable`),
  the one place that knows what a subtree is.
  **A copy carries its bytes, and until SH749 it did not.** A copied post gets a fresh UUID and
  SH38 keys storage on exactly that id, so a copied `File` post pointed at a directory nobody had
  written: the row is there, the reference resolves, `verify` passes, the page renders, and the
  asset **404s at delivery**. Every surface reported success, which is what made it survive since
  the clone button existed; SH244's `folder.copy` is what made it reachable at scale, because an
  agent duplicating a section is the first caller likely to copy a folder of assets and the one
  least able to notice. `ShFileStorageService#copy` duplicates the source id's whole directory,
  and it is asked **of the storage, not of the post-type**: "does this id have a directory" is the
  same question one syscall cheaper and cannot disagree with the layout, so an ordinary post is a
  no-op. **Copied, not shared**, pointing the copy at the source's directory is cheaper and wrong
  the first time either is purged, since `ShStaticFileCleanupListener` frees a directory on
  `DELETE`, so one asset's purge would blank every copy of it. `ShCopyKeepsAssetsTest` asserts the
  reader's own question (a copied `File` answers **200**) rather than a file being on disk, and it
  covers both surfaces because both share the writer. What the copy still does **not** do is move
  the SH45 storage meter, neither does an upload (nothing calls `ShTenantStorageMeter.record`),
  which is SH751.
- `PUT /api/v2/object/moveto/{destFolderId}`: Move objects (body: `[id1, id2]`).
  **A move relocates and changes nothing else (SH139):** it keeps the post's `furl`
  (it used to re-slug it from the title, silently changing a published page's live
  URL), assigns one only when there is none, and delegates the post branch to
  `ShPostUnifiedWriteService#move`: the same writer `post.move` uses, so the console
  and the agent cannot diverge on what a move means. A cross-site move onto an
  occupied URL is a teaching **409**, never quietly uniquified.
  **It answers `{moved, routes}` (SH1078).** `moved` is the folders relocated, as before.
  `routes` has one `{retired, created}` pair of SH79 addresses per *published* post whose
  address changed. The pairs come from reading the reachable posts' addresses before and
  after the write. A URL is a site plus a `furl`, so a move inside one site reports nothing.
  Crossing into another site, or being given a first `furl`, does report.
  **The folder branch delegates to `ShFolderWriteService#move` (SH137)**, which is where
  the cycle and site-root guards live: this path had neither, and a folder re-parented
  into its own subtree does not fail, it detaches the subtree from the site root and
  sends the recursive delete round the loop. Both are **409**s, so the console dialog
  needs its error path (it has one now).
  **And a folder cannot cross sites at all (SH1137).** It used to: the move *succeeded*, the
  folder's parent became the other site's root, and every post beneath it kept the old site in
  `shSite`: so delivery, which resolves a page by `findByShSite_IdAndFurlAndState`, went on
  answering them under the old site while the tree showed them under the new one. The split the
  `ShFolderSite` invariant exists to prevent, reached from the other direction. And because
  `requireUrlFree` never runs for posts beneath a folder, the destination could end up with two
  posts at one URL.

  **The finding is where the refusal was, not that one was missing.** §LXXII.25 supposed the
  agent's `folder.move` "probably has the same hole" and said to check before assuming, and it
  does not: it throws on the address, and its own docstring lists cross-site as the first of its
  four refusals while saying the other two live on the shared writer *"because the console can
  reach them too"*. This one did not. **A refusal that lives on one caller is a refusal the other
  caller does not have**, so it is on the writer now, beside the two that were already there.

  Refusing rather than carrying the subtree over, which §LXXII.25 offered as the alternative:
  carrying over means re-pointing every post row's site, checking each `furl` against the
  destination's, and treating every retired address as SH1064 treats a rename. That is what `shio
  export` and `shio import` already do, it is a bigger operation than "move", and getting it
  half-right is how a site ends up with two pages at one URL and no record of it. The 409 names
  both sites and those two verbs (P6). The site is **walked**, never read off the field (SH128),
  only a root folder carries `shSite`, so comparing fields would call every move cross-site.
  **And the picker no longer offers them (SH203).** The move/copy dialog navigates *down*
  from the site root, so the folders inside the one being dragged are exactly the ones a
  curator scrolls into, and they answer 409. A picker that lists choices the server will
  refuse teaches the curator that the operation fails at random, so the folders in the run
  are shown **disabled** (not hidden, someone hunting for "Docs" needs to see that it is the
  thing they are dragging, not conclude the picker lost it) and the confirm button carries the
  reason. The division to keep is the one SH108's confirm flow already uses: **the client
  prevents what it can see, the server refuses what it must**, a concurrent move or a stale
  tree is still the 409's job. It needs nothing new from the server, because the dialog only
  ever walks downward: a row is unreachable exactly when it is one of the ids being moved, and
  disabling it means the walk can never get *inside* one. That is why there is no ancestor
  check, and why the same disabling covers **copy** as well as move.
- Clone = copy to same folder, which is the gesture SH202 is about; see `copyto` above.

### Folders
- `GET /api/v2/folder/{id}` - Get folder
- `POST /api/v2/folder/{parentFolderId}` - Create folder
- `PUT /api/v2/folder/{id}` - Update folder
- `DELETE /api/v2/folder/{id}` - Delete folder

### Sites
- `GET /api/v2/site`: every live site, each row the site's own fields plus **`pages`
  `{published, draft}`** and **`lastChange` `{at, actor, agent}`** (SH1217, `ShSiteListingService`).
  A page live with a pending draft counts under both states; `agent` is false for a console
  session, which is the curator's (SH996); `lastChange` is absent when nothing was recorded
  against the site. **Three statements at any number of sites**: `findAll`, one grouped count
  (`countDistinctBySiteIdPerState`) and one newest-row read (`findNewestPerSite`), all keyed on the
  site's id. `ShSiteListingCostTest` counts the statements at 15 and 400 sites.

  **No `@Fetch(JOIN)` on a collection (SH1236).** `ShObject.shWorkflowTasks` carried one, which makes
  a collection eager, and a query cannot join it implicitly, so every query over a site, folder or
  post type paid one more select per row: a folder's 200 children cost 202 statements, the post-type
  catalogue one per type. It is lazy now (nothing reads it; the cascade still reaches it), and
  `ShWorkflowTaskFetchCostTest` holds both reads flat across two sizes. Each row also carries **`thumbnail` `{url, digest, at}`** when a
  picture of the published home is stored (SH1218), read with one directory listing.
- `GET /api/v2/site/{id}/thumbnail` · `GET …/thumbnail/status` · `PUT …/thumbnail?digest=`, the
  site's published home as a picture (SH1218, `ShSiteThumbnailService`). **The instance does not
  take it** (no browser, a standing non-goal): the console captures the home and sends the bytes.
  The instance decides whether one is needed, from the published home's **appearance digest**
  (`post:<site>/`, `state=published`), the hash review compares. `status` answers
  `{home, stored, fresh}`. A `PUT` must carry the digest `status` gave. A home that changed since
  is refused 409 `site-thumbnail-stale`, naming the digest now. A picture already at that digest
  answers `stored: false` and writes nothing, so re-publishing an unchanged home stores none.
  WebP, PNG or JPEG up to `shio.site-thumbnails.max-bytes` (512 KB). Bytes live under
  `shio.site-thumbnails.path`, beside `file_source` and outside its public resource handler.
  A site purge and a tenant teardown remove them.
- `GET /api/v2/site/{id}`: one site **by id, furl or name** (SH181, in that order so the
  declared key wins a collision). The reads resolve any of the three; `PUT`, `DELETE` and
  `/{id}/export` stay strictly id-keyed, a read that resolved a near-miss shows the curator
  the wrong site, a write that did would change it.
- `GET /api/v2/site/{id}/export`: a portable SH68 zip (id-keyed). **A failure the caller can fix
  teaches (SH349)**: a post whose stored `data` is not JSON answers **409 `export-post-data-invalid`**
  naming the post's SH79 address (also in `at`), the parser's own offset, and a runnable
  `agent/read` + `post.upsert` fix. The address is built with `ShFolderSite.of`, never
  `folder.getShSite()`: only a root folder carries the site. The other two export-side
  `ShExchangeException.Kind`s are the instance's own IO and stay a 500, rethrown unchanged, because
  `ShAgentErrorRecordingFilter` (`/api/*`) already records them as an SH190 incident with `frames`.
- `POST /api/v2/site`: create. **Session + CSRF**, so a stateless caller gets a bodiless 403;
  that is the friction SH170 is filed for, and `shio-react/e2e/provision.mjs` walks it by hand.

### What an export carries, and what it does not (SH1065)

The zip is an `ShExchangePackage`: the **site** row (url, furl, description, the post-type/layout
bindings, the searchable types, the form-success page), every **folder** under its roots, every
**post** in both states, the **post-types those posts use**, the referenced subset, not the whole
catalogue, and the site's **notes** (SH161, site-scoped only, so what an earlier session worked
out travels with it).

**And the bytes of every `File` post it carries (SH1134).** `files` lists each one whose bytes are on
disk, once per post (its two rows share an id, and storage is keyed by it), named by the post type's
own `FILE` field, and the bytes go in `files/<postId>`. The import stores each under the post's
**new** id, because the clone gives every post a fresh one and bytes left under the exported id
would belong to nothing; `arrived.files` counts them. A listed file with no bytes in the package is
`file-bytes-missing`, by name. A File post with no bytes on the source is simply not listed, which is
the true state of the source too (`asset-missing` says so there). This was a gap the design called a
choice: the SH1065 prose said the export carries no bytes, and nobody had decided that. The design
also claimed the import already restored `files/`; it did not, so both halves were written here.

It does **not** carry users, tokens, ACLs, revisions, releases, gates, schedules, or notes scoped to
another site or to the instance. Neither does it carry the tenant, `buildTenantPackage` is a
different call, SH72 provisioning is its only caller, and it is cloned in memory, so it has no
directory to carry bytes in.

That makes it a **hand-over**, not a backup and not a promotion. `shio pull --content` is the other
shape and answers a different question: it projects one site's content to files for authoring and
merges back three ways, carrying assets as paths on disk. Promoting between environments is
neither, the unit there is a release (Block BA), and naming it here is what keeps somebody from
reaching for a zip because it is the thing that exists.

### Importing an exchange package: `POST /api/v2/import`

Multipart (`file`), accepts an SH68 package zip and clones it in via `ShSiteImportService`.
**It answers `ShImportOutcome` (SH1079):** `{site, arrived, problems}`. `arrived` counts sites,
folders, logical posts, post types created here (a reused type is not counted) and restored files
(SH1134). `problems` holds what the clone loop already knew and used to drop. `post-skipped` is a
post whose folder the package does not carry. `post-type-unresolved` is a type the package does not
define and the instance does not have, so its posts land typeless. `file-bytes-missing` is a listed
file the package carries no bytes for. The console warns with the first problem, and `shio import` prints
an `arrived:` line and a `problem:` line for each. `importPackage` still returns the bare site for
the bootstrap and provisioning callers.
**Every failure teaches (SH189)**: it used to answer `ResponseEntity.badRequest().build()` for all
of them while `ShExchangeException` knew exactly which, and bringing the 2018
`openviglet/bootstrap-site` package forward cost seven field renames inferred by reading Java.
`ShImportFailedException` classifies into four slugs (`import-manifest-unreadable`,
`import-package-unreadable`, `import-no-site`, `import-failed`, plus `import-no-file`), each with an
SH80 `fix` naming the *action* rather than restating the diagnosis. Two rules: the **cause's**
message is carried as well as the wrapper's, because that is where Jackson names the property that
did not map (reporting only the wrapper reproduces the defect one level in); and the `no-site` case
points at `PUT /api/v2/post-type/{name}` and `POST /api/v2/agent/apply`, since a package with no site
is usually a post-type-only or blueprint-shaped archive.
**The classification comes from `ShExchangeException.Kind`, never from the message (SH267)**: a
`switch` with **no `default`**, so a new kind fails the build until somebody decides what a caller
should do about it, and the kind is mandatory at all thirteen throw sites because there is no
message-only constructor. Two properties are asserted over *every* kind: a reworded message cannot
change the slug or the fix, and every kind (export-side ones included) yields a slug and a non-blank
`fix`. Mapping the thirteen sites to what the old string match produced is what found the defects,
a missing `export.json` and a truncated upload both answered `import-failed`, and the manifest fix
named `shio-exchange.json`, a file this codebase never writes; the manifest entry name is
interpolated from `ShExchangePackageCodec.MANIFEST_ENTRY` now.
**CSRF is session-narrowed, not exempt** (SH167's matcher, registered in
`ShCsrfExemptionLintTest.MUST_BE_NARROWED`), the console posts this form with a cookie, so a
blanket exemption would let a forged request create a *site* against a curator's session.
**`shio import <file.zip>` is the CLI half (SH268)**: multipart, printing `imported site:<name>` first, and
the teaching body is rendered by `formatError` rather than swallowed. **There is no MCP route and that
is a decision**: an SH68 package is a zip and this surface is JSON, so the capability is CLI-shaped by
construction (SH186 is the precedent that makes it a decision, a REST-only capability whose input is
JSON became an op there). The P1 inventory carries `exchange` as `CLI_ONLY` with that reason, so an MCP
client reads which tool to reach for instead of discovering it by failing.

### Static files: upload/serve subsystem (SH38)
Binary files are first-class `File`-post-type unified posts; the bytes live on
disk, **keyed by the file post's id** (not the folder tree), under
`shio.file-source.path` (default `store/file_source/<postId>/<fileName>`).
- `POST /api/v2/staticfile/upload` (multipart: `file`, `folderId`, `createPost`),
  creates a **published** `File` `ShPostUnified` (`data.FILE` = original name) +
  writes the bytes in the same tx; returns the created post (the `File` widget
  stores its `id`). `ShStaticFileService.resolveOrCreateFilePostType()` bootstraps
  the system `File` post type on first use (system post types are **not** seeded).
  **A zero-byte file is a file (SH514).** This refused an empty upload with
  `ResponseStatusException(BAD_REQUEST, "Upload is empty")`: Spring's default body, *no title, no
  detail, no fix*, the only endpoint in a whole session's traffic to answer that way while its own
  sibling refusal for a missing `folderId` carries all four. And the file was real:
  `vilt-group.com` serves `/css/en.css?v=cf8427e` as **200 with 0 bytes**, the capture wrote it
  faithfully, and a 64-asset conversion died on an empty stylesheet. `MultipartFile.isEmpty()` was
  answering two questions at once (*no part sent* and *a part holding an empty file*) and
  separating them settles it: the first is a caller mistake (`upload-no-file`, and
  `upload-unnamed-file` beside it, both teaching), the second is **content**, because a replica that
  404s where its source answered an empty 200 is less faithful rather than safer. `convert` counts
  them apart in its report (`… , 1 the source itself serves empty`), which is not the same fact as
  `with no bytes in the tree`.
- `GET /api/v2/staticfile/{id}`: streams the bytes; `GET /pre-upload/{folderId}/{fileName}`
  is a legacy-parity existence check (never blocks upload).
- `POST /api/v2/staticfile/{id}/replace` (multipart: `file`): **new bytes for an existing asset
  (SH210)**, keeping the id, the folder, the name and the SH62 usage list, re-stamping size and
  dimensions, and auditing an `UPDATE`. `POST` rather than `PUT` because servlet multipart
  resolution is specified for `POST` and a container-dependent extra for anything else; the name
  the multipart carries is **ignored**, since it is half the asset's address and a replace must
  not move the asset. An empty body is a teaching 400, it would truncate the asset and answer 200.
- `GET /api/v2/staticfile/{id}/digest`: `{id, fileName, size, sha256}`, so a caller can tell an
  edited asset from an untouched one without transferring either copy (SH210). **Since SH398 the
  digest is stamped into the post's `data` at upload and replace and rides the listing**, so this
  endpoint is the fallback for rows written before that rather than the common path, measured, it
  took `shio pull --content` over a settled 200-asset tree from 208 requests to 8. Two rules that
  keep the cache honest: `replace` **re-stamps** (a digest outliving its bytes restores the silent
  no-op SH210 removed), and the **existence check runs in front of the stamp**, because a stamp
  cannot know somebody deleted the bytes underneath and reporting it would turn SH62's `size: -1`
  into a confident lie. **SH399 stamps the size beside the digest as the cache's validator**, a
  stamped size that disagrees with the file's is proof the hash is stale, and both the listing and
  this endpoint then report no stamp and recompute instead. It detects a length change and nothing
  else, and it never repairs: re-stamping on a read would make a `GET` write.
- Bytes are also public at `/file_source/<postId>/<fileName>` via
  `ShStaticResourceConfiguration` (a purely-additive `WebMvcConfigurer`; only
  `/file_source/**` is exposed, **not** `/store/**`, which holds the H2 DB/logs).
- Deleting the post removes its bytes (`ShStaticFileCleanupListener` on the `DELETE`
  `ShPostLifecycleEvent`). Owner: `com.viglet.shio.api.staticfile` +
  `ShFileSourceProperties`. Per-tenant isolation (SH47) will prefix `tenantId`.

**Replace keeps what it overwrites, outside the served tree (SH1035).** The endpoint has existed
since SH210 with one CLI caller; the console's route to new bytes was delete-and-re-upload, which
mints a new id and breaks every field pointing at the old one, what the verb's own javadoc calls
unsound. What SH1035 adds is the thing that makes replacing safe enough to encourage:

- `POST /api/v2/staticfile/{id}/replace` now **captures the superseded bytes first**, as a numbered
  rendition. A failed capture is logged and does not fail the replace: the curator asked for new
  bytes, and refusing them because the safety net could not be written would make this less reliable
  than the route it removes.
- `GET /api/v2/staticfile/{id}/renditions` lists them, newest first.
  `POST /api/v2/staticfile/{id}/renditions/{n}/revert` puts one back, and **captures the current
  bytes on the way past**, so a revert is itself undoable. Without that, a curator who reverts to the
  wrong version has destroyed the one they were trying to keep.
- **They are not under `/file_source/**`, and that is the one decision with a security edge.** That
  path is mapped at the root of the asset tree, so a rendition kept beside the live bytes would be
  publicly downloadable by anyone who guessed the path, a superseded document says whatever it was
  replaced for saying. They live under `shio.asset-renditions.path`, a sibling with no resource
  handler over it. `ShAssetRenditionTest` asserts that, because nothing about the wrong choice looks
  wrong in the code that writes it.
- **Bounded at `keep` (3) per asset, and excluded from the tenant meter.** SH110's rule is that a
  replace charges the *difference*, so counting a rendition would make replacing a 900 MB video with
  a 1 MB one *increase* usage and a tenant near its quota could not fix a mistake. The bound is what
  licenses the exclusion: the cost is a function of the asset's size, not of how often it is
  replaced. A revert re-stamps the digest and the size (SH398's stale-stamp no-op, reached from the
  other direction) and records an `UPDATE` in the audit trail.
- The name is never taken from the rendition. A replace cannot move an asset, so the live name is
  the asset's own; restoring an old one would change the public URL and break the references this
  whole feature exists to keep.

**`GET /api/v2/staticfile/{id}/usage` reads an index, and SH1033's numbers are why (SH1034).**
It used to be `findReferencingFileId`, `data like concat('%', :fileId, '%')`, a *leading* wildcard,
which no B-tree orders, run once per state, so one detail dialog read every post row in the tenant
twice to answer with a handful. `ShAssetUsageBenchmarkTest` measured it at 1,000 / 10,000 / 50,000
posts: rows examined per scan went 1,002 → 10,002 → 50,002, exactly linear, against a reverse index
that answers by examining **4** at a flat **6 statements per write**. Two curves, not two numbers,
which is why SH1034 built `sh_object_reference` rather than caching a count, and why §LXX.5's
"retire BE6" branch did not fire. The predicate is still on the repository and still measured, so
the saving cannot quietly stop being one.

The index is **derived**, which has one failure mode: a write that does not maintain it. It is
silent, the index answers with yesterday's edges, so usage is confidently wrong and an
unused-asset report offers a live asset for deletion. There is no choke point to rely on
(`ShPostUnifiedWriteService` saves from fifteen places and five other classes bypass it), so
**`ShReferenceIndexMaintenanceLintTest` fails the build** for a class that writes `setData` and
persists without naming `ShObjectReferenceService`. Two rules that are not obvious from the code:

- **Edges come from the model, not the bytes.** The walk reads the fields a post-type *declares* as
  `File` or `Content Select`. An id pasted into an undeclared key is no longer usage, narrower than
  the substring, and deliberately so: it is what lets a finding name the field to fix.
- **Only id-shaped values are indexed.** The `File` post type's own `FILE` attribute holds a file
  *name*, so taking every `File` field's value indexed each asset as pointing at its own name, and
  broke the write outright on a 38-character name against a 36-character column.

**What the count then reaches (SH1034's second half).** The listing stamps `usageCount` on the rows
of the page it returns, one indexed query for those rows, never one per row, and never over the
whole library to draw ten of it. `null` and `0` are different answers and every reader honours the
difference: `null` means this response did not ask, `0` means nothing references the asset, and `0`
is the value a deletion acts on. Four surfaces read it: the media card's badge, `verify`'s
`asset-unused` warning, `shio assets prune` (which sums the bytes and deletes nothing), and
`shio_assets --usage`, where the counts for a page are batched so an unused asset costs no further
query. A CLI meeting an instance that sends no `usageCount` **refuses** rather than reporting every
asset as unused, the reading that would otherwise invite a mass deletion.

**`loads=true` adds what pages load (SH1280).** The reference index cannot see CSS, so a theme's
`@font-face` font counted zero and prune listed Quilhara's fonts as reclaimable while `verify`
(SH1268) said they were used. `ShAssetLoadService` reads pages the way verify's page check does:
published posts of a layout-bound type, rendered, `ShPreviewLinks.referencesIn`, resolved with
`ShSiteAssets.findInSite`. Each row of the page then carries `loadedBy` (pages that request it), and
the envelope carries `pagesRendered` of `pagesTotal`, capped at 500 per site, since a zero past the
cap is a gap. It costs one render per page of the rows' sites, so it is opt-in and `null` otherwise.
Prune asks for it and calls an asset unused only when both counts are zero.
`ShBlueprintAccessibilityTest` asserts it on the applied Quilhara package. `shio_assets usage=true`
writes it on each `used` line (`used 0 loaded=48`, with `rendered=N/M` when the cap cut), through
`loadedByAssets`, which finds the sites from the asset ids. The media card asks only when the curator
presses *Count page loads*, so until then a zero is *unreferenced*, never *unused* (console-ui.md).

The instrument is worth knowing about independently: neither `getEntityLoadCount()` nor SH634's
`getExecutionRowCount()` can see a scan, because both count rows a query **returns** and a scan's
whole defect is that its answer is small. The benchmark reads H2's own `EXPLAIN ANALYZE`
`scanCount`, and the two ITs beside it read the plan on PostgreSQL and MariaDB, where the claim
stops being one fact. Details, including the option `pg_trgm` opens on one engine of three and the
**SH1099** storage defect that made this query return nothing at all on PostgreSQL until the index
moved the read off the column, are in [database.md](database.md) and in
`token-budgets.properties` under *What "where is this used" costs*.

**Every write assigns a friendly URL, and `create` was the one that did not (SH651).** SH396 found
five posts created through `POST /api/v2/post-unified` all reported at their folder's own address,
five distinct posts, one address, every delivery URL a 404, and the write answered `201` throughout.
The probe (`ShPostFurlOnCreateTest`) settled which of three things it was: the title derived
correctly from the `isTitle` field and `furl` came back **null**, and `CreateRequest` had no `furl`
component at all, so no caller could have sent one and no projection could have been wrong about a
value that was not there. Four decisions came out of the fix:

- **Derived from the title, corrected to the first free slug**: `contato`, `contato-2`, `contato-3`.
  The rule is the copy path's (SH202) and now lives in **`ShFurlNaming`**, `create` and `copyPost`
  are the two callers, because a second copy of the loop is the smell SH245 exists to lint.
  Free is checked across every spelling (SH583) and both state rows.
- **The title is never renumbered.** `furl` and title are separate namespaces (SH79) and SH139's
  point is that renaming does not re-derive the URL, so three posts called "Same Title" keep that
  title and get three addresses.
- **`furl` is an optional field on `CreateRequest`/`CreatePayload`.** Sent, it is identity (SH81) and
  is taken as given, a declared address that gets renumbered makes the next upsert of the same
  document create a second post. Omitted, it is derived. `ShAgentBatchService` still creates and
  then calls `setFurl`, which this field would collapse into one call, **not** done here, because
  `setFurl` stores the leading-slash form and folding the two would silently re-spell every
  agent-written address.
- **An uploaded file gets none.** `ShStaticFileService.upload` reaches this same write with the file
  name as its title, which would slug into `logopng`; SH38 says an asset has no friendly URL and
  `id:` is its only address, which is why SH172 added that fallback. `create` skips derivation for
  the `FILE` post-type rather than asking the uploader to opt out, the suite caught this one, and
  the next binary post-type will not have a test already watching.

**The stored spelling is bare, `same-title`, not `/same-title`.** That is what `copyPost`,
`ShFolderWriteService` and `ShSiteWriteService` already store via `ShURLFormatter.format`.
`setFurl`/`move` still guarantee the leading slash through `normalizeUrl`, the two forms coexist,
SH467 keeps whichever the source declared, and `ShFurlSpellings` is why reading tolerates both, but
one write must not emit both, which is what the first version of SH651 did.

**And an address is composed, never concatenated (SH666).** Because both spellings are stored and
both are legitimate, the place they go wrong is the projection. `ShSiteExportService` built
`"post:" + site.getName() + "/" + row.getFurl()`, which assumes the bare form: every post whose
address the *agent* path declared exported as `post:site//page`, and the same post created through
the console exported as `post:site/page`. One of those is not an SH79 address and nothing failed,
the export succeeded, naming a post nobody can resolve. **`ShAddress.post(site, furl)` is the
composer**; it normalises through `ShFurlSpellings.canonical`, which is the same reader every
resolver already uses, and `ShAddress.id(...)` is the fallback for a row with no furl.

Measured before it was fixed: **one** line in the whole backend composed an address by addition,
`ShAddressResolver.addressOf` and `ShAgentBatchService.addressOf` already went through `ShAddress`.
So this was a single bypass rather than a pattern, and the instrument is what keeps it that way:
`ShFurlSpellingLintTest.noAddressIsBuiltByStringAddition` fails on a scheme literal being added to
with a stored URL on the same line. **Both halves of that rule are load-bearing**, the first draft
required only the scheme literal and matched eight lines, one of them a comment: `"site:" + name` is
fine (a site address carries no path, so there is no spelling to get wrong) and the teaching errors
spell `"post:" + site + "/blog/new-url"` as an *example* of the grammar. A gate that trips on those
seven is one people bypass. `ShSiteExportServiceTest` carries the behavioural half: the same row
stored `/broken-post` and `broken-post` must export as one address.

**The schema now prevents a new one, and everything below still applies to the old ones (SH719).**
A unique index over `(site, state, locale, furl)`, carried as a SHA-256 `url_key` column the entity
derives in `@PrePersist`/`@PreUpdate`, for the three reasons `v2026.3.24.yaml` sets out, means a
second *live* post cannot reach an occupied address. It closed a data-corruption defect a lookup
could not: two connections, one `post.upsert` each, the same new address, **both told
`changed=1`**, and an address left that no write can name and no read can resolve. The loser now
gets a `duplicate-url` 409 whose fix says **retry**, the one refusal on this surface where that is
right, because `post.upsert` is an upsert and the address exists now.

Three things the key deliberately does *not* forbid: a post's own DRAFT and PUBLISHED rows sharing a
furl (`state` is in the key, or publishing would refuse itself, SH81); **two locales at one furl**,
which SH348 calls content by design; and a **trashed** row's address, which stays free because SH243
is explicit that a live post may take it and the *restore* is what gets refused. **SH691 gave the
title the same treatment**: a copy's title counter is trash-blind on purpose, numbering against
content no listing shows would produce "Home (3)" beside a single visible "Home (2)", a rename the
curator cannot account for, so a clone may retake a trashed post's title, and the restore refuses
with `restore-title-conflict` (409) naming the folder. Both refusals live in one place,
`ShPostUnifiedWriteService.restoreConflict`, so the throwing half (the folder pre-flight, the batch
plan) and the reporting half (`ShReviewService`) cannot disagree about what a restore would do; the
URL is checked first because it is what an address resolves by. The last two are
carried as `locale` in the digest and NULL for a trashed row, all three engines treat NULLs as
distinct under a unique index, which is how a partial index is expressed where MariaDB and H2 have
none.

*And a declared furl is refused rather than renumbered when taken.* SH81/SH651's rule was that a
declared address is identity and must never be renumbered; what nothing had chosen was that keeping
it meant putting a **second** post on it. Refusing is the other answer that leaves one post at one
address, and it leaves the rule intact.

*And `verify` can see the case its own refusal names (SH720).* The refusal ends *"`GET
/api/v2/agent/verify` reports this as `duplicate-url` with both addresses"*, and that sentence was
**false in the case that produces it**: the check iterated published rows only, so a draft pair came
back `report ok`, refused, sent to an instrument, told nothing is wrong, with no safe way to
believe both answers. The check now counts **distinct post ids** per URL across both states. That is
what makes widening safe rather than noisy: a post's DRAFT and PUBLISHED rows share a furl by design
(SH81), so counting *rows* makes every published page its own duplicate, which is why the old check
looked at published rows alone, and the comment saying so was right about rows and wrong about the
question. Counting ids fires on nothing ordinary and catches three cases that were invisible: draft
vs draft, draft vs another post's published row, and the published pair already caught.

**A friendly URL was not unique by construction, and the two readers differ (SH321).** Nothing in the
schema prevented two posts in one site claiming one, `duplicate-url` is an SH99 *error* for exactly
that, so the finder returns an **ordered `List`**. That reader is not obsolete: SH719 cannot act
retroactively, and `ShPostUrlKeyBackfillRunner` deliberately leaves a losing row un-keyed rather
than refusing to start, so an instance that met the defect keeps its duplicates and keeps needing
them reported. It is also why SH720's fixture lives in the **unit** suite: the conformance suite can
no longer build a duplicate through any sequence of calls, so the state is reachable only by a bulk
row update, which is exactly what a pre-SH719 instance holds. A reader that must serve something takes the
first (`findByShSite_IdAndFurlAndState`'s default): consistently wrong beats alternating, SH278's
rule on a second identity. **`ShAddressResolver` refuses** with a teaching **409** naming both posts
by `id:`, because an address is what a *write* takes and picking the first is how an edit lands on
the wrong post. Before this, the derived `Optional` finder threw
`IncorrectResultSizeDataAccessException` and eight call sites answered **500**.
**The rule now covers locale, and a lint keeps it covered (SH323).** The locale-keyed finder was
the same derived `Optional` one parameter along, SH321 fixed eight call sites and missed it, so the
CDA's localized read still 500'd, and `ShFurlReaderLintTest` refuses the next one: a derived
`Optional` over a `furl` fails the build, with `default` as the only hatch, because a default method
reads the ordered `List` and *picks* rather than assuming the data. That order (**the lint before
the fixes**) is SH322's habit in its first application.
**And `duplicate-url` is deliberately locale-blind, that is the definition, not an oversight.** Two
published translations at one URL look like legitimate content the lint wrongly flags, and the
address grammar settles it the other way: `post:<site>/<furl>` carries **no locale**, so
`ShAddressResolver` refuses the pair and `post.move` refuses the URL, the CMS cannot *name* either
post, whatever tells them apart. A locale-aware lint would leave the agent path refusing content
`verify` had just called clean, which is the worst failure available here. Locale-aware delivery is
for a translation with its **own** URL, plus `availableLocales` linking the two, and the finding
*says so* when the claimants differ by locale (appended, so an ordinary one stays byte-identical),
because the message it had reads as carelessness to somebody who chose deliberately. The locales
come from the cross-cutting URL index, never the loaded rows: a scoped run holds one of the two and
cannot speak for the other (SH143, SH263).

### There is NO generic `DELETE /api/v2/object/{id}` endpoint
Delete must be done by type: `/v2/post-unified/{id}` or `/v2/folder/{id}`.

**A folder delete is reversible, and it is a delegation (SH200).** It used to clear the subtree
with `shPostUnifiedRepository.deleteAll` (the repository, not the post writer) which skipped
the SH55 trash, the SH51 audit and the `DELETE` lifecycle event (so no webhook, and SH38's byte
cleanup never ran) in one line. That made the *most* destructive op on the surface the *least*
recoverable and the least observable, which is the inversion the task was filed for. Now every
post beneath it goes through `ShPostUnifiedWriteService#delete`, so a folder delete means exactly
what N post deletes mean, and `ShFolder.deleted_date` is stamped down the subtree instead of rows
being removed, the folder rows have to outlive the gesture, because a trashed post keeps its
`folder_id` and the `sh_post_unified.folder_id → ShObject` FK is `onDelete NO ACTION`. **The FK
ordering that motivated the old direct `deleteAll` is satisfied at purge time** (`ShFolderWriteService#purge`),
which is also where the lifecycle event fires, SH55's rule that a reversible trash has no side
effects. Beside it: `GET /v2/folder/trash` (one row per deleted *section*, not per folder in it),
`POST /v2/folder/{id}/untrash`, `DELETE /v2/folder/{id}/purge`, and the console's trash page lists
sections and posts together.

**And `DELETE /v2/site/{id}` goes through that same purge, which is what makes it work at all
(SH454).** It called `shSiteRepository.delete(site)` alone, with a comment explaining that an entity
delete is used so the cascade reaches the folders through `ShSite#shFolders`. It does, and **nothing
reached the posts**, which hold `sh_post_unified.folder_id`, so Hibernate ordered a folder delete
against rows that still referenced it and the database answered `Referential integrity constraint
violation`. The controller catches nothing, so the caller got a **500 with a Spring default body**
(SH189's shape) on the console's most destructive button. **Only an empty site deleted, which is the
one nobody deletes**, and that is exactly why it went unnoticed: the obvious test creates a bare
site and removes it, and it passes against the defect. Trashing the posts first is the natural
workaround and does not help; trash is a flag on the row, the FK is still there, and the 500 is
identical. The fix is SH245's shape rather than new logic, the root folder is trashed and purged
through `ShFolderWriteService`, which solved this ordering once and is the only place that knows it,
and only then is there a site whose cascade has nothing left to trip on. The site is **re-read after
the purge** because `purge` clears the persistence context, so the instance loaded before it is
detached by the time it returns. `delete` now takes an **actor**, since the walk down records an
audit entry per post and per folder, the most destructive gesture in the console was the one with
no name against it. Found by SH448's conformance suite **in its own teardown**, which had to do the
three calls by hand; that teardown is one call again, so a regression fails every scenario in the
suite with the 500 that started this.
- **Every folder read excludes a trashed folder**, in `ShFolderRepository`, its derived finders
  are spelled out as JPQL with `deletedDate is null`, keeping their names so no call site changed.
  There is deliberately **no `@SQLRestriction` on `ShFolder`**: it would also fire when a
  `@ManyToOne` to a folder is initialized, and a trashed post's `getShFolder()` is precisely what
  the trash listing reads. Ids still resolve polymorphically through `ShObjectRepository`, which
  knows nothing about this, so **every production read of an id goes through `ShLiveObject.of`
  (SH226)**, one predicate, enforced textually by `ShLiveObjectInvariantTest` over
  `src/main/java`, waived by a `ShLiveObject-exempt: <reason>` comment. SH200 hand-guarded the two
  paths it knew about with a private `live` helper each; **the lint's first run found two more,
  and one was a live defect**, `ShFolderWriteService#create` resolved its parent unguarded, so a
  section could be created *inside* a trashed one and then appear in no listing, because what is
  excluded is the ancestor and nothing asks whether a child's ancestor is gone. It answered `200`.
  The one exemption is `ShContentAclService`'s ancestor walk, which decides *whose grants apply*
  rather than what a curator may see: a trashed folder still has an ACL, and the restore that
  brings it back is authorized by it.
- **A restore returns what *that* deletion trashed**, the delete stamps one instant down the
  subtree and anything marked earlier stays in the trash. Compared on loaded `Instant`s, never in
  SQL: timestamp equality is a fractional-second promise H2, MariaDB and PostgreSQL do not make
  the same way.
- **The audit records the folder *and* each post**, which is what lets the SH109 queue undo a
  section in one entry, "restore the section" is the gesture a curator means. `/agent/changes`
  resolves a folder address for such an entry (through the trash, since the folder is deleted by
  then): an entry nobody can address is a change nobody can act on.

**Every folder write is audited, and one row is one write (SH196).** SH200 recorded `delete` and
`restore`; `create`, `update` and `move` stayed silent, so the feed described a site's *contents*
and never its *shape*, a session resuming with `since=<cursor>` was told a post now lives at
`folder:Blog/archive/2025` and never that the section had appeared, been renamed from `2024`, or
moved, and those call for different next actions. Three decisions, each of which was the tempting
wrong answer:
- **One row per folder write, never one per post beneath it.** The obvious shape is an `UPDATE`
  per affected post, because that is what the feed already carried, but no post changed (SH137:
  a folder move leaves every URL and every field alone), and a hundred updates for one rename is
  what makes a consumer stop trusting the feed.
- **The subject is a column, not a new action.** `ShHistory.subject_kind` (`POST` / `FOLDER`,
  `ShContentSubject`), a rename is an `UPDATE` whose subject is a folder, with the previous name
  in the description (`Updated "2025" (renamed from "2024")`). An action per kind of object would
  double the enum each time something else becomes auditable and break every consumer that
  switches on `action`. `null` means `POST`, which is what every pre-SH196 row was.
- **It is stored, not derived.** The subject *was* derived, ask the folder repository whether the
  audited id is a folder, and that stops working exactly where it matters: after a purge there is
  no row to ask, so a section's own deletion entry was reclassified as a post and the queue
  answered "the post no longer exists" about a section it had just watched being destroyed.
  `ShReviewService` reads the column and falls back to the probe only for rows that predate it.

The undo follows: a folder `CREATE` is reverted by trashing the section (SH55, recoverable), a
`DELETE` by restoring it, and a rename or move is **skipped with the reason**, the description
records what the change was, but the previous parent is a name and not an id, and re-deriving a
folder from a name is how a subtree lands under the wrong section. `ShFolderWriteAuditLintTest`
is why the next mutating method here cannot forget: it fails the build unless every
non-`readOnly` `@Transactional` method reaches the audit helper, `purge` excepted (the `DELETE`
that had to precede it is the audited row, matching `ShPostUnifiedWriteService#purge`).

**Backend ownership (rebuilt 2026.2 after the nuclear refactor wiped them):**
- `ShFolderAPI` + `ShFolderWriteService` (`com.viglet.shio.api.folder`)
- `ShObjectAPI` + `ShObjectNavigationService` + `ShObjectWriteService` (`...api.object`),
  list/path/copyto/moveto; `clear-cache` is a no-op (no page-cache subsystem yet).
  Folders/sites are `ShObject` subclasses; **posts are not** (`ShPostUnified` is its
  own table), so copy/move resolves folder ids via `ShObjectRepository` and post ids
  via `ShPostUnifiedRepository`. `/list` dedups posts by id, DRAFT-preferred.


## Deleting a site now says that it happened (SH223)

§XII.7 described `ShSiteWriteService.delete` as removing the `ShSite` and letting the JPA cascade
take its folders, so every post left past the trash with no audit, no feed entry and no `DELETE`
event. **That is no longer what the code does**, it trashes each root folder first and then purges
it, so the posts leave through SH55's trash with their SH51 rows, the SH88 feed and the events. The
section had gone stale, and reading it before writing anything is what caught that.

What was still true is narrower and worth fixing: **the site itself was unaudited.** Its posts were
recorded one level down and its folders two, so a reader of the feed saw every page of a site leave
and nothing saying the site had, an inference the audit trail exists to remove, on the largest
deletion in the product.

`ShContentSubject` gains `SITE` beside `POST` and `FOLDER`, and the delete records itself **after
the delete succeeds**, because an audit row for something that did not happen is worse than none.

## A deleted site can be restored (SH648)

The question SH223 left open (*is a deleted site trashed, or only its content?*) is answered:
**trashed.** Until this, the recoverability of a gesture *decreased* as it got wider. A curator who
deleted forty pages one at a time could restore them; the same curator deleting the section could
restore it (SH200); the same curator deleting the **site** could not. That is the inversion, and it
is the one nothing asserted, because every level was individually correct.

`ShSite` gains a `deleted_date` for the reason `ShFolder` needed one: the posts keep their
`folder_id` and the root folders their `site_id`, so removing the site row is what makes the gesture
irreversible. The row now outlives the delete.

**The verbs, and which is which.**

- `DELETE /v2/site/{id}`: **trashes**, stamping the site and then trashing each root folder through
  `ShFolderWriteService.delete`. The site is stamped **first**: `restore` brings back only what this
  deletion trashed, comparing each folder's instant against the site's, and stamping the site last
  would put it after every folder's and exclude the whole tree.
- `POST /v2/site/{id}/untrash`: restores, cascading into the folder writer's own restore.
- `DELETE /v2/site/{id}/purge`: SH454's gesture, now asked for by name. It takes a **live** site as
  well as a trashed one, which is what keeps "remove this and everything in it" one call for the
  callers that never wanted a trash: the conformance teardown and `shio site rm --scratch`.
- `GET /v2/site/trash`: what is in there, newest first.

**Every site read excludes a trashed site**, in `ShSiteRepository`, its finders spelled out as JPQL
with `deletedDate is null` and their names kept so no call site changed, the same choice
`ShFolderRepository` made, and for the same reason: an `@SQLRestriction` on `ShSite` would also fire
when a `@ManyToOne` to it is initialized, and a trashed site's root folder reads `getShSite()` on
exactly the path a restore walks. The trash's own reads are `findAnyById`, `findAnyByName`,
`findAnyByFurl` and `findTrashedSites`.

**The collision it introduces, which is why this was a schema task and not a service fix.**
`ShSite.name` is `unique`, so a trashed site keeps holding its name and the re-create that used to
succeed now reaches the database. Left alone that is a body-less 500 naming a site the caller cannot
find in any listing, the worst possible moment to meet it. `ShSiteNameHeldByTrashException` (409)
says where the name went and names both ways out. The **furl** carries no such constraint, so the
opposite case exists too: a live site can take a trashed one's address, and the restore that meets
it is refused by `ShSiteRestoreConflictException` rather than renamed, SH243's rule, one level up.

**On the agent surface**, `site.delete` now trashes and its plan line says so; `data.purge:true`
declines the trash (SH413's modifier shape, the same gesture with the undo declined). `site.restore`
is a name of its own, because a restore is not a way of declaring a site's state and calling it
anything else would break the one vocabulary a caller has already learned from `post.restore` and
`folder.restore`. It is the one restore addressed by `site:<name>` rather than `id:`: a trashed site
keeps its unique name, so an agent that trashed a site can undo it without having kept an id.

## The one write nobody recorded (SH224)

`ShPostUnifiedWriteService.purge` fired the `DELETE` lifecycle event, so the SH12 webhook went and
SH38's listener removed the stored bytes, and wrote **no audit row at all**. The reversible half of
the trash was fully recorded; **the irreversible half recorded nothing**. That is backwards: "who
destroyed this, and when" is a question about the deletion that cannot be undone.

It also left a purge invisible to `/agent/changes`, so a caller walking the feed saw a post enter
the trash and never learned it was gone for good, the two states a reader most needs to tell apart.

### `PURGE` is its own action

Not a second `DELETE`. Folding them would make the feed say a post left twice and never say which
time was final, and the review queue would offer an undo for something no code can reverse, so
`ShReviewService` answers *"permanently deleted, there is nothing left to restore"* rather than a
generic refusal. The compiler enforced that decision: adding a value to the action enum broke three
switches, which is the closed set doing its job.

The row is written **before the rows are gone**, so the title and site come off the entity while it
still exists. SH88 already carries an entry whose object has vanished, it travels with its title
and a `null` address, which is exactly the shape this needs.

`purge` now takes the actor the endpoints already had; a cascade passes `null` honestly rather than
attributing the destruction to whoever happened to be nearest.

### The overload lint was right and I was wrong twice

Threading the actor produced a `purge(id)` / `purge(id, actor)` pair. The first attempt kept the old
signature delegating to the new one, and **a delegation between overloads is a self-invocation that
never crosses the proxy**, so the native purge ran with no transaction and said so
(`No active transaction for update or delete query`). Annotating both was the second wrong answer:
`ShAgentTransactionalOverloadLintTest` refuses the *shape*, not the annotation, and its message says
to collapse the pair. Collapsing is what shipped, and two callers learned one argument.

*(Unrelated, and found while shipping this: `roadkeep lint` reports twelve pre-existing
`body.promise` problems in `IMPROVEMENTS.md` §0.x, twelve with and without this change, so a
roadkeep upgrade surfaced them rather than any commit causing them. Filed as SH649.)*

## A trashed post still holds its friendly URL (SH243)

`ShPostUnified` carries `@SQLRestriction("deleted_date is null")`, which makes the SH55 trash
invisible to every normal read. The cost was that every *"is this URL free"* question was answered
**only about live posts**, and the failure is three ordinary gestures long: clone a page
(`home` → `home-2`), delete the copy, clone again, the second copy takes `home-2`, because nothing
the query can see is holding it, then untrash the first. The instance now has the `duplicate-url`
error SH202 was filed to remove, produced by two individually correct operations.

**Free and occupied are not each other's negation.** `ShFurlNaming` answers both, and they
deliberately disagree about a trashed post:

| Question | Method | Sees the trash? |
|---|---|---|
| "what may I name this?", copy, create | `isFree` | **yes** (`countTrashedByFurl`, native) |
| "must I refuse this?", move, restore | `liveOccupant` | **no** |

Deriving a name has to skip a URL that is only sleeping. A refusal must not name one, because a
refusal points the curator at an occupant, and pointing them at a row no listing shows is a dead end
rather than an instruction (P6). So the trash makes a URL *unusable for a new name* without making it
*occupied*, and the gap that leaves, a URL a trashed post will want back, is closed on the other
side: **`restoreFromTrash` refuses** with `ShRestoreUrlConflictException` (409 `restore-url-conflict`).

Refuse rather than renumber, because a restore asks for *the thing that was there*. A copy has no
claim on any particular address and takes `-2` happily; a restore's caller is holding the link, the
sitemap entry and the search result that name the URL, so handing it back elsewhere is the surprise
P7 exists to prevent. The 409 names the occupant and the address in both branches, including when
the occupant has no title, which is ordinary (an uploaded file never gets one, SH38) and is the case
where the address is the only handle there is.

### One occupied URL refuses the whole section

`ShFolderWriteService.restore` cascades that call, so a collision refuses the entire subtree, and it
is checked **before the first row is written** rather than left to the rollback. Restoring 49 of 50
posts and reporting the 50th is a partial success the caller has to read carefully to notice, and a
curator who asked for their section and got most of it is the reader P7 is written for.

### The refusal has to be *askable*, not only throwable

`ShReviewService.revert` collects a reason per entry it could not undo, and it cannot get that reason
by catching this one: a throw out of an inner `@Transactional` call leaves the shared transaction
**rollback-only**, so the graceful "skipped" row would surface as an `UnexpectedRollbackException` at
commit, a 500 in place of the single line the queue exists to show. Hence the pair,
`requireRestorableUrl` (throws) beside `restoreUrlConflict` (reports), on both write services. The
agent path's `dryRun` asks the throwing one, the way `folderMove` refuses an occupied target before
it consults `session.dryRun`: a plan that promises a restore the write will refuse is the same defect
as a plan that promises one for a post nobody trashed.

### Two instruments were wrong about this, and both were fixed here

- **`ShFixtureShapeGuard`** counted trashed rows, so it reported "two ids on one URL" for a state
  SH243 makes legal and *defends*. Narrowed to `deleted_date is null`, its subject is two posts the
  resolver cannot tell apart, and only one of these two is reachable. Verified by injection: with the
  refusal removed it still reports `/careers`, `/contact` and `/pricing` held by two **live** ids.
- **`ShFolderWriteAuditLintTest`** scanned every occurrence of the string `@Transactional`, including
  prose. The first javadoc here to write it in a sentence about propagation, the subject a method in
  that service has most reason to explain, was read as an annotation, and the lint failed naming a
  method called `"@Transactional"`. It now skips comment lines, which
  `ShAgentTransactionalOverloadLintTest` had already paid for the lesson on.

## A 404 from `post-unified/{id}` says which of the two it is (SH921)

`GET /api/v2/post-unified/{id}` reads `findByIdAndState`. A post published **without ever being
drafted** therefore answers nothing for `?state=draft`, and until SH921 that was
`ResponseEntity.notFound()`: a 404 with **zero bytes**, byte for byte identical to an id nobody
has ever heard of. The caller was left choosing between a wrong id, a wrong state and a deleted
row; the console guessed too, and rendered an empty form behind two failed requests.

Two slugs now, because they are two different problems:

| Slug | When | What it carries |
|---|---|---|
| `post-state-not-found` | the id exists in other states | `allowed` (the states it has), `didYouMean`, a `fix` naming the working URL and `/versions` |
| `no-such-post` | nothing anywhere carries the id | a `fix` pointing at `/agent/resolve`, and at `/staticfile/{id}` and `/folder/{id}` for the other kinds |

**It reports the states rather than falling back to one.** `ShConsolePostReadService.written`
*does* fall back to the sibling state, correctly, there the caller has just written the row and
wants whatever it became. Here the caller **named** a state, and answering with a different one
is a lie no client can detect: an editor would show published content in a draft form and save it
back as an edit nobody made.

### The filed diagnosis was wrong, which is worth more than the fix

SH921 was filed from a browser-check failure on a static file, concluding "a static file is not a
post-unified row and should not pretend to be one". Asked as `?state=published` **the very same id
answers 200**. It *is* a post-unified row; it simply has no draft. The defect is general, any
published-only post, and static files are merely the population where it is always true.

A fix built on the filed premise would have special-cased post-type `File` and left every
imported or directly-published post with the same silent 404. One `curl` with a different query
parameter is what separated those two outcomes; the section had been written from the first
observation rather than the second.

`ShPostStateNotFoundTest` asserts at the boundary the defect crossed, the response body, not the
service's `Optional`, and was verified by reverting the controller to the bare 404: two of its
three assertions fail, and the `published` control stays green.

## The primary key is the server's, and one endpoint let a client choose it (SH924)

`POST /api/v2/group` took the id from its request body and persisted it verbatim. A blank one,
which is what a create form sends when its type makes the field required and the server has not
minted anything yet, stored a row that **nothing can address**: `/v2/group/` does not match
`/v2/group/{id}` so it does not route, every id-shaped path is a 404, and the group is still listed
by `GET /v2/group`. Read 200, delete 404, still there afterwards. It came out with a database
statement.

### The sweep said one, where the report predicted many

SH924 was filed on that endpoint and expected a class: "every `POST /api/v2/{entity}` taking a full
entity shape has the same hole." Measured across all **23** POST handlers with a `@RequestBody`,
**one** was affected. The other twenty-two take a purpose-built `*Request` / `*Payload` / `*Def`
record with no id component, so the key is the server's by construction and there is nothing to
send. `ShGroupDTO` was the last entity-shaped create body in the tree.

The count being one is the argument *for* the lint, not against it.
[`ShCreateBodyKeyLintTest`](../../shio-app/src/test/java/com/viglet/shio/api/ShCreateBodyKeyLintTest.java)
holds it: a POST body type carrying a settable `String id` is a finding to explain, with the
assertion that covers it named, one row per type. The next `@RequestBody ShThingDTO` compiles,
answers 200, and only the client that sent it can tell.

It asks a **structural** question and says so. Whether a handler then honours the id is data flow no
regex reads honestly, and a lint pretending otherwise is one that goes quiet on a rename, so
behaviour is asserted against a running context in `ShGroupCreateIdTest`.

### Mint, don't refuse: and the second door

A teaching 400 under P6 would refuse exactly the shape the console sent until SH904, and the shape
any client written against it still sends: a silent defect becomes a loud regression for callers who
did nothing wrong. Minting makes those requests correct instead, and there was never anything to
teach, the id was always the server's to assign. A **non-blank** id is still honoured, which is
what keeps every import path working.

The sweep also found the door nobody reported. `shGroupUpdate` read its id from the **body** and
ignored its own `{id}` path variable, so `PUT /v2/group/A` carrying `{"id": "B"}` updated **B**,
and a blank body id stranded a row from an endpoint that looks like an update. The resource a
request addresses is the one its URL names; taking the id from the path closes both.

Verified by reverting the controller: two of the three assertions fail, and the "a key the caller
chose is still honoured" control stays green in both directions.

## An endpoint that answers 200 and does nothing (SH954)

`ShObjectAPI.clearCache` was a `@GetMapping("/{id}/clear-cache")` whose entire body was
`return ResponseEntity.ok().build();`. Its own javadoc explained why: the page-cache subsystem it
drove was no longer part of the backend, and the no-op was *"kept for the console's clear cache
action (it expects a 200)"*. The content browser rendered a **Clear Cache** button on every row,
called it, and toasted "Cache cleared", so the product told a curator it had done something, every
time, for as long as the cache had been gone. **The comment saying so was in the file the whole
time**, which is the argument against prose as a guard.

Deleted rather than reimplemented, endpoint and button and i18n keys together, plus the test whose
subject was that the no-op returned 200, a test holding a lie in place goes with it. Cache
invalidation here is the CDA's `Cache-Control` headers and the image-transform cache, both
invalidated by publishing rather than by a row button. If an operator ever needs to purge derived
images, that is a named verb with a real effect and an agent equivalent (P1), and it is a line of
its own.

**`ShNoOpMappingLintTest` is what generalises past one button.** It walks every request mapping and
fails on a body that is exactly one bare success, `ok().build()`, `noContent().build()`, or a bare
`return;`. A body that returns `ok(something)` computed the something and is out of scope, so the
rule cannot be argued wider.

Its `EXPECTED` list holds one entry, and that entry is the distinction worth keeping: `ShMcpAPI.end`
answers 204 to `DELETE` because MCP requires a session-end verb and there is nothing to free, a
session id is an attribution label, not server-side state. **That is an answer; `clear-cache` was a
placeholder for one.** An addition to the list has to carry a sentence of that kind, in a diff
somebody reads.

## One concurrency contract, honoured by both writers (SH985)

SH721 gave the agent `expect`: a write carrying the digest it was planned against is refused when
somebody wrote in between. Its own reasoning was about a curator, *a curator patching one field
while an agent runs `post.upsert` gets 200, the agent gets `changed=1`, and the curator's field is
gone*, and it fixed one direction. The console went on sending `PUT` and `PATCH` with no version at
all, so the same collision with the roles swapped still ended with the agent's write silently
discarded. That is the worse direction: the agent is the writer with a review queue and an audit
row, and neither recorded that anything was lost.

**`GET /api/v2/post-unified/{id}` now answers with an `ETag`**, and `PUT`, `PATCH`,
`POST /{id}/publish` and `DELETE` honour `If-Match`. The tag is `ShAgentPostDigest` in HTTP clothing,
the same value `expect` compares, so the two writers ask one question about one row rather than
each keeping a version of their own, and an agent's digest can be handed to a console write.

Three decisions worth knowing before you touch this:

- **Opt-in on the wire, always sent by the console.** An absent `If-Match` is last-write-wins,
  exactly as an absent `expect` is. A mandatory header would refuse every caller written before it,
  and the guarantee is the console holding the tag and sending it, which is where the defect was.
- **The subject is the row the write changes**, so a publish matches against the *draft* (SH730's
  rule, and the agent's, matching the live row would refuse the case publishing exists for). A
  delete names both rows and **any** of them may match: a caller holding the live row's tag for a
  post that also has a draft has still proved it read the post as it is now.
- **The write answers with the new tag.** Without that, the console would save once and be refused
  on its next save by the write it made itself, a guard that fires on its own writer is worse than
  none, because the way out a caller finds is to stop sending the header.

The refusal is the same problem type as the agent's, `stale-expectation`, with the instruction the
console caller needs: read again, compare, and send a write composed from what is there now. It does
**not** say *resend with the new tag*, that is the overwrite the guard was asked to prevent, in two
steps. The editor answers a 409 by re-reading the draft and opening the comparison: one row per
field that differs, what is stored beside what is in the form, defaulting to **what is stored**,
because the guard exists to stop a save silently discarding the other writer.

`ShVersionGuardLintTest` is the instrument, and it landed first (SH322). It classifies every
mutating mapping in the four content controllers as guarded or unguarded-with-a-reason, and a write
that gains a guard fails until its row moves. Its first draft read the parameter list with
`\(([^)]*)\)` and reported **one** write in `ShStaticFileAPI` where there are three, a parameter
annotated `@RequestParam("file")` closes that group early, so the scan counts parentheses instead.
Under-reporting is the one way a lint like this goes wrong: it is green, and it is not looking.

## The console's publish, with the agent's safeguards (SH996)

`POST /api/v2/post-unified/{id}/publish?dryRun=true` answers **`ShAgentChangeSet`**, the shape the
batch surface returns for a dry run, and writes nothing. One contract, two callers: the console and
the agent ask the same question about the same act, and a console-shaped preview would be a second
description of publishing to keep in step, and the day they disagreed the curator's surface would be
the one that was wrong. It reports the batch path's three outcomes for its reasons: no
draft is a noop, a draft matching what is live is a noop (re-publishing unchanged content must not
claim to have published), everything else is a publish, and a first publish says so. It carries no
routes, those are `include=impact` on the read, and computing them twice is the P5 split.

`requirePublish` is asked on the **dry run as well**: a plan a caller may not carry out is a plan that
misleads.

**A real console publish is recorded under `console:<curator>:<date>`**, which is what puts it in the
review queue. Until now only an agent's work was reviewable and revertable; a curator who published the
wrong draft had the same need and no screen for it, which made "the agent's work is reviewable and mine
is not" a property of the product rather than a decision. The day is the unit, one session per press
would make the queue a log, which the Activity page already is, and one per curator would never close,
and it is derived rather than stored, so two publishes an hour apart join without anything recording
that they belong together. The context is cleared in a `finally`: the thread is pooled, and a session
left bound would stamp the next request's audit rows with this curator's day.

## `/api/v2/history` carries the three facts it recorded and never returned (SH999)

The Activity read gains **`agent`**, **`agentName`** and **`subjectKind`**. The last of those has
been on every row since SH196 and no reader carried it, so the console called a folder rename a
post. `agent` and `actor` filter the read, and they are **not the same filter**, which is the whole
point: an agent acts *as* somebody, so the credential is the same on a run's rows and on that
person's own, and a page that offers only one of the two cannot answer "was that us or the agent".

## One reader over the audit table, for both surfaces (SH1005)

SH999's filters were applied *after* the query, which was defensible only while this read was "the
newest N, again". It is not any more, and the reason it is not is the defect SH1005 closed: every
console read of this trail was **page zero**, capped at five hundred, with no cursor and no filter,
over its own finders, while `/agent/changes` read the same rows with a keyset cursor, a site scope,
a commit-order ceiling and a measured budget. The five-hundredth row was the last one the product
could show a person, on the one table that only grows. That is P1 working and P10 not applied, and
it is the shape a console-only surface takes when nobody names it: the weaker of the two readers is
the one the curator uses.

Both surfaces now go through `ShAgentChangesService`. The agent walks the trail forward from a
cursor and pays a token budget; the console walks it back from the newest row, because a person
scrolls rather than resumes. The query set, the scope and the cursor grammar are one, so the two
cannot disagree about what happened or about where a position is.

| Parameter | What it narrows |
|---|---|
| `before` | the page behind this one, a `cursor` from the previous page |
| `since` | read forward instead: what has been written since the page was drawn |
| `agent` | `agent`, `curator`, or one run's session id (SH999) |
| `actor` | one principal's rows, an agent acts *as* somebody, so this is a different question |
| `action` | one transition kind, e.g. `PUBLISH` |
| `subject` | `post`, `folder` or `site` (SH196) |
| `site` | one site, by name or id |

A page carries `rows`, `latest` (pass it back as `since=`), `cursor` (pass it back as `before=`) and
`more`, which is read from one row beyond the page rather than inferred from a full one.

Two things follow, and neither is housekeeping. **`/api/v2/history/site/{id}` is gone**: a site is
one narrowing among six, and a path per narrowing is how one reader becomes a query set.
`/object/{id}` stays, because that one is not a narrowing, it is the post editor's own history
panel asking about the object the curator is looking at. And **`action`, `actor` and `subject` land
on `/agent/changes` too**: a filter only the console has is the console-only capability P1 refuses,
and they cost a query parameter each rather than schema tokens on the payload an MCP session pays
for on every turn.

`ShHistoryPageableRepository` is gone with them. Two repositories over one table is how a second
reader gets written without anybody deciding to write one, and `ShHistoryTrailTest` now fails the
build if a second file appears in that package, or if the controller reaches for a repository at
all. It asserts the paging by **tiling**, the pages a reader walks, concatenated, must equal the
trail exactly, and the narrowing by **page fullness**, because a filter applied after the query
returns the right kind of row and the wrong number of them.

## Who else is on this page: awareness, not co-editing (SH1008)

SH985 makes a conflicting save fail loudly instead of silently. That is necessary and it arrives
**late**: the curator finds out after writing the paragraph, and on a Shio page the other writer is
usually a machine. What both parties need is not to type into one document, which the non-goals rule
out, but to know the other is here.

A **working mark** is derived from the same audit rows: a run that wrote to an object inside the last
minute is working on it. `GET /api/v2/history/working/{objectId}` is the console's read of it, and
`/agent/read?include=working` attaches it to a read, absent unless it is true, so a page nobody else
is on reads exactly as it always did. It carries `session`, `agentName`, the run's `intent` (SH1000),
`at` and `until`.

Three properties, and each is a decision rather than a detail:

- **Derived, not declared.** Nothing takes a mark and nothing releases one. A declared presence
  outlives the session that declared it exactly when the session died badly, which is the case it
  would have been added for.
- **`until` is carried.** A mark's end has no event (a run that stops writing sends nothing) so the
  console clears the line on its own timer. SH967's stream only makes it re-ask.
- **It is not a lock, and that is held by a test.** No write path may read `ShWorkingService`:
  `ShWorkingMarkTest` names the three files allowed to, and a save while a mark is showing goes
  through on exactly SH985's terms. Presence derived from a timestamp and consulted by a write would
  be a lock held by whoever wrote last and released by nobody.

The window is one minute, as a constant rather than a property: shorter and an agent that thinks
between two writes disappears from a page it is still on; longer and the line goes on saying somebody
is here after they have gone, which is what teaches a reader to ignore it. A value an operator can
tune is a value two instances answer differently, and this one is a claim about what the reader is
looking at.

## Blocks, delivered (SH1016)

A `Blocks` field reaches every tier a consumer reads, and it reaches them **as blocks**.

**CDA.** `attrs` already carried the array (it passes stored JSON through verbatim) so what was
missing was the other half: `rendered`, a map of field name to HTML, beside `attrs` and never
instead of it. A site that maps block types to its own components reads the array; one that has been
dropping a body into a div for two years reads the HTML. Absent (`NON_NULL`) for a post with no block
field, so every post written before the widget serializes exactly as it did.

Rendered per request rather than stored at write, which is a departure from the design and was
argued rather than assumed. Storing derived HTML needs a home and both available ones are wrong: a
sibling key in `data` is an undeclared field the next write refuses (SH168), and a column is a schema
change for a pure function of a value already there. The measured cost is in `ShBlocksTest`, printed
on every run: **blocks + HTML is about 2.5× the HTML alone**, bounded at 3×. That bound is the real
risk the design was pointing at, the body goes out twice.

**HTML is escaped, and every tag comes from the model.** A paragraph is a string an author typed;
the marks say where the emphasis is. So a `<script>` pasted into a heading renders as the characters
typed. That is the property `innerHTML` could not have, and it is why SH956's sanitizer is needed for
an HTML body and not for this one. Escaping happens **per run between mark boundaries**, escaping
first moves every offset the marks are expressed in, and escaping last cannot tell the tags just
inserted from an angle bracket the author typed. A test caught exactly that second bug.

**GraphQL.** A blocks field is typed `[ShBlock]`, a union over the eight registered kinds, narrowed
with an inline fragment. A JSON scalar was the cheaper option and it is a string with a promise: a
consumer would still parse it to find out whether it had a quote or an image. The vocabulary is
declared **whatever content exists**, building it only when some post-type carries a blocks field
would make the published schema depend on what happens to be stored, and a schema that changes shape
between two instances is not a contract a client can be generated from.

**`@viglet/shio-client`** exports `ShioBlock` as a discriminated union, `switch (block.type)`
narrows with no cast, plus `ShioBlockMark`, `SHIO_BLOCK_TYPES` and `shioBlocks(post, field)`, which
answers `null` for a field that is not blocks rather than making a consumer cast and find out by
rendering nothing.

**`@viglet/shio-react-sdk`** gains `<ShioBlocks blocks components fallbackHtml>`, which maps block
types onto components the site owns. The fallback is **per block, not per document**: a page with
one unhandled type should render the seven it knows, and falling back wholesale would make adding a
block type to the model blank a page that was working, the failure a fallback exists to prevent,
arriving by the fallback. An unclaimed block renders a readable default from its own fields rather
than nothing, because "I wrote no component for `embed`" must not mean "the embed is gone".

## Bulk verbs, which are the agent's verbs (SH1021)

`shio_publish` has taken an array of addresses and promised atomicity since it shipped. The
console's batch menu was copy, move and delete; REST had single-id publish. So the agent half
shipped and the curator half did not, and the obvious fix, a publish loop in a console service, is
exactly what lets two surfaces drift about what publishing means.

`POST /api/v2/post-unified/bulk` takes `{op, ids, dryRun, confirm, when}` and **builds ops for
`ShAgentBatchService`**, the writer the agent's batch runs through. One transaction, one
vocabulary, one set of gates. `ShPostUnifiedBulkServiceTest` asserts the delegation rather than the
behaviour, because a forked loop that happened to publish correctly would pass a behavioural test.

**The answer is the change set, never a bare 200.** An outcome and a problem per item, which is what
the agent already returned: a selection where one post is refused publishes the rest and says which
one did not, carrying the fix its refusal came with. A bulk verb that reported one outcome for the
batch either hides the refusal or throws away the twelve that worked.

**`dryRun` defaults to true.** A bulk verb is the one shape where a mistake is multiplied by the
size of the selection, so the request a caller sends by accident is a plan. The console opens that
plan as a sheet and the real run carries the token the plan returned, so a destructive run cannot
happen against a plan nobody saw (SH108).

**Permission is asked twice, deliberately.** The batch's ops check per row and that is the check
that makes the surfaces agree; the guard before them is about what the caller *sees*, without it a
curator with no publish right gets a change set in which every item is refused, and on a dry run a
plan they could not carry out. PUBLISH for the two verbs that change what a visitor sees, WRITE for
the one that removes content, matching the single-id endpoints.

**There is no `If-Match`.** One header carries one row's version and a selection has many; the
batch's per-op `expect` is where a caller who cares says it, row by row.

## The console listing takes the agent's filters (SH1022)

`GET /api/v2/object/{id}/list` serves the whole content-query vocabulary, `type`, `state`,
`locale`, `missingLocale` (SH1060), `modifiedSince`, `modifiedBefore`, `author`, `modifier`,
`submission` (SH1066), `agent` (SH1024) and `subtree`, beside the
`page`/`size`/`sort`/`q` it has had since SH165. `site` and `folder` are the two it refuses: the
folder is the `{id}` in the path, and a second one in the query string would be two answers to one
question. The refusal names `/agent/find` as where those do work.

**`facets=true` counts the filtered posts (SH1024).** It is the listing's own parameter, outside the
vocabulary, and opt-in: two extra reads that a caller drawing no filter bar should not pay for. The
response's `facets` holds `type` (by post-type name), `state` (`draft` for posts with a draft row,
`published` for posts that are live; a post with both counts in each) and `locale`. Both reads are
over `CONSOLE_ROWS`, the same JPQL string the page reads. So every count is a total under the
request's filters, and choosing a value lists as many posts as its count said.
`ShContentQuerySurfacesTest` asserts that per facet, with another filter held.

The words are `ShContentQuery`'s, parsed from the request's **whole parameter map** so an unknown
one is a teaching 400 rather than a silent drop, and the filters are
`ShPostUnifiedRepository.QUERY_FILTERS`: one predicate string, appended by both this listing and
`/agent/find`, for the reason SH240 gave `FULLTEXT_PREDICATE`: two surfaces that each spell
"modified since" for themselves come to disagree about whether the bound is inclusive, and nothing
fails when they do.

**Only the posts are filtered.** A child folder has no post-type, no state and no locale, so
narrowing the folder list by a post's words would empty the navigation every time a filter was set.
`q` still applies to both halves, the search box has always filtered the combined list.

**`state` is asked of the post, not of the row.** This listing answers one row per logical id and
that row is the draft, so `p.state = :state` would empty it; the predicate is
`exists (… s.id = p.id and s.state = :state)`, which selects the same *posts* `/agent/find?state=`
selects while still returning the row this listing returns. `ShContentQuerySurfacesTest` compares
the two address sets filter by filter, which is the only check that catches them drifting, both
answer `200` with a plausible list when they do.

## The folder half is paged on request, and the number that says why (SH1025)

SH165 paged the posts and left the child folders whole, with an argument written into
`ShConsoleListing`: subfolders are structure, the move/copy picker walks them, the browser sorted
them locally, and the set is "bounded by how people organise a site rather than by how much they
publish". Plausible, and never measured, the number it rests on is what a folder costs against
what a post costs.

`ShConsoleListingBenchmarkTest` took it. At **400 folders and 600 posts**, one page of the browser:

| | rows fetched | response bytes |
|---|---|---|
| folders whole | 454 | 63 108 |
| folders paged | 104 | 19 758 |

The argument holds for most folders and not for the one that is a problem, and the cost of being
wrong is the whole set re-sent on **every page of posts**. So `folderPage`/`folderSize` join the
query vocabulary and `totalFolders` joins the envelope, and absent, the listing still answers
whole, because the picker's `size=1` read is correct and is not what the finding was about.

Two consequences worth knowing. **A folder page is ordered by the database**
(`ShConsoleListingSort.folderSort()`, the same axis the posts use, translated onto `name`/`date`/
`position` and always tie-broken by name): the browser applied its three sort options to an array
it held whole, which was correct exactly because it held it whole, and sorting a page client-side
sorts one page. And **the envelope is now a superset of the CDA's rather than equal to it**, the
CDA's six components first, `totalFolders` after, which is the rule the rows have always followed.
Adding the field to the CDA envelope to keep them identical would put something in the public
contract to satisfy a test; `ShConsoleShapeOverlapTest` asserts the prefix and pins the tail.

Milliseconds are printed by the benchmark and never asserted: a timing ceiling on a shared machine
is a flake, and a flake is worse than a break (SH250).

## Console search reads the agent's find, not the delivery API (SH1027)

`GET /api/v2/content/search` is the console's content search: the SH1022 vocabulary, `state`
absent meaning **both**, and the `trash` flag its REST twin has. It is `ShAgentReadService.find`
with a per-object permission filter over it (a shape, not a third search (P10)) because a second
implementation of "what matches" is two that come to disagree, the way the console's own filter
and the delivery search once did (SH240).

**What it replaced was wrong three ways, and none of them failed loudly.** The search page called
`GET /api/v2/cda/search`, whose state is `PUBLISHED` unless the caller holds a preview token and
the console has never sent one, so an agent could find the drafts it had just written and the
curator reviewing them could not, which is backwards: searching for what a run made is the first
thing a curator does after it. The CDA enforces a *token's* site allow-list and knows nothing
about `sh_content_acl`, so on an instance with an ACL set the console listed titles from folders
the reader could not open, and they found that out by clicking one. And its folder scope was
direct children while the agent's is recursive, so one word asked two questions.

**The answer carries `withheld`.** A filtered listing that says nothing is indistinguishable from
a search that found less, and the reader's next move is to conclude the content is not there,
which sends them to write the page again rather than to ask somebody for access. The filter runs
after the cap rather than as a predicate, which is why a page can come back short and why the
count is not optional: the ACL lives in a table keyed by object with inheritance up the folder
chain, and pushing it into the query would make this file the third thing it is not allowed to be.

**The census landed first (SH322).** `ShConsoleReadAclLintTest` derives every `/v2/…` path the
React console GETs out of `shio-react/src`, keeps the content ones, and holds each as ACL-checked
or declared with a reason. It reads **one hop** of delegation, the mapping's own body, or the one
service it hands off to, because a controller that delegates the whole read is the shape half of
this codebase has, and a body-only reader would report those as unchecked and teach the wrong fix.
It prints its counts on every run: 31 console content reads, 13 of which ask the content ACL. The
other 18 are decisions with reasons beside them, and an entry that stops being true fails the
build rather than lingering.

## The media listing parses each asset once (SH1029)

`toItem` read eight keys off a `File` post's `data`, `FILE`, `WIDTH`, `HEIGHT`, `ALT`, `CAPTION`,
`TAGS`, and `SHA256`/`SIZE` through the digest stamp, and every one of the typed readers began by
parsing the whole string again. Nothing about that shows up in a stack trace or a profile of one
request: it is a constant factor on a loop, which is exactly what an argument gets wrong.

`ShMediaListingBenchmarkTest` counted it, with an `ObjectMapper` subclass that reports how often it
was asked to parse, deterministic, unlike a clock (SH264), and the reason no timing is asserted
anywhere in that package. At **400 assets: 2400 parses before, 400 after**, and the response
**138 691 bytes either way**, which is the other half of the claim. A refactor that changed the
answer would be a different task.

Six per row rather than eight because the benchmark's rows have no bytes on disk, so `trustedStamp`
returns before its two parses. On a real library it is eight, so the reported ratio is the
conservative one.

`Data` is a value, not a cache: parsed at the top of the one method that needs the whole row and
then thrown away. A memo keyed on the post would be a second thing that can be stale, and
staleness here is invisible, a listing answering from a cache written before a metadata edit shows
the old alt text and nothing looks wrong.

**The stat per row stays, and that is not an oversight.** The design asked for the size to come
from the stamp instead of the disk; SH398 already argued the opposite and recorded why. The
existence check has to happen anyway to keep SH62's `size: -1` honest, so a stamp would remove one
stat of two and buy a `data` key that can disagree with the disk. SH399 stamps the size to validate
the **digest** against it, deliberately not as the size.

Five typed readers went with the change, which took `ShSilentCatchInventoryTest`'s `api` count
**down** from 74 to 72, each carried its own `catch` around the same parse, and a silent catch
nobody calls is a number the next task has to explain.

## The media listing is a page, with the counts a filter bar needs (SH1030)

`GET /api/v2/staticfile` answers `{ items, page, size, totalFiles, facets }`. `page` and `size`
under the object listing's own names and in its own order (SH165), because this was the one content
read nobody had paged and a third spelling of "which page is this" is the drift SH120 spent a task
removing.

**The facets are the server's, and that is the point rather than a convenience.** The console drew
its category chips by counting the array it had, which worked exactly because it had all of it.
Over a page those counts describe the page, a library of two thousand images reports fifty, the
chip a curator presses to find the rest is the one telling them there is nothing to find, and every
number on screen agrees with every other. That is the half nobody notices breaking, which is why
`ShStaticFilePagingTest` asserts it.

They are counted **over the filtered set minus the category itself**: "document 7" while `image` is
selected is the answer to "what else is there", and counting inside the selection answers "30, 0".
A `TreeMap`, not `Map.copyOf`, an immutable map iterates in hash order and this one is serialised,
which `ShByteStabilityLintTest` refuses (P5).

**What this does not do, stated rather than left to be found.** The *response* is a page; the
*read* is still the folder's rows, narrowed by the predicate SH375 moved into the query and then
filtered here on category and name. Pushing those two down needs the content type stamped at
upload, which is a change to what is written rather than to what is read. Shipping the envelope
first is what lets every surface stop assuming one response while that lands.

**The content type is stamped at upload (SH1030).** `data.CONTENT_TYPE`, written beside the digest
and from the same file, and declared by the `File` post type like every other key the uploader
writes, `ShFilePostTypeSourceTest` fails the build for one that is not, because an undeclared key
makes SH168 report every asset as a broken document.

**The probe stays as the fallback, and not only for old rows.** The stamp answers the *category*,
which is a filter; the probe answers what the file *is*, which is what a download sends. A
stamp-only reader would classify a whole pre-existing library as `other` and every chip would agree
with every other while all of them were wrong, which is why `ShStaticFilePagingTest` asserts the
fallback against a fixture written without stamps.

**The predicates narrow the read, as a conservative superset (SH1030).** `findAssets` takes the
term and a content-type prefix, and the exact decision stays in the service. That is what makes an
approximate predicate safe rather than a second definition of "matches": it can only ever return
*more* rows than the answer, so a mistake costs a row read and discarded, never a row missing from
what a curator sees. Measured, a `q` listing over 400 assets reads **111 rows** where it read 400.

Two approximations, each with its reason. The term is matched against the whole `data` document
while the filter matches file *names*, so an asset whose alt text contains the word comes back and
is dropped. And the category keeps a row whose stamped type matches **or that carries no stamp at
all**, a predicate that dropped unstamped rows would make a pre-existing library vanish from every
category, silently, with every number on screen agreeing.

Only `image`, `video` and `audio` are prefixes. `document`, `archive` and `other` are lists and
exclusions, and a predicate for those would be `categoryOf` written twice in two languages,
agreeing until somebody edits one. Those read every row and filter in the service, which is what
they did before.

## A validator on the console's own reads (SH1048)

SH3 gave the delivery API a body-hash `ETag` and a `Cache-Control`; SH116 gave the agent surface
conditional reads of its own. Everything the **console** calls, `/api/v2/object/…/list`,
`/api/v2/post-unified`, `/api/v2/post-type`, `/api/v2/site`, `/api/v2/staticfile`, `/api/v2/widget`,
answered a full `200` with no validator and no freshness hint. A curator walking a tree and
coming back paid for every byte again, and so did the CLI and MCP, which call the same REST.

`ShConsoleCacheFilter` is one `ShallowEtagHeaderFilter` over `/api/v2/*`, and it is one class rather
than two because the `Cache-Control` has to be stamped *before* the body is written to survive the
`304` the same filter produces, two registrations with two patterns is two chances to exclude the
streaming endpoint from one of them.

**`no-cache` is not `no-store`.** It means revalidate: the browser may keep the body and must ask
before reusing it. That is the whole saving, the second read is a conditional request that comes
back empty, not a request nobody makes. It is `private`, because a console read depends on the
caller's rights and their tenant and a shared proxy must not hold it at all.

**Four exclusions, and the first two are correctness rather than economy.** The filter buffers the
whole response in memory to hash it, so `/api/v2/events` would become Server-Sent Events that never
reach the browser, and `…/export` would put however many megabytes the zip weighs on the heap for a
validator nobody asks about twice. The other two are `/api/v2/cda/` and `/api/v2/agent/`, which
already have their own, hashing a delivery body twice is the cost, and a *shallow* body hash
landing over SH116's **semantic** validator is the defect.

It is registered at `LOWEST_PRECEDENCE - 20`, which is two claims held by an assertion rather than a
comment: **after** the Spring Security chain (`-100`), so a `401`, `403` or `429` never receives an
`ETag`: a validator on a refusal is a client that caches the refusal, and **inside** SH124's cost
header (`-30`), which has to see a conditional `304` as the zero-byte response it really is.

**The measurement landed with the fix, not after it**, and it is the part worth keeping. A 304 saves
whatever the body costs, and nothing here knew what a console body costs. P3 makes a token
regression a build failure on the agent's half; `console-response-budgets.properties` is the same
rule on the half a *person* waits for, in the unit a person pays. Eight reads against a fixed
fixture, so a breach means the shape got bigger rather than the database did, with a headroom check
for SH736's reason, a ceiling at its measurement refuses the next field instead of pricing it. The
widget catalogue is the largest at 2,165 bytes and the only one a new widget legitimately grows.

## What is going on here, in one call (SH1050)

The console's home fired four requests on mount and used one property of two of them: every site and
every post type, transferred whole, for their **length**. There was no count endpoint, so the only
way to ask "how many" was to fetch everything and measure it, on the first screen a curator sees
after logging in, with the tiles blank until both collections landed. An instance with four hundred
sites paid four hundred rows to draw the number 400.

`GET /api/v2/console/summary` answers it with count queries: `count()` on the repositories, the
review queue's own `pending()`, and two new counts, open notes, and the publishes and unpublishes
falling due in the next seven days. It carries the recent trail rows too, so home makes **one**
request instead of four. It composes services that already exist and adds no new way to ask
anything, which is P10 and is what lets a second reader exist without a second implementation.

It sits under `/api/v2`, so SH1048's filter gives it an `ETag` and a `no-cache`: a dashboard left
open on a second screen revalidates into a 304. The endpoint was written after that filter for
exactly that reason.

**The window counts things, not moments.** A post scheduled to go up *and* come down inside the week
counts once, the honest answer to "how many things happen this week" for a tile, and the one a
naive `count(publishAt) + count(unpublishAt)` gets wrong. The agenda page splits them into two
moments because that is what an agenda is for; this is not the agenda. Trashed posts never count.
Both edges are driven in `ShConsoleSummaryAPITest`, including a window entirely in the past, which
is what shows the bound is being applied at all.

**Home is an attention queue now.** "How much content exists" is a number nobody acts on; what a
curator opens the console to find out is what needs *them*. Three tiles, a review queue waiting on
a decision, publishes due this week, notes left open, each linking to the page that acts on it, and
**a tile with nothing in it is not drawn**. A row of zeroes trains its reader to stop looking, and
the point of that section is that a number on it means something is owed.

**`shio status` prints the same rows off the same endpoint**, which is the claim worth keeping. An
agent arriving mid-project and a curator opening the dashboard are asking the same question, and two
implementations of it drift within a release, the one nobody looks at drifting first. `shio
context` says what you *can* do here; this says what is *going on*.

That difference is also why the read is declared in the manifest's endpoint index rather than
classified console-only. Classifying it that way would have been false: an agent orienting itself
wants exactly this, and composing it from the review surface, the scheduled listing, the note list
and the change feed is four calls for one question, P2 by name. It cost 68 tokens on a document
read every session, `response.manifest` was raised with that argument, and `indexIsCuratedNotTheWholeApi`
went from 29 to 30 rows having first asked what it supersedes: not another row, a *sequence*.

### The rest of it: the pack, the runs, the incidents (SH1050)

**`include=status` on the context pack**, and it is opt-in for `usage`'s reason rather than
`releases`': the section changes constantly. A review queue moves when anybody decides anything and
the scheduled count moves as the week passes, so letting it into the default pack would put a number
that changes on every publish inside the document SH116's conditional read exists to keep matching.
That is also the honest answer to the design's own escape clause, *dropped if it regresses the
cheapest read*, because a caller who does not ask pays nothing and `response.context.terse` did not
move. It carries the counts and the attention half, not the trail: what an agent does with this is
decide whether to look somewhere before it writes, and the trail is `/agent/changes` with a cursor.

The pack's site count is the **readable** one, and the endpoint's is the instance total. A pack is
always the caller's view (a credential is a site allow-list) where a console session is a person
with an ACL. Two numbers that are allowed to differ, for a reason stated in both places.

`ShConsoleSummary.SOON` lives on the record rather than the controller so both readers share the
window. Two definitions of "soon" is a tile and a pack disagreeing about what is due, which is the
kind of thing nobody notices and nobody can explain.

**Home names what the agent did.** A list, not a count, the count is already on the review tile,
and what a curator needs before opening a diff is the **intent**: a session id says an agent was
here, the sentence it declared says what it was trying to do. Each row links into the review queue,
which is the "review action" the design asks for. Sessions with nothing pending are drawn too: *the
agent ran and you have dealt with it* is a different fact from *the agent has not run*, and a list
showing only the first would make a quiet instance and a busy one look the same.

**Incidents are a window, and the sentence says so.** The log is in-memory and starts empty at boot,
so a bare count would read as a total, `0` meaning "nothing ever" and `0` meaning "nothing yet" are
different answers about an instance somebody may have restarted. The tile is drawn only when it is
not zero; `shio status` prints *since this process started*, and reports `unpersisted` beside it,
because if the database was the thing failing then the record of the failure is exactly what could
not be written down.

## A site declares the locales it is in (SH1056)

`ShSite.locales` holds `{"default":…,"enabled":[…],"fallback":{…}}`, written and read through
`ShSiteLocales` and resolved by `ShSiteLocaleService`. Before it, nothing could say what languages
a site *offers*: the render frame published an instance-wide default, verify took the union of the
locales it happened to find while scanning, and the translations menu offered all forty-five seeded
tags on every post. Three different shapes of one unanswerable question.

**One column, not three.** The three answers are only meaningful together, a default outside the
enabled set is not a default, and a fallback pointing at a locale the site does not offer is a
redirect to nothing. Three columns would each be individually valid and jointly meaningless, and
the refusal could not say which of the three is wrong.

**Nullable forever, and that is the whole migration.** An unset column reports the derived union,
which is exactly what every site reported before there was a setting, so nothing changes on upgrade
and nothing is backfilled. Writing a derived policy into every row at migration time would look
like the same thing and is not: it freezes a description of what had been typed by then into a
decision nobody made. `declared()` is what tells the two apart, and it is the distinction the
feature exists for, "offers Portuguese" means one thing as a decision and another as a description
of what has been translated so far.

**`site.upsert` fills it and never replaces it.** `locales` joined `ShSiteWriteService.FILLABLE`,
so it gets the rule `url` and `description` already have: filled when the site has none, kept and
reported when it has one (P7). It is the one fillable field that arrives as an object, so
`ShSiteLocaleService.declare` validates it and hands on the bytes. Validation runs **before** the
dry-run return, this class's own rule: a plan derived from a second reading of the rule is a plan
that can disagree with the run it predicts, so a mis-cased `pt-BR` is a teaching 400 on a plan too.

**A site names its page type the same way (SH1283).** `pageType` is the fifth `FILLABLE` field: the
post type a new page in the site starts as, stored in the nullable `ShSite.pageType` column
(`v2026.3.50.yaml`; null is today's behaviour). It must be a type the site binds to a layout, the
bindings it has plus the ones the same op brings, or the op is `page-type-not-bound` listing them, on
a plan too. The site listing carries it (the entity is unwrapped there), and the console's New button
offers it in a folder with nothing to go on, after the type last made in that folder. Still to come:
the blueprint key that lets Quilhara declare `InfoPage`, a console field, and the context pack.

**Where it costs a query, and where it does not.** `of(List<ShSite>)` is batched, because the two
callers are listings and a query per row is not what P2 promises. The **render** path uses
`declared(site)` instead (the column alone, no derive) since composing a page per request must
not add a content query to tell a template something `render.defaultLocale` already says.
`site.defaultLocale` falls back to the instance default so a comparison is never against nothing,
and `site.locales` is absent on an undeclared site rather than derived.

**In the pack, the ordinary site says nothing.** `defaultLocale` is omitted when it equals the
instance default the pack already publishes once, `locales` when there is only one, and
`localesDeclared` unless it is true (P3). `ShAgentContextTest` asserts the silence in both
encodings, which is the assertion that fails if a later row starts emitting a locale
unconditionally.

**The console's door, and the one that replaces.** `GET /api/v2/site/{id}/locales` answers the
policy the site reports (the author's view, drafts counted), `PUT` declares one over whatever it
had, the body is the document `site.upsert` takes under `data.locales`, validated by the same
`ShSiteLocaleService.declare`, so a mis-cased tag is the same teaching 400 on both doors, and
`DELETE` stops declaring, answering with what the site reports now. The write is
`ShSiteWriteService.declareLocales`, its own verb rather than a field of `update`, because the
site's `PUT` is a whole-object replace and a form that did not render the policy would null it on
every save. Replacing a decision is the curator's gesture: the upsert keeps a set policy and says
"change it in the console", and this is where that sentence leads, the same fill boundary `url`
and `description` have, recorded on `/api/v2/site`'s row in `ShStatelessReachabilityLintTest`.

The site editor's **Languages** section edits a draft inside the shell's one form (SH899) and
writes only when the draft moved, so an untouched card never turns a derived policy into a
declared copy of itself. SH53's translations menu narrows to a declared policy's enabled set, in
its order, and marks the default; an undeclared site still offers the whole catalogue and says so,
since narrowing to the languages its content already has would stop it ever gaining a second. The
rules the draft is edited by, a default that is still enabled, fallbacks whose both ends are
offered, none starting at the default, are `shio-react/src/lib/site-languages.ts`, pure so
`site-languages.test.mjs` runs them.

**What SH1056 does not do**: the design asked for a `locales` block in the **site marker file**,
there is no site marker file. The projection has content, folders, assets and a base snapshot, and
a site's own row is projected nowhere, so that half is a subsystem rather than a field, and SH1122
owns it. The CDA read shipped as SH1123, below.

## The delivered site says which languages it offers (SH1123)

SH1056's delivery half, recorded under its own id because a task carries one partial entry.

`GET /api/v2/cda/site` and `/site/{id}` carry `locales`: `{defaultLocale, enabled, fallback,
declared}`, the record `ShCdaSiteLocales`, mirrored in `@viglet/shio-client` as
`ShioSiteLocales` (`ShCdaTypeScriptMirrorTest` holds the two together). With a post's
`availableLocales` beside it, that is a whole language switcher from the delivery API alone: the
site says which languages it offers, the post says which of them this page exists in, and
`fallback` says where a missing one should land.

**`defaultLocale`, not the stored document's `default`.** `default` is a Java keyword, a record
component is what the mirror test compares, and `defaultLocale` is the name the context pack
already gives the same fact, one name per fact across the surfaces.

**An undeclared site is described by what the reader can fetch.** The author-side union counts
drafts on purpose (a site being written in Portuguese is in Portuguese the day before launch). The
delivery read cannot: a production token reads `PUBLISHED` alone, so a draft-only language would be
a switcher entry linking to a 404. `ShSiteLocaleService.of(sites, readable)` derives from the
states the token reads, through `findDistinctLocalesBySiteIdInAndStateIn`, so a preview token hears
the draft's language and a production token does not. A *declared* policy is a decision, and every
reader gets it verbatim.

**The breadcrumb's site carries none.** `ShCdaPath.site` is the same record with `locales` absent:
a path is read on every navigation, and resolving an undeclared site's languages is a content query
a front end needs once. The five-argument constructor is that shape, and the console's own `/path`
(`ShConsoleNavigationMapper`) uses it too. `ShCdaSiteLocalesTest` asserts the declared read, the
production/preview split, the batched listing and the silent breadcrumb.

## A front end that owns its URLs can read which ones moved (SH1133)

A `Redirect` post is honoured by `ShPageRenderService`, which answers `/sites/**`. A headless site
never goes through it: Next.js owns those URLs and asks the CDA for content by address, so SH1064's
op, the publish dialog's offer and `url-orphaned`'s repair all wrote a row no visitor of that site
could observe.

`GET /api/v2/cda/site/{siteId}/redirects` answers the table as `[{from, to, permanent}]`
(`ShCdaRedirect`, mirrored as `ShioRedirect`; `ShioClient.getRedirects(siteId)`), ordered by `from`
(P5). Three rules, each the one another surface already follows:

- **`from` is the post's own friendly URL** (SH385: a redirect has no source field), canonicalised
  with `ShFurlSpellings.canonical`, the CDA does not import `api.agent`, and `ShAddress.normalizePath`
  is only a delegate to it.
- **An empty `TO` is not a row**, because `redirectFor` treats that post as no redirect; `PERMANENT`
  goes through `ShPostFieldFlags.isTrue`, the coercion both writers share (`"1"`/`"0"` from the
  console, a boolean from an op). The mapper's tolerant parse means a malformed document is no row,
  never a 500.
- **The token's own states, draft-preferred for preview**, so a production front end is never told
  to redirect to a page only a draft holds; a site the token may not read is the same 404 as a
  missing one.

`ShCdaRedirectsTest` holds the order, both token kinds, the scoped 404 and the wire names.

**The consumer is a `middleware.ts`, not a page's `redirect()`.** Next's `redirect()` and
`permanentRedirect()` answer 307/308, and the table promises 301/302; only a response carries those.
A redirect's source is its own address (the batch refuses one over a live page), so consulting the
table *first* is the same as consulting it before a 404. `create-shio-app/template/lib/redirects.ts`
is plain TypeScript with no `next` import, which is what lets `create-shio-app/test/redirects.test.mjs`
import it under Node's type stripping: it matches in `ShFurlSpellings.canonical`'s spelling, holds the
table 60 s (the pages' ISR window), shares one in-flight load, and keeps the last table when the CMS is
unreachable. **It calls the endpoint with `fetch`, not `getRedirects`**: the template installs the
published SDK, which predates the method, and an instance without the endpoint answers 404, read as
an empty table. `pnpm -C create-shio-app run emit` carries the two files into all six blueprints.

**Where a repair has to reach is said, not assumed.** `url-orphaned`'s fix and `shio redirects` both
add a note for a site with a front-end URL, naming the table the front end must read. Conditional,
because that URL may be this instance's own `/sites/` address and the string cannot say which.

## A site's coverage in one read (SH1131)

`GET /api/v2/site/{id}/coverage?page=&size=` answers a **row per page and a cell per language**,
`current`, `stale` (SH1058) or `missing`, each with the address to open, plus the per-language
totals **over the whole site** rather than over the page of rows in hand, because a summary counted
over the page says "one missing" on a board showing one of forty. The columns are the site's
declared languages (SH1056), so a language it has written nothing in is a column of `missing`,
which is the backlog the read exists to show.

**Two queries whatever the site holds.** The shape tempts a lookup per group, four hundred pages,
four hundred queries, and the surface meant to make the backlog visible is the one nobody opens. So
it is one projection of the site's rows (`findCoverageRowsBySiteId`: group, id, locale, furl, title,
stamp, state, columns, never documents) and one read of the **sources'** documents to digest, taken
only for groups where some member carries a stamp: a site whose translations predate SH1058 reads no
documents at all. The totals come from the same pass as the rows, so a board cannot say seven and
show six. `missingLocale` on `/agent/find` asks the same question one language at a time, and
`ShSiteCoverageTest` holds the two answers to each other.

## A delivery listing can be asked for one language (SH1127)

SH1057's CDA half. `by-url` was the one read that took `locale`; the folder list, `/query` and
`/search` took none, so a front end rendering one language of a two-language site got every
translation beside it and had to drop half *after* paging, a page of fifty held whatever mix the
database returned and `totalPosts` counted both. All three take `locale` now, and it narrows the
**posts** only: a folder has no language, and a listing that hid subfolders because a locale was
set would empty the navigation.

It is a predicate and not a post-filter, for the count: the production reads page in the database,
through `…AndLocale` finders beside the three paged listings and `LOCALE_FILTER` appended to the six
search queries, the same `(:locale is null or p.locale = :locale)` spelling SH1022's
`QUERY_FILTERS` uses, and null is "every locale", so the entry points that take none answer as they
did. The preview reads already load their two states whole and page draft-preferred in memory, so
there the locale filters the rows before `draftPreferred` pages them. A blank `?locale=` is
"not asked", since that is what a front end sends from an unset variable. `@viglet/shio-client`
passes it on `listChildren`, `query` and, as a third argument, with options still accepted there,
`getPostByUrl`. `ShCdaLocaleFilterTest` holds all three reads on both paths.

**The client asks for a state too (SH1081).** SH990 gave every CDA read `state` (`published`, or
`draft` and `rev:<n>` with a preview token). `@viglet/shio-client` now sends it: in the options of
`getPost` and `getPostByUrl` (`ShioReadOptions`), and in the params of `listChildren` and `query`.
It is typed as a string (`ShioContentState`), not a union, so the server stays the one place that
says which values exist. A refused state throws `ShioHttpError` with `status` 403, and `body`
carries the `state-needs-preview-token` slug. `@viglet/shio-react` passes it through:
`useShioPost(id, state)` and `useShioPostByUrl(site, url, { locale, state })`. `useShioQuery` and
`useShioChildren` now refetch when `locale` or `state` changes. Before this they ignored both.

**GraphQL has the same two fields and the same argument (SH1129).** `ShContent` declares `locale`
and `availableLocales`, and `postByUrl` and `posts` take `locale`. A `posts` page reads every row's
translation group in one query (`findDistinctLocalesByTranslationGroupIdInAndStateIn`), the
single-post read goes through the same method, and that shape on a list would otherwise be a query per
row; `ShCdaAvailableLocalesBenchmarkTest` prints both and holds the page's count flat from five rows to
twenty-five.

### A switcher entry for a page nobody published (SH1128)

`availableLocales` was read from the whole translation group with **nothing in that read asking for a
state**. `translate` creates the sibling as a DRAFT, so the moment a curator started a Portuguese
translation, every production token's read of the English page said it exists in `pt-br`, and a
switcher built from that field linked to a page the same token gets a 404 for, for as long as the
translation was being written.

SH1123 had fixed the identical defect one level up (a site's derived languages come from the states the
token can see) and the post-level field kept the old rule; SH1129 then carried that rule into GraphQL
**deliberately**, so the two surfaces agreed while this was open rather than disagreeing in a way
nobody had filed. This is the pair brought onto SH1123's rule: the group's locales come from the rows
in the states the reader reads, `PUBLISHED` for a production token, both for a preview token.

**By the token, not by the request's `state=`.** The question the field answers is "which of these can
I follow a link to", and the credential decides that. A preview token looking at published rows still
sees the draft translation, which is right: it can fetch it.

**One reading of the rule, not two.** The single-post path used to load whole rows through
`findByTranslationGroupId`; it goes through the batch now, so there is one query with the state rule in
it rather than two that agree until somebody changes one, and the single read got cheaper on the way,
since the batch selects two columns where the finder loaded documents. The assertion is driven from
both surfaces, because they were two different queries before this and that is exactly how they could
have diverged: a published English page with a draft-only `pt-br` sibling reports `["en"]` to a
production token and `["en","pt-br"]` to a preview token, on the REST read and on the GraphQL page.
