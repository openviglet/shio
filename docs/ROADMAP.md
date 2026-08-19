# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block Q — Replication (an existing site becomes an authorable Shio site)

- 📋 **SH848** (deps: SH845 ✅, SH847 ✅) **Role matching reads a token's name, so a shadcn palette hands over a near-white hover tint and keeps its brand colour** — accent took --vg-accent at oklch(97% 0 0) while the orange sits at --vg-brand, so the theme accent stayed its built-in green a fourth round. → §XVII.135
- 📋 **SH849** (deps: SH846 ✅) **propose tells the reader that convert says which colour each token filled, and convert prints nothing of the kind** — A conversion writing 65 objects and a whole theme printed 38 lines and no role coverage, so the promise is in the note and not in the run. → §XVII.136
- 📋 **SH850** (deps: SH845 ✅) **Lifting a token out of the kept CSS deletes it without re-declaring it, so the rules that read it resolve to nothing** — 132 of the 133 lifted properties are declared nowhere on the served page and all 132 are still referenced, taking dangling var() calls from 231 to 958. → §XVII.137
- 📋 **SH851** (deps: SH850) **Nothing counts a var() the page never declares, so a change that quadrupled them shipped as an improvement** — page-unstyled reads class names and verify stayed at two warnings while dangling references went from 231 to 958 and a curator saw the difference. → §XVII.138

## Priority

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
