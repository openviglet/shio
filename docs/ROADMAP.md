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

- 📋 **SH1305** (deps: —) **GET /api/v2/site takes ~10-20 s on a 16-site instance, so the sites list looks frozen on first open** — The list is the console's front door, and measuring showed the thumbnails are not the cause: the listing read alone is. → §XI.71
- 📋 **SH1307** (deps: —) **The content browser stacks five bands of chrome before the first row of a folder** — Counts, actions, view toggles, filter and a Columns-only bar each take a framed row, so a folder of six reads as a toolbar. → §XI.73
- 📋 **SH1308** (deps: —) **The post editor frames every field in its own card with a coloured stripe, inside a card, inside the page** — Three nested frames per field and a rainbow of stripes make a five-field form look busy and push the fourth field below the fold. → §XI.74
- 📋 **SH1309** (deps: —) **The site list paints usage in the accent and fills every row's second line with an address** — An orange bar reads as a warning and `site:Name` under every name is noise, so sixteen rows carry forty-eight loud marks and say little. → §XI.75
- 📋 **SH1310** (deps: —) **The post-type list repeats each name as its second line, "InfoPage" under "InfoPage"** — Most types' title equals their API name, so half the list's text is a duplicate and every row is twice as tall as its content. → §XI.76
- 📋 **SH1311** (deps: —) **The home page spends a 390px tile on each single number, and the one that asks for action looks like the rest** — Sites, types and open notes are three identical tiles of mostly white, so what waits for the curator has no more weight than a count. → §XI.77
- 📋 **SH1312** (deps: —) **The media library floats the site picker and Upload on a row of their own, and a font file is a 290px blank tile** — Three control rows sit between the title and the grid, and files without a preview draw large empty squares that read as broken images. → §XI.78
- 📋 **SH1313** (deps: —) **The post-type editor draws Save, Save and close, Cancel and Add field all in the accent** — Four orange controls on one screen leave no primary action, and Add field's solid fill outshouts Save. → §XI.79
- 📋 **SH1315** (deps: —) **Nothing measures whether a curator can do a task in the console, so three redesigns were judged by form alone** — Tokens, route cost and chrome conformity are measured and human effort is not, so "migrated" stood in for "easy". → §XI.81
- 📋 **SH1316** (deps: SH1315) **Opening a site shows its storage folders (fonts, theme, menu, en, pt) and "0 post", not its pages** — A curator thinks in pages as the visitor sees them; a file manager makes a site of 130 pages look empty and technical. → §XI.82
- 📋 **SH1317** (deps: SH1315) **The curator, the builder and the administrator see one console, with tokens and post types beside page editing** — The product says the agent builds and the curator approves, yet half of what a curator sees is for someone else. → §XI.83
- 📋 **SH1318** (deps: SH1315, SH1311) **The home page opens on counts of sites and post types instead of what is waiting for the curator** — A curator's day is approving and fixing, so the first screen should be that queue, not an inventory. → §XI.84
- 📋 **SH1319** (deps: SH1315) **A page can be edited in the post form or in the Universal Editor, and nothing tells the curator which to use** — Two editors for one job double what a curator learns and split every improvement between two surfaces. → §XI.85
- 📋 **SH1320** (deps: SH1315) **Shio's working pages still use the showcase frame now that the design system ships a work grammar** — The content browser, editors and logs keep a 150px hero and a frosted card that VDS235 made optional per route. → §XI.86

## Block P — The visible site (what the human actually looks at, SH74)

- 📋 **SH1304** (deps: —) **The console has no button that opens a site's published pages; every "View Site" opens the draft preview** — A curator cannot see what a visitor sees without typing /sites/<furl>/ by hand. → §XI.70

## Priority

## Done when — SH1245

- **Every directory entry is live and its link is recorded** The MCP Registry lists
  io.github.openviglet/shio, the Claude Code lists the visibility repo tracks name Shio,
  the More tools section carries a Shio line, and each link is in that repo's
  actions.json.

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
