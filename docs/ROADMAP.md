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
- 💭 **SH140** (deps: SH100 ✅, SH134 ✅) **Report inbound links when a post moves** — A URL typed into another post's rich text names the address rather than the object, so a move breaks it and nothing tells the agent which pages it just broke. → §VIII.13
- 📋 **SH196** (deps: SH88 ✅, SH137 ✅) **No folder write is audited** — No folder create, rename, move or delete is recorded, so the feed shows posts appearing in folders it never saw created. → §VIII.12
- 💭 **SH197** (deps: SH136 ✅) **Finish the byte-stability class** — A hash-ordered Map.of reaching a serialized map component is the SH136 defect the lint does not ban, and no live instance is affected yet. → §VIII.11

## Block K — Perception & verification loop

> Two of the three human round trips in a typical build exist only because the
> agent cannot check its own work. This block is the cheapest large win after H. → §IX

> ✅ **Block K shipped (SH99–SH104, SH141–SH144, SH154)** → see [CHANGELOG.md](CHANGELOG.md) Block K.

- 💭 **SH204** (deps: SH144 ✅) **An incident should name its site where one is knowable** — An incident carries no site, so a site-scoped read of the feed answers with the instance's failures or none, and two of the four recorders already know the site. → §IX.5
- 💭 **SH205** (deps: SH144 ✅) **The incident fold key is the human message, so one problem becomes many rows** — The fold key includes the human message, whose varying detail turns one problem into a row per variant and defeats the folding the durable table depends on. → §IX.6
- 💭 **SH206** (deps: SH141 ✅) **The delivery proof fetches inside the report's read transaction** — A delivery proof fetches front ends while holding the report's pooled connection, which SH141 bounded rather than fixed. → §IX.7
- 📋 **SH207** (deps: SH104 ✅) **The handoff has no console banner** — The handoff exists only as a PR-body artifact, so a curator who never opens a terminal cannot see what an agent did since they last looked. → §IX.8
- 📋 **SH208** (deps: —) **A backtick in the CLI's help text breaks the whole binary** — HELP is a template literal, so a backtick in the prose is a syntax error that stops the binary loading, and only the three tests that spawn it notice. → §IX.9
- 💭 **SH209** (deps: SH102 ✅) **`shio snapshot` photographs one page per run** — The useful artifact for a handoff is the set of a site's pages, and the command captures one page per run. → §IX.10


## Block I — Content as files (local-first authoring)

> An agent is far cheaper and more reliable with `Edit`/`Write`/`Grep` over a text
> tree than with read-modify-write over HTTP. Project content to disk, reconcile
> with the server. Largest cycle reducer in the proposal. → §X

> ✅ **Block I shipped bar SH199 (SH86–SH90, SH145–SH150, SH210–SH215)** → see [CHANGELOG.md](CHANGELOG.md) Block I.

- 📋 **SH147** (deps: SH88 ✅) **Commit-ordered change feed** — SH51 stamps the audit row inside the writer's transaction, so a slow commit can land behind a cursor that already passed it, which for a sync feed is a lost edit. → §X.3
- 📋 **SH210** (deps: SH145 ✅) **Editing an asset's bytes in place does nothing** — push --content reuses a File id when the path already exists, so replacing an asset's bytes uploads nothing and reports success — the projection's one silent no-op. → §X.13
- 📋 **SH211** (deps: SH137 ✅, SH149 ✅) **Renaming a folder marker's directory is a delete plus a create, and the delete takes the posts** — reconcileFolders reads a renamed marker as one folder gone and another arrived, and --prune deletes the old one, cascading to every post beneath it. → §X.14
- 📋 **SH212** (deps: SH99 ✅, SH145 ✅) **`shio verify` cannot see a broken asset path** — An asset path a File field names is checked by push, the last gate, when the tree makes it checkable by verify, the first. → §X.15
- 💭 **SH213** (deps: SH145 ✅) **Every push reads the whole media inventory** — readRemoteAssets lists every File post in a site to resolve one reference, on any push that declares a File field. → §X.16
- 💭 **SH214** (deps: SH150 ✅) **A probable-move warning cannot be accepted** — A legitimate delete-plus-create prints an unprovable-rename warning on every run, and there is no way to accept it the way a verify finding can be. → §X.17
- 💭 **SH215** (deps: SH148 ✅) **A fingerprint collision is undetectable and unreported** — The merge base compares 64-bit hashes, so a collision reads as an unchanged field and the edit is dropped with nothing said. → §X.18

## Block J — Blueprints (appliable units) + Claude Code plugin

> "Skip steps with things already done" only works if the ready-made thing is
> **appliable**, not readable. **Absorbs the old SH23 epic** (SH27–SH31): a
> human-browsable template catalog becomes an agent-appliable blueprint registry. → §XI

> ✅ **Block J shipped (SH92–SH97, SH119, SH151–SH156, SH166)** → see [CHANGELOG.md](CHANGELOG.md) Block J.

- 💭 **SH217** (deps: SH96 ✅) **The community half of the registry** — The local trust model is shipped, and nothing says how a package a stranger wrote earns a place in it. → §XI.5
- 📋 **SH216** (deps: SH156 ✅) **`blog`, `docs` and `product-catalog` carry an index page** — Three starters ship an index page and no listing over their own content, which is what a reader needs and what the section overlay deliberately does not give them. → §XI.11
- 📋 **SH218** (deps: SH119 ✅, SH156 ✅) **A release leaves the generated packages stale, and the next CI run finds out** — The release workflow rewrites the template's dependency ranges without re-running emit, so the generated packages keep the old range and the next push finds out. → §XI.12
- 📋 **SH219** (deps: SH96 ✅) **The local package directory needs a reload that is not a restart** — ShBlueprintRegistry caches on first access, so a package dropped into the configured directory is invisible until the process restarts. → §XI.13
- 📋 **SH220** (deps: SH155 ✅) **A re-apply defaults to the parameters the site was built with** — The record of a site's blueprint parameters exists and no re-apply reads it, so a value the operator misremembers rebuilds the site differently and reports ordinary work. → §XI.14
- 💭 **SH221** (deps: SH153 ✅) **The rest of the write surface should be plannable read-only too** — The plan and dryRun paths are write-free and nothing enforces it, so the next feature needing to resolve what it would create can reintroduce a write. → §XI.15

## Block L — Agent safety & human-in-the-loop

> ✅ **SH106–SH109 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block L. SH110 remains, blocked on SH45.

> ✅ **SH200 and SH201 shipped** → see [CHANGELOG.md](CHANGELOG.md) Block L.

- 💭 **SH110** (deps: SH45, SH106 ✅) **Agent budget guards** — A runaway agent should hit a per-session ceiling and report it rather than a bill, and the metering it must layer on is SH45's. → §XII.5
- 📋 **SH222** (deps: SH55 ✅, SH200 ✅) **An agent can fill the trash and cannot read it** — Two delete ops promise a restorable trash, and no agent read, MCP tool or CLI command can list or restore it. → §XII.6
- 📋 **SH223** (deps: SH38 ✅, SH51 ✅, SH200 ✅) **Deleting a site is SH200 one level up, and worse** — Deleting a site cascades through JPA, so every post in it skips the trash, the audit, the feed and the DELETE event. → §XII.7
- 📋 **SH224** (deps: SH55 ✅, SH200 ✅) **A permanent deletion is the one write nobody records** — purge fires the lifecycle event and writes no audit row, so the irreversible half of the trash is the one nobody can attribute. → §XII.8
- 📋 **SH225** (deps: SH54 ✅, SH201 ✅) **The ACL check depends on `open-in-view` and does not say so** — requireRead and requireWrite reach the folder-chain walk by self-invocation, so the transaction is whatever the caller happened to have — open-in-view in the app, nothing elsewhere. → §XII.9
- 💭 **SH226** (deps: SH200 ✅) **A trashed folder is excluded by convention, not by invariant** — The trashed-folder exclusion lives in one repository's finders, and ids also resolve polymorphically through another that knows nothing about it. → §XII.10

## Block M — Token economy (measured)

> ✅ **Block M shipped in full (SH112–SH116, SH127, SH157–SH161, SH174)** → see [CHANGELOG.md](CHANGELOG.md) Block M.

- 📋 **SH227** (deps: SH132 ✅, SH157 ✅) **`shio_apply` cannot ask for its own proof** — At 457 tokens against a 460 ceiling, the write most likely to want a lint is the one that cannot ask for one. → §XIII.7
- 📋 **SH228** (deps: SH157 ✅) **A write proves only its posts** — The write proof lints the post addresses a run touched and ignores the rest, so a batch of folder upserts comes back with no proof at all. → §XIII.8
- 📋 **SH229** (deps: SH116 ✅, SH159 ✅) **Nothing checks that an ETag covers every parameter that changes the bytes** — An ETag that omits a parameter which changes the bytes lets one projection revalidate against another's cache, and nothing checks the coverage. → §XIII.9
- 💭 **SH230** (deps: SH174 ✅) **The manifest's weight is prose, and terse barely touches it** — About 87% of the manifest is a purpose sentence and an example per endpoint, kept verbatim on purpose, so terse saves 11% where it is worth a third elsewhere. → §XIII.10
- 💭 **SH231** (deps: SH161 ✅) **An instance's own notes travel nowhere** — A site's notes ride its export and the instance's own travel nowhere, which is right for an import and wrong for a move. → §XIII.11
- 💭 **SH232** (deps: SH159 ✅) **`/agent/changes` cannot revalidate the way it is most often called** — In window mode the response's own cursor is the moving edge of the feed, so no two polls agree and every one pays in full. → §XIII.12

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

- ⏳ **SH238** (deps: SH233 ✅, SH182 ✅, Block P) **The Universal Editor is still not driven end to end** — The one fact the save-back rests on is checked from a browser now; the editor itself embeds site.url in an iframe, so nothing drives the panel, the fields or the save. → §XIV.15



## Block O — The human half (the console the curator uses)

> ✅ **Block O shipped, follow-ups included (SH176–SH182, SH202, SH203)** → see [CHANGELOG.md](CHANGELOG.md) Block O.

- 📋 **SH241** (deps: SH182 ✅) **No CI workflow runs on the branch the work happens on** — Two workflows are dispatch-only and the third triggers on a branch this repository does not develop on, so nothing runs on a push. → §XVI.1
- 📋 **SH242** (deps: SH241) **An unmet peer range is not a build failure** — pnpm resolved a peer range it did not satisfy, printed a warning nobody reads and failed nothing, and the console bundle became undeliverable. → §XVI.2
- 💭 **SH243** (deps: SH55 ✅, SH202 ✅) **A trashed post still holds its friendly URL, and nothing knows it** — Every free-URL check reads through @SQLRestriction, so the trash is invisible to it and an untrashed post can collide with the name its own copy took. → §XVI.3
- 💭 **SH244** (deps: SH202 ✅) **Duplicating content is console-only** — Copy exists only as a console PUT, with no batch op, no MCP verb and no CLI path, and a folder is the case an agent cannot reproduce with upserts. → §XVI.4
- 💭 **SH245** (deps: SH139 ✅, SH202 ✅, SH203 ✅) **Make "one writer per verb" a lint, not an expectation** — Four times the console's copy of a shared verb was the careless one, and the rule that would have caught all four is stated in agents.md and enforced nowhere. → §XVI.5

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

- 📋 **SH265** (deps: SH187 ✅) **A raster is still the only answer to most of "looks wrong"** — SH187 reads what the document declares, and overlap, contrast, truncation and anything responsive need a layout engine the server has so far refused to run. → §XV.22
> ✅ **SH189 is complete (with SH267 and SH268)** → see [CHANGELOG.md](CHANGELOG.md) Block P: import teaches, is reachable by a stateless caller, classifies from a kind rather than from prose, and `shio import` accepts a package — the MCP half is CLI-shaped by construction and the P1 inventory says so.

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

- ⏳ **SH247** (deps: SH183 ✅) **`form` is the last of the seven capabilities** — What remains is form, and the missing piece is not code but a decision about where a submission goes: storage, spam, notification, retention. → §XV.14
- ⏳ **SH324** (deps: SH321 ✅) **A test fixture can model a shape the product never produces, and nothing says so** — The premise is asserted (publish keeps the draft's id, so one page is one id and one furl); what remains is the choice of instrument, since the cheap one is not cheap once it has to be sound. → §XVI.31
- 💭 **SH294** (deps: SH290 ✅) **"Imports and renders without a migration" is still only a comment** — The class comment claims a real 2018 export imports and renders, and nothing exercises it — the bundled starter declares no layout, so it cannot. → §XVI.46
- 💭 **SH276** (deps: SH275 ✅) **A premise about a dependency we *do not* have is unfalsifiable, and one is load-bearing** — It justifies ~1500 lines of hand-rolled protocol and cannot be asserted, because checking it means adding the dependency being evaluated. → §XVI.28
- 💭 **SH355** (deps: —) **Half-shipped entries are invisible to the tool that owns the files** — Four lint findings and one refused retire all trace to a ledger id written as SH96 (local half), which roadkeep cannot parse. → §XVI.65
- ⏳ **SH356** (deps: SH341 ✅) **The rationale file carries the debt the roadmap just shed** — The triage is decided and instrumented; 29 sections remain, each needing a reading to say whether it is a standing rule or history. → §XVI.66
- 💭 **SH357** (deps: SH354 ✅) **One diagnostics test reads an empty table once in several full runs** — ShAgentDurableIncidentTest.diagnosticsReadsTheTable expected a webhook incident and found none, on a run where nothing else failed and no code had changed. → §XVI.67

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
