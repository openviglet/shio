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

## Block Q — Replication (an existing site becomes an authorable Shio site)

## Block F — Market positioning

- 💭 **SH44** (deps: SH22 ✅) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
- 💭 **SH45** (deps: SH22 ✅) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
- 💭 **SH46** (deps: SH22 ✅) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
- 💭 **SH47** (deps: SH22 ✅, SH38 ✅) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

## Block S — The public site (shio.viglet.org)

- 📋 **SH540** (deps: SH530 ✅) **Nothing on the site is answered by a running Shio** — The transcripts, the preview links and the manifest read are all recordings, so the single claim a visitor cannot check is the one the whole page rests on. → §XIX.11

## Block U — Documentation the matrix still names

- ⏳ **SH698** (deps: —) **`fixture-traits.test.mjs` fails at file level about one run in five, with every assertion in it green** — Caught once in five `pnpm test` runs: the file reports `test failed` and names no assertion, 951 ms against 209 ms alone, so the gate is red for something outside the tests. → §XXI.11
- 📋 **SH700** (deps: —) **`pnpm conformance:docs` is red on ten checks against the published documentation** — The docs repo is 13 commits ahead of origin and every failing check is fixed in them, so the gate reads a published state that stopped matching the local one on 2026-08-11. → §XXI.12
- 📋 **SH715** (deps: SH705 ✅) **The docs hub still answers on /category/human-curates-it, the one place SH705 could not reach** — Renaming the slug without a redirect breaks every external link into the curator hub, and the docs site has no redirect plugin installed to catch them. → §XXI.13

## Block V — The vocabulary proved live (integration tests against a running instance)

- 📋 **SH707** (deps: —) **No instrument says which of the agent's verbs a live suite has ever called** — Thirteen MCP tools, thirty-three CLI commands and the agent ops are proved by MockMvc and module imports, and only two tools are ever spoken over a wire. → §XXIII.1
- 📋 **SH708** (deps: SH707) **Eight read tools have never answered a real client against real content** — find, changes, digest, verify, assets, marketplace, context and read are proved against MockMvc fixtures, so a schema or projection drift shows up first in Claude Code. → §XXIII.2
- 📋 **SH709** (deps: SH707) **No live run has ever written, published or rolled back through the MCP tools** — write, apply, publish and remember carry draft-default, dry-run and confirm tokens, and every one of those guards is asserted against a mock that cannot leave a wrong row behind. → §XXIII.3
- 📋 **SH710** (deps: SH707) **P6 is asserted on the ops but never on the thirteen tools a client actually calls** — A refusal is the agent's recovery path, and no live run has checked that each tool's wrong call comes back carrying fix, allowed, didYouMean or example. → §XXIII.4
- 📋 **SH711** (deps: SH707) **Thirty-three CLI commands, and only the clone-propose-convert chain is ever run against an instance** — pull, push, apply, verify, diff, impact, audit, doctor, site, types and the rest are proved by importing the module, which skips argv, exit codes and the HTTP client. → §XXIII.5
- 📋 **SH712** (deps: SH708, SH709, SH710) **Every suite calls the verbs in the order its author imagined, which is the one order that works** — An agent publishes before it creates, moves a folder into its own child and reads what it deleted, and nothing exercises the vocabulary out of order on purpose. → §XXIII.6
- 📋 **SH713** (deps: SH712) **Nothing has ever pointed two agents and a curator at one instance at the same time** — Every suite is one caller in sequence, so lost updates, half-visible publishes and a console edit landing mid-plan are failure modes the product has never been asked about. → §XXIII.7
- 📋 **SH714** (deps: SH708, SH709) **Response budgets are measured on fixtures, and P5's byte-identical promise on one call** — A payload sized against a two-post fixture says nothing about a real tree, and no live run repeats a call to check the second answer matches the first byte for byte. → §XXIII.8

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
