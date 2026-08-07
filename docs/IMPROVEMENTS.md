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

### VIII.32 Discovery has fixes and no grep (SH598)

SH338 states the rule: when a review finds a design law broken, the instrument that
finds every *other* break lands before the fixes. P1 obeyed it —
`ShAgentP1ConformanceTest` classifies every manifest `features` key and holds the
unreachable ones as an asserted list, naming four no task had.

P2 did the opposite. Three breaks were found by hand, one per round, each fixed
alone: a capability named by four documents and no command (SH587), an onboarding
command that never read the manifest (SH595), a feature reachable by REST, CLI and
console and by neither MCP list (SH596). So the honest count of P2 breaks today is
*three that somebody happened to notice*.

**The measure is computable without an agent.** Discoverability, defined as *named
in a response already received*, is graph reachability: seed the known set with the
entry point's response, expand by every name it contains, repeat. A capability is
reachable at depth N or it is not reachable at all. No LLM, no tokens, no
judgement — a sibling of `ShAgentNaiveClientBenchmarkTest` (SH340), which is
already naive about *keys* and not about *what exists*.

Assert it as P1's asserted list rather than a ceiling: the run prints how many are
reachable and how many held, so closing one cannot quietly open another. SH597 lands
first — the walk says *which* are unreachable, the benchmark says *what that costs*.

### VIII.33 One capability, three lists, no join (SH599)

A capability in Shio is declared in three unrelated places: a boolean in the manifest's
`features`, a path in its `endpoints`, and a tool or resource in the MCP listing.
Nothing joins them. SH596 was one instance — `diagnostics` was `true` in the first,
present in the second, and in neither MCP list — and it was found because a run needed
it, not because anything checked.

This is also where P1's cost shows up. Every capability lands in four places by law (MCP
→ files/CLI → REST → console), which is a 4x maintenance multiplier on a surface that
only grows. The laws create the surface; nothing yet bounds it. Four ledgers (SH594)
keep what is knowingly wrong visible, but they are human-in-the-loop devices and they do
not catch a capability that was simply never added to one list.

The fix is not a fourth list. One of the three becomes the source — `features` is the
candidate, since it is the one an agent reads first and the one already asserted by
`ShAgentP1ConformanceTest` — and the others are derived from it or asserted equal to it.
A capability then cannot exist in the manifest and be absent from MCP, because the
absence is a build failure rather than a discovery somebody makes in round thirteen.

Out of scope: making the derivation clever. A test that fails naming the missing entry
is enough, and it is the part that cannot be argued.

### VIII.34 The projection that answers nothing, with a 200 (SH600)

`?include=identity` is a reasonable-looking way to ask the manifest a narrow question.
It returns 200. It also removes every one of the 57 `features`, the `limits` and
`startHere` — that is, everything P2 exists to deliver.

A procedure written against that call teaches it to every run that follows. That is what
happened here: a skill's first line carried `?include=identity`, and for twelve rounds
the manifest was called and the capability list was never seen. The cost was proposing
to build two shipped features and diagnosing a 5xx from a server log while `diagnostics:
true` sat two lines away in a response nobody had asked for.

P6 says errors are instructions, and it is scoped to the 4xx. The failure here has no
error. A caller who narrows a *discovery* surface is, by construction, a caller who does
not yet know what is there, so a silent projection is the one place a 200 misleads worse
than a 404: a 404 would have been read.

The rule to add: a discovery response that has been narrowed states what the narrowing
removed — a count and the key names, not the content. `features: 57 omitted
(include=identity)` is a handful of tokens and it converts a silent projection into a
pointer. It costs nothing on the full response, because there is nothing to report.

Small, and it is the cheapest of the P2 tasks by a wide margin.

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

### IX.34 Both sides are held and never compared (SH522)

Shio holds both sides of every comparison it does not make. A capture is a **declaration
of the source**: six pages, eleven links, one image, this text. A proposal is a
declaration of **intent**: these fields from these blocks. A desired-state document is a
declaration of the **write**. And the artefact each produces is compared with none of
them.

So the question I ask every round — "is this what it was supposed to be?" — has no
answer in the product, and I answer it by hand. That hand has been wrong twice: zero
broken references on a page with a missing logo, and 106 editable fields on a page that
had lost most of its content. Both times the declaration was on disk and the artefact
was on the wire, and the join was forty lines of Python I wrote from memory.

The verb is `shio diff`, and its argument is which declaration to judge against — the
capture, the proposal, the document, or the same address at an earlier moment. What it
returns is not pixels: fields present, absent and changed; references that resolved and
that did not; counts the source recorded against counts the page shows.

**Every artefact carries the identity of what declared it**, which is what makes this cheap: the inventory
names the capture, the proposal names the inventory, the document names the proposal. Nothing has to be
guessed or re-fetched — the chain is already written down and nobody has ever walked it.

### IX.37 The fixture as a catalogue of what breaks a replica (SH528)

Every defect this work found came from a real site, and none of them can be reproduced
without one. The fixture is 134 lines: a page, a stylesheet, a webfont behind
`../fonts/`, an image, a favicon. Real sites broke the replica with traits it does not
have.

The list is written already, one per task: a query string on an asset path, a `./../../`
reference served four segments deeper than its source (SH512), a fixed overlay whose
text became three sections' `intro` (SH505), a `&` escaped twice (SH476), a zero-byte
file the upload refused (SH514), a `<canvas>` a script created and `--render` baked dead
(SH517), a page under a locale prefix with no home at `/`, and a second page so a link
can be wrong.

Each is a few lines of the fixture. Together they are the deliverable: **a catalogue of
what a real site does that breaks a replica**, and the first artefact in this work that
keeps a finding without needing the network.

That is what makes an assertion deterministic rather than merely automated. A real site
is how a defect is *found* — `viglet.org` has a consent banner and a React canvas nobody
planned. It is a bad way to keep one: the site changes, the network fails, and a red run
means nothing until somebody checks which.

The order follows SH527's list — a trait exists because a defect needed it, never
because a real site happened to have it.

### IX.38 The debt already standing (SH529)

SH527 states the rule for the next defect and SH528 gives the fixture the traits to
express one. This is the debt already standing: **the defects this work found and
closed, which no assertion holds.**

The count is the first deliverable and it is computable — the ledger names what shipped,
the suite names what it asserts, and the difference is a list. Around forty tasks came
out of the replication rounds and the suite carries five cases, so the list is most of
them.

Not all of them earn one, and saying which do is the work. A one-line fix to a message
does not; a defect that **shipped twice** does, and there are six of those. A defect
nobody could see without a browser does, because that is the boundary the suite exists
to cross. The output is a decision per task, not a test per task.

Order by whether a defect could return. The six half-corrections could — each was a real
fix whose second half was written later, so the code around them is the code most likely
to be revised. SH445's abort is the sharpest: fixed three times, and the third fix has
no assertion on the exit code of a real remote failure either.

It is also the honest end of my part in this. I have been the verification for fifteen
rounds and got it wrong twice; a finding that becomes an assertion no longer depends on
me looking.

### IX.39 A source trait is a property of a site, not of a page (SH560)

SH528 grows the one fixture, and that is right for every trait it lists but one. A page
under a locale prefix with no home at `/` is not a few more lines: a server that answers
`/` cannot also be a source that has none there. The same holds for three traits the
runs found and no task has filed — a source client-rendered end to end, a source whose
pages differ only by a query string (SH557: 91 of 107), and one whose whole style is a
megabyte of external CSS (SH555, SH556).

So the fixture becomes a corpus: `fixtures/source-sites/<archetype>/`, each archetype a
directory of bytes plus an `expectations.json` naming the pages, the assets, the mode and
**the ids it exists to catch**. One parametrised suite iterates it; seven copies of the
chain drift within a week.

The root rather than `cli/`, because three consumers read it: the fast tier in
`cli/test/` with an injected fetch, the live tier in `cli/conformance/` over a real
socket, and `shio-app`, which needs a conversion's output to prove a render.
`ShConvertedLayoutRenderTest` already reads `../cli/src/convert.mjs`, so a
cross-language fixture is a path this repository has. `shio-examples/` is the wrong
home: those build as published artefacts, and a fixture has to be free to change.

None of this replaces the run against a real site. That is how a trait is found. The
corpus is how one is kept, and the archetype needing a browser refuses with the install
command rather than skipping.

### IX.42 The suite that has an instance is the one that does not speak the transport (SH561)

`replication.conformance.mjs` was written to remove substitutions, and it removed three:
the served source, the running instance, the registry. It kept a fourth nobody named. Of
its four scenarios, one spawns `src/index.mjs` as a process — SH456, and the comment
says why: an exit code is something a module call structurally cannot see. The other
three `import * as convert from "../src/commands/convert.mjs"`.

Claude Code uses neither. It speaks `shio mcp` over stdio JSON-RPC, and the layer import
removes is the whole of it: the tool's declared input schema, the name an agent must
guess right, the JSON-RPC framing, the `tools/list` payload P3 holds a budget on, and
the shape of a refusal an agent has to read to recover. A tool renamed, a required
property added, a response grown past its budget — all four pass a run that imports the
module the tool wraps.

So the corpus run gets a second driver over the same archetypes: the same chain, issued
as `tools/call` against a spawned `shio mcp`, asserting the result an agent receives
rather than the value the function returned. The module driver stays — it is faster and
it localises a failure, which is what it is for. What it cannot be is evidence about the
surface the product is named for.

P1 orders MCP first and the manifest claims thirteen tools. `ShAgentP1ConformanceTest`
asks whether the HTTP surface can reach a feature. Nothing yet asks whether the
transport can.

### IX.43 A render defect in Java is found by a hand pass on a real site (SH562)

The eighth run found two defects in the same place, and the place is Java. SH555 is one
brace: a captured theme read inside a `getRelation` block is escaped, so a megabyte of
CSS reaches the browser with 378 entities and `verify` reports ten broken references.
SH556 is two vocabularies: the generated theme styles thirteen role classes and the
section templates draw sixteen, so every authorable page is unstyled including the one
whose section renders.

Neither is reachable from `cli/`. Both are `ShPageRenderService` composing a layout over
content an instance already holds, which is exactly what `ShPageRenderTest` covers — and
it is green, because it builds its fixture with an `EntityManager` and writes the layout
the test author had in mind. A conversion writes a different one: a generated layout, a
captured theme, a page whose body is a list of section posts. That shape exists nowhere
on the classpath.

So `shio-app` reads the corpus too. One archetype's converted output — post-types,
layout, theme, sections, assets — checked in as the desired-state document `apply`
takes, applied in a Spring test, rendered, and asserted on the served bytes: no entity
in a `url()`, every class the templates draw defined by the theme, every reference
resolving.

The precedent is in the same package. `ShConvertedLayoutRenderTest` already reaches
across the language boundary to read `../cli/src/convert.mjs`, for this reason: the two
sides of this seam drift, and the cheap place to notice is `clean test`.

### IX.44 The redirect the crawler followed and the inventory forgot (SH563)

The first run of SH560's corpus found this, which is the argument that corpus was filed
on.

`visit` follows one hop by hand, on purpose: the comment says the redirect should be
*visible* in the inventory rather than erased by the fetch layer, and it returns `{
...result, redirectedTo: pathOf(target) }`. `buildInventory` then builds the page row
from `path`, `title`, `description`, `lang`, `status`, `depth`, `links`, `assets`,
`forms`, `textChars` and three conditionals — and `redirectedTo` is in none of them. Its
own JSDoc lists the field as part of a `visited` entry, so it is documented and dropped
in one file.

This is what SH467 is about, one phase earlier. `/docs/guia` 301s to `/docs/guia/` and
both answer; the capture records **two pages with the same content** and no statement
that one is the other's redirect. A conversion writes two Pages from that, so the
replica serves the body twice and redirects neither — the source's canonical choice is
gone, and the 301 every external link to the old form needs cannot be rebuilt, because
nothing downstream knows there was one.

The status is not the workaround. `entry.status` is the status of the response the crawl
ended on, which is the 200: by the time the row is built the 301 is resolved, so a
reader cannot tell a redirected path from a served one.

Cheap, and its assertion exists and is red — group E of `source-corpus.test.mjs` asserts
the field for this reason (SH527: the assertion that would have caught it).

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

### IX.58 Four ledgers of what is knowingly wrong (SH594)

The device is sound and it has been invented four times:

| where | what it holds | since |
|---|---|---|
| `shio/verify-baseline.json` | accepted `verify` findings, per project | SH142 |
| `cli/test/assertion-debt.test.mjs` `DEBT` | shipped ids carrying no assertion | SH527 |
| `fixtures/source-sites/open-findings.json` | corpus assertions red at HEAD | SH560 |
| `red-suites.json` | a suite allowed to be red, with its reason | SH579 |

Each is a **named list rather than a ceiling**, for the same stated reason every time: a
ceiling lets one task pay the debt and the next spend it. Each is checked in both
directions, so an entry that goes green fails the run. Each was the right call on its
own.

Together they are four formats, four readers and four places to look, and nothing joins
them. This session met the cost directly: `open-findings.json` held a red case owned by
SH555 **after SH555 had shipped**, because the fix landed on the render side and the
CLI's generated layout — which the corpus reads — still emits `{{CSS}}`. Only a check
written this round noticed, and it had to read `docs/ROADMAP.md` to do it.

This is SH583's shape one level up: five places decided what a trailing slash meant and
they agreed only by accident. Four ledgers agree about what is knowingly broken only
while nobody's debt is anybody else's. The question is not whether to merge the files —
their scopes are genuinely different — but whether one reader can answer *what is this
repository knowingly carrying, and which of it has an owner who has already gone*.

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

### XII.15 A site that was never meant to be kept (SH591)

Twelve replication rounds left thirteen sites on one instance — `R6Fid`, `R6Aut`,
`R9Aut`, `R9Loc`, `R10Ssr`, `R11`, `R12` and six older ones. Every one was made to
answer a question that has since been answered. Removing them means thirteen
`site.delete` ops, each answering 428 and each needing its own confirm token, which is
SH108 working exactly as designed and applied to the one case where the surprise it
guards against cannot happen.

The distinction the model is missing is **who declared the site disposable, and when**.
P7 is that a human is never surprised; a site the agent marked as scratch at the moment
it created it, in a document the human could read, is not a surprise — the declaration
happened before there was anything to lose.

So `convert --create-site --scratch` (and `apply --create-site --scratch`) records the
flag on the site, and `shio site rm --scratch` sweeps every site carrying it, in one
confirmation rather than thirteen. A site without the flag is untouched by that command
at any count, which is what keeps it from becoming a way to delete a real site quickly.

The second half is worth as much as the first: a scratch site should be **visible as
such**. `shio context` and the console listing that show thirteen sites today show no
difference between the two the human curates and the eleven an agent left behind, and
the human is the one who has to look at that list.

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

### XIII.20 The cost of finding out (SH597)

Block M measures responses. `tools/list` has a 2375-token cap checked every build, the
agent responses have `ShAgentResponseBudgetTest`, and SH394 asks the same of `shio
--help`. Every one of those scores a **surface**. P2 does not claim a surface is small —
it claims *one call replaces a session*, and that is a property of a **path**: cold
start to the first correct action.

Nothing counts it, which is why twelve replication rounds could propose building a
shipped capability twice, and diagnose a 5xx by grepping a server log with `diagnostics:
true` sitting in the manifest, without a number moving. The manifest was complete the
whole time. The run that found a feature in one call and the run that never found it
scored identically, because neither was scored.

The metric: from an empty context, the calls and tokens spent before the first action
that is *correct* — not the first action. Correctness needs a fixture that declares it,
so the benchmark is a small set of intents with a known right first move (diagnostics
after a 5xx, the notes before re-deriving a convention, the blueprint that already does
the job) and the cost of reaching each.

It lands before the rest of the discovery work, per the project's own rule: a cost that
is argued rather than measured is the same defect as a premise carried in a comment.
Without it, every P2 task after it is an opinion with a commit attached.

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

### XV.35 The reference the graph can now see, and still cannot check (SH572)

SH558 taught `ShTemplateGraph` to read the literal address in a `getRelation` block,
which closed `unused-theme`. It also made the *opposite* gap legible for the first time,
because the graph now resolves these references and can see when one resolves to
nothing.

SH297 already reports a layout whose `THEME` field names a theme the site has not got —
an error, both ways, because a page rendered through it is missing its stylesheet. A
relation literal has exactly the same consequence and no rule at all: `getRelation`
renders its else branch when nothing resolves, and the block deliberately does not mark
the absence (SH247, and it is right — a dangling *field* reference is SH154, with an
address and a fix).

The asymmetry is the defect. A field reference is checked and a template-literal
reference is not, so `/theme/capturd` — one character — produces a page that renders
200, carries no captured CSS, and reports clean. SH549 is how close this sits: that
layout spells its address as a constant precisely because a literal argument had never
been proved to resolve at all, and `ShConvertedLayoutRenderTest` exists to prove that
one instance. Every other layout in the product gets nothing.

The rule reads the same literals SH558 extracts and reports the ones that resolve to no
post in the site, at whatever severity SH297 uses for its field twin.

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

### XVII.88 Recrawling a site to test something that is not the crawl (SH592)

`shio clone` over `vilt-group.com` with `--render`: 198 pages, 403 assets, 52.7 MB,
about ten minutes. It is by far the most expensive step in the chain and the only one
whose output is immutable — everything after it reads the inventory off disk and runs in
seconds.

Twice in this work that asymmetry cost a full recapture for a reason that had nothing to
do with the source. SH578 changed the inventory's asset shape and `diff` refused every
capture written before it — with an excellent error naming the version and the remedy,
which is the right refusal and does not make the remedy cheaper. The other time the
source had not changed at all; a downstream one-line fix needed a capture the new reader
could parse.

The information to do better is already in the file. Every asset row carries a `sha256`,
every page a `status` and its markup hash. `shio clone --refresh` can re-fetch
conditionally and rewrite only what moved, and an inventory one version behind can be
**migrated** rather than refused where the missing field is derivable — SH578's split of
`assets` into `{own, style}` is derivable from the stored markup, which the capture also
keeps.

The rule worth stating alongside it: a version bump on the inventory is a migration or a
refusal, and a refusal is honest only when the new field genuinely cannot be computed
from what is on disk. Otherwise the cost of a reader's change is paid in somebody else's
ten minutes.

## XVIII End-user documentation for 2026.3 (Block R)

The published documentation at `docs-shio` describes the 2018 product. Measured against
the working tree: the words MCP, agent, Universal Editor, Handlebars, blueprint, tenant
and `post-unified` appear in **zero** of its 19 pages, while Nashorn and `shObject`
appear across six, and the landing page pins `v2026.1` and sells server-side JavaScript
rendering plus Hazelcast as the product's four capabilities. Blocks H through Q shipped
an agent-native CMS and none of it is published, so a reader who arrives at
docs.viglet.org/shio is told the opposite of what the ten laws in `agents.md` say the
product is.

This block writes that documentation in the shape `docs-turing` already uses: narrative
hubs with an `.mdx` landing page each, per-page front matter, pre-rendered Mermaid SVGs
from `diagrams/`, the brand CSS variables, no em dashes, and a `COVERAGE.md` matrix that
maps every shipped block to a page or to an explicit "deliberately internal" row. The
matrix comes first (SH338: the instrument precedes the fixes), because an undocumented
subsystem is exactly the defect class no page-by-page pass finds.

`docs-shio` lives in a different git repository
(`D:\Git\viglet\vigletdocs\vigletdocs.github.io`), so each task lands two commits: the
Shio-repo ship, and the vigletdocs page edit.

### XVIII.21 The examples nobody runs (§XVIII.21) (SH499)

The failure this whole block repairs was not that anybody wrote the docs badly. It is
that the pages were written once, the product moved, and nothing ever compared the two.
A coverage matrix catches a subsystem with no page; it does not catch a page whose
example stopped working.

The instrument: extract the copyable invocations from `docs-shio` (fenced `bash`, `http`
and `json` blocks carrying a `shio` verb, an `/api/v2/**` path or a `POST /mcp` body)
and run them against a live instance seeded the way the conformance harness seeds one,
asserting the shape of what comes back rather than the bytes. A renamed flag, a moved
endpoint or a changed response key then fails a run instead of misleading a reader.

Two questions to answer before building it, which is why this is an idea and not a
design. Where does it live: the docs repo has no test harness, and Shio's
`cli/conformance/` already knows how to drive an instance. And how much is worth
extracting: a first count of the executable blocks decides whether this is a gate or a
periodic check, and that count is the first deliverable either way.

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
