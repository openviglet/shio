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
(H → K → I → J → L → M → N), not the block letters — §IX is Block K, §X is
Block I, and so on. Each section header names its block.

**Cross-references** use relative paths from `docs/`; linked code may have moved —
verify against `git ls-files` before acting.

## Table of contents

- [0. Strategy — the agent-native reorientation](#0-strategy--the-agent-native-reorientation)
- **Part 1 — the reorientation (active)**
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
textual; SH102's PNG is scoped for a human. **SH187** revisits that for the other reader
→ [CHANGELOG.md](CHANGELOG.md) Block P.

**The follow-ups taught a second lesson, about the block's own instruments.** Four of
eleven were defects *in* a guard, not in the product: a lint that read prose as code, a
debt reader that could not tell a mention from an assertion, a gate whose fallback was
null in the deployment it demands, and a "flake" that was a scoping bug. A perception
loop that cannot perceive itself is the failure this block should expect next.

## X. Content as files (Block I)

> The largest cycle reducer, and the least obvious. Authoring over HTTP is the
> wrong medium for an agent; a git-diffable text tree is the right one — the agent
> is measurably better and cheaper with `Edit`/`Write`/`Grep` than with
> read-modify-write over an API.

> ✅ **SH86, SH87, SH88, SH89 and SH90 shipped** → [CHANGELOG.md](CHANGELOG.md) Block I.
> What follows is the design rationale for what the projection **still owes**.

## XI. Blueprints + Claude Code plugin (Block J)

*"Skip steps with things that are already done"* only works if the ready-made thing is
**appliable**, not readable. A prose recipe an agent must read and reimplement saves
nothing — it just moves the work. **Absorbs the old SH23 marketplace epic** (SH27–SH31).

The format, the verb, the first-party set, the section vocabulary, the Claude Code
plugin, the five mismatches that shipping the two halves exposed, and the whole
marketplace — catalogue, install, published seed, CLI, MCP tool and console — are
shipped; see [CHANGELOG.md](CHANGELOG.md) Block J. What remains is the role the two
writes still want (§XI.27).

### XI.27 The marketplace writes want the role the reload already has (SH682)

`POST /api/v2/marketplace/{id}/install` and `DELETE /api/v2/marketplace/{id}` are
`authenticated()` — the blanket rule. They should be **`hasRole("ADMIN")`**, and the
argument is `BLUEPRINT_RELOAD_PATH`: merely re-reading the operator's package directory
is already admin-gated, because it changes what every later apply can do. Installing is
that act plus a download, so it cannot honestly be gated more weakly than the thing it
contains.

It was written that way in SH677 and taken out, because the role is unreachable for a
locally authenticated user (SH681) — the reload answers 403 to the seeded admin. Gating
a capability on a role nothing grants does not make it safer, it makes it dead, and a
dead gate teaches the next reader that the gate is decorative.

So this is a separate line rather than a `TODO` in the security config: the change is
one expression, its precondition is somebody else's task, and the pair is only honest if
both are visible in the same backlog.

What lands with it is the assertion SH677 could not write for the same reason: a caller
authenticated but not an administrator is refused the install, and an administrator is
not. That test would have caught SH681 four tasks earlier, so it belongs here even
though the code change is one line.

Check in the same pass that the console's own call still works — it calls this with a
session, and a role gate tested only against the CLI is a second way to ship something
dead.

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

### XIII.24 A ceiling that outlives its finding (SH683)

Measured clean at `5737a217`, 1731 tests green. Every one of the six intents is now
answered at rung 1, the context pack:

    resume-a-session    2 / 1015      write-without-ids   2 /  987
    recall-conventions  1 /  935      prove-the-work      2 /  955
    reuse-a-blueprint   2 / 1434      diagnose-5xx        2 / 1037

Total **6363** against a declared 11700. Two rows moved most: `diagnose-5xx` and
`resume-a-session` were 3 calls and ~3300 tokens each because reaching them meant the
~2400-token endpoint index. SH601 put both in `startHere` and they now cost a third of
that.

Nothing failed, because a benchmark asserts `isLessThanOrEqualTo`. That is right for a
guard and wrong as a record: the ceiling still reads 3650, so the surface it protects
could grow 3.5x before anything objected, and the *win* — the whole point of SH601 — is
not written anywhere a build can see.

Worse, the paragraph above those two lines still says `startHere` does not name them and
calls the numbers *deliberately not good*. It describes the state SH601 removed. A
reader lowering a ceiling would read a rationale arguing against doing it.

So: re-measure the six, lower the ceilings onto the new figures with the headroom rule
this file already states, and delete the prose that argues from the old ones. Then ask
the harder question — whether a benchmark that only fails upward can record an
improvement at all, or whether a floor is what a *finding* needs.

## XIV. Realignment of what already shipped (Block N) — what building it opened

> The reorientation was mostly additive, but it did invalidate some existing choices.
> Block N spent them → [CHANGELOG.md](CHANGELOG.md) Block N, and so are the tasks that
> building it opened (SH163–SH167, SH233, SH234) and the ones that finishing it turned
> up (SH235–SH237, SH239, SH240, SH668). Nothing is left open here.
> Concept spec §10 has the original table.

### XIV.24 A floor in a docs reader fires when the corpus is correct (SH672)

Every reader here carries a non-vacuity floor: a number it must clear, so a regex that
stopped matching cannot report zero problems out of zero subjects and be green for the
one reason that proves nothing. The floors are right. Three in
`improvements-debt.test.mjs` are the wrong *kind* of number.

`cited.length > 5` stands at 10, `anchors.size > 20` at 50, `listed.length > 10` at 15.
All three count a corpus **meant to empty** — `ship` drops a section and takes its
citation and contents row with it — so each is a second deadline nobody declared,
enforced by a test whose stated purpose is something else.

Not a prediction: the file's own comment records three earlier floors firing this way in
one session, when shipping five tasks took the backlog from 24 to 19 while every floor
still said 20. The fourth fired when SH226 took the file from 51 sections to 50 against
`> 50`. That one is a coupling now — the contents and the headings are two independent
structures over one file, so a reader that stops parsing headings disagrees with the
contents in the same breath.

**The deliverable is the instrument, not the three edits** (SH338). A reader over the
suites can classify a floor by what it counts: a registry and a ledger only grow, so an
absolute floor over them is honest; the roadmap and the rationale do not. Hold the rest
as a named list, the shape `assertion-debt` already uses.

### XIV.25 Command-line properties are dropped without a word (SH680)

`Shio.main` builds a `SpringApplication` and then calls **`application.run()`** — no
`args`. Every `--key=value` on the command line is dropped, and dropped *silently*: no
warning, no log line, and the application starts normally with the defaults.

Found while verifying SH676, by pointing an instance at a local catalogue with
`--shio.marketplace.enabled=true`. It came up reporting `"shio.marketplace.enabled is
false"`. The property was right and the value was right, and nothing said the argument
had been ignored — which is the defect: the failure is indistinguishable from the
feature not working.

**Worse than it looks.** `java -jar app.jar --server.port=8080` is the most universal
thing anyone does with a Spring Boot jar. An operator who reaches for it gets a running
instance on the wrong port and no reason. The workaround — environment variables, which
bind through relaxed naming and *do* work — is one nobody has cause to look for.

The fix is `application.run(args)`. What needs care is the assertion: a two-character
change no unit test naturally covers, because it is about the process boundary. The
honest guard is a lint over the bootstrap — `run(` inside `main(String[] args)` must
pass them — cheap, textual, and it fails when somebody writes the argument-less form
again.

Check in the same pass whether any deployment artefact relies on the broken behaviour:
the Docker and compose files configure through environment variables, so this should
widen what works rather than change what already does.

### XIV.26 Only an OAuth login can be an administrator (SH681)

`ShAuthorityResolver` reads user → groups → roles and adds **`ROLE_ADMIN`** for anyone
in the `Administrator` group. Exactly two classes reference it: `ShOAuth2UserService`
and `ShOidcUserService`. A password login goes through `ShCustomUserDetailsService`,
which walks the same tables and builds authorities **from `ShRole` names alone** — no
group rule, no `ROLE_ADMIN`.

On a fresh instance `GET /api/v2/role` answers `[]`. So a password-authenticated user
carries no role authorities at all, and every `hasRole("ADMIN")` gate is unreachable:
the SH219 blueprint reload, the SH22 tenant writes, and whatever is added next.

Measured rather than reasoned: `POST /api/v2/agent/blueprints/reload` answers **403** to
the seeded `admin` — over a console session *and* over Basic auth — while
`/api/v2/user/current` reports `"admin": true`. That endpoint exists to be called by an
administrator and has not been callable by one since SH219. Found while wiring SH677,
which wanted the same gate.

Two things this is not. Not the OAuth path being wrong — that one works, which is why
nobody noticed. And not `admin: true` being wrong: the flag is group membership, which
is correct; what is missing is the step turning membership into an authority.

The repair is one of two, and choosing is the task: wire `ShAuthorityResolver` into the
local `UserDetailsService`, or seed the `ShRole` rows the group is meant to carry. The
first makes one rule serve every login. Whichever lands, the assertion is the same and
absent today: an admin-gated path, called by a locally authenticated administrator, must
not answer 403.

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

`openviglet/bootstrap-site` is not a project — it is an `export.json`, an SH68 exchange
package, and its ten posts are `PT-FOLDER-INDEX` ×1, `PT-ARTICLE` ×3, `PT-PAGE-LAYOUT`
×2, `PT-REGION` ×2, `PT-THEME` ×1, `PT-FILE` ×1. The presentation *is* content, and it
travels the same way content does. Four things in it settle the design:

**1. Regions are slotted by an HTML attribute.** A layout's `HTML` field is a whole
document with named holes:

```html
<body>
  <div sh-region="BOOTSTRAP_NAV_REGION"> </div>
  <div sh-region="BOOTSTRAP_CONTENT_LIST_REGION" style="margin:20px"></div>
```

`sh-region` is the composition mechanism, and it is also — already, in 2018 — exactly
the annotation contract the Universal Editor needs, which SH185 later wrote down as a
protocol → [CHANGELOG.md](CHANGELOG.md) Block P. A rendered page that keeps these
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
> (Historic subsection anchors `§II.1`–`§II.7`, cited by `docs/specs/*` and no longer
> headings here, map to SH6, SH7, SH8, SH9, SH10, SH11, SH62.)

## III. Composable platform primitives

> ✅ **Fully shipped (SH12–SH16, SH26, SH51–SH55)** → [CHANGELOG.md](CHANGELOG.md)
> Block C. The agent surface leans directly on four of them: the **audit trail**
> (SH51 → the change feed and attribution), **soft-delete** (SH55 → revert), the
> **draft/published diff** (SH14 → review), and **search**
> (SH52 → `shio_find`). Open follow-up: folder soft-delete (subtree-hide semantics).
> (Historic anchors `§III.1`–`§III.10`, cited by `docs/specs/*` and no longer headings
> here, map to SH12–SH16, SH51–SH55.)

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
> on, and `ShSiteImportService.provisionInto` is the engine `shio apply`
> reuses — one engine now serving site clone, tenant provisioning, bootstrap and
> blueprints.
>
> **Non-goal (do not re-add):** the legacy `ShSitesNodeJS` "export site as a
> runnable Node app" is superseded by `create-shio-app` (SH8) and, going forward, by
> blueprints → [CHANGELOG.md](CHANGELOG.md) Block J.

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

### XVII.55 Two templates, and neither serves an authorable replica (SH502)

SH446 made the built-in scaffold installable; SH502 measured both templates and shipped
the refusal. What remains is one act: **publishing `@viglet/shio-sections` and
`@viglet/shio-model`**, which have never been on the registry and answer E404. Until
they are, an authorable replica has no installable front end — its content is sections,
and the only scaffold that draws them cannot be installed. A fidelity replica is
unaffected: it has Shio's own renderer.

**Blocked on an operator action, not on code, and verified so on 2026-08-11.** Both
packages build; both pass `pnpm publish --dry-run`; `publish-js-sdk.yml` orders model
before sections, so pnpm rewrites the `workspace:*` to a version that exists by then.
The trigger needs `NPM_TOKEN` and is irreversible on a public registry, which is why it
is an operator's call rather than a task's.

**The post-release half is already instrumented, so nothing has to be remembered.** The
replication conformance keys its assertion on `published.mjs` rather than on a fixed
expectation: while the table says `null` npm must refuse the section template, and the
day the release lands the same line requires a clean install. So a stale table fails the
gate naming itself. Resuming is therefore two steps — dispatch the workflow, then flip
the two `null` entries with `scaffold-versions.test.mjs`'s expectations in the same
commit.

### XVII.94 The guard on one side of the seam (SH684)

Measured on a full authorable run of `www.viglet.org` at `542142e0`: 12 pages, 31
candidates, 23 accepted, 42 objects, all four sampled routes 200 anonymously.

`propose --accept all` declines a candidate whose REQUIRED field the derivation cannot
fill, and says why in its own words: *"the section would be content `shio verify`
refuses."* Four blocks were declined on a missing `heading`.

`convert` then reported **9 field values dropped as chrome** — a value carried by
candidates that share no page is nobody's content, which is correct and is SH574's rule.
Seven of those nine were `heading`.

`verify` returned **7 errors, all `required-field-empty` on `heading`, on exactly those
seven ids** — b425bd805, b6d312963, bafb9442e, bcc346a2f, be82fdd62, bf7b6dd32,
bf83c82c6. Not the four propose declined. Seven others, produced by the phase that runs
after the guard.

So the rule is enforced on the propose side of the seam and nowhere on the convert side,
and the two decisions cannot see each other: acceptance happens before chrome is known,
chrome-dropping happens after acceptance is final. Every authorable replication of a
site with repeated marketing chrome ends red for a reason no phase reports as its own.

The fix is a decision, not a patch: chrome-dropping either declines the candidate the
way propose would, or keeps a value a required field needs. Whichever, the assertion
that closes this is a conversion where those two counts cannot disagree.

### XVII.95 The instrument that cannot read the type it drew (SH685)

`shio annotations` is the answer to *is this page actually editable* — the props a page
emits against the fields its types declare, both declaration forms, collection rows
expanded (SH588, SH465, SH475).

Run on the home page of the `www.viglet.org` replica it reported:

    9 post(s) — 67 of 4 declared field(s) annotated, 4 NOT editable,
    8 whose type this could not resolve
      b577f35b…  (Page)  0/4  missing: content, description, sections, title
      d85d8f5d…  16 annotated — type not resolved, NOT checked

The eight it could not resolve are the sections, and their types resolve fine one call
away: `GET /agent/read?address=id:d85d8f5d…` answers `type=SectionFaq`, and
`post:VigletOrg/sections/b1083a9ab` answers `type=SectionFeatureGrid`. So the comparison
the command exists to make was made for **one** post of nine, and the headline `67 of 4`
is a count of annotations against the declared fields of the only type it managed to
look up.

Two consequences, and the second is the reason this is filed rather than noted. It exits
1 on the Page's `0/4`, so the run reads as a finding about the page while 89% of the
page went unchecked — a red that hides a blind spot is worse than either. And "NOT
checked" is honest prose doing the work an assertion should: nothing fails when the
unresolved count grows.

The Page's own `0/4` may well be real. It is not knowable from this output.

### XVII.96 The selector the reader cannot spell (SH686)

`appearance.definedClasses` parses a theme's CSS for class selectors and
`unstyledClasses` is what the markup uses and it did not find. On the `www.viglet.org`
replica it reported **205 used, 1002 defined, 42 unstyled**, and `verify` turned that
into `page-unstyled`: *"draws with 42 class name(s) its own stylesheet does not define,
and it loads no other."*

Every one of the 42 is defined. Tested against the stored `theme/captured` CSS
(1,061,479 chars):

    class                     .cls    escaped
    bg-background/80          false   true
    dark:text-slate-400       false   true
    disabled:opacity-50       false   true

CSS requires `/` and `:` to be escaped in an identifier, so Tailwind emits
`.bg-background\/80`. The reader matches the raw form only. **All 42 carry a `/` or a
`:`** — the trait is the whole population, not a sample.

Two consequences. The warning is a false positive on any site built with a modern
utility framework, which is most of what a replication will ever capture. And it is
worse than noise: it accuses the *verbatim* half of the page, which renders correctly,
while the real appearance defect on that page — the accepted sections are presented by
the Shio theme and the chrome by the captured one, two visual languages in one document
— has no finding at all. A warning pointing at the wrong half is what sent a whole
investigation into hand-parsing 1.1 MB of HTML.

Unescape per the CSS grammar, and assert a Tailwind-shaped class resolves.

### XVII.97 Counting a page is not describing it (SH687)

Two defects are visible in one glance at the `www.viglet.org` replica's home page: the
captured footer renders **above** the first section, and the accepted sections are
presented by the Shio theme while the chrome keeps the captured one — two visual
languages in one document.

Neither is expressible in anything Shio returns. `landmarks` is a histogram: `footer×1
header×1 nav×1 section×9`. `digest` promises an outline and gives the heading tree,
which excludes a footer because footers carry no headings. `classes` (205),
`definedClasses` (1002), `unstyledClasses` (42), `diff`'s link and character counts,
`annotations`' `67 of 4` — every one is a count or a set. `audit` alone holds geometry,
and it returned **0 errors** here.

So the appearance surface can *measure* a page and cannot *describe* it, and both
defects are positional: one is order, one is attribution.

What would have replaced the session: one answer, per region, in document order, naming
the theme in force.

    region     order  markup     styled by             unstyled
    header     1      verbatim   VigletOrg (captured)  0
    footer     2      verbatim   VigletOrg (captured)  0   ← before content
    section×9  3-11   sections   VigletOrg (Shio)      0
    ⚠ two themes in force; the chrome and the sections share none

Fifteen lines against 1.1 MB of hand-parsed HTML and a human round trip, which is P9
stated exactly backwards.

### XVII.98 The instrument that sat out the question it answers (SH688)

`shio judge` is the one-verdict verb: run the instruments, answer once, name where they
disagreed. On the `www.viglet.org` replica it reported `FINDING from 2 instrument(s), 2
unproven`, and said so honestly:

    audit  NOT RUN — not asked for — pass --browser
      it would have answered: is anything wrong with the page a browser renders

The question being asked was *why does this page look wrong*. `audit` is the only
instrument that renders in a browser and the only one that returns geometry — it reports
overflow in pixels with a CSS selector each. It sat out, and the investigation went to
hand-parsing HTML and then to asking a human for a screenshot.

The honesty is not the problem; `judge` naming what it skipped is SH589 working. The
default is. `--browser` is off because audit needs playwright and loads one page at a
time, which are real costs — but `judge` can *test* whether playwright is present, and
when it is, declining the only geometric instrument is a decision it should not make
silently on a replica whose mode is `mixed`.

Two changes, both small. Run audit when playwright resolves, and say so. When it does
not resolve, keep the current line but state the install, because *not asked for* reads
as a preference and *not installed* reads as a fact — and the fact is the actionable
one.

`snapshot` stays opt-in: choosing a baseline is a judgement, and SH589 is right that
inventing one is worse than abstaining.

### XVII.99 A finding that cannot be falsified (SH689)

`shio audit` returned ten warnings on the replica's home page, two of them:

    warn  font-fallback  Inter Variable — did not load, so the page fell back
    warn  font-fallback  Plus Jakarta Sans Variable — did not load, so the page fell back

The page carries **33** `@font-face` rules. Both families are declared twice over: once
as `url()` into the replica's own asset space, once as an inline
`data:font/woff2;base64` payload. Every data URI decodes to a valid woff2 — magic
`wOF2`, 1,716 to 85,068 bytes. The `url()` form answers **200** with 85,068 bytes at the
path the CSS names.

So the fonts are present by two independent routes, and the warning asserts the
opposite. Either it is wrong, or it observed a load that had not finished when it looked
— a real and different thing. **Nothing in the output distinguishes those**, which is
the defect: `did not load, so the page fell back` is a causal claim about the rendered
result, made by the one instrument that has the rendered result in hand, and it reports
neither the computed `font-family` of any element nor the load state it read.

Report the rendered fact instead of the load event: the computed family of a sampled
heading and body element, with the family the CSS asked for beside it. Then the finding
is falsifiable, and a genuine fallback is still caught.

This is the second appearance instrument on one page whose claim did not survive being
checked (SH686 is the first), which is the substance of SH687.

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
of 40** to **6**, and every one of the six was a cluster **no Block R task ever named**
— not leftovers that ran out of time, never in scope. That is the finding the matrix
existed to produce, and it is why these were a block rather than a snagging list.

Four of the six were curator-facing, which inverts who actually needs a page: an agent
reads a manifest, a person reads a page or gives up. P7 — *never surprise the human* —
is the law the gap sat on. The seventh entry was the one a scan could not see: the image
epic's operator half scored 🧩 rather than 🔴, and a partially-covered row does not read
as an orphan.

**Two tasks are worth keeping for their shape.** SH623 and SH628 were ⚠️ — a page
describing a shape the product left — and that is a *rewrite*, because a new section
under stale prose makes the page contradict itself. SH628 landed the instrument first
(SH322): a version in prose is a claim nothing derives, so `docs-stack.conformance.mjs`
reads Java, Spring Boot, Node and pnpm out of this repository's build files and fails
any page — `README.md` included — that states another. It found three defects on its
first run, and a fourth in SH499's own reader.

## XXI Documentation the matrix still names (Block U)

Block T closed every row its own tasks named and left this file's audit still holding
two entries — one because the summary that scans it could not see the row, and one
because that summary is typed rather than derived. They are the same defect at two
altitudes, which is why they are one block rather than a footnote on the last one.

The block's rule is SH338's: the count is fixed before the page is written. A page added
under a summary that cannot count it is a page the next scan will not know exists.

### XXI.10 No page publishes the four discovery calls as a runnable request (SH674)

`docs-examples.conformance.mjs` does what no vocabulary check can: it issues the
requests the end-user pages print, against a running instance. Four are named rather
than inferred — `GET /api/v2/agent/manifest`, `/agent/context`, `/agent/blueprints` and
`GET /api/v2/post-type` — because they are the discovery surface, P2's *one call
replaces a session*, and a page showing them promises a reader they answer immediately.

Run today against `docs-shio` at `d7735f3`, all four report *"no longer published — this
list has outlived its page"*, and the suite falls to 5 executable requests out of 10,
tripping its own no-op floor. Nothing 500s and nothing 404s: the calls are **absent from
every page**. The gate is red for a documentation regression, which is the gate working.

Two things this is not. Not a stale list — `docs-vocabulary` still resolves those names
against this source tree, so the endpoints exist and answer. And not one page's edit:
four requests spread across the hubs went at once, which points at a restructure that
moved prose and left the request blocks behind.

The repair is a page decision and belongs with the COVERAGE row claiming the discovery
surface is documented. Worth carrying into it: a row can be ✅ while the request it
promises is gone, because the matrix records that a page exists and this suite records
that it still works — the same two-altitudes split Block U was opened for.
