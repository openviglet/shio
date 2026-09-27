# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block AU — The design system's consumer contract, adopted and gated

- 📋 **SH948** (deps: DS VDS130) **copy-ds.cmd cannot find the design system, so a package change cannot be tried in Shio unpublished** — It forwards to use:local, whose walk from the package's position on disk now reaches a worktree container, and the wrapper adds nothing but that path. → §LX.9

## Block AX — The Viglet assistant: where the console reports, and how the curator reaches the agent

- ⏳ **SH971** (deps: SH968 ✅, design system: VigletAssistant passes VigletAvatar's paused prop through) **a curator cannot choose what the dock reports or quiet the mascot** — The kinds can be muted now, but quieting the mascot needs VigletAssistant to pass VigletAvatar's paused prop through. → §LXIII.8

## Block BA — Releases, scheduling and the publish gate

- 📋 **SH1082** (deps: SH991 ✅, a calendar component in the viglet design system) **the Scheduled page is a list only, with no month or week calendar and no drag to reschedule** — The console has no calendar component, and one built inside Shio is a copy the duplicate gate refuses. → §LXVI.11

## Block BC — The authoring surface, field by field

- ⏳ **SH1091** (deps: maintainer security call) **no stateless caller can change one field of a post without declaring the whole document** — Every route to a partial write crosses a boundary drawn on purpose, so which one is a maintainer's call. → §LXVIII.13
- ⏳ **SH1094** (deps: a two-pane page shape in viglet-design-system) **the live preview is a card under the form, so reading it means scrolling away from the fields** — Two pages now want two panes (this and the translation workspace), and the design system has no such page shape. → §LXVIII.16

## Block BD — Finding, selecting and acting on content

- 📋 **SH1148** (deps: design system: BentoDataTable gains a controlled selectedIds beside onSelectionChange) **the table view has no select-everything and no invert, because the table takes no selection in** — SH1142 removed the two rather than leave them inert; they return when the table can be told. → §LXIX.13

## Block BI — The areas this round did not reach

- ⏳ **SH1102** (deps: roadkeep: code.renamed skips a vendored engine) **roadkeep lint has six findings that were never drift, so the docs gate cannot come back clean** — The rule checks prose against roadkeep's own modules whenever the engine is inside the project, which a vendored copy is. → §LXXIV.8

## Priority

## Done when — SH971

- **A curator can freeze the dock's mascot from the preferences dialog** The design
  system has to ship a paused prop on VigletAssistant first, and then the stored choice
  needs a still flag beside muted.

## Done when — SH1091

- **A chosen boundary, and shio post set over it** A maintainer picks the route (a
  narrowed CSRF exemption on the console merge, or If-Match on a merge an AGENT token
  may call); shio post set then changes one field under a version and is refused when
  the post moved.

## Done when — SH1094

- **The editor and the workspace share a design-system split** Once the package ships a
  two-pane page shape, the post editor shows form and preview side by side with a ratio
  kept per curator, the translation workspace moves onto it, and a phone-width page
  stacks with a toggle rather than dropping a pane.

## Done when — SH1102

- **roadkeep lint exits clean here with no baseline** After re-vendoring a roadkeep that
  skips code.renamed for a vendored engine, `lint` with no --baseline reports no finding
  in Shio's three docs.

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
