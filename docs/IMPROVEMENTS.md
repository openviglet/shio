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

## IX. Perception & verification loop (Block K) — ✅ shipped whole

The agent cannot see. Two of the three human round trips in a typical build existed only
for that reason, which made this the cheapest large win after Block H — a human cycle is
the most expensive unit in the system by orders of magnitude.

✅ **Shipped whole**: SH99–SH104, SH141–SH144, SH154, SH169, and the follow-ups closing
the block produced (SH609–SH642). The implementation reports live in
[CHANGELOG.md](CHANGELOG.md) Block K; the design subsections are gone rather than left
behind as history `git log` already holds.

**What the block established, and what later work should not relitigate:** `ok` is false
only for an *error*, because a gate that fails on what the CMS cannot prove is a gate
people bypass — and every mechanism added afterwards (SH141's delivery proof, SH142's
accepted findings, SH114-declared routes) turns a warning into a fact or an explicit
decision rather than tightening the gate. Everything the agent reads is cheap and
textual; SH102's PNG is scoped for a human. **§XV.4 (SH187)** revisits that for the
other reader.

**The follow-ups taught a second lesson, about the block's own instruments.** Four of
the eleven were defects *in* a guard rather than in the product: a lint that read prose
as code, a debt reader that could not tell a mention from an assertion, a gate whose
fallback was null in the deployment it demands, and a "flake" that was a scoping bug. A
perception loop that cannot perceive itself is the failure this block should expect
next.

### IX.73 One failure a run in ten, with no name (SH645)

`pnpm test` has twice reported `cli test: ℹ fail 1` on a tree whose backend and JS
suites were otherwise green, and neither time was the failing test's name captured — the
runs were greped for the summary line, which is the one thing that survives when the
output is filtered.

It does not reproduce. Nine consecutive `pnpm test` runs after the sightings were clean,
and `node --test --test-force-exit "test/*.test.mjs"` run directly in `cli/` has never
failed: 1348 pass, 0 fail, every time.

That combination is the shape SH354 is about, and the reason this is a task rather than
a note: a red that is green on the next attempt teaches people that red means nothing,
and it does that whether or not the red is true. Two sightings is already where the
habit forms.

What would close it is **capture rather than diagnosis**. `pnpm test` runs eight
packages and prints each one's summary, so the failing assertion's text is somewhere in
the output that was thrown away — the fix is a runner invocation that keeps the whole
log, or a per-package reporter that writes one, so the next sighting names a test
instead of a count.

Two cheap things to rule out first, both consistent with the evidence: a temp-directory
or port collision between packages running concurrently, and a test whose fixture writes
under a shared path. The second is one this repository has hit before.

### IX.74 Twelve promises nothing carries (SH649)

`roadkeep lint` reports twelve `body.promise` problems, all in `IMPROVEMENTS.md` §0.x —
the positioning and non-goals sections — where prose names task ids to point at the
epics they belong to. (They are not repeated here: naming them is the defect, and this
section would be the thirteenth.)

The rule's concern is not wording. An id in this project's own prefix is read as
**claimed**, so `next-id` derives past it and a number nobody will ever use is burned;
worse, a later reader follows the pointer and finds nothing carrying it.

They are **pre-existing**, and not introduced by whichever task first surfaced them: the
count is twelve with and without that change, so the rule arrived with a roadkeep
upgrade rather than the prose changing. Worth saying, because the obvious reading of a
newly-red lint is that the last commit did it.

Two ways to close each, and they are different decisions. Where the prose means "the
epic this belongs to", the fix is to name the id that actually carries a line. Where it
means "for example", the fix is to spell the example outside the id prefix so nothing
reads it as a claim.

Small, mechanical, and worth doing before the next `add`: every one of these is a number
the deriver has already skipped.

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

### XI.21 Where the list lives, and who vouches (SH646)

SH643 wrote down what an applied package may do and reserved the `shio-blueprint-`
prefix so it can mean something. What it did not build is the **index** and a
**provenance signal stronger than "it was in a directory"**, because those are not code:
they are a place for the list to live and somebody to say what enters it.

Four homes, each with a real cost. **npm by naming convention** needs no infrastructure
and inherits npm's provenance, but nobody curates — the product would point at a surface
it does not control. **A curated JSON in the repository** makes the vouching explicit
and reviewable in git, and costs recurring review. **A docs-site page** is that curation
plus one more artefact to keep current. **Waiting** is free.

Waiting is the choice, and not out of caution: there is **no third-party package**. An
index with nothing to index is infrastructure ahead of its problem, and the first real
package is what settles whether authors want a registry or a review — a question whose
answer is currently a guess.

What should trigger this is the first package somebody outside the project asks to
publish. The decision is cheap then because it has a subject, and the code half is
already there: the bounds are stated and asserted, the prefix is reserved, and a package
that will not load is refused on its own without taking the catalogue with it.

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

### XII.13 A site the trash cannot hold (SH648)

SH223 found its symptom mostly fixed: `ShSiteWriteService.delete` already trashes each
root folder before purging it, so the posts leave through SH55's trash with their audit
rows, the SH88 feed and the `DELETE` events. What it did not record was **the site
itself**, which SH223 added.

What is still open is that task's first design question: **is a deleted site trashed, or
only its content?** Today it is purged, so there is no undo at site level while there is
one at every level below.

Trashing the content and removing the site row cannot work, for the reason it could not
for a folder: the posts keep their `folder_id` and the root folders their `site_id`. So
the site needs a `deleted_date` of its own — a Liquibase change, a filter on every read
that lists sites, and the cross-DB ITs a schema move brings.

It drags the second question with it. A trashed site still occupies its name and
friendly URL, so re-creating it collides; "that name is taken by something you deleted"
is a teaching error somebody has to write, and it is the kind a caller meets at the
worst moment.

Worth doing as its own task rather than inside a service fix: SH200 chose exactly this
shape one level down, so the precedent is clear and the cost is schema plus a listing
rule, not design.

### XII.16 Nothing stops a fetch-type change from breaking authorization (SH650)

SH225 set out to make a permission decision independent of `spring.jpa.open-in-view` and
found the premise already false: `ShFolder.parentFolder` is a plain `@ManyToOne`, and
JPA's default there is EAGER, so the ancestor chain arrived with the `findById`. A test
written to catch the predicted `LazyInitializationException` — `open-in-view` off, three
levels deep — passed on the unfixed code.

The dependency was real one level down. The check worked *because of an entity mapping's
default fetch type*. Marking that association `LAZY` is the ordinary JPA hygiene move,
someone will make it for reasons unrelated to permissions, and before SH225 it would
have broken **authorization** — as a 500 rather than a denial, which is neither an allow
nor a deny and cannot be handled as either.

SH225 removed the dependency by walking the chain by id, and could not stop the next
edit putting it back, because the property is a *shape*: the walk must not navigate an
association. `ShContentAclEntryPointTest` asserts the annotation half by reflection;
this half has no fixture that can express it, since any behavioural test is green on
both sides — which is why that one was withdrawn rather than kept as reassurance.

So the deliverable is the instrument, per SH338: a lint failing when the chain walk
reaches for the entity graph. The narrow form scans that one class; the form worth
having asks which security-relevant reads navigate associations at all, which overlaps
the SH22 join hazard the same walk used to carry.

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

### XIII.22 A post created through the console gets no friendly URL (SH651)

SH396 extended the build-a-site benchmark's baseline to hand-build a render model, and
it stopped one call short of opening the page. The reason is not the layout. Five posts
created through `POST /api/v2/post-unified` with `postTypeId` and `data.headline` —
where `headline` is the post-type's title field — come back with **no `furl`**, so the
context pack reports all five at their folder's own address:

    node post /pages-4cd715 type=BenchArticle state=DRAFT title=naive-page-1
    node post /pages-4cd715 type=BenchArticle state=DRAFT title=naive-page-2

Five distinct posts, one address. `/sites/<site>/default/en-us/<folder>/naive-page-1` is
a 404, and it is a 404 for a reason that has nothing to do with SH280's published-layout
rule.

What is not known is which of three things this is: the console UI always sends `furl`
explicitly and the API is right to require it; the write should derive one from the
title field the way the agent surface does; or the address projection is wrong and a
furl exists. Each has a different fix and the first is not a defect at all — so the
first deliverable is the probe that tells them apart, not a change.

Two reasons to answer it rather than work around it. A post that cannot be addressed
cannot be verified, linted or delivered, and nothing on the write path says so — it
answers 201. And SH396's ratio is a floor by one call and one page fetch until this is
settled, in the direction that flatters the baseline.

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

### XVII.54 The array is why the other three waited (SH478)

SH474 declared the four keys an upload stamps and stopped there, leaving `ALT`,
`CAPTION` and `TAGS` — SH62's Media Library metadata, written by `updateMetadata` rather
than by an upload. They are the same defect: SH168's `unknown-field` is an error, so the
first caption a curator writes turns a green `verify` red on a post nobody did anything
wrong to.

They waited for one of them. `ALT` is a string and `CAPTION` is a string, and either
would have been a line beside `SHA256`. `TAGS` is an **array**, and the catalogue's
`Multi Select` is the only widget shaped like one — it reads `widgetSettings` naming the
options a curator may pick, and a tag vocabulary is exactly what a media library does
not have in advance. Declaring it with an empty option list models a field that renders
as a select nobody can select from; declaring it as `Text` says the value is a string
when the writer stores a JSON array, which is the same lie the other way round.

So the decision is what a free-form list of strings is in this catalogue, and the two
honest answers are a `widgetSettings` mode on `Multi Select` meaning "any value", or a
new widget. Whichever lands, the declaration and the console component have to agree,
because the File form is one a curator actually opens.

The measurement is already written: `ShFilePostTypeSourceTest.STILL_UNDECLARED` holds
the three with their reasons and fails if one is declared without being removed.

### XVII.55 Two templates, and neither serves an authorable replica (SH502)

SH446 made a scaffold installable by writing the version the registry has: the built-in
template asks for `@viglet/shio-client@^2026.2.1` and `npm install --dry-run` resolves.
The half it did not reach is the one an authorable replica needs.

Two templates, and neither combination delivers one. The **built-in** template installs and carries no
sections renderer — `shio-client` only, nothing under `app/` or `lib/` that reads a Page's `sections`. The
**`marketing-site`** blueprint does render them, and asks for `@viglet/shio-sections@^2026.3.1`, which
answers **E404**: it has never been published, and neither has `@viglet/shio-model`.

So a fidelity replica has two working paths — Shio's own renderer, or a front end it
does not need — and an authorable replica has none. Its content is sections, and the
only scaffold that draws sections cannot be installed.

The measurement is one command per template and it was not in SH446's scope, which asked
for the scaffold to install and got that. It is the same split the skill names: the
version write is the report, the publish is the behaviour, and only the first shipped.

Publishing `shio-sections` is the whole fix, and until it lands the built-in template
should not be offered for a site whose pages hold sections — `init --site` knows the
site and can read whether its Pages carry any, so the wrong scaffold is refusable rather
than silently useless.

### XVII.79 An assertion that names the fix instead of the defect (SH568)

SH560's corpus holds its red assertions as a named list, and every entry is checked in
both directions — an entry gone green fails the run, which is what stops the list
becoming where findings go to be forgotten. That design is right and one entry defeats
it.

`theme-heavy/layout-raw-field-CSS` reads: *the generated layout emits the CSS field with
two braces inside the getRelation block*. That is a statement about the **template**,
and it presumes the fix is a third brace. SH555 did not add one, because SH461 had
already decided this exact question and written the reason down: the cure belongs where
the value is built, *or every hand-written layout in the product keeps the defect and
only the generated one is cured*. So SH555 marked the row's raw-text fields
`Handlebars.SafeString`, and the served page now carries its `url()` quotes intact — the
defect is closed, the corpus's own group D goes green on the rendered document, and this
assertion stays red forever.

The finding is not that the entry is stale. It is that an assertion phrased as *the code
does not do X* pins one implementation of a fix, and the both-directions check then
converts a **correct** repair into a permanent red — the same failure SH554 named one
block over, an instrument in a gate reporting a loss on something that lost nothing. The
entry should assert the behaviour: no entity-escaped `url()` reaches a rendered verbatim
page, whichever brace count produces that.

### XVII.89 Two readers of one brace count (SH606)

`source-corpus.test.mjs` fails the CLI's generated layout for emitting `{{CSS}}` inside
the `{{#getRelation "/theme/captured"}}` block: two braces, so Handlebars escapes it, so
a megabyte of stylesheet reaches the browser with every `url()` as `&quot;…&quot;`. That
reading was right when it was written.

SH555 then shipped, on the **render** side: a `getRelation` row's raw-text fields —
`Theme.CSS` and `JAVASCRIPT`, `Region.JAVASCRIPT`, `PageLayout.JAVASCRIPT` — are
`Handlebars.SafeString`, keyed by field. Under that fix `{{CSS}}` is not escaped, and
the assertion is checking a brace count whose meaning the render changed underneath it.

So the two disagree and **neither run says which is wrong**. The corpus reports a held
finding, the render suite is green, and nothing joins them. SH594's reader is what
surfaced it: the entry was owned by SH555 *after SH555 had shipped*, the one state a
debt ledger cannot see on its own.

Only one of two things is true:

- the render fix is the whole answer and the assertion measures a proxy that stopped meaning
  anything, so it is retired;
- or `SafeString` is narrower than the assertion assumes — a field off SH555's list, a layout
  written by hand, a path where the row does not come through `getRelation` — and the generator
  should write `{{{CSS}}}` anyway.

**The deliverable is the answer, not the edit.** Whichever way it goes, one of the two
artefacts is removed rather than both left standing: two readers of one fact that agree
only by accident is SH583's shape, and this is its third sighting.

### XVII.90 The half of a refresh that is still measured in megabytes (SH611)

SH592 made a page cheap to re-check and left the bytes alone. An **asset** is
re-downloaded only when the page referencing it changed, so a stylesheet, a font or a
hero image that moved under a page that did not is silently the old one — and the only
answer the report can give is *run `shio clone`*, the ten minutes the flag exists to
avoid.

This is the larger half by volume: `vilt-group.com` was 198 pages and **403 assets**,
and the assets are where the 52.7 MB is. Re-checking 198 pages for 198 requests and then
either skipping 403 files or re-downloading all of them answers the cheap question well
and the expensive one not at all.

The mechanism is the one already built, one row along. `download` fetches through
`fetchBytes`; giving an asset row the same optional `etag` and `lastModified` a page row
now carries, and sending them, makes a 304 mean *the file on disk is still right*. The
bytes are already at `shio/assets/<file>`, so nothing has to be rebuilt — strictly
simpler than the page case, which needed the markup.

Two things it must not break. `placeAsset` dedupes by digest, and an asset skipped on a
304 has no digest this run, so the prior row's placement is carried rather than
recomputed. And the action vocabulary — `wrote`, `deduped`, `failed`, `collision`,
`skipped` — gains `unchanged`, which `OUTCOME_OF_ACTION` and the download report both
read; an unchanged asset counted as `wrote` would report work that did not happen.

## XIX The public site (Block S)

Turing ships turing.viglet.org and Shio ships a README. The product with the sharpest
positioning statement in the portfolio - a CMS whose primary operator is a coding agent
- has no page that says so, and the ten laws, the eleven MCP tools, the content-as-files
projection and site replication are legible only to somebody who already cloned the
repository.

This block builds that page in the shape `apps/site` already proved in Turing: Vite,
React 19, Tailwind v4 and shadcn/ui, prerendered to static HTML per route, served at the
root of a domain, with no backend of its own. What it does not copy is the argument.
Turing's centrepiece is a live search box, because a visitor can type a query and judge
the answer. Shio's is a Claude Code session, because what is being sold is not a feature
a human operates - it is how cheaply an agent operates the product. Every section on the
landing page therefore answers one of the ten laws with something a visitor can check: a
token count, a teaching 4xx, a file tree, a draft that did not publish itself.

The site is also written for the reader it is about. An agent evaluating a CMS reads the
page it was handed, and a Claude Code-first product that answers only in HTML is making
its own argument badly.

### XIX.11 A public instance to point at (SH540)

Turing's landing page went from a scripted demo to a genuinely live one by pointing the
widgets at a public read-only instance, and that change is what turned its centrepiece
from an illustration into evidence. Shio's equivalent is an instance serving a seeded
demo site, read-only, with a CDA token scoped to it.

What it makes live is specific and small: the manifest and context pack the page quotes,
the CDA queries behind the delivery section, a preview link that opens a real rendered
page, and the transcript player's outputs checked against what the instance actually
returns. The write half stays a recording - a public instance an anonymous visitor can
write to is a different product with a different threat model.

The condition for building it is that a stale instance is worse than none: SH460 already
made an instance date its own build and the conformance harness refuse one older than
HEAD, so the demo is deployable on that discipline rather than on somebody remembering.
The site degrades to the scripted content when the instance is unreachable, and says
which one it is showing.

## XX End-user documentation, round two (Block T)

Block R measured this and could not fix it. It took the coverage matrix from **33 wrong
of 40** to **6**, and every one of the six is a cluster **no Block R task ever named** —
they were not leftovers that ran out of time, they were never in scope. That is the
finding the matrix existed to produce, and it is why these are a block rather than a
snagging list.

Four of the six are curator-facing. The agent's surface is now the best-documented part
of Shio and the console is the worst, which inverts who actually needs a page: an agent
reads a manifest, a person reads a page or gives up. P7 — *never surprise the human* —
is the law the gap sits on.

Two things make this round different from Block R's. **The examples are executed now**
(SH499): two instruments compare every published verb, route, tool name, configuration
key and read-only request against a running product on each conformance run, so a page
written here is checked rather than trusted. And **⚠️ rows are rewrites, not additions**
— a page describing a shape the product left makes any new section contradict it, so the
stale prose goes first.

Each task lands two commits, as Block R's did: the Shio-repo ship and the `vigletdocs`
page edit, with the `COVERAGE.md` verdict flipped in the same commit as the page.

### XX.4 The webhook is documented from the side that receives it (SH626)

SH12 shipped outbound webhooks on publish, unpublish and delete, with per-site
subscriptions and a signed payload. What is published is the *consumer* half, in
`nextjs-starter § Environment` — a variable to set in somebody else's app. Nothing
describes how to subscribe, what the payload contains, or how to verify the signature.

The signature is the part that makes this more than a thin page. An integrator who
cannot verify it either skips verification — turning a webhook into an unauthenticated
write trigger reachable by anyone who guesses the URL — or abandons the feature. Both
outcomes are worse than the feature not existing, and both follow directly from the
absence.

The page also has to say what it does **not** guarantee: delivery ordering, retry
behaviour, and whether a consumer must be idempotent. Those are the questions an
integrator hits in production, and an answer of "unspecified" is a legitimate one that
still has to be written.

### XX.5 The locale segment a reader meets before anything explains it (SH627)

SH53 shipped content i18n: the locale axis, linked translations, `?locale` on the CDA,
and the checked locale segment in the delivery grammar. No page mentions any of it.

The delivery grammar is why this is not merely a missing feature page.
`/sites/{site}/{format}/ {locale}/{path}` puts the locale in every published URL, so it
is the **first** thing a reader meets — before they have decided whether they want
translations at all. An unexplained segment in the one URL shape everybody copies is a
question asked on every single page of the site.

Then the modelling half, which is the part that is genuinely not guessable: whether a
translation is a separate post linked to its original or a field on one post, what
happens to a link that points at a page translated in one locale and not another, and
what the CDA returns when a requested locale has no translation. Getting that wrong is
discovered after the content is written, which is the most expensive time to discover
it.

`website-development` already documents the grammar and would be the natural home for
the URL half; the modelling half belongs beside post types.

### XX.6 The page a contributor reads first names a stack this product left (SH628)

`developer-guide` still lists Elasticsearch 9.3.3 in the stack and names neither the
agent surface nor the CLI. It is the ⚠️ of the six: not thin, wrong.

Its audience is what makes it the sharpest of them. A reader here is deciding whether to
build against Shio or contribute to it, and the page tells them the product is a 2018
headless CMS with a search dependency it no longer has. Every one of the ten design laws
is invisible from it, and the two surfaces this project treats as primary — MCP and the
CLI — are not mentioned. Someone who reads it and leaves never sees the product that
exists.

The stack claim is also the kind that should be derivable rather than typed. SH499 found
the same class of defect in `README.md` (13 MCP tools claimed, 11 registered, two of the
names invented) and the answer there was an instrument, not a correction. A version
number in a prose page is the same shape of claim, and the same question applies: what
would fail when it goes stale.

### XX.7 The image epic's operator half, folded into a page for authors (SH629)

The SH25 epic's transform parameters are documented — `?w=`, `?h=`, `?format=`, `?crop=`
in `website-development § Static files and images`, correctly, for the person writing a
template. What is missing is everything the person **running** the instance needs: the
result cache and its invalidation, the dimension and format limits, and how to turn
signed URLs on.

`COVERAGE.md` scores the cluster 🧩 rather than 🔴, which is the honest verdict and also
why it survived the block — a partially-covered row does not read as an orphan in a
scan.

The limits are the operationally urgent half. An image transform endpoint with no
documented ceiling is a request for an arbitrarily large allocation, and signed URLs are
the mechanism that stops a stranger driving the resize pipeline from a public page. Both
exist; neither is discoverable, so the default deployment is the unconfigured one.

This is a section under an operations page rather than a page of its own, and the
question it should answer first is what the defaults already protect against — an
operator who reads it and needs to change nothing has still been served.
