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

### XI.5 Blueprint registry + community convention (SH96)

`shio-blueprint-*` npm naming, manifest validation, a curated index (absorbs old
SH28/SH29/SH31).

This is now the **only** thing standing between the first-party set and a community one,
and the reason it was deferred has sharpened rather than softened. A blueprint is code the
instance runs against its own content: it provisions post-types and writes pages under the
caller's credentials. So "where may a package come from" is a trust question, not a
plumbing one, and the registry deliberately loads from the classpath alone until that
question has an answer. Shipping a scanned directory or a fetch-by-URL first would be
shipping the distribution channel without the trust model — which is the shape of every
supply-chain incident anyone has had.

What the task actually owns: the naming convention, a validation pass a publisher can run
before publishing (the load-time checks SH92 already implements, exposed as a command), a
statement of what an applied package is *allowed* to do, and the index. The format itself
needs nothing: a package is already a directory whose contents are validated on load, and
`formatVersion` already refuses a package written for a newer instance rather than
mis-reading it.

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

## XII. Agent safety & human-in-the-loop (Block L)

> An agent-driven CMS is only adoptable if a human can bound it and undo it.
> **Invariant: the agent path is never a privilege escalation** — tenant isolation
> (SH22), content ACL (SH54) and token scopes apply identically on every agent
> surface. An agent-native CMS that quietly runs as root is not adoptable.
>
> SH106–SH109 have shipped; their design notes are gone with them (see CHANGELOG.md Block L).
> What remains here is the one task still blocked.

### XII.5 Agent budget guards (SH110)

Per-session call/write/storage ceilings, layered on SH45's per-tenant metering when
that lands. A runaway agent should hit a ceiling and report it, not a bill.

### XII.6 A folder delete is the one write that leaves no trace (SH200)

`ShFolderWriteService.delete` clears the posts beneath it with
`shPostUnifiedRepository.deleteAll(...)` — the repository, not `ShPostUnifiedWriteService`.
Everything this block built hangs off that writer, so the cascade skips all of it at once:

| | `post.delete` on each post | the same posts via `folder.delete` |
|---|---|---|
| SH55 trash | soft-deleted, restorable | **rows gone** |
| SH51 audit | recorded | **nothing** |
| `/agent/changes` (SH88) | reported | **invisible** |
| SH109 revert | can undo | **nothing to undo** |
| `DELETE` lifecycle event | fired | **not fired** — no webhook |
| SH38 byte cleanup | runs | **never runs; the files leak** |

The framing that makes this urgent rather than tidy: **the most destructive operation on the
surface is also the least recoverable and the least observable, and it is the one SH108's
confirm token exists to gate.** The confirm flow makes a caller look at the list before it
happens, which is worth doing — and then the thing it warned about happens in a way nothing
can report or reverse afterwards. A curator who deletes a section of forty pages by hand can
restore them; the same curator using the folder's delete button cannot. That inversion is the
defect, more than any single missing row.

The fix is almost certainly to route the cascade through the post writer rather than to
re-implement the six behaviours in the folder writer (P10, applied inside the engine: compose,
do not fork — which is also SH139's rule one layer down). Two things to decide while doing it:

- **Trash or purge?** Routing through `delete` puts a whole subtree in the trash, which is the
  recoverable answer and means the FK ordering that motivated the direct `deleteAll` still has
  to be satisfied at *purge* time instead. Routing through the purge path keeps today's
  finality but must then fire the events and write the audit itself.
- **One audit entry or N?** A forty-page subtree should probably record the folder deletion
  plus its members, because SH109's revert operates per entry and "restore the section" is the
  action a human wants — which is an argument for the trash answer, since a purge cannot offer
  it at all.

### XII.7 The console's move and copy authorize only the destination (SH201)

`ShObjectAPI` calls `shContentAclService.requireWrite(destId)` and never checks the objects in
the request body. So a caller with write on any single folder can move content *out of* a
subtree they cannot write — which is a removal from the owner's point of view — and can copy
content they were never granted read on. The agent path checks both ends (`post.move` and
`folder.move` both `requireWrite` the subject and the destination), so this is one surface
enforcing SH54 and the other not.

This block's standing invariant is that **the agent path is never a privilege escalation**, and
it has been argued in that direction throughout — every guard narrower than what was reachable
before. SH201 is the same invariant read from the other end: after SH106–SH109 the agent path
is in places *stricter* than the console, and where that happens the console is the hole. The
invariant to carry forward is symmetrical — **the two surfaces enforce the same ACL, and
whichever is stricter is the correct one** — because a curator's session is not a lower bar
than an agent's credential, it is a different one.

Worth noting what this is *not*: a reason to loosen the agent path to match. The whole point of
Block L is that the strict side is right.

---

## XIII. Token economy, measured (Block M) — what the measurement opened

> ✅ **Block M shipped** → [CHANGELOG.md](CHANGELOG.md) Block M. The design rationale for
> the shipped work lives there and in the code; `git log` is the history. What follows is
> only the unshipped remainder, and all of it exists because the block did what it was
> built to do: produce numbers, and then disagree with an assumption.

### XIII.1 Close the tool-call gap (SH157)

The benchmark's own verdict. Tokens clear the concept spec's ≈10× target on three of the
four canonical tasks; **tool calls clear nothing** — the best task is 3.3× against a ≈6×
target, and the worst is 1.8×.

The reason is visible in the traces and is not a payload problem: the agent path's calls
are no longer *content* calls. Every task opens with discovery (manifest, then context)
and closes with proof (verify), and the actual work in between is one or two calls. So
the remaining round trips are the framing, and shrinking them means either answering two
questions in one read or folding the proof into the write:

- **Discovery in one call.** The manifest and the context pack are two reads a session
  makes back to back, always, in that order. A `?include=manifest` on the context pack —
  or simply serving the manifest's `startHere` inside the pack — removes one turn from
  every session in existence. The reason they are separate is that the manifest is
  instance-scoped and cacheable for a deployment while the pack changes with content;
  SH116 makes that distinction cheap to preserve (two tags, one response is not possible
  — which is the actual design problem here, not the merging).
- **A write that returns its own proof.** `POST /agent/batch` already knows what it
  touched. Running the SH99 checks over exactly those addresses and returning the findings
  costs the server almost nothing and removes the verify turn — and it is strictly better
  than a separate verify, because it scopes the report to the change instead of the site.
  The risk is a write that reports a pre-existing defect as if the write caused it, so the
  findings have to be attributed.

Both are additive and neither changes an existing contract. The benchmark is the
acceptance test: the ratio floors in `token-budgets.properties` move up when this lands,
and that is the only evidence that should be accepted for it.

### XIII.2 Guard the request-reading config against test/prod drift (SH158)

`src/test/resources/application.properties` **shadows** the main file on the test
classpath rather than merging with it. That is ordinary Spring Boot behaviour and it is
also how an entire class of defect hides: a setting that governs how a request is *read*
can be correct in production and simply absent from every test, and no test can notice,
because the tests are the thing that is misconfigured.

It is not hypothetical. `spring.jackson.deserialization.fail-on-null-for-primitives=false`
is required for the write surfaces to accept a body that omits an optional flag, and its
absence made `POST /agent/batch {"ops":[…]}` a 400 through four blocks — found only when
SH112 sent real JSON for the first time.

The fix is not another setting; it is a test that reads both files and asserts that the
keys which affect deserialization agree. Cheap, and it closes the class rather than the
instance. Worth pairing with the narrower guard: at least one test per write surface that
sends the **minimal** body the documentation shows, since a request record constructed in
Java exercises none of the binding.

### XIII.3 Conditional reads for the rest of the surface (SH159)

SH116 covers the manifest, the context pack and the blueprint catalogue — the three
discovery reads. It does not cover `find`, `read`, `changes`, or the SH16 post-type API,
and each of those is a repeat read with a stable digest already available or cheap to
compute. `find` in particular is the read an agent makes on every iteration of a loop.

The work is mechanical; the judgement is the same one SH116 already made and must not be
relaxed: the tag identifies a **representation**, so `format`, `ids` and `fields` all
belong in it. A `read` projected to two fields must not answer 304 to a caller that asked
for the whole document.

### XIII.4 Memory for a client with no shell (SH160)

`shio remember` was chosen over an eleventh MCP tool on a clear argument: a tool schema is
paid on every turn of every session, and a coding agent has a terminal, so a capability
used a handful of times per session should not tax every turn. That argument is right for
Claude Code and wrong for a pure MCP client, which can *read* the memory (it rides in the
context pack for free) but has no way to write one.

Three options, and the answer is not obvious:

1. **An eleventh tool.** Measured cost is roughly 110 tokens per turn against a
   `mcp.tools.list.total` of 2160 with about 60 of headroom — so it also means raising the
   ceiling, which is the thing the ceiling exists to make deliberate.
2. **An argument on an existing tool.** `shio_write` is a write and "remember this" is a
   write; a `remember` property costs perhaps 30 tokens. But it would couple a note to a
   content transaction that may roll back, and a note is not content — that separation is
   load-bearing (see the CHANGELOG on why memory is a table).
3. **Leave it.** Accept that the MCP surface is read-only for memory and say so in the
   manifest, so a client learns it from a flag rather than from a missing tool.

Option 3 is the honest default and the current state; the task is to decide rather than to
let it be decided by nobody looking.

### XIII.5 Memory travels with the content (SH161)

A site's notes live only in the instance that learned them. An SH68 exchange package does
not carry them, and `shio pull --content` does not project them — so exporting a site to
another instance, or rebuilding one from files, silently drops everything earlier sessions
worked out. The compounding saving only compounds if it survives a move.

Both halves are small and both have a decision in them. In the exchange package, notes are
site-scoped data and belong beside the content; the instance-wide ones do **not** travel,
because they describe the installation rather than the site, and importing another
instance's house rules would be a surprise (P7). In the file projection, notes are not
posts and must not become a desired-state document that `--prune` can act on — the natural
home is a single `shio/content/<site>/.memory.yaml`, read on push and written on pull, with
the same three-way merge the content tree already uses.

### XIII.6 The manifest has no terse form (SH174)

Every session starts with `GET /agent/manifest`, and the manifest is the most expensive
document on the surface:

| Response | `X-Shio-Est-Tokens` |
|---|---|
| `/agent/manifest` | **2169** |
| `/agent/context?format=terse` | 410 |
| `/agent/blueprints?format=terse` | 467 |
| `/agent/verify?format=terse` (clean) | 14 |

SH78 gave the pack, the catalogue and the report a compact encoding and left the one
document that is *always* read at full JSON. Nearly all of it is the endpoint index — a
purpose sentence and an example per row, twenty-nine rows — which is genuinely the valuable
part on a first read and pure repetition on every subsequent one. SH116 already covers the
repeat case with an ETag, so the target here is the **first** call of a session, which no
`304` can help.

Two candidates, and they are not exclusive. A terse encoding of the same content, in the
line-oriented form the rest of the surface uses. Or `?include=` on the manifest the way the
context pack has it, so a caller that only needs `features` and `limits` — the two things a
capability check reads — does not pay for the index. The second is probably the better first
move: an agent re-reading the manifest to confirm a feature flag is the common case, and it
currently costs 2169 tokens to answer a boolean.

There is a second, smaller thing to fix in the same place. `/agent/manifest?format=terse`
returns full JSON with no complaint, while `/agent/context?format=nonsense` is a teaching
400 (SH115). Silently ignoring an unknown parameter is the failure mode SH80 exists to
prevent — the caller believes it asked for something and is billed for the full document —
so whichever encoding lands, an unsupported `format=` on this endpoint should answer the way
its neighbour does.

## XIV. Realignment of what already shipped (Block N) — what building it opened

> The reorientation was mostly additive, but it did invalidate some existing choices.
> Block N spent them → [CHANGELOG.md](CHANGELOG.md) Block N. What is left here is the
> one lesson worth carrying forward (§XIV.4) and the design for what building it
> opened. Concept spec §10 has the original table.

### XIV.1 Renaming a post-type is a move, not an upsert (SH163)

SH117 left the post-type surface keyed by name, which is right — it is how SH79
addresses everything else — and it means the one operation the surface cannot express
is a rename. A `PUT /api/v2/post-type/NewName` does not rename `OldName`; it creates a
second post-type, and the first keeps its id, its attribute rows and every
`sh_post_unified` row that points at it. The console's API-name field is read-only for
exactly this reason, which is honest but not a feature.

This is SH134's problem again, and SH134's shape is the answer: **one operation that
moves rather than two that happen to leave the right rows behind.** A `post-type.move`
(or `POST /api/v2/post-type/{name}/rename`) that updates the name in place, refuses an
occupied target, and reports what it moved. The two questions worth settling first:
whether a rename should rewrite the `type` in the SH82 content documents and
`shio/content/**` files that name it (they address the post-type by name too, so a
rename is drift the next `--check` reports), and whether a *managed* type may be
renamed over the API at all or only in the repo that owns it.

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

### XIV.6 Re-establish the token claim on its actual sources (SH164)

Block N's uncomfortable result: SH117 and SH120 fixed the baseline, and every token
ratio in the SH112 benchmark fell against an unchanged agent path — **3.8×–23.3×
became 2.6×–12.6×**. Nothing regressed. The measurement simply stopped being flattered
by a console that shipped entity-shaped JSON.

The problem this leaves is not the smaller number, it is that **the benchmark can no
longer distinguish "the protocol is cheaper" from "the old console was fat."** A single
ratio per task mixes four independent savings, so improving the baseline moved all of
them at once and there is no way to see which of the protocol's ideas is actually
carrying the claim. The fix is attribution: measure the four sources separately —
addressing versus id round trips, one batch versus N writes, a context pack versus a
crawl, a lint that names the defect versus a read-everything sweep — and give each its
own floor. Then a future baseline improvement moves the number it should move and
leaves the rest alone.

The second half is a judgement call to make deliberately rather than by inertia: with
the baseline lean, is ≈10× still the right target, or was it always a proxy for "an
agent can do this in one session instead of three"? A target nobody can hit gets
disabled; a target that measures the wrong thing gets hit and means nothing.

### XIV.7 The console listing is unpaged (SH165)

`GET /api/v2/object/{id}/list` returns every child of a folder. SH120 aligned its
shape with `ShCdaListing` but deliberately did not copy the `page`/`size`/`totalPosts`
fields, because three constants pretending to be a contract are worse than an honest
absence — the CDA has been paged since SH1 and the console has never been.

It is a scaling limit with two halves, and the server half is nearly free: the
projection already exists, so the endpoint needs the parameters and the count. The
browser half is the real work and the real decision — pages, infinite scroll, or a
virtualized list — and it interacts with the browser's client-side sort and search,
which currently operate on the whole set precisely because the whole set is there. A
paged listing with a client-side sort would silently sort one page, which is the kind
of half-migration that is worse than either end state.

### XIV.8 A blueprint's carried files reach an MCP-only caller unsubstituted (SH166)

SH119 split parameter substitution by who knows the answer: the server substitutes a
package's content, and the client substitutes its carried files. That is right for the
CLI — the project's npm name is local identity the server has no business being told,
and the server never writes a project file anyway — and it leaves a caller with no CLI
holding source that still says `{{projectName}}` with nothing to fill it in from.

Three options, in increasing order of how much they concede: say so (the tool result
names the files as templates and lists the parameters, which is P9 and costs nothing);
substitute server-side when the caller supplied the parameter anyway, since an MCP
apply *does* send `params`; or accept that a front-end half needs a filesystem and that
an MCP-only client is not the audience for it. The first is almost certainly worth
doing regardless of which of the others follows.

### XIV.9 Review the remaining blanket CSRF exemptions (SH167)

`/api/v2/post-type` was CSRF-exempt wholesale because only the stateless `shio` CLI
reached it. SH117 pointed a browser at the same path, and the exemption became a hole
that no console feature would have surfaced — so it was narrowed to callers holding no
session cookie (`ShStatelessRequestMatcher`).

`/api/v2/cda/**`, `/api/v2/agent/**` and `/mcp` rest on the same argument, and the
argument is true of all three *today*. It was also true of the post-type API until it
wasn't. The check now exists and costs one matcher, so the question is no longer
whether it is possible but which of the three should adopt it before a feature decides
for us. The agent namespace is the one to watch: SH109's review queue is a console
surface over agent writes, and the moment the console reads or writes there from a
session, that path is in the same position `/api/v2/post-type` was in.
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

**What is missing.** Nothing in this repository renders a page. No template engine is on the
classpath — no Velocity, Thymeleaf, Handlebars or Freemarker — and `ShSitePostTypeLayout`
(`format`/`layout`/`mimeType`/`cacheTTL`) survives only because the exchange format and the
site write service carry it; no code reads it to produce output. `nashorn-core` 15.7 is still
declared in `shio-app/pom.xml` and nothing uses it: the only reference left anywhere is a
`ShWebsiteProperties.nashorn` string array. It is an orphan, and a script engine on the
classpath that nobody calls is build weight and attack surface for free — worth removing
whichever way this section goes.

Both human-facing visual surfaces therefore point outward at something Shio cannot make:

| Surface | Needs | Without it |
|---|---|---|
| Preview (`ShPreviewTokenAPI`) | `site.getUrl()` as a real base | `400 {"error":"site has no url"}` |
| Universal Editor (`visual-editor.page.tsx`) | `<iframe src={site.url}>` | `universalEditor.siteNoUrl` |

Applying `marketing-site` produces thirteen correct pages and no way to see one.

**The model: page → layout → region.** This is the granularity the original engine intended
(`PT-PAGE-LAYOUT` and `PT-REGION` are both declared in FSP's `ShSystemPostType`, and the region
level was never finished — nothing in Java ever read it), and it is the right one for enterprise
work, because each level is owned by a different person and changes on a different clock:

| Level | Is | Owned by | Changes |
|---|---|---|---|
| **Page** | content + a layout binding | author / agent | constantly |
| **Layout** | an HTML skeleton with named region slots | front-end dev | per template |
| **Region** | HTML + client JS that renders one kind of content | front-end dev | per component |

Three properties come out of it that a flat renderer cannot give. A region is the **reuse**
unit, so a header or a product card is authored once and appears on every layout that slots it.
A region is the **edit** unit, which is what makes the Universal Editor mean something — a
curator clicks a region and edits that region, rather than being handed a whole page. And a
region is the **cache** unit, so the FSP `cacheTTL` that already exists per post-type × format
gets a level where it is actually useful: a navigation region cached for an hour inside a page
that is not.

**Why this is not the dropped Block D — and where the JavaScript goes.** The binding non-goal
is *"No GraalJS server-side JS template engine"*. §0.4's argument is specific and still correct:
an in-JVM script engine is **sandboxing liability**, because what it evaluates is user-supplied
code. Choosing Groovy changes the syntax and not one word of that argument — which is why
Groovy is named in the non-goal too, and it is *more* capable inside the JVM than Nashorn, not
less.

The resolution is to split the two things FSP fused. **HTML composition happens on the server,
declaratively**; **JavaScript happens in the browser, as it does in every other front end
written this decade**:

```
server   layout.hbs + region.hbs  ──Handlebars.java──▶  HTML  (+ data-shio-* annotations)
browser  region.js as a static asset  ─────────────────▶  interactivity, progressive enhancement
```

Nothing is evaluated in the JVM. A region author still writes "HTML + JavaScript" — the exact
material asked for — but the JS is shipped to the client instead of `eval`'d on the server, so
`Java.type()`, `Runtime.exec` and the raw `HttpServletRequest` are not reachable, by
construction rather than by a filter someone has to remember to configure.

**Handlebars, in Java, is the same language authors already wrote.** The engine loaded
`handlebars.min.js`, so the templates in existing sites are Handlebars templates; a pure-Java
implementation evaluates them without a script engine. It is logic-less by design and its
expression grammar is not Turing-complete, so the seven `shObject.js` functions become
**explicitly registered helpers** — `{{#navigation}}`, `{{#query}}`, `{{#search}}`, `{{#form}}`,
`{{#getRelation}}` — which is what the façade was already trying to express as a convention.
Two hazards to pin down in the task, because they would smuggle the problem back in: any
JavaScript-helper integration must stay off, and the value resolver must be restricted to maps
and declared field data rather than resolving arbitrary bean properties by reflection.

**Who renders — the options, and the recommendation.** They differ by where markup is produced:

| Renders | Mechanism | Costs | Buys |
|---|---|---|---|
| **Shio (Java)** | layout + regions composed by Handlebars.java | a renderer, plus the region vocabulary | a site visible with **no client setup at all** — the only option an MCP-only agent can reach, and the only one the Universal Editor can annotate |
| **Browser** | a static React/Next export served by Shio, fetching from the CDA | almost nothing — Shio already serves `/console/**` and `/file_source/**` | design freedom, no Node in production |
| **Node** | Next.js, as today | a project and a process the agent cannot create over MCP | SSR, ISR, the production path |

"Deploy React inside Shio and let it render" splits in two, and only one half is available:
server-rendering React *in the JVM* needs a JS runtime and is the non-goal under a new name;
serving a **static export** needs no engine and is cheap — but it renders in the browser, so the
first paint carries no content, which is the wrong trade for preview, for SEO, and for a visual
editor that has to map a DOM node back to a field.

Recommendation: row one as the default visible path, row two as the upgrade a designer reaches
for, Next.js unchanged as production delivery (P10).

**Why this fits the agent surface better than a fixed vocabulary.** Layouts and regions are
**content** — posts of a system post-type. So an agent creates and edits them with
`post.upsert`, they travel in the SH68 exchange package, they project to files under
`shio/content/**` (P4), `shio verify` can lint them, and `shio push --content` can converge
them. A renderer built instead as a closed Java projection of the eight SH95 section types would
be none of those things: unextendable without a Java release, and invisible to every tool the
agent already has. The SH95 vocabulary then becomes what it should be — a set of **default
regions** shipped as content, not the only thing renderable.

It also covers what a fixed section set cannot: navigation, queries and listings, search results
and forms are dynamic, and all four were first-class in FSP. And it keeps the two axes already
present in the entity, the exchange format and `ShSiteRequest`: **format** (same post, alternate
representation, own layout/mime/TTL) and **theme** (`PT-THEME`).

**What it must not become.** A preview URL that renders production content *is* a delivery path
the moment somebody points DNS at it — and then Shio owns HTML rendering, caching and SEO, the
position §0.3 and §0.4 declined. If this grows into the delivery path that has to be a decision
taken deliberately, with the CDA/Next split revisited in the open, not a consequence of a
preview route getting popular. Until then: a distinct route, an unmistakable banner, no
freshness headers that invite a CDN, drafts only behind the SH5 preview token.

**And delete the orphan.** `nashorn-core` 15.7 stays in `shio-app/pom.xml` with nothing calling
it — a script engine on the classpath that no code reaches is build weight and attack surface
for free. It goes whichever way this section is decided.

**Proof that the model still fits: the 2018 package imports.** `openviglet/bootstrap-site` was
converted and imported into a current instance. It worked — `201`, and the content arrived
intact:

| Checked afterwards | Result |
|---|---|
| Post-types | all six recreated; every widget the 2018 fields named (`Ace Editor - Javascript`, `Ace Editor - HTML`, `Text Area`, `Content Select`, `Relator`) **still exists** |
| Layout `HTML` | 1330 chars, byte-identical, both `sh-region="…"` slots present |
| Region `HTML` | the Handlebars template intact (`{{#each this.posts}}`, `{{this.link}}`) |
| Region `JAVASCRIPT` | 767 chars preserved |
| `site.postTypeLayout` | preserved verbatim, in its original simple form |

The conversion was **mechanical — seven renames**, all recorded in the throwaway script: `label`
→ `title`; the post-type `fields` map → an array carrying `name`, with `title`/`summary` becoming
`isTitle`/`isSummary`; `site.rootFolders` → `rootFolderIds`; a folder gaining explicit `siteId`
and `rootFolder` where 2018 implied both; `post.folder`/`postType`/`fields` →
`folderId`/`postTypeName`/`data`; `title`/`summary` promoted from fields to columns; and files
needing a manifest entry plus a `files/<id>` zip entry.

Two things this settles. The page → layout → region model is **expressible in today's content
model with no schema work at all** — SH183 needs a renderer and the three system post-types, not
a migration. And the real-world `postTypeLayout` was the simple map `{"FOLDER": "…LAYOUT",
"PT-ARTICLE": "…LAYOUT"}`, not the `ShSitePostTypeLayouts` array of `{format, layout, mimeType,
cacheTTL}` — so the format axis was built and, in the one shipped example, never used. SH183
should treat multi-format as the extension it evidently was, and not pay for it up front.

One thing it does not settle: whether the *templates* run. They are Handlebars and they import,
but nothing has evaluated them, and the region JavaScript still assumes a `shObject` façade that
would not exist. That is the experiment SH183 has to run.

### XV.2 A site's front-end URL is unreachable from the agent path (SH184)

`site.url` is the one field that turns content into something a human can open, and nothing an
agent can call sets it. SH170's `site.upsert` creates a blank site and deliberately *never
updates* one, on the reasoning that a site's URL and description are console concerns with a
human looking at them. That holds for a decision about a domain; it does not hold for the field
that gates both visual surfaces. An agent can build a site, scaffold a front end and run it,
and still not connect the two without a human opening the console.

With SH183 the default stops being a judgement call: a new site's URL can point at Shio's own
preview route, so a site is visible as soon as it has content, and pointing it at a real
deployment later is the human's edit. Two properties to keep — setting it stays idempotent and
reported like every other op, and it refuses to *replace* a URL a human set. The P7 line runs
between "fill in what was empty" and "redirect what somebody chose"; only the second is a
surprise.

### XV.3 Nothing implements the Universal Editor's iframe protocol (SH185)

`visual-editor.page.tsx` loads the site in an iframe, pushes messages into it
(`contentWindow.postMessage({type, data}, "*")`) and listens for replies
(`window.addEventListener("message", onMessage)`). There is not one `postMessage` in
`create-shio-app`, `shio-react-sdk`, `shio-sections` or `shio-client`. The editor has no
counterpart anywhere in the repository, so it cannot work even against the first-party starter
correctly deployed.

Two consequences. `universalEditor: true` in the agent manifest is not true in the sense the
manifest promises — an unshipped capability reports `false` and never overstates, and this is
the first flag to break its own rule, so it should be downgraded until an implementation
exists. And the protocol is unwritten: the message types, the direction each travels, how a
field maps to a DOM node, and what the editor does when the page was rendered by something
that never heard of it. SH183 gives it a first implementation to target, which is the right
order — specifying a wire format with nothing on the other end is how the current shell got
here.

Worth fixing in the same file: the `"*"` target origin. Posting content into an arbitrary
iframe origin is a data-leak shape, and the site's own origin is known.

### XV.4 The agent sees a page's structure, never its appearance (SH187)

SH169 closed the structural half: `/agent/render` composes a page's referenced sections, so an
agent can prove the outline, the landmarks, the links and the alt text are right, and a
missing section changes the digest instead of hashing to the same empty value as everything
else. What no instrument reports is whether the page *looks* right — `visualSnapshot` and
`signedImageUrls` are `false`, and `renderFetch` is off by default and origin-checked when
enabled.

So an agent asked to change how a page looks still has to hand the question to a human, which
is the expensive unit Block K exists to remove. ✅ **SH102 has shipped and it is the right
instrument** — `shio snapshot`: a PNG, a baseline and a diff — but it shipped scoped *"for the
human handoff rather than for the agent"*, deliberately, and that framing is what this task
revisits: visual autonomy needs the agent to be the reader, which means a call on the agent
surface rather than a CLI command, and a payload a model can act on rather than an image file.
SH183 makes
it cheap instead of a new subsystem, because the renderer's own HTML is the artifact, produced
in-process with no browser and no outbound fetch. Whether a raster image follows is then a
smaller, separate question about what a hash of structure cannot catch — which is most of what
"looks wrong" means.

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

### XV.6 `may_publish` is NULL on existing rows and the field is a primitive (SH188)

`v2026.3.11.yaml` adds `api_token.may_publish` nullable, with no backfill and no
`defaultValueBoolean`, and its comment states the intent plainly: *"read as `false` when absent —
the direction a default on this field has to fail in."* The entity does not implement that intent:

```java
@Column(name = "may_publish")
private boolean mayPublish = false;      // Hibernate cannot set a primitive from SQL NULL
```

So on any instance holding an `api_token` row from before the changeset ran, reading a token
throws `IllegalArgumentException: Can not set boolean field … to null value`. It takes out
`GET /api/v2/api-token` and — the part that matters for this block — `POST /api/v2/preview-token`,
which is the console's *Preview draft* button. Preview is broken for two independent reasons, and
SH184 alone would not fix it.

Three candidate fixes, not equivalent. A `Boolean` wrapper with an explicit null-to-false read
implements the comment exactly and keeps "absent means no" true permanently. A
`defaultValueBoolean: false` plus a backfill changeset puts the default in the database, which is
easier to reason about but leaves the primitive one NULL away from throwing again. Both together
cost one more line.

Worth recording *how* this surfaced, because it argues for the instrument: no test caught it, the
failure needs a database with pre-existing rows, and it appeared as an `api-error` finding in
`shio verify` because SH103's incident window had recorded the 500. Block K doing its job on a
defect nobody was looking for.

### XV.7 Importing an exchange package teaches nothing and is not on the agent surface (SH189)

`ShExchangeImportAPI` ends every failure the same way:

```java
} catch (IOException | ShExchangeException e) {
    return ResponseEntity.badRequest().build();
}
```

`ShExchangeException` knows what went wrong; the caller gets a `400` with no body. Bringing
`bootstrap-site` forward therefore meant reading the `ShExchange*` records in Java to infer seven
field renames, because the endpoint would not say whether the failure was the manifest schema,
the zip layout, a missing post-type or a bad reference. Every other write surface here answers a
bad request with `fix`/`allowed`/`didYouMean` — in the same session a mistyped address came back
with `didYouMean: "bootstrap_nav_region"` unprompted. Import is the outlier, and it is the one
endpoint whose input a human assembled by hand and cannot inspect.

The second half is a law P1 problem. Import is console-only: multipart plus a CSRF token, no
`batch` op, no MCP tool. An agent can create a site, a model and a hundred posts from nothing and
cannot accept a package a human hands it — which is the cheapest possible starting point, and the
form every first-party template ships in.

Two decisions for the task. Whether the manifest gets a version field and a stated migration
path, now that there is proof a real package can be seven renames behind. And whether the
agent-side verb is a `batch` op, its own endpoint, or `shio import` in the CLI — the last is
enough if a package always comes from a filesystem, and is not if an MCP client is meant to
accept one.

### XV.8 Diagnostics name a failure but never carry the evidence (SH190)

An `api-error` finding reports the exception message and then says: *"This call failed
server-side, so its response is gone. Retry it; if it fails again the instance log has the stack
trace (the body never will)."* Honest, and a dead end — it directs the agent to a file it has no
access to, and in this session the running instance was not writing to the log the repository
contains.

The message alone was enough to identify SH188 only because that exception named its own field.
That is luck. A `NullPointerException` inside a service method would produce a finding that says
a call failed and nothing about where, which is exactly as useful as the bare 500.

The fix belongs in SH103's existing window, not in a new capability: capture the throwing frame —
class, method, line, and the few frames of Shio's own code beneath it — where the incident is
already recorded, and serve it on the finding. Deliberately **not** a log-tailing tool. A log tail
hands over every request's data to answer a question that is always about one request; it is a
per-deployment path an agent should not have to know; and it cannot be scoped by the SH2 site
allow-list or the SH54 ACL that bounds everything else on this surface. Bounded, attributed
evidence attached to the incident is smaller and safer than a file.

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

`content-files.mjs` is deliberately narrow: `bodyFieldFor` returns a field only when its widget
is `HTML Editor`, and the comment says why — a value that would not survive the round trip stays
in `fields:` as JSON rather than becoming prettier. That rule is right for content and wrong for
presentation, because a layout's fields are `Ace Editor - HTML` and `Ace Editor - Javascript`,
which do not qualify. The imported region projects like this:

```yaml
fields:
  HTML: "<div class=\"card-columns\">\n {{#each this.posts}}    \n  <div class=\"card\">\n…"
  JAVASCRIPT: "load('https://cdnjs.cloudflare.com/…');\nvar template = Handlebars.compile(html);\n…"
```

An HTML document and a JavaScript program, as escaped one-liners inside YAML. No line numbers, no
highlighting, no `Grep`, no diff a human can review, and every edit means re-escaping by hand.
Law P4 says files beat APIs for authoring, and this is the artifact an iterative loop touches more
than any other.

The fix is a multi-file projection: `bootstrap_content_list_region.md` for the metadata beside
`bootstrap_content_list_region.html` and `.js`, with frontmatter naming which field each sidecar
carries — the same trick `body:` already plays, generalized past one field and one widget. Two
things to settle: which widgets earn a sidecar (`Ace Editor - *` is the obvious set, and it should
be the *widget* that decides, not the post-type, or the rule stops working the moment someone
models their own layout type), and what a pull does when a sidecar and the frontmatter disagree —
the same refusal `url:`-versus-path already gets.

#### XV.10 A template must fail at write time, not at view time (SH192)

Nothing validates a template. `post.upsert` refuses a field the post-type does not declare (SH168)
and has no opinion about the 1330 characters of markup inside one, so an unclosed `{{#each}}`, a
call to a helper that does not exist, or a reference to a field that was renamed is accepted,
stored, published — and discovered as a blank page. Every iteration then costs a guess, which is
precisely the loop this block exists to make cheap.

The reporting standard already exists in this product's own history: FSP's `regionError` printed
the file, the line, the column, a five-line window and a caret under the offending character. That
is what law P6 asks for and it was there in 2019. The task needs a `template-invalid` verify rule
plus a refusal on write, and one decision — whether an unknown *field reference* is an error or a
warning. It should be an error when the post-type is known (a renamed field silently rendering
empty is the SH168 failure wearing a template), and a warning when the data comes from a helper
whose shape the CMS cannot know.

#### XV.11 The blast radius of a region change is uncomputable (SH193)

Composition is expressed as an attribute inside an HTML string:

```html
<div sh-region="BOOTSTRAP_NAV_REGION"> </div>
```

so the content graph cannot answer the question every shared-presentation edit begins with: which
layouts slot this region, which pages use those layouts, how many are published. Changing a
navigation region is an unbounded edit, and `verify` cannot prove afterwards that nothing broke
because it does not know what depended on it.

SH154 solved the identical problem one level up: a `Multi Select` holding post URLs became a real
reference because `widgetSettings` declared what the field held, and then the lint, the digest and
the change feed could all agree about it. Slot references want the same treatment — extracted at
write time into `ShReference`, which already exists and already survives a move. Once they are
edges, "what does this change affect" is a query, `changes` can report it, and SH140's
inbound-links idea gets its second use.

#### XV.12 Appearance needs a before/after and a one-call loop (SH194)

Two costs, both per iteration. **Feedback**: SH187 produces a snapshot, which answers "what does
this look like"; a loop asks "what did my change do", and that is a diff. ✅ SH102 shipped that
diff (`shio snapshot`, with a baseline and a `.prev.png`), and it is still scoped *"for the human
handoff rather than for the agent"* — the framing SH187 corrects, not a missing capability.
**Round trips**: a layout change today is `post.upsert`, then
`/agent/render`, then a look. Three calls per tweak, and a tweak is the unit of this work. A write
that can return the rendered digest of what it just changed collapses that, and it is the same
idea SH157 identified for closing the tool-call gap — a write that returns its own verification.

The honest limit: a structural digest cannot see most of what "looks wrong" means. Spacing,
weight, contrast and alignment do not move a hash. That is the argument for the raster half of
SH102 rather than against the digest, and it is also why XV.13 matters more than it looks.

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

## XVI. The human half (Block O) — what reading the console next found

Block O shipped, and its lesson was that **console defects answer `200`**: seven of them, none
visible to a Java test, a CLI test or the MCP golden. The two below were found the same way but
from a different angle — not by driving the console, but by fixing the *agent's* move verbs
(SH134/SH137/SH139) and having to read the console's implementation of the same operation each
time. The pattern is consistent enough to state as an expectation: **when a verb exists on both
surfaces, the console's copy is where the careless version lives**, because nothing on it is
asserted at the level the agent surface is.

### XVI.1 The console's clone mints a duplicate friendly URL (SH202)

"Clone" is copy-to-same-folder, and `copyPost` derives the copy's `furl` from the source's
title — which is the same title, so the same slug. Two posts in one site then claim one URL.

What makes it worth its own task rather than a footnote is that every downstream mechanism
already treats this as broken, and correctly: `shio verify` reports `duplicate-url` as an
**error** (not a warning — it is broken from a visitor's point of view), and `findPost` resolves
the URL to whichever row it reaches first, so the clone is unaddressable and so, now, is the
original. SH134 spent real design effort refusing an occupied address on *move*; create simply
does not ask.

The fix has an obvious shape (suffix until free) and one decision inside it: whether a copy
should also get a distinguishable **title**, since duplicate titles in a listing are the symptom
the curator actually sees, and a `-2` URL under an identical title still leaves two rows that
look the same. Worth doing together, because the URL is derived from the title and fixing only
the derived value leaves the display ambiguous.

### XVI.2 The move dialog offers destinations the server refuses (SH203)

SH137 added the guard that a folder cannot move into its own subtree — a cycle detaches the
subtree from the site root and sends the recursive delete round the loop. The guard is right and
belongs on the server. But the console's move dialog starts at the site root and navigates
*down*, so the folders inside the one being dragged are exactly the ones a curator scrolls into,
and they now answer `409`.

The design point generalizes past this dialog: **a picker that lists choices the server will
refuse teaches the user that the operation fails at random.** The refusal is the backstop, not
the interface — the tree should disable the moving folder's own subtree, and keep the `409` for
the case the UI cannot know about (a concurrent move, a stale tree). That division — the client
prevents what it can see, the server refuses what it must — is the same one SH108's confirm flow
already uses, where the CLI prints the deletions *and* the server still demands the token.

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
