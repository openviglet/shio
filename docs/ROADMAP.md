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

- ⏳ **SH1245** (deps: owner submits entries) **No MCP directory or Claude Code plugin list names Shio, so a Claude Code user meets it only by chance** — The owner publishes server.json with mcp-publisher, submits the Claude Code lists and the More tools line, and records each link in actions.json. → §XI.28
- 📋 **SH1253** (deps: —) **A marketplace entry has no preview image and its README shows as raw markdown, so the showcase looks like any package** — Screenshots the catalogue declares, proxied like the README, and a rendered README let a curator see the demo before installing it. → §XI.32
- 📋 **SH1254** (deps: SH1250 ✅, SH1252 ✅) **No package ships a designed, bilingual site with real imagery, so a first install shows Shio at its plainest** — One marketplace package builds the showcase's model, theme, layouts, menus and brand pages in en and pt, clean under the a11y gate. → §XI.33
- 📋 **SH1255** (deps: SH1254) **Nothing fails when Shio ships a capability the showcase does not demonstrate** — A test maps each manifest features key and MCP tool to a showcase page or a stated exclusion, holding the gap as a named list so the demo cannot fall behind. → §XI.34
- 📋 **SH1256** (deps: SH1255) **A showcase visitor cannot see which Shio feature a page demonstrates or how it was built** — Tour pages, each with a how-it-was-built panel naming the console screen, MCP tool and CDA call behind it, drain SH1255's debt to zero. → §XI.35
- 📋 **SH1257** (deps: SH1254) **The marketplace is off by default, so a fresh instance never lists the showcase** — The demo only sells Shio if a first boot can reach it; decide between enabling the published catalogue by default and a narrower showcase entry, with the trust cost stated. → §XI.36

## Block S — The public site (shio.viglet.org)

- 📋 **SH1258** (deps: SH1256, SH1257) **shio.viglet.org does not show the showcase or tell a visitor how to install it** — A showcase section with screenshots and both install paths, console marketplace and shio CLI, makes the demo the site's main proof of what Shio does. → §XIX.15
- 📋 **SH1262** (deps: —) **Shio's end-user docs live in the vigletdocs Docusaurus repo, not at shio.viglet.org/docs** — Moving docs-shio into this repo as an Astro Starlight area under shio-site, styled by the design system, puts the docs beside the code they describe. → §XIX.16

## Block BI — The areas this round did not reach

- 📋 **SH1260** (deps: —) **The shipped image boots in 19 s at 1 GB RSS without the JVM levers SH1249 measured** — AppCDS plus SerialGC took the first 200 to 13.7 s and idle RSS to 726 MB at the same p95, for one build step and one flag. → §LXXV.4
- 📋 **SH1261** (deps: —) **hazelcast-all and JGit ship in viglet-shio.jar though nothing starts or imports either** — About 30 of the jar's 163 MB is dead weight with its own CVE surface, and hazelcast.xml tells a reader a cache is running when none is. → §LXXV.5

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
