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

## Block BG — The console as an application: cost, resilience and access

- ⏳ **SH1319** (deps: SH1315 ✅, maintainer picks the primary editor) **A page can be edited in the post form or in the Universal Editor, and nothing tells the curator which to use** — Two editors for one job double what a curator learns and split every improvement between two surfaces. → §XI.85
- 📋 **SH1322** (deps: —) **Preview on a page that has never been published opens Spring's Whitelabel 404 instead of the draft** — Previewing a draft is the curator's check before publishing, and the one page it fails on is the one that has no other way to be seen. → §XI.87
- 📋 **SH1323** (deps: —) **A page's section list shows its sections but none of them opens, and editing a section shows no preview** — A headline lives in the hero section, so a curator must leave the page, search for the section by name and edit it blind. → §XI.89
- 📋 **SH1324** (deps: —) **Search results glue title to type ("PricingSectionHero"), read Rascunho on published posts, and drop accents in pt** — The command palette is the fastest way into content, and three of its four words per row mislead or misspell. → §XI.90
- 📋 **SH1325** (deps: —) **Double-clicking a folder in the file picker opens the one below it, as the dialog re-centres after the first click** — Selecting a row adds the path footer, the centred dialog moves up 26px, and the second click lands on the neighbour. → §XI.88
- 📋 **SH1326** (deps: —) **A new page can only be saved, saving returns to the folder, and the folder's table opens a row only on double-click** — Publishing a page just written costs a curator ten clicks across seven screens, the most of SH1315's six tasks. → §XI.91
- 📋 **SH1327** (deps: —) **Benchmarks that repeat an identical read on H2 time its result cache, so a 3.5 s query printed as 17 ms** — SH1217's own measurement hid the defect SH1305 fixed, and ten timing tests may be reporting cache hits as costs. → §XI.92
- 📋 **SH1328** (deps: —) **22 colour classes in the console paint nothing, because the design system's sheet does not ship them** — A selected row's tint, focus rings and a pages bar's draft share compile, review clean and render transparent. → §XI.93
- 📋 **SH1329** (deps: —) **The sites grid's card link is named only by the site, so axe fails it as a label-content mismatch** — Voice-control users say the text they see and the card does not answer, and every console check run is red on it. → §XI.94
- 📋 **SH1330** (deps: —) **The console check expects /bento/admin to land on users, and it lands on the administration hub** — One of the two is stale, and a probe that is red for a known reason hides the next real failure beside it. → §XI.95
- 📋 **SH1331** (deps: —) **The post editor at 1440px draws three panes and a tab strip where SH1094 holds two panes and none** — Either the layout or the probe changed without the other, and the split-pages probe is red on every run. → §XI.96
- 📋 **SH1332** (deps: —) **A code-managed post type's editor fails axe colour contrast on its violet "managed by code" banner** — Every type a push or the API created opens on a banner whose text does not reach 4.5:1 in the dark theme. → §XI.97
- 📋 **SH1333** (deps: —) **A site's pages view makes every new page at the root, not as a child of the page selected** — SH1316's remainder: a page meant for /about lands at /x and must be moved, because find rows carry no folder. → §XI.98
- 📋 **SH1336** (deps: —) **A page that fails a check is not in home's waiting section, so a curator learns of it only by opening it** — SH1318's remainder: verify renders pages to answer, so the inbox needs a measured cheap read before it can list them. → §XI.101

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
