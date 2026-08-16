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

- 📋 **SH763** (deps: SH693 ✅) **convert writes contentAfter and no post-type declares it, so every conversion against a real instance is refused** — SH693 shipped the writer and the template and not the model, so Page still allows title, content, description, sections and the batch dies at ops[28]. → §XVII.104
- 📋 **SH765** (deps: SH687 ✅) **The regions reader names a region's markup origin and never the theme painting it, the half a curator asks about** — It answers verbatim or sections per region, so a page carrying two stylesheets cannot say which region each one paints. → §XVII.106
- 📋 **SH766** (deps: SH685 ✅) **A mixed page's title is never editable, because the layout annotates it only when there is no verbatim markup** — annotations answers 0/5 on the home page, and the template puts the only sh-field title inside an unless content branch a mixed page never takes. → §XVII.107
- 📋 **SH767** (deps: —) **A sections list holding valid addresses stores, resolves to nothing, renders empty, and verify calls the site ok** — The relation wants a site-relative path, so post:VigR2/sections/vb-top was kept verbatim and the page served with no sections at all and no finding anywhere. → §XVII.108

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
