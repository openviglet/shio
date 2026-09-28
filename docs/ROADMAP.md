# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block AR — The curator's console, surface by surface (§LV)

- 📋 **SH1194** (deps: SH1192 ✅) **Admin lists are tile mosaics: one user fills a 390x300 card with a glow and two thirds of the screen stay empty** — Records people scan and compare belong in a table with columns, filters and row actions; tiles only suit a few visual items. → §LV.31
- 📋 **SH1195** (deps: SH1192 ✅) **Screens carry several primary styles: the site browser shows 8 toolbar buttons in 4 styles and two greens beside orange** — One filled primary per screen, secondary outlined, destructive in the overflow menu, is what tells a curator where the next step is. → §LV.32
- 📋 **SH1196** (deps: SH1192 ✅) **Settings forms are stacks of icon-headed cards with the save button in the hero; nothing says a field was changed** — A settings-row layout with one sticky save bar that names the unsaved change stops edits being lost and makes every form look alike. → §LV.33
- 📋 **SH1197** (deps: SH1195) **Review queue rows all read "Nenhuma intenção declarada", each with its own filled Aprovar button** — A triage list that leads with the run id and change counts, and approves a selection in one action, is what makes reviewing 25 runs feasible. → §LV.34
- 📋 **SH1198** (deps: VDS185 in viglet-design-system) **The mascot notice floats bottom-right over content: it covers row actions in Trash, Post types and tables** — Keeping the mascot as the one notice but docking it in a reserved shell slot means it can never hide a control the curator needs. → §LV.35
- 📋 **SH1200** (deps: SH1194) **A group's page is a hero over empty space: it shows neither members nor roles, only an orange Cancelar** — The reason to open a group is to see who is in it and what it may do; a page without that sends the curator elsewhere. → §LV.38
- 📋 **SH1202** (deps: —) **Copy contradicts itself: Search promises published content but lists drafts; Activity rows are English in pt** — A curator trusts what a page says it shows; one wrong subtitle and a mixed language log both read as the product being unsure of itself. → §LV.37
- 📋 **SH1203** (deps: SH1195) **Content browser rows are 80 px cards with a numbered circle, blue link titles and five pills each; 12 rows fill a screen** — A site tree beside a dense table (type, state, language, changed as columns) is how a curator scans and acts on a folder of fifty posts. → §LV.39
- 📋 **SH1204** (deps: SH1195) **The post editor labels every field with widget-type pills (Title, Text, Ace Editor - HTML) and hides the post's state** — A curator edits content, not widgets: state and address belong in the header, and field chrome should be a label and a help line. → §LV.40

## Block AV — The live defects, fixed before anything is built on them

- 📋 **SH1201** (deps: —) **Media thumbnails are broken under the Vite dev server: /file_source is not proxied and answers index.html** — Every console e2e check runs against Vite, so a dev-only broken image hides real media regressions and makes the library look empty. → §LXI.20

## Block AU — The design system's consumer contract, adopted and gated

- 📋 **SH1199** (deps: VDS182 and VDS183 in the DS) **The design system allows any control height, radius and tone, so the console renders 15 heights, 10 radii and 3 monos** — A closed scale in the DS (two control heights, three radii, one mono, tones as state) makes consistency the default, not a review. → §LV.36

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
