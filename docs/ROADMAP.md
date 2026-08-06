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

- 📋 **SH413** (deps: SH84 ✅) **Turning indexing on leaves the content that is already published unfindable** — SH84 indexes on publish, so an instance that switches Turing on indexes nothing until every post happens to be republished, and nothing says so. → §VIII.16

## Block K — Perception & verification loop

- 💭 **SH204** (deps: SH144 ✅) **An incident should name its site where one is knowable** — An incident carries no site, so a site-scoped read of the feed answers with the instance's failures or none, and two of the four recorders already know the site. → §IX.5
- 💭 **SH205** (deps: SH144 ✅) **The incident fold key is the human message, so one problem becomes many rows** — The fold key includes the human message, whose varying detail turns one problem into a row per variant and defeats the folding the durable table depends on. → §IX.6
- 💭 **SH206** (deps: SH141 ✅) **The delivery proof fetches inside the report's read transaction** — A delivery proof fetches front ends while holding the report's pooled connection, which SH141 bounded rather than fixed. → §IX.7
- 📋 **SH207** (deps: SH104 ✅) **The handoff has no console banner** — The handoff exists only as a PR-body artifact, so a curator who never opens a terminal cannot see what an agent did since they last looked. → §IX.8
- 📋 **SH208** (deps: —) **A backtick in the CLI's help text breaks the whole binary** — HELP is a template literal, so a backtick in the prose is a syntax error that stops the binary loading, and only the three tests that spawn it notice. → §IX.9
- 💭 **SH209** (deps: SH102 ✅) **`shio snapshot` photographs one page per run** — The useful artifact for a handoff is the set of a site's pages, and the command captures one page per run. → §IX.10
- 📋 **SH397** (deps: SH295 ✅, SH325 ✅) **A dev instance reports its version as dev, so a stale server reads as a code defect** — The build stamp comes from the jar manifest, which is absent in the one mode where the running instance can lag the working tree, and nothing composes the readiness facts. → §IX.12
- 📋 **SH423** (deps: SH347 ✅) **The test-context budget is a ceiling nobody sees approaching** — ShContextCacheHeadroomTest fails once 64 is crossed and nothing says a task is spending one, so eleven tasks took the count from 38 to 44 with no moment anybody was asked. → §VIII.20

## Block I — Content as files (local-first authoring)

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
- 📋 **SH392** (deps: SH248 ✅, SH391 ✅) **A region's JavaScript reaches the browser and nothing ever runs it** — SH248 ships a region's script to the reader on purpose, and no test, lint or gate executes it, so the one field whose value is code has no proof path at all. → §XV.28
- 📋 **SH419** (deps: SH384 ✅) **A listing loads the whole folder to render ten rows** — Measured at 213 rows per 10 rendered: limit and page are an in-memory window over a whole-folder read, so a 5000-post archive pays 5000 rows for page one. → §XV.30
- 📋 **SH420** (deps: SH384 ✅) **A nested listing clobbers the outer block's @last** — Measured: an outer row reading {{#if @last}} after an inner query renders twice, because both blocks write @index and @last into one shared data scope. → §XV.31
- 📋 **SH421** (deps: SH389 ✅) **A layout that should not carry the site's scripts is still told off** — site-scripts-dropped warns for every bound layout that omits site.scripts, and an email template or a print sheet omits them on purpose with no way to say so. → §XV.32

## Block Q — Replication (an existing site becomes an authorable Shio site)

- ⏳ **SH467** (deps: SH385 ✅) **The replica's canonical URL is the mirror of the source's, so every captured link costs a 301** — The route half answers both forms now, but the grammar still collapses the declared slash, so the replica serves /shio as canonical and 301s /shio/ — the source does the opposite. → §XVII.44
- 📋 **SH477** (deps: SH472 ✅) **A decorative two-letter mark is still the first word of a link's label** — The tile's aria-label equals its own visible text, which says it is a logo, and scanPage reads it as content. → §XVII.53
- 📋 **SH478** (deps: SH474 ✅) **The three Media Library keys are still fields the File type does not declare** — ALT, CAPTION and TAGS are written by updateMetadata and left undeclared by SH474, so a curator who captions an image turns a green verify red. → §XVII.54
- 📋 **SH502** (deps: SH446 ✅) **The only scaffold that renders sections cannot install, so an authorable replica has no front end** — shio-sections answers E404 and the built-in template carries no sections renderer, so a fidelity replica has two delivery paths and an authorable one has none. → §XVII.55

## Block F — Market positioning

- 💭 **SH44** (deps: SH22 ✅) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
- 💭 **SH45** (deps: SH22 ✅) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
- 💭 **SH46** (deps: SH22 ✅) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
- 💭 **SH47** (deps: SH22 ✅, SH38 ✅) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

## Block R — End-user documentation for 2026.3 (docs-shio)

- 📋 **SH481** (deps: SH482 ✅) **Nothing published says Shio speaks MCP, so its primary operator surface is unfindable** — POST /mcp with its tools, resources and prompts and the Claude Code plugin are how the agent drives Shio, and the word MCP appears in none of the 19 pages. → §XVIII.5
- 📋 **SH485** (deps: SH482 ✅) **Nothing tells a curator what stops an agent from surprising them** — Draft-default, dry-run, confirm tokens, the review queue, attribution and the trash are P7's whole promise to the human and none of it is published. → §XVIII.7
- 📋 **SH486** (deps: SH482 ✅, Block I ✅) **The cheapest authoring path, content as files, has no page at all** — P4 says files beat APIs for authoring, and a reader cannot learn that Edit, Write and Grep author Shio content or how the merge protects a curator's edit. → §XVIII.8
- 📋 **SH487** (deps: SH482 ✅, SH486) **The `shio` CLI is documented nowhere, so its verbs are unfindable** — pull, push, apply, verify, report, clone, propose, convert and snapshot are the scripted path into an instance, and the docs' only CLI pages are Turing's. → §XVIII.9
- 📋 **SH488** (deps: SH482 ✅) **Blueprints, the appliable starting points, are absent from the published docs** — P8 says skipping a step needs an appliable unit, and nothing says a package exists, what blueprint.json declares, or how applying one builds a site. → §XVIII.10
- 📋 **SH489** (deps: SH482 ✅, SH483 ✅) **The Universal Editor has no page, so the curator's own editing surface is unfindable** — The annotated render, the iframe states and the save-back are how a human corrects a page an agent built, and the phrase appears in none of the 19 pages. → §XVIII.11
- 📋 **SH490** (deps: SH482 ✅, SH483 ✅) **The introduction explains a product that no longer exists, so a newcomer starts wrong** — getting-started/intro and core-concepts teach Nashorn pages and shObject and never reach addresses, drafts or the division of labour a reader needs first. → §XVIII.12
- 📋 **SH491** (deps: SH482 ✅, SH483 ✅) **The architecture page draws a CMS whose engine, agent gateway and delivery path are all wrong** — It diagrams Nashorn rendering, an Elasticsearch dependency and no agent surface, so the one page an evaluator reads describes a system Shio is not. → §XVIII.13
- 📋 **SH492** (deps: SH487) **Replication shipped and no page says an existing site can become an authorable Shio site** — clone, propose and convert, fidelity against authorable mode and the /sites/** delivery grammar are Block Q's user-facing half and have no page. → §XVIII.14
- 📋 **SH493** (deps: SH482 ✅, SH22 ✅) **Multi-tenancy is undocumented and the admin guide still describes a single-instance install** — SH22's provisioning, membership, quota and suspension change how Shio is installed, administered and secured, and the word tenant appears in no page. → §XVIII.15
- 📋 **SH494** (deps: SH482 ✅) **The docs promise search out of the box while Turing indexing is off by default** — search-caching.md says content is automatically indexed with no configuration required, and indexing is opt-in and reached only on publish, so the page is a false promise. → §XVIII.16
- 📋 **SH495** (deps: SH482 ✅) **The REST, configuration and security references list endpoints, properties and rules that moved** — They predate the unified post model, the name-keyed post types, the stateless token paths and the Nashorn removal, so every table on them is unverifiable. → §XVIII.17
- 📋 **SH496** (deps: SH482 ✅, SH490) **Content modelling and the delivery SDK pages describe a model and a client that changed** — Post types are one name-keyed surface now, addresses are the SH79 grammar, and the CDA pages predate the shapes the console itself reads, so a front-end reader is misled. → §XVIII.18
- 📋 **SH497** (deps: SH484 ✅) **The one thing a buyer compares agent-native CMSs on, token cost, is unpublished** — Response budgets, terse mode and the build-a-site benchmark are Block M's measured product feature, and no page says what a call costs or how to spend less. → §XVIII.19
- 📋 **SH498** (deps: SH491) **The diagrams directory carries fourteen Turing and Dumont sources and not one Shio diagram** — Every figure in docs-shio is inline mermaid the repo's conventions reserve for small ones, and the agent loop, the render pipeline and replication have no figure at all. → §XVIII.20
- 💭 **SH499** (deps: SH479 ✅, SH487) **No published example is ever executed, so a renamed verb rots the docs silently** — This block writes CLI invocations, MCP calls and CDA requests, and nothing runs one against an instance, which is precisely how the 2018 pages became wrong. → §XVIII.21

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
