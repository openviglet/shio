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

- 📋 **SH871** (deps: SH868 ✅) **convert --annotate names 40 fields nothing creates, repeats each requires line, and exits 1 on a site with no pages** — The first run left 18 assets, zero pages and a 404 home, and only ran after the 40 were hand-added through pull, edit and push. → §XL.13
- 📋 **SH872** (deps: SH868 ✅) **The annotated fields land on the shared Page type under block hashes, so most of a page's fields belong to other pages** — Page went from 4 fields to 44 and the home page reports 14 annotated and 30 not editable, 27 of which are another page's blocks. → §XL.14
- 📋 **SH873** (deps: —) **A page's nav label is its title, so an SEO title becomes the menu and every page is listed alphabetically** — Building a pharmacy site from a blueprint produced a menu reading Contato, Farmacêutico responsável — Farmácia Vitalis, Farmácia Vitalis, Produtos — Farmácia Vitalis. → §XL.15
- 📋 **SH874** (deps: —) **No replication run has ever used an AEM-served source** — A published AEM page is ordinary HTML the existing chain already accepts, so the run decides whether integrating with AEM needs any code at all. → §XL.16
- 💭 **SH875** (deps: SH874) **shio clone takes no header or credential, so an authenticated source cannot be captured** — An Author instance, a staging origin behind basic auth and any gated page are unreachable, and a crawl captures the login page instead of the site. → §XL.17

## Block AP — Interoperating with a CMS Shio does not own (its content model, its editor)

- 💭 **SH876** (deps: SH874) **No importer maps a foreign CMS's content model onto post types** — AEM publishes Content Fragment Models over GraphQL and the capture reads only rendered pages, so a modelled source arrives as markup with its model lost. → §XLV.1
- 💭 **SH877** (deps: an AEM as a Cloud Service tenant to test against) **A page Shio renders is editable only by Shio's own console bridge** — Adobe's editor reads annotations Shio already emits under other names, so an adapter over the existing write path is small and untestable without a tenant. → §XLV.2

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
