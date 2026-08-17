# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block W — What the replication round left behind (instruments, corpus, and the sweep it owes)

- ⏳ **SH778** (deps: a further sighting) **fixture-traits failed once in a full run and passed alone, and nothing kept enough to say why** — One sighting on 2026-08-17: the file-level test reported 'test failed' with no assertion named, and a re-run of the suite was green, so the evidence is a log line. → §XXVI.4
- 📋 **SH780** (deps: SH778 ⏳) **The fixture-traits file still fails intermittently, and the instrument that will name it has not been triggered** — SH778 ruled out three causes and left the process exit; it did not reproduce in twelve runs after the instrument landed, so the cure waits on a sighting. → §XXVI.6
- 📋 **SH793** (deps: —) **A capability is declined on a headroom figure that lives in a comment and nothing measures or prints** — The P1 list refuses postTypeMerge because shio_apply is "3 tokens under" the ceiling; the ceiling is enforced, that number is not, and P3 says a cost is measured. → §XXVI.18
- 📋 **SH794** (deps: —) **A scan regex whose word-ish literal carries no boundary matches inside a longer identifier** — SH792's first run read `exempt` inside ShCsrfExemptionLintTest and reported three files as hand-offs; nothing says which other scans spell a literal the same way. → §XXVI.19

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
