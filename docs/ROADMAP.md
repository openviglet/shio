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

- Block L

## Block L — Agent safety & human-in-the-loop

- 💭 **SH110** (deps: SH746 ✅, SH106 ✅, viglet-quota-spi) **The storage meter landed, so what blocks the ceiling is a delta-aware check in the shared quota SPI** — SH746 counts the bytes; the ceiling needs check(resource, delta) upstream, since the current call compares a total and one upload can exceed it. → §XII.5
- 📋 **SH750** (deps: SH244 ✅, SH110, viglet-quota-spi) **One write that creates N posts asks the post quota once, so a bulk copy overshoots the plan by N-1** — checkCanCreatePost compares a total against the limit, which is exact only for a write that adds one; SH110 names the same missing SPI for bytes. → §XII.28
- 📋 **SH751** (deps: SH45, SH746 ✅) **Nothing calls ShTenantStorageMeter.record, so the storage meter reads zero until somebody reconciles** — The meter, its counter wiring and its reconcile all shipped; the calls that move it on upload and on cleanup were never written. → §XII.29

## Block F — Market positioning

- 💭 **SH45** (deps: SH22 ✅) **Nothing meters API calls, and there are no billing-export hooks** — A call is a flow and the counter SPI models levels; the existing per-token bucket is in-heap and per-node, so it cannot be what a bill is computed from. → §VI.5

## Block V — The vocabulary proved live (integration tests against a running instance)

- 📋 **SH748** (deps: SH244 ✅, SH712 ✅) **folder.copy has no live coverage, and the matrix said so on the run that shipped it** — It was declared uncovered rather than covered, because writing a live scenario against no running instance asserts what nobody observed. → §XXIV.1

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
