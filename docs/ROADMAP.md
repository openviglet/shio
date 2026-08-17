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

- 📋 **SH770** (deps: —) **A gate stamp is read as this tree's colour whatever commit it was taken at, so a stale red excuses or accuses** — ShDeclaredRedTest judged HEAD 42d02432 against a conformance stamp from 4b20d8e0; it prints the sha and asserts as though the two were the same tree. → §XVII.111
- 📋 **SH771** (deps: —) **Three tasks shipped and COVERAGE.md never named them, so the conformance gate is red for docs upkeep** — SH747's ratchet counts 493 unnamed against a recorded 490; the matrix lives in vigletdocs, so a Shio commit cannot lower it and every push since is over a red. → §XVII.112
- 📋 **SH772** (deps: SH763 ✅) **A mixed page whose first block was accepted keeps the source's markup and gets none of the source's CSS** — The head asks {{#if content}} and that page has none — its kept markup is all SectionMarkup, so the captured stylesheet is never referenced and each run renders bare. → §XVII.113
- 📋 **SH773** (deps: SH767 ✅) **An instance whose Page.sections lost its references declaration is invisible to the lint, the digest and the write** — Provision never upserts, so a Page made before the vocabulary carried the key keeps a blind field forever and three readers that key on it stay silent. → §XVII.114

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
