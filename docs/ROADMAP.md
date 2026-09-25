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

- 📋 **SH944** (deps: SH941 ✅, DS VDS135) **the design system reaches this repository as copied files, never as an enabled Claude Code plugin** — Shio already ships claude-plugin/ with a manifest, commands and skills, so the pattern a design-system plugin would follow is in this tree and unread. → §LX.5
- 📋 **SH948** (deps: DS VDS130) **copy-ds.cmd cannot find the design system, so a package change cannot be tried in Shio unpublished** — It forwards to use:local, whose walk from the package's position on disk now reaches a worktree container, and the wrapper adds nothing but that path. → §LX.9

## Block AX — The Viglet assistant: where the console reports, and how the curator reaches the agent

- ⏳ **SH971** (deps: SH968 ✅, design system: VigletAssistant passes VigletAvatar's paused prop through) **a curator cannot choose what the dock reports or quiet the mascot** — The kinds can be muted now, but quieting the mascot needs VigletAssistant to pass VigletAvatar's paused prop through. → §LXIII.8
- 📋 **SH1139** (deps: SH965 ✅) **sixteen console writes end silently on a path, so the dock never hears a delete or a refused create** — SH965's lint lists them: five deletes report nothing, and eleven writes toast success but let a failure escape or swallow it. → §LXIII.10

## Block AY — What the conformance instruments cannot see

- 📋 **SH1076** (deps: viglet-core 2026.3.11 published) **every CI suite fails before it starts, because the viglet-core BOM version is not in Maven Central** — pom.xml imports viglet-core-bom 2026.3.11, which only a local ~/.m2 holds, so Maven cannot read the POM in CI while every local gate passes. → §LXIV.16
- 📋 **SH1115** (deps: —) **the coverage mirror marks ids named from a working copy nobody checked was committed** — It reads COVERAGE.md from a directory rather than a revision, so an uncommitted edit names ids here forever while the published matrix never gains the sentence. → §LXIV.18
- 📋 **SH1118** (deps: SH1049 ✅) **the shared comment stripper silently deletes half a file, and every scan over it passes** — A slash and two stars in any string opens a block comment to it, so one Playwright pattern cut 43,389 characters out of what thirty-one suites read. → §LXIV.19
- 📋 **SH1119** (deps: SH1052 ✅) **two committed regexes hold a literal backspace where a word boundary was meant, so both assert nothing** — A heredoc expanded the escape before the file was written, and the check that internal task ids do not ship has matched nothing since. → §LXIV.20
- 📋 **SH1120** (deps: —) **shio verify --delivery runs fewer checks than shio verify, silently dropping page and a11y** — withDelivery hand-copies the server's default as content,routes,diagnostics, so the flag asking for the stronger proof sends the narrower run. → §LXIV.21

## Block BA — Releases, scheduling and the publish gate

- 📋 **SH1082** (deps: SH991 ✅, a calendar component in the viglet design system) **the Scheduled page is a list only, with no month or week calendar and no drag to reschedule** — The console has no calendar component, and one built inside Shio is a copy the duplicate gate refuses. → §LXVI.11
- 📋 **SH1138** (deps: —) **a scheduled publish or release goes live without evaluating the site's publish gates** — The SH15 sweep fires publishScheduled and ShReleaseRunner.run with no gate call, so a gate declared later, or a draft edited after scheduling, is never checked. → §LXVI.15

## Block BC — The authoring surface, field by field

- ⏳ **SH1091** (deps: maintainer security call) **no stateless caller can change one field of a post without declaring the whole document** — Every route to a partial write crosses a boundary drawn on purpose, so which one is a maintainer's call. → §LXVIII.13
- 📋 **SH1092** (deps: —) **a draft lint reports clean without saying that five markup rules never ran against it** — markup scans published rows only, deliberately, and the notes on a state=draft report describe the indexes instead, so zero errors reads as a verdict it is not. → §LXVIII.14
- 📋 **SH1093** (deps: —) **two suite tests fail intermittently on Windows and pass on an unchanged re-run** — A blueprint install moves a directory across temp dirs and a CLI exit test bounds wall-clock at 100ms, so a held handle or a loaded machine reds a gate nothing is wrong with. → §LXVIII.15
- 📋 **SH1094** (deps: —) **the live preview is a card under the form, so reading it means scrolling away from the fields** — SH1014 left the split view unbuilt because two panes with a remembered ratio change the editor's shape rather than adding to it, which is a design-system question first. → §LXVIII.16
- 📋 **SH1095** (deps: —) **an author editing content as files cannot see the page before pushing it** — Rendering from proposed values reached MCP, REST and the console, and the files tier — the one P4 calls the agent's cheapest — has no verb for it. → §LXVIII.17
- 📋 **SH1132** (deps: —) **the console cannot change a page's URL, so a rename is an agent-only act** — post.move is the only writer of a friendly URL; the console's form has no field for one, and its single path to a URL change is a rollback that restores an older revision's. → §LXVIII.19

## Block BD — Finding, selecting and acting on content

- 🛠 **SH1024** (deps: SH1022 ✅, SH1023 ✅) **the console has no filter bar, so the query vocabulary is reachable only by typing parameters** — The browser, search and trash have the bar; the review queue does not, and facet counts, the agent author option and saved views are still to build. → §LXIX.5
- 🛠 **SH1026** (deps: SH1025 ✅) **three view-mode buttons highlight when pressed and render the identical list** — Cards have no thumbnails, the list and grid are not virtualised, and most of the page is not yet in content-browser. → §LXIX.7
- 📋 **SH1097** (deps: SH1028 ✅) **the shortcuts sheet advertises a key nothing binds and hides the one the shell does bind** — BentoShortcutsDialog renders a fixed list naming a global question mark, which Shio has never bound, and omits the slash SH889 added. → §LXIX.10
- 📋 **SH1142** (deps: SH1026 🛠) **in the browser's table view, the toolbar's Select menu and Clear change nothing the table shows** — BentoDataTable reports its selection but takes none in, so the page's set and the ticked rows can differ under a bulk delete. → §LXIX.11

## Block BE — Media: what the bytes mean

- 📋 **SH1099** (deps: —) **on PostgreSQL no SQL predicate over post data matches: search finds no body text, each post leaks a large object** — Hibernate writes the @Lob data column through pgjdbc as a large object, so the column holds an OID and only the entity read resolves it. → §LXX.8
- 📋 **SH1100** (deps: —) **two reference tables nothing writes are still backfilled and asserted, and readers find them first** — ShReference joins ShObject and a post is not one, so no writer can use it, yet it sits in TENANT_TABLES and in two schema ITs. → §LXX.9

## Block BF — The visual editor becomes a composer

- 📋 **SH1101** (deps: SH1038 ✅) **a copied post comes back with no id, so duplicating a section can only place the same one twice** — `copyto` returns only the folders it made, so nothing can address a post it just copied, and the editor's Duplicate repeats the reference instead. → §LXXI.8
- 📋 **SH1105** (deps: SH1039 ✅) **the inserter rail describes a section in a sentence, which is the worst way to show a shape** — A curator picks between a hero and a rich-text block by looking, and the rail has no thumbnail and no drop target — so placement is two buttons and nothing can be dragged. → §LXXI.9
- 📋 **SH1106** (deps: SH1039 ✅) **a page can name a section of a type its own template says does not belong there, and nothing says so** — `sh-accepts` is read by the console and by nothing else, so any write past that screen composes a page against a list no instrument consults. → §LXXI.10
- 📋 **SH1108** (deps: SH1040 ✅) **the console cannot offer patterns, because it cannot read the project file they live in** — A pattern is a file in the tree and the console is a browser, so the inserter rail's Patterns tab has nothing to list and Save as pattern has nowhere to write. → §LXXI.11
- 📋 **SH1109** (deps: SH1041 ✅) **a save in the visual editor proves nothing — it returns a toast and the curator has to look** — The render digests, the change log and the reporting dock all exist and nothing joins them, so the one surface where a person edits by hand closes its loop with the word saved. → §LXXI.12
- 📋 **SH1110** (deps: SH1042 ✅) **a share link for one page lets its reader see every draft on that site until it expires** — The credential is bounded to a site because the token row has no target, and narrowing it needs an answer about how a shared page's own images and stylesheet are reached. → §LXXI.13
- 📋 **SH1111** (deps: SH1042 ✅) **a share link is sent and then nobody can see it, because the token screen hides ephemeral rows** — Revocation works and the reading does not: nothing lists the live links, and an agent that just built a site has no verb that prints one. → §LXXI.14

## Block BG — The console as an application: cost, resilience and access

- 📋 **SH1051** (deps: DS VDS137) **Shio's accent fails AA contrast on both grounds, and its primary token is never claimed** — Shio's accent gate asserts the tokens exist and never what they measure, and the primary inputs are unset, so the rail's you-are-here marker renders the preset's neutral. → §LXXII.9
- 📋 **SH1053** (deps: DS VDS141, SH964 ✅) **the console has no skip link, most pages have no main landmark, and navigation never moves focus** — A keyboard reader tabs through the rail and header on every page, and after a route change stays where they were, because no shell owns a main region or focus. → §LXXII.11
- 📋 **SH1054** (deps: SH1052 ✅, SH1053) **no accessibility check runs against a rendered console page** — The existing rules scan source for four patterns, there is no axe run, no jsx-a11y plugin and one viewport, so a violation composed across components ships unseen. → §LXXII.12
- 📋 **SH1112** (deps: SH1043 ✅) **an agent cannot ask a running instance what its console costs to load** — The budget's two readers are a pnpm script and a build gate, so checking a console change needs this checkout, its toolchain and a build — which is P1 on the surface P1 is about. → §LXXII.18
- 📋 **SH1113** (deps: SH1043 ✅) **nothing measures what one console route costs, so a screen's own fetches hide inside a total** — SH1043 counts the bytes every route pays before the first row, which is blind to a page that then fetches three collections to draw two figures. → §LXXII.19
- 📋 **SH1114** (deps: SH1045 ✅) **the reachability walk reads one manifest of eleven, and four of the ten it skips are published** — SH1045 found eighteen dead declarations in the console and nothing asks the same question of the CLI, the SDKs or the starter, where a consumer pays for the answer. → §LXXII.20
- 📋 **SH1116** (deps: SH1046 ✅, SH1047 ✅) **thirty-five console screens still read inside an effect and cancel nothing** — SH1047 migrated the five its symptom named and declared the rest, so the same race is recorded rather than fixed on every other screen that fetches. → §LXXII.21
- 📋 **SH1117** (deps: SH1048 ✅) **the CLI never sends If-None-Match to Shio, so every run pays the full body of an unchanged read** — SH1048 made the console's reads answer 304, which a browser takes for free and Node does not — the CLI sends a conditional GET only to external assets it clones. → §LXXII.22
- 📋 **SH1126** (deps: SH1043 ✅) **the console size gate measures whatever build is in public, whether or not it was built from the tree under test** — pnpm test never builds the console, so a pass can measure bytes older than the change and a fail can come from a build made outside the task. → §LXXII.23
- 📋 **SH1136** (deps: —) **seventeen console lint findings are suppressed rather than fixed, ten of them effects that set state on mount** — SH1075 recorded them to get CI's lint green and a local gate running, and each is a render cascade or a stale ref. → §LXXII.24
- 📋 **SH1137** (deps: —) **a folder moved into another site leaves its posts answering under the old site** — The folder's parent changes and its posts keep the old shSite, so the tree and delivery disagree and no URL check runs. → §LXXII.25
- 📋 **SH1140** (deps: —) **SH1047's fetch guard cannot see a service built and called in one expression, so login-form's mount read escapes it** — Its FETCHES pattern needs a dot right after the class name, and new ShSetupService().status() puts () between them. → §LXXII.26

## Block BH — One content model, many languages

- 📋 **SH1125** (deps: SH1056 ✅) **the browser's locale chip does not say which row is the site's default language** — SH1059's design asked for it once a site could declare a default; the console can read the policy now and the browser never asks for it. → §LXXIII.8
- 📋 **SH1128** (deps: SH1123 ✅) **a post's availableLocales names a translation that exists only as a draft, to a production token** — translate creates a DRAFT and the group read asks for no state, so a switcher links to a page its own token gets a 404 for while the translation is written. → §LXXIII.9
- 📋 **SH1130** (deps: SH1057 ✅) **nothing flags a declared locale no page is written in, so every URL in it quietly serves another language** — The route accepts a declared locale and falls back along the chain, so a locale the site promises and never wrote looks served while showing none of it. → §LXXIII.10

## Block BI — The areas this round did not reach

- 📋 **SH1102** (deps: —) **roadkeep lint has six findings that were never drift, so the docs gate cannot come back clean** — `code.renamed` reads a browser global and a filename as symbols this repository should define, so six ledger entries that were correct when written keep the gate permanently red. → §LXXIV.8
- 📋 **SH1103** (deps: —) **a gate stamp stays fresh across the ship that changes what it certified, so a red commit lands unseen** — Staleness is the HEAD sha and `ship` writes the governed docs at that same HEAD — which the JS suite reads, so a ship turns `pnpm test` red after its last green run. → §LXXIV.9
- 📋 **SH1104** (deps: SH1039 ✅) **the editable read is on no response budget, and it just grew a section that scales with the catalogue** — Sixteen responses are measured and `/agent/editable` is not one, so `include=insertable` has no ceiling and the budget fixture renders no page to measure it against. → §LXXIV.10
- 📋 **SH1121** (deps: SH1055 ✅) **the landing blueprint builds pages with no main region and no h1, which the a11y check reports on each** — marketing-site was repaired because a benchmark caught it; landing has the same defect and no assertion holds any blueprint to our own gate. → §LXXIV.11
- 📋 **SH1122** (deps: SH1056 ✅) **a site's own settings are projected to no file, so url, bindings and the locale policy cannot be authored or diffed** — A site root's _folder.md says nothing about the site, so the one object that decides whether a page renders is reachable only through the API. → §LXXIV.12
- 📋 **SH1133** (deps: —) **a redirect on a site with its own front end routes nobody, and the lint still demands one** — Redirect posts are served by the instance's renderer; a Next.js site's URLs are served by the front end, which the CDA and the SDK never tell which addresses moved. → §LXXIV.13
- 📋 **SH1134** (deps: —) **an exported site carries no asset bytes, so every File post arrives pointing at nothing** — The format has a files/ directory and the import restores from it; the site export passes an empty list, so the pages render with broken images and neither side says so. → §LXXIV.14
- 📋 **SH1135** (deps: —) **an instance cannot say what a site was replicated from, and the console cannot start a run** — Every artefact of a round lives on the CLI's disk and the instance sees only the site that came out, so the source URL behind a page is lost the moment the shell closes. → §LXXIV.15

## Priority

## Done when — SH971

- **A curator can freeze the dock's mascot from the preferences dialog** The design
  system has to ship a paused prop on VigletAssistant first, and then the stored choice
  needs a still flag beside muted.

## Done when — SH1024

- **Search, trash and review use the bar, with counts and saved views** The browser
  adopted it first; the other three surfaces, facet counts from the listing, the agent
  author option and per-user saved views are what the design still asks.

## Done when — SH1026

- **Thumbnails, an editable data table and virtualised rows** Cards show an
  image-pipeline thumbnail, the table is BentoDataTable with a persisted column picker
  whose scalar edits are refused on a stale ETag, and each view mounts only its visible
  window over BD6's fixture.

## Done when — SH1091

- **A chosen boundary, and shio post set over it** A maintainer picks the route (a
  narrowed CSRF exemption on the console merge, or If-Match on a merge an AGENT token
  may call); shio post set then changes one field under a version and is refused when
  the post moved.

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
