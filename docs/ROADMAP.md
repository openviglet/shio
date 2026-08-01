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
> **How to pick work:** the **priority queue** below comes first. After it, the
> lowest-numbered task in a block whose `deps` are all shipped. The `→` pointer is
> the section in IMPROVEMENTS.md with the full design.
>
> **An entry here is one sentence: what + why + `→` pointer.** The reasoning lives in
> IMPROVEMENTS.md — that is what the pointer is for, and a line that restates it is how this
> file became a history instead of a queue (SH341, measured: 142 words per line against a
> one-sentence rule).

## Priority queue

> **Reachability before everything else.** Four exploratory builds (SH168–175, SH176–182,
> SH247–285, SH325–337) have each produced 6–13 tasks, and they keep finding the **same classes**:
> SH160's `instanceMemory: true` half-truth returned as SH326's `serverRender: true`; SH170's
> session+CSRF friction returned as SH327 and SH335. The reason is structural — this repository
> turns an **internal-consistency** defect into a build guard (eleven lints do exactly that) and a
> **reachability** defect into a task, which is fixed once and guards nothing. **P1 was the only one
> of the ten laws with no instrument**, and it is the law the product is named after. → §XVI.59
>
> **The rule, which is SH322's one level up:** *when a review finds a law is being broken, the
> instrument that finds every other break is the deliverable, and it lands before the fixes.* So
> do not work SH325–SH337 by hand first — SH338 enumerates them and finds the ones nobody has hit.
>
> ✅ **SH343 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: webhooks are declared and
> CSRF-narrowed — and the read was **leaking the HMAC secret in plain text**, which had to close
> first, because narrowing is what widens who can read it. **SH339's gap list is now empty.**
>
> ✅ **SH348 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: a locale and a sibling link survive the
> file round trip, the link travels as an **address** rather than a group id, and the op turned out to
> be the last of four layers rather than the whole task. The P1 inventory is **9, from 14**.
>
> ✅ **SH344 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: the preview token is declared and
> CSRF-narrowed, and it was **funded by pruning two `/cda/**` rows** rather than by another budget
> raise — SH327's raise is given back in full. The P1 inventory is **11 unreachable, from 14**.
>
> ✅ **SH347 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: the context-cache ceiling is chosen
> here rather than inherited, and `ShContextCacheHeadroomTest` fails while there is still room — **28
> isolated databases against 64**. Its declared limit is that it counts a lower bound.
>
> ✅ **SH342 shipped, in half** → [CHANGELOG.md](CHANGELOG.md) Block P: scheduled publishing is
> `when` on `post.publish`, and a past instant is refused rather than read as "now". The `locales`
> half is **SH348** — it needs a decision an address cannot express. The P1 inventory is **12
> unreachable, from 14 two tasks ago**.
>
> ✅ **SH327 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: `/api/v2/render/**` is CSRF-narrowed
> and `POST /render/post-types` is in the manifest index, so the endpoint that makes a site *visible*
> is reachable and discoverable. Both inventories refused to let their entries outlive the fix.
>
> ✅ **SH332 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: the op vocabulary is a `shio://ops`
> resource, declared once and asserted against the planner's own refusals. It opened **SH346** — the
> resource is MCP-only.
>
> ✅ **SH331 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: one refusal where arguments are read,
> synonyms accepted **and reported**, and a `data` key never touched. **0 silent drops, 6/6 naive
> calls landing first try** — SH340's measurement changed the design from four renames to one guard.
>
> ✅ **SH340 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: a naive client's calls are counted —
> **6 teaching errors and 6 silent drops in 12 calls** — and the sweep found that four of six read
> tools ignore an unrecognised argument, so SH331's one case is a class.
>
> ✅ **SH345 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: `?site=nope` answered a **500**, because
> a read-only transaction marked itself rollback-only and destroyed the refusal it had just written.
> Found by SH340's own first run.
>
> ✅ **SH339 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: **25 mutating roots, 8
> stateless-writable, 3 real gaps** — and a CSRF refusal now carries a body that differs for a
> browser and for an agent, because the measured one was empty.
>
> ✅ **SH325 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: the widget catalogue is seeded per
> widget on every startup, so the render model is creatable on an existing instance — and there were
> **two** guards where the entry named one.
>
> ✅ **SH338 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: **52 features classified, 14 not
> agent-reachable**, held as an asserted list. It did what it was filed to do — **four of the
> fourteen had no task** (SH342–SH344), and one (`reviewQueue`) turned out to be a decision rather
> than a gap.

1. Nothing named by either reachability instrument. **SH339's gap list is empty and the P1 inventory is 9**, of which four are deliberate trades. Next: whatever the block's remaining ideas argue for (SH312, SH316, SH319).
3. Then the rest of SH326–SH337, in whatever order SH338's output argues for.

> **Deprioritized deliberately:** SH258, SH296, SH317, SH318 and SH324 all audit the audits — each
> correct, all second-order, and queued behind the items above. The original reason was that
> reachability had **zero** instrument coverage; it now has two (SH338, SH339), so what keeps these
> behind is that the inventories those two produced name concrete gaps and these do not.

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
> reviewing what was still CSRF-exempt (both shipped, so their design sections are gone),
> and **SH241–SH246** from closing Block O's last two (SH202/SH203) — three of those six are
> not content defects at all but the *build* they were found through (§XVI.1, §XVI.2, and
> SH246, shipped) — and **SH247–SH251** plus **SH259–SH285** from building Block P's renderer (§XV.14–§XV.21, §XVI.14–§XVI.37,
> §XVI.7; SH250 was the build again, and flaky where SH246 was deterministic — shipped), and
> **SH252–SH258** from projecting its templates to files (§XV.18, §XVI.8–§XVI.13 — SH253 is a
> normalization that causes the drift its own comment says it prevents, and SH254 is the same
> defect in the notes format; both were invisible because every fixture in the suite is a value
> its format happens to represent exactly, which is what SH255 turns into a guard — and then
> running that guard's values by hand against the one round trip it does not cover found SH256,
> an asset path that resolves outside the project — and finishing the guard found a third defect
> by reading a refusal's *message*, which is what SH258 is about)
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
> found them — which is a CI *workflow* and, as **SH241** records, not yet a CI *gate*: it
> triggers on `main`, and the default branch is `2026.3`. Two of the seven were one lesson
> arriving late — a read faithful to the *storage* shape is not a read a console can use. Its write-side twin, **SH168**, has
> since shipped: the same class of defect, caught before it destroys anything.

## Block H — The Agent Gateway (MCP + agent-shaped protocol)

> The core deliverable: an MCP surface designed for an agent, not a CRUD wrapper.
> **Supersedes the old SH24 epic** (SH32–SH37 — "MCP tools over the CDA"), which was
> too thin: CRUD-over-MCP reproduces every discovery/write cost it was meant to fix.
> → §VIII

> ✅ **Block H shipped, follow-up pass included (SH75–SH84, SH121–SH135)** → see [CHANGELOG.md](CHANGELOG.md) Block H.

- 💭 **SH84** (deps: SH22 ✅, SH83 ✅) **Turing ecosystem wiring** — the SH22 tenant sweep and its test shipped; what remains is the Turing integration (replaces old SH37). → §VIII.9
- 💭 **SH140** (deps: SH100 ✅, SH134 ✅) **Report inbound links when a post moves** — `ShReference` is id-keyed and survives a move, but a URL typed into another post's rich text does not, and nothing tells the agent which pages it just broke. The cheap version is a count in the change detail; the real one belongs with SH100's route verification, which SH141 has since extended to a delivery-side proof — so a broken inbound link is now checkable rather than only reportable.
- 📋 **SH196** (deps: SH88 ✅, SH137 ✅) **No folder write is audited** — `ShFolderWriteService` records nothing on create, rename, move or delete, so `/agent/changes` reports posts appearing in folders it never saw created and the SH109 review queue cannot show a run's structural half. A resuming session's `since=` catch-up is therefore incomplete by construction. → §VIII.12
- 💭 **SH197** (deps: SH136 ✅) **Finish the byte-stability class** — the SH136 lint bans `Map.copyOf`/`Set.copyOf`; a hash-ordered `Map.of(…)` reaching a serialized map component is the same defect and is still unguarded. No live instance today, which is exactly when the guard is cheap. → §VIII.11

## Block K — Perception & verification loop

> Two of the three human round trips in a typical build exist only because the
> agent cannot check its own work. This block is the cheapest large win after H. → §IX

> ✅ **Block K shipped (SH99–SH104, SH141–SH144, SH154)** → see [CHANGELOG.md](CHANGELOG.md) Block K.

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

> ✅ **Block I shipped bar SH199 (SH86–SH90, SH145–SH150, SH210–SH215)** → see [CHANGELOG.md](CHANGELOG.md) Block I.

- 📋 **SH147** (deps: SH88 ✅) **Commit-ordered change feed** — SH51 stamps the audit row inside the writer's transaction, so a slow commit can land an older timestamp behind a cursor that already passed it, and for a *sync* feed that is a lost edit rather than a late one. The 1s lag narrows the window; only commit-ordered sequencing closes it. **Scoped in July 2026 and deliberately not started:** "assigned at commit" does not exist in JPA (`beforeCommit` still interleaves), the working shape is a `seq` assigned inside the transaction plus a reader that **stops at a gap**, and the blocker is that a portable monotonic non-PK column across H2/MariaDB/PostgreSQL can only be *proved* by the SH26 Testcontainers ITs — which need Docker. Landing the changeset without them would repeat the SH2/SH5 drift. → §X.3
- 📋 **SH210** (deps: SH145 ✅) **Editing an asset's bytes in place does nothing** — `push --content` resolves a `File` path against the server's inventory and reuses the id when the path is already there, so replacing `shio/assets/Blog/media/logo.png` with different bytes uploads nothing and reports success. It is the one silent no-op left in the projection, and it is the *common* edit for an image. The fix needs both halves: a bytes fingerprint in the base snapshot to notice the change, and a way to land it — an upload always creates a new id-keyed post, so either the reference is repointed (cheap, leaves the old bytes) or SH38 grows a replace-bytes path. → §X.13
- 📋 **SH211** (deps: SH137 ✅, SH149 ✅) **Renaming a folder marker's directory is a delete plus a create, and the delete takes the posts** — the exact hazard SH150 fixed for posts, one level up and worse: `reconcileFolders` sees a marker gone and a marker arrived, the push declares the new folder, and `--prune` deletes the old one — which cascades to every post beneath it. `folder.move` (SH137) already exists and the document has a `moves` section (SH150); this is pairing them for the folder side. Until it lands, renaming a marker directory with `--prune` is destructive. → §X.14
- 📋 **SH212** (deps: SH99 ✅, SH145 ✅) **`shio verify` cannot see a broken asset path** — a `File` field naming an asset that is not in the tree is caught by `push`, which is the last gate rather than the first: the documented CI chain runs the content gates *then* `verify`, so the cheapest check reports it last. The tree's asset paths are checkable without a server at all, which makes this a lint rule over the projection — and the first one that would need `verify` to read local files. → §X.15
- 💭 **SH213** (deps: SH145 ✅) **Every push reads the whole media inventory** — `readRemoteAssets` lists every file in each site and joins the sitemap to it, on every push that declares any `File` field, even when no asset changed. A media-heavy site pays for its whole library to resolve one reference. The listing has filters (`q`, `folderId`) that a per-path lookup could use instead, and a missing folder currently re-reads the entire inventory a second time. → §X.16
- 💭 **SH214** (deps: SH150 ✅) **A probable-move warning cannot be accepted** — `push --content` prints "this looks like a rename it cannot prove" every run for a legitimate delete-plus-create, and there is no way to say "yes, these really are two different posts". SH142 solved exactly this shape for `verify` findings with a baseline file of accepted triples; the same mechanism fits, and without it the warning is one people learn to scroll past. → §X.17
- 💭 **SH215** (deps: SH148 ✅) **A fingerprint collision is undetectable and unreported** — the merge base compares 64-bit hashes, so two different values that collide read as "this field did not change" and the edit is dropped silently. The risk is remote and the trade was deliberate, but the *asymmetry* is not: a sync that loses an edit should be able to say so. Storing a length or a second short hash alongside would make a collision a reported conflict rather than a silent skip. → §X.18

## Block J — Blueprints (appliable units) + Claude Code plugin

> "Skip steps with things already done" only works if the ready-made thing is
> **appliable**, not readable. **Absorbs the old SH23 epic** (SH27–SH31): a
> human-browsable template catalog becomes an agent-appliable blueprint registry. → §XI

> ✅ **Block J shipped (SH92–SH97, SH119, SH151–SH156, SH166)** → see [CHANGELOG.md](CHANGELOG.md) Block J.

- 💭 **SH217** (deps: SH96 ✅) **The community half of the registry** — SH96 shipped the local trust model (an operator names a directory; collisions refuse, origins are reported). What is still missing is how a package a *stranger* wrote earns that place: a `shio-blueprint-*` naming convention, a curated index, and provenance stronger than "somebody put it there" (absorbs old SH28/SH29/SH31). → §XI.5
- 📋 **SH216** (deps: SH156 ✅) **`blog`, `docs` and `product-catalog` carry an index page** — SH156 gave the two section packages a front end and deliberately left these three out: they do not compose sections, so the section overlay would ship them a dependency they never import. What they actually lack is a *listing* page over their own content (`/blog`, the docs nav, the catalogue grid), which is per-package and needs the SDK's `query` rather than the section dispatcher. → §XI.11
- 📋 **SH218** (deps: SH119 ✅, SH156 ✅) **A release leaves the generated packages stale, and the next CI run finds out** — `publish-js-sdk.yml` rewrites `create-shio-app/template/package.json`'s dependency ranges and commits, but never re-runs `emit`. The three generated `blueprints/*/files/package.json` keep the previous range, so the drift test fails on the *next* push, attributing a release's side effect to whoever pushed after it. Run `emit` in the bump step and commit the packages with it. → §XI.12
- 📋 **SH219** (deps: SH96 ✅) **The local package directory needs a reload that is not a restart** — `ShBlueprintRegistry` caches on first access, which was free when the classpath was the only source and packages could not change while running. Now an operator can drop a package into `shio.blueprint.path` and nothing sees it until the process restarts, which for a directory whose whole purpose is local iteration is the wrong default. Wants an explicit, `ROLE_ADMIN` reload rather than a watcher — a re-scan that ran itself would re-run validation at an unpredictable moment and could take the registry down between two applies. → §XI.13
- 📋 **SH220** (deps: SH155 ✅) **A re-apply defaults to the parameters the site was built with** — SH155 records name, version and parameters as an SH114 note, which makes "which blueprint is this site" answerable; it does not yet make the next apply cheaper. Re-applying still means re-supplying every `--param`, and a value the operator misremembers silently rebuilds the site differently. The record is already there and already parseable; what is missing is `shio apply --blueprint <name> --site <site>` reading it and saying which values it defaulted. → §XI.14
- 💭 **SH221** (deps: SH153 ✅) **The rest of the write surface should be plannable read-only too** — SH153 removed the last write from a blueprint plan, and the property is worth generalising: `POST /agent/plan` and a `dryRun` batch are already write-free, but nothing *enforces* it, so the next feature that needs to resolve something it would create can reintroduce a rollback-only transaction without a test noticing. A `readOnly` transaction around the dry-run paths would make it structural rather than remembered — the same argument `ShByteStabilityLintTest` makes for `Map.copyOf`. → §XI.15

## Block L — Agent safety & human-in-the-loop

> ✅ **SH106–SH109 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block L. SH110 remains, blocked on SH45.

> ✅ **SH200 and SH201 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block L.

- 💭 **SH110** (deps: SH45, SH106 ✅) **Agent budget guards** — per-session call/write/storage ceilings, layered on SH45's per-tenant metering. Still blocked on SH45: building the ceiling first means a second metering mechanism to reconcile later. A runaway agent should hit a ceiling and report it, not a bill. → §XII.5
- 📋 **SH222** (deps: SH55 ✅, SH200 ✅) **An agent can fill the trash and cannot read it** — `post.delete` and `folder.delete` both answer "to trash (restorable)", and that is the only place the trash appears on the agent surface: there is no `/agent` read, no MCP tool and no `shio` command that lists or restores it. So the undo the protocol *advertises* is reachable only by a human in the console, which inverts P1 on the one capability an agent most needs after a mistake — and makes the reassurance in the change set something the caller cannot act on. → §XII.6
- 📋 **SH223** (deps: SH38 ✅, SH51 ✅, SH200 ✅) **Deleting a site is SH200 one level up, and worse** — `ShSiteWriteService.delete` removes the site entity and lets the JPA cascade take its folders, so every post in the site skips the trash, the audit, the change feed and the `DELETE` lifecycle event exactly as a folder delete used to. It is the largest blast radius on the surface and the only one with no record of what it destroyed. The fix is the same delegation SH200 made, applied through the folder writer rather than a third cascade. → §XII.7
- 📋 **SH224** (deps: SH55 ✅, SH200 ✅) **A permanent deletion is the one write nobody records** — `purge` fires the `DELETE` lifecycle event so the webhook and SH38's byte cleanup run, and writes no SH51 audit row at all. "Who destroyed this, and when" is therefore unanswerable for the *irreversible* half of the trash, while the reversible half is fully recorded. A folder purge can now take forty posts in one call, which is what makes the gap worth closing. → §XII.8
- 📋 **SH225** (deps: SH54 ✅, SH201 ✅) **The ACL check depends on `open-in-view` and does not say so** — `requireRead`/`requireWrite` are unannotated and reach `canRead`/`canWrite` by self-invocation, so the `@Transactional` proxy is bypassed and the folder-chain walk runs with whatever session the request happens to have. In the app that is `spring.jpa.open-in-view`; anywhere else — a scheduled job, an internal call, a test — it is a `LazyInitializationException` *instead of* a permission decision. A guard that fails open-or-explodes depending on a setting whose purpose is to be turned off is not a guard. → §XII.9
- 💭 **SH226** (deps: SH200 ✅) **A trashed folder is excluded by convention, not by invariant** — the exclusion lives in `ShFolderRepository`'s finders, and ids also resolve polymorphically through `ShObjectRepository`, which knows nothing about it. SH200 guarded the three paths that matter by hand; the next read that resolves a folder id there will silently show deleted content. This is the SH128 `ShFolderSite` shape exactly, and that one earned a lint test for the same reason. → §XII.10

## Block M — Token economy (measured)

> ✅ **Block M shipped in full (SH112–SH116, SH127, SH157–SH161, SH174)** → see [CHANGELOG.md](CHANGELOG.md) Block M.

- 📋 **SH227** (deps: SH132 ✅, SH157 ✅) **`shio_apply` cannot ask for its own proof** — `shio_write` took SH157's `verify` argument for ~15 tokens, and the desired-state tool could not: at 457 measured against a 460 per-tool ceiling there is no room, so the write most likely to want a lint (a whole section at once) is the one that must still spend a second call on it. The fix is a trim, not a raise — the ceiling exists to force exactly this decision. → §XIII.7
- 📋 **SH228** (deps: SH157 ✅) **A write proves only its posts** — the SH157 proof lints the `post:` addresses a run touched and silently ignores the rest, so a batch of `folder.upsert`s comes back with no proof at all rather than with the empty-folder warning the lint already knows how to produce. An agent that scaffolds a structure and reads `proof: absent` learns nothing, and cannot tell that from "nothing was wrong". → §XIII.8
- 📋 **SH229** (deps: SH116 ✅, SH159 ✅) **Nothing checks that an ETag covers every parameter that changes the bytes** — SH157 found `include` missing from the context pack's tag, three blocks after SH116 wrote down the rule it breaks: two projections of one unchanged pack shared a tag, so `include=model` could revalidate against a full pack's cache. SH174 then had to re-derive the same guard by hand for the manifest. This is the SH136 shape exactly — an invariant about how code is *written* — and it wants the same answer, a lint, not another careful reviewer. → §XIII.9
- 💭 **SH230** (deps: SH174 ✅) **The manifest's weight is prose, and terse barely touches it** — `format=terse` measured 2088 against JSON's 2343, an 11% saving where the same encoding is worth a third to a half everywhere else, because ~87% of the document is a purpose sentence and a runnable example per endpoint and both are kept verbatim on purpose. `include=features,limits` (298) is the real lever and it only helps a caller that already knows what it wants. Worth deciding whether the index can be earned rather than paid: derivable examples elided, or the index behind its own `include`. → §XIII.10
- 💭 **SH231** (deps: SH161 ✅) **An instance's own notes travel nowhere** — SH161 made a *site's* notes ride its export, and deliberately left the instance-wide ones behind, because importing another installation's house rules alongside somebody's blog is a P7 surprise. That reasoning is right for a site import and wrong for a whole-instance migration, which is the case where those notes are most valuable and least reproducible. The tenant export is the place; the decision is what an import does with them. → §XIII.11
- 💭 **SH232** (deps: SH159 ✅) **`/agent/changes` cannot revalidate the way it is most often called** — in cursor mode an unchanged range is byte-identical and free, which is the SH88 walk working as intended. In window mode the response's own cursor is the moving edge of the feed, so no two calls agree and every poll pays in full. That is *correct* — the answers really do differ — but the window is what a session opens with, and it is the read most likely to be repeated by a client that has not kept a cursor. → §XIII.12

## Block N — Realignment of what already shipped

> ✅ **Block N shipped (SH117, SH119, SH120, SH162)** → see [CHANGELOG.md](CHANGELOG.md) Block N.

> ⏳ **SH238 is half shipped** → see [CHANGELOG.md](CHANGELOG.md) Block N: the fact the editor's
> save-back rests on is checked in a browser (a probe that fails only on the 403 symptom) and
> through the real filter chain, and the chain test was verified against the defect. Driving the
> editor *end to end* still needs a front end to embed — that is Block P, and the remaining task
> below is scoped to it now.
>
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
> **What finishing the block opened (SH235–SH240), and what is left.** Four came from following one
> thread each to its client — SH163's rename had no CLI verb and no guard on the delete it replaces
> (SH235, SH236), and SH165's paging quietly narrowed two things the browser used to do over the
> whole folder (SH237, SH240) — and all four have shipped, as has SH239, which corrected SH164's own
> numbers on the way past. **What is left is the one thing this repository cannot do yet**: driving
> the Universal Editor end to end needs a front end to embed, so SH238's remaining half belongs with
> Block P.

- ⏳ **SH238** (deps: SH233 ✅, SH182 ✅, Block P) **The Universal Editor is still not driven end to end** — the one fact its save-back rests on is now checked from a browser and through the filter chain (shipped, see CHANGELOG Block N), which is what SH233 needed. What is left is the editor itself: it embeds `site.url` in an iframe, so exercising the properties panel, the field round trip and the publish action needs a front end this repository cannot start yet. Pick it up with Block P's renderer; until then the probe's `skipped` reasons are the honest statement of what is uncovered. → §XIV.15



## Block O — The human half (the console the curator uses)

> ✅ **Block O shipped, follow-ups included (SH176–SH182, SH202, SH203)** → see [CHANGELOG.md](CHANGELOG.md) Block O.

- 📋 **SH241** (deps: SH182 ✅) **No CI workflow runs on the branch the work happens on** — `build.yml` and `codeql-analysis.yml` are `workflow_dispatch:`-only, and `console-check.yml` triggers on `push: branches: [main]` while the default branch is `2026.3`. So the Java suite never runs automatically at all, and the SH182 browser gate — built precisely because console defects answer `200` — never runs on a push. It is not a hypothetical: `pnpm run compile:build` was **already failing on the default branch** when Block O's follow-ups were picked up, and nothing had said so. → §XVI.1
- 📋 **SH242** (deps: SH241) **An unmet peer range is not a build failure** — a Dependabot bump put `monaco-editor` at `0.56.0` against `monaco-graphql@1.8`'s declared peer of `>= 0.20.0 < 0.53`, and pnpm resolved it, printed a warning nobody reads and failed nothing; 0.56's new `exports` map then made every `monaco-editor/esm/vs/…` specifier unresolvable and the console bundle undeliverable. Pinning to the `0.52.2` that `@graphiql/react` already depends on fixed it, but the *class* of defect — a bump landing a version the dependent does not support — is a **resolution** fact no test can see. `strict-peer-dependencies` turns it into the refusal it should be. → §XVI.2
- 💭 **SH243** (deps: SH55 ✅, SH202 ✅) **A trashed post still holds its friendly URL, and nothing knows it** — `ShPostUnified` carries `@SQLRestriction("deleted_date is null")`, so every free-URL check is blind to the trash: SH202's new one, SH139's move refusal, and the `duplicate-url` lint. Clone a page, delete the copy, clone again — the second copy takes `home-2` back, and `restoreFromTrash` clears `deleted_date` with no check at all, so untrashing the first recreates exactly the duplicate SH202 removed. SH236 hit this blind spot from the post-type side and answered it by counting trashed rows separately and naming them; the same shape applies, and a restore onto an occupied URL should be a teaching refusal, not a silent one. → §XVI.3
- 💭 **SH244** (deps: SH202 ✅) **Duplicating content is console-only** — copy/clone is `PUT /api/v2/object/copyto/{dest}` with no `batch` op, no MCP verb and no CLI path, which is law P1's "no feature whose only interface is the console". An agent duplicates a *post* by reading it and upserting the fields at a new address, so nothing is unreachable; a **folder** is the gap, because the one thing a copy buys is the subtree in a single call, and reproducing it means walking the tree and upserting every post. If it lands it is a `folder.copy` op, and SH202's naming and SH203's cycle guard are already the shared writer's, so it composes rather than forks. → §XVI.4
- 💭 **SH245** (deps: SH139 ✅, SH202 ✅, SH203 ✅) **Make "one writer per verb" a lint, not an expectation** — four times now the console's copy of a shared verb was the careless one: the move that re-slugged live URLs (SH139), the folder move with no cycle guard (SH137), the folder delete that skipped the trash and the audit (SH200), and the copy that minted duplicate URLs and did not terminate on its own subtree (SH202/SH203). Each was found by reading the console path while fixing the agent's, which is a discovery method that depends on somebody happening to look. The invariant is stated in `agents.md` and enforced nowhere: a write on `ShObjectAPI`/`ShFolderAPI` that mutates content should have to go through the writer service, the way `ShByteStabilityLintTest` and `ShFolderSiteInvariantTest` already fail the build on their own invariants. → §XVI.5

## Block P — The visible site (what the human actually looks at)

> The differentiator is **making something a person can see**. Until SH183 an agent could build
> a site's content and structure over the agent surface and there was no way to look at it: the
> preview minted a URL against `site.url` and 400'd without one, the Universal Editor is an
> `<iframe src={site.url}>`, and nothing in this repository rendered a page. → §XV
>
> ✅ **SH183, SH184 and SH188 have shipped** → [CHANGELOG.md](CHANGELOG.md) Block P:
> `/preview/{site}/{url}` composes page → layout → region with Handlebars.java and **no script
> engine on the classpath** (Nashorn is excluded from the dependency that would have
> reintroduced it), the three levels are system post-types created on demand, `sh-region`
> survives the render as SH185's annotation contract, `site.upsert` fills an empty URL and never
> replaces one somebody chose, a site with no front end is previewed by Shio itself instead of
> answering `400 site has no url`, and `may_publish` no longer 500s on a pre-SH107 row.
>
> ✅ **SH191 has shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: a `PageLayout`'s `HTML`,
> a `Region`'s `HTML`/`JAVASCRIPT` and a `Theme`'s `CSS` project as sidecar files beside the
> `.md`, named by a `files:` block, with the bytes verbatim. It opened **SH252** (still open —
> the widget catalogue is short a CSS entry) and **SH253**, which has ✅ **shipped**: the merge
> now happens in the projection's domain, so the body's CRLF normalization stops causing the
> drift it was written to prevent. Its durable lesson is in §XVI.8.
>
> **SH185 and SH248 have shipped too** — the Universal Editor has a counterpart at last
> (`shio-editor.js`, plus the field-level `sh-field`/`sh-post` annotations and a protocol
> written down in [specs/SH185-universal-editor-protocol.md](specs/SH185-universal-editor-protocol.md)),
> and a region's browser JavaScript now reaches the browser. What that leaves is the tasks
> below plus **SH247**, now down to `form` alone (✅ `getRelation` and `search` shipped — a page
> composed of SH95 sections renders in the previewer at last, which closes one concrete half of
> SH249), and ✅ **SH249** itself — the two composition paths are now one, and the decision is a
> document (see the shipped pointer below).

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

- 📋 **SH265** (deps: SH187 ✅) **A raster is still the only answer to most of "looks wrong"** — SH187 extracts the appearance surface the *document* carries, and the honest limit is that overlap, contrast, truncation, a broken grid and anything responsive need a layout engine. SH102's `shio snapshot` already rasterizes with an optional Playwright import, and SH249 made the input free (the renderer's own HTML), so the remaining question is not how but **who reads it and where it lives**: an image on the agent surface is tokens an agent mostly cannot use, and the pixel diff SH102 already computes is a *number* an agent can. So the shape to design is probably "snapshot the rendered HTML server-side and return the diff ratio plus a signed URL", which needs the SH25 signing path and a decision about whether an instance may run a browser at all — the reason SH102 kept Playwright an optional dependency of the CLI.
> ✅ **SH189 is complete (with SH267 and SH268)** → see [CHANGELOG.md](CHANGELOG.md) Block P: import teaches, is reachable by a stateless caller, classifies from a kind rather than from prose, and `shio import` accepts a package — the MCP half is CLI-shaped by construction and the P1 inventory says so.
- 💭 **SH284** (deps: SH282 ✅, SH283 ✅) **Nothing in CI would have caught either measurement defect, because nothing in CI runs** — SH282 and SH283 were both artefacts of a *local* working tree, so a clean-room build is the only thing that can tell a real green from a local one, which sharpens **SH241**'s justification (filed as "the Java suite never runs") rather than duplicating it. → §XVI.36
- 💭 **SH195** (deps: SH183 ✅) **Most layout changes are tokens, not structure** — `PT-THEME` carries `CSS` and `JAVASCRIPT` as opaque blobs, so "tighten the headings" or "change the accent colour" is a stylesheet rewrite with nothing to verify and nothing to diff. A declared token vocabulary (spacing, type scale, colour roles) makes the common change a field edit an agent can make safely and a human can review, and leaves raw CSS as the escape hatch rather than the interface. → §XV.13

> **Opened by building SH183 (SH247–SH251).** Four are the renderer's own unfinished edges;
> SH250 was the test suite, found the way SH246 was — by running it. ✅ Shipped: Mockito is a
> `-javaagent` instead of self-attaching, so the flake and the JDK deprecation both go.
>
> **Opened by building SH191 (SH252–SH253).** One is the widget catalogue being short an
> entry; the other is a normalization that causes the drift it was written to prevent, and it
> is the more interesting of the two — a rule stated in a comment, contradicted by a caller
> two files away, invisible to every test because no test uses a CRLF value.
>
> ✅ **Opened by building SH255 and both shipped (SH256–SH257)** → [CHANGELOG.md](CHANGELOG.md)
> Block P: an asset path can no longer navigate the filesystem, the table covers all six round
> trips, and a site name whose directory name would not be its name is refused instead of
> trimmed into a tree no later command can read. Durable lessons: §XVI.11–§XVI.12.
>
> **Opened by building SH257 (SH258).** That run found its third defect by reading a refusal's
> *message*, not from the table — which asserts "exact, or declared" and cannot tell a correct
> refusal from a confusing one.
>
> **Opened by shipping SH247's two helpers (SH259).** `search` with a template-supplied term is a
> tag page; a results page needs `?q=`, and that is a question about what a template may know
> about the request rather than about the helper.
>
> ✅ **SH251 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: `template-unknown-field` in
> `checks=content`, over the bindings rather than the pages. It opened ✅ **SH260** (five hardcoded
> copies of the helper catalogue — now one source, the engine) and ✅ **SH261** (a hash argument that
> is a field is checked too, which opened ✅ **SH273** — the grammar coverage is a table now,
> and writing it found `{{&TEXT}}` being dropped; that in turn opened ✅ **SH274** — the AST
> premise is asserted now, and holds; which opened **SH275**).
>
> ✅ **SH194 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: `?digest=true` on `/agent/batch` and
> `/agent/apply` returns the two SH187 hashes per **affected** page — which for a write to a shared
> template is SH193's graph, not the address written. It opened **SH266**, ✅ shipped in turn: the
> caller names the page count and the server clamps it, and ten is a measurement now (6 queries a
> page, SH264) rather than a number that read well.
>
> ✅ **SH187 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: the digest carries an `appearance`
> block (class inventory, unstyled classes, inline styles, images without size, external sheets)
> and a **second** hash, `appearanceDigest`, so a restyle and a structural break are
> distinguishable. `visualSnapshot` stays `false` and `appearanceDigest: true` joins it. It opened
> **SH265** (a raster is still the only answer to most of "looks wrong").
>
> ✅ **SH249 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P, and the decision is written down in
> [specs/SH249-sections-and-regions.md](specs/SH249-sections-and-regions.md): **two mechanisms, one
> composition path.** The digest reads the rendered page (`source: rendered`) and falls back to
> SH169's composition, labelled, for a page that cannot be rendered. **SH187 is now nearly free.**
> It opened **SH264**, ✅ shipped in turn → [CHANGELOG.md](CHANGELOG.md) Block P: 21 queries per
> page, and the same 21 for the tenth as for the first — nothing is reused. It retired SH307 and
> SH310 into one benchmark and opened **SH311**, ✅ shipped in turn: a sweep is 6 queries a page.
>
> ✅ **SH193 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: `ShTemplateGraph` inverts the
> presentation edges, `affects` rides `/agent/changes`, and `verify` warns on an unreachable
> region or an unbound layout. **Computed rather than extracted, which corrected §XV.11's own
> premise** — `ShReference` cannot hold a post-to-post edge, because a post is not an `ShObject`.
> It opened **SH263** (a duplicate template title resolves to whichever row comes back first) and
> **SH262**, ✅ shipped in turn → [CHANGELOG.md](CHANGELOG.md) Block P: `include=impact` on
> `/agent/read`. It opened **SH286** (a theme is a third edge), ✅ shipped in turn.
>
> ✅ **Opened by fixing SH253 and both shipped (SH254–SH255)** → [CHANGELOG.md](CHANGELOG.md)
> Block P. The same asymmetry was in the notes file and *worse* there (a permanent conflict, not
> a permanent write); SH255 is the guard, an adversarial value table over all four round trips,
> which caught its own author's wrong declarations on the first run. Durable lesson: §XVI.10.
>
> ✅ **SH323 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P: the locale-keyed URL finder answers an
> ordered list instead of a 500, `duplicate-url` stays deliberately locale-blind (an SH79 address
> carries no locale), and `ShFurlReaderLintTest` refuses the next derived `Optional` over a `furl` —
> SH322's habit, applied.

- ⏳ **SH247** (deps: SH183 ✅) **`form` is the last of the seven capabilities** — ✅ `getRelation` and `search` shipped → [CHANGELOG.md](CHANGELOG.md) Block P. What remains is `form`, and it is filed rather than half-built because the missing piece is not code: a form needs a decision about **where a submission goes** — storage, spam, notification, retention — which is a subsystem, and `shFormComponent` was the one of the three removed components that had no obvious replacement. Until then `{{#form}}` is a teaching write-time refusal (SH192) naming SH247, which is the honest state: the author is told the capability is absent instead of shipping a page with a silently missing block. → §XV.14
- 💭 **SH312** (deps: SH264 ✅) **The benchmark measures one site's shape, and a real site is not this shape** — the SH264 fixture is one layout, two regions, one theme, ten pages, and no `{{#query}}`. That is enough to answer "is anything reused" and not enough to answer "what does a real sweep cost", because the helpers are the part that varies without bound: a listing region calls `{{#query limit=10}}`, which is a query per page *plus* the rows it reads, and a `{{#getRelation}}` composing sections reads one post per entry. So the **6** a swept page now costs (SH311) is a floor for the simplest possible page — and those helper reads are per-*page* by nature, since they depend on the page's own field values, so no resolution cache touches them. Worth extending the fixture with a listing region and a section-composed page and recording both numbers beside the current ones; caching *them* is a much harder question, because a listing's rows are content and a cache there is a freshness decision rather than a resolution one. → §XVI.19
- 💭 **SH316** (deps: SH314 ✅) **The graph is per binding and a site has one graph per call** — SH314 confirms the walk is bounded by bindings, and SH311 makes it nearly free inside one transaction. What neither addresses is that `ShTemplateGraph.of(site)` is called *per question*: `unusedIn` builds one, `duplicateTitlesIn` scans separately, `missingThemes` builds another, `impactOf` builds one per post, and `usedElsewhere` builds one per site per finding. Within a read-only transaction they now share the underlying template lists, so the cost is the assembly rather than the queries — but the shape is still "every caller asks the graph to rebuild itself", and the natural fix is the same one SH311 used one layer down: memoize the assembled `ShSiteTemplateGraph` for the length of the transaction. Small, and it wants a measurement first (the assembly may be free enough not to matter), which is why it is an idea and not a plan. → §XVI.50
- 💭 **SH317** (deps: SH315 ✅) **Every isolated test boots a Spring context of its own, and nobody has counted the cost** — SH315's rule is right and it has a price: a distinct `spring.datasource.url` means a distinct context cache key, so each of the twelve classes that now declare one starts its own application context. That is the largest fixed cost in the Java suite and it grows by one every time this rule is applied correctly. Two directions worth weighing rather than assuming: **share a context but not a database** (impossible today, since the URL *is* the key — it would need a per-test schema or a `@DirtiesContext`-free reset), or **accept it and measure it**, which at least tells the next person whether adding the thirteenth costs two seconds or twenty. The measurement is cheap (surefire already prints per-class elapsed time), and it belongs beside SH283's lesson that the suite's own numbers must come from the run rather than from `target/`. → §XVI.57
- 💭 **SH319** (deps: SH266 ✅) **A clamped count is invisible unless the answer happens to be cut** — `?digest=200` on an instance whose ceiling is 100 returns a hundred rows, and if the write affected ninety pages it returns ninety and says nothing: the caller asked for two hundred, got ninety, and cannot tell whether that is all there was or the instance disagreed with it. The cut row only appears when the *pages* run out, not when the *request* was reduced. Two candidate fixes and the cheap one is probably right: a `notes` line when the requested count exceeded the ceiling (the SH146 shape — report the narrowing once, not per row), or echoing the effective count in the response. The manifest already reports the ceiling, so this is only about the caller that did not read it — which, on a surface designed for agents that read the manifest first, may be a small enough population to leave alone. Worth deciding rather than defaulting. → §XVI.18
- 💭 **SH322** (deps: SH279 ✅) **Nine call sites shared a defect and the roadmap entry listed three** — SH279 was filed from a first read that named `ShAddressResolver`, `ShStaticFileService` and "whatever a grep turns up". The grep, written as a lint, found **nine** — including one the *previous half of the same task* had created. That gap is not a documentation failure; it is evidence that "find the other call sites" is a job for an executable pattern rather than a reading, and this repository already knows it in three other places (`ShByteStabilityLintTest`, `ShFolderSiteInvariantTest`, `ShAgentIncidentFramesTest`). What is missing is the habit written down: **when a task's body says "and whatever a grep turns up", the grep is the deliverable, and it lands as a lint before the fixes do.** Cheap to state, and it changes the order of work rather than adding any. → §XVI.23
- ⏳ **SH324** (deps: SH321 ✅) **A test fixture can model a shape the product never produces, and nothing says so** — The premise is asserted (publish keeps the draft's id, so one page is one id and one furl); what remains is the choice of instrument, since the cheap one is not cheap once it has to be sound. → §XVI.31
- 💭 **SH293** (deps: SH290 ✅) **The reference model has thirteen types and three are checked** — `ShRenderModelSourceTest` compares `PageLayout`, `Region` and `Theme` against `import/post-types.json` because those are the three SH183 transcribed. The file also declares `Text`, `Photo`, `Video`, `Quote`, `Links`, `File`, `TextArea`, `Article`, `Alias` and `FolderIndex` — several of which this codebase still creates on demand (`File`, SH38) or still names in `ShSystemPostType` while nothing builds them. So the question the new test answers for three types is unanswered for ten, and it splits in two: which of those the running system still provisions (and should therefore be compared the same way), and which are names left in a constants class from 2019 with no creator, which is `ShWidget`'s problem (SH285) one class along. Worth doing as an audit that produces either a comparison or a deletion for each, rather than extending the test blindly. → §XVI.45
- 💭 **SH294** (deps: SH290 ✅) **"Imports and renders without a migration" is still only a comment** — SH290 asserts the *model* matches its 2018 source, which is the half that was wrong. The claim the comment actually makes is larger: that a real `openviglet/bootstrap-site` export **renders**. Nothing exercises that end to end, and the export bundled in this repository cannot: it declares one post-type (`Page`), no `postTypeLayout` and no template posts, so the SH71 starter is not a rendering site at all. What the claim needs is a fixture that *is* one — a small package carrying a `PageLayout`, a `Region` and a bound `postTypeLayout` in the 2018 shape — imported through `POST /api/v2/import` and then rendered through `/preview`, which would also give SH189's import path and SH280's unpublished-layout refusal their first shared test. The reason it is an idea rather than designed is the fixture: hand-writing a 2018-format package risks asserting against a shape the 2018 exporter never produced, so the honest source is a real export, and finding one is the task's first step. → §XVI.46
- 💭 **SH295** (deps: SH289 ✅) **Nothing tells an operator that their instance needs reconciling until a template is already broken** — `untitled-template` is an error on a *post*, so it fires once somebody has written a template that cannot be addressed; the drift itself (a render post-type missing a field the shipped model declares) is invisible until then, and a fresh instance that has run `ensure` but written nothing is indistinguishable from a stale one. What is missing is the drift as a **fact about the instance**, and there are two plausible homes and one non-home: the SH77 context pack's `conventions` (where SH272 just put the helper set, and where an agent would read it before writing a template — but every session pays for a line most instances do not need), or a startup log line (free, and nobody reads logs — SH103's own argument), and *not* a `verify` finding, because a post-type has no SH79 address, which is the constraint that shaped SH289 in the first place. Worth settling with it: whether `GET /api/v2/render/post-types` should report the drift beside each type, since that call already answers "can this instance render" and a caller asking it is exactly the one who needs to know. → §XVI.47
- 💭 **SH296** (deps: SH289 ✅) **A `PER_CLASS` fixture has now hidden three defects, and nothing warns** — SH239 (a benchmark pair writing into the folder another pair measured), SH272 (a test asserting "this instance has no templates" that was really asserting what ran first) and SH289 (an additive-only assertion that survived a mutation making the code overwrite everything, because a sibling test had already repaired the fixture). Each was found by accident, and the third only because the fix was verified by mutation rather than by a green run. The shape is always the same: a mutating test leaves state that makes a later assertion vacuous. Options worth weighing rather than one obvious answer — a convention that a mutating test *forces* its precondition instead of creating it once (what SH289 ended up doing, and cheap); `@TestMethodOrder(Random)` so an order dependence fails loudly instead of quietly (noisy, and a flake is worse than a break per SH250); or a lint that flags a `PER_CLASS` class whose tests write through a shared service. The likeliest real answer is the first plus writing it down, since the other two cost more than the defect. → §XVI.48
- 💭 **SH288** (deps: —) **The CLI cannot read a post** — `shio` has `resolve`, `digest`, `verify`, `changes` and `context`, and no way to see one post's content: the file projection (`shio pull --content`) is the answer for a *tree*, and there is nothing for "show me this one address" — which is what a human debugging an agent's work actually types, and now also the only path to SH262's blast radius that a terminal user does not have. It is not obviously worth a command: `--content` already puts every post on disk where `grep` is cheaper (P4), which is the argument that the gap is deliberate. What is *not* covered by that argument is `impact`, since the projected file holds none of it — so the smaller shape may be `shio impact <address>` rather than a general `read`. Decide which before building either. → §XVI.40
- 💭 **SH285** (deps: SH252 ✅) **`ShWidget.implementationCode` points every widget at a directory that does not exist** — every seeded widget carries `template/widget/<name>.html`, a Thymeleaf template path from the UI Block E retired; the directory is absent from the repository and nothing reads the column. It is still persisted, still returned by `GET /api/v2/widget`, and still **writable** through `ShWidgetAPI`, so it is a field a caller can set that cannot affect anything — the kind of surface that invites somebody to believe a widget is extensible by pointing it at a template. Removing it is a Liquibase drop plus the DTO and the seed; the reason it is an idea rather than designed is that `className` beside it (`com.viglet.shio.widget.ShAceEditorWidget`) needs the same question asked, and those classes may or may not still exist — so the task is "audit what `ShWidget` still carries" rather than "drop one column". → §XVI.37
- ⏳ **SH275** (deps: SH274 ✅) **The load-bearing dependency premises are asserted; the rest are declared** — ✅ the two that pass the bar are done → [CHANGELOG.md](CHANGELOG.md) Block P: an unconfigured Handlebars *does* walk a bean and *does* resolve a partial to a classpath path, so SH183's two guards are provably guarding something. What remains is the judgement pass over the rest of §XVI.27's table — deciding which are load-bearing rather than asserting all of them — plus the finding that `{{secret.class.name}}` is blocked by the library itself, so half of one existing assertion proves less than it appears to. → §XVI.27
- 💭 **SH346** (deps: SH332 ✅) **The op vocabulary is MCP-only, which is the P1 shape this repo refuses** — `shio://ops` is a resource, so an HTTP-only agent cannot read the one document that says which keys a write takes. The rendering is a plain string; which REST surface carries it is the decision (a manifest section costs the SH113 budget every session, a context-pack section moves the SH116 digest). → §XVI.62
- ⏳ **SH341** (deps: —) **The roadmap carries prose it is not the home for** — ✅ two halves done: SH325–SH340 compressed to a sentence each, and nine shipped-history bodies collapsed to the pointer line the rules prescribe (~2.6k words; every dropped id verified present in CHANGELOG first). **What remains:** the other 79 task lines (avg 113 words against a one-sentence rule) need their `→ §` coverage checked before cutting, since some may be the only home for their reasoning; three mixed blockquotes (Block P's L537 shape) interleave shipped notes with open-task provenance and want hand treatment; then a length assertion, so the rule stops depending on the writer noticing it. → §XVI.60
- 💭 **SH276** (deps: SH275 ⏳) **A premise about a dependency we *do not* have is unfalsifiable, and one is load-bearing** — SH75 is hand-rolled MCP because "the MCP Java SDK is Jackson-2/Boot-3 while this app is Jackson-3/Boot-4". That claim justifies ~1500 lines of protocol code and cannot be asserted the way SH274's was, because the artefact is not on the classpath — a test would have to add the dependency to check whether it still conflicts, which is the change it is trying to evaluate. So the honest instrument is different: a dated note recording the SDK version checked and the conflicting coordinates, refreshed when somebody has reason to look, plus the SH132 tool-count and budget tests standing as the real argument (a hand-rolled surface that stays inside its budget is evidence the trade is still good). Worth doing because this is the largest inherited premise in the repository by lines of code it defends, and it is currently a sentence. → §XVI.28

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

  - 💭 **SH44** (deps: SH22 ✅) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
  - 💭 **SH45** (deps: SH22 ✅) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
  - 💭 **SH46** (deps: SH22 ✅) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
  - 💭 **SH47** (deps: SH22 ✅, SH38 ✅) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

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
