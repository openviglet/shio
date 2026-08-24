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

✅ **Block J is shipped** → [CHANGELOG.md](CHANGELOG.md) Block J. The format, the verb,
the first-party set, the section vocabulary, the Claude Code plugin, the five mismatches
that shipping the two halves exposed, and the whole marketplace — catalogue, install,
published seed, CLI, MCP tool and console — are all there, and SH682 closed the block by
giving the two writes the role the blueprint reload already had.

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

## XIV. Realignment of what already shipped (Block N) — what building it opened

> The reorientation was mostly additive, but it did invalidate some existing choices.
> Block N spent them → [CHANGELOG.md](CHANGELOG.md) Block N, and so are the tasks that
> building it opened (SH163–SH167, SH233, SH234), the ones that finishing it turned up
> (SH235–SH237, SH239, SH240, SH668), and the last three, which were all about an
> instrument being wrong rather than a feature (SH729, SH731, SH737). Nothing is left
> open here. Concept spec §10 has the original table.

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

It did, and that precedent is real — the objection deserves an answer rather than a
restatement of the non-goal. Two things changed, and neither is about capability.

*The threat model.* In V7's era a template author was a trusted employee, templates
shipped through a controlled release process, and the server sat on a corporate network.
In Shio a layout is content; content is written by an **agent**, and packages are meant
to arrive from a registry whose trust story is still an open question (SH96 exists
precisely because "where may a package come from" is unanswered). Add the multi-tenant
direction (SH22, SH45–SH47) and "content can execute arbitrary JVM code" stops being a
convenience and becomes a tenant escape. The same design is safe under one set of
assumptions and not under the other.

*The runtime.* Nashorn was deprecated in JDK 11 and **removed from the JDK in 15**. What
is left is the standalone `org.openjdk.nashorn` artifact — the one still sitting unused
in `shio-app/pom.xml`, pinned at 15.7, on a project running Java 21. Building the
product's differentiator on it would mean betting the render path on a library that
exists to ease migration *away* from itself.

So the conclusion is not "V7 was wrong". It is that the part of V7 worth keeping is the
**model** — page → template → component, presentation as managed content — and the part
that has to be replaced is the host that ran it.

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

## XXII Realignment of what already shipped

Block N's family. It holds the cases where a capability shipped complete against its own
brief and incomplete against a law — most often P1, where the console got an interface
and nothing else did. These are not defects in the feature; the feature works. They are
defects in its *reach*, and they are filed separately because the fix is almost never in
the code the original task wrote.

Its recent entries widened that beyond features, and mostly concern instruments failing
to reach: one blind to half the repository (SH738), a *process document* routing a
reader past an instrument that exists (SH739), a rule stopping at a language boundary
(SH740), a ledger taking a shape without the guard that makes it safe (SH741), and a
population found by naming convention rather than by structure (SH742). Nothing is left
open here → [CHANGELOG.md](CHANGELOG.md) Block N.

## XXIII The vocabulary proved live (integration tests against a running instance)

Shio's whole product claim is a vocabulary an external agent drives: thirteen MCP tools
over `POST /mcp` and `shio mcp`, thirty-three CLI commands, and the `/api/v2/agent/**`
ops underneath both. Almost all of it is proved by MockMvc in `shio-app` and by module
imports in `cli/test/` — two tiers that, by construction, cannot be wrong about the
thing an agent actually meets: the tool name it must guess right, the input schema it
must spell, the framing, the response budget, and the shape of the refusal it has to
read to recover. `cli/conformance/` is the tier with an instance, and it calls two
tools.

This block closes that gap deliberately and in order. First the instrument that counts
what has never been spoken live, because six suites with no denominator prove nothing
about coverage. Then the read half, the write half, the teaching errors, the CLI surface
and the live budgets. Then the part a per-verb suite structurally cannot reach: the
chaotic run — verbs interleaved, sequenced wrongly on purpose, permuted under a recorded
seed — because an agent does not call the vocabulary in the order a test author
imagined, and every ordering bug in the product to date has been found by somebody doing
it out of order.

Non-goals bind here as everywhere: the instance runs no browser, and none of this
becomes a third contract.

## XXIV The vocabulary proved live (Block V)

Where the rationale for Block V lives: what a running instance has been asked, and what
it has not. The tier exists because a MockMvc proof and a wire answer can disagree —
over a schema the client had to guess, a refusal slug it has to branch on, or a cache
nothing in a Spring test can have. SH707's matrix is what keeps that honest by naming,
rather than counting, the verbs no live suite has ever called.

## XXV Realignment of what already shipped (Block N)

Where the rationale for Block N lives. The block is a standing category rather than a
project: it receives findings about work that already shipped, so it empties and reopens
rather than finishing, and each reopening is one finding whose repair is not itself a
feature.

## XXVI What the replication round left behind (Block W)

Block Q closed with SH774 after a round that changed what a replica *is* to a curator:
kept markup became content they can move rather than a field they cannot reach.

What it left behind is of three kinds, and none of them is a feature.

**A sweep.** A closed block owes its reader-facing surfaces one pass, and this one
changed the thing those pages describe.

**Corpus gaps the round exposed by needing them.** Every question asked of the section
vocabulary's own render cost went unanswered because the benchmark's pages compose no
sections — so the shape the product is *about* is the one shape nothing measures.

**Capability limits found by hitting them.** A template cannot ask an aggregate question
about a relation, which decided SH772's design rather than being noticed in the
abstract.

The block exists because these were found *while* doing the work and would otherwise
evaporate with the commit that revealed them — the cheapest findings the project ever
gets, and the ones with no reporter but the person who tripped over them.

## XXVII What the gate round left (Block X)

Block W spent thirty-two tasks on the instruments that judge this repository — the
ledgers, the gates, their stamps and the scans that read source. It closed with the four
gates comparable, every declared list joined to one reader, and a flake that had
resisted a hundred and twenty runs both diagnosed and cured.

What it leaves is of two kinds, and this block is named for both.

The first is **consequence**: removing `--test-force-exit` cured the abort and also
removed something the flag had been hiding, which is now somebody's twenty-minute CI
timeout waiting to happen. A round that changes how a suite ends owes the next person
the failure mode it created.

The second is the **method** that round kept rediscovering and never wrote down as
anything runnable. A flake was summoned rather than waited for; a timing claim was wrong
twice because it was unpaired; a scan found two defects in the very document describing
them. Each of those is a technique, and each currently survives only as prose in
`docs/agents/build.md`.

And one thing Block W deliberately did not touch: the **P1 inventory** it produced holds
capabilities an agent cannot reach, most with no task against them. The law the product
is named after has an honest list of its own breaches, and the list has been read more
often than it has been worked.

## XXVIII What the editable surface started (Block Y)

SH809 took the first row of the P1 inventory that named a route it did not have, and
shipped it. The pattern it proved is the point of this block: the inventory has been
printed on every run for many blocks and read more often than it has been worked, and
one row of it turned out to be a small, composable endpoint rather than a subsystem —
because the answer was already written down in the rendered page, for the browser.

It also left two things behind, and both are the ordinary cost of shipping a new surface
rather than a defect in the idea.

The first is a **naming slip in a brand-new contract**, caught by reading the diff
rather than by any guard: an address field that every other endpoint calls `address`.
Cheap now, and a published field name is the most expensive kind of thing to change
late.

The second is the **strictness of the reachability it achieved**. P1's own hierarchy
puts an MCP tool above a declared route — a tool is what an MCP-only client can invoke,
and a route needs HTTP and the manifest to find it. `/agent/editable` is a route. That
is reachable, and it is not the bar the law actually asks for, which the inventory's own
categories say out loud.

Neither was worth holding the task for. Both are worth the block that follows it.

## XXIX The inventory as a worklist (Block Z)

Two blocks ago the P1 inventory was a thing that got printed. SH809 took one row of it
and shipped the route it named; SH811 took the same row to the stricter reach the
inventory itself grades. Both were small, because the honest entries had already done
the hard part — each one names *what would resolve it*, and the test asserts that target
absent, so shipping cannot be forgotten.

That changes what the list is for. It was written as evidence — the first honest measure
of how far P1 is actually honoured — and it is now the backlog for the law the product
is named after.

Which exposes the thing this block starts with. **The list cannot be sorted.** Whether
an entry is a gap or a decision lives in the prose of its reason, so answering *how much
is left* means reading ten paragraphs, and doing that is how this block was planned.
`reviewQueue` is a decision and says so; `visualSnapshot` is honest because the flag
reports false; three are CLI-shaped because their input is a file or somebody else's
URL. The rest are work.

The other half is to keep working it while the reading is cheap. One entry names its
target down to the parameter — a tool that takes no `format`, so the output meant to be
written to a file is reachable only by whoever has a file.

## XL Replication — the palette a source actually wears

Block Q closed and reopened around one question that four rounds of measurement did not
settle: which of a source's colours is *its* colour.

Everything upstream of that now works. `propose` lifts the declared palette whole (133
tokens where it once ranked three literals), the lifted declarations are re-emitted
rather than dropped (SH850), the theme's own names are namespaced so a collision is
reported (SH853), a floor refuses a value that cannot serve its role (SH852), and
`convert` prints which slot each token filled and why the rest kept a default (SH849).

What remains is a judgement, and the sections under this heading are about making it
from evidence a page carries rather than from a property of a colour in isolation.

## XLVI Replication — the structure a source states about itself

§XL is about a source's colours. This one is about everything else a source says plainly
about its own shape and a replica draws from somewhere other than the source: its
navigation, the order its pages are meant to be read in, the labels it gives them.

The pattern is the one the palette work kept finding. A capture records the fact,
`propose` or `convert` has no route for it, and the replica falls back to something
derived from the content model instead — defensible in isolation and wrong beside the
page it replaced. The palette version was ranking utility-class literals while 319
declared custom properties sat unread; the sections here are the same shape on structure
rather than on colour.

What makes these a family of their own rather than a line each under §XL is that the
fallback is usually **invisible**. A wrong accent is a colour somebody can see. A
navigation built from the wrong field renders, links correctly and reads as finished —
it is only wrong to a reader who knows what the source's own menu said, which is nobody
looking at the replica.

The address skips five numbers because each of them was declared and dropped by a
shipped block, and an entry citing one still exists: reusing an address would make that
entry cite this.

## XLVII The corpus, and the shapes it does not carry

SH833 established the rule for section types: a check names any type that loses its last
producer, because a vocabulary entry nothing in the corpus can produce is a capability
nothing exercises. The same question about a **source shape** has no instrument, and the
last two tasks are what that costs.

Both were found by a person building a site, not by a suite. SH873's menu of SEO titles
and SH878's replica of the same shape were sitting in code the whole corpus runs over,
every run, green. Neither could have been caught, because the shape that produces them
is not in the corpus to be captured.

The sections here are about that gap rather than about any one archetype. What makes it
worth a family is that the failure is silent in the direction nobody checks: a suite
over seven archetypes reports seven passes whether the eighth shape is missing or merely
absent, and the count of archetypes is not the count of shapes.

The distinction the entries below turn on is between a fixture that *exercises* a path
and one that merely *does not fail on it*. A capability with one producer is one edit
away from having none, and the run that removes the last one is the run that reports
nothing at all.

## XLVIII The half only the JVM can assert, asserted for the old branch

`ShConvertedLayoutRenderTest`'s own docstring states the rule this family is about: a
template that compiled and listed nothing would pass every check in JavaScript, so the
JVM renders the generated layout and reads what came out. That rule is right and it has
a failure mode of its own — the rendered assertion is about whichever branch the fixture
happens to reach, and a branch added later is covered by nothing while the test stays
green.

The sections here are about that shape rather than about any one branch. A template
assertion in JS proves the markup was written; a render assertion in Java proves it
resolved. When a template gains a branch, the second one does not follow automatically,
and nothing reports the gap: the suite renders one page and finds what it expected,
because what it expected is what the other branch also produces.

The distinction worth holding is between a fixture that reaches a branch and a fixture
that cannot tell which branch it reached. The second is the dangerous one — it passes
for the wrong reason, and it passes just as loudly.

## XLIX A package carries what its manifest does not list

A blueprint is two things at once — a directory of files and a manifest naming which of
them an apply reads — and nothing holds the two together. A file that no key lists is
carried, shipped, kept byte-identical by a generator, and installs nothing.

That is not automatically wrong: `emit.mjs` writes the whole SH95 vocabulary into both
section packages on purpose, and says why. What is wrong is that the reason it gives is
about **provisioning** — *a site that received the full vocabulary can grow a
testimonial section next month* — and a carried file that no manifest lists provisions
nothing, so the benefit the comment claims is not delivered by the arrangement that
claims it.

The sections here are about that gap. It is the second time it has produced a defect:
SH873 found `Menu` and `MenuItem` sitting in both packages' `post-types/` and in neither
manifest, so the navigation mechanism a package needed was one nothing could install,
and both packages listed a page query instead.

The general question is which of the two artefacts is authoritative. A manifest that
must list everything on disk makes the directory the truth and the manifest a derived
index; a directory that may hold more than the manifest reads makes the manifest the
truth and the extra files dead weight a generator keeps refreshing. Both are defensible
and the repository has never chosen, which is why the same defect can arrive twice.

## L An instrument that says RENDERED about what it never looked for

`shio annotations` answers one question — is this page authorable — and it is the
instrument SH465 and SH475 both shipped past. Its summary line is what a reader acts on,
and its exit code is what a gate acts on.

The join it makes is `declared minus annotated`. Nothing in it asks whether a field was
drawn. The sentence it prints about the result says it was:

    N declared field(s) are RENDERED and not editable

That is true of the population SH465 found — a collection row that draws with no
`sh-post` and no `sh-field`, which looks finished and cannot be curated — and it is a
claim about every other member of the same set, made without evidence. A field the
template never draws lands in exactly the same count and is described the same way.

The sections here are about the gap between what a check computes and what its message
says it computed. It is the shape Block W's round found a dozen times in this project's
own instruments, met once more in the one an *operator* reads: SH790 is the nearest
relative — a guard naming allocation when the cause was a sentence, which now computes
which of the two fired.

What makes it worth its own family rather than a wording fix is the second half: the
count also drives the exit code, so a mode whose pages legitimately draw none of those
fields fails the gate every time it is run, forever.

## LI Evidence a coincidence can supply

SH882 gave `shio annotations` a thing it had never had: evidence. A declared field
counts as drawn when its stored value can be found in the served page, which replaced a
sentence that asserted the same thing from the post-type alone.

Evidence has a failure mode an assertion does not, and it arrived on the first live run.
A value of `"3"` is found in almost any page, so the check reported a field as drawn on
a page that does not draw it — and the report is now more convincing than the claim it
replaced, which is the direction that costs a reader something.

The sections here are about what a match has to be before it is evidence. The rule the
first cut used — a non-empty string, found as a substring of the page's collapsed words
— is right for the population it was written against (a heading, a paragraph, a whole
markup blob) and wrong for the one it was not: a scalar short enough to occur by
accident.

A threshold on length would work and is the shape this repository keeps refusing,
because the number is not derivable from anything. What is derivable is the difference
between a value that *is* an element's content and one that merely appears inside the
page — and an annotation attaches to an element whose content is the field, which is
what makes that distinction the one the instrument is actually about.

## LII The page's own fields, on the page a package ships

A section's fields are annotated by both section layouts and have been since the
packages shipped a design. The page's own fields are not, and the difference has never
been argued — it is what nobody looked at, which is a different thing from a decision.

SH766 settled the same question for the layout `convert` writes, and settled it twice
over: a field a layout <em>draws</em> is annotated where it draws it, and a field that
reaches only the document head is declared in a `hidden` element instead — because an
editor cannot attach to a `<title>` or a `<meta>`, and injecting a visible element to
make one reachable would be a worse defect than the one it fixes.

The sections here are about that pattern not reaching the packages. The two layouts are
a starting point a curator is meant to edit, and the first thing anybody edits about a
page is its title.

What makes it worth stating rather than just fixing is how it was found: not by reading
the layout, which several tasks have done, but by running the instrument that exists to
answer exactly this question against a site the package had just built. The check had
been unable to say it — its verdict was a sentence rather than a measurement until SH882
— so the gap survived every round that looked at these packages.

## LIII One rule about what a field is, asked on two surfaces

SH872 gave the annotated values back to the markup that carries them, and the question
that left — a curator's edit stores a key no post-type declares — was answered with a
rule: **a key annotated by the post's own markup is declared by that markup.**
`ShAnnotatedFields` is that rule, and the two readers of the content model on the Java
side both ask it.

The CLI has its own reader of the same model, and it was not told. The sections here are
about the shape that leaves behind: a rule stated once, enforced where it was written,
and silently absent from the other surface that computes the same thing.

It is not the drift SH673 names, where two copies of a fact disagree. There is one fact
and one implementation; what is missing is a *caller*. That is harder to see, because
the surface that never asks does not contradict anything — it simply answers a narrower
question and prints the answer with the same confidence.

What makes it worth a family rather than a line is that the symptom does not look like a
missing rule. It looks like arithmetic: a count over a denominator that no longer means
what the reader assumes, on the one mode the rule exists for.

## LIV The console adopts the shared bento layer (Block AQ)

Shio and Turing are the same design system's two consumers and they do not look alike.
All 24 of Shio's ui files are one-line re-exports of the package, so the primitives are
already shared; what is not shared is the layer above them. Turing's bento redesign -
the airy hero, the frosted hover-lift card, the mosaic, the scroll-linked save bar - is
23 components inside that product's tree, and Shio's console has none of it: 40 of its
43 pages render a console PageHeader, 42 render a flat Card, three carry a sticky save
bar with no morph. This block does the half that has to happen before a single page
changes. The package ships the layer; Shio installs it, builds a parallel shell to
migrate into, declares its surfaces once for the rail and the palette, moves its brand
orange into a token, and translates the chrome. Four of the twelve tasks are instruments
rather than features, and they come first for the reason this project files that
argument every time: a count of what is still console chrome is what says how big this
is, and a textual render proof is what says a migrated surface is right without asking
somebody to look at it.

### LIV.6 The chrome speaks Portuguese too (SH891)

The console ships English and Portuguese and merges its own bundle over the package's
base translations. The bento chrome adds strings that are the package's rather than
Shio's: a palette placeholder and its no-results text, the rail labels, the shortcuts
dialog, and Save and Cancel on the morphing bar. Where the package carries them Shio
inherits both languages; where it does not, Shio adds them. Either way the object-form
default convention applies, so a missing key renders as words rather than as an
identifier, and both locales are updated in the same commit that adds a key. SH887
measured which half is which: the proving page rendered `Unsaved changes` under an
`en-US` browser and `Alterações não salvas` under `pt-BR`, so `registerVigTranslations`
already wires the package's own bundle into this console's i18n in both languages. What
is left for Shio is the half the authoring contract says the package will never hold —
the nav array's own labels, and the palette entries built from it.

### LIV.8 An accessibility baseline that is checked, not remembered (SH893)

The bento layer's accessibility rules are specific and easy to break by accident:
icon-only controls need an aria-label, decorative glyphs need aria-hidden with an
adjacent screen-reader span because a bare SVG's label is not reliably announced, ARIA
values must be string literals rather than boolean expressions, and every animation must
sit inside the reduced-motion guard. They held across 118 pages in one product because
that product's team read one file. Shio has its own lint suite and its own conventions
and none of them know these rules. Add the checks here rather than assuming a package
gate covers a consumer's own use of it.

### LIV.12 The one script that opens a real page (SH897)

e2e/console-check.mjs is the only thing in this repository that drives the console in a
browser, and every route it knows is a console route. Left as it is, it will run green
through the whole of the next block while never visiting a single migrated surface,
which is worse than not running at all: a green check is counted as evidence. Teach it
the bento route tree, and give it the shape of assertion this migration actually needs -
that the hero is present, that the frosted card is frosted, and that the save bar
appears on scroll and not before it.

### LIV.15 The root argument the duplicate gate throws away (SH919)

The design system's `check-duplicates.mjs` parses its arguments like this:

    const manifestFlag = args.indexOf("--manifest")
    const roots = args.filter((a, i) => !a.startsWith("--") && i !== manifestFlag + 1)

`indexOf` returns `-1` when `--manifest` is absent, so `manifestFlag + 1` is **0** and
the filter discards argument zero — the first positional root, on every invocation that
does not pass a manifest. `check-duplicates src app` scans only `app`. `check-duplicates
no-such-dir-at-all` scans `./src` and exits 0.

It hid perfectly because the documented invocation is `check-duplicates src`, and the
argument it drops is the same directory it then falls back to. SH894 found it only
because a vacuity test tried to point the gate at a planted duplicate in a temp
directory: the gate reported 181 files scanned and a clean tree, and the test read that
as "a planted duplicate does not fail the gate" — true, and for a reason that had
nothing to do with the logic under test.

Shio is safe today because `no-duplicate-components.test.mjs` steers with `cwd` and
never passes a root, with the reason written beside it. That is a workaround carried in
a consumer, which is the wrong place for it: the next consumer to wire this gate up will
write the documented invocation and get a gate that reads whatever directory it feels
like. Fix it upstream, then delete the workaround here and let the test pass a root the
ordinary way.

### LIV.16 Two user contexts, one per shell (SH920)

SH894 exempted Shio's `UserProvider` from the duplicate gate on good grounds: it is
typed on `ShCurrentUser` and bound to `ShUserService`, while the package's is generic
over `VigUser` with `fetchUser` injected. Adopting the package's meant changing the type
every console page reads off `user`, which is not swapping an import.

SH888 then found what that exemption costs. `BentoUserMenu` calls the **package's**
`useCurrentUser`, and a local variant of a context is a different context object — so
the shared menu found no provider and threw, blanking the whole shell behind the error
boundary. Nothing in `tsc` says so; the first render did. The bento shell therefore
mounts the package's provider, fed by Shio's service, and Shio's own provider is
deliberately not also mounted there: nothing under that tree reads it yet, and two
providers means two fetches of the same endpoint.

That leaves the console tree on one context and the bento tree on the other, which is
fine exactly until a Block AR task moves a page that calls Shio's `useCurrentUser` — and
then it throws in the same way, on a surface a curator uses. Every one of the fourteen
crosses this seam.

Resolve it once rather than fourteen times: either Shio's provider wraps the package's
and both contexts resolve from one fetch, or `ShCurrentUser` folds into `VigUser` and
the local provider goes. The second removes an exemption; the first keeps the types the
console pages already read.

## LV The curator's console, surface by surface (Block AR)

Forty-three console pages, migrated behind the parallel route the previous block builds,
in an order chosen by what each slice teaches the next. Home first, because it is cheap
and it decides whether the redesign reads as real; then sites, which settle the list
shape for the ten list screens after it; then the content browser, where a curator
actually spends the day. Posts and post types come next and are the hard ones - the post
editor carries fifteen dynamic widgets whose field logic must not be rewritten, which is
what the chrome adapter is for. Admin follows in three slices sized by how alike their
pages are. The Universal Editor is deliberately late: it is the one surface where
swapping the shell can break behaviour rather than appearance, because its preview
bridge reads the chrome around it. The block ends with the three tasks that make a
migration finished rather than merely done - the cutover itself, the retirement of the
console shell, and the published documentation, which after the cutover describes a
product that no longer exists.

### LV.1 Home, the surface that decides whether this reads as real (SH898)

console/home/home.page.tsx is a single page and the first thing a curator sees after
login. It is also the cheapest possible proof that the shell, the tokens, the
translations and the digest all work together, which is why it goes first - not because
of its importance. Compose it from the package's tiles and count tiles rather than from
Card, put its heading in a BentoHero, and take the first before-and-after digest here.
If something about the extracted layer is wrong, it is far cheaper to find on one page
than on the four-page site slice behind it.

### LV.2 Sites, where the list shape is settled (SH899)

Four pages: the site list, new, edit and import. The list is the first of roughly ten
list screens in this console, and whatever shape it takes is the shape the rest will
copy, so this slice deserves more attention than its size suggests - what a tile
carries, what an empty result says, where the New affordance sits, whether a failed load
offers a retry. Use the package's list page rather than hand-rolling the grid the way
the console does today. The edit page is the first entity-shell candidate: a site has a
title, a description and an identity that belongs in a hero rather than in the form.

### LV.3 The content browser, where the day is spent (SH900)

object.children.page.tsx and the two folder pages are the console's main workspace: the
tree, the listing, the selection, copy and move, and the actions that hang off a row. It
is the surface a curator has open most of the time, so a half-migrated console shows
here first and worst. It is also the least entity-shaped screen in the product - a
browser is neither a detail page nor a mosaic - so this slice is where the shared
vocabulary either stretches to a real working surface or is found not to, and that is
much better known before admin than after it.

### LV.4 Posts, the heaviest forms in the product (SH901)

post.edit, post.new and the post-type selector. The editor builds its schema at runtime
from the post type and renders fifteen dynamic widgets through a widget map, each
reading widgetSettings that the backend can return as the literal string "null". None of
that logic changes here and none of it should be touched: the chrome adapter from the
previous block renders the same fields inside frosted sections. What does change is the
identity - title, status, schedule - which moves into the hero, and the save bar, which
becomes the scroll morph. This is the slice most likely to reveal that the adapter needs
a prop it does not have.

### LV.5 Post types, the editor behind every post (SH902)

Three pages, and the edit page carries the attribute editor: the ordered list of fields,
their widgets, their settings, and a console sticky save bar with no scroll morph. It is
the second most visited editor after posts and the one where a curator makes changes
that reshape every post of that type, so an unambiguous save affordance matters more
here than elsewhere. Post types are also the one name-keyed surface in the product,
which means the identity in the hero is a name rather than an id - worth checking
against the shell's inline-edit behaviour rather than assuming.

### LV.6 The three surfaces where empty is normal (SH903)

Search results, the trash and the static-file list. All three routinely render nothing,
and all three say so today in their own way with their own layout, because the console
has no shared empty state. The package has one, and this is the slice that proves it: an
empty search is not the same message as an empty trash, so the component has to carry a
title and a description per use rather than one generic line. The static-file list also
holds the only upload affordance in the console, which is a tile shape no other slice
exercises.

### LV.7 Identity: users, groups and roles (SH904)

Six pages, three list-and-form pairs, and the clearest case in the product for the
entity shell: each of these entities has a title, a description and an enabled state,
which is exactly the identity the shell lifts out of the form and into the hero.
Migrating them is mostly deletion - the fields that move up stop being form fields, and
the delete flow the shell owns replaces three hand-rolled dialogs. Take this slice
before the eleven infrastructure pages, because it is where the shell's assumptions are
tested against a small, well-understood set of entities.

### LV.8 Infrastructure, eleven pages that are nearly the same page (SH905)

API tokens, auth providers, exchange providers, site webhooks, email configuration and
tenants: five list-and-form pairs and a settings page. They are the most repetitive
surface in the console, which is why they come as one slice and why they repay the
shared list and shell more than anything else here. The risk in a slice this size is
that it becomes a mechanical sweep with no attention left over, so run the render digest
between each pair rather than only at the end, and notice when one of them needs
something the shell does not have instead of working around it eleven times.

### LV.9 Marketplace, the review queue and the activity log (SH906)

Three pages with three bespoke layouts and little in common. The review queue matters
most: it is the curator's approval surface, the human half of the agent-native design,
and where a curator goes when they do not trust what an agent did. It should not be the
last part of the console that still looks like the old one. The activity log and the
marketplace are both read-mostly surfaces, which is the page shape the package covers
least well - a hero over a table is not a mosaic - so expect this slice to want a plain
section rather than tiles, and let it have one.

### LV.10 The Universal Editor, where chrome and behaviour meet (SH907)

editor-sites and visual-editor wrap a preview iframe and a bridge: the editor annotates
the rendered page with data attributes and the console reads and writes through them.
Every other slice in this block risks appearance; this one risks behaviour, because the
bridge depends on what surrounds the frame - the sizing, the scroll container, the
overlay stacking - and the bento shell changes all three. It is deliberately late in the
order, so the shell has been exercised by nine slices before it reaches the one surface
that can break silently. It is also the slice where a digest is least sufficient and a
real click-through is required.

### LV.11 The last two, and the one nobody navigates to on purpose (SH908)

The GraphiQL page and the not-found page. Both are mostly something the console does not
control - a third-party editor in one case and nothing at all in the other - which makes
them the two surfaces where the shell is almost the whole page. The not-found page
matters more than its content suggests: during a cutover it is where a stale bookmark,
an old link in an email, or a redirect that has not landed yet puts a user, so it is the
surface most likely to be seen in the wrong chrome by somebody who did not choose to be
there.

### LV.12 Make the choice, and make it once (SH909)

With every surface migrated, the console still mounts two route trees, two shells and
two sets of chrome. The parallel route was chosen for reversibility, and a reversal
nobody claims becomes a permanent second product: the other console has been in exactly
this state, with forty pages beside its migrated ones, long enough for it to look
normal. Make bento the default, redirect the console routes to their bento equivalents
rather than dropping them so that bookmarks survive, and confirm through the digest and
the browser check that no route still answers with the old shell.

### LV.13 Retire what nothing renders (SH910)

After the cutover the console shell, its sidebar, its page header and the breadcrumb
chrome are still declared and still compile, and nothing reaches them. Left there they
are what the next new page copies, because they are what a grep for an existing pattern
finds first. They are also Shio's last use of the design system's console-era exports,
so removing them here is what lets the package deprecate that era without breaking a
live consumer. Delete them together with the console route tree, and let the duplication
lint confirm nothing is quietly re-importing what was removed.

### LV.14 The documentation describes the product that was replaced (SH911)

The published end-user documentation walks a curator through the console: where the
sidebar is, what the header shows, how the breadcrumb works - and it shows all of it in
screenshots. After the cutover none of that is what a curator sees. This project has
been here before, with a documentation set that contradicted every block shipped under
it, and the lesson recorded then was that a docs debt of this shape is noticed by nobody
working in the code. Take it in the same block as the cutover rather than as a
follow-up, since a follow-up is what it became last time.

## LVI What Turing's index never receives

Shio publishes into Turing's index (SH84) and can rebuild it (SH413). What the index
receives is still narrower than what Shio knows: a post-type's own fields arrive as
prose or not at all, a push that fails is never retried, and an attachment's text is
never read. Each is a different kind of gap — a schema decision, a recovery path, an
extraction step — and each is visible only as a search returning the wrong thing rather
than as an error anybody sees.

## LVII What the Turing round left in the build and in a guard
