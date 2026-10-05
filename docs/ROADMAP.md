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

- 📋 **SH1229** (deps: SH1224 ✅) **The scheduled list is a hand-drawn table** — The agenda's list view should share the package's table while its calendar view stays the package's calendar. → §LXXVII.13
- 📋 **SH1230** (deps: SH1224 ✅) **The forms submissions list is a hand-drawn table** — Submissions grow without bound, and a hand-drawn table neither virtualizes nor selects. → §LXXVII.14
- 📋 **SH1231** (deps: SH1224 ✅) **The languages coverage list is a hand-drawn table** — It should sort by coverage and reach the keyboard like the other lists. → §LXXVII.15
- 📋 **SH1232** (deps: SH1224 ✅) **The open notes list is a hand-drawn table** — SH1214 drew it the way its neighbours were drawn, which is the way this round replaces. → §LXXVII.16
- 📋 **SH1233** (deps: SH1224 ✅) **The activity list is a hand-drawn table** — The audit trail is long and filtered, which is the case the package's virtualized table exists for. → §LXXVII.17
- 📋 **SH1234** (deps: SH1224 ✅) **A group's members list is a hand-drawn table** — Removing several members wants the package's selection, which a hand-drawn table lacks. → §LXXVII.18
- 📋 **SH1236** (deps: —) **every site, folder or post type a query loads costs one more select for its workflow tasks** — ShObject.shWorkflowTasks is @Fetch(JOIN), which makes it eager, so a listing of N objects runs N+1 statements. → §LXXVII.19
- 📋 **SH1237** (deps: SH1218 ✅) **the console's capture of a site's home has never run in a real browser** — Its node tests fake the frame and the rasteriser, so a capture that fails in Chromium would leave every card on initials unnoticed. → §LXXVII.20
- 📋 **SH1238** (deps: —) **a user's last sign-in is never recorded; lastLogin holds the seed date** — Only ShUserOnStartup writes it, so the users list cannot say who stopped using the instance. → §LXXVII.21
- 📋 **SH1239** (deps: a sticky selection bar in the DS) **the content browser's bulk verbs scroll out of view down a long folder** — The package keeps the table's header row in view but not its selection bar, so rows ticked low in a folder leave Move and Delete off screen. → §LXXVII.22
- 📋 **SH1240** (deps: —) **search's Published count leaves out drafts of live pages; it reads row.live, which the server never sends** — The server's field is published (SH545), so the console's live is always undefined. → §LXXVII.23

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
