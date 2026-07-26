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
> **SH145–SH150** from findings while implementing Block I —
> new tasks are expected to accrete this way, and each one's
> line says what measurement or failure produced it. Superseded and therefore retired: **SH17/SH18** (GraalJS), **SH23 +
> SH27–SH31** (marketplace → Block J), **SH24 + SH32–SH37** (CRUD-over-MCP → Block H).

---

# Part 1 — The agent-native reorientation (current focus)

> Blocks **H–M** implement the north star. Concept, protocol design and the full
> decomposition: [specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md).
> **SH74** is the epic (this concept) — it is complete when the three roadmap docs
> and `agents.md` carry the orientation; the code lands as SH75+.
>
> Suggested order: **H → K → I → J → L → M** (H, K and I are shipped). Block H is the protocol everything
> else speaks; Block K (verification) is next because it removes the most expensive
> unit in the system — the human round trip.

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
> invariant, written the first time the invariant is violated** — and the open items
> below are what it turned up next: the same move gap one level up (**SH137**), the same
> byte-stability question across the rest of the surface (**SH136**), and a truncation
> hazard that only exists because prune got more powerful (**SH138**).

- 💭 **SH84** (deps: SH83 ✅, SH22 ✅) **Turing ecosystem wiring** — the SH22 tenant sweep and its test shipped; what remains is the Turing integration (replaces old SH37). → §VIII.9
- 📋 **SH136** (deps: SH130 ✅) **Byte-stability guard for the whole agent surface** — SH130 pinned `tools/list` and immediately found it unstable across boots (`Map.of` iteration order). The manifest is byte-stable by test and the context pack by digest, but nothing checks the MCP resources, the prompt list, or any future map-backed payload. One lint (no un-canonicalized `Map`/`Set` reaching a JSON writer) plus a per-payload stability assertion. → §VIII.11
- 📋 **SH137** (deps: SH134 ✅) **`folder.move`** — posts can now be re-addressed; folders cannot. Renaming a section still means `folder.upsert` at the new path plus `folder.delete` of the old, and that delete cascades over every post beneath it. It is the same defect as SH134 one level up and strictly more destructive, and SH134 makes its absence conspicuous. → §VIII.7
- 📋 **SH138** (deps: SH135 ✅, SH78 ✅) **Never truncate a deletion plan** — SH135 widened prune to whole subtrees, so a plan can now list thousands of deletes and collide with the SH78 response budget. A truncated deletion plan is the most dangerous truncation in the system: it reads as a complete review of what will be destroyed. Either exempt destructive rows from the budget or refuse the apply, but never silently shorten the list. → §VIII.7
- 📋 **SH139** (deps: SH134 ✅) **The console move must not rewrite a post's URL** — `ShObjectWriteService#moveTo` resets `furl` to a slug of the title on every move, so dragging a published page between folders silently changes its live URL and discards any hand-set one. SH134 made the agent path careful about exactly this; the console path is the one that a human uses by accident. → §VIII.7
- 💭 **SH140** (deps: SH134 ✅, SH100) **Report inbound links when a post moves** — `ShReference` is id-keyed and survives a move, but a URL typed into another post's rich text does not, and nothing tells the agent which pages it just broke. The cheap version is a count in the change detail; the real one belongs with SH100's route verification. → §IX.2

## Block K — Perception & verification loop

> Two of the three human round trips in a typical build exist only because the
> agent cannot check its own work. This block is the cheapest large win after H. → §IX

> ✅ **The loop is shipped** → [CHANGELOG.md](CHANGELOG.md) Block K: **SH99** (content
> lint + the ninth tool `shio_verify`, and `contentLint` finally true) · **SH100**
> (route/link verification) · **SH101** (render digest + `shio_digest`) · **SH103**
> (agent diagnostics).
>
> **What is deliberately not in it, and why:** SH100 answers "internal links resolve"
> against the content graph rather than by crawling — a front end may not be running,
> may be a different deployment, and legitimately owns routes the CMS never sees. So
> `link-unresolved` is a *warning*, and closing that gap properly is **SH141**.
> Sitemap-coverage-vs-blueprint waits for Block J to have a blueprint to compare
> against. SH102's PNG stays an idea and is reported `false` in the manifest.
>
> **What building it opened.** The `image-no-alt` rule showed the report has no way to
> say "known and accepted" (**SH142**); the verify pass reads every post in the instance
> on every call, which is fine at a hundred posts and not at ten thousand (**SH143**);
> and the SH103 window is honest about being in memory, which is exactly the shape SH88
> already needs to fix (**SH144**).

- 💭 **SH102** (deps: SH101 ✅) **Visual snapshot + diff** — optional Playwright PNG + diff vs previous, for the human handoff rather than for the agent. → §IX.4
- 📋 **SH104** (deps: SH88 ✅, SH101 ✅) **Handoff report** — `shio report` renders the human-facing change summary (what changed, preview links, open questions) for a PR body or console banner. Unblocked by SH88: the cursor exists, so the summary can start where the last one ended instead of mid-story. → §IX.6
- 📋 **SH141** (deps: SH100 ✅, SH101 ✅) **Prove a route from the delivery side** — SH100 resolves links against the content graph, so a page that exists in the CMS and 404s on the site is still only a *warning*. SH101 already fetches one URL safely (origin-checked, opt-in); this walks the published set through the same path and turns "the front end does not serve this" into an error, with the front-end-owns-routes case declared rather than guessed. → §IX.2
- 📋 **SH142** (deps: SH99 ✅) **Baseline / accepted findings** — a verify report has two states, and real content has a third: known and accepted. Without it the first legitimate warning teaches everyone to ignore the exit code. A committed `shio/verify-baseline.json` (rule + address) that `shio verify` subtracts, and that fails when an accepted finding no longer exists — so the file cannot rot into a blanket suppression. → §IX.1
- 📋 **SH143** (deps: SH99 ✅, SH88 ✅) **Scope a verify run to what changed** — the pass reads every post in every readable site, which is right for CI and wrong for the call an agent makes after writing three pages. Needs `?folder=`/`?address=` scoping and `?since=` over SH88's cursor — plus an honest header saying the report covers a subset, because a partial report that reads as a full one is the SH138 hazard again. → §IX.1
- 💭 **SH144** (deps: SH103 ✅, SH88 ✅) **Durable incident feed** — the SH103 window is in memory and says so, which is the correct answer for now and the wrong one for "was anything failing while I was away". The durable form is the same cursor SH88 builds for content changes; folding incidents into it is cheaper than a second retention policy. → §IX.5

## Block I — Content as files (local-first authoring)

> An agent is far cheaper and more reliable with `Edit`/`Write`/`Grep` over a text
> tree than with read-modify-write over HTTP. Project content to disk, reconcile
> with the server. Largest cycle reducer in the proposal. → §X

> ✅ **The projection and its sync are shipped** → [CHANGELOG.md](CHANGELOG.md) Block I:
> **SH86** (projection format) · **SH88** (change feed + cursor, and `changeCursor`
> finally true) · **SH87** (`pull --content` / `push --content`) · **SH89** (three-way
> merge) · **SH90** (`shio dev --content` + the JSONL event log).
>
> **What is deliberately not in it, and why:** `shio/assets/` does not project — a
> binary's bytes are id-keyed on the server (SH38) and a projected `File` field still
> holds a UUID, so the tree is not yet self-contained (**SH145**). Conflicts are
> *reported*, never written as `<<<<<<<` markers: a marker in a field value is content,
> and the next push would send it to the server and the next publish would put it on the
> site. And a pull refuses to write a projection built from a truncated post list rather
> than let a later `--prune` read the gaps as deletions (**SH146** removes the cap).
>
> **What building it opened.** The projection made three gaps conspicuous: a file rename
> pushes as delete-plus-create instead of SH134's `post.move`, which loses the post's id
> and history (**SH150**); an empty folder cannot be authored at all, because folders are
> only implied by the posts inside them (**SH149**); and the merge base keeps a second copy
> of every field value when per-field digests would decide the same cases (**SH148**). The
> change feed's own honesty note is the fourth: the lag narrows the stamp-before-commit
> window, it does not close it (**SH147**).

- 📋 **SH145** (deps: SH86 ✅, SH38 ✅) **Project assets too** — `shio/assets/` with the bytes, and a `File` field that reads as a path rather than a UUID. Today a content tree cannot be moved to another instance or reviewed in a diff without the ids resolving to nothing, which is most of what "content as files" promises. → §X.6
- 📋 **SH146** (deps: SH83 ✅, SH88 ✅) **Page `find`** — the projection reads the post list through `find`, which caps at `shio.agent.read.max-limit` and has no cursor, so a site past the cap cannot be projected at all (pull refuses rather than silently omitting). The cursor SH88 built for changes is the shape to reuse. → §X.7
- 📋 **SH147** (deps: SH88 ✅) **Commit-ordered change feed** — SH51 stamps the audit row inside the writer's transaction, so a slow commit can land an older timestamp behind a cursor that already passed it. The 1s lag narrows that window; only commit-ordered sequencing (an outbox, or a sequence assigned at commit) closes it, and a feed used for sync has to be exact. → §X.3
- 💭 **SH148** (deps: SH89 ✅) **Digest-only merge base** — `shio/.shio/content-base.json` holds a full copy of every projected field. A per-field hash decides all three merge cases; only a genuine conflict needs values, and those can be re-read. Turns the snapshot from O(content) into O(fields). → §X.8
- 📋 **SH149** (deps: SH87 ✅) **Folders as files** — a folder exists in the projection only because a post names it, so an empty section cannot be authored locally and a pull silently drops one. Needs a folder marker file (`_folder.md` or similar) that `push --content` declares directly. → §X.9
- 📋 **SH150** (deps: SH87 ✅, SH134 ✅) **A renamed file is a move, not a re-creation** — `push --content --prune` sends a rename as `post.delete` + `post.upsert`, which is exactly the workaround SH134 exists to prevent: the new post gets a new id and loses the audit trail and version history. The file already refuses to rename halfway (path vs `url:`), so the honest fix is to detect the pair and emit `post.move`. → §X.10

## Block J — Blueprints (appliable units) + Claude Code plugin

> "Skip steps with things already done" only works if the ready-made thing is
> **appliable**, not readable. **Absorbs the old SH23 epic** (SH27–SH31): a
> human-browsable template catalog becomes an agent-appliable blueprint registry. → §XI

- 📋 **SH92** (deps: SH68, SH63) **Blueprint package format** — versioned parameterized bundle: exchange package (post-types + folders + content) + `@viglet/shio-model` DSL modules + optional front-end files + a `params` JSON-schema + post-apply verification. → §XI.1
- 📋 **SH93** (deps: SH92, SH82) **`shio apply <blueprint>`** — the verb, with plan/dry-run, as CLI command and MCP tool (replaces old SH27). → §XI.2
- 📋 **SH94** (deps: SH93) **First-party blueprint set** — `marketing-site`, `blog`, `docs`, `landing`, `product-catalog`; each ships model + sample content + routes + verification (replaces old SH30). → §XI.3
- 📋 **SH95** (deps: SH92) **Section library (`@viglet/shio-sections`)** — composable page-section post-types **plus** matching React components (`hero`, `feature-grid`, `cta`, `logo-wall`, …). One shared vocabulary for the agent, the console and the front end — this is what makes *"now change the site"* cheap instead of archaeological. Highest-leverage item in the block. → §XI.4
- 💭 **SH96** (deps: SH93) **Blueprint registry + community convention** — `shio-blueprint-*` naming, manifest validation, curated index (absorbs old SH28/SH29/SH31). → §XI.5
- 📋 **SH97** (deps: SH75 ✅, SH94) **Claude Code plugin for Shio** — a shipped `.claude` plugin: MCP server config, skills (`shio-site`, `shio-content-model`, `shio-verify`), slash commands, generated `AGENTS.md` fragment. An agent session starts already knowing Shio; the distribution channel for the whole concept. → §XI.6

## Block L — Agent safety & human-in-the-loop

> An agent-driven CMS is only adoptable if a human can bound it and undo it.
> Invariant: **the agent path is never a privilege escalation** — tenant isolation
> (SH22), content ACL (SH54) and token scopes apply identically. → §XII

- 📋 **SH106** (deps: SH2, SH51) **`AGENT` token scope + session identity** — scoped, budgeted, expiring credential; every write tagged with the agent session id. SH75 already mints the id and stamps it into the audit *actor string*; this task owns the credential model, so it is where the id gets a column (one migration, not two). → §XII.1
- 📋 **SH107** (deps: SH106) **Draft-by-default + publish gate** — MCP writes land in DRAFT; publish is a separate tool, off unless the token says `mayPublish`. → §XII.2
- 📋 **SH108** (deps: SH82) **Destructive-op confirm tokens** — deletes/prunes/apply-with-prune take two steps, the second carrying the **plan hash**. → §XII.3
- 📋 **SH109** (deps: SH106, SH14) **Review queue console surface** — "the agent proposed N changes": diff (reuse SH14), approve, revert (reuse SH55 trash). The human's new primary workflow. → §XII.4
- 💭 **SH110** (deps: SH106, SH45) **Agent budget guards** — per-session call/write/storage ceilings. → §XII.5

## Block M — Token economy (measured)

> The stated goal is that this gets cheaper over time — so it must be measured, not
> claimed. The concept spec's targets (≈10× tokens, ≈6× tool calls, ≈3× human
> cycles) are **acceptance criteria for SH112**, not achievements. → §XIII

- 📋 **SH112** (deps: SH83) **Task benchmark harness** — canonical agent tasks ("build a 5-page marketing site", "add a post-type + 10 posts", "fix a broken link", "translate a site") measured in tool calls + tokens + wall clock, in CI, recorded per release. → §XIII.1
- 📋 **SH113** (deps: SH112) **Response budget enforcement** — CI fails when a canonical response's estimated token size regresses past a threshold; stops the slow re-accretion of entity-shaped payloads. → §XIII.2
- 📋 **SH114** (deps: SH77) **Instance memory / conventions store** — decisions + conventions as first-class content, served in the context pack. The compounding saving: session N+1 costs less than session N. → §XIII.3
- 📋 **SH115** (deps: SH114) **`AGENTS.md` generator** — `shio context --as agents-md` renders project agent instructions from the live instance + memory store, so they cannot rot. → §XIII.4
- 📋 **SH116** (deps: SH77) **Agent-read caching** — strong ETags on manifest/context/schema; an unchanged context costs a `304`. The SH77 `digest` is already the ETag value — this wires it to `If-None-Match`. → §XIII.5
- 📋 **SH127** (deps: SH125) **Lean the delivery JSON + SDK types together** — drop nulls from the CDA DTOs *and* change `@viglet/shio-client`'s `string | null` fields to optional in the same change. SH125 stopped at the agent surface because omitting a null unilaterally would turn a declared `null` into `undefined` for typed consumers. Also removes the nulls the SH77 `model` section inherits. → §XIII.2

## Block N — Realignment of what already shipped

> The reorientation is mostly additive, but it does invalidate some existing
> choices. See the concept spec §10. → §XIV

- 📋 **SH117** (deps: —) **Unify the two post-type APIs** — one name-keyed surface (`/api/v2/post-type`); migrate the console off the id-keyed `/api/v2/post/type` and retire it. Two APIs for one concept is a tax paid on every agent session. → §XIV.1
- 📋 **SH119** (deps: SH92) **`create-shio-app` templates → blueprints** — one appliable format; `shio init` stays a thin wrapper. → §XIV.2
- 💭 **SH120** (deps: SH78) **Align console read shapes with the CDA DTOs** where they overlap — one shape to learn. → §XIV.3

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
