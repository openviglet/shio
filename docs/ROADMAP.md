# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block AQ — The console adopts the shared bento layer (§LIV)

- ⏳ **SH919** (deps: —) **the duplicate gate discards its first positional root, so naming a directory scans a different one** — Delete the NO_POSITIONAL_ROOTS workaround once an installed release carries VDS78. → §LIV.15

## Block AR — The curator's console, surface by surface (§LV)

- 📋 **SH923** (deps: —) **the layer has no frosted panel without a heading, so four surfaces silently rendered console cards** — Every consumer needing one hand-rolls it, and the moment two differ the product is inconsistent invisibly. → §LV.16
- ⏳ **SH927** (deps: SH908 ✅) **the explorer's Monaco workers fail under the Vite dev server, so a local console check is red on a page the jar serves** — The explorer's Monaco workers still fall back to the main thread under the dev server, unreproduced here because mounting the explorer needs a backend. → §LV.19
- 📋 **SH931** (deps: SH909 ✅, SH934 ✅) **a bento URL cannot name its tenant, so a shared console link no longer says which tenant it is for** — A link naming a tenant is only useful once the console can switch to one, which SH934 restores. → §LV.21
- 📋 **SH932** (deps: —) **three components named Adaptive* no longer adapt, and the name is what the next page copies** — Each now has one branch, so the name promises a choice that was deleted and invites a second console era to be written against it. → §LV.22
- 📋 **SH933** (deps: —) **twenty-two pages build a breadcrumb trail that nothing has rendered since the console shell went** — The calls compile, cost a render each and teach the next page to make them, and SH716's assertion went with the renderer. → §LV.23
- 📋 **SH935** (deps: —) **bento-nav still carries fallbackRoute and bentoRoute per entry, and since the cutover both resolve to the same path** — Sixteen of seventeen entries declare only a fallback that now points into bento, so the field pair records a migration that is over. → §LV.25
- 📋 **SH936** (deps: —) **nothing tells a curator an agent run is waiting: the pending-review badge has no renderer under the rail** — The count is fetched and the endpoint is cheap, but the rail carries areas rather than screens, so the one ambient signal that approval is owed is gone. → §LV.26

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
