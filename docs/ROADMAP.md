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

- ⏳ **SH1218** (deps: —) **A site has no stored picture of its home page, so a grid of sites can only show initials** — The console must capture the published home in a frame, lazy-loading its rasteriser, and PUT it with the digest status gives. → §LXXVII.2
- 📋 **SH1219** (deps: SH1217 ✅, VDS207 in the DS) **The sites list is a five-column table with an empty Description column and no sign of life** — Description holds half the width for one site in twenty-three, dates repeat without saying who, and no row says whether its site is used. → §LXXVII.3
- 📋 **SH1220** (deps: SH1218 ⏳, SH1219, VDS206 in the DS) **The sites list has no grid view, so a site can only be found by reading its name** — At the counts most instances hold, a site's home page is recognised faster than its name, and the drawing makes grid the default below thirty. → §LXXVII.4
- 📋 **SH1221** (deps: VDS205 in the DS) **Shio's lists keep their filter and import in the hero, apart from the rows they act on** — Once the package gives a list one toolbar, every Shio list that passes headerAction has to move its controls into it or keep the split. → §LXXVII.5
- 📋 **SH1222** (deps: VDS207 in the DS) **The admin lists show a Description column most rows leave empty, and no date or actor** — AdminListPage serves every admin entity with the same four columns, so the sites list's proportion problem repeats on each of them. → §LXXVII.6
- 📋 **SH1223** (deps: VDS204 in the DS) **The content browser's table is capped at 560 px, so a folder scrolls inside the page** — browser-table.tsx passes height 560 on every viewport, which wastes a large screen and puts a second scrollbar in the busiest screen. → §LXXVII.7
- 📋 **SH1224** (deps: —) **Ten console lists are hand-drawn tables, outside the design system's one table** — Each sorts, selects and reaches the keyboard its own way, so the redraw would have to be repeated by hand ten times; the grep lands first. → §LXXVII.8
- 📋 **SH1225** (deps: SH1224) **The post types list is a hand-drawn table** — It should sort, reach the keyboard and pick columns the way every other list does. → §LXXVII.9
- 📋 **SH1226** (deps: SH1224) **The media library's rows view is a hand-drawn table** — The busiest list after the content browser sorts and selects its own way, with hover-only actions. → §LXXVII.10
- 📋 **SH1227** (deps: SH1224) **The trash is a hand-drawn table** — Restoring and purging many items wants the package's selection and range keys, which a hand-drawn table lacks. → §LXXVII.11
- 📋 **SH1228** (deps: SH1224) **Search results are a hand-drawn table** — A result list should sort and open from the keyboard like every other list. → §LXXVII.12
- 📋 **SH1229** (deps: SH1224) **The scheduled list is a hand-drawn table** — The agenda's list view should share the package's table while its calendar view stays the package's calendar. → §LXXVII.13
- 📋 **SH1230** (deps: SH1224) **The forms submissions list is a hand-drawn table** — Submissions grow without bound, and a hand-drawn table neither virtualizes nor selects. → §LXXVII.14
- 📋 **SH1231** (deps: SH1224) **The languages coverage list is a hand-drawn table** — It should sort by coverage and reach the keyboard like the other lists. → §LXXVII.15
- 📋 **SH1232** (deps: SH1224) **The open notes list is a hand-drawn table** — SH1214 drew it the way its neighbours were drawn, which is the way this round replaces. → §LXXVII.16
- 📋 **SH1233** (deps: SH1224) **The activity list is a hand-drawn table** — The audit trail is long and filtered, which is the case the package's virtualized table exists for. → §LXXVII.17
- 📋 **SH1234** (deps: SH1224) **A group's members list is a hand-drawn table** — Removing several members wants the package's selection, which a hand-drawn table lacks. → §LXXVII.18
- 📋 **SH1236** (deps: —) **every site, folder or post type a query loads costs one more select for its workflow tasks** — ShObject.shWorkflowTasks is @Fetch(JOIN), which makes it eager, so a listing of N objects runs N+1 statements. → §LXXVII.19

## Priority

## Done when — SH1218

- **The console stores a picture of a published home it shows** Opening the sites list
  with a stale or missing picture leads the console to read status, capture the home and
  PUT it; a second visit with nothing republished sends nothing, asserted by a console
  test.

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
