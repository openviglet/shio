# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Priority

- Block J
- Block O
- Block L
- Block M

## Block I — Content as files (local-first authoring)

## Block L — Agent safety & human-in-the-loop

- 💭 **SH110** (deps: SH45, SH106 ✅) **Agent budget guards** — A runaway agent should hit a per-session ceiling and report it rather than a bill, and the metering it must layer on is SH45's. → §XII.5
- 💭 **SH648** (deps: SH200 ✅, SH223 ✅) **A deleted site cannot be restored, because only its content passes through the trash** — Trashing the site row needs a deleted_date of its own plus an answer for the name it still occupies, which is a schema change and a teaching error rather than a service fix. → §XII.13
- 💭 **SH650** (deps: SH225 ✅) **Nothing stops a fetch-type change from breaking authorization** — SH225 made the ACL walk independent of ShFolder.parentFolder being EAGER, but that independence is a code shape nothing checks. → §XII.16

## Block M — Token economy (measured)

- 💭 **SH230** (deps: SH174 ✅) **The manifest's weight is prose, and terse barely touches it** — About 87% of the manifest is a purpose sentence and an example per endpoint, kept verbatim on purpose, so terse saves 11% where it is worth a third elsewhere. → §XIII.10
- 💭 **SH231** (deps: SH161 ✅) **An instance's own notes travel nowhere** — A site's notes ride its export and the instance's own travel nowhere, which is right for an import and wrong for a move. → §XIII.11
- 💭 **SH232** (deps: SH159 ✅) **`/agent/changes` cannot revalidate the way it is most often called** — In window mode the response's own cursor is the moving edge of the feed, so no two polls agree and every one pays in full. → §XIII.12

## Block N — Realignment of what already shipped

## Block O — The human half (the console the curator uses)

- 💭 **SH244** (deps: SH202 ✅) **Duplicating content is console-only** — Copy exists only as a console PUT, with no batch op, no MCP verb and no CLI path, and a folder is the case an agent cannot reproduce with upserts. → §XVI.4
- 💭 **SH245** (deps: SH139 ✅, SH202 ✅, SH203 ✅) **Make "one writer per verb" a lint, not an expectation** — Four times the console's copy of a shared verb was the careless one, and the rule that would have caught all four is stated in agents.md and enforced nowhere. → §XVI.5
- 💭 **SH691** (deps: SH243 ✅) **The copy's title counter is still trash-blind, so a restore leaves two rows a curator cannot tell apart** — ShObjectWriteService.java:364 reads live rows only, so a clone retakes "Home (2)" while a trashed one holds it — the symptom SH202 says is the one they see. → §XVI.74
- 📋 **SH702** (deps: SH701 ✅) **Six dialogs load their content by syncing an effect to the `open` prop** — Each raises state synchronously when opened, which is six suppressed lint violations and a reopen that can paint last time's data before this time's arrives. → §XVI.78
- 📋 **SH703** (deps: SH701 ✅) **The two largest console pages hold as state what they could compute** — A re-query spinner and a selection reset are set from effects rather than derived, so four suppressed violations sit on the folder browser and the visual editor. → §XVI.79
- 📋 **SH704** (deps: SH701 ✅) **`useDiscovery` re-publishes a module cache through an effect that also has to guard itself** — use-discovery.ts:37 copies a module-level cache into state from inside the effect, so the one hook every console page calls carries the last suppressed violation. → §XVI.80

## Block Q — Replication (an existing site becomes an authorable Shio site)

## Block F — Market positioning

- 💭 **SH44** (deps: SH22 ✅) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
- 💭 **SH45** (deps: SH22 ✅) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
- 💭 **SH46** (deps: SH22 ✅) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
- 💭 **SH47** (deps: SH22 ✅, SH38 ✅) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

## Block S — The public site (shio.viglet.org)

- 📋 **SH540** (deps: SH530 ✅) **Nothing on the site is answered by a running Shio** — The transcripts, the preview links and the manifest read are all recordings, so the single claim a visitor cannot check is the one the whole page rests on. → §XIX.11

## Block U — Documentation the matrix still names

- 📋 **SH698** (deps: —) **`fixture-traits.test.mjs` fails at file level about one run in five, with every assertion in it green** — Caught once in five `pnpm test` runs: the file reports `test failed` and names no assertion, 951 ms against 209 ms alone, so the gate is red for something outside the tests. → §XXI.11
- 📋 **SH700** (deps: —) **`pnpm conformance:docs` is red on ten checks against the published documentation** — The docs repo is 13 commits ahead of origin and every failing check is fixed in them, so the gate reads a published state that stopped matching the local one on 2026-08-11. → §XXI.12

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
