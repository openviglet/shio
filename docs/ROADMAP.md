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
- 📋 **SH789** (deps: —) **The red-suites exception expires on 2026-08-24 and the only task that removes it is blocked on a sighting** — SH780 needs the flake to recur and it has not in roughly fifty runs, so the JS gate goes red on a date with no unblocked path to green. → §XXVI.14
- 📋 **SH790** (deps: —) **The reserved-range guard fires on prose naming the boundary, and its message tells you to move the range** — Writing the boundary in a shipped sentence made roadkeep derive the next id past it, and the failure named allocation — the one cause it was not. → §XXVI.15
- 📋 **SH791** (deps: —) **The pipeline guard reads only `mvnw` lines, while the rule beside it now claims all three gates** — SH788 stated the rule for the JS and conformance gates too, but its scan matches the Java command alone, so a documented `pnpm test | grep` passes. → §XXVI.16

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
