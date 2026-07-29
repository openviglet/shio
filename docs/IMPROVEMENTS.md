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

### VIII.1 MCP server host (SH75) — ✅ shipped

Shipped: `POST /mcp` + `shio mcp` → [CHANGELOG.md](CHANGELOG.md) Block H. Design
records that bind what follows:

- **Two error channels is the load-bearing decision.** A JSON-RPC `error` is read by
  the *client*; a result with `isError` is read by the *model*. Everything the caller
  could fix therefore travels as a result, because routing an SH80 teaching error the
  other way silently discards the `fix` and `didYouMean` that make the retry succeed.
  Any future protocol surface inherits this: ask who has to change, and send the error
  where that party will see it.
- **Implementing beat importing, for a stated reason and not on principle.** The SDK is
  Jackson-2/Boot-3; this app is Jackson-3/Boot-4. When a dependency would add a second
  copy of core infrastructure to save a few hundred lines of well-specified envelope
  handling, the lines are cheaper. That calculus flips if MCP grows a hard part
  (sampling, elicitation, server-initiated streams) — revisit then, not before.
- **Refusing a capability is a feature when you say so.** `GET /mcp` returns `405`
  rather than opening a stream that never emits, because the two are indistinguishable
  to a client and only one of them is honest.
- **Attribution cannot be retrofitted.** The session id is minted by the transport on
  the first `initialize`, because rows written before it existed simply do not have one.
  It rides in the audit *actor string* (`alice (agent:7f3c…)`) rather than a column:
  SH106 owns the credential model and should migrate the schema once, and the human
  stays visible because an agent acts *as* someone.

### VIII.2 Agent manifest (SH76) — ✅ shipped

Shipped: `GET /api/v2/agent/manifest` → [CHANGELOG.md](CHANGELOG.md) Block H.
Two design records worth keeping, because later tasks inherit them: (1) **an
unshipped capability is reported `false`, never omitted** — an absent flag makes a
caller probe, which is the exact cost the manifest exists to remove, so SH75/SH77/
SH81/SH92 each flip an already-present flag rather than adding one; (2) the
endpoint index is **curated, not generated** — it is read every session, so it
carries a token budget (a test fails the build past 30 entries). The
`shio://manifest` MCP resource is the same payload once SH75 lands.

### VIII.3 Context pack (SH77) — ✅ shipped

Shipped: `GET /api/v2/agent/context` → [CHANGELOG.md](CHANGELOG.md) Block H.
Design records later tasks depend on:

- **The digest is the product, not a nicety.** It is what turns "re-read the
  context" from a decision into a free default, so every ordering in the pack is
  explicit and the digest deliberately excludes the budget/measurement lines. SH116
  wires the same value to `If-None-Match`; **do not** start hashing measurements.
- **Only unbounded sections may be truncated.** The model, sites and conventions are
  charged unconditionally; the sitemap gets the remainder. A partial model is not a
  smaller answer, it is a wrong one — the agent will invent what is missing. A caller
  that cannot afford the model drops it explicitly with `include=`.
- **Charge every bounded section before the gate opens.** Charging one afterwards
  (the bug that shipped and was caught in test) lets the walk admit rows a later
  charge pushes over the ceiling.
- **Conventions are derived, not stored** — the derivable subset only. SH114 adds the
  author-written store on top; the two must stay distinguishable.

**Still open from SH77's stated surface:** the `shio://context` MCP resource (needs
SH75). The `shio context` CLI shipped as SH126 — note its two divergences from the
endpoint, both deliberate: `terse` is the CLI's *default* (its caller is an agent,
not a browser), and the body is passed through as text, never parsed and
re-serialized, because the digest is computed over exactly those bytes.

### VIII.4 Token-lean responses (SH78) — ✅ shipped

Shipped: budget + terse encoding + `include=` + `X-Shio-Est-Tokens` →
[CHANGELOG.md](CHANGELOG.md) Block H. Standing rule that survives: **agent reads
never include authoring metadata by default** (`owner`, `date`, `publishStatus`,
`modifier`) — the CDA DTOs (SH1) already made that call.

**The measurement to carry forward, because it reorders the backlog:** the terse
encoding's saving is **per row, not per payload** (~25–35% on a row; diluted to ~⅓
overall on a small pack where fixed prose dominates), and the residual per-row cost
is dominated by the **36-char UUID**. Encoding is therefore the *smaller* lever:
**path addressing (§VIII.5, SH79) is the bigger one**, and dropping ids once paths
address content (SH121) is worth roughly as much again as the encoding change. Two
corollaries became tasks: extend the cost header to the CDA (SH124), and make
null/default omission a policy with a test rather than an annotation someone
remembers (SH125).

Anti-goal (P3 corollary), unchanged: terseness must not cost a second round trip. A
response that omits something the agent then has to ask for is a regression — which
is why per-field projection on content reads was **not** forced into SH78 and waited
for **SH123** (✅ shipped with SH83, when there were finally content reads to project).
Its own record, in the same spirit: a projection naming a field the type does not have
is a *teaching error*, because an empty projection is indistinguishable from an empty
post and the agent's next move would be to overwrite real content with nothing. The
response also echoes which fields came back — a few tokens that remove an "is this all
of it?" round trip, which is the trade this whole section is about.

### VIII.5 Path addressing (SH79) — ✅ shipped

Shipped: the address grammar, resolver, `GET /api/v2/agent/resolve` and
`shio resolve` → [CHANGELOG.md](CHANGELOG.md) Block H. Design records that bind what
follows:

- **A capability is not the same as a payload field.** Read naively, "path
  addressing everywhere" means putting an `address` on every row — which would have
  made the block's biggest *token lever* a token *regression*, since a row already
  carries the site, path and url the address is built from. The grammar is stated once
  in the conventions; the resolver makes it actionable. Generalize: before adding a
  field, check whether the caller can already derive it.
- **Folder paths and post URLs are two namespaces.** A folder path is a chain of
  folder names; a `furl` is arbitrary. The scheme says which is meant, and the lenient
  `site:` form refuses to guess when both are occupied. SH81/SH82 must keep this —
  an "upsert this path" write that guessed the namespace would create content in the
  wrong place silently.
- **Absent, not forbidden.** An address outside the SH2 allow-list resolves to 404,
  never 403, so a scoped credential cannot use the error to prove a site exists. Every
  future address-taking surface inherits this.
- **`addressOf` is the inverse and is round-trip tested**, because SH121 removed the
  id from context rows and that only stays honest if everything shown is addressable.

Both follow-ups have since shipped: **SH121** (id-free reads) and **SH81/SH82**
(address-native writes) → [CHANGELOG.md](CHANGELOG.md) Block H. One record from SH121
that later tasks inherit: **the cursor is not a row field.** Pagination still travels
as an id, because it is one value per *response* rather than one per row — the cost
argument that removed ids from rows does not apply to it, and an id needs no
namespace disambiguation to be exact. Before dropping an identifier, ask how often it
is paid, not whether it is ugly.

### VIII.6 Teaching errors (SH80) — ✅ shipped

Shipped: RFC 9457 + `fix`/`allowed`/`didYouMean`/`example` across the API →
[CHANGELOG.md](CHANGELOG.md) Block H. **The finding to carry forward:**
nearest-string matching is silent on exactly the guess an agent is most likely to
make. `RichText` is 4 edits from `Text` and further from every other widget,
because it is a **vocabulary** mismatch (Contentful/Sanity/Strapi/Payload
dialects), not a spelling one — so a `didYouMean` built only on edit distance
answers nothing in the common case. The fix was a curated alias map consulted
before the distance fallback (`ShWidgetAliases`), constrained to only ever suggest
an installed target. Generalize this wherever a closed vocabulary is exposed to a
caller who learned a different one: **translate dialects first, correct spelling
second.** Candidate next surfaces: post-state names, locale codes, and the
blueprint parameter vocabulary (§XI.1).

### VIII.7 Batch + plan/apply (SH81, SH82) — ✅ shipped

Shipped: `POST /api/v2/agent/batch` · `/plan` · `/apply` →
[CHANGELOG.md](CHANGELOG.md) Block H. Design records that bind what follows:

- **A dry run must not write.** The tempting implementation — apply, then roll back —
  is wrong for a reason that generalizes past this endpoint: by the time the rollback
  is decided, the audit rows, lifecycle events and quota counters have already
  happened. Anything that calls itself a *plan* has to be computed, which means it also
  has to model what earlier steps in the same plan would have created. Every future
  `--dry-run`/`plan` surface (SH93's blueprints, SH108's confirm tokens) inherits this.
- **The plan and the apply must be the same code path**, differing only in a flag.
  SH82's `plan` is literally `apply(dryRun=true)`, and both compile down to SH81's ops.
  The one place an early version let them diverge — a dry-run shortcut that also fired
  in real runs — silently skipped a publish, which is exactly the failure mode "the
  plan lied" that makes a plan worthless.
- **Imperative and declarative are both needed, and one is built on the other.** A
  batch answers "do this now"; a document answers "this is the site". Only the second
  survives a context compaction, because it does not require the agent to remember what
  it already did. Keeping the document a *compiler* over the ops, rather than a second
  engine, is what stops the two from disagreeing (P10).
- **Destructive scope is declared, never inferred.** `prune` deletes only inside the
  folders the document declares, and declaring none is an error. A blast radius that
  depends on what the author forgot to mention is the definition of a surprise (P7).
  Same rule for the tri-state `publish`: absent means "leave it alone", because
  treating a trimmed field as `false` would unpublish live pages.

**The general lesson, now paid for three times.** A write vocabulary designed against
"build a site" will be missing the verbs for "change a site", and the second is the harder
half of the north star. All three gaps this engine shipped with — no way to move a post
(SH134), a prune that could not remove a folder (SH135), and no way to move a folder
(SH137) — were invisible while building and obvious the first time a section was renamed.
The third is the one that shows the pattern is not coincidence: the *reason* it stayed
missing is that building a site never needs it, and the reason it mattered is that its
absence pushed callers to `folder.upsert` + `folder.delete`, which takes the subtree. When
a vocabulary lacks a verb, callers do not stop — they compose the nearest destructive pair.

**What SH138 turned out to be, which is worth remembering when writing the next one.** It
was filed as a truncation hazard, and the truncation was not there: no code cut a plan row.
Going to look for it found the actual defect one step away — a compiled document was
validated as if the caller had typed its ops, so a large prune was refused with advice
("split into batches of at most 200") that is impossible to follow for ops the server
generated. **A ceiling with no usable way past it is the same dead end as a silent cut**,
and it is the harder one to spot, because the failure is loud and looks like a working
guard. Both halves shipped → [CHANGELOG.md](CHANGELOG.md). Two records that bind what
follows: a limit's *error* is part of the limit, so a ceiling whose `fix` names an action
the caller cannot take is not finished; and where an invariant can be expressed in a type,
express it there — `ShAgentChangeSet` now cannot represent a short list, which is what
keeps the rule true against a `limit` parameter nobody has proposed yet.

**And one rule SH139 paid for, which SH137 then collected on — twice over.** The console had
its own copy of "move a post", and it was the copy that re-slugged a live URL from the title:
the same verb, two implementations, and only the one an agent drives got the careful
semantics. Fixing it meant deleting the fork, not the offending line. So the rule is
**when the console and the agent perform the same content verb, one of them delegates**, and
SH137 was the first verb built that way from the start — one `ShFolderWriteService#move` both
surfaces call.

Writing it that way is what found a defect nobody had filed. Putting the guards on the shared
writer forced the question *what does the console path do about this?* — and the answer was
nothing: it had no cycle check, and its move dialog navigates **down** from the site root, so
a curator could pick a child of the folder being dragged. That does not fail; it succeeds and
detaches the subtree from the root. The generalisation is worth keeping, because it is the
economic argument for the rule rather than the aesthetic one: **merging two implementations of
a verb audits the surviving one against every case the other one handled** — including the
cases neither handled, which is where the unfiled bugs live.

### VIII.8 The agent tools (SH83) — ✅ shipped

Shipped: eight tools, three resources, four prompts →
[CHANGELOG.md](CHANGELOG.md) Block H. The original framing — *why nine and not
forty: **tool schemas are context*** — held up and is now enforced by a test. Design
records that bind what follows:

- **A tool that cannot be a shape over REST is a design smell.** `find`/`read` had no
  REST equivalent, and the tempting move was to put the query logic in the tool. Adding
  `GET /agent/find` and `/read` first cost an hour and kept P10 true; it also means the
  CLI and a plain HTTP caller got the capability for free.
- **Eight, not nine, and the ninth is not a stub.** `shio_verify` waits for SH99 because
  a verify that only pretends is *worse than absent*: the agent stops looking. The same
  reasoning downgraded `shio_changes` to an explicitly-labelled window rather than a
  fake cursor. Generalize: when a capability is partly available, the honest surface is
  the part that works plus a manifest flag saying the rest is not here — never a
  plausible-looking shim.
- **Prompts are procedures, never content.** They encode the call order that avoids
  mistakes (plan before write, publish as its own step) and say nothing about tone or
  copy. §0.7 stands: there is no inference in Shio, and an MCP prompt is a protocol
  object, not a system prompt.
- **Read the drafts.** Composition said "route reads through the CDA"; the CDA only
  sees `PUBLISHED`. An agent that cannot read back its own unpublished work is not
  composing, it is blind — so these reads go to the unified model, DRAFT-preferred,
  with the same SH2 allow-list applied. Compose with the *rule*, not necessarily the
  code path.

The two gaps these tools revealed — no `post.move`, and a prune that skipped folders —
are shipped (SH134, SH135). Neither showed up while *building* a site, only when changing
one, which is exactly the half of the north star that is harder to test by demo, and the
reason §VIII.7 now treats "can this vocabulary change a site?" as a standing question
rather than a finished one.

### VIII.9 Turing ecosystem wiring (SH84)

The SH22 half is shipped: `ShAgentTenantIsolationIT` exercises every Block H surface
from a second tenant and proves the standing invariant — the agent path is **never** a
privilege escalation (§XII). It needed to be its own test rather than a corollary of the
storage isolation one, because this surface resolves content by *address* rather than by
id and its purpose is to describe a whole instance in one call: a context pack carrying
another tenant's sitemap would look exactly like a working context pack.

What remains is the Turing integration (replaces old SH37) — the ecosystem half, which
is a wiring question rather than an enforcement one.

### VIII.10 The recurring defect class, and the rule it produced

Two guards now exist for the same shape of bug: **code that compiles, passes review, and
is wrong only when the data is queried along an axis the author was not thinking about.**
SH122 covered the `@TenantId` JOINED-join hazard; SH128 covers site resolution, where only
a root folder carries `shSite` and everything else has to walk up to find it.

Both are shipped (→ [CHANGELOG.md](CHANGELOG.md) Block H), and what is worth keeping is
not the implementations but the rule they establish:

> **When an invariant is violated for the first time, write the guard for that invariant
> — not a resolution to be careful.**

The evidence is that both guards found more instances than the defect that motivated
them, on the day they were written. SH122 found three broken CDA paths; SH128 found four,
including a scoped CDA token that could not list the children of any folder below a site
root. In both cases the paths were already covered by behavioural tests, which passed,
because the tests exercised the root case and the defect only appears below it.

Two corollaries that make the rule cheap to follow:

- **Prefer a textual lint to a behavioural test** when the invariant is about how code is
  written rather than what it returns. A behavioural test proves the paths it exercises;
  the next instance lands in the path nobody thought to add. A lint covers the paths that
  do not exist yet.
- **Give the lint an escape hatch that costs a sentence.** Both guards accept an inline
  exemption comment naming the reason (`ShFolderSite-exempt`, the `nativeQuery` carve-out).
  A guard with no exemption gets deleted the first time it is wrong; a guard whose
  exemption is a line of prose in a review gets an argument instead.

### VIII.11 What the hand-rolled surfaces earned (SH130, SH131 — ✅ shipped)

Both guards came from one observation: the failure mode of these surfaces is **silence**,
not an exception. Shipped → [CHANGELOG.md](CHANGELOG.md) Block H. The findings that bind
what follows:

- **A version assertion that compares a constant with itself is not a test.** SH130
  replaced `assertThat(version).isEqualTo(PROTOCOL_VERSION)` with a check against the
  *observable obligations* of the revision claimed — batching refused, structured
  capability objects, `content[]`+`isError`, plural `contents`. The tautology would have
  kept passing after someone bumped the constant past a breaking change, which is the
  only moment the assertion had a job to do.
- **Pin shapes, not prose.** The goldens keep every key and the literal value of the
  fields that are themselves contract, and collapse free text to a type token. A golden
  that fails on a copy edit stops being read, and a golden nobody reads is worse than
  none — the diff is the entire product.
- **Determinism is not automatic just because the data is.** The transcript immediately
  caught `tools/list` serializing differently on every boot, because `inputSchema()` is
  built from `Map.of` and iteration order follows the JVM hash seed. Nothing about the
  content changed; the bytes did, on the one payload re-sent every turn. This is the
  first place P5 has been broken by the *language* rather than by a design choice, and
  ✅ **SH136** → [CHANGELOG.md](CHANGELOG.md) shows it was not the last: four more live
  instabilities, all the same shape. Two records that bind what follows.
  - **Some invariants cannot be tested behaviourally, and knowing which is the skill.**
    `Map.copyOf`'s order comes from a salt fixed once per *process*, so "build it twice
    and compare" passes over broken code in every test run there will ever be. Before
    writing a guard, ask what a broken implementation would do differently *inside one
    JVM* — if the answer is nothing, the guard has to be textual or the code has to be
    restructured until the defect becomes observable. SH136 did both: a lint, plus moving
    the sort to the hash site so order-independence could be asserted with a deliberately
    reversed input.
  - **Prove a guard by breaking the code.** SH136's digest assertion was written, passed,
    and was then *verified by reverting the fix* to confirm it failed. That step is cheap
    and it is the only evidence that separates a guard from a decoration — the same
    lesson as SH130's tautological version check, arrived at from the other direction.
    A guard nobody has watched fail is a guard nobody should trust.
- **A contract that can only be honoured by remembering belongs inside the method.**
  SH131's bind requirement lived in a comment; SH83 read the comment and still got it
  wrong, and the breach looked like an empty result set — valid data, not an error. The
  test for "is this comment load-bearing?" is: *if someone ignores it, does the system
  fail loudly?* If not, it is not a comment, it is a missing parameter or a missing
  wrapper.

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

### XIV.4 Build-time guard for the `@TenantId` JOINED-join hazard (SH122) — ✅ shipped

Shipped → [CHANGELOG.md](CHANGELOG.md) Block H (with the three production bugs it
uncovered). **The generalizable lesson, which is why this section survives:**

*Characterize by execution, not by argument.* The hazard's boundary was guessed
twice and wrong twice — first "projections only", then "not predicates" — each time
from a plausible reading of the evidence (shipped `WHERE` queries with green tests).
Executing each form settled it in minutes and produced a rule that is both correct
and simpler than either guess. When a lint's scope depends on where a runtime
boundary actually lies, spend the test rather than the reasoning: a lint built on the
wrong boundary either fails on correct code or misses the defect it was written for.

*A green test is not coverage.* The three broken paths had tests; none reached the
finder. Where a defect class is invisible to compilation **and** easy for tests to
miss, a cheap always-on textual guard is the right instrument — the same role
`ShLiquibaseChangelogAgnosticTest` plays for schema portability. Worth applying to
the next such class rather than waiting for it to bite.

*Self-test the guard.* Both of this lint's regexes were initially inert on real
input (word boundaries fail on both sides of a derived query name). A lint that
matches nothing passes silently forever, so its patterns are asserted against the
exact strings that broke production.

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

### XV.1 A built-in HTML renderer for page layouts (SH183)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three conclusions from building
> it are load-bearing for the rest of this section, so they stay here rather than going
> with the design:
>
> **The non-goal is now a classpath fact, not a policy.** Handlebars.java declares
> `nashorn-core` at *compile* scope for its JS-helper integration, so the obvious cleanup —
> delete the orphaned direct declaration, add the template engine — would have swapped an
> unused script engine for a used one. It is excluded, and `ShRenderNoScriptEngineTest`
> asserts that along with the two defaults that would undo the rest: the value resolver
> (`MapValueResolver` only, or a template walks any object it is handed) and the template
> loader (refusing, or `{{> application}}` in a layout reads the classpath). None of the
> three changes the rendered output when it breaks, which is why each is a test rather than
> a review item.
>
> **Presentation as content paid off immediately.** Layouts, regions and themes are posts of
> three system post-types, so everything the agent surface already does works on them with no
> new code — and the 2018 `openviglet/bootstrap-site` export renders with no migration at
> all, which is the strongest available evidence that the model was right and only its host
> was wrong.
>
> **What it deliberately left**: `search`, `form` and `getRelation` (**SH247**), the browser
> JavaScript a region can author but nothing serves (**SH248**), and the fact that
> `/agent/render` and `/preview` now compose the same page two different ways (**SH249**).

### XV.2 A site's front-end URL is unreachable from the agent path (SH184)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Two rules worth keeping, because
> both were arrived at by rejecting a simpler version:
>
> **Fill what is empty, never replace what somebody chose.** SH170 refused to update a site
> at all and was right about a *decision* and wrong about a *blank* — the agent is the thing
> that just deployed the front end and knows the address. The P7 line is not "may an upsert
> write this field" but "was there anything there".
>
> **The preview fallback is resolved, not stored.** Writing `/preview/<site>` into
> `site.url` at creation is one line and destroys both other properties: the column stops
> meaning "the customer's front end", and there is no longer an empty value to fill or a way
> to tell a placeholder from a choice. `ShSiteFrontEnd` answers the question at the point of
> use instead, and returns a *record* rather than a URL, because the two destinations differ
> by whether a token is needed at all.

### XV.3 Nothing implements the Universal Editor's iframe protocol (SH185)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P; the wire format is
> [specs/SH185-universal-editor-protocol.md](specs/SH185-universal-editor-protocol.md).
>
> The decision worth carrying forward is that **a template declares which post a field
> belongs to, and the engine never infers it**. A region renders against the page's model,
> so `{{TEXT}}` inside it is the page's — but inside a `{{#query}}` block `{{title}}` is the
> listed post's, and the rendered HTML records neither. Inferring would mean the template
> engine tracking provenance through every helper, and getting it wrong means editing the
> wrong post's content. Every visual editor that works asks the author; the ones that guess
> are the ones that edit the wrong thing.
>
> The other half is that `universalEditor: true` was the first manifest flag to break its own
> rule — an unshipped capability reports `false` and never overstates. It was fixed by
> shipping the counterpart rather than by demoting the flag, which was the right way round
> only because SH183 had already landed something for it to target.

### XV.4 The agent sees a page's structure, never its appearance (SH187)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth keeping.
>
> **The honest scope was the whole design.** There is no layout engine, so overlap, contrast,
> truncation and anything responsive are out of reach — and saying that plainly is what made the
> rest possible. What *is* in the document: the class inventory, the classes no stylesheet the page
> carries defines, inline styles, images with no declared size. It covers the appearance failure an
> **agent** creates (markup written against class names the theme lacks) rather than the ones a
> human notices, and that turned out to be the useful trade.
>
> **Two hashes, because they answer different questions.** Folding appearance into SH101's `digest`
> would have made a restyle and a structural break indistinguishable — the one comparison an
> iterative loop most needs to make. `d:` and `a:` prefixes so they cannot be confused in a report.
>
> **A capability flag is added, not repurposed.** `visualSnapshot` stays `false` because there is
> still no raster on this surface; `appearanceDigest: true` joins it. An agent that read
> `visualSnapshot: true` would hunt for a PNG — which is the same rule that flag's own comment set
> and SH185 broke once with `universalEditor`.
>
> The remaining raster question is **SH265**, and the reason it is not a footnote: most of what
> "looks wrong" means still needs pixels.

### XV.5 An MCP-only agent cannot produce a visible site (SH186)

The server never writes a project file, deliberately: a project's name is local identity, and
writing to a developer's filesystem from a content API is the surprise P7 exists to prevent.
Only `shio init` scaffolds a front end, and only a machine with a shell can run one. An MCP
client with no filesystem — the Claude Desktop shape — can therefore build a complete,
verified site that nobody can look at, and nothing in the protocol says so.

SH166 covers the narrower symptom (carried files arrive with `{{projectName}}` unsubstituted).
The broader gap disappears if SH183 lands: the site is visible with no project at all, and the
front-end half becomes an upgrade rather than a prerequisite. If SH183 does not land, the
minimum honest behaviour is for a blueprint apply to say plainly that the content is in place
and the site has no renderer, and for the manifest to carry a flag a client can read before
promising a user a website.

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
> **What split off.** The classifier string-matches log messages because `ShExchangeException` has
> no kind (**SH267**) — a shape this codebase refuses elsewhere, and worth fixing on its own because
> it touches the export path too. And the CLI/MCP reach is **SH268**, where the interesting part is
> that an SH68 package is a *zip*: a client with no filesystem cannot produce one, so "add a tool"
> is not the answer and the honest option may be for the manifest to say this capability is
> CLI-shaped.

### XV.8 Diagnostics name a failure but never carry the evidence (SH190)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth carrying.
>
> **"Go and read the log" is not a protocol.** The old `fix` was accurate and useless: it named a
> file the caller has no access to, and in the session that found this the running instance was not
> even writing to the log the repository contains. Worth generalising — an error whose remedy is
> outside the caller's reach is an error that has not been reported, however precise it is.
>
> **The trimming is the design, not an optimisation.** Only `com.viglet.shio` frames, at most three,
> no arguments, no locals, proxy frames skipped. That is what makes the evidence a *line*, which is
> what makes it affordable to attach to every incident — and the exclusions are also the security
> argument, because arguments are content and content in a diagnostics feed is how a log leaks what
> the ACL exists to protect.
>
> **Absent beats empty, again.** A malformed request rejected by Tomcat has no frame of ours; a 5xx
> the framework rendered has no trace at all. `null` (omitted, `NON_NULL`) rather than `""`, so a
> reader is never sent looking for a location that does not exist — the same rule SH121 set for ids,
> SH193 for `affects` and SH187 for `appearance`. It is becoming the house style, and the reason is
> consistent: a field that is always present is a field nobody reads.
>
> What is left is coverage — three of the four incident kinds still record no frame (**SH269**).

### The iteration loop — what SH183–SH190 does *not* buy (SH191–SH195)

The target is a loop: change the layout, see the result, correct it, repeat — the Lovable
experience, with the CMS as the accelerator rather than a code generator. SH183–SH190 get a site
to *exist and be seen once*. They do nothing for the second iteration, and the gap is not one
feature but five, each of which makes a cycle cost a round trip or a guess.

This matters more here than in a code-generation tool, for a reason worth stating: a region is
**shared**. Lovable edits one component in one app and the blast radius is the file. A CMS region
appears on every page that slots it, so an iterative loop over shared presentation needs bounds
and proof that a single-app loop can simply do without.

#### XV.9 A template is code and must project as files (SH191)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Both open questions closed the way
> the design guessed, and building it opened two tasks:
>
> **The extension is the widget's, and the widget catalogue is short one entry.** A `Theme`'s
> `CSS` field is declared `Ace Editor - HTML` because no CSS widget is seeded, so its sidecar
> is `.html` — the honest consequence of letting the widget decide, and a one-line fix once
> the widget exists (**SH252**).
>
> **A projection that normalizes cannot be exact.** The body field's CRLF → LF exists so a
> pull "cannot report a change nobody made", and it achieved the opposite: the base
> fingerprint was taken from the *server's* document, so the normalized file read back
> different from the base and every later pull reported a local change until somebody pushed.
> Sidecars are verbatim so the question never arises for them; the body needed the comparison
> moved into the projection's domain, which shipped as **SH253** (§XVI.8).

#### XV.10 A template must fail at write time, not at view time (SH192)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P.
>
> Building it settled the "unknown helper" question in a way the design did not anticipate:
> **Handlebars already raises it, but only for a call with parameters**, because
> `{{#search q="x"}}` cannot be a context iteration and `{{#search}}` can. So the compiler
> covers the unambiguous half and a targeted scan covers the three helpers that are known
> missing — and unknown sections *in general* are deliberately not flagged, since a rule with
> false positives is one authors learn to ignore.
>
> The half this could not deliver is the one §XV.10 wanted most: an unknown **field**
> reference as an error "when the post-type is known". At write time it is not known — a
> Region's post-type declares `HTML` and `JAVASCRIPT`, while the fields its template
> interpolates belong to whatever page is later bound to the layout that slots it. That needs
> a resolved page, so it is a lint over rendered pages: **SH251**.

#### XV.17 A template referencing a field nothing declares renders empty (SH251)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three conclusions, one of which
> contradicts how the task was framed.
>
> **"A lint over resolved pages" was the wrong unit, and finding that out was the design work.**
> The resolution does not depend on the page: `postTypeLayout` is keyed by post-type and a
> layout's slots are static attributes, so every page of a type resolves to the same layout and
> regions. Walking the *bindings* checks identical ground at a fraction of the cost and — the part
> that matters more — addresses the finding at the layout or region post, which is the artefact
> somebody edits. The general lesson: when a rule seems to need a per-instance sweep, check
> whether what it actually varies over is the *type*.
>
> **The two severities fall out of who owns the interpolation.** Top-level is the page's
> post-type, which is known, so an undeclared name is an error. Inside a helper block the rows are
> whatever posts the helper found, so the strongest honest claim is "no post-type in this instance
> declares this name" — a warning. That is SH99's line held: `ok` is false only for what the CMS
> can prove from inside itself, and the alternative (an error on something unknowable) is what
> makes a gate people bypass.
>
> **The negative cases were most of the work**, and they are what to protect in any change here:
> frames, `this`/`./`/`../`, data variables, subexpressions, literals, partials, comments, a
> block's own name, an inline helper's name, and every row value. `{{file_url HERO}}` reporting
> `file_url` would have been a false positive on the one helper the shipped 2018 region used. And
> the scanner's own test caught it dropping `{{~TEXT~}}` — Handlebars' whitespace-control form —
> which is the failure direction that stays quiet: a reference not reported, not a wrong one.

#### XV.11 The blast radius of a region change (SH193)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. This section proposed the wrong
> mechanism, and the correction is the part worth keeping.
>
> **`ShReference` cannot hold this edge.** The proposal was to extract slot references at write
> time into it, "which already exists and already survives a move", by analogy with SH154. But
> `ShReference` joins `object_from`/`object_to` to `ShObject`, and **a post is not an `ShObject`**
> — `ShPostUnified` is its own table. The analogy to SH154 was sound and the vehicle was not, and
> the general lesson is to check the *shape* of the thing being reused, not just that it exists:
> "already exists" was true and did nothing to make it applicable.
>
> **Computing beat extracting on the merits, not just on convenience.** SH251 had established
> that presentation resolves from the post-type, so a site's forward index costs one resolution
> per binding however many pages there are, and inverting it is a map. That buys two properties an
> extracted index would have had to earn: it **cannot go stale** — and the interesting staleness is
> not the post write anyone would remember to hook, it is `site.postTypeLayout` changing, which is
> an `ShSite` write no post-level extraction would ever see — and it needs **no migration**, so it
> is correct for an imported 2018 package immediately. A persisted graph buys a cheap reverse query
> at scale; that is a real trade for a different day.
>
> **The surface question answered itself through the MCP cap.** The tool count is capped at 12 with
> 11 used and `tools/list` paid every turn, so a new tool was not available — which pushed the
> answer onto `/agent/changes`, where this section had already suggested it belonged. Constraints
> from P3 selecting the P2 design is a pattern worth noticing rather than resenting.

#### XV.12 Appearance needs a before/after and a one-call loop (SH194)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P, in two halves that turned out to belong to
> different tasks. The *before/after* came free with SH187's second hash; the *round trip* is
> `?digest=true`.
>
> **The reusable shape is SH157's, and it generalises further than it looked.** "A write returns its
> own proof" was filed as a lint idea. It is really a rule about *closing turns*: whatever an agent
> would call next to find out whether its write worked belongs in the write's response. Lint, then
> digest — and the next instrument that appears should be asked the same question before it gets an
> endpoint of its own.
>
> **"Affected" is not "touched".** For a page they coincide; for a template they do not, and the
> honest answer needs SH193's graph. Worth noting that this is the first consumer of that graph
> which uses it to *do* something rather than to report — which is usually the sign a graph was
> worth building.
>
> **Only the hashes.** The caller is asking whether something moved, which is a comparison against a
> value it already holds; the outline would be payload it discards. That is P3 applied to a response
> nobody had thought of as a budget.
>
> The ceiling is the open question — ten pages is a guess, not a measurement (**SH266**, waiting on
> **SH264**).

#### XV.13 Most layout changes are tokens, not structure (SH195)

If the loop is going to be *constant* layout change, it is worth noticing what those changes
actually are. Very few are "add a region". Most are spacing, type scale, colour role, radius,
density — and `PT-THEME` holds `CSS` and `JAVASCRIPT` as opaque blobs, so every one of them is a
stylesheet rewrite: nothing declares what may be changed, nothing verifies the result, nothing
diffs meaningfully, and an agent editing raw CSS by string replacement is the least reliable thing
in this whole picture.

A declared token vocabulary turns the common change into a field edit — verifiable, diffable,
reviewable by a human in the console, and safe for an agent to make. It also gives SH183's default
stylesheet something to be built from instead of hardcoded values, and it gives the Universal
Editor something to offer besides text. Raw CSS stays as the escape hatch; the point is that it
stops being the interface. The open question is whose vocabulary — inventing one is a design
project, and adopting an existing convention is cheaper and more portable.

### What building the renderer found (SH247–SH249)

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

#### XV.19 A search results page needs the reader's query (SH259)

`{{#search q="handbook"}}` is a tag page, a curated collection, a "more about this" listing —
genuinely several of the things the 2018 regions did by hand. It is not what an author means by
*search*, because the term comes from the template and a results page's term comes from `?q=`.

The render model has no request in it, and that is not an oversight to correct casually. SH183's
entire resolution of the Block D non-goal turned on a layout being **content**: FSP's failure was
`getScriptEngine(String[])` without a `ClassFilter`, which put `Java.type()` and the raw
`HttpServletRequest` in scope of a template somebody could edit through the console. So the
question this task has to answer is not "how do I pass a parameter" but **what a template is
allowed to know about the request**.

The narrow answer is a `param` frame holding query-string values as strings — read-only, no
headers, no cookies, no request object — which covers search, pagination and a filter, and is
nothing like what was withdrawn. Naming it `param` and not `query` matters only because `query`
is already a helper.

Two things to settle before it lands. **Caching**: this would be the first render whose output
depends on something other than content, and while the preview route is `no-store` so nothing is
stored today, the answer wants writing down before preview is ever reconsidered as delivery.
**Escaping**: Handlebars escapes `{{param.q}}` by default, so the reflected-XSS risk is an
author's `{{{param.q}}}` — and unlike a front end, this codebase has SH192's write-time machinery
available, so a triple-stash over a request value can be *refused when the template is written*
rather than found in production. That is a better answer than a front end can give, and it is
worth doing as part of the same task rather than after it.

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

#### XV.15 A region's browser JavaScript is authored and never served (SH248)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. The two open questions were
> answered as: **inline**, because the page is `no-store` so a cacheable route buys nothing
> and adds a thing that can 404; and **once per region name**, because a layout may slot the
> same region twice and the markup is wanted in both places while the initialisation is not.
> One thing the implementation found: the script body has to be a jsoup `DataNode`, not text
> — as text, `if (a && b)` is escaped to `&amp;&amp;` and the script becomes a syntax error.

#### XV.16 The renderer and the digest compose the same page differently (SH249)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. The decision is a document:
> [specs/SH249-sections-and-regions.md](../specs/SH249-sections-and-regions.md). Three things to
> carry.
>
> **The prior question had a real answer and it was not "unify them".** Sections and regions differ
> on every axis that matters — who names them, by what key, whether they vary with the page or with
> the *type*, whether they are homogeneous. What joins them is that a region's template can iterate
> a page's section field (SH247's `getRelation`), so a section-composed page renders through the
> ordinary path and the renderer needs no knowledge of the section vocabulary. **Sections are
> content a template reads, not a level of the render model** — which is SH185's "the engine never
> infers ownership" one level out, and it is what stopped this from becoming a third path.
>
> **A fallback that is labelled beats a fallback that is guessed at.** SH169's composition stays,
> for a post with no layout binding or no friendly URL, and `source` says which question was
> answered. The alternative — fail where the digest used to answer — would have broken the SH99 loop
> for every site mid-build, which is every site until it is finished.
>
> **SH171's hazard is a class, not an incident.** Catching a teaching exception thrown by an inner
> `@Transactional` marks the caller's transaction rollback-only, and the failure surfaces at
> *commit*, nowhere near the catch. It cost eleven unrelated test errors here. Any new
> speculative call into a service that throws teaching exceptions needs `noRollbackFor`, and the
> tell is that the caller means to *handle* the refusal rather than propagate it.

#### XV.18 A stylesheet is not an HTML document, and the catalogue says it is (SH252)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth keeping.
>
> **It was filed as cosmetic and was a defect.** This section described the wrong editor mode and the
> `.html` sidecar. Reading the code before changing it found the third consequence: `ACE_HTML` is in
> the verify service's `MARKUP_WIDGETS`, so a `Theme`'s stylesheet was being **parsed as HTML** by the
> SH100 routes check and the SH101 digest. Worth generalising — a widget name is not decoration here,
> it is a *type* that several subsystems dispatch on, so "the closest thing seeded" was a wrong type
> declaration and behaved like one.
>
> **One of the six places needed no edit, and that is the return on SH191's rule.**
> `SIDECAR_WIDGETS` keys on the widget, so the projection started writing `.css` the moment the
> field's widget changed. Second time in this block that a decision placed in one spot fixed an
> untouched call site (SH279's `resolveReference` was the first) — which is the concrete form of the
> argument SH245 usually makes in the abstract.
>
> **The fix does not reach existing installs, and says so.** `ensure` never touches an existing
> post-type (SH152), so an instance that already created its `Theme` keeps the old widget. The
> alternative is a startup that rewrites somebody's model, which is worse; recording the limit is what
> keeps the changelog honest about what shipped.

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

### XVI.1 No CI workflow runs on the branch the work happens on (SH241)

Three workflows exist. `build.yml` (Maven build + Sonar) and `codeql-analysis.yml` are
`workflow_dispatch:`-only — no `push`, no `pull_request`. `console-check.yml` has
`push: branches: [main]` and `pull_request`, and the repository's default branch is **`2026.3`**;
`main` is a stale branch. So on a push to the branch everything is actually developed on,
**nothing runs**.

That matters more here than it would elsewhere, because SH182 exists precisely to catch the
class of defect nothing else can see. Block O's whole finding was that console defects answer
`200` and are invisible to Java tests, CLI tests and the MCP golden; the answer was a browser
walking the routes in CI. A gate that does not run on the branch is not a gate — it is a script
in the repository, and the block's most durable output has been inert since it shipped.

The evidence it is not hypothetical: picking up Block O's follow-ups meant building the console,
and the build was **already red** (§XVI.2). Nothing had reported it, because nothing had run.

The fix is a trigger, and the choice worth making deliberately is *which* jobs run on push
versus pull request — the console check packages a jar and drives Chromium, so it is a
pull-request and merge-queue gate rather than something to pay for on every push, while the
Java suite is cheap enough to run on both. What must not survive is the current state, where
the answer is "neither".

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

### XVI.7 The suite's mocks depend on dynamic agent attachment (SH250)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Two things worth keeping.
>
> **The hypothetical in this section's own last paragraph was already true.** It said the wiring
> "should be checked against what Spring Boot's parent POM already sets on `argLine` (and against the
> JaCoCo argLine, if that is ever added)". JaCoCo sets it *today* — the build prints `argLine set to
> -javaagent:…org.jacoco.agent…` — so `@{argLine}`, surefire's late replacement, was load-bearing
> rather than defensive: `${argLine}` would have silently replaced JaCoCo's agent with Mockito's and
> coverage would have quietly stopped being collected. Worth generalising: when a design note hedges
> about a condition, check whether it already holds before treating the hedge as future work.
>
> **Fixing a flake is worth more than its size suggests.** The change is a dozen lines of POM. What
> it buys is that a red run means something again — and the value was demonstrated immediately, since
> SH250's own fix was verified by the *absence* of a warning, which is only a signal in a log clean
> enough for an absence to be visible. That property is currently luck, not design: **SH271**.

### XVI.8 A normalization has to hold for the comparison, not just the file (SH253)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth keeping, because
> the design got one of them wrong.
>
> **The rule.** *Any* transformation a projection applies on the way out has to be applied to
> the remote document on the way **in**, or the two sides are compared in different domains and
> a value the projection cannot represent reads as an edit that never lands. The format now has
> one boundary (`projectedPost`, called through `projectRemote` by both commands) instead of one
> per normalization. SH145 had already reached this conclusion for `File` fields and stated it
> in a comment; nothing generalized it, so the next normalization repeated the mistake.
>
> **What the design got wrong.** It named "fingerprint the projected document rather than the
> remote one" as the real answer and the alternative as what a hurry would pick. Measured, that
> is half a fix: it turns a permanent `local-changed` into a permanent `remote-changed`. Worth
> remembering as a *shape* — a repair that moves which side looks wrong, while leaving the
> asymmetry that made either side look wrong, reads as a fix and passes the obvious test. The
> regression test pulls a third time for exactly this reason.
>
> **Why nothing caught it for four blocks.** No fixture in the suite had a CRLF in it, so every
> round-trip assertion ran where the normalization is the identity function. A rule stated in a
> comment, contradicted by a caller two files away, satisfied by every fixture: that combination
> is invisible to a suite of any size, and it is the same class as SH136's byte-stability lint
> (a defect twice-in-one-process comparison cannot see) and SH188's NULL column (a defect no
> test that constructs the entity in Java can see).

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

### XVI.13 A declared refusal is not checked for being the right refusal (SH258)

SH255's property is "the value comes back exactly, or the loss is declared". A **throw** satisfies
the second branch whatever it says, so all of these are indistinguishable to the guard:

- the refusal the format intends, naming the value and what to do about it;
- a refusal from three layers down that blames a symptom (`siteDir`'s trim, surfacing as
  `parsePost` complaining that a file disagrees with its own path);
- a `TypeError` from a bug in the check itself.

That is not hypothetical — the second is exactly what happened during SH257, and it is why that
defect had to be found by reading rather than by running.

The fix is small: a `DECLARED` entry should carry the refusal it expects — a pattern the thrown
message has to match — so "refused" stops being one outcome and becomes N. It is the same argument
SH80 makes for the HTTP surface, that a 4xx has to carry a `fix` rather than just a status, applied
to a test's own notion of an acceptable failure.

Worth stating the boundary, because it is easy to over-build: this is not a demand that every
error message be asserted verbatim. The entry only has to pin the part that identifies *which* rule
fired, which is the part a reader would use to tell a good refusal from a confusing one.

### XVI.14 The helper catalogue is five hardcoded lists (SH260)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth keeping.
>
> **What got consolidated was not the names but the *shapes*.** The obvious fix — expose the
> registered names and have the readers ask — solves the validator's list and nothing else, because
> the scanner does not need to know *what* is registered, it needs to know how a call is written
> (does the first token belong to the helper or is it a field?). So the registration declares a
> shape, and that is the piece that could not have been derived: it lives in the author's head at
> `register()` and was being reconstructed by two other classes from memory.
>
> **The dangerous convenience was the one not to provide.** `referencesIn(template)` with no shapes
> would be defaulting to "nothing is registered", which turns a forgotten argument into a false
> positive from the rule that exists to catch typos. Removing the overload is what makes the omission
> a compile error instead of a wrong finding — the same instinct as SH138 making a truncated change
> set unrepresentable rather than merely avoided.
>
> **Two of the new assertions were wrong before they were right**, and the reasons are worth
> recording because both are about *order*: the engine's list is alphabetical rather than in
> registration order (a total order per SH136, and registration order was the hand-written list's
> accident of history), and the refusal that exercises it needs a *parameterised unknown* helper,
> because `{{#form}}` is in `KNOWN_MISSING_HELPERS` and takes the SH247 branch instead.
>
> What was left — the capability set being discoverable only by getting a 400 — is **SH272**, ✅ shipped
> in turn: the signature travels with the registration and the context pack states it.

### XVI.15 A hash argument that is a field is not checked (SH261)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Two things worth keeping.
>
> **The exclusion that mattered was the subexpression, and it is skipped whole.** This section
> worried that `q=(concat A B)` would need parsing far enough to be excluded. It does not: tracking
> paren depth in the tokenizer keeps the subexpression as one token, and one look at its first
> character rejects it. The general shape — *make the thing you refuse to understand indivisible,
> then refuse it* — is cheaper than understanding it and cannot be wrong about the inside.
>
> **Whitespace-only splitting would have passed the tests for the wrong reason.** `q="two words"`
> cut in half yields `q="two`, which is not a path and is therefore dropped — the correct outcome,
> reached by accident, and it stops being correct the first time a subexpression appears. Six lines
> of quote and paren tracking removes the class instead of the instance. Worth noticing as a review
> habit: a test that passes because a broken input happens to fail a later check is not evidence.
>
> What this leaves is that the scanner is now a hand-written reader of three constructs of somebody
> else's grammar, with no statement of what it does *not* handle — **SH273**.

### XVI.16 A duplicate template title is silently the first one (SH263)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth carrying.
>
> **The open question in this section answered itself the other way.** It asked whether the write side
> should refuse instead, and worried that refusing would break importing a package that already
> contains a collision. Building the lint made the shape clearer: the two are not alternatives, and the
> write-side refusal needs a distinction the write path does not currently draw — interactive versus
> bulk. That is **SH277**, ✅ shipped in turn, and it was a better task for having the lint first — the
> distinction turned out to need no new mechanism, because the bulk path (SH68's import) writes through
> the repository rather than the post writer, so putting the refusal on the writer gets both halves.
>
> **Scoping mattered more here than for most rules.** A scoped run that saw one of two duplicates
> would report that the name is *unique*. Most false greens are an absence of information; this one is
> a positive wrong answer, which is why detection is instance-wide within the site even though
> reporting is subject-scoped.
>
> **The false positive was the design.** A template's own PUBLISHED row shares its id *and* its title
> with its DRAFT row, so counting rows would have reported every published template in every instance
> as a duplicate of itself — a rule that fires everywhere is one nobody reads. Keying by id is the
> whole implementation and it has its own test.
>
> One finding about the test rather than the code: the first version added the duplicate to a
> `PER_CLASS` fixture and broke two unrelated assertions in the same class. In a shared fixture every
> mutating test is an input to the others — SH239's confound, one class along.

### XVI.17 A transaction-bound cache is invisible outside a transaction (SH313)

SH311 binds its resolved templates to `TransactionSynchronizationManager`, which is what makes
staleness impossible: the cache cannot outlive the call, and a **writing** transaction is excluded by
a condition rather than by a convention.

The consequence nobody has looked at is the other side of that. A call path that renders outside a
Spring transaction — or inside a writing one — gets no cache, no warning and no test saying so. Today
`ShPageRenderService.render` is `@Transactional(readOnly = true)`, so the three real entry points are
covered by accident of that annotation rather than by anything that checks.

Two cheap instruments:

- **A test per entry point** (`/preview`, `/agent/render`, `?digest=true`) asserting the cache is
  actually active there, so a refactor that moves a transaction boundary fails rather than costing
  three times as much silently.
- **A signal when a render finds no cache** — a counter or a debug line — so the next path that
  appears announces itself instead of quietly paying 19 queries a page.

The second is the more valuable one, because the failure mode here is a performance regression that
looks exactly like normal operation.

### XVI.17 The benchmark measures one site's shape, and a real site is not this shape (SH312)

SH264's fixture is one layout, two regions, one theme, ten pages and **no helpers**. That was enough
to answer "is anything reused" — it was not — and SH311 has since made a sweep cost 6 queries a page
instead of 19.

It is not enough to answer *what does a real sweep cost*, because helpers are the part that varies
without bound: a listing region calls `{{#query limit=10}}`, which is a query per page **plus** the
rows it reads, and a section-composed page reads a post per `{{#getRelation}}` entry. Those reads are
per-*page* by nature — they depend on the page's own field values — so SH311's cache does not touch
them, and the 6 is a floor for the simplest page there is.

Worth extending the fixture with a listing region and a section-composed page, and recording both
numbers beside the current ones. Worth doing **after** SH311 rather than with it, because the cache
changed which number is the interesting one — and it will change again if anyone caches helper
results, which is a much harder question (a listing's rows are content, so a cache there is a
freshness decision rather than a resolution one).

### XVI.19 `ShExchangeException` has no kind (SH267)

SH189's teaching errors classify by testing the message:

```java
if (lower.contains("manifest")) { … }
if (lower.contains("zip") || lower.contains("escapes")) { … }
if (lower.contains("no site")) { … }
```

That works, and it is the shape this codebase refuses everywhere else. The information — *which*
failure this is — exists at the `throw` and is discarded there, so it has to be reconstructed by
guessing at prose. Two consequences follow, and the second is the one that will bite: a message
reworded for clarity silently changes an HTTP response, and a new failure mode added to the codec
lands in the catch-all with no sign that anybody should look at it.

`ShExchangeException` is a bare `RuntimeException(String)` with fifteen call sites across
`ShExchangePackageCodec`, `ShSiteExportService` and `ShSiteImportService`. Giving it a kind — a
nested enum, or a subclass per failure mode — turns the classifier into a `switch` and makes the
messages presentational again.

It is a separate task from SH189 because of blast radius: the same exception is thrown on the
**export** path, which has its own callers and its own tests, and a task that quietly widened from
"make import teach" to "restructure the exchange layer's error model" is the kind that gets abandoned
half-done.

### XVI.20 Import is not on the agent path, and a package is a file (SH268)

No `shio import`, no `batch` op, no MCP tool. So an agent can create a site, a model and a hundred
posts from nothing, and cannot accept a package a human hands it — which is the cheapest possible
starting point and the form every first-party template ships in.

The CLI half is now straightforward: SH189 made the endpoint reachable by a credential client, so
`shio import <file>` is a multipart POST and a report.

**The MCP half is a real design question, and this is the interesting part.** An SH68 package is a
**zip**. A client with no filesystem can neither read one nor hand bytes over JSON-RPC. Three
options, none free:

- **A URL the server fetches.** This is SH101's request-forgery surface argued a second time, and it
  would need the same treatment — off by default, an origin allow-list — for a capability far less
  central than a render digest.
- **A base64 body.** A megabyte of tokens for something a shell does in one line, which is P3's
  argument against it and also SH114's argument about `shio_remember` running the other way.
- **Say so.** The manifest reports capabilities precisely so a client learns what it cannot do
  *before* promising a user something. "Import is CLI-shaped" is a legitimate answer, and an
  explicit flag is better than an MCP client discovering the gap by not finding a tool.

The third reading is the one to test first, and it should be tested against **SH186** — which is the
same question in general form: what an MCP-only client can and cannot finish. If SH186 concludes
that a filesystem-free client is a supported shape, this becomes a real gap; if it concludes such a
client is expected to lean on a companion CLI, this is documentation.

### XVI.21 A frame is evidence for one incident kind and absent for three (SH269)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Two things worth keeping, one of which is a
> testing judgement rather than a design one.
>
> **The absent frame became informative.** Four of the five recorders pass one; the webhook's
> give-up branch does not, because delivery completed and the *subscriber* rejected it — there is no
> exception of ours and nothing in Shio's code to point at. SH190 set that rule as a technicality
> about honesty; here it turns into signal, because the presence of the field now distinguishes "we
> failed" from "they refused".
>
> **A source lint beat three integration fixtures, and the reasoning generalises.** Reproducing a
> failing webhook dispatch, a corrupt image and a scheduled publish that throws is three expensive
> fixtures each asserting one line — and none of them tests what actually goes wrong. What goes wrong
> is a `catch` that records the message and drops the throwable it is standing on: invisible until
> somebody reads a diagnostics row and finds nothing there. So the guard walks the source for
> `incidentLog.record(...)` inside a `catch` with no frame, and it was verified the only way a lint
> can be — by reverting a call site and watching it name the file. Same family as
> `ShByteStabilityLintTest` and `ShCsrfExemptionLintTest`: when the defect is an *omission at a call
> site*, the cheap correct test reads the call sites.
>
> The gap the lint cannot see is a `catch` that records nothing at all — **SH270**.

### XVI.22 A swallowed exception leaves no frame, and no incident either (SH270)

SH269's lint can only see the recorders that exist. The blind spot is a `catch` that records
*nothing*, and there are several, most of them correct:

- `ShAgentIncidentStore` swallows and logs — deliberately, because a diagnostics write must never
  turn a webhook timeout into a 500 (SH144's rule).
- `ShPageRenderService.bindingsOf` returns `Map.of()` for an unparseable `postTypeLayout` (SH183).
- `ShRenderModel.fieldsOf` returns no fields for a post whose `data` is not an object.

The last two are the interesting ones, because both mean *the content is wrong and the page looks
empty* — the failure mode this whole block exists to make visible — and both are currently
invisible everywhere. Neither is a bug: degrading rather than failing is right for a renderer, and
SH99's lint is the stated place those defects get reported. The question is whether "the lint would
catch it if anybody ran the lint" is good enough for a condition the renderer has already observed
at request time.

**What this must not become is more recording.** Logging every handled condition would fill the SH103
window with things that are working as designed, which is exactly what that task refused when it
declined to record 4xx. So the deliverable is a *decision* about which degradations are worth an
incident, and the bar should probably be: the content is malformed in a way the model cannot express,
and the request succeeded anyway.

The lint shape is available if the answer is "some of them": a `catch` that neither records nor
rethrows could be made to name a reason inline, the way `byte-stable-exempt` does for SH136. That
turns a silent decision into a written one without adding a single log line.

### XVI.23 A warning printed on every run is a warning nobody reads (SH271)

SH250's fix was named by the build's own output — *"add Mockito as an agent to your build"* — on every
run, for as long as anyone can remember. What turned it into a task was a **flake**, not the message.
That is the whole finding: the message was doing nothing.

Two others are in exactly that state right now:

- `logback` on every context start: *"This appender no longer admits a layout as a sub-component, set
  an encoder instead."* A deprecation on a timer, like the Mockito one — it will stop being a warning
  and start being a config error.
- The JVM: *"Sharing is only supported for boot loader classes because bootstrap classpath has been
  appended."* Probably harmless, and now caused by the agent SH250 added, which is worth knowing
  rather than shrugging at.

The work is to fix what is fixable and, for whatever genuinely is not, silence it **deliberately** —
and the second half matters as much as the first, because an undecided warning is indistinguishable
from an unnoticed one.

The argument is not tidiness. SH250 was verified by the *absence* of a warning, and an absence is only
a signal in a log clean enough for it to be visible. That property was luck this time. Making it
design is what this buys, and the beneficiary is whoever reads the build the day something new starts
warning.

### XVI.25 The scanner is a hand-written parser of somebody else's grammar (SH273)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth keeping.
>
> **The table found a defect the moment it was written**, which is the argument for it in one line:
> `{{& TEXT}}` — unescaped output, the same reference as a triple stash — was skipped as though it
> were a directive, so a real field was silently uncovered by the lint. Nothing was failing. The
> defect was found by *enumerating*, which is exactly what this section predicted and exactly why
> "we would have noticed" is not a plan.
>
> **Three dispositions, not two, and the third carries a reason.** `SKIPPED` means "not a field";
> `UNREACHABLE` means "could be a field, and something else already stops it mattering" — three rows
> rest on SH183's refusing `TemplateLoader` making partials fail to compile. That second kind is a
> claim about another part of the system, so it rots when that part changes and has to be written
> down rather than assumed. A test enforces that every such row says why.
>
> **Verifying the fix corrected the fix.** It was made in two places — the directive skip-list and
> `strip` — and reverting the first showed it had never mattered, because `strip` runs earlier and
> the branch was already unreachable. The comment now says so. That is three changes in this stretch
> whose halves were not both load-bearing, each caught by reverting one and watching the test rather
> than by reading the code. Cheap habit, high yield.
>
> The AST alternative is now cheap to evaluate and still unevaluated — **SH274**.

### XVI.26 The AST question is answerable now, and was answered (SH274)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. The premise holds; three things came out of
> checking it that would not have come out of continuing to believe it.
>
> **The reality was worse than the premise claimed.** The inherited belief was "flat, no scope".
> `collect` is actually *deduplicated*: four references to `TEXT` at three different scopes return
> `[TEXT]`. So the belief was directionally right and quantitatively wrong, which is the most common
> way an inherited premise misleads — it stays true enough to keep, while nobody knows how much
> margin there is.
>
> **Checking a premise can validate an unrelated decision.** `collectReferenceParameters()` returns
> the hash *path* and not the hash *literal* — the library drawing SH261's line in exactly the same
> place, independently. That is stronger evidence for SH261 than its own tests, because it comes from
> somewhere that had no knowledge of them. Worth expecting: reading a dependency properly tends to
> settle more than the question you went in with.
>
> **The answer is enforced, not recorded.** Four assertions, a version number, and a failure message
> that says to reopen the task. A design note saying "we checked and it was fine" is a habit with a
> date on it; an assertion is a tripwire. That difference is the whole deliverable.
>
> The same shape of unenforced claim exists for several other dependencies — **SH275**.

### XVI.27 A dependency's premise is asserted for one library and assumed for the rest (SH275)

SH274 converted one inherited belief about somebody else's code into four assertions with a version
number, and the conversion paid twice: the reality was worse than the belief (deduplication, not just
missing scope) and one API independently confirmed SH261's rule.

The same kind of claim is load-bearing elsewhere and enforced nowhere. A partial list, from the
comments they currently live in:

| Claim | Where it matters | Enforced? |
|---|---|---|
| Nashorn's classes are not on the classpath | SH183's whole non-goal | **yes** — `ShRenderNoScriptEngineTest` |
| the default `TemplateLoader` reads the classpath | why ours refuses everything | no |
| the default resolver chain includes `JavaBeanValueResolver` | why the context is map-only | no |
| Mockito self-attaches without a `-javaagent` | SH250's flake | no (and now false) |
| `Map.copyOf` iterates from a per-JVM salt | SH136's whole lint | no |
| the MCP Java SDK is Jackson-2 / Boot-3 | why SH75 is hand-rolled | no |

Every row is a fact about code this project does not own, and a version bump can change any of them
silently. The one that is enforced is enforced because SH183 judged it worth a test — which is exactly
the judgement to make for the rest, rather than doing all of them.

**The bar should be: does the premise expiring change behaviour, or only tidiness?** `Map.copyOf`
becoming order-stable would make SH136's lint pedantic but harmless. The default resolver chain
changing would silently put the persistence model back in template scope, which is the liability the
whole of Block P was built to avoid — and nothing would fail. That one is the next candidate, and
`ShRenderNoScriptEngineTest` is the model: it already asserts two of the three defaults, so the third
is a method rather than a new file.

Worth noting the entry that has already gone stale: SH250 made "Mockito self-attaches" false. Nothing
noticed, because nothing was watching — which is the argument in miniature.

> ⏳ **The two Handlebars rows are done** → [CHANGELOG.md](CHANGELOG.md) Block P. Three findings.
>
> **The gap was the premise, not the behaviour.** The suite already proved *our* engine walks no bean
> and refuses every partial. It never proved an *unconfigured* one does the dangerous thing — so a
> library that became safe by default would turn both guards into dead weight and both comments into
> lies, with nothing failing. That asymmetry is what "asserted for one library and assumed for the
> rest" actually means in practice: the guard is tested, the reason for it is not.
>
> **Measuring corrected an existing assertion's meaning.** `beanPropertiesAreNotResolved` asserts
> `[{{secret.value}}][{{secret.class.name}}]` → `[][]`, which reads as both routes closed by
> `MapValueResolver`. In fact `class` is blocked by the library itself, on a default engine. So half
> that assertion is evidence for the guard and half is evidence for Handlebars. Pinned separately
> now — and worth generalising: a compound assertion hides which half is load-bearing.
>
> **The evidence for the loader premise is the error message, not the failure.** A default engine
> fails `{{> application}}` with a message naming `/application.hbs` — it is not refusing to look, it
> is failing to find. "It throws either way" would have been a false confirmation.
>
> Two rows are deliberately not assertable and say so: `Map.copyOf` cannot be caught behaviourally at
> all (SH136's own finding, which is why that guard is a lint), and the MCP SDK is a premise about a
> dependency this project does not have — **SH276**.

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

### XVI.30 The renderer resolves by title in an unspecified order (SH278)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. It was filed as one `order by` and was two
> defects; three things worth keeping.
>
> **The undocumented half was the serious one.** The ticket was about duplicate titles. Reading the
> method showed it also read *both states with no DRAFT preference*, so a preview could render the
> published layout while the curator edited the draft. Worth noticing as a habit: a task that names one
> symptom of an unspecified behaviour is a reason to read the behaviour, not just to fix the symptom.
>
> **"None" is a worse failure than "a second copy".** SH247 extracted `preferDraft` out of the listing
> helpers explicitly because a duplicate would be a chance to get it backwards. The extraction landed
> and nobody asked whether the *other* resolver needed it — it had no copy at all. So the rule to carry
> is stronger than "one writer per verb": when a rule is extracted, the question is which callers
> should now be using it, not only which copies should be deleted.
>
> **The verification made the argument better than the fix did.** Reverting the change failed the
> published-render assertion and left the draft-preview one passing, because on H2 `findFirst` happened
> to return the draft. The more serious half of the defect was *unobservable on the engine the suite
> runs against*. That is the case for deciding an order rather than discovering one, and it is why
> SH279 sweeps for the same shape elsewhere.

### XVI.32 A published render now requires published templates (SH280)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Three things worth keeping.
>
> **The urgency check came back negative, and that is a result.** This section's third item worried
> that if the SH68 import or SH92 blueprints wrote templates without publishing them, every imported
> site would arrive broken. Import is *faithful* — it preserves each post's own state from the package
> — and no first-party blueprint ships a `PageLayout`. So the exposure is an agent-built site rather
> than an imported one, and the task shrank. Worth doing that check first every time: it was the
> cheapest of the three items and it determined the size of the other two.
>
> **The second condition is what makes a lint usable.** `unpublished-layout` fires only where the site
> has *published pages* of the bound type. Without it the rule would report every site mid-build,
> which is how a warning becomes noise. Stated generally: **the difference between work in progress
> and something wrong is almost always another condition, not a stricter first one** — SH193's unused
> templates needed the same shape, and so did SH270's question about which degradations deserve an
> incident.
>
> **A distinct type beats a better sentence.** `UnpublishedLayoutException` is a 409 with its own
> `fix`, not a reworded 404, so a caller can branch on it. The alternative — one exception whose text
> explains both cases — reads fine to a human and gives a program nothing.
>
> The same distinction is unmade at the region and theme levels, where the renderer degrades rather
> than refusing — **SH281**.

### XVI.33 A region and a theme have the same unpublished problem, and worse (SH281)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Two things worth keeping.
>
> **A wrong diagnosis is worse than a missing one, and this section is the clearest case of it.** An
> unfilled slot said "no Region post" while the region sat in the console as a draft. A silent failure
> sends somebody looking; a confident wrong answer sends them to create a duplicate. That is the same
> asymmetry SH189 found in import (a bodiless 400 versus four classified ones) and SH280 found in the
> layout refusal, and it is now three for three: **when a message can be wrong rather than absent,
> fixing it beats adding one.**
>
> **The shape of the fix follows from the level's contract.** A layout that cannot resolve refuses,
> because there is no page without one; a region and a theme degrade, because the rest of the page is
> still worth serving. So these two get a marker and a finding rather than an exception — and the
> finding's `fix` says explicitly that neither refuses the render, because a reader who has just met
> SH280's 409 would otherwise expect one. Matching the instrument to the level's contract is the part
> that took thought; the code is small.
>
> A separate attribute rather than a value on the existing one, so SH169's rule survives a third state
> — missing, empty, unpublished must stay distinguishable in the DOM and therefore in the digest.

### XVI.34 `mvn compile` reports success on code that does not compile (SH282)

Measured rather than reasoned about, and **the first diagnosis filed here was wrong** — worth leaving
that on the record, because the wrong version was plausible and would have produced the wrong fix.

**What was filed:** ecj's proceed-on-error, on the strength of the message
`java.lang.Error: Unresolved compilation problem`, which is characteristically Eclipse's. Proposed fix:
turn `proceedOnError` off or move to javac.

**What is true:** this build already uses plain javac — there is no ecj configuration anywhere. The
sequence is:

1. the **IDE** (Eclipse JDT, via the VS Code Java extension) compiles into `target/classes` on save,
   and for broken source it emits a class with the error embedded rather than no class at all;
2. that `.class` is therefore *newer* than the `.java`, so `maven-compiler-plugin`'s staleness check
   reports **"Nothing to compile - all classes are up to date"** and skips the module;
3. `mvn compile` exits 0 on source that does not compile;
4. the embedded `Error` surfaces at runtime, in whatever test calls the broken member — which is how
   one missing accessor read as 53 errors across five unrelated test classes.

Reproduced deliberately: a call to a non-existent method gives BUILD SUCCESS on `compile` and
`cannot find symbol` on `clean compile`. `-Dmaven.compiler.useIncrementalCompilation=false` does **not**
help, because what short-circuits is the staleness check rather than the incremental mode.

**The fix is about where the IDE writes, not about the compiler.** Giving the IDE its own output
directory is the real answer and needs checking against what the extension supports; failing that, the
rule is that a verification run needs `clean`. The generalisable lesson is smaller and sharper than the
one filed: **"nothing to compile" is not the same as "nothing is wrong", and a build that can skip work
can skip the work that would have failed.**

### XVI.35 A stale `target/` misreports the suite in the other direction (SH283)

> ✅ **Shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. Two things worth keeping, and the first is a
> correction to this section as originally written.
>
> **The accumulating directory was the second link, not the cause.** This section blamed
> `target/surefire-reports/` for accumulating, which is true. The reason anybody was reading that
> directory is that **`-q` suppresses surefire's `Results:` summary** — it is a Maven INFO line, so
> quiet mode deletes the only authoritative total, and summing files is what you do next. Fixing the
> visible link would have left the cause in place: a future measurement would have found some other way
> to be wrong. Worth generalising — *when a workaround produces a wrong answer, the bug is usually in
> whatever made the workaround necessary.*
>
> **The inflated numbers were corrected rather than dropped.** 977 claimed, 974 real. Nothing was
> failing, so the temptation is to treat it as cosmetic — and a test count is offered as **evidence**,
> which makes "it was arithmetic over a directory" a defect in the evidence rather than in the tests.
> The earlier commit messages carry the wrong figure and the changelog now says so.
>
> The fix is a command in `agents.md`, verified in both directions, sitting beside SH282's because they
> are one shape: an artefact of an earlier command changing what a later one appears to say.

### XVI.36 Nothing in CI would have caught either measurement defect (SH284)

SH282 and SH283 are both artefacts of a **local working tree**: an IDE writing compiled classes into
`target/classes`, and a reports directory accumulating across hand-run commands. Neither can happen in a
job that starts from a clone.

That is the strongest argument yet for **SH241** — the open task that no workflow triggers on the branch
the work happens on — and it changes that task's *justification* rather than just adding weight to it.
SH241 was filed as "the Java suite never runs automatically". The sharper form is:

> **A clean-room build is the only thing that can tell a real green from a local one.**

Filed separately because that reframing has consequences SH241 does not currently carry:

- **The workflow should publish surefire's summary as the run's headline**, so a count is never
  re-derived by hand. SH283 exists because a number was reconstructed; a CI job that prints the
  authoritative line removes the reconstruction rather than making it more careful.
- **SH282 still wants a local guard.** CI protects the *branch*; it does nothing for the person about to
  commit, who will see the same false green and only learn from a failed pipeline. That is a slower loop
  than the one SH282 is about, so "CI will catch it" is not a resolution.

Worth stating the uncomfortable part plainly: this block has been verified almost entirely by
hand-running Maven in a tree an IDE is also compiling into. The findings hold — they were reproduced
deliberately — but the *confidence* they were reported with was higher than the instrument deserved
until SH282 and SH283 were understood.

### XVI.37 `ShWidget` still carries fields from a UI that was deleted (SH285)

Every seeded widget sets `implementationCode` to `template/widget/<name>.html`. **That directory does
not exist in the repository** — the templates went with the Thymeleaf UI Block E retired — and nothing
reads the column.

It is nevertheless persisted, returned by `GET /api/v2/widget`, and **writable** through
`ShWidgetAPI`. That is the part worth fixing rather than tolerating: a settable field that cannot
affect anything invites the reasonable inference that a widget is extensible by pointing it at a
template, which it is not. Dead data that is read-only is clutter; dead data that is *writable* is a
false affordance.

Noticed while seeding SH252's widget — where the choice was to match the existing inert pattern rather
than invent a convention, since a lone widget with a different-looking value would read as meaningful.

The task is deliberately **an audit rather than a drop**, because the neighbouring field asks the same
question and may answer differently: `className` is set to `com.viglet.shio.widget.ShAceEditorWidget`,
and whether those classes still exist — and whether anything dispatches on them — has not been
checked. One of the two may be live. So: establish what `ShWidget` still carries that is read by
something, remove the rest with a Liquibase drop, and narrow the API so the removed fields cannot be
set.

Worth pairing with SH275's habit: both are about claims a codebase keeps making after they stopped
being true, and the cheap version of both is to look rather than to reason.

### XVI.40 The CLI cannot read a post (SH288)

`shio` can resolve an address, digest a page, verify a site, list changes and print a context pack. It
cannot show one post's content.

For a *tree* that is correct and deliberate: `shio pull --content` puts every post on disk, where
`Grep` and `Edit` are the cheapest tools an agent has (P4), and a `read` command would be a worse
version of a file. The honest question is whether "show me this one address" is a real gap or a
misapplied API habit — a human debugging an agent's work does type it, but they can also open the file.

What that argument does **not** cover is SH262's `impact`, because the projected file holds none of it:
the blast radius is computed from the site's bindings, not stored in the post, so there is no file to
read it out of and a terminal user has no path to it at all. So the smaller and better-justified shape
may be `shio impact <address>` — one question the files genuinely cannot answer — rather than a general
`read` that duplicates the projection. Decide which before building either.

### XVI.43 A helper's signature is prose beside the code that implements it (SH291)

SH272 put the call form at the registration — the one place that knows both the name and the shape,
which is SH260's rule applied to a third fact. It is still **a string somebody has to keep true**.

Nothing fails if `search` gains a `folder=` parameter and the signature is not updated. The failure
would be a context pack teaching an incomplete call, and the author would discover it by writing a
template whose parameter is silently ignored — which is worse than the 400 this task replaced, because
an ignored hash key is not an error at all.

Three candidate instruments, and the obvious one is unavailable:

- **Reflection** cannot answer it. A hash key is read by `options.hash("q")` inside a method body;
  there is no signature, annotation or type that carries it. Finding it means reading bytecode or
  source.
- **A source lint**, in the shape `ShAgentIncidentFramesTest` and `ShByteStabilityLintTest` already
  use: every `hash(options, "X"` (and `options.hash("X")`) inside a helper method must appear in that
  helper's registered signature. Cheap and honest, and brittle in the usual way — it reads the source
  as text, so a key built from a constant escapes it.
- **Declare it unenforced.** SH275's pattern: a premise written down as unenforced is not the same
  defect as one nobody has noticed, and there are five helpers whose parameters have not changed since
  they were written.

Worth choosing deliberately rather than defaulting to the lint because it is available.

### XVI.44 The pack states the helpers and nothing states the model they run against (SH292)

An author now learns from the context pack that `{{#query folder= type= limit=}}` exists. Nothing
tells them:

- that a top-level `{{TEXT}}` is the **page's** field while the same reference inside the block is the
  **listed post's** — the scoping SH251's scanner exists to track, and the single most likely thing to
  get wrong;
- that a row carries `title`, `link`, `summary`, `date` (`ShRenderHelpers.ROW_KEYS`);
- that `post.`, `site.`, `theme.` and `render.` are reserved frames.

The asymmetry has a real cost. SH251's `template-unknown-field` is an **error** at top level and only a
**warning** inside a `{{#query}}` block, because the rows are whatever the helper found and the engine
cannot prove what they hold. So the place where the lint is weakest is exactly the place the author
now has the least documentation for — the pack tells them the block exists and nothing about what is
in scope inside it.

The content already exists in one place each: `ROW_KEYS` (SH260 made it the source) and the scanner's
frame list. What needs deciding is **where**, and this is the point where "add it to conventions"
stops being obviously right: it would roughly double that line for every rendering instance, and P3 is
a budget rather than a preference. The alternatives are a `shio://` resource (discoverable, MCP-only),
a section of the pack somebody opts into, or a documentation link — which this codebase has so far
refused on the grounds that a link is not an appliable unit (P8).

### XVI.45 The reference model has thirteen types and three are checked (SH293)

`ShRenderModelSourceTest` compares `PageLayout`, `Region` and `Theme` against
`import/post-types.json`, because those are the three SH183 transcribed and the three SH277 found a
defect in.

That file declares ten more: `Text`, `Photo`, `Video`, `Quote`, `Links`, `File`, `TextArea`,
`Article`, `Alias`, `FolderIndex`. The question the new test answers for three of them is unanswered
for the rest, and it is really two questions:

- **Which are still provisioned by this system?** `File` is (SH38 creates it on first upload), so it
  can and should be compared the same way — its `FILE` field is what the static-file subsystem writes
  into, and a name drift there breaks uploads rather than rendering.
- **Which are names in `ShSystemPostType` with no creator at all?** `Alias`, `FolderIndex`, `Quote`
  and others are declared as constants and nothing builds them. That is `ShWidget`'s problem (SH285)
  one class along: dead declarations that read as capabilities.

So the task is an **audit that produces either a comparison or a deletion for each**, not an
extension of the test's type list. Extending it blindly would pin ten types this codebase does not
create, which is a test asserting that a file matches itself.

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

### XVI.47 Nothing announces the drift until a template is already broken (SH295)

SH289 reports `untitled-template`, which is a finding on a **post**: it fires once somebody has
already written a template that cannot be addressed. The drift itself — a render post-type missing a
field the shipped model declares — is invisible until then, so an instance that has run `ensure` and
written nothing looks exactly like a healthy one.

The reason it is reported that way is a real constraint, not an oversight: a post-type has no SH79
address, so it cannot carry a `verify` finding, and SH143's scoping has nowhere to put one. That
constraint has not gone away, so the answer is not "add a finding".

Two plausible homes, with the trade each carries:

- **The context pack's `conventions`.** An agent reads it before writing anything, which is exactly
  the moment this matters, and SH272 has just established the precedent for an instance-specific
  render fact living there. The cost is P3: every session pays for a line that most instances do not
  need — though the same conditional test SH272 uses (only when the instance has the render model)
  would narrow it to those that could be affected.
- **A startup log line.** Free, and nobody reads logs. That is SH103's own argument against a
  log-tailing tool, applied to ourselves.

Worth settling alongside: whether `GET /api/v2/render/post-types` should report the drift beside each
type. That call already answers "can this instance render", and the caller asking it is precisely the
one who needs to know.

### XVI.48 A PER_CLASS fixture has hidden three defects, and nothing warns (SH296)

Three times now, a test class with `@TestInstance(PER_CLASS)` has had one test's writes make another
test's assertion vacuous:

- **SH239**: a benchmark pair wrote into the folder another pair measured, so one ratio read 26.2×
  or 11.8× depending on order.
- **SH272**: a test asserting "this instance has no templates" was really asserting what ran first,
  because the render post-types are created on first use by anything in the JVM.
- **SH289**: an additive-only assertion survived a mutation that made the code overwrite every field,
  because a sibling test had already repaired the fixture, so `reconcile` found nothing to do and
  wrote nothing.

Each was found by accident. The third only surfaced because the fix was verified **by mutation**
rather than by a green run — which is the strongest argument that the other two were not the last.

Three instruments, and they are not equally good:

- **A convention: a mutating test forces its precondition rather than creating it once.** That is
  what SH289 ended up doing, it is cheap, and it makes the test independent of order without any
  machinery. Its weakness is that it is a habit, not a check.
- **`@TestMethodOrder(Random)`.** An order dependence would fail loudly — sometimes. A test that
  fails on some runs is a flake, and SH250's lesson is that a flake is worse than a break because it
  teaches people that red might mean nothing.
- **A lint** flagging a `PER_CLASS` class whose tests write through a shared service. Precise in
  principle, and "writes through a shared service" is most of this codebase's tests.

The likely answer is the first plus writing it down — the other two cost more than the defect does.

### XVI.52 A listing's rows are another post-type (SH305)

SH303 sends the **page's** post-type with each selection and the console labels fields from its
schema. That is right for the common case — a layout interpolating the page's own fields, which is
what `<body>`'s `data-shio-post` and `data-shio-post-type` describe.

A `{{#query}}` or `{{#getRelation}}` block renders rows of *other* posts. SH185 already handles
ownership there: each row carries `sh-post="{{id}}"`, and the nearest-ancestor walk finds it. The
**type** does not travel, so a row's field is labelled from the page's schema — and when that schema
has no such field the label falls back to the field name, which is indistinguishable from a
post-type that simply declares no labels. Silent, and wrong in the direction that looks fine.

Two candidate fixes, and the obvious one is not clearly right:

- **Emit `data-shio-post-type` per row.** The renderer knows it (the row came from a real post), and
  it is bytes on every listing row of every page, paid by every visitor, for a case only the editor
  uses. That is SH121's trade about ids, and it went the other way.
- **Resolve the type from the post id in the console.** It has the id already (that is what a save
  needs), so this is one fetch it is going to make anyway when editing begins.

Worth measuring rather than choosing by taste.

### XVI.53 A delivery check proves a route serves and never looks at what it served (SH302)

`shio verify --delivery` (SH141) fetches each published page and reports `route-not-served` on a bad
status. The body is discarded.

Since SH299 that body can be counted for markers with one jsoup parse, turning *"the front end
answered 200"* into *"the front end answered 200 with a page that is missing a region"* — a much
stronger claim, and arguably the one a delivery check exists to make.

Two things stop it from being obvious:

- **It only applies to Shio's own `/preview`.** An external Next.js front end renders its own markup
  from the CDA and carries no `data-shio-*` at all, so the check would silently cover nothing for
  precisely the deployments SH141 was built for — the "a check that covered nothing while answering
  ok" failure that task itself warns about.
- **A marker in *delivered* HTML is a stale-cache signal as much as a content defect.** The CMS may
  have been fixed since; what is being served is old. That is a different finding with a different
  fix, and reporting it as the content defect would send somebody to edit a post that is already
  right.

So the question to settle first is whether the honest form is a delivery *finding* at all, or a note
saying which pages were checked this way and what was seen.

### XVI.53 A delivery check proves a route serves and never looks at what it served (SH302)

`shio verify --delivery` (SH141) fetches each published page and reports `route-not-served` on a bad
status. The body is discarded.

Since SH299 that body can be counted for markers with one jsoup parse, turning *"the front end
answered 200"* into *"the front end answered 200 with a page that is missing a region"* — a much
stronger claim, and arguably the one a delivery check exists to make.

Two things stop it from being obvious:

- **It only applies to Shio's own `/preview`.** An external Next.js front end renders its own markup
  from the CDA and carries no `data-shio-*` at all, so the check would silently cover nothing for
  precisely the deployments SH141 was built for — the "a check that covered nothing while answering
  ok" failure that task itself warns about.
- **A marker in *delivered* HTML is a stale-cache signal as much as a content defect.** The CMS may
  have been fixed since; what is being served is old. That is a different finding with a different
  fix, and reporting it as the content defect would send somebody to edit a post that is already
  right.

So the question to settle first is whether the honest form is a delivery *finding* at all, or a note
saying which pages were checked this way and what was seen.

### XVI.52 Three markers exist and nothing lists them (SH300)

The rendered DOM carries nine `data-shio-*` attributes: `region`, `region-post`, `region-missing`,
`region-unpublished`, `theme`, `theme-missing`, `post`, `prop`, `post-type`. They are defined as
constants in two classes, explained across four changelog entries, and consumed by the Universal
Editor bridge, by anyone reading preview source, and — since SH299 — by the digest's
marker count, which reads three of them out of `ShRegionComposer.DEFECT_MARKERS`.

There is no single place that says what they mean. [SH185's protocol
document](specs/SH185-universal-editor-protocol.md) covers the editor's four and predates the rest.

This is exactly the gap SH272 closed for helpers, on the other side of the renderer: **the rendered
DOM is a contract**, it has grown by three attributes in two blocks, and the only way to learn it is
`git log`. The consumer is different, though — a front-end developer or an editor implementation
rather than an agent mid-session — so this probably belongs in the protocol document rather than in
the context pack, which is a budgeted per-session read.

The part worth copying from SH273 is the **test**: a table asserting that the constants in the code
and the rows in the document agree, because a hand-kept list of what a renderer emits drifts
silently, and the failure direction is under-documentation nobody notices.

### XVI.54 Two editor scripts, two vocabularies, both served (SH304)

- `shio-app/src/main/resources/render/shio-editor.js` — SH185's, 170 lines, **injected** into Shio's
  own preview for `?editor=true`, reads `data-shio-*` written by the renderer.
- `shio-app/src/main/resources/universal-editor/shio-editor.js` — 550 lines, **served** at a path the
  security config permits, for a front end that includes it itself, reads `data-sh-*` written by hand
  by that front end's author.

Two implementations of one protocol against two attribute vocabularies, and only the first is
specified. This is P10's warning about a second contract, inside a single feature — and the
unspecified one is the case SH185 explicitly leaves open: *"an external front end has to emit the
annotations itself."*

Three ways out, and they are not equivalent:

- **Teach the old script the `data-shio-*` names.** Cheapest, one vocabulary, and it silently breaks
  any front end already annotated with `data-sh-*`.
- **Emit the annotations from `@viglet/shio-react-sdk`,** so a Next.js project produces the same DOM
  Shio's own renderer does and the injected script works unchanged. The answer SH185 gestures at, and
  much the most work — but it is the one that makes the *rendered DOM* the single contract, which is
  what SH300 just wrote down.
- **Delete the old script** and declare external visual editing unsupported until the SDK lands.
  Honest, and a capability regression for whoever is using it.

Worth settling before either file is touched again, because every edit to the pair widens the
distance between them.

### XVI.55 Nothing tests the bridge script, in any language (SH306)

`render/shio-editor.js` is 170 lines implementing one side of the SH185 protocol, and it has **no
test**. `ShEditorBridgeTest` asserts the *injection* — that the script tag appears for
`?editor=true`, not otherwise, with the parent origin on it — and stops at the file boundary. SH303
changed three functions inside it and the only check was `node --check`, which proves it parses.

What is worth testing is pure and small:

- `labelOf` / `describe` — the shape of the message the console consumes.
- `ownerOf` — the nearest-ancestor walk, which is the rule SH185 states as load-bearing and the one
  a refactor would most plausibly break.
- The origin guard, which must never accept `"*"` and never post to an unexpected parent. That is a
  security property currently guaranteed by reading the file.

The obstacle is structural: it is a browser IIFE served as a static resource, with no module
boundary. Two ways out, and they cost different things — a DOM harness (`jsdom`, in the
`shio-react` workspace where a test runner already exists, importing the file as text and evaluating
it), or restructuring the script to expose a testable core, which changes a file that is
deliberately dependency-free and inert when not framed.

Worth deciding now, because the next change to it will be exactly as unverified as this one.

### XVI.56 A finding's address is a string, and nothing checks it resolves (SH309)

Every teaching message in `verify` is assembled by concatenation: SH308 puts `post:blog/l-article`
into one, `dangling-reference` puts a URL into another, SH297 names a theme title, SH289 names an
endpoint. Where they are asserted at all it is with `contains("post:")`.

The failure mode is specific and this repository has already had it once: SH172's `id:` fallback
lives in `ShAddressResolver.addressOf` precisely because an address was being built in three places
and one of them was wrong. A finding that names an address which does not resolve is worse than one
with no address, because the reader stops trusting the rest of the report.

The instrument is cheap — walk a report's findings, extract anything matching the SH79 grammar from
`message` and `fix`, resolve it — and the interesting part is the exception. `duplicate-url` names a
URL two posts claim; `unpublished-template` names something that exists but not in the state
required; SH297's `missing-theme` names a theme that **deliberately** does not exist. So the rule
cannot be "every address resolves". It is probably: resolvable, unless the rule is in a declared list
of ones that name an absent thing on purpose — SH255's `DECLARED` shape, where the declaration is
the interesting artefact.

### XVI.50 Every caller asks the graph to rebuild itself (SH316)

SH314 confirms the walk is bounded by bindings; SH311 makes the queries behind it nearly free inside
one read-only transaction. Neither touches the shape above them: `ShTemplateGraph.of(site)` is called
**once per question**.

In a single `verify` pass that is `unusedIn`, `duplicateTitlesIn`, `missingThemes`, one `impactOf`
per template post, and one `usedElsewhere` per site per finding — each assembling the same map of the
same site. Since SH311 they share the underlying template lists, so what is left is the assembly:
maps, sorts and set unions rather than queries.

The natural fix is SH311's, one layer up — memoize the assembled `ShSiteTemplateGraph` for the length
of the transaction, with the same read-only condition making staleness unaskable. It is small.

It is filed as an idea rather than a plan because it wants a measurement first: after SH311 the
assembly may be cheap enough that this is optimisation for its own sake, and this block has just
spent three tasks establishing that a cost is measured before it is fixed.

### XVI.57 Isolation has a price, and the list of what needs it is hand-kept (SH317, SH318)

SH315's rule is right and it costs something. A distinct `spring.datasource.url` is a distinct
**context cache key**, so every class that correctly isolates its database also boots its own Spring
application context — twelve of them now, growing by one each time the rule is applied. That is the
largest fixed cost in the Java suite and nobody has counted it. **SH317.**

Two directions, and the first may not exist: *share a context but not a database* would need a
per-test schema or a reset that does not dirty the context, since the URL is the cache key; *accept
it and measure it* at least tells the next person whether the thirteenth class costs two seconds or
twenty. Surefire already prints per-class elapsed time, so the measurement is nearly free — and it
belongs beside SH283's lesson that the suite's own numbers must come from the run rather than from
`target/`.

**SH318** is the other half. The lint knows three instance-wide reads — `verify(null, …)`,
`context(null, …)`, `changes(null, …)` — because those are the three that had already gone wrong.
`find` with no site, the SH62 media listing, `GET /api/v2/site`, the SH16 post-type listing and
`diagnostics` are instance-wide too, and a test asserting a count on any of them has the same latent
order-dependence.

That list is hand-kept, which is precisely the shape SH260 removed for helpers and SH300 for DOM
attributes. The honest version derives it from the surface: **a read whose site parameter is nullable
is instance-wide by construction.** Worth doing when the third or fourth name gets added by hand —
and worth writing down now, because the lint's message reads as though the list were exhaustive when
it is a list of the ones that have already bitten.

### XVI.18 A clamped count is invisible unless the answer is cut (SH319)

SH266 clamps `?digest=<n>` to the instance's ceiling rather than refusing it, on the argument that a
caller asking for more wants as many as it can have and the cut row tells it the rest.

That argument has a hole. The cut row appears when the **pages** run out, not when the **request**
was reduced. So `?digest=200` against a ceiling of 100, on a write that affected ninety pages,
returns ninety rows and says nothing at all — and the caller cannot tell whether ninety was
everything or the instance quietly disagreed with it.

Two fixes, and the cheap one is probably right:

- **A `notes` line when the requested count exceeded the ceiling.** SH146's shape: report the
  narrowing once rather than per row.
- **Echo the effective count.** More precise, and it adds a field every response carries for a case
  most callers never hit.

There is a real argument for doing neither: the manifest reports the ceiling, and this surface is
designed for agents that read the manifest first. The population affected is exactly the caller that
did not — which may be small enough to leave alone, but should be *decided* rather than defaulted.

### XVI.31 A follow-up from the URL sweep (SH324)

**SH324 — a fixture can model a shape the product cannot produce.** SH321's refusal fired on
`ShTemplateGraphTest`, which built a published post as two `post(...)` calls and therefore two ids —
two posts sharing a URL, which is the defect rather than a published page. The suite was green for
blocks with a fixture describing something `ShPostUnifiedWriteService` cannot create, and
`ShPageRenderTest` had the correct helper the whole time.

This is SH290's class one layer down: a hand-written artefact claiming to represent something it does
not. Two instruments, and they cost very differently — build fixtures **through the writer** wherever
the test is not about the persistence shape itself (right, and it makes a test depend on the code it
may be testing), or a **lint** asserting that no fixture persists two `ShPostUnified` rows with one
furl and different ids (narrow, cheap, and it only catches this one shape). The narrow one is
probably the right first move, on the same reasoning SH322 just wrote down: the pattern that finds
the next occurrence is worth more than the fix to this one.

### XVI.31 The habit the sweep taught (SH322)

SH279's body named three call sites from a first read and added "whatever a grep turns up". The grep
— written as a lint — found **nine**, one of which the *previous half of the same task* had created
by adding a comparator inline. SH321, filed from the same sweep, then turned eight 500s into a
refusal and a deterministic read.

That is not a documentation failure. It is evidence that "find the other call sites" is a job for an
executable pattern rather than a reading, and this repository already knows that in three places:
`ShByteStabilityLintTest`, `ShFolderSiteInvariantTest`, `ShAgentIncidentFramesTest`. The habit is now
written into `agents.md` beside the measure-first rule:

> **When a task's body says "and whatever a grep turns up", the grep is the deliverable — and it
> lands as a lint before the fixes do.**

Writing the lint first would have found the ninth site before it was written rather than after, and
it costs nothing extra: the lint was going to exist anyway.

## XVI.58 What an end-to-end MCP build of a site actually costs (SH325–SH332)

An agent connected over the `shio mcp` stdio bridge and built a working site from nothing — a
`Theme`, two `Region`s, a `PageLayout` with `sh-region` slots and `sh-field` annotations, three
`Page`s, published, rendering at `/preview/Demo/home` with `?editor=true` injecting the bridge. It
worked, and the SH99 gate closed green (`report ok checks=content,routes checked=10`).

Everything that made it *hard* is below. The pattern across all eight is worth stating first, because
it is not "features are missing": **every capability existed, and the friction was in reaching it.**
Three of the eight are a route that only a browser can take, three are a vocabulary the agent had to
guess at, one is a plan that cannot describe its own first step, and one is a bootstrap that fails on
every instance older than the feature.

### The bootstrap fails on every existing instance (SH325)

`ShWidgetOnStartup.createDefaultRows()` guards the **entire** widget catalog with
`if (shWidgetRepository.findAll().isEmpty())`. So SH252's `Ace Editor - CSS` is created only on a
brand-new database, and on every instance that predates it `POST /api/v2/render/post-types` throws
`IllegalStateException: Widget 'Ace Editor - CSS' is not seeded` — a **500**. SH183's whole render
model is therefore uncreatable on any existing install, which is where every real user is.

The sharpest part: **SH289's `reconcile` fails identically.** That verb exists precisely to repair an
older instance's render model, and it is broken by the same defect it was written to repair — because
both go through `specsFor`, which SH289 deliberately made the one field table. One reader, one bug,
both doors.

This is SH152's provision-never-upsert rule applied at the wrong **granularity**. Post-types are
provisioned per type ("`ensure` creates what is absent"); widgets are provisioned per *catalog*. And
unlike a post-type, a widget catalog is closed and code-owned — a curator never adds one — so
per-widget find-or-create is both safe and obviously correct. The same guard is why an
SH60-deprecated `reCAPTCHA` row is still being served by `GET /api/v2/widget` on this instance:
nothing has ever revisited a catalog that is not empty.

Second half: the `IllegalStateException` should be a teaching refusal. The code knows exactly which
widget is missing and exactly what to run — that is SH189's bodiless-400 lesson, and it is a 500 here
only because nobody expected the branch to be reachable.

### Three routes only a browser can take (SH326, SH327, SH328)

**SH328 is the one that matters most**, because it sits in the middle of the feature.
`site.postTypeLayout` is the single edge that connects a layout to content, and
`ShSiteWriteService.FILLABLE` is `["url", "description", "furl"]`. So an agent can create the theme,
the regions, the layout and the pages — and then cannot bind them. Nothing renders.

What makes it acute rather than merely missing is that the protocol *already tells the agent to do
it*. SH272's conventions line ends "and binds to a post-type through the site's `postTypeLayout`
map", and SH298's `unused-layout` fix says `Bind a post-type to it in the site's postTypeLayout map`.
Two surfaces instruct the agent to perform a write that no agent op can perform. The only route is
the console's id-keyed `PUT /api/v2/site/{id}` — a full replace that nulls `postTypeLayout` and
`searchablePostTypes` when a field is omitted (the hazard `ShSiteWriteService`'s own comment warns
about), behind session + CSRF.

The shape should follow SH184 exactly, one field-type wider: **per-key fill, never a replace.** An
entry for a post-type that has no binding is filled; one that already names a layout is kept and
reported. That keeps "fills what is empty, never replaces what somebody chose" intact, keeps the op
idempotent, and lets a `dryRun` answer from the same code that writes (`planFill`'s rule).

**SH326** is the same class one level out: `POST /api/v2/render/post-types` and `/reconcile` have no
MCP tool and no CLI command, so an MCP-only client — the operator this product is designed for —
cannot create the render model at all. Meanwhile the manifest reports `serverRender: true`, which is
the identical half-truth `instanceMemory: true` was before SH160: true of the instance, useless to
the client reading it. The answer is *not* a twelfth tool (the cap is 12 with 11 used, and
`tools/list` is paid every turn — P3); it is an **op on the existing write surface**, where
`site.upsert` and `folder.upsert` already live.

**SH327** is why even the REST fallback is unpleasant: `/api/v2/render/**` is not in SH167's
`ShStatelessRequestMatcher` list, so a Basic-auth caller gets a **bodiless 403** and has to walk
`GET /api/v2/csrf` → cookie → `X-XSRF-TOKEN` by hand. That is SH170's friction on `POST /api/v2/site`
and SH189's on `/api/v2/import`, for a third path.

### A plan cannot describe its own first step (SH329)

`{site.upsert site:Demo}, {folder.upsert folder:Demo/pages}` with `dryRun:true` fails at `ops[1]`
with `No readable site 'Demo'`. SH153 built `ShAgentBatchService.Declared` for exactly this — a plan
resolving against what the plan itself would create — and applied it to blueprint plans, where the
declared resources are the site and its post-types. A plain batch declares pending **folders** and
not a pending **site**, so "look before you write" is broken for precisely the bootstrap case: the
first thing any agent does on a new instance.

The message is the second defect and the worse one. `Fix: Pass ?site= with one of the listed names` /
`Example: ?site=Bootstrap` is a **read-scoping** error surfacing on a write op — there is no `?site=`
on `POST /api/v2/agent/batch`. P6 says errors are instructions; this one instructs the caller to do
something that does not exist, which is worse than saying nothing, and it is what a shared resolver
costs when only one of its callers was considered.

### A content gate reporting operational history (SH330)

`checks=diagnostics` is in the default set and turns an SH144 incident into a verify **error**. There
is no verb to acknowledge or resolve one, so any instance that has ever returned a 5xx fails
`shio verify` — which exits 1 on an error — **forever**, after the cause is fixed. This was observed
directly: the SH325 500 kept `ok:false` on a site whose content was clean, and `checks=content,routes`
had to be named explicitly to see the truth.

The durability is right (SH144's promise is that an empty report means nothing has failed, not that
nothing is remembered). What is wrong is the **severity**, and SH99's own rule settles it: `ok` is
false only for what the CMS can prove from inside itself *now*. A past incident is not that — it is
the definition of a warning. SH142's baseline is not the escape hatch either: it keys on
`(rule, address, field)` and an `api-error` finding carries no address.

### One vocabulary, because the agent guessed wrong three times (SH331, SH332)

Four wrong calls in this run, none of them a random guess — each was a rule the agent applied
consistently and Shio breaks in one of its surfaces:

| sent | wanted | the inconsistency |
|---|---|---|
| `postType` | `type` | the concept has four names: `postTypeId` (REST), `shPostType` (console), `type` (batch + document), `postType` (`POST /api/v2/post-unified`) |
| `address` + `folder:"folder:Demo/templates"` | `url` + `folder:"/pages"` | `shio_write` names a subject `address` and takes SH79 addresses; `shio_apply` names it `url` and takes bare paths — so `folder` means two different things |
| `addresses:[…]` | `address:[…]` | a plural-valued field with a singular name |
| `scope:"Demo"` | `site:"Demo"` | `PUT /api/v2/agent/memory` takes `scope`, `shio remember` takes `--site`, `shio_remember` takes `site` |

**The fourth is the serious one, because it did not fail.** `shio_remember` ignored the unknown key
and wrote the note instance-wide; the response said `(instance-wide)` and never mentioned dropping an
argument, so the note landed where no session working on that site will read it and the call looked
like a success. Finding out why took reading the tool's Java.

That is a direct violation of the rule SH320 settled one task earlier — **a parameter that selects or
bounds work teaches; one that only adds a section does not** — and `site`/`scope` plainly selects,
since it decides which memory the note joins and therefore which sessions ever see it. The lesson is
not that the rule needs restating but that it needs an **instrument**: SH320 established a principle,
and here is a tool written before it that nobody audited against it. A lint over MCP argument handling
— every declared property read, every unrecognised key refused — is the shape, and it is the argument
SH322 just made about greps: the pattern that finds the next occurrence is worth more than the fix to
this one.

The teaching errors caught the other three immediately, which is why they cost minutes rather than hours —
but P2 is that one call replaces a session, and a `fix` that repairs a guess is a safety net, not a
vocabulary. **SH331** is the alignment: one name per concept across both surfaces, `address` accepting
a string or an array everywhere, and — the part that removes the round trip rather than shortening it
— **an unambiguous synonym accepted and reported** rather than refused. Sending `postType` should
work and come back with a note naming the canonical key.

That last point needs a boundary drawn, or it will be "simplified" into the opposite of SH168.
Accepting a synonym is safe **for an op's argument names**, which are a closed set the server owns
and where the intent is unambiguous. It is *not* safe for a `data` key, where SH168's whole point is
that an upsert declares whole state and a near-miss silently replaced the field it meant. Closed set,
server-owned, unambiguous — all three, or refuse. It also sits correctly against SH320's rule: a
synonym does not select or bound work, so accepting it cannot answer a different question than the
one asked.

**SH332** is what would have prevented two of the three outright. `tools/list` carries tool schemas,
but the **op** vocabulary — `site.upsert`, `post.move`, `folder.delete`, and each op's required and
optional keys — exists only as prose inside a tool description. `ShAgentBatchService` already knows
the set exhaustively; publishing it as a resource is P2 applied to the write surface, and it is the
same move SH260 made for helpers and SH300 for DOM attributes: **the code that owns the set answers
what the set is, instead of a reader restating it.** Every hand-kept copy in this repository has
drifted, and a drifted op list does not fail a build — it makes an agent invent a key.

The design steer here is explicit and worth recording, because it inverts the usual one: this
vocabulary is optimised for **agent reliability, not human readability**. The console is the human's
surface. If a canonical op list reads as machine noise, that is acceptable; an agent that has to
infer a key name from four inconsistent spellings is not.

### The preview renders a site nobody can walk through (SH336, SH337)

`{{link}}` is `post.getFurl()` — `/contato` — and the page is served at `/preview/Demo/contato`. So
every internal link in a previewed page points outside the preview: `GET /contato` is a **404**.
`ShRenderAPI.pathWithin` uses the `/preview/<site>` prefix to *parse* an incoming path and nothing
anywhere rewrites an outgoing one.

Two things make this worse than a broken link. It compounds SH334 — the editor is an iframe on the
preview, so with no page picker *and* no working links, exactly one page of a site is reachable in the
Universal Editor, by any means. And **the lint cannot see it**: `verify`'s `routes` check resolves a
link against content friendly URLs, where `/contato` is correct, because from the content's point of
view nothing is wrong. The breakage lives in the preview's own address space, which no check models.

The inversion is active rather than merely absent, which is the detail worth keeping: a hand-written
`/preview/Demo/home` link — one that *works* in the preview — is reported `link-unresolved`, while
every link that is actually broken passes. The instrument built to find broken links is pointing the
wrong way for a previewed site. That is the same class as SH330 (a check reporting the wrong thing
confidently) and it argues the same way: **a check that is wrong is worse than a check that is
missing.**

**Three mechanisms, three jobs, and the mistake would be picking one.** The proposal that came out of
this run was a server-side DOM pass; the objection raised against it — that it would move SH101's
digest and make preview and delivery incomparable — turned out to be **wrong on the facts**, and
checking that is what clarified the division. `ShPreviewBanner.wrap` and `ShEditorBridge.inject` are
applied only in `ShRenderAPI.page`, while `/agent/render` calls `ShPageRenderService.render` directly,
so nothing injected at that layer is ever hashed.

So the **server pass carries the common case** (a): it fixes 2018 content with no author change, and it
is the only one of the three under which middle-click, "copy link address", a screenshot and a
JS-disabled browser all still work. It must rewrite a root-relative URL *only* when it resolves to a
furl in this site — `{{file_url}}` emits `/file_source/<id>/<name>`, a real server path, and SH141's
declared `frontend-routes` are real too — which means reusing the index `routes` already builds rather
than inventing a rule.

**Click interception in the browser is the complement** (b), and it earns its place on one argument
the server pass cannot answer: a region's SH248 `JAVASCRIPT` can build a nav at runtime, and no
server-side pass will ever see those links. It belongs in `shio-editor.js`, which already ships JS and
already walks the DOM — and deliberately **not** as a new always-on preview script, because *"an
ordinary preview must ship no editor code at all"* is a rule with a real reason (a page somebody
screenshots should not be running our JavaScript), and weakening it to fix link navigation would be
paying a permanent cost for an occasional one.

**And a helper answers what neither rewrite can** (c) — **SH337**. A canonical tag, an `og:url`, a
sitemap entry, an absolute link in an email: none of those is a click, so nothing can be intercepted
and nothing can be rewritten after the fact. They need the site's real origin, the server already has
exactly one answer for it (`ShSiteFrontEnd`, SH184), and no template can ask. That gap exists whether
or not the preview is ever made navigable, which is why it is its own task.

What is *not* an option is making `{{link}}` preview-aware. It is content-facing, so the same field
would mean two different things in two renders — the coupling SH187 spent a task splitting apart.

## XVI.59 Reachability has no instrument, and that is why the same defects keep coming back (SH338–SH341)

This section is a strategy change, not a defect. It comes from asking why SH325–SH337 exist at all,
given how carefully everything they touch was built.

**The pattern.** Four exploratory builds have each produced 6–13 tasks — SH168–175 (the first
end-to-end agent build), SH176–182 (driving the console over what that build produced), SH247–285
(building the renderer) and SH325–337 (building a site over MCP). That method plainly works: it is the
most productive thing in this repository's history. What it also does is find **the same classes
repeatedly**:

| first time | again |
|---|---|
| SH160 — `instanceMemory: true` was a half-truth "for exactly the clients the manifest informs" | SH326 — `serverRender: true`, identical shape |
| SH170 — `POST /api/v2/site` needs session + CSRF, worked around in `e2e/provision.mjs` | SH327 (`/api/v2/render/**`) and SH335 (the editor demanding a `WRITE` token) |

**Why they come back is structural.** This repository is unusually good at converting a defect into a
build guard — eleven of them — and every single one guards *internal consistency*: the code agrees
with itself (`ShByteStabilityLintTest`, `ShRootFolderReaderLintTest`, `ShFolderSiteInvariantTest`,
`ShTenantJoinLintTest`), the code agrees with its own documentation (`ShRenderedDomContractTest`,
`ShTemplateGrammarCoverageTest`, `ShHandlebarsAstPremiseTest`), or a decision was taken rather than
inherited (`ShCsrfExemptionLintTest`, `ShRequestReadingConfigLintTest`, `ShBenchmarkIsolationLintTest`).

Not one of them asks whether the **intended operator can reach the capability**. And not one of
SH325–SH337 is a consistency defect — every one of them is a capability that exists, is internally
consistent, and cannot be reached from where the product says its operator stands.

So a consistency defect becomes a permanent guard, and a reachability defect becomes a task that is
fixed once and guards nothing. **P1 — the law the product is named after — is the only one of the ten
with no instrument**, while P3 has `ShAgentResponseBudgetTest` plus the task benchmark, P5 has the
byte-stability lint, P6 has a `fix` on every 4xx and P10 has `ShConsoleShapeOverlapTest`.

**The rule this argues for is SH322's, one level up.** SH322 established that when a task says "and
whatever a grep turns up", the grep is the deliverable and it lands before the fixes. The same holds
for laws: *when a review finds a law is being broken, the instrument that finds every other break is
the deliverable.* Hence the ordering in the roadmap's priority queue — **SH338 before SH325–337**,
because working thirteen known instances by hand leaves the fourteenth to the next exploratory build.

> ✅ **SH338 shipped** → [CHANGELOG.md](CHANGELOG.md) Block P. The prediction this section made held:
> the instrument found what hand-work would not have. **52 features, 14 unreachable, four of them
> named by nothing** — §XVI.61 is what those four became.

**SH339** is the narrower sibling. `ShCsrfExemptionLintTest` asks "is this blanket exemption a
decision?" — the right question from the security side, which leaves the operator side unasked, and
the recurring class above is the cost. The inverse assertion is that every mutating path outside
`/console/**` is reachable by a stateless credential or names the reason a browser session is
required. Worth keeping distinct from SH338: SH338 asks whether a capability has an agent route at
all, SH339 asks whether the route it has can be used without a cookie. And a path that legitimately
requires a session must still **refuse with a body** — a bodiless 403 is SH189's defect, and it is
what made SH327 cost a CSRF dance to diagnose rather than a read.

**SH340** closes the measurement gap, and it is the one that most directly serves the agent's own
working cost. `ShAgentTaskBenchmarkTest` asserts call and token ratios over a canonical task, which
means it measures a **correct script** — and is therefore structurally blind to SH331, since a script
never sends `postType` for `type` or `addresses` for `address`. The session behind this block sent
four wrong calls in about twenty-five. Two design points decide whether the metric means anything: the
fixture must be driven the way a *naive* client would drive it, because writing it with the correct
keys defeats the entire purpose; and a **silently dropped argument must count more heavily than a
teaching error**, because a teaching error costs one round trip while a dropped argument (SH331's
`scope`/`site` case) costs a wrong outcome nobody notices.

### XVI.61 The four reachability gaps nothing had named (SH342–SH344)

These come from SH338's own output rather than from a build, which is the point of having built it:
thirteen of the fourteen unreachable capabilities had either a task or a stated reason, and **four
had neither**. They are grouped here because they share one shape — *an id-keyed REST verb that
never grew an address-native form* — and separated into three tasks because the subsystems behind
them have nothing else in common.

**SH342 — `locales` and `scheduledPublish` are advertised and have no agent form.** Both are
`POST /api/v2/post-unified/{id}/…` (`translate`, `schedule`), both undeclared in the manifest's
endpoint index, and neither has an address-native equivalent: `post.upsert` takes no locale, and
`shio_publish` takes an address and `dryRun`. So the manifest tells an agent this instance does
content i18n and scheduled publishing, and the agent can do neither. The design question is the same
one for both and worth answering once: **a locale is part of a post's identity in a way an SH79
address does not express** (SH323 settled the neighbouring case — an address carries no locale, which
is exactly why `duplicate-url` is locale-blind), so `post.translate` cannot simply take a second
address. Either the op takes `{from, locale}` and returns the new post's address, or the locale
becomes an addressing dimension, which is a much larger change. Scheduling is the easier half: a
`when` on `post.publish` is additive, and the only real decision is whether a schedule in the past
publishes immediately or refuses.

**SH343 — the agent reads its own webhook failures and cannot touch the subscription.** SH103 put
webhook delivery failures in `/agent/diagnostics` and SH269 gave them frames, so the agent is told
precisely which subscription is failing and has no route to inspect, repair or remove it:
`/api/v2/site-webhook` is undeclared and MCP-unreachable. That asymmetry is the interesting part —
**a diagnostic for a resource the reader cannot act on is SH190's defect one level up** (the `fix`
that named a log file the caller cannot open). Whether the answer is a declared route, a `webhook.*`
batch op or an admission that this is console-shaped is undecided; what is not is that
`webhooks: true` plus an incident feed is currently a promise the surface does not keep.

**SH344 — the agent cannot mint the preview token its own answer requires.** SH184 resolved "where
can a person look at this site" and its external-front-end branch ends in a token: `ShPreviewTokenAPI`
returns one so a Next.js deployment can turn draft mode on at `/api/preview`. That endpoint is
undeclared, so the agent that just deployed the front end cannot produce the credential the flow
needs. It is the narrowest of the three and the cheapest to close — one entry in the endpoint index,
and a decision about whether minting a preview token needs its own gate the way SH107 gates
publishing.

### XVI.60 The roadmap became a history instead of a queue (SH341)

Measured rather than asserted, which is the point: **95 active task lines averaging 142 words each**,
against this project's own rule that a roadmap entry is *"≈1 sentence: what + why + pointer"*. Task
bodies are **65%** of the document. The worst single line is 555 words — and six of the worst eight
were added in the session that then noticed the problem, which is the useful part of the finding: the
drift is not caused by carelessness but by writing the analysis where the analysis is freshest
instead of where it belongs.

The structural cause is that a task line and its `→ §` pointer are **redundant by design** — the
pointer exists precisely so the line does not have to carry the reasoning — so nothing pushes back
when the line grows. Every constraint here is enforced somewhere except this one: response sizes have
a budget test, tool schemas have a ceiling, and `ShRenderedDomContractTest` fails when a table drifts
from the code. A roadmap entry's length has only a sentence in `agents.md`.

Two consequences worth separating. The **cost to a reader** is that picking work now means paging
through prose, which is the opposite of what a queue is for. The **cost to correctness** is subtler:
when the roadmap carries the full argument, IMPROVEMENTS stops being the single home for design
rationale, and the two can then disagree — the failure mode this three-file split exists to prevent.

The compression is lossless where the detail is already in IMPROVEMENTS, which is true of SH325–SH340
by construction (§XVI.58 and §XVI.59 were written first, then the lines cut). It is *not* verified for
the other 79, which is why **SH341** is a task rather than something done in one sweep: some of those
lines may be the only place their reasoning exists, and compressing them blind would destroy it. The
check is mechanical — every task with a `→ §` pointer whose section actually covers it can be cut to a
sentence; a task whose pointer is missing or thin needs its rationale *moved* first. And the durable
fix is the usual one for this repository: a length assertion, so the rule stops depending on whoever
is writing the entry noticing it.

### The curator is already authenticated and the editor asks for a token anyway (SH335)

Editing text inline and pressing *Salvar rascunho* answers:

> Nenhum token de API com escopo WRITE encontrado. Crie um em Admin → Tokens de API para habilitar o
> salvamento.

The curator is sitting inside an authenticated console session, and the console is being asked for a
bearer credential to write its own content. The save path is `PATCH /api/v2/cda/post/{id}` — the
**CDA**, gated on `ROLE_CDA_WRITE` — with the token read out of `localStorage` under `sh-ue-token`,
where it was pasted by hand on the editor-sites page.

Meanwhile `ShPostUnifiedAPI` already exposes `GET /{id}`, `PUT /{id}` and `POST /{id}/publish`, all
session-authenticated, and the post edit page has been using them the whole time. So the flagship
console feature reaches its own content through a third contract, which is **P10 read backwards**:
compose, don't fork — and this is the one place in the product that forked.

Three consequences. The feature is **unusable on a fresh instance**, since no WRITE token exists until
somebody creates one, and "the editor silently saves nothing" is how that presents.

It is a **security downgrade**, and the argument against it is already written down in this
repository: SH106 refused to alias the `AGENT` scope to `WRITE` precisely because a `WRITE` key
"patches published content outside the agent protocol's guards". The editor now instructs the curator
to mint that key and leave it in browser storage, reachable by any XSS on the console, to do something
the session cookie already authorizes. A long-lived, exfiltratable credential replacing an
ambient one is a strictly worse position.

And it opens a hole in SH107. The publish gate is enforced in `ShAgentBatchService` on the stated
premise that "a console caller has no token and is unaffected" — which was true when it was written
and this path makes false. `PATCH {publish:true}` and `POST /post/{id}/publish` on the CDA check no
`may_publish` at all, so a console gesture now carries a credential whose publish authority nothing
gates. **This is the shape to watch for generally: a guard whose correctness rests on a sentence about
what some *other* surface does not do.** SH167's blanket-CSRF lint exists for the same reason — a
premise that expired when a cookie-bearing client arrived at the path — and SH234 turned that one into
a build failure rather than a comment. This premise deserves the same treatment.

**Why it is not a one-line swap**, and the decision that has to come first: the CDA offers a *merge*
(`PATCH` these attrs into the draft) while the console offers *whole state* (`PUT` a `data` map, per
SH168's rule that an upsert declares everything). The editor edits two or three fields of a page, so
pointing it at `PUT` means read-modify-write on the client — which it is halfway to already, since
`getEditable` reads the post for the properties panel — and that reintroduces a lost update the PATCH
does not have. The alternative is a merge verb on the console surface, which is the thing genuinely
missing rather than the thing being worked around. Cheaper: the read-modify-write. Righter: the merge
verb. Pick before touching the client, not after.

### The last mile is the human's, and it opened on a 404 (SH333, SH334)

The site rendered, the digest was clean and the SH99 gate closed green — and when the curator opened
the Universal Editor on it, the canvas showed this:

```json
{"detail":"Looked for a DRAFT post whose friendly URL is '/'.","status":404,
 "title":"No page at '/' in site 'Demo'",
 "fix":"GET /api/v2/agent/find?site=Demo lists the addresses this site holds."}
```

Three things are wrong in that one frame, and they are worth separating because only one of them is a
console bug.

**The teaching error reached the wrong reader.** SH80's contract is that every 4xx carries a `fix`,
and this one is a good fix — for an API caller. A curator looking at an editor canvas cannot run a
`GET`, so the most carefully designed part of the response is the least usable part of it. The rule
this suggests is narrow and general: **a teaching error is addressed to whoever can act on it**, and a
surface that renders a machine's error to a human has not reported the error, it has relocated it.
That is SH189's argument (a refusal that teaches nothing) inverted — a refusal that teaches the wrong
audience.

**Nothing timed out.** The iframe has no `onLoad`/`onError`, and the SH185 handshake never completes
because an error page carries no `editor.js`, so the badge read `Conectando…` indefinitely. "Still
loading" and "this will never load" were indistinguishable. The honest instrument is the handshake
timeout rather than inspecting the frame's document, for the same reason SH185 pins the origin instead
of using `"*"`: the editor must work against an external front end it cannot read. **SH333.**

**And the editor can only ever open one page.** `siteDestination` mirrors `ShSiteFrontEnd`, whose job
is to answer "where can a person look at this site", and its answer is the site **root**. There is no
picker, no address bar, no navigation — so the curator can edit a page at furl `/` and reach every
other page only by hand-writing the `?url=` external-mode escape hatch. Of the three pages this run
built, one was editable.

The root cause is a model gap rather than a console one, and it is the part worth carrying forward:
**a page at furl `/` is a convention that nothing enforces, defaults or reports.** The SH71 starter
has one and the `Cafeteria` fixture has one, which is why this has never surfaced; an agent following
SH79 has no reason to create one, because nothing in the address scheme, the render model or the lint
says a site needs a home. So "this site has no home page" is a state the product now produces
routinely — an agent-built site is the normal case — and never names.

That ordering matters for **SH334**: a `verify` warning for a site with published pages and nothing at
`/` is cheaper than a picker and removes most of the occasions for one. Build the warning first and
find out whether the picker is still the thing needed, rather than assuming the fix belongs in the
console because that is where the symptom appeared.

---

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
