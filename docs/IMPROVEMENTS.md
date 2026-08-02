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

### VIII.9 Turing ecosystem wiring (SH84)

The SH22 half is shipped: `ShAgentTenantIsolationIT` exercises every Block H surface
from a second tenant and proves the standing invariant — the agent path is **never** a
privilege escalation (§XII). It needed to be its own test rather than a corollary of the
storage isolation one, because this surface resolves content by *address* rather than by
id and its purpose is to describe a whole instance in one call: a context pack carrying
another tenant's sitemap would look exactly like a working context pack.

What remains is the Turing integration (replaces old SH37) — the ecosystem half, which
is a wiring question rather than an enforcement one.

### VIII.12 Folders are absent from the change feed (SH196)

`ShFolderWriteService` records no audit row on create, rename, move or delete. Post writes
all do, so `/agent/changes` describes a site's *contents* and never its *shape*.

The consequence is specific and it falls on the one workflow the feed exists for. A session
resuming with `since=<cursor>` is told a post now lives at `folder:Blog/archive/2025` and is
never told that folder appeared, was renamed from `2024`, or moved. It cannot distinguish
"someone filed this post into an existing section" from "someone restructured the site", and
those call for different next actions. The SH109 review queue has the same hole from the
other side: it groups audit rows by session, so a run whose whole purpose was structural
shows up empty or half-present, and there is nothing for **revert** to act on.

Two design points worth settling before the code:

- **A folder change is not a post change, and flattening it into one would lie.** The
  temptation is to emit an `UPDATE` per affected post when a folder moves, because that is
  what the feed already knows how to carry. But no post changed — SH137 is explicit that a
  folder move leaves every URL and every field alone — and a feed that reported a hundred
  post updates for one rename would make a cheap operation look like a rewrite, which is
  exactly the misreading that makes a consumer stop trusting the feed. The entry is about
  the folder.
- **`ShContentAction` has no vocabulary for it yet.** `CREATE`/`UPDATE`/`DELETE` are about
  the right shape, but a consumer needs to know the subject is a folder to interpret the
  address (`folder:` vs `post:` per SH79) — so the audit row needs a subject kind, not a new
  action per subject. That is the same "one dimension per column" argument that kept
  `agent_session_id` separate from `owner` (§XII).

### VIII.13 Report inbound links when a post moves (SH140)

`ShReference` is id-keyed, so a reference stored *as a reference* survives a move: the
row points at the post, whatever its address becomes. A URL typed into another post's
rich text is not that. It is a string in a field, it names the address rather than the
object, and a move rewrites the address underneath it.

So a move can break pages nowhere near the one being moved, and the agent that moved it
is told nothing. That is the shape SH79 addresses were meant to remove and cannot,
because the broken thing is authored prose rather than a modelled relationship — it will
always be possible to write a link this system does not know about.

**Two versions, and the cheap one is not the useful one.** A count in the change detail — "this move
may affect N posts mentioning the old URL" — is a text scan that would ship in an afternoon and
answers with a number the caller cannot act on. The useful version is a *finding per affected post*,
with its address, and that belongs where route claims already live: SH100's verification, which SH141
extended into a delivery-side proof. A broken inbound link is therefore **checkable** now rather than
only reportable, which changes the design: the question is not how to detect one but whether
detection belongs in the write's response or in the lint that already fetches routes.

Worth settling before building either, because the two answers are different features.

### VIII.14 Finish the byte-stability class (SH197)

SH136 made four live instabilities fail the build and left one shape unguarded. Its lint
bans `Map.copyOf` and `Set.copyOf`, whose iteration order comes from a per-JVM salt; a
hash-ordered `Map.of(…)` reaching a serialized map component is the **same defect** and
is not banned, because the lint was written against the two forms the sweep actually
found.

No live instance today, which is exactly when the guard is cheap: a rule that fires on
nothing costs an argument in review, while adding it after the first defect costs the
defect too. That is `build.md`'s rule — write the guard for the invariant, not a
resolution to be careful.

**Why it must be a lint and not a test**, restated because it keeps being re-derived: the salt is
fixed once per *process*, so building the same map twice inside one JVM gives the same order however
broken the code is. A behavioural guard would pass in every run there will ever be; the only
observable difference is between JVMs, which no harness can arrange. So the check reads source, and
the escape hatch stays a sentence.

The narrower question to settle first: whether to ban `Map.of` reaching a *serialized*
component specifically, or `Map.of` in the model at all. The first needs the lint to
know what is serialized, which it cannot; the second is broad enough to need the
exemption often, which is how a guard gets deleted.

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

## X. Content as files (Block I)

> The largest cycle reducer, and the least obvious. Authoring over HTTP is the
> wrong medium for an agent; a git-diffable text tree is the right one — the agent
> is measurably better and cheaper with `Edit`/`Write`/`Grep` than with
> read-modify-write over an API.

> ✅ **SH86, SH87, SH88, SH89 and SH90 shipped** → [CHANGELOG.md](CHANGELOG.md) Block I.
> What follows is the design rationale for what the projection **still owes**.

### X.3 Commit-ordered change feed (SH147)

The feed's cursor is exact about ties (it is an `(instant, id)` pair) and inexact about
one thing it cannot fix from the read side: SH51 assigns the audit row's timestamp
**inside the writer's transaction**, so a slow transaction can commit an older timestamp
after a faster one has already been read and the cursor has moved past it. That entry is
then invisible for ever, which for a *sync* feed means a lost edit rather than a late one.

The shipped mitigation is a lag — the feed will not report an entry younger than
`now - lag` (1s), which gives any transaction that already stamped an earlier timestamp
that long to commit — and the response states the rule rather than implying exactness. It
narrows the window; it does not close it, and a bulk apply of hundreds of posts can still
outrun it.

Closing it properly means changing how the row is *written*, not how it is read. That is why
it is a separate task — SH88 is the read shape, and the read shape is right.

**What a pass at it in July 2026 established, before stopping.** The two phrasings this
section used to offer — "a sequence assigned at commit, or an outbox the reader drains in
commit order" — are not two designs of equal weight, and neither is as cheap as it sounds:

* **"Assigned at commit" is not available.** There is no JPA hook that runs *at* commit; the
  latest one is `TransactionSynchronization.beforeCommit`, and two transactions can still
  interleave between their `beforeCommit` and their actual commit. It narrows the window
  exactly the way the lag already does, so it buys nothing and costs a column.
* **A sequence assigned *inside* the transaction does work, but only with gap detection.** Rows
  get a monotonic `seq`, the cursor becomes a `seq`, and the reader **stops at the first hole**
  rather than reading past it — a hole is either a transaction still in flight (wait) or one
  that rolled back (skip after a timeout). This is the real shape, and it turns the existing
  `lag-millis` from "hope the commit is fast" into "how long to wait for a gap", which is a far
  better justification for the same knob.
* **The blocker is portability, not design.** A monotonic non-PK column has to work on H2,
  MariaDB *and* PostgreSQL: identity/auto-increment on a non-PK column differs across all
  three, Hibernate will not drive a sequence generator for a non-id field, and `nextval` vs
  `NEXT VALUE FOR` is engine-specific SQL. Whatever is chosen, the **only** proof it applies is
  the SH26 Testcontainers ITs (`ShMariaDbSchemaIT` / `ShPostgresSchemaIT`), which need Docker
  and auto-skip without it. Landing a changeset for a portable-sequence column without running
  them would repeat the SH2/SH5 drift exactly: a column that works on the dev H2 and leaves a
  clean Liquibase database broken.

So the task is: pick the portable mechanism, add `ShHistory.seq` with a changeset, make the
cursor a `seq` (accepting the old `<instant>|<id>` form and translating, since it is a
published contract), teach the reader to stop at a gap, and **run the cross-DB ITs**. It is
schema work that must not be done blind, and that is the whole reason it is still open.

### X.13 Editing an asset's bytes in place does nothing (SH210)

SH145 made a `File` field hold an asset path and made `push --content` resolve that path back
to an id — idempotently, by looking the path up in the server's inventory and reusing the id
when it is already there. That is the right rule for the case it was designed for (a second
push must not upload a second copy) and it is the wrong rule for the case nobody separated
from it: **replacing the bytes at a path that already exists uploads nothing and reports
success.** Editing an image is the most ordinary thing anyone does to an asset, and it is now
the one silent no-op left in the projection.

Noticing it is cheap and already half-built: the base snapshot fingerprints every projected
field (SH148), and the same treatment applied to an asset's bytes would make "this file
changed" a fact rather than an inference from its path.

Landing it is the harder half, because the server has no replace-the-bytes operation — an
upload always creates a new id-keyed `File` post (which is exactly why SH145 could treat a
path as immutable). Two candidates, and they are not equivalent: **repoint the reference** —
upload the new bytes as a new post and rewrite the field — which is cheap, keeps history, and
leaves the old bytes and post behind as garbage; or **give SH38 a replace path**, which keeps
one post per asset and its usage list (SH62) intact, at the cost of a mutation the storage
layer currently does not have. The first is a CLI change, the second is a protocol change.

### X.14 Renaming a folder marker is a delete plus a create (SH211)

This is SH150's defect, one level up, and the level matters: a post re-created under a new
URL loses its history, while a **folder** deleted by `--prune` takes every post beneath it
(`ShFolderWriteService` cascades). SH149 gave folders a marker file; it did not give them a
move. So renaming `shio/folders/Blog/news/_folder.md` to `.../updates/_folder.md` reads as one
marker gone and one arrived — the push declares `/updates`, and the prune deletes `/news` with
its contents.

Everything needed already exists on both sides: `folder.move` (SH137) is the op, with its four
refusals living on the one folder-move writer, and the desired-state document grew a `moves`
section for SH150. What is missing is the pairing rule, and the folder side is *easier* than
the post side because a folder has no field values to compare: a marker gone and a marker
arrived in the same site, with no other candidate, is a rename. The ambiguity that forced
SH150 to refuse — two files that could each be the same rename — is the same here and should
be refused the same way.

Note the interaction with the friendly-URL rule: a folder move changes **no post's URL**
(SH137's whole point), so this is a structural rename with no delivery consequence, which is
what makes it safe to detect automatically rather than requiring a `movedFrom:`.

### X.15 `shio verify` cannot see a broken asset path (SH212)

The documented CI chain is `push --check` → `push --content --check` → `apply --check` →
`verify`, in that order because each gate is cheaper than the next. A `File` field naming an
asset that is not in the tree currently fails at `push --content`, which inverts it: the
defect is local, checkable with no server at all, and reported by the most expensive gate.

The rule itself is trivial (`assetLocationOf` the value, look for the file). What makes it a
task is that it would be the **first `verify` rule that reads the project's own files** — every
existing rule is a server-side lint whose findings the CLI merely prints. That is either a new
kind of finding on the client (a projection lint, with its own reporting and its own place in
the SH142 baseline) or a reason to keep the two apart deliberately. Worth deciding rather than
drifting into.

### X.16 Every push reads the whole media inventory (SH213)

`readRemoteAssets` lists every `File` post in each site and joins the sitemap's folder chains
to it, on any push that declares a single `File` field. A site with a large media library pays
for the whole library to resolve one reference — and when an upload's folder is missing, the
inventory is read a **second** time to pick up the new folder id.

The listing already takes `folderId` and `q` filters (SH62), so a per-path lookup is
available; the reason SH145 did not use it is that resolving N paths individually is N requests
against one, and the inventory read was the cheaper shape for the small case. The fix is
therefore a threshold rather than a replacement, and the second read is a straightforward
fix on its own.

### X.17 A probable-move warning cannot be accepted (SH214)

SH150 prints a warning when a vanished post and a new one *might* be the same rename but the
content differs, because the alternative — a delete plus a create that destroys history in
silence — is worse than a false positive. That is right, and it has the failure mode every
un-silenceable warning has: for a legitimate delete-plus-create it prints on every run
forever, and a warning that is always there is a warning nobody reads.

SH142 solved exactly this shape for `verify`: a project file of accepted `(rule, address,
field)` triples, merged rather than replaced, where **an accepted finding the run no longer
produces fails the run** so the file cannot rot into a blanket suppression. The same mechanism
fits a `(from, to)` pair. The one thing to get right is that the accepted entry must be keyed
on both addresses — accepting "this deletion is fine" alone would silence the warning for a
*different* new post arriving later, which is the case the warning exists for.

### X.18 A fingerprint collision is undetectable (SH215)

SH148 replaced the merge base's field values with 64-bit fingerprints, and the trade was
deliberate: every question the merge asks is an equality test, so a hash answers it, and the
collision risk over one post's field list is far below what every hash-based sync (git
included) already accepts at much wider scope.

What was *not* deliberate is the asymmetry in how a collision fails. Two different values that
hash the same read as "this field did not change", so the edit is dropped — silently, with the
sync reporting success. A sync that loses an edit should at minimum be able to say so. Storing
the value's **length** beside the hash, or a second short hash with a different seed, makes a
collision a reported conflict instead of a silent skip, at a few bytes per field. The point is
not to make collisions less likely; it is to make the remaining ones loud.

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

SH96 shipped the local half: the classpath, plus one directory an operator names. That is a
real trust model — whoever can set `shio.blueprint.path` can already put a jar on the
classpath, so it grants no new authority, and the person who set it is the person vouching
for what is in it. Collisions refuse rather than shadow, an unreadable path fails rather
than yielding fewer packages, and every package reports where it came from.

What that answer does *not* cover is a package a stranger wrote. "An operator put it there"
is a subject for a sentence about trust; "it resolved from npm" is not, and the difference
is the whole remaining task. A blueprint provisions post-types and writes pages under the
caller's credentials, so fetch-by-URL or registry resolution moves the decision from a
person to a network — which is the shape of every supply-chain incident anyone has had.

What this task owns, then: the `shio-blueprint-*` naming convention (absorbs old
SH28/SH29/SH31); a validation pass a publisher can run *before* publishing — the load-time
checks SH92 already implements, exposed as a command, so an author's mistake is caught by
the author; a written statement of what an applied package is allowed to do, which SH151/SH152
made answerable by making the dangerous parts opt-in; a curated index; and some provenance
signal stronger than a directory listing.

The format itself still needs nothing. A package is a directory whose contents are validated
on load, `formatVersion` refuses one written for a newer instance rather than mis-reading it,
and SH96 proved the validation is source-independent: a local package is held to exactly the
same checks, so "it loaded" already means the same thing wherever it came from.

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

So the protocol tells the caller an undo exists and gives it no way to reach it. That inverts
P1 on the capability an agent needs *most* immediately after a mistake, and it is the one
inversion where "the console can do it" is least comforting: the mistake is usually noticed by
the agent, in the same run, seconds later.

Two design questions, neither settled:

- **How much surface does it cost?** A read plus a restore is two more operations, and the MCP
  tool budget is measured per turn (SH132). The cheap shape is not a new tool but a verb on an
  existing one — `shio_write` already carries the addressed write vocabulary, and `post.restore`
  / `folder.restore` are ops in exactly that shape. The listing is the harder half: it is a read
  of things that have no address any more, which is what SH172's `id:` fallback exists for.
- **Does a restore need a confirm token?** SH108's rule is that a *destructive* op needs the
  plan it was shown. A restore is not destructive, but it is not free either — restoring a
  section can bring back content a human deliberately removed. The honest answer is probably no
  token and a change-set entry naming what came back, since SH200's restore is already audited.

### XII.7 Deleting a site is SH200 one level up, and worse (SH223)

`ShSiteWriteService.delete` removes the `ShSite` entity and lets the JPA cascade
(`ShSite#shFolders`, `CascadeType.ALL`) take its folders. Every post in the site therefore
leaves the same way a folder's posts used to before SH200: past the SH55 trash, with no SH51
audit row, invisible to the SH88 feed, unrevertable by the SH109 queue, and with no `DELETE`
lifecycle event — so no webhook and no SH38 byte cleanup.

It is the same defect with the largest blast radius on the surface, and the argument for fixing
it is the one SH200 made: **the most destructive operation must not be the least recoverable and
the least observable.** A site is the whole thing.

The fix is a delegation, not a third cascade — the site's root folders go through
`ShFolderWriteService`, which is now the one place that knows what deleting a subtree means. Two
things to decide while doing it:

- **Is a deleted site trashed too, or only its content?** Trashing the content and removing the
  site row cannot work, for the same reason it could not work for a folder: the posts keep their
  `folder_id` and the root folders keep their `site_id`. So either the site gets a
  `deleted_date` of its own — consistent, and it means a site can be restored whole — or the
  delete stays a purge and must fire the events and write the audit itself. SH200 chose the
  first shape one level down, which is an argument for symmetry.
- **What does the site listing owe the curator?** A trashed site still occupies its name and
  friendly URL, so re-creating it will collide, and "that name is taken by something you
  deleted" is a teaching error somebody has to write.

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

SH174 measured it and the number is worth keeping in view:

| Reading of `/agent/manifest` | `X-Shio-Est-Tokens` |
|---|---|
| full JSON | **2343** |
| `format=terse` | 2088 (**1.1×**) |
| `include=features,limits` | 298 (**7.9×**) |

Everywhere else on the surface the terse encoding is worth a third to a half, because
everywhere else the payload is *rows* and JSON's cost is the key names repeated per row.
The manifest is not rows. It is twenty-nine endpoint entries whose weight is a purpose
sentence and a runnable example, both kept verbatim on purpose — an abbreviated example is
a call the instance would reject.

So the index is the target, and `include` only helps a caller that already knows what it
wants. Two candidates, and both have a real objection:

- **Elide a derivable example.** Most entries' examples are `METHOD {apiBase}{path}` with no
  parameters, so the string adds nothing a reader cannot reconstruct. Against it:
  `ShAgentManifestTest` asserts every indexed endpoint carries a runnable example, and that
  assertion exists because a missing example is what sends an agent guessing. A rule the
  reader has to know in order to reconstruct is not obviously better than the bytes.
- **Put the index behind its own `include`, and default it off.** Honest about what the
  document is for — a capability check — and it moves the cost to the session that actually
  wants the index. Against it: the first read of a *first* session is exactly when the index
  is worth having, and defaulting it off means that session pays two calls instead of one,
  which is the opposite of SH157.

There may be a third: the index is the most cacheable document on the surface and the only
part of the manifest that never changes between builds. A separate `/agent/endpoints` with
its own long-lived tag would make the manifest small permanently, at the cost of one more
endpoint on a surface whose whole pitch is that it has few.

### XIII.11 An instance's own notes travel nowhere (SH231)

SH161 made a site's notes ride its export and deliberately left the instance-wide ones
behind. That reasoning holds for a *site* import — importing another installation's house
rules alongside somebody's blog is a P7 surprise, and nobody asked for them — and it does
not hold for the case where those notes matter most: moving a whole instance.

An instance-wide note is the accumulated answer to "how is this deployment driven" — copy
is authored in files here, this front end owns `/docs/**`, publishing happens on Fridays.
It is the least reproducible thing in the system and the only one with no export path at
all.

The tenant package (`buildTenantPackage`) is the obvious home, since it already means
"everything this installation is". The decision is what an import does with them, and the
options are not equal: merging them into the target's memory is the surprise SH161 refused;
importing them **scoped to nothing and marked as imported** would need a provenance field
the note shape does not have; and writing them to a *file* for a human to review is the
option that fits P7 and does not need a schema change at all. That last one is probably
right, and it makes this a CLI task rather than a server one.

### XIII.12 `/agent/changes` cannot revalidate the way it is most often called (SH232)

After SH159, the change feed answers `304` in cursor mode: an unchanged range is
byte-identical and free, which is the SH88 walk working exactly as designed. In **window**
mode it never will, because the response's own cursor is `now - lagMillis` — the moving edge
of the feed — so two calls a second apart genuinely are different answers.

That is correct behaviour, and it is also the read most likely to be repeated by a client
that has not kept a cursor: the window is what a session opens with, and "has anything
changed since I got here" is asked more often than it is answered yes.

Three shapes, in increasing order of how much they concede:

- **Quantise the window cursor** to the second or the configured lag, so consecutive polls
  inside one tick agree. Cheap, and it makes the cursor slightly less precise than the
  position it names — which is the one property SH88 spent a whole task getting right.
- **Tag the window response over its `changes` array only**, excluding the cursor. Honest
  about what the caller is asking (*did anything happen*), and it means a `304` withholds a
  cursor the caller might have wanted — tolerable, since a caller that wanted the cursor is
  the caller who should be in cursor mode.
- **Say nothing and document it**, which is the current state. Defensible: the fix for a
  client that polls the window is to keep the cursor it was already given, and that is one
  line in the endpoint's own description.

The third is the default and the first is probably wrong. Worth deciding rather than
leaving to whoever next notices the poll is not free.

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

**The template language was Handlebars, not JavaScript.** `ShCacheJavascript.shObjectJSFactory()`
concatenates `classpath:/js/server-side/shObject.js` and `handlebars.min.js` and evaluates them
once per thread. Authors wrote Handlebars — logic-less, declarative, no code execution. Nashorn
was the *host* that ran Handlebars and bridged to Java; it was never the authoring surface.

**The capability set was already meant to be closed.** `shObject.js` exposes seven functions and
nothing else — `formComponent`, `searchComponent`, `navigation`, `navigationFolder`, `query`,
`queryByPostType`, `getRelation` — each a one-line wrapper over a Spring bean bound in
`ShNashornEngineConfiguration` (`shNavigationComponent`, `shQueryComponent`, `shSearchComponent`,
`shFormComponent`, `shGetRelationComponent`, plus three `*Utils`). That is an allow-list
expressed as a convention.

**The liability was the host, and it was real.** `ShNashornEngineConfiguration` calls
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

**What the model handled that a fixed section vocabulary does not.** This is the question that
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

**"But V7 rendered with a script engine too, and it worked."** It did, and that precedent is
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

**What a real template looked like.** `openviglet/bootstrap-site` is not a project — it is an
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

**2. The layout's JavaScript is ceremony, not capability.** Both layouts carry the same four
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

**3. A region's JavaScript is a view-model builder — and every part of it is a helper.** The
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

**4. A theme carries site-wide assets.** `PT-THEME` has `CSS` and `JAVASCRIPT` fields — both
`null` in this sample, so the intent is clearer than the usage: one place for the stylesheet and
script a whole site shares, referenced by each layout's `THEME` field.

So the honest conclusion is that the old engine's *architecture* was better than what replaced
it for the specific job of "make something a human can see" — and the only part that has to go
is one line, `scriptEngine.eval(javascript, sc)`, plus the boilerplate that existed to feed it.

### XV.7 Importing an exchange package teaches nothing and is not on the agent surface (SH189)

> ⏳ **The teaching half shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Two things it settled and
> two it split off.
>
> **The bodiless 400 is gone, and classification was worth more than the message.** Passing the
> exception's text through would have been most of the value; it would not have told the caller what
> *kind* of thing to look at, and the four causes need four different next actions. The slug is the
> kind, the `fix` is the action. The cause's message is carried as well as the wrapper's, because
> Jackson names the offending property in the cause — reporting only the wrapper would have
> reproduced this defect one level in.
>
> **The second decision this section asked for answers itself.** "Whether the agent-side verb is a
> `batch` op, its own endpoint, or `shio import`" — the endpoint already exists and was
> console-only for a mundane reason: a stateless caller could not satisfy CSRF. SH167's matcher
> fixes that in one line, and nothing new needed inventing. The general lesson: before designing a
> new surface for a capability, check whether the existing one is unreachable rather than absent.
>
> **What split off.** The string-matching classifier was **SH267**, ✅ shipped in turn → see
> [CHANGELOG.md](CHANGELOG.md) Block P. What remains is the CLI/MCP reach, **SH268**, where the interesting part is
> that an SH68 package is a *zip*: a client with no filesystem cannot produce one, so "add a tool"
> is not the answer and the honest option may be for the manifest to say this capability is
> CLI-shaped.

### XV.14 What building the renderer found (SH247–SH249)

Three edges, and they share a shape: each is somewhere the renderer is *nearly* the thing it
replaced, and where the remaining gap is invisible from the outside because the page still
comes out.

#### XV.14 `form` is the last of the seven capabilities (SH247)

> ⏳ **`getRelation` and `search` shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. What §XV.14
> predicted held: they were not equally hard and treating them as one thing would have been the
> mistake. Two conclusions from building them, plus what is left.
>
> **The row shape is the interface, not the helper name.** All five listing helpers yield the
> same row, so a card body written for `{{#query}}` works for a relation or a search result.
> That was not in the original design and it is the thing that makes a *closed* capability set
> feel small rather than arbitrary: five helpers over one vocabulary, instead of five
> vocabularies. The corollary is that the DRAFT-preference and the total order had to be
> extracted and shared — a second copy of the preference is a second chance to get it backwards,
> and that failure mode is a preview showing the live page while the curator hunts for the
> unpublished change they came to look at.
>
> **A missing reference and a missing region are different mistakes with different owners.**
> SH183 marks an unfilled region slot in the page; `getRelation` skips a dangling reference. That
> looks inconsistent and is not: a region slot is named inside a layout's HTML, where before
> SH193 nothing but the renderer could see it, while a dangling post reference is already an
> SH154 `dangling-reference` finding with an address and a `fix`. The rule to carry: **mark what
> only the renderer can see; leave to the lint what the lint already reports.** One defect in two
> reports is two reports that can disagree.
>
> **What is left is `form`, and its absence is a decision.** A form needs somewhere for a
> submission to go — storage, spam handling, notification, retention — which is a subsystem, not a
> helper. Writing it before that is answered is writing half a feature, and the honest interim
> state is the one that now exists: `{{#form}}` is a write-time refusal naming SH247, so the
> author is told the capability is absent rather than shipping a page with a silently missing
> block.

#### XV.21 A raster is still the only answer to most of "looks wrong" (SH265)

SH187 extracts the appearance surface a document carries and stops exactly where a layout engine
would begin. Overlap, contrast, truncation, a broken grid, anything responsive: none of it is in the
markup, and no amount of parsing invents it.

The pieces exist. SH102 rasterizes already, through an **optional** Playwright import with a
teaching error naming the install — deliberately optional so `@viglet/shio` never pulls a browser.
SH249 made the input free: the renderer's own HTML, in-process, no outbound fetch. So the open
question is not how to take the picture.

It is **who reads it and where it lives.** An image on the agent surface is tokens an agent mostly
cannot use — SH102's own framing, and still true. But the pixel diff SH102 computes is a *number*,
and a number is exactly what an agent can act on. So the shape to design is probably: snapshot the
rendered HTML server-side, return the **diff ratio** against a stored baseline plus a signed URL for
the human who wants to look. That reuses the SH25 signing path rather than inventing a second one.

Two decisions in front of it. Whether an instance may run a browser at all — the reason SH102 kept
Playwright out of the CLI's dependencies applies with more force server-side, where the operator did
not choose it. And where the baseline lives: `shio/snapshots/` is a project directory and this would
be instance state, which is either a new store or an argument that the *client* stays the baseline's
owner and the server only ever answers "here is the current one".

### XV.22 A raster is still the only answer to most of "looks wrong" (SH265)

SH187 extracts the appearance surface the *document* carries — class inventory, unstyled
classes, inline styles, images without size, external sheets — and hashes it separately,
so a restyle and a structural break are distinguishable. The honest limit is everything
that needs a layout engine to be true or false: overlap, contrast, truncation, a broken
grid, anything responsive.

The pieces for the rest exist. SH102's `shio snapshot` rasterizes, with Playwright as an
*optional* import so `@viglet/shio` never pulls a browser, and SH249 made the input free
by giving the renderer its own HTML. So the open question is not how to take the
picture.

**It is who reads it and where it lives.** An image on the agent surface is tokens an agent mostly
cannot use; the pixel diff SH102 already computes is a *number* it can. That points at a shape —
snapshot the rendered HTML server-side, return the diff ratio plus a signed URL a human can open —
with two costs the design must accept first. It needs the SH25 signing path, so the URL is
time-bounded rather than a permanent hole. And it needs a decision this product has avoided: **may an
instance run a browser at all?** SH102 kept Playwright optional precisely to leave that open, because
a server rendering pages in Chromium is a different operational thing from one that does not.

Answer that first; the endpoint is small once it is answered.

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

`monaco-graphql@1.8.0` declares `peerDependencies: { "monaco-editor": ">= 0.20.0 < 0.53" }`.
A Dependabot bump set the console's direct dependency to `monaco-editor: 0.56.0`. pnpm resolved
it, and nothing failed.

What made it fatal rather than merely unsupported is that 0.56 introduced an `exports` map
(`"."`, `"./*.js"`, `"./*"`) whose patterns re-prefix `esm/vs/`, so the deep specifier
`monaco-editor/esm/vs/editor/editor.worker` expands to `esm/vs/esm/vs/editor/editor.worker.js`
and resolves to nothing — and 0.56 had also *removed* files monaco-graphql imports outright
(`esm/vs/editor/edcore.main.js`). `pnpm run compile:build` therefore failed, which is
`mvn package`, which is the release jar and the SH182 check.

The fix was a pin, not a shim: `@graphiql/react` already depends on `monaco-editor@0.52.2`
exactly, so the console's `0.56.0` was the outlier and aligning on 0.52.2 both satisfies the
peer range and collapses three copies of Monaco to one. The first attempt was a Vite `resolve`
alias remapping the broken prefix, and it is worth recording why that was wrong: it made the
first two specifiers resolve and then failed on a third, because the packages are genuinely
incompatible and an alias only hides the *symptom* at whichever import the bundler reaches
first. A build error that moves when you patch it is telling you the version is wrong.

The generalizable half is the guard. This is a **resolution** fact — true the moment the
lockfile is written, invisible to every runtime test — so the instrument has to be the
installer: `strict-peer-dependencies` (pnpm's default is off) turns "resolved a version the
dependent does not support" into a refusal. That is cheap and it is the only thing that would
have caught this at the bump rather than three commits later. It depends on SH241 only in the
sense that a guard nothing runs is the same as no guard.

### XVI.3 A trashed post still holds its friendly URL, and nothing knows it (SH243)

`ShPostUnified` carries `@SQLRestriction("deleted_date is null")`, which is what makes the SH55
trash invisible to every normal read. The cost is that every question of the form "is this URL
free" is answered *only about live posts*: SH202's new check, SH139's move refusal, and the
`duplicate-url` lint all inherit the restriction from the finders they use.

The failure is short: clone a page (`home` → `home-2`), delete the copy, clone again — the
second copy takes `home-2`, because as far as the query is concerned nothing holds it. Then
untrash the first. `restoreFromTrash` clears `deleted_date` with **no check at all**, and the
instance now has exactly the duplicate SH202 was filed to remove, produced by two operations
that are each individually correct.

SH236 met this blind spot from the post-type side — a trashed post still holds its
`post_type_id`, so a delete refusal that counted only live rows would have said "0 posts" and
then failed with a constraint violation — and its answer is the one to copy: **count the trashed
rows too and name them separately**, because the caller's next move differs (restore it, or
purge it). Applied here that is two changes rather than one: the free-URL search should see the
trash so a copy skips a URL that is only sleeping, and `restoreFromTrash` should refuse a
restore onto an occupied URL with a teaching error instead of creating the duplicate silently.

The second is the important one and it is not obviously a refusal — an alternative is to restore
under a numbered URL the way a copy does. That would be wrong here: a restore is a request to
get *the thing that was there* back, and quietly returning it at a different address is the
class of surprise P7 exists to prevent. Refuse, name the occupant, and let the human choose.

### XVI.4 Duplicating content is console-only (SH244)

Copy lives at `PUT /api/v2/object/copyto/{dest}` and nowhere else: no `batch` op, no MCP verb,
no CLI path. That is law P1's "no feature whose only interface is the console".

For a **post** this is close to harmless, and worth saying so rather than overstating the gap:
an agent duplicates a page by reading it and upserting the fields at a new address, which is two
calls and loses nothing, because a copy of a post is genuinely just its field values somewhere
else. The honest gap is the **folder**. The one thing a folder copy buys is the whole subtree in
a single call, and reproducing it over the agent surface means walking the tree, reading every
post and emitting an upsert each — the exact discovery-and-write loop Block H exists to remove,
in the one case where the server can do it in one traversal.

If it lands, it is a `folder.copy` op on the batch surface rather than a new endpoint, and it
composes rather than forks: SH202's naming and SH203's cycle guard already live on the shared
writers, so the op is an address pair and a call. Two things it must inherit rather than
reinvent — the copy is **draft** (`copyPost` sets `published(false)`, and a batch that published
a hundred pages by copying a folder would need SH107's gate for a reason nobody intended) and it
is **not** destructive, so it needs no SH108 confirm.

It stays 💭 rather than 📋 because the demand is unproven: no blueprint, no CLI command and no
recorded session has wanted it. Filed so that the next one does not have to rediscover that the
capability is reachable but expensive.

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
through the writer service, and the two surfaces cannot diverge on what a verb means — and it is
enforced nowhere. Every instance was caught by a person happening to read the neighbouring file
while fixing something else, which is not a method that scales and has already missed three of
these for at least a release each.

This repository has the right instrument for invariants that no functional test can watch:
`ShByteStabilityLintTest` fails the build on `Map.copyOf` where iteration order is observable,
`ShFolderSiteInvariantTest` on a direct `folder.getShSite()`, and `ShCsrfExemptionLintTest` on a
new blanket CSRF exemption. Each exists because the failure mode is a *future* caller rather than
a current request. This is the same shape: a controller method that writes content without
delegating to the writer that owns the verb.

The design work is the exemption hatch, not the check. The precedent is
`ShFolderSite-exempt: <reason>` and `byte-stable-exempt: <reason>` — a named comment carrying the
argument, so the rare legitimate case is *stated* rather than silently permitted. What the lint
keys on needs deciding too, and the cheap version is likely the right one: a write mapping on an
API class whose body touches a `*Repository.save`/`delete` directly. Precision matters less than
the build going red the day somebody adds the fifth one.

### XVI.9 A comment claiming parity is not parity (SH254)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. `content-notes.mjs`' `scalar()` said it
> used "the same rule `content-files` uses" and was missing that rule's `value !== value.trim()`
> clause, so a note with a trailing space could not survive the file — and with an empty base
> that was a **conflict** on every pull, not a phantom write.
>
> The durable point is the shape, and it is SH245's ("one writer per verb") in miniature: an
> invariant asserted in prose is an invariant enforced nowhere, and a comment claiming parity is
> *worse* than no comment, because it stops the next reader from checking. The repair was not to
> merge the two functions — they are deliberately separate readers with different parsers and
> therefore different charsets — but to assert the property they actually have to share, which
> is §XVI.10.

### XVI.10 The round trip is a property, and it is asserted as one (SH255)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth carrying.
>
> **The gap was in *kind*, not in coverage.** `parse(serialize(x)) === x` is universally
> quantified and every test was an example, with fixtures that all happened to be values their
> format represents exactly. Two real defects (SH253, SH254) lived in that gap across four
> blocks. A suite of any size has this blind spot; only a table closes it.
>
> **A declared exemption is a decision, and it expires.** A format may legitimately not hold
> something, so the property is "exact, or named with a reason" — plus SH142's rule, that a
> declared loss the format no longer has *fails*, or the list rots into a suppression hiding the
> next defect at the same spot. A refusal counts as legitimate non-holding: it is the only one
> that cannot lose work silently.
>
> **Writing the list is where the thinking happened.** The first draft declared nine losses; the
> real number is three. The reflex — stripping the body's framing must cost every whitespace-ish
> value — is wrong, because `bodySafe` keeps those in `fields:`, so they survive the *document*
> and merely stop being the body. The guard failed its own author's declarations on the first
> run, which is the strongest evidence that the exemption list has to be checked rather than
> trusted.

### XVI.11 An asset path accepts traversal (SH256)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Two things worth carrying.
>
> **The convention existed and one file had not heard of it.** `siteDir`, `sidecarNameFor` and
> `ShBlueprintRegistry` all refuse a separator or a `..` in a name, each with its own reasoning
> written down; `content-assets.mjs` was written before the rule was stated anywhere and never
> revisited. Worth noticing as a *review* lesson rather than a coding one: the defect is invisible
> to anyone reading that file on its own, and obvious to anyone who has just read one of the other
> three.
>
> **The two ends refuse differently, on purpose.** `assetLocationOf` throws because its input
> comes from the tree — hand-editable, and writable by a blueprint or a generated document — and
> `null` there already means "this is an id, leave it alone" (SH145), so a silent null would send
> a traversal to the server as a dangling reference and never mention it. `assetPathFor` returns
> null because its input is the server's own listing, where SH173's rule applies: one
> unprojectable row must not take down a pull. Same defect, two inputs, two correct answers —
> which is the thing to check when adding a refusal anywhere here.

### XVI.12 Finishing the round-trip table found the defect the table could not (SH257)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. The table now covers six round trips and
> the sixteen new declarations are all refusals, which is what a format whose output is a
> filesystem path should mostly do.
>
> The lesson is the third defect it turned up: `siteDir` trimmed a site's name for the directory
> while `serializePost` wrote the untrimmed name into the frontmatter, so a pull emitted a file
> whose `site:` disagreed with its own path and every later command refused it — the projection
> writing a tree it cannot read. **The table did not catch it**, because it asserts "exact, or
> declared" and a refusal was declared and a refusal happened; it was caught by reading the
> message, which blamed the disagreement rather than the trim. So extending a guard is worth doing
> for the reading it forces as much as for the assertions it adds, and the gap itself is §XVI.13.

### XVI.28 A premise about a dependency we do not have is unfalsifiable (SH276)

SH75 is hand-rolled MCP — around fifteen hundred lines of JSON-RPC, tool dispatch, resource routing
and a wire-conformance golden — and the justification is one sentence: *"not the MCP Java SDK, which
is Jackson-2/Boot-3 while this app is Jackson-3/Boot-4."*

That is the largest inherited premise in the repository measured by code it defends, and it is the one
kind SH274's method cannot touch. The artefact is **not on the classpath**, so a test cannot ask it
anything; checking would mean adding the dependency, which is the change being evaluated. Asserting
the absence of a conflict you have avoided by not depending on something is not a test, it is a
tautology.

So the instrument has to be different, and there are two honest ones:

- **A dated note** recording the SDK version examined, the conflicting coordinates, and who looked.
  Weaker than an assertion and much stronger than a sentence in the present tense, because it makes
  staleness visible: a reader can see the check is three years old and decide.
- **The budget tests as the standing argument.** SH132 caps the tool count and pins
  `mcp.tools.list.total`; `ShMcpWireConformanceTest` pins the wire against a golden. A hand-rolled
  surface that stays inside its budget and conformant is live evidence that the trade is still paying
  — which is a better ongoing case than a claim about the alternative.

Worth being clear about what this is not: not an argument to adopt the SDK, and not a suspicion that
the premise is wrong. It is that a sentence in the present tense about somebody else's release train
is the weakest form of a load-bearing claim, and this one carries more weight than any other in the
codebase.

### XVI.46 "Imports and renders without a migration" is still only a comment (SH294)

SH290 asserts that the render model matches its 2018 source — the half that was actually wrong. The
comment's full claim is larger: that a real `openviglet/bootstrap-site` export **imports and
renders**.

Nothing exercises that, and the export bundled here cannot: it declares one post-type (`Page`), no
`postTypeLayout`, and no template posts. The SH71 starter is a content site, not a rendering one. So
the strongest end-to-end evidence for Block P's central compatibility claim is a sentence.

What it needs is a fixture that *is* a rendering site in the 2018 shape — a `PageLayout` with
`sh-region` slots, a `Region`, a bound `postTypeLayout` — imported through `POST /api/v2/import` and
then fetched from `/preview`. That test would also be the first to exercise SH189's import
classification and SH280's unpublished-layout refusal together, which is where an imported package's
own publication state starts to matter.

The reason this is an idea rather than a designed task is the fixture. Hand-writing a package in the
2018 format risks asserting against a shape the 2018 exporter never produced — proving the code
agrees with the test's author rather than with the artefact. The honest source is a real export, so
the first step is finding one, not writing one.

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

### XVI.65 Half-shipped entries are invisible to the tool that owns the files (SH355)

This corpus records a half-shipped task by writing the ledger id with a parenthetical:
`- **SH96 (local half)** —`, `- **SH275 (partial)** —`, and two more. It is a good
convention, it predates roadkeep, and the tool cannot read it: an id it cannot parse is
an id that is **not in the changelog**, which is a different statement from "not done".

Four findings and one refusal come from that one gap. `SH217` and `SH219` are reported
as waiting on an unknown `SH96` when its local half shipped and the roadmap annotates it
`✅`. `SH238` is reported as being in two files at once — open in the roadmap, recorded
in the ledger — because its shipped half carries the bare id while its unfinished half
is still a task. And `retire` refuses outright: this project sets `[ledger] marker =
false` so its 234 pre-tool entries parse, and without a marker a retired line cannot be
told from a shipped one.

None of it is a *content* defect — every one of those lines says something true to a
reader. What is wrong is that the file's conventions and the tool's grammar disagree,
and the tool is the half that reports. Two directions, and the choice is roadkeep's
rather than this repository's: teach the grammar the parenthetical, or give partial
completion a first-class form. Until then the findings are noise that hides real ones,
which is SH142's argument about a baseline.

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

### XVI.67 One diagnostics test reads an empty table once in several full runs (SH357)

SH354's instrument paid for itself: the next occurrence had a **name**.
`ShAgentDurableIncidentTest.diagnosticsReadsTheTable` asserted the durable table was
empty, recorded one WEBHOOK incident, and read back `{}` instead of `{"webhook": 1}` —
on a run where nothing else failed and no code had changed. Roughly one run in fifteen.

**Three hypotheses eliminated, which is most of what an intermittent defect needs.** *Context-cache
eviction* (SH347's shape, and the obvious suspect since that class was its first victim): the run
boots **35** contexts against a ceiling of 64, so nothing is evicted — and that 35 is worth keeping,
because the source counter reports 33 and calls itself a lower bound. *Configuration leak*: the
sibling test that lowers `maxRows` restores it in a `finally`. *The SH88 lag*: this class sleeps for
it in seven places, but the diagnostics read applies no lag at all.

What is left is a two-way split, and `record` being **synchronous** is what makes it
decidable: it sets `unpersisted` when the durable store refuses a row. The test now
asserts that *first*, as a discriminator rather than as coverage — if it fails, the
store rejected the write; if it passes and the count still comes back empty, the row was
written and the read did not see it. One is a store problem, the other a transaction
problem, and until now the failure could not tell them apart.

Not fixed. The next occurrence names its own cause, which is the difference worth
shipping.

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

### VI.1 Multi-tenancy / SaaS (SH22) — core shipped, follow-ups open

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

### VI.2 Starter marketplace (SH23 epic) — superseded

> ⛔ **Superseded by Block J** (§XI). A human-browsable template catalog becomes an
> agent-appliable **blueprint registry**: same goal (accelerate a new site), correct
> unit (appliable package, not readable template). SH27→SH93, SH30→SH94,
> SH28/SH29/SH31→SH96.

### VI.3 AI authoring via Turing / MCP (SH24 epic) — superseded

> ⛔ **Superseded by Block H** (§VIII). The original decomposition (SH32 skeleton,
> SH33 read tools, SH34 schema tool, SH35 write tools, SH36 high-level ops, SH37
> Turing/tenant) was **CRUD-over-MCP** — a faithful wrapper around human-shaped
> endpoints, which would have reproduced every discovery, write-loop and
> verification cost it was meant to remove. Block H keeps the ambition and replaces
> the design: manifest + context pack + terse reads + teaching errors + batch +
> plan/apply **first**, then a deliberately small intent-named tool set on top.
> SH32→SH75, SH33/SH34/SH35/SH36→SH83, SH37→SH84.

### VI.4 Image/asset transformation API (SH25 epic)

> ✅ **Fully shipped (SH38–SH43)** → [CHANGELOG.md](CHANGELOG.md) Block F. The
> `shio_assets` tool (§VIII.8) is a shape over this; SH47 (§VI.1) remains the open
> per-tenant isolation follow-up.

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
