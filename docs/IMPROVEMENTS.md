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
  - [IX. Perception & verification loop (Block K)](#ix-perception--verification-loop-block-k)
  - [X. Content as files (Block I)](#x-content-as-files-block-i)
  - [XI. Blueprints + Claude Code plugin (Block J)](#xi-blueprints--claude-code-plugin-block-j)
  - [XII. Agent safety & human-in-the-loop (Block L)](#xii-agent-safety--human-in-the-loop-block-l)
  - [XIII. Token economy, measured (Block M)](#xiii-token-economy-measured-block-m)
  - [XIV. Realignment of what already shipped (Block N)](#xiv-realignment-of-what-already-shipped-block-n)
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

**The general lesson, now paid for twice.** A write vocabulary designed against "build
a site" will be missing the verbs for "change a site", and the second is the harder half
of the north star. Both gaps this engine shipped with — no way to move a post (SH134) and
a prune that could not remove a folder (SH135) — were invisible while building and
obvious the first time a section was renamed. They are shipped; what they left behind is
the same shape one level up and one hazard they created:

- **`folder.move` (SH137).** Posts can be re-addressed now; folders cannot. Renaming a
  section still means `folder.upsert` at the new path plus `folder.delete` of the old,
  and that delete cascades over everything beneath it — the same defect as SH134, one
  level up, and strictly more destructive. SH134 is what makes its absence conspicuous:
  an agent that has learned `post.move` will reasonably assume the folder verb exists.
- **Never truncate a deletion plan (SH138).** Widening prune to whole subtrees made a
  plan able to list thousands of deletions, which puts it in reach of the SH78 response
  budget for the first time. A truncated deletion plan is the worst truncation available:
  unlike a shortened sitemap, it does not read as incomplete — it reads as a complete
  review of what is about to be destroyed. The choice is to exempt destructive rows from
  the budget or to refuse the apply; silently shortening the list is not on the table.
- **The console move rewrites URLs (SH139).** `ShObjectWriteService#moveTo` resets a
  post's `furl` to a slug of its title on every move. SH134 spent its design budget on
  exactly this question for the agent path — a move must not change what it was not asked
  to change — while the console path, the one a human triggers by dragging something,
  silently changes a live URL and discards any hand-set one. Found by SH134; worth fixing
  on its own terms rather than as part of it, because it is a visible behaviour change.

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
  first place P5 has been broken by the *language* rather than by a design choice, and it
  will not be the last — which is **SH136**: the manifest is byte-stable by test and the
  context pack by digest, but the MCP resources, the prompt list, and any future
  map-backed payload are unchecked. One lint (no un-canonicalized `Map`/`Set` reaching a
  JSON writer) plus a per-payload stability assertion covers the class.
- **A contract that can only be honoured by remembering belongs inside the method.**
  SH131's bind requirement lived in a comment; SH83 read the comment and still got it
  wrong, and the breach looked like an empty result set — valid data, not an error. The
  test for "is this comment load-bearing?" is: *if someone ignores it, does the system
  fail loudly?* If not, it is not a comment, it is a missing parameter or a missing
  wrapper.

## IX. Perception & verification loop (Block K)

> The agent cannot see. Two of the three human round trips in a typical build exist
> only for that reason, which makes this the cheapest large win after Block H —
> a human cycle is the most expensive unit in the system by orders of magnitude.
>
> **The loop itself shipped** — content lint (SH99), route checks (SH100), render digest
> (SH101), diagnostics (SH103); see [CHANGELOG.md](CHANGELOG.md) Block K. What is left
> below is the design for what the shipped version deliberately does not do, plus the
> two tasks it was always going to depend on (SH102's pixels, SH104's handoff report).

### IX.1 What the lint still owes (SH142, SH143)

> The lint itself shipped (SH99). Two things it does not do yet are the difference
> between a check that is run and a check that is switched off.

**A report has two states; real content has three.** `ok` / `not ok` cannot express
*known and accepted*, and every codebase has some: an image that is decorative in a way
the markup cannot state, a folder deliberately staged empty, a link into a front-end
route. The first legitimate warning that nobody intends to fix is the moment the exit
code stops being read — and then the errors stop being read with it. A committed
baseline of `(rule, address)` pairs that `shio verify` subtracts fixes that, on one
condition: it must **fail when an accepted finding no longer exists**, or it decays into
a blanket suppression that hides the next real defect at the same address.

**A full pass is the wrong shape for the common call.** Verification is read twice for
different reasons: once in CI, where "every post in every site" is exactly right, and
once by an agent that has just written three pages, where it is a hundred times more
work than the question needs. Scoping by folder or address — and by SH88's cursor, which
now exists — makes the second call cheap. The constraint is the SH138 one: a report over
a subset must say so in the payload, because a partial report that reads as a full one
is worse than no report.

### IX.2 Proving a route from the delivery side (SH141)

SH100 answers "every furl resolves" and "internal links land" against the **content
graph**: the set of published friendly URLs, compared through the instance's one
canonical path form. That was the right first move — it needs no front end, no network
and no deployment, so it runs everywhere including a cold CI checkout — but it cannot
see the failure §IX names as the most common one, a page that exists in the CMS and
404s on the site. So `link-unresolved` is a warning, and a warning is what you emit
when you are not sure.

What closes it is not a crawler. SH101 already fetches one URL safely — origin-checked
against the readable sites, opt-in by property — and walking the published set through
that same path turns "the front end does not serve this" into an error that deserves the
name. The part that needs design is the legitimate exception: a front end owns generated
routes the CMS has never heard of, and the answer has to be *declared* (in the site's
own conventions, alongside SH114's memory store) rather than guessed at per-request.
Sitemap-coverage-vs-expectation waits for Block J, since the expectation is a blueprint's.

### IX.4 Visual snapshot + diff (SH102, optional)

Playwright PNG + diff against the previous snapshot — deliberately framed as an
artifact **for the human handoff** (§IX.6) rather than an agent input, so nobody
mistakes it for the primary verification path.

### IX.5 A durable incident feed (SH144)

Diagnostics shipped (SH103) as a bounded in-memory window, and the payload says so:
`since`, `dropped`, and a note that an empty report means "nothing since process start",
not "nothing ever". That honesty is load-bearing — a diagnostics endpoint that cannot
tell those apart is the kind of clean bill of health that costs a human a day — but it is
also an admission. "Was anything failing while I was away" is a question about a period
the window may no longer cover.

The durable form is not a second table with its own retention policy. It is the same
cursor SH88 built for content changes: an incident is a thing that happened at a
position, which is exactly what a change feed already models. Folding the two is cheaper
to build and cheaper to read — one `since=` for "what changed and what broke".

### IX.6 Handoff report (SH104)

`shio report` renders the human-facing change summary — what changed (over §X.3's
feed), preview links per changed page, and explicit open questions — ready to drop
into a PR body or a console banner. This is the *"help the interaction with the
developer"* half of the brief: the agent's output for a human is a reviewable
summary with links, not a wall of prose.

---

### IX.7 Verify a page's section references (SH154)

The lint's `dangling-reference` rule knows two kinds of reference: a `Content Select` field
holding a post id and a `File` field holding an asset id. SH95's section vocabulary
introduced a third — a `Page`'s `sections`, a `Multi Select` of section **friendly URLs**,
in render order — and the lint does not know about it. So a page can name a section that
does not exist, or one that was renamed, and `shio verify` reports `ok: true` over a page
that renders with a hole in it.

This is the one check that makes composition safe, and it is worth stating why the gap
exists rather than treating it as an oversight: `Multi Select` is a free-form list of
strings by design, and most of them are tags. A rule that treated every Multi Select value
as a URL would report a defect for every tag on every post. So the rule needs a way to know
which field holds addresses — the honest options are a widget setting the vocabulary sets
(`multiselect: {postType: …}` already exists and is nearly this), or a convention the
context pack states. Picking one is most of the task.

Two properties to keep. A reference to an **unpublished** section on a published page is
the existing `unpublished-dependency` case, not a new one. And a reference the credential
cannot read must not be reported as dangling — SH99 already re-checks by id for exactly
this reason, so the allow-list never manufactures a defect.

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

Closing it properly means changing how the row is *written*, not how it is read: a
sequence assigned at commit, or an outbox the reader drains in commit order. That is why
it is a separate task — SH88 is the read shape, and the read shape is right.

### X.6 Project assets too (SH145)

`shio/content/**` holds the posts; the binaries stay on the server, id-keyed under
`store/file_source/<postId>/` (SH38). So a projected `File` field still reads as a UUID,
which means a content tree cannot be moved to another instance, reviewed usefully in a
diff, or committed as a complete description of a site — three of the four things "content
as files" is supposed to buy.

The shape follows the SH86 rules: bytes under `shio/assets/`, addressed by a path, and a
`File` field that projects as that path instead of an id. The interesting question is the
inverse direction — a push has to resolve a path back to a File post, creating one when it
does not exist, and it must be idempotent by path so a second push does not upload a
second copy. That is the same idempotency argument SH81's `post.upsert` settled for posts,
applied to bytes.

### X.7 Page `find` (SH146)

The projection reads the post list through `find`, which caps at
`shio.agent.read.max-limit` and has **no cursor**. A site past the cap therefore cannot be
projected at all: pull refuses rather than writing a tree that omits posts, because the
very next `push --prune` would read those omissions as deletions (the SH138 hazard, on the
file side).

Refusing is the right behaviour for a cap that exists; removing the cap is the fix. The
cursor SH88 built for the change feed is the shape to reuse — keyset over a stable sort,
never an offset, since content is being written while the walk runs.

### X.8 Digest-only merge base (SH148)

The three-way merge needs a base, and the base currently keeps a full copy of every
projected field value in `shio/.shio/content-base.json`. That is one extra copy of the
whole site on disk, and if the file is committed (which is the reasonable choice for a
team, since it makes the merge base shared) it is one extra copy in every clone.

A per-field **hash** decides all three cases the merge asks about — did this side change,
did that side change, did both — because every one of them is an equality test against the
base rather than a read of it. Only a genuine conflict needs the base *value*, and by then
the server has been read anyway. The snapshot goes from O(content) to O(fields).

Left as an idea rather than designed: it trades a clear data structure for a smaller one,
and the merge is new. It is worth doing after the merge has been used enough to trust.

### X.9 Folders as files (SH149)

A folder exists in the projection only because a post names it — `push --content` derives
the declared folder set from the posts' `folder` values plus their ancestors. So an empty
section cannot be authored locally (there is no file to write), and a pull drops one that
exists on the server (there is nothing to write it to).

That is a real gap for the agent-first workflow: "scaffold the structure, then fill it" is
exactly how a site gets built, and the structure step currently has to go through the API
while the content step goes through files. The fix is a folder marker file — `_folder.md`
or similar, carrying nothing but its own existence and any folder-level metadata — that
push declares directly as a `folder.upsert`.

### X.10 A renamed file is a move, not a re-creation (SH150)

`push --content --prune` sends a rename as `post.delete` plus `post.upsert`, which is
precisely the workaround **SH134** exists to prevent: the replacement gets a new id and
loses the post's audit trail and version history, and any inbound `ShReference` to it
breaks.

The file format already refuses to rename *halfway* — a path that disagrees with its
`url:` frontmatter is an error, because one is a move and the other is a create and
guessing between them either duplicates or deletes a post. So the information needed is
already there: a post present in the base under one address and in the tree under another,
with the same fields, is a **move**, and push should emit `post.move` (SH134) for it.

Two details to settle in the task: how much divergence still counts as the same post (an
address change plus a body rewrite is still a move), and what to do when two files could
each claim the same base post — which, like every other ambiguity in this block, should be
a refusal rather than a guess.

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

### XI.7 A blueprint can create the site it needs (SH151)

Applying a blueprint refuses when the target site does not exist, which is correct and is
also the seam in the §3.1 story: *"`shio_apply blueprint marketing-site` → plan → confirm
→ applied"* is one call only if there is already a site to apply it into. Creating one is
not currently a step a blueprint can take, because `ShSiteWriteService.create()` clones the
SH71 bootstrap template — so "create a site for this package" would mean applying a package
on top of a Welcome page and an About page nobody asked for, and then pruning them.

The fix has two parts and the order matters. First a **blank** site-creation path (a site,
a root folder, no template), which is worth having on its own: the bootstrap clone exists
because an empty site used to be useless, and a blueprint is exactly the thing that makes
it useful. Then `site: {create: true}` on the apply request, and `shio init --blueprint
<name>` in the CLI so scaffolding a project and seeding its content are one command.

The thing to be careful about is P7: creating a site is the most visible side effect in the
system, so it must be explicit in the request, named in the plan before it happens, and
never inferred from "the site you named does not exist".

### XI.8 Additive post-type reconciliation (SH152)

A blueprint provisions a post-type only when the instance does not already have one of that
name, and reports the fields the existing type lacks. That is honest — the alternative,
upserting, reverts a field a human added and flips SH66's `managed` flag from a package the
user never wrote — but it stops one step short of useful: the caller reads
`exists · Page · missing: description, sections`, and until they add those fields by hand
the package's content is writing values the console cannot render and the front end cannot
find.

The middle position is an **additive** merge, opt-in per apply: add the fields the package
declares and the type lacks, never change a field's widget or settings, never remove one,
never touch a system type. That is a well-defined and reversible operation, and it is what
the collision the first-party set actually hits (the bootstrap `Page` versus the section
vocabulary's `Page`) needs.

Two things to settle in the task. A field's **ordinal** has to go somewhere — appending
after the highest existing ordinal is the only choice that cannot reorder somebody's form.
And the report must distinguish "added" from "already there", because a merge that reads as
a provision would hide the fact that the type is now a hybrid of two models.

### XI.9 A plan that needs no write transaction (SH153)

The blueprint plan provisions the package's post-types inside its transaction, plans the
content against them, and marks the transaction rollback-only. It is accurate — without it,
a plan for a package that brings its own types fails on its first post with *"no post-type
named Article"*, which is useless for exactly the case blueprints exist for — and the
reasoning for why this is acceptable where `ShAgentBatchService` refuses it is sound:
provisioning a post-type emits no audit rows, no webhooks, no lifecycle events and no quota
movement, so nothing outside the transaction observes it.

What it costs is a property nobody asked to give up: **a plan can no longer run read-only**.
A read-only credential, a read replica, or a future `AGENT` token scope (SH106) with no
write permission cannot plan a blueprint, even though planning writes nothing that survives.
For a surface whose whole safety story is "look before you write", that is the wrong
constraint to leave in place.

The fix is to thread the *declared* post-types through the batch planner the way it already
threads pending folders: `ShAgentBatchService`'s dry-run session keeps a set of objects
earlier ops would have created, precisely so "create /blog, then put a post in /blog" does
not report a spurious 404. A pending **post-type** set is the same idea one resource wider,
and it removes the transaction rather than justifying it.

### XI.10 Record which blueprint a site received (SH155)

`changed: 0` on a re-apply proves a site matches the document the package compiles to. It
does not record that the document came from `marketing-site 1.0.0` — so a newer version of a
package is undetectable, "which blueprint is this site" is unanswerable, and the version in
the apply response is a fact that exists only in whatever transcript captured it.

The cheap version is a row per applied package (name, version, parameters, when, by whom).
The right version is SH114's instance memory: this is exactly a *decision about the site*
that a later session should not have to re-derive, and it belongs in the store that already
exists to serve those in the context pack. That also answers the question the cheap version
cannot — the parameters are part of the record, so a re-apply can default to what was used
last time instead of asking again.

Not to be confused with drift detection: knowing a site received `marketing-site 1.0.0`
does not mean it still matches it. That answer is already available (`dryRun` and read
`changed`), and this is the fact that makes it possible to *ask* without a human
remembering.

### XI.11 Blueprints carry their front-end half (SH156)

The format supports front-end files, the CLI writes them, and the first-party set ships
none. So a blueprint delivers content that the site cannot render until somebody wires the
components up — which for a section-based package means installing `@viglet/shio-sections`
and writing a page that resolves a `Page`'s section URLs and dispatches on their type. That
is a dozen lines, and a dozen lines an agent has to write from a description is exactly the
prose recipe P8 objects to.

The reason it is not simply "add the files" is that there would then be two starters: the
`create-shio-app` template tree and the packages. SH119 already exists to fold the first
into the second, so this task is that fold seen from the blueprint side, and doing them
together is the only way it results in one starter rather than three.

Whatever ships must keep the rule SH93 established: the **server never writes a project
file**. It returns them, the CLI writes them, and it refuses to overwrite without `--force`
— a blueprint applied twice must not revert a page somebody has since edited.

## XII. Agent safety & human-in-the-loop (Block L)

> An agent-driven CMS is only adoptable if a human can bound it and undo it.
> **Invariant: the agent path is never a privilege escalation** — tenant isolation
> (SH22), content ACL (SH54) and token scopes apply identically on every agent
> surface. An agent-native CMS that quietly runs as root is not adoptable.

> ✅ **SH106–SH110 shipped** → [CHANGELOG.md](CHANGELOG.md) Block L. The sections below
> are what building them opened, and only that.

### XII.6 Confirm tokens across more than one node (SH157)

SH108's token is an HMAC over the plan's destructive rows, signed with a secret that is
**random per JVM** unless `shio.agent.confirm.secret` is set. That keeps the surface
stateless — which is the property that lets any instance answer any MCP request — and it
means a plan issued by node A is refused by node B.

The refusal is the part that needs fixing more than the mechanism does. Today an
unrecognised signature is reported as `MISMATCH`, whose message says *the content changed
between the plan and this call* — a true statement about the common cause and a completely
misleading one here, because it sends the caller round the plan/apply loop forever. Two
honest fixes, and they are not exclusive: derive the secret from something the cluster
already shares (the database, an operator-set value validated at boot rather than silently
defaulted), and make the multi-node case *nameable* so the error can say "this plan was
issued by another node" instead of guessing. A gate whose failure mode is an infinite retry
is worse than one that is occasionally strict, because the retry looks like progress.

### XII.7 Revert an edit, not just a lifecycle change (SH158)

SH109's revert undoes four of the six transitions exactly: create→delete to the SH55 trash,
delete→restore, publish↔unpublish. `UPDATE` has no inverse, because the audit row records
*that* a post changed and not what it changed *from*, and `RESTORE` is already an undo.

The queue reports both as skipped with the reason, which is honest and half a feature. SH14
already stores the versions, so the missing piece is not storage but a **decision the
reviewer has to be shown**: "restore the version immediately before this run" is well
defined per post, and is not the same as "undo this one edit" when a run edited the same
post three times. The right shape is probably per-post rather than per-session — a revert
button on a change row, next to the version it would restore — because bulk-reverting edits
without showing what comes back is exactly the surprise P7 forbids. Whatever ships must keep
the current property that a partial undo is never reported as a complete one.

### XII.8 Budgets that survive a restart (SH159)

SH110's counters are a bounded LRU in memory. Three things follow, and `/agent/session`
states all three rather than hiding them: a restart resets every budget, an idle session is
forgotten after its TTL, and enough concurrent sessions evict the oldest.

For a runaway guard that is nearly enough — a loop that survives a restart is a different
problem — but it is not enough for a *ceiling* anyone bills against, and the honest reading
is that today's numbers are advisory between restarts. The durable version belongs with
SH45's per-tenant metering rather than in a table of its own: that is where a number which
has to persist and be aggregated already lives, and duplicating the concept would give an
operator two answers to "how much has this tenant used". The thing to preserve is the
current split between **calls** and **writes**: they fail differently, and folding them into
one number lets the cheap failure consume the budget that exists for the expensive one.

### XII.9 The review queue is a queue, not a window (SH160)

`ShAgentReviewService` groups the newest 2000 audit rows and filters to the agent-attributed
ones. On an instance where humans are also working, an old *unreviewed* agent session can
fall off the bottom of that window and disappear from the queue — silently, which is the
part that matters: the screen would say nothing is waiting.

The fix is small and the reason it was not done first is honest — the window was the cheap
way to get grouping. The index on `agent_session` already exists; what is needed is a query
that selects unreviewed agent rows directly (and a session-level projection so the list does
not load every row of every session to count them), plus a stated bound on the result the
way SH138 requires: if the queue truncates, it says how many it could not show. A review
queue that quietly drops the oldest item is the one kind of queue nobody should ship.

### XII.10 Expiring agent credentials (SH161)

§XII.1 asked for a **scoped, budgeted, expiring** credential. Block L shipped the first two.

The machinery for the third is already there: `ShApiToken.expiresAt` exists (SH13 added it
for preview tokens) and `ShApiTokenFilter` stops authenticating a token once it passes, with
no separate revocation step. What is missing is everything around it — a TTL chosen at
creation, a console field, a visible expiry on the token list, and a rotation story that is
not "edit the row by hand". The reason it matters more for `AGENT` than for the other scopes
is what an agent credential ends up living in: a config file in a repository, an environment
variable in CI, a `.mcp.json` on someone's laptop. A delivery key that leaks reads published
content; an agent key that leaks writes it.

---

## XIII. Token economy, measured (Block M)

> The brief asks that this get cheaper over time. Then it must be **measured**, not
> asserted. The concept spec's targets (≈10× tokens, ≈6× tool calls, ≈3× human
> cycles for a canonical site build) are **acceptance criteria for SH112**, not
> claims about the present.

### XIII.1 Task benchmark harness (SH112)

Canonical agent tasks — "build a 5-page marketing site", "add a post-type + 10
posts", "fix a broken link", "translate a site" — measured in **tool calls +
tokens + wall clock**, run in CI, recorded per release. Without this, every claim
in this document is a vibe, and the natural entropy of a codebase (someone adds a
convenient field to a response) silently erodes the whole position.

### XIII.2 Response budget enforcement (SH113) + the leanness policy

CI fails when a canonical response's estimated token size regresses past a
threshold. The cheap, always-on guard between full benchmark runs; the same role
`ShLiquibaseChangelogAgnosticTest` plays for schema portability. The number it
regresses against is now emitted by the instance itself on both the agent and
delivery surfaces (`X-Shio-Est-Tokens`, SH124 — ✅ shipped), so the guard reads a
header instead of re-tokenizing bodies.

**The mechanism already exists — SH132 built it** (✅ shipped) for the MCP tool list,
which is the payload with no opt-out: it is re-sent on every turn of every session
before any work happens. SH113 extends the same file,
`shio-app/src/test/resources/token-budgets.properties`, with the response budgets rather
than inventing a second place to keep a number. Two decisions from SH132 that carry over:
**the ceiling lives next to a note on what it guards**, so raising it is a reviewable diff
with a reason attached instead of an edited magic number three files away; and **a count
is not a budget** — the 12-tool cap was a proxy, and eight tools with essay-length
descriptions are worse than twelve terse ones.

**The leanness policy (SH125 — ✅ shipped) and its binding limit.** Nulls are always
waste and are omitted from every agent-owned DTO. Dropping zero-defaults is *not* a
blanket rule, and the reason generalizes beyond JSON: **a default can be an answer.**
`features: {mcp: false}` is the most valuable thing the manifest says; `truncated:
false` is the answer to "was anything cut?". Stripping either would make the payload
smaller and the protocol worse — silence reads as "unknown", and an unknown sends the
caller probing, which is the cost the whole block exists to remove. So: NON_NULL
baseline, NON_DEFAULT only where absence and default genuinely coincide (row-shaped
DTOs). Any future "make it smaller" change must clear that test.

**Still open (SH127):** the delivery path stays null-carrying, because
`@viglet/shio-client` declares nullable CDA fields as `string | null` rather than
optional — omitting a null unilaterally would turn a declared `null` into
`undefined` for typed consumers. Backend and SDK types have to move together, which
also removes the nulls the SH77 context pack inherits through the shared
`ShCdaSchemaField` shape (the visible price of P10 composition over a frozen
contract, currently pinned by a characterization test).

### XIII.3 Instance memory / conventions store (SH114)

Decisions and conventions stored as first-class content and served in the context
pack — "sections come from `@viglet/shio-sections`, don't invent layout post-types",
"copy is authored in files, never via the API", "this site's blog uses `Article`,
not `Post`". This is the **compounding saving**: what one session learned, the next
session is told. Written by the agent (with human approval, §XII.4), read by every
future session.

### XIII.4 `AGENTS.md` generator (SH115)

`shio context --as agents-md` renders project agent instructions from the live
instance + memory store, so they **cannot rot**. A hand-maintained `CLAUDE.md`
describing a content model is stale the moment the model changes, and a stale
instruction file is worse than none — it costs tokens *and* misleads.

### XIII.5 Agent-read caching (SH116)

Strong ETags on manifest/context/schema; an unchanged context returns `304` and
costs ~0. The infrastructure exists (SH3 already put `ShallowEtagHeaderFilter` on
the CDA) — this extends it to the agent surface and makes "re-read the context" the
cheap default instead of a decision the agent has to weigh.

---

## XIV. Realignment of what already shipped (Block N)

> The reorientation is mostly additive, but it does invalidate a few existing
> choices. Concept spec §10 has the full table.

### XIV.1 Unify the two post-type APIs (SH117)

Today there are two: the SH16 **name-keyed, lossless** `/api/v2/post-type` (CLI,
CSRF-exempt) and the console **id-keyed, entity-shaped** `/api/v2/post/type`
(session/CSRF). Two APIs for one concept is a tax paid on every agent session
("which one, and by id or by name?") and a violation of P5/P10. Converge on the
name-keyed surface, migrate the console onto it, retire the id-keyed one.

### XIV.2 `create-shio-app` templates → blueprints (SH119)

Fold the template tree into the §XI.1 blueprint format; `shio init` stays a thin
wrapper. One appliable format instead of two half-formats.

### XIV.3 Align console read shapes with the CDA DTOs (SH120)

Where the shapes overlap, the console should return the CDA DTO rather than a
second shape of the same thing — one shape for the agent to learn (P5/P10).

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
