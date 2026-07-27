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
> (SH101), diagnostics (SH103), and the follow-ups that made the report usable in anger
> (SH142's accepted findings, SH143's scoping, SH154, SH169); see
> [CHANGELOG.md](CHANGELOG.md) Block K. What is left below is the design for what the
> shipped version deliberately does not do, plus the two tasks it was always going to
> depend on (SH102's pixels, SH104's handoff report).

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
>
> SH106–SH109 have shipped; their design notes are gone with them (see CHANGELOG.md Block L).
> What remains here is the one task still blocked.

### XII.5 Agent budget guards (SH110)

Per-session call/write/storage ceilings, layered on SH45's per-tenant metering when
that lands. A runaway agent should hit a ceiling and report it, not a bill.

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

### XV.1 A built-in HTML renderer for the section vocabulary (SH183)

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

**Why this is not the dropped Block D.** The binding non-goal is *"No GraalJS server-side JS
template engine"*, inherited from the Nashorn engine the original product shipped (still alive
on the `FSP` branch). §0.4's argument is specific and still correct: an in-JVM script engine is
**sandboxing liability with no agent-side benefit**, because what it executes is user-supplied
code. Choosing Groovy instead changes the syntax and not one word of that argument — Groovy is
named in the non-goal for exactly this reason, and it is *more* powerful than Nashorn inside
the JVM, not less.

A renderer over the SH95 vocabulary executes nothing. The eight section post-types are a
closed, first-party set with known fields, so turning `SectionHero{heading, subheading, body,
ctaLabel, ctaHref, align}` into semantic markup is a **projection** — the same kind the CDA
already performs into JSON, and the same kind `ShAgentRenderService` already performs into a
structural digest now that SH169 composes a page's sections. There is no user template, so
there is nothing to sandbox. That is why this is buildable today and the script engine was
not: the vocabulary *is* the template.

**Who renders — the three options that respect the non-goal.** They differ by where the
markup is produced, and they are complementary rather than exclusive:

| Renders | Mechanism | What it costs | What it buys |
|---|---|---|---|
| **Shio (Java)** | declarative HTML from the vocabulary | a new renderer, preview-grade | a site is visible with **no client setup at all** — the only option that works for an MCP-only agent |
| **Browser** | a statically exported React/Next bundle served by Shio, fetching from the CDA | almost nothing new — Shio already serves `/console/**` and `/file_source/**` | full design freedom, no Node process in production |
| **Node** | Next.js, as today | a project and a running process the agent cannot create over MCP | SSR, ISR, the real delivery path |

Note what the middle column rules out. "Deploy React inside Shio and let it render" splits in
two: server-side-rendering React *in the JVM* requires a JS runtime and is the non-goal again
under a new name; serving a **static export** requires no engine and is genuinely cheap. Only
the second is available, and it is worth having — but it renders in the browser, so a page's
content arrives by client-side fetch and the first paint has no content in it, which is the
wrong trade for preview and for anything SEO touches.

The recommendation is therefore the first row **as the default visible path**, with the second
as the upgrade a designer reaches for and Next.js unchanged as production delivery (P10).

**What it must not become.** A preview URL that renders production content *is* a delivery
path the moment somebody points DNS at it — and then Shio owns HTML rendering, caching and
SEO, the position §0.3 and §0.4 declined. So the boundary has to be visible in the product,
not merely asserted here: a distinct route, an unmistakable banner, no freshness headers that
invite a CDN, and drafts reachable only behind the SH5 preview token.

**Three decisions the task owns.** *Where the markup lives* — `@viglet/shio-sections` already
ships React components with stable `shio-*` class names, and two renderers for one vocabulary
will drift, so the Java side must emit the same structure and classes with a test comparing
them, the way `emit.test.mjs` already guards the two copies of the post-type definitions.
*What a non-vocabulary post-type does* — a `Page` with an `HTML Editor` field is trivially
renderable and an arbitrary custom type is not; the honest answer is a labelled field dump,
not a blank page. And *styling* — the components are deliberately unstyled, so this needs a
minimal default stylesheet that makes a page legible without pretending to be a design.

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
is the expensive unit Block K exists to remove. SH102 already proposes the right instrument —
a snapshot and a diff — but scoped *"for the human handoff rather than for the agent"*, and
that framing is what to revisit: visual autonomy needs the agent to be the reader. SH183 makes
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
