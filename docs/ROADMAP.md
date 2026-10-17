# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block S — The public site (shio.viglet.org)

- ⏳ **SH1241** (deps: owner sets repo metadata) **The GitHub repos describe the 2018 Shio: AngularJS, Nashorn, viglet.org/shio; shio-ce shows only its own name** — GitHub search and AI assistants read the repo page first, so it should carry the agent-ready descriptor. → §LVIII.1
- 🛠 **SH1242** (deps: —) **Searches for a CMS that Claude Code or an MCP client can drive do not surface Shio** — In headless CMS Shio competes with Strapi, Sanity and Contentful, while in agent-driven CMS it has almost no rival and agent-gateway already ranks 9.9 unaided. → §LVIII.3
- 📋 **SH1243** (deps: the owner's decision in viglet.org W42) **shio.viglet.org loads Google Analytics without asking consent, unlike www.viglet.org** — The owner's measurement decision for viglet.org (W42 there, DOC6, T1097) has to hold here too, or four sites measure under three rules. → §LVIII.4
- 📋 **SH1244** (deps: —) **The home ranks 4.8 in Google with no clicks, and its title names no category a stranger would recognise** — A searcher who has never heard of Shio decides from the title, so it has to say what Shio is and what sets it apart. → §LVIII.5
- 📋 **SH1247** (deps: —) **Six vendored docs/design pages were hand-edited, so the SH942 page-reference drift check is red** — The em-dash pass edited files the design system owns, and the gate refuses any copy that differs. → §LVIII.7

## Block J — Blueprints (appliable units) + Claude Code plugin (SH74)

- 📋 **SH1245** (deps: —) **No MCP directory or Claude Code plugin list names Shio, so a Claude Code user meets it only by chance** — Claude Code users are the audience the owner reaches this quarter through claude-tray, and Shio is the one Viglet product built for them. → §XI.28

## Priority

## Done when — SH1241

- **gh repo view shows the agent-ready descriptor and shio.viglet.org** `gh repo view
  openviglet/shio` prints the descriptor from LVIII.1 as description,
  https://shio.viglet.org as homepage, and no angularjs/nashorn/wcm topics; shio-ce is
  archived or carries the same text.

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
