# Shio — Improvements & Roadmap (design rationale)

> **What this file is:** the *design rationale* (the what & why) for **unshipped**
> work — the spec behind each backlog item. It is one of three roadmap documents;
> keep them in sync:
>
> | File | Holds | One line per |
> |---|---|---|
> | [ROADMAP.md](ROADMAP.md) | **Task status** (single source of truth) — active backlog | task |
> | [CHANGELOG.md](CHANGELOG.md) | What has **shipped** (git is authoritative) | shipped task |
> | **IMPROVEMENTS.md** (this file) | Design rationale for active sections | — |
>
> **Founding rule:** once a task ships, its detail is removed from here and
> reduced to a one-line entry in CHANGELOG.md — `git log` is the authoritative
> history. Do not re-accrete shipped implementation reports into this file.
> (Sections §I–§V and §VII were collapsed to pointers when Blocks H–M landed; the
> numbering is preserved so existing `docs/specs/*` cross-references still resolve.)

**Status legend** (status itself lives in ROADMAP.md): ⏳ partial · 📋 designed · 💭 idea.

**Section order** below follows the **suggested work order** in ROADMAP.md
(H → K → I → J → L → M → N), not the block letters — §VIII is Block H, §IX is
Block K, §X is Block I, and so on. Each section header names its block.

**Cross-references** use relative paths from `docs/`; linked code may have moved —
verify against `git ls-files` before acting.

## Table of contents

- [0. Strategy — the agent-native reorientation](#0-strategy--the-agent-native-reorientation)
- **Part 1 — the reorientation (active)**
  - [VIII. The Agent Gateway (Block H)](#viii-the-agent-gateway-block-h)
  - [IX. Perception & verification loop (Block K) — ✅ shipped whole](#ix-perception--verification-loop-block-k--✅-shipped-whole)
  - [X. Content as files (Block I)](#x-content-as-files-block-i)
  - [XI. Blueprints + Claude Code plugin (Block J)](#xi-blueprints--claude-code-plugin-block-j)
  - [XII. Agent safety & human-in-the-loop (Block L)](#xii-agent-safety--human-in-the-loop-block-l)
  - [XIII. Token economy, measured (Block M) — what the measurement opened](#xiii-token-economy-measured-block-m--what-the-measurement-opened)
  - [XIV. Realignment of what already shipped (Block N) — what building it opened](#xiv-realignment-of-what-already-shipped-block-n--what-building-it-opened)
  - [XV. The visible site (Block P)](#xv-the-visible-site-block-p)
- **Part 2 — pre-existing sections**
  - [I. Headless foundation — the CDA](#i-headless-foundation--the-cda) (shipped)
  - [II. JavaScript-for-pages DX](#ii-javascript-for-pages-dx) (shipped)
  - [III. Composable platform primitives](#iii-composable-platform-primitives) (shipped)
  - [IV. Server-side JS rendering — dropped](#iv-server-side-js-rendering--dropped)
  - [V. Legacy retirement](#v-legacy-retirement) (shipped)
  - [VI. Market positioning](#vi-market-positioning)
  - [VII. Content portability](#vii-content-portability) (shipped)

---

## 0. Strategy — the agent-native reorientation

> Full concept, protocol design and worked example:
> [specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md).

### 0.1 The goals, restated

The original brief mixed two goals — *"be relevant to today's market"* (an
architecture decision: headless/API-first) and *"let JavaScript developers build
pages"* (a DX decision). Both shipped: the CDA (Block A), the SDKs and starter
(Block B), post-types as typed code (SH62).

A third goal now supersedes them in priority, without contradicting either:

3. **"Be the CMS a coding agent can drive fast."** Optimize the whole system so an
   agent — Claude Code specifically — needs fewer cycles, fewer tokens, and fewer
   human round trips to build and then *change* a site.

This is again **not** a language decision, and again not a rewrite. It is a
decision about **protocol shape, discoverability, determinism and verifiability**.

### 0.2 What Shio is today (the facts that drive the decision)

- The headless foundation is **done**: versioned CDA with scopes/env keys/caching
  (SH1–SH5), framework-agnostic + React SDKs (SH6/SH7), Next.js starter with
  SSG/ISR/on-demand revalidation (SH8/SH9), type generation (SH11), post-types as
  typed TS code with a drift guard and a console lock (SH62–SH66), webhooks,
  preview, versioning UI, scheduling, audit, search, i18n, ACL, soft-delete
  (SH12–SH16, SH51–SH55), multi-tenancy (SH22), media + transforms (SH38–SH43,
  SH73), a full exchange/portability engine (SH68–SH72).
- Every one of those is shaped for a **human operator**: entity-shaped JSON, UUID
  addressing, a GUI-first console, docs written as prose recipes.
- The primitives an agent-native protocol needs are therefore *already here* —
  declarative sources, canonical serialization, plan/apply (`push --check`), an
  exchange package format, an audit trail, soft-delete, scoped tokens. What is
  missing is the **shape**, not the machinery.

### 0.3 Why NOT rewrite the backend (binding non-goal — unchanged)

- A rewrite costs 6–18 months just to **reach parity** — zero new user value.
- It **destroys the Turing/Dumont synergy**: shared models, security, SDKs become
  an HTTP-only island that must be re-integrated.
- **The language is not the market problem.** Reference composable CMSs are Java
  (Magnolia, dotCMS) or JS; PHP (WordPress/Drupal) *is* the legacy we want to move
  past; Go is great for infra but rare for CMS with weak admin/templating ecosystem.
- **A CMS's differentiator is never the server language** — it's the content
  model, the API, the editing experience, and delivery. Under the new orientation
  it is the *protocol*: an agent cannot tell what the server is written in, only
  how many calls it costs.
- Conclusion: rewrite = **high cost, high risk, no perceived value**. Keep Java.

### 0.4 Why in-JVM JS/Groovy templating is not the answer

Groovy would solve *server-side scripting*, but the audience never asked for it.
The same now applies to **GraalJS** (the former Block D, SH17/SH18 — **dropped**):
an agent builds the front end in Next.js, where it is strongest and where the SSG/
ISR/preview story already exists (SH8/SH9). An in-JVM script engine adds sandboxing
risk, a second template language, and hot-reload complexity, and buys the agent
nothing. The answers stay: **headless + Next.js** (Block B, shipped) for delivery,
and the **agent protocol** (Block H) for authoring.

### 0.5 The north star

> **Shio is a CMS whose primary operator is a coding agent.** The agent builds and
> maintains the site; the **human curates** — approves, corrects, tweaks; the
> **CDA delivers**. Java/Spring stays the content engine, JS/TS stays the delivery
> layer, and every new decision is judged by *agent cycles and tokens*, not clicks.

The three actors and their interfaces:

| Actor | Interface | Job |
|---|---|---|
| **Agent** (Claude Code) | MCP tools + content files on disk + CLI | Build & maintain: model, structure, content, front end, verification |
| **Human** | React console, Universal Editor (SH10), preview links, PR review | Curate: approve, correct, tweak copy, decide |
| **Front end** | CDA (REST/GraphQL) + SDKs | Deliver |

Note what this does *for the human*: with the agent doing data entry and
scaffolding, the console stops being a form-filling tool and becomes a review and
visual-tweak surface. That is a better console, not a deprecated one — and it is
why the Universal Editor and Media Library get *more* important, not less.

### 0.6 The ten design laws

Binding. A feature that violates one is wrong even if it is requested. Each new
task in Blocks H–N traces to at least one.

| # | Law | Consequence |
|---|---|---|
| **P1** | **Agent-first design order** | Every capability lands MCP → files/CLI → REST → console. A console-only feature is incomplete. |
| **P2** | **One call replaces a session** | Discovery is a product feature (manifest + context pack), not a docs problem. |
| **P3** | **Tokens are a measured budget** | Response budgets + a CI benchmark; a token regression is a build failure. |
| **P4** | **Files beat APIs for authoring** | Project content to disk; the agent's cheapest tools are `Edit`/`Write`/`Grep`. |
| **P5** | **Determinism over cleverness** | Stable order, path addressing, idempotent writes, byte-identical serialization. |
| **P6** | **Errors are instructions** | Every 4xx carries `fix`/`allowed`/`didYouMean`/`example`. |
| **P7** | **Never surprise the human** | Draft-default, dry-run, explicit publish, confirm tokens, attribution, review queue. |
| **P8** | **Skipping steps needs appliable units** | Blueprints, not prose recipes. |
| **P9** | **Close the perception loop** | Cheap textual proof (lint, routes, render digest) instead of a human round trip. |
| **P10** | **Compose, don't fork** | MCP is a shape over the CDA + console services, never a third contract. |

### 0.7 What Shio deliberately is *not*

**Shio hosts no model.** No bundled LLM, no prompt runner, no provider API keys in
the CMS, no "generate this page for me" button. Shio is the substrate an external
agent drives. The reasoning:

- The defensible asset is **protocol quality** — discoverability, determinism,
  verifiability, token economy. Inference is a commodity that someone else
  operates better.
- Embedding inference imports another vendor's rate limits, cost model, safety
  posture and outage surface into the content engine.
- The agent already *is* the intelligence, and it is on the other side of the tool
  call with far more context than the CMS could ever pass it.

Corollary: "agent-native" is measured in tool calls and tokens, never in how much
AI the product contains.

---

# Part 1 — the reorientation (active design)

## VIII. The Agent Gateway (Block H)

> The core deliverable: an MCP surface designed for an agent. **Supersedes the old
> SH24 epic** (SH32–SH37), which was CRUD-over-MCP — it would have faithfully
> reproduced every discovery and write cost it was meant to remove. Full protocol
> design: [specs/SH74-agent-native-cms.md](specs/SH74-agent-native-cms.md) §4.

### VIII.16 Reindex an instance that was already full (SH413)

SH84's seam is `AFTER_COMMIT` on a lifecycle event, which is the right seam and covers
exactly one case: content published *after* indexing was switched on. The operator who
turns it on is normally doing so for a site that already exists, and for them the index
stays empty with no error anywhere — the honest failure of SH84's own design, and the
one that reads as "the integration does not work".

What it needs is a backfill over the `PUBLISHED` rows of one site, built through the
same `ShTuringIndexService.indexItem` the listener uses so there is one mapping and not
two, and reporting what it sent rather than returning silently (P9). It is an operation
an agent invokes, unlike the routing, which is deployment configuration — so P1 puts it
on the agent surface as a batch op rather than in the console, and the `searchIndexing`
manifest flag is what tells a caller whether invoking it would do anything at all.

The bound matters more than the shape: a site with ten thousand posts must not become
ten thousand queued jobs on the single-threaded sender, which is the queue-full incident
SH84 already reports arriving in bulk. A page size, and a report that says how far it
got, are the difference between a backfill and an outage of the publish path.

### VIII.20 A budget with a wall and no gauge gets spent until it stops the build (SH423)

SH347 made the Spring context-cache ceiling visible by asserting it: 64, configured
rather than defaulted, with a margin of 8 and the count printed on every run. That
closed the failure it was written for — an evicted context is a correctness problem, not
a slow one.

What it does not do is price the next one. A task that adds `@SpringBootTest(properties
= "spring.datasource.url=…mem:shio-shNNN…")` spends a slot silently; the number moves;
nothing asks. Measured over Block Q: eleven tasks took it from 38 to 44, and the only
reason it is not higher is that two of the last three deliberately extended an existing
fixture instead — a judgement call made because the count happened to be in view, not
because anything prompted it.

Two things would help and they are different. A **gauge**: the run already prints the
count, so the same assertion could warn on approach rather than only fail at the wall —
the shape `token-budgets.properties` has, where a ceiling is a reviewable number and a
diff that raises it is the moment somebody is asked. And a **cheaper default**: most of
these isolate only because a fixture writes instance-wide post-types, which is SH246's
real reason. A shared context with per-test site scoping would need no slot at all, and
the tasks that cannot have it would then be the exception rather than the habit.

## IX. Perception & verification loop (Block K) — ✅ shipped whole

> The agent cannot see. Two of the three human round trips in a typical build existed only
> for that reason, which made this the cheapest large win after Block H — a human cycle is
> the most expensive unit in the system by orders of magnitude.
>
> ✅ **Shipped whole**: SH99–SH104, SH141–SH144, SH154, SH169. The implementation reports
> live in [CHANGELOG.md](CHANGELOG.md) Block K; per the maintenance rules the design
> subsections are gone rather than left behind as history `git log` already holds.
>
> **What the block established, and what later work should not relitigate:** `ok` is false
> only for an *error*, because a gate that fails on what the CMS cannot prove is a gate
> people bypass — and every mechanism the block added afterwards (SH141's delivery proof,
> SH142's accepted findings, SH114-declared front-end routes) turns a warning into a fact or
> an explicit decision instead of tightening the gate. Everything the agent reads is cheap and
> textual; the one expensive artifact, SH102's PNG, is scoped for a human and stays off the
> agent surface (`visualSnapshot` is still `false`, correctly — there is no call for it).
> **§XV.4 (SH187)** is where that scoping is revisited for the other reader.
>
> The subsections below are the **open follow-ups closing the block produced**. They are here
> and not in the changelog because none of them has shipped.

### IX.5 An incident should name its site where one is knowable (SH204)

SH144 folded incidents into the SH88 feed, and it had to add a note saying `site=` does not
narrow them: an incident carries no site, so a site-scoped read gets the instance's failures or
none. That note is honest and it is not a fix — it describes a hole rather than closing one.

The reason it shipped that way is worth keeping in view, because it is the argument *against*
doing this carelessly. Two of the four recorders know a site: the webhook dispatcher holds the
`ShSiteWebhook` it was delivering for, and the scheduled publish holds the post. Two often
cannot: a 5xx on `/api/v2/agent/batch` belongs to a request, not a site, and a failed image
transform belongs to a file that may be referenced from several. So this is not "add a column
and populate it" — it is "populate it where it is known, and keep the unattributed rows visible
to the callers who may see them", which is exactly the rule the change feed already applies to
audit rows whose site was not recorded (`unrestricted` sees them, a token-scoped read does not).

The payoff is not only narrower reads: `shio report --site` currently shows one site's changes
next to the whole instance's failures, which is the kind of mismatch a reviewer notices once and
then distrusts.

### IX.6 The incident fold key is the human message (SH205)

Folding is the load-bearing decision in SH144 — it is what makes a durable table affordable,
because the row count tracks the number of distinct *problems* rather than the traffic. The key
is `(kind, subject, message)`, and the message is prose written for a human: *"gave up after 3
attempt(s), last status 500"*. The same broken webhook answering 503 on the next publish is a
different message, therefore a different row.

So folding fails precisely where it matters most — a flapping target, which is the highest-volume
case — and the rows it fails to fold then push against the cap and evict unrelated incidents.
The failure is invisible: the report looks like several distinct problems, which is what it
would look like if there really were several.

The fix is the split SH142 already needed for a different reason: an identity separate from its
prose. A `signature` — kind plus subject plus a normalized cause, with the varying detail
stripped — folds correctly, while `message` keeps the detail of the most recent occurrence for
whoever reads it. The normalization is the whole design question, and the safe default is a
short allow-list of things known to vary (an attempt count, a status code, a duration) rather
than a clever generic scrubber, because a scrubber that over-normalizes folds two genuinely
different failures into one and there is nothing in the payload that would show it.

### IX.7 The delivery proof fetches inside a read transaction (SH206)

SH141 runs its network pass inside `verify()`'s read-only transaction, holding a pooled
connection for the duration. That was accepted with the cost written down and bounded three ways
— a 50-request cap, a 15-second wall-clock budget, fail-fast per unreachable origin — which
makes it *safe*, not right: on a slow front end a verification call still occupies a connection
for fifteen seconds doing no database work at all.

The shape that removes it is not subtle: gather the targets inside the transaction (they are
plain strings — origins, paths, addresses), close it, fetch, then assemble the findings. The
reason it was not done first is structural rather than intellectual. `verify()` is a single
transactional entry point that the checks all read entities through, and Spring's proxying means
a self-call cannot change propagation — so this needs the read pass and the report assembly
separated into two beans, which is a refactor of the block's largest class and wants to be its
own change rather than a rider on the feature that exposed it.

Worth stating for whoever does it: the fetches must stay *bounded* afterwards. The transaction
was never the only reason for the cap and the budget — a verification call that takes four
minutes is a hang whether or not it holds a connection.

### IX.8 The handoff has no console banner (SH207)

SH104 shipped the handoff as `shio report`, which is right for where a handoff usually lands: a
PR body, written by whatever ran the agent. §IX.6 described a second destination — a console
banner — and that half is unshipped, which leaves the curator who never opens a terminal with no
answer to "what did the agent do since I last looked".

It is a small feature over already-composed data (the same three reads), and its natural home is
the SH109 review queue page, which is already the console's answer to "what did an agent do, and
do I accept it". The one design question is the position: a banner that shows on every page is
noise, and a banner only on the review page is a page nobody visits unless they already suspect
something. The honest version is probably a count in the navigation that opens the report — the
same shape as an unread indicator, which is a pattern a curator already knows how to ignore
safely when it is zero.

### IX.9 A backtick in the CLI's help text breaks the whole binary (SH208)

`cli/src/index.mjs` holds `HELP` as a template literal. Help text is prose, prose about a CLI
contains command names, and command names in Markdown-ish prose attract backticks — at which
point the file is a syntax error and `shio` does not load. Not the command that was edited: the
whole binary.

This is filed because of *how it was found*: it happened twice in one session, while adding
`verify --delivery` and then `shio report`, and both times the only tests that noticed were the
three that spawn the CLI as a subprocess. Every other CLI test imports the command modules
directly, so 155 tests passed against a package whose entry point could not be parsed.

**It then happened a third time**, in a different session, documenting SH150's `movedFrom:` in
the content section — one backtick pair around a frontmatter key. Three occurrences across
three sessions, each one caught only by the same three tests, is no longer a run of bad luck: it
is a file whose failure mode is disproportionate to the edit, and the reason this moved from 💭
to 📋.

The class is removable rather than manageable: `String.raw`, or a `help.txt` read at startup.
The second is slightly better than it looks — it also stops help text from being reindented by
whatever formats the JavaScript, and makes the one file a contributor is most likely to edit a
file with no syntax at all.

### IX.10 `shio snapshot` photographs one page per run (SH209)

SH102 captures a page. The artifact anyone actually wants for a handoff or a nightly job is the
*set*: every published page of a site, one baseline each, and a list of which ones moved — which
is also the form in which a visual diff is worth reading, because "these three pages changed" is
a sentence and "this page changed" is a fact you already knew.

The parts exist (`/agent/find` gives the addresses; the command gives the capture), so the work
is the loop's own properties rather than the photography: one browser reused across pages instead
of one launch each, a per-page timeout so a single hanging page cannot consume the run, and a
summary that reports the pages it *could not* reach as distinct from the pages that did not
change. That last one is the SH141 lesson arriving in a second place — an unreachable page and an
unchanged page must never be the same row.

---

### IX.12 Can I work here — one call, and the fact it needs is missing in dev (SH397)

The manifest's `version` reads `getPackage().getImplementationVersion()`, which the POM
stamps with the version and the abbreviated commit — **for a packaged jar**. Run from
Maven or an IDE, it is `null` and the manifest answers `"dev"`. That is exactly the mode
where the instance can be several commits behind the tree being edited, and the failure
it produces is the expensive kind: a change that is not there reads as a change that
does not work, and the diagnosis happens in the wrong file. It has cost real sessions,
which is why a memory note exists to warn about it.

Two halves. **A stamp that exists in dev** — the commit and time from `git.properties`,
or failing that the newest timestamp among the loaded classes, so the answer is
comparable rather than a constant. **`shio doctor`**, one call that answers *can I
work*: is the instance up and which build, is the render model provisioned and does it
drift (SH295), are the widgets seeded (SH325's 409 is the tell), do the credentials
authenticate, is the content tree in sync (`push --content --check`), which sites exist.

Every fact already has a reader; nothing composes them, so the first minutes of a
session are five calls and a guess. This is P2 pointed at the environment instead of at
the content — and it is CLI-shaped, because half the inputs are files.

## X. Content as files (Block I)

> The largest cycle reducer, and the least obvious. Authoring over HTTP is the
> wrong medium for an agent; a git-diffable text tree is the right one — the agent
> is measurably better and cheaper with `Edit`/`Write`/`Grep` than with
> read-modify-write over an API.

> ✅ **SH86, SH87, SH88, SH89 and SH90 shipped** → [CHANGELOG.md](CHANGELOG.md) Block I.
> What follows is the design rationale for what the projection **still owes**.

## XI. Blueprints + Claude Code plugin (Block J)

> *"Skip steps with things that are already done"* only works if the ready-made
> thing is **appliable**, not readable. A prose recipe an agent must read and
> reimplement saves nothing — it just moves the work. **Absorbs the old SH23
> marketplace epic** (SH27–SH31).
>
> The format, the verb, the first-party set, the section vocabulary and the Claude Code
> plugin are shipped — see [CHANGELOG.md](CHANGELOG.md) Block J. What remains is the
> distribution question (§XI.5) and the five mismatches that shipping the two halves
> exposed (§XI.7–§XI.11).

### XI.5 The community half of the registry (SH217)

SH96 shipped the local half: the classpath, plus one directory an operator names. That is a real
trust model — whoever can set `shio.blueprint.path` can already put a jar on the classpath, so it
grants no new authority, and whoever set it is vouching for what is in it. Collisions refuse
rather than shadow, an unreadable path fails rather than yielding fewer packages, and every
package reports its source.

What that does *not* cover is a package a stranger wrote. "An operator put it there" is a subject
for a sentence about trust; "it resolved from npm" is not, and that difference is the remaining
task. A blueprint provisions post-types and writes pages under the caller's credentials, so
fetch-by-URL moves the decision from a person to a network — the shape of every supply-chain
incident anyone has had.

What this task owns: the `shio-blueprint-*` naming convention (absorbs old SH28/SH29/SH31); a
validation pass a publisher can run *before* publishing — SH92's load-time checks exposed as a
command, so an author's mistake is caught by the author; a written statement of what an applied
package may do, which SH151/SH152 made answerable by making the dangerous parts opt-in; a
curated index; and a provenance signal stronger than a directory listing.

The format needs nothing. A package is a directory validated on load, `formatVersion` refuses
one written for a newer instance rather than mis-reading it, and SH96 proved the validation is
source-independent: "it loaded" already means the same thing wherever it came from.

### XI.11 The remaining packages carry an index page (SH216)

SH156 gave `landing` and `marketing-site` a front end — the starter tree plus a section
overlay, generated by one `emit` and guarded by a drift test — and deliberately stopped
there. `blog`, `docs` and `product-catalog` do not compose sections: their models are
ordinary posts with an HTML field, which the starter's catch-all already renders. Giving
them the section overlay would ship a dependency they never import and let three packages
claim a front end that renders their pages no better than the plain starter does.

What they are actually missing is one page each that the starter cannot provide, because
it is about *their* model rather than about any page: a post list at `/blog`, a
navigation tree for the docs, a grid for the catalogue. That is the SDK's `query` rather
than the section dispatcher, and the shape differs per package — which is exactly why it
is not one overlay.

The mechanism is already there and should not be reinvented: an entry in `PACKAGES` with
its own overlay directory. The open question is only where a per-package overlay lives,
since unlike the section one it belongs to no shared vocabulary.

### XI.12 A release leaves the generated packages stale (SH218)

`publish-js-sdk.yml` bumps every publishable package, rewrites the dependency ranges in
`create-shio-app/template/package.json`, and commits. It does not re-run `emit`. The
template is the *source* of three generated trees — `nextjs-starter`, and since SH156
`marketing-site` and `landing` — each of which carries its own copy of that `package.json`
with the old range baked in.

Nothing fails during the release. The drift test fails on the **next** push, which means a
release's side effect is reported against whoever pushed after it, and the obvious reading
of a red build ("what did I break?") is wrong. That misattribution is the actual cost; the
stale range is trivially fixed by the command the failure already names.

The fix is one step in the workflow: run `emit` after the bump and add the three package
directories to the commit. Worth doing carefully rather than quickly, because it makes the
release job write into `shio-app/src/main/resources/`, and a job that regenerates content
should fail loudly if the regeneration changes something it did not expect.

### XI.13 Reloading the local package directory (SH219)

`ShBlueprintRegistry` caches on first access and never invalidates. That was free when the
classpath was the only source: packages cannot change inside a running JVM, so a cache with
no invalidation was not a cache with a bug, it was a constant.

SH96 changed the premise. An operator can now drop a package into `shio.blueprint.path`, and
nothing will see it until the process restarts — for a directory whose whole point is local
iteration on a package, that is the wrong default and the one thing everybody will hit first.

What it should *not* be is a watcher. A registry that re-scanned itself would re-run
validation at a moment nobody chose, and a half-saved manifest would take the whole registry
down between two applies — turning an author's editing state into an outage for every caller.
An explicit `ROLE_ADMIN` reload puts the moment in a person's hands, which is the same
argument SH96 makes about the directory itself: the trust model is that somebody decided.

### XI.14 A re-apply defaults to what the site was built with (SH220)

SH155 records the package, its version and its parameters as an instance-memory note, so
"which blueprint is this site" is answerable. What it does not yet do is make the *next*
apply cheaper: re-applying still means re-supplying every `--param`, from memory or from a
transcript.

That is not merely tedious, it is a correctness problem. A parameter the operator
misremembers does not fail — it rebuilds the site with a different brand, headline or CTA,
and `changed: n` reports it as ordinary work. The safe default is the value that produced
the site.

The record is already there and already parseable (sorted `name=value`, `site` excluded). The
work is `shio apply --blueprint` reading it, and — this is the load-bearing half — **saying
which values it defaulted**, because a parameter that was silently supplied is one nobody
reviews. An explicit `--param` always wins over the record.

### XI.15 Make write-free planning structural, not remembered (SH221)

SH153 removed the last write from a blueprint plan, and the reasoning generalises further
than the fix did. `POST /agent/plan` and a `dryRun` batch write nothing today, but nothing
*enforces* that: the property is maintained by every author remembering it, and the last time
it was not, the reason was good — the planner needed to resolve a post-type the run would
create, and a rollback-only transaction was the obvious way to get one.

That reason will recur. The next resource a plan needs to resolve before it exists will make
the same argument, and it will be just as locally defensible.

Wrapping the dry-run paths in a `readOnly` transaction makes the guarantee structural: the
database refuses the write instead of a reviewer catching it. This is the same argument
`ShByteStabilityLintTest` makes about `Map.copyOf` — a contract that can only be honoured by
remembering belongs inside a mechanism (§VIII.11). The open question is scope, since the
batch service's entry points are shared between plan and apply and the flag cannot simply be
moved onto the class.

### XI.16 The appliable unit stops where the visible site starts (SH395)

Six packages ship — `blog`, `docs`, `landing`, `marketing-site`, `nextjs-starter`,
`product-catalog` — and **not one declares a `Theme`, a `PageLayout`, a `Region` or a
`site.postTypeLayout` binding**. SH280 records the consequence in passing ("no
first-party blueprint ships a layout, so this is an agent-built-site exposure"), and
that is the whole gap: apply a blueprint and the pages render through SH169's labelled
fallback, which is a safety net and not a design.

What the agent then does by hand is the most trap-dense sequence in the product:
`render.provision`, three templates whose fields are keyed the 2018 way, a `TITLE` that
must be set or nothing can address the template (SH277), a flat `postTypeLayout` map
keyed by post-type, a theme resolved by title, and a **publish** of every template or a
published render 409s (SH280). Each trap has a teaching error, which means it is learned
one failure at a time — P2 backwards, in the one flow the product is named for.

So `CONTENT_KEYS` gains the render model: a package may declare templates and its
binding, `apply` provisions the types first (SH152's `ensure`, never an upsert) and
publishes the templates it wrote, because a draft-only layout is the state that 409s.
One command, one visible site — and the packages become the place the render conventions
are *demonstrated* rather than described.

## XII. Agent safety & human-in-the-loop (Block L)

> An agent-driven CMS is only adoptable if a human can bound it and undo it.
> **Invariant: the agent path is never a privilege escalation** — tenant isolation
> (SH22), content ACL (SH54) and token scopes apply identically on every agent
> surface. An agent-native CMS that quietly runs as root is not adoptable.
>
> SH106–SH109, SH200 and SH201 have shipped; their design notes are gone with them (see
> CHANGELOG.md Block L). What remains here is the one task still blocked, plus the follow-ups
> SH200/SH201 turned up.
>
> **The invariant grew a second edge while they landed.** SH106–SH109 were all argued as
> tightenings, and SH201 is the same rule read from the other end: after Block L the agent path
> was in places *stricter than the console*, and where that happens the console is the hole. The
> form to carry forward is symmetrical — **the two surfaces enforce the same ACL, and whichever
> is stricter is the correct one.** SH222–SH226 are the places that edge points at next.

### XII.5 Agent budget guards (SH110)

Per-session call/write/storage ceilings, layered on SH45's per-tenant metering when
that lands. A runaway agent should hit a ceiling and report it, not a bill.

### XII.6 An agent can fill the trash and cannot read it (SH222)

`post.delete` and `folder.delete` both answer `"to trash (restorable)"`, deliberately — an
agent that believes it destroyed something will not offer the human the undo. But that string
is the *only* place the trash appears on the agent surface. There is no `/agent` read that
lists it, no MCP tool, and no `shio` command; `GET /api/v2/post-unified/trash`,
`POST .../untrash`, `GET /api/v2/folder/trash` and `POST .../untrash` are console endpoints.

So the protocol tells the caller an undo exists and gives it no way to reach it. That inverts P1
on the capability an agent needs most immediately after a mistake, and it is where "the console
can do it" is least comforting: the mistake is usually noticed by the agent, seconds later.

Two design questions, neither settled:

- **How much surface does it cost?** A read plus a restore is two more operations, and the MCP
  tool budget is per turn (SH132). The cheap shape is a verb on an existing tool, not a new one
  — `shio_write` already carries the addressed write vocabulary. The listing is the harder half:
  a read of things with no address any more, which is what SH172's `id:` fallback exists for.
- **Does a restore need a confirm token?** SH108's rule is that a *destructive* op needs the
  plan it was shown. A restore is not destructive but not free — it can bring back content a
  human deliberately removed. Probably no token and a change-set entry naming what came back,
  since SH200's restore is already audited.

### XII.7 Deleting a site is SH200 one level up, and worse (SH223)

`ShSiteWriteService.delete` removes the `ShSite` entity and lets the JPA cascade
(`ShSite#shFolders`, `CascadeType.ALL`) take its folders. Every post in the site therefore
leaves the same way a folder's posts used to before SH200: past the SH55 trash, with no SH51
audit row, invisible to the SH88 feed, unrevertable by the SH109 queue, and with no `DELETE`
lifecycle event — so no webhook and no SH38 byte cleanup.

Same defect, largest blast radius; the argument for fixing it is SH200's: **the most
destructive operation must not be the least recoverable and the least observable.**

The fix is a delegation, not a third cascade — the site's root folders go through
`ShFolderWriteService`, the one place that knows what deleting a subtree means. Two things to
decide while doing it:

- **Is a deleted site trashed too, or only its content?** Trashing the content and removing the
  site row cannot work, for the reason it could not for a folder: the posts keep their
  `folder_id` and the root folders their `site_id`. So either the site gets a `deleted_date` of
  its own — consistent, and a site can then be restored whole — or the delete stays a purge and
  must fire the events and write the audit itself. SH200 chose the first one level down.
- **What does the site listing owe the curator?** A trashed site still occupies its name and
  friendly URL, so re-creating it collides, and "that name is taken by something you deleted"
  is a teaching error somebody must write.

### XII.8 A permanent deletion is the one write nobody records (SH224)

`ShPostUnifiedWriteService.purge` fires the `DELETE` lifecycle event — so the SH12 webhook is
delivered and SH38's listener removes the stored bytes — and writes **no audit row at all**. The
reversible half of the trash is fully recorded (SH55's `delete` writes one, and since SH200 a
deleted section writes one per post); the irreversible half records nothing.

That is backwards. "Who destroyed this, and when" is a question about the deletion that cannot
be undone, and it is the only content transition the SH51 trail does not carry. It also means a
purge is invisible to `/agent/changes`, so a caller walking the feed sees a post enter the trash
and never learns it is now gone for good — the two states a reader most needs to tell apart.

The obvious objection is that the post no longer exists, so the entry has no address. SH88
already answers that: an entry whose object is gone travels with its title and a `null` address,
and the feed says so in `notes`. The row is worth having on exactly those terms.

Scope note: a folder purge can now take forty posts in one call, which is what moves this from
tidy to worth doing. Whether that writes forty rows or one plus forty is the same question
SH200 answered on the delete side, and it should be answered the same way.

### XII.9 The ACL check depends on `open-in-view` and does not say so (SH225)

`ShContentAclService.requireRead`/`requireWrite` carry no `@Transactional`, and they reach
`canRead`/`canWrite` by **self-invocation** — which does not pass through the Spring proxy, so
the annotation on those methods never applies. The permission decision then walks a folder's
lazy `parentFolder` chain with whatever persistence session the caller happens to have.

Inside the app there is one, because `spring.jpa.open-in-view` defaults to true. Anywhere else
there is not: a scheduled job, an internal service call, or a test that calls a controller bean
directly gets a `LazyInitializationException` **instead of a permission decision**. SH201's test
found it by being the first thing to ask the ACL a question outside a request, and had to open a
transaction of its own to get an answer at all.

Two reasons this deserves a task rather than a one-line annotation:

- A guard whose failure mode is "explodes" beats one that fails open, but it is still not a
  guard: the code that has to work is the code that *denies*, and it currently depends on a
  setting whose entire purpose is to be turnable off. Somebody will turn it off for the reasons
  people always do, and the symptom will be 500s on authorization, in production, under load.
- The fix is not only the annotation. `aclChain` walking lazy associations is why a session is
  needed at all; resolving the chain with an id-keyed query would make the check
  session-independent, which is what a security decision should be. That also takes the SH22
  join hazard out of the walk.

### XII.10 A trashed folder is excluded by convention, not by invariant (SH226)

SH200 made every read in `ShFolderRepository` exclude a trashed folder, which covers navigation,
the CDA, the sitemap, the content lint and the exchange export in one place. But ids also
resolve **polymorphically** through `ShObjectRepository`, which knows nothing about
`deleted_date`, and three call sites had to be guarded by hand
(`ShObjectNavigationService.list`/`path`, `ShObjectWriteService.moveTo`/`copyTo`) so a curator
could not browse into a deleted section by its own id or move live content into one.

Hand-guarded is not enforced. The next read that resolves a folder id through the polymorphic
repository will show deleted content, answer `200`, and be invisible to every test — which is
character-for-character the SH128 `ShFolderSite` shape, and that one earned
`ShFolderSiteInvariantTest` for exactly this reason.

Two candidate answers, not exclusive:

- **A lint**, in the SH128 mould: fail the build on a `ShObjectRepository.findById` result used
  as a `ShFolder` without a trash check, with a named escape hatch for the trash's own reads.
  Cheap, and it catches the case at the moment somebody writes it.
- **A resolver**, in the `ShFolderSite.of` mould: one method that resolves an object id and drops
  a trashed folder, so the correct form is also the shortest one. The lint then has something to
  point at, which is what made SH128's guard teachable rather than annoying.

## XIII. Token economy, measured (Block M) — what the measurement opened

> ✅ **Block M shipped in full**, including the six follow-ups the measurement itself opened
> → [CHANGELOG.md](CHANGELOG.md) Block M. The design rationale for shipped work lives there
> and in the code; `git log` is the history. What follows is only the unshipped remainder —
> and, again, all of it exists because the block did what it was built to do: produce
> numbers, and then disagree with an assumption.
>
> The current reading is **tokens 2.8×–10.5×, tool calls 2.3×–3.8×**. SH157 was the first
> change in three blocks to move a ratio by improving the *agent* path rather than by the
> baseline getting leaner, and what it left behind is mostly the shape of that argument
> getting harder: the calls that remain are real work.

### XIII.7 `shio_apply` cannot ask for its own proof (SH227)

SH157 gave `shio_write` a `verify` argument for about fifteen tokens of schema, and could
not give it to `shio_apply`. The measured tool sits at **457 against a 460 per-tool
ceiling** — there is no room, and the ceiling exists precisely to make that a decision
rather than a drift.

The awkward part is which tool got it. A desired-state apply writes a whole section at
once, so it is the write *most* likely to want a lint of what it just did; the batch that
did get the argument is the finer-grained one. An MCP client therefore still spends the
closing turn on exactly the operation where folding it in saves most.

The fix is a trim, not a raise. `shio_apply` is the tool the budget file has been warning
about since Block J — it is the only one carrying two verbs, and its description has grown
each time one of them gained an option. Two directions are worth measuring before choosing:
state the blueprint verb in one clause instead of a sentence, or move the parameter
documentation that duplicates the schema out of the description. If neither buys fifteen
tokens, the honest answer is that the eleventh tool (SH160) used the headroom and this one
needs the twelfth slot's argument instead.

### XIII.8 A write proves only its posts (SH228)

The SH157 proof collects the `post:` addresses a run touched and lints those. Everything
else it silently drops — so a batch of `folder.upsert` ops comes back with **no `proof` key
at all**, which an agent cannot distinguish from "asked for, and nothing was wrong".

That is the wrong answer twice over. The lint already has rules that apply to folders (an
empty folder is one of its warnings), so the report exists and is simply not being asked
for; and "scaffold the structure, then fill it" is a real, common first step whose whole
point is to be checked before content goes in.

Two things to decide. Whether a folder address can be handed to `verify`'s SH143 `address=`
scope at all, or whether it needs `folder=` (which is a subtree, and a subtree of a
just-created folder is empty by construction — so it may report the very warning the
scaffold expects). And what an *empty* proof should look like: absent is currently
ambiguous, and a caller that asked for verification deserves an explicit clean report, the
same way `verify` itself answers `ok` with no findings rather than nothing.

### XIII.9 Nothing checks that an ETag covers every parameter that changes the bytes (SH229)

SH116 wrote the rule down clearly — *a tag identifies a representation, so `format`, `ids`
and `fields` all belong in it* — and then SH157 found `include` missing from the context
pack's own tag, three blocks later. Two projections of one unchanged pack shared a tag off
the same digest, so a caller asking for `include=model` could revalidate against a full
pack's cached copy and keep a pack with no sitemap in it. Nothing failed; nothing could
have.

SH174 then had to derive the same guard by hand for the manifest, and SH159 sidestepped it
entirely by hashing the rendered body — which is exact, and is also an admission that
getting the discriminator list right by hand is not something to keep doing.

This is the **SH136 shape exactly**: an invariant about how code is *written*, invisible to
behavioural tests because any single request is internally consistent. It wants the same
answer — a lint. The tractable form is a test that, for each conditional endpoint, walks its
`@RequestParam`s and fails when one is absent from the tag's inputs, with an explicit
exemption list for the parameters that genuinely do not change the bytes (`budget`, which
only bounds a walk that is already reflected in the content, is the interesting case to get
right). The alternative — make `ShAgentBodyEtag` the only way to build a tag on this surface
— is stronger and costs the three discovery endpoints their cheap digest-based path.

### XIII.10 The manifest's weight is prose, and terse barely touches it (SH230)

SH174 measured it:

| Reading of `/agent/manifest` | `X-Shio-Est-Tokens` |
|---|---|
| full JSON | **2343** |
| `format=terse` | 2088 (**1.1×**) |
| `include=features,limits` | 298 (**7.9×**) |

Everywhere else the terse encoding is worth a third to a half, because everywhere else
the payload is *rows* and JSON's cost is the key names repeated per row. The manifest is
not rows: twenty-nine endpoint entries whose weight is a purpose sentence and a runnable
example, both verbatim on purpose — an abbreviated example is a call the instance would
reject.

So the index is the target, and `include` only helps a caller that already knows what it
wants. Two candidates, both with a real objection:

- **Elide a derivable example.** Most entries' examples are `METHOD {apiBase}{path}` with no
  parameters. Against it: `ShAgentManifestTest` asserts every indexed endpoint carries a
  runnable example, because a missing example is what sends an agent guessing, and a rule the
  reader must reconstruct is not obviously better than the bytes.
- **Put the index behind its own `include`, defaulted off.** Honest about what the document is
  for — a capability check — and it moves the cost to the session that wants the index. Against
  it: a *first* session is exactly when the index is worth having, and it would then pay two
  calls instead of one, which is the opposite of SH157.

A third: the index is the most cacheable document on the surface and never changes
between builds, so a separate `/agent/endpoints` with a long-lived tag would make the
manifest small permanently — at the cost of one more endpoint.

### XIII.11 An instance's own notes travel nowhere (SH231)

SH161 made a site's notes ride its export and deliberately left the instance-wide ones
behind. That reasoning holds for a *site* import — importing another installation's house
rules alongside somebody's blog is a P7 surprise, and nobody asked for them — and it does
not hold for the case where those notes matter most: moving a whole instance.

An instance-wide note is the accumulated answer to "how is this deployment driven" — copy is
authored in files here, this front end owns `/docs/**`, publishing happens on Fridays. It is
the least reproducible thing in the system and the only one with no export path at all.

The tenant package (`buildTenantPackage`) is the obvious home. The decision is what an import
does with them, and the options are not equal: merging them into the target's memory is the
surprise SH161 refused; importing them **scoped to nothing and marked as imported** needs a
provenance field the note shape does not have; and writing them to a *file* for a human to
review fits P7 and needs no schema change. That last is probably right, which makes this a CLI
task rather than a server one.

### XIII.12 `/agent/changes` cannot revalidate the way it is most often called (SH232)

After SH159, the change feed answers `304` in cursor mode: an unchanged range is
byte-identical and free, the SH88 walk as designed. In **window** mode it never will,
because the response's own cursor is `now - lagMillis` — the moving edge of the feed —
so two calls a second apart differ.

That is correct, and also the read most repeated by a client that kept no cursor: the
window is what a session opens with, and "has anything changed since I got here" is
asked more often than it is answered yes.

Three shapes, in increasing order of how much they concede:

- **Quantise the window cursor** to the second or the configured lag, so consecutive polls
  inside one tick agree. Cheap, and it makes the cursor less precise than the position it
  names — the one property SH88 spent a task getting right.
- **Tag the window response over its `changes` array only**, excluding the cursor. Honest
  about what the caller is asking (*did anything happen*); a `304` then withholds a cursor —
  tolerable, since a caller that wanted one should be in cursor mode.
- **Say nothing and document it**, the current state. Defensible: a client polling the
  window should keep the cursor it was given, one line in the endpoint's description.

The third is the default and the first is probably wrong. Worth deciding rather than
leaving to whoever next notices the poll is not free.

### XIII.13 One surface is measured and the other is assumed cheap (SH394)

`token-budgets.properties` declares `mcp.tools.list.total=2375` and `max-per-tool=460`,
and SH132's test fails the build on a regression, because that payload is re-sent every
turn of every session. The CLI has no equivalent: `shio --help` is **14,363 bytes —
roughly 3,600 tokens** — and no test, ceiling or benchmark touches it.

The two costs are shaped differently and that is the whole point. MCP's is **per turn
and bounded**; the CLI's is **per session, on demand, and unbounded** — paid when an
agent reads the help, the area file or the skill. Neither number is knowable from the
other, so the recurring argument that one surface is cheaper than the other has been
made from intuition on both sides, which P3 calls a defect regardless of which way it
points.

So: a budget for the help text and for each command's own help, in the same file and the
same shape, and the per-session comparison printed rather than asserted (`shio --help`
plus the smallest set of reads that gets an agent from nothing to a correct first call,
against `tools/list` × a session's turns). It is a benchmark before it is a ceiling.
SH208 is a dependency rather than a coincidence: the same template literal is what this
measures and what breaks the binary on a backtick.

### XIII.14 The number guards the job that stops short of the promise (SH396)

`build-marketing-site` is the canonical benchmark for the product's own headline task,
budgeted at 4 calls and 2700 tokens, and its calls are: context, blueprints, apply,
verify. It ends at a **lint**. Nothing in it provisions the render model, binds a
layout, publishes a template or fetches a page, so the number that P3 defends describes
*content that passes checks* — not a site anybody can look at.

That matters twice. The agent path is understated, because the steps it is missing are
precisely the ones SH395 collapses into the blueprint. And the naive path is understated
far more, because a hand-built site meets SH277's empty title and SH280's
unpublished-layout 409 on the way, which is where a real session actually loses its
turns — so the honest ratio is larger than the one being reported, and the reported one
is the product's own claim about itself.

The fix is to extend the task to its real end: apply, provision, bind, publish, then
`GET /agent/render` and assert a digest that is not the hash of nothing. Two
consequences to accept — the budget rises (a truthful number is the point, not a small
one), and the benchmark starts covering the flow SH277 named as the one nothing tested
end to end, which is how that defect survived four blocks.

## XIV. Realignment of what already shipped (Block N) — what building it opened

> The reorientation was mostly additive, but it did invalidate some existing choices.
> Block N spent them → [CHANGELOG.md](CHANGELOG.md) Block N, and so are the tasks that
> building it opened (SH163–SH167, SH233, SH234). What is left here is the one lesson worth
> carrying forward (§XIV.4) and the one task that **finishing** it opened and is still open
> (§XIV.15: the editor nothing drives, which needs a front end to embed before it can be driven
> end to end). The other five (SH235–SH237, SH239, SH240) have shipped.
> Concept spec §10 has the original table.

### XIV.15 The Universal Editor is driven by nothing (SH238)

SH233's defect — a save-back that could not authenticate — lived behind a comment that described
the mechanism *incorrectly*, and it survived because there is no test anywhere in this repository
that drives the Universal Editor. Every test either builds the request in Java or mocks the axios
client, and both of those agree with the comment: they cannot see that a browser attaches a
same-origin cookie regardless of `withCredentials`.

This is Block O's lesson recurring in the one place the block did not reach. **The one fact the
feature rests on has since been checked** (shipped — see CHANGELOG Block N): SH182's harness runs
the editor's PATCH from a page the console served and fails on the 403 symptom, and a full-chain
MockMvc test pins the same precedence through the real filter order, verified by restoring the
defect and watching it fail.

What is still open is the editor *itself*, and it is genuinely blocked: it is an
`<iframe src={site.url}>`, so exercising the properties panel, a field's round trip and the publish
action needs a front end to embed — which is Block P. The thing to carry forward is why the cheap
half was worth doing first: the alternative was discovering the next plumbing defect the way this
one was discovered, by reading the code and noticing that a comment is wrong.

## XV. The visible site (Block P)

> An agent can build a site's model, folders and pages over the agent surface, and nobody
> can look at the result. This section is the gap between *correct content* and *a page a
> person sees* — the product's differentiator, and the one part of the loop with no path
> through it.

### XV.0 What the original engine actually was (the `FSP` analysis)

Before designing a renderer it is worth knowing precisely what was removed, because the
reputation of the old engine ("Nashorn, therefore unsafe, therefore never again") turns out to
describe its *host* and not its *design*. `origin/FSP` is not a divergent branch — it is an
ancestor of `HEAD`, last touched **2019-09-09** — so this is Shio's own history, readable with
`git show origin/FSP:<path>`.

The pipeline:

```
URL → ShSitesContextURL                        parse object id + `format`
    → site.postTypeLayout                      {PostType: [{format, layout, mimeType, cacheTTL}]}
    → PT-PAGE-LAYOUT post                      attrs: HTML, JAVASCRIPT, THEME
    → Nashorn eval(JAVASCRIPT)                 bindings: shContent, html, request
         ├── shObject.js                        a 174-line façade over 7 Java components
         └── handlebars.min.js                  ← the actual template language
    → ShCachePage / ShCachePageLayout           keyed page cache, honouring cacheTTL
```

Four things follow, and they reverse the conclusion I would otherwise have reached.

### XV.0.1 The template language was Handlebars, not JavaScript

`ShCacheJavascript.shObjectJSFactory()`
concatenates `classpath:/js/server-side/shObject.js` and `handlebars.min.js` and evaluates them
once per thread. Authors wrote Handlebars — logic-less, declarative, no code execution. Nashorn
was the *host* that ran Handlebars and bridged to Java; it was never the authoring surface.

**The capability set was already meant to be closed.** `shObject.js` exposes seven functions and
nothing else — `formComponent`, `searchComponent`, `navigation`, `navigationFolder`, `query`,
`queryByPostType`, `getRelation` — each a one-line wrapper over a Spring bean bound in
`ShNashornEngineConfiguration` (`shNavigationComponent`, `shQueryComponent`, `shSearchComponent`,
`shFormComponent`, `shGetRelationComponent`, plus three `*Utils`). That is an allow-list
expressed as a convention.

### XV.0.2 The liability was the host, and it was real

`ShNashornEngineConfiguration` calls
`getScriptEngine(String[])` — Nashorn's `getScriptEngine(ClassFilter)` overload exists precisely
to bound what a script may reach, and it is not used. So a layout's JavaScript had `Java.type()`
over the entire classpath: `Runtime.exec`, file I/O, the `DataSource`. The raw
`HttpServletRequest` was bound into script scope as `request`, and `shObject.js` uses it
directly. Layouts are *content*, so this made a content author a JVM operator. §0.4's objection
is correct and this is the concrete form of it — but it indicts `eval`, not Handlebars and not
the layout model.

**Two smaller design notes worth keeping.** `regionError` reports a script failure with file,
line, column, a five-line window and a caret under the offending character — better authoring
feedback than most template engines ship, and exactly the shape law P6 asks for. And the
`ThreadLocal<Map<String, ScriptContext>>` that caches script contexts per thread reuses bindings
across requests, which is a state-leak between visitors; a template evaluator with no mutable
global scope does not have that problem to solve.

### XV.0.3 What the model handled that a fixed section vocabulary does not

This is the question that
matters for SH183, and the answer is unambiguous. Navigation, queries and listings, search
results and forms are all *dynamic* — none is expressible as a static section post, and all four
were first-class in the old engine. A closed vocabulary of eight section types cannot render a
menu, a paginated article list, a search results page or a contact form. It also had two axes
this repository has no equivalent for: **format** (the same post rendered as `default`, or as
another format with its own layout, mime type and TTL — the mechanism behind alternate
representations) and **theme** (`PT-THEME`, resolved per layout).

**The lineage.** The model is OpenText WEM / Vignette V7's — page → template → component, with
presentation held as managed content rather than as files in a build. That is why the levels line
up the way they do, and it is worth knowing when weighing the design: this is a shape with
twenty years of enterprise use behind it, not an invention.

### XV.0.4 The objection: V7 rendered with a script engine too

It did, and that precedent is
real — the objection deserves an answer rather than a restatement of the non-goal. Two things
changed, and neither is about capability.

*The threat model.* In V7's era a template author was a trusted employee, templates shipped
through a controlled release process, and the server sat on a corporate network. In Shio a
layout is content; content is written by an **agent**, and packages are meant to arrive from a
registry whose trust story is still an open question (SH96 exists precisely because "where may a
package come from" is unanswered). Add the multi-tenant direction (SH22, SH44–SH47) and
"content can execute arbitrary JVM code" stops being a convenience and becomes a tenant escape.
The same design is safe under one set of assumptions and not under the other.

*The runtime.* Nashorn was deprecated in JDK 11 and **removed from the JDK in 15**. What is left
is the standalone `org.openjdk.nashorn` artifact — the one still sitting unused in
`shio-app/pom.xml`, pinned at 15.7, on a project running Java 21. Building the product's
differentiator on it would mean betting the render path on a library that exists to ease
migration *away* from itself.

So the conclusion is not "V7 was wrong". It is that the part of V7 worth keeping is the
**model** — page → template → component, presentation as managed content — and the part that
has to be replaced is the host that ran it.

### XV.0.5 What a real template looked like: regions are slotted by an HTML attribute

`openviglet/bootstrap-site` is not a project — it is an
`export.json`, an SH68 exchange package, and its ten posts are `PT-FOLDER-INDEX` ×1,
`PT-ARTICLE` ×3, `PT-PAGE-LAYOUT` ×2, `PT-REGION` ×2, `PT-THEME` ×1, `PT-FILE` ×1. The
presentation *is* content, and it travels the same way content does. Four things in it settle
the design:

**1. Regions are slotted by an HTML attribute.** A layout's `HTML` field is a whole document
with named holes:

```html
<body>
  <div sh-region="BOOTSTRAP_NAV_REGION"> </div>
  <div sh-region="BOOTSTRAP_CONTENT_LIST_REGION" style="margin:20px"></div>
```

`sh-region` is the composition mechanism, and it is also — already, in 2018 — exactly the
annotation contract the Universal Editor needs (§XV.3). A rendered page that keeps these
attributes tells the editor what each part of the DOM is and which post to save it to.

### XV.0.6 The layout's JavaScript is ceremony, not capability

Both layouts carry the same four
lines:

```js
load('https://cdnjs.cloudflare.com/ajax/libs/handlebars.js/4.0.11/handlebars.min.js');
var template = Handlebars.compile(html);
var html = template(shContent);
html;
```

Compile the HTML as a Handlebars template against the data, return it. That is what
Handlebars.java does natively with no script at all. (The article layout adds one small data
remap — `{title, description, text, theme}` picked out of `shContent` — which is a projection,
not logic.) Note also `load('https://cdnjs.cloudflare.com/...')`: the engine fetched its
template library **from a CDN at render time**, so the render path depended on remote code
being both reachable and unmodified.

### XV.0.7 A region's JavaScript is a view-model builder, and every part of it is a helper

The
navigation region registers a helper, calls a component, and reshapes the result:

```js
Handlebars.registerHelper('file_url', ...);              // → a value helper
var navigation = shObject.navigation(siteName, true);    // → a block helper
forEach.call(navigation, function (shFolder) { ... });   // → the helper yields this
```

and the content-list region is the same shape: `shObject.query(folderId, "PT-ARTICLE")`, then a
loop mapping `TITLE`/`DESCRIPTION`/`TEXT` and building links with
`shObject.generateObjectLink`. Between 767 and 1098 characters of hand-written JavaScript per
region, to produce what a domain-aware block helper produces in one line:

```hbs
{{#query folder=this postType="PT-ARTICLE"}}
  <div class="card"><h5>{{title}}</h5><a href="{{link}}">Read More</a></div>
{{/query}}
```

So migrating to registered helpers is not a loss of expressiveness — it is the removal of
boilerplate that exists only because the loop had nowhere else to live. The templates themselves
(`{{#each}}`, `{{#if}}`, `{{{text}}}`, custom helpers) are ordinary Handlebars and port as-is.

### XV.0.8 A theme carries site-wide assets

`PT-THEME` has `CSS` and `JAVASCRIPT` fields — both
`null` in this sample, so the intent is clearer than the usage: one place for the stylesheet and
script a whole site shares, referenced by each layout's `THEME` field.

So the honest conclusion is that the old engine's *architecture* was better than what replaced
it for the specific job of "make something a human can see" — and the only part that has to go
is one line, `scriptEngine.eval(javascript, sc)`, plus the boilerplate that existed to feed it.

### XV.25 A security claim in prose, checked by nobody (SH363)

SH359 was found by accident: SH265 needed to know whether `shio snapshot` would have to
authenticate, so somebody read the config. Nothing was looking. The defect had been live
since SH183 and its two halves each read correctly on their own — an allow-list entry
naming a path that used to mean something else, and a controller comment naming the rule
it believed it inherited.

**The instrument is the deliverable (SH338), and the honest version is narrow.** "Every
prose claim about authentication is true" is not checkable. Two things are:

- **Every `permitAll()` entry names a route that still exists**, and carries a reason
  beside it. `/preview/**` outlived its route by years; an entry pointing at nothing is
  the exact shape that silently adopts the next controller mounted on the prefix.
- **Every controller under a governed prefix is asserted from outside, anonymously.**
  `ShPreviewIsNotPublicTest` is that test for one route. The general form is a table:
  path → expected status with no credentials, with a new mapping failing the build until
  somebody declares which side of the line it is on.

The second is the one that would have caught SH359, and it is the SH354 habit applied to
security: the question is not "is this safe" but "what does the allow-list already say
about this prefix", and only a request can answer it. A test over the config restates
the config.

### XV.26 The form's receiving half, now that it has a design (SH364)

SH247 shipped the rendering half and left this deliberately. What changed is that it is
no longer an open question: FSP's `ShFormConfiguration` carries `createPost` and
`folder`, so **a submission becomes a post in a named folder** — no storage subsystem,
no retention policy of its own, because the trash, the export, the ACL and the agent
surface already apply to a post.

What is genuinely undecided is the door, and it is a security decision rather than a
rendering one. A public endpoint that writes content needs, at minimum: the folder named
by the **site** rather than by the form (a folder chosen in a template is a folder an
attacker chooses), the post created `DRAFT` so SH7's review queue is the moderation
step, a rate limit per subject, and a honeypot — the cheapest spam control that costs an
author nothing.

Three things worth settling before code. **Whether the CMS should accept this at all**,
given the renderer is preview-grade and production delivery is Next.js over the CDA: the
front end posting to its own handler and calling the CDA is a legitimate answer that
leaves Shio's write surface closed. **What the post-type may be** — an allow-list on the
site, or any type, which is the difference between a contact form and a
content-injection endpoint. And **what a submission is worth keeping**: a post carries
an owner, and an anonymous one has none.

### XV.28 The field whose value is a program is the one nothing runs (SH392)

SH248 settled that a `Region`'s `JAVASCRIPT` is never evaluated in the JVM and is
shipped to the browser instead — the resolution of the Block D non-goal, and correct.
The consequence nobody has picked up is that **this repository never executes it**.
`ShRenderNoScriptEngineTest` asserts the opposite property (that the server *cannot* run
it), the composer's test asserts the emission, and SH336's click interception is
asserted as a script string. A region whose nav never opens, whose accordion throws on
load, or whose selector matches nothing renders, hashes, lints and verifies clean.

So the honest state is that the render model's one code-valued field is guaranteed to be
*delivered* and not to *work*. SH391's harness is what changes that at a marginal cost:
the page is already being loaded in a browser, so a console error, an uncaught
rejection, and a declared behaviour that did not happen are findings beside the visual
ones rather than a second mechanism.

Two boundaries to keep. This is **not** a general test framework for content — an
author's script is theirs, and the claim is only that it loads without erroring and that
what the region declares it does happens. And it stays a **CLI gate**, never a `verify`
finding: the server cannot see it, and one defect reported by two subsystems is two
reports that disagree.

### XV.30 The window belongs in the query, and the reason it was not put there is a correctness argument (SH419)

SH384's first deliverable was the measurement, and it found something the sweep
benchmarks are blind to: they count prepared statements, where a listing is flat, while
`findByShFolderAndStateIn` reads the **whole folder** and `limit` is applied afterwards
in memory. `ShRenderListingPageBenchmarkTest` records 213 entity rows loaded to render
10, identical for page 1 and page 19, with the ceiling asserted on the *ratio* so
enlarging a fixture cannot absorb it.

That measurement licensed paging, sorting and filtering as free in-memory operations,
and it is also the finding: the cost grows with the folder rather than with the page, so
an archive of 5000 posts pays 5000 rows for the first ten of them.

Pushing the window into the query is **not** free, which is why SH384 deferred it rather
than doing it in passing. Three things need rows a windowed query would exclude. The
DRAFT-preference fold reads both states and keeps one row per id, so a window applied
before the fold can return fewer rows than asked for, or the wrong ones. The exact
totals the pager reports come from the full matching set, so a windowed read owes a
second count query with the same predicates, which has to stay in step with them or the
pager lies. And the field filter is a predicate over the post's JSON data, evaluated in
Java today.

### XV.31 The pager idiom SH384 documented is unreliable in exactly the page that needs it (SH420)

SH384 published `@page`, `@pages`, `@total`, `@first` and `@last` as Handlebars data
variables so a pager could be drawn **once**: a block helper's body repeats per row and
there is no way to write into the enclosing scope, so `{{#if @last}}` is the only place
a template can emit one.

That idiom breaks as soon as a listing contains a listing, and the failure was verified
rather than reasoned about. A layout whose outer `{{#query}}` body holds an inner
`{{#query}}` and then reads `{{#if @last}}` renders the pager **on every outer row** —
measured 2 of 2 where 1 was expected. The inner block's final iteration sets `@last =
true`, the outer body reads it afterwards, and both writes land in one scope. `@index`
and `@first` have the same defect.

The page shape that hits it is not exotic: an archive whose cards list their own
children, or a hub whose sections include a listing. So the documented way to draw a
pager is wrong precisely where a real site would draw one.

Three candidate fixes, and the choice is part of the task. **Save and restore** the five
values around `options.fn`, the smallest change, leaving the shared scope. **Namespace**
them per call (`@query.last`), which is a template-visible rename and therefore a break.
Or **push them onto the row**, giving up drawing a pager once — the option SH384
rejected for repeating markup per item, worth re-examining now the alternative is known
unsound.

### XV.32 A lint over templates fires on correct work or nobody reads it (SH421)

SH389's `site-scripts-dropped` is the rule that makes the task's fix real: moving a
site's scripts into one ordered list stops the duplication and does nothing about a
layout that never emits them, which is a set of pages with no consent banner rendering
perfectly.

It has one false-positive shape, introduced with it and worth closing before somebody
meets it. The rule fires for **every** bound layout in a site that declares scripts, and
some layouts omit them deliberately: a transactional email template, a print sheet, an
AMP-shaped variant, an embed a partner iframes. Each is a legitimate layout that must
not load a chat widget, and its author has no way to say so.

The lesson SH388 wrote down applies directly: a lint over templates that fires on
correct work is one authors learn to skip, and the true finding goes unread with it.

Two shapes to choose between. A **baseline acceptance** already works and costs nothing
to build, but records the decision away from the layout it is about. A **field on the
layout** — a declared opt-out — puts it where an author reads it and makes the omission
intentional in the content rather than in a suppression file, at the cost of one more
field on a system post-type. The second is probably right for the same reason SH385 put
a redirect's source on the post rather than in a side table.

## XVI. The human half (Block O) — what reading the console next found

Block O shipped, and its lesson was that **console defects answer `200`**: seven of them, none
visible to a Java test, a CLI test or the MCP golden. The two below were found the same way but
from a different angle — not by driving the console, but by fixing the *agent's* move verbs
(SH134/SH137/SH139) and having to read the console's implementation of the same operation each
time. The pattern is consistent enough to state as an expectation: **when a verb exists on both
surfaces, the console's copy is where the careless version lives**, because nothing on it is
asserted at the level the agent surface is.

> ✅ **SH202 and SH203 shipped** → [CHANGELOG.md](CHANGELOG.md) Block O. Their two durable
> rules: a copy corrects each of its names **in the scope that name is unique in** (title per
> folder, URL per site — sharing one counter renames a page for a collision the curator cannot
> see), and **the client prevents what it can see, the server refuses what it must**. The five
> sections below are what finishing them found. A sixth, **SH246** — the Java suite red by one
> since SH144 made the incident store durable — has since shipped too; its answer was the
> SH164/SH239 precedent, that a test asserting something about the whole instance has to boot
> its own database.

### XVI.2 An unmet peer range is not a build failure (SH242)

`monaco-graphql@1.8.0` declares `peerDependencies: { "monaco-editor": ">= 0.20.0 < 0.53"
}`. A Dependabot bump set the console's direct dependency to `monaco-editor: 0.56.0`;
pnpm resolved it, and nothing failed.

What made it fatal rather than unsupported is that 0.56 introduced an `exports` map
(`"."`, `"./*.js"`, `"./*"`) whose patterns re-prefix `esm/vs/`, so
`monaco-editor/esm/vs/editor/editor.worker` expands to
`esm/vs/esm/vs/editor/editor.worker.js` and resolves to nothing — and 0.56 had also
*removed* files monaco-graphql imports outright (`esm/vs/editor/edcore.main.js`). `pnpm
run compile:build` therefore failed, which is `mvn package`, the release jar and the
SH182 check.

The fix was a pin, not a shim: `@graphiql/react` already pins `monaco-editor@0.52.2`, so
the console's `0.56.0` was the outlier; aligning on it satisfies the peer range and
collapses three Monaco copies to one. The first attempt was a Vite `resolve` alias
remapping the broken prefix, and why it was wrong is worth recording: it made two
specifiers resolve and failed on a third, because the packages are incompatible and an
alias hides the symptom at whichever import the bundler reaches first. **A build error
that moves when you patch it is telling you the version is wrong.**

The generalizable half is the guard. This is a **resolution** fact — true the moment the
lockfile is written, invisible to every runtime test — so the instrument is the
installer: `strict-peer-dependencies` (pnpm defaults it off) turns "resolved a version
the dependent does not support" into a refusal, the only thing that would have caught
this at the bump.

### XVI.3 A trashed post still holds its friendly URL, and nothing knows it (SH243)

`ShPostUnified` carries `@SQLRestriction("deleted_date is null")`, which makes the SH55 trash
invisible to every normal read. The cost is that every "is this URL free" question is answered
*only about live posts*: SH202's check, SH139's move refusal and the `duplicate-url` lint all
inherit the restriction from their finders.

The failure is short: clone a page (`home` → `home-2`), delete the copy, clone again —
the second copy takes `home-2`, because the query sees nothing holding it. Then untrash
the first: `restoreFromTrash` clears `deleted_date` with **no check at all**, and the
instance has the duplicate SH202 was filed to remove, produced by two individually
correct operations.

SH236 met this blind spot from the post-type side — a trashed post still holds its
`post_type_id`, so a refusal counting only live rows said "0 posts" and failed on a
constraint — and its answer is the one to copy: **count the trashed rows too and name
them separately**. Two changes here: the free-URL search should see the trash so a copy
skips a URL that is only sleeping, and `restoreFromTrash` should refuse a restore onto
an occupied URL rather than duplicate silently.

The second is the important one, and not obviously a refusal — the alternative is a
numbered URL, the way a copy does. That would be wrong: a restore asks for *the thing
that was there*, and quietly returning it at a different address is the surprise P7
exists to prevent. Refuse, name the occupant, let the human choose.

### XVI.4 Duplicating content is console-only (SH244)

Copy lives at `PUT /api/v2/object/copyto/{dest}` and nowhere else: no `batch` op, no MCP verb,
no CLI path. That is law P1's "no feature whose only interface is the console".

For a **post** this is close to harmless, worth saying rather than overstating the gap: an agent
duplicates a page by reading it and upserting the fields at a new address, which loses nothing,
because a copy of a post is just its field values somewhere else. The honest gap is the
**folder**. A folder copy buys the whole subtree in one call, and reproducing that over the
agent surface means walking the tree and emitting an upsert per post — the discovery-and-write
loop Block H exists to remove, in the one case where the server can do it in one traversal.

If it lands it is a `folder.copy` op on the batch surface, not a new endpoint, and it composes:
SH202's naming and SH203's cycle guard already live on the shared writers, so the op is an
address pair and a call. Two things it must inherit — the copy is **draft** (`copyPost` sets
`published(false)`, and a batch that published a hundred pages would need SH107's gate for a
reason nobody intended) and it is **not** destructive, so it needs no SH108 confirm.

It stays 💭 because the demand is unproven: no blueprint, no CLI command and no recorded session
has wanted it. Filed so the next one need not rediscover that the capability is reachable but
expensive.

### XVI.5 Make "one writer per verb" a lint, not an expectation (SH245)

Four times the same defect has been found the same way. Fixing the agent's version of a verb
meant reading the console's, and the console's was the careless one:

| verb | what the console's copy did | found by |
|---|---|---|
| move a post | re-slugged `furl` from the title, changing live URLs | SH139 |
| move a folder | no cycle guard — re-parenting into its own subtree detached it | SH137 |
| delete a folder | `deleteAll` on the repository: no trash, no audit, no lifecycle event | SH200 |
| copy a folder | duplicate URLs, and non-termination on its own subtree | SH202/SH203 |

The rule that would have prevented all four is already written down — a content mutation goes
through the writer service — and it is enforced nowhere. Every instance was caught by somebody
happening to read the neighbouring file while fixing something else, which does not scale and
has already missed three of these for a release each.

This repository has the right instrument for invariants no functional test can watch:
`ShByteStabilityLintTest`, `ShFolderSiteInvariantTest` and `ShCsrfExemptionLintTest` each fail
the build because the failure mode is a *future* caller rather than a current request. This is
the same shape: a controller method that writes content without delegating to the writer that
owns the verb.

The design work is the exemption hatch, not the check. The precedent is
`ShFolderSite-exempt: <reason>` — a named comment carrying the argument, so the rare legitimate
case is *stated* rather than silently permitted. What the lint keys on needs deciding too, and
the cheap version is likely right: a write mapping on an API class whose body touches a
`*Repository.save`/`delete` directly. Precision matters less than the build going red the day
somebody adds the fifth one.

### XVI.1 No CI workflow runs on the branch the work happens on (SH241)

Three workflows exist. `build.yml` (Maven + Sonar) and `codeql-analysis.yml` are
`workflow_dispatch:`-only. `console-check.yml` triggers on `push: branches: [main]`, and
the default branch is **`2026.3`**. So on a push to the branch everything is developed
on, **nothing runs**.

That matters most for SH182, which exists to catch what nothing else can see: console
defects answer `200` and are invisible to Java tests, the CLI suite and the MCP golden,
so the answer was a browser walking the routes in CI. A gate that does not run is a
script. Not hypothetical — picking up Block O's follow-ups meant building the console,
and it was **already red** (§XVI.2), unreported.

**The sharper justification is SH284's, folded in here:** SH282 and SH283 were artefacts of a *local
working tree* — an IDE writing classes into `target/classes`, and a reports directory accumulating
across hand-run commands. Neither can happen in a job that starts from a clone, so **a clean-room
build is the only thing that can tell a real green from a local one.** SH282's lint protects the
person about to commit; CI protects the branch, and only CI catches a *stale but valid* class.

Two consequences that carries: the workflow should publish surefire's summary as the
run's headline, so no count is re-derived by hand (SH283's defect, SH354's at the
terminal); and which jobs run on push versus pull request is a deliberate choice — the
console check drives Chromium, the Java suite is cheap enough for both.

### XVI.66 The rationale file carries the debt the roadmap just shed (SH356)

SH341 took the roadmap's 42 over-long lines to zero and `roadkeep lint` fell from 249
findings to 107. What is left is almost entirely one file: **45 sections over the
250-word budget, 42 whose task has already shipped, 9 that no line points at, and 3
anchors claimed twice.**

The three kinds are not one job. A **stale** section is the cheapest and most misleading: `ship`
drops a section as its third edit, so 42 survivors mean 42 tasks whose rationale was moved by hand
before the tool owned the files — and a reader meeting one cannot tell live design from history. An
**orphan** is a section nothing can reach: either the last copy of an argument or dead weight, and
only reading says which. **Over-long** is SH341's drift one file along, with the same fix — split, or
move the remainder into the commit that made it true.

Four line-level defects sit beside them and are not editorial at all: two `U+0008`
control characters inside a ledger line, an entry with no `—` separator, a duplicate
`SH347`, and an entry naming a file the repository no longer contains. That is
corruption rather than prose, and `lint --fix` does not touch it.

SH341's order applies — instrument first — but a section gate cannot ratchet from 45 the
way the line gate did, because dropping a section is destructive where shortening a line
is not. So the honest first step is deciding what a *stale* section should become.

# Part 2 — pre-existing sections

## I. Headless foundation — the CDA

> ✅ **Fully shipped (SH1–SH5)** → [CHANGELOG.md](CHANGELOG.md) Block A; per-task
> design records in `docs/specs/SH1`–`SH5`. Under the new orientation the CDA is
> the **read substrate every agent tool composes over** (P10) — unchanged, and more
> load-bearing than before. The SH1 forward-compat constraints (state is a
> service-level param, not a URL segment; `siteId` is a first-class query param)
> still bind, and now also bind the agent surface.

## II. JavaScript-for-pages DX

> ✅ **Fully shipped (SH6–SH11, and the SH62 epic SH63–SH66)** →
> [CHANGELOG.md](CHANGELOG.md) Block B; specs `SH11-type-generation.md`,
> `SH62-post-types-dsl.md`. **Why it still matters:** post-types-as-code is the
> template the whole agent-native protocol follows — declarative source → canonical
> serialization → plan → apply → drift guard → explicit GUI/code boundary. Blocks H
> and I are that same pattern applied to content and to the protocol itself.
> (Historic subsection anchors §II.1–§II.7 map to SH6, SH7, SH8, SH9, SH10, SH11, SH62.)

## III. Composable platform primitives

> ✅ **Fully shipped (SH12–SH16, SH26, SH51–SH55)** → [CHANGELOG.md](CHANGELOG.md)
> Block C. The agent surface leans directly on four of them: the **audit trail**
> (SH51 → §X.3 change feed, §XII.1 attribution), **soft-delete** (SH55 → §XII.4
> revert), the **draft/published diff** (SH14 → §XII.4 review), and **search**
> (SH52 → `shio_find`). Open follow-up: folder soft-delete (subtree-hide semantics).
> (Anchors §III.1–§III.10 map to SH12–SH16, SH51–SH55.)

## IV. Server-side JS rendering — dropped

> ⛔ **Dropped as a non-goal.** The former Block D (SH17 GraalJS spike, SH18 `.js`
> page templates) is removed: an agent builds the front end in Next.js, where the
> SSG/ISR/preview story already exists (SH8/SH9). An in-JVM script engine adds
> sandboxing risk, a second template language and hot-reload complexity, and buys
> the agent nothing. See §0.4.

## V. Legacy retirement

> ✅ **Fully shipped (SH19–SH21, SH48–SH50, SH56–SH61, SH73)** →
> [CHANGELOG.md](CHANGELOG.md) Block E. `shio-react` + the headless CDA are the only
> console/delivery surfaces. Note the reframe: the console surfaces rebuilt here —
> Universal Editor (SH10), Media Library (SH73) — are now the **human's** curation
> tools (§0.5), so they gained importance rather than becoming maintenance debt.

## VI. Market positioning

### VI.1 Multi-tenancy / SaaS (SH44, SH45, SH46, SH47) — SH22's core shipped, follow-ups open

Core DB isolation, the tenant catalog, delivery-path token→tenant resolution,
per-tenant provisioning, tenant-keyed CDA cache and the console Tenants page all
shipped → [CHANGELOG.md](CHANGELOG.md) Block F; spec
[specs/SH22-multi-tenancy.md](specs/SH22-multi-tenancy.md). Identity is the
existing `workspace_id`-claim/username resolution (`ShTenantFilter`). Open:

- **SH44 — Superadmin impersonation**: let a privileged operator act as / switch
  into any tenant from the console (audited), beyond the principal-derived
  `ShTenantContext` resolution.
- **SH45 — Tenant quotas & billing**: per-tenant usage metering (content count,
  API calls, storage) + quota enforcement + billing-export hooks. §XII.5's
  per-agent-session guards layer on this.
- **SH46 — Nav/feature gating by tenant**: show/hide console nav + features by
  `multiTenant`/role so single-tenant installs don't surface SaaS-only UI.
- **SH47 — Per-tenant file storage**: prefix `tenantId` into the id-keyed
  `ShFileStorageService` layout (`file_source/{tenantId}/<postId>/...`) from
  `ShTenantContext` (SH38 shipped, so this is now unblocked).

## VII. Content portability

> ✅ **Fully shipped (SH67 epic: SH68–SH72)** → [CHANGELOG.md](CHANGELOG.md) Block G.
> The original "new sites are blank" regression is closed. **Why it still matters:**
> the SH68 exchange package format is the substrate Block J's blueprints are built
> on (§XI.1), and `ShSiteImportService.provisionInto` is the engine `shio apply`
> reuses — one engine now serving site clone, tenant provisioning, bootstrap and
> blueprints.
>
> **Non-goal (do not re-add):** the legacy `ShSitesNodeJS` "export site as a
> runnable Node app" is superseded by `create-shio-app` (SH8) and, going forward, by
> blueprints (§XI.1).

## XVII Replication (Block Q) — a site that already exists becomes one an agent can author

The question this block answers is one a customer asks in one sentence: *take the site
we have and make it a Shio site the agent maintains.* Nothing in Blocks A–P is wrong for
it, and none of it is aimed at it — every capability assumes the site is being
**built**, from a blueprint or from nothing. A replication inverts the direction: the
target already exists, somebody else's markup is the specification, and "done" is not
"it renders" but "it renders the same".

That inversion splits into three phases, and each has a different owner. **Capture** is
the CLI's, because the instance fetches no URL (SH268) and runs no browser (a standing
non-goal). **Convert** is the file tree's, because a proposal a human corrects before it
is applied is a diff, not an API call (P4, P7). **Prove** is the perception loop's, and
it is the phase Block P left honest about its limits — the appearance digest covers the
failure an *agent* creates and not the one a human sees (SH265), which is precisely the
failure a replica has.

Two gaps in the middle are not about replication at all: they are ordinary site features
nobody has needed yet because no first-party blueprint has a menu, a second page of a
listing, or six languages. They surface here because a real site has all three.

### XVII.44 The canonical is the mirror of the source's (SH467)

The source serves `/shio/` with a **200** and answers `/shio` with a **301** to it. The
replica did the exact opposite: `/shio` was 200 and `/shio/` was **404**, on the public
route and the preview alike — so it was the stored friendly URL, not the route.

The route half has landed: `pageAt` tries the other trailing-slash form and redirects
toward the stored `furl`, so both spellings answer and the direction is read off the
content. What remains is that the stored form is still the grammar's. The capture reads
`/shio/` and `ShAddress.normalizePath` collapses it, so the replica's **canonical** is
the mirror of the source's: every captured link, sitemap entry and bookmark costs a 301
instead of a 200, and a search index built from the replica names a URL the source
redirects away from.

SH385's rule is that a replica keeps the original's URL space, and one character per
directory-style path is what is missing. It is also not cheap. Preserving the declared
slash makes `/shio` and `/shio/` **two addresses**, and `normalizePath` is the one
canonical path form precisely so they are one: nine exact-`furl` lookups — resolver,
CDA, `verify`, render, copy/move — become slash-sensitive, and an agent typing a stray
slash creates a second post instead of updating the first. A P5 identity change wearing
a capture fix's clothes.

So the question is narrower than it looked: is a replica's canonical worth splitting the
address space, or is a redirect the honest answer for URLs Shio never owned?

### XVII.49 The report shipped ahead of the fetch, again (SH473)

`--scripts` exists, its help says "take this site's own `<script src>` too", and the
site's own script is still not taken. Measured on a `--depth 1 --render --scripts`
capture of `www.viglet.org`: the captured markup carries `<script type="module"
crossorigin src="/assets/index-LNwgBwpz.js">`, the inventory's `scripts` list holds
**only** the two third-party hosts, `script` is absent from the asset kinds, no `.js`
reaches the tree, and the served replica has one inline script and no `src`. The bundle
404s.

So the dark/light toggle and the consent modal are still dead, which is where this
started.

What SH469 did land is the reporting, and it is good: third-party scripts are a named
section, off-origin references are counted and explained, and `--scripts` is
discoverable. What did not land is the fetch — the same split as SH452, whose note
became honest while the download stayed unwritten, and SH455 which had to be filed to
finish it. Three times now the report has shipped ahead of the behaviour it reports on,
which is worth naming as a pattern rather than as a third accident: a phase that
*describes* an omission reads as done.

The first-party half is the whole ask. A `<script src>` on this origin is an asset like
a stylesheet, and `--scripts` already declares the intent — what is missing is `script`
as a kind the collector walks.

### XVII.50 The rule the false one was hiding (SH474)

SH470 removed the eighty-four false `dangling-reference` errors, and the run under them
is **66 `unknown-field` errors** — the ones the false rule was hiding. `File` declares
exactly one field, `FILE`, and every uploaded asset stores five: `FILE`, `SHA256`,
`SIZE`, `WIDTH`, `HEIGHT`. Counted by field: 18 `SIZE`, 6 `WIDTH`, 6 `HEIGHT`.

The rule is right and the data is right; the **type** is the thing that never caught up.
`SHA256` and `SIZE` are what SH429's uploader has always written — the digest is how a
re-upload is skipped and the size is what the delivery reports — and `WIDTH`/`HEIGHT`
arrived with SH471 so a converted section can draw an image without measuring it. Four
fields the writer needs, the reader uses and the declaration does not mention.

So the fix is to declare them, not to stop writing them. `File` is a system post-type,
so it comes from `import/post-types.json` and `ShRenderModelSourceTest` holds that file
and the model together — the same door `render.provision` opens for an instance that
already exists.

The lesson is the sequencing. Two rules disagreed about the same posts and the louder
one was wrong, so the quieter and correct one was unreadable until the first was fixed.
`verify` was red both times, and only the second red is worth acting on — which is why a
gate's *first* run after a fix is worth reading in full rather than diffing against the
count.

### XVII.51 Drawn, and still not authorable (SH475)

SH465 made the collection draw and stopped short of making it editable. The rows are
there — `<li class="shio-feature" data-shio-row="features">` with the title and body of
both cards — and the row carries **no `sh-field`, no `data-shio-prop` and no
`sh-post`**. The editable count on the page is unchanged at five: the grid's own
`heading`, and nothing inside it.

So a curator can edit the heading above two cards and neither card. That is the shape
SH464 was filed to end, one level down: rendered and not authorable, on the half of the
vocabulary that holds the actual content. Four of seven section types are
collection-shaped, so it is most of what a section-based replica says.

SH465's own rationale names the missing piece — "with `sh-post` on the row so the editor
can address it" — so this is the half that did not land rather than a decision.
`data-shio-row="features"` is already emitted, which means the template knows it is in a
row and only the annotation is absent.

The protocol says what the row needs. A collection item is a post, so `sh-post="{{id}}"`
addresses it and `sh-field` on each of its fields names them, exactly as the section
itself already does — SH185's resolution is nearest-ancestor, so a row inside an
annotated section resolves correctly once it declares its own post. The check is the
same one SH465 shipped: count the annotated fields against what the type declares, and
count *inside* a collection too.

### XVII.52 An entity escaped twice is content nobody can repair (SH476)

The source writes `rerank &amp; media models`, which a reader sees as `rerank & media
models`. The authorable replica writes `rerank &amp;amp; media models`, which a reader
sees as `rerank &amp; media models`. The entity is escaped twice.

Extraction keeps `&amp;` as five literal characters instead of decoding it to `&`, and
the renderer then escapes the `&` it finds — correctly, since it is emitting text into
HTML. Neither step is wrong on its own; the missing one is the decode at the boundary
where markup becomes content.

It reaches everything a source spells with an entity: `&`, `&nbsp;`, `&mdash;`,
`&quot;`, `&lt;`, and the numeric forms. A page that uses them heavily accumulates the
corruption in every field, and each re-capture escapes what the last one stored — so it
compounds rather than staying at one layer.

**Fidelity mode is unaffected**, which is why this survived: verbatim markup keeps `&amp;` and renders
it exactly once. Only the authorable path extracts text into a field, so only the mode nobody had
finished testing carries it.

The curator is the one who sees it, and cannot fix it in place — the editor field holds
`&amp;` too, so typing the correction stores `&` and the next capture overwrites it.
That makes it worse than a display bug: it is content a human cannot repair through the
surface built for repairing content.

Decode entities when text leaves the markup, and assert a round trip on a source that
uses one.

### XVII.53 A mark that labels itself is not a label (SH477)

SH472 spaced the mark correctly and left it in place. A product card on `www.viglet.org`
is labelled `Du Dumont DEP`, and `Du` is a logo — a 48×48 tile whose two letters are
drawn, not written. However it is spaced, it is not part of the label, and a nav label
becomes a menu entry in the replica that a curator reads and has to delete by hand.

The signal is in the markup and needs no heuristic: the tile carries `aria-label="Du"`
and its own visible text is `Du`. An accessible name that merely repeats what is already
on screen is the author saying the element is decorative — that is what `aria-label` is
for on a graphic, and a text run duplicating it adds nothing a reader would want twice.

So the rule is narrow: inside an anchor, a text run whose collapsed value equals the
`aria-label` of an element enclosing it contributes nothing to the label. It is not
"drop short runs" and not "drop uppercase runs" — both would eat real content, and `DEP`
is the counter-example sitting in the same string.

What has to be measured first is how often the pattern is real. One site is one site,
and a rule that fires on a single fixture is a rule that fires on nobody else's markup.
The capture already holds the anchors, so counting the runs that match across a crawl's
pages is the first deliverable and the drop is the second.
