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

- ⏳ **SH1245** (deps: owner republishes npm with mcpName, owner submits entries) **No MCP directory or Claude Code plugin list names Shio, so a Claude Code user meets it only by chance** — Entries are drafted in the visibility repo; the npm package must ship mcpName before the registry accepts it. → §XI.28
- ⏳ **SH1256** (deps: SH1255 ✅, SH1277) **A showcase visitor cannot see which Shio feature a page demonstrates or how it was built** — The /members page for contentAcl is still owed: a package cannot declare who reads a folder until SH1277 lands. → §XI.35
- ⏳ **SH1257** (deps: SH1254 ✅, owner picks the trust posture) **The marketplace is off by default, so a fresh instance never lists the showcase** — The demo sells Shio only if a first boot reaches it: enable the catalogue by default, or list only the showcase. → §XI.36
- 📋 **SH1275** (deps: —) **The content browser's create button offers an arbitrary post type, Novo Guide in every Quilhara folder** — The first action on every folder suggests the wrong thing, even in a folder whose posts are all Trails. → §XI.47
- 📋 **SH1276** (deps: —) **ShSiteListingCostTest fails about one full run in four: its first measurement sometimes counts 6 statements, then 3** — A red Java gate on a commit that never touched the site listing stops the work and teaches people to rerun until green. → §XI.48
- 📋 **SH1277** (deps: —) **A blueprint cannot restrict who reads a folder, so a package's members area ships public** — Quilhara's /members page, the showcase's contentAcl demo, cannot be built without an operator locking it by hand. → §XI.49
- 📋 **SH1278** (deps: —) **The search helper cannot narrow to the page's language, so a bilingual results page lists both** — Quilhara's English search for Marina returns five Portuguese posts, while the CDA's /search already takes a locale. → §XI.50
- 📋 **SH1279** (deps: —) **A row in another language links under the current page's locale, and that wrong URL serves the page** — On the en-us search page a pt-br trail links to /en-us/trilhas/..., which answers 200, so one page has two addresses. → §XI.51
- 📋 **SH1280** (deps: —) **assets prune and the media library count a font every page loads as unused, unlike verify since SH1268** — Quilhara's three fonts are listed reclaimable with an instruction to delete them, while verify now says they are used. → §XI.52
- 📋 **SH1281** (deps: —) **A site cannot relabel a language in its switcher, so pt-br and pt-pt both read Português** — SH1270 names each language from a fixed table, which has no regional or brand wording a site could set. → §XI.53
- 📋 **SH1282** (deps: —) **The editor's live preview shows an unpublished image or font as broken, since its frame can reach published bytes only** — A curator previewing an upload they have not published sees a broken image, though the page shows it once published. → §XI.54

## Block S — The public site (shio.viglet.org)

- 📋 **SH1258** (deps: SH1256 ⏳, SH1257) **shio.viglet.org does not show the showcase or tell a visitor how to install it** — A showcase section with screenshots and both install paths, console marketplace and shio CLI, makes the demo the site's main proof of what Shio does. → §XIX.15

## Priority

## Done when — SH1245

- **Every directory entry is live and its link is recorded** The MCP Registry lists
  io.github.openviglet/shio, the Claude Code lists the visibility repo tracks name Shio,
  the More tools section carries a Shio line, and each link is in that repo's
  actions.json.

## Done when — SH1256

- **The showcase debt list is empty** cli/test/showcase-coverage.test.mjs holds DEBT and
  PAGES_OWED with no entries, which means /members exists in en and pt, and verify plus
  the a11y check pass on it.

## Done when — SH1257

- **A fresh instance lists the showcase in its marketplace** On a new store with no
  marketplace properties set, the admin marketplace, shio marketplace list and
  shio_marketplace all name quilhara, and the chosen trust cost is stated in
  docs/agents/blueprints.md.

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
