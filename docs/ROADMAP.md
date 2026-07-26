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
> the agent-native epic; Blocks H–N run **SH75–SH135** with deliberate gaps
> (SH85 · SH91 · SH98 · SH105 · SH111 · SH118 are reserved, unused block-header
> slots). **SH121–SH127** and **SH128–SH135** were added from findings while
> implementing Block H — new tasks are expected to accrete this way, and each one's
> line says what measurement or failure produced it. Superseded and therefore retired: **SH17/SH18** (GraalJS), **SH23 +
> SH27–SH31** (marketplace → Block J), **SH24 + SH32–SH37** (CRUD-over-MCP → Block H).

---

# Part 1 — The agent-native reorientation (current focus)

> Blocks **H–M** implement the north star. Concept, protocol design and the full
> decomposition: [specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md).
> **SH74** is the epic (this concept) — it is complete when the three roadmap docs
> and `agents.md` carry the orientation; the code lands as SH75+.
>
> Suggested order: **H → K → I → J → L → M**. Block H is the protocol everything
> else speaks; Block K (verification) is next because it removes the most expensive
> unit in the system — the human round trip.

## Block H — The Agent Gateway (MCP + agent-shaped protocol)

> The core deliverable: an MCP surface designed for an agent, not a CRUD wrapper.
> **Supersedes the old SH24 epic** (SH32–SH37 — "MCP tools over the CDA"), which was
> too thin: CRUD-over-MCP reproduces every discovery/write cost it was meant to fix.
> → §VIII

> ✅ **The gateway is shipped** → [CHANGELOG.md](CHANGELOG.md) Block H: **SH76**
> (manifest) · **SH80** (teaching errors) · **SH77** (context pack) · **SH78**
> (token-lean responses) · **SH79** (path addressing) · **SH121** (id-free reads) ·
> **SH81** (batch writes) · **SH82** (desired-state plan/apply) · **SH75** (MCP host +
> `shio mcp`) · **SH83** (the agent tools, resources and prompts) · **SH123** (field
> projection) · **SH122** (tenant-join guard) · **SH124** (delivery cost header) ·
> **SH125** (null-omission policy) · **SH126** (`shio context` CLI).
>
> **What is deliberately not in it, and why:** `shio_verify` waits for **SH99** — a
> verify that only pretends leaves the agent believing its work was checked, which is
> worse than knowing it was not. `shio_changes` ships as a *window* over the SH51 audit;
> the `since=` cursor is **SH88**, and faking one over a timestamp would look resumable
> while dropping same-timestamp entries. Both are reported `false` in the manifest so no
> caller has to probe. **Findings that reorder what follows:** the "valid code, wrong at
> runtime" class has now produced four defects (SH122's three CDA paths, plus SH81's
> site-less posts) → **SH128**; and building the tools surfaced two gaps in the write
> vocabulary that only show up once you try to *change* a site rather than build one →
> **SH134**, **SH135**.

- 💭 **SH84** (deps: SH83 ✅, SH22) **Tenant/scope-aware tools + ecosystem wiring** — the per-tenant enforcement pass over the finished tool set (the SH2 allow-list and SH54 ACL already apply; this is the SH22 tenant sweep and its test), plus Turing integration (replaces old SH37). → §VIII.9
- 📋 **SH128** (deps: —) **Guard the "site-less object" defect class** — a test that asserts every write path resolves a folder's owning site by walking to the root, not by reading `folder.getShSite()`. Fourth runtime-only defect of this shape; a build-time guard is cheaper than the fifth. → §VIII.10
- 📋 **SH129** (deps: SH81 ✅) **`shio apply`/`plan` in the CLI** — law P1's order is MCP → files/CLI → REST, and content verbs landed at MCP and REST but not the CLI, because they belong with SH87's file projection. Until then a desired-state document has to be `curl`ed. → §VIII.7
- 📋 **SH130** (deps: SH75 ✅) **MCP wire-conformance guard** — golden-transcript tests pinning the exact JSON-RPC shapes, plus a check that the advertised `protocolVersion` matches what the envelopes actually implement. The protocol is hand-rolled, so nothing else stops a well-meaning edit from silently breaking every client. → §VIII.11
- 📋 **SH131** (deps: —) **Make the full-text bind contract unmissable** — `FULLTEXT_PREDICATE` requires a pre-lowercased, `%`-wrapped term and says so only in a comment; SH83 got it wrong and the symptom was an empty result, not an error. Wrap it in a repository method that does the normalizing. → §VIII.11
- 📋 **SH132** (deps: SH113, SH83 ✅) **Budget the MCP tool-list** — the tool schemas are context re-sent on every turn of every session, so their total token size belongs in the SH113 CI budget alongside the response budgets. A 12-tool cap is a proxy for the number that matters. → §XIII.2
- 📋 **SH134** (deps: SH81 ✅) **`post.move` in the write vocabulary** — there is no way to change a post's URL or folder: re-addressing an upsert creates a second post, so a rename silently orphans the original and loses its history. Found while writing the tools; it is the first thing an agent needs when *changing* a site rather than building one. → §VIII.7
- 📋 **SH135** (deps: SH82 ✅) **Prune folders, not just posts** — a desired-state document cannot remove a folder it no longer declares, so a section that moves leaves its empty shell behind and the document stops describing the site. Same declared-scope rule as post prune. → §VIII.7

## Block K — Perception & verification loop

> Two of the three human round trips in a typical build exist only because the
> agent cannot check its own work. This block is the cheapest large win after H. → §IX

- 📋 **SH99** (deps: SH77) **Content lint** — required fields, dangling references, missing assets, duplicate furls, orphan folders, unpublished dependencies → machine-readable report + exit code. **Ships the ninth tool**: `shio_verify` was held back from SH83 for want of something real to verify, and this task registers it and flips the manifest's `contentLint`. → §IX.1
- 📋 **SH100** (deps: SH99) **Route/link verification** — every furl resolves; internal links + images 200; sitemap coverage vs the blueprint's expectation. → §IX.2
- 📋 **SH101** (deps: SH99) **Render digest** — fetch a delivery URL and return a token-lean *structural* digest (heading outline, landmarks, link/image inventory with alt, word counts). Textual proof of layout without pixels. → §IX.3
- 💭 **SH102** (deps: SH101) **Visual snapshot + diff** — optional Playwright PNG + diff vs previous, for the human handoff rather than for the agent. → §IX.4
- 📋 **SH103** (deps: SH76) **Agent diagnostics** — `GET /api/v2/agent/diagnostics`: last N errors, failed webhooks, broken transforms, schema drift. One call instead of log grepping. → §IX.5
- 📋 **SH104** (deps: SH88, SH101) **Handoff report** — `shio report` renders the human-facing change summary (what changed, preview links, open questions) for a PR body or console banner. → §IX.6

## Block I — Content as files (local-first authoring)

> An agent is far cheaper and more reliable with `Edit`/`Write`/`Grep` over a text
> tree than with read-modify-write over HTTP. Project content to disk, reconcile
> with the server. Largest cycle reducer in the proposal. → §X

- 📋 **SH86** (deps: SH79) **Filesystem projection format** — `shio/content/<site>/<path>.md` (frontmatter = attrs, body = primary rich-text field) + `shio/assets/`; lossless round-trip, deterministic serialization (same discipline as SH68/SH63). → §X.1
- 📋 **SH87** (deps: SH86, SH82) **Two-way content sync** — `shio pull --content` / `push --content` on the plan/apply engine, with `--check` drift for CI. → §X.2
- 📋 **SH88** (deps: SH51, SH79) **Change feed / cursor API** — `GET /api/v2/agent/changes?since=<cursor>` over the audit trail, so the human's console edits flow back and a follow-up session reads only the delta. SH83's `shio_changes` is the window this makes resumable; it also flips the manifest's `changeCursor`. → §X.3
- 📋 **SH89** (deps: SH87, SH88) **Conflict policy** — per-field 3-way merge with explicit conflicts and `--ours/--theirs`; never a silent clobber (both actors are legitimate writers, cf. the SH66 lock). → §X.4
- 💭 **SH90** (deps: SH87) **Watch mode** — `shio dev` syncs both directions and emits an agent-readable event log. → §X.5

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
