# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block I — Content as files (local-first authoring)

## Block J — Blueprints (appliable units) + Claude Code plugin

- 💭 **SH646** (deps: SH643 ✅) **There is no index of third-party packages and no provenance stronger than a directory listing** — Where the list lives and who says what enters it is a decision about the project rather than a change to it, and no third-party package exists yet to shape it. → §XI.21

## Block L — Agent safety & human-in-the-loop

- 💭 **SH110** (deps: SH45, SH106 ✅) **Agent budget guards** — A runaway agent should hit a per-session ceiling and report it rather than a bill, and the metering it must layer on is SH45's. → §XII.5
- 💭 **SH226** (deps: SH200 ✅) **A trashed folder is excluded by convention, not by invariant** — The trashed-folder exclusion lives in one repository's finders, and ids also resolve polymorphically through another that knows nothing about it. → §XII.10
- 💭 **SH648** (deps: SH223 ✅, SH200 ✅) **A deleted site cannot be restored, because only its content passes through the trash** — Trashing the site row needs a deleted_date of its own plus an answer for the name it still occupies, which is a schema change and a teaching error rather than a service fix. → §XII.13
- 💭 **SH650** (deps: SH225 ✅) **Nothing stops a fetch-type change from breaking authorization** — SH225 made the ACL walk independent of ShFolder.parentFolder being EAGER, but that independence is a code shape nothing checks. → §XII.16

## Block M — Token economy (measured)

- 💭 **SH230** (deps: SH174 ✅) **The manifest's weight is prose, and terse barely touches it** — About 87% of the manifest is a purpose sentence and an example per endpoint, kept verbatim on purpose, so terse saves 11% where it is worth a third elsewhere. → §XIII.10
- 💭 **SH231** (deps: SH161 ✅) **An instance's own notes travel nowhere** — A site's notes ride its export and the instance's own travel nowhere, which is right for an import and wrong for a move. → §XIII.11
- 💭 **SH232** (deps: SH159 ✅) **`/agent/changes` cannot revalidate the way it is most often called** — In window mode the response's own cursor is the moving edge of the feed, so no two polls agree and every one pays in full. → §XIII.12
- 📋 **SH651** (deps: SH396 ✅) **A post created through the console gets no friendly URL, so nothing can open it** — SH396 found five console-created posts all landing at their folder's address, which makes them unreachable by any delivery URL. → §XIII.22

## Block N — Realignment of what already shipped

- ⏳ **SH238** (deps: SH233 ✅, SH182 ✅, Block P ✅) **The Universal Editor is still not driven end to end** — The one fact the save-back rests on is checked from a browser now; the editor itself embeds site.url in an iframe, so nothing drives the panel, the fields or the save. → §XIV.15
- 📋 **SH656** (deps: —) **shio.git.* is bound, shipped with placeholder values, and read by nothing** — A configuration class no code consumes still declares real keys to the property vocabulary, so a page could document a setting that does nothing and pass the gate. → §IX.76
- 📋 **SH657** (deps: —) **Two code-editor fields are off the renderer's raw-text list, so their JSON is entity-escaped** — RAW_TEXT_FIELDS says it holds the Ace Editor CSS/JavaScript fields and holds four of six - Theme.TOKENS and SiteScripts.SCRIPTS are hand-omitted. → §IX.77
- ⏳ **SH659** (deps: SH238 ✅) **Nothing drives the Universal Editor's handshake, panel or field round trip** — SH238's ledger entry closed the save-back and recorded the drive as waiting on Block P, which shipped - so the open line is a duplicate id and the drive is unexercised. → §XIV.18
- 📋 **SH660** (deps: —) **Opening an uploaded File post issues two 404s the console swallows, so the widget resolves no path** — file.widget.tsx passes the FILE field to /v2/post/xp/{id} and /v2/folder/{id}, but SH38 stores a path there, so both lookups miss and .catch hides it. → §XIV.19
- 📋 **SH662** (deps: —) **A ledger entry carrying two shipped ids hides the second from every reader that keys on one** — SH169 shipped inside SH154's entry, so show answers never written, lint calls a prose mention a broken promise, and no ledger reader counts it either way. → §IX.78

## Block O — The human half (the console the curator uses)

- 💭 **SH243** (deps: SH55 ✅, SH202 ✅) **A trashed post still holds its friendly URL, and nothing knows it** — Every free-URL check reads through @SQLRestriction, so the trash is invisible to it and an untrashed post can collide with the name its own copy took. → §XVI.3
- 💭 **SH244** (deps: SH202 ✅) **Duplicating content is console-only** — Copy exists only as a console PUT, with no batch op, no MCP verb and no CLI path, and a folder is the case an agent cannot reproduce with upserts. → §XVI.4
- 💭 **SH245** (deps: SH139 ✅, SH202 ✅, SH203 ✅) **Make "one writer per verb" a lint, not an expectation** — Four times the console's copy of a shared verb was the careless one, and the rule that would have caught all four is stated in agents.md and enforced nowhere. → §XVI.5
- 📋 **SH654** (deps: SH14 ✅, SH107 ✅) **A curator cannot publish from the post edit screen, because it has no publish button** — The only console publish paths are the Universal Editor and approving an agent run, and the label for the missing button is a translated key nothing renders. → §XVI.72

## Block Q — Replication (an existing site becomes an authorable Shio site)

- ⏳ **SH502** (deps: SH446 ✅) **The only scaffold that renders sections cannot install, so an authorable replica has no front end** — shio-sections answers E404 and the built-in template carries no sections renderer, so a fidelity replica has two delivery paths and an authorable one has none. → §XVII.55
- 💭 **SH658** (deps: SH477 ✅) **No archetype carries an accessibility attribute, so two capture rules are exercised by literals alone** — SH477's measurement found the decorative-mark pattern zero times across every HTML the repo holds, corpus included, so a real page's shape is asserted nowhere. → §XVII.91

## Block F — Market positioning

- 💭 **SH44** (deps: SH22 ✅) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
- 💭 **SH45** (deps: SH22 ✅) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
- 💭 **SH46** (deps: SH22 ✅) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
- 💭 **SH47** (deps: SH22 ✅, SH38 ✅) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

## Block S — The public site (shio.viglet.org)

- 📋 **SH540** (deps: SH530 ✅) **Nothing on the site is answered by a running Shio** — The transcripts, the preview links and the manifest read are all recordings, so the single claim a visitor cannot check is the one the whole page rests on. → §XIX.11

## Block U — Documentation the matrix still names

- 📋 **SH652** (deps: —) **The eleven `shio audit` rules are named nowhere, so a finding cannot be acted on without reading the source** — COVERAGE has listed them as an orphan since it shipped and its own summary never counted them, which is why no block ever picked them up. → §XXI.1
- 📋 **SH653** (deps: —) **COVERAGE's summary counts are typed, and the file records that they do not reconcile with its own tables** — A status count nobody derives disagrees with the rows it summarises in the direction that hides work, which is how one orphan went uncounted for two blocks. → §XXI.2

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
