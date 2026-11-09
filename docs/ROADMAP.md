# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block J — Blueprints (appliable units) + Claude Code plugin (SH74)

- ⏳ **SH1245** (deps: owner submits entries) **No MCP directory or Claude Code plugin list names Shio, so a Claude Code user meets it only by chance** — Entries are drafted in the visibility repo; the npm package must ship mcpName before the registry accepts it. → §XI.28
- 📋 **SH1411** (deps: —) **The fallback error page a browser gets is unstyled and always in English, even under a pt-br address** — A visitor turned away from a missing or members-only page sees bare browser-default HTML, which reads as a broken site. → §XI.127

## Block BJ — What a visitor finds first: release, links and a demo

- ⏳ **SH1342** (deps: maintainer deploy) **Nobody can open a Shio site or its console without installing it; the site offers screenshots and a scripted session** — Every launch channel lacks a demo that opens without installing, and no production site exists. → §XCV.4

## Block R — End-user documentation for 2026.3 (docs-shio)

- 📋 **SH1405** (deps: SH1402 ✅) **content-console.md runs 4,300 words and describes console surfaces as prose instead of tasks a curator does** — Rewritten with shio-docs-write, one Diátaxis type per section and anchors kept, until the SH1401 lint drops it from its list. → §XCVII.3
- 📋 **SH1406** (deps: SH1402 ✅) **website-development.md runs 4,000 words with template reference and build procedures interleaved** — Rewritten with shio-docs-write, one Diátaxis type per section and anchors kept, until the SH1401 lint drops it from its list. → §XCVII.3
- 📋 **SH1407** (deps: SH1402 ✅) **replication.md runs 3,800 words with no single walk-through from a source URL to a published replica** — Rewritten with shio-docs-write, one Diátaxis type per section and anchors kept, until the SH1401 lint drops it from its list. → §XCVII.3
- 📋 **SH1408** (deps: SH1402 ✅) **cli.md runs 2,800 words and mixes the verb reference with how-to prose** — Rewritten with shio-docs-write, one Diátaxis type per section and anchors kept, until the SH1401 lint drops it from its list. → §XCVII.3
- 📋 **SH1409** (deps: SH1402 ✅) **administration-guide.md runs 2,700 words with settings described in prose instead of a reference table** — Rewritten with shio-docs-write, one Diátaxis type per section and anchors kept, until the SH1401 lint drops it from its list. → §XCVII.3
- 📋 **SH1412** (deps: —) **shio-docs-write tells a writer a bare Shio is fine after the first mention, and the full-names lint fails the page** — The skill and the gate disagree, so every Block R rewrite that follows the skill lands red on SH1369. → §XCVII.5

## Block BF — The visual editor becomes a composer

- 📋 **SH1410** (deps: —) **Open External URL's hint tells a curator to instrument a page with data-sh-* attributes, which SH304 retired** — A curator following it annotates nothing the bridge reads; the hint should name the editor package and its origin meta tag. → §XCVII.4

## Priority

## Done when — SH1245

- **Every directory entry is live and its link is recorded** The MCP Registry lists
  io.github.openviglet/shio, the Claude Code lists the visibility repo tracks name Shio,
  the More tools section carries a Shio line, and each link is in that repo's
  actions.json.

## Done when — SH1342

- **A visitor opens the showcase and a guest console without installing** The README and
  the site link a hosted instance where the Quilhara showcase renders publicly and a
  guest role can browse, edit drafts and see the review queue but not publish, reset
  from the blueprint on a schedule.

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
- **The publish gate is a checklist, not an orchestrator** No workflow triggers,
  branches, waits or flow builder inside Shio: a gate is a declared list of checks
  evaluated on publish and answering a teaching 409, and GATE_FAILED is the edge an
  external orchestrator acts on, because an engine here would be a third contract (P10).
- **Awareness, not co-editing: no CRDT or shared-document runtime** The second editor on
  a page is usually a machine. If-Match, a teaching 409, a short-lived working mark and
  a presence line solve a lost update, and a presence signal must never become a lock
  that blocks a write.
- **The agent never approves, holds or rejects its own work** Review decisions stay off
  the agent namespace and the manifest keeps the approve route out of its index. An
  agent may read the verdict on its own runs through a read-only resource, and nothing
  more.
- **The assistant is answered by the connected agent, never by a model**
  VigletAssistant's composer relays a curator's request to the external agent as a note.
  Shio adds no endpoint, key, prompt or model id for it, and with no agent connected the
  dock shows no composer and stays a status surface.
- **A new capability does not arrive as a thirteenth MCP tool** tools/list is resent
  every turn and sits near its ceiling. The ladder is an op name, then a parameter, then
  a resource, then an endpoint, and a tool is added only in a commit that names the tool
  that leaves.
