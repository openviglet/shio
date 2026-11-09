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
- ⏳ **SH1356** (deps: decision: how a FORM token reaches a script-free form) **No blueprint carries a photo gallery or a contact form, so a site built from photos has nowhere for them** — SectionForm and the small-business package remain; a script-free form must carry the FORM token, a security choice. → §XI.109
- 📋 **SH1376** (deps: SH1369 ✅) **The Quilhara sample pages still name the product bare, in both languages and in the showcase screenshots** — The hub's full-name rule covers what a visitor reads, and the showcase displays these pages on the public site. → §XI.110

## Block BG — The console as an application: cost, resilience and access

- ⏳ **SH1319** (deps: SH1315 ✅, maintainer picks the primary editor) **A page can be edited in the post form or in the Universal Editor, and nothing tells the curator which to use** — Two editors for one job double what a curator learns and split every improvement between two surfaces. → §XI.85

## Block BJ — What a visitor finds first: release, links and a demo

- ⏳ **SH1342** (deps: a hosted Viglet Cloud instance) **Nobody can open a Shio site or its console without installing it; the site offers screenshots and a scripted session** — A demo that opens without installing is what every launch channel is missing, and no production site exists to show. → §XCV.4

## Block F — Market positioning / bold bets

- 📋 **SH1375** (deps: SH1362 ✅, decision: how a session reaches a site host) **A visitor who signs in from a site host lands on the page's /sites address on the instance, not on the site host** — Returning there needs a session on that host, and a parent-domain cookie or a handoff code is a security choice. → §VI.11

## Block S — The public site (shio.viglet.org)

- 📋 **SH1373** (deps: —) **The public site scarcely explains the optional search integration described by the hub** — Show how published content reaches Viglet Turing while keeping Viglet Shio useful on its own and keeping agent support claims precise. → §XIX.21
- 📋 **SH1374** (deps: SH1369 ✅, SH1370 ✅, SH1371 ✅, SH1372 ✅, SH1373) **No acceptance check compares the product site with the revised hub contract** — Validate the rendered brand and journeys across routes, themes and small screens so future site changes cannot silently undo the alignment. → §XIX.22

## Priority

## Done when — SH1245

- **Every directory entry is live and its link is recorded** The MCP Registry lists
  io.github.openviglet/shio, the Claude Code lists the visibility repo tracks name Shio,
  the More tools section carries a Shio line, and each link is in that repo's
  actions.json.

## Done when — SH1319

- **Opening a page from anywhere lands in one editor, with the other a mode** Checked
  live after the maintainer picks the primary editor: the browser, search, home and the
  site list all open a page in the same editor, the other is a switch inside it that
  keeps the selection, and the decision is recorded in console-ui.md.

## Done when — SH1342

- **A visitor opens the showcase and a guest console without installing** The README and
  the site link a hosted instance where the Quilhara showcase renders publicly and a
  guest role can browse, edit drafts and see the review queue but not publish, reset
  from the blueprint on a schedule.

## Done when — SH1356

- **A contact form on a blueprint page submits with script off** An applied
  small-business site's contact page, with submissions enabled, stores a Contact post
  from a plain browser form post, and verify reports its three promised pages.

## Done when — SH1373

- **Evidence and boundary are verified** Re-read the cited source and rendered routes,
  satisfy the acceptance checks in the design, and preserve technical identifiers and
  existing URLs.
- **The hub contract survives the change** Review full names, relevant light/dark and
  mobile layouts, and links to installation, documentation and the portfolio; retain the
  product-specific behavior.

## Done when — SH1374

- **Evidence and boundary are verified** Re-read the cited source and rendered routes,
  satisfy the acceptance checks in the design, and preserve technical identifiers and
  existing URLs.
- **The hub contract survives the change** Review full names, relevant light/dark and
  mobile layouts, and links to installation, documentation and the portfolio; retain the
  product-specific behavior.

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
