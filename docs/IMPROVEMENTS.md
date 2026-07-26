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

### VIII.1 MCP server host (SH75)

Spring-hosted MCP at `/mcp` over **streamable HTTP**, plus `shio mcp` in the CLI as
a **stdio bridge** so local dev is `claude mcp add shio -- shio mcp` with no
CORS/auth ceremony. Auth extends the SH2 token model with an **`AGENT` scope**
(§XII.1) rather than inventing a credential. Every session carries an **agent
session id**, stamped into the SH51 audit trail on every write — that id is what
makes the §XII.4 review queue possible, so it belongs in the host, not bolted on later.

Design constraint (P10): the host is a **transport + tool registry only**. Tools
delegate to existing services (`ShCdaService`, `ShPostUnifiedWriteService`,
`ShPostTypeAdminService`, `ShSiteImportService`…). No business logic in the MCP layer.

### VIII.2 Agent manifest (SH76) — ✅ shipped

Shipped: `GET /api/v2/agent/manifest` → [CHANGELOG.md](CHANGELOG.md) Block H.
Two design records worth keeping, because later tasks inherit them: (1) **an
unshipped capability is reported `false`, never omitted** — an absent flag makes a
caller probe, which is the exact cost the manifest exists to remove, so SH75/SH77/
SH81/SH92 each flip an already-present flag rather than adding one; (2) the
endpoint index is **curated, not generated** — it is read every session, so it
carries a token budget (a test fails the build past 30 entries). The
`shio://manifest` MCP resource is the same payload once SH75 lands.

### VIII.3 Context pack (SH77) — the highest-value endpoint

One deterministic, token-budgeted payload that answers everything an agent asks at
session start: instance/features, the content **model** in terse form, the
**sitemap by path** (depth-limited, counted), the instance **conventions** (§XIII.3),
and a **cursor** for §X.3. Exposed three ways (MCP resource, REST, `shio context`).

Properties that make it work, each load-bearing:
- **Terse encoding** (§VIII.4) — entity JSON spends most bytes on punctuation,
  repeated keys and authoring metadata an agent never reads.
- **Deterministic order** — so it caches (§XIII.5) and so two dumps diff.
- **Budgeted** — a hard token ceiling with an explicit truncation cursor, never a
  silent cut (a silently truncated sitemap is worse than a small one).
- **Path-keyed** (§VIII.5) — no UUIDs in the agent's working memory.

This single endpoint is intended to replace the 10–30 call, 15–40k token discovery
phase measured in the concept spec §3.

### VIII.4 Token-lean responses (SH78)

`fields=` projection on every agent-facing read; `format=terse` (compact,
line-oriented) alongside JSON; an `X-Shio-Est-Tokens` header and a hard per-response
budget that truncates **with a cursor**. Standing rule: **agent reads never include
authoring metadata by default** (`owner`, `date`, `publishStatus`, `modifier`) — the
CDA DTOs (SH1) already made that call; agent responses inherit it.

Anti-goal (P3 corollary): terseness must not cost a second round trip. A response
that omits something the agent then has to ask for is a regression, and §XIII.2's
benchmark is what keeps that honest.

### VIII.5 Path addressing (SH79)

`site:viglet.org/blog/hello-world` addresses reads **and** writes everywhere. UUIDs
remain the storage key and stay valid; they stop being the agent's currency. Two
consequences worth stating:
1. It removes UUID threading across calls — a real and frequent failure mode
   (the id from call 3 is needed in call 7, and any context compaction loses it).
2. It makes writes **naturally idempotent**: "upsert this path" instead of
   "create, remember the id, then update". That is what makes §VIII.7 and §X.2
   possible at all.

`furl` already resolves posts by URL in the CDA (SH1) — this generalizes the idea
into a first-class addressing scheme covering folders, sites and drafts.

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

### VIII.7 Batch + plan/apply (SH81, SH82)

- **Batch** — `POST /api/v2/agent/batch`: N ops, atomic, `dryRun`, per-op results.
  A 12-page site structure becomes one call instead of ~24.
- **Plan/apply** — generalize `shio push --check` (SH65) from post-types to
  **content**: a declarative desired-state document → `plan` (a diff, zero writes)
  → `apply`. This is the pattern that removes read-modify-write loops *and* gives
  the human a reviewable artifact before anything changes (P7). SH65 already proved
  the shape; this is the same engine over a second resource type.

### VIII.8 The nine tools (SH83)

`shio_context` · `shio_find` · `shio_read` · `shio_write` · `shio_apply` ·
`shio_publish` · `shio_verify` · `shio_assets` · `shio_changes`. Plus resources
(`shio://schema/{postType}`, `shio://blueprints`) and prompts (`new-site`,
`add-section`, `translate-site`, `audit-content`).

Why nine and not forty: **tool schemas are context**. A 40-tool CRUD surface costs
tokens on every single turn of every session, before any work happens. The tools
are therefore *intent-named* (what the agent is trying to do) rather than
resource×verb, path-addressed, terse by default, and dry-runnable wherever they
write. `find` returns **identifiers, not bodies** — the agent then `read`s exactly
what it needs with a `fields` projection.

### VIII.9 Tenant/scope-aware tools + ecosystem (SH84)

Scope every tool per SH22 tenant and wire into Turing (replaces old SH37). Deferred
behind SH83 because tenant scoping is an enforcement pass over a finished tool set,
not a design input to it — but the invariant is fixed from day one: the agent path
is **never** a privilege escalation (§XII).

---

## IX. Perception & verification loop (Block K)

> The agent cannot see. Two of the three human round trips in a typical build exist
> only for that reason, which makes this the cheapest large win after Block H —
> a human cycle is the most expensive unit in the system by orders of magnitude.

### IX.1 Content lint (SH99)

Required fields empty, dangling content/file references, missing assets, duplicate
`furl`s, orphan folders, published posts depending on unpublished ones → a
machine-readable report plus an exit code, as `shio verify` and an MCP tool.
This is the content-side equivalent of `tsc`: the agent's own correctness check,
not a human's review checklist.

### IX.2 Route/link verification (SH100)

Every `furl` resolves; internal links and images return 200; sitemap coverage
matches what the applied blueprint (§XI) promised. Catches the single most common
"looks done but isn't" failure — a page that exists in the CMS and 404s on the site.

### IX.3 Render digest (SH101)

Fetch a delivery URL and return a **token-lean structural digest**: heading
outline, landmarks, link and image inventory with alt text, word counts per
section. Not pixels — the cheap textual proof that the page is *structurally*
right, which is what actually goes wrong (empty section, missing hero, unstyled
list, image with no alt). A screenshot costs orders of magnitude more tokens and
tells the agent less.

### IX.4 Visual snapshot + diff (SH102, optional)

Playwright PNG + diff against the previous snapshot — deliberately framed as an
artifact **for the human handoff** (§IX.6) rather than an agent input, so nobody
mistakes it for the primary verification path.

### IX.5 Agent diagnostics (SH103)

`GET /api/v2/agent/diagnostics`: last N errors, failed webhook deliveries, broken
transforms, schema drift. One call instead of grepping `store/logs`. The complement
to §VIII.6 — teaching errors cover *synchronous* failures; this covers the
asynchronous ones an agent otherwise never learns about.

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

### X.1 Filesystem projection format (SH86)

`shio/content/<site>/<path>.md` — YAML frontmatter = attrs, body = the primary
rich-text field; `shio/assets/` for binaries. Lossless round-trip and deterministic
serialization, following exactly the discipline that made SH68 (exchange) and SH63
(DSL) work: one canonical byte-for-byte serialization so a diff means something and
two writers cannot disagree about formatting.

Open design questions to settle in the SH86 spec: Relator items (nested files vs
inline arrays), locale siblings (SH53 — suffix vs directory), and whether DRAFT and
PUBLISHED both project or only DRAFT (leaning: only DRAFT projects; publish stays an
explicit act, per P7).

### X.2 Two-way content sync (SH87)

`shio pull --content` / `shio push --content` over the §VIII.7 plan/apply engine,
with `--check` drift for CI — the same three verbs post-types already have (SH16/
SH65), extended to content. During authoring the API cost collapses to **one plan +
one apply**, with everything in between happening in the agent's cheapest tools.

### X.3 Change feed / cursor API (SH88)

`GET /api/v2/agent/changes?since=<cursor>` over the SH51 audit trail. Two jobs:
the human's console edits flow **back** into the file tree, and a follow-up session
reads only the delta instead of re-reading the world. This is one of the three
mechanisms that make session N+1 cheaper than session N (§XIII).

### X.4 Conflict policy (SH89)

Per-field 3-way merge with explicit conflict markers and `--ours/--theirs`; never a
silent clobber. Both actors are legitimate writers — the same reasoning that
produced the SH66 "managed by code" lock, applied to content instead of the model.
The lesson from SH66 is worth restating: the fix for two writers is an **explicit
boundary**, not last-writer-wins.

### X.5 Watch mode (SH90)

`shio dev` syncs both directions and emits an agent-readable event log (structured,
greppable) so a failure during a long build surfaces as a line the agent can read,
not a scrollback it has to reconstruct.

---

## XI. Blueprints + Claude Code plugin (Block J)

> *"Skip steps with things that are already done"* only works if the ready-made
> thing is **appliable**, not readable. A prose recipe an agent must read and
> reimplement saves nothing — it just moves the work. **Absorbs the old SH23
> marketplace epic** (SH27–SH31).

### XI.1 Blueprint package format (SH92)

A versioned, parameterized bundle: an SH68 exchange package (post-types + folders +
content) **+** `@viglet/shio-model` DSL modules **+** optional front-end files **+**
a `params` JSON-schema **+** post-apply verification (§IX). One format for "a
starting point", where today there are two half-formats (the `create-shio-app`
template tree and the bootstrap-site exchange package). The exchange engine
(SH70/SH71) already imports most of it — this is largely a manifest + parameter
substitution layer over shipped machinery.

### XI.2 `shio apply <blueprint>` (SH93)

The verb, with plan/dry-run, as CLI command and MCP tool. Replaces old SH27's
`create-shio-app --template`: applying a blueprint to an *existing* instance is the
common case, and scaffolding a new project is the special case of it.

### XI.3 First-party set (SH94)

`marketing-site`, `blog`, `docs`, `landing`, `product-catalog` — each shipping
model + sample content + routes + verification. Replaces old SH30. The
verification part is what makes them trustworthy to apply unattended: a blueprint
that cannot prove it worked is a liability.

### XI.4 Section library (SH95) — the highest-leverage item

Composable page-section post-types **plus** matching React components
(`@viglet/shio-sections`): `hero`, `feature-grid`, `cta`, `logo-wall`, `testimonial`,
`faq`, `rich-text`… A page becomes an ordered list of **known** sections instead of
invented ad-hoc post-types.

Why this matters more than the blueprints themselves: the brief's real pain is
*"if I need to change the site, I want less friction"*. Friction on change comes
from **archaeology** — an agent re-deriving what the previous session invented. A
fixed section vocabulary means the agent, the console and the front end share one
language, so "add a testimonial section to the pricing page" is a single known
operation rather than a design exercise. It is also exactly what the human's
Universal Editor (SH10) gets to manipulate, and what §IX.3's render digest can
verify structurally.

### XI.5 Blueprint registry + community convention (SH96)

`shio-blueprint-*` npm naming, manifest validation, a curated index (absorbs old
SH28/SH29/SH31). Deferred until the first-party set proves the format.

### XI.6 Claude Code plugin for Shio (SH97)

Shio ships a `.claude` plugin: MCP server config, skills (`shio-site`,
`shio-content-model`, `shio-verify`), slash commands, and a generated `AGENTS.md`
fragment (§XIII.4). An agent session starts *already knowing* Shio instead of
discovering it — and this is the **distribution channel for the whole concept**:
the plugin is how a developer adopts agent-native Shio in one command.

---

## XII. Agent safety & human-in-the-loop (Block L)

> An agent-driven CMS is only adoptable if a human can bound it and undo it.
> **Invariant: the agent path is never a privilege escalation** — tenant isolation
> (SH22), content ACL (SH54) and token scopes apply identically on every agent
> surface. An agent-native CMS that quietly runs as root is not adoptable.

### XII.1 `AGENT` token scope + session identity (SH106)

A scoped, budgeted, expiring credential in the existing SH2/SH5 token model, plus
an agent session id stamped on every write into the SH51 audit. Extends the token
model rather than adding a parallel one (P10).

### XII.2 Draft-by-default + publish gate (SH107)

MCP writes land in `DRAFT`; publishing is a separate tool, disabled unless the token
carries `mayPublish`. The unified model's two-row-per-state shape (SH-0d/SH14) makes
this nearly free — draft-targeted writes are already how the Universal Editor
works (SH10).

### XII.3 Destructive-op confirm tokens (SH108)

Deletes, prunes and apply-with-prune take two steps, the second carrying the
**plan hash** from the first. An agent therefore cannot destroy anything it did not
just render for the human — and cannot destroy something that changed underneath it
between plan and apply.

### XII.4 Review queue console surface (SH109)

"The agent proposed N changes" → field-level diff (reuse SH14), approve, revert
(reuse SH55 trash). This becomes the human's **primary** console workflow under the
new orientation, which is why §VIII.1 puts the session id in the host from the start.

### XII.5 Agent budget guards (SH110)

Per-session call/write/storage ceilings, layered on SH45's per-tenant metering when
that lands. A runaway agent should hit a ceiling and report it, not a bill.

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

### XIII.2 Response budget enforcement (SH113)

CI fails when a canonical response's estimated token size regresses past a
threshold. The cheap, always-on guard between full benchmark runs; the same role
`ShLiquibaseChangelogAgnosticTest` plays for schema portability.

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
