# Shio — Roadmap (active backlog)

> **One line per unshipped task** — 📋 designed · 💭 idea · ⏳ partial · 🛠 in progress.
> Shipped work is in [CHANGELOG.md](CHANGELOG.md); the design behind a line is at its
> `→ §` pointer in [IMPROVEMENTS.md](IMPROVEMENTS.md). **A line is one sentence: what + why
> + pointer** — anything longer belongs at the pointer (SH341).
>
> This file is owned by **roadkeep** and never hand-edited: `roadkeep brief` picks and briefs
> the next task, `list`/`stats` count, `deps` resolves a blocker, `gaps` says what happened to
> an id that is in neither file. Blocks with no line left are finished — see the ledger.

## Block K — Perception & verification loop

- 📋 **SH613** (deps: SH206 ✅, SH322 ✅) **A textual lint reads prose, so writing about a rule can fail the build for breaking it** — SH206 hit this and fixed one lint; 17 of the 22 source-scanning tests still match their own subject inside a comment, and nothing says which of them mean to. → §IX.64
- 📋 **SH615** (deps: SH109 ✅, SH207 ✅) **The review queue's per-run pending count never reaches zero, because publishing keeps the draft** — A run stays 'N to review' forever and its Approve button never settles, since the count asks whether a DRAFT row exists and the editable draft always does. → §IX.65
- 📋 **SH619** (deps: SH522 ✅, SH545 ✅) **A draft cannot be compared with what is live, because the read surface cannot return the published row** — Every agent read is DRAFT-preferred with no state parameter, so 'what will change when I publish this' has no answer outside the console's own diff dialog. → §IX.66
- 📋 **SH630** (deps: —) **Naming a task id in a comment marks it asserted, so a debt is paid by mentioning it** — The SH527 walk greps suite files for ids and cannot tell an assertion from prose, so three ids left the debt list in one task because a new file explained why they were relevant. → §IX.67
- 📋 **SH635** (deps: —) **The blueprint apply suite reported two verify errors once and none on four later runs** — Seen while SH572 landed a rule: applyKeepsThePackagesPromise wanted zero errors and got two, then the same code was green four times, so an order-dependent finding leaks into a UUID-named site. → §IX.68
- 📋 **SH639** (deps: SH22 ✅) **A scheduled publish never fires for any tenant but the default, because the sweep runs with no tenant bound** — ShPostUnified is @TenantId, so the finder's implicit predicate is the default discriminator and a due post of another tenant is never returned. → §IX.69

## Block I — Content as files (local-first authoring)

## Block J — Blueprints (appliable units) + Claude Code plugin

- 💭 **SH217** (deps: SH96 ✅) **The community half of the registry** — The local trust model is shipped, and nothing says how a package a stranger wrote earns a place in it. → §XI.5
- 📋 **SH216** (deps: SH156 ✅) **`blog`, `docs` and `product-catalog` carry an index page** — Three starters ship an index page and no listing over their own content, which is what a reader needs and what the section overlay deliberately does not give them. → §XI.11
- 📋 **SH218** (deps: SH119 ✅, SH156 ✅) **A release leaves the generated packages stale, and the next CI run finds out** — The release workflow rewrites the template's dependency ranges without re-running emit, so the generated packages keep the old range and the next push finds out. → §XI.12
- 📋 **SH219** (deps: SH96 ✅) **The local package directory needs a reload that is not a restart** — ShBlueprintRegistry caches on first access, so a package dropped into the configured directory is invisible until the process restarts. → §XI.13
- 📋 **SH220** (deps: SH155 ✅) **A re-apply defaults to the parameters the site was built with** — The record of a site's blueprint parameters exists and no re-apply reads it, so a value the operator misremembers rebuilds the site differently and reports ordinary work. → §XI.14
- 💭 **SH221** (deps: SH153 ✅) **The rest of the write surface should be plannable read-only too** — The plan and dryRun paths are write-free and nothing enforces it, so the next feature needing to resolve what it would create can reintroduce a write. → §XI.15
- 📋 **SH395** (deps: SH156 ✅, SH183 ✅, SH280 ✅) **A blueprint stops one step before a site anybody can look at** — No first-party package ships a theme, a layout or a binding, so applying one yields content whose design is the labelled fallback and an agent finishes the job by hand. → §XI.16
- 📋 **SH518** (deps: SH509 ✅) **A blueprint-built site cannot be seen, because the layout generator only runs on the replication path** — apply --blueprint writes 13 authorable posts and no PageLayout or Theme, so /preview answers 400 while convert writes both. → §XVII.70
- 📋 **SH604** (deps: SH599 ✅) **The plugin's prose enumerates the tool surface, so it is a copy that drifts the moment one ships** — Its README lists ten tools and four resources against a server carrying eleven and seven, so remember and diagnostics both shipped and the plugin never learned either. → §XI.18
- 📋 **SH605** (deps: SH598 ✅, SH604) **Nothing proves the published artefacts alone carry the path, since every run so far had the repo beside it** — Twelve rounds read agents.md, docs/agents and a repo-local skill without noticing, so a stranger gets a surface nobody has driven without its source tree. → §XI.19

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
- 📋 **SH394** (deps: SH132 ✅, SH208 ✅) **The CLI's discovery surface has no budget, so it reads as free** — tools/list is capped at 2375 tokens and checked every build while shio --help is 14 KB nobody measures, so the two surfaces cannot be compared on the cost that decides between them. → §XIII.13
- 📋 **SH396** (deps: SH112 ✅, SH395) **The build-a-site benchmark ends at verify, before anything renders** — Its four calls stop at a lint, so the measured job is content that passes checks rather than a page a person can open, and the traps between the two are unmeasured. → §XIII.14

## Block N — Realignment of what already shipped

- ⏳ **SH238** (deps: SH233 ✅, SH182 ✅, Block P ✅) **The Universal Editor is still not driven end to end** — The one fact the save-back rests on is checked from a browser now; the editor itself embeds site.url in an iframe, so nothing drives the panel, the fields or the save. → §XIV.15

## Block O — The human half (the console the curator uses)

- 📋 **SH241** (deps: SH182 ✅) **No CI workflow runs on the branch the work happens on** — Two workflows are dispatch-only and the third triggers on a branch this repository does not develop on, so nothing runs on a push. → §XVI.1
- 💭 **SH243** (deps: SH55 ✅, SH202 ✅) **A trashed post still holds its friendly URL, and nothing knows it** — Every free-URL check reads through @SQLRestriction, so the trash is invisible to it and an untrashed post can collide with the name its own copy took. → §XVI.3
- 💭 **SH244** (deps: SH202 ✅) **Duplicating content is console-only** — Copy exists only as a console PUT, with no batch op, no MCP verb and no CLI path, and a folder is the case an agent cannot reproduce with upserts. → §XVI.4
- 💭 **SH245** (deps: SH139 ✅, SH202 ✅, SH203 ✅) **Make "one writer per verb" a lint, not an expectation** — Four times the console's copy of a shared verb was the careless one, and the rule that would have caught all four is stated in agents.md and enforced nowhere. → §XVI.5

## Block Q — Replication (an existing site becomes an authorable Shio site)

- 📋 **SH477** (deps: SH472 ✅) **A decorative two-letter mark is still the first word of a link's label** — The tile's aria-label equals its own visible text, which says it is a logo, and scanPage reads it as content. → §XVII.53
- 📋 **SH478** (deps: SH474 ✅) **The three Media Library keys are still fields the File type does not declare** — ALT, CAPTION and TAGS are written by updateMetadata and left undeclared by SH474, so a curator who captions an image turns a green verify red. → §XVII.54
- ⏳ **SH502** (deps: SH446 ✅) **The only scaffold that renders sections cannot install, so an authorable replica has no front end** — shio-sections answers E404 and the built-in template carries no sections renderer, so a fidelity replica has two delivery paths and an authorable one has none. → §XVII.55
- 📋 **SH568** (deps: SH555 ✅) **A held finding pins the mechanism of a fix, so the corpus stays red on a defect that is closed** — layout-raw-field-CSS asserts the template does not spell the escaped field, but SH461 put the cure on the value, so SH555's SafeString closed it and the entry cannot go green. → §XVII.79
- 📋 **SH606** (deps: SH555 ✅) **A corpus assertion and the fix that closed its task disagree about whether two braces are a defect** — SH555 made those fields SafeString at render and the corpus still fails the generated layout for emitting {{CSS}}, so one of the two is wrong and no run says which. → §XVII.89
- 📋 **SH611** (deps: SH592 ✅) **An asset is re-read only when its page moved, so a byte that changed under a still page is the old one** — The 403 assets hold the 52.7 MB, and --refresh either skips them all or re-downloads them all - the cheap question answered well and the expensive one not at all. → §XVII.90

## Block F — Market positioning

- 💭 **SH44** (deps: SH22 ✅) **Superadmin impersonation** — let a privileged operator act as / switch into any tenant from the console (audited). → §VI.1
- 💭 **SH45** (deps: SH22 ✅) **Tenant quotas & billing** — per-tenant usage metering + quota enforcement + billing-export hooks (SH110 builds on this). → §VI.1
- 💭 **SH46** (deps: SH22 ✅) **Nav/feature gating by tenant** — hide SaaS surfaces on single-tenant installs. → §VI.1
- 💭 **SH47** (deps: SH22 ✅, SH38 ✅) **Per-tenant file storage** — prefix `tenantId` into the id-keyed `ShFileStorageService` layout from `ShTenantContext`. → §VI.1

## Block S — The public site (shio.viglet.org)

- 📋 **SH540** (deps: SH530 ✅) **Nothing on the site is answered by a running Shio** — The transcripts, the preview links and the manifest read are all recordings, so the single claim a visitor cannot check is the one the whole page rests on. → §XIX.11

## Block T — End-user documentation, round two (the curator's half)

- 📋 **SH623** (deps: —) **The administration guide describes AngularJS screens the React console replaced** — A page that describes a shape the product left is worse than a missing one, and dashboard, Media Library, the static-file manager, the webhooks UI and the config pages are all read through it. → §XX.2
- 📋 **SH624** (deps: —) **None of the curator's lifecycle tools are documented** — Preview tokens, draft-vs-published diff and restore, scheduled publish, the activity view and the trash are five shipped surfaces a curator cannot discover from any page. → §XX.3
- 📋 **SH625** (deps: —) **The content console has no end-user page at all** — Block R documented the agent's half well and left the surface a curator is in every day undocumented: the browser, the post form, copy/move, select-everything and the trash. → §XX.1
- 📋 **SH626** (deps: —) **Outbound webhooks are documented only from the consumer side** — SH12 shipped per-site subscriptions and a signed payload, and no page describes the subscription, the payload shape or how to verify the signature. → §XX.4
- 📋 **SH627** (deps: —) **Content i18n has no page, so the locale axis is undiscoverable** — SH53 shipped linked translations, CDA locale and the checked locale segment, and a reader meets the segment first in a URL nobody explained. → §XX.5
- 📋 **SH628** (deps: —) **The developer guide names a stack this product left, and neither the agent surface nor the CLI** — It still lists Elasticsearch 9.3.3, so the page a contributor reads first is actively misleading rather than merely thin. → §XX.6
- 📋 **SH629** (deps: —) **The image epic's operational half is undocumented** — The transform parameters are on the render page and the result cache, the dimension and format limits and signed-URL configuration are the operator's questions, answered nowhere. → §XX.7

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
