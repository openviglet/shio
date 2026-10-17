# SH74: Shio as an agent-native CMS, concept, protocol & backlog

> Roadmap: [ROADMAP.md](../ROADMAP.md) Blocks **H–M** · Design: [IMPROVEMENTS.md](../IMPROVEMENTS.md) §0, §VIII–§XIII
> Status: 📋 designed, not started · deps: the shipped headless foundation (SH1–SH16, SH51–SH55, SH62–SH73)
> Scope: this document is the **product reorientation**, the concept, the design
> laws, the protocol, and the decomposition into Blocks H–M. Each block gets its
> own implementable spec later (`SH75-…`, `SH86-…`, …); this one is the constitution.

---

## 1. The shift

Shio today is a **headless CMS for JavaScript developers**: a Java content engine,
a versioned Content Delivery API, TS SDKs, a Next.js starter, post-types as typed
code, a React console. That foundation is done and it is good. It optimizes for a
**person** who reads docs, clicks a console, and writes a front end by hand.

The new orientation keeps every one of those parts and changes **who the primary
operator is**:

> **Shio is a CMS whose primary operator is a coding agent.**
> The agent builds and maintains the site. The curator approves, corrects and tweaks.
> The CDA delivers. Every design decision is judged by *agent cycles and tokens*,
> not by clicks.

This is not "add AI features to a CMS". Shio hosts no model, no prompts, no API
keys, and does not generate content itself (see §9, non-goals). It is the
**substrate an external agent drives**: the thing on the other side of the tool
call. The differentiator is protocol quality, not intelligence.

### 1.1 Why this is the right bet

- **Coding agents are now the majority builder of small-to-mid sites.** The
  bottleneck is no longer "can a JS dev use this API", it's "how many tool calls
  and tokens does an agent burn before the site is right".
- **Nobody has built for it.** Contentful/Strapi/Sanity ship MCP servers that are
  thin CRUD wrappers over console-shaped REST: entity-shaped payloads, UUID
  addressing, no plan/apply, no verification, errors that don't teach. They are
  *reachable* by an agent, not *designed for* one. That gap is a real, defensible
  position and it is available now.
- **It compounds with what already shipped.** Post-types as code (SH62–SH66),
  the exchange package format (SH68), the drift guard (`push --check`, SH65), the
  audit trail (SH51), soft-delete (SH55), scoped tokens (SH2/SH5) are *exactly*
  the primitives an agent-native protocol needs. This is a reframe with a running
  start, not a rewrite.
- **The curator's path is not sacrificed, it is promoted.** With the agent doing data
  entry and scaffolding, the console stops being a form-filling tool and becomes
  what it should be: review, approval, and visual tweaking (the Universal Editor,
  SH10). That is a better console, not a deprecated one.

### 1.2 The three actors

| Actor | Interface | Job |
|---|---|---|
| **Agent** (Claude Code) | MCP tools + files on disk + CLI | Build & maintain: model, structure, content, front end, verification |
| **Curator** (developer / editor) | React console, Universal Editor, preview links, PR review | Curate: approve, correct, tweak copy, decide |
| **Front end** (Next.js/any) | CDA (REST/GraphQL) + SDKs | Deliver |

Everything below serves the first column without degrading the other two.

---

## 2. The ten design laws

These are binding. A feature that violates one is wrong even if users ask for it.

**P1, Agent-first design order.** Every capability lands in this order:
**MCP tool → files/CLI → REST → console UI.** A feature whose *only* interface is
the console is incomplete. (Inverts the current, GUI-first habit.)

**P2, One call should replace a session of exploration.** Discovery is a product
feature, not a docs problem. If an agent needs 12 calls to learn what a site
contains, that is a bug in Shio, not in the agent.

**P3, Tokens are a measured budget.** Every agent-facing response has a size
budget; every canonical task has a measured token cost; a regression is a build
failure (SH112/SH113). "It got cheaper" must be a number, not a claim.

**P4, Files beat APIs for authoring.** An agent is far better and cheaper at
`Edit`/`Write`/`Grep` over a text tree than at read-modify-write over HTTP.
Project content to disk, reconcile with the server (Block I).

**P5, Determinism over cleverness.** Stable ordering, **path addressing**,
idempotent writes, byte-identical serialization. A re-read must be free (ETag
`304`) and a diff must be meaningful. No UUID bookkeeping in the agent's head.

**P6, Errors are instructions.** Every 4xx carries the fix: what was wrong, the
allowed values, the nearest match, a minimal correct example. An error that costs
a round trip to interpret is a defect.

**P7, Never surprise the curator.** Draft by default, dry-run available, explicit
publish, confirm tokens for destructive ops, every change attributed to an agent
session, a review surface to approve or revert.

**P8, Skipping steps requires machine-applicable units.** A recipe an agent has
to *read and reimplement* saves nothing. Ship **blueprints**: parameterized,
appliable, verifiable packages (Block J).

**P9, Close the perception loop.** The agent cannot see. Give it cheap, textual
proof that what it built is correct, lint, route checks, a structural render
digest (Block K). Otherwise every mistake costs a round trip to a person.

**P10, Compose, don't fork.** MCP is a **shape** over the existing CDA + console
services. It is not a third contract with its own semantics. (Extends the standing
non-goal.)

---

## 3. Where the tokens and cycles actually go today

Honest accounting of a real task, *"build viglet.org, authorable in React"*,
against Shio as it stands. Figures are order-of-magnitude estimates from the
current response shapes; SH112 replaces them with measurements.

| Phase | Today | Cost driver |
|---|---|---|
| Learn the instance | `GET /api/v2/post-type` returns **lossless entity trees for every type**; `GET /site`; walk `/object/{id}/list` per folder | Entity-shaped JSON, no projection, one call per folder. **~15–40k tokens, 10–30 calls** |
| Create the model | `shio push` (good, already code-shaped) | Cheap ✅ |
| Create structure + content | POST per folder, POST per post, read-back per post, UUIDs threaded by hand | **~1–3k tokens per write**, 20–40 calls, no atomicity |
| Wire the front end | Hand-write routes/components from a doc recipe | Reimplementation from prose |
| Verify | *Nothing.* Ask a person to look | **2–3 curator round trips**, the most expensive unit in the system |
| Next session | Re-derive everything (stale `CLAUDE.md` or none) | Full discovery cost again, every time |

Ballpark: **~80–150k tokens, 40+ tool calls, 3 curator cycles.** Two of those three
curator cycles exist purely because the agent cannot verify its own work.

### 3.1 The same task under this proposal

```
1. shio_context                        → model + sitemap + conventions, terse   (~2–4k, cached; 304 next time)
2. shio_apply blueprint marketing-site → plan → confirm → applied               (~1k)
3. (author copy as files: Write/Edit under shio/content/**)                     (0 API tokens)
4. shio_push --content                 → plan + apply, idempotent by path       (~0.5k)
5. shio_verify                         → lint + routes + render digest: PASS     (~0.3k)
6. shio_report                         → handoff summary + preview links        (~0.3k)
```

Target: **~10–15k tokens, ~6 tool calls, 1 curator cycle (approve).** ≈10× tokens,
≈6× calls, ≈3× curator cycles. These are the **targets SH112 must prove or falsify**,
they are stated as acceptance criteria, not as achievements.

### 3.2 The compounding part

The steady-state cost matters more than the first build. Three mechanisms make
session N+1 cheaper than session N:

1. **Cacheable context** (SH116): unchanged context returns `304`, costing ~0.
2. **Delta reads** (SH88): `changes?since=<cursor>` returns only what moved,
   including the curator's console edits. A follow-up session syncs in one small call.
3. **Instance memory** (SH114/SH115): conventions and decisions are stored in the
   instance and served in the context pack, and `AGENTS.md` is *generated* from the
   live instance instead of hand-maintained and rotting. The agent stops
   re-deriving what a previous session already learned.

---

## 4. Block H: the Agent Gateway (the protocol)

The core deliverable. Supersedes the old SH24 epic (SH32–SH37), which was "MCP
tools over the CDA", necessary but far too thin: CRUD-over-MCP reproduces every
cost in §3.

### 4.1 Transport & host (SH75)

- Spring-hosted MCP server, **streamable HTTP**, mounted at `/mcp`.
- `shio mcp` in the CLI is a **stdio bridge** to it, so local dev is
  `claude mcp add shio -- shio mcp` with no CORS/auth ceremony.
- Auth: the SH2 token model extended with an **`AGENT` scope** (SH106), scoped,
  budgeted, expiring, `mayPublish` off by default.
- Every session gets an **agent session id**, stamped on every write into the SH51
  audit trail. This is what makes Block L's review surface possible.

### 4.2 The tool surface: deliberately small

Nine tools. Intent-named, path-addressed, terse by default, dry-run wherever they
write. A tool count in the forties is itself a token cost (schemas are context).

| Tool | Purpose | Notes |
|---|---|---|
| `shio_context` | Model + sitemap + conventions + cursor | The §4.3 context pack. Cached, ETag'd |
| `shio_find` | Search/filter by path, type, field, text → **paths** | Over SH52 search; returns identifiers, not bodies |
| `shio_read` | Read one or many by path, with `fields` projection | Never returns more than asked |
| `shio_write` | Upsert content (DRAFT), batch, idempotent by path, `dryRun` | One call for N posts |
| `shio_apply` | Apply a blueprint or a desired-state doc | Returns a **plan**; applies on confirm |
| `shio_publish` | Publish / unpublish / schedule by path | Gated by `mayPublish` (P7) |
| `shio_verify` | Lint + routes + render digest | Returns a report + pass/fail |
| `shio_assets` | Upload / list / transform-URL for media | Over SH38–SH43 |
| `shio_changes` | Delta since a cursor | Includes a curator's console edits |

**MCP resources** (read-only, cacheable, zero-argument): `shio://manifest`,
`shio://context`, `shio://schema/{postType}`, `shio://blueprints`.

**MCP prompts** (guided multi-step workflows the server owns, so the agent doesn't
have to invent them): `new-site`, `add-section`, `translate-site`, `audit-content`.

### 4.3 The context pack (SH77): the single most valuable endpoint

One deterministic, token-budgeted payload answering everything an agent asks at
the start of a session:

```yaml
instance: { version, features: [i18n, transforms, tenants], apiVersion: v2 }
site: viglet.org
model:                        # post-types, terse: name, fields as "name:widget[!]"
  Page:    [title:Text!, slug:Text!, body:HTMLEditor, sections:Relator]
  Article: [title:Text!, summary:TextArea, body:HTMLEditor, cover:File]
sitemap:                      # paths, not UUIDs; depth-limited, counted
  /:                Page   published
  /blog:            Folder (12 posts)
  /blog/hello-world: Article draft
conventions:                  # SH114 instance memory
  - "Sections are composed from @viglet/shio-sections; do not invent post-types for layout"
  - "Copy is authored in shio/content/**.md and pushed; never edited via the API"
cursor: "c:8f21e0"            # feed to shio_changes next session
```

Properties that make it work: **terse encoding** (§4.4), **deterministic order**
(so it caches and diffs), **budgeted** (a hard token ceiling with an explicit
truncation cursor rather than a silent cut), **path-keyed** (P5), and
**self-describing feature flags** so the agent never probes for a capability.

### 4.4 Token-lean responses (SH78)

- `fields=` projection on every agent-facing read (return what was asked, nothing else).
- `format=terse`: a compact line-oriented rendering instead of deep JSON.
  Entity JSON spends most of its bytes on punctuation, repeated keys, and
  authoring metadata (`owner`, `date`, `publishStatus`, `modifier`) an agent never reads.
- `X-Shio-Est-Tokens` response header + a hard per-response budget; over-budget
  responses truncate **with a cursor**, never silently.
- Corollary rule: **agent-facing reads never include authoring metadata by default.**
  The CDA DTOs (SH1) already made this choice; agent responses inherit it.

### 4.5 Path addressing (SH79)

`site:viglet.org/blog/hello-world` addresses reads *and* writes, everywhere. UUIDs
remain the storage key and stay valid, but stop being the agent's currency. This
single change removes a whole class of failure (UUID threading across calls) and
makes writes naturally idempotent, "upsert this path" instead of "create, then
remember the id, then update".

### 4.6 Teaching errors (SH80)

RFC 9457 `application/problem+json`, extended:

```json
{ "type": "https://shio.dev/errors/unknown-widget", "status": 400,
  "title": "Unknown widget 'RichText'",
  "detail": "Field 'body' of post-type 'Article' names a widget that does not exist.",
  "fix": "Use one of the 14 catalog widgets. 'HTML Editor' is the closest match.",
  "allowed": ["Text","Text Area","HTML Editor","..."],
  "didYouMean": "HTML Editor",
  "example": { "name": "body", "widget": "HTML Editor" } }
```

Applied uniformly across CDA, console API and MCP. Each field here is a round trip
the agent does not have to spend.

### 4.7 Batch + plan/apply (SH81, SH82)

- **Batch**: `POST /api/v2/agent/batch`: N ops, atomic, `dryRun`, per-op results.
  Creating a 12-page site structure is one call, not 24.
- **Plan/apply**: generalize `shio push --check` (SH65) from post-types to
  **content**: a declarative desired-state document → `plan` (a diff, no writes) →
  `apply`. This is the pattern that lets an agent act confidently without
  read-modify-write loops, and lets a curator review before anything changes.

---

## 5. Block I: content as files (P4)

The largest cycle reducer, and the least obvious. Authoring over HTTP is the wrong
medium for an agent; a git-diffable text tree is the right one.

- **Projection format (SH86)**: `shio/content/<site>/<path>.md`: YAML frontmatter
  = attrs, body = the primary rich-text field; `shio/assets/` for binaries.
  Lossless round-trip, deterministic serialization (the same discipline as SH68/SH63).
- **Two-way sync (SH87)**: `shio pull --content` / `push --content` on the SH82
  plan/apply engine, with `--check` drift for CI.
- **Change feed (SH88)**: `changes?since=<cursor>` over the SH51 audit, so the
  curator's console edits flow back and a follow-up session reads only the delta.
- **Conflict policy (SH89)**: per-field 3-way merge with explicit conflicts;
  never a silent clobber. Both actors are legitimate writers (the same reasoning
  that produced the SH66 "managed by code" lock).
- **Watch mode (SH90)**: `shio dev` syncs both directions and emits an
  agent-readable event log.

The payoff: during authoring the agent uses `Write`/`Edit`/`Grep`, its cheapest,
most reliable tools, and the API cost collapses to one plan + one apply.

---

## 6. Block J: blueprints (P8), and the Claude Code plugin

"Skip steps with things that are already done" only works if the ready-made thing
is **appliable**, not readable.

- **Blueprint format (SH92)**: a versioned, parameterized bundle:
  an SH68 exchange package (post-types + folders + content) **+** `@viglet/shio-model`
  DSL modules **+** optional front-end files **+** a `params` JSON-schema **+**
  post-apply verification (Block K). One format; the existing exchange engine
  already imports most of it.
- **`shio apply` (SH93)**: the verb, with plan/dry-run, as CLI and MCP tool.
- **First-party set (SH94)**: `marketing-site`, `blog`, `docs`, `landing`,
  `product-catalog`. Each ships model + sample content + routes + verification.
- **Section library (SH95)**: the highest-leverage item in this block:
  composable page-section post-types **plus** matching React components
  (`@viglet/shio-sections`). A page becomes an ordered list of *known* sections
  (`hero`, `feature-grid`, `cta`, `logo-wall`, …) instead of invented ad-hoc
  post-types. This gives the agent, the console and the front end **one shared
  vocabulary**, which is exactly what makes "now change the site" cheap instead of
  archaeological. It is also what the curator's Universal Editor gets to manipulate.
- **Registry + community convention (SH96)**: `shio-blueprint-*` naming, manifest
  validation, a curated index (absorbs SH28/SH29/SH31).
- **Claude Code plugin (SH97)**: Shio ships a `.claude` plugin: MCP server config,
  skills (`shio-site`, `shio-content-model`, `shio-verify`), slash commands, and a
  generated `AGENTS.md` fragment. An agent session starts *already knowing* Shio
  instead of discovering it. This is the distribution channel for the whole concept.

---

## 7. Block K: the perception loop (P9)

Two of the three curator cycles in §3 exist because the agent cannot check its work.

- **Content lint (SH99)**: required fields, dangling references, missing assets,
  duplicate furls, orphan folders, unpublished dependencies → machine-readable
  report + exit code.
- **Route/link verification (SH100)**: every furl resolves; internal links and
  images return 200; sitemap coverage matches the blueprint's expectation.
- **Render digest (SH101)**: render/fetch a delivery URL and return a **token-lean
  structural digest**: heading outline, landmarks, link/image inventory with alt
  text, word counts. Not pixels, the cheap textual proof that the page is
  structurally right. This is the single most requested missing capability.
- **Visual snapshot + diff (SH102, optional)**: Playwright PNG + diff vs previous,
  for the curator handoff rather than for the agent.
- **Diagnostics (SH103)**: `GET /api/v2/agent/diagnostics`: last N errors, failed
  webhooks, broken transforms, schema drift. One call instead of log grepping.
- **Handoff report (SH104)**: `shio report` generates the curator-facing change
  summary: what changed, preview links, open questions. Drops straight into a PR
  body or a console banner. This is the "help the interaction with the developer"
  half of the request.

---

## 8. Block L: safety and human-in-the-loop (P7)

An agent-driven CMS is only adoptable if a curator can bound it and undo it.

- **`AGENT` scope + session identity (SH106)**: scoped, budgeted, expiring
  credential; every write tagged with the session id (SH2/SH5 + SH51).
- **Draft-by-default + publish gate (SH107)**: MCP writes land in DRAFT; publish
  is a separate tool, disabled unless the token says `mayPublish`.
- **Confirm tokens (SH108)**: deletes/prunes/apply-with-prune take two steps, the
  second carrying the **plan hash**, so an agent cannot destroy something it didn't
  just show the curator.
- **Review queue (SH109)**: a console surface: "the agent proposed N changes" →
  diff (reuse SH14), approve, revert (reuse SH55 trash). The curator's new primary
  workflow.
- **Budget guards (SH110)**: per-session call/write/storage ceilings (ties to SH45).

Note the invariant: **the agent path is never a privileged bypass.** Tenant
isolation (SH22), content ACL (SH54) and token scopes apply identically. An
agent-native CMS that quietly runs as root is not adoptable.

---

## 9. Block M: the token economy, measured (P3)

The stated goal is that this gets cheaper over time. Then it must be measured.

- **Benchmark harness (SH112)**: canonical tasks ("build a 5-page marketing
  site", "add a post-type + 10 posts", "fix a broken link", "translate a site")
  measured in **tool calls + tokens + wall clock**, run in CI, recorded per
  release. The §3.1 numbers become a tracked series or are refuted.
- **Response budget enforcement (SH113)**: CI fails when a canonical response's
  estimated token size regresses past a threshold. Prevents the slow re-accretion
  of entity-shaped payloads.
- **Instance memory (SH114)**: conventions and decisions stored as first-class
  content, served in the context pack. The compounding saving.
- **`AGENTS.md` generator (SH115)**: project agent instructions rendered from the
  live instance + memory store (`shio context --as agents-md`), so they cannot rot.
- **Agent-read caching (SH116)**: strong ETags on manifest/context/schema; an
  unchanged context costs a `304`.

---

## 10. What changes in what already shipped

The reorientation is mostly additive, but it does invalidate some existing choices.

| Change | Task | Why |
|---|---|---|
| **Unify the two post-type APIs** onto one name-keyed surface (`/api/v2/post-type`), migrate the console off the id-keyed `/api/v2/post/type` | SH117 | Two APIs for one concept is a tax an agent pays on every session (which one? by id or by name?). P10. |
| **`create-shio-app` templates become blueprints** (SH92 format); `shio init` stays a thin wrapper | SH119 | One appliable format instead of two half-formats. P8. |
| **Align console read shapes with the CDA DTOs** where they overlap | SH120 | One shape to learn. P5/P10. |
| **Drop Block D (GraalJS server-side JS templates, SH17/SH18)** → non-goal | n/a | An agent writes Next.js; it does not need an in-JVM template language. A sandboxed script engine is pure liability with no agent-side benefit. |
| **Old SH24 MCP epic (SH32–SH37) superseded** by Block H | n/a | It was CRUD-over-MCP; it would have reproduced every §3 cost. |
| **Old SH23 marketplace (SH27–SH31) absorbed** into Block J | n/a | A browsable template catalog becomes an agent-appliable blueprint registry. |

Retained unchanged and *more* valuable under this orientation: the CDA (SH1–SH5),
the SDKs (SH6/SH7), post-types as code (SH62–SH66), the exchange engine
(SH68–SH72), the audit trail (SH51), soft-delete (SH55), the Universal Editor
(SH10) and the Media Library (SH73), the last two are now the *curator's* surfaces.

---

## 11. Non-goals (binding)

- **Shio does not host a model.** No bundled LLM, no prompt runner, no provider API
  keys in the CMS, no "generate this page for me" button. Shio is the substrate an
  external agent drives. Adding inference would trade a defensible protocol
  position for a commodity feature and would put someone else's rate limits and
  safety posture inside the content engine.
- **No third API contract.** MCP is a shape over the CDA + console services (P10).
- **The agent path is not a privilege escalation.** ACL, tenant and scope always apply.
- **No GraalJS server-side JS template engine** (see §10).
- **No feature whose only interface is the console** (P1).
- **Do not rewrite the backend**: the original §0.3 reasoning is untouched and
  still binding.
- **Do not optimize for a benchmark by degrading correctness**: a terse response
  that omits something the agent then has to ask for twice is a regression, not a win.

---

## 12. Acceptance criteria for the concept (not the code)

This spec is done when:

1. `ROADMAP.md` carries Blocks H–M with one terse line per task, deps, and `→ §`
   pointers; Block D is gone; SH23/SH24 are marked superseded.
2. `IMPROVEMENTS.md` §0 states the agent-native north star and the ten laws, and
   carries a design section per new block.
3. `agents.md` tells a future session the product orientation before it touches code.
4. Every new task is traceable to one of the ten laws and to a §3 cost line.

The first *implementation* milestone (Block H: SH75 → SH77 → SH78 → SH80) is
independently useful: manifest + context pack + terse reads + teaching errors
already collapse most of the §3 discovery cost before a single write tool exists.
