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

### VIII.23 The model's half of the proposal (SH523)

Modelling is the least agent-native thing here, and the contrast is inside the product.
For **content**, SH379 gives a proposal: what would change, what it costs, accept it an
entry at a time. For the **model** there is no such artefact — I write a post-type, push
it, and find what broke later.

What I could not ask, in fifteen rounds of needing to: which posts would violate a field
I am about to make required; which templates read a field I am about to remove; which
declared fields nothing reads; which stored keys no type declares. That last one is not
hypothetical — a `File` post has stored `SHA256`, `SIZE`, `WIDTH` and `HEIGHT` against a
type declaring one field, and it took a lint firing 66 times to surface (SH474).

`impact <address>` already answers the neighbouring question for templates — what a
layout change would affect — so the shape is established and this is the model's half of
it.

The proposal is the right artefact rather than a lint, because a model change is a
**decision with alternatives**: making a field required is either a data cleanup or a
mistake, and only the count of violating posts tells them apart. A refusal cannot say
that; a proposal can, and I can accept the parts I mean.

It is also what makes modelling incremental. Today the safe move is to declare a field
optional and never tighten it, because tightening is the change nobody can see the cost
of.

### VIII.24 The model's own reachability (SH524)

`template-unknown-field` catches a template reading a field the type does not declare.
Nothing catches the inverse — **a field no template renders** — and that inverse has now
produced two defects, found by hand, weeks apart.

`SectionFeatureGrid.features`, `SectionFaq.questions`, `SectionLogoWall.logos` and
`SectionTestimonial.quotes` were declared, captured, stored and drawn nowhere: four of
seven section types rendered as a bare heading (SH465). `SectionHero.ctaHref` was
captured, stored, rendered into an `href` and annotated by nothing, so a curator could
rename a button and not repoint it (SH503). Both are modelling failures that surfaced as
rendering bugs, and both were found by counting annotations against fields by hand.

**A field nothing renders is dead content.** It costs a curator a form control that changes nothing, it
costs a capture the work of extracting it, and it makes every count of "how authorable is this page"
wrong in the flattering direction.

The project already owns this idea and is proud of it: `ShAgentP1ConformanceTest`
classifies every capability by whether an agent can reach it, and holds the unreachable
ones as an asserted list — a number that cannot drift without a reviewable diff. This is
that instrument pointed at the content model instead of the API surface: for every
declared field, does a template read it, and does the reader annotate it.

The asserted-list shape matters more than the check. A field nobody renders is sometimes
correct — a `SHA256` is delivery metadata — so the answer is a list somebody signed, not
a refusal.

### VIII.25 Modelling by example, pointed inward (SH525)

`propose` derives a post-type from markup nobody wrote for Shio: on `viglet.org` it
offered `BlockBuildTheFuture` with `heading:text, body:htmlEditor, image:file,
items:relator`, inferred from one page's structure. That is data modelling by example,
it works, and it exists only inside conversion — pointed at a stranger's site.

Point it at mine. Three product pages written as content, and the question "what
post-type are these three instances of" has the same answer computed the same way. The
capability is built; only its input is hard-wired to a capture.

It is the modelling verb I have most wanted and never had. Writing a post-type from
nothing means naming fields before knowing what they hold, which is the wrong order — a
model is easier to see once three instances exist and disagree. Today the only route is
to author blind, discover the shape, then rewrite the type and migrate what already used
it.

Two things it should carry that the conversion version does not need. **Which instances
disagree**, since a field two of three share is a different proposal from one all three
do, and the disagreement is the interesting part. And **what it would cost to adopt**,
which is SH523's plan — inferring a type is only useful if applying it is reviewable.

The honest limit belongs in the report: three instances of a thing are evidence about
those three. A proposal, not a conclusion — which is what `propose` already gets right.

### VIII.30 The sitemap still has the word SH545 replaced (SH553)

`folderPosts` fetches both state rows, drops the published one and reports `state` from
what is left, which is the same defect SH545 fixed on `/agent/read` and `/agent/find` —
so a session that starts by reading the pack is told every page of a fully published
site is a draft.

It was left out of SH545 deliberately and the reason is a measurement, not caution. Two
of that task's three designs were refused by benchmarks: an unconditional field cost
`addressing@scale` its floor and a full convention sentence cost `discovery` its own.
The sitemap is the token-heaviest of the three surfaces — one node per post — and it
carries an SH116 digest, so a field there changes what a cached pack compares against,
and that has to be measured before it is added rather than after.

What makes it worth doing anyway is what the pack is for. P2 says one call replaces a
session, and the call it names is this one: an agent that reads the pack to learn what a
site holds learns the structure and is then misinformed about the state of every page in
it. `Node` also uses `NON_DEFAULT` rather than `NON_NULL`, so the conditional shape
SH545 settled on does not transfer unexamined — whether a `Boolean` false is omitted
there has to be asserted rather than assumed.

### VIII.35 Inventory is not routing (SH601)

The manifest is complete. That is the finding, and it is the uncomfortable one: every
round that lost time to discovery lost it holding a correct and complete list.
Completeness was never the constraint.

What a reader arrives with is an **intent** — *this run failed with a 500*, *I need to
know what conventions this instance already has*, *I want a page like the one over
there*. What the manifest offers is 57 booleans, 28 paths and a `limits` object. Mapping
the first onto the second is the work, it is unbudgeted, and it is exactly where the
errors were: `diagnostics` is discoverable to a reader who already suspects it exists.

So P2's second half is routing. `startHere` exists and lists; it does not branch. A
version that did would answer *given this intent, here is the one call*, for a small
closed set of intents that covers what runs actually arrive with — a failure to explain,
a convention to learn, a job that may already have a blueprint, a claim to prove.

Two cautions. It must not become a fifth registry (SH599 is the opposite direction). And
the intent set has to come from measurement, not from introspection: SH597's fixture is
precisely a list of intents with a known right first move, so the benchmark and the
router want the same table. Build the table once, score against it, then route with it.

### VIII.36 The other narrowed discovery response (SH608)

SH600 closed this on the manifest: a projected document carries `omitted`, a section
name and an entry count for everything `include=` removed, so `?include=identity` can no
longer answer 200 while dropping all fifty-seven capability flags in silence.

`/agent/context` takes the same parameter and has the same hole. `?include=instance`
returns four scalars and drops the model, the sitemap and the conventions — including
the remembered notes, the section SH597 measures a whole intent against. SH600's
argument applies unchanged: a caller narrowing a *discovery* surface does not yet know
what is there.

It was left out deliberately, for one reason worth stating. The pack's sections are
**content**-shaped, so a projection and an empty instance are indistinguishable after
the fact the way `features: null` and `features: {}` are not: `sites` is an empty list
on a fresh instance and an empty list when nobody asked. The report has to be built
where the sections are chosen, from the parsed section set, rather than derived from the
assembled pack.

The second half needs a decision rather than a port. The pack already reports
`budget.truncated` when the sitemap walk stopped early, so two mechanisms would be
saying "you did not get all of it" for two reasons. Whether that is one field or two is
the work.

### VIII.37 The 404 that says nothing, on the surface a typo is likeliest (SH621)

SH499's execution instrument issued the requests `docs-shio` publishes and this was its
first finding: `GET /api/v2/post-type/Article` on a bare instance answers **404 with
`Content-Length: 0`**. Not a thin problem document — no body at all.

Compare the same miss one surface over. `GET /api/v2/agent/read?address=post:nosuch/x`
answers a full RFC 9457 document: `type`, `title`, `detail`, `fix` naming the call that
lists the options, and `allowed` carrying the names. P6 is implemented, and it is
implemented on `/api/v2/agent/**` only.

That is the wrong half to have covered, because post types are **the one name-keyed
surface** (SH117 retired the id-keyed twin), so a name is the only thing a caller can
get wrong and a typo is the likeliest way to arrive here. `didYouMean` is a solved
mechanism on this codebase and this is the endpoint it was invented for.

The gap is not cosmetic: with zero bytes back, a renamed type and a type that never
existed are the same answer, and the caller's next move — retry, create, or ask a human
— depends on which. An agent that cannot tell them apart guesses, and guessing on a
model change is how a second post-type gets created beside the one that was meant.

### VIII.38 P6 is a controller-advice property, and the first error is not thrown by a controller (SH622)

The other two findings of SH499's first execution run were `GET /api/v2/cda/site` and
`GET /api/v2/cda/post/{id}/editable`, both answering **403** to console HTTP Basic with
Spring's own body: `{timestamp, status, error, path}`. Anonymous gets the same shape
with 401.

The cause is structural rather than an omission on those two endpoints. P6 is
implemented in `@ControllerAdvice`, and a rejected credential never reaches a controller
— the filter chain answers first, so *every* endpoint on this product has one 4xx that
carries no `fix`, and it is the one a caller hits before any other. A page publishing a
CDA read is correct about the route and silent about the credential, and the 403 that
would have said so says nothing.

This is worse for an agent than for a person. A human reads the page again; an agent has
three indistinguishable readings of 403 — wrong scope, right scope with the wrong key,
or an endpoint that is not for this credential at all — and no field to tell them apart.
The manifest knows which scope each endpoint wants, so the answer exists and is simply
not where the error is.

The fix is an entry point and an access-denied handler emitting the same problem
document the advice does, with `fix` naming the scope and the call that mints one. The
reason this was never noticed is the reason it is worth writing down: no test asks an
unauthenticated question and reads the *body*.

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

### IX.45 An absolute href becomes a field value, and the rebase never sees it (SH564)

`propose` extracted `ctaHref = "https://awkward.test/docs/guia"` from an anchor whose
href was absolute, and `convert` wrote it into `SectionHero` unchanged. The button on
the accepted hero then points at the original site.

SH466 is not the fix and is not wrong. It rebases **root-relative** hrefs onto the site
being served, and it does that in the renderer over the page's `content`. Neither half
reaches this: `ctaHref` is a field, not content, and its value is an absolute URL with a
foreign host — which at render time is indistinguishable from a link a curator meant to
point off-site. Rewriting it there would break every deliberate outbound link on every
page.

So the phase that can tell is the one that knows what the origin was. `clone` records
the source's origin and `propose` resolves each href against the page that carried it;
an href whose host equals the capture's origin is the source's own path and should be
stored the way the theme already stores one — as the **source-relative path** (SH461:
the rewritten CSS names `/img/x`, not the tree path, because that is what the instance
resolves). An href whose host is anything else is genuinely external and stays whole.

The shape is the one this project keeps finding: the correction shipped half of itself.
SH466 closed verbatim markup and left the authorable path, exactly as SH452 fixed a note
and not the fetch. Its assertion is in `source-corpus.test.mjs` group D, red, naming the
field.

### IX.46 The title is RCDATA, and both entity fixes routed around it (SH565)

Measured: `scanPage("<title>Northwind &amp; Co</title>")` yields the five characters
`&amp;`, and `decodeEntities` on the same string yields `&`. The decoder is right and
nothing calls it here.

Two shipped fixes and the title falls between them. SH476 decodes text "when it becomes
a field" — `propose.mjs`' `text()` and `alt`. SH550 decodes attribute values, and states
the exclusion in its own words: raw-text bodies are not decoded. `decodeEntities`
appears exactly twice in `clone.mjs`, once in `parseAttrs` and once as its own export.

`<title>` is neither an attribute nor raw text. In HTML it is **RCDATA**: a `<` inside
it is data, and an entity inside it *is* decoded — which is what separates it from
`<script>` and `<style>`, the elements SH550's exclusion was written for. Lumping them
together is the whole defect, and `title` is the one RCDATA element a capture reads.

It reaches further than the other two did, because the title does not travel through
`propose`. `scanPage` puts it on `doc.title`, `buildInventory` copies it onto the page
row, and `convert` writes it into `Page.data.title` — so **fidelity and authorable are
both wrong**, where SH476 was authorable only. Every source page with an ampersand in
its title is affected, and it compounds on re-capture the way SH476 does: each pass
escapes what the last one stored.

Its assertion is `source-corpus.test.mjs` group D, on the `awkward` archetype, red,
comparing the stored title against the source's own bytes.

### IX.47 The reader does not read the tree it names (SH567)

SH527's reader holds a named DEBT list, and one of its groups is *the public site in
`shio-site/`, which has no test tree at all — the cheapest group to fix and the one
whose absence is least defensible*. That sentence is now false: SH566 shipped
`shio-site/scripts/scroll-ownership.test.mjs` and an opt-in browser test beside it, both
naming their id.

The reader still walks 285 suite files and none of them is in that tree, so SH566
arrived in `+ actual` on the next run and the suite went red on a task that had done
exactly what the rule asks. The debt read 44 where it should read 43, and the fix a
reviewer is steered toward is the wrong one: adding SH566 to DEBT would record *this
task cannot carry an assertion* about a task that carries two.

The comment already names the remedy and calls it cheap. What it did not anticipate is
that the list would go wrong in this direction — an entry earned by a tree the reader
cannot see is indistinguishable, at the assertion, from an entry earned by a fix nobody
tested. The nine SH530–SH539 entries in the same group should be re-judged in the same
pass: some of them may now be asserted too.

### IX.48 A source that answers a request badly, which no fixture has ever been (SH569)

The shared fixture says why it is not this, and it is right about itself: *nothing 404s
— an unreadable page is its own finding, and one in the fixture would blur the rule
being asserted.* Correct for a seam test, and exactly the hole. Every source this work
was driven against answered some request badly, and the two that cost most were both
fetch-layer:

- **SH431** — a 200 of `application/json`, linked on purpose, filed as an error. Twenty-four of
  `openviglet.github.io`'s data files landed in `problems`, where the coverage gate reads them as
  pages that could not be read at all and downgrades **every count in the report** to a lower
  bound. A wrong classification, not a wrong fetch.
- **SH544** — a 265-character title ended a 201-page conversion **after 403 assets had been
  uploaded**, blocking every retest until it was fixed.

So the subject is not that a request fails. It is that **the run does not die and the
report is honest about what it got**: a dead internal link, a 500, a redirect loop, a
linked JSON document, HTML served as `text/plain`, an asset past `--max-asset-bytes`.
Each has a right answer that is neither a crash nor a silent omission, and `routes`
spells all six.

The assertion to write first is the one SH431 is about: a `problems` row and a `notHtml`
row stay different claims, because collapsing them turns a bounded sample into a report
reading as complete.

### IX.49 Markup a browser repairs, and a scanner does not (SH570)

SH569's archetype fails at the fetch layer; this one is a 200 whose body is wrong, and
the two are separate because a scanner cannot report what a fetch already refused.

Nothing in the corpus or the shared fixture is malformed. Every page was typed by
somebody who closed their tags, so `tokenize`, `scanPage` and `stripNonMarkup` have only
ever been measured against markup a browser would not have had to repair — and a real
source is repaired constantly and silently. The list, one trait per known cost:

- an **unclosed** `<div>` or `<section>`, so a block's extent is a guess;
- **no `<title>`**, which `Page` requires and SH544 proved is load-bearing;
- an **absurdly long** title — SH544's 265 characters, as a fixture rather than a story;
- a **nested form**, which the parser must not treat as two;
- a `<base href>` that **disagrees** with the serving path, so every relative reference has two
  possible resolutions and only one is the browser's;
- a declared `charset` the **bytes contradict**, which is where mojibake enters as content rather
  than as a terminal artefact.

The last needs care, because the checklist records getting it wrong from the other
direction: judge encoding on **bytes**, never on what a terminal prints. So the
assertion compares the stored field against the source's bytes, and the fixture carries
the mis-declared charset deliberately — an archetype whose own encoding is ambiguous
cannot be the one that settles an encoding question.

### IX.59 The findings nobody narrated (SH602)

Twelve rounds produced roughly forty tasks. Every one of them came from the same place:
a session ended, somebody read it back, and a pattern was noticed. That is an expensive
and lossy collector, and it only ever runs when a human asks for it.

The instance already has the parts. Durable incidents record what failed
(`durableIncidents`), `diagnostics` explains a 5xx, `shio remember` survives a session,
and `open-findings.json` is the ledger a finding lands in. What is missing is between
them: the cheap signals that are not incidents. A 404 on a path an agent constructed. A
4xx whose `didYouMean` fired. A capability asked for under a name that does not exist. A
command run with a flag the notes had already recommended. None of those is a failure
worth an incident, and together they are the whole discovery-friction record.

The shape: the instance counts them, keyed by what was *asked for* rather than by what
broke, and `diagnostics` reports the top of that list. A key asked for often and
answered never is a missing capability with a name attached — which is strictly better
evidence than anything a transcript review produces, because it is the reader's own
vocabulary rather than a summariser's.

This is the task that converts "we will find out by using it" from a hope into a
pipeline. It matters most for readers who are not this one: nobody is going to narrate a
stranger's session.

### IX.60 One reader, one instance, one shape (SH603)

The strongest evidence this project has about its own protocol is twelve replication
rounds. They are also its narrowest: one agent, one instance, one family of sources, and
a corpus that the same reader helped design. The verification loop closes entirely
inside the project — the laws are enforced by lints written by the people who wrote the
laws, and scored by a reader who knows where everything is.

That is enough to find defects. It cannot distinguish a protocol that is genuinely
discoverable from one a familiar reader has memorised, and it says nothing at all about
the shapes replication never exercises: building a content model from nothing, migrating
from another CMS, a locale added after the fact, a run that is mostly curation rather
than authoring. Whatever P2 costs in those, the number today is unknown, and no amount
of further analysis produces it.

So the deliverable is a second task shape driven end to end and scored with SH597's
metric — build a small site from an empty instance, no capture, no corpus, following
only what the instance and the plugin say. The value is not the site. It is the delta
between the two numbers: if discovery costs the same in a shape nothing was tuned for,
P2 is real; if it does not, the tuning is what was being measured all along.

Run it before believing any P2 improvement generalises.

### IX.61 The gate whose fallback is null in the shape it demands (SH609)

SH460 asks two questions in one: is this instance running the code under test, and can a
matching commit still say so. On a clean tree the hash answers it. On a **dirty** tree
the hash no longer describes what compiled, so SH500 falls back to `builtAt` — and that
field is `null` on precisely the deployment the gate insists on.

`ShAgentBuildIdentity.resolveBuiltAt` reads the timestamp off
`CodeSource.getLocation()`. Under `java -jar` that is a Spring Boot nested URL
(`jar:file:…!/BOOT-INF/classes!/`), which `Paths.get` refuses; the `catch` treats it as
unreadable and returns `null`, honestly, leaving the fallback with nothing.
`spring-boot:run` would resolve it — `target/classes` is a directory — but
`requireLiveBuild` rejects a dev-mode instance for reporting no commit. Each shape
supplies exactly the field the other lacks, and the intersection is empty.

The consequence is not a wrong answer but a gate that is off during the only state
anyone runs it in: a working tree with edits in it. `SHIO_CONFORMANCE_ALLOW_STALE=1`
becomes the standing incantation, and an escape hatch used on every run is a check
deleted slowly — the shape SH579's ledger exists to refuse.

The fix is small and on the Java side: a Boot fat jar has its own `lastModifiedTime`,
reachable before the nested-URL conversion or from the attribute the repackage step
writes. And the assertion is the deliverable (SH527) — a case that resolves the identity
from a fat-jar `CodeSource` and requires a timestamp, so the null cannot return the next
time the packaging changes.

### IX.63 The tenant a pool thread does not have (SH612)

`ShAgentIncident` carries `@TenantId`, and `ShTenantContext` is a thread-local. Two
recorders write from a pool thread that never had one set: the webhook dispatcher inside
`dispatchAsync(...).whenComplete(...)`, and the Turing listener inside
`executor.execute(...)`. Both run past `AFTER_COMMIT`, so there is no request, no
session and no tenant — the store's `REQUIRES_NEW` write is stamped with the default
discriminator, `shio`.

SH22 did not miss this so much as never reach it. Until SH204 the row held a URL, a
message and a timestamp, and being filed under the wrong tenant made it invisible rather
than wrong. SH204 added a site id, so a misfiled row now carries an identifier belonging
to a tenant that cannot see it.

The consequence is quiet, and worth stating precisely. The row is visible only to the
default tenant, whose readable sites never include the foreign id, so it matches no
scoped read and resolves to no name in an unrestricted one. Nothing leaks — but the
operator whose site actually failed is the one person who cannot see the failure, and
the webhook that never arrived is exactly the incident a tenant is paying for.

The fix is to carry the tenant across the boundary the way `siteId` and `what` already
are: capture it on the committing thread, restore it around the write. What decides the
shape is whether any other `@TenantId` write happens off-thread — a question for a lint
rather than a reading.

### IX.64 The lints that read what is written about them (SH613)

`ShRenderCacheReachLintTest` scanned raw file text for `@Transactional`. SH206 removed
that annotation from `ShAgentVerifyService` and explained in the javadoc why — and the
build went red, because a sentence naming the annotation is indistinguishable from
declaring it. The lint had done this since it was written; nothing had previously needed
to write about the rule.

The measurement: **22 tests walk `src/main/java` and read source, and 17 of them do no
comment stripping.** SH206 fixed one. The other 16 are not all defects, and that is the
actual finding — some read comments **on purpose**, and the two shapes are
indistinguishable from outside. `ShSilentCatchInventoryTest` looks for exemption
comments, `ShAgentTransactionalOverloadLintTest` skips comment lines as it goes, and
`ShFolderSite`'s guard keys on a `ShFolderSite-exempt:` marker. Those are correct. A
lint that greps for a symbol and happens to hit a javadoc mention is not, and today
nothing separates them.

Two things worth having, in this order. A **shared stripper** — SH206 wrote a string-
and text-block-aware one as a private method, which is the second copy this repository
will want and the first it should not have. And a **declaration**: a scanning test
states whether it reads code, prose, or both, so the accidental case becomes visible
instead of waiting for somebody to describe a rule in the file it guards.

The cost of leaving it is not a red build. It is the quieter thing: a reader who hits
this once learns not to write the comment.

### IX.65 The count that cannot go down (SH615)

`ShReviewSession.pending` asks, for each of a run's audit entries, whether its post has
a `DRAFT` row. Its javadoc says what it means to answer — "a change whose post is gone,
or that is already published, is history rather than a decision" — and it cannot,
because `publish` writes a `PUBLISHED` row and **keeps** the draft as the editable copy.
Every post that has ever existed has one, forever.

So the number only grows. A curator who approves a run sees the page reload with the
same "3 to review", and Approve stays enabled on work already published. No error,
nothing logged: the page asserts something untrue about the thing it exists to resolve.

SH207 found it by needing the opposite property. A navigation badge must be safely
ignorable at zero, so it could not reuse this. `ShReviewService.pending()` asks whether
a draft has **no published twin**, or was **modified after** its twin was published, and
`ShReviewPendingTest` pins both numbers side by side so the disagreement is stated
rather than latent.

Two decisions, and the second is the real one. The definition is written and tested one
method away, so adopting it is small. But `pending` also counts **entries** rather than
posts — one page edited four times reports four — so fixing only the staleness leaves a
number that is fresh and still not a count of decisions. Both or neither: a half-fix
here reads as correct.

### IX.66 The moment the read surface cannot reach (SH619)

SH522 named four declarations to judge an artefact against. Three are settled: the
capture and the proposal are built, and the document turned out to be `apply --check`
under another name. The fourth — the same address at an earlier moment — is not built,
and the reason is worth recording because it is not effort.

`GET /api/v2/agent/read` is **DRAFT-preferred and takes no `state`**. SH545 added
`published` so a caller can tell whether a live row exists, which is the right field and
answers a different question: whether, not what. So the published row's *fields* are
unreachable from the agent surface, and a fact-level comparison of draft against live
cannot be assembled at all — not expensively, not at all.

The rendered published page is reachable (`/preview`, `/sites/**`), so a *pixel* answer
exists and `snapshot` gives it. What does not exist is the cheap one: which fields
changed, which is the question somebody asks before publishing and the one the SH14
dialog answers for exactly one post at a time, in a browser, to a human.

The work is therefore a read before it is a diff. Either `read?state=published` — small,
and it makes `state` mean what its own javadoc says it means — or a projection that
returns both rows where they differ, which is closer to what a caller wants and costs a
shape nobody has designed. Deciding that is the task; `shio diff --against published` is
what it unblocks.

### IX.67 The debt reader cannot tell an assertion from a mention (SH630)

Found by using it. SH356's ratchet shipped with a header explaining why it holds SH361's
outcome and why it deliberately does not assert a cross-file rule, naming three other
ids to say so. All three left the debt list on the next run. Nothing asserted them; a
file had said their numbers out loud.

The walk's own header already argues one half of this — a glob over the whole repository
would count an id in a source comment, so it is restricted to suite trees. That narrows
the population and does not address the mechanism: **inside** a walked file, a comment
and an assertion message are the same bytes to a regex.

The direction of the failure is what makes it worth fixing. It never invents debt, it
only *retires* it — silently, in a diff about something else, at the moment somebody is
writing carefully about why a task matters. The better the comment, the more debt it
pays. And the workaround this session used was to **stop naming the ids**, which
degrades the prose to protect the counter: the instrument now shapes what may be written
about the code it reads.

Two candidate repairs, and a count decides between them. Restrict recognition to an id
inside an assertion's message argument, or require an explicit marker the way the other
four ledgers require an owner. The first is a parse; the second is a convention nobody
applies by accident.

### IX.68 A red run nobody could reproduce, recorded rather than forgotten (SH635)

SH572 added `dangling-relation` and the first full suite after it came back **red**:
`ShBlueprintApplyTest.applyKeepsThePackagesPromise` expected `verify().errors() == 0`
and got **2**, and `applyingTwiceConverges` failed with it. The same code then ran green
four times — the class alone, and three full suites — and printing every finding at that
assertion showed an empty list.

One mechanism was found and fixed in the same commit, and it explains the **2** exactly:
the rule keyed its broken templates by post id and then walked `scan.posts()`, which
carries a DRAFT row and a PUBLISHED row sharing one id. A published template reported
one defect twice, and whether the site has a published row depends on what ran before it
— which is what an order-dependent red looks like.

What it does not explain is why any relation dangled in a blueprint at all: no
first-party package contains a `getRelation` literal, and the site is UUID-named so
nothing else writes into it. The fix is real and the diagnosis is incomplete, which is
why this is a task rather than a commit paragraph.

SH354 is why it is written down: a run that is red once and green after is the one
nobody can investigate later, because the next `clean` removes
`target/surefire-reports/`. What would close this is a way to re-run the suite in a
**recorded** order — surefire's `runOrder` with the seed printed — so an order-dependent
finding is reproducible on demand instead of waited for.

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

### XI.18 The fourth registry is the one that is published (SH604)

Measured at `0f4a906f`. The MCP server registers **11 tools** and **7 resources**.
`claude-plugin/README.md` states 10 and 4. Missing: `shio_remember`, and the
`shio://diagnostics`, `shio://ops` and `shio://blueprint/` resources.

The two it is wrong about are the two that cost the most: `remember` is where a
convention survives a session, `diagnostics` is what SH596 shipped precisely so a plugin
user could read a 5xx. Both landed within three commits of the README, and the README is
the artefact a new installation reads first.

So SH599's three registries are four, and the fourth is the published one — the only one
whose reader cannot check it against the source.

The rule is not "regenerate the list". It is that **naming a tool to teach a rule is
teaching, and enumerating tools is a copy**. The AGENTS fragment naming `shio_apply`
while stating what apply guarantees is the first kind and should stay. README lines
28–29 are an inventory of a surface the reader can ask the instance for, and every one
of those is a copy with a half-life.

Two moves, in order: assert the enumeration equals the server's, so the build fails when
a tool ships; then delete the enumeration in favour of routing to `shio://manifest`, so
there is nothing left to assert. The leak guard added for internal ids is the same shape
and the same file — one more walk over the published artefacts.

### XI.19 The clean room, and what is actually in the box (SH605)

Every measurement this project has was taken with the source tree on disk. The reader
had `agents.md`, `docs/agents/**`, `docs/specs/**` and a repo-local skill, and used them
without noticing — that is what using them looks like. A stranger who runs `npm i -g`
and adds the plugin has **none** of it, and no run has ever been made under that
constraint.

So the question "is Shio discoverable" has never actually been asked. What was measured
is "is Shio discoverable to a reader holding its repository".

The clean room is mechanical and cheap: build the publishable artefacts and nothing else
— the CLI tarball and `claude-plugin/` — install them into an empty directory, and run
SH598's reachability walk with that directory as the only filesystem. A step that needs
a file which is not in the box fails naming the file. No agent, no tokens, no judgement.

It is the enforcement half of SH603. SH603 says *follow only what the instance and the
plugin say*; a reader with the repo mounted cannot honour that and cannot tell that it
did not, because the cheapest read is the one you do not remember making. One of the two
is a promise and this one is the guard.

Expect it to be red on the first run, and expect the missing files to be interesting:
what leaves the box is a decision nobody has made deliberately.

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

### XV.36 The other half of the label, and the migration inside it (SH573)

SH559 fixed the reader's problem: the preview banner now states `verbatim`, `authorable`
or `mixed`, computed from the page's own fields, so nobody has to infer a mode from a
name. It deliberately did not fix the name, and this is that half.

`convert.mjs` still calls the one generated layout `<site> (fidelity)`. That was
accurate when one layout served one mode. SH464 made a single layout serve all three, so
the word has been decoration since — decoration that looks like a fact, which is why
SH559 had to *quote* the title rather than print it. A quoted wrong name is a smaller
defect, not a closed one.

The reason it was left is the whole content of this task. The title is the key in
`site.postTypeLayout`: `convert` writes the binding and the post's `TITLE` from one
constant, so a new conversion is self-consistent the moment that constant changes. A
site converted *before* is not — its stored binding names the old title, and re-running
`convert` rewrites both, which is a write nobody asked for on a site somebody may have
edited since.

So the decision is not what to call it. It is whether the rename ships as a constant
change only new conversions see, leaving old sites bound to a name the code no longer
emits, or as something that carries the existing ones across. The second needs a
statement about what `convert` may rewrite on a site that already exists, which the
product has not made anywhere else.

### XV.38 The destination is site configuration, because a template is content (SH632)

2018's `ShFormConfiguration` carried `folder` — a **template** setting. That is the one
part of the 2018 answer SH364 does not adopt, and the reason is a sentence: a folder
chosen in a template is a folder an attacker chooses, because a template is content and
content is what a compromised author edits.

So the target folder and the eligible post-types are configuration **of the site**,
reachable by an operator and not by a page. `/api/v2/site-webhook` is the precedent
worth following — a table keyed by site, its own API, no column added to `ShSite` — and
it is the precedent for the same reason: per-site delivery configuration that an
operator owns.

This is also what keeps the renderer honest. `{{#form type="Contact"}}` names a *type*
and nothing else; it does not know an address and must not learn one, which
`ShRenderFormTest.theHelperNamesNoDestination` now holds. The page says what the form is
about; the site says where it goes; neither can answer the other's question.

The third of SH364's questions resolves here too. **What is a submission worth
keeping?** It is a post, so the trash, the export, the ACL and the agent surface already
apply and it needs no retention policy of its own. Its owner is the **token** rather
than a person — an anonymous submission has no user to attribute, and naming the token
is both true and the thing an operator needs when one form starts producing rubbish.

### XV.40 The scope is the control, because the token is public by construction

SH364 settled the door: submissions are **token-scoped and never anonymous**. The token
sits in a page a stranger can read, so secrecy is not what protects anything — the
**scope** is, and that makes the scope the whole security design rather than a field on
it.

A `FORM` scope names one site and may do exactly one thing: create a `DRAFT` post of an
eligible type in that site's configured folder. It cannot publish, cannot read, cannot
move, cannot touch another site.

**Eligibility excludes every system post-type, and that is not tidiness.** `PageLayout`,
`Region` and `Theme` are Handlebars, compiled and executed by the renderer. A form that
could create one is not a spam problem, it is a stranger writing a template this
instance runs — the whole endpoint's severity turns on this one predicate, which is why
it belongs in the scope rather than in a check the endpoint remembers to call.

So the answer to *what post-type may be submitted* is an allow-list on the site,
intersected with "not a system type" — an allow-list alone would still let an operator
name `Theme` by mistake, and that is not a mistake worth leaving available.

`ShApiTokenScope` already exists and `ShApiTokenFilter` already maps a scope to a role,
so this composes rather than forks (P10). What is new is a scope whose authority is
bounded by *data* — a site, a folder, a type list — where the existing ones are bounded
by verb.

### XV.41 The endpoint, once nothing about it is still undecided (SH633)

With the scope bounded (§XV.40) and the destination owned by the site (§XV.38), what is
left is ordinary. `POST` one document, get a `DRAFT` post back or a refusal.

Four properties, none of them novel here, which is the argument for this rather than a
submissions subsystem:

- **`DRAFT` only, always.** SH7's review queue is the moderation step, so a submission is visible to
  a curator and invisible to a visitor, and nothing new decides what "approved" means.
- **The per-token bucket the CDA already has** (`shio.cda.rate-limit`). A form token is an API
  token, so it is already limited; what this needs is a *tight* default, because the caller is the
  internet rather than one integrator.
- **A honeypot field** — the cheapest spam control that costs an author nothing and a visitor
  nothing: rendered hidden, and a submission must leave it empty.
- **Classified `AUTHENTICATED` in `ShAnonymousRouteAccessTest`.** Not a formality here: this is the
  one endpoint whose whole purpose is to be reachable from a public page, so it is where a future
  reader is likeliest to reason that it "should" be allow-listed. SH363's instrument answers that
  with a build failure instead of a discussion.

The teaching refusals matter more than usual because the caller is a browser nobody
controls: a non-eligible type, a full bucket and a tripped honeypot are three different
answers, and only the first two should say so.

### XV.42 The scan that is left, and the reason it was not removed with the rest (SH634)

SH419 took the listing from **213 entity rows loaded to render 10** down to **2 per
row**: the fold, the filters and the sort run over sort keys, and only the survivors are
read whole. It did not remove the scan. The keys query is still `where folder = ? and
state in (?)`, so a 5000-post archive reads 5000 tuples to draw ten — five scalar
columns and no JSON, but O(folder) all the same.

Two of SH384's three objections are now answered rather than avoided. The **fold** runs
over keys before anything loads; the **exact total** is the size of the folded, filtered
key list, free and still exact.

The third is unchanged and a fourth is new. The **`field=` filter** is a predicate over
the post's JSON, so that path still reads whole rows — declared, not quietly excepted.
And **the ORDER BY cannot be pushed down portably**: `date` is nullable and the
comparator is `nullsLast`, so the clause needs explicit null placement. MariaDB has no
`NULLS LAST`, and it and PostgreSQL disagree about where a `DESC` null falls by default
— a listing whose page boundaries differ by engine moves SH101's digest for a change
nobody made, on one deployment and not another.

So what is left is not "add a `Pageable`". It is deciding how the order is expressed so
every engine produces one sequence, and that decision wants the cross-database ITs
actually running.

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

### XVII.70 The generator is on the wrong path (SH518)

`shio apply --blueprint marketing-site --site X` writes what it promises: 8 post-types,
4 folders, 13 posts — three pages composed of ten sections, every field authorable. Then
`/preview/X/` answers **400, binds no layout for post-type 'Page'**, because the
blueprint declares no `PageLayout`, no `Theme` and no `postTypeLayout`. None of the six
blueprints does.

The generator exists and is in the wrong place. `convert` writes `Theme (captured)`,
`Theme (Shio)` and a `PageLayout` — SH464, SH508 and SH509 — but only on the replication
path, so a site that starts from a blueprint cannot be looked at inside Shio. Getting
the clothing-shop site to render meant harvesting those two posts from a throwaway
conversion, retitling them and binding the site by hand: four ops that the blueprint
should have carried.

That is backwards for the product. Replication is the *unusual* case; a blueprint is how
something new starts, and it is the demo a stranger asks for. So the path that was never
designed to render one can, and the path designed to start one cannot.

The fix is to move the generator rather than copy it into six blueprints. `apply
--blueprint` already provisions post-types and knows the site, so it can write the
layout and the Shio theme the same way `convert` does — one code path, and a blueprint
stays a declaration of content.

The assertion is the same either way: apply a blueprint to a fresh site and `/preview/`
answers 200 with `data-shio-prop` on it.

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

### XX.1 The console is the least documented part of the product, and it is where the human is (SH625)

The content console: the browser and its paged listing, the post form built from the
post-type, copy/move with both ends authorized and free names, "select everything", and
the trash. All shipped, all reachable from the first screen a curator sees, and none of
it written down anywhere a reader can reach.

`COVERAGE.md` scores it 🔴 with the note that SH485 covers *the rails* — the safety
model, the review queue, the publish gate — and not the console. That is an accurate
split and it is the problem: the rails are the part an agent drives and the console is
the part a person drives, so the documented half is the half that needs documentation
least.

"Select everything" is the sharpest instance. It is a real capability with a real blast
radius, its semantics are not guessable from the checkbox, and a curator's only way to
find out what it selects is to use it. That is P7 inverted by omission rather than by
design.

Scope note: this is a page, not a tour. What earns space is what is not guessable — what
copy/move does about name collisions and about permissions at both ends, what the trash
retains and for how long, and which listing operations are scoped to the page versus the
whole result set.

### XX.2 A page describing a shape the product left is worse than no page (SH623)

`administration-guide` describes the AngularJS screens the React console replaced.
`COVERAGE.md` gives that verdict its own symbol — ⚠️, which `docs-turing` does not need
— because "has a page" and "has a correct page" are different questions for every Shio
page written before 2026.3.

The distinction decides how it is fixed. A 🔴 is an addition; a ⚠️ is a **rewrite**, and
a new section under stale prose makes the page contradict itself. So the stale text goes
first. Read through this one page today: the dashboard, the Media Library, the
static-file manager, the webhooks UI and the `config/` pages (auth providers, exchange
providers, email/SMTP, search) — a reader following it is hunting for screens that no
longer exist, and concluding the product is broken rather than the page.

One question to settle before writing, because it changes the shape rather than the
wording: whether this stays one page covering all of them, or whether the Media Library
and the static-file manager move under the human's hub. They are the two a **curator**
uses rather than an operator, and the matrix is grouped by who does the job.

### XX.3 Five lifecycle surfaces a curator cannot discover (SH624)

SH13 short-lived preview tokens and console preview, SH14 draft-versus-published diff
and restore, SH15 scheduled publish and unpublish, SH51 audit history and the Activity
viewer, SH55 trash and restore. Five shipped surfaces, one 🔴 row, and `content-modeling
§ Publishing` covers none of them.

They belong together because they answer one question — *what happened to this page, and
can I undo it* — and because a curator who does not know they exist does the work the
hard way: sends a screenshot instead of a preview link, re-types instead of restoring,
sits at a keyboard at the hour a post should go live.

The undocumented half of a safety feature is the expensive one. Draft-default and the
publish gate exist so a mistake is recoverable, and a person who does not know restore
exists experiences a mistake as unrecoverable anyway. The feature was paid for and the
reassurance was not delivered.

Preview tokens have a second reason to be written down: they are short-lived by design,
and a curator who does not know that reads an expired link as a broken product.

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
