# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block H — The Agent Gateway (MCP + agent-shaped protocol)

- 💭 **SH84** (deps: SH22 ✅, SH83 ✅) **Turing ecosystem wiring** — the SH22 tenant sweep and its test shipped; what remains is the Turing integration (replaces old SH37). → §VIII.9
- 💭 **SH140** (deps: SH100 ✅, SH134 ✅) **Report inbound links when a post moves** — A URL typed into another post's rich text names the address rather than the object, so a move breaks it and nothing tells the agent which pages it just broke. → §VIII.13
- 💭 **SH197** (deps: SH136 ✅) **Finish the byte-stability class** — A hash-ordered Map.of reaching a serialized map component is the SH136 defect the lint does not ban, and no live instance is affected yet. → §VIII.14
- 📋 **SH393** (deps: SH338 ✅, SH268 ✅) **Which surface owns a capability is re-argued per task** — Six shipped tasks each re-derived the MCP-or-CLI boundary from scratch, and the P1 conformance test records reachability without recording which surface was chosen. → §VIII.15

## Block K — Perception & verification loop

- 💭 **SH204** (deps: SH144 ✅) **An incident should name its site where one is knowable** — An incident carries no site, so a site-scoped read of the feed answers with the instance's failures or none, and two of the four recorders already know the site. → §IX.5
- 💭 **SH205** (deps: SH144 ✅) **The incident fold key is the human message, so one problem becomes many rows** — The fold key includes the human message, whose varying detail turns one problem into a row per variant and defeats the folding the durable table depends on. → §IX.6
- 💭 **SH206** (deps: SH141 ✅) **The delivery proof fetches inside the report's read transaction** — A delivery proof fetches front ends while holding the report's pooled connection, which SH141 bounded rather than fixed. → §IX.7
- 📋 **SH207** (deps: SH104 ✅) **The handoff has no console banner** — The handoff exists only as a PR-body artifact, so a curator who never opens a terminal cannot see what an agent did since they last looked. → §IX.8
- 📋 **SH208** (deps: —) **A backtick in the CLI's help text breaks the whole binary** — HELP is a template literal, so a backtick in the prose is a syntax error that stops the binary loading, and only the three tests that spawn it notice. → §IX.9
- 💭 **SH209** (deps: SH102 ✅) **`shio snapshot` photographs one page per run** — The useful artifact for a handoff is the set of a site's pages, and the command captures one page per run. → §IX.10
- 📋 **SH391** (deps: SH102 ✅, SH265 ✅) **A browser runs in this repo twice and neither output is for the agent** — The console e2e drives the console and snapshot draws a PNG for a human, so the one failure class no digest reaches is still checked by asking somebody to look at it. → §IX.11
- 📋 **SH397** (deps: SH295 ✅, SH325 ✅) **A dev instance reports its version as dev, so a stale server reads as a code defect** — The build stamp comes from the jar manifest, which is absent in the one mode where the running instance can lag the working tree, and nothing composes the readiness facts. → §IX.12

## Block I — Content as files (local-first authoring)

- 📋 **SH398** (deps: SH375 ✅) **A media listing stats every file it lists, and a settled pull asks for a digest per asset** — Size, content type and sha-256 are read from disk on every listing and every comparison, though each is fixed the moment the bytes are written. → §X.1

## Block J — Blueprints (appliable units) + Claude Code plugin

- 💭 **SH217** (deps: SH96 ✅) **The community half of the registry** — The local trust model is shipped, and nothing says how a package a stranger wrote earns a place in it. → §XI.5
- 📋 **SH216** (deps: SH156 ✅) **`blog`, `docs` and `product-catalog` carry an index page** — Three starters ship an index page and no listing over their own content, which is what a reader needs and what the section overlay deliberately does not give them. → §XI.11
- 📋 **SH218** (deps: SH119 ✅, SH156 ✅) **A release leaves the generated packages stale, and the next CI run finds out** — The release workflow rewrites the template's dependency ranges without re-running emit, so the generated packages keep the old range and the next push finds out. → §XI.12
- 📋 **SH219** (deps: SH96 ✅) **The local package directory needs a reload that is not a restart** — ShBlueprintRegistry caches on first access, so a package dropped into the configured directory is invisible until the process restarts. → §XI.13
- 📋 **SH220** (deps: SH155 ✅) **A re-apply defaults to the parameters the site was built with** — The record of a site's blueprint parameters exists and no re-apply reads it, so a value the operator misremembers rebuilds the site differently and reports ordinary work. → §XI.14
- 💭 **SH221** (deps: SH153 ✅) **The rest of the write surface should be plannable read-only too** — The plan and dryRun paths are write-free and nothing enforces it, so the next feature needing to resolve what it would create can reintroduce a write. → §XI.15
- 📋 **SH395** (deps: SH156 ✅, SH183 ✅, SH280 ✅) **A blueprint stops one step before a site anybody can look at** — No first-party package ships a theme, a layout or a binding, so applying one yields content whose design is the labelled fallback and an agent finishes the job by hand. → §XI.16

## Block L — Agent safety & human-in-the-loop

- 💭 **SH110** (deps: SH45, SH106 ✅) **Agent budget guards** — A runaway agent should hit a per-session ceiling and report it rather than a bill, and the metering it must layer on is SH45's. → §XII.5
- 📋 **SH222** (deps: SH55 ✅, SH200 ✅) **An agent can fill the trash and cannot read it** — Two delete ops promise a restorable trash, and no agent read, MCP tool or CLI command can list or restore it. → §XII.6
- 📋 **SH223** (deps: SH38 ✅, SH51 ✅, SH200 ✅) **Deleting a site is SH200 one level up, and worse** — Deleting a site cascades through JPA, so every post in it skips the trash, the audit, the feed and the DELETE event. → §XII.7
- 📋 **SH224** (deps: SH55 ✅, SH200 ✅) **A permanent deletion is the one write nobody records** — purge fires the lifecycle event and writes no audit row, so the irreversible half of the trash is the one nobody can attribute. → §XII.8
- 📋 **SH225** (deps: SH54 ✅, SH201 ✅) **The ACL check depends on `open-in-view` and does not say so** — requireRead and requireWrite reach the folder-chain walk by self-invocation, so the transaction is whatever the caller happened to have — open-in-view in the app, nothing elsewhere. → §XII.9
- 💭 **SH226** (deps: SH200 ✅) **A trashed folder is excluded by convention, not by invariant** — The trashed-folder exclusion lives in one repository's finders, and ids also resolve polymorphically through another that knows nothing about it. → §XII.10

## Block M — Token economy (measured)

- 📋 **SH227** (deps: SH132 ✅, SH157 ✅) **`shio_apply` cannot ask for its own proof** — At 457 tokens against a 460 ceiling, the write most likely to want a lint is the one that cannot ask for one. → §XIII.7
- 📋 **SH228** (deps: SH157 ✅) **A write proves only its posts** — The write proof lints the post addresses a run touched and ignores the rest, so a batch of folder upserts comes back with no proof at all. → §XIII.8
- 📋 **SH229** (deps: SH116 ✅, SH159 ✅) **Nothing checks that an ETag covers every parameter that changes the bytes** — An ETag that omits a parameter which changes the bytes lets one projection revalidate against another's cache, and nothing checks the coverage. → §XIII.9
- 💭 **SH230** (deps: SH174 ✅) **The manifest's weight is prose, and terse barely touches it** — About 87% of the manifest is a purpose sentence and an example per endpoint, kept verbatim on purpose, so terse saves 11% where it is worth a third elsewhere. → §XIII.10
- 💭 **SH231** (deps: SH161 ✅) **An instance's own notes travel nowhere** — A site's notes ride its export and the instance's own travel nowhere, which is right for an import and wrong for a move. → §XIII.11
- 💭 **SH232** (deps: SH159 ✅) **`/agent/changes` cannot revalidate the way it is most often called** — In window mode the response's own cursor is the moving edge of the feed, so no two polls agree and every one pays in full. → §XIII.12
- 📋 **SH394** (deps: SH132 ✅, SH208) **The CLI's discovery surface has no budget, so it reads as free** — tools/list is capped at 2375 tokens and checked every build while shio --help is 14 KB nobody measures, so the two surfaces cannot be compared on the cost that decides between them. → §XIII.13
- 📋 **SH396** (deps: SH112 ✅, SH395) **The build-a-site benchmark ends at verify, before anything renders** — Its four calls stop at a lint, so the measured job is content that passes checks rather than a page a person can open, and the traps between the two are unmeasured. → §XIII.14

## Block N — Realignment of what already shipped

- ⏳ **SH238** (deps: SH233 ✅, SH182 ✅, Block P) **The Universal Editor is still not driven end to end** — The one fact the save-back rests on is checked from a browser now; the editor itself embeds site.url in an iframe, so nothing drives the panel, the fields or the save. → §XIV.15

## Block O — The human half (the console the curator uses)

- 📋 **SH241** (deps: SH182 ✅) **No CI workflow runs on the branch the work happens on** — Two workflows are dispatch-only and the third triggers on a branch this repository does not develop on, so nothing runs on a push. → §XVI.1
- 📋 **SH242** (deps: SH241) **An unmet peer range is not a build failure** — pnpm resolved a peer range it did not satisfy, printed a warning nobody reads and failed nothing, and the console bundle became undeliverable. → §XVI.2
- 💭 **SH243** (deps: SH55 ✅, SH202 ✅) **A trashed post still holds its friendly URL, and nothing knows it** — Every free-URL check reads through @SQLRestriction, so the trash is invisible to it and an untrashed post can collide with the name its own copy took. → §XVI.3
- 💭 **SH244** (deps: SH202 ✅) **Duplicating content is console-only** — Copy exists only as a console PUT, with no batch op, no MCP verb and no CLI path, and a folder is the case an agent cannot reproduce with upserts. → §XVI.4
- 💭 **SH245** (deps: SH139 ✅, SH202 ✅, SH203 ✅) **Make "one writer per verb" a lint, not an expectation** — Four times the console's copy of a shared verb was the careless one, and the rule that would have caught all four is stated in agents.md and enforced nowhere. → §XVI.5

## Block P — The visible site (what the human actually looks at)

- ⏳ **SH356** (deps: SH341 ✅) **The rationale file carries the debt the roadmap just shed** — All four line-level defects are repaired now that SH361 renumbered the duplicate SH347; the 7 over-long sections that remain have no sanctioned repair, because no verb shortens a live section. → §XVI.66
- 📋 **SH363** (deps: —) **A class comment can claim its route is authenticated and nothing checks** — ShRenderAPI said it sits under anyRequest().authenticated() while /preview/** was in permitAll, and SH359 fixed that one instance without adding anything that would find the next. → §XV.25
- 💭 **SH364** (deps: SH247 ✅) **A rendered form posts nowhere, and the design for where it should post is written** — 2018's createPost plus folder says a submission becomes a post in a named folder, and the only thing between that and shipping is a public unauthenticated write nobody has scoped. → §XV.26
- 💭 **SH365** (deps: SH265 ✅) **shio snapshot's --url is two arguments wearing one name** — It is read as the page to photograph and as the instance base URL, so the flag that says where Shio is also decides what gets photographed. → §XV.27
- 📋 **SH392** (deps: SH248 ✅, SH391) **A region's JavaScript reaches the browser and nothing ever runs it** — SH248 ships a region's script to the reader on purpose, and no test, lint or gate executes it, so the one field whose value is code has no proof path at all. → §XV.28

## Block Q — Replication (an existing site becomes an authorable Shio site)

- 📋 **SH377** (deps: SH268 ✅, SH391) **Nothing captures a site that already exists** — Replicating a live site starts with an inventory of its pages, assets, locales and forms, and no command crawls one, so the work cannot even be scoped. → §XVII.1
- 📋 **SH378** (deps: SH145 ✅, SH377) **A captured page references bytes nothing downloads** — The projection reads assets off disk, and nothing fetches the original's images, fonts and stylesheets into the tree or rewrites their URLs to asset paths. → §XVII.2
- 📋 **SH379** (deps: SH95 ✅, SH191 ✅, SH377) **Captured markup can only become a frozen copy** — Fidelity wants the original's HTML verbatim and authorability wants sections, and nothing proposes the mapping between them, so a replica is one or the other. → §XVII.3
- 📋 **SH380** (deps: SH195 ✅, SH378) **An imported stylesheet lands as one opaque blob** — A Theme carries CSS and DTCG tokens, and a capture that writes only the CSS yields a replica nobody can restyle, though the source's own values are what a token set is. → §XVII.4
- 📋 **SH381** (deps: SH183 ✅) **A navigation row carries no URL, so a menu cannot link** — The navigation helper yields id, name and title over the folder tree while its own docstring promises a url, so the helper a site's menu needs cannot emit an href. → §XVII.5
- 📋 **SH382** (deps: SH381) **A site's menu is the folder tree or nothing** — Ordered labels, nested groups and external links are what a real menu is and no folder chain expresses any of them, so the nav gets hand-written into every layout. → §XVII.6
- 📋 **SH383** (deps: SH348 ✅, SH323 ✅) **The renderer knows nothing about locale** — SH348 made the tree locale-aware and the render model stayed monolingual: no sibling translations in scope, no locale in the route and no switcher a six-language site needs. → §XVII.7
- 📋 **SH384** (deps: SH247 ✅, SH259 ✅) **A listing has a ceiling and no second page** — query takes folder, type and limit only, so an archive past its limit has no page two and a hub filtered by industry or tag cannot be written at all. → §XVII.8
- 📋 **SH385** (deps: SH377) **A replica silently abandons the original's URL space** — The capture knows every source URL and nothing records the source-to-replica map or serves a redirect, so every inbound link to a path the replica renamed is lost. → §XVII.9
- 📋 **SH386** (deps: SH265 ✅, SH365, SH391) **Nothing proves the replica looks like the original** — snapshot compares a page against its own earlier capture, so the comparison a replication needs — the source as the baseline, at several widths — cannot be asked for. → §XVII.10
- 📋 **SH387** (deps: SH212 ✅, SH377) **Fidelity has no report, so coverage is a feeling** — Which source pages, links, assets and form fields never arrived is the question a replication asks, and it is provable from the tree and the inventory with no instance. → §XVII.11
- 💭 **SH388** (deps: SH337 ✅) **A page's head is markup nobody checks** — Title, description, og and canonical tags are template freedom with no convention and no lint, so sixty replica pages can share one description and verify stays green. → §XVII.12
- 💭 **SH389** (deps: SH183 ✅, SH248 ✅) **A site-wide script is copied into every layout** — Consent, analytics and chat belong to the site and the only slots are a layout's or a theme's own fields, so a replica repeats them per layout and their order is per copy. → §XVII.13
- 💭 **SH390** (deps: SH145 ✅, SH210 ✅) **One asset, one size** — file_url yields a single path, so no template can emit a srcset and a replica ships the desktop image to a phone where the original it is being compared against does not. → §XVII.14

## Block F — Market positioning

- 💭 **SH44** (deps: SH22 ✅) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
- 💭 **SH45** (deps: SH22 ✅) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
- 💭 **SH46** (deps: SH22 ✅) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
- 💭 **SH47** (deps: SH22 ✅, SH38 ✅) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

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
