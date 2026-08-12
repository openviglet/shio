# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block I — Content as files (local-first authoring)

## Block J — Blueprints (appliable units) + Claude Code plugin

- 📋 **SH678** (deps: SH675 ✅) **An agent cannot reach the marketplace over MCP, and the budget for a tool is three tokens** — The list is at 2397 of 2400 and shio_apply at 441 of 460, so this lands only after a trim finds the room, which the budget file requires before any raise. → §XI.26
- 📋 **SH679** (deps: SH646 ✅, SH675 ✅) **Nothing in the console mentions a blueprint, so a curator cannot see or install one** — The human half is entirely unbuilt - zero files in shio-react name a blueprint - so browsing a catalog, reading a README and filling a package parameter form has no surface. → §XI.25
- 📋 **SH682** (deps: SH677 ✅, SH681) **The marketplace writes are open to any authenticated caller** — Installing a package is the admin-gated blueprint reload plus a download, so it wants the same role - which is unreachable until SH681. → §XI.27

## Block L — Agent safety & human-in-the-loop

- 💭 **SH110** (deps: SH45, SH106 ✅) **Agent budget guards** — A runaway agent should hit a per-session ceiling and report it rather than a bill, and the metering it must layer on is SH45's. → §XII.5
- 💭 **SH648** (deps: SH200 ✅, SH223 ✅) **A deleted site cannot be restored, because only its content passes through the trash** — Trashing the site row needs a deleted_date of its own plus an answer for the name it still occupies, which is a schema change and a teaching error rather than a service fix. → §XII.13
- 💭 **SH650** (deps: SH225 ✅) **Nothing stops a fetch-type change from breaking authorization** — SH225 made the ACL walk independent of ShFolder.parentFolder being EAGER, but that independence is a code shape nothing checks. → §XII.16

## Block M — Token economy (measured)

- 💭 **SH230** (deps: SH174 ✅) **The manifest's weight is prose, and terse barely touches it** — About 87% of the manifest is a purpose sentence and an example per endpoint, kept verbatim on purpose, so terse saves 11% where it is worth a third elsewhere. → §XIII.10
- 💭 **SH231** (deps: SH161 ✅) **An instance's own notes travel nowhere** — A site's notes ride its export and the instance's own travel nowhere, which is right for an import and wrong for a move. → §XIII.11
- 💭 **SH232** (deps: SH159 ✅) **`/agent/changes` cannot revalidate the way it is most often called** — In window mode the response's own cursor is the moving edge of the feed, so no two polls agree and every one pays in full. → §XIII.12

## Block N — Realignment of what already shipped

- 📋 **SH672** (deps: —) **A floor in a docs reader fires when the corpus is correct, not when the reader breaks** — Three floors in the rationale reader are absolute counts over a corpus meant to empty, and a fourth already fired for the corpus being right rather than the reader being broken. → §XIV.24
- 📋 **SH680** (deps: —) **Every command-line property passed to the jar is silently discarded** — The bootstrap calls application.run() instead of run(args), so --server.port and every other flag is dropped with no error and no log line. → §XIV.25
- 📋 **SH681** (deps: —) **No locally authenticated user can reach an admin-gated path** — The resolver granting ROLE_ADMIN from the Administrator group is wired only into the OAuth2 and OIDC services, and the role table a password login reads is empty. → §XIV.26

## Block O — The human half (the console the curator uses)

- 💭 **SH243** (deps: SH55 ✅, SH202 ✅) **A trashed post still holds its friendly URL, and nothing knows it** — Every free-URL check reads through @SQLRestriction, so the trash is invisible to it and an untrashed post can collide with the name its own copy took. → §XVI.3
- 💭 **SH244** (deps: SH202 ✅) **Duplicating content is console-only** — Copy exists only as a console PUT, with no batch op, no MCP verb and no CLI path, and a folder is the case an agent cannot reproduce with upserts. → §XVI.4
- 💭 **SH245** (deps: SH139 ✅, SH202 ✅, SH203 ✅) **Make "one writer per verb" a lint, not an expectation** — Four times the console's copy of a shared verb was the careless one, and the rule that would have caught all four is stated in agents.md and enforced nowhere. → §XVI.5

## Block Q — Replication (an existing site becomes an authorable Shio site)

- ⏳ **SH502** (deps: SH446 ✅) **The only scaffold that renders sections cannot install, so an authorable replica has no front end** — shio-sections answers E404 and the built-in template carries no sections renderer, so a fidelity replica has two delivery paths and an authorable one has none. → §XVII.55

## Block F — Market positioning

- 💭 **SH44** (deps: SH22 ✅) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
- 💭 **SH45** (deps: SH22 ✅) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
- 💭 **SH46** (deps: SH22 ✅) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
- 💭 **SH47** (deps: SH22 ✅, SH38 ✅) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

## Block S — The public site (shio.viglet.org)

- 📋 **SH540** (deps: SH530 ✅) **Nothing on the site is answered by a running Shio** — The transcripts, the preview links and the manifest read are all recordings, so the single claim a visitor cannot check is the one the whole page rests on. → §XIX.11

## Block U — Documentation the matrix still names

- 📋 **SH674** (deps: SH499 ✅) **No end-user page publishes the four discovery calls as a runnable request any more** — The suite that executes the published examples finds none of the four P2 discovery GETs on any page, so the promise the product is sold on is the one a reader cannot copy. → §XXI.10

## Non-goals (do NOT add as tasks)

Binding constraints — rationale in [IMPROVEMENTS.md](IMPROVEMENTS.md) §0.3–§0.4, §0.7 and
[specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md) §11.

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
- **The instance does not run a browser.** Raster stays in the CLI — SH102's shio
  snapshot, Playwright an optional dynamic import — because that is where the agent
  already has a shell (P4) and where the human opens the PNG. Chromium per request is a
  different operational thing, and the diff ratio is a number the CLI already computes.
