# Shio — Roadmap (active backlog)

> **Single source of truth for task status.** Flat, one line per task.
> Only **unshipped** work lives here (📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress).
> Shipped work is moved to [CHANGELOG.md](CHANGELOG.md); design rationale (the
> *what/why* per task) lives in [IMPROVEMENTS.md](IMPROVEMENTS.md).
>
> **North star (see IMPROVEMENTS.md §0 and the concept spec
> [specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md)):**
> **Shio is a CMS whose primary operator is a coding agent.** The agent builds and
> maintains the site (MCP tools + content as files + blueprints); the **human
> curates** — approves, corrects, tweaks (console, Universal Editor, preview
> links); the **CDA delivers**. Every design decision is judged by *agent cycles
> and tokens*, not by clicks.
>
> Shio hosts **no model and no prompts** — it is the substrate an external agent
> drives, and the differentiator is protocol quality, not intelligence. The
> Java/Spring content engine and the headless JS/TS delivery layer are unchanged
> and still binding (§0.3, §0.4): we do **not** rewrite the backend.
>
> **The ten design laws** (IMPROVEMENTS.md §0.6) bind new work. In short:
> agent-first design order (MCP → files/CLI → REST → GUI) · one call replaces a
> session · tokens are a measured budget · files beat APIs for authoring ·
> determinism + path addressing · errors are instructions · never surprise the
> human · skipping steps needs appliable units · close the perception loop ·
> compose, don't fork.
>
> **How to pick work:** lowest-numbered task in a block whose `deps` are all
> shipped. The `→` pointer is the section in IMPROVEMENTS.md with the full design.

| Symbol | Meaning |
|---|---|
| 📋 | Designed but not started |
| 💭 | Idea worth exploring; needs design |
| ⏳ | Partial — direction is right, more work remains |
| 🛠 | In progress |

> **Numbering.** `SH<n>` ids are permanent and **not contiguous** — epics own
> sub-ranges and superseded tasks keep their numbers (never reuse them). **SH74** is
> the agent-native epic; Blocks H–N run **SH75–SH150** with deliberate gaps
> (SH85 · SH91 · SH98 · SH105 · SH111 · SH118 are reserved, unused block-header
> slots). **SH121–SH127** and **SH128–SH135** were added from findings while
> implementing Block H, **SH141–SH144** from findings while implementing Block K, and
> **SH204–SH209** from closing Block K's last four (SH102/SH104/SH141/SH144), and
> **SH210–SH215** from finishing Block I, and
> **SH145–SH150** from findings while implementing Block I, and **SH157–SH162** from
> what the SH112 benchmark measured while implementing Block M, and **SH168–SH175** from
> the first end-to-end agent-driven build over the finished surface (§VIII.12–§VIII.16,
> §IX.8, §XIII.6), and **SH176–SH182** from driving the *console* over the site that
> build produced (Block O — shipped, so its design section is gone; see CHANGELOG.md Block O),
> and **SH227–SH232** from finishing Block M (§XIII.7–§XIII.12), and **SH233–SH234** from
> reviewing what was still CSRF-exempt (both shipped, so their design sections are gone)
> — new tasks are expected to accrete this way, and each one's
> line says what measurement or failure produced it. Superseded and therefore retired: **SH17/SH18** (GraalJS), **SH23 +
> SH27–SH31** (marketplace → Block J), **SH24 + SH32–SH37** (CRUD-over-MCP → Block H).

---

# Part 1 — The agent-native reorientation (current focus)

> Blocks **H–M** implement the north star. Concept, protocol design and the full
> decomposition: [specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md).
> **SH74** is the epic (this concept) — it is complete when the three roadmap docs
> and `agents.md` carry the orientation; the code lands as SH75+.
>
> Suggested order: **H → K → I → J → O → L → M** — **all of them are now shipped**, Block L
> except SH110 (blocked on SH45), and **Block K is shipped to the last task**. Block H is the
> protocol everything else speaks; Block K removed the most expensive unit in the system, the
> human round trip, and then closed its own follow-ups — a route is provable from the delivery
> side, the incident record survives a restart, and the loop finally produces the artifact a
> human reads (`shio report`); Block O made the human half work at all, which is what made
> Block L's question askable; and Block L answered it — how a human bounds and undoes an agent.
>
> **What is left is follow-ups, not blocks**: H's original edges shipped (SH136–SH139) and
> driving them turned up the next layer (SH196–SH203, spread across H, I, L and O — the
> engine's own asymmetries rather than missing capabilities), **Block K is now complete**
> (SH102/SH104/SH141/SH144 closed its last four), **Block I is down to SH147**
> (SH145, SH146, SH148, SH149, SH150, SH198 and SH199 have shipped), J's reconciliation and registry (SH96,
> SH153–SH156), **Block L is down to SH110** (SH200 and SH201 have shipped, and closing them
> opened SH222–SH226), M's attribution (SH157–SH161, SH174), **Block N is complete**
> (SH163–SH167 and SH233–SH234 have all shipped; closing it opened SH235–SH240), plus
> SH44–SH47's tenant work. The highest-value one left is **SH223** (deleting a *site* is SH200
> one level up: it takes every post past the trash, the audit and the byte cleanup, with the
> largest blast radius on the surface and no record of what it destroyed).
>
> **What driving it end to end found (SH168–SH175).** A whole site — model, folder tree,
> thirteen pages, a file projection, a CI gate — was built over the agent surface alone, and
> the protocol held: addressing, `dryRun`, the teaching errors, the change cursor, memory,
> the `304`s and MCP's error routing all behaved as designed, and a blueprint apply landed 30
> ops in one call. What it exposed is not the protocol but its **edges**.
>
> ✅ **SH168–SH173 and SH175 have shipped** → [CHANGELOG.md](CHANGELOG.md): a write
> refuses an undeclared field (and the lint reports the ones already stored), a multi-address read
> fails per row, an address without a URL falls back to `id:`, `site.upsert` puts site creation on
> the surface, the CI gate stops failing on content the tree does not manage, `folder` states
> its own conditional requirement, and the render digest composes a page out of the sections it
> names instead of reporting the hash of nothing. **Nothing is left from that build** — SH174 was
> Block M's, and has shipped with the rest of it.
>
> **And what driving the console over it found (Block O, shipped).** Seven defects, every one
> of them answering `200` and none visible to a Java test, a CLI test or the MCP golden: the
> gap was one of *medium*. The block's most durable output is therefore the browser check that
> found them, now a CI gate. Two of the seven were one lesson arriving late — a read faithful
> to the *storage* shape is not a read a console can use. Its write-side twin, **SH168**, has
> since shipped: the same class of defect, caught before it destroys anything.

## Block H — The Agent Gateway (MCP + agent-shaped protocol)

> The core deliverable: an MCP surface designed for an agent, not a CRUD wrapper.
> **Supersedes the old SH24 epic** (SH32–SH37 — "MCP tools over the CDA"), which was
> too thin: CRUD-over-MCP reproduces every discovery/write cost it was meant to fix.
> → §VIII

> ✅ **The gateway is shipped, and so is the follow-up pass** →
> [CHANGELOG.md](CHANGELOG.md) Block H: **SH76** (manifest) · **SH80** (teaching errors) ·
> **SH77** (context pack) · **SH78** (token-lean responses) · **SH79** (path addressing) ·
> **SH121** (id-free reads) · **SH81** (batch writes) · **SH82** (desired-state
> plan/apply) · **SH75** (MCP host + `shio mcp`) · **SH83** (the agent tools, resources
> and prompts) · **SH123** (field projection) · **SH122** (tenant-join guard) ·
> **SH124** (delivery cost header) · **SH125** (null-omission policy) · **SH126**
> (`shio context` CLI) · **SH134** (`post.move`) · **SH135** (folder prune) · **SH128**
> (site-resolution guard) · **SH130** (MCP wire conformance) · **SH131** (full-text bind
> contract) · **SH132** (tool-list budget) · **SH129** (`shio apply` CLI) · **SH84**
> (tenant sweep, SH22 half).
>
> **What is deliberately not in it, and why:** `shio_verify` waits for **SH99** — a
> verify that only pretends leaves the agent believing its work was checked, which is
> worse than knowing it was not. `shio_changes` ships as a *window* over the SH51 audit;
> the `since=` cursor is **SH88**, and faking one over a timestamp would look resumable
> while dropping same-timestamp entries. Both are reported `false` in the manifest so no
> caller has to probe.
>
> **What the follow-up pass taught, and what it opened.** Two of the guards paid for
> themselves the day they were written: SH128's lint found four more live instances of
> the same defect (one of them a scoped CDA token denied on every non-root folder), and
> SH130's transcript found `tools/list` serializing differently on every boot. Both were
> invisible to the behavioural tests that already covered those paths. The general
> lesson is now a working rule — **when a defect class recurs, the fix is a guard per
> invariant, written the first time the invariant is violated** — and everything it turned up
> has now shipped (SH136–SH139). What replaced it is a different *kind* of item: not a missing
> capability but an **asymmetry** between two paths that do the same thing — folder writes that
> are not audited the way post writes are (**SH196**), and the four cross-block findings that
> came out of the same reading (SH198–SH203). ✅ **SH136** → [CHANGELOG.md](CHANGELOG.md) was
> the rule's clearest
> vindication: the byte-stability lint found **four** live instabilities, one of them inside
> SH101's render digest, and none of them detectable by any test that runs in a single JVM.
> ✅ **SH137** → [CHANGELOG.md](CHANGELOG.md) closed the move gap one level up,
> and the guard rule paid out a fourth time: writing the one folder-move writer both surfaces
> call turned up a live console defect nobody had filed — the move dialog could re-parent a
> folder into its own subtree, which succeeds and produces a cycle.
> ✅ **SH138** → [CHANGELOG.md](CHANGELOG.md) found something the hazard framing missed:
> the plan was never truncated, but a compiled document was held to the *hand-written*
> batch's op ceiling, so a large prune failed with advice ("split into batches") that
> nobody can follow for ops the server generated. A ceiling with no usable way past it is
> the same dead end as a silent cut, reached from the other side.

- 💭 **SH84** (deps: SH83 ✅, SH22 ✅) **Turing ecosystem wiring** — the SH22 tenant sweep and its test shipped; what remains is the Turing integration (replaces old SH37). → §VIII.9
- 💭 **SH140** (deps: SH134 ✅, SH100) **Report inbound links when a post moves** — `ShReference` is id-keyed and survives a move, but a URL typed into another post's rich text does not, and nothing tells the agent which pages it just broke. The cheap version is a count in the change detail; the real one belongs with SH100's route verification, which SH141 has since extended to a delivery-side proof — so a broken inbound link is now checkable rather than only reportable.
- 📋 **SH196** (deps: SH88 ✅, SH137 ✅) **No folder write is audited** — `ShFolderWriteService` records nothing on create, rename, move or delete, so `/agent/changes` reports posts appearing in folders it never saw created and the SH109 review queue cannot show a run's structural half. A resuming session's `since=` catch-up is therefore incomplete by construction. → §VIII.12
- 💭 **SH197** (deps: SH136 ✅) **Finish the byte-stability class** — the SH136 lint bans `Map.copyOf`/`Set.copyOf`; a hash-ordered `Map.of(…)` reaching a serialized map component is the same defect and is still unguarded. No live instance today, which is exactly when the guard is cheap. → §VIII.11

## Block K — Perception & verification loop

> Two of the three human round trips in a typical build exist only because the
> agent cannot check its own work. This block is the cheapest large win after H. → §IX

> ✅ **The block is complete (SH99–SH104, SH141–SH144, SH154)** → see
> [CHANGELOG.md](CHANGELOG.md) Block K. The loop, its follow-ups and the human-facing
> handoff have all shipped; the last piece was SH102's optional PNG.
>
> One thing worth carrying forward, because it is a rule and not a task: **`ok` is false only
> for an error**. A warning is what the CMS cannot prove from inside itself, and the three
> mechanisms that grew out of respecting that line — SH141's delivery-side proof, SH142's
> accepted findings, and SH114-declared front-end routes — are all ways of turning a warning
> into either a fact or an explicit decision, rather than ways of making the gate stricter.
>
> **What closing the last four opened (SH204–SH209).** Each line below says which task produced
> it. Two are about the shape of a *record* (SH204, SH205), one is a cost the delivery proof
> currently pays inside a transaction (SH206), one is half a promise SH104 made (SH207), one is a
> fragility that broke the CLI twice in a single session (SH208), and one is the missing scale of
> SH102 (SH209).

- 💭 **SH204** (deps: SH144 ✅) **An incident should name its site where one is knowable** — SH144 folded incidents into the change feed, and they carry no site, so `site=` cannot narrow them and the response has to say so in a note. That note is the honest form, not the fix. Two of the four recorders already know: the webhook dispatcher holds a `ShSiteWebhook`, and a scheduled publish holds the post. Attributing those would let a site-scoped read narrow them like every other row, and let `shio report --site` show one site's failures instead of the instance's. The 5xx filter and the transform pipeline often cannot know, and must keep reporting honestly rather than guessing. → §IX.5
- 💭 **SH205** (deps: SH144 ✅) **The incident fold key is the human message, so one problem becomes many rows** — folding is what makes the durable table affordable (it grows with the number of *problems*, not with traffic), and the key is `(kind, subject, message)` where the message carries varying detail: "gave up after 3 attempt(s), last status 500" and the same failure with 503 are two rows for one broken webhook. It defeats folding exactly where volume is highest, and then pushes the cap. The fix is a stable `signature` beside the human message — the same split SH142 needed between a finding's identity and its prose. → §IX.6
- 💭 **SH206** (deps: SH141 ✅) **The delivery proof fetches inside the report's read transaction** — SH141 accepted this deliberately and bounded it (a 50-request cap, a 15s wall-clock budget, fail-fast per origin), which makes it safe rather than right: a verification call still holds a pooled connection for as long as a front end takes to answer. Gathering the targets inside the transaction and running the network pass outside it is the real shape; the reason it was not done first is that `verify()` is one transactional entry point and splitting it is a refactor, not a fix. → §IX.7
- 📋 **SH207** (deps: SH104 ✅) **The handoff has no console banner** — SH104 shipped the artifact for a PR body, which is where a handoff usually lands; §IX.6 also described a *console banner*, and a curator who never opens a terminal has no way to see what an agent did since they last looked. The data is already composed (changes + sites + verify) and the review queue page (SH109) is where it belongs, so this is a console feature over shipped reads rather than new protocol. → §IX.8
- 📋 **SH208** (deps: —) **A backtick in the CLI's help text breaks the whole binary** — `cli/src/index.mjs`'s `HELP` is a template literal, so prose containing a backtick is a *syntax error* and `shio` fails to load at all. Only the three tests that spawn the CLI catch it; every other CLI test imports command modules directly and passes. **It has now happened three times** — `verify --delivery`, `shio report`, and again while documenting SH150's `movedFrom:` — which is enough to stop treating it as carelessness and fix the class. Help text is the most-edited prose in the package and the least likely place anyone expects a parse error. `String.raw`, or a `help.txt` read at startup, removes it. → §IX.9
- 💭 **SH209** (deps: SH102 ✅) **`shio snapshot` photographs one page per run** — the useful artifact for a handoff or a CI job is the *set*: every published page of a site, one baseline each, and a list of which ones moved. The pieces exist (`/agent/find` gives the addresses, the command gives the capture), and the reason it is not a loop is that a sweep needs a per-page budget, one browser reused across pages, and a summary that survives one page failing to load. → §IX.10


## Block I — Content as files (local-first authoring)

> An agent is far cheaper and more reliable with `Edit`/`Write`/`Grep` over a text
> tree than with read-modify-write over HTTP. Project content to disk, reconcile
> with the server. Largest cycle reducer in the proposal. → §X

> ✅ **The block is shipped bar one task** → [CHANGELOG.md](CHANGELOG.md) Block I:
> **SH86** (projection format) · **SH88** (change feed + cursor) · **SH87** (`pull --content` /
> `push --content`) · **SH89** (three-way merge) · **SH90** (`shio dev --content`) · **SH145**
> (assets) · **SH146** (paged `find`) · **SH148** (fingerprint merge base) · **SH149** (folders
> as files) · **SH150** (a rename is a move) · **SH173** · **SH198** (the suite runs green) ·
> **SH199** (a large tree splits).
>
> **What is deliberately not in it:** conflicts are *reported*, never written as `<<<<<<<`
> markers — a marker in a field value is content the next push would publish; and asset
> *deletion* does not project, because bytes go when their post goes and a `File` post can be
> referenced by content this tree does not own.
>
> **What remains is one task and its follow-ups.** **SH147** is the change feed's own honesty
> note: the lag narrows the stamp-before-commit window, it does not close it. **SH210–SH215**
> came out of shipping the rest of the block — the projection's own edges, now that both
> directions of it exist.

- 📋 **SH147** (deps: SH88 ✅) **Commit-ordered change feed** — SH51 stamps the audit row inside the writer's transaction, so a slow commit can land an older timestamp behind a cursor that already passed it, and for a *sync* feed that is a lost edit rather than a late one. The 1s lag narrows the window; only commit-ordered sequencing closes it. **Scoped in July 2026 and deliberately not started:** "assigned at commit" does not exist in JPA (`beforeCommit` still interleaves), the working shape is a `seq` assigned inside the transaction plus a reader that **stops at a gap**, and the blocker is that a portable monotonic non-PK column across H2/MariaDB/PostgreSQL can only be *proved* by the SH26 Testcontainers ITs — which need Docker. Landing the changeset without them would repeat the SH2/SH5 drift. → §X.3
- 📋 **SH210** (deps: SH145 ✅) **Editing an asset's bytes in place does nothing** — `push --content` resolves a `File` path against the server's inventory and reuses the id when the path is already there, so replacing `shio/assets/Blog/media/logo.png` with different bytes uploads nothing and reports success. It is the one silent no-op left in the projection, and it is the *common* edit for an image. The fix needs both halves: a bytes fingerprint in the base snapshot to notice the change, and a way to land it — an upload always creates a new id-keyed post, so either the reference is repointed (cheap, leaves the old bytes) or SH38 grows a replace-bytes path. → §X.13
- 📋 **SH211** (deps: SH149 ✅, SH137 ✅) **Renaming a folder marker's directory is a delete plus a create, and the delete takes the posts** — the exact hazard SH150 fixed for posts, one level up and worse: `reconcileFolders` sees a marker gone and a marker arrived, the push declares the new folder, and `--prune` deletes the old one — which cascades to every post beneath it. `folder.move` (SH137) already exists and the document has a `moves` section (SH150); this is pairing them for the folder side. Until it lands, renaming a marker directory with `--prune` is destructive. → §X.14
- 📋 **SH212** (deps: SH145 ✅, SH99 ✅) **`shio verify` cannot see a broken asset path** — a `File` field naming an asset that is not in the tree is caught by `push`, which is the last gate rather than the first: the documented CI chain runs the content gates *then* `verify`, so the cheapest check reports it last. The tree's asset paths are checkable without a server at all, which makes this a lint rule over the projection — and the first one that would need `verify` to read local files. → §X.15
- 💭 **SH213** (deps: SH145 ✅) **Every push reads the whole media inventory** — `readRemoteAssets` lists every file in each site and joins the sitemap to it, on every push that declares any `File` field, even when no asset changed. A media-heavy site pays for its whole library to resolve one reference. The listing has filters (`q`, `folderId`) that a per-path lookup could use instead, and a missing folder currently re-reads the entire inventory a second time. → §X.16
- 💭 **SH214** (deps: SH150 ✅) **A probable-move warning cannot be accepted** — `push --content` prints "this looks like a rename it cannot prove" every run for a legitimate delete-plus-create, and there is no way to say "yes, these really are two different posts". SH142 solved exactly this shape for `verify` findings with a baseline file of accepted triples; the same mechanism fits, and without it the warning is one people learn to scroll past. → §X.17
- 💭 **SH215** (deps: SH148 ✅) **A fingerprint collision is undetectable and unreported** — the merge base compares 64-bit hashes, so two different values that collide read as "this field did not change" and the edit is dropped silently. The risk is remote and the trade was deliberate, but the *asymmetry* is not: a sync that loses an edit should be able to say so. Storing a length or a second short hash alongside would make a collision a reported conflict rather than a silent skip. → §X.18

## Block J — Blueprints (appliable units) + Claude Code plugin

> "Skip steps with things already done" only works if the ready-made thing is
> **appliable**, not readable. **Absorbs the old SH23 epic** (SH27–SH31): a
> human-browsable template catalog becomes an agent-appliable blueprint registry. → §XI

> ✅ **The format, the verb, the first-party set, the section vocabulary and the plugin are
> shipped** → [CHANGELOG.md](CHANGELOG.md) Block J: **SH92** (package format + load-time
> validation) · **SH93** (`/agent/blueprints`, `shio apply --blueprint`, `blueprint` on
> `shio_apply`) · **SH94** (`blog` · `docs` · `product-catalog` · `marketing-site` ·
> `landing`) · **SH95** (`@viglet/shio-sections`) · **SH97** (the Claude Code plugin).
>
> **Block J is otherwise closed.** SH153, SH155, SH156 and SH96's local half have all shipped;
> what remains below is what driving them turned up (SH216–SH221), not what they left undone.
>
> ✅ **Where a package may come from now has an answer (SH96, local half)**: the classpath, plus
> one directory an operator names with `shio.blueprint.path`. Whoever can set that could already
> put a jar on the classpath, so it grants no new authority — and the person who set it is the
> person vouching for what is in it. Collisions refuse rather than shadow, an unreadable path
> fails rather than loading fewer packages, and every package reports its origin. The
> **community** half — naming convention, curated index, provenance — is **SH217**.
>
> ✅ **The front-end half has landed (SH156)**: `landing` and `marketing-site` carry the
> starter tree plus a section overlay, generated by one `emit` and guarded by a drift test —
> the copies exist because the instance cannot run a JS build, and nobody writes them. It also
> unblocked itself by publishing `@viglet/shio-model` and `@viglet/shio-sections`, which the
> release workflow had never shipped. The three non-section packages still want a listing page
> of their own (**SH216**).
>
> **What building it opened, and what has since closed.** The two halves that had to meet each
> other did not quite fit. ✅ **SH151, SH152 and SH154 have shipped** →
> [CHANGELOG.md](CHANGELOG.md): an apply can create its target site (blank, opt-in, named in the
> plan), it can add the fields an existing post-type lacks — which SH168 turned from a nicety into
> the difference between a refused write and a package that cannot land — the lint now reads a
> `Multi Select` that declares itself as holding post URLs, so a `Page` naming a section that is not
> there is an error instead of a green run. ✅ **SH153** and **SH155** have closed the rest: a plan
> declares what it would create instead of writing it and rolling back, so planning a package no
> longer needs write access — and a site now records which package and version built it, as an
> SH114 note that reaches the next session through the context pack it already reads.

- 💭 **SH217** (deps: SH96 ✅) **The community half of the registry** — SH96 shipped the local trust model (an operator names a directory; collisions refuse, origins are reported). What is still missing is how a package a *stranger* wrote earns that place: a `shio-blueprint-*` naming convention, a curated index, and provenance stronger than "somebody put it there" (absorbs old SH28/SH29/SH31). → §XI.5
- 📋 **SH216** (deps: SH156 ✅) **`blog`, `docs` and `product-catalog` carry an index page** — SH156 gave the two section packages a front end and deliberately left these three out: they do not compose sections, so the section overlay would ship them a dependency they never import. What they actually lack is a *listing* page over their own content (`/blog`, the docs nav, the catalogue grid), which is per-package and needs the SDK's `query` rather than the section dispatcher. → §XI.11
- 📋 **SH218** (deps: SH119 ✅, SH156 ✅) **A release leaves the generated packages stale, and the next CI run finds out** — `publish-js-sdk.yml` rewrites `create-shio-app/template/package.json`'s dependency ranges and commits, but never re-runs `emit`. The three generated `blueprints/*/files/package.json` keep the previous range, so the drift test fails on the *next* push, attributing a release's side effect to whoever pushed after it. Run `emit` in the bump step and commit the packages with it. → §XI.12
- 📋 **SH219** (deps: SH96 ✅) **The local package directory needs a reload that is not a restart** — `ShBlueprintRegistry` caches on first access, which was free when the classpath was the only source and packages could not change while running. Now an operator can drop a package into `shio.blueprint.path` and nothing sees it until the process restarts, which for a directory whose whole purpose is local iteration is the wrong default. Wants an explicit, `ROLE_ADMIN` reload rather than a watcher — a re-scan that ran itself would re-run validation at an unpredictable moment and could take the registry down between two applies. → §XI.13
- 📋 **SH220** (deps: SH155 ✅) **A re-apply defaults to the parameters the site was built with** — SH155 records name, version and parameters as an SH114 note, which makes "which blueprint is this site" answerable; it does not yet make the next apply cheaper. Re-applying still means re-supplying every `--param`, and a value the operator misremembers silently rebuilds the site differently. The record is already there and already parseable; what is missing is `shio apply --blueprint <name> --site <site>` reading it and saying which values it defaulted. → §XI.14
- 💭 **SH221** (deps: SH153 ✅) **The rest of the write surface should be plannable read-only too** — SH153 removed the last write from a blueprint plan, and the property is worth generalising: `POST /agent/plan` and a `dryRun` batch are already write-free, but nothing *enforces* it, so the next feature that needs to resolve something it would create can reintroduce a rollback-only transaction without a test noticing. A `readOnly` transaction around the dry-run paths would make it structural rather than remembered — the same argument `ShByteStabilityLintTest` makes for `Map.copyOf`. → §XI.15

## Block L — Agent safety & human-in-the-loop

> ✅ **SH106–SH109 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block L: the `AGENT` token scope
> and the agent session id as a column, draft-by-default with a per-credential publish gate,
> confirm tokens on destructive writes, and the review queue.
>
> **The invariant held:** every guard is *narrower* than what an agent could reach before —
> nothing on the surface gained a capability. Two of them are deliberate tightenings and the
> changelog says so: a `WRITE` token that used to publish through the agent surface now needs
> `mayPublish`, and a prune now needs the token from its own plan.
>
> **What building it taught.** The publish gate wanted to live at the publish site and belongs
> before the first write instead — "you cannot publish" is a different answer from a half-built
> site with a 403 at the end. And the review queue is only expressible because a *run* has an
> identity: a session minted per request would have produced two hundred one-change sessions,
> which is the same as having none.

> ✅ **SH200 and SH201 have shipped** → [CHANGELOG.md](CHANGELOG.md) Block L: a folder delete
> trashes its subtree through the post writer instead of erasing it past the trash, the audit and
> the byte cleanup; and the console's move/copy authorizes its sources, not only its destination.
>
> **What driving those two found (SH222–SH226).** The block's invariant now cuts both ways —
> *whichever surface is stricter is the correct one* — and applying it turned up the same
> asymmetry in three more places: the trash an agent can fill but not read, the site delete that
> is SH200 one level up, and a permanent deletion nobody records. Everything below is a
> follow-up; nothing in Block L is unbuilt except SH110.

- 💭 **SH110** (deps: SH106 ✅, SH45) **Agent budget guards** — per-session call/write/storage ceilings, layered on SH45's per-tenant metering. Still blocked on SH45: building the ceiling first means a second metering mechanism to reconcile later. A runaway agent should hit a ceiling and report it, not a bill. → §XII.5
- 📋 **SH222** (deps: SH200 ✅, SH55 ✅) **An agent can fill the trash and cannot read it** — `post.delete` and `folder.delete` both answer "to trash (restorable)", and that is the only place the trash appears on the agent surface: there is no `/agent` read, no MCP tool and no `shio` command that lists or restores it. So the undo the protocol *advertises* is reachable only by a human in the console, which inverts P1 on the one capability an agent most needs after a mistake — and makes the reassurance in the change set something the caller cannot act on. → §XII.6
- 📋 **SH223** (deps: SH200 ✅, SH38 ✅, SH51 ✅) **Deleting a site is SH200 one level up, and worse** — `ShSiteWriteService.delete` removes the site entity and lets the JPA cascade take its folders, so every post in the site skips the trash, the audit, the change feed and the `DELETE` lifecycle event exactly as a folder delete used to. It is the largest blast radius on the surface and the only one with no record of what it destroyed. The fix is the same delegation SH200 made, applied through the folder writer rather than a third cascade. → §XII.7
- 📋 **SH224** (deps: SH55 ✅, SH200 ✅) **A permanent deletion is the one write nobody records** — `purge` fires the `DELETE` lifecycle event so the webhook and SH38's byte cleanup run, and writes no SH51 audit row at all. "Who destroyed this, and when" is therefore unanswerable for the *irreversible* half of the trash, while the reversible half is fully recorded. A folder purge can now take forty posts in one call, which is what makes the gap worth closing. → §XII.8
- 📋 **SH225** (deps: SH54 ✅, SH201 ✅) **The ACL check depends on `open-in-view` and does not say so** — `requireRead`/`requireWrite` are unannotated and reach `canRead`/`canWrite` by self-invocation, so the `@Transactional` proxy is bypassed and the folder-chain walk runs with whatever session the request happens to have. In the app that is `spring.jpa.open-in-view`; anywhere else — a scheduled job, an internal call, a test — it is a `LazyInitializationException` *instead of* a permission decision. A guard that fails open-or-explodes depending on a setting whose purpose is to be turned off is not a guard. → §XII.9
- 💭 **SH226** (deps: SH200 ✅) **A trashed folder is excluded by convention, not by invariant** — the exclusion lives in `ShFolderRepository`'s finders, and ids also resolve polymorphically through `ShObjectRepository`, which knows nothing about it. SH200 guarded the three paths that matter by hand; the next read that resolves a folder id there will silently show deleted content. This is the SH128 `ShFolderSite` shape exactly, and that one earned a lint test for the same reason. → §XII.10

## Block M — Token economy (measured)

> ✅ **Block M shipped in full (SH112–SH116, SH127, SH157–SH161, SH174)** → see
> [CHANGELOG.md](CHANGELOG.md) Block M: the task benchmark, response budgets, instance
> memory, the `AGENTS.md` generator, conditional agent reads, the lean delivery JSON, and
> the six follow-ups the measurement itself opened.
>
> **What the measurement says now.** Tokens **2.8×–10.5×**, tool calls **2.3×–3.8×** across
> the four canonical tasks. SH157 is the first change in three blocks to move a ratio by
> improving the *agent* path rather than by the baseline getting leaner: every task lost a
> call, because discovery folded into one read and proof folded into the write. The ≈10×
> token target is met on one task of four and the ≈6× call target on none — the remaining
> calls are real work, so the next one to remove is a harder argument than these two were.
> Numbers and ceilings: [token-budgets.properties](../shio-app/src/test/resources/token-budgets.properties).

- 📋 **SH227** (deps: SH157 ✅, SH132 ✅) **`shio_apply` cannot ask for its own proof** — `shio_write` took SH157's `verify` argument for ~15 tokens, and the desired-state tool could not: at 457 measured against a 460 per-tool ceiling there is no room, so the write most likely to want a lint (a whole section at once) is the one that must still spend a second call on it. The fix is a trim, not a raise — the ceiling exists to force exactly this decision. → §XIII.7
- 📋 **SH228** (deps: SH157 ✅) **A write proves only its posts** — the SH157 proof lints the `post:` addresses a run touched and silently ignores the rest, so a batch of `folder.upsert`s comes back with no proof at all rather than with the empty-folder warning the lint already knows how to produce. An agent that scaffolds a structure and reads `proof: absent` learns nothing, and cannot tell that from "nothing was wrong". → §XIII.8
- 📋 **SH229** (deps: SH116 ✅, SH159 ✅) **Nothing checks that an ETag covers every parameter that changes the bytes** — SH157 found `include` missing from the context pack's tag, three blocks after SH116 wrote down the rule it breaks: two projections of one unchanged pack shared a tag, so `include=model` could revalidate against a full pack's cache. SH174 then had to re-derive the same guard by hand for the manifest. This is the SH136 shape exactly — an invariant about how code is *written* — and it wants the same answer, a lint, not another careful reviewer. → §XIII.9
- 💭 **SH230** (deps: SH174 ✅) **The manifest's weight is prose, and terse barely touches it** — `format=terse` measured 2088 against JSON's 2343, an 11% saving where the same encoding is worth a third to a half everywhere else, because ~87% of the document is a purpose sentence and a runnable example per endpoint and both are kept verbatim on purpose. `include=features,limits` (298) is the real lever and it only helps a caller that already knows what it wants. Worth deciding whether the index can be earned rather than paid: derivable examples elided, or the index behind its own `include`. → §XIII.10
- 💭 **SH231** (deps: SH161 ✅) **An instance's own notes travel nowhere** — SH161 made a *site's* notes ride its export, and deliberately left the instance-wide ones behind, because importing another installation's house rules alongside somebody's blog is a P7 surprise. That reasoning is right for a site import and wrong for a whole-instance migration, which is the case where those notes are most valuable and least reproducible. The tenant export is the place; the decision is what an import does with them. → §XIII.11
- 💭 **SH232** (deps: SH159 ✅) **`/agent/changes` cannot revalidate the way it is most often called** — in cursor mode an unchanged range is byte-identical and free, which is the SH88 walk working as intended. In window mode the response's own cursor is the moving edge of the feed, so no two calls agree and every poll pays in full. That is *correct* — the answers really do differ — but the window is what a session opens with, and it is the read most likely to be repeated by a client that has not kept a cursor. → §XIII.12

## Block N — Realignment of what already shipped

> ✅ **Block N shipped (SH117, SH119, SH120, SH162)** → see [CHANGELOG.md](CHANGELOG.md) Block N:
> one name-keyed post-type surface, the starter as a blueprint, console navigation reads on
> the CDA shapes, and the SDK type mirror with a test behind it.
>
> **What it cost the claim, and why that is the right outcome.** SH117 and SH120 both fixed
> the *baseline* the SH112 benchmark measures against, so every token ratio fell against an
> unchanged agent path: **3.8×–23.3× became 2.6×–12.6×** (and 2.1×–12.1× after Block O's SH176
> did the same to the post read), and tokens now clear the concept
> spec's ≈10× on one canonical task of four rather than three. A large part of the measured
> advantage was never the protocol — it was the console reads being entity-shaped, and it is
> now lean on both sides. What is left is the part worth defending — addressing, batching, the
> context pack, a lint that names the defect — and **SH164 has now measured each of them
> separately** rather than leaving the claim resting on an aggregate.
>
> ✅ **SH164 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block N: the claim is attributed per
> source (addressing 26× and the lint 15× carry the tokens; batching buys turns; projection is
> the smallest), and the aggregate ≈10× per-task target is retired in favour of per-source floors
> plus the session claim on calls.
>
> ✅ **SH163 and SH165 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block N: renaming a
> post-type is an operation that moves it, and the console listing pages — with the sort and the
> search moving to the server alongside it.
>
> ✅ **SH166 and SH167 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block N: the carried files
> say they are templates and are reachable over MCP at all, and the last three blanket CSRF
> exemptions are narrowed to the callers that need them. Reviewing the CDA one turned up
> **SH233** and **SH234**, and both have since shipped: a presented `Key` beats an existing
> session on the CDA path (which is why the Universal Editor could not save), and `/graphql`'s
> "no Mutation" premise is now a build failure carrying the decision made in advance.

> ✅ **SH239 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block N: the two scaling sources are
> measured at a second fixture size and required to *rise*, which also exposed that SH164's own
> first numbers moved with the test order (addressing was 26.2× or 11.8× depending on what ran
> first; the corrected figures are in the budgets file).
>
> ✅ **SH237 and SH240 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block N: "select everything"
> pages the rest in before selecting (so the dialog's count is the count that will be acted on),
> and the listing filter is the CDA's own full-text predicate rather than a title substring.
>
> ✅ **SH235 and SH236 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block N: `shio push` renames a
> post-type instead of creating and deleting one (two signals, ambiguity refused, the source kept
> out of `--prune`'s way), and deleting a type content still points at is a teaching 409 naming
> the count instead of a constraint violation dressed as a server fault.
>
> **What finishing the block opened (SH235–SH240), and what is left.** Five came from following
> one thread each to its client — SH163's rename had no CLI verb and no guard on the delete it
> replaces (SH235, SH236), and SH165's paging quietly narrowed two things the browser used to do
> over the whole folder (SH237, SH240) — and all four have since shipped. **The two still open are
> the two that need something this repository does not have yet**: SH238 needs a front end to
> embed before the Universal Editor can be driven end to end. Everything else has shipped.

- 📋 **SH238** (deps: SH233 ✅, SH182 ✅) **The Universal Editor is driven by nothing** — SH233's defect survived because every test in this repository builds the request in Java or mocks the client, and the one comment that described the mechanism described it wrongly. The editor needs a front end to embed, so full coverage waits on Block P; the cheap half does not — SH182's harness can assert that the editor's PATCH reaches the CDA authenticated as the token rather than the curator, which is the single fact the whole feature rests on. → §XIV.15


## Block O — The human half (the console the curator uses)

> ✅ **Block O shipped (SH176–SH182)** → see [CHANGELOG.md](CHANGELOG.md) Block O: the console
> smoke check in CI, the post read that carries its post-type and parsed data, `schemaJson`
> retired, one form-schema entry point, View Site pointed at something that exists, a
> not-found route, and a site read that answers the key it is given.
>
> **What building it taught.** Every defect in the block answered `200`, and none was visible
> to a Java test, a CLI test or the MCP golden — the gap was one of *medium*, so the block's
> most durable output is the browser check that found them (SH182) rather than any one fix.
> Two of the seven were the same lesson one endpoint late: a read faithful to the *storage*
> shape is not a read the console can use (SH176, after SH120), and a second way of saying
> one thing is a second thing to keep right (SH177/SH178).
>
> **What it opened.** The check asserts *a* field renders, not the *right* field, and it has no
> accepted-findings baseline — SH142's shape (now shipped for `shio verify`) applies to it
> unchanged. The site provisioning it needed walked the console's CSRF path by hand, which was
> **SH170** measured rather than argued, and has since shipped.
>
> **And what SH137/SH139 found on the way past.** Fixing the agent's move verbs meant reading the
> console's, and the same lesson landed a third time: the console path is where the careless
> version of a shared verb lives, because nothing on it is asserted at the level the agent
> surface is. The two below are what that reading turned up.

- 📋 **SH202** (deps: SH99 ✅) **The console's clone mints a duplicate friendly URL** — "clone" is copy-to-same-folder, and `copyPost` re-slugs `furl` from the title, so cloning yields two posts in one site claiming one URL. `shio verify` reports that as a `duplicate-url` **error**, address resolution picks whichever it reaches first, and the curator's most casual action is the thing that produces it. A copy needs a free URL (`-2`, or the SH134 occupied-target rule applied on create). → §XVI.1
- 💭 **SH203** (deps: SH137 ✅) **The move dialog offers destinations the server refuses** — it navigates *down* from the site root, so the folders inside the one being moved are reachable and now answer `409` (SH137's cycle guard, correctly). A picker that lists choices which cannot work teaches the curator that moves fail at random; the subtree being moved should be disabled in the tree, with the refusal kept as the backstop it is. → §XVI.2

## Block P — The visible site (what the human actually looks at)

> The differentiator is **making something a person can see**. Today an agent can build a
> site's content and structure over the agent surface and there is no way to look at it: the
> preview mints a URL against `site.url` and 400s without one, the Universal Editor is an
> `<iframe src={site.url}>`, and nothing in this repository renders a page. Both human-facing
> visual surfaces are windows onto a front end that only `shio init` can scaffold and only a
> developer's machine can run. → §XV

> **The model is the one this product already had: page → layout → region.** Recovered from
> `origin/FSP` (an ancestor of HEAD, last touched 2019-09-09) and from the real template in
> `openviglet/bootstrap-site`, which is an SH68 exchange package — the presentation *is*
> content. The lineage is OpenText WEM / Vignette V7 (page → template → component), which is
> what corporate clients built institutional sites on, and the granularity is the point: each
> level has a different owner and a different clock, a region is the unit of reuse, of
> **editing** (which is what makes the Universal Editor mean anything) and of caching.

> **The non-goal this respects, and how.** "No GraalJS server-side JS template engine" (the
> former Block D, SH17/SH18 — the Nashorn lineage) is binding, and the reason is
> **sandboxing liability**: `ShNashornEngineConfiguration` called `getScriptEngine(String[])`
> and never the `ClassFilter` overload, so a layout — which is *content* — had `Java.type()`
> over the whole classpath and the raw `HttpServletRequest` in scope. Groovy changes the syntax
> and none of that argument. The resolution splits what FSP fused: **HTML composes on the
> server, declaratively** (the template language already *was* Handlebars, loaded into Nashorn
> from a CDN at render time — a Java implementation runs the same templates with no script
> engine), and **JavaScript runs in the browser** as a region's static asset. An author still
> writes HTML + JavaScript; nothing is evaluated in the JVM. Production delivery stays Next.js
> over the CDA (P10), and what lands here is **preview-grade** until that is deliberately
> revisited.

- 📋 **SH183** (deps: SH68 ✅, SH95 ✅) **Render page → layout → region, server-side, without a script engine** — restore the three-level model as system post-types (`PAGE_LAYOUT`, `REGION`, `THEME`), compose them with a Java Handlebars implementation, and register the seven `shObject.js` capabilities (navigation, query, search, form, relation, links) as explicit helpers instead of bindings on an unfiltered engine. The sample's per-region JavaScript is 767–1098 characters of loop-and-remap that a domain-aware block helper replaces with one line. Layouts and regions being content is what makes them agent-authorable via `post.upsert`, exportable, projectable to files and lintable — a closed Java projection of the SH95 types would be none of those. → §XV.1
- 📋 **SH184** (deps: SH170 ✅, SH183) **A site's front-end URL is unreachable from the agent path** — `site.url` gates both visual surfaces (`400 site has no url`; `universalEditor.siteNoUrl`), and `site.upsert` deliberately never updates a site, so an agent that builds a site and deploys a front end still cannot connect the two. With SH183 the sensible default is Shio's own preview route. → §XV.2
- 📋 **SH185** (deps: SH183) **The Universal Editor has no counterpart, and `sh-region` is already the contract** — `visual-editor.page.tsx` posts `{type, data}` into the iframe and listens for replies, and there is not one `postMessage` in `create-shio-app`, `shio-react-sdk`, `shio-sections` or `shio-client`, so the editor cannot work against anything. The 2018 templates already annotate composition with `sh-region="NAME"`; a renderer that preserves those attributes tells the editor which DOM node is which region and which post to save. Needs the protocol written down, SH183 as its first implementation, `"*"` replaced by the site's origin, and `universalEditor: true` demoted until it is true. → §XV.3
- 📋 **SH187** (deps: SH183, SH169 ✅, SH102 ✅) **The agent sees a page's structure, never its appearance** — SH169 made the digest compose a page's sections, so an agent can prove the outline is right; nothing lets it see that the page *looks* right, since `visualSnapshot` and `signedImageUrls` are `false` and `renderFetch` is off by default. SH102 has shipped as `shio snapshot` and is the correct instrument scoped for the wrong reader — "for the human handoff rather than for the agent", i.e. a CLI command and an image file rather than a call and a payload — and SH183 makes it nearly free, because the renderer's own HTML is the artifact to snapshot. → §XV.4
- 📋 **SH188** (deps: SH107 ✅) **`api_token.may_publish` is NULL on existing rows and the field is a primitive** — `v2026.3.11.yaml` adds the column nullable with no backfill and no `defaultValueBoolean`, stating the intent that it "read as `false` when absent"; `ShApiToken` maps it as `private boolean`, which Hibernate cannot populate from NULL. So any install that had a token before that changeset throws `IllegalArgumentException` on `GET /api/v2/api-token` **and `POST /api/v2/preview-token`** — preview is dead for a second reason, independent of SH184. Either the wrapper type with an explicit null read, or a default plus a backfill; the changeset's own comment says which behaviour was meant. Found by SH103's feed surfacing it through `shio verify`. → §XV.6
- 📋 **SH189** (deps: SH68 ✅, SH80 ✅) **Importing an exchange package teaches nothing and is not on the agent surface** — `POST /api/v2/import` answers `ResponseEntity.badRequest().build()`, so a `ShExchangeException` that knows exactly which field or entry was wrong arrives as a bodiless `400`. Bringing the 2018 `bootstrap-site` package forward meant inferring seven schema renames from the Java records instead of reading them off a response. It is also console-only — multipart plus CSRF, no `batch` op, no MCP tool — so an agent can build a site from scratch but cannot import one. → §XV.7
- 💭 **SH190** (deps: SH103 ✅) **Diagnostics name a failure but never carry the evidence** — an `api-error` finding reports the exception message and then advises "the instance log has the stack trace (the body never will)", which is an accurate description of a protocol that stops one step short: the agent is told to go somewhere it has no access to. The window already exists per SH103; what it needs is the frame that failed, captured where the incident is recorded. Cheaper and more honest than a log-tailing tool, which would hand out everything for a question that is always about one request. → §XV.8
- 📋 **SH191** (deps: SH183, SH86 ✅) **A template is code and must project as files** — `bodyFieldFor` treats only an `HTML Editor` field as a document body, so a layout's `Ace Editor - HTML` and a region's `Ace Editor - Javascript` collapse into `fields:` as escaped JSON one-liners inside YAML frontmatter: no line numbers, no highlighting, nothing `Grep` can reach, and every tweak is a re-escape. Law P4 fails on the one artifact an iterative loop edits constantly. Needs sidecar files (`<post>.html`, `<post>.js`) beside the `.md`, round-tripping losslessly. → §XV.9
- 📋 **SH192** (deps: SH183, SH99 ✅) **A template must fail at write time, not at view time** — nothing validates a layout or region, so an unclosed block, an unknown helper or a reference to a field the post-type does not declare is accepted by `post.upsert` and discovered as a blank page. The iteration cost is a guess per save. Needs a `template-invalid` lint rule and a refusal on write, reported the way FSP's `regionError` already did in 2019: file, line, column and a caret under the offending character. → §XV.10
- 📋 **SH193** (deps: SH183, SH154 ✅) **The blast radius of a region change is uncomputable** — `sh-region="NAME"` lives inside an HTML string, so nothing can answer "which layouts slot this region, and which pages use those layouts". Editing a shared region is therefore unbounded and unverifiable, which is the difference between a CMS region and a component in one app. Extract slot references into the reference graph the way SH154 did for `Multi Select` post URLs, so `find`, `verify` and `changes` all agree on who is affected. → §XV.11
- 📋 **SH194** (deps: SH187, SH102 ✅) **Appearance needs a before/after and a one-call loop** — a snapshot answers "what does this look like"; iteration asks "what did my change do", which is a diff. SH102 shipped that diff, still scoped for the human handoff. The other half is round trips: today a layout edit is upsert, then render, then look. A write that can return the rendered result of what it just changed turns three calls into one. → §XV.12
- 💭 **SH195** (deps: SH183) **Most layout changes are tokens, not structure** — `PT-THEME` carries `CSS` and `JAVASCRIPT` as opaque blobs, so "tighten the headings" or "change the accent colour" is a stylesheet rewrite with nothing to verify and nothing to diff. A declared token vocabulary (spacing, type scale, colour roles) makes the common change a field edit an agent can make safely and a human can review, and leaves raw CSS as the escape hatch rather than the interface. → §XV.13
- 💭 **SH186** (deps: SH183, SH166) **An MCP-only agent cannot produce a visible site** — the server never writes a project file (correct — P7), and only the CLI scaffolds one, so a client with no filesystem ends with content nobody can look at. SH183 is the answer if it lands: the site is visible without a project at all, and SH166's unsubstituted-files problem stops being on the critical path. → §XV.5

---

# Part 2 — Pre-existing backlog (unchanged, still valid)

## Block A — Headless foundation (Content Delivery API)

> ✅ **All tasks shipped (SH1–SH5)** → see [CHANGELOG.md](CHANGELOG.md) Block A.
> Under the new orientation the CDA is the read substrate every agent tool composes
> over (law P10) — unchanged and more load-bearing than before.

## Block B — JavaScript-for-pages DX

> ✅ **Foundation shipped (SH6–SH11)** and ✅ **SH62 epic — post-types as typed TS
> code (SH63–SH66)** → see [CHANGELOG.md](CHANGELOG.md) Block B.
> Post-types-as-code is the template the whole agent-native protocol follows:
> declarative source → canonical serialization → plan → apply → drift guard.

## Block C — Composable platform primitives

> ✅ **All tasks shipped (SH12–SH16, SH26, SH51–SH55)** → see [CHANGELOG.md](CHANGELOG.md) Block C.
> Follow-up worth noting: SH55 shipped soft-delete for **posts**; folder soft-delete
> (subtree-hide semantics) remains a deliberate future task.

## Block E — Legacy retirement

> ✅ **All tasks shipped (SH19–SH21, SH48–SH50, SH56–SH61, SH73)** → see [CHANGELOG.md](CHANGELOG.md) Block E.
> The legacy AngularJS UI + Thymeleaf templates are gone; `shio-react` (+ the headless CDA) is the only console/delivery surface.

## Block F — Market positioning

- ✅ **SH22** (deps: SH2) **Multi-tenancy / SaaS** — shipped → [CHANGELOG.md](CHANGELOG.md) Block F. Follow-ups remain:
  - 💭 **SH44** (deps: SH22) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
  - 💭 **SH45** (deps: SH22) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
  - 💭 **SH46** (deps: SH22) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
  - 💭 **SH47** (deps: SH22, SH38) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

> ⛔ **SH23 — Starter marketplace (SH27–SH31): superseded** by Block J (blueprints).
> A human-browsable template catalog becomes an agent-appliable blueprint registry.
>
> ⛔ **SH24 — AI authoring via MCP (SH32–SH37): superseded** by Block H.
> The original decomposition was CRUD-over-MCP; see [specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md) §4.
>
> ✅ **SH25 — Image/asset transformation API** (epic) fully shipped (SH38–SH43) → see [CHANGELOG.md](CHANGELOG.md) Block F.

## Block G — Content portability (export / import / clone / templates)

> ✅ **SH67 — Content exchange / portability** (epic) fully shipped (SH68–SH72) → see [CHANGELOG.md](CHANGELOG.md) Block G.
> The exchange package format (SH68) is the substrate Block J's blueprints are built on.

---

## Non-goals (do NOT add as tasks)

Binding constraints — see [IMPROVEMENTS.md](IMPROVEMENTS.md) §0.3–§0.4, §0.7 and
[specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md) §11 for the full
rationale. Summary:

- **Shio does not host a model.** No bundled LLM, no prompt runner, no provider API
  keys in the CMS, no "generate this page for me" button. Shio is the substrate an
  external agent drives; adding inference trades a defensible protocol position for
  a commodity feature and puts someone else's rate limits inside the content engine.
- **Do not rewrite the backend in Node/Next/Go/PHP.** Java/Spring Boot is the
  content engine and the glue with Turing + Dumont; a rewrite is high-cost,
  high-risk, zero user-visible value (§0.3).
- **No third API contract.** MCP is a *shape* over the CDA (SH1) + console services,
  never a parallel semantic (law P10).
- **The agent path is not a privilege escalation.** Tenant isolation (SH22),
  content ACL (SH54) and token scopes apply identically on every agent surface.
- **No feature whose only interface is the console** (law P1) — every capability
  needs an MCP/CLI/file path.
- **No GraalJS server-side JS template engine** (the former Block D, SH17/SH18 —
  dropped). An agent writes Next.js; an in-JVM script engine is sandboxing
  liability with no agent-side benefit. Groovy is likewise not the answer (§0.4).
- **Don't couple the JS SDK to React** — the framework-agnostic core (SH6) is the
  base; React is one consumer.
- **Don't optimize a benchmark by degrading correctness** — a terse response that
  omits something the agent must then ask for twice is a regression, not a win.
